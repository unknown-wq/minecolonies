package com.minecolonies.core.generation.defaults;

import com.minecolonies.api.items.ModTags;
import com.minecolonies.core.generation.ModTagAppender;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Port note (26.2 / Fabric): NeoForge's {@code BiomeTagsProvider} is gone -- vanilla's own
 * {@code net.minecraft.data.tags.BiomeTagsProvider} hardcodes the vanilla tag set, so this extends the generic
 * {@link FabricTagsProvider} over {@code Registries.BIOME} instead.  {@code Tags.Biomes.*} became
 * fabric-convention-tags' {@code ConventionalBiomeTags.*} (same {@code c:} ids), and the appender shape is
 * restored by {@link ModTagAppender}.
 */
@SuppressWarnings({"ConstantConditions", "unchecked"})
public class DefaultBiomeTagsProvider extends FabricTagsProvider<Biome>
{

    public DefaultBiomeTagsProvider(
      final FabricPackOutput output,
      final CompletableFuture<HolderLookup.Provider> lookupProvider)
    {
        super(output, Registries.BIOME, lookupProvider);
    }

    private ModTagAppender<Biome> tagOf(@NotNull final TagKey<Biome> key)
    {
        return ModTagAppender.byKey(builder(key), getOrCreateRawBuilder(key));
    }

    @NotNull
    @Override
    public String getName()
    {
        return "MineColonies Biome Tags";
    }

    @Override
    protected void addTags(final HolderLookup.Provider holder)
    {
        tagOf(ModTags.coldBiomes)
          .addTags(BiomeTags.IS_TAIGA)
          .addTags(BiomeTags.SPAWNS_SNOW_FOXES)
          .addTags(BiomeTags.POLAR_BEARS_SPAWN_ON_ALTERNATE_BLOCKS)
          .addTags(ConventionalBiomeTags.IS_COLD)
          .addTags(BiomeTags.IS_END)
          .addTags(ConventionalBiomeTags.IS_SNOWY)
          .addKeys(Biomes.COLD_OCEAN,
            Biomes.DEEP_COLD_OCEAN,
            Biomes.DEEP_FROZEN_OCEAN,
            Biomes.DEEP_DARK,
            Biomes.FROZEN_OCEAN,
            Biomes.DEEP_FROZEN_OCEAN,
            Biomes.FROZEN_RIVER,
            Biomes.FROZEN_PEAKS,
            Biomes.GROVE,
            Biomes.ICE_SPIKES,
            Biomes.JAGGED_PEAKS,
            Biomes.OLD_GROWTH_PINE_TAIGA,
            Biomes.OLD_GROWTH_SPRUCE_TAIGA,
            Biomes.SNOWY_BEACH,
            Biomes.SNOWY_PLAINS,
            Biomes.SNOWY_TAIGA,
            Biomes.SNOWY_SLOPES,
            Biomes.STONY_PEAKS,
            Biomes.STONY_SHORE,
            Biomes.TAIGA,
            Biomes.WINDSWEPT_FOREST,
            Biomes.WINDSWEPT_HILLS,
            Biomes.WINDSWEPT_GRAVELLY_HILLS);

        tagOf(ModTags.temperateBiomes)
          .addTags(BiomeTags.HAS_VILLAGE_PLAINS)
          .addTags(ConventionalBiomeTags.IS_PLAINS)
          .addTags(ConventionalBiomeTags.IS_SWAMP)
          .remove(ConventionalBiomeTags.IS_COLD, ConventionalBiomeTags.IS_DRY, ConventionalBiomeTags.IS_DESERT)
          .addKeys(Biomes.BIRCH_FOREST,
            Biomes.CHERRY_GROVE,
            Biomes.DARK_FOREST,
            Biomes.DEEP_LUKEWARM_OCEAN,
            Biomes.DEEP_OCEAN,
            Biomes.FLOWER_FOREST,
            Biomes.FOREST,
            Biomes.LUKEWARM_OCEAN,
            Biomes.MEADOW,
            Biomes.MUSHROOM_FIELDS,
            Biomes.OLD_GROWTH_BIRCH_FOREST,
            Biomes.PLAINS,
            Biomes.RIVER,
            Biomes.SUNFLOWER_PLAINS,
            Biomes.SWAMP);

        tagOf(ModTags.humidBiomes)
          .addTags(BiomeTags.IS_JUNGLE)
          .addTags(ConventionalBiomeTags.IS_WET_OVERWORLD)
          .addKeys(Biomes.BAMBOO_JUNGLE,
            Biomes.BEACH,
            Biomes.RIVER,
            Biomes.WARM_OCEAN,
            Biomes.DRIPSTONE_CAVES,
            Biomes.JUNGLE,
            Biomes.LUSH_CAVES,
            Biomes.MANGROVE_SWAMP,
            Biomes.SPARSE_JUNGLE);

        tagOf(ModTags.dryBiomes)
          .addTags(BiomeTags.HAS_DESERT_PYRAMID)
          .addTags(ConventionalBiomeTags.IS_DESERT)
          .addTags(ConventionalBiomeTags.IS_DRY)
          .addTags(BiomeTags.IS_SAVANNA)
          .addTags(BiomeTags.IS_NETHER)
          .addKeys(Biomes.BADLANDS,
            Biomes.DESERT,
            Biomes.ERODED_BADLANDS,
            Biomes.SAVANNA,
            Biomes.SAVANNA_PLATEAU,
            Biomes.WINDSWEPT_SAVANNA);
    }
}
