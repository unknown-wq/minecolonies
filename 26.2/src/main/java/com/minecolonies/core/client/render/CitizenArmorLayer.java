package com.minecolonies.core.client.render;

import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.client.render.modeltype.CitizenRenderState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.SkullBlock;
import org.jetbrains.annotations.NotNull;

/**
 * Armour layer of a citizen.
 * <p>
 * PORT-26.2: 26.2 rewrote {@code HumanoidArmorLayer} completely — it renders {@code state.headEquipment} and friends
 * through {@code EquipmentLayerRenderer}, and {@code renderArmorPiece}, {@code setPartVisibility}, {@code renderTrim},
 * {@code renderGlint} and the NeoForge {@code ClientHooks.getArmorTexture} hook are all gone. The citizen specific part
 * that survives is: (a) the "display armour" of the citizen data view, which is now substituted onto the render state
 * in {@code RenderBipedCitizen#extractRenderState} and therefore rendered by plain vanilla, and (b) the custom player
 * skin head, drawn here.
 */
@Environment(EnvType.CLIENT)
public class CitizenArmorLayer extends HumanoidArmorLayer<CitizenRenderState, CitizenModel<AbstractEntityCitizen>, HumanoidModel<CitizenRenderState>>
{
    private final SkullModelBase playerSkullModel;
    private final PlayerSkinRenderCache                playerSkinRenderCache;

    public CitizenArmorLayer(
      final RenderLayerParent<CitizenRenderState, CitizenModel<AbstractEntityCitizen>> parentLayer,
      final EntityRendererProvider.Context context)
    {
        super(parentLayer,
          ArmorModelSet.<HumanoidModel<CitizenRenderState>>bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
          context.getEquipmentRenderer());
        this.playerSkullModel = SkullBlockRenderer.createModel(context.getModelSet(), SkullBlock.Types.PLAYER);
        this.playerSkinRenderCache = context.getPlayerSkinRenderCache();
    }

    @Override
    public void submit(
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      final int lightCoords,
      @NotNull final CitizenRenderState state,
      final float yRot,
      final float xRot)
    {
        if (state.isInvisible)
        {
            return;
        }

        if (state.customTextureProfile != null)
        {
            final SkullModelBase skullModel = this.playerSkullModel;
            if (skullModel != null)
            {
                final RenderType renderType = this.playerSkinRenderCache.getOrDefault(state.customTextureProfile).renderType();

                poseStack.pushPose();
                poseStack.scale(1.0F, -1.0F, -1.0F);
                poseStack.rotateAround(Axis.YP.rotationDegrees(180), 0.0f, 0.0f, 0.0f);
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                final SkullModelBase.State skullState = new SkullModelBase.State();
                skullState.animationPos = 0f;
                skullState.yRot = yRot;
                skullState.xRot = xRot;
                submitNodeCollector.submitModel(skullModel,
                  skullState,
                  poseStack,
                  renderType,
                  lightCoords,
                  OverlayTexture.NO_OVERLAY,
                  state.outlineColor,
                  null);
                poseStack.popPose();
            }
        }

        super.submit(poseStack, submitNodeCollector, lightCoords, state, yRot, xRot);
    }
}
