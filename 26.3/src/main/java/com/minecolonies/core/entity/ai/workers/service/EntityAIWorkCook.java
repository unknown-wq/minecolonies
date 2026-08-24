package com.minecolonies.core.entity.ai.workers.service;

import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.requestsystem.requestable.Food;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.items.IMinecoloniesFoodItem;
import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.minecolonies.core.debug.FreeMode;
import com.minecolonies.api.util.*;
import com.minecolonies.api.util.constant.CitizenConstants;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.RestaurantMenuModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCook;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.jobs.JobCook;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIUsesFurnace;
import com.minecolonies.core.entity.citizen.EntityCitizen;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import com.minecolonies.api.inventory.api.IItemHandler;
import com.minecolonies.api.inventory.api.InvWrapper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Predicate;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.CitizenConstants.AVERAGE_SATURATION;
import static com.minecolonies.api.util.constant.CitizenConstants.FULL_SATURATION;
import static com.minecolonies.api.util.constant.Constants.*;
import static com.minecolonies.api.util.constant.StatisticsConstants.FOOD_SERVED;
import static com.minecolonies.api.util.constant.StatisticsConstants.FOOD_SERVED_DETAIL;
import static com.minecolonies.api.util.constant.TranslationConstants.*;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.RESTAURANT_MENU;

/**
 * Cook AI class.
 */
public class EntityAIWorkCook extends AbstractEntityAIUsesFurnace<JobCook, BuildingCook>
{
    /**
     * The amount of food which should be served to the worker.
     */
    public static final int SATURATION_TO_SERVE = 16;

    /**
     * Delay between each serving.
     */
    private static final int SERVE_DELAY = 30;

    /**
     * Level at which the cook should give some food to the player.
     */
    private static final int LEVEL_TO_FEED_PLAYER = 10;

    /**
     * The citizen the worker is currently trying to serve.
     */
    private final Queue<AbstractEntityCitizen> citizenToServe = new ArrayDeque<>();

    /**
     * The citizen the worker is currently trying to serve.
     */
    private final Queue<Player> playerToServe = new ArrayDeque<>();

    /**
     * How long free mode's cook waits for the colony's own stock before conjuring raw food, in ticks.
     * <p>
     * Two in-game minutes, a tenth of a day. The number is a courier round trip with room to spare: the standing
     * {@code MinimumStack} request is filed on a colony tick (500 ticks) and a deliveryman then has to be assigned,
     * walk to a warehouse, pick up and walk here, which on the test colony took about 700 ticks door to door. Waiting
     * a couple of those means an ordinary, merely slow delivery is never given up on, while a delivery that is never
     * coming costs the cook one furnace load of idling and no more - it conjures, cooks the stack, and only waits
     * again once that is gone.
     * <p>
     * Measured against {@code START_WORKING}'s own 60 tick delay this is at most forty passes through
     * {@link #supplyRawFood}, so the wait is bounded in attempts as well as in time however the state machine is
     * scheduled.
     */
    private static final int SUPPLY_DEFER_TICKS = 2400;

    /**
     * How long a gap between two empty larders counts as the drought having ended, in ticks.
     * <p>
     * Comfortably more than {@code START_WORKING}'s 60 tick delay plus the walk back to the hut, and far less than
     * {@link #SUPPLY_DEFER_TICKS}, so a continuous drought is never mistaken for two separate ones.
     */
    private static final int SUPPLY_DEFER_GAP_TICKS = 400;

    /**
     * Game time the current wait for the colony's own stock started, or -1 if the cook is not waiting.
     */
    private long deferSinceTick = -1;

    /**
     * Game time {@link #deferToColonyStock} last ran, to tell one drought from the next.
     */
    private long lastDeferTick = -1;

    /**
     * Cooking icon
     */
    private final static VisibleCitizenStatus COOK =
      new VisibleCitizenStatus(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/icons/work/cook.png"), "com.minecolonies.gui.visiblestatus.cook");

    /**
     * Constructor for the Cook. Defines the tasks the cook executes.
     *
     * @param job a cook job to use.
     */
    public EntityAIWorkCook(@NotNull final JobCook job)
    {
        super(job);
        super.registerTargets(
          new AITarget(COOK_SERVE_FOOD_TO_CITIZEN, this::serveFoodToCitizen, SERVE_DELAY),
          new AITarget(COOK_SERVE_FOOD_TO_PLAYER, this::serveFoodToPlayer, SERVE_DELAY)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<BuildingCook> getExpectedBuildingClass()
    {
        return BuildingCook.class;
    }

    /**
     * Very simple action, cook straightly extract it from the furnace.
     *
     * @param furnace the furnace to retrieve from.
     */
    @Override
    protected void extractFromFurnace(final FurnaceBlockEntity furnace)
    {
        InventoryUtils.transferItemStackIntoNextFreeSlotInItemHandler(
          new InvWrapper(furnace), RESULT_SLOT,
          worker.getInventoryCitizen());
        worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
        this.incrementActionsDoneAndDecSaturation();
    }

    @Override
    public IAIState startWorking()
    {
        return super.startWorking();
    }

    @Override
    protected boolean isSmeltable(final ItemStack stack)
    {
        //Only return true if the item isn't queued for a recipe.
        return ItemStackUtils.ISCOOKABLE.test(stack) && building.getModule(RESTAURANT_MENU).getMenu().contains(new ItemStorage(MinecoloniesAPIProxy.getInstance().getFurnaceRecipes().getSmeltingResult(stack)));
    }

    @Override
    protected boolean reachedMaxToKeep()
    {
        if (super.reachedMaxToKeep())
        {
            return true;
        }
        final int buildingLimit = Math.max(1, building.getBuildingLevel() * building.getBuildingLevel()) * SLOT_PER_LINE;
        return InventoryUtils.getCountFromBuildingWithLimit(building,
            FoodUtils.EDIBLE.and(stack -> FoodUtils.canEatLevel(stack, building.getBuildingLevel() - 1)),
          stack -> stack.getMaxStackSize() * 6) > buildingLimit;
    }

    @Override
    public void requestSmeltable()
    {
        final RestaurantMenuModule menuModule = building.getModule(RESTAURANT_MENU);

        if (worksWithoutMaterials() && supplyRawFood(menuModule))
        {
            return;
        }

        if (menuModule.getMenu().isEmpty() && worker.getCitizenData() != null)
        {
            worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(FURNACE_USER_NO_FOOD), ChatPriority.BLOCKING));
        }
    }

    /**
     * Put raw food into the hut so free mode's cook has something to cook.
     * <p>
     * The cook is the one furnace worker free mode never reached. Where {@code EntityAIWorkSmelter#requestSmeltable}
     * files a real request, {@link #requestSmeltable} here only raises an interaction - and free mode's interception
     * ({@code FreeMode#fulfil}, hooked into {@code StandardRetryingRequestResolver}) can only serve requests that
     * exist. Measured with five furnaces, fuel and free mode on: 96.9% of the working day in {@code START_WORKING},
     * and the cook went to {@code FILL_UP_FURNACES} the moment raw food was put in the hut by hand. This does that by
     * itself, the way {@code EntityAIWorkSifter#supplySiftingMaterials} does for the sifter.
     * <p>
     * The raw item is derived rather than named: for each item on the menu, the input of the smelting recipe that
     * produces it - the same lookup {@code RestaurantMenuModule#onColonyTick} uses to order raw food through the
     * request system. With no menu set at all, free mode has already put {@code BuildingCook#FREE_FOOD} (cooked beef)
     * on the menu ({@code RestaurantMenuModule#getMenu}), so the cook is handed the raw form of that.
     * <p>
     * Self limiting: {@code AbstractEntityAIUsesFurnace#startWorking} only calls here when neither hut nor inventory
     * holds anything smeltable and the cook is not already at its keep limit, so one stack arrives per empty larder
     * rather than per tick. Outside free mode nothing here runs.
     * <p>
     * The colony's own stock comes first. {@link FreeMode} conjures only what nothing in the colony can serve - that
     * is the whole shape of {@code FreeMode#fulfil}, which sits behind every real resolver - and a stack dropped
     * straight into the hut here would break that promise, because {@code RestaurantMenuModule#onColonyTick} has
     * already filed a real {@code MinimumStack} request for this very item and the conjured stack simply wins the
     * race against the courier. So when {@link #inColonyStock} finds the raw food, or the finished dish, sitting in
     * a warehouse, nothing is handed over and the standing request is left to be delivered. See
     * {@link #SUPPLY_DEFER_TICKS} for what happens when it never is.
     *
     * @param menuModule the restaurant menu.
     * @return true if raw food was handed over, or if the cook is waiting for the colony's own stock instead.
     */
    private boolean supplyRawFood(@NotNull final RestaurantMenuModule menuModule)
    {
        final List<ItemStack> raws = new ArrayList<>();
        boolean stocked = false;

        for (final ItemStorage menuItem : menuModule.getMenu())
        {
            final IRecipeStorage recipe = MinecoloniesAPIProxy.getInstance().getFurnaceRecipes().getFirstSmeltingRecipeByResult(menuItem);
            if (recipe == null || recipe.getInput().isEmpty())
            {
                continue;
            }

            final ItemStack raw = recipe.getInput().get(0).getItemStack();
            if (ItemStackUtils.isEmpty(raw))
            {
                continue;
            }

            raws.add(raw);
            // Either form is worth waiting for: the courier brings whichever the module happened to ask for that
            // tick, and a delivery of the finished dish feeds the citizens without the furnace being involved at all.
            stocked |= inColonyStock(raw) || inColonyStock(menuItem.getItemStack());
        }

        if (raws.isEmpty())
        {
            return false;
        }

        if (stocked && deferToColonyStock())
        {
            return true;
        }

        for (final ItemStack raw : raws)
        {
            if (InventoryUtils.addItemStackToProvider(building, raw.copyWithCount(raw.getMaxStackSize())))
            {
                deferSinceTick = -1;
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the colony has an item a courier could bring here.
     * <p>
     * Only warehouses count, and only loaded ones. A warehouse is where the request system takes a delivery from
     * ({@code WarehouseRequestResolver}), so a rack in some other hut is not stock the restaurant can expect to be
     * brought; and an unloaded warehouse is one whose racks {@code TileEntityWareHouse} would skip anyway, so it is
     * treated as empty rather than as a reason to keep waiting.
     *
     * @param stack what to look for, its own count is ignored.
     * @return true if at least one is there.
     */
    private boolean inColonyStock(@NotNull final ItemStack stack)
    {
        if (ItemStackUtils.isEmpty(stack))
        {
            return false;
        }

        for (final IWareHouse wareHouse : building.getColony().getServerBuildingManager().getWareHouses())
        {
            if (!WorldUtil.isBlockLoaded(world, wareHouse.getPosition()))
            {
                continue;
            }

            final AbstractTileEntityWareHouse tileEntity = wareHouse.getTileEntity();
            if (tileEntity != null && tileEntity.hasMatchingItemStackInWarehouse(stack, 1, true))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether to keep waiting for the colony's own stock rather than conjure.
     * <p>
     * The escape hatch, and the reason this guard cannot starve the cook. Seeing the item in a warehouse is not the
     * same as getting it: there may be no courier hired, the courier may have no warehouse it is allowed into, the
     * request may be parked behind another one, or the racks holding it may sit in a chunk that unloads a moment
     * later. None of those ever resolve on their own, and a cook that waited on them would be exactly the stall the
     * direct supply was written to fix. So the wait is bounded: once {@link #SUPPLY_DEFER_TICKS} have passed with the
     * larder still empty, the stack is conjured anyway.
     *
     * @return true if the cook should do nothing this tick.
     */
    private boolean deferToColonyStock()
    {
        final long now = world.getGameTime();
        if (deferSinceTick < 0 || now - lastDeferTick > SUPPLY_DEFER_GAP_TICKS)
        {
            // Either the first empty larder, or the first since food last arrived: the cook is only asked here while
            // it has nothing smeltable at all, so a gap between two calls means it spent that time cooking something
            // that had been delivered. That earns the colony a fresh wait - without this reset the timer would still
            // be expired from the previous drought and every later delivery would lose the race again.
            deferSinceTick = now;
        }
        lastDeferTick = now;

        if (now - deferSinceTick < SUPPLY_DEFER_TICKS)
        {
            return true;
        }

        deferSinceTick = -1;
        return false;
    }

    /**
     * Serve food to citizen.
     * @return next IAIState
     */
    private IAIState serveFoodToCitizen()
    {
        if (citizenToServe.isEmpty())
        {
            return START_WORKING;
        }

        worker.getCitizenData().setVisibleStatus(COOK);

        if (!building.isInBuilding(citizenToServe.peek().blockPosition()))
        {
            worker.getNavigation().stop();
            citizenToServe.poll();
            return getState();
        }

        if (!walkToWorkPos(citizenToServe.peek().blockPosition()))
        {
            return getState();
        }

        final AbstractEntityCitizen citizen = citizenToServe.poll();
        final InventoryCitizen handler = citizen.getInventoryCitizen();
        final RestaurantMenuModule module = worker.getCitizenData().getWorkBuilding().getModule(RESTAURANT_MENU);
        final Predicate<ItemStack> canEatPredicate = stack -> module.getMenu().contains(new ItemStorage(stack));
        final ICitizenData citizenData = citizen.getCitizenData();

        if (!handler.hasSpace())
        {
            for (int feedingAttempts = 0; feedingAttempts < 10; feedingAttempts++)
            {
                final int foodSlot = FoodUtils.getBestFoodForCitizen(worker.getInventoryCitizen(), citizenData, module.getMenu());
                if (foodSlot != -1)
                {
                    final ItemStack stack = worker.getInventoryCitizen().extractItem(foodSlot, 1, false);
                    citizenData.increaseSaturation(FoodUtils.getFoodValue(stack, worker));
                    worker.getCitizenColonyHandler().getColonyOrRegister().getStatisticsManager().increment(FOOD_SERVED, worker.getCitizenColonyHandler().getColonyOrRegister().getDay());
                    StatsUtil.trackStatByStack(building, FOOD_SERVED_DETAIL, stack, 1);
                }
                else
                {
                    break;
                }

                if (citizenData.getSaturation() >= CitizenConstants.FULL_SATURATION)
                {
                    break;
                }
            }

            return getState();
        }
        else if (InventoryUtils.hasItemInItemHandler(handler, canEatPredicate))
        {
            return getState();
        }

        final int foodSlot = FoodUtils.getBestFoodForCitizen(worker.getInventoryCitizen(), citizenData, module.getMenu());
        if (foodSlot == -1)
        {
            if (InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), canEatPredicate) <= 0)
            {
                citizenToServe.clear();
                return START_WORKING;
            }
            return getState();
        }

        if (citizenData.getHomeBuilding() != null && citizenData.getHomeBuilding().getBuildingLevelEquivalent() > building.getBuildingLevel() + 1)
        {
            worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(POOR_RESTAURANT_INTERACTION), ChatPriority.IMPORTANT));
        }

        String foodName = worker.getInventoryCitizen().getStackInSlot(foodSlot).getItem().getDescriptionId();
        int qty = (int) (Math.max(1.0, (FULL_SATURATION - citizen.getCitizenData().getSaturation()) / FoodUtils.getFoodValue(worker.getInventoryCitizen().getStackInSlot(foodSlot), citizen)));
        // Hand out a bit extra
        qty = (int) Math.ceil(qty * 1.5);
        if (InventoryUtils.transferXOfItemStackIntoNextFreeSlotInItemHandler(worker.getInventoryCitizen(), foodSlot, qty, citizenData.getInventory()))
        {
            worker.getCitizenColonyHandler().getColonyOrRegister().getStatisticsManager().incrementBy(FOOD_SERVED, qty, worker.getCitizenColonyHandler().getColonyOrRegister().getDay());
            StatsUtil.trackStatByName(building, FOOD_SERVED_DETAIL, foodName, qty);
            worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
            worker.decreaseSaturationForContinuousAction();
        }

        return getState();
    }

    /**
     * Serve food to player.
     * @return next IAIState
     */
    private IAIState serveFoodToPlayer()
    {
        if (playerToServe.isEmpty())
        {
            return START_WORKING;
        }

        worker.getCitizenData().setVisibleStatus(COOK);
        if (!building.isInBuilding(playerToServe.peek().blockPosition()))
        {
            worker.getNavigation().stop();
            playerToServe.poll();
            return START_WORKING;
        }

        if (!walkToWorkPos(playerToServe.peek().blockPosition()))
        {
            return getState();
        }

        final Player player = playerToServe.poll();
        final IItemHandler handler = new InvWrapper(player.getInventory());
        final RestaurantMenuModule module = worker.getCitizenData().getWorkBuilding().getModule(RESTAURANT_MENU);
        final Predicate<ItemStack> canEatPredicate = stack -> module.getMenu().contains(new ItemStorage(stack));
        if (InventoryUtils.isItemHandlerFull(handler))
        {
            return getState();
        }
        else if (InventoryUtils.hasItemInItemHandler(handler, canEatPredicate))
        {
            return getState();
        }

        final Object2IntMap<ItemStack> transferredItemMap = InventoryUtils.transferFoodUpToSaturation(worker, handler, building.getBuildingLevel() * SATURATION_TO_SERVE, canEatPredicate);
        int count = 0;
        for (int v : transferredItemMap.values()) count += v;

        if (count <= 0)
        {
            playerToServe.clear();
            return START_WORKING;
        }
        worker.getCitizenColonyHandler().getColonyOrRegister().getStatisticsManager().incrementBy(FOOD_SERVED, count, worker.getCitizenColonyHandler().getColonyOrRegister().getDay());
        StatsUtil.trackStatByStackMap(building, FOOD_SERVED_DETAIL, transferredItemMap);
        MessageUtils.format(MESSAGE_INFO_CITIZEN_COOK_SERVE_PLAYER, worker.getName().getString()).sendTo(player);

        worker.getCitizenExperienceHandler().addExperience(BASE_XP_GAIN);
        this.worker.decreaseSaturationForContinuousAction();
        return START_WORKING;
    }

    /**
     * Check if the entity to serve can eat the given stack
     *
     * @param stack   the stack to check
     * @param citizen the citizen to check for.
     * @return true if the stack can be eaten
     */
    private boolean canEat(final ItemStack stack, final AbstractEntityCitizen citizen)
    {
        final RestaurantMenuModule module = worker.getCitizenData().getWorkBuilding().getModule(RESTAURANT_MENU);
        if (!module.getMenu().contains(new ItemStorage(stack)))
        {
            return false;
        }
        if (citizen != null)
        {
            final IBuilding building = citizen.getCitizenData().getHomeBuilding();
            if (building != null)
            {
                return building.canEat(stack);
            }
        }
        return true;
    }
    
    /**
     * Checks if the cook has anything important to do before going to the default furnace user jobs. First calculate the building range if not cached yet. Then check for citizens
     * around the building. If no citizen around switch to default jobs. If citizens around check if food in inventory, if not, switch to gather job. If food in inventory switch to
     * serve job.
     *
     * @return the next IAIState to transfer to.
     */
    @Override
    protected IAIState checkForImportantJobs()
    {
        final List<? extends Player> playerList = WorldUtil.getEntitiesWithinBuilding(world, Player.class,
          building, player -> player != null
                                && player.getFoodData().getFoodLevel() < LEVEL_TO_FEED_PLAYER
                                && building.getColony().getPermissions().hasPermission(player, Action.MANAGE_HUTS)
        );

        playerToServe.addAll(playerList);
        final RestaurantMenuModule module = worker.getCitizenData().getWorkBuilding().getModule(RESTAURANT_MENU);

        if (building.getBuildingLevel() >= 3)
        {
            boolean hasMinecoloniesFoodInMenu = false;
            for (ItemStorage menuItem : module.getMenu())
            {
                if (menuItem.getItem() instanceof IMinecoloniesFoodItem)
                {
                    hasMinecoloniesFoodInMenu = true;
                    break;
                }
            }

            if (!hasMinecoloniesFoodInMenu)
            {
                worker.getCitizenData().triggerInteraction(new StandardInteraction(Component.translatable(POOR_MENU_INTERACTION), ChatPriority.IMPORTANT));
            }
        }

        for (final EntityCitizen citizen : WorldUtil.getEntitiesWithinBuilding(world, EntityCitizen.class, building, null))
        {
            if (citizen.getCitizenJobHandler().getColonyJob() instanceof JobCook
                  || !shouldBeFed(citizen)
                  || InventoryUtils.hasItemInItemHandler(citizen.getItemHandlerCitizen(), stack -> canEat(stack, citizen)))
            {
                continue;
            }

            if (FoodUtils.hasBestOptionInInv(worker.getInventoryCitizen(), citizen.getCitizenData(), module.getMenu(), building))
            {
                citizenToServe.add(citizen);
            }
            else
            {
                final ItemStorage storage = FoodUtils.checkForFoodInBuilding(citizen.getCitizenData(), module.getMenu(), building);
                if (storage != null)
                {
                    needsCurrently = new Tuple<>(stack -> new ItemStorage(stack).equals(storage), STACKSIZE);
                    return GATHERING_REQUIRED_MATERIALS;
                }

                if (building.worksWithoutFood()
                      && InventoryUtils.addItemStackToItemHandler(worker.getInventoryCitizen(), BuildingCook.FREE_FOOD.copyWithCount(BuildingCook.FREE_FOOD.getMaxStackSize())))
                {
                    // Nothing servable in the cook's inventory and nothing in the restaurant's racks: this is where a
                    // colony with no food leaves the citizen hungry. The cook is handed a stack instead, so
                    // serveFoodToCitizen below runs completely unmodified and the usual xp and statistics still fire.
                    citizenToServe.add(citizen);
                }
            }
        }

        if (!citizenToServe.isEmpty())
        {
            return COOK_SERVE_FOOD_TO_CITIZEN;
        }

        if (!playerToServe.isEmpty())
        {
            final Predicate<ItemStack> foodPredicate = stack -> module.getMenu().contains(new ItemStorage(stack));
            if (!InventoryUtils.hasItemInItemHandler(worker.getInventoryCitizen(), foodPredicate))
            {
                if (InventoryUtils.hasItemInProvider(building, foodPredicate))
                {
                    needsCurrently = new Tuple<>(foodPredicate, STACKSIZE);
                    return GATHERING_REQUIRED_MATERIALS;
                }
            }
            return COOK_SERVE_FOOD_TO_PLAYER;
        }

        return START_WORKING;
    }

    /**
     * Check if the citizen can be fed.
     *
     * @return true if so.
     */
    private boolean shouldBeFed(AbstractEntityCitizen citizen)
    {
        return citizen.getCitizenData() != null
                 && !citizen.getCitizenData().isWorking()
                 && citizen.getCitizenData().getSaturation() <= AVERAGE_SATURATION
                 && !citizen.getCitizenData().justAte();
    }

    @Override
    protected int getActionsDoneUntilDumping()
    {
        return 1;
    }

    @Override
    protected IRequestable getSmeltAbleClass()
    {
        return new Food(STACKSIZE, building.getBuildingLevel());
    }
}
