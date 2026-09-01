# The colony's guards

Research only. Date: 2026-08-27. Tree: `26.3/src/` on branch `26.3`. **No feature code was written**;
everything below is source reading and arithmetic over cited constants. Nothing was built and nothing was
run.

Scope: the defence side — knights, huscarls, rangers, marksmen, druids; the guard tower, barracks and its
towers, archery and combat academy; hiring, levelling, equipment, the combat AI, patrol/guard/follow/rally,
retreat, healing, death, and the seams where guards meet raids and the colony claim.

**Cavalry is out of scope** (`88bebb96`, `c5d4a64d`, and live edits in the tree). `MeleeCombatAI`,
`AttackMoveAI`, `TargetAI`, `ThreatTable` and `AbstractEntityAIGuard` are shared base classes; they are
audited here **as they serve the foot guards**, and every cavalry-only branch in them
(`CAVALRY_*` constants, `holdsGroundInAttackRange`, `usesSpearFootwork`'s mounted guard) is left alone. One
incidental defect in a shared file that belongs to the cavalry work is flagged in §7 and not pursued.

---

## Verdict in one page

The guard code is not broken; it is **mis-scaled in half a dozen independent places, most of them one to
twenty lines**. There is no single large rewrite waiting here. There are about sixteen small levers, and
five of them change what a player sees within one raid.

The three that matter most are all arithmetic:

1. **Every melee guard hits for exactly twice his weapon's damage**, because `MeleeCombatAI:520` adds an
   enchantment helper's return value to the value it passed in, and that helper returns the whole figure
   rather than the enchantment's contribution. A netherite-sworded knight deals 20 per swing where the
   surrounding code plainly intends 10. Re-verified here against vanilla; see §2.2. **Fixing it is one
   line and halves every melee guard in the game — a balance change, not a bug fix.**
2. **A guard never asks for better armour once he owns any.** `AbstractEntityAIFight:211-222` creates an
   armour request only when the slot is *empty*. Upgrade a guard tower from 1 to 5 and the guard stays in
   the leather he was issued on day one, for ever, unless the player hand-stocks the hut. §3.2.
3. **The marksman's damage collapses as he levels.** `RangeCombatAI:307` multiplies his true-damage share
   by ten and computes it from the already-reduced arrow damage, so his output peaks at Adaptability 0 and
   falls by a factor of about fifty by Adaptability 99 — a research-locked late-game unit that is worse
   than a fresh ranger. §3.5.

Two more are behavioural and equally cheap:

4. **The guard tower — the guard building — does not patrol.** `BuildingGuardTower:230-234` routes it into
   `AbstractEntityAIGuard:539-555`, a 20-block random wander with a 2-in-5 chance of striking out to a
   uniformly random building (level-0 huts included, no distance clamp). The real automatic patrol, with
   its distance clamp and its wait-for-the-others logic, is only reached by barracks towers. §3.3.
5. **Every guard's target box is smaller than its own weapon, and six blocks tall.** A ranger on GUARD
   wants to shoot at 34+ blocks and can see 32 in one direction and 16 in the other three
   (`TargetAI:223-249`, `RangeCombatAI:230-257`). A knight and a druid cannot see three blocks up. §3.4.

Everything below is ranked by how much it changes play. Fix sizes are **S** (< ~150 lines) unless marked.
Fourteen of the sixteen findings are S; two are M. That is the shape of this area.

---

## Evidence standard

Same as `docs/studies/worldmap-chunk-generation.md`:

* **[VERIFIED]** — I opened the file and the cited `file:line` says what I claim, or I ran the thing and
  watched the output.
* **[UNVERIFIED]** — inference I did not confirm. Each one names what would confirm it.

Paths: `26.3/src/main/java/com/minecolonies/` is abbreviated to `mcol/`; vanilla sources are read from
`/opt/mc-src-26.3-snapshot-10/net/minecraft/`, abbreviated to `mc/`.

**Version, stated up front.** `mc/SharedConstants.java:16` reads `public static final int WORLD_VERSION =
5015`. Snapshot-10 is 5015 and snapshot-9 is 5011, so this is the right tree and the right build — the trap
that caught the world-map study does not apply. **[VERIFIED]**

**Line numbers, and a hazard.** Other work was landing in this working tree while this study was written.
Two files it cites moved under it: `ServerConfiguration.java` gained two fields and a fifteen-line block
(raider vision keys), and `AbstractEntityMinecoloniesMonster.java` gained a scaled call-for-help box. Every
citation below was re-resolved against the tree as it stands at the end of this study, but a reader who
finds a cited line off by a few should search for the quoted code rather than trust the number. Nothing in
the guard files themselves changed while this was written.

**Nothing in this study was executed.** Every damage, health and per-tick figure is arithmetic over cited
constants, not a measurement. Where a number is a measurement it is quoted from
`26.2/audit/GUARD-AUDIT.md`, which was measured on a running dedicated server, and it is labelled as such
and as being on the 26.2 tree. The distinction matters: an earlier study in this repository misread that
document's pre-doubling damage measurement as a final figure.

---

## 0. What I read

**Combat core, in full:** `mcol/core/entity/ai/combat/TargetAI.java` (319),
`AttackMoveAI.java` (235), `CombatUtils.java` (160),
`mcol/api/entity/ai/combat/threat/ThreatTable.java` (286), `ThreatTableEntry.java` (96),
`IThreatTableEntity.java` (14).

**Guard AI, in full:** `mcol/core/entity/ai/workers/guard/MeleeCombatAI.java` (638),
`RangeCombatAI.java` (448), `DruidCombatAI.java` (361), `AbstractEntityAIGuard.java` (887),
`AbstractEntityAIFight.java` (370), `EntityAIMelee.java` (182), `EntityAIRange.java` (87),
`EntityAIDruid.java` (84), `training/AbstractEntityAITraining.java` (129), and the relevant halves of
`training/EntityAICombatTraining.java` and `training/EntityAIArcherTraining.java`.

**Jobs and buildings:** `mcol/core/colony/jobs/AbstractJobGuard.java`, `guard/JobKnight.java`,
`JobRanger.java`, `JobMarksman.java`, `JobHuscarl.java`, `JobDruid.java`;
`mcol/core/colony/buildings/AbstractBuildingGuards.java` (865),
`workerbuildings/BuildingGuardTower.java`, and the guard-relevant parts of `BuildingBarracksTower.java`,
`BuildingBarracks.java`, `BuildingArchery.java`, `BuildingCombatAcademy.java`;
`modules/GuardBuildingModule.java`, `modules/BuildingModules.java:565-620`,
`modules/settings/GuardTaskSetting.java`, `GuardPatrolModeSetting.java`.

**Equipment and damage plumbing:** `mcol/api/equipment/ModEquipmentTypes.java`,
`api/equipment/registry/EquipmentTypeEntry.java`, `api/entity/ai/workers/util/GuardGear.java`,
`GuardGearBuilder.java`, `api/util/ItemStackUtils.java` (armour tables and weapon predicates),
`api/util/constant/GuardConstants.java`, `EquipmentLevelConstants.java`,
`api/inventory/InventoryCitizen.java`, `mcol/core/entity/other/CustomArrowEntity.java`,
`mcol/core/items/ItemSpear.java`, `mcol/core/util/AttributeModifierUtils.java`.

**Citizen side:** `mcol/core/entity/citizen/EntityCitizen.java` (`hurtServer`, `handleDamagePerformed`,
`callForHelp`, `getArmorValue`, `getItemBySlot`, `die`, `checkHeal`),
`mcol/api/entity/citizen/AbstractEntityCitizen.java`, `mcol/core/entity/ai/workers/CitizenAI.java`,
`mcol/core/colony/CitizenData.java:560-620`, `mcol/core/colony/managers/GraveManager.java:258-335`,
`mcol/core/entity/citizen/citizenhandlers/CitizenSkillHandler.java`, `CitizenExperienceHandler.java`.

**Config and research:** `mcol/api/configuration/ServerConfiguration.java:263-420`,
`mcol/core/generation/defaults/DefaultResearchProvider.java:81-1250`.

**Vanilla:** `mc/world/item/enchantment/EnchantmentHelper.java:188-194`,
`mc/world/entity/LivingEntity.java` (`hurtServer` 1171-1250, `getArmorValue` 1866,
`getDamageAfterArmorAbsorb` 1896-1903, `detectEquipmentUpdates` 2908-2950),
`mc/world/damagesource/CombatRules.java:16-32`, `mc/world/item/ToolMaterial.java:20-90`,
`mc/world/item/Item.java:505-568`, `mc/world/item/Items.java:1554-1602, 2330-2352`,
`mc/world/entity/projectile/arrow/AbstractArrow.java:417-442`,
`mc/world/entity/projectile/Projectile.java:164-182`, `mc/SharedConstants.java:16`.

**Prior work:** `docs/studies/raids-and-raiders.md` (§1 in full),
`docs/studies/cavalry-vanilla-attack-path.md` (§2.3, §6), `26.2/audit/GUARD-AUDIT.md` (in full),
`docs/studies/territory-mechanics.md` §4, `docs/studies/worldmap-chunk-generation.md` (evidence standard).

**Not read, deliberately:** `CavalryCombatAI`, `EntityAICavalry`, `CavalryHorseEntity`, `JobCavalry`,
`BuildingStable`, the cavalry client GUI. Also not read: the guard hut BlockUI layouts, the rally-banner
renderer, and anything client-only.

---

## 1. Where the earlier work stands in 26.3

`26.2/audit/GUARD-AUDIT.md` is in Russian and its numbers are measurements on a live 26.2 server. Its
method paragraphs are sound; the trap is that its §4.1 damage table is a table of **weapon terms**, i.e.
the value of `addDmg` *before* `MeleeCombatAI`'s doubling and before research — the cavalry study read one
of those figures as a final per-swing number. Read §4.1 as "the contribution of the weapon", not "damage
dealt".

| 26.2 finding | Status in 26.3 |
|---|---|
| §3.1 knight blind ±3 blocks vertically | **Still true, now behind a config.** `TargetAI:264-267` reads `guardVerticalVision`, whose minimum *and* default are both `Y_VISION` = 3 (`ServerConfiguration.java:401`). Out of the box, nothing changed. `RangeCombatAI:424-434` keeps the archer's +25 while guarding. `DruidCombatAI:344-352` does **not** read the config — see §3.14. **[VERIFIED]** |
| §3.2 thrash on an unreachable target | **Fixed and carried over.** `TargetAI:200` is `foundTarget \|= getThreatFor(entity) >= 0`. **[VERIFIED]** |
| §3.3 guards nap at noon | **Still true, now behind `guardsfallasleep` (default `true`).** `AbstractEntityAIGuard:278-311`. The comment's arithmetic still disagrees with the code (`+ 20`, not `+ 10`). **[VERIFIED]** |
| §3.4 self-healing for ~75 s, no healer involvement | **Still true.** `AbstractEntityAIGuard:393-410`; `CitizenAI:136-153` still routes guards past every hospital branch and checks only `isSick()`, never `isHurt()`. **[VERIFIED]** |
| §3.5 no retreat without the `RETREAT` research | **Still true.** `AbstractEntityAIGuard:382`. **[VERIFIED]** |
| §3.6 dead `target` field | **Still true.** Declared twice (`AbstractEntityAIGuard:95`, `AbstractEntityAIFight:56`), assigned nowhere; `grep -n '\btarget\s*=' ` on both files returns only the declarations. Read by `shouldSleep` (`:283`), where it is always null. **[VERIFIED]** |
| §3.6 `shouldFlee` compares a squared distance with 20 | **Still true.** `AbstractEntityAIGuard:380`. See §3.9. **[VERIFIED]** |
| §3.6 archer range clamped before the height bonus | **Still true.** `RangeCombatAI:244-254`. See §3.15. **[VERIFIED]** |
| §3.6 slow release of an unreachable target | **Still true**, and the arithmetic is confirmable: `AttackMoveAI:84-95` drops one threat per six failed path attempts and gives up below 5, from a starting threat of 10-19. **[VERIFIED]** |
| §4 huscarl axe damage read from durability | **Fixed, and the fix is documented in place** (`MeleeCombatAI:494-514`). The port's numbers are re-derived independently here in §2.2 and agree with the measured table. **[VERIFIED]** |
| §0.3.3 "one guard tower holds one guard" | **Still true and still by design.** `BuildingModules:590-603` gives each of the five tower modules `(b) -> 1` and `GuardBuildingModule:78-81` counts *all* assigned citizens, so five modules of one still total one. Barracks towers use `ICommonBuilding::getBuildingLevel`. **[VERIFIED]** |
| §8.1 `notifyGuardsOfTarget` walks every colony building | **Still true.** `CombatUtils:147-158`. Bounded arithmetically in §4. **[VERIFIED]** |

Nothing the 26.2 audit claimed turned out to be wrong. Two of its five "not done" recommendations
(`shouldFlee`, the range clamp) are still not done, and both are restated below with the balance flag it
asked for.

From `docs/studies/raids-and-raiders.md`: F1 (raiders blind above and below) is the attacker-side twin of
§3.4 here, and the raider half of it is already being worked (`AbstractEntityMinecoloniesMonster:301-323`
now scales the call-for-help box with the shooter's distance, which upstream did not). The guard half is
untouched.

From `docs/studies/cavalry-vanilla-attack-path.md` §2.3: the melee doubling was reported there as
**[UNVERIFIED whether live in 26.3]**. It is verified here, both halves — the mod line and the vanilla
helper. See §2.2.

---

## 2. The arithmetic, end to end

### 2.1 What a guard is made of

A citizen starts at `MAX_HEALTH` 20 and `ATTACK_DAMAGE` 3 (`AbstractEntityCitizen:167-171`,
`CitizenConstants:95` `BASE_MAX_HEALTH = 20`, `GuardConstants:274` `BASE_PHYSICAL_DAMAGE = 3`).
**[VERIFIED]** Skill levels run 1-99 (`CitizenSkillHandler:43,182`, `CitizenConstants:192`
`MAX_CITIZEN_LEVEL = 99`). **[VERIFIED]**

Max health is then four `AttributeModifier`s, all added through `AttributeModifierUtils.addHealthModifier`
(which removes by id first, so re-application is idempotent — `AttributeModifierUtils:62-75`):

| Modifier | Value | Site |
|---|---|---|
| building | `getBonusHealth()` | `AbstractJobGuard:76-77` |
| config | `guardHealthMult - 1`, `ADD_MULTIPLIED_TOTAL`, default 1.0 → 0 | `AbstractJobGuard:78-81`, `ServerConfiguration:394` |
| level | knight `Stamina + 15`; druid `Mana/2 + 12`; **ranger: none** | `JobKnight:66-70`, `JobDruid:50`, and the absence of `onLevelUp` in `JobRanger` |
| research | `HEALTH_BOOST`, up to +20 | `CitizenData:2029-2032`, `DefaultResearchProvider:100` |

`getBonusHealth()` is `buildingLevel × 2` (`AbstractBuildingGuards:723-726`), plus a flat **+20 for a guard
tower only** (`BuildingGuardTower:171, 236-240`). **[VERIFIED]**

So, max health, no research:

| Unit | level-1 guard tower | level-5 guard tower, skill 99 | level-5 barracks tower, skill 99 |
|---|---|---|---|
| Knight / huscarl | 20 + 16 + 22 = **58** | 20 + 114 + 30 = **164** | 20 + 114 + 10 = **144** |
| Ranger / marksman | 20 + 0 + 22 = **42** | 20 + 0 + 30 = **50** | 20 + 0 + 10 = **30** |
| Druid | 20 + 12 + 22 = **54** | 20 + 61 + 30 = **111** | 20 + 61 + 10 = **91** |

`HEALTH_BOOST` adds a flat 20 to every row at full research. **[VERIFIED]** for every term; the sums are
mine. A max-level ranger in a barracks tower has **less health than a fresh knight in a level-1 tower**.
That is §3.6.

### 2.2 What a melee guard deals, per swing

`MeleeCombatAI.getAttackDamage()` (`:463-538`), in order:

1. **Weapon term.** One of five branches, all inside `doesItemServeAsWeapon` (`:469`):
   * sword (`:471-474`) — the *citizen's* `ATTACK_DAMAGE` attribute, i.e. base 3 plus the item's mainhand
     modifier. Vanilla swords are `sword(material, 3.0F, …)` (`mc/Items.java:1554-1594`) and
     `ToolMaterial.applySwordProperties` writes `attackDamageBaseline + material.attackDamageBonus()`
     (`mc/ToolMaterial.java:75-90`), so the term is `6 + bonus`.
   * mod spear (`:475-478`) — `ItemSpear.getDamage()` = 3 (`ItemSpear:22,102-105`) plus 3 → **6**.
   * vanilla spear (`:479-491`) — the item's own `ATTACK_DAMAGE` modifier, which
     `Item.Properties#spear` sets to `0.0F + material.attackDamageBonus()` (`mc/Item.java:552-558`), plus 3.
   * axe (`:492-515`, huscarl only) — the item's modifier plus 3. Vanilla axe baselines are 5-7
     (`mc/Items.java:1562-1602`).
   * anything else → `TinkersToolHelper.getDamage`, i.e. 0 without Tinkers.

   Material bonuses (`mc/ToolMaterial.java:23-29`): wood 0, gold 0, stone 1, copper 1, iron 2, diamond 3,
   netherite 4. **[VERIFIED]**

   | | wood | gold | stone | copper | iron | diamond | netherite |
   |---|---|---|---|---|---|---|---|
   | sword (knight) | 6 | 6 | 7 | 7 | 8 | 9 | **10** |
   | axe (huscarl) | 9 | 9 | 11 | 11 | 11 | 11 | **12** |
   | vanilla spear | 3 | 3 | 4 | 4 | 5 | 6 | **7** |
   | mod spear | — | — | — | — | — | — | **6** |

   The axe row reproduces `26.2/audit/GUARD-AUDIT.md` §4.1's measured "порт" column exactly (9, 11, 11, 9,
   11, 11, 12), which is a useful independent check on this derivation. **[VERIFIED]**

2. **The doubling** (`:520`):

   ```java
   addDmg += EnchantmentHelper.modifyDamage((ServerLevel) user.level(), heldItem, target, ..., (float) addDmg);
   ```

   `mc/world/item/enchantment/EnchantmentHelper.java:188-194` seeds a `MutableFloat` **with the damage it
   was given** and returns the whole thing:

   ```java
   MutableFloat result = new MutableFloat(damage);
   runIterationOnItem(itemStack, (e, l) -> e.value().modifyDamage(..., result));
   return result.floatValue();
   ```

   So the line evaluates to `addDmg = 2 × addDmg + (enchantment contribution)`. **Unconditional, every
   melee guard, enchanted or not.** **[VERIFIED]** — both the mod line and the vanilla helper were opened
   and read. This confirms the cavalry study's §2.3 reading, which it could only mark unverified.

3. `+ MELEE_DAMAGE` research, flat 0.5/1/1.5/2/**4** (`:523`, `DefaultResearchProvider:103`).
4. `× 2` if health ≤ 20 % of max (`:526-529`) — carrying a `TODO: Recheck balancing`.
5. `× 1.5` with probability `1 − 1/(1 + GUARD_CRIT)` (`:531-535`). `GUARD_CRIT` is 0.2/0.3/0.4/**0.5**
   (`DefaultResearchProvider:137`), i.e. **16.7 % to 33.3 %**. With no research the probability is
   `nextDouble() > 1`, which is never — crits are entirely research-gated, which is correct.
6. `× guardDamageMultiplier`, default 1.0, range 0.1-15 (`:537`, `ServerConfiguration:393`). Applied once.

Cadence: `KNIGHT_ATTACK_DELAY_BASE 32 − Adaptability/(huscarl ? 2 : 3)`, floored at
`KNIGHT_ATTACK_DELAY_MIN 16` (`:547-552`, `GuardConstants:113,118`). A knight reaches the floor at
Adaptability 48, a huscarl at 32. **[VERIFIED]**

Putting it together (no config change, crit expectation folded in):

| Guard | swing | delay | dps |
|---|---|---|---|
| Knight, level 1, level-1 tower, stone sword, no research | 7 → **14** | 32 t | 8.8 |
| the same, without the line-520 doubling | 7 | 32 t | 4.4 |
| Knight, Adaptability 99, netherite sword, `MELEE_DAMAGE` 4, `GUARD_CRIT` 0.5 | 10 → 20 → 24 → mean **28** | 16 t | **35** |
| Huscarl, same, netherite axe | 12 → 24 → 28 → mean **32.7** | 16 t | **41**, armour-ignoring (§3.10) |

All four rows are arithmetic over the constants above, not measurements.

**Range** is `MAX_DISTANCE_FOR_ATTACK = 2` blocks centre-to-centre (`GuardConstants:250`,
`MeleeCombatAI:543`), or the spear's own reach for a spearman.

### 2.3 What a ranged guard deals, per arrow

`RangeCombatAI.calculateDamage` (`:271-328`):

```
damage  = Agility / 5
        + EnchantmentHelper.modifyDamage(bow, …, 1) / 2.5      // 0.4 on an unenchanted bow
        + POWER level
        + ARCHER_DAMAGE research (0.5 … 4)
        + 2 if ARCHER_USE_ARROWS is researched and an arrow is in the pack
damage ×= 2 if health ≤ 20 %
damage ×= 1.5 on a crit
return (RANGER_BASE_DMG 2 + damage) × guardDamageMultiplier × (marksman ? 1 − share : 1)
```

Note that the low-health and crit multipliers apply to `damage` only, **not** to the flat
`RANGER_BASE_DMG = 2` — the opposite of the melee path, where they apply to everything. A small, harmless
asymmetry.

The velocity term cancels: `CustomArrowEntity.onHitEntity:100-107` divides `baseDamage` by
`getDeltaMovement().length()` immediately before `super.onHitEntity`, and vanilla then computes
`Mth.ceil(pow × baseDamage)` (`mc/AbstractArrow.java:420-428`). So an arrow deals `ceil(calculateDamage())`
at any range. **[VERIFIED]** That is a deliberate and correct piece of engineering, and it is why the
`ARROW_SPEED × 1 + dist3d/35` launch power in `CombatUtils.shootArrow:105` is a ballistics term only.

Cadence: `RANGED_ATTACK_DELAY_BASE = 60` ticks, `× 0.67` for a marksman (`:260-263`, `GuardConstants:94`).
`DOUBLE_ARROWS` adds a second arrow with probability up to 0.5 (`:186-192`,
`DefaultResearchProvider:92`).

| Guard | per arrow | volley | dps |
|---|---|---|---|
| Ranger, Agility 1, level-1 tower, no research | ceil(2.6) = **3** | 3 | 1.0 |
| Ranger, Agility 99, `ARCHER_DAMAGE` 4, arrows, `GUARD_CRIT` 0.5, `DOUBLE_ARROWS` 0.5 | ceil(32.6) = **33** | ×1.5 = 49 | **16** |
| Marksman, Adaptability 1, otherwise as above | 17 + 81.5 unblockable | **81.5** | **41** |
| Marksman, Adaptability 99, otherwise as above | 1 + 0.6 unblockable | **1.6** | **0.8** |

The last two rows are §3.5.

### 2.4 What a guard takes

Three things happen, in this order:

1. **The cap.** `EntityCitizen.handleDamagePerformed:1403`:
   `float damageInc = Math.min(damage, getMaxHealth() * 0.2f)`. **Every hit on every citizen is capped at
   a fifth of max health**, before armour. **[VERIFIED]**
2. **Research.** A knight blocks the hit outright with probability `BLOCK_ATTACKS` (up to 0.5) if
   `SHIELD_USAGE` is researched (`:1457-1466`). A guard below 20 % health whose hut has `RETREAT` on takes
   `× (1 − FLEEING_DAMAGE)`, up to −75 % (`:1468-1472`, `DefaultResearchProvider:95`).
3. **Armour**, in `super.hurtServer` → `mc/LivingEntity.java:1896-1903` → `mc/CombatRules.java:16-32`:
   `realArmor = clamp(armor − damage/(2 + toughness/4), armor × 0.2, 20)`, then
   `damage × (1 − realArmor/25)`.

`EntityCitizen.getArmorValue:1817-1829` multiplies the armour attribute by `1 + MELEE_ARMOR` for a knight
and `1 + ARCHER_ARMOR` for a ranger, both up to **+100 %** (`DefaultResearchProvider:81,102`). A druid gets
neither. Because `CombatRules` subtracts `damage/toughness` **before** clamping to 20, that research is not
wasted on a guard already at 20 armour: it cancels the incoming hit's armour penetration. **[VERIFIED]**

The cap at (1) is the interesting one. Against the mod's own raiders it almost never binds: raider melee
damage is `ATTACK_DAMAGE 2.0 + difficultyModifier × min(raidLevel/400, 3)` on a `[1, 20]`-clamped attribute
(`RaiderMobUtils:47-48, 101-104`; `RaiderConstants:46`), so 2 to about 9 — well under a fifth of any
guard's health. **[VERIFIED]** It binds against chiefs, creepers and modded heavy hitters, and there it has
a consequence worth stating plainly:

> **Because the cap is a fraction of max health, the number of big hits it takes to kill a guard is five,
> regardless of how much health he has.** Max health only buys survivability against hits *below* a fifth
> of it. Armour, which is applied after the cap, is what actually scales.

A worked duel, a knight in iron (armour 15) against an ordinary raider hitting for 6 every 30 ticks
(`RaiderConstants:97` `MELEE_ATTACK_DELAY = 30`, reach `MIN_DISTANCE_FOR_ATTACK = 2.5` at `:92`):
`realArmor = clamp(15 − 6/2, 3, 20) = 12`, fraction 0.48, so 3.1 per hit. A 58-HP level-1-tower knight
survives 18 hits — about 27 seconds. He kills a 10-HP raider (`RaiderConstants:84`) in one swing. That is
the intended shape and it works.

---

## 3. Findings, ranked

### 3.1 Melee guards deal exactly double their weapon damage

**What the code does.** `mcol/core/entity/ai/workers/guard/MeleeCombatAI.java:520` adds
`EnchantmentHelper.modifyDamage(level, heldItem, target, source, addDmg)` to `addDmg`, and that helper
returns `addDmg` plus the enchantments' contribution (`mc/world/item/enchantment/EnchantmentHelper.java:
188-194`). **[VERIFIED]**

**What it produces in play.** Every knight, huscarl and spear-armed guard in the game hits for twice the
weapon term derived in §2.2: 20 for a netherite sword, 24 for a netherite axe, 10 for an iron spear, 14 for
a stone sword. Melee guards are roughly twice as strong as the rest of the file's numbers imply, and the
`MELEE_DAMAGE` research — a flat +4 at the top — is correspondingly a much smaller share of a knight's
output than its five-tier research tree suggests. Against ranged guards, whose path has no such error, the
melee/ranged balance is off by a factor of two.

**Why it is wrong.** The intent is unambiguous: every other term in the method is `addDmg += <a
contribution>`, and the argument passed to `modifyDamage` is the running total, which is what a "modify
this damage" helper is expected to consume, not to be added to.

**The fix.** `addDmg = EnchantmentHelper.modifyDamage(...)`. **One line, S.**
**This is a balance change affecting every melee guard in the game and must be shipped as one**, not as a
bug fix — it halves colony melee output at a stroke. The honest packaging is either (a) fix the line and
raise `BASE_PHYSICAL_DAMAGE` / the `MELEE_DAMAGE` tiers to land near today's numbers, or (b) fix the line
and let `guardDamageMultiplier` (already a config, default 1.0) carry servers that want the old feel.
Option (a) is another ~10 lines and is the one that keeps the research tree meaningful.

### 3.2 A guard never requests better armour once he owns any

**What the code does.** `mcol/core/entity/ai/workers/guard/AbstractEntityAIFight.java:211-222`:

```java
final Map<IItemHandler, List<Integer>> items = InventoryUtils.findAllSlotsInProviderWith(building, item);
if (items.isEmpty())
{
    if ((item.getType().isArmor() && ItemStackUtils.isEmpty(worker.getInventoryCitizen().getArmorInSlot(item.getType()))) || ...)
    {
        checkForToolOrWeaponAsync(item.getItemNeeded(), item.getMinArmorLevel(), item.getMaxArmorLevel());
    }
}
```

An armour request is created **only** when the hut's racks hold nothing matching *and* the guard's slot is
empty. If the hut holds nothing and the guard is wearing something — anything — no request is made.
**[VERIFIED]**

**What it produces in play.** The gear tier a guard is *allowed* is a function of hut level
(`AbstractEntityAIFight:83-87` builds five `GuardGear` lists; at level 4 the band is chain..diamond, at
level 5 iron..netherite; `GuardGear.test:150-158` enforces it). A day-one guard in a level-1 tower is
issued leather (band 0..1) through this same request path, because his slots were empty. From then on the
slots are never empty, so no further request is ever made. Upgrade the tower to 5 and he is still in
leather: 7 armour points where the design intends 20. The only route to better armour is the player
hand-stocking the hut, after which `atBuildingActions:226-289` does compare levels and swap correctly.

**Why it is wrong.** The level comparison exists (`:236-245`) and works; only the *request* is gated on
emptiness. `bestLevel` is already computed from the currently worn piece two dozen lines earlier
(`:195-208`) and is exactly the number the request branch needs.

**The fix.** Move the request out of the `items.isEmpty()` branch and gate it on
`bestLevel < item.getMinArmorLevel()` instead of on the slot being empty — the guard asks when what he is
wearing is below the band his hut licenses. Roughly 15 lines. **S.** Not a balance change: it makes the
existing progression actually happen.

### 3.3 The guard tower does not patrol; it wanders

**What the code does.** `mcol/core/colony/buildings/workerbuildings/BuildingGuardTower.java:230-234`:

```java
return (patrolTargets == null || patrolTargets.isEmpty() || tempNextPatrolPoint != null || !shallPatrolManually())
         && tempNextPatrolPoint == null;
```

With the stock settings — `GUARD_TASK` defaults to `PATROL` and `PATROL_MODE` to `AUTO`
(`BuildingModules:616,619`; `GuardPatrolModeSetting:28`) — `patrolTargets` is empty, so this returns true,
and `AbstractEntityAIGuard.patrol():537-556` takes the branch:

```java
if (!EntityNavigationUtils.walkToRandomPos(worker, 20, 1.0)) return getState();
if (worker.getRandom().nextInt(5) <= 1)
{
    currentPatrolPoint = randomPatrolPoint();   // getRandomBuilding(b -> true)
    if (currentPatrolPoint != null) walkToSafePos(currentPatrolPoint);
}
```

`randomPatrolPoint():525-528` is `getServerBuildingManager().getRandomBuilding(b -> true)` — **no level
filter, no weighting, no distance clamp**. **[VERIFIED]**

`BuildingBarracksTower` does *not* override `requiresManualTarget`, so it inherits
`AbstractBuildingGuards:376-380`'s `false` and gets the real automatic patrol
(`AbstractBuildingGuards:420-504`): a `PathJobRandomPos` or `getRandomPatrolTarget()` (which *does* filter
`getBuildingLevel() >= 1`, `:529-532`), clamped to `PATROL_BASE_DIST 50 + level × PATROL_DISTANCE 30`
(`:534-538`, `IGuardBuilding:15`), with an arrival rendezvous across the tower's guards. **[VERIFIED]**

**What it produces in play.** The guard tower is the building a player builds first and builds most of.
Its guard mills about within 20 blocks of wherever he happens to stand, and three times in five he simply
re-rolls that. The other two times in five he sets off — with no distance clamp at all — to a uniformly
random building in the colony, which may be an unbuilt level-0 hut on the far side of town. Barracks
guards, by contrast, visibly walk a bounded circuit. The player's reading is "guard towers are useless for
patrolling, only barracks work", and they are right.

The colony's *own* patrol machinery is bypassed too: the building's `patrolTimer` still fires
`startPatrolNext()` every five colony ticks — 125 seconds, since a colony tick is `MAX_TICKRATE` 500 game
ticks (`Colony.java:527`, `TickRateConstants:11`) — enqueueing a `PathJobRandomPos` and incrementing
`PATROLS_STARTED` for a value the tower's AI never reads. **[VERIFIED]**

**Why it is wrong.** The override reads as an attempt to say "this tower has no manual route, so improvise"
and lands on "so ignore the automatic route as well". The automatic route already exists, is clamped, and
is what every other guard building uses.

**The fix.** Delete the override, or reduce it to `patrolTargets.isEmpty() && shallPatrolManually()` so it
only means "the player chose Manual and gave me no points". About 10 lines including tidying the now-dead
wander branch, **S**. It changes where guards walk, so flag it as behaviour, though not balance. The
`getBorderPatrolTarget` hook (`AbstractBuildingGuards:517-521`) is already in place for the border-patrol
mode `docs/studies/territory-mechanics.md` §4 proposes, and this fix is a prerequisite for it reaching
guard towers at all.

### 3.4 Every guard's target box is smaller than its own weapon, and six blocks tall

**What the code does.** `mcol/core/entity/ai/combat/TargetAI.java:223-249` builds the acquisition box from
`getSearchRange()` and `DEFAULT_VISION = 16` (`GuardConstants:13`), stretched toward one of the four
compass directions, cycled per scan:

```java
x1 = x + max(range * step.x + 16, 16);   x2 = x + min(range * step.x - 16, -16);
y1 = y + getYSearchRange();              y2 = y - getYSearchRange();
z1 = z + max(range * step.z + 16, 16);   z2 = z + min(range * step.z - 16, -16);
```

`getSearchRange()` returns 16 (`TargetAI:274-277`) and the only guard override is
`MeleeCombatAI:620-624`, which returns 16 as well. `RangeCombatAI` and `DruidCombatAI` do not override it
at all. **[VERIFIED]** — `grep -n getSearchRange` over `core/entity/ai/workers/guard/` and
`core/entity/ai/combat/` returns exactly those three sites.

So every colony guard, of every type, acquires inside a box **32 wide × 48 deep × 2·Y high**, reaching 32
blocks in the swept direction and 16 in the other three. `getYSearchRange()` is the server config
`guardVerticalVision`, whose default *and minimum* are 3 (`TargetAI:264-267`,
`ServerConfiguration:401`) — a six-block-tall box. `RangeCombatAI:424-434` raises Y to 28 for an archer
**on the GUARD task only**. **[VERIFIED]**

Against that, `RangeCombatAI.getAttackDistance():229-257`:

```
10 (BASE_DISTANCE_FOR_RANGED_ATTACK) + buildingLevel + (Adaptability/50)·15,
clamped to 24, then + (userY − targetY), then + 10 while on GUARD
```

A max-level ranger in a level-5 tower on GUARD wants to engage at **34 blocks plus his height advantage**.
**[VERIFIED]**

**What it produces in play.** Three things, all of which players report as "my archers don't shoot":

* The archer can never open fire at its own range. For 240 of every 320 ticks, its box does not reach past
  16 blocks in the direction an approaching raid is coming from.
* A knight or druid four blocks from a mob standing four blocks above him does not see it. This is 26.2
  §3.1, measured there: three knights, 250 seconds, **zero** target acquisitions against a zombie six
  blocks up in clear line of sight, while archers beside them engaged the same target three times.
* An archer on PATROL loses the +25 vertical entirely, so the wall-top archer only sees down while the
  hut's task is set to GUARD.

**Why it is weak.** `getSearchRange` was never made a function of `getAttackDistance`, and the vertical
range was never made a function of anything. The building even carries a `getBonusVision()` —
`BASE_VISION_RANGE 15 + level × VISION_RANGE_PER_LEVEL 3` (`AbstractBuildingGuards:755-759`) — which is
read by exactly one caller in the tree, `BuildingGateHouse:90-92`, and by nothing on the guard path.
**[VERIFIED]** A guard tower's level buys no vision at all.

**The fix.** Three parts, all small:
* `RangeCombatAI` and `DruidCombatAI` override `getSearchRange()` to
  `(int) Math.ceil(getAttackDistance())` — the archer sees as far as it shoots. ~8 lines.
* Raise the *minimum* of `guardVerticalVision` from 3 so the config can actually be lowered as well as
  raised, and give `MeleeCombatAI` a modest floor of its own (a knight who cannot see a mob on his own hut
  roof is the single most-reported guard complaint). ~6 lines plus a config edit.
* Optionally wire `getBonusVision()` into `getSearchRange()` so tower level means something. ~5 lines.

**S** in total. The first is behaviour, the second and third are **balance changes** — they change *which*
enemies a guard picks — and belong behind the existing config key, defaulting to today's numbers, exactly
as the 26.2 work did. Cost: the acquisition box is `getEntitiesOfClass` once per 80 ticks per guard and its
volume is linear in Y, so 3 → 12 is ×4 on that query. See §4.

### 3.5 The marksman gets weaker the more he trains

**What the code does.** `mcol/core/entity/ai/workers/guard/RangeCombatAI.java:327` scales a marksman's
arrow down by his true-damage share:

```java
return (RANGER_BASE_DMG + damage) * guardDamageMultiplier * ((isMarksman() ? 1 - marksManTrueDamageShare() : 1));
```

with `marksManTrueDamageShare() = (50 + Adaptability/2)/100` (`:339-342`) — 0.505 at skill 1, **0.995 at
skill 99**. The armour-bypassing half is then supposed to be paid back in the arrow's on-hit callback
(`:298-308`):

```java
entityRayTraceResult.getEntity().hurt(source(PIERCE, user),
    (float) ((CustomArrowEntity) arrow).getBaseDamage() * (float) marksManTrueDamageShare() * 10);
```

`getBaseDamage()` here is the **already-reduced** figure (`CustomArrowEntity:132` restores it just before
the callback at `:133`), and it is multiplied by the share **and by ten**. **[VERIFIED]**

**What it produces in play.** Write `d` for the undivided damage. The arrow carries `d·(1−s)`; the callback
adds `d·(1−s)·s·10`. That product peaks at `s = 0.5` and falls to nothing as `s → 1`:

| Adaptability | share `s` | arrow | pierce | total, relative to a ranger's `d` |
|---|---|---|---|---|
| 1 | 0.505 | 0.495 d | 2.50 d | **2.50 d** |
| 25 | 0.625 | 0.375 d | 2.34 d | 2.34 d |
| 50 | 0.750 | 0.250 d | 1.88 d | 1.88 d |
| 75 | 0.875 | 0.125 d | 1.09 d | 1.09 d |
| 99 | 0.995 | 0.005 d | 0.050 d | **0.05 d** |

(The pierce hit lands in the same tick as the arrow, so vanilla's cooldown at
`mc/LivingEntity.java:1212-1219` applies only the difference — which is why the totals are the pierce
column rather than the sum. **[VERIFIED]**)

With the numbers of §2.3: a level-1 marksman deals ~81 unblockable damage every 40 ticks; a level-99
marksman deals **1.6**. The marksman is locked behind a research (`BuildingModules:579-581,600-601`), so
the player unlocks a late-game unit, trains him, and watches him become useless. Worse: the pierce hit is
registered only inside the `ARCHER_USE_ARROWS`-and-an-arrow-in-the-pack branch (`:280-315`), so a marksman
without that research or without arrows deals **only** the `1 − s` fraction — 0.5 % of a ranger's damage at
max level.

**Why it is wrong.** The intent is visible in the comment: "calculate true damage from reduced arrow
damage", i.e. reconstruct `d = baseDamage/(1−s)` and take `d·s`. The `×10` is a mis-derived constant, and
dividing by `(1−s)` was replaced with multiplying by 10.

**The fix.** `getBaseDamage() / (1 - share) * share` — or, better, do not scale the arrow at all and
compute the split from the unreduced figure, which removes the reconstruction entirely. Move the
`(1 − share)` factor and the pierce registration out of the `ARCHER_USE_ARROWS` branch so the split does
not depend on a research. ~15 lines. **S.** **Balance change** — it makes the marksman roughly `d` at every
level instead of `2.5d` … `0.05d` — and should be shipped as one.

### 3.6 A ranger gains no health for the whole of his career

**What the code does.** `JobKnight.onLevelUp():58-72` adds `Stamina + KNIGHT_HP_BONUS 15` to max health.
`JobDruid:40-53` adds `Mana/2 + DRUID_HP_BONUS 12`. `JobRanger` has **no `onLevelUp` at all**, and
`JobMarksman extends JobRanger` inherits the absence. `GuardConstants` defines no `RANGER_HP_BONUS`.
**[VERIFIED]** — the file is 50 lines and contains only the constructor, `generateGuardAI`, `getModel` and
`getEquipmentType`.

**What it produces in play.** §2.1's table: a max-level ranger in a level-5 barracks tower has 30 max
health where the knight beside him has 144. The archer is meant to be the fragile one, but the difference
is not a tuning choice, it is the absence of the method. Because the incoming-damage cap of §2.4 is
proportional, this does not change how many *big* hits kill him (five, like everyone) — it changes how
quickly he bleeds out to ordinary raider hits, which are the ones that fall under the cap. A 30-HP ranger
in leather takes 10 raider hits; the knight beside him takes 45.

**The fix.** Give `JobRanger` an `onLevelUp` on the same shape as `JobKnight`'s, with its own constant.
~12 lines. **S.** **Balance change.** Note that `AbstractJobGuard.initEntityValues` correctly re-applies
the level modifier on load, via `CitizenData.initEntityValues:605` →
`CitizenExperienceHandler.updateLevel:65-71` → `onLevelUp()`, so a new method will survive a restart —
**[VERIFIED]**, and worth stating because the modifier is transient.

### 3.7 A guard who dies outside the colony loses his armour

**What the code does.** `mcol/core/entity/citizen/EntityCitizen.java:1680-1688`:

```java
if (colony.isCoordInColony(level(), blockPosition()))  gravePos = graveManager.createCitizenGrave(...);
else { gravePos = null; InventoryUtils.dropItemHandler(citizenData.getInventory(), level(), ...); }
```

`InventoryUtils.dropItemHandler:2559-2570` iterates `handler.getSlots()`, and
`InventoryCitizen.getSlots():163-165` returns `mainInventory.size()` — the 27 pack slots. The four worn
pieces live in a separate `armorInventory` (`InventoryCitizen:55-56`) that `getStackInSlot` explicitly
refuses to reach (`:291-303`). **[VERIFIED]**

The grave path handles them properly (`GraveManager:305-312` loops `EquipmentSlot.values()` and reads
`getArmorInSlot`), but its own two fallbacks do not: `GraveManager:303` (grave inventory full) and
`GraveManager:332` (no air-over-solid within 10 blocks) both call `dropItemHandler`. **[VERIFIED]**

**What it produces in play.** A guard who dies chasing a raider past the claim edge, on `PATROL_MINE` in a
mine outside the claim, on `FOLLOW` with a player, or anywhere underground with no room for a grave, has
his entire worn armour set **deleted**. His pack drops. The player sees a pile of arrows and food and no
diamond chestplate.

**The fix.** Extract the armour loop from `GraveManager:305-312` into a helper and call it from all three
drop sites; or make `dropItemHandler` take the citizen inventory's armour into account through a small
overload. ~10 lines. **S.** Not a balance change — it stops destroying player property.

### 3.8 A guard on Follow never eats, and eventually never heals

**What the code does.** `CitizenAI.shouldEat():312-315` returns false when
`!job.canAIBeInterrupted()`, and `AbstractEntityAIGuard.canBeInterrupted():804-813` returns false whenever
`buildingGuards.getTask().equals(GuardTaskSetting.FOLLOW)`. **[VERIFIED]** For the guard branch of
`calculateNextState` (`:138-153`), eating is the *only* thing that can interrupt work, so a guard on FOLLOW
has no way out of `WORK` at all.

**What it produces in play.** Guards burn saturation 20 % faster than everyone else
(`AbstractJobGuard.getSaturationFactor():86-89` returns 1.2), plus one continuous-action decrement per
`ACTIONS_EACH_BLOCKS_WALKED` = 25 blocks walked (`EntityCitizen:915-922`, `Constants:107`) — and FOLLOW is
the mode in which they walk furthest. At zero saturation
`EntityCitizen.updateHealing:830-844` pins them with permanent Slowness I, and
`checkHeal:927-948` cuts their out-of-combat regeneration to `1 × (saturation/FULL)/2`, i.e. to nothing.
A player who leaves guards on Follow for a long expedition comes home with a slow, unhealing escort, and
nothing in the UI explains why.

`canBeInterrupted` is also false while a rally banner is active, which is fine — rallies are short.

**The fix.** Let `shouldEat` through for a guard on FOLLOW when he is not actually in combat: the state is
already available as `fighttimer > 0 || getState() == ATTACKING`. ~5 lines in `canBeInterrupted` or a
dedicated `canEat()`. **S.**

### 3.9 "Far enough from home to run away" is 4.5 blocks

**What the code does.** `AbstractEntityAIGuard.shouldFlee():380`:

```java
if (buildingGuards.shallRetrieveOnLowHealth() && worker.getHealth() < ((int) worker.getMaxHealth() * 0.2D)
      && worker.distanceToSqr(Vec3.atCenterOf(building.getID())) > 20)
```

`distanceToSqr` is a squared distance; 20 is √20 = **4.47 blocks**. Every neighbouring constant is a linear
block count (`MAX_GUARD_DERIVATION 10`, `MAX_FOLLOW_DERIVATION 30`, `MAX_PATROL_DERIVATION 80`,
`:70-85`). **[VERIFIED]** — this is 26.2 §3.6's finding, still unfixed.

**What it produces in play.** A wounded guard standing five blocks from his own hut door decides he is far
from home and runs for it, which is very nearly a no-op, and then spends 75 seconds in `GUARD_REGEN`
(measured on 26.2, §3.4) doing nothing while his post is empty. The condition was plainly meant to read
"don't bother fleeing if you are already home".

**The fix.** `> 400`. One line, **S**, and a **balance change**: it makes wounded guards fight on inside a
20-block bubble around their hut instead of disengaging at 4.5. That is the owner's call, which is why 26.2
left it, but it should be made rather than left indefinitely.

### 3.10 The huscarl's second blow never lands

**What the code does.** `MeleeCombatAI:356-361` splits a huscarl's swing into two `hurt` calls:

```java
double share = (50 + Adaptability / 2.0) / 100.0;
target.hurt(source(PIERCE, user), damage * share);
target.hurt(source,               damage * (1.0 - share));
```

Vanilla `mc/LivingEntity.java:1212-1215`: within the 20-tick `damageCooldownTime`, a second hit with
`damage <= lastHurt` **returns false** and does nothing. Since `share >= 0.5` always, the second call is
always the smaller one. **[VERIFIED]**

**What it produces in play.** A huscarl deals `damage × share`, all of it through
`minecolonies:pierce` — which is in `bypasses_armor` *and* `bypasses_shield`
(`26.3/src/main/generated/data/minecraft/tags/damage_type/*.json`, **[VERIFIED]**). At Adaptability 1 he
loses half his damage; at Adaptability 99 he loses nothing and deals **the whole thing as unblockable
damage** — 32.7 per swing every 16 ticks, ignoring every point of armour on the target. That is why the
huscarl is the strongest unit in the colony by a wide margin (§2.2), and it is not what the split says it
is doing.

**The fix.** Swap the two calls (the larger pierce hit second, so the cooldown branch applies the
difference), or apply the split as one `hurt` against a source chosen by a roll. Three lines, **S**,
**balance change** — it restores the intended half-and-half at low level and cuts the max-level huscarl's
armour-ignoring share.

### 3.11 Spear equipment levels have no middle

**What the code does.** `ModEquipmentTypes.java:176` scores a spear as
`durabilityBasedLevel(stack, new ItemStack(ModItems.spear).getMaxDamage())`, and
`durabilityBasedLevel:229-237` is `min(stack.getMaxDamage() / reference, 5)`. The mod spear's durability is
250 (`ItemSpear:26`); vanilla spear durabilities are their materials'
(`mc/Item.java:522`, `mc/ToolMaterial.java:23-29`). **[VERIFIED]**

| spear | durability | level |
|---|---|---|
| gold | 32 | 0 |
| wooden | 59 | 0 |
| stone | 131 | 0 |
| copper | 190 | 0 |
| iron | 250 | 1 |
| diamond | 1561 | **5** |
| netherite | 2031 | **5** |

Against `IBuilding.getMaxEquipmentLevel():479-490` — hut level 1..4 gives 1..4, hut level 5 gives
`Integer.MAX_VALUE` — this means: **a level-1 guard tower may issue an iron spear, and hut levels 2, 3 and
4 add nothing at all**; the next step up is a level-5 hut, which jumps straight to netherite. There is no
spear at level 2, 3 or 4.

Compare the sword scale, which comes from `material.attackDamageBonus()` via
`Compatibility.registerItemTierIfAbsent(item, material, (int) material.attackDamageBonus())`
(`ModEquipmentTypes:276`): wood/gold 0, stone/copper 1, iron 2, diamond 3, netherite 4 — smooth, and a
diamond sword is licensed at hut level 3 while a diamond *spear* is refused until 5. **[VERIFIED]**

The same durability yardstick also flattens armour and bows: `getArmorLevel:101-124` puts **leather and
gold at the same level 1** despite 7 versus 11 armour points, and gold's negligible durability. The earlier
work's observation that "a gold spear scores like a stone one" is the same defect seen from the other side:
durability is not a proxy for effectiveness, and gold is where it fails hardest.

**What it produces in play.** A player who crafts spears finds that upgrading a guard tower from 1 to 4
changes nothing about what his spearmen carry, and that his diamond spears sit in the rack until the hut is
maxed. A player who stocks gold armour finds his guards treat it as leather.

**The fix.** Score spears the way tools are scored — off `ToolMaterial.attackDamageBonus()`, which
`ModEquipmentTypes.toolMaterialOf:334-351` already recovers from durability — with the mod's own spear
pinned to a chosen tier. ~20 lines including the gold-armour case. **S.** **Balance change** (it changes
which hut level can arm which guard).

### 3.12 The threat table is never pruned, never reset, and its reciprocal-aggro branch is dead

**What the code does.** Three things in `mcol/api/entity/ai/combat/threat/ThreatTable.java`:

* `resetTable():281-285` exists and is **called from nowhere** — `grep -rn 'resetTable()'` over the whole
  tree returns only the definition. **[VERIFIED]**
* Entries are removed only by `removeCurrentTarget():267-276`, which removes at `currentTargetIndex`, and
  `getTarget():189-193` only ever inspects the head. An entry that never reaches the head — a raider the
  guard saw once, at threat 13, below a live enemy at 19 — stays for the life of the entity, holding a
  strong reference to a `LivingEntity` that may have been dead for hours. **[VERIFIED]**
* `getTarget():200-203` reads:

  ```java
  if (current instanceof IThreatTableEntity threatTableEntity && threatTableEntity.getThreatTable().threatList.isEmpty())
  ```

  `current` is a `ThreatTableEntry`, which does not implement `IThreatTableEntity` (`ThreatTableEntry.java`
  is 96 lines and implements nothing; `IThreatTableEntity` is a 14-line interface implemented by
  `EntityCitizen:1940` and `AbstractEntityMinecoloniesMonster:379`). **The branch is unreachable.**
  `current.getEntity() instanceof …` is obviously what was meant. **[VERIFIED]**

**What it produces in play.** The dead branch is the one that would make a raider notice a guard who has
just targeted *it* — reciprocal aggro. Without it, a guard that acquires a raider does not put itself in
the raider's book; the raider only reacts once it is actually hit (`AbstractEntityMinecoloniesMonster:
286-296`) or its own scan finds the guard, which §3.4's twin defect on the raider side often prevents. So
archers on towers shoot at raiders that walk on past.

The retention is slower-burning: on a long-lived server a tower guard accumulates one entry per distinct
enemy ever seen, each pinning a dead entity object, and every `addThreat`/`getThreatFor` is a linear scan
over the lot. It is bounded by the entity's lifetime (nothing is serialised), so a restart clears it, but a
colony that stays loaded across several raids does not.

**The fix.** Two lines for the `instanceof` (**S**, but it is a **behaviour change** — raiders will start
answering archers, which is the point). Roughly 30 lines to sweep the list: drop entries whose entity is
`isRemoved()` or older than `MAX_TRACKING_TICKS` on each `addThreat`, and call `resetTable()` when the
guard leaves combat (`onCombatLeave`, `AbstractEntityAIGuard:221-226`, is the natural hook). **S.**

### 3.13 The raid alert reaches guard towers and then sits there

**What the code does.** `CombatUtils.notifyGuardsOfTarget:147-158`: when a guard acquires an
`AbstractEntityMinecoloniesRaider`, every guard building within `PATROL_DEVIATION_RAID_POINT` = 40² blocks
gets `setTempNextPatrolPoint(target.blockPosition())`. **[VERIFIED]** (The 1600 is compared against
`distSqr`, so the units are right.)

That point is consumed only by `getNextPatrolTarget(true)` (`AbstractBuildingGuards:429-434`);
`getNextPatrolTarget(false)`, which is what the AI calls each `decide()`, returns `lastPatrolPoint`
untouched (`:424-427`). `getNextPatrolTarget(true)` is reached from `startPatrolNext()`, which fires either
when every assigned guard has reported arrival (`arrivedAtPatrolPoint:383-398`) or when the building's
`patrolTimer` counts 5 down to 0 — five colony ticks, i.e. **125 seconds** (`:345-359`, `:412-418`;
`Colony.java:527` schedules `worldTickSlow` at `MAX_TICKRATE` = 500 ticks, `TickRateConstants:11`).
**[VERIFIED]**

**What it produces in play.** A guard alerted to a raider first walks to wherever his previous patrol point
was, reports arrival, and only then picks up the alert. If he was already there, the delay is one `decide()`
(100 ticks). If he was mid-leg, it is the rest of that leg. If he is a lone guard whose current point is
unreachable, it is up to 125 seconds. Meanwhile the mechanism the raid study praised — "the one thing this
does right is warn the defence" — is warning them about where a raider was two minutes ago.

There is a second, quieter waste: the alert is pushed at *every* guard building in range, including ones
set to GUARD or FOLLOW, which never read `tempNextPatrolPoint` at all.

**The fix.** Consume `tempNextPatrolPoint` in `getNextPatrolTarget(false)` as well — it is a one-shot field
and a fresh alert should pre-empt the current leg; skip buildings whose task does not patrol. ~10 lines.
**S.** Behaviour change, worth flagging: guards will break off patrol legs to converge on raiders, which is
what the mechanism is for.

### 3.14 The druid ignores the vertical-vision config, and a fresh druid throws blind

**What the code does.** `DruidCombatAI.getYSearchRange():343-352` returns the literal `Y_VISION + 25`
while guarding and the literal `Y_VISION` otherwise — it never calls `super`, so
`guardVerticalVision` has no effect on druids. `RangeCombatAI:424-434` deliberately does call `super` with
a `Math.max`; the druid copy was not updated when the config was added. **[VERIFIED]**

Separately, `DruidCombatAI.doAttack:143`:

```java
final float inaccuracy = 99f / level;
```

where `level` is the druid's secondary skill, minimum 1 (`CitizenSkillHandler:43`). At level 1 the
inaccuracy passed to `Projectile#shoot` is **99**, and vanilla adds
`random.triangle(0, 0.0172275 × uncertainty)` to each component of a *normalised* direction vector
(`mc/Projectile.java:164-171`) — a spread of ±1.7 against a unit vector. The direction is essentially
random. **[VERIFIED]** It is tolerable by about level 20 and correct near 99.

**What it produces in play.** A newly hired druid throws potions in random directions for his first
several in-game days, which reads as "the druid does nothing", and no amount of raising
`guardverticalvision` will make him notice a mob on a wall.

**The fix.** `Math.max(super.getYSearchRange(), Y_VISION + 25)` in the guard branch and `super` in the
other, matching `RangeCombatAI`; and give the inaccuracy a sane curve — e.g.
`Mth.clamp(15f / level, 0.5f, 6f)`, the same shape the archer already uses
(`RangeCombatAI:219`, `HIT_CHANCE_DIVIDER / (Adaptability + 1)`). ~6 lines. **S.** The inaccuracy change is
a **balance change** in the druid's favour.

### 3.15 The archer's range clamp is applied before the bonuses it was meant to clamp

**What the code does.** `RangeCombatAI.getAttackDistance():229-257` clamps to
`MAX_DISTANCE_FOR_RANGED_ATTACK` = 24 at line 244, then adds the height difference at `:246-249` and the
+10 GUARD bonus at `:251-254`. **[VERIFIED]** — 26.2 §3.6, still unfixed.

**What it produces in play.** An archer 20 blocks above his target on GUARD has an effective attack
distance of 54, not 24, and the constant's own comment ("24 max arrow dist") stops being true. In practice
he still cannot *see* that far (§3.4), so the two defects partly cancel; fixing §3.4 without fixing this
one would make tower archers snipe at 50+ blocks.

**The fix.** Move the clamp to the end of the method. Two lines, **S**, **balance change**. It should ship
together with §3.4 or not at all — they are the same knob seen from two sides.

### 3.16 Smaller things, verified and not worth their own section

* **The archer kites when the enemy is 2.6 blocks away.** `RangeCombatAI:168` tests
  `user.distanceToSqr(target) < RANGED_FLEE_SQDIST` with `RANGED_FLEE_SQDIST = 7`
  (`GuardConstants:84`) — √7 = 2.65 blocks — and then only backs off with probability 1/3 and only when
  the hut is not on GUARD. So an archer with a raider at four blocks stands and shoots it point-blank.
  Meanwhile `moveInAttackPosition:345-358` runs a `PathJobMoveAwayFromLocation` to 7 blocks whenever the
  target is within 2, so the archer's disengage threshold and his re-position threshold disagree. A druid
  has the same pair with an even worse mismatch: he backs away to **12** blocks
  (`DruidCombatAI:208-219`) while his own attack distance is at most 8 (`:187-203`), so he yo-yos.
  Fix: make both read one distance derived from `getAttackDistance()`. ~10 lines, **S**, balance.
* **A knight with a shield in his pack is immune to explosions.** `JobKnight.ignoresDamage:81-100` returns
  true for anything in `IS_EXPLOSION` when `SHIELD_USAGE` is researched and a shield is *anywhere in the
  inventory* — it does not check that the shield is raised, or in hand, or that the guard was facing the
  blast. Creepers, ghasts and TNT do nothing to a researched knight. **[VERIFIED]** Fix: require the shield
  to be the active offhand item. ~5 lines, **S**, balance.
* **`AbstractEntityAIGuard.target` and `AbstractEntityAIFight.target` are dead fields** (§1). Three lines
  to delete, **S**. Worth doing because `shouldSleep:283` reads one of them and looks like it works.
* **`equipInventoryArmor` marks equipment dirty on every call for nothing.**
  `AbstractEntityAIFight.cleanVisibleSlots:357-369` writes `EMPTY` into the four vanilla armour slots, but
  `EntityCitizen.getItemBySlot:1794-1815` reads armour out of `InventoryCitizen` instead, so the write is
  invisible — except that `AbstractEntityCitizen.setItemSlot:691-703` compares old against new and calls
  `markEquipmentDirty()` four times. Called from `decide()` 5 % of the time and from every hut visit.
  Harmless, but it is four spurious sync flags per call. **[VERIFIED]**
* **The training grounds do no damage and cannot.**
  `EntityAICombatTraining:213` calls `trainingPartner.hurt(TRAINING, 0.0F)`, and
  `EntityCitizen.checkIfValidDamageSource:1352-1357` refuses any damage between citizens of the same
  colony before that ever matters. So partner training is animation plus XP. That is fine, and it is
  recorded here so nobody re-investigates it. Per action: combat academy `XP_BASE_RATE = 2`
  (`EntityAICombatTraining:39`), archery `0.2` plus `1` per successful shot
  (`EntityAIArcherTraining:45,65`) — an order of magnitude apart. The two AIs count their attack delay in
  different units, though (`AbstractEntityAITraining.reduceAttackDelay:122-128` decrements once per
  transition, and the archery shadows `RANGED_ATTACK_DELAY_BASE` with a local **10**,
  `EntityAIArcherTraining:60`, against `GuardConstants`' 60 in the academy), so the *effective* XP rates
  are not the ratio of those two numbers. **[VERIFIED]** for the constants; the effective rates were not
  derived and would need a stand.

---

## 4. Performance

**This section is arithmetic over cited constants, not a measurement.** Where a measurement exists it is
26.2's and is labelled.

### 4.1 What ticks

Every guard's job AI and combat AI share one `TickRateStateMachine`, ticked every
`ENTITY_AI_TICKRATE = 5` game ticks (`AbstractEntityCitizen:73, 460`; `CitizenAI:106`), and
`TickRateStateMachine.checkTransition:114-126` decrements by the machine's tick rate, so **the tick-rate
numbers below are game ticks**. `tick():73-106` returns after the first transition that fires, so at most
one runs per machine tick. **[VERIFIED]**

| State | Transition | Period | Site |
|---|---|---|---|
| `NO_TARGET` | `checkForTarget` | 5 t | `TargetAI:53` |
| `NO_TARGET` | `searchNearbyTarget` | **80 t** | `TargetAI:54` via `AttackMoveAI:44` |
| `NO_TARGET` | `decide` | 100 t | `AbstractEntityAIGuard:179` |
| `NO_TARGET` | `shouldSleep` | 200 t | `:172` |
| `NO_TARGET`/`ATTACKING` | `shouldFlee` | 40 t | `:177-178` |
| `ATTACKING` | `tryAttack` | 5 t | `AttackMoveAI:46` |
| `ATTACKING` | `move` | 10 t | `AttackMoveAI:47` |
| `ATTACKING` | `inCombat` | 8 t | `AbstractEntityAIGuard:182` |
| `ATTACKING` | `attackProtect` (knights) | 8 t | `MeleeCombatAI:122` |
| `NEEDS_ITEM`, `INVENTORY_FULL` | `checkForTarget` / `searchNearbyTarget` | 5 t / 80 t | `MeleeCombatAI:123-124`, `RangeCombatAI:103-104`, `DruidCombatAI:108-109`; group at `BehaviourStateGroup:16` |
| `GUARD_REGEN` | `regen` | 40 t | `AbstractEntityAIGuard:175` |

**[VERIFIED]** for every row.

### 4.2 The three costs that scale

**Target acquisition.** One `getEntitiesOfClass(LivingEntity.class, box)` per guard per 80 ticks, over a
32 × 6 × 48 box (§3.4) — six to eight entity sections. Then, per candidate that passes
`isEntityValidTarget`, one `Sensing.hasLineOfSight` (`TargetAI:177`), which is a `level.clip` over up to 32
blocks. For `G` guards and `R` candidates in the box:

```
scans/tick      = G / 80
ray casts/tick  = G · R / 80
```

At `G = 40`, `R = 30` (a raid pressing a barracks): **0.5 scans and 15 ray casts per tick**, each ray
roughly 32-96 block-state lookups → order 1500 lookups/tick. That is small. Raising
`guardVerticalVision` from 3 to 12 multiplies the box volume by 4 and, with it, `R` — so the vision fix in
§3.4 is the one change here with a measurable price, and the honest recommendation is 8-12, not 28.

**`notifyGuardsOfTarget`.** `CombatUtils:147-158` iterates **every building in the colony** on every target
change to a raider. `onTargetChange` can fire once per `checkForTarget`, i.e. once per 5 ticks per guard,
and target changes *are* frequent during a raid because `ThreatTable.getTarget():157-206` will switch to a
newly-seen enemy whenever its threat exceeds the current target's by 10 % (in melee range) or 30 %
(outside) — and a fresh entry starts at `10 + up to 7 for proximity + up to 2 for being wounded`
(`ThreatTable:88-97`), i.e. up to 19 against a current target sitting at 10. Worst case for `G` guards and
`B` buildings:

```
map walks/tick = G / 5 · B
```

At `G = 40`, `B = 150`: **1200 map-entry visits per tick**. Still not a disaster, but it is the largest
single avoidable number in the guard path, and it exists to set one `BlockPos` on the handful of buildings
within 40 blocks. A `getBuildingsWithinRange`-style query, or simply caching the guard-building subset,
removes it. ~15 lines, **S**.

**`callForHelp`.** `EntityCitizen:1565-1606` iterates every citizen in the colony on each hit taken, gated
by a 100-tick per-citizen cooldown (`CALL_HELP_CD`, `:140`). For `G` guards under fire and `C` citizens:
`G/100 · C` = 20 visits/tick at `G = 40, C = 50`. Negligible.

**Threat table scans.** Linear, over a list that never shrinks (§3.12). At 100 retained entries and 30
candidates per scan, `40/80 × 30 × 100` = 1500 comparisons/tick. Negligible as CPU; the retained entity
references are the real cost.

**Pathfinding.** 26.2 measured this and the answer was "nothing": the pool ran at **0.0-0.1 % busy** with
six guards in and out of combat, on flat ground, and the two plausible hot spots (a knight re-pathing per
swing, an archer's edge-walk) were both disproved by measurement (`26.2/audit/GUARD-AUDIT.md` §1.3, §2.1,
§2.2). Nothing in 26.3 changes the shape of those call sites. The caveat that study attached still stands
and is the honest bound: **that was flat ground**, and the unreachable-target branch (§1, `AttackMoveAI:
84-95`, up to 66 path attempts before release) is the one that gets expensive on real terrain. That branch
has not been measured on either tree.

### 4.3 What I would measure first

If someone stands up a stand: guard count versus main-thread milliseconds during a raid, with
`guardVerticalVision` at 3 and at 12; and the `PathfindingStats` node-limit-hit rate on hilly terrain with
guards chasing raiders up cliffs. Those are the two numbers this section could not derive.

---

## 5. Things that are fine

Whoever picks this area up next need not re-audit these. Each was read end to end and found correct.

* **The arrow damage pipeline.** `CustomArrowEntity.onHitEntity:98-137` cancels vanilla's velocity scaling
  exactly, so an arrow deals `ceil(baseDamage)` at any range, and restores the value before the on-hit
  callback. The save/load overrides (`:157-167`) correctly refuse to persist guard arrows. **[VERIFIED]**
* **Armour attribute bookkeeping.** `InventoryCitizen.transferArmorToSlot:359-376` /
  `moveArmorToInventory:382-394` pair with `AbstractEntityCitizen.onArmorAdd:726-739` /
  `onArmorRemove:709-724`, both of which `removeModifier(id)` before `addTransientModifier`, and vanilla's
  `collectEquipmentChanges` (`mc/LivingEntity.java:2942-2947`) does the same. Nothing double-counts.
  **[VERIFIED]**
* **Health modifiers survive a restart.** `CitizenData.initEntityValues:584-611` sets the base, then
  `updateLevel()` → `onLevelUp()` re-applies the level modifier, then `onJobChanged` →
  `AbstractJobGuard.initEntityValues` re-applies the building and config ones.
  `addHealthModifier:62-75` preserves the health *percentage* across the change, so no free healing.
  **[VERIFIED]**
* **The crit roll.** `nextDouble() > 1/(1 + GUARD_CRIT)` is exactly "chance = 1 − 1/(1+e)", and with no
  research it is `> 1`, which never fires. Correct in both `MeleeCombatAI:531` and `RangeCombatAI:322`.
  **[VERIFIED]**
* **The fake-player filter** in `ThreatTable.addThreat:70-73` and `TargetAI.isEntityValidTarget:101`. The
  port note explaining why it was restored is accurate. **[VERIFIED]**
* **The direction-cycling search sweep** (`TargetAI:231-239`). Upstream rolled one of four directions at
  random per scan, giving no upper bound on how long an enemy could stand unnoticed; cycling caps it at
  four scans at identical cost, and the start index is still randomised so neighbouring guards stay out of
  phase. **[VERIFIED]**
* **The sleeping-colleague scan continuation** (`TargetAI:157-175`, `DruidCombatAI:274-288`). The
  `skipped` flag keeps the wake-up firing at most once per scan without hiding the rest of the box.
  **[VERIFIED]**
* **The knight-with-a-spear weapon selection.** `EntityAIMelee.getToolsNeeded:90-114` and
  `MeleeCombatAI.getWeaponType:198-215` agree with each other, never strand an armed knight in `PREPARING`,
  and fall back to a sword when spears run out. `hasTool:636-648` reads `getToolsNeeded()` rather than the
  fixed list, which is what makes that work. **[VERIFIED]**
* **`AbstractBuildingGuards.getRallyLocation:615-679`.** Every exit is handled: banner removed, player
  gone, out of colony, telescope research, and the deliberate decision not to clear the location when the
  banner is merely stowed is documented with its cost. **[VERIFIED]**
* **`FOLLOW` teleport recovery.** `AbstractEntityAIGuard.follow:463-473` teleports a guard that falls more
  than `MAX_FOLLOW_DERIVATION` = 30 blocks behind, so a player sprinting off does not permanently lose his
  escort. **[VERIFIED]**
* **Training does nothing dangerous** (§3.16, last bullet).
* **`PATROL_PERMANENT`** is registered only on the stable (`BuildingModules:209`) and is cavalry's; every
  other guard building's task list omits it (`:616`). No guard building offers an option it has no
  behaviour for. **[VERIFIED]**

---

## 6. What a player notices first

Ranked by how quickly it turns into a complaint, which is not the same as the ranking in §3.

1. **"My guards ignore the mob on the wall / the roof / the hill."** §3.4. Measured on 26.2: zero
   acquisitions in 250 seconds against a target six blocks up in clear sight.
2. **"My level 5 barracks guards are still wearing leather."** §3.2. This is the one that makes a player
   think the whole guard system is broken, because everything else in the colony upgrades and this does
   not.
3. **"Guard towers are useless, only the barracks patrols."** §3.3.
4. **"My archers won't shoot until things are on top of them."** §3.4 plus §3.16's kite threshold.
5. **"The marksman is worse than the ranger."** §3.5 — and this one arrives *late*, after the player has
   spent the research, which makes it the most annoying of the five.
6. **"My guard died and his diamond armour is gone."** §3.7. Rare, and infuriating when it happens.
7. **"Guards sleep at noon."** 26.2 §3.3, still true, now switchable. Between 2.6 % and 16.8 % of a guard's
   day, measured.
8. **"Wounded guards run home for a minute and a quarter."** 26.2 §3.4 (75 seconds measured) plus §3.9's
   4.5-block trigger.
9. **"My escort is slow and won't heal."** §3.8, Follow only.
10. **"Creepers don't hurt knights."** §3.16. Players notice this one and like it, which is its own
    problem.

---

## 7. What I could not verify

1. **Nothing here was run.** Every damage, health and per-tick number is arithmetic over cited constants.
   Confirming any of them means a stand: `26.2/audit/GUARD-AUDIT.md` §9 is a complete, reproducible recipe
   and the fastest route. The two figures I would most like measured are the melee doubling in play
   (one `Probe`-style read of `MeleeCombatAI#getAttackDamage`'s return, §3.1) and the marksman curve (§3.5,
   two readings at low and high Adaptability).
2. **The retained-threat-table growth rate.** I verified that nothing prunes and nothing resets
   (§3.12); I did not verify how large the list actually gets over a multi-day raid, which needs a live
   heap read.
3. **`AttackMoveAI`'s unreachable-target release on real terrain.** The arithmetic bound (up to 66 path
   attempts) is derivable; whether it is reached in practice is not. 26.2 could not reproduce it on flat
   ground.
4. **Anything client-side.** Guard hut GUI, the combat status icons, the rally-banner renderer and the
   settings layouts were not read. Two fixes above would need client work if they change what the hut
   shows: §3.4's vision (if `getBonusVision` is surfaced) and §3.2's armour band (if the hut is to explain
   what tier it licenses). Everything else in this study is server-side only.
5. **PvP mode.** `pvp_mode` branches exist in `MeleeCombatAI:340-343`,
   `EntityCitizen.checkIfValidDamageSource:1360-1364` and the `guardpvp` damage type; none were followed.
6. **Modded weapons.** The Tinkers fall-through (`MeleeCombatAI:518`) and
   `Compatibility.isCustomWeapon` were not exercised. `ModEquipmentTypes.canPerformDefaultActions:381-384`
   already carries a `TODO(port-26.2)` noting that tag-less modded tools are invisible to colonists.
7. **One thing I saw and did not pursue, because it is cavalry's:**
   `EntityCitizen.handleDamagePerformed:1411-1445` contains **the same eight-line cavalry
   damage-split block twice, back to back**, with identical comments. Both run, so a mounted cavalryman's
   `CAVALRY_RANGED_DAMAGE_VULNERABILITY` is applied twice and the horse takes two shares of the hit.
   **[VERIFIED]** by reading; flagged here for the cavalry work rather than fixed, since the
   file is being edited concurrently.

---

## 8. Fix sizes at a glance

| § | Finding | Size | Balance? |
|---|---|---|---|
| 3.1 | Melee damage doubled | S (1 line, +10 to re-tune) | **Yes, sweeping** |
| 3.2 | Armour never re-requested | S (~15) | No |
| 3.3 | Guard tower wanders instead of patrolling | S (~10) | Behaviour |
| 3.4 | Search box smaller than the weapon; six blocks tall | S (~20 + config) | **Yes** |
| 3.5 | Marksman damage collapses with level | S (~15) | **Yes** |
| 3.6 | Ranger gains no health per level | S (~12) | **Yes** |
| 3.7 | Worn armour destroyed on death outside the claim | S (~10) | No |
| 3.8 | Guard on Follow never eats | S (~5) | No |
| 3.9 | `shouldFlee` squared-distance mix-up | S (1 line) | **Yes** |
| 3.10 | Huscarl's second blow never lands | S (3 lines) | **Yes** |
| 3.11 | Spear (and gold-armour) equipment scoring | S (~20) | **Yes** |
| 3.12 | Threat table never pruned; dead reciprocal-aggro branch | S (~30) | Behaviour |
| 3.13 | Raid alert not consumed promptly | S (~10) | Behaviour |
| 3.14 | Druid vertical vision and throw accuracy | S (~6) | **Yes** |
| 3.15 | Archer range clamp before bonuses | S (2 lines) | **Yes** |
| 3.16 | Kite thresholds, explosion immunity, dead fields | S (~20 total) | Mixed |
| 4.2 | `notifyGuardsOfTarget` walks every building | S (~15) | No |

Two items would grow past S if taken further than described: a real patrol route for guard towers rather
than reusing the automatic one (**M**, and `docs/studies/territory-mechanics.md` §4 already scopes that at
~115 lines), and rebuilding the equipment-level system on effectiveness rather than durability across all
types rather than just spears and gold (**M**, ~250 lines, touches every worker).

**No proposal in this study needs a mixin, and none needs an access widener.** Every site named is a
`public` or `protected` method in the mod's own tree; the only vanilla behaviour any of them depends on —
`EnchantmentHelper.modifyDamage`, `CombatRules.getDamageAfterAbsorb`, `Projectile#shoot`,
`AbstractArrow#onHitEntity` — is already called from mod code today. **No upstream MineColonies asset is
required by any of them.** Nothing proposed depends on the integrated server or single player.
