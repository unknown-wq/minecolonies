# World map: generating chunks instead of walking them

Research only. Date: 2026-08-27. Tree: `worldmap/26.3/` on branch `26.3`. **No feature code was written**;
everything below is analysis plus four throwaway measurement probes run against a stock dedicated server.

## Verdict

**Possible, in single player only, with no mixin and no access widener — but the honest recommendation is a
much smaller cap than 100.** In single player the integrated server lives in the same JVM and every API
needed is `public`: `Minecraft#getSingleplayerServer` → `MinecraftServer#getLevel` →
`ServerLevel#getChunkSource` → `ServerChunkCache#addTicketAndLoadWithRadius` / `#getChunkFuture` /
`#getChunkNow`, all of them already-public vanilla methods. On a multiplayer server it is **not possible at
all** without a server-side mod. On cost: the user's "N ≤ 100" almost certainly means chunks, and at N = 100
that is **40 401 chunks at FULL, another ~9 300 chunks of generation halo, roughly 360 MB of permanent,
irreversible growth in the save folder** (measured seed, vanilla, no mods — a modded MineColonies world will
be larger), and on the order of **10–60 minutes of wall clock on a real client**. My *measured* number is
much smaller than that — see §2, it is one 10×10 probe — and the large-radius figures are arithmetic
projections from it, not observations. The disk figure is the one that should decide this: **N = 100 writes
about a third of a gigabyte into the player's world and there is no undo.** I would ship a default of 16 and
a hard cap of 32, with 64 available only by editing the config file, and I would not offer 100.

Two secondary findings matter as much as the headline:

* **The map screen currently pauses the game, and a paused integrated server never runs its chunk-unload
  pass or its ticket-expiry pass.** Generation *does* still progress while paused (the task pump lives
  outside `tickServer`), so a naive "open map → press Generate" button would generate tens of thousands of
  chunks that the server can never release. That is the fastest route to an out-of-memory crash in this
  whole design, and it is invisible until the run is big.
* **This study was written against snapshot-9, and one of its findings was wrong because of it.** An
  earlier revision reported that the brief's premise about chunk statuses was mistaken -- that
  `NOISE`/`SURFACE`/`CARVERS` still exist and `TERRAIN` does not. That is true of `/opt/mc-src-26.3`,
  which despite its name is snapshot-**9** (`SharedConstants.WORLD_VERSION = 5011`; snapshot-10 is
  5015). It is not true of the version the mod targets. See §1.4.

---

## Evidence standard

Same as `docs/studies/structurize-performance.md`:

* **[VERIFIED]** — I opened the file and the cited `file:line` says what I claim, or I ran the thing and
  watched the output.
* **[UNVERIFIED]** — inference I did not confirm. Each one names what would confirm it.

Paths: `worldmap/26.3/src/main/java/com/unknownwq/worldmap/` is abbreviated to `wm/`; vanilla sources are
read from `/opt/mc-src-26.3/net/minecraft/`, abbreviated to `mc/`.

**Version skew, stated up front.** The mod builds against **26.3-snapshot-10**. The decompiled sources I
read are `/opt/mc-src-26.3`, and the only server jar available to run was
`/opt/mc-26.3-snapshot-9/server.jar` — one snapshot older. Line numbers in the crash report I quote in §3
differ by ~70 lines from `/opt/mc-src-26.3`, so the two are not the same build. **One claim did depend on a
behaviour that changed between them — the chunk-status list, see §1.4** — and it was wrong in the first
draft for exactly that reason. Every other API this study leans on (`ServerChunkCache`,
`addTicketAndLoadWithRadius`, `getChunkFuture`, `getChunkNow`, `TicketType`, `ChunkLevel`, `ChunkPyramid`,
`ChunkStep`) has since been re-checked with `javap -p` against the snapshot-10 jar and is present with the
signatures quoted. The measured numbers are still snapshot-9 and the line citations are still the
`/opt/mc-src-26.3` tree. [UNVERIFIED that snapshot-9 and snapshot-10 generate at the
same speed; confirming would mean building and running the snapshot-10 server.]

---

## 0. What I read, and what I ran

**Read end to end:** all eight source files of the mod (`wm/WorldMapClient.java`, `WorldMapConfig.java`,
`WorldMapKeys.java`, `map/MapService.java`, `map/ColumnScanner.java`, `map/MapTile.java`, `map/TileKey.java`,
`map/TileStore.java`) plus the parts of `screen/WorldMapScreen.java` and `render/TileTextures.java` that bear
on pausing and on tile memory.

**Read in vanilla:** `mc/server/level/ServerChunkCache.java`, `ChunkMap.java` (scheduling, unload, save),
`ChunkLevel.java`, `Ticket.java`, `TicketType.java`, `DistanceManager.java` (top),
`ChunkTaskDispatcher.java`, `ThrottlingChunkTaskDispatcher.java`, `WorldGenRegion.java` (write-radius half),
`mc/world/level/chunk/status/ChunkStatus.java`, `ChunkPyramid.java`, `ChunkStep.java`, the `full`/`spawn`
tasks of `ChunkStatusTasks.java`, `mc/world/level/chunk/PalettedContainer.java`, `LevelChunkSection.java`,
`LevelChunk.java` (constructors + `getBlockState`), `ProtoChunk.java` (write path),
`mc/util/ThreadingDetector.java`, `mc/util/Util.java` (executor sizing), `mc/server/MinecraftServer.java`
(`runServer`, `waitUntilNextTick`, `pollTask`, `prepareLevels`, `setInitialSpawn`),
`mc/client/server/IntegratedServer.java`, `mc/client/multiplayer/ClientChunkCache.java`,
`mc/server/commands/ForceLoadCommand.java`, `mc/world/level/TicketStorage.java`.

**Ran** (throwaway probes, all in the scratchpad, nothing committed): a stock
`/opt/mc-26.3-snapshot-9/server.jar` dedicated server, seed `1234567`, `-Xmx6G`, on this container's
4-core Xeon @ 2.1 GHz. Probes drove it through a fifo on stdin and sampled `utime+stime` from
`/proc/<pid>/stat` every two seconds; chunk counts came from a small Python script that reads the 4096-byte
sector table at the head of each `.mca` and counts non-zero entries. Four probes:

1. three separate 16×16-chunk `/forceload` regions, timed (run before the 10×10 cap was imposed);
2. a **64×64** attempt issued as sixteen back-to-back `/forceload add` commands — **killed the server**, see §1.3;
3. a second, *paced* contiguous 64×64 attempt — **stopped by me before completion**, on instruction to cap probes at 10×10;
4. the capped **10×10** probe that supplies the one measured row in §2.

**Did not run:** the mod itself, or any client. I did not build anything, so I never touched
`tools/mc-build.sh`.

---

## 1. Is it possible, and where?

### 1.1 Single player — yes, and with public API only

The integrated server is in the same process and reachable from client code:

| Step | Call | Access |
|---|---|---|
| get the server | `Minecraft#getSingleplayerServer()` | `public`, `mc/client/Minecraft.java:2560` [VERIFIED] — and `wm/map/MapService.java:491` already calls it |
| get the level | `MinecraftServer#getLevel(ResourceKey<Level>)` | `public`, `mc/server/MinecraftServer.java:1175` [VERIFIED] |
| get the chunk source | `ServerLevel#getChunkSource()` | `public`, `mc/server/level/ServerLevel.java:1206` [VERIFIED] |
| request generation | `ServerChunkCache#addTicketAndLoadWithRadius(TicketType, ChunkPos, int)` | `public`, returns `CompletableFuture<?>`, `ServerChunkCache.java:485` [VERIFIED] |
| or, lower level | `ServerChunkCache#getChunkFuture(int,int,ChunkStatus,boolean)` | `public`, `ServerChunkCache.java:212` [VERIFIED] |
| collect the result | `ServerChunkCache#getChunkNow(int,int)` | `public`, `ServerChunkCache.java:176` [VERIFIED] |
| hop onto the server thread | `MinecraftServer#execute` / `#submit` | `public`, inherited from `BlockableEventLoop:98,62` [VERIFIED] |
| ticket types | `TicketType.PLAYER_SPAWN` etc. | `public static final`, `mc/server/level/TicketType.java:17-25` [VERIFIED] |
| target status | `ChunkStatus.FULL` | `public static final`, `ChunkStatus.java:32` [VERIFIED] |

**No mixin and no access widener is needed.** Every member above is already `public` in the merged jar the
mod compiles against, and the client jar contains the whole server package tree (that is what
`IntegratedServer extends MinecraftServer` means). The mod's existing `"environment": "client"` declaration
is unaffected — it controls where the mod loads, not which vanilla classes exist.

**Which ticket.** `getChunkFuture(..., loadOrGenerate = true)` adds a `Ticket(TicketType.UNKNOWN, 33)` of its
own (`ServerChunkCache.java:234`) [VERIFIED]. `UNKNOWN` has `timeout = 1` tick and the
`FLAG_CAN_EXPIRE_IF_UNLOADED` bit (`TicketType.java:25`, flags `18`) [VERIFIED], and `canTicketExpire`
returns `true` immediately for such a type (`TicketStorage.java:296-298`) [VERIFIED]. Vanilla gets away with
that only because its synchronous `getChunk` blocks the server thread so no tick can purge the ticket in
between. An *asynchronous* caller must not rely on it: the ticket can be purged two ticks later, while the
chunk is still generating. Vanilla says so itself — `addTicketAndLoadWithRadius` explicitly refuses any
ticket type with `canExpireIfUnloaded()` "cannot fetch asynchronously" (`ServerChunkCache.java:490-492`)
[VERIFIED].

The right ticket is **`TicketType.PLAYER_SPAWN`**: `timeout = 20` ticks, flags `2` — loads, does *not*
simulate, does *not* persist, does *not* expire while unloaded (`TicketType.java:17`) [VERIFIED]. It holds
the chunk until generation settles and then lets go about a second later. Using a loading-only (not
simulating) ticket also means `ChunkMap#prepareTickingChunk` never runs for these chunks, so they are never
post-processed, never entity-ticked, and — importantly — never queued to be *sent to the client*, because
the send path is inside `prepareTickingChunk` (`ChunkMap.java:673-688`) [VERIFIED].

### 1.2 Multiplayer — no

On a real server the client has no way to make the server generate a chunk it is not already sending, and no
way to receive one outside its tracking view; there is no vanilla packet for either, so this feature is
impossible without a server-side mod and that branch is not worth investigating further.

*(A world opened to LAN is the awkward middle case: `getSingleplayerServer()` is still non-null and the
generation would work, but `isPublished()` is true, the world does not pause, and the run would degrade
every guest's tick rate. The feature should refuse to start when `isPublished()`.)*

### 1.3 `/forceload` — relevant only as a cautionary tale

* It **does** generate, not merely keep loaded: `setChunkForced` adds the ticket and then calls
  `this.getChunk(chunkX, chunkZ)` (`mc/server/level/ServerLevel.java:1517-1524`) [VERIFIED], which is the
  synchronous, blocking path.
* Its cap is **256 chunks per command invocation** — `chunkCount > 256L` throws
  (`mc/server/commands/ForceLoadCommand.java:151-154`) [VERIFIED] — and it needs permission level
  `GAMEMASTERS` (`:63`), i.e. cheats.
* Its ticket is `TicketType.FORCED` at `ChunkMap.FORCED_TICKET_LEVEL` = entity-ticking
  (`TicketStorage.java:383-385`, `ChunkMap.java:126`) [VERIFIED]. `FORCED` has `FLAG_PERSIST`
  (`TicketType.java:22`, flags `15`) [VERIFIED], so the chunks stay force-loaded and *ticking* across
  restarts until removed. Completely wrong shape for a map sweep.
* And it is the live demonstration of the blocking-call hazard: my 64×64 probe issued sixteen
  16×16 `/forceload add` commands back to back, and the sixteenth one blocked the server tick inside
  `ServerChunkCache.getChunk` → `managedBlock` for sixty seconds until the watchdog killed the server:

  ```
  [16:00:02] [Server Watchdog/ERROR]: A single server tick took 60.00 seconds (should be max 0.05)
  ...
    at net.minecraft.util.thread.BlockableEventLoop.managedBlock
    at net.minecraft.server.level.ServerChunkCache.getChunk(ServerChunkCache.java:148)
    at net.minecraft.server.level.ServerLevel.setChunkForced(ServerLevel.java:1445)
    at net.minecraft.server.commands.ForceLoadCommand.changeForceLoad(ForceLoadCommand.java:172)
  ```
  [VERIFIED, observed]

  That is exactly the failure the design must avoid: **never call the synchronous `ChunkSource#getChunk` for
  a chunk that is not already there.**

### 1.4 The chunk statuses in 26.3

**The status list differs between the two snapshots, and this is where reading the mislabelled tree bit.**

`/opt/mc-src-26.3` -- snapshot-**9**, `SharedConstants.WORLD_VERSION = 5011` -- registers, in order
(`mc/world/level/chunk/status/ChunkStatus.java:21-32`) [VERIFIED]:

```
EMPTY → STRUCTURE_STARTS → STRUCTURE_REFERENCES → BIOMES → NOISE → SURFACE → CARVERS
      → FEATURES → INITIALIZE_LIGHT → LIGHT → SPAWN → FULL
```

**Snapshot-10, which is what the mod targets, does not.** Read out of the bytecode the build actually
compiles against -- `javap -p` on `ChunkStatus` extracted from
`~/.gradle/caches/fabric-loom/26.3-snapshot-10/minecraft-merged.jar` [VERIFIED]:

```
EMPTY → STRUCTURE_STARTS → STRUCTURE_REFERENCES → BIOMES → TERRAIN
      → FEATURES → INITIALIZE_LIGHT → LIGHT → SPAWN → FULL
```

So the brief was right and this study's first draft was wrong: snapshot-10 *does* collapse
`NOISE`/`SURFACE`/`CARVERS` into a single `TERRAIN`. The same check confirms `SurfaceRules` is absent from
the snapshot-10 jar while present in the snapshot-9 tree, which is the other half of the same divergence.

`SPAWN` -- a step between `LIGHT` and `FULL` running `generator().spawnOriginalMobs(...)`, the initial mob
population that used to happen inside `FULL` (`ChunkPyramid.java:40`, `ChunkStatusTasks.java:181-189`)
[VERIFIED] -- exists in **both** snapshots, so it is not a 26.3-snapshot-10 change as first reported. It
writes no block states.

**Method note for anyone extending this study:** a source tree can be mislabelled; the jar the build
resolves cannot. Settle existence-and-signature questions with `unzip -l` and `javap -p` against
`~/.gradle/caches/fabric-loom/26.3-snapshot-10/minecraft-merged.jar`, and use the snapshot-9 source tree
only to read implementation logic.

Two derived numbers this design needs:

* **`ChunkLevel.MAX_LEVEL = 33 + RADIUS_AROUND_FULL_CHUNK`** (`ChunkLevel.java:15`) [VERIFIED], and
  `RADIUS_AROUND_FULL_CHUNK` is the accumulated dependency radius of the `FULL` step. From the 16×16
  `/forceload` probe the loaded-and-persisted region came out at exactly **42×42 chunks**, i.e. a halo of 13
  chunks around a ticket placed at level 31, giving `MAX_LEVEL = 44` and **`RADIUS_AROUND_FULL_CHUNK = 11`**
  [VERIFIED, measured — 1764 = 42² chunk entries counted in the four region files of round A and again of
  round B]. A ticket at level 33 (`FULL`, no simulation) therefore drags an **11-chunk halo** with it.
* **After a chunk reaches `FULL`, no worldgen worker writes block states into it any more.** The only step
  with a non-zero `blockStateWriteRadius` past the terrain steps is `FEATURES`, at radius 1
  (`ChunkPyramid.java:31-37`) [VERIFIED]. `LIGHT` requires `INITIALIZE_LIGHT` at radius 1
  (`ChunkPyramid.java:39`) [VERIFIED] and `INITIALIZE_LIGHT`'s parent is `FEATURES`
  (`ChunkStatus.java:29`) [VERIFIED]; accumulated dependencies only ever grow through
  `ChunkStep.Builder.buildAccumulatedDependencies` (it takes `ChunkStatus.max` at every distance,
  `ChunkStep.java:114-135`) [VERIFIED]. So a chunk at `FULL` has every radius-1 neighbour at
  `INITIALIZE_LIGHT` or later, i.e. past `FEATURES`, i.e. past the last thing that could write into it.
  This is the load-bearing safety argument for §4.

---

## 2. What it costs

### 2.1 The one measured row

**The capped probe.** Vanilla dedicated server, snapshot-9, seed `1234567`, warm JVM, nothing else
running on the box. One `/forceload add 480000 480000 480159 480159` — a 10×10-chunk square at chunk
(30000, 30000), far from anything previously generated.

```
idle baseline            13 jiffies / 10 s   (≈ 0.13 cores)
t = 0–2 s               510 jiffies / 2 s
t = 2–4 s               395
t = 4–6 s               370
t = 6–8 s                18   ← back to idle
...
cpu_jiffies (26 s window) = 1502
```
[VERIFIED, observed]

* **Wall clock of the burst: ~7 seconds.**
* **CPU consumed by the burst: 1275 jiffies ≈ 12.8 CPU-seconds** (510 + 395 + 370), at ~2.1 cores of
  parallelism out of the 3 worldgen worker threads this box gets
  (`Util.maxAllowedExecutorThreads()` = `availableProcessors − 1` = 3, `mc/util/Util.java:236-238`)
  [VERIFIED].
* **Chunks brought to `FULL`: 196.** Counted directly out of `r.937.937.mca` after `save-all flush` — 196
  non-zero sector-table entries = 14², which is the 10×10 forced square plus the 2-chunk skirt a level-31
  ticket adds [VERIFIED, measured].
* **Chunk slots touched including halo: 36² = 1296** (10 + 2 × 13). These are proto-chunks; `save-all` does
  not persist them (`ChunkMap.java:434` filters to `ImposterProtoChunk`/`LevelChunk`) [VERIFIED], but the
  unload path does (`ChunkMap.save(ChunkAccess)`, `ChunkMap.java:742-765`, only skips a structure-free
  `EMPTY` chunk) [VERIFIED] — which is why the earlier 16×16 rounds ended up with all 1764 halo chunks on
  disk once their tickets were dropped.
* **Disk: `r.937.937.mca` = 1 609 932 bytes for those 196 FULL chunks = 8.2 KB per FULL chunk**
  [VERIFIED, measured]. A proto-chunk in the halo costs at least one 4 KB region sector.

Two derived unit costs, and I will be explicit that the second is the one I project from:

* **~65 ms of CPU per chunk brought to FULL** — 12.8 s / 196. This number is *inflated* by the halo, which
  in a 10×10 square is 85 % of all the work.
* **~9.9 ms of CPU per chunk slot touched** — 12.8 s / 1296. The three pre-cap 16×16 rounds give 24–25
  CPU-seconds each over 1764 slots = **13.9 ms per slot** [VERIFIED, observed, but those runs are the ones I
  was told to stop repeating]. I use **12 ms/slot** as the central figure and treat 10–14 ms as the band.

### 2.2 The projections

For a square sweep of radius N chunks: side `S = 2N + 1`, chunks at `FULL` = `S²`, chunk slots touched =
`(S + 22)²` (the 11-chunk halo on each side, §1.4). Projected CPU = slots × 12 ms; projected wall = CPU /
2.1 effective cores; projected disk = `S² × 8.2 KB + halo × 4 KB`.

**Every row in this table is a projection, not a measurement.** The only thing I measured is 196 FULL
chunks / 1296 slots in ~7 s and 12.8 CPU-seconds, which sits between the N = 4 and N = 8 rows; everything
else is that unit cost multiplied out.

| N (chunks) | side | chunks at FULL | slots touched | proj. CPU | proj. wall, dedicated box | proj. region-file growth |
|---:|---:|---:|---:|---:|---:|---:|
| 4 | 9 | 81 | 961 | 12 s | ~6 s | ~4 MB |
| 8 | 17 | 289 | 1 521 | 18 s | ~9 s | ~7 MB |
| 16 | 33 | 1 089 | 3 025 | 36 s | ~17 s | ~16 MB |
| 32 | 65 | 4 225 | 7 569 | 91 s | ~43 s | ~47 MB |
| 64 | 129 | 16 641 | 22 801 | 274 s | ~2 min 10 s | ~157 MB |
| **100** | **201** | **40 401** | **49 729** | **~600 s** | **~5 min** | **~360 MB** |

**Why the wall-clock column is a lower bound, and by how much.** The coordinator's warning is right and I
want to restate it in my own terms:

1. **The probe ran on a dedicated server with no client.** On a real single-player client the same JVM is
   also running the client thread and the render thread on the same 4–8 cores. Effective worldgen
   parallelism drops; a factor of **2–3×** on wall clock is my expectation. [UNVERIFIED — confirming means
   running the actual client, which this container cannot do.]
2. **The probe world was vanilla.** MineColonies + Structurize + Domum Ornamentum add features, structure
   sets and block entities to worldgen. Pregeneration folklore puts modded worldgen at **2–5×** vanilla per
   chunk. [UNVERIFIED — no measurement, and this repo's mods were not loaded.]
3. **One seed, one biome mix.** Ocean chunks are cheap; a run that lands in a jungle, a cave-dense region or
   a trial-chamber cluster costs several times more per chunk. My 16×16 rounds varied by 30 % between three
   locations on the *same* seed [VERIFIED, observed: 42.5 / 24.9 / 24.1 CPU-seconds, the first inflated by
   JIT warmup].
4. **Per-chunk cost is not flat.** The first chunks of a run pay fixed setup, and neighbouring chunks share
   dependency work — which is exactly why I model per *slot touched* rather than per FULL chunk, and why
   scaling the measured 65 ms/FULL-chunk straight to 40 401 chunks (which would give 44 minutes) badly
   overstates the halo and is the wrong arithmetic.
5. **The box is a 2.1 GHz Xeon.** A typical gaming CPU is faster per core, which pushes the other way.

Netting those: **N = 100 on a real modded client is plausibly 10–60 minutes.** That is a projection with a
wide band. The disk figure is much more robust, because 8.2 KB per FULL chunk is a directly measured,
CPU-independent quantity, and modded chunks are only ever *bigger*.

### 2.3 "не более 100" — both readings

| reading | area | chunks at FULL | sane? |
|---|---|---:|---|
| N = 100 **chunks** | 201 × 201 chunks = 3216 × 3216 blocks | 40 401 | This is clearly what was meant — it produces a map you would call large. It is also the reading that costs ~360 MB of save. |
| N = 100 **blocks** | 201 × 201 blocks ≈ 13 × 13 chunks | 169 | Projects to ~15 CPU-seconds and ~7 MB. Cheap — and useless: a 201-pixel-wide patch is smaller than the map window at 1 px per block. |

So: chunks. And the sentence worth writing down plainly is —

> **At N = 100 the button generates 40 401 chunks, writes roughly 360 MB into the player's save folder in
> vanilla (more with mods), takes somewhere between ten minutes and an hour on a real client, and cannot be
> undone.**

---

## 3. How it would avoid freezing the game

### 3.1 The pause trap — the most important item in this study

`WorldMapScreen#isPauseScreen()` returns `true` unconditionally (`wm/screen/WorldMapScreen.java:129-133`)
[VERIFIED], and in single player that really does stop the integrated server ticking:
`IntegratedServer#tickServer` sets `this.paused = Minecraft.getInstance().isPaused() || players.isEmpty()`
and, when paused, calls `tickPaused()` instead of `super.tickServer(haveTime)`
(`mc/client/server/IntegratedServer.java:130-162`) [VERIFIED].

Now trace what a paused server still does, because it is not what you would guess:

* `MinecraftServer#runServer` calls `processPacketsAndTick(...)` and then, **unconditionally**,
  `waitUntilNextTick()` (`mc/server/MinecraftServer.java:744-751`) [VERIFIED].
* `waitUntilNextTick()` calls `runAllTasks()` and then `managedBlock(() -> !haveTime())`
  (`MinecraftServer.java:844-853`) [VERIFIED].
* `pollTaskInternal()` drains the server's own task queue **and then every level's
  `chunkSource.pollTask()`** while `haveTime()` (`MinecraftServer.java:881-892`) [VERIFIED].
* `runDistanceManagerUpdates()` — which is what actually dispatches queued generation tasks via
  `chunkMap.runGenerationTasks()` (`ServerChunkCache.java:280-289`, `ChunkMap.java:668-671`) [VERIFIED] — is
  called both from `ServerChunkCache#tick` (skipped while paused) **and from
  `getChunkFutureMainThread` whenever a chunk is absent** (`ServerChunkCache.java:236-246`) [VERIFIED],
  which is our own call path.

So **generation progresses perfectly well while the game is paused** — in fact it gets almost the whole
50 ms tick budget, because `tickServer` did nothing.

But the passes that *release* chunks live inside `ServerChunkCache#tick`, which is skipped:

* `ticketStorage.purgeStaleTickets(chunkMap)` — the only place ticket timeouts are decremented
  (`ServerChunkCache.java:317-320`, `TicketStorage.java:279-289`) [VERIFIED]. Paused ⇒ **no ticket ever
  expires**.
* `chunkMap.tick(haveTime)` → `processUnloads(haveTime)` — the only place `toDrop` is drained and unload
  tasks run (`ServerChunkCache.java:330`, `ChunkMap.java:450-457, 474-495`) [VERIFIED]. Paused ⇒ **no chunk
  is ever unloaded or freed**.

**Consequence: a generation run started from the current, pausing map screen accumulates every generated
chunk in the `ChunkMap` with no ceiling.** From the crash report of the 64×64 probe, ~4 000 force-loaded
chunks plus halo sat at 2.5 GB resident [VERIFIED, observed: "Process Resident Size (MiB): 2544.84"], i.e.
crudely half a megabyte of heap per resident chunk. Forty thousand of those is not a number any client heap
survives.

**The fix is structural, not a tuning knob:** while a run is active, `isPauseScreen()` must return `false`.
The screen already has a `gamePauses()` helper and a footer line that tells the truth about pausing
(`wm/screen/WorldMapScreen.java:139-147`) [VERIFIED]; that text has to change while generating. The world
then ticks behind the map — which is also the honest behaviour, since the world *is* being changed.

### 3.2 Pacing

Two separate budgets, because two different threads are at risk:

* **The server thread.** Every ticket added does work on the main thread:
  `addTicketAndLoadWithRadius` calls `runDistanceManagerUpdates()` (`ServerChunkCache.java:496`)
  [VERIFIED], and `DistanceManager#runAllUpdates` runs `loadingChunkTracker.runDistanceUpdates(Integer.MAX_VALUE)`
  — **unbounded** (`mc/server/level/DistanceManager.java:68`) [VERIFIED]. Submitting 40 401 tickets in one
  go would do 40 401 level propagations inside one tick. So: **K tickets per server tick, K small.** I would
  start at K = 4 and make it configurable. The chunk system already self-throttles downstream —
  `DistanceManager`'s ticket dispatcher allows only 4 chunks in execution at a time
  (`DistanceManager.java:54`) [VERIFIED] — so a larger K buys queue depth, not throughput.
* **The client thread.** It does nothing but enqueue work and read counters. The existing scanner thread
  absorbs the actual scans at its existing `chunksPerTick` budget (`wm/WorldMapConfig.java:52`).

The right shape is *in-flight-bounded*, not rate-based: keep at most `M` outstanding chunk futures (M ≈ 8–16),
and submit a new one each time one completes. That naturally paces to whatever the machine can do and needs
no tuning per hardware. Wall-clock ETA then falls out of a moving average of completions per second.

### 3.3 Going through the async pipeline rather than blocking

`ServerChunkCache#getChunkFuture` behaves differently depending on the calling thread
(`ServerChunkCache.java:212-226`) [VERIFIED]:

* **from the server thread** it calls `getChunkFutureMainThread` and then `managedBlock(future::isDone)` —
  it *blocks the tick* until the chunk is done. This is the path that killed my probe server (§1.3). Do not
  use it.
* **from any other thread** it does `supplyAsync(() -> getChunkFutureMainThread(...), mainThreadProcessor)
  .thenCompose(...)` — it schedules the request onto the chunk-source main-thread executor and returns
  immediately. This is the one to use.

`addTicketAndLoadWithRadius(type, pos, 0)` must be called *on* the server thread (it mutates `TicketStorage`
and drives `runDistanceManagerUpdates`), but it does **not** block: for radius 0 it returns
`chunkHolder.scheduleChunkGenerationTask(FULL, chunkMap)` directly (`ServerChunkCache.java:485-499`,
`ChunkMap.java:300-306`) [VERIFIED]. So the clean pattern is `server.execute(() -> { ...add ticket, keep
the future... })` and let the future's completion callback do the rest. Nothing ever blocks the server tick
and nothing ever blocks the client tick.

### 3.4 Progress reaching the UI

The screen renders on the render thread and reads whatever the run object exposes. Nothing here needs a
lock: `requested` is written once, `completed` is an `AtomicInteger` bumped from the future callbacks,
`state` is a `volatile` enum, and the ETA is computed on the render thread from `completed` plus a
`volatile long startNanos`. This is the same discipline `MapTile` already uses for `revision`/`lastUsed`
(`wm/map/MapTile.java:32-35` and the class comment) [VERIFIED]. A progress bar that is one frame stale is
not a bug.

The bar should show **two** numbers, because they diverge: chunks *generated* and chunks *drawn*. The
scanner thread runs behind generation and the difference is exactly the backlog `MapService#backlog()`
already reports (`wm/map/MapService.java:138-141`) [VERIFIED].

### 3.5 Cancellation

Mandatory, and there are five triggers:

| trigger | detected by | what must happen |
|---|---|---|
| player presses Cancel / closes the screen | `Screen#removed()` | stop submitting; leave outstanding futures alone (they finish and their `PLAYER_SPAWN` tickets expire 20 ticks later) |
| dimension change | `MapService#tick` already recomputes `dimension` each tick (`MapService.java:210`) [VERIFIED] | abort — the target `ServerLevel` is no longer the one being displayed |
| world change / disconnect | `MapService#tick` already detects a world-key change and calls `leaveWorld` (`MapService.java:204-209`) [VERIFIED] | abort **before** `leaveWorld` swaps the store, otherwise in-flight scans get filed under the wrong world. The existing `runScan` world-key guard (`MapService.java:370-376`) [VERIFIED] already drops them, but the run itself must stop submitting |
| client shutdown | `ClientLifecycleEvents.CLIENT_STOPPING`, already wired to `service.shutdown()` (`WorldMapClient.java:67`) [VERIFIED] | abort, then the existing `flushAll(true)` writes what was scanned |
| server pauses because the run's screen closed and another pausing screen opened | polling `IntegratedServer#isPaused()` | pause the run (stop submitting) until the server ticks again — see §3.1 |

"Cancel" cannot mean "un-generate". Every chunk already produced stays in the save. The UI must say so
before the run starts, not after.

---

## 4. Getting the generated chunks onto the map

### 4.1 The client will never see them

`ClientChunkCache.Storage` is a fixed ring buffer of `(2r+1)²` slots centred on the view position, with
`r = max(2, renderDistance) + 3`, and `inRange` rejects anything outside it
(`mc/client/multiplayer/ClientChunkCache.java:161-162, 224-238, 330-332`) [VERIFIED]. A chunk 100 chunks
away is not representable client-side at all. So the existing `ClientChunkEvents.CHUNK_LOAD` path
(`WorldMapClient.java:50`) cannot be the delivery mechanism, and no amount of ticket juggling changes that.

### 4.2 Can the scanner read a server chunk directly?

Type-wise, trivially: both are `net.minecraft.world.level.chunk.LevelChunk`, and `ColumnScanner#scan` takes
a `LevelChunk` and touches only `getPos`, `getMinY`, `getHeight(Heightmap.Types.WORLD_SURFACE, …)`,
`getBlockState`, `state.getMapColor(chunk, pos)` and `state.isFaceSturdy(chunk, pos, UP)`
(`wm/map/ColumnScanner.java:55-129`) [VERIFIED]. All of those are `BlockGetter`/`ChunkAccess` API that a
server `LevelChunk` implements identically.

**Does the server chunk have what the scan needs at the moment it becomes FULL?** Yes. `WORLD_SURFACE` is in
`ChunkStatus.FINAL_HEIGHTMAPS` (`ChunkStatus.java:18-19`) [VERIFIED] and the `LevelChunk(ServerLevel,
ProtoChunk, …)` constructor copies exactly the `FULL.heightmapsAfter()` set across from the proto-chunk
(`mc/world/level/chunk/LevelChunk.java:117-121, 156-161`) [VERIFIED]. Lighting is irrelevant — the map never
reads it. Nothing is missing.

**Is it safe?** The existing argument is that `PalettedContainer#get` does not call `acquire()` — verified
again here: `get(int,int,int)` and `get(int)` go straight to `data.palette.valueFor(data.storage.get(index))`
with no `threadingDetector` involvement, while `set`, `getAndSet`, `read` and `write` all wrap themselves in
`acquire()`/`release()` (`mc/world/level/chunk/PalettedContainer.java:100-176`) [VERIFIED]. So a reader can
never *trip* the `ThreadingDetector`; only two mutators colliding can
(`mc/util/ThreadingDetector.java:29-52`) [VERIFIED].

That argument **does still hold against a server chunk, but only after `FULL`, and the reason is different
from the client case:**

* Worldgen workers are done with it. §1.4 established that a chunk at `FULL` has every radius-1 neighbour
  past `FEATURES`, and `FEATURES` (write radius 1) is the last step that writes block states into a
  neighbour (`ChunkPyramid.java:11-42`) [VERIFIED]. **So the worker pool is not a writer any more.** This is
  the claim I would re-check first if anything ever corrupts a tile.
* The `ProtoChunk` phase is a different story and must be avoided entirely. During generation the chunk is
  written by whichever worker owns the step, and there is no ordering with an outside reader. Reading a
  chunk before `FULL` — e.g. by optimistically calling `getChunkNow` or by grabbing the future's
  intermediate results — is where a plausible-looking design corrupts a tile or throws. **Only ever read a
  chunk after its `FULL` future has completed.**
* The residual writer is the **server main thread**, and only if the chunk happens to be inside the player's
  simulation distance, in which case it is block-ticking and random-ticking (fluid flow, grass, leaves).
  Those writes go through `LevelChunkSection#setBlockState(..., checkThreading = true)` →
  `PalettedContainer#getAndSet` → `acquire()` (`mc/world/level/chunk/LevelChunkSection.java:56-66`)
  [VERIFIED]. A concurrent `get()` from the scanner does not trip the detector, but it can read a stale or —
  in the pathological case of a non-`volatile` `long` word in `SimpleBitStorage` — a torn value. **This is
  precisely the hazard the mod already accepts on the client side**, and the existing catch-all in
  `MapService#runScan` (`wm/map/MapService.java:388-397`) [VERIFIED] already reduces it to "one lost chunk".
  It is not a new risk in kind, only in frequency.
* One genuinely new hazard: `PalettedContainer#onResize` swaps `this.data` (a `volatile` field, `:26`) only
  *after* `newData.copyFrom(...)` completes (`PalettedContainer.java:91-98`) [VERIFIED], so a reader sees
  either the whole old `Data` or the whole new one. Good. But `createOrReuseData` can *return the old
  object* when the configuration is unchanged (`:79-83`) [VERIFIED], and then `copyFrom` runs in place. I
  did not chase whether that path is reachable during a live resize. [UNVERIFIED.]

### 4.3 The safe handoff

**Recommended: scan on the server thread, hand over finished pixels.**

1. On the **server thread**, when the `FULL` future for chunk (x, z) completes: call
   `chunkSource.getChunkNow(x, z)` — which returns `null` unless it is on the main thread
   (`ServerChunkCache.java:176-179`) [VERIFIED] — and `getChunkNow(x, z-1)` for the north neighbour the
   shading needs.
2. Run `ColumnScanner#scan` right there, still on the server thread. Nothing else can be writing the chunk
   at that instant, so every race in §4.2 disappears by construction.
3. Hand the resulting **256 ARGB ints** (copied out of the scanner's reusable buffer) to the scanner thread,
   which merges them into a tile exactly as it does today.

It costs a few hundred microseconds of server tick per chunk — at 4 chunks per tick that is well under a
millisecond of the 50 ms budget. It is the one place in this design where the conservative option is nearly
free, so take it. It also means the generation path needs its own `ColumnScanner` instance, since the
existing one is explicitly single-threaded and stateful (`wm/map/ColumnScanner.java:22-24`) [VERIFIED].

**The cheaper alternative, and why I would not take it.** Hand the two `LevelChunk` objects straight to the
existing scanner queue as a `ScanJob`, exactly as the client path already does
(`wm/map/MapService.java:240`) [VERIFIED]. That works — a `ScanJob` carries the object rather than the
position and already survives the chunk unloading underneath it — but it reopens the "server main thread may
be block-ticking this chunk" window from §4.2 for however long the chunk waits in the queue, which under
load is seconds rather than microseconds.

**Do not** copy the sections defensively in either variant. A 24-section deep copy per chunk costs more than
the scan it protects and buys nothing that scanning in the completion callback does not already give.

### 4.4 The north-neighbour problem

`ColumnScanner` shades a row against the row to its north and draws the chunk's northernmost row flat if the
neighbour is missing (`wm/map/ColumnScanner.java:44-50, 66-73`) [VERIFIED], and **chunks are never
re-scanned**, so that flat row is permanent (`wm/map/MapTile.java` class comment, `MapService.java:381-385`)
[VERIFIED]. In the walking case that rarely bites, because chunks stream in around the player. In a sweep it
would bite *every* chunk unless the order is right.

Sweep in **rows of constant z, ascending**, and retain the previous row's `LevelChunk` references until the
current row is scanned. At N = 100 that is 201 detached chunks held for one row — order 30 MB
[UNVERIFIED, from the ~0.5 MB/chunk resident figure in §3.1] — which is affordable, and it guarantees every
chunk except the very first row gets its northern neighbour.

---

## 5. Memory and disk

### 5.1 The tile store is fine — the brief's estimate is off by about fifty times

The brief says "at N=100 the covered area is 1600×1600 chunks ≈ 25 600×25 600 blocks ≈ 2500 tiles". That
arithmetic converts N to blocks and then labels the result chunks. The real numbers:

* radius 100 chunks ⇒ side `2 × 100 + 1 = 201` chunks ⇒ **3216 × 3216 blocks**;
* a tile is 512 × 512 blocks (`wm/map/MapTile.java:27`) [VERIFIED] ⇒ 3216 / 512 = **6.28 tiles per axis**;
* so **49 tiles typically, 64 in the worst tile alignment** — not 2500.

| | at N = 100 |
|---|---|
| tiles covered | 49 (7×7) typical, up to 64 (8×8) worst alignment |
| heap while resident | 49–64 MiB (1 MiB per tile, `MapTile.java:27-30`) [VERIFIED] |
| against the current `cpuTileCap` | 64 (`WorldMapConfig.java:53`) [VERIFIED] — **exactly at the limit** |
| on disk | 49–64 × ~12–16 KiB ≈ **0.6–1.0 MiB** (the README quotes ~16 KiB for a typical explored tile) |

So the LRU + gzip store survives N = 100 without redesign. Two small caveats:

* Sitting *at* `cpuTileCap` means the run's tiles plus the tiles the player already had resident will thrash
  the LRU: `evict()` sorts the whole map and drops the excess every two seconds
  (`wm/map/MapService.java:428-447`) [VERIFIED], and each eviction of a dirty tile is a gzip of 1 MiB on the
  scanner thread. Raising `cpuTileCap` to ~128 for the duration of a run, or simply documenting that a large
  run wants a higher cap, removes it. Nothing needs redesigning.
* `gpuTileCap` is 48 (`WorldMapConfig.java:54`) [VERIFIED], below the 49–64 tiles a fully-zoomed-out view of
  an N = 100 result needs, so the outermost tiles would flicker black at maximum zoom-out. That is already a
  documented behaviour of the mod ("Known limits" in the README) and it is a one-line config change.

### 5.2 The world save is the real cost

This is the number that should decide the cap. Measured: **8.2 KB per FULL chunk** in the region file, plus
≥ 4 KB per halo proto-chunk, plus entity and POI files (I measured 537 KB `entities/` and 259 KB `poi/`
against a 21.8 MB `region/` in the same world — about 3.6 %) [VERIFIED, measured].

| N | region growth | + entities/poi | total, vanilla |
|---:|---:|---:|---:|
| 4 | ~4 MB | ~0.1 MB | ~4 MB |
| 8 | ~7 MB | ~0.3 MB | ~7 MB |
| 16 | ~16 MB | ~0.6 MB | ~17 MB |
| 32 | ~47 MB | ~1.7 MB | ~49 MB |
| 64 | ~157 MB | ~6 MB | ~163 MB |
| **100** | **~360 MB** | **~13 MB** | **~373 MB** |

All projections except the shape of the per-chunk constant. On a modded MineColonies world expect
noticeably more per chunk. **And it is permanent**: generated terrain is written to the save, cannot be
un-generated, and will not pick up any future worldgen changes.

### 5.3 Heap during the run

Bounded only by the unload pass, which is why §3.1 is the critical finding. With the server ticking
normally, a `PLAYER_SPAWN` ticket at level 33 expires 20 ticks after the chunk settles, `processUnloads`
drops it, and steady-state residency is roughly `in-flight × (1 + halo)`, i.e. tens of chunks — nothing. With
the server paused, residency is the whole run. [The ~0.5 MB/chunk figure comes from the crashed probe's
"Process Resident Size (MiB): 2544.84" against ~4 000 forced chunks + halo — VERIFIED as an observation,
[UNVERIFIED] as a per-chunk constant, since I did not attribute the heap.]

---

## 6. Proposed design

Classes and thread boundaries only; no code.

```
                    client thread                 |  server (integrated) thread   |  scanner thread
  ------------------------------------------------+-------------------------------+------------------
  WorldMapScreen                                  |                               |
    "Generate radius N" button                    |                               |
    progress bar + ETA + Cancel                   |                               |
    isPauseScreen() == false while running        |                               |
        |                                         |                               |
        v                                         |                               |
  GenerationRun  (new, one per active run)        |                               |
    - target dimension + ServerLevel identity     |                               |
    - row-major cursor over the square (§4.4)     |                               |
    - AtomicInteger completed, volatile State     |                               |
    - in-flight set, bounded at M                 |                               |
    - previousRow: List<LevelChunk> (north refs)  |                               |
        |                                         |                               |
        |  each client tick, while in-flight < M: |                               |
        |     server.execute( submit K chunks ) ----->  addTicketAndLoadWithRadius |
        |                                         |     (PLAYER_SPAWN, pos, 0)    |
        |                                         |          |                    |
        |                                         |          v  future completes   |
        |                                         |     getChunkNow(x,z), (x,z-1)  |
        |                                         |     ColumnScanner.scan(...)    |
        |                                         |          |                    |
        |  <---- completed.incrementAndGet() ------          +--- 256 ARGB ints ------> MapService.acceptPixels
        |                                         |                               |        (tile write,
        |                                         |                               |         existing path)
```

**New pieces, three of them:**

* **`GenerationRun`** (in `wm/map/`) — owns the cursor, the in-flight bound, the counters and the cancel
  flag. Created by the screen, held by `MapService` so it survives the screen closing, destroyed on cancel
  or completion. Knows nothing about rendering.
* **`ServerChunkSource` accessor** (a few static helpers, in `wm/map/`) — the only place that touches
  `Minecraft#getSingleplayerServer`, `getLevel`, `getChunkSource`. Returns `Optional.empty()` on a
  multiplayer server or a published LAN world, which is how the button greys itself out.
* **A second entry point on `MapService`** — `acceptScannedChunk(worldKey, dimension, blockX, blockZ,
  int[] colours)`, taking finished pixels rather than a `LevelChunk`. It reuses `resident(...)`,
  `hasChunk(...)` and `writeChunk(...)` exactly as `runScan` does today (`wm/map/MapService.java:368-400`),
  with the same world-key guard. The existing chunk-carrying `ScanJob` path is untouched.

**Thread rules, stated as invariants:**

1. Nothing on the client thread ever calls a blocking chunk API.
2. Nothing on the server thread ever calls `ChunkSource#getChunk` (the blocking one) or
   `ServerChunkCache#getChunkFuture` (which blocks when called from the server thread).
3. Ticket submission and `getChunkNow` happen **only** on the server thread.
4. `ColumnScanner#scan` for generated chunks happens **on the server thread** (§4.3), and only after the
   chunk's `FULL` future has completed; the scanner thread receives `int[256]`, never a `LevelChunk`.
5. Everything the render thread reads is `volatile` or an `Atomic*`.
6. `isPauseScreen()` returns `false` for as long as a run exists.

**Configuration additions:** `generateRadiusDefault` (16), `generateRadiusMax` (32 by default; the code
clamps whatever the file says to 64), `generateChunksInFlight` (8), `generateTicketsPerTick` (4).

---

## 7. Risks that would sink a naive implementation

Ordered by how likely I think they are to actually happen.

**R1 — Generating with the map screen open (i.e. with the server paused).** §3.1. No unloads, no ticket
expiry, unbounded heap growth. The symptom is a client that gets slower and slower and then dies, with
nothing in the log pointing at the map mod. **This is the one that will be shipped by accident**, because
the obvious place to put the button is the screen that pauses the game.

**R2 — Using the blocking `getChunk`.** §1.3, demonstrated: sixty seconds of frozen tick and a watchdog
kill in *vanilla*, with no mod involved. On a client there is no watchdog, so the symptom is instead a
hard hang with no crash report. The trap is that `ServerChunkCache#getChunkFuture` *also* blocks when called
from the server thread — the safe-looking method is only safe from the wrong-looking thread.

**R3 — Reading a chunk before it is FULL.** §4.2. Racing the worldgen workers on a `ProtoChunk` is the one
place where "both are `LevelChunk`, it's fine" produces corrupt pixels or an exception in a stack frame
nobody will connect to the map. Guard: only ever read from the `FULL` future's completion.

**R4 — Submitting all the tickets at once.** `DistanceManager#runAllUpdates` propagates *unbounded*
(`DistanceManager.java:68`) [VERIFIED]. 40 401 tickets in one tick is a multi-second stall before a single
chunk has generated.

**R5 — `TicketType.UNKNOWN`.** §1.1. It expires after one tick and vanilla itself refuses to use it for
asynchronous loads. Symptom: futures that complete with an unloaded result under load, i.e. a run that
mysteriously skips chunks on a slow machine and not on a fast one.

**R6 — No cancellation on world/dimension change.** The existing world-key guard in `runScan` protects the
*pixels* (`MapService.java:370-376`) [VERIFIED], but a run that keeps submitting into a `ServerLevel` the
player has left keeps generating terrain the player did not ask for, in the background, forever.

**R7 — Promising an undo.** There is none. If the UI has a Cancel button next to a progress bar, players
will read it as "put it back". The confirmation before the run has to state the disk cost.

**R8 — Not turning the feature off for LAN.** `getSingleplayerServer()` is non-null for a published world;
generating 40 000 chunks while guests are connected is a denial of service on your own friends. Gate on
`!isPublished()`.

**R9 — Row-major order without holding the previous row.** §4.4. Every chunk in the sweep gets a permanently
flat northern row, and because chunks are never re-scanned it is unfixable afterwards. The map would come
out visibly striped.

---

## 8. Recommended cap

**Default N = 16. Hard cap N = 32 in the UI. N = 64 only by editing `config/worldmap.properties`. Do not
offer N = 100.**

The reasoning, in the order I weight it:

1. **Disk is the binding constraint, and it is irreversible.** N = 16 costs ~17 MB, N = 32 ~49 MB, N = 64
   ~163 MB, N = 100 ~373 MB (vanilla; more with mods). A map feature has no business quietly adding a third
   of a gigabyte of terrain to somebody's save. Every step in N doubles the side and quadruples everything.
2. **N = 32 is already a real map.** 65 × 65 chunks = 1040 × 1040 blocks — four map tiles, and at the
   default 1 px per block it a little more than fills the width of a 1080p window at GUI scale 2. That is a
   large map by any reasonable reading of "a large map of the world without having to walk it".
3. **Time is not actually the limiting factor, and I want to be honest about that.** My projection puts
   N = 100 at ~5 minutes on a dedicated box and 10–60 minutes on a real modded client. That is tolerable if
   you are willing to leave the game running. It is the disk and the irreversibility that make it a bad
   default, not the clock.
4. **The failure modes are all superlinear in N.** Heap residency, LRU thrash, ticket-propagation cost and
   the blast radius of any of R1–R9 all scale with the area. A cap of 32 keeps every one of them in a range
   where a bug is an annoyance rather than a corrupted save.
5. **If the number 100 must exist**, put it behind the config file with the projected disk cost written in
   the comment next to it, and make the in-game confirmation dialog show the estimate for the chosen N
   before the run starts. Numbers in a dialog are cheap; a 400 MB surprise is not.

---

## 9. What I could not verify

* **Anything about the actual client.** I never ran Minecraft. Every claim about how a generation run
  behaves *inside the client process* — the 2–3× wall-clock penalty from sharing cores with rendering, the
  interaction between a non-pausing map screen and the client's own frame pacing, whether the progress bar
  stays smooth — is reasoning from source, not observation. [UNVERIFIED]
* **Modded worldgen cost.** The 2–5× multiplier for a MineColonies + Structurize world is folklore, not a
  measurement. I did not load this repository's mods into the probe server. [UNVERIFIED]
* **Snapshot skew.** Measurements are 26.3-snapshot-9; the mod targets snapshot-10; line citations are from
  `/opt/mc-src-26.3`, which matches neither exactly (the crash report's `ServerLevel.setChunkForced` is at
  :1445, mine is at :1517). [UNVERIFIED that generation behaviour or speed is identical.]
* **`RADIUS_AROUND_FULL_CHUNK = 11`** is inferred from a measured 42×42 persisted region around a 16×16
  level-31 ticket, which gives `MAX_LEVEL = 44`. I did not evaluate
  `ChunkPyramid.GENERATION_PYRAMID.getStepTo(FULL).accumulatedDependencies().getRadius()` at runtime.
  Confirming it means bootstrapping the registries in a standalone JVM. [UNVERIFIED as a source-derived
  constant; VERIFIED as a measurement.]
* **`PalettedContainer#createOrReuseData` returning the old `Data`** (`:79-83`) and then `copyFrom` running
  in place — whether that path is reachable while a reader holds the same `Data` reference. I stopped at
  noticing it. Confirming means tracing every `onResize` caller. [UNVERIFIED]
* **The 0.5 MB-per-resident-chunk heap figure.** Taken from one crash report's resident size divided by an
  approximate chunk count. Order of magnitude only. [UNVERIFIED]
* **Per-chunk disk size on varied terrain.** 8.2 KB/chunk is one 196-chunk sample on one seed at one
  location. Ocean is smaller, jungle and structure-dense terrain larger. [UNVERIFIED as a general constant.]
* **Whether the completion callback of `addTicketAndLoadWithRadius`'s future runs on the server thread.**
  I assumed the design must not rely on it and must re-dispatch with `server.execute` before calling
  `getChunkNow`. Whether the callback happens to already be on the right thread, I did not trace.
  [UNVERIFIED — and the design is written so that it does not matter.]
* **Fabric's `ServerTickEvents` from a client-only entrypoint.** I proposed driving the pump from
  `ClientTickEvents` + `server.execute` specifically so this question never arises. Whether registering a
  common-side Fabric lifecycle event from a `"environment": "client"` mod is supported, I did not check.
  [UNVERIFIED]

---

## 10. Considered and rejected

**Reading the save's region files directly instead of generating anything.** In single player the world is a
directory on the same disk. A client-only mod could walk `region/*.mca`, decode the chunk NBT and produce
map pixels for every chunk the world has *ever* generated — with zero generation, zero tick cost and zero
disk growth. That is strictly better for the common complaint "I explored this before I installed the map
mod and it's all black". It does not satisfy the actual request (it cannot show ground nobody has ever
generated), and decoding a chunk outside a `ServerLevel` needs `SerializableChunkData`, whose usability from
client code I did not check [UNVERIFIED]. **Worth a study of its own; it is the cheaper half of what the
user wants.**

**`ServerChunkCache#addTicketAndLoadWithRadius(type, pos, N)` with a large radius.** One call, whole square,
looks perfect. It is not: the radius becomes a *ticket level* of `33 − N` (`TicketStorage.java:138-141`)
[VERIFIED], which goes negative for anything past 33, and `ChunkMap#getChunkRangeFuture` materialises all
`(2N+1)²` chunk holders and futures at once (`ChunkMap.java:300-325`) [VERIFIED]. Fine for vanilla's
radius-of-11 spawn chunks; catastrophic at 100. Radius 0, one chunk per call, is the usable form.

**Copying every `LevelChunkSection` before scanning.** Removes the residual read race, but a 24-section copy
per chunk costs more than the scan it protects and allocates megabytes per second on the server thread.
Scanning on the server thread (§4.3) gets the same guarantee for a few hundred microseconds.

**Driving the run from `ServerTickEvents` on the integrated server.** Cleaner in principle — the pump would
run exactly where the work happens. Rejected because it puts a common-side event registration into a
`"environment": "client"` mod for no benefit that `server.execute()` from the client tick does not already
give.

**Raising `chunksPerTick` to absorb the sweep.** Irrelevant: that budget governs how many *client* chunk
positions are resolved per tick (`wm/map/MapService.java:226`) [VERIFIED], and generated chunks never enter
that path at all.
