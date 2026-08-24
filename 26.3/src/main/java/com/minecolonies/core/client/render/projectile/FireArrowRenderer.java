package com.minecolonies.core.client.render.projectile;

import com.minecolonies.api.util.constant.Constants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.state.ArrowRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.NotNull;

/**
 * Custom renderer for the fire arrows.
 * <p>
 * PORT-26.2: {@code ArrowRenderer} gained a render-state type parameter.
 */
@Environment(EnvType.CLIENT)
public class FireArrowRenderer extends ArrowRenderer<AbstractArrow, FireArrowRenderer.FireArrowRenderState>
{
    /**
     * Array of different textures.
     */
    private static final Identifier[] RES = new Identifier[]
                                                    {
                                                      Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow1.png"),
                                                      Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow2.png"),
                                                      Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow3.png"),
                                                      Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow4.png"),
                                                      Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow5.png"),
                                                      Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/magicalarrows/magical_arrow6.png")
                                                    };

    public FireArrowRenderer(final EntityRendererProvider.Context context)
    {
        super(context);
    }

    @NotNull
    @Override
    public FireArrowRenderState createRenderState()
    {
        return new FireArrowRenderState();
    }

    @Override
    public void extractRenderState(final AbstractArrow entity, final FireArrowRenderState state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        state.textureIndex = entity.tickCount % RES.length;
    }

    @NotNull
    @Override
    protected Identifier getTextureLocation(@NotNull final FireArrowRenderState state)
    {
        return RES[state.textureIndex];
    }

    /**
     * PORT-26.2: {@code getTextureLocation} only sees the render state, so the animation frame is copied there.
     */
    @Environment(EnvType.CLIENT)
    public static class FireArrowRenderState extends ArrowRenderState
    {
        public int textureIndex = 0;
    }
}
