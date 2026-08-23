package com.minecolonies.core.entity.ai.workers.service;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.IRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.IDeliverymanRequestable;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingDeliveryman;
import com.minecolonies.core.colony.interactionhandling.PosBasedInteraction;
import com.minecolonies.core.colony.interactionhandling.StandardInteraction;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.requestsystem.requests.StandardRequests.DeliveryRequest;
import com.minecolonies.core.colony.requestsystem.requests.StandardRequests.PickupRequest;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.minecolonies.core.tileentities.TileEntityRack;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.minecolonies.api.inventory.api.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.StatisticsConstants.ITEMS_DELIVERED;
import static com.minecolonies.api.util.constant.StatisticsConstants.DELIVERIES_MADE;
import static com.minecolonies.api.util.constant.StatisticsConstants.PICKUPS_MADE;
import static com.minecolonies.api.util.constant.TranslationConstants.*;

/**
 * Delivers item at needs.
 */
public class EntityAIWorkDeliveryman extends AbstractEntityAIInteract<JobDeliveryman, BuildingDeliveryman>
{
    /**
     * Min distance the worker should have to the warehouse to make any decisions.
     */
    private static final int MIN_DISTANCE_TO_WAREHOUSE = 5;

    /**
     * Wait 5 seconds for the worker to decide what to do.
     */
    private static final int DECISION_DELAY = TICKS_SECOND * 5;

    /**
     * Wait a few ticks for the worker to decide what to pick up.
     */
    private static final int PICKUP_DELAY = 5;

    /**
     * The inventory's slot which is held in hand.
     */
    private static final int SLOT_HAND = 0;

    /**
     * Completing a request with a priority of at least PRIORITY_FORCING_DUMP will force a dump.
     */
    private static final int PRIORITY_FORCING_DUMP = 10;

    /**
     * Delivery icon
     */
    private final static VisibleCitizenStatus DELIVERING =
      new VisibleCitizenStatus(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/icons/work/delivery.png"), "com.minecolonies.gui.visiblestatus.delivery");

    /**
     * Render meta backpack.
     */
    public static final String RENDER_META_BACKPACK = "backpack";

    /**
     * How long the courier stops trying to dump after the warehouse has refused its whole pack, in ticks.
     * <p>
     * Two minutes: long enough that a colony of couriers is not pacing to a full warehouse and back, short enough that
     * the very next delivery or the player's own tidying is picked up quickly. It also lines up with nothing in
     * particular on purpose -- the warehouse's own chat warning is throttled to five minutes, and a courier that gave
     * up for five minutes would look, to a player who has just freed some space, exactly like a courier that is broken.
     */
    private static final int WAREHOUSE_REFUSED_COOLDOWN = TICKS_SECOND * 120;

    /**
     * Amount of stacks left to gather from the inventory at the gathering step.
     */
    private int currentSlot = 0;

    /**
     * Game tick until which this courier does not try to dump into the warehouse, because the last attempt stored
     * nothing at all. Zero when the warehouse last took something. Deliberately not persisted: a reload is as good a
     * moment as any to try the warehouse again.
     */
    private long warehouseRefusedUntil = 0;

    /**
     * Amount of stacks the worker already kept in the current gathering process.
     */
    private List<ItemStorage> alreadyKept = new ArrayList<>();

    /**
     * Initialize the deliveryman and add all his tasks.
     *
     * @param deliveryman the job he has.
     */
    public EntityAIWorkDeliveryman(@NotNull final JobDeliveryman deliveryman)
    {
        super(deliveryman);
        super.registerTargets(
          /*
           * Check if tasks should be executed.
           */
          new AITarget(IDLE, () -> START_WORKING, 1),
          // PORT-NOTE(26.2): upstream runs this transition at DECISION_DELAY, five seconds, and that is not a wait on
          // entry -- it is how often the decision point is allowed to fire at all. Measured on a dedicated server
          // under a permanent backlog, a courier spent 27.3 % of its life standing in START_WORKING having moved
          // zero blocks, with work waiting the whole time. The rate is now the ordinary one and decide() puts the
          // five seconds back for a courier that genuinely has nothing to do, so an idle courier still polls the
          // shared queue once every five seconds and a busy one turns round in a quarter of a second.
          new AITarget(START_WORKING, this::checkIfExecute, this::decide, STANDARD_DELAY),
          new AITarget(PREPARE_DELIVERY, this::prepareDelivery, STANDARD_DELAY),
          new AITarget(DELIVERY, this::deliver, STANDARD_DELAY),
          new AITarget(PICKUP, this::pickup, PICKUP_DELAY),
          new AITarget(DUMPING, this::dump, TICKS_SECOND)

        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    protected void updateRenderMetaData()
    {
        worker.setRenderMetadata(worker.getInventoryCitizen().isEmpty() ? "" : RENDER_META_BACKPACK);
    }

    @Override
    public Class<BuildingDeliveryman> getExpectedBuildingClass()
    {
        return BuildingDeliveryman.class;
    }

    /**
     * Pickup items from a hut that has requested a pickup.
     *
     * @return the next state to go to.
     */
    private IAIState pickup()
    {
        setDelay(WALK_DELAY);
        final IRequest<? extends IDeliverymanRequestable> currentTask = job.getCurrentTask();

        if (!(currentTask instanceof PickupRequest))
        {
            // The current task has changed since the Decision-state. Restart.
            return START_WORKING;
        }

        if (cannotHoldMoreItems())
        {
            this.alreadyKept = new ArrayList<>();
            this.currentSlot = 0;
            return DUMPING;
        }

        worker.getCitizenData().setVisibleStatus(DELIVERING);

        final BlockPos pickupTarget = currentTask.getRequester().getLocation().getInDimensionLocation();
        final IBuilding pickupBuilding = building.getColony().getServerBuildingManager().getBuilding(pickupTarget);
        if (pickupBuilding == null)
        {
            job.finishRequest(false);
            return START_WORKING;
        }

        if (!walkToBuilding(pickupBuilding))
        {
            return PICKUP;
        }

        if (pickupFromBuilding(pickupBuilding))
        {
            this.alreadyKept = new ArrayList<>();
            this.currentSlot = 0;
            job.finishRequest(true);
            StatsUtil.trackStatByName(this.building, PICKUPS_MADE, pickupBuilding.getBuildingDisplayName(), 1);

            worker.decreaseSaturationForContinuousAction();
            worker.getCitizenExperienceHandler().addExperience(0.05D);

            if (currentTask.getRequest().getPriority() >= PRIORITY_FORCING_DUMP)
            {
                return DUMPING;
            }
            else
            {
                return START_WORKING;
            }
        }
        else if (InventoryUtils.openSlotCount(worker.getInventoryCitizen()) <= 0)
        {
            this.alreadyKept = new ArrayList<>();
            this.currentSlot = 0;
            return DUMPING;
        }

        setDelay(5);
        currentSlot++;
        return PICKUP;
    }

    /**
     * Gather not needed Items from building.
     *
     * @param targetBuilding building to gather it from.
     * @return true when finished.
     */
    private boolean pickupFromBuilding(@NotNull final IBuilding targetBuilding)
    {
        if (cannotHoldMoreItems() || InventoryUtils.openSlotCount(worker.getInventoryCitizen()) <= 0)
        {
            return false;
        }

        final IItemHandler handler = targetBuilding.getItemHandlerCap();
        if (handler == null)
        {
            return false;
        }

        if (currentSlot >= handler.getSlots())
        {
            return true;
        }

        ItemStack stack = handler.getStackInSlot(currentSlot);

        while (stack.isEmpty())
        {
            currentSlot++;
            if (currentSlot >= handler.getSlots())
            {
                return true;
            }
            stack = handler.getStackInSlot(currentSlot);
        }

        final int amount = workerRequiresItem(targetBuilding, stack, alreadyKept);
        if (amount <= 0)
        {
            return false;
        }

        if (ItemStackUtils.isEmpty(handler.getStackInSlot(currentSlot)))
        {
            return false;
        }

        final ItemStack activeStack = handler.extractItem(currentSlot, amount, false);
        InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(activeStack, worker.getInventoryCitizen());
        targetBuilding.markDirty();
        CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, SLOT_HAND);

        return false;
    }

    /**
     * Check if the worker can hold that much items. It depends on his building level. Level 1: 1 stack Level 2: 2 stacks, 4 stacks, 8, unlimited. That's 2^buildingLevel-1.
     *
     * @return whether this deliveryman can hold more items
     */
    private boolean cannotHoldMoreItems()
    {
        if (building.getBuildingLevel() >= building.getMaxBuildingLevel())
        {
            return false;
        }
        return InventoryUtils.getAmountOfStacksInItemHandler(worker.getInventoryCitizen()) >= Math.pow(2, building.getBuildingLevel() - 1.0D) + 1;
    }

    /**
     * Check if worker of a certain building requires the item now. Or the builder for the current task.
     *
     * @param building         the building to check for.
     * @param stack            the stack to stack with.
     * @param localAlreadyKept already kept resources.
     * @return the amount which can get dumped.
     */
    public static int workerRequiresItem(final IBuilding building, final ItemStack stack, final List<ItemStorage> localAlreadyKept)
    {
        return building.buildingRequiresCertainAmountOfItem(stack, localAlreadyKept, false);
    }

    /**
     * Dump the inventory into the warehouse.
     *
     * @return the next state to go to.
     */
    private IAIState dump()
    {
        final @Nullable IWareHouse warehouse = getAndCheckWareHouse();
        if (warehouse == null)
        {
            return START_WORKING;
        }

        if (!walkToBuilding(warehouse))
        {
            setDelay(WALK_DELAY);
            return DUMPING;
        }

        final int stored = warehouse.getTileEntity().dumpInventoryIntoWareHouse(worker.getInventoryCitizen());
        CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, SLOT_HAND);

        if (stored == 0 && !worker.getInventoryCitizen().isEmpty())
        {
            // The warehouse took nothing at all. Measured before this branch existed: the courier walked to the
            // warehouse, failed, returned to START_WORKING, and decide() sent it straight back to DUMPING -- for ever,
            // in 100 % of samples, so it never reached PREPARE_DELIVERY and therefore never took anything *out* of the
            // warehouse either. The pack is still loaded, so the round is deliberately not forgotten here, and the
            // dump branch of decide() is suppressed for a bounded time so the courier gets on with the deliveries it
            // can still make. Bounded rather than permanent because the condition it is waiting on -- somebody freeing
            // a slot -- is invisible from here.
            warehouseRefusedUntil = world.getGameTime() + WAREHOUSE_REFUSED_COOLDOWN;
            if (worker.getCitizenData() != null)
            {
                worker.getCitizenData()
                  .triggerInteraction(new PosBasedInteraction(Component.translatableEscape(COM_MINECOLONIES_COREMOD_JOB_DELIVERYMAN_WAREHOUSEFULL,
                      warehouse.getPosition().toShortString()),
                    ChatPriority.IMPORTANT,
                    Component.translatableEscape(COM_MINECOLONIES_COREMOD_JOB_DELIVERYMAN_WAREHOUSEFULL),
                    warehouse.getPosition()));
            }
            return START_WORKING;
        }

        warehouseRefusedUntil = 0;

        // Whatever the courier was carrying for the rest of its round is in the warehouse now. The requests stay
        // claimed -- they are still this courier's to make -- but they are no longer loaded, and deliver() must not
        // walk on to a stop whose goods it has just put back on the shelf.
        job.forgetLoadedDeliveries();

        return START_WORKING;
    }

    /**
     * Gets the colony's warehouse for the Deliveryman.
     *
     * @return the warehouse. null if no warehouse registered.
     */
    @Nullable
    private IWareHouse getAndCheckWareHouse()
    {
        return job.findWareHouse();
    }

    /**
     * Deliver the items to the hut, then walk on to the next stop of the round if there is one.
     * <p>
     * PORT-NOTE(26.2): upstream's precondition here was "the dman's inventory may only consist of the requested
     * itemstack", and the unload loop relied on it: it swept the whole inventory, and for any slot whose item was one
     * of the things it was delivering it extracted the <em>entire slot</em> and forced it into the target. That is safe
     * only while the courier can be carrying goods for exactly one building. Now that it can carry for three, two
     * buildings that ordered the same item would have crossed: the first stop would empty the slots meant for the
     * third, and the player would see a courier hand over goods that were not for that building and a request fail for
     * want of items that were plainly delivered. The loop below therefore tracks <em>counts</em> owed to the building
     * it is standing in front of, and never takes more of an item out of a slot than that building asked for. The old
     * precondition is gone with it, and the courier's own possessions are no longer at risk either.
     *
     * @return the next state.
     */
    private IAIState deliver()
    {
        final IRequest<? extends IDeliverymanRequestable> currentTask = job.getCurrentTask();

        if (!(currentTask instanceof DeliveryRequest))
        {
            // The current task has changed since the Decision-state.
            // Since prepareDelivery() was called earlier, go dumping first and then restart.
            return DUMPING;
        }

        worker.getCitizenData().setVisibleStatus(DELIVERING);

        final ILocation targetBuildingLocation = ((Delivery) currentTask.getRequest()).getTarget();
        if (!targetBuildingLocation.isReachableFromLocation(worker.getLocation()))
        {
            Log.getLogger().info(worker.getCitizenColonyHandler().getColonyOrRegister().getName() + ": " + worker.getName() + ": Can't inter dimension yet: ");
            return START_WORKING;
        }

        final IBuilding targetBuilding = worker.getCitizenColonyHandler().getColony().getServerBuildingManager().getBuilding(targetBuildingLocation.getInDimensionLocation());
        if (targetBuilding == null)
        {
            // PORT-NOTE(26.2): upstream reports success here, so the whole parent request chain is told its goods
            // arrived at a building that no longer exists. Nothing is lost either way -- the chain is torn down
            // rather than retried, because its requester went with the building -- but a delivery that was never
            // made is a failure, and the difference shows up in anything that reads request outcomes.
            job.finishRequest(false);
            return nextStopOfRound();
        }

        if (!walkToBuilding(targetBuilding))
        {
            setDelay(WALK_DELAY);
            return DELIVERY;
        }

        boolean success = true;
        boolean extracted = false;
        final IItemHandler workerInventory = worker.getInventoryCitizen();
        // What this stop is owed, by item and by count. A set of items was enough while a round had one stop; with
        // several it is exactly what would let two buildings ordering the same thing rob each other, so the quantity
        // has to be carried through the loop and decremented by what actually goes into the chest.
        // getTaskListWithSameDestination is already per-stop -- it matches on target as well as source -- so the map
        // below describes this building and no other.
        final Map<ItemStorage, Integer> owed = new HashMap<>();
        for (final IRequest<? extends Delivery> parcel : job.getTaskListWithSameDestination((IRequest<? extends Delivery>) currentTask))
        {
            // Only what was actually gathered. A parcel whose rack turned out to be empty is in this stop's list and
            // is not in the pack, and counting it as owed would let this stop make up the shortfall out of the next
            // stop's goods -- the same theft the counting is here to stop, arriving by a different door.
            if (!job.isLoadedDelivery(parcel.getId()))
            {
                continue;
            }
            owed.merge(new ItemStorage(parcel.getRequest().getStack()), parcel.getRequest().getStack().getCount(), Integer::sum);
        }

        for (int i = 0; i < workerInventory.getSlots(); i++)
        {
            final ItemStack slotStack = workerInventory.getStackInSlot(i);
            if (slotStack.isEmpty())
            {
                continue;
            }

            final ItemStorage storage = new ItemStorage(slotStack);
            final int owedHere = owed.getOrDefault(storage, 0);
            if (owedHere <= 0)
            {
                continue;
            }

            final ItemStack stack = workerInventory.extractItem(i, Math.min(owedHere, slotStack.getCount()), false);
            final int count = stack.getCount();
            if (ItemStackUtils.isEmpty(stack))
            {
                continue;
            }

            extracted = true;
            final ItemStack insertionResultStack;

            if (targetBuilding instanceof AbstractBuilding)
            {
                insertionResultStack = InventoryUtils.forceItemStackToItemHandler(
                  targetBuilding.getItemHandlerCap(), stack, ((IBuilding) targetBuilding)::isItemStackInRequest);
            }
            else
            {
                // Buildings that are not inherently part of the request system, but just receive a delivery, cannot have their items replaced.
                // Therefore, the keep-predicate always returns true.
                insertionResultStack =
                  InventoryUtils.forceItemStackToItemHandler(targetBuilding.getItemHandlerCap(),
                    stack,
                    itemStack -> true);
            }

            if (!ItemStackUtils.isEmpty(insertionResultStack))
            {
                // A stack was replaced (meaning the inventory didn't have enough space).

                if (ItemStack.matches(insertionResultStack, stack) && worker.getCitizenData() != null)
                {
                    // The replaced stack is the same as the one we tried to put into the inventory.
                    // Meaning, replacing failed.
                    success = false;

                    if (targetBuilding.hasModule(WorkerBuildingModule.class))
                    {
                        worker.getCitizenData()
                          .triggerInteraction(new PosBasedInteraction(Component.translatableEscape(COM_MINECOLONIES_COREMOD_JOB_DELIVERYMAN_NAMEDCHESTFULL,
                            targetBuilding.getFirstModuleOccurance(WorkerBuildingModule.class).getFirstCitizen().getName()),
                            ChatPriority.IMPORTANT,
                            Component.translatableEscape(COM_MINECOLONIES_COREMOD_JOB_DELIVERYMAN_NAMEDCHESTFULL),
                            targetBuilding.getID()));
                    }
                    else
                    {
                        worker.getCitizenData()
                          .triggerInteraction(new PosBasedInteraction(Component.translatableEscape(COM_MINECOLONIES_COREMOD_JOB_DELIVERYMAN_CHESTFULL,
                            Component.literal(" :" + targetBuilding.getSchematicName())),
                            ChatPriority.IMPORTANT,
                            Component.translatableEscape(COM_MINECOLONIES_COREMOD_JOB_DELIVERYMAN_CHESTFULL),
                            targetBuildingLocation.getInDimensionLocation()));
                    }
                }

                // Insert the result back into the pack so we do not lose it. It can no longer be assumed to fit in the
                // slot it came out of: a stop takes only what it is owed out of a slot, so that slot may still hold
                // items -- of another kind, when what comes back is the stack the target chest displaced -- and
                // insertItem then hands the whole thing straight back. Upstream emptied the slot outright, so that
                // return value could never be anything but empty and was ignored; here it is what would go missing.
                ItemStack leftOver = workerInventory.insertItem(i, insertionResultStack, false);
                if (!ItemStackUtils.isEmpty(leftOver))
                {
                    leftOver = InventoryUtils.addItemStackToItemHandlerWithResult(workerInventory, leftOver);
                }
                if (!ItemStackUtils.isEmpty(leftOver))
                {
                    // Nowhere in the pack either. Dropping it is what the rest of the mod does with goods that have no
                    // home left, and it is the only option here that does not simply delete somebody's items.
                    InventoryUtils.spawnItemStack(world, worker.getX(), worker.getY(), worker.getZ(), leftOver);
                }
            }
            // What the building actually took. forceItemStackToItemHandler answers with one of three things: nothing,
            // meaning all of it went in; the remainder of this very stack, meaning a partial insert; or -- when it had
            // to make room -- the stack it displaced, which is a different item entirely and says nothing about how
            // much of ours went in. Subtracting a displaced stack's count is how `count - result.getCount()` used to
            // read, and against a displaced stack larger than the one delivered it goes negative, which *grows* what
            // this stop is owed and lets it take the next stop's goods -- the cross-contamination the counting is here
            // to prevent, arriving by a third door.
            final int handedOver = ItemStackUtils.compareItemStacksIgnoreStackSize(insertionResultStack, stack)
                                     ? Math.max(0, count - insertionResultStack.getCount())
                                     : count;
            // Only what the building actually took counts against what it is owed; what came back is still ours to
            // give, here or at the next attempt.
            owed.put(storage, owedHere - handedOver);
            worker.getCitizenColonyHandler()
              .getColonyOrRegister()
              .getStatisticsManager()
              .incrementBy(ITEMS_DELIVERED, handedOver, worker.getCitizenColonyHandler().getColonyOrRegister().getDay());
            StatsUtil.trackStatByName(building, DELIVERIES_MADE, targetBuilding.getBuildingDisplayName(), 1);
        }

        if (!extracted)
        {
            // The courier is not carrying anything this building asked for. Let the retry-system handle this one, and
            // carry on with the round -- the other stops' goods are still in the pack and still owed.
            worker.decreaseSaturationForContinuousAction();
            CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, SLOT_HAND);
            job.finishRequest(false);

            // No need to go dumping in this case.
            return nextStopOfRound();
        }

        worker.getCitizenExperienceHandler().addExperience(1.5D);
        worker.decreaseSaturationForContinuousAction();
        CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, SLOT_HAND);
        job.finishRequest(true);

        // The chest was full and the goods came home again. The rest of the round stays claimed, but dump() will tip it
        // back into the warehouse, and prepareDelivery has to fetch it again before the round can go on.
        return success ? nextStopOfRound() : DUMPING;
    }

    /**
     * Where to go once a stop is done: on to the next stop of the round, or back to the decision point.
     * <p>
     * Deliberately asks the job whether anything <em>loaded</em> is still owed rather than calling
     * {@code getCurrentTask()}, which claims fresh work when the queue runs dry -- that would send the courier
     * straight to a building it had fetched nothing for, where it would find its pack empty and fail the request it
     * had just claimed.
     *
     * @return {@code DELIVERY} to walk to the next stop, {@code START_WORKING} to decide afresh.
     */
    private IAIState nextStopOfRound()
    {
        return job.hasLoadedDeliveryPending() ? DELIVERY : START_WORKING;
    }

    /**
     * Prepare deliveryman for delivery. Check if the building still needs the item and if the required items are still in the warehouse.
     *
     * @return the next state to go to.
     */
    private IAIState prepareDelivery()
    {
        final IRequest<? extends IRequestable> currentTask = job.getCurrentTask();
        if (!(currentTask instanceof DeliveryRequest))
        {
            // The current task has changed since the Decision-state.
            // Restart.
            return START_WORKING;
        }

        // The whole round, not just the first stop. The loading loop that walks from rack to rack already existed --
        // one order spread over three racks has always been three Delivery requests with three different starts -- and
        // this is the same loop asked to fill the pack for three buildings instead of one. The unload side is what had
        // to learn to tell them apart again.
        final List<IRequest<? extends Delivery>> taskList = job.getRoundTaskList();
        final List<ItemStack> alreadyInInv = new ArrayList<>();
        IRequest<? extends Delivery> nextPickUp = null;

        int parallelDeliveryCount = 0;
        for (final IRequest<? extends Delivery> task : taskList)
        {
            parallelDeliveryCount++;
            int totalCount = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(),
              itemStack -> ItemStackUtils.compareItemStacksIgnoreStackSize(task.getRequest().getStack(), itemStack));
            int hasCount = 0;
            for (final ItemStack stack : alreadyInInv)
            {
                if (ItemStackUtils.compareItemStacksIgnoreStackSize(stack, task.getRequest().getStack()))
                {
                    hasCount += stack.getCount();
                }
            }

            if (totalCount < hasCount + task.getRequest().getStack().getCount())
            {
                nextPickUp = task;
                break;
            }
            else
            {
                // The goods for this stop are in the pack, so the pack and the loaded set have to agree about it.
                // They can disagree: decide() sends a courier with a non-empty pack straight here while the warehouse
                // is refusing dumps, and dump() forgets the whole loaded set even when the warehouse only took part of
                // the pack. In either case this loop finds the items and gathers nothing, so nothing ever marked them
                // loaded -- and deliver() builds what a stop is owed out of the loaded set alone, so it handed over
                // nothing and failed a request whose goods the courier was visibly carrying. The set is a set, so
                // marking what is already marked costs nothing on the ordinary path.
                job.addConcurrentDelivery(task.getId());
                alreadyInInv.add(task.getRequest().getStack());
            }
        }

        if (nextPickUp == null || parallelDeliveryCount > job.getMaxParallelDeliveries())
        {
            return DELIVERY;
        }

        final ILocation location = nextPickUp.getRequest().getStart();

        if (!location.isReachableFromLocation(worker.getLocation()))
        {
            job.finishRequest(false);
            return START_WORKING;
        }

        if (!walkToSafePos(location.getInDimensionLocation()))
        {
            return PREPARE_DELIVERY;
        }

        if (getInventory().isFull())
        {
            return DUMPING;
        }

        final BlockEntity tileEntity = world.getBlockEntity(location.getInDimensionLocation());
        job.addConcurrentDelivery(nextPickUp.getId());
        if (gatherIfInTileEntity(tileEntity, nextPickUp.getRequest().getStack()))
        {
            return PREPARE_DELIVERY;
        }

        if (parallelDeliveryCount > 1)
        {
            job.removeConcurrentDelivery(nextPickUp.getId());
            return DELIVERY;
        }

        job.finishRequest(false);
        job.removeConcurrentDelivery(nextPickUp.getId());
        return START_WORKING;
    }

    /**
     * Finds the first @see ItemStack the type of {@code is}. It will be taken from the chest and placed in the worker inventory. Make sure that the worker stands next the chest to
     * not break immersion. Also make sure to have inventory space for the stack.
     *
     * @param entity the tileEntity chest or building or rack.
     * @param is     the itemStack.
     * @return true if found the stack.
     */
    public boolean gatherIfInTileEntity(final BlockEntity entity, final ItemStack is)
    {
        if (ItemStackUtils.isEmpty(is))
        {
            return false;
        }

        IItemHandler handler = null;
        if (entity instanceof final TileEntityColonyBuilding hut && InventoryUtils.hasBuildingEnoughElseCount(hut.getBuilding(), new ItemStorage(is), is.getCount()) >= is.getCount())
        {
            handler = hut.getItemHandlerCap();
        }
        else if (entity instanceof final TileEntityRack rack && rack.getCount(new ItemStorage(is)) >= is.getCount())
        {
            handler = rack.getItemHandlerCap();
        }

        if (handler != null)
        {
            return InventoryUtils.transferItemStackIntoNextFreeSlotFromItemHandler(handler,
              stack -> !ItemStackUtils.isEmpty(stack) && ItemStackUtils.compareItemStacksIgnoreStackSize(is, stack, true, true),
              is.getCount(),
              worker.getInventoryCitizen());
        }

        return false;
    }

    /**
     * @return true while this courier is inside the cooldown that started when the warehouse refused its whole pack.
     */
    private boolean warehouseRefusedRecently()
    {
        return warehouseRefusedUntil > 0 && world.getGameTime() < warehouseRefusedUntil;
    }

    /**
     * Check the wareHouse for the next task.
     *
     * @return the next AiState to go to.
     */
    private IAIState decide()
    {
        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);
        final IRequest<? extends IDeliverymanRequestable> currentTask = job.getCurrentTask();
        if (currentTask == null)
        {
            // Nothing to do, so put the five seconds back. setCurrentDelay, not setDelay: setDelay suspends the whole
            // AI for n ticks (AbstractEntityAIBasic#waitingForSomething), which is the wrong tool and would freeze the
            // courier rather than slow its polling; setCurrentDelay writes ticksToUpdate on the transition currently
            // executing -- this one -- which TickRateStateMachine#checkTransition has just reset to the transition's
            // own rate, so the override lands and applies from the next visit onwards. Without this line twenty idle
            // couriers would run the O(queue) claim scan four times a second each.
            setCurrentDelay(DECISION_DELAY);

            // If there are no deliveries/pickups pending, just loiter around the warehouse.
            if (!walkToBuilding(getAndCheckWareHouse()))
            {
                setDelay(WALK_DELAY);
                return START_WORKING;
            }
            else
            {
                if (!worker.getInventoryCitizen().isEmpty() && !warehouseRefusedRecently())
                {
                    return DUMPING;
                }
                else
                {
                    return START_WORKING;
                }
            }
        }
        if (currentTask instanceof DeliveryRequest)
        {
            // Before a delivery can be made, the inventory first needs to be dumped -- unless the warehouse has just
            // refused the whole pack, in which case insisting on an empty pack is what turns "the warehouse is full"
            // into "the colony has no couriers". prepareDelivery counts what is already in the pack before fetching
            // and deliver() hands over only what each stop is owed, so walking on with goods aboard is safe; the one
            // case that is not is a pack with no room left at all, and that courier waits instead of pacing.
            if (!worker.getInventoryCitizen().isEmpty())
            {
                if (!warehouseRefusedRecently())
                {
                    return DUMPING;
                }

                if (getInventory().isFull())
                {
                    setCurrentDelay(DECISION_DELAY);
                    return START_WORKING;
                }
            }
            return PREPARE_DELIVERY;
        }
        else
        {
            return PICKUP;
        }
    }

    /**
     * Check if the deliveryman code should be executed. More concretely if he has a warehouse to work at.
     *
     * @return false if should continue as planned.
     */
    private boolean checkIfExecute()
    {
        final IWareHouse wareHouse = getAndCheckWareHouse();
        if (wareHouse != null)
        {
            worker.getCitizenData().setWorking(true);
            if (wareHouse.getTileEntity() == null)
            {
                return false;
            }
            {
                return true;
            }
        }

        worker.getCitizenData().setWorking(false);
        if (worker.getCitizenData() != null)
        {
            worker.getCitizenData()
              .triggerInteraction(new StandardInteraction(Component.translatableEscape(COM_MINECOLONIES_COREMOD_JOB_DELIVERYMAN_NOWAREHOUSE),
                ChatPriority.BLOCKING));
        }
        return false;
    }
}
