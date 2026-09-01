package com.minecolonies.core.client.render;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.core.client.render.worldevent.WorldEventContext;
import com.minecolonies.core.tileentities.TileEntityColonySign;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.util.constant.TranslationConstants.NEXT;
import static com.minecolonies.api.util.constant.TranslationConstants.PREVIOUS;
import static com.minecolonies.core.blocks.BlockColonySign.CONNECTED;

/**
 * Renderer of the colony sign.
 * <p>
 * PORT-26.2: {@code BakedModel} + {@code BlockRenderDispatcher#getModelRenderer().renderModel(...)} and the NeoForge
 * {@code RenderTypeHelper}/{@code ModelData} helpers are gone; the sign model is resolved into a
 * {@link BlockModelRenderState} while extracting and submitted afterwards. Text goes through
 * {@code SubmitNodeCollector#submitText}, and the in-world hover labels delegate to Structurize's
 * {@code WorldRenderMacros#renderDebugText}, which is the 26.2 form of the copy this class carried.
 */
@Environment(EnvType.CLIENT)
public class TileEntityColonySignRenderer implements BlockEntityRenderer<TileEntityColonySign, TileEntityColonySignRenderer.ColonySignRenderState>
{
    /**
     * Maximum characters of a text line.
     */
    private static final int MAX_SIZE = 20;

    /**
     * 26.2 needs a display context to resolve a block model; a plain one is enough here.
     */
    private static final BlockDisplayContext BLOCK_DISPLAY_CONTEXT = BlockDisplayContext.create();

    private final BlockModelResolver blockModelResolver;

    public TileEntityColonySignRenderer(final BlockEntityRendererProvider.Context context)
    {
        this.blockModelResolver = context.blockModelResolver();
    }

    @NotNull
    @Override
    public ColonySignRenderState createRenderState()
    {
        return new ColonySignRenderState();
    }

    @Override
    public void extractRenderState(
      final TileEntityColonySign blockEntity,
      final ColonySignRenderState state,
      final float partialTicks,
      final Vec3 cameraPosition,
      final ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);

        state.visible = false;
        state.lines.clear();
        state.model.clear();

        final BlockState blockState = blockEntity.getBlockState();
        if (blockState.getBlock() != ModBlocks.blockColonySign)
        {
            return;
        }

        state.visible = true;
        state.rotation = blockEntity.getRelativeRotation();

        final boolean connected = blockEntity.getTargetColonyId() != blockEntity.getCachedSignAboveColony();
        this.blockModelResolver.update(state.model,
          connected ? blockState.setValue(CONNECTED, true) : blockState.setValue(CONNECTED, false),
          BLOCK_DISPLAY_CONTEXT);

        final String colonyName = blockEntity.getColonyName();
        final int distance = blockEntity.getColonyDistance();
        if (colonyName.isEmpty())
        {
            state.lines.add(new SignLine("Unknown Colony", 0, 0));
            state.lines.add(new SignLine(Component.translatable("com.minecolonies.coremod.dist.blocks", distance).getString(), 3, 0));
        }
        else
        {
            final String targetColonyName = blockEntity.getTargetColonyName();
            if (!targetColonyName.isEmpty() && connected)
            {
                addColonyName(state, colonyName, distance, -10);
                addColonyName(state, targetColonyName, blockEntity.getTargetColonyDistance(), -60);
            }
            else
            {
                addColonyName(state, colonyName, distance, -35);
            }
        }
    }

    /**
     * Lay out the name and distance of one colony on the sign.
     */
    private static void addColonyName(final ColonySignRenderState state, final String colonyName, final int distance, final int offset)
    {
        final Font font = Minecraft.getInstance().font;
        if (font.width(colonyName) > 90)
        {
            final List<FormattedText> splitName = font.getSplitter().splitLines(colonyName, 90, Style.EMPTY);
            for (int i = 0; i < Math.min(2, splitName.size()); i++)
            {
                state.lines.add(new SignLine(splitName.get(i).getString(), i, offset));
            }
        }
        else
        {
            state.lines.add(new SignLine(colonyName, 0, offset));
        }
        state.lines.add(new SignLine(Component.translatable("com.minecolonies.coremod.dist.blocks", distance).getString(), 3, offset));
    }

    @Override
    public void submit(
      final ColonySignRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        if (!state.visible)
        {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        poseStack.rotate(Axis.YP.rotationDegrees(state.rotation));
        poseStack.translate(-0.5, -0.5, -0.5);
        state.model.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();

        submitTextOnSide(state, poseStack, submitNodeCollector, true);
        submitTextOnSide(state, poseStack, submitNodeCollector, false);
    }

    /**
     * Draw the text of the sign on one of its two faces.
     */
    private static void submitTextOnSide(
      final ColonySignRenderState state,
      final PoseStack poseStack,
      final SubmitNodeCollector submitNodeCollector,
      final boolean mirrored)
    {
        poseStack.pushPose();
        poseStack.translate(0.5f, 0.5F, 0.5f);
        poseStack.rotate(Axis.YP.rotationDegrees(state.rotation));
        if (mirrored)
        {
            poseStack.rotate(Axis.YP.rotationDegrees(180));
        }
        poseStack.translate(-0.0f, -0.1F, 0.2f);
        poseStack.scale(0.007F, -0.007F, 0.007F);

        for (final SignLine line : state.lines)
        {
            submitText(submitNodeCollector, poseStack, state.lightCoords, line.text(), line.line(), line.offset());
        }

        poseStack.popPose();
    }

    /**
     * Text render utility.
     */
    private static void submitText(
      final SubmitNodeCollector submitNodeCollector,
      final PoseStack poseStack,
      final int lightCoords,
      String text,
      final int line,
      final float offset)
    {
        if (text.length() > MAX_SIZE)
        {
            text = text.substring(0, MAX_SIZE);
        }

        final FormattedCharSequence sequence = FormattedCharSequence.forward(text, Style.EMPTY);
        final Font fontRenderer = Minecraft.getInstance().font;
        final float x = (float) (-fontRenderer.width(sequence) / 2); //render width of text divided by 2

        submitNodeCollector.submitText(poseStack, x, line * 8f + offset, sequence, false, Font.DisplayMode.NORMAL, lightCoords, 0xdcdcdc00, 0, 0);
    }

    public static void renderSignHover(final WorldEventContext context)
    {
        final HitResult rayTraceResult = Minecraft.getInstance().hitResult;
        if (!(rayTraceResult instanceof final BlockHitResult blockRayTraceResult) || blockRayTraceResult.getType() == HitResult.Type.MISS)
        {
            return;
        }

        final BlockPos posAtCamera = blockRayTraceResult.getBlockPos();
        if (context.clientLevel.getBlockState(posAtCamera).getBlock() != ModBlocks.blockColonySign)
        {
            return;
        }

        if (context.clientLevel.getBlockEntity(posAtCamera) instanceof TileEntityColonySign tileEntityColonySign)
        {
            if (!BlockPos.ZERO.equals(tileEntityColonySign.getPreviousPos()))
            {
                renderTextBoxAtPos(context, tileEntityColonySign.getPreviousPos(), List.of(Component.translatable(PREVIOUS).getString()));
            }
            if (!BlockPos.ZERO.equals(tileEntityColonySign.getNextPosition()))
            {
                renderTextBoxAtPos(context, tileEntityColonySign.getNextPosition(), List.of(Component.translatable(NEXT).getString()));
            }
        }
    }

    private static void renderTextBoxAtPos(final WorldEventContext context, final BlockPos pos, final List<String> text)
    {
        context.pushPoseCameraToPos(pos);
        context.renderLineBoxWithShadow(BlockPos.ZERO, 0xffffffff, WorldEventContext.DEFAULT_LINE_WIDTH * 2);
        context.popPose();
        // PORT-26.2: the hand written copy of renderDebugText is gone; Structurize's macro already does exactly this
        //  on the 26.2 submit pipeline (and applies the camera-relative translation itself).
        context.renderDebugText(pos, text, true, 3);
    }

    /**
     * One laid out line of sign text.
     *
     * @param text   the text.
     * @param line   the line index within its block.
     * @param offset the vertical offset of the block.
     */
    public record SignLine(String text, int line, float offset)
    {
    }

    /**
     * Render state of a colony sign.
     */
    @Environment(EnvType.CLIENT)
    public static class ColonySignRenderState extends BlockEntityRenderState
    {
        public final BlockModelRenderState model    = new BlockModelRenderState();
        public final List<SignLine>        lines    = new ArrayList<>();
        public       boolean               visible  = false;
        public       float                 rotation = 0.0F;
    }
}
