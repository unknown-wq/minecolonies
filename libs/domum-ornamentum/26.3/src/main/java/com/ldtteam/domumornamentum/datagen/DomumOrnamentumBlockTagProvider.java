package com.ldtteam.domumornamentum.datagen;

import com.ldtteam.domumornamentum.datagen.allbrick.AllBrickBlockTagProvider;
import com.ldtteam.domumornamentum.datagen.bricks.BrickBlockTagProvider;
import com.ldtteam.domumornamentum.datagen.door.DoorsCompatibilityTagProvider;
import com.ldtteam.domumornamentum.datagen.door.DoorsComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.door.fancy.FancyDoorsCompatibilityTagProvider;
import com.ldtteam.domumornamentum.datagen.door.fancy.FancyDoorsComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.extra.ExtraBlockTagProvider;
import com.ldtteam.domumornamentum.datagen.fence.FenceCompatibilityTagProvider;
import com.ldtteam.domumornamentum.datagen.fence.FenceComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.fencegate.FenceGateCompatibilityTagProvider;
import com.ldtteam.domumornamentum.datagen.fencegate.FenceGateComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.floatingcarpet.FloatingCarpetBlockTagProvider;
import com.ldtteam.domumornamentum.datagen.frames.light.FramedLightComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.frames.timber.TimberFramesComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.global.GlobalTagProvider;
import com.ldtteam.domumornamentum.datagen.pillar.PillarComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.post.PostComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.shingle.normal.ShinglesComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.shingle.slab.ShingleSlabComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.slab.SlabCompatibilityTagProvider;
import com.ldtteam.domumornamentum.datagen.slab.SlabComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.stair.StairsCompatibilityTagProvider;
import com.ldtteam.domumornamentum.datagen.stair.StairsComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.trapdoor.TrapdoorsCompatibilityTagProvider;
import com.ldtteam.domumornamentum.datagen.trapdoor.TrapdoorsComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.trapdoor.fancy.FancyTrapdoorsCompatibilityTagProvider;
import com.ldtteam.domumornamentum.datagen.trapdoor.fancy.FancyTrapdoorsComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.utils.BlockTagAppender;
import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.datagen.wall.paper.PaperwallComponentTagProvider;
import com.ldtteam.domumornamentum.datagen.wall.vanilla.WallCompatibilityTagProvider;
import com.ldtteam.domumornamentum.datagen.wall.vanilla.WallComponentTagProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Single block tag provider that runs all 30 domain sub providers.
 *
 * <p>Port note (26.2 / Fabric): NeoForge's {@code ExistingFileHelper} let several {@code BlockTagsProvider}s
 * contribute to the same tag file — {@code minecraft:mineable/pickaxe}, {@code minecraft:stairs} and
 * {@code minecraft:wooden_doors} are each written by two different DO providers. Fabric has no merging, so a second
 * provider would simply overwrite the first's file. Running everything inside one provider restores the union,
 * because {@code TagsProvider#builder} hands back the same {@code TagBuilder} for a repeated {@link TagKey}.</p>
 */
public class DomumOrnamentumBlockTagProvider extends FabricTagsProvider.BlockTagsProvider {

    private static final List<IBlockTagSubProvider> SUB_PROVIDERS = List.of(
            new GlobalTagProvider(),
            new AllBrickBlockTagProvider(),
            new BrickBlockTagProvider(),
            new DoorsCompatibilityTagProvider(),
            new DoorsComponentTagProvider(),
            new FancyDoorsCompatibilityTagProvider(),
            new FancyDoorsComponentTagProvider(),
            new ExtraBlockTagProvider(),
            new FenceCompatibilityTagProvider(),
            new FenceComponentTagProvider(),
            new FenceGateCompatibilityTagProvider(),
            new FenceGateComponentTagProvider(),
            new FloatingCarpetBlockTagProvider(),
            new FramedLightComponentTagProvider(),
            new TimberFramesComponentTagProvider(),
            new PillarComponentTagProvider(),
            new PostComponentTagProvider(),
            new ShinglesComponentTagProvider(),
            new ShingleSlabComponentTagProvider(),
            new SlabCompatibilityTagProvider(),
            new SlabComponentTagProvider(),
            new StairsCompatibilityTagProvider(),
            new StairsComponentTagProvider(),
            new TrapdoorsCompatibilityTagProvider(),
            new TrapdoorsComponentTagProvider(),
            new FancyTrapdoorsCompatibilityTagProvider(),
            new FancyTrapdoorsComponentTagProvider(),
            new PaperwallComponentTagProvider(),
            new WallCompatibilityTagProvider(),
            new WallComponentTagProvider());

    public DomumOrnamentumBlockTagProvider(final FabricPackOutput output,
                                           final CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(final HolderLookup.Provider registries) {
        // TagsProvider already declares tag(TagKey<T>) with a different return type, so the sub provider sink is a
        // separate (functional) object rather than this provider itself.
        final IBlockTagSubProvider.Sink sink = tag -> new BlockTagAppender(builder(tag));
        for (final IBlockTagSubProvider subProvider : SUB_PROVIDERS) {
            subProvider.addTags(sink);
        }
    }

    @Override
    @NotNull
    public String getName() {
        return "Domum Ornamentum Block Tags";
    }
}
