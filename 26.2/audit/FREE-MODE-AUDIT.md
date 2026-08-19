# Free-mode audit — what still stalls, and where the switch should live

Research report. **No code was changed.** All paths are relative to `26.2/` unless
stated otherwise; every claim carries a file and a line number, verified against
`294f469f` ("Merge version/main after PR #5 was merged").

Two questions are answered:

1. **Where does a worker still stall on something the four toggles do not cover?**
   The target set by the user is *everything works without anything*, crafters
   explicitly included. So the survey below is not a list of "plausible gaps" —
   it is the **checklist** the switch has to satisfy.
2. **Where should the switch live?** The user has picked a direction: one global
   switch, driven by a command, no per-hut checkboxes. Section 3 designs exactly
   that and costs out the deletion.

---

## 0. How the existing mechanism works (established facts)

| Fact | Evidence |
|---|---|
| Four setting keys exist | `core/colony/buildings/AbstractBuilding.java:122` (`NO_TOOL_REQUESTS`), `:130` (`WORK_WITHOUT_MATERIALS`), `:138` (`WORK_WITHOUT_FOOD`); `core/colony/buildings/workerbuildings/BuildingBuilder.java:51` (`BUILD_WITHOUT_RESOURCES`) |
| Four accessors read them | `AbstractBuilding.java:1239`, `:1253`, `:1263`; `core/colony/buildings/AbstractBuildingStructureBuilder.java:448` |
| A hut only ever reads its **first** settings module | `AbstractBuilding.java:1220-1228` — both `getSetting` and `getSettingValueOrDefault` route through `getFirstModuleOccurance(ISettingsModule.class)` |
| A missing *key* is tolerated, a missing *module* is not | `core/colony/buildings/modules/SettingsModule.java:117-121` returns `def` for an absent key; `hasModule(ISettingsModule.class)` is checked first in all four accessors |
| Synchronous tool requests funnel through one method | `core/entity/ai/workers/AbstractEntityAIBasic.java:980-1013` (`checkForToolOrWeapon`), async twin at `:1022-1064` |
| Stack/deliverable/tag requests funnel through three methods | `AbstractEntityAIBasic.java:1755` (all five `checkIfRequestForItemExistOrCreate*` stack overloads delegate here), `:1816` (`IDeliverable`), `:1861` (tag). Free-material branches at `:1781`, `:1836`, `:1878` |
| The free-material helper | `AbstractEntityAIBasic.java:1419` `supplyMaterialWithoutRequest` |
| Everything is server-side | `AbstractBuilding` is the server class; the client holds `AbstractBuildingView`. `CitizenColonyHandler.java:65-68` resolves the work building through `CitizenData` (server) — so `instanceof AbstractBuilding` in e.g. `core/entity/other/NewBobberEntity.java:250` can only be true on the server. **No client sync is needed for any of the four behaviours.** |

### Coverage today, per hut

Derived mechanically from `apiimp/initializer/ModBuildingsInitializer.java` and
`core/colony/buildings/modules/BuildingModules.java`.

| Settings module producer | Decl. | tool | material | food | Huts using it |
|---|---|---|---|---|---|
| `SETTINGS_CRAFTER_RECIPE` | `BuildingModules.java:53-56` | ✔ | – | – | bakery, blacksmith, sawmill, stoneMason, stoneSmelter, glassblower, dyer, fletcher, mechanic, concreteMixer |
| `TOOL_SETTINGS` | `:62-64` | ✔ | – | – | archery, combatAcademy, fisherman, sifter, graveyard, kitchen |
| `MATERIAL_SETTINGS` | `:73-76` | ✔ | ✔ | – | library, florist, enchanter, hospital, alchemist |
| `COOK_SETTINGS` | `:82-84` | – | – | ✔ | cook (dining hall) |
| `BUILDER_SETTINGS` | `:486-491` | ✔ | – | – | builder (+ `BUILD_WITHOUT_RESOURCES`) |
| `FARMER_SETTINGS`, `PLANTATION_SETTINGS`, `BEEKEEPER_SETTINGS`, `CHICKENHERDER_SETTINGS_BREEDING`, `COWHERDER_SETTINGS`, `STABLE_SETTINGS`, `RABBITHERDER_SETTINGS`, `SHEPERD_SETTINGS`, `SWINEHERDER_SETTINGS`, `CRUSHER_SETTINGS`, `MINER_SETTINGS`, `GUARD_SETTINGS`, `GATE_GUARD_SETTINGS`, `NETHERWORKER_SETTINGS` | various | ✔ | ✔ | – | one hut each |
| `COMPOSTER_SETTINGS` | `:143-149` | – | ✔ | – | composter |
| `FORESTER_SETTINGS` | `:398-406` | ✔ | – | – | lumberjack |
| **`SMELTER_SETTINGS`** | **`:412-413`** | **–** | **–** | **–** | **smeltery — carries only `BuildingSmeltery.MIN`** |
| `TOWNHALL_SETTINGS` | `:575-583` | – | – | – | townHall (unrelated settings) |

**Huts with no settings module at all**: `barracks`, `deliveryman`, `home`,
`wareHouse`, `postBox`, `university`, `stash`, `school`, `tavern`,
`mysticalSite`, `simpleQuarry`, `mediumQuarry`
(`ModBuildingsInitializer.java:446`, `:474`, `:638`, `:645` and siblings).

Of those, four have a worker that can stall: **school** (teacher, pupil),
**university** (researcher — see §1.6), **simpleQuarry** and **mediumQuarry**
(quarrier).

---

## 1. Question 1 — the gap checklist

Ordered by how much of the colony each gap freezes.

### 1.1 Crafting recipe inputs — the single biggest hole (CONFIRMED)

**The claim on record is correct in its facts and wrong in its conclusion.**
`COMMANDS.md:118-120` says recipe inputs "are requested by the request system
when the crafting task is created, not by the worker, so no hut setting can make
a sawmill craft without planks". The first half is exactly right. The second half
is false: the place where those requests are created **has the building in
scope**, so it is reachable — just not from `AbstractEntityAIBasic`.

**Where the inputs are requested.** The chain for "someone wants planks":

| Step | Code | What happens |
|---|---|---|
| 1 | `core/colony/requestsystem/resolvers/core/AbstractCraftingRequestResolver.java:132-178` | `canResolveForBuilding` — sawmill claims the request if it holds a matching recipe, has an assigned worker (`:187-198`) and level > 0 (`:134`) |
| 2 | `AbstractCraftingRequestResolver.java:285-303` → `:315-366` | `createRequestsForRecipe` creates one or more **`PublicCrafting`/`PrivateCrafting`** child requests (`:360`) — it splits by inventory size, it does **not** request materials |
| 3 | `core/colony/requestsystem/resolvers/core/AbstractCraftingProductionResolver.java:115-147` | `attemptResolveForBuildingAndStack`. `:134-138` — if `getFirstFulfillableRecipe(...)` finds the ingredients already in the building, it returns `ImmutableList.of()` (no children, immediately resolvable). Otherwise → step 4 |
| 4 | **`AbstractCraftingProductionResolver.java:155-192`** | **`createRequestsForRecipe` — this is where the recipe inputs are requested**, one `Stack` per ingredient via `createNewRequestForStack` at `:195-199` → `manager.createRequest(this, stackRequest)` |
| 5 | warehouse / other crafters / `StandardPlayerRequestResolver.java:73-94` | The ingredient requests are resolved by whoever can. The player resolver accepts **everything** as last resort (`:75` returns true unconditionally), so an unsatisfiable ingredient request never fails — it **hangs forever** in the player-request list |
| 6 | `resolvers/PublicWorkerCraftingProductionResolver.java:200-223` | Only once every child is done does the crafting request get handed to the crafter's job queue |

**Where the worker gives up.** Even if you got the task to the worker, the AI
checks the building again and *fails the request* rather than asking:

- `core/entity/ai/workers/crafting/AbstractEntityAICrafting.java:398-409` —
  `getRecipe()`: `if (availableCount < remaining) { currentRecipeStorage = null;
  job.finishRequest(false); … return START_WORKING; }`
- `AbstractEntityAICrafting.java:491-502` — `checkForItems()`: if the ingredient
  is not in the worker and not in the building, `return GET_RECIPE` with the
  request dropped.

So there are **two** gates, and both read the **building's** inventory:
`AbstractCraftingBuildingModule.java:768-793` (`getFirstFulfillableRecipe` →
`RecipeStorage.canFullFillRecipe`, `api/crafting/RecipeStorage.java:476-494`,
which sums `citizen handlers + InventoryUtils.getCountFromBuilding(building, …)`)
and `AbstractEntityAICrafting.java:398-401` (same sum, inline).

**What it takes to close it.** One insertion point covers both gates, because
both read the building inventory:

> In `AbstractCraftingProductionResolver#createRequestsForRecipe`
> (`:155-192`) — which already receives `@NotNull final AbstractBuilding building`
> as its second parameter — when free mode is on, insert each
> `storage.getCleanedInput()` ingredient into the building
> (`InventoryUtils.addItemStackToProvider`, `api/util/InventoryUtils.java:1056`)
> at `ingredient.getAmount() * count`, then `return ImmutableList.of()`.

An empty child list means "resolvable now, nothing to wait for". Then:

- `resolveForBuilding` re-runs `getFirstFulfillableRecipe`
  (`AbstractCraftingProductionResolver.java:247`) — now true, because the items
  are physically in the hut's racks.
- Private crafters (`PrivateWorkerCraftingProductionResolver.java:65-89`) call
  `module.fullFillRecipe(storage)` which consumes from `building.getHandlers()`
  (`AbstractCraftingBuildingModule.java:796-798`) — satisfied.
- Public crafters hand the task to the worker
  (`PublicWorkerCraftingProductionResolver.java:200-223`), and the worker's own
  two gates (`AbstractEntityAICrafting.java:398`, `:491`) also read the building
  — satisfied, and `checkForItems` walks the worker to the racks
  (`GATHERING_REQUIRED_MATERIALS`) exactly as it would with real materials.

**Cost:** ~12 lines in **one** file
(`core/colony/requestsystem/resolvers/core/AbstractCraftingProductionResolver.java`),
plus the flag lookup. This single change covers **every** crafter — bakery,
sawmill, blacksmith, stonemason, fletcher, mechanic, dyer, glassblower,
stone smeltery, concrete mixer, chef's kitchen, crusher, sifter, alchemist,
farmer/planter/lumberjack crafting modules, and the enchanter.

Two secondary conditions the switch does **not** remove and should not try to:

- The crafter must have been **taught the recipe** — `getFirstRecipe` at
  `AbstractCraftingRequestResolver.java:149`. Already covered by the existing
  `/mc colony teachRecipes` command (`COMMANDS.md:50`).
- The hut must be **level ≥ 1** with an **assigned worker** —
  `AbstractCraftingRequestResolver.java:134-139`. Covered by
  `BUILD_WITHOUT_RESOURCES` + `/mc citizens fill`.

**Also worth doing (defensive, ~8 lines):** the same free-mode branch in
`AbstractEntityAICrafting#getRecipe` at `:403` and `#checkForItems` at `:491`,
supplying into the *worker* inventory. Requests created before the switch was
turned on, or tasks queued by hand, would otherwise still hit the old fail path.

### 1.2 Fuel — furnaces and brewing stands (CONFIRMED, four separate sites)

None of these go through `checkIfRequestForItemExistOrCreate`, so
`WORK_WITHOUT_MATERIALS` cannot reach any of them today.

| Hut(s) | Site | Behaviour with no fuel |
|---|---|---|
| bakery, glassblower, stone smeltery, dyer, chef's kitchen (`AbstractEntityAIRequestSmelter` subclasses) | `core/entity/ai/workers/crafting/AbstractEntityAIRequestSmelter.java:105-117` | Raw `createRequestAsync(new StackList(possibleFuels, …))` at `:113`, bypassing the chokepoint |
| same | `AbstractEntityAIRequestSmelter.java:180-193` | `addFuelToFurnace`: no fuel in worker, none in hut → `:191-192` `furnacePos = null; return START_WORKING;` |
| same | `AbstractEntityAIRequestSmelter.java:479-501` | `executeCraftingAction`: with no fuel it never reaches `ADD_FUEL_TO_FURNACE` (`:484`) nor `FILL_UP_FURNACES` (`:498`) and falls out at `:501` `return START_WORKING` — **an idle spin** |
| smeltery, dining hall (`AbstractEntityAIUsesFurnace` subclasses) | `core/entity/ai/workers/AbstractEntityAIUsesFurnace.java:243-248` | Raw `createRequestAsync(new StackList(getAllowedFuel(), …))` |
| alchemist | `core/entity/ai/workers/crafting/EntityAIWorkAlchemist.java:538-544` | Raw `createRequestAsync(new Stack(BLAZE_POWDER …))` at `:542`; and at `:564-565` `//We need to wait for Fuel to arrive` → `return getState()` — **a hard stall** |

**Coverage needed:** four supply points. Each is 3-6 lines: when free mode is on,
put a stack of the first allowed fuel (`getAllowedFuel()`,
`AbstractEntityAIUsesFurnace.java:269-276` / `getActivePossibleFuels()`) or
`Items.BLAZE_POWDER` into the worker inventory instead of filing the request.
Alternatively — and much cheaper — a single hook that fills the hut's fuel
supply, since every one of these paths falls back to
`InventoryUtils.hasBuildingEnoughElseCount(building, …)` first.

### 1.3 The smeltery has a settings module but neither key (CONFIRMED)

`BuildingModules.java:412-413`:

```
SMELTER_SETTINGS = new BuildingEntry.ModuleProducer<>("smelter_settings",
  () -> new SettingsModule().with(BuildingSmeltery.MIN, new IntSetting(0)), …);
```

Registered at `ModBuildingsInitializer.java:351`. Consequences for
`EntityAIWorkSmelter`:

- ore: `core/entity/ai/workers/crafting/EntityAIWorkSmelter.java:197-224` —
  raw `createRequestAsync(getSmeltAbleClass())` at `:207` /
  `new StackList(...)`, plus a `FURNACE_USER_NO_ORE` blocking interaction at
  `:220-221`. Not on the chokepoint, and the hut has no key anyway.
- fuel: `AbstractEntityAIUsesFurnace.java:243-248` (see §1.2).
- tools: the smelter needs none, so `NO_TOOL_REQUESTS` would be a no-op — but
  its absence is still an inconsistency with the other 38 huts.

### 1.4 Huts with no settings module whose worker stalls (CONFIRMED)

| Hut | Worker | Stall | Line |
|---|---|---|---|
| `simpleQuarry`, `mediumQuarry` | quarrier | Mines through `mineBlock` → `holdEfficientTool` → `requestTool`; `worksWithoutTools()` is false because `hasModule(ISettingsModule.class)` is false | `core/entity/ai/workers/production/EntityAIQuarrier.java:587`, gate at `AbstractEntityAIBasic.java:1367` |
| same | quarrier | Placement resources: `buildsWithoutResources()` is false for the same reason, so `hasListOfResInInvOrRequest` still requests | `AbstractBuildingStructureBuilder.java:448-451`, `core/entity/ai/workers/AbstractEntityAIStructure.java:763-767` |
| `school` | teacher | Raw paper request, bypasses the chokepoint | `core/entity/ai/workers/education/EntityAIWorkTeacher.java:213-216`, decided at `:92-95` |
| `school` | pupil | `PUPIL_NO_CARPET` blocking interaction — structural, not an item | `core/entity/ai/workers/education/EntityAIWorkPupil.java:97` |

**Note on the teacher:** it files a request that nothing may ever satisfy, but it
does **not** stall — `teach()` at `EntityAIWorkTeacher.java:152-161` only hands
paper over if it happens to have some, and the XP at `:163-165` is granted either
way. So this is a *leaked open request*, not a freeze. Worth covering for
tidiness, not urgency.

### 1.5 Requests that bypass the chokepoint on huts that DO have the key (CONFIRMED)

These huts already carry `WORK_WITHOUT_MATERIALS`, but the specific request is
not made through `checkIfRequestForItemExistOrCreate*`, so the toggle misses it.

| Hut | Site | Item | Stalls? |
|---|---|---|---|
| stable | `core/entity/ai/workers/production/herders/EntityAIWorkStablemaster.java:496-500` | saddle / horse armour, raw `createRequestAsync(new StackList(...))` | No — falls through, the horse simply is not readied |
| alchemist | `EntityAIWorkAlchemist.java:542` | blaze powder | **Yes**, `:565` |
| farm | `core/entity/ai/workers/production/agriculture/EntityAIWorkFarmer.java:226-234` | fertilizer, raw `createRequestAsync(new StackList(compostAbleItems, …))` at `:233` | No — the `else if` at `:236` means it keeps farming unfertilised |
| composter | `core/entity/ai/workers/production/agriculture/EntityAIWorkComposter.java:189-207` | compostables, raw `createRequestAsync` at `:200-205` | **Already handled** by an explicit gate at `:173-185` |

### 1.6 Non-item stalls the switch would have to cover separately

These are not "missing materials", so a material toggle can never reach them.
Listed for completeness because "everything works without anything" implies them.

| Hut | Blocked on | Line |
|---|---|---|
| mine | Hut level / mineshaft depth: `NEEDS_BETTER_HUT` blocking interaction, `return IDLE` | `core/entity/ai/workers/production/EntityAIStructureMiner.java:437-445` |
| any digger | Hut equipment level too low for the block's harvest level → `REQUEST_SYSTEM_BUILDING_LEVEL_TOO_LOW` | `AbstractEntityAIBasic.java:1447-1455`; validator at `apiimp/initializer/InteractionValidatorInitializer.java:115-126` |
| sifter | **Mesh.** The sifter never requests one — no `checkIfRequestForItemExistOrCreate` call exists in the class. `SIFTER_NO_MESH` interaction, then `progress = 0; return START_WORKING` | `core/entity/ai/workers/crafting/EntityAIWorkSifter.java:130-157` (interaction at `:142`, spin at `:156`) |
| courier | **Warehouse.** `checkIfExecute` returns false, `setWorking(false)`, `COM_MINECOLONIES_COREMOD_JOB_DELIVERYMAN_NOWAREHOUSE` | `core/entity/ai/workers/service/EntityAIWorkDeliveryman.java:660-668` |
| enchanter | Needs another worker with levels to drain — `NO_WORKERS_TO_DRAIN_SET` | `core/entity/ai/workers/service/EntityAIWorkEnchanter.java:150-153` |
| apiary | Hives / bees / flowers in world | `core/entity/ai/workers/production/agriculture/EntityAIWorkBeekeeper.java:209`, `:217`, `:239` |
| flower shop | Plantable ground | `core/entity/ai/workers/production/agriculture/EntityAIWorkFlorist.java:157` |
| farm, plantation | Free fields | `EntityAIWorkFarmer.java:246`, `core/entity/ai/workers/production/agriculture/EntityAIWorkPlanter.java:120` |
| bakery/glassblower/… | No furnaces tagged in the hut | `AbstractEntityAIRequestSmelter.java:441-450` |
| quarry | No quarry / quarry finished | `EntityAIQuarrier.java:113`, `:120`, `:474`, `:479` |
| university | Research start cost — items must be in the warehouse. Already covered by `/mc colony research completeall` (`COMMANDS.md:41`) | — |

**Recommendation:** treat this table as explicitly **out of scope for the item
switch**. These are world state and hut level, not materials; `/mc citizens fill`,
`/mc colony research completeall` and `BUILD_WITHOUT_RESOURCES` (which lets a
builder raise any hut to level 5 for free) already cover the reachable ones.

### 1.7 Toggle is on, worker still cannot do the job (CONFIRMED — a correctness gap, not a stall)

`NO_TOOL_REQUESTS` makes `checkForToolOrWeapon` answer "no tool is missing"
(`AbstractEntityAIBasic.java:980-990`), which unblocks `AbstractEntityAIFight#prepare`
at `core/entity/ai/workers/guard/AbstractEntityAIFight.java:118-125`. But the
combat AIs then refuse to attack because they look the weapon up themselves:

- `core/entity/ai/workers/guard/MeleeCombatAI.java:160-178` —
  `canAttack()` returns `false` when
  `InventoryUtils.getFirstSlotOfItemHandlerContainingEquipment(...) == -1`.
- `core/entity/ai/workers/guard/RangeCombatAI.java:118-134` — same shape.

So a guard with the toggle on **does not stall in `PREPARING`, but never swings
either**. `COMMANDS.md:86-87` ("Guards fight bare-handed, which is weaker but
beats standing idle") is inaccurate — they do not fight at all.

Closing it needs a free-mode branch in those two `canAttack()` methods (return
true and clear the held item), ~4 lines each. The archer *training* case was
already handled this way at
`core/entity/ai/workers/guard/training/EntityAIArcherTraining.java:230`; the
combat academy path (`EntityAICombatTraining.java:333-351`) is fine because its
`attackDummy`/`attack` only `swing()` (`:211`, `:316`).

Arrows are **not** a gap: they are a damage bonus only
(`RangeCombatAI.java:280-301`, gated on the `ARCHER_USE_ARROWS` research).
Armour is **not** a gap: absent armour only lowers survivability
(`AbstractEntityAIFight.java:182-193`).

### 1.8 Confirmed non-gaps (checked, nothing to do)

| Claim | Verdict |
|---|---|
| Builder beyond `BUILD_WITHOUT_RESOURCES` | **Clean.** Tools covered — the builder's hut carries `NO_TOOL_REQUESTS` (`BuildingModules.java:490`). Materials covered at four points: `AbstractBuildingStructureBuilder.java:432`, `AbstractEntityAIStructure.java:763`, `AbstractEntityAIStructureWithWorkOrder.java:203` and `:241`, `core/entity/ai/workers/util/BuildingStructureHandler.java:249` and `:274`. Its own hut level is not gated. No fuel. |
| Courier | **Nothing to conjure.** It never requests an item; the only blocker is the warehouse (§1.6). It is however a *systemic* dependency: every `Delivery` request needs a courier, so `/mc citizens fill` staffing one is a precondition for the crafting chain regardless of the switch. |
| Miner | Cobble and ladders both go through the chokepoint (`EntityAIStructureMiner.java:369`, `:383`, `:486-487`, `:787`) and the hut has `WORK_WITHOUT_MATERIALS` (`BuildingModules.java:333-336`). |
| Nether miner | Trip kit via chokepoint (`core/entity/ai/workers/production/EntityAIWorkNether.java:236`); tools via `checkForToolOrWeapon` (`:259-263`); food is optional (`:955` returns `getState()`). Hut has both keys. |
| Student (library) | Study items go through the `IDeliverable` chokepoint (`core/entity/ai/workers/education/EntityAIStudy.java:158` → `AbstractEntityAIBasic.java:1836`); and the student levels up anyway at `EntityAIStudy.java:161`. |
| Healer | Covered by the new gate at `core/entity/ai/workers/service/EntityAIWorkHealer.java:280-286`. |
| Herders | Breeding and extra items via chokepoint (`core/entity/ai/workers/production/herders/AbstractEntityAIHerder.java:321`, `:326`, `:756`); bare-handed butchering/shearing handled at `:715` and `:814`. |
| Cook serving food | Covered — `core/entity/ai/workers/service/EntityAIWorkCook.java:391`, `core/colony/buildings/modules/RestaurantMenuModule.java:87`, `core/entity/ai/minimal/EntityAIEatTask.java:268`. **But** it requires a built dining hall to exist: `EntityAIEatTask.java:244-247` returns `SEARCH_RESTAURANT` when `restaurantPos == null`, and the cook's own smelting still needs fuel + raw food (§1.2). |

### 1.9 The checklist, condensed

What a "works without anything" switch has to do, in priority order:

| # | Behaviour | Files to touch | Approx. lines |
|---|---|---|---|
| 1 | **Crafting recipe inputs** — inject ingredients into the hut at resolve time | `AbstractCraftingProductionResolver.java` | ~12 |
| 1b | same, defensively, worker-side | `AbstractEntityAICrafting.java` | ~8 |
| 2 | **Furnace fuel** (2 sites) | `AbstractEntityAIRequestSmelter.java`, `AbstractEntityAIUsesFurnace.java` | ~10 |
| 3 | **Brewing fuel** | `EntityAIWorkAlchemist.java` | ~5 |
| 4 | **Smeltable ore** | `EntityAIWorkSmelter.java` | ~6 |
| 5 | **Sifter mesh** | `EntityAIWorkSifter.java` | ~6 |
| 6 | **Guards actually swing** | `MeleeCombatAI.java`, `RangeCombatAI.java` | ~8 |
| 7 | Stablemaster tack, teacher paper, farmer fertilizer (leaks, not stalls) | 3 files | ~12 |
| 8 | Quarry + smeltery + school gain coverage | **free** under a global switch — they need no settings module at all | 0 |

Item 8 is the whole argument for consolidation: under the per-hut design those
three huts need new module producers and new registration lines; under a global
switch they are covered the moment the accessors stop reading settings.

---

## 2. Question 2 — the recommended design

The direction is set: **one global switch, driven by a command, per-hut
checkboxes superseded.** What follows is a single concrete design, not a menu.
Where a decision could reasonably have gone either way it is called out and one
option is recommended.

### 2.1 Design at a glance

```
/mc colony freemode <colony> <on|off>          ← the only user-facing surface
        │
        ▼
Colony.freeMode  (boolean field, colony NBT)   ← the only stored state
        │
        ▼
FreeMode.isOn(IColony)                          ← the only read point
        │
        ├── AbstractBuilding#worksWithoutTools()        (rewritten, 1 line)
        ├── AbstractBuilding#worksWithoutMaterials()    (rewritten, 1 line)
        ├── AbstractBuilding#worksWithoutFood()         (rewritten, 1 line)
        ├── AbstractBuildingStructureBuilder#buildsWithoutResources()  (1 line)
        └── the new crafter/fuel paths from §1.9        (read FreeMode directly)
```

Everything the feature owns lives behind the single symbol `FreeMode`.
`grep -rn FreeMode src/main/java` is a complete inventory of the feature.

### 2.2 Where the flag lives, and how it persists

**Recommendation: a boolean field on `Colony`, per-colony, saved in colony NBT.**

The exact template already exists — `canColonyBeAutoDeleted`:

| Concern | Template |
|---|---|
| Field | `core/colony/Colony.java` (declared alongside `canColonyBeAutoDeleted`) |
| Load | `Colony.java:843-846` |
| Save | `Colony.java:1006` |
| Getter | `Colony.java:1398-1401` |
| Setter (`markDirty()`) | `Colony.java:1509-1513` |

**Do not add it to `IColony`.** `canBeAutoDeleted` is on `IColony`
(`api/colony/IColony.java:153`, `:344`), which forces a stub in `ColonyView`
(`core/colony/ColonyView.java:468-471`, `:1087-1091`). That stub exists only
because the interface demands it. Since free mode is read exclusively on the
server (§0, last row), putting the field on `Colony` alone and having
`FreeMode.isOn` do `colony instanceof Colony c && c.isFreeMode()` keeps two files
(`IColony.java`, `ColonyView.java`) untouched — **one fewer interface to unpick
when deleting**.

**Persistence across reload:** yes, via the two NBT lines above. Colony NBT is
written on world save and read on load, same as the auto-delete flag.

**Client sync:** **not needed.** All four behaviours and every new path in §1.9
run on the server:

- `AbstractBuilding` is server-only; the client sees `AbstractBuildingView`.
- `CitizenColonyHandler.java:65-68` resolves the work building through server
  `CitizenData`, so `NewBobberEntity.java:250`'s
  `instanceof AbstractBuilding` is false client-side by construction.
- The request resolvers all early-out on
  `manager.getColony().getWorld().isClientSide()`
  (`AbstractCraftingProductionResolver.java:73`, `:84`,
  `PublicWorkerCraftingProductionResolver.java:202`).
- `RestaurantMenuModule` and `SettingsModule` are server modules.

That is a real saving: no packet, no `serializeToView`, no `ColonyView` field, no
`AbstractBuildingView` plumbing, and nothing to unwire on deletion.

### 2.3 Per-colony or server-wide?

**Recommendation: per-colony.**

| | Per-colony (`Colony` field) | Server-wide (`ServerConfiguration`) |
|---|---|---|
| Turn on from in-game | ✔ one command | ✘ edit `minecolonies-server.toml`, needs a config reload |
| Test a "normal" colony beside a "free" one | ✔ | ✘ |
| Persists across reload | ✔ NBT | ✔ toml |
| Files to add | `Colony.java` (+5 lines) | `api/configuration/ServerConfiguration.java` (+2 lines: field decl ~line 33, `defineBoolean` ~line 138) |
| Deletion cost | 5 lines in 1 file | 2 lines in 1 file |
| Synced to client | not needed | automatic (`ServerConfiguration` javadoc: "Loaded serverside, synced on connection") — but unused, so irrelevant |

Server config is marginally cheaper to delete (2 lines vs 5) but is materially
worse to use: the whole point of the feature is toggling behaviour mid-session
while watching a colony. A 3-line difference in deletion cost does not buy that
back. **If you want both**, the cheap compromise is: keep the per-colony field as
the authority and add nothing else — `/mc colony freemode <colony> on` run on
each colony is a 5-second operation, and `<colony>` already accepts `here`,
`mine` or a player name (`COMMANDS.md:5-6`).

### 2.4 The command

**Recommendation: `/mc colony freemode <colony> <on|off>`**, with a bare
`/mc colony freemode <colony>` reporting the current state.

- Name: `freemode` over `freebies` — it reads as a mode, not as a gift, and it
  matches the `FreeMode` symbol so grepping finds the command too.
- Placement: `core/commands/colonycommands/CommandColonyFreeMode.java`,
  registered with one line in `core/commands/EntryPoint.java:64` next to
  `new CommandColonyTeachRecipes().build()` (which puts it under both `/minecolonies`
  and `/mc`, `EntryPoint.java:41` and `:110`).
- Permission: `IMCOPCommand`, same as `CommandColonyTeachRecipes.java:44`.
- Arguments: `ColonyIdArgument.getColony(context, COLONYID_ARG)` +
  `BoolArgumentType` (or a literal `on`/`off` pair). Both idioms are already in
  the tree — `CommandSetDeletable.java` is the closest model, since it does
  exactly "colony + boolean → setter".
- Lang keys needed (2, in `manual_en_us.json` — **not edited by this report**):
  `com.minecolonies.command.colony.freemode.success` and `…​.state`.

**Optional, one extra line:** have the command also print how many buildings and
citizens the colony has, so the operator gets confirmation the right colony was
hit. `CommandColonyDiagnose` already does this kind of reporting.

### 2.5 How everything reads it — one early return in front of everything

New file, ~30 lines:
`core/debug/FreeMode.java` (next to the existing `core/debug/command/CommandToggleDebug.java`,
so the whole feature sits in the package already reserved for debug scaffolding).

```
public final class FreeMode
{
    public static boolean isOn(@Nullable final IColony colony)   // the ONE read point
    public static boolean isOn(@Nullable final IBuilding building)  // convenience
}
```

Then the four existing accessors collapse from a module lookup to a delegation:

| Accessor | Today | After |
|---|---|---|
| `AbstractBuilding.java:1239` `worksWithoutTools()` | `hasModule(ISettingsModule.class) && getSettingValueOrDefault(NO_TOOL_REQUESTS, false)` | `FreeMode.isOn(colony)` |
| `AbstractBuilding.java:1253` `worksWithoutMaterials()` | ditto | `FreeMode.isOn(colony)` |
| `AbstractBuilding.java:1263` `worksWithoutFood()` | ditto | `FreeMode.isOn(colony)` |
| `AbstractBuildingStructureBuilder.java:448` `buildsWithoutResources()` | ditto | `FreeMode.isOn(colony)` |

`colony` is available in all four — `AbstractSchematicProvider.java:46` declares
`protected final IColony colony` and every building extends it.

**Keep the four accessor names.** They are already the single funnel for 38 call
sites (`worksWithoutTools` 15, `worksWithoutMaterials` 10, `worksWithoutFood` 4,
`buildsWithoutResources` 9 occurrences including declarations). Renaming them
would touch 17 files for no gain; leaving them means the *behaviour* half of the
feature does not move at all during migration, so the migration diff is confined
to the *plumbing* half.

The new §1.9 paths (crafting resolver, fuel, mesh, guards) call `FreeMode.isOn`
directly, since they sit outside `AbstractBuilding`/`AbstractEntityAIBasic` —
the resolver has `building` in scope
(`AbstractCraftingProductionResolver.java:157`) and `manager.getColony()`
everywhere else.

The four accessors becoming one-liners is the whole "one early-return" property:
every existing `if (worksWithoutTools())` guard in the AI tree keeps working
unchanged, but they all now bottom out in a single boolean read.

### 2.6 What happens to the 21 + 38 per-hut checkboxes

**Recommendation: delete outright.** Do not leave them registered-but-ignored — a
checkbox that does nothing is worse than no checkbox, and the point of the
exercise is fewer places to unpick later.

**Is deleting a setting key safe for existing saves? Yes — verified.**

- Orphaned key on load: `core/colony/buildings/modules/SettingsModule.java:58-80`.
  `deserializeNBT` walks the saved `settingslist` and only applies an entry when
  `settings.containsKey(settingsKey)` (`:70`). A key the module no longer
  declares is silently skipped; the body is additionally wrapped in
  `catch (final IllegalArgumentException ex)` at `:76-79` which logs
  `"Detected Removed Setting"` and continues. **No crash, no data migration
  needed.** The stale value is simply dropped on the next save
  (`serializeNBT`, `:83-94`, writes only declared keys).
- Removing an entire module producer (`TOOL_SETTINGS`, `MATERIAL_SETTINGS`,
  `COOK_SETTINGS`): `AbstractBuilding.java:380-386` iterates the modules the
  building *currently has* and loads a tag only
  `if (compound.getCompoundOrEmpty(TAG_BUILDING_MODULES).contains(module.getProducer().key))`.
  A saved subtag with no matching module is never read and is dropped on the next
  save at `:419`. **Safe.**
- One caveat: the six huts whose *only* settings module is `TOOL_SETTINGS`
  (archery, combat academy, fisherman, sifter, graveyard, kitchen), the five on
  `MATERIAL_SETTINGS` (library, flower shop, enchanter, hospital, alchemist) and
  the dining hall on `COOK_SETTINGS` will **lose their settings tab entirely** —
  they had no other settings. That is the correct outcome (it restores upstream's
  GUI), but it is a visible change and should be stated in `COMMANDS.md`.
- `SETTINGS_CRAFTER_RECIPE` and the 14 bespoke producers keep their tabs; only
  the `.with(AbstractBuilding.NO_TOOL_REQUESTS, …)` /
  `.with(AbstractBuilding.WORK_WITHOUT_MATERIALS, …)` lines come out.

### 2.7 Migration — the exact edit list

| # | File | Edit |
|---|---|---|
| 1 | **NEW** `core/debug/FreeMode.java` | `isOn(IColony)` / `isOn(IBuilding)`, ~30 lines |
| 2 | **NEW** `core/commands/colonycommands/CommandColonyFreeMode.java` | ~60 lines, modelled on `CommandSetDeletable` |
| 3 | `core/commands/EntryPoint.java` | +1 line at `:64` |
| 4 | `core/colony/Colony.java` | +1 field, +1 line at `:843` (load), +1 at `:1006` (save), +getter, +setter ≈ 12 lines |
| 5 | `core/colony/buildings/AbstractBuilding.java` | −3 `ISettingKey` declarations (`:122`, `:130`, `:138`, ~18 lines with javadoc); rewrite 3 accessor bodies (`:1241`, `:1255`, `:1265`) to `return FreeMode.isOn(colony);` |
| 6 | `core/colony/buildings/workerbuildings/BuildingBuilder.java` | −`BUILD_WITHOUT_RESOURCES` (`:51`, ~7 lines) |
| 7 | `core/colony/buildings/AbstractBuildingStructureBuilder.java` | rewrite `:450` to `return FreeMode.isOn(colony);` |
| 8 | `core/colony/buildings/modules/BuildingModules.java` | −3 whole producers (`TOOL_SETTINGS` `:58-64`, `MATERIAL_SETTINGS` `:66-76`, `COOK_SETTINGS` `:78-84`); −34 `.with(...)` lines across 15 surviving producers; −1 line at `:488` ≈ **40 lines** |
| 9 | `apiimp/initializer/ModBuildingsInitializer.java` | −12 `addBuildingModuleProducer` lines (`:40`, `:133`, `:157`, `:230`, `:268`, `:336`, `:430`, `:443`, `:464`, `:620`, `:668`, `:684`) |
| 10 | `src/main/resources/assets/minecolonies/lang/manual_en_us.json` | −7 keys (`:2211-2217`), +2 command keys. **Not edited by this report** — listed for the implementer |
| 11 | `src/generated/resources/assets/minecolonies/lang/en_us.json` | regenerated |
| 12 | `COMMANDS.md` | rewrite the "Hut settings" section (`:65-143`) as one command entry |

**Unchanged during migration:** all 17 AI/behaviour files. Their
`if (worksWithout*())` guards and `supplyMaterialWithoutRequest` keep working
verbatim. That is the property that makes this migration low-risk.

**Then** apply the §1.9 gap closures on top — 8 further files, ~65 lines, all of
them `if (FreeMode.isOn(...)) { … }` blocks in the same shape as the existing
guards.

### 2.8 The deletion story — the headline number

This is the metric the user asked to be ranked on.

#### Today (per-hut checkboxes)

| Layer | Files | Lines |
|---|---|---|
| Setting keys | `AbstractBuilding.java`, `BuildingBuilder.java` | ~25 |
| Module producers + `.with` lines | `BuildingModules.java` | ~40 |
| Hut registration | `ModBuildingsInitializer.java` | 12 |
| Accessors | `AbstractBuilding.java`, `AbstractBuildingStructureBuilder.java` | ~30 |
| Behaviour guards + helper | 17 AI/module/entity files | ~330 |
| Lang | `manual_en_us.json`, generated `en_us.json` | 7 + 7 |
| **Total** | **22 Java + 2 lang = 24 files** | **~550** |

Adding the toggle to one more hut today costs 1-3 lines in two files
(`BuildingModules.java` + `ModBuildingsInitializer.java`) and a decision about
which existing producer it can join without violating the
first-settings-module-only rule (`AbstractBuilding.java:1220-1228`).

#### After consolidation

| Layer | Files | Lines |
|---|---|---|
| Flag | `Colony.java` | 12 |
| Read point | `FreeMode.java` (**delete whole file**) | 30 |
| Command | `CommandColonyFreeMode.java` (**delete whole file**) + 1 line in `EntryPoint.java` | 61 |
| Accessors | `AbstractBuilding.java` ×3, `AbstractBuildingStructureBuilder.java` ×1 | 4 method bodies |
| Behaviour guards + helper | 17 AI/module/entity files (unchanged from today) | ~330 |
| New gap closures (§1.9) | 8 further files | ~65 |
| Lang | `manual_en_us.json` + generated | 2 + 2 |
| **Total** | **2 new files to delete outright + ~24 files to revert** | **~505** |

The raw line count barely moves — that is expected, because ~65 % of the feature
is behaviour guards that must exist under any design. What changes is the
*shape* of the deletion:

| | Today | After |
|---|---|---|
| Symbols to grep to find the whole feature | 5 (`NO_TOOL_REQUESTS`, `WORK_WITHOUT_MATERIALS`, `WORK_WITHOUT_FOOD`, `BUILD_WITHOUT_RESOURCES`, `TOOL_SETTINGS`/`MATERIAL_SETTINGS`/`COOK_SETTINGS`) | **1** (`FreeMode`) |
| Files that must be *edited surgically* (not just reverted) | `BuildingModules.java` (37 scattered `.with` lines inside 18 producers), `ModBuildingsInitializer.java` (12 scattered lines) — **easy to leave a stray line behind** | none of these — those two files never gain a line in the first place |
| Files deletable outright | 0 | 2 |
| Registry/GUI/lang surface to unpick | 3 module producers, 4 setting keys, 7 lang keys, 12 huts' registration | 2 lang keys |
| Risk of a half-deletion compiling but misbehaving | moderate — a leftover `.with(...)` on a producer silently re-adds a checkbox | low — a leftover call to `FreeMode` fails to compile once the class is gone |
| Cost of covering one more hut / one more stall | 1-3 lines + a settings-module decision | **0 lines** — global switch covers every hut, including the 12 with no settings module |

**The headline:** consolidation does not remove much code, but it removes the
*scattered* code. After it, deleting the feature is `rm` two files, revert four
one-line method bodies, revert 12 lines in `Colony.java`/`EntryPoint.java`, and
strip ~330 lines of guards that are mechanically findable because they all
reference the one deleted class. The compiler finds every remaining reference for
you. Today, nothing does — a forgotten `.with(AbstractBuilding.NO_TOOL_REQUESTS,
new BoolSetting(false))` in `BuildingModules.java` would still compile and would
still ship a dead checkbox.

### 2.9 Order of work

1. `FreeMode.java` + `Colony` field + command + `EntryPoint` line. Feature is
   now switchable, per-hut checkboxes still work independently.
2. Repoint the four accessors at `FreeMode`. Behaviour unchanged for anyone with
   the flag on; per-hut checkboxes become inert.
3. Strip `BuildingModules.java` + `ModBuildingsInitializer.java` + lang keys.
   Checkboxes gone.
4. Close the §1.9 gaps, crafting resolver first — it is the one that unblocks the
   whole request chain and is a single ~12-line insertion in a single file.

Steps 1-3 are pure refactor: no behaviour change for a colony with the flag on.
Step 4 is the only step that changes what the mod can do.

---

## Appendix — files currently carrying the feature

Behaviour (unchanged by the migration, ~330 lines):

`core/entity/ai/workers/AbstractEntityAIBasic.java`,
`core/entity/ai/workers/AbstractEntityAIStructure.java`,
`core/entity/ai/workers/AbstractEntityAIStructureWithWorkOrder.java`,
`core/entity/ai/workers/util/BuildingStructureHandler.java`,
`core/entity/ai/workers/production/herders/AbstractEntityAIHerder.java`,
`core/entity/ai/workers/production/agriculture/EntityAIWorkBeekeeper.java`,
`core/entity/ai/workers/production/agriculture/EntityAIWorkComposter.java`,
`core/entity/ai/workers/production/agriculture/EntityAIWorkPlanter.java`,
`core/entity/ai/workers/production/agriculture/EntityAIWorkFlorist.java`,
`core/entity/ai/workers/production/agriculture/EntityAIWorkFisherman.java`,
`core/entity/ai/workers/guard/training/EntityAIArcherTraining.java`,
`core/entity/ai/workers/service/EntityAIWorkCook.java`,
`core/entity/ai/workers/service/EntityAIWorkHealer.java`,
`core/entity/ai/minimal/EntityAIEatTask.java`,
`core/entity/other/NewBobberEntity.java`,
`core/colony/buildings/modules/RestaurantMenuModule.java`,
`core/colony/buildings/workerbuildings/BuildingCook.java`.

Plumbing (removed or rewritten by the migration, ~110 lines + 14 lang lines):

`core/colony/buildings/AbstractBuilding.java`,
`core/colony/buildings/AbstractBuildingStructureBuilder.java`,
`core/colony/buildings/workerbuildings/BuildingBuilder.java`,
`core/colony/buildings/modules/BuildingModules.java`,
`apiimp/initializer/ModBuildingsInitializer.java`,
`src/main/resources/assets/minecolonies/lang/manual_en_us.json`,
`src/generated/resources/assets/minecolonies/lang/en_us.json`.

Files the §1.9 gap closures would newly touch (8):

`core/colony/requestsystem/resolvers/core/AbstractCraftingProductionResolver.java`,
`core/entity/ai/workers/crafting/AbstractEntityAICrafting.java`,
`core/entity/ai/workers/crafting/AbstractEntityAIRequestSmelter.java`,
`core/entity/ai/workers/AbstractEntityAIUsesFurnace.java`,
`core/entity/ai/workers/crafting/EntityAIWorkAlchemist.java`,
`core/entity/ai/workers/crafting/EntityAIWorkSmelter.java`,
`core/entity/ai/workers/crafting/EntityAIWorkSifter.java`,
`core/entity/ai/workers/guard/MeleeCombatAI.java` + `RangeCombatAI.java`.
