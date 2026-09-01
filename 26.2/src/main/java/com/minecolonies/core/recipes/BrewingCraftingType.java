package com.minecolonies.core.recipes;

import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.compatibility.ICompatibilityManager;
import com.minecolonies.api.crafting.GenericRecipe;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.registry.CraftingType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A crafting type for brewing recipes
 */
public class BrewingCraftingType extends CraftingType
{
    public BrewingCraftingType()
    {
        super(ModCraftingTypes.BREWING_ID);
    }

    @Override
    @NotNull
    public List<IGenericRecipe> findRecipes(@NotNull RecipeManager recipeManager, @Nullable Level world)
    {
        final List<IGenericRecipe> recipes = new ArrayList<>();
        final ICompatibilityManager compatibilityManager = MinecoloniesAPIProxy.getInstance().getColonyManager().getCompatibilityManager();

        final List<ItemStack> containers = compatibilityManager.getListOfAllItems().stream()
                // 26.2: PotionBrewing#isInput split into isContainerIngredient/isPotionIngredient;
                // the brewing-stand bottle slot accepts either, which is what isInput used to mean.
                .filter(stack -> world.potionBrewing().isContainerIngredient(stack)
                                   || world.potionBrewing().isPotionIngredient(stack))
                .toList();
        final List<ItemStack> ingredients = compatibilityManager.getListOfAllItems().stream()
                .filter(world.potionBrewing()::isIngredient)
                .toList();

        for (final ItemStack container : containers)
        {
            for (final ItemStack ingredient : ingredients)
            {
                final ItemStack output = world.potionBrewing().mix(ingredient, container);
                if (!output.isEmpty() && output != container)
                {
                    // A brewing stand brews all three bottles from a single reagent. The bottle comes first: the
                    // teaching GUI, the AI that loads the stand and the JEI display all read input 0 as the bottle
                    // and input 1 as the reagent, and this enumerator is the only other thing that builds a brewing
                    // recipe.
                    recipes.add(GenericRecipe.builder()
                            .withOutput(output.copyWithCount(3))
                            .withInputs(List.of(List.of(container.copyWithCount(3)), List.of(ingredient)))
                            .withIntermediate(Blocks.BREWING_STAND)
                            .build());
                }
            }
        }

        return recipes;
    }
}
