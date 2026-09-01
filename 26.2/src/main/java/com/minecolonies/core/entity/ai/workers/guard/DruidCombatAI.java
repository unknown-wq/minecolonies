package com.minecolonies.core.entity.ai.workers.guard;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.guardtype.registry.ModGuardTypes;
import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.colony.jobs.guard.JobDruid;
import com.minecolonies.core.entity.ai.combat.AttackMoveAI;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.other.DruidPotionEntity;
import com.minecolonies.core.entity.pathfinding.PathfindingUtils;
import com.minecolonies.core.entity.pathfinding.PathingOptions;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobCanSee;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveAwayFromLocation;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveToLocation;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

import static com.minecolonies.api.research.util.ResearchConstants.DRUID_USE_POTIONS;
import static com.minecolonies.api.util.constant.GuardConstants.*;
import static com.minecolonies.core.entity.ai.BehaviourStateGroup.GUARD_ABORT_AND_FIGHT;
import static com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIFight.SPEED_LEVEL_BONUS;

/**
 * Druid combat AI
 */
public class DruidCombatAI extends AttackMoveAI<EntityCitizen>
{
    /**
     * List of potential positive effects.
     */
    private static final ImmutableList<Holder<MobEffect>> SUPPORT_EFFECTS = ImmutableList.of(MobEffects.STRENGTH, MobEffects.SATURATION, MobEffects.INSTANT_HEALTH, MobEffects.RESISTANCE);

    /**
     * List of potential positive effects.
     */
    private static final ImmutableList<Holder<MobEffect>> ADVERSE_EFFECTS = ImmutableList.of(MobEffects.SLOWNESS, MobEffects.WEAKNESS);

    /**
     * The xp per thrown potion
     */
    private static final double PER_POTION_XP = 0.05D;

    /**
     * The value of the speed which the guard will move.
     */
    private static final double COMBAT_SPEED = 1.0;

    /**
     * Potion velocity.
     */
    public static final float POTION_VELOCITY = 0.5f;

    /**
     * Flee chance
     */
    private static final int FLEE_CHANCE = 3;

    /**
     * The parent combat AI.
     */
    private final AbstractEntityAIGuard<JobDruid, AbstractBuildingGuards> parentAI;

    /**
     * The combat pathing options.
     */
    private final PathingOptions combatPathingOptions;

    /**
     * If the last attack was an instant effect potion.
     */
    private boolean instantEffect;

    public DruidCombatAI(
      final EntityCitizen owner,
      final ITickRateStateMachine stateMachine,
      final AbstractEntityAIGuard parentAI)
    {
        super(owner, stateMachine);

        stateMachine.addTransitionGroup(GUARD_ABORT_AND_FIGHT, new TickingTransition(this::checkForTarget, () -> CombatAIStates.ATTACKING, 5).withName("busy_checkTarget"));
        stateMachine.addTransitionGroup(GUARD_ABORT_AND_FIGHT, new TickingTransition(this::searchNearbyTarget, () -> CombatAIStates.ATTACKING, 80).withName("busy_searchTarget"));

        this.parentAI = parentAI;
        combatPathingOptions = new PathingOptions();
        combatPathingOptions.setEnterDoors(true);
        combatPathingOptions.setEnterGates(true);
        combatPathingOptions.setCanOpenDoors(true);
        combatPathingOptions.setCanSwim(true);
        combatPathingOptions.withOnPathCost(0.8);
        combatPathingOptions.withJumpCost(0.01);
        combatPathingOptions.withDropCost(1.5);
    }

    @Override
    protected void doAttack(final LivingEntity target)
    {
        if (user.distanceToSqr(target) < getKiteDistanceSq())
        {
            if (user.getRandom().nextInt(FLEE_CHANCE) == 0 &&
                  !((AbstractBuildingGuards) user.getCitizenData().getWorkBuilding()).getTask().equals(GuardTaskSetting.GUARD))
            {
                EntityNavigationUtils.walkAwayFrom(user, target.blockPosition(), (int) (getAttackDistance() / 2.0), getCombatMovementSpeed());
            }
        }
        else
        {
            user.getNavigation().stop();
        }

        user.swing(InteractionHand.MAIN_HAND);

        final int level = user.getCitizenData().getCitizenSkillHandler().getLevel(ModGuardTypes.druid.get().getSecondarySkill());
        final int time = user.getCitizenData().getCitizenSkillHandler().getLevel(ModGuardTypes.druid.get().getPrimarySkill()) * 20;

        // A fresh druid used to throw at inaccuracy 99. Projectile#shoot adds random.triangle(0, 0.0172275 * this)
        // to each component of a normalised direction vector, so 99 is a spread of about +-1.7 against a unit
        // vector: the potion goes in an essentially random direction, which reads in play as "the druid does
        // nothing" for his first several days. This is the archer's own curve (HIT_CHANCE_DIVIDER over the skill),
        // clamped at both ends so a level-one druid is merely bad rather than blind and a level-99 one still misses
        // occasionally.
        final float inaccuracy = Mth.clamp(HIT_CHANCE_DIVIDER / (level + 1), 0.5f, 6f);
        final Holder<MobEffect> effect;
        final ItemStack stack = Items.SPLASH_POTION.getDefaultInstance();
        boolean gotMaterial = false;
        BiPredicate<LivingEntity, MobEffect> predicate;
        if (user.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(DRUID_USE_POTIONS) > 0
              && InventoryUtils.hasItemInItemHandler(user.getInventoryCitizen(), item -> item.getItem() == ModItems.magicpotion))
        {
            gotMaterial = true;
        }
        if (AbstractEntityAIGuard.isAttackableTarget(user, target))
        {
            effect = ADVERSE_EFFECTS.get(user.getRandom().nextInt(gotMaterial ? 2 : 1));
            predicate = (entity, eff) -> AbstractEntityAIGuard.isAttackableTarget(user, entity);
        }
        else
        {
            effect = SUPPORT_EFFECTS.get(user.getRandom().nextInt(gotMaterial ? 4 : 1));
            predicate = (entity, eff) -> !AbstractEntityAIGuard.isAttackableTarget(user, entity);
        }

        stack.set(DataComponents.POTION_CONTENTS, PotionContents.EMPTY.withEffectAdded((new MobEffectInstance(effect, time, gotMaterial ? 2 : 0))));
        DruidPotionEntity.throwPotionAt(stack, target, user, user.level(), POTION_VELOCITY, inaccuracy, predicate);

        if (gotMaterial)
        {
            InventoryUtils.removeStackFromItemHandler(user.getCitizenData().getInventory(), new ItemStack(ModItems.magicpotion, 1), 1);
        }

        this.instantEffect = effect.value().isInstantaneous();

        user.setItemInHand(InteractionHand.MAIN_HAND, stack);

        user.getThreatTable().removeCurrentTarget();

        user.getCitizenExperienceHandler().addExperience(PER_POTION_XP);
    }

    @Override
    protected int getAttackDelay()
    {
        return this.instantEffect ? super.getAttackDelay() * 2 : super.getAttackDelay();
    }

    @Override
    protected double getAttackDistance()
    {
        int attackDist = BASE_DISTANCE_FOR_POTION_ATTACK;
        // + 1 Blockrange per building level for a total of +5 from building level
        if (user.getCitizenData().getWorkBuilding() != null)
        {
            attackDist += user.getCitizenData().getWorkBuilding().getBuildingLevelEquivalent();
        }

        if (target != null)
        {
            attackDist += user.getY() - target.getY();
        }

        return attackDist;
    }

    @Override
    protected PathResult moveInAttackPosition(final LivingEntity target)
    {
        if (BlockPosUtil.getDistanceSquared(target.blockPosition(), user.blockPosition()) <= getKiteDistanceSq())
        {
            final PathJobMoveAwayFromLocation job = new PathJobMoveAwayFromLocation(user.level(),
              PathfindingUtils.prepareStart(target),
              target.blockPosition(),
              (int) (getAttackDistance() / 2.0),
              (int) user.getAttribute(Attributes.FOLLOW_RANGE).getValue(),
              user);
            final PathResult pathResult = ((MinecoloniesAdvancedPathNavigate) user.getNavigation()).setPathJob(job, null, getCombatMovementSpeed(), true);
            job.setPathingOptions(combatPathingOptions);
            return pathResult;
        }
        else if (BlockPosUtil.getDistance2D(target.blockPosition(), user.blockPosition()) >= 20)
        {
            final PathJobMoveToLocation job = new PathJobMoveToLocation(user.level(), PathfindingUtils.prepareStart(user), target.blockPosition(), 200, user);
            final PathResult pathResult = ((MinecoloniesAdvancedPathNavigate) user.getNavigation()).setPathJob(job, null, getCombatMovementSpeed(), true);
            job.setPathingOptions(combatPathingOptions);
            return pathResult;
        }
        final PathJobCanSee job = new PathJobCanSee(user, target, user.level(), ((AbstractBuildingGuards) user.getCitizenData().getWorkBuilding()).getGuardPos(user), 40);
        final PathResult pathResult = ((MinecoloniesAdvancedPathNavigate) user.getNavigation()).setPathJob(job, null, getCombatMovementSpeed(), true);
        job.setPathingOptions(combatPathingOptions);
        return pathResult;
    }

    /**
     * Get combat speed
     *
     * @return movent speed
     */
    protected double getCombatMovementSpeed()
    {
        double levelAdjustment = user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Mana) * SPEED_LEVEL_BONUS;
        levelAdjustment += (user.getCitizenData().getWorkBuilding().getBuildingLevelEquivalent() - 1) * SPEED_LEVEL_BONUS;

        levelAdjustment = Math.min(levelAdjustment, 0.3);
        return COMBAT_SPEED + levelAdjustment;
    }

    @Override
    protected boolean isAttackableTarget(final LivingEntity entity)
    {
        return (AbstractEntityAIGuard.isAttackableTarget(user, entity)
                  || (entity instanceof IThreatTableEntity && ((IThreatTableEntity) entity).getThreatTable().getTarget() != null)
                  || (entity instanceof Player && entity.getLastHurtByMobTimestamp() != 0 && entity.tickCount > entity.getLastHurtByMobTimestamp()
                        && entity.tickCount - entity.getLastHurtByMobTimestamp() < 20 * 30))
                 && !wasAffectedByDruid(entity);
    }

    @Override
    protected boolean searchNearbyTarget()
    {
        if (checkForTarget())
        {
            return true;
        }

        final List<LivingEntity> entities = user.level().getEntitiesOfClass(LivingEntity.class, getSearchArea());

        if (entities.isEmpty())
        {
            return false;
        }

        int targetsUnderEffect = 0;
        boolean foundTarget = false;
        // See TargetAI#searchNearbyTarget: aborting the scan on the first sleeping guard hid every real enemy
        // found later in the same list. Continue instead, waking at most one sleeper per scan.
        boolean skipped = false;
        for (final LivingEntity entity : entities)
        {
            if (!entity.isAlive())
            {
                continue;
            }

            if (!skipped && skipSearch(entity))
            {
                skipped = true;
                continue;
            }

            if (isEntityValidTarget(entity))
            {
                if (user.hasLineOfSight(entity))
                {
                    user.getThreatTable().addThreat(entity, 0);
                    foundTarget = true;
                }
            }
            else if (wasAffectedByDruid(entity))
            {
                targetsUnderEffect++;
            }
        }

        return foundTarget && targetsUnderEffect <= parentAI.building.getBuildingLevelEquivalent() * 2;
    }

    /**
     * Check if an entity has one of the potion effects the druid hands out.
     *
     * @param entity the entity to check for.
     * @return true if so.
     */
    private boolean wasAffectedByDruid(final LivingEntity entity)
    {
        return entity.hasEffect(MobEffects.SLOWNESS) || entity.hasEffect(MobEffects.SATURATION) || entity.hasEffect(MobEffects.STRENGTH)
                 || entity.hasEffect(MobEffects.WEAKNESS) || entity.hasEffect(MobEffects.RESISTANCE) || entity.hasEffect(MobEffects.INSTANT_HEALTH);
    }

    @Override
    protected boolean isWithinPersecutionDistance(final LivingEntity target)
    {
        return parentAI.isWithinPersecutionDistance(target.blockPosition(), getAttackDistance());
    }

    @Override
    protected boolean skipSearch(final LivingEntity entity)
    {
        // Found a sleeping guard nearby
        if (entity instanceof EntityCitizen && user.getRandom().nextInt(10) < 1)
        {
            final EntityCitizen citizen = (EntityCitizen) entity;
            if (citizen.getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard && ((AbstractJobGuard<?>) citizen.getCitizenJobHandler().getColonyJob()).isAsleep()
                  && user.getSensing().hasLineOfSight(citizen))
            {
                parentAI.setWakeCitizen(citizen);
                return true;
            }
        }

        return false;
    }

    @Override
    protected int getSearchRange()
    {
        return Math.max(AbstractEntityAIGuard.getGuardVisionRange(user), (int) Math.ceil(getAttackDistance()));
    }

    @Override
    protected int getYSearchRange()
    {
        // Calls super so the guardverticalvision config reaches druids at all. The literals here were copied from
        // RangeCombatAI before that config existed and never updated with it, so a druid's vertical box was frozen
        // at the old numbers however the server was configured.
        if (((AbstractBuildingGuards) user.getCitizenData().getWorkBuilding()).getTask().equals(GuardTaskSetting.GUARD))
        {
            return Math.max(super.getYSearchRange(), Y_VISION + 25);
        }

        return super.getYSearchRange();
    }

    @Override
    protected void onTargetDied(final LivingEntity entity)
    {
        parentAI.incrementActionsDone();
        user.getCitizenExperienceHandler().addExperience(EXP_PER_MOB_DEATH);
        user.decreaseSaturationForContinuousAction();
    }
}
