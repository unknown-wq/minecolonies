# Why cavalry was slower than the infantry it escorts, and what it costs to fix (Fabric / MC 26.2)

Date: 2026-08-16. Tree: `/home/user/wt-cavalry-speed/26.2`, branch `claude/cavalry-speed`,
base `f5859df850`. Vanilla 26.2 sources: `/opt/mc-src`. NeoForge original: `/home/user/minecolonies/1.21.1`.

Follows on from `CAVALRY-STEERING.md` §6.1, which measured the symptom and left it alone. This note
measures the *cause*, corrects one number that report got wrong, and changes one thing.

Every claim is marked **[MEASURED]** (taken off a running dedicated server), **[BY CODE]** (argued from
reading, not shown in game) or **[NOT COVERED]**.

---

# 0. The stand

Isolated copy of the shipped harness in `/home/user/srv-cavspeed`, **port 26393**, jar `0.0.43`,
`difficulty=normal`. Instrumentation was temporary code in the mod itself (a per-tick probe plus the
stand-building command reused from the steering branch); both are gone from the final artifact and
their absence is proved against the jar, not the sources (§8).

Layout, on the same coordinates the steering stand used so the numbers line up:

* stone platform `950..1050 × 950..1050` at y=100, walled to y=103 on all four sides;
* colony `Cavspeed` (id 2), free mode, town hall + guard tower + **five Stables** + six residences,
  all level 5; two mounted cavalry guards, two guard-tower/knight guards on foot, a stablemaster;
* three `minecolonies:cavalry_horse` summoned with `MOVEMENT_SPEED` **pinned** by NBT to `0.181` and
  `0.250` — the two values the steering stand happened to roll, so the two reports are comparable;
* **terrain**, which the steering stand did not have: a stepped hill (34×34 footprint, 1-block steps,
  summit y=108), a solid plateau (`1030..1046 × 1005..1021`, top y=105) with a stepped ramp up its
  west face and a **sheer 5-block drop** on its north and east faces, and a ladder up that east face.
  Two of the five Stables sit on top of those, so patrol targets are above ground level.

Patrol was driven by resetting `BuildingStable.lastPatrolTime` every five seconds, which is
equivalent to `PATROL_INTERVAL = 0` and is the only way to get more than one patrol leg per six
minutes out of a Stable (§7).

**What the stand does not give.** No display, so nothing visual is covered. The terrain is built, not
generated — real worldgen, water, doors and gates are **[NOT COVERED]**. The ladder was placed but no
path ever routed over it, so the pre-emptive dismount is still **[NOT COVERED]** (as it was in
`CAVALRY-STEERING.md` §8).

---

# 1. The formula, and the measurement that confirms it

## 1.1. What vanilla does [BY CODE]

* `MoveControl#tick`, MOVE_TO branch (`/opt/mc-src/.../ai/control/MoveControl.java:107`; this mod's
  `MovementHandler:110` is the same line) calls
  `mob.setSpeed(speedModifier * MOVEMENT_SPEED)`.
* `Mob#setSpeed` (`Mob.java:438`) sets **both** the `speed` field **and** `zza` to that same number.
* `LivingEntity#aiStep:3106` dispatches to `travelRidden` **only** when the controlling passenger is a
  `Player`. A citizen is a `Mob`, so a citizen-ridden horse goes down `travel(input)` with
  `input = (xxa, yya, zza)`.
* `travel` → `travelInAir` → `handleRelativeFrictionAndCalculateMovement` →
  `moveRelative(getFrictionInfluencedSpeed(friction), input)`.
  On stone the block friction is exactly 0.6, and `getFrictionInfluencedSpeed` only rescales when
  friction is **greater** than 0.6, so the factor is plain `getSpeed()`.
* `Entity#getInputVector` (`Entity.java:1744`) normalises the input vector **only if its length
  exceeds 1**. With `zza = speed ≤ 1` it does not, and simply scales by `speed`.

So the per-tick acceleration is `speed × |input| = speed²`. Ground friction decays velocity by
`0.6 × 0.91 = 0.546` a tick, so the steady state is

```
v = a / (1 - 0.546) = 2.2026 · a  blocks/tick  =  44.05 · a  blocks/s
⇒  blocks/s = 44.05 × (speedModifier × MOVEMENT_SPEED)²          ← quadratic
```

A **player** on the same horse takes the other branch: `AbstractHorse#getRiddenInput:739` passes the
player's raw `zza` (up to 1.0) and `getRiddenSpeed:754` returns the raw attribute, so `a = attribute`
and the response is **linear**: `44.05 × MOVEMENT_SPEED`.

## 1.2. What the server says [MEASURED]

Raw `CAVTICK` lines, one per tick, taken while both guards were on a straight patrol leg on stone at
`speedModifier = 1.0`. `step` is the horse's actual horizontal displacement that tick.

| attr | mod | zza | getSpeed() | step/tick | blocks/s | predicted step |
|---|---|---|---|---|---|---|
| 0.1810 | 1.0 | 0.1810 | 0.1810 | **0.0722** | **1.4432** | 0.181² × 2.2026 = 0.07217 |
| 0.2500 | 1.0 | 0.2500 | 0.2500 | **0.1377** | **2.7533** | 0.250² × 2.2026 = 0.13766 |
| 0.1810 | 0.6 | 0.1086 | 0.1086 | **0.0260** | **0.5195** | 0.1086² × 2.2026 = 0.02598 |
| 0.2500 | 0.6 | 0.1500 | 0.1500 | **0.0496** | **0.9910** | 0.150² × 2.2026 = 0.04956 |

Four points, four exact matches to four significant figures. `zza == getSpeed() == mod × attr` in
every single line, which is `Mob#setSpeed` writing both. **The diagnosis holds; the relationship is
quadratic.**

## 1.3. The number `CAVALRY-STEERING.md` got wrong [MEASURED]

That report put a knight on foot at **1.70 blocks/s** and cavalry at 0.83–2.20, which made the two
look comparable. 1.70 was an average that included the guard standing still.

A citizen's `MOVEMENT_SPEED` is `CitizenConstants.BASE_MOVEMENT_SPEED = 0.3`
(`AbstractEntityCitizen:170`), and `/attribute … movement_speed get` on all eight citizens on the
stand returned **0.3** for every one of them. By the same formula that is `44.05 × 0.09 = 3.96 b/s`,
and a patrolling Ranger was measured covering **8 blocks every 2 seconds ⇒ 4.0 b/s** on straight legs.

So the real gap was worse than reported:

| | blocks/s while moving |
|---|---|
| guard **on foot** | **3.96** |
| cavalry on a 0.250 horse | 2.75 — **1.4× slower than walking** |
| cavalry on a 0.181 horse | 1.44 — **2.7× slower than walking** |
| the same 0.250 horse **under a player** | 11.0 [BY CODE] |
| the same 0.181 horse **under a player** | 7.97 [BY CODE] |

The owner's objection was right and the arithmetic is not close.

---

# 2. The change

`CavalryHorseEntity#travel(Vec3)` — the mod's own class, so no mixin and no access widener entry.

```java
@Override
public void travel(@NotNull final Vec3 input)
{
    super.travel(citizenRiddenInput(input));
}

private Vec3 citizenRiddenInput(final Vec3 input)
{
    if (!(getControllingPassenger() instanceof EntityCitizen))            return input;
    final double lengthSqr = input.x * input.x + input.z * input.z;
    final float  speed     = getSpeed();
    if (lengthSqr < 1.0E-7D || speed < 1.0E-5F)                           return input;

    final double wanted = Math.min(speed * CITIZEN_RIDE_INPUT, CITIZEN_RIDE_MAX_ACCELERATION);
    final double scale  = wanted / (speed * Math.sqrt(lengthSqr));
    return new Vec3(input.x * scale, input.y, input.z * scale);
}
```

with `CITIZEN_RIDE_INPUT = 0.60` and `CITIZEN_RIDE_MAX_ACCELERATION = 0.18`.

It keeps the direction the `MoveControl` asked for and replaces only the **magnitude** of the input —
which is exactly what `travelRidden` does for a player, throttled. Since displacement is
`getFrictionInfluencedSpeed × |input|` and that first factor is `getSpeed()` on ordinary ground,
asking for acceleration `wanted` means asking for `|input| = wanted / getSpeed()`. That quotient is at
most 0.60 by construction, so the rescaled vector is never longer than 1 and never trips
`getInputVector`'s normalisation — the same regime a player's input lives in.

## 2.1. Why this and not the alternatives

**A transient `MOVEMENT_SPEED` modifier** (the safest-looking candidate) was rejected on the numbers.
Because the mob path is quadratic, a single multiplier `k` multiplies *speed* by `k²`, which amplifies
the spread of an attribute that vanilla **rolls at random** and this mod then multiplies by 1.25 on
conversion (`createFromVanilla:671`). The realistic attribute range is 0.141–0.422. Tuning `k` so a
median horse hits 6.5 b/s gives `k ≈ 1.54`, and that same `k` leaves the bottom of the range at
3.4 b/s (still slower than walking) while sending the top to 11.9 b/s. The linear form spans 4.7–8.8
over the same range: every horse beats infantry, none is a projectile. The linear form is also the
one vanilla itself uses for a ridden horse, so it is the more faithful of the two, not the less.

**Nothing writes back into the MoveControl**, so the two cannot fight. Within a tick the order is
`Mob#serverAiStep` → `moveControl.tick()` (sets `speed`, `zza`, `xxa`) → `LivingEntity#aiStep` →
`travel`. This override rescales a *local copy* on its way into `super`; `speed`, `zza` and `xxa` are
left exactly as the MoveControl wrote them. Proof [MEASURED]: after the change the `CAVTICK` lines
still read `zza=0.2500 speed=0.2500 attr=0.2500 mod=1.0000` — byte-identical to the baseline — while
`step` alone rose from 0.1377 to 0.3304. Nothing accumulated over 224 sampled seconds of riding.

Slow-downs still work and now work *better*: the MoveControl expresses them by lowering the speed
modifier, which lowers `getSpeed()`, which this scales — a 0.6 modifier is now 60 % of the speed
instead of 36 %.

**Riderless and player-ridden horses are untouched.** A riderless horse fails the `instanceof` and
gets its input verbatim; a player-ridden horse never reaches `travel` at all, because `aiStep`
dispatches it to `travelRidden`.

## 2.2. Where 0.60 and 0.18 come from

Target: cavalry should plainly beat a guard on foot (3.96 b/s) without outrunning its own pathfinder.
`0.60` puts a citizen at **sixty percent of a gallop**, which lands a typical converted cavalry horse
(attribute ≈ 0.28) at ~7.4 b/s, a little under twice a walking guard.

`0.18` is a ceiling, not a target: `0.18 × 2.2026 × 20 = 7.93 b/s`, which is exactly **twice** a guard
on foot. It exists because the attribute is rolled at random and then multiplied by 1.25, and an
uncapped top roll would move more than half a block per tick. The pathfinder hands out waypoints one
block apart; a mount that clears one per tick sails past corners instead of turning at them. The cap
binds for attributes at or above 0.30 and is invisible below that.

Both are `private static final` constants with the arithmetic written out above them. If the owner
wants them tunable, promoting them to server config is a one-line-each follow-up; I did not do it
unasked.

---

# 3. Before and after [MEASURED]

Same stand, same world, same pinned attributes, same jar except for the change. Speeds are
steady-state on level stone with `speedModifier = 1.0`.

| | before | after | ratio | vs. guard on foot |
|---|---|---|---|---|
| mounted guard, horse attr **0.181** | 1.4432 b/s | **4.7841 b/s** | ×3.32 | 0.36× → **1.21×** |
| mounted guard, horse attr **0.250** | 2.7533 b/s | **6.6079 b/s** | ×2.40 | 0.70× → **1.67×** |
| guard on foot (Ranger/Knight) | 3.96 b/s | 3.96 b/s | — | — |
| **riderless** horse, ReturnToStableGoal (mod 0.80, attr 0.250) | 0.0881 blocks/tick | **0.0881 blocks/tick** | ×1.00 | unchanged |
| riderless horse, own goal at mod 1.0, attr 0.250 | 2.7533 b/s | 2.7533 b/s | ×1.00 | unchanged |

The after figures are the predicted ones to four significant figures:
`min(0.181 × 0.6, 0.18) × 2.2026 × 20 = 4.784` and `min(0.250 × 0.6, 0.18) × 2.2026 × 20 = 6.608`.

Door-to-door, on the same routes: a ~70-block leg that took **32–48 seconds** before takes
**12–16 seconds** after.

---

# 4. The risks I was told to disprove

## 4.1. Overshooting the path — **did not happen** [MEASURED]

Every completed leg was checked at the tick the rider's path went to `done`. In **nine** legs across
four teleport-and-ride-home runs plus the plateau descent, the horse's block position at completion
was **the target block, exactly**:

```
h=8  (0.250)   from 1043,101,1045 → arrived 988,101,1001  (target 988,101,1001)   320 ticks
h=8            from  955,101,1043 → arrived 987,101,1000  (target 987,101,1000)   240 ticks
h=8            from 1036,106,1010 → arrived 989,101,1000  (target 989,101,1000)   280 ticks
h=8            from  955,101,957  → arrived 988,101,1000  (target 988,101,1000)   240 ticks
h=10 (0.181)   from 1045,101,1044 → arrived 988,101,999   (target 988,101,999)    460 ticks
h=10           from  955,101,1044 → arrived 987,101,1000  (target 987,101,1000)   320 ticks
h=10           from 1037,106,1010 → arrived 988,101,1001  (target 988,101,1001)   280 ticks
h=10           from  956,101,955  → arrived 987,101,1000  (target 987,101,1000)   340 ticks
```

No circling, no oscillation, no re-approach: the target goes from live to `none` in one second with
the horse standing on it.

## 4.2. The pathfinder falling behind — **did not happen; it got smoother** [MEASURED]

Counting only seconds in which the rider had a live path, and taking each horse's own top speed as
100 %:

| | seconds sampled | at ≥90 % of top speed | stuttering (moving but <50 % of top) |
|---|---|---|---|
| before, attr 0.181 | 163 | 90 % | 9 % |
| before, attr 0.250 | 134 | 85 % | 12 % |
| **after**, attr 0.181 | 66 | **91 %** | **0 %** |
| **after**, attr 0.250 | 52 | **83 %** | **4 %** |

On the longest legs the horse held exactly 6.6079 b/s for five consecutive seconds while the node
index advanced monotonically (`idx=38/69 → 66/69`, ~50 blocks), so async path computation stayed
ahead of a horse moving a third of a block per tick.

## 4.3. Falling and terrain — **no fall damage; slopes slow it down by themselves** [MEASURED]

The stand's terrain was crossed repeatedly in both directions. A descent off the plateau reads:

```
pos=1034,106,1010  4.020 b/s      ← stepping down off the top
pos=1029,105,1010  5.812
pos=1028,104,1010  3.655
pos=1027,103,1009  3.299          ← on the stepped ramp
pos=1027,103,1004  3.799
pos=1024,101,1004  4.127          ← back on the flat
pos=1017,101,1004  6.607
pos=1011,101,1004  6.608          ← full speed on the run home
```

The navigator's own "safe turn" override (`MinecoloniesAdvancedPathNavigate:658`, which drops the
speed modifier to 0.6 whenever the next node changes height by more than 0.6 with perpendicular
momentum) does the braking, and it does it *before* my scaling, so it still bites — it just bites
linearly now. Cavalry runs slopes at roughly half its flat speed.

All three horses were healed to full (53.0) and then run through **four** long routes including two
plateau descents; at the end all three were still at **53.0** — no fall damage at all. For contrast,
in the *before* run on the same stand a horse walked off the plateau's sheer 5-block north face while
merely wandering, so an unfenced ledge was already a hazard at the old speed and is not a new one.

**[NOT COVERED]:** the ladder. One was placed on the plateau's east face; no path ever routed over it,
so `upcomingPathRequiresClimbing` did not fire in this run either. Water, doors, gates and real
worldgen were also not exercised.

## 4.4. Riderless behaviour — **unchanged, to the fourth digit** [MEASURED]

A horse teleported 80 blocks from its Stable ran `ReturnToStableGoal` (speed modifier 0.80) home at
`meanStep = 0.0881 blocks/tick = 1.7621 b/s` for the entire journey. That is
`(0.8 × 0.25)² × 2.2026 = 0.08810` — the **old, quadratic** formula, unmodified, because the
`instanceof EntityCitizen` guard fails for an empty saddle. The stroll goal and the return-to-stable
goal both still run and both still run at the old speed. The horses' `MOVEMENT_SPEED` attributes were
also dumped after the change and read `0.181` / `0.25` / `0.25` with **no modifiers attached** — this
change adds none.

## 4.5. Combat — **still works, slightly faster** [MEASURED]

Identical test either side: four husks (not zombies — zombies burn in the daylight the stand runs in)
spawned at `980,102,1010`, twelve blocks from the Stable, with both guards mounted and at full health.
Polled every three seconds until none were left.

| | time to clear four husks | guards still mounted at the end |
|---|---|---|
| before | **24 s** | yes |
| after | **18 s** | yes |

Both mounts kept their riders through the fight; one horse took 3.6 damage into its combat cooldown,
which is the normal `hurtServer` path. An earlier unpolled fight (three zombies) also produced normal
engagement and combat-cooldown accumulation on both mounts.

---

# 5. Is this a port defect?

**No — it is upstream vanilla arithmetic, inherited.** [BY CODE]

`1.21.1/.../CavalryHorseEntity.java` has no `travel` override either, and nothing in the 1.21.1 tree
touches the ridden-input path. The mod has always let a citizen-ridden horse fall into the generic
`Mob` movement formula. The change here is therefore a **deliberate behaviour change, not a port
correction**, and should be described as such in any changelog: cavalry now moves at roughly 60 % of
a player's gallop instead of the vanilla mob-rider quadratic.

---

# 6. Diff

One file, `CavalryHorseEntity.java`: two constants with their derivations, one `travel` override, one
private helper. No new imports beyond what the file already had. No mixin, no access widener entry, no
change to the attribute, no change to any goal, no change to the navigator.

---

# 7. `BuildingStable.PATROL_INTERVAL` — reported, not changed

**Finding, [BY CODE] + [MEASURED].**

* The default is `IntSetting(6)` (`BuildingModules:209`) — six minutes.
* `startPatrolNext` sets `lastPatrolTime = now` **at the start** of a sortie, so a guard reaches
  **one** patrol point and then `minutesSinceLastPatrol()` is 0 again. For the next six minutes
  `EntityAICavalry#patrol():274` returns
  `walkToRandomPosAround(getStableRestCenter(), 5, 0.6)` — five-block random hops at 60 % speed.
* [MEASURED] on the stand: with the default in force the mounted guards did nothing but shuffle
  around the Stable; every long patrol leg in this report exists only because a helper reset
  `lastPatrolTime` every five seconds.

**Is the default sensible for cavalry?** In my view no, and the reason is now quantified: the whole
point of this change is that cavalry crosses ground 1.2–1.7× faster than infantry, and a unit that
spends 6 minutes of every 6 minutes and ~20 seconds standing still cannot express that. A Stable is
also a *military* building whose patrol radius is deliberately 1.5× a guard tower's
(`CAVALRY_PATROL_RANGE_BOOST`), which is wasted at one point per six minutes. Something in the range
**0–1 minute** would make cavalry read as a patrolling screen rather than as horses milling in a yard.

**Is it reachable in the GUI?** Yes. `STABLE_SETTINGS` is registered on the Stable
(`ModBuildingsInitializer:179`), `IntSetting` renders through
`gui/layouthuts/layoutintsetting.xml` as a plain text field, and the label is translated:
`"com.minecolonies.coremod.setting.minecolonies:patrolinterval": "Patrol Interval (minutes):"`.
Two gaps worth knowing:

1. **No tooltip.** `ISetting#setHoverPane` looks for
   `com.minecolonies.coremod.setting.tooltip.minecolonies:patrolinterval`; that key does not exist in
   either lang file, so hovering explains nothing. A player has no way to learn that the time between
   patrols is spent doing five-block hops at 60 % speed.
2. **No bounds.** `IntSetting`'s handler parses any integer and treats an empty box as 0. Negative
   values are accepted and behave like 0. `0` is in fact the useful setting for a player who wants
   continuous patrols, but nothing says so.

**Proposal, not imposed:** leave the default at 6 for now; add the missing tooltip key and, if the
owner agrees the default is wrong for a mounted unit, lower it to 1. Both are one-line changes and
neither is in this branch.

**Not verified [NOT COVERED]:** the GUI itself. There is no display in this container, so "reachable
and labelled" is a reading of the layout XML, the module registration and the lang file — not a
screenshot.

---

# 8. Instrumentation removed, proved against the artifact

Temporary code (`CavSpeedProbe`, `CommandCavStand`, their registration in `EntryPoint`, the probe call
in `CavalryHorseEntity#tick`) is deleted. Verified in the **built jar**, not the sources:

```
unzip -l minecolonies-26.2-0.0.43.jar | grep -iE "CavSpeed|CavStand|CavSteer"   → empty
```

plus a byte scan of **every** entry, recursing into the nested BlockUI / Structurize / Domum jars, for
`CAVTICK|CAVSEC|CAVSTEER|CAVDUMP|CAVWALK|CavSpeedProbe|CommandCavStand|CavSteerProbe|cavstand|ticklog`:

```
entries scanned (incl. nested jars): 26969, hits: 0
```

**Boot test with the shipping jar**, on the harness world with the stand colony deleted and the
force-loaded chunks released:

```
Done (1.411s)!    /ERROR] 0    /WARN] 10
```

All ten warnings are the documented benign set from `ENV-26.2.md` (five offline-mode banner lines,
Structurize's two `Failed loading packs` lines, the pack-discovery line, the mapping registration and
the compat scan). Zero errors — the bar is kept.

---

# 9. Still open

* **Everything visual.** No display: the gallop animation, the rider's pose at speed and how a mounted
  guard looks turning a corner at 6.6 b/s are all **[NOT COVERED]**.
* **Ladders**, water, doors, gates, fence gates and generated terrain — the stand's terrain is built
  from `/fill`.
* **A raid.** Cavalry at speed against an actual raid event was not run; only husks and zombies at the
  Stable's doorstep.
* **A large colony.** Everything here was inside a 100×100 platform; whether path computation still
  keeps ahead over the several-hundred-block legs a mature colony produces is untested.
* **Very fast horses.** The stand pinned 0.181 and 0.250. The `0.18` cap only binds at attributes
  ≥ 0.30, so the capped regime itself is **[NOT COVERED]** by measurement — it is arithmetic.
