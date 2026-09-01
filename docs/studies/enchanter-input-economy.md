# The enchanter's input economy: seven designs for work between raids

Design study. Date: 2026-08-28. Tree: `26.3/` on branch `26.3`, targeting Minecraft
**26.3-snapshot-10**. **No game code was written or changed**; the only file this document touches is
itself.

Third in a set. `docs/studies/enchanter.md` audited the profession; `docs/studies/enchanter-proposals.md`
proposed ten changes to make hut levels 4 and 5 worth building. **Four of those ten shipped while this
document was being written** (§0), and none of them touched the input. What is left is the question
proposal P2 tried to answer and was rejected for:

> The enchanter's only input is the ancient tome, which drops from raiders only, at roughly one raid
> per fourteen nights. What else can it eat, such that the enchanter has work between raids, the input
> cannot be farmed and forgotten, and the ancient tome still means something?

**"Lapis and a book at hut 4" is out of scope by instruction, and so is anything shaped like it.** The
reasoning is in §1.2 and the rejected directions are in §6. Every design below is measured against the
same three tests and is honest about which of them it fails.

---

## Evidence standard

Same as the two companion documents:

* **[VERIFIED]** — I opened the file and the cited `file:line` says what I claim, or the arithmetic
  shown is arithmetic on lines I read.
* **[UNVERIFIED]** — inference I did not confirm. Each one names what would confirm it.

**Nothing was run.** No build, no `runDatagen`, no server, no colony. Every claim is a source reading
of this tree or of `/opt/mc-src-26.3-snapshot-10`. No design below has been played, and no throughput
number in it has been observed.

Paths: `mc/` is `26.3/src/main/java/com/minecolonies/`; `gen/` is
`26.3/src/main/generated/data/minecolonies/`; `v/` is `/opt/mc-src-26.3-snapshot-10/net/minecraft/`.
The enchanter AI file is abbreviated `AI` (`mc/core/entity/ai/workers/service/EntityAIWorkEnchanter.java`)
and its datagen `PROV` (`mc/core/generation/defaults/workers/DefaultEnchanterCraftingProvider.java`).

**Line anchors, and a warning about them.** This checkout is shared and the enchanter was being actively
rewritten *during* this study. **Every anchor below is against `HEAD` = `019e039e`**, and `PROV` anchors
in particular are written `PROV(HEAD)` because that file was modified and uncommitted when read
**[VERIFIED]** by `git status`. By the time this document was finished, `EntityAIWorkEnchanter.java` and
`BuildingEnchanter.java` were also modified and uncommitted, with work in flight that this document
accounts for in §0.2 **[VERIFIED]**. **Every anchor here should be re-read before it is acted on.**

**Constraints honoured throughout, stated per design and summarised here.**

* **No design below requires a mixin.** `minecolonies.mixins.json` lists exactly one unrelated client
  mixin (`PackRepositoryMixin`) **[VERIFIED]**, and nothing here would add to it.
* **No design below requires an access widener.** `minecolonies.accesswidener` is 127 lines and
  mentions nothing in `net.minecraft.world.item.enchantment` **[VERIFIED]**; every vanilla member any
  design calls is already `public` — `EnchantmentHelper.getEnchantmentsForCrafting`
  (`v/world/item/enchantment/EnchantmentHelper.java:79`), `getComponentType` (`:86`),
  `updateEnchantments` (`:57`), `setEnchantments` (`:75`), `getEnchantmentCost` (`:494`), `enchantItem`
  (`:512`, `:527`), `selectEnchantment` (`:540`), `ItemEnchantments.Mutable#set`/`#upgrade`
  (`v/world/item/enchantment/ItemEnchantments.java:131, 139`),
  `EnchantWithLevelsFunction.enchantWithLevels` and `Builder#withOptions`
  (`v/world/level/storage/loot/functions/EnchantWithLevelsFunction.java:89, 106-112`),
  `UniformContainerBase.Builder#setQuality`
  (`v/world/level/storage/loot/entries/UniformContainerBase.java:62`) **[VERIFIED]**.
* **No upstream MineColonies asset enters the repo or the jar.** This repository's
  `26.3/src/main/resources/assets/minecolonies/` contains `lang/`, one item definition
  (`items/scepterclaim.json`), one item model (`models/item/fieldstick.json`), three textures and
  `sounds.json` **[VERIFIED]** by directory listing. Everything else the enchanter renders is a
  *reference* to a runtime-fetched upstream asset. **None of the seven designs below needs a new
  texture, model or icon** — that was a design constraint, not an accident, and §6 records the one
  idea that was dropped partly because it would have needed original art. New lang keys go in this
  repository's own `en_us.json` (419 lines, all port-added) **[VERIFIED]**.

Sizes: **S** under ~150 changed lines, **M** ~150-400, **L** 400+.

---

## 0. What changed under this study, and why it sharpens the question

### 0.1 Four proposals shipped

Between the first and last reading for this document, four of the companion document's proposals were
shipped to this branch **[VERIFIED]** by `git log`:

| Commit | What it did | Proposal |
|---|---|---|
| `3c6c4012` | "Make the enchanter work all day, pay for its books and level up" | P6 (audit F2, F3, F6, F8) |
| `996f3aa3` | "Roll the enchanter's books the way a table does, and let Mana decide" | P1 + P3 |
| `c1b4cf62` | "Stop selling hut level as enchanting speed" | P10 |

The state of the profession is therefore no longer what the audit described. Specifically:

* **The mana debit is real.** `AI:319` now reads `EnchantmentHelper.getEnchantmentsForCrafting(stack)`,
  and `AI:296` debits Mana by the book's highest stored level **[VERIFIED]**.
* **The worker works all day and gains experience.** `AI:153` tests `craftState != IDLE`; `AI:300`
  awards `XP_PER_ENCHANT + enchantmentLevel` per book **[VERIFIED]**.
* **Books are now rolled the way a vanilla table rolls them, and the worker's Mana decides how well.**
  `PROV(HEAD):216-222` builds every entry from `EnchantWithLevelsFunction.enchantWithLevels(...)`
  `.withOptions(IN_ENCHANTING_TABLE)`; `PROV(HEAD):145-152` puts two such entries in the main pool, a
  "plain" band at `quality = -8` and a "fine" band at `quality = +8`
  (`PROV(HEAD):91-94`); the treasure enchantments live in a separate bonus pool gated at hut 3, 4 and
  5 (`PROV(HEAD):167-200`) **[VERIFIED]**. An entry's effective weight is
  `max(floor(weight + quality * luck), 0)` (`v/world/level/storage/loot/entries/UniformContainerBase.java:87-88`)
  **[VERIFIED]** and the luck a crafter passes is
  `getEffectiveSkillLevel(getPrimarySkillLevel())` — Mana —
  (`mc/core/entity/ai/workers/crafting/AbstractEntityAICrafting.java:797`) **[VERIFIED]**.
* **Hut level buys yield, not speed.** `AI:261` is now
  `MAX_ENCHANTMENT_TICKS - (buildingLevel - 1) * ENCHANTMENT_TICKS_PER_LEVEL` (`AI:79, 90`), and
  `BONUS_ROLLS` climbs `{0, 1, 1, 2, 3}` across the five hut levels (`PROV(HEAD):78`) **[VERIFIED]**.

**This makes the input question sharper, not softer, and it changes what the best answer is.** The
profession now has a working economy at every point except two:

1. **Mana is now a real, spendable currency** — books cost it, it decides their quality, and the only
   thing that replenishes it is the drain.
2. **The drain still takes from nobody.** `AI:356` still iterates
   `getModuleForJob().getAssignedEntities()`, which is the enchanter's *own* work module
   (`mc/core/entity/ai/workers/AbstractEntityAIBasic.java` `getModuleForJob` -> `AbstractJob#getWorkModule`),
   capped at one worker (`mc/core/colony/buildings/modules/BuildingModules.java:640-642`, `(b) -> 1`)
   **[VERIFIED]**. The building it walked to, `buildingWorker` (`AI:340`), is still used only for the
   walk and a null check **[VERIFIED]**.

So: **the meter is now wired at both ends except the one that reads from the colony.** That is the
single most important fact in this document and it decides the ranking in §5.

### 0.2 And more is in flight, uncommitted

At the moment of writing, the working tree carries uncommitted changes to
`EntityAIWorkEnchanter.java` (+219/-14) and `BuildingEnchanter.java` (+16) **[VERIFIED]** by
`git diff --stat`. Reading the diff, they implement the companion document's **P4** — the drain becomes a
gear-enchanting round — and, on the way, **fix `AI:356`**: `getModuleForJob().getAssignedEntities()`
becomes a loop over `buildingWorker.getModulesByType(WorkerBuildingModule.class)`, and
`MIN_DISTANCE_TO_DRAIN` goes from 10 to 20 **[VERIFIED]** by reading the diff hunks. The enchanter also
starts carrying a finished enchanted book to the hut it visits and applying it to the worker's gear from
hut level 4.

**What that does to this document.** It removes one line from D1's work and it removes the "can the drain
find anybody?" risk from §7 — both good. **It does not touch D1's premise.** The drain will target a real
citizen and will *spend* one of the enchanter's own books on that citizen's gear; it still takes
*nothing* from the citizen. There is no call to `removeXpFromSkill` anywhere in the diff **[VERIFIED]**
by grep over it. The colony still pays nothing for a book, and the second-input question is untouched.

If that work lands, D1 shrinks: it becomes "add the debit at `AI:426-450`, make the mana gate a
reservoir, and price a second recipe family in mana", and it acquires a pleasant new shape — the drained
citizen loses skill and gains an enchantment on their tool in the same visit, which is a trade the player
can watch happen. **That makes D1 a better first pick than it was, not a worse one.** Its anchors,
though, will all have moved; re-derive them.

---

## 1. What has to be true

### 1.1 The three tests

Every design is scored against these, and each design's own section says plainly where it fails.

**T1 — Non-automatable.** At least one term in the price cannot be produced by a machine the player
builds once and walks away from. A cobblestone generator, a sugar-cane farm, an autocrafter and a
villager trading hall are all "build once, harvest forever"; a design whose whole price is items of
that kind has not solved anything.

**T2 — Capped, not merely expensive.** Throughput must have a ceiling that does not rise with the
number of machines pointed at the hut. An expensive input scales linearly with the player's industrial
base; a cap does not.

**T3 — Tome-preserving.** The ancient tome must remain the *best* input, not merely one of several.
Since `996f3aa3` this has a precise meaning: the tome path is the only thing that reaches the **bonus
pool**, which is where Frost Walker, Soul Speed, Swift Sneak, Mending and this mod's own
`raider_damage_enchant` live (`PROV(HEAD):181-192`) **[VERIFIED]** — none of which
`#minecraft:enchantment/in_enchanting_table` can produce. **Any second input path must therefore roll
the main pool only, at a lower cost band.** That is a one-line constraint in datagen and it makes T3
cheap to satisfy for every design below.

### 1.2 Two structural facts that decide most of this

**The cheapest way to make an input unfarmable is to make it not an item.** Hoppers, droppers,
autocrafters, water streams and item filters move items. None of them can move a citizen's skill
level, a counter in a building's NBT, a tool's durability, a worker's absence from the colony, or a
day boundary. Every design below puts at least one non-item term in the price, and the designs that
put *only* non-item terms in the price are the ones that pass T2 outright.

**In MineColonies every crafted item already costs a citizen-day, but that cap is soft.** "Make
another profession pay for it" is a real opportunity cost — a citizen assigned to producing the
enchanter's input is a citizen not doing anything else. But the player can answer it by building
another hut and hiring another citizen, so it bounds throughput only by the colony's population, which
grows. **Soft limiters belong in the quality curve; hard limiters belong in the building or in the
day.** The designs that rely only on "another worker has to make it" are ranked lower for exactly this
reason, and they say so.

One consequence applies to four of the seven: **this tree puts no per-colony limit on the number of
enchanter huts.** A grep of `RegisteredStructureManager` and `BuildingEntry` finds no building-count
cap, no duplicate check and no "only one per colony" flag **[VERIFIED]** by absence. So a cap that
lives in one building is per-building; two enchanters is twice the throughput. What stops that is the
cost of the second building and the second citizen — a hut, a bed, a share of the food supply and a
share of the colony's citizen cap — which is a real cost but not an infinite one. Where a design leans
on a per-building cap, its adversarial section says what that costs the player.

### 1.3 What is left to fix before any of this is observable

Only one of the audit's blocking defects survives, and one design is entirely about it:

* **`AI:356` — the enchanter drains itself.** `getModuleForJob().getAssignedEntities()` returns the
  enchanter's own module's citizens, so `citizenToGatherFrom` is always the enchanter and the walk,
  the thirty-second particle beam and the player-curated station list all decide nothing
  **[VERIFIED]**. The empty-list branch at `AI:368` already handles an unmanned hut. **This is being
  fixed in the working tree as this is written — see §0.2 — and the fix does not add a debit against the
  target, so every design below stands.**

Two smaller ones, both noted by the audit and both still present, matter to designs that touch the
drain:

* `AI:438` discards the return of `EnchantmentHelper.enchantItem`, which returns a **new** stack when
  the input is a book (`v/.../EnchantmentHelper.java:527-538`) **[VERIFIED]**, and passes
  `Optional.empty()`, which means the entire enchantment registry rather than
  `#minecraft:enchantment/in_enchanting_table` (`v/.../EnchantmentHelper.java:519-524`) **[VERIFIED]**.
* `AI:69` sets `MIN_DISTANCE_TO_DRAIN = 10` with 60 retries (`AI:390`), tuned for a target that was
  always in range because it *was* the enchanter **[VERIFIED]**.

---

## 2. What this snapshot's vanilla offers that the enchanter's code predates

The companion document surveyed `enchant_with_levels`, the enchantment tags and loot `quality` as
*available*; commit `996f3aa3` has since put all three into the tables. This section covers what
remains unused and what is relevant to an *input* economy specifically.

| Mechanism | Where | What an input economy can do with it |
|---|---|---|
| **`EnchantWithLevelsFunction`** takes its cost from a `NumberProvider`, so the cost can be data rather than a constant | `v/world/level/storage/loot/functions/EnchantWithLevelsFunction.java:34, 41, 77-78, 89-112` **[VERIFIED]**; already used at `PROV(HEAD):216-222` with `UniformGenerator.between(low, high)` **[VERIFIED]** | **A price can choose a table.** A design that charges a variable amount can spend that amount as the `levels` argument. `PROV(HEAD)`'s `tableRolledBook(low, high)` helper already exists and takes exactly those two numbers, so a second path is one more call to a written method. |
| **`EnchantmentHelper.getEnchantmentCost(random, slot, bookcases, stack)`** — the real table formula, bookshelves clamped at 15 | `v/world/item/enchantment/EnchantmentHelper.java:494` **[VERIFIED]** | "Bookshelves in the tower" becomes a first-class input, with vanilla's own clamp as the cap. |
| **`EnchantmentProvider`** and its implementations `EnchantmentsByCost`, `EnchantmentsByCostWithDifficulty`, `SingleEnchantment`, registered under `Registries.ENCHANTMENT_PROVIDER` | `v/world/item/enchantment/providers/` **[VERIFIED]** by directory listing; `EnchantmentsByCost.java:18-32` and `SingleEnchantment.java:15-32` read in full **[VERIFIED]** | A datapack-defined "enchant this with cost N drawn from this set", usable outside a loot table. If a design's price should be datapack-tunable rather than Java-tunable, this is where it goes. Nothing in this repository uses it yet **[VERIFIED]** by grep. |
| **Trial chamber and archaeology items** — `TRIAL_KEY` / `OMINOUS_TRIAL_KEY` (`v/world/item/Items.java:2800-2801`), `OMINOUS_BOTTLE` (`:2803-2808`), `HEAVY_CORE` (`:251`), `BREEZE_ROD` (`:2134`), 20+ pottery sherds (`:2711` onward), `BRUSH` (`:2653`), `SUSPICIOUS_SAND`/`SUSPICIOUS_GRAVEL` (`:222-223`), `ECHO_SHARD` (`:2652`) | all **[VERIFIED]** by reading `Items.java` | A whole class of items that postdate the enchanter's design and that **cannot be farmed** — they come out of generated structures and nothing else. Design 6 is built on this and needs no new assets at all. Nothing in this repository references any of them **[VERIFIED]** by grep. |
| **`ItemEnchantments.Mutable#set` / `#upgrade` and `EnchantmentHelper.updateEnchantments`** | `v/world/item/enchantment/ItemEnchantments.java:124-160`, `v/.../EnchantmentHelper.java:57-68` **[VERIFIED]** | Anvil semantics — merge a book's enchantments into an item, keeping the higher level — for free. Design 2 is built on it. |
| **Mending already works on citizen gear** | `mc/core/util/citizenutils/CitizenItemUtils.java:351-356` **[VERIFIED]** — a citizen's damaged, Mending-enchanted tool consumes the citizen's own XP at 2 XP per durability point | The one place the colony already *spends* an enchantment, and the proof that enchanted worker gear is a live system rather than decoration. |

Three in-tree mechanisms matter as much as the vanilla ones, because four designs below are assembled
out of them rather than written from scratch:

* **A recipe input that is also a secondary output becomes a *tool*: damaged one point per craft,
  returned to the building, consumed only when it breaks.** `RecipeStorage#processInputsAndTools`
  matches inputs against `secondaryOutputs` and moves the match into `tools`
  (`mc/api/crafting/RecipeStorage.java:388-393`) **[VERIFIED]**; `fullfillRecipeAndCopy` then damages
  it instead of consuming it (`:721-746`) **[VERIFIED]**. The Sifter's meshes are the shipped example
  (`mc/core/generation/defaults/workers/DefaultSifterCraftingProvider.java`, the inputs-plus-secondary
  pattern inside `registerRecipes`) **[VERIFIED]**. **This is a rate limiter available from datagen
  alone.**
* **A building can carry a second crafting module with its own recipe family, its own loot tables and
  its own AI state.** `BuildingSmeltery.OreBreakingModule` extends
  `AbstractCraftingBuildingModule.Custom` (`mc/core/colony/buildings/workerbuildings/BuildingSmeltery.java:155-186`),
  is registered as `SMELTER_OREBREAK` (`mc/core/colony/buildings/modules/BuildingModules.java:372-373`),
  added to the building entry (`mc/apiimp/initializer/ModBuildingsInitializer.java:341`), and driven by
  a dedicated `BREAK_ORES` state (`mc/core/entity/ai/workers/crafting/EntityAIWorkSmelter.java:68,
  78-119`) **[VERIFIED]**. **A second input path does not have to fight the tome path inside
  `decide()`** — which matters, because `AI:254` still assumes "the only empty-output recipes are the
  tome ones" **[VERIFIED]**.
* **An item can carry a data component that only the mod's own AI writes, and recipe matching can
  require it.** `AdventureData` is the shipped example (`mc/api/items/component/AdventureData.java:16-40`,
  registered at `mc/api/items/component/ModDataComponents.java:30`) **[VERIFIED]**; `ItemStorage`'s
  `ignoreNBT` flag (`mc/api/crafting/ItemStorage.java:66, 92`) feeds `RecipeStorage`'s comparison at
  `:721-725` **[VERIFIED]**, so a recipe can demand the component-bearing variant and refuse the
  crafted one. **This is how an input made of common materials is made un-craftable.**

---

## 3. The designs

Seven. Each names, in order: the fantasy; what is consumed and produced; the rate limiter and why it
cannot be farmed around; the code changes with anchors; size; assets, datagen and config; whether it is
a balance change; mixin / AW / neither; and how it fails against a player who is trying to break it.

---

### D1 — The mana ledger: a book costs somebody else's talent

**Fantasy.** The enchanter does not find magic, he takes it. Every book he binds is a little of
somebody else's skill, walked over from their hut, drawn out of them in a thirty-second beam, and
spent on a page.

**Consumed.** Per book: one `minecraft:book` (trivially farmable, and deliberately so — it is the
*material*, not the *price*) plus **N points of the enchanter's Mana skill**, exactly as `AI:296`
already debits. Mana is replenished only by draining an assigned citizen at a player-selected station,
and **each drain removes skill XP from that citizen** — the debit the drain has never had.

**Produced.** One book from the main pool at a cost band one tier below the tome band for the same hut
level, using the `tableRolledBook(low, high)` helper that already exists (`PROV(HEAD):216-222`). The
bonus pool — Mending, Soul Speed, Swift Sneak, Frost Walker, `raider_damage_enchant`
(`PROV(HEAD):181-192`) — stays behind the tome, so T3 holds by one datagen line.

**The rate limiter, and where it lives.** The Mana pool, refilled at a rate set by three things the
player cannot automate:

1. **One drain per station per in-game day.** `EnchanterStationsModule#getRandomBuildingToDrainFrom`
   only returns stations whose flag is `false`
   (`mc/core/colony/buildings/modules/EnchanterStationsModule.java:106-114`), `setAsGathered` flips it
   (`:121-124`), and `onWakeUp` clears every flag at dawn (`:148-154`) **[VERIFIED]**. That machinery
   is already written, already has a GUI, and today decides nothing.
2. **The target must be an assigned citizen who is at home.** The drain aborts if the target is more
   than `MIN_DISTANCE_TO_DRAIN = 10` blocks away after 60 retries (`AI:69, 390`) **[VERIFIED]**. A
   lumberjack or a miner is away from its hut most of the day; a guard between raids, a baker or a
   smith is not. **Whom the player can afford to tax becomes a real question with a real answer.**
3. **Skill XP is destroyed when spent, and its floor is level 1.**
   `CitizenSkillHandler#removeXpFromSkill` walks the citizen down through levels and stops at level 1
   (`mc/core/entity/citizen/citizenhandlers/CitizenSkillHandler.java:226-247`, floor at `:233`)
   **[VERIFIED]**, and the XP needed per level rises with level
   (`ExperienceUtils.getXPNeededForNextLevel`, called at `:205` and `:241`) **[VERIFIED]**.

**Why it cannot be farmed around.** The currency is a citizen's skill level. There is no hopper for it.
It is created only by citizens doing their jobs, at a rate that *falls* as they get better, and it is
destroyed by the enchanter. To raise supply you must employ more citizens, which costs housing, beds,
food and a share of a citizen cap the colony grows only slowly. And the cost is visible in the place
the player looks most: the level-40 miner is now a level-38 miner.

**Why this design in particular, now.** Since `996f3aa3`, **Mana is not just permission to work, it is
the quality dial**: the main pool's two entries carry `quality = -8` and `quality = +8`
(`PROV(HEAD):91-94`) against a luck term that is the worker's effective Mana
(`AbstractEntityAICrafting.java:797`), so a well-fed enchanter rolls the fine band far more often
(`PROV(HEAD):83-97` records the intended figures: 30% at hut one, 67% at hut five, 83% at Mana 99).
**Draining therefore already improves the books — it just takes the mana from nobody.** D1 is not
"add an economy"; it is "connect the last wire in an economy that now works".

**Hut levels 4 and 5.** Today `AI:164` gates work on `Mana >= buildingLevel * 10` and nothing else, so
Mana is an on/off switch that is either satisfied or not. Make it a reservoir: drain while Mana is
below `buildingLevel * MANA_REQ_PER_LEVEL * 2` and spend downward from there. The ceiling is then 60 at
hut 3, 80 at hut 4 and 99 at hut 5 (clamped by `MAX_CITIZEN_LEVEL`, `CitizenSkillHandler.java:182`)
**[VERIFIED]**, and the number of stations serviceable in one day scales with hut level. **Levels 4 and
5 buy capacity to hold and spend talent** — which, given `quality`, translates directly into better
books, not just more of them.

**Code changes.**

| Change | Anchor |
|---|---|
| Target the *visited* building's workers, not the enchanter's own module — **probably already done by the time this is read, see §0.2** | `AI:356`; `buildingWorker` is fetched at `AI:340` and used only for the walk and a null check **[VERIFIED]** |
| Debit the drained citizen | `AI:426-450`, the payoff block, which today writes only to the enchanter (`AI:444` gives the enchanter +1 Mana). Add `removeXpFromSkill` against `citizenToGatherFrom` (`CitizenSkillHandler.java:226`) |
| Turn the gate into a reservoir | `AI:164`, constant at `AI:95` |
| Generalise the stocking branch so a second empty-output recipe family is stockable | `AI:200-229`; `IS_ANCIENT_TOME` at `AI:59`, `IS_BOOK` at `AI:64`. Note `AI:254`'s comment still assumes the tome recipes are the only empty-output ones **[VERIFIED]** |
| New recipes + tables for the book path at hut 4-5 | `PROV(HEAD):242-256` (recipe loop), `PROV(HEAD):116-131` (table assembly), `PROV(HEAD):297-307` (`registerTables`) |
| Fix the drain's throwaway enchant while in there | `AI:438` — assign the return, pass `IN_ENCHANTING_TABLE` (see §1.3) |

**Fix size.** **M**, roughly 200 lines including datagen.

**Assets / datagen / config.** No new assets, no new items. `runDatagen` re-run for two recipes and the
tables. One or two new lang keys for a "nobody home to draw from" interaction, following
`NO_WORKERS_TO_DRAIN_SET` (`mc/api/util/constant/TranslationConstants.java:338`, validator at
`mc/apiimp/initializer/InteractionValidatorInitializer.java:259`) **[VERIFIED]**. No config needed; if
one is wanted, the XP debit per drain is the knob.

**Balance change?** Yes, and a large one in two directions at once. Books become available between raids
for the first time; citizens become worse at their jobs for the first time. It also interacts with
`996f3aa3`: a real drain means the enchanter's Mana can now be pushed *above* the gate rather than
sitting on it, which moves the fine-band probability the new tables were tuned around.

**Mixin / AW / neither.** **Neither.**

**Adversarial.** *"I will build twenty huts with one citizen each and drain them all."* Each of those
citizens needs a bed, a share of the food supply and a slot under the colony's citizen cap, and each
drained citizen is measurably worse at the job you hired it for. *"I will keep a battery of citizens
who do nothing but be drained."* A citizen who does nothing gains no skill XP, and `removeXpFromSkill`
floors at level 1 — the battery drains flat and stays flat. To recharge it you must give them jobs,
which is the outcome the design wants. *"I will leave the station huts unstaffed."* Today that works,
because the enchanter drains itself; after the `AI:356` fix an unstaffed hut yields nothing and the
existing empty-list branch at `AI:368` handles it. *"I will point a hopper at it."* There is nothing
item-shaped to move: books are the cheap half.

**Where it fails.** `MIN_DISTANCE_TO_DRAIN = 10` was tuned for a target that was always in range. If in
practice no worker is ever home during the enchanter's window, the whole economy stalls. That is the one
thing here a source reading cannot settle **[UNVERIFIED]**; confirming means watching a colony for a few
in-game days. Mitigations exist — relax the range, or let the enchanter draw from gear left in the
target hut's racks — and either is a small change, but neither is free.

---

### D2 — The exchange: five books you do not want for one you do

**Fantasy.** The tower's shelves fill with enchantments nobody will ever apply — Bane of Arthropods,
Depth Strider I, a third copy of Aqua Affinity. The enchanter will take five of them and give back one
book of an enchantment the player names, one level below the best thing they handed over.

**Consumed.** K enchanted books (K = 5 at hut 4, 3 at hut 5) plus one `minecraft:book` plus Mana.
Optionally the same family accepts a damaged, enchanted *item* in place of a book, destroying the item.
**Produced.** One enchanted book carrying one enchantment drawn from the set fed in, at
`max(level of the best input) - 1`, minimum 1.

**The rate limiter, and where it lives.** **Conservation.** The exchange never creates enchantment
levels; it destroys them at a ratio the hut level sets. The pool of enchantment levels inside a colony
grows only from ancient tomes, mob and raid loot, villager trades and the player's own table — every one
of which is bounded by something outside this building. **The enchanter therefore cannot out-produce its
own input by construction, at any hut level, with any number of hoppers.** That is the strongest form of
T2 available: not a cap on a rate, but a conservation law.

**Why it cannot be farmed around.** A player who can buy the book they want has no reason to buy five
they do not want and convert. The exchange is only attractive against a pile that already exists — the
pile the audit found rotting in every warehouse (audit §6: one research consumes one book, ever; each
book is unstackable against every differently-enchanted book and holds a rack slot forever). A zombie
farm or a trading hall produces the low tiers, and low tiers convert to low tiers.

**Hut levels 4 and 5.** The exchange rate is the level reward, and it is the most legible one in this
document: five-for-one at hut 4, three-for-one at hut 5. Nothing else in this profession gives the
player a number that goes *down* when they invest.

**Code changes.**

| Change | Anchor |
|---|---|
| A recipe input matching *any* enchanted book | `ItemStorage(stack, ignoreDamage, ignoreNBT)` at `mc/api/crafting/ItemStorage.java:92`; the tome recipe already uses this exact form (`PROV(HEAD):244-246`) **[VERIFIED]** |
| Reading the inputs' enchantments | `EnchantmentHelper.getEnchantmentsForCrafting` (`v/.../EnchantmentHelper.java:79`), which `AI:319` and `AI:484` both already call correctly **[VERIFIED]** |
| Writing the output | `EnchantmentHelper.updateEnchantments` + `ItemEnchantments.Mutable#upgrade` (`v/.../EnchantmentHelper.java:57`, `v/.../ItemEnchantments.java:139`) |
| Where the work happens | a second crafting module on `BuildingEnchanter` — the module class sits at `mc/core/colony/buildings/workerbuildings/BuildingEnchanter.java:54-73`, its producer at `BuildingModules.java:643-644`, its registration at `ModBuildingsInitializer.java:428-438`; the pattern to copy is `BuildingSmeltery.java:155-186` + `BuildingModules.java:372-373` + `ModBuildingsInitializer.java:341` **[VERIFIED]** |
| Player names the target enchantment | a settings module; idiom at `BuildingModules.java:118-121`, setting types at `mc/core/colony/buildings/modules/settings/` (`BoolSetting`, `IntSetting`, `StringSetting`, `StringSettingWithDesc`) **[VERIFIED]** |

The output cannot be a static recipe result, because it depends on the inputs. It has to be built in the
AI, where `enchant()` builds the loot today (`AI:288-303`).

**Fix size.** **M**, ~200-250 lines. The "name your enchantment" half is what makes it M; a random draw
from the fed-in set is closer to S.

**Assets / datagen / config.** No new items, no new textures. One new recipe; no new loot table if the
output is computed rather than rolled. New lang keys for the settings entries — and note
`SettingsModuleWindow` uses the shared `layoutsettings.xml` and `SettingsModuleView` the shared
`settings.png`, both runtime-fetched upstream assets already referenced by many buildings, so nothing
new enters the jar. No config.

**Balance change?** Yes, but gentle: it converts dead inventory into live inventory and cannot increase
the colony's total enchantment stock.

**Mixin / AW / neither.** **Neither.**

**Adversarial.** *"I will feed the enchanter's own books back in."* Five in, one out, one level lower —
strictly lossy, and it terminates. *"I will trade for books with villagers."* Then buy the one you want;
the exchange is worse than the trade. *"I will name Mending every time."* Correct, and here is the
design's one real hole: Mending has a single level, so "one level below the best input" does not reduce
it. **The single-level treasure enchantments must be excluded from the exchange's output set and left
behind the tome path** — which T3 requires anyway, and which the bonus-pool split at `PROV(HEAD):181-192`
already expresses. *"I will automate it."* You can automate the hauling; couriers already do. Hauling
was never the constraint.

**Where it fails.** It is an exchange, not a source. On its own it creates no work between raids until a
pile exists, and the pile only exists because tomes arrived. **D2 is a multiplier on whatever else ships,
not a first move.**

---

### D3 — The tower's charge: an overnight budget the player spends

**Fantasy.** Something settles into the tower's shelves overnight — call it the day's noise going quiet.
In the morning the enchanter has a fixed budget of it and no way to get more before tomorrow, and the
only question is whether he spends it on three careless pages or one careful one.

**Consumed.** `charge`, an integer on the building, plus one `minecraft:book` per output. Nothing else.
**Produced.** One book rolled by `enchant_with_levels` at a cost equal to the charge spent, from
`#minecraft:enchantment/in_enchanting_table` — exactly what a player's own table would produce for the
same number of levels, and therefore never a treasure enchantment.

**The rate limiter, and where it lives.** The reservoir, in building NBT, refilled once per colony day.
Accrual on `onWakeUp()` (`mc/api/colony/buildings/IBuilding.java:78`,
`mc/core/colony/buildings/AbstractBuilding.java:261`, overridden by
`mc/core/colony/buildings/workerbuildings/BuildingNetherWorker.java:139-152` as the shipped precedent)
**[VERIFIED]**:

```
charge = min(cap(buildingLevel), charge + rate(buildingLevel) * shelfFactor)
```

where `shelfFactor` counts bookshelves registered inside the hut, clamped at 15 to mirror vanilla's own
clamp in `getEnchantmentCost` (`v/.../EnchantmentHelper.java:494`) **[VERIFIED]**. Counting shelves has
an in-tree precedent: `BuildingLibrary` keeps a `List<BlockPos> bookCases` (`:43`) populated by
`registerBlockPosition` (`:96-101`) and persisted to NBT (`:65-86`)
(`mc/core/colony/buildings/workerbuildings/BuildingLibrary.java`) **[VERIFIED]**.

**Why it cannot be farmed around.** Charge is not an item. It has no stack size, no rack slot and no
hopper. The reservoir does not care how much lapis, how many books or how many hoppers arrive; it
refills once per day, at a rate the building level and the room set, and it caps. The only way to raise
it is to build the tower higher and furnish it, both one-time costs the designer prices.

**Where the ancient tome goes.** It stops being an input and becomes a **charge bomb**: burning one adds
a large lump *above* the reservoir cap and unlocks the bonus pool for the next few rolls. That is a
strictly better relationship than today's — the tome is no longer what lets the enchanter work at all,
it is what lets him work *beyond his means* — and it satisfies T3 sharply rather than by tuning.

**Hut levels 4 and 5.** They set `rate` and `cap`, the only two numbers in the design. This is the
cleanest mapping of hut level to throughput available anywhere in this document, and it composes with
what `c1b4cf62` just did: hut level already stopped meaning speed, and this gives it a second, harder
meaning.

**Code changes.**

| Change | Anchor |
|---|---|
| `charge` field + NBT | `BuildingEnchanter.java:25-52` (class body); serialize/deserialize precedent `BuildingNetherWorker.java:157-180` **[VERIFIED]** |
| Daily accrual | override `onWakeUp()`; precedent `BuildingNetherWorker.java:139-152` **[VERIFIED]** |
| Shelf count | override `registerBlockPosition`; precedent `BuildingLibrary.java:96-101` **[VERIFIED]** |
| Spend-level control | a settings module with an `IntSetting`; idiom at `BuildingModules.java:118-121` **[VERIFIED]** |
| The roll | a charge-path recipe family beside the tome one at `PROV(HEAD):242-256`, its tables built from the existing `tableRolledBook(low, high)` helper (`PROV(HEAD):216-222`) **[VERIFIED]** |
| The AI branch | `AI:251-306`; note `AI:254`'s "only empty-output recipes are tome recipes" assumption has to go **[VERIFIED]** |

**Fix size.** **M**, ~250 lines with datagen and the settings module. Materially cheaper than it was a
week ago, because `tableRolledBook` and the `enchant_with_levels` plumbing now exist.

**Assets / datagen / config.** No new items, no new textures. `runDatagen` re-run: new recipes plus new
tables. New lang keys for the settings entries. A config entry for the base accrual rate is the one
place in this document where a server knob clearly earns its keep, since it is the single number that
decides the profession's whole output.

**Balance change?** Yes, and the most sweeping: it replaces the input economy rather than extending it.

**Mixin / AW / neither.** **Neither.**

**Adversarial.** *"Hoppers."* Nothing to move. *"I will fill the hut with 400 bookshelves."* Clamped at
15, following vanilla. *"I will build five enchanter towers."* This is the real attack, and §1.2 said
why it works: there is no per-colony building cap in this tree **[VERIFIED]** by absence. Five towers is
five citizens, five huts, five sets of housing and food and five station lists — expensive, not
impossible. If that is judged too weak, move part of the cap into the colony: a colony-wide daily charge
budget divided among enchanter huts. That is a larger change and a different design, and it is the
honest cost of this one's simplicity. *"I will sleep repeatedly to force more dawns."* `onWakeUp` fires
once per colony wake-up cycle; a player cannot get two accruals from one night.

---

### D4 — Marginalia: the Library writes, the enchanter binds

**Fantasy.** A student who has just understood something writes it in the margin, and the margin is
worth keeping. The enchanter collects those pages and binds them; eight margins of genuine insight are
worth about as much as one page of a dead sorcerer's book.

**Consumed.** K *marginalia* (8 at hut 4, 5 at hut 5) plus one book plus Mana. A marginalia is a
`minecraft:writable_book` **carrying a data component that only the Student AI writes**.
**Produced.** One book from the main pool at a mid cost band: useful, never treasure.

**Where marginalia come from.** `EntityAIStudy` already walks a student to a bookshelf, picks a study
item, rolls `skillIncreaseChance`, and consumes the item on a `breakChance` roll
(`mc/core/entity/ai/workers/education/EntityAIStudy.java:112-184`; the roll and consumption at
`:167-183`) **[VERIFIED]**. `StudyItem` is a datapack-driven record
(`mc/core/datalistener/model/StudyItem.java:16-19`) **[VERIFIED]**. A marginalia is emitted on a
*successful* study roll, at a low rate.

**The rate limiter, and where it lives.** **Student-days.** A marginalia costs one successful study
cycle, and study cycles are the thing that raises the colony's Intelligence. Producing them for the
enchanter trades directly against the colony's own education. Students are citizens housed in the school
and library, bounded by those buildings' worker caps and by the citizen cap.

**Why it cannot be crafted around.** `minecraft:writable_book` is craftable from a book, ink and a
feather, all farmable — **so the recipe must not accept a plain one.** It does not have to: a data
component the mod writes makes the item unforgeable, and `ItemStorage`'s `ignoreNBT` flag
(`mc/api/crafting/ItemStorage.java:66, 92`) feeds `RecipeStorage`'s comparison at
`RecipeStorage.java:721-725` **[VERIFIED]**, so an input declared with `ignoreNBT = false` refuses the
crafted variant. `AdventureData` is the shipped example of exactly this pattern — a component written
only by the netherworker AI and read back by its recipes (`mc/api/items/component/AdventureData.java:16-40`,
registered at `mc/api/items/component/ModDataComponents.java:30`) **[VERIFIED]**. **Using an existing
vanilla item plus a component means this design needs no new art at all.**

**Hut levels 4 and 5.** The binding ratio, as in D2. Also natural: the component can carry the student's
skill level the way `AdventureData` carries damage and XP (`AdventureData.java:16`), so at hut 5 a
marginalia written by a bright student rolls a higher cost band.

**Code changes.**

| Change | Anchor |
|---|---|
| Emit the marginalia | `EntityAIStudy.java:167-183`, inside the existing success branch |
| The component | a record beside `AdventureData` (`mc/api/items/component/AdventureData.java`), registered in `ModDataComponents.java:30-31`'s list |
| The recipes | `PROV(HEAD):242-256`, `.minBuildingLevel(4)`, input declared with `ignoreNBT = false` |
| The stocking branch | `AI:200-229`, generalised as in D1 |

**Fix size.** **M**, ~180 lines. It touches a second profession's AI, which is its main cost.

**Assets / datagen / config.** **No new assets** — the point of using `writable_book` plus a component.
`runDatagen` re-run for the recipes and tables. No config.

**Balance change?** Yes, with a second-order effect worth flagging: it makes study items worth
stockpiling in a colony that wants books, which re-prices the Library.

**Mixin / AW / neither.** **Neither.**

**Adversarial.** *"I will craft writable books."* Rejected by component matching. *"I will build four
libraries and four schools."* This works, and it is the design's honest weakness: the cap is soft in
exactly the sense §1.2 warned about. Hardening it means a per-colony daily emission cap, or pairing it
with D1's or D3's hard limiter. *"I will hopper the marginalia to the enchanter."* Fine — the courier
already does; hauling was never the constraint.

**Where it fails.** Soft cap. Also, marginalia are *items*, so they are transportable, storable and
stockpilable: a player who ignores the enchanter for a year comes back to a chest full of them and runs
the tower flat out for a week. Whether that is a bug or a feature is a judgement call worth making
deliberately.

---

### D5 — Field study: the enchanter leaves

**Fantasy.** There are things you cannot learn in a tower. At hut level 4 the enchanter packs a satchel
and walks out of the colony for two days; he comes back hungry, sometimes hurt, occasionally not at all,
and with a folio or two of somebody else's notes.

**Consumed.** The worker's *presence* for N in-game days; food from the hut; armour and weapon
durability; and health. **Produced.** One to three rolls of a mid cost band, plus a small chance of a
genuine ancient tome — the one place a design should be allowed to *add* tomes, because it costs the
thing tomes are supposed to cost.

**The rate limiter, and where it lives.** Two hard ones stacked:

1. **A per-period trip counter in building NBT.** `BuildingNetherWorker` is the shipped precedent in
   full: `MAX_PER_PERIOD = 1` (`:65`), `PERIOD_DAYS = 3` (`:70`), incremented by `recordTrip()` (`:238`),
   tested by `isReadyForTrip()` (`:221-235`), rolled over in `onWakeUp()` (`:139-152`), persisted at
   `:157-180` **[VERIFIED]**.
2. **The worker is gone.** `ITravellingManager#startTravellingTo(citizenData, target, ticks)` removes
   the citizen entity from the world for a duration
   (`mc/core/entity/ai/workers/production/EntityAIWorkNether.java:181-191`, with the
   `isTravelling`/`getTravellingTargetFor` checks at `:196-208`) **[VERIFIED]**. **A travelling enchanter
   produces nothing else, so the design is self-limiting in the most literal way: the throughput of the
   second path is subtracted from the throughput of the first.**

**Why it cannot be farmed around.** There is nothing to build. The counter is in NBT and reset by the
day, and the citizen's absence is enforced by the entity being removed. Risk can be *reduced* — better
armour, a higher-skill worker — but reduction costs armour durability and skill, and the whole damage
loop is written and reusable: a `DamageSource` at `EntityAIWorkNether.java:404`, incoming damage reduced
by `getSecondarySkillLevel() * SECONDARY_DAMAGE_REDUCTION` at `:412-413`, weapon damage read from the
stack's `ATTRIBUTE_MODIFIERS` component at `:434-444`, tool durability consumed at `:447`, and a death
branch at `:496-500` **[VERIFIED]**.

**Hut levels 4 and 5.** Hut 4 unlocks the trip at one per three days; hut 5 shortens the period, or
lengthens the trip and raises the cost band. The netherworker's own numbers (`:65, :70`) are the
starting point.

**Code changes.** The largest of the seven. New `AIWorkerState` entries beside `ENCHANTER_DRAIN` and
`ENCHANT` (`mc/api/entity/ai/statemachine/states/AIWorkerState.java:599, 604`) **[VERIFIED]**; a trip
counter and NBT on `BuildingEnchanter`; a loot provider modelled on
`mc/core/generation/defaults/workers/DefaultNetherWorkerLootProvider.java`, which is where the
"adventure token" pattern lives (`createAdventureToken(mob, damage_done, xp_gained)` at `:222`, block and
mob pools at `:94-220`) **[VERIFIED]**; and the travel calls above.

**Fix size.** **L.** 400+ lines even reusing everything reusable.

**Assets / datagen / config.** No new assets if the trip rolls the existing tables directly; a
"weathered folio" item would need original art and is not necessary. `runDatagen` for the trip loot
tables. A config entry for trip length is defensible.

**Balance change?** Yes, and the riskiest to tune of the seven, because the death of a skilled citizen
is a large and irreversible loss.

**Mixin / AW / neither.** **Neither.**

**Adversarial.** *"I will send him out in full diamond and never lose him."* That is the intended
counterplay and it costs a set of armour's durability per trip — which is, pleasingly, a sink for the
enchanter's own output. *"I will build five enchanters and send them all."* Same per-building-cap hole
as D3, same answer. *"I will automate the trip."* There is nothing to automate: the cap is a day counter
and the worker is not in the world.

**Where it fails.** It is a strange answer to "the enchanter has nothing to do": the fix is that he
leaves. It is also by far the most code, and the most that cannot be settled by reading — how often a
trip kills a citizen is a question for a running server.

---

### D6 — The antiquarian: the enchanter will read anything old

**Fantasy.** A sherd brushed out of a desert well, a shard from the deep dark, a key from a room full of
traps — every one of them is a note from somebody who is not here to be asked. The enchanter reads them
the way he reads a tome, badly, and gets something out.

**Consumed.** Items from a new item tag, `minecolonies:enchanter_relics`, plus one book, plus Mana.
Tiered by what was fed in:

| Tier | Items | Anchor | Cost band |
|---|---|---|---|
| Low, hut 4 | the ~20 pottery sherds | `v/world/item/Items.java:2711` onward **[VERIFIED]** | the hut-2 band, `BOOK_LEVELS[1]` (`PROV(HEAD):64-71`) |
| Mid, hut 4 | `TRIAL_KEY`, `OMINOUS_TRIAL_KEY`, `OMINOUS_BOTTLE`, `BREEZE_ROD` | `Items.java:2800-2801, 2803-2808, 2134` **[VERIFIED]** | the hut-3 band |
| High, hut 5 | `ECHO_SHARD`, `HEAVY_CORE` | `Items.java:2652, 251` **[VERIFIED]** | the hut-4 band |

**Produced.** One book from the **main pool only** — no bonus pool, no treasure, so T3 holds by
construction.

**The rate limiter, and where it lives.** **World generation.** Not one item in that list is craftable.
Pottery sherds come out of suspicious sand and suspicious gravel, which occur only in generated
structures and are destroyed by being brushed (`Items.SUSPICIOUS_SAND`/`SUSPICIOUS_GRAVEL` at
`Items.java:222-223`, `BRUSH` at `:2653` **[VERIFIED]**; that brushing consumes the block and that no
recipe produces a sherd are properties of vanilla's *datapack*, which is not in this source tree, so both
are **[UNVERIFIED]** here — see §7). Trial keys come from trial spawners, echo shards from ancient city
chests, heavy cores from ominous vaults. **Every one requires the player to physically go somewhere they
have not been.** There is no farm, and no hopper reaches a desert temple.

**Why it is worth including even though it is the smallest.** It is the only design here that costs the
player nothing they were using, and it turns the drawer of duplicate sherds that every long-running world
accumulates into a reason to walk into the tower. It also gives the colony a reason to care about
exploration, which the mod otherwise almost entirely lacks.

**Hut levels 4 and 5.** The tier gate: hut 4 reads sherds and trial keys, hut 5 reads echo shards and
heavy cores. A player who reaches hut 5 and has been to an ancient city gets a materially better band —
a reason to build level 5 that has nothing to do with probability tuning.

**Code changes.** Datagen only, plus one shared AI change.

| Change | Anchor |
|---|---|
| The item tag | `mc/core/generation/defaults/DefaultItemTagsProvider.java` (the `.add(...)` idiom is at `:145` and `:222`) **[VERIFIED]**; tag key constant beside the others in `ModTags` (`meshes` at `:61`, `breakable_ore` at `:66`) **[VERIFIED]** |
| The recipes | `PROV(HEAD):242-256`, three entries with `.minBuildingLevel(4)` / `(5)`, inputs declared like the tome's (`PROV(HEAD):244-246`) |
| The tables | reuse `tableRolledBook(low, high)` (`PROV(HEAD):216-222`), registered at `PROV(HEAD):297-307` |
| The stocking branch | `AI:200-229`. **The only non-datagen change**, and it is the same generalisation D1, D3, D4 and D7 all need: `decide()` hard-codes `IS_ANCIENT_TOME` (`AI:59`) as the only thing worth fetching, and `AI:254` assumes the tome recipes are the only empty-output ones |

**Fix size.** **S**, comfortably under 150 lines including the tag and the recipes.

**Assets / datagen / config.** **No new assets and no new items.** `runDatagen` re-run; one new tag file,
three recipe files, three loot tables. No config.

**Balance change?** Yes, but small and well-bounded: a slow trickle keyed to how much of the world the
player has seen.

**Mixin / AW / neither.** **Neither.**

**Adversarial.** *"I will farm trial chambers."* Trial spawners recharge on a cooldown and each chamber
has a fixed number, so a chamber is a slow renewable and a world has finitely many near a colony; the
exact cooldown in snapshot-10 is **[UNVERIFIED]** here. Price trial keys low. *"I will farm nether stars
with a wither farm."* Correct, which is precisely why `NETHER_STAR` (`Items.java:2213`) is **not** in the
tag despite being thematically perfect: it is the one "rare" vanilla item with a well-known automated
farm. Every item added to this tag has to survive that test individually. *"I will trade for sherds."*
Sherds are not in any villager or wandering-trader table **[UNVERIFIED]** — confirm against the vanilla
datapack before shipping, because a trader entry would break the design outright. *"I will hopper them
in."* Yes, and it does not matter: the supply upstream of the hopper is the cap.

**Where it fails.** It does not create *steady* work; it creates bursts, after the player explores. A
colony whose owner never leaves home gets nothing from it. That is a feature if the goal is to reward
exploration and a bug if the goal is a daily rhythm.

---

### D7 — Battle salvage: the field after a raid

**Fantasy.** After a raid there is a field full of broken kit, and a notched sword still remembers the
swing that notched it. The enchanter goes through what the guards dragged home and gets a fraction of a
tome out of every few pieces.

**Consumed.** Four items from a new tag `minecolonies:raider_spoils` — the equipment raiders drop — plus
one book. **Produced.** One main-pool roll at the band one tier below the tome band for the same hut
level.

**The rate limiter, and where it lives.** **Raids, deliberately.** Raider drops come from the same entity
loot tables that already produce ancient tomes
(`mc/core/generation/defaults/DefaultEntityLootProvider.java:73-191`; the tome entries at `:76, 81, 86,
94, 99, 108, 113, 118, 125, 130, 135, 148, 152, 157, 168, 173, 178, 191`) **[VERIFIED]**, and raid
frequency is config-capped at an average of 14 nights and a minimum of 10
(`mc/api/configuration/ServerConfiguration.java:389-390`) **[VERIFIED]**.

**What this design is honestly for.** It does not create a second economy. It **spreads one raid's output
over the fortnight after it**, which from the player's chair is the same thing as "work between raids"
and costs a fraction of what the other six cost. It is the cheapest way to reduce spikiness without
changing the shape of the profession.

**Hut levels 4 and 5.** The conversion ratio: six spoils per roll at hut 3, four at hut 4, three at hut 5.

**Code changes.** Datagen only, plus the same `AI:200-229` stocking generalisation every second path
needs. Tag in `DefaultItemTagsProvider`, recipes at `PROV(HEAD):242-256`, tables at `PROV(HEAD):297-307`.

**Fix size.** **S**, under 100 lines.

**Assets / datagen / config.** **No new assets, no new items.** `runDatagen` re-run. No config.

**Balance change?** Yes, mild.

**Mixin / AW / neither.** **Neither.**

**Adversarial.** *"I will summon raids with the command."*
`mc/core/commands/colonycommands/CommandRaid.java` exists **[VERIFIED]** and
`IRaiderManager.RaidSettings#withImmediateStart` is a first-class option
(`mc/api/colony/managers/interfaces/IRaiderManager.java:237`) **[VERIFIED]**. That is an operator
command, not a farm, and a server that hands it out has already decided this question. *"I will farm
raider mobs."* Colony raiders spawn from the colony's own raid cycle, not from a spawner, so there is
nothing to build. *"I will feed it ordinary iron swords."* Only if the tag is written carelessly — it must
name raider-specific drops, or carry the same component-matching trick D4 uses.

**Where it fails.** It is a smoothing function. If the raid cadence is the real problem, this
redistributes the problem rather than solving it.

---

## 4. Ranking

Ranked by "how much of the stated problem it solves, per line of code, without opening a new hole".
Tests are from §1.1.

| # | Design | T1 non-automatable | T2 capped | T3 tome-preserving | Size | Balance | New assets | New items | Datagen | Config |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | **D1 — The mana ledger** | **hard** (citizen skill XP) | **hard** (1 drain/station/day; floor at level 1) | yes, bonus pool stays behind the tome | M | yes, large | none | none | yes | optional |
| 2 | **D3 — The tower's charge** | **hard** (not an item) | **hard** per building; soft per colony | yes, the tome becomes the overflow | M | yes, sweeping | none | none | yes | worth one |
| 3 | **D6 — The antiquarian** | **hard** (worldgen) | hard, but bursty | yes, main pool only | **S** | yes, small | none | none | yes | no |
| 4 | **D2 — The exchange** | **hard** (conservation) | **hardest** (a conservation law) | needs one explicit exclusion | M | yes, gentle | none | none | minimal | no |
| 5 | **D4 — Marginalia** | hard (component-gated) | **soft** (build more libraries) | yes, main pool only | M | yes | none | none | yes | no |
| 6 | **D7 — Battle salvage** | **hard** (raids) | hard, but it is the raid cap again | yes, main pool only | **S** | yes, mild | none | none | yes | no |
| 7 | **D5 — Field study** | **hard** (absence, risk) | **hard** (per-period counter) | yes, and it may add tomes | **L** | yes, riskiest | none | none | yes | worth one |

Notes on the order, since the columns do not fully explain it:

* **D2 has the strongest limiter of all seven and still ranks fourth**, because a conservation law over
  an empty warehouse conserves nothing. It is a multiplier on D1, D3 or D6, not a first move.
* **D6 ranks above D2 and D4 despite being the smallest**, because it is S, needs no new assets, no new
  items, no new AI state and no balance argument beyond "how many sherds is a book worth" — and it is
  genuinely unfarmable.
* **D7 ranks low not because it is bad but because it is small in effect**: it makes the existing economy
  less spiky without making it a different economy.
* **D5 ranks last on cost, not on quality.** It has the best fantasy in the document and the second
  hardest cap. It is L, it is the only one that can kill a citizen, and nothing about how it feels can be
  settled without playing it.

Two pairings worth respecting:

* **D2 after D1, D3 or D6.** The exchange needs a pile.
* **D6 with anything.** It is additive, cheap, and shares the one AI change (`AI:200-229`) that D1, D3,
  D4 and D7 all need anyway, so shipping it alongside any of them costs almost nothing extra.

One anti-pairing: **D1 and D3 are alternatives, not complements.** Both make a non-item budget the price
of a book. Shipping both gives the enchanter two currencies and no clear story, and the balance work
doubles.

---

## 5. First pick

**D1, the mana ledger — shipped together with D6, the antiquarian.**

D1 first, for five reasons, the first of which is new since `996f3aa3` landed:

1. **The economy is already built; one wire is loose.** Books now cost Mana (`AI:296`), Mana now decides
   book *quality* through the loot `quality` term (`PROV(HEAD):91-94` against the crafter's luck at
   `AbstractEntityAICrafting.java:797`), and the only thing that replenishes Mana is the drain. The drain
   is the one part that still takes from nobody (`AI:356`). **D1 is not a new system; it is the missing
   input to a system that now works everywhere else.** No other design on this list can say that, and
   none of them makes the drain mean anything.
2. **Its limiter is the hardest available.** A citizen's skill level cannot be farmed, cannot be
   hoppered, is destroyed when spent, and gets more expensive to replace the more of it you have. No
   item-based input matches that, and no per-building counter survives the "build a second hut" attack
   the way this does: a second enchanter needs a second set of stations and taxes the same finite pool of
   citizens.
3. **It is a trade with a face on it, which is the difference between a cost and a tax.** The player
   chooses *whom* to drain, and the choice has texture. Guards between raids are idle and near their
   towers, so draining them to make the books that arm them is a loop that closes on itself; miners and
   lumberjacks are out of range most of the day and are effectively exempt. That is a decision worth
   making repeatedly, and it is made in a screen the player already has open — a screen that has existed
   since the profession shipped and has never decided anything.
4. **It needs no new items, no new textures, no new models, no access widener, no mixin and no upstream
   asset.** New lang keys go in this repository's own `en_us.json`.
5. **It closes the last of the audit's blocking defects on the way past.** `AI:356` is F1 and `AI:438` is
   F5, both in the same method. If the in-flight work of §0.2 lands first, D1 gets smaller still: what is
   left is the debit, the reservoir and one recipe family.

D6 alongside, because it costs almost nothing to add: it shares D1's only structural AI change (the
`AI:200-229` stocking generalisation), it is pure datagen otherwise, it adds no assets, and it covers
D1's one gap. **D1 gives the enchanter a steady trickle bounded by the colony's own talent; D6 gives it
an occasional burst bounded by the player's own exploration.** Together the tower is never idle and never
a factory.

Then, in order: **D3** if the mana economy proves too slow in play and a second, independent throughput
dial is wanted — noting the anti-pairing in §4, which means choosing between them rather than stacking
them; **D2** once a pile exists worth exchanging; **D5** only if the profession is being reimagined
rather than repaired.

---

## 6. Directions considered and rejected

**Lapis and a book at hut 4 (proposal P2).** Rejected by the project owner, and correctly: it fails T1
outright and T2 by construction. Lapis is renewable from a mineshaft or a villager, books are renewable
from sugar cane and cows, and the pair is autocraftable. The general form — *any* second path whose
entire price is farmable items — fails the same way, which is why every design above puts at least one
non-item term in the price.

**Grave-lore: bind a dead citizen's memory.** The graveyard system exists
(`mc/api/colony/managers/interfaces/IGraveManager.java`, `mc/core/colony/managers/GraveManager.java`,
`BuildingGraveyard`) **[VERIFIED]** by file listing, and the fantasy writes itself. Rejected on
incentives: any design that pays the player for a dead citizen pays the player for killing citizens, and
a colony has no shortage of ways to arrange that. A mechanic must not make the player want the thing the
game treats as a failure.

**Player experience levels.** Superficially the right currency — it is what a vanilla table costs. But XP
is the most thoroughly farmed resource in Minecraft, and a colony that eats it is a colony whose enchanter
runs off a mob grinder. Fails T1.

**A flat daily quota with no other price.** "Three books a day, no inputs." Passes T2 perfectly and fails
the fun test completely: there is no decision in it and nothing to trade. D3 is this idea with a decision
added — the budget is spent at a level the player picks — which is what makes it a design rather than a
limiter.

**A consumable focus item that wears out.** The recipe-tool mechanism makes this nearly free from datagen
(§2), and it is a good *component*: an "enchanter's lens" taking one durability point per book, crafted
from something expensive. Rejected as a *design*, for two reasons. A durable item made from farmable
materials is a tax, not a cap — it scales linearly with the player's mining throughput. And it would need
a new item, and therefore original 16x16 art plus a model JSON, which is the only thing on this list that
would have put a new asset in the jar. Worth keeping in the toolbox as a tuning knob on whichever design
ships.

**Nether stars, and any farmable "rare" item.** Named here because it is the trap this whole document is
about: an item's *rarity* is not its *unfarmability*. Nether stars, ender pearls, blaze rods, wither
skulls, gold and iron all have well-known automated farms. Only items that come out of generated
structures and cannot be crafted or traded — D6's list — are actually safe, and every addition to that tag
has to be checked individually.

---

## 7. What I could not verify

* **Anything requiring a running server, a build or datagen.** No `runDatagen`, no `build`, no colony.
  Every throughput number above is arithmetic on constants, not an observation.
* **Whether the drain can find a target at all** — D1's one real risk. `MIN_DISTANCE_TO_DRAIN = 10` with
  60 retries (`AI:69, 390`) was written for a target that was always in range because it *was* the
  enchanter. How often a real worker is within ten blocks of its own hut during the enchanter's working
  window is the single most important unknown in this document, and it decides whether D1 is a working
  economy or a stalled one. Confirming means watching a colony. The in-flight work of §0.2 raises the
  range to 20, which helps but does not settle it.
* **How the newly shipped tables actually behave.** `996f3aa3`'s figures — 30% fine-band at hut one, 67%
  at hut five, 83% at Mana 99 (`PROV(HEAD):83-97`) — are the commit's own arithmetic, read but not
  recomputed here and not observed in play. D1 changes the Mana distribution those numbers assume, so
  they will need re-deriving.
* **Vanilla datapack facts used by D6**: that pottery sherds have no crafting recipe, that brushing
  consumes the suspicious block, that sherds appear in no villager or wandering-trader table, and the
  trial-spawner recharge cooldown. All four are properties of vanilla's *data*, which is not in
  `/opt/mc-src-26.3-snapshot-10`'s source tree. They are well-known properties of the items, but this
  document does not claim to have read them, and each should be confirmed against the vanilla data pack
  before D6 ships.
* **The tome supply figure** of roughly 0.1-0.3 per in-game day, inherited from the audit and itself
  [UNVERIFIED] on raid composition. Every "the enchanter is starved" argument here inherits that
  uncertainty. If the true figure is an order of magnitude higher, D7 gains value and D1, D3 and D6 lose
  some.
* **The client half of anything.** The settings-module windows D2 and D3 would use were read for their
  asset references only, in the companion document; nothing about rendering has been exercised on this
  branch.
* **`PROV` line anchors specifically.** They are `git show HEAD:` anchors taken while that file was
  modified and uncommitted in the working tree **[VERIFIED]** by `git status`. They were correct for `HEAD`
  (`019e039e`) at the time of reading and may already be wrong.
