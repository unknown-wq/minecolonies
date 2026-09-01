# Cavalry on vanilla's kinetic-weapon attack path

Research only. Date: 2026-08-27. Tree: `26.3/src/` on branch `26.3`. **No feature code was written**;
everything below is source reading. Nothing was built and nothing was run.

## Verdict

**Yes — it is possible without a mixin, and without an access widener either.** Every method vanilla's
spear path needs on the attacker is `public` on `LivingEntity` or `Mob`, and vanilla itself already drives
that path from a plain `Mob`: `SpearUseGoal` is a `Goal` that a `Zombie` runs
(`mc/world/entity/monster/zombie/Zombie.java:132`), and it does nothing more exotic than
`mob.startUsingItem(InteractionHand.MAIN_HAND)`. A citizen is a `PathfinderMob`, and this mod already
calls `startUsingItem` on citizens in five places today. **The access-widener answer is zero lines.**

That is the easy half. The hard half is that **plumbing the path in buys almost nothing on its own**,
because vanilla's stab does damage only while the attacker is *moving*, and the guard AI's whole design is
to close to attack distance and then stop. Concretely: an iron spear's damage condition needs
`relativeSpeed >= 4.6 * 0.2 = 0.92` blocks/s of closing speed for a non-player wielder
(`mc/world/item/component/KineticWeapon.java:166`, `mc/world/item/Items.java:2343`). A cavalryman standing
still to swing has a closing speed of zero and would deal **no damage at all**. So the real work is not the
attack hook, it is replacing the guard's approach-and-stop movement with a charge-and-withdraw cycle. That
is where the lines are.

**Line-count headline: 260 lines at the floor, ~570 at the realistic ceiling**, across five or six existing
files plus possibly one new one. The breakdown and its basis are §5. What decides where in the range is a
single design choice — how the mod's damage scaling is reconciled with vanilla's formula (§5.3), which has
three answers costing 0, ~60 and ~40 lines and having wildly different blast radii.

**And the payoff is negative on damage.** Per hit, an iron-speared cavalryman charging at the mod's own
documented 7.9 blocks/s top speed would deal `3 + floor(7.9 * 0.95) = 10` damage. That is what he deals
*today* with the same spear (§6). What he would lose is rate: today he swings every 16–32 ticks; on the
vanilla path he lands roughly one stab per charge-and-reposition cycle, on the order of 90 ticks. **The
honest summary is that this is a large, invasive change that reduces cavalry damage output and removes
cavalry from the colony's damage-progression system entirely.** The one thing it genuinely buys that
nothing else can is **knockback and dismount** — a charging cavalryman would knock riders off mounts
(§1.6), which the mod has no other route to. If that is the goal, say so and scope the work to it; if the
goal is "charging should hurt more", this is the wrong mechanism and a `CAVALRY_CHARGE_MULTIPLIER` term in
`getAttackDamage` keyed off `user.getRootVehicle().getKnownSpeed()` is about fifteen lines.

---

## Evidence standard

Same as `docs/studies/worldmap-chunk-generation.md`:

* **[VERIFIED]** — I opened the file and the cited `file:line` says what I claim.
* **[UNVERIFIED]** — inference I did not confirm. Each one names what would confirm it.

Paths: `26.3/src/main/java/com/minecolonies/` is abbreviated to `mcol/`; vanilla sources are read from
`/opt/mc-src-26.3-snapshot-10/net/minecraft/`, abbreviated to `mc/`.

**Version, stated up front.** The tree I read is `/opt/mc-src-26.3-snapshot-10`, and
`mc/SharedConstants.java:16` reads `public static final int WORLD_VERSION = 5015`. Snapshot-10 is 5015 and
snapshot-9 is 5011, so this is the right tree and the right build — the trap that caught the earlier
world-map study (a directory named `26.3` that was in fact snapshot-9) does not apply here. **[VERIFIED]**

Nothing in this study was executed. Every damage figure is arithmetic over cited constants, not a
measurement. Where a number depends on runtime state I could not observe — actual closing speed at the
moment of contact, alignment of the look vector with the horse's heading, how long a target stays inside
the sweep during a pass — it is marked and the range is given.

---

## 0. What I read

**Vanilla, end to end:** `mc/world/item/component/KineticWeapon.java` (all 177 lines),
`mc/world/entity/ai/goal/SpearUseGoal.java` (all 171), `mc/world/entity/ai/behavior/SpearAttack.java` (all
109), `mc/world/item/component/AttackRange.java`, `mc/world/item/component/PiercingWeapon.java`.

**Vanilla, in part:** `mc/world/entity/LivingEntity.java` (the use-item block 3388–3597, `stabAttack`
2846–2903, `hurtServer` 1171–1240, `tick` 2728–2732, `getAttackRangeWith` 2208, `swing` 2014–2035,
`onKineticHit` 2149–2157, `postPiercingAttack` 1698), `mc/world/entity/Mob.java` (`doHurtTarget` 1370–1392,
`swingForAttack` 1465, `chargeSpeedModifier` 1461), `mc/world/entity/Entity.java` (`computeSpeed` 592–599,
`getKnownSpeed` 4135, `baseTick` 536–539, `getLookAngle`/`getHeadLookAngle` 2646/2654,
`isPassengerOfSameVehicle` 3678), `mc/world/item/Item.java` (`use` 203–226, `getUseAnimation` 313–322,
`getUseDuration` 324–331, `Properties#spear` 510–568), `mc/world/item/ItemStack.java` (`onUseTick`
1107–1119, `hurtEnemy` 534–546, `postHurtEnemy` 548–553), `mc/world/item/Items.java` (2334–2352),
`mc/world/item/ToolMaterial.java`, `mc/world/entity/projectile/ProjectileUtil.java` (38–145),
`mc/world/item/enchantment/EnchantmentHelper.java` (`modifyDamage` 188–194).

**Mod, end to end:** `mcol/core/entity/ai/workers/guard/MeleeCombatAI.java` (638),
`mcol/core/entity/ai/workers/guard/CavalryCombatAI.java` (80),
`mcol/core/entity/ai/combat/AttackMoveAI.java` (219).

**Mod, in part:** `mcol/core/entity/ai/combat/TargetAI.java`,
`mcol/core/entity/other/cavalry/CavalryHorseEntity.java` (the ride-input and steering blocks),
`mcol/core/entity/citizen/EntityCitizen.java` (`hurtServer`, `checkIfValidDamageSource`),
`mcol/api/entity/citizen/AbstractEntityCitizen.java` (attributes), `mcol/api/util/constant/GuardConstants.java`,
`mcol/core/colony/jobs/guard/JobCavalry.java`, `mcol/core/entity/ai/workers/guard/RangeCombatAI.java`
(the bow-hold precedent), `mcol/core/client/render/RenderUtils.java`,
`mcol/api/util/ItemStackUtils.java`, `mcol/api/equipment/ModEquipmentTypes.java`,
`26.3/src/main/resources/minecolonies.accesswidener`, `26.3/src/main/resources/minecolonies.mixins.json`.

---

## 1. What vanilla's path actually is

### 1.1 The entry point is `startUsingItem`, and it is public

`LivingEntity#startUsingItem(InteractionHand)` is `public` at `mc/world/entity/LivingEntity.java:3468`.
It stores the stack in `this.useItem`, sets `useItemRemaining = itemStack.getUseDuration(this)`, raises the
"using item" data flag, and — this is the part specific to spears — allocates the stab-memory map:

```java
if (this.useItem.has(DataComponents.KINETIC_WEAPON)) {
    this.recentKineticEnemies = new Object2LongOpenHashMap<>();
}
```

(`LivingEntity.java:3477–3479`) **[VERIFIED]**. That allocation only happens on the server
(`!this.level().isClientSide()`, line 3473) and only through `startUsingItem`, which matters: without it
`rememberStabbedEntity` silently no-ops (`LivingEntity.java:2854–2858`) and `wasRecentlyStabbed` always
returns false (2846–2852), so every target would be stabbed every single tick. There is no other way to
allocate it. **[VERIFIED]**

For a player, `Item#use` is what calls `startUsingItem` — `mc/world/item/Item.java:216–220` reads the
`KINETIC_WEAPON` component, calls `player.startUsingItem(hand)` and plays the weapon's sound. **That branch
is `Player`-typed and unreachable for a mob** (`Item#use(Level, Player, InteractionHand)`, line 203).
**[VERIFIED]** Mobs bypass it entirely and call `startUsingItem` directly; see §3.

### 1.2 The hold has a duration of 72000 ticks and is driven by the entity tick

`Item#getUseDuration` returns `72000` for anything carrying `BLOCKS_ATTACKS` or `KINETIC_WEAPON`
(`Item.java:324–331`) **[VERIFIED]** — i.e. effectively "until released". So a couched spear does not
expire on its own; something has to call `stopUsingItem`.

Every tick, `LivingEntity#tick()` calls `this.updatingUsingItem()` as its second statement
(`LivingEntity.java:2728–2731`) **[VERIFIED]**. `updatingUsingItem` is `private` (3396) but needs no
caller of ours: it checks `isUsingItem()`, re-reads the held stack, verifies it is still the same item and
calls `updateUsingItem` (3396–3405). `updateUsingItem` is `protected` (3441) and calls
`useItem.onUseTick(this.level(), this, this.getUseItemRemainingTicks())` (3442). **[VERIFIED]**

`ItemStack#onUseTick` is public and contains the whole hook:

```java
KineticWeapon kineticWeapon = this.get(DataComponents.KINETIC_WEAPON);
if (kineticWeapon != null && !level.isClientSide()) {
    kineticWeapon.damageEntities(this, ticksRemaining, livingEntity, livingEntity.getUsedItemHand().asEquipmentSlot());
}
```

(`mc/world/item/ItemStack.java:1107–1118`) **[VERIFIED]**. Note it is server-only and note it *replaces*
the item's own `onUseTick` rather than running alongside it.

**Consequence that decides the whole design: nothing in the mod ever has to call `damageEntities`. Calling
`startUsingItem` once is sufficient; the entity tick does the rest for as long as the hold lasts.**

### 1.3 `damageEntities`: the sweep, the cooldown, the damage

`KineticWeapon#damageEntities` is public, `mc/world/item/component/KineticWeapon.java:99–144`.
**[VERIFIED]** In order:

1. `int ticksUsed = stack.getUseDuration(livingEntity) - ticksRemaining;` — counts *up* from 0 (line 100).
   Nothing happens until `ticksUsed >= this.delayTicks` (101); then `ticksUsed -= this.delayTicks` (102).
2. `Vec3 attackerLookVector = livingEntity.getLookAngle();` (103) — body/head yaw and pitch of the
   attacker, **not** `getHeadLookAngle`.
3. `double attackerSpeedProjection = attackerLookVector.dot(getMotion(livingEntity));` (104).
4. `float actionFactor = livingEntity instanceof Player ? 1.0F : 0.2F;` (105) — **every speed threshold is
   five times easier for a mob than for a player.**
5. `AttackRange attackRange = livingEntity.getAttackRangeWith(stack);` (106).
6. `double baseMobDamage = livingEntity.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);` (107) — the
   attribute's **base value**, so item and equipment modifiers are excluded. This is the single most
   consequential line in the whole study; see §4.2.
7. The sweep: `ProjectileUtil.getHitEntitiesAlong(livingEntity, attackRange, e -> PiercingWeapon.canHitEntity(livingEntity, e), ClipContext.Block.COLLIDER)`
   (110–113) — **all** entities along the ray, not just the nearest.
8. Per target: `livingEntity.wasRecentlyStabbed(otherEntity, this.contactCooldownTicks)` (119) gates
   re-hits; `rememberStabbedEntity` records the game time (121).
9. `double targetSpeedProjection = attackerLookVector.dot(getMotion(otherEntity));` (122) and
   `double relativeSpeed = Math.max(0.0, attackerSpeedProjection - targetSpeedProjection);` (123) — a
   target running *at* the attacker projects negative, so a head-on collision adds to the closing speed.
10. Three independent conditions tested against `(ticksUsed, attackerSpeedProjection, relativeSpeed, actionFactor)`:
    dismount (124–125), knockback (126–127), damage (128–129).
11. `float damageDealt = (float)baseMobDamage + Mth.floor(relativeSpeed * this.damageMultiplier);` (131),
    then `livingEntity.stabAttack(equipmentSlot, otherEntity, damageDealt, dealsDamage, dealsKnockback, dealsDismount)` (132).
12. If anything was affected: `broadcastEntityEvent(livingEntity, (byte)2)` (138), and the advancement
    trigger for `ServerPlayer` only (139–141).

`Condition#test` is `ticksUsed <= maxDurationTicks && attackerSpeed >= minSpeed * entityFactor && relativeSpeed >= minRelativeSpeed * entityFactor`
(`KineticWeapon.java:165–167`) **[VERIFIED]**.

### 1.4 `getMotion` and where the mounted case comes from

```java
public static Vec3 getMotion(Entity livingEntity) {
    if (!(livingEntity instanceof Player) && livingEntity.isPassenger()) {
        livingEntity = livingEntity.getRootVehicle();
    }
    return livingEntity.getKnownSpeed().scale(20.0);
}
```

(`KineticWeapon.java:78–84`) **[VERIFIED]**. The briefing's claim is confirmed exactly: a **non-player**
passenger is measured by its root vehicle. A player passenger is measured by himself.

`Entity#getKnownSpeed` returns `this.getControllingPassenger() instanceof Player controller && this.isAlive() ? controller.getKnownSpeed() : this.lastKnownSpeed`
(`mc/world/entity/Entity.java:4135–4137`) **[VERIFIED]**. For a citizen-ridden horse the controlling
passenger is not a Player, so it is the horse's own `lastKnownSpeed`.

`lastKnownSpeed` is set in `Entity#computeSpeed`: `this.lastKnownSpeed = this.position().subtract(this.lastKnownPosition);`
(`Entity.java:592–599`), called from `Entity#baseTick` (536–539) **[VERIFIED]**. So it is
**displacement per tick**, and the `scale(20.0)` in `getMotion` converts it to blocks per second. Every
threshold in `Item.Properties#spear` is therefore in blocks/second.

### 1.5 The hit sweep, and who is in it

`ProjectileUtil.getHitEntitiesAlong(Entity, AttackRange, Predicate, ClipContext.Block)`
(`mc/world/entity/projectile/ProjectileUtil.java:38–47`) **[VERIFIED]**:

```java
Vec3 look = attacker.getHeadLookAngle();
Vec3 eyePosition = attacker.getEyePosition();
Vec3 from = eyePosition.add(look.scale(attackRange.effectiveMinRange(attacker)));
double movementComponent = attacker.getKnownMovement().dot(look);
Vec3 to = eyePosition.add(look.scale(attackRange.effectiveMaxRange(attacker) + Math.max(0.0, movementComponent)));
```

Note the sweep uses `getHeadLookAngle` while the speed projection in §1.3 uses `getLookAngle` — the two are
different vectors (`Entity.java:2646` vs `2654`, the second built from `getYHeadRot()`). **[VERIFIED]**
Note also the reach is *extended by forward motion*, so a charge reaches further than a standstill.

`AttackRange#effectiveMaxRange` halves the reach for a non-player: `this.maxReach * this.mobFactor`
(`mc/world/item/component/AttackRange.java:96–102`) **[VERIFIED]**. Every vanilla spear carries
`new AttackRange(2.0F, 4.5F, 2.0F, 6.5F, 0.125F, 0.5F)` (`Item.java:549`), so a mob's spear reaches
**1.0 to 2.25 blocks from the eyes**, plus forward-motion extension, plus a 0.125 hitbox margin.
**[VERIFIED]**

The filter is `PiercingWeapon.canHitEntity(livingEntity, e)`
(`mc/world/item/component/PiercingWeapon.java:57–69`) **[VERIFIED]**:

```java
if (target.isInvulnerableToPiercingWeapon() || !target.isAlive()) return false;
else if (target instanceof Interaction) return true;
else if (!target.canBeHitByProjectile()) return false;
else return target instanceof Player targetPlayer && jabber instanceof Player jabbingPlayer && !jabbingPlayer.canHarmPlayer(targetPlayer)
    ? false : !jabber.isPassengerOfSameVehicle(target);
```

Two things follow. **The rider's own mount is excluded**, because `isPassengerOfSameVehicle` is
`this.getRootVehicle() == other.getRootVehicle()` (`Entity.java:3678–3680`) and for a citizen on a horse
both sides evaluate to the horse. **[VERIFIED]** But **there is no ally filter of any kind** — the sweep
would return every living thing along the ray, colony animals and other colonies' citizens included. For
same-colony citizens the mod already stops the damage on the receiving side:
`EntityCitizen#checkIfValidDamageSource` returns false when the source entity is an `EntityCitizen` of the
same colony (`mcol/core/entity/citizen/EntityCitizen.java:1349–1357`) **[VERIFIED]**. Colony *animals*
have no such protection. **[UNVERIFIED that a charging cavalryman would in practice spit a cow — confirming
means running a charge past livestock and watching.]**

`getHitEntitiesAlong` clips against blocks first and returns `Either.left(blockHit)` if the wall is nearer
than `from` (`ProjectileUtil.java:96–102`), and `damageEntities` maps a `left` to an empty list
(`KineticWeapon.java:113`). So a spear will not stab through a wall. **[VERIFIED]**

### 1.6 `stabAttack`: what a hit actually does

`LivingEntity#stabAttack(EquipmentSlot, Entity, float, boolean, boolean, boolean)` is public,
`mc/world/entity/LivingEntity.java:2864–2903`. **[VERIFIED]** Server-only (2867). In order: read the
weapon from the slot; `DamageSource damageSource = weaponItem.getDamageSource(this)` (2871) — **the item's
own `DAMAGE_TYPE` component, which for a vanilla spear is `DamageTypes.SPEAR`** (`Item.java:525`);
enchantment damage modification (2872); `target.hurtServer(serverLevel, damageSource, postEnchantmentDamage)`
if `dealsDamage` (2875); two knockback impulses if `dealsKnockback` (2877–2880);
`target.stopRiding()` if `dismounts` and the target is a passenger not tagged
`CANNOT_BE_DISMOUNTED_BY_ITEM_USAGE` (2882–2885); `weaponItem.hurtEnemy(livingTarget, this)` (2888);
post-attack enchantment effects (2891–2893); `setLastHurtMob` and `playAttackSound` (2899–2900).

Two absences matter:

* **`postHurtEnemy` is never called.** Durability wear lives there —
  `weapon.itemDamagePerAttack()` → `hurtAndBreak` (`mc/world/item/ItemStack.java:548–553`) **[VERIFIED]** —
  and the only callers are `PiercingWeapon#attack` (via `attacker.postPiercingAttack()`,
  `PiercingWeapon.java:84`), `Mob#doHurtTarget` (`Mob.java:1390`) and `Player` (`Player.java:984`).
  **[VERIFIED]** So **a kinetic stab costs the spear no durability at all.** Vanilla spears do carry
  `DataComponents.WEAPON` (`Item.java:567`), it is just never consulted on this path.
* **No swing.** `stabAttack` does not call `swing`. The visual is the couched-spear pose plus entity
  event 2 → `LivingEntity#onKineticHit`, which plays a local hit sound at most once every 10 ticks
  (`LivingEntity.java:2149–2157`). **[VERIFIED]**

### 1.7 The cooldown, twice over

There are two independent gates and they interact.

* `contactCooldownTicks` is 10 for every vanilla spear (`Item.java:529`) **[VERIFIED]**. That is a
  per-attacker/per-target memory, so a held spear can re-stab the same target once every 10 ticks.
* `LivingEntity#hurtServer` sets `damageCooldownTime = 20` on a full hit and, while
  `damageCooldownTime > 10`, applies only the *excess* over `lastHurt`
  (`mc/world/entity/LivingEntity.java:1211–1226`) **[VERIFIED]**. `damageCooldownTime` decrements once per
  tick (477–478).

Ten ticks after a full hit, `damageCooldownTime` is exactly 10 and the strict `> 10` test is false, so the
next stab lands in full. **The two windows are tuned to line up: a held spear against a stationary target
can theoretically land 2 full-damage hits per second.** **[UNVERIFIED that the tick alignment holds in
practice — the two counters advance in different parts of the tick and one tick of drift halves the rate;
confirming means instrumenting a live stab.]**

### 1.8 The spear numbers, for the record

`Item.Properties#spear(material, attackDuration, damageMultiplier, delay, dismountTime, dismountThreshold,
knockbackTime, knockbackThreshold, damageTime, damageThreshold)` at `mc/world/item/Item.java:510–568`
**[VERIFIED]** builds:

* `contactCooldownTicks = 10`, `delayTicks = (int)(delay * 20)`, `forwardMovement = 0.38F`
* `dismountConditions = Condition.ofAttackerSpeed((int)(dismountTime*20), dismountThreshold)`
* `knockbackConditions = Condition.ofAttackerSpeed((int)(knockbackTime*20), knockbackThreshold)`
* `damageConditions   = Condition.ofRelativeSpeed((int)(damageTime*20), damageThreshold)`
* `ATTACK_RANGE(2.0, 4.5, 2.0, 6.5, 0.125, 0.5)`, `ATTACK_ANIMATION = SwingAnimation(STAB, attackDuration*20)`,
  `DAMAGE_TYPE = DamageTypes.SPEAR`, `WEAPON = new Weapon(1)`,
  `ATTACK_DAMAGE` modifier `= 0.0F + material.attackDamageBonus()`

Iron: `.spear(IRON, 0.95F, 0.95F, 0.6F, 2.5F, 11.0F, 6.75F, 5.1F, 11.25F, 4.6F)`
(`mc/world/item/Items.java:2343`) **[VERIFIED]**, `ToolMaterial.IRON.attackDamageBonus() == 2.0F`
(`mc/world/item/ToolMaterial.java:26`) **[VERIFIED]**. Resolved, with the mob factor of 0.2 applied:

| quantity | iron spear | as a mob threshold |
|---|---|---|
| `delayTicks` | 12 | — |
| damage window | `ticksUsed - 12 <= 225` | 11.25 s of couched spear |
| damage needs | `relativeSpeed >= 4.6` | **>= 0.92 blocks/s closing** |
| knockback window | `<= 135` | 6.75 s |
| knockback needs | `attackerSpeed >= 5.1` | **>= 1.02 blocks/s** |
| dismount window | `<= 50` | 2.5 s |
| dismount needs | `attackerSpeed >= 11.0` | **>= 2.20 blocks/s** |
| `damageMultiplier` | 0.95 | — |
| `computeDamageUseDuration()` | `12 + 225 = 237` ticks | the engagement length |

The briefing's "about 0.92 blocks/s" is exactly right. **[VERIFIED]**

---

## 2. What the mod's path is

### 2.1 The state machine

`AttackMoveAI<T extends Mob & IThreatTableEntity> extends TargetAI<T>`
(`mcol/core/entity/ai/combat/AttackMoveAI.java:23`) registers two transitions in `ATTACKING`:
`tryAttack` every 5 ticks and `move` every 10 (46–47). **[VERIFIED]**

`tryAttack` (133–154) **[VERIFIED]**: bail if no target or `!canAttack()`; bail if
`nextAttackTime >= gameTime` or `!isInDistanceForAttack(target)`; then, if there is line of sight,
`user.getLookControl().setLookAt(target); doAttack(target); nextAttackTime = gameTime + getAttackDelay();`

`move` (55–107) **[VERIFIED]** is `private` (line 55) — **it cannot be overridden by a subclass.** Its
governing test is line 79: `if (!isInAttackDistance(target) || !canSeeTarget)`, and only inside that branch
does it path. **Once the guard is inside attack distance, `move` does nothing at all.** That single line is
the reason a plumbing-only change cannot work: a cavalryman in range stops moving, closing speed goes to
zero, and vanilla's damage condition fails.

### 2.2 `MeleeCombatAI#doAttack`

`mcol/core/entity/ai/workers/guard/MeleeCombatAI.java:322–391` **[VERIFIED]**. In order: optionally take a
step in (328–331); `user.swingForAttack(InteractionHand.MAIN_HAND)` (333); play `SPEAR_ATTACK` or
`PLAYER_ATTACK_SWEEP` (334–336); compute `getAttackDamage()` (338); build a
`DamageSourceKeys.GUARD` source, or `GUARD_PVP` if `pvp_mode` and the target is a Player (339–343); fire
aspect (345–349); the whirlwind AoE on a cooldown (351–354); then the hit —

```java
target.hurt(source, (float) damageToBeDealt);
```

(line 364; the huscarl branch at 359–360 splits it between `PIERCE` and `GUARD`). Then
`setLastHurtByMob` (367), the `KNIGHT_TAUNT` research retarget (369–376), **`user.stopUsingItem()` (378)**,
the visible status (379), `CitizenItemUtils.damageItemInHand(user, MAIN_HAND, 1)` (380), and the spear
recoil step (387–390).

Line 378 is the second structural blocker: **every melee swing tears down any item use in progress.**

### 2.3 `getAttackDamage`

`MeleeCombatAI.java:463–538` **[VERIFIED]**. Branches on the held item — `ItemTags.SWORDS` reads the
entity attribute `user.getAttribute(Attributes.ATTACK_DAMAGE).getValue()` (473); the mod's own `ItemSpear`
uses its own field (477); `ItemTags.SPEARS` reads the item's own `ATTACK_DAMAGE` modifier plus
`BASE_PHYSICAL_DAMAGE` (489–490); `ItemTags.AXES` likewise (513–514); everything else falls through to
Tinkers (518). Then, still inside the weapon branch:

```java
addDmg += EnchantmentHelper.modifyDamage((ServerLevel) user.level(), heldItem, target, user.level().damageSources().mobAttack(user), (float) addDmg);
```

(line 520). Then `MELEE_DAMAGE` research (523), a ×2 below 20 % health (526–529), a ×1.5 `GUARD_CRIT` roll
(531–535), and `× guardDamageMultiplier` (537).

**An aside that is not this study's subject but bears on its arithmetic.** `EnchantmentHelper.modifyDamage`
returns the *full* damage, not the enchantment delta — `MutableFloat result = new MutableFloat(damage); ...; return result.floatValue();`
(`mc/world/item/enchantment/EnchantmentHelper.java:188–194`) **[VERIFIED]**. So line 520 as written is
`addDmg += addDmg`, i.e. **an unconditional doubling of every melee guard's weapon damage**, enchanted or
not. `26.2/audit/GUARD-AUDIT.md:437` records a *measured* "knight with a netherite sword — 10.0", and 10.0
is exactly the pre-line-520 value (citizen base 3.0 + netherite sword modifier 7.0; the sword modifier is
`attackDamageBaseline 3.0 + NETHERITE.attackDamageBonus 4.0`, `mc/world/item/Items.java:1593–1594` and
`mc/world/item/ToolMaterial.java:29` **[VERIFIED]**). The audit's whole table is pre-line-520.
**[UNVERIFIED whether the doubling is live in 26.3 — the code reading is unambiguous but the only
measurement on record disagrees with it; confirming means one `axecalc`-style probe on a running server.]**
§6 gives the payoff both ways.

### 2.4 Timing, distance, and what `CavalryCombatAI` overrides

`getAttackDelay()` = `max(KNIGHT_ATTACK_DELAY_BASE - Adaptability/(huscarl ? 2 : 3), KNIGHT_ATTACK_DELAY_MIN)`
(`MeleeCombatAI.java:546–552`), with `KNIGHT_ATTACK_DELAY_BASE = 32` and `KNIGHT_ATTACK_DELAY_MIN = 16`
(`mcol/api/util/constant/GuardConstants.java:113,118`). **[VERIFIED]** So one swing per 16–32 ticks.

`CavalryCombatAI` (`mcol/core/entity/ai/workers/guard/CavalryCombatAI.java`, 80 lines) **[VERIFIED]**
overrides exactly four things: `getAttackDamage()` × `CAVALRY_DAMAGE_MULTIPLIER` (29–34, and the constant
is `1.00`, `GuardConstants.java:133`), `getWeaponType()` → `JobCavalry.getWeaponType()` = the spear
equipment type (41–45, `mcol/core/colony/jobs/guard/JobCavalry.java:190`), `getAttackDistance()` =
`max(super * CAVALRY_RANGE_MULTIPLIER, getSpearReach())` (62–68, multiplier `1.20`,
`GuardConstants.java:138`), and the combat icon (75–79). **It overrides nothing about how the attack is
delivered.**

`MeleeCombatAI#usesSpearFootwork()` is deliberately `!user.isPassenger() && isUsingSpear()`
(`MeleeCombatAI.java:236–239`) **[VERIFIED]** — the existing spear footwork is switched *off* while
mounted, by design, with the comment saying mounted combat was left alone.

### 2.5 Where the two paths would have to meet

Precisely three places.

1. **`MeleeCombatAI.java:378`** — `user.stopUsingItem()` at the end of every `doAttack`. Must not run for a
   charging cavalryman.
2. **`AttackMoveAI.java:79`** — `if (!isInAttackDistance(target) || !canSeeTarget)`. Must be inverted for a
   charging cavalryman, or the horse stops and the damage condition fails. `move` is `private`, so this
   means either changing `AttackMoveAI` (one visibility keyword, or a new overridable predicate) or lying
   in `isInAttackDistance` — and `isInAttackDistance` also feeds `isInDistanceForAttack` (172–175) and
   hence `tryAttack`'s gate, so lying there has knock-on effects.
3. **The off-hand.** `startUsingItem` refuses outright if `isUsingItem()` is already true
   (`mc/world/entity/LivingEntity.java:3470`) **[VERIFIED]**, and only one item can be in use at a time.
   Three mod sites grab the off-hand with a shield: `MeleeCombatAI#attackProtect` (139),
   `JobCavalry#ignoresDamage` (`JobCavalry.java:131`) — which fires on **every** explosion or projectile
   hit — and `AbstractEntityAIGuard.java:721`, which calls `worker.stopUsingItem()` every guard tick when
   the fight timer has run out. **[VERIFIED]** All three would cancel a charge.

---

## 3. Can a `Mob` drive the vanilla path?

**Yes, completely, with no widener.** This is the crux and the answer is unambiguous, for two independent
reasons.

### 3.1 Everything needed is public

| what | where | visibility |
|---|---|---|
| `LivingEntity#startUsingItem(InteractionHand)` | `mc/world/entity/LivingEntity.java:3468` | `public` |
| `LivingEntity#stopUsingItem()` | `:3585` | `public` |
| `LivingEntity#releaseUsingItem()` | `:3572` | `public` |
| `LivingEntity#isUsingItem()` | `:3388` | `public` |
| `LivingEntity#getUsedItemHand()` | `:3392` | `public` |
| `LivingEntity#getUseItem()` | `:3556` | `public` |
| `LivingEntity#getUseItemRemainingTicks()` | `:3560` | `public` |
| `LivingEntity#getTicksUsingItem()` | `:3564` | `public` |
| `LivingEntity#stabAttack(...)` | `:2864` | `public` |
| `LivingEntity#getAttackRangeWith(ItemStack)` | `:2208` | `public` |
| `LivingEntity#wasRecentlyStabbed` / `rememberStabbedEntity` | `:2846` / `:2854` | `public` |
| `Mob#chargeSpeedModifier()` | `mc/world/entity/Mob.java:1461` | `public` |
| `Mob#swingForAttack(InteractionHand)` | `:1465` | `public` |
| `Entity#getKnownSpeed()` | `mc/world/entity/Entity.java:4135` | `public` |
| `KineticWeapon#damageEntities(...)` | `mc/world/item/component/KineticWeapon.java:99` | `public` |
| `KineticWeapon#computeDamageUseDuration()` | `:95` | `public` |
| `KineticWeapon#makeSound(Entity)` | `:86` | `public` |
| `ItemStack#onUseTick(...)` | `mc/world/item/ItemStack.java:1107` | `public` |
| `DataComponents.KINETIC_WEAPON` | `mc/core/component/DataComponents.java:219` | `public` |

**[VERIFIED — each line opened.]**

Only two members of the machinery are not public, and **neither needs to be called**:
`LivingEntity#updatingUsingItem()` is `private` (3396) and `LivingEntity#updateUsingItem(ItemStack)` is
`protected` (3441). Both are reached automatically from `LivingEntity#tick()` (2731). A citizen inherits
that tick unmodified — `AbstractFastMinecoloniesEntity extends PathfinderMob`
(`mcol/api/entity/other/AbstractFastMinecoloniesEntity.java:27`), and grepping the four classes in the
citizen hierarchy for `tick()`/`baseTick()` overrides finds **none** (only `aiStep` overrides at
`AbstractEntityCitizen.java:457` and `EntityCitizen.java:725`, both of which call `super`). **[VERIFIED]**

**Access widener lines required: none.** The one `Player`-only thing in the area is `Item#use`
(`mc/world/item/Item.java:203`), and it is not on the path — it is merely the player's *trigger*.

### 3.2 Vanilla itself already does exactly this from a `Goal`

`SpearUseGoal<T extends Monster> extends Goal` (`mc/world/entity/ai/goal/SpearUseGoal.java:16`)
**[VERIFIED]**, added by `Zombie#registerGoals` at priority 2
(`mc/world/entity/monster/zombie/Zombie.java:132`) and by `ZombifiedPiglin` at priority 1
(`ZombifiedPiglin.java:72`). **[VERIFIED]** Its `tick()` (79–132) is the reference implementation of a
mob-driven charge:

* `canUse()` = has a target, main hand has `KINETIC_WEAPON`, and `!mob.isUsingItem()` (45–51).
* Approach: while `targetDistSqr > approachDistanceSq`, path at the reposition speed and return (94–97).
* Engage: `state.startEngagement(getKineticWeaponUseDuration()); this.mob.startUsingItem(InteractionHand.MAIN_HAND);` (99–100).
  The duration is `reducedTickDelay(KineticWeapon::computeDamageUseDuration)` (53–56) — 237 ticks for iron.
* Charge: `this.mob.getNavigation().moveTo(target, speedModifier * this.speedModifierWhenCharging)` and,
  once inside `targetInRangeRadiusSq` or the path finishes, pick a withdrawal point 6–7 blocks away
  (`+2` if mounted) via `LandRandomPos.getPosAway` (124–128).
* Withdraw, then `state.done` (112–122).
* `stop()` calls `this.mob.stopUsingItem()` (71–77).

And crucially for cavalry, **the mounted case is first-class in vanilla**: line 84 reads
`Entity mount = this.mob.getRootVehicle();` and lines 86–88 scale the speed by
`vehicleMob.chargeSpeedModifier()`, which `ZombieHorse` overrides to `1.4F`
(`mc/world/entity/animal/equine/ZombieHorse.java:200–203`) and `CamelHusk` also overrides
(`mc/world/entity/animal/camel/CamelHusk.java:130`). **[VERIFIED]** Line 90 adds
`int mountDistance = this.mob.isPassenger() ? 2 : 0;` to the withdrawal distances. The scenario vanilla
designed this for is a spear-armed zombie on a zombie horse — which is cavalry.

The brain-based equivalent is `SpearApproach`/`SpearAttack`/`SpearRetreat` (73/109/89 lines), wired into
`PiglinAi.java:180–182`. `SpearAttack#start` is likewise just
`body.startUsingItem(InteractionHand.MAIN_HAND)` (`mc/world/entity/ai/behavior/SpearAttack.java:55`).
**[VERIFIED]**

### 3.3 The mod already drives this machinery on citizens

`RangeCombatAI#canAttack` holds a bow open across ticks with exactly this API:

```java
if (nextAttackTime - BOW_HOLDING_DELAY >= user.level().getGameTime() && !user.isUsingItem()) {
    user.startUsingItem(InteractionHand.MAIN_HAND);
}
```

(`mcol/core/entity/ai/workers/guard/RangeCombatAI.java:126–129`) **[VERIFIED]**. There are eleven
`startUsingItem`/`stopUsingItem`/`releaseUsingItem` call sites on citizens across the tree. The
machinery demonstrably works on a citizen today.

**So: `startUsingItem` on a mounted, spear-carrying citizen would, with no further code, cause vanilla to
sweep and stab every tick from tick 12 to tick 237 of the hold. The mechanism is not in question. The
movement that makes it deal damage is.**

---

## 4. What breaks

Scoped to a change confined to `CavalryCombatAI` plus the three meeting points in §2.5.

### 4.1 Not touched

* **Ranged guards and druids.** `RangeCombatAI` and `DruidCombatAI` extend `AttackMoveAI` directly, not
  `MeleeCombatAI` (`RangeCombatAI.java:59`, `DruidCombatAI.java:54`). **[VERIFIED]** Unaffected unless
  `AttackMoveAI` itself is edited — and the only edit contemplated there is a visibility change on `move`
  or a new overridable predicate, neither of which changes behaviour.
* **Raiders.** `RaiderMeleeAI` and `RaiderRangedAI` also extend `AttackMoveAI` directly
  (`mcol/core/entity/mobs/aitasks/RaiderMeleeAI.java:36`, `RaiderRangedAI.java:33`). **[VERIFIED]**
  `MeleeCombatAI` has exactly one subclass in the whole tree and it is `CavalryCombatAI`. **[VERIFIED]**
* **Knights and huscarls**, provided the guards on the `MeleeCombatAI` meeting points are keyed on
  "mounted and charging" rather than on "holding a spear".
* **Kill credit, experience, statistics.** `TargetAI#checkForTarget` fires `onTargetDied` on
  `target != null && !target.isAlive()` (`mcol/core/entity/ai/combat/TargetAI.java:62–68`) **[VERIFIED]** —
  it does not care who landed the killing blow. `MeleeCombatAI#onTargetDied` (626–637) — action counter,
  `EXP_PER_MOB_DEATH`, `MOBS_KILLED` statistic, per-mob-type statistic, saturation — all survive intact.
* **The client-side pose.** `RenderUtils#getArmPose` already returns `HumanoidModel.ArmPose.SPEAR` when
  `entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0` and the animation is
  `ItemUseAnimation.SPEAR` (`mcol/core/client/render/RenderUtils.java:37–53`), and
  `RenderBipedCitizen.java:84–85` feeds both arms through it. **[VERIFIED]** A couched citizen would draw
  correctly for free. `Item#getUseAnimation` returns `SPEAR` for anything with `KINETIC_WEAPON`
  (`mc/world/item/Item.java:320`). **[UNVERIFIED that `ArmPose.SPEAR` looks right on the citizen model
  while seated on a horse — confirming means running the client.]**

### 4.2 Touched, and this is the serious one: damage scaling is lost entirely

`KineticWeapon#damageEntities` line 107 uses `getAttributeBaseValue(Attributes.ATTACK_DAMAGE)` — the
**base**, not `getValue()`. A citizen's base is registered once, as
`.add(Attributes.ATTACK_DAMAGE, BASE_PHYSICAL_DAMAGE)`
(`mcol/api/entity/citizen/AbstractEntityCitizen.java:169`), and `BASE_PHYSICAL_DAMAGE = 3`
(`GuardConstants.java:188`). **[VERIFIED]** Grepping the whole mod tree for `setBaseValue` on an attack
attribute finds only raider entities (`AbstractEntityMinecoloniesMonster.java:233` and the chief
subclasses); **nothing ever changes a citizen's `ATTACK_DAMAGE` base.** **[VERIFIED]**

Everything that makes a level-99 cavalryman better than a fresh recruit lives in
`MeleeCombatAI#getAttackDamage` and none of it reaches vanilla's formula: the weapon's own damage, the
`MELEE_DAMAGE` research, the `GUARD_CRIT` research, the below-20 %-health doubling, `guardDamageMultiplier`,
`CAVALRY_DAMAGE_MULTIPLIER`, and the enchantment term. **On the vanilla path a cavalryman deals
`3 + floor(relativeSpeed * multiplier)`, forever, at every colony level, with every research.** This is not
a rough edge; it is the single decision the whole design turns on. §5.3 costs the three ways out.

### 4.3 Touched: attack timing

The guard's `getAttackDelay()` model (16–32 ticks, scaling with Adaptability) stops governing. Vanilla's
gates are a fixed 10-tick per-target contact cooldown (§1.7) inside a 225-tick damage window, and in
practice the binding constraint is how often the charge cycle brings the target back inside 2.25 blocks of
the rider's eyes. The Adaptability skill would stop affecting cavalry attack speed.

### 4.4 Touched: durability and the request economy

`stabAttack` never calls `postHurtEnemy`, so **the spear takes no durability** (§1.6). Today
`MeleeCombatAI.java:380` calls `CitizenItemUtils.damageItemInHand(user, MAIN_HAND, 1)` on every swing.
A cavalryman on the vanilla path would never wear out a spear, so `EntityAIMelee`'s spear re-request logic
(`mcol/core/entity/ai/workers/guard/EntityAIMelee.java:98,137,161`) would never fire again for cavalry.
**[UNVERIFIED as an end-to-end consequence — confirming means watching a cavalry unit's spear durability
across a raid.]**

### 4.5 Touched: the damage source

`stabAttack` uses `weaponItem.getDamageSource(this)`, which for a vanilla spear is `DamageTypes.SPEAR`
(`Item.java:525`) **[VERIFIED]**. So `minecolonies:guard` is bypassed, and with it:

* the `entity.minecolonies.guard` death message (`mcol/core/generation/defaults/DefaultDamageTypeProvider.java:70`),
* PvP mode's `minecolonies:guardpvp`, which is in `DamageTypeTags.BYPASSES_ARMOR`
  (`mcol/core/generation/defaults/DefaultDamageTagsProvider.java:41`) **[VERIFIED]** — armoured players
  would suddenly take armour-reduced damage from cavalry,
* the huscarl `minecolonies:pierce` split, which is `BYPASSES_ARMOR` *and* `BYPASSES_SHIELD` (`:41,43`) —
  irrelevant to cavalry, but it is the same `doAttack` being bypassed.

Note also `DamageTypes.SPEAR` is not in `BYPASSES_COOLDOWN`, so §1.7's cooldown arithmetic applies.
**[UNVERIFIED that `minecraft:spear` carries no tag that changes this — confirming means reading the
vanilla damage-type tag data, which I did not.]**

### 4.6 Touched: the off-hand shield contention

Three sites grab the off-hand and start using a shield (§2.5 item 3). Because `startUsingItem` refuses when
`isUsingItem()` is already true (`LivingEntity.java:3470`), and because `stopUsingItem` clears
`recentKineticEnemies` (`LivingEntity.java:3588`) so a re-couch restarts the whole 12-tick delay, a
cavalryman with a shield in his pack and `SHIELD_USAGE` researched would spend a raid alternating between
blocking and re-starting a charge that never reaches its damage window. `JobCavalry#ignoresDamage:131` is
the worst of the three because it fires on every projectile hit taken. **[VERIFIED that the code paths
exist and conflict; UNVERIFIED that it produces the described thrash in play — confirming means running a
cavalry unit with a shield through arrow fire.]**

### 4.7 Touched: sounds, particles, the swing, the AoE, the taunt

* **Swing.** `stabAttack` never swings; the visual is the static couched pose plus the hit sound. The
  mod's `user.swingForAttack(...)` (`MeleeCombatAI.java:333`) would no longer run for cavalry. Whether
  that is a loss or a gain is a taste question — vanilla's spear is *meant* to look couched, and
  `SwingAnimation(STAB, ...)` on the item (`Item.java:551`) is only consumed by the non-kinetic
  `PiercingWeapon#attack` path (`PiercingWeapon.java:74,90`). **[VERIFIED]**
* **Use sound.** `KineticWeapon#makeSound` is played by `Item#use` for *players only* (`Item.java:219`).
  A mob's `startUsingItem` plays nothing. One line to add.
* **Hit sound.** `broadcastEntityEvent(livingEntity, (byte)2)` (`KineticWeapon.java:138`) →
  `LivingEntity#onKineticHit` → `makeLocalHitSound`, reading `this.useItem` on the client. The client's
  `useItem` is populated from the synced flag in `onSyncedDataUpdated`
  (`LivingEntity.java:3491–3500`) **[VERIFIED]**, so it should work for a citizen.
  **[UNVERIFIED — confirming means listening on a client.]**
* **Whirlwind AoE** (`doAoeAttack`, 409–456) and the **`KNIGHT_TAUNT` retarget** (369–376) live inside
  `doAttack` and would stop firing for cavalry unless re-hosted. Cavalry currently does get the whirlwind
  — `doAoeAttack` checks only the research, not the job.
* **Fire aspect** (345–349) likewise; though `stabAttack` does run `EnchantmentHelper.doPostAttackEffects`
  (`LivingEntity.java:2892`), so enchantment-driven effects survive in a different form.

### 4.8 Touched: friendly fire on non-citizens

§1.5. Same-colony citizens are protected on the receiving side; colony animals, visitors of other colonies
and neutral mobs along the ray are not.

---

## 5. The estimate

**Method.** I am counting lines of source *in this repository's style*, which is heavily commented —
`MeleeCombatAI.java` is 638 lines for roughly 250 lines of statement, a ratio of about 2.5:1, and
`CavalryHorseEntity.java` is 1177 lines at a similar ratio. Where vanilla has a working analogue I use its
length as the logic yardstick and apply that ratio; where it does not, I count against the nearest existing
method in the mod. Every figure below is *added or changed* lines, not file sizes.

### 5.1 Phase 1 — plumbing: hold the spear (50–80 lines)

Make a mounted cavalryman with a `KINETIC_WEAPON` in hand couch it, and stop the three sites that tear it
down.

| file | change | lines |
|---|---|---|
| `CavalryCombatAI.java` | `canAttack()` override that couches the spear when mounted and not already using an item — the shape of `RangeCombatAI.java:118–134`, which is 17 lines | 25–35 |
| `CavalryCombatAI.java` | `doAttack(...)` override that skips the mod's `hurt` and does not `stopUsingItem` | 15–25 |
| `MeleeCombatAI.java` | guard `attackProtect` (139) and `stopUsingItem` (378) on a new `isCharging()` predicate | 8–14 |
| `AbstractEntityAIGuard.java` | guard the out-of-combat `stopUsingItem` (721) | 4–8 |

**After this phase the feature does not work**, for the reason in §2.1: the guard stops moving in range and
`relativeSpeed` collapses to zero. This phase is only shippable bundled with Phase 2.

### 5.2 Phase 2 — the charge cycle (150–300 lines)

Replace approach-and-stop with approach → engage → charge through → withdraw → repeat, i.e. port
`SpearUseGoal#tick` onto the guard's `TickRateStateMachine` and the citizen's
`MinecoloniesAdvancedPathNavigate`.

Basis: `SpearUseGoal.java` is 171 lines total, of which the `SpearUseState` inner class is 37 and
constructor/fields are ~28. That is roughly 105 lines of actual cycle logic — and vanilla's version carries
none of the guard's obligations: patrol area, `isWithinPersecutionDistance`, the threat table, rally and
follow modes, the guard-task settings, losing the mount mid-charge, or the building-level speed bonus.
Against `MeleeCombatAI`'s comment ratio, 105 lines of logic is 250–260 lines of file; a lean version that
reuses `EntityNavigationUtils` and the existing `moveInAttackPosition`/`isInAttackDistance` seams instead of
re-deriving them comes in nearer 150.

| file | change | lines |
|---|---|---|
| `CavalryCombatAI.java` (or a new `CavalryChargeState.java`) | engage timer, withdrawal point, fleeing timer, and the four state predicates | 60–140 |
| `CavalryCombatAI.java` | `moveInAttackPosition`, `isInAttackDistance`, `isInDistanceForAttack`, `getCombatMovementSpeed` overrides for the charge | 70–130 |
| `AttackMoveAI.java` | make the "stop pathing once in range" rule overridable — either `private IState move()` → `protected`, or a new `protected boolean shouldHoldPosition(...)` seam at line 79 | 1–15 |
| `GuardConstants.java` | charge distances, withdrawal radii, engage timeout | 10–20 |

The `AttackMoveAI` line is the one to be careful with. Changing `move` to `protected` is a one-word diff
that touches nothing at runtime; lying in `isInAttackDistance` instead is a three-line trick with a long
tail, because the same method feeds `isInDistanceForAttack` (172–175) and the path-recompute test at line
99. Take the one-word diff.

### 5.3 Phase 3 — reconcile the damage model (0, ~40 or ~60 lines, and this decides the range)

Three mutually exclusive answers to §4.2.

**(a) Accept vanilla's numbers. 0 lines.** Cavalry deals `3 + floor(relativeSpeed × multiplier)` at every
colony level with every research. Cheapest and, in my view, not shippable: it silently removes one guard
type from the colony's progression system.

**(b) Write the mod's computed damage into the citizen's `ATTACK_DAMAGE` base for the duration of a charge.
~40–60 lines.** `AttributeInstance#setBaseValue` is public and the mod already uses it on raiders
(`AbstractEntityMinecoloniesMonster.java:233`). The hazards are real and cost most of the lines: the base
value is persisted in the entity's attribute save data, so a server stop, a chunk unload or a crash during
a charge leaves a permanently buffed guard, which needs a restore-on-load fix-up; and `getValue()` is what
`MeleeCombatAI.java:473` reads for the sword branch, so a dismounted cavalryman with a sword would be
reading a value the charge code wrote.

**(c) Keep the mod's `doAttack` as the damage and use the vanilla path only for knockback and dismount.
~20–40 lines of datagen.** Send `damage_conditions` unreachable via an item-component override on the
spear items. Cheap in Java, but it changes vanilla spears **for players too**, on this server, which is a
much wider blast radius than the feature justifies.

I would cost the realistic build at **(b), ~60 lines**, and note that (a) at 0 is what "the floor" means in
the headline.

### 5.4 Phase 4 — bookkeeping (40–100 lines)

| item | change | lines |
|---|---|---|
| §4.4 durability | wear the spear on a timer while charging, since there is no per-hit hook without a mixin | 8–15 |
| §4.5 damage source | either accept `minecraft:spear` (0) or a datagen override putting `minecolonies:guard` on the spears (20–30, wide blast radius) | 0–30 |
| §4.6 shield contention | make the three shield sites stand down for a charging cavalryman | 10–20 |
| §4.7 sounds | `kineticWeapon.makeSound(user)` at couch time; keep or drop the swing | 2–8 |
| §4.7 whirlwind / taunt | re-host or accept the loss for cavalry | 0–25 |
| §4.8 friendly fire | either accept, or pre-filter by aborting the charge when an ally is in the ray — but **there is no hook to filter vanilla's sweep**, so the only lever is aborting the whole charge | 20–30 |

Note the last row carefully. `damageEntities` takes its filter from `PiercingWeapon.canHitEntity`, a
`static` method with no extension point. Without a mixin **there is no way to add an ally filter to the
sweep.** The only available mitigations are aborting the charge before it starts, or protecting on the
receiving side the way `EntityCitizen#checkIfValidDamageSource` already does for citizens — which would
mean touching every colony-owned animal's damage handling, and is out of scope at any sane budget.

### 5.5 Phase 5 — client (0 lines, probably)

§4.1. `RenderUtils`/`RenderBipedCitizen` already handle `ItemUseAnimation.SPEAR`. **[UNVERIFIED that it
looks right mounted.]**

### 5.6 Totals

| | floor | realistic ceiling |
|---|---|---|
| Phase 1 plumbing | 50 | 80 |
| Phase 2 charge cycle | 150 | 300 |
| Phase 3 damage model | 0 (option a) | 60 (option b) |
| Phase 4 bookkeeping | 40 | 100 |
| Phase 5 client | 0 | 30 |
| **total** | **240** | **570** |

Rounded, and with the floor nudged up because option (a) is not really shippable: **260 to 570 lines**,
across `CavalryCombatAI.java` (the bulk, +150 to +350), `MeleeCombatAI.java` (+15 to +40),
`AttackMoveAI.java` (+1 to +15), `AbstractEntityAIGuard.java` (+5 to +10), `GuardConstants.java`
(+10 to +20), optionally one new `CavalryChargeState.java` (0 to +80) and optionally datagen (0 to +40).

**What decides where in the range:** (1) which of §5.3's three answers is chosen — the difference between
"cavalry stops scaling" at 0 lines and "cavalry keeps scaling" at ~60 with a save-persistence hazard;
(2) whether the charge cycle gets its own state object or is bolted onto the existing
`isInAttackDistance`/`moveInAttackPosition` seams; (3) whether §4.5 and §4.7's losses are accepted or
re-hosted. There is no reading of this task under 200 lines, and no reading over 600.

**Explicitly not costed, because it should not be done:** reimplementing `KineticWeapon#damageEntities` and
`stabAttack` inside the mod to get a filterable sweep and a mod-controlled damage formula. It is about 90
lines of vanilla logic, so perhaps 200 in this tree's style — but it would duplicate five vanilla methods
(`getMotion`, the condition tests, the `ProjectileUtil` sweep, `stabAttack`, the stab memory) that Mojang
is actively tuning in a snapshot cycle, and every one of them would have to be re-diffed against the
vanilla source on every snapshot bump. **That is not 200 lines; it is 200 lines plus a permanent tax.**
Given that everything needed is already public, there is no reason to pay it.

---

## 6. Is it worth it?

### 6.1 The arithmetic

Closing speed. `CavalryHorseEntity` documents its own ceiling: `CITIZEN_RIDE_MAX_ACCELERATION = 0.18`, and
"steady-state speed on ordinary ground is acceleration / (1 - 0.6 * 0.91) = 2.203 * acceleration blocks per
tick, so 0.18 caps a mounted guard at **7.9 blocks/s**"
(`mcol/core/entity/other/cavalry/CavalryHorseEntity.java:157–168`). **[VERIFIED as the documented figure;
UNVERIFIED as an observed one — the comment is arithmetic, and confirming means measuring a ridden horse's
`lastKnownSpeed` in play.]**

That 7.9 is the horse's speed, and `getMotion` reads exactly the horse (§1.4). The projection onto the
rider's look vector costs some of it: the horse forces the rider's yaw toward its own heading at ≤12°/tick
(`CavalryHorseEntity.java:535–538`, `RIDER_ALIGN_MAX_STEP_DEGREES = 12.0F` at line 141) **[VERIFIED]**
while the combat AI writes the rider's yaw toward the target
(`AttackMoveAI.java:81–82`, `MeleeCombatAI.java:294–295`), and pitch enters `getLookAngle` too. Call the
alignment 0.90–1.00.

Iron spear, `damageMultiplier = 0.95`, base 3.0:

| alignment | `relativeSpeed` | `floor(v × 0.95)` | **damage per stab** |
|---|---|---|---|
| 1.00 | 7.90 | 7 | **10** |
| 0.95 | 7.51 | 7 | **10** |
| 0.90 | 7.11 | 6 | **9** |
| 0.75 | 5.93 | 5 | **8** |
| stationary | 0.00 | — | **0 — condition fails** |

Against a target closing head-on, `relativeSpeed` rises by the target's own speed; a zombie at roughly
0.9 blocks/s would push the 0.90 row back to 10. Netherite spear (`damageMultiplier = 1.2`,
`mc/world/item/Items.java:2352`) at alignment 0.95 gives `3 + floor(7.51 × 1.2) = 3 + 9 = 12`.

### 6.2 Against what the guard has today

Per-swing, iron vanilla spear, no research, no crit, `guardDamageMultiplier = 1.0`,
`CAVALRY_DAMAGE_MULTIPLIER = 1.00`:

* Reading `MeleeCombatAI.java:489–490` and **excluding** the line-520 doubling: `2.0 + 3 = ` **5.0**.
* Reading the code as written, **including** it: **10.0**. (§2.3; the audit's measurement and the code
  disagree, and I could not run the game to settle it.)

Netherite sword on a knight, same two readings: **10.0** (measured, `26.2/audit/GUARD-AUDIT.md:437`) or
**20.0**.

So per hit the vanilla charge path lands **9–10 damage** — i.e. **exactly what a cavalryman with the same
spear already deals** under the doubled reading, and about double under the undoubled one. Per hit it is
at best a wash and at worst a wash.

Rate is where it goes wrong. Today: one swing per `getAttackDelay()` = 16–32 ticks, so **0.6–1.25 hits/s**.
On the vanilla path the theoretical ceiling is 2 hits/s (§1.7), but that requires the target to stay inside
2.25 blocks of the rider's eyes while the horse is moving at 7.9 blocks/s — which it cannot, because at
that speed the rider crosses the 2.25-block window in about 6 ticks. The realistic figure is **one stab per
charge pass**, and `SpearUseGoal`'s withdrawal picks a point 8–9 blocks away for a mounted attacker
(`SpearUseGoal.java:107,127` with `mountDistance = 2`), which at 7.9 blocks/s is roughly **90–120 ticks
round trip**. That is **0.17–0.22 hits/s**.

| | damage/hit | hits/s | **DPS** |
|---|---|---|---|
| today, iron spear (undoubled reading) | 5 | 0.6–1.25 | 3.0–6.3 |
| today, iron spear (doubled reading) | 10 | 0.6–1.25 | 6.0–12.5 |
| vanilla charge path, iron spear | 9–10 | 0.17–0.22 | **1.5–2.2** |

**[UNVERIFIED — every figure in this table is arithmetic over verified constants, not a measurement. The
hits/s column for the charge path is the softest number in the study: it depends on the withdrawal radius
chosen in Phase 2, on how often the pathfinder actually completes a pass, and on whether the target chases.
Confirming means building the thing and timing it, which is exactly the cost this study exists to avoid.]**

### 6.3 What it does buy

One thing, and it is not damage:

* **Knockback** needs `attackerSpeed >= 5.1 × 0.2 = 1.02` blocks/s within 135 ticks of couching, and
  **dismount** needs `>= 11.0 × 0.2 = 2.20` blocks/s within 50 ticks (§1.8). A cavalryman at 7.9 clears both
  by a wide margin. `stabAttack` then applies two knockback impulses (`LivingEntity.java:2877–2880`) and
  calls `target.stopRiding()` (2882–2885). **[VERIFIED]** **A charging cavalryman would unhorse mounted
  raiders and scatter formations, and the mod has no other route to either.**
* Reach extends with forward motion (§1.5), so a charge genuinely strikes from further out.

### 6.4 Recommendation

**Do not do this to increase cavalry damage.** By the numbers it is a large, invasive change that lowers
cavalry DPS by roughly a factor of three, removes cavalry from the colony's damage-progression system
(§4.2), silently stops spears wearing out (§4.4), drops the mod's damage source and with it PvP armour
bypass (§4.5), and cannot filter friendly fire on the sweep at all without a mixin (§5.4). If "a mounted
charge should hurt more" is the actual requirement, it is about fifteen lines in `CavalryCombatAI`:

```
getAttackDamage() += floor(user.getRootVehicle().getKnownSpeed().scale(20).length() * SOME_MULTIPLIER)
```

which reuses vanilla's own speed accounting (`Entity#getKnownSpeed`, public, §1.4), keeps every existing
scaling term, keeps the damage source, keeps durability, keeps the timing model, breaks nothing in §4, and
can be tuned in a constant. That is the change I would make.

**Do consider it if the requirement is knockback and dismount** — a cavalry charge that scatters a raid
and unhorses riders is a real capability and §6.3 is the only way to get it. In that case take §5.3
option (c): keep `doAttack` as the source of damage, couch the spear purely for its knockback and dismount
conditions, and accept that the design has two damage paths. That is the cheapest honest version of this
feature and lands nearer the 260 end of the range.

**In no case is a mixin needed, and in no case is an access widener needed.** That part of the question has
a clean answer.

---

## 7. Summary of unverified claims

Collected so each can be checked before anyone acts on it.

1. Whether `MeleeCombatAI.java:520` doubles melee damage in play. The code reading is unambiguous
   (§2.3) but `26.2/audit/GUARD-AUDIT.md:437`'s measurement is the pre-doubling value. **One probe on a
   running server settles it, and it moves every number in §6.2.**
2. The cavalry top speed of 7.9 blocks/s is `CavalryHorseEntity`'s own comment, not an observation.
   Measuring a ridden horse's `lastKnownSpeed` settles it.
3. Alignment between the rider's `getLookAngle()` and the horse's motion during a charge. Assumed 0.90–1.00;
   the yaw contention in §6.1 could be worse. Logging `attackerSpeedProjection` during a charge settles it.
4. Hits per charge pass (§6.2). The softest number in the study.
5. Whether `ArmPose.SPEAR` renders correctly on a mounted citizen (§4.1). Needs a client.
6. Whether the kinetic hit sound reaches the client for a citizen (§4.7).
7. Whether a charging cavalryman would in practice spit colony livestock (§1.5).
8. Whether `minecraft:spear` carries any damage-type tag that changes §4.5's conclusions — I read the
   mod's tag providers but not vanilla's damage-type tag data.
9. The end-to-end consequence of spears never wearing out on the request economy (§4.4).
