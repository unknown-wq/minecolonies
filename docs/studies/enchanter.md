# The enchanter: what it does, what it costs, and what it is worth

Research and audit only. Date: 2026-08-27. Tree: `26.3/` on branch `26.3`, targeting Minecraft
**26.3-snapshot-10**. **No feature code was written**; the only file this study touches is itself.

## Verdict

The enchanter is a two-mode worker: a **drain** mode that walks to another hut and plays a particle
beam at a citizen, and an **enchant** mode that turns one *ancient tome* into one random enchanted
book off a five-tier loot table. Both modes are inherited byte-for-byte from upstream
MineColonies 1.21.1 — the only difference in the whole AI file is two `swing` → `swingForAttack`
renames forced by the port (§0.2). Three things are wrong with it, and all three are invisible:

1. **It drains itself.** `getModuleForJob()` returns the module of the *enchanter's own* job, not the
   module of the building it just walked to, so `citizenToGatherFrom` is always the enchanter
   (`EntityAIWorkEnchanter.java:324`). The target citizen is never touched. The walk, the beam and
   the whole "drain a worker" fantasy are decoration over a no-op.
2. **The enchanting cost is never paid.** The mana debit reads `DataComponents.ENCHANTMENTS` off an
   enchanted book (`EntityAIWorkEnchanter.java:287`); an enchanted book stores its enchantments under
   `STORED_ENCHANTMENTS`, so the debit is always zero. The enchanter drains until it passes the
   level gate once and then never drains again for the life of the colony.
3. **It stops working at noon.** The guard at `EntityAIWorkEnchanter.java:132` tests
   `craftState != START_WORKING`, but `getNextCraftingState()` cannot return `START_WORKING` — it
   returns `IDLE` when there is no order. So every afternoon the enchanter returns `IDLE` from
   `decide()` and does nothing at all until the next dawn. It loses roughly 45% of its work day.

None of the three is a port regression: the AI file is upstream's, verbatim (§0.2). (2) in particular
is the shape of defect this fork should be hunting for on a version bump — **an enchantment-API call
that still compiles, still returns a valid `ItemEnchantments`, and no longer means what it meant.**
The `ENCHANTMENTS`/`STORED_ENCHANTMENTS` split is older than 26.3 **[UNVERIFIED]** as to exactly
which version introduced it; what is verified is that snapshot-10 has it and that the enchanter is on
the wrong side of it (§5.1).

On worth: **build the enchanter's tower to level 3 for the four scroll recipes, and do not level it
past 3 for the books.** The arithmetic is in §7. The short version: the only input is the *ancient
tome*, which drops only from raiders, and raids come on average every 14 nights
(`ServerConfiguration.java:387`). A level-5 enchanter can consume five tomes a day and will see
roughly one every four days. It runs at a few per cent of its capacity, and even at maximum level 88%
of what it does produce is a book nobody would apply — at hut levels 1 and 2, all of it. Nothing in
the colony consumes an enchanted book except one research, once, which needs one.

---

## Evidence standard

Same as `docs/studies/worldmap-chunk-generation.md`:

* **[VERIFIED]** — I opened the file and the cited `file:line` says what I claim, or I ran the thing
  and watched the output.
* **[UNVERIFIED]** — inference I did not confirm. Each one names what would confirm it.

Paths: `26.3/src/main/java/com/minecolonies/` is abbreviated to `mc/`;
`26.3/src/main/generated/data/minecolonies/` to `gen/`. Vanilla sources are read from
**`/opt/mc-src-26.3-snapshot-10/net/minecraft/`**, abbreviated to `v/`, and **no other vanilla tree
is cited anywhere in this document**. The upstream 1.21.1 snapshot used for the "is this ours or
theirs" question is read out of git history (`git show b104817d^:1.21.1/...`), which is the tree the
repository documented as byte-identical to `ldtteam/minecolonies@325157eba3` before it was removed on
the 26.3 branch (`UPSTREAM-SYNC.md`).

**Nothing here was run.** Every claim is a source reading. Where a claim would need a running server
to settle, it is marked [UNVERIFIED] and says so.

**Line numbers are against the branch tip this study is committed on, not against the working tree.**
The checkout is shared and several files were being edited while this was written; the only cited
file affected is `mc/api/configuration/ServerConfiguration.java`, whose two raid-frequency lines sit
at 387–388 in the commit and had drifted to 390–391 in the tree at the time of reading. Every other
cited file was unmodified.

---

## 0. What I read, and what I compared against

### 0.1 Read end to end

`mc/core/entity/ai/workers/service/EntityAIWorkEnchanter.java` (483 lines),
`mc/core/colony/jobs/JobEnchanter.java`,
`mc/core/colony/buildings/workerbuildings/BuildingEnchanter.java`,
`mc/core/colony/buildings/modules/EnchanterStationsModule.java`,
`mc/core/colony/buildings/moduleviews/EnchanterStationsModuleView.java`,
`mc/core/client/gui/modules/building/EnchanterStationModuleWindow.java`,
`mc/core/network/messages/server/colony/building/enchanter/EnchanterWorkerSetMessage.java`,
`mc/core/generation/defaults/workers/DefaultEnchanterCraftingProvider.java` (460 lines),
`mc/core/generation/defaults/DefaultEnchantmentProvider.java`,
`mc/api/enchants/ModEnchants.java`,
`mc/core/tileentities/TileEntityEnchanter.java`.

Read in part, for the machinery the enchanter sits on:
`mc/core/entity/ai/workers/AbstractEntityAIBasic.java`,
`mc/core/entity/ai/workers/crafting/AbstractEntityAICrafting.java`,
`mc/core/colony/buildings/modules/AbstractCraftingBuildingModule.java`,
`mc/api/crafting/RecipeStorage.java`,
`mc/core/entity/ai/workers/CitizenAI.java`,
`mc/core/entity/citizen/citizenhandlers/CitizenSkillHandler.java`,
`mc/core/entity/citizen/citizenhandlers/CitizenExperienceHandler.java`,
`mc/api/util/WorldUtil.java`, `mc/api/util/StatsUtil.java`, `mc/api/util/ItemStackUtils.java`,
`mc/apiimp/initializer/ModBuildingsInitializer.java`,
`mc/apiimp/initializer/InteractionValidatorInitializer.java`,
`mc/core/colony/buildings/modules/BuildingModules.java`.

### 0.2 Compared against upstream

`diff` of `mc/core/entity/ai/workers/service/EntityAIWorkEnchanter.java` against the 1.21.1 snapshot
in git history returns **exactly two hunks, four lines**: `worker.swing(...)` →
`worker.swingForAttack(...)` at lines 251/255 and 384/388 **[VERIFIED]**. The same diff against the
current upstream `version/1.21` tip file is byte-identical to the snapshot **[VERIFIED]** — upstream
has not touched this file since. `AbstractEntityAIBasic#getModuleForJob` is likewise the same
one-liner in both trees (`1.21.1/.../AbstractEntityAIBasic.java:669-672`,
`mc/core/entity/ai/workers/AbstractEntityAIBasic.java:796-799`) **[VERIFIED]**.

**Consequence for this audit: every behavioural defect below is upstream's, not the port's.** That
does not make them less real in this fork — the port ships them — but it means none of them are
regressions introduced here, and it means a fix is a divergence from upstream that has to be
maintained.

### 0.3 Vanilla read

`v/world/item/enchantment/EnchantmentHelper.java`, `v/world/item/enchantment/Enchantment.java`,
`v/world/item/enchantment/Enchantments.java`, `v/world/item/enchantment/ItemEnchantments.java`,
`v/world/item/ItemStack.java`, `v/world/item/Items.java`, `v/core/component/DataComponents.java`,
`v/world/inventory/EnchantmentMenu.java`, `v/tags/EnchantmentTags.java`, `v/world/level/Level.java`.

---

## 1. Where it lives

| Piece | File |
|---|---|
| Job | `mc/core/colony/jobs/JobEnchanter.java` |
| Job registration | `mc/apiimp/initializer/ModJobsInitializer.java:219-223` |
| AI | `mc/core/entity/ai/workers/service/EntityAIWorkEnchanter.java` |
| AI states | `mc/api/entity/ai/statemachine/states/AIWorkerState.java:599` (`ENCHANTER_DRAIN`), `:604` (`ENCHANT`) |
| Building | `mc/core/colony/buildings/workerbuildings/BuildingEnchanter.java` |
| Building registration | `mc/apiimp/initializer/ModBuildingsInitializer.java:428-438` |
| Modules | `mc/core/colony/buildings/modules/BuildingModules.java:640-646` — `enchanter_work`, `enchanter_craft`, `enchanter_stations` |
| Crafting module | `BuildingEnchanter.CraftingModule` (`BuildingEnchanter.java:55-73`), extends `AbstractCraftingBuildingModule.Custom` |
| Station-selection module | `mc/core/colony/buildings/modules/EnchanterStationsModule.java` |
| Station module view | `mc/core/colony/buildings/moduleviews/EnchanterStationsModuleView.java` |
| Station GUI | `mc/core/client/gui/modules/building/EnchanterStationModuleWindow.java` (layout `minecolonies:gui/layouthuts/layoutenchanter.xml`, a runtime asset) |
| Station packet | `mc/core/network/messages/server/colony/building/enchanter/EnchanterWorkerSetMessage.java` |
| Hut block / tile / renderer | `mc/core/blocks/huts/BlockHutEnchanter.java`, `mc/core/tileentities/TileEntityEnchanter.java`, `mc/core/client/render/TileEntityEnchanterRenderer.java` |
| Citizen models | `mc/core/client/model/{Male,Female}EnchanterModel.java` |
| Datagen | `mc/core/generation/defaults/workers/DefaultEnchanterCraftingProvider.java` |
| Mod enchantment | `mc/api/enchants/ModEnchants.java`, `mc/core/generation/defaults/DefaultEnchantmentProvider.java` |
| Generated loot tables | `gen/loot_table/recipes/enchanter{1..5}.json` |
| Generated recipes | `gen/crafterrecipes/enchanter/` |
| Generated enchantment | `gen/enchantment/raider_damage_enchant.json` |
| Hut craft recipe | `gen/recipe/blockhutenchanter.json` — enchanting table + `structurize:sceptergold` + 7 planks |
| Advancement | `gen/advancement/production/build_enchanter.json` (parent: `build_library`) |
| Interaction validator | `mc/apiimp/initializer/InteractionValidatorInitializer.java:259-260` |

Registration facts worth pinning down:

* Primary skill **Mana**, secondary skill **Knowledge**, **one** worker per building
  (`BuildingModules.java:641`: `new CraftingWorkerBuildingModule(ModJobs.enchanter.get(), Skill.Mana,
  Skill.Knowledge, false, (b) -> 1)`) **[VERIFIED]**.
* Max building level **5** (`BuildingEnchanter.java:28,50-53`) **[VERIFIED]**.
* The building keeps up to a full stack of ancient tomes (`BuildingEnchanter.java:39`,
  `keepX(... == ModItems.ancientTome, STACKSIZE, true)`) **[VERIFIED]**.
* The building has **no settings module** (`ModBuildingsInitializer.java:433-437` lists only
  `ENCHANTER_WORK`, `ENCHANTER_CRAFT`, `ENCHANTER_STATIONS`, `MIN_STOCK`, `STATS_MODULE`)
  **[VERIFIED]**. There is no settings tab; the player's only controls are the station list, the
  per-recipe enable/disable toggle in the crafting tab, and min-stock.
* `CraftingModule.addRecipe` returns `false` unconditionally (`BuildingEnchanter.java:67-72`)
  **[VERIFIED]** — the player cannot teach the enchanter new recipes. Its recipe set is exactly what
  datagen writes.
* **No blueprints in this tree.** `26.3/src/main/resources/` has no `blueprints/` directory; the
  hut's schematics come from the runtime asset fetch (`26.3/src/main/resources/assetfetch/`)
  **[VERIFIED]**. On the 26.2 branch, which still carries them, the enchanter has all five levels in
  21 styles **[VERIFIED]** by directory listing.

---

## 2. What it actually does — the state trace

The citizen AI calls `getWorkerAI().tick()` every **5 game ticks**
(`CitizenAI.java:94-106`, `AbstractEntityCitizen.java:73` `ENTITY_AI_TICKRATE = 5`) **[VERIFIED]**.
Each `AITarget`'s third argument is a period in **game ticks**
(`TickingTransition.java:16-18,49-53`) **[VERIFIED]**.

The enchanter registers two targets of its own (`EntityAIWorkEnchanter.java:110-113`):

```
new AITarget(ENCHANTER_DRAIN, this::gatherAndDrain, 10)     // every 10 ticks
new AITarget(ENCHANT,         this::enchant,        TICKS_SECOND)  // every 20 ticks
```

and inherits the crafter's targets (`AbstractEntityAICrafting.java:162-167`):

```
new AITarget(IDLE, this::hasWorkToDo, () -> START_WORKING, 20)
new AITarget(IDLE, this::idle, 20)
new AITarget(START_WORKING, this::decide, 5)
new AITarget(QUERY_ITEMS, ...), new AITarget(GET_RECIPE, ...), new AITarget(CRAFT, ...)
```

`hasWorkToDo()` is overridden to **always return true** (`EntityAIWorkEnchanter.java:210-213`)
**[VERIFIED]**, so `IDLE` is never a resting state: it bounces straight back to `START_WORKING` on
the next 20-tick beat.

### 2.1 `START_WORKING` → `decide()`

| Step | `file:line` | What happens |
|---|---|---|
| clear held item | `EntityAIWorkEnchanter.java:125` | `setItemInHand(MAIN_HAND, EMPTY)` every pass |
| walk home | `:126-129` | not at the tower → `START_WORKING` (walk continues) |
| **time / order gate** | `:131-135` | `getNextCraftingState()`, then `if (craftState != START_WORKING && !WorldUtil.isPastTime(world, 6000)) return craftState;` |
| dump gate | `:137-141` | `wantInventoryDumped()` → return current state; the `STATE_BLOCKING` `inventoryNeedsDump` target (`AbstractEntityAIBasic.java:255,467-474`) then takes it to `INVENTORY_FULL` |
| **mana gate** | `:143` | `getPrimarySkillLevel() < building.getBuildingLevel() * 10` → drain branch |
| no stations | `:147-155` | `NO_WORKERS_TO_DRAIN_SET` blocking interaction, `return IDLE` |
| need a book | `:157-168` | in building → `GATHERING_REQUIRED_MATERIALS`; else file an async request for 1 `minecraft:book` and `return IDLE` |
| pick a target | `:170-176` | `module.getRandomBuildingToDrainFrom()`; null → `IDLE`; else `job.setBuildingToDrainFrom(pos)` and `return ENCHANTER_DRAIN` |
| tome-recipe disabled? | `:179-188` | scan the crafting module's recipes for an ancient-tome input that the player has toggled off |
| need a tome | `:192-203` | in building → `GATHERING_REQUIRED_MATERIALS`; else async request for 1 `minecolonies:ancienttome` and `return IDLE` |
| work | `:206` | `return ENCHANT` |

**The time gate is inverted-looking and it matters.** `WorldUtil.isPastTime(world, t)` returns
`world.getOverworldClockTime() % 24000 <= t` (`WorldUtil.java:183-187`) **[VERIFIED]** — it is true
*before* `t`, despite the name. So `!isPastTime(world, 6000)` is true in the **afternoon**. And
`getNextCraftingState()` returns one of `IDLE`, `INVENTORY_FULL`, `QUERY_ITEMS`, `GET_RECIPE`
(`AbstractEntityAICrafting.java:310-329`) **[VERIFIED]** — **never `START_WORKING`**. The left half
of the `&&` is therefore a constant `true`, and line 132 reduces to:

> after game time 6000, return whatever `getNextCraftingState()` says — which is `IDLE` unless a
> player crafting order is outstanding.

Citizens work until roughly game time 11000 (`CitizenAI.java:181`,
`!WorldUtil.isPastTime(world, NIGHT - 2000)` gates the go-to-bed branch) **[VERIFIED]**. So the
enchanter's productive window is game ticks **0–6000 of an 11000-tick work day**, and for the other
5000 ticks it stands in its tower cycling `IDLE` → `START_WORKING` → `IDLE` with the citizen status
reading "Working" (`AbstractEntityAIBasic.java:2287-2290` `canGoIdle()` returns `false`, so
`CitizenAI.java:289-296` pins it in `WORK`) **[VERIFIED]**.

Read with `!= IDLE` instead of `!= START_WORKING`, the line becomes a coherent design — *mornings
drain and enchant, afternoons fill the player's scroll orders* — which is almost certainly what was
meant. As written the afternoon is dead.

### 2.2 `ENCHANTER_DRAIN` → `gatherAndDrain()`, every 10 ticks

| Step | `file:line` | What happens |
|---|---|---|
| no target pos | `:303-306` | `IDLE` |
| walk | `:308-312` | `walkToBuilding(buildingWorker)`; a `null` building returns `true` immediately (`AbstractEntityAIBasic.java:941-948`) so there is no NPE |
| building gone | `:314-319` | `resetDraining()`, drop the entry from the station list, `IDLE` |
| **pick a citizen** | `:321-347` | **`getModuleForJob().getAssignedEntities()`** — see below |
| entity not loaded | `:349-353` | clear and retry |
| distance | `:355-367` | > 10 blocks 2D → `job.incrementWaitingTicks()`, 60 tries max (`JobEnchanter.java:24,112-120`), then give up |
| beam | `:369-392` | 60 invocations × 10 ticks = **600 ticks = 30 s** of `StreamParticleEffectMessage` + `CircleParticleEffectMessage` + arm swings |
| payoff | `:394-416` | see §3 |
| reset | `:417`, `:424-432` | mark the station gathered, clear state, `incrementActionsDoneAndDecSaturation()`, `IDLE` |

**Line 324 is the defect.** `getModuleForJob()` is
`(WorkerBuildingModule) job.getWorkModule()` (`AbstractEntityAIBasic.java:796-799`) **[VERIFIED]**,
and `AbstractJob#getWorkModule` returns the module *this job is assigned to*
(`AbstractJob.java:151-155`) **[VERIFIED]** — i.e. the `enchanter_work` module of the enchanter's own
tower. `getAssignedEntities()` returns that module's own citizens
(`AbstractAssignedCitizenModule.java:120-123`) **[VERIFIED]**, and the enchanter module is capped at
one worker (`BuildingModules.java:641`, `(b) -> 1`) **[VERIFIED]**. So `workers` is always the
singleton `[the enchanter]`, `workers.size() > 1` is false, and line 341 selects the enchanter
itself.

The local `buildingWorker` fetched at line 308 is used for the walk and the null check and **never
for anything else** **[VERIFIED]** — which is the smell that gives the bug away.

Downstream of that: the distance check at `:358` compares the enchanter to itself and always passes;
the particle stream at `:372-376` runs from `worker.position()` to `worker.position()`; and the
"drained" citizen at `:397-409` is the enchanter's own inventory.

### 2.3 `ENCHANT` → `enchant()`, every 20 ticks

| Step | `file:line` | What happens |
|---|---|---|
| find the recipe | `:229` | `getFirstFulfillableRecipe(ItemStackUtils::isEmpty, 1, false)` — matches the one recipe whose *primary output is empty*, i.e. the tome→loot-table recipe for this building level |
| none | `:230-234` | `progressTicks = 0; return START_WORKING` |
| spin | `:236-258` | `progressTicks++ < 300 / buildingLevel` passes × 20 ticks: three enchant-particle circles, `ENCHANTMENT_TABLE_USE`, a random arm swing |
| produce | `:263` | `currentRecipeStorage.fullfillRecipeAndCopy(getLootContext(), building.getHandlers(), true)` |
| **pay** | `:266-271` | `max(getEnchantedBookLevel(...))` over the loot, then `incrementLevel(Skill.Mana, -level)` |
| record | `:272` | `recordEnchantmentStats(loot)` |
| account | `:273` | `incrementActionsDoneAndDecSaturation()` |
| finish | `:277-279` | clear, `return IDLE` |

Cycle length is `300 / buildingLevel + 1` invocations at 20 ticks each, i.e. **6000 / buildingLevel +
20 game ticks**: 6020 (5:01 real) at level 1, then 3020, 2020, 1520, **1220 (1:01 real) at level 5**
**[VERIFIED]** by arithmetic on `MAX_ENCHANTMENT_TICKS = 60 * 5` (`:80`), the post-increment compare
at `:236`, and the target's 20-tick period. Rounded to `6000 / buildingLevel` below.

`ENCHANT` is declared **not okay to eat** (`AIWorkerState.java:604`, `ENCHANT(false)`) **[VERIFIED]**,
and `canBeInterrupted()` is `getState().isOkayToEat()` (`AbstractAISkeleton.java:128`)
**[VERIFIED]**. At building level 1 the enchanter therefore spends its entire 6000-tick productive
window, and a little beyond it, in a state it cannot leave to eat or to dump.

The mana debit is dead — see §5.1.

---

## 3. The experience drain

**Nothing is drained. From anyone. By design, and then again by accident.**

The payoff block, in full (`EntityAIWorkEnchanter.java:394-416`) **[VERIFIED]**:

```java
final int bookSlot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), Items.BOOK);
if (bookSlot != -1)
{
    final int size = citizenToGatherFrom.getInventory().getSlots();
    final int attempts = (int) (getSecondarySkillLevel() / 5.0);

    for (int i = 0; i < attempts; i++)
    {
        int randomSlot = worker.getRandom().nextInt(size);
        final ItemStack stack = citizenToGatherFrom.getInventory().getStackInSlot(randomSlot);
        if (!stack.isEmpty() && stack.isEnchantable())
        {
            EnchantmentHelper.enchantItem(worker.getRandom(), stack, getSecondarySkillLevel() > 50 ? 2 : 1, world.registryAccess(), Optional.empty());
            break;
        }
    }

    worker.getInventoryCitizen().extractItem(bookSlot, 1, false);
    worker.getCitizenData().getCitizenSkillHandler().incrementLevel(Skill.Mana, 1);
    worker.getCitizenExperienceHandler().addExperience(XP_PER_DRAIN);
    worker.getCitizenData().markDirty(80);
    StatsUtil.trackStat(building, CITIZENS_VISITED, 1);
}
```

**Whom it takes from.** `citizenToGatherFrom` is the enchanter (§2.2). Even with that fixed, the
block reads the target's inventory and **never touches the target's skills, experience, saturation or
happiness**. There is no debit anywhere in the class against anyone but the enchanter itself.

**How much.** Zero from the target. The *enchanter* gains `+1` Mana level
(`:412`) and 10 job experience (`:413`, `XP_PER_DRAIN = 10` at `:90`).

**How often.** Once per selected station per in-game day. `getRandomBuildingToDrainFrom()` only
returns stations whose flag is `false` (`EnchanterStationsModule.java:106-114`); `setAsGathered`
flips it (`:121-124`); `onWakeUp` clears every flag at dawn (`:148-154`) **[VERIFIED]**. A drain
costs ~30 s of beam plus the walk, all of which must fit inside the 0–6000 window (§2.1).

**What it costs the citizen drained.** One book — belonging to the *enchanter*, not the target
(`:394,411`). Nothing else. The name "drain" is flavour text.

**Is it visible to the player?** No. The `CITIZENS_VISITED` statistic is incremented (`:415`) and
shows in the building's stats tab; nothing in the target citizen's happiness, skills or interaction
log records the visit **[VERIFIED]** by absence of any write to `citizenToGatherFrom`.

**Can a citizen be drained to a level that hurts the colony? No, and nothing needs to stop it.**
`CitizenSkillHandler#incrementLevel` clamps to `[1, MAX_CITIZEN_LEVEL]` (`:179-183`,
`MAX_CITIZEN_LEVEL = 99` at `CitizenConstants.java:192`) **[VERIFIED]**, so even a working drain
could not push anyone below skill 1 — but no drain is ever applied, so the floor is never approached.

**One side effect worth naming.** `incrementLevel` writes the level field directly and bypasses the
housing cap that `addXpToSkill` enforces — the latter refuses to raise a skill past
`(homeHutLevel + 1) * 10` (`CitizenSkillHandler.java:195-199`) **[VERIFIED]**. So each drain grants a
free Mana level the normal progression system would have refused. In practice this is bounded by the
gate: the enchanter stops draining the moment `Mana >= buildingLevel * 10`.

---

## 4. The output

### 4.1 The book path

One `minecolonies:ancienttome` in, one enchanted book out, drawn from
`minecolonies:recipes/enchanter{1..5}` — a single pool, `rolls: 1`, weighted entries, each an
`ENCHANTED_BOOK` with one `stored_enchantments` component **[VERIFIED]** by reading
`gen/loot_table/recipes/enchanter1.json`.

The recipe itself has an **empty primary output** and a loot table
(`DefaultEnchanterCraftingProvider.java:400-407`): `minBuildingLevel(N).maxBuildingLevel(N)`, so
exactly one tier is live per building level. Its declared `secondaryOutputs` (a bare
`ENCHANTED_BOOK`) is **never emitted**, because `RecipeStorage#insertCraftedItems` only adds
`secondaryOutputs` when the primary output is non-empty (`RecipeStorage.java:797-816`, gate at `:802` and `:815`)
**[VERIFIED]**. Only the loot table produces anything, which is what the comment at
`EntityAIWorkEnchanter.java:228` says.

Output goes into `building.getHandlers()` — the assigned citizens' inventories plus the hut's
containers (`AbstractBuilding.java:1314-1337`) **[VERIFIED]**.

### 4.2 The pool, in numbers

Computed by summing weights out of the five generated JSON files **[VERIFIED]**:

| Hut level | Entries | Total weight | Tiers present (entry weight) | P(a book whose enchantment is at its table-maximum) |
|---|---|---|---|---|
| 1 | 22 | 1100 | tier-1 only (50) | 100% |
| 2 | 44 | 1650 | tier-1 (50) + tier-2 (25) | 39.4% |
| 3 | 68 | 1996 | tier-1 (50) + tier-2 (25) + tier-3 (15) + fortune (1) | 29.9% |
| 4 | 69 | 1007 | tier-2 (25) + tier-3 (15) + tier-4 (5) + fortune/infinity (1) | 46.8% |
| 5 | 75 | 485 | tier-3 (15) + tier-4 (5) + tier-5 (1) | 60.4% |

The table does climb: level 4 drops the tier-1 block entirely and level 5 drops tier-2 as well.

The entries that a player would actually cross the room for, at hut level 5 (weight / 485):

| Book | Weight | Probability |
|---|---|---|
| Unbreaking III | 21 | 4.33% |
| Looting III | 21 | 4.33% |
| Infinity | 6 | 1.24% |
| Mending | 1 | **0.206%** |
| Silk Touch | 1 | 0.206% |
| Fortune III | 1 | 0.206% |
| Sharpness V | 1 | 0.206% |
| Efficiency V | 1 | 0.206% |
| Power V | 1 | 0.206% |
| Smite V | 1 | 0.206% |
| Protection IV | 1 | 0.206% |
| Multishot | 1 | 0.206% |
| `minecolonies:raider_damage_enchant` II | 1 | 0.206% |

Aggregated: **11.75%** of level-5 books fall in that "worth having" set; **13.8%** are a level-1
book of a multi-level enchantment. At hut level 3 the same "worth having" figure is **1.50%**; at
hut levels 1 and 2 it is **0%** — those two tiers cannot produce a single book a player would
choose to apply.

Mending, Silk Touch and Fortune III exist **only at hut level 5** and only at 1 weight each.
Fortune appears at all from level 3 (Fortune I, weight 1 of 1996 = 0.05%).

### 4.3 How this compares with a vanilla enchanting table

* **Pool.** The vanilla table draws only from `#minecraft:enchantment/in_enchanting_table`
  (`v/world/inventory/EnchantmentMenu.java:187-189`) **[VERIFIED]**, which excludes every treasure
  enchantment: **Mending, Frost Walker, Soul Speed, Swift Sneak, Curse of Binding, Curse of
  Vanishing**. The enchanter's *loot table* is a hand-written list that happens to include Mending
  and Frost Walker — so it is the only renewable in-colony source of Mending.
* **Levels.** A vanilla level-30 book roll routinely produces 2–3 enchantments at or near maximum
  level. The enchanter's book always carries **exactly one** enchantment (one entry, one pool, one
  roll) **[VERIFIED]** — and vanilla explicitly removes one enchantment from a book roll when there
  is more than one (`v/world/inventory/EnchantmentMenu.java:196-197`), which the enchanter has no
  equivalent of because it never has more than one.
* **Rate.** A vanilla table with 15 bookshelves gives a level-30 roll for 3 levels and 3 lapis, as
  fast as the player can click. The enchanter needs 6000/hutLevel game ticks and one ancient tome
  per book (§7).
* **Luck does nothing.** `getLootContext()` sets `withLuck(effectiveSkillLevel(primarySkill))`
  (`AbstractEntityAICrafting.java:797`) **[VERIFIED]**, but luck only reaches a loot pool through an
  entry `quality` or `bonus_rolls`, and **no entry in any of the five tables carries either**
  **[VERIFIED]** by inspecting the generated JSON. **The enchanter's Mana level has zero effect on
  what comes out of the book table.** It only gates whether it may work at all (§2.1) and how fast a
  scroll order is crafted.

### 4.4 The drain path's enchantments

The other enchanting the class does is `EnchantmentHelper.enchantItem(..., getSecondarySkillLevel() >
50 ? 2 : 1, world.registryAccess(), Optional.empty())` (`:406`). Two things about it:

* **`Optional.empty()` means the whole registry.** `EnchantmentHelper#enchantItem` falls back to
  `registryAccess.lookupOrThrow(Registries.ENCHANTMENT).listElements()` when the set is absent
  (`v/world/item/enchantment/EnchantmentHelper.java:519-524`) **[VERIFIED]**. That is a strictly
  larger pool than a vanilla table's: curses, treasure enchantments and this mod's own
  `raider_damage_enchant` are all eligible.
* **The cost is 1 or 2.** After `selectEnchantment`'s adjustment
  (`v/.../EnchantmentHelper.java:544-551`) a book (enchantment value 1,
  `v/world/item/Items.java:1907`) lands at an effective cost of 2–3, and a mid-tier tool at roughly
  2–10 **[VERIFIED]** by reading the arithmetic. At that cost almost nothing survives the
  `minCost <= value <= maxCost` filter (`v/.../EnchantmentHelper.java:587-600`) except level 1 of the
  cheapest enchantments — Protection I, Efficiency I, Power I, Sharpness I, Piercing I, Impaling I,
  Projectile Protection I. **[UNVERIFIED]** exactly which of those can appear on which item in play;
  confirming means running the roll. Curses (min cost 25), Mending (25) and Swift Sneak (25) are out
  of reach; **Soul Speed (min cost 10, boots)** and **`raider_damage_enchant` (min cost 10, weapons,
  `gen/enchantment/raider_damage_enchant.json`)** are the two that could squeak in at the top of the
  range for a high-enchantability item.
* **The result is thrown away when the item is a book.** See §5.2.

---

## 5. Enchantment API in 26.3

Every enchantment-API call the enchanter makes, checked against snapshot-10.

### 5.1 The mana debit reads the wrong component — the silent defect

`EntityAIWorkEnchanter.java:282-294` **[VERIFIED]**:

```java
private static int getEnchantedBookLevel(@NotNull final ItemStack stack)
{
    if (stack.getItem().equals(Items.ENCHANTED_BOOK))
    {
        int level = 0;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet())
        {
            level = Math.max(level, entry.getIntValue());
        }
        return level;
    }
    return 0;
}
```

An enchanted book does not store its enchantments in `ENCHANTMENTS`. It stores them in
`STORED_ENCHANTMENTS`:

* `EnchantmentHelper#getComponentType` — `itemStack.is(Items.ENCHANTED_BOOK) ?
  DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS`
  (`v/world/item/enchantment/EnchantmentHelper.java:86-88`) **[VERIFIED]**.
* `ItemStack#enchant` writes through `EnchantmentHelper.updateEnchantments`, which uses
  `getComponentType` (`v/world/item/ItemStack.java:1004-1006`,
  `v/.../EnchantmentHelper.java:57-68`) **[VERIFIED]** — so the datagen at
  `DefaultEnchanterCraftingProvider.java:376-383` writes `stored_enchantments`, which the generated
  JSON confirms **[VERIFIED]**.
* `ENCHANTED_BOOK` declares `STORED_ENCHANTMENTS` as its own component
  (`v/world/item/Items.java:2227-2234`) and inherits `ENCHANTMENTS = ItemEnchantments.EMPTY` from
  `COMMON_ITEM_COMPONENTS` (`v/core/component/DataComponents.java:455-467`) **[VERIFIED]**.

So `stack.getOrDefault(DataComponents.ENCHANTMENTS, EMPTY)` on an enchanted book returns `EMPTY`,
the loop body never runs, `getEnchantedBookLevel` returns 0, and
`incrementLevel(Skill.Mana, -0)` (`:271`) is a no-op. **[VERIFIED]**

The same class gets it right 165 lines further down: `recordEnchantmentStats` uses
`EnchantmentHelper.getEnchantmentsForCrafting(stack)` (`:452`), which routes through
`getComponentType` (`v/.../EnchantmentHelper.java:79-81`) **[VERIFIED]**. And the mod's own
`ItemStackUtils#getMaxEnchantmentLevel` uses the same correct accessor
(`mc/api/util/ItemStackUtils.java:300-315`) **[VERIFIED]**. Two correct call sites and one wrong one,
in the same feature.

**What this produces in play:** the enchanter never pays for a book. Once its Mana passes
`buildingLevel * 10` it never drops back, so it never returns to the drain branch, so it never
consumes another `minecraft:book`, never visits another hut, and — since `enchant()` awards no
experience (§7.3) — **never gains another point of citizen experience** unless the player sends it a
scroll order.

**Is this a 26.3 API change?** The `ENCHANTMENTS`/`STORED_ENCHANTMENTS` split predates 26.3 and
upstream 1.21.1 has the identical line **[VERIFIED]** by diff (§0.2). It is not a port regression. It
is, however, the exact failure mode a version bump is supposed to catch and cannot: a call that
compiles, returns a valid `ItemEnchantments`, and silently means nothing.

### 5.2 `enchantItem`'s return value is discarded

`EntityAIWorkEnchanter.java:406` calls `EnchantmentHelper.enchantItem(...)` as a statement.
`enchantItem` **replaces the stack** when the input is a book:

```java
public static ItemStack enchantItem(final RandomSource random, ItemStack itemStack, final int enchantmentCost, final Stream<Holder<Enchantment>> source) {
    List<EnchantmentInstance> enchants = selectEnchantment(random, itemStack, enchantmentCost, source);
    if (itemStack.is(Items.BOOK)) {
        itemStack = new ItemStack(Items.ENCHANTED_BOOK);
    }
    for (EnchantmentInstance enchant : enchants) {
        itemStack.enchant(enchant.enchantment(), enchant.level());
    }
    return itemStack;
}
```
(`v/world/item/enchantment/EnchantmentHelper.java:527-538`) **[VERIFIED]**

For a non-book, `itemStack.enchant` mutates the passed stack in place and discarding the return is
harmless. For a **book** — and `minecraft:book` is `.enchantable(1)`
(`v/world/item/Items.java:1907`) **[VERIFIED]**, so `isEnchantable()` passes
(`v/world/item/ItemStack.java:995-1001`) **[VERIFIED]** — the enchantment lands on a throwaway copy
and the original book is untouched.

Because of §2.2 the inventory being scanned is the enchanter's own, and the only enchantable things
in it are the books it fetched for draining. **So in practice this branch does nothing at all, ever.**

### 5.3 Everything else checks out

* `EnchantmentHelper.enchantItem(RandomSource, ItemStack, int, RegistryAccess, Optional<? extends
  HolderSet<Enchantment>>)` — present with that signature
  (`v/.../EnchantmentHelper.java:512-525`) **[VERIFIED]**.
* `Enchantment.getFullname(Holder<Enchantment>, int)` — present
  (`v/world/item/enchantment/Enchantment.java:173-186`) **[VERIFIED]**; used at
  `EntityAIWorkEnchanter.java:460,464`.
* `ItemEnchantments.EMPTY`, `entrySet()`, `keySet()`, `getLevel(Holder)`, `size()` — all present
  (`v/world/item/enchantment/ItemEnchantments.java`) **[VERIFIED]**.
* `Enchantment.enchantment(definition).withEffect(...).build(Identifier)` in
  `DefaultEnchantmentProvider.java:23-44` produces JSON whose keys match the snapshot-10 codec
  exactly — `description`, `exclusive_set`, `effects`, `supported_items`, `primary_items`, `weight`,
  `max_level`, `min_cost`, `max_cost`, `anvil_cost`, `slots`
  (`v/world/item/enchantment/Enchantment.java:64-74, 706-713` vs
  `gen/enchantment/raider_damage_enchant.json`) **[VERIFIED]**.
* `raider_damage_enchant` is in **no** enchantment tag — this repository ships no
  `data/**/tags/enchantment/**` at all **[VERIFIED]**. That is correct for `in_enchanting_table`
  (it should not appear at a player's table), and harmless for `tooltip_order`, because
  `ItemEnchantments#addToTooltip` appends anything not in the ordering tag
  (`v/world/item/enchantment/ItemEnchantments.java:68-73`) **[VERIFIED]**.
* `WorldUtil.isPastTime` was ported from `Level#getDayTime` to `Level#getOverworldClockTime`
  (`WorldUtil.java:183-187`); snapshot-10 uses `getOverworldClockTime()` where it means "day time"
  (`v/world/level/Level.java:880-882, 983`) **[VERIFIED]**. The mapping is right and the comparison
  semantics are unchanged from 1.21.1 **[VERIFIED]** by diff against the snapshot.

### 5.4 The pool is frozen at 1.21.1

`Enchantments` in snapshot-10 declares **43** enchantment keys **[VERIFIED]** (counted as distinct
`key("...")` calls in `v/world/item/enchantment/Enchantments.java`). The enchanter's five loot tables
name **27** of them, plus its own `raider_damage_enchant` **[VERIFIED]**. The **16** absent from every
tier at every hut level are:

`binding_curse`, `breach`, `channeling`, `density`, `impaling`, `loyalty`, `luck_of_the_sea`,
`lunge`, `lure`, `piercing`, `riptide`, `soul_speed`, `swift_sneak`, `thorns`, `vanishing_curse`,
`wind_burst`.

The mace family (`density`, `breach`, `wind_burst`) is a 1.21 addition and the tables never learned
it; `lunge` is newer still. **[UNVERIFIED]** that `lunge` specifically postdates 1.21.1 — confirming
means reading a 1.21.1 vanilla tree, which this environment does not have and which the evidence rule
forbids substituting for. What *is* verified is that `lunge` exists in snapshot-10 and appears
nowhere in this repository **[VERIFIED]**.

Practical consequence: the enchanter cannot make a single book for a trident (`impaling`, `loyalty`,
`riptide`, `channeling`), a mace (`density`, `breach`, `wind_burst`) or a fishing rod
(`luck_of_the_sea`, `lure`), it cannot make `piercing` for a crossbow or `thorns` for armour, and its
list will keep drifting as vanilla adds enchantments.

---

## 6. The request system

**What the enchanter asks for.** Two things, both async, both one at a time:

* `minecraft:book`, quantity 1 (`EntityAIWorkEnchanter.java:166`) — only while
  `Mana < buildingLevel * 10`.
* `minecolonies:ancienttome`, quantity 1, `matchNBT = false`
  (`EntityAIWorkEnchanter.java:201`) — the interesting one.

Both go through `checkIfRequestForItemExistOrCreateAsync`
(`AbstractEntityAIBasic.java:1997-2000`) **[VERIFIED]**, so they surface in the colony request list
and a courier fills them from the warehouse. The building also declares
`keepX(ancientTome, STACKSIZE, true)` (`BuildingEnchanter.java:39`) **[VERIFIED]**, so a courier will
top the hut up to a full stack when tomes exist.

**Where ancient tomes come from: raiders, and only raiders.** Every raider entity loot table in
`mc/core/generation/defaults/DefaultEntityLootProvider.java:73-185` carries an `ancientTome` entry —
weight 3–5 against an 80-weight empty entry for rank and file (≈5%), weight 30–50 for chiefs (≈16–26%)
**[VERIFIED]**. No recipe anywhere produces one **[VERIFIED]** by grep. Raids arrive on average every
**14** nights, minimum 10 (`mc/api/configuration/ServerConfiguration.java:387-388`) **[VERIFIED]**,
and the same item is the default cost of resetting research
(`ServerConfiguration.java:357`, `"minecolonies:ancienttome:1"`) **[VERIFIED]**, so it has a
competing sink.

**What asks the enchanter for things.** Its crafting module carries four scroll recipes
(`DefaultEnchanterCraftingProvider.java:409-445`) **[VERIFIED]**:

| Recipe | Inputs | Output | Gate |
|---|---|---|---|
| `scroll_tp` | 3 paper + compass + `structurize` build tool | 3 × `scrollColonyTP` | — |
| `scroll_area_tp` | 3 × `scrollColonyTP` | 1 × `scrollColonyAreaTP` | hut ≥ 2 |
| `scroll_guard_help` | `scrollColonyTP` + 5 lapis + ender pearl + paper | 2 × `scrollGuardHelp` | hut ≥ 3, research `MORE_SCROLLS` |
| `scroll_highlight` | 3 × `scrollColonyTP` + 6 glowstone dust + 2 paper | 5 × `scrollHighLight` | hut ≥ 3, research `MORE_SCROLLS` |

These are ordinary `PublicCrafting` outputs and the enchanter fills them through the normal crafter
path (`AbstractEntityAICrafting.getRecipe` → `craft`). **Nothing inside the colony ever requests
them** — a grep for `scrollColonyTP`, `scrollColonyAreaTP`, `scrollGuardHelp` and `scrollHighLight`
across all of `26.3/src/main/java` finds no consumer other than the recipes themselves, the item
registry, the creative tab, and one research icon (`DefaultResearchProvider.java:1441`)
**[VERIFIED]**. They are player goods, ordered by hand.

**Can the enchanter's *books* be requested?** No. `getFirstFulfillableRecipe` matches on the primary
output (`AbstractCraftingBuildingModule.java:826-851`) **[VERIFIED]** and the tome recipe's primary
output is empty, so no request for `minecraft:enchanted_book` can ever resolve to it.

**Does anything consume an enchanted book?** Almost nothing:

* One research — *Skilled Butcher*, `technology/skilledbutcher`, `addItemCost(Items.ENCHANTED_BOOK, 1)`
  (`DefaultResearchProvider.java:1332`) **[VERIFIED]**. One book, once, per colony.
* One recruitment cost, rarity 9 (`gen/colony/recruitment_items/enchanted_book.json`) **[VERIFIED]** —
  and that is paid out of the **player's** inventory, not the colony's.
* Two research icons (`DefaultResearchProvider.java:1228, 1634`) **[VERIFIED]**.

No worker recipe, no building requirement, no equipment path takes one. This confirms and extends to
26.3 what `26.2/audit/WAREHOUSE-CLEANUP.md:98` found on the 26.2 branch: every enchanted book that
enters the warehouse stays there forever, and because a book's `stored_enchantments` component makes
it unstackable against any differently-enchanted book, each one occupies its own rack slot for good.

---

## 7. Levelling, building levels, and whether it is worth a citizen

### 7.1 What each level changes

| Hut level | Enchant cycle | Mana gate | Book table | Scrolls unlocked |
|---|---|---|---|---|
| 1 | 6020 ticks (5:01 real) | Mana ≥ 10 | tier 1 only, 0% useful | `scroll_tp` |
| 2 | 3020 ticks (2:31) | Mana ≥ 20 | +tier 2, 0% useful | +`scroll_area_tp` |
| 3 | 2020 ticks (1:41) | Mana ≥ 30 | +tier 3 +Fortune I, 1.50% useful | +`scroll_guard_help`, `scroll_highlight` (research) |
| 4 | 1520 ticks (1:16) | Mana ≥ 40 | drops tier 1; +Infinity, Fortune II; 4.57% useful | — |
| 5 | 1220 ticks (1:01) | Mana ≥ 50 | drops tier 2; +Mending, Silk Touch, Fortune III, all the V's; 11.75% useful | — |

Cycle length from `MAX_ENCHANTMENT_TICKS / building.getBuildingLevel()`
(`EntityAIWorkEnchanter.java:80, 236`); gate from `getPrimarySkillLevel() < building.getBuildingLevel()
* MANA_REQ_PER_LEVEL` (`:85, 143`); table composition from §4.2; scroll gates from
`DefaultEnchanterCraftingProvider.java:417, 424, 435` — all **[VERIFIED]**.

**Do the numbers make sense?** Partly. The cycle scaling is generous and the table climb is real. The
Mana gate is not: it scales linearly with hut level while the only thing that raises Mana past the
housing cap is the drain, at +1 per drain per station per day. A level-5 tower demands Mana 50, which
means roughly 45 successful drains for a fresh citizen (initial skills roll 1..happiness-1,
`CitizenSkillHandler.java:48-65`) **[VERIFIED]** — with the caveat that §5.1 means those 45 are paid
once and never again.

### 7.2 Throughput, and the input that decides everything

Best case at hut level 5, ignoring supply: cycles are 1220 ticks and can only *start* inside the
0–6000 window (§2.1), so **five books per in-game day**. At hut level 1 the cycle is 6020 ticks —
marginally longer than the entire window — so **one book per day at most, and only if the citizen is
at its bench at dawn and the cycle is allowed to run past noon, which it is, because the time gate
lives in `decide()` and `ENCHANT` never re-enters it**.

Supply: raids every ~14 nights; a raid wave of a dozen raiders at roughly 5% each plus a chief at
~16–26% yields on the order of **1–4 tomes per raid**, i.e. **≈0.1–0.3 tomes per in-game day**
**[UNVERIFIED]** — the per-kill drop rates are verified from the loot tables, but the number of
raiders per wave and the fraction of drops the colony actually recovers are not; confirming means
running a colony through several raid cycles and counting tomes into the warehouse.

Take the optimistic end, 0.3 tomes/day:

* Expected days to one **Mending** book at hut 5: `1 / (0.3 × 0.00206)` ≈ **1600 in-game days**.
* Expected days to any book from the "worth having" set at hut 5: `1 / (0.3 × 0.1175)` ≈ **28
  in-game days**.
* At hut 3: `1 / (0.3 × 0.0150)` ≈ **220 in-game days** for one useful book.
* At hut 1 or 2: **never**.

A level-5 enchanter therefore runs at roughly **6% of its own capacity** (0.3 of 5 books/day). Which
means the two things a level upgrade buys — a faster cycle and a better table — split badly: **the
cycle speed is worthless** (the worker is idle waiting for tomes regardless) and only the table
matters.

### 7.3 The experience trap

`enchant()` awards no experience. The only `addExperience` calls on this worker's path are
`XP_PER_DRAIN = 10` per drain (`EntityAIWorkEnchanter.java:413`) and `count / 2.0` on completing a
crafting *request* (`AbstractEntityAICrafting.java:674, 721`) **[VERIFIED]**;
`incrementActionsDoneAndDecSaturation` only decrements saturation and bumps the action counter
(`AbstractEntityAIBasic.java:1798-1802`) **[VERIFIED]**.

Combined with §5.1: once the enchanter passes its Mana gate it stops draining, and since book
production gives nothing, **its citizen level freezes** until the player sends it a scroll order.
A colony that never orders scrolls will have an enchanter that has not levelled since its first week.

### 7.4 The verdict, plainly

**Build the tower. Take it to level 3. Stop.**

* Levels 1–3 cost the usual hut resources and buy the four scrolls, which are the enchanter's real
  product: colony teleport, area teleport, guard summon and the resource highlighter are all things a
  player uses and nothing else in the mod makes.
* Level 3 → 5 buys a book table that goes from 1.5% to 11.75% useful, applied to an input the colony
  receives about once every four days, and a cycle speed the colony cannot feed. It also triples the
  Mana gate, meaning more of the worker's early life spent walking between huts playing a beam that
  does nothing.
* **Employing a citizen on it is defensible only if the colony wants scrolls.** As a book factory it
  is not: the same citizen on any production hut returns a measurable output every day, and the
  player's own enchanting table out-produces a maxed enchanter's book output by orders of magnitude
  for the cost of three levels and some lapis.

---

## 8. Failure modes

| Situation | What the code does | `file:line` | What the player sees |
|---|---|---|---|
| **No stations selected** | `NO_WORKERS_TO_DRAIN_SET` blocking interaction, then `IDLE` | `:147-155`; validator `InteractionValidatorInitializer.java:259-260` | Correct: a blocking chat bubble that clears when a station is added. The one failure mode that is handled well. |
| **All stations already drained today** | `getRandomBuildingToDrainFrom()` returns null → `IDLE` | `:170-174`, `EnchanterStationsModule.java:106-114` | Worker stands in the tower; no message. Resets at dawn (`:148-154`). |
| **No book** | async request for 1 `minecraft:book`, `IDLE` | `:157-167` | Request appears in the colony list. Fine. |
| **No ancient tome** | async request for 1 tome, `IDLE` | `:192-203` | Request appears; usually unfillable for days. No message explains why. |
| **Target hut destroyed** | `getBuilding(pos)` null → `resetDraining()`, entry dropped from the list, `IDLE` | `:314-319` | Self-healing on the first attempt. Good. |
| **Target citizen not loaded** | `citizenToGatherFrom = null`, retry | `:349-353` | Unreachable in practice — the target is the enchanter itself, which is loaded by definition. |
| **Target too far** | 60 tries of `incrementWaitingTicks` at 10-tick spacing (≈30 s), then abandon | `:355-367`, `JobEnchanter.java:112-120` | Unreachable in practice, same reason. |
| **Building racks full** | `fullfillRecipeAndCopy` returns null before consuming the tome; `loot == null` → no mana, no action, `IDLE`; `decide()` sends it straight back to `ENCHANT` | `:263-274`, `RecipeStorage.java:644-663, 700-705` | **Silent.** The worker burns a full 6000/hutLevel-tick cycle, produces nothing, and repeats. No interaction, no status. Nothing is lost but the day. |
| **Tome recipe disabled in the GUI** | `ancientTomeCraftingDisabled = true` skips the tome fetch, `decide()` still returns `ENCHANT` (`:206`), `enchant()` finds no fulfillable recipe (disabled recipes are skipped at `AbstractCraftingBuildingModule.java:830`) and returns `START_WORKING` | `:179-206, 229-234` | **Permanent `START_WORKING` ↔ `ENCHANT` ping-pong.** The documented way to turn book production off leaves the worker looping forever, and it will not drain either once its Mana is above the gate. |
| **Afternoon** | `decide()` returns `IDLE`, `hasWorkToDo()` returns it to `START_WORKING` | `:132`, `:210-213`, `AbstractEntityAICrafting.java:162` | **Standing still, status "Working", every afternoon.** |
| **Interrupted mid-enchant** (chunk unload, restart, going to eat is impossible but sleep is) | `progressTicks` is a plain field and is **not persisted**; `JobEnchanter.serializeNBT` saves only `posToDrainFrom` and `waitingTicks` | `:100`, `JobEnchanter.java:75-85` | Progress resets to zero. No tome lost — the input is only consumed on completion (`RecipeStorage.java:709-780`) — but at hut level 1 a whole day's work evaporates. |
| **The enchanter itself being drained** | It is always the one being drained (§2.2). It gains its own +1 Mana and loses one of its own books. | `:324, 397-412` | Nothing. Which is the problem. |
| **Inventory full** | `getActionsDoneUntilDumping()` is 1 (`:216-219`), so a dump follows every single action; `inventoryNeedsDump` is gated on `canBeInterrupted()` so it cannot fire during `ENCHANT` | `:216-219`, `AbstractEntityAIBasic.java:467-474` | Fine. The worker is already at its bench. |
| **Colony has no other workers at all** | Irrelevant. The station list can be empty (handled), but a populated list of empty huts drains just as "well" as a populated one. | — | — |

---

## 9. Data and recipes: what is data-driven and what is not

**Data-driven, regenerated by `runDatagen`:**

* The five book tables, `gen/loot_table/recipes/enchanter{1..5}.json` — a datapack can replace them
  wholesale, and that is the supported way to change what the enchanter makes.
* The five tome recipes and the four scroll recipes, `gen/crafterrecipes/enchanter/`.
* `gen/enchantment/raider_damage_enchant.json`.
* The hut craft recipe, its advancement, its block loot table, its recruitment entry.

**Hardcoded in Java, not overridable:**

* The **list of enchantments and levels** that goes into those tables lives in
  `DefaultEnchanterCraftingProvider.java:61-370` as ~230 hand-written `enchantedBook(key, level)
  .setWeight(n)` calls **[VERIFIED]**. The tables are data; their *content* is source.
* The mana gate constant `MANA_REQ_PER_LEVEL = 10` (`:85`), the cycle length
  `MAX_ENCHANTMENT_TICKS = 300` (`:80`), the drain length `MAX_PROGRESS_TICKS = 60` (`:75`), the
  drain range `MIN_DISTANCE_TO_DRAIN = 10` (`:70`), `XP_PER_DRAIN = 10` (`:90`), the drain's
  enchanting cost `getSecondarySkillLevel() > 50 ? 2 : 1` (`:406`) and the attempt count
  `getSecondarySkillLevel() / 5.0` (`:398`) — **[VERIFIED]**. **There is no config entry for any of
  them**; the only enchanter-adjacent server config is `researchresetcost`, whose default happens to
  be one ancient tome (`ServerConfiguration.java:357`) **[VERIFIED]**.
* The 6000-tick work window (`:132`).

**Upstream assets:** none in the repository. The enchanter's GUI layout
(`minecolonies:gui/layouthuts/layoutenchanter.xml`, referenced at
`EnchanterStationModuleWindow.java:62`), its module icon
(`minecolonies:textures/gui/modules/entity.png`, `EnchanterStationsModuleView.java:80`), its citizen
textures, its blueprints and every enchanter language key are **references to runtime-fetched assets**
**[VERIFIED]** — `26.3/src/main/resources/assets/minecolonies/` contains only `lang/` (port-added keys
only; a case-insensitive grep for "enchant" in `en_us.json` returns nothing), `items/scepterclaim.json`,
`models/item/fieldstick.json`, two textures and `sounds.json` **[VERIFIED]**. That is a file *pointing
at* upstream art, which the rules allow, and not a copy of it, which they do not.

**Mixins and access wideners:** the enchanter needs neither, and none of the fixes below would.
`minecolonies.mixins.json` lists exactly one client mixin (`PackRepositoryMixin`) unrelated to this
**[VERIFIED]**; `minecolonies.accesswidener` (127 lines) mentions nothing in
`net.minecraft.world.item.enchantment` **[VERIFIED]**. Every vanilla member a fix would need —
`EnchantmentHelper.getEnchantmentsForCrafting`, `EnchantmentHelper.getComponentType`,
`EnchantmentTags.IN_ENCHANTING_TABLE`, `EnchantmentHelper.enchantItem` — is already `public`
(`v/.../EnchantmentHelper.java:79, 86, 512`; `v/tags/EnchantmentTags.java:18`) **[VERIFIED]**.

---

## 10. Findings, ranked

Sizes: **S** under ~150 lines, **M** ~150–400, **L** 400+.

### F1 — The enchanter drains itself; the drain walk is theatre. **S**

*Code:* `EntityAIWorkEnchanter.java:324` iterates `getModuleForJob().getAssignedEntities()`, which is
the enchanter's own work module (`AbstractEntityAIBasic.java:796-799` → `AbstractJob.java:151-155`).
The building it walked to, `buildingWorker` (`:308`), is used for the walk and the null check and
nothing else. **[VERIFIED]**

*In play:* the enchanter walks to a hut, plays a 30-second particle beam from itself to itself,
consumes one of its own books, gives itself a Mana level, and leaves. The worker in the hut is
unaffected in every respect. The station list the player curates decides only where the enchanter
walks.

*Why it is wrong:* the whole feature — the module, its GUI, its packet, its NBT, its daily reset —
exists to choose whom to drain, and the choice is discarded.

*Fix:* enumerate `buildingWorker`'s worker modules instead, excluding the enchanter itself:

```java
final List<AbstractEntityCitizen> workers = new ArrayList<>();
for (final WorkerBuildingModule module : buildingWorker.getModulesByType(WorkerBuildingModule.class))
{
    for (final Optional<AbstractEntityCitizen> citizen : module.getAssignedEntities())
    {
        citizen.filter(c -> c != worker).ifPresent(workers::add);
    }
}
```

The existing empty-list branch (`:336-340`) already handles an unmanned hut. Server-side only; no
client work; no access widener.

**Balance:** yes. The drain currently always succeeds; afterwards it will fail whenever the target
worker is out on a job more than 10 blocks from its hut, which for a lumberjack or a miner is most of
the day. Expect the Mana ramp (§7.1) to take substantially longer. Ship it with F5, or with a
relaxed `MIN_DISTANCE_TO_DRAIN`.

### F2 — The mana cost of a book is always zero. **S**

*Code:* `EntityAIWorkEnchanter.java:287` reads `DataComponents.ENCHANTMENTS` off an
`ENCHANTED_BOOK`, whose enchantments live in `STORED_ENCHANTMENTS`
(`v/.../EnchantmentHelper.java:86-88`, `v/world/item/Items.java:2227-2234`,
`v/core/component/DataComponents.java:455-467`). **[VERIFIED]**

*In play:* `incrementLevel(Skill.Mana, -0)` at `:271`. The enchanter passes its gate once and then
enchants forever without draining, without consuming books, without visiting anyone, and without
gaining experience (§7.3).

*Fix:* one line —

```java
for (Object2IntMap.Entry<Holder<Enchantment>> entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet())
```

which is exactly what `recordEnchantmentStats` two methods below already does (`:452`) and what
`ItemStackUtils#getMaxEnchantmentLevel` does (`ItemStackUtils.java:308`).

**Balance:** yes, and a large one. Existing colonies have enchanters sitting on a Mana level they
never pay down; after this they will drop below the gate on their first Mending or Sharpness V and
return to the drain loop. That is the designed behaviour, but it is not the behaviour any save in
this fork has ever had. Ship it as a stated balance change, not as a bug fix.

### F3 — The enchanter does nothing every afternoon. **S**

*Code:* `EntityAIWorkEnchanter.java:132`, `craftState != START_WORKING`, against
`getNextCraftingState()` which returns `IDLE`/`INVENTORY_FULL`/`QUERY_ITEMS`/`GET_RECIPE` and never
`START_WORKING` (`AbstractEntityAICrafting.java:310-329`). **[VERIFIED]**

*In play:* from game time 6000 to bedtime (~11000) the enchanter stands still with status "Working".
Roughly 45% of its work day.

*Fix:* `craftState != IDLE`. One token. The line then reads as it was evidently meant to — mornings
for draining and enchanting, afternoons for the player's crafting orders, and no dead time either
way. An alternative is to drop the time gate entirely, which would make scroll orders pre-empt book
production; the `!= IDLE` form keeps upstream's split.

**Balance:** yes — it roughly doubles the enchanter's working hours. Both F2 and F3 push in opposite
directions on output, so they are best evaluated together.

### F4 — 88% of what a level-5 enchanter makes is unusable, and nothing consumes any of it. **M**

*Code:* the tables (`DefaultEnchanterCraftingProvider.java:61-370`) and the consumer census in §6.
**[VERIFIED]**

*In play:* the warehouse fills with single-slot, unstackable enchanted books forever. One research
consumes one.

*Fix:* two independent halves. (a) Give the colony a use — an anvil-style "apply book to worker
gear" step, or let the blacksmith consume a book as a recipe input. (b) Rebalance the tables so that
levels 1–2 are not 0% useful. Either is **M**; both are balance changes; (a) needs new AI or building
code, not just datagen.

*Cheaper mitigation:* the min-stock module already lets the player cap what the hut keeps, and the
existing recipe toggle lets them switch tome enchanting off. Documenting that is **S**.

### F5 — The drain's enchanting is a no-op for books and unfiltered for everything else. **S**

*Code:* `EntityAIWorkEnchanter.java:406`. The return value of `EnchantmentHelper.enchantItem` is
discarded, and `enchantItem` returns a **new** stack when the input is a book
(`v/.../EnchantmentHelper.java:529-531`); the pool is `Optional.empty()`, which means the entire
enchantment registry rather than `#minecraft:enchantment/in_enchanting_table`
(`v/.../EnchantmentHelper.java:519-524` vs `v/world/inventory/EnchantmentMenu.java:187-189`).
**[VERIFIED]**

*In play:* nothing, today, because of F1. After F1 it becomes visible: books in a worker's inventory
would be enchanted into a copy that is thrown away, and non-book gear could in principle pick up
`raider_damage_enchant` or Soul Speed, neither of which a player's own table can produce.

*Fix:* assign the return back into the slot, and pass the table tag:

```java
final HolderSet<Enchantment> pool = world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                                      .get(EnchantmentTags.IN_ENCHANTING_TABLE).orElse(null);
final ItemStack result = EnchantmentHelper.enchantItem(worker.getRandom(), stack,
                             getSecondarySkillLevel() > 50 ? 2 : 1, world.registryAccess(),
                             Optional.ofNullable(pool));
if (result != stack)
{
    citizenToGatherFrom.getInventory().setStackInSlot(randomSlot, result);
}
```

All members public; no access widener. Note the interaction with hut equipment limits: a newly
enchanted tool raises `ItemStackUtils.getMaxEnchantmentLevel` and can push the item over the target
building's `getMaxEquipmentLevel` ceiling (`ItemStackUtils.java:285-315`) **[VERIFIED]**, at which
point the worker refuses to hold its own tool. Any fix here must exclude equipment the worker is
actually using, or cap the level.

**Balance:** yes.

### F6 — Turning book production off leaves the worker in a permanent two-state loop. **S**

*Code:* `:179-206` sets `ancientTomeCraftingDisabled` and then returns `ENCHANT` anyway; `enchant()`
skips disabled recipes and returns `START_WORKING` (`:229-234`,
`AbstractCraftingBuildingModule.java:830-833`). **[VERIFIED]**

*Fix:* return `IDLE` (or fall through to the drain branch) when the tome recipe is disabled. A few
lines.

### F7 — A full building silently costs a whole production cycle, repeatedly. **S**

*Code:* `enchant()` runs the full `6000/hutLevel`-tick animation before calling
`fullfillRecipeAndCopy`, which fails its `checkForFreeSpace` precondition and returns null
(`:236-274`, `RecipeStorage.java:644-663, 700-705`). **[VERIFIED]**

*Fix:* hoist the free-space check into `decide()` and raise an interaction when it fails. **S**,
server-side, needs one new translation key (a client-visible string, so the language file needs the
key added — a port-added key in `en_us.json`, which this repository does own).

### F8 — Book production grants no experience; a maxed enchanter's level freezes. **S**

*Code:* `enchant()` calls only `incrementActionsDoneAndDecSaturation()` (`:273`), which awards no XP
(`AbstractEntityAIBasic.java:1798-1802`). **[VERIFIED]**

*Fix:* one `worker.getCitizenExperienceHandler().addExperience(...)` call in `enchant()`, scaled like
the crafter's. **Balance change.**

### F9 — The enchant state cannot be interrupted to eat, for up to 6000 ticks. **S**

*Code:* `AIWorkerState.java:604` declares `ENCHANT(false)`; `canBeInterrupted()` is
`getState().isOkayToEat()` (`AbstractAISkeleton.java:128`); the cycle at hut level 1 is 6000 ticks.
**[VERIFIED]** *That the enchanter actually starves in practice is* **[UNVERIFIED]** — confirming
means watching a level-1 enchanter's saturation over several days on a running server.

*Fix:* either `ENCHANT(true)` (and accept that a hungry worker abandons a cycle), or cap
`MAX_ENCHANTMENT_TICKS / buildingLevel` well below the work window. **Balance change.**

### F10 — The station packet is unvalidated. **S**

*Code:* `EnchanterWorkerSetMessage.onExecute` (`:63-73`) stores whatever `BlockPos` arrives; the GUI
filters out the enchanter's own building type and buildings without a worker module
(`EnchanterStationModuleWindow.java:71-75`) but the server does not. **[VERIFIED]**

*In play:* low severity — a bogus position self-heals on the first drain attempt (`:314-319`), and a
valid-but-filtered position is harmless while F1 stands. Worth closing when F1 is fixed.

*Fix:* reject a position that does not resolve to a colony building carrying a `WorkerBuildingModule`,
or that is the enchanter's own. A handful of lines.

### F11 — The enchantment pool is frozen at the 1.21.1 list. **M**

*Code:* §5.4. 27 of 43 vanilla enchantments appear; the mace family and the newer additions do not.
**[VERIFIED]**

*Fix:* extend `DefaultEnchanterCraftingProvider` and re-run `runDatagen`. The provider is ~230
repeated builder lines already; adding a dimension without restructuring it would push it past 600.
Restructuring it into a `(enchantment, maxLevelPerTier)` table first would be the cheaper route and
would shrink the file. **Balance change.**

---

## 11. Things that are fine

* **The state machine wiring.** Two custom states, correct tick periods, correct `isOkayToEat` flags,
  and `hasWorkToDo()` overridden so the worker never parks in `IDLE`.
  (`EntityAIWorkEnchanter.java:110-113, 210-213`, `AIWorkerState.java:599, 604`)
* **The `NO_WORKERS_TO_DRAIN_SET` interaction.** Raised at the right moment, and paired with a
  validator that clears it automatically the instant a station is added
  (`:149-153`, `InteractionValidatorInitializer.java:259-260`). This is the model the other failure
  modes should follow.
* **Null safety on the walk.** `walkToBuilding(null)` returns `true` rather than throwing
  (`AbstractEntityAIBasic.java:941-948`), so the destroyed-building path at `:314-319` reaches its
  null check and self-heals the station list instead of crashing the AI.
* **`recordEnchantmentStats`** (`:444-472`). It uses the correct component accessor, formats a
  multi-enchantment book into a comma-separated list of proper names via
  `Enchantment.getFullname`, and routes through the `Component` overload of `trackStatByName`
  (`StatsUtil.java:114-123`), which resolves to a display string rather than a debug dump.
* **The port's time-source change.** `Level#getDayTime` → `Level#getOverworldClockTime`
  (`WorldUtil.java:183-187`) is the right mapping for 26.3
  (`v/world/level/Level.java:880-882, 983`), the comparison semantics are unchanged from upstream,
  and the port left a note saying why.
* **`raider_damage_enchant`.** The datagen produces JSON that matches the snapshot-10 enchantment
  codec field for field, its `#minecolonies:raiders` predicate is intact, it is correctly kept out of
  `#minecraft:enchantment/in_enchanting_table`, and it still renders on tooltips despite being
  outside `tooltip_order`.
* **Recipe tiering.** `minBuildingLevel(N).maxBuildingLevel(N)` on the tome recipes means exactly one
  loot table is live per hut level, with no overlap and no silent fallback
  (`DefaultEnchanterCraftingProvider.java:400-407`).
* **Loot-table climb.** Level 4 drops the tier-1 block and level 5 drops tier-2, so a maxed tower
  genuinely stops producing the worst books rather than merely diluting them.
* **No assets, no mixins, no wideners.** The enchanter needs none, and neither would any fix above.
* **Output routing.** Books land in `building.getHandlers()` and the action counter is 1, so a dump
  follows immediately and nothing accumulates in the worker's pockets
  (`:216-219`, `AbstractBuilding.java:1314-1337`).

---

## 12. What a player would notice first

Ranked by how quickly it registers in ordinary play.

1. **"My enchanter is standing still."** Every afternoon, all afternoon, status "Working". This is
   the first thing anyone reports. **F3, S.**
2. **"The beam does nothing."** The enchanter walks to a hut, throws a spectacular particle stream at
   a worker for thirty seconds, and that worker's skills, tools and mood are exactly as they were.
   Players who check will find no XP moved anywhere. **F1, S.**
3. **"It never asks for books any more."** Early on the enchanter constantly wants `minecraft:book`;
   after a week it stops forever and no longer visits anyone. That is F2 presenting as a feature
   quietly switching itself off. **F2, S.**
4. **"There are ninety enchanted books in the warehouse and nothing uses them."** Each one its own
   rack slot, none of them stackable against each other. **F4, M.**
5. **"It never has an ancient tome."** The request sits unfilled between raids, which is most of the
   time, and nothing in the UI explains that raiders are the only source. **§6, documentation-only
   fix, S.**
6. **"A level-1 enchanter makes one book a day, and it takes the whole morning."** 6000 ticks per
   book against a 6000-tick window. **F9/§7.1.**
7. **"My level-5 enchanter is still level 12."** No experience from its main activity. **F8, S.**
8. **"I turned tome enchanting off and now it does nothing at all, ever."** **F6, S.**

Items 1, 2 and 3 are three one-line changes between them, and together they are the difference
between a profession that visibly works and one that visibly does not.

---

## 13. Prior work

There is **no earlier audit of the enchanter** in this repository: `26.2/audit/` contains twenty-one
documents and none is about this profession; `git log --oneline --all | grep -iE "enchant"` returns
nothing **[VERIFIED]**. Four earlier findings touch it in passing.

| Earlier finding | Where | Status |
|---|---|---|
| "Every level-1..5 enchanter recipe has `ENCHANTED_BOOK` as a secondary output, drawn from `minecolonies:recipes/enchanter{1..5}`… **Nothing in the mod ever consumes an enchanted book**" | `26.2/audit/WAREHOUSE-CLEANUP.md:98` | **Still holds, and is sharper than stated.** Re-verified on 26.3: one research consumes one book (`DefaultResearchProvider.java:1332`), the recruitment cost is paid from the player's inventory, and there is no recipe input. One correction: the declared `secondaryOutputs` on those recipes is **never emitted** at all, because `RecipeStorage#insertCraftedItems` only adds secondaries when the primary output is non-empty (`RecipeStorage.java:797-816`, gate at `:802` and `:815`) — the books come solely from the loot table. |
| "enchanter → Needs another worker with levels to drain — `NO_WORKERS_TO_DRAIN_SET`, `EntityAIWorkEnchanter.java:150-153`" | `26.2/audit/FREE-MODE-AUDIT.md:225` | **Half right, and the half that is wrong is F1.** The interaction and its line range are correct (26.3: `:149-153`). But the enchanter does **not** need another worker with levels: it drains itself, so a station list full of empty huts works exactly as well as a staffed one. The blocking interaction fires on an *empty list*, not on an unstaffed target. |
| "`MATERIAL_SETTINGS` … library, florist, **enchanter**, hospital, alchemist" and the recommendation to remove those free-mode settings producers | `26.2/audit/FREE-MODE-AUDIT.md:41, 478-486` | **Fixed / applied.** `MATERIAL_SETTINGS` no longer exists in either the 26.2 or the 26.3 tree **[VERIFIED]** by grep, and the enchanter's module list carries no settings producer at all (`ModBuildingsInitializer.java:433-437`). The predicted consequence — the enchanter losing its settings tab entirely — is now the shipped state. |
| "`EntityAIWorkUndertaker` and `EntityAIWorkEnchanter` are byte-for-byte identical to 1.21.1" | `26.2/audit/WORKER-AUDIT.md:452` | **Still holds on 26.3, with a four-line exception.** Two `swing` → `swingForAttack` renames (§0.2). |

Nothing in `docs/studies/` audits the enchanter. `api-needs.md:262, 1113` and `progression.md:352,
444, 1073` discuss the hut *equipment and enchantment-level ceiling*, which is a different mechanism
(`ItemStackUtils.verifyEquipmentLevel`) and is untouched by anything here — except as the hazard noted
under F5.

---

## 14. What I could not verify

* **Anything requiring a running server.** No probe was built and nothing was executed. Every claim
  above is a source reading of this tree and of `/opt/mc-src-26.3-snapshot-10`. In particular:
  * the tome supply rate per raid (§7.2) — verified drop *weights*, not raid composition or how much
    of a raider's drop the colony recovers;
  * whether a level-1 enchanter actually starves during its 6000-tick uninterruptible cycle (F9);
  * the exact enchantment outcomes of the drain path's cost-1/2 roll (§4.4).
  All three would be settled by a dedicated server with a scripted colony and a few in-game weeks.
* **Whether `lunge` postdates 1.21.1** (§5.4). It exists in snapshot-10 and appears nowhere in this
  repository; placing it on a version timeline would need a 1.21.1 vanilla tree, which is not
  available here and which the evidence rule forbids substituting for.
* **The client half.** `EnchanterStationModuleWindow`, `TileEntityEnchanterRenderer` and the two
  citizen models were read for structure only. The repository's own README states that nothing about
  rendering or input has been exercised in play on this branch, and this audit did not change that.
  The particle messages (`CircleParticleEffectMessage`, `StreamParticleEffectMessage`) were read at
  the call site only, not through their client handlers.
* **Whether the `IDLE` ↔ `START_WORKING` afternoon spin has a measurable server cost.** It runs
  `decide()` at most every 5 ticks per enchanter, which is one building per colony, so it is almost
  certainly noise — but that is inference, not a profile.
