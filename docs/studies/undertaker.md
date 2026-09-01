# The undertaker, the grave, and everything that happens after a citizen dies

Research and audit only. Date: 2026-08-28. Tree: `26.3/` on branch `26.3`, targeting Minecraft
**26.3-snapshot-10**. **No game code was written or changed**; the only file this study touches is
itself.

Scope: the whole death pipeline, not just the worker. What `EntityCitizen#die` does; when a grave is
created and when it is not; what the grave holds and how long it holds it; how the undertaker finds,
empties, digs and buries it; the resurrection roll and everything that feeds it; what survives of a
citizen across death and resurrection; the graveyard building, its capacity, its levels and its
research branch; and what the worker does at night, in the rain, during a raid, when idle and when
the graveyard is full.

---

## Verdict in one page

The undertaker works. He is one of the few MineColonies service workers whose AI does what its
comments say it does — no dead branch, no silent no-op of the kind the enchanter is built out of
(`docs/studies/enchanter.md` §F1–F3). What is wrong with him is **arithmetic and edge cases**, and
the two that matter most are on either side of the same line of code.

Ranked by what a player actually notices:

1. **Every death duplicates the dead citizen's boots.** `GraveManager.java:305-312` loops over
   `EquipmentSlot.values()` and calls `getArmorInSlot` for every slot where `isArmor()` is true. In
   26.3 vanilla that set is `FEET, LEGS, CHEST, HEAD, BODY`, and `BODY.getIndex()` is **0** — the
   same index as `FEET` (`v/world/entity/EquipmentSlot.java:15-20,73-75`). The citizen's armour
   array is four entries indexed by that same `getIndex()` (`InventoryCitizen.java:56,312-319`), so
   the boots are read twice and inserted into the grave twice. §5.1. **S, and it is a dupe: fixing
   it removes free items from the game.**

2. **A successful resurrection duplicates the whole worn armour set.** The same loop copies armour
   into the grave without ever clearing the armour slots, and the citizen NBT that the grave stores
   for the resurrection is serialised **afterwards** (`GraveManager.java:305-312` then `:324`), so it
   still contains the armour. The undertaker hauls one set to the warehouse and the resurrected
   citizen walks out wearing another. §5.2. **S.**

3. **The resurrection chance is capped at 2.5%, and a level-5 graveyard reaches that cap on its
   own.** `getResurrectChance` sums building level × 0.005, Mana × 0.00125 and research, then clamps
   to `0.025 + mysticalSiteLevel × 0.005` (`EntityAIWorkUndertaker.java:375-391`,
   `UndertakerConstants.java:26-41`). Without a Mystical Site the ceiling is 2.5%; graveyard level 5
   contributes exactly 0.025 by itself. **Both Resurrection Chance researches and every point of the
   undertaker's Mana skill buy nothing at all** in a colony without a Mystical Site. §6.1.

4. **The undertaker's Mana level — which sets the chance term *and* how long the ritual takes — is
   fixed at the moment you hire him.** He earns 7.5 XP per grave into his **primary** skill
   (Strength) and 0.05 × modifiers into the shared pool that feeds Mana at half rate
   (`EntityAIWorkUndertaker.java:260`, `AbstractEntityAIInteract.java:237`,
   `CitizenExperienceHandler.java:104-115`). Moving Mana from 1 to 25 needs about **21,000 graves**.
   §6.3.

5. **He does not work during a raid, and the grave clock does.** Every non-guard citizen is put into
   `CitizenAIState.SLEEP` for the whole of a raid (`CitizenAI.java:163-168`) — which is exactly when
   the graves appear. A grave lives 24,000 ticks (20 real minutes) by default, ticked from the colony's
   500-tick slow beat regardless of what anyone is doing (`AbstractTileEntityGrave.java:22`,
   `TileEntityGrave.java:244-267`, `Colony.java:622`). Rain stops him too
   (`CitizenAI.java:269-278`). §8.2.

And the honest answer to "is he worth building": **yes, at level 1, for the gear.** The resurrection
is a lottery — at the default 2.5% cap and one death per raid it is roughly **560 game days per
citizen recovered** (§9.2) — but automatic recovery of a dead colonist's inventory is worth a citizen
slot on its own, and it is the only thing in the game that does it. Level him to **3** if you want
Grave Decay I, which is the research that actually addresses the failure mode that loses gear. Level
5 is for the two researches that do nothing unless you also build a Mystical Site.

Everything below is ranked by how much it changes play. Fix sizes are **S** (< ~150 lines) unless
marked. Fifteen of the sixteen findings below are S; one is M. **Every defect in this study is
upstream's** — the port changed exactly two `swing` → `swingForAttack` calls in the AI and one
`dropItemHandler` → `dropCitizenInventory` in `GraveManager` (§0.2), and that one change is a fix.

---

## Evidence standard

Same as `docs/studies/enchanter.md` and `docs/studies/guards.md`:

* **[VERIFIED]** — I opened the file and the cited `file:line` says what I claim, or I ran the thing
  and watched the output.
* **[UNVERIFIED]** — inference I did not confirm. Each one names what would confirm it.

Paths: `26.3/src/main/java/com/minecolonies/` is abbreviated to `mc/`;
`26.3/src/main/generated/data/minecolonies/` to `gen/`. Vanilla sources are read from
**`/opt/mc-src-26.3-snapshot-10/net/minecraft/`**, abbreviated to `v/`, and **no other vanilla tree
is cited anywhere in this document**. The upstream 1.21.1 snapshot used for the "ours or theirs"
question is read out of git history (`git show b104817d^:1.21.1/...`), the tree `UPSTREAM-SYNC.md`
documents as byte-identical to upstream before it was removed on the 26.3 branch.

**Nothing here was run.** No build, no server, no world. Every number is arithmetic over cited
constants or a count taken from a file on disk. Where a claim would need a running server to settle,
it is marked [UNVERIFIED] and says what would settle it.

**Line numbers, and a hazard.** Three other agents were working in this checkout while this was
written, and one of them holds `mc/api/util/InventoryUtils.java`, `mc/core/entity/citizen/EntityCitizen.java`,
`mc/core/entity/ai/workers/CitizenAI.java` and `mc/core/colony/managers/GraveManager.java` open. Every
citation below was re-resolved against the working tree at the end of this study, and at that moment
`git diff` reported no content change in any of those four files. A reader who finds a cited line off
by a few should search for the quoted code rather than trust the number.

**Blueprints.** The 26.3 tree carries no blueprints — they are fetched at runtime
(`26.3/src/main/resources/assetfetch/manifest.json`). The graveyard capacity figures in §7.1 were
counted out of the blueprints still present on the **26.2** branch, which are upstream ARR assets.
They were read for their numbers only; **no upstream asset is copied into this repository or into
anything this study proposes.**

---

## 0. What I read, and what I compared against

### 0.1 Read end to end

`mc/core/entity/ai/workers/service/EntityAIWorkUndertaker.java` (530),
`mc/core/colony/jobs/JobUndertaker.java`,
`mc/core/colony/buildings/workerbuildings/BuildingGraveyard.java` (226),
`mc/core/colony/buildings/modules/GraveyardManagementModule.java` (206),
`mc/core/colony/buildings/moduleviews/GraveyardManagementModuleView.java`,
`mc/core/client/gui/modules/building/GraveyardManagementWindow.java`,
`mc/core/colony/managers/GraveManager.java` (336),
`mc/core/colony/GraveManagerView.java`,
`mc/api/colony/managers/interfaces/IGraveManager.java`,
`mc/api/colony/GraveData.java`, `mc/api/colony/IGraveData.java`,
`mc/api/util/constant/UndertakerConstants.java`,
`mc/api/blocks/AbstractBlockMinecoloniesGrave.java`,
`mc/api/blocks/AbstractBlockMinecoloniesNamedGrave.java`,
`mc/api/blocks/types/GraveType.java`,
`mc/core/blocks/BlockMinecoloniesGrave.java`, `mc/core/blocks/BlockMinecoloniesNamedGrave.java`,
`mc/api/tileentities/AbstractTileEntityGrave.java`, `mc/core/tileentities/TileEntityGrave.java`,
`mc/api/inventory/container/ContainerGrave.java`,
`mc/core/placementhandlers/GravePlacementHandler.java`,
`mc/core/placementhandlers/NamedGravePlacementHandler.java`,
`mc/api/advancements/UndertakerTotemTrigger.java`.

Read in part, for the machinery the undertaker sits on:
`mc/core/entity/citizen/EntityCitizen.java` (`die`, `dropEquipment`, `getAllSlots`,
`getItemBySlot`, `hurtServer`), `mc/core/colony/CitizenData.java` (`serializeNBT`,
`serializeToView`, `onResurrect`, family fields),
`mc/core/colony/managers/CitizenManager.java` (`resurrectCivilianData`, `removeCivilian`,
`updateCitizenMourn`), `mc/core/colony/managers/CitizenAging.java` (`killAway`),
`mc/core/entity/ai/workers/CitizenAI.java` (`calculateNextState`, `shouldEat`),
`mc/core/entity/ai/workers/AbstractEntityAIBasic.java` (targets, dump, `walkWithProxy`,
`rescueIfStuck`, skill accessors), `mc/core/entity/ai/workers/AbstractEntityAIInteract.java`
(`mineBlock`), `mc/core/entity/ai/workers/util/StuckRescue.java`,
`mc/core/entity/ai/minimal/EntityAIMournCitizen.java`,
`mc/api/inventory/InventoryCitizen.java`, `mc/api/util/InventoryUtils.java`,
`mc/api/util/BlockPosUtil.java` (`findAround`),
`mc/core/entity/citizen/citizenhandlers/CitizenSkillHandler.java`,
`mc/core/entity/citizen/citizenhandlers/CitizenExperienceHandler.java`,
`mc/core/util/ExperienceUtils.java`,
`mc/core/colony/Colony.java` (`worldTickSlow`),
`mc/core/colony/buildings/AbstractBuilding.java` (`buildingRequiresCertainAmountOfItem`),
`mc/core/colony/buildings/modules/BuildingModules.java:648-653`,
`mc/apiimp/initializer/ModBuildingsInitializer.java:605-613`,
`mc/core/generation/defaults/DefaultResearchProvider.java:712-789`,
`mc/core/generation/defaults/DefaultAdvancementsProvider.java:249-265`,
`mc/core/generation/defaults/DefaultBlockLootTableProvider.java`,
`mc/api/entity/ai/statemachine/states/AIWorkerState.java`,
`mc/api/entity/ai/statemachine/tickratestatemachine/TickingTransition.java`,
`mc/core/entity/ai/workers/util/BuildingStructureHandler.java:198-215`.

### 0.2 Compared against upstream

`diff` of every file in the list above against the 1.21.1 snapshot in git history. Of the seventeen
files that exist in both trees, **fifteen differ only in port mechanics** — `ResourceLocation` →
`Identifier`, `net.minecraft.util.Tuple` → `com.minecolonies.api.util.Tuple`, `compound.getList(k,
TAG)` → `getListOrEmpty(k)`, `@OnlyIn(Dist.CLIENT)` → `@Environment(EnvType.CLIENT)`, the 26.2
block-entity hook split. **[VERIFIED]** The two exceptions:

* `mc/core/entity/ai/workers/service/EntityAIWorkUndertaker.java` — **two lines**, both
  `worker.swing(...)` → `worker.swingForAttack(...)` (`:202`, `:330`). Nothing else in 530 lines.
  **[VERIFIED]**
* `mc/core/colony/managers/GraveManager.java:334` — upstream's `InventoryUtils.dropItemHandler` is
  this fork's `InventoryUtils.dropCitizenInventory`, with a comment saying why: that branch is the
  only exit that skips the armour loop, so it has to drop the worn pieces itself. **[VERIFIED]**
  This is a fix and it is ours.

**Consequence for this audit: every defect below is upstream's.** That does not make any of them
less real — the port ships them — but none is a regression introduced here, and every fix is a
divergence that has to be maintained.

### 0.3 Vanilla read

`v/world/entity/EquipmentSlot.java` (the enum in full),
`v/world/entity/LivingEntity.java` (`die` 1454-1473, `dropAllDeathLoot` 1501-1510,
`checkTotemDeathProtection` 1381-1412, `hurtServer` 1247-1256),
`v/world/item/component/DeathProtection.java`,
`v/core/component/DataComponents.java:210`,
`v/world/item/Items.java:2354-2356`,
`v/world/entity/item/ItemEntity.java:44,180`,
`v/world/level/block/state/BlockBehaviour.java:266-275,1005,1172-1177`,
`v/server/ReloadableServerRegistries.java:80-82`.

---

## 1. Where it lives

| Piece | File |
|---|---|
| Job | `mc/core/colony/jobs/JobUndertaker.java` (extends `AbstractJobCrafter`, though nothing crafts) |
| AI | `mc/core/entity/ai/workers/service/EntityAIWorkUndertaker.java` |
| AI states | `mc/api/entity/ai/statemachine/states/AIWorkerState.java:169` `EMPTY_GRAVE`, `:174` `DIG_GRAVE`, `:179` `BURY_CITIZEN`, `:184` `TRY_RESURRECT`, `:613` `WANDER` |
| Building | `mc/core/colony/buildings/workerbuildings/BuildingGraveyard.java` |
| Building registration | `mc/apiimp/initializer/ModBuildingsInitializer.java:605-613` — modules `GRAVEYARD_WORK`, `GRAVEYARD`, `STATS_MODULE`, and nothing else |
| Worker module | `mc/core/colony/buildings/modules/BuildingModules.java:650-653` — `WorkerBuildingModule(undertaker, Skill.Strength, Skill.Mana, false, (b) -> 1)` |
| Graveyard module | `mc/core/colony/buildings/modules/GraveyardManagementModule.java` + `moduleviews/GraveyardManagementModuleView.java` |
| GUI | `mc/core/client/gui/modules/building/GraveyardManagementWindow.java` (layout `minecolonies:gui/layouthuts/layoutgraveyard.xml`, a runtime asset) |
| Colony-wide grave registry | `mc/core/colony/managers/GraveManager.java`, view `mc/core/colony/GraveManagerView.java` |
| Grave data | `mc/api/colony/GraveData.java` — citizen name, job name, and the whole citizen NBT |
| Grave block / tile | `mc/core/blocks/BlockMinecoloniesGrave.java`, `mc/core/tileentities/TileEntityGrave.java` |
| Headstone block / tile | `mc/core/blocks/BlockMinecoloniesNamedGrave.java`, `mc/core/tileentities/TileEntityNamedGrave.java` |
| Grave container | `mc/api/inventory/container/ContainerGrave.java`, screen `mc/core/client/gui/containers/WindowGrave.java` |
| Placement | `mc/core/placementhandlers/GravePlacementHandler.java`, `NamedGravePlacementHandler.java` |
| Constants | `mc/api/util/constant/UndertakerConstants.java` |
| Research | `mc/core/generation/defaults/DefaultResearchProvider.java:712-789` |
| Advancements | `mc/core/generation/defaults/DefaultAdvancementsProvider.java:249-265` |
| Statistics | `mc/api/util/constant/StatisticsConstants.java:105-106` — `graves_dug`, `citizens_resurrected` |
| Citizen models | `mc/core/client/model/{Male,Female}UndertakerModel.java` |

Registration facts worth pinning down:

* Primary skill **Strength**, secondary skill **Mana**, **one** worker at every building level
  (`BuildingModules.java:652`, the `(b) -> 1` supplier) **[VERIFIED]**.
* Max building level **5** (`BuildingGraveyard.java:43,183-187`) **[VERIFIED]**.
* The building keeps **one shovel and two Totems of Undying** out of the worker's dump
  (`BuildingGraveyard.java:85-86`) **[VERIFIED]**.
* **No settings module and no crafting module.** The player's only controls are hiring and the
  read-only graveyard tab (`ModBuildingsInitializer.java:610-612`) **[VERIFIED]**.
* The graveyard is unlocked by the **Remembrance** research: Town Hall 2 and 8 bones
  (`DefaultResearchProvider.java:713-721`) **[VERIFIED]**.

---

## 2. What happens when a citizen dies

`EntityCitizen#die` (`mc/core/entity/citizen/EntityCitizen.java:1631-1721`) runs in this order
**[VERIFIED]**:

1. `raiderManager.onLostCitizen(...)`, then `citizenExperienceHandler.dropExperience()` (`:1637`).
2. **`this.remove(RemovalReason.KILLED)`** (`:1638`). Remember this line; §2.4 turns on it.
3. Unless the death was of old age or the citizen was a guard, a three-day colony-wide misery
   modifier (`:1646-1653`; the old-age exemption and its reasoning are this fork's, `:1640-1645`).
4. Death advancement, then family mourning for non-guards (`:1654-1659`).
5. The `DEATH` statistic (`:1661`).
6. **The grave** (`:1663-1679`):

```java
final BlockPos gravePos;
if (!isInvisible())
{
    if (citizenColonyHandler.getColonyOrRegister().isCoordInColony(level(), blockPosition()))
    {
        gravePos = ...getGraveManager().createCitizenGrave(level(), blockPosition(), citizenData);
    }
    else
    {
        gravePos = null;
        InventoryUtils.dropItemHandler(citizenData.getInventory(), level(), ...);
    }
}
else
{
    gravePos = null;
}
```

7. The chat announcement, with a hover link to the grave if there is one (`:1681-1706`).
8. `job.onRemoval()`, `citizenManager.removeCivilian(...)` — which strips the citizen out of every
   building's assignment module and clears his work orders (`CitizenManager.java:400-426`) (`:1708-1712`).
9. The colony event log and the `CitizenDiedModEvent` (`:1714-1718`).
10. `super.die(damageSource)` (`:1720`).

### 2.1 Does a grave always spawn? No — five ways it does not

| Situation | What happens | Where |
|---|---|---|
| Died **outside the colony claim** | No grave. The 27-slot main inventory is dropped on the ground. **Worn armour is destroyed** — `dropItemHandler` walks `getSlots()`, which is the main inventory only (`InventoryCitizen.java:163-166`). | `EntityCitizen.java:1670-1674` |
| Died **invisible** | No grave, **no drop at all**. The entire inventory and armour are destroyed silently, and the announcement has no "grave spawned" clause. The only thing that sets a citizen invisible is a respawn at a stored position with `spawnVisible` false (`CitizenData.java:1021-1025`) and the nether expedition (`EntityAIWorkNether.java:178,655`). | `EntityCitizen.java:1664,1676-1679` |
| Died **standing in lava** | `createCitizenGrave` returns `null` immediately with a chat warning. **Nothing is dropped** — this is the one exit from that method that neither places a grave nor drops the inventory. Fire-resistant items (netherite) are destroyed where a player's would have survived. | `GraveManager.java:260-265` |
| Died **in water** with no air-with-solid-floor within ±1 block and 16 blocks | Chat warning, then the whole inventory *including* worn armour is dropped at the death position — into the water. | `GraveManager.java:268-286,330-334` |
| Died **anywhere else** with no air-block-over-solid within ±10 y and 10 blocks (deep in a filled cave, in the void, buried) | Same: warning-less drop of everything at the death position. Below the world there is nothing solid anywhere, so a void death drops items into the void. | `GraveManager.java:289-293,330-334` |
| Died **of old age in an unloaded chunk** | `CitizenAging.killAway` runs the whole bookkeeping by hand — announcement, mourning, statistic, event, mod event, removal — but never touches the inventory. **Everything the citizen carried and wore is deleted with the citizen data.** This path is this fork's (`CitizenAging.java:289-340`); the comment at `:297-301` says the grave is lost and does not mention the gear. | `CitizenAging.java:306-340` |

`isCoordInColony` is a per-chunk claim test, not a radius (`Colony.java:1818-1828`) **[VERIFIED]** —
so "outside the claim" means outside the *chunk claim*, which for a guard on patrol or a lumberjack
at the edge of the work radius is a very short walk.

### 2.2 Where the grave actually goes

`createCitizenGrave` (`GraveManager.java:257-335`):

* If the block at the death position is `Blocks.LAVA`, give up (§2.1).
* If it is `Blocks.WATER`, look up to 10 blocks up for an `AirBlock`; if one is found, search
  `BlockPosUtil.findAround(world, pos, 1, 16, air && solid-below)` — **vertical range 1**
  (`:274`). The air it just found 1-10 blocks above is outside that search box, so for a citizen
  drowning in open water the search is over three y-levels of water and finds nothing. §11 F16.
* Otherwise `findAround(world, pos, 10, 10, air && solid-below)` (`:289`) — a ring search, centre
  first, then the four horizontal neighbours, then square rings of increasing radius at
  `verticalRange + 2 = 12` y-offsets (`BlockPosUtil.java:1033-1100`) **[VERIFIED]**.
* On success: place `ModBlocks.blockGrave`, move the citizen inventory in, copy the armour in
  (§5.1), apply the `GRAVE_DECAY_BONUS` research, build the `GraveData`, register the grave with the
  colony (`:296-328`).

The chosen position can be up to 10 blocks from the body and up to 10 blocks up or down, so the
grave routinely appears through a wall or a floor from where the citizen fell. The chat message
carries the exact coordinates on hover (`EntityCitizen.java:1698-1703`), which is how a player is
expected to find it. **[VERIFIED]**

Nothing checks that the chosen position is still inside the colony claim. A citizen dying one block
inside the claim border can be given a grave up to 10 blocks outside it **[VERIFIED by reading;
the consequence — that the undertaker will still walk to it, because the grave manager tracks
positions and not claims — is [UNVERIFIED] in play].**

### 2.3 What goes into the grave, and what is lost on the way

```java
if (!InventoryUtils.transferAllItemHandler(citizenData.getInventory(), graveEntity.getInventory()))
{
    InventoryUtils.dropItemHandler(citizenData.getInventory(), world, pos.getX(), pos.getY(), pos.getZ());
}
```
(`GraveManager.java:301-304`)

`transferAllItemHandler` moves stacks one at a time and **returns `false` at the first stack that
will not fit**, leaving the rest where they are (`InventoryUtils.java:2617-2633`) **[VERIFIED]**.
The fallback then drops whatever is left at the death position — correct, and it cannot duplicate,
because every stack that did move was removed from the source first.

The grave holds `DEFAULT_SIZE * 2 = 54` slots (`AbstractTileEntityGrave.java:42`,
`Constants.java:137`) against a citizen inventory of 27 (`InventoryCitizen.java:44`)
**[VERIFIED]**, so overflow only happens if the grave was somehow pre-filled. **Nothing is lost on
this path.**

### 2.4 `dropEquipment` never runs, and that is load-bearing

`EntityCitizen` overrides `dropEquipment(ServerLevel)` to drop the whole citizen inventory
(`:1745-1757`). It is dead code. `die()` calls `this.remove(RemovalReason.KILLED)` at `:1638`,
long before `super.die(...)` at `:1720`, and vanilla's `LivingEntity#die` opens with
`if (!this.isRemoved() && !this.dead)` (`v/world/entity/LivingEntity.java:1454`) **[VERIFIED]**.
So `dropAllDeathLoot` — and with it `dropEquipment` and `dropExperience` — is never reached for a
citizen.

This is upstream's ordering **[VERIFIED]** by diff, and removing it would immediately duplicate
every dead citizen's inventory: the grave already holds it. Three smaller things go with it, all
[VERIFIED by reading the vanilla method that is skipped]:

* `killer.awardKillScore` and `sourceEntity.killedEntity(...)` never run, so a player who kills a
  citizen gets no kill score and no looting-style hooks fire.
* `level.broadcastEntityEvent(this, (byte) 3)` never runs — the client never plays the death
  animation or particles. The entity simply vanishes, which is what `remove()` did a moment earlier.
* No wither rose from a wither kill.

None of that is worth changing. It is listed here because `dropEquipment` reads like live code and
is not.

---

## 3. The grave itself

### 3.1 The block

`BlockMinecoloniesGrave` (`mc/core/blocks/BlockMinecoloniesGrave.java`) **[VERIFIED]**:

* Hardness 1.5, resistance 5, two variants — `DEFAULT` and `DECAYED` (`GraveType.java:11-12`).
* **No collision box** (`:184-188` returns `Shapes.empty()`), so citizens and players walk through
  it; the visual box is the inner 0.1–0.9 cube (`:65,85-90`).
* Right-click opens the grave inventory to anyone with `Action.ACCESS_HUTS`, or to anyone at all if
  the grave is outside every colony (`:145-169`). A player can loot a grave by hand, and can also
  put items *into* it — `ContainerGrave` uses plain `SlotItemHandler`s with no insert filter
  (`ContainerGrave.java:59-120`) **[VERIFIED]**.
* Breaking it spills the inventory through `AbstractTileEntityRack#preRemoveSideEffects`
  (`mc/api/tileentities/AbstractTileEntityRack.java:136-145`) — this fork's fix, with the
  PORT-NOTE at `BlockMinecoloniesGrave.java:203-210` explaining that the 26.2 hook split moved the
  drop after the block entity was already gone. **[VERIFIED]** **Breaking a grave destroys the
  `GraveData` with it: the citizen can never be resurrected afterwards.**
* The block has **no loot table**. `DefaultBlockLootTableProvider` never registers it and
  `assetfetch` ships none, so `getDrops` resolves a missing key to `LootTable.EMPTY`
  (`v/world/level/block/state/BlockBehaviour.java:266-275`,
  `v/server/ReloadableServerRegistries.java:80-82`) **[VERIFIED]**. Breaking a grave yields no
  grave item, silently. That is the right outcome; the headstone gets there deliberately via
  `.noLootTable()` (`BlockMinecoloniesNamedGrave.java:53`) and the grave gets there by omission.

### 3.2 Decay

`TileEntityGrave#onColonyTick` (`:244-267`) is driven from `GraveManager#onColonyTick`
(`GraveManager.java:113-137`), which is driven from `Colony#worldTickSlow` — a `TickingTransition`
at `MAX_TICKRATE = 500` ticks (`Colony.java:527,622`, `TickRateConstants.java:11`)
**[VERIFIED]**. Each beat subtracts 500 from `decay_timer`.

`DEFAULT_DECAY_TIMER = TICKS_SECOND * 60 * 10 = 12,000` ticks (`AbstractTileEntityGrave.java:22`)
**[VERIFIED]**. The lifecycle is two phases:

| Phase | Length | What happens |
|---|---|---|
| Fresh → decayed | 12,000 ticks + research bonus | Block variant flips to `DECAYED`; the timer resets to 12,000 |
| Decayed → gone | 12,000 ticks | `dropItemHandler` spills the contents on the ground; the block is set to air; the manager drops the entry |

So a grave lives **24,000 ticks — one full Minecraft day, 20 real minutes** — plus the research
bonus, which `delayDecayTimer` adds once at creation and therefore only to the first phase
(`GraveManager.java:315`, `AbstractTileEntityGrave.java:51-54`) **[VERIFIED]**.

After it drops, the items are ordinary `ItemEntity`s with vanilla's 6,000-tick lifetime
(`v/world/entity/item/ItemEntity.java:44,180`) **[VERIFIED]** — five more real minutes. The
`GraveData`, and with it the possibility of resurrection, is gone the moment the block is removed.

Two things the decay clock does **not** do:

* It does not run in an unloaded chunk — `onColonyTick` `continue`s past any grave whose position is
  not loaded (`GraveManager.java:118-121`). A grave in a chunk the player never returns to sits
  there for ever.
* It does not stop while the colony sleeps, is raided, or is rained out. §8.2.

### 3.3 The colony's grave registry

`GraveManager` holds a single `HashMap<BlockPos, Boolean>` — position to "is reserved"
(`:51`) **[VERIFIED]**. It is written to and read from the colony NBT (`:73-105`), so
reservations survive a restart.

Entries leave the map in exactly two places: `onColonyTick` when the block entity is gone
(`:123-129`) and `reserveNextFreeGrave` when it walks past one whose block entity is gone
(`:232-237`). **`removeGrave` is never called from anywhere** — grep across the whole tree returns
only the interface, the view stub and the implementation **[VERIFIED]**.

`unReserveGrave` has exactly one caller, `BuildingGraveyard#getGraveToWorkOn`
(`BuildingGraveyard.java:116`) **[VERIFIED]**. §11 F10 is the consequence.

---

## 4. The undertaker's day — the state trace

The worker AI is ticked every **5 game ticks** (`AbstractEntityCitizen.java:73`
`ENTITY_AI_TICKRATE = 5`, `AbstractAISkeleton.java:58`), and each `AITarget`'s third argument is a
period in game ticks (`TickingTransition.java:16-18,31-51`) **[VERIFIED]**.

`EntityAIWorkUndertaker` registers six targets (`:88-96`) **[VERIFIED]**:

```
IDLE          -> START_WORKING                  every REQUEST_DELAY (60)
START_WORKING -> startWorking()                 every STANDARD_DELAY (5)
WANDER        -> wander()                       every 5
EMPTY_GRAVE   -> emptyGrave()                   every 5
TRY_RESURRECT -> tryResurrect()                 every 5
DIG_GRAVE     -> digGrave()                     every 5
BURY_CITIZEN  -> buryCitizen()                  every 5
```

plus everything `AbstractEntityAIBasic` registers — the dump trigger, the needs-item trigger, the
stuck rescue, the pause handling (`AbstractEntityAIBasic.java:255-296`).

### 4.1 `START_WORKING` → `startWorking()`

```java
@Nullable final BlockPos currentGrave = building.getGraveToWorkOn();
if (currentGrave != null)
{
    if (!walkToBuilding()) { return getState(); }
    if (world.getBlockEntity(currentGrave) instanceof TileEntityGrave) { return EMPTY_GRAVE; }
    building.ClearCurrentGrave();
}
return WANDER;
```
(`:113-135`) **[VERIFIED]**

Two things to notice. First, `getGraveToWorkOn()` is not a query — it **reserves**
(`BuildingGraveyard.java:102-122`). Second, the undertaker walks **to his own hut first**, then out
to the grave. A grave 300 blocks away costs the walk home before the walk out, every time.

`getGraveToWorkOn` keeps the current reservation if the block entity is still there, otherwise
un-reserves it and calls `reserveNextFreeGrave()`, which walks `new ArrayList<>(graves.keySet())`
and takes the **first unreserved grave in HashMap iteration order** (`GraveManager.java:222-246`)
**[VERIFIED]**. Not the nearest, not the oldest, not the one closest to decaying. §11 F11.

The `ClearCurrentGrave()` at `:131` is effectively unreachable — `getGraveToWorkOn` only returns a
position whose block entity it has just confirmed. It matters anyway, because it clears the
building's pointer **without un-reserving the grave** (`BuildingGraveyard.java:92-95`). §11 F10.

### 4.2 `WANDER` → `wander()`

```java
if (worker.getNavigation().isDone())
{
    if (building.isInBuilding(worker.blockPosition()))
    { EntityNavigationUtils.walkToRandomPosWithin(worker, 10, DEFAULT_SPEED, building.getCorners()); }
    else
    { walkToBuilding(); }
}
return IDLE;
```
(`:143-158`) **[VERIFIED]**

This is the whole idle behaviour: wander inside the graveyard's corners, return `IDLE`, come back to
`START_WORKING` 60 ticks later. It costs one `reserveNextFreeGrave` scan of the grave map per
65-ish ticks. With no graves that is an empty-map walk and a single `ArrayList` allocation.

The comment above the method says he is "learning more about magic", and `UndertakerConstants.java:21`
declares `XP_PER_WANDER = 2` — **which nothing in the tree reads** (grep returns the declaration and
nothing else) **[VERIFIED]**. Wandering grants no experience of any kind.

### 4.3 `EMPTY_GRAVE` → `emptyGrave()`

(`:166-216`) **[VERIFIED]**

* Needs a shovel; `checkForToolOrWeapon` files a request and bounces to `IDLE` if there is none
  (`:170`).
* Sprints if the **Undertaker Emergency** research is on (`:176`).
* Walks to within 3 blocks of the grave (`:182`).
* If the grave is already empty → `TRY_RESURRECT` (`:190-192`). If the worker's own inventory is
  full → `INVENTORY_FULL` (`:195-198`).
* Otherwise accumulate effort: `effortCounter += getPrimarySkillLevel()` once per call until it
  reaches `EFFORT_EMPTY_GRAVE = 100` (`:200-205`, `UndertakerConstants.java:61`). At Strength *S*
  that is `ceil(100 / S)` calls × 5 ticks.
* Then `transferAllItemHandler(grave, worker)`. All of it moved → `TRY_RESURRECT`; some left over
  (worker full) → `IDLE`, from where the dump trigger takes over and he comes back for the rest
  (`:209-215`).

Note that he **unequips** before emptying (`:177`) and the shovel is only a gate, never a tool, on
this path.

### 4.4 `TRY_RESURRECT` → `tryResurrect()`

(`:299-367`) **[VERIFIED]**

* Shovel gate again (`:303-307`), unequip, walk to within 3 (`:309-322`).
* Accumulate effort with the **secondary** skill: `effortCounter += getSecondarySkillLevel()` until
  `EFFORT_RESURRECT = 400` (`:327-334`, `UndertakerConstants.java:71`), swinging and spraying
  `ParticleTypes.ENCHANT` each time. At Mana *M* that is `ceil(400 / M)` calls × 5 ticks:

  | Mana | Ritual length |
  |---|---|
  | 1 | 2,000 ticks (100 s) |
  | 5 | 400 ticks (20 s) |
  | 15 | 135 ticks (7 s) |
  | 30 | 70 ticks (3.5 s) |

* Then, in this order (`:337-346`):
  1. `shouldDumpInventory = true`.
  2. Compute the chance (§6.1).
  3. **If he is carrying any totem at all, roll `TOTEM_BREAK_CHANCE = 0.01` and destroy one totem on
     a hit** — before, and independently of, whether the resurrection succeeds.
  4. Roll the resurrection.
* On success (`:346-363`): heart particles, `resurrectCivilianData`, a colony message, two
  statistics, `updateCitizenMourn(newData, false)`, the advancement, `setLastGraveData(null)`, the
  grave block set to air, and `INVENTORY_FULL` to go dump the loot.
* On failure: `DIG_GRAVE` (`:366`).

The grave block is set to air directly (`:361`) rather than mined, so the resurrection path skips
`mineBlock` entirely: **a resurrection grants no experience, no `GRAVES_DUG` statistic, and no
saturation cost.** The manager notices the missing block entity on its next 500-tick beat and drops
the entry. **[VERIFIED]**

### 4.5 `DIG_GRAVE` → `digGrave()`

(`:223-291`) **[VERIFIED]**

Equip the shovel, `mineBlock(gravePos)`, and on success: clear the building's grave pointer, store
the `GraveData` on the module as `lastGraveData`, decrease saturation, grant `XP_PER_DIG = 7.5` to
the **primary** skill directly, bump both statistics, and go to `BURY_CITIZEN`.

`mineBlock` also grants `XP_PER_BLOCK = 0.05` through the normal experience handler
(`AbstractEntityAIInteract.java:237`, `XP_PER_BLOCK` at `:47`) **[VERIFIED]** — this is the only
route by which the undertaker's **Mana** skill ever grows, and it is the reason §6.3 exists.

### 4.6 `BURY_CITIZEN` → `buryCitizen()`

(`:428-476`) **[VERIFIED]**

* Shovel gate; bail to `IDLE` if `lastGraveData` is null.
* Pick a headstone slot: reuse `burialPos` if the block there is still replaceable, otherwise
  `building.getRandomFreeVisualGravePos()` (`:439-442`).
* If there is none: one chat line naming the citizen, and `IDLE` (`:444-450`). **`lastGraveData` is
  not cleared**, so the name simply sits there until the next dug grave overwrites it.
* Otherwise walk to within 4, accumulate `EFFORT_BURY = 400` at the **primary** skill rate while
  hitting the ground with the shovel (`:452-463`), then `module.buryCitizenHere(...)`, the bury
  advancement, clear `lastGraveData`, and `INVENTORY_FULL`.

`buryCitizenHere` (`GraveyardManagementModule.java:171-205`) destroys whatever is at the position,
places `ModBlocks.blockNamedGrave` facing the recorded direction, writes three lines onto the
headstone — first name, last name, job name — and appends the name to `restingCitizen`. It is
guarded by `!restingCitizen.contains(name)` (`:173`), which is where §11 F9 comes from.

### 4.7 The dump

`wantInventoryDumped()` returns true exactly once after a resurrection or a burial
(`:484-492`) **[VERIFIED]**, which feeds `inventoryNeedsDump` and sends him to `INVENTORY_FULL`
(`AbstractEntityAIBasic.java:467-474`). The dump keeps one shovel and up to **two totems** in his
personal inventory, because `buildingRequiresCertainAmountOfItem` consults `keepX` with the
inventory flag set (`AbstractBuilding.java:1204-1247`, `BuildingGraveyard.java:85-86`)
**[VERIFIED]**. That is how a totem stays with him once he has one — and §11 F12 is how it gets
there in the first place.

---

## 5. Item duplication

### 5.1 The boots are copied into the grave twice

```java
for (final EquipmentSlot equipmentSlot : EquipmentSlot.values())
{
    final ItemStack stack = citizenData.getInventory().getArmorInSlot(equipmentSlot);
    if (!InventoryUtils.addItemStackToItemHandler(graveEntity.getInventory(), stack))
    {
        InventoryUtils.spawnItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
    }
}
```
(`GraveManager.java:305-312`) **[VERIFIED]**

```java
public ItemStack getArmorInSlot(final EquipmentSlot equipmentSlot)
{
    if (equipmentSlot.isArmor()) { return armorInventory.get(equipmentSlot.getIndex()); }
    return ItemStack.EMPTY;
}
```
(`InventoryCitizen.java:312-319`, with `armorInventory` a four-entry list at `:56`) **[VERIFIED]**

And vanilla:

```java
FEET(EquipmentSlot.Type.HUMANOID_ARMOR, 0, 1, 1, "feet"),
LEGS(EquipmentSlot.Type.HUMANOID_ARMOR, 1, 1, 2, "legs"),
CHEST(EquipmentSlot.Type.HUMANOID_ARMOR, 2, 1, 3, "chest"),
HEAD(EquipmentSlot.Type.HUMANOID_ARMOR, 3, 1, 4, "head"),
BODY(EquipmentSlot.Type.ANIMAL_ARMOR, 0, 1, 6, "body"),
SADDLE(EquipmentSlot.Type.SADDLE, 0, 1, 7, "saddle");
...
public boolean isArmor() { return this.type == Type.HUMANOID_ARMOR || this.type == Type.ANIMAL_ARMOR; }
```
(`v/world/entity/EquipmentSlot.java:15-20,73-75`) **[VERIFIED]**

`BODY` passes `isArmor()` and its index is **0**, the same index `FEET` uses. The loop therefore
reads the boots on the third iteration and again on the seventh, and inserts them into the grave
twice. `SADDLE` is harmless (`Type.SADDLE` fails `isArmor()`), and `MAINHAND`/`OFFHAND` return
`ItemStack.EMPTY`, which `addItemStackToItemHandler` rejects and `spawnItemStack` turns into a
zero-iteration loop (`InventoryUtils.java:1071-1078,2644-2659`) **[VERIFIED]**.

What lands in the grave depends on whether the boots are damaged.
`addItemStackToItemHandler` copies an undamaged stack and inserts the copy, but for a damaged stack
it hands the *same object* straight to `insertItem` (`InventoryUtils.java:1078-1091`), and
`insertItem` stores the reference rather than a copy when the target slot is empty and the count
fits (`mc/api/inventory/api/ItemStackHandler.java:118-123`) **[VERIFIED]**. So:

* **Undamaged boots**: two independent copies in the grave. A clean, immediate duplication.
* **Damaged boots** (the normal case for worn armour): two grave slots aliasing one `ItemStack`
  object, which the block entity writes out as two stacks on the next save. **[VERIFIED]** that the
  aliasing happens; **[UNVERIFIED]** exactly what a player sees before that first save — the two
  slots share a count, so the observable outcome between insertion and serialisation needs a running
  server to pin down.

Either way the colony gains a pair of boots on every death of a booted citizen. **This is upstream's
code, byte-for-byte** (§0.2). Whether the same collision existed in 1.21.1's `EquipmentSlot` is
**[UNVERIFIED]** — that vanilla tree is not in this repository — but `BODY` predates 1.21.1 and the
mod-side code has not changed, so it almost certainly did.

The same shape appears in this fork's `dropCitizenInventory` (`InventoryUtils.java:2590-2608`) and
is harmless there **only by accident**: that loop clears each slot as it goes
(`forceClearArmorInSlot`), so by the time `BODY` re-reads index 0 the boots are already gone.
**[VERIFIED]**

### 5.2 A resurrection duplicates the whole armour set

The armour loop copies armour into the grave. It never clears `armorInventory`. Nineteen lines
later:

```java
graveData.setCitizenDataNBT(citizenData.serializeNBT(world.registryAccess()));
```
(`GraveManager.java:324`) **[VERIFIED]**

and `CitizenData#serializeNBT` writes the inventory, armour included
(`CitizenData.java:1491` → `InventoryCitizen#write`, which serialises `armorInventory` at
`InventoryCitizen.java:613-623`) **[VERIFIED]**.

So the snapshot the grave carries for the resurrection still has the armour on. The undertaker
empties the grave — armour and all — into the graveyard hut, and if the roll comes up the
resurrected citizen is deserialised straight out of that snapshot and walks out wearing the same
armour again. **Net: one extra full set per successful resurrection, and one extra pair of boots on
top of that from §5.1.**

The main inventory is not affected: `transferAllItemHandler` empties it before the serialise
(`:301`), so the snapshot's main inventory is genuinely empty.

The fix is one line — take the serialise before the transfer, or clear each armour slot as it is
copied, exactly as `dropCitizenInventory` already does. **Upstream. S.**

---

## 6. Resurrection: the arithmetic

### 6.1 The formula, and the cap that eats it

```java
double totemChance = getTotemResurrectChance();
double chance = buildingGraveyard.getBuildingLevel() * RESURRECT_BUILDING_LVL_WEIGHT
              + worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Mana) * RESURRECT_WORKER_MANA_LVL_WEIGHT
              + ...getEffectStrength(RESURRECT_CHANCE)
              + totemChance;

final double cap = MAX_RESURRECTION_CHANCE
                 + ...getMysticalSiteMaxBuildingLevel() * MAX_RESURRECTION_CHANCE_MYSTICAL_LVL_BONUS
                 + totemChance;
if (chance > cap) { chance = cap; }
```
(`EntityAIWorkUndertaker.java:375-391`) **[VERIFIED]**

| Constant | Value | Where |
|---|---|---|
| `RESURRECT_BUILDING_LVL_WEIGHT` | 0.005 per graveyard level | `UndertakerConstants.java:26` |
| `RESURRECT_WORKER_MANA_LVL_WEIGHT` | 0.00125 per Mana level | `:31` |
| `MAX_RESURRECTION_CHANCE` | 0.025 | `:36` |
| `MAX_RESURRECTION_CHANCE_MYSTICAL_LVL_BONUS` | 0.005 per Mystical Site level | `:41` |
| `SINGLE_TOTEM_RESURRECTION_CHANCE_BONUS` | 0.05 | `:46` |
| `MULTIPLE_TOTEMS_RESURRECTION_CHANCE_BONUS` | 0.075 | `:51` |
| `RESURRECT_CHANCE` research | +0.01 / +0.03 | `DefaultResearchProvider.java:114` |

All **[VERIFIED]**. The Mystical Site's maximum level is 5
(`BuildingMysticalSite.java:19`, `RegisteredStructureManager.java:794-808`) **[VERIFIED]**.

Work the cap out:

| Setup | `chance` | `cap` | Effective |
|---|---|---|---|
| Graveyard 1, Mana 10, no research, no site, no totem | 0.005 + 0.0125 = **0.0175** | 0.025 | **1.75%** |
| Graveyard 3, Mana 10, no research, no site, no totem | 0.015 + 0.0125 = 0.0275 | 0.025 | **2.5%** |
| Graveyard **5**, Mana **0**, no research, no site, no totem | 0.025 | 0.025 | **2.5%** |
| Graveyard 5, Mana 40, **both researches**, no site, no totem | 0.025 + 0.05 + 0.03 = 0.105 | 0.025 | **2.5%** |
| Graveyard 5, Mana 40, both researches, **Mystical Site 5** | 0.105 | 0.05 | **5%** |
| ... plus **one** totem | 0.155 | 0.10 | **10%** |
| ... plus **two** totems | 0.18 | 0.125 | **12.5%** |

**The maximum resurrection chance in the game is 12.5%**, and reaching it needs a level-5 graveyard,
a level-5 Mystical Site, both Resurrection Chance researches, the Raising the Dead research, and two
Totems of Undying sitting in the undertaker's personal inventory.

The line that matters for most colonies is the third one. A level-5 graveyard hits the base cap on
its own. Everything else — the researches, the skill, the levels — is bought and then thrown away
unless a Mystical Site is standing. Even at level 1 the cap is reached by a Mana of 16, which is an
ordinary hire.

That is a real finding and not a nitpick: the branch's two most expensive researches (a ghast tear,
then sixteen chorus fruit behind a level-5 building requirement) sell "+1%" and "+3%" and deliver
**nothing at all** in the common case. `DefaultResearchProvider.java:733-753` **[VERIFIED]**.

### 6.2 The totem, and what it costs

```java
if (getTotemResurrectChance() > 0 && random.nextDouble() <= TOTEM_BREAK_CHANCE)
{
    worker.getInventoryCitizen().extractItem(findFirstSlotInItemHandlerWith(..., Items.TOTEM_OF_UNDYING), 1, false);
    worker.playSound(SoundEvents.TOTEM_USE, 1.0f, 1.0f);
}
```
(`EntityAIWorkUndertaker.java:340-344`, `TOTEM_BREAK_CHANCE = 0.01` at
`UndertakerConstants.java:56`) **[VERIFIED]**

The break roll happens on every completed ritual, success or failure. At the 12.5% ceiling the
expected number of rituals per success is 8, so the expected totem cost per resurrection is
`8 × 0.01 = 0.08` totems — **one totem funds about twelve resurrections.** Two totems are worth
roughly 25.

That is cheap, but the cliff matters: the moment one of the two breaks, the bonus drops from 0.075
to 0.05 and the cap from 12.5% to 10%; when the second breaks it falls to the 5% site-only cap. And
nothing restocks it. §11 F12.

`getTotemResurrectChance` also fires the `UNDERTAKER_TOTEM` advancement for every player in the
colony on every ritual where he holds one (`:400-407`) **[VERIFIED]**. The advancement is a child of
`citizen_resurrect` (`DefaultAdvancementsProvider.java:255-265`), which needs a successful
resurrection first — so the tree asks the player to resurrect somebody *before* it recognises the
item that makes resurrecting likely. Cosmetic, but backwards.

### 6.3 Why the undertaker's Mana never grows

Two experience sources, and only one of them reaches Mana.

* `digGrave` grants `XP_PER_DIG = 7.5` straight into the **primary** skill via
  `addXpToSkill(getModuleForJob().getPrimarySkill(), ...)` (`EntityAIWorkUndertaker.java:260`).
  Primary is **Strength** (`BuildingModules.java:652`). Mana gets nothing from this.
* `mineBlock` grants `XP_PER_BLOCK = 0.05` through `CitizenExperienceHandler#addExperience`
  (`AbstractEntityAIInteract.java:237`), which multiplies by
  `1 + (workBuildingLevel + homeLevel)/10`, then by `1 + Intelligence/100`, then by the `LEVELING`
  research, gives the whole of it to the primary skill and **half of it** to the secondary
  (`CitizenExperienceHandler.java:86-115`).

All **[VERIFIED]**. So Mana receives, per grave dug:

```
0.05 × (1 + (5+5)/10) × (1 + 50/100) / 2  =  0.05 × 2 × 1.5 / 2  =  0.075
```

with a level-5 hut, a level-5 home and Intelligence 50 — a generous case, ignoring the `LEVELING`
research.

The cost of the level after *L* is `f(L) = 1 + 5L + 0.005L³`
(`mc/core/util/ExperienceUtils.java:68-77`, with `EXPERIENCE_MULTIPLIER = 1` at `:11`, consumed one
level at a time by `CitizenSkillHandler#addXpToSkill:186-215`) **[VERIFIED]**. Summing:

| Mana target | XP needed from level 1 | Graves at 0.075/grave |
|---|---|---|
| 10 | 244 | **~3,300** |
| 25 | 1,974 | **~26,000** |
| 50 | 9,381 | **~125,000** |

A colony that loses a citizen every ten game days digs 36 graves a game year. **The undertaker's
Mana level is, for all practical purposes, whatever it was on the day you hired him.**
Skills start between 1 and `(int) colony happiness` at creation, plus a role-model bonus of 25–49
points spread across all ten skills (`CitizenSkillHandler.java:47-118`) **[VERIFIED]**, so a
realistic hire has Mana somewhere in 1–25.

That matters twice over: it fixes his contribution to the chance (which the cap has already made
irrelevant, §6.1) *and* it fixes the length of every ritual he ever performs (§4.4). Hire the
highest-Mana citizen you can find, because he will never get better.

---

## 7. The graveyard building

### 7.1 Capacity: the headstones are counted in the blueprint

`visualGravePositions` is populated by `registerBlockPosition`, which the graveyard overrides to
record any position whose **blueprint** state is `ModBlocks.blockNamedGrave`, together with its
facing (`BuildingGraveyard.java:189-197`) **[VERIFIED]**. The builder calls it from
`triggerSuccess` using the blueprint state rather than the world state
(`BuildingStructureHandler.java:198-206`) **[VERIFIED]**, and `NamedGravePlacementHandler#handle`
deliberately places nothing on the builder path (`:42-48`) **[VERIFIED]** — so the positions stay
air, and `getRandomFreeVisualGravePos` finds them by testing `canBeReplaced()`
(`BuildingGraveyard.java:202-225`).

Counting `minecolonies:blockminecoloniesnamedgrave` out of the blueprints on the 26.2 branch
(script: gzip + NBT parse, palette lookup, block array unpacked as two 16-bit indices per int;
totals cross-checked against `size_x × size_y × size_z`) **[VERIFIED]**:

| Style | L1 | L2 | L3 | L4 | L5 |
|---|---|---|---|---|---|
| `warped` | 10 | 27 | 46 | 59 | **76** |
| `darkoak` | 8 | 13 | 18 | 37 | **102** |
| `jungle` | 6 | 12 | 24 | 35 | **49** |
| `spacewars` | 4 | 8 | 16 | 22 | **22** |

So capacity is entirely a matter of which style you play — a `darkoak` level-5 graveyard holds 102
headstones and a `spacewars` one holds 22, and `spacewars` gains **nothing at all** from levels 4 to
5. **[VERIFIED]** for these four styles; the other twenty-one styles were not counted
**[UNVERIFIED]**.

### 7.2 What happens when it fills

Every burial converts one air slot into a real `blockNamedGrave`, which is not replaceable, so
`getRandomFreeVisualGravePos` returns null once they are all used
(`BuildingGraveyard.java:209-221`). From then on `buryCitizen` sends one chat line per burial
attempt and returns `IDLE` (`EntityAIWorkUndertaker.java:444-450`).

**Nothing else breaks.** He keeps emptying graves, keeps attempting resurrections and keeps digging.
Only the headstone and the `restingCitizen` entry are lost — which costs the mourning AI its
destination (`EntityAIMournCitizen.java:294-308` looks for a graveyard that
`hasRestingCitizen(...)`) **[VERIFIED]**, and nothing else.

There is **no way to clear a graveyard.** Nothing removes a `blockNamedGrave` and nothing ever
shrinks `restingCitizen` — the list is only ever appended to and cleared on load
(`GraveyardManagementModule.java:52,63-71,202`) **[VERIFIED]**. Upgrading the hut is the only way to
add capacity, and level 5 is the end of it. A long-lived colony fills its graveyard permanently.

### 7.3 What each level actually unlocks

| Level | Resurrection term | Max shovel | Headstones | Research gate |
|---|---|---|---|---|
| 1 | +0.5% | wood/gold | 4–10 | — |
| 2 | +1.0% | 1 | 8–27 | Undertaker Emergency (sprinting) |
| 3 | +1.5% | 1 | 16–46 | Resurrection Chance I, Grave Decay I |
| 4 | +2.0% | 2 | 22–59 | — |
| 5 | +2.5% | maximum | 22–102 | Resurrection Chance II, Raising the Dead, Grave Decay II |

Max equipment level from `IBuilding#getMaxEquipmentLevel` (`IBuilding.java:479-490`)
**[VERIFIED]**; research gates from `DefaultResearchProvider.java:723-786` **[VERIFIED]**; the
resurrection term is 0.005 × level, and §6.1 shows the cap swallows it.

**The building level buys one worker at every level** (`(b) -> 1`, `BuildingModules.java:652`)
**[VERIFIED]** — unlike most service buildings, levelling does not add staff.

### 7.4 The research branch

All under `Remembrance` (`DefaultResearchProvider.java:713-786`) **[VERIFIED]**:

| Research | Requires | Costs | Effect |
|---|---|---|---|
| Remembrance | Town Hall 2 | 8 bones | Unlocks the graveyard |
| Undertaker Emergency | Graveyard 2 | 1 iron boots | `UNDERTAKER_RUN` — he sprints to graves |
| Resurrection Chance I | Graveyard 3 | 1 ghast tear | +1% chance — see §6.1 |
| Resurrection Chance II | Graveyard 5, RC I | 16 chorus fruit | +3% chance — see §6.1 |
| Raising The Dead | Graveyard 5, RC II | 1 totem | `USE_TOTEM` — lets him use totems at all |
| Grave Decay I | Graveyard 3 | 64 rotten flesh | +5 minutes before decay |
| Grave Decay II | Graveyard 5, GD I | 8 nether wart blocks | +10 minutes before decay |

`GRAVE_DECAY_BONUS` levels are 5 and 10 (`DefaultResearchProvider.java:115`) **[VERIFIED]** and
`delayDecayTimer` converts them to ticks at creation (`AbstractTileEntityGrave.java:51-54`). Because
the timer is reset to `DEFAULT_DECAY_TIMER` when the first phase ends
(`TileEntityGrave.java:251-256`), **the bonus is applied once, not per phase**: Grave Decay II takes
a grave's total life from 24,000 ticks to 36,000 ticks, not to 48,000. **[VERIFIED]**

**Grave Decay is the branch that pays.** The two Resurrection Chance researches are the ones that do
not.

---

## 8. Night, rain, raids, and the graveyard being far away

### 8.1 Night

`CitizenAI#calculateNextState` sends any non-guard citizen to `SLEEP` while
`!WorldUtil.isPastTime(world, NIGHT - 2000)` — that is, while `time % 24000 > 10600`
(`CitizenAI.java:183-197`, `WorldUtil.java:183-187`, `CitizenConstants.java:253` `NIGHT = 12600`)
**[VERIFIED]**. So the working window is at most **10,600 ticks of each 24,000-tick day** — a bit
under 45%. The undertaker is an ordinary civilian in this respect.

The grave's 24,000-tick life is therefore *just* enough: a grave created at the last possible moment
of the working day has 24,000 ticks, spends 13,400 of them asleep, and still has 10,600 — one full
working day — at dawn. Night alone does not lose graves.

### 8.2 Raids and rain, which do

Two branches sit *above* the work branch and both stop him:

```java
if (...getRaiderManager().isRaided())
{
    ...
    return CitizenAIState.SLEEP;
}
```
(`CitizenAI.java:163-168`) **[VERIFIED]**

```java
if (world.isRaining() && !shouldWorkWhileRaining() && !WorldUtil.isNetherType(...))
{
    ...
    return CitizenAIState.IDLE;
}
```
(`CitizenAI.java:269-278`) **[VERIFIED]**

Meanwhile `graveManager.onColonyTick` is on the colony's own 500-tick beat and does not consult any
of that (`Colony.java:622`) **[VERIFIED]**.

The raid one is the sharp edge. **A raid is when citizens die, and the undertaker is asleep for the
whole of it.** Then the raid ends, and whatever is left of the graves' 24,000 ticks is what he has —
minus the night, minus any rain, minus the walk. Rain lasts up to a full Minecraft day, so a
rainstorm the morning after a night raid can spend the rest of a grave's life on its own.

This is the failure mode that costs a player gear, and it is exactly what Grave Decay I and II
address: +6,000 and +12,000 ticks, moving a grave's life from one Minecraft day to one and a half.
The `WORKING_IN_RAIN` research fixes the rain half separately.

### 8.3 Unreachable graves

`walkWithProxy(gravePos, 3)` returns "still walking" for as long as the pathfinder cannot get there
(`AbstractEntityAIBasic.java:1023-1037`) **[VERIFIED]**, which on its own would pin the worker in
`EMPTY_GRAVE` for ever. `StuckRescue` catches it: it watches the navigator's destination rather than
the path, and teleports the worker there after `stuckRescueSeconds` (default 60) of not getting
closer (`mc/core/entity/ai/workers/util/StuckRescue.java:27-120`,
`ServerConfiguration.java:329`) **[VERIFIED]**. That is this fork's machinery, and it covers the
undertaker without knowing about him.

So a grave sealed inside a hillside or at the bottom of a ravine costs a minute, not a stall.

### 8.4 What he cannot be interrupted for

`EMPTY_GRAVE`, `TRY_RESURRECT`, `DIG_GRAVE`, `BURY_CITIZEN` and `INVENTORY_FULL` are all declared
`isOkayToEat() == false` (`AIWorkerState.java:23,169,174,179,184`) **[VERIFIED]**, and eating is
gated on exactly that: `shouldEat` → `job.canEat()` → `canAIBeInterrupted()` →
`getState().isOkayToEat()` (`CitizenAI.java:312-315`, `IJob.java:161-164`,
`AbstractJob.java:356-364`, `AbstractAISkeleton.java:126-129`) **[VERIFIED]**.

The whole chain from picking up a grave to finishing the burial is uninterruptible. Worst case, with
Strength 1 and Mana 1: 500 + 2,000 + 2,000 ticks of effort plus the walking, so on the order of
**5,000 ticks — a little over four real minutes** with no meal. With ordinary skills (Strength 20,
Mana 15) it is under 400 ticks. Less severe than the enchanter's 6,000-tick lock
(`docs/studies/enchanter.md` §F9), but the same shape.

---

## 9. Is he worth building?

### 9.1 What he actually delivers

Three things, in descending order of value:

1. **Automatic recovery of a dead colonist's gear.** Without an undertaker the player has the
   grave's 20 minutes (30 with Grave Decay II) to walk out to the coordinates in the chat message,
   plus 5 more minutes of ground items, or the gear is gone. With one, it walks itself back to the
   graveyard hut and from there to the warehouse on the normal courier path. **This is the whole
   case for the building.**
2. **The headstone and the mourning destination.** Cosmetic, plus it gives grieving citizens
   somewhere to go (`EntityAIMournCitizen.java:294-308`).
3. **A resurrection lottery** at 1.75–2.5% for a colony without a Mystical Site.

### 9.2 The resurrection, in game days

Raids arrive on average every 14 nights, minimum 10
(`ServerConfiguration.java:389-390`) **[VERIFIED]**. Let *D* be citizen deaths per raid and *p* the
effective chance from §6.1. Game days per resurrection is `14 / (p · D)`:

| Setup | *p* | *D* = 1 | *D* = 3 |
|---|---|---|---|
| Graveyard 3, no site, no research | 0.025 | **560 days** | 187 days |
| Graveyard 5, no site, both researches (identical — the cap) | 0.025 | 560 days | 187 days |
| + Mystical Site 5 | 0.05 | 280 days | 93 days |
| + Raising the Dead, 1 totem | 0.10 | 140 days | 47 days |
| + 2 totems | 0.125 | 112 days | **37 days** |

A Minecraft day is 20 real minutes, so the default column is **560 game days ≈ 187 real hours** for
one citizen brought back. The fully-kitted bottom-right corner is **37 game days ≈ 12 real hours** —
respectable, and it costs a level-5 Mystical Site, four researches and two totems the game gives you
no way to obtain except by killing evokers.

The comparison the enchanter study invites: that worker needs ~1,600 game days for one Mending
(`docs/studies/enchanter.md` §7). The undertaker's resurrection is about three times better than
that at default settings and forty times better fully kitted — but it is still a lottery, and it is
not why you build him.

*D* is an assumption, not a measurement **[UNVERIFIED]** — a real figure would come from a long
server run reading the `deaths` statistic against the raid history.

### 9.3 What a resurrected citizen is worth

He keeps his **name, gender, texture, voice, age, saturation, happiness, family names and — the
expensive part — every skill level** (`CitizenData#serializeNBT:1446-1493`, `TAG_NEW_SKILLS` at
`:1471`) **[VERIFIED]**. Given how slowly skills grow (§6.3), a resurrected veteran is worth
considerably more than a fresh hire.

He loses:

* **His job and his home**: `onResurrect()` is `homeBuilding = null; setJob(null)`
  (`CitizenData.java:2094-2098`) **[VERIFIED]**, and `removeCivilian` already stripped him out of
  every assignment module on death (`CitizenManager.java:400-426`). The player must re-hire and
  re-house him by hand.
* **His identity number**: `resurrectCivilianData` is called with `resetId = true` and rewrites
  `TAG_ID` to the lowest free citizen id (`EntityAIWorkUndertaker.java:353`,
  `CitizenManager.java:371-397`) **[VERIFIED]**.

The id reset has two consequences worth knowing:

* **His marriage becomes one-sided.** He still carries his partner's id; his partner carries his
  *old* id, which no longer resolves, and `serializeToView` clears it to 0 in place
  (`CitizenData.java:1154-1160`) **[VERIFIED]** — a persistent field mutation, not a display filter.
  Sibling and child sets are pruned the same way (`:1159-1160`).
* **He may be adopted into a stranger's family.** Ids are reused deliberately
  (`CitizenManager.java:374-383`) and the tree already knows the hazard — the comment at
  `CitizenData.java:299-302` says "a stored id can come to name a stranger" and `pruneParentIds`
  guards *parents* by name. `partner`, `siblings` and `children` have no such guard, so if the
  resurrected citizen takes an id that some living citizen still lists as a sibling or child, the
  relationship silently transfers to him. **[VERIFIED]** by reading; **[UNVERIFIED]** how often it
  happens in play.

Mourning is handled: `updateCitizenMourn(citizenData, false)` clears him from every mourner's
deceased list *by name* (`EntityAIWorkUndertaker.java:357`, `CitizenManager.java:670-688`)
**[VERIFIED]** — and by name is the right key here, because the id changed.

The undertaker himself is exempt from mourning by design (`CitizenManager.java:677` excludes
`JobUndertaker` alongside guards) **[VERIFIED]**, which is a nice touch: the man who buries people
does not down tools to grieve them.

### 9.4 The recommendation

* **Build it, at level 1, as soon as the colony starts losing people.** Gear recovery pays for the
  citizen slot on the first dead guard.
* **Level to 3** for Grave Decay I. That is the research that addresses the actual gear-loss failure
  mode (§8.2).
* **Level to 5 only if you are also building a Mystical Site.** Without one, Resurrection Chance I
  and II are inert (§6.1) and level 5 buys you headstone slots and a diamond shovel.
* **Hire for Mana, not Strength.** Strength shortens the emptying and burial loops, which are
  seconds; Mana sets the ritual length and the (capped) chance and never grows (§6.3).
* **Put two Totems of Undying in his inventory by hand** if you have the Raising the Dead research.
  Nothing else will (§11 F12), and two totems are worth 25 resurrections' worth of break rolls.

---

## 10. Performance

### 10.1 What ticks

| Thing | Rate | Cost |
|---|---|---|
| `GraveManager#onColonyTick` | every 500 ticks | one `isBlockLoaded` + one `getBlockEntity` per grave in the colony |
| `TileEntityGrave#onColonyTick` | same beat | an int subtraction, plus a `setBlockAndUpdate` twice in a grave's life |
| The undertaker's AI | every 5 ticks | one target function |
| `reserveNextFreeGrave` | on every `getGraveToWorkOn` where the current reservation is void | `new ArrayList<>(graves.keySet())` + a scan |
| `GraveyardManagementModule#serializeToView` | whenever the module is dirty and a player is subscribed | a `getBlockEntity` per grave, plus every resting-citizen name |

All **[VERIFIED]** by reading the cited methods.

### 10.2 The three that scale

**Graves that never leave the map.** An entry is removed only when its block entity has gone *and*
the chunk happens to be loaded when someone looks (`GraveManager.java:118-129,227-237`). A grave in
a chunk the player abandons is never decayed and never removed, so it stays in the map, in the
colony NBT, and in the 500-tick scan for the life of the world. Bounded by how many people die in
places you never revisit — small, but monotonic. **S** to fix by aging out entries that have been
unloaded for a long time; also **[UNVERIFIED]** whether it is worth doing.

**`restingCitizen` grows without bound.** One string per burial, for ever
(`GraveyardManagementModule.java:52,202`) **[VERIFIED]**. It is written to the colony save on every
serialise and pushed to every subscribed client in full on every module sync
(`:82-96,99-130`) — and `setLastGraveData` calls `markDirty()` on every grave dug (`:136-140`), so
that sync happens once per grave. A thousand-death colony pushes about 20 KB of names down the wire
every time the undertaker finishes a grave. Not a stall; a slow leak that a player sees as a GUI
list they can never clear. **S** to cap the list, **M** to give the player a way to clear a
graveyard and free the headstone slots (which is the same feature §7.2 wants).

**`findAround` on a death with nowhere to put a grave.** `findAround(pos, 10, 10, ...)` walks 12
y-levels of square rings up to radius 10; the predicate does two `getBlockState` calls. When it
fails, that is on the order of **6,000 block lookups in one tick**
(`BlockPosUtil.java:1033-1100`, arithmetic mine) **[VERIFIED by reading the loop; the 6,000 figure
is arithmetic, not a measurement]**. It happens once per death, and only on deaths that will not get
a grave — a citizen entombed in a cave, or one killed underground during a raid. Harmless singly;
worth knowing about if a raid kills a dozen citizens in a sealed room in the same tick.

### 10.3 What I would measure first

The `serializeToView` cost on a colony with a few hundred burials, by timing
`GraveyardManagementModule#serializeToView` with the graveyard GUI open. Everything else in this
area is small enough that reading it is enough.

---

## 11. Findings, ranked

Fix sizes: **S** under ~150 lines. "Ours/upstream" is about where the code came from, per §0.2.

### F1 — Every death copies the dead citizen's boots into the grave twice. **S. Upstream. Balance: yes (removes free items).**

§5.1. `GraveManager.java:305-312` iterates `EquipmentSlot.values()` and `getArmorInSlot` maps both
`FEET` and `BODY` to armour index 0 (`InventoryCitizen.java:312-319`,
`v/world/entity/EquipmentSlot.java:15-20,73-75`). **[VERIFIED]**

**Fix**: iterate the four humanoid armour slots explicitly, or clear each slot as it is copied the
way `dropCitizenInventory` does (`InventoryUtils.java:2590-2608`). ~5 lines. **Neither a mixin nor
an access widener.** Ship it as a bug fix and say in the changelog that a duplication is going away.

### F2 — A successful resurrection duplicates the whole worn armour set. **S. Upstream. Balance: yes (removes free items).**

§5.2. The armour is copied to the grave but never cleared, and `citizenData.serializeNBT(...)` runs
afterwards at `GraveManager.java:324`, so the snapshot the grave keeps for the resurrection still
has it. **[VERIFIED]**

**Fix**: move the serialise above the armour loop, or clear the slots. ~4 lines. Fixes F1 in the same
stroke if done by clearing. **Neither.**

### F3 — Both Resurrection Chance researches, and the undertaker's Mana, buy nothing without a Mystical Site. **S. Upstream. Balance: yes, unambiguously.**

§6.1. `chance` is clamped to `0.025 + mysticalSiteLevel × 0.005 + totem`
(`EntityAIWorkUndertaker.java:383-389`), and a level-5 graveyard alone contributes 0.025.
**[VERIFIED]**

**Fix options**, all balance changes:
(a) let the graveyard level raise the cap the way the Mystical Site does — one term,
~3 lines, and it makes levelling the building mean something;
(b) drop the building-level term from `chance` and keep it only in `cap`, so the researches and the
skill are what fill the headroom — ~2 lines, and it makes both researches live;
(c) raise `MAX_RESURRECTION_CHANCE` and leave the structure alone.
(b) is the smallest change that makes the existing research tree honest. **Neither.**

### F4 — The undertaker's Mana level is fixed at hire; it is the stat that matters and the one that cannot grow. **S. Upstream. Balance: yes.**

§6.3. 7.5 XP per grave goes to the primary skill only
(`EntityAIWorkUndertaker.java:260`); Mana receives half of 0.05 × modifiers via `mineBlock`. Moving
Mana 1 → 25 is ~26,000 graves. **[VERIFIED]**

**Fix**: route `XP_PER_DIG` through `worker.getCitizenExperienceHandler().addExperience(...)` instead
of `addXpToSkill(primary, ...)`, so both skills get their normal share. One line, and Mana then grows
at 3.75 per grave — Mana 25 in about 500 graves, which is a long career but a reachable one.
**Neither.** It is a balance change: it makes the ritual shorter over time and, if F3 is also fixed,
the chance higher.

### F5 — A citizen who dies outside the claim loses his worn armour. **S. Upstream bug, fix already in this tree.**

`EntityCitizen.java:1670-1674` calls `InventoryUtils.dropItemHandler`, which walks the main
inventory only (`InventoryUtils.java:2561-2572`, `InventoryCitizen.java:163-166`). This fork already
has `dropCitizenInventory` and already uses it for the analogous case in `GraveManager.java:334`.
**[VERIFIED]** Same defect as `docs/studies/guards.md` §3.7, restated here because it is on the
death path and because the fix is now a one-word change rather than a new method.

**Fix**: `dropItemHandler` → `dropCitizenInventory` at `EntityCitizen.java:1673`. One line.
**Neither.** Not a balance change: it returns gear the player already paid for.

### F6 — Dying in lava, or invisible, destroys everything silently. **S. Upstream.**

§2.1. `GraveManager.java:260-265` returns without dropping; `EntityCitizen.java:1664,1676-1679`
skips both the grave and the drop when the citizen is invisible. **[VERIFIED]**

The lava case costs a player fire-resistant gear that vanilla would have preserved. The invisible
case is rarer — the nether-expedition worker — but it is total and it is not announced.

**Fix**: in the lava branch, drop the fire-immune part of the inventory (or all of it, and let lava
sort it out) before returning; in the invisible branch, drop as the out-of-claim branch does. ~8
lines across two files. **Neither.**

### F7 — The undertaker sleeps through raids, and graves decay through them. **S for the flag, M to do it properly. Upstream. Behaviour.**

§8.2. `CitizenAI.java:163-168` puts every non-guard into `SLEEP` for the whole raid.
`GraveManager#onColonyTick` keeps counting (`Colony.java:622`). **[VERIFIED]**

**Fix options**: (a) exempt `JobUndertaker` from the raid branch the way guards are exempt — ~4
lines, but it sends an unarmed civilian out among raiders, so it wants a "stay inside the claim"
condition or it is a way to lose the undertaker as well; (b) pause the decay clock while
`isRaided()` — ~5 lines in `GraveManager#onColonyTick`, no citizen is put in danger, and it addresses
the actual complaint. (b) is the smaller and safer change. **Neither.**

### F8 — The graveyard fills permanently and the resting list never shrinks. **M. Upstream.**

§7.2, §10.2. `restingCitizen` is append-only (`GraveyardManagementModule.java:52,202`) and headstone
slots never return to the pool. **[VERIFIED]**

**Fix**: a GUI action on the graveyard tab that clears a chosen headstone — remove the block, drop
the name from the list — plus the packet and the permission check. ~120 lines with the network
message and the module method, so borderline S/M; call it **M** because it needs a new
`AbstractBuildingServerMessage` and a GUI button in a runtime-asset layout this repository does not
own, which is the part that makes it awkward. **Neither.**

### F9 — Two citizens with the same name: the second never gets a headstone. **S. Upstream.**

`buryCitizenHere` is guarded by `!restingCitizen.contains(lastGraveData.getCitizenName())`
(`GraveyardManagementModule.java:173`) **[VERIFIED]**, so the second burial places nothing while
the AI still counts the work done (`EntityAIWorkUndertaker.java:467-475`) and clears
`lastGraveData`. The citizen is silently unburied.

Name collisions are not exotic: names come from a fixed first/last list and a colony of a few
hundred over a few hundred days will repeat one.

**Fix**: key the list on something unique, or drop the guard and let two headstones carry the same
name. ~10 lines including the NBT migration. **Neither.**

### F10 — A grave reserved by a graveyard that is then destroyed is reserved for ever. **S. Upstream.**

`unReserveGrave` has exactly one caller (`BuildingGraveyard.java:116`) and `ClearCurrentGrave`
(`:92-95`) does not call it. The reservation is persisted (`GraveManager.java:96-104`)
**[VERIFIED]**. Break the graveyard hut while the undertaker holds a reservation and that grave is
never worked again by any graveyard; it decays. With one graveyard per colony this is a corner, but
nothing forbids two.

**Fix**: un-reserve in `ClearCurrentGrave`, and un-reserve on building removal. ~8 lines.
**Neither.**

### F11 — He picks an arbitrary grave, not the nearest or the most urgent. **S. Upstream. Behaviour.**

`reserveNextFreeGrave` takes the first unreserved entry in `HashMap` iteration order
(`GraveManager.java:222-246`) **[VERIFIED]**. With several graves outstanding after a raid, he will
happily walk 200 blocks past one that is about to decay to reach one that is fresh.

**Fix**: sort candidates by remaining decay time, or by distance from the graveyard, before picking.
~20 lines, and it needs `decay_timer` exposed on `AbstractTileEntityGrave` (it is `protected`; a
getter is a one-liner in the mod's own tree). **Neither.** Not a balance change; it strictly reduces
lost gear.

### F12 — Nothing ever gives the undertaker a totem. **S. Upstream.**

`getTotemResurrectChance` counts totems in the *worker's* inventory
(`EntityAIWorkUndertaker.java:400-407`) **[VERIFIED]**. `keepX` will hold two there once he has them
(`BuildingGraveyard.java:86`), and the dump respects it (`AbstractBuilding.java:1204-1247`) — but
nothing puts them there. The building files no request; the AI never gathers one; the only routes
are a dead citizen who happened to be carrying one, an item on the ground (he has
`setCanPickUpLoot(true)`, `EntityAIWorkUndertaker.java:97`), or the player opening his inventory
through `ContainerCitizenInventory` and placing them by hand **[VERIFIED]** that the container's
slots are writable (`ContainerCitizenInventory.java:166-180`).

So the entire Raising the Dead research is, in practice, "the player must remember to hand-stock the
undertaker", with no interaction bubble and no GUI hint.

**Fix**: have the building request a totem when the research is on and it holds none — a
`checkIfRequestForItemExistOrCreate` in `startWorking` guarded on `USE_TOTEM`, plus a `keepX` entry
that already exists. ~15 lines. **Neither.** Arguably a balance change: it makes totems a colony
consumable and puts them on the courier's route.

### F13 — The totem is matched by item identity, not by the component 26.3 uses. **S. Upstream/port.**

Three sites hardcode `Items.TOTEM_OF_UNDYING` (`EntityAIWorkUndertaker.java:342,402`,
`BuildingGraveyard.java:86`) **[VERIFIED]**. In this snapshot the totem's behaviour lives in the
`DataComponents.DEATH_PROTECTION` component (`v/core/component/DataComponents.java:210`,
`v/world/item/Items.java:2354-2356`, `v/world/item/component/DeathProtection.java`) **[VERIFIED]**,
and vanilla itself matches on the component (`v/world/entity/LivingEntity.java:1387-1397`). Any
datapack or mod item carrying `death_protection` is invisible to the undertaker.

**Fix**: `stack.has(DataComponents.DEATH_PROTECTION)` in all three places. ~6 lines. **Neither.**
This is exactly the shape of drift the enchanter study flagged (`docs/studies/enchanter.md` §5.1) —
code that still compiles and no longer means what the version it was written for meant.

### F14 — The graveyard's module tab is labelled with the enchanter's translation key. **S. Upstream.**

```java
public Component getDesc() { return Component.translatable("com.minecolonies.gui.workerhuts.enchanter.workers"); }
```
(`GraveyardManagementModuleView.java:68-72`) **[VERIFIED]** — and the diff against upstream is
`ResourceLocation` → `Identifier` only, so the key is theirs. What that key renders as is
**[UNVERIFIED]**: the full language file is a runtime asset and the in-tree `en_us.json` does not
contain it.

**Fix**: one line, plus a key. **Neither.**

### F15 — He cannot stop to eat for the whole grave-to-headstone chain. **S. Upstream.**

§8.4. Five consecutive states are `isOkayToEat() == false`
(`AIWorkerState.java:23,169,174,179,184`) **[VERIFIED]**. Worst case a little over four real
minutes with skills at 1.

**Fix**: flip `EMPTY_GRAVE` and `BURY_CITIZEN` to `true` — they are both "stand here and swing"
loops that survive being resumed, because `effortCounter` is a field and is not reset on state
change. Two enum arguments. **Neither.** Not a balance change.

### F16 — Smaller things, verified and not worth their own section

* **The water branch's search box cannot contain the air it just found.** `GraveManager.java:268-280`
  looks up to 10 blocks up for air, then searches with `verticalRange = 1` centred on the *drowned
  body*. For a citizen who drowns in open water the search is three y-levels of water and always
  fails. Making it `findAround(pos, 12, 16, ...)` — or centring on the air block it found — is one
  line and turns "grave in water" from a message into a grave. **[VERIFIED]**
* **`ClearCurrentGrave()` leaks a reservation** — F10, listed again because it is a two-line
  companion fix.
* **`effortCounter` carries between tasks.** It is one field shared by emptying, resurrecting and
  burying (`EntityAIWorkUndertaker.java:63`), reset only on completion (`:206,335,464`). A state
  abandoned midway — inventory filled during `EMPTY_GRAVE`, `IDLE` from a null grave — leaves the
  partial credit behind for whatever runs next. **[VERIFIED]** Harmless in practice; it makes the
  next task finish sooner, never later.
* **`XP_PER_WANDER = 2` is dead** (`UndertakerConstants.java:21`); nothing reads it. **[VERIFIED]**
* **`IGraveManager#removeGrave` is dead**; nothing calls it. **[VERIFIED]**
* **`reserveNextFreeGrave` removes stale entries without `colony.markDirty()`**
  (`GraveManager.java:232-237`) — the removal can be lost across a save. **[VERIFIED]**
* **Two hardcoded English strings in the graveyard GUI**: `"Grave of "` and `"Unknown Citizen"`
  (`GraveyardManagementWindow.java:96-99`). **[VERIFIED]**
* **`EntityCitizen#dropEquipment` is unreachable** (§2.4). Leave it, but a comment saying so would
  save the next reader the trip.

---

## 12. Things that are fine

Verified, and deliberately not findings:

* **The grave never eats items on the normal path.** `transferAllItemHandler` removes each stack from
  the source only after the target accepted it, and the fallback drops the remainder
  (`InventoryUtils.java:2617-2633`, `GraveManager.java:301-304`). A 54-slot grave against a 27-slot
  citizen means the fallback never fires in practice.
* **A broken grave spills its contents.** `AbstractTileEntityRack#preRemoveSideEffects` is the one
  hook in 26.2+ that still sees the block entity, and this fork moved the drop there with a PORT-NOTE
  explaining exactly why (`BlockMinecoloniesGrave.java:203-216`). Good port work; without it a broken
  grave would have swallowed everything.
* **The grave block has no collision box**, so a grave that spawns in a doorway or a path does not
  block anyone (`BlockMinecoloniesGrave.java:184-188`).
* **The grave block has no loot table, silently and correctly.** Vanilla resolves the missing key to
  `LootTable.EMPTY` rather than throwing (`v/server/ReloadableServerRegistries.java:80-82`).
* **The headstone cannot be stacked on itself and needs a floor**
  (`BlockMinecoloniesNamedGrave.java:150-155`), and its `.noLootTable()` is explicit.
* **The builder does not fight the undertaker over headstone positions.**
  `NamedGravePlacementHandler` places nothing on the builder path and asks for no materials, and
  `doesWorldStateMatchBlueprintState` leaves a real headstone alone on a repair or an upgrade
  (`:42-48,66-74`). Burials survive levelling the hut.
* **The undertaker is exempt from mourning** (`CitizenManager.java:677`) — otherwise the one worker
  who can clear the mourning would be the one stopped by it.
* **Resurrection clears mourning by name, not by id** (`CitizenManager.java:684`), which is the only
  key that survives the id reset.
* **`StuckRescue` covers unreachable graves** without the undertaker knowing anything about it
  (§8.3). This fork's machinery, paying off for a worker it was not written for.
* **The old-age death path deliberately skips the colony misery modifier**, with three paragraphs at
  `EntityCitizen.java:1640-1645` explaining that a generational colony would otherwise grieve itself
  to death. Right call, clearly argued.
* **`getResurrectChance` reads `Skill.Mana` directly** rather than `getSecondarySkillLevel()`
  (`EntityAIWorkUndertaker.java:379`) — which happens to be the same thing, but it is the reading
  that stays correct if the module's skills are ever reshuffled.
* **The two `swingForAttack` renames are the entire AI port.** 530 lines, two changed. That is the
  standard the rest of this branch is being held to.

---

## 13. What a player notices first

In the order a player meets them:

1. **"Grave spawned" in chat, with coordinates on hover, and no grave where the body fell.** The
   grave can be 10 blocks away in any direction including down (§2.2). Players go looking at the
   corpse's position and find nothing.
2. **The undertaker walks home before he walks to the grave.** `startWorking` calls
   `walkToBuilding()` before releasing him (`:120-123`). On a raid across the map that doubles the
   trip.
3. **He is asleep for the entire raid** (§8.2) and only starts collecting when it is over.
4. **He never resurrects anybody.** 2.5%, and the two researches that promise more do nothing
   (§6.1). A player who buys Resurrection Chance I and II and sees no change is seeing the cap.
5. **The graveyard tab says "Enchanter"** (F14).
6. **The resting-citizen list grows for ever and the headstones run out** (§7.2). The second player
   with the same name as somebody already buried gets no stone at all (F9).
7. **A resurrected citizen turns up unemployed and homeless**, and his spouse no longer knows him
   (§9.3).

---

## 14. What I could not verify

* **The duplication's exact runtime shape for damaged armour** (§5.1). The two grave slots alias one
  `ItemStack`; the NBT round-trip separates them. What a player sees between the insertion and the
  first save needs a running server. The fix is the same either way.
* **Whether 1.21.1's `EquipmentSlot` had the same `FEET`/`BODY` index collision.** That vanilla tree
  is not in this repository. The mod-side code is byte-identical, so the answer only changes who to
  blame, not what to fix.
* **What `com.minecolonies.gui.workerhuts.enchanter.workers` renders as.** The full language file is
  a runtime asset (F14).
* **Headstone counts for twenty-one of the twenty-five blueprint styles** (§7.1). Four were counted.
* **Deaths per raid**, which is the only free variable in §9.2. A long server run reading the
  `deaths` statistic against `RaidHistory` would settle it.
* **How often the id-reuse family transfer of §9.3 actually bites.** Reading says it can; only a long
  run with many resurrections says it does.
* **Whether a grave placed just outside the claim behaves any differently** (§2.2). The grave manager
  tracks positions, not claims, so reading says it does not; not tested.
* **Nothing in this study was built or run.** No `tools/mc-build.sh` invocation was made.

---

## 15. Fix sizes at a glance

| § | Finding | Size | Ours/upstream | Balance? |
|---|---|---|---|---|
| F1 | Boots copied into the grave twice | S (~5) | Upstream | **Yes — removes a dupe** |
| F2 | Resurrection duplicates the armour set | S (~4) | Upstream | **Yes — removes a dupe** |
| F3 | Resurrection cap makes two researches and Mana inert | S (~3) | Upstream | **Yes, sweeping** |
| F4 | Undertaker's Mana cannot grow | S (1 line) | Upstream | **Yes** |
| F5 | Armour destroyed on death outside the claim | S (1 line) | Upstream bug, fix in tree | No |
| F6 | Lava / invisible death destroys everything silently | S (~8) | Upstream | No |
| F7 | Asleep through raids while graves decay | S (~5) | Upstream | Behaviour |
| F8 | Graveyard fills permanently; resting list never shrinks | M (~120) | Upstream | Behaviour |
| F9 | Duplicate name means no headstone | S (~10) | Upstream | No |
| F10 | Reservation leaked when the graveyard goes away | S (~8) | Upstream | No |
| F11 | Arbitrary grave chosen, not nearest or most urgent | S (~20) | Upstream | No |
| F12 | Nothing ever supplies the undertaker a totem | S (~15) | Upstream | **Yes** |
| F13 | Totem matched by item, not by `DEATH_PROTECTION` | S (~6) | Upstream/port | No |
| F14 | Module tab labelled with the enchanter's key | S (1 line) | Upstream | No |
| F15 | Cannot eat for the whole grave-to-headstone chain | S (2 args) | Upstream | No |
| F16 | Water search box, effort carry-over, dead constants, dead method, missing `markDirty`, hardcoded strings | S (~25 total) | Upstream | Mixed |

**No proposal in this study needs a mixin, and none needs an access widener.** Every site named is a
`public` or `protected` member of the mod's own tree. The only vanilla API any of them newly touches
is `DataComponents.DEATH_PROTECTION` (F13), which is a plain public component type already reachable
from mod code. **No upstream MineColonies asset is required by any of them**, and none of them
depends on the integrated server or single player.

The one thing that would grow past S if taken further than described is F8: giving the player a way
to clear a graveyard is a GUI feature in a layout this repository does not own, and doing it properly
means either shipping our own layout or driving it from an existing control.
