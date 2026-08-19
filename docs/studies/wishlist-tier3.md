# Wishlist, tier 3: copper, mounts, and the sea

Estimation study, not an implementation. Date: 2026-08-15. Tree: `26.2/` at 0.0.35. Companion to
the tier-1 and tier-2 estimates being written alongside it.

Evidence standard, same as the studies before it:

* **[VERIFIED]** — I opened the file, the `path:line` is real, and it says what I claim it says.
* **[INFERRED]** — a conclusion drawn from verified code, but not itself a line I can point at.
* **[UNTESTED]** — there is no display in this container, `runClient` does not start, and I did not
  boot a server for this study. Nothing below was played. Every claim about *behaviour in a running
  game* is inference from source unless it is marked otherwise.

Paths are relative to the repository root. `26.2/src/main/java/com/minecolonies/` is abbreviated to
`mc/`. Decompiled vanilla 26.2 lives at `/opt/mc-src` and is cited in full.

---

## 0. The answer, on one page

| | Verdict | Real size | Was his diagnosis right? | The single obstacle |
|---|---|---|---|---|
| **1. Copper golem + copper chests** | **Don't build.** | 3–6 weeks for the version he described, and it cannot be finished without a mixin | **No — wrong on both halves, and right about oxidation at the wrong layer** | `TransportItemsBetweenContainers` hard-checks `instanceof ChestBlockEntity` in a method body. An AccessWidener cannot reach into a method body. |
| **2. Mounted couriers and guards** | **Guards: the code is finished, the *content* is not. Finish the content. Couriers: don't build.** | Guards: 1 recipe line + 5–120 blueprints, **no Java**. Couriers: 2–3 weeks for a feature that makes deliveries *slower* | **No — comprehensively wrong about the code, and accidentally right that the feature is missing.** Every navigation problem he listed is already solved and in the jar; the reason he has never seen a mounted guard is that **there is no stable blueprint and no stable recipe, anywhere, in this port or upstream.** | Nobody can build a Stable, so 3,000 lines of working cavalry are unreachable from a player's chair. |
| **3. Nautilus + underwater outpost** | **Don't build the outpost. The nautilus is a separate, much smaller question.** | Outpost: 6–10 weeks and a permanent maintenance tax on every AI state. Nautilus alone: 1–2 weeks | **Yes, and he is the only one of the three he got right** | `EntityAIFloat` is a priority-0 goal on every citizen that stops the navigator and *blocks it for 15 seconds* whenever an eye goes underwater. |

**The one thing on this tier I would actually do:** §2.6 — ship a Stable. Not the Java; the Java is
finished and has been for two upstream releases. One uncommented recipe line and one blueprint set,
and the mounted guards he calls "the most expensive thing on the list" turn on. This is content
work, not engine work, and it is the highest ratio of delivered-wish to spent-effort anywhere in
tier 3 by a wide margin.

---

## 1. Copper golem, and copper chests

### 1.1 His stated trap, tested line by line

He wrote three things. All three are wrong, though the third is wrong in an interesting way.

#### Claim A: "the warehouse works through racks" — **true, and more strictly than he thinks**

`TileEntityWareHouse` does not merely *prefer* racks, it is typed on them:

* `hasMatchingItemStackInWarehouse` filters `entity instanceof final TileEntityRack rack`
  (`mc/core/tileentities/TileEntityWareHouse.java:51`, again at `:81`).
* `getMatchingItemStacksInWarehouse` the same (`:113`), then calls `rack.getInventory()` (`:115`).
* `getRackForStack` **returns `AbstractTileEntityRack`** (`:171`), as do
  `getPositionOfChestWithItemStack` (`:192`), `getPositionOfChestWithSimilarItemStack` (`:219`) and
  `searchMostEmptyRack` (`:244`).
* The abstract contract itself is rack-typed: `AbstractTileEntityColonyBuilding.getPositionOfChestWithItemStack`
  (`mc/api/tileentities/AbstractTileEntityColonyBuilding.java:131`).

And the rack-specific calls those methods make are not on any vanilla interface:
`rack.getItemCount(Predicate)`, `rack.hasSimilarStack(stack)`, `rack.getFreeSlots()`,
`rack.hasItemStack(stack, 1, true)`, `upgradeRackSize()`. A `ChestBlockEntity` has none of them.
**[VERIFIED]**

Registration is stricter still. `AbstractBuildingContainer.registerBlockPosition`
(`mc/core/colony/buildings/AbstractBuildingContainer.java:158`) adds a position to `containerList`
in exactly one branch: `else if (block instanceof BlockMinecoloniesRack)` (`:174`). There is no
chest branch. `BuildingWareHouse.registerBlockPosition` (`mc/core/colony/buildings/workerbuildings/BuildingWareHouse.java:121`)
narrows further, calling `setInWarehouse(true)` and `upgradeRackSize()` only for racks. **[VERIFIED]**

So a vanilla chest — or a copper chest — placed inside a warehouse today is not "converted". It is
**invisible**. The colony never learns it exists.

#### Claim B: "building from a blueprint converts chests into racks" — **false. There is no such conversion, anywhere.**

This is the load-bearing premise of his whole design, and it does not exist.

The only function in the codebase that looks like it is
`AbstractBlockMinecoloniesRack.shouldBlockBeReplacedWithRack(Block)`
(`mc/api/blocks/AbstractBlockMinecoloniesRack.java:31`), which returns
`block == Blocks.CHEST || block instanceof AbstractBlockMinecoloniesRack`.

It has **zero callers**. Not in this port, not in the 1.21.1 NeoForge original, not upstream:

```
$ grep -rn shouldBlockBeReplacedWithRack --include=*.java 26.2/src ../1.21.1/src /workspace/ldtteam/minecolonies/src
26.2/src/.../AbstractBlockMinecoloniesRack.java:31
../1.21.1/src/.../AbstractBlockMinecoloniesRack.java:31
/workspace/ldtteam/minecolonies/src/.../AbstractBlockMinecoloniesRack.java:31
```

Three declarations, no call sites. It is dead code that has survived at least two major versions.
**[VERIFIED]**

The reason there is no conversion is that **the blueprints already contain racks**. I decompressed
and parsed the NBT of `src/main/resources/blueprints/minecolonies/medievaloak/craftsmanship/storage/warehouse5.blueprint`:

```
tile_entities: domum_ornamentum:materially_retexturable ×379,
               minecolonies:rack ×82,
               minecraft:barrel ×13, minecraft:sign ×6,
               minecolonies:warehouse ×1, minecraft:lectern ×1,
               minecraft:ender_chest ×1
palette:       minecolonies:blockminecoloniesrack at indices 39, 40, 42, 43, 59, 102
               (variants blockrackemptysingle / blockrackempty / blockrackair)
```

Eighty-two racks, one ender chest for decoration, and **not one `minecraft:chest`**. **[VERIFIED]**

`RackPlacementHandler` (`mc/core/placementhandlers/RackPlacementHandler.java:35`) claims
`blockState.getBlock() instanceof BlockMinecoloniesRack` — it handles racks that were *already
racks in the blueprint*. It does not touch chests. **[VERIFIED]**

**Consequence for the estimate:** the thing he was budgeting to fight — "turn conversion off for
that block" — costs zero, because there is nothing to turn off. The thing he was not budgeting for
— "teach the warehouse that a non-rack block entity can be a container" — is the entire job, and it
is a refactor of six return types and their transitive callers.

#### Claim C: "oxidation means the warehouse can silently break over time" — **false for the chest, true for the golem**

He put the oxidation risk on the wrong object.

**The copper chest does not lose anything to oxidation.** All five copper chest blocks share one
block entity type with the vanilla chest:

```java
public static final BlockEntityType<ChestBlockEntity> CHEST = register(
    BlockEntityTypeIds.CHEST, ChestBlockEntity::new,
    Util.copyAndAdd(Blocks.COPPER_CHEST.asList(), Blocks.CHEST));
```
`/opt/mc-src/net/minecraft/world/level/block/entity/BlockEntityTypes.java:21-22` **[VERIFIED]**

And Mojang explicitly protected the block entity across the weathering step:

```java
@Override
public boolean shouldChangedStateKeepBlockEntity(final BlockState oldState) {
    return oldState.is(BlockTags.COPPER_CHESTS);
}
```
`/opt/mc-src/net/minecraft/world/level/block/CopperChestBlock.java:140-143` **[VERIFIED]**

That is the flag `LevelChunk.setBlockState` consults at
`/opt/mc-src/net/minecraft/world/level/chunk/LevelChunk.java:307` before tearing a block entity
down. Oxidation calls `level.setBlockAndUpdate`
(`/opt/mc-src/net/minecraft/world/level/block/ChangeOverTimeBlock.java:19`), the flag returns true,
the `ChestBlockEntity` and its inventory survive. A copper chest that oxidizes from unaffected to
oxidized keeps every item. **[VERIFIED]**

A mod-side "is this a container" check written as `instanceof ChestBlockEntity` — which is exactly
what the two existing chest checks in the codebase are (`mc/core/colony/buildings/AbstractBuilding.java:1430`
and `mc/core/entity/ai/workers/AbstractEntityAIBasic.java:1176`) — is **already oxidation-proof**.
No GUI indicator is needed. **The feature he priced as a mandatory extra does not need to be
built.** **[VERIFIED]**

**The golem, on the other hand, genuinely does destroy itself.**
`CopperGolem.updateWeathering` (`/opt/mc-src/net/minecraft/world/entity/animal/golem/CopperGolem.java:259-280`)
advances one weather stage every `random.nextIntBetweenInclusive(504000, 552000)` ticks — 7.0 to 7.7
hours of *loaded, ticking* time per stage, so roughly 21–23 hours of ticking to reach OXIDIZED. Then:

```java
if (isFullyOxidized && this.canTurnToStatue(level)) { this.turnToStatue(level); }
...
private boolean canTurnToStatue(final Level level) {
    return level.getBlockState(this.blockPosition()).isAir() && level.getRandom().nextFloat() <= 0.0058F;
}
```
`:274`, `:282-284`. `turnToStatue` (`:286-305`) replaces the golem with
`Blocks.COPPER_GOLEM_STATUE`, calls `dropPreservedEquipment` and then `this.discard()`.
**[VERIFIED]**

A 0.58% roll per tick, once oxidized, is ~172 ticks — under nine seconds — of standing on air. The
golem is gone within a minute of reaching full oxidation, in practice. **[INFERRED from the rates]**

Waxing freezes it: `itemStack.is(Items.HONEYCOMB) && this.nextWeatheringTick != -2L` sets
`nextWeatheringTick = -2L` (`:232-237`), and `updateWeathering` returns immediately on `-2L`
(`:260`). So the fix for the *real* oxidation problem is one honeycomb, applied by hand, by the
player. **[VERIFIED]**

He was right that something in this feature rots. He named the wrong thing, and the thing that does
rot is fixed by an item that already exists.

### 1.2 What actually blocks it: a method body, and the no-mixin rule

Here is the wall.

The golem's item-moving behaviour is `TransportItemsBetweenContainers`, constructed in
`CopperGolemAi.initIdleActivity` (`/opt/mc-src/net/minecraft/world/entity/animal/golem/CopperGolemAi.java:75-77`)
with two predicates declared at `:43-44`:

```java
private static final Predicate<BlockState> TRANSPORT_ITEM_SOURCE_BLOCK      = b -> b.is(BlockTags.COPPER_CHESTS);
private static final Predicate<BlockState> TRANSPORT_ITEM_DESTINATION_BLOCK = b -> b.is(Blocks.CHEST) || b.is(Blocks.TRAPPED_CHEST);
```
**[VERIFIED]**

Note the asymmetry, which matters: the *source* is a **block tag** (`#minecraft:copper_chests`,
datapack-editable, so a mod could legitimately add its own block to it), but the *destination* is
two hard block references. A golem can be taught to take items **out of** a modded block with a
datapack; it can never be taught to put items **into** one that way.

You might think the AccessWidener saves you. It nearly does — `accessWidener v1` supports
`mutable field`, which strips `final`, so this is a legal three-line addition to
`src/main/resources/minecolonies.accesswidener`:

```
accessible field net/minecraft/world/entity/animal/golem/CopperGolemAi TRANSPORT_ITEM_DESTINATION_BLOCK Ljava/util/function/Predicate;
mutable    field net/minecraft/world/entity/animal/golem/CopperGolemAi TRANSPORT_ITEM_DESTINATION_BLOCK Ljava/util/function/Predicate;
```

and you overwrite the predicate at mod init. **This does not work**, and the reason is thirty lines
away:

```java
for (BlockEntity potentialTarget : levelChunk.getBlockEntities().values()) {
    if (potentialTarget instanceof ChestBlockEntity chestBlockEntity) {
```
`/opt/mc-src/net/minecraft/world/entity/ai/behavior/TransportItemsBetweenContainers.java:283-284`

**Verified against the built artifact, not just the decompiler** — this claim carries the whole
recommendation, so I disassembled it:

```
$ javap -p -c -cp /root/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar \
        net.minecraft.world.entity.ai.behavior.TransportItemsBetweenContainers
...
  private java.util.Optional<...TransportItemTarget> getTransportTarget(ServerLevel, PathfinderMob);
       144: checkcast     #426   // class net/minecraft/world/level/block/entity/BlockEntity
       151: instanceof    #428   // class net/minecraft/world/level/block/entity/ChestBlockEntity
       159: checkcast     #428   // class net/minecraft/world/level/block/entity/ChestBlockEntity
       166: invokevirtual #430   // ChestBlockEntity.getBlockPos:()Lnet/minecraft/core/BlockPos;
```
**[VERIFIED in bytecode.]** The same run confirms the two predicates are real
`private static final Predicate<BlockState>` fields on `CopperGolemAi`, so the AccessWidener idea
below is a genuine option that genuinely does not help.

The golem's candidate scan **only ever considers block entities that are `ChestBlockEntity`**. The
predicate you widened is consulted afterwards, at `:449` (`isWantedBlock`), on a target that already
had to pass that `instanceof`. An AccessWidener changes access flags on fields, methods and classes.
It cannot change a bytecode `instanceof` inside a method body. **There is no non-mixin way to make
a `TileEntityRack` visible to a copper golem.**

And `TileEntityRack` is not a `ChestBlockEntity` and cannot become one:

* `TileEntityRack extends AbstractTileEntityRack implements IMateriallyTexturedBlockEntity, Clearable, ExtendedMenuProvider`
  (`mc/core/tileentities/TileEntityRack.java:71`).
* `AbstractTileEntityRack extends BlockEntity implements MenuProvider, IItemHandlerCapProvider`
  (`mc/api/tileentities/AbstractTileEntityRack.java:25`). It is **not** a `net.minecraft.world.Container`.
* `ChestBlockEntity extends RandomizableContainerBlockEntity`
  (`/opt/mc-src/net/minecraft/world/level/block/entity/ChestBlockEntity.java:28`).

Java has single inheritance. A block entity cannot be both `AbstractTileEntityRack` (which the whole
warehouse is typed on, §1.1) and `ChestBlockEntity` (which the golem requires) unless you **reparent
`AbstractTileEntityRack` onto `ChestBlockEntity`** — dragging `Container`, `WorldlyContainer`, the
loot-table/lock fields, `ContainerOpenersCounter` and the lid animation into the single most
instantiated block entity in the mod (82 of them in one warehouse blueprint), on top of the
Domum Ornamentum texture cache that already had to be defended against Sodium's chunk workers
(`mc/core/tileentities/TileEntityRack.java:164-170`, `:839-861`, fixed in commit `47949ae3`).

That refactor is possible. It is also the single most dangerous change available in this codebase,
it changes the on-disk shape of every rack in every existing save, and its entire payoff is that a
vanilla mob whose search radius is 32 blocks horizontal and 8 vertical
(`CopperGolemAi.java:39-40`) can shuffle stacks around inside a building that already has a courier.

### 1.3 What it would actually cost, if you insisted

Everything below is *in addition to* the reparenting risk, not instead of it.

| Piece | Size | Note |
|---|---|---|
| `AbstractTileEntityRack` reparented onto `ChestBlockEntity` | 400–700 lines touched | Two save formats to reconcile; `AbstractTileEntityRack.RackInventory` (`:70`) vs `RandomizableContainerBlockEntity`'s `NonNullList<ItemStack>`. Every existing save migrates or breaks. |
| Warehouse re-typing, if you go the other way (chest-as-container) | 6 method signatures + all callers | `TileEntityWareHouse.java:171/192/219/244`, `AbstractTileEntityColonyBuilding.java:131`, `AbstractBuildingContainer.java:158`, `BuildingWareHouse.java:121` |
| A `Container` adapter over `IItemHandlerCapProvider` | ~150 lines | Slot-count, `setChanged`, `stillValid`, and the rack's `content` index map must stay coherent |
| Golem lifetime management | ~200 lines | Auto-waxing on spawn, or a colony job that re-waxes; otherwise the golem statues itself in ~a day of play (§1.1 C) |
| A "copper rack" block, if you take the cheap route | block + item + blockstate + 2 models + 4 oxidation textures + loot table + recipe + lang | see §1.4 |
| Blueprints | **0 if the block is optional, 187 warehouse blueprints × 24 styles if it is not** | Nobody is repainting 187 blueprints for this |
| Maintenance | **permanent** | `TransportItemsBetweenContainers` is new in 26.x. Its internals will move. Every field you widened and every `instanceof` you worked around is a break waiting for the next MC version — and the whole point of this port's zero-mixin rule is that upgrades stay cheap. |

Honest range for "copper golem feeds the warehouse": **3–6 weeks**, ending in a mixin. Or **never**,
which is my recommendation.

### 1.4 The cheap version — and it is worth naming, because it is genuinely cheap

**A "copper rack" that is a rack with a copper look, and no golem.**

`BlockMinecoloniesRack` is an `IMateriallyTexturedBlock` (see the exclusion at
`mc/core/placementhandlers/DoBlockPlacementHandler.java:61`), which means the rack *already*
retextures itself from the materials it is built with — that is what `refreshTextureCache`
(`mc/core/tileentities/TileEntityRack.java:698`) and `textureDataCache` (`:170`) do. A player who
builds a rack out of copper blocks gets a copper-looking rack today, without a line of code.
**[INFERRED — I read the texture pipeline, I did not render it, and there is no client here.]**

If that turns out not to hold for copper specifically, a dedicated `blockcopperrack` is:

* one block class subclassing `BlockMinecoloniesRack`, one `BlockItem`, one blockstate JSON, two
  models, four textures (one per weather state) if you want it to age cosmetically,
* one loot table entry (`DefaultBlockLootTableProvider.java:84` pattern), one recipe
  (`DefaultRecipeProvider.java:479` pattern), one block tag (`DefaultBlockTagsProvider.java:198`),
  one lang key,
* `registerBlockPosition` already accepts it, because the check is
  `block instanceof BlockMinecoloniesRack` (`AbstractBuildingContainer.java:174`) — **a subclass
  passes for free**. So does every `instanceof TileEntityRack` in the warehouse (§1.1).

**One trap, and it is the only one:** the rack's block entity type is registered against a
*closed set of exactly one block* —

```java
MinecoloniesTileEntities.RACK = register("rack", () -> buildType(TileEntityRack::new, ModBlocks.blockRack));
...
private static <T extends BlockEntity> BlockEntityType<T> buildType(
        final BlockEntityType.BlockEntitySupplier<T> factory, final Block... validBlocks) {
    return new BlockEntityType<>(factory, Set.of(validBlocks));
}
```
`mc/apiimp/initializer/TileEntityInitializer.java:31`, `:83-88`. **[VERIFIED]**

A new `blockCopperRack` must be added to that varargs list, or `LevelChunk.setBlockEntity` rejects
the block entity with `"Trying to set block entity … but state … does not allow it"`
(`/opt/mc-src/net/minecraft/world/level/chunk/LevelChunk.java:425-431`) and the rack silently has no
inventory. Compare `MinecoloniesTileEntities.BUILDING`, which is registered against
`ModBlocks.getHuts()` — a *set* — precisely because there are many hut blocks
(`TileEntityInitializer.java:27`). One extra argument on line 31 is the whole fix, but it is not
optional and it fails quietly.

**Two to three days, low risk, zero blueprint work, zero maintenance.** It gets the player the
copper aesthetic he is actually asking for. It does not get him a golem, and the golem is the part
that cannot be built.

If he wants the golem *visible* rather than *useful*: a waxed copper golem placed decoratively in a
warehouse blueprint costs nothing and does nothing, and vanilla will let him do that with no mod
support at all.

### 1.5 What it would cost him to find this out the hard way

He would build the copper rack block (day 3, fine), then discover the golem ignores it (day 4, one
`instanceof`), then try the AccessWidener predicate (day 6, looks like it should work), then find
`TransportItemsBetweenContainers:284` (day 7–10, because the failure is silent — the golem just
strolls). Then he would either write a mixin, breaking the rule the whole port exists to keep, or
throw the week away. **Call it 8–10 days to reach "no".** This section is that week.

---

## 2. Mounted couriers and guards

### 2.1 The finding that changes the price: mounted guards already ship

He called this "the most expensive thing on the list". It is the cheapest, because **it is done**.
Upstream MineColonies shipped a cavalry unit across four PRs (`98ffca0f` "Cavalry 3 of 4",
`2af949d1` "Cavalry 2 of 4 (CavalryHorseEntity)", `a609c4d0` "Feature/moar guards"), and this port
carries all of it:

| Piece | Path | Lines |
|---|---|---|
| The horse | `mc/core/entity/other/cavalry/CavalryHorseEntity.java` | 1070 |
| Cavalry AI | `mc/core/entity/ai/workers/guard/EntityAICavalry.java` | 420 |
| Cavalry combat AI | `mc/core/entity/ai/workers/guard/CavalryCombatAI.java` | 68 |
| The job | `mc/core/colony/jobs/guard/JobCavalry.java` | 192 |
| Horse goals | `mc/core/entity/ai/cavalry/ReturnToStableGoal.java`, `CavalryStrollGoal.java` | 378 + 130 |
| Stablemaster (trains vanilla horses into cavalry horses) | `mc/core/entity/ai/workers/production/herders/EntityAIWorkStablemaster.java` | 747 |
| Building + module | `BuildingStable.java`, `StableCavalryBuildingModule.java` | — |
| Renderers | `CavalryHorseRenderer.java`, `CavalryOverlayLayer.java` | 113 + 77 |
| Textures | `assets/minecolonies/textures/entity/horse/cavalry_overlay_layer{,0..5}.png`, `entity/shield/horse.png`, `entity/banner/horse.png` | 9 files |
| Blueprints | 5 `stable` blueprints already shipped | — |

All of it is registered: the entity type at `mc/apiimp/initializer/EntityInitializer.java:165-169`,
the job at `ModJobsInitializer.java:147-151`, the guard type at `ModGuardTypesInitializer.java:81-89`,
the building at `ModBuildingsInitializer.java:170-182` (with `CAVALRY_STABLE_WORK`,
`STABLEMASTER_WORK`, `STABLEMASTER_HERDING`, `GUARD_ENTITY_LIST`, `STABLE_SETTINGS`), the hut block
at `ModBlocksInitializer.java:109` and `:210`, the interaction at
`InteractionValidatorInitializer.java:161-166`. **[VERIFIED]** — all present in the compiled tree;
the baseline `gradle build` on this tree is green per `ENV-26.2.md`. **[UNTESTED in game.]**

### 2.1a …and none of it can be reached by a player

This is the finding that explains the whole disagreement, and I nearly missed it.

**There is no Stable blueprint. In any style. In this port or upstream.**

```
$ find 26.2/src/main/resources/blueprints -name "*.blueprint" | grep -i stable
.../medievalbirch/decorations/misc/small_stable.blueprint
.../medievaloak/decorations/misc/small_stable.blueprint
.../birch/decorations/misc/stable.blueprint
.../original/decorations/misc/stable.blueprint
.../medievalspruce/decorations/misc/small_stable.blueprint
```

Five files, all under `decorations/misc/`, and I unpacked two of them: neither contains a
`blockhutstable` and neither carries a `stall` tag. They are scenery — a hitching post for a
farmyard, not a building. **[VERIFIED by parsing the NBT.]**

Every real worker building follows `<style>/<category>/<subcategory>/<schematicname><level>.blueprint`
— `medievaloak/agriculture/horticulture/farmer1..5.blueprint`,
`spacewars/agriculture/husbandry/cowboy1..4.blueprint`, and so on for all 9,374 of them.
`BuildingStable.getSchematicName()` returns `ModBuildings.STABLE_ID` = `"stable"`
(`mc/core/colony/buildings/workerbuildings/BuildingStable.java:88-92`, `ModBuildings.java:33`), and
`BuildingStable` does not override `getMaxBuildingLevel()`, so it inherits
`CONST_DEFAULT_MAX_BUILDING_LEVEL = 5` (`AbstractBuilding.java:1308`, `BuildingConstants.java:11`).
**There is no `stable1.blueprint` through `stable5.blueprint` anywhere.** **[VERIFIED]**

**And the hut block has no recipe:**

```java
// TODO: Cavalry 4 of 4 - replace stable recipe.
// registerHutRecipe1(consumer, ModBlocks.blockHutStable, Items.GOLDEN_APPLE);
```
`mc/core/generation/defaults/DefaultRecipeProvider.java:172-173` — commented out, sitting in the
middle of an otherwise complete list of hut recipes. **[VERIFIED]**

**Upstream is in the same state.** `/workspace/ldtteam/minecolonies` ships 9,460 blueprints and
`grep -ci stable` over them returns the same 5 decorations. The commit subject in this repo's own
history is `98ffca0f Cavalry 3 of 4`, and the TODO above is literally labelled *4 of 4*. **Upstream
built the cavalry across three PRs and never landed the fourth.** The brief asked whether upstream
had attempted something like this and abandoned it — this is that evidence, and it is better than
"abandoned": the feature is *finished except for its front door*. **[VERIFIED]**

So: the owner is right that he has never seen a mounted guard, and wrong about every reason why. He
is not missing a navigation system. He is missing a building.

### 2.2 His diagnosis, item by item — every one is already handled

> "The mod has its own multithreaded navigation and it knows nothing about riding: entity width,
> jumps, water, dismounting at a door."

**"knows nothing about riding" — false.** The navigator has a dedicated hook:

```java
protected PathingOptions getOptionsForPathJob() {
    if (ourEntity.getVehicle() instanceof Mob riddenMob
        && riddenMob.getNavigation() instanceof AbstractAdvancedPathNavigate vehicleNavigation) {
        final PathingOptions mountedOptions = new PathingOptions();
        mountedOptions.importFrom(vehicleNavigation.getPathingOptions());
        if (riddenMob instanceof CavalryHorseEntity) {
            mountedOptions.setEnterGates(true);
            mountedOptions.setEnterDoors(false);
            mountedOptions.setTurnPenalty(GuardConstants.CAVALRY_CORNER_PENALTY);
        }
        return mountedOptions;
    }
    return getPathingOptions();
}
```
`mc/core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java:477-496`, called from
`:463` one line before `pathResult.startJob(Pathfinding.getExecutor())` at `:465`. The rider's path
job is costed with **the vehicle's** constraints, copied rather than mutated so neither navigator's
long-lived settings are disturbed — which is exactly the thread-safety discipline this codebase
needs, since the job then runs on the pathfinding executor's own threads. **[VERIFIED]**

**"entity width" — handled, deliberately.** `CavalryHorseEntity.SLIM_W = 0.70F` with the comment
"the width is deliberately slim to allow 1-wide pathing for cavalry units"
(`mc/core/entity/other/cavalry/CavalryHorseEntity.java:83-89`), applied at registration via
`.sized(CavalryHorseEntity.SLIM_W, CavalryHorseEntity.BASE_H)` (`EntityInitializer.java:169`).
Vanilla `Horse` is 1.396 wide. **[VERIFIED]**

**"jumps" — handled.** `createNavigation` sets `withJumpCost(1D)` and `withDropCost(1D)`
(`CavalryHorseEntity.java:446-447`). **[VERIFIED]**

**"water" — handled.** `setCanSwim(true)` and `setCanFloat(true)` (`:449`, `:451`). **[VERIFIED]**

**"dismounting at a door" — handled, and more carefully than he asked for.** Three separate
mechanisms:

1. The horse never paths through doors at all: `setEnterDoors(false)`, `setCanOpenDoors(false)`
   (`CavalryHorseEntity.java:443-445`), and the mounted override re-asserts
   `setEnterDoors(false)` while allowing gates (`MinecoloniesAdvancedPathNavigate.java:486`).
2. Ladders force a dismount, both from the horse's side —
   `upcomingPathRequiresClimbing(path)` scans 8 nodes ahead for `PathPointExtended.isOnLadder()` or a
   `LadderBlock`, then `cavunit.stopRiding(); nav.stop();` (`CavalryHorseEntity.java:534-540`,
   `:559-583`) — and from the navigator's side, `handleLadders()`
   (`MinecoloniesAdvancedPathNavigate.java:904-911`), which is careful to *not* discard the vehicle
   when it is a `CavalryHorseEntity` (unlike a boat or cart, which are consumables).
3. Gates get opened *by the rider on the horse's behalf*: `EntityAIInteractToggleAble.canUse()`
   has an explicit branch — "If we are a rider on a horse, we need to use our mount collision to
   determine if gates need to be opened" — reading `horse.hadHorizontalCollission()` because "the
   horse collides, but it is the rider's path which is being followed"
   (`mc/core/entity/ai/minimal/EntityAIInteractToggleAble.java:120-129`). **[VERIFIED]**

And four more he did not think to list, all present:

* **Eating.** A cavalryman parks the horse outside the restaurant:
  `EntityAIEatTask.java:460-462`. **[VERIFIED]**
* **Sleeping.** `EntityAICavalry.sleep()` dismounts before sleeping (`:121-129`). **[VERIFIED]**
* **Rider yaw slaved to the horse**, clamped to 12°/tick so the rider does not spin
  (`CavalryHorseEntity.java:505-509`, `RIDER_ALIGN_MAX_STEP_DEGREES` at `:139`). **[VERIFIED]**
* **Horse reservation with a 200-tick expiry**, so two guards do not fight over one horse
  (`CavalryHorseEntity.reserve` at `:907`, `hasReservation` at `:977`, expiry constant
  `RESERVATION_EXPIRATION_LIMIT` at `:122` ticked down in `tick()` `:487-497`; consumed by
  `EntityAICavalry.isAvailableFor` `:335-344`). **[VERIFIED]**
* **Push/collision suppression while mounted** in `AbstractEntityCitizen.push` and `isPushable`
  (`:411`, `:442`) — though note these name only `MinecoloniesMinecart` and `MinecoloniesBoat`, **not**
  `CavalryHorseEntity`. See §2.5.

His model of this feature is a model of a codebase that stopped being true two upstream releases
ago.

### 2.3 On boats: they are the *second*-nearest analogue, not the nearest

The brief pointed at boats. They are worth reading, and the boat machinery is real — vehicle
spawning at water's edge, `probeBoatEdge`-style water probing
(`MinecoloniesAdvancedPathNavigate.java:1673-1720`), a `BOATS` research gate
(`ResearchConstants.java:159`, `DefaultResearchProvider.java:432-435`), a configurable boat speed
(`ServerConfiguration.java:40-68`, `:420`), a `VehicleClaim` protocol for stopping vanilla's
"anyone may board" behaviour (`mc/api/entity/other/VehicleClaim.java:13-28`), and abandoned-vehicle
cleanup when a path dies mid-crossing (`MinecoloniesAdvancedPathNavigate.java:~530-560`).
`AbstractEntityAIGuard.onCombatLeave` restores `setCanUseBoat(canPathOnBoat())` after a fight
(`mc/core/entity/ai/workers/guard/AbstractEntityAIGuard.java:221-226`), called at `:702`.
**[VERIFIED]**

But boats answer the wrong half of the question. A boat is a **disposable** vehicle spawned by the
pathfinder on one shore and discarded on the other; the code paths that matter for it are "place
vehicle", "cross", "destroy vehicle" — hence the repeated
`entity.remove(Entity.RemovalReason.DISCARDED)` guarded by `!(vehicle instanceof CavalryHorseEntity)`
at `:908`, `:1264-1266`, `:1797-1799`, `:1930-1932`. Those four guards are the tell: **the horse was
retrofitted into a system built for disposable vehicles, and someone went round and excluded it from
every "throw the vehicle away" site.** That work is done. If boats had been the only precedent, the
mounted feature would be expensive. They are not, and it is not.

### 2.4 Mounted *couriers*: the half that does not exist, and should not

The courier is `EntityAIWorkDeliveryman extends AbstractEntityAIInteract<JobDeliveryman, BuildingDeliveryman>`
(`mc/core/entity/ai/workers/service/EntityAIWorkDeliveryman.java:58`), six states:
`START_WORKING`, `PREPARE_DELIVERY`, `DELIVERY`, `PICKUP`, `DUMPING`, `IDLE` (`:118-123`).
Every productive one ends in a walk *into a building*:

* `deliver()` calls `walkToBuilding(targetBuilding)` and then pushes stacks into
  `targetBuilding.getItemHandlerCap()` (`:360-363`, `:398-411`).
* `dump()` (`:297`) and `pickup()` (`:146`) do the same at the warehouse and at the pickup target.

The horse is configured never to enter a door (`setEnterDoors(false)`, twice — §2.2). So a mounted
courier's loop is: ride to the building, dismount, walk in, transact, walk out, remount, ride on.
The dismount/remount is not free — `EntityAICavalry.findMount` costs a `walkToPos` to the horse
plus a `GUARD_MOUNT_INTERVAL` of 50 ticks between attempts (`:52`, `:231`), and `findStable` another
50 (`:79`). A courier's median leg inside a colony is short. **A mounted courier would be slower than
a walking one for most deliveries, and would only pay off on a colony sprawling enough that legs
routinely exceed a few hundred blocks.** **[INFERRED — this is arithmetic on verified constants, not
a measurement. There is no client here to measure in.]**

If you still wanted it, the work is:

| Piece | Size | Why |
|---|---|---|
| Lift `FIND_MOUNT`/`FIND_STABLE` out of `CombatAIStates` | ~150 lines | They live in the combat state enum (`mc/api/entity/ai/combat/CombatAIStates`, used at `EntityAICavalry.java:78-79`); the courier's state machine is `AIWorkerState`, a different enum. A shared mixin-free home means a new interface or a small state-machine module. |
| Mount/dismount states in the deliveryman FSM | ~250 lines | Six existing states each need a "am I mounted, and should I be?" pre-check, plus two new states and their transitions. Every one is a place a courier can get stuck holding a colony's whole request queue. |
| Horse ownership for non-guards | ~150 lines | `CavalryHorseEntity.isReadyForCombat()` gates availability (`EntityAICavalry.isAvailableFor:342`), and `prepareForCombat` / `getCombatCooldown` (`:843-869`) are the stablemaster's readiness pipeline. A courier horse needs a different readiness concept, or the couriers and the cavalry will fight over the stable's stock. |
| Persistence | ~40 lines | `JobCavalry` stores `myMount` as a UUID (`:46`, `:95-107`); `JobDeliveryman` would need the same, plus a save migration for existing colonies. |
| Settings + GUI | ~120 lines + XML | A "use mounts" toggle on the courier building, or players get surprise horses. |
| Stable coupling | — | A colony with couriers but no stable gets the `CAVALRY_NOHORSE` nag loop (`EntityAICavalry.java:203-214`) forever. Needs its own gate. |

**2–3 weeks, and the result is slower deliveries.** Don't build it.

### 2.5 One real defect I found while reading, worth a line of its own

`AbstractEntityCitizen.push` (`:411`) and `isPushable` (`:442`) suppress pushing for
`MinecoloniesMinecart` and `MinecoloniesBoat` — **but not `CavalryHorseEntity`**. So a mounted
cavalryman is still pushable by, and still pushes, other entities. Compare
`AbstractCivilianEntity.startRiding` (`:144-152`), which *was* updated to include
`CavalryHorseEntity`. This looks like an omission rather than a decision. It is a two-word fix and
it is not part of any of the three features; flagging it here because it is the kind of thing that
produces "my cavalry gets shoved off the road" reports. **[VERIFIED as a source asymmetry;
**[UNTESTED]** as a gameplay symptom.]**

### 2.6 The cheap version — and this is the one thing on my tier I would do

**Write no Java. Ship the Stable's front door.**

Everything the cavalry needs to work exists (§2.1) and nothing a player can do reaches it (§2.1a).
The gap is three items, in ascending order of cost:

**1. The recipe — one line, minutes.** Uncomment `DefaultRecipeProvider.java:173` and run datagen.
`registerHutRecipe1(consumer, ModBlocks.blockHutStable, Items.GOLDEN_APPLE)` is already written; the
TODO above it says "replace", implying somebody wanted a better recipe than a golden apple and never
chose one. Any recipe beats no recipe. Note `ENV-26.2.md`'s warning: **do not run `runDatagen` and
`build` in one Gradle invocation.**

**2. The blueprints — the real cost, and it is content, not code.** Five levels, and the honest
range is:

* **Minimum viable: 5 blueprints in one style.** There is no cross-pack fallback: a building's
  blueprint is a plain stored string, `AbstractSchematicProvider.getBlueprintPath()` returning the
  `path` field set at placement (`mc/core/colony/buildings/AbstractSchematicProvider.java:147-151`,
  `:153-159`), and `StructurePacks.getCategories(structurePackId, subPath)` browses exactly one pack
  (`/workspace/structurize/26.2/.../StructurePacks.java:543`). A style with no `stable*.blueprint`
  simply never offers the building — it does not borrow one. **[VERIFIED]** But the pack is chosen
  per building, not per colony (`WindowBuildBuilding.updateStyles` reads `building.getStructurePack()`,
  `:381-397`), so shipping one style is a complete, usable feature; the stable just looks like that
  style. Call it **3–5 days of somebody's evening** in Structurize's in-game editor.
* **Full parity: 5 levels × 24 styles = 120 blueprints.** That is the number the gatehouse study's
  60-blueprints-for-three-levels-across-nineteen-styles implies, and nobody on this project has that
  budget. Do not attempt it.

Each blueprint needs the `stall` schematic tag, because `BuildingStable.stallPositions()`
(`:108-119`) warns and returns empty without it — the same silent-failure shape the gatehouse study
found for `knight`/`archer` tags — plus `TAG_GROUNDLEVEL` and `TAG_PATROL_POINT`, which
`BuildingStable` imports at `:33-34`.

**3. Nice-to-haves, half a day each.** A GUI row in the stable window showing horse count, readiness
and reservation (the data is all there: `reservedBy()`, `isReadyForCombat()`, `getCombatCooldown()`
at `CavalryHorseEntity.java:907`, `:856`, `:869`); the `isPushable` fix from §2.5; a changelog line
telling him the feature exists.

**Total: about a week, most of it spent in the blueprint editor rather than in Java.** It delivers
"mounted guards" — the thing he called the most expensive item on the list — for a fraction of one
percent of what he budgeted, because someone else already paid for it.

---

## 3. Nautilus, and an underwater outpost

### 3.1 His diagnosis is correct — and here is the exact line

> "This is blocked not by the mount but by colonists living badly underwater at all."

Right. And the mechanism is one goal, registered at **priority 0 on every citizen in the game**:

```java
this.goalSelector.addGoal(priority, new EntityAIFloat(this));
```
`mc/core/entity/citizen/EntityCitizen.java:346` (priority is 0 at that point, `:345`). **[VERIFIED]**

`EntityAIFloat.tick()` (`mc/core/entity/ai/minimal/EntityAIFloat.java:40-78`):

```java
if (owner.isEyeInFluid(FluidTags.WATER) || owner.isEyeInFluid(FluidTags.LAVA)) {
    if (owner.level().getBlockState(BlockPos.containing(owner.getEyePosition()).above()).isAir()) return;
    if (waterPathing == null || !waterPathing.isInProgress()) {
        nav.setPauseTicks(0);
        nav.stop();
        waterPathing = nav.setPathJob(new PathJobEscapeWater(...), null, 1.0, false);
        nav.setPauseTicks(20 * 15);
    }
}
```

Three things happen, in this order, whenever a citizen's **eye** is in water with a non-air block
above it:

1. `nav.stop()` — whatever the citizen was walking towards is discarded.
2. A `PathJobEscapeWater` is queued, aimed at **the colony centre**
   (`PathJobEscapeWater.java:49-56` overrides `preferredDirection` to `colony.getCenter()` for
   citizens).
3. `nav.setPauseTicks(300)` — and `MinecoloniesAdvancedPathNavigate.setPathJob` opens with
   `if (pauseTicks > 0) { return null; }` (`:372-375`).

**[VERIFIED, all three.]**

That third point is the killer, and it is not obvious from the goal alone. For the next fifteen
seconds, **every** path request the citizen's work AI makes returns `null` — silently. The AI state
machine's `walkToBuilding` / `walkToPos` never completes, so the worker sits in whatever state it
was in, re-requesting, until the pause expires; then the goal fires again, and it pauses for another
fifteen seconds. A submerged citizen is not "slow", it is **a work AI running against a navigator
that refuses every order**, permanently.

So an underwater outpost does not need "breathing and delivery fixed". It needs the mod's own
anti-drowning safety net **selectively disabled inside a defined volume**, without disabling it for
the citizen who fell in a river fifty blocks away — and `EntityAIFloat` has no notion of place,
building, or colony region. It is a `FloatGoal` subclass with one field.

### 3.2 The rest of the picture, so the estimate is honest in both directions

Things that **already work** and that he should not be charged for:

* **Pathfinding can swim.** `PathingOptions.swimCost = 4D`, `swimCostEnter = 24D`
  (`mc/core/entity/pathfinding/PathingOptions.java:65`, `:75`), a diving cost (`divingCost = 4D`, `:95`), and a
  full `walkUnderWater` mode (`:146`, `:264-271`, `:417-419`) already consumed by
  `PathfindingUtils.isPassable` at `:369` and `:386`. The drowned-pirate raiders switch it on
  wholesale — `withStartSwimCost(0.0D).withSwimCost(0.0D).withDivingCost(0.0D).withWalkUnderWater(true)`
  (`mc/api/entity/mobs/drownedpirate/AbstractDrownedEntityPirateRaider.java:82`). **Underwater
  raiders are a shipped, working feature.** The pathfinder is not the problem. **[VERIFIED]**
* **A dive path exists**: `AbstractPathJob.java:1972` computes `isDiving`, `:3054-3071` handles
  goto-in-liquid, `:3153` the water floor. **[VERIFIED]**
* **There is already an air research.** "Scuba" (`civilian/air`, cost: 1 Heart of the Sea) grants
  `MORE_AIR`, and `CitizenData` applies it as `setMaxAir(600)`
  (`mc/core/colony/CitizenData.java:2034-2037`, `DefaultResearchProvider.java:705-711`,
  `ResearchConstants.java:176`). That doubles vanilla's 300 ticks to 600 — **30 seconds, not
  permanent**. **[VERIFIED]**
* **Citizens drown normally otherwise.** `AbstractFastMinecoloniesEntity.decreaseAirSupply` returns
  `supply - 1` (`:138-141`) — vanilla behaviour, no exemption. Vanilla then damages at
  `LivingEntity.java:444-448`. **[VERIFIED]**

Things that are **broken and were not on his list**:

* **`updateSwimming()` is a no-op** — `// Noop our entities dont swim`
  (`AbstractFastMinecoloniesEntity.java:240-244`). The swimming *pose* flag is never set on a
  citizen. **[VERIFIED]** Consequences are a client-side rendering and hitbox question I cannot test
  here **[UNTESTED]**, but a colony of citizens standing bolt upright underwater is at minimum a
  cosmetic problem, and `Pose` is also consulted by `setPathJob` (`:377-380`).
* **`updateFluidInteraction` runs one tick in ten** (`:212-221`), a deliberate performance
  optimisation with a careful port note. Water-state detection therefore has 10-tick granularity —
  fine for a citizen crossing a stream, less fine for one that lives in water. **[VERIFIED]**
* **`isInWaterOrRain()` returns false unless the citizen is on fire** (`:258-267`). Anything
  vanilla gates on "is wet" is dark for citizens. **[VERIFIED]**
* **The whole delivery chain assumes standing.** `EntityAIWorkDeliveryman.deliver()` →
  `walkToBuilding` → building inventory; nothing about it is water-aware, and it will inherit the
  paused-navigator failure of §3.1 verbatim.
* **`PathJobEscapeWater` aims at the colony centre for citizens** (`:49-56`). If the colony centre
  *is* underwater, that job is asking the citizen to escape to the place it is escaping from.
  **[INFERRED from the code; would need a game to confirm the loop.]**

### 3.3 Size

He called this "a subproject of its own". That is the one estimate of his that is not optimistic.

| Piece | Size | Note |
|---|---|---|
| Region-aware `EntityAIFloat` | 200–400 lines, and it is the risky part | Needs a colony-side "this volume is habitable" query answerable **fast, per citizen, per tick, from a goal**. Cheap answers (radius around a hut) break on the first oddly-shaped build. It must also not regress the ordinary "citizen fell in a river" case, which is the reason the goal exists. |
| Permanent breathing | 150–300 lines | Either a conduit-like colony effect, a per-citizen `canBreatheUnderwater` override (`LivingEntity.java:399` — overridable, no widener needed), or a research extension. Each has a different balance story and a different save-data footprint. |
| Swim pose restoration | 100 lines + client work | Undoing the `updateSwimming` no-op re-introduces the per-tick cost it was written to avoid, on **every** citizen in **every** colony, to benefit the handful that are underwater. That is exactly the trade this codebase has repeatedly refused. |
| Work-AI audit | **This is the open-ended one** | Every `AbstractEntityAIBasic` subclass — 40+ worker AIs — has to be checked for "does this state survive being submerged". Farmer, fisherman, lumberjack, miner, builder, smelter. Most will need nothing; the ones that need something will not announce themselves, and there is no client here to find them in. |
| A new building type | 800–1500 lines | Hut block, building class, module set, GUI window, request resolvers — the standard cost, visible in any existing `BuildingX` |
| Blueprints | **24 styles × N levels** | This is the number people forget. The gatehouse study counted 60 blueprints for one three-level building across 19 styles. A five-level outpost across 24 styles is ~120 blueprints, each hand-built, each needing schematic tags. Nobody in this project has that budget. |
| The nautilus mount itself | 1–2 weeks | See §3.5 — genuinely the cheapest part |
| Maintenance | **permanent, and it taxes everything** | Once one colony can be underwater, every future AI state has to be written twice. |

**6–10 weeks**, and the last row is the one that should decide it: this is not a feature you finish,
it is a constraint you take on.

### 3.4 The cheap version he proposed, tested

> "an underwater outpost that is really just a dry room with a door"

**This one works, and it is close to free — because it is not a feature at all.**

A player today can build an air pocket on the seabed, place a hut block inside it, and the citizens
inside it are in air. `EntityAIFloat` never fires (its condition is `isEyeInFluid`,
`EntityAIFloat.java:44`). Work AIs are unaffected. Delivery is unaffected. The courier's problem is
only the *journey*, and the pathfinder already swims (§3.2) — expensively (`swimCostEnter = 24D`),
but it works, and the boat research exists for surface crossings.

So "a dry room with a door" needs **zero code**. What it might reasonably need is content and
polish, and that is a different budget line:

* Blueprints for an airlock-styled outpost — **a decoration pack, not a mod feature.** Structurize
  will place it. This is the honest home for the request.
* If you want the door to actually hold water back, that is vanilla's problem
  (doors are not waterlogged-sealing), and the answer is a trapdoor-and-airpocket build, which
  players already do.

**Recommendation: tell him the dry room already works, and offer blueprints instead of code.**

### 3.5 The nautilus, separated out

He is right that the mount is not the blocker — but that also means the nautilus is *severable*, and
on its own it is the cheapest thing in this study after §2.6.

A rideable aquatic mount, given what §2 established, is: an entity class in the shape of
`CavalryHorseEntity` but simpler (no combat readiness, no reservation-vs-guard contention), a
`createNavigation` with `setCanSwim(true)`/`walkUnderWater(true)`/`setCanFloat(false)`, an
`AbstractCivilianEntity.startRiding` allow-list entry (`:148`, one clause), the four
`!(vehicle instanceof CavalryHorseEntity)` discard guards extended to it
(`MinecoloniesAdvancedPathNavigate.java:908`, `:1264`, `:1797`, `:1930`), model + texture +
renderer. **1–2 weeks.**

But it is a mount with nowhere to go. Without §3.1 there is no underwater destination worth riding
to, and with boats already crossing water on the surface it is a slower answer to a solved problem.
**Build it only if the answer to §3.4 is "blueprints", and only as flavour.**

---

## 4. What I could not check

* **No game client.** `runClient` does not start in this container. Nothing here was played. Every
  rendering claim (the rack's copper retexturing in §1.4, the swim-pose consequences in §3.2, and
  every behavioural claim about cavalry) is source reading.
* **No server run.** I did not start one for this study; the estimates do not depend on runtime
  measurement, and starting one risks the shared box. Port 26073 was never opened.
* **Upstream history is a shallow clone.** `/workspace/ldtteam/minecolonies` has exactly one commit
  (`2d453335`), so I could not mine its history for abandoned attempts. I could and did compare its
  *working tree*: 9,460 blueprints, the same five decorative stables, no `stable1..5`, which is what
  establishes §2.1a. The cavalry provenance comes from *this* repository's history, which retains
  the upstream commit subjects (`98ffca0f`, `2af949d1`, `a609c4d0`). **Searching upstream's issue
  tracker for an abandoned underwater attempt, and for why "Cavalry 4 of 4" never landed, is the one
  piece of evidence this study is missing, and it is worth ten minutes of someone's browser.**
* **Blueprint fallback — resolved, from source.** §2.6 now cites the code: no fallback, one pack
  per lookup, pack chosen per building. I did not confirm it by placing a hut, because there is no
  client here.
* **`instanceof` reachability — this gap is closed.** The load-bearing claim of §1 was checked
  against the built artifact with `javap -c` on
  `/root/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar`, not only against `/opt/mc-src`. The
  disassembly is quoted in §1.2.
* **Rack container semantics.** I established that `AbstractTileEntityRack` is not a
  `net.minecraft.world.Container` by reading its declaration (`:25`). I did not attempt to write the
  adapter, so the "~150 lines" in §1.3 is a shape estimate, not a measured one.

---

## 5. Recommendation, restated plainly

1. **Copper**: build the copper rack block (2–3 days, cosmetic, no risk). Do not build the golem.
   Tell him the copper chest does not rot its contents and the golem does rot itself, and that a
   honeycomb fixes the second.
2. **Mounts**: **do this one.** Write no Java — uncomment one recipe line and build a Stable
   blueprint set (§2.6). Three thousand lines of finished, registered, navigation-integrated
   cavalry are sitting behind a building that has never had a blueprint, here or upstream. Fix the
   `isPushable` omission in §2.5 while you are there. Do not build mounted couriers.
3. **Underwater**: build nothing. Offer blueprints. If the nautilus is wanted for its own sake, it
   is a two-week flavour item, not a subproject.

Total recommended spend across tier 3: **a week or two**, most of it in the blueprint editor,
against the three-to-four months the list implies. Two of the three features should not be built at
all; the third is already built and needs a door.
