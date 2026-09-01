package com.minecolonies.core.entity.ai.workers.service;

import com.minecolonies.api.advancements.AdvancementTriggers;
import com.minecolonies.api.colony.GraveData;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.ai.JobStatus;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.StatsUtil;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.colony.buildings.modules.GraveyardManagementModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGraveyard;
import com.minecolonies.core.colony.jobs.JobUndertaker;
import com.minecolonies.core.entity.ai.workers.AbstractEntityAIInteract;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.network.messages.client.VanillaParticleMessage;
import com.minecolonies.core.tileentities.TileEntityGrave;
import com.minecolonies.core.util.AdvancementUtils;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.research.util.ResearchConstants.*;
import static com.minecolonies.api.util.constant.CitizenConstants.FACING_DELTA_YAW;
import static com.minecolonies.api.util.constant.Constants.DEFAULT_SPEED;
import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;
import static com.minecolonies.api.util.constant.StatisticsConstants.CITIZENS_RESURRECTED;
import static com.minecolonies.api.util.constant.StatisticsConstants.GRAVES_DUG;
import static com.minecolonies.api.util.constant.TranslationConstants.MESSAGE_INFO_CITIZEN_UNDERTAKER_GRAVEYARD_NO_SPACE;
import static com.minecolonies.api.util.constant.TranslationConstants.MESSAGE_INFO_CITIZEN_UNDERTAKER_RESURRECTED_SUCCESS;
import static com.minecolonies.api.util.constant.UndertakerConstants.*;
/**
 * Undertaker AI class.
 */
public class EntityAIWorkUndertaker extends AbstractEntityAIInteract<JobUndertaker, BuildingGraveyard>
{
    /**
     * The random variable.
     */
    private Random random = new Random();

    /**
     * A counter to delay some task.
     */
    private int effortCounter = 0;

    /**
     * The state the effort currently accumulated was earned in. One counter is shared by emptying, resurrecting and
     * burying, and it used to be reset only on completion, so a task abandoned midway - the inventory filling during
     * EMPTY_GRAVE, a null grave sending him to IDLE - left its partial credit lying around for whatever ran next.
     */
    private IAIState effortState = null;

    /**
     * Changed after finished digging in order to dump the inventory.
     */
    private boolean shouldDumpInventory = false;

    /**
     * The current pos to wander at.
     */
    private BlockPos wanderPos = null;

    /**
     * The current pos to grave to build.
     */
    private Tuple<BlockPos, Direction> burialPos = null;

    /**
     * Constructor for the Undertaker. Defines the tasks the Undertaker executes.
     *
     * @param job a undertaker job to use.
     */
    public EntityAIWorkUndertaker(@NotNull final JobUndertaker job)
    {
        super(job);
        super.registerTargets(
          new AITarget(IDLE, START_WORKING, REQUEST_DELAY),
          new AITarget(START_WORKING, this::startWorking, STANDARD_DELAY),
          new AITarget(WANDER, this::wander, STANDARD_DELAY),
          new AITarget(EMPTY_GRAVE, this::emptyGrave, STANDARD_DELAY),
          new AITarget(TRY_RESURRECT, this::tryResurrect, STANDARD_DELAY),
          new AITarget(DIG_GRAVE, this::digGrave, STANDARD_DELAY),
          new AITarget(BURY_CITIZEN, this::buryCitizen, STANDARD_DELAY)
        );
        worker.setCanPickUpLoot(true);
    }

    @Override
    public Class<BuildingGraveyard> getExpectedBuildingClass()
    {
        return BuildingGraveyard.class;
    }

    /**
     * Prepares the undertaker for digging.
     * Also requests the tools and checks if the undertaker has queued graves.
     *
     * @return the next IAIState
     */
    @NotNull
    private IAIState startWorking()
    {
        worker.getCitizenData().setVisibleStatus(VisibleCitizenStatus.WORKING);

        requestTotemsIfNeeded();

        @Nullable final BlockPos currentGrave = building.getGraveToWorkOn();
        if (currentGrave != null)
        {
            if (!walkToBuilding())
            {
                return getState();
            }

            final BlockEntity entity = world.getBlockEntity(currentGrave);
            if (entity instanceof TileEntityGrave)
            {
                worker.getCitizenData().setJobStatus(JobStatus.WORKING);
                return EMPTY_GRAVE;
            }
            building.ClearCurrentGrave();
        }
        worker.getCitizenData().setJobStatus(JobStatus.IDLE);
        return WANDER;
    }

    /**
     * Ask the colony for the totems the resurrection ritual runs on.
     * <p>
     * Nothing used to put one in his hands. The Raising the Dead research turns the totem bonus on, keepX holds two
     * back out of his dump once he has them, and the break roll spends them - but the building filed no request and
     * the AI never went looking, so the entire research came down to "the player must remember to open the
     * undertaker's inventory and place them by hand", with no bubble and no hint that it wanted anything. He now
     * asks for them the way every other worker asks for what it consumes: only while the research is on, only up to
     * the two he is allowed to keep, and asynchronously, so a colony that cannot find a totem does not stall the
     * only worker who empties graves.
     */
    private void requestTotemsIfNeeded()
    {
        if (worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(USE_TOTEM) <= 0)
        {
            return;
        }

        final int held = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), ItemStackUtils::hasDeathProtection);
        if (held >= TOTEMS_TO_KEEP)
        {
            return;
        }

        checkIfRequestForItemExistOrCreateAsync(new ItemStack(Items.TOTEM_OF_UNDYING), TOTEMS_TO_KEEP - held, 1);
    }

    /**
     * The undertaker wander in the city, learning more about magic
     *
     * @return the next IAIState
     */
    @NotNull
    private IAIState wander()
    {
        if (worker.getNavigation().isDone())
        {
            if (building.isInBuilding(worker.blockPosition()))
            {
                EntityNavigationUtils.walkToRandomPosWithin(worker, 10, DEFAULT_SPEED, building.getCorners());
            }
            else
            {
                walkToBuilding();
            }
        }

        return IDLE;
    }

    /**
     * The undertaker empty the inventory from a grave to the graveyard inventory
     * The undertake will make multiple trip if needed
     *
     * @return the next IAIState
     */
    private IAIState emptyGrave()
    {
        @Nullable final BuildingGraveyard buildingGraveyard = building;

        if (buildingGraveyard == null || checkForToolOrWeapon(ModEquipmentTypes.shovel.get()) || buildingGraveyard.getGraveToWorkOn() == null)
        {
            return IDLE;
        }

        worker.getCitizenData().setVisibleStatus(EMPTYING_ICON);
        worker.setSprinting(worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(UNDERTAKER_RUN) > 0);
        unequip();

        @Nullable final BlockPos gravePos = buildingGraveyard.getGraveToWorkOn();

        // Still moving to the block
        if (walkWithProxy(gravePos, 3))
        {
            return getState();
        }

        final BlockEntity entity = world.getBlockEntity(gravePos);
        if (entity instanceof TileEntityGrave)
        {
            if (((TileEntityGrave) entity).isEmpty())
            {
                return TRY_RESURRECT;
            }

            if (worker.getInventoryCitizen().isFull())
            {
                return INVENTORY_FULL;
            }

            if (!spendEffort(EFFORT_EMPTY_GRAVE, getPrimarySkillLevel()))
            {
                worker.swingForAttack(InteractionHand.MAIN_HAND);
                return getState();
            }

            //at position - try to take all item
            if (InventoryUtils.transferAllItemHandler(((TileEntityGrave) entity).getInventory(), worker.getInventoryCitizen()))
            {
                return TRY_RESURRECT;
            }
        }

        return IDLE;
    }

    /**
     * The undertaker dig (remove) the grave tile entity of a fallen citizen
     *
     * @return the next IAIState
     */
    private IAIState digGrave()
    {
        @Nullable final BuildingGraveyard buildingGraveyard = building;

        if (checkForToolOrWeapon(ModEquipmentTypes.shovel.get()) || buildingGraveyard.getGraveToWorkOn() == null)
        {
            return IDLE;
        }

        worker.getCitizenData().setVisibleStatus(DIGGING_ICON);
        worker.setSprinting(worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(UNDERTAKER_RUN) > 0);

        @Nullable final BlockPos gravePos = buildingGraveyard.getGraveToWorkOn();

        if (gravePos == null)
        {
            return IDLE;
        }

        // Still moving to the block
        if (walkWithProxy(gravePos, 3))
        {
            return getState();
        }

        worker.setSprinting(false);

        final BlockEntity entity = world.getBlockEntity(gravePos);
        if (entity instanceof TileEntityGrave)
        {
            //at position
            if (!digIfAble(gravePos, entity))
            {
                return getState();
            }

            worker.decreaseSaturationForAction();
            // Through the experience handler, not addXpToSkill(primary): the flat form put all 7.5 into Strength and
            // left Mana with nothing but the 0.05 per block that mineBlock grants, which is 0.075 a grave at best.
            // Mana is the stat that sets the ritual length and feeds the resurrection chance, so an undertaker's
            // Mana was in practice whatever it happened to be on the day he was hired - about 21,000 graves to move
            // it from 1 to 25. The handler applies the usual building, intelligence and research modifiers and gives
            // the secondary skill its normal half share, exactly as it does for every other worker.
            worker.getCitizenExperienceHandler().addExperience(XP_PER_DIG);
            StatsUtil.trackStat(building, GRAVES_DUG, 1);
            building.getColony().getStatisticsManager().increment(GRAVES_DUG, worker.getCitizenColonyHandler().getColonyOrRegister().getDay());
            return BURY_CITIZEN;
        }

        return IDLE;
    }

    /**
     * Checks if we can dig a grave, and does so if we can.
     *
     * @param position the grave to harvest.
     * @param entity
     * @return true if we harvested or not supposed to.
     */
    private boolean digIfAble(final BlockPos position, final BlockEntity entity)
    {
        if (!checkForToolOrWeapon(ModEquipmentTypes.shovel.get()))
        {
            equipShovel();
            final GraveData graveData = (GraveData) ((TileEntityGrave) entity).getGraveData();
            if (mineBlock(position))
            {
                worker.decreaseSaturationForContinuousAction();
                building.ClearCurrentGrave();
                building.getFirstModuleOccurance(GraveyardManagementModule.class).setLastGraveData(graveData);
                return true;
            }
        }
        return false;
    }

    /**
     * Attempt to resurrect buried citizen from its citizen data
     * Randomize to see if resurrection successful and resurrect if need be
     *
     * @return the next IAIState
     */
    private IAIState tryResurrect()
    {
        @Nullable final BuildingGraveyard buildingGraveyard = building;

        if (checkForToolOrWeapon(ModEquipmentTypes.shovel.get())
              || buildingGraveyard.getGraveToWorkOn() == null)
        {
            return IDLE;
        }

        unequip();

        @Nullable final BlockPos gravePos = buildingGraveyard.getGraveToWorkOn();

        if (gravePos == null)
        {
            return IDLE;
        }

        // Still moving to the block
        if (walkWithProxy(gravePos, 3))
        {
            return getState();
        }

        final BlockEntity entity = world.getBlockEntity(gravePos);
        if (entity instanceof TileEntityGrave)
        {
            if (!spendEffort(EFFORT_RESURRECT, getSecondarySkillLevel()))
            {
                worker.getLookControl().setLookAt(gravePos.getX(), gravePos.getY(), gravePos.getZ(), FACING_DELTA_YAW, worker.getMaxHeadXRot());
                worker.swingForAttack(InteractionHand.MAIN_HAND);
                new VanillaParticleMessage(gravePos.getX() + 0.5f, gravePos.getY() + 0.05f, gravePos.getZ() + 0.5f, ParticleTypes.ENCHANT).sendToTrackingEntity(worker);
                return getState();
            }

            shouldDumpInventory = true;
            final double chance = getResurrectChance(buildingGraveyard);

            if (getTotemResurrectChance() > 0 && random.nextDouble() <= TOTEM_BREAK_CHANCE)
            {
                worker.getInventoryCitizen()
                  .extractItem(InventoryUtils.findFirstSlotInItemHandlerWith(worker.getInventoryCitizen(), ItemStackUtils::hasDeathProtection), 1, false);
                worker.playSound(SoundEvents.TOTEM_USE, 1.0f, 1.0f);
            }

            if (chance >= random.nextDouble())
            {
                new VanillaParticleMessage(gravePos.getX() + 0.5f, gravePos.getY() + 0.05f, gravePos.getZ() + 0.5f, ParticleTypes.HEART).sendToTrackingEntity(worker);

                final GraveData graveData = (GraveData) ((TileEntityGrave) entity).getGraveData();
                final ICitizenData citizenData = buildingGraveyard.getColony()
                                                   .getCitizenManager()
                                                   .resurrectCivilianData(graveData.getCitizenDataNBT(), true, world, gravePos);
                MessageUtils.format(MESSAGE_INFO_CITIZEN_UNDERTAKER_RESURRECTED_SUCCESS, citizenData.getName()).sendTo(buildingGraveyard.getColony()).forManagers();
                StatsUtil.trackStat(building, CITIZENS_RESURRECTED, 1);
                building.getColony().getStatisticsManager().increment(CITIZENS_RESURRECTED, worker.getCitizenColonyHandler().getColonyOrRegister().getDay());
                worker.getCitizenColonyHandler().getColony().getCitizenManager().updateCitizenMourn(citizenData, false);
                AdvancementUtils.TriggerAdvancementPlayersForColony(worker.getCitizenColonyHandler().getColony(),
                  playerMP -> AdvancementTriggers.CITIZEN_RESURRECT.get().trigger(playerMP));
                buildingGraveyard.getFirstModuleOccurance(GraveyardManagementModule.class).setLastGraveData(null);
                world.setBlockAndUpdate(gravePos, Blocks.AIR.defaultBlockState());
                return INVENTORY_FULL;
            }
        }

        return DIG_GRAVE;
    }

    /**
     * Calculate chance of resurrection from multiple factor: Undertaker Skill, Building Level, Research, Mystical Sites in the city
     *
     * @param buildingGraveyard the building.
     * @return the chance of resurrection
     */
    private double getResurrectChance(@NotNull final BuildingGraveyard buildingGraveyard)
    {
        double totemChance = getTotemResurrectChance();
        double chance = buildingGraveyard.getBuildingLevel() * RESURRECT_BUILDING_LVL_WEIGHT +
                          worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Mana) * RESURRECT_WORKER_MANA_LVL_WEIGHT +
                          worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(RESURRECT_CHANCE) +
                          totemChance;

        final double cap =
          MAX_RESURRECTION_CHANCE
            + buildingGraveyard.getBuildingLevel() * MAX_RESURRECTION_CHANCE_GRAVEYARD_LVL_BONUS
            + worker.getCitizenColonyHandler().getColonyOrRegister().getServerBuildingManager().getMysticalSiteMaxBuildingLevel() * MAX_RESURRECTION_CHANCE_MYSTICAL_LVL_BONUS
            + totemChance;
        if (chance > cap)
        {
            chance = cap;
        }
        return chance;
    }

    /**
     * Check for a totem of undying, the required research, and get the increased resurrection chance
     *
     * @return the chance increase that the totem provides
     */
    private double getTotemResurrectChance()
    {
        if (worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(USE_TOTEM) > 0)
        {
            final int totems = InventoryUtils.getItemCountInItemHandler(worker.getInventoryCitizen(), ItemStackUtils::hasDeathProtection);

            if (totems > 0)
            {
                AdvancementUtils.TriggerAdvancementPlayersForColony(worker.getCitizenColonyHandler().getColony(), AdvancementTriggers.UNDERTAKER_TOTEM.get()::trigger);
            }

            if (totems == 1)
            {
                return SINGLE_TOTEM_RESURRECTION_CHANCE_BONUS;
            }
            else if (totems > 1)
            {
                return MULTIPLE_TOTEMS_RESURRECTION_CHANCE_BONUS;
            }
        }

        return 0;
    }

    /**
     * The Undertaker search for an empty grave site in the graveyard and build a named graved with
     * the name of the citizen and its job as text
     *
     * @return the next IAIState
     */
    private IAIState buryCitizen()
    {
        @Nullable final BuildingGraveyard buildingGraveyard = building;
        final GraveyardManagementModule module = buildingGraveyard.getFirstModuleOccurance(GraveyardManagementModule.class);

        if (checkForToolOrWeapon(ModEquipmentTypes.shovel.get()) || module.getLastGraveData() == null)
        {
            return IDLE;
        }
        worker.getCitizenData().setVisibleStatus(BURYING_ICON);

        if (burialPos == null || !world.getBlockState(burialPos.getA()).canBeReplaced())
        {
            burialPos = building.getRandomFreeVisualGravePos();
        }

        if (burialPos == null || burialPos.getA() == null)
        {
            // couldn't find a place to dig a grave
            MessageUtils.forCitizen(worker, Component.translatable(MESSAGE_INFO_CITIZEN_UNDERTAKER_GRAVEYARD_NO_SPACE, module.getLastGraveData().getCitizenName()))
              .sendTo(worker.getCitizenColonyHandler().getColonyOrRegister().getMessagePlayerEntities());
            return IDLE;
        }

        if (walkWithProxy(burialPos.getA(), 4))
        {
            return getState();
        }

        if (!spendEffort(EFFORT_BURY, getPrimarySkillLevel()))
        {
            equipShovel();
            CitizenItemUtils.hitBlockWithToolInHand(worker, burialPos.getA(), false);
            return getState();
        }
        unequip();

        module.buryCitizenHere(burialPos, worker);
        //Disabled until Mourning AI update: worker.getCitizenColonyHandler().getColony().setNeedToMourn(false, buildingGraveyard.getLastGraveData().getCitizenName());
        AdvancementUtils.TriggerAdvancementPlayersForColony(worker.getCitizenColonyHandler().getColony(), playerMP -> AdvancementTriggers.CITIZEN_BURY.get().trigger(playerMP));

        module.setLastGraveData(null);
        burialPos = null;
        shouldDumpInventory = true;

        return INVENTORY_FULL;
    }

    /**
     * Put one call's worth of work into the current task and report whether it is finished.
     * <p>
     * The credit belongs to the state that earned it: arriving in a different state throws away whatever the
     * previous one had accumulated rather than handing it over.
     *
     * @param required  the effort the task takes.
     * @param perCall   the effort this call contributes, normally a skill level.
     * @return true when the task is done and the counter has been cleared.
     */
    private boolean spendEffort(final int required, final int perCall)
    {
        if (effortState != getState())
        {
            effortState = getState();
            effortCounter = 0;
        }

        if (effortCounter >= required)
        {
            effortCounter = 0;
            effortState = null;
            return true;
        }

        effortCounter += perCall;
        return false;
    }

    /**
     * Called to check when the InventoryShouldBeDumped.
     *
     * @return true if the conditions are met
     */
    @Override
    protected boolean wantInventoryDumped()
    {
        if (shouldDumpInventory)
        {
            shouldDumpInventory = false;
            return true;
        }
        return false;
    }

    /**
     * Sets the shovel as held item.
     */
    private void equipShovel()
    {
        CitizenItemUtils.setHeldItem(worker, InteractionHand.MAIN_HAND, getShovelSlot());
    }

    /**
     * Sets the nothing as held item.
     */
    private void unequip()
    {
        CitizenItemUtils.removeHeldItem(worker);
    }

    /**
     * Get's the slot in which the shovel is in.
     *
     * @return slot number
     */
    private int getShovelSlot()
    {
        return InventoryUtils.getFirstSlotOfItemHandlerContainingEquipment(getInventory(), ModEquipmentTypes.shovel.get(), TOOL_LEVEL_WOOD_OR_GOLD, building.getMaxEquipmentLevel());
    }

    /**
     * Returns the undertaker's worker instance. Called from outside this class.
     *
     * @return citizen object
     */
    @Nullable
    public AbstractEntityCitizen getCitizen()
    {
        return worker;
    }
}
