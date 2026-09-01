package com.minecolonies.core.entity.ai.workers.guard;

import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.util.*;
import com.minecolonies.api.util.constant.ColonyConstants;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.colony.jobs.guard.JobRanger;
import com.minecolonies.core.entity.ai.combat.AttackMoveAI;
import com.minecolonies.core.entity.ai.combat.CombatUtils;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.other.CustomArrowEntity;
import com.minecolonies.core.entity.pathfinding.PathfindingUtils;
import com.minecolonies.core.entity.pathfinding.PathingOptions;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.navigation.MinecoloniesAdvancedPathNavigate;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobCanSee;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveAwayFromLocation;
import com.minecolonies.core.entity.pathfinding.pathjobs.PathJobMoveToLocation;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.minecolonies.core.util.citizenutils.CitizenItemUtils;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import static com.minecolonies.api.research.util.ResearchConstants.*;
import static com.minecolonies.api.util.constant.GuardConstants.*;
import static com.minecolonies.api.util.constant.StatisticsConstants.MOBS_KILLED;
import static com.minecolonies.api.util.constant.StatisticsConstants.MOB_KILLED;
import static com.minecolonies.core.colony.buildings.modules.BuildingModules.STATS_MODULE;
import static com.minecolonies.core.entity.ai.BehaviourStateGroup.GUARD_ABORT_AND_FIGHT;
import static com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIFight.SPEED_LEVEL_BONUS;
import static com.minecolonies.core.entity.ai.workers.guard.AbstractEntityAIGuard.PATROL_DEVIATION_RAID_POINT;
import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/**
 * Knight combat AI
 */
public class RangeCombatAI extends AttackMoveAI<EntityCitizen>
{
    /**
     * Visible combat icon
     */
    private final static VisibleCitizenStatus ARCHER_COMBAT =
      new VisibleCitizenStatus(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/icons/work/archer_combat.png"), "com.minecolonies.gui.visiblestatus.archer_combat");

    private final AbstractEntityAIGuard parentAI;

    /**
     * The value of the speed which the guard will move.
     */
    private static final double COMBAT_SPEED = 1.0;

    /**
     * Extra damage for arrow usage
     */
    private static final double ARROW_EXTRA_DAMAGE = 2.0f;

    /**
     * How many ticks we activate the bow before shooting
     */
    private static final int BOW_HOLDING_DELAY = 40;

    /**
     * Bonus range for shooting while guarding
     */
    private static final int GUARD_BONUS_RANGE = 10;

    /**
     * Flee chance
     */
    private static final int FLEE_CHANCE = 3;

    private final PathingOptions combatPathingOptions;

    public RangeCombatAI(
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
    public boolean canAttack()
    {
        final int weaponSlot =
          InventoryUtils.getFirstSlotOfItemHandlerContainingEquipment(user.getInventoryCitizen(), getWeaponType(), 0, user.getCitizenData().getWorkBuilding().getMaxEquipmentLevel());

        if (weaponSlot != -1)
        {
            CitizenItemUtils.setHeldItem(user, InteractionHand.MAIN_HAND, weaponSlot);
            if (nextAttackTime - BOW_HOLDING_DELAY >= user.level().getGameTime() && !user.isUsingItem())
            {
                user.startUsingItem(InteractionHand.MAIN_HAND);
            }
            return true;
        }

        return false;
    }

    /**
     * Gets the weapon type that the AI will look for when checking if it can attack.
     *
     * @return the weapon type.
     */
    public EquipmentTypeEntry getWeaponType()
    {
        if (parentAI.getJob() instanceof JobRanger jobRanger)
        {
            return jobRanger.getEquipmentType();
        }

        // Default to bow.
        return ModEquipmentTypes.bow.get();
    }

    @Override
    protected boolean checkForTarget()
    {
        final boolean validTarget = super.checkForTarget();

        if (!validTarget && user.isUsingItem())
        {
            user.stopUsingItem();
        }

        return validTarget;
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

        user.getCitizenData().setVisibleStatus(ARCHER_COMBAT);
        user.swing(InteractionHand.MAIN_HAND);
        user.stopUsingItem();

        int amountOfArrows = 1;
        if (user.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(DOUBLE_ARROWS) > 0)
        {
            if (user.getRandom().nextDouble() < user.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(DOUBLE_ARROWS))
            {
                amountOfArrows++;
            }
        }

        for (int i = 0; i < amountOfArrows; i++)
        {
            final AbstractArrow arrow = CombatUtils.createArrowForShooter(user);

            if (user.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(ARROW_PIERCE) > 0)
            {
                arrow.setPierceLevel((byte) 2);
            }

            // Add bow enchant effects: Knocback and fire
            final ItemStack bow = user.getItemInHand(InteractionHand.MAIN_HAND);

            if (EnchantmentHelper.getItemEnchantmentLevel(Utils.getRegistryValue(Enchantments.FLAME, user.level()), bow) > 0)
            {
                arrow.setRemainingFireTicks(5 * 20);
            }

            double damage = calculateDamage(arrow);
            // PORT-NOTE(26.2): AbstractArrow#shotFromCrossbow() is gone. The 1.21.1 call was a
            // no-op getter used as a statement, so nothing is lost by dropping it. Upstream plainly
            // meant setShotFromCrossbow(true) here and never wrote it — which is why CombatUtils#shootArrow
            // resolves the same flag to a constant false rather than re-deriving it from the weapon.
            // See the note there before "fixing" either of these.
            arrow.setBaseDamage(damage);

            final float chance = HIT_CHANCE_DIVIDER / (user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) + 1);
            CombatUtils.shootArrow(arrow, target, chance);
            user.playSound(SoundEvents.SKELETON_SHOOT, (float) BASIC_VOLUME, (float) SoundUtils.getRandomPitch(user.getRandom()));
        }

        target.setLastHurtByMob(user);
        CitizenItemUtils.damageItemInHand(user, InteractionHand.MAIN_HAND, 1);
        user.stopUsingItem();
    }

    @Override
    protected double getAttackDistance()
    {
        int attackDist = BASE_DISTANCE_FOR_RANGED_ATTACK;
        // + 1 Blockrange per building level for a total of +5 from building level
        if (user.getCitizenData().getWorkBuilding() != null)
        {
            attackDist += user.getCitizenData().getWorkBuilding().getBuildingLevelEquivalent();
        }
        // ~ +1 each three levels for a total of +10 from guard level
        if (user.getCitizenData() != null)
        {
            attackDist += (user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) / 50.0f) * 15;
        }

        if (target != null)
        {
            attackDist += user.getY() - target.getY();
        }

        // Null-safe: getSearchRange now calls this on every target scan, not only once a target exists, so the
        // unguarded cast that used to sit here would have been reachable for a guard between buildings.
        if (user.getCitizenData().getWorkBuilding() instanceof AbstractBuildingGuards guardBuilding
              && guardBuilding.getTask().equals(GuardTaskSetting.GUARD))
        {
            attackDist += GUARD_BONUS_RANGE;
        }

        // The clamp belongs last. It used to be applied before the height difference and the guard-post bonus, so an
        // archer twenty blocks above his target on Guard had an effective attack distance of 54 and the constant's
        // own comment ("24 max arrow dist") was simply untrue. It did not show up in play only because his target
        // search box could not reach that far either; now that getSearchRange follows this number, it would.
        return Math.min(attackDist, MAX_DISTANCE_FOR_RANGED_ATTACK);
    }

    @Override
    protected int getSearchRange()
    {
        // An archer's eyes have to keep up with his bow: the inherited 16 was less than half the distance
        // getAttackDistance asks him to open fire at, so for most of the scan cycle a raid could walk to within
        // sixteen blocks of a tower before anyone on it noticed.
        return Math.max(AbstractEntityAIGuard.getGuardVisionRange(user), (int) Math.ceil(getAttackDistance()));
    }

    @Override
    protected int getAttackDelay()
    {
        return (int) (RANGED_ATTACK_DELAY_BASE * (isMarksman() ? 0.67 : 1));
    }

    /**
     * Calculates the ranged attack damage
     *
     * @param arrow
     * @return the attack damage
     */
    private double calculateDamage(final AbstractArrow arrow)
    {
        double damage = user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Agility) / 5d;

        final ItemStack heldItem = user.getItemInHand(InteractionHand.MAIN_HAND);
        damage += EnchantmentHelper.modifyDamage((ServerLevel) user.level(), heldItem, target, user.level().damageSources().mobAttack(user), 1) / 2.5;
        damage += EnchantmentHelper.getItemEnchantmentLevel(Utils.getRegistryValue(Enchantments.POWER, user.level()), heldItem);
        damage += user.getCitizenColonyHandler().getColony().getResearchManager().getResearchEffects().getEffectStrength(ARCHER_DAMAGE);

        boolean consumesArrow = false;
        if (user.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(ARCHER_USE_ARROWS) > 0)
        {
            final int slot = InventoryUtils.findFirstSlotInItemHandlerWith(user.getInventoryCitizen(), item -> item.getItem() instanceof ArrowItem);
            if (slot != -1 && !ItemStackUtils.isEmpty(user.getInventoryCitizen().extractItem(slot, 1, true)))
            {
                damage += ARROW_EXTRA_DAMAGE;
                consumesArrow = true;
            }
        }

        if (user.getHealth() <= user.getMaxHealth() * 0.2D)
        {
            damage *= 2;
        }

        if (ColonyConstants.rand.nextDouble() > 1 / (1 + user.getCitizenColonyHandler().getColonyOrRegister().getResearchManager().getResearchEffects().getEffectStrength(GUARD_CRIT)))
        {
            damage *= 1.5;
        }

        // A marksman splits his shot: `share` of it ignores armour, the rest is an ordinary arrow. The arrow below is
        // launched carrying only the `1 - share` fraction, and the on-hit callback pays back the difference.
        final double trueDamageShare = isMarksman() ? Math.min(marksManTrueDamageShare(), MARKSMAN_MAX_TRUE_DAMAGE_SHARE) : 0.0;

        if (arrow instanceof CustomArrowEntity customArrowEntity && (consumesArrow || trueDamageShare > 0))
        {
            final boolean consumeArrow = consumesArrow;
            customArrowEntity.setOnHitCallback(entityRayTraceResult ->
            {
                if (consumeArrow)
                {
                    final int arrowSlot = InventoryUtils.findFirstSlotInItemHandlerWith(user.getInventoryCitizen(), item -> item.getItem() instanceof ArrowItem);
                    if (arrowSlot != -1)
                    {
                        user.getInventoryCitizen().extractItem(arrowSlot, 1, false);
                    }
                }

                if (trueDamageShare > 0)
                {
                    // Reconstruct the undivided shot from the reduced figure the arrow is carrying, and hand the
                    // whole of it to the armour-bypassing source. Vanilla's damage cooldown (LivingEntity#hurtServer)
                    // has just been set by the arrow's own hit, so this call applies exactly the remainder --
                    // `share` of the shot, through armour and shields. It used to read
                    // `getBaseDamage() * share * 10`, a product that peaks at share 0.5 and decays to nothing as the
                    // marksman trains: a level-99 marksman dealt about a fiftieth of what a level-1 one did, which
                    // is why the research-locked late-game unit was worse than a fresh ranger.
                    //
                    // The damage source comes from the entity that was hit rather than from the guard's target: an
                    // arrow is still in the air long after the guard has dropped that target, and reading
                    // target.level() then threw a NullPointerException out of the arrow's tick, which crashes the
                    // server. It is the same level either way, since something has just been shot in it.
                    entityRayTraceResult.getEntity()
                      .hurt(entityRayTraceResult.getEntity().level().damageSources().source(DamageSourceKeys.PIERCE, user),
                        (float) (customArrowEntity.getBaseDamage() / (1.0 - trueDamageShare)));
                }

                return true;
            });
        }

        return (RANGER_BASE_DMG + damage) * MineColonies.getConfig().getServer().guardDamageMultiplier.get() * (1.0 - trueDamageShare);
    }

    /**
     * Check if is a marksman instance.
     * @return true if so.
     */
    public boolean isMarksman()
    {
        return parentAI.getJob().getJobRegistryEntry() == ModJobs.marksman.get();
    }

    public double marksManTrueDamageShare()
    {
        return (50 + user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Adaptability) / 2.0) / 100.0;
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
        double levelAdjustment = user.getCitizenData().getCitizenSkillHandler().getLevel(Skill.Agility) * SPEED_LEVEL_BONUS;
        levelAdjustment += (user.getCitizenData().getWorkBuilding().getBuildingLevelEquivalent() * 2 - 1) * SPEED_LEVEL_BONUS;

        levelAdjustment = Math.min(levelAdjustment, 0.3);
        return COMBAT_SPEED + levelAdjustment;
    }

    @Override
    protected boolean isAttackableTarget(final LivingEntity entity)
    {
        return AbstractEntityAIGuard.isAttackableTarget(user, entity);
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
        if (entity instanceof EntityCitizen)
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
    protected void onTargetChange(final LivingEntity newTarget)
    {
        super.onTargetChange(newTarget);
        CombatUtils.notifyGuardsOfTarget(user, newTarget, PATROL_DEVIATION_RAID_POINT);
    }

    @Override
    protected int getYSearchRange()
    {
        if (((AbstractBuildingGuards) user.getCitizenData().getWorkBuilding()).getTask().equals(GuardTaskSetting.GUARD))
        {
            // The archer's guarding bonus stays what it was; taking the max means raising the shared config
            // (which exists for the knight, who has no bonus at all) can never shrink the archer's box.
            return Math.max(super.getYSearchRange(), Y_VISION + 25);
        }

        return super.getYSearchRange();
    }

    @Override
    protected void onTargetDied(final LivingEntity entity)
    {
        parentAI.incrementActionsDone();
        user.getCitizenExperienceHandler().addExperience(EXP_PER_MOB_DEATH);
        user.getCitizenColonyHandler().getColonyOrRegister().getStatisticsManager().increment(MOBS_KILLED, user.getCitizenColonyHandler().getColonyOrRegister().getDay());
        if (entity.getType().getDescription().getContents() instanceof TranslatableContents translatableContents)
        {
            parentAI.building.getModule(STATS_MODULE).increment(MOB_KILLED + ";" + translatableContents.getKey());
        }
        user.decreaseSaturationForContinuousAction();
    }
}
