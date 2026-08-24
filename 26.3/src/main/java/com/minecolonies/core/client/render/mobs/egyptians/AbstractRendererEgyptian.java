package com.minecolonies.core.client.render.mobs.egyptians;

import com.minecolonies.api.client.render.modeltype.RaiderRenderState;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import com.minecolonies.core.client.render.RenderUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Abstract for rendering Egyptians.
 * <p>
 * PORT-26.2: the renderer is state driven now. {@code ItemInHandLayer} and the head/wings layers come from
 * {@link HumanoidMobRenderer} itself, so only the armour layer is added here, and the arm pose moved from the removed
 * {@code render(...)} into {@link #getArmPose}, which vanilla calls while extracting the render state.
 */
public abstract class AbstractRendererEgyptian<T extends AbstractEntityMinecoloniesMonster, M extends HumanoidModel<RaiderRenderState>>
  extends HumanoidMobRenderer<T, RaiderRenderState, M>
{
    public AbstractRendererEgyptian(final EntityRendererProvider.Context context, final M modelBipedIn, final float shadowSize)
    {
        super(context, modelBipedIn, shadowSize);
        this.addLayer(new HumanoidArmorLayer<>(this,
          ArmorModelSet.<HumanoidModel<RaiderRenderState>>bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
          context.getEquipmentRenderer()));
    }

    @Override
    public RaiderRenderState createRenderState()
    {
        return new RaiderRenderState();
    }

    @Override
    public void extractRenderState(final T entity, final RaiderRenderState state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        state.textureId = entity.getTextureId();
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(final T raider, final HumanoidArm arm)
    {
        return RenderUtils.getArmPose(raider, arm);
    }
}
