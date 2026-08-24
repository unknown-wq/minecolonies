package com.minecolonies.core.client.render;

import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer for a normal tile entity (Nothing special with rendering).
 * <p>
 * PORT-26.2: {@code BlockEntityRenderer} gained a render-state type parameter and {@code render(...)} became
 * {@code submit(...)}.
 */
@Environment(EnvType.CLIENT)
public class EmptyTileEntitySpecialRenderer implements BlockEntityRenderer<TileEntityColonyBuilding, BlockEntityRenderState>
{
    public EmptyTileEntitySpecialRenderer(final BlockEntityRendererProvider.Context context)
    {
        super();
    }

    @NotNull
    @Override
    public BlockEntityRenderState createRenderState()
    {
        return new BlockEntityRenderState();
    }

    @Override
    public void submit(
      @NotNull final BlockEntityRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        // nothing to draw
    }
}
