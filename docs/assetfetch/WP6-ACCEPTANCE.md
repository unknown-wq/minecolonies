# WP6 — final acceptance run: report

Branch `claude/assetfetch` at `fe40f05c` plus the one fix this run found (see §5).
Everything below was executed on 2026-08-19; every number is copied from a real run,
and the commands that produced each one are named.

| criterion | verdict |
| --- | --- |
| 1 — build green, jar content exact | **PASS** |
| 2 — real download, really verified | **PASS** |
| 3 — dedicated server boots | **PASS** — after fixing a real server-crash defect this run uncovered |
| 4 — assets there and actually used | **PASS on disk and in game**, with two named gaps (§4.4) |

One defect was found and fixed: the D2 gate's signature made a dedicated server die at
startup. Details, cause and disposition in §5.

---

## 1. Criterion 1 — build green

Both Gradle invocations went through `/home/user/mc-build.sh`, separately, datagen first.

```
$ /home/user/mc-build.sh /workspace/minecolonies-fabric/26.2 runDatagen
...
[19:28:37] [Render thread/INFO] (Minecraft) All providers took: 1245 ms
[19:28:37] [Render thread/INFO] (Minecraft) Caching: total files: 1574, old count: 1574, new count: 1574, removed stale: 0, written: 309
BUILD SUCCESSFUL in 44s
6 actionable tasks: 6 executed

$ /home/user/mc-build.sh /workspace/minecolonies-fabric/26.2 build
> Task :compileJava UP-TO-DATE
> Task :processResources
> Task :validateAccessWidener
> Task :jar
BUILD SUCCESSFUL in 26s
```

Datagen ran from the asset-less checkout (the ARR tree is not in the repo) and wrote 309
files into `26.2/src/main/generated/assets/` — 235 `items/`, 69 `models/item/`,
3 `lang/`, 2 `colony/stories/`. That directory is untracked by design and was **not**
committed. `build` ran afterwards, so the jar carries the fresh datagen output.

The build was run a second time after the §5 fix; the jar-entry list is byte-for-byte the
same set (`diff` of the two `unzip -Z1` listings is empty), so every count below holds for
the final jar:

```
/workspace/minecolonies-fabric/26.2/build/libs/minecolonies-26.2-0.0.51.jar
46,384,579 bytes   sha256 7abee16b0f34081057326d7d1384dddf1e55eae60f856ccf6019ac46687dd665
```

### 1.1 `assets/minecolonies/` — 316 files, and nothing else

```
assets/minecolonies files total : 316
  items/*.json                  : 235   (234 datagen + the port's scepterclaim.json)
  models/item/*.json            :  70   (69 datagen + the port's fieldstick.json)
  colony/stories/*.json         :   2   (abandonedcolonies.json, supplies.json — datagen)
  lang/*.json                   :   4   (en_us.json, default.json, quests.json, tag.item.json)
  sounds.json                   :   1
  textures/**                   :   4
textures/entity_icon matches    :   0
```

Everything outside `items/` and `models/item/`, listed in full:

```
assets/minecolonies/colony/stories/abandonedcolonies.json
assets/minecolonies/colony/stories/supplies.json
assets/minecolonies/lang/default.json
assets/minecolonies/lang/en_us.json
assets/minecolonies/lang/quests.json
assets/minecolonies/lang/tag.item.json
assets/minecolonies/sounds.json
assets/minecolonies/textures/block/transparent.png
assets/minecolonies/textures/item/spawn_egg.png
assets/minecolonies/textures/item/spawn_egg_overlay.png
assets/minecolonies/textures/misc/rack_empty.png
```

All six port files present:

```
OK  items/scepterclaim.json                OK  textures/item/spawn_egg.png
OK  models/item/fieldstick.json            OK  textures/item/spawn_egg_overlay.png
OK  textures/block/transparent.png         OK  textures/misc/rack_empty.png
```

Contents, read out of the jar:

```
en_us.json keys              : 403          (single entry, exactly as required)
sounds.json events           : 272
sounds.json key prefixes     : {'citizen.huscarl.': 136, 'citizen.marksman.': 136}
keys outside those two       : 0
lang/default.json keys       : 425
lang/quests.json keys        : 637
lang/tag.item.json keys      : 72
```

No `textures/entity_icon/**`, no upstream `sounds.json` content (the 272 port events are
disjoint from the 7002 events in the fetched pack — intersection is empty, §4.2), no other
ARR-tree file.

### 1.2 The rest of the jar

```
assetfetch/manifest.json                            (1,611,863 bytes, sha256 723b120b…33b9fa)
assetfetch/transforms.json
assetfetch/patches/**            : 63 files (57 *.jsonpatch + 6 *.diff)
assets/minecraft/**              :  2 files — atlases/blocks.json,
                                    textures/gui/container/creative_inventory/tab_minecolonies_background.png
minecolonies.mixins.json         : present, client = ["PackRepositoryMixin"]
com/minecolonies/core/mixin/PackRepositoryMixin.class            : present
com/minecolonies/core/client/assetfetch/*.class                  : 45
com/minecolonies/core/client/assetfetch/gui/*.class              :  6
fabric.mod.json mixins           : ['minecolonies.mixins.json']
fabric.mod.json accessWidener    : minecolonies.accesswidener
```

63 shipped patch files + the 6 mechanically flattened models described by
`transforms.json` = the 69 files the installer patches (§2).

---

## 2. Criterion 2 — a real download, really performed

Driver: `/home/user/wp6-test/src/Wp6E2E.java`, adapted from WP3's `E2ETest` with one
deliberate change — it takes the bundle from **`BundleResources.ofModJar()`**, and the
classpath is the built jar, so both the pipeline classes and `manifest.json` /
`transforms.json` / `patches/**` are the ones that ship:

```
bundle source : jar:file:/workspace/minecolonies-fabric/26.2/build/libs/minecolonies-26.2-0.0.51.jar!/assetfetch/manifest.json
```

Run (`java -cp out:<built jar>:<gson,log4j> Wp6E2E`, fresh empty game directory):

| measurement | value | expected |
| --- | --- | --- |
| source used | `maven-1374`, HTTP 200, one attempt, no failures | source 1 |
| bytes transferred | **78,071,143** | 78,071,143 ✓ |
| whole-jar SHA-256 | **9ea739a273cb20d02a3a0ce05f6e3d315aa7e9ddd17a23f0e70ee5f00c3e4cfa** | pinned hash ✓ |
| files extracted | **8470** | 8470 ✓ |
| files patched | **69** | 69 ✓ |
| files pruned (not in manifest) | 0 | — |
| files verified vs `manifest.json` | **8474 / 8474** | all ✓ |
| files in `pack/` afterwards | 8475 (manifest set + `pack.mcmeta`) | — |
| installed pack size on disk | **82,435,189 bytes** | — |
| `tmp/` afterwards | gone | — |
| wall clock | 5 s (first run) / 6 s (repeat on the final jar) | — |

`state.json` after the run:

```json
{
  "version": 1,
  "status": "installed",
  "sourceId": "maven-1374",
  "sourceUrl": "https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam/minecolonies/1.1.1374-1.21.1-snapshot/minecolonies-1.1.1374-1.21.1-snapshot.jar",
  "jarSha256": "9ea739a273cb20d02a3a0ce05f6e3d315aa7e9ddd17a23f0e70ee5f00c3e4cfa",
  "manifestSha256": "723b120be1abf2edb4bad0ef014e27e62e7f9f41cc962e23234c609cce33b9fa",
  "installedAt": "2026-08-19T19:39:41.777687157Z"
}
```

`status=installed`, and `manifestSha256` equals the SHA-256 of `assetfetch/manifest.json`
inside the built jar — the shipped manifest is what verified the install.

`pack/pack.mcmeta`: `{"pack":{"description":"MineColonies assets, downloaded from LDTTeam's official build","min_format":88,"max_format":88}}`.

The whole run was repeated against the post-fix jar; every number above is identical.

**Source 2** was not re-downloaded. WP3 §6.3 exercised it end-to-end (chain fell through a
dead source 1 to `maven-1368`: 77,945,293 bytes, extracted 8470, patched 69, verified 8474,
pack 82,434,471 bytes, and the `alt` overlay hash used for `uk_ua.json`). Nothing in the
chain has changed since: `git diff --stat dfbc3bdc..HEAD -- .../client/assetfetch/ .../resources/assetfetch/`
lists only added files (the WP5 consent UI and the gate) — `SourceChain`, `AssetSource`,
`JarDownloader`, `JarAssetExtractor`, `PatchBundle`, `PackVerifier`, `AssetManifest`,
`InstallPipeline` and the shipped bundle are untouched, so WP3's result stands.

Nothing downloaded, extracted or installed was committed or uploaded anywhere; it all
lives under `/home/user/wp6-test/` and the gitignored `26.2/run/`.

---

## 3. Criterion 3 — dedicated server boot

Harness `/home/user/srv-integ`, port 25611, `bin/start.sh` (mkfifo + fifo-holder + `java -jar
fabric-server-launch.jar nogui`), the built jar copied over
`mods/minecolonies-26.2-0.0.51.jar`. Baseline: `bootF.log`, the most recent prior boot
(2026-08-16 15:18).

**First attempt (`boot49.log`) failed** — see §5. That was a real defect in the shipped
code, not a harness problem.

**After the fix (`boot50.log`)**:

```
[19:38:12] [main/INFO]: Loading Minecraft 26.2 with Fabric Loader 0.19.3
[19:38:24] [Server thread/INFO]: Done (1.269s)! For help, type "help"
```

| | boot50 (this jar) | bootF (baseline) |
| --- | --- | --- |
| `Done (` line | yes, 1.269s | yes, 1.416s |
| ERROR | **0** | 0 |
| FATAL | **0** | 0 |
| WARN | **23** | 23 |

The WARN sets are not merely equal in count — `diff` of the two sorted, timestamp-stripped
WARN sets is empty. They are the pre-existing ones: offline mode, the duplicate-colony
notice for the three colonies in the test world, Structurize's pack-discovery warnings, and
the JVM's `sun.misc.Unsafe` / native-access notices.

Nothing client-side loaded:

```
$ grep -in "assetfetch|PackRepositoryMixin|Cannot load class|environment type SERVER" boot50.log
(no matches)
```

and, asked of the live JVM while it was running (`jcmd <pid> VM.class_hierarchy`):

```
com.minecolonies.core.client.assetfetch.AssetFetchGate      => <not loaded>
com.minecolonies.core.client.assetfetch.AssetFetch          => <not loaded>
com.minecolonies.core.mixin.PackRepositoryMixin             => <not loaded>
com.minecolonies.core.client.gui.containers.WindowField     => <not loaded>
com.minecolonies.core.blocks.BlockScarecrow                 => loaded (hierarchy printed)
```

`GC.class_histogram` on the same JVM: 2400 `com.minecolonies` classes loaded, **0** of them
under `client.assetfetch`, `client.gui` or `core.mixin`.

Shut down with `stop` through the fifo; the server saved all dimensions and exited on its
own (`bin/stop.sh` reported `stopped`). No process was killed by name.

---

## 4. Criterion 4 — the assets are there, and are used

### 4.1 On disk (independent of the installer)

A separate script re-hashed every file in `pack/` against `assetfetch/manifest.json` read
straight out of the built jar — not the installer's own verifier:

```
independent manifest verify: match=8474 differ=0 missing=0 of 8474
pack files on disk: 8475, bytes: 82,435,189
```

Inventory of the installed pack:

```
assets/minecolonies/textures/entity_icon : 3481 files
assets/minecolonies/textures (all)       : 7655
assets/minecolonies/gui (BlockUI XML)    :   94
assets/minecolonies/lang                 :   97
assets/minecolonies/sounds (.ogg)        :   68
assets/minecolonies/models               :  458
assets/minecolonies/blockstates          :   95
assets/minecolonies/atlases              :    1
assets/minecolonies/{sounds.json,LICENSE}
```

The two overlay relationships the design depends on both hold: the pack's
`lang/en_us.json` has 3898 keys and the jar's has 403, of which 6 collide (the port's
overrides) and 397 are new keys; the pack's `sounds.json` has 7002 events and the jar's
272, with an empty intersection.

### 4.2 In game — a client **could** be run here

The box has no display, but `Xvfb` is installed and Mesa's software rasteriser is present,
so a client is possible after all. A GLFW/LWJGL probe (natives already extracted under
`/tmp/lwjgl_root`, `LIBGL_ALWAYS_SOFTWARE=1`, `xvfb-run -s "-screen 0 1280x720x24"`) got a
core context:

```
GL_VERSION  : 4.5 (Core Profile) Mesa 25.2.8-0ubuntu0.24.04.1
GL_RENDERER : llvmpipe (LLVM 20.1.2, 256 bits)
```

so `runClient` was run twice through `mc-build.sh` under Xvfb — once with the installed
pack copied into the run directory, once with it moved away — which makes the pack
injection an A/B experiment rather than an assertion.

**With the pack installed**, the very first resource reload:

```
[19:43:36] [Render thread/INFO] (Minecraft) Reloading ResourceManager: minecolonies:fetched_assets, vanilla, blockui, domum_ornamentum, fabric-api, …
```

`minecolonies:fetched_assets` is `FetchedAssetsSource.PACK_ID`. It is in the list, so the
mixin ran, the source was discovered, and `required = true` force-selected the pack.

**With the pack moved away**, same client, same command:

```
[19:49:41] [Render thread/INFO] (Minecraft) Reloading ResourceManager: vanilla, blockui, domum_ornamentum, fabric-api, …
$ grep -c fetched_assets  → 0
```

The pack is offered only when `AssetFetch.isReady()`, exactly as designed.

A concrete asset observation, from the same pair of runs — the MineColonies GUI sprite
atlas, whose `atlases/minecolonies_gui.json` exists **only** in the fetched pack (the jar
has no `assets/minecolonies/atlases/`):

```
with pack     : Created: 128x128x0 minecolonies:textures/atlas/minecolonies_gui.png-atlas
without pack  : Created:   32x32x0 minecolonies:textures/atlas/minecolonies_gui.png-atlas
```

The atlas is built from sprites that came out of the download: with the pack the stitcher
has real textures to place, without it the atlas collapses to the empty 32×32 minimum. No
`Using missing texture` line appeared for any `minecolonies:` resource in the run with the
pack. Both clients reached a rendered screen (screenshots grabbed off the Xvfb display,
kept at `/home/user/wp6-test/titlescreen.png` and `consent-screen.png`).

### 4.3 A bonus: two of WP5's unverified items are now verified

The asset-less run reached `TitleScreen` and the D1 hook fired: the consent screen
rendered, correctly laid out at 1280×720 — heading, the three body paragraphs (what, from
where, the ARR notice, the local-jar route) and the three buttons, nothing overflowing,
nothing clipped. So WP5 §7.1 (screen layout) and §7.4 (the title-screen trigger firing) are
no longer unverified, at least at this resolution.

### 4.4 What could NOT be produced

There is no way to send input to the client here — no `xdotool`, no `python-xlib`, no
screenshot/automation tooling, and installing any was out of scope. Everything that needs a
mouse click therefore remains unobserved:

1. **A BlockUI MineColonies window actually opening** (right-click a hut, `WindowTownHall`
   or any other). The D2 gate was never observed letting a window through, so
   "windows open with the assets present" is still inferred from the XML being on disk
   (94 files, all manifest-verified) and never seen.
2. **The D2 gate screen** (`AssetsMissingScreen`) appearing in place of a window when the
   assets are absent — same reason: it needs a right-click on a hut in a world.
3. **The download driven from the consent screen** — the "Download" button was never
   clicked. The pipeline behind it was exercised headlessly instead (§2), and the screen
   itself was seen (§4.3), but the two were not seen wired together, so the progress
   screen, the post-install `reloadResourcePacks()` and the "installed" end state are
   still unobserved.
4. **D3, the sleeping-citizen particle** — needs a citizen asleep in a world.
5. **The client command** `/minecolonies-client fetchassets` — needs a chat box in a world.

WP5 §7's remaining items (Windows path handling, non-English text) are untouched by this
run.

---

## 5. The defect this run found, and what was done about it

**Symptom.** The first boot of the freshly built jar (`boot49.log`) never reached
`Done (`. The server died during mod init:

```
[19:33:40] [main/ERROR]: Failed to start the minecraft server
java.lang.RuntimeException: Could not execute entrypoint stage 'main' … at 'com.minecolonies.core.MineColonies'!
Caused by: java.lang.RuntimeException: Cannot load class com.minecolonies.core.client.gui.containers.WindowField in environment type SERVER
	at net.fabricmc.loader.impl.transformer.FabricTransformer.transform(FabricTransformer.java:61)
	…
	at knot//com.minecolonies.apiimp.initializer.ModBlocksInitializer.init(ModBlocksInitializer.java:64)
	at knot//com.minecolonies.core.MineColonies.onInitialize(MineColonies.java:137)
```

`ModBlocksInitializer.java:64` is `new BlockScarecrow()`. Nothing there mentions a window.

**Cause.** WP5's D2 change turned every gated call site from

```java
new WindowField(scarecrow).open();
```

into

```java
AssetFetchGate.openOrOffer(() -> new WindowField(scarecrow));
```

with the gate declared as `openOrOffer(Supplier<BOWindow>)`. javac compiles that lambda into
a synthetic method **declared to return `BOWindow`** whose body returns a `WindowField`:

```
private static com.ldtteam.blockui.views.BOWindow lambda$useItemOn$0(TileEntityScarecrow);
   0: new  #432  // class com/minecolonies/core/client/gui/containers/WindowField
   …
   8: areturn
```

Verifying `BlockScarecrow` therefore has to prove `WindowField` is a `BOWindow`, and proving
that **loads `WindowField`** — at class-link time, on whatever side is linking. On a
dedicated server that is a `@Environment(CLIENT)` class and Fabric refuses it, so the server
dies the moment any such block or item class is touched. The old code never triggered it:
`invokevirtual open()` on `WindowField` resolves lazily, at execution, on the client only.

Reduced to a five-line test case and measured with `-verbose:class`, the two shapes differ
exactly as described: the `Supplier<Base>` form loads the subclass when the *calling* class
is touched; a `<X extends Base> Supplier<X>` form does not.

This was not a one-file problem. Of the 25 gate call sites, 23 hand it an explicit lambda
whose body constructs a concrete window class, and they sit in blocks, items, entities,
`ColonyManager`, `BuildingDataManager` and six client-bound message classes — all classes a
dedicated server loads. Whichever one the server touched first would have killed it. (The
other two — `this::getWindow`, which already returns `BOWindow`, and
`WindowSchematicAnalyzer::new`, a constructor reference the JVM resolves lazily — were never
affected.)

**Fix** (`AssetFetchGate.java`, one signature):

```java
public static <T extends BOWindow> void openOrOffer(final Supplier<T> window)
```

`T` is inferred as the concrete window type at each call site, so the synthetic method's
declared and actual return types are identical, nothing has to be proved, and the window
class is not loaded until the lambda runs — which only ever happens on a client. No call
site changed. Verified in the rebuilt jar:

```
private static com.minecolonies.core.client.gui.containers.WindowField lambda$useItemOn$0(TileEntityScarecrow);
```

**Disposition.** Small, mechanical, and clearly this project's own regression, so it was
fixed here rather than reported: one line plus the javadoc paragraph explaining why the type
variable is load-bearing. The jar was rebuilt, criterion 1's content check re-run (identical
entry list), criterion 2 re-run end-to-end (identical numbers), and criterion 3 re-run from
scratch (`boot50.log`, green). It is committed separately from this report.

Nothing else was changed. No other defect was found.

---

## 6. Where the evidence lives

| what | where |
| --- | --- |
| Gradle logs | `/tmp/gradle-last.log` (last run); the transcripts quoted above |
| E2E driver + run logs | `/home/user/wp6-test/src/Wp6E2E.java`, `e2e-source1.log`, `e2e-source1-final.log` |
| Installed pack (not committed) | `/home/user/wp6-test/gamedir/minecolonies/fetched-assets/` |
| Server boot logs | `/home/user/srv-integ/boot49.log` (the failure), `boot50.log` (green), `bootF.log` (baseline) |
| Server class-load evidence | `/home/user/wp6-test/class-histogram.txt` |
| Client logs | `/home/user/wp6-test/client-installed.log`, `client-noassets.log` |
| Client screenshots | `/home/user/wp6-test/titlescreen.png`, `consent-screen.png` |
| GL probe | `/home/user/wp6-test/gl/GlProbe.java` |
