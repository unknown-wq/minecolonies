package com.ldtteam.domumornamentum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * TODO(port-26.2): DISABLED — the immediate-mode buffer path this renderer was built on does not exist in
 * 26.2, and every model API it called was removed.
 *
 * <p>What broke, verified against {@code /opt/mc-src}:
 * <ul>
 *   <li>{@code BakedModel}, {@code ModelData}, {@code ChunkRenderTypeSet}, {@code BakedModel#getRenderPasses},
 *       {@code BlockRenderDispatcher#getBlockModelShaper} - all gone. The block-side model interface is
 *       {@code net.minecraft.client.renderer.block.dispatch.BlockStateModel} and quads are no longer fetched
 *       with {@code getQuads(state, dir, random, modelData, renderType)}.</li>
 *   <li>{@code BakedQuad#getVertices()} returning {@code int[]} - gone. {@code BakedQuad} is a record of four
 *       {@code Vector3fc} positions and four {@code long} packed UVs
 *       ({@code /opt/mc-src/net/minecraft/client/resources/model/geometry/BakedQuad.java}), so
 *       {@code putColoredBulkData}'s {@code IntBuffer} hackery has no input.</li>
 *   <li>{@code ItemRenderer#renderQuadList} and the whole {@code net.minecraft.client.renderer.entity
 *       .ItemRenderer} class - gone.</li>
 *   <li>{@code RenderType#draw(MeshData)} - gone. Level rendering is submit-to-queue
 *       ({@code SubmitNodeCollector}); there is no uploader for a hand-built {@code BufferBuilder} any more
 *       ({@code Tesselator} / {@code BufferUploader} are also gone).</li>
 *   <li>{@code net.neoforged.neoforge.client.event.RenderLevelStageEvent}, which drove this renderer, has no
 *       NeoForge-shaped equivalent.</li>
 * </ul>
 *
 * <p>Consequence in game: no translucent blue "ghost" preview of the block about to be placed. Placement
 * itself, the block outline, and all server-side behaviour are unaffected - this was purely cosmetic.
 *
 * <p>How to fix:
 * <ol>
 *   <li>Hook {@code net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.AfterTranslucentTerrain}
 *       (or {@code LevelExtractionEvents}) from {@code fabric-rendering-v1} instead of
 *       {@code RenderLevelStageEvent}.</li>
 *   <li>Get the placement state exactly as this class did (that part still compiles: {@code BlockPlaceContext},
 *       {@code DataComponents.BLOCK_STATE}, {@code BlockItemStateProperties#apply}).</li>
 *   <li>Submit the geometry with {@code OrderedSubmitNodeCollector#submitBlockModel(...)} instead of building
 *       a buffer by hand - see the overload list in
 *       {@code /opt/mc-src/net/minecraft/client/renderer/OrderedSubmitNodeCollector.java}. For a fully custom
 *       translucent pass use {@code submitCustomGeometry}.</li>
 *   <li>The material data no longer needs plumbing: build a throw-away
 *       {@code IMateriallyTexturedBlockEntity} exactly as before, or read
 *       {@code MaterialTextureData.readFromItemStack(stack)} and feed it to
 *       {@code RetexturedBakedModelBuilder.resolve(...)} directly.</li>
 * </ol>
 *
 * <p>The original NeoForge implementation is preserved in git history and in {@code 26.1/}
 * (read-only source of the port), file {@code client/render/ModelGhostRenderer.java}.
 */
public class ModelGhostRenderer
{
    private static final ModelGhostRenderer INSTANCE = new ModelGhostRenderer();

    public static ModelGhostRenderer getInstance()
    {
        return INSTANCE;
    }

    private ModelGhostRenderer()
    {
    }

    /**
     * Disabled no-op. See the class javadoc for the replacement plan.
     */
    @SuppressWarnings("unused")
    public void renderGhost(
      final PoseStack poseStack,
      final ItemStack renderStack,
      final Vec3 targetedRenderPos,
      final BlockHitResult blockHitResult,
      final ClientLevel level,
      final boolean ignoreDepth)
    {
        // no-op
    }
}
