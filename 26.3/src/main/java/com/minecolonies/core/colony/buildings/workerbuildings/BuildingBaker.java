package com.minecolonies.core.colony.buildings.workerbuildings;

import com.google.common.collect.ImmutableSet;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.crafting.AbstractCrafting;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.util.CraftingUtils;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.OptionalPredicate;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.AbstractCraftingBuildingModule;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import net.minecraft.core.BlockPos;
import com.minecolonies.api.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.TagConstants.CRAFTING_BAKER;
import static com.minecolonies.api.util.constant.TranslationConstants.BAKERY_MENU_ADDED_MESSAGE;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.BAKER_WORK;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.RESTAURANT_MENU;

/**
 * Building for the bakery.
 */
public class BuildingBaker extends AbstractBuilding
{
    /**
     * General bakery description key.
     */
    private static final String BAKER = "baker";

    /**
     * Max hut level of the bakery.
     */
    private static final int BAKER_HUT_MAX_LEVEL = 5;

    /**
     * Constructor for the bakery building.
     *
     * @param c Colony the building is in.
     * @param l Location of the building.
     */
    public BuildingBaker(final IColony c, final BlockPos l)
    {
        super(c, l);
    }

    /**
     * Gets the name of the schematic.
     *
     * @return Baker schematic name.
     */
    @NotNull
    @Override
    public String getSchematicName()
    {
        return BAKER;
    }

    /**
     * Gets the max level of the bakery's hut.
     *
     * @return The max level of the bakery's hut.
     */
    @Override
    public int getMaxBuildingLevel()
    {
        return BAKER_HUT_MAX_LEVEL;
    }

    @Override
    protected boolean keepFood()
    {
        return false;
    }

    @Override
    public void onColonyTick(final IColony colony)
    {
        super.onColonyTick(colony);
        publishProduceToMenus(colony);
    }

    /**
     * Put everything this bakery can bake on the colony's restaurant menus.
     * <p>
     * Nothing but the restaurant GUI used to write a menu, and a restaurant whose menu is empty orders no food at
     * all: the bakery filled the warehouse with bread while the citizens who could have eaten it went hungry,
     * because a player has to know to type the loaf in by hand. The bakery therefore offers its own output.
     * <p>
     * The offer is made from the recipes the bakery has actually learned, not from everything carrying the baker's
     * crafting tag. A tagged recipe the hut is too low a level to be taught, or that is still behind research, is
     * something this colony cannot make; putting it on a menu only produces orders nobody can fill. The learned
     * list moves with hut level, research and player teaching on its own, so a bakery gains menu entries as it
     * grows without anything else having to notice.
     * <p>
     * This runs on the colony tick rather than at a single moment such as "hut built" or "recipe learned", so a
     * bakery that was already standing when the save was loaded is covered by the same code as a new one.
     * {@link RestaurantMenuModule#offerMenuItem} decides what actually lands, and refuses anything a player took
     * off that menu by hand.
     *
     * @param colony the colony this bakery belongs to.
     */
    private void publishProduceToMenus(final IColony colony)
    {
        if (getBuildingLevel() <= 0)
        {
            return;
        }

        final List<ItemStack> produce = getEdibleProduce();
        if (produce.isEmpty())
        {
            return;
        }

        for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
        {
            if (!other.hasModule(RESTAURANT_MENU))
            {
                continue;
            }

            final RestaurantMenuModule menuModule = other.getModule(RESTAURANT_MENU);
            for (final ItemStack stack : produce)
            {
                if (menuModule.offerMenuItem(stack))
                {
                    MessageUtils.format(BAKERY_MENU_ADDED_MESSAGE, stack.getHoverName(), other.getBuildingDisplayName())
                      .sendTo(colony).forAllPlayers();
                }
            }
        }
    }

    /**
     * The edible results of every recipe this bakery knows.
     * <p>
     * Only primary outputs, and only food: the doughs and batters are inputs to the next step and the water bottle
     * is not a meal, so {@link FoodUtils#EDIBLE} does the filtering. Cake is left out by the same test, because a
     * cake is placed rather than eaten from the hand and no citizen can be served one.
     *
     * @return the food this bakery can produce, possibly empty.
     */
    private List<ItemStack> getEdibleProduce()
    {
        final List<ItemStack> produce = new ArrayList<>();
        for (final ICraftingBuildingModule module : getModulesByType(ICraftingBuildingModule.class))
        {
            for (final IToken<?> token : module.getRecipes())
            {
                final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
                if (storage == null)
                {
                    continue;
                }

                final ItemStack output = storage.getPrimaryOutput();
                if (!output.isEmpty() && FoodUtils.EDIBLE.test(output))
                {
                    produce.add(output);
                }
            }
        }
        return produce;
    }

    @Override
    public boolean canEat(final ItemStack stack)
    {
        if (stack.getItem() == Items.WHEAT)
        {
            return false;
        }

        final ICitizenData citizenData = getModule(BAKER_WORK).getFirstCitizen();
        if (citizenData != null)
        {
            final IRequest<? extends IRequestable> currentTask = ((AbstractJobCrafter<?, ?>) citizenData.getJob()).getCurrentTask();
            if (currentTask == null)
            {
                return super.canEat(stack);
            }
            final IRequestable request = currentTask.getRequest();
            if (request instanceof AbstractCrafting craftingRequest)
            {
                final IRecipeStorage recipe = IColonyManager.getInstance().getRecipeManager().getRecipe(craftingRequest.getRecipeID());
                if (recipe != null)
                {
                    if (recipe.getCleanedInput().contains(new ItemStorage(stack)))
                    {
                        return false;
                    }

                    if (ItemStack.isSameItem(recipe.getPrimaryOutput(), stack))
                    {
                        return false;
                    }
                }
            }
        }
        return super.canEat(stack);
    }

    public static class CraftingModule extends AbstractCraftingBuildingModule.Crafting
    {
        /**
         * Always try to keep at least 2 stacks of recipe inputs in the inventory and in the worker chest.
         */
        private static final int RECIPE_INPUT_HOLD = 128;

        /**
         * Cached ingredient hold-back map, and the learned-recipe list it was built from.
         */
        private Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> cachedRecipeInputs = Collections.emptyMap();

        private List<IToken<?>> cachedInputsForRecipes = null;

        /**
         * Create a new module.
         *
         * @param jobEntry the entry of the job.
         */
        public CraftingModule(final JobEntry jobEntry)
        {
            super(jobEntry);
        }

        @NotNull
        @Override
        public OptionalPredicate<ItemStack> getIngredientValidator()
        {
            return CraftingUtils.getIngredientValidatorBasedOnTags(CRAFTING_BAKER)
                    .combine(super.getIngredientValidator());
        }

        @Override
        public boolean isRecipeCompatible(@NotNull final IGenericRecipe recipe)
        {
            if (!super.isRecipeCompatible(recipe)) return false;
            final Optional<Boolean> isRecipeAllowed = CraftingUtils.isRecipeCompatibleBasedOnTags(recipe, CRAFTING_BAKER);
            return isRecipeAllowed.orElse(false);
        }

        @Override
        public Set<CraftingType> getSupportedCraftingTypes()
        {
            return (building == null || building.getBuildingLevel() >= 3)
                    ? super.getSupportedCraftingTypes()
                    : ImmutableSet.of();
        }

        @Override
        public Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> getRequiredItemsAndAmount()
        {
            final Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> map = super.getRequiredItemsAndAmount();
            map.putAll(getRecipeInputsToHold());
            return map;
        }

        /**
         * The "keep two stacks of every ingredient I know a recipe for" half of {@link #getRequiredItemsAndAmount()},
         * cached against the learned-recipe list.
         * <p>
         * The building asks for this map once per stack while a worker empties his inventory, and the bakery knows
         * around twenty-five recipes; rebuilding a predicate per ingredient every time made dumping cost far more
         * than it needed to. The learned-recipe list is the only input, so the cache is rebuilt exactly when that
         * list changes.
         *
         * @return the ingredients to hold back, mapped to the amount.
         */
        private Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> getRecipeInputsToHold()
        {
            final List<IToken<?>> current = getRecipes();
            if (cachedInputsForRecipes != null && cachedInputsForRecipes.equals(current))
            {
                return cachedRecipeInputs;
            }

            final Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> inputs = new HashMap<>();
            for (final IToken<?> token : current)
            {
                final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);
                // 26.2/Fabric: same unguarded lookup as the stale-token NPE in checkForWorkerSpecificRecipes, in the
                // other direction -- this one runs off the worker's tick, not the colony's, so it stops one baker
                // rather than the colony. The module's own colony tick prunes such a token, but the worker can get here
                // first after a load. Nothing to keep in stock for a recipe that no longer exists.
                if (storage == null)
                {
                    continue;
                }
                for (final ItemStorage itemStorage : storage.getCleanedInput())
                {
                    final ItemStack stack = itemStorage.getItemStack();
                    inputs.put(testStack -> ItemStack.isSameItem(stack, testStack), new Tuple<>(RECIPE_INPUT_HOLD, false));
                }
            }

            cachedInputsForRecipes = new ArrayList<>(current);
            cachedRecipeInputs = inputs;
            return inputs;
        }
    }

    public static class SmeltingModule extends AbstractCraftingBuildingModule.Smelting
    {
        /**
         * Create a new module.
         *
         * @param jobEntry the entry of the job.
         */
        public SmeltingModule(final JobEntry jobEntry)
        {
            super(jobEntry);
        }

        @NotNull
        @Override
        public OptionalPredicate<ItemStack> getIngredientValidator()
        {
            return CraftingUtils.getIngredientValidatorBasedOnTags(CRAFTING_BAKER)
                    .combine(super.getIngredientValidator());
        }

        @Override
        public boolean isRecipeCompatible(@NotNull final IGenericRecipe recipe)
        {
            if (!super.isRecipeCompatible(recipe))
            {
                return false;
            }
            return CraftingUtils.isRecipeCompatibleBasedOnTags(recipe, CRAFTING_BAKER).orElse(false);
        }

        @Override
        public Set<CraftingType> getSupportedCraftingTypes()
        {
            return (building == null || building.getBuildingLevel() >= 3)
                    ? super.getSupportedCraftingTypes()
                    : ImmutableSet.of();
        }
    }
}
