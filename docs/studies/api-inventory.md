# Build inventory: what exists behind the Tier 1 API surface

What each Tier 1 entry of `api-needs.md` would cost to build, measured against the `26.3/` tree.
Compiled from two independent passes over disjoint halves of the catalogue and merged here.
Date: 2026-08-24.

Evidence standard, same as the other studies in this directory:

* **[VERIFIED]** — the file was opened and the cited line says what is claimed.
* **[UNCHECKED]** — inference, not observed.

`26.3/src/main/java/com/minecolonies/` is abbreviated to `mc/`.

Four states, one per row:

* **EXISTS** — a server-side method or field already returns this. A wrapper of a few lines suffices.
* **PARTIAL** — the data is present but not in the shape the row calls for: spread across objects,
  reachable only through a packet meant for the interface, computed on the client, or bundled into
  something larger.
* **COMMAND EXISTS** — the verb is implemented, but not as a callable method: it lives inside a
  packet handler, needs a player, returns `void`, and reports refusal as chat.
* **MISSING** — nothing in the tree produces this.

Scope: **Tier 1 only**, 77 calls and 24 events. The three supply-camp rows of §2.12 are cancelled and
absent. Tier 2 and Tier 3 are not inventoried.

---

## 1. Summary

| | EXISTS | PARTIAL | COMMAND EXISTS | MISSING | total |
|---|---|---|---|---|---|
| Calls | 19 | 39 | 14 | 5 | **77** |
| Events | 5 | 6 | — | 13 | **24** |
| **Total** | **24** | **45** | **14** | **18** | **101** |

Twenty-four rows out of a hundred and one are a wrapper away. Forty-five have their data somewhere in
the tree but not in a usable shape. Fourteen are verbs that work today and cannot answer a question
without also performing the action. Eighteen produce nothing at all, and thirteen of those eighteen
are events.

The distribution says the same thing three times. **The mod computes what the API needs and then
throws it away**, because the only consumer it was ever written for is a screen. Availability of
build resources, percent complete and stage counters are calculated directly into a client packet;
upgrade cost is calculated on the client and never known to the server at all; the ranked list of a
citizen's problems is sorted after the packet, in the view object. Nothing anywhere is timestamped,
which is why events dominate the MISSING column: a predicate evaluated every tick and discarded
cannot produce an edge.

## 2. Corrections to the catalogue

Four statements in `api-needs.md` do not hold for this tree and should be amended before any of it is
built.

| catalogue says | this tree | evidence |
|---|---|---|
| Courier slots: 2 per warehouse level, max 10 | `level * 4` | `mc/core/colony/buildings/modules/CourierAssignmentModule.java:127` [VERIFIED] |
| Saturation threshold 5 | `AVERAGE_SATURATION = 10`, `LOW_SATURATION = 6` | `mc/api/util/constant/CitizenConstants.java:54,59` [VERIFIED] |
| Dining hall fuel must be enabled | coal and charcoal are allowed by default | see §2.8 below |
| `WorkOrderRequestResult` has 13 constants | 12 | `mc/api/colony/workorders/WorkOrderRequestResult.java` [VERIFIED] |

The saturation correction matters beyond a number: `colony.food()` and `ColonySaturationCrossedEvent`
were specified against the wrong threshold, and the catalogue's food tiers and the tree's saturation
scale are not the same scale.

---

## 3. The rows, by block

### 3.1. §2.1 Colony

| row | state | evidence | what is missing |
|---|---|---|---|
| `Colonies.list()` | EXISTS | `mc/api/colony/IColonyManager.java:148` `List<IColony> getAllColonies()`; impl walks every server level at `mc/core/colony/ColonyManager.java:374` | Only the `ColonySummary` record. Name, id, dimension, centre and citizen count are all plain getters on `IColony`. |
| `Colonies.byId(ColonyId)` | EXISTS | `mc/api/colony/IColonyManager.java:94` `getColonyByDimension(int, ResourceKey<Level>)`; also `:84` `getColonyByWorld(int, Level)` | Nothing. The id pair (number + dimension) is already the identity the manager keys on. |
| `Colonies.nearest(pos, maxBlocks)` | PARTIAL | `mc/api/colony/IColonyManager.java:233` `getClosestColony` (impl `mc/core/colony/ColonyManager.java:554`); `mc/api/colony/IColony.java:91` `long getDistanceSquared(BlockPos)`; `mc/api/colony/IColonyManager.java:140` `getColonies(Level)` | Returns one colony, not a sorted list, and takes no radius. The impl also short-circuits on the chunk owner (`ColonyManager.java:557-561`) and returns it regardless of distance, so it is not a distance query at all when standing on claimed ground. A list with distances would be a loop over `getColonies` with `getDistanceSquared`. |
| `Colonies.foundationCheck(pos)` | PARTIAL | `mc/api/colony/IColonyManager.java:131` `boolean isFarEnoughFromColonies` (impl `mc/core/colony/ColonyManager.java:334`); `mc/core/util/ChunkDataHelper.java:126` `canClaimChunksInRange`; `mc/api/colony/IColonyManager.java:280` `getMinimumDistanceBetweenTownHalls`; config at `mc/api/configuration/ServerConfiguration.java:246` `minColonyDistance` | Every check exists, none of them says why. `isFarEnoughFromColonies` is a boolean that folds together two different refusals (too close to a colony centre, and a chunk in range already claimed). The spawn-distance rules are not in it at all — they are inline in the packet handler at `mc/core/network/messages/server/CreateColonyMessage.java:136-152`, as is the "no tile entity here" case (`:110-116`) and the one-colony-per-owner rule (`:160-162`). A `FoundationCheck` needs those five refusals collected into one non-mutating call with the measured distances attached. |
| `Colonies.found(pos, name, owner)` | COMMAND EXISTS | `mc/core/network/messages/server/CreateColonyMessage.java:97` `onExecute(PlayMessageContext, ServerPlayer)`; `mc/api/colony/IColonyManager.java:48` `createColony(ServerLevel, BlockPos, Player, String, String)` (impl `mc/core/colony/ColonyManager.java:113`) | `createColony` alone does not found a colony: the packet handler is what runs the checks, reactivates a deactivated hut, sets the structure pack and path, and registers the town hall building (`CreateColonyMessage.java:164-165`). Between the handler and a plain call stand the `ServerPlayer` (used for ownership, subscription and every refusal message) and the requirement that a `TileEntityColonyBuilding` already sit at the position. |
| `colony.identity()` | PARTIAL | `mc/api/colony/IColony.java:65` `getName`, `:74` `getPermissions` (owner at `mc/api/colony/permissions/IPermissions.java:132` `getOwner`, `:91` `getOwnerName`), `:304` `getDimension`, `:58` `getCenter`, `:223` `getStructurePack` | Founding time is not recorded anywhere. `Colony.day` (`mc/core/colony/Colony.java:456`, incremented at `:1055`) counts colony days but is a counter, not a timestamp, and is not a founding mark. Centre chunk is a shift of `getCenter`. Everything else is one getter each and needs only the record. |
| `colony.progress()` | PARTIAL | Population: `mc/api/colony/managers/interfaces/ICitizenManager.java:121` `getCurrentCitizenCount`; buildings: `mc/api/colony/managers/interfaces/ICommonRegisteredStructureManager.java:61` `getBuildings()`; town hall: `:35` `getTownHall()`; warehouses: `mc/api/colony/managers/interfaces/IRegisteredStructureManager.java:183` `getWareHouses()`; research: `mc/api/colony/IColony.java:318` `getResearchManager` | No aggregate. The nearest thing that exists is `mc/core/commands/colonycommands/CommandColonyInfo.java:50-107`, which computes a subset (population split by adults/children, homeless count, beds, mayor, coordinates) and writes it straight into chat components rather than returning it. Building counts by type, highest builder's hut level and courier count are each a loop over `getBuildings()` that nothing performs today. |
| `colony.clock()` | PARTIAL | `mc/api/colony/IColony.java:484` `getDay()` (impl `mc/core/colony/Colony.java:2710`), `:131` `isDay()` (`Colony.java:2625`, updated in `checkDayTime` at `Colony.java:1038-1058`); `mc/api/util/WorldUtil.java:166` `isDayTime(Level)`; rain via `Level#isRaining`, used at `mc/core/colony/managers/RegisteredStructureManager.java:516` | Whether the daylight cycle gamerule is on is never read: the only `GameRules` reads in the tree are `SPAWN_MOBS` (`mc/api/util/WorldUtil.java:254`), `LIMITED_CRAFTING`, `MOB_GRIEFING` and `MOB_DROPS`. Snow is not distinguished from rain anywhere. Time of day comes from `Level#getOverworldClockTime` and is not exposed on the colony. The record itself is missing. |
| `colony.settings()` | PARTIAL | `mc/api/colony/IColony.java:505` `ICommonSettingsModule getSettings()` (impl `mc/core/colony/Colony.java:2728`, module built at `Colony.java:466`); the five town hall switches are registered at `mc/core/colony/buildings/modules/BuildingModules.java:542-548` and keyed at `mc/core/colony/buildings/workerbuildings/BuildingTownHall.java:67-86` | Reads work per key (`getSetting(ISettingKey)`), but nothing enumerates them: `SettingsModule.settings` is a package-private field with no accessor (`mc/core/colony/buildings/modules/SettingsModule.java:34`). A `ColonySettings` record would have to name the five keys itself. |
| `colony.setNewCitizensEnabled(boolean)` | COMMAND EXISTS | Key `MOVE_IN` at `mc/core/colony/buildings/workerbuildings/BuildingTownHall.java:67`, read at `mc/core/colony/managers/CitizenManager.java:234` and `:624`; write path `mc/core/colony/buildings/modules/SettingsModule.java:109` `updateSetting(key, value, ServerPlayer)`; GUI entry `mc/core/network/messages/server/colony/building/TriggerSettingMessage.java:85-99` | `updateSetting` takes a `ServerPlayer` (passed on to `ISetting#onUpdate`) and replaces the whole setting object; `BoolSetting.trigger()` (`mc/core/colony/buildings/modules/settings/BoolSetting.java:110`) toggles rather than sets. A set-to-value entry point that takes no player and marks the colony dirty does not exist. |
| `colony.capacity()` | PARTIAL | `mc/api/colony/managers/interfaces/ICitizenManager.java:100` `getMaxCitizens`, `:107` `getPotentialMaxCitizens`, `:114` `maxCitizensFromResearch`, `:121` `getCurrentCitizenCount`; impls at `mc/core/colony/managers/CitizenManager.java:531`, `:537`, `:543`, `:562`; bed arithmetic at `CitizenManager.java:443-481`; config ceiling `mc/api/configuration/ServerConfiguration.java:183` `maxCitizenPerColony` | `getMaxCitizens` returns `min(beds, research, config)` at `CitizenManager.java:533`, so the answer never says which term bound it — the `CapSource` the row asks for has to be recomputed from the three inputs. Beds total and free are per residence only: `LivingBuildingModule.getModuleMax` (`mc/core/colony/buildings/modules/LivingBuildingModule.java:227`), `getResidentCount` (`:152`), actual bed blocks at `mc/core/colony/buildings/modules/BedHandlingModule.java:74`. |
| `colony.townHall()` | EXISTS | `mc/api/colony/managers/interfaces/ICommonRegisteredStructureManager.java:35` `getTownHall()`, `:68` `hasTownHall()` | Nothing; only the id conversion from `IBuilding` to `BuildingId`. |

**Push.**

| row | state | evidence | what is missing |
|---|---|---|---|
| `ColonyFoundedEvent` | EXISTS | `ColonyCreatedModEvent` posted at `mc/core/network/messages/server/CreateColonyMessage.java:180`; class `mc/api/eventbus/events/colony/ColonyCreatedModEvent.java:9`; bus `mc/api/eventbus/EventBus.java:15` `subscribe`, `:21` `post` | The moment is right and a hook is already there. Three gaps: the payload carries only `IColony` (name, owner and centre are read back off it, which is fine, but the record fields must be added); the post sits in the packet handler, not in `ColonyManager.createColony`, so a colony created any other way is silent; and `EventBus` has `subscribe` with no unsubscribe, so `Subscription.close()` has nothing behind it. |

---


### 3.2. §2.2 Construction & Build Orders

| row | state | evidence | what is missing |
|---|---|---|---|
| `building.buildEligibility(int)` | PARTIAL | The refusal vocabulary already exists as an enum: `mc/api/colony/workorders/WorkOrderRequestResult.java` (12 constants, including `NO_BUILDER_GOOD_ENOUGH`, `NO_BUILDER_IN_RANGE`, `ALREADY_QUEUED`, `NO_BLUEPRINT`, `TOO_HIGH`, `TOO_LOW`). Every test that produces one is inside `AbstractBuilding.requestWorkOrder` (`mc/core/colony/buildings/AbstractBuilding.java:479-604`), which is `protected` and mutating. Supporting predicates: `canBeBuiltByBuilder` (`AbstractBuilding.java:624`, overridden only at `mc/core/colony/buildings/workerbuildings/BuildingBuilder.java:191`), `AbstractWorkOrder.canBeResolved` (`mc/core/colony/workorders/AbstractWorkOrder.java:867`), `WorkOrderBuilding.tooFarFromAnyBuilder` (`mc/core/colony/workorders/WorkOrderBuilding.java:222`) | A non-mutating check. The research gate that blocks an upgrade lives separately again, in `AbstractBuilding.requestUpgrade` (`:881-897`), and only sends the player a chat message. `mc/core/commands/colonycommands/CommandColonyRepairAll.java:138-167` already tried this and got three of the refusals out in preview mode, recording in its own comment that everything past them "is decided inside requestRepair, which cannot answer without also acting". Worst of all, the "outside the claim" refusal is decided later still, in `WorkManager.addWorkOrder` → `findClaimGap` (`mc/core/colony/workorders/WorkManager.java:310-315`, `:410-457`), *after* `requestWorkOrder` has already returned `QUEUED` — so today the mod itself reports success for an order it then discards. |
| `colony.maxBuildableLevel()` | PARTIAL | `mc/core/colony/workorders/AbstractWorkOrder.java:867` `canBeResolved(colony, level)` tests exactly the right predicate — some `BuildingBuilder` with an assigned citizen whose level is at least `level` | No maximum. It answers a yes/no for one level and would have to be inverted into a max over the builder huts. There is no colony-level accessor and no list of builder buildings; every call site filters `getBuildings()` by `instanceof BuildingBuilder` by hand (`AbstractWorkOrder.java:874`, `mc/core/colony/workorders/WorkManager.java:507`). |
| `building.requestBuild()` | COMMAND EXISTS | `mc/api/colony/buildings/IBuilding.java:189` `requestUpgrade(Player, BlockPos)`, impl `mc/core/colony/buildings/AbstractBuilding.java:881`, which chooses `BUILD` or `UPGRADE` and calls `requestWorkOrder`; GUI entry `mc/core/network/messages/server/colony/building/BuildRequestMessage.java:97-116` | Returns `void`. The `WorkOrderRequestResult` that `requestWorkOrder` produces is discarded at `AbstractBuilding.java:902` and `:906`. A `Player` is required for the three refusal messages. `requestRepair(BlockPos, boolean)` (`IBuilding.java:228`) is the one member of the family that already returns the result — the pattern to copy. |
| `building.requestUpgrade(int targetLevel)` | COMMAND EXISTS | `mc/api/colony/buildings/IBuilding.java:200` `requestUpgradeTo(Player, BlockPos, int)`, impl `mc/core/colony/buildings/AbstractBuilding.java:929` | It refuses an explicit target level outright unless free mode is on (`AbstractBuilding.java:931-939`) and silently downgrades to a one-level upgrade. So "order an upgrade to level N" is presently a debug-only path; ordinary play only has "upgrade by one". Also `void`, also needs a `Player`. |
| `colony.buildOrders()` | EXISTS | `mc/api/colony/workorders/IWorkManager.java:74` `Map<Integer, IServerWorkOrder> getWorkOrders()`; priority-ordered variant `:129` `getOrderedList(Predicate, BlockPos)`, impl sorts by priority descending at `mc/core/colony/workorders/WorkManager.java:571-578` | Only the summary record. The ordered variant filters by claimant, so a whole-queue view would use the unfiltered map plus the same comparator. |
| `order.status()` | PARTIAL | `mc/api/colony/workorders/IWorkOrder.java` — `getID` `:28`, `getPriority` `:42`, `getWorkOrderType` `:98`, `getCurrentLevel` `:77`, `getTargetLevel` `:84`, `isClaimed` `:119`, `getClaimedBy` `:127`, `getStage` `:199`. Builder-side progress at `mc/core/colony/buildings/AbstractBuildingStructureBuilder.java:396` `getProgress()` | Percent complete is computed nowhere except on the way to the client: `BuildingResourcesModule.serializeToView` (`mc/core/colony/buildings/modules/BuildingResourcesModule.java:76-106`) divides the summed needed amount by `workOrder.getAmountOfResources()` and writes it, with `totalStages`/`currentStage`, straight into the packet. Nothing keeps it. Stall reason does not exist at all (see `BuildOrderStalledEvent`). |
| `order.missingResources()` | PARTIAL | `mc/core/colony/buildings/modules/BuildingResourcesModule.java:165` `getNeededResources()` returns `Map<String, BuildingBuilderResource>`; the red/green/black classification is already a server-side method, `BuildingBuilderResource.getAvailabilityStatus()` (`mc/core/colony/buildings/utils/BuildingBuilderResource.java:95-118`, five states including `IN_DELIVERY`) | Three things. (1) The list hangs off the builder's hut, not off the order — an unclaimed order has none. (2) `available` is only refreshed by the private `updateAvailableResources()` (`BuildingResourcesModule.java:115`), whose only caller is `serializeToView`, so a direct read returns whatever the last client packet left behind. (3) The row asks for "how many the colony holds"; `updateAvailableResources` counts only the builder's own inventory, the hut's container and external work stations (`:115-155`), never the warehouse. `amountPlayer`, which `getAvailabilityStatus` branches on, is documented as client-only (`BuildingBuilderResource.java:14`). |
| `order.cancel()` | COMMAND EXISTS | `mc/api/colony/workorders/IWorkManager.java:28` `removeWorkOrder(int)`, impl `mc/core/colony/workorders/WorkManager.java:102-125`; GUI entry `mc/core/network/messages/server/colony/WorkOrderChangeMessage.java:80-90` | There is only one removal verb, and it is the destructive one: it calls `AbstractBuildingStructureBuilder.onWorkOrderCancellation` (`mc/core/colony/buildings/AbstractBuildingStructureBuilder.java:482-504`), which cancels the builder's requests, resets the needed resources and clears the progress position. The row's cancel/delete distinction has no counterpart in the code. It also returns `void` and never reports "order already complete". |
| `colony.builders()` | PARTIAL | Pieces: `getBuildings()` filtered by `BuildingBuilder` (the idiom at `mc/core/colony/workorders/WorkManager.java:505-509`), `getBuildingLevel()` (`mc/api/colony/buildings/ICommonBuilding.java:20`), `AbstractBuildingStructureBuilder.hasWorkOrder()` (`mc/core/colony/buildings/AbstractBuildingStructureBuilder.java:523`) and `getWorkOrder()` (`:537`), assigned worker via `WorkerBuildingModule#getFirstCitizen` (used at `WorkManager.java:509`) | The aggregate. Note that `hasWorkOrder` is also the "is this builder free" test the assignment loop uses, so "free" is already defined; only the list is absent. |

**Push.** No work-order event is posted on the bus anywhere: `grep` for the three building events
finds three posts in the whole tree and none of them is a work-order event.

| row | state | evidence | what is missing |
|---|---|---|---|
| `BuildOrderCreatedEvent` | MISSING | The moment is `mc/core/colony/workorders/WorkManager.java:344` `workOrders.put(order.getID(), order)` inside `addWorkOrder` (`:293`), immediately followed by `order.onAdded(colony, readingFromNbt)` | A new firing point. `onAdded` is already the hook for per-order side effects (`WorkOrderBuilding.onAdded` places construction tape at `mc/core/colony/workorders/WorkOrderBuilding.java:299-310`), so the post belongs next to it, guarded by `!readingFromNbt` so that a world load does not replay the queue as new orders. |
| `BuildOrderClaimedEvent` | MISSING | The moment is `WorkManager.tryAssignWorkOrder` (`mc/core/colony/workorders/WorkManager.java:503`), at the two assignment sites `:524-525` and `:538-539`, each `setWorkOrder(order); order.setClaimedBy(building.getID())` | A new firing point at both sites, or a single one factored out of them. The builder's citizen id is one step further in (`WorkManager.java:509` already holds the `ICitizenData`). |
| `BuildOrderStalledEvent` | MISSING | Nothing tracks a stall on a work order. The closest analogues are both outside the order: `BuildingMiner#getShaftStallTicks`, read at `mc/core/commands/colonycommands/CommandColonyDiagnose.java:480`, which is specific to the mine shaft; and the diagnose command's own memory, a static per-colony map of observations (`CommandColonyDiagnose.java:91`, `observe()` at `:661`) that only measures the gap between two invocations of the command and is described in its own comment as "new" whenever it cannot tell | The whole mechanism: a stall clock per order, a grace period, a reason taxonomy, and edge detection so that the event fires once on entering the state. The reason itself would have to be read from the builder's AI state and its open requests, neither of which the order can see today. This is the largest single piece of new machinery in this half. |
| `BuildOrderResumedEvent` | MISSING | As above — the falling edge of a state that is not tracked | Same mechanism; free once the stall state exists. |
| `BuildOrderCompletedEvent` | EXISTS | `BuildingConstructionModEvent` posted at `mc/core/entity/ai/workers/AbstractEntityAIStructureWithWorkOrder.java:489`, carrying the `IBuilding` and the completed `WorkOrderBuilding` (`mc/api/eventbus/events/colony/buildings/BuildingConstructionModEvent.java:22`) | The order id, kind and new level are all reachable from the attached work order, so only the payload record is new. Two limits: it fires only for `WorkOrderBuilding` (`:460`), so decoration, miner and plantation-field orders are silent; and it fires from the builder AI, so an order completed any other way would not post. |
| `BuildOrderRejectedEvent` | MISSING | Refusals are returned as `WorkOrderRequestResult` from `AbstractBuilding.requestWorkOrder` (`mc/core/colony/buildings/AbstractBuilding.java:479-604`) and announced as chat when `announce` is true; nothing is posted | A firing point at each `return` in `requestWorkOrder`, plus the same treatment for the late claim-gap refusal in `WorkManager.addWorkOrder` (`:310-315`) which currently only sends a message. The `EligibilityBlock` type maps closely onto the existing enum. |

---


### 3.3. §2.3 Buildings & Levels

| row | state | evidence | what is missing |
|---|---|---|---|
| `colony.buildings()` | EXISTS | `mc/api/colony/managers/interfaces/ICommonRegisteredStructureManager.java:61` `Map<BlockPos, B> getBuildings()` | Only the summary record. |
| `colony.buildingsOfType(type)` | PARTIAL | Type on the building at `mc/api/colony/buildings/ICommonBuilding.java:35` `getBuildingType()`; existing type queries are either yes/no (`ICommonRegisteredStructureManager.java:249` `hasBuilding(Identifier, int, boolean)`) or hard-coded per type (`mc/api/colony/managers/interfaces/IRegisteredStructureManager.java:183` `getWareHouses()`, `:197` `getMysticalSites()`) | A general by-type filter. One loop over `getBuildings()` comparing `getBuildingType().getRegistryName()`; the mod does this inline in several places rather than once. |
| `colony.building(BuildingId)` | EXISTS | `ICommonRegisteredStructureManager.java:50` `getBuilding(BlockPos)` (map lookup) and the typed `:77` `getBuilding(BlockPos, Class<BB>)` | Nothing. |
| `colony.buildingAt(WorldPos)` | EXISTS | Same map lookup; cross-colony fallback at `mc/api/colony/IColonyManager.java:103` `getBuilding(Level, BlockPos)`, impl `mc/core/colony/ColonyManager.java:285-309` | Nothing for the hut-block position the row asks about. (For an arbitrary position inside a building's footprint the answer is `mc/api/colony/buildings/IBuilding.java:435` `isInBuilding(BlockPos)`, which would need a scan.) |
| `building.info()` | PARTIAL | Level `ICommonBuilding.java:20`, position `:28`, type `:35`, containers `:52`; max level `mc/api/colony/buildings/ISchematicProvider.java:131`, structure pack `:72`, blueprint path `:86`, rotation/mirror `:161`; custom name `mc/api/colony/buildings/IBuilding.java:64`; pickup priority `mc/api/colony/buildings/IBuildingContainer.java:28`; workers `IBuilding.java:505` `getAllAssignedCitizen()` | Facing is not on the building — it lives in the block state (`FACING`, `mc/api/blocks/AbstractColonyBlock.java:66`), so it needs a world read. The paused flag does not exist per building (next row but one). Worker slots come from whichever assignment module the building carries. The record itself is new. |
| `building.upgradeCost(int targetLevel)` | PARTIAL | The computation exists, but only on the client: `mc/core/client/gui/WindowBuildBuilding.java:443-476` loads the target level's blueprint and runs Structurize's `StructurePlacer` with `Operation.GET_RES_REQUIREMENTS`, accumulating into its own local list (`:486`). Server-side, the identical scan exists only as a builder AI state machine that runs after an order is placed: `mc/core/entity/ai/workers/AbstractEntityAIStructureWithWorkOrder.java:240-360`, feeding `building.addNeededResource` (`:292`, `:312`, `:335`, `:350`) | An on-demand server-side scan for an arbitrary building and target level, off the AI's incremental state machine. The blueprint path for level N is derived by rewriting the last character of the current path (`WindowBuildBuilding.java:442`, and the same trick inside `WorkOrderBuilding#create`), so the input is available; what is missing is a synchronous, order-free entry point and somewhere to cache the answer. This is the second-largest piece of new work in this half. |
| `building.limits()` | PARTIAL | `mc/api/colony/buildings/IBuilding.java:479` `default int getMaxEquipmentLevel()` — the ceiling by hut level, with the wood/max special cases | The row asks for tool tier and enchantment level separately; the mod does not separate them. `mc/api/util/ItemStackUtils.java:284` `verifyEquipmentLevel` compares `equipmentLevel + getMaxEnchantmentLevel(stack)` against the single ceiling, so "a diamond pickaxe in a level-1 hut is refused" is a sum, not two limits. Either the API states one combined number, or the split has to be invented. |
| `building.isPaused()` | PARTIAL | Pause is a citizen flag, not a building flag: `mc/api/colony/ICitizen.java:64` `isPaused()`, impl `mc/core/colony/CitizenData.java:880`; consumed by the worker AI at `mc/core/entity/ai/workers/AbstractEntityAIBasic.java:2197-2210` | Nothing on `IBuilding`. A building-level answer has to be derived over `getAllAssignedCitizen()` (`mc/api/colony/buildings/IBuilding.java:505`) and has to decide what "paused" means for a hut with two workers, one paused. |
| `building.setPaused(boolean)` | COMMAND EXISTS | `mc/api/colony/ICitizen.java:57` `setPaused(boolean)` (`mc/core/colony/CitizenData.java:873`); GUI verb `mc/core/network/messages/server/colony/citizen/PauseCitizenMessage.java:74`, which reads `citizen.setPaused(!citizen.isPaused())` | The message toggles rather than sets, and acts on one citizen. A building-level setter is a loop; the underlying setter needs no player and is already public. |
| `building.capacity(CapacityKind)` | PARTIAL | `mc/api/colony/buildings/modules/IAssignsCitizen.java:34` `getAssignedCitizen()`, `:40` `isFull()`, `:46` `getModuleMax()` — this covers worker, courier, researcher and guard slots uniformly. Beds: `mc/core/colony/buildings/modules/LivingBuildingModule.java:227` `getModuleMax()` (the building level) and `:152` `getResidentCount()`, with the real bed blocks at `mc/core/colony/buildings/modules/BedHandlingModule.java:74` `getRegisteredBlocks()`. Fields come from the building-extension modules (`mc/api/colony/buildingextensions/`) | The kind → module dispatch. Every kind is readable; nothing maps a `CapacityKind` onto the right module, and nothing refuses a kind that does not apply to the type. |
| `building.as(Class<T> view)` | PARTIAL | Capability is expressed two ways today: by module, `mc/api/colony/modules/IModuleContainer.java:181` `getFirstModuleOccurance(Class)` and `:140` `hasModule(Class)`; and by concrete interface, `IWareHouse`, `IGuardBuilding`, `IMysticalSite`, `ITownHall` in `mc/api/colony/buildings/` | There is no view type and no dispatch. The typed views the API names (warehouse, residence, dining hall, farm, university, guard building, barracks) do not correspond one-to-one with either mechanism — a residence is a `LivingBuildingModule`, a farm is a set of building extensions, a warehouse is an interface. Each view is a small hand-written adapter over the right module. |

**Push.**

| row | state | evidence | what is missing |
|---|---|---|---|
| `BuildingConstructedEvent` | EXISTS | `BuildingConstructionModEvent` at `mc/core/entity/ai/workers/AbstractEntityAIStructureWithWorkOrder.java:489`; separately `BuildingAddedModEvent` at `mc/core/colony/managers/RegisteredStructureManager.java:842`, which fires when the hut block registers as a level-0 building | The "reached level 1 from nothing" distinction. Derivable — the attached work order's `getCurrentLevel()` is 0 and its type is `BUILD` — but the current event does not say so, and it fires for repairs and removals too. |
| `BuildingUpgradedEvent` | EXISTS | Same post, `AbstractEntityAIStructureWithWorkOrder.java:489`; `oldLevel`/`newLevel` from the order's `getCurrentLevel()`/`getTargetLevel()` (`mc/api/colony/workorders/IWorkOrder.java:77`, `:84`) | Only the payload split from the construction event. Note the level is also written directly at `AbstractEntityAIStructureWithWorkOrder.java:480-483` in the fallback path where the schematic carries no level. |
| `BuildingRemovedEvent` | EXISTS | `BuildingRemovedModEvent` posted at `mc/core/colony/managers/RegisteredStructureManager.java:904`, the last statement of `removeBuilding` (`:850`) | The `cause` field. `removeBuilding` is reached from at least four places that mean different things — a player breaking the hut block (`mc/core/colony/permissions/ColonyPermissionEventHandler.java:360` and `:376`), a colony being deleted (`mc/core/colony/ColonyManager.java:198`), a parent building taking its children down (`mc/core/colony/buildings/AbstractBuilding.java:419`), and `pickUp` (`AbstractBuilding.java:995`) — and by the time the event is posted the reason has been lost. |

---


### 3.4. §2.4 Citizens & Professions

| row | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `colony.citizens()` | EXISTS | `mc/api/colony/managers/interfaces/ICitizenManager.java:82` `List<ICitizenData> getCitizens()`; also `:93` `getCitizensUnmodifiable()` and `:121` `getCurrentCitizenCount()` | Children are in the list and carry `ICitizen.isChild()` (`mc/api/colony/ICitizen.java:43`). Visitors are held by a separate manager, `IColony.getVisitorManager()` (`mc/api/colony/IColony.java:240`), so "visitors flagged as such" is a two-list union plus a flag. |
| `colony.citizen(CitizenId id)` | EXISTS | `mc/api/colony/managers/interfaces/ICitizenManager.java:182` `ICitizenData getCivilian(int)`; `mc/api/colony/IColony.java:499` `ICitizen getCitizen(int)` | Nothing. Two accessors already exist, one typed for the server, one for the shared interface. |
| `citizen.info()` | PARTIAL | Name/gender/child/saturation/inventory/paused on `mc/api/colony/ICitizen.java:15,22,29,36,43,50,64`. Job `mc/api/colony/ICitizenData.java:68`, workplace `:61`, home `:46`, position `:99` `getLastPosition()`, skills `:155` → `mc/api/entity/citizen/citizenhandlers/ICitizenSkillHandler.java:66,113` `getLevel(Skill)` / `getSkills()` | No `CitizenInfo` record; the fields sit on three interfaces and two handlers. Job **level** is not stored as such: it is the skill level of the work module's primary/secondary skill (`mc/api/colony/buildings/IBuildingWorkerModule.java:33,41`), so it is a join, not a field. Live position needs the loaded entity; `getLastPosition()` is the fallback. |
| `citizen.needs()` | PARTIAL | Saturation `mc/api/colony/ICitizen.java:36`. Home/bed `mc/api/colony/ICitizenData.java:46,120`. Sickness `mc/api/entity/citizen/citizenhandlers/ICitizenDiseaseHandler.java:23,45,61` `isSick()/getDisease()/isHurt()`. Happiness `mc/api/entity/citizen/citizenhandlers/ICitizenHappinessHandler.java:52`. Days without a home: `mc/apiimp/initializer/InteractionValidatorInitializer.java:296` reads `((ITimeBasedHappinessModifier) getModifier(HOMELESSNESS)).getDays()`, the modifier registered at `mc/core/entity/citizen/citizenhandlers/CitizenHappinessHandler.java:61-64` | Four sub-fields are genuinely absent. **Mourning days left**: mourning is a boolean, `mc/core/entity/citizen/citizenhandlers/CitizenMournHandler.java:94-97` `isMourning()`, set true at the first dawn after a death and cleared at the next one (`mc/core/colony/managers/CitizenManager.java:744-757`) — no counter exists. **Health** lives only on the loaded entity; `CitizenData` holds none (`mc/core/colony/CitizenData.java`, no health field). **Required food tier** as a number is not stored, only a predicate (see §2.8). **Home-to-work distance** is never computed. |
| `colony.unemployed()` | PARTIAL | `mc/core/colony/managers/CitizenManager.java:429-439` `getJoblessCitizen()` walks the citizen map applying exactly the right predicate — `getWorkBuilding() == null && !isChild()` — and returns the **first** match | The predicate exists; the list form does not. A caller must re-filter `getCitizens()` itself. |
| `building.workers()` | EXISTS | `mc/api/colony/buildings/IBuilding.java:505` `Set<ICitizenData> getAllAssignedCitizen()`; per-module `mc/core/colony/buildings/modules/AbstractAssignedCitizenModule.java:91` `getAssignedCitizen()` | `getAllAssignedCitizen()` is the union across every assignment module, so it mixes job holders with residents in a building that also houses people. Job holders alone come from `getModulesByType(IAssignsJob.class)`. |
| `building.hire(CitizenId)` | COMMAND EXISTS | `mc/core/colony/buildings/modules/WorkerBuildingModule.java:101-120` `assignCitizen()`, called from `mc/core/network/messages/server/colony/building/HireFireMessage.java:83-105` | The method returns `boolean`, not a reason. All three refusals the row names — no free slot (`isFull()`, `AbstractAssignedCitizenModule.java:107`), already employed, child — collapse into one `false`, and the child case is only implicit (the module's auto-hire path filters children, the manual path does not). Between the verb and a plain call stands only the permission check the message wrapper performs. |
| `building.fire(CitizenId)` | COMMAND EXISTS | `mc/core/colony/buildings/modules/AbstractAssignedCitizenModule.java:41-52` `removeCitizen()`; reached from `HireFireMessage.java:102` | Same `boolean` return. Note that `removeCitizen` calls `onRemoval` whether or not the citizen was assigned, so "citizen not assigned there" is not distinguishable from success. The teardown chain — `WorkerBuildingModule.onRemoval:219-228` → `AbstractJob.onRemoval:429` → `citizen.setJob(null)` — is what makes it a real firing. |
| `building.recallWorkers()` | COMMAND EXISTS | `mc/core/network/messages/server/colony/building/RecallCitizenHutMessage.java:50-70` — the whole implementation is inside `onExecute`: respawn if AWOL (`setNextRespawnPosition` / `updateEntityIfNecessary`), then `TeleportHelper.teleportCitizen` | Not extracted anywhere. The method body needs a `ServerPlayer` because failure is reported by `MessageUtils.format(WARNING_CITIZEN_RECALL_FAILED).sendTo(player)` rather than returned. A single-citizen variant exists at `mc/core/network/messages/server/colony/citizen/RecallSingleCitizenMessage.java:65`. |
| `citizen.assignHome(BuildingId)` | COMMAND EXISTS | `mc/core/network/messages/server/colony/building/home/AssignUnassignMessage.java:92-125` — resolves the `LivingBuildingModule`, checks `!module.isFull()`, un-assigns the old home, then `module.assignCitizen(citizen)` | Same pattern: the sequence lives in a packet handler, returns nothing, and silently no-ops on every refusal. The free-bed question is answerable independently — `mc/api/colony/managers/interfaces/IRegisteredStructureManager.java:222,239` `getHouseWithSpareBed()` — but is not consulted here. |

**Push.**

| event | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `CitizenSpawnedEvent` | PARTIAL | `mc/api/eventbus/events/colony/citizens/CitizenAddedModEvent.java:8` with source enum at `:40-61` (`INITIAL`, `BORN`, `HIRED`, `RESURRECTED`, `COMMANDS`). Posted at `mc/core/colony/managers/CitizenManager.java:663` (INITIAL), `:395` (RESURRECTED), `mc/core/colony/interactionhandling/RecruitmentInteraction.java:213` (HIRED), `mc/core/commands/citizencommands/CommandCitizenFill.java:235` and `CommandCitizenSpawnNew.java:37` (COMMANDS) | The `BORN` constant exists in the enum but **nothing posts it**: the birth path ends at `mc/core/colony/managers/ReproductionManager.java:364-379`, which spawns the citizen, sends two chat messages, adds a `CitizenBornEvent` log description and increments the `BIRTH` statistic without touching the bus. So `bornInColony` is exactly the case the existing event does not fire for. Firing point to add: `ReproductionManager.java:364`. |
| `CitizenDiedEvent` | PARTIAL | `mc/api/eventbus/events/colony/citizens/CitizenDiedModEvent.java:10`, posted at `mc/core/entity/citizen/EntityCitizen.java:1732` and `mc/core/colony/managers/CitizenAging.java:337` (old age). A parallel log description exists at `mc/core/colony/eventhooks/citizenEvents/CitizenDiedEvent.java` | Payload carries `DamageSource` only. `cause` as a string, the `guard` flag and `BlockPos where` are all derivable at the firing point but are not in the record. Both firing points are correct and already server-side. |
| `CitizenHiredEvent` | PARTIAL | `mc/api/eventbus/events/colony/citizens/CitizenJobChangedModEvent.java:9`, posted from `mc/core/colony/CitizenData.java:1065` inside `setJob(...)`, guarded by an `onLoad` flag so deserialisation does not fire it | One event covers both directions; the caller must inspect `getPreviousJob()` and the citizen's current job to tell hire from fire. The building is not in the payload. The moment is right: every hire reaches `setJob` through `WorkerBuildingModule.assignCitizen:108-119`. |
| `CitizenFiredEvent` | PARTIAL | Same event and same line. The fire path reaches it via `AbstractAssignedCitizenModule.removeCitizen:41` → `WorkerBuildingModule.onRemoval:219-224` → `mc/core/colony/jobs/AbstractJob.java:429-431` `citizen.setJob(null)` | No `reason` string, no building, and no distinction between "fired by command" and "the building was removed" — both arrive as the same job change to null. |

---


### 3.5. §2.5 Work & Idleness

This is the block the catalogue exists for, and it is the block where the gap between "the mod knows"
and "a caller can ask" is widest. Section 9 treats the question in full; the table records the
verdict.

| row | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `citizen.workStatus()` | PARTIAL | State: `mc/core/entity/citizen/EntityCitizen.java:1950` `ITickRateStateMachine<IState> getCitizenAI()`, whose state is one of `mc/api/entity/ai/statemachine/states/CitizenAIState.java:8-16` (`IDLE, FLEE, EATING, SICK, SLEEP, MOURN, WORK, WORKING, INACTIVE`). Job-level state: `mc/api/colony/jobs/IJob.java:177` `getWorkerAI()` → `mc/api/entity/ai/ITickingStateAI.java:38` `getState()`. A coarser three-value status is persisted on the data object: `mc/api/entity/ai/JobStatus.java:8-16` (`IDLE, WORKING, STUCK`), read at `mc/api/colony/ICitizenData.java:262` `getJobStatus()`. Activity in words: `mc/api/colony/jobs/IJob.java:90` `getNameTagDescription()`. Position: `mc/api/colony/ICitizenData.java:99` | Three of the six fields of `WorkStatus` need work. `CitizenAIState` is reachable **only through the loaded entity**, not through `ICitizenData`, so an unloaded citizen has no state at all. `lastProductiveGameTime` is not recorded anywhere — `mc/core/commands/colonycommands/CommandColonyDiagnose.java:85-86` says so in as many words and works around it with a static observation map rebuilt per command run. And `problems` is the row below. |
| `citizen.problems()` | PARTIAL | The substrate exists and is server-side: `mc/core/colony/CitizenData.java:252` `protected final Map<Component, IInteractionResponseHandler> citizenChatOptions`, populated through the single funnel `mc/core/colony/CitizenData.java:1871-1882` `triggerInteraction(...)`, re-validated every tick at `:1832-1866`, and persisted at `:1499-1507`. Each entry carries a severity, `mc/api/colony/interactionhandling/ChatPriority.java:8-12` (`HIDDEN, CHITCHAT, PENDING, IMPORTANT, BLOCKING`), and a stable identity, `mc/core/colony/interactionhandling/ServerCitizenInteraction.java:59` `validatorId`. 57 registrations (a handful of keys appear twice) name the causes as predicates in `mc/apiimp/initializer/InteractionValidatorInitializer.java:55-408` | No server-side getter. `getOrderedInteractions()` and `hasBlockingInteractions()` are declared only on the **client view** interface, `mc/api/colony/ICitizenDataView.java:111,127`, implemented at `mc/core/colony/CitizenDataView.java:460,473`; the sorting that makes the list a ranked problem list happens at `mc/core/colony/CitizenDataView.java:385`, after the packet. Also missing: a typed `ProblemKind` (identity is a translation-key `Component`), a `suggestedFix`, and any way to enumerate the registry — `InteractionValidatorRegistry`'s three maps are private with only per-key getters (`mc/api/colony/interactionhandling/InteractionValidatorRegistry.java:22-32,38-64`). See section 9. |
| `building.problems()` | PARTIAL | The building-shaped causes exist as individual predicates and fields: no field assigned — `mc/apiimp/initializer/InteractionValidatorInitializer.java:124-125` (`FARMER_FIELDS ... hasNoExtensions()`); mine below its depth limit — `:280-290` (`NEEDS_BETTER_HUT`, compares `getLastLadder` against `getDepthLimit`); shaft stalled — `:136-137` and `mc/core/commands/colonycommands/CommandColonyDiagnose.java:480-485` (`BuildingMiner.getShaftStallTicks()`); no worker hired — `mc/core/colony/buildings/modules/AbstractAssignedCitizenModule.java:126` `hasAssignedCitizen()` against `:107` `isFull()`/`getModuleMax()`; blueprint/level inconsistencies — `CommandColonyDiagnose.java:487-513` | Every one of these is currently reached **through a citizen**, not through a building: the validators take `ICitizenData` and dereference `citizen.getWorkBuilding()`. A building with nobody in it therefore reports nothing at all, which is precisely the case the row cares about. There is no pause flag on a building: `isPaused()` is per citizen, `mc/api/colony/ICitizen.java:64`, toggled by `mc/core/network/messages/server/colony/citizen/PauseCitizenMessage.java:74`. No aggregation, no typing, no severity. |
| `colony.idleCitizens()` | PARTIAL | `mc/core/colony/CitizenData.java:1885-1890` `isIdleAtJob()` returns `jobStatus == JobStatus.STUCK`; the field is persisted (`:1694-1701`, `:1508`) and set from the AI, e.g. `mc/core/entity/ai/workers/AbstractEntityAIBasic.java:1091-1103` `checkForToolOrWeapon` sets `STUCK`/`WORKING` | No list accessor, and `JobStatus` is written by only six classes — `AbstractEntityAIBasic` (the tool check), `EntityAIWorkNether`, `EntityAIWorkFarmer`, `EntityAIWorkPlanter`, `EntityAIWorkStablemaster`, `EntityAIWorkUndertaker` — so a worker idle for one of the other causes still reads `WORKING`. `CommandColonyDiagnose.java:281` uses `isIdleAtJob()` as a severity bump for exactly this reason and treats the AI state string as the real signal. |

**Push.**

| event | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `CitizenBlockedEvent` | MISSING | No bus post exists. The natural firing point is already a single funnel: `mc/core/colony/CitizenData.java:1871-1882` `triggerInteraction(...)` is the only place an interaction enters the map, and it already short-circuits when the interaction is present, so it fires once per transition | Needs: an event class; a post at `CitizenData.java:1875`, filtered to `ChatPriority.IMPORTANT`/`BLOCKING`; and the grace-period timer the design asks for, which has no counterpart in the code — the closest thing is `markDirty(20 * 5)` on the same line. |
| `CitizenUnblockedEvent` | MISSING | The mirror moment also exists as a single funnel: `mc/core/colony/CitizenData.java:1855-1866`, the removal loop that runs every tick over handlers whose `isValid(this)` has gone false | Needs an event class and a post at `CitizenData.java:1857`, conditioned on the blocking set becoming empty rather than on each individual removal. |

---


### 3.6. §2.6 Requests & Logistics

| row | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `colony.requests(RequestFilter)` | PARTIAL | Every live request is in one map: `mc/core/colony/requestsystem/management/IStandardRequestManager.java:16` `getRequestIdentitiesDataStore()` → `getIdentities()`, a `Map<IToken<?>, IRequest<?>>` written at `mc/core/colony/requestsystem/management/handlers/RequestHandler.java:78` and read at `:601`. A colony-wide walk that does not need the cast is demonstrated at `mc/core/commands/colonycommands/CommandColonyDiagnose.java:552-560`: iterate `getBuildings().values()`, then `getOpenRequestsByRequestableType()` (`mc/api/colony/buildings/IBuilding.java:446`) | The full map is on `IStandardRequestManager`, not the public `IRequestManager` (`mc/api/colony/requestsystem/manager/IRequestManager.java`), so reaching it requires a cast to the implementation interface — which `mc/core/colony/buildings/AbstractBuilding.java:1911` already does. No filter type, no `RequestSummary`, and the mod's `RequestState` (`mc/api/colony/requestsystem/request/RequestState.java:13-140`) has more members than the design's, so a mapping is needed. "Important" has no field; the Clipboard approximates it by excluding `MinimumStack`-typed requests (`mc/core/client/gui/WindowClipBoard.java:149-153`). |
| `colony.playerRequests()` | PARTIAL | The two server-side sources are `IRequestManager.getPlayerResolver()` and `getRetryingRequestResolver()` (`mc/api/colony/requestsystem/manager/IRequestManager.java:184,192`), each with `getAllAssignedRequests()`. Both are already combined server-side at `mc/core/colony/buildings/AbstractBuilding.java:1108-1109` and `:1906-1907`, and counted at `mc/core/commands/colonycommands/CommandColonyDiagnose.java:559-560` | The list the player actually sees is assembled **on the client**: `mc/core/client/gui/WindowClipBoard.java:112-176` unions the two resolvers, walks each request up to its root via `hasParent()`, drops async requests gathered from citizen job views, and sorts by distance to the player. That whole method is the missing server-side assembly. |
| `request.detail()` | PARTIAL | `mc/api/colony/requestsystem/request/IRequest.java` has all of it: `:65` `getState()`, `:81` `getRequester()`, `:91` `getRequest()`, `:137` `hasParent()`, `:192,200` `hasChildren()/getChildren()`, `:223` `getDeliveries()`, `:253,262` display strings. Resolution: `IRequestManager.getResolverForRequest` (`:125`) | No `RequestDetail` record; the parent token accessor and the "acceptable alternatives" notion (`IDeliverable` matching) are not surfaced as a list. `getResolverForRequest` throws on an unknown token rather than returning empty, and mapping a concrete resolver class onto the design's five `RequestResolverKind` values is new work. |
| `citizen.openRequests()` | EXISTS | `mc/api/colony/buildings/IBuilding.java:328` `Collection<IRequest<?>> getOpenRequests(int citizenid)`, backed by `mc/core/colony/buildings/AbstractBuilding.java:1642-1660` over the per-building data store `getOpenRequestsByCitizen()` (`:1532`) | Only that the caller must go through the citizen's work building rather than the citizen; a citizen with no building has no requests by construction. Filtered and typed variants already exist at `IBuilding.java:330,340,343,386`. |
| `building.openRequests()` | EXISTS | `mc/api/colony/buildings/IBuilding.java:446` `Map<TypeToken<?>, Collection<IToken<?>>> getOpenRequestsByRequestableType()`, backed by `mc/core/colony/buildings/AbstractBuilding.java:1527-1530` | Returns tokens, not requests — one `getRequestForToken` per entry. Otherwise complete, and this is the accessor `CommandColonyDiagnose` uses to sweep the colony. |
| `request.fulfill(InventoryRef)` | COMMAND EXISTS | Two halves, both server-side: `IRequestManager.overruleRequest(token, stack)` (`mc/api/colony/requestsystem/manager/IRequestManager.java:144`, implemented at `mc/core/colony/requestsystem/management/manager/StandardRequestManager.java:351-361`) and the item transfer, `mc/core/network/messages/server/colony/citizen/TransferItemsToCitizenRequestMessage.java`. The state half is exposed as `mc/core/network/messages/server/colony/UpdateRequestStateMessage.java:77-85` | `overruleRequest` **only overrides the deliveries and flips the state** — it takes nothing from anyone's inventory. Handing items over is a two-message dance driven entirely from the client: `mc/core/client/gui/citizen/RequestWindowCitizen.java:146-206` finds the slot in the player's inventory, refuses equipped items, clamps the count, sends the transfer message, then sends `UpdateRequestStateMessage(..., OVERRULED, copy)`. That sequence — source lookup, count clamp, "source lacks the items" refusal — is the missing piece and it currently exists only in GUI code. |
| `colony.couriers()` | PARTIAL | Per warehouse: `mc/api/colony/managers/interfaces/IRegisteredStructureManager.java:183` `getWareHouses()`; slots from `mc/core/colony/buildings/modules/CourierAssignmentModule.java:124-128` `getModuleMax()` — in this tree `buildingLevel * 4`, not the 2-per-level the catalogue quotes — against `getAssignedCitizen().size()`; hiring mode at `AbstractAssignedCitizenModule.java:175`. Courier huts are the `DeliverymanAssignmentModule` holders. The exact per-warehouse report is already formatted, as a string, at `mc/core/commands/colonycommands/CommandColonyDiagnose.java:342-361` | No record type, and one field has no source: **stacks per trip** is not a value the code holds. The courier's carrying capacity is the citizen inventory's free-slot count, consulted inline as `InventoryUtils.openSlotCount(worker.getInventoryCitizen())` (`mc/core/entity/ai/workers/service/EntityAIWorkDeliveryman.java:221,241`); nothing maps hut level to a stack count. Listing the courier huts that belong to a warehouse is also new: the association is one-way (a courier's `JobDeliveryman.findWareHouse()`), not stored on the warehouse. |

**Push.**

| event | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `RequestOpenedEvent` | MISSING | No bus post. The funnel exists and is single: `mc/core/colony/buildings/AbstractBuilding.java:1557-1580` `createRequest(citizenData, requested, async)` creates the request, registers it in the per-building maps, assigns it and raises the citizen's chat bubble — every citizen request passes through it. The building-level variant is `:1591-1605` | An event class and a post at `AbstractBuilding.java:1576`. The requester, item and count are all in hand at that line. Note the row's condition ("that the colony did not satisfy from stock") is not evaluated at creation — assignment to a resolver happens on the next line. |
| `RequestEscalatedToPlayerEvent` | PARTIAL | The predicate for this exact condition already exists and is already evaluated on the server every tick: `mc/api/colony/requestsystem/request/RequestUtils.java:30-55` `requestChainNeedsPlayer(token, manager)` walks the child chain and returns true when the leaf is `IN_PROGRESS` on an `IPlayerRequestResolver` or `IRetryingRequestResolver`. It is registered as the validity predicate of the `REQUEST_RESOLVER_NORMAL` interaction at `mc/apiimp/initializer/InteractionValidatorInitializer.java:164-173`, and that interaction is raised at `mc/core/colony/buildings/AbstractBuilding.java:1574-1576` with `ChatPriority.BLOCKING` | The condition is computed but never announced. What is missing is transition detection — the predicate is re-tested on every validity sweep, so "became true" has to be derived — and a post. The chat bubble is the existing signal and it is the red gear the design describes. |
| `RequestResolvedEvent` | MISSING | No bus post. Three correctly-placed moments exist: `mc/core/colony/requestsystem/management/handlers/RequestHandler.java:340` `onRequestResolved`, `:378` `onRequestCompleted`, `:417` `onRequestOverruled` | Event class plus posts. `resolvedBy` needs a mapping from the resolver instance to the design's `RequestResolverKind`; the resolver is available via `IRequestManager.getResolverForToken` (`mc/api/colony/requestsystem/manager/IRequestManager.java:115`). |

---


### 3.7. §2.7 Storage & Resources

| row | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `colony.stock()` | PARTIAL | Per building: `mc/api/util/InventoryUtils.java:3305-3324` `getBuildingInventory(IBuilding)` walks `getContainers()` and reads each `TileEntityRack.getAllContent()`. Per warehouse there is a sampled, persisted index: `mc/core/colony/buildings/modules/WarehouseIdleTrackerModule.java:337` `getHistory()` returning `Map<ItemStorage, ItemHistory>`, with `:303,311,319,327` `getUsedSlots/getTotalSlots/getItemCount/getStackEquivalents`, aggregated across warehouses at `mc/core/commands/colonycommands/CommandColonyWarehouseStock.java:91-119` | No colony-wide aggregation, and above all no split by **where**. The design's three buckets — warehouses, other buildings, citizen inventories — map onto three different traversals, and the third (citizen inventories) has no helper at all: it would iterate `getCitizens()` and read `ICitizen.getInventory()` (`mc/api/colony/ICitizen.java:50`). `getBuildingInventory` also drops counts, returning one stack per distinct item. Unloaded chunks are skipped silently (`InventoryUtils.java:3311` `WorldUtil.isBlockLoaded`), so any total is a lower bound. |
| `colony.countOf(ItemSpec)` | PARTIAL | Per building, exact: `mc/api/util/InventoryUtils.java:843-861` `getCountFromBuilding(IBuilding, ItemStorage)`, with list and predicate overloads at `:824,920,948`. Per warehouse, existence-with-threshold: `mc/core/tileentities/TileEntityWareHouse.java:45,71,77,99` `hasMatchingItemStackInWarehouse(...)` and `:106` `getMatchingItemStacksInWarehouse(predicate)` | The primitive is right; the colony-wide loop over `getBuildings().values()` plus citizen inventories is not written. Same loaded-chunk caveat. |
| `colony.canAfford(List<ResourceNeed>)` | MISSING | The nearest thing is builder-specific and per-order: `mc/core/colony/buildings/utils/BuildingBuilderResource.java:95-118` `getAvailabilityStatus()` returning `NOT_NEEDED / IN_DELIVERY / NEED_MORE / HAVE_ENOUGH` from an available count the builder's hut maintains | Nothing computes affordability for an arbitrary list against the whole colony. It would have to be built on top of the `countOf` loop above: for each line, sum across warehouses, buildings and citizen inventories, then classify. The four-value enum above is a usable model for the per-line status but is populated only for a builder's own required-resources list. |
| `building.contents()` | EXISTS | `mc/api/util/InventoryUtils.java:3305` `getBuildingInventory(IBuilding)`; the hut block itself via `mc/api/colony/buildings/IBuildingContainer.java:82` `getTileEntity()`; rack positions via `mc/api/colony/buildings/ICommonBuilding.java:52` `getContainers()` | Counts, as noted: `getBuildingInventory` returns the `ItemStorage` keys' stacks, so a caller wanting amounts reads `TileEntityRack.getAllContent()` directly. A few lines either way. |
| `building.deposit(source, items)` | COMMAND EXISTS | `mc/core/network/messages/server/colony/building/TransferItemsRequestMessage.java:82-140` — clamps the amount to what the player actually holds, splits into stack-sized chunks, and calls `InventoryUtils.addItemStackToProviderWithResult(building.getTileEntity(), ...)` per chunk, keeping the remainder | The primitive (`addItemStackToProviderWithResult`) is general; everything around it is bound to a `ServerPlayer` and a creative-mode check, and the result — how much was accepted, how much refused for lack of space — is computed as `remainingItemStack` and then discarded rather than returned. `DepositResult` is that discarded value. |
| `Api.inventoryOf(InventoryRef)` | PARTIAL | For a player: `mc/api/util/InventoryUtils.java:2983-3000` `getAllItemsForProviders(...)` over `new InvWrapper(player.getInventory())`, exactly as `TransferItemsRequestMessage.java:102` does it. For a container: `:1260` `filterProvider(provider, stack -> true)` | There is no `InventoryRef` notion — no way to name a player or a world container as a value, no resolution step, and no permission rule for reading another player's inventory. The reading itself is one line. |

**Push.** No Tier 1 events in this block.

---


### 3.8. §2.8 Food & Happiness

| row | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `colony.food()` | PARTIAL | Per-citizen saturation `mc/api/colony/ICitizen.java:36`; the threshold constant `mc/api/util/constant/CitizenConstants.java:54` `AVERAGE_SATURATION = 10`; dining halls by `getBuildings().values()` filtered on `BuildingCook`, the same lookup the eat task does at `mc/core/entity/ai/minimal/EntityAIEatTask.java:360` `getBestBuilding(..., BuildingCook.class)`; staffing from each building's `IAssignsJob` modules; food consumed per day from the statistics manager, `mc/api/colony/managers/interfaces/IStatisticsManager.java:49` `getStatsInPeriod(id, dayStart, dayEnd)` with `mc/api/util/constant/StatisticsConstants.java:17` `FOOD_SERVED` | No aggregation of any kind. The colony average saturation is never computed — the only colony-wide average that exists is happiness, `mc/core/colony/Colony.java:1960-1980` `getOverallHappiness()`, which is the shape to copy. Counts of citizens below the threshold and at zero, the dining-hall/chef/waiter/baker tallies, and `anyFuelEnabled` are all one-pass loops that nothing currently writes. Note the threshold: the code's working figure is 10, not the 5 the catalogue quotes. |
| `citizen.saturation()` | EXISTS | `mc/api/colony/ICitizen.java:36` `double getSaturation()`; ceiling at `mc/api/colony/ICitizenData.java:31` `MAX_SATURATION = 60` | Nothing. |
| `citizen.foodRequirement()` | PARTIAL | The rule is implemented as a predicate, not a tier: `mc/api/util/FoodUtils.java:47-56` `canEat(stack, homeBuilding, workBuilding)` reads `homeBuilding.getBuildingLevelEquivalent()` and delegates to `:64-76` `canEatLevel(stack, buildingLevel)`, which for level ≥ 3 requires `nutrition >= buildingLevel + 1`. The inverse exists: `:84-87` `getBuildingLevelForFood(stack)`. A separate tier notion exists for mod food: `:126-146` `getFoodTier(stack)`. "Refusing available food" has a near-equivalent: `mc/core/colony/CitizenData.java:2070-2085` `needsBetterFood()` | The row's `requiredTier` is not a stored number and the two tier notions in the file are different scales — `canEatLevel` gates on building level, `getFoodTier` returns 0 or 1 for vanilla food and the item's own tier for mod food. Deriving one integer per citizen means inverting `canEatLevel` against the home level. `needsBetterFood()` looks only at the citizen's own inventory, not at what the colony holds, and as written its two `InventoryUtils` calls use the same predicate, so it answers a narrower question than its name suggests. |
| `colony.foodStock()` | MISSING | The ingredients exist — `FoodUtils.getFoodTier` (`mc/api/util/FoodUtils.java:126`), `getFoodValue` (`:96,113`), and the stock traversals of §2.7 | Nothing groups colony stock by food tier or sums saturation per tier. It is the §2.7 `colony.stock()` loop plus a classifier, and neither half exists today. |
| `diningHall.allowedFuel()` | EXISTS | `mc/core/colony/buildings/modules/ItemListModule.java:124-127` `getList()`, on the module registered as `ITEMLIST_FUEL` at `mc/core/colony/buildings/modules/BuildingModules.java:74-79`; membership test at `ItemListModule.java:109`. The universe of fuels to diff against is `mc/api/compatibility/ICompatibilityManager.java:79` `getFuel()` | Only the shape: the row wants `List<FuelSetting>` (every fuel with an allowed flag), which is `getFuel()` minus `getList()`. Worth recording against §7.4's premise: in this tree the module is constructed with coal and charcoal already allowed (`BuildingModules.java:75`), so the "all fuels off by default" starting state does not hold here. The same list is read as an emptiness check by the no-fuel complaint at `mc/apiimp/initializer/InteractionValidatorInitializer.java:55-57` and `:220-222`. |
| `diningHall.setFuelAllowed(fuel, allowed)` | COMMAND EXISTS | `mc/core/colony/buildings/modules/ItemListModule.java:99-121` `addItem(ItemStorage)` / `removeItem(ItemStorage)`, both marking the building dirty; driven from `mc/core/network/messages/server/colony/building/AssignFilterableItemMessage.java:76-89` | The methods are plainly callable already; what is missing is only the outcome — both return `void`, so "fuel is not burnable" is neither checked nor reported. The check would be membership in `ICompatibilityManager.getFuel()`. |

**Push.**

| event | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `CitizenStarvingEvent` | MISSING | No bus post. The moment is evaluated every tick at `mc/core/entity/citizen/EntityCitizen.java:831-844` `updateHealing()`, which applies Slowness while `getSaturation() <= 0` and removes it otherwise; the experience handler makes the same test at `mc/core/entity/citizen/citizenhandlers/CitizenExperienceHandler.java:93`. The chat-bubble equivalents are registered at `mc/apiimp/initializer/InteractionValidatorInitializer.java:60-68` (`RAW_FOOD`, `BETTER_FOOD`, `BETTER_FOOD_CHILDREN`), all keyed on `getSaturation() == 0` | A transition. Both existing tests are per-tick predicates with no memory, so "reached zero" has to be introduced; `EntityCitizen.java:833` is the line where it would go, and it already distinguishes the two directions. The `workBuilding` field of the payload is at hand via `getCitizenData().getWorkBuilding()`. |
| `ColonySaturationCrossedEvent` | MISSING | Nothing computes a colony saturation average, so there is no value to cross a threshold. The analogous daily colony-wide pass is `mc/core/colony/managers/CitizenManager.java:589-595` `checkCitizensForHappiness()`, and the analogous average is `mc/core/colony/Colony.java:1960-1980` | The average, a stored previous value, the comparison and the post — all four. The natural firing point is the daily pass at `CitizenManager.java:589`, which already walks every citizen once. |

---


### 3.9. §2.11 Territory & Claims

| row | state | evidence | what is missing |
|---|---|---|---|
| `colony.claim()` | PARTIAL | Claimed chunks: `mc/core/colony/Colony.java:2737` `getClaimData()` and `mc/api/colony/IColonyManager.java:493` `getClaimData(ResourceKey<Level>)`; the count is already computed exactly this way at `mc/core/commands/colonycommands/ColonyChunkReport.java:118-133` (filtering the per-dimension map by owning colony id). Per-building contribution: `mc/api/colony/buildings/IBuilding.java:159` `getClaimRadius(int buildingLevel)`, with the town hall's 0/1/1/2/3/5 table at `mc/core/colony/buildings/workerbuildings/BuildingTownHall.java:192-209`. Configured limits: `mc/api/configuration/ServerConfiguration.java:244` `maxColonySize` (default 20), `:247` `initialColonySize` (default 4) | There is no "current radius" to read: the claim is a set of chunks grown per building (`mc/core/util/ChunkDataHelper.java:87` `claimColonyChunks`, `:104` `claimBuildingChunks`), not a stored radius. A radius would have to be derived from the chunk set or from the maximum building contribution. `ColonyChunkReport.gather` (`:110`) is close to the record this row wants but is built for force-loading, so it reports ticketed and ticking chunks instead of contributions. |
| `Colonies.chunkOwner(ChunkRef)` | EXISTS | `mc/api/util/ColonyUtils.java:196` `getOwningColony(ResourceKey<Level>, ChunkPos)` — reads the claim map without loading the chunk; backed by `mc/api/colony/IColonyManager.java:501` `getClaimData(dimension, ChunkPos)` and `mc/api/colony/claim/IChunkClaimData.java:51` `getOwningColony()`. Partial (hand-edited) claims are visible through `IChunkClaimData.java:102` `hasPartialClaim()` and `:110` `isColumnClaimed(BlockPos)` | Only "whether it is inside that colony's build range", which is not a stored property of a chunk. Everything else is one call. |
| `colony.containsPosition(WorldPos)` | EXISTS | `mc/api/colony/IColony.java:83` `isCoordInColony(Level, BlockPos)`, impl `mc/core/colony/Colony.java:1817-1828`, which checks the dimension and then `ColonyUtils.getOwningColony(chunk, pos)` (`mc/api/util/ColonyUtils.java:213`) so that it honours column-level claims | Nothing. Note it takes a `Level` and loads the chunk (`getChunkAt`); the dimension-and-`ChunkPos` variant at `ColonyUtils.java:196` does not. |

§2.11 has no Tier 1 events.

---


### 3.10. §2.12 World & Placement

`Sites.supplyState`, `Sites.checkSupplyCampSite` and `Sites.placeSupplyCamp` are cancelled and are
not inventoried.

| row | state | evidence | what is missing |
|---|---|---|---|
| `Sites.terrain(centre, radiusBlocks)` | MISSING | Nothing in the tree measures ground. There is no flatness, surface-height-range or largest-flat-square computation anywhere (`grep` for `flatness`, `isFlat`, `levelGround` returns nothing); `Heightmap` appears only in unrelated places such as `mc/core/entity/pathfinding/world/ChunkCache.java` and `mc/core/commands/generalcommands/CommandAircraft.java`. Biome is read per position for other purposes but never counted over an area | The whole report. It has to be computed from scratch: a bounded scan over the surface heightmap of the chunks in range, producing height range, a flatness score, the largest flat square, solid-ground and water block counts, and the biome. The one thing that exists to build on is the discipline already applied elsewhere of reading claim data without loading chunks (`mc/api/util/ColonyUtils.java:183-195` documents exactly this hazard); a terrain scan cannot avoid loading, so it needs the "chunks not loaded" failure the row already names. |
| `Sites.checkHutSite(type, pos, facing, style, level)` | PARTIAL | The claim half exists and is precise: `mc/core/colony/workorders/WorkManager.java:410-457` `findClaimGap` computes the blueprint's footprint with `mc/api/util/ColonyUtils.java:124` `calculateCorners(BlockPos, Level, Blueprint, RotationMirror)`, walks it chunk by chunk, and reports the first unowned chunk, how many are missing, the total, and who owns the first one. Type-specific refusals: `mc/api/blocks/AbstractBlockHut.java:386` `canPlaceAt(BlockPos, Player)`, overridden for the town hall (`mc/core/blocks/huts/BlockHutTownHall.java:220-233`, refuses a second town hall) and the tavern (`mc/core/blocks/huts/BlockHutTavern.java:55`); dispatched through `mc/api/colony/managers/interfaces/IRegisteredStructureManager.java:207` and `mc/core/colony/managers/RegisteredStructureManager.java:1063` | Obstruction and overlap with another building are never checked — the builder simply clears whatever is in the way, so the mod has never needed to ask. There is no count of blocks that would need clearing and no bounding box returned. `findClaimGap` is private, is written against an existing `IWorkOrder` rather than a prospective type and level, and runs only when an order is created. Its refusal messages take the player's chat rather than a return value (`WorkManager.java:311-315`). |
| `Sites.placeHutBlock(type, pos, facing, style, level)` | COMMAND EXISTS | `mc/core/network/messages/server/DirectPlaceMessage.java:101-141` sets the block, resolves the blueprint asynchronously, calls `setPlacedBy` and consumes the stack; the build-tool path is `mc/api/blocks/AbstractBlockHut.java:113-132` `onBlockPlacedByBuildTool`. The registration itself is `mc/api/blocks/AbstractColonyBlock.java:275-301` `setPlacedBy`, which finds the colony and calls `addNewBuilding`. A player-free path already exists: `mc/core/placementhandlers/HutPlacementHandler.java:109` calls `setPlacedBy(world, pos, state, null, stack)` when a builder's own structure contains child huts | The verb is assembled inside a packet handler that needs a `ServerPlayer`, reduces a stack in that player's inventory (`DirectPlaceMessage.java:105`) and reports refusals as chat. No check runs before the block goes down. The blueprint path resolution is asynchronous (`ServerFutureProcessor.queueBlueprint`, `:121`), so a synchronous call has to decide what to return before the future completes. |

**Push.**

| row | state | evidence | what is missing |
|---|---|---|---|
| `HutBlockBrokenEvent` | PARTIAL | The moment exists and, uniquely, still knows who did it: `mc/core/colony/permissions/ColonyPermissionEventHandler.java:343` `onBlockBreak(Player, BlockPos, BlockState)` tests `state.getBlock() instanceof AbstractBlockHut`, resolves the building, and calls `building.destroy()` at `:360` (protection off) and `:376` (protection on, permission granted). Downstream, `BuildingRemovedModEvent` is posted at `mc/core/colony/managers/RegisteredStructureManager.java:904` | The event itself and the attribution. `BuildingRemovedModEvent` fires for every removal and carries no player and no cause, so a caller cannot tell a broken hut block from a deconstruction. A new post at `ColonyPermissionEventHandler.java:376` (and `:360`) with the player in hand is the natural firing point; the "while its building still existed" condition is already implied there, since the handler returns early when `getBuilding` is null (`:352-356`). |

---


### 3.11. §2.13 Permissions

This is the best-covered block in the inventory: the mod has a full rank-and-action permission model
and two of the three Tier 1 calls are one-line wrappers.

| row | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `Api.callerIdentity()` | MISSING | The pieces exist separately. Operator status: `mc/core/commands/commandTypes/IMCCommand.java:120-128` `isPlayerOped(Player)`; a config-driven bypass level is used instead in the permission path, `mc/core/colony/permissions/Permissions.java:710`. Colony membership: `mc/api/colony/permissions/IPermissions.java:99` `isColonyMember(Player)`. Owner: `:132` `getOwner()`, `:113` `getOwnerEntry()` | There is no notion of a caller at all — every entry point in the mod is either a packet carrying a `ServerPlayer` or a command carrying a `CommandSourceStack`. "Is this automation rather than a person" has no counterpart anywhere, and neither does "what may it do with no colony in hand". The record and the identity concept are both new. |
| `colony.permissions().rankOf(PlayerRef)` | EXISTS | `mc/api/colony/permissions/IPermissions.java:153` `Rank getRank(UUID player)`, with a `Player` overload at `:161`; the five well-known ranks at `:51-75` `getRankOwner/getRankOfficer/getRankHostile/getRankNeutral/getRankFriend`; reached from `mc/api/colony/IColony.java:74` `getPermissions()` | Only the mapping from the mod's open-ended `Rank` set (ranks can be added and removed, `IPermissions.java:182,188`) onto the design's closed five-value enum. |
| `colony.permissions().can(PlayerRef, ColonyAction)` | EXISTS | `mc/api/colony/permissions/IPermissions.java:28` `hasPermission(Rank, Action)` and `:84` `hasPermission(Player, Action)`; the action set at `mc/api/colony/permissions/Action.java:9-43` (31 actions, including `PLACE_HUTS`, `MANAGE_HUTS`, `ACCESS_HUTS`, `EDIT_PERMISSIONS`) | The `Player`-typed overload needs a loaded entity; the `Rank` overload composed with `getRank(UUID)` avoids that and is the wrapper to write. The design's `ColonyAction` and the mod's `Action` are different vocabularies — the mod's is block-interaction-shaped (`PLACE_BLOCKS`, `FILL_BUCKET`, `THROW_POTION`) and has no member for "start research" or "hire" — so several design actions have no action to test. |

**Push.** No Tier 1 events in this block.

---


### 3.12. §2.14 Diagnostics

| row | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `colony.problems()` | PARTIAL | A working prototype exists as an operator command: `mc/core/commands/colonycommands/CommandColonyDiagnose.java:107-233` `onExecute` builds eight problem lists — workers by AI state, citizens with no AI, jobs with no building, couriers no warehouse adopted (`:331-361`), stalled or unresolved requests (`:533-600`), unclaimed work orders, inconsistent buildings (`:471-520`), unfilled job slots — counts them, sorts workers by severity then age, and prints. Per-citizen problems come from the interaction system of section 9 | Everything that makes it an API. The output is `String.format` lines emitted to chat and the server log through `IMCOPCommand`, so it needs a `CommandSourceStack` and an operator. Problems are strings, not typed values with a kind, a severity, a subject and a fix — severity exists only as an `int` inside a private `Worker` record (`:714`), assigned at `:281`. Ages come from a static, non-persisted `Map<Integer, Map<String, Observation>>` rebuilt on each run (`:91`), because, as the comment at `:85-86` states, nothing in the colony model records how long a citizen has been in an AI state or how long a request has been open. And it never consults the per-citizen interaction list, which is where the nine causes of §7.2 actually live. |
| `Colonies.config()` | PARTIAL | `mc/api/IMinecoloniesAPI.java:70` `getConfig()` returning `Configurations<Client, Server, Common>`; the fields the row names are all present on `mc/api/configuration/ServerConfiguration.java` — `:179` `initialCitizenAmount`, `:183` `maxCitizenPerColony`, `:180` `allowInfiniteSupplyChests`, `:181` `allowInfiniteColonies`, `:201-204` raid settings, `:205-207` free-mode settings | No `ConfigReport` record and no single accessor: the values are individual `IntValue`/`BooleanValue` holders spread over three configuration classes, and colony size / minimum distance / daylight-cycle state are read from elsewhere (world game rules and other config groups). Assembly only, but assembly across several sources. |
| `Api.version()` | MISSING | `mc/api/util/constant/Constants.java:11` holds `MOD_ID` and nothing else; no version constant exists in the API package. The mod does read its own metadata elsewhere via `FabricLoader.getInstance().getModContainer(namespace)` (`mc/core/event/ClientEventHandler.java:358-359`, client-side) | A version accessor. Mod version comes from the Fabric mod container, game version from the server; API version is a new concept with nothing to read. Small, but nothing exists. |

**Push.**

| event | state | evidence (file:line, symbol) | what is missing |
|---|---|---|---|
| `ColonyProblemRaisedEvent` | MISSING | No bus post, and no colony-level problem set to raise from. `CommandColonyDiagnose` computes the equivalent lists on demand (`mc/core/commands/colonycommands/CommandColonyDiagnose.java:117-192`) and discards them at the end of the command. The colony's own per-tick pass, `mc/core/colony/Colony.java` `onWorldTick`/`onServerTick` (`mc/api/colony/IColony.java:46,51`), is where such a set would be maintained | The whole subsystem: a typed problem set held on the colony, recomputed on a schedule, diffed against the previous set, and posted. This is the row that turns `colony.problems()` from a query into a subscription, and it has no foundation to sit on. |
| `ColonyProblemClearedEvent` | MISSING | Same | Same — the mirror half of the same diff. |

---

---

## 4. The idleness question: what the mod knows about why a citizen is not working

The design treats the reason a worker stands still as a first-class typed value and asks whether that
is a thin wrapper or a new subsystem. The answer is **neither cleanly**: the mod knows almost every
one of the nine causes, knows them server-side, keeps them in a persisted per-citizen collection, and
re-checks them every tick — but it keeps them as **translated chat text keyed by translated chat
text**, and every accessor that turns that collection into a ranked list lives on the client.

### 4.1. Four places the knowledge lives, in decreasing usefulness

**(a) The interaction map — the real answer.** `mc/core/colony/CitizenData.java:252` declares

```java
protected final Map<Component, IInteractionResponseHandler> citizenChatOptions = new HashMap<>();
```

This is the citizen's live problem list. Entries are added through one funnel,
`triggerInteraction(...)` at `CitizenData.java:1871-1882`, which refuses duplicates and marks the
citizen dirty. They are removed through one funnel, the validity sweep in `CitizenData.update(...)`
at `:1832-1866`, which calls `handler.isValid(this)` on every entry each tick and drops the ones that
have gone false. The map is serialised to disk (`:1499-1507`) and to the client (`:1123-1130`).

Each entry carries three things a `WorkProblem` needs:

* a **severity** — `IInteractionResponseHandler.getPriority()`
  (`mc/api/colony/interactionhandling/IInteractionResponseHandler.java:69`) returning one of
  `HIDDEN, CHITCHAT, PENDING, IMPORTANT, BLOCKING`
  (`mc/api/colony/interactionhandling/ChatPriority.java:8-12`). The design's three-value
  `ProblemSeverity` maps onto this cleanly;
* a **message** — `getInquiry()` (`:29`), a translatable `Component`;
* a **stable identity** — `ServerCitizenInteraction.validatorId`
  (`mc/core/colony/interactionhandling/ServerCitizenInteraction.java:59`), persisted at `:198` and
  used at `:222` to re-attach the predicate after a reload. This is the field a `ProblemKind` would
  be derived from. It is a `Component`, i.e. a translation key wrapped in a text object, not an enum.

**(b) The validator registry — the catalogue of causes.**
`mc/api/colony/interactionhandling/InteractionValidatorRegistry.java` holds three maps of predicates:
`Predicate<ICitizenData>` (`:22`), `BiPredicate<ICitizenData, BlockPos>` (`:27`) and
`BiPredicate<ICitizenData, IToken<?>>` (`:32`). `mc/apiimp/initializer/InteractionValidatorInitializer.java`
registers 57 of them between lines 55 and 408. They are the closest thing the mod has to a
`ProblemKind` enum, and they are all pure, server-side, side-effect-free tests over citizen data.
Against §7.2's nine causes:

| §7.2 cause | what the tree has |
|---|---|
| 1. Night, rain or snow | Rain: predicate at `InteractionValidatorInitializer.java:383-386`; raised at `mc/core/entity/ai/workers/CitizenAI.java:275` with `ChatPriority.HIDDEN` and the `BAD_WEATHER` icon, returning `CitizenAIState.IDLE` (`:277`). The rain exemption — config, research, hut level — is `CitizenAI.java:342-363` `shouldWorkWhileRaining()`. Night: predicate at `:391-392` keyed on `isAsleep()`, raised at `mc/core/entity/citizen/citizenhandlers/CitizenSleepHandler.java:128` with `ChatPriority.HIDDEN`; the sleep decision is `CitizenAI.java:180-196`. |
| 2. Mourning | Predicate at `:394-396`; raised at `CitizenAI.java:247` / `:257` with `ChatPriority.IMPORTANT`, returning `CitizenAIState.MOURN` (`:264`). |
| 3. A raid is running | Predicate at `:388-389`; raised at `CitizenAI.java:165` with `ChatPriority.IMPORTANT`, returning `CitizenAIState.SLEEP` (`:167`). |
| 4. The hut is paused | Not a hut flag: `ICitizen.isPaused()` (`mc/api/colony/ICitizen.java:64`), toggled per citizen by `mc/core/network/messages/server/colony/citizen/PauseCitizenMessage.java:74`, honoured by the AI at `mc/core/entity/ai/workers/AbstractEntityAIBasic.java:2197-2209`. No interaction is raised for it. |
| 5. Hunger | `CitizenAI.java:305-337` `shouldEat()` decides it and returns `CitizenAIState.EATING` (`:227`); the zero-saturation complaints are `InteractionValidatorInitializer.java:60-68`. |
| 6. An open request | `mc/core/colony/buildings/AbstractBuilding.java:1574-1576` raises a `RequestBasedInteraction` at `ChatPriority.BLOCKING` for every non-async request, with the request token attached; validity is `RequestUtils.requestChainNeedsPlayer` (`:164-173`). The AI's own waiting state is `AIWorkerState.NEEDS_ITEM`, entered at `AbstractEntityAIBasic.java:263,274`. |
| 7. Wrong tool tier | The ceiling is `IBuilding.getMaxEquipmentLevel()` (`mc/api/colony/buildings/IBuilding.java:479`); the complaint compares it against the block being mined at `InteractionValidatorInitializer.java:146-162` (`REQUEST_SYSTEM_BUILDING_LEVEL_TOO_LOW`). The tool request path is `AbstractEntityAIBasic.java:1091-1143`, and it is one of the few places that writes `JobStatus.STUCK` (`:1096`). |
| 8. Full inventory | Predicate at `:130-131` (`InventoryUtils.isBuildingFull`); raised at `AbstractEntityAIBasic.java:1320`; the AI state is `AIWorkerState.INVENTORY_FULL`, entered at `:255`. |
| 9. Low hut level or low experience | `NEEDS_BETTER_HUT` predicate at `:280-290`, raised at `mc/core/entity/ai/workers/production/EntityAIStructureMiner.java:512` with `ChatPriority.BLOCKING`. Written for the miner only; no equivalent exists for a worker whose job level is below what the task needs. |

Eight of the nine already have a named, persisted, server-evaluated marker. The ninth (pause) is a
plain boolean on the citizen.

**(c) `JobStatus` — a three-value summary, sparsely written.**
`mc/api/entity/ai/JobStatus.java:8-16` defines `IDLE`, `WORKING`, `STUCK`; it is persisted
(`CitizenData.java:1508`, `:1694-1701`) and exposed as `getJobStatus()` / `isIdleAtJob()`
(`mc/api/colony/ICitizenData.java:262`, `mc/core/colony/CitizenData.java:1885-1890`). It carries no
reason, and only six classes write it — the tool check in `AbstractEntityAIBasic`, plus
`EntityAIWorkNether`, `EntityAIWorkFarmer`, `EntityAIWorkPlanter`, `EntityAIWorkStablemaster` and
`EntityAIWorkUndertaker` — so a worker blocked for any other cause still reads `WORKING`.

**(d) The AI state machines — the cause as control flow.**
`CitizenAI.calculateNextState()` (`mc/core/entity/ai/workers/CitizenAI.java:136-298`) is the method
that decides *why* a citizen is not working. It tests sickness, raid, night, mourning, rain and
hunger in that order and returns a `CitizenAIState`. The reason itself is not returned: what survives
each branch is a state (`SLEEP`, `MOURN`, `EATING`, `IDLE`), an icon
(`VisibleCitizenStatus`, `mc/api/entity/citizen/VisibleCitizenStatus.java:26-40`), and — for three of
the six branches — an interaction. So the branch is where the knowledge is at its most precise and
where it is discarded fastest. The per-job machine below it (`AIWorkerState`,
`mc/api/entity/ai/statemachine/states/AIWorkerState.java`) holds the finer causes as states:
`NEEDS_ITEM`, `INVENTORY_FULL`, `PAUSED`. That state is readable —
`EntityCitizen.getCitizenAI()` (`mc/core/entity/citizen/EntityCitizen.java:1950`) and
`IJob.getWorkerAI().getState()` (`mc/api/entity/ai/ITickingStateAI.java:38`) — but only while the
entity is loaded.

### 4.2. What is actually missing

Not the causes. Four things:

1. **A server-side accessor.** `citizenChatOptions` is `protected` with no getter on `ICitizenData`.
   The list accessors — `getOrderedInteractions()`, `hasBlockingInteractions()` — are declared on
   `mc/api/colony/ICitizenDataView.java:111,127` and implemented on the client view
   (`mc/core/colony/CitizenDataView.java:460-484`), and the ordering that makes it a *ranked* list is
   built at `CitizenDataView.java:385`, after the packet. Server-side the same data is an unordered
   `HashMap`. This is the single change that turns the block from a subsystem into a wrapper.
2. **A type.** Identity is a translation-key `Component`. Mapping those keys onto the design's
   `ProblemKind` enum is a table someone must write, and the registry cannot be enumerated to help:
   `InteractionValidatorRegistry` exposes per-key getters and `hasValidator` (`:106`) but never
   its key set.
3. **The three causes with no interaction.** Pause, and the two halves of cause 1 and 5 that live
   only as AI branches, raise nothing durable. They would be read directly — `isPaused()`, the world
   clock and weather, `getSaturation()` — rather than through the interaction map, so a `problems()`
   implementation is necessarily a union of two sources, not one.
4. **`suggestedFix` and `guideSection`.** No counterpart exists in any form.

`building.problems()` is a larger job than `citizen.problems()`, because every building-shaped
predicate in the registry is written to take an `ICitizenData` and reach the building through
`citizen.getWorkBuilding()`. A hut with nobody in it — the case the row exists for — evaluates none of
them.

`colony.problems()` is larger still, and its obstacle is different: `CommandColonyDiagnose`
demonstrates that the aggregation is a few hundred lines of straightforward traversal, but it also
demonstrates the one thing the model does not hold. Its comment at
`mc/core/commands/colonycommands/CommandColonyDiagnose.java:85-86` — *"Nothing in the colony model
records how long a citizen has been in an AI state or how long a request has been open"* — is the
reason it keeps a static observation map, and it is the reason the design's grace period, its
`lastProductiveGameTime`, and its raised/cleared event pair all need new state rather than a new
query.

---


---

## 5. Cross-cutting findings

### 5.1. From the colony, construction, buildings, territory and placement half


**One accessor would satisfy nine rows.** `ICommonRegisteredStructureManager.getBuildings()`
(`mc/api/colony/managers/interfaces/ICommonRegisteredStructureManager.java:61`) plus a type filter
is behind `colony.buildings()`, `colony.buildingsOfType()`, `colony.building()`,
`colony.buildingAt()`, `colony.builders()`, `colony.maxBuildableLevel()`, the building counts in
`colony.progress()`, the per-building contributions in `colony.claim()`, and the bed arithmetic in
`colony.capacity()`. The map is already the single source; what is missing is a by-type query, which
the mod re-implements inline at least five times.

**Everything is assembled for the interface and thrown away.** The clearest case is
`BuildingResourcesModule.serializeToView` (`mc/core/colony/buildings/modules/BuildingResourcesModule.java:76-106`):
it refreshes availability, computes percent complete, and writes progress stage counters directly
into a client packet. None of it is stored, and the refresh method it calls is private with that one
caller. `CommandColonyInfo` (`mc/core/commands/colonycommands/CommandColonyInfo.java:50-107`) does
the same with chat components instead of packets. `WindowBuildBuilding`
(`mc/core/client/gui/WindowBuildBuilding.java:443-476`) goes further and performs the entire
upgrade-cost computation on the client, so the server has never needed to know what a level costs
before a builder starts scanning for it. Any API row whose answer today reaches a human through a
GUI is in this category.

**Commands cannot answer without acting.** Seven of the ten commands in scope are implemented only
inside a `ServerPlayer`-bearing packet handler, and the mutating methods behind them return `void`
and report refusals by sending the player chat. `AbstractBuilding.requestWorkOrder`
(`mc/core/colony/buildings/AbstractBuilding.java:479-604`) is the worst instance: it is `protected`,
it computes eight distinct refusals, it returns the right enum, and the two public entry points
above it discard that enum (`:902`, `:906`). Two pieces of prior work in this tree point the way —
`WorkOrderRequestResult` (`mc/api/colony/workorders/WorkOrderRequestResult.java`), a refusal enum
built precisely so a caller asking about many buildings could count answers instead of reading sixty
chat lines, and `CommandColonyRepairAll.repair(building, preview)`
(`mc/core/commands/colonycommands/CommandColonyRepairAll.java:138-167`), which separates a read-only
preview from the act and documents in its own comment where the separation stops being possible.
Extending that split through `requestWorkOrder` collapses `building.buildEligibility()`,
`building.requestBuild()`, `building.requestUpgrade()` and `BuildOrderRejectedEvent` into one piece
of work.

**Refusals are scattered across three layers, and one of them is after the fact.** Founding a colony
is checked in the packet handler (`mc/core/network/messages/server/CreateColonyMessage.java:110-162`),
in the manager (`isFarEnoughFromColonies`) and in the chunk helper (`canClaimChunksInRange`).
Ordering a build is checked in `requestUpgrade` (research), in `requestWorkOrder` (builder, blueprint,
world height) and — after `requestWorkOrder` has already returned `QUEUED` — in
`WorkManager.addWorkOrder` via `findClaimGap` (`mc/core/colony/workorders/WorkManager.java:310-315`).
Every `*Check` and `*Eligibility` row in this half is really the same task: pull those tests out of
the acting path and give them a common answer type.

**The event bus is a subscribe-only surface with three colony-lifecycle posts.**
`EventBus` (`mc/api/eventbus/EventBus.java:15,21`) offers `subscribe` and `post` and no
unsubscribe, so `Subscription.close()` has nothing behind it. Of the eleven events in this half,
five already have a post at the right moment; the five build-order events have none, and their
firing points are all inside two methods, `WorkManager.addWorkOrder` and
`WorkManager.tryAssignWorkOrder`. Payload work is uniform: the existing events carry only the domain
object, and every field the catalogue names is read back off it.

**Nothing measures the world.** §2.12's terrain row has no precedent of any kind. The mod reads the
world for pathing, for farmland and for placement, but never characterises an area. That row shares
no machinery with anything else in this half.

**Colony-day counting is a counter, not a clock.** `Colony.day` (`mc/core/colony/Colony.java:456`)
is incremented in `checkDayTime` (`:1055`) and `isDay` is toggled beside it, which serves
`colony.clock()` and the Tier 2 day/night events, but there is no founding timestamp and no
gamerule read, so `colony.identity()` and `colony.clock()` both need new stored or read-through
state rather than a wrapper.

---


### 5.2. From the citizens, work, requests, storage, food, permissions and diagnostics half


**One accessor would satisfy an entire block.** A server-side `List<IInteractionResponseHandler>
getInteractions()` on `ICitizenData`, mirroring `ICitizenDataView.getOrderedInteractions()`, is the
load-bearing change for `citizen.problems()`, most of `citizen.workStatus()`, `building.problems()`,
`colony.problems()`, both §2.5 events and both §2.14 events. Eight of the 52 rows in this inventory
turn on that one method plus a key-to-enum table.

**The recurring failure mode is "assembled for the interface".** Four separate rows are blocked by
the same pattern: the value is computed, sent to the client as raw parts, and given its useful shape
in a GUI class. The list is `colony.playerRequests()` (assembled in
`mc/core/client/gui/WindowClipBoard.java:112-176`), `request.fulfill()` (sequenced in
`mc/core/client/gui/citizen/RequestWindowCitizen.java:146-206`), `citizen.problems()` (ordered in
`mc/core/colony/CitizenDataView.java:385`), and the citizen's own health and happiness, which reach a
caller only through `CitizenData.serializeViewNetworkData` (`mc/core/colony/CitizenData.java:1084-1135`).
In each case the server holds the inputs and the client owns the algorithm.

**The second failure mode is "computed per tick and discarded".** `CitizenAI.calculateNextState()`
(`mc/core/entity/ai/workers/CitizenAI.java:136`) decides the reason a citizen is idle and returns a
state; `EntityCitizen.updateHealing()` (`:831`) decides a citizen is starving and applies an effect;
`RequestUtils.requestChainNeedsPlayer()` (`mc/api/colony/requestsystem/request/RequestUtils.java:30`)
decides a request has escalated to the player and is used only as a chat-bubble validity test. All
three are the exact predicate an event needs, and none of them is remembered from one tick to the
next. **Every Tier 1 event in this inventory that is MISSING is missing for this reason**: the moment
is computed, the transition is not.

**The third is "reachable only with a player or a command source".** All seven COMMAND EXISTS rows
are reached through packet handlers taking a `ServerPlayer` — six distinct classes, `HireFireMessage`
serving both hire and fire, plus `AssignUnassignMessage`, `RecallCitizenHutMessage`,
`TransferItemsRequestMessage`, `AssignFilterableItemMessage` and `UpdateRequestStateMessage`. The
`colony.problems()` prototype takes a `CommandSourceStack` and requires operator rank. In every case the verb itself is a plain method one level down and the player
is needed only to report failure, because these handlers report by sending chat rather than by
returning a value. Extracting `CommandOutcome` from them is one shape of work repeated seven times.

**A fourth: nothing is timestamped.** No citizen records when it last did useful work, no request
records when it was opened, no building records when it last produced. `CommandColonyDiagnose` says
so and works around it with a per-run static map (`:85-91`). The design's grace period,
`lastProductiveGameTime`, "days standing", request age and stall detection all sit on top of state
that does not exist. Whatever adds it should add it once.

**Several rows collapse into one piece of work.** `colony.stock()`, `colony.countOf()`,
`colony.canAfford()` and `colony.foodStock()` are one traversal — warehouses, then other buildings,
then citizen inventories — with four different reducers. The per-building primitives already exist
(`InventoryUtils.getCountFromBuilding:843`, `getBuildingInventory:3305`); the traversal does not.
Likewise `colony.requests()`, `colony.playerRequests()` and `request.detail()` are one request-graph
walk with three projections.

**Three of the catalogue's stated premises do not hold in this tree**, and a caller written against
them would be wrong. Courier slots per warehouse are `buildingLevel * 4`
(`mc/core/colony/buildings/modules/CourierAssignmentModule.java:124-128`), not two per level capped at
ten. Dining-hall fuel is not off by default: the module is constructed with coal and charcoal already
allowed (`mc/core/colony/buildings/modules/BuildingModules.java:75`). The saturation threshold the
code works to is `AVERAGE_SATURATION = 10` (`mc/api/util/constant/CitizenConstants.java:54`), not 5.
This is the argument for the catalogue's own rule that such numbers be read live rather than assumed.

---


---

## 6. Biggest gaps

### 6.1. Colony, construction, buildings, territory, placement


Ordered by expected cost.

1. **`BuildOrderStalledEvent` / `BuildOrderResumedEvent`** — no stall state exists on a work order at
   all, so this is a clock, a grace period, a reason taxonomy and edge detection, and the reason has
   to be gathered from the builder's AI state and open requests, which the order cannot currently
   see.
2. **`Sites.terrain`** — the only row in this half with no precedent whatsoever; height range,
   flatness, largest flat square, ground and water counts and biome all have to be computed from a
   bounded, chunk-loading scan that nothing in the mod resembles.
3. **`building.upgradeCost`** — the computation exists only in a client GUI and, server-side, only as
   an incremental builder-AI state that runs after an order is placed; making it answerable on demand
   for an arbitrary target level means lifting the Structurize `GET_RES_REQUIREMENTS` scan out of
   both and giving it somewhere to cache.
4. **`Sites.checkHutSite`** — the claim half is genuinely there in `findClaimGap`, but obstruction and
   overlap have never been checked by anything, because the builder clears whatever is in the way;
   the footprint box and the cleared-block count are new.
5. **`building.buildEligibility`** — eight refusals computed correctly today but only as a side effect
   of acting, plus one refusal (outside the claim) evaluated a layer later, after success has already
   been reported.
6. **`order.missingResources`** — the list belongs to the builder's hut rather than the order, its
   availability figures are refreshed only on the way to a client packet, and colony-held stock is
   never counted at all, so the row's four-number answer is one number short at source.
7. **`colony.capacity`** — a `min()` of three terms discards which term bound the answer, and the
   beds half exists only per residence, so the row needs both a decomposition and an aggregation.
8. **`Colonies.found`** — the verb is real but only as a packet handler that requires a player, an
   already-placed tile entity and a chain of inline checks; a plain call means re-composing it around
   `createColony` without losing any of the five refusals.

### 6.2. Citizens, work, requests, storage, food, permissions, diagnostics


Ordered by cost, worst first.

1. **`colony.problems()` (§2.14) and its two events.** The call has a prototype;
   `ColonyProblemRaisedEvent` and `ColonyProblemClearedEvent` have nothing whatever. Together they
   need a typed problem set held on the colony, recomputed on a schedule, diffed for the two events,
   and fed from citizens, buildings, orders and requests. `CommandColonyDiagnose` proves the
   traversal is tractable and in the same breath proves what is absent — the model has no memory of
   how long anything has been in its current state, which is what both events and the design's grace
   period depend on.
2. **`building.problems()` (§2.5).** Every building-shaped predicate in the tree is written to start
   from a citizen and reach the building through `getWorkBuilding()`, so the empty hut — the case the
   row exists for — currently reports nothing. Inverting them is per-predicate work across the
   whole registry.
3. **`citizen.problems()` (§2.5).** Cheap in one sense and expensive in another: the accessor is a
   few lines, but the 57 registered translation-key identities have to be mapped by hand onto a
   `ProblemKind` enum, and three causes (pause, weather, hunger) are not in the interaction map at all and must be
   read from a second source and merged.
4. **`colony.food()` and `colony.foodStock()` (§2.8).** Two aggregate reports over data that is never
   aggregated. Colony average saturation does not exist as a value anywhere, and grouping stock by
   food tier requires both a colony-wide stock traversal that does not exist and a tier classifier
   whose two candidate implementations use different scales.
5. **`colony.stock()` and `colony.canAfford()` (§2.7).** The per-building count primitives are solid;
   what is missing is a colony-wide traversal that splits by location, includes citizen inventories,
   and states honestly that unloaded chunks are excluded. `canAfford` then sits on top of it, with
   nothing to reuse but a builder-specific four-value availability enum.
6. **`request.fulfill()` (§2.6).** The verb exists but does only half the job — `overruleRequest`
   moves no items. The half that moves items, clamps counts and refuses when the source is short is
   client GUI code and has to be rewritten server-side.
7. **`colony.couriers()` (§2.6).** Most of the report can be assembled from existing modules, but
   "stacks per trip" is not a quantity this tree computes at all — carrying capacity is read as free
   inventory slots at the moment of use — and the warehouse-to-courier-hut association is one-way.
8. **`Api.callerIdentity()` (§2.13).** Small in code and total in concept: the mod has no caller,
   only players and command sources. Everything else in §2.13 is a one-line wrapper over a permission
   model that is already complete.