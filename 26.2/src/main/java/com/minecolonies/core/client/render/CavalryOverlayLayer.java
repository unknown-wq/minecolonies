package com.minecolonies.core.client.render;

import com.minecolonies.api.util.constant.Constants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.jetbrains.annotations.NotNull;

/**
 * Renders the cavalry horse overlay layer, which decorates the horse and indicates the horse's readiness for combat.
 * <p>
 * PORT-26.2: layers get a render state instead of the entity, so the readiness segment count is computed in
 * {@link CavalryHorseRenderer#extractRenderState} and only read off the state here.
 */
@Environment(EnvType.CLIENT)
public class CavalryOverlayLayer extends RenderLayer<HorseRenderState, HorseModel>
{
    /**
     * Overlay textures, indexed by readiness segment count.
     */
    private static final Identifier[] OVERLAY_TEXTURES = new Identifier[6];

    static
    {
        for (int i = 0; i < OVERLAY_TEXTURES.length; i++)
        {
            OVERLAY_TEXTURES[i] = Identifier.fromNamespaceAndPath(Constants.MOD_ID,
              "textures/entity/horse/cavalry_overlay_layer" + i + ".png");
        }
    }

    public CavalryOverlayLayer(final RenderLayerParent<HorseRenderState, HorseModel> parent)
    {
        super(parent);
    }

    @Override
    public void submit(
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      @NotNull final HorseRenderState state,
      final float yRot,
      final float xRot)
    {
        if (state.isInvisible)
        {
            return;
        }

        final int segments = Math.max(0, Math.min(OVERLAY_TEXTURES.length - 1, ((FabricRenderState) state).getDataOrDefault(CavalryHorseRenderer.COMBAT_READINESS_SEGMENTS, 0)));

        // 0.85f alpha -> 217 (out of 255)
        final int color = ARGB.color((int) (0.85f * 255.0f), 255, 255, 255);

        submitNodeCollector.submitModel(this.getParentModel(),
          state,
          poseStack,
          RenderTypes.entityTranslucent(OVERLAY_TEXTURES[segments]),
          lightCoords,
          OverlayTexture.NO_OVERLAY,
          color,
          null,
          state.outlineColor,
          null);
    }
}
