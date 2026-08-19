# Colony needs model — design plan

Target tree: `/home/user/minecolonies/26.2` (Fabric / MC 26.2). Not approved, nothing in the repository
was touched, nothing was committed.

Every `file:line` below was read in this session and is relative to
`26.2/src/main/java/com/minecolonies/`. Claims are marked **[verified]** (I read the code) or
**[assumed]** (inference I did not test). No number here was measured by me; the only measurements
cited are `AI-SCALE-AUDIT.md`'s.

---

## 0. Executive summary, so the decision can be made from this section alone

**About 70 % of what a colony needs list would contain is already computed, in five different shapes,
for five different consumers.** The genuinely missing thing is not the content. It is a *shape*: a
typed, ranked, colony-level, machine-readable list with a claim lifecycle. My recommendation is to
build that shape, populate it exclusively by *reading the existing owners of each number*, and rewrite
`/mc colony diagnose` on top of it as the proof that nothing was lost.

Three decisions carry the design:

1. **A need is a measured deficit, never an event.** It is recomputed from the world on an interval and
   is never "closed" by anyone. It stops existing because the next measurement does not find it. This
   is the only structural defence against the re-trigger bug the brief points at.
2. **The rank is not invented.** Category weights are lifted from the happiness modifier weights the mod
   already ships and has balanced for years (`CitizenHappinessHandler.java:55-82`). Anti-starvation aging
   is lifted from the deliveryman priority aging (`AbstractDeliverymanRequestable.java:96-101`).
3. **The model sits above the request system and reads from it.** The dividing line, stated once: *the
   request system answers "who brings what to whom"; the needs model answers "what should the colony
   start doing next".* Anything with a named recipient is a request. Anything without one is a need.

**The tick cost is negative.** Building the colony aggregate snapshot the model needs also lets
`CitizenHappinessHandler.getSocialModifier` (`:222-258`) and `getGuardFactor` (`:266-283`) stop looping
all citizens. Those two are called once per citizen from `getHappiness` (`:139-159`), and every
citizen's happiness cache is invalidated at nightfall (`Colony.java:671` →
`CitizenManager.checkCitizensForHappiness()` `:564-570`). That is an existing **2·N² ≈ 2 000 000
iteration burst per in-game day at 1000 citizens** which the snapshot reduces to O(N). The model's own
recompute is ~1150 field reads every 600 ticks. **[verified: the code paths; assumed: that the burst is
material — it is not in the audit's JFR because the stand had zero subscribers, and `checkCitizensForHappiness`
is guarded by `!packageManager.getCloseSubscribers().isEmpty()`.]**

If only one part of this ships, ship Stage 0 (§7): it is a diagnostic and a performance fix, and it
changes no behaviour.

---

## 1. Survey — what already exists

### 1.1 Correction to the brief

The brief says `/mc colony diagnose` prints "citizens without beds, unemployed adults, job types with no
building, open requests, work orders with no builder". **Two of those five are in a different command.**
**[verified]**

| number | command | where |
|---|---|---|
| unemployed adults | `diagnose` | `commands/colonycommands/CommandColonyDiagnose.java:162-167` |
| open requests / player-waiting / retried | `diagnose` | `CommandColonyDiagnose.java:337-411` |
| work orders with no builder | `diagnose` | `CommandColonyDiagnose.java:420-453` |
| job slots with no worker | `diagnose` | `CommandColonyDiagnose.java:134-149` |
| **citizens with no bed** | **`/mc citizens fill`** | `commands/citizencommands/CommandCitizenFill.java:93-98` |
| **job types with no building** | **`/mc citizens fill`** | `CommandCitizenFill.java:286-339` |

Confirmed against the generated lang file, `26.2/src/main/generated/assets/minecolonies/lang/en_us.json:423-483`.
This matters: the housing number — the one the first consumer needs — is the one `diagnose` does *not*
have, and it lives in a command whose javadoc calls itself "purely a testing aid" (`CommandCitizenFill.java:39`).

### 1.2 What `diagnose` lacks to become a needs list

It is ~80 % of the *measurement* and 0 % of the *model*. **[verified]**

* No bed accounting at all (§1.1).
* Everything is computed inside `onExecute` and discarded. The only state that survives is a `static
  Map<Integer, Map<String, Observation>> OBSERVATIONS` (`:70`) whose own javadoc calls it "purely a
  debugging aid ... never persisted".
* **It produces strings, not values.** Every finding is a `String.format` into a `List<String>`
  (`:242`, `:276`, `:323`, `:397`, `:442`). Nothing is machine-readable.
* Its only rank is an ad-hoc `int severity` 0..3 on workers (`:232-248`), not comparable across
  categories.
* OP-only, manual, chat-capped at 8 or 20 entries.

So the work is not "expose diagnose". It is "compute the same things continuously, as values, with a
rank and a lifecycle". But `diagnose` **should be rewritten to read the model** — that is the honest
test that nothing the debugging view needed was lost, and it deletes ~100 lines of duplicate counting.

### 1.3 The request system — the closest existing thing

**[verified]** `IRequest` (`api/colony/requestsystem/request/IRequest.java`) is a per-item, per-requester
demand with a full lifecycle: 13 states (`RequestState.java`), parent/child chains, deliveries, display
strings. Buildings raise them via `IBuilding#createRequest` (`api/colony/buildings/IBuilding.java:289,299`).

Facts that constrain my design:

* **Priority exists only on deliveryman requestables.** `AbstractDeliverymanRequestable.java:15-18`:
  `MAX_BUILDING_PRIORITY 10`, `DEFAULT_DELIVERY_PRIORITY 13`, `MAX_AGING_PRIORITY 14`,
  `PLAYER_ACTION_PRIORITY 15`. A `Stack` or `Tool` request has no priority at all. So the request system
  cannot rank "housing vs tools"; it only ranks deliveries against each other.
* **Aging already exists and works.** `incrementPriorityDueToAging()` (`:96-101`) is called from exactly
  one place, `JobDeliveryman.java:289`, when a request is skipped over in the warehouse queue. This is
  the mod's own anti-starvation idiom and I reuse its shape.
* **Escalation to the player already exists.** `StandardRetryingRequestResolver.java:32-33`:
  `MAX_RETRIES = 3`, `RETRY_DELAY = 1200` ticks. After three failures a request lands on the player
  resolver. `diagnose` reads both sets (`CommandColonyDiagnose.java:348-349`) and calls them, correctly,
  "the requests the colony could not satisfy on its own".
* **The canonical need→request→self-clearing pattern already exists**, and I copy it verbatim:
  `MinimumStockModule.onColonyTick` (`colony/buildings/modules/MinimumStockModule.java:115-150`) computes
  `target − count = delta`, creates **at most one** open request per item when `delta > threshold`
  (`:136-142`), and **cancels the request when `delta <= 0`** (`:144-147`). That is the discipline the
  57-requests-in-90-seconds bug lacked.

**Relation to my model — explicit.** The needs model sits **above** the request system.

* *Reads from it*: `MATERIAL` and `TOOL` needs are an aggregation, by item, of requests assigned to the
  player resolver or the retrying resolver — exactly what `diagnose` already counts. The model does not
  re-derive what any building wants.
* *Writes to it*: only for needs whose resolution is `CRAFT`/`REQUEST`, and only through the existing
  `IBuilding#createRequest`, the same call `MinimumStockModule.java:141` makes. **No new resolver, no new
  requestable type, no parallel delivery pipeline.**
* *Never*: the model must not express "the builder wants 64 cobblestone" —
  `BuildingResourcesModule.java:43` (`neededResources`) already does that correctly, with buckets and
  stages. And the request system must not be asked to express "724 people, 276 beds" — there is no
  bed-slot requestable and inventing one would be exactly the parallel model the brief warns about.

### 1.4 Happiness / citizen state — a needs list in per-citizen form

**[verified]** 15 modifiers (`api/util/constant/HappinessConstants.java:47-77`), instantiated with
weights in `entity/citizen/citizenhandlers/CitizenHappinessHandler.java:55-82`:

| modifier | weight | factor function |
|---|---|---|
| `SECURITY` | 4.0 | `getGuardFactor(colony)` — guards vs workers, colony-wide loop |
| `HOMELESSNESS` | 3.0 | `homeBuilding == null ? 0.0 : level/3.0` |
| `FOOD` | 3.0 | `getFoodFactor(citizen)` — diversity + quality vs home level |
| `SOCIAL` | 2.0 | `getSocialModifier(colony)` — colony-wide loop |
| `UNEMPLOYMENT` | 2.0 | `workBuilding == null ? 0.5 : ...` |
| `HEALTH` | 2.0 | sick? |
| `SLEPTTONIGHT` | 1.5 | |
| `IDLEATJOB` / `SCHOOL` / `MYSTICAL_SITE` | 1.0 | `isIdleAtJob()` / pupil / site level |

Functions registered at `apiimp/initializer/ModHappinessFactorTypeInitializer.java:41-53`.
`HOMELESSNESS` and `UNEMPLOYMENT` are `TimeBasedHappinessModifier`s that escalate at 7 and 14 days
(`HappinessConstants.java:15-25`) — i.e. **the mod already has a notion of "this need has gone unmet for
too long"**.

Two performance facts I lean on:

* `getSocialModifier` (`:222-258`) and `getGuardFactor` (`:266-283`) each iterate every citizen, and each
  is called once per citizen from `getHappiness` (`:145-152`). Cached per citizen in `cachedHappiness`
  (`:45`), invalidated wholesale in `processDailyHappiness` (`:135`) which
  `CitizenManager.checkCitizensForHappiness` (`:564-570`) runs over every citizen at nightfall
  (`Colony.java:662-683`, line 671). **Result: 2·N² once per in-game day, when a player is subscribed.**
* `Colony.getOverallHappiness` (`:1493-1512`) iterates all citizens once a second from
  `ColonyView#serializeNetworkData`. Cheap while cached, the trigger for the burst above once not.

**And the aggregation to a colony-level ranked list already exists — on the client, for display.**
`client/gui/townhall/WindowCitizenPage.fillHappinessList` (`:217-275`) sums every modifier over every
citizen, divides by population (`:250`), and renders happy/satisfied/unsatisfied/unhappy icons. That is
a ranked needs list in all but name, computed client-side, unusable by any server system.

### 1.5 Interaction validators — ~40 named shortage predicates

**[verified]** `apiimp/initializer/InteractionValidatorInitializer.java` (374 lines) registers roughly
forty predicates into `InteractionValidatorRegistry` that each name a specific missing thing: no
restaurant, no hospital, no fuel, no ore in the smeltery list, no free fields, no hives, no bees, no
compost, no mesh, no plant ground, no flowers configured, no warehouse for the courier, quarrier with no
quarry, miner with no mineshaft, building level too low for the harvest level, no guard near work/home,
plus the `DEMANDS+HOMELESSNESS` / `NO+UNEMPLOYMENT` / food-quality / food-diversity escalations.

These are evaluated per citizen: `CitizenData.update` (`:1658-1726`) revalidates the citizen's
*already-materialised* `citizenChatOptions` every 3 s (`Colony.java:412-415`, `tickCitizenData`), and
`triggerInteraction` (`:1729-1740`) adds one when a predicate fires. `ChatPriority` is
`HIDDEN/CHITCHAT/PENDING/IMPORTANT/BLOCKING`.

This is the richest existing catalogue of "what is missing" and the most dangerous to consume naively:
re-evaluating 40 predicates × 1000 citizens is dead on arrival. See §6 open question 7.

### 1.6 Building modules — how a building declares what it wants

**[verified]**

* `BuildingResourcesModule` (`colony/buildings/modules/BuildingResourcesModule.java`) is **builder-specific**,
  not a general declaration: `neededResources` (`:43`) is the resource list of the *current work order*,
  fed by `addNeededResource` (`:200`) / `reduceNeededResource` (`:256`) / `resetNeededResources` (`:305`),
  bucketed by `BuilderBucket` and staged (`nextStage`, `:380`). It is the right owner of "this build needs
  X" and I do not duplicate it.
* `MinimumStockModule` (§1.3) is the general standing-demand mechanism.
* `LivingBuildingModule` owns beds: `getModuleMax() = buildingLevel` (`:106-109`), `onAssignment` sets the
  home and calls `calculateMaxCitizens` (`:92-96`), and `onColonyTick` (`:69-89`) **already auto-houses
  every homeless citizen it can** when the town hall's `AUTO_HOUSING_MODE` is on or the module's
  `HiringMode` is `AUTO`.
* `WorkerBuildingModule.onColonyTick` (`:166-177`) **already auto-hires** one jobless citizen per tick per
  slot when `BuildingUtils.canAutoHire` (`util/BuildingUtils.java:106-113`) passes — and it is already
  gated on a research effect (`:169`).
* Bed *blocks* are tracked separately in `BedHandlingModule` (`:31` `bedList`), which is about where a
  citizen sleeps, not about capacity.

**The colony's housing capacity is `CitizenManager.calculateMaxCitizens()`**
(`colony/managers/CitizenManager.java:421-456`) — it walks the buildings, adds `LivingBuildingModule.getModuleMax()`
(or only the assigned count when `HiringMode.LOCKED`), and separately handles
`WorkAtHomeBuildingModule` buildings where guards live at their post. It is called from 10 sites
(the two `LivingBuildingModule` assignment hooks, `WorkerBuildingModule:246`, three sites in
`RegisteredStructureManager`, citizen removal at `CitizenManager:401`, `CommandCitizenFill:68`).
**I must not reimplement this.** Duplicating the `LOCKED` / work-at-home subtleties is precisely the
drift the brief warns about.

Housing deficit is therefore, exactly and already:
`getCurrentCitizenCount()` (`:537`) − `getMaxCitizens()` (`:506`).
`getPotentialMaxCitizens()` (`:512`) additionally exposes beds that exist but are behind unfilled
work-at-home slots — useful for distinguishing "build more" from "hire into what exists".

### 1.7 Research — what bounds what a colony *can* need

**[verified]** `ResearchManager` (`colony/managers/ResearchManager.java`). Effects are read as
`getResearchEffects().getEffectStrength(Identifier)` (`api/research/IResearchEffectManager.java:17`).
Already consumed as a gate in several places: citizen cap (`CitizenManager.maxCitizensFromResearch`,
`:517-529`), auto-hire (`WorkerBuildingModule:169`), minimum-stock ordering thresholds
(`MinimumStockModule:134`), happiness multiplier (`CitizenHappinessHandler:154`).

**Research already partly runs itself**: `checkAutoStartResearch` (`:141-212`) walks
`autoStartResearch`, checks requirements and university depth, and **starts any zero-cost research
without asking**, or messages the player for costly ones (`:193-205`). So the precedent for "the colony
decides on its own and tells the player" exists in the research system today.

### 1.8 Where periodic colony work is scheduled

**[verified]** `Colony.java:402-428` builds a `TickRateStateMachine<ColonyState>` with these ACTIVE
transitions:

| what | interval (ticks) | constant |
|---|---|---|
| `updateState` | 100 | `ColonyConstants.java:58` |
| `citizenManager.tickCitizenData` | 60 | inline `TICKS_SECOND * 3` |
| `updateSubscribers` | 20 | `ColonyConstants.java:48` |
| `tickRequests` | 11 | `ColonyConstants.java:62` |
| `tickTravellers` / `checkDayTime` | 20 | `:53`, `:66` |
| `updateWayPoints` | 100 | `:43` |
| **`worldTickSlow`** | **500** | `TickRateConstants.MAX_TICKRATE = 500` |
| `tickWorkManager` | 20 | inline |
| `tickImmediateRaids` | 5 | inline |

`worldTickSlow` (`:511-541`) is where every manager's `onColonyTick` runs. `TickingTransition`
(`api/entity/ai/statemachine/tickratestatemachine/TickingTransition.java:40-60`) offsets each transition
by a rotating static counter mod its tick rate (`MAX_TICKRATE_VARIANT = 50`), so transitions and colonies
spread across ticks by construction, and `setTickRate` (`:107-110`) allows changing the rate at runtime.
Max is `MAX_AI_TICKRATE = 12000`.

### 1.9 Automation precedents and off switches

**[verified]** `colony/buildings/workerbuildings/BuildingTownHall.java`: `MOVE_IN` (`:67`),
`AUTO_HIRING_MODE` (`:76`), `AUTO_HOUSING_MODE` (`:81`), plus per-module override
`HiringMode {DEFAULT, AUTO, MANUAL, LOCKED}` stored on `AbstractAssignedCitizenModule:39`. This is
exactly the two-level shape ("colony-wide default" + "per-building override") that a needs-driven
feature should copy rather than invent.

Server config lives in `api/configuration/ServerConfiguration.java` (215 lines,
`createCategory`/`swapToCategory` + `defineBoolean`/`defineInteger`, e.g. `:137`).

### 1.10 Work orders — the build pipeline the housing consumer will use

**[verified]** `WorkOrderBuilding.create(type, building)` (`colony/workorders/WorkOrderBuilding.java:52-80`)
**requires an existing `IBuilding`** — it reads `getBlueprintPath()`, `getStructurePack()`,
`getRotationMirror()` off it. So a hut block must already be placed and registered; a work order cannot
conjure a building. That is the auto-housing feature's hard problem, not mine.

`WorkManager.onColonyTick` (`:390-420`) already: drops invalid orders, un-claims orders whose builder is
gone, and calls `tryAssignWorkOrder` (`:427-468`) twice — once for claimed, once for buildable — each of
which loops **every building in the colony**. That is O(workOrders × buildings) per 20 ticks. At 148
buildings and a large order backlog this is a real cost that an automation feature will make worse.
`getOrderedList` (`:495-502`) already sorts by `IWorkOrder#getPriority()` descending, so work orders have
their own priority axis independent of everything above.

### 1.11 Event bus — usable for invalidation, but incomplete

**[verified]** `api/eventbus/events/colony/` has `CitizenAddedModEvent`, `CitizenRemovedModEvent`,
`CitizenJobChangedModEvent`, `BuildingAddedModEvent`, `BuildingRemovedModEvent`,
`BuildingConstructionModEvent`. They are posted — but **not from every path**. `CommandCitizenFill.java:148`
posts `CitizenAddedModEvent` *by hand* after calling `spawnOrCreateCivilian`, which shows the manager
itself does not post it on that path. `CitizenManager` posts only on resurrection (`:373`) and the
initial spawn (`:638`).

**This is the single most important input to the push-vs-pull decision (§3):** a push-only model would
silently miss needs, and nobody would notice.

---

## 2. The shape

```java
public enum NeedCategory { HOUSING, WORKER, WORKPLACE, FOOD, MATERIAL, TOOL, SECURITY, RESEARCH }

public enum Resolution { BUILD, UPGRADE, HIRE, CRAFT, REQUEST, RESEARCH, PLAYER, NONE }

/** One measured colony-level deficit. Immutable; replaced wholesale on recompute. */
public record ColonyNeed(
    NeedCategory category,
    Identifier   subject,     // minecolonies:residence, minecraft:iron_ingot, minecolonies:job/farmer
    int          magnitude,   // units missing, in the category's own unit
    int          inFlight,    // units already covered by live claims
    float        urgency,     // 0..1, comparable ACROSS categories
    Resolution   resolution,  // the suggested way to fix it
    long         firstSeen)   // game time this (category,subject) first appeared unmet
{
    public int outstanding() { return Math.max(0, magnitude - inFlight); }
}
```

Four design claims, each load-bearing:

**(a) `magnitude` and `urgency` are separate fields, in different units.**
`magnitude` is a count in the category's own unit — beds, workers, item stacks, tool levels. A consumer
that already knows it handles housing reads `magnitude` and needs no scale. `urgency` is a unitless
0..1 derived value whose only job is cross-category comparison. Merge them and you get the
"everything is 10/10" failure the brief names: every consumer would have to invert a score back into a
count and they would all invert it differently.

**(b) `inFlight` is the anti-re-trigger field, and it is the design's centre.**
A need is not "does the shortage exist" but "does the shortage exceed what is already being done about
it". Consumers act on `outstanding()`, never on `magnitude`. This is `MinimumStockModule`'s
`delta = target − count` (`:132`) lifted to colony scope, and it is why the 57-requests loop cannot
happen here: the second attempt sees the first attempt's units.

**(c) `subject` is an `Identifier`, not an enum or a string.**
It must name a building type, an item, a job or a research id uniformly, and third-party addons register
into all four registries. `Identifier` is also what the rest of the mod keys on
(`HappinessRegistry.java:134-144`, job registry, `ModBuildings`).

**(d) There is no `id` and no mutable state on the record.**
Identity is `(category, subject)`. The list is rebuilt wholesale every recompute. `firstSeen` and the
lease/backoff state live in a side map keyed by `(category, subject)` inside the manager, so a need's
*history* survives a recompute while the need itself is always a fresh measurement.

### Units, per category — stated so consumers agree

| category | magnitude unit | denominator for the deficit ratio | source |
|---|---|---|---|
| `HOUSING` | beds missing | population | `getCurrentCitizenCount() − getMaxCitizens()` |
| `WORKER` | unfilled job slots | total job slots | `IAssignsJob.getModuleMax() − getAssignedCitizen().size()` |
| `WORKPLACE` | adults with no job | adults | `job == null && !isChild()` |
| `FOOD` | citizens below saturation | population | `getSaturation() <= LOW_SATURATION` |
| `MATERIAL` | stacks of one item unresolvable | open requests | player+retrying resolver requests, by item |
| `TOOL` | workers `STUCK` awaiting a tool | workers | `JobStatus.STUCK` + open `Tool` requests |
| `SECURITY` | guard slots below the ratio | workers | `getGuardFactor` inputs |
| `RESEARCH` | 1 per available unstarted research | 1 | `autoStartResearch` residue |

Note `WORKER` and `WORKPLACE` are deliberately two categories, not one: "we have huts nobody staffs"
and "we have people with nowhere to work" are opposite problems with opposite fixes (hire vs build),
and a colony can have both at once. `diagnose` already reports them as two separate lines
(`CommandColonyDiagnose.java:162-172`).

### The rank

```
urgency = clamp01( deficitRatio × normalisedWeight × ageBoost )

deficitRatio    = outstanding() / max(1, denominator)          // 0..1, per the table above
normalisedWeight= categoryWeight / 4.0                          // 4.0 = SECURITY, the max
ageBoost        = 1 + min(1, (now - firstSeen) / AGE_CAP)       // 1..2, AGE_CAP = 1 in-game day
```

`categoryWeight` is **read from the happiness modifier weights**, not invented:
SECURITY 4.0, HOUSING 3.0, FOOD 3.0, WORKPLACE 2.0, TOOL/WORKER 1.0, RESEARCH 0.5 (new, deliberately
lowest — research is never the thing to do when people are homeless).

Why this and not something cleverer: the mod has already priced the player-visible consequence of every
one of these shortages, in the weights it uses to compute happiness, and those weights have been balanced
across releases. Any formula I invent would be a second, unbalanced opinion about the same question.

**What makes housing beat tools this tick**: weight 3.0 vs 1.0, and a homeless ratio that is usually
larger than a stuck-worker ratio. But if 90 % of workers are toolless and 5 % homeless,
`0.9 × 0.25 = 0.225` beats `0.05 × 0.75 = 0.038` and tools win — which is correct, and is the reason the
ratio has to be in the formula rather than a fixed category order.

`ageBoost` is the deliveryman aging idea (`AbstractDeliverymanRequestable.java:96-101`, capped at
`MAX_AGING_PRIORITY`): a low-weight need that has gone unmet for a full day doubles, so nothing starves,
and — like the deliveryman's — it is capped so old low-priority needs never permanently outrank fresh
critical ones.

---

## 3. Where it lives and who updates it

**A new `ColonyNeedsManager`, hung off `Colony` alongside the existing 18 managers**
(`api/colony/IColony.java:49-469` lists them), exposed as `IColony#getNeedsManager()`. One new
`TickingTransition` in the `ACTIVE` state only, mirroring `tickRequests` (`Colony.java:421`).

**Pull on an interval, with a push-driven dirty flag that can only shorten the interval, never lengthen it.**

* **Not pure push**, because the discovery sites are demonstrably incomplete (§1.11): the event bus does
  not fire on every citizen add, and there is no event at all for "a tool broke" or "the restaurant ran
  out of menu items". A push-only model would drift silently, which is the exact failure the brief names.
* **Not pure periodic**, because the model would be laggy right after the events that matter most — a
  residence finishes and four citizens should stop being homeless.
* **So**: recompute every **600 ticks (30 s)** by default, deliberately co-prime-ish with `worldTickSlow`'s
  500 so the two rarely land together (and `TickingTransition`'s rotating offset,
  `TickingTransition.java:53`, spreads them further). `markDirty()` pulls the next recompute forward to
  the next colony tick, floored at **20 ticks** so a burst of building changes cannot cause a burst of
  recomputes.

`markDirty()` is called from a deliberately short list of places that already exist and already run on
exactly the state changes that matter:

| call site | why |
|---|---|
| `CitizenManager.calculateMaxCitizens()` (`:421`) | 10 existing callers, all the housing-relevant ones |
| `CitizenData.setJob(...)` (near `:1038`, where `CitizenJobChangedModEvent` is posted) | employment |
| `RegisteredStructureManager.onBuildingUpgradeComplete` | capacity |
| `WorkManager.addWorkOrder` / `removeWorkOrder` | in-flight build accounting |

That is four one-line insertions. Everything else is caught by the 30 s floor.

### What it costs

**Recompute = one pass over citizens + one pass over buildings, per 600 ticks.** At the 1000-citizen /
148-building fixture that is ~1150 iterations of cheap field reads (`isChild`, `getJob`, `getHomeBuilding`,
`getSaturation`, `getJobStatus`) plus a walk of open requests. Order of tens of microseconds, once every
30 seconds. **[assumed — not measured; the estimate is from the shape of the loop, not from a profile.]**

Hard rules, stated because the audit's lesson is that they get broken quietly:

1. **Nothing per-citizen-per-tick.** The model is a colony-level transition, never anything on
   `CitizenData.update` or the entity tick.
2. **Nothing nested over citizens.** Single pass, accumulate into counters. Any future need whose
   computation is O(N) *per need* is rejected at review.
3. **Iterate `getCitizensUnmodifiable()`** (`CitizenManager.java:500`), **never `getCitizens()`**
   (`:494`, `new ArrayList<>(citizens.values())`). `ICitizenManager.java:86-89` documents exactly this
   distinction, added by `OPT-FIXES.md §8`, and `AI-SCALE-AUDIT.md §3.4` confirms the copies left the
   profile. **Note in passing, not mine to change here:** `CitizenManager.tickCitizenData` (`:575`) and
   `CitizenManager.onColonyTick` (`:593`) still use the copying variant — 1000 references copied every
   60 and every 500 ticks respectively. Two one-line fixes, unrelated to this plan.
4. **Nothing touches the pathfinder.** The audit's finding is that the pool runs at 86 % of one core
   with 262 ms queue waits (`AI-SCALE-AUDIT.md §1.2`); the model must never issue a path query, and no
   consumer may treat "is this position reachable" as free. For the housing consumer this means siting
   must not be validated by pathfinding.

### What it saves

Once the snapshot exists, `getSocialModifier` (`CitizenHappinessHandler.java:222-258`) and
`getGuardFactor` (`:266-283`) become O(1) reads of it instead of full citizen loops. Both are invoked
once per citizen from `getHappiness` (`:145-152`), and all caches are dropped for all citizens at
nightfall (`Colony.java:671`). That removes an existing **2·N² burst per in-game day** — ~2 000 000
iterations at N=1000. The snapshot is at most one recompute-interval stale, which for a colony-wide
social/security average is not merely acceptable but arguably more correct than a value that changes
between two citizens in the same loop.

**Net effect on the tick budget is negative.** Given the audit's headline (32 ms/tick at 1000 citizens,
62 % of budget), a feature that only adds cost would be hard to justify; this one does not.

---

## 4. How a consumer acts, and how the need clears

Four calls, server thread only:

```java
List<ColonyNeed> peek(NeedCategory... categories);      // ranked, immutable snapshot
boolean          claim(NeedCategory c, Identifier subject, int units, int leaseTicks);
void             report(NeedCategory c, Identifier subject, int units, int leaseTicks); // renew
void             release(NeedCategory c, Identifier subject);
```

**A need is never closed by its consumer.** It ceases to exist because the next recompute measures the
world and does not find it. This is the whole point of a measured model: it cannot leak. The only
mutable state a consumer writes is `(units, expiry)` into a side map, and both decay on their own.

Three layers of re-trigger protection, each with an existing precedent in this codebase:

1. **`inFlight` subtraction.** `outstanding() = magnitude − inFlight`; a fully-claimed need has urgency 0
   and drops off the actionable list while remaining *visible* to the player, greyed, labelled "being
   handled". Precedent: `MinimumStockModule.java:132-147`.
2. **Leases, not ownership.** A claim is `(units, expiresAtGameTime)`. Nothing records *who*. If the
   consumer dies, is unloaded, or simply forgets, the lease lapses and the units come back. There is no
   orphan state to clean up and no "who owns this need" bookkeeping to get wrong. Consumers renew with
   `report` as they make progress, which is also the natural place to shrink `units` as work completes.
3. **Failure backoff, then escalate to the player.** Per `(category, subject)`: after **3** failed claims
   (a claim released without progress, or a lease that lapsed with `units` unchanged), the need's
   `resolution` flips to `PLAYER` and it is withheld from `peek` for **1200 ticks × attempt**. Numbers and
   shape taken from `StandardRetryingRequestResolver.java:32-33`. This is the mechanism that stops the
   colony from trying to site a residence on the same cliff face fifty-seven times.

The lease and backoff maps are the only persisted state (§6 Q1).

---

## 5. Player visibility and the off switch

**Read.** Three surfaces, cheapest first:

* `/mc colony needs <id>` — mirrors `/mc colony diagnose`, and `diagnose` itself is rewritten to render
  the model plus its existing structural checks (missing AI, orphaned work orders, inconsistent
  blueprint state) which are *defects*, not needs, and stay where they are.
* Town hall GUI: a list in the same widget as the existing happiness aggregate
  (`WindowCitizenPage.fillHappinessList`, `:217-275`) — same rows, same satisfied/unsatisfied icons, but
  with real magnitudes, an urgency bar, and a "being handled" state. **Unverifiable in this container:
  there is no display and `runClient` does not start (`ENV-26.2.md`).**
* Wire: serialize the **top 12** needs on the existing `updateSubscribers` path (20 ticks,
  `Colony.java:420`). 12 × ~20 bytes is negligible next to the per-citizen messages that
  `AI-SCALE-AUDIT.md §2.5` flags as entirely unmeasured — and I add nothing per citizen.

**Off.** Three levels, matching the precedent at `BuildingTownHall.java:76,81` exactly:

1. Server config `colonyneeds` (`ServerConfiguration`, `gameplay` category, default **on**). Off = the
   transition is never registered, the manager never allocates, the feature costs literally zero. Note
   this also forfeits the happiness O(N²) fix, so the config gates *automation and display*, while the
   snapshot itself stays unconditional — decide this explicitly at review.
2. Per-consumer town hall toggle (`AUTO_HOUSING_BUILD`, etc.), sibling to `AUTO_HIRING_MODE` /
   `AUTO_HOUSING_MODE`. The model still computes and displays; nothing acts.
3. Per-need dismiss, persisted: "stop offering this one". Lets a player veto "build another residence"
   without switching off the category.

Levels 1 and 2 are deliberately separable: "show me what's wrong" and "fix it for me" are different
requests, and the mod already draws that line at `AUTO_HIRING_MODE`.

---

## 6. Genuinely new vs. a rename of something existing

**Genuinely new**

* The `ColonyNeed` record, the cross-category `urgency`, and the ranked list.
* The claim/lease/backoff lifecycle.
* The colony aggregate snapshot as a first-class, once-per-interval object (which also deletes the
  existing O(N²), §3).
* The player-facing needs view and its off switches.

**Renames / reuse — everything else**

| the "new" thing | what it actually is | where |
|---|---|---|
| housing deficit | `getCurrentCitizenCount() − getMaxCitizens()`, already printed | `CitizenManager.java:506,537`; `CommandCitizenFill.java:93` |
| unemployment count | the loop in `getSocialModifier` and in `fill` | `CitizenHappinessHandler.java:236-238`; `CommandCitizenFill.java:100-107` |
| blocked workers | `isIdleAtJob()` / `JobStatus.STUCK` | `CitizenData.java:1745-1749`; `api/entity/ai/JobStatus.java` |
| unfilled job slots | the free-slots section | `CommandColonyDiagnose.java:134-149` |
| unmet materials | player-assigned + retrying requests | `CommandColonyDiagnose.java:348-349` |
| category weights | happiness modifier weights | `CitizenHappinessHandler.java:55-82` |
| anti-starvation aging | deliveryman priority aging | `AbstractDeliverymanRequestable.java:96-101` |
| delta/cancel discipline | minimum stock | `MinimumStockModule.java:115-150` |
| backoff → escalate to player | retrying resolver | `StandardRetryingRequestResolver.java:32-33` |
| automation on/off shape | town hall auto modes + `HiringMode` | `BuildingTownHall.java:76,81`; `AbstractAssignedCitizenModule.java:39` |
| "the colony decides for itself" | auto-start research | `ResearchManager.java:141-212` |
| what the colony *can* need | research effects, already used as gates | `IResearchEffectManager.java:17`; `WorkerBuildingModule.java:169` |

**My honest conclusion, as the brief invites: roughly 70 % of the content exists. The new work is the
shape, the rank and the lifecycle.** That is still worth doing, for one specific reason: three
prospective consumers (auto-housing, auto-research, auto-crafting) will each otherwise re-derive
"how many citizens are homeless" from their own loop, and the moment `calculateMaxCitizens`'s
work-at-home rule changes, two of the three will be wrong and nobody will notice. A single reader is
the point.

---

## 7. Staged path

**Stage 0 — useful on its own, no automation, no behaviour change.** ~700 new lines, ~120 changed.
`NeedCategory`, `Resolution`, `ColonyNeed`, `ColonySnapshot`, `ColonyNeedsManager`, the transition, five
categories (HOUSING, WORKER, WORKPLACE, FOOD, MATERIAL). `/mc colony needs`. `CommandColonyDiagnose`
rewritten to read the model (−~100 lines of duplicate counting). `getSocialModifier` / `getGuardFactor`
switched to the snapshot. **Deliverable: a real diagnostic and a net tick-cost reduction.** Nothing acts
on anything.

**Stage 1 — the list becomes actionable.** ~300 new lines + GUI/network. Claim/lease/backoff, persistence
of leases and dismissals, the town hall view, the config and settings toggles. Still no consumer.

**Stage 2 — first consumers.** Auto-housing (the parallel design) reads `peek(HOUSING)`. Then the
smallest possible second consumer to prove the shape generalises: extend `checkAutoStartResearch`
(`:141-212`) to prefer, among the researches it would already start, those whose effects address a
top-ranked need. Deliberately a *reordering* of an existing automatic behaviour, not a new one — the
cheapest possible test that the model is not housing-shaped.

**Stage 3 — material needs drive requests.** Colony-wide standing shortfalls (`"out of iron for three
days"`) raise the relevant building's minimum stock or create a request through `IBuilding#createRequest`.
Riskiest, because it writes into the request system; do it last and behind its own toggle.

**Stage 4 — "the colony runs itself".** An arbiter that spends one budget across categories, because at
that point building, crafting and researching all compete for the same builders and the same warehouse.
Do not attempt before Stage 3 has run in a real world; the arbiter is a balance problem, not a code
problem.

---

## 8. Open questions, with a recommendation for each

1. **Persist the needs, or recompute on load?**
   **Recommend: persist leases, backoff counters and dismissals; recompute the needs themselves.**
   Needs are measurements and must never survive the world that produced them; leases and dismissals are
   *decisions* and must. A claim in flight across a save is preserved; a stale need list is not.
2. **Per colony, or global?**
   **Recommend: per colony.** `IColony` already carries 18 managers. Alliances exist but there is no
   shared logistics, so no cross-colony need is meaningful.
3. **`JobStatus.STUCK` carries no reason.** ~30 AI sites set it (`EntityAIWorkNether`, `EntityAIWorkFarmer`,
   `EntityAIWorkPlanter`, …) with no indication of *what* is missing, so a `TOOL`/`WORKPLACE` need can say
   "12 workers stuck" but not why.
   **Recommend: accept it in Stage 0.** Add an optional reason `Identifier` to `setJobStatus` in Stage 2
   if a consumer actually needs the discrimination. Do not block on it — and do not substitute the
   interaction validators for it (see 7 below).
4. **Rank on the server or the client?**
   **Recommend: server.** Rank once, ship the ordered top-N. Client-side ranking would put the weights in
   two places and let two players see two different orders.
5. **`urgency` as float or as an int like deliveryman priority?**
   **Recommend: float internally, `int 0..10` on the wire and in the GUI**, to match
   `HappinessConstants.MAX_HAPPINESS = 10` which players already read fluently.
6. **What happens when the colony is `UNLOADED`?**
   **Recommend: do not tick** — register the transition on `ACTIVE` only, exactly as `tickRequests` does
   (`Colony.java:421`). Stamp the snapshot with its game time so a consumer can tell it is stale rather
   than trusting a frozen list.
7. **Should the ~40 interaction validators feed the model?**
   Tempting — they are the richest catalogue of named shortages in the mod (§1.5). But re-evaluating 40
   predicates × 1000 citizens is exactly the "per-citizen" cost that is dead on arrival.
   **Recommend: in Stage 1 only, and read the *already-materialised* `citizenChatOptions` map** rather
   than the predicates. Counting how many citizens currently *hold* each complaint id is
   O(citizens × complaints-held) ≈ O(N), and costs nothing extra because `CitizenData.update`
   (`:1690-1706`) already walks that map every 3 s to revalidate it. That turns "no restaurant",
   "no hospital", "no fuel", "quarrier with no quarry" into needs for free.
8. **Does the config gate the snapshot too, or only the needs?**
   **Recommend: gate only the needs and the automation; keep the snapshot unconditional**, because the
   snapshot is what removes the existing O(N²) and turning it off would be a performance regression
   disguised as an opt-out. Flag this explicitly at review — it is the one place where "off" is not
   literally zero-cost.

---

## 9. Coordination with the parallel auto-housing design

**Housing expresses cleanly, and richly enough.**

```
category  = HOUSING
subject   = minecolonies:residence
magnitude = getCurrentCitizenCount() − getMaxCitizens()             // beds missing
inFlight  = beds under construction: Σ over WorkOrderBuilding for residences of targetLevel
urgency   = clamp01( outstanding()/population × (3.0/4.0) × ageBoost )
resolution= BUILD
```

The loop, end to end, with nothing invented: the builder agent calls `peek(HOUSING)`, reads
`magnitude = 724`, claims 4 units (one level-4 residence) for 30 minutes, places a hut block and creates
a `WorkOrderBuilding` (`WorkOrderBuilding.create` requires an existing `IBuilding`, §1.10 — placement is
theirs), and calls `report` as the build advances. When the residence completes,
`LivingBuildingModule.onColonyTick` (`:69-89`) already auto-houses homeless citizens under
`AUTO_HOUSING_MODE`, `onAssignment` (`:92-96`) calls `calculateMaxCitizens`, that call marks the needs
model dirty, and the next recompute finds `magnitude` four lower. **No part of that loop requires the
housing feature to tell the model anything.** If the build is abandoned, the lease lapses and the units
come back with no cleanup.

**Should the two be one system? No — and I would argue against merging.**

1. The housing feature's hard part is **siting**: finding a legal, non-overlapping position inside the
   claim for a blueprint of known footprint, without asking the pathfinder (§3, rule 4). That is where
   nearly all of its risk lives and it has nothing to do with needs. Folding it in makes the needs
   manager own terrain analysis.
2. The needs model has to be right for consumers that will never build anything. A model shaped around
   one consumer is shaped wrong for the rest — and "shaped around its first consumer" is how the parallel
   models the brief warns about get created in the first place.

**What they must share is exactly one thing: the claim/lease protocol**, so the second consumer does not
fight the first over builders. My concrete recommendation to that agent: build the feature standalone
against a small local helper that counts homeless citizens and in-flight residence work orders — but give
that helper the **`peek` / `claim` / `report` signatures verbatim**. Then adopting the real manager is a
one-line substitution and nothing about the siting or build logic moves. If the needs plan is rejected,
they lose nothing.

---

## 10. Size and risk

**Size.** Stage 0: ~700 new lines across 5 files, ~120 changed in `Colony`, `IColony`,
`CitizenHappinessHandler`, `CommandColonyDiagnose`; one command; ~20 lang keys.
Stage 1: ~300 new + ~150 GUI/network + config + 2 town hall settings.
Stages 2-4 are consumer work, not model work.

**Risks, worst first.**

* **Drift — the brief's own warning.** Mitigation is structural, not disciplinary: every number is read
  from its existing owner and never recomputed. The one number I would most be tempted to recompute is
  the bed count, and I explicitly do not: `calculateMaxCitizens` (`:421-456`) handles `HiringMode.LOCKED`
  and the work-at-home case, and duplicating those rules is precisely the failure being guarded against.
  A review rule worth writing down: *a needs computation that does not call an existing accessor is a
  defect until proven otherwise.*
* **The urgency formula is a balance decision wearing code.** Reusing happiness weights means it is not
  invented, but it is still a knob that needs play testing, which this container cannot do.
* **`AUTO_HOUSING_MODE` interaction.** If auto-housing is off, homeless citizens are homeless *by player
  choice* and the HOUSING need is noise. The model must read that setting and suppress the need, or the
  first thing players see is a permanent red bar they asked for. Easy, but easy to forget.
* **Lease across a restart.** Persisting leases (Q1) means a consumer that does not survive a restart —
  an AI in a state — leaves a lease that must lapse. It will, within `leaseTicks`, but the window is
  visible to players as "being handled" when nothing is.
* **Client is unverifiable here.** Anything in Stage 1's GUI is untested: no display, `runClient` does not
  start. Say so in the eventual report rather than implying otherwise.
* **`colony-1000.zip` is the wrong validation fixture for this feature specifically.** 871 unemployed,
  724 homeless, no blueprints, two job types with no building (`testworlds/README.md:37-42`). It will
  produce enormous magnitudes and prove nothing about ranking or about the clearing loop. It is however
  the *right* fixture for the cost claim in §3, and the only one available for that.
* **Stage 3 writes into the request system.** Every other stage only reads. Keep it behind its own toggle
  and treat any regression in deliveryman behaviour as attributable to it by default.

---

## 11. What I verified, and what I did not

**Verified by reading the code**, this session: the `diagnose` / `fill` split and every number each
prints; the request system's states, priority scope, aging call site, retry constants and player
escalation; `MinimumStockModule`'s delta/cancel loop; the happiness modifier set, weights, factor
functions and the nightfall invalidation path; the ~40 interaction validators and where they are
re-evaluated; `calculateMaxCitizens` and its ten callers; `LivingBuildingModule` / `WorkerBuildingModule`
auto-assignment and their town hall toggles; the colony state machine's transitions and intervals;
`WorkOrderBuilding.create` requiring an existing building; `WorkManager.onColonyTick`'s
O(orders × buildings) shape; the event bus's incompleteness; `ResearchManager.checkAutoStartResearch`;
the client-side happiness aggregation in `WindowCitizenPage`.

**Not verified — assumptions stated as such.** I did not build, boot a server, or measure anything: this
was a design task and the time went to reading. Specifically unmeasured: the recompute's actual cost
(§3, estimated from loop shape); the size and real-world impact of the 2·N² happiness burst (the code
path is verified, its cost is not, and the audit's stand had zero subscribers so it would not appear in
that profile either); whether serializing 12 needs per second is genuinely negligible against the
subscriber-serialisation branch that `AI-SCALE-AUDIT.md §2.5` says was never measured at all.

**Where the brief and the code disagree**, both noted above: (a) bed and job-type-coverage numbers live
in `/mc citizens fill`, not `/mc colony diagnose`; (b) `BuildingResourcesModule` is not a general
"building declares what it wants" mechanism — it is the builder's current-work-order resource list, and
the general standing-demand mechanism is `MinimumStockModule`.
