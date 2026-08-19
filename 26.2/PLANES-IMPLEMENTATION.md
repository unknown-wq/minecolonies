# The aircraft integration, as built

What actually landed, how it is gated, and — separately for each of the three features — what was
verified on a server and what was not.

Companions: [`PLANES-INTEGRATION.md`](PLANES-INTEGRATION.md) (the design survey) and
[`PLANES-AIR-DEFENCE.md`](PLANES-AIR-DEFENCE.md) (the defence study this implements two thirds of).

Same evidence standard as those: **[VERIFIED]** means I read the source or watched it happen on a
server, **[UNCHECKED]** means I did not.

---

## 0. Shape of the thing

Three features, one integration, and **not one line of it is in the Simple Planes repository** —
which stays read-only, and which the licence direction forbids writing to anyway (MineColonies is
GPL-3.0-only, Simple Planes LGPL-3.0-or-later; linking works in one direction only).

| | file | lines |
|---|---|---|
| **Proxy, always compiled** | `api/compatibility/simpleplanes/AircraftCompat.java` | 141 |
| **Air raid event** | `core/colony/events/raid/pirateEvent/PirateAirRaidEvent.java` | 528 |
| **Bridge** | `core/compatibility/simpleplanes/SimplePlanesCompat.java` | 215 |
| **Transports** | `core/compatibility/simpleplanes/DropRunTracker.java` | 232 |
| **Air-raid warning** | `core/compatibility/simpleplanes/AircraftWatch.java` | 196 |
| **Anti-air battery** | `core/compatibility/simpleplanes/AntiAirBattery.java` | 415 |

Edited: `Compatibility`, `ServerConfiguration`, `RaiderMobUtils` (one new method), `CombatUtils` (one
new method), `IRaiderManager` + `RaidManager` (one new method), `ModColonyEventTypeInitializer`,
`MineColonies`, `TranslationConstants`, `manual_en_us.json`, `build.gradle`, `gradle.properties`,
`optional-integrations.txt`.

### 0.1 How it is gated, in four layers

1. **The proxy.** `Compatibility.aircraftCompat` is an `AircraftCompat` — *not* an interface with a
   stub, a concrete class whose answers are complete for a world with no aircraft in it. `deploy()`
   applies slow falling; `launchDropRun()` returns false; `sightings()` returns an empty list. Every
   caller may call it unconditionally.
2. **The loader check.** `MineColonies#onInitialize` swaps in `SimplePlanesCompat` only behind
   `FabricLoader.getInstance().isModLoaded("simpleplanes")`, following the precedent at
   `CompatibilityManager.java:847-856`. That line is the only thing in the mod that can load a
   `xyz.przemyk` class.
3. **The source set.** `build.gradle` drops `com/minecolonies/core/compatibility/simpleplanes/**`
   from compilation entirely when `simpleplanes_jar` is missing, and logs that it did. The mod builds
   on a machine that has never heard of Simple Planes.
4. **The config.** `airraids` (default true), `airraidchance` (default 25%), `aircraftwarnings`
   (default true). All three are documented in `manual_en_us.json` and say plainly that they need
   Simple Planes.

The dependency is **compileOnly** and deliberately not `include`: bundling an LGPL jar inside a GPL
one is a redistribution with its own obligations and there is no reason to take them on.

**[VERIFIED on a server]**: with both mods installed the log prints
`MineColonies: Simple Planes detected, aircraft integration enabled.` and the server reaches
`Done (1.166s)`. The no-Simple-Planes path is **[VERIFIED by construction]** — the classes are
excluded from the jar and nothing references them — but I did not boot a server without the aircraft
mod to watch it.

---

## 1. Pirate paradrop

### What the player sees
A raid is announced before anything appears: *"An aircraft is inbound on <colony>. Pirates are aboard,
and they are not landing at the gate."* A cargo aircraft comes in from a random bearing three hundred
blocks out, at seventy blocks above a building it has picked, flying at 1.2 blocks/tick — slowly, on
purpose, because it is meant to be seen and shot at. Over the building the bay opens and pirates leave
one every four ticks, in a stick, each under a parachute. They drift down at about a tenth of a block
a tick, land unhurt, and raid. The aircraft flies on and lands somewhere else.

### The seams
- Type selection: `RaidManager.java:452-466` — a new first branch in the horde block, taken only when
  `PirateAirRaidEvent.isAvailable()` (config **and** aircraft mod), either because the raid was asked
  for by name or on a `airraidchance` roll.
- Registration: `ModColonyEventTypeInitializer.java:40`.
- Flight: `AutopilotSpawner.launchRoute(level, [ingress, drop, egress], altitude, 1, null, null, 1.2,
  Blast(3, false, false), AircraftType.CARGO)` — `DropRunTracker.java:95-107`.
- Spawn: `RaiderMobUtils.spawnAt(...)`, a new method that places one raider at exactly the position
  given, with no ground search, and returns it. The old `spawn` would in fact have worked in open air
  — its `findAround` is bounded and falls through to the position handed in — but only by accident,
  and it returns nothing, and the caller needs the entity to put a parachute under it.
- Descent: `SimplePlanesCompat#deploy` — slow falling **first**, then a `ParachuteEntity` with the
  raider mounted via `startRiding(parachute, true, true)`.

### The raid bookkeeping, all three problems fixed
1. **The unloaded-chunk cull** (`HordeRaidEvent.java:469-476` deletes any raider whose block is not
   entity-ticking) would delete a parachutist mid-descent. `PirateAirRaidEvent#cullAndRespawn` keeps
   the cull and adds one guard: `entity.onGround()`. Anything still in the air is going somewhere and
   stays.
2. **The ground top-up** (`HordeRaidEvent.java:454-461` re-spawns to quota at a ground position) would
   replace a dead paratrooper with an infantryman walking in from the border. There is no top-up. An
   air drop is one wave — which is also what makes the transport worth shooting at.
3. **The reporting.** `onStart` moves `spawnPoint` to the drop point before anything reads it (the
   boss bar and every raid message derive a compass direction from it), and calls the new
   `IRaiderManager#updateLastSpawnPoint` so `/mc colony raid info` and `getLastSpawnPoints()` name the
   drop rather than the border point the manager had already written down.

### The trap I flagged in the defence study, closed
A transport destroyed before it drops anybody leaves `horde.hordeSize` at its initial value, and since
the raid only ends when that reaches zero (`HordeRaidEvent.java:434-438`), the raid would never end —
boss bar up, `isRaided()` true, for ever. `PirateAirRaidEvent#reconcile` closes the books explicitly:
the horde is rewritten to exactly what left the aircraft, and a wave stopped entirely ends the raid
with *"The transport went down before it could drop a single pirate!"* This is not conditional on the
anti-air battery existing — a transport can also fly into a hillside, or be lost to a restart.

### Cost
~530 lines in one new event class, plus ~450 across the bridge and tracker shared with the other two
features.

### Which descent shipped, and how I established it

**Shipped: the parachute, with slow falling underneath it as an unconditional backstop.** Both, not
either. The order matters — the effect is applied before the mount is attempted, so a parachute that
fails for any reason still leaves a raider who lands alive.

What I could establish, and it is less than I wanted:

- **[VERIFIED on a server]** `ParachuteEntity` carries a pathfinding mob down from altitude, and the
  passenger stays aboard. Summoned at y=150 with a zombie mounted at spawn: at t+2s the parachute was
  at 149.5 with the zombie riding at 149.845 and `fall_distance: 0.0`; at t+22s the parachute was at
  109.95, the zombie still aboard at 110.25, `Health: 20.0` — full. That is 39.6 blocks in 20 seconds,
  about 0.1 blocks/tick, with no damage and no accumulated fall distance.
- **[VERIFIED by reading]** Nothing in MineColonies dismounts a passenger from it. The navigator's
  only dismount is guarded on `MinecoloniesBoat` and `MinecoloniesMinecart` specifically
  (`MinecoloniesAdvancedPathNavigate.java:540-547`), and vanilla `LivingEntity` only calls
  `stopRiding` underwater with a `dismountsUnderwater` vehicle, or on going to sleep
  (`/opt/mc-src/.../LivingEntity.java:454-455, 3750`).
- **[NOT VERIFIED]** A MineColonies *raider* on a parachute, and the drop end to end.

**Why not, precisely.** A raid cannot be made to happen on a headless server. `Colony#updateState`
(`Colony.java:472-493`) returns `ACTIVE` only when there are close subscribers or important colony
players; with nobody logged in the colony is `INACTIVE`, its state machine never runs
`eventManager.onColonyTick` or `tickImmediateRaids`, and no raid event — mine or any other — ever
starts. I confirmed this the hard way: `/mc colony raid 1 now minecolonies:pirate_air_raid false 8`
reports *"Raid started for colony ScaleTest: 8 raiders at strength 0.89"* and then nothing happens at
all, for four minutes, with no debug line from `onStart` firing. The same is true of an ordinary
ground raid. And a raider summoned directly is discarded within a few ticks for want of a live event
(`EventManager.java:105-112`, `AbstractEntityMinecoloniesRaider.java:403-413`), so it cannot be
substituted.

So the two halves are verified separately and the join is not. That is the honest position, and it is
why the slow-falling backstop is in the shipped code rather than being an alternative I chose against.

**One real bug the testing did find**, which reading would not have: the ground fallback was broken.
If the transport cannot be launched, the event defers to `HordeRaidEvent#onStart` — which resolves its
spawn through `getLoadedPositionTowardsCenter` and refuses any point within `MIN_CENTER_DISTANCE` of
the colony centre. With `spawnPoint` already moved to the drop point, directly over a building, that
returns null and the event is `CANCELED`: a raid that silently never happens. The ground spawn point
is now saved and restored before falling back.

### Risks
- The join above.
- Drop altitude 70 is chosen so parachutists stay inside chunks the colony keeps loaded. Over a very
  large or sparse colony that may not hold, and `cullAndRespawn` would then recycle them once they
  land. **[UNCHECKED]**
- A run still in the air when the world saves is written off on load rather than resumed — the tracker
  is in memory and the aircraft does not know it is carrying anyone. What landed keeps raiding.

---

## 2. Air-raid warning

### What the player sees
The raid horn and a message naming a compass direction, either when an aircraft comes within 160
blocks, or — far more usefully — **the instant a scripted attack run is ordered against anything the
colony owns**, whatever distance it is at. Guards within 200 blocks of the threatened point are sent
to it. One alarm per colony per minute.

### The seams
- `AutopilotRegistry.active()` (`autopilot/AutopilotRegistry.java:82-85`) for scripted aircraft — a
  pruned list, no world lookup. `FlightPlan.kind() == STRIKE` and `strikeTarget()` give the aim point
  straight off the flight plan.
- A bounded `getEntitiesOfClass(PlaneEntity.class, …)` on a 20-tick interval for aircraft a player is
  flying by hand, which are in no registry.
- `IColonyManager#getColonyByPosFromWorld` to decide whose problem it is.
- `PlayAudioMessage.sendToAll(…, RaidSounds.WARNING, …)` and
  `AbstractBuildingGuards#setTempNextPatrolPoint` — the same call `RaidManager.java:1036-1039` makes.

### Why it is registry-first
Because it is the difference between fourteen seconds and three. A strike ordered 800 blocks out flies
for about 286 ticks at the airframe's ~2.8 blocks/tick. A colony claim is at most 20 chunks, so a
border detector sees it 320 blocks out at best — 5.7 seconds — and a young colony's claim buys 1.1.
The registry sees it at t=0.

### Cost
~196 lines, one file.

### Verification
**[VERIFIED by reading]** every API it uses. **[NOT VERIFIED]** end to end, for the same reason as the
paradrop — `AircraftWatch` walks `IColonyManager.getColonies(level)` every 20 ticks and warns colonies,
but with no player online there is nobody to warn and no colony ticking to be warned about. The sweep
itself runs off the server tick, not the colony tick, so it does execute; what it does when it finds
something is untested.

---

## 3. Anti-air battery

### What the player sees
A guard tower of level 3 or higher, with a bow-carrying guard assigned to it and arrows in its
inventory, engages aircraft out to **200 blocks**. It fires one arrow a second, leading the target.
Four hits bring an aircraft down. What comes down is a real aeroplane on a real trajectory: it loses
power where it was hit, falls, and hits the ground somewhere out in the country, and goes up.
*"The guards of <colony> have shot down an aircraft!"* Run out of arrows and it stops, and says so.

### Range is the design parameter
The engagement range is not a number to minimise. **A battery that kills the bomber overhead has
failed even when it kills it every time**, because the wreck lands on the colony. 200 blocks against
an aircraft closing at ~2.8 blocks/tick is ~71 ticks of tracking; four hits at one a second is ~80
ticks. So **one tower engages a fast target and does not quite finish it; two do** — which is the
intended difficulty, and why the range is not larger. It is also inside what the projectile can do:
`Ballistics` notes an arrow's horizontal reach saturates at `100 × launch speed`, which at the
battery's 4.0 blocks/tick is 400.

### Why it is not a change to `RangeCombatAI`
Because that range budget is wrong before anything else is. `RangeCombatAI#getAttackDistance`
(`:230-257`) caps at `MAX_DISTANCE_FOR_RANGED_ATTACK` = 24 (`GuardConstants.java:74`) and then adds
`user.getY() - target.getY()`, which for a target *above* the shooter is negative and makes the
envelope smaller. A strike run-in is flown at 100 blocks AGL. The guards' own combat AI is untouched.

### Bringing one down — the part that is design, not code
**A plane at zero health does not fall.** Both death paths require `onGround()`
(`PlaneEntity.java:681` and `:482`), so an aircraft shot to pieces at altitude flies on under its
flight director and detonates its warhead wherever it arrives — which for a bomber aimed at the town
hall is the town hall. Damage alone moves the crater.

So the kill is three lines in `AntiAirBattery#disable`, and the first one is the whole design:

1. **`setAutopilot(null)`.** The flight director stops flying it and `isPowered()` goes false — a
   scripted aircraft has no engine of its own, the autopilot was its power — so it becomes a falling
   object. **It also disarms the warhead.** `PlaneEntity#explode` reads the blast off
   `getAutopilot().getPlan().blast()` and falls back to `Blast.DEFAULT` when there is no autopilot
   (`PlaneEntity.java:507-511`). A 16-power incendiary block-breaker becomes ordinary TNT the moment
   the flight plan goes away. **[VERIFIED by reading]** — this is the mechanism the whole feature rests
   on, and it is why the owner's "let it fall and go bang out in the country" reads as a *reward*
   rather than a second disaster.
2. **`setThrottle(0)`** — the airbrake, so it comes down near where it was hit rather than gliding on.
3. **A chunk ticket**, renewed every tick until it lands. Clearing the autopilot takes it out of
   `AutopilotRegistry` and with it the ticket that was the only reason it ticked this far from a
   player; without a replacement it would hang in the air for ever.

Then it is **left alone**. A wreck coming down in open country is the point, not something to tidy
away: it lands, `crash` runs, it explodes with `Blast.DEFAULT`, and the player can see where it went.

**The one intervention** is `AntiAirBattery#tickWrecks`: a wreck descending *inside the colony's own
claim*, once it is within 5 blocks of the ground, is replaced by 60 large-smoke and 40 poof particles
and an explosion sound, and `discard()`ed. The player watches the whole descent and sees and hears an
impact; there is no crater through somebody's roof. `discard()` rather than `kill()` because `kill()`
runs `crash`, which explodes — the trick Simple Planes documents against itself at
`GunshipSortie.java:404-406`.

### Gunnery
- `Ballistics.solve(muzzle, aim, 4.0)` — called, not copied. It is direction-agnostic: `dy` is signed
  throughout, so a target 100 blocks up is the same algebra as one 20 blocks down.
- Lead in **all three axes**, from `plane.getDeltaMovement()`, three fixed-point passes. This is the
  one place `GunshipSortie` cannot be followed: it deliberately throws away vertical velocity because
  for a mob that is gravity jitter, and for a bomber in a 32° dive it is most of the motion.
- `CombatUtils.launchArrow(arrow, velocity)` — a new 4-line method taking a solved velocity, because
  `shootArrow` is typed on `LivingEntity` and, more to the point, aims with a `0.18 × range` gravity
  fudge tuned for walking mobs.
- Ammunition comes out of the guard tower's inventory, one arrow per shot. Running dry is the intended
  failure mode and is reported.

### Cost
~415 lines in one new file, plus ~20 in `CombatUtils`.

### Verification — the load-bearing claim, tested

**[VERIFIED on a server]: an arrow fired at a plane connects, damages it, and four arrows destroy it.**

The run, in order:
1. `/damage @e[type=simpleplanes:plane] 3 minecraft:arrow` took a fresh plane from `health: 10` to
   `health: 7`. `PlaneEntity#hurtServer` is reachable and the NBT reflects it.
2. A real arrow entity fired from 4 blocks took it from 10 to 5 — one hit, 5 damage, which is vanilla's
   `ceil(|v| × baseDamage)` at |v|≈1.5 and base 3.
3. **After four such arrows the plane was gone** — `No entity was found`.
4. A **lofted 40-block shot** — the elevation `Ballistics` exists to compute — also connected, taking a
   fresh plane to `health: 4`.

The first attempt at this failed and is worth recording, because it is the feature's whole rationale:
arrows fired flat from 10 blocks all missed and stuck in the ground (`inGround: 1b`). An arrow drops
about three blocks over ten, so a flat aim is not an approximation, it is a miss. That is precisely
why the battery solves a trajectory instead of pointing.

**[VERIFIED on a server]** separately: `AutopilotSpawner.launchRoute` really does produce an unmanned
aircraft that flies — `/autopilot route 300 140 0 -300 140 0 1.2` produced Plane #1114, which flew the
route and then ran a full arrival: approach planning, three go-arounds for terrain, a runway switch,
and a committed landing.

**[NOT VERIFIED]** The battery firing at a live target: it needs a colony with a manned, stocked,
level-3 guard tower, and a colony does not tick with no player online. Untested in practice: the
lead solution's accuracy, the ammunition draw, whether four hits land inside the engagement window,
and `tickWrecks`. **[UNCHECKED]** what `discard()` does to a pilot riding a hand-flown plane.

---

## What is left for the owner to confirm in play

1. **A raider stays on a parachute.** Order `/mc colony raid <id> now minecolonies:pirate_air_raid
   false 8` while standing in the colony. Everything else in that chain is verified; this is the join.
2. **The battery hits something.** Level-3 guard tower, an archer assigned, arrows in the hut, then
   `/autopilot strike <a colony block>` from 400 out and watch.
3. **The warning fires** for both a strike and a hand-flown plane.
4. That the transport at 1.2 blocks/tick and 70 blocks up reads well, and that the drop lands where it
   should.
