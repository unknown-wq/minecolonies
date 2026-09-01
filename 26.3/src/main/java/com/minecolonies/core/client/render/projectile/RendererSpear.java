package com.minecolonies.core.client.render.projectile;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.model.SpearModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ThrownTridentRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import org.jetbrains.annotations.NotNull;

/**
 * Custom renderer for spears.
 * <p>
 * PORT-26.2: modelled on the rewritten vanilla {@code ThrownTridentRenderer} — {@code render(...)} became
 * {@code submit(...)} and {@code SpearModel} is a {@code Model<Unit>} now.
 * <p>
 * PORT-26.3: {@code RenderTypes.entityGlint()} is gone, and with it the "draw the model, then draw it again in
 * the glint type" shape. Vanilla's trident renderer now picks one render type or the other for the single draw
 * ({@code ThrownTridentRenderer:35-41}); this follows it.
 */
@Environment(EnvType.CLIENT)
public class RendererSpear extends EntityRenderer<ThrownTrident, ThrownTridentRenderState>
{
    private final Identifier texture = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/entity/spear.png");
    private final SpearModel model;

    /**
     * Create a new spear renderer.
     *
     * @param context the context.
     */
    public RendererSpear(final EntityRendererProvider.Context context)
    {
        super(context);
        this.model = new SpearModel(context.bakeLayer(ModelLayers.TRIDENT));
    }

    @NotNull
    @Override
    public ThrownTridentRenderState createRenderState()
    {
        return new ThrownTridentRenderState();
    }

    @Override
    public void extractRenderState(final ThrownTrident entity, final ThrownTridentRenderState state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        state.yRot = entity.getYRot(partialTicks);
        state.xRot = entity.getXRot(partialTicks);
        state.isFoil = entity.isFoil();
    }

    @Override
    public void submit(
      final ThrownTridentRenderState state,
      @NotNull final PoseStack poseStack,
      @NotNull final SubmitNodeCollector submitNodeCollector,
      @NotNull final CameraRenderState camera)
    {
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        poseStack.rotate(Axis.ZP.rotationDegrees(state.xRot + 90.0F));
        if (state.isFoil)
        {
            submitNodeCollector.submitModel(this.model,
              Unit.INSTANCE,
              poseStack,
              RenderTypes.entitySolidGlint(this.texture),
              state.lightCoords,
              OverlayTexture.NO_OVERLAY,
              state.outlineColor);
        }
        else
        {
            submitNodeCollector.submitModel(this.model,
              Unit.INSTANCE,
              poseStack,
              this.texture,
              state.lightCoords,
              OverlayTexture.NO_OVERLAY,
              state.outlineColor);
        }
        poseStack.popPose();
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
