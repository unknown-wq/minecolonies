package com.minecolonies.core.client.render;

import com.minecolonies.core.blocks.BlockDecorationController;
import com.minecolonies.core.tileentities.TileEntityDecorationController;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the decoration controller's own block model on the face of the block it points at.
 * <p>
 * PORT-26.2: {@code BlockRenderDispatcher#getModelRenderer().tesselateBlock(...)} no longer exists. 26.2 resolves the
 * block model into a {@link BlockModelRenderState} while extracting (which is where level access still exists) and
 * submits that state afterwards.
 */
@Environment(EnvType.CLIENT)
public class TileEntityDecoControllerRenderer
  implements BlockEntityRenderer<TileEntityDecorationController, TileEntityDecoControllerRenderer.DecoControllerRenderState>
{
    /**
     * 26.2 needs a display context to resolve a block model; a plain one is enough here.
     */
    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

    private final BlockModelResolver blockModelResolver;

    public TileEntityDecoControllerRenderer(final BlockEntityRendererProvider.Context context)
    {
        this.blockModelResolver = context.blockModelResolver();
    }

    @NotNull
    @Override
    public DecoControllerRenderState createRenderState()
    {
        return new DecoControllerRenderState();
    }

    @Override
    public void extractRenderState(
      final TileEntityDecorationController blockEntity,
      final DecoControllerRenderState state,
      final float partialTicks,
      final Vec3 cameraPosition,
      final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        state.visible = false;
        state.model.clear();

        final Level level = blockEntity.getLevel();
        if (level == null)
        {
            return;
        }

        final BlockState decoController = blockEntity.getBlockState();
        if (decoController.isAir())
        {
            return;
        }

        final Direction direction = decoController.getValue(BlockDecorationController.FACING);
        final BlockPos offsetPos = blockEntity.getBlockPos().relative(direction);
        final BlockState neighbour = level.getBlockState(offsetPos);
        final VoxelShape shape = neighbour.getShape(level, offsetPos);

        if (shape.isEmpty() || Block.isShapeFullBlock(shape))
        {
            state.offset = Vec3.ZERO;
        }
        else
        {
            state.offset = switch (direction)
            {
                case UP -> new Vec3(0, shape.min(Direction.Axis.Y), 0);
                case DOWN -> new Vec3(0, shape.max(Direction.Axis.Y) - 1, 0);
                case NORTH -> new Vec3(0, 0, shape.max(Direction.Axis.Z) - 1);
                case SOUTH -> new Vec3(0, 0, shape.min(Direction.Axis.Z));
                case EAST -> new Vec3(shape.min(Direction.Axis.X), 0, 0);
                case WEST -> new Vec3(shape.max(Direction.Axis.X) - 1, 0, 0);
            };
        }

        this.blockModelResolver.update(state.model, decoController, BLOCK_DISPLAY_CONTEXT);
        state.visible = true;
    }

    @Override
    public void submit(
      final DecoControllerRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        if (!state.visible)
        {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(state.offset.x, state.offset.y, state.offset.z);
        state.model.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * Render state of the decoration controller.
     */
    @Environment(EnvType.CLIENT)
    public static class DecoControllerRenderState extends BlockEntityRenderState
    {
        public final BlockModelRenderState model = new BlockModelRenderState();
        public       boolean               visible = false;
        public       Vec3                  offset  = Vec3.ZERO;
    }
}
