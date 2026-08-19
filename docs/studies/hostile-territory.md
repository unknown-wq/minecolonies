# Hostile territory, and rectangle mode for the land-claim scepters

Study, not an implementation. Date: 2026-08-15. Tree: `26.2/` in this repo, compared against the
NeoForge original at `1.21.1/` and upstream MineColonies at `/workspace/ldtteam/minecolonies`
(`2d453335`).

Evidence standard, same as [`26.2/PLANES-AIR-DEFENCE.md`](../../26.2/PLANES-AIR-DEFENCE.md):

* **[VERIFIED]** — I read the source, the `file:line` is real and says what I claim it says.
* **[UNCHECKED]** — inference from the code, not observed. **There is no game client in this
  environment and nothing here was play-tested.** Every runtime claim is [UNCHECKED] by that fact
  alone; I mark the ones where the inference is more than a step long.

All paths are relative to the repository root. `26.2/src/main/java/com/minecolonies/` is abbreviated
to `mc/` below where it would otherwise crowd the line.

---

## 0. Verdicts first

| Feature | Verdict | Single biggest obstacle |
|---|---|---|
| **Rectangle mode** for the claim/unclaim/border scepters | **BUILD.** Genuinely small — half a day, and most of the pieces already exist in this repo. | `ColonyView` re-serialises the *entire* claim map into every colony-view packet, so area is bounded by network cost, not CPU. Needs a hard cap. |
| **Hostile territory scepter** | **BUILD, narrowly and in stages** — but as a *flagged shell colony*, which is design 1, not as the separate ownership layer of design 2. | Ownership is one `int` per chunk plus one 256-bit mask. A hostile territory and the player's colony can therefore **never interleave inside a single chunk**: they can abut on the chunk grid, or with a strip of no-man's-land between, and nothing else. |

The counter-intuitive result of this study is that **design 2 (a separate ownership layer) is the
expensive one**, and design 1 (a colony with no town hall) is the cheap one. Section B says why, with
the list of what actually breaks.

---

## A. The ownership layer as it stands

### A.1 Where the answer lives

There are exactly two pieces of state.

**The per-chunk claim**, `mc/api/colony/claim/ChunkClaimData.java`:

* `owningColony` — one `int`, `NO_COLONY_ID` (0) when nobody owns it
  (`ChunkClaimData.java:47`). **[VERIFIED]**
* `colonies` — a set of ids that have a *static* claim near the chunk (`:42`).
* `claimingBuildings` — colony id → set of building anchors that claim the chunk (`:52`).
* `claimedColumns` — `long[4]`, or `null`, the 256-bit column mask the border scepter writes
  (`:60`). Bit `(z & 15) * 16 + (x & 15)`, computed in `columnBit` (`:222`). Null is the normal
  state and means "the whole chunk". **[VERIFIED]**

**The global index**, `mc/core/colony/ColonyManager.java:98`:

```java
private Map<ResourceKey<Level>, Long2ObjectMap<ChunkClaimData>> chunkClaimData = new HashMap<>();
```

Read through `ColonyManager.getClaimData(dimension, pos)` (`:928`), which is a
`computeIfAbsent` on a plain `HashMap` wrapping a `Long2ObjectOpenHashMap`. **This is a mutating
read on a non-thread-safe map**, which matters in section E. **[VERIFIED]**

### A.2 The four questions, and which of them sees the column mask

`mc/api/util/ColonyUtils.java` is the front door:

| Method | Line | Granularity |
|---|---|---|
| `getOwningColony(ChunkAccess)` | `ColonyUtils.java:177` | whole chunk — **ignores the mask** |
| `getOwningColony(ResourceKey, ChunkPos)` | `:196` | whole chunk — ignores the mask, and loads no chunk |
| `getOwningColony(ChunkAccess, BlockPos)` | `:213` | **column-precise**, returns `NO_COLONY_ID` for a cut-out column |
| `getAllClaimingBuildings` / `getStaticClaims` / `getChunkCapData` | `:228`, `:241`, `:251` | whole chunk |

**Only four call sites in the whole mod use the column-precise form.** **[VERIFIED]**

* `ColonyManager.getColonyByPosFromWorld` — `ColonyManager.java:311`, via `:318`
* `ColonyManager.getColonyView(Level, BlockPos)` — `:456`, via `:460`
* `Colony.isCoordInColony` — `mc/core/colony/Colony.java:1708`, via `:1717`
* `ColonyView.isCoordInColony` — `mc/core/colony/ColonyView.java:1035`, via `:1038`

Everything else is chunk-granular. That is the practical resolution of the ownership layer: **a
hand-painted border is only visible to protection, to `isCoordInColony`, and to the border
renderer.** Raid spawning, chunk tickets, JourneyMap, subscriber management and the pvp guard
handler all still see whole chunks. **[VERIFIED]**

### A.3 Who asks, and what they get out of it

This is the payoff list — every one of these is a mechanic a hostile territory inherits for free if
it can answer the ownership question. All **[VERIFIED]** by reading the call site.

**Protection and permissions**

| Asker | Site | What it gates |
|---|---|---|
| `ColonyPermissionEventHandler.onBlockBreak` | `mc/core/colony/permissions/ColonyPermissionEventHandler.java:338` | `BREAK_BLOCKS` / `BREAK_HUTS` |
| `…onBlockInteract` | `:349` | `ACCESS_HUTS`, chest access, decoration controllers |
| `…onItemUse` | `:429` | `THROW_POTION`, scan tool |
| `…isActionDenied` | `:497`–`:519` | the generic permission gate; every action funnels here |
| `…onAttackEntity` | `:554` | attacking citizens / guards inside a claim |
| `Permissions.addPlayer` | `mc/core/colony/permissions/Permissions.java:834` | whether a newly added player becomes a close subscriber |

Note the permission fallback: an unknown player gets `NEUTRAL_RANK_ID`
(`Permissions.java:790`–`:793`), and the pvp system already has a `HOSTILE` rank
(`mc/api/colony/permissions/IPermissions.java:26`, `Rank.java:93`). **[VERIFIED]**

**Player presence / subscriptions**

| Asker | Site | What it does |
|---|---|---|
| `EventHandler.onEnteringChunk` | `mc/core/event/EventHandler.java:302`, `:331`, `:343` | adds the player as visiting player + close subscriber of the chunk's owner; alerts claiming buildings |
| `EventHandler.playerChangeDim` | `:270`, `:286` | same, across dimensions |
| `ColonyPackageManager.updateClosePlayers` | `mc/core/colony/managers/ColonyPackageManager.java:130` | drops a subscriber who has left the claim |

This is how a colony's data reaches a client at all, and it is entirely claim-driven. **[VERIFIED]**

**Citizens and raiders**

| Asker | Site | What it does |
|---|---|---|
| `EventHandler.onEnteringChunkEntity` | `EventHandler.java:469` | pvp mode: a guard entering another colony's claim is registered as an attacker |
| `AbstractEntityMinecoloniesRaider.onEnterChunk` | `mc/api/entity/mobs/AbstractEntityMinecoloniesRaider.java:359` | a raider crossing a foreign claim triggers `setPassThroughRaid()` on that colony |
| `RaidManager.isOtherColony` | `mc/core/colony/events/raid/RaidManager.java:770` | refuses a raid spawn point inside someone else's claim |
| `EntityCitizen.die` | `mc/core/entity/citizen/EntityCitizen.java:1644` | a grave is only created inside the colony; outside, the inventory is dropped |
| `GeneralEntityWalkToProxy.getWayPoints` | `mc/core/entity/pathfinding/proxy/GeneralEntityWalkToProxy.java:36` | waypoint-assisted walking only applies inside a claim |
| `PathJobPathway.computeHeuristic` | `mc/core/entity/pathfinding/pathjobs/PathJobPathway.java:56` | **dead code**; see E.1 |

**Chunk loading and colony bookkeeping**

| Asker | Site |
|---|---|
| `ChunkDataHelper.loadChunk` / `unloadChunk` | `mc/core/util/ChunkDataHelper.java:46`, `:65` |
| `Colony.registerClaimedChunkTickets` / `releaseTicketsOnLostGround` | `Colony.java:743`, `:804` |
| `ChunkDataHelper.BuildingClaimGuard.owns` | `ChunkDataHelper.java:390` — the `maxoutlyingchunks` budget |
| `ChunkDataHelper.canClaimChunksInRange` | `:139` — the founding test |
| `BackUpHelper` | `mc/core/util/BackUpHelper.java:455` |
| `WorkManager` | `mc/core/colony/workorders/WorkManager.java:441` |

**Building placement and world interaction**

`AbstractBlockHut.getColony` (`mc/api/blocks/AbstractBlockHut.java:277`, `:318`),
`AbstractColonyBlock` (`:223`, `:295`), `BlockScarecrow`, `BlockPlantationField`,
`BlockDecorationController`, `BlockMinecoloniesGrave`, `BlockMinecoloniesRack`,
`MinecoloniesCropBlock`, `TileEntityColonyBuilding:188`, `TileEntityScarecrow:74`,
`TileEntityColonyFlag:67`, `ItemFieldStick` (three sites), `ItemSupplyChestDeployer:253`,
`ItemSupplyCampDeployer:219`, `SurvivalHandler:241`/`:328`, `DirectPlaceMessage:104`,
`PickupBlockMessage:60`, `ReactivateBuildingMessage:69`,
`AbstractTileEntityRack:158` (`isCoordinateInAnyColony`). **[VERIFIED]** by grep + spot reads.

**Rendering and maps** — section D.

**Aircraft** — `AircraftWatch.java:121`, `AntiAirBattery.java:277` and `:724`. Section E.3.

**Commands** — `CommandShowClaim.java:68`, `ColonyChunkReport.java:123`,
`CommandClaimChunks`, `CommandReclaimChunks`, `CommandForceLoadClaims`.

### A.4 What the 26.2 port added over 1.21.1

The whole column mask is **port-original**. `1.21.1/src/main/java/com/minecolonies/api/colony/claim/ChunkClaimData.java`
has no `claimedColumns`, no `isColumnClaimed`, no `TAG_PARTIAL_CLAIM`; grepping `isColumnClaimed`
across `1.21.1/` returns nothing. **[VERIFIED]**

So are the three scepters. `1.21.1/…/core/items/` has `ItemScepterBeekeeper`, `ItemScepterGuard`,
`ItemScepterLumberjack`, `ItemScepterPermission`, `ItemPharaoScepter` — and no
`ItemScepterClaim`, `ItemScepterUnclaim` or `ItemScepterBorder`. Upstream MineColonies at
`2d453335` has the same five and no others. **[VERIFIED]**

**Upstream has never shipped anything resembling either feature.** No hostile/neutral territory
concept, no rectangle claim, no sub-chunk claim. There is no cheap oracle here; both features are
this port's own inventions to design. **[VERIFIED]**

---

## B. Can a territory exist without being a colony?

### B.1 Design 1 — the shell colony

A real `Colony` registered in `ServerColonySaveData`, with a centre, an unclaimable random-UUID
owner, no town hall, no buildings, no citizens.

**What is already null-tolerant / gated, so does not break:**

| Concern | Evidence |
|---|---|
| Citizen spawning | `CitizenManager.java:612` and `:621` both gate on `hasTownHall()`. No town hall → the respawn timer and the move-in path never fire. **[VERIFIED]** |
| Visitors | `VisitorManager.java:236`, `:300`, `:343` all gate on `hasTownHall()`. **[VERIFIED]** |
| Reproduction | `ReproductionManager.java:248` only reached with existing citizens. **[VERIFIED]** |
| Happiness | `Colony.getOverallHappiness()` returns a hard-coded `5.5` at zero citizens (`Colony.java:1858`). **[VERIFIED]** |
| Event descriptions | `EventDescriptionManager.java:58`–`:65` explicitly branches on a null town hall. **[VERIFIED]** |
| Raids **against** it | `RaidManager.canRaid()` requires `!getImportantColonyPlayers().isEmpty()` (`RaidManager.java:929`–`:934`). A colony nobody owns has none. Also `calculateSpawnLocation` aborts with a log line when there are no loaded buildings (`:635`). **[VERIFIED]** |
| Request manager | Created lazily on first `getRequestManager()` (`Colony.java:1756`–`:1764`) and ticked only in `ACTIVE` (`:474`). An empty one is a no-op. **[VERIFIED]** |
| Ticking cost | `updateState()` (`Colony.java:489`) returns `INACTIVE` unless there are close subscribers or important players. `worldTickSlow` (`:564`) — the expensive one — is an `ACTIVE`-only transition (`:478`). A hostile territory nobody is standing in costs one state-machine poll every `UPDATE_STATE_INTERVAL`. **[VERIFIED]** |
| Persistence | Free. The claim map is a field on `Colony` (`Colony.java:412`), written to colony NBT under `TAG_CLAIM_DATA` (`:1387`–`:1395`), read back at `:1242`–`:1250`, and pushed into the global index by `IColonyManager.addClaimData` (`:1250`). Survives reload by construction. **[VERIFIED]** |
| Client sync + rendering | Free. The whole claim map goes out in every colony-view packet (`ColonyView.java:394`–`:400`) and is installed client-side at `:772`–`:785`. A player who walks into the hostile claim becomes a close subscriber via `EventHandler.onEnteringChunk` (`EventHandler.java:331`–`:340`), so the data arrives exactly when it is needed. **[VERIFIED]** |
| Protection | Free, at `NEUTRAL` rank (`Permissions.java:790`–`:793`). |
| Deletion | `ColonyManager.deleteColony` (`:160`) iterates citizens and buildings — both empty — then unregisters. No special case needed. **[VERIFIED]** |

**What actually breaks, and it is a short list:**

1. **`getClosestColony` starts answering with the shell.** `ColonyManager.getClosestColony`
   (`:514`) short-circuits on the chunk owner before doing any distance search. Inside hostile
   ground it returns the shell, so `ItemScepterClaim.getColony` (`ItemScepterClaim.java:219`),
   `ItemScepterBorder.closestColony` (`ItemScepterBorder.java:311`) and `CreateColonyMessage`
   (`CreateColonyMessage.java:101`) all act on the wrong colony near the border. **[VERIFIED]** —
   this is the one genuine defect, and it is what forces the design to carry a flag.
2. **`AbstractEntityMinecoloniesRaider.onEnterChunk` calls `tempColony.getRaiderManager()` with no
   null guard** (`AbstractEntityMinecoloniesRaider.java:358`–`:364`). Against a *real* shell colony
   this is fine (the object exists). It is listed because it is fatal to design 2 — see B.2.
   **[VERIFIED]**
3. **`Colony.getCenter()` must be real.** It is `final` (`Colony.java:321`), set at construction,
   and used by `BuildingClaimGuard` (`ChunkDataHelper.java:287`), by
   `ItemScepterBorder.releaseIfEmpty` to protect the centre chunk (`ItemScepterBorder.java:287`),
   by `AircraftWatch` sighting radius (`AircraftWatch.java:130`) and by `AntiAirBattery.visible`
   (`AntiAirBattery.java:359`). A hostile territory needs a nominal centre — the position of the
   first painted column is fine. **[VERIFIED]**
4. **`ItemScepterUnclaim` and `ItemScepterBorder` refuse to release the centre chunk**
   (`ItemScepterUnclaim.java:185`, `ItemScepterBorder.java:287`). Correct behaviour, but it means a
   hostile territory can never be fully erased with the scepter; deletion needs a command.
   **[VERIFIED]**
5. **Cosmetic**: the shell appears in `getColonies(level)`, so `AircraftWatch.tick`
   (`AircraftWatch.java:95`) and `AntiAirBattery.tick` (`AntiAirBattery.java:288`) iterate it. Both
   loops then find no guard towers and do nothing. Cheap, but it means an *enemy* territory would
   be "warned" about the player's aircraft, which is nonsense text nobody receives (no managers).
   **[VERIFIED]**

**The fix for all of it is one bit.** A `boolean hostile` on `Colony`, saved in NBT, mirrored into
`ColonyView`, plus a helper `IColony#isHostile()`. Then:

* `getClosestColony` / `getClosestColonyView` skip hostile colonies when *searching*, but still
  return them when the chunk is owned — or, more simply, the three scepters and
  `CreateColonyMessage` ask `isHostile()` and refuse. Either shape is a handful of lines.
* the renderer picks a distinct colour (section D),
* pathfinding, no-fly and the spawner key off it (section E),
* `AircraftWatch`/`AntiAirBattery` skip it.

**No mixin anywhere.** Everything above is mod-internal code.

### B.2 Design 2 — a separate ownership layer

Hostile areas in their own `SavedData`, with the "who owns this position" lookup taught to consult
both.

**Persistence** is not the problem: `IServerColonySaveData.getOrComputeSaveData`
(`mc/api/colony/savedata/IServerColonySaveData.java:73`–`:79`) is a plain
`level.getDataStorage().computeIfAbsent(TYPE)`, and a second `SavedData` of the same shape is
routine. **[VERIFIED]**

**The lookup path is the problem, and it is fatal.** The ownership answer is an `int`, and
essentially every consumer immediately resolves it through `IColonyManager.getColonyByWorld(id,
level)` and then calls methods on the result. There are two ways to plug a hostile layer in and
both are bad:

* **Smuggle hostile ids into `owningColony`.** Then `getColonyByWorld` returns `null` for them, and
  every unguarded dereference becomes an NPE. `AbstractEntityMinecoloniesRaider.java:361` is the
  proof it happens: `tempColony.getRaiderManager().setPassThroughRaid()` with no null check.
  **[VERIFIED]** There will be more; that one was found by reading, not by searching exhaustively.
  **[UNCHECKED]** how many.
* **Keep hostile ownership in a parallel map and teach each caller.** Then you inherit *nothing*.
  Protection (6 sites), subscriber management (3 sites), chunk tickets, JourneyMap, the border
  renderer, the pvp guard handler, `isCoordinateInAnyColony`, and every block/tile-entity site in
  A.3 would each need a second question added. That is roughly **50 call sites**, against **one
  boolean** for design 1.

Plus a new client sync path (a new packet, a new client-side map, keyed by dimension), where design
1 rides the existing `ColonyView` for free.

### B.3 Recommendation

**Design 1, with a `hostile` flag.** It is the cheaper design by a wide margin, it inherits
protection, persistence, client sync, chunk tickets and border rendering unchanged, and its entire
cost is the flag plus the four or five places that must stop treating a hostile territory as a
neighbour colony.

The one thing design 1 does *not* give you, and design 2 would not either, is the ability to
interleave with the player's colony inside a chunk. That is a property of the data model, not of
either design. See C.2.

---

## C. The adjacency rule

### C.1 What the minimum-distance rule actually is

* **Config key**: `minColonyDistance`, default 8, range 1–200, in the `claims` category —
  `mc/api/configuration/ServerConfiguration.java:375` (declared `:246`). **[VERIFIED]**
* **Enforcement site**: `ColonyManager.isFarEnoughFromColonies`, `ColonyManager.java:333`. It takes
  `max(minColonyDistance, initialColonySize) << 4` blocks (`:335`), measures from the *closest
  colony's centre*, and then additionally requires
  `ChunkDataHelper.canClaimChunksInRange(world, pos, initialColonySize)` — every chunk in an
  `initialColonySize`-radius square must have `owningColony == 0` (`ChunkDataHelper.java:123`–`:146`).
  **[VERIFIED]**
* **Callers**: `CreateColonyMessage.java:150` (the real gate), `GetColonyInfoMessage.java:80` (the
  build-tool preview), `SurvivalHandler.java:114`, `ClientEventHandler.java:432`. **[VERIFIED]**

**The critical fact: this is a colony-*founding* rule, not a land rule.** Nothing in the claiming
path consults it. `ItemScepterClaim` documents the omission in so many words
(`ItemScepterClaim.java:101`–`:104`): *"Deliberately no distance test against maxColonySize … the
scepter is the manual override, so its only rule is that the chunk has no owner."* `tryClaim`
(`ChunkDataHelper.java:434`) asks nothing about distance either. **[VERIFIED]**

### C.2 Which rule a hostile territory violates

**Under design 1, none — provided it is not created through `CreateColonyMessage`.**

If a hostile territory is created by an admin command or a scepter that calls
`IServerColonySaveData.createColony` directly and *skips* `ChunkDataHelper.claimColonyChunks`
(which `ColonyManager.createColony` does at `:138`), it starts with an empty claim and no distance
test has ever been run on it. Painting it in with the border scepter then goes through `tryClaim`
with `forceOwnerChange = false`, which cannot take a chunk from anybody. It can be painted right up
to the player's border. **[VERIFIED]** by reading; **[UNCHECKED]** in game.

The knock-on: `isFarEnoughFromColonies` will afterwards refuse to let the player found a *new*
colony near the hostile territory, because `getClosestColony` returns it. If that is unwanted, the
`hostile` flag suppresses it in the same place. **[VERIFIED]**

**The real constraint is not distance, it is granularity.** A chunk has one `owningColony` and one
`claimedColumns` mask, and the mask belongs to that owner (`ChunkClaimData.java:47`, `:60`). So:

* A hostile chunk and a player chunk can share an edge — borders touch exactly on the chunk grid.
  ✔
* A single chunk cannot be half hostile and half the player's. The mask can only carve columns
  *out* of the one owner's claim, and a carved-out column belongs to nobody
  (`ColonyUtils.java:213`–`:226`). ✔ for a no-man's-land strip, ✘ for interleaving.

If the owner's mental picture is "the enemy border runs diagonally through the field behind my
warehouse", that works only if the enemy owns those chunks outright and the player's colony stops
at the chunk line. **[VERIFIED]**

### C.3 What happens today when two claims fight

There is **no conflict resolution path**, only first-come-first-served plus some defensive skipping.

* `ChunkClaimData.addColony` (`:74`–`:90`): the id joins the `colonies` set; `owningColony` is only
  reassigned if it is currently `NO_COLONY_ID` **or** the current owner no longer resolves to a live
  colony. An existing owner keeps the chunk. **[VERIFIED]**
* `ChunkClaimData.removeColony` (`:92`–`:114`): on losing the owner, the replacement is
  `claimingBuildings.keySet().iterator().next()` or `colonies.iterator().next()` — **arbitrary hash
  order**. **[VERIFIED]**
* `ChunkDataHelper.tryClaim` has a `forceOwnerChange` flag (`:456`–`:463`) that *does* steal a
  chunk. Every scepter passes `false` (`ItemScepterClaim.java:125`, `ItemScepterBorder.java:251`,
  `ItemScepterUnclaim.java:157`). Only `staticClaimInRange` can be asked to force. **[VERIFIED]**
* `ItemScepterClaim.isOwnedByAnotherColony` (`:158`) skips owned chunks outright, so a click on the
  edge of someone else's land leaves no trace in it. **[VERIFIED]**
* At **column** level there is nothing to resolve: one mask, one owner. Two colonies cannot fight
  over columns of the same chunk because only one of them can hold the chunk at all. **[VERIFIED]**

Practical consequence for hostile territory: a hostile claim can never take ground the player
already owns, and vice versa, unless something explicitly passes `forceOwnerChange = true`. That is
the right default and I would not change it.

---

## D. Client-side rendering

### D.1 How borders are drawn today

`mc/core/client/render/worldevent/ColonyBorderRenderer.java`. **[VERIFIED]** throughout.

* Gated on the held item: Structurize's build tool, or one of the three scepters
  (`showsBorders`, `:320`–`:327`), and on `ctx.hasNearestColony()` (`:69`) —
  `WorldEventContext.nearestColony` is `getClosestColonyView(level, player)`
  (`WorldEventContext.java:74`).
* Walks the chunks around the player, reads `IColonyManager.getClaimData(dimension, chunkPos)`
  (`:98`) and builds `Map<ChunkPos, Integer>` — **chunk → owning colony id** (`:111`).
* Chunks with a mask are diverted: put into `partialMap`, and marked `NO_COLONY_ID` in the chunk map
  so their whole-chunk neighbours draw an edge against them (`:101`–`:108`). Their real outline is
  drawn column by column in `drawPartialClaims` (`:411`–`:477`), which asks
  `isColumnOwnedBy` (`:491`) per column.
* Colour comes from `borderColour` (`:373`–`:393`): with `colonyteamborders` on, the colony view's
  `getTeamColonyColor()`; otherwise **white for your colony, `(70,70,255)` for anybody else's**.
  Falls back to `ChatFormatting.RED` when the view is unknown (`:385`).
* Geometry is cached and invalidated by `claimSignature` (`:342`–`:360`), a hash over
  `getOwningColony() * 257 + getClaimedColumnCount()` for a 5×5 chunk window.

### D.2 Can it show a second, hostile-coloured border?

**Yes, with essentially no work under design 1.** **[VERIFIED]** by construction:

* The renderer is keyed on an `int` colony id from the claim data. A shell colony's id is just
  another id.
* It already draws foreign borders in a different colour. A hostile territory would appear in blue
  (or its team colour) the moment the player holds a scepter inside it — with **no renderer change
  at all**.
* To give it a *dedicated* colour, `borderColour` (`:381`) resolves an `IColonyView` by id; add one
  `if (colony != null && colony.isHostile()) return HOSTILE_COLOUR;`. One line.
* The column mask already renders (`drawPartialClaims`), so a hand-painted hostile border draws at
  column precision for free.

**Two caveats.**

1. **The data must reach the client.** Claim data is only installed client-side from
   `ColonyView.deserializeNetworkData` (`ColonyView.java:772`–`:785`), i.e. only for colonies the
   player is subscribed to. `EventHandler.onEnteringChunk` (`EventHandler.java:331`) makes the
   player a close subscriber of whatever chunk they walk into, so standing *in* the hostile claim
   works — but a hostile border seen from 40 blocks away, from inside your own colony, will only
   draw if the client has that colony's view. **[UNCHECKED]** — I could not test how often this
   bites; it depends on `getImportantColonyPlayers` and the close-subscriber lifecycle. This is a
   pre-existing property of every foreign border, not something hostile territory introduces.
2. **Single-player thread hazard**, pre-existing. `ColonyView.java:783` deliberately skips
   `addClaimData` when `getSingleplayerServer() != null`, so in single player the render thread
   reads the *server's* `chunkClaimData` map — a plain `HashMap` whose `getClaimData` does
   `computeIfAbsent` (`ColonyManager.java:928`). **[VERIFIED]** that the code does this;
   **[UNCHECKED]** whether it has ever misbehaved.

### D.3 The other renderers

* **JourneyMap** — `mc/core/compatibility/journeymap/ColonyBorderMapping.java`. Purely
  colony-id-keyed: `updateChunk` (`:141`) reads `getOwningColony(chunk)` and
  `queueChunks` (`:186`–`:205`) walks the whole per-dimension claim map building one
  `ColonyBorderOverlay` per id, whose label comes from the colony view. A hostile shell colony
  appears automatically, named. **Chunk-granular only — it ignores the mask**, so a partial claim
  already shows as a full chunk on the minimap. **[VERIFIED]**
* **`ItemColonyMap` / `WindowColonyMap`** — `mc/core/client/gui/map/`. Grepping the package for
  `getClaimData`, `ChunkPos` or `border` returns nothing; it draws buildings and citizens, not
  claims. **Not a rendering surface for this feature.** **[VERIFIED]**
* **`TileEntityColonySignRenderer`** — `mc/core/client/render/TileEntityColonySignRenderer.java:51`
  is a `BlockEntityRenderer` for sign text. Nothing to do with borders. **[VERIFIED]**

---

## E. The mechanics he named

### E.1 Citizens refusing to path into hostile ground

**Where the decision lives.** `mc/core/entity/pathfinding/pathjobs/AbstractPathJob.java`:

* `exploreInDirection` (`:1774`) is the per-edge expansion. Walkability is decided by
  `getGroundHeight` (`:2574`) and `isPassable` (`:2674` / `:2797`) — pure block-state questions.
* `computeCost` (`:2126`) prices the step.
* **`modifyCost` (`:2302`, called at `:1939`) is the designed extension point**, and six path jobs
  already override it (`PathJobPathway:106`, `PathJobFindWater:121`,
  `PathJobMoveAwayFromLocation:82`, `PathJobRaiderPathing:156`, `PathJobMoveToWithPassable:53`,
  `PathJobFindTree:195`). It receives `(cost, parent, swimstart, swimming, x, y, z, state, below)` —
  world coordinates included. **This is where an area rule belongs.** **[VERIFIED]**

**The honest cost, and it is a real trap.**

* Pathfinding runs on **one** daemon worker thread (`Pathfinding.java:57`, a
  `ThreadPoolExecutor(1, 1, …)` over a 10 000-slot queue). **[VERIFIED]**
* A job's node budget is `min(MAX_NODES=8000, range²) × pathNodeLimitMultiplier`
  (`AbstractPathJob.java:72`, `:315`, `:330`), and `search()` may exceed it by
  `maxCost² × 2` (`:592`). `modifyCost` runs on **every considered transition**, so on the order of
  10⁴ calls per job, tens of jobs per second across a colony. **[VERIFIED]**
* **`IColonyManager.getClaimData` must not be called from there.** `ColonyManager.java:928` is a
  `computeIfAbsent` on a plain `HashMap` (`:98`) — a *mutating* read, from a thread that is not the
  server thread. **[VERIFIED]** The one class in the tree that does this is
  `PathJobPathway.computeHeuristic` (`:56`), which carries the comment
  `// TODO: Before usage not thread safe chunk/cap access` (`:52`) — and grepping the mod for
  `PathJobPathway` outside its own file returns **nothing**, i.e. it is dead code. **[VERIFIED]**
  Worse, it calls `world.getChunk(x >> 4, z >> 4)`, which on the server generates terrain.

**The design that fits.** Snapshot, do not query.

`AbstractPathJob`'s constructors already build a `ChunkCache` over the search box on the *calling*
(server) thread (`:303`–`:330`, `:373`+). Add a `LongOpenHashSet` of hostile chunk keys over the
same box, filled there. For a 100-block range the box is ~260 blocks ≈ **17×17 = 289 map lookups,
once per job, on the server thread**. In `modifyCost` the test is `((long) (x >> 4) << 32) | (z >> 4)`
plus a hash-set probe — single-digit nanoseconds, and only reached when the set is non-empty (one
`isEmpty()` branch otherwise, so colonies with no hostile neighbour pay nothing measurable).
**[UNCHECKED]** — no profiling was possible here; the reasoning is from the node budget and the
existing `RecentTargetCache` precedent, whose own comment (`AbstractPathJob.java:562`–`:565`)
documents that a `System.currentTimeMillis()` per expanded node was already too expensive.

Column precision would need the four masks of any partial chunk carried alongside — cheap, since a
partial chunk is rare, but for a first version chunk granularity is right.

**Cost, not veto.** A hard `isPassable == false` over an area risks a citizen with no route at all,
which lands in `PathingStuckHandler` and eventually a teleport. A large multiplier in `modifyCost`
(the same shape `PathJobMoveAwayFromLocation` already uses) makes citizens walk around hostile
ground when they can and through it when there is no alternative, which is also the better game
behaviour. **Recommendation: multiplier, not veto.**

**Verdict: affordable, but only via the snapshot.** Anything that asks the live claim map per node
is both a data race and a performance bug.

### E.2 Hostile mob spawning inside the territory

**What exists.**

* `RaiderMobUtils.spawn(entityType, count, pos, world, colony, eventID)` —
  `mc/api/entity/mobs/RaiderMobUtils.java:133`. Places raiders with a 5-block ground search, calls
  `entity.setColony(colony)`, `setEventID(eventID)`, `registerWithColony()`. **[VERIFIED]**
* `RaiderMobUtils.spawnAt` (`:196`) — exact placement, returns the entity. Written for air drops.
* The ship raids already run a **periodic spawner**: `AbstractShipRaidEvent` keeps a list of vanilla
  `Blocks.SPAWNER` positions (`:145`) and tops raiders back up from them on the event tick
  (`:282`–`:290`). That is the closest existing precedent to what the owner wants. **[VERIFIED]**
* `BaseSpawner.nextSpawnData` and `requiredPlayerRange` are **already access-widened**
  (`26.2/src/main/resources/minecolonies.accesswidener`, "blocks and block entities" section), so a
  configured vanilla spawner block is reachable without a mixin. **[VERIFIED]**

**The blocker for reusing raid infrastructure.**
`AbstractEntityMinecoloniesRaider.readAdditionalSaveData` **discards itself on reload** when it has
no colony *and* no event id:

```java
if (colony == null || eventID == 0) { this.remove(RemovalReason.DISCARDED); }
```
`AbstractEntityMinecoloniesRaider.java:257`–`:260`. **[VERIFIED]**

So MineColonies raiders cannot be spawned free-standing. They need a colony **and** a registered
`IColonyEvent`. Under design 1 the colony exists, so this is a matter of registering one long-lived
event on the shell — doable, but it drags in `EventManager`, the raid bar, the raid-level maths
(`RaidManager.getColonyRaidLevel`, `:957`, which sums citizen skills and building levels and would
return 0 for a shell) and the whole `canRaid` gate (`:929`).

**Recommendation: a fresh, plain spawner, not the raid system.**

A `HostileSpawner` on the shell colony's state machine (an `ACTIVE`-only `TickingTransition`
alongside `worldTickSlow`, `Colony.java:478`) or on the existing shared server tick, which:

* picks a random claimed column inside the territory,
* checks light/space with `BlockPosUtil.findAround(..., SOLID_AIR_POS_SELECTOR)` — the same helper
  `RaiderMobUtils.spawn` uses (`RaiderMobUtils.java:152`),
* spawns **vanilla** hostiles (`EntityType.ZOMBIE` &c.) with `EntitySpawnReason.EVENT`,
* caps the population by counting entities in the claim box.

`ACTIVE`-only is the right gate: the colony is `INACTIVE` with no subscribers (`Colony.java:489`),
so nothing spawns when no player is anywhere near, which is both correct and free.

**One caveat worth knowing.** `EventHandler.isSpawnBlockedByBuilding` (`EventHandler.java:383`) —
the check that keeps vanilla hostiles out of colony buildings — is **dead code in this port**. Its
own doc comment says so: Fabric has no spawn-veto event and the mod ships no mixin. **[VERIFIED]**
So vanilla monsters already spawn freely inside colony buildings in 26.2; a hostile territory
spawner is not making anything worse, and equally cannot use that machinery.

**Difficulty: small.** Perhaps 150 lines, no new persistence (the territory already saves), no
mixin.

### E.3 Aircraft not allowed to fly over it

**What the existing integration is.** `mc/core/compatibility/simpleplanes/`, gated on
`FabricLoader.isModLoaded("simpleplanes")` and installed from one shared
`ServerTickEvents.END_LEVEL_TICK` (`SimplePlanesCompat.java:68`–`:73`). **[VERIFIED]**

`AntiAirBattery` is **entirely colony-and-building-driven**, and this is the crux:

* Targets come from `AutopilotRegistry.active()` plus a periodic `getEntitiesOfClass` box around
  **each colony centre** (`AntiAirBattery.visible`, `:341`–`:368`). **[VERIFIED]**
* Shooters are guard-tower buildings at `MIN_TOWER_LEVEL`+ with a citizen gunner in them
  (`tickEmplacement`, `:377`–`:420`; `emplacements`, `:314`–`:329`). **[VERIFIED]**
* Ownership is asked **only** to attribute a wreck: `getColonyByPosFromWorld` at `:277` and `:724`.
  **[VERIFIED]**

A hostile territory has no buildings and no citizens, so **the anti-air battery is the wrong thing
to attach to.** It is the colony shooting *outward*; a no-fly zone is territory acting on anything
*inside* it.

**What a no-fly rule would attach to, three options:**

1. **A territory sweep in the existing shared tick** — the recommended one. In
   `SimplePlanesCompat.init`'s callback add a fourth call that walks
   `AutopilotRegistry.active()` (free, no world lookup) plus, on an interval, a bounded entity scan,
   asks `getColonyByPosFromWorld(level, plane.blockPosition())` — the exact call
   `AntiAirBattery` already makes at `:724` — and if the answer is a hostile territory, reuses the
   `disable(level, plane, colony)` recipe at `AntiAirBattery.java:666`–`:682`:
   `setAutopilot(null)` (which also disarms the warhead — see the class comment at `:88`–`:100`),
   `setThrottle(0)`, and a chunk ticket via the `WRECKS` list so it does not freeze mid-air
   (`tickWrecks`, `:700`+). **No mixin, no change to Simple Planes, no new licence question.**
   **[VERIFIED]** that all the pieces exist; **[UNCHECKED]** how it plays.
2. **A warning-only rule** — the `AircraftWatch` shape (`:87`–`:148`), i.e. tell the pilot they are
   entering hostile airspace and give them a few seconds before (1) applies. Cheap and much better
   game feel than an instant kill. **[UNCHECKED]**
3. **Refuse at plan time** — a seam in Simple Planes' `AutopilotRegistry`/`FlightPlan` so a route
   over hostile ground is never planned. This needs a change to Simple Planes, in the style of its
   `xyz.przemyk.simpleplanes.api.BlastGuard` seam and bound reflectively as
   `SimplePlanesBlastGuard` does (`SimplePlanesBlastGuard.java:59`–`:90`). The licence direction is
   already established and correct — MineColonies links *to* Simple Planes, never the reverse
   (`SimplePlanesBlastGuard.java:53`–`:58`). **Not needed for a first version.** **[VERIFIED]**

**Difficulty: small (option 1+2), medium (option 3).** Option 1 is maybe 80 lines in the package
that already exists.

---

## F. Rectangle mode

**Verdict: build it. It is genuinely small,** and most of the machinery is already in this tree.

### F.1 Where the two-corner state lives — already solved

`ItemScepterLumberjack` is the working precedent, in this repo, in the same package:

* Constructor declares `.component(ModDataComponents.POS_SELECTION, PosSelection.EMPTY)`
  (`mc/core/items/ItemScepterLumberjack.java:48`). **[VERIFIED]**
* `useOn` sets corner B (`:53`–`:66`), `canDestroyBlock` sets corner A (`:71`–`:82`) — left-click
  and right-click, no extra keybind. `getDestroySpeed` returns `Float.MAX_VALUE` so the left-click
  never breaks anything (`:84`).
* `PosSelection` is a Structurize record `(Optional<BlockPos> startPos, Optional<BlockPos> endPos)`
  with `readFromItemStack` / `updateItemStack` helpers
  (`/workspace/structurize/26.2/…/items/AbstractItemWithPosSelector.java:188`–`:260`). **[VERIFIED]**
* It also implements `IBlockOverlayItem.getOverlayBoxes` (`:131`), so the selection draws itself.

**So: item component on the stack, not server-side per-player.** It survives relog, it is
per-item so two players cannot collide, and the rendering comes with it. Nothing new to design.

### F.2 How fill interacts with the mask and with whole-chunk claims

This is the part that is nicer than expected: **the mask makes a rectangle exact, and the code is
already shaped for it.**

For a rectangle from `(x1,z1)` to `(x2,z2)`:

* **Interior chunks** — wholly inside the rectangle → `ChunkDataHelper.tryClaim(..., true, colony,
  false)` (`ChunkDataHelper.java:434`) and leave the mask `null`. Zero extra bytes
  (`ChunkClaimData.java:155`–`:160` deliberately refuses to allocate a full mask). **[VERIFIED]**
* **Edge chunks** — partly inside → `tryClaim`, then `clearAllColumns(chunk)`
  (`ChunkClaimData.java:194`) and `setColumnClaimed(pos, true, chunk)` for each column inside the
  rectangle. That is exactly what `ItemScepterBorder.pullChunkIn` already does
  (`ItemScepterBorder.java:249`–`:261`). **[VERIFIED]**
* **Chunks owned by someone else** — skip, per `ItemScepterClaim.isOwnedByAnotherColony` (`:158`).
* **Chunks already this colony's with a mask** — OR the rectangle's columns in;
  `setColumnClaimed` drops the mask automatically once all 256 bits are set
  (`ChunkClaimData.java:171`–`:176`). **[VERIFIED]**

`ChunkPos.rangeClosed(ChunkPos.containing(a), ChunkPos.containing(b))` already exists and is used
by `ChunkDataHelper.buildingClaimBox` (`:213`). **[VERIFIED]**

### F.3 The sane cap on area, and what breaks without one

Three independent limits, in increasing order of pain:

1. **Chunk loading — the immediate one.** `tryClaim` calls `world.getChunk(chunkBlockPos)`
   (`ChunkDataHelper.java:441`) and `setColumnClaimed` needs a `LevelChunk` to `markUnsaved`. On
   ungenerated terrain that is **synchronous world generation on the server thread, per chunk**.
   `ColonyUtils.getOwningColony(dimension, pos)` carries a doc comment saying exactly this
   (`ColonyUtils.java:184`–`:191`): *"`Level#getChunk(int, int)` loads and, on ground nobody has
   visited, generates the chunk on the server thread, which is a large price."* A 64×64-chunk
   rectangle is 4096 chunk generations in one click — a multi-minute freeze. **[VERIFIED]** the
   mechanism; **[UNCHECKED]** the duration.
2. **Network — the sneaky one.** `ColonyView.serializeNetworkData` writes the **entire** claim map
   into **every** colony-view packet, with no dirty check (`ColonyView.java:394`–`:400`) — unlike
   ticketed chunks immediately above it, which *are* gated on
   `hasNewSubscribers || isTicketedChunksDirty` (`:381`). Packets go out on
   `UPDATE_SUBSCRIBERS_INTERVAL = 20` ticks (`mc/api/util/constant/ColonyConstants.java:48`) while
   the colony is dirty. Per chunk the payload is ≥13 bytes, +32 when masked
   (`ChunkClaimData.serialize`, `:433`–`:461`). So **10 000 chunks ≈ 130 KB/s per subscriber, or
   450 KB/s if masked.** **[VERIFIED]** by arithmetic on the serialiser.
3. **Chunk tickets.** `Colony.registerClaimedChunkTickets` tickets one chunk each, capped by
   `maxforcedchunks` (default 1024, `ServerConfiguration.java:331`), sorting by distance from the
   centre when over the cap (`Colony.java:750`–`:768`). Beyond that, claimed ground silently stops
   being force-loaded. **[VERIFIED]**

**Recommended cap: 256 chunks per rectangle** (a 16×16-chunk block, 256×256 blocks), with a config
key in the `claims` category alongside `maxoutlyingchunks`. That sits an order of magnitude under
the network cliff, keeps a single click's chunk generation to something a server can absorb, and is
far more land than a player claims by hand today. Refuse above it with a lang key stating the size
and the limit, the way `ItemScepterLumberjack.storeRestrictedArea` already refuses an oversized area
(`:114`–`:118`). **[VERIFIED]** as a pattern to copy.

### F.4 Does unclaim need the same treatment

**Yes, and for a reason beyond symmetry.** A rectangle claim that is too big to undo one click at a
time is a trap. `ItemScepterUnclaim` already has the whole shape — a range loop, an
owner test, a centre-chunk guard, `unclaim()` clearing a stale mask
(`ItemScepterUnclaim.java:108`–`:127`, `unclaim` at `:160`–`:170`). Rectangle unclaim is the same loop over
`ChunkPos.rangeClosed`, with the same cap and the same centre-chunk refusal. **[VERIFIED]**

The border scepter should get it too: a rectangle *of columns* (paint/erase a block-precision
rectangle within one or a few chunks) is the most useful of the three, and it needs no chunk-loading
cap at all if it is restricted to chunks the colony already owns.

### F.5 The shape of the change

Roughly:

1. `AbstractItemMinecolonies` subclasses declare `ModDataComponents.POS_SELECTION`; corner A on
   left-click (`canDestroyBlock` + `getDestroySpeed`), corner B on `useOn`, as
   `ItemScepterLumberjack` does. Sneak-`use` in air clears the selection.
2. A shared `ChunkDataHelper.claimRectangle(level, colony, cornerA, cornerB, add)` doing the
   interior/edge split of F.2, returning counts for the message.
3. A cap check with a new config key and a refusal lang key.
4. `IBlockOverlayItem.getOverlayBoxes` for the preview — free from the same precedent.
5. New lang keys in `26.2/src/main/resources/assets/minecolonies/lang/manual_en_us.json`, then
   datagen.

**Estimate: half a day.** No new persistence, no new packets, no client-only code on a server path,
no mixin.

### F.6 A pre-existing bug found on the way, worth fixing while in there

`manual_en_us.json` uses `%d` in the border scepter strings — `item.minecolonies.scepterborder.info`
(`:383`) and `.progress` (`:387`). Vanilla's `TranslatableContents.decomposeTemplate` accepts only
`%s` and `%%`; anything else throws `TranslatableFormatException`
(`/opt/mc-src/net/minecraft/network/chat/contents/TranslatableContents.java:129`–`:131`), which
`decompose` catches and falls back to emitting the **raw template** (`:98`–`:101`). So those two
messages currently render with a literal `%d` and no numbers. `MessageUtils.format(key, args)` is a
plain `Component.translatable` (`mc/api/util/MessageUtils.java:122`), so nothing rescues it.
**[VERIFIED]** against decompiled vanilla.

There are **170** `%d` occurrences in `manual_en_us.json`, so this is a broad inherited problem, not
one the border scepter introduced. Fixing the two border-scepter strings is a one-line change and in
scope for whoever does rectangle mode. The other 168 are a separate errand.

---

## G. Verdict and staged plan

### G.1 Rectangle mode — **BUILD**

Small, well-precedented, no architectural risk. Do it first: it is independently useful, and it is
also the tool the hostile-territory feature would be drawn with.

Order: rectangle **border** paint (no chunk-loading risk, most useful) → rectangle **claim** (with
cap) → rectangle **unclaim** (same cap).

### G.2 Hostile territory — **BUILD, narrowly, as a flagged shell colony**

But with two things said plainly first.

**What the owner should know before agreeing.**

* **Interleaving is impossible.** One owner per chunk, one mask per chunk. The enemy border can run
  along the chunk grid next to yours, or leave a strip of no-man's-land, but a chunk cannot be half
  yours and half theirs. If the picture in his head is a jagged frontier weaving between his
  buildings, the answer is no, and the data model would have to change (two `int`s and two masks per
  chunk, touching persistence, the network format and every reader) before it could be yes.
* **"Just a border plus a spawner" is not what he will get.** A shell colony *is* a colony as far as
  ~50 call sites are concerned. That is the whole point — it is what makes the feature cheap — but
  it also means the territory has permissions, a name, a team colour, a request manager object and a
  place in `/mc colony list`. Suppressing the visible parts of that is more of the work than the
  border is.

**Staged plan.**

**Stage 1 — the smallest thing that is actually playable.** Territory + protection + rendering.
* `boolean hostile` on `Colony`, in NBT and in `ColonyView`; `IColony#isHostile()`.
* An op command `/mc territory create <name> <pos>` that calls
  `IServerColonySaveData.createColony` directly and **skips** `claimColonyChunks`, then
  `setOwnerAbandoned()` (`Permissions.java:519`) so every player is `NEUTRAL`.
* Teach `ItemScepterClaim`, `ItemScepterBorder`, `ItemScepterUnclaim` and `CreateColonyMessage` to
  refuse when `getClosestColony` returns a hostile colony — and add an op-only variant (or a
  permission check against op level) so the scepters *can* paint a hostile territory deliberately.
* One line in `ColonyBorderRenderer.borderColour` for a hostile colour.
* Skip hostile colonies in `AircraftWatch.tick` and `AntiAirBattery.tick`/`visible`.

At the end of stage 1 the player has enemy ground that renders in its own colour, that he cannot
build in, and that persists. **That is already a playable mechanic**, and every line of it is
mod-internal.

**Stage 2 — the spawner.** A plain periodic vanilla-mob spawner on the shell's `ACTIVE` state
machine, per E.2. Do *not* route through the raid system.

**Stage 3 — pathfinding avoidance.** Snapshot in the `AbstractPathJob` constructor, multiplier in
`modifyCost`, per E.1. **Measure before and after** with `PathfindingStats`; this is the only stage
with a genuine performance question, and I could not answer it here.

**Stage 4 — no-fly.** Warning first, then `disable`, both from the existing shared Simple Planes
tick, per E.3.

**What can wait, or never happen.**
* Any change to the claim data model to allow interleaving. High cost, breaks the save format and
  the network format, and the chunk grid is a perfectly legible frontier in a Minecraft world.
* Reusing the raid system for territory mobs. It drags in raid levels, raid bars, spawn-point
  search and `canRaid` gating, all of which are shaped around a colony with citizens.
* A separate ownership layer (design 2). Stated once more because it is the intuitive choice and it
  is wrong: it costs ~50 call sites and a new sync path to buy nothing that the flag does not.

### G.3 Where I would push back

Nothing here is a "don't build" — but the honest framing is that **feature 1 is a small feature
wearing a big feature's clothes**. The territory itself is nearly free. The *mechanics keyed off it*
— pathfinding avoidance especially — are where the work and the risk are, and they are each
independently sized. If only one of them ever ships, ship the protection and the rendering (stage 1)
and see whether the ground feeling hostile is enough on its own before paying for the rest.

---

## Appendix: what I could not determine

* **Nothing was play-tested.** There is no game client in this environment. I did not start a
  server (my port 25833 was never used), because every question in this brief was answerable by
  reading, and the ones that were not — how a hostile border *feels*, how often the subscriber
  lifecycle leaves a foreign border undrawn, the real per-node cost of the pathfinding snapshot —
  need a client, not a headless server.
* **The pathfinding cost is reasoned, not measured.** The node-budget arithmetic is verified; the
  conclusion "single-digit nanoseconds, immeasurable at zero hostile chunks" is an inference. It
  should be checked with `PathfindingStats` before stage 3 lands.
* **How many unguarded `getColonyByWorld(...)` dereferences exist.** I found one
  (`AbstractEntityMinecoloniesRaider.java:361`) by reading. I did not enumerate them, because under
  the recommended design it does not matter — the shell colony is a real object.
* **Whether the single-player claim-map thread hazard (D.2, caveat 2) has ever actually bitten.**
  The code path is verified; the consequence is not observed.
