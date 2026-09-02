package com.ldtteam.domumornamentum.recipe;

import com.ldtteam.domumornamentum.recipe.architectscutter.ArchitectsCutterRecipe;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.function.Supplier;

public class ModRecipeTypes
{
    public static Supplier<RecipeType<ArchitectsCutterRecipe>> ARCHITECTS_CUTTER = register("architects_cutter");

    /**
     * Class-load hook — registration happens eagerly in the static initialiser above (contract C1).
     */
    public static void init()
    {
    }

    /**
     * 26.2 has no {@code RecipeType.simple(Identifier)}; vanilla itself registers an anonymous instance
     * ({@code /opt/mc-src/net/minecraft/world/item/crafting/RecipeType.java:16-23}).
     */
    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> Supplier<RecipeType<T>> register(final String name)
    {
        final RecipeType<T> value = Registry.register(BuiltInRegistries.RECIPE_TYPE, Constants.resLocDO(name), new RecipeType<T>()
        {
            @Override
            public String toString()
            {
                return Constants.MOD_ID + ":" + name;
            }
        });
        return () -> value;
    }

    private ModRecipeTypes()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModRecipeTypes. This is a utility class");
    }
}
