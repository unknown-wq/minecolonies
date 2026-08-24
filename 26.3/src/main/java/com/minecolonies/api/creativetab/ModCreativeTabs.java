package com.minecolonies.api.creativetab;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.AbstractColonyBlock;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;
import java.util.function.Supplier;

/**
 * Class used to handle the creativeTab of minecolonies.
 */
public final class ModCreativeTabs
{
    public static final Supplier<CreativeModeTab> HUTS = register("mchuts", new CreativeModeTab.Builder(CreativeModeTab.Row.TOP, 1)
                                                                                                      .icon(() -> new ItemStack(ModBlocks.blockHutTownHall))
                                                                                                      .title(Component.translatable("com.minecolonies.creativetab.huts")).displayItems((config, output) -> {
          for (final AbstractColonyBlock<?> hut : ModBlocks.getHuts())
          {
              output.accept(hut);
          }
      }).build());

    public static final Supplier<CreativeModeTab> GENERAL = register("mcgeneral", new CreativeModeTab.Builder(CreativeModeTab.Row.TOP, 1)
                                                                                                      .icon(() -> new ItemStack(ModBlocks.blockRack))
                                                                                                      .title(Component.translatable("com.minecolonies.creativetab.general")).displayItems((config, output) -> {
          output.accept(ModBlocks.blockScarecrow);
          output.accept(ModBlocks.blockPlantationField);
          output.accept(ModBlocks.blockRack);
          output.accept(ModBlocks.blockGrave);
          output.accept(ModBlocks.blockNamedGrave);
          output.accept(ModBlocks.blockWayPoint);
          output.accept(ModBlocks.blockBarrel);
          output.accept(ModBlocks.blockDecorationPlaceholder);
          output.accept(ModBlocks.blockCompostedDirt);
          output.accept(ModBlocks.blockConstructionTape);
          output.accept(ModBlocks.blockColonySign);

          output.accept(ModItems.scepterLumberjack);
          output.accept(ModItems.permTool);
          output.accept(ModItems.scepterGuard);
          output.accept(ModItems.scepterClaim);
          output.accept(ModItems.scepterUnclaim);
          output.accept(ModItems.scepterBorder);
          output.accept(ModItems.scepterTerritory);
            output.accept(ModItems.assistantHammer_Gold);
            output.accept(ModItems.assistantHammer_Iron);
            output.accept(ModItems.assistantHammer_Diamond);
          output.accept(ModItems.scepterBeekeeper);
          output.accept(ModItems.fieldStick);

          output.accept(ModItems.bannerRallyGuards);

          output.accept(ModItems.supplyChest);
          output.accept(ModItems.supplyCamp);

          output.accept(ModItems.clipboard);
          output.accept(ModItems.resourceScroll);
          output.accept(ModItems.compost);
          output.accept(ModItems.mistletoe);
          output.accept(ModItems.magicpotion);
          output.accept(ModItems.buildGoggles);
          output.accept(ModItems.scanAnalyzer);
          output.accept(ModItems.questLog);
          output.accept(ModItems.colonyMap);

          output.accept(ModItems.scrollColonyTP);
          output.accept(ModItems.scrollColonyAreaTP);
          output.accept(ModItems.scrollBuff);
          output.accept(ModItems.scrollGuardHelp);
          output.accept(ModItems.scrollHighLight);

          output.accept(ModItems.santaHat);

          output.accept(ModItems.irongate);
          output.accept(ModItems.woodgate);

          output.accept(ModItems.flagBanner);

          output.accept(ModItems.ancientTome);
          output.accept(ModItems.chiefSword);
          output.accept(ModItems.scimitar);
          output.accept(ModItems.pharaoscepter);
          output.accept(ModItems.firearrow);
          output.accept(ModItems.spear);
          output.accept(ModItems.pirateHelmet_1);
          output.accept(ModItems.pirateChest_1);
          output.accept(ModItems.pirateLegs_1);
          output.accept(ModItems.pirateBoots_1);

          output.accept(ModItems.pirateHelmet_2);
          output.accept(ModItems.pirateChest_2);
          output.accept(ModItems.pirateLegs_2);
          output.accept(ModItems.pirateBoots_2);

          output.accept(ModItems.plateArmorHelmet);
          output.accept(ModItems.plateArmorChest);
          output.accept(ModItems.plateArmorLegs);
          output.accept(ModItems.plateArmorBoots);

          output.accept(ModItems.sifterMeshString);
          output.accept(ModItems.sifterMeshFlint);
          output.accept(ModItems.sifterMeshIron);
          output.accept(ModItems.sifterMeshDiamond);

          output.accept(ModItems.breadDough);
          output.accept(ModItems.cookieDough);
          output.accept(ModItems.cakeBatter);
          output.accept(ModItems.rawPumpkinPie);

          output.accept(ModItems.milkyBread);
          output.accept(ModItems.sugaryBread);
          output.accept(ModItems.goldenBread);
          output.accept(ModItems.chorusBread);

          // 26.2: SpawnEggItem#byId returns Optional<Holder<Item>>.
          if (SpawnEggItem.byId(ModEntities.CAMP_BARBARIAN).isPresent())
          {
                SpawnEggItem.byId(ModEntities.CAMP_BARBARIAN).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_ARCHERBARBARIAN).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_CHIEFBARBARIAN).ifPresent(egg -> output.accept(egg.value()));

                SpawnEggItem.byId(ModEntities.CAMP_PIRATE).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_ARCHERPIRATE).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_CHIEFPIRATE).ifPresent(egg -> output.accept(egg.value()));

                SpawnEggItem.byId(ModEntities.CAMP_MUMMY).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_ARCHERMUMMY).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_PHARAO).ifPresent(egg -> output.accept(egg.value()));

                SpawnEggItem.byId(ModEntities.CAMP_SHIELDMAIDEN).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_NORSEMEN_ARCHER).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_NORSEMEN_CHIEF).ifPresent(egg -> output.accept(egg.value()));

                SpawnEggItem.byId(ModEntities.CAMP_AMAZON).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_AMAZONSPEARMAN).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_AMAZONCHIEF).ifPresent(egg -> output.accept(egg.value()));

                SpawnEggItem.byId(ModEntities.CAMP_DROWNED_PIRATE).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_DROWNED_ARCHERPIRATE).ifPresent(egg -> output.accept(egg.value()));
                SpawnEggItem.byId(ModEntities.CAMP_DROWNED_CHIEFPIRATE).ifPresent(egg -> output.accept(egg.value()));
          }

      }).build());

    public static final Supplier<CreativeModeTab> FOOD = register("mcfood", new CreativeModeTab.Builder(CreativeModeTab.Row.TOP, 1)
                                                                                                      .icon(() -> new ItemStack(ModBlocks.blockTomato))
                                                                                                      .title(Component.translatable("com.minecolonies.creativetab.food")).displayItems((config, output) -> {
          output.accept(ModBlocks.farmland);
          output.accept(ModBlocks.floodedFarmland);

          for (final Block crop : ModBlocks.getCrops())
          {
              output.accept(crop);
          }

          // bottles
          output.accept(ModItems.large_empty_bottle);
          output.accept(ModItems.large_water_bottle);
          output.accept(ModItems.large_milk_bottle);
          output.accept(ModItems.large_soy_milk_bottle);

          for (final Item food : ModItems.getAllIngredients())
          {
              output.accept(food);
          }

          for (final Item food : ModItems.getAllFoods())
          {
              output.accept(food);
          }
      }).build());

    /**
     * Register one creative tab eagerly (contract C1: the field stays a {@link Supplier}).
     *
     * @param name the tab path.
     * @param tab  the built tab.
     * @return a supplier of the registered tab.
     */
    private static Supplier<CreativeModeTab> register(final String name, final CreativeModeTab tab)
    {
        final CreativeModeTab value = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, name), tab);
        return () -> value;
    }

    /**
     * Class-load hook — registration happens eagerly in the static initialisers above (contract C1).
     */
    public static void init()
    {
    }

    /**
     * Private constructor to hide the implicit one.
     */
    private ModCreativeTabs()
    {
        /*
         * Intentionally left empty.
         */
    }
}
