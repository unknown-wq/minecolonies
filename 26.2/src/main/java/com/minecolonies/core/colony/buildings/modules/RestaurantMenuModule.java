package com.minecolonies.core.colony.buildings.modules;

import com.google.common.reflect.TypeToken;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IAltersRequiredItems;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.MinimumStack;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.RecipeStorage;
import com.minecolonies.api.util.*;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.minecolonies.api.research.util.ResearchConstants.MIN_ORDER;
import static com.minecolonies.api.util.constant.Constants.STACKSIZE;
import net.minecraft.nbt.NbtOps;

/**
 * Minimum stock module.
 */
public class RestaurantMenuModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule, IAltersRequiredItems
{
    /**
     * Minimum stock it can hold per level.
     */
    public static final int STOCK_PER_LEVEL = 5;

    /**
     * The minimum stock tag.
     */
    private static final String TAG_MENU = "menu";

    /**
     * Tag for the items a player has taken off the menu by hand.
     */
    private static final String TAG_REFUSED = "menurefused";

    /**
     * The minimum stock.
     */
    protected final Set<ItemStorage> menu = new HashSet<>();

    /**
     * Everything a player has deliberately taken off this menu.
     * <p>
     * {@link #removeMenuItem} is only ever reached from the GUI, so every entry here is a decision somebody made.
     * {@link #offerMenuItem} honours it: a producer that keeps offering the same loaf must not be able to undo a
     * removal, or the button in the GUI would do nothing that lasts. Adding the item back by hand clears the entry.
     */
    protected final Set<ItemStorage> refused = new HashSet<>();

    /**
     * Whether the worker here can cook.
     */
    private final boolean                      canCook;

    /**
     * Get max stock calculation.
     */
    private final Function<IBuilding, Integer> expectedStock;

    /**
     * Get the restaurant menu.
     * <p>
     * Everything that decides whether a stack counts as servable food runs it past this menu -- the cook's
     * {@code FoodUtils#getBestFoodForCitizen} calls, its {@code canEat} check, and the eating task's {@code hasFood}.
     * The menu is filled by the player in the GUI ({@link #addMenuItem}) and by the colony's own food producers
     * ({@link #offerMenuItem}); in free mode the free meal is added here rather than to the stored menu, so the
     * switch needs no menu set up and turning it off leaves nothing behind.
     *
     * @return the menu.
     */
    public Set<ItemStorage> getMenu()
    {
        if (building instanceof final AbstractBuilding workBuilding && workBuilding.worksWithoutFood())
        {
            final Set<ItemStorage> withFreeFood = new HashSet<>(menu);
            withFreeFood.add(new ItemStorage(BuildingCook.FREE_FOOD));
            return withFreeFood;
        }
        return menu;
    }

    /**
     * Create a restaurant menu module.
     * @param canCook whether the worker here can cook.
     */
    public RestaurantMenuModule(final boolean canCook, final Function<IBuilding, Integer> expectedStock )
    {
        this.canCook = canCook;
        this.expectedStock = expectedStock;
    }

    /**
     * Add a new menu item.
     * @param itemStack the menu item to add.
     */
    public void addMenuItem(final ItemStack itemStack)
    {
        if (!FoodUtils.EDIBLE.test(itemStack))
        {
            Log.getLogger().warn("Tried to add nonedible food stack: " + itemStack);
            return;
        }

        if (menu.size() >= building.getBuildingLevel() * STOCK_PER_LEVEL)
        {
            return;
        }

        menu.add(new ItemStorage(itemStack));
        refused.remove(new ItemStorage(itemStack));
        markDirty();
    }

    /**
     * Offer a menu item on behalf of a building that produces it.
     * <p>
     * Unlike {@link #addMenuItem} this is not a player action, so it gives way to one: an item the player has taken
     * off the menu is not put back, and the per-level menu size limit is respected so an automatic offer can never
     * crowd out a hand-picked entry that comes later.
     *
     * @param itemStack the food being offered.
     * @return true if the menu actually changed.
     */
    public boolean offerMenuItem(final ItemStack itemStack)
    {
        final ItemStorage storage = new ItemStorage(itemStack);
        if (menu.contains(storage) || refused.contains(storage) || !FoodUtils.EDIBLE.test(itemStack))
        {
            return false;
        }

        if (menu.size() >= building.getBuildingLevel() * STOCK_PER_LEVEL)
        {
            return false;
        }

        menu.add(storage);
        markDirty();
        return true;
    }

    /**
     * Remove a menu item.
     * @param itemStack the menu item to remove.
     */
    public void removeMenuItem(final ItemStack itemStack)
    {
        final ItemStorage removed = new ItemStorage(itemStack);
        menu.remove(removed);
        refused.add(removed);

        // The orders this module files in onColonyTick are MinimumStack, and AbstractBuilding#addRequestToMaps
        // indexes an open request under its own concrete class, so a lookup under Stack.class matched nothing at
        // all: taking a dish off the menu left its delivery order standing for ever, because onColonyTick only ever
        // revisits items that are still on the menu. Measured on a dedicated server: the restaurant's open request
        // count was 3 before removeMenuItem and 3 after, and the type keys present were MinimumStack and StackList,
        // never Stack.
        final Collection<IToken<?>> list =
          new ArrayList<>(building.getOpenRequestsByRequestableType().getOrDefault(TypeToken.of(MinimumStack.class), new ArrayList<>()));
        cancelOrderFor(itemStack, list);

        // onColonyTick asks for the dish or for the raw input of its smelting recipe, whichever it happened to draw
        // that tick, so both have to go.
        if (canCook && MinecoloniesAPIProxy.getInstance().getFurnaceRecipes().getFirstSmeltingRecipeByResult(removed) instanceof RecipeStorage recipeStorage
              && !recipeStorage.getInput().isEmpty())
        {
            cancelOrderFor(recipeStorage.getInput().get(0).getItemStack(), list);
        }
        markDirty();
    }

    /**
     * Cancel the standing order for one stack, if there is one.
     *
     * @param stack the stack that is no longer wanted.
     * @param list  the tokens to search, a copy rather than the live collection because cancelling mutates it.
     */
    private void cancelOrderFor(final ItemStack stack, final Collection<IToken<?>> list)
    {
        final IToken<?> token = getMatchingRequest(stack, list);
        if (token != null)
        {
            building.getColony().getRequestManager().updateRequestState(token, RequestState.CANCELLED);
        }
    }

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        if (WorldUtil.isBlockLoaded(colony.getWorld(), building.getPosition()))
        {
            final Collection<IToken<?>> list = building.getOpenRequestsByRequestableType().getOrDefault(TypeToken.of(MinimumStack.class), new ArrayList<>());

            for (final ItemStorage menuItem : menu)
            {
                final ItemStack originalStack = menuItem.getItemStack().copy();
                if (originalStack.isEmpty())
                {
                    continue;
                }
                ItemStack requestStack = originalStack;
                ItemStack rawStack = ItemStack.EMPTY;
                if (canCook && MinecoloniesAPIProxy.getInstance().getFurnaceRecipes().getFirstSmeltingRecipeByResult(menuItem) instanceof RecipeStorage recipeStorage)
                {
                    // Smelting Recipes only got 1 input. Request sometimes the input if this is a smeltable.
                    rawStack = recipeStorage.getInput().get(0).getItemStack().copy();
                }

                final int target = originalStack.getMaxStackSize() * getExpectedStock();
                final int count = InventoryUtils.hasBuildingEnoughElseCount(this.building, new ItemStorage(originalStack, true), target);
                final int rawCount = rawStack.isEmpty() ? 0 : InventoryUtils.hasBuildingEnoughElseCount(this.building, new ItemStorage(rawStack, true), target);
                final int delta = target - count - rawCount;
                if (MathUtils.RANDOM.nextBoolean() && !rawStack.isEmpty())
                {
                    requestStack = rawStack.copy();
                }
                final IToken<?> request = getMatchingRequest(requestStack, list);
                if (delta > (building.getColony().getResearchManager().getResearchEffects().getEffectStrength(MIN_ORDER) > 0 ? target / 4 : 0))
                {
                    if (request == null)
                    {
                        final int qty = Math.min(STACKSIZE, Math.min(requestStack.getMaxStackSize(), delta));
                        final MinimumStack stack = new MinimumStack(requestStack, false, true, ItemStackUtils.EMPTY, qty, 1);

                        stack.setCanBeResolvedByBuilding(false);
                        building.createRequest(stack, true);
                    }
                }
                else if (request != null && delta <= 0)
                {
                    building.getColony().getRequestManager().updateRequestState(request, RequestState.CANCELLED);
                }
            }
        }
    }

    /**
     * Get the request from the list that matches this stack.
     *
     * @param stack the stack to search for in the requests.
     * @param list  the list of requests.
     * @return the token of the matching request or null.
     */
    private IToken<?> getMatchingRequest(final ItemStack stack, final Collection<IToken<?>> list)
    {
        for (final IToken<?> token : list)
        {
            final IRequest<?> iRequest = building.getColony().getRequestManager().getRequestForToken(token);
            if (iRequest != null && iRequest.getRequest() instanceof Stack && ItemStackUtils.compareItemStacksIgnoreStackSize(((Stack) iRequest.getRequest()).getStack(), stack))
            {
                return token;
            }
        }
        return null;
    }

    /**
     * Get the max stock in stacks per menu item.
     * @return the max stock.
     */
    public int getExpectedStock()
    {
        return expectedStock.apply(building);
    }

    @Override
    public void alterItemsToBeKept(final TriConsumer<Predicate<ItemStack>, Integer, Boolean> consumer)
    {
        for (final ItemStorage menuItem : menu)
        {
            consumer.accept(stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, menuItem.getItemStack(), false, true), menuItem.getItemStack().getMaxStackSize() * getExpectedStock(), false);
            if (canCook && MinecoloniesAPIProxy.getInstance().getFurnaceRecipes().getFirstSmeltingRecipeByResult(menuItem) instanceof RecipeStorage recipeStorage)
            {
                final ItemStack smeltStack = recipeStorage.getInput().get(0).getItemStack();
                consumer.accept(stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, smeltStack, false, true), smeltStack.getMaxStackSize() * getExpectedStock(), false);
            }
        }
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        menu.clear();
        final ListTag minimumStockTagList = compound.getListOrEmpty(TAG_MENU);
        for (int i = 0; i < minimumStockTagList.size(); i++)
        {
            final ItemStack itemStack = ItemStack.OPTIONAL_CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), minimumStockTagList.getCompoundOrEmpty(i)).result().orElse(ItemStack.EMPTY);
            if (FoodUtils.EDIBLE.test(itemStack))
            {
                menu.add(new ItemStorage(itemStack));
            }
        }

        // Absent on a save written before producers could offer menu items, which is what makes the migration work:
        // no refusals on record, so an existing restaurant -- empty menu or not -- gets the bakery's output on its
        // next colony tick. The one thing that cannot be recovered is a removal made before this tag existed; such
        // an item is offered once more and has to be removed again to stick.
        refused.clear();
        final ListTag refusedTagList = compound.getListOrEmpty(TAG_REFUSED);
        for (int i = 0; i < refusedTagList.size(); i++)
        {
            final ItemStack itemStack = ItemStack.OPTIONAL_CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), refusedTagList.getCompoundOrEmpty(i)).result().orElse(ItemStack.EMPTY);
            if (!itemStack.isEmpty())
            {
                refused.add(new ItemStorage(itemStack));
            }
        }
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        @NotNull final ListTag minimumStockTagList = new ListTag();
        for (final ItemStorage menuItem : menu)
        {
            minimumStockTagList.add(ItemStack.OPTIONAL_CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), menuItem.getItemStack()).getOrThrow());
        }
        compound.put(TAG_MENU, minimumStockTagList);

        @NotNull final ListTag refusedTagList = new ListTag();
        for (final ItemStorage refusedItem : refused)
        {
            refusedTagList.add(ItemStack.OPTIONAL_CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), refusedItem.getItemStack()).getOrThrow());
        }
        compound.put(TAG_REFUSED, refusedTagList);
    }

    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        // getMenu(), not the raw field: in free mode the free meal is added by getMenu() rather than stored, so
        // writing the field sends the client a menu the server does not actually use. The tooltip in
        // ClientEventHandler tests the stack against this view, and with the field it told the player their free
        // steak was "not on the dining hall's menu" while the citizen was happily eating it.
        final Set<ItemStorage> served = getMenu();
        buf.writeInt(served.size());
        for (final ItemStorage menuItem : served)
        {
            Utils.serializeCodecMess(buf, menuItem.getItemStack());
        }
    }
}
