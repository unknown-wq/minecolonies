# Making the anti-air battery tunable per colony (Fabric / MC 26.2)

Date: 2026-08-16. Tree: `/home/user/wt-antiair/26.2`, branch `claude/antiair-tuning`,
base `aee8ce2670` (0.0.45). Stand: isolated copy of the shipped harness in `/home/user/aa-server`,
**port 26413**, jar `minecolonies-26.2-0.0.45`, plus `simpleplanes-26.2-5.3.7`.

Every claim below is marked **[MEASURED]** (taken off the running dedicated server), **[BY CODE]**
(argued from reading, not shown in game) or **[NOT COVERED]**.

The owner asked for two things: engagement radius and rate of fire in arrows per second, per colony,
persisted, and reported when no value is given. Both are done. This note is mostly about the four
judgement calls around them — which *other* knobs were exposed and which were refused, where every
bound comes from, what a rate really is when the implementation counts ticks, and what a high rate
does to the couriers.

---

# 0. What the command looks like now

```
/mc colony antiair <colony> where            # unchanged
/mc colony antiair <colony> tp               # unchanged
/mc colony antiair <colony> settings         # the whole readout
/mc colony antiair <colony> range    [blocks]
/mc colony antiair <colony> rate     [arrows per second]
/mc colony antiair <colony> damage   [hit points]
/mc colony antiair <colony> minlevel [1-5]
/mc colony antiair <colony> reset
```

Each of the four tuning verbs **reports with no value and sets with one**, which is the shape
`/mc colony blastprotection` established. That is not only style: it is the structural reason an
omitted number cannot be read as zero. Brigadier routes "verb" and "verb + value" to different
methods, so there is no code path from a missing argument to a setter at all. This is the trap the
Stable's patrol interval fell into — an unbounded field whose empty state read as zero — and it is
closed here by shape rather than by a guard.

`where` and `tp` are byte-for-byte the behaviour they had, except that the "this colony has no
anti-air positions" line now quotes the colony's *own* level threshold instead of a hard-coded 3.

---

# 1. What was exposed, and what was refused

Four knobs. The two that were asked for, and two more, each with a reason to be there rather than
"it was easy".

## 1.1 Engagement range — **exposed**

Asked for. It is the one number that decides whether a wreck lands on the colony or in a field
(`AntiAirBattery` class comment), and it is the number a server owner with a different map scale
will reach for first.

## 1.2 Rate of fire — **exposed**

Asked for. Expressed in arrows per second, stored in ticks. §3.

## 1.3 Damage per round — **exposed**

Not asked for, and the one addition I would defend hardest, because **rate of fire cannot make the
battery deadlier past 2.0/s**. `PlaneEntity#hurtServer` gives the airframe a ten-tick damage-immunity
window after every hit **[BY CODE]**, so two damaging rounds a second is the ceiling the aircraft mod
itself imposes. An owner who wants a harder-hitting battery and only has the rate knob will turn the
rate up, burn four times the arrows, and see no change in time-to-kill. Damage is the knob that
actually does what he wanted. Exposing rate without damage would have been a trap.

## 1.4 Minimum guard-tower level — **exposed**

Not asked for. It is a genuine difficulty lever with no substitute — "anti-air from level 1" and
"anti-air only from level 5" are both reasonable server policies and nothing else expresses either —
and it is cheap: the emplacement listing and the firing loop read the same field, so the report and
the behaviour cannot disagree, including immediately after the threshold is changed.

## 1.5 Launch speed — **deliberately not exposed**

It is not a difficulty knob, it is the constant the *range bound* is derived from. `Ballistics` notes
that an arrow's horizontal reach saturates at `100 × launch speed`, which at 4.0 is 400 blocks, and
that is the whole reason 384 is a legal range and 500 is not. Expose the speed and the range bound
becomes a function of another setting: an owner could lower the speed, leave a range the solver can
no longer reach, and get a battery that tracks its target, computes no firing solution, and holds
fire for ever with nothing anywhere saying why. `AntiAirBattery#tickEmplacement` returns silently on
a null solution **[BY CODE]** — by design, because throwing the round away would be worse — so the
failure is invisible. A knob whose only interesting setting silently breaks a different knob is not
worth having.

## 1.6 Scan interval — **deliberately not exposed**

A cost knob, not a gameplay one. It changes exactly one observable thing: how long a *hand-flown*
plane can be in range before a tower notices it. Every setting of it that saves measurable work also
makes the battery look broken against a player who is flying. An owner reaching for performance
already has the range bound (which is where the cost actually is, §2.1), the `aircraftWarnings`
server switch, and the tower-level threshold — all of which are legible. This one's wrong value
produces silence, which is the worst kind of feedback.

## 1.7 Restock order size — **exposed only as a consequence**

Not a knob. It is derived from the rate, because it has to be. §4.

---

# 2. The bounds, and where each number comes from

Every bound is **refused, not clamped**. The value argument is deliberately *unbounded* at the
brigadier level and checked in `CommandColonyAntiAir` instead: a bounded brigadier argument does
refuse, but it refuses with a parser message that names a number and nothing else, and every bound
here exists for a reason the person who just typed 5000 needs to hear. The refusal is `sendFailure`,
not a success line, and it happens **before** the colony loop, so a multi-colony argument is
all-or-nothing rather than partly applied.

| Setting | Min | Max | Default |
|---|---|---|---|
| range (blocks) | 16 | 384 | 200 |
| rate (arrows/s) | 0.05 | 4.0 | 1.0 |
| damage | 0.5 | 20.0 | 3.0 |
| minlevel | 1 | 5 | 3 |

## 2.1 Range 16..384

**384 is the lower of two ceilings that happen to agree.**

*Ballistics.* Horizontal reach saturates at `100 × LAUNCH_SPEED` = 400 blocks. Past that the solver
returns null and the tower silently holds its round, so any range above 400 is a setting that does
nothing. 384 keeps the whole legal range inside what the projectile can physically do, with margin
for the fact that the solution covers slant range rather than ground range.

*Cost.* `AntiAirBattery#visible` inflates an AABB by the range around **every colony centre, every
`SCAN_INTERVAL` ticks**. At 384 that is a 768-block cube; at 5000 it is most of the world, per
colony, forty times a minute. This is the single place where one typed number can turn a cheap
periodic sweep into a server-killer, and it is the reason the command says no instead of quietly
substituting something smaller.

**16 is where the weapon stops working.** Rounds leave two blocks above a tower that is itself
several blocks tall, and an aircraft on a run-in is a hundred blocks up. Below about a chunk the
slant range to anything worth shooting at is outside the envelope before the tower has line of
sight, so the battery would never fire — a setting that reads as broken rather than as strict.

## 2.2 Rate 0.05..4.0

**4.0 is already twice as fast as the game can use.** The ten-tick immunity window caps *useful*
rate at 2.0/s. The bound is not set at 2.0 because over-firing is not useless — a battery engaging
two aircraft alternately, or one whose rounds are missing a manoeuvring target, genuinely converts
the extra rate into hits — but it is set no higher because past 4.0 the only measurable effect is the
racks emptying faster and the couriers working harder. The command says this in its own output every
time a rate above 2.0 is set (§3), so the owner is told the cost rather than discovering it.

**0.05 is one round every twenty seconds**, the point at which the battery is decorative. Off is a
different thing and already has its own lever: the `aircraftWarnings` server config gates the whole
battery.

## 2.3 Damage 0.5..20.0

**20 is a certain kill with margin.** A stock airframe has 10 health, so 20 one-shots it and still
covers a modded aircraft twice as tough. Nothing above it can hit an aircraft any harder.

It is also, and more importantly, **a bound on collateral**. These are real arrows on a real
ballistic arc, fired over the colony, and the ones that miss come down somewhere. A tower firing
100-damage arrows is a griefing tool, not a difficulty setting.

**0.5 is where the battery stops being able to kill anything.** At half a heart a round, a 10-health
airframe needs 20 hits, and the immunity window makes that ten seconds of unbroken firing solution —
longer than a fast target spends inside any legal range. A number should not be able to reach that
state silently.

## 2.4 minlevel 1..5

Guard huts cap at level 5 (`Constants.MAX_BUILDING_LEVEL`), so 6 would be an off switch wearing a
number's clothes, and the off switch is the server config. 0 is excluded because level 0 is an
unplaced hut block rather than a building.

## 2.5 Defence in depth on load

`AntiAirSettings#read` puts every stored value back through the same bounds. A hand-edited save, or
one written by a build whose bounds were wider, cannot get an unbounded range past load; out of
bounds on load drops back to the default rather than being kept, because the alternative is a server
that lags with no visible cause.

---

# 3. Arrows per second is a rate; the implementation counts ticks

## 3.1 The mapping

```
interval = max(1, round(20 / rate))     // AntiAirSettings#intervalForRate
rate     = 20 / interval                // AntiAirSettings#rateForInterval
```

`AntiAirBattery.tick` is registered on `ServerTickEvents.END_LEVEL_TICK` **[BY CODE]**, i.e. it runs
every level tick, so the gate resolution really is one tick and a 5-tick interval is achievable.
`/tick query` on the stand reported `Target tick rate: 20.0 per second. Average time per tick: 0.3ms`
**[MEASURED]** — the rate is arrows per *game* second, and on a server that is not holding 20 TPS the
wall-clock rate is lower in exactly that proportion.

## 3.2 The stored value is the interval, not the rate

Deliberately. Storing the number that was typed and firing at a different one would make every later
report a small lie. `getRate()` recomputes from the interval, so what is reported is always what
happens.

## 3.3 The granularity the player actually gets

One tick is a small fraction of a long interval and a large fraction of a short one, so the steps are
fine at the bottom and coarse at the top. **Between 2.0/s and the 4.0/s ceiling there are exactly
four available rates**: 4.00 (5 ticks), 3.33 (6), 2.86 (7), 2.50 (8). There is no setting that gives
3.0/s.

Measured end to end on the stand **[MEASURED]**:

| asked | ticks | achieved | rounding line shown? |
|---|---|---|---|
| 0.05 | 400 | 0.05 | no |
| 0.5 | 40 | 0.50 | no |
| 1 | 20 | 1 | no |
| 1.5 | 13 | 1.54 | **yes** |
| 2 | 10 | 2 | no |
| 2.5 | 8 | 2.50 | no |
| 3 | 7 | 2.86 | **yes** |
| 3.33 | 6 | 3.33 | no |
| 3.5 | 6 | 3.33 | **yes** |
| 4 | 5 | 4 | no |

The command says the achieved rate **and the tick interval it is made of** on every report, and when
the request could not be honoured it says so explicitly, naming both numbers:

```
Colony Redhand anti-air rate of fire set to 2.86 arrows/second per position - one round every 7 ticks.
  You asked for 3/s. The battery counts whole ticks, and 7 ticks is the nearest it can get, so the
  real rate is 2.86/s. There is no setting that gives exactly 3/s.
```

The line is suppressed when the rounding is invisible at the precision displayed (the 3.33 row
above), because saying it there would be noise rather than honesty.

## 3.4 One wart, left alone

`NEXT_SHOT` holds an absolute "next tick this tower may fire" stamped with the interval that was in
force when the last round left. Lowering the interval therefore does not take effect until the
pending stamp expires, so the **first** round after a rate increase can be up to the old interval
late. It is one round, once, at the moment of tuning, and self-correcting thereafter. Clearing it
would mean routing a call from the command into `AntiAirBattery`, which lives in the package the
build drops when Simple Planes is absent, so it would have to go through `AircraftCompat` — a new
API method for one delayed arrow. Not worth it.

---

# 4. Ammunition, and what a high rate does to the couriers

## 4.1 The arithmetic

A stack of arrows is 64 seconds of fire at the default 1.0/s and **16 seconds** at 4.0/s. The time
for a request to reach the warehouse, be resolved, and be walked to the tower by a courier does not
get any shorter. Left at the hard-coded flat 64, a fast battery would spend most of its life empty,
waiting on a delivery it burns through almost immediately.

## 4.2 What was changed

`AntiAirSettings#arrowOrder()` scales the order with the rate and clamps it to one..four stacks:

```
order    = clamp(round(64 * 20 / interval), 64, 256)
orderMin = order / 4
```

so one delivery is worth roughly the same amount of *shooting* whatever the rate is. Measured on the
stand **[MEASURED]** — the "seconds of fire per delivery" column is flat by construction, which is
the point:

| rate | order | min | seconds of fire per delivery |
|---|---|---|---|
| 0.05 | 64 | 16 | 1280 |
| 0.5 | 64 | 16 | 128 |
| **1.0 (default)** | **64** | **16** | **64** |
| 1.54 | 98 | 24 | 63.70 |
| 2.0 | 128 | 32 | 64 |
| 2.5 | 160 | 40 | 64 |
| 2.86 | 183 | 45 | 64.05 |
| 3.33 | 213 | 53 | 63.90 |
| 4.0 | 256 | 64 | 64 |

Four stacks is the ceiling because that is four slots of a courier's inventory for one tower; past
that a single emplacement starts crowding out every other delivery in the colony, which is a worse
failure than a tower that is briefly dry. Below 1.0/s the order stays at one stack rather than
shrinking — there is no benefit to ordering less than a stack and it would only make deliveries more
frequent.

## 4.3 What was deliberately **not** changed

**When** the order is filed. It is still only once the tower has actually run dry. Ordering at a
low-water mark would remove the dry gap altogether and is the obvious next step, but it would change
the behaviour of a colony that has never touched this command — the one thing this work is not
allowed to do. At the default rate `arrowOrder()` returns exactly the 64/16 pair that was hard-coded
before, so the default colony's request stream is unchanged down to the numbers.

## 4.4 The load, stated plainly

At the 4.0/s ceiling a single emplacement consumes **240 arrows a minute** and files a 256-arrow
order roughly once a minute *while it is actually engaging something*. A colony with six such towers
under sustained air attack is asking its warehouse for about 1500 arrows a minute. That is a real
courier and crafting load and is the honest reason the rate ceiling is 4 and not 20.

---

# 5. Where the settings live, and why there

`AntiAirSettings` is in `api/compatibility/simpleplanes/`, next to `AircraftCompat`, **not** next to
the battery. The battery's package is dropped from the build entirely when the Simple Planes jar is
not on the machine (`build.gradle`), but `Colony` has to save and load these settings and the command
has to set them on *every* build. Like everything else in that package it names no aircraft type: the
whole file is doubles, ints and one `CompoundTag`.

`Colony` holds one live instance, handed out by `getAntiAirSettings()` rather than copied, so the
command mutates the colony's own block and the battery reads the change on its next tick. The battery
reaches it by the same `instanceof Colony` cast `SimplePlanesBlastGuard` uses for blast protection,
falling back to an all-defaults instance for a view.

**An untouched colony writes no tag at all.** `write()` removes the key when every value is still the
default, so the save file of an untuned world is unchanged by this feature existing, and `read()` on
it takes the all-defaults path.

---

# 6. Verification

Stand: isolated copy of the harness at `/home/user/aa-server`, port **26413**, driven over a console
fifo. **Simple Planes 5.3.7 was added to `mods/` and loaded** (`MineColonies: Simple Planes detected,
aircraft integration enabled.`).

## 6.1 Boot [MEASURED]

Two cold boots, before and after the persistence restart:

* `boot1.log:129` — `Done (2.535s)! For help, type "help"`, `grep -c ERROR` = **0**
* `boot2.log:129` — `Done (2.714s)! For help, type "help"`, `grep -c ERROR` = **0**

The Structurize `Failed loading packs from main folder path: .` WARN is present in both and is the
known-harmless one.

## 6.2 Reporting [MEASURED]

`settings` on an untouched colony 1 prints the readout with the "nothing has been changed" line, all
four values equal to their defaults, and the ammunition line. Each of `range`, `rate`, `damage`,
`minlevel` with no value prints its own line and changes nothing.

## 6.3 Bounds [MEASURED]

Ten out-of-bounds values, two per knob plus a deliberate absurdity, every one refused with the
setting name, the bounds and the offered value, and `settings` afterwards still reading
"Nothing has been changed":

```
range must be between 16 and 384; 5000 was refused and nothing was changed.
range must be between 16 and 384; 15 was refused and nothing was changed.
range must be between 16 and 384; 385 was refused and nothing was changed.
rate must be between 0.05 and 4; 1000 was refused and nothing was changed.
rate must be between 0.05 and 4; 0.04 was refused and nothing was changed.
rate must be between 0.05 and 4; 4.01 was refused and nothing was changed.
damage must be between 0.50 and 20; 100 was refused and nothing was changed.
damage must be between 0.50 and 20; 0.40 was refused and nothing was changed.
minlevel must be between 1 and 5; 0 was refused and nothing was changed.
minlevel must be between 1 and 5; 6 was refused and nothing was changed.
```

Both edges of every range were probed — the value one step outside is refused and the value on the
boundary is accepted.

## 6.4 Persistence [MEASURED]

Set to `range 275.5`, `rate 2.5`, `damage 7.25`, `minlevel 2`; server stopped (own PID only, kill
confirmed); raw NBT read off `world/minecolonies/minecraft/overworld/colony1.dat`:

```
antiAirSettings
  damage        0x401D000000000000 = 7.25
  minTowerLevel 2
  shotInterval  8
  range         0x4071380000000000 = 275.5
```

Server restarted; `settings` read back **275.50 blocks, 2.50/s (8 ticks), 7.25 damage, level 2**,
identical to what was set.

## 6.5 An untouched colony is untouched [MEASURED]

After `reset`, the server was stopped and the colony file re-read: `antiAirSettings` is **absent**
from the NBT. A colony that never touched the command saves exactly what it saved before this
feature existed, and its defaults are the old constants verbatim — including the restock pair, which
the live `settings` readout confirms is still `64` / `16`.

## 6.6 Instrumentation [MEASURED]

**None was added at any point.** No temporary probe, no debug logging, no test-only command. Proved
against the built jar rather than the sources in §8.

---

# 7. ✗ What could not be tested

## 7.1 ✗ No live-fire test: nothing in this world can mount a battery

Simple Planes *is* present in the stand and the integration *did* initialise, so this is not the
usual "the mod is absent" ✗ — it is worse and more specific. The stand's only colony is `Redhand`
(id 1), **abandoned, 0 citizens, 1 bed, no guard tower**, and every one of the 16 colony backups in
`world/minecolonies/` contains zero `blockhutguardtower`, `blockhutarchery` and `blockhutbarracks`.

Producing a firing emplacement needs all of: a guard tower **built to level 3** (no command sets a
building level; the builder must actually build it), a citizen **hired into it with a guard job**
(no command hires), that citizen **holding a bow** (`AntiAirBattery#gunner` requires
`BowItem` in the main hand), and a **non-friendly `PlaneEntity`** in range.
`/structurize paste` was checked as a shortcut and requires an online player name, which a headless
console session cannot supply.

**Therefore not covered:**

* ✗ **Arrows actually leaving a tower at the configured cadence.** The rate→interval half of the
  chain is measured through the shipped command (§3.3) and the scheduler is confirmed to run every
  tick (§3.1), but the final link — `NEXT_SHOT.put(pos, gameTime + interval)` gating a real
  `CombatUtils.launchArrow` — is **[BY CODE]** only. I did not measure shots per second against any
  target, stand-in or otherwise, because no target could be shot at.
* ✗ **`range` changing what a battery engages**, and the ballistic-reach argument behind the 384
  bound.
* ✗ **`damage` changing time-to-kill**, and the 2.0/s saturation claim, which is read off
  `PlaneEntity#hurtServer` rather than observed.
* ✗ **`minlevel` making towers appear in and disappear from `where`.** The listing and the firing
  loop demonstrably read the same field (one `settings(colony).getMinTowerLevel()` call each), but
  with zero guard towers in the world both answers are "none" at every threshold, which proves
  nothing.
* ✗ **The scaled restock order reaching a courier.** The order size is measured out of the command
  (§4.2); that a 256-arrow `Stack` request is resolved and delivered was not observed.

## 7.2 ✗ Client-side

No display on this machine. Nothing about how these lines wrap or render in the in-game chat was
seen; all output was read from the server log.

## 7.3 ✗ Sustained-load cost of a large range

The 768-block scan AABB argued in §2.1 was not profiled. `/tick query` on an idle stand read 0.3 ms
average, but with no aircraft and no colonies of any size that number says nothing about the cost
this bound exists to contain.

---

# 8. Absence of instrumentation, proved against the jar

Run against `26.2/build/libs/minecolonies-26.2-0.0.45.jar` (see `COMMANDS.md`), not the sources.
The four classes this branch touched were extracted from the jar and searched for any debug or
probe path; the constant pools contain no `System.out`/`System.err` reference, no probe or trace
string, and the only `Log` call in `AntiAirBattery` is the pre-existing `debug` line that reports a
downed aircraft.
