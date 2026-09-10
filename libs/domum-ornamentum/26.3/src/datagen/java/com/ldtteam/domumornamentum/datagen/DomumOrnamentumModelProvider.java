package com.ldtteam.domumornamentum.datagen;

import com.ldtteam.domumornamentum.datagen.allbrick.AllBrickBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.allbrick.AllBrickStairBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.bricks.BrickBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.door.DoorsBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.door.fancy.FancyDoorsBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.extra.ExtraBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.fence.FenceBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.fencegate.FenceGateBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.floatingcarpet.FloatingCarpetBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.frames.dynamic.DynamicTimberFramesBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.frames.light.FramedLightBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.frames.timber.TimberFramesBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.panel.PanelBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.pillar.PillarBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.post.PostBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.shingle.normal.ShinglesBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.shingle.slab.ShingleSlabBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.slab.SlabBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.stair.StairsBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.trapdoor.TrapdoorsBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.trapdoor.fancy.FancyTrapdoorsBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import com.ldtteam.domumornamentum.datagen.wall.paper.PaperwallBlockStateProvider;
import com.ldtteam.domumornamentum.datagen.wall.vanilla.WallBlockStateProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The single model provider, running all 23 former {@code BlockStateProvider}s as sub providers.
 *
 * <p>Port notes (26.2 / Fabric):</p>
 * <ul>
 *   <li>NeoForge's {@code BlockStateProvider} does not exist. {@link FabricModelProvider} extends vanilla's
 *       {@code ModelProvider} and hands out {@link BlockModelGenerators} (blockstates + block models + item model
 *       definitions) and {@link ItemModelGenerators} (plain items). The three sinks
 *       {@code blockStateOutput} / {@code itemModelOutput} / {@code modelOutput} are {@code public final} fields in
 *       26.2 ({@code /opt/mc-src/net/minecraft/client/data/models/BlockModelGenerators.java:131,135,139}) — the
 *       access widener that {@code /workspace/desolation} needed for 26.1 is no longer required.</li>
 *   <li>Fabric mixes into {@code ModelProvider} so vanilla's own {@code BlockModelGenerators#run()} is replaced by
 *       {@link #generateBlockStateModels}, and the "missing definition" validation is restricted to this mod's
 *       namespace and only runs under strict validation.</li>
 *   <li>Domum Ornamentum has no non-block items, so {@link #generateItemModels} is empty.</li>
 * </ul>
 */
public class DomumOrnamentumModelProvider extends FabricModelProvider {

    private static final List<IBlockStateSubProvider> SUB_PROVIDERS = List.of(
            new AllBrickBlockStateProvider(),
            new AllBrickStairBlockStateProvider(),
            new BrickBlockStateProvider(),
            new DoorsBlockStateProvider(),
            new FancyDoorsBlockStateProvider(),
            new ExtraBlockStateProvider(),
            new FenceBlockStateProvider(),
            new FenceGateBlockStateProvider(),
            new FloatingCarpetBlockStateProvider(),
            new DynamicTimberFramesBlockStateProvider(),
            new FramedLightBlockStateProvider(),
            new TimberFramesBlockStateProvider(),
            new PanelBlockStateProvider(),
            new PillarBlockStateProvider(),
            new PostBlockStateProvider(),
            new ShinglesBlockStateProvider(),
            new ShingleSlabBlockStateProvider(),
            new SlabBlockStateProvider(),
            new StairsBlockStateProvider(),
            new TrapdoorsBlockStateProvider(),
            new FancyTrapdoorsBlockStateProvider(),
            new PaperwallBlockStateProvider(),
            new WallBlockStateProvider());

    public DomumOrnamentumModelProvider(final FabricPackOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(final BlockModelGenerators generator) {
        final ModelCollector models = new ModelCollector(generator);
        for (final IBlockStateSubProvider subProvider : SUB_PROVIDERS) {
            subProvider.generate(models);
        }
        generateHandWrittenBlockItems(models);
    }

    /**
     * The three blocks whose blockstate and models are hand written in {@code src/main/resources} and therefore have
     * no sub provider: the cutter and the two barrels.
     *
     * <p>They still need the 1.21.4+ {@code assets/domum_ornamentum/items/&lt;name&gt;.json} dispatch file, and
     * vanilla's auto-fill does not reach them — Fabric's {@code ModelProviderItemInfoCollectorMixin} only auto-fills
     * block items whose block was processed by this provider. Without it they render as the missing model (the
     * black-and-magenta cube). The models they point at stay hand written.</p>
     */
    private static void generateHandWrittenBlockItems(final ModelCollector models) {
        for (final Block block : new Block[] {
                ModBlocks.getInstance().getArchitectsCutter(),
                ModBlocks.getInstance().getStandingBarrel(),
                ModBlocks.getInstance().getLayingBarrel() }) {
            models.itemModelReference(block,
                    ModelCollector.modLoc("item/" + ModelCollector.registryPath(block)));
        }
    }

    @Override
    public void generateItemModels(final ItemModelGenerators generator) {
        // Domum Ornamentum registers block items only; their models are emitted from generateBlockStateModels.
    }

    @Override
    @NotNull
    public String getName() {
        return "Domum Ornamentum Models";
    }
}
