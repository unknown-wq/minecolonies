package com.minecolonies.core.entity.ai.workers.guard;

import net.minecraft.world.phys.Vec3;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.requestsystem.location.ILocation;
import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.ai.statemachine.AIOneTimeEventTarget;
import com.minecolonies.api.entity.ai.statemachine.AITarget;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.*;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.EntityListModule;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.entity.ai.workers.util.MinerLevel;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.other.SittingEntity;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.network.messages.client.SleepingParticleMessage;
import com.minecolonies.core.util.TeleportHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Random;

import static com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState.*;
import static com.minecolonies.api.research.util.ResearchConstants.*;
import static com.minecolonies.api.util.constant.Constants.GLOW_EFFECT_DURATION;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.GuardConstants.GUARD_FOLLOW_LOSE_RANGE;
import static com.minecolonies.api.util.constant.GuardConstants.GUARD_FOLLOW_TIGHT_RANGE;
import static com.minecolonies.api.util.constant.GuardConstants.DEFAULT_VISION;
import static com.minecolonies.core.colony.buildings.AbstractBuildingGuards.HOSTILE_LIST;

/**
 * Class taking of the abstract guard methods for all fighting AIs.
 *
 * @param <J> the generic job.
 */
public abstract class AbstractEntityAIGuard<J extends AbstractJobGuard<J>, B extends AbstractBuildingGuards> extends AbstractEntityAIFight<J, B>
{
    /**
     * Entities to kill before dumping into chest.
     */
    private static final int ACTIONS_UNTIL_DUMPING = 5;

    /**
     * Max derivation of current position when patrolling.
     */
    private static final int MAX_PATROL_DERIVATION = 80;

    /**
     * How far off patrols are alterated to match a raider attack point, sq dist
     */
    public static final int PATROL_DEVIATION_RAID_POINT = 40 * 40;

    /**
     * Max derivation of current position when following..
     */
    private static final int MAX_FOLLOW_DERIVATION = 30;

    /**
     * Max derivation of current position when guarding.
     */
    private static final int MAX_GUARD_DERIVATION = 10;

    /**
     * How far from his own hut a wounded guard has to be before running home is worth doing, in blocks.
     * <p>
     * The test it feeds is a squared distance, and it used to be compared against a bare 20 -- 4.47 blocks. A guard
     * standing five blocks from his own door decided he was far from home, disengaged, and then spent about
     * seventy-five seconds regenerating with his post empty. Every neighbouring constant here is a linear block
     * count, which is what that 20 was plainly meant to be.
     */
    private static final int MIN_FLEE_HOME_DISTANCE = 20;

    /**
     * The amount of time the guard counts as in combat after last combat action
     */
    protected static final int COMBAT_TIME = 30 * 20;

    /**
     * The current blockPos we're patrolling at.
     */
    protected BlockPos currentPatrolPoint = null;

    /**
     * The guard building assigned to this job.
     */
    protected final IGuardBuilding buildingGuards;

    /**
     * The interval between sleeping particles
     */
    private static final int PARTICLE_INTERVAL = 30;

    /**
     * Interval between sleep checks
     */
    private static final int SHOULD_SLEEP_INTERVAL = 200;

    /**
     * Interval between guard task updates
     */
    private static final int GUARD_TASK_INTERVAL = 100;

    /**
     * Interval between follow-target refreshes while the task is Follow.
     * <p>
     * Following used to be driven solely by {@link #decide()} on {@link #GUARD_TASK_INTERVAL}, so an escort noticed
     * that its player had moved at most once every five seconds -- which read as guards standing around for ages and
     * then teleporting to catch up. A tenth of that is enough to look attentive; it is deliberately not every tick,
     * because each refresh of a moving target is a fresh path job.
     */
    private static final int FOLLOW_REFRESH_INTERVAL = 10;

    /**
     * How close to his guard post a guard stands before {@link #guardMovement()} stops ordering him about, in
     * blocks. The same 5 the walk order has always used as its reach distance, named so the "am I there" test and
     * the walk cannot drift apart.
     */
    private static final int GUARD_POST_RANGE = 5;

    /**
     * Interval between guard regen updates
     */
    private static final int GUARD_REGEN_INTERVAL = 40;

    /**
     * Amount of regular actions before the action counter is increased
     */
    private static final int ACTION_INCREASE_INTERVAL = 10;

    /**
     * The timer for sleeping.
     */
    private int sleepTimer = 0;

    /**
     * Timer for the wakeup AI.
     */
    protected int wakeTimer = 0;

    /**
     * Timer for fighting, goes down to 0 when hasnt been fighting for a while
     */
    protected int fighttimer = 0;

    /**
     * The sleeping guard we found
     */
    protected WeakReference<EntityCitizen> sleepingGuard = new WeakReference<>(null);

    /**
     * Random generator for this AI.
     */
    private Random randomGenerator = new Random();

    /**
     * Small timer for increasing actions done for continuous actions
     */
    private int regularActionTimer = 0;

    /**
     * The last position a guard did some guard task on
     */
    private BlockPos lastGuardActionPos;

    public AbstractEntityAIGuard(@NotNull final J job)
    {
        super(job);
        super.registerTargets(
          new AITarget(DECIDE, CombatAIStates.NO_TARGET, 1),
          new AITarget(CombatAIStates.NO_TARGET, this::shouldSleep, () -> GUARD_SLEEP, SHOULD_SLEEP_INTERVAL),
          new AITarget(GUARD_SLEEP, this::sleep, 1),
          new AITarget(GUARD_SLEEP, this::sleepParticles, PARTICLE_INTERVAL),
          new AITarget(GUARD_REGEN, this::regen, GUARD_REGEN_INTERVAL),
          new AITarget(GUARD_FLEE, this::flee, 20),
          new AITarget(CombatAIStates.ATTACKING, this::shouldFlee, () -> GUARD_FLEE, GUARD_REGEN_INTERVAL),
            new AITarget(CombatAIStates.NO_TARGET, this::shouldFlee, () -> GUARD_FLEE, GUARD_REGEN_INTERVAL),
          new AITarget(CombatAIStates.NO_TARGET, this::decide, GUARD_TASK_INTERVAL),
          // The fast lane for Follow, behind decide() in the list so that everything decide() owns -- rallying,
          // armour, the action counter, standing the glow effect up and down -- still runs on its own clock. This
          // only keeps the walk target fresh between decides.
          new AITarget(CombatAIStates.NO_TARGET, this::wantsFollowRefresh, this::follow, FOLLOW_REFRESH_INTERVAL),
          new AITarget(GUARD_WAKE, this::wakeUpGuard, TICKS_SECOND),

          new AITarget(CombatAIStates.ATTACKING, this::inCombat, 8)
        );

        buildingGuards = building;
        lastGuardActionPos = buildingGuards.getPosition();
    }

    /**
     * Updates fight timer during combat
     */
    private IAIState inCombat()
    {
        if (fighttimer <= 0)
        {
            onCombatEnter();
        }

        if (!hasTool())
        {
            return PREPARING;
        }

        fighttimer = COMBAT_TIME;
        return null;
    }

    /**
     * On combat enter
     */
    private void onCombatEnter()
    {
        worker.setCanBeStuck(false);
        worker.getNavigation().getPathingOptions().setCanUseRails(false);
        worker.getNavigation().getPathingOptions().setCanUseBoat(false);
    }

    /**
     * On combat leave
     */
    private void onCombatLeave()
    {
        worker.getNavigation().getPathingOptions().setCanUseRails(((EntityCitizen) worker).canPathOnRails());
        worker.getNavigation().getPathingOptions().setCanUseBoat(((EntityCitizen) worker).canPathOnBoat());
        worker.setCanBeStuck(true);

        // Thirty seconds after the last combat action there is nothing in the threat table worth keeping: the guard
        // will re-acquire from scratch, and every entry held a strong reference to an entity that is very likely
        // already gone. resetTable() existed and was called from nowhere.
        ((EntityCitizen) worker).getThreatTable().resetTable();
    }

    /**
     * Wake up a nearby sleeping guard
     *
     * @return next state
     */
    private IAIState wakeUpGuard()
    {
        if (sleepingGuard.get() == null || !(sleepingGuard.get().getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard) || !sleepingGuard.get()
                                                                                                                                          .getCitizenJobHandler()
                                                                                                                                          .getColonyJob(AbstractJobGuard.class)
                                                                                                                                          .isAsleep())
        {
            return CombatAIStates.NO_TARGET;
        }

        wakeTimer++;
        if (wakeTimer > 30)
        {
            return CombatAIStates.NO_TARGET;
        }

        final EntityCitizen sleepingCitizen = sleepingGuard.get();

        // Move into range
        if (BlockPosUtil.getDistanceSquared(sleepingCitizen.blockPosition(), worker.blockPosition()) > 2.25)
        {
            walkToUnSafePos(sleepingCitizen.blockPosition());
        }
        else
        {
            worker.swing(InteractionHand.OFF_HAND);
            sleepingCitizen.hurt(world.damageSources().source(DamageSourceKeys.WAKEY, this.worker), 1);
            sleepingCitizen.setLastHurtByMob(worker);
            return CombatAIStates.NO_TARGET;
        }

        return getState();
    }

    /**
     * Whether the guard should fall asleep.
     *
     * @return true if so
     */
    private boolean shouldSleep()
    {
        // Nodding off is not gated on the time of day: measured on a stand at high noon, a knight spent
        // 11.1 % of a patrol window and an archer 12.7-16.8 % of a guard-post window in GUARD_SLEEP, with
        // another 1.3-5.0 % spent walking over to hit a sleeping colleague awake. That is a game mechanic
        // (the SLEEP_LESS research reduces it), so the switch defaults to the stock behaviour.
        if (!MineColonies.getConfig().getServer().guardsFallAsleep.get())
        {
            return false;
        }

        // Used to read `|| target != null ||` as well, against a field declared on this class and assigned nowhere,
        // so the clause was always false while looking like it worked. Nothing is lost by dropping it: this
        // transition only fires out of NO_TARGET, and the combat timer below covers a guard who has just been in a
        // fight.
        if (worker.getLastHurtByMob() != null || fighttimer > 0 || job.getCitizen().getCitizenDiseaseHandler().isSick())
        {
            return false;
        }

        // Not in a fluid. A guard nods off wherever it happens to stand, and standing in water is a normal thing for
        // one to do - a patrol route along a shore, a bridge with a gap, a swim back from a chase. Once asleep it is
        // pinned to a SittingEntity, and the only things that end the nap are damage from a mob and the timer running
        // out, neither of which drowning is: hurt() is what sets getLastHurtByMob, and drowning has no attacker.
        if (worker.isInWater() || worker.isInLava())
        {
            return false;
        }

        final double chance = 1 / (1 + worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(SLEEP_LESS));

        // Chance to fall asleep every 10sec, Chance is 1 in (10 + level/2) = 1 in Level1:5,Level2:6 Level6:8 Level 12:11 etc
        if (worker.getRandom().nextInt((int) (worker.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) * 0.5) + 20) == 1
              && worker.getRandom().nextDouble() < chance)
        {
            // Sleep for 2500-3000 ticks
            sleepTimer = worker.getRandom().nextInt(500) + 2500;
            worker.getNavigation().stop();
            SittingEntity.sitDown(worker.blockPosition(), worker, sleepTimer);

            return true;
        }

        return false;
    }

    /**
     * Emits sleeping particles and regens hp when asleep
     *
     * @return the next state to go into
     */
    private IAIState sleepParticles()
    {
        new SleepingParticleMessage(worker.getX(), worker.getY() + 2.0d, worker.getZ()).sendToTrackingEntity(worker);

        if (worker.getHealth() < worker.getMaxHealth())
        {
            worker.setHealth(worker.getHealth() + 0.5f);
        }

        return null;
    }

    /**
     * Sleep activity
     *
     * @return the next state to go into
     */
    protected IAIState sleep()
    {
        // The fluid check is here as well as in shouldSleep because water can arrive after the guard nodded off: a
        // flowing source that finally reached it, a raider knocking it off a bridge, a bucket. Waking is the whole
        // remedy - stopSleeping already stands the worker up out of the SittingEntity.
        if (worker.getLastHurtByMob() != null || worker.isInWater() || worker.isInLava() || (sleepTimer -= getTickRate()) < 0)
        {
            stopSleeping();
            ((EntityCitizen) worker).getThreatTable().removeCurrentTarget();
            worker.setLastHurtByMob(null);
            return CombatAIStates.NO_TARGET;
        }

        worker.getLookControl()
          .setLookAt(worker.getX() + worker.getDirection().getStepX(),
            worker.getY() + worker.getDirection().getStepY(),
            worker.getZ() + worker.getDirection().getStepZ(),
            0f,
            30f);
        ((LookHandler) worker.getLookControl()).setLookAtCooldown(sleepTimer);
        return null;
    }

    /**
     * Stops the guard from sleeping
     */
    protected void stopSleeping()
    {
        if (getState() == GUARD_SLEEP)
        {
            worker.stopRiding();
            worker.setPos(worker.getX(), worker.getY() + 1, worker.getZ());
            worker.getCitizenExperienceHandler().addExperience(1);
            ((LookHandler) worker.getLookControl()).setLookAtCooldown(2);
        }
    }

    /**
     * How far, horizontally, a guard working out of the given building can pick a target out.
     * <p>
     * {@code IGuardBuilding#getBonusVision} -- {@code BASE_VISION_RANGE + level * VISION_RANGE_PER_LEVEL}, so 18 at
     * level one and 30 at level five -- existed but was read by nothing on the guard path, which meant upgrading a
     * guard tower bought no extra sight at all. It is the floor here rather than the whole answer because a ranged
     * guard's eyes also have to keep up with his weapon; see {@code RangeCombatAI#getSearchRange}.
     *
     * @param user the guard.
     * @return the horizontal search range in blocks.
     */
    public static int getGuardVisionRange(final AbstractEntityCitizen user)
    {
        if (user.getCitizenData() != null && user.getCitizenData().getWorkBuilding() instanceof IGuardBuilding guardBuilding)
        {
            return Math.max(DEFAULT_VISION, guardBuilding.getBonusVision());
        }
        return DEFAULT_VISION;
    }

    /**
     * Whether the guard should flee
     *
     * @return
     */
    private boolean shouldFlee()
    {
        if (buildingGuards.shallRetrieveOnLowHealth() && worker.getHealth() < ((int) worker.getMaxHealth() * 0.2D) && worker.distanceToSqr(Vec3.atCenterOf(building.getID())) > MIN_FLEE_HOME_DISTANCE * MIN_FLEE_HOME_DISTANCE)
        {
            return worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(RETREAT) > 0;
        }

        return false;
    }

    /**
     * Regen at the building and continue when more than half health.
     *
     * @return next state to go to.
     */
    private IAIState regen()
    {
        if (((EntityCitizen) worker).getThreatTable().getTargetMob() != null && ((EntityCitizen) worker).getThreatTable().getTargetMob().distanceTo(worker) < 10)
        {
            return CombatAIStates.ATTACKING;
        }

        if (worker.getHealth() < ((int) worker.getMaxHealth() * 0.75D) && buildingGuards.shallRetrieveOnLowHealth())
        {
            if (!worker.hasEffect(MobEffects.REGENERATION))
            {
                worker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200));
            }
            return GUARD_REGEN;
        }

        return START_WORKING;
    }

    /**
     * Flee to the building.
     *
     * @return next state to go to.
     */
    private IAIState flee()
    {
        if (!worker.hasEffect(MobEffects.SPEED))
        {
            final double effect = worker.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(FLEEING_SPEED);
            if (effect > 0)
            {
                worker.addEffect(new MobEffectInstance(MobEffects.SPEED, 200, (int) (0 + effect)));
            }
        }

        if (!walkToBuilding())
        {
            return GUARD_FLEE;
        }

        return GUARD_REGEN;
    }

    /**
     * Guard at a specific position.
     *
     * @return the next state to run into.
     */
    private IAIState guard()
    {
        guardMovement();
        return getState();
    }

    /**
     * Movement when guarding.
     * <p>
     * This used to walk to the post on a 1-in-10 roll per decide(), i.e. once every fifty seconds on average and
     * unboundedly worse on bad luck, which is what "my knight ignores his guard spot" looks like in play. Distance
     * is what the roll was standing in for: a guard on his post has nothing to do here, a guard off it should not
     * wait for dice. The ranger and druid overrides keep their own idle repositioning and are unaffected.
     */
    public void guardMovement()
    {
        final BlockPos guardPos = buildingGuards.getGuardPos(worker);
        if (BlockPosUtil.getDistance2D(worker.blockPosition(), guardPos) > GUARD_POST_RANGE)
        {
            walkToUnSafePos(guardPos, GUARD_POST_RANGE);
        }
    }

    /**
     * Whether the follow fast lane applies: the task is Follow and no rally banner overrides it. Kept cheap on
     * purpose -- it is evaluated every {@link #FOLLOW_REFRESH_INTERVAL} ticks on every guard, following or not.
     *
     * @return true if the follow target should be refreshed.
     */
    private boolean wantsFollowRefresh()
    {
        return buildingGuards != null && buildingGuards.getRallyLocation() == null && GuardTaskSetting.FOLLOW.equals(buildingGuards.getTask());
    }

    /**
     * Follow a player.
     *
     * @return the next state to run into.
     */
    private IAIState follow()
    {
        final BlockPos followPos = buildingGuards.getPositionToFollow();
        final long distance = BlockPosUtil.getDistance2D(worker.blockPosition(), followPos);
        if (distance > MAX_FOLLOW_DERIVATION)
        {
            TeleportHelper.teleportCitizen(worker, worker.level(), followPos);
            return null;
        }

        // Only walk while actually outside the formation range. The old code asked for the walk unconditionally,
        // which the navigation deduplicated as long as the player stood still -- but the fast lane above calls this
        // ten times as often, and a guard already in position must not pay a path job for each call just because
        // its player shifted a block.
        final int range = buildingGuards.isTightGrouping() ? GUARD_FOLLOW_TIGHT_RANGE : GUARD_FOLLOW_LOSE_RANGE;
        if (distance > range)
        {
            walkToUnSafePos(followPos, range);
        }
        return null;
    }

    @Override
    protected int getActionsDoneUntilDumping()
    {
        return ACTIONS_UNTIL_DUMPING * building.getBuildingLevelEquivalent();
    }

    /**
     * Rally to a location. This function assumes that the given location is reachable by the worker.
     *
     * @return the next state to run into.
     */
    private IAIState rally(final ILocation location)
    {
        final ICitizenData citizenData = worker.getCitizenData();
        if (!walkToUnSafePos(location.getInDimensionLocation()
                                             .offset(randomGenerator.nextInt(GUARD_FOLLOW_TIGHT_RANGE) - GUARD_FOLLOW_TIGHT_RANGE / 2,
                                               0,
                                               randomGenerator.nextInt(GUARD_FOLLOW_TIGHT_RANGE) - GUARD_FOLLOW_TIGHT_RANGE / 2),
          GUARD_FOLLOW_TIGHT_RANGE) && citizenData != null)
        {
            if (!worker.hasEffect(MobEffects.SPEED))
            {
                // Guards will rally faster with higher skill.
                // Considering 99 is the maximum for any skill, the maximum theoretical getJobModifier() = 99 + 99/4 = 124. We want them to have Speed 5
                // when they're at half-max, so at about skill60. Therefore, divide the skill by 20.
                worker.addEffect(new MobEffectInstance(MobEffects.SPEED,
                  5 * TICKS_SECOND,
                    Mth.clamp((citizenData.getCitizenSkillHandler().getLevel(Skill.Adaptability) / 30), 0, 3),
                  false,
                  false));
            }
        }

        return null;
    }

    @Override
    protected IAIState startWorkingAtOwnBuilding()
    {
        if (buildingGuards != null)
        {
            buildingGuards.setTempNextPatrolPoint(buildingGuards.getPosition());
        }
        return DECIDE;
    }

    /**
     * Provides a random patrol point from all buildings in the colony when the guard is set to automatic patrol mode.
     * @return a BlockPos of the patrol point.
     */
    protected BlockPos randomPatrolPoint()
    {
        return buildingGuards.getColony().getServerBuildingManager().getRandomBuilding(b -> true);
    }

    /**
     * Patrol between a list of patrol points.
     *
     * @return the next patrol point to go to.
     */
    public IAIState patrol()
    {
        if (buildingGuards.requiresManualTarget())
        {
            if (currentPatrolPoint == null || walkToSafePos(currentPatrolPoint) || !WorldUtil.isEntityBlockLoaded(world, currentPatrolPoint))
            {
                currentPatrolPoint = null;
                if (!EntityNavigationUtils.walkToRandomPos(worker, 20, 1.0))
                {
                    return getState();
                }

                if (worker.getRandom().nextInt(5) <= 1)
                {
                    currentPatrolPoint = randomPatrolPoint();
                    if (currentPatrolPoint != null)
                    {
                        walkToSafePos(currentPatrolPoint);
                    }
                }
            }
        }
        else
        {
            currentPatrolPoint = buildingGuards.getNextPatrolTarget(false);
            if (currentPatrolPoint != null && (!WorldUtil.isEntityBlockLoaded(world, currentPatrolPoint) || walkToSafePos(currentPatrolPoint)))
            {
                setCurrentDelay(10);
                buildingGuards.arrivedAtPatrolPoint(worker);
            }
        }
        return null;
    }

    /**
     * Patrol between all completed nodes in the assigned mine
     *
     * @return the next point to patrol to
     */
    public IAIState patrolMine()
    {
        if (buildingGuards.getMinePos() == null)
        {
            return PREPARING;
        }
        if (currentPatrolPoint == null || walkToSafePos(currentPatrolPoint))
        {
            final IBuilding building = buildingGuards.getColony().getServerBuildingManager().getBuilding(buildingGuards.getMinePos());
            if (building != null)
            {
                if (building instanceof BuildingMiner)
                {
                    final BuildingMiner buildingMiner = (BuildingMiner) building;
                    final MinerLevel level = buildingMiner.getModule(BuildingModules.MINER_LEVELS).getCurrentLevel();
                    if (level == null)
                    {
                        setNextPatrolTarget(buildingMiner.getPosition());
                    }
                    else
                    {
                        setNextPatrolTarget(level.getRandomCompletedNode(buildingMiner));
                    }
                }
                else
                {
                    buildingGuards.getModule(BuildingModules.GUARD_SETTINGS).getSetting(AbstractBuildingGuards.GUARD_TASK).set(GuardTaskSetting.PATROL);
                }
            }
            else
            {
                buildingGuards.getModule(BuildingModules.GUARD_SETTINGS).getSetting(AbstractBuildingGuards.GUARD_TASK).set(GuardTaskSetting.PATROL);
            }
        }
        return null;
    }

    /**
     * Sets the next patrol target.
     *
     * @param target the next patrol target.
     */
    private void setNextPatrolTarget(final BlockPos target)
    {
        currentPatrolPoint = target;
    }

    /**
     * Get the current patrol point
     *
     * @return
     */
    public BlockPos getCurrentPatrolPoint()
    {
        return currentPatrolPoint;
    }

    /**
     * Check if the worker has the required tool to fight.
     *
     * @return true if so.
     */
    public boolean hasTool()
    {
        for (final EquipmentTypeEntry toolType : toolsNeeded)
        {
            if (!InventoryUtils.hasItemHandlerEquipmentWithLevel(getInventory(), toolType, 0, buildingGuards.getMaxEquipmentLevel()))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Assigning the guard to help a citizen.
     *
     * @param attacker the citizens attacker.
     */
    public void startHelpCitizen(final LivingEntity attacker)
    {
        if (canHelp(attacker.blockPosition()))
        {
            ((IThreatTableEntity) worker).getThreatTable().addThreat(attacker, 20);
            registerTarget(new AIOneTimeEventTarget(CombatAIStates.ATTACKING));
        }
    }

    /**
     * Check if we can help a citizen
     *
     * @param pos
     * @return true if not fighting/helping already
     */
    public boolean canHelp(final BlockPos pos)
    {
        if ((getState() == CombatAIStates.NO_TARGET || getState() == GUARD_SLEEP) && canBeInterrupted())
        {
            if (buildingGuards.getTask().equals(GuardTaskSetting.GUARD) && !isWithinPersecutionDistance(pos, getPersecutionDistance()))
            {
                return false;
            }

            // Stop sleeping when someone called for help
            stopSleeping();
            return true;
        }
        return false;
    }

    /**
     * Decide what we should do next! Ticked once every GUARD_TASK_INTERVAL Ticks
     *
     * @return the next IAIState.
     */
    protected IAIState decide()
    {
        final ILocation rallyLocation = buildingGuards.getRallyLocation();

        if (regularActionTimer++ > ACTION_INCREASE_INTERVAL)
        {
            incrementActionsDone();
            regularActionTimer = 0;
        }

        if (worker.getRandom().nextDouble() < 0.05)
        {
            equipInventoryArmor();
        }

        if (!hasTool())
        {
            return PREPARING;
        }

        if (fighttimer > 0)
        {
            fighttimer -= GUARD_TASK_INTERVAL;
            if (fighttimer <= 0)
            {
                onCombatLeave();
            }
        }
        else
        {
            worker.stopUsingItem();
            lastGuardActionPos = worker.blockPosition();
        }

        if (rallyLocation != null || buildingGuards.getTask().equals(GuardTaskSetting.FOLLOW))
        {
            worker.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_EFFECT_DURATION, 0, false, false));
        }
        else
        {
            worker.removeEffect(MobEffects.GLOWING);
        }

        if (rallyLocation != null && rallyLocation.isReachableFromLocation(worker.getLocation()))
        {
            return rally(rallyLocation);
        }

        return switch (buildingGuards.getTask())
                 {
                     case GuardTaskSetting.PATROL, GuardTaskSetting.PATROL_PERMANENT, GuardTaskSetting.PATROL_BORDER -> patrol();
                     case GuardTaskSetting.GUARD -> guard();
                     case GuardTaskSetting.FOLLOW -> follow();
                     case GuardTaskSetting.PATROL_MINE -> patrolMine();
                     default -> PREPARING;
                 };
    }

    /**
     * Check if a position is within the allowed persecution distance.
     *
     * @param entityPos the position to check.
     * @return true if so.
     */
    public boolean isWithinPersecutionDistance(final BlockPos entityPos, final double attackRange)
    {
        return BlockPosUtil.getDistanceSquared(getTaskReferencePoint(), entityPos) <= Math.pow(getPersecutionDistance() + attackRange, 2);
    }

    /**
     * Get the reference point from which the guard comes.
     *
     * @return the position depending ont he task.
     */
    private BlockPos getTaskReferencePoint()
    {
        switch (buildingGuards.getTask())
        {
            case GuardTaskSetting.PATROL:
            case GuardTaskSetting.PATROL_PERMANENT:
            case GuardTaskSetting.PATROL_BORDER:
            case GuardTaskSetting.PATROL_MINE:
                return lastGuardActionPos;
            case GuardTaskSetting.FOLLOW:
                return buildingGuards.getPositionToFollow();
            default:
                return buildingGuards.getGuardPos(worker);
        }
    }

    /**
     * Returns the block distance at which a guard should chase his target
     *
     * @return the block distance at which a guard should chase his target
     */
    protected int getPersecutionDistance()
    {
        if (buildingGuards.getRallyLocation() != null)
        {
            return MAX_FOLLOW_DERIVATION;
        }
        switch (buildingGuards.getTask())
        {
            case GuardTaskSetting.PATROL:
            case GuardTaskSetting.PATROL_PERMANENT:
            case GuardTaskSetting.PATROL_BORDER:
                return MAX_PATROL_DERIVATION;
            case GuardTaskSetting.PATROL_MINE:
            case GuardTaskSetting.FOLLOW:
                return MAX_FOLLOW_DERIVATION;
            default:
                return MAX_GUARD_DERIVATION + (getModuleForJob().getJobEntry() == ModJobs.knight.get() ? 20 : 0);
        }
    }

    @Override
    public boolean canBeInterrupted()
    {
        if (fighttimer > 0 || getState() == CombatAIStates.ATTACKING || worker.getLastAttacker() != null || buildingGuards.getRallyLocation() != null || buildingGuards.getTask()
                                                                                                                                                           .equals(GuardTaskSetting.FOLLOW))
        {
            return false;
        }
        return super.canBeInterrupted();
    }

    /**
     * Whether this guard may break off to eat.
     * <p>
     * Everything {@link #canBeInterrupted()} allows, plus the Follow task while the guard is not actually fighting.
     * Follow is uninterruptible on purpose so an escort keeps up with the player, but eating was the only thing that
     * could ever take a guard out of WORK, so a guard on Follow had no route to food at all.
     *
     * @return true if the guard may stop to eat.
     */
    public boolean canEat()
    {
        if (canBeInterrupted())
        {
            return true;
        }

        return buildingGuards.getTask().equals(GuardTaskSetting.FOLLOW)
                 && fighttimer <= 0
                 && getState() != CombatAIStates.ATTACKING
                 && worker.getLastAttacker() == null
                 && buildingGuards.getRallyLocation() == null;
    }

    /**
     * Set the citizen to wakeup
     *
     * @param citizen
     */
    public void setWakeCitizen(final EntityCitizen citizen)
    {
        sleepingGuard = new WeakReference<>(citizen);
        wakeTimer = 0;
        registerTarget(new AIOneTimeEventTarget(GUARD_WAKE));
    }

    @Override
    public Class<B> getExpectedBuildingClass()
    {
        return (Class<B>) AbstractBuildingGuards.class;
    }

    /**
     * Check whether the target is attackable
     *
     * @param user
     * @param entity
     * @return
     */
    public static boolean isAttackableTarget(final AbstractEntityCitizen user, final LivingEntity entity)
    {
        if (IColonyManager.getInstance().getCompatibilityManager().getAllMonsters().contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())) && !user.getCitizenData()
                                                                                                                                                          .getWorkBuilding()
                                                                                                                                                          .getModuleMatching(
                                                                                                                                                            EntityListModule.class,
                                                                                                                                                            m -> m.getId()
                                                                                                                                                                   .equals(
                                                                                                                                                                     HOSTILE_LIST))
                                                                                                                                                          .isEntityInList(
                                                                                                                                                            BuiltInRegistries.ENTITY_TYPE.getKey(
                                                                                                                                                              entity.getType())))
        {
            return true;
        }

        final IColony colony = user.getCitizenColonyHandler().getColonyOrRegister();
        if (colony == null)
        {
            return false;
        }

        // Players
        if (entity instanceof Player && (colony.getPermissions().getRank((Player) entity).isHostile() || colony.isValidAttackingPlayer((Player) entity)))
        {
            return true;
        }

        // Other colonies guard citizen attacking the colony
        if (entity instanceof EntityCitizen otherCitizen && otherCitizen.getCitizenColonyHandler().getColonyId() != colony.getID()
            && colony.isValidAttackingGuard((AbstractEntityCitizen) entity))
        {
            return true;
        }

        return false;
    }

    /**
     * Getter for the job.
     * @return the job.
     */
    @NotNull
    public J getJob()
    {
        return job;
    }
}
