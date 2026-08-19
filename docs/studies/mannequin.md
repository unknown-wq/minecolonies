# The mannequin, and ten things MineColonies could do with it

Design study, not an implementation. Date: 2026-08-15. Tree: `26.2/` in this repo (0.0.31), against
decompiled vanilla 26.2 in `/opt/mc-src`, Structurize at `/workspace/structurize/26.2`, and upstream
MineColonies at `/workspace/ldtteam/minecolonies` (`2d453335`). **No feature code was written.**

Evidence standard, same as [`territory-mechanics.md`](territory-mechanics.md):

* **[MEASURED]** — observed on a live dedicated server (port 25973, superflat, `dist/minecolonies-26.2-0.0.31.jar`),
  or read out of a shipped artifact. The appendix lists every command that produced a number here.
* **[VERIFIED]** — I read the source, the `file:line` is real and says what I claim it says.
* **[UNCHECKED]** — inference from code, not observed. **There is no game client in this container**, so
  every claim about what something *looks like* is [UNCHECKED] by that fact alone.

Paths are relative to the repository root; `26.2/src/main/java/com/minecolonies/` is abbreviated to `mc/`,
and `/opt/mc-src/net/minecraft/` to `mj/`.

**Upstream oracle: there is none.** `grep -ri mannequin` over `/workspace/ldtteam/minecolonies` returns
nothing — upstream is on 1.21.1, where the entity does not exist. Everything below is first-hand.

---

## 0. The ten, cheapest first

| # | Name | New / touched | Verdict |
|---|---|---|---|
| 1 | Blueprint armour stands become mannequins | 0 java, ~30 blueprint files | Build. The mod already ships 263 blueprints with armour stands in them; this is the change the entity exists for. |
| 2 | The founder's statue (`/mc colony statue`) | ~110 / 2 | **Build this first.** Ten minutes from merge to "oh, that's me". The only idea that uses the mannequin's one perfect trick — a real player skin. |
| 3 | The scarecrow gets a body | ~70 / 3 | **Cheap, and I would not build it.** It swaps 400 lines of working renderer for a killable entity and breaks every existing field. |
| 4 | The combat academy's dummy stops being a pumpkin | ~150 / 4 | Build. The knight already swings at that block twenty times a minute and hits nothing. |
| 5 | Silhouettes on the archery range | ~160 / 4 | Build after 4, and watch the hit statistics. |
| 6 | The graveyard remembers | ~200 / 3 + art | Build if the art gets made. Without a 64×64 skin it is Steve in a graveyard. |
| 7 | The fallen lie where they fall | ~240 / 4 | Best value on the list. Costs one entity per open grave and pays the mourning mechanic back with interest. |
| 8 | Watchmen on the frontier | ~270 / 3 | Atmosphere for hostile territory, and the cheapest way to make enemy ground feel inhabited. |
| 9 | The straw man raiders attack | ~310 / 5 + item | **Best idea here at any price.** The only entry that changes how a raid is played. |
| 10 | The statue workshop (block + GUI) | ~1100 / 8 | The real feature, and a month of work. **Do not start here.** |

Line counts are authored lines in this repo's house style, which runs long — the 85-line
`CommandCitizenTeleport` (`mc/core/commands/citizencommands/CommandCitizenTeleport.java`) does one
`snapTo` call, and that is the unit I priced commands against.

**Build first: 2.** **Best regardless of price: 9.** They differ because 2 is a decoration you can see
before your first raid and 9 is a mechanic you need a raid to see.

**No entry on this list needs a mixin**, and none needed redesigning to avoid one. The mannequin is a
vanilla entity that is fully configurable through public NBT (`Entity#load`) and fully renderable by
vanilla; the only bytecode-level thing any of these want is three optional `accessible method` lines in
`minecolonies.accesswidener` for `Mannequin`'s private setters, and even those are a convenience, not a
requirement (§1.6). Nothing here reaches into a client-only class from server-loaded code either: every
entry is server-side spawn-and-configure, with all rendering done by vanilla.

---

## 1. What the mannequin actually is

### 1.1 The class

`Mannequin extends Avatar extends LivingEntity` (`mj/world/entity/decoration/Mannequin.java:28`,
`mj/world/entity/Avatar.java:13`). `Avatar` is new in 26.2 and is the shared parent of `Player` and
`Mannequin` (`mj/world/entity/player/Player.java:126`): *a player-shaped thing that wears a
`ResolvableProfile`* (`Avatar.java:70`), knows its main hand (`:38`) and which skin layers are hidden
(`:39`), and carries the player pose table — standing, crouching, swimming, fall-flying, sleeping, dying
(`:24`–`:37`).

**It is not a `Mob`.** No `GoalSelector`, no `Brain` worth the name, no `PathNavigation`, no
`serverAiStep` implementation. Nothing in this codebase's pathfinding, threat-table or AI machinery
applies to it — it is a puppet that server code moves, not an agent. `isEffectiveAi()`
(`Mannequin.java:121`) is not "does it think", it is the vanilla test that gates `travel()`
(`mj/world/entity/LivingEntity.java:3108`), i.e. *does gravity and fluid drag apply*. `aiStep()`
(`:177`) does exactly one thing beyond `LivingEntity`: `updateSwingTime()`, so it can play an arm swing.

Registered as `MobCategory.MISC`, hitbox 0.6×1.8, eye 1.62, tracking range 32, update interval 2
(`mj/world/entity/EntityTypes.java:627`–`635`), with plain `LivingEntity.createLivingAttributes()`
(`mj/world/entity/ai/attributes/DefaultAttributes.java:140`) — **20 max health, movement speed 0.7,
knockback resistance 0** [MEASURED: `attributes: [{id: "minecraft:movement_speed", base: 0.7d}]`,
`Health: 20.0f`] — and an empty loot table
(`mj/data/loot/packs/VanillaEntityLoot.java:557`).

### 1.2 The four things it carries

| Data | Type | What it buys |
|---|---|---|
| `DATA_PROFILE` | `ResolvableProfile` | the skin — see §1.3, the important one |
| `DATA_IMMOVABLE` | `boolean` | `isImmobile() = true` and `isEffectiveAi() = false` (`Mannequin.java:116`–`123`) → **no gravity, no travel, cannot be shoved** |
| `DATA_DESCRIPTION` | `Optional<Component>` | a free second line under the name tag (`mj/client/entity/ClientMannequin.java:85`), drawn by vanilla, no render code |
| pose | `Pose`, own codec | standing / crouching / swimming / fall-flying / sleeping (`Mannequin.java:37`–`39`) |

All four persist through NBT (`:126`–`:153`) and all four sync. [MEASURED] a summoned mannequin reads
back `{immovable: 1b, description: "Hans Muller", pose: "standing", profile: {...}}`, and
`pose:"sleeping"` and `pose:"crouching"` both survive ticking.

### 1.3 The skin, and the trap

`ResolvableProfile` is not just "a Mojang account". It is *either* a name/UUID that the client resolves
asynchronously **plus** a `PlayerSkin.Patch` (`mj/world/item/component/ResolvableProfile.java:28`–`35`),
and the patch's `body` field is a `ClientAsset.ResourceTexture`
(`mj/world/entity/player/PlayerSkin.java:33`–`47`) — **an arbitrary `Identifier` resolved as
`textures/<path>.png` out of any loaded resource pack** (`mj/core/ClientAsset.java:19`–`27`). It is in
the stream codec, so it syncs.

[MEASURED] this works. `/summon minecraft:mannequin ... {profile:{name:"Settler",
texture:"minecolonies:entity/citizen/default/settlermale1", model:"wide"}}` round-trips intact through
`data get`. **The mod can dress a mannequin in its own asset with no client code, no Mojang lookup and
no mixin.**

**And then the trap.** MineColonies citizen textures are *not player skins.*
`ISimpleModelType.getTexture` (`mc/api/client/render/modeltype/ISimpleModelType.java:51`–`82`) builds
`minecolonies:textures/entity/citizen/<style>/<base><sex><n><suffix>.png`; the shipped files are
**128×64** [MEASURED, `settlermale1_a.png`], and the models that consume them declare their own
layouts — `LayerDefinition.create(mesh, 128, 64)` in every per-job model
(`mc/core/client/model/FemaleBakerModel.java:80` and ~120 siblings), `128, 128` for the aristocrat, and
`64, 32` for the fallback `CitizenModel` (`mc/api/client/render/modeltype/CitizenModel.java:86`–`89`).
The vanilla mannequin is drawn by the vanilla avatar renderer against a **64×64 modern skin layout**.
Feed it a citizen texture and the UVs land in the wrong places. [UNCHECKED] exactly how ugly, no client
here — but the layouts provably differ, so no design below may assume "the mannequin wears the citizen's
face".

Three honest ways round it, in cost order:

1. **Use a real player skin.** `ResolvableProfile.createUnresolved(uuid|name)` — the client resolves and
   caches it natively (`ClientMannequin.java:60`–`68`). Free, correct, and the mod already stores such a
   UUID: `ICitizenData.getCustomTexture()` (`mc/api/colony/ICitizenData.java:450`) and the visitor's
   `textureUUID`, which `VisitorDataView.getCustomTexture` (`mc/core/colony/VisitorDataView.java:63`–`90`)
   currently resolves by hand on a background executor with its own cache — code the mannequin makes
   redundant.
2. **Author 64×64 skins as mod assets** — a handful of PNGs ("a settler", "a guard", "a mourner") pointed
   at by `profile.texture`. An art task, not a code task; price it as art.
3. **Convert the citizen atlas at datagen time.** Mechanical, and 440 files per style × 8 styles. Nobody
   should do this for a decoration.

### 1.4 How it dies, which is the part that decides designs

It is a living, pickable, hittable entity: `isPickable()` (`LivingEntity.java:3338`) → true, so
`canBeHitByProjectile()` (`mj/world/entity/Entity.java:2006`) → true, so arrows and swords land on it.
[MEASURED] `/damage <it> 25` kills it; `Invulnerable:1b` refuses all of it and it stays at 20 HP.

**And invulnerability is not free, which shapes two of the ten.** `hurtServer` returns at its first line
for an invulnerable entity (`LivingEntity.java:1169`–`1172`), *before* `hurtTime`, before
`broadcastDamageEvent`, before the hurt sound. So an invulnerable mannequin **cannot be made to flinch**.
A zero-damage hit on a *vulnerable* one does everything but subtract health: `actuallyHurt` ignores a
0.0 damage (`:1959`), while `hurtTime = 10` and `level.broadcastDamageEvent(...)` still run (`:1221`,
`:1233`), and the call returns `true` (`:1258`, `:1279`). That is exactly what the combat academy already
does to a live citizen (`EntityAICombatTraining.java:213`). The same fork decides what an arrow does:
`AbstractArrow.onHitEntity` (`mj/world/entity/projectile/arrow/AbstractArrow.java:463`) discards the
arrow and plays the hit sound when `hurtOrSimulate` returns true, and **deflects it backwards** when it
returns false (`:500`–`:511`). A training dummy must therefore be *vulnerable and re-healed*, never
`Invulnerable:1b`. [VERIFIED, all of it, by source.]

What actually kills a mannequin nobody is attacking:

| Cause | Evidence |
|---|---|
| **Suffocation** — a block at *eye* level (feet+1) | [MEASURED] 20 HP → 4 HP in 8 s, dead in ~10 s. A block at the feet does nothing. `LivingEntity.java:427` |
| **Drowning** | [MEASURED] air 300 → 95 in ~12 s under water. `LivingEntity.java:439`–`449` |
| **Cramming** | [MEASURED] 26 immovable mannequins summoned on one block: within seconds all had 2–8 HP and six were dead. `LivingEntity.java:3205`–`3216`, gamerule default 24 |
| **Fall damage** | [MEASURED] dropped 10 blocks, 20 → 13 HP. Only when *not* immovable |
| Fire, lava, cactus, raid AoE, a stray training arrow | [UNCHECKED], but it is a `LivingEntity` with no resistances |

And when it dies: **nothing drops.** The loot table is empty, and [MEASURED] a mannequin wearing a
diamond chestplate and an iron sword left **no item entities** on death. Any "display your guard's kit"
design that puts real gear on a mannequin is one creeper away from deleting it. The only trace is a
server-log line, `LOGGER.info("Named entity {} died: {}")` (`LivingEntity.java:1456`) — no chat spam.

**A vanilla bug worth knowing before entries 4, 5 and 9.** [MEASURED] shutting the test server down while
a mannequin was mid-death printed:

```
Serialization errors:
chunk@[3, 3]Mannequin['Butt'/73, ...]: Failed to encode value 'DYING' to field 'pose': Invalid pose: dying
```

`Avatar.POSES` contains `Pose.DYING` (`Avatar.java:36`) but `Mannequin.VALID_POSES` does not
(`Mannequin.java:37`), so a mannequin that is saved during its death animation cannot encode its own
pose. It is a log line, not a crash, and the entity is going away anyway — but every design that lets
mannequins die (the training dummies, the straw man) will produce these in a busy colony, and the port
should not spend an afternoon hunting a "serialization error" that is vanilla's.

A non-immovable mannequin is also **pushable**: [MEASURED] a zombie spawned one block away never attacked
it (vanilla monsters do not target avatars) but shoved it 3.4 blocks in ten seconds. Anything decorative
must be `immovable:1b`. Conversely an immovable one cannot be shoved *back*, so one standing in a
one-wide doorway will keep shoving citizens aside forever.

### 1.5 What it costs

[MEASURED] on an otherwise idle dedicated server, `tick query` over 100 samples:

```
baseline (no mannequins)          0.2 ms/tick   (P95 0.2)
+200 immovable, invulnerable      1.4 ms/tick   (P95 1.8)
after killing them                0.1 ms/tick
```

**≈6 µs per mannequin per tick.** Cheap enough that a few dozen per colony are free, expensive enough
that "one per citizen" in the 1000-citizen test world would be 6 ms — a third of the frame gone on
scenery. Entities in unloaded chunks cost nothing and are simply not there [MEASURED: a mannequin
summoned outside the force-loaded area could not be selected until `forceload add`].

Network cost is negligible: `updateInterval(2)` with data that never changes after spawn, plus one
`ResolvableProfile` in the spawn packet.

### 1.6 What you cannot do with it

* **There is no mannequin item.** No spawn egg, no creative-tab entry, nothing — `grep -r MANNEQUIN`
  over all of `/opt/mc-src` hits only the entity, its attributes, its loot table, the render dispatcher,
  and `/fetchprofile`. Vanilla's only way in is `/summon`, or the `[Summon Mannequin]` chat button that
  `FetchProfileCommand` prints for gamemasters (`mj/server/commands/FetchProfileCommand.java:76`). Three
  lang keys exist in the whole game: `entity.minecraft.mannequin`, `.label` ("NPC"),
  `commands.fetchprofile.summon_mannequin` [MEASURED from `/opt/vanilla/server-26.2.jar`]. **Any
  player-facing way to place one is the mod's to build.**
* **There is no right-click.** `LivingEntity` has no `interact` override and `Mannequin` adds none, so
  vanilla cannot equip, pose or rename one by hand. `/item replace entity` works [MEASURED].
* **Do not subclass it.** The client renderer is chosen by `case ClientMannequin mannequin ->`
  (`mj/client/renderer/entity/EntityRenderDispatcher.java:97`), and `ClientMannequin` is installed by
  overwriting a static factory on the base class from `Minecraft` (`ClientMannequin.java:29`–`31`,
  `mj/client/Minecraft.java:567`). A `MinecoloniesMannequin extends Mannequin` would get no renderer, and
  writing one means reimplementing `ClientAvatarEntity` + `AvatarRenderer` in client-only classes. Use
  `EntityTypes.MANNEQUIN` as-is, always.
* **Its setters are private** (`Mannequin.java:85`, `:93`, `:101`). Two ways in, both mixin-free:
  build a `CompoundTag` and call the public `Entity.load(TagValueInput.create(...))`
  (`mj/world/entity/Entity.java:2139`) — the exact pattern Structurize already uses at
  `structurize/26.2/.../placement/StructurePlacer.java:340`–`349` — or add three `accessible method`
  lines to `26.2/src/main/resources/minecolonies.accesswidener`, which already carries 43 such entries.
  I would use the AccessWidener; the NBT route is the fallback if a version bump ever renames them.
* **It has no per-entity mod state.** Marking a mannequin as "this colony's grave marker #7" means either
  the Fabric data-attachment API (present in the dependency set — `fabric-data-attachment-api-v1` is in
  the resolved `fabric-api 0.154.2+26.2`) or, cheaper and with no new dependency surface, a
  `List<UUID>` in the owning building's NBT plus `ServerLevel#getEntity(UUID)`. Every entry below
  assumes the second.

---

## 2. What the port already has, and whether the mannequin displaces it

**Archery targets — sits beside.** `BuildingArchery.registerBlockPosition`
(`mc/core/colony/buildings/workerbuildings/BuildingArchery.java:58`–`69`) records vanilla `Blocks.TARGET`
as targets and `Blocks.GLOWSTONE` as stands; `EntityAIArcherTraining` shoots a zero-damage arrow at the
block and grades the shot by the arrow's distance to it (`:238`). The target block is the *scoring*
device and stays. A mannequin is the thing standing in front of it. See entry 5.

**Combat academy dummies — replaced, visually.** The "dummy" is a carved pumpkin on a hay bale
(`BuildingCombatAcademy.java:70`–`76`); the knight paths to it and swings at air —
`attackDummy` (`mc/core/entity/ai/workers/guard/training/EntityAICombatTraining.java:276`–`330`) plays a
sound and damages the held sword but **hurts nothing**, because there is nothing there to hurt. Entry 4.

**Blueprints already contain armour stands.** [MEASURED] **263 of the 9374 shipped blueprints** contain
`minecraft:armor_stand`, concentrated in `military/` (barracks, barracks towers, guard towers, archery,
combat academy) plus town halls and universities. `combatacademy4.blueprint` carries three posed stands
and item frames holding an iron sword. So the *idea* of a human-shaped decoration in these buildings is
already shipped — badly. Entry 1.

**The field scarecrow — not worth touching.** It is a block entity with two texture variants
(`mc/api/tileentities/ScareCrowType.java`), a 116-line model and a 286-line renderer
(`mc/core/client/render/TileEntityScarecrowRenderer.java`), and it *owns the field*: the block entity is
what `FarmField` and the field GUI hang off. A mannequin can decorate a field; it cannot be a field.
Entry 3, with a "no".

**Graves and mourning — complemented.** `GraveManager.createCitizenGrave`
(`mc/core/colony/managers/GraveManager.java:258`) puts a grave block where a citizen dies;
`GraveyardManagementModule.buryCitizenHere` (`:171`–`203`) turns it into a named grave with the citizen's
first name, last name and job; `EntityAIMournCitizen` already walks citizens to the graveyard and has a
`STARING` state. Crucially `GraveData` carries the dead citizen's **entire NBT**
(`mc/api/colony/GraveData.java:29`–`50`), so gender, texture id, suffix and style are all recoverable.
Entries 6 and 7.

**Citizen skins — do not touch.** §1.3. The suffix system (`CitizenData.SUFFIXES`,
`mc/core/colony/CitizenData.java:104`, inherited by children through
`ReproductionManager.java:361`) picks between `_a/_b/_d/_w` variants of a 128×64 custom-layout texture.
It is a different universe from `PlayerSkin`. The one place they touch is
`CitizenData.getCustomTexture()` — a real player UUID — which the mannequin renders natively.

**Build-tool preview — no overlap.** The preview draws blocks through Structurize's blueprint renderer;
the only citizen-shaped things outside the world are BlockUI's `EntityIcon`
(`mc/core/client/gui/blockui/EntityIcons.java`) and the dead Halloween ghost flag
(`RenderBipedCitizen.isItGhostTime`). A mannequin is a world entity; it can add nothing here.

**Visitors and the tavern — not worth it.** `TavernBuildingModule.onColonyTick`
(`mc/core/colony/buildings/modules/TavernBuildingModule.java:138`–`165`) already spawns *real* visitor
entities that walk, talk, and can be recruited. A mannequin version of a visitor is a worse visitor. The
one thing worth stealing is the reverse: `VisitorDataView`'s hand-rolled profile resolution (§1.3) is
what `ClientMannequin` does for free, which is an argument about *that* code, not about mannequins.

**One incidental find, reported and not pursued:** `ItemStackUtils.getListOfStackForEntity`
(`mc/api/util/ItemStackUtils.java:205`) — the mod's own entity-cost function, with the
`ArmorStand` branch — has **zero callers** in the port. The builder actually prices blueprint entities
through Structurize's copy (`structurize/.../StructurePlacer.java:379` →
`structurize/.../api/ItemStackUtils.java:168`), which for a mannequin returns an **empty list**:
`getPickResult()` is `null` for anything that is not a `Mob` or an `ItemFrame`
(`mj/world/entity/Entity.java:3852`), and a mannequin holds no `Container`. A mannequin in a blueprint is
therefore built **for free, wearing whatever it wears**. That is entry 1's charm and entry 1's balance
hole.

---

## 3. The ten

### 1. Blueprint armour stands become mannequins

**What the player experiences.** The builder finishes the archery range and there are two figures on the
shooting line in leather and a padded jack, and a third slumped by the door. The barracks courtyard has
a rank of them. They were armour stands last week: brass poles with arms.

**What it attaches to.** No mod code at all. Structurize spawns blueprint entities in its own placement
phase — `StructurePlacer.handleEntitySpawn` (`structurize/26.2/src/main/java/com/ldtteam/structurize/placement/StructurePlacer.java:332`),
which reads the stored `CompoundTag` through `TagValueInput`, calls `EntityType.by(...)`,
`entity.load(...)` and places it (`:340`–`:360`), after the block phases. The `foundEntity` dedupe at
`:358`–`:369` compares exact positions, so an immovable mannequin is never duplicated by a rebuild. The
change is to the 263 blueprint files under `26.2/src/main/resources/blueprints/` that contain
`minecraft:armor_stand` — realistically the ~30 military ones.

**Code size.** Zero java, zero lang keys, zero packets. One throwaway python script over gzipped NBT
(the format is plain: root keys `entities`, `blocks`, `palette`, `tile_entities`, …), rewriting each
armour-stand tag to `{id: "minecraft:mannequin", Pos, Rotation, immovable: 1b, profile: {...},
pose: "standing"}` and keeping the `equipment` compound. Call it 60 lines of script and a careful diff
review of ~30 binary files.

**What could go wrong.** Three things, all real.

1. **They can die and armour stands mostly could not.** A block placed at head height kills one in ten
   seconds [MEASURED] — so a *later* upgrade or a player's wall will quietly delete the decoration, and
   nothing tells anyone. Raid AoE and lava do the same.
2. **Free armour.** The builder charges nothing for a blueprint entity or its equipment (§2), so a
   mannequin in iron is a free set of iron the player can never pick up (it drops nothing [MEASURED])
   but can see. Keep them unarmoured or in leather.
3. **The stored NBT is 1.21.1-era.** The shipped tags carry `ArmorItems`, `HandItems`,
   `forge:entity_gravity` and `minecraft:generic.movement_speed` [MEASURED], and `StructurePlacer` runs
   no DataFixer. Whatever those old armour stands were supposed to be wearing, they are probably not
   wearing it now. Anything written fresh should be written in 26.2 form.

Performance: ~6 µs/tick each, a dozen per military building, invisible.

**Is it actually good?** Yes, and it is the single cheapest visible change in this document. It is also
the *point* of the entity: vanilla shipped a posable skinned humanoid so that builds could have people
in them. The port would be using it exactly as intended, for the price of a script.

### 2. The founder's statue

**What the player experiences.** `/mc colony statue 1` and there you are, on the town hall steps, in
your own skin, with **Founder of Rivermouth** under your name. It does not move and cannot be pushed
over. A second form of the command puts up any player by name.

**What it attaches to.** `Permissions.getOwner()` (`mc/core/colony/permissions/Permissions.java:539`)
gives the owner's UUID; `ResolvableProfile.createUnresolved(uuid)` is all the client needs — it resolves
and caches the skin itself (`ClientMannequin.java:60`–`68`), which is precisely the work
`VisitorDataView.getCustomTexture` (`mc/core/colony/VisitorDataView.java:63`–`90`) does by hand today.
Spawn with `EntityTypes.MANNEQUIN.create(level, EntitySpawnReason.MOB_SUMMONED)`, configure via
`load(TagValueInput.create(...))` or three AccessWidener lines (§1.6), `immovable:1b`,
`Invulnerable:1b`, description from a lang key.

**Code size.** One command class in `mc/core/commands/colonycommands/`, ~90 lines against the 85-line
`CommandCitizenTeleport` precedent; one line in `CommandTree`; ~4 lang keys in `manual_en_us.json`
(`%s` only — the colony name and the player name are both `%s`, so the pipeline's `%d` limitation never
bites). Plus a small shared helper — `MannequinUtils.place(level, pos, profile, description)`, ~40
lines — that every later entry reuses. No packets, no saved data, no client code.

**What could go wrong.** Offline mode: `/fetchprofile`-style resolution needs the session service, and
this container's server runs `online-mode=false`, where a name resolves to an offline UUID and the skin
falls back to Steve/Alex [UNCHECKED — no client]. On a real online-mode server it just works. The statue
is invulnerable, so the failure mode is a player walling it in — which does nothing, because
`Invulnerable:1b` beats suffocation [MEASURED].

**Is it actually good?** As a *mechanic*, no — it is a command that spawns a decoration. As a **first
delivery**, it is the best thing on this list: it is visible in the first ten minutes, it exercises the
whole spawn/configure/persist path that entries 4–9 all need, and it is the only use of the mannequin
that is 100% correct with zero art. Build it, ship the helper, then decide about the rest.

*Variant worth considering later:* place it automatically when the town hall reaches level 3, keyed off
`AbstractBuilding.onUpgradeComplete` (`mc/core/colony/buildings/AbstractBuilding.java:1096`), with the
UUID stored in the building NBT. That is +50 lines and turns a toy into a milestone.

### 3. The scarecrow gets a body

**What the player experiences.** The thing in the middle of your wheat is a straw-stuffed figure in a
pumpkin mask instead of the current cross-shaped model.

**What it attaches to.** `BlockScarecrow` / `TileEntityScarecrow`
(`mc/api/tileentities/AbstractTileEntityScarecrow.java`), its two variants
(`ScareCrowType.PUMPKINHEAD`, `NORMAL`), the 116-line `ScarecrowModel` and the 286-line
`TileEntityScarecrowRenderer`. A mannequin with a carved pumpkin in the head slot standing on the block
would replace the renderer entirely.

**Code size.** ~70 lines to spawn/maintain the entity from the block entity, minus ~400 lines of model
and renderer deleted. Superficially the best line-count trade here.

**What could go wrong.** Everything that matters.

* The scarecrow **is** the field. The block entity is what the farmer's field, the field GUI
  (`WindowField`) and `FarmField` hang off; the entity can only ever be paint on top, so you keep the
  block entity *and* add an entity — a net gain in objects, not a loss.
* It becomes killable in the middle of a farm, where creepers go [MEASURED: 20 HP, no drops]. The
  current one is a block.
* Every existing field in every existing world keeps the old block; you now maintain two appearances.
* The pumpkin-head variant is a texture swap today and would become an equipment slot, so the two
  variants stop looking like the art that was drawn for them.

**Is it actually good?** **No.** It is cheap and I would not build it. It is the kind of change that
looks like a simplification on a line-count table and costs a week in defect reports. The scarecrow
already works, is already a colony-specific silhouette, and nobody has complained about it.

### 4. The combat academy's dummy stops being a pumpkin

**What the player experiences.** The knights in the combat academy now hit a figure. It jerks when it is
hit, it has a name — *Training Dummy* — and after a level-up it is wearing better padding. Before, they
were swinging at a pumpkin on a hay bale.

**What it attaches to.** `BuildingCombatAcademy.registerBlockPosition`
(`mc/core/colony/buildings/workerbuildings/BuildingCombatAcademy.java:70`–`76`) collects `fightingPos`
— the hay block under a carved pumpkin — and serialises it (`:84`–`:101`).
`EntityAICombatTraining.findDummyPartner` (`:241`) picks one and paths to it;
`attackDummy` (`:276`–`:330`) faces it, swings, plays `PLAYER_ATTACK_SWEEP`, damages the sword — and
touches no entity. The addition: in `onColonyTick` (the building tick, which runs once per 500 world
ticks — see `TavernBuildingModule.java:138`'s port note), ensure a mannequin exists at each
`fightingPos.above()`, and in `attackDummy` call `mannequin.hurt(damageSources().source(DamageSourceKeys.TRAINING, worker), 0.0F)`
exactly as partner training already does to a live citizen (`:213`) — a zero-damage hit that flashes and
sounds without taking a single point off (§1.4).

**Code size.** ~150 lines: a `List<UUID>` beside `fightingPos` in `BuildingCombatAcademy` with its NBT
(the file already does this dance for positions — copy it, ~35 lines), ~60 lines of ensure/lookup/cleanup,
~15 lines in `attackDummy`, ~40 in the shared helper from entry 2. Four touched files, two lang keys, no
packets, no new saved data beyond the building's own NBT.

**What could go wrong.** It must be immovable but **not** `Invulnerable:1b` — §1.4: invulnerability
returns before the flash and the design collapses into the pumpkin it replaced. So the dummy really can
be killed, by a raid, a fire, or a knight who hits it with something that is not the training damage
source. The tick that ensures it exists must therefore also top its health back up (`setHealth(20)` on a
dummy below full is two lines) and re-spawn it if it has gone. Get that wrong and the academy silently
reverts to swinging at air, with nothing in the log. Performance: one entity per fighting position, ~3
per academy, ~20 µs/tick.

**Is it actually good?** Yes. It costs almost nothing, it is on screen inside one work cycle of an
existing worker, and it fixes something that has always looked wrong. It is the best of the "building
looks better" entries because the AI already aims at that exact block.

### 5. Silhouettes on the archery range

**What the player experiences.** The archers are shooting at people-shaped targets. An arrow that lands
thumps into the figure; one that misses sails past into the target block behind it.

**What it attaches to.** `BuildingArchery` records `Blocks.TARGET` positions (`:58`–`69`);
`EntityAIArcherTraining.selectTarget` (`:116`) line-of-sight checks to the block,
`shoot` (`:162`) spawns a zero-damage `ModEntities.MC_NORMAL_ARROW`, and `checkShot` (`:233`) grades it
three seconds later by `arrow.distanceToSqr(target) < MIN_DISTANCE_FOR_SUCCESS` (2.0). A mannequin goes
one block in front of each target, immovable and — for the reason in §1.4 — **vulnerable**: a
zero-damage arrow that hits a vulnerable entity is consumed with a hit sound
(`AbstractArrow.java:463`, `:495`–`:499`), while one that hits an invulnerable entity is *deflected
backwards* (`:500`). The discarded arrow keeps its last position, so `checkShot`'s distance test still
reads the point of impact.

**Code size.** ~160 lines, same shape as entry 4 (UUID list + NBT in `BuildingArchery`, ensure in the
building tick, helper reuse), plus two lang keys. Four touched files.

**What could go wrong.** Two things, one of them a balance change.

1. **The hit statistics move.** `checkShot` also feeds `ARROWS_HIT`/`ARROWS_FIRED`
   (`StatisticsConstants`) and archer XP. An arrow stopped by the silhouette records its position a block
   short of the target block instead of flying past it, so some near-misses become successes — a real, if
   small, buff to archer training speed. [VERIFIED] the mechanism; [UNCHECKED] the size of the shift.
   Offset it by putting the figure *beside* the target rather than in front, which costs one vector.
2. **The line-of-sight check.** `selectTarget` (`:126`) requires `world.clip(...)` from the archer's eye
   to the target block to *reach the block*. It is a block raycast and ignores entities, so a mannequin
   in front does not break it [VERIFIED — `ClipContext.Block.COLLIDER`, no entity clip].

Performance: ~3–8 targets per range, negligible. Arrows despawn normally.

**Is it actually good?** Yes, but strictly after entry 4 — same code, one extra risk. If the statistics
shift annoys anyone, the mannequin moves a block sideways and becomes a spectator.

### 6. The graveyard remembers

**What the player experiences.** Behind each named grave in the graveyard stands a still figure with the
dead citizen's name over it and their trade beneath: *Miner*, *Baker*, *Guard*. A graveyard that has
been running a hundred colony days is a crowd.

**What it attaches to.** `GraveyardManagementModule.buryCitizenHere`
(`mc/core/colony/buildings/modules/GraveyardManagementModule.java:171`–`203`) already splits
`lastGraveData.getCitizenName()` into first and last name and writes the job name onto the named grave's
text lines. The mannequin goes up in the same block, one position behind, using `CustomName` for the name
and `DATA_DESCRIPTION` for the job — the second line vanilla draws for free
(`ClientMannequin.java:85`). Removal hangs off `restingCitizen` (`:52`) and the module's serialisation
(`:63`–`:130`), so the UUID list rides along with the list of the buried.

**Code size.** ~200 lines: ~60 in the module (UUID list + NBT + cleanup), ~50 spawn/label, helper reuse,
~30 for a colony-tick reconciliation that re-creates a memorial whose mannequin has been destroyed, and
3–4 lang keys. **Plus art**: 2–4 hand-made 64×64 skins (a plain settler, male/female; optionally a
guard). Without them, §1.3 says every memorial is default Steve, which is worse than nothing in a
graveyard.

**What could go wrong.** The graveyard is a *building*: the undertaker digs, and the builder repairs. A
figure standing on a plot the undertaker wants is in the way, and one the builder walls in dies in ten
seconds [MEASURED]. Keep them strictly on the row *behind* the grave block, invulnerable, immovable.
Volume: a mature colony with generations on can bury hundreds — 300 memorials is 1.8 ms/tick, which is
real. **Cap it** (the last N, or one per grave plot that exists), and cap it in the design rather than in
a hotfix.

**Is it actually good?** Yes if the art gets made, no if it does not. It is the most on-theme use of an
entity that vanilla literally labels "NPC", and with generations turned on the graveyard is where the
mod's new long game is told. But a row of identical Steves is a bug report, not a memorial.

### 7. The fallen lie where they fall

**What the player experiences.** A citizen dies in the woods and their body is *there*: face down beside
the grave block, in the clothes they died in, name over it. Mourners walk out to it. When the undertaker
finally collects the grave, the body is gone.

**What it attaches to.** `GraveManager.createCitizenGrave`
(`mc/core/colony/managers/GraveManager.java:258`–`320`) is the single choke point: every death in the
mod, including the new `minecolonies:oldage` path from `GENERATIONS.md`, goes through it, and it already
searches for a valid grave position with `BlockPosUtil.findAround` (`:290`) before placing the block. The
mannequin goes at that same position in `pose:"sleeping"` [MEASURED: the pose persists], removed in
`GraveManager.removeGrave` (`:182`) and by the grave's own decay timer (`TileEntityGrave`, delayed by
`GRAVE_DECAY_BONUS` at `:314`). Mourning already exists and already walks citizens to graves:
`EntityAIMournCitizen` (`mc/core/entity/ai/minimal/EntityAIMournCitizen.java`) has
`WALK_TO_GRAVE` and `STARING` states — they currently stare at a block.

**Code size.** ~240 lines: ~50 in `GraveManager` (spawn, remove, and a `Map<BlockPos, UUID>` in the
manager's existing NBT), ~40 for the appearance (gender and job come off `ICitizenData` directly; the
skin is the same art question as entry 6), ~60 for a reconciliation pass over graves on colony load,
helper reuse, 3 lang keys. Four touched files, no packets.

**What could go wrong.**

* **Volume.** One raid kills fifteen citizens; that is fifteen bodies until the undertaker gets round to
  them, and a colony with no undertaker never gets round to them. The grave decay timer bounds it
  [VERIFIED it exists], but the cap should be explicit — bodies beyond N, oldest first, simply do not
  spawn.
* **Water and lava.** `createCitizenGrave` already handles both by relocating (`:266`–`:288`); the body
  must follow the *grave's* final position, not the death position, or it drowns [MEASURED] and vanishes.
* **`pose:"sleeping"`** on an avatar with no bed: vanilla only cancels sleeping when a *sleeping position*
  is set (`LivingEntity.java:2773`), which a raw pose does not set, so it sticks. How a bedless sleeping
  avatar is *drawn* is [UNCHECKED] — if it renders standing, use `crouching`, which is one word.

**Is it actually good?** Yes — this is the best value on the list. It costs one small hook in one manager,
it is felt the first time somebody dies, it makes an existing mechanic (mourning) legible, and it is the
one entry that gets *better* as the port's own generational mechanic ages a colony. It also has the
cleanest story: the mod's deaths already produce a persistent, decaying, colony-owned object; the body is
that object with a face.

### 8. Watchmen on the frontier

**What the player experiences.** You walk up to the red border of Blackreach and there are figures
standing along it, twenty blocks apart, facing out at you. They do not move, they do not fight, and the
line under their names says whose ground you are looking at. The territory has people in it.

**What it attaches to.** `HostileTerritory.in(dimension)` / `.at(dimension, pos)`
(`mc/api/colony/territory/HostileTerritory.java:89`, `:101`) and the chunk/column masks described in
`hostile-territory.md`; `HostileTerritorySight.subscribeNearby`, already called per chunk change from
`EventHandler.onEnteringChunk` (`mc/core/event/EventHandler.java:346`), is the natural place to
materialise them near a player and drop them when nobody is close. Territory publication
(`HostileTerritory.Builder.publish`, `:202`) is where the roster of watch posts gets computed.

**Code size.** ~270 lines: ~90 to derive watch positions from the border chunks (the border walk already
exists for the renderer), ~80 for spawn/despawn keyed on player proximity, ~60 for the saved roster,
helper reuse, 3–4 lang keys. Three touched files. No packets — everything is entity state.

**What could go wrong.** Spawning near a player and despawning behind them means a mannequin can be
created twice or leaked; the reconciliation must be idempotent (find-by-UUID first, and never spawn on a
chunk that is not entity-ticking — `WorldUtil.isEntityBlockLoaded`, which `RaidManager` already uses).
Terrain: a watch position derived from a chunk corner can be inside a hill, and a mannequin inside a hill
dies in ten seconds [MEASURED] — reuse `BlockPosUtil.findAround` with the solid-below/air-at predicate
the grave and raid code both use. Skin: enemy livery is the same art question again, though here a plain
dark skin does the job.

**Is it actually good?** Yes, if hostile territory is going to be a real feature rather than a coloured
map. It is the cheapest thing that makes enemy ground feel *owned*, and it sets up the entry that
follows it: once there are figures on the border, making some of them hostile decoys or bounty targets is
an increment, not a new system. On its own, though, it is scenery — rank it after 7 unless the territory
work is the current focus.

### 9. The straw man raiders attack

**What the player experiences.** You craft a *straw man* — a scarecrow of war — and stake it out in front
of the gate before a raid. The barbarians go for it. It absorbs a dozen swings and a couple of arrows,
falls apart in a burst of straw, and by then your archers have had ten extra seconds of free shooting.
Put up three and you have chosen where the raid happens.

**What it attaches to.** Raider targeting is two identical predicates:
`RaiderMeleeAI.isAttackableTarget` (`mc/core/entity/mobs/aitasks/RaiderMeleeAI.java:86`) and
`RaiderRangedAI.isAttackableTarget` (`:198`), both currently
`(EntityCitizen | AbstractVillager | IronGolem) && !isInvisible() || Player && !creative`. Adding "or a
mannequin this colony has staked" is one disjunct in each. Acquisition already sweeps a box around the
raider (`TargetAI.getSearchArea`, `mc/core/entity/ai/combat/TargetAI.java:240`) and the threat table
(`IThreatTableEntity`) handles the rest. Our own guards will **not** attack it:
`AbstractEntityAIGuard.isAttackableTarget` (`mc/core/entity/ai/workers/guard/AbstractEntityAIGuard.java:824`)
only accepts registered monsters, hostile players and enemy colony guards [VERIFIED].

**Code size.** ~310 lines plus assets: a new item and its placement (~90, with the mod's existing item
patterns), the entity spawn and its colony-side registry of staked decoys (~70, and this one *should* be
a colony-level list, not a building's), the two AI disjuncts (~10), a hit/collapse handler that turns
lethal damage into a particle burst and removal so it never leaves a corpse (~40), helper reuse, an item
model + texture, ~6 lang keys, one config key for how much punishment one absorbs. Five touched files.

**What could go wrong.**

* **The failure mode a player hits:** the straw man is *too* good and every raid becomes "put up three
  straw men". That is a numbers problem — health, count cap per colony, cooldown — and it must be a
  server config from day one, not a constant.
* Raiders that path to a decoy in an unreachable spot stall. The persecution-distance guards
  (`RaiderMeleeAI.isWithinPersecutionDistance`, `:93`) bound the chase, but a decoy on a roof will make
  raiders mill; refuse placement without a walkable path, or accept it and let it be a player skill.
* It must **not** be invulnerable — this is the one entry where the mannequin's 20 HP is the mechanic.
  Which means cramming, fire and stray arrows also kill it; that is fine and thematic.
* Performance: one path recomputation per raider retarget, which the raid already pays.

**Is it actually good?** **This is the best idea in the document.** It is the only entry that changes a
decision the player makes — where the raid gets fought — and it is built almost entirely out of two
one-line predicate changes in code that already exists. It is also the entry that makes the mannequin's
worst property (it dies easily, drops nothing, leaves no trace) into exactly the right property. If the
owner will pay for one non-trivial thing, pay for this.

### 10. The statue workshop

**What the player experiences.** A block you place and right-click. A window with a pose picker, a skin
field ("type a player name"), a description line, four equipment slots and a rotation dial. You build the
hall of ancestors you wanted, one figure at a time, and the colony keeps them.

**What it attaches to.** Everything: a new block and block entity
(`ModBlocks`/`MinecoloniesTileEntities`/`TileEntityInitializer`), a BlockUI window under
`mc/core/client/gui/`, at least two network messages under
`mc/core/network/messages/server/`, a placement handler so it survives being in a blueprint, saved data
per statue, and the profile-resolution round trip for names typed by players. The mannequin itself is
the least of it.

**Code size.** ~1100 lines across ~8 new files and ~6 touched, plus an item model, a block model, a GUI
xml, ~20 lang keys and two packets. Weeks, not days.

**What could go wrong.** Player-typed names hitting the session service (rate limits, offline mode,
someone typing 200 names); a GUI that must round-trip pose + equipment + description without desync;
statues that outlive their block; and a permission story (who in the colony may pose the founder?).

**Is it actually good?** It is the *real* feature and it is the wrong place to start. Every cheap entry
above delivers a slice of it, and after entries 1, 2, 6 and 7 exist, most of what this window would do is
already happening automatically. Build it, if ever, when somebody asks for it by name.

---

## 4. What I would do

Two afternoons: entry 2 (the founder's statue, with the shared helper), then entry 1 (the blueprint
script). Both are visible immediately and neither can break a save.

Then entry 7 (the fallen), because it is the one that makes an existing mechanic legible, and it is
where the port's own generational work pays off.

Then, if there is appetite for a real mechanic, entry 9.

Entry 3 I would decline, and entry 10 I would not begin.

---

## Appendix. What was run on the server

A dedicated 26.2 Fabric server, copy of `/home/user/fabric-server-26.2` in this session's scratchpad,
**port 25973**, superflat, `fabric-api-0.154.2+26.2` + `dist/minecolonies-26.2-0.0.31.jar`, no client.
Started, driven through a console FIFO, stopped at the end of the session. The 3×3 chunk area around
spawn was `forceload`ed; without that, a summoned mannequin cannot even be selected — an entity in an
unloaded chunk is simply not there.

| What | Command | Result |
|---|---|---|
| Skin patch to a mod asset | `summon minecraft:mannequin ... {profile:{name:"Settler",texture:"minecolonies:entity/citizen/default/settlermale1",model:"wide"}}` | round-trips through `data get` intact |
| Immovable = no gravity | one immovable and one plain mannequin at y −50 | immovable stayed at −50.0; the other fell to −60.0 |
| Fall damage | the faller, above | 20 → 13 HP |
| Lethal damage, no drops | `damage <it> 25`, then a search for item entities | died; a diamond chestplate and an iron sword on it dropped **nothing** |
| Invulnerable | `Invulnerable:1b` then `damage 25`, then a block at eye level | "Target is invulnerable"; 20 HP after both |
| Suffocation | `setblock` at feet, then at eye height | feet: nothing. Eye: 20 → 4 HP in 8 s, dead in ~10 s |
| Drowning | water at eye level | `Air` 300 → 95 in ~12 s |
| Cramming | 26 immovable mannequins on one block | all survivors at 2–8 HP within seconds; six dead |
| Monsters ignore it, but push it | a zombie one block away for 10 s | 20 HP untouched; pushed 3.4 blocks |
| Poses persist | `pose:"sleeping"`, `pose:"crouching"` | both read back unchanged after ticking |
| Tick cost | `tick query`, 100 samples, 0 → 200 → 0 mannequins | 0.2 ms → 1.4 ms → 0.1 ms, i.e. **≈6 µs each** |
| Equipment | `item replace entity ... armor.chest` | works; `equipment` compound as for any living entity |
| A dying mannequin cannot save its pose | `stop` with a corpse in a chunk | `Failed to encode value 'DYING' to field 'pose'` — vanilla, §1.4 |

Read out of shipped artifacts rather than the running server:

* `/opt/vanilla/server-26.2.jar` — the only three mannequin lang keys in the game.
* `dist/minecolonies-26.2-0.0.31.jar` — citizen textures are 128×64 (`settlermale1_a.png`), 440 files in
  the `default` style alone, 8 styles.
* `26.2/src/main/resources/blueprints/` — 263 of 9374 blueprints contain `minecraft:armor_stand`;
  `warped/military/combatacademy4.blueprint` has three posed stands, in 1.21.1-era NBT.

Everything about **appearance** — how a bedless sleeping avatar is drawn, how badly a citizen texture is
mangled on a player model, whether the red flash reads at all on a figure in leather — is [UNCHECKED] and
must be looked at in play before any of it is promised.
