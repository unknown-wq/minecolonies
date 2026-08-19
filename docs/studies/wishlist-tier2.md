# Wishlist estimate — spearman, alchemist, undead camels

Estimate only. No feature code was written. Every claim below is either a `path:line` citation into
this tree, into `/opt/mc-src` (decompiled vanilla 26.2), or into the upstream oracle at
`/workspace/ldtteam/minecolonies` — or it is flagged as an inference.

Paths are relative to `26.2/src/main/` unless they start with `/opt` or `/workspace`.

**A note on scope.** The brief assigned "tier 2" but then asked, repeatedly and specifically, for a
verdict on all three features and on all three of the design calls, plus an ordering across them.
The ordering question cannot be answered from one feature, so all three are priced here. The
alchemist (tier 2) is the deepest section; the other two are priced to the same standard of citation
but with less exploration of alternatives.

**What could not be checked.** There is no display in this container, so `runClient` does not start.
Nothing below about GUI rendering, model/texture appearance, or how anything *looks* has been
observed; those are read off the code and the shipped assets. No build was run for this study — it is
an estimate, and the tree was not modified.

---

## Headline

| | His guess | Verdict | Real size | Biggest trap |
|---|---|---|---|---|
| **Spearman** | cheap-ish; new guard type + AI + new equipment type | **Cheaper than he thinks.** Design call holds. | 1–2 new files, ~9 touched, ~250 lines | The equipment type he budgeted for **already exists**, and so does the charged attack — vanilla drives it for non-players. But it runs on its own damage number, damage source and cooldown, and it fights the shield for the use slot |
| **Alchemist** | cheap; crafter on the existing framework | **Already built. Zero.** Design call is moot; the "apothecary" does not exist | 0 new files. The sulfur idea is separately **not viable** as written | There is no sulfur *item* in 26.2 — sulfur is a building-block family and a physics mob, neither of which the alchemist can use |
| **Undead camels** | cheap-ish; mounted raiders inside the existing desert raid | **Design call holds, cost guess is wrong — it is the expensive one.** | 2–4 new files, ~8 touched, ~600–900 lines | `AbstractEntityMinecoloniesRaider.getNavigation()` overrides vanilla's mount delegation with a **covariant return type**, so vanilla's whole mounted-mob mechanism is structurally unavailable to raiders |

**Order:** alchemist (free — it is a documentation fix), then spearman, then camels.
**Not worth doing as specified:** the sulfur recipes. See §2.

---

## 1. The spearman

### 1.1 His design call: "register it as a third guard type in the existing tower"

**Holds up, and is more obviously right than he realises.** Two of his premises are wrong in his
favour.

**"a third guard type."** There are already **six** registered guard types, not two:

* `api/colony/guardtype/registry/ModGuardTypes.java:12-24` — knight, ranger, druid, cavalry, huscarl,
  marksman.
* `apiimp/initializer/ModGuardTypesInitializer.java:26-90` builds all six through the same
  `GuardType.Builder`, and `:101-106` registers each with a plain
  `Registry.register(CommonMinecoloniesAPIImpl.GUARD_TYPE_REGISTRY, …)`. The registry is open. Nothing
  enumerates a fixed set.

**"the tower's guard-type list" — open, and already four long.** The tower's entry is a builder chain:

* `apiimp/initializer/ModBuildingsInitializer.java:230-245` — `KNIGHT_TOWER_WORK`,
  `RANGER_TOWER_WORK`, `MARKSMAN_TOWER_WORK`, `HUSCARL_TOWER_WORK`, then the shared modules.
* The producers themselves are at `core/colony/buildings/modules/BuildingModules.java:571-587`, each a
  one-liner: `new GuardBuildingModule(ModGuardTypes.x.get(), true, (b) -> 1)`.

Adding a spearman to the tower is **one `.addBuildingModuleProducer(SPEARMAN_TOWER_WORK)` line** plus
the three-line producer next to the others. There is no enumeration of exactly two anywhere; his
worry does not exist.

The mirror image is the barracks (`BuildingModules.java:554-568`) — five `*_BARRACKS_WORK` producers,
same shape. Whether to add the spearman there too is a design choice, not a cost.

`GuardBuildingModule` itself is generic over `GuardType`
(`core/colony/buildings/modules/GuardBuildingModule.java:39-54`), so no new module class is needed.

### 1.2 His trap: "the spear has to be registered as a new equipment type"

**This trap does not exist. It is already done.**

* `api/equipment/ModEquipmentTypes.java:52` declares `spear`, and `:167-177` registers it — matching
  both the mod's own `ModItems.spear` and the vanilla `#minecraft:spears` tag, with a
  durability-derived level.
* `api/items/ModItems.java:37` — the item. `core/items/ItemSpear.java` — the item class, with a
  throwable `SpearEntity`, and models/textures/recipe/damage type already shipped
  (`resources/assets/minecolonies/models/item/spear.json`,
  `generated/data/minecolonies/recipe/spear.json`).

**What the request system does with it, verified.** `api/colony/requestsystem/requestable/Tool.java:26`
is `Tool implements IDeliverable`, constructed at `:53` as `new Tool(equipmentType, minLevel, maxLevel)`
— it is generic over *any* registered `EquipmentTypeEntry`. Guards feed it from
`core/entity/ai/workers/AbstractEntityAIBasic.java:1035` and `:1090`. So the answer to "what happens
today to a weapon the mod has no equipment type for" is: it is invisible — `checkForToolOrWeapon`
never fires and the colonist never asks for it. But the spear is **not** in that position. The spear is
a registry entry, has been for a while, and the guard tooling already carries a spear-specific special
case: `core/entity/ai/workers/guard/AbstractEntityAIFight.java:133` passes minimum level `-1` when the
stack `instanceof ItemSpear`.

So: **new registry entry, not a new subsystem — and the registry entry is already there.** His single
biggest budgeted item is already paid for.

### 1.3 The thing he did not price: there is already a spear guard

`JobCavalry` is a **mounted spear guard**, in this tree, working:

* `core/colony/jobs/guard/JobCavalry.java:37` — the job; `getWeaponType()` returns
  `ModEquipmentTypes.spear`.
* `core/entity/ai/workers/guard/CavalryCombatAI.java` — 68 lines, and all it overrides is attack
  damage (`:30-34`), weapon type (`:41-45`), attack distance (`:52-55`) and the combat icon.
* `core/entity/ai/workers/guard/EntityAICavalry.java` — 420 lines, but almost all of that is
  *finding and riding a horse*, which a foot spearman does not need.

A **dismounted** spearman is therefore the cavalry with the mount half deleted. That is the cheapest
possible starting point and it is already in the tree — and it is stable ground, because the cavalry
is upstream's rather than this port's invention (`EntityAICavalry` and `CavalryHorseEntity` both exist
at `/workspace/ldtteam/minecolonies/src/main/java/com/minecolonies/core/entity/`), so it will not shift
under a future merge.

### 1.4 The precedent for the cost: the huscarl

The huscarl is the most recently added guard type and is exactly the shape the spearman would be.
Its complete footprint, from a whole-tree grep:

| File | What changes |
|---|---|
| `core/colony/jobs/guard/JobHuscarl.java` | **new**, 20 lines |
| `api/colony/guardtype/registry/ModGuardTypes.java` | +1 id, +1 field |
| `apiimp/initializer/ModGuardTypesInitializer.java` | +11-line builder block |
| `api/colony/jobs/ModJobs.java` | +1 id, +1 field |
| `apiimp/initializer/ModJobsInitializer.java` | +1 registration |
| `api/util/constant/translation/JobTranslationConstants.java` | +2 keys |
| `core/colony/buildings/modules/BuildingModules.java` | +2 producers (tower + barracks) |
| `apiimp/initializer/ModBuildingsInitializer.java` | +2 lines |
| `core/entity/ai/workers/guard/EntityAIMelee.java` | branch on the job (`:34-41`) |
| `core/entity/ai/workers/guard/MeleeCombatAI.java` | branch on `isHuscarl()` (`:149-151, 186, 225`) |
| `api/research/util/ResearchConstants.java` + `core/generation/defaults/DefaultResearchProvider.java:155, 923` | research unlock |
| `resources/assets/minecolonies/lang/manual_en_us.json` | ~5 keys |

**No new model, no new texture, no new blueprint.** `JobHuscarl` reuses the knight's model; so does
`JobCavalry` (`core/colony/jobs/guard/JobCavalry.java:83-87`, `ModModelTypes.KNIGHT_GUARD_ID`). A
spearman would do the same.

### 1.5 The charged attack — cheaper than expected, but on a separate track

He is right that a charged attack suits "holds position at the wall." It suits it better than he knows,
and it is nearly free — but not in the way you would guess, and the way it is free is itself the trap.

**Holding position is free.** `GuardTaskSetting.GUARD` already exists on every guard building
(`core/colony/buildings/modules/BuildingModules.java:599-605`, the `GUARD_SETTINGS` producer, which
lists `PATROL, GUARD, FOLLOW, PATROL_MINE`). There is nothing to build.

**26.2's spear is a charge weapon, and vanilla drives the charge for mobs, not just players.**
This is the finding that changes the estimate. Traced end to end in `/opt/mc-src`:

* `/opt/mc-src/net/minecraft/world/item/Items.java:1628-1650` — seven vanilla spears, wood through
  netherite. (The comment at `ModEquipmentTypes.java:169-174` asserting this is **true**; checked,
  per the rule of this port.)
* `/opt/mc-src/net/minecraft/tags/ItemTags.java:173` — the `#minecraft:spears` tag.
* `/opt/mc-src/net/minecraft/world/item/Item.java:484-524` — `Properties.spear(...)` attaches
  `KINETIC_WEAPON`, `PIERCING_WEAPON`, `ATTACK_RANGE` (2.0 base / 4.5 charged, out to 6.5) and
  `MINIMUM_ATTACK_CHARGE = 1.0F`.
* `/opt/mc-src/net/minecraft/world/entity/LivingEntity.java:2741` → `:3403-3407` → `:3448-3449` —
  the ordinary `LivingEntity` tick calls `updatingUsingItem()` → `useItem.onUseTick(...)`. **Generic
  `LivingEntity`, not `Player`.**
* `/opt/mc-src/net/minecraft/world/item/ItemStack.java:1089-1099` — `onUseTick` routes a stack with
  `KINETIC_WEAPON` into `KineticWeapon.damageEntities(...)`.
* `/opt/mc-src/net/minecraft/world/item/component/KineticWeapon.java:99-144` — the charge itself:
  sweeps the attack range, and on each entity tests dismount / knockback / damage conditions.
  Line 105 is the giveaway: `float actionFactor = livingEntity instanceof Player ? 1.0F : 0.2F;`
  and `Condition.test` (`:165-167`) multiplies the speed thresholds by it. **Vanilla explicitly
  supports non-player spear users and makes the thresholds five times easier for them.**

So the mechanic is obtained by having the spearman call `startUsingItem(InteractionHand.MAIN_HAND)`.
That is a call the mod already makes — `MeleeCombatAI.java:130` does exactly this for the shield.

**And it lands on the "holds position" fantasy exactly.** The damage condition on a spear is
`Condition.ofRelativeSpeed(...)` (`Item.java:507`), and relative speed is
`max(0, attackerProjection − targetProjection)` (`KineticWeapon.java:122-123`). A spearman standing
still while a raider runs onto him generates relative speed from the *raider's* motion. A braced pike
impales the charge. That is free, and it is better than anything that would have been written by hand.

**The trap: it runs on a completely separate track from the mod's combat.** `MeleeCombatAI.doAttack`
(`core/entity/ai/workers/guard/MeleeCombatAI.java:197-235`) computes its own damage and calls
`target.hurt(source, damage)` with `DamageSourceKeys.GUARD` (and swaps to `GUARD_PVP` under
`pvp_mode`). The kinetic charge does none of that:

* **Different damage number.** `KineticWeapon.java:107, 131` —
  `getAttributeBaseValue(Attributes.ATTACK_DAMAGE) + floor(relativeSpeed × damageMultiplier)`. It does
  not consult `MeleeCombatAI.getAttackDamage()`, so guard level, research and the building's
  contribution are all bypassed.
* **Different damage source.** It goes through `livingEntity.stabAttack(...)` (`:132`), not
  `DamageSourceKeys.GUARD`. PvP mode's source switch and anything keyed on the guard damage type will
  not see it.
* **Different cooldown.** `wasRecentlyStabbed` / `rememberStabbedEntity` (`:119-121`) is its own
  bookkeeping, independent of `nextAttackTime`.
* **It contends for the use slot.** `LivingEntity.startUsingItem` (`:3475-3489`) sets a single
  `this.useItem`, and `:3477` refuses while `isUsingItem()`. A guard raising a shield in the offhand
  (`MeleeCombatAI.java:126-131`) and a spearman charging in the main hand **cannot both be using an
  item**. The spearman must be excluded from the shield branch — precisely the way `isHuscarl()`
  already excludes the huscarl at `:126`. That line is the template; it is one more disjunct.

**So the decision is a design call, not a cost.** Either let vanilla drive the charge and accept that
the spearman's damage scales off the attribute rather than off the mod's guard curve — cheap, correct
sounds and animations, and it will drift with vanilla — or reimplement it in `MeleeCombatAI` terms so
it stays on the mod's balance curve, which is maybe eighty more lines. Given how much this codebase
cares about guard levelling, **the second is probably right, with the vanilla numbers read off the
item as inputs.** Either way the estimate stands; the first option would shave perhaps fifty lines.

**A free specification, thrown in.** Vanilla also ships the *behaviour*:
`/opt/mc-src/net/minecraft/world/entity/ai/goal/SpearUseGoal.java` (used by `Zombie.java:120` and
`ZombifiedPiglin.java:72`) and the brain-based `SpearApproach`/`SpearAttack`/`SpearRetreat`
(`PiglinAi.java:179-180`). None of them is directly usable — `SpearUseGoal<T extends Monster>` and a
citizen is not a `Monster` — but they are a written-down answer to "what does a spear user do", with
tuned reposition distances (`SpearUseGoal.java:17-20`). Read them before inventing the state machine.

### 1.6 Size

* **New files:** 1 (`JobSpearman.java`, ~25 lines). Optionally a second (`SpearCombatAI extends
  MeleeCombatAI`, ~70 lines) if you would rather subclass than branch — the huscarl precedent says
  branch, the cavalry precedent says subclass. Either is fine.
* **Touched files:** ~9, per the huscarl table above, plus `EntityAIMelee.java` for the gear list.
* **Rough lines:** 200–300 including the charged attack.
* **New lang keys:** ~5–7 (`com.minecolonies.job.spearman`, the button key,
  `minecolonies:spearman.job.desc`, `.skills.desc`, plus a research effect string). Remember `%s` only.
* **Datagen:** research entry (if gated like huscarl/marksman), lang. `sounds.json` only if you want a
  distinct voice — reuse `"knight"` and it is free.
* **New models/textures:** none, if it reuses `KNIGHT_GUARD_ID`. A combat status icon
  (`textures/icons/work/…png`) is optional; the cavalry has one.
* **Old saves:** unaffected. Guard types and jobs are registry entries keyed by `Identifier` and
  resolved by name; adding one cannot invalidate an existing citizen. *(Inference from the registry
  shape at `ModGuardTypesInitializer.java:101-106` and `ModJobsInitializer.java`; not tested against a
  real old save, and there is no client here to load one.)*

### 1.7 Traps, verified

| Trap | Verdict |
|---|---|
| Needs a new equipment type | **False.** `ModEquipmentTypes.java:167` |
| Request system cannot order a spear | **False.** `Tool.java:53` is generic; `AbstractEntityAIFight.java:133` already special-cases spears |
| Tower enumerates exactly two guard types | **False.** Four in the tower, six registered, open builder |
| Needs a new building | **False**, and he was right to avoid it |
| Vanilla's spear charge is player-only | **False.** `KineticWeapon.java:105` branches on `instanceof Player` to *ease* the thresholds for mobs; the whole path is `LivingEntity`-generic |
| …so it works out of the box | **Half true, and new.** It fires, but on its own damage number, its own damage source and its own cooldown, none of which the mod's guard curve or PvP mode can see |
| A spearman can carry a shield like a knight | **False, and new.** `LivingEntity.startUsingItem:3477` allows one used item at a time; charging and blocking are mutually exclusive |
| Needs a new model/texture | **False**, if it reuses the knight model |
| Upstream already has one | **No.** `/workspace/ldtteam/minecolonies` has "spearman" only as the *amazon raider* (`EntityAmazonSpearman`, `ISpearmanMobEntity`) — same as here. No colonist spearman upstream. Nothing to lift. |

### 1.8 In play

A fifth button on the guard tower's hiring page. He hires a spearman, the colony orders a spear through
the normal request chain (the mod's own, or any of the seven vanilla ones), and the spearman stands
where he is told with the spear levelled. Raiders that run onto him take the brunt — the damage scales
off *their* closing speed, so a charging mummy impales itself and a cautious one does not. He hits
further than a knight and cannot carry a shield. That last part is a genuine trade the player can feel,
and it comes out of the engine rather than out of a balance table, which is the best kind.

Visually it will look like a knight holding a spear; the reach and the rhythm are what distinguish it.
If that is not enough differentiation, the art is where extra cost would go, not the mechanics. **Not
observed** — there is no client here, and the spear's held-item animations
(`/opt/mc-src/net/minecraft/client/model/effects/SpearAnimations.java`) were read, not seen.

---

## 2. The alchemist

### 2.1 The finding

**The alchemist already exists in this port, complete.** This is not an estimate; it is in the tree:

* `core/colony/buildings/workerbuildings/BuildingAlchemist.java` — "Crafts potions and grows netherwart"
  (`:37`), tracks soul sand, leaves and brewing stands (`:88-101`).
* `core/colony/jobs/JobAlchemist.java`, `core/entity/ai/workers/crafting/EntityAIWorkAlchemist.java`.
* `apiimp/initializer/ModBuildingsInitializer.java:650` and
  `apiimp/initializer/ModJobsInitializer.java:321` — registered.
* `core/colony/buildings/modules/BuildingModules.java:253, 256, 258` — worker module, crafting module,
  **and a separate brewing module**.
* `core/blocks/huts/BlockHutAlchemist.java`, `core/client/model/{Male,Female}AlchemistModel.java`,
  citizen textures in every style
  (`generated/assets/minecolonies/textures/entity_icon/citizen/{default,medieval,nordic,eastasian,incan,…}/alchemist*.png`).
* `core/generation/defaults/workers/DefaultAlchemistCraftingProvider.java` — its datagen.
* **Blueprints: 5 levels in each of 21 styles**, under `resources/blueprints/minecolonies/*/craftsmanship/luxury/`.
  The blueprint question he correctly identified as the one that could dominate a new crafter's cost is
  already answered — every shipped style has the hut. *(Not present in `medievalbirch`,
  `medievaldarkoak`, `medievaloak`, `truedwarven` or `template`; those styles are incomplete for many
  huts, so this is not alchemist-specific.)*

**Cost of "add an alchemist": zero.** The design call — crafter on the existing framework, like the
enchanter and the baker — is exactly what was done, by upstream, years ago. Upstream has it too
(`/workspace/ldtteam/minecolonies/src/main/java/com/minecolonies/core/colony/buildings/workerbuildings/BuildingAlchemist.java`),
which is where this port's copy came from.

### 2.2 "And look at the apothecary too"

**There is no apothecary.** A case-insensitive grep for `pothecar` across all of `26.2/src/main`
returns nothing, and the same grep upstream returns nothing. MineColonies' potion crafter has only
ever been called the Alchemist. He has one building in mind under two names. Nothing to look at, and
nothing to decide between.

### 2.3 Sulfur — the part that is actually a request, and why it does not work

He asked for "a range of sulfur recipes." I looked at what 26.2's sulfur actually is.

**There is no sulfur item.** Verified in `/opt/mc-src`:

* `/opt/mc-src/net/minecraft/world/level/block/Blocks.java:4884-4905` and
  `/opt/mc-src/net/minecraft/references/BlockItemIds.java:36-49, 729` — sulfur is a **stone-like
  building-block family**: `sulfur`, `potent_sulfur`, slab/stairs/wall, `polished_sulfur` + its three,
  `sulfur_bricks` + its three, `chiseled_sulfur`, `sulfur_spike`. Fifteen blocks. Every one is a
  `registerBlock` item at `/opt/mc-src/net/minecraft/world/item/Items.java:94-107, 1921`.
* `/opt/mc-src/net/minecraft/world/level/biome/Biomes.java:63` — a `sulfur_caves` biome.
* `/opt/mc-src/net/minecraft/world/level/block/PotentSulfurBlock.java:31-90` — a geyser block driven
  by water above and a tag-matched block below. Terrain, not an ingredient.

**The "sulfur cube" is a mob, not an item.**

* `/opt/mc-src/net/minecraft/world/entity/monster/cubemob/SulfurCube.java` — a slime-shaped physics
  mob. `/opt/mc-src/net/minecraft/world/entity/SulfurCubeArchetypes.java:24-34` gives it twelve
  archetypes (`bouncy`, `sticky`, `explosive`, `hot`, `light`, `high_resistance`, …), selected by which
  block it has absorbed (`SulfurCubeArchetype.items`, `/opt/mc-src/net/minecraft/world/entity/SulfurCubeArchetype.java:23-30`).
* `/opt/mc-src/net/minecraft/world/item/component/SulfurCubeContent.java:15` — the absorbed block is a
  data component; `SulfurCube.java:907` drops it on death.
* The only sulfur-cube *items* are `SULFUR_CUBE_BUCKET` and `SULFUR_CUBE_SPAWN_EGG`
  (`/opt/mc-src/net/minecraft/world/item/Items.java:1250, 1389`).

So a sulfur cube is a movement/redstone toy that carries a block around. It is not a reagent, it does
not drop a reagent, and **nothing in 26.2 turns sulfur into a potion.** The alchemist has no possible
sulfur recipe that is not invented from nothing.

**Where sulfur does belong: the stonemason, and it may already be free.** Crafters here validate
ingredients against item tags — `core/colony/buildings/workerbuildings/BuildingStonemason.java:65-75`
uses `CraftingUtils.getIngredientValidatorBasedOnTags(CRAFTING_STONEMASON)`, and the tag is datagen'd
to `generated/data/minecolonies/tags/item/stonemason_ingredient.json` (currently 40-odd entries:
bricks, deepslate, blackstone, purpur, copper…). Adding `minecraft:sulfur` and `minecraft:sulfur_bricks`
to that provider is **a handful of lines in one datagen class**, after which the stonemason can be
taught every sulfur slab/stair/wall/polished/chiseled recipe the player wants, because the recipes
themselves are vanilla's and the mod learns them by teaching.

That is the coherent version of "generate a range of sulfur recipes," it costs almost nothing, and it
has nothing to do with the alchemist.

### 2.4 Size

* **Alchemist:** 0 new files, 0 touched files, 0 lines, 0 lang keys. It ships.
* **Sulfur → alchemist:** not viable as specified. Would require inventing a sulfur reagent item, its
  model, texture, recipe and an unexplained fiction for why a decorative sandstone-analogue brews. If
  he wants it anyway, price it as a new item + a new recipe set: 1 new item class, ~4 datagen entries,
  2 assets, ~120 lines — but it is invented content, not 26.2 content.
* **Sulfur → stonemason (the recommendation):** ~10 lines in
  `core/generation/defaults/workers/` (the stonemason tag provider), regenerate datagen. 0 new lang
  keys. 0 new assets. Old saves unaffected — item tags are reloadable data.

### 2.5 Traps

| Trap | Verdict |
|---|---|
| "a new worker needs a hut in every shipped style" | **True in general, moot here** — 21 styles × 5 levels already ship |
| Apothecary might be the better home for sulfur | **The apothecary does not exist** |
| Sulfur cubes are a crafting ingredient | **False.** They are a mob with no reagent drop |
| Sulfur is alchemy-flavoured | **False in 26.2.** It is a masonry block family and a cave biome |
| Upstream has an alchemist | **Yes**, and so does this port |

### 2.6 In play

He builds the alchemist hut he already has, in whichever of the 21 styles his colony is, and a
colonist brews potions and grows nether wart. If the stonemason tag is extended, he can also teach the
stonemason to cut sulfur bricks and stairs, and build in the new stone. Nothing about that requires the
alchemist.

---

## 3. Undead camels

### 3.1 His design call: "mounted raiders inside the existing desert raid, not a new event type"

**Holds up.** A new event type would duplicate a great deal: `HordeRaidEvent` is 586 lines of wave,
respawn, campfire, boss-bar, win-condition and NBT bookkeeping
(`core/colony/events/raid/HordeRaidEvent.java`), and the desert raid on top of it is only 184
(`core/colony/events/raid/egyptianevent/EgyptianRaidEvent.java`). Registration is one line
(`apiimp/initializer/ModColonyEventTypeInitializer.java:34`) and selection is one biome branch
(`core/colony/events/raid/RaidManager.java:466-471`, on `BiomeTags.HAS_DESERT_PYRAMID`). He is right
that inventing a new event to hold a mount would pay for all of that again.

**But his cost guess is wrong.** This is the expensive one of the three, by a wide margin, and the
reason is not the raid framework — it is the pathfinding, exactly as the brief warned.

### 3.2 The good news: vanilla 26.2 already ships the undead camel

`/opt/mc-src/net/minecraft/world/entity/EntityTypes.java:244-246`:

```
CAMEL_HUSK = register(EntityTypeIds.CAMEL_HUSK,
  EntityType.Builder.of(CamelHusk::new, MobCategory.MONSTER).sized(1.7F, 2.375F)…)
```

`/opt/mc-src/net/minecraft/world/entity/animal/camel/CamelHusk.java` is a `Camel` subclass, hostile
category, `removeWhenFarAway` true, cannot breed, has its own sounds. **No new entity, no new model, no
new texture, no new renderer is needed for the camel itself.** That removes what would otherwise be the
largest single line item.

Better: vanilla explicitly supports a **mob** riding it.

* `CamelHusk.java:34-36` — `isMobControlled()` returns `getFirstPassenger() instanceof Mob`.
* `/opt/mc-src/net/minecraft/world/entity/animal/camel/CamelAi.java:105` — the camel's own wander
  behaviour stands down when `isMobControlled()`.
* `/opt/mc-src/net/minecraft/world/entity/Mob.java:219-222` — `getControllingPassenger()` returns a
  passenger `Mob` when `canControlVehicle()`.
* `/opt/mc-src/net/minecraft/world/entity/Mob.java:348-354` — `updateControlFlags()` hands MOVE/JUMP/LOOK
  to the rider.

And the mechanism that makes it work:

```java
// /opt/mc-src/net/minecraft/world/entity/Mob.java:214-216
public PathNavigation getNavigation() {
    return this.getControlledVehicle() instanceof Mob riding ? riding.getNavigation() : this.navigation;
}
```

A mob riding a mob it controls transparently *borrows the mount's navigation*. Its AI says "walk here"
and the mount walks. That is vanilla's entire mounted-mob mechanism and it needs no mixin.

### 3.3 The trap he did not see, and it is the whole story

**MineColonies raiders override `getNavigation()` and destroy that delegation.**

```java
// api/entity/mobs/AbstractEntityMinecoloniesRaider.java:170-198
@NotNull
@Override
public AbstractAdvancedPathNavigate getNavigation()
{
    if (this.newNavigator == null) { … }
    return newNavigator;
}
```

Two separate problems, one of them structural:

1. **It never consults the vehicle.** A mounted raider keeps issuing paths to its own navigator, which
   is attached to a passenger that cannot move. It will compute a path, fail to follow it, and be handed
   to the stuck handler configured at `:184-194` — `withTakeDamageOnStuck(0.4f)`, `withBuildLeafBridges()`,
   `withPlaceLadders()`, and `withBlockBreaks()` when `raidersbreakblocks` is on. **A mounted raider left
   naive will sit on its camel taking damage and chewing holes in the terrain.** That is not a theory
   about performance; it is what that configuration does to an entity that stops making progress.

2. **The return type makes the vanilla fix inexpressible.** The override is covariant:
   `AbstractAdvancedPathNavigate`, not `PathNavigation`. A vanilla `CamelHusk` has a vanilla
   `GroundPathNavigation`, which is *not* an `AbstractAdvancedPathNavigate`. So you cannot simply copy
   vanilla's one-liner into the raider — it will not compile.

   And it is not one override. The same covariant `getNavigation()` is repeated in **nine** entity base
   classes:

   ```
   api/entity/citizen/AbstractEntityCitizen.java:378
   api/entity/mobs/AbstractEntityMinecoloniesRaider.java:172
   api/entity/mobs/AbstractEntityMinecoloniesMonster.java:172
   api/entity/mobs/vikings/AbstractEntityNorsemen.java:68
   api/entity/mobs/vikings/AbstractEntityNorsemenRaider.java:67
   api/entity/mobs/pirates/AbstractEntityPirate.java:62
   api/entity/mobs/pirates/AbstractEntityPirateRaider.java:79
   api/entity/mobs/drownedpirate/AbstractDrownedEntityPirate.java:70
   api/entity/mobs/drownedpirate/AbstractDrownedEntityPirateRaider.java:74
   ```

   So the three options are: (a) widen the signature to `PathNavigation` — nine classes plus every
   caller that relies on the covariance, of which there are many (`EntityNavigationUtils.java:59, 158,
   193, 221, 245, 279, 291` casts unconditionally, as do a dozen guard and worker AIs). **Rule this
   out.** (b) Give the camel a `MinecoloniesAdvancedPathNavigate` by subclassing `CamelHusk`, which
   reintroduces the new-entity cost the vanilla husk had just saved but keeps the blast radius at one
   file. (c) Drive the mount explicitly from the rider — which is what this codebase already does for
   the cavalry, and which also needs the subclass. **(b) and (c) converge on the same subclass, and
   that is the route to price.**

**This is the closest thing to a mixin-shaped problem in the three features.** It is not one — every
route above is reachable with plain overrides and this port's access widener — but it is the reason the
estimate is what it is.

### 3.4 What the codebase already knows about mounted mobs

More than the brief assumed, and it points straight at option (c).

* `core/entity/other/cavalry/CavalryHorseEntity.java` — 1070 lines. A `Horse` subclass with a
  `MinecoloniesAdvancedPathNavigate` of its own (`:440-453`), a passenger attachment offset (`:411-419`),
  and a `tick()` (`:480-543`) in which **the mount does the pathfinding and the rider is slaved to it** —
  the rider's yaw is stepped toward the horse's (`:505-509`), the rider's look control is aimed at the
  next path node (`:527-533`), and a path that needs a ladder forces a dismount (`:537-542`).
* `core/entity/pathfinding/navigation/MinecoloniesAdvancedPathNavigate.java:477-496` —
  `getOptionsForPathJob()` imports the vehicle's pathing options when
  `ourEntity.getVehicle() instanceof Mob riddenMob && riddenMob.getNavigation() instanceof AbstractAdvancedPathNavigate`.
  **That guard is the second half of the same trap:** a vanilla `CamelHusk` fails the `instanceof`, so a
  raider riding one would silently path with *its own* options — its own width, its own jump and door
  rules — and route the camel through gaps a camel cannot fit.

And it cannot fit. `CavalryHorseEntity.java:83-88`:

> *"the width is deliberately slim to allow 1-wide pathing for cavalry units"* — `SLIM_W = 0.70F`.

The mod had to **narrow a horse from 1.4 to 0.7** to make mounted pathing work here. A camel husk is
**1.7 wide and 2.375 tall** (`/opt/mc-src/net/minecraft/world/entity/EntityTypes.java:244-246`) —
two and a half times the width the mod found it needed, and tall enough to fail a 2-high doorway. Either
the camel gets narrowed the same way (a subclass, and the new-entity cost comes back), or mounted
raiders simply cannot follow a raid path into a built-up colony.

### 3.5 The raid bookkeeping — verified against the air-raid precedent

`PirateAirRaidEvent` documents, at `core/colony/events/raid/pirateEvent/PirateAirRaidEvent.java:50-89`,
exactly what fought back when a raid arrived unusually. Every one of its three items has a camel twin,
plus three more that are camel-specific.

**A horde is three fixed roles, not a list.** `core/colony/events/raid/barbarianEvent/Horde.java:23-38`
— `numberOfRaiders`, `numberOfArchers`, `numberOfBosses`, and that is all, serialized under three NBT
keys (`:14-17`). `HordeRaidEvent.spawnHorde` (`:234-239`) spawns exactly three types from three
abstract getters, and `EgyptianRaidEvent` supplies them at `:161-177`. **A mounted raider is not
expressible as a fourth role** without adding a field to `Horde`, an NBT key, a getter to
`HordeRaidEvent`, and a branch in `registerEntity`/`onEntityDeath` in **all seven** raid-event classes
that implement it — the same three-way `instanceof` chain is copy-pasted into each:

```
core/colony/events/raid/egyptianevent/EgyptianRaidEvent.java:81
core/colony/events/raid/amazonevent/AmazonRaidEvent.java:55
core/colony/events/raid/barbarianEvent/BarbarianRaidEvent.java:47
core/colony/events/raid/norsemenevent/NorsemenRaidEvent.java:54
core/colony/events/raid/pirateEvent/PirateGroundRaidEvent.java:66
core/colony/events/raid/pirateEvent/PirateAirRaidEvent.java:486
core/colony/events/raid/AbstractShipRaidEvent.java:394
```

The cheaper shape, and the one to recommend: **do not add a role. Mount some of the existing ones.**
Spawn the horde exactly as today and, in an override of `spawnHorde` in `EgyptianRaidEvent`, put a
camel under a fraction of the mummies. The horde counters never learn about camels, so nothing in the
586 lines of bookkeeping has to change — which is the same trick the air raid used in reverse.

**But the camels are then untracked, and that is a leak.** Verified:

* `registerEntity` is only ever called from `AbstractEntityMinecoloniesRaider:411`, so a camel is never
  registered with the event.
* `HordeRaidEvent.onFinish` (`:298-314`) removes `getEntities()` — and `getEntities()` (`:159-167`) is
  exactly the three raider maps. **The camels survive the raid and stay in the world for ever.**
* `HordeRaidEvent.onUpdate:469-476` discards any raider in a non-entity-ticking chunk and queues a
  respawn. The rider vanishes; the camel does not. **Orphan camels accumulate over a raid.**
* The respawn itself (`:440-451`) is `RaiderMobUtils.spawn(entry.getA(), 1, …)` — entity type only.
  **Respawned raiders come back on foot.** A long raid degrades from mounted to unmounted, which is
  exactly the "first pirate killed would be replaced by one walking in from the border" failure the air
  raid called out at `PirateAirRaidEvent.java:66-71`.

All three are fixable — track the camels in a fourth map, tear them down in `onFinish`, remount on
respawn — but each is real work and none of it is visible from outside the code. Budget them.

One more, from vanilla: `CamelHusk.removeWhenFarAway()` returns **true** (`CamelHusk.java:29-31`), so a
camel spawned at the colony border can despawn on its own before the raid reaches you, dropping its
rider mid-approach. Raiders set `setPersistenceRequired()` in their constructor
(`AbstractEntityMinecoloniesRaider.java:164`); the camel would need the same call.

### 3.6 Size

* **New files:** 2–4. A `RaidCamelEntity extends CamelHusk` is effectively unavoidable — it is where the
  narrowed hitbox, the `MinecoloniesAdvancedPathNavigate`, the persistence flag and the
  rider-slaving `tick()` live. Expect it to be a heavily reduced `CavalryHorseEntity`, ~250–350 lines.
  Plus an `EgyptianMountedRaid` helper or an override block in `EgyptianRaidEvent`. **No new renderer
  is needed:** `/opt/mc-src/net/minecraft/client/renderer/entity/CamelHuskRenderer.java:14` is declared
  `MobRenderer<Camel, CamelRenderState, CamelModel>` — parameterised on `Camel`, not on `CamelHusk` — so
  a `RaidCamelEntity extends CamelHusk` binds to it directly through
  `EntityRendererRegistry` in `core/event/ClientRegistryHandler.java:48`, which is where every other mob
  renderer in this port is registered. Verified at the type level; not rendered, because there is no
  client here.
* **Touched files:** `EgyptianRaidEvent.java`, `HordeRaidEvent.java` (the finish/cull/respawn hooks),
  `api/entity/ModEntities.java`, `apiimp/initializer/EntityInitializer.java`,
  `core/event/ClientRegistryHandler.java`, `RaidManager.java` (optional, if mounted raids get their own
  chance), `DefaultEntityLootProvider`, `manual_en_us.json`. ~8.
* **Rough lines:** 600–900. The uncertainty is entirely in §3.3–3.4: if the narrowed-hitbox route works
  first time it is at the low end; if mounted raiders turn out to need the same
  `MinecoloniesAdvancedPathNavigate` treatment the cavalry needed — and `CavalryHorseEntity` is 1070
  lines of evidence that they might — it is above the high end.
* **New lang keys:** 2–4 (entity name, possibly a raid-message variant).
* **Datagen:** entity loot table, lang. Spawn egg only if wanted.
* **New models/textures:** ideally none. Vanilla ships the camel husk model, texture and renderer
  (`/opt/mc-src/net/minecraft/client/renderer/entity/CamelHuskRenderer.java`). Mummy-on-camel needs no
  new art — the mummy already has one.
* **Old saves:** the new entity type and the fourth NBT field are additive; existing colonies keep
  loading. A raid *in progress* across the upgrade would deserialize with no camel field and default to
  none, which is correct behaviour. *(Inference from `HordeRaidEvent.deserializeNBT:542-557`, which uses
  `getIntOr`/`getListOrEmpty` defaults throughout — not tested.)*
* **Client/server split:** the renderer must stay out of anything server-loaded. `ClientRegistryHandler`
  is the existing seam and is already used this way.
* **Mixins:** none needed. Every route in §3.3 is a plain override or a subclass. Confirmed there are
  still zero mixins in the tree and only `resources/minecolonies.accesswidener`.

### 3.7 Traps

| Trap | Verdict |
|---|---|
| "otherwise you duplicate all the wave, barrier and reward logic" | **True.** 586 lines in `HordeRaidEvent` + one-line registration + one-line biome branch |
| A mounted raider is expressible in the raid framework | **Only by not telling the framework.** `Horde` has three fixed int roles; adding a fourth touches six subclasses |
| The mod knows nothing about mobs riding things | **False.** `CavalryHorseEntity` (1070 lines) and `MinecoloniesAdvancedPathNavigate:477-496` |
| Undead camel needs a new entity | **False for the mob itself** — vanilla ships `CamelHusk`. **True for a usable one** — hitbox and navigation force a subclass |
| Vanilla's mob-rides-mount mechanism just works | **False for raiders.** `AbstractEntityMinecoloniesRaider.getNavigation():172` overrides it, covariantly — and so do eight sibling base classes |
| The raider needs to be made able to control a mount | **False, and a pleasant surprise.** `Entity.canControlVehicle()` (`/opt/mc-src/…/Entity.java:2668`) defaults to true unless tagged `NON_CONTROLLING_RIDER`, and the mod overrides neither `canControlVehicle` nor `getControlledVehicle` anywhere |
| Camels get cleaned up with the raid | **False.** `onFinish:298-314` only removes registered raiders |
| Respawned raiders come back mounted | **False.** `onUpdate:440-451` respawns by entity type only |
| Camels persist near the colony | **False.** `CamelHusk.removeWhenFarAway()` is true |
| Needs a mixin | **No** |
| Upstream has anything close | **No.** No camel, no mounted raider anywhere in `/workspace/ldtteam/minecolonies` |

### 3.8 In play

Desert raid arrives as it does now, except a third of the mummies are on camels: faster across open
sand, visibly taller, and they reach the wall ahead of the foot troops. Against a colony with walls the
camels are a liability to the attacker — they cannot fit through the gaps the mummies use — which is
either a nice emergent property or a bug depending on how it reads. Against an open colony they are a
real difficulty spike. **The honest summary is that this is a good idea whose whole cost is in the two
hundred lines nobody would think to write** — the hitbox, the navigation delegation, and the three
bookkeeping leaks. It looks like a two-hour job and it is not.

---

## 4. Recommendation

**Do first: the alchemist — as documentation, not code.** It is already there. Tell him where it is,
tell him the apothecary does not exist, and separately offer the ten-line stonemason tag change so
26.2's sulfur blocks become buildable through the colony. Free, and it removes a whole item from the
wishlist.

**Do second: the spearman.** The design call is right, the registry work he budgeted for is already
paid, the tower list is open, and the charged attack turns out to be a vanilla mechanic that fires for
mobs — so the work is deciding how much of it to keep rather than building it. ~250 lines for a visibly
new unit with a real tactical identity is the best ratio of the three. Do the cheap version first: hire
a spearman, give him a spear, call `startUsingItem`, and watch whether the vanilla charge alone is
already the unit he wanted. If it is, most of the estimate evaporates.

**Do third, and only deliberately: the undead camels.** The design call is right and the idea is the
most interesting of the three in play, but the price is three to four times his guess and the reason
is invisible from outside the code. If he wants it, the order of work is: subclass `CamelHusk` with the
narrowed hitbox and the advanced navigator first, prove a single mounted mummy can walk from the border
to a wall, *and only then* touch the raid. If that first step is hard, stop — everything after it is
wasted.

**Not worth doing at all: sulfur recipes for the alchemist.** There is no sulfur reagent in 26.2 to
build them from, and inventing one is a different feature wearing this one's name.

---

## 5. Method and confidence

**Verified by reading code in this tree or `/opt/mc-src`:** every `path:line` citation above. The
guard-type registry being open; the spear equipment type, item, model, texture and recipe existing; the
tower holding four types; `Tool` being generic over equipment types; the huscarl's full footprint; the
alchemist's full existence including 21 styles of blueprint; the absence of any apothecary; sulfur being
a block family and a mob rather than an item; `CamelHusk` existing in vanilla with mob-control support;
`Mob.getNavigation()`'s delegation and `AbstractEntityMinecoloniesRaider`'s covariant override of it;
`Horde`'s three fixed roles; `onFinish`/`onUpdate`'s cleanup covering only registered raiders;
`CavalryHorseEntity`'s deliberately narrowed hitbox; and the vanilla spear's charge mechanic, traced
from `Item.Properties.spear` through `LivingEntity.updatingUsingItem` and `ItemStack.onUseTick` into
`KineticWeapon.damageEntities`, including its explicit non-player branch and its use of the attribute
damage rather than the mod's.

**Corrected during this study.** An earlier draft of §1.5 claimed the vanilla spear charge could not
reach a MineColonies guard at all, on the strength of `MeleeCombatAI.doAttack` bypassing the item. That
was wrong: the charge is not driven from the attack path but from the use-item tick, which is generic
to `LivingEntity`. The corrected finding is both cheaper for the feature and trappier in its details,
and it is recorded here because the same mistake — reasoning about one code path and concluding about
another — is the failure mode this port's own notes keep warning about.

**Verified in the upstream oracle** (`/workspace/ldtteam/minecolonies`, at `2d453335`): no colonist
spearman — "spearman" appears there only as the amazon raider, exactly as here; no camel of any kind
(a case-insensitive grep for `camel` across `src/main/java` returns nothing); no mounted raider. The
alchemist is present and matches this port's copy, and so is the cavalry, so neither is a local
invention that a future merge could disturb.

**Inferred, not proven:** the old-save claims (registry entries are name-keyed and the NBT readers at
`HordeRaidEvent.deserializeNBT:542-557` use defaulting getters throughout, but no old save was loaded);
the specific line counts, which are extrapolations from the huscarl and cavalry precedents rather than
from an implementation; and the claim in §3.3 that a naive mounted raider will actually be *seen* as
stuck — the stuck handler's configuration is verified, its behaviour against a passenger is reasoned,
not observed.

**Not checked at all:** anything visual. There is no display in this container, `runClient` does not
start, and no build was run for this study.
