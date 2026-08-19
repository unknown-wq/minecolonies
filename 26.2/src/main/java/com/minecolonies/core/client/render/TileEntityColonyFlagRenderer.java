package com.minecolonies.core.client.render;

import com.ldtteam.structurize.blocks.ModBlocks;
import com.minecolonies.core.blocks.decorative.BlockColonyFlagBanner;
import com.minecolonies.core.blocks.decorative.BlockColonyFlagWallBanner;
import com.minecolonies.core.tileentities.TileEntityColonyFlag;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.model.object.banner.BannerModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The custom renderer to render the colony flag patterns if they exist, and a placeholder marker if in Creative mode.
 * <p>
 * PORT-NOTE(26.2): the whole vanilla banner renderer was rewritten around a render state and
 * {@code BannerRenderer.submitPatterns(SpriteGetter, …)}; {@code ModelBakery.BANNER_BASE} became
 * {@code Sheets.BANNER_BASE}, and {@code ModelLayers.BANNER} split into standing/wall pole + flag layers. The
 * creative-mode placeholder that used {@code ItemRenderer#renderStatic} now resolves an
 * {@link ItemStackRenderState} in extract and submits it, which is how 26.2 draws items from a renderer.
 */
@Environment(EnvType.CLIENT)
public class TileEntityColonyFlagRenderer implements BlockEntityRenderer<TileEntityColonyFlag, TileEntityColonyFlagRenderer.ColonyFlagRenderState>
{
    /**
     * How far above the flag the creative placeholder floats, and how big it is drawn.
     */
    private static final double PLACEHOLDER_OFFSET_Y = 0.5D;
    private static final float  PLACEHOLDER_SCALE    = 0.75F;

    private final BannerModel      standingModel;
    private final BannerModel      wallModel;
    private final BannerFlagModel  standingFlagModel;
    private final BannerFlagModel  wallFlagModel;
    private final SpriteGetter     sprites;
    private final ItemModelResolver itemModelResolver;

    public TileEntityColonyFlagRenderer(final BlockEntityRendererProvider.Context context)
    {
        this.standingModel = new BannerModel(context.bakeLayer(ModelLayers.STANDING_BANNER));
        this.wallModel = new BannerModel(context.bakeLayer(ModelLayers.WALL_BANNER));
        this.standingFlagModel = new BannerFlagModel(context.bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
        this.wallFlagModel = new BannerFlagModel(context.bakeLayer(ModelLayers.WALL_BANNER_FLAG));
        this.sprites = context.sprites();
        this.itemModelResolver = context.itemModelResolver();
    }

    @NotNull
    @Override
    public ColonyFlagRenderState createRenderState()
    {
        return new ColonyFlagRenderState();
    }

    @Override
    public void extractRenderState(
      final TileEntityColonyFlag blockEntity,
      final ColonyFlagRenderState state,
      final float partialTicks,
      final Vec3 cameraPosition,
      final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        state.patterns = blockEntity.getPatterns();
        state.wall = false;
        state.rotation = 0.0F;

        final BlockState blockState = blockEntity.getBlockState();
        if (blockState.getBlock() instanceof BlockColonyFlagWallBanner)
        {
            state.wall = true;
            state.rotation = -blockState.getValue(BlockColonyFlagWallBanner.HORIZONTAL_FACING).toYRot();
        }
        else if (blockState.getBlock() instanceof BlockColonyFlagBanner)
        {
            state.rotation = (float) (-blockState.getValue(BlockColonyFlagBanner.ROTATION) * 360) / 16.0F;
        }

        final long gameTime = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0L;
        final BlockPos pos = blockEntity.getBlockPos();
        state.phase = ((float) Math.floorMod(pos.getX() * 7 + pos.getY() * 9 + pos.getZ() * 13 + gameTime, 100L) + partialTicks) / 100.0F;

        state.placeholder = extractPlaceholder(blockEntity);
    }

    /**
     * Resolve the creative-mode placeholder hint: while a creative player holds a banner, a Structurize substitution
     * block floats above the flag to show that the flag is a schematic anchor.
     * <p>
     * PORT-NOTE(26.2): this was {@code ItemRenderer#renderStatic}, which is gone. Items are now resolved into an
     * {@link ItemStackRenderState} up front and submitted later, so the resolve has to happen here in extract, where
     * the level and the {@link ItemModelResolver} handed over by the renderer context are both available.
     *
     * @param blockEntity the flag being rendered.
     * @return the resolved placeholder item, or null when it should not be shown.
     */
    @Nullable
    private ItemStackRenderState extractPlaceholder(final TileEntityColonyFlag blockEntity)
    {
        final Minecraft mc = Minecraft.getInstance();
        if (blockEntity.getLevel() == null
              || mc.player == null
              || mc.gameMode == null
              || mc.gameMode.getPlayerMode() != GameType.CREATIVE
              || !(mc.player.getMainHandItem().getItem() instanceof BannerItem))
        {
            return null;
        }

        final ItemStackRenderState placeholder = new ItemStackRenderState();
        this.itemModelResolver.updateForTopItem(placeholder,
          new ItemStack(ModBlocks.blockSubstitution.get()),
          ItemDisplayContext.FIXED,
          blockEntity.getLevel(),
          null,
          0);
        return placeholder;
    }

    @Override
    public void submit(
      final ColonyFlagRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        poseStack.pushPose();

        if (state.wall)
        {
            poseStack.translate(0.5D, -0.16666667F, 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.rotation));
            poseStack.translate(0.0D, -0.3125D, -0.4375D);
        }
        else
        {
            poseStack.translate(0.5D, 0.5D, 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(state.rotation));
        }

        if (state.placeholder != null)
        {
            poseStack.pushPose();
            poseStack.translate(0.0D, PLACEHOLDER_OFFSET_Y, 0.0D);
            poseStack.scale(PLACEHOLDER_SCALE, PLACEHOLDER_SCALE, PLACEHOLDER_SCALE);
            state.placeholder.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        poseStack.pushPose();
        poseStack.scale(2 / 3F, -2 / 3F, -2 / 3F);

        final BannerModel poleModel = state.wall ? this.wallModel : this.standingModel;
        final BannerFlagModel flagModel = state.wall ? this.wallFlagModel : this.standingFlagModel;

        submitNodeCollector.submitModel(poleModel,
          Unit.INSTANCE,
          poseStack,
          state.lightCoords,
          OverlayTexture.NO_OVERLAY,
          -1,
          Sheets.BANNER_BASE,
          this.sprites,
          0,
          state.breakProgress);

        BannerRenderer.submitPatterns(this.sprites,
          poseStack,
          submitNodeCollector,
          state.lightCoords,
          OverlayTexture.NO_OVERLAY,
          flagModel,
          state.phase,
          true,
          DyeColor.WHITE,
          state.patterns,
          state.breakProgress);

        poseStack.popPose();
        poseStack.popPose();
    }

    /**
     * Render state of a colony flag.
     */
    @Environment(EnvType.CLIENT)
    public static class ColonyFlagRenderState extends BlockEntityRenderState
    {
        public BannerPatternLayers patterns = BannerPatternLayers.EMPTY;
        public boolean             wall     = false;
        public float               rotation = 0.0F;
        public float               phase    = 0.0F;

        /**
         * The creative-mode placeholder hint, or null when it should not be drawn this frame.
         */
        public @Nullable ItemStackRenderState placeholder = null;
    }
}
