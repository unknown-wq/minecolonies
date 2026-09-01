# The alchemist: brewing stands, mistletoe, and who actually drinks

Research and audit only. Date: 2026-08-28. Tree: `26.3/` on branch `26.3`, targeting Minecraft
**26.3-snapshot-10**. **No game code was written or changed**; the only file this study touches is
itself.

## Verdict in one page

The alchemist is three jobs bolted to one citizen: a **brewing-stand tender** that loads vanilla
brewing recipes the player has taught it, a **nether-wart farmer**, and a **mistletoe picker** whose
crop feeds exactly one recipe. The AI file is upstream MineColonies 1.21.1, verbatim apart from six
forced renames and one `ServerLevel` guard (§0.2). Everything the profession does works — there is no
enchanter-style dead branch here — but four things are wrong and one of them is ours.

Ranked by what a player notices:

1. **He tells you nothing when he has no brewing stand.** The only failure message the alchemist can
   raise is the *baker's* "no furnaces" string, and the validator registered for that string tests
   `citizen.getWorkBuilding() instanceof BuildingBaker`
   (`mc/apiimp/initializer/InteractionValidatorInitializer.java:251-252`). `CitizenData#triggerInteraction`
   refuses to add an interaction whose validator is already false (`CitizenData.java:1873`), so the
   message is **discarded at the moment it is raised**. An alchemist whose stands were broken or
   replaced loops silently between `CRAFT` and `START_WORKING` forever with the status "Working".
   §8.1, **S**, upstream's.
2. **`/mc colony teachRecipes` teaches him brewing recipes with the two inputs swapped.** The
   crafting-type enumerator builds the recipe as `[reagent, container]`
   (`mc/core/recipes/BrewingCraftingType.java:70-76`) while the teaching GUI and the AI both assume
   `[container, reagent]` (`WindowBrewingstandCrafting.java:120-125`,
   `EntityAIWorkAlchemist.java:932, 1000`). The ordering mismatch is upstream's; the command that
   turns those enumerated recipes into real `RecipeStorage`s is **ours**
   (`mc/core/commands/colonycommands/CommandColonyTeachRecipes.java:213-243`), and it is the only
   code path that does. The result is an alchemist that tries to put nether wart in the bottle slots,
   is refused by `canPlaceItem`, and never brews. §8.2, **S**, ours.
3. **The nether-wart farm harvests one tick too early and is worth about 1.7 wart a day.** The ripeness
   gate is `AGE < 2` (`EntityAIWorkAlchemist.java:170`) where nether wart pays 2–4 only at `AGE == 3`
   (`v/data/loot/packs/VanillaBlockLoot.java:1178-1187`). Every plant caught at age 2 yields exactly
   one wart, which is exactly what replanting costs. §3.1, §7.3, **S**, upstream's.
4. **The building's position lists grow a duplicate set on every build.** `registerBlockPosition`
   appends soul sand, leaves and brewing stands with no `contains` check
   (`mc/core/colony/buildings/workerbuildings/BuildingAlchemist.java:88-104`) — the florist, doing the
   same job, checks (`BuildingFlorist.java:103`). Every upgrade and every repair pass runs
   `registerBlockPosition` over the whole schematic again
   (`mc/core/entity/ai/workers/util/BuildingStructureHandler.java:199-206`), so a level-5 hut carries
   five copies of every brewing stand — which multiplies the AI's brewing acceleration by five and is
   the only thing in this profession that scales badly. §8.4, §10, **S**, upstream's.

On worth: **take the research, build the hut to level 3, and only build it at all if you have druids
or intend to hand-feed it.** The arithmetic is §7. The short version: the alchemist's two self-supply
loops produce roughly **1.6 mistletoe and 1.7 net nether wart per game day**, one mistletoe makes one
magic potion, and a druid throws one magic potion every 40–80 game ticks in combat
(`mc/core/entity/ai/workers/guard/DruidCombatAI.java:176, 191`;
`mc/core/entity/ai/combat/AttackMoveAI.java:238`). **A full day of alchemist output is spent in three
to six seconds of one druid fighting.** The research that makes druids want potions at all costs 64
mistletoe (`gen/researches/combat/druidpotion.json`) — **forty game days** of a dedicated alchemist.
Nothing in the colony consumes a vanilla brewed potion; every one of those is for the player (§5.2).

---

## Evidence standard

Same as `docs/studies/enchanter.md` and `docs/studies/guards.md`:

* **[VERIFIED]** — I opened the file and the cited `file:line` says what I claim, or I ran the thing
  and watched the output.
* **[UNVERIFIED]** — inference I did not confirm. Each one names what would confirm it.

Paths: `26.3/src/main/java/com/minecolonies/` is abbreviated to `mc/`;
`26.3/src/main/generated/data/minecolonies/` to `gen/`. Vanilla sources are read from
**`/opt/mc-src-26.3-snapshot-10/net/minecraft/`**, abbreviated to `v/`, and **no other vanilla tree is
cited anywhere in this document**. The upstream 1.21.1 snapshot used for the "ours or theirs" question
is read out of git history (`git show b104817d^:1.21.1/...`), the tree `UPSTREAM-SYNC.md` documents as
byte-identical to `ldtteam/minecolonies@325157eba3`.

**Version, pinned.** `v/SharedConstants.java:15` reads `WORLD_VERSION = 5015`, which is snapshot-10.
**[VERIFIED]**

**Nothing here was run.** Every figure below is a source reading or arithmetic over cited constants.
No server was started, no colony was played, no probe was built. Where a claim would need a running
world to settle it is marked [UNVERIFIED] and says what would settle it.

**Line numbers are against the branch tip this study is committed on.** The checkout is shared and a
separate body of guard work landed in it while this was written; five cited files were touched by it
(`InventoryUtils.java`, `ItemStackUtils.java`, `AttackMoveAI.java`, `CitizenAI.java`,
`DruidCombatAI.java`) and every citation of those five was re-resolved against the tip this commit
sits on. No alchemist file was touched. A reader who finds a number off by a few should search for
the quoted code rather than trust the number.

---

## 0. What I read, and what I compared against

### 0.1 Read end to end

`mc/core/entity/ai/workers/crafting/EntityAIWorkAlchemist.java` (1130 lines),
`mc/core/colony/buildings/workerbuildings/BuildingAlchemist.java` (276),
`mc/core/colony/jobs/JobAlchemist.java`,
`mc/core/blocks/huts/BlockHutAlchemist.java`,
`mc/core/generation/defaults/workers/DefaultAlchemistCraftingProvider.java`,
`mc/core/recipes/BrewingCraftingType.java`,
`mc/core/client/gui/containers/WindowBrewingstandCrafting.java`,
`mc/core/commands/colonycommands/CommandColonyTeachRecipes.java`,
`mc/api/inventory/api/InvWrapper.java`.

Read in part, for the machinery the alchemist sits on:
`mc/core/entity/ai/workers/crafting/AbstractEntityAICrafting.java` (809),
`mc/core/entity/ai/workers/AbstractEntityAIBasic.java`,
`mc/core/entity/ai/workers/AbstractEntityAIInteract.java`,
`mc/core/colony/buildings/modules/AbstractCraftingBuildingModule.java`,
`mc/core/colony/buildings/modules/CraftingWorkerBuildingModule.java`,
`mc/core/colony/buildings/modules/BuildingModules.java`,
`mc/api/crafting/RecipeStorage.java`, `mc/api/crafting/GenericRecipe.java`,
`mc/api/crafting/ItemStorage.java`,
`mc/core/colony/crafting/RecipeAnalyzer.java`, `mc/core/colony/crafting/GenericRecipeUtils.java`,
`mc/api/entity/ai/statemachine/tickratestatemachine/TickRateStateMachine.java`,
`mc/core/entity/ai/workers/CitizenAI.java`,
`mc/core/colony/CitizenData.java` (interaction validity),
`mc/apiimp/initializer/InteractionValidatorInitializer.java`,
`mc/apiimp/initializer/ModBuildingsInitializer.java`,
`mc/apiimp/initializer/ModJobsInitializer.java`,
`mc/core/generation/defaults/DefaultResearchProvider.java` (the technology and combat branches),
`mc/core/entity/ai/workers/guard/EntityAIDruid.java`,
`mc/core/entity/ai/workers/guard/DruidCombatAI.java`,
`mc/core/entity/ai/workers/production/agriculture/EntityAIWorkComposter.java` (the compostable list),
`mc/core/entity/ai/workers/util/BuildingStructureHandler.java`,
`mc/api/util/ItemStackUtils.java` (the three brewing-stand predicates),
`mc/api/util/InventoryUtils.java` (the transfer helpers).

Generated data read in full: `gen/crafterrecipes/alchemist/magicpotion.json`,
`gen/researches/technology/alchemist.json`, `gen/researches/technology/opennether.json`,
`gen/researches/technology/oceanheart.json`, `gen/researches/combat/druidpotion.json`,
`gen/researches/effects/consumepotions.json`, `gen/researches/effects/blockhutalchemist.json`,
`gen/recipe/blockhutalchemist.json`, `gen/loot_table/blocks/blockhutalchemist.json`,
`gen/colony/quests/general/alchemy.json`.

### 0.2 Compared against upstream

| File | Diff against the 1.21.1 snapshot |
|---|---|
| `EntityAIWorkAlchemist.java` | **Six hunks, all forced.** `net.neoforged...InvWrapper` → `com.minecolonies.api.inventory.api.InvWrapper`; three `stack.getDescriptionId()` → `stack.getItem().getDescriptionId()`; `worker.swing` → `worker.swingForAttack`; `state.getSoundType(world, pos, worker)` → `state.getSoundType()`; and a five-line `world instanceof ServerLevel` guard added because `BrewingStandBlockEntity.serverTick` now takes a `ServerLevel` (`:475-481`). **No behavioural change.** **[VERIFIED]** |
| `BuildingAlchemist.java` | Two hunks: `net.minecraft.util.Tuple` → the mod's own, and three `compound.getList(tag, TAG_INT_ARRAY)` → `compound.getListOrEmpty(tag)`. **[VERIFIED]** |
| `JobAlchemist.java` | `ResourceLocation` → `Identifier`. **[VERIFIED]** |
| `BlockHutAlchemist.java` | A constructor added (Fabric registers blocks by instance, not by deferred supplier). **[VERIFIED]** |
| `DefaultAlchemistCraftingProvider.java` | **Byte-identical.** **[VERIFIED]** |
| `ModBuildingsInitializer` alchemist entry, `BuildingModules.ALCHEMIST_*` | **Identical** module list, skills and worker cap. **[VERIFIED]** |
| `WindowBrewingstandCrafting.OnButtonPress` | Same input order and same count of 3; only the output lookup changed, from `level.potionBrewing().mix(...)` to a `RecipeType.BREWING` match against the synchronized recipe set. **[VERIFIED]** |
| `BrewingCraftingType.java` | **Rewritten by the port** — `PotionBrewing` and `Level#potionBrewing()` were removed in 26.3, so it enumerates `BrewingRecipe`s instead of probing every item. **The input order it emits, `[reagent, container]`, is upstream's order, preserved.** **[VERIFIED]** |
| `CommandColonyTeachRecipes.java` | **Port-added.** No such command exists upstream (`git show b104817d^:1.21.1/.../colonycommands/` lists 22 commands, none of them this). **[VERIFIED]** |

**Consequence for this audit.** Every behavioural defect below except §8.2 is upstream's. §8.2 is a
latent upstream inconsistency that only becomes reachable through a port-added command, so the fix
belongs here.

### 0.3 Vanilla read

`v/world/level/block/entity/BrewingStandBlockEntity.java`, `v/world/item/component/BrewingFuel.java`,
`v/world/item/crafting/BrewingRecipe.java`, `v/world/item/crafting/PotionIngredient.java`,
`v/world/item/crafting/BrewingInput.java`, `v/world/item/crafting/RecipePropertySet.java`,
`v/world/item/alchemy/Potions.java`, `v/world/level/block/NetherWartBlock.java`,
`v/data/recipes/BrewingProvider.java`, `v/data/recipes/packs/VanillaBrewingProvider.java`,
`v/data/loot/packs/VanillaBlockLoot.java` (nether wart), `v/tags/BlockTags.java`,
`v/data/tags/VanillaBlockTagsProvider.java`,
`v/world/level/storage/loot/providers/number/NumberProviders.java`,
`v/core/component/DataComponents.java`, `v/world/item/Items.java` (blaze powder),
`v/world/inventory/BrewingStandMenu.java`, `v/SharedConstants.java`.

---

## 1. Where it lives

| Piece | File |
|---|---|
| Job | `mc/core/colony/jobs/JobAlchemist.java` |
| Job registration | `mc/apiimp/initializer/ModJobsInitializer.java:321-325` |
| AI | `mc/core/entity/ai/workers/crafting/EntityAIWorkAlchemist.java` |
| AI states | `mc/api/entity/ai/statemachine/states/AIWorkerState.java:647` (`START_USING_BREWINGSTAND`), `:652` (`RETRIEVING_END_PRODUCT_FROM_BREWINGSTAMD`), `:657` (`RETRIEVING_USED_FUEL_FROM_BREWINGSTAND`), `:662` (`ADD_FUEL_TO_BREWINGSTAND`), `:667` (`HARVEST_MISTLETOE`), `:672` (`HARVEST_NETHERWART`) — all declared `(true)`, i.e. okay to eat |
| Building | `mc/core/colony/buildings/workerbuildings/BuildingAlchemist.java` |
| Building registration | `mc/apiimp/initializer/ModBuildingsInitializer.java:651-660` |
| Modules | `mc/core/colony/buildings/modules/BuildingModules.java:262-268` — `alchemist_work`, `alchemist_craft`, `alchemist_brew` |
| Brewing module | `BuildingAlchemist.BrewingModule` (`:214-225`) extends `AbstractCraftingBuildingModule.Brewing` (`AbstractCraftingBuildingModule.java:1143-1177`) |
| Crafting module | `BuildingAlchemist.CraftingModule` (`:227-275`) — `getSupportedCraftingTypes()` is **empty**, `isRecipeCompatible` accepts only `magicpotion` |
| Crafting type | `mc/core/recipes/BrewingCraftingType.java`, registered at `mc/apiimp/initializer/ModCraftingTypesInitializer.java:38` |
| Teaching GUI | `mc/core/client/gui/containers/WindowBrewingstandCrafting.java`, container `mc/api/inventory/container/ContainerCraftingBrewingstand.java` |
| Hut block | `mc/core/blocks/huts/BlockHutAlchemist.java` |
| Datagen | `mc/core/generation/defaults/workers/DefaultAlchemistCraftingProvider.java` |
| Generated recipe | `gen/crafterrecipes/alchemist/magicpotion.json` (the **only** one) |
| Hut craft recipe | `gen/recipe/blockhutalchemist.json` — brewing stand + `structurize:sceptergold` + 7 planks |
| Block loot | `gen/loot_table/blocks/blockhutalchemist.json` |
| Research | `gen/researches/technology/alchemist.json`, source at `mc/core/generation/defaults/DefaultResearchProvider.java:1458-1464` |
| Quest | `gen/colony/quests/general/alchemy.json` |

Registration facts worth pinning down:

* Primary skill **Dexterity**, secondary skill **Mana**, **one** worker per building, **cannot work in
  the rain** (`BuildingModules.java:262-264`:
  `new CraftingWorkerBuildingModule(ModJobs.alchemist.get(), Skill.Dexterity, Skill.Mana, false, (b) -> 1)`)
  **[VERIFIED]**. The five-argument constructor sets `craftingSpeedSkill = primary` and
  `recipeImprovementSkill = secondary` (`CraftingWorkerBuildingModule.java:34-43`) **[VERIFIED]**.
* Max building level **5** (`BuildingAlchemist.java:82-86`, `CONST_DEFAULT_MAX_BUILDING_LEVEL = 5` at
  `mc/api/util/constant/BuildingConstants.java:11`) **[VERIFIED]**.
* The building keeps one pair of shears, one axe, and 16 nether wart
  (`BuildingAlchemist.java:70-72`) **[VERIFIED]**.
* **No settings module and no min-stock module.** The registered producers are exactly
  `ALCHEMIST_WORK`, `ALCHEMIST_CRAFT`, `ALCHEMIST_BREW`, `CRAFT_TASK_VIEW`, `STATS_MODULE`
  (`ModBuildingsInitializer.java:655-659`) **[VERIFIED]**, which is byte-identical to upstream's list.
  The player's only controls are the two recipe tabs and the per-recipe enable toggle. There is no way
  to tell an alchemist "always keep 16 healing potions in stock".
* **No `build_alchemist` advancement.** `DefaultAdvancementsProvider` writes 23 `build_*`
  advancements and the alchemist is not among them **[VERIFIED]** by enumerating the
  `GROUP + "build_..."` literals.
* **The mystical site is not the alchemist's.** `mc/core/colony/buildings/BuildingMysticalSite.java`
  is a worker-less decoration building with no relationship to this profession **[VERIFIED]**; the
  alchemist's second harvesting loop gathers **mistletoe from leaf blocks**, not from a mystical site.

---

## 2. What it actually does — the state trace

The citizen AI ticks the worker state machine every **5 game ticks**, and each transition's third
argument is a period in game ticks (`TickRateStateMachine.checkTransition:114-126` decrements by the
machine's tick rate) **[VERIFIED]**.

The alchemist registers seven transitions of its own (`EntityAIWorkAlchemist.java:101-113`):

```
AIEventTarget(EVENT, this::isFuelNeeded,          this::checkBrewingStandFuel, 200)   // every 200 ticks
AIEventTarget(EVENT, this::accelerateBrewingStand, this::getState,              20)   // every 20 ticks
AITarget(START_USING_BREWINGSTAND,                this::fillUpBrewingStand,     20)
AITarget(RETRIEVING_END_PRODUCT_FROM_BREWINGSTAMD, this::retrieveBrewableFromBrewingStand, 20)
AITarget(RETRIEVING_USED_FUEL_FROM_BREWINGSTAND,  this::retrieveUsedFuel,       20)
AITarget(ADD_FUEL_TO_BREWINGSTAND,                this::addFuelToBrewingStand,  20)
AITarget(HARVEST_MISTLETOE,                       this::harvestMistleToe,       20)
AITarget(HARVEST_NETHERWART,                      this::harvestNetherWart,      20)
```

and inherits the crafter's (`AbstractEntityAICrafting.java:158-168`): `IDLE→START_WORKING` (20),
`IDLE→idle` (20), `START_WORKING→decide` (5), `QUERY_ITEMS` (5), `GET_RECIPE` (5), `CRAFT` (10).

`hasWorkToDo()` is overridden to **always return true** (`:1124-1129`) **[VERIFIED]**, and the port's
`idle()` hands straight back to `START_WORKING` when that is so (`AbstractEntityAICrafting.java:189-192`,
with a port note explaining the twenty-second stall it fixed) **[VERIFIED]**. So the alchemist never
parks in `IDLE`.

### 2.1 The second `AIEventTarget` is a side-effect ticker, not a transition

`new AIEventTarget(EVENT, this::accelerateBrewingStand, this::getState, TICKS_SECOND)` passes
`accelerateBrewingStand` as the **predicate** (`AIEventTarget.java:23-29`), and that method always
returns `false` (`EntityAIWorkAlchemist.java:500`) **[VERIFIED]**. The action is therefore never run
and no state change happens; the method exists purely to hand extra `serverTick`s to the brewing
stands as a side effect of being evaluated. Same shape for `isFuelNeeded`, except that one can return
`true` and then `checkBrewingStandFuel` really does pick the next state.

**One consequence worth knowing:** `waitingForSomething` is an `AI_BLOCKING` transition
(`AbstractEntityAIBasic.java:248`) and `TickRateStateMachine.tick()` returns as soon as any
`AI_BLOCKING` transition fires (`:75-81`) **[VERIFIED]**. So **while a `setDelay(...)` window is
open, the brewing stands are not accelerated at all**. The alchemist sets a 20-tick delay every time
it decides it is fully committed (`checkIfAbleToSmelt:864`), and a 400-tick delay whenever it wanders
its hut with nothing to do (`decide:326`).

### 2.2 `START_WORKING` → `decide()`

| Step | `file:line` | What happens |
|---|---|---|
| status | `:309` | `setVisibleStatus(WORKING)` every pass |
| **no orders** | `:310-335` | if the task queue is empty: with the navigation done, roll `nextInt(30) <= 1` for `HARVEST_NETHERWART`, then the same roll for `HARVEST_MISTLETOE`; otherwise `setDelay(400)` and walk to a random spot within 10 blocks of the hut corners. Return `IDLE`. |
| walk home | `:337-340` | not at the hut → `START_WORKING` |
| dump gate | `:342-346` | actions done ≥ 32 → hold the current state so the dump can fire |
| work | `:348` | `getNextCraftingState()` → `IDLE` / `INVENTORY_FULL` / `QUERY_ITEMS` / `GET_RECIPE` |

**There is no time-of-day gate.** Unlike the enchanter (`docs/studies/enchanter.md` §2.1) the
alchemist works the whole day, sunrise to roughly game time 11000 when `CitizenAI.java:183` sends it
to bed. **[VERIFIED]**

**It does stop in the rain.** `canWorkDuringTheRain()` is `false` for the alchemist's worker module,
so `CitizenAI.decide` returns `IDLE` for the whole of any rainstorm until the
`technology/rainman` research (`CitizenAI.java:269-278, 342-363`;
`DefaultResearchProvider.java:1647-1653`) **[VERIFIED]**. That is the same setting every crafter has,
so it is not an alchemist bug — but it is worth knowing that a worker whose bench is a brewing stand
indoors downs tools when it drizzles.

### 2.3 The crafting path, for a brewing recipe

```
decide → GET_RECIPE → getRecipe() → QUERY_ITEMS → checkForItems() → CRAFT → craft()
                                                                       ↓
                                          checkIfAbleToSmelt() → START_USING_BREWINGSTAND
                                                                       ↓
                                                        fillUpBrewingStand() → START_WORKING
                                                                       ↓ (later)
                            getRecipe()/craft() find a finished stand → RETRIEVING_END_PRODUCT…
                                                                       ↓
                                       retrieveBrewableFromBrewingStand() → INVENTORY_FULL / START_WORKING
```

The alchemist overrides five of the base crafter's methods so that a recipe whose `intermediate` is
`Blocks.BREWING_STAND` is routed to the stands instead of being crafted in hand:
`getExtendedCount` (`:357-392`), `getRecipe` (`:394-435`), `checkForItems` (`:694-732`),
`craft` (`:1066-1122`) and `decide` (`:306-349`). Anything whose intermediate is not a brewing stand —
in practice, `magicpotion`, whose generated recipe declares `"intermediate": "minecraft:air"` — falls
through to `super.craft()` and is made by hitting the hut block, like any other crafter's output.
**[VERIFIED]**

### 2.4 The one dead state

`RETRIEVING_USED_FUEL_FROM_BREWINGSTAND` is registered (`:109`) and its handler `retrieveUsedFuel()`
(`:809-832`) is fully written, but **nothing in the tree ever returns that state** — a grep across
`26.3/src/main/java` finds the enum declaration and the registration and nothing else **[VERIFIED]**.
Contrast the furnace equivalent, which is returned from
`mc/core/entity/ai/workers/AbstractEntityAIUsesFurnace.java:221`. 24 lines of dead code; harmless,
because in 26.3 the fuel stack is consumed in place (`v/.../BrewingStandBlockEntity.java:137`) and
there is nothing left in slot 4 to retrieve.

---

## 3. The two harvesting loops

### 3.1 Nether wart (`harvestNetherWart`, `:121-189`)

`BuildingAlchemist` records every `SOUL_SAND` position in the schematic
(`BuildingAlchemist.java:92-95`). The AI picks one at random each visit.

| Situation | `file:line` | What happens |
|---|---|---|
| no soil at all | `:127-130` | `IDLE`, silently |
| the block is no longer soul sand | `:152-155` | dropped from the list — self-healing |
| wart above | `:138-142` | walk to it |
| air above | `:143-150` | async request for 16 nether wart (min 1); if the request is new, `IDLE` |
| **wart not ripe** |  `:170-173` | `AGE < 2` → give up, `IDLE`. The walk is wasted. |
| ripe enough | `:176-187` | roll the drops for the statistic, then `mineBlock` |
| replant | `:189-208` | one wart out of the inventory into the ground |

**The ripeness gate is one short.** `NetherWartBlock.MAX_AGE = 3` (`v/world/level/block/NetherWartBlock.java:23`)
and the loot table pays `UniformGenerator.between(2, 4)` plus a fortune bonus **only when
`AGE == 3`** (`v/data/loot/packs/VanillaBlockLoot.java:1178-1187`) **[VERIFIED]**. At `AGE == 2` the
pool falls back to the bare `LootItem.lootTableItem(Items.NETHER_WART)` — one wart. Replanting costs
one wart (`:206`). **So every plant harvested at age 2 is a net zero, plus two walks.**

The fix is `AGE < NetherWartBlock.MAX_AGE`. One token. §8.3.

**A second, smaller thing in the same method.** `getNetherwartDrops` (`:220-227`) runs a *complete
second* `BlockPosUtil.getBlockDrops` roll purely so the statistic can be recorded, and it does so on
every pass of `HARVEST_NETHERWART` including the pass where `mineBlock` merely counts down its mining
delay (`AbstractEntityAIInteract.java:170-174`). The stack that ends up in the citizen's inventory
comes from `mineBlock`'s own independent roll (`:196`), so **the recorded statistic is a different
random sample from the wart actually harvested** — with a fortune-bearing tool the two will routinely
disagree. **[VERIFIED]** Cosmetic, but it is also a wasted loot-table evaluation per second while the
worker stands at a plant.

**The tool in hand is whatever was last displayed.** `mineBlock` reads `worker.getMainHandItem()` for
fortune and silk touch (`:176-197`), and the alchemist never equips anything for this job; the hand
usually holds a *copy* of a recipe input that `fillUpBrewingStand` put there for show
(`:946-949, 1012-1015`). That copy is not in the citizen's inventory, so nothing is duplicated and
nothing is damaged, and a water bottle carries no fortune — harmless, but it means **the axe the
building keeps (`BuildingAlchemist.java:72`) is never used for anything**, in this method or any
other. A grep for `ModEquipmentTypes.axe` in the alchemist's AI returns nothing **[VERIFIED]**.

### 3.2 Mistletoe (`harvestMistleToe`, `:234-304`)

`BuildingAlchemist` records every block in `BlockTags.LEAVES` (`:96-99`). The AI needs shears
(`:236-239`), walks to a random leaf, swings at it once per second, and on a `nextInt(40) <= 0` roll
(`:286`) produces exactly one `ModItems.mistletoe`, damages the shears by 1, and **returns
`INVENTORY_FULL`** (`:294`) **[VERIFIED]**.

Two things follow from that last line.

* **Expected 40 invocations at a 20-tick period = 800 game ticks (40 seconds) of swinging per
  mistletoe.** The state is re-entered from itself (`:303`), so once it commits it stays.
* **`INVENTORY_FULL` forces a full dump trip for a single item.** `dumpInventory` walks the citizen to
  the hut and empties it a few slots at a time (`AbstractEntityAIBasic.java:1305-1400`) **[VERIFIED]**.
  The normal threshold is 32 actions (`ACTIONS_UNTIL_DUMP = 32`,
  `mc/api/util/constant/CitizenConstants.java:177`); the mistletoe path bypasses it entirely. Every
  mistletoe costs a round trip.

Mistletoe stacks to `STACKSIZE` (`mc/core/items/ItemMistletoe.java:17`), so there is no inventory
reason for the immediate dump.

### 3.3 How often either loop runs

Both are entered from `decide()` only when the task queue is empty, on a `nextInt(30) <= 1` roll — a
2-in-30 chance each, the second conditional on the first failing (`:314-322`). Between rolls the
worker sets a 400-tick delay and strolls (`:326-327`).

Per roll: `P(netherwart) = 2/30 = 6.67 %`, `P(mistletoe) = (28/30)(2/30) = 6.22 %`, otherwise 405 ticks
of strolling. Taking a netherwart trip at ~100 ticks and a mistletoe run at ~1000 ticks (800 swinging
plus the walk and the dump), the mean roll-cycle is

```
0.0667 × 100 + 0.0622 × 1000 + 0.871 × 405 ≈ 422 game ticks
```

An 11000-tick working day therefore holds about **26 rolls**, giving **≈1.6 mistletoe** and
**≈1.7 nether-wart trips** per game day. **[UNVERIFIED]** as a measurement — the constants and
probabilities are all verified, the trip durations are estimates, and confirming the figure means
watching a colony for a few in-game weeks. It is the right order of magnitude regardless: the roll
alone caps mistletoe at one per ~6500 ticks even if picking were instant.

---

## 4. The brewing stand

### 4.1 What 26.3 changed underneath it

`BrewingStandBlockEntity` in snapshot-10:

* **Fuel is a data component.** `serverTick` reads `fuel.get(DataComponents.BREWING_FUEL)`
  (`v/.../BrewingStandBlockEntity.java:131-139`), and `canPlaceItem(4, stack)` accepts anything with
  that component (`:270-272`) **[VERIFIED]**. `BrewingFuel` is `(uses, speedMultiplier)`, both
  resolvable numbers (`v/world/item/component/BrewingFuel.java:11-18`). In vanilla exactly one item
  carries it — `Items.BLAZE_POWDER`, with `BREWING_DEFAULT_USES` = 20 and a 1.0 multiplier
  (`v/world/item/Items.java:2023`, `v/world/level/storage/loot/providers/number/NumberProviders.java:99-100`)
  **[VERIFIED]**.
* **Brew time is `ceil(400 / speedMultiplier)`** (`:157`) — 400 ticks at the vanilla multiplier.
* **The bottle slots refuse anything that is not a potion input**: `canPlaceItem(0..2, stack)` requires
  `PotionIngredient.isPotionInput(stack, recipeAccess) && getItem(slot).isEmpty()` (`:279-281`), and
  slot 3 requires membership of the `BREWING_REAGENTS` recipe property set (`:280`) **[VERIFIED]**.
* **Brewing is an ordinary recipe type.** `RecipeType.BREWING` / `BrewingRecipe`; `PotionBrewing` and
  `Level#potionBrewing()` are gone. The vanilla pack defines **279 brewing recipes** — 63 mixes × 3
  containers, plus 2 container transformations × 45 potions
  (`v/data/recipes/packs/VanillaBrewingProvider.java`, `v/data/recipes/BrewingProvider.java:34-54`)
  **[VERIFIED]** by counting the provider calls. 45 of the 46 potions in
  `v/world/item/alchemy/Potions.java` are brewable; `LUCK` is not, in vanilla either **[VERIFIED]**.

The port handled the recipe-type change (`BrewingCraftingType`, `WindowBrewingstandCrafting`) and the
`ServerLevel` signature change. **It did not notice the fuel component.** The mod names
`Items.BLAZE_POWDER` literally in five places (`EntityAIWorkAlchemist.java:546, 547, 550, 564, 566,
600, 602, 625, 629`) **[VERIFIED]**, so a datapack that adds a second brewing fuel — or that gives
blaze powder a different `uses` count — is invisible to the alchemist. Today, with the vanilla pack,
the hardcode is *correct*; it is a future-proofing gap, not a live bug. §8.7.

### 4.2 Fuel handling

`isFuelNeeded` (`:506-532`) fires every 200 ticks and only while a brewing recipe is current. It is
true when any stand is idle and either has a reagent but no fuel or has neither. `checkBrewingStandFuel`
(`:537-591`) then either requests blaze powder, gathers it from the hut, or sends the worker to
`ADD_FUEL_TO_BREWINGSTAND`, which transfers `BREWING_MIN_FUEL_COUNT = 8`
(`mc/api/util/constant/Constants.java:152`) into slot 4 (`:628-630`). Eight blaze powder is 160 brews
(`DEFAULT_FUEL_USES = 20`, `v/.../BrewingStandBlockEntity.java:52`).

**The request for fuel is gated on the worker having no other outstanding `Stack` request at all:**

```java
if (!hasItemInItemHandler(worker…, BLAZE_POWDER)
      && !hasItemInProvider(building, BLAZE_POWDER)
      && !building.hasWorkerOpenRequestsOfType(worker.getCitizenData().getId(), TypeToken.of(Stack.class)))
```
(`:546-548`) **[VERIFIED]**

`hasWorkerOpenRequestsOfType` is `!getOpenRequestsOfType(...).isEmpty()`
(`mc/core/colony/buildings/AbstractBuilding.java:1697-1712`) **[VERIFIED]**, and the nether-wart
request the farming loop files (`:145`, `:191`) is a `Stack` request through
`checkIfRequestForItemExistOrCreateAsync` (`AbstractEntityAIBasic.java:1997-2000`) **[VERIFIED]**.
Nether wart is only obtainable in the Nether or from the alchemist's own plot, so that request can sit
open for a long time — and while it does, **the alchemist cannot ask for blaze powder**. Whether a
colony actually deadlocks on this depends on how long the request system leaves an unfillable request
open, which I did not trace. **[UNVERIFIED]** — confirming it means watching a colony with no nether
wart in the warehouse and an alchemist that has run out of fuel. §8.6.

### 4.3 Acceleration, and what the secondary skill buys

```java
final int accelerationTicks = (skillHandler.getLevel(getModuleForJob().getSecondarySkill()) / 10) * 2;
```
(`:473`) **[VERIFIED]** — secondary skill is **Mana**.

Every 20 game ticks, each registered stand receives `accelerationTicks` synthetic
`BrewingStandBlockEntity.serverTick` calls on top of the 20 it gets naturally (`:490-496`). The speed
factor is therefore

```
1 + 2·floor(Mana/10) / 20   =   1 + floor(Mana/10)/10
```

| Mana | Factor | Brew time |
|---|---|---|
| 0–9 | 1.0× | 400 ticks (20 s) |
| 30 | 1.3× | 308 ticks |
| 50 | 1.5× | 267 ticks |
| 99 | 1.9× | 211 ticks (10.5 s) |

**[VERIFIED]** by arithmetic on `:473`, the 20-tick transition period, and
`v/.../BrewingStandBlockEntity.java:157`. Caveat, from §2.1: the accelerate pass is suppressed inside
any `setDelay` window, so real-world speed is somewhere between 1.0× and the figure above.
**[UNVERIFIED]** where in that band it lands.

### 4.4 How many stands he can use

```java
final int maxSkillBrewingStand = (skillHandler.getLevel(getModuleForJob().getPrimarySkill()) / 10) + 1;
return Math.min(maxSkillBrewingStand, building.getAllBrewingStandPositions().size());
```
(`:649-653`) **[VERIFIED]** — primary skill is **Dexterity**.

| Dexterity | Stands used |
|---|---|
| 0–9 | 1 |
| 10–19 | 2 |
| 50–59 | 6 |
| 90–99 | 10 |

A fresh citizen rolls its initial skills in `1..happiness-1`, so **a newly hired alchemist uses exactly
one brewing stand no matter how many the hut contains**. This is the single largest level-up effect in
the profession and it is on the *citizen*, not the building.

`countOfBubblingBrewingStands()` (`:442-466`) gates on the same number: `checkIfAbleToSmelt` refuses to
load another stand while `burning >= getMaxUsableBrewingStands()` (`:862`), and `fillUpBrewingStand`
transfers nothing while `burningCount >= maxBrewingStands` (`:963-966, 1022-1025`) **[VERIFIED]**.

### 4.5 The loading order, and the input the AI expects

`fillUpBrewingStand` fills the three bottle slots first and the reagent slot afterwards
(`:928-997` then `:998-1055`) — which is the order the stand requires, since `isBrewable` needs both a
reagent and at least one matching bottle (`v/.../BrewingStandBlockEntity.java:191-214`). It reads:

```java
final ItemStack potionStack     = currentRecipeStorage.getCleanedInput().get(0).getItemStack();   // :932
final ItemStack ingredientStack = currentRecipeStorage.getCleanedInput().get(1).getItemStack();   // :1000
```

**[VERIFIED]** — input 0 is the bottle, input 1 is the reagent. `RecipeStorage#processInputsAndTools`
preserves the declared order (`RecipeStorage.java:372-419`) **[VERIFIED]**, and the teaching GUI
declares them in exactly that order with the bottle at count 3
(`WindowBrewingstandCrafting.java:120-125`) **[VERIFIED]**. Everything taught through the GUI is
therefore consistent. §8.2 is about the one path that is not.

**A unit slip in the reagent branch.** The bottle branch computes its target in bottles
(`count × outputCount`, `:937-938`); the reagent branch computes the same figure and then subtracts
`ingredientInBrewingStand * 3` from it (`:1003-1005`) **[VERIFIED]**, so the number handed to
`needsCurrently` is three times the reagents actually needed. The worker over-gathers by 3× and dumps
the surplus back. Churn, not loss. §8.9.

---

## 5. What it produces, and who consumes it

### 5.1 `minecolonies:magicpotion` — one real consumer

One recipe, generated by `DefaultAlchemistCraftingProvider.java:47-53` and written to
`gen/crafterrecipes/alchemist/magicpotion.json`:

```
1 minecolonies:mistletoe  +  1 minecolonies:large_water_bottle  →  1 minecolonies:magicpotion
research gate: minecolonies:effects/consumepotions
```

`large_water_bottle` declares `craftRemainder(large_empty_bottle)`
(`mc/apiimp/initializer/ModItemsInitializer.java:263`) and `RecipeStorage` turns a crafting remainder
into a secondary output automatically (`RecipeStorage.java:381-386`) **[VERIFIED]**, so the glass is
returned. The real input is one mistletoe and one bucket of water.

**The druid drinks it.** `EntityAIDruid.atBuildingActions` pulls up to 32 magic potions from the hut
and files a request for 16 whenever it holds fewer than 8
(`mc/core/entity/ai/workers/guard/EntityAIDruid.java:51-63`) **[VERIFIED]**. In combat,
`DruidCombatAI.attack` consumes exactly one per throw (`:174-177`), and having one raises the potion's
amplifier from 0 to 2 and widens the effect pool from 1 to 2 (adverse) or 1 to 4 (support)
(`:155-171`) **[VERIFIED]**. **A druid without magic potions still fights** — it throws amplifier-0
potions of a single effect — so the alchemist is a force multiplier, not a prerequisite.

This is the one place the enchanter's problem does not repeat: the alchemist's flagship product has a
genuine, hungry, automatic in-colony consumer.

The gate on both sides is the same research: `combat/druidpotion` ("Panoramix"), which grants
`effects/consumepotions` and costs **64 mistletoe** plus a level-3 barracks
(`DefaultResearchProvider.java:1234-1240`, `gen/researches/combat/druidpotion.json`) **[VERIFIED]**.

One cosmetic miss: the druid's render flag for "carrying potions" tests `Items.POTION`
(`EntityAIDruid.java:39`) while what it stocks is `ModItems.magicpotion` **[VERIFIED]**, so the
overlay never appears from alchemist supply. Upstream's.

### 5.2 Brewed potions — nobody in the colony wants one

The brewing module's output is whatever vanilla potion the player taught it. A grep across all of
`26.3/src/main/java` for `Items.POTION`, `SPLASH_POTION` and `LINGERING_POTION` finds: the druid's
throwing stack (a fresh `Items.SPLASH_POTION.getDefaultInstance()`, not one the alchemist made,
`DruidCombatAI.java:152`), three research icons, the baker's water-bottle recipe, the alchemy quest's
reward, and a permission enum. **No worker requests a potion, no recipe takes one as an input, no
building requires one.** **[VERIFIED]**

That is the enchanter's shape exactly (`docs/studies/enchanter.md` §6): the machinery exists, it works,
and its output has no sink inside the colony. The difference is that a potion is *useful to the
player*, stacks with its own kind, and is something a player will actually walk to the warehouse for —
where an enchanted book is not. So this is a design observation, not a defect, and it is why the
alchemist deserves a min-stock module it does not have (§1).

### 5.3 Mistletoe — one source, three sinks

**Source:** the alchemist, and nothing else. A grep for `mistletoe` across `26.3/src/main` finds the
item registration, the creative tab, the alchemist's own AI and building, the compostables tag, the
compostable data map, and the two recipes below **[VERIFIED]**. No loot table, no trade, no other
worker.

**Sinks:**

* the `magicpotion` recipe, one each (`DefaultAlchemistCraftingProvider.java:48`);
* `combat/druidpotion`, 64 once per colony (`DefaultResearchProvider.java:1238`);
* the composter, *if the player ticks it* — mistletoe is in `#minecolonies:compostables`
  (`mc/core/generation/defaults/DefaultItemTagsProvider.java:145`) and carries a 0.5 compost value
  (`DefaultDataMapsProvider.java:61`). The composter's item list starts **empty** and it composts only
  what the player selected (`EntityAIWorkComposter.java:422-431`) **[VERIFIED]**, so this is an
  opt-in hazard, not a default one.

### 5.4 Nether wart

Consumed by the farm's own replanting (`:206`), by any brewing recipe that starts from awkward
(one reagent per three potions), and by the `technology/alchemist` research itself — 16 wart, which the
player must import from the Nether before the hut exists (`DefaultResearchProvider.java:1462`).

---

## 6. The research tree

```
technology/morescrolls   (enchanter L3; 64 paper + 1 ancient tome + 64 lapis)
        └── technology/opennether     (3 gilded blackstone) → unlocks the nether worker
                └── technology/alchemist  "Magic Potions"  (16 nether wart) → unlocks the hut
                        └── technology/oceanheart  (fisherman L4 + 1 heart of the sea) → fishing treasure

combat/arrowuse
        └── combat/druidpotion  "Panoramix"  (barracks L3; 64 mistletoe) → druids use magic potions
```

**[VERIFIED]** from `DefaultResearchProvider.java:1449-1486, 1234-1240` and the generated JSON.

Two things follow.

* **The alchemist sits behind the enchanter.** `morescrolls` requires an enchanter at level 3 and an
  ancient tome, which `docs/studies/enchanter.md` §6 shows drops only from raiders. A colony cannot
  reach the alchemist until it has survived raids and built a tower this repository's own audit
  recommends stopping at level 3.
* **The research is worth taking even if the hut never is.** `technology/alchemist` is the sole parent
  of `technology/oceanheart`, which is the fishing-treasure effect. 16 nether wart for that is cheap.
* **`combat/druidpotion` is the chicken-and-egg.** Its 64-mistletoe cost can only be paid by an
  alchemist, and the alchemist's mistletoe is only worth anything once that research is done — the
  `magicpotion` recipe itself is gated on `effects/consumepotions`
  (`DefaultAlchemistCraftingProvider.java:51`). So the first forty game days of an alchemist's life
  are spent picking leaves to unlock the reason he was hired.

---

## 7. Levels, skills, and whether he is worth a citizen

### 7.1 What the building level changes

| | Effect | Source |
|---|---|---|
| Recipe capacity | `2^level × 5 × (1 + RECIPES research)` — 10 / 20 / 40 / 80 / **160** at levels 1–5, ×5 again with all four recipe researches | `AbstractCraftingBuildingModule.java:174-182`, `EXTRA_RECIPE_MULTIPLIER = 5` at `:90` |
| Tool tier | wood/gold at level 1, maximum at level 5 — the shears he picks mistletoe with | `mc/api/colony/buildings/IBuilding.java:479-490` |
| Quest | `general/alchemy` needs an alchemist hut at level 2 | `gen/colony/quests/general/alchemy.json` triggers |
| Schematic contents | number of brewing stands, soul sand blocks and leaf blocks | **[UNVERIFIED]** — the blueprints are runtime-fetched assets and are not in this repository (`26.3/src/main/resources/` has `assetfetch/` and no `blueprints/`) **[VERIFIED]** |
| Worker count | **unchanged**: always 1 | `BuildingModules.java:263`, `(b) -> 1` |

All **[VERIFIED]** except the schematic row.

Against 279 vanilla brewing recipes (§4.1), the capacity ladder means: level 1 holds 10, which is one
potion line and its splash variant; level 3 holds 40, which is a realistic menu; level 5 holds 160,
which is 57 % of everything vanilla can brew, and all 279 with the recipe research from the sawmill
branch (`DefaultResearchProvider.java:1613-1646`). **[VERIFIED]**

Note what "one recipe" means here: *each container form is separate*. Potion, splash and lingering
healing II are three learned recipes, and each needs its own precursor chain. That is why 10 slots go
quickly.

### 7.2 What the citizen's skills change

| Skill | Effect | Source |
|---|---|---|
| **Dexterity** (primary) | stands used = `floor(Dex/10) + 1`, capped by the stand count | `:649-653` |
| **Dexterity** (again) | crafting speed for `magicpotion`: `PROGRESS_MULTIPLIER / min(Dex/2 + 1, MAX_LEVEL) × HITTING_TIME` | `AbstractEntityAICrafting.java:758-762`, `CraftingWorkerBuildingModule.java:41` |
| **Mana** (secondary) | brewing acceleration, 1.0×–1.9× (§4.3) | `:473` |
| **Mana** (nominally) | recipe improvement — **never invoked for brewing**: `improveRecipe` is only called from `executeCraftingAction` (`AbstractEntityAICrafting.java:646`), which the brewing path never reaches | **[VERIFIED]** |

The last row is worth stating plainly: **the alchemist's secondary skill has exactly one effect, and it
is the brewing speed.** That is also the correct outcome — `improveRecipe` reduces an input's amount by
one (`AbstractCraftingBuildingModule.java:665-706`), and applied to a brewing recipe's three-bottle
input it would silently break the accounting in `checkForItems` and `fillUpBrewingStand`.

### 7.3 The arithmetic, in game days

**Self-supply.** From §3.3: **≈1.6 mistletoe** and **≈1.7 nether-wart trips** per game day. Of those
trips, roughly half are replanting rather than harvesting (a plot needs one visit to harvest and a
later one to replant), and a ripe plot pays 2–4. Net:

```
≈0.85 harvests/day × 3 wart  −  0.85 replants/day × 1 wart  ≈  1.7 net nether wart per game day
```

One nether wart is one brewing operation — three awkward potions. **The farm sustains about five
awkward potions a day.** **[UNVERIFIED]** as a measurement; the drop table, the age gate, the roll
probabilities and the replant cost are all verified, the visit rate is derived.

**Brewing capacity, for comparison.** Three potions per stand per 211–400 ticks, times
`floor(Dex/10)+1` stands. Even at Dexterity 20 and Mana 50 that is 6 potions per 267 ticks — **247
potions per 11000-tick day** if the inputs existed. They do not. **The alchemist is input-starved by
two orders of magnitude, and every input except nether wart has to come from the player**: glass
bottles, blaze powder, and the reagents (glistering melon, ghast tear, rabbit's foot, magma cream,
pufferfish, phantom membrane, breeze rod…).

That capacity figure is an upper bound and almost certainly not reachable: each stand needs a separate
`fillUpBrewingStand` visit for the bottles, another for the reagent, and a
`retrieveBrewableFromBrewingStand` visit to unload, each costing a walk and a `setDelay(5)` round trip
through `START_WORKING`. **[UNVERIFIED]** where the worker's service rate actually caps out; measuring
it means timing a stocked alchemist with 4+ stands.

**Magic potions against druid demand.** Supply ≈1.6/day. A druid throws one per `getAttackDelay()`,
which is 40 ticks, doubled to 80 for instantaneous effects
(`DruidCombatAI.java:189-192`, `AttackMoveAI.java:238-241`) **[VERIFIED]**. So:

| | |
|---|---|
| One alchemist-day of magic potions | 1.6 |
| Time a druid needs to throw 1.6 potions | **64–128 game ticks (3.2–6.4 s)** |
| Filling one druid's pouch once (32 potions) | **20 game days** |
| Three druids, one fill each | **60 game days** |
| `combat/druidpotion` research (64 mistletoe) | **40 game days** |

**[VERIFIED]** arithmetic over **[UNVERIFIED]** supply rate.

### 7.4 The verdict, plainly

**Take the research. Build the hut to level 3. Do not expect it to feed itself.**

* **Level 1–2** is a single potion line and the alchemy quest. Fine as a starter.
* **Level 3** (40 recipes) is where a real potion menu fits — a few effects, their strong and long
  variants, and the splash conversions. This is the stopping point for almost every colony.
* **Level 4–5** buys recipe slots for potions nothing in the colony consumes. It buys nothing else
  that the citizen's own Dexterity does not already gate.
* **Employ a citizen on it if** you have druids and are willing to run a mistletoe operation for six
  in-game weeks before the research lands, **or** if you want a bulk potion machine and will keep it
  stocked with bottles, blaze powder and reagents by hand. As a self-sufficient production hut it is
  not: its two farms together produce about **1.6 mistletoe and 1.7 nether wart a day**, and the same
  citizen on a farm, a mine or a forester returns something measurable every single day.

---

## 8. Findings, ranked

Sizes: **S** under ~150 lines, **M** ~150–400, **L** 400+.
**No proposal in this study needs a mixin.** Where an access widener is involved it is named.

### 8.1 — The "no brewing stand" message is thrown away before the player sees it. **S**

*Code.* Both places the alchemist reports having no stand raise
`StandardInteraction(BAKER_HAS_NO_FURNACES_MESSAGE, BLOCKING)`
(`EntityAIWorkAlchemist.java:905, 1089`). The validator registered for that string is
`citizen.getWorkBuilding() instanceof BuildingBaker && …getFurnaces().isEmpty()`
(`InteractionValidatorInitializer.java:251-252`), which replaces an earlier, looser registration for
the same key at `:58-59` because the registry is a `HashMap<Component, Predicate<…>>`
(`mc/api/colony/interactionhandling/InteractionValidatorRegistry.java:22, 73-76`).
`CitizenData#triggerInteraction` adds nothing whose `isValid` is already false
(`CitizenData.java:1873`), and `ServerCitizenInteraction#isValid` is `validator.test(citizen)`
(`ServerCitizenInteraction.java:112-115`). **[VERIFIED] end to end.**

*In play.* An alchemist whose brewing stands were broken, replaced or never registered (§8.4's mirror
image) loops `CRAFT → craft() → interaction discarded → setDelay(5) → START_WORKING → GET_RECIPE →
CRAFT` forever, status "Working", with no chat bubble and no explanation. Requests never complete.

*Fix.* Give the alchemist its own translation key and its own validator —
`citizen.getWorkBuilding() instanceof BuildingAlchemist && ((BuildingAlchemist) …).getAllBrewingStandPositions().isEmpty()`
— modelled on the beekeeper's `NO_HIVES` two lines below (`:254-255`). Roughly 10 lines plus one new
key in `26.3/src/main/resources/assets/minecolonies/lang/en_us.json`, which is a port-owned file (the
upstream language assets are runtime-fetched and are not in this repository **[VERIFIED]**).
**Neither a mixin nor an access widener.** Not a balance change.

*Ours or theirs?* **Upstream's.** The same two registrations and the same baker key appear in the
1.21.1 snapshot at `:55-56` and `:219-220` **[VERIFIED]**.

### 8.2 — `/mc colony teachRecipes` teaches the alchemist unusable brewing recipes. **S**

*Code.* `BrewingCraftingType.findRecipes` emits
`withInputs(List.of(reagents, containers))` — reagent first (`BrewingCraftingType.java:70-76`).
`CommandColonyTeachRecipes.toStorage` copies `recipe.getInputs()` in order into a `RecipeStorage`
(`:213-243`), and `RecipeStorage#processInputsAndTools` preserves that order
(`RecipeStorage.java:372-419`); `GenericRecipeUtils.filterInputs` preserves it too
(`GenericRecipeUtils.java:82-112`). The AI then reads input 0 as the bottle and input 1 as the reagent
(`EntityAIWorkAlchemist.java:932, 1000`). **[VERIFIED] at every link.**

*In play.* The alchemist tries to put nether wart into bottle slots 0–2 and a water bottle into reagent
slot 3. `InvWrapper.insertItem` checks `canPlaceItem` first and returns the stack untouched
(`mc/api/inventory/api/InvWrapper.java:56-59`), and vanilla rejects both
(`v/.../BrewingStandBlockEntity.java:279-281`). `transferXInItemHandlerIntoSlotInItemHandler` returns 0
and breaks out (`InventoryUtils.java:2103-2125`) **[VERIFIED]** — so **nothing is lost and nothing is
brewed**. The worker cycles through `fillUpBrewingStand` indefinitely. Since the command is meant to
put a colony into a state where the request → craft → deliver chain can be exercised, this is a test
tool that quietly makes one profession untestable.

The same ordering also renders the brewing recipes backwards in the JEI crafter view, which reads the
same `IGenericRecipe`s (`mc/core/compatibility/jei/GenericRecipeCategory.java:364`) **[VERIFIED]**.

*Fix.* Swap the two lists in `BrewingCraftingType.findRecipes` so the enumerator agrees with the
teaching GUI, the AI and the JEI display:

```java
.withInputs(List.of(containers.stream().map(stack -> stack.copyWithCount(3)).toList(),
                    reagents))
```

Two lines. The JEI transfer handler is unaffected — it reads JEI's own `IJeiBrewingRecipe`, not this
(`mc/core/compatibility/jei/transfer/PrivateBrewingTeachingTransferHandler.java:75-84`) **[VERIFIED]**.
**Neither a mixin nor an access widener.** Not a balance change: no shipped recipe changes, only the
order two inputs are listed in.

*Ours or theirs?* **The ordering is upstream's** — `git show b104817d^:1.21.1/.../BrewingCraftingType.java:50-54`
has `List.of(List.of(ingredient), List.of(container.copyWithCount(3)))` and upstream's teaching GUI has
the reverse **[VERIFIED]**. **The consequence is ours**, because `CommandColonyTeachRecipes` is
port-added and is the only path that turns an enumerated brewing recipe into a real one.

### 8.3 — The nether-wart farm harvests a stage early. **S**

*Code.* `if (aboveState.getBlock() == Blocks.NETHER_WART && aboveState.getValue(NetherWartBlock.AGE) < 2)`
(`EntityAIWorkAlchemist.java:170`) against `MAX_AGE = 3`
(`v/world/level/block/NetherWartBlock.java:23`) and a loot table that pays 2–4 only at age 3
(`v/data/loot/packs/VanillaBlockLoot.java:1178-1187`). **[VERIFIED]**

*In play.* Every plant caught at age 2 yields one wart and costs one to replant: a wasted round trip
in a loop that only runs about 1.7 times a game day. Age 2 → 3 takes roughly 13 600 game ticks of
random ticking (`randomTick` advances on a 1-in-10 roll, `v/.../NetherWartBlock.java:51-57`), so the
window is real, not theoretical.

*Fix.* `AGE) < NetherWartBlock.MAX_AGE`. One token. **Neither a mixin nor an access widener** —
`NetherWartBlock.MAX_AGE` is `public static final` and the class is already access-widened to
accessible by Fabric's transitive wideners (`v/.../NetherWartBlock.java:19-22`) **[VERIFIED]**.

**Balance:** yes, mildly — it roughly triples the farm's yield per harvest. Given §7.3 that takes the
farm from "insignificant" to "small".

*Ours or theirs?* **Upstream's**, byte-identical (§0.2).

### 8.4 — The building's position lists accumulate a duplicate set per build. **S**

*Code.* `BuildingAlchemist.registerBlockPosition` appends without checking
(`:88-104`); `deserializeNBT` appends to lists it never clears (`:106-127`).
`BuildingStructureHandler.triggerSuccess` calls `registerBlockPosition` for every block of the
schematic on every build pass, upgrade or repair (`:199-206`). The florist, doing the same job for
composted dirt, guards with `!plantGround.contains(pos)` (`BuildingFlorist.java:103`). **[VERIFIED]**

*In play.* A hut taken to level 5 has been built five times, so `brewingStands`, `soulsand` and
`leaves` each hold five copies of every position. Consequences, in order of severity:

1. **`accelerateBrewingStand` ticks each physical stand once per duplicate** (`:482-499`), so a
   level-5 hut brews at up to five times the intended acceleration — and does five times the work
   (§10).
2. `countOfBubblingBrewingStands` counts a bubbling stand once per duplicate (`:442-466`), which
   inflates it against `getMaxUsableBrewingStands` and can make `checkIfAbleToSmelt` decline to load a
   stand that is genuinely free (`:862`).
3. `removeBrewingStand`/`removeSoilPosition`/`removeLeafPosition` are `List.remove(Object)` and
   delete **one** occurrence (`:191-212`), so the self-healing paths need as many passes as there are
   duplicates.
4. The lists, and the NBT they serialise to, grow linearly with the number of builds.

*Fix.* Three `contains` guards in `registerBlockPosition`, and a `clear()` at the top of each
`deserializeNBT` block. About 10 lines. **Neither a mixin nor an access widener.**
**Balance:** yes — it removes an accidental brewing-speed bonus that scales with hut level. Existing
saves will keep their duplicates unless the fix also de-duplicates on load, which the `clear()` plus a
`contains` guard in the read loop would do.

*Ours or theirs?* **Upstream's**, byte-identical apart from the NBT accessor rename (§0.2).

### 8.5 — One mistletoe costs a full trip home. **S**

*Code.* `harvestMistleToe` returns `INVENTORY_FULL` after every single pick (`:294`), bypassing the
32-action threshold (`ACTIONS_UNTIL_DUMP = 32`, `CitizenConstants.java:177`;
`inventoryNeedsDump` at `AbstractEntityAIBasic.java:467-474`). `dumpInventory` walks the citizen to the
hut (`:1305-1400`). **[VERIFIED]**

*In play.* Forty seconds of swinging, then a walk home, a dump, and a walk back — for one item that
stacks to a full stack.

*Fix.* Return `HARVEST_MISTLETOE` and let `incrementActionsDone()` (or simply the normal dump
threshold) decide. Two lines. **Neither a mixin nor an access widener.**
**Balance:** yes — it meaningfully raises the mistletoe rate, which is the input to §7.3's whole
argument. Ship it with 8.3 and re-derive the numbers.

*Ours or theirs?* **Upstream's.**

### 8.6 — An open nether-wart request blocks the blaze-powder request. **S**

*Code.* `:546-548`, `hasWorkerOpenRequestsOfType(id, TypeToken.of(Stack.class))` — any outstanding
`Stack` request suppresses the fuel request, and the farm's nether-wart request is one (`:145, 191`
via `checkIfRequestForItemExistOrCreateAsync`). **[VERIFIED]** that the code says this;
**[UNVERIFIED]** how long an unfillable request stays open, which is what decides whether it bites.

*Fix.* Narrow the guard to blaze-powder requests, the way every other worker does it — use
`checkIfRequestForItemExistOrCreateAsync(new ItemStack(Items.BLAZE_POWDER), n, 1)` instead of the
hand-rolled `createRequestAsync` plus type test. About 5 lines. **Neither a mixin nor an access
widener.** Not a balance change.

*Ours or theirs?* **Upstream's.**

### 8.7 — Brewing fuel is hardcoded to blaze powder; 26.3 made it a data component. **S**

*Code.* `Items.BLAZE_POWDER` appears nine times in `EntityAIWorkAlchemist.java` (`:546, 547, 550, 564,
566, 600, 602, 625, 629`). Vanilla's own test is `itemStack.has(DataComponents.BREWING_FUEL)`
(`v/.../BrewingStandBlockEntity.java:270-272`), and the component carries a `uses` count and a
`speedMultiplier` (`v/world/item/component/BrewingFuel.java:11`). **[VERIFIED]**

*In play.* Nothing today: blaze powder is the only vanilla item with the component
(`v/world/item/Items.java:2023`) **[VERIFIED]**. It matters for datapacks and for the next vanilla
version that adds a fuel.

*Fix.* Replace the item identity tests with `stack.has(DataComponents.BREWING_FUEL)` and pick a
concrete item for the *request* from whatever the colony already has, falling back to blaze powder.
Roughly 20 lines. **Neither a mixin nor an access widener** — `DataComponents.BREWING_FUEL` is public.
Not a balance change against the vanilla pack.

*Ours or theirs?* The hardcode is upstream's; **the failure to notice the 26.3 component is ours**,
since 26.3 is the version this port targets. This is the same class of defect the enchanter study
named: an API that changed shape underneath a call that still compiles.

### 8.8 — `RETRIEVING_USED_FUEL_FROM_BREWINGSTAND` is unreachable. **S**

*Code.* Registered at `:109`, handler at `:809-832`, returned by nothing
(grep over `26.3/src/main/java`). **[VERIFIED]**

*Fix.* Delete the state, the target and the handler — 30 lines removed — or wire it up the way the
furnace worker does (`AbstractEntityAIUsesFurnace.java:221`). Deleting is right: in 26.3 the fuel stack
is consumed in place and slot 4 never holds anything to reclaim. **Neither a mixin nor an access
widener.** Not a balance change.

*Ours or theirs?* **Upstream's.**

### 8.9 — Smaller things, verified and not worth their own section

* **The reagent gather target is 3× too large.** `:1003-1005` subtracts `ingredientInBrewingStand * 3`
  from a bottle-denominated target and hands the result to `needsCurrently`. Over-gathering, dumped
  back later. One line. **[VERIFIED]**
* **`checkForItems` cancels a request and then returns `CRAFT` anyway.** `:722-731` calls
  `job.finishRequest(false); resetValues();` inside the input loop without returning, and `resetValues`
  does not null `currentRecipeStorage` or `currentRequest`
  (`AbstractEntityAICrafting.java:698-706`). The worker recovers through `craft()`'s
  `CANCELLED/FAILED` branch (`:1112-1119`) a few ticks later, so this is untidy rather than broken.
  **[VERIFIED]**
* **The alchemist's `checkForItems` override drops free mode's supply hook.** The base version has a
  `worksWithoutMaterials() && supplyMaterialWithoutRequest(...)` branch
  (`AbstractEntityAICrafting.java:523-527`); the override does not (`:694-732`). `getRecipe`'s hook
  (`:419-425`) still fires, so free mode probably still works, but the two paths disagree.
  **[VERIFIED]** that the branch is absent; **[UNVERIFIED]** whether free-mode brewing actually
  stalls. This one is **ours** — free mode is a port feature (`mc/core/debug/FreeMode.java`).
* **The statistic and the harvest are two different loot rolls.** §3.1. **[VERIFIED]**
* **The druid's potion render flag tests the wrong item.** `EntityAIDruid.java:39`. **[VERIFIED]**
* **No `build_alchemist` advancement.** §1. **[VERIFIED]**
* **`getPositionOfBrewingStandToRetrieveFrom` reads block entities without a loaded-chunk guard**
  (`:668-670`), unlike every other loop in the class, which calls `WorldUtil.isBlockLoaded` first.
  **[VERIFIED]**

---

## 9. Failure modes

| Situation | What the code does | `file:line` | What the player sees |
|---|---|---|---|
| **No brewing stand** | blocking interaction raised and **immediately discarded**; `setDelay(5)`; `START_WORKING` | `:905, 1089`; `CitizenData.java:1873` | **Nothing.** Status "Working", forever. §8.1 |
| **A brewing stand was broken** | first pass through any of the five loops drops it from the list | `:383, 430, 461, 520, 586, 882, 1059` | Self-healing, and thorough — seven separate call sites do it. Good. |
| **No soul sand registered** | `IDLE`, silently | `:127-130` | Nothing. Correct: the schematic supplies the soil, so an empty list means the hut was never built. |
| **No leaves registered** | `IDLE`, silently | `:245-248` | Nothing. Same reasoning. |
| **No shears** | `checkForToolOrWeapon` files a request, `IDLE` | `:236-239` | The standard tool request and its chat bubble. Fine. |
| **No nether wart to plant** | async request for 16, `IDLE` | `:145-150, 191-197` | A request appears in the colony list. Fine, though nothing explains that the Nether is the only source. |
| **No blaze powder** | request, unless another `Stack` request is open (§8.6); otherwise wait in place | `:546-552, 572-573` | A request appears — or does not. |
| **A stand is full of the wrong potion** | `getPositionOfBrewingStandToRetrieveFrom` only matches the current recipe's primary output, so a stand holding something else is never emptied | `:676-685` | The stand is occupied and the worker cannot use it. No message. **[UNVERIFIED]** how often this happens in play — it needs a cancelled order to leave bottles behind. |
| **Request cancelled mid-brew** | `craft()`'s `CANCELLED/FAILED` branch resets and returns `START_WORKING`; the bottles already in the stand stay there | `:1112-1119` | Leftovers in the stand, picked up by `getExtendedCount` on the next matching order. Untidy but not lost. |
| **Racks full** | `dumpInventory` raises `INVENTORYFULLCHEST` and files a forced pickup | `AbstractEntityAIBasic.java:1314-1334` | Correct and visible. |
| **Rain** | `CitizenAI` returns `IDLE` for the whole storm | `CitizenAI.java:269-278` | The standard bad-weather status. Consistent with every other crafter. |
| **Night** | sleeps from ~11000 to dawn; the stands keep brewing at the vanilla rate with nobody accelerating or unloading them | `CitizenAI.java:183-197` | A stand that finishes overnight sits full until morning. Fine. |
| **Interrupted mid-state** | every alchemist state is `(true)` — okay to eat and okay to interrupt | `AIWorkerState.java:647-672` | No starvation trap of the kind `docs/studies/enchanter.md` F9 describes. |
| **Recipe taught with swapped inputs** | transfers refused by `canPlaceItem`, worker loops | §8.2 | **Silent.** Nothing brews, nothing is lost, no message. |

---

## 10. Performance

Arithmetic over cited constants, not a measurement.

### 10.1 What ticks

One alchemist per hut, one hut per colony in practice (`(b) -> 1`, `BuildingModules.java:263`).

| Transition | Period | Site |
|---|---|---|
| `waitingForSomething` (AI_BLOCKING) | 5 t | `AbstractEntityAIBasic.java:248` |
| `updateVisualState` (AI_BLOCKING) | 20 t | `:241` |
| `checkIfNeedsItem` (AI_BLOCKING) | 20 t | `:262-264` |
| `rescueIfStuck` (AI_BLOCKING) | 100 t | `:272` |
| `cleanAsync` (AI_BLOCKING) | 200 t | `:266` |
| **`accelerateBrewingStand` (EVENT)** | **20 t** | `EntityAIWorkAlchemist.java:106` |
| `isFuelNeeded` (EVENT) | 200 t | `:105` |
| `inventoryNeedsDump` (STATE_BLOCKING) | 100 t | `AbstractEntityAIBasic.java:255` |
| `shouldRestart` (STATE_BLOCKING) | 20 t | `:283` |
| `START_WORKING → decide` | 5 t | `AbstractEntityAICrafting.java:164` |
| `CRAFT` | 10 t | `:167` |
| the six alchemist states | 20 t each | `EntityAIWorkAlchemist.java:107-112` |

**[VERIFIED]** for every row.

### 10.2 The one thing that scales

**`accelerateBrewingStand`.** Once per 20 ticks it walks the stand list and, for each entry, runs
`2 × floor(Mana/10)` synthetic `BrewingStandBlockEntity.serverTick` calls. Each of those does an
`isBrewable` check that may run up to three `RecipeManager.CachedCheck#getRecipeFor` lookups
(`v/.../BrewingStandBlockEntity.java:191-214`) plus a `setChanged` and a `getPotionBits` comparison.
For `S` list entries at Mana `M`:

```
synthetic ticks/second = S · 2·floor(M/10)
recipe lookups/second  ≤ S · 2·floor(M/10) · 3
```

At `S = 4` physical stands and Mana 99: 72 synthetic ticks and ≤216 lookups per second. Small. **With
§8.4's duplicates on a level-5 hut, `S = 20`: 360 synthetic ticks and ≤1080 lookups per second, from
one colonist.** Still not a disaster, but it is the only figure here that grows without bound, and it
grows every time the hut is repaired.

The checks are `CachedCheck`s, so the lookup cost is a hash probe in the common case; the real cost is
the loop and the `setChanged` block-entity marking. **[UNVERIFIED]** what any of it measures at —
confirming means a profiler on a colony with a maxed alchemist.

### 10.3 The small costs

* **`getAllBrewingStandPositions()`, `getAllSoilPositions()` and `getAllLeavePositions()` each allocate
  a fresh `ArrayList` on every call** (`BuildingAlchemist.java:162-185`) **[VERIFIED]**.
  `accelerateBrewingStand` alone does one per second; `getExtendedCount` does one per input per
  `checkForItems` and three or four more per `fillUpBrewingStand`; the harvest loops copy the whole
  soil or leaf list once per second while active. All of it is garbage that a `Collections.unmodifiableList`
  view would avoid. Trivial per colony; worth knowing on a server with many colonies.
* **`getNetherwartDrops` runs a full loot-table roll on every `HARVEST_NETHERWART` pass**, including
  the passes where `mineBlock` only counts down (§3.1). One roll per second while a worker stands at a
  plant.
* **Pathfinding.** The wander in `decide()` (`:327`) issues a fresh random walk within 10 blocks every
  ~405 ticks. `walkToWorkPos` on the harvest loops issues a path per trip. Two to three paths a minute
  from one citizen. Negligible.

### 10.4 What I would measure first

Stand up a level-5 alchemist hut built through all five levels, read `getAllBrewingStandPositions().size()`,
and compare it against the number of stands actually in the schematic. That single number settles §8.4
and tells you how much of §10.2 is real.

---

## 11. Things that are fine

Whoever picks this area up next need not re-audit these. Each was read end to end and found correct.

* **The state machine wiring.** Seven transitions, sane periods, and every alchemist state declared
  `(true)` in `AIWorkerState.java:647-672` so the worker can always break off to eat or dump. The
  enchanter's uninterruptible-cycle trap (`docs/studies/enchanter.md` F9) has no analogue here.
* **The `EVENT`-as-side-effect trick.** `accelerateBrewingStand` returning `false` unconditionally
  (`:500`) is deliberate and correct: it runs for its effect and never steals a state transition.
  The `ServerLevel` guard the port added above it (`:475-481`) is the right shape and is commented.
* **Self-healing on vanished blocks.** Seven independent call sites drop a stale brewing-stand
  position (`:383, 430, 461, 520, 586, 882, 1059`), and the two harvest loops drop stale soil and leaf
  positions (`:154`, `:259`). Nothing here can NPE on a block the player mined.
* **Bottles before reagent.** `fillUpBrewingStand` loads slots 0–2 first and slot 3 second
  (`:928` then `:998`), which is what `isBrewable` requires
  (`v/.../BrewingStandBlockEntity.java:191-214`). Loading them the other way round would start a brew
  the stand would then abort.
* **`InvWrapper` respects `canPlaceItem`.** `mc/api/inventory/api/InvWrapper.java:56-59` — the port's
  own replacement for NeoForge's wrapper checks the container's own placement rule before inserting,
  which is why §8.2 loses no items.
* **The crafting module is correctly locked down.** `BuildingAlchemist.CraftingModule` returns an empty
  supported-crafting-type set and accepts only `magicpotion` (`:239-252`), so the player cannot teach
  the alchemist arbitrary bench recipes and the module stays hidden until the research lands
  (`AbstractCraftingBuildingModule.java:445`).
* **`improveRecipe` never touches a brewing recipe.** It is only reached from `executeCraftingAction`
  (`AbstractEntityAICrafting.java:646`), which the brewing path does not use — which is fortunate,
  because reducing the three-bottle input by one would break the accounting in two places (§7.2).
* **The display recipes for the two farms.** `getAdditionalRecipesForDisplayPurposesOnly`
  (`BuildingAlchemist.java:255-274`) advertises both the mistletoe and the nether-wart loops in JEI,
  with the right tool and the right intermediate block. Players can discover the farms.
* **The magic potion returns its bottle.** `large_water_bottle` carries `craftRemainder`
  (`ModItemsInitializer.java:263`) and `RecipeStorage` converts that into a secondary output
  automatically (`RecipeStorage.java:381-386`), so no glass is consumed.
* **The composter will not eat the mistletoe by default.** Its allow-list starts empty and it composts
  only what the player selected (`EntityAIWorkComposter.java:422-431`,
  `BuildingModules.java:81-83`).
* **The port's `BrewingCraftingType` rewrite.** Enumerating `BrewingRecipe`s is the right replacement
  for the removed `PotionBrewing`, and `expand()` correctly declines to enumerate a potion predicate
  that constrains individual effects rather than whole potions
  (`BrewingCraftingType.java:91-121`). Its only flaw is the input order it inherited (§8.2).
* **No mixins, and the one access widener is inert.** `minecolonies.mixins.json` lists a single client
  mixin (`PackRepositoryMixin`) unrelated to any of this **[VERIFIED]**. The widener has one
  brewing-related entry, `BrewingStandBlockEntity.brewTime`
  (`26.3/src/main/resources/minecolonies.accesswidener:67`) — and snapshot-10 declares that field
  `public` (`v/.../BrewingStandBlockEntity.java:55`) **[VERIFIED]**, so the entry is at worst
  redundant. **Every vanilla member any fix in this study would need is already public.**
* **No upstream assets.** The alchemist's blueprints, GUI layouts, citizen textures and language keys
  are all runtime-fetched; `26.3/src/main/resources/assets/minecolonies/` contains only the port's own
  `lang/`, two item models, two textures and `sounds.json` **[VERIFIED]**. Nothing proposed here
  requires an upstream asset.

---

## 12. What a player notices first

Ranked by how quickly it turns into a complaint, which is not the same as §8's ranking.

1. **"My alchemist doesn't do anything and won't say why."** Any of §8.1, §8.2, or a full rack — all
   three present identically: a citizen standing at a brewing stand with the status "Working" and no
   chat bubble.
2. **"His nether wart farm produces nothing."** Two plants a day, half of them a break-even harvest at
   age 2. §8.3 plus §7.3.
3. **"He walks home after every single mistletoe."** §8.5. Anyone who watches him for two minutes sees
   it.
4. **"The druids ran out of potions immediately."** §7.3 — a day of production is three to six seconds
   of one druid's combat.
5. **"Panoramix costs sixty-four mistletoe?"** Forty game days of a dedicated worker to unlock the
   thing that gives the worker a purpose. §6.
6. **"He only uses one of the four brewing stands."** Dexterity under 10. §4.4. This one reads as a bug
   and is not.
7. **"He downed tools because it started raining."** Indoors, at a brewing stand. Shared with every
   other crafter, and switchable with `technology/rainman`.
8. **"There are two hundred potions in the warehouse and nothing wants them."** §5.2 — and with no
   min-stock module, no way to tell him to stop.

---

## 13. What I could not verify

1. **Anything requiring a running server.** Nothing was executed. Every figure is a source reading or
   arithmetic over cited constants. In particular:
   * the mistletoe and nether-wart rates (§3.3, §7.3) — the probabilities and the drop tables are
     verified, the trip durations are estimates;
   * the worker's real brewing-stand service rate (§7.3), which is what actually caps throughput;
   * where in the 1.0×–1.9× band the acceleration lands once `setDelay` suppression is accounted for
     (§4.3);
   * whether §8.6's fuel-request block ever bites in practice.
2. **The schematic.** How many brewing stands, soul sand blocks and leaf blocks each hut level
   contains is decided by the runtime-fetched blueprints, which are not in this repository. Everything
   in §7.1's throughput reasoning that depends on the stand count is therefore bounded, not fixed.
   Settling it means placing a level-1 and a level-5 alchemist hut and counting.
3. **Whether leaf positions decay away.** `harvestMistleToe` drops any registered position that is no
   longer a `LeavesBlock` (`:257-262`). If the schematic's leaves are not `persistent`, the mistletoe
   grove would thin out over time and the loop would quietly die. The blueprints would settle it.
4. **How long an unfillable `Stack` request stays open**, which decides §8.6.
5. **The client half.** The brewing teaching GUI, the container and the JEI category were read for
   structure and slot indices only. The repository's own README states nothing about rendering or
   input has been exercised in play on this branch, and this audit did not change that. The one
   client-side claim made here — that the teaching GUI writes `[bottle, reagent]` — is a source
   reading of `WindowBrewingstandCrafting.java:120-125`, not an observation.
6. **Free mode.** §8.9's missing supply hook is verified as absent from the override; whether free-mode
   brewing actually stalls because of it is not.
7. **Modded brewing.** No third-party brewing recipe or fuel was considered beyond noting that §8.7
   would not see one.

---

## 14. Fix sizes at a glance

| § | Finding | Size | Balance? | Ours or upstream's? | Mixin / widener? |
|---|---|---|---|---|---|
| 8.1 | "No brewing stand" message discarded before it is shown | S (~10 + 1 lang key) | No | Upstream's | Neither |
| 8.2 | `teachRecipes` teaches brewing recipes with inputs swapped | S (2 lines) | No | Ordering upstream's, **consequence ours** | Neither |
| 8.3 | Nether wart harvested at age 2 instead of 3 | S (1 token) | **Yes** (≈3× the farm) | Upstream's | Neither |
| 8.4 | Position lists duplicated on every build | S (~10) | **Yes** (removes an accidental speed bonus) | Upstream's | Neither |
| 8.5 | A full dump trip per mistletoe | S (2 lines) | **Yes** (raises the mistletoe rate) | Upstream's | Neither |
| 8.6 | Any open `Stack` request blocks the fuel request | S (~5) | No | Upstream's | Neither |
| 8.7 | Brewing fuel hardcoded; 26.3 made it a data component | S (~20) | No (against the vanilla pack) | Hardcode upstream's, **the miss is ours** | Neither |
| 8.8 | `RETRIEVING_USED_FUEL_FROM_BREWINGSTAND` unreachable | S (−30) | No | Upstream's | Neither |
| 8.9 | Reagent gather target 3× too large | S (1 line) | No | Upstream's | Neither |
| 8.9 | `checkForItems` cancels then returns `CRAFT` | S (1 line) | No | Upstream's | Neither |
| 8.9 | Free-mode supply hook missing from the override | S (~5) | No | **Ours** | Neither |
| 8.9 | Statistic rolled from a second, different loot draw | S (~5) | No | Upstream's | Neither |
| 8.9 | Druid potion render flag tests `Items.POTION` | S (1 line) | No | Upstream's | Neither |
| 8.9 | No `build_alchemist` advancement | S (~10) | No | Upstream's | Neither |
| 8.9 | `getPositionOfBrewingStandToRetrieveFrom` lacks a loaded-chunk guard | S (2 lines) | No | Upstream's | Neither |
| §1 | No min-stock module on the alchemist | S (1 line in the registration) | Behaviour | Upstream's | Neither |
| §5.2 | Nothing in the colony consumes a brewed potion | M | **Yes** | Upstream's design | Neither |

Two items would grow past S if taken further than described. Giving the colony a use for brewed
potions — a healer that consumes regeneration, guards that drink strength before a raid — is **M** and
needs new AI, not datagen. Making the alchemist self-sufficient enough to be worth a citizen on its own
— a real nether-wart plot cycle, a mistletoe rate that does not walk home between items, and a
mistletoe sink beyond one recipe — is **M** as well, and every part of it is a balance change.

**No proposal in this study needs a mixin.** Every site named is a method in the mod's own tree, and
every vanilla member any of them touches — `NetherWartBlock.MAX_AGE`, `DataComponents.BREWING_FUEL`,
`BrewingStandBlockEntity.canPlaceItem`, `BrewingStandBlockEntity.serverTick` — is already `public` and
already called from mod code today. **No upstream MineColonies asset is required by any of them.**
