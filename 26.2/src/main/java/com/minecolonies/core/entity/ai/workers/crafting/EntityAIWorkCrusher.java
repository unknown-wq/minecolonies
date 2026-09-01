package com.minecolonies.core.entity.ai.workers.crafting;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingCrusher;
import com.minecolonies.core.colony.jobs.JobCrusher;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.network.messages.client.LocalizedParticleEffectMessage;
import com.minecolonies.core.util.WorkerUtil;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.resources.Identifier;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.util.constant.Constants.STACKSIZE;

/**
 * Crusher AI class.
 */
public class EntityAIWorkCrusher extends AbstractEntityAICrafting<JobCrusher, BuildingCrusher>
{
    /**
     * Delay for each of the craftings.
     */
    private static final int TICK_DELAY = 40;

    /**
     * Crusher icon
     */
    private final static VisibleCitizenStatus CRUSHING =
      new VisibleCitizenStatus(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/icons/work/crusher.png"), "com.minecolonies.gui.visiblestatus.crusher");

    /**
     * Constructor for the crusher. Defines the tasks the crusher executes.
     *
     * @param job a crusher job to use.
     */
    public EntityAIWorkCrusher(@NotNull final JobCrusher job)
    {
        super(job);
        // No IDLE target of its own. The crusher used to carry an unconditional IDLE -> START_WORKING at one tick,
        // to get itself back to work the moment there was crushing to do -- the daily quota resetting at dawn is not
        // a task in the queue, so the plain crafter's "is there a task" test would have left it sitting there. That
        // job now belongs to hasWorkToDo() below, which the base class tests from IDLE once a second and which
        // answers for the daily quota as well.
        //
        // Kept as it was, the one-tick target beat both of the base transitions to the punch on nineteen ticks out
        // of twenty, so START_WORKING -> decide() -> IDLE -> START_WORKING went round without pause and idle() was
        // never reached: a crusher that had finished its day could not sit down, shelter from the rain or walk its
        // hut like every other crafter.
        super.registerTargets(
          new AITarget(CRUSH, this::crush, TICK_DELAY)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<BuildingCrusher> getExpectedBuildingClass()
    {
        return BuildingCrusher.class;
    }

    @Override
    protected IAIState decide()
    {
        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);
        if (job.getTaskQueue().isEmpty() || job.getCurrentTask() == null)
        {
            if (building.getCurrentDailyQuantity() < building.getSetting(BuildingCrusher.DAILY_LIMIT).getValue())
            {
                return CRUSH;
            }

            return IDLE;
        }

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

    @Override
    public boolean hasWorkToDo()
    {
        return super.hasWorkToDo() || building.getCurrentDailyQuantity() < building.getSetting(BuildingCrusher.DAILY_LIMIT).getValue();
    }

    /**
     * The crushing process.
     *
     * @return the next AiState to go to.
     */
    protected IAIState crush()
    {
        if (!walkToBuilding())
        {
            return getState();
        }

        worker.getCitizenData().setVisibleStatus(CRUSHING);
        job.setProgress(job.getProgress() + TICK_DELAY);

        final BuildingCrusher crusherBuilding = building;
        WorkerUtil.faceBlock(crusherBuilding.getPosition(), worker);

        final IRecipeStorage recipeMode = crusherBuilding.getSetting(BuildingCrusher.MODE).getValue(crusherBuilding);
        final int dailyLimit = crusherBuilding.getSetting(BuildingCrusher.DAILY_LIMIT).getValue();
        if (getState() != CRAFT)
        {
            // Free crushing follows the mode in the hut window, so re-read it every cycle. Picking it up only when
            // currentRecipeStorage happened to be null meant the setting took hold when the material ran out and not
            // before -- a crusher with a chest of gravel behind it went on making sand for as long as the gravel
            // lasted after the player had switched it to something else.
            currentRecipeStorage = recipeMode;
        }
        else if (currentRecipeStorage == null)
        {
            currentRecipeStorage = recipeMode;
        }

        if ((getState() != CRAFT && crusherBuilding.getCurrentDailyQuantity() >= dailyLimit) || currentRecipeStorage == null)
        {
            return START_WORKING;
        }

        final IAIState check = checkForItems(currentRecipeStorage);
        if (job.getProgress() > MAX_LEVEL - Math.min((getSecondarySkillLevel() / 2) + 1, MAX_LEVEL))
        {
            job.setProgress(0);

            if (check == CRAFT)
            {
                // Nothing counts until the recipe has actually been fulfilled. fullfillRecipe answers false when the
                // output does not fit or an input is no longer extractable, and every line below this one used to run
                // ahead of it and regardless of its answer: the daily quota, the craft counter, the experience, the
                // building statistics -- and addDelivery, which reports goods to the open request. A single false
                // then fed itself, because checkForItems credits the craft counter it was just handed: the order ran
                // to its end and closed on items that were never made. AbstractEntityAICrafting#executeCraftingAction
                // gates the same bookkeeping the same way.
                if (!currentRecipeStorage.fullfillRecipe(getLootContext(), ImmutableList.of(worker.getItemHandlerCitizen())))
                {
                    if (getState() == CRAFT)
                    {
                        currentRequest = null;
                        incrementActionsDone(getActionRewardForCraftingSuccess());
                        job.finishRequest(false);
                        resetValues();
                    }
                    return START_WORKING;
                }

                if (getState() != CRAFT)
                {
                    crusherBuilding.setCurrentDailyQuantity(crusherBuilding.getCurrentDailyQuantity() + 1);
                    if (crusherBuilding.getCurrentDailyQuantity() >= dailyLimit)
                    {
                        incrementActionsDoneAndDecSaturation();
                    }
                }
                if (currentRequest != null)
                {
                    currentRequest.addDelivery(currentRecipeStorage.getPrimaryOutput());
                }

                worker.swing(InteractionHand.MAIN_HAND);
                job.setCraftCounter(job.getCraftCounter() + 1);

                worker.decreaseSaturationForContinuousAction();
                worker.getCitizenExperienceHandler().addExperience(0.1);
                recordCraftingBuildingStats(currentRequest, currentRecipeStorage);
            }
            else if (getState() != CRAFT)
            {
                currentRecipeStorage = recipeMode;
                final int requestQty = Math.min((dailyLimit - crusherBuilding.getCurrentDailyQuantity()) * 2, STACKSIZE);
                if (requestQty <= 0)
                {
                    return START_WORKING;
                }
                final ItemStack stack = currentRecipeStorage.getInput().get(0).getItemStack().copy();
                stack.setCount(requestQty);
                checkIfRequestForItemExistOrCreateAsync(stack);
                return START_WORKING;
            }
            else
            {
                return check;
            }
        }
        if (check == CRAFT)
        {
            new LocalizedParticleEffectMessage(currentRecipeStorage.getInput().get(0).getItemStack().copy(), crusherBuilding.getID()).sendToTrackingEntity(worker);
            new LocalizedParticleEffectMessage(currentRecipeStorage.getPrimaryOutput().copy(), crusherBuilding.getID().below()).sendToTrackingEntity(worker);
            job.playSound(building.getID(), (EntityCitizen) worker);
        }
        return getState();
    }

    /**
     * The actual crafting logic.
     *
     * @return the next state to go to.
     */
    @Override
    protected IAIState craft()
    {
        if (currentRecipeStorage == null)
        {
            return START_WORKING;
        }

        if (currentRequest == null && job.getCurrentTask() != null)
        {
            return GET_RECIPE;
        }

        if (!walkToBuilding())
        {
            return getState();
        }

        job.setProgress(job.getProgress() + 1);

        worker.setItemInHand(InteractionHand.MAIN_HAND,
          currentRecipeStorage.getCleanedInput().get(worker.getRandom().nextInt(currentRecipeStorage.getCleanedInput().size())).getItemStack().copy());
        worker.setItemInHand(InteractionHand.OFF_HAND, currentRecipeStorage.getPrimaryOutput().copy());
        CitizenItemUtils.hitBlockWithToolInHand(worker, building.getPosition());

        currentRequest = job.getCurrentTask();

        if (currentRequest != null && (currentRequest.getState() == RequestState.CANCELLED || currentRequest.getState() == RequestState.FAILED))
        {
            currentRequest = null;
            incrementActionsDone(getActionRewardForCraftingSuccess());
            currentRecipeStorage = null;
            return START_WORKING;
        }

        final IAIState check = crush();
        if (check != getState())
        {
            // A state that is not this one is a detour, not a verdict on the order. checkForItems hands back
            // GATHERING_REQUIRED_MATERIALS when the input is sitting in the hut a few blocks away, and GET_RECIPE
            // when the recipe has to be picked again; crush() itself hands back START_WORKING when it has already
            // dealt with the request. Treating all three as a failure dropped orders the worker only had to walk
            // for. The base crafter goes where it is sent (AbstractEntityAICrafting#craft), so do that.
            return check;
        }

        if (job.getCraftCounter() >= job.getMaxCraftingCount())
        {
            incrementActionsDone(getActionRewardForCraftingSuccess());
            currentRecipeStorage = null;
            worker.decreaseSaturationForAction();
            resetValues();
            if (inventoryNeedsDump())
            {
                if (job.getMaxCraftingCount() == 0 && job.getProgress() == 0 && job.getCraftCounter() == 0 && currentRequest != null)
                {
                    job.finishRequest(true);
                }
            }
        }

        return getState();
    }

}
