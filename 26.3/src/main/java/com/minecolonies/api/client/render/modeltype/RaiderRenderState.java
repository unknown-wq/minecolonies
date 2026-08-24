package com.minecolonies.api.client.render.modeltype;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * Render state shared by every MineColonies raider renderer.
 * <p>
 * PORT-26.2: {@code EntityRenderer#getTextureLocation} only sees the render state, so the per-entity texture index has
 * to be copied here while extracting.
 */
@Environment(EnvType.CLIENT)
public class RaiderRenderState extends HumanoidRenderState
{
    /**
     * Texture variant index of the raider ({@code AbstractEntityMinecoloniesMonster#getTextureId()}).
     */
    public int textureId = 0;
}
