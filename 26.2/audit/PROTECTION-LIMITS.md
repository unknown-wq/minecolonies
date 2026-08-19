# What colony protection can and cannot do (Fabric / MC 26.2)

Date: 2026-08-16. Tree: `/home/user/minecolonies/.claude/worktrees/protection-limits/26.2`,
branch `claude/protection-limits`, base commit `35e5fac00c`, artifact
`build/libs/minecolonies-26.2-0.0.39.jar`.
Diffed against the NeoForge original at `/home/user/minecolonies/1.21.1`; vanilla 26.2 read from
`/opt/mc-src`; Fabric API read from the unpacked `fabric-api-0.154.2+26.2.jar`.

**Research report. No production code was changed** — `git diff` on `26.2/src` is empty; the only
file this branch adds is this one.

Subject: everything that stands between a colony and *damage or intrusion* — explosions, block
breaking and placing, fire and lava, mob griefing, and damage to citizens. Entry point is
`core/colony/permissions/ColonyPermissionEventHandler.java`, whose class comment at `:77-91` lists
what the port lost. **That list is accurate as far as it goes and incomplete as a map of colony
protection** — three more protections died in `core/event/EventHandler.java`, and one whole
permission (`PLACE_BLOCKS`) is quietly unenforced without being on anybody's list (§3).

## The one rule

**This port has zero mixins, by decision.** The only widening mechanism is
`src/main/resources/minecolonies.accesswidener` (128 lines, `official` namespace). So each gap below
ends one of two ways:

* **✅ reachable** — a named Fabric API callback or vanilla hook that closes it, plus a line estimate.
* **❌ crossed out** — one sentence naming the hook that does not exist. No mixin is designed,
  costed, or argued for. `26.2/BLAST-PROTECTION.md` §4 already costed the one mixin somebody may
  eventually want; this report does not re-litigate it.

## Evidence labels

* **[LIVE]** — measured on a dedicated Fabric 26.2 server booted for this report (§0).
* **[CODE]** — read out of the source or the decompiled vanilla; not shown in game.
* **[UNCHECKED]** — inference, said out loud as inference.

There is no game client in this container, so **nothing client-side was exercised at all** and no
claim below rests on what a screen showed.

---

## 0. The stand

Isolated copy of `/home/user/fabric-server-26.2` at `/home/user/srv-protection-limits`, **port
26293**, `mods/` holding exactly two jars (`fabric-api-0.154.2+26.2.jar` and the 0.0.39 build of this
worktree), stdin fed from a fifo so the console takes commands. Boot: `Done (1.889s)!`, **0 errors,
11 warnings** — the ENV baseline is 10, and the extra one is the mod's own compat-scan banner.

There is no player, and a colony normally needs one. The way round it is the port's own
`/mc colony territory` command (`core/commands/colonycommands/CommandColonyTerritory.java:435-455`),
which takes an explicit position and so runs from the console:

```
forceload add 96 96 160 160
fill 100 100 100 140 100 140 minecraft:stone
setblock 120 101 120 minecolonies:blockhuttownhall
mc colony territory create "TestT" 120 101 120     -> colony id 2
mc colony territory grow 2 2                       -> "TestT took 25 more chunks"
```

A hostile territory **is an ordinary `Colony`** for every purpose in this report: it is created
through `IServerColonySaveData#createColony`, it gets a `ColonyPermissionEventHandler` from
`Colony#onWorldLoad` (`core/colony/Colony.java:1451-1463`) like any other, and
`Colony#isCoordInColony` (`:1775-1785`) answers off the chunk claim. So "inside a claimed chunk of a
live colony with `enablecolonyprotection = true`" is exactly the condition under test. Config as
shipped: `enablecolonyprotection = true`, `turnoffexplosionsincolonies = "DAMAGE_ENTITIES"`.

---

## 1. Explosions

### 1.1 What upstream did

`ColonyPermissionEventHandler` on 1.21.1 hooked both halves of the NeoForge explosion event:

* `ExplosionEvent.Start` (`1.21.1/.../ColonyPermissionEventHandler.java:345-354`) — cancelled the
  explosion outright when the policy was `DAMAGE_NOTHING` and the centre was in the colony.
* `ExplosionEvent.Detonate` (`:301-338`) — filtered the affected lists. `DAMAGE_ENTITIES` (**the
  shipped default**) stripped every block inside the colony from `getAffectedBlocks()`.
  `DAMAGE_PLAYERS` additionally stripped every non-player entity; `DAMAGE_NOTHING` stripped every
  entity including hostiles.

Read plainly: **on the default config, upstream made every block of a colony immune to every
explosion in the game.** Creepers, TNT, beds, respawn anchors, end crystals, wither skulls, ghast
fireballs and other mods' bombs all funnelled through that one event. [CODE]

### 1.2 What this port does

`turnoffexplosionsincolonies` and `Action.EXPLODE` are read in exactly one place —
`core/compatibility/simpleplanes/SimplePlanesBlastGuard.java:190` and `:214`. [CODE, by grep for
`turnOffExplosionsInColonies` and `Action.EXPLODE` across `26.2/src/main/java`: the only other hits
are the config declaration, the permissions table and the `/mc colony blastprotection` command.]

That guard is reached only through Simple Planes' own `BlastGuards` seam, i.e. only for blasts that
Simple Planes itself produces. Everything else in the game reaches
`ServerExplosion#explode()` without passing anything of ours.

**Fabric API ships no explosion hook.** Unpacking `fabric-api-0.154.2+26.2.jar` and all **43** nested
jars yields **no class anywhere with `explos` in its name** — the only near-misses in the whole set
are `FireBlockHooks` and `FireBlockMixin` in the content-registry module, which register
flammability, not explosions. [VERIFIED — I ran the unpack and the enumeration myself for this
report, rather than quoting `BLAST-PROTECTION.md`'s second-hand claim.]

### 1.3 Measured

All three shots fired inside the claimed chunks of colony 2, oak-plank floor laid at y=101, town hall
at `120 101 120`:

| shot | command | plank at the blast | town hall |
|---|---|---|---|
| **TNT** | `summon minecraft:tnt 122.5 102 120.5 {fuse:20s}` | **destroyed** | survived |
| **creeper** | `summon minecraft:creeper 118.5 102 118.5 {ignited:true,fuse:20s}` | **destroyed** | survived |
| **end crystal** | `summon minecraft:end_crystal` + `damage … 5` | **destroyed** | survived |

[LIVE] — markers `MARK_HUT_SURVIVED`/`MARK_PLANK_DESTROYED`, `MARK_C_*`, `MARK_X_*` in the server
log. Three different explosion origins (block, mob, entity), same answer each time.

The town hall surviving is **not** colony protection: `AbstractColonyBlock.RESISTANCE =
Float.POSITIVE_INFINITY` (`api/blocks/AbstractColonyBlock.java:76`, applied at `:107`). Every hut
block and every rack has it; nothing else in a colony does. A colony is overwhelmingly vanilla blocks
placed from a blueprint, and those are exactly as fragile as they are anywhere else.

### 1.4 Verdict per explosion source

| source | protected in this port? | note |
|---|---|---|
| creeper | **no** | [LIVE] |
| TNT / TNT minecart | **no** | [LIVE] for TNT |
| end crystal | **no** | [LIVE] |
| bed in the Nether / End, respawn anchor in the Overworld | **no** | [CODE] — same `ServerExplosion#explode()` path as the three above |
| ghast fireball, wither skull, breeze wind charge | **no** | [CODE] — same path |
| Simple Planes crash / strike / gunship | **yes** | via `SimplePlanesBlastGuard`, see `BLAST-PROTECTION.md` |
| Simple Planes **payload bomb** | **no** | `PayloadUpgrade#dropAsPayload` spawns a vanilla primed TNT, which explodes on its own account [CODE, from `BLAST-PROTECTION.md` §1] |

**❌ The block half cannot be restored without a mixin.** The gate on block removal is
`ServerExplosion#interactsWithBlocks()`, private, with no callback in front of it, and Fabric API has
no explosion event to substitute.

### 1.5 …but the entity half **is** reachable — ✅

This is the one thing in the explosion story nobody has claimed yet, and the current design document
explicitly gives it up. `BLAST-PROTECTION.md` §2.3 says of the plane guard:

> `DAMAGE_PLAYERS` … this seam cannot express it: it decides what the explosion *is* before vanilla
> casts a single ray, and it has no say over which entities the rays then hit.

The permission handler already holds a hook that has exactly that say:
`ServerLivingEntityEvents.ALLOW_DAMAGE`, registered at `ColonyPermissionEventHandler.java:210-219`
and currently used for one narrow case. Vanilla tags explosion damage —
`DamageTypeTags.IS_EXPLOSION` (`/opt/mc-src/net/minecraft/tags/DamageTypeTags.java:19`) — so the
entity half of the old policy is a filter on a callback the mod is already subscribed to:

* `DAMAGE_NOTHING` → veto explosion damage to any entity standing in the colony;
* `DAMAGE_PLAYERS` → veto it for everything that is not a `ServerPlayer`;
* `DAMAGE_ENTITIES` / `DAMAGE_EVERYTHING` → abstain.

**≈25 lines** inside `allowDamage`, no new subscription, no new config. It restores the citizen- and
livestock-protecting half of the two strict policies for **every** explosion in the game — creeper,
TNT, bed, crystal — while the block half stays lost. It is also strictly *more* faithful than the
plane seam, which had to degrade `DAMAGE_PLAYERS` into `DAMAGE_ENTITIES`. [CODE]

Honest caveat: on the shipped default (`DAMAGE_ENTITIES`) this changes nothing, because that policy
never protected entities upstream either. It only pays off for servers that set the config stricter —
and today those servers get *nothing at all* for setting it. [CODE]

---

## 2. Block breaking

### 2.1 By a player — restored, and faithfully

`PlayerBlockBreakEvents.BEFORE` (`ColonyPermissionEventHandler.java:162-171`) carries the whole of
upstream's `BlockEvent.BreakEvent` handler: `BREAK_HUTS` for hut blocks and decoration controllers,
`BREAK_BLOCKS` for everything else, town-hall `getValidBreak()`, `pvp_mode` colony deletion,
`building.destroy()`. Line for line against `1.21.1/.../ColonyPermissionEventHandler.java:239-294`
the only difference is the plumbing (return value instead of `setCanceled`). **No regression.**
[CODE]

Fabric injects `BEFORE` into `ServerPlayerGameMode#destroyBlock`
(`net.fabricmc.fabric.mixin.event.interaction.ServerPlayerGameModeMixin#breakBlock`), which is the
same method NeoForge fires `BlockEvent.BreakEvent` from. Creative instant-break goes through it too.
[CODE, from the fabric-api bytecode.]

### 2.2 By other players' machinery — parity, and the parity is bad in both

A quarry, a block-breaker or any other mod's machine that calls `Level#destroyBlock` or
`Level#removeBlock` directly is **not** seen — on Fabric because `PlayerBlockBreakEvents` only fires
from `ServerPlayerGameMode`, on NeoForge because `BlockEvent.BreakEvent` only fires from the same
place. Machinery that drives a `FakePlayer` through `ServerPlayerGameMode#destroyBlock` *is* seen,
and `EntityUtils.getPlayerOfFakePlayer` (`isActionDenied`, `:500`) resolves it to the owning player
exactly as upstream did. **No regression; the hole was already there.** [CODE]

### 2.3 By everything else

Pistons, falling anvils, water washing away torches, wither skulls, ravagers — none of these were
hooked upstream and none are hooked now. [CODE, by grep: `1.21.1/src/main/java` contains **zero**
references to `PistonEvent`, `NeighborNotifyEvent`, `LivingDestroyBlockEvent`,
`EntityMobGriefingEvent` or `FluidPlaceBlockEvent`.]

---

## 3. Block placing — the gap nobody has written down

### 3.1 What upstream did

`BlockEvent.EntityPlaceEvent` (`1.21.1/.../ColonyPermissionEventHandler.java:108-120`) checked
`PLACE_HUTS` for hut blocks and `PLACE_BLOCKS` for everything else, on **every** placement by an
entity — hand, build tool, dispenser, fake player. [CODE]

### 3.2 What this port does

`Action.PLACE_BLOCKS` is enforced in exactly three places, all of them MineColonies' own items:
`ItemSupplyChestDeployer.java:254`, `ItemSupplyCampDeployer.java:220`, `ItemAssistantHammer.java:83`
(plus `PlayerAssistantBuildRequestMessage.java:52`). **There is no general placement check at all.**
`Action.PLACE_HUTS` survives on the two blueprint paths — `AbstractBlockHut#canPaste`
(`api/blocks/AbstractBlockHut.java:316-348`, reached from `:242`) and
`EventHandler#onBlockHutPlaced` (`core/event/EventHandler.java:726-758`, reached from
`core/placementhandlers/main/SurvivalHandler.java:155`) — but **not** on placing a hut block straight
out of the hotbar. [CODE, by grep for both constants over `26.2/src/main/java`.]

Two consequences, and they are different sizes.

**(a) A hut block placed by hand into somebody else's colony is not checked, and registers a building
in the victim's colony.** `AbstractColonyBlock#setPlacedBy`
(`api/blocks/AbstractColonyBlock.java:275-301`) resolves the colony from the world position and calls
`colony.getServerBuildingManager().addNewBuilding(hut, worldIn)` with **no permission test whatever**
— upstream relied entirely on `EntityPlaceEvent` having cancelled the placement first. Same file,
same code, on 1.21.1. [CODE] Player-visible: **yes, loudly** — an intruder can plant huts inside a
colony he is not a member of.

**(b) `PLACE_BLOCKS` is dead as a permission.** Look at the default rank table
(`core/colony/permissions/Permissions.java:164-208`): `OFFICER` and above get `PLACE_BLOCKS`;
`FRIEND` deliberately does not. Today a `FRIEND` may place any block anywhere in the colony. [CODE]

A `NEUTRAL` player — the rank a random stranger gets — is blocked, but **by accident**: `NEUTRAL`
holds only `ACCESS_TOGGLEABLES` and `MAP_BORDER`, so it fails the `RIGHTCLICK_BLOCK` test at
`ColonyPermissionEventHandler.java:381-384`, `UseBlockCallback` returns `FAIL`, and Fabric's
`ServerPlayerGameModeMixin#interactBlock` short-circuits `useItemOn` before `BlockItem#place` ever
runs. The stranger is stopped; he is stopped for the wrong reason, with the wrong denial recorded in
the town hall log (`RIGHTCLICK_BLOCK`, not `PLACE_BLOCKS`), and he is un-stopped the moment an owner
grants `RIGHTCLICK_BLOCK`. [CODE]

### 3.3 ✅ Reachable — two hooks, both already half-wired

* **`UseBlockCallback`** — already registered at `:175`. Adding, before the `RIGHTCLICK_BLOCK` test,
  "if the held stack is a `BlockItem` and the block is an `AbstractBlockHut` → `PLACE_HUTS`, else
  → `PLACE_BLOCKS`" restores both permissions for hand placement, which is the case that matters.
  **≈20 lines**, no new subscription.
* **`ItemEvents.USE_ON`** (`net.fabricmc.fabric.api.event.player.ItemEvents#USE_ON`, present in
  `fabric-events-interaction-v0-5.2.6`, injected around `ItemStack#useOn(UseOnContext)` by
  `ItemStackMixin`) — fires for **any** caller of `ItemStack#useOn`, not only
  `ServerPlayerGameMode`, so it also catches fake players driven by other mods. **≈15 lines** on top
  of the above, and it is the closest thing this loader has to `EntityPlaceEvent`. [CODE — I read
  both classes out of the fabric-api jar; neither is used by the port today.]

Neither reaches dispensers or falling blocks. That residue is **❌** — there is no vanilla or Fabric
callback in front of `DispenseItemBehavior`.

---

## 4. Fire and lava

### 4.1 Neither version protects a colony from fire

Grep for `doFireTick`, `FireBlock`, `FluidPlaceBlockEvent` over `1.21.1/src/main/java`: the only hit
in either tree is `PathfindingUtils` treating fire as dangerous to walk on. **Upstream had no fire
protection and no lava protection, and neither does this port.** [CODE] So there is no regression
here — but the ground under the question moved, and that is worth writing down.

### 4.2 What 26.2 changed

`doFireTick` is gone. Fire spread and lava ignition now both gate on
`ServerLevel#canSpreadFireAround(BlockPos)`
(`/opt/mc-src/net/minecraft/server/level/ServerLevel.java:1816-1819`):

```java
int spreadRadius = this.getGameRules().get(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER);
return spreadRadius == -1 || this.chunkSource.chunkMap.anyPlayerCloseEnoughTo(pos, spreadRadius);
```

Default **128**, `-1` meaning "everywhere"
(`/opt/mc-src/net/minecraft/world/level/gamerules/GameRules.java:37`). Three callers:
`FireBlock#tick:136`, `LavaFluid#randomTick:80`, `LightningBolt:154`. [CODE]

**This is a real gain for a colony, and an odd one.** In `FireBlock#tick` the radius test wraps the
*entire* body: with no player within 128 blocks the block reschedules its tick and does nothing else.
So an unattended colony does not burn down — **and the fire that is already in it does not go out
either.** It freezes, at whatever age it had, until a player walks back into range, and then resumes.
Lava behaves the same: outside the radius it never ignites a flammable neighbour.

Measured twice, zero players online both times. A 15×15 oak-plank floor at y=101 and roof at y=103,
one fire block lit at `177 102 177`:

* after ~45 s — fire present, floor plank present, roof plank present, and
  `fill 170 102 170 184 102 184 stone replace fire` reported **`1 block(s)`**: the fire had not
  spread to a single one of the other 224 cells of that plane;
* relit and left for **5 minutes** — fire still present, and `replace fire` over the **whole**
  15×15×3 volume again reported **`1 block(s)`**, with all 450 planks intact.

[LIVE — markers `MARK_G_*`, `MARK_H_FIRE_STILL_THERE` in the server log.] An earlier 50 s run in a
different part of the world gave the same answer.

### 4.3 What that means for the report's question

"Unattended" now means *safe*, and "attended" means exactly as dangerous as 1.21.1. A colony whose
owner is standing in it is fully exposed to fire; a colony he has walked away from cannot burn. The
mod's own citizens do **not** count — `anyPlayerCloseEnoughTo` walks `ChunkMap`'s tracked
**players**, and an `EntityCitizen` is not a player. [CODE] So a fully staffed colony with no human
present is fire-proof, which is not a sentence anyone would have written on purpose.

**❌ Colony-scoped fire protection is not reachable.** There is no Fabric callback in front of
`FireBlock#tick` or `LavaFluid#randomTick`, and the gamerule is a global radius, not a region.
The one honest half-measure that *is* mixin-free costs zero lines: tell server owners that
`/gamerule fire_spread_radius_around_player 0` disables fire spread outright, world-wide.

---

## 5. Mob damage to blocks

**Upstream hooked none of it, and neither does this port** — `1.21.1/src/main/java` contains zero
references to `EnderMan`, `Ravager`, `WitherBoss`, `Silverfish`, `EntityMobGriefingEvent` or
`LivingDestroyBlockEvent` outside of unrelated imports. [CODE, by grep.] Every line below is
therefore **parity, not regression**, and every one of them is **❌** for the same reason: 26.2 routes
all of it through the global `mob_griefing` gamerule
(`/opt/mc-src/net/minecraft/world/level/gamerules/GameRules.java:57`) with no per-region hook, and
NeoForge's `EntityMobGriefingEvent`, which *was* the per-region hook on the other loader, has no
Fabric counterpart.

| threat | what it can actually take | hut blocks? |
|---|---|---|
| enderman | anything in `#minecraft:enderman_holdable` — grass, dirt, sand, gravel, flowers | **no** — hut blocks are not in that tag, and the mod ships no tag file that adds them [CODE, by search over `src/main/resources/data`] |
| ravager | `#minecraft:leaves`, plus crops it walks over (`CropBlock:163`, `PitcherCropBlock:123`) | no |
| silverfish | infests stone/deepslate/cobble variants | no |
| zombie | `BreakDoorGoal`, hard difficulty only, wooden doors | no — but a blueprint's doors are ordinary wooden doors |
| wither | `WitherBoss#canDestroy` (`/opt/mc-src/.../WitherBoss.java:347`), blast resistance under 3.41 | no — `Float.POSITIVE_INFINITY` |

The single line that matters for a colony owner: **hut blocks and racks survive all of it; the
blueprint around them does not.** [CODE]

The `mob_griefing` gamerule is a blunt but free world-wide answer, and it is the only one available.

---

## 6. Mobs spawning inside the colony — a protection the class comment does not mention

The permission handler's comment lists six casualties. **It is not the only file that lost one.**
`core/event/EventHandler.java` carries three methods that are complete, correct, and **never called**;
the port marks them honestly as "degradation ladder step 2", which is why they are easy to miss —
they are documented in the file that lost them, not in the file the reader starts from.

| method | was | consequence |
|---|---|---|
| `EventHandler#isSpawnBlockedByBuilding` (`:386-413`) | `MobSpawnEvent.PositionCheck` | **hostile mobs spawn inside colony buildings again** |
| `EventHandler#shouldPreventCropTrample` (`:800-808`) | `BlockEvent.FarmlandTrampleEvent` | the `SOFT_SHOES` research no longer stops farmers trampling their own crops |
| `EventHandler#onEntityConverted` (`:822-…`) | `LivingConversionEvent.Pre` | curing a zombie villager in a colony with a tavern no longer recruits a visitor |

[CODE, by grep: each of the three has exactly one occurrence in `26.2/src/main/java` — its own
declaration.]

The first is a protection, and a good one: upstream refused every natural hostile spawn whose
position fell inside a built building of level ≥ 1 (`1.21.1/.../EventHandler.java:310-344`). Losing it
means zombies spawning in the dark corner of your own warehouse. Player-visible: **yes**.

### ✅ Reachable, and without a mixin — via the AccessWidener

The port's file comment says "vanilla 26.2 exposes no hook either". That is true of *events* and
false of *data*. Vanilla's spawn predicate table is an ordinary mutable map:

```java
// /opt/mc-src/net/minecraft/world/entity/SpawnPlacements.java
private static final Map<EntityType<?>, SpawnPlacements.Data> DATA_BY_TYPE = Maps.newHashMap();   // :54
private record Data(Heightmap.Types heightMap, SpawnPlacementType placement,
                    SpawnPlacements.SpawnPredicate<?> predicate) {}                                // :188

public static <T extends Entity> boolean checkSpawnRules(
    EntityType<T> type, ServerLevelAccessor level, EntitySpawnReason spawnReason,
    BlockPos pos, RandomSource random) {                                                           // :82-91
    …
    SpawnPlacements.Data data = DATA_BY_TYPE.get(type);
    return data == null || data.predicate.test(type, level, spawnReason, pos, random);
}
```

and `NaturalSpawner` consults it on the natural-spawn path
(`/opt/mc-src/net/minecraft/world/level/NaturalSpawner.java:259`, and again at `:386` for structure
spawners). [CODE]

`register` refuses a duplicate, but nothing stops a `put`. Three AccessWidener lines —

```
accessible	field	net/minecraft/world/entity/SpawnPlacements	DATA_BY_TYPE	Ljava/util/Map;
accessible	class	net/minecraft/world/entity/SpawnPlacements$Data
accessible	method	net/minecraft/world/entity/SpawnPlacements$Data	<init>	(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/world/entity/SpawnPlacementType;Lnet/minecraft/world/entity/SpawnPlacements$SpawnPredicate;)V
```

— plus a loop at server-start over every `EntityType` whose entity class is `Enemy`, replacing its
`Data` with the same heightmap and placement type and a predicate that delegates to the original and
then `&& !(reason != SPAWNER && isSpawnBlockedByBuilding(level, pos))`. **≈50 lines of Java and 3
AccessWidener lines**, and `isSpawnBlockedByBuilding` is already written and already correct — it is
one call away from being live again.

Two honest caveats. Spawns that skip `SpawnPlacements#checkSpawnRules` and go through
`Mob#checkSpawnRules` (`NaturalSpawner:283`, `:405` — chunk-generation spawns) are not covered;
upstream's `PositionCheck` covered them. And the swap must run after the class's static initialiser,
i.e. from a server-lifecycle callback rather than a static block. [CODE / [UNCHECKED] on the second
caveat — not built.]

---

## 7. The four item permissions the class comment lists

`TOSS_ITEM`, `PICKUP_ITEM`, `FILL_BUCKET`, `SHOOT_ARROW`. The comment is right that all four lost
their event; it is wrong to leave the impression that all four are equally unreachable. **Two of the
four fall out of a callback the file already subscribes to.**

### ✅ `FILL_BUCKET` — ≈8 lines

Upstream hooked `VanillaGameEvent` / `GameEvent.FLUID_PICKUP`
(`1.21.1/.../ColonyPermissionEventHandler.java:614-625`). On Fabric, filling a bucket is
`BucketItem#use` → `ItemStack#use` → `ServerPlayerGameMode#useItem`, which is precisely where
`UseItemCallback` fires — and the port already registers it (`:177-186`, dispatching to `onItemUse`).
`onItemUse` already branches on `stack.getItem() instanceof PotionItem`; one more branch on
`BucketItem` with `Action.FILL_BUCKET` restores it. [CODE]

Deviation to state: upstream checked the *fluid's* position, `onItemUse` checks
`player.blockPosition()`. For a colony-boundary test at bucket range that is the same answer in all
but pathological cases. [UNCHECKED]

### ✅ `SHOOT_ARROW` — ≈8 lines

Upstream hooked `ArrowLooseEvent`, i.e. the *release*. `BowItem#use` / `CrossbowItem#use` are the
*draw*, and they go through the same `UseItemCallback` as above; a branch on `ProjectileWeaponItem`
denies the draw. [CODE]

Deviation to state, and it is visible: the player will not be able to nock the arrow at all, rather
than drawing and having the shot swallowed. That is arguably better feedback and is certainly a
different feel.

### ❌ `TOSS_ITEM`

Dropping is `ServerGamePacketListenerImpl#handlePlayerAction` with `DROP_ITEM` / `DROP_ALL_ITEMS`;
neither Fabric API nor vanilla exposes a callback there.

### ❌ `PICKUP_ITEM`

Pickup is `ItemEntity#playerTouch`; Fabric API has no callback in front of it
(`PlayerPickItemEvents` is middle-click pick-block, not item pickup). An approximation exists —
holding `ItemEntity#setPickUpDelay` open from a tick handler — but it denies the item to *everyone*
including its owner, which is not the rule upstream enforced, so it is not a restoration.

---

## 8. Damage to citizens, and damage by them

### 8.1 `HURT_CITIZEN` / `HURT_VISITOR` — never went through an event, so never broke

These two permissions are enforced inside the entities themselves:
`EntityCitizen#checkIfValidDamageSource` (`core/entity/citizen/EntityCitizen.java:1333-1376`, called
from `hurtServer` at `:1293`) and `VisitorCitizen.java:190-195`. Identical to
`1.21.1/.../EntityCitizen.java:1320-1333`. A player without `HURT_CITIZEN` still cannot deal more
than 1 damage to a citizen; guards during a raid still take damage from anyone. **No regression.**
[CODE]

Note what this does *not* cover, in either version: `checkIfValidDamageSource` returns `true` for
every non-player, non-citizen source. A creeper blast, a fall, a lava pool and a zombie all hurt
citizens exactly as much inside a colony as outside it. [CODE]

### 8.2 `ATTACK_CITIZEN` / `ATTACK_ENTITY` — restored

`AttackEntityCallback` (`:199-208` → `onAttackEntity`, `:544-581`) is a faithful port of
`AttackEntityEvent`, including the guard/hostile-rank exception and the `Monster` early-out. [CODE]

### 8.3 The one behavioural deviation, and it is already documented in the file

Upstream's `LivingDamageEvent.Pre` handler set the damage to `0.0f` when a colony's own guard hit a
non-hostile player during a raid. Fabric's `ServerLivingEntityEvents.ALLOW_DAMAGE` can only veto the
hit outright (`:530-537`). Same visible outcome — the player takes nothing — but the hit is now
*cancelled* rather than *zeroed*, so no hurt animation, no knockback and **no invulnerability
frames**, which means the guard may connect again on the very next tick instead of on the next
half-second. Whether anyone notices is [UNCHECKED]; it is a difference, and the port already says so
at `:525-527`.

### 8.4 Mobs targeting citizens

`mobattackcitizens` (default `true`) still installs the two `NearestAttackableTargetGoal`s on every
hostile that joins the level (`core/event/EventHandler.java:170-180`). The port note at `:160-167`
records what *did* break in the move from `EntityJoinLevelEvent` to `ServerEntityEvents.ENTITY_LOAD`:
the event was cancellable and fired *before* the entity entered the level, so the two guards that
used to cancel a join now `discard()` instead, and the "duplicate `AbstractFastMinecoloniesEntity`"
guard degenerates into "always true" and had to go. Neither of those is colony protection against an
outside threat; noted for completeness. [CODE]

---

## 9. Auditing the class comment, line by line

The comment at `ColonyPermissionEventHandler.java:77-91` was the starting map. Checked, not trusted:

| claim in the comment | verdict |
|---|---|
| `BlockEvent.EntityPlaceEvent` — PLACE_BLOCKS / PLACE_HUTS gone | **true, and understated.** It does not say that hand-placing a hut into someone else's colony still *registers a building there* (§3.2a), nor that `FRIEND` rank is now effectively `OFFICER` for building purposes (§3.2b) |
| `ExplosionEvent.Start` / `.Detonate` — partly restored via Simple Planes | **true.** Confirmed live for three vanilla explosion types (§1.3). What it misses is that the **entity** half of the policy is reachable from a callback this very file already subscribes to (§1.5) |
| `ItemTossEvent` — TOSS_ITEM gone | **true**, and unreachable (§7) |
| `ItemEntityPickupEvent.Pre` — PICKUP_ITEM gone | **true**, and unreachable (§7) |
| `VanillaGameEvent` FLUID_PICKUP — FILL_BUCKET gone | **true, but reachable in ≈8 lines** from `UseItemCallback`, already registered at `:177` (§7) |
| `ArrowLooseEvent` — SHOOT_ARROW gone | **true, but reachable in ≈8 lines** from the same callback (§7) |
| "the protections they carried are, **in full**" | **false as a statement about colony protection.** Three more died in `core/event/EventHandler.java` (§6), and one — hostile mobs spawning inside buildings — is a protection of the same weight as the ones listed |

Everything else in the file is a faithful port. Verified equivalent against 1.21.1, no behaviour lost:
`BREAK_BLOCKS`, `BREAK_HUTS`, `ACCESS_HUTS`, `ACCESS_TOGGLEABLES`, `RIGHTCLICK_BLOCK`,
`RIGHTCLICK_ENTITY`, `THROW_POTION`, `USE_SCAN_TOOL`, `ATTACK_CITIZEN`, `ATTACK_ENTITY`, the
free-block / free-position exceptions, `pvp_mode`, the ten-second denial cooldown and the levitation
punishment. [CODE]

### One quiet narrowing: `OPEN_CONTAINER`

Upstream asked NeoForge's capability system whether the block exposes an item handler on **any**
face (`1.21.1/.../ColonyPermissionEventHandler.java:397-409`). The port replaced that with
`blockEntity instanceof Container` (`:386-394`) and says so at `:387`. For vanilla chests, barrels,
hoppers and furnaces the two agree. **They do not agree for a modded inventory that exposes items
without implementing vanilla `Container`** — that block is protected on NeoForge and unprotected
here.

**✅ Reachable, ≈6 lines.** `fabric-transfer-api-v1` is inside the fabric-api the mod already
depends on and ships `net.fabricmc.fabric.api.transfer.v1.item.ItemStorage`, whose `SIDED` lookup is
the exact Fabric counterpart of `Capabilities.ItemHandler.BLOCK`. `isContainer` becomes
`blockEntity instanceof Container || ItemStorage.SIDED.find(level, pos, state, blockEntity, null) != null`
(plus the six-direction loop upstream had). The port uses nothing from that package today. [CODE]

---

## 10. Ranked — what to do, most protection per line first

Reachable without a mixin: **7 items.** Crossed out: **8 items.**

| # | item | § | cost | what it buys |
|---|---|---|---|---|
| **1** | **`PLACE_BLOCKS` / `PLACE_HUTS` on `UseBlockCallback`** | 3.3 | **≈20 lines**, no new subscription | Stops an intruder planting huts in a colony he does not belong to, and makes `FRIEND` mean `FRIEND` again. This is the **best line-for-line buy in the report**: it closes a permission that is currently enforced nowhere, using a callback that is already registered, and it turns an accidental denial (via `RIGHTCLICK_BLOCK`) into the intended one. |
| **2** | **Hostile spawns blocked inside buildings** | 6 | ≈50 lines + 3 AccessWidener lines | Restores `isSpawnBlockedByBuilding`, which is already written and already correct. Highest *player-felt* value of anything here — zombies in your own warehouse is a thing every player meets on night one. Costs the report's only AccessWidener change, which is the sanctioned mechanism. |
| **3** | `FILL_BUCKET` + `SHOOT_ARROW` on `UseItemCallback` | 7 | ≈16 lines for both | Two of the four "gone in full" permissions come back inside a callback the file already answers. Cheapest items on the list. |
| **4** | Explosion policy, entity half, on `ALLOW_DAMAGE` | 1.5 | ≈25 lines | Makes `turnoffexplosionsincolonies` mean *something* for vanilla explosions for the first time, and expresses `DAMAGE_PLAYERS` exactly — which the Simple Planes seam cannot. Ranked below the first three only because it is inert on the shipped default. |
| **5** | `PLACE_*` also on `ItemEvents.USE_ON` | 3.3 | ≈15 lines on top of #1 | Extends #1 to fake players driven by other mods. Do it with #1 or not at all. |
| **6** | `OPEN_CONTAINER` via `ItemStorage.SIDED` | 9 | ≈6 lines | Closes modded inventories that are not vanilla `Container`s. Zero cost, narrow audience. |
| **7** | Say the two gamerules out loud in the docs | 4.3, 5 | 0 lines | `fire_spread_radius_around_player 0` and `mob_griefing false` are the only levers that exist for §4 and §5. They are global and blunt, and a server owner who does not know they exist has nothing at all. |

### ❌ Crossed out — the list, one sentence each

1. **Block damage from every vanilla explosion.** The gate is `ServerExplosion#interactsWithBlocks()`, private, and fabric-api 0.154.2+26.2 contains no class with `explos` in its name.
2. **Colony-scoped fire spread.** No callback in front of `FireBlock#tick`; the only lever is the world-wide `fire_spread_radius_around_player` gamerule.
3. **Colony-scoped lava ignition.** Same gate, `LavaFluid#randomTick:80`.
4. **Mob griefing** — enderman, ravager, silverfish, zombie doors, wither. All read the global `mob_griefing` gamerule directly; NeoForge's `EntityMobGriefingEvent` has no Fabric counterpart.
5. **`TOSS_ITEM`.** Dropping is `ServerGamePacketListenerImpl#handlePlayerAction`, with no callback in front of it.
6. **`PICKUP_ITEM`.** Pickup is `ItemEntity#playerTouch`, with no callback in front of it.
7. **Crop trample / `SOFT_SHOES`.** No hook in front of `FarmlandBlock#fallOn`.
8. **Block placement by dispensers and falling blocks.** No callback in front of `DispenseItemBehavior`.

---

## 11. What I did not verify

* **Nothing client-side.** There is no display in this container, `runClient` does not start, and no
  player ever connected to the test server. Every claim about what a *player* sees — the denial
  message, the levitation punishment, whether a cancelled placement desyncs the client's hand — is
  inference.
* **No test involved a real colony member.** Rank-dependent claims (§3.2b: a `FRIEND` may place
  blocks) are read off `Permissions.java` and the callback chain, not demonstrated. Demonstrating
  them needs a connected client, which is out of reach here.
* **The enderman experiment was inconclusive**, and the reason is worth recording so the next person
  does not repeat it: four persistent endermen were kept for ~11 minutes, across six arrangements of
  grass floor, grass field, grass-walled pen and grass corridor, inside the claimed chunks and at
  midnight with `mob_griefing = true`, and took nothing at all. `EnderManTakeBlockGoal#tick`
  (`/opt/mc-src/net/minecraft/world/entity/monster/EnderMan.java:582-604`) picks a target at
  `yt = floor(getY() + random × 3)` — never *below* the mob — so an enderman standing **on** a flat
  grass floor can never pick that floor up. It needs blocks at or above its own feet within ±2. The
  underlying claim (§5) does not depend on the experiment: neither tree hooks mob griefing at all.
* **Beds, respawn anchors, ghast fireballs and wither skulls** were not detonated; they are asserted
  to share `ServerExplosion#explode()` with the three that were. [CODE]
* **None of the seven ✅ items was built or compiled.** The line counts are estimates from the shape
  of the surrounding code, not from a diff.
* Whether `SpawnPlacements$Data` can in fact be widened by AccessWidener as §6 describes — the entry
  syntax is standard, but the build was not run with it. [UNCHECKED]

---

## 12. Reproducing the stand

```sh
cp -r /home/user/fabric-server-26.2 /home/user/srv-protection-limits
rm /home/user/srv-protection-limits/mods/minecolonies*.jar
sed -i 's/^server-port=.*/server-port=26293/' /home/user/srv-protection-limits/server.properties
/home/user/mc-build.sh <this worktree>/26.2 build
cp <this worktree>/26.2/build/libs/minecolonies-26.2-0.0.39.jar /home/user/srv-protection-limits/mods/
mkfifo cmd.fifo
tail -f cmd.fifo | java -Xmx3G -jar fabric-server-launch.jar nogui > server.log 2>&1 &
```

Then the console script of §0, followed by the three explosion shots of §1.3 and the fire setup of
§4.2. Every assertion is made with `execute if block … run say MARK_…` and read back out of the log,
so the whole thing is greppable rather than eyeballed. The server directory was deleted afterwards.
