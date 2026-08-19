package com.minecolonies.core.util;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.compatibility.IFurnaceRecipes;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.RecipeStorage;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class FurnaceRecipes implements IFurnaceRecipes
{
    /**
     * Furnace recipes.
     */
    private Map<ItemStorage, RecipeStorage> recipes = new HashMap<>();
    private Map<ItemStorage, RecipeStorage> reverseRecipes = new HashMap<>();

    /**
     * Load all the recipes in the recipe storage.
     *
     * @param recipeManager  The recipe manager to parse.
     */
    public void loadRecipes(final RecipeManager recipeManager, final Level level)
    {
        recipes.clear();
        reverseRecipes.clear();
        // 26.2: RecipeManager#getAllRecipesFor is gone -- filter the full recipe collection by type instead.
        // A cooking recipe also no longer exposes getIngredients()/getResultItem(RegistryAccess): it carries a
        // single Ingredient (input()) and its result is produced by assemble(SingleRecipeInput).
        recipeManager.getRecipes().stream()
          .filter(holder -> holder.value().getType() == RecipeType.SMELTING)
          .forEach(holder -> {
            final SmeltingRecipe recipe = (SmeltingRecipe) holder.value();
            for (final Holder<Item> smeltableItem : recipe.input().items().toList())
            {
                final ItemStack smeltable = new ItemStack(smeltableItem);
                if (!smeltable.isEmpty())
                {
                    final ItemStack result = recipe.assemble(new SingleRecipeInput(smeltable));
                    final RecipeStorage storage = RecipeStorage.builder()
                            .withInputs(ImmutableList.of(new ItemStorage(smeltable)))
                            .withPrimaryOutput(result)
                            .withGridSize(1)
                            .withIntermediate(Blocks.FURNACE)
                            .withRecipeId(holder.id().identifier())
                            .build();

                    recipes.put(storage.getCleanedInput().get(0), storage);

                    final ItemStack output = result.copy();
                    output.setCount(1);
                    reverseRecipes.put(new ItemStorage(output), storage);
                }
            }
        });
    }

    @Override
    public ItemStack getSmeltingResult(final ItemStack itemStack)
    {
        final RecipeStorage storage = recipes.getOrDefault(new ItemStorage(itemStack), null);
        if (storage != null)
        {
            return storage.getPrimaryOutput();
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    @Override
    public RecipeStorage getFirstSmeltingRecipeByResult(final ItemStorage storage)
    {
        return reverseRecipes.get(storage);
    }
}
