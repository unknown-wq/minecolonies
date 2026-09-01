package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildings.BuildingMysticalSite;
import com.minecolonies.core.colony.buildings.DefaultBuildingInstance;
import com.minecolonies.core.colony.buildings.modules.HomeBuildingModule;
import com.minecolonies.core.colony.buildings.modules.TavernBuildingModule;
import com.minecolonies.core.colony.buildings.views.EmptyView;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import net.minecraft.resources.Identifier;


import static com.minecolonies.core.colony.buildings.modules.BuildingModules.*;
import net.minecraft.core.Registry;
import java.util.function.Supplier;

public final class ModBuildingsInitializer
{

    private ModBuildingsInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModBuildingsInitializer but this is a Utility class.");
    }

    static
    {
        ModBuildings.archery = register(ModBuildings.ARCHERY_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutArchery)
          .setBuildingProducer(BuildingArchery::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.ARCHERY_ID))
          .addBuildingModuleProducer(ARCHERY_WORK_HOME)
          .addBuildingModuleProducer(BED)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.bakery = register(ModBuildings.BAKERY_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutBaker)
          .setBuildingProducer(BuildingBaker::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.BAKERY_ID))
          .addBuildingModuleProducer(BAKER_WORK)
          .addBuildingModuleProducer(BAKER_CRAFT)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(BAKER_SMELT)
          .addBuildingModuleProducer(SETTINGS_CRAFTER_RECIPE)
          .addBuildingModuleProducer(FURNACE)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(ITEMLIST_FUEL)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.barracks = register(ModBuildings.BARRACKS_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutBarracks)
          .setBuildingProducer(BuildingBarracks::new)
          .setBuildingViewProducer(() -> BuildingBarracks.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.BARRACKS_ID))
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(BARRACKS_SETTINGS)
          .addBuildingModuleProducer(BARRACKS_STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.barracksTower = register(ModBuildings.BARRACKS_TOWER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutBarracksTower)
          .setBuildingProducer(BuildingBarracksTower::new)
          .setBuildingViewProducer(() -> BuildingBarracksTower.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.BARRACKS_TOWER_ID))
          .addBuildingModuleProducer(KNIGHT_BARRACKS_WORK)
          .addBuildingModuleProducer(RANGER_BARRACKS_WORK)
          .addBuildingModuleProducer(DRUID_BARRACKS_WORK)
          .addBuildingModuleProducer(HUSCARL_BARRACKS_WORK)
          .addBuildingModuleProducer(MARKSMAN_BARRACKS_WORK)
          .addBuildingModuleProducer(GUARD_TOOL)
          .addBuildingModuleProducer(GUARD_ENTITY_LIST)
          .addBuildingModuleProducer(GUARD_SETTINGS)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(BED)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.blacksmith = register(ModBuildings.BLACKSMITH_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutBlacksmith)
          .setBuildingProducer(BuildingBlacksmith::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.BLACKSMITH_ID))
          .addBuildingModuleProducer(BLACKSMITH_WORK)
          .addBuildingModuleProducer(BLACKSMITH_CRAFT)
          .addBuildingModuleProducer(SETTINGS_CRAFTER_RECIPE)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.builder = register(ModBuildings.BUILDER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutBuilder)
          .setBuildingProducer(BuildingBuilder::new)
          .setBuildingViewProducer(() -> BuildingBuilder.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.BUILDER_ID))
          .addBuildingModuleProducer(BUILDER_WORK)
          .addBuildingModuleProducer(BUILDER_CRAFT)
          .addBuildingModuleProducer(BUILDING_RESOURCES)
          .addBuildingModuleProducer(BUILDER_SETTINGS)
          .addBuildingModuleProducer(WORKORDER_VIEW)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.chickenHerder = register(ModBuildings.CHICKENHERDER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutChickenHerder)
          .setBuildingProducer(BuildingChickenHerder::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.CHICKENHERDER_ID))
          .addBuildingModuleProducer(CHICKENHERDER_WORK)
          .addBuildingModuleProducer(CHICKENHERDER_HERDING)
          .addBuildingModuleProducer(CHICKENHERDER_SETTINGS_BREEDING)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.combatAcademy = register(ModBuildings.COMBAT_ACADEMY_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutCombatAcademy)
          .setBuildingProducer(BuildingCombatAcademy::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.COMBAT_ACADEMY_ID))
          .addBuildingModuleProducer(KNIGHT_TRAINING)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(BED)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.composter = register(ModBuildings.COMPOSTER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutComposter)
          .setBuildingProducer(BuildingComposter::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.COMPOSTER_ID))
          .addBuildingModuleProducer(COMPOSTER_WORK)
          .addBuildingModuleProducer(COMPOSTER_SETTINGS)
          .addBuildingModuleProducer(ITEMLIST_COMPOSTABLE)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.cook = register(ModBuildings.COOK_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutCook)
          .setBuildingProducer(BuildingCook::new)
          .setBuildingViewProducer(() ->  BuildingCook.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.COOK_ID))
          .addBuildingModuleProducer(COOK_WORK)
          .addBuildingModuleProducer(FURNACE)
          .addBuildingModuleProducer(ITEMLIST_FUEL)
          .addBuildingModuleProducer(RESTAURANT_MENU)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.cowboy = register(ModBuildings.COWBOY_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutCowboy)
          .setBuildingProducer(BuildingCowboy::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.COWBOY_ID))
          .addBuildingModuleProducer(COWHERDER_WORK)
          .addBuildingModuleProducer(COWHERDER_HERDING)
          .addBuildingModuleProducer(COWHERDER_SETTINGS)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.stable = register(ModBuildings.STABLE_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutStable)
          .setBuildingProducer(BuildingStable::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.STABLE_ID))
          .addBuildingModuleProducer(CAVALRY_STABLE_WORK)
          .addBuildingModuleProducer(STABLEMASTER_WORK)
          .addBuildingModuleProducer(STABLEMASTER_HERDING)
          .addBuildingModuleProducer(GUARD_ENTITY_LIST)
          .addBuildingModuleProducer(STABLE_SETTINGS)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.crusher = register(ModBuildings.CRUSHER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutCrusher)
          .setBuildingProducer(BuildingCrusher::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.CRUSHER_ID))
          .addBuildingModuleProducer(CRUSHER_WORK)
          .addBuildingModuleProducer(CRUSHER_CRAFT)
          .addBuildingModuleProducer(CRUSHER_SETTINGS)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.deliveryman = register(ModBuildings.DELIVERYMAN_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutDeliveryman)
          .setBuildingProducer(BuildingDeliveryman::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.DELIVERYMAN_ID))
          .addBuildingModuleProducer(COURIER_WORK)
          .addBuildingModuleProducer(COURIER_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.farmer = register(ModBuildings.FARMER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutFarmer)
          .setBuildingProducer(BuildingFarmer::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.FARMER_ID))
          .addBuildingModuleProducer(FARMER_WORK)
          .addBuildingModuleProducer(FARMER_CRAFT)
          .addBuildingModuleProducer(FARMER_FIELDS)
          .addBuildingModuleProducer(FARMER_SETTINGS)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.fisherman = register(ModBuildings.FISHERMAN_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutFisherman)
          .setBuildingProducer(BuildingFisherman::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.FISHERMAN_ID))
          .addBuildingModuleProducer(FISHER_WORK)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.guardTower = register(ModBuildings.GUARD_TOWER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutGuardTower)
          .setBuildingProducer(BuildingGuardTower::new)
          .setBuildingViewProducer(() -> BuildingGuardTower.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.GUARD_TOWER_ID))
          .addBuildingModuleProducer(KNIGHT_TOWER_WORK)
          .addBuildingModuleProducer(RANGER_TOWER_WORK)
          .addBuildingModuleProducer(MARKSMAN_TOWER_WORK)
          .addBuildingModuleProducer(HUSCARL_TOWER_WORK)
          .addBuildingModuleProducer(GUARD_TOOL)
          .addBuildingModuleProducer(GUARD_ENTITY_LIST)
          .addBuildingModuleProducer(GUARD_SETTINGS)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(BED)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.home = register(ModBuildings.HOME_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutHome)
          .setBuildingProducer((colony, blockPos) -> new DefaultBuildingInstance(colony, blockPos, "residence", 5))
          .setBuildingViewProducer(() -> HomeBuildingModule.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.HOME_ID))
          .addBuildingModuleProducer(HOME)
          .addBuildingModuleProducer(LIVING)
          .addBuildingModuleProducer(BED)
          .createBuildingEntry());

        ModBuildings.library = register(ModBuildings.LIBRARY_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutLibrary)
          .setBuildingProducer(BuildingLibrary::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.LIBRARY_ID))
          .addBuildingModuleProducer(STUDENT_WORK)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.lumberjack = register(ModBuildings.LUMBERJACK_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutLumberjack)
          .setBuildingProducer(BuildingLumberjack::new)
          .setBuildingViewProducer(() -> BuildingLumberjack.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.LUMBERJACK_ID))
          .addBuildingModuleProducer(FORESTER_WORK)
          .addBuildingModuleProducer(FORESTER_CRAFT)
          .addBuildingModuleProducer(FORESTER_SETTINGS)
          .addBuildingModuleProducer(FORESTER_TOOL)
          .addBuildingModuleProducer(ITEMLIST_SAPLING)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.miner = register(ModBuildings.MINER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutMiner)
          .setBuildingProducer(BuildingMiner::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.MINER_ID))
          .addBuildingModuleProducer(MINER_WORK)
          .addBuildingModuleProducer(QUARRIER_WORK)
          .addBuildingModuleProducer(MINER_CRAFT)
          .addBuildingModuleProducer(MINER_LEVELS)
          .addBuildingModuleProducer(MINER_SETTINGS)
          .addBuildingModuleProducer(MINER_GUARD_ASSIGN)
          .addBuildingModuleProducer(BUILDING_RESOURCES)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.sawmill = register(ModBuildings.SAWMILL_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutSawmill)
          .setBuildingProducer(BuildingSawmill::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.SAWMILL_ID))
          .addBuildingModuleProducer(SAWMILL_WORK)
          .addBuildingModuleProducer(SAWMILL_CRAFT)
          .addBuildingModuleProducer(SAWMILL_DO_CRAFT)
          .addBuildingModuleProducer(SETTINGS_CRAFTER_RECIPE)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.shepherd = register(ModBuildings.SHEPHERD_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutShepherd)
          .setBuildingProducer(BuildingShepherd::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.SHEPHERD_ID))
          .addBuildingModuleProducer(SHEPERD_WORK)
          .addBuildingModuleProducer(SHEPERD_HERDING)
          .addBuildingModuleProducer(SHEPERD_SETTINGS)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.sifter = register(ModBuildings.SIFTER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutSifter)
          .setBuildingProducer(BuildingSifter::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.SIFTER_ID))
          .addBuildingModuleProducer(SIFTER_WORK)
          .addBuildingModuleProducer(SIFTER_CRAFT)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.smeltery = register(ModBuildings.SMELTERY_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutSmeltery)
          .setBuildingProducer(BuildingSmeltery::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.SMELTERY_ID))
          .addBuildingModuleProducer(SMELTER_WORK)
          .addBuildingModuleProducer(SMELTER_SMELTING)
          .addBuildingModuleProducer(SMELTER_OREBREAK)
          .addBuildingModuleProducer(FURNACE)
          .addBuildingModuleProducer(ITEMLIST_FUEL)
          .addBuildingModuleProducer(ITEMLIST_ORE)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(SMELTER_SETTINGS)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.stoneMason = register(ModBuildings.STONE_MASON_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutStonemason)
          .setBuildingProducer(BuildingStonemason::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.STONE_MASON_ID))
          .addBuildingModuleProducer(STONEMASON_WORK)
          .addBuildingModuleProducer(STONEMASON_CRAFT)
          .addBuildingModuleProducer(STONEMASON_DO_CRAFT)
          .addBuildingModuleProducer(SETTINGS_CRAFTER_RECIPE)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.stoneSmelter = register(ModBuildings.STONE_SMELTERY_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutStoneSmeltery)
          .setBuildingProducer(BuildingStoneSmeltery::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.STONE_SMELTERY_ID))
          .addBuildingModuleProducer(STONESMELTER_WORK)
          .addBuildingModuleProducer(STONESMELTER_SMELTING)
          .addBuildingModuleProducer(SETTINGS_CRAFTER_RECIPE)
          .addBuildingModuleProducer(FURNACE)
          .addBuildingModuleProducer(ITEMLIST_FUEL)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.swineHerder = register(ModBuildings.SWINE_HERDER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutSwineHerder)
          .setBuildingProducer(BuildingSwineHerder::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.SWINE_HERDER_ID))
          .addBuildingModuleProducer(SWINEHERDER_WORK)
          .addBuildingModuleProducer(SWINEHERDER_HERDING)
          .addBuildingModuleProducer(SWINEHERDER_SETTINGS)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.townHall = register(ModBuildings.TOWNHALL_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutTownHall)
          .setBuildingProducer(BuildingTownHall::new)
          .setBuildingViewProducer(() -> BuildingTownHall.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.TOWNHALL_ID))
          .addBuildingModuleProducer(TOWNHALL_SETTINGS)
          .createBuildingEntry());

        ModBuildings.wareHouse = register(ModBuildings.WAREHOUSE_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutWareHouse)
          .setBuildingProducer(BuildingWareHouse::new)
          .setBuildingViewProducer(() -> BuildingWareHouse.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.WAREHOUSE_ID))
          .addBuildingModuleProducer(WAREHOUSE_COURIERS)
          .addBuildingModuleProducer(WAREHOUSE_OPTIONS)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(WAREHOUSE_REQUEST_QUEUE)
          .addBuildingModuleProducer(WAREHOUSE_IDLE)
          .createBuildingEntry());

        ModBuildings.postBox = register(ModBuildings.POSTBOX_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockPostBox)
          .setBuildingProducer(PostBox::new)
          .setBuildingViewProducer(() -> PostBox.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.POSTBOX_ID))
          .addBuildingModuleProducer(MIN_STOCK_POSTBOX)
          .createBuildingEntry());

        ModBuildings.florist = register(ModBuildings.FLORIST_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutFlorist)
          .setBuildingProducer(BuildingFlorist::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.FLORIST_ID))
          .addBuildingModuleProducer(FLORIST_WORK)
          .addBuildingModuleProducer(FLORIST_ITEMS)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.enchanter = register(ModBuildings.ENCHANTER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutEnchanter)
          .setBuildingProducer(BuildingEnchanter::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.ENCHANTER_ID))
          .addBuildingModuleProducer(ENCHANTER_WORK)
          .addBuildingModuleProducer(ENCHANTER_CRAFT)
          .addBuildingModuleProducer(ENCHANTER_STATIONS)
          .addBuildingModuleProducer(ENCHANTER_SETTINGS)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.university = register(ModBuildings.UNIVERSITY_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutUniversity)
          .setBuildingProducer(BuildingUniversity::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .addBuildingModuleProducer(UNIVERSITY_WORK)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.UNIVERSITY_ID))
          .addBuildingModuleProducer(UNIVERSITY_RESEARCH)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.hospital = register(ModBuildings.HOSPITAL_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutHospital)
          .setBuildingProducer(BuildingHospital::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.HOSPITAL_ID))
          .addBuildingModuleProducer(HEALER_WORK)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.stash = register(ModBuildings.STASH_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockStash)
          .setBuildingProducer(Stash::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.STASH_ID))
          .createBuildingEntry());

        ModBuildings.school = register(ModBuildings.SCHOOL_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutSchool)
          .setBuildingProducer(BuildingSchool::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.SCHOOL_ID))
          .addBuildingModuleProducer(TEACHER_WORK)
          .addBuildingModuleProducer(PUPIL_WORK)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.glassblower = register(ModBuildings.GLASSBLOWER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutGlassblower)
          .setBuildingProducer(BuildingGlassblower::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.GLASSBLOWER_ID))
          .addBuildingModuleProducer(GLASSBLOWER_WORK)
          .addBuildingModuleProducer(GLASSBLOWER_CRAFT)
          .addBuildingModuleProducer(GLASSBLOWER_DO_CRAFT)
          .addBuildingModuleProducer(GLASSBLOWER_SMELTING)
          .addBuildingModuleProducer(SETTINGS_CRAFTER_RECIPE)
          .addBuildingModuleProducer(FURNACE)
          .addBuildingModuleProducer(ITEMLIST_FUEL)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.dyer = register(ModBuildings.DYER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutDyer)
          .setBuildingProducer(BuildingDyer::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.DYER_ID))
          .addBuildingModuleProducer(DYER_WORK)
          .addBuildingModuleProducer(DYER_CRAFT)
          .addBuildingModuleProducer(DYER_SMELT)
          .addBuildingModuleProducer(SETTINGS_CRAFTER_RECIPE)
          .addBuildingModuleProducer(FURNACE)
          .addBuildingModuleProducer(ITEMLIST_FUEL)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.fletcher = register(ModBuildings.FLETCHER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutFletcher)
          .setBuildingProducer(BuildingFletcher::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.FLETCHER_ID))
          .addBuildingModuleProducer(FLETCHER_WORK)
          .addBuildingModuleProducer(FLETCHER_CRAFT)
          .addBuildingModuleProducer(FLETCHER_DO_CRAFT)
          .addBuildingModuleProducer(SETTINGS_CRAFTER_RECIPE)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.tavern = register(ModBuildings.TAVERN_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutTavern)
          .setBuildingProducer((colony, blockPos) -> new DefaultBuildingInstance(colony, blockPos, "tavern", 3))
          .setBuildingViewProducer(() -> TavernBuildingModule.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.TAVERN_ID))
          .addBuildingModuleProducer(TAVERN_LIVING)
          .addBuildingModuleProducer(TAVERN_VISITOR)
          .addBuildingModuleProducer(BED)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.mechanic = register(ModBuildings.MECHANIC_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutMechanic)
          .setBuildingProducer(BuildingMechanic::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.MECHANIC_ID))
          .addBuildingModuleProducer(MECHANIC_WORK)
          .addBuildingModuleProducer(MECHANIC_CRAFT)
          .addBuildingModuleProducer(MECHANIC_DO_CRAFT)
          .addBuildingModuleProducer(SETTINGS_CRAFTER_RECIPE)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.plantation = register(ModBuildings.PLANTATION_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutPlantation)
          .setBuildingProducer(BuildingPlantation::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.PLANTATION_ID))
          .addBuildingModuleProducer(PLANTATION_WORK)
          .addBuildingModuleProducer(PLANTATION_CRAFT)
          .addBuildingModuleProducer(PLANTATION_FIELDS)
          .addBuildingModuleProducer(PLANTATION_SETTINGS)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.rabbitHutch = register(ModBuildings.RABBIT_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutRabbitHutch)
          .setBuildingProducer(BuildingRabbitHutch::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.RABBIT_ID))
          .addBuildingModuleProducer(RABBITHERDER_WORK)
          .addBuildingModuleProducer(RABBITHERDER_HERDING)
          .addBuildingModuleProducer(RABBITHERDER_SETTINGS)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.concreteMixer = register(ModBuildings.CONCRETE_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutConcreteMixer)
          .setBuildingProducer(BuildingConcreteMixer::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.CONCRETE_ID))
          .addBuildingModuleProducer(CONCRETEMIXER_WORK)
          .addBuildingModuleProducer(CONCRETEMIXER_CRAFT)
          .addBuildingModuleProducer(SETTINGS_CRAFTER_RECIPE)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.beekeeper = register(ModBuildings.BEEKEEPER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutBeekeeper)
          .setBuildingProducer(BuildingBeekeeper::new)
          .setBuildingViewProducer(() -> BuildingBeekeeper.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.BEEKEEPER_ID))
          .addBuildingModuleProducer(BEEKEEPER_WORK)
          .addBuildingModuleProducer(BEEKEEPER_TOOL)
          .addBuildingModuleProducer(BEEKEEPER_HERDING)
          .addBuildingModuleProducer(BEEKEEPER_SETTINGS)
          .addBuildingModuleProducer(ITEMLIST_FLOWER)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.mysticalSite = register(ModBuildings.MYSTICAL_SITE_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutMysticalSite)
          .setBuildingProducer(BuildingMysticalSite::new)
          .setBuildingViewProducer(() -> BuildingMysticalSite.View::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.MYSTICAL_SITE_ID))
          .createBuildingEntry());


        ModBuildings.graveyard = register(ModBuildings.GRAVEYARD_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutGraveyard)
          .setBuildingProducer(BuildingGraveyard::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.GRAVEYARD_ID))
          .addBuildingModuleProducer(GRAVEYARD_WORK)
          .addBuildingModuleProducer(GRAVEYARD)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.netherWorker = register(ModBuildings.NETHERWORKER_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutNetherWorker)
          .setBuildingProducer(BuildingNetherWorker::new)
          .setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.NETHERWORKER_ID))
          .addBuildingModuleProducer(NETHERWORKER_WORK)
          .addBuildingModuleProducer(NETHERWORKER_CRAFT)
          .addBuildingModuleProducer(NETHERWORKER_EXPEDITION)
          .addBuildingModuleProducer(NETHERWORKER_SETTINGS)
          .addBuildingModuleProducer(NETHERMINER_MENU)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(MIN_STOCK)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.simpleQuarry = register(ModBuildings.SIMPLE_QUARRY_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockSimpleQuarry)
          .setBuildingProducer((colony, blockPos) -> new DefaultBuildingInstance(colony, blockPos, ModBuildings.SIMPLE_QUARRY_ID, 1)).setBuildingViewProducer(() -> EmptyView::new)
          .addBuildingModuleProducer(SIMPLE_QUARRY)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.SIMPLE_QUARRY_ID))
          .createBuildingEntry());

        ModBuildings.mediumQuarry = register(ModBuildings.MEDIUM_QUARRY_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockMediumQuarry)
          .setBuildingProducer((colony, blockPos) -> new DefaultBuildingInstance(colony, blockPos, ModBuildings.MEDIUM_QUARRY_ID, 1)).setBuildingViewProducer(() -> EmptyView::new)
          .addBuildingModuleProducer(MEDIUM_QUARRY)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.MEDIUM_QUARRY_ID))
          .createBuildingEntry());

        /*ModBuildings.largeQuarry = register( , () -> new BuildingEntry.Builder()
                                   .setBuildingBlock(ModBlocks.blockLargeQuarry)
                                   .setBuildingProducer((colony, blockPos) -> new DefaultBuildingInstance(colony, blockPos, "largequarry", 1)).setBuildingViewProducer(() -> EmptyView::new)
                                   .addBuildingModuleProducer(QuarryModule::new, () -> MinerAssignmentModuleView::new)
                                   .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.LARGE_QUARRY_ID))
                                   .createBuildingEntry());*/

        ModBuildings.alchemist = register(ModBuildings.ALCHEMIST_ID, () -> new BuildingEntry.Builder()
          .setBuildingBlock(ModBlocks.blockHutAlchemist)
          .setBuildingProducer(BuildingAlchemist::new).setBuildingViewProducer(() -> EmptyView::new)
          .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.ALCHEMIST_ID))
          .addBuildingModuleProducer(ALCHEMIST_WORK)
          .addBuildingModuleProducer(ALCHEMIST_CRAFT)
          .addBuildingModuleProducer(ALCHEMIST_BREW)
          .addBuildingModuleProducer(CRAFT_TASK_VIEW)
          .addBuildingModuleProducer(STATS_MODULE)
          .createBuildingEntry());

        ModBuildings.kitchen = register(ModBuildings.KITCHEN_ID, () -> new BuildingEntry.Builder()
                                                                                           .setBuildingBlock(ModBlocks.blockHutKitchen)
                                                                                           .setBuildingProducer(BuildingKitchen::new)
                                                                                           .setBuildingViewProducer(() -> EmptyView::new)
                                                                                           .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.KITCHEN_ID))
                                                                                           .addBuildingModuleProducer(MIN_STOCK)
                                                                                           .addBuildingModuleProducer(CRAFT_TASK_VIEW)
                                                                                           .addBuildingModuleProducer(CHEF_WORK)
                                                                                           .addBuildingModuleProducer(CHEF_CRAFT)
                                                                                           .addBuildingModuleProducer(CHEF_SMELT)
                                                                                           .addBuildingModuleProducer(FURNACE)
                                                                                           .addBuildingModuleProducer(ITEMLIST_FUEL)
                                                                                           .addBuildingModuleProducer(STATS_MODULE)
                                                                                           .createBuildingEntry());

        ModBuildings.gateHouse = register(ModBuildings.GATE_HOUSE_ID, () -> new BuildingEntry.Builder()
            .setBuildingBlock(ModBlocks.blockHutGateHouse)
            .setBuildingProducer(BuildingGateHouse::new)
            .setBuildingViewProducer(() -> BuildingGateHouse.View::new)
            .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, ModBuildings.GATE_HOUSE_ID))
            .addBuildingModuleProducer(KNIGHT_GATE_WORK)
            .addBuildingModuleProducer(RANGER_GATE_WORK)
            .addBuildingModuleProducer(GUARD_ENTITY_LIST)
            .addBuildingModuleProducer(GATE_GUARD_SETTINGS)
            .addBuildingModuleProducer(MIN_STOCK)
            .addBuildingModuleProducer(BED)
            .addBuildingModuleProducer(STATS_MODULE)
            .addBuildingModuleProducer(CONNECTION_MODULE)
            .createBuildingEntry());
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends BuildingEntry> Supplier<T> register(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.BUILDING_REGISTRY,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1); calling this from
     * the mod entry point is what pins the moment it happens.
     */
    public static void init()
    {
    }
}
