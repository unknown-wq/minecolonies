# Warehouse idle tracking

What has been sitting in the colony's warehouses without anybody taking it, and how fast each thing is
actually being drawn down. Answers "what is junk I can clear out" and, later, "what is safe to trade
away" — the two questions the player asked, and they need two different numbers.

Added as a **pure observer**: nothing in the withdrawal path calls into it, no access widener entry, and
(as everywhere in this port) **no mixins**.

## Files

| File | What it is |
|---|---|
| `core/colony/buildings/modules/WarehouseIdleTrackerModule.java` | new — the sampler, the per-item history, the NBT, and the per-colony aggregation |
| `core/commands/colonycommands/CommandColonyWarehouseStock.java` | new — `/mc colony warehousestock <colony>`, chat summary plus the CSV file |
| `core/colony/buildings/modules/BuildingModules.java:416-421` | registers `WAREHOUSE_IDLE`, deliberately **without** a view producer |
| `apiimp/initializer/ModBuildingsInitializer.java:406` | adds the module to the warehouse building entry |
| `core/commands/EntryPoint.java:70` | adds the command to the colony subtree |
| `api/util/constant/translation/CommandTranslationConstants.java:495-514` | six translation keys |
| `resources/assets/minecolonies/lang/manual_en_us.json:654-659` | their English |

## How it works

`TileEntityRack#getAllContent()` already keeps a `Map<ItemStorage, Integer>` summary of a rack's
contents up to date, so summing a whole warehouse costs one map walk per rack instead of a slot-by-slot
scan. `BuildingWareHouse` already has modules and `getContainers()`, and a module implementing
`IPersistentModule, ITickingModule` gets `onColonyTick` — the pattern `RestaurantMenuModule` uses,
including the `WorldUtil.isBlockLoaded` guard. Nothing else was needed.

Once per colony tick (500 ticks, ~25 s — colony-tick granularity is plenty for a metric measured in
days) the module sums the warehouse and compares with the previous sample. Per item type it keeps:

* **`firstSeen`** — when this type first appeared here;
* **`lastTaken`** — the last time the total went **down**;
* **a rolling 7-day withdrawal window** — seven day-buckets, newest first, rolled forward on every
  sample;
* **`totalTaken`** — lifetime, so "has this ever been used at all" survives the window rolling over.

**Only a decrease counts.** An increase is a delivery or a crafter's output and says nothing about
demand. Restocking therefore deliberately leaves the idle clock alone: a barrel of cobblestone topped
up every morning and never drawn from is exactly the junk this is meant to find.

**Two numbers, never one.** Idle age alone misjudges a thing taken rarely but regularly. "Idle 20 days,
never taken" is junk; "idle 1 day, 400 taken this week" is a consumable that must not be sold. The
report gives both, and the rate divides by the item's own observed lifetime when that is shorter than
the window, so a stack delivered an hour ago and drawn from once is not quoted at a seventh of its real
rate.

## The correctness traps

**Unloaded chunks — the one that would have ruined it.** A rack in an unloaded chunk reads as absent, so
everything only it held looks like it was just taken to zero. The load check runs over *every*
registered container **before** anything is read, and one failure throws the entire sample away and
clears the baseline flag, so the next accepted sample re-establishes counts instead of being
differenced against a stale one (`WarehouseIdleTrackerModule.java:182-193`, `285`).

**Racks added, removed, upgraded.** The sample records the set of container positions that actually
*resolved to a rack*, and treats a change in that set — or in `WarehouseModule#getStorageUpgrade()` — as
structural: counts are refreshed, no withdrawal is attributed (`:238`).

Comparing the *resolved* set rather than the building's registered container list is not a detail.
Breaking a rack with a pickaxe leaves its position registered for ever — the only caller of
`removeContainerPosition` is `TileEntityColonyBuilding`'s inventory-capability walk, and it only fires
when the position holds some *other* block entity, never when it holds air. The first version compared
registered lists, and a single broken rack therefore made every later sample "structural" and silently
stopped tracking the whole warehouse. Caught on the live server; see verification (3) below.

**Items shuffled between racks** net to zero because the totals are summed per warehouse before the
comparison. Verified rather than assumed.

**Several warehouses in one colony.** Each warehouse tracks itself; the command aggregates. For an item
type held in two of them: `count` is the sum, `taken_last_7_days` and `total_taken` are sums,
`first_seen` is the **earliest** arrival anywhere, and `days_idle` is measured from the **latest**
withdrawal in any warehouse — an item drawn from warehouse A daily is in use, whatever the untouched
copy in warehouse B suggests. When no warehouse has ever seen a withdrawal, idle age is measured from
the earliest arrival instead, so carrying a copy of a month-old item into a new warehouse does not make
it look a day old. (The first version took the max over "last activity", which had exactly that bug;
caught on the live server.)

**The NBT map cannot grow without bound.** An item type that leaves the warehouse entirely is kept for
seven days — exactly the width of the rate window — and then forgotten. After that it has no count left
to report and no withdrawals left in the window either, so its row would be empty in every column the
report has; keeping it for the window and no longer means a warehouse briefly emptied (a courier
carrying the last stack across, a rack being rebuilt) does not lose its history. What remains is
therefore bounded by what the warehouse physically holds, which is bounded by its slot count.

## Fill percentage: two numbers, not blended

* **Slot occupancy** is the headline: occupied slots / total slots. It is what actually stops a courier
  from storing anything, because a slot with three cobblestone in it is full as far as a new item type
  is concerned.
* **Capacity fill** is the second number: the goods measured in full stacks *of their own item*
  (three cobblestone = 3/64, one sword = 1/1) over the same slot count.

They are printed side by side and never averaged. The test warehouse read **596/1827 slots occupied
(32.6%) but only 8.2% of capacity** — a warehouse that is a third unusable while nearly empty by weight,
which is precisely the state the single blended percentage would have hidden.

Capacity is read live from each rack's inventory, so a storage upgrade is reflected by the next sample.
Occupied slots are counted by scanning the slots rather than by `TileEntityRack#getFreeSlots()`, and
that is a deliberate cost: `upgradeRackSize` copies the stacks into a nine-slot-larger inventory and
never re-runs `updateContent()`, so `freeSlots` keeps the pre-upgrade figure. Measured on the test
warehouse the moment the upgrade landed: 610 occupied slots reported as **1069** across 51 upgraded
racks — nine per rack, exactly. The scan is an array read per slot and cannot be wrong.

## Cost, measured

Live server, one warehouse of **51 racks / 1827 slots / 589 distinct item types**, sampler invoked 50
times in a row:

```
min 634 us   median 875 us   max 4904 us
```

Before the slot scan was added (49 racks, 603 types) the median was 629 us. Once per colony tick
(25 s) that is under 0.004 % of a server thread.

NBT: **108 830 bytes for 589 item types — 184 bytes per type**, uncompressed. The colony's uncompressed
backup file went from 13 066 to 125 884 bytes with two tracked warehouses in it; the gzipped live save
went from 2 461 to 8 988 bytes, i.e. about **11 bytes per item type on disk** after compression. A
1000-type warehouse costs roughly 184 KB in memory-format NBT and ~11 KB on disk. That is real and is
reported rather than hidden.

The module has **no view producer** and never calls `markDirty()`. Buildings are serialised
unconditionally when the colony saves, so persistence costs nothing extra, and a view would have added
a payload proportional to the item-type count to every building sync for a screen that does not exist
(a GUI tab was explicitly out of scope).

## Verification, on a live dedicated server

`/home/user/fabric-server-26.2`, port 26373, jar built from this branch. Boot: `Done (3.576s)`,
**0 errors, 10 warnings** (the baseline ten). Ticks were driven through
`RegisteredStructureManager#onColonyTick` by an out-of-repo harness, because a colony only reaches
`ACTIVE` while a player is subscribed to it and this container has no client; everything below that call
is the real path, including the per-building chunk-loaded guard.

1. **Idle grows.** Untouched stacks: 0.002 d → 2.066 d after 49 558 game ticks (2.065 days). Later the
   same items read 17.9 d.
2. **Withdrawal resets the age and moves the rate.** `dark_oak_planks` 16 → 6: `lastTaken` set,
   `total=10`, `window=10`, idle 2.075 d → 0.001 d while its neighbours kept ageing.
3. **Withdrawal still recorded with a permanently broken rack registered** (the bug in the first
   version): `gold_ingot` 24 → 14, `lastTaken=446925`, `window=10`, idle → 0.001 d.
4. **Restocking does not reset.** `gold_ingot` 14 → 78 by delivery: idle 1.017 d → 1.020 d,
   `lastTaken` and `total` unchanged.
5. **Chunk unload/reload does not corrupt anything.** Three racks were left in an unloaded chunk (six
   chunks away, so no neighbour ticket reaches them) for two in-game days and ten colony ticks: the
   sample was refused every time (`sampleTick` frozen at 471387, `baselined=false`), and `emerald`,
   which lives *only* in those racks, stayed `count=24 lastTaken=-1 total=0`. After reloading, sampling
   resumed and idle ages continued from their real values — 14.084 d → 14.095 d, not reset.
6. **Server restart preserves everything.** Across five restarts, `dark_oak_planks` kept
   `first=91212 lastTaken=140993 total=10`, and the window correctly emptied once the withdrawal aged
   past seven days.
7. **Nothing else ever produced a withdrawal.** After ~20 in-game days covering five restarts, a chunk
   unload/reload, a storage upgrade, five stacks shuffled between racks, a rack broken with its 13
   stacks, and several restocks, the CSV contained **exactly two** rows with `ever_taken=true` — the two
   item types actually withdrawn, out of 589 tracked.
8. **Forgetting works.** `gold_ore` was emptied to zero, stayed tracked at `count=0` for 10.4 days, and
   was gone from the map at 12.5 days.
9. **Fill matches a hand count.** Independent slot-by-slot count: `used=610/1863 (32.7%)`; the command
   reported `610/1863 slots occupied (32.7%)`. After a storage upgrade the capacity went 1404 → 1863
   slots and the percentage fell from 43.4 % to 32.7 %.

### What could not be verified

**A live worker or courier was not the one doing the withdrawing (✗).** A colony only ticks while a
player is subscribed to it, and this container has no Minecraft client, so no citizen AI ran. The
withdrawals were made through `rack.getInventory().extractItem(...)` — the same `IItemHandlerModifiable`
the courier and worker paths mutate, since `TileEntityColonyBuilding`'s combined handler is built out of
exactly those rack inventories. The tracker cannot tell the difference, because it only ever reads
totals, but the end-to-end "a courier took it" path is untested here.

**Nothing client-side was tested (✗)**, for the same reason. Nothing was added client-side: the module
has no view and the command prints to chat.
