package com.ldtteam.structurize.event;

import com.ldtteam.structurize.Structurize;
import com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.client.BlueprintRenderer.TransparencyHack;
import com.ldtteam.structurize.items.ItemTagTool.TagData;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BlueprintPreviewData;
import com.ldtteam.structurize.storage.rendering.types.BoxPreviewData;
import com.ldtteam.structurize.util.WorldRenderMacros;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.ldtteam.structurize.util.WorldRenderMacros.Stage;

import java.util.List;
import java.util.Map;

/**
 * For rendering into world.
 *
 * <p>Port note: {@code RenderLevelStageEvent.Stage} was NeoForge. The render stage enum now belongs to
 * {@link WorldRenderMacros} (owner: render agent) and has to keep the three constants used below —
 * {@code AFTER_TRANSLUCENT_BLOCKS}, {@code AFTER_BLOCK_ENTITIES}, {@code AFTER_ENTITIES}. Careful when
 * reading Fabric docs: {@code net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext} is an
 * unrelated class with the same simple name.</p>
 */
public class WorldRenderContext extends WorldRenderMacros
{
    static final WorldRenderContext INSTANCE = new WorldRenderContext();

    @Override
    protected void renderWithinContext(final Stage stage)
    {
        final double alpha = Structurize.getConfig().getClient().rendererTransparency.get();
        final boolean isAlphaApplied = alpha > 0 && alpha < TransparencyHack.THRESHOLD;

        final Stage when = isAlphaApplied ? Stage.AFTER_TRANSLUCENT_BLOCKS : Stage.AFTER_BLOCK_ENTITIES;
        // otherwise even worse sorting issues arise
        if (stage == when)
        {
            renderBlueprints();
        }

        if (stage == WorldRenderMacros.STAGE_FOR_LINES)
        {
            renderBoxes();
            renderTagTool();
        }
    }

    private void renderBlueprints()
    {
        for (final BlueprintPreviewData previewData : RenderingCache.getBlueprintsToRender())
        {
            final Blueprint blueprint = previewData.getBlueprint();

            if (blueprint != null)
            {
                Profiler.get().push("struct_render");

                renderBlueprint(previewData, previewData.getPos());

                Profiler.get().pop();
            }
        }
    }

    private void renderBoxes()
    {
        for (final BlueprintPreviewData previewData : RenderingCache.getBlueprintsToRender())
        {
            final Blueprint blueprint = previewData.getBlueprint();

            if (blueprint != null)
            {
                final BlockPos anchor = blueprint.getPrimaryBlockOffset();

                Profiler.get().push("struct_render");
                pushPoseCameraToPos(previewData.getPos().subtract(anchor));

                renderWhiteLineBox(BlockPos.ZERO,
                    new BlockPos(blueprint.getSizeX() - 1, blueprint.getSizeY() - 1, blueprint.getSizeZ() - 1),
                    DEFAULT_LINE_WIDTH);
                renderRedGlintLineBox(anchor, anchor, DEFAULT_LINE_WIDTH);

                popPose();
                Profiler.get().pop();
            }
        }

        for (final BoxPreviewData previewData : RenderingCache.getBoxesToRender())
        {
            final BlockPos root = previewData.pos1();

            Profiler.get().push("struct_box");
            pushPoseCameraToPos(root);

            // Used to render a red box around a scan's Primary offset (primary block)
            renderWhiteLineBox(BlockPos.ZERO, previewData.pos2().subtract(root), DEFAULT_LINE_WIDTH);
            previewData.anchor().map(pos -> pos.subtract(root)).ifPresent(pos -> renderRedGlintLineBox(pos, pos, DEFAULT_LINE_WIDTH));

            popPose();
            Profiler.get().pop();
        }
    }

    private void renderTagTool()
    {
        final Player player = mc.player;
        final ItemStack itemStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        final TagData tags = TagData.readFromItemStack(itemStack);
        if (tags.anchorPos().isPresent())
        {
            final BlockPos tagAnchor = tags.anchorPos().get();
            final BlockEntity te = player.level().getBlockEntity(tagAnchor);

            Profiler.get().push("struct_tags");
            pushPoseCameraToPos(tagAnchor);

            if (te instanceof final IBlueprintDataProviderBE blueprintProvider)
            {
                final Map<BlockPos, List<String>> tagPosList = blueprintProvider.getWorldTagPosMap();

                for (final Map.Entry<BlockPos, List<String>> entry : tagPosList.entrySet())
                {
                    final BlockPos pos = entry.getKey().subtract(tagAnchor);
                    renderWhiteLineBox(pos, pos, DEFAULT_LINE_WIDTH);
                    renderDebugText(pos, entry.getKey(), entry.getValue(), true, 3);
                }
            }
            renderRedGlintLineBox(BlockPos.ZERO, BlockPos.ZERO, DEFAULT_LINE_WIDTH);

            popPose();
            Profiler.get().pop();
        }
    }
}
