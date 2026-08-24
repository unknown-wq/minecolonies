package com.minecolonies.core.client.render;

import com.minecolonies.api.util.constant.Constants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;

/**
 * Renderer of the spear item in inventories and in hand.
 * <p>
 * TODO(port-26.2): DISABLED — this was a {@code BlockEntityWithoutLevelRenderer} plugged in through the NeoForge
 * {@code IClientItemExtensions#getCustomRenderer} hook. 26.2 removed the class entirely and Fabric has no equivalent
 * hook; the 26.2 replacement is a {@code SpecialModelRenderer} declared from {@code assets/minecolonies/items/spear.json}
 * with {@code "minecraft:special"}, which is a datagen change rather than a client one. Until then the spear item uses
 * its flat item model instead of the 3-D spear model (the thrown entity is unaffected, see {@code RendererSpear}).
 */
@Environment(EnvType.CLIENT)
public class SpearItemTileEntityRenderer
{
    /**
     * Texture of the spear.
     */
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/entity/spear.png");

    private SpearItemTileEntityRenderer()
    {
        // disabled
    }
}
