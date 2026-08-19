package com.minecolonies.core.generation;

import com.minecolonies.api.crafting.CompostRecipe;
import net.fabricmc.fabric.api.recipe.v1.ingredient.DefaultCustomIngredients;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Datagen for {@link CompostRecipe}
 *
 * <p>Port note (26.2 / Fabric): NeoForge's {@code CompoundIngredient.of(...)} (a union of several ingredients,
 * serialised as a bare JSON array) has no vanilla counterpart. The Fabric equivalent is
 * {@link DefaultCustomIngredients#any(Ingredient...)} from {@code fabric-recipe-api-v1}, which serialises as
 * {@code {"fabric:type":"fabric:any","ingredients":[…]}}. The three generated {@code composting} recipes therefore
 * differ from the 1.21.1 oracle in the shape of their {@code "input"} field; the set of matched items is the same.</p>
 */
public class CompostRecipeBuilder
{
    private final List<Ingredient> inputs = new ArrayList<>();
    private final int strength;

    private CompostRecipeBuilder(final int strength)
    {
        this.strength = strength;
    }

    public static CompostRecipeBuilder strength(final int strength)
    {
        return new CompostRecipeBuilder(strength);
    }

    public CompostRecipeBuilder input(@NotNull final Ingredient ingredient)
    {
        this.inputs.add(ingredient);
        return this;
    }

    public void save(@NotNull final RecipeOutput consumer,
                     @NotNull final Identifier id)
    {
        final Ingredient combined = inputs.size() == 1
                                      ? inputs.get(0)
                                      : DefaultCustomIngredients.any(inputs.toArray(Ingredient[]::new));
        consumer.accept(ResourceKey.create(Registries.RECIPE, id), new CompostRecipe(combined, strength), null);
    }
}