package com.ldtteam.structurize.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * The {@code fabric-datagen} entrypoint — replaces the NeoForge {@code GatherDataEvent} handler that used to
 * live in {@code event/LifecycleSubscriber#onDatagen}.
 *
 * <p>Wired up through {@code fabric.mod.json} and {@code fabricApi.configureDataGeneration { client = true }}
 * in {@code build.gradle}.</p>
 */
public class StructurizeDataGenerator implements DataGeneratorEntrypoint
{
    @Override
    public void onInitializeDataGenerator(final FabricDataGenerator generator)
    {
        final FabricDataGenerator.Pack pack = generator.createPack();

        pack.addProvider(BlockTagProvider::new);
        pack.addProvider(BlockEntityTagProvider::new);
        pack.addProvider(EntityTagProvider::new);
    }
}
