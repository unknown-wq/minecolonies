# Free mode's cook eats the colony's own stock first

Date: 2026-08-16. Tree: `/home/user/wt-freemode-cook/26.2`, branch `claude/freemode-cook`.

## What was wrong

`FreeMode`'s design principle is that items are conjured only by `FreeMode#fulfil`, hooked into
`StandardRetryingRequestResolver` — the resolver a request reaches once every real resolver, the colony's own
chests, warehouses and crafters included, has declined it. The warehouse gets first refusal by construction.

`EntityAIWorkCook#supplyRawFood` broke that. The moment the cook's larder was empty it dropped a full stack of
raw food straight into the hut through `InventoryUtils#addItemStackToProvider`, asking nobody — while
`RestaurantMenuModule#onColonyTick` had already filed a real `MinimumStack` request for that same food. The
conjured stack won the race, the module then cancelled its own request (`delta <= 0`), and a stocked warehouse
was never consumed in free mode.

## What it does now

Before conjuring, `supplyRawFood` asks `inColonyStock` whether the raw item, or the finished dish, is sitting in
a loaded warehouse of the colony (`AbstractTileEntityWareHouse#hasMatchingItemStackInWarehouse`). If it is,
nothing is handed over this tick and the standing request is left to be delivered.

**The wait is bounded.** Seeing food in a warehouse is not getting it: there may be no courier, no route, or the
request may be parked. `SUPPLY_DEFER_TICKS = 2400` (two in-game minutes, a tenth of a day, at most forty passes
through `START_WORKING`'s 60-tick delay) is how long the cook waits before conjuring anyway. A gap of more than
`SUPPLY_DEFER_GAP_TICKS = 400` between two empty larders counts as the drought having ended and earns the colony
a fresh wait, so a delivery that arrives never loses the next race.

## Measured, on a dedicated server

Flat world, colony `CookTest`, restaurant (3 furnaces, menu `cooked_beef`, coal in the fuel list), warehouse with
two racks, courier's hut with five couriers. Free mode on unless stated. Stock is measured with
`InventoryUtils#getCountFromBuilding`, cooking with the vanilla furnace `RecipesUsed` counter.

| | before (0.0.42) | after |
|---|---|---|
| **1. Stocked warehouse, courier present.** Warehouse given 64 raw + 64 cooked beef, larder emptied | restaurant gains 64 **conjured** raw beef after 13 s while the warehouse still holds its 64 | restaurant gains nothing until the couriers move the stock: warehouse 64+64 → **0+0**, all of it arrives at the restaurant (cooked at t=124 s, raw at t=143 s, into the furnace by t=149 s) |
| **2. Warehouse empty of raw food** | 96.9 % of the working day in `START_WORKING` (`WORKER-AUDIT-2.md` §1, the broken case) | conjures within 22 s; **97** items smelted in 5 min across three furnaces, 0 → 194 cooked beef; 94.3 % `START_WORKING` (496 of 526 samples), the rest `FILL_UP_FURNACES`, `GATHERING_REQUIRED_MATERIALS`, `RETRIEVING_END_PRODUCT_FROM_FURNACE`, `INVENTORY_FULL` |
| **3. No warehouse and no courier at all** | — | conjures immediately (64 raw beef in the larder at the first sample), **75** items smelted in 4 min, 92.4 % `START_WORKING` (390 of 422) |
| **4. Free mode off** | — | unchanged: warehouse 64+64 → 0+0, everything arrives at the restaurant and is cooked, nothing is conjured. None of the new code runs — it is all behind `worksWithoutMaterials()` |

**The fallback, measured twice.** Warehouse holding 64 raw beef, every courier removed from the colony, larder
emptied: the cook conjured after **123 s** and **122 s** in two runs against the designed 120 s, and went on
cooking. Without the bound this is the case that would have stalled it for ever.

## The other free-mode direct-supply sites, and why they were left alone

| site | verdict |
|---|---|
| `EntityAIWorkSifter#supplySiftingMaterials` | same shape, **not** the same risk: the sifter files no request anywhere in the class, so there is no standing order for a warehouse to serve. A warehouse-first rule there would have to file a new request as well — new behaviour, not a guard. Left alone |
| `AbstractEntityAIFight` arrows, `FreeMode#equip`, `AntiAirBattery` | deliberately conjured, see the javadoc on `FreeMode#equip`: the combat AI looks the weapon up in the inventory itself and refuses to fight without one. Both already read the real inventory first and conjure only what is missing. Left alone |
| `AnimalPen#stock` | livestock, not items; nothing to deliver. Left alone |
| `AbstractEntityAIBasic#checkIfRequestForItemExistOrCreate` (and the farmer's `supplyMaterialWithoutRequest`) | the one remaining member of the family: it checks the worker's inventory and the hut's racks, but not the warehouses, before conjuring. Unlike the cook it is **not** racing a standing request — it *is* the request site, and making it wait for a delivery would reintroduce the worker stalls free mode exists to avoid, at fifteen call sites at once. Out of scope, recorded here |
