# Raids, raiders, mounts and the barbarian camp

Research only. Date: 2026-08-27. Tree: `26.3/` on branch `26.3`, at `27ff1f87`. **No feature code was
written**; nothing was built, nothing was run, no server and no client. Everything below is source
reading plus arithmetic over constants that were read.

## Verdict in one page

**The raider AI is not stupid so much as blind and undirected.** It is a competent state machine
wrapped around three decisions that were never made: raiders cannot see anything more than three
blocks above or below them, they walk to a *uniformly random* building rather than to anything that
matters, and the raid's whole director loop — reinforcement, win check, phase change — runs once
every **500 ticks**. The three together produce the raid everyone recognises: a blob that wanders
past the guard tower to the sawmill, is continuously topped back up to full strength, and ends when
a timer says so. The single highest-value change in this document is not a new feature; it is giving
raiders a vertical search range. §1.3, F1.

**Mounted raiders: yes for horses, no for camels.** The earlier study in
`docs/studies/wishlist-tier2.md` §3 concluded that a mounted raider would sit on its mount unable to
move because `AbstractEntityMinecoloniesRaider` overrides `getNavigation()`. **That conclusion is
wrong**, and §2.5 shows why with the vanilla line that decides it: the *navigator* is not what moves
a mount — `Mob#getMoveControl` is, and it redirects to the vehicle. This is exactly how the cavalry
that shipped two commits ago works. The remaining cost is the four bookkeeping leaks that study
identified correctly, and those are real. **Camels are a different answer.** A camel is slower than
a raider on foot (0.09 against 0.25), its dash is Player-typed and unreachable from a mob rider, and
its 2.375-block height puts the rider's centre exactly 2.0 blocks above a knight's — against a
knight's attack distance of **2** and a raider's of **2.5**. A camel raider would hit guards that
cannot hit back. §2.7.

**The asset constraint decides more of this than anything else.** A raider riding a vanilla horse or
camel costs **zero** files under `assets/minecolonies/**`; the mount renders itself with vanilla art.
A brand-new raider *type* cannot ship at all — its model and texture would have to live in the one
tree this repository is forbidden to contain (`README.md:575-587`). Every proposal below is costed on
that axis and none of them needs upstream art.

**The barbarian camp is a vanilla jigsaw structure with no mod code behind it at all.** Nine
spawners, twelve loot barrels of vanilla village/outpost loot, and mobs that have 20 HP, 0 armour
and 2 damage for ever because nothing ever calls `initStatsFor` on them. It never scales, never
regrows, drops nothing, and is cleared permanently the first time a player with an iron pickaxe
walks in. It is also the cheapest thing in this document to make interesting: the mod's quest system
is fully data-driven and already has a `killentity` objective, a `breakblock` objective and a
**raid-adjustment reward**. §4.

---

## Evidence standard

Copied from `docs/studies/worldmap-chunk-generation.md`:

* **[VERIFIED]** — I opened the file and the cited `file:line` says what I claim, or I ran the thing
  and watched the output.
* **[UNVERIFIED]** — inference I did not confirm. Each one names what would confirm it.

Paths: `26.3/src/main/java/com/minecolonies/` is abbreviated to `mcol/`, `26.3/src/main/resources/`
to `res/`, `26.3/src/main/generated/` to `gen/`. Vanilla sources are read from
`/opt/mc-src-26.3-snapshot-10/net/minecraft/`, abbreviated to `mc/`.

**Version, stated up front.** `mc/SharedConstants.java:16` reads `public static final int
WORLD_VERSION = 5015`. Snapshot-10 is 5015, snapshot-9 is 5011, so this is the right tree — the trap
that caught the world-map study (a directory named `26.3` that was in fact snapshot-9) does not apply
here. **[VERIFIED]** It *does* apply to `docs/studies/wishlist-tier2.md`, which cites `/opt/mc-src`
throughout; every vanilla claim of that study reused below has been re-read against snapshot-10 and
re-cited, and the line numbers differ by one to eighty-eight. §2.5.

**Nothing here was measured.** There is no client and no running server in this environment. Every
performance number in §1.4 is arithmetic over cited constants, and every statement about how a raid
*feels* is a reading of what the code does, not an observation of it doing it.

**Working-tree note.** Two other agents were committing into this checkout while this was written.
`AttackMoveAI.java`, `CavalryCombatAI.java` and `GuardConstants.java` were dirty at the start and
landed as `c5d4a64d` / `27ff1f87` before the citations below were taken; all line numbers in this
document are against `27ff1f87` with a clean tree. **[VERIFIED]** — `git status --short` was empty
when the §2 citations were taken.

---

## 0. What I read

**Mod, end to end:** `mcol/api/entity/mobs/AbstractEntityMinecoloniesRaider.java` (568),
`AbstractEntityMinecoloniesMonster.java` (411), `RaiderMobUtils.java` (304), `RaiderType.java`,
`mcol/core/entity/mobs/aitasks/RaiderWalkAI.java` (147), `RaiderMeleeAI.java` (103),
`RaiderRangedAI.java` (209), `CampWalkAI.java` (57), `EntityAIBreakDoor.java` (159),
`mcol/core/entity/mobs/registry/MobAIRegistry.java` (173),
`mcol/core/colony/events/raid/RaidManager.java` (1431), `HordeRaidEvent.java` (586),
`RaiderConstants.java` (116), `barbarianEvent/Horde.java` (116), `barbarianEvent/BarbarianRaidEvent.java`
(150), `mcol/core/entity/ai/combat/AttackMoveAI.java` (235), `TargetAI.java` (319),
`mcol/core/entity/pathfinding/pathjobs/PathJobRaiderPathing.java` (186),
`mcol/core/entity/pathfinding/Pathfinding.java` (95).

**Mod, in part:** `AbstractShipRaidEvent.java` (the spawner and blueprint blocks),
`pirateEvent/PirateAirRaidEvent.java` (the class comment and the drop cycle),
`pirateEvent/ShipBasedRaiderUtils.java` (waypoints, ship placement, `getLoadedPositionTowardsCenter`),
`mcol/core/colony/Colony.java` (`worldTickSlow`, `checkDayTime`),
`mcol/core/colony/managers/EventManager.java`, `EventStructureManager.java`,
`mcol/core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java` (the vehicle,
too-far and move-control blocks), `PathingStuckHandler.java` (the stuck ladder),
`mcol/core/entity/other/cavalry/CavalryHorseEntity.java` (riding, travel, attachment, conversion),
`mcol/core/entity/ai/workers/guard/CavalryCombatAI.java` (459), `EntityAICavalry.java` (mounting),
`MeleeCombatAI.java` (reach and targeting), `AbstractEntityAIGuard.java` (`isAttackableTarget`),
`mcol/api/compatibility/CompatibilityManager.java` (the monster set),
`mcol/api/configuration/ServerConfiguration.java`, `mcol/api/util/constant/GuardConstants.java`,
`mcol/apiimp/initializer/EntityInitializer.java`, `ModColonyEventTypeInitializer.java`,
`mcol/api/colony/managers/interfaces/IRaiderManager.java`,
`mcol/core/quests/rewards/RaidAdjustmentRewardTemplate.java`.

**Vanilla:** `mc/world/entity/Mob.java` (203–223, 340–355, 707–745),
`mc/world/entity/Entity.java` (2430–2442, 2461–2468, 2752–2758, 3733–3739),
`mc/world/entity/LivingEntity.java` (3042–3050, 3186, 3259–3262),
`mc/world/entity/animal/camel/Camel.java` (all 704), `CamelHusk.java` (all 133), `CamelAi.java` (105),
`mc/world/entity/animal/equine/AbstractHorse.java` (84–95, 364–395, 700–770, 795–830, 925–935,
949–950, 1025–1029), `Horse.java`, `Llama.java`, `ZombieHorse.java`, `SkeletonHorse.java`,
`mc/world/entity/monster/zombie/Husk.java` (90–145), `mc/world/entity/EntityTypes.java` (242–247,
524–527), `mc/world/entity/SpawnPlacements.java` (116),
`mc/world/entity/ai/attributes/Attributes.java` (13, 68–70), `mc/SharedConstants.java`.

**Data:** `res/data/minecolonies/worldgen/{structure,structure_set,template_pool}/{barbarian,amazon,desert}_camp.json`,
`res/data/minecolonies/tags/worldgen/biome/has_structure/*.json`,
`res/data/minecolonies/worldgen/processor_list/placeholder_replacement.json`,
`res/data/minecolonies/structure/camps/*.nbt` (decoded with a throwaway NBT reader in the scratchpad),
`res/data/minecolonies/colony/quests/`, `gen/data/minecolonies/loot_table/entities/`,
`res/minecolonies.accesswidener`, `res/minecolonies.mixins.json`, `26.3/build.gradle:199`.

**Prior work read before writing anything:** `git show 649fbcf5` and `658db068` (PR #8, the earlier
raid audit — two crash fixes, no behaviour change), `docs/studies/wishlist-tier2.md` §3 (undead
camels), `docs/studies/cavalry-vanilla-attack-path.md`, `docs/studies/gatehouse.md`,
`docs/studies/hostile-territory.md`, `docs/studies/territory-mechanics.md`,
`docs/studies/worldmap-chunk-generation.md` (evidence standard).

---

## 1. Question 1 — what the raider AI does, and where it is weak

### 1.1 The whole stack, in one place

Every raider and every camp mob is built the same way. `AbstractEntityMinecoloniesMonster`'s
constructor calls `IMinecoloniesAPI.getInstance().getMobAIRegistry().applyToMob(this)`
(`mcol/api/entity/mobs/AbstractEntityMinecoloniesMonster.java:99`), and the registry hands out five
vanilla `Goal`s and four state AIs by predicate (`mcol/core/entity/mobs/registry/MobAIRegistry.java:48-57`):

| Layer | What it is | Applied to |
|---|---|---|
| `FloatGoal` | vanilla swim | everything except drowned pirates |
| `EntityAIInteractToggleAble(FENCE_TOGGLE)` | opens fence gates | everything |
| `EntityAIBreakDoor` | breaks doors and the mod's own gate blocks | everything |
| two `LookAtPlayerGoal`s | cosmetic | everything |
| `RaiderMeleeAI` | melee combat | everything that is **not** an `IArcherMobEntity` |
| `RaiderRangedAI` | bow combat | everything that **is** an `IRangedMobEntity` |
| `RaiderWalkAI` | travel to the colony | raiders only |
| `CampWalkAI` | wander ±10 of spawn | camp mobs only |

**[VERIFIED]** All of it. The state AIs share one `TickRateStateMachine` per entity
(`AbstractEntityMinecoloniesMonster.java:81`) ticked every `ENTITY_AI_TICKRATE = 5` ticks
(`:267-270`, constant at `mcol/api/entity/citizen/AbstractEntityCitizen.java:73`), with two states,
`NO_TARGET` and `ATTACKING`.

The transitions and their periods, which are the whole behaviour budget:

| State | Transition | Period | Site |
|---|---|---|---|
| `NO_TARGET` | `checkForTarget` | 5 t | `mcol/core/entity/ai/combat/TargetAI.java:53` |
| `NO_TARGET` | `searchNearbyTarget` | **80 t** | `TargetAI.java:54` + `AttackMoveAI.java:44` |
| `NO_TARGET` | `RaiderWalkAI#walk` | **80 t** | `mcol/core/entity/mobs/aitasks/RaiderWalkAI.java:51` |
| `ATTACKING` | `tryAttack` | 5 t | `mcol/core/entity/ai/combat/AttackMoveAI.java:46` |
| `ATTACKING` | `move` | 10 t | `AttackMoveAI.java:47` |

**[VERIFIED]**

### 1.2 The life of a raid, end to end

1. **Nightfall.** `Colony#checkDayTime` fires `raidManager.onNightFall()` on the day→night edge
   (`mcol/core/colony/Colony.java:1040-1044`). **[VERIFIED]** Raids are already night-gated; nothing
   makes them behave differently once the sun comes up.
2. **Should there be a raid.** `raidThisNight` refuses inside `minimumnumberofnightsbetweenraids`
   (default 10) plus a penalty, forces one past `average + 2` (default 14), and otherwise rolls
   `1/(average - minimum)` (`RaidManager.java:1007-1021`; defaults at
   `mcol/api/configuration/ServerConfiguration.java:387-388`). **[VERIFIED]**
3. **How many.** `calculateRaiderAmount` = `1 + min(maxRaiders, raidLevel/60 × difficultyModifier ×
   (1 + 0.05 per nearby player) × U(0.85, 1.15))` (`RaidManager.java:846-862`); `maxRaiders` defaults
   to **80** (`ServerConfiguration.java:385`). `raidLevel` is 5 per adult citizen plus skill/100, plus
   `5 + level²/5` per built building, plus 3 per completed research, all scaled by population fill
   (`RaidManager.java:967-998`). **[VERIFIED]**
4. **Where from.** `calculateSpawnLocation` averages the positions of *loaded* buildings, picks a
   random compass bearing, and walks outward in 48-block steps from the nearest building toward a
   point 500 blocks away, stopping at the first candidate that is ≥ `MIN_BUILDING_SPAWN_DIST` (35)
   plus a per-building level bonus away from everything built (`RaidManager.java:626-707`,
   `:716-769`, `:790-826`). It gives up the moment it steps into an unloaded chunk (`:752-760`).
   **[VERIFIED]**
5. **The approach path.** `createSpawnPath` runs a `PathJobRaiderPathing` from the building nearest
   the spawn point *to* the spawn point, off-thread (`RaidManager.java:602-618`). That job is allowed
   to path through solid blocks at a cost of 30 and to place ladders, with `maxNodes = 20000` and
   `heuristicMod = 5.0` (`mcol/core/entity/pathfinding/pathjobs/PathJobRaiderPathing.java:52-63`,
   `:155-179`). Its result is turned into waypoints every 20 blocks
   (`HordeRaidEvent.java:57`, `:355`; `ShipBasedRaiderUtils.java:421-442`). **[VERIFIED]**
6. **Spawn and mill.** `HordeRaidEvent#onStart` resolves a loaded ground position, drops
   `max(1, hordeSize/5)` vanilla campfires, sets `campFireTime` to 6 or 3, and spawns the horde
   (`HordeRaidEvent.java:346-389`, `:280-296`). The horde is three fixed roles: 10 % chiefs, 30 %
   archers, the rest melee (`barbarianEvent/Horde.java:52-57`; multipliers at
   `mcol/api/util/constant/ColonyConstants.java:76-77`). **[VERIFIED]**
7. **March.** `RaiderWalkAI#walk` asks `RaidManager#getRandomBuilding()` for a target, picks the
   waypoint that is nearer the target than the raider is, and paths to it
   (`RaiderWalkAI.java:76-85`, `:105-120`; `ShipBasedRaiderUtils.java:452-477`). Speed is 1.8 while
   more than 50 blocks from the first waypoint, 1.1 after (`RaiderWalkAI.java:83`, `:119`).
   **[VERIFIED]**
8. **Arrive.** Within 5 blocks of the target the raider switches to wandering inside the building's
   corner box at speed 0.7, with doors, gates and climbables made free (`RaiderWalkAI.java:86-104`).
   **[VERIFIED]**
9. **Fight.** Melee: 2.5 blocks reach, 2.9 at difficulty ≥ 1.9, one hit per 30 ticks, damage straight
   off the `mc_mob_damage` attribute with a per-raider-type damage source
   (`RaiderMeleeAI.java:45-72`; constants `RaiderConstants.java:90-97`). Ranged: a
   `min` 40-block attack distance, 60-tick cadence, arrows that pierce at difficulty > 3 and become
   armour-piercing 10-damage incendiaries against anything that has been flying for 5 ticks
   (`RaiderRangedAI.java:100-164`). **[VERIFIED]**
10. **Reinforce.** Every colony tick the event tops the three role counts back up to their quota from
    a position near the original spawn point, and separately re-spawns any raider standing in a
    non-entity-ticking chunk (`HordeRaidEvent.java:440-461`, `:469-476`). **[VERIFIED]**
11. **End.** Whichever comes first: fewer than 10 % of the initial horde left
    (`HordeRaidEvent.java:463-467`), three in-game days (`:317-324`), the colony losing more than
    half its maximum citizens (`RaidManager.java:1109-1118`), or each raider individually dying of
    old age 30 minutes after it spawned
    (`AbstractEntityMinecoloniesRaider.java:220-223`, `:317-322`; `RaiderConstants.java:36`).
    **[VERIFIED]**
12. **Adapt.** On the next nightfall, losing more than 15 % of maximum citizens drops `raidDifficulty`
    and adds a 40 % delay; losing under 5 % raises it by one. Range 1–14, initial 7
    (`RaidManager.java:886-901`, `:105-112`, `:144`). **[VERIFIED]**

### 1.3 Where it is weak

Findings are ordered by how much they change play, not by how easy they are.

---

**F1 — Raiders cannot see anything more than three blocks above or below them, so a raid does not
fight a wall; it walks past it.**

`RaiderMeleeAI` overrides `getSearchRange()` to return **0** (`RaiderMeleeAI.java:99-102`).
`RaiderRangedAI` does not override it, so it inherits 16 (`TargetAI.java:274-277`). Neither
overrides `getYSearchRange()`, so both get
`MineColonies.getConfig().getServer().guardVerticalVision.get()` (`TargetAI.java:264-267`), which is
defined with **minimum equal to default equal to `GuardConstants.Y_VISION` = 3**
(`ServerConfiguration.java:399`, `GuardConstants.java:33`). Feeding those into
`TargetAI#getSearchArea` (`TargetAI.java:223-249`) with `DEFAULT_VISION = 16`
(`GuardConstants.java:13`) gives:

* melee raider: a box **32 × 6 × 32** centred on the raider;
* archer raider: a box **48 × 6 × 32** (or 32 × 6 × 48), swept one horizontal direction at a time.

**[VERIFIED]** for every constant and for the arithmetic in `getSearchArea`.

Three consequences follow directly:

1. **A guard standing on a four-block wall is invisible to the raiders at its foot.** So is a guard
   in a tower, on a roof, or on any of the raised firing positions players actually build. The raid
   walks under them.
2. **The archer's search box is smaller than its own weapon.** `RaiderRangedAI#getAttackDistance`
   returns `20 × max(difficulty, 2)` — never less than **40 blocks**
   (`RaiderRangedAI.java:58`, `:150-153`) — while its acquisition box reaches 32 in the swept
   direction and 16 the other way. A raider archer will never open fire at its own effective range;
   it can only shoot things that have already come well inside it.
3. **Calling for help is a 20 × 5 × 20 box.** `AbstractEntityMinecoloniesMonster#hurtServer`
   propagates a new attacker's threat to `AABB.ofSize(position(), 20, 5, 20)` — ±10 horizontal, ±2.5
   vertical (`AbstractEntityMinecoloniesMonster.java:285-295`). **[VERIFIED]** A colony archer
   shooting from a tower 30 blocks away therefore draws no reaction from anybody except the raider
   it hit, and that raider cannot see it either.

What would be better, and why: give `RaiderMeleeAI` and `RaiderRangedAI` their own vertical range
(`RangeCombatAI` already does exactly this for colony rangers, raising its own Y range to 28 while
guarding — noted in `TargetAI.java:251-262`), and give `RaiderRangedAI` a `getSearchRange()` that
tracks its attack distance. That single change is what turns "the raid ignores my defences" into
"the raid attacks my defences", and it costs no new content at all. It is a balance change and
belongs behind a config, in the same shape as `guardverticalvision`.

---

**F2 — Raiders walk to a uniformly random building, and the whole horde shares one.**

`RaiderWalkAI#walk` calls `raider.getColony().getRaiderManager().getRandomBuilding()`
(`RaiderWalkAI.java:78`). That method keeps **one** `lastBuilding` field for the entire colony and
re-rolls it only after `max(6, lastRaid.raiderAmount / 3)` calls
(`RaidManager.java:1025-1074`, field at `:212`). The roll is
`buildingArray[random.nextInt(buildingArray.length)]` over `getBuildings().values()` — **no weight,
no filter, unbuilt level-0 huts included** (`:1032-1037`). Compare `isValidSpawnPoint`, which does
skip level 0 (`:794`), and does weight guard towers, houses and the town hall differently
(`:800-817`): the code that decides where raiders *spawn* knows what a colony is made of, and the
code that decides where they *go* does not.

**[VERIFIED]**

The behaviour that produces: a whole horde marches to one arbitrary building — as likely the
stonemason as the town hall — mills inside it for up to 30 seconds (`RaiderWalkAI.java:100-104`),
then the shared target flips and the whole horde marches somewhere else together. `walkTimer` is 240
seconds (`:79`), so a raider that reaches nothing in four minutes simply re-rolls.

The one thing this *does* do right is warn the defence: on each re-roll it finds up to four guards
within 75 blocks of the previous target that answer `canHelp`, and pushes the old target at them as
a temporary patrol point (`RaidManager.java:1039-1063`). That is a good mechanism aimed at a random
building.

Better: weight the pick — town hall, guard towers and the warehouse heavily, level-0 huts not at
all — and let different parts of the horde carry different targets, which the shared-field design
currently forbids. The chief could carry the objective and the melee raiders follow it.

---

**F3 — The raid's director runs once every 25 seconds.**

`HordeRaidEvent#onUpdate` is the whole director: it advances the preparation phase, updates the boss
bar, drains the respawn queue, tops the horde back up, checks the win condition and reapplies the
glow (`HordeRaidEvent.java:423-483`). It is called from `EventManager#onColonyTick`
(`mcol/core/colony/managers/EventManager.java:185-213`), which is called from `Colony#worldTickSlow`
(`Colony.java:620`), which is a transition registered at `MAX_TICKRATE` — **500 ticks**
(`Colony.java:527`; constant at
`mcol/api/entity/ai/statemachine/tickratestatemachine/TickRateConstants.java:11`). **[VERIFIED]**

Consequences, all of them arithmetic over the above:

* **The preparation phase is 150 or 75 seconds of standing at campfires.** `campFireTime` is set to
  6 (spawn inside 100 blocks of centre) or 3 (further) at `HordeRaidEvent.java:367-375`, and
  `prepareEvent` decrements it once per `onUpdate` (`:244-251`). Six × 500 ticks = 150 s.
* **The win check has 25 seconds of latency.** Killing the last raider does not end the raid; the
  next director tick does.
* **Reinforcement is continuous and unconditional.** Line `:454-461`: if the live entity count is
  below the quota, spawn the difference at the spawn point. There is no wave structure and no cap on
  total spawns; the only thing that ends the flow is the counters at `:463-467` going below 10 %,
  and those counters only fall when a *registered* raider dies (`BarbarianRaidEvent.java:76-111`).
  A colony that kills raiders at the border faster than it kills them at the wall does not shorten
  the raid at all.
* **The colony is told the raid is over by a message, not by anything the player did.**

Better: split the director off the 500-tick transition. `Colony.java:529` already registers
`tickImmediateRaids` at `TICKS_SECOND / 4`, so the pattern and the seam both exist; a raid director
at 20 ticks costs one method call per second per active raid.

---

**F4 — There is no behaviour for "I cannot reach my target", only for "I am stuck".**

Two separate give-up paths exist and neither of them tells anybody anything.

* In combat, `AttackMoveAI#move` counts path attempts, and after five failures or one
  `failedToReachDestination()` it subtracts threat and drops the target below 5
  (`AttackMoveAI.java:84-95`). The raider then goes back to `NO_TARGET` and walks off.
* In travel, `RaiderWalkAI` never checks the path result at all. It re-issues a path whenever
  `raider.getNavigation().isDone()` (`RaiderWalkAI.java:105-120`) and waits out the 240-second timer.

What fills the gap instead is `PathingStuckHandler`, configured on every raider at
`AbstractEntityMinecoloniesRaider.java:185-197`: take 0.4 damage per stuck tick, build leaf bridges,
place ladders, and — when `raidersbreakblocks` is on, which is the default
(`ServerConfiguration.java:386`) — break blocks, with `withCompleteStuckBlockBreak(6)`. **[VERIFIED]**

So the answer to "raiders cannot get in" is a lone raider chewing a random six-block hole in the
wall wherever it happened to jam, at a damage cost to itself, with no coordination and no signal to
the rest of the horde. A raid never masses at a breach because nothing records that there is one.
`IColonyRaidEvent` has no notion of a breach point; the only shared geometry it carries is
`getWayPoints()` (`mcol/api/colony/colonyEvents/IColonyRaidEvent.java:44`).

Better: when a travel path comes back with `failedToReachDestination()`, post the last reachable
node to the event, and have `RaiderWalkAI` prefer a posted breach point over a fresh waypoint. That
is the difference between a wall being chewed and a wall being besieged.

---

**F5 — Doors and gates are handled well; walls are not handled at all.**

`EntityAIBreakDoor` is the one genuinely good piece of siege behaviour in the tree. It scales break
time with block hardness, speeds up by one step per five raiders within 5 blocks, is slowed by the
`MECHANIC_ENHANCED_GATES` research, removes the mod's multi-block gates properly through
`AbstractBlockGate#removeGate`, suppresses the stuck handler while breaking, and — best of all —
summons up to four nearby guards to a point three blocks *inside* the gate
(`mcol/core/entity/mobs/aitasks/EntityAIBreakDoor.java:87-158`). **[VERIFIED]**

Everything it does is keyed on `doorPos`, which comes from vanilla's `BreakDoorGoal`. A cobblestone
wall is not a door, so none of it applies. The raid has one good behaviour and it only fires against
the one block type players are least likely to use as their outer defence.

---

**F6 — Difficulty scaling is four multipliers deep and one of them silently swallows the others.**

`getRaidDifficultyModifier()` is
`(raidDifficulty/10 + 0.2) × (config/DEFAULT) × (worldDifficulty/2) × spawnCountAdjustedDifficulty ×
requestedStrength` (`RaidManager.java:1077-1084`). **[VERIFIED]** The pieces:

* `raidDifficulty` is the adaptive term, 1–14, starting at 7 (`:111-112`, `:144-149`), moved by
  citizen losses on the following nightfall (`:886-901`).
* `worldDifficulty/2` means **Easy halves every raid and Hard multiplies it by 1.5** — a larger
  lever than the entire adaptive range at the bottom end.
* `spawnCountAdjustedDifficulty` is the interesting one. When the computed raider count exceeds
  `maxRaiders`, the *excess ratio* is folded into the difficulty instead
  (`RaidManager.java:319-324`). So past the entity cap a colony stops seeing more raiders and starts
  seeing arbitrarily stronger ones, with no ceiling — at raid level 4800 and difficulty 14 the
  arithmetic gives roughly 2× the intended count, i.e. a doubling of every raider's health and
  damage. Nothing in the UI says this happened.

The modifier then drives health `max(10, 10 + raidLevel × 0.025) × modifier`, damage
`2 + modifier × min(raidLevel/400, 3)`, armour `modifier × 1`, and movement speed ×1.2 above 2.4
(`mcol/api/entity/mobs/RaiderMobUtils.java:93-121`; `AbstractEntityMinecoloniesMonster.java:231-240`).
**[VERIFIED]**

On top of that, damage a *player* deals to a raider is rewritten:
`max(damage, (damage - min(damage,7)) + min(damage,7) × 0.03 × maxHealth × (1 + enchant/5))`
(`AbstractEntityMinecoloniesRaider.java:481-490`). **[VERIFIED]** The first 7 points of any player
hit become 21 % of the raider's maximum health, which is why raid health scaling does not make
raiders spongy for a player and does make them spongy for a guard, whose damage goes through
`MeleeCombatAI` unmodified.

---

**F7 — Two smaller things worth writing down.**

* **A raider that tries to path further than `maxpathfindingdistance` is teleported to its
  destination.** `MinecoloniesAdvancedPathNavigate` refuses the job, and for anything that is not an
  `AbstractEntityCitizen` calls `ourEntity.snapTo(dest)` and pauses pathing for 300 seconds
  (`mcol/core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java:409-452`).
  **[VERIFIED]** The default limit is 2000 (`ServerConfiguration.java:423`) and spawn points are
  usually a few hundred blocks out, so this is latent rather than live — but a server that lowers
  the limit for performance turns it into "raiders teleport into the colony". **[UNVERIFIED that any
  real raid reaches this branch; confirming it means logging `recordRefusedTooFar` during a raid.]**
* **Raiders never disengage.** `isWithinPersecutionDistance` measures from the raider's *current*
  position, not from a home point (`RaiderMeleeAI.java:92-96`, 64 blocks;
  `RaiderRangedAI.java:204-208`, 80 blocks; constants `RaiderConstants.java:74-75`). **[VERIFIED]**
  A raider chasing a fleeing citizen re-anchors its leash every step, so it will follow the citizen
  across the world until the 30-minute despawn timer kills it.

### 1.4 What a large raid costs

All arithmetic, no measurement.

**The pathfinding pool is one thread.** `Pathfinding#getExecutor` builds
`new ThreadPoolExecutor(1, 1, 10, SECONDS, jobQueue, …)` over an `ArrayBlockingQueue(10000)`
(`mcol/core/entity/pathfinding/Pathfinding.java:17`, `:59`). **[VERIFIED]** That one thread serves
every citizen in every colony on the server *and* every raider. `MAX_NODES` is 8000
(`mcol/core/entity/pathfinding/pathjobs/AbstractPathJob.java:75`).

**The request rate.** A raider in `NO_TARGET` asks for a path at most once per 80 ticks
(`RaiderWalkAI.java:51`). A raider in `ATTACKING` asks for one whenever the navigation has run dry,
checked every 10 ticks (`AttackMoveAI.java:47`, `:97-103`). So an 80-raider raid in contact is up to
**80 requests per 10 ticks = 8 jobs per tick** onto a single worker that is also serving the
colony's workforce. Peak, not average — a raider with a live path re-uses it. **[UNVERIFIED as a
measurement; confirming it means counting `PathResult#startJob` calls during a raid, which
`PathfindingStats` is already positioned to do.]**

**Target scans.** One `level.getEntitiesOfClass(LivingEntity.class, box)` per raider per 80 ticks
(`TargetAI.java:150`), box 32 × 6 × 32 for melee. 80 raiders is one scan per tick. Cheap as written;
**F1's fix multiplies the box volume by roughly 4–5**, which is the one place where making raiders
smarter also makes them more expensive, and is why the fix belongs behind a config value.

**The director.** `HordeRaidEvent#onUpdate` is O(horde) per 500 ticks — negligible.
`RaidManager#getRandomBuilding` is the exception: on a re-roll it walks every citizen in the colony
and calls `canHelp` on each guard (`RaidManager.java:1044-1055`). With 80 raiders each calling it
once per 80 ticks, and a re-roll every `max(6, 80/3) = 26` calls, that is a full citizen scan roughly
every 26 ticks. Fine at 25 citizens, less fine at 100.

**Entities.** 80 raiders is 80 `Mob`s with a state machine, an advanced navigator and a stuck
handler each, plus up to `max(1, 80/5) = 16` campfire block updates at start-up
(`HordeRaidEvent.java:282`). The raid can also *exceed* 80: `raiderEvent` splits a large raid into
`amount / BIG_HORDE_SIZE` separate events (`RaidManager.java:336`, `BIG_HORDE_SIZE = 20` at
`ColonyConstants.java:88`), each of which independently tops itself back up.

---

## 2. Question 2 — mounted raiders and camels

### 2.1 What rideable mobs vanilla 26.3-snapshot-10 actually has

Read from `mc/world/entity/EntityTypes.java` and the entity classes. All **[VERIFIED]**.

| Mob | Category | Size (w × h) | `MOVEMENT_SPEED` | `MAX_HEALTH` | Notes |
|---|---|---|---|---|---|
| Horse | CREATURE | 1.396 × 1.6 | 0.1125–0.3375 rolled | 15–30 rolled | `passengerAttachments(1.44375)` (`EntityTypes.java:524-527`); base attrs `AbstractHorse.java:368-376`; roll formulae `:925-935` |
| Donkey / Mule | CREATURE | — | same roll | same roll | `AbstractChestedHorse`, chest slots |
| Llama / Trader llama | CREATURE | — | `createBaseChestedHorseAttributes` | — | cannot be steered by a rider at all |
| Skeleton horse | CREATURE | — | 0.2 fixed | 15 | `SkeletonHorse.java:47-49` |
| Zombie horse | CREATURE | — | rolled | 25 | `ZombieHorse.java:53-55`, overrides `isMobControlled` |
| **Camel** | CREATURE | **1.7 × 2.375** | **0.09** | 32 | `EntityTypes.java:242-244`; attrs `Camel.java:118-124` |
| **Camel husk** | **MONSTER** | 1.7 × 2.375 | 0.09 | 32 | `EntityTypes.java:245-247`; `CamelHusk.java` |
| Pig | CREATURE | — | 0.25 | 10 | player + carrot-on-a-stick only (`Pig.java:96-101`) |
| Strider | CREATURE | — | 0.175 | — | lava only |
| Happy ghast | CREATURE | — | 0.05 | 20 | flying, four seats |

Two of these matter. **The camel is the slowest rideable mob in the game** — 0.09 against a
MineColonies raider's own `MOVEMENT_SPEED` of **0.25** (`RaiderConstants.java:81`). **[VERIFIED]** A
raider put on a camel travels at roughly a third of its walking speed.

**Vanilla already ships mounted desert raiders in snapshot-10.** `Husk#finalizeSpawn` gives a
naturally-spawned husk a 10 % chance of an iron spear plus a `CamelHusk` under it plus a `Parched` as
a second passenger, wired with `this.startRiding(camelHusk, true, true)` and
`level.addFreshEntityWithPassengers(parched)` (`mc/world/entity/monster/zombie/Husk.java:108-130`).
**[VERIFIED]** That is a working, shipped, art-complete example of exactly the fantasy being asked
about, and it is the reason the camel-husk model, texture, renderer and sound set all exist without
this repository containing any of them.

### 2.2 How a mob rides another mob and fights from it

Four vanilla methods carry the whole mechanism. All **[VERIFIED]**.

```java
// mc/world/entity/Mob.java:207-209
public MoveControl getMoveControl() {
    return this.getControlledVehicle() instanceof Mob riding ? riding.getMoveControl() : this.moveControl;
}

// mc/world/entity/Mob.java:215-217
public PathNavigation getNavigation() {
    return this.getControlledVehicle() instanceof Mob riding ? riding.getNavigation() : this.navigation;
}

// mc/world/entity/Mob.java:220-223
public @Nullable LivingEntity getControllingPassenger() {
    Entity firstPassenger = this.getFirstPassenger();
    return !this.isNoAi() && firstPassenger instanceof Mob passenger && firstPassenger.canControlVehicle() ? passenger : null;
}

// mc/world/entity/Mob.java:349-355
protected void updateControlFlags() {
    boolean noController = !(this.getControllingPassenger() instanceof Mob);
    this.goalSelector.setControlFlag(Goal.Flag.MOVE, noController);
    ...
}
```

`Entity#getControlledVehicle` is `vehicle != null && vehicle.getControllingPassenger() == this ?
vehicle : null` (`mc/world/entity/Entity.java:3737-3739`), and `Entity#canControlVehicle` defaults to
true unless the type is tagged `NON_CONTROLLING_RIDER` (`Entity.java:2756-2758`). Nothing in this mod
overrides either.

The passenger keeps thinking. `Entity#rideTick` calls `this.tick()`
(`mc/world/entity/Entity.java:2430-2436`), `LivingEntity#aiStep` reaches `serverAiStep`
(`LivingEntity.java:3048-3050`), and `Mob#serverAiStep` ticks `this.navigation` and
`this.moveControl` — **the fields, not the redirecting getters** (`Mob.java:730-742`).

That last detail is the one that decides the whole question, and §2.5 spends it.

**Camel-specific.** `CamelHusk#isMobControlled()` returns `getFirstPassenger() instanceof Mob`
(`CamelHusk.java:34-37`), and the camel's own brain stands down when it is true
(`mc/world/entity/animal/camel/CamelAi.java:105`). `AbstractHorse#isMobControlled` returns false, and
only `ZombieHorse` and `CamelHusk` override it (`AbstractHorse.java:804-806`, `ZombieHorse.java:69`).
**[VERIFIED]**

**The camel's dash is unreachable from a mob rider.** `Camel#executeRidersJump` is `protected`
(`Camel.java:302-315`) and is only ever called from `AbstractHorse#tickRidden(Player controller,
Vec3 riddenInput)` (`AbstractHorse.java:708-727`), which is Player-typed; the other two entry points,
`onPlayerJump` and `handleStartJump`, are the `PlayerRideableJumping` interface
(`mc/world/entity/PlayerRideableJumping.java:4`, `:8`). `getRiddenSpeed`, `getRiddenInput` and
`getRiddenRotation` are all Player-typed too (`Camel.java:269-283`, `AbstractHorse.java:739-757`).
**[VERIFIED]** A mob-ridden camel never dashes, never gallops and never uses the sprint bonus.

### 2.3 What this fork already knows about mounted combat

`CavalryHorseEntity` is a `Horse` subclass, 1177 lines, and it is the worked example. The parts that
matter:

* **A deliberately narrow hitbox.** `SLIM_W = 0.70F`, `BASE_H = 1.6F`, with the comment *"the width
  is deliberately slim to allow 1-wide pathing for cavalry units"*
  (`mcol/core/entity/other/cavalry/CavalryHorseEntity.java:84-90`). **[VERIFIED]** This is not
  cosmetic. **The mod's pathfinder has no entity-width term at all** — `getBbWidth` appears nowhere
  in `AbstractPathJob.java`, and `PathingOptions` has no size field
  (`mcol/core/entity/pathfinding/PathingOptions.java:13-156`). Every path it emits is a 1-block
  corridor, so a mount wider than one block does not fit its own route. **[VERIFIED]**
* **The rider's navigator drives the mount, and the class says so.** The 18-line comment at
  `CavalryHorseEntity.java:540-557` spells out the mechanism: `Mob#getControllingPassenger` makes the
  citizen the controller, `Mob#getMoveControl` then answers with the *horse's* MoveControl when the
  citizen is asked for its own, so the citizen's `MinecoloniesAdvancedPathNavigate` computes the path
  and pushes wanted positions into the horse's movement handler. *"Nothing ever calls moveTo on the
  horse's navigator, so getPath() on it is null for every tick of every ride."* **[VERIFIED]**
* **The navigator already handles mounts generically.** `getOptionsForPathJob()` imports the
  vehicle's pathing options whenever `ourEntity.getVehicle() instanceof Mob riddenMob &&
  riddenMob.getNavigation() instanceof AbstractAdvancedPathNavigate`, with a cavalry-specific tweak
  on top (`MinecoloniesAdvancedPathNavigate.java:484-503`). **[VERIFIED]** The `instanceof` is the
  catch: a mount with a *vanilla* navigator fails it and the rider silently paths with its own
  options.
* **All five `setWantedPosition` call sites go through the redirecting getter**
  (`MinecoloniesAdvancedPathNavigate.java:590`, `:668`, `:965`, `:1051`, `:1062`). **[VERIFIED]**
* **A finished mounted charge AI landed two commits ago.** `CavalryCombatAI` (459 lines,
  `c5d4a64d`) is a complete charge-and-withdraw cycle: `holdsGroundInAttackRange()` returns false
  while mounted so the movement layer never plants the unit (`:121-125`, hooked at
  `AttackMoveAI.java:79` and `:128`), `getCombatMovementSpeed()` returns the gallop factor
  (`:137-141`), `getChargeMultiplier()` scales the blow by
  `user.getRootVehicle().getKnownSpeed().horizontalDistance() * 20` (`:181-191`), `doAttack` starts
  the run-out in the same tick as the blow (`:201-210`), and a dedicated 5-tick `keepCharging`
  transition ends each leg on distance rather than on the navigation running dry (`:262-289`).
  **[VERIFIED]**
* **Conversion from a vanilla horse exists and is 100 lines.** `createFromVanilla` snapshots tame
  state, temper, health, variant and leash, calls `vanilla.convertTo(ModEntities.CAVALRY_HORSE, …)`
  and re-applies everything (`CavalryHorseEntity.java:736-840`). **[VERIFIED]**

**What could be reused for raiders, verbatim or nearly:** the narrow-hitbox mount pattern; the
`getOptionsForPathJob` mounted branch (it is written against `Mob`, not against cavalry — only the
inner tweak is cavalry-specific); the whole of `CavalryCombatAI`'s charge cycle, since
`RaiderMeleeAI` and `MeleeCombatAI` both extend `AttackMoveAI` and `holdsGroundInAttackRange` is
declared on `AttackMoveAI` itself; the `startRiding(mount, true, true)` call
(`EntityAICavalry.java:258`).

**What could not:** `CavalryHorseEntity` itself. It is welded to the colony — `IManagedAnimal`, an
`IAnimalData` registered with the colony's animal manager, a stable, a reservation system, a
`ReturnToStableGoal`, a colony id in synched data (`CavalryHorseEntity.java:79-124`, `:779-781`). A
raider mount wants none of that. The right shape is a sibling class, not a subclass.

### 2.4 The asset constraint, head on

`README.md:575-587` is explicit: `assets/minecolonies/` is All Rights Reserved and **absent from
every commit**; the tree is fetched at install time from a manifest
(`res/assetfetch/manifest.json`). **[VERIFIED]** A build from this repository alone ships no artwork
and Minecraft substitutes placeholders. `res/assets/minecolonies/` contains exactly four PNGs, none
of them a mob texture. **[VERIFIED]**

Blueprints are the opposite case and are **not** affected: `26.3/build.gradle:199` copies
`../26.2/src/main/resources/blueprints` into the jar, and that tree is GPL with no licence file of
its own (`README.md:582-585`). **[VERIFIED]** New blueprints may be added freely.

So the cost ladder for anything raider-shaped is:

| Proposal shape | Asset cost | Verdict |
|---|---|---|
| Existing raider riding a **vanilla horse or camel** | **zero** — the mount is vanilla art, the rider already has (or lacks) its art either way | free of the constraint |
| Existing raider riding a **new `Horse` subclass** | zero *if* it binds to vanilla's `HorseRenderer`; a client registration line, no art | free of the constraint |
| A **new raider type** with its own look | one model + one texture under `assets/minecolonies/**` | **cannot ship.** Rule it out |
| A new **block** or **item** | one model + one texture under `assets/minecolonies/**` | **cannot ship** as art; may ship as behaviour on a vanilla block |

`CamelHuskRenderer` is declared `MobRenderer<Camel, CamelRenderState, CamelModel>` — parameterised on
`Camel`, not `CamelHusk` — so a `Camel` subclass binds to it directly. **[UNVERIFIED at runtime:** I
read the declaration in `mc/client/renderer/entity/CamelHuskRenderer.java`; nothing was rendered.
Confirming means registering the type and starting a client.**]**

### 2.5 The correction to `wishlist-tier2.md` §3

That study's §3.3 is titled *"The trap he did not see, and it is the whole story"*, and its first
claim is:

> **It never consults the vehicle.** A mounted raider keeps issuing paths to its own navigator, which
> is attached to a passenger that cannot move. It will compute a path, fail to follow it, and be
> handed to the stuck handler […] **A mounted raider left naive will sit on its camel taking damage
> and chewing holes in the terrain.**

**This is not what happens, and the file the study itself cites says so.** Movement does not come out
of the navigator; it comes out of the `MoveControl`, and `Mob#getMoveControl` redirects a controlling
passenger's move control to its vehicle exactly as `Mob#getNavigation` redirects the navigator
(`mc/world/entity/Mob.java:207-209`, right above the `:215-217` the study quotes). Every one of the
mod navigator's five `setWantedPosition` calls goes through that redirecting getter
(`MinecoloniesAdvancedPathNavigate.java:590`, `:668`, `:965`, `:1051`, `:1062`). So a raider that
overrides `getNavigation()` still computes its own path and still steers its mount along it — which
is precisely, and only, why the cavalry works: `CavalryHorseEntity.java:540-557` documents the same
mechanism for a citizen, and notes that `AbstractEntityCitizen` also overrides `getNavigation()` and
therefore also does *not* inherit the vehicle redirect. **[VERIFIED]**

Three further corrections and one addition:

* The covariant return type is **not** a blocker. Nothing needs to be widened to `PathNavigation`,
  because nothing needs to read the mount's navigator. The mount's navigator is unused for the whole
  ride, by design.
* `getOptionsForPathJob` is a real defect but a smaller one than described: the fix is to give the
  raider mount a `MinecoloniesAdvancedPathNavigate`, which the mount subclass has to have anyway for
  the hitbox, at which point the existing `instanceof` passes.
* The line numbers in that study are snapshot-9. Re-read against snapshot-10:
  `Entity#canControlVehicle` is `Entity.java:2756` not `:2668`; `Mob#getControllingPassenger` is
  `:220-223` not `:219-222`; `Mob#updateControlFlags` is `:349-355` not `:348-354`; `CamelHusk` is
  `EntityTypes.java:245-247` not `:244-246`. **[VERIFIED]** The facts survived; the citations did not.
* **The study missed `Husk#finalizeSpawn`** (`mc/world/entity/monster/zombie/Husk.java:108-130`) —
  vanilla's own mounted-raider spawn, which is both a template and evidence that the mechanism works
  in a shipping game.

**What that study got right, and which still stands as the real cost:** the four bookkeeping leaks.
Mounts are never registered with the event, so `HordeRaidEvent#onFinish` (`:298-314`) does not remove
them and they outlive the raid; the unloaded-chunk cull (`:469-476`) deletes riders and leaves
orphaned mounts; the respawn path is `RaiderMobUtils.spawn(entry.getA(), 1, …)`, entity type only
(`:440-451`), so a long raid degrades from mounted to unmounted; and `CamelHusk#removeWhenFarAway()`
returns true (`CamelHusk.java:29-32`) where raiders call `setPersistenceRequired()` in their
constructor (`AbstractEntityMinecoloniesRaider.java:164`). **[VERIFIED]** All four re-checked and all
four still hold.

### 2.6 The reach arithmetic, which is what decides camels

Constants, all **[VERIFIED]**:

| Quantity | Value | Site |
|---|---|---|
| Guard melee attack distance | **2** | `GuardConstants.java:250`, used at `MeleeCombatAI.java:541-544` |
| Raider melee attack distance | **2.5**, or **2.9** at difficulty ≥ 1.9 | `RaiderConstants.java:90-92`, `RaiderMeleeAI.java:62-66` |
| The comparison | `user.distanceTo(target) <= getAttackDistance()` — entity centre to entity centre | `AttackMoveAI.java:123-126` |
| Citizen dimensions | 0.6 × 1.8 | `mcol/api/util/constant/CitizenConstants.java:103`, `:107` |
| Camel rider attachment | `dimensions.height() - 0.375` = **2.0** standing | `Camel.java:487-503`, `:510-512` |
| Vanilla horse rider attachment | **1.44375** | `EntityTypes.java:526` |
| Cavalry horse rider attachment | 1.44375 − `SEATING_OFFSET` 0.75 = **0.69** | `CavalryHorseEntity.java:95`, `:438-448` |

The arithmetic — **[UNVERIFIED as a runtime observation; this is geometry over verified constants,
and confirming it means standing a knight next to a mounted raider in game]**:

* **On a camel.** Rider centre sits at ground + 2.0 + 0.9 = **+2.9**. A knight's centre is at +0.9.
  Vertical separation alone is **2.0**, and horizontal separation is at least
  (1.7 + 0.6)/2 = 1.15, giving `distanceTo` ≈ **2.30**. Against a knight's attack distance of 2 that
  is **out of reach**, and against the raider's 2.5–2.9 it is **in reach**. A camel-mounted raider
  hits guards that cannot hit back.
* **On a vanilla horse.** Rider centre at +2.34, vertical separation 1.44, horizontal ≥ 1.0,
  `distanceTo` ≈ **1.75** — inside 2. Reachable.
* **On a cavalry-style narrow horse.** Rider centre at +1.59, horizontal ≥ 0.65,
  `distanceTo` ≈ **0.99**. Comfortably reachable.

One escape hatch exists for the camel, and only one: **guards can attack the mount, if the mount is
a monster.** `AbstractEntityAIGuard#isAttackableTarget` tests membership of the compatibility
manager's monster set (`mcol/core/entity/ai/workers/guard/AbstractEntityAIGuard.java:840-854`), and
that set is built from every entity type whose `getCategory() == MobCategory.MONSTER`
(`mcol/api/compatibility/CompatibilityManager.java:662-680`). **[VERIFIED]** `minecraft:camel_husk`
is MONSTER (`EntityTypes.java:245-246`); `minecraft:camel` and every horse are CREATURE. So:

* a raider on a **camel husk** is survivable — guards kill the 32-HP mount and the rider drops;
* a raider on a **plain camel** or on **any horse** has a mount that colony guards will not target at
  all, and on a camel that makes the pair effectively immune to melee.

### 2.7 The plain answers

**Is mounted raider cavalry feasible? Yes.** With horses, and specifically with a narrow-hitbox
`Horse` subclass in the shape of `CavalryHorseEntity` minus the colony plumbing. The pathfinding
objection that made the earlier study price this at 600–900 lines does not hold (§2.5); what remains
is one mount class (~200–250 lines: hitbox, `MinecoloniesAdvancedPathNavigate`,
`setPersistenceRequired`, rider-yaw slaving), a mount map and four hooks in `HordeRaidEvent`
(register, teardown in `onFinish`, keep mount with rider in the cull, remount on respawn — ~80
lines), a `spawnHorde` override in whichever raid event gets them (~40), and one line in
`RaiderMeleeAI` to answer `holdsGroundInAttackRange()` false so the raider inherits the charge cycle
that already exists. Call it **M, ~350–450 lines**, one new file plus five touched. **Asset cost:
zero** — a horse renders as a horse. **Client work:** one `EntityRendererRegistry` line in
`mcol/core/event/ClientRegistryHandler.java` binding to vanilla's `HorseRenderer`. **No mixin. No
access widener.**

**Are camels usable? Technically yes; as a design, no — and the reasons are specific:**

1. **They are slower than walking.** 0.09 against a raider's own 0.25 (`Camel.java:121`,
   `RaiderConstants.java:81`). Mounting a raider on a camel *slows the raid down*. There is no
   `getRiddenSpeed` bonus available, because that method is Player-typed.
2. **The dash — the one thing that makes a camel interesting — cannot fire.** §2.2. Reaching it needs
   either a `Camel` subclass calling the `protected` method itself, or, on a vanilla camel, exactly
   one access-widener line:
   `accessible method net/minecraft/world/entity/animal/camel/Camel executeRidersJump (FLnet/minecraft/world/phys/Vec3;)V`.
   Permitted by policy, but it buys a 22-block lunge on a mount that cannot path through a colony.
3. **They do not fit.** 1.7 blocks wide against a pathfinder that emits 1-wide corridors and has no
   width term at all (§2.3). A camel raider jams at the first gate and hands itself to the stuck
   handler, which will start breaking blocks.
4. **They break melee.** §2.6. A rider 2.0 blocks above a knight's centre is out of the knight's
   reach and inside its own. On a plain camel, with the mount untargetable, that is not a hard
   raider — it is an unbeatable one for a melee garrison.

So: **an interesting raider or a frustrating one? Frustrating, and specifically so.** The single
thing that would make it interesting — a camel that dashes over a wall — is the one thing the vanilla
API withholds from mob riders. Narrowing and speeding up a camel to fix (1) and (3) means a
`Camel` subclass with a 0.7 hitbox and a horse's speed, at which point it is a horse wearing a camel
model, and the honest version of that idea is *"mounted mummies, on horses, with camel-husk mounts as
a rare non-charging variant in desert raids where the terrain is open and there is no wall to path
through"*. If the desert flavour is the point, the cheapest true-to-vanilla version is to copy
`Husk#finalizeSpawn` outright: put a camel husk under a fraction of the mummies in
`EgyptianRaidEvent#spawnHorde` and accept that they are slow, tall shock troops that only work in the
open. **S, ~60 lines**, zero assets — and it should be tried before anything is built, precisely
because it will show all four problems above within one raid.

---

## 3. Question 3 — five ways to make raids more interesting

Ranked by (value × confidence) ÷ cost. Sizes: **S** under ~150 lines, **M** ~150–400, **L** 400+.
Every one of the five is free of mixins, free of access wideners, adds no file under
`assets/minecolonies/**`, and depends on nothing that only exists on an integrated server. Where
that needs saying twice, it is said.

---

### 1. The raid attacks the defence — sight, reaction and a shared breach

**What the player experiences.** Today a horde walks under a manned wall and past a guard tower to
reach the sawmill, and only fights what physically collides with it. After this, archers on the wall
draw return fire from raider archers at their own range; a raider that takes an arrow tells the
raiders around it where it came from; and when melee raiders cannot get in, they *converge* on the
one place a raider did get through instead of each chewing a private hole. The wall becomes a thing
the raid is attacking rather than scenery it is walking past.

**Why it is better than what happens today.** §1.3 F1, F4 and F5. The current numbers are a 32 × 6 ×
32 acquisition box on a melee raider, the same six-block vertical band on an archer whose weapon
reaches 40 blocks, a 20 × 5 × 20 call-for-help, and no shared record of a breach anywhere in
`IColonyRaidEvent`.

**How it would be built.**
* `RaiderMeleeAI` and `RaiderRangedAI` override `getYSearchRange()` off a new server config
  (`raidervision`, same shape as `guardverticalvision` at `ServerConfiguration.java:399`);
  `RaiderRangedAI` also overrides `getSearchRange()` to track `getAttackDistance()`.
* `AbstractEntityMinecoloniesMonster#hurtServer:289` — replace the fixed
  `AABB.ofSize(position(), 20, 5, 20)` with one sized off the attacker's distance, capped.
* `IColonyRaidEvent` gains `setBreachPoint(BlockPos)` / `getBreachPoint()`, defaulted on the
  interface so no existing implementor changes. `RaiderWalkAI:105-120` posts the last node of a
  `failedToReachDestination()` result and prefers a posted breach over
  `ShipBasedRaiderUtils.chooseWaypointFor`.
* `EntityAIBreakDoor` already posts a gate under attack to four guards (`:117-145`); the same call
  becomes the breach post, so the defence learns about it at the same moment the attackers do.

**Size: M** (~200 lines across five existing files, one new config value).

**Risks.** The search box is the raid's dominant per-entity cost (§1.4) and this multiplies its
volume by roughly 4–5. It must be a config with the stock value as the default, and it should be
raised only while the event is `PROGRESSING`. Second: raiders that can see upward will path upward,
and `PathJobRaiderPathing` places ladders — expect raiders to start climbing walls, which is either
the whole point or a regression depending on the server. Gate the vertical range and the ladder
behaviour together.

---

### 2. Waves with a shape, and a director that ticks

**What the player experiences.** A raid arrives in three recognisable movements: a probe (archers
only, testing the walls), the assault (melee plus chiefs, at the weakest point the probe found), and
— only if the first two failed — a last push. Between waves nothing spawns: the colony gets ninety
seconds to heal, re-arm and move guards, and the horn sounds again. Killing raiders quickly now
actually shortens the raid.

**Why it is better than what happens today.** §1.3 F3. The current event is one blob topped back up
to quota every 500 ticks with no cap and no phase, and the win check has 25 seconds of latency. The
150-second campfire phase is the only pacing the raid has, and it happens before the player can see
anything.

**How it would be built.**
* Move the raid director off `Colony#worldTickSlow`. `Colony.java:529` already registers
  `tickImmediateRaids` at `TICKS_SECOND / 4` through the same state machine — add a
  `tickActiveRaids` transition beside it that calls a new `IColonyEvent#onFastUpdate()`, defaulted
  empty on the interface.
* `HordeRaidEvent` gains a wave schedule replacing the unconditional top-up at `:454-461`: a small
  `record Wave(int bosses, int archers, int raiders, int delayTicks)` list, an index, and a "wave is
  spent" test on the live entity count. `Horde`'s three counters stay exactly as they are, so
  nothing in the NBT (`Horde.java:88-115`) or in the seven `registerEntity`/`onEntityDeath`
  implementations changes.
* The boss bar already exists (`HordeRaidEvent.java:82`, `:404-413`) and can show wave *n* of *m*
  instead of a raw fraction.
* Night versus day falls out for free: `WorldUtil.isDayTime` is what `Colony#checkDayTime:1040` uses,
  so a schedule can simply hold the next wave until dark.

**Size: M** (~250 lines, one new interface default, `HordeRaidEvent` and `Colony` touched).

**Risks.** The 500-tick cadence is also what keeps the raid cheap. The fast director must do only
phase bookkeeping — the per-raider AI stays at `ENTITY_AI_TICKRATE`. Second: a raid that stops
reinforcing can be defeated, which is the point, but it changes difficulty at every raid level at
once; it needs `raidDifficulty` re-tuning (`RaidManager.java:111-112`) or it will feel easier
overnight. **No new assets; no client work** — the boss bar is a vanilla `ServerBossEvent`.

---

### 3. The siege camp — a raid that builds a forward base you can attack

**What the player experiences.** Instead of a handful of decorative campfires, the horde raises a
palisade camp on the ground it spawned on, with spawners inside it. Raiders that fall come back out
of that camp, not out of the air. You can stand on your wall and wait it out, or you can sortie with
your knights, fight through to the camp and break it — and breaking it ends the raid. When the raid
is over the ground goes back exactly as it was.

**Why it is better than what happens today.** Today `spawnCampFires` drops `max(1, hordeSize/5)`
vanilla campfires and deletes them at the end (`HordeRaidEvent.java:280-296`, `:307-310`); the
reinforcement they are meant to represent comes from nowhere. There is no way to attack a raid, only
to survive it.

**How it would be built — and almost all of it already exists.**
* `IEventStructureManager#spawnTemporaryStructure(Blueprint, BlockPos, eventID)` places a blueprint
  *and writes a backup of the ground it covers* to
  `blueprints/structbackup/<colony>/<dim>/<anchor>.blueprint`
  (`mcol/core/colony/managers/EventStructureManager.java:86-124`). `EventManager#onColonyTick` calls
  `structureManager.loadBackupForEvent(event.getID())` the moment the event goes `DONE`
  (`EventManager.java:192-198`, restore at `EventStructureManager.java:126-170`). **[VERIFIED]**
  This is the exact mechanism ship raids use.
* The spawner half is `AbstractShipRaidEvent`: a `List<BlockPos> spawners`, `addSpawner` populated by
  `CreativeRaiderStructureHandler` during placement, `onUpdate` ending the raid when
  `spawners.isEmpty() && raiders.isEmpty()` (`:269`), `onTileEntityBreak` removing a broken spawner
  and advancing the boss bar (`:352-356`), and a kill-threshold that retires a spawner as the raid
  wears down (`:389-395`). `IColonyRaidEvent#addSpawner` is already on the interface and is a no-op
  in `HordeRaidEvent` (`HordeRaidEvent.java:565-569`) — the seam is cut and unused. **[VERIFIED]**
* The work is therefore: lift the spawner bookkeeping out of `AbstractShipRaidEvent` into a shared
  base (or a small `RaidSpawnerSet` helper), have `HordeRaidEvent#onStart` place a camp blueprint at
  `spawnPos` instead of scattering campfires, and route the top-up through the spawners.
* **Blueprints, not assets.** Three new `.blueprint` files (small/medium/large) under
  `26.2/src/main/resources/blueprints/minecolonies/original/decorations/camps/`, which
  `26.3/build.gradle:199` already copies into the jar. That tree is GPL (`README.md:582-585`).
  Nothing at all is added under `assets/minecolonies/`.

**Size: L** (~450 lines of Java plus three blueprints).

**Risks.** The backup/restore is destructive by design: it snapshots the footprint at placement and
writes it back at the end. Ship raids get away with it because they place on open water; a ground
camp can land on something a player builds *during* the raid, and that build will be erased on
restore. Mitigation is a footprint check against `colony.getServerBuildingManager().getBuildings()`
before placing — `RaidManager.isValidSpawnPoint` (`:790-826`) is already exactly this test and can
be reused with the camp's radius. Second risk: the file write is per-event and synchronous
(`StructurePacks.storeBlueprint`, `EventStructureManager.java:114`); a very large camp on a busy
server is a hitch. Keep the camps small. **No mixin, no access widener. Client work: none** — every
block placed is vanilla or an existing mod block.

---

### 4. Objectives and withdrawal — a raid that wants something and then leaves

**What the player experiences.** The raid announces what it is after: *"They are making for the
warehouse."* Raiders converge on that building instead of a random one, and when they have what they
came for — or when their objective is denied for long enough — they turn round and walk back the way
they came, and the raid ends. You can win by defending one place. You can lose without losing a
single citizen.

**Why it is better than what happens today.** §1.3 F2. The target is `random.nextInt(buildings)`
shared by the whole horde, level-0 huts included, re-rolled every 26 requests. And there is no
withdrawal at all: raiders are only ever removed by death, by the 3-day timer, or by dying of
`DamageSourceKeys.DESPAWN` 30 minutes after they spawned
(`AbstractEntityMinecoloniesRaider.java:317-322`).

**How it would be built.**
* `IColonyRaidEvent` gains `getObjectivePos()` defaulted to `null`. `RaiderWalkAI:78` prefers it over
  `getRandomBuilding()`. Nothing else in the walk AI changes — it already paths to a `BlockPos`
  through waypoints.
* `HordeRaidEvent` gains an objective chosen at `onStart` from the buildings the colony actually has,
  weighted the way `isValidSpawnPoint` already weights them (`RaidManager.java:800-817` is the
  template): town hall, warehouse, guard tower.
* Withdrawal is a `withdrawing` flag that makes `RaiderWalkAI` path to `getSpawnPos()` and, on
  arrival or on a timeout, sets `EventStatus.DONE`. The event already owns `spawnPoint` and the
  waypoint list, so the route home is the route in, reversed.
* The plunder variant needs one inventory touch: `InventoryUtils` against the target building's
  handler. **This must be config-gated off by default** and must skip anything the building's
  `MinimumStockModule` reserves.
* The "keep them out for N minutes" variant needs no inventory work at all and is the one to build
  first.

**Size: M** for the deny-and-withdraw version (~250 lines); **L** if plunder is included.

**Risks.** Taking items out of a player's warehouse is the most complained-about mechanic in this
genre and it is not recoverable from — default it off and price it in single stacks. Second: a horde
that converges on one building is a horde that ignores everything else, which makes a single
well-defended chokepoint trivially correct; pair this with proposal 1 so the raid still fights what
shoots at it on the way. Third: withdrawal interacts with the respawn top-up
(`HordeRaidEvent.java:454-461`) — it must be suppressed once `withdrawing` is set or the raid will
reinforce its own retreat. **No assets, no mixin, no access widener.** Client work: none; the
announcement is a `MessageUtils…forManagers()` line like every other raid message.

---

### 5. Terms — the chief demands a levy, and the colony can pay it

**What the player experiences.** A raid that has taken the outer ring but not the town hall stops
and the chief makes a demand: so many iron ingots, or so much food, delivered to the town hall inside
five minutes, and the horde leaves. Pay and they walk away and do not come back for a fortnight;
refuse and the next raid is angrier.

**Why it is better than what happens today.** The game already has an unnegotiated version of this
and the player has no part in it: lose more than half your maximum citizens and every raid event is
force-ended (`RaidManager.java:1109-1118`), a "mercy" message is sent, and the next raid is delayed
by twice the average gap (`:1212-1216`). The mechanic exists; it just fires on the worst possible
trigger and gives the player no agency.

**How it would be built.**
* `IChiefMobEntity` already exists as the marker (`mcol/api/entity/mobs/IChiefMobEntity.java`), and
  `HordeRaidEvent` already tracks bosses in their own map (`:89`).
* The demand is a `MessageUtils.format(...).withPriority(DANGER).sendTo(colony).forManagers()` in the
  same shape as `HordeRaidEvent.java:381-384`; the price is computed off
  `RaidManager#getColonyRaidLevel()` (`:967-998`) so it scales with what the colony can afford.
* Acceptance is an inventory poll of the town hall building on the fast director from proposal 2, or
  on the existing 500-tick tick if proposal 2 is not taken.
* On payment: `EventStatus.DONE` plus `extraDaysToNextRaid` (field at `RaidManager.java:238`, already
  written by the mercy path at `:1215`). On refusal: `raidDifficulty++`, clamped at
  `MAX_RAID_DIFFICULTY` (`:112`) — the same lever the nightfall adaptation uses.
* The quest system already has the reward primitive for the peaceful half:
  `RaidAdjustmentRewardTemplate` shifts `nightsSinceLastRaid`
  (`mcol/core/quests/rewards/RaidAdjustmentRewardTemplate.java:42-46`). **[VERIFIED]**

**Size: S–M** (~150 lines, `HordeRaidEvent` and `RaidManager` touched, two lang keys).

**Risks.** The obvious one: if paying is cheaper than fighting, every player pays every time and
raids stop being combat content. Price it off raid level and cap how often a colony may buy its way
out — `RaidHistory` already persists every raid the colony has ever had (`RaidManager.java:1293-1379`)
so the count is free. Second: multiplayer. The demand must be visible to every colony manager, not
to whoever happens to be online; `MessageUtils…forManagers()` is the existing seam and it already
handles that. **No assets, no mixin, no access widener, no single-player dependence.**

---

## 4. Question 4 — the barbarian camp

### 4.1 What it is

**There is no Java behind it.** The camp is a vanilla jigsaw structure defined entirely in data, and
nothing in `mcol/` places, ticks, tracks or removes it. The complete definition:

| File | Contents |
|---|---|
| `res/data/minecolonies/worldgen/structure/barbarian_camp.json` | `minecraft:jigsaw`, `size: 1`, `step: surface_structures`, `terrain_adaptation: beard_box`, `project_start_to_heightmap: WORLD_SURFACE_WG` |
| `res/data/minecolonies/worldgen/structure_set/barbarian_camp.json` | `random_spread`, `spacing: 55`, `separation: 25`, `salt: 1223366777` |
| `res/data/minecolonies/worldgen/template_pool/barbarian_camp.json` | two elements, weight 1 each: `camps/large_barbarian_camp`, `camps/small_barbarian_camp` |
| `res/data/minecolonies/tags/worldgen/biome/has_structure/barbarian_camp.json` | plains, forests, windswept hills, meadow |

**[VERIFIED]** all four. Two siblings exist on identical placement settings — `desert_camp` (`#c:is_desert`,
one element) and `amazon_camp` (`#minecraft:is_jungle`, `size: 7`, a real jigsaw with corridors,
shafts, ladders, TNT traps and two boss rooms — 17 template files under
`res/data/minecolonies/structure/camps/amazon/`). All three share `salt: 1223366777`,
`spacing: 55`, `separation: 25`, so they compete for the same chunk in each 55 × 55-chunk region and
only the one whose biome tag matches can land. **[VERIFIED]**

**What is inside**, decoded from the NBT (a throwaway reader, scratchpad only, nothing committed):

| Structure | Size | Spawners | Loot containers |
|---|---|---|---|
| `small_barbarian_camp` | 27 × 10 × 26 | 3 × `campbarbarian`, 1 × `camparcherbarbarian`, 1 × `campchiefbarbarian` | 6 × `village_armorer`, 5 × `pillager_outpost`, 1 × `jungle_temple` |
| `large_barbarian_camp` | 29 × 20 × 23 | 4 / 3 / 1 | 7 × `pillager_outpost`, 1 × `jungle_temple` |
| `desert_camp` | 23 × 21 × 29 | 2 × `campmummy`, 2 × `camparchermummy`, 1 × `camppharao` | 4 × `village_armorer`, 3 × `jungle_temple` |
| `big_amazon_pyramid` | 35 × 23 × 35 | 6 / 4 / 3 | 8 × `simple_dungeon`, 4 × `pillager_outpost` |

**[VERIFIED]** Every spawner is a plain `minecraft:mob_spawner` with `SpawnCount: 4`,
`SpawnRange: 4`, `MaxNearbyEntities: 6`, `RequiredPlayerRange: 16`, `MinSpawnDelay: 200`,
`MaxSpawnDelay: 800`, `SpawnPotentials: []`. Every loot table is a **vanilla** one. There is also
scenery — beds, campfires, red banners with a skull pattern.

### 4.2 What the mobs do

The `camp*` entity types are registered exactly like the raider ones —
`MobCategory.MONSTER`, `.notInPeaceful()`, citizen dimensions
(`mcol/apiimp/initializer/EntityInitializer.java:281-403`). **[VERIFIED]** They differ from raiders in
four ways, and every one of the four is a consequence of them extending
`AbstractEntityMinecoloniesMonster` rather than `AbstractEntityMinecoloniesRaider`:

1. **They wander, they do not march.** `CampWalkAI` caches a ±10 × ±5 × ±10 box around
   `getSpawnPos()` and walks randomly inside it at speed 0.6 every 600 ticks
   (`mcol/core/entity/mobs/aitasks/CampWalkAI.java:140-158`). They will chase a target up to 64
   blocks (`RaiderMeleeAI.java:92-96`) and then walk home.
2. **They never scale.** `RaiderMobUtils.setMobAttributes` — the only caller of `initStatsFor` — is
   invoked from `AbstractEntityMinecoloniesRaider#registerWithColony`
   (`AbstractEntityMinecoloniesRaider.java:410-420`), which camp mobs never reach. So they keep the
   registered defaults: `Attributes.MAX_HEALTH` **20** (`mc/world/entity/ai/attributes/Attributes.java:68-70`),
   `Attributes.ARMOR` **0** (`Attributes.java:13`), `mc_mob_damage` **2.0**
   (`RaiderMobUtils.java:47-48`), and `getDifficulty()` hard-coded to **1**
   (`AbstractEntityMinecoloniesMonster.java:394-397`). **[VERIFIED]** Forever, in every world, at
   every game stage.
3. **A camp chief is a barbarian with a fancier sword.** The chief-sword speed aura requires
   `difficulty > CHIEF_SWORD_SPEED_DIFFICULTY = 2.0` and lives in
   `AbstractEntityMinecoloniesRaider#aiStep` (`:329-344`), which camp mobs do not have. Their
   difficulty is 1. The aura never fires. **[VERIFIED]**
4. **They drop nothing.** `gen/data/minecolonies/loot_table/entities/` holds 18 files —
   `barbarian.json`, `chiefbarbarian.json`, `mummy.json` and so on — and **not one `camp*.json`**.
   **[VERIFIED]** The raider tables are worth having (ancient tomes, the chief sword, iron and
   diamond weapons); the camp mobs that look identical give you nothing but the 5 XP from
   `BARBARIAN_EXP_DROP` (`RaiderConstants.java:13`).

They do persist. `setPersistenceRequired()` is called in the base constructor
(`AbstractEntityMinecoloniesMonster.java:97`), so a camp mob never despawns, and the type is
`notInPeaceful()` so it vanishes on Peaceful and comes back on any other setting. **[VERIFIED]**

### 4.3 Can a player clear it permanently? Yes, completely, on the first visit.

Nothing regenerates. The structure is placed once at chunk generation; the spawners are ordinary
`minecraft:spawner` blocks that a pickaxe removes; the mobs are persistent so they can be killed once
and stay killed. There is no camp state anywhere in the colony NBT, no tick handler, no respawn.
**[VERIFIED]** by the absence of any Java referencing the camp structures — the only mod code that
mentions camps is the entity classes and their registration.

Concretely: nine spawners and about twenty 20-HP mobs, protecting twelve barrels of village-armourer
and pillager-outpost loot. A player in iron with a bow clears a barbarian camp in a few minutes and
never has a reason to look at another one. **[UNVERIFIED as play experience; this is what the
numbers say, not something observed.]**

### 4.4 What could be done with it

Sizes as in §3. All five respect the constraints; the asset column is the interesting one, because
the two best ideas here need **no code at all**.

---

**C1 — Give the camps a reason to exist: quests. Size S, and zero Java.**

The quest system is entirely data-driven JSON under `res/data/minecolonies/colony/quests/`, and it
already has every primitive this needs. `minecolonies:killentity` takes an `entity-type` and a `qty`
(worked example at `.../tutorial/military/zombies.json`), `minecolonies:breakblock` exists
(`mcol/core/quests/objectives/BreakBlockObjectiveTemplate.java`), rewards include items, happiness,
skills, research — and **`minecolonies:raidadjustment`, which moves `nightsSinceLastRaid`**
(`mcol/core/quests/rewards/RaidAdjustmentRewardTemplate.java:42-46`). Triggers include
`minecolonies:random` and `minecolonies:worlddifficulty`
(`mcol/core/quests/triggers/`). **[VERIFIED]**

So: a colonist asks you to clear the camp over the hill — kill 12 `minecolonies:campbarbarian`,
break 5 `minecraft:spawner` — and pays in a raid-free fortnight plus loot. That is three JSON files
and two lang keys. It makes the camp a colony objective instead of scenery, and it is the single
cheapest thing in this entire document.

**Risks.** The quest cannot check that a camp is actually nearby — there is no position trigger in
the trigger registry — so the objective has to be worded as "kill barbarians", not "go to the camp at
X". **No assets, no Java, no mixin.**

---

**C2 — Make the camp scale, and make it drop things. Size S.**

Two independent halves.

*Stats.* Camp mobs need `initStatsFor` called on them once. The natural hook is
`AbstractEntityMinecoloniesMonster#finalizeSpawn`, scaling off world difficulty and distance from
world spawn rather than off a colony (they have none). Roughly fifteen lines plus a difficulty
curve. This is what turns a camp from a one-time chore into content that stays relevant.

*Loot.* Eighteen entity loot tables exist and none of them is a camp mob
(`gen/data/minecolonies/loot_table/entities/`). Adding `camp*` tables is datagen —
`mcol/core/generation/defaults/` is where the existing ones are produced. Point them at the same
pools as their raider twins so a camp yields ancient tomes and the chief sword.

*Chest loot.* Replace the four vanilla tables with a `minecolonies:chests/raider_camp` that carries
things a colony wants — the mod's own materials, blueprints, ancient tomes — rather than village
armourer stock. Data only.

**Risks.** Scaling a camp off world difficulty makes it swingy on servers that change the setting.
Scaling off distance from spawn is the more predictable axis. **No assets** — every item named is
either vanilla or an existing mod item with existing art. **No mixin.**

---

**C3 — A camp left standing raids you. Size M.**

This is the loop that makes a camp a strategic object rather than a dungeon. A camp within N chunks
of a colony that has not been cleared raises that colony's `raidDifficulty` a little each week, and
eventually launches a raid **from the camp's own position**.

The launch is nearly free: `IRaiderManager#raiderEvent(RaidSettings)` takes an explicit `location`
(`mcol/api/colony/managers/interfaces/IRaiderManager.java:213-220`) and `RaidManager#raiderEvent`
uses it verbatim as the sole spawn point (`RaidManager.java:329-333`), with `forcedSpawn` bypassing
the `canRaid()` gate (`:297-300`). **[VERIFIED]** So "the camp attacks you" is one call.

The work is knowing a camp is there. `ServerLevel#structureManager().findNearestMapStructure` is the
vanilla route and is public; the alternative is to register the camp positions when their chunks
generate. Either way this needs a small per-colony record of known camps, persisted alongside
`raidHistories` in `RaidManager#write` (`:1122-1135`), which is already the pattern.

**Risks.** A colony founded next to a camp would be attacked from a fixed, close position
repeatedly, which `isValidSpawnPoint` normally prevents (`:790-826`) — the camp raid must respect the
same minimum distances or it will spawn inside the walls. Second: this makes world generation a
difficulty input, which is fine but must be visible to the player, or "why is my colony being raided
every week" has no discoverable answer. **No assets, no mixin, no access widener.**

---

**C4 — Camps that re-arm. Size M.**

A camp that has been attacked and not finished re-arms itself: surviving spawners get their
`RequiredPlayerRange` and `SpawnCount` raised, a chief is added, and a palisade blueprint is placed
around it. All of that is block-entity NBT on a vanilla `SpawnerBlockEntity` plus one blueprint
placement, and the placement machinery is the same
`IEventStructureManager#spawnTemporaryStructure` proposal 3 uses — except that a camp upgrade should
be *permanent*, so it wants a plain `StructurePlacer` rather than the backup path.

**Risks.** A camp that grows toward the player is a horror-game mechanic and needs a hard ceiling, or
players who ignore one for a hundred days come back to a fortress. Cap the escalation at two steps.
**Blueprints, not assets.** **No mixin.**

---

**C5 — Let the colony raid the camp. Size L, and the one to do last.**

Guards can already be sent to a position: `AbstractBuildingGuards#setTempNextPatrolPoint` is what the
raid uses to pull defenders toward a threatened building (`RaidManager.java:1061`,
`EntityAIBreakDoor.java:143`). A "sortie" order that sends a barracks' garrison to a known camp
position and brings them back is a building setting plus a patrol-target override, not new AI. The
barracks already has border-patrol modes keyed off hostile territory
(`mcol/core/colony/buildings/workerbuildings/BuildingBarracks.java:341`), which is the closest
existing shape.

**Risks.** This is a whole feature — guards away from the colony, casualties, a raid arriving while
the garrison is out — and it should follow C1–C3 rather than lead them. **No assets; a GUI setting
needs client work**, and the GUI is one of the areas where the missing `assets/minecolonies/**` tree
bites hardest.

---

## 5. Considered and rejected

| Idea | Why not |
|---|---|
| **A new raider faction with its own look** (e.g. a mounted steppe horde) | Needs a model and a texture under `assets/minecolonies/**`. This repository may not contain them, and a build from it alone would ship a placeholder-textured mob. Ruled out by policy, not by cost. §2.4 |
| **Camels as the headline mounted raider** | §2.7. Slower than walking, dash unreachable from a mob rider, 1.7 blocks wide against a pathfinder with no width term, and a reach asymmetry that puts the rider outside a knight's 2-block attack distance while the raider's own is 2.5. |
| **Raids that come out of hostile territory** | `docs/studies/gatehouse.md:16-19` records this as being built by another agent; it is not in the 26.3 tree (`RaidManager.java` never mentions `HostileTerritory`, and the only consumers of the territory index are `BuildingBarracks` and the world map). Left alone rather than re-proposed. |
| **Widening `getNavigation()` to `PathNavigation` across the nine entity base classes** | The earlier study rules this out and is right, but for the wrong reason — it is unnecessary, not merely expensive. Nothing reads the mount's navigator. §2.5 |
| **Putting mounted raiders on the vanilla kinetic-weapon spear path** | Already studied and already answered: `docs/studies/cavalry-vanilla-attack-path.md` prices it at 260–570 lines for a net *loss* of damage output, with knockback as the only thing it uniquely buys. The same arithmetic applies from the raider side. |
| **Adding a fourth role to `Horde`** for mounted raiders | `Horde` is three ints and three NBT keys (`Horde.java:23-38`, `:88-94`), and the same three-way `instanceof` chain is copy-pasted into seven event classes. Mounting a fraction of the *existing* roles costs none of that, which is the shape §2.7 recommends. |
| **A mixin for explosion or block-break control during sieges** | Standing ban; the one grandfathered mixin is `PackRepositoryMixin`, client-side and unrelated (`res/minecolonies.mixins.json`). Every proposal above is plain overrides, data, or blueprints. |
| **Making raiders break walls faster by loosening the stuck handler** | It is already on by default with `withCompleteStuckBlockBreak(6)` (`AbstractEntityMinecoloniesRaider.java:191-196`). Turning it up makes the terrain damage worse without making the raid smarter; §3.1's shared breach point is the version that improves play. |
| **A "day raid" variant** | Raids already fire on the nightfall edge (`Colony.java:1040-1044`) and simply continue into the day for up to three days (`HordeRaidEvent.java:317-324`). A day/night distinction is worth having but is one field on proposal 2's wave schedule, not a proposal of its own. |
| **Reducing the 500-tick colony tick globally** | It is load-bearing for the whole colony, not just raids (`Colony.java:613-624` runs eight managers on it). Proposal 2 adds a raid-only fast transition beside the existing `tickImmediateRaids` one instead. |

---

## 6. What I could not verify

* **Nothing was built and nothing was run.** No Gradle, no server, no client, per the brief. Every
  behavioural claim is a reading of code.
* **No performance figure in §1.4 is a measurement.** The path-job rate, the scan cost and the citizen
  scan cadence are arithmetic over cited constants. Confirming them means instrumenting
  `PathResult#startJob` and `TargetAI#searchNearbyTarget` during a real raid; `PathfindingStats`
  already exists and is the natural place.
* **The camel reach geometry (§2.6) is arithmetic, not observation.** The constants are verified; the
  resulting `distanceTo` values are computed from entity dimensions and attachment offsets. A knight
  standing beside a mounted raider is what would settle it.
* **Whether the ±3 vertical search box produces the "raiders ignore my wall" complaint in play** is
  inference from the box arithmetic. It is a strong inference, but it is inference.
* **The camp spawn frequency** (one attempt per 55 × 55 chunks, ~880 blocks apart) is read off the
  structure set, not observed in a generated world.
* **That the `placeholder_replacement` processor leaves spawners and loot barrels untouched** is
  inferred from the processor list — a `block_ignore` on two Structurize substitution blocks and a
  rule processor with two rules, neither matching a spawner
  (`res/data/minecolonies/worldgen/processor_list/placeholder_replacement.json`). Not observed in a
  placed camp.
* **That a `Camel` subclass binds to vanilla's `CamelHuskRenderer`** is a type-level reading of the
  renderer's declaration. Nothing was rendered.
* **Whether camp mobs' equipment drops** — they are given weapons by `RaiderMobUtils#setEquipment`
  (`:238-285`) and vanilla's default equipment drop chance would apply — was not checked against
  `DropChances`. The claim in §4.2 is specifically that they have **no loot table**, which is
  verified by the file listing.
* **The 26.2/26.3 blueprint copy** (`26.3/build.gradle:199`) was read, not exercised; that new
  blueprints placed in the 26.2 tree reach a 26.3 jar is inference from that one line.
