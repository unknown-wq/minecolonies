package com.minecolonies.core.entity.ai.workers.crafting;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.colony.requestsystem.requestable.crafting.PublicCrafting;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.RecipeStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.util.*;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.modules.CraftingWorkerBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.core.colony.interactionhandling.SimpleNotificationInteraction;
import com.minecolonies.core.colony.jobs.AbstractJobCrafter;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.other.SittingEntity;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import com.minecolonies.core.network.messages.client.BlockParticleEffectMessage;
import com.minecolonies.core.network.messages.client.LocalizedParticleEffectMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.minecolonies.api.colony.requestsystem.requestable.deliveryman.AbstractDeliverymanRequestable.MAX_BUILDING_PRIORITY;
import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.CitizenConstants.*;
import static com.minecolonies.api.util.constant.Constants.DEFAULT_SPEED;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.SchematicTagConstants.*;
import static com.minecolonies.api.util.constant.StatisticsConstants.ITEMS_CRAFTED;
import static com.minecolonies.api.util.constant.TranslationConstants.CRAFTER_IS_OUT_OF_MATERIALS_MESSAGE;
import static com.minecolonies.core.util.WorkerUtil.hasTooManyExternalItemsInInv;

import static com.minecolonies.api.util.constant.StatisticsConstants.ITEMS_CRAFTED_DETAIL;

/**
 * Abstract class for the principal crafting AIs.
 */
public abstract class AbstractEntityAICrafting<J extends AbstractJobCrafter<?, J>, B extends AbstractBuilding> extends AbstractEntityAIInteract<J, B>
{
    /**
     * Allow transitioning to any other state, otherwise stay in current.
     */
    public static IAIState NO_CHANGE = null;

    /**
     * Time the worker delays until the next hit.
     */
    protected static final int HIT_DELAY = 10;

    /**
     * Increase this value to make the product creation progress way slower.
     */
    public static final int PROGRESS_MULTIPLIER = 10;

    /**
     * Max level which should have an effect on the speed of the worker.
     */
    protected static final int MAX_LEVEL = 50;

    /**
     * Times the product needs to be hit.
     */
    private static final int HITTING_TIME = 3;

    /**
     * The current request that is being crafted;
     */
    public IRequest<? extends PublicCrafting> currentRequest;

    /**
     * The current recipe that is being crafted.
     */
    protected IRecipeStorage currentRecipeStorage;

    /**
     * Player damage source.
     */
    private DamageSource playerDamageSource;

    /**
     * Already dumped during this iteration.
     */
    private boolean dumped = false;

    /**
     * Idle pos.
     */
    public BlockPos idlePos = null;

    /**
     * The number of actions a crafting "success" is worth. By default, that's 1 action for 1 crafting success. Override this in your subclass to make crafting recipes worth more
     * actions :-)
     *
     * @return The number of actions a crafting "success" is worth.
     */
    protected int getActionRewardForCraftingSuccess()
    {
        return 1;
    }

    /**
     * Returns the name of the crafting stat that is used in the building's statistics.
     * Override this in your subclass to change the description of the smelting stat.
     * @return The name of the crafting statistic.
     */
    protected String getCraftingStatName()
    {
        return ITEMS_CRAFTED_DETAIL;
    }

    /**
     * Records the crafting request in the building's statistics.
     * Override this in your subclass to change the description of the crafting stat.
     * @param request the request to record.
     */
    protected void recordCraftingBuildingStats(IRequest<?> request, IRecipeStorage recipe)
    {
        if (recipe == null) 
        {
            return;
        }

        StatsUtil.trackStatByName(building, getCraftingStatName(), recipe.getPrimaryOutput().getHoverName(), recipe.getPrimaryOutput().getCount());
    }


    /**
     * Initialize the crafter job and add all his tasks.
     *
     * @param job the job he has.
     */
    public AbstractEntityAICrafting(@NotNull final J job)
    {
        super(job);
        super.registerTargets(
          /*
           * Check if tasks should be executed.
           */
          new AITarget(IDLE, this::hasWorkToDo, () -> START_WORKING, TICKS_SECOND),
          new AITarget(IDLE, this::idle, TICKS_SECOND),
          new AITarget(START_WORKING, this::decide, STANDARD_DELAY),
          new AITarget(QUERY_ITEMS, this::queryItems, STANDARD_DELAY),
          new AITarget(GET_RECIPE, this::getRecipe, STANDARD_DELAY),
          new AITarget(CRAFT, this::craft, HIT_DELAY)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    protected void updateRenderMetaData()
    {
        worker.setRenderMetadata(getState() == CRAFT ? RENDER_META_WORKING : "");
    }

    protected IAIState idle()
    {
        // IDLE carries two transitions of the same tick rate: "there is work -> START_WORKING" and this one. Their
        // start offsets differ (TickingTransition assigns ticksToUpdate from a shared counter), so which of the two
        // matures first after entering IDLE is effectively a coin flip. When this one wins, the setDelay below stops
        // the whole AI for twenty seconds -- waitingForSomething is an AI_BLOCKING event registered ahead of every
        // state target, so it swallows the work transition as well. Measured on a lumberjack: 20.2 seconds standing
        // still after an inventory dump, for a worker whose hasWorkToDo() is true by definition.
        //
        // Hand back to the work transition directly instead of idling on top of it. A crafter with an empty task
        // queue is unaffected: hasWorkToDo() is false for it and it idles exactly as before.
        if (hasWorkToDo())
        {
            return START_WORKING;
        }

        if ((idlePos != null && !walkToSafePos(idlePos)) || !worker.getNavigation().isDone())
        {
            return NO_CHANGE;
        }

        if (!building.isInBuilding(worker.blockPosition()))
        {
            walkToBuilding();
            return NO_CHANGE;
        }

        setDelay(TICKS_20 * 20);

        if (idlePos != null)
        {
            if (building.getLocationsFromTag(TAG_SITTING).contains(idlePos)
                || building.getLocationsFromTag(TAG_SIT_IN).contains(idlePos)
                || building.getLocationsFromTag(TAG_SIT_OUT).contains(idlePos))
            {
                SittingEntity.sitDown(idlePos, worker, TICKS_SECOND * 20);
                idlePos = null;
                return NO_CHANGE;
            }
            idlePos = null;
        }

        if (MathUtils.RANDOM.nextBoolean())
        {
            final List<BlockPos> sitPositions = new ArrayList<>(building.getLocationsFromTag(TAG_SITTING));
            sitPositions.addAll(building.getLocationsFromTag(TAG_SIT_IN));
            if (worker.level().isRaining())
            {
                if (!sitPositions.isEmpty())
                {
                    idlePos = sitPositions.get(MathUtils.RANDOM.nextInt(sitPositions.size()));
                    return NO_CHANGE;
                }
            }
            else
            {
                sitPositions.addAll(building.getLocationsFromTag(TAG_SIT_OUT));
                if (!sitPositions.isEmpty())
                {
                    idlePos = sitPositions.get(MathUtils.RANDOM.nextInt(sitPositions.size()));
                    return NO_CHANGE;
                }
            }
        }

        if (MathUtils.RANDOM.nextBoolean())
        {
            final List<BlockPos> standPositions = new ArrayList<>(building.getLocationsFromTag(TAG_STAND_IN));
            if (worker.level().isRaining())
            {
                if (!standPositions.isEmpty())
                {
                    idlePos = standPositions.get(MathUtils.RANDOM.nextInt(standPositions.size()));
                    return NO_CHANGE;
                }
            }
            else
            {
                standPositions.addAll(building.getLocationsFromTag(TAG_STAND_OUT));
                if (!standPositions.isEmpty())
                {
                    idlePos = standPositions.get(MathUtils.RANDOM.nextInt(standPositions.size()));
                    return NO_CHANGE;
                }
            }
        }

        EntityNavigationUtils.walkToRandomPosWithin(worker, 10, DEFAULT_SPEED, building.getCorners());
        return NO_CHANGE;
    }

    /**
     * If the crafter should go in idle mode or not.
     * @return true if so.
     */
    public boolean hasWorkToDo()
    {
        return !job.getTaskQueue().isEmpty() && job.getCurrentTask() != null;
    }

    /**
     * Main method to decide on what to do.
     *
     * @return the next state to go to.
     */
    protected IAIState decide()
    {
        if (!hasWorkToDo())
        {
            return IDLE;
        }
        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);

        if (!walkToBuilding())
        {
            return START_WORKING;
        }

        if (job.getActionsDone() >= getActionsDoneUntilDumping())
        {
            // Wait to dump before continuing.
            return getState();
        }

        return getNextCraftingState();
    }

    /**
     * Gets the next crafting state required, if a task exists.
     *
     * @return next state
     */
    protected IAIState getNextCraftingState()
    {
        if (job.getCurrentTask() == null)
        {
            return IDLE;
        }

        if (currentRecipeStorage != null && !dumped && hasTooManyExternalItemsInInv(currentRecipeStorage, worker.getInventoryCitizen()))
        {
            dumped = true;
            return INVENTORY_FULL;
        }

        if (currentRequest != null && currentRecipeStorage != null)
        {
            return QUERY_ITEMS;
        }

        return GET_RECIPE;
    }

    /**
     * Query the IRecipeStorage of the first request in the queue.
     *
     * @return the next state to go to.
     */
    protected IAIState getRecipe()
    {
        final IRequest<? extends PublicCrafting> currentTask = job.getCurrentTask();

        if (currentTask == null)
        {
            return START_WORKING;
        }

        final ICraftingBuildingModule module = building.getCraftingModuleForRecipe(currentTask.getRequest().getRecipeID());
        if (module == null)
        {
            job.finishRequest(false);
            incrementActionsDone(getActionRewardForCraftingSuccess());
            return START_WORKING;
        }
        currentRecipeStorage = module.getFirstFulfillableRecipe(stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, currentTask.getRequest().getStack()), 1, false);
        if (currentRecipeStorage == null)
        {
            job.finishRequest(false);
            incrementActionsDone(getActionRewardForCraftingSuccess());
            return START_WORKING;
        }

        if (!dumped && hasTooManyExternalItemsInInv(currentRecipeStorage, worker.getInventoryCitizen()))
        {
            dumped = true;
            currentRecipeStorage = null;
            return INVENTORY_FULL;
        }

        if (currentRecipeStorage.getRequiredTool() != ModEquipmentTypes.none.get())
        {
            if (checkForToolOrWeapon(currentRecipeStorage.getRequiredTool()))
            {
                currentRecipeStorage = null;
                job.finishRequest(false);
                incrementActionsDone(getActionRewardForCraftingSuccess());
                return START_WORKING;
            }
        }

        currentRequest = currentTask;
        final int currentCount = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(),
          stack -> ItemStackUtils.compareItemStacksIgnoreStackSize(stack, currentRecipeStorage.getPrimaryOutput()));
        final int inProgressCount = getExtendedCount(currentRecipeStorage.getPrimaryOutput());

        final int countPerIteration = currentRecipeStorage.getPrimaryOutput().getCount();
        final int doneOpsCount = currentCount / countPerIteration;
        final int progressOpsCount = inProgressCount / countPerIteration;

        final int minRemainingOpsCount = currentRequest.getRequest().getMinCount() - doneOpsCount - progressOpsCount;
        int availableOpsCount = currentRequest.getRequest().getCount();
        final List<ItemStorage> input = currentRecipeStorage.getCleanedInput();
        for (final ItemStorage inputStorage : input)
        {
            final ItemStack container = inputStorage.getItemStack().getItem().getCraftingRemainder() == null
                                          ? ItemStack.EMPTY
                                          : inputStorage.getItemStack().getItem().getCraftingRemainder().create();
            boolean isToolOrContainer = true;
            final int remaining;
            if (!currentRecipeStorage.getCraftingToolsAndSecondaryOutputs().isEmpty()
                  && ItemStackUtils.compareItemStackListIgnoreStackSize(currentRecipeStorage.getCraftingToolsAndSecondaryOutputs(), inputStorage.getItemStack(), false, true))
            {
                remaining = inputStorage.getAmount();
            }
            else if (!ItemStackUtils.isEmpty(container) && ItemStackUtils.compareItemStacksIgnoreStackSize(inputStorage.getItemStack(), container, false, true))
            {
                remaining = inputStorage.getAmount();
            }
            else
            {
                remaining = inputStorage.getAmount() * minRemainingOpsCount;
                isToolOrContainer = false;
            }

            int availableCount = InventoryUtils.getCountFromBuilding(building, itemStack -> ItemStackUtils.compareItemStacksIgnoreStackSize(itemStack, inputStorage.getItemStack(), false, true))
                + InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(),
                itemStack -> ItemStackUtils.compareItemStacksIgnoreStackSize(itemStack, inputStorage.getItemStack(), false, true))
                + getExtendedCount(inputStorage.getItemStack());

            if (availableCount < remaining)
            {
                if (worksWithoutMaterials() && supplyMaterialWithoutRequest(inputStorage.getItemStack(), remaining - availableCount) > 0)
                {
                    // Belt and braces behind the request system hook: a task queued before the switch was thrown, or
                    // one whose ingredients were eaten in the meantime, would otherwise be failed outright here rather
                    // than asking for anything.
                    availableCount = remaining;
                }
                else
                {
                    // The order dies here, and it used to die silently: the crafter chain has no "out of materials"
                    // interaction at all, only the generic exception and the full-chest one, so an order whose
                    // ingredients were taken between the delivery and the crafting simply vanished with the citizen
                    // still reporting "Working". Say what is missing.
                    complainAboutMissingMaterial(inputStorage.getItemStack());
                    currentRecipeStorage = null;
                    job.finishRequest(false);
                    incrementActionsDone(getActionRewardForCraftingSuccess());
                    return START_WORKING;
                }
            }

            if (!isToolOrContainer)
            {
                // Convert available ingredient count to possible recipe iterations
                final int possibleIterations = availableCount / inputStorage.getAmount();
                availableOpsCount = Math.min(possibleIterations, availableOpsCount);
            }
        }

        job.setMaxCraftingCount(Math.min(availableOpsCount + doneOpsCount, currentRequest.getRequest().getCount()));
        job.setCraftCounter(doneOpsCount);
        return QUERY_ITEMS;
    }

    /**
     * Get an extended count that can be overriden.
     *
     * @param stack the stack to add.
     * @return the additional quantities (for example in a furnace).
     */
    protected int getExtendedCount(final ItemStack stack)
    {
        return 0;
    }

    @Override
    public IAIState getStateAfterPickUp()
    {
        return START_WORKING;
    }

    /**
     * Query the required items to take them in the inventory to craft.
     *
     * @return the next state to go to.
     */
    private IAIState queryItems()
    {
        if (currentRecipeStorage == null)
        {
            return START_WORKING;
        }

        return checkForItems(currentRecipeStorage);
    }

    /**
     * Check for all items of the required recipe.
     *
     * @param storage the recipe storage.
     * @return the next state to go to.
     */
    protected IAIState checkForItems(@NotNull final IRecipeStorage storage)
    {
        final int inProgressCount = getExtendedCount(currentRecipeStorage.getPrimaryOutput());
        final int countPerIteration = currentRecipeStorage.getPrimaryOutput().getCount();
        final int progressOpsCount = inProgressCount / Math.max(countPerIteration, 1);

        final List<ItemStorage> input = storage.getCleanedInput();
        for (final ItemStorage inputStorage : input)
        {
            final Predicate<ItemStack> predicate = stack -> !ItemStackUtils.isEmpty(stack) && new Stack(stack, false).matches(inputStorage.getItemStack());
            final int invCount = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), predicate) + getExtendedCount(inputStorage.getItemStack());
            final ItemStack container = inputStorage.getItemStack().getItem().getCraftingRemainder() == null
                                          ? ItemStack.EMPTY
                                          : inputStorage.getItemStack().getItem().getCraftingRemainder().create();
            final int remaining;
            if (!currentRecipeStorage.getCraftingToolsAndSecondaryOutputs().isEmpty()
                  && ItemStackUtils.compareItemStackListIgnoreStackSize(currentRecipeStorage.getCraftingToolsAndSecondaryOutputs(), inputStorage.getItemStack(), false, true))
            {
                remaining = inputStorage.getAmount();
            }
            else if (!ItemStackUtils.isEmpty(container) && ItemStackUtils.compareItemStacksIgnoreStackSize(inputStorage.getItemStack(), container, false, true))
            {
                remaining = inputStorage.getAmount();
            }
            else
            {
                remaining = inputStorage.getAmount() * Math.max(job.getMaxCraftingCount(), 1);
            }

            if (invCount + inProgressCount <= 0
                  || invCount + ((job.getCraftCounter() + progressOpsCount) * inputStorage.getAmount()) < remaining)
            {
                if (InventoryUtils.hasItemInProvider(building, predicate))
                {
                    needsCurrently = new Tuple<>(predicate, remaining);
                    return GATHERING_REQUIRED_MATERIALS;
                }
                if (worksWithoutMaterials() && supplyMaterialWithoutRequest(inputStorage.getItemStack(), remaining - invCount) > 0)
                {
                    // Same reasoning as in getRecipe: hand it over rather than drop the task on the floor.
                    continue;
                }
                currentRecipeStorage = null;
                currentRequest = null;
                return GET_RECIPE;
            }
        }

        return CRAFT;
    }

    /**
     * The actual crafting logic.
     *
     * @return the next state to go to.
     */
    protected IAIState craft()
    {
        if (currentRecipeStorage == null || job.getCurrentTask() == null)
        {
            return START_WORKING;
        }

        if (currentRequest == null && job.getCurrentTask() != null)
        {
            return GET_RECIPE;
        }

        if (!walkToTaggedWorkPos())
        {
            return getState();
        }

        job.setProgress(job.getProgress() + 1);

        int toolSlot = -1;
        if (currentRecipeStorage.getRequiredTool() != ModEquipmentTypes.none.get())
        {
            toolSlot = InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), stack -> currentRecipeStorage.getRequiredTool().checkIsEquipment(stack));
        }
        // Purely cosmetic: what the worker is seen holding while the animation plays. nextInt() throws on an empty
        // list, though, and a recipe whose inputs are all tools or containers has one.
        final List<ItemStorage> shownInput = currentRecipeStorage.getCleanedInput();
        final ItemStack shownStack = shownInput.isEmpty()
                                       ? currentRecipeStorage.getPrimaryOutput().copy()
                                       : shownInput.get(worker.getRandom().nextInt(shownInput.size())).getItemStack().copy();
        if (toolSlot >= 0)
        {
            worker.getInventoryCitizen().setHeldItem(InteractionHand.MAIN_HAND, toolSlot);
            worker.setItemInHand(InteractionHand.MAIN_HAND, worker.getInventoryCitizen().getStackInSlot(toolSlot));
            worker.setItemInHand(InteractionHand.OFF_HAND, shownStack);
        }
        else
        {
            worker.setItemInHand(InteractionHand.MAIN_HAND, shownStack);
            worker.setItemInHand(InteractionHand.OFF_HAND, currentRecipeStorage.getPrimaryOutput().copy());
        }
        hitBlockWithToolInHand(building.getPosition());
        new LocalizedParticleEffectMessage(worker.getMainHandItem(), building.getPosition().above()).sendToTrackingEntity(worker);

        currentRequest = job.getCurrentTask();

        if (currentRequest != null && (currentRequest.getState() == RequestState.CANCELLED || currentRequest.getState() == RequestState.FAILED))
        {
            currentRequest = null;
            incrementActionsDone(getActionRewardForCraftingSuccess());
            currentRecipeStorage = null;
            return START_WORKING;
        }

        if (job.getProgress() >= getRequiredProgressForMakingRawMaterial())
        {
            final IAIState check = checkForItems(currentRecipeStorage);
            if (check == CRAFT)
            {
                return executeCraftingAction(toolSlot);
            }
            return START_WORKING;
        }

        return getState();
    }

    /**
     * Execute the actual crafting action.
     * @param toolSlot the tool slot to consider.
     * @return the next state to go to.
     */
    public IAIState executeCraftingAction(final int toolSlot)
    {
        final List<ItemStack> addedStacks = currentRecipeStorage.fullfillRecipeAndCopy(getLootContext(), ImmutableList.of(worker.getItemHandlerCitizen()), true);
        if (addedStacks == null)
        {
            currentRequest = null;
            incrementActionsDone(getActionRewardForCraftingSuccess());
            job.finishRequest(false);
            resetValues();
            return START_WORKING;
        }
        recordCraftingBuildingStats(currentRequest, currentRecipeStorage);
        for (final ItemStack addedStack : addedStacks)
        {
            if (ItemStackUtils.compareItemStacksIgnoreStackSize(currentRecipeStorage.getPrimaryOutput(), addedStack))
            {
                currentRequest.addDelivery(addedStack);
            }
            else
            {
                job.getSecondaryOutputs().addTo(new ItemStorage(addedStack), addedStack.getCount());
            }
        }

        job.setCraftCounter(job.getCraftCounter() + 1);
        if (toolSlot != -1)
        {
            CitizenItemUtils.damageItemInHand(worker, InteractionHand.MAIN_HAND, 1);
        }

        if (job.getCraftCounter() >= job.getMaxCraftingCount())
        {
            incrementActionsDone(getActionRewardForCraftingSuccess());
            final ICraftingBuildingModule module = building.getCraftingModuleForRecipe(currentRecipeStorage.getToken());
            if (module != null)
            {
                module.improveRecipe(currentRecipeStorage, job.getCraftCounter(), worker.getCitizenData());
            }

            return finalizeCraftingTask();
        }
        else if (toolSlot >= 0 && worker.getInventoryCitizen().getHeldItem(InteractionHand.MAIN_HAND).isEmpty())
        {
            // tool broke, abort crafting
            currentRequest = null;
            job.finishRequest(false);
            incrementActionsDoneAndDecSaturation();
            resetValues();
            return START_WORKING;
        }
        else
        {
            job.setProgress(0);
            return GET_RECIPE;
        }
    }

    /**
     * Tell the player that an order was dropped because an ingredient was not there.
     *
     * @param missing the ingredient that ran out.
     */
    private void complainAboutMissingMaterial(final ItemStack missing)
    {
        final ICitizenData citizenData = worker.getCitizenData();
        if (citizenData != null)
        {
            citizenData.triggerInteraction(new SimpleNotificationInteraction(
              Component.translatableEscape(CRAFTER_IS_OUT_OF_MATERIALS_MESSAGE, missing.getHoverName()), ChatPriority.IMPORTANT));
        }
    }

    public IAIState finalizeCraftingTask()
    {
        currentRecipeStorage = null;
        resetValues();

        // There used to be a second experience award here, guarded on the three counters being zero -- which
        // resetValues() has just made true, so the guard never held anything back. Every order was paid twice: once
        // here and once in afterDump(), which is where the request is actually finished. INVENTORY_FULL always leads
        // there, so dropping this one leaves exactly one award per order.
        return INVENTORY_FULL;
    }

    public void hitBlockWithToolInHand(@Nullable final BlockPos blockPos)
    {
        worker.getLookControl().setLookAt(blockPos.getX(), blockPos.getY(), blockPos.getZ(), FACING_DELTA_YAW, worker.getMaxHeadXRot());

        worker.swing(worker.getUsedItemHand());

        final BlockState blockState = worker.level().getBlockState(blockPos);
        final BlockPos vector = blockPos.subtract(worker.blockPosition());
        final Direction facing = BlockPosUtil.directionFromDelta(vector.getX(), vector.getY(), vector.getZ()).getOpposite();

        new BlockParticleEffectMessage(blockPos, blockState, facing.ordinal())
            .sendToTargetPoint((ServerLevel) worker.level(), null, blockPos.getX(), blockPos.getY(), blockPos.getZ(), BLOCK_BREAK_PARTICLE_RANGE);

        job.playSound(blockPos, (EntityCitizen) worker);
    }

    /**
     * Reset all the values.
     */
    public void resetValues()
    {
        job.setMaxCraftingCount(0);
        job.setProgress(0);
        job.setCraftCounter(0);
        worker.setItemInHand(InteractionHand.MAIN_HAND, ItemStackUtils.EMPTY);
        worker.setItemInHand(InteractionHand.OFF_HAND, ItemStackUtils.EMPTY);
        dumped = false;
    }

    @Override
    public IAIState afterDump()
    {
        if (job.getMaxCraftingCount() == 0 && job.getProgress() == 0 && job.getCraftCounter() == 0 && currentRequest != null)
        {
            // Fallback security blanket. Normally, the craft() method should have dealt with the request.
            if (currentRequest.getState() == RequestState.IN_PROGRESS)
            {
                worker.getCitizenColonyHandler()
                    .getColonyOrRegister()
                    .getStatisticsManager()
                    .incrementBy(ITEMS_CRAFTED, currentRequest.getRequest().getCount(), worker.getCitizenColonyHandler().getColonyOrRegister().getDay());
                job.finishRequest(true);
                worker.getCitizenExperienceHandler().addExperience(currentRequest.getRequest().getCount() / 2.0);
            }
            currentRequest = null;
            resetValues();
        }

        if (!job.getSecondaryOutputs().isEmpty())
        {
            final BlockPos closestWarehouse = job.getColony().getServerBuildingManager().getBestBuilding(worker, BuildingWareHouse.class);
            if (closestWarehouse != null)
            {
                final IBuilding warehouse = job.getColony().getServerBuildingManager().getBuilding(closestWarehouse);
                for (final Map.Entry<ItemStorage, Integer> output : job.getSecondaryOutputs().entrySet())
                {
                    warehouse.createRequest(new Delivery(building.getLocation(),
                        warehouse.getLocation(),
                        output.getKey().getItemStack().copyWithCount(output.getValue()),
                        MAX_BUILDING_PRIORITY), true);
                }
            }
            job.getSecondaryOutputs().clear();
        }

        return super.afterDump();
    }

    @Override
    protected int getActionsDoneUntilDumping()
    {
        return 1;
    }

    /**
     * Get the required progress to execute a recipe.
     *
     * @return the amount of hits required.
     */
    private int getRequiredProgressForMakingRawMaterial()
    {
        final int jobModifier = worker.getCitizenData().getCitizenSkillHandler().getLevel(((CraftingWorkerBuildingModule) getModuleForJob()).getCraftSpeedSkill()) / 2;
        return PROGRESS_MULTIPLIER / Math.min(jobModifier + 1, MAX_LEVEL) * HITTING_TIME;
    }

    @Override
    public boolean isAfterDumpPickupAllowed()
    {
        return currentRequest == null;
    }

    /**
     * get the LootContextBuilder for
     *
     * @return the LootContext to use for crafting
     */
    protected LootParams getLootContext()
    {
        return getLootContext(false);
    }

    /**
     * get the LootContextBuilder for
     *
     * @param includeKiller true for killer-based parameters
     * @return the LootContext to use for crafting
     */
    protected LootParams getLootContext(boolean includeKiller)
    {
        if (playerDamageSource == null)
        {
            playerDamageSource = world.damageSources().playerAttack(getFakePlayer());
        }

        LootParams.Builder builder = (new LootParams.Builder((ServerLevel) this.world))
                                       .withParameter(LootContextParams.ORIGIN, worker.position())
                                       .withParameter(LootContextParams.THIS_ENTITY, worker)
                                       .withParameter(LootContextParams.TOOL, worker.getMainHandItem())
                                       .withLuck((float) getEffectiveSkillLevel(getPrimarySkillLevel()));

        if (includeKiller)
        {
            builder = builder
                        .withParameter(LootContextParams.DAMAGE_SOURCE, playerDamageSource)
                        .withParameter(LootContextParams.ATTACKING_ENTITY, playerDamageSource.getEntity())
                        .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, playerDamageSource.getDirectEntity());
        }

        return builder.create(RecipeStorage.recipeLootParameters);
    }
}
