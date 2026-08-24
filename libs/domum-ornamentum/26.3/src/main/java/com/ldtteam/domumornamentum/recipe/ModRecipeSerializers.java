package com.ldtteam.domumornamentum.recipe;

import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.function.Supplier;

public class ModRecipeSerializers
{
    public static Supplier<RecipeSerializer<ArchitectsCutterRecipe>> ARCHITECTS_CUTTER =
        register("architects_cutter", new RecipeSerializer<>(ArchitectsCutterRecipe.CODEC, ArchitectsCutterRecipe.STREAM_CODEC));

    /**
     * Class-load hook — registration happens eagerly in the static initialiser above (contract C1).
     */
    public static void init()
    {
    }

    private static <T extends Recipe<?>> Supplier<RecipeSerializer<T>> register(final String name, final RecipeSerializer<T> serializer)
    {
        final RecipeSerializer<T> value = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, Constants.resLocDO(name), serializer);
        // Modded recipes are not part of the vanilla client/server recipe sync: without this the architect's
        // cutter menu is silently empty on the client while the server knows every recipe. Failure mode is
        // quiet - no log line, no exception, just a menu with nothing in it.
        RecipeSynchronization.synchronizeRecipeSerializer(value);
        return () -> value;
    }

    private ModRecipeSerializers()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModRecipeSerializers. This is a utility class");
    }
}
