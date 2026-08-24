package com.minecolonies.core.client.render.mobs.drownedpirates;

import com.minecolonies.api.client.render.modeltype.RaiderRenderState;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import com.minecolonies.core.client.render.mobs.RaiderBodyLayers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.NotNull;

/**
 * Renderer used for Barbarians And Archer Barbarians.
 */
public class RendererDrownedArcherPirate extends AbstractRendererDrownedPirate<AbstractEntityMinecoloniesMonster, HumanoidModel<RaiderRenderState>>
{
    /**
     * Texture of the entity.
     */
    private static final Identifier TEXTURE1 = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/drowned_pirate5.png");
    private static final Identifier TEXTURE2 = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/drowned_pirate6.png");
    private static final Identifier TEXTURE3 = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/drowned_pirate7.png");
    private static final Identifier TEXTURE4 = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/drowned_pirate8.png");

    /**
     * Constructor method for renderer
     *
     * @param context the renderManager
     */
    public RendererDrownedArcherPirate(final EntityRendererProvider.Context context)
    {
        super(context, new HumanoidModel<>(RaiderBodyLayers.outerBody()), 0.5F);
    }

    @NotNull
    @Override
    public Identifier getTextureLocation(@NotNull final RaiderRenderState state)
    {
        switch (state.textureId)
        {
            case 0:
                return TEXTURE1;
            case 1:
                return TEXTURE2;
            case 2:
                return TEXTURE3;
            default:
                return TEXTURE4;
        }
    }
}
