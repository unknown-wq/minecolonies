# Automatic colony construction — design plan

**Status: proposal, nothing implemented, no source file touched.**
Tree: `/home/user/minecolonies/26.2` (Fabric / MC 26.2). All `file:line` below are relative to
`26.2/src/main/java/com/minecolonies/` unless the path says otherwise.

---

## 0. Read this first: what I verified, and where the brief is wrong

Everything I assert about existing code was read in this tree. Three things in the brief did not
survive contact with it, and one of them changes the design.

| Brief says | Reality |
|---|---|
| "`/mc colony diagnose` reports `724 citizens have no bed: the colony only houses 276`" | That line comes from **`/mc citizen fill`**, not `diagnose`. `core/commands/citizencommands/CommandCitizenFill.java:93-98`, lang key `com.minecolonies.command.citizenfill.homeless` (`resources/assets/minecolonies/lang/manual_en_us.json:502`). `CommandColonyDiagnose` has no housing line at all — its citizen line is `%s/%s - %s employed, %s children, %s unemployed adults` (`manual_en_us.json:485`). The *number* is real and is where the brief says it is in spirit: `getCurrentCitizenCount() - getMaxCitizens()`. |
| "A builder takes an order and erects a blueprint" — implying an order can be created for a building that does not exist yet | `WorkOrderBuilding.create(type, building)` takes an **existing `IBuilding`** (`core/colony/workorders/WorkOrderBuilding.java:52-82`). There is no path from "I want a house here" to a work order that does not go through *registering a building first*. Auto-build therefore has to place a hut block and register a level‑0 building, exactly as a player does, and only then request the order. This is the single most structural fact in the design. |
| "gate it behind research like `civilian/rails` / `civilian/boats`" | Those are fine precedents (`core/generation/defaults/DefaultResearchProvider.java:419-436`), but research in this mod is **one-way** — once completed it cannot be undone without a reset item. It is an unlock, not an off switch. I use a town-hall setting for the off switch and argue against a research gate for v1 (§10). |

Two more things I checked rather than assumed, because the design leans on them:

* **Residence footprints do not change with level.** I decoded the blueprint NBT of every shipped pack:
  `residence1` … `residence5` have byte-identical `size_x/size_y/size_z` in all 20 packs that ship them
  (colonial 16×17×16, medievaloak 14×19×15, nordic 15×24×11, …). So a site chosen for level 1 is
  automatically valid for level 5, and an in-place upgrade can never fail for lack of room. Method:
  gzip + NBT parse of `resources/blueprints/minecolonies/*/fundamentals/residence*.blueprint`.
* **Blueprints carry their own ground reference.** `colonial/fundamentals/residence1.blueprint` has
  `optional_data.structurize.primary_offset = (6,3,8)` and a `groundlevel` positioned tag at
  `(-1,-3,5)` relative to the anchor. Structurize turns that into
  `BlueprintTagUtils.getGroundAnchorOffset(blueprint, 1)`
  (`/workspace/structurize/26.2/src/main/java/com/ldtteam/structurize/blueprints/v1/BlueprintTagUtils.java:121-131`),
  which is what the build tool itself uses to snap the preview to the ground
  (`.../client/gui/AbstractBlueprintManipulationWindow.java:587-611`). I do not have to invent a
  Y-placement rule; I reuse that one.

**Not verified — assumptions I am making:**
* I did not boot a server. Nothing below is measured; the cost numbers in §11 are budgets derived from
  the code paths, not from a profiler. `testworlds/colony-1000.zip` cannot validate this feature anyway —
  its buildings have **no blueprints** (`testworlds/README.md`), which is precisely the input this
  feature needs. Verification needs a fresh small colony.
* I assume `StructurePlacer`'s CLEAR stage removes everything inside the blueprint box that is not in the
  blueprint (that is what `BuildingProgressStage.CLEAR` and the ship-raid clearing imply). I read the
  stage enum (`core/entity/ai/workers/util/BuildingProgressStage.java:8-15`) but not the whole placer.
  If that is wrong, the "what happens on rough terrain" answer in §6.4 gets *better*, not worse.
* I assume the two bottom "solid substitution" layers of a residence blueprint are meant to be buried in
  the terrain. The colonial residence1 bottom layer is 150/256 `structurize:blocksolidsubstitution`,
  which strongly implies it, and the `groundlevel` tag sits on that layer.

---

## 1. What the mechanism does

One new per-colony manager. Every 500 server ticks (25 s), on a colony that is loaded and has the
setting on, it does this and nothing else:

```
1. read two ints  →  is there a housing shortage?        (O(1), §4)
        no → return, cost is two field reads
2. is there already an auto-order outstanding?           (O(pending), ≤2)
        yes → return
3. can I upgrade an existing residence instead?          (O(buildings), §5)
        yes → request an UPGRADE work order, return
4. survey ≤16 grid cells for a legal site                (§6, budgeted, resumable)
        none this pass → remember the cursor, return
5. place the residence hut block + register the building (§7)
6. request a BUILD work order for it                     (§8)
```

Everything after step 5 is existing machinery: the existing `WorkManager` hands the order to an
existing builder, the existing `BuildingResourcesModule` raises the material requests, the existing
`LivingBuildingModule.onColonyTick` (`core/colony/buildings/modules/LivingBuildingModule.java:68-89`)
moves homeless citizens in once the house is finished. **The feature adds a trigger and a siting
policy. It adds no placement path, no request path and no AI.**

---

## 2. Shape of the code

Two new classes plus small edits. Deliberately split in two, because the second half is the piece the
parallel "colony needs" design will want to reuse (§12):

| New file | Role | ~lines |
|---|---|---|
| `core/colony/managers/AutoBuildManager.java` | the trigger, the upgrade-vs-place policy, the pending-order bookkeeping, the give-up latch | 320 |
| `core/colony/managers/BuildingSiteSurveyor.java` | given a `BuildingEntry` + a loaded `Blueprint`, find a legal anchor position and rotation inside the colony; resumable, budgeted | 300 |

`BuildingSiteSurveyor` knows nothing about housing. `AutoBuildManager` is its only caller today.

---

## 3. Hook points — where each piece attaches

| What | Where | Change |
|---|---|---|
| Tick the manager | `core/colony/Colony.java:402-429` (state-machine constructor) | one more `TickingTransition<>(ACTIVE, this::tickAutoBuild, () -> ACTIVE, MAX_TICKRATE)`; `MAX_TICKRATE` is 500 (`api/entity/ai/statemachine/tickratestatemachine/TickRateConstants.java:11`) and `TickingTransition` clamps anything larger, so slower cadences need an internal counter |
| Own the manager | `core/colony/Colony.java:374-400` (constructor) | one field + one getter, next to the other 13 managers |
| Persistence | — | **none.** All state is derivable from `buildings` at load; the survey cursor may restart at ring 0 (§6.6) |
| Housing shortage number | `core/colony/managers/CitizenManager.java:420-456` `calculateMaxCitizens()`, `:506-509` `getMaxCitizens()`, `:536-540` `getCurrentCitizenCount()` | **read only**, no change |
| Beds per residence | `core/colony/buildings/modules/LivingBuildingModule.java:106-109` — `getModuleMax() == building.getBuildingLevel()` | read only |
| Residence definition | `apiimp/initializer/ModBuildingsInitializer.java:246-254` — `ModBuildings.HOME_ID = "residence"`, max level 5, modules `HOME`/`LIVING`/`BED`, block `ModBlocks.blockHutHome` (registry path `blockhutcitizen`) | read only |
| Default blueprint path | `api/compatibility/newstruct/BlueprintMapping.java:140` `getPathMapping("", "residence")` → `fundamentals/residence`; already used as the fallback at `core/colony/buildings/AbstractSchematicProvider.java:230-234` | read only |
| Async blueprint load | `api/util/ColonyUtils.java:56-105` `queueBlueprintLoad` — IO pool, callback marshalled back to the server thread by `ServerFutureProcessor` | read only |
| Ground offset from blueprint | `/workspace/structurize/…/blueprints/v1/BlueprintTagUtils.java:121-131` | read only |
| Footprint box from anchor+rotation | `api/util/ColonyUtils.java:123-141` `calculateCorners` | read only |
| Flat-area test primitive | `core/colony/events/raid/pirateEvent/ShipBasedRaiderUtils.java:211-275` `isSurfaceAreaMostlyMaterial` (percentage tolerance + 5-block headroom check) | reuse as-is for the fine pass |
| Chunk-loaded guard | `api/util/WorldUtil.java:51-76` | read only |
| Colony border | `Colony.java:1352-1363` `isCoordInColony` → `ColonyUtils.getOwningColony(chunk,pos)`; claim bookkeeping `core/util/ChunkDataHelper.java:150-235` | read only |
| **Place the hut** | `api/blocks/AbstractBlockHut.java:113-132` `onBlockPlacedByBuildTool(level,pos,state,placer,stack,rotMir,pack,path)` — sets pack/path/rotation on the TE **then** calls `setPlacedBy` | **called** by the new code. This is the whole placement API and it already exists |
| Register the building | `api/blocks/AbstractColonyBlock.java:281-307` → `RegisteredStructureManager.addNewBuilding` `core/colony/managers/RegisteredStructureManager.java:561-614` (copies pack/path/rotation off the TE, claims chunks, recalculates max citizens) | reached via the above |
| **Create the order** | `core/colony/buildings/AbstractBuilding.java:447-530` `requestWorkOrder(WorkOrderType, BlockPos)` — already player-free | **needs a public entry point**, see below |
| Order → builder | `core/colony/workorders/WorkManager.java:389-420` `onColonyTick` + `:427-468` `tryAssignWorkOrder` (every 20 ticks) | read only |
| Off switch | `core/colony/buildings/workerbuildings/BuildingTownHall.java:67-86` (setting keys), `core/colony/buildings/modules/BuildingModules.java:520-527` (`TOWNHALL_SETTINGS`), `core/client/gui/townhall/WindowSettings.java:28-38,52-64`, `resources/assets/minecolonies/gui/townhall/layoutsettings.xml` (rows at y=100..180, next free row y=200, window is 243 tall — it fits) | +1 `BoolSetting`, +1 GUI row |
| Config | `api/configuration/ServerConfiguration.java:129-151` (`gameplay` category) | +4 values |
| Observability | `core/commands/colonycommands/CommandColonyDiagnose.java:160-195` | +1 report section |

### The one genuinely new API on an existing class

`AbstractBuilding.requestWorkOrder` is `protected`, and the public wrapper `requestUpgrade(Player,
BlockPos)` (`AbstractBuilding.java:796-828`) **cannot be called with a null player**: every guard inside
it does `MessageUtils.format(...).sendTo(player)`, and `MessageUtils`' varargs `sendTo` dereferences each
element (`api/util/MessageUtils.java:271-287`) — a null player NPEs. So:

* add `boolean requestAutomaticWorkOrder(WorkOrderType type)` to `IBuilding` /
  `AbstractBuilding` — the research gate and level bounds from `requestUpgrade` minus the player
  messages, then `requestWorkOrder(...)`. ~25 lines.
* change `AbstractBuilding.requestWorkOrder` to return `boolean` instead of `void`. It currently
  reports failure only by chatting at the colony and returning; auto-build has to know, or it will
  place a hut block and never find out no order was created. 4 call sites in `AbstractBuilding`,
  1 override in `BuildingBarracksTower.java:74`. Small and contained, but it *is* a signature change
  on shared code and belongs in the report as such.

---

## 4. The trigger, and what it costs

**Signal:** `deficit = citizenManager.getCurrentCitizenCount() + reserve - bedCount`.

* `getCurrentCitizenCount()` is `citizens.size()` — a field read (`CitizenManager.java:536-540`).
* `bedCount` is the `maxCitizens` field maintained by `calculateMaxCitizens()`
  (`CitizenManager.java:420-456`), which is O(buildings) and is already re-run on every building
  add/remove, every level change and every home assignment. **Auto-build never calls it.**
* `reserve` is a config (default 2): build a little ahead so the colony grows instead of merely
  catching up. Without it the feature is purely reactive and a healthy colony never triggers it,
  because `ReproductionManager` already refuses to spawn past the bed count
  (`core/colony/managers/ReproductionManager.java:92`).

**Cost of the trigger: two field reads and an int compare, once per 500 ticks per loaded colony.**
That is the honest number, and it is the reason the design is shaped this way. Concretely: no citizen
iteration anywhere in this feature — not in the trigger, not in the policy, not in the survey.

Deliberately **not** used as the trigger:
* *counting citizens with `getHomeBuilding() == null`* — semantically the truest number, but O(citizens),
  i.e. 1000 iterations per check on the fixture colony. Rejected. If it is ever wanted, it should be an
  incrementally maintained counter on `CitizenManager`, updated in `CitizenData.setHomeBuilding`,
  not a scan.
* *`getMaxCitizens()`* rather than the raw field — `getMaxCitizens()` clamps against the research cap
  and `maxcitizenpercolony` (`CitizenManager.java:506-509`), so it reports "shortage" when the colony is
  merely at its population cap. Using it as the trigger would make a capped colony build forever.
  Use the raw bed count; use the clamped value only for the stop condition (§9).

---

## 5. What it builds, and at what level: upgrade before placing

A residence houses exactly `buildingLevel` citizens (`LivingBuildingModule.java:106-109`), levels 1–5.
So an upgrade L→L+1 and a fresh level‑1 build both yield **+1 bed**. They are not equally good.

**Policy: upgrade if any residence can be upgraded; place a new one only when none can.**

Upgrade candidate = a `residence` building where all of:
* `getBuildingLevel() >= 1` and `< getMaxBuildingLevel()` (5);
* no work order already exists at its position — `requestWorkOrder` checks this itself
  (`AbstractBuilding.java:449-455`);
* some staffed `BuildingBuilder` can build the target level —
  `AbstractWorkOrder.canBeResolved(colony, level)` (`core/colony/workorders/AbstractWorkOrder.java:867-874`)
  and `WorkOrderBuilding.tooFarFromAnyBuilder` (`WorkOrderBuilding.java:186-195`, 100-block radius);
* the hut research gate passes (`ResearchManager.getResearchEffectIdFrom(block)`,
  `core/colony/managers/ResearchManager.java:135-138`).

Pick the **lowest-level** candidate, tie-broken by distance to the town hall.

Why upgrade first — three reasons, in order of weight:

1. **It is free of the only genuinely risky step.** No siting, no terrain, no chance of bulldozing
   something the player built. Verified: the footprint is identical at every level in every shipped
   pack (§0), so an in-place upgrade cannot fail geometrically.
2. **It does not make pathfinding worse.** `AI-SCALE-AUDIT.md` §1.2 measured that `PathJobMoveToLocation`
   accounts for 72.7 s of the 77.4 s of pathfinding-pool work in a 90 s window, at 8.95 ms per job, with
   96 % of those jobs failing to reach the destination. A feature whose default behaviour is *scatter
   more huts further from the centre* attacks the mod's worst measured hot spot. Upgrading in place does
   not move anything.
3. Materials: the builder computes required resources by diffing the world against the blueprint
   (`core/entity/ai/workers/AbstractEntityAIStructureWithWorkOrder.java:239-360`), so upgrading a
   standing house costs the delta, while a fresh build costs everything plus clearing.

Cost: O(buildings) filter over `getServerBuildingManager().getBuildings().values()` — 148 entries on the
fixture colony — once per 500 ticks, and only when there *is* a deficit. Negligible.

Counter-argument, stated honestly: an upgrade-only colony saturates at `5 × residences` beds and then
stalls forever behind the placement path anyway. And a level‑5 residence is a lot of material for +1 bed.
That is why placement exists at all, and why §9 gives the player a knob (`autobuildpreferupgrade`).

---

## 6. Where it builds — the hard part

### 6.1 Candidate generation: a grid, not random sampling

The blueprint's rotated footprint is W×L (12×11 to 23×20 across shipped packs). Lay a **square grid over
the colony**, cell size `max(W, L) + 2`, origin at the town hall, axis-aligned. Walk cells outward in
Chebyshev rings from the town hall, keeping a persistent cursor.

Why a grid rather than the random-sample-then-test approach the raid code uses
(`RaidManager.calculateSpawnLocation`, `RaidManager.java:606-686`):
* **Non-overlap between auto-placed buildings is structural, not tested.** One building per cell.
* Deterministic and resumable: the cursor is one `int` ring + one `int` index; a budget of 16 cells per
  pass is trivially enforced and the next pass continues where the last stopped.
* It produces a village, not a sprawl. Rings mean the colony grows outward evenly and stays compact,
  which is the pathfinding argument again.
* Rejected alternative — *random position + AABB test against every building*: O(buildings) per sample,
  no progress guarantee, and it yields the scattered layout that makes paths long.

### 6.2 The filter chain (cheapest test first)

Per candidate cell, in this order; any failure skips to the next cell:

| # | Test | Cost |
|---|---|---|
| 1 | cell already carries an auto-placed building (in-memory `LongOpenHashSet` of packed cell coords, rebuilt from `buildings` at load) | hash lookup |
| 2 | all chunks the footprint touches are **loaded** — `WorldUtil.isBlockLoaded` (`api/util/WorldUtil.java:51-76`). **Never** touch an unloaded chunk: `Level#getChunk` generates synchronously | ≤4 map lookups |
| 3 | every touched chunk is owned by this colony — `ColonyUtils.getOwningColony(chunk, pos)`. This is the colony border, and it is chunk-granular | ≤4 |
| 4 | within 100 blocks of a staffed `BuildingBuilder` hut. **Mandatory** — `WorkOrderBuilding.MAX_DISTANCE_SQ = 100*100` (`WorkOrderBuilding.java:35,156-167`) and `requestWorkOrder` refuses outright otherwise (`AbstractBuilding.java:473-478`). Skipping this test means placing a hut block for an order that is never created | O(builders) |
| 5 | footprint box (inflated by 1) intersects no existing building's `getCorners()` box | O(buildings) |
| 6 | **coarse terrain**: sample the footprint every 2 blocks (≈64 columns for 16×16) with `level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z)`. Take the median `G`; require ≥85 % of samples within `G±1`. `OCEAN_FLOOR` is `Usage.LIVE_WORLD` and `keepAfterWorldgen()` is true (`/opt/mc-src/net/minecraft/world/level/levelgen/Heightmap.java:145-182`), so on a loaded chunk this is an array read — **zero block reads** | ≈64 array reads |
| 7 | **water**: `getHeight(MOTION_BLOCKING, …) > getHeight(OCEAN_FLOOR, …)` at a column means fluid above the floor. Require ≤2 % of columns wet. Still zero block reads | ≈64 |
| 8 | **block entities**: iterate `chunk.getBlockEntities()` for the ≤4 touched chunks and reject if any lies inside the footprint. Cheap, and it catches chests, furnaces, signs, beds — i.e. most of what "the player built something here" looks like | O(BEs in 4 chunks) |
| 9 | **fine terrain / player-work veto** (only for the one surviving candidate): reuse `ShipBasedRaiderUtils.isSurfaceAreaMostlyMaterial` (`ShipBasedRaiderUtils.java:211-275`) with a natural-ground predicate list (`BlockTags.DIRT`, `SAND`, `GRAVEL`, `BASE_STONE_OVERWORLD`, `TERRACOTTA`, snow) at `y = G`, tolerance 90 %. ≈256 `getBlockState` calls on one candidate | ≈256 block reads, once |

### 6.3 Y placement

`anchorY = G + BlueprintTagUtils.getGroundAnchorOffset(blueprint, 1)`.

For colonial `residence1` that is `G + 3`, which puts the blueprint's bottom layer — the one that is
59 % `structurize:blocksolidsubstitution` — exactly at the terrain surface, and the floor one block
above it. This is the same call the build tool makes to snap its preview; I am not inventing a rule.

Off-by-one warning I could not settle from source alone: the build tool does `--groundOffset; //
compensate for clicking the top face of the ground block`
(`AbstractBlueprintManipulationWindow.java:609`). Whether `G` should be the top solid block or the first
air block above it must be checked once in a running world by placing one house and looking at it.
It is a one-line fix either way, but it is the difference between a house on the ground and a house
one block sunk.

### 6.4 Rough terrain, water, existing buildings — the direct answers

* **Rough terrain: it does not build there.** Filter 6 rejects anything that is not already within ±1
  over 85 % of the footprint. There is no terraforming step and I am not proposing one — the mod has no
  levelling machinery and writing one is a bigger feature than this whole plan. The builder's CLEAR
  stage will still shave the few high blocks inside the box, which is what the 15 % tolerance is for.
* **Water: it does not build there.** Filter 7. No pier support, no drainage. A colony on a lake shore
  builds inland.
* **Against existing buildings: cannot happen.** Filters 1 and 5, plus the grid's one-building-per-cell
  invariant.
* **Against things the player built that are not registered buildings** (roads, farms, decorations
  placed with the build tool, a chest under a tree): filters 8 and 9 catch most of it and **will not
  catch all of it**. This is the honest limit of the design and the main reason the feature is off by
  default (§10). A player-drawn exclusion zone is the obvious follow-up; I am not designing it here.

### 6.5 Rotation

Face the building toward the town hall: pick the cardinal `Rotation` whose facing best matches the
vector from site to `colony.getCenter()`, mirror `NONE`. Passed to `onBlockPlacedByBuildTool` as a
`RotationMirror`, which is where the TE and then the building and then the work order pick it up
(`RegisteredStructureManager.java:580`, `WorkOrderBuilding.java:71-79`).

### 6.6 Budget and give-up

* ≤16 cells examined per pass; ring cursor persists in memory across passes.
* After `autobuildmaxrings` rings (default 6 — beyond that the 100-block builder radius makes it moot
  anyway) or after N consecutive fruitless full sweeps, set a **give-up latch**: stop surveying entirely
  and say so once in chat and once in the log. The latch clears when a building is added or removed or
  the town hall changes level — i.e. when the answer might have changed. Without this, a colony walled
  in by mountains pays for a survey every 25 s forever.

---

## 7. Where the blueprint comes from

Three sources, tried in order, resolved **once per colony** and cached:

1. **Clone an existing residence in the colony.** Take any built `residence` and read its
   `getStructurePack()` + `getBlueprintPath()` (`api/colony/buildings/ISchematicProvider.java:72,86`),
   strip the trailing level digit and substitute `1` — exactly the rewrite `WorkOrderBuilding.create`
   already performs (`WorkOrderBuilding.java:69-70`). This is by construction a valid residence
   blueprint in this colony's pack, and it is the variant the player chose. If several distinct paths
   exist, pick the most common one.
2. **The default mapping**: pack `colony.getStructurePack()` (`Colony.java:1563`), path
   `BlueprintMapping.getPathMapping("", "residence") + "1.blueprint"` = `fundamentals/residence1.blueprint`
   — the same fallback `AbstractSchematicProvider.java:230-234` uses.
3. **Nothing.** Disable auto-build for this colony, one log line, one chat line.

Source 2 alone is **not** sufficient and this is measured, not guessed: of the 23 shipped packs,
20 have `fundamentals/residence1.blueprint`; `cavern` puts it under `fundamentals/default/`,
`sandstone` names it `residencelarge`/`residencetower`/`residenceluxury*`, and `shire` names it
`house`. Third-party packs will be worse. Source 1 is what makes the feature work on those.

Rejected: **scanning the pack for blueprints whose anchor is `blockhutcitizen`**. That is what the
client GUI does (`core/client/gui/WindowBuildingBrowser.java:54,103` — four IO worker threads and a
progress bar to walk one pack). It is disk IO measured in seconds. It has no place on a server tick.

**Validation before anything is placed.** The chosen path is loaded once via
`ColonyUtils.queueBlueprintLoad` (`api/util/ColonyUtils.java:56-105`) — IO pool, callback marshalled
back to the server thread by `ServerFutureProcessor` — and the result is checked:
`blueprint.getBlockState(blueprint.getPrimaryBlockOffset()).getBlock() == ModBlocks.blockHutHome`.
If it is not, source 1 is discarded and source 2 tried. The cached `Blueprint` is what supplies the
footprint (§6.1) and the ground offset (§6.3), so **no site is ever surveyed before a blueprint is in
hand.** This is the port's own "a building registered without a blueprint misbehaves" lesson applied
up front: we never register a building whose blueprint we have not already loaded and verified.

---

## 8. Placing it, and who pays

**Placement** — three calls, all existing:

```java
world.setBlockAndUpdate(anchorPos, ModBlocks.blockHutHome.defaultBlockState().setValue(FACING, facing));
((AbstractBlockHut<?>) ModBlocks.blockHutHome)
    .onBlockPlacedByBuildTool(world, anchorPos, state, null, null, rotMir, pack, path);  // AbstractBlockHut.java:113-132
building.requestAutomaticWorkOrder(WorkOrderType.BUILD);                                  // §3
```

`onBlockPlacedByBuildTool` sets pack/path/rotation on the TE and then calls `setPlacedBy`, which
registers the building through `RegisteredStructureManager.addNewBuilding`
(`AbstractColonyBlock.java:281-307`, `RegisteredStructureManager.java:561-614`). That call also claims
the chunks, recalculates max citizens and notifies subscribers. Nothing new.

If `requestAutomaticWorkOrder` returns false (no builder in range, level too high for any builder,
box outside the colony — `WorkManager.isWorkOrderWithinColony`, `WorkManager.java:347-382`), **remove
the hut block and destroy the building again** so a failed attempt leaves no litter, mark the cell
unusable, and move on. This is the single most important error path in the feature; without it a
misconfigured colony accumulates unbuildable level‑0 huts.

**Who pays.** The existing request system, unchanged. The builder claims the order, the
`BuildingResourcesModule` raises `Stack` requests bucket by bucket
(`core/colony/buildings/AbstractBuildingStructureBuilder.java:430-438`), the warehouse and couriers
serve them, and anything the colony cannot produce falls to the player resolver or the retrying
resolver — visible in `/mc colony diagnose`'s request section. `FreeMode` (`core/debug/FreeMode.java`)
still short-circuits all of it for testing.

So: **if the colony cannot afford it, the order queues and the builder waits.** That is the same
outcome as a player-issued order and it needs no new code. What it does need is a bound, because a
waiting builder is a blocked builder:

* **At most `autobuildmaxpending` auto-orders exist at once, default 1.** So auto-build can occupy at
  most one builder, ever, by default.
* An auto-order is only created when **at least one `AbstractBuildingStructureBuilder` has a staffed
  worker and no current work order** (`AbstractBuildingStructureBuilder.hasWorkOrder()`). Auto-build
  never competes with a player order for a busy builder.
* Note for the reviewer: work-order priority does **not** help here. `getPriority()` is only consulted
  by `WorkManager.getOrderedList`, whose sole caller is the miner
  (`MinerLevelManagementModule.java:120`, `EntityAIStructureMiner.java:206`). Builders claim in
  `LinkedHashMap` insertion order in `tryAssignWorkOrder` (`WorkManager.java:427-468`). Our order
  therefore lands at the back of the queue, which is the behaviour we want, but it is FIFO, not
  priority. A plan that promised "low priority so it never gets in the way" would be wrong.
* Builders already have a per-hut opt-out: the `MODE` setting set to `MANUAL_SETTING`
  (`core/colony/buildings/workerbuildings/BuildingBuilder.java:44,50`), which `tryAssignWorkOrder`
  honours (`WorkManager.java:455-459`). Nothing to add.

**The hut block item itself is free** — see the open question in §14.

---

## 9. What stops it

Five independent bounds; the first is the one that actually terminates the loop.

1. **Population cap.** Stop when `bedCount >= min(researchCap, maxcitizenpercolony)`
   (`CitizenManager.java:506-529`; config default 250). Beyond that, beds cannot turn into citizens, so
   there is nothing to chase. The build→bed→citizen→build loop is therefore bounded by an existing,
   player-visible, already-configured number.
2. **Concurrency cap:** `autobuildmaxpending`, default 1 (§8).
3. **Geometry:** the colony border (filter 3), the 100-block builder radius (filter 4), the ring cap
   and the give-up latch (§6.6). `maxColonySize` (20 chunks, `ServerConfiguration.java:173`) bounds the
   border itself.
4. **Total cap:** `autobuildmaxbuildings`, default 0 = unlimited, counting only auto-placed buildings.
   For the player who wants "a few, then stop".
5. **The off switch** (§10), which is checked before anything else.

---

## 10. How the player turns it off

* **Town-hall setting, default OFF.** A `BoolSetting` beside `MOVE_IN`, `AUTO_HIRING_MODE`,
  `AUTO_HOUSING_MODE`, `CONSTRUCTION_TAPE` (`BuildingTownHall.java:67-86`, registered in
  `BuildingModules.java:520-527`, one GUI row in `layoutsettings.xml` at y=200 and two lines in
  `WindowSettings.java`). Read as `colony.getSettings().getSetting(AUTO_BUILD_MODE).getValue()` — a
  map lookup, and it is the *first* thing the tick does. `SettingsModule.deserializeNBT`
  (`core/colony/buildings/modules/SettingsModule.java:57-80`) keeps the default for keys not present in
  an old save, so adding a setting is save-compatible.
* **Server config `autobuild` (default true)** in the `gameplay` category — the server owner's master
  kill switch for the whole feature.
* Per-builder: the existing `MANUAL_SETTING` mode already excludes a builder from all automatic
  assignment.

Default OFF is a deliberate recommendation, not timidity: §6.4 admits the design can bulldoze
unregistered player work, and a feature that can do that must be opted into.

**Rejected: gating it behind a research node.** Research in this mod is one-way; `civilian/boats` is an
unlock, not a toggle, so it cannot serve as the off switch and a player who dislikes auto-build would be
stuck with the *option* forever. A research node is a fine way to make it *earnable* later (an
"Urban Planning" node under `civilian`, effect id `effects/autobuildunlock`, checked with
`getResearchEffects().getEffectStrength(...) > 0` exactly like `RAILS`/`BOATS` in
`core/entity/citizen/EntityCitizen.java:840,859`) — but it should be added on top of the setting, not
instead of it, and not in v1.

---

## 11. What it costs per tick

Read against `AI-SCALE-AUDIT.md`: 32.2 ms/tick at 1000 citizens, pathfinding pool at 86 % of one core,
and the audit's own negative result that the AI state machine is 1.21 % of the server thread.

| Case | Frequency | Work |
|---|---|---|
| Setting off, or config off | every 500 ticks | one map lookup |
| No deficit (the normal case) | every 500 ticks | two field reads + compare |
| Deficit, pending order outstanding | every 500 ticks | one small collection scan (≤2) |
| Deficit, upgrade found | every 500 ticks | O(buildings) filter — 148 on the fixture — then one `requestWorkOrder` |
| Deficit, survey pass | every 500 ticks | ≤16 cells × (≤8 map lookups + ≈128 heightmap array reads) ≈ **2 000 array reads**, plus ≈256 `getBlockState` on at most one candidate |
| Placement | at most once per 500 ticks, capped at 1 outstanding | one `setBlockAndUpdate`, one `addNewBuilding`, one blueprint box chunk walk in `isWorkOrderWithinColony` |

**Nothing in this feature iterates citizens.** The most expensive periodic path is the survey, at
roughly 2 000 chunk-array reads once per 25 s — around 80 reads/second, against a tick budget in which
`LevelChunk.getBlockState` alone already accounts for 8.87 %. It is not measurable.

Two second-order costs I will not hide:
* Every extra work order costs `WorkManager.onColonyTick` an O(buildings) sweep every 20 ticks
  (`WorkManager.java:389-420` runs `tryAssignWorkOrder` twice per order). With `autobuildmaxpending = 1`
  that is one extra order — but note this loop is already O(orders × buildings) every second and is a
  pre-existing scaling smell worth its own audit item.
* `WorkManager.isWorkOrderWithinColony` calls `world.getChunk(x, z)` — a *blocking, generating* chunk
  fetch — for every chunk the box touches (`WorkManager.java:347-382`). Our survey already required
  those chunks loaded, so in practice it hits the cache; but it fires once per order creation and that
  is a real cost that belongs in the ledger.

---

## 12. Relationship to the parallel "colony needs" design

**They should be two things, and this is the seam.**

My feature splits cleanly into a *demand* half and a *supply* half:

* **Demand** — §4, all of it: two field reads and a config reserve, about 30 lines. This is exactly the
  part a general needs model replaces. When that model exists, `AutoBuildManager` stops computing
  `deficit` and instead reads `needs.get(HOUSING)`; the type it needs back is
  `(BuildingEntry, count, urgency)` and nothing else. Everything downstream is untouched.
* **Supply** — §5 through §8: pick upgrade vs place, find a legal site, place the anchor, create the
  order, handle the failure path, respect the caps. This is the half that is genuinely hard, and
  **nothing in it is housing-specific except the choice of `BuildingEntry`.** `BuildingSiteSurveyor`
  already takes a `BuildingEntry` and a `Blueprint`; `AutoBuildManager`'s placement half would need only
  a signature widening to serve "build a farm", "build a second warehouse", "upgrade the smeltery".

So my argument to the other agent: a needs list that says "the colony lacks 40 beds and 2 farms" is
worthless without something that can put a farm on the ground, and that something is the hard part.
Merging the two into one class would bury a 300-line siting problem inside a bookkeeping system and
make both harder to review. Keep the list; consume this service.

If that design does not land, this feature stands alone unchanged — the demand half is 30 lines and
already written into this plan.

---

## 13. Open questions, with my recommendation for each

1. **Does auto-build pay for the hut block item?**
   *Recommendation: no, v1 places it free.* The real cost is the ~600 blocks of build material, which
   the request system already charges. Charging a crafted `blockhutcitizen` from the warehouse needs a
   new withdrawal path and creates a silent failure mode ("nothing happens and the player cannot tell
   why"). Add `autobuildchargehutblock` later if playtesting says it is too cheap. *Also unresolved:*
   `HutPlacementHandler.getRequiredItems` (`core/placementhandlers/HutPlacementHandler.java:123-142`)
   *does* list the hut block as a required item, but the requirements pass only walks blocks that do
   not already match the world — so a pre-placed anchor is probably skipped. Worth confirming in-world;
   if it is not skipped, the colony pays for the hut block automatically and this question answers
   itself.

2. **Reactive (house the homeless) or proactive (grow)?**
   *Recommendation: both, via the `reserve` config, default 2.* Reserve 0 makes it purely reactive; a
   healthy colony then never triggers it, because reproduction is already gated on bed count. Reserve 2
   makes the colony grow by itself, which is what "builds on its own" means to a player.

3. **Upgrade-first or place-first?**
   *Recommendation: upgrade-first, config `autobuildpreferupgrade` (default true).* Argued in §5; the
   pathfinding evidence in `AI-SCALE-AUDIT.md` §1.2 is the strongest part of that argument.

4. **Should it also build the *builder* it needs?** A colony with no staffed builder can never
   auto-build anything (filter 4). *Recommendation: no.* Bootstrapping the first builder is a player
   decision, and a self-placing builder hut is a much bigger blast radius. Report the condition in
   `/mc colony diagnose` instead.

5. **Should the give-up latch tell the player?**
   *Recommendation: yes, once, via `MessageUtils…sendTo(colony).forAllPlayers()`* — the same channel
   `requestWorkOrder` already uses for `BUILDER_TOO_FAR_AWAY`. Silence here is the difference between
   "the feature is off" and "the feature is broken" from the player's chair.

6. **Grid origin at the town hall, or at the centroid of existing buildings?**
   *Recommendation: town hall.* It is stable across the colony's life; a centroid moves every time
   something is built, which would invalidate the cell set.

7. **Does `G` mean the top solid block or the first air block?** (§6.3.) *Recommendation: settle it by
   building one house in a live world before writing the rest.* It is one line, but it is the line that
   decides whether the feature looks right.

---

## 14. Size estimate

| | files | ~lines |
|---|---|---|
| `AutoBuildManager` (new) | 1 | 320 |
| `BuildingSiteSurveyor` (new) | 1 | 300 |
| `IBuilding` + `AbstractBuilding` — `requestAutomaticWorkOrder`, `requestWorkOrder` returns boolean | 2 | 35 |
| `Colony` wiring | 1 | 12 |
| Town-hall setting: `BuildingTownHall`, `BuildingModules`, `WindowSettings`, `layoutsettings.xml`, lang | 5 | 35 |
| `ServerConfiguration` (4 values) | 1 | 10 |
| `CommandColonyDiagnose` section | 1 | 40 |
| **Total** | **12 (2 new)** | **≈750** |

Roughly three days of implementation for one agent, plus a day of in-world verification that cannot be
skipped and cannot use `colony-1000.zip` (§0). The client half — the one GUI row — **cannot be tested in
this container at all**: there is no display and `runClient` does not start.

---

## 15. Risks, worst first

1. **It destroys unregistered player work.** Filters 8 and 9 catch block entities and non-natural
   surface blocks; they will not catch a cobblestone path or a hand-built wall. Mitigated by
   default-off, by the box-overlap and block-entity vetoes, and by the fact that construction tape marks
   the site before the builder starts (`WorkOrderBuilding.onAdded` → `ConstructionTapeHelper`,
   `WorkOrderBuilding.java:263-274`), giving the player a visible warning and time to cancel. Not
   eliminated. This is the reason for every conservative choice above.
2. **Sprawl degrades pathfinding**, the mod's worst measured problem. Mitigated by upgrade-first, the
   ring cap, the compact grid and the 100-block builder radius. Should be re-measured after the feature
   exists, on a *played* colony, not the fixture.
3. **A stuck auto-order blocks a builder indefinitely** if the colony cannot source a material.
   Bounded to one builder by `autobuildmaxpending = 1`. Consider a follow-up: cancel an auto-order that
   has made no progress for N minutes. Not in v1 — cancelling half-built structures has its own hazards.
4. **Blueprint sourcing fails on an exotic pack.** Handled by the three-source chain and the
   verify-anchor step (§7); worst case the feature disables itself loudly for that colony.
5. **The failure path leaves litter.** If `requestAutomaticWorkOrder` returns false after the hut is
   placed and the rollback in §8 is buggy, the colony accumulates level‑0 huts, which `/mc colony
   diagnose` will report as buildings with no blueprint problems but which the player must remove by
   hand. This path deserves a test of its own.
6. **`requestWorkOrder` signature change** touches shared code with a `BuildingBarracksTower` override.
   Small, but it is a contract deviation and must be reported as one per `AGENT-BRIEF.md` §C6.
7. **Grid cells are not persisted**, so a restart restarts the survey at ring 0. Harmless (occupied
   cells are re-derived from `buildings`) but it means one wasted sweep after every server start. Chosen
   deliberately over adding NBT.

---

## 16. What I would verify first, if approved

In this order, because each one can invalidate the next:

1. Place one residence by hand in a fresh colony, read back
   `getStructurePack()`/`getBlueprintPath()`/`getRotationMirror()`, and confirm the §7 source-1 clone
   reproduces them.
2. Place one residence via `onBlockPlacedByBuildTool` from a command, at `G + getGroundAnchorOffset`,
   and look at where it lands (open question 7).
3. Confirm the builder actually claims the resulting order and that the material list does or does not
   include the hut block (open question 1).
4. Only then write the surveyor.
