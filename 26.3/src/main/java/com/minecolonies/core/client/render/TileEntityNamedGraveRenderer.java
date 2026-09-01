package com.minecolonies.core.client.render;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.blocks.huts.AbstractBlockMinecoloniesDefault;
import com.minecolonies.core.tileentities.TileEntityNamedGrave;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * PORT-26.2: block entity renderers extract a state first and then submit; the text lines and the facing of the grave
 * therefore have to be copied onto a MineColonies specific render state, and text goes through
 * {@code SubmitNodeCollector#submitText} instead of {@code Font#drawInBatch}.
 */
@Environment(EnvType.CLIENT)
public class TileEntityNamedGraveRenderer implements BlockEntityRenderer<TileEntityNamedGrave, TileEntityNamedGraveRenderer.NamedGraveRenderState>
{
    /**
     * Basic rotation to achieve a certain direction.
     */
    private static final int BASIC_ROTATION = 90;

    /**
     * Rotate by amount to go east.
     */
    private static final int ROTATE_EAST = 1;

    /**
     * Rotate by amount to go north.
     */
    private static final int ROTATE_NORTH = 2;

    /**
     * Rotate by amount to go west.
     */
    private static final int ROTATE_WEST = 3;

    /**
     * Maximum characters of a name line.
     */
    private static final int MAX_SIZE = 20;

    public TileEntityNamedGraveRenderer(final BlockEntityRendererProvider.Context context)
    {
        super();
    }

    @NotNull
    @Override
    public NamedGraveRenderState createRenderState()
    {
        return new NamedGraveRenderState();
    }

    @Override
    public void extractRenderState(
      final TileEntityNamedGrave blockEntity,
      final NamedGraveRenderState state,
      final float partialTicks,
      final Vec3 cameraPosition,
      final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        state.facing = null;
        state.lines.clear();

        if (blockEntity.getLevel() == null)
        {
            return;
        }

        final BlockState blockState = blockEntity.getLevel().getBlockState(blockEntity.getBlockPos());
        if (blockState.getBlock() != ModBlocks.blockNamedGrave)
        {
            return;
        }

        state.facing = blockState.getValue(AbstractBlockMinecoloniesDefault.FACING);
        if (blockEntity.getTextLines().isEmpty())
        {
            state.lines.add("Unknown Citizen");
        }
        else
        {
            for (final String line : blockEntity.getTextLines())
            {
                state.lines.add(line.length() > MAX_SIZE ? line.substring(0, MAX_SIZE) : line);
            }
        }
    }

    @Override
    public void submit(
      final NamedGraveRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        if (state.facing == null || state.lines.isEmpty())
        {
            return;
        }

        poseStack.pushPose();

        switch (state.facing)
        {
            case NORTH:
                poseStack.translate(0.5f, 1.18F, 0.48F); //in front of the center point of the name plate
                poseStack.scale(0.006F, -0.006F, 0.006F); //size of the text font
                poseStack.rotate(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_NORTH));
                break;
            case SOUTH:
                poseStack.translate(0.5f, 1.18F, 0.54F);
                poseStack.scale(0.006F, -0.006F, 0.006F);
                //don't rotate at all.
                break;
            case EAST:
                poseStack.translate(0.54f, 1.18F, 0.5F);
                poseStack.scale(0.006F, -0.006F, 0.006F);
                poseStack.rotate(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_EAST));
                break;
            case WEST:
                poseStack.translate(0.48f, 1.18F, 0.5F);
                poseStack.scale(0.006F, -0.006F, 0.006F);
                poseStack.rotate(Axis.YP.rotationDegrees(BASIC_ROTATION * ROTATE_WEST));
                break;
            default:
                break;
        }

        for (int i = 0; i < state.lines.size(); i++)
        {
            submitText(submitNodeCollector, poseStack, state.lightCoords, state.lines.get(i), i);
        }

        poseStack.popPose();
    }

    private static void submitText(
      final SubmitNodeCollector submitNodeCollector,
      final PoseStack poseStack,
      final int lightCoords,
      final String text,
      final int line)
    {
        final FormattedCharSequence sequence = FormattedCharSequence.forward(text, Style.EMPTY);
        final Font fontRenderer = Minecraft.getInstance().font;
        final float x = (float) (-fontRenderer.width(sequence) / 2); //render width of text divided by 2

        submitNodeCollector.submitText(poseStack,
          x,
          line * 10f,
          sequence,
          false,
          Font.DisplayMode.NORMAL,
          lightCoords,
          0xdcdcdc00,
          0,
          0);
    }

    /**
     * Render state of a named grave.
     */
    @Environment(EnvType.CLIENT)
    public static class NamedGraveRenderState extends BlockEntityRenderState
    {
        /**
         * Facing of the grave block, or null when the block is no longer a named grave.
         */
        public @Nullable Direction facing = null;

        /**
         * Text lines of the grave, already trimmed to the maximum length.
         */
        public final List<String> lines = new ArrayList<>();
    }
}
