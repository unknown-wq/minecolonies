# TODO

Work that is designed, measured or discovered but **not done**. Everything here has a written basis
somewhere in this tree — nothing on this list is a bare idea.

Three states are used, and they mean different things:

* **Designed, awaiting approval** — there is a plan. Nobody has agreed to build it.
* **Measured, not started** — there are numbers saying it is worth doing.
* **Known and deliberately left** — found while doing something else, judged out of scope. Recorded so
  the next person does not rediscover it and think it is new.

---

## Designed, awaiting approval

Two plans, written 2026-08-08, both deliberately unimplemented until the owner decides. They were
designed in parallel and are meant to compose: the second is the framework, the first is its first
consumer. **Neither was measured** — both cost estimates are inferences from loop shape, and both plans
say so in their own last section.

### The colony builds housing by itself

Builds residences without the player, until every citizen has a bed. ~750 lines, 12 files, 2 new
classes, no persistence. Off by default: a town-hall setting plus a server config, because research is
one-way and cannot be an off switch.

The shape of it: upgrade an existing residence before placing a new one — both give one bed, but
upgrading has no siting risk and does not scatter huts across the map, which matters because
[`AI-SCALE-AUDIT.md`](../audit/AI-SCALE-AUDIT.md) §1.2 measured failed `PathJobMoveToLocation` at 72.7 of the
pool's 77.4 busy seconds. Siting walks rings of a grid, one building per cell, so non-overlap is
structural rather than checked; the terrain test reads heightmaps only. It does not terraform — rough
ground and water are rejected, not levelled. Materials go through the existing request system unchanged.

Two structural facts the plan turns on, both verified in code: a work order cannot be created for a
building that does not exist (`WorkOrderBuilding.create` needs a live `IBuilding`), so a hut block must
be placed and registered first, exactly as a player does; and residence footprints are byte-identical
across levels 1–5 in every shipped pack, so a level-1 site is a level-5 site.

**Biggest risk, and it is not eliminated:** the mechanism can bulldoze player work that is not a
registered building. Block-entity and natural-surface vetoes catch most of it and construction tape
warns before the builder starts. This is why the default is off and every other choice is conservative.

### A ranked account of what the colony lacks

A live list — housing, food, tools, materials, production, workers — that other systems read to decide
what to build, craft, farm and research. A need is a **measured deficit, never an event**: recomputed
from the world, never closed by a consumer, it stops existing because the next measurement does not find
it. That is the structural answer to re-trigger leaks, of which this codebase already has one measured
example (`AI-SCALE-AUDIT.md`: the same unreachable walk re-asked 57 times in 90 seconds).

The record separates `magnitude` (a count) from `urgency` (0..1, comparable across categories), plus
`inFlight` so consumers act on `magnitude − inFlight`. Ranking is not invented — the category weights
are the mod's own happiness modifier weights, balanced for years. It sits **above** the request system
and reads from it: requests answer "who brings what to whom", this answers "what should the colony start
doing next".

Honest conclusion in the plan itself: **roughly 70 % of the content already exists**, scattered across
five shapes. The new work is the shape, the rank and the lifecycle.

**Stage 0 is the piece worth taking even if nothing else is:** a diagnostic plus a performance fix with
zero behaviour change, which deletes ~100 lines of duplicate counting by rewriting `/mc colony diagnose`
onto the model. It also removes an existing burst of roughly 2·N² iterations per game day — the
happiness caches all drop at nightfall and `getSocialModifier`/`getGuardFactor` each loop every citizen.

### [`../docs/studies/gatehouse.md`](../../docs/studies/gatehouse.md) — five things to do with the gatehouse

Written 2026-08-15, shown to the owner, **parked by him for later**. Five ideas, cheapest first, all
priced against the code.

What the study establishes first is worth keeping even if none of the five is ever built: **the
gatehouse is not a trade post and never was**, here or upstream — nothing in it moves goods. It is two
things bolted together: a four-post guard tower (2 knights, 2 archers, task locked to GUARD, max level
3), and the endpoint of a **fully implemented colony-to-colony diplomacy and fast-travel network** —
colony signs bound to a gatehouse, planted every ≤50 blocks along a road the mod *pathfinds to verify*
(a sign whose path fails is destroyed in the world), terminating at another colony's gatehouse, with
ally/feud from the town hall's Alliance page and teleport between allied gates for a gold-nugget toll.

Three things about that network matter to anyone who touches it: `DiplomacyStatus.HOSTILE` has **zero**
mechanical consequence anywhere (it hides a button and fails a teleport, nothing else); the gatehouse
has **no relation to the colony border or to hostile territories**; and the whole second half is **dead
content in single-player**, because it needs a second colony.

The five, cheapest first: the gate names its enemies (~95 lines, client-only, signage); a gate that
charges a settable toll (~150, cheap and dull); **the gate pushes the frontier** — a manned level-3
gatehouse takes enemy chunks one at a time, the only mechanic where a *building* rather than a scepter
click moves a border (~230); **the border post** — the territory across the line posts standing offers,
stock via the existing courier feed, rate decaying lazily on read, clock from your own colony (~340
java + ~90 data, no new block, blueprint or recipe); and a caravan of travelling citizens along the sign
road, loss chance scaled by how much of it crosses hostile ground (~450, servers only, and the one that
can lose citizens permanently).

The study's author would build the border post with the enemy list as its front page: the trade thread
at roughly a third of the month that [`../docs/studies/territory-mechanics.md`](../../docs/studies/territory-mechanics.md)
entry 10 costs, precisely because six of that entry's nine expensive parts already exist on this
building — and unlike entry 10 it works solo.

**Four real defects were found in the existing gatehouse code** while writing it, including a "your
nearest sign is 2147483647 blocks away" message (an int overflow standing in for "there is no sign")
and a dead missing-link guard. They are listed in the study and none is fixed.

### Decisions the owner has not made yet

1. Both plans, or only stage 0 of the needs model?
2. Auto-build standalone, or on top of the needs model? Both authors and the orchestrator recommend
   standalone, taking the `peek`/`claim`/`report` signatures verbatim so adoption is later a one-line
   swap. The two designs argue against merging: auto-build's hard problem is siting, which has nothing
   to do with needs, and a model shaped around one consumer is shaped wrong for the rest.
3. Is the bulldozing risk acceptable, given the feature ships off?

---

## Wanted: 26.2 content the port does not use yet

The owner's own list, written 2026-08-15, from playing 26.2 and noticing what the mod ignores. This is
a **wishlist with the owner's own cost guesses**, not a set of plans — nothing here has been priced
against the code yet. The traps named in each item are his, found by knowing this mod rather than by
reading it; they are the right places for anyone estimating to look first, and they need verifying
rather than believing.

### Tier 1 — the owner's estimate is a datapack evening each

* **Cinnabar to red dye.** An ordinary recipe plus an entry in the dyer's custom recipe list. **Zero
  code**, on his reading.
* **A copper equipment tier.** The work is not the items, it is registering copper in the level tables
  — the mining level, and the armour and tool scoring the mod uses when handing equipment to guards and
  workers. Leave that out and colonists ignore copper as an unknown item. Plus building-level
  requirements in research.
* **Animal variants by biome.** The one he finds most interesting. Breeding either inherits the
  parents' variant or takes the biome's by location; the change lands in the shepherd and cowhand AI.

### Tier 2

* **A spearman.** The point is **not to make a new building**: register a third guard type in the
  existing tower beside the knight and the archer — a new guard type plus AI. The spear must also be
  registered as a new equipment type, or the request system cannot order it. A charged attack fits the
  "holds position at the wall" behaviour the tower already has.
* **An alchemist.** Cheapest as a crafter on the existing framework, the way the enchanter and baker
  are. 26.2 has sulfur cubes, so the recipes have something to be made of. **Also look at the
  apothecary** — sulfur may fit there naturally instead of, or as well as.
* **Undead camels.** Biome raids already exist and Egyptians already spawn in the desert. Cheaper and
  more coherent to add mounted raiders **inside the existing desert raid** than to invent a new event
  type, which would mean duplicating the wave, barrier and reward logic.

### Tier 3

* **A copper golem, and copper chests.** The trap: the warehouse works through **racks**, and building
  from a blueprint converts chests into racks. A copper chest will not simply slot in — either a
  separate "copper rack" block the golem accepts as a chest, or conversion disabled for that block and
  couriers taught to see it as a container. And oxidation means the warehouse can **silently break over
  time**, so it needs a visible indicator in the GUI or it will arrive as bug reports.
* **Mounted couriers and guards.** The most expensive thing on the list. The mod has its own
  multithreaded navigation and it knows nothing about riding: entity width, jumps, water, dismounting
  at a door.
* **A nautilus, and an underwater outpost.** Blocked not by the mount but by colonists living badly
  underwater at all — breathing, working in liquid, delivery. A subproject of its own.

---

## Deferred by the owner, 2026-08-14

Designed or measured during the 0.0.18 round, shown to the owner, and explicitly postponed —
*"we'll do it later"*. Not rejected. Recorded here so none of it has to be re-derived.

### [`TOURISTS.md`](../TOURISTS.md) — visitors that behave like guests

Ten pieces, A–J, **5.75 days in total**, each standing alone so any prefix can ship. The owner asked
for the full picture — guests walking to the restaurant, spending gold and emeralds since the mod has
no currency, staying 1–5 colony days, the tavern acting as a hotel — and the answer is that most of the
machinery already exists for the wrong entity.

**Minimum coherent slice is A + B, 1.25 days**, and it is the best visible result per unit of work by a
wide margin: lift `EntityAICitizenWander` from `EntityCitizen` to `AbstractEntityCitizen`, plus a
give-up rule for unreachable targets. Guests then leave the tavern and walk to the town hall, the
library, the tavern and any `leisure`-tagged decoration, because `getRandomLeisureSite()` already
enumerates all of those and that AI already *is* a tourist — it walks to a site, wanders inside its
blueprint corners, uses the `stand_in`/`sit_*` tags and reads a book in the library. The blocker is
mechanical: the class hangs its transitions on `getCitizenAI()`, while visitors run on
`getEntityStateController()`. **It must be a lift, not a copy of 300 lines.** Second best is F, buying
food, which makes payment visible as gold appearing on the restaurant's shelves.

Four findings that shape the rest, each read from code:

* **The purse already exists.** `VisitorData extends CitizenData`, so a visitor has a real inventory —
  persisted, dropped on death, and already openable with shift-right-click. "Arrives carrying twelve
  gold ingots" is a filled inventory, not a new system. `recruitCost` stays a price tag; do not
  generalise it.
* **The currency table is already a datapack.** `colony/recruitment_items`, ~54 generated files with a
  rarity 1–9, and `getRandomRecruitCost` already rolls against building level, so "poor colony, poor
  guests" is a formula that exists. Clone it to a `visitor_wealth` list rather than reuse it — the
  recruit list deliberately contains bread and paper, which is fine to hire with and wrong to pay a
  restaurant with.
* **`EntityAIEatTask` is a complete restaurant visit** — nine states, and `BuildingCook` already hands
  out seats, skips occupied ones and will not seat anyone outside in the rain. A tourist can eat
  without the cook AI being touched. But **the cook cannot see a visitor**: `checkForImportantJobs`
  scans `EntityCitizen.class` and `VisitorCitizen` is a sibling, not a subclass. Copy the cook's
  existing serve-a-*player* path rather than widening the citizen loop.
* **Two expiry timers will fight.** Starvation removes a visitor after ~150 000 ticked ticks; a 5-day
  stay is 120 000. The day counter has to win, and hunger should become the *reason to visit a
  restaurant* rather than a reason to be deleted — which collapses eating and spending into one
  mechanism.

Checked and clear: **beds do not feed the citizen cap.** `calculateMaxCitizens` reads
`LivingBuildingModule#getModuleMax()`, and the tavern's is a hardcoded 4 regardless of blueprint beds,
so guests sleeping cannot move the population ceiling. There is a different collision — `EntityAISleep`
picks a bed by list index and only checks occupancy after walking there, so a guest can displace a
resident. A separate `guest_bed` blueprint tag keeps the lists disjoint.

Two things called out as not-Java: the hotel's markup is blueprint work whose size is set by the number
of styles in the pack, with a free fallback of "guests sleep on benches" (roughly what visitors already
do at night). And **piece L, walking in from the colony border, should be cut entirely** — that is the
39–47 ms path job that does not arrive on natural terrain, aimed into chunks the colony does not hold.
Arrive and depart at the gate; half of that already exists in `spawnVisitor()`.

Cost, computed against the measured figures in [`PATHFINDING-DISTANCE.md`](../PATHFINDING-DISTANCE.md) and
[`AI-SCALE-AUDIT.md`](../audit/AI-SCALE-AUDIT.md) rather than guessed: a visit is ≈14 ms of pool time, so a
tourist visiting every two minutes is about **a seventh of a citizen**; 50 of them ≈ 7 citizens. 20 is
free, 50 shows up only on instruments, 100+ wants measuring. **But N is not the real risk** — 96 % of
jobs on a large colony never arrive and the walk helpers re-ask forever, so one tourist with an
unreachable target outcosts ten well-behaved ones. Without the give-up rule from piece B, no N is safe.

### [`CITIZEN-LOD.md`](CITIZEN-LOD.md) — cheaper citizens in distant claimed chunks

The owner asked for "virtual citizens": no routes, no bodies, teleporting and placing blocks in far
chunks, materialising when a player approaches. **The recommendation is not to build it**, and the
reason is worth keeping: a distant citizen already has no body and already costs nothing, so
`forceloadallclaims` and a LOD scheme are two changes that cancel.

Measured on an isolated server, `/tick sprint 600`, no players or entities: 441 force-loaded chunks
cost **0.96 ms/tick**, 1089 cost 3.25 ms; with `random_tick_speed 0` those fall to **0.10** and 0.66.
So **~90 % of the cost of force-loaded ground is vanilla random block ticks**, not this mod, and the
ground is ~13 % of a distant colony's cost against ~87 % for the bodies. The owner's "one player per
441 chunks" is a chunk-count equivalence, not a tick-time one — in tick time it is ~2 % of budget.

What already exists, and is the reason not to design something new: **`IJob`/`IBuilding#processOfflineTime`
is declared in the API and called from `worldTickSlow` off a persisted `lastOnlineTime`, and exactly
one building of fifty implements it.** That is "accumulated history, you arrive and it is done",
already wired. The builder's `progressPos`/`progressStage` cursor lives on the building and is
persisted, so the blueprint plus that cursor already *is* a journal — a separate one is redundant.
`TravellingManager` is a finished remove-body/tick-a-timer/respawn mechanism shipped for the nether
worker. And `CitizenData.update()` returns on its first line when there is no entity, which is why
nothing progresses today.

Three designs, ranked: **(1)** fill in `processOfflineTime`, ~400 lines for the builder and 50–150 per
profession after, medium risk, zero cost while away, no save-format change. **(2)** hold the claim at
`BLOCK_TICKING` instead of `ENTITY_TICKING` — ~20 lines, low risk, ~10× cheaper by the measurement
above, but it costs crop growth, so config-only. **(3)** the journal, last: redundant for builders and
the only option whose failure mode destroys the player's property. A second simplified AI per
profession was rejected outright.

**Independent of any of that:** `updateEntityIfNecessary` respawns *every* citizen in one tick with no
budget. That is an arrival stall that exists today, and it is the cheapest thing on this list.

### Land macro edges, and the exit condition that is now shipped

**Update, later the same day:** the exit condition described below as the blocker is **done and merged**
— `stopsearchonarrival`, written up in [`PATHFINDING-EXIT.md`](../PATHFINDING-EXIT.md). It shipped off in
0.0.19 and is default **on** from 0.0.20, now that it is being played. It is
what makes both macro-edge rounds pay: rail 800 m falls 6223 → 16 nodes, open sea 796 m 8301 → 30, bare
plain 400 m 8109 → 402, every path identical. It also repairs the open-water regression the rails round
found in the shipped 0.0.17 boat edges. The measured prize on an *ordinary* colony is only 3.7–5.6 % of
pool time, because 93–96 % of searches never arrive and so have no tail — the value is concentrated
entirely in corridor routes.

One thing there is **not** an account for: `PathJobMoveToLocation` measured 8 % cheaper end to end while
its own tail measures 2 %. Most likely second-order (routes return sooner, the job mix in the window
differs), but it is unexplained, and the honest split is 5 % attributable against 10 % observed.

**Still open below:** land macro edges.

### Rail and land macro edges, and the distance ceiling

Rails are **in flight** as of this entry. Land is not, and is the larger job: open ground is
unstructured, so a straight probe only pays on flat terrain and the win is much less predictable than
water's. It wants a design pass first — Fable's directions 1 and 2, including the lazy `ChunkCache`.

The ceiling at `MinecoloniesAdvancedPathNavigate.java:404` (`distSqr > 900 * 900`) is one line, and
**raising it is not the hard part**. `MAX_NODES` is 8000 (adjustable to 32 000 through
`pathNodeLimitMultiplier`), but `Pathfinding.java:50` is `ThreadPoolExecutor(1, 1, …)` — one thread for
every citizen, raider and animal on the server. At ~2.7 expanded nodes per block of route without macro
edges, 3000 blocks does not fit in the budget at all and 2000 barely does, at ~27 ms of that one thread
per search. The cap earns its keep: refusing instantly is far cheaper than searching to exhaustion and
failing. It is also **terrain-blind**, so it cannot tell a now-cheap water route from an expensive
overland one.

### Farmer: one visit per cell instead of three passes

Measured as worth **up to 3× on top of the 8.6× already shipped**, and deliberately not taken. What
prevents it is not the AI: `FarmField.Stage` is a persisted, player-visible enum with an icon and a
"current / next stage" tooltip in `FarmFieldsModuleWindow`, and the one-pass-per-colony-day gate is
keyed to it. Merging the stages makes it meaningless and changes a field's pacing from a third of the
work per colony day to all of it. **Most of the benefit is already banked** — with the per-cell skip in
place you pay for one walk plus two cheap scans rather than three walks — which is why the risk is not
worth taking now.

---

## Measured, not started

From [`AI-SCALE-AUDIT.md`](../audit/AI-SCALE-AUDIT.md), taken on a live 1000-citizen colony with the pathfinding
pool instrumented. Its own ranked table at the end is the authority; this is the short version.

**Cheap, measured, no gameplay change — do these first.** `ThreadLocalRandom` instead of the shared
`ColonyConstants.rand` in `AbstractPathJob.computeCost` (8.3 % of the pathfinding thread's core, two
lines); `getRandomCitizen()` without `toArray()` (`CitizenManager.java:666`, allocates a 1000-element
array per call); `Pathfinding.shutdown()` actually shutting the pool down and `getExecutor()`
synchronised (`Pathfinding.java:46-61` — `shutdown()` currently only clears the queue); purging
cancelled jobs from the queue. Under 30 lines in total.

**Then, in this order and not the other:** back off when re-pathing to an unreachable target
(`EntityNavigationUtils.java:154-206`), *then* raise the pathfinding pool to two threads
(`Pathfinding.java:50`). Item 1 removes the cause, item 4 compensates for the symptom. Doing only the
latter masks the retry loop — the audit measured duplicate searches falling 44 % → 29 % purely because
paths arrive faster — and the real problem returns in the next audit.

**Research before it is work:** `EntityCitizen.getItemBySlot` costs 3.93 % of the server tick, reached
from vanilla's `EnchantmentHelper.tickEffects`. This corrects `AI-AUDIT.md` item 13, which under-priced
it by roughly 50× and proposed a mixin on the wrong caller — no mixin is needed. Also unmeasured: the
colony-view serialisation path, because the measurement colony had zero subscribers, which needs a real
client.

**Still open from the earlier [`AI-AUDIT.md`](../audit/AI-AUDIT.md):** items 11–16, 19 and 20. Item 16 is now
closed by the scale audit.

---

## Known and deliberately left

From [`REVIEW-2.md`](../audit/REVIEW-2.md), the second review of this branch. Both were raised as
merge-blocking, both were shown to the owner, and **the owner decided to ship as is**. Recorded so the
next person does not rediscover them and think they are new.

* **A homeless citizen can stay homeless forever with free beds in the colony.** A house now takes one
  only if it is the colony's "nearest", but the candidate list (`hasSpareBedFor`) excludes only
  `HiringMode.LOCKED`, while the condition under which a house actually does auto-house is much
  narrower (`LivingBuildingModule.java:72`). Three ordinary situations split them: a house in `MANUAL`
  passes as a candidate but never houses anyone; town-hall auto-housing off with one house in `AUTO`;
  and the nearest house sitting in an unloaded chunk, since `RegisteredStructureManager.onColonyTick`
  ticks only loaded buildings. In each, every house that *would* take the citizen defers to one that
  never will. Upstream, any eligible house took them. The fix is to narrow the candidate condition to
  the one under which a house really houses — the same shape `BuildingExtensionsModule.isNearestClaimant`
  already uses for fields, which guards both cases.
* **`/mc colony rehouse` moves citizens both out of and into hand-managed houses**, contradicting its
  own documented contract, with no undo. Same `MANUAL`/`LOCKED` confusion as above.
* **172 translation keys use `%d`, which Minecraft's component pipeline does not support** — 165 of
  them already in 1.21.1, so the pattern is upstream's. Measured against real 26.2 code: a template
  with `%d` renders as its own source text, while `%s` substitutes integers correctly. Only the two
  keys added on this branch were corrected. Whether all 172 are broken depends on how each is built,
  since a key formatted through `I18n` rather than through a component is unaffected — and one such
  message has been seen rendering correctly in a real client. That contradiction is unresolved.

From [`FARMER-AUDIT.md`](../audit/FARMER-AUDIT.md), found while chasing the reported "farmer is not hoeing the
ground". The report's own cause is **fixed**; these are what the audit passed over.

* **`BuildingExtensionsModule.serializeNBT` writes to the wrong compound.** `compound.put(TAG_ID, …)`
  where it means `listEntry.put(TAG_ID, …)`, the list goes under `TAG_LIST` while `deserializeNBT`
  reads `TAG_BUILDING_EXTENSIONS`, and a `putLong` is read back by `getIntOr`. So "when was this field
  last checked" does not survive a reload and the farmer walks its fields in a poor order once after
  relogging. **Byte-identical in `1.21.1/`** — an upstream bug, not a porting loss, which is why it was
  left. Three lines to fix, but it is an upstream change and should be taken as one.
* **`FarmField.deserializeNBT` throws on absent tags** — `getIntArray(TAG_RADIUS)` defaulting to an
  empty array makes `getRadius()` index out of bounds, and `Stage.valueOf("")` throws. Same on 1.21.1,
  and unreachable outside a corrupted save.
* **A level-1 farmer hut refuses an iron hoe** (`getMaxEquipmentLevel() == 1`, iron is level 2).
  Measured in game, identical on 1.21.1 — intended "tool too good" mechanics, not a fault. Worth
  knowing because it produces the *same* external symptom as the bug that was fixed, so some reports
  of an idle farmer will be this instead.
* **The dead tail of `findHoeableSurface` is still there** (lines ~409-432): the guard above returns
  `null` when `isRightFarmLandForCrop` is true, so the inlined till table is unreachable, here and on
  1.21.1 alike. Left in place to stay diffable against upstream; the comment now says so, since the
  old one claimed the block enforced hoe ownership, which it never did.
* **The farmer's client side is unverified** — the scarecrow GUI, the field list in the hut, and the
  seed/assign packets from the client were read but never run, for the same no-display reason as
  everything else client-side here.

* **The minecart has the same start-point hole the boat had.** `finalizePath` builds `points[0]` without
  flags, so it never carries `onRails` either — a path recalculated while a citizen is riding discards
  the cart the way it used to discard the boat. Pre-existing; the boat fix did not widen scope to it.
* **`/mc citizens walk` throws from the console for an employed citizen** — `UUID.fromString("unknown")`
  when there is no sender entity, swallowed as a WARN. Unemployed citizens take the other branch and
  work. `execute as <entity> run …` works around it.
* **A crossing interrupted by a node the citizen must dive through splits into two boat legs**, with a
  boarding each. The costs make it strongly unattractive; not special-cased.
* **Nothing client-side is covered by any automated check.** There is no display in the build container
  and `runClient` does not start, so the boat renderer registration, and the GUI generally, are verified
  only by a person playing the built jar.
* **The colony-needs design corrects two things this project believed.** The bed-shortage line
  comes from `/mc citizens fill`, not `/mc colony diagnose` — diagnose carries the same information only
  as the `current/max` ratio. And `BuildingResourcesModule` is the builder's current-work-order resource
  list, not the general "a building declares what it wants" mechanism; that is `MinimumStockModule`.

Found during the 0.0.18 round (2026-08-14), each while doing something else and each judged out of
scope for the change in hand.

* **The minecart has no held-up rescue at all.** The boat now has a 40/60-tick ladder that ends a
  crossing which has stopped making progress; `rescueIfHeldUp` is boat-only, and `handleRails` has no
  stuck detection of any kind. A cart that derails or jams mid-leg leaves its citizen sitting on a node
  that still looks legitimate. Nobody has reported it; it is a larger piece than the two dismount holes
  that were closed.
* **`handlePathOnRails` has no mirror of the boat side's "get out of the old vehicle first".** A rails
  leg immediately after a water leg silently orphans the boat. Harmless today, because an empty
  `MinecoloniesBoat` discards itself within a tick.
* **`reevaluteHeuristic` counts path length with `dist++` per node rather than summing
  `getEdgeLength()`.** With macro edges a node can stand for up to 64 blocks, so that counter now
  measures nodes, not blocks. Water made it wrong; rails will make it wronger. Being looked at with the
  rail work.
* **`checkIfShouldExecute`'s `PLANTED` branch spends compost and grows crops** while doing what reads
  as a scan — it calls `findHarvestableSurface` across the whole spiral in a single tick. Upstream, and
  startling. It is also the reason the farmer's per-cell skip deliberately does *not* pre-test
  harvesting: asking twice would fertilise from a distance and again on arrival.
* **`workingOffset`/`cell` live on the building, not on the field**, and `nextValidCell` only resets the
  counter when the offset is null. A field released or reassigned mid-pass leaves the next field
  continuing from a stale cell index, skipping its inner rings for that one pass. Self-corrects at the
  end of the pass. Port-introduced, minor.
* **`SOFT_SHOES` trample prevention is dead code.** `EventHandler.shouldPreventCropTrample` was a
  NeoForge `FarmlandTrampleEvent` handler, so the research "Farmers will no longer trample crops" does
  nothing. Restoring it properly means a mixin on vanilla `FarmlandBlock#fallOn`, and **this tree has no
  mixin configuration at all** — no `mixins` block in `fabric.mod.json` or `build.gradle` — so it is a
  build-level change. Fixing only `MinecoloniesFarmland` would switch the research on for mod crops and
  leave it off for wheat, which reads worse than uniformly off.
* **MineColonies' own crops are not in `#minecraft:maintains_farmland`.** On a dry field,
  `minecolonies:farmland` under a *growing* mod crop still reverts and the crop pops off. Upstream is
  the same — `MinecoloniesCropBlock` never implemented NeoForge's `SpecialPlantable`. A one-line tag
  entry would fix it, but it is a deliberate behaviour change away from upstream.
* **A free-mode player who empties the fuel list by hand is still nagged.** Every other "choose
  something for me" interaction is now free-mode-safe; the smelter/baker fuel list ships with coal and
  charcoal in it, so reaching this needs a deliberate act. One line if it ever matters.
* **Colony-view traffic, from the chunk-ticket work.** `Colony.ticketedChunksDirty` is never reset to
  false, so the whole ticketed set is serialised into every colony view packet; and
  `ColonyView.serializeNetworkData` sends the *entire* claim map every update unconditionally — for a
  400-chunk claim that is ~12–28 KB/s per close subscriber, which already dominates. Also
  `BackUpHelper.reclaimChunks` restores only the initial box plus building claims, so scepter-claimed
  enclaves are not recovered, and `ColonyConstants.TICKET_ID` is now dead.
* **A chunk's `ChunkClaimData` lives in the map of whichever colony first touched it**, and ownership
  can move to a neighbour without the entry moving. Two colonies claiming into each other can hide a
  chunk from its owner's map. Pre-existing; it already governs `maxoutlyingchunks`.
* **`KEEP_LOADED_TYPE` is not registered in `BuiltInRegistries.TICKET_TYPE`.** Safe unregistered — no
  `FLAG_PERSIST`, so it is never codec-serialised — but `/mc colony chunkstatus` prints it as a bare
  record rather than a name.
* **Domum Ornamentum's 26.2 rewrite lost quad erasure**, which is what made empty rack slots render as
  `minecraft:missingno`. Fixed on our side by giving empty slots a transparent sprite. **Worth
  reporting upstream**; nobody has.
* **The client very nearly runs in this container.** `xvfb-run gradle runClient --offline` boots to the
  resource-reload stage under mesa software GL — `xvfb-run` and `libGLX_mesa` are present and the assets
  are already in the loom cache. It does not reach a world: quickplay arguments arrive at the JVM, but
  `onResourceLoadFinished` never fires under llvmpipe. That is closer to working than
  [`PORT-STATUS.md`](../PORT-STATUS.md) claims, and finishing it would let the GUI be checked by eye
  instead of by arithmetic — which is currently the single largest gap in how anything here is verified.
