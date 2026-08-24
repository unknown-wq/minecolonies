package com.minecolonies.core.recipes;

import com.minecolonies.api.crafting.GenericRecipe;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.registry.CraftingType;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.PotionIngredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A crafting type for brewing recipes.
 * <p>
 * 26.3: {@code PotionBrewing} and {@code Level#potionBrewing()} were removed outright -- brewing is now
 * an ordinary recipe type ({@link BrewingRecipe}, {@code RecipeType.BREWING}). Instead of probing every
 * known item against a hardcoded registry, we now simply enumerate the loaded brewing recipes, the same
 * way {@link ArchitectsCutterCraftingType} enumerates cutter recipes.
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

        for (final RecipeHolder<?> holder : recipeManager.getRecipes())
        {
            if (!(holder.value() instanceof final BrewingRecipe recipe))
            {
                continue;
            }

            // the bottle slot (water bottle / potion / splash potion / ...)
            final List<ItemStack> containers = expand(recipe.getInput());
            // the top slot (nether wart, redstone, gunpowder, ...)
            final List<ItemStack> reagents = expand(recipe.getReagent());
            if (containers.isEmpty() || reagents.isEmpty())
            {
                continue;
            }

            final ItemStack output = recipe.getOutput().create();
            if (output.isEmpty())
            {
                continue;
            }

            // a brewing stand brews all three bottles from a single reagent
            recipes.add(GenericRecipe.builder()
                    .withRecipeId(holder.id().identifier())
                    .withOutput(output.copyWithCount(output.getCount() * 3))
                    .withInputs(List.of(reagents,
                            containers.stream().map(stack -> stack.copyWithCount(3)).toList()))
                    .withIntermediate(Blocks.BREWING_STAND)
                    .build());
        }

        return recipes;
    }

    /**
     * Turns a {@link PotionIngredient} back into the concrete stacks it accepts, so that it can be shown
     * in the recipe list. The ingredient is an item set plus an optional potion-contents predicate; the
     * cross product of the two is what the brewing stand would actually accept.
     *
     * @param ingredient the recipe-side ingredient.
     * @return the matching stacks, possibly empty when the predicate is not enumerable.
     */
    @NotNull
    private static List<ItemStack> expand(@NotNull final PotionIngredient ingredient)
    {
        final List<ItemStack> stacks = new ArrayList<>();
        final List<Holder<Item>> items = ingredient.ingredient().items().toList();

        // Without a potion predicate this is a plain item ingredient such as nether wart. A predicate that
        // also constrains individual effects is not enumerable, so it degrades to the bare item as well.
        HolderSet<Potion> potions = null;
        if (ingredient.potions().isPresent() && ingredient.potions().get().effects().isEmpty())
        {
            potions = ingredient.potions().get().potions().orElse(null);
        }

        for (final Holder<Item> item : items)
        {
            if (potions == null)
            {
                stacks.add(new ItemStack(item));
                continue;
            }

            for (final Holder<Potion> potion : potions)
            {
                final ItemStack stack = new ItemStack(item);
                stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potion));
                stacks.add(stack);
            }
        }

        return stacks;
    }
}
