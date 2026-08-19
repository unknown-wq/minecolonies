package com.minecolonies.api.crafting.registry;

import com.minecolonies.api.crafting.ZeroWasteRecipe;
import com.minecolonies.api.crafting.CompostRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.function.Supplier;

/**
 * Holds ref to the mod recipe serializers and recipe types.
 */
public class ModRecipeSerializer
{
    public static Supplier<RecipeSerializer<CompostRecipe>> CompostRecipeSerializer;
    public static Supplier<RecipeType<CompostRecipe>> CompostRecipeType;

    public static Supplier<RecipeSerializer<ZeroWasteRecipe>> ZeroWasteRecipeSerializer;
}
