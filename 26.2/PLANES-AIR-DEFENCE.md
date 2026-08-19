# What a colony can do about an air strike

Companion to [`PLANES-INTEGRATION.md`](PLANES-INTEGRATION.md). Same evidence standard: every claim is
either **[VERIFIED]** (I read the source, the `file:line` is real) or **[UNCHECKED]** (inference).
Nothing here was run in game. Nothing here is implemented.

The question: a player types

```
/autopilot tool 800 259 16 true true
```

and right-clicks a colony. What can the colony do about it?

---

## 0. The strike path, traced

Read this first; the useful defences are the ones that hook something on this list.

| # | step | file:line |
|---|---|---|
| 1 | `/autopilot tool <distance> [bearing] [blast] [blocks] [fire]` writes five data components onto the held tool | `autopilot/AutopilotCommand.java:476-521` (`:496-499` are the four `stack.set` calls) |
| 2 | `/autopilot` root requires **op level 2** (`LEVEL_GAMEMASTERS`) | `autopilot/AutopilotCommand.java:79-80` |
| 3 | Right-click a block with the tool → reads distance/bearing/blast off the stack | `items/PlaneStrikeToolItem.java:96-122` (`:57-77` are the getters) |
| 4 | …and launches. **No permission check of any kind in this method** — not op level, not MineColonies permissions, not colony claims | `items/PlaneStrikeToolItem.java:122` |
| 5 | `AutopilotSpawner.launchStrike` spawns the aircraft `distance` out at `STRIKE_RUN_IN_AGL`, fits a booster, sets `STRIKE_MAX_SPEED = 3.0f`, launches at 2.0 blocks/tick, engages `FlightPlan.strike(target, blast)` | `autopilot/AutopilotSpawner.java:69-108` |
| 6 | `PlaneAutopilot.tickStrike` flies the run-in at 100 AGL with terrain following, enters a 32° dive when the target is that far below the nose, proximity-fuses on closest approach | `autopilot/PlaneAutopilot.java:932-1011` (dive geometry `:955-958`, fuse `:986-999`) |
| 7 | `plane.crash(16)` | `autopilot/PlaneAutopilot.java:996` |
| 8 | `crash` → `explode()` → **`level().explode(this, x, y, z, blast.power(), blast.fire(), blast.interaction())`** | `entities/PlaneEntity.java:1403-1412`, `:491-516` (the detonation is the single line `:515`) |
| 9 | which lands in `ServerLevel#explode(...)`, the 13-argument override — the one funnel every explosion in the game goes through | `/opt/mc-src/net/minecraft/server/level/ServerLevel.java:1207-1232` |

**[VERIFIED — all nine steps.]**

### 0.1 Two facts that change the whole answer

**(a) This is not an admin weapon. It is a survival weapon.**
`data/simpleplanes/recipe/plane_strike_tool.json` is a shaped recipe: **one TNT and two iron ingots.**
**[VERIFIED]** The tool is in the creative tab (`setup/SimplePlanesItems.java:106-107,137`) and craftable
by anyone.

And the op-only command is not needed to arm it. Sneak-right-clicking the tool cycles the spawn
distance through `{100, 200, 400, 800}` and, each time that wraps, steps the blast through
`{4.0, 8.0, 16.0, 1.0}` — `MAX_POWER` is 16 — while leaving `breaksBlocks` at its unset default of
**true** (`items/PlaneStrikeToolItem.java:49-51`, `:141-166`, and the default in `getBlast` at
`:67-71`). **[VERIFIED]**

> **A survival player with 1 TNT, 2 iron and eight right-clicks has a 16-power block-breaking strike
> at 800 blocks range.** The only thing `/autopilot tool` adds that the gesture cannot is
> `fire = true`. So "does this defence only stop the command?" is nearly the wrong question — the
> command is not the main vector. I have marked it per entry anyway, because it still separates
> *stopping the tool* from *stopping a plane a player flies in by hand*.

**(b) Shooting it down does not defuse it.**
A plane at zero health **keeps flying**. Both places that end a plane's life require it to be touching
the ground: `PlaneEntity.tick` — `if (getHealth() <= 0 && onGround() && !isRemoved()) crash(16);`
(`entities/PlaneEntity.java:681`) — and `hurtServer` — `else if (getOnGround() && getHealth() <= 0)`
(`:482`). So a strike aircraft shot to pieces at altitude glides on under autopilot, hits the ground,
and **detonates its 16-power warhead wherever it comes down.** **[VERIFIED]**

The fix is known and simple-planes documents it against itself: `GunshipSortie.despawn()` says
"`discard()`, not `kill()`: `kill()` runs `PlaneEntity#crash`, which explodes"
(`combat/GunshipSortie.java:404-406`). **[VERIFIED]** Any anti-air that wants to *prevent* the
explosion rather than relocate it must call `plane.discard()` on the killing blow, not merely damage
it to zero.

### 0.2 How much warning there is, in blocks and seconds

A boosted airframe settles at about **2.8 blocks/tick = 56 blocks/second** — `AutopilotSpawner.java:52-54`
derives it from the thrust fade ("about 2.8 at 3.0"), and `AutopilotConfig.java:55-64` reports
notch 10 measured at 2.78–2.83. Launch is at 2.0 and it accelerates, so use 2.6–2.8. **[VERIFIED as
the mod's own numbers; [UNCHECKED] as measured behaviour.]**

| distance ordered | flight time | what sees it, and when |
|---|---|---|
| 800 | **~14–15 s** | `AutopilotRegistry.active()` sees it **at t=0, at 800 blocks** — the registry has no range limit at all |
| 400 (tool default, `AutopilotConfig.java:979`) | ~7–8 s | same |
| 200 | ~3.5–4 s | same |
| 100 | ~2 s | same |

Against that, anything that waits for the aircraft to be *near* the colony:

| detector | radius | warning |
|---|---|---|
| colony claim at `maxColonySize = 20` chunks (`ServerConfiguration.java:367`) | 320 blocks | **5.7 s** |
| a young colony at `initialColonySize = 4` chunks (`:370`) | 64 blocks | **1.1 s** |
| the terminal dive itself: 100 AGL ÷ tan(32°) = **160 blocks** (`AutopilotConfig.java:991,1006`) | 160 blocks | **2.9 s** |

**Conclusion, and it decides entries 4 and 7 below: detect from the registry, not from the border.**
A border detector answers a strictly smaller question — at best 5.7 seconds against 14, and against
an 800-block order it spends 480 blocks of the flight blind. Everything that triggers on proximity is
marked *"inbound-only"* in the table at the end.

---

## The ten, cheapest to build first

### 1. Delete the recipe — 1 JSON file, 0 lines of Java

**What the player does.** Nothing; the server owner drops in a datapack that overrides
`data/simpleplanes/recipe/plane_strike_tool.json` with an unobtainable pattern (or removes it — a
recipe file whose result is absent simply fails to load). The tool stops being craftable.

**Where.** A datapack. Neither repo.

**Warning bought.** None — this is prevention, not warning.

**Does not protect against.** An op with `/give`. An op with `/autopilot strike`, which does not need
the item at all (`AutopilotCommand.java:426-463`). A player flying a plane in by hand and crashing it
— an ordinary plane crash still calls `explode()` with `Blast.DEFAULT`, which is TNT-strength and
breaks blocks (`entities/PlaneEntity.java:507`, `autopilot/Blast.java:44`). It removes the *cheap
mass-produced* vector and nothing else.

**Command only, or hand-flown too?** Neither, strictly: it stops **the item**. The command is
untouched.

**Cost.** 1 JSON. **[VERIFIED that the recipe file exists and what it contains.]**

---

### 2. Restore `turnoffexplosionsincolonies` — ~90 lines, 3 files, MineColonies

**This is the cheapest real defence, and it is not a new feature — it is a feature this port lost.**

**What the player sees.** Explosions inside the colony's claimed chunks stop rearranging the world.
Creepers, TNT, a strike plane, all of it. Which of the four behaviours applies is already a config the
player can set.

**Why it is nearly free.** Everything except the hook already exists in this repo and is already
wired:

- `api/colony/permissions/Explosions.java` — the enum
  `{DAMAGE_NOTHING, DAMAGE_PLAYERS, DAMAGE_ENTITIES, DAMAGE_EVERYTHING}`. **[VERIFIED]**
- `api/configuration/ServerConfiguration.java:271` declares `turnOffExplosionsInColonies`, and
  `:402` defines it with default `Explosions.DAMAGE_ENTITIES`. **[VERIFIED]**
- `api/colony/permissions/Action.java:35` — `EXPLODE(22)` is a real permission action. **[VERIFIED]**
- And `core/colony/permissions/ColonyPermissionEventHandler.java:80-81` says, in the port's own
  words, that the protections lost on Fabric include
  *"`ExplosionEvent.Start` / `.Detonate` — EXPLODE, and the `turnOffExplosionsInColonies` config
  entirely"*. **[VERIFIED]**

So the config is shipped, documented, settable — and does nothing. Restoring it is one hook.

**The hook.** `ServerLevel#explode(...)`, the 13-argument override at
`/opt/mc-src/net/minecraft/server/level/ServerLevel.java:1207`. Every explosion funnels through it —
`Level`'s four convenience overloads (`Level.java:581,601,627,653`) all delegate down to it, and
`PlaneEntity.explode()` calls one of them at `entities/PlaneEntity.java:515`. **[VERIFIED]**

**It needs a mixin, and this repo currently has none.** There is no mixin package under
`26.2/src/main/java`, no `mixins.json` in resources, and no `mixins` key in `fabric.mod.json`.
**[VERIFIED]** And Fabric API cannot help: I unzipped every jar under
`~/.gradle/caches/.../net.fabricmc.fabric-api` (0.154.2+26.2) and **no class in any of them has
"explos" in its name** — there is no explosion callback to register. **[VERIFIED by enumeration.]**

So: 1 mixin class (~50 lines: `@Inject(method = "explode", at = @At("HEAD"), cancellable = true)`,
resolve the colony from the chunk with `ColonyUtils.getOwningColony(...)` exactly as
`RaidManager.java:756-760` does, read the config, cancel or fall through), 1 `minecolonies.mixins.json`,
1 line in `fabric.mod.json`. Plus wiring the four enum cases.

**Warning bought.** None. It is armour, not radar.

**Does not protect against.** Entity damage, under the default `DAMAGE_ENTITIES` — citizens still die,
the crater just does not appear. Anything outside claimed chunks. Nothing stops the aircraft.

**Command only, or hand-flown too?** **Both, and everything else besides.** It does not know or care
what caused the explosion. This is the entry with the widest coverage per line in the whole list.

**Cost.** ~90 lines, 3 files, MineColonies. **[cost [UNCHECKED]; every fact it rests on [VERIFIED].]**

---

### 3. Blast downgrade inside the claim — ~20 lines on top of #2

**What the player sees.** The plane still arrives, still detonates, still kills livestock and hurts
citizens — but inside the colony border the warhead is clamped: `breaksBlocks → false`,
`fire → false`, power clamped to something like 4. The drama survives; the quarter-hour of repairs
does not.

**Where.** The same mixin as #2, one branch further: instead of cancelling, rewrite the arguments.
Because `interactionType` and `fire` are separate parameters of `ServerLevel#explode`
(`ServerLevel.java:1215-1216`) and `NONE` maps to `Explosion.BlockInteraction.KEEP` (`:1223`), the
downgrade is a two-argument substitution — no need to re-implement the explosion. **[VERIFIED]**

A fifth enum constant (`DAMAGE_ENTITIES_NO_TERRAIN`, say) rather than a new config, so the existing
key keeps working.

**Warning bought.** None.

**Does not protect against.** The same list as #2. Citizens still die.

**Command only, or hand-flown too?** Both, and everything else.

**Cost.** ~20 lines on top of #2. Do them together.

---

### 4. Air-raid warning, extended to strikes — ~25 lines on top of the Part 2 feature

**What the player sees.** The moment the strike is ordered — not when it arrives — the town hall
sounds the raid warning and a message names the bearing it is coming from and roughly how long there
is. Fourteen seconds is enough to run.

**Where the warning comes from, and why it is the registry.** `AutopilotRegistry.active()`
(`autopilot/AutopilotRegistry.java:82-85`) returns every aircraft currently under autopilot, pruned,
"cheaper and more precise than scanning the level for entities". Each carries its plan:
`PlaneEntity.getAutopilot()` (`entities/PlaneEntity.java:153`) → `PlaneAutopilot.getPlan()`
(`autopilot/PlaneAutopilot.java:288`) → `FlightPlan.kind()` and `FlightPlan.strikeTarget()`
(`autopilot/FlightPlan.java:199,219`). **[VERIFIED]**

So the test is: *is there an aircraft whose plan is `Kind.STRIKE` and whose `strikeTarget()` lies in
my claimed chunks?* That is true **at t=0, 800 blocks out**, before the aircraft has moved a block. No
scan, no proximity, no border.

This is the same file as the air-raid warning being built in Part 2 (item 7 of the integration
report); the strike case is an extra branch in it, not a new feature.

**Warning bought.** **The entire flight — ~14–15 s at 800 blocks, ~7–8 s at 400.** The best in the
list, by a factor of three over anything border-based.

**Does not protect against.** Anything. It is a siren. Also: it sees only *autopilot* aircraft — a
hostile player flying a bomber in by hand is not in the registry and needs the AABB scan described in
#7.

**Command only, or hand-flown too?** **Command and tool only.** Hand-flown needs #7's scan.

**Cost.** ~25 lines, MineColonies, in the file Part 2 is already creating.

---

### 5. MineColonies refuses to aim at colonies — ~30 lines

**What the player sees.** A config, `colonyairstrikes`, default off. The MineColonies-side features
that launch strikes (the hostile-colony air raid, §6 of the integration report) will not target a
claimed colony when it is off, and the paradrop will not drop inside one that has it disabled.

**Where.** MineColonies, in the compat bridge. It is a guard on *our* launches.

**Warning bought.** N/A.

**Does not protect against.** **The player's tool or the `/autopilot` command — at all.** This is
house rules for MineColonies' own aggression, nothing more. It is on the list because the owner asked
for the cheap end and because a feature that can bomb colonies should ship with an off switch, not
because it defends anything.

**Command only, or hand-flown too?** Neither.

**Cost.** ~30 lines, MineColonies.

---

### 6. Alert behaviour: guards converge, citizens shelter — ~70 lines

**What the player sees.** On the warning from #4, every guard within range of the predicted impact
point is sent there, and citizens are pushed indoors for the duration.

**Where.** `AbstractBuildingGuards.setTempNextPatrolPoint(BlockPos)`
(`core/colony/buildings/AbstractBuildingGuards.java:498`) — already used exactly this way by
`RaidManager.getRandomBuilding()` at `:1036-1039` to send the three nearest guards to a threatened
building, and by `CombatUtils.notifyGuardsOfTarget` at
`core/entity/ai/combat/CombatUtils.java:127-132`. Reusable verbatim, with the strike's
`FlightPlan.strikeTarget()` as the point. **[VERIFIED]**

**Warning bought.** Whatever #4 bought; this consumes it.

**Does not protect against.** The bomb. Sending guards *to* the impact point arguably kills more of
them — the sheltering half is the useful half, and "stand somewhere else" is the correct behaviour,
which makes this partly a redesign of the alert rather than a straight reuse.

**Command only, or hand-flown too?** Inherits #4 / #7.

**Cost.** ~70 lines, MineColonies.

---

### 7. **Anti-air battery — the shoot-down mechanism** — ~350 lines, 4 files, MineColonies

The required entry. It is seventh because it genuinely costs that much, not because it is unwanted.

#### 7.1 What is already true, and it is more than my last report said

My previous report said guards cannot engage a plane because `PlaneEntity` is not a `LivingEntity`.
That is true about *vanilla mob targeting* and I over-generalised from it. Checked properly:

| question | answer | evidence |
|---|---|---|
| Is a plane destructible? | Yes. Own health field, `MAX_HEALTH` default **10** | `entities/PlaneEntity.java:73-74`, `:201`, `setHealth :275`, `getHealth :279` |
| Is there a damage entry point? | Yes, `hurtServer(ServerLevel, DamageSource, float)` | `entities/PlaneEntity.java:445` |
| **Can an ordinary arrow hit one?** | **Yes.** `Entity.canBeHitByProjectile() { return isAlive() && isPickable(); }` and `PlaneEntity.isPickable()` returns `true` | `/opt/mc-src/.../Entity.java:2006-2008`, `entities/PlaneEntity.java:526-528` |
| Does the projectile's own filter allow it? | Yes. `Projectile.canHitEntity` only refuses `!canBeHitByProjectile()` and same-vehicle passengers | `/opt/mc-src/.../projectile/Projectile.java:318-325` |
| Is a strike plane armoured? | **No.** The autopilot fits a *booster*, never an `ArmorUpgrade` | `autopilot/AutopilotSpawner.java:120-124` |
| How fast can it be hurt? | Once per **10 ticks** — `damageTimeout = 10` on every hit, and `damageTimeout > 0` refuses | `entities/PlaneEntity.java:477`, `:466` |

**So the kill is already possible today: 10 hit points, no armour, hittable by any arrow, at two
hits per second maximum.** A guard arrow's damage (`RangeCombatAI.calculateDamage:271-279`, agility/5
plus enchants plus the `ARCHER_DAMAGE` research) is comfortably 2–5, so **two to five hits, i.e. one
to two and a half seconds of sustained fire from a single shooter.** **[VERIFIED for every component;
[UNCHECKED] as an end-to-end outcome.]**

**What is missing is not the kill. It is the aim and the reach.**

#### 7.2 Why ordinary archers can never be the answer

`RangeCombatAI.getAttackDistance()` (`:230-257`) starts from
`BASE_DISTANCE_FOR_RANGED_ATTACK = 10`, caps at `MAX_DISTANCE_FOR_RANGED_ATTACK = 24`
(`api/util/constant/GuardConstants.java:64,74`) — and then adds `user.getY() - target.getY()`
(`:248`), which for a target **above** the guard is **negative** and makes the envelope *smaller*.
**[VERIFIED]**

The strike run-in is flown at `STRIKE_RUN_IN_AGL = 100` blocks above ground
(`autopilot/AutopilotConfig.java:991`). **A guard's ranged envelope is 24 blocks and shrinks with
altitude. It is not close.** No amount of work on target goals turns an archer into anti-air; the
range budget is wrong by a factor of four before the sign error on altitude.

So anti-air must be **its own thing with its own range**, not a patch to `RangeCombatAI`.

#### 7.3 The design

**A colony anti-air position**: a marked block (reuse a decoration block, or the guard tower's own
position at building level ≥ 4) that runs a per-tick server-side battery. Not a mob AI at all — the
same architecture `GunshipRegistry` uses for the gunship, and for the same reason.

**Targeting source — free, and the reason this is affordable.**
`AutopilotRegistry.active()` (`autopilot/AutopilotRegistry.java:82-85`) gives the live list of
autopilot aircraft with no world scan. For hand-flown aircraft, which are *not* in that registry, add
a bounded `level.getEntitiesOfClass(PlaneEntity.class, aabb)` around the battery every 20 ticks. Both
paths converge on the same list of `PlaneEntity`.

**Ballistics — reusable, and reusable in the right direction.**
`combat/Ballistics.solve(Vec3 muzzle, Vec3 aim, double speed)` (`combat/Ballistics.java:92`) is a
closed-form solve of the arrow recurrence for the *flat* root. It is **completely direction-agnostic**:
`dy` is just `aim.y - muzzle.y` (`:95`) and every downstream expression is signed
(`residual :76-84`, `solutionAt :121-129`). A target 100 blocks *up* is the same algebra as one 20
blocks down. **[VERIFIED]** Its own note says horizontal reach saturates at `100 * launchSpeed`
(`:41-43`), so at a bow's 1.4–4 blocks/tick that is 140–400 blocks of theoretical reach — the physics
is not the constraint, the AI's 24-block cap was.

Lead is the other half, and `GunshipSortie` already contains the pattern to copy conceptually: a
ring buffer of the last N positions, velocity averaged over the window, and a three-pass fixed point
because flight time and lead feed each other (`combat/GunshipSortie.java:584-619`, `:643-659`). A
plane at 2.8 blocks/tick over a 20-tick flight is **56 blocks of lead** — this is not optional
polish, an unled shot misses by the length of the runway.

**Firing — MineColonies' own arrow path, one small addition.**
`CombatUtils.createArrowForShooter(LivingEntity)` (`core/entity/ai/combat/CombatUtils.java:48`) is
reusable as-is. `CombatUtils.shootArrow(arrow, LivingEntity target, hitChance)` (`:79-108`) is not —
it is typed on `LivingEntity`. But it is fifteen lines and it only ever reads `target.getX()`,
`target.getBoundingBox().minY + getBbHeight()/2`, `target.getZ()`, and `target.level()`/`playSound`.
**A `Vec3`-and-velocity overload is about 15 new lines in the same file** and would take the solved
velocity straight from `Ballistics` instead of the `AIM_SLIGHTLY_HIGHER_MULTIPLIER = 0.18` heuristic
at `:104`, which is a fudge for walking mobs and will not put an arrow on a 56-blocks-per-second
target. **[VERIFIED]**

**The kill — and this is the part that must not be got wrong.**
Per §0.1(b), damaging a plane to zero health at altitude does **not** bring it down; it flies on and
detonates on impact. So the battery must, on the shot that takes health to zero,
`plane.discard()` — the trick simple-planes documents at `combat/GunshipSortie.java:404-406`. Then the
aircraft is gone, silently, with no explosion, and the payload with it.

**Licence note.** `Ballistics` is LGPL code in the simple-planes repo. MineColonies **calls** it; it
does not copy it. That is ordinary linking and it is what §0.1 of the integration report says is
allowed in this direction. The whole battery lives in the excludable
`core/compatibility/simpleplanes` package, so with Simple Planes absent there are no planes to shoot
and no class to load.

#### 7.4 Counterplay — how hard it should be

| | proposal |
|---|---|
| engagement range | 120–150 blocks — enough to reach the 100-block run-in and to matter before the 160-block dive entry |
| rate | one shot per ~1 s per emplacement, i.e. roughly the plane's own 10-tick damage timeout |
| shots to kill | 10 HP ÷ ~3 damage ≈ **4 hits**, so ~4 s of tracking from one emplacement, ~2 s from two |
| against an **800-block** order | ~14 s of flight, of which the last ~2.5 s is inside 150 blocks — **one emplacement is not enough; two or three are.** That is the intended difficulty |
| what happens to the aircraft | `discard()` — vanishes. No crater, no wreck, no drop |
| what happens to the crew | Nothing to happen to. Strike aircraft are unmanned (`launchStrike` passes the player only as an owner for messages, `AutopilotSpawner.java:69-107`). A hand-flown bomber's pilot is a passenger and `discard()` on the vehicle dismounts them **at altitude** — they fall. That is a real design decision, not an accident: state it, and consider `kill()`-with-neutered-blast instead if that reads as too harsh. **[UNCHECKED — I did not verify vanilla dismount-on-discard behaviour.]** |
| a bomb already released | Nothing recovers it. A `PayloadUpgrade` or `SupplyCrateUpgrade` released before the aircraft died is an independent entity (`upgrades/payload/PayloadUpgrade.java:87-98`). Shooting the aircraft down after the drop is too late by design |

#### 7.5 Cost

| piece | lines |
|---|---|
| `AntiAirBattery` — track list, lead filter, `Ballistics.solve`, fire, `discard()` on kill | ~180 |
| `CombatUtils.shootArrowAt(arrow, Vec3 aim, Vec3 velocity)` overload | ~20 |
| registry + server tick pump, on the `GunshipRegistry` pattern | ~60 |
| emplacement definition, config, ammunition draw from the building | ~90 |

**≈ 350 lines, 4 files, all MineColonies** (3 new in the excludable compat package, 1 edited in
`core/entity/ai/combat/`). **[cost [UNCHECKED]; every API fact it rests on [VERIFIED].]**

#### 7.6 Risks

- **Ammunition.** If it consumes arrows from the guard tower, someone must keep it stocked; if it
  does not, it is a free turret. I would make it consume, and make running dry the actual failure
  mode.
- **A turret that shoots at every plane** will shoot the player's own courier from §8 of the
  integration report. It needs an owner test — cheapest is a set of entity ids the colony launched
  itself, same as the air-raid warning needs.
- **Lead against a diving target.** The lead filter in `GunshipSortie` deliberately uses only
  horizontal velocity (`:641-642`) because mob vertical motion is gravity jitter. A plane in a 32°
  dive has real vertical velocity, so the anti-air version **must** lead in three axes — that is the
  one place the gunship's code cannot be followed. **[VERIFIED that the gunship zeroes `dy`;
  [UNCHECKED] what a 3-axis lead does to hit rate.]**
- **I have not verified end to end that an arrow fired at a plane connects.** Every link in the chain
  is verified individually; the chain itself is not.

#### 7.7 Does this help against raiders?

**Against ordinary ground raiders: no.** They walk. Aircraft are irrelevant and this is the wrong
mechanism entirely — that is what guards and walls are.

**Against the §1 paradrop: yes, decisively, and it is the same mechanism.** The transport flies a
route at cruise altitude to a drop point over the colony. An anti-air battery that brings it down
*before* the drop means **the wave never arrives**. That is the best piece of counterplay in this
whole document: a raid you can prevent rather than survive, using a thing you built, against a threat
you were warned about ten seconds earlier.

**One gotcha, and it is a real one.** `HordeRaidEvent` ends when `horde.hordeSize <= 0`
(`core/colony/events/raid/HordeRaidEvent.java:434-438`) or when the live count falls below 10% of
`initialSize` (`:463-467`). Both count *spawned* raiders. If the transport is destroyed and nobody
ever spawns, `hordeSize` sits at its initial value and **the raid never ends** — `isRaided()` stays
true for ever, the raid bar never clears, and `nightsSinceLastRaid` is pinned at 0 by
`onUpdate` (`:432`). **[VERIFIED from the code paths.]** The air-drop event must therefore treat
"transport destroyed with raiders still aboard" explicitly: either set `EventStatus.DONE` (the wave
was destroyed — the player won), or spawn the survivors on the ground where it fell. I would take the
first; the player earned it. **This must be handled in the paradrop implementation whether or not
anti-air is ever built**, because a transport can also be destroyed by terrain.

---

### 8. Research-gated anti-air — ~15 lines of Java + JSON, but only on top of #7

**What the player sees.** Anti-air emplacements do not work until the colony completes an "Air
Defence" research on the combat branch; a second tier improves range or rate.

**Where.** Research is entirely datapack (`data/<ns>/researches/…`, schema in
`src/main/generated/data/minecolonies/researches/combat/ironarmor.json`), and reading an effect at
runtime is one call — `RangeCombatAI.java:186,198,278` does exactly this three times:
`colony.getResearchManager().getResearchEffects().getEffectStrength(ARCHER_DAMAGE)`. **[VERIFIED]**

**Cost.** ~15 lines plus a few JSON files — **but it is meaningless without #7**, so its real cost is
#7's. Listed separately because it is how #7 should be paced, not because it is a defence on its own.

**Does not protect against.** Anything, alone.

---

### 9. Radar hut — a new building, 800–1500 lines plus blueprints

**What the player sees.** A hut with an assigned citizen. While staffed and powered, it extends the
warning from #4: it names the aircraft type, the bearing, the estimated impact point and a countdown,
and it marks the impact point on the map. At higher levels it warns earlier and cues the anti-air
emplacements automatically instead of requiring the player to be online.

**Why it is expensive and not a defence in itself.** The *detection* is free — #4 already sees
everything at t=0 from the registry, and no building can improve on "the instant it is ordered". What
a radar hut adds is **structure**: a reason the warning exists, a thing to build, a thing to lose. The
cost is the standard MineColonies building cost: hut block, building class, modules, view, GUI, job,
job AI, skill mapping, five levels of blueprint in every style pack, datagen, loot tables,
translations. Extrapolated from the shape of the existing building packages, not measured.
**[UNCHECKED]**

**Does not protect against.** Anything, directly.

**Recommendation.** Build #4 first and see whether a bare siren is enough. If it is, the hut is
content, not necessity — and it should then be designed as the thing that *gates* #7, which is a much
better job for it than detection.

---

### 10. Colony interceptor — expensive, and I do not think it works

**What the player sees.** The colony launches its own aircraft at the inbound one.

**Why it is last.** The pieces exist — `GunshipRegistry`, `AutopilotSpawner.launchStrike` — but the
geometry does not. `GunshipSortie` **hovers**; its own comment says it "does not orbit and it does not
dive" and is "a guard post, not a hunter" (`combat/GunshipSortie.java:53-55`), and `HoverControl`
deliberately has no horizontal translation (`combat/HoverControl.java:23-26`). **[VERIFIED]** So an
interceptor is not a reuse, it is a new air-to-air flight director plus an air-to-air firing solution
— and it would have to complete inside the ~2.5 seconds the target spends within engagement range.
A ground battery (#7) solves the same problem with none of that.

The one version that *is* cheap: launch a gunship to **hover over the colony permanently** during an
alert and let its existing `Enemy`-based targeting handle anything that lands — which is §3 of the
integration report, already free, and not anti-air at all.

**Cost.** 600+ lines, MineColonies, high risk. **[UNCHECKED]**

---

## Summary

| # | defence | cost | repo | warning bought | stops the **tool/command** | stops a **hand-flown** plane |
|---|---|---|---|---|---|---|
| 1 | Delete the strike-tool recipe | 1 JSON | datapack | — | item only, not the command | no |
| 2 | Restore `turnoffexplosionsincolonies` (mixin) | ~90 L, 3 files | minecolonies | — | **yes** | **yes** (and creepers, and TNT) |
| 3 | Blast downgrade inside the claim | +~20 L | minecolonies | — | **yes** | **yes** |
| 4 | Air-raid warning extended to strikes | +~25 L | minecolonies | **~14 s @ 800** | yes | no (needs #7's scan) |
| 5 | MineColonies refuses to aim at colonies | ~30 L | minecolonies | — | no | no |
| 6 | Guards converge / citizens shelter | ~70 L | minecolonies | consumes #4's | inherits | inherits |
| 7 | **Anti-air battery** | **~350 L, 4 files** | minecolonies | inbound-only, ~2.5 s in range | **yes** | **yes** |
| 8 | Research gate for #7 | ~15 L + JSON | minecolonies | — | via #7 | via #7 |
| 9 | Radar hut | 800–1500 L + art | minecolonies | no better than #4 | no | no |
| 10 | Colony interceptor | 600+ L | minecolonies | — | doubtful | doubtful |

**If only two things are built: #2 and #4.** Ninety lines each, one restores a feature this port is
documented as having lost, the other buys fourteen seconds instead of three — and between them they
cover the command, the tool, a hand-flown bomber, and every creeper that has ever walked into a wall.
**#7 is the one the owner asked for and it is worth building, but it is fourth in line by value, not
first**, and it must be built knowing that a plane shot down at altitude still explodes unless the
code calls `discard()`.

## What I did not verify

- End to end: that an arrow fired by a MineColonies guard actually connects with a plane. Every link
  is verified separately; the chain is not.
- What `discard()` does to a pilot riding the plane.
- That the 2.8 blocks/tick figure is what a strike aircraft really flies; it is the mod's own
  documented number.
- Anything about hit rates, either the gunship's over a colony or a hypothetical battery's.
- Whether a 3-axis lead (needed against a diving target) behaves.
