package com.minecolonies.core.client.render;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.tileentities.TileEntityEnchanter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.book.BookModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the floating book above the enchanter hut.
 * <p>
 * PORT-26.2: modelled on the rewritten vanilla {@code EnchantTableRenderer}: a render state carries the animation
 * values, the book is submitted with a {@code SpriteId} + {@code SpriteGetter} instead of a {@code Material} +
 * {@code MultiBufferSource}, and {@code BookModel#setupAnim} takes a {@code BookModel.State}.
 */
@Environment(EnvType.CLIENT)
public class TileEntityEnchanterRenderer implements BlockEntityRenderer<TileEntityEnchanter, TileEntityEnchanterRenderer.EnchanterRenderState>
{
    /**
     * Sprite of the book: the 1.21.1 {@code Material(BLOCK_ATLAS, minecolonies:block/enchanting_table_book)} maps
     * 1:1 onto {@code Sheets.BLOCKS_MAPPER} (same atlas, same {@code block/} prefix).
     */
    public static final SpriteId TEXTURE_BOOK =
      Sheets.BLOCKS_MAPPER.apply(net.minecraft.resources.Identifier.fromNamespaceAndPath(Constants.MOD_ID, "enchanting_table_book"));

    /**
     * The book model to be rendered.
     */
    private final BookModel modelBook;

    /**
     * Sprite lookup of the renderer.
     */
    private final SpriteGetter sprites;

    /**
     * Create the renderer.
     *
     * @param context the context.
     */
    public TileEntityEnchanterRenderer(final BlockEntityRendererProvider.Context context)
    {
        this.modelBook = new BookModel(context.bakeLayer(ModelLayers.BOOK));
        this.sprites = context.sprites();
    }

    @NotNull
    @Override
    public EnchanterRenderState createRenderState()
    {
        return new EnchanterRenderState();
    }

    @Override
    public void extractRenderState(
      final TileEntityEnchanter blockEntity,
      final EnchanterRenderState state,
      final float partialTicks,
      final Vec3 cameraPosition,
      final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        state.isEnchanter = true;
        final TileEntityEnchanter entity = blockEntity;

        state.time = entity.tickCount + partialTicks;

        final double rotVPrev = entity.bookRotation - entity.bookRotationPrev;
        final float circleRot = (float) ((rotVPrev + Math.PI % (2 * Math.PI)) - Math.PI);
        state.yRot = entity.bookRotationPrev + circleRot * partialTicks;

        state.pageFlip = Mth.lerp(partialTicks, entity.pageFlipPrev, entity.pageFlip);
        state.bookSpread = Mth.lerp(partialTicks, entity.bookSpreadPrev, entity.bookSpread);
    }

    @Override
    public void submit(
      final EnchanterRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        if (!state.isEnchanter)
        {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.75D, 0.5D);
        poseStack.translate(0.0D, (0.1F + Mth.sin(state.time * 0.1F) * 0.01F), 0.0D);
        poseStack.mulPose(Axis.YP.rotation(-state.yRot));
        poseStack.mulPose(Axis.ZP.rotationDegrees(80.0F));

        final float flipA = Mth.frac(state.pageFlip + 0.25F) * 1.6F - 0.3F;
        final float flipB = Mth.frac(state.pageFlip + 0.75F) * 1.6F - 0.3F;
        final BookModel.State bookState =
          BookModel.State.forAnimation(state.time, Mth.clamp(flipA, 0.0F, 1.0F), Mth.clamp(flipB, 0.0F, 1.0F), state.bookSpread);

        submitNodeCollector.submitModel(this.modelBook,
          bookState,
          poseStack,
          state.lightCoords,
          OverlayTexture.NO_OVERLAY,
          -1,
          TEXTURE_BOOK,
          this.sprites,
          0);

        // 26.3: the block-breaking overlay is no longer a trailing submitModel argument but its own
        // submitCrumblingOverlay call -- see BannerRenderer#submitBanner (BannerRenderer.java:170-177).
        if (state.breakProgress != null)
        {
            submitNodeCollector.submitCrumblingOverlay(this.modelBook,
              bookState,
              poseStack,
              TEXTURE_BOOK.renderType(this.modelBook.renderType()),
              state.lightCoords,
              OverlayTexture.NO_OVERLAY,
              -1,
              state.breakProgress);
        }

        poseStack.popPose();
    }

    /**
     * Animation state of the enchanter book.
     */
    @Environment(EnvType.CLIENT)
    public static class EnchanterRenderState extends BlockEntityRenderState
    {
        public boolean isEnchanter = false;
        public float   time        = 0;
        public float   yRot        = 0;
        public float   pageFlip    = 0;
        public float   bookSpread  = 0;
    }
}
