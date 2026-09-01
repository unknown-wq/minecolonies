package com.minecolonies.core.entity.ai.workers.guard;

import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.jobs.guard.JobCavalry;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.VisibleCitizenStatus;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_CHARGE_DAMAGE_BONUS;
import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_CHARGE_MAX_FAILED_PASSES;
import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_CHARGE_RUN_OUT_DISTANCE;
import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_CHARGE_RUN_OUT_TIMEOUT;
import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_CHARGE_SPEED;
import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_CHARGE_SUSPEND_TICKS;
import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_CHARGE_TOP_SPEED;
import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_CHARGE_TURN_DISTANCE;
import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_DAMAGE_MULTIPLIER;
import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_RANGE_MULTIPLIER;

/**
 * Mounted guard combat AI.
 * <p>
 * A cavalryman does not stand and trade blows. While a hostile target is alive he is always moving: he gallops onto
 * the target, strikes in passing, rides on through without slowing, turns wide and comes round for another pass. The
 * cycle is two legs and no waiting state -- see {@link #moveInAttackPosition(LivingEntity)} for the closing leg and
 * {@link #keepCharging()} for the turn -- and the blow lands while the horse is at speed, which is the whole point:
 * {@link #getChargeMultiplier()} reads the mount's actual speed at the moment of the hit, so a strike delivered from
 * a standstill gets nothing.
 * <p>
 * Every part of this is confined to cavalry. The one thing it needs from further up is
 * {@code AttackMoveAI#holdsGroundInAttackRange}, which defaults to the previous behaviour for everything else.
 */
public class CavalryCombatAI extends MeleeCombatAI
{
    /**
     * Combat icon
     */
    private final static VisibleCitizenStatus CAVALRY_COMBAT_ICON =
      new VisibleCitizenStatus(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/icons/work/cavalry_combat.png"), "com.minecolonies.gui.visiblestatus.cavalry_combat");

    /**
     * Game time at which the current run-out leg gives up, or 0 when the rider is closing rather than running out.
     */
    private long runOutUntil = 0;

    /**
     * Where the current run-out leg is headed, or null when the pathfinder is picking its own way out.
     */
    private BlockPos runOutPos = null;

    /**
     * Where the rider was when the current run-out leg started, used to tell being boxed in from being chased.
     */
    private BlockPos runOutFrom = null;

    /**
     * Consecutive run-outs that timed out having covered no ground at all.
     */
    private int failedRunOuts = 0;

    /**
     * Game time until which charging is given up as impossible and the unit fights on the spot.
     */
    private long chargeSuspendedUntil = 0;

    @SuppressWarnings({"rawtypes", "unchecked"})
    public CavalryCombatAI(final EntityCitizen owner, final ITickRateStateMachine<?> stateMachine, final AbstractEntityAIGuard<?, ?> parentAI)
    {
        super(owner, stateMachine, parentAI);

        ((ITickRateStateMachine) stateMachine).addTransition(new TickingTransition<>(CombatAIStates.ATTACKING, () -> true, this::keepCharging, 5));
    }

    /**
     * Whether this unit is in a position to charge at all.
     * <p>
     * The mount is checked by type rather than by {@code isPassenger()} because a citizen is also a passenger while
     * he is asleep or sitting -- {@code EntityAICavalry#sleep} puts him on a {@code SittingEntity} -- and a seat
     * cannot be ridden anywhere. A dismounted cavalryman, or one whose horse has been killed under him, answers false
     * here and falls back to ordinary stationary melee for as long as that lasts.
     *
     * @return true if the charge cycle applies.
     */
    protected boolean isCharging()
    {
        return user.getVehicle() instanceof CavalryHorseEntity horse
                 && horse.isAlive()
                 && user.level().getGameTime() >= chargeSuspendedUntil;
    }

    /**
     * Whether the rider is on the outbound leg of a pass.
     *
     * @return true if running out.
     */
    private boolean isRunningOut()
    {
        return runOutUntil > 0;
    }

    /**
     * A cavalryman never plants himself, so the movement layer must not stop pathing when the target comes into
     * reach. This is the single behaviour change above this class, and it is gated on the charge being live: a
     * dismounted or boxed-in cavalryman answers true and fights exactly like a knight.
     *
     * @return false while the charge cycle is running.
     */
    @Override
    protected boolean holdsGroundInAttackRange()
    {
        return !isCharging();
    }

    /**
     * Ride at the gallop while charging.
     * <p>
     * This is the only lever the charge needs on the horse: the citizen's navigation hands the factor to the
     * vehicle's {@code MoveControl} ({@code Mob#getMoveControl} redirects a passenger's move control to its mount),
     * which writes {@code speedFactor * MOVEMENT_SPEED} into the horse's speed. No attribute is modified, so there is
     * nothing to restore and nothing that can be persisted into a save by a crash or a chunk unload mid-charge.
     *
     * @return the navigation speed factor.
     */
    @Override
    protected double getCombatMovementSpeed()
    {
        return isCharging() ? CAVALRY_CHARGE_SPEED : super.getCombatMovementSpeed();
    }

    /**
     * Calculates the damage to deal, scaled by how fast the mount was actually travelling when the blow landed.
     *
     * @return attack damage
     */
    @Override
    protected double getAttackDamage()
    {
        // TODO: Allow this to improve through research
        return super.getAttackDamage() * CAVALRY_DAMAGE_MULTIPLIER * getChargeMultiplier();
    }

    /**
     * How much harder this blow lands for the speed the mount is carrying.
     * <p>
     * The speed comes from vanilla's own accounting rather than from anything this mod tracks:
     * {@code Entity#computeSpeed} records the displacement of the last tick from {@code Entity#baseTick} and
     * {@code Entity#getKnownSpeed} hands it back, so twenty times its horizontal length is blocks per second. It is
     * read off the root vehicle, which is what carries the rider's momentum; a citizen on foot has no vehicle and
     * gets nothing. The vertical component is dropped so that a horse dropping off a ledge does not read as a charge.
     * <p>
     * Multiplicative, and clamped at the documented top speed. Worked through for a cavalryman with the mod's own
     * spear, before research, crit and the config multiplier, taking {@code MeleeCombatAI#getAttackDamage} exactly as
     * written -- its {@code EnchantmentHelper.modifyDamage} term is seeded with the running total and added back to
     * it, so the weapon damage of every melee guard is doubled there:
     * <pre>
     *   at rest          0.0 blocks/s   x1.00   12 damage
     *   at half speed    3.95 blocks/s  x1.50   18 damage
     *   at top speed     7.9 blocks/s   x2.00   24 damage
     * </pre>
     * For scale, on the same reading a foot knight with a netherite sword deals 20 per swing, and a cavalryman with a
     * vanilla iron spear runs 10 / 15 / 20 and with a netherite one 14 / 21 / 28. A pass costs roughly 30-40 ticks
     * against an attack cooldown of 16-32, so the charge is worth more per hit than standing still is per hit and
     * lands them slightly less often -- and it is nowhere near a one-shot of anything a knight would not also
     * one-shot.
     *
     * @return the multiplier to apply to the blow, 1.0 at a standstill.
     */
    protected double getChargeMultiplier()
    {
        if (!user.isPassenger())
        {
            return 1.0;
        }

        final double blocksPerSecond = user.getRootVehicle().getKnownSpeed().horizontalDistance() * 20.0;

        return 1.0 + CAVALRY_CHARGE_DAMAGE_BONUS * Math.min(1.0, blocksPerSecond / CAVALRY_CHARGE_TOP_SPEED);
    }

    /**
     * Strike, then ride on.
     * <p>
     * The rider does not stop to see the result: the outbound leg is started here, in the same tick as the blow, so
     * that the horse is already committed to riding through before the movement layer next runs.
     *
     * @param target the target struck.
     */
    @Override
    protected void doAttack(final LivingEntity target)
    {
        super.doAttack(target);

        if (isCharging())
        {
            beginRunOut(target);
        }
    }

    /**
     * The two legs of the charge cycle.
     * <p>
     * Closing rides at the target's own block rather than stopping short of it at {@link #getAttackDistance()}, which
     * is what keeps the horse accelerating right through the moment the blow lands. Reaching striking distance ends
     * the leg whether or not the blow was ready -- a pass that arrives early simply carries on through and comes
     * round again rather than pulling up to wait for the cooldown.
     *
     * @param target target to move towards
     * @return path result
     */
    @Override
    protected PathResult moveInAttackPosition(final LivingEntity target)
    {
        if (!isCharging())
        {
            endRunOut();
            return super.moveInAttackPosition(target);
        }

        if (isRunningOut())
        {
            if (user.level().getGameTime() < runOutUntil && user.distanceTo(target) < CAVALRY_CHARGE_TURN_DISTANCE)
            {
                return rideOut(target);
            }

            endRunOut();
        }

        if (user.distanceTo(target) <= getAttackDistance())
        {
            return beginRunOut(target);
        }

        EntityNavigationUtils.walkToPos(user, target.blockPosition(), 0, false, getCombatMovementSpeed());
        return user.getNavigation().getPathResult();
    }

    /**
     * Keeps the horse moving between the movement layer's ticks.
     * <p>
     * {@code AttackMoveAI#move} only re-paths when the navigation has run dry, which for a charge is exactly the
     * wrong moment: by then the horse has coasted to a halt at the far end of its run-out. This runs at the same
     * cadence as the attack check and ends each leg on the condition that matters -- distance from the target -- so
     * the turn is taken with a live path still under the horse and the return leg is ordered while it is still
     * moving. It issues no path of its own; it only decides when a leg is over.
     *
     * @return null, this never changes state.
     */
    private IState keepCharging()
    {
        if (target == null || !target.isAlive())
        {
            return null;
        }

        if (!isCharging())
        {
            endRunOut();
            return null;
        }

        if (!isRunningOut())
        {
            // Arrived. Ride on through, ready or not.
            if (user.distanceTo(target) <= getAttackDistance())
            {
                beginRunOut(target);
            }

            return null;
        }

        final boolean timedOut = user.level().getGameTime() >= runOutUntil;
        if (!timedOut && user.distanceTo(target) < CAVALRY_CHARGE_TURN_DISTANCE)
        {
            return null;
        }

        // A leg that runs out of time having covered no ground is a horse that is not getting away at all: walled in,
        // on a ledge, or wedged on a building corner. A few of those in a row and the charge is put aside for a while
        // and the unit fights on the spot, rather than spending the whole engagement failing to path. A leg that
        // covered ground but was followed the whole way is not that -- it is being chased, and it turns back in and
        // fights on.
        if (timedOut && runOutFrom != null && BlockPosUtil.dist(user.blockPosition(), runOutFrom) < CAVALRY_CHARGE_TURN_DISTANCE)
        {
            if (++failedRunOuts >= CAVALRY_CHARGE_MAX_FAILED_PASSES)
            {
                failedRunOuts = 0;
                chargeSuspendedUntil = user.level().getGameTime() + CAVALRY_CHARGE_SUSPEND_TICKS;
            }
        }
        else
        {
            failedRunOuts = 0;
        }

        endRunOut();
        moveInAttackPosition(target);

        return null;
    }

    /**
     * Starts the outbound leg of a pass.
     *
     * @param target the target being ridden past.
     * @return path result
     */
    private PathResult beginRunOut(final LivingEntity target)
    {
        runOutUntil = user.level().getGameTime() + CAVALRY_CHARGE_RUN_OUT_TIMEOUT;
        runOutFrom = user.blockPosition();
        runOutPos = pickRunOutPos(target);

        return rideOut(target);
    }

    /**
     * Ends the outbound leg, whether it succeeded or not.
     */
    private void endRunOut()
    {
        runOutUntil = 0;
        runOutPos = null;
        runOutFrom = null;
    }

    /**
     * Paths the outbound leg.
     * <p>
     * The chosen point is on the far side of the target, and it may well be unreachable -- a wall, a drop, a building.
     * When the pathfinder says so, the leg falls back to walking away from the target, which is the same job the
     * archer already uses to give ground and will find whatever way out actually exists.
     *
     * @param target the target being ridden past.
     * @return path result
     */
    private PathResult rideOut(final LivingEntity target)
    {
        if (runOutPos != null)
        {
            if (!EntityNavigationUtils.walkToPos(user, runOutPos, 1, false, getCombatMovementSpeed()))
            {
                final PathResult result = user.getNavigation().getPathResult();
                if (result == null || !result.failedToReachDestination())
                {
                    return result;
                }
            }

            runOutPos = null;
        }

        EntityNavigationUtils.walkAwayFrom(user, target.blockPosition(), CAVALRY_CHARGE_TURN_DISTANCE, getCombatMovementSpeed());
        return user.getNavigation().getPathResult();
    }

    /**
     * Picks the point a pass carries on to.
     * <p>
     * It is placed beyond the target along the line the horse is already travelling, so the pass rides through rather
     * than bouncing off. Where the horse has no motion to read -- the first pass out of a standstill -- the line from
     * rider to target is used instead, which puts the point directly beyond him.
     *
     * @param target the target being ridden past.
     * @return the point to ride on to, or null if no direction could be derived.
     */
    private BlockPos pickRunOutPos(final LivingEntity target)
    {
        Vec3 heading = user.getRootVehicle().getKnownSpeed();
        if (heading.horizontalDistanceSqr() < 1.0E-6D)
        {
            heading = target.position().subtract(user.position());
        }

        heading = new Vec3(heading.x, 0.0D, heading.z);
        if (heading.lengthSqr() < 1.0E-6D)
        {
            return null;
        }

        return BlockPos.containing(target.position().add(heading.normalize().scale(CAVALRY_CHARGE_RUN_OUT_DISTANCE)));
    }

    @Override
    public void resetTarget()
    {
        super.resetTarget();
        endRunOut();
        failedRunOuts = 0;
    }

    @Override
    protected void onTargetChange(final LivingEntity newTarget)
    {
        super.onTargetChange(newTarget);
        endRunOut();
        failedRunOuts = 0;
    }

    /**
     * Gets the weapon type that the AI will look for when checking if it can attack.
     *
     * @return the weapon type.
     */
    @Override
    public EquipmentTypeEntry getWeaponType()
    {
        return JobCavalry.getWeaponType();
    }


    /**
     * Get the attack distance for cavalry units.
     * <p>
     * A mounted unit does not fight with a spearman's footwork - it has a horse under it and its own reach and damage
     * treatment - so the inherited figure it scales is a swordsman's {@code MAX_DISTANCE_FOR_ATTACK}. When the
     * cavalryman is in fact holding a spear, that came out shorter than the weapon in his hand: vanilla writes
     * {@code min_reach 2.0 / max_reach 4.5} on every one of its spears with a {@code mob_factor} of 0.5, which is
     * 2.25 blocks from the eyes for a non-player wielder, and about 2.85 once the guard's own width converts it to
     * the centre-to-centre distance this method is measured in. The cavalry figure was 2.4. Taking whichever is
     * greater lets a lance be used at the length it actually has, and leaves a cavalryman carrying anything else
     * exactly where he was.
     *
     * @return the attack distance, increased by {@link #CAVALRY_RANGE_MULTIPLIER}.
     */
    @Override
    protected double getAttackDistance()
    {
        final double mountedReach = super.getAttackDistance() * CAVALRY_RANGE_MULTIPLIER;

        return isUsingSpear() ? Math.max(mountedReach, getSpearReach()) : mountedReach;
    }

    /**
     * Get the icon to display when in combat.
     *
     * @return the icon.
     */
    @Override
    protected VisibleCitizenStatus getCombatStatus()
    {
        return CAVALRY_COMBAT_ICON;
    }

}
