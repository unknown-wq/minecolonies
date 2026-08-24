package com.ldtteam.structurize.client;

import com.ldtteam.structurize.blockentities.BlockEntityTagSubstitution;
import com.ldtteam.structurize.component.CapturedBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tag anchor renderer; renders replacement block model inside of anchor "overlay" model.
 *
 * <p>Port note (26.2): block entity rendering became extract-then-submit
 * ({@link BlockEntityRenderer} is now {@code <T, S extends BlockEntityRenderState>}), and the whole
 * {@code BlockEntityWithoutLevelRenderer} / {@code IClientItemExtensions#getCustomRenderer} item path was
 * deleted from the game. Only the in-world half of this renderer survives.</p>
 */
public class TagSubstitutionRenderer
    implements BlockEntityRenderer<BlockEntityTagSubstitution, TagSubstitutionRenderer.TagSubstitutionRenderState>
{
    private static TagSubstitutionRenderer INSTANCE;

    public static TagSubstitutionRenderer getInstance()
    {
        return INSTANCE;
    }

    /**
     * 26.2 resolves block models against a display context instead of a level.
     */
    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

    private final BlockModelResolver blockModelResolver;

    public TagSubstitutionRenderer(@NotNull final BlockEntityRendererProvider.Context context)
    {
        INSTANCE = this;
        this.blockModelResolver = context.blockModelResolver();
    }

    /**
     * Carries the resolved replacement model from the extraction pass to the submit pass.
     */
    public static class TagSubstitutionRenderState extends BlockEntityRenderState
    {
        public @Nullable BlockModelRenderState replacement;
    }

    @Override
    public TagSubstitutionRenderState createRenderState()
    {
        return new TagSubstitutionRenderState();
    }

    @Override
    public void extractRenderState(@NotNull final BlockEntityTagSubstitution blockEntity,
                                   @NotNull final TagSubstitutionRenderState state,
                                   final float partialTicks,
                                   @NotNull final Vec3 cameraPosition,
                                   final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);

        final CapturedBlock replacement = blockEntity.getReplacement();
        if (replacement.blockState().isAir())
        {
            state.replacement = null;
            return;
        }

        final BlockModelRenderState model = new BlockModelRenderState();
        this.blockModelResolver.update(model, replacement.blockState(), BLOCK_DISPLAY_CONTEXT);
        state.replacement = model.isEmpty() ? null : model;

        // TODO(port-26.2): DEGRADED — the replacement's own block entity is no longer rendered. The 1.21.1 code
        //  spun up a SingleBlockFakeLevel and called BlockEntityRenderDispatcher#render on the nested block
        //  entity; in 26.2 that renderer's state has to be extracted through
        //  BlockEntityRenderDispatcher#tryExtractRenderState, which culls against the dispatcher's own camera
        //  position (set once per frame for the real level) and would reject a block entity sitting at
        //  BlockPos.ZERO of a fake level. Result: a tag anchor holding e.g. a chest shows the chest's
        //  block model only, without the animated lid.
        /*
        renderLevel.withFakeLevelContext(replacement.blockState(),
            BlockEntity.loadStatic(BlockPos.ZERO, replacement.blockState(), replacement.serializedBE().get(), realLevel.registryAccess()),
            realLevel,
            fakeLevel -> { ... entityDispatcher.render(renderLevel.getLevelSource().blockEntity, ...); });
        */
    }

    @Override
    public void submit(@NotNull final TagSubstitutionRenderState state,
                       @NotNull final PoseStack poseStack,
                       @NotNull final SubmitNodeCollector submitNodeCollector,
                       @NotNull final CameraRenderState camera)
    {
        if (state.replacement == null)
        {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(0.995f, 0.995f, 0.995f);
        poseStack.translate(0.0025f, 0.0025f, 0.0025f);

        // TODO(port-26.2): DEGRADED — NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT is gone; the sheet is now
        //  picked by BlockModelRenderState#setupModel (cutout or translucent block-item sheet), so the
        //  replacement is no longer forcibly layered/translucent inside the anchor.
        state.replacement.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }

    // TODO(port-26.2): DISABLED — item rendering. BlockEntityWithoutLevelRenderer does not exist in 26.2 and
    //  the NeoForge hook that installed it (IClientItemExtensions#getCustomRenderer) has no Fabric counterpart;
    //  the 26.2 replacement is a SpecialModelRenderer declared from the item model JSON ("minecraft:special").
    //  Visible effect: the tag substitution item in inventory/hand draws its plain model, without the
    //  captured replacement block inside it.
    /*
    @Override
    public void renderByItem(final ItemStack stack, final ItemDisplayContext transformType, final PoseStack poseStack,
                             final MultiBufferSource buffers, final int packedLight, final int packedOverlay)
    {
        final RenderType renderType = NeoForgeRenderTypes.ITEM_LAYERED_TRANSLUCENT.get();
        if (stack.getItem() instanceof ItemTagSubstitution anchor)
        {
            this.context.getBlockRenderDispatcher().renderSingleBlock(anchor.getBlock().defaultBlockState(),
                    poseStack, buffers, packedLight, packedOverlay, ModelData.EMPTY, renderType);
            render(CapturedBlock.readFromItemStack(stack), BlockPos.ZERO, 0, poseStack, buffers, packedLight, packedOverlay, renderType);
        }
    }
    */
}
