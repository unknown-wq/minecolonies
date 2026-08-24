package com.minecolonies.core.client.render;

import com.minecolonies.api.blocks.huts.AbstractBlockMinecoloniesDefault;
import com.minecolonies.api.tileentities.AbstractTileEntityScarecrow;
import com.minecolonies.api.tileentities.ScareCrowType;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.blocks.BlockScarecrow;
import com.minecolonies.core.client.model.ScarecrowModel;
import com.minecolonies.core.event.ClientRegistryHandler;
import com.minecolonies.core.tileentities.TileEntityScarecrow;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class to render the scarecrow.
 * <p>
 * PORT-NOTE(26.2): block entity renderers extract a state and then submit; {@code Material} became {@code SpriteId}
 * plus a {@code SpriteGetter}. The vanilla lantern that used to be drawn with
 * {@code BlockRenderDispatcher#renderSingleBlock} now goes through
 * {@link SubmitNodeCollector#submitMovingBlock}, which is the same entry point vanilla's piston renderer uses to draw
 * a single block state from a block entity renderer.
 */
@Environment(EnvType.CLIENT)
public class TileEntityScarecrowRenderer implements BlockEntityRenderer<TileEntityScarecrow, TileEntityScarecrowRenderer.ScarecrowRenderState>
{
    /**
     * Offset to the block middle.
     */
    private static final double BLOCK_MIDDLE = 0.5;

    /**
     * Y-Offset in order to have the scarecrow over ground.
     */
    private static final double YOFFSET = 1.5;

    /**
     * Rotate the model some degrees.
     */
    private static final int ROTATION = 180;

    /**
     * Basic rotation to achieve a certain direction.
     */
    private static final int BASIC_ROTATION = 90;

    /**
     * Rotate by amount to go east.
     */
    private static final int ROTATE_EAST = 1;

    /**
     * Rotate by amount to go south.
     */
    private static final int ROTATE_SOUTH = 2;

    /**
     * Rotate by amount to go west.
     */
    private static final int ROTATE_WEST = 3;

    /**
     * Where the lantern hangs, relative to the scarecrow's un-rolled origin. Out along the arm, and a little forward.
     */
    private static final float LANTERN_OFFSET_X = 0.6F;
    private static final float LANTERN_OFFSET_Y = -0.6F;
    private static final float LANTERN_OFFSET_Z = -0.375F;

    /**
     * The lantern is drawn a little smaller than a real block so it reads as hanging rather than stacked.
     */
    private static final float LANTERN_SCALE_X = 0.75F;
    private static final float LANTERN_SCALE_Y = 0.65F;
    private static final float LANTERN_SCALE_Z = 0.75F;

    public static final SpriteId SCARECROW_A =
      Sheets.BLOCKS_MAPPER.apply(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "blockscarecrowpumpkin"));
    public static final SpriteId SCARECROW_B =
      Sheets.BLOCKS_MAPPER.apply(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "blockscarecrownormal"));

    /**
     * The model of the scarecrow.
     */
    @NotNull
    private final ScarecrowModel model;

    /**
     * Sprite lookup of the renderer.
     */
    private final SpriteGetter sprites;

    /**
     * The public constructor for the renderer.
     *
     * @param context the render context.
     */
    public TileEntityScarecrowRenderer(final BlockEntityRendererProvider.Context context)
    {
        this.model = new ScarecrowModel(context.bakeLayer(ClientRegistryHandler.SCARECROW));
        this.sprites = context.sprites();
    }

    @NotNull
    @Override
    public ScarecrowRenderState createRenderState()
    {
        return new ScarecrowRenderState();
    }

    @Override
    public void extractRenderState(
      final TileEntityScarecrow blockEntity,
      final ScarecrowRenderState state,
      final float partialTicks,
      final Vec3 cameraPosition,
      final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        state.upperHalf = blockEntity.getBlockState().getValue(BlockScarecrow.HALF) == DoubleBlockHalf.UPPER;
        state.pumpkinHead = blockEntity.getScarecrowType() == ScareCrowType.PUMPKINHEAD;
        state.facing = null;
        state.lantern = null;

        if (blockEntity.getLevel() != null
              && blockEntity.getLevel().getBlockState(blockEntity.getBlockPos()).getBlock() instanceof BlockScarecrow)
        {
            state.facing = blockEntity.getLevel()
                             .getBlockState(blockEntity.getBlockPos())
                             .getValue(AbstractBlockMinecoloniesDefault.FACING);
        }

        if (blockEntity.getBlockState().getValue(BlockScarecrow.LANTERN) && blockEntity.getLevel() instanceof ClientLevel level)
        {
            state.lantern = createLanternState(level, blockEntity.getBlockPos());
        }
    }

    /**
     * Build the render state for the vanilla lantern hanging off the scarecrow's arm.
     * <p>
     * PORT-NOTE(26.2): {@code BlockRenderDispatcher#renderSingleBlock} is gone, and a block model can no longer be
     * baked into a buffer straight from a renderer. The replacement is a {@link MovingBlockRenderState}, the same
     * carrier vanilla's {@code PistonHeadRenderer} fills in: it answers {@link Blocks#LANTERN} at
     * {@code pos} and air everywhere else, so the lantern is lit and shaded as a free-standing block rather than
     * picking up the scarecrow's neighbours. It has to be built here, in extract, because the submit phase has no
     * level to read the light engine and biome off.
     *
     * @param level the client level the scarecrow lives in.
     * @param pos   the scarecrow's own position, which is where the light for the lantern is sampled; the scarecrow
     *              block emits the lantern's own light level while {@code LANTERN} is set, so this reads bright.
     * @return the state to hand to {@link SubmitNodeCollector#submitMovingBlock}.
     */
    private static MovingBlockRenderState createLanternState(@NotNull final ClientLevel level, @NotNull final BlockPos pos)
    {
        final MovingBlockRenderState lantern = new MovingBlockRenderState();
        lantern.blockState = Blocks.LANTERN.defaultBlockState();
        lantern.blockPos = pos;
        lantern.randomSeedPos = pos;
        lantern.biome = level.getBiome(pos);
        lantern.cardinalLighting = level.cardinalLighting();
        lantern.lightEngine = level.getLightEngine();
        return lantern;
    }

    @Override
    public void submit(
      final ScarecrowRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        if (state.upperHalf)
        {
            return;
        }

        //Store the transformation
        poseStack.pushPose();
        //Set viewport to tile entity position to render it
        poseStack.translate(BLOCK_MIDDLE, YOFFSET, BLOCK_MIDDLE);
        poseStack.mulPose(Axis.ZP.rotationDegrees(ROTATION));

        //In the case of worldLags tileEntities may sometimes disappear.
        if (state.facing != null)
        {
            switch (state.facing)
            {
                case EAST:
                    poseStack.mulPose(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_EAST));
                    break;
                case SOUTH:
                    poseStack.mulPose(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_SOUTH));
                    break;
                case WEST:
                    poseStack.mulPose(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_WEST));
                    break;
                default:
                    //don't rotate at all.
            }
        }

        final SpriteId sprite = state.pumpkinHead ? SCARECROW_A : SCARECROW_B;
        submitNodeCollector.submitModel(this.model,
          Unit.INSTANCE,
          poseStack,
          state.lightCoords,
          OverlayTexture.NO_OVERLAY,
          -1,
          sprite,
          this.sprites,
          0);

        // 26.3: submitModel no longer takes the block-breaking overlay as a trailing argument; it is a separate
        // submitCrumblingOverlay call, exactly as vanilla's BannerRenderer#submitBanner now does it
        // (BannerRenderer.java:170-177).
        if (state.breakProgress != null)
        {
            submitNodeCollector.submitCrumblingOverlay(this.model,
              Unit.INSTANCE,
              poseStack,
              sprite.renderType(this.model.renderType()),
              state.lightCoords,
              OverlayTexture.NO_OVERLAY,
              -1,
              state.breakProgress);
        }

        if (state.lantern != null)
        {
            renderLantern(state.lantern, poseStack, submitNodeCollector);
        }

        poseStack.popPose();
    }

    /**
     * Hang the lantern off the scarecrow's arm.
     * <p>
     * The transform is the one the 1.21.1 renderer used, kept verbatim so the lantern keeps its old position: the
     * second 180 degree roll cancels the one the scarecrow model is drawn under, which puts the lantern upright again
     * before it is nudged out to the arm and squashed slightly.
     *
     * @param lantern              the extracted lantern block state.
     * @param poseStack            the pose stack, already positioned and rotated for the scarecrow.
     * @param submitNodeCollector  the collector to submit to.
     */
    private static void renderLantern(
      @NotNull final MovingBlockRenderState lantern,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector)
    {
        poseStack.pushPose();

        poseStack.mulPose(Axis.ZP.rotationDegrees(ROTATION));
        poseStack.translate(LANTERN_OFFSET_X, LANTERN_OFFSET_Y, LANTERN_OFFSET_Z);
        poseStack.scale(LANTERN_SCALE_X, LANTERN_SCALE_Y, LANTERN_SCALE_Z);

        submitNodeCollector.submitMovingBlock(poseStack, lantern, 0);

        poseStack.popPose();
    }

    /**
     * Render state of a scarecrow.
     */
    @Environment(EnvType.CLIENT)
    public static class ScarecrowRenderState extends BlockEntityRenderState
    {
        public boolean            upperHalf    = false;
        public boolean            pumpkinHead  = false;
        public @Nullable Direction facing      = null;

        /**
         * The lantern to hang off the arm, or null when the block state has none.
         */
        public @Nullable MovingBlockRenderState lantern = null;
    }
}
