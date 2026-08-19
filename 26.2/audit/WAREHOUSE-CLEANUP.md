# Cleaning the warehouse: ten options, ranked

Date: 2026-08-16. Tree: `/home/user/wt-whcleanup/26.2`, branch `claude/warehouse-cleanup`.
Written study. **No production code was written.** Every number below comes from reading this tree, from
arithmetic on the owner's `/mc colony warehousestock` CSV, or from the vanilla 26.2 server jar. Nothing was run
on a live server for this study (✗ — see *What could not be verified*).

Prerequisite reading: `audit/WAREHOUSE-IDLE.md` and `core/colony/buildings/modules/WarehouseIdleTrackerModule.java`.

---

## Summary

**Start with the two things that cost nothing and are already in the game**: press the Sort button, and buy the
remaining rack-storage upgrade. Together they are worth **1300 to 5200 slots** against a warehouse that is
currently 2455 slots deep. Every software option in this report is smaller than that.

**The single thing I would build first is a one-line fix**, because without it the storage upgrade the player
pays an emerald block for does not work at the exact moment they need it: `TileEntityRack#upgradeRackSize`
(`core/tileentities/TileEntityRack.java:323`) never recomputes `freeSlots`, so a full rack still reports zero
free slots after being made nine slots larger, and `TileEntityWareHouse#getRackForStack` therefore refuses to
put anything in it.

**The thing I would not build is the sell-junk feature.** It is the largest job in this report, it is the only
one that can destroy a player's goods irreversibly, this mod has no economy to hang it on, and the owner's own
data cannot yet tell junk from working stock.

---

## The data, and what it does and does not say

One warehouse: **2455 of 4455 slots occupied (55.1 %)** holding **66 963 items = 1702 stack-equivalents
(38.2 % of capacity)**. **564 rows, 345 distinct item ids.**

Two consequences follow arithmetically and are used throughout:

* **Fragmentation costs 753 slots.** 2455 occupied − 1702 stack-equivalents = 753 slots holding part-stacks.
* **Of those, between 409 and 753 are recoverable by defragmenting.** 219 of the 564 rows are NBT variants of
  gear (books, bows, boots, rods, netherite) whose stack size is 1; for those, `ceil(count/maxStack) = count`
  exactly and no packing can help. At most 345 rows can round up, each by less than one slot, so a perfectly
  packed warehouse needs **at least 1702 and fewer than 2047 slots** — a saving of **more than 408 and at most
  753 slots, 9–17 % of the whole warehouse**.
* **219 slots are permanently unrecoverable** and will grow: damaged and enchanted gear cannot stack, ever.

**`ever_taken` is false on every row, and every row's idle age equals its first-seen age (0.906 days).** The
tracker has run only since the last restart. **The idle metric cannot yet distinguish junk from working stock in
this data, and nothing in this report rests on it.** Cobblestone (9673) and dirt (8491) look exactly like
`enchanted_book` in this CSV — never withdrawn, 0.906 days old — and one of them is the builder's most-consumed
resource. This is why option 7 says *wait*.

### How much capacity is available without any code at all

Capacity is `Σ over registered racks of (27 + 9 × storageUpgrade)`
(`Constants.DEFAULT_SIZE = 27`, `SLOT_PER_LINE = 9`, `BuildingWareHouse.MAX_STORAGE_UPGRADE = 3`). All racks of a
warehouse are always at the same size: `BuildingWareHouse#upgradeContainers` (`:164`) upgrades every registered
container, and `registerBlockPosition` (`:121`) brings a newly placed rack up to the current level.

4455 has exactly two decompositions for an upgrade level of 0–3:

| upgrade level | slots/rack | racks | upgrades left | slots they would add |
|---|---|---|---|---|
| 0 | 27 | **165** | 3 | **+4455** (→ 8910, double) |
| 2 | 45 | **99** | 1 | **+891** (→ 5346) |

(36 and 54 slots per rack do not divide 4455.) Which one it is, I could not determine from the CSV (✗) — but
either way the player has **at least 891 slots and possibly 4455** waiting behind one to three emerald blocks.
For scale: the largest stock level-5 warehouse blueprint in this tree contains **45 racks** (counted directly out
of the blueprint NBT across all 22 styles; the smallest has 3), so the owner has already hand-placed 54–120 extra
racks. Adding more racks inside the warehouse's registered area works and is unlimited.

---

## Where the junk comes from

The general inflow path, with no filter anywhere along it:

```
item on the ground  →  EntityCitizen#onServerUpdateHandlers (every 20 ticks, EntityCitizen.java:759)
                    →  CitizenItemUtils#pickupItems  (4×2×4 box, CitizenItemUtils.java:247)
                    →  CitizenItemUtils#tryPickupItemEntity  (:41)  — gated only by
                       AbstractJob#pickupSuccess, which returns true (AbstractJob.java:116)
                    →  worker inventory
                    →  AbstractEntityAIBasic#dumpOneMoreSlot (:1349) — keeps only what
                       IBuilding#buildingRequiresCertainAmountOfItem says to keep; the default
                       (AbstractBuilding.java:1204) returns the whole stack for anything not in keepX
                    →  worker's hut racks
                    →  EntityAIWorkDeliveryman#pickupFromBuilding (:222) → workerRequiresItem (:293),
                       the same rule again
                    →  courier → TileEntityWareHouse#dumpInventoryIntoWareHouse (:126)
                    →  warehouse, where nothing ever removes it.
```

Every citizen in the colony is a vacuum cleaner with a four-block reach, and the warehouse is where the bag is
emptied. That is the mechanism behind every oddity below.

| what | where it comes from | code |
|---|---|---|
| **26 rows `enchanted_book`** | Two producers. (a) The **enchanter**: every level-1..5 enchanter recipe has `ENCHANTED_BOOK` as a secondary output, drawn from loot table `minecolonies:recipes/enchanter{1..5}`, one random enchantment each, so each is its own `ItemStorage` row. Its input is `ancientTome`, which **every raider loot table drops** (weights 3–50). (b) Vanilla fishing treasure. **Nothing in the mod ever consumes an enchanted book** — grep for `ENCHANTED_BOOK` finds three producers, one research icon, one recruitment cost paid from the *player's* inventory, and no recipe input. | `DefaultEnchanterCraftingProvider.java:377-408`, `DefaultEntityLootProvider.java:45-120`, `DefaultFishermanLootProvider.java:58` |
| **25 rows `bow`, 18 `leather_boots`, 16 `fishing_rod`, 22 saddles, 280 rotten flesh, ~2100 raw fish** | **The fisherman.** `ModLootTables.FISHING` is weight 10 junk / 5 treasure / 85 fish, and junk and treasure are straight references to vanilla `BuiltInLootTables.FISHING_JUNK` and `FISHING_TREASURE`. Vanilla junk is damaged leather boots, damaged fishing rods, rotten flesh, lily pads, sticks, string, bowls; vanilla treasure is enchanted bows, enchanted books, enchanted fishing rods, name tags, nautilus shells and **saddles**. Random damage and random enchantments mean every single one is a distinct row. | `DefaultFishermanLootProvider.java:42-67` |
| **142 sponges** | **The fisherman again**, bonus table, level 4–5 hut only: `Items.SPONGE` at weight 1 in 1000 with a per-skill-point quality bonus — 0.1 % at skill 0, **7.77 % at skill 100**. The comment in the file spells the numbers out. | `DefaultFishermanLootProvider.java:69-102` |
| **14 rows `netherite_chestplate`, 14 rows `netherite_helmet`** | **Guard gear cycling.** 14 *distinct* rows means 14 distinct damage values, i.e. worn pieces. `AbstractEntityAIFight#atBuildingActions` (`:162`) walks the guard's needed gear, and when it finds a better piece in the hut it **moves the currently worn piece back into the hut** (`transferItemStackIntoNextFreeSlotInProvider`) before equipping the new one. `AbstractBuildingGuards`'s keep list holds exactly **one** item per equipment slot (`keepX.put(..., new Tuple<>(1, true))`, `:171-183`), so the second chestplate onwards is "not required" and the courier takes it to the warehouse. Netherite specifically because the level-5 gear entry is built with `ARMOR_LEVEL_MAX = Integer.MAX_VALUE` (`EquipmentLevelConstants.java:62`, used at `AbstractEntityAIFight.java:88`). Which producer made them — the blacksmith's netherite smithing recipes (`DefaultBlacksmithCraftingProvider.java:50-58`) or free-mode conjuring via `checkForToolOrWeaponAsync` → `FreeMode#equip`/`#fulfil`, whose `pick` deliberately takes the **highest** equipment level for a `Tool` — I cannot tell from the CSV (✗). A tell: free mode conjures all four slots, so netherite helmets and chestplates *without* matching leggings and boots points at the blacksmith and at wear, not at free mode. | `AbstractEntityAIFight.java:88,162-278`; `AbstractBuildingGuards.java:171-193`; `FreeMode.java:139-165, 402-426` |
| **46 poisonous potatoes** | Vanilla potato crop drop, 2 % per harvest, from the farmer. 7158 potatoes at 2 % would be ~146; 46 is consistent with a smaller cumulative harvest than the current stock. |  |
| **2155 `leaf_litter`** | Not produced by the mod. In 26.2 `minecraft:leaf_litter` is a worldgen ground cover (`birch_leaf_litter` and friends) and a *smelting product of leaves*. Any block-breaking worker standing on it collects it, and it reaches the warehouse by the generic path above. Which worker in this particular colony, I cannot say (✗). Note it is **not** in `minecraft:leaves` (checked in the server jar), so it is **not** compostable by the default tags. |  |
| **86 `simpleplanes:plane`** | **Cannot be determined from this repository (✗).** `simpleplanes` is not in this tree and not in `/home/user/fabric-server-26.2/mods`. Nothing in MineColonies produces a foreign mod's item deliberately: the only registry-wide item source is `FreeMode#candidatesFor`, and its `pick` takes `candidates.get(0)` of `CompatibilityManager#getListOfAllItems`, which is built in creative-tab order (`CompatibilityManager.java:668-713`) and so returns a vanilla item for any ordinary deliverable. The overwhelmingly likely path is the generic one at the top of this section: a plane item on the ground within four blocks of any citizen — dropped by the player, or by a plane being broken — is picked up within one second and is in the warehouse by the next courier round. If planes have a stack size of 1, those 86 items are **86 slots, 3.5 % of the occupied warehouse**. |  |

### The conclusion this forces

**Six of the seven worst NBT-variant categories come from one worker: the fisherman.** 26 + 25 + 18 + 16 rows of
books, bows, boots and rods, plus 142 sponges, 22 saddles and 280 rotten flesh, is one hut's loot table. Disposal
is the wrong fix for that; *not fishing it up* is the right one, and it is option 6.

**Nothing in the colony consumes gear.** There is no meltdown recipe, no repair sink, no smelter armour
breakdown in this tree. Every enchanted book and every damaged netherite plate that arrives is there for ever.
That is a one-way ratchet, and it is why the 219 permanently-unstackable slots will keep growing whatever else
is done.

---

## What already exists — do not build these

| already in the mod | where | what it means |
|---|---|---|
| **A full-warehouse chat notification** | `TileEntityWareHouse.java:140-158`, keys `com.minecolonies.coremod.warehouse.full{,.level5,.max}` | Already sent to **all players of the colony**, throttled to one per five minutes (`TICKS_FIVE_MIN`), and already picks a different message depending on whether the building can still be levelled, storage-upgraded, or neither. The owner's "chat notification when the warehouse is full" **exists**. |
| **Full stack defragmentation, on a button** | `SortingUtils#sort`, `SortBuildingMessage`, `WarehouseOptionsModuleWindow#sortWarehouse:199`, gated on building level ≥ 3 | The Sort button in the warehouse GUI empties every slot into a map keyed by `ExactMatchItemStorage`, merges the counts, and re-inserts full stacks grouped by creative tab. That *is* defragmentation, across all racks at once. It is manual and nothing tells the player when it is worth pressing. |
| **A rack storage upgrade** | `UpgradeWarehouseMessage`, `BuildingWareHouse#upgradeContainers:164` | One emerald block, +9 slots on every rack, three times, at max building level. See the table above. |
| **A read-only "everything in this warehouse" screen with a text filter and a rack locator** | `WindowHutAllInventory.java` | Lists every `ItemStorage` with counts, sortable five ways, filterable by name, with a button that highlights the racks holding a given item. This is the natural base for any sell/dispose GUI: it already does 90 % of the work. |
| **A way to get any item out of the warehouse by hand** | `PostBox`, `PostBoxRequestMessage`, `WindowPostBoxMain` | The player asks a postbox for N of item X; a `Stack` request goes to the warehouse resolver; a courier delivers it; the player takes it and does whatever they like with it. Manual disposal already works, one item type at a time. |
| **An item sink for the organic bulk: the composter** | `EntityAIWorkComposter.java:190-208, 422`, `BuildingComposter.java:80`, `ItemListModule` id `compostables` | This is the important one. See option 4. |
| **No trade, sale, shop, currency or price mechanism of any kind** | grep for `trade\|sell\|merchant\|shop\|buy` across `src/main/java` finds only prose in comments | Anything resembling "sell junk" is built from nothing. |
| **No config knob for warehouses, dumping or couriers** | `ServerConfiguration.java`, 84 options, none related |  |
| **No mixins, and no mixin config** | no `*.mixins.json`, no `*Mixin*.java`, no `mixin` key in `fabric.mod.json` | Every option below is judged against this. All ten are achievable with zero mixins. |

---

## What a full warehouse actually does today — the baseline

This matters because the owner asked for a notification, and the notification is not the missing piece.

1. `EntityAIWorkDeliveryman#dump` (`:303`) walks the courier to the warehouse and calls
   `dumpInventoryIntoWareHouse`.
2. `TileEntityWareHouse#dumpInventoryIntoWareHouse` (`:126`) loops over the courier's slots. For each stack it
   calls `getRackForStack` → a rack already holding it *with a free slot*, else a rack holding something similar
   *with a free slot*, else the emptiest rack. If all three fail it sends the chat message (at most once per five
   minutes, colony-wide) and **`return`s — abandoning the rest of the courier's inventory as well**.
3. The courier goes back to `START_WORKING`. `decide()` (`:664`) sees a non-empty inventory and, for any pending
   task, returns `DUMPING` again.
4. **That is a closed loop.** The courier walks to the warehouse, fails, walks back to the warehouse, fails,
   for ever. It never reaches `PREPARE_DELIVERY`, so it never *takes* anything out of the warehouse either.

So the honest description of a full warehouse is not "the player is not told". It is: **the player is told once
every five minutes, in a line of chat with no numbers in it, while every courier in the colony silently stops
delivering and the colony grinds to a halt for reasons the message does not mention.**

There is a second-order trap on top of it. `getRackForStack`'s first two probes require `rack.getFreeSlots() > 0`,
so **a rack with a half-full stack of cobblestone and no empty slot will not accept more cobblestone**, even
though it physically could. That is one of the ways the 753 fragmented slots got there.

And the third-order trap, which is option 1:
`TileEntityRack#upgradeRackSize` (`:323`) builds a nine-slot-larger `RackInventory`, copies the stacks in, and
assigns it to `inventory` — **after** the copy loop. Each `setStackInSlot` in that loop fires
`onContentsChanged` → `updateItemStorage()` → `updateContent()` (`:391`), which recomputes `freeSlots` **over the
old, still-assigned inventory**. After the assignment nothing recomputes anything. So a rack that was full before
the upgrade reports `getFreeSlots() == 0` after it. `getPositionOfChestWithItemStack` and
`getPositionOfChestWithSimilarItemStack` reject it (both require `> 0`), and `searchMostEmptyRack` (`:244`)
rejects it too — `isEmpty()` is false and `0 > 0` is false — so it returns `null`.

**A player who pays an emerald block to enlarge a full warehouse gets nothing.** Each rack heals itself the first
time something is extracted from it (extraction *does* fire `onContentsChanged` against the new inventory), and
all of them heal on a chunk reload or server restart, because `loadAdditional` calls `updateContent()` directly.
But in the exact situation the upgrade exists for — every rack full, every courier stuck in the `DUMPING` loop
and therefore extracting nothing — nothing extracts, and the deadlock survives the purchase.

This is derived from reading the code, not observed on a running server (✗). It does **not** distort the owner's
current CSV: the tracker has only been running since the last restart, and a restart re-runs `updateContent()` on
every rack.

---

## The ten options, ranked

Ranked by value to the player divided by cost and risk. Not by how interesting they are.

---

### 1. Make the storage upgrade actually free the slots it was paid for

**What the player sees.** They pay an emerald block to enlarge the warehouse racks, and the couriers immediately
start using the new space. Today, if the warehouse was completely full when they paid, nothing happens until they
reload the world.

**How it is built here.** One line in `core/tileentities/TileEntityRack.java#upgradeRackSize` (`:323`): after
`inventory = tempInventory;`, call `updateItemStorage()` (public, `:353`) so `updateContent()` recomputes
`freeSlots` and `content` against the new inventory. `BuildingWareHouse#upgradeContainers` (`:164`) is the only
caller and needs no change. **1 line, plus a comment explaining why, plus a line in the changelog.**

**Zero mixins?** ✓. It is one call inside a class this port already owns.

**What it can break.** Almost nothing. `updateItemStorage` additionally toggles the rack's full/empty block-state
variant and calls `setChanged()`; running it once more per rack per upgrade is one extra `updateContent()` pass
(one array read per slot, ≤ 54 reads) and at most one `setBlockAndUpdate` that would have been correct anyway.
The one thing to watch on a busy server: `upgradeContainers` loops over every rack, so this adds
`racks × slots` array reads to a single button press — 99 × 54 ≈ 5 300 reads, immeasurable. The current
behaviour is already the buggy one, so there is no regression risk in the sense of losing anything a player has.

**Cost.** 1 line, server only, no client work, no GUI. Verifiable on a headless server by upgrading a rack that
has been filled to capacity and reading `getFreeSlots()`.

---

### 2. Defragment automatically, and show the player the two numbers

**What the player sees.** The warehouse screen gains one line: "2455 of 4455 slots used (55 %) — but only 38 % by
capacity. Sorting would free about 500 slots." The Sort button next to it is no longer a mystery, and when
occupancy passes a threshold the warehouse sorts itself once, on its own, and says so.

**How it is built here.** The work is already done — `SortingUtils#sort` is a complete defragmenter and
`SortBuildingMessage`/`WarehouseOptionsModuleWindow#sortWarehouse:199` already drive it. Two pieces are missing.
(a) *The numbers.* `WarehouseIdleTrackerModule` already computes and stores `lastUsedSlots`, `lastTotalSlots`,
`lastItemCount` and `lastStackEquivalents` every colony tick. It is registered in `BuildingModules.java:419`
**with a null view producer**, deliberately. Give it a four-`int`/one-`double` view — a
`serializeToView` of five values, a `WarehouseIdleTrackerModuleView`, and five lines in
`WarehouseOptionsModuleWindow#updateResourcePane` — and the payload stays constant-size, unlike the per-item-type
view the module's comment rejects. (b) *The auto-sort.* `WarehouseIdleTrackerModule` already implements
`ITickingModule`; in `onColonyTick`, after the sample, if `usedSlots / totalSlots > 0.85` and
`usedSlots − stackEquivalents > 200` and the last auto-sort was more than an in-game day ago, call
`building.sort(...)` exactly as `SortBuildingMessage` does. **~60 lines module + view, ~30 lines GUI, ~40 lines XML
and lang.**

**Zero mixins?** ✓.

**What it can break.** `SortingUtils#sort` empties every slot of the building's `CombinedItemHandler` and
re-inserts. It takes an NBT backup first and restores it on any exception, which is the right shape, but:
*(i)* on a warehouse this size the backup is a serialisation of ~2455 stacks on the server thread; *(ii)* every
`setStackInSlot` fires `onContentsChanged` → `updateItemStorage` → `updateContent`, which is O(slots of that
rack), so the whole sort is O(total slots × rack size) ≈ **400 000 inner iterations for 4455 slots**, plus a
block-state check per rack. I have not measured it (✗) and it must be measured before being put on a timer.
*(iii)* On a busy server the dangerous window is a sort running while a courier is mid-`dumpInventoryIntoWareHouse`
or a worker mid-extraction: both mutate the same `IItemHandler`s, both run on the server thread, and today the
sort only ever happens on a player's click, i.e. rarely and never twice at once. Putting it on a tick makes
concurrent-ish interleaving routine. *(iv)* Sorting renumbers every slot, which invalidates any cached slot index
— `EntityAIWorkDeliveryman#pickupFromBuilding` keeps `currentSlot` across AI ticks, and
`AbstractEntityAIBasic#dumpOneMoreSlot` keeps `slotAt`. Neither will lose items (both re-read the slot), but both
can skip or double-visit stacks for one pass.

Nothing is destroyed. Worst case the sort throws and the backup is restored.

**Cost.** ~130 lines Java + XML + lang. **Needs client work** for the readout, which cannot be verified in this
container (✗ — no display). The auto-sort half is server-only and can be verified headless.

**Value.** 409–753 slots, computed above, plus the first honest capacity number the player has ever seen.

---

### 3. Make the full-warehouse warning tell the truth, and stop the couriers deadlocking

**What the player sees.** Instead of "The Warehouse is full, please upgrade it!" once every five minutes, they
get, once, a message that says how full it is, how many slots sorting would recover, how many rack upgrades are
left, and which courier is stuck — and a persistent entry in the citizen interaction list that does not scroll
away. The couriers stop pacing to the warehouse and back, and go and do the deliveries they can still do.

**How it is built here.** Three changes, all server side.
(a) *The message.* `TileEntityWareHouse.java:140-158` already picks between three keys; add the numbers from the
`WarehouseIdleTrackerModule` getters (`getUsedSlots`, `getTotalSlots`, `getStackEquivalents`) and from
`WarehouseModule#getStorageUpgrade`, and raise it as a `StandardInteraction`/`PosBasedInteraction` on the
courier's citizen data as well as chat — exactly the pattern `EntityAIWorkDeliveryman#deliver` already uses for
`COM_MINECOLONIES_COREMOD_JOB_DELIVERYMAN_CHESTFULL` (`:466-479`). ~35 lines plus lang keys.
(b) *The dump loop.* `dumpInventoryIntoWareHouse` `return`s on the first stack it cannot place, abandoning the
rest of the inventory even though a later stack might fit a rack that already holds it. Change `return` to
`continue` and return a count of what was placed. ~10 lines.
(c) *The deadlock.* Have `dump()` (`EntityAIWorkDeliveryman.java:303`) notice that nothing at all was placed and
go to `START_WORKING` in a mode where `decide()` is allowed to serve `PickupRequest`s and, more importantly, to
walk on to a `DeliveryRequest` whose goods it is already carrying, rather than insisting the pack be emptied
first. The minimal version is a boolean on the AI ("the warehouse refused me this round") that suppresses the
`DUMPING` branch in `decide()` for a few minutes. ~25 lines.

**Zero mixins?** ✓.

**What it can break.** (b) is safe. (c) is the risky one and must be done carefully: `decide()`'s "dump before
delivering" rule exists so that `prepareDelivery` starts from a known-empty pack, and the port note at
`EntityAIWorkDeliveryman.java:342-352` records that the unload loop was only recently taught to tell one stop's
goods from another's. Suppressing the dump means a courier can enter `PREPARE_DELIVERY` holding items nobody
asked for; the current `owed`-map loop handles that correctly, but it is exactly the invariant that was broken
once already. On a busy server with twenty couriers the failure mode is subtle: couriers that never empty their
packs slowly reduce the colony's effective carrying capacity, because `cannotHoldMoreItems()` is a function of
stack count. Bound the suppression in time, and re-test the multi-stop round.

**Cost.** ~70 lines, server only, no GUI. Fully verifiable headless.

---

### 4. Drain the organic bulk through the composter, which already does exactly this

**What the player sees.** They open the composter's Compostables tab, tick potato, carrot, wheat seeds, raw cod,
raw beef and rotten flesh, and the couriers start carrying them out of the warehouse to the barrels, where they
become compost that the farmer, florist and plantation then use. No new screen, no new button, no code.

**How it works, and why this is not speculation.** `EntityAIWorkComposter` (`:190-208`) files a
`StackList` request for `64 × barrels` of every item ticked in the hut's `ItemListModule` with id
`compostables` (`BuildingComposter.COMPOSTABLE_LIST`). A `StackList` is served by the ordinary request system,
which means the **warehouse resolver serves it and a courier delivers it**. `BuildingComposter`'s keep list
(`:80`) then keeps compostables in the composter for ever, so they never come back.

Checked against the real tags in this tree (`src/main/generated/data/minecolonies/tags/item/compostables*.json`)
and the vanilla 26.2 server jar:

| owner's stock | compostable? |
|---|---|
| potato 7158, carrot 4696 | ✓ `#c:crops` → `#c:crops/potato`, `#c:crops/carrot` |
| wheat_seeds 1731 | ✓ `#c:seeds` (via `compostables_poor`) |
| ~2100 raw fish and meat | ✓ `#c:foods/raw_fish` (cod, salmon, tropical fish, pufferfish), `#c:foods/raw_meat` (beef, porkchop, chicken, rabbit, mutton) |
| rotten flesh 280 | ✓ named explicitly in `minecolonies:compostables` |
| poisonous potato 46 | likely, via the `minecolonies:food` ingredient with `max-saturation 0.5` in `compostables_poor` — not individually verified (✗) |
| **leaf_litter 2155** | **✗ no.** `minecraft:leaf_litter` is **not** in `minecraft:leaves` — verified by extracting `data/minecraft/tags/item/leaves.json` from `server-26.2.jar`, which lists eleven leaf blocks and not leaf litter. |
| cobblestone, dirt, copper, bamboo | no, and they should not be — they are builder and crafter stock |

That is roughly **16 000 items, about 250 slots, disposed of in-fiction, reversibly, using nothing but
checkboxes**.

**What it can break — and this is why it is fourth and not first.** Potato, carrot, raw beef and raw fish are
**food**. The restaurant's `RestaurantMenuModule` files `MinimumStack` requests for menu items and their raw
inputs, and the cook needs raw stock. A composter set to eat potatoes competes with the kitchen through the same
request system, and the composter asks for `64 × barrels` at a time. With ten barrels that is 640 potatoes per
request. Compost the surplus, not the staple: tick rotten flesh and wheat seeds without hesitation, tick potato
and carrot only after checking the restaurant's menu, and never tick something the colony is short of. On a busy
server the sharp edge is a composter with many barrels and a high `MIN` setting outbidding the restaurant during
a food shortage.

**Zero mixins?** ✓ — there is no code at all.

**Cost.** **0 lines** for the benefit. Optionally ~30 lines to add a warehouse-aware guard so the composter's
`StackList` asks only for stock above a floor, which would remove the food risk. That guard is worth building;
the checkbox is worth using tonight.

---

### 5. A "do not store" list on the warehouse — the filter at the courier's end

**What the player sees.** A new tab on the warehouse with a searchable item list and checkboxes, like the
composter's Compostables tab. Anything ticked is never carried into the warehouse again. It stays in the hut that
made it, which fills up and complains, so the player finds out where the junk is coming from instead of finding
it in the warehouse a month later.

**How it is built here.** The list itself is `ItemListModule` (163 lines, already generic, takes a string id) plus
`ItemListModuleView` (153) plus `ItemListModuleWindow` (232) plus the two existing list messages — all of it
already exists and is used by the composter, florist, sifter and others; the warehouse just gains
`.addBuildingModuleProducer(...)` in `ModBuildingsInitializer.java:398-408` and an entry in
`BuildingModules.java`. The hook is one line in `EntityAIWorkDeliveryman#workerRequiresItem` (`:293`) — or better,
in `pickupFromBuilding` (`:222`) before the `workerRequiresItem` call, so the courier does not even pick it up:
consult the warehouse's list module and return 0. **~40 lines of new Java on top of ~550 lines of existing
machinery, plus one `layouthuts` XML file (~50 lines, copy `layoutfilterablelist.xml`) and ~6 lang keys.**

**Zero mixins?** ✓.

**What it can break.** Nothing is destroyed — this is the non-destructive twin of option 10, and that is the whole
argument for it. But the junk has to go *somewhere*: it accumulates in the producing hut instead, and a full
worker hut has its own failure mode (`AbstractEntityAIBasic#dumpOneMoreSlot` cannot place, the worker's inventory
stays full, the worker stalls). Two specific traps: *(i)* the courier's pickup is driven by
`buildingRequiresCertainAmountOfItem`, and blocking an item the *producing hut itself* wants to be rid of will
pin it there; *(ii)* an over-eager list containing something a crafter needs as an *input* would silently starve
the crafter, because the warehouse would never accumulate it. The list must be exclusion-only and must never be
consulted on the delivery side. On a busy server the visible symptom is one hut going red while the warehouse
looks healthy — better than the reverse, but only if the message says which hut.

**Cost.** ~90 lines new Java + XML + lang, on ~550 lines of reused machinery. **Needs client work** (✗ not
verifiable here). The server half is verifiable headless.

---

### 6. Stop the fisherman producing the junk in the first place

**What the player sees.** A setting on the fisherman's hut: *Catch* — `Fish only` / `Fish and treasure` /
`Everything`. Set to `Fish only` and the enchanted books, bows, boots, rods, saddles, sponges and rotten flesh
stop arriving. The fish keep coming.

**How it is built here.** `ModLootTables.FISHING` is a single pool of three nested table references with weights
10 (junk), 5 (treasure) and 85 (fish), and the treasure branch is already conditional
(`EntityInBiomeTag.of(IS_OCEAN)` or the `FISH_TREASURE` research) — the shape for conditioning it further is
already there. Two ways: (a) generate three variants of `minecolonies:fishing` in `DefaultFishermanLootProvider`
and have `EntityAIWorkFisherman` pick the table by setting; (b) leave the tables alone and filter the rolled
loot in the fisherman AI before it enters the inventory. (b) is smaller and does not touch datagen. The setting
itself is a `StringSettingWithDesc` in the existing `SettingsModule` — the same three-line pattern as
`GuardTaskSetting`, plus one entry in the fisherman's `BuildingEntry` and one XML row.
The sponge bonus table is separate (`FISHERMAN_BONUS.get(4|5)`) and would want the same treatment.
**~40 lines Java + a datagen change + ~6 lang keys, and `runDatagen` as its own invocation if (a).**

**Zero mixins?** ✓.

**What it can break.** Little, and it is reversible by flipping the setting back. The two honest costs: the
fisherman's *experience* and the colony's supply of nautilus shells, name tags and sponges disappear with the
junk, and some players want those; and if implemented as (b), items are rolled and then discarded, so the loot
table's `EnchantedCountIncreaseFunction` and any datapack the player has layered on top still run — a pack that
adds valuable modded loot to `minecraft:fishing_treasure` would have it silently voided. Default the setting to
today's behaviour and it breaks nobody.

**Cost.** ~40 lines. **Needs a client GUI row**, but only a row in an existing settings list — the cheapest
possible client work. Server side verifiable headless.

**Why it is worth more than it looks.** This is the only option in the list that reduces the *permanently
unstackable* 219 slots, which is the part of the problem that nothing else can touch.

---

### 7. Use the idle metric — in two weeks, not now

**What the player sees.** `/mc colony warehousestock` gains a `junk_candidates` section, and the warehouse GUI a
"nobody has touched this in N days" column: items with `ever_taken = false` after more than, say, fourteen days
of continuous tracking, sorted by slots wasted. That is the list the player acts on, by hand, through the postbox
or the sell GUI or a bin.

**How it is built here.** Nearly nothing new. `WarehouseIdleTrackerModule.Aggregate` already carries
`everTaken`, `getIdleDays`, `getAgeDays`, `getTakenPerDay` and `takenInWindow`, and
`CommandColonyWarehouseStock` already sorts by idle time and writes them all to CSV. The addition is a filter and
a slot-cost column (`ceil(count / maxStackSize)`), ~40 lines in the command, plus the view from option 2 if it is
to appear in the GUI.

**Zero mixins?** ✓.

**What it can break.** Nothing — it is a report. **The risk is that somebody builds an automatic action on top of
it too early.** The owner's current CSV has `ever_taken = false` on all 564 rows, including 9673 cobblestone and
8491 dirt, which are the builder's staples. Anything that deleted or sold "everything never taken" today would
sell the colony's construction materials. The tracker also loses its baseline (deliberately, and correctly) on
every server restart, unloaded chunk and rack change (`WarehouseIdleTrackerModule.java:174-194, 238, 396`), so
`ever_taken` needs a long uninterrupted run to mean anything.

**Cost.** ~40 lines, server only. **But the right action now is to do nothing and let it run.** Come back when
`ever_taken` is true for more than a handful of rows out of 564.

---

### 8. Capacity-aware worker behaviour

**What the player sees.** When the warehouse passes some fill threshold, producers slow down or stop: the
fisherman stops fishing, the farmer stops harvesting, the lumberjack stops felling, until space appears.

**How it is built here.** A helper on `IWareHouse` returning fill fraction (the module from option 2 already
computes it), consulted in each producer AI's decision state — `EntityAIWorkFisherman`, `EntityAIWorkFarmer`,
`EntityAIWorkLumberjack` and so on — or, more centrally, in `AbstractEntityAIBasic`'s dump path so a worker whose
hut is full and whose warehouse is full simply idles instead of thrashing. ~15 lines per worker across a dozen
workers, or ~60 lines in one shared place plus a `SettingsModule` toggle. **~150–250 lines.**

**Zero mixins?** ✓.

**What it can break.** A great deal, quietly. This is the option I would be most careful with despite it being
the cleanest-sounding. Stopping the farmer stops food; stopping the lumberjack stops the builder; stopping
production because the warehouse is 90 % full of *junk* punishes the colony for the junk instead of removing it.
Worse, MineColonies workers are already prone to burning their whole day in a decision state — the port notes at
`EntityAIWorkDeliveryman.java:119-124` and `audit/WORKER-AUDIT-2.md` §1 record two separate cases of exactly that
— and an extra global stall condition is the easiest possible way to reintroduce one. On a busy server the
failure mode is a colony that looks fine and produces nothing, with no message explaining why.

**Cost.** ~150–250 lines, server only, plus a setting row client side. High test burden: every affected worker
needs a soak run.

**Verdict.** It treats the symptom, it is the most expensive way to do so, and it makes the colony worse at the
moment the player most needs it to work. **I would not build this**, or if it must exist, build it as a *warning*
only — "the warehouse is 92 % full and the fisherman is still fishing" — which is option 3 with a different
trigger and a tenth of the risk.

---

### 9. Sell junk: the trade function, the button and the sell-list GUI

Judged as one option because the three cannot be built separately and be worth anything. A button with no price
table sells at made-up prices; a GUI with no sale does nothing.

**What the player sees.** A new tab on the warehouse listing everything it holds, with a checkbox and a quantity
per row and a price in emeralds beside each. They tick the 26 enchanted books, the 142 sponges and the 25 bows,
press *Sell*, and get emeralds in the warehouse and the slots back.

**How it is built here.** Four separate pieces, none of which exist:
1. *A price table.* MineColonies has no currency, no economy and no prices — grep confirms it. A price for every
   item in a modded registry has to come from somewhere: a datapack the player writes, vanilla villager trade
   tables reverse-engineered, or a crafting-cost heuristic. All three are wrong in different ways, and all three
   are exploitable: any price that is not below the crafting cost turns the colony's crafters into a money
   printer. This is the actual work, and it is a design problem, not a coding one. **~200–400 lines plus a
   datapack format plus datagen.**
2. *A fiction for who buys.* The tavern's visitor system (`TavernBuildingModule`, `VisitorData`,
   `RecruitmentInteraction`) is the only merchant-shaped thing in the mod, and it takes items **from the player's
   own inventory**, not from the warehouse (`RecruitmentInteraction.java:140-165`). Reusing it means either a new
   visitor type or a new building. **~200–300 lines.**
3. *The sale itself.* Extracting N of item X from the racks and inserting emeralds is the easy part:
   `InventoryUtils` and the existing `CombinedItemHandler` do it. **~80 lines**, plus a server message
   (`AbstractBuildingServerMessage`, ~70 lines, cf. `AddMinimumStockToBuildingModuleMessage`).
4. *The GUI.* Copy `WindowHutAllInventory` (which already lists every `ItemStorage` with counts, a text filter,
   five sort modes and a rack locator) and add a checkbox column, a quantity field and a total. **~250 lines +
   ~80 lines XML + ~15 lang keys.** For calibration, the entire minimum-stock feature — module, view, two
   messages, window, two interfaces — is **645 lines** in this tree.

**Total: 800–1200 lines**, roughly ten times option 5 and a hundred times option 1.

**Zero mixins?** ✓ — nothing here needs one. That is not the constraint.

**What it can break.** This is the only option that destroys the player's property, and it destroys it
irreversibly. The failure modes:
* **Selling working stock.** The idle metric cannot yet tell junk from stock (see the data section). Sell
  "everything untouched for a week" today and 9673 cobblestone and 8491 dirt go with it, and the builder stops.
* **Selling into a pending request.** The request system holds *promises*: a builder's material list, a
  `MinimumStack` from the restaurant, a `Delivery` already claimed by a courier that has not walked to the rack
  yet. Extracting stock the resolver has already committed makes requests fail after the fact, and the failure
  surfaces as a stalled worker somewhere else entirely. Any sale must consult
  `IBuilding#isItemStackInRequest`/the open request set before extracting, and even that races.
* **Selling the wrong NBT variant.** 219 of the rows are one-of-a-kind gear. An item list keyed on item id rather
  than `ItemStorage` will sell the guard's good netherite chestplate along with the fourteen worn ones — note
  `ItemStorage#equals` (`:264`) compares damage **and** components by default, so the distinction exists, and any
  UI that groups by id throws it away.
* **The busy-server ones.** Two players open the sell tab at once and both sell the same 26 books: the second
  sale extracts what is no longer there and either silently sells nothing or, if written naively against a cached
  client-side view, sells something else that happened to move into that slot. The GUI reads
  `Minecraft.getInstance().level.getBlockEntity(...)` (`WindowHutAllInventory.java:133`), i.e. the **client's**
  copy, which lags the server and is blank for racks in chunks the client has not loaded — so the list the player
  ticks is not the list the server sells. Any sell message must carry `ItemStorage` + count and be re-validated
  server side, never slot indices.
* **No undo.** There is none, and adding one means an escrow, which is another feature.

**Cost.** 800–1200 lines. **Substantial client work**, unverifiable in this container (✗ — no display), on the
riskiest surface in the report.

**Verdict.** **I would not build this now.** Options 1, 2, 3 and 4 recover more slots than the owner's entire
junk pile is worth, at under 5 % of the cost and with nothing destroyed. If it is built later, build it *after*
option 7 has fourteen days of real idle data, because without that the player is being asked to guess which of
564 rows they can afford to lose — and the whole point of the tracker was to answer that question.

---

### 10. Automatic disposal rules — a void or burn list

**What the player sees.** A list of items the warehouse deletes on sight, or a threshold ("never keep more than
2000 cobblestone") past which the surplus is destroyed. No merchant, no emeralds, no button to press each time.

**How it is built here.** Cheaper than option 9 and shaped like option 5: an `ItemListModule` (or a
`MinimumStockModule`-style map of item → cap, 207 lines of existing pattern), plus a loop in
`WarehouseIdleTrackerModule#onColonyTick` — which already walks every rack once per colony tick and already has
the totals in hand — calling `extractItem` and discarding. **~120 lines server, plus the same ~500 lines of
list-GUI machinery as option 5.**

**Zero mixins?** ✓.

**What it can break.** Everything option 9 can break, minus the price table, plus one more: **it does it while
the player is not looking.** A sell button is a decision a human makes once, in front of a list. An automatic
rule is a decision made once and executed for ever, including on the day the player's circumstances change — the
day they start a big build and want 20 000 cobblestone, the day a crafter is taught a recipe that consumes the
thing being voided, the day a modpack update changes an item id and the rule now matches something else. It also
interacts badly with the request system in a way the manual sale does not: the deletion runs on a tick, so it can
fire in the gap between a resolver promising stock and a courier collecting it, and it will do so repeatedly and
invisibly. On a busy server the symptom is requests that fail for no reason anyone can reproduce.

**Cost.** ~620 lines with the GUI. Client work required (✗ unverifiable here).

**Verdict.** **Do not build this.** It is option 5 with the safety removed: the same list, the same GUI, the
same tick loop, but the items are destroyed instead of never arriving. Option 5 achieves the same end state —
the junk is not in the warehouse — without ever deleting anything the player owns. If the goal is "stop this
item filling my warehouse", the correct answer is to stop it arriving, not to arrange for it to arrive and then
be shredded.

---

## What I would do, in order

1. **Tonight, no code.** Press **Sort** in the warehouse GUI (409–753 slots). Buy the remaining **rack storage
   upgrade(s)** with emerald blocks (891–4455 slots). Tick rotten flesh, wheat seeds and any genuinely surplus
   crops in the **composter's Compostables** list (~16 000 items, ~250 slots) — but check the restaurant's menu
   before ticking potato or carrot.
2. **First code: option 1**, the one-line `upgradeRackSize` fix. Without it, step 1's second half silently does
   nothing on a warehouse that has actually filled up.
3. **Then option 3**, the honest warning and the courier deadlock, because a full warehouse currently stops the
   colony without saying so.
4. **Then option 2**, the capacity readout and auto-defragment, so the player can see the problem before it is a
   crisis. Measure `SortingUtils#sort` on a 4455-slot warehouse before putting it on a timer.
5. **Then option 6**, the fisherman setting, because it is the only fix that stops the permanently-unstackable
   pile growing.
6. **Then option 5**, the "do not store" list, when the junk sources are understood well enough to name them.
7. **Leave options 8, 9 and 10 alone.** Revisit 9 only after option 7 has two weeks of real idle data behind it.

---

## What could not be verified (✗)

1. **Nothing was run on a server for this study.** No colony was built, no warehouse was filled, no courier was
   observed. Every claim is from the source in this tree, from the vanilla `server-26.2.jar`, from the blueprint
   NBT, or from arithmetic on the owner's CSV. The behavioural claims in *What a full warehouse actually does*
   and in option 1 are read off the code, not watched.
2. **The origin of the 86 `simpleplanes:plane`.** `simpleplanes` is in neither this tree nor the test server's
   mod folder. The generic citizen-pickup path is named and is almost certainly it, but it is not proven.
3. **Which producer made the netherite.** Blacksmith smithing recipes or free-mode conjuring; the CSV does not
   say, and I do not know whether free mode is on in the owner's colony.
4. **The owner's rack count and storage-upgrade level.** Bounded to exactly two possibilities (165 racks at
   upgrade 0, or 99 racks at upgrade 2) by the fact that 4455 has only those two decompositions; not resolved.
5. **Whether the colony has a composter at all, and what is ticked in its Compostables list.** Option 4 assumes
   one exists.
6. **Poisonous potato compostability.** Inferred from the `minecolonies:food` / `max-saturation 0.5` ingredient
   in `compostables_poor`, not checked item by item. (`rotten_flesh`, `#c:crops`, `#c:seeds`,
   `#c:foods/raw_fish`, `#c:foods/raw_meat` **were** checked, in this tree and in the Fabric convention-tags jar.)
7. **Which worker brings in the leaf litter.** The item is confirmed **not** compostable — `minecraft:leaf_litter`
   is absent from `data/minecraft/tags/item/leaves.json` in the 26.2 server jar — but its producer in this colony
   is unidentified.
8. **The cost of `SortingUtils#sort` on a 4455-slot warehouse.** Argued to be O(total slots × rack size) ≈ 400 000
   inner iterations plus a full NBT serialisation, from reading `RackInventory#setStackInSlot` →
   `updateItemStorage` → `updateContent`. Not measured. Option 2 must not go on a timer until it is.
9. **All client-side work.** This container has no display, so no GUI in this report — the capacity readout
   (option 2), the item-list tabs (options 5, 10), the settings row (option 6) or the sell window (option 9) —
   could be rendered or clicked. Everything client side in this report is a cost estimate, not a tested design.
10. **Whether the 86 planes are stack-size 1.** If they are, they occupy 86 slots; if not, one or two. The CSV
    gives item counts, not slot counts.
