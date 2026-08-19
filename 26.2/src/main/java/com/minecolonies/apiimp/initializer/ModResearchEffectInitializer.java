package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.research.ModResearchEffects;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.research.GlobalResearchEffect;
import net.minecraft.resources.Identifier;


import static com.minecolonies.api.research.ModResearchEffects.*;
import net.minecraft.core.Registry;
import java.util.function.Supplier;

/**
 * Registry initializer for the {@link ModResearchEffects}.
 */
public class ModResearchEffectInitializer
{
    static
    {
        globalResearchEffect = create(GLOBAL_EFFECT_ID, GlobalResearchEffect::new);
    }
    private ModResearchEffectInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModResearchEffectInitializer but this is a Utility class.");
    }

    /**
     * Utility method to aid in the creation of a research effect.
     *
     * @param registryName the registry name for this entry.
     * @param readFromNBT  function to read this item from json.
     * @return the finalized registry object.
     */
    private static Supplier<ResearchEffectEntry> create(
        final Identifier registryName,
        final ReadFromNBTFunction readFromNBT)
    {
        return register(registryName.getPath(), () -> new ResearchEffectEntry(registryName, readFromNBT));
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends ResearchEffectEntry> Supplier<T> register(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.RESEARCH_EFFECT_REGISTRY,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1); calling this from
     * the mod entry point is what pins the moment it happens.
     */
    public static void init()
    {
    }
}
