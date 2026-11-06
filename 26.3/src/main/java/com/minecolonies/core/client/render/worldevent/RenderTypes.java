package com.minecolonies.core.client.render.worldevent;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * Render types used by MineColonies' own world rendering.
 * <p>
 * The citizen status icon used to have a mod-registered pipeline (entity format, emissive, no depth test). Shader
 * mods such as Iris only know the vanilla pipelines, so that unknown pipeline was drawn with a mismatched vertex
 * layout and smeared long streaks across the sky whenever a citizen with an icon was near the camera. The icon now
 * goes through vanilla's see-through text pipeline instead, the one name tags use behind walls: translucent, no depth
 * test, and already supported by every shader pack.
 */
public class RenderTypes
{
    /**
     * Usable for rendering simple flat textures that stay visible through terrain. The vertex format is that of text:
     * position, color, uv and light, with no overlay or normal.
     *
     * @param resLoc texture location
     * @return render type
     */
    public static RenderType worldEntityIcon(final Identifier resLoc)
    {
        return net.minecraft.client.renderer.rendertype.RenderTypes.textSeeThrough(resLoc);
    }
}
