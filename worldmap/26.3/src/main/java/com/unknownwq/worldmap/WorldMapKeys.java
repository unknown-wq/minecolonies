package com.unknownwq.worldmap;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * The mod's single key binding: M opens the map.
 *
 * <p>Two 26.3 shapes worth knowing, both the same ones {@code ModKeyMappings} in the MineColonies tree
 * documents. {@link KeyMapping}'s constructor takes a {@link KeyMapping.Category} record rather than a
 * category string, and the category has to be registered once through
 * {@link KeyMapping.Category#register(Identifier)} -- registering the same id twice throws. And
 * {@code InputConstants.Type.KEYSYM} is now {@code Type.KEYBOARD}: input moved from GLFW to SDL, so the
 * key constants are SDL scancodes ({@code InputConstants.KEY_M} is 16, not GLFW's 77).</p>
 */
@Environment(EnvType.CLIENT)
public final class WorldMapKeys
{
    private static final KeyMapping.Category CATEGORY =
      KeyMapping.Category.register(Identifier.fromNamespaceAndPath(WorldMapClient.MOD_ID, "general"));

    private static KeyMapping openMap;

    /**
     * Registers the bindings. Must run from the client entrypoint, before the options screen is first built.
     */
    public static void register()
    {
        openMap = KeyMappingHelper.registerKeyMapping(
          new KeyMapping("key.worldmap.open", InputConstants.Type.KEYBOARD, InputConstants.KEY_M, CATEGORY));
    }

    /**
     * @return the "open map" binding, or null if {@link #register()} has not run yet.
     */
    public static KeyMapping openMap()
    {
        return openMap;
    }

    private WorldMapKeys()
    {
        /*
         * Intentionally left empty.
         */
    }
}
