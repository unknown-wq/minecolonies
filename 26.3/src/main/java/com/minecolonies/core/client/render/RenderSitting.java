package com.minecolonies.core.client.render;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Empty renderer for entities which should not actually be rendered.
 * <p>
 * PORT-26.2: {@code getTextureLocation} is gone from {@code EntityRenderer} (textures are chosen at submit time), and
 * every renderer has to supply a render state; a bare {@link EntityRenderState} is enough here because nothing is ever
 * drawn.
 *
 * @param <T> the entity that shall sit.
 */
public class RenderSitting<T extends Entity> extends EntityRenderer<T, EntityRenderState>
{
    public RenderSitting(final EntityRendererProvider.Context context)
    {
        super(context);
    }

    @NotNull
    @Override
    public EntityRenderState createRenderState()
    {
        return new EntityRenderState();
    }

    // 26.3: EntityRenderer#shouldRender gained a trailing partialTicks parameter (EntityRenderer.java:64).
    @Override
    public boolean shouldRender(@NotNull T entity, @NotNull Frustum clippingHelper, double x, double y, double z, float partialTicks)
    {
        return false;
    }
}
