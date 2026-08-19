package com.minecolonies.core.client.render.mobs;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.model.MercenaryModel;
import com.minecolonies.core.event.ClientRegistryHandler;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * Renderer for EntityMercenary.
 * <p>
 * PORT-26.2: renderers are render-state driven now, and the item-in-hand / custom-head layers already come with
 * {@link HumanoidMobRenderer}, so only the armour layer stays. The entity type narrowed from {@code PathfinderMob} to
 * {@link Mob} because {@code HumanoidMobRenderer} is bound to {@code Mob}; the only entity registered against this
 * renderer is {@code EntityMercenary}, which is a {@code PathfinderMob} anyway.
 */
public class RenderMercenary extends HumanoidMobRenderer<Mob, HumanoidRenderState, MercenaryModel>
{
    /**
     * Texture of the entity.
     */
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/entity/citizen/default/settlermale1_b.png");

    /**
     * Renders the mercenary mobs, with an held item and armorset.
     *
     * @param context RenderManager
     */
    public RenderMercenary(final EntityRendererProvider.Context context)
    {
        super(context, new MercenaryModel(context.bakeLayer(ClientRegistryHandler.MERCENARY)), 0.5f);

        this.addLayer(new HumanoidArmorLayer<>(this,
          ArmorModelSet.<HumanoidModel<HumanoidRenderState>>bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), HumanoidModel::new),
          context.getEquipmentRenderer()));
    }

    @Override
    public HumanoidRenderState createRenderState()
    {
        return new HumanoidRenderState();
    }

    @Override
    public Identifier getTextureLocation(final HumanoidRenderState state)
    {
        return TEXTURE;
    }
}
