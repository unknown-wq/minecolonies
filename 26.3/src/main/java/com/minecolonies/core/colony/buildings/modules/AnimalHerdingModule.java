package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IHasRequiredItemsModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.crafting.GenericRecipe;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.util.FoodUtils;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingHospital;
import com.minecolonies.core.colony.crafting.CustomRecipeManager;
import com.minecolonies.core.colony.crafting.LootTableAnalyzer;
import com.minecolonies.core.entity.ai.animals.AnimalPen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import com.minecolonies.api.util.Tuple;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;
import static com.minecolonies.api.util.constant.TranslationConstants.HERDER_MENU_ADDED_MESSAGE;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.RESTAURANT_MENU;

/**
 * Provides some basic definitions used by the animal herding AI (and JEI).
 */
public class AnimalHerdingModule extends AbstractBuildingModule implements IHasRequiredItemsModule, ITickingModule
{
    private final JobEntry jobEntry;
    private final Predicate<Animal> animalPredicate;
    private final ItemStorage breedingItem;

    /**
     * The animals this hut keeps in a pen: the ones free mode conjures into it, and the ones {@link AnimalPen} holds
     * inside it.
     * <p>
     * Declared per hut rather than derived from {@link #animalPredicate}, because a predicate can only be asked about
     * an animal that already exists. The mod does own a way to invert one - {@code RecipeAnalyzer#createAnimals}
     * instantiates every registered creature and tests the predicate against each - but that is a datagen and JEI
     * path, and running it to answer "what does a cowboy hut keep" would build a hundred throwaway entities.
     * <p>
     * An empty list opts a hut out of both halves of the feature, which is how the beekeeper stays out of it: bees
     * live in hives with their own homing and are never over-bred or butchered, so a pen is meaningless for them.
     */
    private final List<EntityType<? extends Animal>> penAnimals;

    @SafeVarargs
    public AnimalHerdingModule(@NotNull final JobEntry jobEntry,
                               @NotNull final Predicate<Animal> animalPredicate,
                               @NotNull final ItemStorage breedingItem,
                               final EntityType<? extends Animal>... penAnimals)
    {
        this.jobEntry = jobEntry;
        this.animalPredicate = animalPredicate;
        this.breedingItem = breedingItem;
        this.penAnimals = List.of(penAnimals);
    }

    /**
     * The animals this hut is for, in the order a stocking pass may pick from.
     *
     * @return the types, empty if this hut keeps no pen.
     */
    @NotNull
    public List<EntityType<? extends Animal>> getPenAnimals()
    {
        return penAnimals;
    }

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        AnimalPen.onColonyTick(this);
        publishProduceToMenus(colony);
    }

    /**
     * Put the meat this hut's animals yield on the colony's restaurant menus.
     * <p>
     * Same shape, and for the same reason, as {@code BuildingBaker#publishProduceToMenus}: a restaurant whose menu
     * is empty orders no food at all, so a player who builds a herder hut without also typing the dish into the
     * restaurant window by hand ends up with a hut full of raw beef and citizens who never see it. The hut that
     * produces the food is the one that knows about it, so it offers it.
     * <p>
     * Offered to every restaurant in the colony rather than the nearest one. The menu is per building and a citizen
     * eats at whichever restaurant is closest to it ({@code FoodUtils#getBestFoodForCitizen}), so "nearest to the
     * hut" would leave the other half of a two-restaurant colony without the dish for no reason the player can see;
     * and "nearest" is not stable either, since building another restaurant would silently move the choice. A
     * player who does not want the dish at one particular restaurant removes it there, and
     * {@link RestaurantMenuModule#offerMenuItem} never puts a removed item back.
     * <p>
     * Driven from the colony tick, not from a build-complete or upgrade hook, and deliberately so: this has to work
     * whichever order the two buildings are finished in. A hook on the hut alone covers "hut, then restaurant" and
     * nothing else -- the restaurant that appears later would never be told. The colony tick covers both orders,
     * plus saves that predate this code, plus a hut whose level changed, with one code path and no second hook on
     * the restaurant side. It is cheap: a handful of cached loot drops and one smelting lookup per animal.
     *
     * @param colony the colony this hut belongs to.
     */
    private void publishProduceToMenus(@NotNull final IColony colony)
    {
        if (building == null || building.getBuildingLevel() <= 0)
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
                    MessageUtils.format(HERDER_MENU_ADDED_MESSAGE, stack.getHoverName(), other.getBuildingDisplayName())
                      .sendTo(colony).forAllPlayers();
                }
            }
        }
    }

    /**
     * The servable dishes the animals in this pen yield.
     * <p>
     * Cooked, never raw, and that is not a preference: {@link FoodUtils#EDIBLE} is
     * {@code ISFOOD && !ISCOOKABLE}, so raw beef fails it and {@link RestaurantMenuModule#offerMenuItem} would
     * throw it straight back. The whole chain downstream agrees -- {@code EntityAIWorkCook#isSmeltable} smelts a
     * raw stack only while the *cooked* form is on the menu, {@code RestaurantMenuModule#onColonyTick} orders the
     * raw input of a menu item's smelting recipe on its own, and {@code FoodUtils#canEat} refuses raw food to a
     * citizen. Putting the cooked dish on the menu is therefore what makes the cook cook: it is the one entry that
     * pulls the raw meat in, gets it into the furnace, and ends up eaten.
     * <p>
     * Derived from the pen animals' loot tables rather than named per hut, the way the bakery derives its offer
     * from the recipes it has actually learned. That keeps a data pack that retunes a drop honest, and it is what
     * keeps the stable out of this without a special case: a horse drops leather, leather is not food and does not
     * smelt into food, so the stable offers nothing. The drops come from
     * {@link CustomRecipeManager#getLootDrops}, which already analyses exactly these tables for JEI and is rebuilt
     * on every data pack reload.
     *
     * @return the dishes to offer, possibly empty.
     */
    @NotNull
    private List<ItemStack> getEdibleProduce()
    {
        final List<ItemStack> produce = new ArrayList<>();
        for (final EntityType<? extends Animal> type : penAnimals)
        {
            final ResourceKey<LootTable> lootTable = type.getDefaultLootTable().orElse(null);
            if (lootTable == null)
            {
                continue;
            }

            for (final LootTableAnalyzer.LootDrop drop : CustomRecipeManager.getInstance().getLootDrops(lootTable))
            {
                for (final ItemStack dropStack : drop.getItemStacks())
                {
                    final ItemStack dish = toServableDish(dropStack);
                    if (!dish.isEmpty())
                    {
                        produce.add(dish);
                    }
                }
            }
        }
        return produce;
    }

    /**
     * Turn one loot drop into the dish a restaurant can serve, if there is one.
     *
     * @param drop the raw drop.
     * @return the dish, or empty if this drop is not food and does not cook into food.
     */
    @NotNull
    private static ItemStack toServableDish(@NotNull final ItemStack drop)
    {
        if (drop.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        if (FoodUtils.EDIBLE.test(drop))
        {
            return drop.copyWithCount(1);
        }

        if (ItemStackUtils.ISCOOKABLE.test(drop))
        {
            final ItemStack cooked = MinecoloniesAPIProxy.getInstance().getFurnaceRecipes().getSmeltingResult(drop);
            if (!cooked.isEmpty() && FoodUtils.EDIBLE.test(cooked))
            {
                return cooked.copyWithCount(1);
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Gets the herding job associated with this module.
     *
     * @return The job.
     */
    @NotNull
    public IJob<?> getHerdingJob()
    {
        return jobEntry.produceJob(null);
    }

    /**
     * Check if this module handles the particular animal.
     *
     * @param animal the animal to check.
     * @return true if so.
     */
    public boolean isCompatible(@NotNull final Animal animal)
    {
        return animalPredicate.test(animal);
    }

    /**
     * Gets the item required to breed the animal.
     *
     * @return The animal's preferred breeding item (as a list of alternatives).
     */
    @NotNull
    public List<ItemStorage> getBreedingItems()
    {
        return Collections.singletonList(breedingItem);
    }

    /**
     * Gets a list of loot tables that should be available for drop
     * analysis.  This is not intended for actually generating loot,
     * just for display purposes such as in JEI (e.g. via {@link #getRecipesForDisplayPurposesOnly}).
     *
     * @param animal An example animal. (Don't use specific properties of this; it's only for checking type.)
     * @return The list of loot table ids
     */
    public List<ResourceKey<LootTable>> getLootTables(@NotNull final Animal animal)
    {
        return animal.getLootTable().map(Collections::singletonList).orElseGet(Collections::emptyList);
    }

    @Override
    public Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> getRequiredItemsAndAmount()
    {
        final Map<Predicate<ItemStack>, Tuple<Integer, Boolean>> map = new HashMap<>();
        map.put(itemStack -> ItemStackUtils.hasEquipmentLevel(itemStack, ModEquipmentTypes.axe.get(), TOOL_LEVEL_WOOD_OR_GOLD, building.getMaxEquipmentLevel()), new Tuple<>(1, true));
        map.put(stack -> getBreedingItems().contains(new ItemStorage(stack)), new Tuple<>(1, true));
        return map;
    }

    @Override
    public Map<ItemStorage, Integer> reservedStacksExcluding(final @Nullable IRequest<? extends IDeliverable> excluded)
    {
        return Collections.emptyMap();
    }

    /**
     * Get a list of "recipes" for items obtainable by herding the given animal.  This can include loot drops
     * for killing the animal as well as anything else acquired through other means.
     *
     * These are purely for JEI display purposes and don't have to represent actual crafting recipes.
     *
     * @param animal An example animal. (Don't use specific properties of this; it's only for checking type.)
     * @return the list of additional display recipes.
     */
    @NotNull
    public List<IGenericRecipe> getRecipesForDisplayPurposesOnly(@NotNull final Animal animal)
    {
        return List.of(GenericRecipe.builder()
                .withRecipeId(BuiltInRegistries.ENTITY_TYPE.getKey(animal.getType()))
            .withInputs(List.of(getBreedingItems().stream().map(ItemStorage::getItemStack).toList()))
            .withLootTable(animal.getLootTable().orElse(null))
            .withRequiredTool(ModEquipmentTypes.axe.get())
            .withRequiredEntity(animal.getType())
            .build());
    }
}
