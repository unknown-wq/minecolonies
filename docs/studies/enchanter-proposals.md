# Making the enchanter worth having, with levels 4 and 5 in mind

Design proposals. Date: 2026-08-28. Tree: `26.3/` on branch `26.3`, targeting Minecraft
**26.3-snapshot-10**. **No game code was written or changed**; the only file this document touches is
itself.

Companion to `docs/studies/enchanter.md`, which audited the profession and supplied the numbers this
document argues from. Read that first. Where a number here comes from that audit rather than from a
reading of my own, it is attributed as *(audit §N)*.

## The question this answers

The audit's verdict was "build the tower to level 3 and stop". Levels 4 and 5 buy two things — a
faster enchanting cycle and a better book table — and both are worthless in play. This document
proposes ten changes that would make them worth the resources, ranked by value per line of code.

---

## Evidence standard

Same as the audit:

* **[VERIFIED]** — I opened the file and the cited `file:line` says what I claim, or the arithmetic
  shown is arithmetic on lines I read.
* **[UNVERIFIED]** — inference I did not confirm. Each one names what would confirm it.

**Nothing was run.** No build, no server, no datagen. Every claim is a source reading of this tree or
of `/opt/mc-src-26.3-snapshot-10`.

Paths: `mc/` is `26.3/src/main/java/com/minecolonies/`; `gen/` is
`26.3/src/main/generated/data/minecolonies/`; `v/` is `/opt/mc-src-26.3-snapshot-10/net/minecraft/`.
The enchanter AI file is abbreviated `AI` (`mc/core/entity/ai/workers/service/EntityAIWorkEnchanter.java`)
and its datagen `PROV` (`mc/core/generation/defaults/workers/DefaultEnchanterCraftingProvider.java`).

**Line numbers are against the branch tip this document is committed on, not against the working
tree.** Every anchor was read on 2026-08-28; at the moment of reading `git status` was clean for all
of them **[VERIFIED]**. The checkout is shared and two other agents were editing it while this was
written, so by the time it was committed three cited files had drifted in the working tree —
`mc/api/util/ItemStackUtils.java`, `mc/api/configuration/ServerConfiguration.java` and
`mc/core/entity/ai/workers/guard/AbstractEntityAIFight.java`. The anchors given for those three are
the committed ones, re-derived from `git show HEAD:…` **[VERIFIED]**; expect them to move again.

**Constraints honoured throughout.** No proposal below needs a mixin. **No proposal below needs an
access widener**: every vanilla member any of them calls is already `public` — `EnchantmentHelper`
(`v/world/item/enchantment/EnchantmentHelper.java:57, 71, 75, 79, 86, 494, 512, 527, 540, 587`),
`Enchantment` (`v/world/item/enchantment/Enchantment.java:124-192`), `ItemEnchantments` and
`ItemEnchantments.Mutable` (`v/world/item/enchantment/ItemEnchantments.java:54-157`),
`EnchantWithLevelsFunction` (`v/world/level/storage/loot/functions/EnchantWithLevelsFunction.java:89-124`),
`UniformContainerBase.Builder#setQuality` (`v/world/level/storage/loot/entries/UniformContainerBase.java:62`)
**[VERIFIED]**. The repository's widener is 127 lines and mentions nothing in
`net.minecraft.world.item.enchantment` **[VERIFIED]** (`26.3/src/main/resources/minecolonies.accesswidener`),
and `minecolonies.mixins.json` lists exactly one unrelated client mixin **[VERIFIED]**.
No proposal introduces an upstream MineColonies asset; the two that touch the GUI (P9) or the chat
log (P4, P6) reuse layouts and icons the repository already *references* and add only new keys to
this repository's own `26.3/src/main/resources/assets/minecolonies/lang/en_us.json` (419 lines, all
port-added) **[VERIFIED]**.

Sizes: **S** under ~150 changed lines, **M** ~150–400, **L** 400+.

---

## 1. Why levels 4 and 5 are pointless, stated as three constraints

Any proposal has to move at least one of these or it cannot help.

**C1 — The input is raid-gated, so cycle speed never binds.** The only input is
`minecolonies:ancienttome`, which drops from raider loot tables and nothing else
(`mc/core/generation/defaults/DefaultEntityLootProvider.java:73-185`; no recipe produces one)
**[VERIFIED]**, and raids come on average every 14 nights
(`mc/api/configuration/ServerConfiguration.java:389-390`, `averagenumberofnightsbetweenraids = 14`,
`minimumnumberofnightsbetweenraids = 10`) **[VERIFIED]**. The audit put supply at ≈0.1–0.3 tomes per
in-game day against a level-5 capacity of five books per day *(audit §7.2, itself [UNVERIFIED] on
raid composition)*. **A level-5 enchanter runs at roughly 6% of its capacity.** The cycle length
`MAX_ENCHANTMENT_TICKS / building.getBuildingLevel()` (`AI:80, AI:236`) **[VERIFIED]** is therefore
the one thing hut level scales that the colony can never use.

**C2 — The output has no consumer.** One research eats one enchanted book, once
(`mc/core/generation/defaults/DefaultResearchProvider.java:1332`) **[VERIFIED]**; the recruitment
entry is paid from the player's own inventory *(audit §6)*. No worker recipe, no building
requirement and no equipment path takes one. Books are unstackable against each other, so each takes
a rack slot forever.

**C3 — Nothing about the worker's own progression scales with the hut.** The Mana gate rises to 50 at
hut 5 (`AI:85, AI:143`) **[VERIFIED]**, but Mana buys nothing: luck reaches a loot pool only through
an entry `quality` or a pool `bonusRolls` (`v/world/level/storage/loot/LootPool.java:85, 101, 114`)
**[VERIFIED]**, and no entry in any of the five generated tables carries either *(audit §4.3)*. And
`enchant()` awards no experience at all (`AI:263-274`) **[VERIFIED]**, so the worker's level freezes
once the gate is passed.

Two corollaries. Anything that only re-tunes the book table addresses none of the three. And the
audit's three one-line defects (F1 self-drain, F2 zero mana cost, F3 dead afternoons) are
**preconditions**, not improvements: until the enchanter works a full day and pays for its books,
no balance number below can be observed in play.

---

## 2. What vanilla now offers that this code predates

The table content is a hand-written list of 278 `enchantedBook(key, level).setWeight(n)` calls
(`PROV:62-370`, 279 occurrences of `enchantedBook(` counted including the method declaration)
**[VERIFIED]**. It names 27 of the 43 enchantments snapshot-10 declares *(audit §5.4)*; snapshot-10's
list is `protection … density breach wind_burst lunge mending vanishing_curse`, 43 `key("…")` calls
**[VERIFIED]** (`v/world/item/enchantment/Enchantments.java`). Since that list was written, vanilla
has grown machinery that makes the hand-written form obsolete:

| Mechanism | Where | What it would give the enchanter |
|---|---|---|
| `minecraft:enchant_with_levels` loot function, with `levels` (a `NumberProvider`) and an optional `options` enchantment `HolderSet` | `v/world/level/storage/loot/functions/EnchantWithLevelsFunction.java:29-91` **[VERIFIED]** | A book rolled exactly as a vanilla table rolls one, at a level the loot table chooses. Applied to a plain `minecraft:book` it returns an `ENCHANTED_BOOK`, because `enchantItem` swaps the item (`v/…/EnchantmentHelper.java:527-538`) **[VERIFIED]**. |
| 30 enchantment tags, including `IN_ENCHANTING_TABLE`, `TREASURE`, `NON_TREASURE`, `ON_RANDOM_LOOT`, `TRADEABLE` | `v/tags/EnchantmentTags.java:8-36` **[VERIFIED]** | Data-driven pools. A tag-referenced pool picks up `density`, `breach`, `wind_burst` and `lunge` with no code change, and keeps picking up whatever vanilla adds next. |
| `EnchantmentHelper.getEnchantmentCost(random, slot, bookcases, stack)` — the real table formula, bookshelves clamped at 15 | `v/…/EnchantmentHelper.java:494-510` **[VERIFIED]** | "Bookshelves = f(hut level)" as a first-class idea, instead of five hand-tuned weight tables. |
| `Registries.ENCHANTMENT_PROVIDER` and `EnchantmentsByCost(HolderSet, IntProvider)` | `v/core/registries/Registries.java:273`, `v/world/item/enchantment/providers/EnchantmentsByCost.java:18-32`, examples at `v/…/providers/VanillaEnchantmentProviders.java:23-31` **[VERIFIED]** | A datapack-defined "enchant this with cost N drawn from this set". An alternative home for the same logic if it should be overridable by datapack rather than by loot table. |
| `ItemEnchantments.Mutable#upgrade(holder, level)` and `EnchantmentHelper.updateEnchantments` | `v/world/item/enchantment/ItemEnchantments.java:124-160`, `v/…/EnchantmentHelper.java:57-68` **[VERIFIED]** | Anvil semantics — merge a book's enchantments into an existing item, keeping the higher level — for free. This is the whole of P4's mechanic. |
| `Enchantment#canEnchant`, `Enchantment.areCompatible`, `EnchantmentHelper.isEnchantmentCompatible` | `v/…/Enchantment.java:169, 188`, `v/…/EnchantmentHelper.java:577` **[VERIFIED]** | The validity checks P4 needs, already written. |
| Loot entry `quality`: `weight + quality × luck`, floored at 0 | `v/world/level/storage/loot/entries/UniformContainerBase.java:62, 87-88` **[VERIFIED]** | The missing link between the worker's Mana and what comes out of the table. The luck is already being passed: `withLuck(getEffectiveSkillLevel(getPrimarySkillLevel()))` (`mc/core/entity/ai/workers/crafting/AbstractEntityAICrafting.java:797`) **[VERIFIED]**. |

The datagen provider already holds everything needed to use the first three: it keeps a
`HolderLookup.Provider` field (`PROV:49, 59`) **[VERIFIED]**, and the sibling
`mc/core/generation/defaults/DefaultBlockLootTableProvider.java:98` shows the idiom
(`lookupProvider.lookupOrThrow(Registries.ENCHANTMENT)`) **[VERIFIED]**. `UniformGenerator.between`
already returns the `Holder<NumberProvider>` the function's builder wants
(`v/world/level/storage/loot/providers/number/UniformGenerator.java:22`) **[VERIFIED]**, and the
repository already calls it in loot datagen
(`mc/core/generation/defaults/DefaultEntityLootProvider.java:163-167`) **[VERIFIED]**.

---

## 3. The proposals

### P1 — Roll books the way a vanilla table does, scaled by hut level

**What the player gets.** A level-5 enchanter that produces books like a fifteen-bookshelf table:
often two or three enchantments on one book, at or near maximum level. A level-1 enchanter that
produces books like a bare table. The gap between them becomes the reason to build levels 4 and 5,
and it is a gap players already understand without reading a wiki. Every enchantment vanilla adds
from now on appears automatically.

**What changes in code.** `PROV:62-370` — the 309 lines of hand-written entries — collapse to five
one-entry pools of the form

```java
LootPool.lootPool().add(LootItem.lootTableItem(Items.BOOK)
    .apply(EnchantWithLevelsFunction.enchantWithLevels(
        provider.lookupOrThrow(Registries.ENCHANTMENT), UniformGenerator.between(lo, hi))
      .withOptions(provider.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE))))
```

with `(lo, hi)` climbing per hut level — 1–8, 5–14, 10–20, 17–26, 25–30 is the obvious first cut.
A vanilla table draws only from `IN_ENCHANTING_TABLE` (`v/world/inventory/EnchantmentMenu.java:189`)
**[VERIFIED]**, and that tag excludes the treasure enchantments — Mending, Frost Walker, Soul Speed,
Swift Sneak and the two curses *(audit §4.3; the tag's contents are datapack JSON and are not present
in the snapshot source tree, so on this tree that exclusion is* **[UNVERIFIED]***, and confirming it
means reading the vanilla datapack)*. Those therefore have to stay as explicit entries in a second,
small pool at hut 4–5; the existing `enchantedBook(key, level)` helper (`PROV:376-383`)
stays exactly as it is to build them. Nothing outside `PROV` changes — the recipes at `PROV:400-407`
still point at `minecolonies:recipes/enchanter{1..5}` and the AI still calls
`getFirstFulfillableRecipe(ItemStackUtils::isEmpty, …)` (`AI:229`) **[VERIFIED]**.

**Fix size.** **S**, and negative: roughly 60 lines written against ~309 deleted.

**Assets / datagen.** No assets. `runDatagen` must be re-run; the five files under
`gen/loot_table/recipes/` are rewritten wholesale.

**Balance change.** Yes, and the largest single one here. It also raises the mana debit once F2 is
fixed, because the debit is the maximum stored level (`AI:266-271`) **[VERIFIED]** and a level-30
roll produces high levels — which is the intended economy, but it is a second-order effect worth
watching.

**Risk.** `enchant_with_levels` declares no referenced context params unless
`includeAdditionalCostComponent` is set (`v/…/EnchantWithLevelsFunction.java:64-66`) **[VERIFIED]**,
so it is safe under this repository's `LootContextParamSets.ALL_PARAMS` registration (`PROV:448-458`)
**[VERIFIED]**. **[UNVERIFIED]** that the mod's loot-table datagen writes the function's JSON without
further coaxing — confirming means running `runDatagen` and reading `gen/loot_table/recipes/enchanter5.json`.
Also note this supersedes finding F11 of the audit permanently rather than patching it once.

---

### P2 — A second, tome-free input path, unlocked at hut level 4

**What the player gets.** An enchanter that has something to do on the 13 days out of 14 when no raid
has happened. Lapis lazuli plus a book becomes an enchanted book at hut 4 and above; ancient tomes
stay the premium input and keep the treasure pool to themselves. This is the only proposal that
attacks C1 directly, and C1 is the constraint that makes every hut-level speed increase meaningless.

**What changes in code.** Two halves.

*Datagen:* new recipes beside the existing five (`PROV:398-407`), with `.minBuildingLevel(4)`, inputs
`[lapis ×3, book ×1]` and their own loot tables registered in `registerTables` (`PROV:448-458`). The
recipe shape is already proven — an empty primary output plus `.lootTable(…)` is exactly what
`tome{1..5}` does, and the generated JSON confirms it (`gen/crafterrecipes/enchanter/tome5.json`)
**[VERIFIED]**.

*AI:* `decide()` currently hard-codes the ancient tome as the only thing worth stocking —
`IS_ANCIENT_TOME` at `AI:60`, the fetch-or-request branch at `AI:192-203`, the disabled-recipe scan at
`AI:179-188` **[VERIFIED]**. That block has to become a loop over the crafting module's empty-output
recipes, gathering or requesting the inputs of the first one that is enabled and not already
satisfied. `getFirstFulfillableRecipe` already iterates in registration order and skips disabled
recipes (`mc/core/colony/buildings/modules/AbstractCraftingBuildingModule.java:826-833`)
**[VERIFIED]**, so recipe *selection* needs no change at all — only the stocking logic that runs
before it.

**Fix size.** **M**, ~120–180 lines counting the datagen.

**Assets / datagen.** No assets. `runDatagen` re-run; new recipe and loot-table files.

**Balance change.** Yes, large. It converts the enchanter from a raid-loot processor into a standing
production building, and it is the change that makes the cycle-speed scaling at `AI:236` mean
anything. Tuning lever: keep the lapis path's `levels` range well below the tome path's, so tomes
remain the way to a good book.

---

### P3 — Make the worker's Mana decide what comes out, via loot `quality`

**What the player gets.** A reason to level the enchanter, and a visible payoff for the drain: a
skilled enchanter stops producing junk, an unskilled one mostly produces it. Today Mana gates
whether the worker may work and nothing else *(audit §4.3)*.

**What changes in code.** `.setQuality(n)` on the entries in `PROV:62-370` — negative on the low
tiers, positive on the treasure entries. Weight resolves as `max(floor(weight + quality × luck), 0)`
(`v/…/UniformContainerBase.java:87-88`) **[VERIFIED]** and luck is
`getEffectiveSkillLevel(getPrimarySkillLevel())` (`AbstractEntityAICrafting.java:797`) **[VERIFIED]**,
which is `((raw+1) × 2) − ((raw+1)/10)²` (`mc/core/entity/ai/workers/AbstractEntityAIBasic.java:808-811`)
**[VERIFIED]** — **75 at Mana 50, 45 at Mana 25** [VERIFIED by arithmetic on those lines].

**That magnitude is the whole difficulty.** Current level-5 entry weights are 15 / 5 / 1
*(audit §4.2)*, so a `quality` of −1 erases a weight-15 entry at Mana 7 and a `quality` of +1 makes a
weight-1 treasure entry the dominant outcome at Mana 20. Using `quality` at all means first
multiplying every weight in the table by ~20 so the integer arithmetic has room. If P1 ships, this
gets much cheaper: the table is then two pools, and `quality` goes on two entries rather than 278.

**Fix size.** **S** — about 10 lines after P1, or a mechanical edit of 278 lines before it.

**Assets / datagen.** No assets. `runDatagen` re-run.

**Balance change.** Yes. Note it is inert until F2 (the mana debit that reads the wrong data
component, `AI:287`) is fixed, because without a debit the enchanter sits at exactly its gate value
forever *(audit §5.1)*.

---

### P4 — Turn the drain into a gear-enchanting round: the level 4–5 service

**What the player gets.** The single biggest change in how the profession reads. The enchanter walks
to a hut carrying an enchanted book from its own stock, plays the beam it already plays, and the
worker there comes away with the book's enchantment on its tool or armour. Guards get Protection and
Sharpness; miners get Efficiency and Unbreaking; the warehouse pile becomes fuel. Hut level decides
how strong an enchantment may be applied and how many huts get serviced per day, which is a level-4
and level-5 payoff with nothing book-shaped about it.

**What changes in code.** The machinery is 90% written; it is currently pointed at the wrong citizen
and throws its result away.

* *Target selection* — `AI:324` iterates `getModuleForJob().getAssignedEntities()`, which is the
  enchanter's own work module, so the enchanter always drains itself **[VERIFIED]** *(audit F1, with
  a fix already drafted there)*. It has to enumerate the worker modules of `buildingWorker`, the
  building fetched at `AI:308` and currently used only for the walk and a null check **[VERIFIED]**.
* *The payoff block* — `AI:394-416`. Replace the `Items.BOOK` slot lookup at `AI:394` with a search
  for an `ENCHANTED_BOOK`; read its enchantments with
  `EnchantmentHelper.getEnchantmentsForCrafting` (`v/…/EnchantmentHelper.java:79-81`), which is the
  accessor two methods further down this same class already uses correctly (`AI:452`) **[VERIFIED]**;
  apply them with `EnchantmentHelper.updateEnchantments` + `ItemEnchantments.Mutable#upgrade`
  (`v/…/ItemEnchantments.java:139`) **[VERIFIED]**; gate each one on `Enchantment#canEnchant`
  (`v/…/Enchantment.java:188`) and `Enchantment.areCompatible` (`:169`) **[VERIFIED]**.
* *The ceiling.* `ItemStackUtils.getMaxEnchantmentLevel` returns `max(maxLevel − 1, 0)` and
  `verifyEquipmentLevel` adds it to the equipment level before comparing against the hut's ceiling
  (`mc/api/util/ItemStackUtils.java:285-315`) **[VERIFIED]**, and that ceiling is
  `IBuilding#getMaxEquipmentLevel` — `buildingLevel − 1`, or the maximum at max level
  (`mc/api/colony/buildings/IBuilding.java:479-490`) **[VERIFIED]**. So an over-enthusiastic
  enchantment makes a worker refuse its own tool. The applied level must be capped against the
  *target* building's ceiling, not the enchanter's. This is the one non-obvious correctness
  requirement in the whole proposal and it is why P4 is not S.
* *Level gating.* The natural knob is the enchanter's own hut level: maximum applied level
  `buildingLevel − 2` (so 1 at hut 3, 2 at hut 4, 3 at hut 5), and stations serviced per day already
  resets at dawn (`mc/core/colony/buildings/modules/EnchanterStationsModule.java:149-154`)
  **[VERIFIED]**.
* *Range.* `MIN_DISTANCE_TO_DRAIN = 10` (`AI:70`) with 60 retries (`AI:355-367`) **[VERIFIED]** was
  written for a target that was always in range because it was the enchanter itself. Against a real
  worker — a lumberjack or miner out on a job — it will usually fail. Either relax it or have the
  enchanter enchant gear left in the target building's racks instead of on the citizen.

**Fix size.** **M**, ~150–250 lines including the target-selection fix, plus one or two lang keys for
"no gear to enchant here" / "no books in stock".

**Assets / datagen.** No new assets. New keys go in this repository's own `en_us.json`.

**Balance change.** Yes, and the largest in effect: it is a straight, permanent power increase for
every armed and tooled citizen in the colony. It should probably be gated behind hut level 4 on its
own merits, which conveniently is the level this whole document is about.

**Risk.** This is the proposal most likely to produce surprises in play — worker tool churn, guards
swapping gear, equipment ceilings — and the least confirmable by reading. Ship it after P1/P6, not
with them.

---

### P5 — Give the pile a sink: enchanted book → bottles o' enchanting at hut 4

**What the player gets.** The ninety unstackable books in the warehouse become something. An
enchanted book of any kind plus glass bottles yields bottles o' enchanting, which vanilla otherwise
makes obtainable only by trading. It is a level-4 recipe, so it is a reason to build level 4 even for
a player who does not care about the book table.

**What changes in code.** One recipe in `registerRecipes` (`PROV:393-445`), following the scroll
recipes verbatim:

```java
recipe(ENCHANTER, MODULE_CUSTOM, "xp_bottles")
    .inputs(List.of(new ItemStorage(new ItemStack(Items.ENCHANTED_BOOK), true, true),
                    new ItemStorage(new ItemStack(Items.GLASS_BOTTLE, 3))))
    .result(new ItemStack(Items.EXPERIENCE_BOTTLE, 3))
    .minBuildingLevel(4)
    .showTooltip(true)
    .build(consumer);
```

The `(stack, ignoreDamage, ignoreNBT)` constructor is `mc/api/crafting/ItemStorage.java:92`
**[VERIFIED]** and the ignore-NBT flag is what makes *any* enchanted book match: the builder emits
`"matchType": "ignore"` for it (`mc/core/generation/CustomRecipeProvider.java:287-290`) **[VERIFIED]**,
exactly as the tome input already does (`gen/crafterrecipes/enchanter/tome5.json`) **[VERIFIED]**.
Unlike the tome recipe this one has a real primary output, so it is requestable through the normal
crafter path — the loot-table recipes are not, because request matching is on the primary output and
theirs is empty (`AbstractCraftingBuildingModule.java:826-851`) **[VERIFIED]**.

**Fix size.** **S** — under 20 lines.

**Assets / datagen.** No assets. `runDatagen` re-run; one new recipe file.

**Balance change.** Yes: experience bottles become renewable inside a colony.

**Open question.** The enchanter already carries the min-stock module
(`mc/apiimp/initializer/ModBuildingsInitializer.java:428-438`) **[VERIFIED]**, and min-stock files
requests through `building.createRequest(stack, true)`
(`mc/core/colony/buildings/modules/MinimumStockModule.java:139-141`) **[VERIFIED]**. Whether a
building's own min-stock request can be resolved by that same building's crafting module is
**[UNVERIFIED]** — confirming means reading the crafting resolver or watching a colony. If it cannot,
the recipe is still driven the way the four scrolls already are: by a player order from the crafting
tab.

---

### P6 — The four lines that have to land before any of this is observable

**What the player gets.** An enchanter that works afternoons, pays for what it makes, and levels up.
None of these is an improvement on its own; together they are the difference between a balance change
you can see and one you cannot.

**What changes in code.** Three of the audit's findings, restated only to fix their line anchors:

* `AI:287` reads `DataComponents.ENCHANTMENTS` off an enchanted book, whose enchantments live in
  `STORED_ENCHANTMENTS` (`v/…/EnchantmentHelper.java:86-88`) **[VERIFIED]**, so the mana debit at
  `AI:271` is always `-0`. Swap the loop source to `EnchantmentHelper.getEnchantmentsForCrafting(stack)`.
  One line.
* `AI:132` tests `craftState != START_WORKING` against a `getNextCraftingState()` that never returns
  `START_WORKING` **[VERIFIED]**, so from game time 6000 to bedtime the worker stands still. `!= IDLE`.
  One token.
* `enchant()` awards no experience (`AI:263-274`) **[VERIFIED]**. One
  `worker.getCitizenExperienceHandler().addExperience(…)` beside `AI:273`, scaled like the crafter's.

Add, optionally, the two smallest of the audit's other findings: returning `IDLE` rather than
`ENCHANT` when the tome recipe is toggled off (`AI:179-206`, the permanent two-state loop), and
hoisting the free-space check out of `enchant()` so a full building does not silently burn a whole
cycle (`AI:236-274`).

**Fix size.** **S** — four lines for the three, perhaps 40 with the two optional ones and their
interaction key.

**Assets / datagen.** None for the three. The free-space interaction needs one new lang key.

**Balance change.** Yes, and it should be shipped as one: existing saves have enchanters sitting
above their mana gate that will drop back into the drain loop the first time they make a good book.

---

### P7 — Deterministic top-tier books at hut 5

**What the player gets.** A stated reason to reach level 5 that does not depend on a probability
table: at hut 5 the enchanter can make a specific book to order. The obvious candidate is
`minecolonies:raider_damage_enchant`, which is the mod's own enchantment, exists at 1 weight in the
level-5 pool only *(audit §4.2)*, and is the one book that is unambiguously worth having before a
raid. Mending is the other candidate.

**What changes in code.** One or two recipes beside the scrolls (`PROV:409-445`), with
`.minBuildingLevel(5)`, an input of one ancient tome plus something expensive, and
`.result(bookStack)` where `bookStack` carries the `stored_enchantments` component. Component-bearing
results serialize correctly: `stackAsJson` runs the stack through `ItemStack.OPTIONAL_CODEC`
(`mc/core/generation/CustomRecipeProvider.java:255-265`) **[VERIFIED]**, which is the same codec the
generated loot tables' `set_components` entries already round-trip
(`gen/loot_table/recipes/enchanter5.json`) **[VERIFIED]**. Because the primary output is real, the
colony can request these — the loot-table recipes cannot be requested at all.

**Fix size.** **S** — about 25 lines.

**Assets / datagen.** No assets. `runDatagen` re-run.

**Balance change.** Yes: it makes a scarce random drop into a purchasable one, at a hut level and an
input cost the designer picks.

---

### P8 — Let guards actually prefer the enchanted gear

**What the player gets.** The point of P4 and of the books generally: gear that is better gets worn.
Today it does not reliably get picked up.

**What changes in code.** A guard picks the best piece in its building by
`item.getItemNeeded().getMiningLevel(stack)` and takes it only if it is strictly greater than what it
holds (`mc/core/entity/ai/workers/guard/AbstractEntityAIFight.java:236-242`) **[VERIFIED]**. For
armour that level is `Compatibility.getItemLevel(itemStack)`
(`mc/api/equipment/ModEquipmentTypes.java:131-153`) **[VERIFIED]**, which does not consider
enchantments — so an enchanted diamond chestplate and a plain one compare equal and the guard keeps
whichever it found first. Adding `ItemStackUtils.getMaxEnchantmentLevel(stack)`
(`mc/api/util/ItemStackUtils.java:300-315`) **[VERIFIED]** as a tie-break in that comparison is a few
lines.

**Fix size.** **S**.

**Assets / datagen.** None.

**Balance change.** Yes, mild on its own; it is a multiplier on P4.

**Risk, and a warning.** `verifyEquipmentLevel` *already* adds the enchantment level when deciding
whether a worker may hold an item at all (`ItemStackUtils.java:285-291`) **[VERIFIED]**, so the
acceptance test and the preference test would then use the same term for different purposes; the
change must not let a guard prefer an item its hut will subsequently refuse. Also: this is the only
proposal whose code lives in `mc/core/entity/ai/workers/guard/`, which another agent is editing in
this checkout right now. **Re-read the anchors before acting on this one.**

---

### P9 — A settings tab: decide what the enchanter does with its day

**What the player gets.** Control, and an exit from the trap where turning book production off in the
crafting tab leaves the worker ping-ponging between `START_WORKING` and `ENCHANT` forever *(audit
F6)*. With P2 and P4 in the tree there are three things the worker could be doing and no way to
choose between them.

**What changes in code.** A `SettingsModule` producer beside `ENCHANTER_STATIONS`
(`mc/core/colony/buildings/modules/BuildingModules.java:640-646`) **[VERIFIED]**, added to the
building entry (`ModBuildingsInitializer.java:428-438`, which today lists only `ENCHANTER_WORK`,
`ENCHANTER_CRAFT`, `ENCHANTER_STATIONS`, `MIN_STOCK`, `STATS_MODULE`) **[VERIFIED]**, and read in
`decide()` at the two branch points (`AI:143` and `AI:179-206`). The setting types already exist —
`BoolSetting`, `StringSetting`, `IntSetting` in
`mc/core/colony/buildings/modules/settings/` **[VERIFIED]**.

**Fix size.** **S–M**, ~80–150 lines.

**Assets / datagen.** **No new assets, and this is worth stating explicitly given the constraint.**
`SettingsModuleWindow` uses the shared `minecolonies:gui/layouthuts/layoutsettings.xml`
(`mc/core/client/gui/modules/building/SettingsModuleWindow.java:37`) **[VERIFIED]** and
`SettingsModuleView` the shared `textures/gui/modules/settings.png`
(`mc/core/colony/buildings/moduleviews/SettingsModuleView.java:105-108`) **[VERIFIED]** — both are
runtime-fetched upstream assets that many other buildings already reference, so nothing new enters
the repository or the jar. Only new lang keys, which go in this repository's own `en_us.json`.

**Balance change.** No — it adds control, not power. It does change what a player can choose to turn
off, which is a design decision even when the numbers do not move.

---

### P10 — Convert hut level into yield rather than speed

**What the player gets.** Levels 4 and 5 turn each scarce ancient tome into more output instead of
into idle time the colony cannot fill. This is the cheapest possible answer to C1 if P2 is judged too
large.

**What changes in code.** Two edits. Flatten or greatly reduce the level term in
`MAX_ENCHANTMENT_TICKS / building.getBuildingLevel()` (`AI:236`, constant at `AI:80`) **[VERIFIED]**;
and give the higher tables more rolls — `.setRolls(ConstantValue.exactly(2))` at hut 4 and 3 at hut 5
in `registerTables` (`PROV:448-458`). The pool-rolls idiom is already used in this repository
(`mc/core/generation/defaults/DefaultEntityLootProvider.java:121`, `:200`) **[VERIFIED]**.

**Fix size.** **S** — under 20 lines, mostly datagen.

**Assets / datagen.** No assets. `runDatagen` re-run.

**Balance change.** Yes. Note the interaction with F2 and P3: more books per tome means a larger mana
debit per cycle, which means more drains, which is coherent but multiplies rather than adds.

**Caveat.** Do not ship this together with P1 without re-tuning: multiple rolls of an
`enchant_with_levels` pool at hut 5 is a lot of books, each of them good.

---

## 4. Ranked by value per line

Value here means "how much closer this gets a level-4 or level-5 enchanter to being worth its
citizen", divided by the code it costs.

| # | Proposal | Size | Balance? | Datagen? | Why it ranks here |
|---|---|---|---|---|---|
| 1 | **P1** — vanilla-rolled books scaled by hut level | S (net −250 lines) | yes | yes | Deletes more than it writes, produces the entire level 1→5 quality curve, and permanently ends the "frozen at the 1.21.1 enchantment list" problem. Nothing else on this list has a negative line count. |
| 2 | **P6** — the four one-line correctness fixes | S | yes | no | Four lines buy a worker that works all day, pays for its books and levels. Every other proposal's effect is unobservable without them. |
| 3 | **P10** — yield instead of speed | S | yes | yes | ~20 lines aimed squarely at the constraint that makes hut level 4 and 5 pointless. |
| 4 | **P5** — book → bottles o' enchanting at hut 4 | S | yes | yes | Under 20 lines, and the only proposal that empties the warehouse. A level-4 unlock a player will notice immediately. |
| 5 | **P7** — deterministic hut-5 books | S | yes | yes | ~25 lines for a stated, non-probabilistic reason to reach level 5. |
| 6 | **P3** — `quality` on the loot entries | S | yes | yes | Cheap *after* P1 and inert before P6; connects Mana, the drain and the table for the first time. |
| 7 | **P2** — tome-free input path at hut 4 | M | yes | yes | The largest real unlock on the list and the only one that fixes the supply constraint outright, but it costs an AI rewrite of the stocking branch. |
| 8 | **P4** — the drain becomes a gear-enchanting round | M | yes | no | Highest player-facing value of anything here; also the most code, the most balance risk, and the most that cannot be settled by reading. |
| 9 | **P9** — settings tab | S–M | no | no | Necessary once there is more than one thing to do, near-worthless before that. |
| 10 | **P8** — guards prefer enchanted gear | S | yes | no | Small and correct, but worth almost nothing until P4 exists, and it sits in files another agent is editing. |

Two pairings worth respecting: **P3 after P1** (two entries instead of 278), and **P10 not with P1
unless re-tuned** (multiple rolls of a table-quality pool is a lot of good books).

---

## 5. What I would ship first

**P1, in a pull request that also carries P6's four lines.**

The reasoning is not that P1 is the most valuable — P2 and P4 are — but that it is the only change on
the list whose cost is negative, and it is the one that makes levels 4 and 5 *feel* different the
first time a player builds them. Concretely, that first change:

* deletes ~309 lines of hand-maintained data and replaces them with ~60 that never need maintaining
  again;
* gives hut level 5 books that a player would actually apply, without touching the AI, the request
  system, the building or the GUI;
* touches exactly two files (`PROV` and `AI`) and no client code;
* needs no assets, no access widener and no mixin;
* is entirely reversible — `runDatagen` regenerates the old tables from the old source.

P6 rides along because three of its four lines are in the same file as nothing else, and because
without them nobody can tell whether P1 worked: an enchanter that stops at noon and never pays for a
book produces too few observations to judge a table by.

After that, in order: **P5** (a sink, so the new books have somewhere to go), then **P2** (a supply,
so the hut's speed scaling starts to matter), then **P4** (the service that makes the profession
worth a citizen rather than worth a warehouse slot).

---

## 6. What I could not verify

* **Anything requiring a running server or a build.** No `runDatagen`, no `build`, no colony. In
  particular: that `EnchantWithLevelsFunction` serializes cleanly through this repository's loot-table
  datagen (P1); that a building's own min-stock request can be resolved by its own crafting module
  (P5); and every claim about how any of these *feel* in play.
* **The tome supply figure** of ≈0.1–0.3 per in-game day, which is the audit's and is itself
  [UNVERIFIED] on raid composition. Every "levels 4 and 5 are starved" argument here inherits that
  uncertainty. If the true figure is an order of magnitude higher, P2 and P10 lose most of their
  value and P1 and P4 keep all of theirs.
* **Whether `MIN_DISTANCE_TO_DRAIN = 10` is workable against a real target** (P4). Miners and
  lumberjacks are away from their huts most of the day; how often the enchanter would find one home
  is a question for a running colony, not for a source reading.
* **The client half of P9.** `SettingsModuleWindow` and `SettingsModuleView` were read for their
  asset references only; nothing about rendering was exercised.
* **Line anchors in `mc/core/entity/ai/workers/guard/`** (P8 only). They were correct when read on
  2026-08-28 and are in files under active edit by another agent in this checkout.
