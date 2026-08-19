# More than one courier per Courier's Hut

Study **and** implementation. Date: 2026-08-15. Tree: `26.2/` in this repo, compared against the
NeoForge original at `1.21.1/` and upstream MineColonies at `/workspace/ldtteam/minecolonies`
(`2d453335`).

Evidence standard, same as the other studies in this directory:

* **[VERIFIED]** — I read the source, the `file:line` is real and says what I claim it says.
* **[SERVER]** — observed on a running dedicated server in this container, with the log line quoted.
* **[UNCHECKED]** — inference from the code, not observed. **There is no game client here**, so
  nothing about the hut GUI was seen with human eyes; those claims are marked.

All paths are relative to the repository root.
`26.2/src/main/java/com/minecolonies/` is abbreviated to `mc/` where it would crowd the line.

---

## 0. The answer

**Yes, it can, and yes, it should — the change is one expression plus two small bug fixes, and it is
built and committed on this branch.**

The thing worth knowing before anything else: **there were never two limits fighting each other.**
The warehouse was *always* willing to take ten couriers. The hut's constant `1` was the only thing
stopping you. Raising it does not run into a second ceiling — it uses headroom that has been sitting
there unused in every colony ever played.

Here is that headroom, from a real server, in a colony with 148 huts and 870 unemployed adults,
before anything was changed:

```
warehouse5 deliveryman 1/10 at -20, 73, -30
```

One courier assigned. Ten slots. 870 idle citizens standing around who could fill the other nine and
cannot, because the single Courier's Hut is full at 1/1. **[SERVER]**

### What this branch ships

| | Was | Now | Function of | Where |
|---|---|---|---|---|
| **Courier's Hut** | **1**, a constant | **hut level** — 5 at level 5 | nothing → hut level | `mc/core/colony/buildings/modules/BuildingModules.java:399` |
| **Warehouse** | `buildingLevel * 2` — 10 at level 5 | `buildingLevel * 4` — **20** at level 5 | warehouse level | `mc/core/colony/buildings/modules/CourierAssignmentModule.java:110` |

**The hut is the limit that bit.** Always was — the warehouse's ten was roughly double what any player
could actually supply, because supplying it meant building that many separate huts.

Raising the warehouse to twenty (section 9) deliberately re-opens that gap rather than closing it, and
it preserves the property the hut curve was chosen for: **the warehouse sits a constant four huts
ahead of the hut at every level.** Four level-5 huts (5 couriers each) fill one level-5 warehouse.
Four level-2 huts fill one level-2 warehouse. The ratio never moves.

---

## 1. The rule as it stood

### 1.1 The hut

```java
new BuildingEntry.ModuleProducer<>("courier_work",
    () -> new DeliverymanAssignmentModule(ModJobs.delivery.get(), Skill.Agility, Skill.Adaptability, false, (b) -> 1),
  () -> WorkerBuildingModuleView::new);
```

That last argument is the module's size limit. `WorkerBuildingModule` stores it as
`Function<IBuilding, Integer> sizeLimit` and returns it straight out of `getModuleMax()`
(`mc/core/colony/buildings/modules/WorkerBuildingModule.java`), which
`AbstractAssignedCitizenModule.isFull()` compares against `assignedCitizen.size()`
(`AbstractAssignedCitizenModule.java:107-110`). A constant `1` therefore means one courier, at every
hut level, forever. **[VERIFIED]**

### 1.2 The warehouse

```java
@Override
public int getModuleMax()
{
    return this.building.getBuildingLevel() * 2;
}
```

`CourierAssignmentModule` is a *different* module on a *different* building. It does not create
couriers — it adopts existing ones. Its `onColonyTick` scans the colony for any citizen whose job is
`JobDeliveryman` and whose `findWareHouse()` is null, and attaches them
(`CourierAssignmentModule.java:25-41`). **[VERIFIED]**

### 1.3 How the two compose

They are in series, and the hut is upstream of the warehouse:

1. The **hut** is what turns a citizen into a courier. Nothing else creates a `JobDeliveryman`.
2. The **warehouse** then adopts couriers that exist and have no warehouse yet.

So the reachable number of couriers is `min(sum of all hut capacities, sum of all warehouse
capacities)`. With hut capacity fixed at 1, the left term equals *the number of courier huts you
built*, and the right term was 10 per level-5 warehouse. The left term is the one you hit.

This branch raises both terms — the left to 5 per level-5 hut, the right to 20 per level-5 warehouse
— and keeps the left below the right at every level, so the hut stays the meaningful constraint and
the warehouse stays headroom rather than becoming a new wall.

---

## 2. Why it was `1`

**Inherited, not a port decision.** The line is byte-identical in all three trees:

| Tree | Line | Text |
|---|---|---|
| `26.2/` (before this change) | `BuildingModules.java:391` | `..., false, (b) -> 1),` |
| `1.21.1/` (NeoForge original) | `BuildingModules.java:389` | `..., false, (b) -> 1),` |
| upstream `2d453335` | `BuildingModules.java:389` | `..., false, (b) -> 1),` |

Same for the warehouse's `buildingLevel * 2`, before section 9 changed it (`:102` here pre-change,
`:101` in `1.21.1`, `:100` upstream).
**[VERIFIED]** There was no `// PORT-NOTE(26.2):` on either, correctly — the port had not touched them.

And it is *deliberate* upstream design, not an oversight. The proof is a shipped research:

```java
new Research(..., "civilian/moq", TECH).setParentResearch(memoryAid)
  .setTranslatedName("Minimum Order Quantity")
  .addBuildingRequirement(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.DELIVERYMAN_ID), 9)
```

`mc/core/generation/defaults/DefaultResearchProvider.java:1670`. A Courier's Hut maxes out at level 5
(`BuildingDeliveryman.getMaxBuildingLevel()` returns `CONST_DEFAULT_MAX_BUILDING_LEVEL`). A
requirement of **9** is only satisfiable because `ICommonRegisteredStructureManager.hasBuilding`
*sums levels across buildings* when `singleBuilding` is false
(`mc/api/colony/managers/interfaces/ICommonRegisteredStructureManager.java:208-231`). **[VERIFIED]**

In other words: upstream ships a research that **cannot be unlocked without building at least two
Courier's Huts**. "One courier per hut, build more huts" is the intended shape of the game.

That matters for section 5 — it is the one real gameplay casualty of this change.

---

## 3. What raising it actually does

This is the part I expected to kill the idea, and it did not.

### 3.1 The request system: it was already built for this

The critical realisation is that **N couriers sharing one warehouse is not a new situation.** It is
what every mid-game colony already does — it just spells it "N courier huts". The request system has
been handling it for years.

The flow:

1. A building needs something. Its request is resolved by a `DeliverymenRequestResolver`, which does
   not assign to a courier at all — it drops the token into a **shared per-warehouse queue**:
   ```java
   final WarehouseRequestQueueModule module = wareHouse.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
   module.addRequest(request.getId());
   ```
   `mc/core/colony/requestsystem/resolvers/DeliverymenRequestResolver.java:102-118`. **[VERIFIED]**

2. Each courier, when idle, calls `JobDeliveryman.getCurrentTask()`. It scores every token in the
   shared list, picks the best, and then — the important bit — **removes it from the shared list and
   puts it in its own private data store**:
   ```java
   getTaskQueueFromDataStore().add(resultRequestId);
   ...
   wareHouseModule.getMutableRequestList().removeAll(reqsToRemove);
   ```
   `mc/core/colony/jobs/JobDeliveryman.java:243-318`. **[VERIFIED]**

That is a claim, and it is atomic with respect to every other courier, because all of this runs on
the server thread inside the citizen's AI tick. **Two couriers cannot take the same request**: the
second one to run finds the token already gone from `getMutableRequestList()`. No collision, no
double-delivery, no fighting.

Each courier's queue is genuinely its own — `JobDeliveryman` allocates a private
`REQUEST_SYSTEM_DELIVERY_MAN_JOB_DATA_STORE` per citizen in `setupRsDataStore()`
(`JobDeliveryman.java:80-90`), and releases it in `onRemoval()`. **[VERIFIED]**

There is even a fairness mechanism already in place: requests the courier *skipped* get
`incrementPriorityDueToAging()` (`JobDeliveryman.java:285-290`), so a request repeatedly passed over
by a busy courier climbs the queue until somebody takes it. **[VERIFIED]**

**Nothing in the request system knows or cares which building a courier sleeps in.** It only asks
`findWareHouse()`, which searches by warehouse assignment, not by hut
(`JobDeliveryman.java:575-586`). **[VERIFIED]**

### 3.2 The hut itself: nothing to collide over

The Courier's Hut has no bed, no bunk, and no per-worker capacity of any kind. Its registration is
three modules and none of them is spatial:

```java
.addBuildingModuleProducer(COURIER_WORK)
.addBuildingModuleProducer(COURIER_TASK_VIEW)
.addBuildingModuleProducer(STATS_MODULE)
```

`mc/apiimp/initializer/ModBuildingsInitializer.java:195-203`. **[VERIFIED]** No `BED` module — beds
belong to houses, and the courier's bed is wherever he lives, not where he works.

Nor do couriers gather at the hut. When idle they walk to the **warehouse**, not to their hut:
`decide()` calls `walkToBuilding(getAndCheckWareHouse())` (`EntityAIWorkDeliveryman.java:600-622`),
and `dump()` unloads into the warehouse (`:297-315`). **[VERIFIED]** A courier's working day barely
involves the hut at all.

The AI's own mutable state (`currentSlot`, `alreadyKept`) lives on the `EntityAIWorkDeliveryman`
instance, one per citizen, not on the building. **[VERIFIED]**

The one thing the hut *does* contribute is shared-by-design and scales the right way: carrying
capacity is `2^(level-1) + 1` stacks, unlimited at level 5, read from the **building** level
(`EntityAIWorkDeliveryman.java:265-277`). Every courier in the hut gets the same allowance, which is
correct — it is the hut's storage, not the man's. **[VERIFIED]**

### 3.3 The two places that did assume one courier

I found exactly two, both fixed in the implementation commit.

**`JobDeliveryman.getMaxParallelDeliveries()`** — read slot 0 of the assignee list:

```java
return 1 + (getWorkModule().getAssignedCitizen().get(0).getCitizenSkillHandler()
              .getLevel(((WorkerBuildingModule) getWorkModule()).getSecondarySkill()) / 5);
```

While the hut holds one courier, "slot 0" and "me" are the same citizen. With five, every courier in
the hut would inherit the parallel-delivery throughput of whoever happened to be listed first — a
fresh hire would get a veteran's capacity, and the veteran would lose his own if hired second. The
value feeds the loop bound at `JobDeliveryman.java:306`, so this is a real throughput bug, not
cosmetic. Fixed to read `getCitizen()`. **[VERIFIED]**

**`BuildingDeliveryman.canEat()`** — read `getFirstCitizen()` only. `canEat` is consulted per citizen
via `FoodUtils.canEat(stack, homeBuilding, workBuilding)` (`mc/api/util/FoodUtils.java:47-55`), so
couriers 2..n were being judged against courier 1's cargo, and would happily eat the stack they were
carrying to another building. **[VERIFIED]**

That one came with a bonus. The upstream implementation asked through `job.getCurrentTask()` — which,
per 3.1, is **not** the read-only accessor its name implies: on an empty private queue it *claims* a
request off the shared warehouse queue. Calling it once per courier on every food check would hand
out delivery work as a side effect of a citizen wondering about lunch. The fix reads the
already-claimed queue directly, which is side-effect free and is the courier's actual cargo anyway.
**[VERIFIED]**

### 3.4 The colony's books: untouched

Worker huts do not raise the population cap. `CitizenManager.calculateMaxCitizens()` counts only
buildings that have a `BED` module with a `WorkAtHomeBuildingModule`, or a `LivingBuildingModule`
(`mc/core/colony/managers/CitizenManager.java:443-471`). The Courier's Hut has neither. **[VERIFIED]**

So five couriers still need five beds in five houses, and still count against `maxcitizenpercolony`
like anyone else. **The change buys jobs, not people.** That is the right constraint to leave in
place — it means the hut cannot conjure population, only employ population you already housed.

The town hall's citizen and job listings are driven off the same per-citizen data and need no
changes.

### 3.5 The GUI: already generic

No GUI or lang work was needed, which surprised me until I looked:

* `AbstractAssignedCitizenModule.serializeToView` already writes `getModuleMax()` to the client
  (`:150-165`), so the hut window reads its capacity dynamically rather than assuming 1. **[VERIFIED]**
* `CourierRequestTaskModuleView.getTasks()` **already loops over every assigned citizen** and
  concatenates their queues (`mc/core/colony/buildings/moduleviews/CourierRequestTaskModuleView.java`).
  It was written for multiple couriers before there could be any. **[VERIFIED]**
* The precedent is load-bearing: `LivingBuildingModule.getModuleMax()` is exactly
  `building.getBuildingLevel()` (`:127-130`), the school's student module is `2 * b.getBuildingLevel()`
  (`BuildingModules.java:419`), and the cavalry stable passes `ICommonBuilding::getBuildingLevel`
  (`:570`) through the **same** `WorkerBuildingModuleView` the courier hut uses. **[VERIFIED]**

A worker list that can show five workers is therefore not new code — it is the same list the school
has been showing ten pupils in. **[UNCHECKED]** that it *looks* right, because there is no client here.

### 3.6 Balance: what it does to the game

This is the real cost, and it is a design cost, not a technical one.

**Before:** five couriers = five huts = five build orders, five sets of materials, five plots of land,
and five separate level-5 upgrade paths. Logistics throughput was gated by *construction effort and
town space*, which is a meaningful mid-game tax.

**After:** five couriers = one hut at level 5. The material cost of the 2nd–5th courier drops to
zero; only the hut's own upgrade path remains.

That is a **large** logistics buff, and it should be described honestly as one. Concretely:

* Delivery latency across the colony falls sharply. Couriers are the single most common bottleneck in
  a growing colony — every builder, every crafter, every restocking hut waits on them.
* The `WarehouseRequestQueueModule` backlog drains roughly five times faster at a level-5 hut, so the
  "my builder is stuck waiting for materials" complaint largely goes away.
* It removes a genuine spatial decision from town planning. Some players like that decision.

Two things keep it from being a free win, and both are pre-existing mechanics rather than anything I
added:

1. **Beds.** Five couriers need five houses' worth of population (3.4). In a tight colony that is the
   binding cost.
2. **The warehouse still has to keep up.** At the shipped `4 * level` a warehouse supports five
   couriers from level 2 onward, so this is now only reachable with a level-1 warehouse. Such a
   warehouse beside a level-5 hut hires five couriers and can attach four; the fifth gets
   `findWareHouse() == null`, `checkIfExecute()` returns false, and the player gets a **blocking chat
   interaction** telling them so (`COM_MINECOLONIES_COREMOD_JOB_DELIVERYMAN_NOWAREHOUSE`,
   `EntityAIWorkDeliveryman.java:647-670`). They idle politely and complain; nothing breaks.
   **[VERIFIED]**, and section 9.3 provokes it deliberately at the top end. Under the original
   `2 * level` this bit much harder — five couriers needed a level-3 warehouse.

Point 2 is why I chose `getBuildingLevel()` and not something steeper — see section 6.

---

## 4. What it cost to build

Five files across two commits, and both headline changes are one expression each.

**The hut (commit 1):**

| File | Lines | What |
|---|---|---|
| `mc/core/colony/buildings/modules/BuildingModules.java` | 1 changed (+8 comment) | `(b) -> 1` → `ICommonBuilding::getBuildingLevel` |
| `mc/core/colony/jobs/JobDeliveryman.java` | ~7 changed (+5 comment) | `getMaxParallelDeliveries` reads this citizen |
| `mc/core/colony/buildings/workerbuildings/BuildingDeliveryman.java` | ~20 changed (+9 comment) | `canEat` checks all couriers, without the claim side effect |

**The warehouse (commit 2, section 9):**

| File | Lines | What |
|---|---|---|
| `mc/core/colony/buildings/modules/CourierAssignmentModule.java` | 1 changed (+6 comment), 1 import dropped | `* 2` → `* 4`; redundant `new ArrayList<>` removed from the tick loop |
| `mc/core/colony/requestsystem/resolvers/DeliverymenRequestResolver.java` | 2 changed (+5 comment) | `!getAssignedCitizen().isEmpty()` → `hasAssignedCitizen()`, twice |

Everything else is **zero**:

* **No new lang keys.** Nothing new is displayed. The one relevant string
  (`...JOB_DELIVERYMAN_NOWAREHOUSE`) already exists and already fires. No `runDatagen` needed.
* **No GUI work** (3.5).
* **No save-format change.** `WorkerBuildingModule.serializeNBT` already writes
  `TAG_WORKING_RESIDENTS` as an `int[]` of every assigned citizen and reads it back the same way
  (`WorkerBuildingModule.java:123-163, 180-192`). One entry or five is the same format. **[VERIFIED]**
* **No mixins.** Nothing here needs bytecode access.

### Old saves

**Loading an old save is a no-op**: an existing hut has one courier in its `int[]`, deserialises to
one courier, and simply stops being full — auto-hire picks up the slack on the next colony tick if
hiring mode allows. **[VERIFIED]**

**Going back** (reverting the branch, or downgrading a hut) is also safe, and worth stating precisely
because it is the one asymmetric direction. `deserializeNBT` re-assigns through `assignCitizen`,
which respects `isFull()` (`AbstractAssignedCitizenModule.java:56-64`), so surplus couriers are simply
not re-assigned: they keep their `JobDeliveryman` but end up with no work building, and turn up in
`/mc colony diagnose` under "citizens with a job but no work building". Nothing crashes and nothing is
lost — the citizens are still there and can be re-hired elsewhere.

Note this is **not new behaviour I introduced**. No `AbstractAssignedCitizenModule` trims its list
when `getModuleMax()` shrinks — the school (`2 * level`) and every house (`level`) have always
behaved this way on downgrade. The courier hut now shares the existing pattern rather than a new one.
**[VERIFIED]**

---

## 5. The alternatives

### "Just build a second Courier's Hut" — the honest answer, and why it is not enough

This *is* the intended answer, and section 2 proves it with the Minimum Order Quantity research. It
also costs nothing to implement. So why go further?

Because the objection to it is not "it doesn't work", it is **"then why did the warehouse count to
ten?"** A level-5 warehouse advertising 10 courier slots, in a game where filling them means ten
separate buildings, is a number almost no colony ever reaches. The `1/10` in the server log is the
whole argument in one line: the game already has the ambition, and only the hut's constant was
holding it back.

The second-hut answer is also weakest exactly where the request is strongest. It is fine going from
one courier to two. It is a poor answer at five, where you are placing five near-identical buildings
whose only purpose is to be a job slot.

### Is there a config option or research that already does this?

**No.** I checked and found nothing:

* No courier-count config. `grep -i "courier\|deliveryman\|dman"` across
  `mc/api/configuration/*.java` returns nothing. **[VERIFIED]**
* No research effect raises courier counts. The only two researches that mention the Courier's Hut
  use it as a *requirement*, not a target: `civilian/rails` (level 3) and `civilian/moq` (level 9),
  `DefaultResearchProvider.java:424` and `:1670`. **[VERIFIED]**
* `WorkerBuildingModule` does support a `researchRequirement` constructor parameter that gates a
  module on a research (`WorkerBuildingModule.java:83-95`), but it is a **boolean gate on the whole
  module**, not a capacity multiplier — it cannot express "+1 courier per research". Wiring capacity
  to research would be a genuinely bigger change than the one I made. **[VERIFIED]**

So the question was not moot. Nothing existing raises it.

### The casualty: Minimum Order Quantity

Worth flagging plainly, because it is the one place this change makes the game slightly worse.

MoQ needs **summed** Courier's Hut levels ≥ 9, so it still needs two huts (2). Before this change,
every player built several huts anyway and tripped over the research naturally. After it, a player
who builds one level-5 hut has all the couriers they need and **no reason to ever build a second** —
and will quietly never unlock MoQ, with the research screen telling them to build a Courier's Hut they
already have.

The requirement is data-driven (`DefaultResearchProvider`), so it is a one-line fix if he wants it:
change `9` to `5`, or to a `singleBuilding` requirement. I did **not** change it, because it is a
balance call on his game and not part of what was asked. It is the first thing I would look at if he
takes this branch.

---

## 6. Why `getBuildingLevel()` and not something else

He asked about five at level five, i.e. one per level. I looked for a reason to argue and did not
find one:

* **It is the curve upstream already uses for "how many people belong in this building"** —
  `LivingBuildingModule.getModuleMax()` is exactly `building.getBuildingLevel()`. Using the same shape
  keeps the hut legible to a player who already understands houses. **[VERIFIED]**
* **It stays under the warehouse's curve at every level.** Against the original `2 * level` that was
  1/2, 2/4, 3/6, 4/8, 5/10; against the `4 * level` this branch now ships (section 9) it is
  1/4, 2/8, 3/12, 4/16, 5/20. Either way an equally-levelled warehouse is never the bottleneck, so
  the feature does not silently half-work. A steeper hut curve (say `2 * level`) would have put hut
  and warehouse in exact lockstep under the old warehouse number and made *any* warehouse lag produce
  idle couriers and blocking chat spam.
* **Level 1 is unchanged at 1 courier**, so early game plays exactly as before and nothing about the
  starting experience shifts.
* It scales with the same building level that already governs carrying capacity (3.2), so a hut that
  can hold more couriers is also a hut whose couriers carry more — the upgrade reads as one coherent
  improvement rather than two unrelated ones.

The alternative worth a sentence is `(level + 1) / 2` → 1, 1, 2, 2, 3, which would be the
conservative choice if the balance worry in 3.6 is decisive. It is strictly milder and a one-token
change if he wants it. I did not pick it because it answers a question he did not ask.

---

## 7. What was verified how

**On a running dedicated server** (port 25953, `testworlds/colony-1000.zip`, 999 citizens, 148 huts):

* Baseline, before the change: exactly one courier in the colony, and
  `warehouse5 deliveryman 1/10` — the warehouse advertising nine unfillable slots. **[SERVER]**
* `/mc citizens fill 1` on a colony with **870 unemployed adults** produced **no** second courier and
  left the warehouse at `1/10`. This is the clearest possible demonstration that the hut, not the
  warehouse, was the binding constraint. **[SERVER]**
* After the hut change, same world, same command: **five couriers from the one hut**, all five adopted
  by the warehouse (`5/10`), all five in `START_WORKING`, `No problems found`, and zero new errors.
  Section 8 has the log lines.
* After the warehouse change, world re-extracted pristine: hut `5/5`, warehouse `5/20`, job slots
  `347` decomposing exactly as `333 + 4 + 10`. Section 9.3. **[SERVER]**
* **At the warehouse's full new width** — via a throwaway instrumented jar putting 25 couriers in one
  hut, reverted before commit — **25 couriers offered, exactly 20 accepted**, warehouse full, five
  surplus idling gracefully, `No problems found`, zero new errors. Section 9.3. **[SERVER]**

The middle one is the important result, and it is the thing I most expected to fail. **Five couriers
sharing one warehouse queue do not collide, double-deliver, or strand each other**, and the colony's
own diagnostic — which explicitly hunts for orphaned jobs, unresolved requests and stuck AI — finds
nothing to complain about. The last one says the same thing at four times the width.

**By reading source**: everything marked [VERIFIED], against `26.2/`, `1.21.1/` and upstream
`2d453335`.

**Not verified, and I want to be explicit about it:**

* **Nothing was seen in a GUI.** There is no client in this container and `runClient` does not start.
  Two claims rest on source reading alone: that the hut window will list five couriers (3.5,
  from `getModuleMax()` being serialised plus the school/cavalry-stable precedent), and that the
  warehouse's ten-row courier pane **scrolls** rather than truncating at twenty (9.2, from BlockUI
  registering `list` as a `ScrollingList` with a scrollbar). Both are strong inferences, neither is an
  observation. **These two are what to eyeball before shipping.**
* **No long-run balance data.** I did not run a colony for a simulated week to measure how much faster
  the request queue actually drains with five couriers. Section 3.6 argues from mechanism, not
  measurement. The five couriers were seen *running*, not seen *racing each other for the same
  request* — with 0 open requests in the fixture there was no contention to observe. The
  no-collision claim in 3.1 therefore still rests on the source: the claim is a list removal on the
  server thread.
* **The idle-courier path** (3.6) is now **partly** verified: section 9.3 stranded five couriers by
  over-supplying the warehouse and they idled in `START_WORKING` without crashing. What is still
  unobserved is the player-facing half — the blocking `...NOWAREHOUSE` chat interaction — because
  that needs a client. Note also that `/mc colony diagnose` did **not** flag the five, so the log is
  not a substitute for that interaction.

---

## 8. The change, running

> **This section records the run made after the hut change but *before* the warehouse was raised**,
> so the warehouse still reads `/10` throughout. It is kept as-is rather than restated, because it is
> the evidence that the hut change works *on its own* — useful if the warehouse half is ever backed
> out. Section 9.3 has the numbers at the shipped `/20`.

Same world, same colony, jar rebuilt from this branch. The line that was absent before — because a
1/1 hut is full and full huts are not listed as having free slots — now appears:

```
deliveryman5 deliveryman 1/5 at -30, 73, -30
warehouse5 deliveryman 1/10 at -20, 73, -30
```

**[SERVER]** The level-5 Courier's Hut advertises five slots, one filled. The warehouse still reads
`1/10`, which is the point of section 1.3: the hut's four new slots are drawn from headroom the
warehouse already had.

### Five couriers, one hut, actually working

`/mc citizens fill 1` then filled it. The result is the whole feature in three lines:

```
#4   Itzayana U. Grey   [deliveryman] state=START_WORKING held=48s  status=IDLE
#190 Corinne G. Corbett [deliveryman] state=START_WORKING held=new  status=IDLE
#335 Jayde I. Cobham    [deliveryman] state=START_WORKING held=new  status=IDLE
#479 David V. Garret    [deliveryman] state=START_WORKING held=new  status=IDLE
#500 Hattie M. Gaur     [deliveryman] state=START_WORKING held=new  status=IDLE
```

```
warehouse5 deliveryman 5/10 at -20, 73, -30
```

**[SERVER]** Five couriers, from **one** Courier's Hut. The hut itself has dropped off the "job slots
with no worker" list entirely — it is full at 5/5. All five are in `START_WORKING`, i.e. running the
normal courier AI, not stuck.

And critically, **all five were adopted by the warehouse automatically**: `5/10`, up from `1/10`, with
no player action. That is `CourierAssignmentModule.onColonyTick` doing exactly what section 1.2
describes — it never cared which hut a courier came from.

Supporting numbers from the same run, all **[SERVER]**:

| | Before | After |
|---|---|---|
| Employed citizens | 129 | 133 (+4) |
| Colony job slots | 130/**333** | 138/**337** (+4 slots) |
| Warehouse couriers | 1/10 | 5/10 |
| Diagnose verdict | `No problems found` | `No problems found` |

The `+4` on total job slots is the hut's new capacity and nothing else — no other building's numbers
moved.

`No problems found` is the load-bearing one: `/mc colony diagnose` specifically looks for citizens
with a job but no work building, requests no resolver took, and stuck AI states. **With five couriers
sharing one warehouse queue it reports none of them.** Open requests stayed at 0 and no courier
ended up orphaned.

Boot is clean: **17 errors, all of them `Error loading blueprint`** — precisely the seventeen
documented in [`testworlds/README.md`](../../testworlds/README.md) as this fixture world's own fault,
present on the unmodified jar too. **Zero new errors, and no exception anywhere mentioning a courier,
warehouse or request.** **[SERVER]**

### Two operational notes for whoever re-runs this

**Build lock.** My first `gradle build` was invoked directly before I had read
`/home/user/ENV-26.2.md`. It completed green and the Loom cache was undamaged, but it was wrong: with
several agents in one container, every build must go through `/home/user/mc-build.sh <projectdir>
<task>`, which serialises them. Every build after that one did. Nothing in this branch is suspect as
a result — the artifact tested is from a locked build — but do not copy the first invocation.

**Memory.** A run with `-Xmx4G` was killed by the OS partway through `/mc citizens fill 1` on the
999-citizen fixture. That is the container being shared, not a defect: `testworlds/README.md` already
says this world needs **6 GB**, and the 6 GB run was stable. `citizens fill` on a colony this size is
a heavy hammer in any case — the hut's own auto-hire (`WorkerBuildingModule.onColonyTick`) fills empty
slots from the 870 unemployed adults without it.

---

## 9. Raising the warehouse to twenty

Added after the first draft, at the owner's request. **The hut is unchanged** — still one courier per
hut level, five at five. Only the warehouse moved.

### 9.1 The expression, and why this one

```java
return this.building.getBuildingLevel() * 4;
```

`CourierAssignmentModule.java:110-113`. Four, eight, twelve, sixteen, **twenty**.

`* 4` is the obvious reading of "twenty at the top" and I kept it, but the reason is not that it is
obvious — it is that **a linear multiple is the only shape that preserves the ratio to the hut**.
The hut is `getBuildingLevel()`. A constant multiplier here means the warehouse sits a fixed *four
huts* ahead of the hut curve at every tier:

| Level | Hut | Warehouse | Huts to fill one warehouse |
|---|---|---|---|
| 1 | 1 | 4 | 4 |
| 2 | 2 | 8 | 4 |
| 3 | 3 | 12 | 4 |
| 4 | 4 | 16 | 4 |
| 5 | **5** | **20** | **4** |

That "4" column never moving is the whole point. It is the same property that made me pick
`getBuildingLevel()` for the hut in section 6, just with far more room.

The tempting alternative, `level * level`, overshoots to 25 at level five and looks like it also
"reaches twenty" — but it collapses at the bottom: **1** at level one, down from today's 2. That
exactly *equals* the hut's 1 rather than staying above it, so a level-1 warehouse would have zero
headroom and a second level-1 hut would immediately strand its courier. The ratio column above would
read 1, 2, 3, 4, 5 instead of a flat 4 — the invariant the linear form exists to protect. Rejected.

### 9.2 What twenty exposes that ten never did

Ten was never reachable — it needed ten huts — so the per-warehouse loops have never actually run
near their stated width. Two were paying for that width needlessly. Both are fixed in the same commit.

**`DeliverymenRequestResolver.hasCouriers()`** tested emptiness like this:

```java
return !wareHouse.getModule(BuildingModules.WAREHOUSE_COURIERS).getAssignedCitizen().isEmpty();
```

`getAssignedCitizen()` is **not** an accessor — it returns `new ArrayList<>(assignedCitizen)`
(`AbstractAssignedCitizenModule.java:91-94`). So this allocated a courier-sized list on every call
purely to ask whether it was empty, and it sits on the **request-resolution path**: both
`canResolveRequest` and `attemptResolveRequest` reach it for every request against every candidate
resolver. That is far hotter than any tick loop, and the allocation grew with the cap. Replaced with
the existing allocation-free `hasAssignedCitizen()` (`:126-129`). Same edit in `resolveRequest`.
**[VERIFIED]**

**`CourierAssignmentModule.onColonyTick()`** wrapped that already-copied list in *another* copy:

```java
for (final ICitizenData citizenData : new ArrayList<>(getAssignedCitizen()))
```

A copy of a copy, in the one loop in this class that runs at the module's full width on every colony
tick. Honest sizing, because it would be easy to oversell this: `worldTickSlow()` runs every **500
ticks — 25 seconds** (`Colony.java:582-590`), so even at twenty couriers this was *two* short-lived
20-element lists per warehouse every 25 seconds where one would do. That is nothing. I removed it
because it is provably redundant and free to remove, not because it was costing anything measurable
— and the file already carries a comment two lines up showing the author cared about exactly this
kind of waste in the loop above it. **[VERIFIED]**

### The two earlier fixes, re-examined at twenty

Both are unaffected, and for a reason worth stating:

* **`BuildingDeliveryman.canEat()`** iterates `COURIER_WORK` — the **hut's** module, capped at 5 —
  not `WAREHOUSE_COURIERS`. It does not widen with the warehouse at all. **[VERIFIED]**
* **`JobDeliveryman.getMaxParallelDeliveries()`** is O(1): it reads this citizen's own skill and
  never touches the assignee list. Safe at any width. **[VERIFIED]**

### One thing I checked and left alone

The warehouse's courier list in the GUI is `<list id="workers" size="164 110">` with 11-pixel rows —
**exactly ten visible rows**, which is exactly the old cap, and plainly sized to it
(`assets/minecolonies/gui/layouthuts/layoutcourierassignment.xml`). At twenty that is either a scroll
or a truncation, and truncation would be a real bug.

It scrolls. BlockUI registers `list` as `ScrollingList` (`Loader.java:43`), which extends
`ScrollingView` and owns a `Scrollbar` (`ScrollingView.java:11-57`). So twenty entries scroll in a
ten-row window and nothing is lost. **[VERIFIED]** in BlockUI's source — **[UNCHECKED]** visually,
because there is still no client here. This is the second GUI item on the eyeball-before-shipping
list, alongside the hut window in 3.5.

### 9.3 On the server, at the new limit

Same fixture, world re-extracted pristine each time so the before/after numbers are directly
comparable with section 8.

**Shipped configuration** — hut 5, warehouse 20:

```
before:  deliveryman5 deliveryman 1/5    warehouse5 deliveryman 1/20
after:   (hut full, delisted)            warehouse5 deliveryman 5/20
```

Five couriers from the one hut, all `START_WORKING`, `No problems found`. Job slots went
`130/347 → 138/347`. **[SERVER]**

That `347` is worth a beat: the pristine baseline before any of this work was `333`. It is now
`333 + 4 + 10 = 347` — four from the hut going 1→5, ten from the warehouse going 10→20. Every slot is
accounted for and no other building moved. **[SERVER]**

**Driving the warehouse to its full width.** The fixture has only one Courier's Hut, and nothing can
create another — `AbstractColonyBlock.setPlacedBy` registers a building only when a `LivingEntity`
places the block, so `/setblock` does not do it and there is no command that does. To get past ten
couriers I therefore built a **throwaway instrumented jar with the hut at `level * 5` (25)** and left
the warehouse at its real shipped `level * 4` (20). The instrumentation was reverted before commit;
`git status` is clean of it and the hut ships as `ICommonBuilding::getBuildingLevel`.

```
before:  deliveryman5 deliveryman 1/25   warehouse5 deliveryman 1/20
after:   (both full, delisted)
```

**25 distinct couriers** existed afterwards, and the warehouse was **gone from the free-slots list**
— it had been sitting there at `1/20` minutes earlier, so its disappearance means full: **20/20**.
Twenty-five couriers offered, twenty accepted, **the cap binds exactly where it should**. Employed
went `133 → 153` (+20) and job slots `347 → 367` (+20 from the instrumented hut), so the arithmetic
closes. **[SERVER]**

| | Shipped (hut 5) | Instrumented (hut 25) |
|---|---|---|
| Couriers created | 5 | **25** |
| Warehouse holds | 5/20 | **20/20 (full)** |
| Surplus with no warehouse | 0 | **5** |
| Courier AI states | all `START_WORKING` | all 25 `START_WORKING` |
| Employed | 133 | 153 |
| Job slots | 138/347 | 173/367 |
| Diagnose | `No problems found` | `No problems found` |
| Errors | 17, all fixture blueprints | 17, all fixture blueprints |
| Non-fixture exceptions | none | **none** |

The five surplus couriers are the graceful-degradation path from 3.6 finally provoked: they sit in
`START_WORKING` with `checkIfExecute()` returning false, and nothing crashes.

**One honest caveat about that last row.** `/mc colony diagnose` reported `No problems found` *with
five couriers stranded without a warehouse*. That is not the feature misbehaving — the five do have a
work building, so none of diagnose's categories (job-without-building, unresolved request, stuck AI)
matches them. But it does mean **diagnose will not tell a player they over-built couriers**; only the
in-game blocking chat interaction will, and that needs a client to see. Worth knowing before treating
`No problems found` as proof of a well-balanced colony.
