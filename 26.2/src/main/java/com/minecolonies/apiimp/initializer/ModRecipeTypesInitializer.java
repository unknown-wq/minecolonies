package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.crafting.ClassicRecipe;
import com.minecolonies.api.crafting.ModRecipeTypes;
import com.minecolonies.api.crafting.MultiOutputRecipe;
import com.minecolonies.api.crafting.registry.RecipeTypeEntry;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;

public final class ModRecipeTypesInitializer
{

    private ModRecipeTypesInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModRecipeTypesInitializer but this is a Utility class.");
    }

    static
    {
        ModRecipeTypes.Classic = register(ModRecipeTypes.CLASSIC_ID.getPath(), () -> new RecipeTypeEntry.Builder()
                                .setRecipeTypeProducer(ClassicRecipe::new)
                                .setRegistryName(ModRecipeTypes.CLASSIC_ID)
                                .createRecipeTypeEntry());

        ModRecipeTypes.MultiOutput = register(ModRecipeTypes.MULTI_OUTPUT_ID.getPath(), () -> new RecipeTypeEntry.Builder()
                                .setRecipeTypeProducer(MultiOutputRecipe::new)
                                .setRegistryName(ModRecipeTypes.MULTI_OUTPUT_ID)
                                .createRecipeTypeEntry());
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends RecipeTypeEntry> Supplier<T> register(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.RECIPE_TYPE_REGISTRY,
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
