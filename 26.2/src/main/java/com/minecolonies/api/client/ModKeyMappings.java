package com.minecolonies.api.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

/**
 * Key mappings.
 *
 * <p>NeoForge's {@code RegisterKeyMappingsEvent}, {@code KeyConflictContext} and {@code Lazy} are all gone
 * (contracts C4/C5). Fabric registers key mappings eagerly through {@code KeyMappingHelper}
 * (fabric-key-mapping-api-v1), and 26.2's {@link KeyMapping} constructor takes a {@link KeyMapping.Category}
 * record instead of a category string ({@code /opt/mc-src/net/minecraft/client/KeyMapping.java:94,206}).</p>
 *
 * <p>TODO(port-26.2): DISABLED — {@code KeyConflictContext.IN_GAME} has no counterpart, so the binding no longer
 * declares that it only conflicts with other in-game bindings; a conflict with a GUI-only binding is now reported.</p>
 */
@Environment(EnvType.CLIENT)
public class ModKeyMappings
{
    private static final KeyMapping.Category CATEGORY =
      KeyMapping.Category.register(Identifier.fromNamespaceAndPath("minecolonies", "general"));

    /**
     * Toggle. Kept as a {@link Supplier} so the {@code .get()} call sites keep compiling (contract C1).
     */
    public static final Supplier<KeyMapping> TOGGLE_GOGGLES = register(new KeyMapping("key.minecolonies.toggle_goggles",
      InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));

    /**
     * Class-load hook — registration happens eagerly in the static initialiser above.
     */
    public static void register()
    {
    }

    private static Supplier<KeyMapping> register(final KeyMapping mapping)
    {
        final KeyMapping value = KeyMappingHelper.registerKeyMapping(mapping);
        return () -> value;
    }

    /**
     * Private constructor to hide the implicit one.
     */
    private ModKeyMappings()
    {
        /*
         * Intentionally left empty.
         */
    }
}
