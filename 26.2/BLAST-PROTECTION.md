# Colony blast protection — what was restored, and what was not

Follow-on to [`PLANES-AIR-DEFENCE.md`](PLANES-AIR-DEFENCE.md) §2 and §3. That document said the
cheapest real defence a colony can have is the `turnoffexplosionsincolonies` feature this port lost,
and that restoring it needs a mixin on `ServerLevel#explode`. **The feature is now back for aircraft
blasts, without a mixin.** The mixin question is still open and is costed below with real numbers so
it can be decided rather than guessed at.

Same evidence standard as the two design documents: **[VERIFIED]** means I read the source or ran
the command; **[UNCHECKED]** means it is an inference. Nothing here was tested in game.

---

## 1. What is protected, and what is not

### Protected

| blast | how it gets here |
|---|---|
| the craftable **strike tool** (1 TNT + 2 iron), at any of its `{4, 8, 16, 1}` power settings | `PlaneStrikeToolItem` → autopilot → `PlaneEntity#crash` |
| **`/autopilot strike`** and `/autopilot tool`, including `fire = true` | same |
| a **hostile player flying a bomber in by hand** and crashing it on the town hall | `PlaneCollisions` → `PlaneEntity#crash` |
| a **plane shot down** that glides on and hits the ground inside the claim | `PlaneEntity#tick` → `crash(16)` |
| an ordinary **plane crash** (`Blast.DEFAULT`, TNT strength, breaks blocks) | `PlaneCollisions#causeFallDamage` → `crash` |
| a **gunship** that runs out of sky | same |

All six converge on one method — `PlaneEntity#explode()` — and that is where the seam sits. There are
exactly four callers of `PlaneEntity#crash` in Simple Planes and one caller of `explode()`.
**[VERIFIED by grep over the whole mod.]**

### Not protected — say this out loud

**Every vanilla explosion.** A creeper, a TNT block, a bed in the Nether, a respawn anchor in the
Overworld, an end crystal, a wither skull, a ghast fireball, another mod's bomb — and **a payload
bomb dropped from a plane**, because `PayloadUpgrade#dropAsPayload` spawns an ordinary vanilla entity
(primed TNT or whatever the datapack names) which then explodes on its own account and never touches
Simple Planes' blast code again (`upgrades/payload/PayloadUpgrade.java:87-98`). **[VERIFIED]**

Those all go through `ServerLevel#explode` without passing anything of ours. Reaching them
generically needs a mixin; this port has none, and Fabric API ships no explosion callback to use
instead — an unpacked `fabric-api 0.154.2+26.2` and all 43 of its nested jars contain **no class with
"explos" in its name at all**. **[VERIFIED by the previous agent's enumeration; I did not repeat the
unpack, so [UNCHECKED] at second hand.]**

So: **an air strike on a colony now rearranges nothing. A creeper still does.** That is the honest
shape of this change, and it is worth knowing that the creeper was never the thing the owner asked
about.

### One thing that was already true

**Hut blocks are already blast-proof and always were.** `AbstractColonyBlock.RESISTANCE =
Float.POSITIVE_INFINITY` (`api/blocks/AbstractColonyBlock.java:76`), applied at `:107`, and all 55
blocks in `core/blocks/huts/` extend `AbstractColonyBlock` — 53 of them through `AbstractBlockHut`,
plus `BlockPostBox` and `BlockStash` directly. Racks are the same
(`core/blocks/BlockMinecoloniesRack.java:82`). **[VERIFIED]** See §6 for the rest of that question.

---

## 2. How it works

Three pieces, in the order a blast meets them.

### 2.1 The seam, in Simple Planes — `xyz.przemyk.simpleplanes.api`

Two new files in a new `xyz.przemyk.simpleplanes.api` package and a nine-line change to
`PlaneEntity#explode`:

- **`BlastGuard`** — a functional interface. Given `(ServerLevel, Entity, Vec3, Blast)` it returns the
  `Blast` to apply: the argument unchanged to abstain, a weaker one to downgrade, or `null` to
  suppress the explosion entirely.
- **`BlastGuards`** — the registry. Guards are chained, each handed what the previous one returned, so
  two mods' downgrades compose and neither has to know about the other. A guard that throws is logged
  and skipped — a broken third-party guard must not turn a plane crash into a server crash. With no
  guards registered the cost is one `isEmpty()` test on a static field and the behaviour is bit for
  bit what it was.

**It names no mod but Simple Planes, and it must stay that way.** This is a licence constraint, not a
style preference. MineColonies here is GPL-3.0-only; Simple Planes is LGPL-3.0-or-later. LGPLv3 is
GPLv3 plus additional permissions, and GPLv3 §7 lets a downstream conveyor strip those permissions —
so a GPL-3.0-only work may link against the LGPL one and be conveyed as GPLv3. The reverse does not
hold: GPL-3.0-only code cannot be conveyed under LGPL-3.0-or-later, so nothing of MineColonies may be
copied into that jar without making it undistributable under its own licence. **I agree with the
reading in `PLANES-INTEGRATION.md` §0.1 and built on it.** [VERIFIED as a reading of the two licence
files; I am not a lawyer.]

There is no `init()` and nothing to add to `SimplePlanesMod`. The list is a plain static field, so a
foreign mod may register from its own initialiser whether that runs before or after Simple Planes'.
Fabric gives no ordering guarantee between two mods' initialisers, and a hook another mod can miss by
being early is a hook that fails silently on somebody's machine.

### 2.2 The registration, in MineColonies — `SimplePlanesBlastGuard`

`core/compatibility/simpleplanes/SimplePlanesBlastGuard.java`, called once from
`MineColonies#onInitialize` next to the other callbacks. `FabricLoader.isModLoaded("simpleplanes")`
first; without the mod it returns immediately, which is the same no-op the rest of the compat layer
gives.

**The binding is reflective, and that needs justifying.** Simple Planes is not on this project's
compile classpath: it has no Maven coordinate, it is not staged into `.staged-libs` the way BlockUI,
Domum and Structurize are, and the only jar of it that exists locally
(`/workspace/unknown-wq/simple-planes/dist/simpleplanes-26.2-5.3.7.jar`) predates the API and so
does not contain `BlastGuard`. **[VERIFIED — there is no `simpleplanes` string anywhere in
`26.2/build.gradle` or `26.2/gradle.properties`.]** A `compileOnly` dependency on that jar would
therefore turn the build red, and parking the file in `optional-integrations.txt` the way JEI and
JourneyMap are parked would mean the feature never runs until somebody edits a build file. Reflection
buys a class that compiles today, ships today, and starts working the moment a Simple Planes jar
carrying the API is present.

The cost is real and worth stating: **nothing type-checks this binding.** If the API is
renamed upstream, the failure is a log line at start-up —
*"Simple Planes is present but has no blast guard API"* — and silently no protection. The four names
it depends on are all in one block of constants at the top of the file.

If the owner would rather have it type-checked, the swap is: publish or stage a Simple
Planes jar carrying the API, add it as `compileOnly` in `26.2/build.gradle`, and replace the `Proxy` with
`BlastGuards.register(SimplePlanesBlastGuard::guard)`. Roughly 40 lines shorter, and the build then
depends on that jar existing.

### 2.3 The policy — the config and the enum that were already there

No new config key. The decision uses exactly what the port already shipped:

| what | where | state |
|---|---|---|
| `Explosions {DAMAGE_NOTHING, DAMAGE_PLAYERS, DAMAGE_ENTITIES, DAMAGE_EVERYTHING}` | `api/colony/permissions/Explosions.java` | unchanged |
| `turnoffexplosionsincolonies`, default `DAMAGE_ENTITIES` | `api/configuration/ServerConfiguration.java:271` and `:402` | unchanged, now read |
| `enablecolonyprotection` master switch | `ServerConfiguration.java:270`, `:401` | honoured first, as everywhere else |
| `Action.EXPLODE(22)` | `api/colony/permissions/Action.java:35` | unchanged, now read |

Line numbers re-checked in this worktree at `87321c0d`. **[VERIFIED — all four are exactly where
`PLANES-AIR-DEFENCE.md` said, no drift.]**

The decision, in order:

1. `enablecolonyprotection` off → let it through.
2. `turnoffexplosionsincolonies == DAMAGE_EVERYTHING` → let it through.
3. Not in a claimed chunk (`IColonyManager#getIColony`) → let it through. Outside a colony this mod
   has no business touching anybody's explosion.
4. That colony has blast protection switched off (§2.4) → let it through.
5. The pilot has `Action.EXPLODE` in that colony → let it through. Officers demolishing their own
   build with a plane is a legitimate thing to do and the permission exists to say so. The pilot is
   read off vanilla's `Entity#getControllingPassenger`, so no Simple Planes type is named. **A strike
   aircraft is unmanned and never takes this exit** — this branch only ever fires for a hand-flown
   plane.
6. `DAMAGE_NOTHING` → suppress the blast entirely.
7. `DAMAGE_ENTITIES` / `DAMAGE_PLAYERS` → keep the power, force `breaksBlocks = false` and
   `fire = false`. The bang happens, whatever is standing in it still gets hurt, the world is left
   exactly as it was.

**Where the old semantics do not fit, exactly one place.** `DAMAGE_PLAYERS` means "only players take
damage", and this seam cannot express it: it decides what the explosion *is* before vanilla casts a
single ray, and it has no say over which entities the rays then hit. So `DAMAGE_PLAYERS` is honoured
exactly on its block half and behaves as `DAMAGE_ENTITIES` on its entity half — citizens and
livestock in the blast still get hurt. That is one of the two things a mixin would buy outright (§4).
No new enum constant was added; `PLANES-AIR-DEFENCE.md` §3 proposed a fifth
(`DAMAGE_ENTITIES_NO_TERRAIN`) for the downgrade, and it turned out not to be needed — the existing
`DAMAGE_ENTITIES` already means precisely that.

### 2.4 The off switch — `/mc colony blastprotection`

The config is global: it says *how much* every colony on the server is shielded, and says it once.
A single colony that would rather be blown up — a test colony, a PvP colony, one whose owner prefers
craters — had nowhere to say so. So:

```
/mc colony blastprotection <colony>          # report, do not change
/mc colony blastprotection <colony> on
/mc colony blastprotection <colony> off
```

OP-gated (`IMCOPCommand`), colony resolved with `ColonyIdArgument`, on/off as literals, in the
`colony` subtree of `EntryPoint` — copied verbatim from `CommandColonyFreeMode`, which is the closest
existing thing. The flag is a boolean on `Colony`, default `true`, saved under
`NbtTagConstants.TAG_BLAST_PROTECTION` and read back defaulting to `true`, so every colony saved
before this existed comes back protected.

The bare report form also tells the player what the protection covers, because the honest answer is
narrower than the name suggests:

> Blast protection is true for colony Springfield, and the server policy is DAMAGE_ENTITIES. It
> covers blasts from aircraft only - creepers, TNT and other vanilla explosions are not covered.

Two lang keys, both present in `manual_en_us.json`:
`com.minecolonies.command.colony.blastprotection.success` and `.state`. `%s` throughout, never `%d`.

**A general `/mc config` command was considered and deliberately not built**, per the brief.

---

## 3. Getting the Simple Planes side

**There is nothing to apply.** The seam lives in the Simple Planes repository, on its own branch, and
is maintained there. This was once a patch file in this repository, carried here only because that
repository was not writable at the time; it has since been applied upstream and the patch file is
gone. Do not go looking for it.

All that is needed on a server is a Simple Planes build that carries
`xyz.przemyk.simpleplanes.api.BlastGuards`. With an older one, MineColonies logs a single line at
start-up naming that class and does nothing else — no error, no degraded behaviour, just no aircraft
blast protection.

Two switches exist, and they are independent:

| switch | lives in | decides |
|---|---|---|
| `/mc colony blastprotection <colony> [on\|off]` | this repository, per colony, saved in the colony | what this guard *answers* when it is consulted |
| `/blastguard [status\|on\|off]` | Simple Planes, per server, saved in the world | whether *any* guard is consulted at all |

Turning the aircraft mod's own switch off stops it asking anybody, so an explosion is exactly the one
the aircraft ordered. Neither switch needs to know the other's position: a guard that is never
consulted simply never runs.

---

## 4. What one mixin would cost and buy

Numbers, so this can be decided later rather than hand-waved.

### The wrong injection point, and why the old report picked it

`PLANES-AIR-DEFENCE.md` §2 proposed `ServerLevel#explode`. That method is
**thirteen parameters wide** (`/opt/mc-src/net/minecraft/server/level/ServerLevel.java:1207-1221`),
and four of those thirteen are pure presentation — two `ParticleOptions`, a
`WeightedList<ExplosionParticleInfo>` and a `Holder<SoundEvent>`. **[VERIFIED]** A mixin binds by
descriptor, so any change to any one of those thirteen makes the mixin fail to apply, and a mixin
that fails to apply is a **hard crash at launch**, not a silent degrade. Cosmetic parameters are
exactly the kind that churn between versions. **[the churn claim is [UNCHECKED] — I did not diff
version history.]**

### The right one

`ServerExplosion#explode()` — **zero parameters, returns `int`**
(`/opt/mc-src/net/minecraft/world/level/ServerExplosion.java:234`). Everything a colony check needs
is already public on it: `level()` (`:272`), `center()` (`:116`), `radius()` (`:111`), and the source
entity through the `Explosion` interface. **[VERIFIED]**

That makes the whole mixin roughly:

```java
@Inject(method = "explode", at = @At("HEAD"), cancellable = true)
private void minecolonies$colonyBlastGuard(CallbackInfoReturnable<Integer> cir) { ... cir.setReturnValue(0); }
```

There is a second, even narrower point: `interactsWithBlocks()` — private, zero args, returns
`boolean` (`:263`), and it is the single gate on both the block removal and the fire
(`:238`, `:245`, and `isSmall()` at `:312`). A mixin that forces it to `false` inside a claim is
**exactly** the `DAMAGE_ENTITIES` behaviour, in one line. **[VERIFIED]**

### The cost

| | |
|---|---|
| new files | 1 mixin class (~50 lines), 1 `minecolonies.mixins.json` |
| edited files | 1 line in `fabric.mod.json` (`"mixins": [...]`) |
| new gradle dependencies | **none.** Sponge Mixin arrives with fabric-loader, which is already an `implementation` dependency, and Loom wires the annotation processor. **[UNCHECKED — not tried in this build.]** |
| what is given up | the property that this port has zero mixins. Minecraft 26.1+ is unobfuscated, so there are no mappings to break; what remains is signature drift on one zero-argument method, which is about as small as that risk gets |
| ongoing | one method signature to re-check per Minecraft update |

### The buy

- **Every vanilla explosion**, which is the entire §1 "not protected" list: creepers, TNT, beds,
  respawn anchors, end crystals, wither skulls, ghast fireballs, plane payload bombs, and other mods'
  explosions.
- **`DAMAGE_PLAYERS` becomes exact.** `ServerExplosion.hurtEntities()` (`:171`) is where entity damage
  is dealt, and `ServerLevel#explode` takes an `ExplosionDamageCalculator` as a parameter; either is a
  handle on *which* entities get hurt, which the current seam has no way to reach.
- **The Simple Planes seam becomes optional**, since plane blasts also go through
  `ServerExplosion`. It would still be worth keeping — a guard that can weaken a warhead before the
  explosion object is even built is cheaper than one that cancels it afterwards, and it is the only
  one of the two that another mod can use.

### My recommendation

**Build the mixin when a creeper actually ruins something.** The strike tool is one TNT and two iron
and reaches 800 blocks; a creeper walks. The air threat is closed now, and it was the asymmetric one.
If and when it is built, target `ServerExplosion`, not `ServerLevel` — that choice is worth more than
the decision to have a mixin at all.

---

## 5. Files touched

MineColonies (this repo, all in `26.2/`):

| file | change |
|---|---|
| `BLAST-PROTECTION.md` | new — this document |
| `src/main/java/.../core/compatibility/simpleplanes/SimplePlanesBlastGuard.java` | new — the guard and the policy |
| `src/main/java/.../core/commands/colonycommands/CommandColonyBlastProtection.java` | new — the per-colony off switch |
| `src/main/java/.../core/MineColonies.java` | +1 import, +1 call in `onInitialize` |
| `src/main/java/.../core/commands/EntryPoint.java` | +1 node in the `colony` subtree |
| `src/main/java/.../core/colony/Colony.java` | +1 field, +1 NBT read, +1 NBT write, +2 accessors |
| `src/main/java/.../api/util/constant/NbtTagConstants.java` | +1 tag constant |
| `src/main/java/.../api/util/constant/translation/CommandTranslationConstants.java` | +2 keys |
| `src/main/resources/assets/minecolonies/lang/manual_en_us.json` | +2 strings |
| `src/main/java/.../core/colony/permissions/ColonyPermissionEventHandler.java` | the comment at `:80` said the explosion protections were lost "entirely"; it now says which half came back and which did not |

Deliberately **not** touched, to keep the concurrent anti-air branch merging cleanly: the guard AI,
the raid event classes, and everything in `core/compatibility/simpleplanes` other than the one new
file.

Build: `cd 26.2 && JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 gradle build --offline` →
**BUILD SUCCESSFUL**. **[VERIFIED — run in this worktree.]** No tests were written; the owner
verifies by playing.

---

## 6. Blast resistance on our own blocks — considered, nothing to do

The question was whether raising MineColonies' own blocks' explosion resistance is worth it. Answer:
**it is already done where it matters, and the rest is not worth a file each.**

- **Hut blocks and racks are already immune.** `Float.POSITIVE_INFINITY`, on all 55 blocks in
  `core/blocks/huts/` through `AbstractColonyBlock` and separately on `BlockMinecoloniesRack`.
  **[VERIFIED]** Nothing to raise, and nothing feels wrong, because *hardness* — how long a pickaxe
  takes — is a separate number (`HARDNESS = 10F`) and is untouched by any of this.
- **What is left is small and mostly cosmetic:** `BlockColonySign`, `BlockWaypoint`,
  `BlockPlantationField`, `BlockDecorationController` and `BlockMinecoloniesNamedGrave` at `1F`, and
  `BlockMinecoloniesGrave` at `5F`. **[VERIFIED]** The one with a real consequence is
  `BlockDecorationController`: destroy it and the decoration it anchors is orphaned. I left it alone
  — it is one file changed for a case that needs a creeper to walk to a decoration, and an
  indestructible marker standing in a crater where the build used to be reads worse than losing it.
- **And the honest limit on this whole line of thinking:** a colony is overwhelmingly *vanilla*
  blocks placed from blueprints. Making our forty blocks immune while the walls, floors and roofs
  around them are oak planks buys close to nothing against a 16-power warhead. The block properties
  were never going to be the answer; the interception is.

---

## 7. What I did not verify

- **Nothing was run in game.** No server was started for the work this section was written about.
  Every statement about what a player *sees* is still inference from the code — there is no game
  client in this container.
- ~~**The reflective binding has never executed.**~~ **[VERIFIED 2026-08-15.]** A dedicated Fabric
  26.2 server was booted with exactly three jars in `mods/` — Fabric API, this mod, and a Simple
  Planes build carrying `xyz.przemyk.simpleplanes.api.BlastGuards` — and logged both halves of the
  handshake at start-up, zero errors:

  ```
  MineColonies: Simple Planes detected, aircraft integration enabled.
  Simple Planes is present: colony blast protection is active for aircraft blasts.
  ```

  The second line is only reached after `Class.forName` on all three names, `getConstructor`/
  `getMethod` on the four `Blast` members, and `BlastGuards.register` accepting the proxy — so the
  class names, the `(float, boolean, boolean)` constructor and the three accessors below are all
  confirmed reachable. Simple Planes' own `/blastguard status` then reported **`1 blast guard(s)
  registered`**, which is this one, from the other side of the seam.
- **What a guard *decides* is still [UNCHECKED].** The only strike flown on that server came down
  at `277, 114, 279` in a world with no colony in it, so the furthest `guard()` can have got is its
  `colony == null` exit. `defuse()` — the part that rebuilds a weaker `Blast` through the reflective
  constructor — has never been called, and neither has the `Action.EXPLODE` branch or either
  `Explosions` policy. Nothing was logged from the guard, which is what a guard that answers without
  throwing looks like, and is all it shows.
- That Loom wires Sponge Mixin without a new dependency (§4). Inference from how Loom normally works.
- The fabric-api "no explosion callback" enumeration is quoted from `PLANES-AIR-DEFENCE.md`; I did
  not repeat the unpack.
- Whether `Explosions.DAMAGE_PLAYERS` degrading to `DAMAGE_ENTITIES` matters to anyone in practice.
  It is a deviation from upstream semantics and it is stated rather than hidden.
