package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries.BuildingExtensionEntry;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildingextensions.PlantationField;
import com.minecolonies.core.colony.buildings.workerbuildings.plantation.modules.specific.*;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.Items;

import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.SchematicTagConstants.*;
import net.minecraft.core.Registry;
import java.util.function.Supplier;

public final class ModBuildingExtensionsInitializer
{
    static
    {
        BuildingExtensionRegistries.farmField = createEntry(BuildingExtensionRegistries.FARM_FIELD_ID, builder -> builder.setExtensionProducer(FarmField::new));

        BuildingExtensionRegistries.plantationSugarCaneField = createEntry(BuildingExtensionRegistries.PLANTATION_SUGAR_CANE_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new)
                .addExtensionModuleProducer(field -> new SugarCanePlantModule(field, SUGAR_FIELD, SUGAR_CROP, Items.SUGAR_CANE)));

        BuildingExtensionRegistries.plantationCactusField = createEntry(BuildingExtensionRegistries.PLANTATION_CACTUS_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new)
                .addExtensionModuleProducer(field -> new CactusPlantModule(field, CACTUS_FIELD, CACTUS_CROP, Items.CACTUS)));

        BuildingExtensionRegistries.plantationBambooField = createEntry(BuildingExtensionRegistries.PLANTATION_BAMBOO_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new)
                .addExtensionModuleProducer(field -> new BambooPlantModule(field, BAMBOO_FIELD, BAMBOO_CROP, Items.BAMBOO)));

        BuildingExtensionRegistries.plantationCocoaBeansField = createEntry(BuildingExtensionRegistries.PLANTATION_COCOA_BEANS_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new)
                .addExtensionModuleProducer(field -> new CocoaPlantModule(field, COCOA_FIELD, COCOA_CROP, Items.COCOA_BEANS)));

        BuildingExtensionRegistries.plantationVinesField = createEntry(BuildingExtensionRegistries.PLANTATION_VINES_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new).addExtensionModuleProducer(field -> new VinePlantModule(field, VINE_FIELD, VINE_CROP, Items.VINE)));

        BuildingExtensionRegistries.plantationKelpField = createEntry(BuildingExtensionRegistries.PLANTATION_KELP_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new).addExtensionModuleProducer(field -> new KelpPlantModule(field, KELP_FIELD, KELP_CROP, Items.KELP)));

        BuildingExtensionRegistries.plantationSeagrassField = createEntry(BuildingExtensionRegistries.PLANTATION_SEAGRASS_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new)
                .addExtensionModuleProducer(field -> new SeagrassPlantModule(field, SEA_GRASS_FIELD, SEA_GRASS_CROP, Items.SEAGRASS)));

        BuildingExtensionRegistries.plantationSeaPicklesField = createEntry(BuildingExtensionRegistries.PLANTATION_SEA_PICKLES_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new)
                .addExtensionModuleProducer(field -> new SeapicklePlantModule(field, SEA_PICKLE_FIELD, SEA_PICKLE_CROP, Items.SEA_PICKLE)));

        BuildingExtensionRegistries.plantationGlowberriesField = createEntry(BuildingExtensionRegistries.PLANTATION_GLOWBERRIES_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new)
                .addExtensionModuleProducer(field -> new GlowBerriesPlantModule(field, GLOW_BERRY_FIELD, GLOW_BERRY_CROP, Items.GLOW_BERRIES)));

        BuildingExtensionRegistries.plantationWeepingVinesField = createEntry(BuildingExtensionRegistries.PLANTATION_WEEPING_VINES_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new)
                .addExtensionModuleProducer(field -> new WeepingVinesPlantModule(field, WEEPY_VINE_FIELD, WEEPY_VINE_CROP, Items.WEEPING_VINES)));

        BuildingExtensionRegistries.plantationTwistingVinesField = createEntry(BuildingExtensionRegistries.PLANTATION_TWISTING_VINES_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new)
                .addExtensionModuleProducer(field -> new TwistingVinesPlantModule(field, TWISTY_VINE_FIELD, TWISTY_VINE_CROP, Items.TWISTING_VINES)));

        BuildingExtensionRegistries.plantationCrimsonPlantsField = createEntry(BuildingExtensionRegistries.PLANTATION_CRIMSON_PLANTS_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new)
                .addExtensionModuleProducer(field -> new CrimsonPlantsPlantModule(field, CRIMSON_FIELD, CRIMSON_CROP, Items.CRIMSON_FUNGUS)));

        BuildingExtensionRegistries.plantationWarpedPlantsField = createEntry(BuildingExtensionRegistries.PLANTATION_WARPED_PLANTS_FIELD_ID,
            builder -> builder.setExtensionProducer(PlantationField::new)
                .addExtensionModuleProducer(field -> new WarpedPlantsPlantModule(field, WARPED_FIELD, WARPED_CROP, Items.WARPED_FUNGUS)));
    }
    private ModBuildingExtensionsInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModBuildingExtensionsInitializer but this is a Utility class.");
    }

    private static Supplier<BuildingExtensionEntry> createEntry(Identifier registryName, Consumer<BuildingExtensionEntry.Builder> builder)
    {
        BuildingExtensionEntry.Builder field = new BuildingExtensionEntry.Builder().setRegistryName(registryName);
        builder.accept(field);
        return register(registryName.getPath(), field::createExtensionEntry);
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends BuildingExtensionEntry> Supplier<T> register(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.BUILDING_EXTENSION_REGISTRY,
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
