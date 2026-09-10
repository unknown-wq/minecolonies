package com.ldtteam.domumornamentum.datagen;

import com.ldtteam.domumornamentum.datagen.bricks.BrickRecipeProvider;
import com.ldtteam.domumornamentum.datagen.extra.ExtraRecipeProvider;
import com.ldtteam.domumornamentum.datagen.floatingcarpet.FloatingCarpetRecipeProvider;
import com.ldtteam.domumornamentum.datagen.global.GlobalLanguageProvider;
import com.ldtteam.domumornamentum.datagen.global.GlobalLootTableProvider;
import com.ldtteam.domumornamentum.datagen.global.GlobalRecipeProvider;
import com.ldtteam.domumornamentum.datagen.global.MateriallyTexturedBlockRecipeProvider;
import com.ldtteam.domumornamentum.datagen.loot.MaterialLootTableProvider;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * The {@code fabric-datagen} entrypoint — the Fabric replacement for the NeoForge
 * {@code GatherDataEvent} handler that used to live in
 * {@code com.ldtteam.domumornamentum.event.handlers.ModBusEventHandler#dataGeneratorSetup}.
 *
 * <p>Wired up through {@code fabric.mod.json} and {@code fabricApi.configureDataGeneration { client = true }} in
 * {@code build.gradle}. Shape copied from
 * {@code /workspace/desolation/src/main/java/raltsmc/desolation/data/DesolationDatagen.java:9-20}.</p>
 *
 * <p>The 26.1 tree had 23 standalone {@code BlockStateProvider}s, 30 {@code BlockTagsProvider}s and 2
 * {@code ItemTagsProvider}s. Fabric has no equivalent of NeoForge's {@code ExistingFileHelper} merge, so each of
 * those groups was folded into a single aggregate provider — see {@link DomumOrnamentumModelProvider},
 * {@link DomumOrnamentumBlockTagProvider} and {@link DomumOrnamentumItemTagProvider}. The 23 language sub providers
 * were already aggregated upstream by {@link GlobalLanguageProvider}.</p>
 */
public class DomumOrnamentumDataGenerator implements DataGeneratorEntrypoint
{
    @Override
    public void onInitializeDataGenerator(final FabricDataGenerator generator)
    {
        final FabricDataGenerator.Pack pack = generator.createPack();

        // assets/: blockstates, block models, item models and 1.21.4+ item model definitions
        pack.addProvider(DomumOrnamentumModelProvider::new);

        // assets/<ns>/lang/en_us.json
        pack.addProvider(GlobalLanguageProvider::new);

        // data/<ns>/tags/{block,item}/ and data/minecraft/tags/block/
        final DomumOrnamentumBlockTagProvider blockTags = pack.addProvider(DomumOrnamentumBlockTagProvider::new);
        pack.addProvider((output, registries) -> new DomumOrnamentumItemTagProvider(output, registries, blockTags));

        // data/<ns>/loot_table/blocks/
        pack.addProvider(GlobalLootTableProvider::new);
        pack.addProvider(MaterialLootTableProvider::new);

        // data/<ns>/recipe/ and data/<ns>/advancement/recipes/
        pack.addProvider(GlobalRecipeProvider::new);
        pack.addProvider(BrickRecipeProvider::new);
        pack.addProvider(ExtraRecipeProvider::new);
        pack.addProvider(FloatingCarpetRecipeProvider::new);
        pack.addProvider(MateriallyTexturedBlockRecipeProvider::new);
    }
}
