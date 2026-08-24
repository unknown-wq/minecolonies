package com.minecolonies.core.client.render.mobs.barbarians;

import com.minecolonies.api.client.render.modeltype.RaiderRenderState;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import com.minecolonies.core.client.render.mobs.RaiderBodyLayers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer used for Barbarians And Archer Barbarians.
 */
public class RendererBarbarian extends AbstractRendererBarbarian<AbstractEntityMinecoloniesMonster, HumanoidModel<RaiderRenderState>>
{
    /**
     * Texture of the entity.
     */
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/barbarian1.png");

    /**
     * Constructor method for renderer
     *
     * @param context the renderManager
     */
    public RendererBarbarian(final EntityRendererProvider.Context context)
    {
        super(context, new HumanoidModel<>(RaiderBodyLayers.outerBody()), 0.5F);
    }


    @Override
    public Identifier getTextureLocation(@NotNull final RaiderRenderState state)
    {
        return TEXTURE;
    }
}
