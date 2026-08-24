package com.minecolonies.core.client.render.mobs.egyptians;

import com.minecolonies.api.client.render.modeltype.RaiderRenderState;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import com.minecolonies.core.client.model.raiders.ModelArcherMummy;
import com.minecolonies.core.event.ClientRegistryHandler;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Renderer used for archer mummy.
 */
public class RendererArcherMummy extends AbstractRendererEgyptian<AbstractEntityMinecoloniesMonster, ModelArcherMummy>
{
    /**
     * Texture of the entity.
     */
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("minecolonies", "textures/entity/raiders/archer_mummy.png");

    /**
     * Constructor method for renderer
     *
     * @param context the renderManager
     */
    public RendererArcherMummy(final EntityRendererProvider.Context context)
    {
        super(context, new ModelArcherMummy(context.bakeLayer(ClientRegistryHandler.ARCHER_MUMMY)), 0.5F);
    }

    @Override
    public Identifier getTextureLocation(@NotNull final RaiderRenderState state)
    {
        return TEXTURE;
    }
}
