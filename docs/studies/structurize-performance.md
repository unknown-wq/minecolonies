# Structurize performance review

Review only. Date: 2026-08-27. Tree: `libs/structurize/26.3/` on branch `26.3`. No production code
was changed; the patches below are proposals, not applied.

Evidence standard, same as the other studies here:

* **[VERIFIED]** — I opened the file and the cited `file:line` says what I claim.
* **[UNCHECKED]** — inference I did not confirm. Each one names what would confirm it.

Paths are relative to the repository root.
`libs/structurize/26.3/src/main/java/com/ldtteam/structurize/` is abbreviated to `sz/`,
`26.3/src/main/java/com/minecolonies/` to `mc/`.

---

## 0. What I read

End to end, line by line:

* the placement pipeline — `sz/placement/StructurePlacer.java`, `AbstractBlueprintIterator.java`,
  `BlueprintIteratorDefault.java`, `BlueprintIteratorInwardCircle.java`, the head of
  `BlueprintIteratorHilbert.java`, `IPlacementHandler.java`, `IStructureHandler.java`,
  `AbstractStructureHandler.java`, `CreativeStructureHandler.java`, `IPlacementContext.java`,
  `SimplePlacementContext.java`, and all 1500 lines of `handlers/placement/PlacementHandlers.java`.
* `sz/util/BlockUtils.java` in full.
* `sz/blueprints/v1/Blueprint.java` and `BlueprintUtil.java` in full; `BlueprintUtils.java`,
  `DataFixerUtils.java`, `DataVersion.java`.
* `sz/storage/StructurePacks.java` in full, `sz/util/IOPool.java`, `sz/util/ChangeStorage.java`
  (first 160 lines), `sz/management/Manager.java` (tick + queue half).
* client: `sz/client/BlueprintRenderer.java`, `BlueprintHandler.java`, `RenderingCacheKey.java`,
  `fakelevel/BlueprintBlockAccess.java`, `sz/event/WorldRenderContext.java`,
  `ClientEventSubscriber.java`, `sz/storage/rendering/RenderingCache.java`,
  `types/BlueprintPreviewData.java`, and the top third of `sz/util/WorldRenderMacros.java`.

On the consumer side I read `mc/core/entity/ai/workers/util/BuildingStructureHandler.java`,
`WorkerLoadOnlyStructureHandler.java`, the dispatch block of
`mc/core/entity/ai/workers/AbstractEntityAIStructure.java`, and
`AbstractEntityAIStructureWithWorkOrder.java` around the placer construction — enough to know which
handler methods the builders actually hit and how often.

I also disassembled the relevant vanilla methods out of the loom cache jar
(`26.3-snapshot-10/minecraft-merged-deobf`) with `javap` where a claim depended on what vanilla does
internally. Those are marked as bytecode-verified.

**Not covered.** `sz/client/gui/**` (10 windows, ~4000 lines) beyond `WindowScan`'s one call site.
`sz/network/messages/**`. `sz/operations/**` beyond `Manager`'s driving loop — I read the queue, not
the individual operations. `sz/api/ItemStackUtils.java` past the two methods on the placement path.
The bottom two-thirds of `WorldRenderMacros` (the line/triangle emitters) — I grepped it for
allocations and found nothing, but I did not read the vertex code. `sz/compat/`, `sz/datagen/`,
`sz/commands/`.

I did not build or run anything. Everything claiming a *measured* number would need a profile;
nothing here is measured.

---

## 1. Findings, by expected impact

### F1 — Every worldgen-surface query recompiles the whole material rule tree

**What it does now.** `BlockUtils.getWorldgenBlock` (`sz/util/BlockUtils.java:177-186`) [VERIFIED]
asks vanilla for the surface material at one position:

```java
return randomState.surfaceSystem()
    .topMaterial(generatorSettings.materialRule().value(),
        randomState,
        new WorldGenerationContext(chunkGenerator, serverLevel),
        serverLevel.getBiomeManager()::getBiome,
        chunk,
        randomState.samplersWithContext(SamplerContext.EMPTY_UNCACHED),
        location,
        !stateAbove.getFluidState().isEmpty())
    .orElse(null);
```

**Why it costs.** Disassembling `MaterialSystem#topMaterial` from the 26.3-snapshot-10 jar
[VERIFIED, bytecode] shows that *per call* vanilla allocates a `DensityVolume`, allocates a
`MaterialRuleContext`, calls `MaterialRule.compile(ctx)` to build a fresh `RuleEvaluator` tree, runs
`getSurfaceGradientX`/`getSurfaceGradientZ`, then `updateXZ`, `updateY`, `tryApply`, and wraps the
answer in an `Optional`. `RandomState#samplersWithContext` allocates a new anonymous
`RandomState$2` on every call [VERIFIED, bytecode]. Vanilla itself only ever calls `topMaterial`
through `buildSurface`, where one compiled evaluator serves 256 columns; here it is compiled for one
block. On top of that the Structurize side allocates a `WorldGenerationContext` and a bound method
reference (`serverLevel.getBiomeManager()::getBiome`) per call, both of which depend only on the
level, and calls `serverLevel.getChunk(location)` per call.

**How often.** Two distinct callers, with very different exposure:

1. `FallingBlockPlacementHandler` — **on the ordinary builder path.**
   `getRequiredItems` walks up to ten blocks down calling `getWorldgenBlock` each step
   (`PlacementHandlers.java:357-369`) [VERIFIED], and `handle` does the same walk again
   (`PlacementHandlers.java:400-410`) [VERIFIED]. `canHandle` is
   `blockState.getBlock() instanceof FallingBlock || instanceof Fallable`
   (`PlacementHandlers.java:336-339`) [VERIFIED], so every sand, gravel, concrete-powder,
   scaffolding and anvil block in a blueprint takes this path whenever the block below it is not
   solid. Worst case per such block: 10 in the resource pass + 10 in `getRequiredItems` + 10 in
   `handle` = 30 rule compiles.
2. `CreativeStructureHandler.getSolidBlockForPos`
   (`sz/placement/structure/CreativeStructureHandler.java:136-139`) [VERIFIED] → every solid
   placeholder in a Structurize paste, the build tool's "place", and MineColonies'
   `CreativeBuildingStructureHandler` / raider ships / colony events, which extend it
   (`mc/api/util/CreativeBuildingStructureHandler.java:48`) [VERIFIED]. Those run at
   `maxOperationsPerTick` = 1000 blocks per server tick
   (`sz/config/ServerConfiguration.java:75`, `CreativeStructureHandler.java:112-115`) [VERIFIED].
   `SolidSubstitutionPlacementHandler` calls it twice for the same position in one placement —
   once from `getRequiredItems` (`PlacementHandlers.java:1248`) and once from `handle`
   (`PlacementHandlers.java:1229`) [VERIFIED].

Note the **ordinary MineColonies builder does not hit path 2**: `BuildingStructureHandler` and
`WorkerLoadOnlyStructureHandler` both delegate `getSolidBlockForPos` to
`AbstractEntityAIStructure#getSolidSubstitution`, which just returns the hut's configured fill block
(`mc/core/entity/ai/workers/AbstractEntityAIStructure.java:1171-1174`) [VERIFIED]. I nearly wrote
this finding as "every builder, every foundation block"; it is not that.

3. Client preview: `BlueprintBlockAccess.prepareBlockStateForRendering`
   (`sz/client/fakelevel/BlueprintBlockAccess.java:63`) [VERIFIED] calls
   `getSubstitutionBlockAtWorld` per solid-placeholder block during renderer `init`, once per
   blueprint per cache key. Same cost, one-shot, and see F12 for the thread question.

**What to do.**

*Cheapest and safest, no vanilla surface required:* memoize. The answer is a pure function of
(level, x, y, z) for a generated chunk and never changes during a session. A small bounded
`Long2ObjectLinkedOpenHashMap<BlockState>` keyed on `BlockPos.asLong()`, capped at a few thousand
entries and cleared on level unload, collapses:

* the duplicate `getRequiredItems`/`handle` pair per solid placeholder to one call,
* the ten-deep column walk in `FallingBlockPlacementHandler` — successive falling blocks in the same
  column re-query the same positions,
* the resource pass against the placement pass.

*Also worth doing regardless:* hoist the three per-call constants out of `getWorldgenBlock`. The
`WorldGenerationContext`, the biome function and the `DensitySamplerSet` depend only on the
`ServerLevel`, so they can live in a one-entry cache keyed on the level:

```diff
--- a/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/util/BlockUtils.java
+++ b/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/util/BlockUtils.java
@@
+    /** Per-level pieces of the surface query that never change while the level is loaded. */
+    private record SurfaceQueryContext(WorldGenerationContext genContext,
+        Function<BlockPos, Holder<Biome>> biomes,
+        DensitySamplerSet samplers) {}
+
+    private static ServerLevel surfaceCtxLevel = null;
+    private static SurfaceQueryContext surfaceCtx = null;
+
+    private static SurfaceQueryContext surfaceContextFor(final ServerLevel level, final NoiseBasedChunkGenerator gen)
+    {
+        if (surfaceCtxLevel != level)
+        {
+            final RandomState rs = level.getChunkSource().randomState();
+            surfaceCtx = new SurfaceQueryContext(new WorldGenerationContext(gen, level),
+                level.getBiomeManager()::getBiome,
+                rs.samplersWithContext(SamplerContext.EMPTY_UNCACHED));
+            surfaceCtxLevel = level;
+        }
+        return surfaceCtx;
+    }
```

and then feed `ctx.genContext()`, `ctx.biomes()`, `ctx.samplers()` into the `topMaterial` call. That
removes three allocations per call but **not** the rule compile, which is the dominant cost and is
only reachable by keeping fewer calls. Both changes are worth making; the memo is the one that
matters.

I checked whether the compiled `RuleEvaluator` itself could be cached: `MaterialRule#compile` takes a
`MaterialRuleContext`, whose constructor the port note at `BlockUtils.java:155-166` says is
package-private [VERIFIED — the note], so building one outside vanilla would need an access widener.
Do not go there; the memo gets the same win.

**One honest caveat:** 26.2 was also expensive here. The old code built a `SurfaceRules.Context` and
walked the column by hand (`libs/structurize/26.2/.../BlockUtils.java:157-200`) [VERIFIED], and
`surfaceRule().apply(ctx)` compiled the rule tree per call there too. This is upstream badness the
port carried over, not a regression the port introduced.

---

### F2 — `Blueprint.buildBlockInfoCaches` is O(volume × entities) and allocates three objects per blueprint position

**What it does now.** `sz/blueprints/v1/Blueprint.java:579-601` [VERIFIED]:

```java
cacheEntitiesMap.put(tempPos,
    Arrays.stream(this.getEntities())
        .filter(data -> data != null && isAtPos(data, tempPos))
        .toArray(CompoundTag[]::new));
```

inside a triple loop over `sizeY × sizeZ × sizeX`. `isAtPos` (`Blueprint.java:924-931`) [VERIFIED]
allocates a `BlockPos` per comparison.

**Why it costs.** For a blueprint of volume `V` with `E` entities this is `V` stream pipelines, `V`
array allocations (nearly all of them zero-length), `V × E` `isAtPos` calls and `V × E` throwaway
`BlockPos` objects. A 50×30×50 hut is V = 75 000. With 20 entities that is 1.5 M `BlockPos`
allocations and 75 000 stream chains to answer a question that one pass over 20 entities answers.

It is also a memory finding, and the bigger half of it. `cacheBlockInfoMap` and `cacheEntitiesMap`
both get an entry for *every* position — including the ~90 % that are air and the ~100 % that have
no entity. Per position that is a `BlockPos` key, a `BlockInfo` record, a `HashMap.Node` in each of
two maps, and an empty `CompoundTag[]`: order 150 bytes. 75 000 positions ≈ 11 MB per blueprint,
and there is no cap on how many `Blueprint` objects are alive (see F3 — every load makes a new one).

**How often.** Once per blueprint, on first access to `getBlockInfoAsList/Map` or
`getCachedEntitiesAsMap`, and again after every `cacheReset` — which `setRotationMirrorRelative`
triggers (`Blueprint.java:807`) [VERIFIED] and `addBlockState` triggers per call
(`Blueprint.java:301`) [VERIFIED]. The renderer's `init` walks `getBlockInfoAsList()` twice
(`sz/client/BlueprintRenderer.java:126, 148`) [VERIFIED], and every placement touches
`getBlockInfoAsMap()` per position through `getBlockState` / `getTileEntityData` /
`getBluePrintPositionInfo`.

**What to do.** Bucket the entities once, before the loop, and share a single empty array:

```diff
--- a/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/blueprints/v1/Blueprint.java
+++ b/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/blueprints/v1/Blueprint.java
@@
+    private static final CompoundTag[] NO_ENTITIES = new CompoundTag[0];
+
     private void buildBlockInfoCaches()
     {
         cacheBlockInfo = new ArrayList<>(getVolume());
         cacheBlockInfoMap = new HashMap<>(getVolume());
-        cacheEntitiesMap = new HashMap<>(getEntities().length);
+        cacheEntitiesMap = new HashMap<>(getEntities().length);
+
+        // one pass over the entities instead of one pass per position
+        final Map<BlockPos, List<CompoundTag>> byPos = new HashMap<>();
+        for (final CompoundTag data : this.getEntities())
+        {
+            if (data != null)
+            {
+                byPos.computeIfAbsent(posOf(data), k -> new ArrayList<>()).add(data);
+            }
+        }
+        byPos.forEach((pos, list) -> cacheEntitiesMap.put(pos, list.toArray(NO_ENTITIES)));
+
         for (short y = 0; y < this.sizeY; y++)
         {
             for (short z = 0; z < this.sizeZ; z++)
             {
                 for (short x = 0; x < this.sizeX; x++)
                 {
                     final BlockPos tempPos = new BlockPos(x, y, z);
                     final BlockInfo blockInfo = new BlockInfo(tempPos, palette.get(structure[y][z][x] & 0xFFFF), tileEntities[y][z][x]);
                     cacheBlockInfo.add(blockInfo);
                     cacheBlockInfoMap.put(tempPos, blockInfo);
-                    cacheEntitiesMap.put(tempPos,
-                        Arrays.stream(this.getEntities())
-                            .filter(data -> data != null && isAtPos(data, tempPos))
-                            .toArray(CompoundTag[]::new));
                 }
             }
         }
     }
+
+    private static BlockPos posOf(final CompoundTag entityData)
+    {
+        final ListTag list = entityData.getListOrEmpty(ENTITY_POS);
+        return new BlockPos((int) list.getDoubleOr(0, 0), (int) list.getDoubleOr(1, 0), (int) list.getDoubleOr(2, 0));
+    }
```

This changes `cacheEntitiesMap` from "an entry for every position" to "an entry for every position
that has an entity", which is what `getCachedEntitiesAsMap`'s only reader already assumes —
`getBluePrintPositionInfo` uses `getOrDefault(pos, ...)` (`Blueprint.java:914`) [VERIFIED]. That
single behaviour change is the reason to look twice before applying, and it removes a few MB per
blueprint on top of the CPU.

While you are in that method, `new CompoundTag[0]` at `Blueprint.java:914` is allocated twice per
call, once as an unused `getOrDefault` default and once for the `includeEntities == false` branch.
Point both at `NO_ENTITIES`. `getBluePrintPositionInfo` is called once per iterated position (F4),
so that is one wasted allocation per position, up to 10 000 per builder AI tick.

---

### F3 — There is no blueprint cache, and the IO pool that loads them is effectively single-threaded

**What it does now.** `StructurePacks.getBlueprint(String pack, Path path, boolean, Provider)`
(`sz/storage/StructurePacks.java:423-444`) [VERIFIED] reads the file, decompresses, runs the data
fixer over the palette / block entities / entities, and builds a fresh `Blueprint`. Nothing anywhere
in `StructurePacks` stores the result — I read all 858 lines; the only maps are `packMetas` and
`clientPackMetas`, both of `StructurePackMeta`. There is a `maxCachedSchematics` config option
(`sz/config/ServerConfiguration.java:77`) [VERIFIED] with **no reader anywhere in either tree**
[VERIFIED — `grep -rn maxCachedSchematics` finds only the two declarations].

**Why it costs.** Every builder that starts a hut, every work-order preview, every browse of the
build tool re-reads and re-parses the same file. For a blueprint written before the current data
version, `fixPalette` runs the DFU per palette entry (`sz/blueprints/v1/BlueprintUtil.java:296-320`)
[VERIFIED] and `fixTileEntities` per block entity (`BlueprintUtil.java:410-412`) [VERIFIED]. Then
F2's cache build runs on top.

The pool that does it: `IOPool.getExecutor()` builds
`new ThreadPoolExecutor(1, 2, 10, SECONDS, ioQueue, ...)` where `ioQueue` is an *unbounded*
`LinkedBlockingDeque` (`sz/util/IOPool.java:16, 53`) [VERIFIED]. A `ThreadPoolExecutor` only grows
past `corePoolSize` when the queue rejects an offer, and an unbounded queue never does — so the
`maximumPoolSize` of 2 is dead and the pool runs one thread. Every blueprint load in the game
serialises behind it.

**How often.** MineColonies calls the *synchronous* `getBlueprint` straight from server-thread code
in at least four places — `mc/core/colony/workorders/WorkManager.java:413`,
`mc/core/colony/buildings/AbstractBuilding.java:1160`,
`mc/core/colony/events/raid/pirateEvent/ShipBasedRaiderUtils.java:142`,
`mc/core/tileentities/TileEntityColonyBuilding.java:563` [VERIFIED, grep]. Those are MineColonies'
choices and out of scope to change, but they are only viable because Structurize offers no cached
path. The async path (`getBlueprintFuture`) is non-blocking on the consumer side —
`AbstractStructureHandler.getBluePrint` checks `isDone()` rather than `get()`
(`sz/placement/structure/AbstractStructureHandler.java:117-128`) [VERIFIED] — so the pool depth
shows up as *latency before a builder starts*, not as a stall.

**What to do.**

1. `IOPool.java:53` — one word:

```diff
-            executor = new ThreadPoolExecutor(1, 2, 10, TimeUnit.SECONDS, ioQueue, new StructurizeThreadFactory());
+            executor = new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, ioQueue, new StructurizeThreadFactory());
```

   (`allowCoreThreadTimeOut(true)` if the idle threads bother you.) While there, `getExecutor()` is
   an unsynchronised lazy init read from both the client and server threads — two executors can be
   created under a race. Make the field `static final` or synchronise the getter.

2. Add the cache the config already promises: a bounded `LinkedHashMap`/Caffeine keyed on
   `(packName, path)` in front of `getBlueprint`, sized by `maxCachedSchematics`.

   **The complication that must not be waved away:** `Blueprint` is mutable. `setRotationMirror`
   rewrites `structure`, `palette`, `entities` and `tileEntities` in place
   (`Blueprint.java:682-808`) [VERIFIED], and `AbstractStructureHandler.getBluePrint` calls it on
   whatever the future returns (`AbstractStructureHandler.java:122`) [VERIFIED]. Handing the same
   instance to two builders with different rotations corrupts both. So the cache has to either
   (a) store the parsed NBT and rebuild the `Blueprint` per handout — this still saves the disk read
   and the DFU cascade, which is most of the cost, or (b) key on `(pack, path, rotationMirror)` and
   hand out deep copies. Option (a) is the one I would take. Marked **[UNCHECKED]** as to which is
   faster in practice; both need a profile.

---

### F4 — Five allocations and two world reads per position in the skip scan, at up to 10 000 positions per builder AI tick

**What it does now.** `AbstractBlueprintIterator.iterateWithCondition`
(`sz/placement/AbstractBlueprintIterator.java:90-118`) [VERIFIED] loops
`while (count++ < structureHandler.getMaxBlocksCheckedPerCall())`, and per iteration:

* `getProgressPos()` returns `progressPos.immutable()` (`AbstractBlueprintIterator.java:184`)
  [VERIFIED] — a fresh `BlockPos` from a field that is already a `MutableBlockPos`.
* `structureHandler.getProgressPosInWorld(progressPos)` is
  `getCenterPos().subtract(getBluePrint().getPrimaryBlockOffset()).offset(localPos)`
  (`sz/placement/structure/IStructureHandler.java:174-177`) [VERIFIED] — two `BlockPos`
  allocations, and it recomputes `centerPos - primaryOffset` every call although both are fixed for
  the lifetime of the handler.
* `getBluePrintPositionInfo(progressPos)` allocates a `BlueprintPositionInfo` plus a
  `new CompoundTag[0]` (`Blueprint.java:910-915`) [VERIFIED] and does a `HashMap` lookup on a
  `BlockPos` key.
* `IPlacementHandler.doesWorldStateMatchBlueprintState` does
  `PlacementHandlers.getHandler(...)` plus `getWorld().getBlockState(worldPos)` and, when the
  blueprint position has block-entity data, `getWorld().getBlockEntity(worldPos)`
  (`sz/placement/handlers/placement/IPlacementHandler.java:26-41`) [VERIFIED].

**How often.** `BuildingStructureHandler.getMaxBlocksCheckedPerCall()` returns **10 000**
(`mc/core/entity/ai/workers/util/BuildingStructureHandler.java:305-308`) [VERIFIED] and
`getStepsPerCall()` returns **1** (same file, `299-302`) [VERIFIED]. So one builder AI tick places
at most one block and may scan ten thousand positions to find it. `structureStep` is an `AITarget`
on `STANDARD_DELAY` (`mc/core/entity/ai/workers/AbstractEntityAIStructure.java:189`) [VERIFIED].
Across the whole build the skip scan visits every position once per stage, and there are seven
stages in the dispatch (`AbstractEntityAIStructure.java:402-462`) [VERIFIED]. That is roughly
`7 × volume` iterations per building, times the number of concurrent builders. For the creative
paste path `getMaxBlocksCheckedPerCall` is `maxOperationsPerTick` = 1000
(`CreativeStructureHandler.java:70-73`) [VERIFIED], per tick, per queued operation.

**What to do.** Three independent changes, all local:

1. `getProgressPos()` is called by `iterateWithCondition:99` and `StructurePlacer:132`; both use the
   result read-only for the rest of the iteration. Adding a package-visible
   `getProgressPosMutable()` returning the field, and using it in `iterateWithCondition`, removes one
   allocation per iteration with no visible API change. `getProgressPos()` stays as it is for
   external callers.
2. Cache the anchor in `IStructureHandler`/`AbstractStructureHandler`:

```diff
--- a/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/placement/structure/IStructureHandler.java
+++ b/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/placement/structure/IStructureHandler.java
@@
     default BlockPos getProgressPosInWorld(final BlockPos localPos)
     {
-        return getCenterPos().subtract(getBluePrint().getPrimaryBlockOffset()).offset(localPos);
+        return getAnchorInWorld().offset(localPos);
     }
+
+    /**
+     * The world position local (0,0,0) maps to. Constant for the life of the handler; cache it.
+     */
+    default BlockPos getAnchorInWorld()
+    {
+        return getCenterPos().subtract(getBluePrint().getPrimaryBlockOffset());
+    }
```

   with `AbstractStructureHandler` overriding `getAnchorInWorld()` to memoise on first call
   (invalidated in `setBlueprint`). Halves the `BlockPos` allocations on that path and removes a
   `getPrimaryBlockOffset()` null-check per call.
3. The shared-empty-array fix from F2.

None of these is dramatic on its own. Together they take the per-iteration allocation from five
objects to two, on a loop with a 10 000 iteration ceiling.

---

### F5 — `handlerCache` is a plain `IdentityHashMap` written from both the client and the server thread, and never invalidated when tags reload

**What it does now.** `sz/placement/handlers/placement/PlacementHandlers.java:155` [VERIFIED]:

```java
private static Map<Block, IPlacementHandler> handlerCache = new IdentityHashMap<>(128);
```

`getHandler` reads it, and on a miss walks `handlers` and `put`s the result
(`PlacementHandlers.java:164-186`) [VERIFIED]. There is no synchronisation on that path. The
`add(...)` overloads *do* synchronise, on `handlers`, and `add(IPlacementHandler)` clears the cache
(`PlacementHandlers.java:143-150`) [VERIFIED] — so the author was aware of concurrency here, and the
read/write path was left out.

**Why it costs.** Two separate problems.

*Correctness.* `getHandler` is called from the server thread on every placement
(`StructurePlacer.java:262, 263, 518`) [VERIFIED] and from the client thread by `WindowScan`'s
resource-list build (`sz/client/gui/WindowScan.java:525`) [VERIFIED]. In singleplayer those are
different threads. A concurrent `put` into a `HashMap`-family table can lose entries or, on resize,
spin. The window is small and the map settles quickly, but it is real.

*Staleness.* The cache is keyed on `Block` and never cleared except in `add`. One `canHandle`
implementation is tag-based — `BlackListedBlockPlacementHandler` tests
`blockState.is(ModTags.BLUEPRINT_BLACKLIST)` (`PlacementHandlers.java:1174`) [VERIFIED]. After a
datapack reload changes that tag, cached entries stay wrong until a mod calls `add`. The same
applies to `BlockUtils.trueSolidBlocks`, which is guarded by `if (trueSolidBlocks.isEmpty())`
(`BlockUtils.java:89-99`) [VERIFIED] and so is built once and never rebuilt, even though its filter
reads `ModTags.WEAK_SOLID_BLOCKS`.

**What to do.** `ConcurrentHashMap` costs essentially nothing on the read path here and fixes the
first problem outright. `IdentityHashMap` was presumably chosen for identity hashing on `Block`, but
`Block` does not override `hashCode`, so `ConcurrentHashMap` gives identity behaviour anyway.

```diff
-    private static Map<Block, IPlacementHandler> handlerCache = new IdentityHashMap<>(128);
+    private static final Map<Block, IPlacementHandler> handlerCache = new ConcurrentHashMap<>(128);
```

For the staleness half, clear `handlerCache` and reset `trueSolidBlocks` from whatever tag-reload
callback the port uses. I did not find such a hook in `sz/event/` — `checkOrInit` is called from
`onWorldTick` (`sz/event/EventSubscriber.java:52`) [VERIFIED], which is a no-op after the first
tick. Whether a Fabric tag-reload callback is wired up anywhere is **[UNCHECKED]**; confirming means
grepping for a `CommonLifecycleEvents.TAGS_LOADED` registration, which I did not find.

I checked the cache's *key* for correctness and it is fine: every `canHandle` in the file is a pure
function of `blockState.getBlock()` — instanceof tests, block identity, or a tag lookup which
vanilla resolves on the block's registry holder [VERIFIED, all 22 implementations read].

---

### F6 — The same block is asked for its state three to five times per placement

**What it does now.** For one placed block on the builder path:

| # | site | call |
|---|------|------|
| 1 | `AbstractBlueprintIterator.java:108` | `doesWorldStateMatchBlueprintState` → `getBlockState(worldPos)` |
| 2 | `StructurePlacer.java:235` | `world.getBlockState(worldPos)` |
| 3 | `StructurePlacer.java:257` | `doesWorldStateMatchBlueprintState` → `getBlockState(worldPos)` **again** |
| 4 | `PlacementHandlers.java:878` (`GeneralBlockPlacementHandler.handle`) | `world.getBlockState(pos)` |
| 5–6 | `ChangeStorage.java:86, 97` (paste path only) | `getBlockState` + `getBlockEntity`, twice |

[VERIFIED — every row read.]

Row 3 is the interesting one. The iterator has *just* established at row 1 that the world does not
match, and returns `NEW_BLOCK` only in that case — the sole exception being a position that carries
entities, where the `&& info.getEntities().length == 0` clause at
`AbstractBlueprintIterator.java:108` can let a matching position through. So in the overwhelmingly
common no-entity case, `StructurePlacer.java:257` re-derives an answer the caller already knows,
paying a `getHandler` lookup, a `getBlockState`, and (when the blueprint position has block-entity
data) a `getBlockEntity` for it.

Rows 5–6 are worse than they look: `ChangeStorage.addPreviousDataFor`/`addPostDataFor`
(`ChangeStorage.java:84-98`) [VERIFIED] call `world.getBlockEntity(place)` unconditionally, on every
position of a paste, and `Level#getBlockEntity` promotes a pending block-entity tag into a live
block entity rather than just reading a map. At `maxOperationsPerTick` = 1000 that is 2000
`getBlockEntity` calls per tick, almost all of them on positions with no block entity at all.

**What to do.** The `ChangeStorage` one is a clean small patch — the state is already being fetched
one line up:

```diff
--- a/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/util/ChangeStorage.java
+++ b/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/util/ChangeStorage.java
@@
     public void addPreviousDataFor(final BlockPos place, final Level world)
     {
-        blocks.computeIfAbsent(place, p -> new BlockChangeData()).withPreState(world.getBlockState(place)).withPreTE(world.getBlockEntity(place));
+        final BlockState state = world.getBlockState(place);
+        blocks.computeIfAbsent(place, p -> new BlockChangeData())
+            .withPreState(state)
+            .withPreTE(state.hasBlockEntity() ? world.getBlockEntity(place) : null);
     }
@@
     public void addPostDataFor(final BlockPos place, final Level world)
     {
-        blocks.computeIfAbsent(place, p -> new BlockChangeData()).withPostState(world.getBlockState(place)).withPostTE(world.getBlockEntity(place));
+        final BlockState state = world.getBlockState(place);
+        blocks.computeIfAbsent(place, p -> new BlockChangeData())
+            .withPostState(state)
+            .withPostTE(state.hasBlockEntity() ? world.getBlockEntity(place) : null);
     }
```

Rows 1–3 want a signature change rather than a patch: pass the already-fetched `worldState` from
`StructurePlacer.handleBlockPlacement` into `doesWorldStateMatchBlueprintState` instead of letting
it re-read. `IPlacementHandler.doesWorldStateMatchBlueprintState(BlockState, ...)` — the per-handler
overload at `IPlacementHandler.java:131` — already takes the state; only the static convenience
wrapper at `:26-41` re-reads. Add an overload taking the state, and call it from
`StructurePlacer.java:257` and `AbstractBlueprintIterator.java:108`.

**While you are in `ChangeStorage`:** it keeps a `Map<BlockPos, BlockChangeData>` with an entry per
touched block (`ChangeStorage.java:42`) [VERIFIED], each holding two `BlockState`s and two live
`BlockEntity` references, and `Manager` retains up to `maxCachedChanges` = 50 of these *per player*
(`sz/management/Manager.java:92-103`, `ServerConfiguration.java:76`) [VERIFIED]. With
`schematicBlockLimit` = 100 000 (`ServerConfiguration.java:79`) [VERIFIED] the worst case is 50 ×
100 000 retained entries holding strong references to `BlockEntity` objects — which in turn hold
`Level`. This is an undo buffer, so retention is the point, but retaining live `BlockEntity`
instances rather than their serialised NBT is not, and the total is bounded only by an
unrealistically large product. Worth a cap on total retained blocks, not just on operation count.

---

### F7 — `getResourceRequirements` re-fetches and deep-copies tile-entity data it was handed as a parameter

**What it does now.** `StructurePlacer.getResourceRequirements` takes
`CompoundTag tileEntityData` as its fifth parameter (`sz/placement/StructurePlacer.java:488-493`)
[VERIFIED] — and the only caller passes
`handler.getBluePrint().getTileEntityData(worldPos, localPos)` (`StructurePlacer.java:176`)
[VERIFIED]. Then at `StructurePlacer.java:509` [VERIFIED] the method builds a `BlockInfo` from
`handler.getBluePrint().getTileEntityData(worldPos, localPos)` **again**, ignoring the parameter it
already has.

**Why it costs.** `Blueprint.getTileEntityData` (`Blueprint.java:532-545`) [VERIFIED] calls
`getBlockInfoAsMap()` three times, does a `containsKey` plus two `get`s on a `BlockPos`-keyed
`HashMap`, and — the expensive part — `CompoundTag.copy()`, a deep copy of the whole block-entity
tag, before rewriting three ints on it.

**How often.** Once per position in the `GET_RES_REQUIREMENTS` phase, which walks the entire
structure once per building. Only positions that actually have block-entity data pay the deep copy,
but every position pays the three map lookups.

**What to do.** One line:

```diff
--- a/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/placement/StructurePlacer.java
+++ b/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/placement/StructurePlacer.java
@@
-        if (IPlacementHandler.doesWorldStateMatchBlueprintState(new BlockInfo(localPos, localState, handler.getBluePrint().getTileEntityData(worldPos, localPos)), worldPos, this.handler))
+        if (IPlacementHandler.doesWorldStateMatchBlueprintState(new BlockInfo(localPos, localState, tileEntityData), worldPos, this.handler))
```

Also worth folding the three `getBlockInfoAsMap()` calls in `Blueprint.getTileEntityData` into one
local — the accessor is a null-check plus a field read, but the map lookup is not.

---

### F8 — `ContainerPlacementHandler.handle` deserialises a whole block entity and its inventory just to see whether it throws, then discards it

**What it does now.** `sz/placement/handlers/placement/PlacementHandlers.java:939-948` [VERIFIED]:

```java
try
{
    // Try detecting inventory content.
    ItemStackUtils.getItemStacksOfTileEntity(tileEntityData, blockState, world);
}
catch (final Exception ex)
{
    // If we can't load the inventory content of the TE, return early, don't fill TE data.
    return ActionProcessingResult.SUCCESS;
}
```

The return value is not assigned. `getItemStacksOfTileEntity` (`sz/api/ItemStackUtils.java:56-75`)
[VERIFIED] does a full `BlockEntity.loadStatic` from the NBT, then walks every item handler on it
inside a fake level, copying every stack (`ItemStackUtils.java:81-97`) [VERIFIED].

Immediately afterwards, `handleTileEntityPlacement` (`PlacementHandlers.java:1401-1428`) [VERIFIED]
does `BlockEntity.loadStatic` a **second** time, then `newTile.saveWithFullMetadata(...)` to
serialise it straight back out, then `TagValueInput.create(...)` and
`worldBlockEntity.loadWithComponents(...)` to deserialise it a **third** time into the block entity
the world already has.

**Why it costs.** Placing one chest costs: one BE deserialisation + full inventory walk with a copy
of every stack (thrown away), one BE deserialisation, one BE serialisation, one BE deserialisation.
Four NBT traversals of the same data where one would do.

**How often.** Per placed block whose block is a `BaseEntityBlock` — chests, furnaces, racks,
barrels, every MineColonies hut block. A furnished hut has hundreds.

**What to do.** The probe at `:939-948` is a validity check whose result is already recomputed by
`getRequiredItems` on the same tick (`PlacementHandlers.java:968`) [VERIFIED, same handler). Either
drop the probe and let `handleTileEntityPlacement` fail safely, or cache the probe's answer per
`(blockState, tileEntityData identity)`.

The load→save→load in `handleTileEntityPlacement` can collapse when the block entity is not
`IRotatableBlockEntity` (the common case) and the world already has one: load `tileEntityData`
directly into `worldBlockEntity` and skip constructing `newTile` at all. I am *not* offering that as
a patch — `loadStatic` returning null is currently the guard that stops a mismatched id from being
applied, and `saveWithFullMetadata` normalises the tag on the way through. Reproducing both
correctly needs more care than a diff in a review deserves. Flagging the shape, not the fix.

---

### F9 — `BlueprintUtil.createBlueprint` is O(volume × palette) and does a registry reverse-lookup, a string, and a `getChunkAt` per block

**What it does now.** `sz/blueprints/v1/BlueprintUtil.java:78-118` [VERIFIED], per scanned position:

* `BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace()` (`:85`) — a reverse registry map
  lookup plus a `String` from the `Identifier`.
* `world.getChunkAt(mutablePos)` (`:103`) — per block, not per chunk.
* `pallete.contains(state)` (`:113`) — a linear scan of the palette.
* `pallete.indexOf(state)` (`:117`) — **the same linear scan again**, immediately after.
* `pallete.indexOf(Blocks.AIR.defaultBlockState())` (`:94`) — a linear scan for an index that is
  always 0 (air is added first at `:72`).

**Why it costs.** With volume `V` and palette size `P`, the two `List` scans are `2 × V × P`
`BlockState.equals` calls. A 100×50×100 scan with a 500-entry palette is 500 000 × 500 × 2 = 500 M
comparisons. That is the O(n²) in this codebase.

**How often.** Once per scan — the scan tool, `/structurize scan`, `UpdateSchematicsCommand`,
`UpdateSchematicPackCommand`. Not per tick, but it runs on the server thread, and a large scan is
exactly when a player notices.

**What to do.** Replace the two scans with one map:

```diff
--- a/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/blueprints/v1/BlueprintUtil.java
+++ b/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/blueprints/v1/BlueprintUtil.java
@@
         final List<BlockState> pallete = new ArrayList<>();
         // Allways add AIR to Pallete
         pallete.add(Blocks.AIR.defaultBlockState());
+        final Map<BlockState, Short> paletteIndex = new IdentityHashMap<>();
+        paletteIndex.put(Blocks.AIR.defaultBlockState(), (short) 0);
@@
-                if (!FabricLoader.getInstance().isModLoaded(modName))
-                {
-                    structure[y][z][x] = (short) pallete.indexOf(Blocks.AIR.defaultBlockState());
-                    continue;
-                }
+                if (!FabricLoader.getInstance().isModLoaded(modName))
+                {
+                    structure[y][z][x] = 0; // air is always palette entry 0
+                    continue;
+                }
@@
-            if (!pallete.contains(state))
-            {
-                pallete.add(state);
-            }
-            structure[y][z][x] = (short) pallete.indexOf(state);
+            structure[y][z][x] = paletteIndex.computeIfAbsent(state, s -> {
+                pallete.add(s);
+                return (short) (pallete.size() - 1);
+            });
```

`IdentityHashMap` is correct here because `BlockState` instances are interned by the block's state
definition, which is the same reason `List.contains` worked at all.

Two more, same method, both trivial: hoist the mod-namespace check behind a
`Set<Block> alreadyChecked` (or key the `paletteIndex` miss path on it) so the registry reverse
lookup and the `isModLoaded` call happen once per distinct block rather than once per position; and
hoist `getChunkAt` out of the inner loop by tracking the current `ChunkPos`.

---

### F10 — `BlueprintIteratorInwardCircle.setProgressPos` replays the whole spiral, and `StructurePlacer` calls it once per AI tick

**What it does now.** `sz/placement/BlueprintIteratorInwardCircle.java:128-147` [VERIFIED]:

```java
this.progressPos.set(NULL_POS);
while (progressPos.getX() != localPosition.getX() || progressPos.getZ() != localPosition.getZ())
{
    iterate(true);
}
```

`StructurePlacer.executeStructureStep` calls `iterator.setProgressPos(...)` unconditionally at the
top of every invocation (`sz/placement/StructurePlacer.java:124`) [VERIFIED], and MineColonies
constructs a **fresh** `StructurePlacer` — and therefore a fresh iterator with `progressPos ==
NULL_POS` — on each pass through the material-request and build paths
(`mc/core/entity/ai/workers/AbstractEntityAIStructureWithWorkOrder.java:264`,
`mc/core/entity/ai/workers/builder/EntityAIStructureBuilder.java:139`) [VERIFIED]. So the
`!this.progressPos.equals(localPosition)` guard at `:134` never short-circuits.

`BlueprintIteratorHilbert.setProgressPos` has the same shape with a different cost — a linear scan
of a precomputed `positions` list of `sizeX × sizeZ` entries
(`sz/placement/BlueprintIteratorHilbert.java:60-77`) [VERIFIED].

**Why it costs.** O(sizeX × sizeZ) per `executeStructureStep`. A 60×60 building is 3600 iterations
of ring arithmetic before a single block is considered, on every builder AI tick that uses one of
these iterators. The default is `"default"` (`ServerConfiguration.java:80`) [VERIFIED], which
overrides `setProgressPos` not at all and is O(1) — but MineColonies exposes the choice to players
per work order through `BuilderModeSetting`
(`AbstractEntityAIStructureWithWorkOrder.java:258-261`) [VERIFIED], so real colonies do run it.

There is also a latent hang: the `while` loop has no iteration bound, and `iterate` calls `reset()`
and returns `AT_END` at the top of the structure (`BlueprintIteratorInwardCircle.java:55, 102`)
[VERIFIED), which puts `progressPos` back to `NULL_POS`. If `localPosition`'s (x, z) is never
produced by the spiral — an out-of-range resume position, say — the loop never terminates. I did not
construct a case that reaches it, so **[UNCHECKED]** as to reachability; the missing bound is
[VERIFIED].

**What to do.** Bound the loop (`for (int i = 0; i < size.getX() * size.getZ(); i++)`) — that is a
two-line change and closes the hang regardless. The performance fix is to keep the four ring bounds
(`min_x`, `max_x`, `min_z`, `max_z`) as derivable state: for a given (x, z) inside a rectangle, the
ring index is `min(x, z, sizeX-1-x, sizeZ-1-z)` and the bounds follow from it in O(1), so
`setProgressPos` can restore the iterator directly instead of replaying. For Hilbert, an
`Object2IntMap<BlockPos>` built alongside `positions` in `generateLayerPattern` turns the scan into
a lookup.

---

### F11 — The preview renderer submits every resolved block every frame, with no per-block culling and a pose push/pop each

**What it does now.** `sz/client/BlueprintRenderer.java:359-366` [VERIFIED]:

```java
for (final ResolvedBlock resolvedBlock : resolvedBlocks)
{
    final BlockPos blockPos = resolvedBlock.pos();
    matrixStack.pushPose();
    matrixStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    resolvedBlock.model().submit(matrixStack, collector, resolvedBlock.lightCoords(), OverlayTexture.NO_OVERLAY, 0);
    matrixStack.popPose();
}
```

The only culling is one whole-blueprint AABB test at `BlueprintRenderer.java:298` [VERIFIED]. A
`blueprintLocalFrustum` is built at `:341-342` [VERIFIED] but is used only for entities (`:376`),
never for blocks.

**Why it costs.** This is the per-frame cost of the preview and it scales with the blueprint's
visible block count, not with what is on screen. A 40×20×40 hut resolves on the order of 10 000
blocks; at 60 fps that is 600 000 `submit` calls and 1.2 M pose push/pops per second for one
preview, and `drawAtListOfPositions` multiplies it by the number of positions
(`sz/client/BlueprintHandler.java:124-127`) [VERIFIED]. The port note at `BlueprintRenderer.java:51-58`
[VERIFIED] explains why: 1.21.1 baked the whole blueprint into `VertexBuffer`s once and drew them
with two GL calls; 26.3 has no such API, so the geometry is re-submitted every frame. This is the
single largest client cost in the mod and it is a direct consequence of the port.

**What to do.** Two things, in order of payoff:

1. **Cull per 16³ section.** Group `resolvedBlocks` into sections at `init` time (they are built in
   blueprint-local coordinates, so the grouping is static), keep each section's local AABB, and test
   it against `blueprintLocalFrustum` before iterating its blocks. For a preview seen from one side
   this typically halves the submitted set, and for a large blueprint the player is standing inside
   it cuts far more. The frustum object already exists at `:341`; this is bookkeeping, not new
   machinery.
2. **Drop the push/pop.** Sort `resolvedBlocks` by position at `init` and translate by the *delta*
   to the next block instead of pushing a pose per block; the offsets are integers, so there is no
   float drift, and one `popPose` at the end restores the stack. Removes two `PoseStack` operations
   per block per frame. Whether `PoseStack.pushPose` allocates in 26.3 is **[UNCHECKED]** — I did not
   disassemble it — but even without an allocation it is a 4×4 matrix copy each way.

Neither needs an access widener or a mixin.

---

### F12 — `BlueprintBlockAccess` looks up the player by UUID per substitution block, and reads the *server* level from the client thread

**What it does now.** `sz/client/fakelevel/BlueprintBlockAccess.java:41-45, 63` [VERIFIED]:

```java
private static Level anyLevel()
{
    final Minecraft mc = Minecraft.getInstance();
    return mc.hasSingleplayerServer() ? mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).level() : mc.level;
}
...
return BlockUtils.getSubstitutionBlockAtWorld(anyLevel(), worldPos.offset(pos), levelSource.getRawBlockStateFunction().compose(b -> b.subtract(worldPos)));
```

**Why it costs.** `getPlayerList().getPlayer(UUID)` is a linear scan of the player list in vanilla,
and this runs once per solid-placeholder block during renderer `init`
(`BlueprintRenderer.java:172` → `prepareBlockStateForRendering`) [VERIFIED], on top of F1's
surface-rule compile. The `.compose(b -> b.subtract(worldPos))` allocates a composed function *and*
a capturing lambda per call.

The bigger issue is not speed. `anyLevel()` deliberately hands the **integrated server's**
`ServerLevel` to `getWorldgenBlock`, which then calls `serverLevel.getChunk(location)` and reads the
chunk's blocks and heightmaps (`BlockUtils.java:168, 174-175`) [VERIFIED] — from the client render
thread, while the server thread may be writing them. That is a cross-thread read of live server
state. It is pre-existing (26.2 does the same, `libs/structurize/26.2/.../BlueprintBlockAccess.java:63`)
[VERIFIED], and I have no crash to point at, so the practical risk is **[UNCHECKED]** — confirming
would mean running a preview of a placeholder-heavy blueprint next to active chunk generation. But it
is worth knowing about, and it is a reason to prefer the memo from F1 to be per-thread or
synchronised rather than a naive static map.

**What to do.** Hoist `anyLevel()` and the composed function out of the per-block path — both are
constant for one `init` run. Cache them in the `BlueprintBlockAccess` alongside `worldPos`.

---

### F13 — `Manager.onWorldTick` burns the whole per-tick budget after the queue empties

**What it does now.** `sz/management/Manager.java:57-75` [VERIFIED]:

```java
if (!scanToolOperationPool.isEmpty())
{
    while (count++ <= Structurize.getConfig().getServer().maxOperationsPerTick.get())
    {
        final ITickedWorldOperation operation = scanToolOperationPool.peek();
        if (operation != null && operation.apply(world))
        { ... }
    }
}
```

**Why it costs.** Once the queue drains, `peek()` returns null and the loop keeps spinning to the
budget — 1000 iterations by default (`ServerConfiguration.java:75`) [VERIFIED] — each re-reading
`maxOperationsPerTick` through two `volatile` reads and an `Integer` unbox. It is small (microseconds
per tick), and it only happens on ticks where the queue emptied, but there is no reason for it.

**What to do.**

```diff
--- a/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/management/Manager.java
+++ b/libs/structurize/26.3/src/main/java/com/ldtteam/structurize/management/Manager.java
@@
-        int count = 0;
-        if (!scanToolOperationPool.isEmpty())
-        {
-            while (count++ <= Structurize.getConfig().getServer().maxOperationsPerTick.get())
-            {
-                final ITickedWorldOperation operation = scanToolOperationPool.peek();
-                if (operation != null && operation.apply(world))
+        final int budget = Structurize.getConfig().getServer().maxOperationsPerTick.get();
+        for (int count = 0; count <= budget; count++)
+        {
+            final ITickedWorldOperation operation = scanToolOperationPool.peek();
+            if (operation == null)
+            {
+                break;
+            }
+            if (operation.apply(world))
+            {
                 ...
-            }
         }
```

---

## 2. Considered and rejected

**`BlockUtils.FREE_TO_PLACE_BLOCKS` creating a `BooleanProperty` inside a lambda**
(`BlockUtils.java:69-76`). `BooleanProperty.create("upper")` per predicate evaluation looked like a
per-block allocation, and `BlockState.getValue` on a freshly created property would not even work.
It is dead code — `grep -rn FREE_TO_PLACE_BLOCKS` over both trees finds only the declaration
[VERIFIED]. Worth deleting for hygiene; not a performance finding.

**`BlockUtils.checkOrInit`'s exception-driven filter** (`BlockUtils.java:89-99`). It calls
`canBlockSurviveWithoutSupport` on every registered block, which is a `try { canSurvive(null, null) }
catch` (`BlockUtils.java:696-703`). Hundreds of thrown-and-caught NPEs — but it runs once, guarded by
`trueSolidBlocks.isEmpty()`, on the first server tick. Milliseconds, one time. (The *staleness* half
of it is in F5.)

**`isWeakSolidBlock`'s `canSurvive(null, null)` on the per-block path** (`BlockUtils.java:672-686`).
This looked like an exception per block on the WEAK_SOLID build stage, which
`AbstractEntityAIStructure.java:416` [VERIFIED] runs per iterated position. It is not: the tag test
short-circuits first, and `WEAK_SOLID_BLOCKS` is leaves plus falling blocks
(`sz/datagen/BlockTagProvider.java:41-47`) [VERIFIED). Leaves are handled by the `instanceof
LeavesBlock` branch above it, and falling blocks inherit vanilla's `canSurvive` returning true, so
nothing throws.

**`Manager.pasteStructure` → `posList.forEach(blueprint::addBlockState)`** (`Manager.java:260`).
`addBlockState` (`Blueprint.java:281-302`) does a linear palette scan *and* a full `cacheReset(true)`
per call, which reads as O(n²) with repeated cache invalidation. It is not, in this caller: the
shape generators build a palette of exactly two states, and nothing rebuilds the cache between
`addBlockState` calls, so the resets are just field writes. It *would* be O(n²) for any caller with a
large palette; there is no such caller today.

**`RenderingCacheKey`'s hash being recomputed per frame.** `Blueprint.hashCode`
(`Blueprint.java:864-879`) is a content hash over name/path/size, which looked like a per-frame cost
on the renderer cache lookup. `BlueprintPreviewData` caches the key object in a field and rebuilds it
only in `applyRotationMirrorAndSync` (`BlueprintPreviewData.java:330, 335-342`) [VERIFIED], and the
fields it hashes are `String`s (cached hash) and ints. Two or three nanoseconds per frame.

**`TagData.readFromItemStack` in the per-frame tag-tool render.**
`WorldRenderContext.renderTagTool` (`sz/event/WorldRenderContext.java:112-141`) calls it every frame
for whatever the player is holding. It resolves to
`itemStack.getOrDefault(ModDataComponents.TAGS_DATA, TagData.EMPTY)`
(`sz/items/ItemTagTool.java:239-242`) [VERIFIED] — a data-component map lookup with a shared
singleton default. Free.

**`ConfigValue.get()` in tight loops.** Two `volatile` reads and a null check
(`libs/blockui/26.3/.../ConfigValue.java:99-104`) [VERIFIED]. Even at 1000 calls per tick this is
noise; F13 is about the wasted loop iterations, not about the config read.

**`DataVersion.findFromDataVersion`'s linear walk** (`DataVersion.java:151-159`) over ~60 enum
constants. Once per blueprint load, dwarfed by the DFU it precedes.

**The `Files.readAllBytes` → `ByteArrayInputStream` in `getBlueprint`**
(`StructurePacks.java:427`). It buffers the whole compressed file on the heap before decompressing,
which a `BufferedInputStream` would avoid. Blueprint files are tens to hundreds of KB and this runs
on the IO pool, not the server thread. Real but not worth a line of risk; the caching in F3 removes
the call entirely for repeat loads.

---

## 3. Summary

| # | Finding | File | Impact | Effort |
|---|---------|------|--------|--------|
| F1 | Surface material rule recompiled per query; up to 30 compiles per falling block, 2 per pasted placeholder | `sz/util/BlockUtils.java:177` | High (paste, instant-build, any sand/gravel in a blueprint) | Medium — memo + hoisted per-level context |
| F2 | `buildBlockInfoCaches` is O(volume × entities); an entry + empty array per blueprint position | `sz/blueprints/v1/Blueprint.java:579` | High (one-time CPU, ~11 MB per 75k-volume blueprint, permanent) | Low — patch supplied |
| F3 | No blueprint cache; `IOPool` is single-threaded despite `maximumPoolSize = 2` | `sz/storage/StructurePacks.java:423`, `sz/util/IOPool.java:53` | High (every build start re-reads and re-fixes the file, serialised) | Low for the pool; Medium for the cache (mutable `Blueprint`) |
| F4 | 5 allocations + 2 world reads per position, at 10 000 positions per builder AI tick | `sz/placement/AbstractBlueprintIterator.java:99` | Medium-High (scales with builder count) | Low — three small changes, patch supplied for one |
| F5 | `handlerCache` is an unsynchronised `IdentityHashMap` written from client and server threads; never invalidated on tag reload | `sz/placement/handlers/placement/PlacementHandlers.java:155` | Medium (correctness first, perf second) | Trivial — patch supplied |
| F6 | Same position's state read 3–5 times per placement; `getBlockEntity` per position on every paste | `sz/placement/StructurePlacer.java:235`, `sz/util/ChangeStorage.java:86` | Medium | Low for `ChangeStorage` (patch supplied); Medium for the overload |
| F7 | `getResourceRequirements` re-fetches and deep-copies the tile-entity tag it was passed | `sz/placement/StructurePlacer.java:509` | Medium (whole-structure resource pass) | Trivial — one line, patch supplied |
| F8 | `ContainerPlacementHandler.handle` deserialises a block entity + inventory and throws it away; then load→save→load | `sz/placement/handlers/placement/PlacementHandlers.java:942`, `:1401` | Medium (every hut, chest, furnace, rack) | Low to drop the probe; Medium for the round-trip |
| F9 | `createBlueprint` does two linear palette scans, a registry reverse-lookup and a `getChunkAt` per block | `sz/blueprints/v1/BlueprintUtil.java:113` | Medium (scan tool, server thread, O(V×P)) | Low — patch supplied |
| F10 | `InwardCircle`/`Hilbert` `setProgressPos` replays or scans the whole layer, once per AI tick; unbounded `while` | `sz/placement/BlueprintIteratorInwardCircle.java:138` | Medium when those modes are selected | Low to bound; Medium to make O(1) |
| F11 | Renderer submits every resolved block every frame, no per-block culling, pose push/pop each | `sz/client/BlueprintRenderer.java:359` | High client-side, per frame per preview | Medium — section grouping at init |
| F12 | Player-list-by-UUID lookup per substitution block; server-level chunk reads from the client thread | `sz/client/fakelevel/BlueprintBlockAccess.java:41` | Low perf, non-trivial thread-safety question | Trivial to hoist |
| F13 | `Manager.onWorldTick` spins its full 1000-iteration budget after the queue empties | `sz/management/Manager.java:62` | Low | Trivial — patch supplied |

The three I would do first: **F3's one-word `IOPool` fix** (largest win per character changed),
**F2's entity bucketing** (removes an O(V×E) loop and several MB per blueprint, and the patch is
written), and **F1's memo** (the only thing standing between a creative paste and a rule-tree
compile per block).
