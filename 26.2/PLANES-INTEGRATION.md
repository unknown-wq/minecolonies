# MineColonies × Simple Planes — integration design & feasibility

**Status:** design report. Nothing here is implemented. No code in either repository was changed to
produce it.

**Companion:** [`PLANES-AIR-DEFENCE.md`](PLANES-AIR-DEFENCE.md) — what a colony can do about an
incoming air strike, including a worked anti-air design. Read that one for the strike path; it
corrects one claim made in §7 below (guards cannot *target* a plane, but an arrow *can* hit one).

**Repositories read**

| | path | module | licence (as declared) |
|---|---|---|---|
| MineColonies | `/home/user/minecolonies` | `26.2/` | `GPL-3.0-only` (`26.2/LICENSE`, `fabric.mod.json:22`, `README.md:470`) |
| Simple Planes | `/workspace/unknown-wq/simple-planes` (read-only) | `26.2/` | `LGPL-3.0-or-later` (`26.2/LICENSE`, `fabric.mod.json:15`) |

**How to read the confidence markers.** Every claim below is one of two things and they are never
mixed inside a sentence:

- **[VERIFIED]** — I opened the file and read the code. The `file:line` is real and was correct at
  the commit this branch was cut from (`ff38237e`). It means *the source says this*. It does **not**
  mean I ran it.
- **[UNCHECKED]** — a reasonable inference about runtime behaviour, cost, or a Minecraft API I did
  not trace to its definition. Treat as a hypothesis.

**Nothing in this report was tested in game.** No server was started, no build was run, no entity was
spawned. Every statement about what happens at runtime — does a raider stay seated on a parachute,
does a route aircraft actually reach a waypoint 400 blocks away, does the raid bar look right — is
**[UNCHECKED]** by construction.

---

## 0. The two things that constrain every entry

### 0.1 Licence: which way code may move

Read the licence files, not the badges.

- MineColonies here is **GPL-3.0-*only*** — `README.md:470` says "version 3 only" and
  `fabric.mod.json` declares `"license": "GPL-3.0-only"`. **[VERIFIED]**
- Simple Planes is **LGPL-3.0-or-later** — `26.2/LICENSE` is the LGPLv3 text, `fabric.mod.json`
  declares `"license": "LGPL-3.0-or-later"`. **[VERIFIED]**

What follows (I am not a lawyer; this is a careful reading of the two licence texts, and where I am
unsure I say so):

1. **MineColonies may link against Simple Planes.** LGPLv3 is, by its own first paragraph, "the terms
   and conditions of version 3 of the GNU General Public License, supplemented by the additional
   permissions listed below" (`26.2/LICENSE:9-11`). GPLv3 §7 lets a downstream conveyor remove
   additional permissions from a copy; removing LGPLv3's additional permissions leaves plain GPLv3.
   A GPL-3.0-only work combined with an LGPL-3.0-or-later work is therefore distributable as GPLv3.
   **[VERIFIED as a reading of the licence text]**
2. **Code may be copied Simple Planes → MineColonies**, relicensed to GPLv3 on the way in, with the
   original copyright notice preserved and the provenance recorded. Same clause as (1).
3. **Code may NOT be copied MineColonies → Simple Planes.** GPL-3.0-only cannot be conveyed under
   LGPL-3.0-or-later; that would be adding permissions the GPL does not grant. Do not lift
   `BlockPosUtil`, `RaiderMobUtils` or anything else out of this repo into the planes repo, not even
   a helper method. **[VERIFIED as a reading of the licence text]**
4. **Therefore: cross-mod glue code lives in MineColonies, or in a third module licensed GPL-3.0.**
   Putting the glue in Simple Planes would make the Simple Planes jar contain a class whose
   combined work with MineColonies is GPLv3, which conflicts with distributing that jar as
   LGPL-3.0-or-later. This is the single most important structural conclusion in the report and it
   decides the "which repo hosts the code" column for almost every entry below.
5. **Uncertain fine point, stated rather than guessed:** whether a *soft, optional, reflection-only*
   bridge in the Simple Planes jar that never has MineColonies on its compile classpath would count
   as a combined work is exactly the question the FSF's plugin/dynamic-linking position turns on, and
   it is contested. I am not confident either way. The safe pattern — glue in the GPL repo — avoids
   having to have an opinion, so use it.

### 0.2 Optional dependency: the precedent this repo already sets

MineColonies already has three distinct mechanisms for optional integration, and they should be
reused rather than invented:

- **Loader check.** `FabricLoader.getInstance().isModLoaded("...")` at
  `api/compatibility/CompatibilityManager.java:847,851,856` and
  `core/colony/buildings/modules/settings/DynamicTreesSetting.java:31,43`. **[VERIFIED]**
- **Proxy + no-op fallback.** `api/compatibility/dynamictrees/DynamicTreeProxy.java` is a concrete
  class of no-ops; the real implementation subclasses it and is swapped in only when the mod is
  present. `api/compatibility/Compatibility.java` holds the static proxy references. **[VERIFIED]**
- **Source-set exclusion.** `26.2/optional-integrations.txt` lists `.java` paths that
  `build.gradle:119-126` excludes from compilation entirely — this is how JEI and JourneyMap are
  parked. A whole compat package can be compiled or not compiled by editing one text file.
  **[VERIFIED]**

There is a fourth seam this repo does not use for mods but does use heavily for content, and it is
the best one available here: **datapack**. Both mods load JSON from `data/<ns>/…` with no compile-time
coupling at all —

- MineColonies: `data/<ns>/crafterrecipes/*.json` (`core/generation/CustomRecipeProvider.java:56`,
  parsed by `core/colony/crafting/CustomRecipe.java`), `data/<ns>/researches/*.json`. **[VERIFIED]**
- Simple Planes: `data/<ns>/plane_payload/*.json`
  (`datapack/PlanePayloadReloadListener.java:34`) and `data/<ns>/plane_liquid_fuels/*.json`
  (`datapack/PlaneLiquidFuelReloadListener.java:30`). **[VERIFIED]**

Anything achievable through datapack costs zero lines of Java in either repo and is automatically
soft in both directions: the JSON simply fails to resolve its item/entity id and the entry is
skipped. Three of the ten entries below are pure datapack for exactly this reason.

---

## 1. Pirates arriving by plane and parachuting into the colony

*(the owner's seed idea — presented first because it was asked for; on a strict value-÷-cost ranking
it sits around fourth, behind three entries that are nearly free.)*

### 1.1 Verdict, up front

**Plausible, and cheaper than it looks — but not free, and not a datapack.** The two halves that
sounded hardest are both already written and neither needs to be built:

- **The unmanned scripted flight already exists**, and it is *not* `GunshipSortie`. Simple Planes has
  a complete fixed-wing autopilot: `autopilot/AutopilotSpawner.java:169
  launchRoute(level, waypoints, cruiseAltitude, legs, airfieldName, owner, cruiseSpeed, blast, type)`
  spawns an aircraft at the first waypoint with no player aboard, fits it a booster, gives it launch
  speed and engages `PlaneAutopilot` to fly the waypoint list. Its own javadoc at
  `AutopilotSpawner.java:24-25` says "Nothing here needs a player… every entry point works from the
  server console, a command block or a datapack function." **[VERIFIED]**
- **A parachute entity already exists.** `entities/ParachuteEntity.java` is a rideable, no-gravity-ish
  descent entity: it takes a `LivingEntity` passenger via `getControllingPassenger()` (line 54), zeroes
  its own `fallDistance` every tick (line 108), clamps descent to −0.1 blocks/tick (line 121), and
  removes itself and drops a parachute item when the block below stops being replaceable (line 73-76).
  It is immune to damage (`hurtServer` returns false, line 63). **[VERIFIED]**

**The single biggest obstacle is not the plane and not the descent. It is that
`HordeRaidEvent` is written on the assumption that raiders are always near a
walkable ground spawn point, and it re-asserts that assumption every colony tick.** Details in §1.6.
Everything else is a couple of hundred lines.

### 1.2 Can `GunshipSortie` fly the drop run?

**No, and it should not be asked to.** `combat/GunshipSortie.java` is a *helicopter* that climbs over
one point, hovers there and shoots. Its own class comment is explicit that this is deliberate: "The
gunship is a **guard post**, not a hunter. It kills what comes within `ENGAGEMENT_RADIUS` of where it
was placed and it does not chase" (`GunshipSortie.java:53-55`), and the controller it flies through,
`combat/HoverControl.java`, deliberately excludes horizontal translation — "Deliberately *not* in this
interface… waypoints, cruise speed, terrain following, obstacle avoidance, runways, and any notion of
horizontal translation beyond `faceTowards`" (`HoverControl.java:23-26`). **[VERIFIED]**

Nor does a "transport sortie" need to be written. The right vehicle is the **route autopilot**, which
already does the whole thing:

| what the drop run needs | what already exists | file:line |
|---|---|---|
| unmanned aircraft, no player | `AutopilotSpawner.launchRoute(...)` | `autopilot/AutopilotSpawner.java:169` |
| flies a list of waypoints at a cruise altitude | `FlightPlan.route(...)` → `PlaneAutopilot.tickCruise` | `autopilot/FlightPlan.java:132`, `autopilot/PlaneAutopilot.java:823` |
| knows when it is *over the drop point* | `if (distanceToWaypoint < arrivalRadius(plane)) { plan.advance(); … }` | `autopilot/PlaneAutopilot.java:866-873` |
| keeps its own chunks loaded far from any player | `AutopilotRegistry` + `PlaneAutopilot.keepChunksLoaded` | `autopilot/AutopilotRegistry.java:50,100-113` |
| leaves afterwards, or crashes harmlessly | `beginLanding` after the last leg; or `Blast(0, false, false)` | `autopilot/PlaneAutopilot.java:870-872`; `autopilot/Blast.java` |
| a big airframe with cargo bays | `AircraftType.CARGO` → `CargoPlaneEntity` | `autopilot/AircraftType.java:69,118-124` |
| drops one bay per call | `CargoPlaneEntity.dropPayload()` — loops `largeUpgrades`, drops the first droppable one, `break` | `entities/CargoPlaneEntity.java:133-146` |

`PlaneEntity.dropPayload()` is a public no-op on the base class (`entities/PlaneEntity.java:1728`),
overridden by `CargoPlaneEntity` and `LargeAirframeEntity`. **A caller outside the mod can call it.**
**[VERIFIED]**

So: **a new transport sortie class is not needed.** What is needed is ~40 lines that build the
waypoint list and ~80 lines that watch the aircraft and decide when to open the doors.

**Two ways to open the doors, and they differ in cost and in flexibility:**

- **(a) Payload upgrades, mostly data.** `upgrades/payload/PayloadUpgrade.java:87 dropAsPayload()`
  spawns `payloadEntry.dropSpawnEntity()`, loads `payloadEntry.compoundTag()` into it, sets its
  position to the plane's and inherits the plane's velocity. `PayloadEntry` comes from
  `data/<ns>/plane_payload/<id>.json` with keys `item` / `block` / `entity` / `entity_nbt`
  (`datapack/PlanePayloadReloadListener.java:56-69`). A JSON with `"entity": "minecolonies:pirate"`
  drops a pirate. **[VERIFIED]**
  **But the NBT is static, baked into the JSON at datapack load.** MineColonies raiders read their
  colony and their raid event out of NBT — `AbstractEntityMinecoloniesRaider.readAdditionalSaveData`
  reads `TAG_EVENT_ID` and `TAG_COLONY_ID` (lines 248-255) and **discards itself if either is
  missing**: `if (colony == null || eventID == 0) { this.remove(DISCARDED); }` (lines 257-260).
  **[VERIFIED]** A datapack cannot know the live colony id or the event id. So route (a) can drop
  *decorative* mobs but **cannot** drop raiders that belong to a raid. Dead end for the seed idea, and
  worth knowing before someone spends a day on it.
- **(b) MineColonies spawns them itself at the aircraft's position.** This is the right one, and it is
  cheaper than it sounds — see §1.4.

### 1.3 Where MineColonies spawns a raid, exactly

The chain, traced end to end:

1. **`RaidManager.raiderEvent(RaidSettings)` — `core/colony/events/raid/RaidManager.java:291`.**
   Decides the raider count (`:302-325`), picks one or more spawn points
   (`calculateSpawnLocation()` at `:606`, or an explicit `raidSettings.location()` at `:329-331`),
   then for each spawn point picks a concrete event class in a long if/else chain (`:375-501`) and
   adds it to the event manager. **[VERIFIED]**
2. **The event-class chain is where an air-drop variant is selected.** Note the precedent already in
   the file: `if (config.skyRaiders.get() && spawnState.isAir() && belowState.isAir()) raidSettings =
   raidSettings.withExplicitType(PIRATE_RAID)` at `:388-393`. There is *already* a config
   (`api/configuration/ServerConfiguration.java:333 skyraiders`, default false) whose whole job is to
   let raids spawn in mid-air. **[VERIFIED]**
3. **`HordeRaidEvent.onStart()` — `core/colony/events/raid/HordeRaidEvent.java:346`.** Resolves the
   spawn path into waypoints, pulls the spawn point back to a *loaded* position with
   `ShipBasedRaiderUtils.getLoadedPositionTowardsCenter(...)` (`:358`, defined at
   `pirateEvent/ShipBasedRaiderUtils.java:315`), places campfires, then calls `spawnHorde`. **[VERIFIED]**
4. **`HordeRaidEvent.spawnHorde(spawnPos, colony, id, bosses, archers, raiders)` —
   `HordeRaidEvent.java:234`.** Three lines, each `RaiderMobUtils.spawn(type, n, spawnPos, world,
   colony, id)`. **[VERIFIED]**
5. **`RaiderMobUtils.spawn(...)` — `api/entity/mobs/RaiderMobUtils.java:133`. This is the seam.**
   Per raider it does: `findAround(world, spawnLocation ± deviation, 5, 5, SOLID_AIR_POS_SELECTOR)`
   (`:152`), falls back to `spawnLocation.above()` if that returns null (`:153-156`), `snapTo(...)`,
   `addEntity`, `setColony`, `setEventID`, `registerWithColony()` (`:158-162`). **[VERIFIED]**

> **A pleasant surprise, verified by reading:** `RaiderMobUtils.spawn` **already works in mid-air,
> unmodified.** `BlockPosUtil.findAround` (`api/util/BlockPosUtil.java:1033`) is bounded — 5 blocks
> vertical, 5 horizontal — and returns `null` when nothing matches (there is no unbounded loop; the
> outer loop is `for (int i = 0; i < verticalRange + 2; i++)`). At a cruise altitude of a hundred-odd
> blocks over open air there is no solid block within that box, so the `SOLID_AIR_POS_SELECTOR` finds
> nothing, `spawnpos` is null, and line 155 falls back to `spawnLocation.above()` — i.e. **exactly the
> position handed in**. Calling `RaiderMobUtils.spawn(PIRATE, 1, planeBlockPos, world, colony,
> event.getID())` from the cargo bay of an aircraft is a legal, unmodified call. **[VERIFIED by
> reading; the arithmetic that "there is no solid block within 5 of the aircraft" is [UNCHECKED] and
> false over a mountain.]**

That single fact removes the largest chunk of the imagined cost. The paradrop does not need a new
spawn function; it needs a new *event* that calls the existing one from a different place.

### 1.4 The design that follows

**Where it lives:** MineColonies, in a new package `com.minecolonies.core.compatibility.simpleplanes`,
listed in `optional-integrations.txt` so it can be compiled out. Justified by §0.1(4): it links
GPL-only code against an LGPL library, which is legal in that direction only.

**How it is gated:** the existing loader-check-plus-proxy pattern.
`AirRaidBridge` is a concrete no-op class in `api/compatibility/` (mirroring `DynamicTreeProxy`);
`SimplePlanesAirRaidBridge extends AirRaidBridge` is in the excludable package and is instantiated
only behind `FabricLoader.getInstance().isModLoaded("simpleplanes")`. When Simple Planes is absent
the bridge returns `false` from `canFlyDrop()` and the event falls straight through to the ordinary
ground `spawnHorde` — i.e. an air-drop raid degrades into a `PirateGroundRaidEvent`, which is a
perfectly good raid. Nothing in the base mod imports a `xyz.przemyk` class.

**The pieces:**

| # | what | where | new lines |
|---|---|---|---|
| 1 | `PirateAirRaidEvent extends HordeRaidEvent` — overrides `onStart()` to ask the bridge for a drop run instead of calling `spawnHorde` at ground level; keeps `registerEntity`/`onEntityDeath` identical to `PirateGroundRaidEvent.java:66-130` | MineColonies, new file next to `pirateEvent/PirateGroundRaidEvent.java` | ~140 |
| 2 | Registry entry, one line, alongside `apiimp/initializer/ModColonyEventTypeInitializer.java:31-38` | MineColonies | ~2 |
| 3 | Selection in the if/else chain at `RaidManager.java:440-451` — one more branch, guarded by `bridge.canFlyDrop()` and a new `skydrop` server config next to `skyraiders` (`ServerConfiguration.java:333`) | MineColonies | ~15 |
| 4 | `AirRaidBridge` no-op proxy | MineColonies (`api/compatibility/`) | ~30 |
| 5 | `SimplePlanesAirRaidBridge` — builds three waypoints (ingress 250 blocks out on a random bearing, drop point over `colony.getRaiderManager().getRandomBuilding()` (`RaidManager.java:1003`), egress 250 blocks beyond), calls `AutopilotSpawner.launchRoute(..., AircraftType.CARGO)`, then registers itself on `ServerTickEvents.END_LEVEL_TICK` and drops one stick of raiders every ~6 ticks while the aircraft is within ~30 blocks of the drop point | MineColonies (excludable package) | ~180 |
| 6 | Descent — see below | MineColonies (same file) | ~25 |

**Total ≈ 390 lines across 5 new files and 2 edited files, all in MineColonies.** Nothing in the
Simple Planes repo changes at all. **[cost estimate: [UNCHECKED]; the file/line targets it is built
from are [VERIFIED]]**

### 1.5 The descent — cheapest thing that looks right

Three options, in ascending cost:

1. **`MobEffects.SLOW_FALLING` on each raider as it is spawned, duration ~200 ticks.** One line.
   Vanilla slow falling both slows the fall and cancels fall damage, so nothing else is needed. No
   reference to any Simple Planes class, so this works even in the no-planes fallback. Looks like a
   drifting descent, not a parachute — no canopy. **[VERIFIED that the raiders have no fall-damage
   override of their own: neither `AbstractEntityMinecoloniesRaider` nor
   `api/entity/other/AbstractFastMinecoloniesEntity` mentions `causeFallDamage`, `fallDistance`,
   `setNoGravity` or `SLOW_FALLING` anywhere.] [UNCHECKED that slow falling reads well visually.]**
2. **Ride a `ParachuteEntity`.** `new ParachuteEntity(level)`, `setPos(plane.position())`,
   `addFreshEntity`, then `raider.startRiding(parachute)`. The entity handles the rest: it decays
   horizontal motion by 0.9/tick, clamps descent at −0.1, zeroes its own `fallDistance`, and on
   touchdown kills itself and drops a parachute item (`entities/ParachuteEntity.java:68-125`).
   **[VERIFIED that the class does this. [UNCHECKED] whether a MineColonies raider stays seated —
   its navigation and goal system are custom (`AbstractEntityMinecoloniesRaider.java:170-199` builds
   a `MinecoloniesAdvancedPathNavigate` with a stuck handler), and I did not trace whether anything
   in that AI calls `stopRiding` while airborne. This is the one runtime unknown that would most
   embarrass the feature, and it is a five-minute test in game.]**
   Cost: ~8 lines, but it is a hard compile-time reference to `xyz.przemyk…ParachuteEntity`, so it
   must live inside the excludable package. That is fine — the bridge is already there.
3. **A MineColonies-owned parachute entity.** Do not. Option 2 is the same thing, already written, and
   the licence permits linking to it.

**Recommendation: (2) inside the bridge, with (1) as the fallback the no-planes build uses anyway.**

### 1.6 What breaks in the raid bookkeeping — the honest list

This is the part that decides whether the feature is a weekend or a fortnight. Each item is traced.

1. **Raid location reporting survives, and is slightly wrong in a way nobody will mind.**
   `RaidManager.raiderEvent` records `new RaidSpawnInfo(raidEvent.getEventTypeID(),
   targetSpawnPoint)` at `:503`; `getLastSpawnPoints()` (`:807`) and
   `CommandColonyRaidsInfo.java:37` read it back. The recorded point is the *ground* spawn point the
   manager chose, not the drop point. `/mc colony raid info` will therefore report a plausible
   compass direction that is not where the pirates actually landed. Cosmetic; fixable by writing the
   drop point into `RaidSpawnInfo` instead. **[VERIFIED]**
2. **The raid bar direction is wrong for the same reason, and this one is visible.**
   `HordeRaidEvent.updateRaidBar()` (`:404-413`) names the bar
   `BlockPosUtil.calcDirection(colony.getCenter(), spawnPoint)`, and `onStart` announces
   `RAID_EVENT_MESSAGE + horde.getMessageID()` with the same direction (`:381-384`). Pirates that
   fall out of the sky onto the town hall will be announced as coming "from the north". The fix is to
   set `spawnPoint` to the drop point in the overridden `onStart` **before** calling
   `updateRaidBar()`. One line, but it must be remembered. **[VERIFIED]**
3. **The "raid is over" condition survives intact.** It is counted from the horde, not from geometry:
   `horde.hordeSize <= 0` → `DONE` (`HordeRaidEvent.java:434-438`), and
   `numberOfBosses + numberOfRaiders + numberOfArchers < floor(initialSize * 0.1)` → `announceWin()`
   → `DONE` (`:463-467`). Decrementing happens in each subclass's `onEntityDeath`
   (`PirateGroundRaidEvent.java:96-130`). None of it looks at where anything spawned. **[VERIFIED]**
4. **Horde-size accounting survives, because `registerEntity` is a counting gate, not a position
   gate.** `PirateGroundRaidEvent.registerEntity` (`:66-93`) accepts a pirate only while
   `boss/archers/normal` are below the horde's quotas and discards the surplus. So dropping 12
   raiders when the horde says 12 is exactly right, and dropping 13 silently deletes one. The bridge
   must drop precisely `horde.numberOfBosses + numberOfArchers + numberOfRaiders`. **[VERIFIED]**
5. **⚠ The respawn/top-up loop is the real problem, and it will put raiders back on the ground.**
   `HordeRaidEvent.onUpdate()` runs every colony tick and does two things that fight an air drop:
   - **Unloaded-chunk recycling (`:469-476`):** any raider whose block is not entity-loaded is
     `DISCARDED` and queued into `respawns`. A raider *in mid-air under a parachute, 120 blocks up,
     over a chunk nobody is standing in* is a strong candidate for this. The aircraft holds a chunk
     ticket around itself (`AutopilotRegistry.java:100-113`,
     `AutopilotConfig.CHUNK_TICKET_RADIUS`) — but the raiders leave the aircraft, and the aircraft
     flies on. **The drop must happen close enough to the colony that the drop zone is inside the
     colony's own loaded area, or the parachuting raiders will be deleted and re-spawned on the
     ground mid-descent.** **[VERIFIED that the loop does this; [UNCHECKED] whether it actually fires
     in practice, which depends on where the drop point is relative to loaded chunks.]**
   - **Top-up (`:454-461`):** whenever the live count is below the horde quota, it calls
     `spawnHorde` at `getLoadedPositionTowardsCenter(spawnPoint, …)` — the *ground* helper
     (`ShipBasedRaiderUtils.java:315`, which ends in `BlockPosUtil.getFloor(...)` at `:370`). So the
     moment one air-dropped pirate dies, its replacement walks in from the border like an ordinary
     raid. **[VERIFIED]**

   **Both are fixable in the same override.** `PirateAirRaidEvent` overrides `onUpdate()` to either
   (a) suppress the top-up entirely for this event type — a paradrop is one wave, which is arguably
   the better game design anyway — or (b) re-run a fresh drop sortie for the replacements. (a) is
   ~10 lines; (b) is another ~60 and a second aircraft. **Take (a).**
6. **Serialization is fine.** `HordeRaidEvent.serializeNBT/deserializeNBT` (`:521-557`) write the
   spawn point, campfires, status, days-left, horde and waypoints — nothing positional that an air
   drop invalidates. The aircraft persists itself: `launchRoute` passes `persistent = true`
   (`AutopilotSpawner.java:208-209`) and `FlightPlan` is codec-serialisable
   (`FlightPlan.java:14-15,49`). An in-flight drop run survives a restart. **[VERIFIED]**
7. **`skipPreparation` / `immediate` still work.** `RaidManager.tickImmediateRaids()` (`:517`) calls
   `event.onStart()` then `event.skipPreparation()`; `HordeRaidEvent.skipPreparation` (`:266`) just
   zeroes the campfire timer. Neither cares how the raiders arrived. **[VERIFIED]**
8. **Campfires.** `onStart` calls `spawnCampFires(spawnPos)` (`:366`, defined `:280`), which pastes
   campfire blocks around the spawn position. At altitude those are placed in mid-air, or not at all
   (`BlockPosUtil.getRandomPosition` with `true` for ground). The override should simply not call it —
   air-dropped pirates do not camp. **[VERIFIED]**

### 1.7 Risks

- **The raider-stays-seated question (§1.5.2)** is the one unknown that could force a rewrite of the
  descent. Mitigation: slow falling costs one line and always works.
- **The unloaded-chunk recycler (§1.6.5)** could make the whole thing look broken in a way that is
  hard to debug — pirates that vanish mid-air and reappear walking. Mitigation: drop close in, and
  suppress the top-up.
- **A cargo plane at cruise altitude is a large entity flying through terrain.**
  `PlaneAutopilot` has terrain following and a route planner (`autopilot/RoutePlanner.java`), but I
  did not verify that a route over mountains behaves. **[UNCHECKED]**
- **Fairness.** Raiders that land inside the walls bypass every defensive structure the player built.
  That is the *point* of the idea, but it makes the raid meaningfully harder and the difficulty
  scaling in `RaidManager.getRaidDifficultyModifier()` (`:1054`) does not know it. Expect to want
  a lower raider count for an air drop than for a ground raid.
- **I could not verify anything about how it looks.** No screenshots, no test flight.

---

## 2. Aviation research branch + plane-part recipes for colony crafters — **pure datapack**

**What the player sees.** A new branch in the research tree — "Aviation" — costing iron and redstone,
gated behind a level-4 Blacksmith. Completing it teaches the Mechanic to craft the plane item, the
furnace engine, the seats and the cargo bay; before that, the recipes simply are not in the crafter's
list. The plane is then requested through the normal request system like any other item, built by a
citizen, and appears in the warehouse.

**Hook points.** Nothing is hooked; two JSON directories are populated.
- `data/<ns>/researches/<branch>/<name>.json` — schema visible in
  `26.2/src/main/generated/data/minecolonies/researches/combat/ironarmor.json` (branch, costs,
  effects, parentResearch, requirements, researchLevel). Branch file schema in
  `researches/combat.json`. **[VERIFIED]**
- `data/<ns>/crafterrecipes/<crafter>/<name>.json` — parser at
  `core/colony/crafting/CustomRecipe.java:54-164`; the `research-id` key
  (`CustomRecipe.java:129 RECIPE_RESEARCHID_PROP`) gates a recipe on a completed research, and
  `min-building-level` (`:139`) gates it on building level. Example on disk:
  `src/main/generated/data/minecolonies/crafterrecipes/fletcher/string.json`. Output path constructed
  at `core/generation/CustomRecipeProvider.java:56`. **[VERIFIED]**
- **Important constraint:** the recipe must have `"intermediate": "minecraft:air"`. The crafting
  module only accepts recipes whose intermediate is `Blocks.AIR`
  (`core/colony/buildings/modules/AbstractCraftingBuildingModule.java:1030-1033`), the smelting
  module only `Blocks.FURNACE` (`:1067-1070`), brewing only `Blocks.BREWING_STAND` (`:1107`).
  **You therefore cannot teach a crafter to use the plane workbench**
  (`recipes/PlaneWorkbenchRecipe.java:16` is its own `RecipeType`, invisible to MineColonies). You
  restate the recipe as a flat ingredient list instead. That is a duplication, and it can drift from
  the workbench recipe. **[VERIFIED]**

**Cost.** ~10 JSON files, 0 lines of Java, 0 files changed in either repo's source. Ships as a
standalone datapack, or inside MineColonies' `data/minecolonies/` guarded by nothing at all — a
recipe naming `simpleplanes:plane` when Simple Planes is absent fails item resolution and is skipped
by `CustomRecipe`'s parser, which logs and continues. **[VERIFIED that the parser is per-file
try/catch: `CustomRecipe.java` parse path; [UNCHECKED] that a missing item id specifically produces a
skip rather than a hard failure — worth confirming before shipping it inside the main jar rather than
as a separate pack.]**

**Which repo.** Neither, ideally: a third artefact, a datapack. If it must ship inside a jar, ship it
in MineColonies (licence direction, §0.1).

**Risks.** Recipe drift from the workbench. Balance — a colony that mass-produces planes trivialises
the plane workbench's own progression. The item-resolution question above.

**Confidence: verified by reading the code**, except the two [UNCHECKED] notes.

---

## 3. Colony defensive gunship — **works today, zero code**

**What the player sees.** During a raid, the player runs `/gunship launch <x y z>` over the colony. An
armed helicopter climbs to 18 blocks, hovers, and shoots the barbarians. It does not shoot the
citizens, and it holds fire rather than putting a round through one.

**Why this already works, verified rather than assumed.** `combat/HostileTargets.java:68-82` decides
what the gunship shoots: `candidate instanceof Enemy` is the primary rule. MineColonies raiders
**are** `Enemy` — `AbstractEntityMinecoloniesMonster` at
`api/entity/mobs/AbstractEntityMinecoloniesMonster.java:46` declares
`implements IThreatTableEntity, Enemy`, and `AbstractEntityMinecoloniesRaider:46` repeats it.
MineColonies citizens are **not** — `EntityCitizen` (`core/entity/citizen/EntityCitizen.java:133`)
extends `AbstractEntityCitizen` (`api/entity/citizen/AbstractEntityCitizen.java:70`), and neither
implements `Enemy`. So citizens fall into the gunship's *bystander* set and get a keep-out box around
them: `GunshipSortie.bystanders()` at `:730-739` inflates every non-hostile living entity's bounding
box by `BYSTANDER_CLEARANCE` (0.5) and refuses any shot whose sampled ballistic arc clips it
(`pathIsClear`, `:669-691`). **[VERIFIED — all of it, in both repos.]**

Conversely, colony guards cannot shoot the gunship down, because `PlaneEntity` is not a
`LivingEntity` and mob targeting requires one — stated in `HostileTargets.java:32-35`. **[VERIFIED]**

**Hook points.** `combat/GunshipCommand.java:76 register()`; registered from
`SimplePlanesMod`. `GunshipRegistry.MAX_ACTIVE = 16` (`combat/GunshipRegistry.java:67`).

**The cheap upgrade** (optional, ~60 lines): auto-launch on raid start. There is no raid event on
MineColonies' event bus (`api/eventbus/events/` contains colony/citizen/building events only —
**[VERIFIED]**), so the hook is a direct call at `RaidManager.java:506` next to the `immediate` block,
through the same `AirRaidBridge` proxy from §1. Optionally gated on a research and on a new
"Airfield" building level — but see §9 for why the building is expensive.

**Cost.** 0 lines as-is. ~60 lines in MineColonies for auto-launch.

**Which repo.** MineColonies, for the auto-launch. Nothing in Simple Planes.

**Risks.** `GunshipSortie`'s own comment (`:74-75`) admits an arrow that misses lands somewhere and
can hit whoever walks under it — over a colony, that is a citizen. Ammunition consumption is not
modelled against colony stock. A hovering gunship holds a chunk bubble (`GunshipRegistry.java:63`).

**Confidence: verified by reading the code.** [UNCHECKED] whether the hit rate over a built-up colony
resembles the numbers `GunshipSortie`'s javadoc quotes for flat ground.

---

## 4. Colony production feeds plane engines — **pure datapack / already true**

**What the player sees.** The colony's charcoal burners and miners keep the hangar stocked. A furnace
engine takes the same coal and charcoal the colony already produces in bulk; if the colony makes a
fluid, a `plane_liquid_fuels` JSON turns it into aviation fuel with a burn rate.

**Hook points.**
- Furnace engine fuel is vanilla fuel. MineColonies' own fuel set is derived from the same vanilla
  lookup: `api/compatibility/CompatibilityManager.java:808-809` —
  `level.fuelValues().isFuel(stack)`, with the ported note "26.2: `FurnaceBlockEntity#isFuel` is
  gone; fuel is a per-level `FuelValues` lookup". So the two mods already agree on what fuel is, with
  no code. **[VERIFIED]**
- Liquid fuel is one JSON per fluid: `data/<ns>/plane_liquid_fuels/*.json`, keys `fluid` and
  `burn_time_per_mb` (`datapack/PlaneLiquidFuelReloadListener.java:30,53-60`). **[VERIFIED]**

**Cost.** 0–2 JSON files. 0 lines of Java.

**Which repo.** A datapack.

**Risks.** The value here is thematic, not mechanical — nothing new happens, it is just coherent.
Whether MineColonies registers any fluid worth flying on is **[UNCHECKED]**; I did not look.

**Confidence: verified by reading the code** for the two mechanisms; the *usefulness* is a judgement.

---

## 5. Allied-colony supply drop

**What the player sees.** A colony that has established an alliance with a neighbour
(`/mc colony connections`, gatehouse) occasionally receives a cargo plane from that neighbour: it
flies over the warehouse at altitude, opens the bay, and a crate drifts down on a parachute and
becomes a barrel full of goods on the ground. Being at *feud* instead gets you §8.

**Hook points.**
- Diplomacy exists and is real: `api/colony/connections/ColonyConnection.java` holds a
  `DiplomacyStatus` per connected colony; `ConnectionEventType` (`api/colony/connections/`) has
  `ALLY_REQUEST, ALLY_CONFIRMED, FEUD_STARTED, NEUTRAL_SET, DISCONNECTED`;
  `core/colony/managers/ColonyConnectionManager.java:724-764 triggerConnectionEvent` maps them onto
  `DiplomacyStatus.ALLIES / HOSTILE / NEUTRAL` on both sides.
  `IColonyConnectionManager.getDirectlyConnectedColonies()` gives the ally list. **[VERIFIED]**
- The crate half is entirely written: `upgrades/supplycrate/SupplyCrateUpgrade.java:83-89
  dropAsPayload()` constructs `new ParachuteEntity(level, container)`, positions it at the plane and
  gives it the plane's velocity; `entities/ParachuteEntity.java:73-102` turns it into a barrel with
  the contents on touchdown, and spills the items on the floor if it cannot place one. **[VERIFIED]**
- Flight: `AutopilotSpawner.launchRoute(..., AircraftType.CARGO)` (`:169`), fit the crate with
  `PlaneEntity.addUpgradeUsingWrench(stack, new SupplyCrateUpgrade(plane))`
  (`entities/PlaneEntity.java:431`), release with `CargoPlaneEntity.dropPayload()` (`:133`).
  **[VERIFIED that these methods are public with these signatures.]**

**Cost.** ~150 lines, 2 new files, MineColonies only (inside the same excludable
`compatibility/simpleplanes` package as §1 — it reuses the same waypoint builder and the same
tick watcher, so most of the 150 is content, not machinery). Roughly half of it disappears if §1 is
built first.

**Which repo.** MineColonies. §0.1(4).

**Risks.** What goes in the crate, and where it comes from — taking real items out of the ally's
warehouse is a much bigger feature than conjuring them. Recommend conjured, on a long cooldown, with
a small fixed loot table. Whether the barrel placement finds a legal spot in a dense colony is
**[UNCHECKED]** (`ParachuteEntity.java:78-95` searches 50 blocks upward for a replaceable block, then
gives up and spills).

**Confidence: verified by reading the code** for every mechanism; the design is a proposal.

---

## 6. Hostile-colony air strike

**What the player sees.** A colony you are at *feud* with sends a plane. It comes in low and fast from
one side, dives into a building, and detonates. Guard towers cannot touch it (see §3). The damage is
configurable and can be set to zero blocks-broken, so it is a scare rather than a crater.

**Hook points.**
- `AutopilotSpawner.launchStrike(level, target, distance, approachBearing, owner, blast)` —
  `autopilot/AutopilotSpawner.java:69`. Spawns the aircraft `distance` blocks out on a bearing at
  `STRIKE_RUN_IN_AGL`, fits a booster, sets `STRIKE_MAX_SPEED = 3.0f`, launches at 2.0 blocks/tick.
  `approachBearingFrom(origin, target)` at `:148` derives a run-in direction from where the order
  came from — pass the attacking colony's centre. **[VERIFIED]**
- Impact: `PlaneAutopilot.tickStrike` (`:932-1011`) holds altitude on the run-in with terrain
  following, computes a dive entry from the height to lose (`:955-958`), commits, and detonates on a
  speed-scaled proximity fuse (`:986-999 → plane.crash(16)`). It also self-destructs if it clips
  something (`:1005-1010`) rather than parking a live aircraft in the scenery. **[VERIFIED]**
- Warhead: `autopilot/Blast.java` — `record Blast(float power, boolean breaksBlocks, boolean fire)`,
  clamped, `MAX_POWER` documented as a server-cost limit. `Blast(4.0f, false, false)` is "TNT-strength
  entity damage, not one block moved". **[VERIFIED]**
- Trigger: diplomacy as in §5, plus a cooldown. Cleanest seam is `RaidManager.onNightFall()`
  (`:860-902`), which already runs the nightly "does something happen tonight" decision.

**Cost.** ~120 lines, 1–2 new files, MineColonies (same excludable package).

**Which repo.** MineColonies.

**Risks.** A strike is not a raid: it produces no `IColonyRaidEvent`, so none of the raid bookkeeping,
the raid bar, the happiness modifiers or `/mc colony raid info` know about it. That is a feature (it
is a *different* kind of threat) but it means the player has no in-game record of what hit them
unless a message is sent. Griefing potential on a multiplayer server with `breaksBlocks = true`.
**[UNCHECKED]** whether a strike aircraft launched 400 blocks out reliably arrives — the class comment
mentions "measured twice, 57 and 54 blocks long" for an earlier version of the dive, which suggests
the run-in has been iterated on but I have no evidence about the current one.

**Confidence: verified by reading the code.**

---

## 7. Air-raid warning: the colony notices hostile aircraft

**What the player sees.** When an unmanned aircraft that is not the colony's own crosses the colony's
claimed chunks, the town hall raises an alert — the raid warning sound and a message — and nearby
guards are sent to patrol under the flight path.

**Hook points.**
- Detection is cheap and needs no entity scanning: `AutopilotRegistry.active()`
  (`autopilot/AutopilotRegistry.java:82-85`) returns the pruned list of every aircraft currently
  under autopilot, "cheaper and more precise than scanning the level for entities". Poll it from the
  colony tick, test each aircraft's chunk against the colony. **[VERIFIED]**
- The colony-ownership test is `ColonyUtils.getOwningColony(chunk)`, used exactly this way at
  `RaidManager.java:756-760 isOtherColony` and
  `AbstractEntityMinecoloniesRaider.java:356-365 onEnterChunk`. **[VERIFIED]**
- Alarm: `PlayAudioMessage.sendToAll(colony, …, new PlayAudioMessage(RaidSounds.WARNING, HOSTILE))`
  — exactly as `HordeRaidEvent.onStart` does at `:387-388`. **[VERIFIED]**
- Directing guards: `AbstractBuildingGuards.setTempNextPatrolPoint(BlockPos)` —
  `core/colony/buildings/AbstractBuildingGuards.java:498`, already used by
  `RaidManager.getRandomBuilding()` at `:1038` to send the three nearest guards to a threatened
  building. Reusable verbatim. **[VERIFIED]**

**Be honest about what this cannot be.** It cannot be anti-aircraft fire. Guards attack
`LivingEntity` targets; `PlaneEntity` is not one — Simple Planes states this itself at
`combat/HostileTargets.java:32-35` ("a `PlaneEntity` is not one, so a mob *cannot* target the gunship
at all"). Making guards shoot planes means either a new goal that fires arrows at a non-living target
or a mixin, and that is a different, much larger feature. **[VERIFIED]**

**Cost.** ~90 lines, 1 new file plus a call from the colony tick, MineColonies (excludable package,
because `AutopilotRegistry` is a Simple Planes type).

**Which repo.** MineColonies.

**Risks.** Distinguishing "hostile" from "the colony's own supply plane" needs a marker — simplest is
for the bridge to keep the entity ids of the aircraft it launched itself. Polling every colony tick
over up to `MAX_ACTIVE_AUTOPILOTS` aircraft is trivial. The alarm firing for a *player's* own plane
flying home is the obvious annoyance; gate on unmanned-only (`plane.getControllingPassenger() == null`)
**[UNCHECKED — I did not verify that accessor exists on `PlaneEntity`]**.

**Confidence: verified by reading the code**, except the last note.

---

## 8. Long-haul courier between connected colonies

**What the player sees.** Two colonies with an established connection get an air bridge. A cargo plane
leaves colony A's warehouse area, flies to colony B, and lands; the goods appear in B's warehouse. It
replaces a courier walking two thousand blocks.

**Hook points.**
- The flight is a solved problem *if both ends are registered airfields*:
  `AutopilotSpawner.launchSortie(level, departure, destination, owner, cruiseSpeed, blast,
  departureDelayTicks, type)` (`autopilot/AutopilotSpawner.java:232`) does park → taxi → take off →
  cruise → approach → land → taxi to a stand, with runway reservation
  (`AutopilotMode.usesRunway()`, `autopilot/AutopilotMode.java:71-73`) and stand occupancy
  (`autopilot/StandOccupancy.java`). Airfields are named, surveyed and persisted:
  `Airfield.survey(level, name, clickedA, clickedB)` (`autopilot/Airfield.java:901`) and
  `AutopilotSavedData.put/get/nearest` (`autopilot/AutopilotSavedData.java:77,82,146`). **[VERIFIED]**
- Helicopters remove the runway requirement entirely: `launchHelicopterSortie(level, departurePad,
  destinationPad, …)` (`:338`) flies pad to pad, and a `Helipad`
  (`autopilot/Helipad.java:53`) is a centre, a radius and a clear-sector count — a flat roof.
  **This is the version to build.** A colony can plausibly have a helipad; a colony that must build a
  183-block runway cannot.
- The MineColonies end: `IColonyConnectionManager.getDirectlyConnectedColonies()` for the pair, and
  the warehouse for the goods (`core/colony/buildings/modules/WarehouseModule.java`). The delivery
  itself is a `SupplyCrateUpgrade` as in §5 if you want it dropped, or a straight inventory transfer
  on arrival.

**Cost.** ~250 lines, 3 new files, MineColonies. Plus the player having to place two helipads, which
is content, not code.

**Which repo.** MineColonies.

**Risks.** Highest-uncertainty entry in the list. The request system is not a shipping manifest — it
is a per-colony resolver, and there is no existing notion of a cross-colony delivery request; wiring
one in is a real feature on the MineColonies side and I did not scope it. `JobDeliveryman` and
`EntityAIWorkDeliveryman` (`core/entity/ai/workers/service/EntityAIWorkDeliveryman.java:333 deliver()`,
`:346` reads `((Delivery) request).getTarget()`) are built around a citizen walking to an
`ILocation` in the same colony, and `:347` explicitly bails on
`!targetBuildingLocation.isReachableFromLocation(worker.getLocation())`. **[VERIFIED that this is
what the code does; [UNCHECKED] what it would take to extend it.]** Recommend: do **not** try to make
a courier ride a plane. Make the plane a separate transport that moves items between two warehouses,
and leave the couriers alone.

**Confidence: mechanisms verified by reading the code; the integration design is plausible but
unchecked.**

---

## 9. Builder / deliveryman long-range transit — *evaluated and not recommended*

**Why it is on the list.** `maxbuilderdistance` defaults to **2000** in this port
(`api/configuration/ServerConfiguration.java:329 defineInteger("maxbuilderdistance", 2000, 16, 5000)`),
and `WorkOrderBuilding.java:45` reads it as the reach a builder will accept a work order at, with the
file's own comment noting it "rather than the 100 blocks this used to hardcode". **[VERIFIED]** So a
builder really can be sent two kilometres, and really will walk it. That is a genuine problem worth
solving and it is why this idea keeps coming up.

**Why it should not be solved with planes.** The whole MineColonies worker AI is a pathfinding state
machine. `AbstractEntityAIBasic.walkToBuilding(...)` and its `AITarget` states
(`EntityAIWorkDeliveryman.java:120-121` is a representative example) assume the citizen is walking and
re-enter the same state until arrival. Putting a citizen in a vehicle does not advance any of that; it
suspends it. Making it work means either a new AI state that knows about flight, or teleport-with-a-
cutscene dressed up as a plane. The first is a large, risky change to the most load-bearing class in
the mod. The second is a lie, and cheaper to implement honestly as a config.

**Cost if attempted anyway.** 400+ lines in MineColonies touching `AbstractEntityAIBasic`, plus
mixins. **[UNCHECKED — I did not scope it properly, because I do not think it should be done.]**

**Which repo.** MineColonies, unavoidably.

**Risks.** Very high. This is the entry most likely to consume a week and produce a citizen standing
in a field.

**Recommendation:** solve the 2000-block problem by other means (a second builder, or the existing
`Delivery` reachability check), and use planes for cargo (§8), not for people.

**Confidence: the two file:line facts are verified by reading the code; the conclusion is a
judgement.**

---

## 10. A colony "Airfield" hut building — *evaluated, expensive, listed last on purpose*

**What the player sees.** A new hut block. Placing and building it registers the strip as a named
Simple Planes airfield; upgrading it adds parking stands; a citizen assigned to it refuels and repairs
aircraft on the apron and becomes the requester for plane parts.

**Why it is last.** The Simple Planes half is nearly free — `Airfield.survey(level, name, a, b)`
(`autopilot/Airfield.java:901`) and `AutopilotSavedData.put(airfield)`
(`autopilot/AutopilotSavedData.java:77`) are all that registration takes, and `withParkingSpots`
(`:69`) and `withRequiredStands` (`:74`) are already there. **[VERIFIED]** The MineColonies half is
the expensive one: a new building means a hut block (`core/blocks/huts/`), a building class, at least
a `WorkerBuildingModule` and a `SettingsModule`, a view class, a GUI window, a job, a job AI, a
citizen skill mapping, blueprints for five levels in every style pack, datagen entries, loot tables
and translations. That is the standard MineColonies building cost and it is not small.

**Cost.** 800–1500 lines plus blueprints, MineColonies. **[UNCHECKED — extrapolated from the shape of
the existing building packages, not measured.]**

**Which repo.** MineColonies.

**Risks.** Blueprints are the real cost and they are art, not code. Every style pack that does not
have the building will show a hole. Recommend deferring until several of the entries above exist and
have proved they are worth a building to hang off.

**Confidence: the Simple Planes API points are verified by reading the code; the cost estimate is
plausible but unchecked.**

---

## Summary table

Ranked by value ÷ cost, except that #1 is presented first because it is the one that was asked for;
on the ratio alone it would sit between "Allied supply drop" and "Air-raid warning".

| Rank | Integration | Repo | New lines | Confidence |
|---|---|---|---|---|
| — | **Pirate paradrop** (§1) | MineColonies | ~390 / 5 new + 2 edited files | mechanisms verified; runtime unchecked |
| 1 | Aviation research + crafter recipes (§2) | datapack | 0 Java, ~10 JSON | verified |
| 2 | Colony defensive gunship (§3) | none (0) / MineColonies (~60 for auto-launch) | 0–60 | verified |
| 3 | Colony fuel feeds plane engines (§4) | datapack | 0 Java | verified |
| 4 | Allied-colony supply drop (§5) | MineColonies | ~150 / 2 files | verified mechanisms |
| 5 | *(paradrop sits here on ratio)* | | | |
| 6 | Hostile-colony air strike (§6) | MineColonies | ~120 / 2 files | verified |
| 7 | Air-raid warning (§7) | MineColonies | ~90 / 1 file | verified |
| 8 | Long-haul courier between colonies (§8) | MineColonies | ~250 / 3 files | plausible, unchecked design |
| 9 | Builder/deliveryman by air (§9) | MineColonies | 400+ | not recommended |
| 10 | Airfield hut building (§10) | MineColonies | 800–1500 + blueprints | cost unchecked |

## What I did not do

- Did not build either project, did not run Gradle, did not start a server, did not launch the game.
- Did not write, compile or run any spike. Every API claim is from reading source.
- Did not verify that a MineColonies raider stays seated on a `ParachuteEntity` (§1.5) — the single
  most valuable five-minute experiment on this list.
- Did not verify that a route aircraft reliably flies a long leg over varied terrain.
- Did not scope what it would take to extend the request system across colonies (§8).
- Did not read the whole of `PlaneAutopilot.java` (2528 lines) or `Airfield.java` (1228 lines); I read
  the entry points, the cruise/strike ticks and the public surface.
