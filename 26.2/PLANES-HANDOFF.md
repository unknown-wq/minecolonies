# Handoff — aircraft integration

Branch `claude/planes-integration-report`, worktree
`/home/user/minecolonies/.claude/worktrees/planes-integration-report`.

**Everything is committed and `gradle build --offline` is green.** Nothing is half-edited, nothing is
stubbed, no instrumentation is left in the tree. All three approved features are implemented. What is
missing is **in-game verification of two things**, and one of them cannot be done headlessly at all —
see §4, which is the most important section here.

Commits, oldest first:

| | |
|---|---|
| `7039a87d` | `PLANES-INTEGRATION.md` — the ten-integration design survey |
| `b141744d` | `PLANES-AIR-DEFENCE.md` — the ten-defence study, and a correction to the survey |
| `38cad37d` | the implementation, all three features |
| this commit | `PLANES-IMPLEMENTATION.md` (what was built) and this file |

Read `PLANES-IMPLEMENTATION.md` first if you want the design rationale. This file is the operational
half: what runs, what does not, and what cost me time.

---

## 1. Done and committed, file by file

### New files

| file | what it does |
|---|---|
| `api/compatibility/simpleplanes/AircraftCompat.java` | The proxy, always compiled. A **concrete class of complete no-aircraft answers**, not an interface with a stub — `deploy()` applies `SLOW_FALLING`, `launchDropRun()` returns false, `sightings()` returns empty. Every caller calls it unconditionally. Vocabulary is vanilla only (`BlockPos`, `Vec3`, `ServerLevel`) so it compiles with Simple Planes absent. |
| `core/colony/events/raid/pirateEvent/PirateAirRaidEvent.java` | The air raid. Extends `HordeRaidEvent`, overrides `onStart`, `onUpdate`, `skipPreparation`, serialisation, and the pirate horde bookkeeping copied in shape from `PirateGroundRaidEvent`. Falls back to a ground raid when no transport can fly. |
| `core/compatibility/simpleplanes/SimplePlanesCompat.java` | The bridge. One `ServerTickEvents.END_LEVEL_TICK` registration driving all three features. Holds the `FRIENDLY` entity-id set. |
| `core/compatibility/simpleplanes/DropRunTracker.java` | Launches transports via `AutopilotSpawner.launchRoute` and drives the bay. |
| `core/compatibility/simpleplanes/AircraftWatch.java` | The warning. Registry-first, proximity scan second. |
| `core/compatibility/simpleplanes/AntiAirBattery.java` | The battery. Targeting, gunnery, the kill, and following wrecks down. |

### Edited files

| file | change |
|---|---|
| `api/compatibility/Compatibility.java` | `public static AircraftCompat aircraftCompat = new AircraftCompat();` + import |
| `api/configuration/ServerConfiguration.java` | `airRaids` (true), `airRaidChance` (25), `aircraftWarnings` (true) |
| `api/entity/mobs/RaiderMobUtils.java` | **new** `spawnAt(type, pos, world, colony, eventID)` → returns the raider, no ground search |
| `core/entity/ai/combat/CombatUtils.java` | **new** `launchArrow(arrow, Vec3 velocity)` — 4 lines |
| `api/colony/managers/interfaces/IRaiderManager.java` | **new** default `updateLastSpawnPoint(Identifier, BlockPos)` |
| `core/colony/events/raid/RaidManager.java` | implements the above; `RaidSpawnInfo.spawnpos` is no longer `final`; one new first branch in the horde selection at ~`:452` |
| `apiimp/initializer/ModColonyEventTypeInitializer.java` | registers `pirate_air_raid` |
| `core/MineColonies.java` | the `isModLoaded("simpleplanes")` swap, just before `EventHandler.register()` |
| `api/util/constant/TranslationConstants.java` | 8 new keys |
| `resources/assets/minecolonies/lang/manual_en_us.json` | 8 message strings + 6 config strings. **All use `%s`, never `%d`.** |
| `build.gradle` | `compileOnly files(simpleplanes_jar)` when present; excludes `core/compatibility/simpleplanes/**` when absent |
| `gradle.properties` | `simpleplanes_jar=/workspace/unknown-wq/simple-planes/dist/simpleplanes-26.2-5.3.7.jar` |
| `optional-integrations.txt` | a comment block explaining why the aircraft package is *not* listed there |

---

## 2. Started but incomplete

**Nothing.** There is no half-finished edit to pick up. Every method I began is finished and compiles.

---

## 3. Not started

Of the three approved features, none. All three are implemented.

Not started, and not asked for: everything else in the two design documents — the crafter recipes and
research branch (pure datapack, §2 of the survey), the allied supply drop, the hostile-colony strike,
the courier, the radar hut. **Item #2 of the defence study — restoring
`turnoffexplosionsincolonies` via a mixin on `ServerLevel#explode` — was assigned to a different agent
in parallel. I did not touch `ServerLevel#explode`, the `Explosions` enum, `Action.EXPLODE`, or add
any mixin, so the two branches should merge cleanly.**

---

## 4. The two load-bearing unknowns — exact state

### 4.1 Does an arrow really damage a plane? — **ANSWERED YES, on a server**

Verified end to end, not just link by link. The run:

1. `/damage @e[type=simpleplanes:plane] 3 minecraft:arrow` → `health` 10 → 7.
   `PlaneEntity#hurtServer` is reachable and the NBT key `health` reflects it.
2. A real arrow entity fired from **4 blocks** → 10 → 5. (One hit, 5 damage: vanilla computes
   `ceil(|v| × baseDamage)`, |v|≈1.5 × base 3.)
3. **Four such arrows destroyed the plane** — `No entity was found`.
4. A **lofted 40-block shot** also connected → a fresh plane to `health: 4`.

**The failed first attempt is worth keeping**: arrows fired *flat* from 10 blocks all missed and stuck
in the ground (`inGround: 1b`). An arrow drops ~3 blocks over 10. A flat aim is not an approximation,
it is a miss — which is exactly why the battery solves a trajectory with `Ballistics` instead of
pointing. If you re-test, loft the shot or fire from under 5 blocks.

### 4.2 Does a raider stay seated on a `ParachuteEntity`? — **NOT ANSWERED, and it cannot be headlessly**

What I did establish:

- **[VERIFIED on a server]** A `ParachuteEntity` carries a pathfinding mob down and the passenger
  stays aboard. Summoned at y=150 with a zombie mounted at spawn: t+2s parachute 149.5, zombie riding
  149.845, `fall_distance: 0.0`; t+22s parachute 109.95, zombie still aboard 110.25, `Health: 20.0`.
  **39.6 blocks in 20 seconds ≈ 0.1 blocks/tick, full health, zero fall distance.**
- **[VERIFIED by reading]** Nothing in MineColonies dismounts it. The navigator's only dismount is
  guarded on `MinecoloniesBoat`/`MinecoloniesMinecart` (`MinecoloniesAdvancedPathNavigate.java:540-547`);
  vanilla `LivingEntity` only lets go underwater or on sleeping (`LivingEntity.java:454-455, 3750`).

**What shipped: the parachute AND slow falling, in that order** — `super.deploy(raider)` applies
`SLOW_FALLING` *first*, then the mount is attempted inside a `try`. So even if a raider does come off,
it lands alive. I did **not** fall back to slow-falling-only; I shipped both, precisely because I could
not close this gap.

**Why it cannot be tested headlessly** (this cost me about an hour, do not repeat it):
`Colony#updateState` (`Colony.java:472-493`) returns `ACTIVE` only when
`!packageManager.getCloseSubscribers().isEmpty()` or there are important colony players. **With nobody
logged in the colony is `INACTIVE`, its state machine never runs `eventManager.onColonyTick` or
`tickImmediateRaids`, and no raid event of any kind ever starts.** I confirmed it directly:
`/mc colony raid 1 now minecolonies:pirate_air_raid false 8` reports *"Raid started for colony
ScaleTest: 8 raiders at strength 0.89"* and then **nothing happens for four minutes** — no transport,
no pirates, and a `warn`-level probe inside `PirateAirRaidEvent#onStart` never fired. The same is true
of an ordinary ground raid.

And a raider cannot be substituted: summoned directly it is discarded within a few ticks for want of a
live raid event (`EventManager.java:105-112` discards when the event id is unknown;
`AbstractEntityMinecoloniesRaider.java:403-413` removes itself when `colony == null || eventID == 0`).
I tried `summon simpleplanes:parachute … {Passengers:[{id:"minecolonies:pirate",colonyId:1,eventId:1}]}`
— gone within seconds.

**To close this you need a player in the world.** Log in, stand in the colony, and run the raid
command. That is the single most valuable five minutes left on this feature.

---

## 5. Everything I learned by running things

Not in the other two documents. Roughly in the order it cost me time.

1. **The raid command is `/mc colony raid …`, not `/mc raid …`.** `CommandRaid` is added to the
   `colony` subtree (`EntryPoint.java:51`). Every form of `/mc raid …` fails with
   `Incorrect argument for command` and a `<--[HERE]` at the *end* of the line, which reads like a
   trailing-argument problem and is not. Full form:
   `/mc colony raid <id> now <raidtype> <ships> [<amount>] [<location>]`, e.g.
   `/mc colony raid 1 now minecolonies:pirate_air_raid false 8`.
2. **A dedicated server pauses when empty.** `pause-when-empty-seconds` defaults to **60**; the log
   says `Server empty for 60 seconds, pausing` and after that *nothing* ticks. Set it to `0` in
   `server.properties` or every timed test silently measures nothing.
3. **Do not overwrite the mod jar while the server is running.** I did, and the running server died
   with `ZipException: ZipFile invalid LOC header` out of `KnotClassDelegate.getRawClassByteArray`,
   and left a 3 GB java process behind that the driver script could not stop. Copy the jar, *then*
   start.
4. **`ParachuteEntity` kills itself on its first tick with no passenger.** `ParachuteEntity#tick:73`
   removes it when `passenger == null && !hasStorageCrate()`. So `/summon simpleplanes:parachute` then
   `/ride … mount …` never works — the parachute is gone before the second command runs. The only way
   to test one from commands is
   `/summon simpleplanes:parachute x y z {Passengers:[{id:"minecraft:zombie"}]}`, which mounts at
   creation. The production code is fine: `addFreshEntity` then `startRiding` in the same tick, before
   the entity is ticked.
5. **Zombies burn.** My first parachute test was in daylight and the passenger caught fire mid-descent
   (`Fire: 119s`) and died, which looked like a dismount and was not. `time set midnight` and
   `gamerule doDaylightCycle false` first.
6. **Vanilla mobs despawn instantly with no player online**, so you cannot observe a landing — the
   zombie was gone by t+52s even though it was alive and healthy at t+22s. Use the descent numbers,
   not the touchdown.
7. **`MC_NORMAL_ARROW` does flat damage.** `CustomArrowEntity#onHitEntity:98-107` divides base damage
   by the arrow's speed before vanilla multiplies it back, so a MineColonies arrow does exactly its
   `setBaseDamage` at any range. That is why the battery's time-to-kill is predictable. Note the
   contrast with a *vanilla* arrow, which is where the 5-damage figure in §4.1 comes from.
8. **`AutopilotSpawner.launchRoute` works and is impressive.** `/autopilot route 300 140 0 -300 140 0
   1.2` produced Plane #1114, which flew the route and then ran a full arrival — approach planning,
   three go-arounds for terrain, a runway switch to 24, and a committed landing. It picks an
   improvised field (`field-1114/06`) when no airfield is named. Nothing in `DropRunTracker` steers.
9. **The bug the testing found that reading did not.** The ground fallback in `PirateAirRaidEvent`
   was broken: `onStart` moves `spawnPoint` to the drop point *before* deciding whether the transport
   launched, and `HordeRaidEvent#onStart` then resolves its spawn through
   `getLoadedPositionTowardsCenter`, which refuses any point within `MIN_CENTER_DISTANCE` (100) of the
   colony centre. The drop point is directly over a building. Result: `null` → `EventStatus.CANCELED`
   → a raid that silently never happens. **Fixed** — the ground spawn point is saved as `groundSpawn`
   and restored before `super.onStart()`. If you touch that method, keep the restore.
10. **The test world.** `testworlds/colony-1000.zip` unpacks to a `world/` directory with **colony 1,
    "ScaleTest", 999 citizens, at 0,72,0**. It boots in about 40 seconds on 5 GB. It logs a great deal
    of `Missing Portal Tag In Nether Worker Building!` and `Error loading blueprint:` noise that is
    pre-existing and unrelated.
11. **`FRIENDLY` marking is deliberately not applied to the raid transport.** I wrote it that way
    first and it was wrong: protecting the raid's own aircraft from the colony's guns removes the best
    counterplay in the feature. The transport is a legitimate target from launch, and the air-raid
    warning firing on it is correct. The `FRIENDLY` set exists for future colony-owned aircraft.
12. **Port 25731 was in use by another agent** in the same scratchpad (`…/scratchpad/rp/srv`) for part
    of the session. Check `ps aux | grep "port 25731"` before assuming it is free, and do not kill a
    process you did not start.

---

## 6. Left in a temporary or odd state

Nothing in the repository. Specifically:

- **No instrumentation remains.** I added `AIRRAID-DEBUG` `warn` lines to `PirateAirRaidEvent#onStart`
  and `DropRunTracker#launch` while chasing item 9 above, and removed all four. `grep -r AIRRAID-DEBUG
  26.2/src` returns nothing.
- One log line was *deliberately* promoted from `debug` to `warn` and should stay: "Air raid could not
  launch a transport for colony X; falling back to a ground raid." It is a genuine anomaly.
- **No server is running.** Verified with `ps aux | grep fabric-server-launch` → 0.
- The test server lives outside the repo at
  `/tmp/claude-0/-home-user-minecolonies/25394ac8-…/scratchpad/aa-server`, with
  `pause-when-empty-seconds=0`, `port 25731`, and the colony-1000 world unpacked. The driver script is
  `…/scratchpad/drive.sh <serverdir> <cmdfile> <heap> <boot_timeout_s> <run_timeout_s>`; it stops the
  server on exit. Reuse or delete freely.
- `gradle.properties` now carries an **absolute path** to the Simple Planes jar
  (`/workspace/unknown-wq/simple-planes/dist/simpleplanes-26.2-5.3.7.jar`), matching how the three
  ldtteam jars are already pointed at. The build degrades gracefully if it is missing, so this is not
  a hard requirement for anyone else's machine — but it is a machine-specific path in a tracked file,
  which the integrator may want to reconsider.

---

## 7. If you pick this up

In priority order:

1. **Log in and run one air raid.** `/mc colony raid <id> now minecolonies:pirate_air_raid false 8`
   while standing in the colony. Watch whether the pirates stay on their parachutes. That is the only
   thing standing between this and "verified".
2. **Fire the battery once.** Level-3 guard tower, an archer assigned to it, arrows in the hut, then
   `/autopilot strike <a block in the colony>` from 400 blocks out. Untested in practice: the lead
   solution's accuracy, the ammunition draw, whether four hits land inside the engagement window, and
   `AntiAirBattery#tickWrecks`.
3. **Watch the warning fire** for both a strike and a hand-flown plane.
4. Tune, if it needs it: `DropRunTracker.CRUISE_SPEED` (1.2), `RUN_IN_DISTANCE` (300),
   `PirateAirRaidEvent.DROP_ALTITUDE` (70), `AntiAirBattery.ENGAGEMENT_RANGE` (200),
   `SHOT_INTERVAL` (20), `ROUND_DAMAGE` (3.0), `MIN_TOWER_LEVEL` (3). Every one has a comment saying
   why it is that number; the engagement range in particular is a design parameter, not a magic
   constant — see `PLANES-IMPLEMENTATION.md` §3.
5. **Do not** try to make the guards' own `RangeCombatAI` shoot at aircraft. Its range budget caps at
   24 blocks and *shrinks* with target altitude (`getAttackDistance` adds `user.getY() - target.getY()`),
   against a run-in flown at 100 AGL. That is why the battery is a separate emplacement.
