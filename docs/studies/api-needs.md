# What a MineColonies control API needs to contain

Design study, not an implementation. Date: 2026-08-24. Stage one of a multi-stage design: this
document answers one question, **what does a caller actually need**, and nothing else. Mapping these
needs onto anything that exists in this repository is a later stage and is deliberately absent here.
Nothing below is a claim about the current code.

The single source of requirements is [`progression.md`](progression.md) in this directory — the
walkthrough of what it takes to play MineColonies from nothing to a finished colony. Every entry in
this catalogue is justified by a moment in that document, and the moment is cited. An entry that
could not be tied to a moment was not written down.

Citations of the form §4.2 refer to sections of `progression.md`.

---

## 0. How to read this catalogue

**Handles and calls.** The caller holds a handle to a thing — a colony, a building, a citizen, a
build order, a request — and asks it a question or tells it to do something. One call, one answer.
There is no session, no transaction, no cursor. A handle is obtained from a root service or from
another handle.

The root services, which need no handle:

| Service | What it is for |
|---|---|
| `Api` | version, caller identity, event subscription |
| `Colonies` | find, list, found and delete colonies; server configuration |
| `Sites` | terrain and placement questions about the world, including before any colony exists |
| `Catalog` | static reference data: building types, jobs, research nodes, food tiers |

`Sites` is named that way rather than `World` because it answers questions about *places*, not about
the Minecraft world object.

**Push is events only.** Nothing streams. No tick feed, no periodic dump, no snapshot or delta
layer. An event fires when something discrete has happened. Subscription is:

```java
Subscription Api.events().subscribe(Class<E> type, ColonyId scope, Consumer<E> listener);
Subscription Api.events().subscribeAll(Class<E> type, Consumer<E> listener);
void Subscription.close();
```

Every event record carries three fields before the ones listed in its block table: `ColonyId colony`,
`long gameTime`, `int day`. Those three are never repeated in the payload column.

**Tiers.** Each call and each event is marked:

* **Tier 1** — needed to drive a colony from nothing to a working warehouse and courier, i.e. through
  §2 steps 0–7.
* **Tier 2** — needed to finish the game as §1.5 defines finishing: research, military, the long
  economy.
* **Tier 3** — convenience. Everything still works without it, but a caller has to do more work or
  fly blinder.

**The "when it fails" column** lists only failures specific to that call. The failures that apply to
every call — stale handle, missing colony, permission, thread — are stated once in section 4 and are
never repeated. A dash means the call has no specific failure of its own.

**Refusal is not failure.** A command that the game's rules do not allow — building above the
builder's hut level, starting research without the items — returns a `CommandOutcome` that says it
was refused and why. It does not throw. Only the section 4 conditions throw.

---

## 1. The caller

**The automated agent.** A program that plays a colony: it decides where the town hall goes, places
the builder's hut, keeps the builder supplied, hires citizens into professions, notices that everyone
is starving, and works its way to a warehouse, a university and a defended town. It has no screen. It
cannot read a GUI, cannot see a red gear over a citizen's head, cannot look at the Required Resources
tab, and cannot judge from the outside whether the ground is flat. Everything a player learns by
looking must be a call that returns a value. The agent's characteristic loop is: ask what is blocking
progress, act on the answer, wait for an event that says the action landed. It acts in bursts, not
continuously — which is why polling loops are the wrong shape and events are the right one.

The agent is also the caller that makes the hardest demands on completeness. §7.2 lists nine separate
reasons a worker might be standing still, and a human can distinguish them by looking at the sky, the
hut GUI, the citizen's inventory and the chat log. An agent can distinguish them only if the API
names all nine. §7.3, §7.4 and §7.5 are three more lists of the same kind. Those four lists are the
backbone of this catalogue: an API that cannot answer "why is nothing happening" cannot drive a
colony, however many other calls it has.

**The human at a server console.** An operator or a developer with a text prompt and no client. They
ask questions in bursts — "what colonies are on this server", "why has this build order not moved in
an hour", "is this colony starving", "what is blocking research" — and occasionally intervene: unpause
a hut, cancel a work order, reset the request system, delete a colony that was founded in a bad spot
(§7.1). They need the same information as the agent but in aggregate form, and they need it to be
cheap to ask for. Where the agent wants `citizen.problems()` for a specific citizen, the operator
wants `colony.problems()` for the whole colony, sorted worst first. Both must exist.

**Both callers are server-side and both are trusted only as far as their permissions go.** Single
player and multiplayer are the same surface: in single player the caller is the one player and usually
owns the colony; in multiplayer the caller may be an operator acting on someone else's colony, an
automation account with limited rank, or a colony member with no authority at all. Nothing in the
catalogue assumes ownership, and every command states what it needs (section 4, section 2.13).

A third caller is explicitly *not* served: anything running on a client. See section 5.

---

## 2. The blocks

The domain is carved into fourteen blocks along functional joints. The carving follows the shape of
`progression.md` itself: the guide is organised by what a player is trying to achieve at each stage —
get something built, get someone fed, get someone hired, get materials delivered — and those are the
joints that matter to a caller. Two blocks (Work & Idleness, Diagnostics) exist because the guide's
troubleshooting sections are a distinct kind of question: not "what is the state of X" but "why is
nothing happening", which cuts across every other block.

Each block has the same six parts: Purpose, Questions it answers, Pull, Push, Worked example, Tier.

---

### 2.1. Colony

**Purpose.** Find, create, identify and configure a colony as a whole.

**Questions it answers.**

* Which colonies exist on this server, and which one is near me?
* May I found a colony here, or is another colony too close?
* How old is this colony, how many citizens does it have, and is it allowed to grow?
* Is it night, is it raining, is the daylight cycle even on?
* Why are no new citizens arriving?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `Colonies.list()` | `List<ColonySummary>` | Every colony the server knows, in every dimension. The operator's first call. | — | 1 |
| `Colonies.byId(ColonyId id)` | `ColonyHandle` | A handle for further calls. | — | 1 |
| `Colonies.nearest(WorldPos pos, int maxBlocks)` | `List<ColonyDistance>` | Colonies within `maxBlocks` of a point, nearest first; empty if none. Answers "whose ground am I standing on" before founding (§2 step 2, §7.1). | `maxBlocks` out of range | 1 |
| `Colonies.foundationCheck(WorldPos townHallBlock)` | `FoundationCheck` | Whether a colony may be founded on this town hall block, and if not, why: no block, too close to another colony (`minColonyDistance`, default 8 chunks, §1.1), wrong dimension. The one irreversible step in the game (§2 step 2) gets a check before it is taken. | — | 1 |
| `Colonies.found(WorldPos townHallBlock, String name, PlayerRef owner)` | `FoundResult` | Performs "right-click the block, name it, Found Colony" (§2 step 2). | refused if `foundationCheck` would refuse | 1 |
| `colony.identity()` | `ColonyIdentity` | Name, owner, dimension, centre block, centre chunk, founding time, build style. | — | 1 |
| `colony.progress()` | `ColonyProgress` | Population by category, building counts, town hall level, highest builder's hut level, warehouse and courier counts, university level, research finished. The single "where am I" call. | — | 1 |
| `colony.clock()` | `ColonyClock` | Day number, time of day, day phase, rain, snow, whether the daylight cycle is on. §7.2 cause 1 (no work at night or in rain) and §7.4 (the farmer needs the daylight cycle) both need this. | — | 1 |
| `colony.settings()` | `ColonySettings` | Town hall switches, including whether new citizens may spawn — §7.5 names this as a cause of "the colony is not growing". | — | 1 |
| `colony.setNewCitizensEnabled(boolean on)` | `CommandOutcome` | Turns citizen spawning back on. | — | 1 |
| `colony.capacity()` | `ColonyCapacity` | Beds total and free, current citizen count, current cap and what sets it — beds, research tier, config ceiling (§1.4, §7.5). | — | 1 |
| `colony.townHall()` | `BuildingId` | The town hall, which every colony has. Root of §3.4's research gates. | — | 1 |
| `colony.style()` | `StyleId` | The build style in force; the supply camp footprint and every schematic depend on it (§2 step 1). | — | 2 |
| `colony.setStyle(StyleId style)` | `CommandOutcome` | Changes the default style for future builds. | unknown style | 2 |
| `colony.rename(String name)` | `CommandOutcome` | — | name empty or too long | 3 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `ColonyFoundedEvent` | A colony is created. | `String name`, `PlayerRef owner`, `BlockPos center` | 1 |
| `ColonyDeletedEvent` | A colony is removed, by command or by losing its town hall. | `String name`, `String reason` | 2 |
| `DayStartedEvent` | The colony's day counter advances. Cheap heartbeat for daily planning: the farmer acts once per field per day (§8.1). | `int newDay` | 2 |
| `NightFallEvent` | Night begins in the colony's dimension. Raids start only at nightfall (§6.1) and work stops at night (§7.2). | `boolean raidPossibleTonight` | 2 |
| `PopulationCapReachedEvent` | A citizen would have spawned and did not, because the cap was reached. | `int cap`, `CapSource capSource` | 2 |
| `ColonySettingChangedEvent` | A town hall setting changes, by any actor. | `String setting`, `boolean newValue` | 3 |

**Worked example — found a colony on a scouted site (§2 step 2).**

1. `Sites.terrain(candidate, 128)` → check `largestFlatSquareSide` and `flatnessScore`; §2 step 2 asks
   for a large, reasonably flat area, and §7.1 mistake 1 is founding in a valley.
2. `Colonies.nearest(candidate, 512)` → confirm nothing is close.
3. `Colonies.foundationCheck(candidate)` → must return `allowed == true`; if it names
   `TOO_CLOSE_TO_COLONY`, read `nearestColonyDistanceChunks` against `minimumDistanceChunks` and move.
4. `Sites.placeHutBlock(BuildingType.TOWN_HALL, candidate, facing, style, 1)` — the block from the
   supply camp (§2 step 1).
5. `Colonies.found(candidate, "Firstlight", owner)` → `FoundResult.id`.
6. Subscribe to `ColonyFoundedEvent` beforehand if the caller wants confirmation rather than the
   return value.

**Tier.** Tier 1: 12 calls, 1 event. Tier 2: 2 calls, 4 events. Tier 3: 1 call, 1 event. Founding and
identifying a colony is the first thing any caller does, so the block is almost entirely Tier 1.

---

### 2.2. Construction & Build Orders

**Purpose.** Turn a placed hut block into a finished building, and see why that is not happening.

**Questions it answers.**

* I placed the hut and nothing happened — what did I forget?
* Can this building be upgraded at all right now, or is the builder's hut too low?
* What is the builder waiting for?
* How long is the build queue, and which order is at the front?
* Can I cancel this and come back to it?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `building.buildEligibility(int targetLevel)` | `BuildEligibility` | Whether a builder would accept an order for this building at this level, and what blocks it: builder's hut level (§0, §3.1 — the real limiter in the game), no builder hired, already at max level, type not unlocked by research, an order already open, outside the claim. Must be asked before every order. | `targetLevel` below 1 or above the type's max | 1 |
| `colony.maxBuildableLevel()` | `int` | The highest level any building can currently reach, derived from the builder's huts that exist. §3.1: "in order for the Builder to upgrade any building, the Builder's Hut must be upgraded first". | — | 1 |
| `building.requestBuild()` | `BuildOrderResult` | Presses Build Building. §7.3 names "Build Building was never pressed" as the first cause of "the builder is not building" — placing the block does nothing on its own. | refused if `buildEligibility` refuses | 1 |
| `building.requestUpgrade(int targetLevel)` | `BuildOrderResult` | Orders an upgrade to the given level. | as above | 1 |
| `building.requestRepair()` | `BuildOrderResult` | Orders a repair — the escalation step in §7.2's fix list. | building not built | 2 |
| `building.requestRemove()` | `BuildOrderResult` | Orders the building torn down. | — | 3 |
| `colony.buildOrders()` | `List<BuildOrderSummary>` | The whole queue, in priority order. §4.5 names "one builder — a work-order queue" as the day-60 bottleneck; the caller cannot see it otherwise. | — | 1 |
| `order.status()` | `BuildOrderStatus` | State, percent complete, assigned builder, current stage, stall reason. | — | 1 |
| `order.missingResources()` | `List<ResourceNeed>` | The Required Resources list (§4.3), typed: for each item, how many are needed, how many the builder has on site, how many the colony holds, and the resulting red / green / black status. | — | 1 |
| `order.cancel()` | `CommandOutcome` | Cancels without deleting progress — §7.3: cancelling and re-issuing resumes where it stopped. | order already complete | 1 |
| `order.delete()` | `CommandOutcome` | Deletes the order, which restarts the build from scratch next time (§7.3). | — | 2 |
| `order.setPriority(int priority)` | `CommandOutcome` | Moves an order in the queue. | priority out of range | 2 |
| `order.assignTo(CitizenId builder)` | `CommandOutcome` | Pins an order to a specific builder — the answer to §4.5's "3–5 builders with level-5 huts". | citizen is not a builder; builder's hut level too low | 2 |
| `colony.builders()` | `List<BuilderStatus>` | Every builder, their hut level, their current order and whether they are free. A builder will not take a second order while one is open (§4.3). | — | 1 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `BuildOrderCreatedEvent` | An order enters the queue, from any actor. | `BuildOrderId order`, `BuildingId building`, `WorkOrderKind kind`, `int targetLevel` | 1 |
| `BuildOrderClaimedEvent` | A builder takes the order and starts walking to it. | `BuildOrderId order`, `CitizenId builder` | 1 |
| `BuildOrderStalledEvent` | An in-progress order has been unable to advance for the stall grace period, for a stated reason. Not a progress feed: it fires once when the order enters the stalled state. | `BuildOrderId order`, `String reason`, `List<ResourceNeed> missing` | 1 |
| `BuildOrderResumedEvent` | A stalled order advances again. | `BuildOrderId order` | 1 |
| `BuildOrderCompletedEvent` | The structure is finished and the building's level has changed. | `BuildOrderId order`, `BuildingId building`, `int newLevel` | 1 |
| `BuildOrderCancelledEvent` | An order is cancelled or deleted. | `BuildOrderId order`, `boolean deleted` | 2 |
| `BuildOrderRejectedEvent` | An order was refused on creation, including because the target level exceeds the builder's hut level. | `BuildingId building`, `int targetLevel`, `EligibilityBlock block` | 1 |

**Worked example — get the Builder's Hut to level 1 (§2 step 3).**

1. `Sites.checkHutSite(BuildingType.BUILDER, pos, facing, style, 1)` → `placeable == true`; §2 step 3
   also advises the centre of the intended town, so the caller picks `pos` near the town hall.
2. `Sites.placeHutBlock(BuildingType.BUILDER, pos, facing, style, 1)`.
3. `colony.buildingAt(pos)` → the new `BuildingHandle`.
4. `building.workers()` (2.4) → a builder is assigned automatically; if the list is empty,
   `building.hire(citizenId)`.
5. `building.buildEligibility(1)` → a builder always upgrades their own hut, so this is allowed even
   at hut level 0 (§0).
6. `building.requestBuild()` → `BuildOrderResult.order`.
7. Subscribe `BuildOrderStalledEvent`; on each firing read `missing` and deposit the named items with
   `warehouse.deposit(...)` or, before a warehouse exists, `building.deposit(...)` (2.7).
8. Wait for `BuildOrderCompletedEvent` with `newLevel == 1`. That is §2 step 3's exit condition.

**Tier.** Tier 1: 9 calls, 5 events. Tier 2: 4 calls, 1 event. Tier 3: 1 call, 0 events. Construction
is the spine of the early game and is nearly all Tier 1.

---

### 2.3. Buildings & Levels

**Purpose.** Enumerate buildings, read what a level buys, and set the per-building switches.

**Questions it answers.**

* What have I built, at what level, and where?
* What will the next level cost me, and can I afford it?
* Which building types exist, and which are still locked behind research?
* Why will this worker not pick up the diamond pickaxe I gave them?
* Is this hut paused?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `colony.buildings()` | `List<BuildingSummary>` | Everything built or under construction. | — | 1 |
| `colony.buildingsOfType(BuildingType type)` | `List<BuildingSummary>` | Residences, guard towers, mines and farms can exist in any number (§1.2), so type lookup returns a list, never one. | — | 1 |
| `colony.building(BuildingId id)` | `BuildingHandle` | — | — | 1 |
| `colony.buildingAt(WorldPos pos)` | `List<BuildingSummary>` | The building whose hut block is at this position; empty if none. | — | 1 |
| `building.info()` | `BuildingInfo` | Type, custom name, level, max level, position, facing, style, paused flag, pickup priority, workers, worker slots, container positions. | — | 1 |
| `building.upgradeCost(int targetLevel)` | `List<ResourceNeed>` | What the next level needs, before ordering it. Answers "can I afford to start this hut?". | target level out of range | 1 |
| `building.limits()` | `BuildingLimits` | The maximum tool tier and enchantment level this hut level allows (§4.2, §10.2). §4.2 states the consequence outright: a diamond pickaxe in a level-1 hut is not picked up, and the worker sits in "waiting for tool". | — | 1 |
| `building.isPaused()` | `boolean` | §7.2 cause 4: huts have a pause button and it is easy to forget. | — | 1 |
| `building.setPaused(boolean paused)` | `CommandOutcome` | Pause and unpause — §7.2's escalating fix list starts here. | — | 1 |
| `building.capacity(CapacityKind kind)` | `BuildingCapacity` | Total and used slots of one kind: beds in a residence, courier slots in a warehouse, researcher slots in a university, guard slots in a tower, fields in a farm, towers in a barracks (§3.3, §10.1). | kind not applicable to this type | 1 |
| `building.setPickupPriority(int priority)` | `CommandOutcome` | 0–10, affects collection only; warehouse deliveries are always high priority (§4.4). | out of 0–10 | 2 |
| `building.setCustomName(String name)` | `CommandOutcome` | — | — | 3 |
| `building.as(Class<T> view)` | `T` | A typed view of a building — warehouse, residence, dining hall, farm, university, guard building, barracks — carrying the calls that only apply to that type. | building is not of that type | 1 |
| `Catalog.buildingTypes()` | `List<BuildingTypeInfo>` | Every building type the installed version has, with its maximum level, whether it is free-standing, how many one colony may have, and which research unlocks it. §1.2 counts 51 types with version-dependent exceptions; the caller must not carry its own list. | — | 2 |
| `colony.buildingTypeStatus()` | `List<BuildingTypeStatus>` | Per type: unlocked, how many built, highest level reached. Directly serves finish-line item 5, "every building type built at least once at max level" (§1.5). | — | 2 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `BuildingConstructedEvent` | A building reaches level 1 from nothing. | `BuildingId building`, `BuildingType type` | 1 |
| `BuildingUpgradedEvent` | A building's level increases. | `BuildingId building`, `BuildingType type`, `int newLevel`, `int oldLevel` | 1 |
| `BuildingRemovedEvent` | A hut block is destroyed or the building is torn down. | `BuildingId building`, `BuildingType type`, `String cause` | 1 |
| `BuildingTypeUnlockedEvent` | Research completes that makes a new type buildable (§5.3). | `BuildingType type`, `ResearchId research` | 2 |
| `BuildingPauseChangedEvent` | A hut is paused or unpaused by any actor. | `BuildingId building`, `boolean paused` | 3 |

**Worked example — decide whether to upgrade the Builder's Hut before the Warehouse (§2 step 7, §3.3).**

1. `colony.buildingsOfType(BuildingType.BUILDER)` → the hut, currently level 1.
2. `warehouseType = BuildingType.WAREHOUSE`; `colony.buildingTypeStatus()` → warehouse not built yet.
3. `Catalog.buildingTypes()` → warehouse max level 5, unlocked without research.
4. `builderHut.buildEligibility(2)` → allowed, because a builder always upgrades their own hut.
5. `builderHut.upgradeCost(2)` and `warehouse.upgradeCost(1)` → two resource lists.
6. `colony.canAfford(list)` for each (2.7) → pick the affordable one.
7. §3.3 says a warehouse gives 2 couriers per level and a builder's hut raises the ceiling on
   everything; a caller that wants §2 step 7's exit condition prioritises the warehouse, then returns
   to the hut.

**Tier.** Tier 1: 11 calls, 3 events. Tier 2: 3 calls, 1 event. Tier 3: 1 call, 1 event.

---

### 2.4. Citizens & Professions

**Purpose.** See who lives in the colony, put them into jobs and homes, and take them out again.

**Questions it answers.**

* Who is unemployed, and what could they do?
* Which professions are unstaffed, and therefore which requests will land on me?
* Who has no home, and for how long?
* This worker is broken — can I fire and rehire them?
* Can this colony have children at all?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `colony.citizens()` | `List<CitizenSummary>` | Everyone alive, including children and visitors flagged as such. | — | 1 |
| `colony.citizen(CitizenId id)` | `CitizenHandle` | — | — | 1 |
| `citizen.info()` | `CitizenInfo` | Name, gender, child flag, job, workplace, home, position, job level, experience, skills. Gender matters: §7.5 states children are born only if the colony has at least one man and one woman. | — | 1 |
| `citizen.needs()` | `CitizenNeeds` | Saturation, required food tier, health, happiness, home and bed flags, sickness, injury, mourning with days left, home-to-work distance, days without a home. §7.1 mistake 4 notes the commute complaint starts above 100 blocks; §8.1 gives the homelessness complaint at two weeks and the sleep complaint at three nights. | — | 1 |
| `colony.unemployed()` | `List<CitizenSummary>` | Citizens with no job, the pool every hire draws from. | — | 1 |
| `building.workers()` | `List<CitizenId>` | Who is assigned to this building. | — | 1 |
| `building.hire(CitizenId citizen)` | `CommandOutcome` | Assigns a citizen to a job slot. | no free slot; citizen is a child; citizen already employed | 1 |
| `building.fire(CitizenId citizen)` | `CommandOutcome` | Removes them. §7.2's escalation list includes "fire and rehire". | citizen not assigned there | 1 |
| `building.recallWorkers()` | `CommandOutcome` | Teleports the assigned workers back to their hut — the first escalation in §7.2. | building has no workers | 1 |
| `citizen.assignHome(BuildingId residence)` | `CommandOutcome` | Gives a citizen a bed. §7.5: no free beds is the first cause of a colony that will not grow. | no free bed; building is not a residence or tavern | 1 |
| `citizen.clearHome()` | `CommandOutcome` | — | — | 3 |
| `colony.professionCoverage()` | `List<ProfessionCoverage>` | Per profession: buildings built, slots, staffed. §4.4 states the rule that makes this Tier 2 and not Tier 3 — the more distinct professions are staffed, the fewer requests reach the player. | — | 2 |
| `Catalog.jobTypes()` | `List<JobType>` | Every profession and the building type that provides it. | — | 2 |
| `citizen.skills()` | `List<SkillLevel>` | Per-skill levels, which govern work speed and, for researchers, offline catch-up (§5.1). | — | 2 |
| `colony.visitors()` | `List<VisitorSummary>` | Tavern visitors and the items they can be recruited for (§2 step 4). | no tavern | 2 |
| `colony.recruitVisitor(int visitorId, InventoryRef payment)` | `CommandOutcome` | Turns a visitor into a citizen. | visitor gone; payment missing; population capped | 2 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `CitizenSpawnedEvent` | A new citizen appears, by arrival or by birth. | `CitizenId citizen`, `String name`, `boolean bornInColony` | 1 |
| `CitizenDiedEvent` | A citizen dies. | `CitizenId citizen`, `String name`, `String cause`, `boolean guard`, `BlockPos where` | 1 |
| `CitizenHiredEvent` | A citizen is assigned to a job. | `CitizenId citizen`, `BuildingId building`, `JobType job` | 1 |
| `CitizenFiredEvent` | A citizen loses a job, by command or because the building was removed. | `CitizenId citizen`, `BuildingId building`, `String reason` | 1 |
| `CitizenHomeChangedEvent` | A citizen gains or loses a home. | `CitizenId citizen`, `BuildingId home`, `boolean assigned` | 2 |
| `CitizenSickEvent` | A citizen falls ill or is cured. | `CitizenId citizen`, `boolean sick`, `String illness` | 2 |
| `CitizenComplaintEvent` | A citizen raises one of the timed complaints: homeless after two weeks, no bed after three nights, commute over the distance threshold, nothing to do at work (§8.1, §7.1, §10.5). | `CitizenId citizen`, `ComplaintKind kind`, `int daysStanding` | 2 |
| `MourningStartedEvent` | A death puts a family into mourning, which stops their work the next day (§6.1, §7.2 cause 2). | `List<CitizenId> mourners`, `CitizenId deceased`, `int days` | 2 |

**Worked example — house the first four citizens (§2 step 4).**

1. `colony.capacity()` → `beds == 0`, `citizens == 4`; §2 step 4's exit condition is more free beds
   than citizens.
2. `Catalog.buildingTypes()` → the Tavern gives 4 beds at once and caps at level 3; a Residence gives
   one bed per level and is unlimited (§10.1).
3. Build a Tavern via 2.2, or two Residences if the tavern's cost is out of reach.
4. `colony.citizens()` → for each with `needs().hasHome == false`, `citizen.assignHome(residenceId)`.
5. `colony.capacity()` again → `bedsFree > 0`; new citizens will now arrive, provided
   `colony.settings().newCitizensEnabled` (2.1).
6. Subscribe `CitizenSpawnedEvent` to know when the fifth citizen has come.

**Tier.** Tier 1: 10 calls, 4 events. Tier 2: 5 calls, 4 events. Tier 3: 1 call, 0 events.

---

### 2.5. Work & Idleness

**Purpose.** Answer "why is this worker standing still" with a named cause, for one citizen, one
building, or a whole colony.

§7.2 says this in as many words: *"This list is the specification for a `problems()` accessor on a
citizen handle: nine causes, each observable server-side, none of them currently visible from
outside."* This block is the direct expression of that sentence, and it is the block the whole
catalogue exists for.

**Questions it answers.**

* Why is my builder idle?
* Why is the miner refusing to go down?
* Is this citizen actually broken, or is it just raining?
* Which of my buildings has nobody in it?
* What was the last useful thing this citizen did, and when?

**The nine causes of §7.2, as values.** Each is a `ProblemKind`; the check order is the order §7.2
gives, and the API returns all that apply, not just the first.

| §7.2 cause | `ProblemKind` | What has to be readable for it to be reported |
|---|---|---|
| 1. Night, rain or snow | `NIGHT_OR_WEATHER` | Day phase and weather (2.1), and whether the rain research is done (2.9) |
| 2. Mourning | `MOURNING` | Days of mourning left on the citizen |
| 3. A raid is running | `RAID_IN_PROGRESS` | Raid state (2.10) |
| 4. The hut is paused | `BUILDING_PAUSED` | The hut's pause flag (2.3) |
| 5. Hunger | `HUNGRY` | Saturation at or near zero (2.8) |
| 6. An open request | `OPEN_REQUEST` | The citizen's open requests (2.6) |
| 7. Wrong tool tier | `TOOL_TIER_TOO_HIGH` | Hut level limits against the tool offered (2.3, §4.2) |
| 8. Full inventory | `INVENTORY_FULL` | Free slots in the citizen's inventory |
| 9. Low hut level or low experience | `LOW_LEVEL` | Hut level and job level; reported as `INFO`, because §7.2 says this stalling is normal |

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `citizen.workStatus()` | `WorkStatus` | State, current activity in words, position, the game time of the last productive action, and the list of problems. One call answers "why is my builder idle". | — | 1 |
| `citizen.problems()` | `List<WorkProblem>` | The problem list alone, for callers that want nothing else. Each entry carries a kind, a severity, a message, the item or building it concerns, and a suggested fix. | — | 1 |
| `building.problems()` | `List<WorkProblem>` | Problems that belong to the building rather than a person: paused, no worker hired, no field assigned, no seed in the scarecrow, mine hut below its depth limit (§7.4, §7.6). | — | 1 |
| `building.isBlocked()` | `boolean` | Cheap yes/no for the same question, for a caller sweeping many buildings. | — | 2 |
| `colony.idleCitizens()` | `List<CitizenSummary>` | Everyone whose work state is idle, so an operator does not have to walk the whole citizen list. | — | 1 |
| `citizen.lastProductiveTime()` | `long` | Game time of the last completed work action. "Standing still" is only meaningful against a clock. | — | 2 |
| `Catalog.problemKinds()` | `List<ProblemKindInfo>` | Every problem kind with its human-readable description and typical fix, so an operator's console can print something useful without hard-coding text. | — | 3 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `CitizenBlockedEvent` | A citizen's blocking problem set goes from empty to non-empty and stays non-empty past the grace period. Fires once per transition, not repeatedly. | `CitizenId citizen`, `List<WorkProblem> problems` | 1 |
| `CitizenUnblockedEvent` | The same citizen's blocking problem set becomes empty again. | `CitizenId citizen` | 1 |
| `BuildingBlockedEvent` | The same transition at building level. | `BuildingId building`, `List<WorkProblem> problems` | 2 |
| `BuildingUnblockedEvent` | The building's blocking problems clear. | `BuildingId building` | 2 |

The grace period exists so that a worker walking across the town does not generate an event. It is a
server-side constant, and its value is an open question (section 7).

**Worked example — the builder has not moved (§7.2, §7.3).**

1. `citizen.workStatus()` on the builder → `state == IDLE`, `problems` non-empty.
2. If `NIGHT_OR_WEATHER` — read `colony.clock()`; wait, or check in 2.9 whether the rain research is
   available.
3. If `BUILDING_PAUSED` — `building.setPaused(false)`.
4. If `OPEN_REQUEST` — `citizen.openRequests()` (2.6), then supply the item.
5. If `TOOL_TIER_TOO_HIGH` — `building.limits()` gives the hut's ceiling; remove the over-tier tool and
   supply one at or below the ceiling (§4.2).
6. If none of those and the builder has an order — `order.status()` (2.2) → `stallReason`, and
   `order.missingResources()` for the red entries (§4.3).
7. If the problem list is empty and the builder still is not building, §7.3's first cause applies:
   `colony.buildOrders()` is empty because Build Building was never pressed.

**Tier.** Tier 1: 4 calls, 2 events. Tier 2: 2 calls, 2 events. Tier 3: 1 call, 0 events.

---

### 2.6. Requests & Logistics

**Purpose.** See what the colony is asking for, who is expected to satisfy it, and take over when
nobody can.

§2 step 7 is the hinge of the whole game: *"Until there is a Warehouse and at least one Courier, every
request is fulfilled by hand."* Before that point the caller **is** the logistics system, and it needs
to see every request. After that point it needs to see only the ones that escaped.

**Questions it answers.**

* What is the colony asking me for right now?
* Which requests are stuck because nobody can craft the item?
* How many couriers do I have, and how much can each carry per trip?
* Why is the material in the warehouse not arriving at the hut?
* Is the request system itself wedged?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `colony.requests(RequestFilter filter)` | `List<RequestSummary>` | The Clipboard (§4.4), typed. The filter can restrict to a state, a resolver kind, a building, or to important requests only — §4.4 notes "Show Important Requests Only" hides perpetual ones such as ore for the smelter. | — | 1 |
| `colony.playerRequests()` | `List<RequestSummary>` | Only the requests that landed on the player — §4.4 step 5, the red gear over the citizen's head. The most important single list in the early game. | — | 1 |
| `request.detail()` | `RequestDetail` | Item, count, requester, state, resolver, acceptable alternatives, parent and child requests. §4.4 step 4 makes requests recursive — oak stairs to planks to logs — so the tree must be visible. | — | 1 |
| `citizen.openRequests()` | `List<RequestSummary>` | What this one worker is waiting for; §7.2 cause 6. | — | 1 |
| `building.openRequests()` | `List<RequestSummary>` | The same for a building. | — | 1 |
| `request.fulfill(InventoryRef source)` | `CommandOutcome` | Hands over the requested items from a player's inventory or a named container. This is how the caller does §2 step 7's "fulfilled by hand". | source lacks the items; request already resolved | 1 |
| `request.cancel()` | `CommandOutcome` | Withdraws a request. | request not cancellable in its current state | 2 |
| `colony.couriers()` | `List<CourierReport>` | Per warehouse: courier slots by level (2 per level, max 10, §3.3), couriers assigned, stacks per trip by courier hut level (2/3/4/5/unlimited), and the courier huts themselves. §2 step 7 requires each courier to have their own hut, and §7.6 warns a courier only sees their own warehouse — so this is per warehouse, never colony-wide. | — | 1 |
| `colony.resetRequestSystem()` | `CommandOutcome` | The last resort in §7.2's escalation list. Destructive: open requests are dropped. | — | 2 |
| `colony.postboxes()` | `List<BlockPos>` | Where the colony's postboxes are (§4.4). | — | 3 |
| `colony.stashes()` | `List<BlockPos>` | Where the stashes are — the reverse postbox couriers collect from (§4.4). | — | 3 |
| `postbox.order(ItemAmount item)` | `CommandOutcome` | Requests an item out of the colony to a postbox. | item not obtainable | 3 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `RequestOpenedEvent` | A citizen raises a request that the colony did not satisfy from stock. | `RequestId request`, `ItemSpec item`, `int count`, `CitizenId requester` | 1 |
| `RequestEscalatedToPlayerEvent` | A request reaches §4.4 step 5 — nobody in the colony can resolve it and it is now the player's. | `RequestId request`, `ItemSpec item`, `int count`, `CitizenId requester`, `BuildingId building` | 1 |
| `RequestResolvedEvent` | A request is satisfied, by stock, courier, crafter or player. | `RequestId request`, `RequestResolverKind resolvedBy` | 1 |
| `RequestCancelledEvent` | A request is cancelled or fails. | `RequestId request`, `String reason` | 2 |
| `CourierAssignedEvent` | A courier is hired into a warehouse or loses that assignment. | `CitizenId courier`, `BuildingId warehouse`, `boolean assigned` | 3 |

**Worked example — reach §2 step 7's exit condition, requests satisfied without the player.**

1. `colony.playerRequests()` → a long list; every entry is work the caller is doing by hand.
2. Build a Warehouse (2.2, 2.3) and wait for `BuildingConstructedEvent`.
3. `warehouse.capacity(CapacityKind.COURIER_SLOTS)` → 2 free slots at level 1 (§3.3).
4. Build a Courier's Hut for each courier — §2 step 7: each courier needs their own hut.
5. `colony.unemployed()` (2.4) → `courierHut.hire(citizenId)`.
6. `colony.couriers()` → `couriersAssigned >= 1`, `stacksPerTrip == 2` at hut level 1.
7. `warehouse.deposit(playerInventory, materials)` (2.7).
8. `colony.playerRequests()` again after a day: the entries that remain are the ones no profession
   can craft, which §4.4 says is a staffing problem, not a logistics one — go to
   `colony.professionCoverage()` (2.4).

**Tier.** Tier 1: 7 calls, 3 events. Tier 2: 2 calls, 1 event. Tier 3: 3 calls, 1 event.

---

### 2.7. Storage & Resources

**Purpose.** Know what the colony holds, get materials into it, and decide what is affordable.

**Questions it answers.**

* Do I have the materials for this build, or do I need to go and dig?
* Where in the colony is this item?
* Who could craft this if I do not have it?
* How do I put a stack of planks into the colony from outside?
* What should the colony always keep in stock?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `colony.stock()` | `List<StockEntry>` | Everything the colony can reach, per item, split by where it is: warehouses, other buildings, citizen inventories. | — | 1 |
| `colony.countOf(ItemSpec item)` | `int` | One number for one item, for callers that do not want the whole list. | — | 1 |
| `colony.canAfford(List<ResourceNeed> needs)` | `Affordability` | Whether the colony holds the materials for a build or an upgrade, with a per-line breakdown of what is missing. The direct answer to "can I afford to start this hut?" (§4.3). | — | 1 |
| `building.contents()` | `List<ItemAmount>` | What is in this building's hut block and racks — the first two places §4.4 says a citizen looks. | — | 1 |
| `building.deposit(InventoryRef source, List<ItemAmount> items)` | `DepositResult` | Puts items into a building, reporting what was accepted and what was refused for lack of space. Before a warehouse exists this is how the builder gets supplied (§4.3, §4.5 day 3–10). | source lacks the items; building has no space | 1 |
| `building.withdraw(List<ItemAmount> items, InventoryRef target)` | `DepositResult` | The reverse. §7.2's escalation list includes clearing a worker's and a hut's inventory and giving back only what was requested. | items not present | 2 |
| `colony.craftableBy(ItemSpec item)` | `List<CraftingOption>` | Which profession could make this, in which building, whether that building is staffed, and what the recipe needs. §4.4 step 4 is exactly this lookup, and §4.4's closing rule — staff more professions, get fewer player requests — cannot be acted on without it. | — | 2 |
| `building.minimumStock()` | `List<MinimumStockEntry>` | What this building keeps on hand automatically. | type has no minimum stock | 2 |
| `building.setMinimumStock(ItemSpec item, int count)` | `CommandOutcome` | §4.5 names setting Minimum Stock as the fix for the day-25 food bottleneck. Setting count to 0 removes the entry. | count negative; no free minimum-stock slot | 2 |
| `Api.inventoryOf(InventoryRef ref)` | `List<ItemAmount>` | What a player's inventory or a named container actually holds. The caller needs this because two things in the game are paid for from outside the colony: build materials handed over by hand before a warehouse exists (§4.3), and research costs, which §5.1 says must be **in the player's inventory** when the research starts. | container not loaded; caller may not read that player's inventory | 1 |
| `colony.itemLocations(ItemSpec item)` | `List<BlockPos>` | Which containers hold it. An operator's call, for when something is in the colony but not arriving. | — | 3 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `WarehouseFullEvent` | A courier cannot deposit because no rack has space. | `BuildingId warehouse`, `ItemSpec item` | 2 |
| `MinimumStockUnmetEvent` | A minimum-stock entry falls below its target and the colony cannot refill it. | `BuildingId building`, `ItemSpec item`, `int have`, `int want` | 3 |

**Worked example — supply a stalled build order (§4.3).**

1. `BuildOrderStalledEvent` arrives with `missing`.
2. For each `ResourceNeed` with `status == MISSING`: `colony.craftableBy(need.item)`.
3. Where a staffed crafter exists, do nothing — §4.4 step 4 says the request system will issue the
   order itself.
4. Where none exists, the caller supplies it: `building.deposit(playerInventory, ...)` into the
   builder's hut, or `warehouse.deposit(...)` once a warehouse and courier exist.
5. `order.missingResources()` again → every line now `HAVE_ON_SITE` or `HAVE_IN_COLONY`.
6. Wait for `BuildOrderResumedEvent`.

**Tier.** Tier 1: 6 calls, 0 events. Tier 2: 4 calls, 1 event. Tier 3: 1 call, 1 event.

---

### 2.8. Food & Happiness

**Purpose.** Keep citizens fed and content, and detect the two failure modes — starvation and the
happiness slide — before they stop the colony.

§4.1 is unambiguous about the stakes: at zero saturation a citizen **stops working**, stops gaining
levels, gets Slowness and complains. Colony-average saturation above 5 raises happiness, below 5
lowers it.

**Questions it answers.**

* Am I about to starve?
* Why is there food in the colony and citizens still hungry?
* Why is the chef cooking nothing?
* Is this farm actually farming?
* What is dragging happiness down?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `colony.food()` | `FoodReport` | Average saturation, how many citizens are below the threshold of 5 and how many are at zero, dining halls, chefs and waiters staffed, whether any fuel is enabled, stock by food tier, and how much food was consumed in the last day. The one call that answers "am I about to starve". | — | 1 |
| `citizen.saturation()` | `double` | One citizen's saturation bar (§4.1). | — | 1 |
| `citizen.foodRequirement()` | `FoodRequirement` | The minimum food tier this citizen accepts, the home level that sets it, and whether they are currently refusing food that exists. §4.1 states the requirement follows the **home** level, not the workplace, and §7.4 lists "food exists but is the wrong tier" as a cause of starvation. The mapping from home level to tier is not published (§4.1), so it must be read live and never computed by the caller. | — | 1 |
| `colony.foodStock()` | `List<FoodStock>` | Items and total saturation per tier 1/2/3 (§4.1). | — | 1 |
| `diningHall.allowedFuel()` | `List<FuelSetting>` | Which fuels the dining hall may burn. §7.4: **by default all fuel types are off and the cook can cook nothing** — non-obvious and very common. A caller that does not check this will watch a fully staffed kitchen produce nothing. | building is not a dining hall | 1 |
| `diningHall.setFuelAllowed(ItemSpec fuel, boolean allowed)` | `CommandOutcome` | Turns a fuel on. | fuel is not burnable | 1 |
| `farm.fields()` | `List<FieldInfo>` | Per field: scarecrow position, assigned farm, seed, hydration, state. §7.4 lists no field assigned, no seeds in the scarecrow and unhydrated soil as three separate causes. | building is not a farm | 2 |
| `farm.assignField(BlockPos scarecrow)` | `CommandOutcome` | Attaches a field to the farm, up to the level's field count (1 per level, §3.3). | field slots full; field belongs to another farm; outside the claim | 2 |
| `field.setSeed(ItemSpec seed)` | `CommandOutcome` | Puts the seed in the scarecrow. | seed not plantable; seed not held by the colony | 2 |
| `colony.happiness()` | `HappinessReport` | The colony average and the factor breakdown. §10.5 gives three base factors with explicit thresholds — saturation above 5, home above level 2.5, at least 2 guards per 3 citizens — plus positive and negative modifiers. | — | 2 |
| `citizen.happiness()` | `List<HappinessFactorValue>` | One citizen's factors, for finding the unhappy one. | — | 2 |
| `Catalog.foodTiers()` | `List<FoodTierInfo>` | Which items count as which tier and what saturation they give (§4.1's table), including whether an item is vanilla food and therefore carries a happiness penalty. | — | 2 |
| `colony.biomeCropAvailability()` | `List<CropAvailability>` | Which mod crops can grow in the biomes the colony can reach. §4.1: crops are biome-locked and some tier-3 dishes need ingredients from different biomes, so in a single-biome world the top food tier may be unreachable without a second colony. | — | 3 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `CitizenStarvingEvent` | A citizen's saturation reaches zero and they stop working (§4.1). | `CitizenId citizen`, `BuildingId workBuilding` | 1 |
| `CitizenFedEvent` | A previously starving citizen eats. | `CitizenId citizen`, `double saturation` | 2 |
| `ColonySaturationCrossedEvent` | The colony average crosses the happiness threshold of 5, in either direction (§4.1). | `double average`, `CrossDirection direction` | 1 |
| `FoodTierRefusedEvent` | A citizen refuses available food because it is below their required tier (§4.1, §7.4). | `CitizenId citizen`, `int requiredTier`, `int offeredTier` | 2 |
| `HappinessDroppedEvent` | The colony happiness average falls below a stated threshold, once per crossing. | `double average`, `HappinessFactor worstFactor` | 2 |

**Worked example — the colony is starving (§7.4).**

1. `colony.food()` → `citizensAtZero > 0`.
2. `anyFuelEnabled == false` → `diningHall.allowedFuel()`, then `setFuelAllowed(charcoal, true)`. §7.4
   puts this first because it is the cause nobody guesses.
3. `diningHalls == 0` → build one; §7.4: without it citizens ask the player directly.
4. `chefsStaffed == 0` → `colony.unemployed()` and `kitchen.hire(...)` (2.4).
5. `stock` shows tier-1 food only, and `citizen.foodRequirement()` returns `requiredTier == 2` for the
   citizens in level-3 residences → the food is real but refused; raise production, not quantity.
6. `farm.fields()` → any field with `state == NO_SEED` gets `field.setSeed(...)`, any with
   `NOT_HYDRATED` needs water placed by the caller.
7. `colony.clock().daylightCycleOn == false` → the farmer will not work at all (§7.4); this is a world
   setting, not a colony one, and the caller can only report it.

**Tier.** Tier 1: 6 calls, 2 events. Tier 2: 6 calls, 3 events. Tier 3: 1 call, 0 events.

---

### 2.9. Research

**Purpose.** Read the research tree, start and cancel research, and handle the choices that cannot be
undone.

Two facts from `progression.md` shape this block. First, research time is **real** time, half an hour
for a column-1 node and sixteen hours for a column-6 node (§5.2), with roughly 587 hours of research
in a full tree and a 117-hour floor even with five researchers (§8.1). A caller cannot brute-force it;
it must plan. Second, §0 reports that the mutually exclusive choices sit at the **roots** of the
branches, not at the ends: *"The irreversible choice is made in the first hour of a colony's life,
not the last, and it costs an entire branch rather than a single node. Anything planning a research
order has to decide these five before it decides anything else."*

**Questions it answers.**

* What can I research right now, and what does it cost?
* What do I have to research to unlock a Sawmill?
* Which choices will lock me out of an entire branch if I make them carelessly?
* Are all my researchers busy?
* How much real time is left on this?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `Catalog.researchNodes()` | `List<ResearchNode>` | The whole tree as data: branch, column, required university level, parents, exclusions, item cost, building requirements, what it unlocks, whether it is cancellable, base duration. §5.3's unlock table is this call. The counts are version-dependent (§1.3), so the caller must read the tree, not carry one. | — | 2 |
| `research.state(ResearchId id)` | `ResearchState` | Locked, available, in progress, finished or excluded; seconds remaining and total; whether it can be started now and, if not, the named blockers. | unknown research | 2 |
| `research.all()` | `List<ResearchState>` | The same for every node, for a caller planning an order. | — | 2 |
| `research.capacity()` | `ResearchCapacity` | University level, researcher slots (one per level, max 5), how many are running, and whether offline catch-up is active (from level 3, §3.3, §5.1). §4.5's late-game bottleneck is "always 5 in parallel". | no university | 2 |
| `research.start(ResearchId id, InventoryRef costSource)` | `CommandOutcome` | Starts a research. §5.1: the items must be in the player's inventory when it starts, so the caller must name where they come from. | items missing; no free researcher slot; university level too low; prerequisites unmet; branch excluded | 2 |
| `research.cancel(ResearchId id)` | `CommandOutcome` | Stops a running research. §5.1: cancelling does **not** refund the items, and some research is marked non-cancellable. | research not cancellable | 2 |
| `research.exclusiveChoices()` | `List<ExclusiveChoice>` | Every mutually exclusive fork: the branch, whether it has been decided, what was chosen, and per option how many nodes taking it would cost. §0 makes this the most consequential single call in the block. | — | 2 |
| `research.unlocksOf(ResearchId id)` | `List<BuildingType>` | Which buildings a node unlocks. | unknown research | 2 |
| `research.requiredFor(BuildingType type)` | `List<ResearchId>` | The inverse: what stands between the colony and a Sawmill. §5.3's table read backwards, which is the direction a caller actually needs. | type has no research gate | 2 |
| `research.inProgress()` | `List<ResearchState>` | What is running and how long is left. | — | 2 |
| `research.effects()` | `List<ResearchEffect>` | Active numeric effects and their current values — population ceiling, block-place speed, tool durability, guard damage. §5.3 ranks research by effect, and a caller cannot rank without reading the numbers. | — | 3 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `ResearchStartedEvent` | A research begins. | `ResearchId research`, `int durationSeconds` | 2 |
| `ResearchCompletedEvent` | A research finishes. | `ResearchId research`, `List<BuildingType> unlocked`, `List<ResearchEffect> effects` | 2 |
| `ResearchCancelledEvent` | A research is cancelled. | `ResearchId research` | 3 |
| `ResearcherIdleEvent` | A researcher slot becomes free. The prompt to keep five running (§4.5). | `int freeSlots`, `int totalSlots` | 2 |

**Worked example — unlock the Sawmill (§5.3, §2 step 8).**

1. `research.requiredFor(BuildingType.SAWMILL)` → the node.
2. `Catalog.researchNodes()` → its `buildingRequirements` are Forester's Hut, 3 levels total, plus any
   planks (§5.3).
3. `colony.buildingsOfType(BuildingType.FORESTER)` (2.3) → sum the levels; if under 3, build or upgrade
   through 2.2 first.
4. `research.exclusiveChoices()` → check that the branch this node sits on has not been closed by an
   earlier root choice (§0).
5. `research.capacity()` → a free researcher slot and a university at or above the node's column.
6. `research.start(node, playerInventory)` → the cost leaves the inventory and is not refundable.
7. Wait for `ResearchCompletedEvent`; `unlocked` contains the Sawmill, and `BuildingTypeUnlockedEvent`
   (2.3) fires alongside.

**Tier.** Tier 2: 10 calls, 3 events. Tier 3: 1 call, 1 event. Nothing here is Tier 1: §2 puts the
university at step 8, after the warehouse, and a colony reaches a working warehouse and courier
without a single research node.

---

### 2.10. Military & Raids

**Purpose.** Know when a raid is coming, know how strong the colony is, and act during a raid.

**Questions it answers.**

* Can a raid happen tonight?
* Am I defended enough, by the game's own standard?
* A raid started an hour ago and has not ended — where is the last raider?
* Which guards are missing equipment?
* Did that raid cost me anyone?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `colony.raidForecast()` | `RaidForecast` | Nights since the last raid against the configured minimum (10) and average (14), whether a raid is possible tonight, the horde-size and difficulty settings, and the raider type the colony's biome implies (§6.1's biome table, §6.2's config table). It does not predict horde size — §6.2 states plainly that the difficulty formula is not publicly known. | — | 2 |
| `colony.currentRaid()` | `RaidStatus` | Whether a raid is running, its type, announced direction, raiders remaining and killed, citizens lost, and whether spies are active. §6.1: during a raid citizens drop their work and run home, which is §7.2 cause 3. | — | 2 |
| `colony.defence()` | `DefenceReport` | Guards, citizens, the guards-per-citizen ratio against §10.5's "at least 2 guards per 3 citizens", guard towers, barracks and their towers, free guard slots and free beds. §6.3: every new guard needs a free Residence bed to appear at all, so beds belong in the defence report. | — | 2 |
| `colony.guards()` | `List<GuardInfo>` | Per guard: type, building, level, patrol mode and radius, and what equipment they are missing (§6.4). | — | 2 |
| `guardBuilding.setGuardType(GuardType type)` | `CommandOutcome` | Knight, ranger or druid (§6.4). | type not unlocked; no guard assigned | 2 |
| `guardBuilding.setPatrolMode(PatrolMode mode)` | `CommandOutcome` | Patrol, hold a post, follow. Guard tower patrol radius grows 80/110/140/170/200 with level (§3.3). | mode not available at this level | 3 |
| `guardBuilding.setRetreatOnLowHealth(boolean on)` | `CommandOutcome` | — | — | 3 |
| `colony.hireSpies()` | `CommandOutcome` | Makes raiders glow. §6.3: this is unlocked at Barracks 3 and is how you find the last stuck enemy and end a raid that will not end. | barracks below level 3; no raid running; cost not payable | 2 |
| `raid.remainingRaiders()` | `List<RaiderPosition>` | Where the surviving raiders are, once spies are hired. | spies not active | 3 |
| `colony.raidHistory(int maxEntries)` | `List<RaidRecord>` | Past raids with type, size, losses and outcome. §6.2 says difficulty grows with development but not how, so a caller that wants to model it has to keep its own record — this call gives it the data. | `maxEntries` out of range | 3 |
| `colony.triggerRaid(RaiderType type, int size)` | `CommandOutcome` | Starts a raid on demand. For testing defences and for the operator at a console; not part of normal play. | caller lacks admin rights; a raid is already running | 3 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `RaidWarningEvent` | A raid is announced at nightfall, with its direction (§6.1). | `RaiderType type`, `String direction`, `int estimatedSize` | 2 |
| `RaidStartedEvent` | The first raider spawns. | `RaiderType type`, `int raiders` | 2 |
| `RaidEndedEvent` | The last raider dies or the raid is otherwise over. | `int raidersKilled`, `int citizensLost`, `boolean noLosses` | 2 |
| `GuardEquipmentMissingEvent` | A guard cannot be equipped for lack of a weapon or armour piece. | `CitizenId guard`, `List<ItemSpec> missing` | 2 |
| `RaidStalledEvent` | A raid has been running past a stated duration with no raider killed — the situation §6.3 says spies exist for. | `int raidersRemaining`, `long runningTicks` | 3 |

Guard deaths are not a separate event: `CitizenDiedEvent` (2.4) carries a `guard` flag, and §6.1 notes
guards are not mourned, which the same flag explains.

**Worked example — prepare for the first raid (§6.5).**

1. `colony.raidForecast()` on day 5 → `nightsSinceLastRaid` counting up, `minimumNights == 10`.
2. `colony.defence()` → `guardTowers == 0`. §6.5 step 1: two or three guard towers around the
   perimeter is enough for the first raid.
3. Build them through 2.2, siting them at the colony edge, where §6.3 notes they also extend the claim
   (2.11).
4. `colony.capacity()` (2.1) → free beds, because §6.3 requires a free bed for a guard to appear.
5. `towerBuilding.hire(citizenId)` (2.4), then `colony.guards()` → check `missingEquipment` and supply
   swords or bows through 2.7.
6. Subscribe `RaidWarningEvent`; on firing, note the direction and stop issuing build orders, since
   §6.1 says citizens drop their work anyway.
7. After `RaidEndedEvent` with `noLosses == true`, §6.1 gives a colony-wide happiness bonus, visible
   in `colony.happiness()` (2.8).

**Tier.** Tier 2: 6 calls, 4 events. Tier 3: 5 calls, 1 event.

---

### 2.11. Territory & Claims

**Purpose.** Know which ground belongs to the colony, because building outside it is not possible and
founding too close to another colony is not allowed.

**Questions it answers.**

* Is this spot inside my colony?
* How far does my claim reach, and what would extend it?
* Whose chunk is this?
* Which colonies am I allied with?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `colony.claim()` | `ClaimInfo` | Centre chunk, current radius, claimed chunk count, the configured maximum, and which buildings contribute how much. §1.1 gives the starting radius of 4 chunks and the maximum of 20; §10.4 gives per-building contributions, with the town hall at 1/1/2/3/5 and guard towers at 2/3/3/4/5. | — | 1 |
| `Colonies.chunkOwner(ChunkRef chunk)` | `ChunkOwner` | Whether a chunk is claimed, by whom, and whether it is inside that colony's build range. Answers "may I place a hut here" before the placement check (2.12). | — | 1 |
| `colony.containsPosition(WorldPos pos)` | `boolean` | The same question for a block position. | — | 1 |
| `colony.claimAfter(BuildingId building, int level)` | `ClaimInfo` | What the claim would look like if that building reached that level. Lets a caller decide whether a guard tower at the edge is worth building for the ground alone (§6.3). | unknown building; level out of range | 3 |
| `colony.allies()` | `List<AlliedColony>` | Allied colonies and whether teleport is available — §3.4 puts teleport to allied colonies behind Town Hall 3, and §4.1 makes a second colony the only route to some tier-3 dishes in a single-biome world. | — | 3 |
| `colony.setRelation(ColonyId other, Relation relation)` | `CommandOutcome` | Sets ally, neutral or enemy. | caller is not the owner of this colony | 3 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `ClaimChangedEvent` | The set of claimed chunks changes, which happens when the town hall or a guard tower changes level (§10.4). | `int radiusChunks`, `int claimedChunks`, `BuildingId cause` | 2 |
| `RelationChangedEvent` | A relation with another colony changes. | `ColonyId other`, `Relation relation` | 3 |

**Worked example — decide where the next residence goes (§7.1 mistake 4).**

1. `colony.claim()` → `radiusChunks`, and the claimed chunk list.
2. `Colonies.chunkOwner(candidateChunk)` → claimed by this colony, `inBuildRange == true`.
3. §7.1 warns that buildings spread over a large area cost courier walking time, invite raider spawns
   between buildings, and trigger the commute complaint above 100 blocks from home to work — so the
   caller measures against existing buildings with `colony.buildings()` (2.3) rather than filling the
   claim outward.
4. `Sites.checkHutSite(...)` (2.12) → `placeable`.

**Tier.** Tier 1: 3 calls, 0 events. Tier 2: 0 calls, 1 event. Tier 3: 3 calls, 1 event.

---

### 2.12. World & Placement

**Purpose.** Answer questions about the ground before anything is placed on it, and place the things
that are placed by hand rather than built: the supply camp, and every hut block.

This is the only block that must work before a colony exists. §2 steps 0–3 all happen on bare ground.

**Questions it answers.**

* Is this ground flat enough for a supply camp, and if not, exactly which blocks are in the way?
* Have I already used my one supply camp?
* Is there enough water here for a fisher's hut?
* Will this schematic fit, and what would it overwrite?
* Is there any stone under this mine?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `Sites.terrain(WorldPos centre, int radiusBlocks)` | `TerrainReport` | Surface height range, a flatness score, the side of the largest flat square, solid ground and water block counts, biome and biome category. §2 step 2 asks for at least 8×8 chunks of reasonably flat ground and §7.1 makes a bad choice permanent, so the caller needs measurements, not a yes/no. | radius above the server's scan limit; chunks not loaded | 1 |
| `Sites.supplyState(PlayerRef player)` | `SupplyState` | Whether a supply camp or ship has already been placed and whether the config allows more. §2 step 1: one per world, `allowInfiniteSupplyChests` defaults to false. | — | 1 |
| `Sites.checkSupplyCampSite(WorldPos pos, Facing facing, StyleId style)` | `PlacementCheck` | Whether the camp will place here and, if not, every offending block by position and kind: not flat, obstructed, vegetation, water. §2 step 1 gives the default footprint as 16×17 fully cleared — no holes, flowers, grass, ferns, seagrass or coral — but also notes the footprint depends on the style, so the check must be asked, never assumed. | unknown style | 1 |
| `Sites.checkSupplyShipSite(WorldPos pos, Facing facing, StyleId style)` | `PlacementCheck` | The same for the ship, which needs a body of water of at least 32×20 by default (§2 step 1). | unknown style | 2 |
| `Sites.placeSupplyCamp(WorldPos pos, Facing facing, StyleId style, InventoryRef source)` | `CommandOutcome` | Places the camp, consuming the item. §2 step 1: this is where the town hall block comes from, and the town hall block cannot be crafted before one has been placed. | check would refuse; camp already used; item not held | 1 |
| `Sites.placeSupplyShip(WorldPos pos, Facing facing, StyleId style, InventoryRef source)` | `CommandOutcome` | The same for the ship. | as above | 2 |
| `Sites.checkHutSite(BuildingType type, WorldPos pos, Facing facing, StyleId style, int level)` | `PlacementCheck` | Whether the hut block and its eventual schematic fit: obstruction, overlap with another building, outside the claim, unloaded chunks. Returns the footprint box and how many blocks would need clearing. | unknown type or style; level out of range | 1 |
| `Sites.placeHutBlock(BuildingType type, WorldPos pos, Facing facing, StyleId style, int level)` | `CommandOutcome` | Places the hut block. It does **not** start construction — §7.3's first cause of "the builder is not building" is exactly this confusion. Construction is 2.2. | check would refuse; outside the claim; caller lacks the block | 1 |
| `Sites.waterNear(WorldPos centre, int radiusBlocks)` | `List<WaterBody>` | Water bodies with their dimensions and depth. §2 step 5 requires 7×7×2 next to a fisher's hut, which is the fastest early food; §9.5 makes water a first-class scouting problem. | radius above the scan limit | 2 |
| `Sites.checkMineSite(WorldPos pos)` | `MineSiteCheck` | Stone layers below the position, the deepest shaft the hut level could reach, and whether the hut sits too low. §7.6: a mine hut placed below its depth limit leaves the miner refusing to work and demanding an upgrade; keep the hut at least 4 blocks above the level's depth limit. | — | 2 |
| `Sites.biomeAt(WorldPos pos)` | `BiomeInfo` | Biome id and category. Determines raider type (§6.1) and which mod crops will grow (§4.1). | chunk not loaded | 2 |
| `Catalog.styles()` | `List<StyleId>` | Which build styles are installed. Every footprint and every schematic depends on the style (§2 step 1). | — | 2 |
| `Sites.clearArea(BoundingBox box, InventoryRef dropTarget)` | `CommandOutcome` | Removes vegetation and loose blocks inside a box so a placement check will pass. Deliberately narrow: it clears, it does not build. See section 5 on world editing. | box larger than the server's limit; box outside the claim once a colony exists | 3 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `SupplyPlacedEvent` | A supply camp or ship is placed anywhere on the server. | `WorldPos where`, `boolean ship`, `PlayerRef by` | 2 |
| `HutBlockPlacedEvent` | A hut block is placed, by any actor. | `BuildingType type`, `WorldPos where`, `PlayerRef by` | 2 |
| `HutBlockBrokenEvent` | A hut block is destroyed while its building still existed. | `BuildingId building`, `BuildingType type`, `PlayerRef by` | 1 |

**Worked example — place the supply camp (§2 step 1).**

1. `Sites.supplyState(player)` → `supplyCampPlaced == false`; if it is true and
   `infiniteSupplyAllowed == false`, the run is over before it starts (§7.1 mistake 2).
2. `Catalog.styles()` → pick a style; the footprint follows from it.
3. `Sites.terrain(candidate, 32)` → a flat square of at least 17 on a side.
4. `Sites.checkSupplyCampSite(candidate, facing, style)` → if `placeable == false`, read `problems`:
   every `VEGETATION` and `OBSTRUCTED` entry has a position, so the caller can clear precisely rather
   than levelling a field. §2 step 1's advice, "shift one or two blocks and widen the cleared area",
   becomes a loop over positions.
5. `Sites.clearArea(check.footprint, player)` if the caller may edit the world; otherwise re-check at a
   shifted position.
6. `Sites.placeSupplyCamp(candidate, facing, style, player)`.
7. §2 step 1's exit condition is a town hall block and a build tool in inventory, which the caller
   confirms with `Api.inventoryOf(player)` (2.7).

**Tier.** Tier 1: 6 calls, 1 event. Tier 2: 6 calls, 2 events. Tier 3: 1 call, 0 events.

---

### 2.13. Permissions

**Purpose.** Say who the caller is and what they may do, so that every other block can state its
requirements once and stop worrying.

Multiplayer makes this compulsory: a colony has an owner, other players have ranks, and some actions
— deleting a colony, forcing a raid, lifting the build-level gate — belong to an operator only. §7.1
says moving a badly sited town hall means an operator deleting the colony, which is precisely the sort
of action that must not be available to an ordinary caller.

**Questions it answers.**

* Who does the API think I am?
* May I place huts in this colony? May I hire? May I start research?
* Who else has rights here?
* How do I hand an automation account exactly the rights it needs?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `Api.callerIdentity()` | `CallerIdentity` | The player the caller acts as, whether it is a server operator, whether it is automation rather than a person, and the actions it may take with no colony in hand. Every caller's first call. | — | 1 |
| `colony.permissions().rankOf(PlayerRef player)` | `PermissionRank` | Owner, officer, friend, neutral or hostile. | — | 1 |
| `colony.permissions().can(PlayerRef player, ColonyAction action)` | `boolean` | Whether that player may take that action here. A caller asks before acting rather than acting and reading the refusal. | unknown action | 1 |
| `colony.permissions().list()` | `List<PlayerPermission>` | Everyone with a rank in this colony. | — | 2 |
| `colony.permissions().setRank(PlayerRef player, PermissionRank rank)` | `CommandOutcome` | Promotes or demotes. | caller is not the owner; cannot change the owner's own rank | 2 |
| `colony.permissions().setActionAllowed(PermissionRank rank, ColonyAction action, boolean allowed)` | `CommandOutcome` | Adjusts what a rank may do. | caller is not the owner | 3 |
| `Colonies.delete(ColonyId id, boolean deleteBuildings)` | `CommandOutcome` | Removes a colony. §7.1: this is the only remedy for a town hall in a bad spot, and it is an operator's action. | caller is not an operator | 3 |
| `colony.setFreeMode(boolean on)` | `CommandOutcome` | Lifts the builder's-hut level gate for testing (§0). An operator's switch, and a caller driving a real colony must not use it. | caller is not an operator; free mode disabled by config | 3 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `PermissionChangedEvent` | A rank or an action permission changes. | `PlayerRef player`, `PermissionRank rank`, `ColonyAction action`, `boolean allowed` | 3 |

**Worked example — an automation account joins an existing colony.**

1. `Api.callerIdentity()` → `automation == true`, `serverOperator == false`.
2. `colony.permissions().rankOf(identity.player)` → `NEUTRAL`; the agent can read but not act.
3. The colony owner runs `colony.permissions().setRank(agent, OFFICER)`.
4. The agent re-checks `can(agent, ColonyAction.PLACE_HUT)` and `can(agent, MANAGE_HUTS)` before its
   first build order, and reports a clear refusal to its operator instead of failing mid-sequence.

**Tier.** Tier 1: 3 calls, 0 events. Tier 2: 2 calls, 0 events. Tier 3: 3 calls, 1 event.

---

### 2.14. Diagnostics

**Purpose.** Answer "what is wrong with this colony" and "how far along is it" in one call each, and
expose the server settings that change the rules.

The other thirteen blocks answer specific questions. This one exists because both callers — the agent
choosing what to do next, and the operator asked why a colony is stuck — start from no hypothesis at
all.

**Questions it answers.**

* What is wrong with this colony, worst first?
* What is the single next thing blocking progress?
* Is this server running default settings, or has the config changed the rules?
* What has the colony been saying in chat?
* Which version of this API am I talking to?

**Pull.**

| call | returns | what it means | when it fails | tier |
|---|---|---|---|---|
| `colony.problems()` | `List<ColonyProblem>` | Every problem in the colony, aggregated from citizens, buildings, orders and requests, sorted by severity and by how many citizens each affects. Each entry names its subject and a suggested fix. This is the operator's front door and the agent's planning input. | — | 1 |
| `colony.progression()` | `ProgressionReport` | Where the colony stands against §1.5's checklist: town hall level, highest builder's hut, warehouse level, courier count, university level, research finished against reachable, building types built against total, building levels built, population against cap, and a coarse stage. | — | 2 |
| `colony.nextGates()` | `List<StageGate>` | The requirements for the next stage, each met or unmet with a suggested fix. §3.2's dependency chain expressed as a checklist the caller can walk. | — | 2 |
| `Colonies.config()` | `ConfigReport` | The server settings that change the rules of the game: starting citizens, citizen ceiling, colony size and minimum distance, supply chest policy, raid frequency and horde size, whether the daylight cycle is on, whether free mode is available. §1.1, §1.4, §6.2 and §9.8 all note that these are configurable and that the wiki's numbers are defaults. A caller that assumes defaults will be wrong on someone's server. | — | 1 |
| `colony.recentNotifications(int maxEntries)` | `List<ColonyNotification>` | The messages the colony has produced — the chat lines a player would have seen. §4.1 has citizens complain in chat at zero saturation, §6.1 announces raid direction in chat, and an operator reading a log needs them. | `maxEntries` out of range | 2 |
| `Api.version()` | `ApiVersion` | API version, mod version, game version. The counts in §1.2 and §1.3 are version-dependent, so a caller must be able to tell which version it is driving. | — | 1 |
| `Api.events().active()` | `List<SubscriptionInfo>` | What this caller is subscribed to, for a caller that has lost track. | — | 3 |
| `Catalog.problemKinds()` | `List<ProblemKindInfo>` | See 2.5. | — | 3 |

**Push.**

| event | fires exactly when | payload fields | tier |
|---|---|---|---|
| `ColonyProblemRaisedEvent` | A colony-level problem appears that was not there before — no warehouse, no courier, no dining hall, population capped, research slots idle. | `ProblemKind kind`, `ProblemSeverity severity`, `String message`, `int affectedCitizens` | 1 |
| `ColonyProblemClearedEvent` | The same problem stops applying. | `ProblemKind kind` | 1 |
| `ProgressionStageChangedEvent` | The colony's coarse stage advances or regresses. | `ProgressionStage from`, `ProgressionStage to` | 2 |

**Worked example — an operator is asked why a colony has done nothing for an hour.**

1. `Colonies.list()` (2.1) → find the colony.
2. `colony.problems()` → sorted list. Suppose the top entry is `NO_COURIER`, severity `BLOCKING`,
   affecting eleven citizens.
3. `colony.couriers()` (2.6) → one warehouse, two courier slots, zero couriers assigned.
4. `colony.unemployed()` (2.4) → three idle citizens; `colony.buildingsOfType(COURIER)` → no courier
   hut exists. §2 step 7: each courier needs their own hut.
5. The operator reports the answer: the colony has been fulfilling every request by hand and there has
   been no player to do it.
6. `colony.nextGates()` confirms it — the gate for the "self-running" stage is unmet.

**Tier.** Tier 1: 3 calls, 2 events. Tier 2: 3 calls, 1 event. Tier 3: 2 calls, 0 events.

---

### 2.15. Totals across the blocks

| Block | Pull calls | Events | Tier 1 (calls / events) | Tier 2 | Tier 3 |
|---|---|---|---|---|---|
| 2.1 Colony | 15 | 6 | 12 / 1 | 2 / 4 | 1 / 1 |
| 2.2 Construction & Build Orders | 14 | 7 | 9 / 5 | 4 / 1 | 1 / 1 |
| 2.3 Buildings & Levels | 15 | 5 | 11 / 3 | 3 / 1 | 1 / 1 |
| 2.4 Citizens & Professions | 16 | 8 | 10 / 4 | 5 / 4 | 1 / 0 |
| 2.5 Work & Idleness | 7 | 4 | 4 / 2 | 2 / 2 | 1 / 0 |
| 2.6 Requests & Logistics | 12 | 5 | 7 / 3 | 2 / 1 | 3 / 1 |
| 2.7 Storage & Resources | 11 | 2 | 6 / 0 | 4 / 1 | 1 / 1 |
| 2.8 Food & Happiness | 13 | 5 | 6 / 2 | 6 / 3 | 1 / 0 |
| 2.9 Research | 11 | 4 | 0 / 0 | 10 / 3 | 1 / 1 |
| 2.10 Military & Raids | 11 | 5 | 0 / 0 | 6 / 4 | 5 / 1 |
| 2.11 Territory & Claims | 6 | 2 | 3 / 0 | 0 / 1 | 3 / 1 |
| 2.12 World & Placement | 13 | 3 | 6 / 1 | 6 / 2 | 1 / 0 |
| 2.13 Permissions | 8 | 1 | 3 / 0 | 2 / 0 | 3 / 1 |
| 2.14 Diagnostics | 8 | 3 | 3 / 2 | 3 / 1 | 2 / 0 |
| **Total** | **160** | **60** | **80 / 24** | **55 / 28** | **25 / 8** |

Eighty calls and twenty-four events are needed before a colony has a warehouse and a courier. That is
half the catalogue spent on §2 steps 0–7, which is the right proportion: those steps are where a
colony is lost, and everything after them is repetition at a larger scale.

---

## 3. Types

Every return value in section 2 is a primitive, one of the records below, or a list of one of those.
There is no `Object`, no map keyed by string, no JSON blob, no field whose meaning depends on another
field's value except where stated in words here.

Conventions, stated once:

* **No nulls in lists.** A query with no answer returns an empty list, never null.
* **Absent references.** A record field that names another entity and may legitimately be absent — a
  citizen with no workplace, an order with no builder — holds a sentinel value of its type whose
  fields are documented as "absent": `ColonyId(0, "")`, `BlockPos` is never absent, `CitizenId(colony,
  0)`, `BuildingId(colony, BlockPos(0,0,0))`. A caller checks the number against 0.
* **`-1` means unlimited**, never "unknown". Where a value is genuinely unknown the record carries an
  explicit boolean saying so.
* **Strings are identifiers, not display text**, except fields named `message`, `displayName`,
  `description`, `activity`, `reason` and `suggestedFix`, which are human-readable and may be
  translated.
* **Handles are not data.** `ColonyHandle`, `BuildingHandle`, `CitizenHandle`, `BuildOrderHandle`,
  `RequestHandle` and the typed building views are objects with methods. They have no readable
  fields, they are not serialisable, and they are not listed below.
* **Times.** `gameTime` is the world's tick counter. `day` is the colony's day counter. Durations in
  research are `seconds` of real time (§5.2). Nothing is a wall-clock timestamp.

### 3.1. Identifiers and common types

```java
record ColonyId(int number, String dimension)
record BlockPos(int x, int y, int z)
record WorldPos(String dimension, BlockPos pos)
record ChunkRef(String dimension, int chunkX, int chunkZ)
record BoundingBox(BlockPos min, BlockPos max)
record BuildingId(ColonyId colony, BlockPos hutBlock)
record CitizenId(ColonyId colony, int number)
record BuildOrderId(ColonyId colony, int number)
record RequestId(ColonyId colony, UUID token)
record ResearchId(String branch, String name)
record PlayerRef(UUID uuid, String name)
record StyleId(String namespace, String path)

record BuildingType(String id, String displayName)
// Constants for every type named in progression.md §1.2 are published on this record:
// BuildingType.TOWN_HALL, BUILDER, RESIDENCE, TAVERN, WAREHOUSE, COURIER, FISHER, FARMER,
// DINING_HALL, CHEFS_KITCHEN, BAKERY, FORESTER, MINE, QUARRY, SAWMILL, STONEMASON, CRUSHER,
// SIFTER, SMELTERY, BLACKSMITH, MECHANIC, GLASSBLOWER, COMPOSTER, PLANTATION, DYER, FLOWERSHOP,
// NETHER_MINE, ALCHEMIST, SCHOOL, LIBRARY, HOSPITAL, GRAVEYARD, MYSTICAL_SITE, UNIVERSITY,
// GUARD_TOWER, BARRACKS, BARRACKS_TOWER, COMBAT_ACADEMY, ARCHERY, GATEHOUSE, STABLE, APIARY,
// COWHAND, SHEPHERD, CHICKEN_FARMER, SWINEHERD, RABBIT_HUTCH, FLETCHER, CONCRETE_MIXER,
// BRICK_YARD, ENCHANTER, and any further types the installed version publishes.

record JobType(String id, String displayName, BuildingType building)

record ItemSpec(String itemId, String variant)   // variant "" means "any variant of this item"
record ItemAmount(ItemSpec item, int count)

enum Facing { NORTH, EAST, SOUTH, WEST }

record CommandOutcome(boolean accepted, OutcomeCode code, String message)
enum OutcomeCode {
    ACCEPTED, REFUSED_RULE, REFUSED_STATE, REFUSED_RESOURCES,
    REFUSED_PERMISSION, REFUSED_UNKNOWN_TARGET, NO_EFFECT
}

record InventoryRef(InventoryKind kind, WorldPos container, PlayerRef player)
enum InventoryKind { PLAYER, CONTAINER, COLONY_STOCK }
// PLAYER uses the player field, CONTAINER uses the container field, COLONY_STOCK uses neither.

record Subscription(UUID id)                     // close() ends it
record SubscriptionInfo(UUID id, String eventType, ColonyId scope, long createdGameTime)
record ApiVersion(int major, int minor, String modVersion, String minecraftVersion)
```

### 3.2. Colony

```java
record ColonySummary(ColonyId id, String name, PlayerRef owner, BlockPos center,
                     int citizenCount, int townHallLevel, boolean loaded)
record ColonyDistance(ColonySummary colony, double distanceBlocks, int distanceChunks)
record ColonyIdentity(ColonyId id, String name, PlayerRef owner, String dimension,
                      BlockPos center, ChunkRef centerChunk, long foundedGameTime, StyleId style)
record ColonyProgress(int citizens, int citizenCap, int adultMen, int adultWomen, int children,
                      int buildings, int buildingsUnderConstruction, int buildingTypesBuilt,
                      int townHallLevel, int maxBuilderHutLevel, int warehouseCount,
                      int courierCount, int universityLevel, int researchFinished)
record ColonyClock(int day, int timeOfDayTicks, DayPhase phase, boolean raining, boolean snowing,
                   boolean daylightCycleOn)
enum DayPhase { MORNING, DAY, EVENING, NIGHT }
record ColonySettings(boolean newCitizensEnabled, boolean autoHiringEnabled,
                      boolean autoHousingEnabled, boolean movingInEnabled,
                      boolean freeModeOn, boolean progressToChat)
record ColonyCapacity(int beds, int bedsFree, int citizens, int citizenCap, CapSource capSource,
                      int configCeiling)
enum CapSource { BEDS, RESEARCH, CONFIG, TOWN_HALL }
record FoundationCheck(boolean allowed, List<FoundationProblem> problems,
                       int nearestColonyDistanceChunks, int minimumDistanceChunks,
                       int initialRadiusChunks)
record FoundationProblem(FoundationProblemKind kind, String message, BlockPos where)
enum FoundationProblemKind {
    NO_TOWN_HALL_BLOCK, TOO_CLOSE_TO_COLONY, INSIDE_ANOTHER_COLONY,
    DIMENSION_NOT_ALLOWED, NOT_OWNER_OF_BLOCK, CHUNKS_NOT_LOADED
}
record FoundResult(boolean founded, ColonyId id, CommandOutcome outcome)
```

### 3.3. Construction

```java
record BuildOrderSummary(BuildOrderId id, BuildingId building, BuildingType type, WorkOrderKind kind,
                         int targetLevel, int priority, BuildOrderState state,
                         CitizenId assignedBuilder, int percentComplete)
enum WorkOrderKind { BUILD, UPGRADE, REPAIR, REMOVE, MOVE }
enum BuildOrderState { QUEUED, CLAIMED, IN_PROGRESS, STALLED, COMPLETE, CANCELLED }
record BuildOrderStatus(BuildOrderId id, BuildOrderState state, int percentComplete,
                        CitizenId assignedBuilder, BuildStage stage,
                        List<ResourceNeed> missing, String stallReason,
                        long lastProgressGameTime)
enum BuildStage { NOT_STARTED, CLEARING, STRUCTURE, DECORATION, ENTITIES, FINISHED }
record BuildOrderResult(boolean created, BuildOrderId order, CommandOutcome outcome)
record ResourceNeed(ItemSpec item, int needed, int atSite, int inColony, ResourceStatus status)
enum ResourceStatus { HAVE_ON_SITE, HAVE_IN_COLONY, MISSING }   // §4.3: black, green, red
record BuildEligibility(boolean allowed, int targetLevel, int maxBuilderHutLevel,
                        EligibilityBlock block, String message)
enum EligibilityBlock {
    NONE, BUILDER_HUT_LEVEL, NO_BUILDER, MAX_LEVEL_REACHED, TYPE_NOT_UNLOCKED,
    ORDER_ALREADY_OPEN, OUTSIDE_CLAIM, BUILDING_PAUSED
}
record BuilderStatus(CitizenId builder, BuildingId hut, int hutLevel, boolean free,
                     BuildOrderId currentOrder, int percentComplete)
```

### 3.4. Buildings

```java
record BuildingSummary(BuildingId id, BuildingType type, int level, int maxLevel, BlockPos position,
                       boolean underConstruction, boolean paused, int assignedWorkers,
                       int workerSlots)
record BuildingInfo(BuildingId id, BuildingType type, String customName, int level, int maxLevel,
                    BlockPos position, Facing facing, StyleId style, boolean paused,
                    boolean underConstruction, int pickupPriority, List<CitizenId> workers,
                    int workerSlots, List<BlockPos> containers)
record BuildingTypeInfo(BuildingType type, int maxLevel, boolean unlocked, ResearchId unlockedBy,
                        JobType job, boolean freeStanding, int maxPerColony)
// maxPerColony is -1 for unlimited (§1.2: Residence, Guard Tower, Mine, Farmer's Hut and others).
// freeStanding is false for types that only exist inside another building, such as Barracks Tower.
record BuildingTypeStatus(BuildingType type, boolean unlocked, int built, int highestLevel)
record BuildingLimits(int level, ToolTier maxToolTier, int maxEnchantLevel, int maxBowEnchantments)
enum ToolTier { WOOD_OR_GOLD, STONE, IRON, DIAMOND, NETHERITE }   // §10.2
enum CapacityKind { BEDS, COURIER_SLOTS, RESEARCH_SLOTS, GUARD_SLOTS, FIELD_SLOTS,
                    TOWER_SLOTS, RECIPE_SLOTS, WORKER_SLOTS }
record BuildingCapacity(CapacityKind kind, int total, int used, boolean applicable)
```

### 3.5. Citizens

```java
record CitizenSummary(CitizenId id, String name, JobType job, BuildingId workBuilding,
                      BuildingId homeBuilding, int jobLevel, double saturation, double happiness,
                      boolean child, boolean idle)
record CitizenInfo(CitizenId id, String name, Gender gender, boolean child, JobType job,
                   BuildingId workBuilding, BuildingId homeBuilding, BlockPos position,
                   int jobLevel, int experience, List<SkillLevel> skills)
enum Gender { MALE, FEMALE }
record SkillLevel(Skill skill, int level)
enum Skill { ATHLETICS, DEXTERITY, STRENGTH, AGILITY, STAMINA, MANA,
             ADAPTABILITY, FOCUS, CREATIVITY, KNOWLEDGE, INTELLIGENCE }
record CitizenNeeds(CitizenId id, double saturation, int requiredFoodTier, double health,
                    double maxHealth, double happiness, boolean hasHome, boolean hasBed,
                    boolean sick, boolean injured, boolean mourning, int mourningDaysLeft,
                    int homeToWorkDistance, int daysWithoutHome, int nightsWithoutBed)
record ProfessionCoverage(JobType job, BuildingType building, int buildingsBuilt, int slots,
                          int staffed)
record VisitorSummary(int visitorId, String name, BuildingId tavern, List<ItemAmount> recruitCost,
                      List<SkillLevel> skills)
enum ComplaintKind { HOMELESS, NO_BED, COMMUTE_TOO_LONG, NOTHING_TO_DO, SICK_UNTREATED, HUNGRY }
```

### 3.6. Work and problems

```java
enum WorkState { WORKING, WALKING, IDLE, SLEEPING, EATING, HIDING_FROM_RAID,
                 MOURNING, PAUSED, NO_JOB, DEAD }
record WorkStatus(CitizenId id, WorkState state, String activity, BlockPos position,
                  long lastProductiveGameTime, List<WorkProblem> problems)
record WorkProblem(ProblemKind kind, ProblemSeverity severity, String message,
                   ItemSpec item, BuildingId building, CitizenId citizen, String suggestedFix)
enum ProblemSeverity { INFO, WARNING, BLOCKING }
enum ProblemKind {
    // §7.2, the nine causes of a worker standing still
    NIGHT_OR_WEATHER, MOURNING, RAID_IN_PROGRESS, BUILDING_PAUSED, HUNGRY, OPEN_REQUEST,
    TOOL_TIER_TOO_HIGH, INVENTORY_FULL, LOW_LEVEL,
    // §7.3, the builder is not building
    NO_BUILD_ORDER, BUILDER_HUT_LEVEL, NO_BUILDER, MISSING_BUILD_RESOURCE,
    // §7.4, starving
    NO_DINING_HALL, NO_FUEL_ENABLED, FOOD_TIER_TOO_LOW, NO_FIELD, NO_SEED, FIELD_NOT_HYDRATED,
    DAYLIGHT_CYCLE_OFF,
    // §7.5, the colony is not growing
    NO_FREE_BED, POPULATION_CAPPED, SPAWNING_DISABLED, NO_ADULT_PAIR,
    // §7.6 and §2, structural
    NO_WAREHOUSE, NO_COURIER, MINE_TOO_DEEP, MULTIPLE_WAREHOUSE_CONFUSION, PROFESSION_UNSTAFFED,
    RESEARCH_SLOT_IDLE, NO_HOME, UNDEFENDED
}
record ProblemKindInfo(ProblemKind kind, String description, String typicalFix, String guideSection)
```

`guideSection` names the section of `progression.md` the cause comes from, so an operator's console can
point at the reasoning rather than restating it.

### 3.7. Requests and logistics

```java
record RequestSummary(RequestId id, ItemSpec item, int count, RequestState state,
                      CitizenId requester, BuildingId requesterBuilding,
                      RequestResolverKind resolver, int priority, boolean important,
                      long createdGameTime)
enum RequestState { CREATED, ASSIGNED, IN_PROGRESS, WAITING_FOR_PLAYER, DELIVERING,
                    COMPLETED, CANCELLED, FAILED }
enum RequestResolverKind { BUILDING_STOCK, WAREHOUSE_STOCK, COURIER_DELIVERY, CRAFTER,
                           PLAYER, NONE }   // §4.4 steps 1 to 5
record RequestDetail(RequestSummary summary, List<ItemAmount> alternatives,
                     List<RequestId> children, RequestId parent, String description)
record RequestFilter(RequestState state, RequestResolverKind resolver, BuildingId building,
                     boolean importantOnly)
// A filter field left at its neutral value does not filter: state and resolver may be null,
// building may be the absent sentinel, importantOnly may be false.
record CourierReport(BuildingId warehouse, int warehouseLevel, int courierSlots,
                     int couriersAssigned, int stacksPerTrip, List<CitizenId> couriers,
                     List<BuildingId> courierHuts)
```

### 3.8. Storage

```java
record StockEntry(ItemSpec item, int inWarehouses, int inBuildings, int inCitizenInventories,
                  int total)
record Affordability(boolean affordable, List<ResourceNeed> lines, int missingKinds,
                     int missingItems)
record DepositResult(CommandOutcome outcome, List<ItemAmount> accepted, List<ItemAmount> rejected)
record CraftingOption(ItemSpec item, JobType job, BuildingType building, BuildingId builtAt,
                      boolean staffed, boolean unlocked, List<ItemAmount> ingredients)
record MinimumStockEntry(ItemSpec item, int count)
```

### 3.9. Food and happiness

```java
record FoodReport(double averageSaturation, double threshold, int citizensBelowThreshold,
                  int citizensAtZero, int diningHalls, int chefsStaffed, int waitersStaffed,
                  int bakersStaffed, boolean anyFuelEnabled, List<FoodStock> stock,
                  int foodConsumedLastDay)
record FoodStock(int tier, int items, int totalSaturation)
record FoodTierInfo(ItemSpec item, int tier, int saturation, boolean vanilla,
                    boolean happinessPenalty)
record FoodRequirement(CitizenId citizen, int requiredTier, int homeLevel,
                       boolean refusingAvailableFood)
record FuelSetting(ItemSpec fuel, boolean allowed)
record FieldInfo(BlockPos scarecrow, BuildingId farm, ItemSpec seed, boolean hydrated,
                 boolean assigned, FieldState state)
enum FieldState { UNASSIGNED, EMPTY, TILLED, PLANTED, GROWN, NOT_HYDRATED, NO_SEED }
record HappinessReport(double average, List<HappinessFactorValue> factors, int unhappyCitizens)
record HappinessFactorValue(HappinessFactor factor, double value, double weight)
enum HappinessFactor {   // §10.5
    SATURATION, HOUSING, SECURITY, SOCIAL, HEALTH, MOURNING, UNEMPLOYMENT,
    IDLE_AT_WORK, COMMUTE, RAID_SURVIVED, SCHOOL, DEATH, HOMELESSNESS, SICKNESS
}
enum CrossDirection { UPWARD, DOWNWARD }
record CropAvailability(ItemSpec crop, BiomeCategory requiredCategory, boolean reachable,
                        int foodTierUnlocked)
```

### 3.10. Research

```java
record ResearchNode(ResearchId id, String displayName, String branch, int column,
                    int requiredUniversityLevel, List<ResearchId> parents,
                    List<ResearchId> exclusiveWith, List<ItemAmount> cost,
                    List<BuildingRequirement> buildingRequirements,
                    List<BuildingType> unlocksBuildings, boolean cancellable,
                    int baseDurationSeconds)
record BuildingRequirement(BuildingType type, int totalLevels)   // §5.3: "3 levels total"
record ResearchState(ResearchId id, ResearchStatus status, int secondsRemaining, int secondsTotal,
                     boolean startable, List<String> blockers)
enum ResearchStatus { LOCKED, AVAILABLE, IN_PROGRESS, FINISHED, EXCLUDED }
record ResearchCapacity(int universityLevel, int researcherSlots, int inProgress,
                        boolean offlineProgress)
record ExclusiveChoice(String branch, boolean decided, ResearchId chosen,
                       List<ExclusiveOption> options)
record ExclusiveOption(ResearchId id, String displayName, int subtreeSize)
// subtreeSize is how many nodes become unreachable if a different option is taken (§0).
record ResearchEffect(String effectId, String displayName, double value, String unit)
```

### 3.11. Military

```java
record RaidForecast(int nightsSinceLastRaid, int minimumNights, int averageNights,
                    boolean possibleTonight, int maxHordeSize, int hordeDifficulty,
                    RaiderType likelyType, boolean sizeIsPredictable)
// sizeIsPredictable is always false: §6.2 states the difficulty formula is not publicly known.
record RaidStatus(boolean active, RaiderType type, String direction, int raidersRemaining,
                  int raidersKilled, int citizensLost, long startedGameTime, boolean spiesActive)
enum RaiderType { BARBARIAN, PIRATE, MUMMY, NORDIC, AMAZON, UNKNOWN }   // §6.1
record DefenceReport(int guards, int citizens, double guardsPerCitizen, int guardTowers,
                     int barracks, int barracksTowers, int freeGuardSlots, int freeBeds,
                     boolean securityFactorMaxed, List<GuardTypeCount> byType)
// securityFactorMaxed is true at two guards per three citizens (§10.5).
record GuardTypeCount(GuardType type, int count)
enum GuardType { KNIGHT, RANGER, DRUID }   // §6.4
record GuardInfo(CitizenId citizen, GuardType type, BuildingId building, int level,
                 PatrolMode patrolMode, int patrolRadius, boolean equipped,
                 List<ItemSpec> missingEquipment)
enum PatrolMode { PATROL, GUARD_POST, FOLLOW, AUTO }
record RaiderPosition(int raiderId, RaiderType type, BlockPos position, double health)
record RaidRecord(int day, RaiderType type, int raiders, int citizensLost, boolean noLosses,
                  long durationTicks)
```

### 3.12. Territory

```java
record ClaimInfo(ColonyId colony, ChunkRef centerChunk, int radiusChunks, int claimedChunks,
                 int maxRadiusChunks, List<ClaimContribution> contributions)
record ClaimContribution(BuildingId building, BuildingType type, int level, int extraRadiusChunks)
record ChunkOwner(ChunkRef chunk, boolean claimed, ColonyId colony, boolean inBuildRange)
record AlliedColony(ColonyId colony, String name, Relation relation, boolean teleportEnabled)
enum Relation { NEUTRAL, ALLY, ENEMY }
```

### 3.13. World and placement

```java
record PlacementCheck(boolean placeable, List<PlacementProblem> problems, BoundingBox footprint,
                      int blocksToClear)
record PlacementProblem(PlacementProblemKind kind, BlockPos where, String message)
enum PlacementProblemKind {
    NOT_FLAT, OBSTRUCTED, VEGETATION, WATER, LAVA, NOT_ENOUGH_WATER, OUTSIDE_CLAIM,
    OVERLAPS_BUILDING, TOO_CLOSE_TO_BUILDING, CHUNKS_NOT_LOADED, BELOW_WORLD, ALREADY_PLACED,
    UNKNOWN_STYLE
}
record TerrainReport(WorldPos center, int radiusBlocks, int minSurfaceY, int maxSurfaceY,
                     int modalSurfaceY, double flatnessScore, int largestFlatSquareSide,
                     int solidGroundBlocks, int waterBlocks, String biomeId,
                     BiomeCategory biomeCategory)
// flatnessScore is the fraction of columns in the radius whose surface Y equals modalSurfaceY,
// from 0.0 to 1.0. It is a measurement, not a judgement; see the coverage gap in section 6.
enum BiomeCategory { ANY, COLD, TEMPERATE, HOT_HUMID, HOT_DRY }   // §4.1, mod crop categories
record BiomeInfo(String biomeId, BiomeCategory category, RaiderType raiderType, boolean ocean)
record WaterBody(BlockPos center, int lengthX, int lengthZ, int depth, int surfaceBlocks,
                 boolean ocean)
record SupplyState(boolean supplyCampPlaced, boolean supplyShipPlaced,
                   boolean infiniteSupplyAllowed, PlayerRef placedBy)
record MineSiteCheck(boolean suitable, int hutY, int deepestShaftY, int stoneLayersBelow,
                     List<PlacementProblem> problems)
```

### 3.14. Permissions

```java
enum PermissionRank { OWNER, OFFICER, FRIEND, NEUTRAL, HOSTILE }
enum ColonyAction {
    VIEW, PLACE_HUT, BREAK_HUT, MANAGE_HUTS, ACCESS_HUTS, MANAGE_CITIZENS, MANAGE_RESEARCH,
    MANAGE_REQUESTS, MANAGE_PERMISSIONS, MANAGE_MILITARY, RECEIVE_MESSAGES, TELEPORT, ADMIN
}
record PlayerPermission(PlayerRef player, PermissionRank rank, List<ColonyAction> actions)
record CallerIdentity(PlayerRef player, boolean serverOperator, boolean automation,
                      List<ColonyAction> globalActions)
```

### 3.15. Diagnostics

```java
record ColonyProblem(ProblemKind kind, ProblemSeverity severity, String message,
                     String suggestedFix, CitizenId citizen, BuildingId building,
                     BuildOrderId order, RequestId request, int affectedCitizens)
record ProgressionReport(int townHallLevel, int builderHutMaxLevel, int warehouseLevel,
                         int courierCount, int universityLevel, int researchFinished,
                         int researchReachable, int buildingTypesBuilt, int buildingTypesTotal,
                         int buildingLevelsBuilt, int population, int populationCap,
                         ProgressionStage stage)
enum ProgressionStage { NO_COLONY, FOUNDED, BUILDER_READY, HOUSED, FED, SUPPLIED,
                        SELF_RUNNING, RESEARCHING, DEFENDED, EXPANDING, ENDGAME }
record StageGate(ProgressionStage stage, String requirement, boolean met, String suggestedFix)
record ConfigReport(int initialCitizenAmount, int maxCitizenPerColony, int initialColonySizeChunks,
                    int maxColonySizeChunks, int minColonyDistanceChunks,
                    boolean allowInfiniteSupplyChests, boolean barbariansSpawn,
                    int barbarianHordeDifficulty, int maxBarbarianSize,
                    int averageNightsBetweenRaids, int minimumNightsBetweenRaids,
                    boolean raidersBreakWalls, boolean raidersBreakDoors, boolean skyRaiders,
                    boolean noSupplyPlacementRestrictions, boolean daylightCycleOn,
                    boolean freeModeAvailable)
record ColonyNotification(long gameTime, NotificationKind kind, String message,
                          CitizenId citizen, BuildingId building)
enum NotificationKind { CHAT, WARNING, RAID, CONSTRUCTION, RESEARCH, DEATH, REQUEST, HAPPINESS }
```

---

## 4. Failure semantics

Stated once. No block table repeats any of this, and no call hedges about it.

**Two kinds of unhappy answer.** A call that the caller was not entitled to make, or that names
something that does not exist, **throws**. A command that the game's rules do not permit **returns**
a `CommandOutcome` with `accepted == false` and a code. Refusal is a normal answer: "the builder's hut
is level 3 and you asked for level 4" is information, not an error, and a caller that wraps every
command in exception handling has misread the API.

**The six global conditions.**

| Condition | What it means | What happens |
|---|---|---|
| **Colony does not exist** | The `ColonyId` names no colony in that dimension, or the colony was deleted since the handle was taken. | `NoSuchColonyException(ColonyId)`. Handles taken from that colony throw the same on their next call. |
| **Stale handle** | The handle refers to something that has ceased to exist: a citizen who died, a build order that completed, a request that resolved, a building whose hut block was broken. | `StaleHandleException(String kind, String id)`. The exception names what went and why, so a caller can decide whether to re-resolve or to give up. A handle never silently resurrects. |
| **Building destroyed mid-call** | A hut block is broken while a call against that building is in flight. | The call completes against the state at entry and returns normally, or throws `StaleHandleException` if it had not yet read anything. The caller learns about the loss from `HutBlockBrokenEvent` and `BuildingRemovedEvent`, not from a corrupted return value. Nothing half-updated is ever returned. |
| **Value out of range** | A level below 1 or above the type's maximum, a priority outside 0–10, a negative count, a scan radius above the server's limit, `maxEntries` below 1. | `IllegalArgumentException` with the parameter name, the value given and the permitted range. This is a programming error in the caller, not a game state, so it throws rather than returning a refusal. |
| **Caller lacks permission** | The caller's rank in this colony does not allow the action (2.13), or an operator-only call was made by a non-operator. | Query calls throw `PermissionDeniedException(ColonyAction, PermissionRank)`. Command calls return `CommandOutcome(false, REFUSED_PERMISSION, …)`, because being refused is a fact about the world the caller may want to report rather than an exception it must catch. A caller that wants to avoid both asks `permissions().can(...)` first. |
| **Called off the server thread** | Any call made from a thread that is not the server thread. | `WrongThreadException`. There is no locking, no queueing and no thread-safe variant. A caller with its own thread must hand the work to the server thread itself. Event listeners are invoked **on** the server thread, so a listener may call the API directly; it must return promptly and must not block. |

**Two further rules that follow from the above.**

* **Every returned record is a snapshot taken at the moment of the call.** It does not update, it does
  not track, and holding it does not keep anything alive. Two fields inside one returned record are
  always consistent with each other. Two separate calls are not guaranteed to be consistent with each
  other, and a caller that needs consistency across several questions must ask the one call that
  answers all of them — which is why records like `ColonyProgress`, `FoodReport` and `DefenceReport`
  are wide rather than split into a dozen accessors.
* **Events are delivered at most once per subscription, in the order they occurred**, and a
  subscription created after an event fired does not receive it. There is no replay and no backlog. A
  caller that needs to know the current state asks a pull call; events tell it when to ask.

---

## 5. Non-goals

Stated so that nobody implementing this invents them.

1. **No streaming, no tick hook, no periodic state dump.** There is no call that returns a stream, no
   subscription that fires on a timer, and no "watch this citizen" facility. `DayStartedEvent` and
   `NightFallEvent` are the highest-frequency events in the catalogue and they fire twice a game day.
2. **No snapshot or delta layer.** The API does not maintain a mirror of colony state for the caller,
   does not diff it, and does not deliver changes as patches. Records are snapshots at the moment of
   the call and nothing more.
3. **No client-side component.** Nothing here runs on a client, renders anything, opens a GUI, draws an
   overlay or sends a packet to a player's screen. The GUIs referred to throughout `progression.md` —
   the hut window, the Required Resources tab, the Clipboard, the Resource Scroll — are named only to
   identify *what information* they show, never as things this API drives.
4. **No world editing.** `Sites.clearArea` is the single exception and is deliberately narrow: it
   removes vegetation and loose blocks from a box so that a placement check can pass. There is no
   block-set call, no schematic paste outside the mod's own placement, no terraforming, no fill, no
   undo. A caller that wants a flat plateau digs it the way a player would, or picks another site.
5. **No item creation.** Nothing in this catalogue brings an item into existence. `deposit`, `withdraw`
   and `fulfill` move items that already exist, from a named source that must actually hold them.
   §9's whole argument about what is and is not reachable in a given world is meaningless if the API
   can conjure a diamond.
6. **No pathfinding, navigation or entity control.** The API does not move citizens block by block,
   does not steer guards during a raid, and does not expose the AI's internal task state.
   `recallWorkers` and `setPatrolMode` are the only movement-adjacent commands, and both are switches
   the game itself offers.
7. **No config editing.** `Colonies.config()` reads the settings that change the rules. It does not
   write them. §9.8 treats changing config as changing the rules of the run, and that decision belongs
   to a server operator with a text editor, not to a colony-driving agent.
8. **No cross-colony trade implementation.** Relations and allies are readable (2.11) because §3.4 and
   §4.1 make them matter, but the trade route mechanics of Colony Connections are not modelled here.
   See section 6.
9. **No strategy.** The API answers what is true and does what it is told. It does not decide which
   building to place next, does not rank research, and does not choose a town hall site. Records like
   `StageGate` and `ProblemKindInfo` carry a `suggestedFix` string because §7 already supplies those
   fixes in words — that is a citation, not a planner.
10. **No persistence for the caller.** The API stores nothing on the caller's behalf: no plans, no
    notes, no history beyond what the game itself keeps. `colony.raidHistory` returns what the colony
    records, not what a caller asked to be remembered.

---

## 6. Coverage check

One row per stage of `progression.md`. The middle column names the calls and events that carry the
stage; the right column names what is **not** covered. A named gap is worth more than an invented call,
so gaps are stated plainly and are expanded in 6.1.

| Stage (§) | Covered by | Gap |
|---|---|---|
| **Before the colony** (§2 step 0) — gather wood, cobblestone, coal, iron, string, leather, wool, saplings, flowers, food | `Api.inventoryOf`, `Sites.terrain`, `Sites.biomeAt`, `Colonies.config` | **Not covered.** Nothing here gathers anything. The caller needs its own means of acting in the world before a colony exists. See 6.1 (a). |
| **Supply camp or ship** (§2 step 1) | `Sites.supplyState`, `Sites.checkSupplyCampSite`, `Sites.checkSupplyShipSite`, `Sites.placeSupplyCamp`, `Sites.placeSupplyShip`, `Sites.clearArea`, `Catalog.styles`, `SupplyPlacedEvent` | Whether the one-per-world limit is per world or per player is not settled here. See 6.1 (b). |
| **Town hall siting** (§2 step 2, §7.1) | `Sites.terrain`, `Colonies.nearest`, `Colonies.foundationCheck`, `Sites.placeHutBlock`, `Colonies.found`, `ColonyFoundedEvent`, `colony.claim` | The API measures ground; it does not judge a site. "At least 8×8 chunks of reasonably flat area" stays a caller's decision. See 6.1 (c). |
| **Builder's hut** (§2 step 3, §3.1, §7.3) | `Sites.checkHutSite`, `Sites.placeHutBlock`, `building.hire`, `building.buildEligibility`, `colony.maxBuildableLevel`, `building.requestBuild`, `order.status`, `order.missingResources`, `colony.builders`, `building.deposit`, `BuildOrderCreatedEvent`, `BuildOrderClaimedEvent`, `BuildOrderStalledEvent`, `BuildOrderResumedEvent`, `BuildOrderCompletedEvent`, `BuildOrderRejectedEvent` | None. This stage is fully covered, as it must be: it is the gate on everything else. |
| **Housing** (§2 step 4, §10.1, §7.5) | `colony.capacity`, `colony.citizens`, `citizen.needs`, `citizen.assignHome`, `building.capacity(BEDS)`, `colony.visitors`, `colony.recruitVisitor`, `colony.settings`, `colony.setNewCitizensEnabled`, `CitizenSpawnedEvent`, `CitizenHomeChangedEvent`, `CitizenComplaintEvent`, `PopulationCapReachedEvent` | The residence level a citizen must have to accept a given food tier cannot be planned in advance. See 6.1 (d). |
| **Food** (§2 step 5, §4.1, §7.4) | `colony.food`, `colony.foodStock`, `citizen.saturation`, `citizen.foodRequirement`, `diningHall.allowedFuel`, `diningHall.setFuelAllowed`, `farm.fields`, `farm.assignField`, `field.setSeed`, `Catalog.foodTiers`, `Sites.waterNear`, `colony.biomeCropAvailability`, `CitizenStarvingEvent`, `ColonySaturationCrossedEvent`, `FoodTierRefusedEvent` | No trustworthy "days of food remaining". See 6.1 (e). |
| **Wood and stone** (§2 step 6, §4.5) | `colony.buildingsOfType`, `building.limits`, `Sites.checkMineSite`, `Sites.terrain`, `colony.stock`, `colony.craftableBy`, `building.problems`, `citizen.problems` | Nothing reports whether a forester's hut site actually has trees it may cut, or how much stone is left under a mine. See 6.1 (f). |
| **Warehouse and courier** (§2 step 7, §4.4, §7.6) | `colony.couriers`, `warehouse.capacity(COURIER_SLOTS)`, `building.hire`, `colony.requests`, `colony.playerRequests`, `request.detail`, `request.fulfill`, `warehouse.deposit`, `colony.resetRequestSystem`, `RequestOpenedEvent`, `RequestEscalatedToPlayerEvent`, `RequestResolvedEvent` | With more than one warehouse, nothing says which warehouse will serve which building. See 6.1 (g). |
| **University and research** (§2 step 8, §5, §0) | `Catalog.researchNodes`, `research.state`, `research.all`, `research.capacity`, `research.start`, `research.cancel`, `research.exclusiveChoices`, `research.unlocksOf`, `research.requiredFor`, `research.inProgress`, `research.effects`, `Api.inventoryOf`, `ResearchStartedEvent`, `ResearchCompletedEvent`, `ResearcherIdleEvent` | `secondsRemaining` is an estimate wherever offline catch-up applies. See 6.1 (h). |
| **Military** (§6) | `colony.raidForecast`, `colony.currentRaid`, `colony.defence`, `colony.guards`, `guardBuilding.setGuardType`, `guardBuilding.setPatrolMode`, `colony.hireSpies`, `raid.remainingRaiders`, `colony.raidHistory`, `colony.triggerRaid`, `RaidWarningEvent`, `RaidStartedEvent`, `RaidEndedEvent`, `RaidStalledEvent`, `GuardEquipmentMissingEvent` | Raid strength cannot be predicted, only bounded by config. See 6.1 (i). |
| **Expansion** (§2 step 9, §3.3, §7.1) | `colony.buildings`, `colony.buildingTypeStatus`, `Catalog.buildingTypes`, `colony.professionCoverage`, `colony.claim`, `colony.claimAfter`, `Colonies.chunkOwner`, `order.assignTo`, `order.setPriority`, `colony.progression`, `colony.nextGates`, `BuildingUpgradedEvent`, `BuildingTypeUnlockedEvent`, `ClaimChangedEvent` | None for the mechanics. The layout advice in §7.1 — do not spread the town out — is a caller's judgement over `colony.buildings`. |
| **Finishing** (§1.5) | `colony.progression`, `colony.buildingTypeStatus`, `research.all`, `research.exclusiveChoices`, `colony.capacity`, `Colonies.config`, `Catalog.buildingTypes` | "Finished" is a community definition, and two of its five items are not machine-checkable in general. See 6.1 (j). |

Two requirements that appear across several stages and are not covered anywhere are listed as 6.1 (k)
and 6.1 (l).

### 6.1. The gaps, named

**(a) Everything before the ground.** §2 step 0 is a stage of the game — gather wood and iron — and
this API has nothing to say about it. It begins at `Sites`, which asks questions about places, and at
`Api.inventoryOf`, which reads an inventory but cannot fill one. A headless agent therefore cannot
start a colony with this API alone: it needs a separate facility for mining, chopping and crafting.
§9.4 makes this sharper than it looks — without the first piece of wood the game does not start at
all, and in a flat world that wood comes from breaking a village house by hand. Nothing here does
that. This is a deliberate non-goal (section 5, items 4 and 5) and a real gap at the same time.

**(b) The supply camp's one-per-world bookkeeping.** §2 step 1 says one camp or ship per world, with
`allowInfiniteSupplyChests` defaulting to false. `Sites.supplyState` takes a `PlayerRef` and returns
`placedBy`, which quietly assumes the limit is tracked per player. If it is tracked per world, the
parameter is wrong and the call should take none. The guide does not say which, and this document
declines to guess.

**(c) Site quality.** `TerrainReport` returns measurements: a modal surface height, a flatness
fraction, the side of the largest flat square. §2 step 2 asks for "a large, reasonably flat area…at
least 8×8 chunks", and §7.1 makes the consequence permanent. No measurement in this catalogue captures
"reasonably". Worse, a good site is one with room for buildings that do not exist yet, and the API
cannot know the caller's plan. The gap is real and is left open on purpose: a call that returned a
site score would be inventing a judgement the guide does not supply.

**(d) Home level to food tier.** §4.1 states that the required food tier follows the citizen's home
level, and then states that the wiki does not publish the mapping. `citizen.foodRequirement()`
therefore reports what a specific citizen currently requires, and there is deliberately no catalogue
call returning the table. A caller cannot plan "upgrade these residences to level 4 and then I will
need tier 2" — it can only observe the requirement after the upgrade. Anything more would be a
fabricated table.

**(e) Days of food remaining.** §4.1 is explicit: no source gives a per-citizen per-day figure, only
that consumption rises with worker level and is charged at least once per night. `FoodReport` reports
`foodConsumedLastDay`, which is observed, and total saturation in stock, which is arithmetic. It does
not report days remaining, because that would be a forecast dressed as a fact. A caller that wants one
divides the two numbers and owns the error.

**(f) What the forester and the miner can actually reach.** §2 step 6 gives the forester a working
radius of roughly 150 blocks and excludes trees that are part of a building schematic or have
cobblestone under them; §7.6 and §10.3 make the miner's viable depth depend on the hut's Y and level.
`Sites.checkMineSite` covers the miner's placement problem. Nothing covers the forester: there is no
call that counts the harvestable trees near a candidate site, and no call that reports how much stone
remains beneath a working mine. A caller siting a second forester in a cleared valley (§4.5, the
day 10–25 bottleneck) will find out by waiting.

**(g) Which warehouse serves which building.** §7.6 warns that a courier only sees their own
warehouse. `colony.couriers()` is deliberately per warehouse so that the caller can see the split, but
no call maps a building to the warehouse that will serve it. With one warehouse this does not matter;
with two it is the difference between a working colony and a hut that never receives anything.

**(h) Research time is an estimate under offline catch-up.** §5.1 says that from University 3
researchers partly recover time spent while the colony was offline, and how much depends on the
Knowledge skill capped by Mana. `ResearchState.secondsRemaining` is therefore accurate only while the
colony is ticking. The API reports the number the game holds; it does not promise the finish time.

**(i) Raid strength.** §6.2 quotes the wiki saying that what drives raid difficulty is not publicly
known. `RaidForecast` gives the config bounds — minimum and average nights, horde difficulty, maximum
horde size — the biome-implied raider type, and a `sizeIsPredictable` flag that is always false. A
caller that wants to model raid growth keeps its own records through `colony.raidHistory`.

**(j) "Finished" is not machine-checkable.** §1.5's checklist has five items. Three are countable —
town hall 5, university, warehouse and builder's hut at 5, population at the config ceiling. Two are
not. "Every building type built at least once at max level" depends on which types count: §1.2 notes
that a Barracks Tower only exists inside a Barracks and the Quarry is worked by someone hired at the
Mine, and that the count itself is version-dependent. "All research except the mutually exclusive
branches" is 184 upstream and 185 in this line (§1.3, §0), so the target moves with the version.
`ProgressionReport` therefore reports counts and never declares victory.

**(k) Quests.** §4.1 mentions in passing that the first level-3 Residence triggers a "build a Chef's
Kitchen" quest, and `progression.md` lists a quest system among its sources. No stage of the guide
requires a caller to read or complete a quest, so nothing here does. If quests turn out to gate
content, this catalogue is missing a block.

**(l) Inter-colony trade.** §4.1 and §9.5 make a second colony the only route to some tier-3 dishes in
a single-biome world, through Colony Connections with a Gatehouse at both ends, and §3.4 puts teleport
to allied colonies behind Town Hall 3. `colony.allies()` and `colony.setRelation()` report and set the
relationship; nothing models the trade itself — no route, no shipment, no exchange. A caller pursuing
the top food tier in one biome has no API for the only mechanism that reaches it.

---

## 7. Open questions

Decisions that `progression.md` alone does not settle. Each states the options and the choice this
document makes, so that a later stage can overturn it deliberately rather than by accident.

**7.1. How a building is identified.** Options: (a) the position of its hut block; (b) an opaque
number assigned by the colony; (c) both, with the number authoritative. This document uses (a),
`BuildingId(ColonyId, BlockPos)`, because every caller starts from a position it chose and placed, and
because an operator at a console can type coordinates but not an internal number. The cost is that
§2 step 2 allows the town hall block to be moved within the zone, which would invalidate such an
identifier. The mitigation is `colony.buildingAt(WorldPos)`, which re-resolves. If moving buildings
turns out to be common, (c) is the better answer and the change is mechanical.

**7.2. The grace period before a citizen counts as blocked.** `CitizenBlockedEvent` fires when a
blocking problem has persisted past a grace period, so that a worker walking across town does not
generate events. Options: (a) a fixed server constant; (b) a server config value; (c) a value the
caller passes when subscribing, bounded below by a server minimum. This document picks (c): an agent
driving a colony wants to hear about a stalled builder within seconds, while an operator watching a
whole server wants only the chronic cases, and one constant cannot serve both. If (c) is rejected,
(b) is preferable to (a), and `ConfigReport` gains a field.

**7.3. Whether `Sites.clearArea` should exist at all.** It is the one world-editing call in the
catalogue and it sits uneasily against non-goal 4. Options: (a) drop it, and require the caller to
find ground that is already clear; (b) keep it narrow, clearing vegetation and loose blocks inside a
declared box; (c) allow general block placement and removal. This document picks (b), because §2
step 1 demands a fully cleared 16×17 with no grass, ferns or flowers, and a headless caller has no
other way to produce one. (a) is defensible and would be the stricter choice; (c) is not, because it
would make §9's entire argument about reachable resources meaningless.

**7.4. Whether queries should throw on permission or return a refusal.** Section 4 splits them:
queries throw, commands return `CommandOutcome`. Options: (a) the split as written; (b) everything
throws; (c) everything returns a result object. (a) is chosen because a refused command is a fact
about the colony that a caller usually wants to report, while a query the caller was never entitled to
make is a mistake in the caller. (c) is the more uniform design and would be a reasonable alternative;
it costs every query call a wrapper record.

**7.5. How wide the wide records should be.** `ColonyProgress`, `FoodReport` and `DefenceReport` each
answer several questions at once, so that their fields are consistent with one another (section 4).
Options: (a) wide records as written; (b) narrow accessors, one question each; (c) both. (a) is chosen
because the alternative forces a caller asking "am I about to starve" to make six calls whose answers
may disagree. The cost is that adding a field to a record is a compatibility event.

**7.6. Whether guards belong in Citizens or in Military.** Guards are citizens: they eat, they need
beds to appear (§6.3), they die and are not mourned (§6.1). Options: (a) one Citizens block covering
guards; (b) a separate Military block, with `CitizenDiedEvent` carrying a `guard` flag; (c) guards
duplicated in both. (b) is chosen, because everything a caller does about guards it does for a reason
that lives in §6 — raids — and because guard-specific state (patrol radius, missing equipment, tower
assignment) has no analogue for a farmer. The seam is `CitizenId`, which both blocks use.

**7.7. Whether `colony.problems()` should have a defined order.** Options: (a) unordered; (b) sorted
by severity, then by affected citizens, then by kind. (b) is chosen, because the operator's first
question is "what is worst" and an unordered list makes every caller re-implement the same sort. The
risk is that callers come to depend on the order as an API contract, which it then becomes.

**7.8. Whether the API should expose the colony's chat feed at all.**
`colony.recentNotifications()` returns the messages a player would have seen. Options: (a) expose it;
(b) rely entirely on typed events. (a) is chosen at Tier 2 because §4.1 has citizens complain in chat
and §6.1 announces raid direction in chat, and an operator reading a stuck colony wants the same
sentences a player would have read. The risk is that callers parse the strings instead of using the
typed events; the mitigation is that every notification that matters also has an event, and the text
is translatable and therefore unparseable by design.

**7.9. Whether a second, aggregate "state of the colony" call is needed.** Both callers begin from no
hypothesis, and `colony.problems()` plus `colony.progression()` is two calls where one might do.
Options: (a) leave them separate; (b) add a single `colony.overview()` returning both plus
`ColonyProgress`, `FoodReport` and `DefenceReport`. (a) is chosen, because (b) is a state dump by
another name and would drift toward the polling loop this design rules out (section 5, items 1 and 2).
If a caller genuinely needs one consistent picture, the honest answer is to widen `ProgressionReport`,
not to add a call that returns everything.
