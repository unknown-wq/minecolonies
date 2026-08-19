# Courier delivery: how the round trip works, what it wastes, and whether one courier can serve several buildings at once

Design study, not an implementation. Date: 2026-08-15. Tree: `26.2/` in this repo, against the
NeoForge 1.21.1 original at `1.21.1/` and upstream MineColonies at `/workspace/ldtteam/minecolonies`
(`2d453335`). Companion to [`courier-capacity.md`](courier-capacity.md), which is assumed read: it
established the request-system plumbing (shared per-warehouse queue, claim-by-removal, no hut
affinity) and this study does not repeat those citations except where a price depends on one.

Evidence standard, same as the other studies here:

* **[VERIFIED]** — I read the source, the `file:line` is real and says what I claim it says.
* **[MEASURED]** — a number taken off a running dedicated server in this container, or computed from
  data taken off one. The method is in §3 and the raw log lines are quoted.
* **[UNCHECKED]** — inference from the code, not observed. **There is no game client here**, so
  nothing below was seen in a GUI or played.

Paths are relative to the repository root; `26.2/src/main/java/com/minecolonies/` is abbreviated to
`mc/`. Line numbers are against the branch base (`c7cb6ac5`), **not** against `claude/courier-capacity`,
which shifts three files.

---

## 0. The answer

**A courier is a walking machine and almost nothing else.** Measured on a dedicated server with a real
colony under a real backlog: **77 % of a busy courier's life is spent walking, 23 % is spent standing
still waiting for a five-second timer, and essentially 0 % is spent doing anything I could call work.**
[MEASURED] Moving items in and out of chests is instantaneous. So anything that shortens the walk
converts almost one-for-one into throughput, and there is no third thing to optimise.

**The walk is a star, not a route.** Every delivery is warehouse → one building → warehouse. The
courier never visits two buildings on one trip *unless they are the same building*: the claim rule
extends a claim only to requests with an identical target (`mc/core/colony/jobs/JobDeliveryman.java:298`).
[VERIFIED]

**So yes — «может за раз носить нескольким» is the right question, and the answer is that it cannot
today, that inventory space is not what stops it, and that the geometry pays well.** On the real
building layout of the test colony, sending one courier to the head-of-queue building plus its two
nearest neighbours instead of making three separate round trips saves **54 % of the walking**, and a
plain greedy nearest-next ordering captures almost all of the saving that an optimal tour would
(29.1 % vs 30.8 % on three random drops). [MEASURED, geometry] It costs **no extra path searches** —
the same number of legs, each shorter.

**But the cheapest useful change is not the routing.** It is the five-second `DECISION_DELAY`
(`mc/core/entity/ai/workers/service/EntityAIWorkDeliveryman.java:68`), which costs a measured 23 % of
courier time for a one-token edit, and the claim rule's blindness to where the courier is standing
(`JobDeliveryman.java:191-210`), which is ~15 lines and matters *more* the more couriers there are —
which is exactly what is about to happen. **Ranked list in §6; the one I would build is §6.2 + §6.3
together, about 40 lines across two files.**

**And one thing found by accident that outranks all of it in urgency.** Five couriers were put in one
hut, 192 requests were waiting, and **four of the five stood still for the entire run and delivered
nothing** — because being hired at the Courier's Hut is only half of what a courier needs; the
warehouse keeps a second assignment list, and a courier missing from it cannot run its AI at all.
`/mc colony diagnose` called that colony healthy. [MEASURED] With one courier per hut this was a
misconfiguration you noticed. With five it is four statues. **§2.1, and §6.1b is the fix.**

Also found, and they are not opinions: **three inherited defects** — one makes a guard against picking
work in unloaded chunks completely dead, and combined with the fact that a claim is never released
(§2.5) it lets one courier strand a batch of requests where no other courier can see them (§2.4).

---

## 1. How it actually works today

### 1.1 The shape of the loop

The courier AI is a six-state machine registered in the constructor
(`EntityAIWorkDeliveryman.java:114-125`) [VERIFIED]:

| State | Handler | Tick rate | What it is |
|---|---|---|---|
| `IDLE` | — | 1 | falls straight through to `START_WORKING` |
| `START_WORKING` | `decide()` | **100 (5 s)** | pick the next task |
| `PREPARE_DELIVERY` | `prepareDelivery()` | 5 | walk to the source rack(s) and load |
| `DELIVERY` | `deliver()` | 5 | walk to the target and unload |
| `PICKUP` | `pickup()` | 5 | walk to a building and take its surplus |
| `DUMPING` | `dump()` | 20 | walk to the warehouse and empty out |

The tick rate is not a one-off wait on entry; it is how often that transition is allowed to run at
all. `TickingTransition#countdownTicksToUpdate` decrements by the AI tick rate (5) each AI tick, and
`TickRateStateMachine#checkTransition` resets it to the transition's own rate after each run
(`mc/api/entity/ai/statemachine/tickratestatemachine/TickRateStateMachine.java:114-126`,
`TickingTransition.java:118-121`) [VERIFIED]. So the courier's decision point genuinely fires once
every 100 server ticks and no faster. Hold that thought for §2.2.

### 1.2 How a request becomes a delivery

Nothing a building asks for is a "delivery" to begin with. The chain is:

1. A building calls `createRequest(new Stack(...))` — for a builder's materials, a crafter's inputs, a
   minimum-stock top-up (`mc/core/colony/buildings/modules/MinimumStockModule.java:134-140`), a
   restaurant's menu. [VERIFIED]
2. `AbstractWarehouseRequestResolver` claims it if the warehouses between them hold enough
   (`.../resolvers/core/AbstractWarehouseRequestResolver.java:72-130`), then immediately resolves it
   (`:231-233`). [VERIFIED]
3. **On completion it manufactures the actual `Delivery` requests** in
   `getFollowupRequestForCompletion` (`:238-307`). This is the important step and it is easy to miss:
   it walks `getMatchingItemStacksInWarehouse`, which returns `Tuple<ItemStack, BlockPos>` — *one
   entry per rack that holds some of the item* — and creates **one `Delivery` per rack**, each with
   `start` = that rack's position and `target` = the requester (`:293-294`). [VERIFIED]
   A single "I need 64 planks" therefore becomes three `Delivery` requests if the planks are spread
   over three racks.
4. Each `Delivery` is itself a request, and the resolver that takes it is
   `DeliverymenRequestResolver`, which does not assign it to anybody. It appends the token to the
   **shared per-warehouse queue** (`.../resolvers/DeliverymenRequestResolver.java:106-113`). [VERIFIED]

Deliveries can also be born outside the warehouse: a crafter's output goes straight to the parent
requester (`PublicWorkerCraftingProductionResolver.java:93`) or to the warehouse
(`AbstractEntityAICrafting.java:735`). Those have a `start` that is not a warehouse rack. [VERIFIED]

Pickups are the mirror image and are created by the building itself
(`mc/core/colony/buildings/AbstractBuilding.java:1744`), scheduled for a future colony day based on
the building's pickup priority and how much has piled up (`:1742-1743`). [VERIFIED]

### 1.3 How a courier chooses what to take

`JobDeliveryman.getCurrentTask()` (`:219-321`) is the whole scheduler, and its name is a lie: it is
not an accessor. If the courier's private queue is non-empty it peeks and returns (`:221-225`);
otherwise it **claims**. [VERIFIED]

The claim is three passes over the shared queue:

**Pass 1 — score everything** (`:243-257`), by `getRequestPriority` (`:191-210`):

```java
int priority = 1;
if (!WorldUtil.isBlockLoaded(getColony().getWorld(), getTarget(req)))  { priority -= 1000; }
if (req.getRequest() instanceof AbstractDeliverymanRequestable requestable)
{
    priority = requestable.getPriority();              // ← overwrites the -1000
    if (requestable instanceof Pickup pickup && pickup.getDay() > getColony().getDay()) { priority -= 100; }
}
priority += mutableRequestList.size() - mutableRequestList.indexOf(token);
final int distance = (int) Math.sqrt(getSource(req).distManhattan(getTarget(req)));
return priority - distance;
```

Three things to notice, all of which matter later:

* **The `-1000` is dead.** Every token that can be in this list is a `Delivery` or a `Pickup`, and both
  extend `AbstractDeliverymanRequestable` (`mc/api/colony/requestsystem/requestable/deliveryman/Delivery.java`,
  `Pickup.java`), so the branch that assigns `priority` always runs and always discards it. [VERIFIED]
  There is even a `TODO` two methods down admitting the problem is unsolved (`:218`).
* **The queue-position term dominates.** Base priorities are tightly clustered — `DEFAULT_DELIVERY_PRIORITY`
  is 13, aging caps at 14, the player's "pick up now" is 15
  (`mc/api/colony/requestsystem/requestable/deliveryman/AbstractDeliverymanRequestable.java:15-18`)
  [VERIFIED] — while `size - indexOf` runs from 1 to the queue length. At the queue depths I measured
  (**mean 117, peak 175** [MEASURED]) the position term is an order of magnitude bigger than
  everything else, so the rule degenerates to **FIFO**.
* **The distance term is the wrong distance.** It is `sqrt(manhattan(source, target))` — how far the
  *parcel* travels — not how far the *courier* is from either end. The courier's own position is not
  an input to the choice at all. And because it is subtracted, a long delivery is *penalised*: outlying
  buildings are systematically served last until aging rescues them. [VERIFIED]

**Pass 2 — take the winner** (`:264-271`): its token moves into the courier's private data store.

**Pass 3 — extend the claim** (`:272-310`). This is the only batching that exists today:

```java
if (getTarget(localRequest).equals(getTarget(resultRequest)))
{
    getTaskQueueFromDataStore().add(reqId);
    extendedReqs++;
    reqsToRemove.add(reqId);
}
...
if (extendedReqs >= getMaxParallelDeliveries()) { break; }
```

**Identical target only.** Not "nearby", not "on the way" — `BlockPos.equals`. The bound is
`getMaxParallelDeliveries()` = `1 + secondarySkill/5` (`:515-522`), so a level-50 Adaptability courier
can carry eleven parcels *to the same building*. Everything skipped over on the way to the winner gets
`incrementPriorityDueToAging()` (`:287-290`), which is the only thing that stops a far-away building
starving. [VERIFIED]

Finally the claimed tokens are removed from the shared list and the module is marked dirty
(`:317-318`).

### 1.4 What it then does

`decide()` (`EntityAIWorkDeliveryman.java:600-640`) [VERIFIED]:

* **No task** → walk to the *warehouse* (not the courier's hut) and loiter there; dump if carrying
  anything (`:604-623`).
* **A delivery** → **if the inventory is not empty, go dump first** (`:624-634`). This is unconditional:
  the courier will not begin a delivery while holding anything at all, including food it forgot to eat.
* **A pickup** → straight to `PICKUP`.

`prepareDelivery()` (`:478-557`) builds the same-source-and-destination task list
(`JobDeliveryman.getTaskListWithSameDestination`, `:488-509`), finds the first parcel it is not
already carrying, walks to that parcel's `start` — a rack inside the warehouse — and pulls the stack
(`:523-546`). It then re-enters `PREPARE_DELIVERY` and repeats for the next parcel, so a multi-rack
order is a little walk around the inside of the warehouse. When there is nothing left to fetch, or the
parallel bound is hit, it goes to `DELIVERY` (`:518-521`).

`deliver()` (`:333-471`) walks to the target, then sweeps its own inventory and force-inserts
everything whose `ItemStorage` is in the delivery set (`:372-412`), and finishes the request. Returns
`START_WORKING` on success, `DUMPING` if the target's chest was full (`:470`).

`pickup()` (`:146-208`) is the one part of the loop that **already is multi-stop**: it takes surplus
from a building, calls `finishRequest(true)` and returns `START_WORKING` (`:179-196`), and only
diverts to `DUMPING` when `cannotHoldMoreItems()` (`:270-277`, `2^(level-1)+1` stacks, unlimited at
level 5) or the inventory is out of slots. So a courier will happily chain pickups from five buildings
before returning. **Deliveries do not get the same treatment, and there is no technical reason for the
asymmetry.** [VERIFIED]

### 1.5 What happens when it cannot finish

Four ways a delivery fails, and they are not equally well handled [VERIFIED]:

| Situation | Code | What happens |
|---|---|---|
| Target building destroyed | `:353-358` | `finishRequest(**true**)` — the chain is told it succeeded |
| Target in another dimension | `:346-351` | logs, returns `START_WORKING`, request stays claimed |
| Courier's inventory turned out empty | `:454-464` | `finishRequest(false)`, the retry system re-queues |
| **Target's chest is full** | `:414-446` | player interaction fires, the stack goes back into the courier's inventory — **and `finishRequest(true)` still runs at `:469`** |

The last one is the interesting one. The request is marked `RESOLVED` although the goods came home
again; the courier goes `DUMPING`, puts them back in the warehouse, and the building has to notice it
is still short and ask again. Nothing is lost, but a full chest turns into a silent loop rather than a
stalled request, and the only trace is the chat interaction.

The claim is safe against other couriers throughout, because all of this runs on the server thread
inside the citizen AI tick, and the claim is a list removal — the second courier to look simply does
not see the token. That was established in `courier-capacity.md` §3.1 and I re-verified it here.

### 1.6 Predicting a courier

Put together: **a courier alternates between the warehouse and exactly one other building, and pauses
five seconds at each end.** Given an idle courier and a non-empty queue, it will
take the oldest request in the queue (not the nearest), walk to the rack holding it, pick up
everything else in the queue bound for the *same* building, walk there, unload, stand still for five
seconds, and walk back. If it is holding anything when the next delivery is chosen, it makes an extra
warehouse trip first.

---

## 2. Where it is weak

Ordered by what a player feels, with the class of problem named.

### 2.1 A courier the warehouse has not adopted is completely inert — **the defect that matters most right now**

This is the one I did not expect and it is the one I would fix before shipping the capacity change.

**Measured:** five couriers in one level-5 Courier's Hut, 192 requests waiting, 3 m 38 s of server
time. **Four of the five moved zero blocks and delivered nothing.** The one that worked did all twelve
deliveries. `/mc colony diagnose` reported `No problems found`. The full log is in §3.5.

**Mechanism**, and it is short [VERIFIED]:

1. A courier is only allowed to run its AI at all if `checkIfExecute()` returns true, and that requires
   `job.findWareHouse() != null` (`EntityAIWorkDeliveryman.java:647-670`). A `false` condition on the
   `START_WORKING` `AITarget` (`:119`) means `decide()` is never called, so the courier does not even
   loiter — it stands wherever it is, forever.
2. `findWareHouse()` (`JobDeliveryman.java:575-586`) searches the colony's warehouses for one whose
   **`CourierAssignmentModule` has this citizen in its own assigned list**. Being hired at the
   Courier's Hut is not enough and never has been. There are **two** assignment lists and both must
   contain the courier.
3. The only thing that populates the second list automatically is `CourierAssignmentModule.onColonyTick`
   (`mc/core/colony/buildings/modules/CourierAssignmentModule.java:26-51`), and it is behind three
   gates, any one of which silently skips it: `!isFull()`; `BuildingUtils.canAutoHire(...)`
   (`mc/core/util/BuildingUtils.java:106-113`), which needs the building assignable **and** either the
   town hall's colony-wide `AUTO_HIRING_MODE` setting on with the module in `HiringMode.DEFAULT` or the
   module explicitly in `HiringMode.AUTO`; and, outside the module entirely, the warehouse being in a
   **loaded chunk** at that tick (`RegisteredStructureManager.java:300-306`).

In my run the colony-wide auto-hiring gate was the one that was shut — the same gate also stopped the
Courier's Hut auto-hiring at all, which is why it sat at 1/5 with 870 unemployed adults available for
six minutes in the first run. [MEASURED] I did **not** isolate which of the three gates fails in any
other configuration, and turning colony-wide auto-hiring off is a legitimate thing for a player to do,
not a bug in itself.

**Why it matters now.** The player *is* told — `checkIfExecute` fires a `ChatPriority.BLOCKING`
interaction (`:663-668`) [VERIFIED] — and with one courier per hut the message and the symptom line up:
you hired one courier, one courier is complaining, you go and fix it. **With five couriers appearing
from one hut the same misconfiguration produces four identical statues and four identical complaints,
and the colony's own health command says everything is fine.** Twenty at a warehouse makes it worse
again.

**What to do about it** is in §6.1b, and it is small.

### 2.2 The five-second pause after every delivery — **inefficiency**, and the cheapest to fix

`DECISION_DELAY = TICKS_SECOND * 5` (`EntityAIWorkDeliveryman.java:68`), used as the tick rate of the
`START_WORKING` transition (`:119`). [VERIFIED] Every completed delivery and every completed pickup
returns to `START_WORKING`, where nothing happens until that transition next comes up.

Measured, on one courier under a permanent backlog over 4 minutes 1 second of server time:

```
[COURIERPROBE] Itzayana U. Grey  ticks=4830 dist=1030b deliv=13 fail=0 pick=0 items=224 |
               START_WORKING:1130t/0b PREPARE_DELIVERY:1800t/516b DELIVERY:1900t/514b
[COURIERPROBE] START_WORKING     1130 ticks ( 23.4%)         0 blocks (  0.0%)
[COURIERPROBE] PREPARE_DELIVERY  1800 ticks ( 37.3%)       516 blocks ( 50.1%)
[COURIERPROBE] DELIVERY          1900 ticks ( 39.3%)       514 blocks ( 49.9%)
```

[MEASURED] 1130 ticks in `START_WORKING`, zero blocks moved, across 13 deliveries — **87 ticks of
standing still per delivery, and 13 × 100 = 1300 is the theoretical maximum.** The queue was never
empty during this window, so this is not a courier with nothing to do; it is a courier with work
waiting, standing still.

Cross-check on the other 76.6 %: 1030 blocks over 3700 ticks in the two walking states is
0.28 blocks/tick — a citizen's ordinary walking pace. The courier is walking essentially every tick it
is not in `START_WORKING`. **The item transfers themselves cost no measurable time at all.**

### 2.3 The claim ignores where the courier is standing — **inefficiency, becoming contention**

`getRequestPriority` never reads the courier's position (§1.3). With one courier this is merely
suboptimal. With five in a hut and twenty at a warehouse it becomes the classic failure the owner
described — couriers walking past the building they are about to deliver to — because **all of them
score the identical list with the identical function and therefore all want the same request.** They
do not collide (the claim is atomic), but they queue up behind the same head of list: courier A takes
request 1, courier B a tick later takes request 2, and both requests can be at opposite ends of the
colony from where A and B happen to be.

At the queue depths that make couriers worth having, the position term makes this **FIFO across the
whole colony**, so the twenty couriers behave as one FIFO server with twenty legs rather than as
twenty local servers. There is no spatial partition anywhere in the design and nothing that would
create one. [VERIFIED]

### 2.4 Three inherited defects

All three are byte-identical in `1.21.1/` and upstream `2d453335`, so none of them is a porting loss —
but all three are now in this port's shipping jar. [VERIFIED]

**(a) The unloaded-chunk guard is dead code.** `JobDeliveryman.java:194-197`, quoted in §1.3: the
`-1000` penalty for a target in an unloaded chunk is overwritten one line later by
`priority = requestable.getPriority()` for every request type that can ever reach it. A courier will
therefore happily claim, and walk toward, a delivery whose destination is not loaded — which is what
the `TODO` at `:218` says the code is supposed to avoid. Fix is one line: `priority -= 1000` after the
assignment instead of before it, or hoist the check.

**(b) The pickup-merge branch is unreachable.** `AbstractBuilding.java:1724`:

```java
if (request != null && request.getState() == RequestState.IN_PROGRESS && req instanceof Pickup pickup)
```

`req` is the `IToken<?>` loop variable, not the request. A token is never a `Pickup`, so the body —
which is the code that increases an outstanding pickup's quantity and pulls its scheduled day forward
as more goods pile up — never runs. The method still returns `false` early
(`:1740`), so no second pickup is created either: a building that keeps producing simply keeps
whatever pickup priority and day it had when the first pickup was raised. Should be
`request.getRequest() instanceof Pickup pickup`. Identical upstream at
`/workspace/ldtteam/minecolonies/.../AbstractBuilding.java:1534`. [VERIFIED]

**(c) A destroyed target counts as a successful delivery.** `EntityAIWorkDeliveryman.java:353-358`
calls `job.finishRequest(true)` when `getBuilding(targetLocation)` returns null. The parent request
chain is told its goods arrived. Harmless when the building really is gone; wrong in principle, and
one character to fix.

Two further defects in code that assumed one courier per hut are already documented and fixed on
`claude/courier-capacity` (§3.3 there): `getMaxParallelDeliveries()` reading assignee slot 0, and
`BuildingDeliveryman.canEat()` reading `getFirstCitizen()`. **The second is worse than that study had
room to say.** `canEat` routes through `job.getCurrentTask()` — which, per §1.3, *claims work* — and
`FoodUtils` calls `canEat` **once per candidate food stack**, both over the citizen's own inventory
(`mc/api/util/FoodUtils.java:164`) and over every stack in every rack of the restaurant
(`:229`, `:323`). A single hungry courier scanning a stocked restaurant can therefore fire dozens of
claims as a side effect of looking for lunch. [VERIFIED] Both are unfixed on the branch base.

### 2.5 A claim is never released — **contention, and it compounds §2.4's defect (a)**

Once a token leaves the shared queue it is that courier's until one of exactly three things happens:
`finishRequest` (`JobDeliveryman.java:328-393`), an external cancellation via `onTaskDeletion`
(`:400-411`), or the whole job being torn down / going inactive via `cancelAssignedRequests`
(`:423-438`). **There is no timeout and no "I have been trying this for five minutes, give it back".**
[VERIFIED]

The inactivity path cannot serve as one, either. `CitizenData.java:1737` only starts the inactivity
timer when `isWorking` is false, and `checkIfExecute` sets `setWorking(true)` for any courier that has
a warehouse at all (`EntityAIWorkDeliveryman.java:652`). [VERIFIED] A courier that is stuck *while
employed* is, by that definition, working.

Put that together with defect (a) above and there is a concrete stranding scenario: the dead
unloaded-chunk guard lets a courier claim a delivery whose target is not loaded, the claim pass takes
up to `getMaxParallelDeliveries()` more parcels for the same address with it (`:274-310`), the courier
then cannot complete any of them, and **none of the other nineteen couriers can see those requests
any more.** With one courier this is a stalled colony that a player restarts. With twenty it is the
"one courier does all the work while the others idle" shape — except the idle ones are idle because
the work has been claimed, not because they are lazy. [UNCHECKED] — I did not provoke this on the
server; it is read off the three release paths and the dead guard.

### 2.6 A dead constant

`MIN_DISTANCE_TO_WAREHOUSE = 5` (`EntityAIWorkDeliveryman.java:63`) has no other reference anywhere in
the tree. [VERIFIED] Cosmetic, listed only because it suggests a "don't bother if you are already
close" rule that was removed and not replaced.

### 2.7 What is *not* weak, and I expected it to be

* **Inventory space is not the constraint on batching.** A citizen has 27 slots
  (`mc/api/inventory/InventoryCitizen.java:44`), extensible by research (`:591-593`), and the
  `cannotHoldMoreItems()` cap of `2^(level-1)+1` stacks is used **only in the pickup path**
  (`EntityAIWorkDeliveryman.java:157`, `:218`) — the delivery path checks only
  `getInventory().isFull()` (`:536`). [VERIFIED] So a level-1 courier that may carry two stacks of
  *surplus* may carry twenty-seven stacks of *deliveries*. Batching three or four buildings' worth of
  parcels needs three or four slots in the common case of one stack each.
* **Path searches are not where the courier's cost is.** Courier legs use
  `PathJobMoveCloseToXNearY` (`EntityNavigationUtils.java:95-124`), which measured **3.5 ms and 711
  nodes per search** against `PathJobMoveToLocation`'s 11 ms and 2336 nodes in the same window.
  [MEASURED] Over the whole 5 m 49 s run the colony did 241 of them against 20 674 `MoveToLocation`
  and 12 099 `RandomPos` from the 870 unemployed citizens milling about. The courier is a rounding
  error in the pathfinding budget, and §4.3 shows batching *reduces* its share rather than raising it.
* **Contention over the shared queue does not corrupt anything.** `courier-capacity.md` §8 has the
  clean result — five couriers adopted by one warehouse, all five running, `No problems found`, no
  duplicated deliveries and no orphaned tokens. My own runs never had more than one courier actually
  attached to the warehouse (§2.1), so I am relying on that study for this one, not on my own numbers.

### 2.8 One contention cost that is real but small

Every claim calls `wareHouseModule.markDirty()` (`JobDeliveryman.java:318`), which marks the whole
warehouse dirty (`AbstractBuildingModule.java:27-35`), and
`WarehouseRequestQueueModule.serializeToView` writes **every token in the queue** to the client
(`:54-63`). [VERIFIED] At the 117-deep queue I measured, that is a 117-entry list re-serialised on
every claim; with twenty couriers claiming instead of one, twenty times as often. It only costs
anything while a player has the warehouse window open, so it is a nuisance rather than a bug, but it
is the one thing that scales badly with courier count and it is worth knowing before the count goes
to twenty. [UNCHECKED] that it is actually noticeable — no client here.

---

## 3. What was measured, and how

### 3.1 Method

A throwaway probe (**not committed**; the working tree was reverted before this file was written)
sampled every courier in the colony once per server tick and accumulated, per AI state, the ticks
spent in it and the horizontal distance moved during it, taking positions from
`Entity#position()`. Completed deliveries, failed deliveries, pickups and items actually inserted were
counted at the four places in `EntityAIWorkDeliveryman` that finish a request. A companion command
injected load (`n` buildings each requesting 16 cobblestone, warehouse pre-stocked), hired couriers,
and dumped the colony's building geometry.

Fixture: `testworlds/colony-1000.zip` on a dedicated Fabric 26.2 server in this container, port 25993,
`-Xmx4G`, jar built from this worktree through `/home/user/mc-build.sh`. The jar also carried the
three source changes from `claude/courier-capacity`, so that the five-courier run was possible at all.
Boot was clean apart from the seventeen blueprint errors that fixture is documented to produce.

**Read the fixture's limits before trusting the absolute numbers.** It is compact: 149 buildings
inside a 90 × 65 block box, median 30 blocks from the warehouse, maximum 71. A real colony is several
times more spread out, which makes the *absolute* blocks-per-delivery figure a floor. The *fractions*
in §3.4 are ratios of distances and are scale-invariant, so they carry over. The load is synthetic — one
item type, one request per building — which under-represents same-destination batching (a real builder
orders many different materials to one address) and over-represents distinct destinations.

### 3.2 The courier's time and distance

One courier, permanent backlog, 4 m 01 s:

| | |
|---|---|
| Deliveries completed | 13 (0 failed) |
| Items delivered | 224 |
| Distance walked | 1030 blocks |
| **Blocks walked per delivery** | **79.2** |
| **Blocks walked per item** | **4.60** |
| Ticks per delivery | 371 (18.6 s) |
| **Share of time walking** | **76.6 %** |
| **Share of time standing in `START_WORKING`** | **23.4 %** |
| Share of time doing anything else | ~0 % |

[MEASURED] The 79 blocks per delivery against a median 30-block building distance is the round trip
plus the walk between racks inside the warehouse, and it is the number the whole study turns on: a
courier that saves 30 % of its walking gains ~23 % more deliveries per day, because there is nothing
else in its budget to trade against.

### 3.3 The queue

| | |
|---|---|
| Mean queue depth | 116.9 |
| Peak queue depth | 175 |
| Samples with ≥ 2 *distinct* destinations pending | **100 %** |
| Distribution of requests per destination | `{1:15478, 2:50928, 3:40912, 4:32768, 5:23537, 6:12610}` |

[MEASURED] Two readings, and they point in the same direction:

* **The opportunity for multi-drop is always there.** In every single one of 4830 samples, at least two
  different buildings were waiting. Not one tick of the run had a queue that today's same-target rule
  could have batched but a multi-drop rule could not.
* **Same-destination batching does fire, and it is not enough.** Requests did pile up 2–6 deep on one
  address, so the `:298` rule earns its keep. But that is a small fraction of a 117-deep queue: the
  courier batched 224 items into 13 deliveries — 17 items, i.e. one or two parcels, per trip — while
  well over a hundred parcels for other addresses sat waiting. (The multiplicity figures are inflated
  by my re-injecting the same 40 buildings; treat them as an upper bound.)

### 3.4 The geometry, and what batching is worth

Computed from the fixture's 149 real building positions and its real warehouse position, 20 000–200 000
random draws each. The quantity is the saving of one tour against the same drops as separate round
trips, `(Σ2·d(W,pᵢ) − tour) / Σ2·d(W,pᵢ)`:

| Batching rule | Mean saving in walking |
|---|---|
| Two random destinations together | **17.7 %** |
| Three random destinations, optimal order | 30.8 % |
| Three random destinations, **greedy nearest-next** | **29.1 %** |
| Five random destinations, greedy nearest-next | 43.7 % |
| Head of queue + its **best partner**, queue depth 8 | 35.0 % |
| Head of queue + its best partner, queue depth 32 | 41.8 % |
| Head of queue + its **two nearest neighbours**, queue depth 8 | 42.9 % |
| Head of queue + its two nearest neighbours, queue depth 32 | **54.1 %** |

[MEASURED, geometry] Three conclusions I would stake the proposal on:

1. **Deep queues make batching better, not worse.** The more work is waiting, the closer together the
   best two or three drops can be. At the depths actually observed (mean 117), picking the head plus
   its two nearest neighbours halves the walk.
2. **Greedy beats nothing and ties optimal.** 29.1 % against 30.8 % on three drops. There is no reason
   to write a travelling-salesman solver; sorting the claimed drops nearest-next from the current
   position is within two points of perfect and is O(k²) on k ≤ 4.
3. **Even blind pairing is worth 18 %.** The floor for a change that batches two arbitrary requests
   with no proximity test at all is still nearly a fifth of the walking.

### 3.5 The five-courier run — four of the five never moved

This was meant to measure contention. It measured something more useful. Four extra couriers were
hired into the one level-5 Courier's Hut (5/5), 192 requests were waiting, and the run went for
3 m 38 s:

```
[COURIERPROBE] ticks sampled=4362  queue depth avg=191.90 max=202
[COURIERPROBE] India X. Magic       ticks=4362 dist=1b deliv=0 pick=0 items=0 | START_WORKING:4362t/1b
[COURIERPROBE] Macie P. Alicock     ticks=4362 dist=0b deliv=0 pick=0 items=0 | START_WORKING:4362t/0b
[COURIERPROBE] Itzayana U. Grey     ticks=4362 dist=917b deliv=12 pick=0 items=352 | START_WORKING:1142t/1b PREPARE_DELIVERY:1610t/465b DELIVERY:1610t/451b
[COURIERPROBE] Sekani E. Astley     ticks=4362 dist=0b deliv=0 pick=0 items=0 | START_WORKING:4362t/0b
[COURIERPROBE] Brycen J. Coppinger  ticks=4362 dist=0b deliv=0 pick=0 items=0 | START_WORKING:4362t/0b
```

[MEASURED] **Four couriers spent 4362 ticks each in `START_WORKING`, moved zero blocks, and delivered
nothing, while two hundred requests waited.** One courier — the one that was already there when the
world was saved — did all twelve deliveries. Its own budget is unchanged from §3.2 (76 blocks per
delivery, 1142 ticks of `START_WORKING` for 12 deliveries ≈ 95 per delivery).

And `/mc colony diagnose 1`, run at the end of that window, said:

```
No problems found - full report in the server log
  #4   Itzayana U. Grey    [deliveryman] state=DELIVERY      held=new status=IDLE
  #126 Brycen J. Coppinger [deliveryman] state=START_WORKING held=new status=IDLE
  #127 Sekani E. Astley    [deliveryman] state=START_WORKING held=new status=IDLE
  #128 Macie P. Alicock    [deliveryman] state=START_WORKING held=new status=IDLE
  #129 India X. Magic      [deliveryman] state=START_WORKING held=new status=IDLE
  warehouse5 deliveryman 1/10 at -20, 73, -30
```

[MEASURED] `warehouse5 deliveryman **1**/10` is the whole story, and §2.1 is what it means.

Two honest caveats. **The backlog was three times what even five working couriers could clear**, so
this run says nothing about how five *working* couriers share a queue; `courier-capacity.md` §8 has
that result and it is clean. And **a first attempt at this run was killed by the operating system**
(other work in this shared container took the memory) before it could dump — the container's own
documentation says this fixture wants 6 GB and only 4 GB was available.

### 3.6 What I could not measure

* **Nothing was seen in a GUI.** No client in this container.
* **No A/B of a batching implementation.** This is a study; nothing in §6 was built, so every figure
  in §3.4 is geometry on real positions, not an observed improvement.
* **No realistic request mix.** The synthetic load is one item type. The true share of deliveries that
  today's same-target rule already batches, in a played colony, is not measured here — §3.3's
  multiplicity histogram is an upper bound distorted by how I generated load.
* **No long-run balance data.** Nothing about how much faster a colony actually grows.
* **Upstream's issue history could not be searched** — the GitHub tooling in this session is scoped to
  the owner's forks and refuses `ldtteam/minecolonies`, and the local upstream checkout is a single
  squashed commit with no history. §4's claim about what upstream has attempted rests on reading
  upstream's shipping code, not its tracker.

---

## 4. The multi-drop question, answered properly

### 4.1 Can it today? No, and the reason is one `equals`

`JobDeliveryman.java:298` extends a claim only to requests whose target `BlockPos` is identical.
[VERIFIED] There is no notion of "nearby", no notion of "on the way". A courier's private queue can
hold many parcels, but they are always for one address.

The consumer side agrees. `getTaskListWithSameDestination` (`:488-509`) is stricter still — it requires
same target **and** same source, where "same source" is generously defined as "both racks belong to
the same warehouse" (`haveTasksSameSourceAndDest`, `:463-480`). `deliver()` builds its unload set from
that list (`EntityAIWorkDeliveryman.java:372-373`) and `prepareDelivery()` gathers from it (`:488`).
[VERIFIED]

Note the asymmetry with the multi-source case, which the code *does* handle: a single order spread over
three racks becomes three `Delivery` requests with three different `start` positions (§1.2), and
`prepareDelivery()` loops until it has visited all three (`:518-546`). **The loop that visits several
places to load already exists. There is no equivalent loop to visit several places to unload.**

### 4.2 What upstream has done instead

Upstream's answer to "too many small deliveries" is not routing; it is **making the orders bigger at
the source**. The `Minimum Order Quantity` research (`DefaultResearchProvider.java:1666-1675`) grants
the `MIN_ORDER` effect, whose entire implementation is a threshold change in two modules: with it,
a building only raises a restock request once it is short by more than a quarter of its target rather
than short by anything at all (`MinimumStockModule.java:134`, `RestaurantMenuModule.java:175`).
[VERIFIED] Same-target claim extension (`:298`) and `getMaxParallelDeliveries` are the other half of
the same idea.

So upstream has attacked delivery efficiency twice — fewer, larger orders; batch what shares an
address — and has never attacked the round trip itself. There is no oracle to copy here.

### 4.3 What it would cost

**Inventory space: not the constraint.** 27 slots (§2.7), and the stack-count cap does not apply to
deliveries at all. A courier carrying for four buildings needs four to eight slots in the ordinary
case. This is the assumption in the owner's question that turns out not to bind.

**Claims: already plural, already unwind cleanly.** The private queue is a `LinkedList<IToken<?>>` and
`getCurrentTask` already puts several tokens into it in one claim (§1.3). Failure unwinds per token,
not per batch: `finishRequest` resolves or fails exactly the tokens in `getOngoingDeliveries()`
(`JobDeliveryman.java:364-373`), which are the ones `prepareDelivery` actually loaded
(`EntityAIWorkDeliveryman.java:542`), and `onTaskDeletion` (`:400-411`) pulls a single cancelled token
out of the middle of the queue without disturbing the rest. **A partially-failed multi-drop round is
already expressible.** [VERIFIED]

**Routing order: greedy, and it must be greedy.** §3.4 measures nearest-next at 29.1 % against an
optimal 30.8 %. Anything cleverer buys 1.7 points and costs a solver.

**The third building filled up or vanished.** Both paths already exist and already run per drop:
`deliver()` re-reads `getBuilding(targetLocation)` at the moment of arrival (`:353`) and force-inserts
against the live handler (`:401`). A multi-drop version does not need new failure handling; it needs
the existing handling to run in a loop and the loop to continue to the next drop instead of returning.

**Path searches: fewer, not more.** Today each delivery is one `walkCloseToXNearY` to a rack and one to
the target. A three-drop round is the same rack legs plus three target legs — the same count for three
deliveries — but each target leg is a short hop between neighbours instead of a full radius out and
back. Search cost scales with the nodes expanded, which scales with distance, so the batched round is
**cheaper in path jobs than the three round trips it replaces**. This is the one place where the
project's usual rule ("more clever routing means more path searches means a regression") does not
apply, and it is worth stating plainly.

### 4.4 What fraction of real deliveries could actually be batched?

This is the question that decides whether a batching system is worth writing, and the honest answer
has two halves.

**The opportunity is essentially universal when there is a backlog.** 100 % of 4830 samples had at
least two distinct destinations pending. [MEASURED] A rule of the form "take the head of the queue plus
up to k others whose targets are within R of it" fires on essentially every claim a courier makes while
the queue is non-trivial.

**But couriers are not always in a backlog, and that is the real limiter.** With one courier and a
colony that outruns it, the queue is deep and batching fires constantly. With twenty couriers and the
same colony, the queue drains to nearly empty and most claims will find one or two candidates, where
the batching saving falls to the 18 % floor of §3.4 and often to zero. **The value of batching and the
value of more couriers eat each other.** That is not an argument against batching — 18 % of a courier
whose entire budget is walking is still 18 % — but it is the reason I would not build the elaborate
version.

The rule of thumb that falls out of §3.4: **batching is worth roughly `min(queue depth, 4)` drops per
trip and no more**, and it is worth most exactly when the player is complaining.

---

## 5. What to *not* do

Two things that look attractive and are not, so they are not proposed again later.

**Do not build a global assignment.** Scoring all couriers against all requests each tick — a
Hungarian assignment or anything of that family — is O(couriers × queue) per evaluation on the server
thread, with the queue reaching 175 (§3.3) and couriers reaching 20. The present claim is already
O(queue) per idle courier per 5 s (§1.3) and that is the right order to stay at.

**Do not make couriers "own" buildings.** A static partition (courier 1 serves the north quarter) is
tempting because §2.3's problem is the absence of a spatial partition, but it produces the failure mode
where the north courier is idle while the south is buried, and it needs new persisted state that has to
survive a courier being fired. The distance term in §6.3 gets a *dynamic* partition for free: whoever
is nearest wins, and if nobody is near, somebody far away still takes it.

---

## 6. Proposals, cheapest first

Sizes are lines of production code, excluding comments in the house style. All of them are pure server
logic, no mixins, no new lang keys unless stated, no client classes.

### 6.1 Fix the three inherited defects — ~4 lines, 2 files. **Build this regardless.**

`JobDeliveryman.java:194-197` (move the `-1000` after the assignment),
`AbstractBuilding.java:1724` (`request.getRequest() instanceof Pickup`),
`EntityAIWorkDeliveryman.java:356` (`finishRequest(false)`).

**What the player notices:** nothing directly, except that couriers stop marching toward buildings in
unloaded chunks and a building that keeps producing gets its pickup escalated as designed.
**Per-tick cost:** zero. **What could go wrong:** (b) turns on a code path that has never run in
production, in this port or upstream; the merge it performs is upstream's own intended behaviour but it
has never been exercised. Worth a colony-day of watching pickup priorities.
**Verdict: cheap, correct, and the pickup one is a genuine behaviour change — ship (a) and (c) now, ship
(b) with a note.**

### 6.1b Make an unadopted courier visible — ~6 lines, 1 file. **Ship this with the capacity change, not after it.**

§2.1 measured four couriers standing still through a 192-request backlog while `/mc colony diagnose`
said `No problems found`. Two independent halves, either of which is worth doing on its own:

* **Report it.** `CommandColonyDiagnose` already walks every citizen with a job
  (`mc/core/commands/colonycommands/CommandColonyDiagnose.java:107-121`) and already has a
  "citizens with a job but no work building" list. A courier whose `findWareHouse()` is null belongs
  in a list of the same kind. ~5 lines and one lang key (`%s`, never `%d`).
* **Adopt more eagerly.** `CourierAssignmentModule.onColonyTick` (`:26-51`) currently refuses to adopt
  an existing, jobless-of-warehouse courier when colony-wide auto-hiring is off — but adoption is not
  hiring. Nothing is being taken from the labour pool; the citizen is already a courier and is already
  doing nothing. Dropping `canAutoHire` from the guard for the *adoption* loop (keeping `!isFull()`)
  makes the warehouse pick up any courier that has no warehouse, which is the only sensible thing to
  do with one. ~1 line.

**What the player notices:** couriers they hired actually work, and if something else is wrong the
health command tells them which courier and why.
**Per-tick cost:** the adoption loop already runs; removing a guard makes it run more often on
warehouses that are not full, which is `getCitizensUnmodifiable()` scanned once per warehouse colony
tick. That is the existing cost of the un-gated path, not a new one.
**What could go wrong:** the adoption change makes the warehouse's own hiring mode less meaningful — a
player who deliberately set the warehouse to manual so as to keep couriers off it would be overridden.
Gate on `getHiringMode() != HiringMode.MANUAL` instead of dropping the check entirely if that matters.
**Verdict: the diagnose half is unarguable. The adoption half is a behaviour change and should be
the owner's call — but four inert citizens and "No problems found" is not a defensible pairing.**

### 6.2 Give the decision point a fast lane — ~3 lines, 1 file. **Best value in the study.**

Lower the `START_WORKING` transition's rate from `DECISION_DELAY` to `STANDARD_DELAY` at
`EntityAIWorkDeliveryman.java:119`, and inside `decide()` call **`setCurrentDelay(DECISION_DELAY)`** on
the branch that finds no task (`:604-623`).

Use `setCurrentDelay`, **not** `setDelay` — they are different mechanisms and only one of them does
this job. `setDelay` (`AbstractEntityAIBasic.java:422-425`) sets a blocking wait that suspends the
whole AI for n ticks (`waitingForSomething`, `:522-542`); it is what the walk branches already use and
it is the wrong tool here. `setCurrentDelay` (`AbstractAISkeleton.java:170-173`) reaches
`TickRateStateMachine#setCurrentDelay` (`:140-144`), which writes `ticksToUpdate` on
`executedTransition` — the transition whose supplier is running, i.e. the `START_WORKING` transition
itself. [VERIFIED] `checkTransition` sets that field to the transition's own rate *before* calling the
supplier (`:118-125`), so the call inside `decide()` overrides it, and an idle courier keeps polling
once every five seconds while a busy one turns around in a quarter of a second.

**What the player notices:** goods arrive noticeably sooner, and couriers stop standing at a doorway
staring into space after every drop.
**Attaches to:** `EntityAIWorkDeliveryman.java:119`, `:600-640`.
**Size:** 3 lines plus a comment. One file.
**Per-tick cost:** for a *busy* courier, zero new work — it does the same claim it was going to do, just
sooner. For an *idle* courier, unchanged, because the idle branch restores the 5 s delay. The trap is
getting that branch wrong and letting twenty idle couriers run the O(queue) scan of §1.3 twenty times a
second; the `setCurrentDelay` in the idle branch is not optional.
**What could go wrong:** the override applies to the *next* visit to `START_WORKING`, not this one, so a
courier that has just gone idle gets one extra fast poll before settling. Harmless. The other risk is
that upstream chose 5 s deliberately as a throttle and something else depends on the courier lingering;
I found nothing that does. Confusing `setDelay` for `setCurrentDelay` here would freeze the courier
instead of speeding it up, which is why the paragraph above spells out which is which.
**Verdict: measured 23.4 % of courier time for three lines. Build it first.**

### 6.3 Make the claim prefer work near the courier — ~15 lines, 1 file. **Build this second.**

In `getRequestPriority` (`JobDeliveryman.java:191-210`), pass the courier's current position in and
replace the parcel-length term with a term that measures how far *this courier* is from the job:

* subtract a scaled distance from the courier to `getSource(req)` (the rack, or the warehouse for a
  pickup), and
* rescale the queue-position term so it cannot swamp it — the present `size - indexOf` reaches 175 and
  drowns everything else (§1.3). Capping it at a small constant, or dividing it by a constant, turns
  the rule from FIFO-with-noise into "nearest first, with ageing as the fairness backstop". The ageing
  machinery at `:287-290` already exists and is what stops starvation.

**What the player notices:** with one courier, materials for nearby buildings arrive first and the
courier stops crossing the colony to fetch something it will bring back past where it started. With
five or twenty, the couriers spread out on their own — the one standing by the north gate takes the
north jobs — without any zoning UI or persisted assignment.
**Attaches to:** `JobDeliveryman.java:191-210`, `:251`.
**Size:** ~15 lines, one file, no new state, no serialisation change.
**Per-tick cost:** one extra `distManhattan` per queue entry per claim. The scan is already there
(`:243-257`) and already does a `getRequestForToken` and a `WorldUtil.isBlockLoaded` per entry, both far
dearer than a subtraction. **Zero new path searches** — this is straight-line distance, never a path
query.
**What could go wrong:** the tuning. Weight the distance too heavily and a far-off building starves
until ageing rescues it (ageing caps at +1 per skip and tops out at 14, so a badly-weighted distance
term could out-run it). Weight it too lightly and nothing changes. Get it wrong in the direction of
"nearest always wins" and the colony's edges get poor service — the thing this is supposed to fix.
I would start conservative: keep the existing terms, add the courier-distance term, and cap the
position term at, say, 10 so it is comparable to a priority rather than to a queue length.
**Verdict: this is the change that makes twenty couriers behave like twenty couriers instead of twenty
legs on one FIFO server. It is the one that pays off *because of* the capacity change, not despite it.**

### 6.4 Multi-drop rounds — ~120 lines, 3 files. **The answer to the owner's question. Build it third, if at all.**

Three pieces:

1. **Claim.** In the extension pass (`JobDeliveryman.java:274-310`), replace the `equals` at `:298`
   with "same target, **or** target within R blocks of the chosen target", R something like 24, and
   keep the `getMaxParallelDeliveries()` bound. Roughly 10 lines.
2. **Route.** Sort the claimed drop list nearest-next from the courier's position when the round
   begins. O(k²) on k ≤ 4, no path queries, §3.4 says it is within 1.7 points of optimal. ~20 lines.
3. **Unload in a loop.** This is the real work. `deliver()` (`EntityAIWorkDeliveryman.java:333-471`)
   currently finishes and returns after one target. It has to walk the sorted list: unload what belongs
   to drop *i*, `finishRequest` for drop *i* only, and re-enter `DELIVERY` for drop *i+1* instead of
   returning to `START_WORKING`. `getTaskListWithSameDestination` (`JobDeliveryman.java:488-509`) has to
   become "the parcels for *this* drop" rather than "the parcels in my queue", and `prepareDelivery`
   (`:478-557`) has to gather for the whole round rather than for one destination. ~80 lines and the
   two touchiest methods in the file.

**What the player notices:** a courier leaves the warehouse with a full pack and visits three or four
buildings before coming back. This is the change the owner asked about and it is the one that *looks*
different.
**Attaches to:** `JobDeliveryman.java:274-310`, `:488-509`; `EntityAIWorkDeliveryman.java:333-471`,
`:478-557`.
**Size:** ~120 lines across 2–3 files. No serialisation change — the private queue is already a list of
tokens.
**Per-tick cost:** **negative in path searches** (§4.3): the same number of legs, each shorter, so fewer
nodes expanded on the pathfinding thread. Slightly more work per claim (a distance test per candidate
instead of an `equals`).
**What could go wrong:** the most, of anything here. `deliver()` and `prepareDelivery()` are the two
methods with the most implicit invariants — `deliver()` assumes the courier's inventory contains only
this delivery's items (there is an upstream `TODO` saying exactly that at `:329`), and it force-inserts
with a predicate that can *evict* items already in the target chest (`:399-402`). Carrying parcels for
building C while unloading at building A means the sweep at `:375-452` must not hand C's goods to A;
the `itemsToDeliver` set is keyed by `ItemStorage` and the extraction at `:388` is
`extractItem(i, Integer.MAX_VALUE, false)` — **the whole slot, regardless of what was asked for**. So
two buildings ordering the same item on one round would cross-contaminate: the first drop empties the
slots meant for the third. The loop has to be rewritten to track counts, not just item identity.
Upstream's own `TODO` at `:329` states the invariant this breaks — *"the dman's inventory may only
consist of the requested itemstack"*. That is the single hardest part of this proposal and it is not
visible from the outside.
**Verdict: worth 30–54 % of the walking at realistic queue depths (§3.4), and it is what was asked for.
But it is thirty times the code of §6.2 + §6.3 for maybe twice the benefit, in the two methods most
likely to produce a "my courier ate my planks" bug report. Build it only after §6.2 and §6.3 have been
played for a while, and only with the item-identity trap above fixed first.**

### 6.5 Multi-drop for pickups only — ~10 lines. **Cheap, and I would not build it.**

`pickup()` already chains across buildings (§1.4) and already stops on a capacity rule; the only thing
missing is that the *claim* does not batch pickups by proximity, so the chaining is accidental rather
than planned. Adding the §6.4 proximity rule for `Pickup` tokens only would be a fraction of the work,
because there is no unload loop to write.
**Verdict: cheap and I would not build it.** Pickups are not what players complain about — nobody
notices surplus arriving at the warehouse late — and the §6.3 distance term already improves pickup
ordering for free.

### 6.6 Reduce the number of deliveries at the source — ~0 lines. **Already shipped; consider a config.**

Upstream's `MIN_ORDER` research (§4.2) already exists in this tree and already halves restock traffic
for players who unlock it. `courier-capacity.md` §5 notes that the capacity change makes that research
*harder* to reach, because a player with one level-5 hut has no reason to build a second and the
research needs summed hut levels ≥ 9 (`DefaultResearchProvider.java:1670`).
**Verdict: no code. But if §6.2 and §6.3 ship, lowering that `9` becomes the cheapest remaining
throughput change in the game, and it is a one-token data edit.**

---

## 7. Summary table

| # | Change | Files | Lines | Player sees | Path cost | Verdict |
|---|---|---|---|---|---|---|
| 6.1 | Three inherited defect fixes | 2 | ~4 | correctness | 0 | ship |
| 6.1b | Report / adopt an unadopted courier | 1–2 | ~6 | **couriers that do nothing stop doing nothing** | 0 | **ship with the capacity change** |
| 6.2 | Fast lane on the decision timer | 1 | ~3 | **+23 % courier throughput** | 0 | **build first** |
| 6.3 | Claim prefers work near the courier | 1 | ~15 | couriers stop crossing town; N couriers self-partition | 0 | **build second** |
| 6.4 | Multi-drop rounds | 2–3 | ~120 | one trip, several buildings; **−30…54 % walking** | **negative** | build third, carefully |
| 6.5 | Multi-drop pickups only | 1 | ~10 | nothing | 0 | cheap; would not build |
| 6.6 | Lower the MoQ research requirement | data | 1 token | fewer, larger orders | 0 | consider after 6.2/6.3 |

**§6.2 + §6.3 is the cheapest thing that gets most of the benefit: about 18 lines in two files, no new
state, no new path searches, and it addresses both the measured 23 % dead time and the reason twenty
couriers will not behave like twenty couriers.** §6.4 is the answer to «может за раз носить
нескольким» and it is a real, well-paying change — but it is a second project, not a follow-up commit.
