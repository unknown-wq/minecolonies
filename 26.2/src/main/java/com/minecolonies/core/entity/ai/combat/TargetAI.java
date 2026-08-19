package com.minecolonies.core.entity.ai.combat;

import com.minecolonies.api.entity.ai.IStateAI;
import com.minecolonies.api.entity.ai.combat.CombatAIStates;
import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.ai.combat.threat.ThreatTableEntry;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.core.MineColonies;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.fabricmc.fabric.api.entity.FakePlayer;

import java.util.List;

import static com.minecolonies.api.util.constant.GuardConstants.DEFAULT_VISION;
import static com.minecolonies.api.util.constant.GuardConstants.Y_VISION;

/**
 * Target search AI
 */
public class TargetAI<T extends Mob & IThreatTableEntity> implements IStateAI
{
    /**
     * The entity this AI runs for
     */
    protected final T user;

    /**
     * Current target reference
     */
    protected LivingEntity target;

    /**
     * Which of the four horizontal directions {@link #getSearchArea()} will stretch the search box towards next.
     * Advanced once per scan so the four are covered in turn. Seeded at random so guards standing next to each
     * other do not all sweep in lockstep.
     */
    private int searchDirectionIndex = -1;

    /**
     * Constructor method for AI
     *
     * @param user The creature which is using the AI
     */
    public TargetAI(final T user, final int targetFrequency, final ITickRateStateMachine stateMachine)
    {
        this.user = user;
        stateMachine.addTransition(new TickingTransition<>(CombatAIStates.NO_TARGET, this::checkForTarget, () -> CombatAIStates.ATTACKING, 5));
        stateMachine.addTransition(new TickingTransition<>(CombatAIStates.NO_TARGET, this::searchNearbyTarget, () -> CombatAIStates.ATTACKING, targetFrequency));
    }

    /**
     * Checks if the current targets is still valid, if not searches a new target. Adds experience if the current target died.
     *
     * @return true if we found a target, false if no target.
     */
    protected boolean checkForTarget()
    {
        if (target != null && !target.isAlive())
        {
            onTargetDied(target);
            target = null;
        }

        final ThreatTableEntry nextTarget = user.getThreatTable().getTarget();
        if (nextTarget == null)
        {
            return false;
        }

        if (isEntityValidTarget(nextTarget.getEntity()))
        {
            if (target != nextTarget.getEntity())
            {
                target = nextTarget.getEntity();
                onTargetChange(target);
            }

            return true;
        }
        else
        {
            resetTarget();
            return false;
        }
    }

    /**
     * Checks whether the given entity is a valid target to attack.
     *
     * @param target Entity to check
     * @return true if should attack
     */
    public boolean isEntityValidTarget(final LivingEntity target)
    {
        if (target == user || target == null || !target.isAlive() || !isWithinPersecutionDistance(target) || target instanceof FakePlayer)
        {
            return false;
        }

        if (target == user.getLastHurtByMob())
        {
            return true;
        }

        return isAttackableTarget(target);
    }

    /**
     * Resets the current target and removes it from all saved targets.
     */
    public void resetTarget()
    {
        if (target == null)
        {
            return;
        }

        if (user.getLastHurtMob() == target)
        {
            user.setLastHurtMob(null);
        }

        if (user.getLastHurtByMob() == target)
        {
            user.setLastHurtByMob(null);
        }

        user.getThreatTable().markInvalidTarget();
        target = null;
    }

    /**
     * Get a target for the guard. First check if we're under attack by anything and switch target if necessary.
     *
     * @return The next IAIState to go to.
     */
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

        boolean foundTarget = false;
        // skipSearch() is how a guard notices a sleeping colleague and schedules the wake-up. It used to abort the
        // whole scan (`return false`), so any sleeping guard anywhere in the search box hid every real enemy in the
        // same box — and the box is filled in chunk-iteration order, so which one that was is not deterministic.
        // During a night raid on the barracks the awake guards simply did not react until the sleepers were up.
        // Now the scan continues; the flag keeps the wake-up firing at most once per scan, as before.
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

            if (isEntityValidTarget(entity) && user.getSensing().hasLineOfSight(entity))
            {
                user.getThreatTable().addThreat(entity, 0);

                // Report a find only if the threat table will actually hand this entity back.
                //
                // A target the guard has already given up on -- one it could see but never reach, so move()
                // walked its threat down and called resetTarget() -- keeps a threat of -1, and
                // ThreatTable#getTarget refuses to return an entry with a negative threat. It still passes
                // isEntityValidTarget, so upstream reports it as found, the state machine goes to ATTACKING,
                // and checkForTarget throws the guard straight back to NO_TARGET on the next tick. That
                // repeats once per scan for as long as the thing stands there.
                //
                // Measured on a stand (GUARD-AUDIT.md 3.2): three knights against three zombies on pillars
                // they could see and could not reach made 86 NO_TARGET -> ATTACKING -> NO_TARGET round trips
                // in 250 s; with this line, 11 over the identical experiment. The path searches themselves
                // barely move (112 -> 105, node-limited 32 -> 28): those are spent on the engagements that
                // do happen, which is legitimate work. What this removes is the thrash after the guard has
                // already decided to give up.
                //
                // Nothing about *which* enemies a guard fights changes: an entry at -1 is one the guard's own
                // give-up logic put there, and it recovers exactly as before -- the entity ages out of the
                // table after MAX_TRACKING_TICKS, or it hits the guard and addThreat lifts it back above zero.
                foundTarget |= user.getThreatTable().getThreatFor(entity) >= 0;
            }
        }

        return foundTarget;
    }

    /**
     * Skips the search if true
     *
     * @param entity checked entity
     * @return true if skip
     */
    protected boolean skipSearch(final LivingEntity entity)
    {
        return false;
    }

    /**
     * Get the {@link AABB} we're searching for targets in.
     *
     * @return the {@link AABB}
     */
    protected AABB getSearchArea()
    {
        final BlockPos raiderPos = user.blockPosition();
        // Upstream picked one of the four horizontal directions at random per scan and only stretched the box that
        // way. With a scan every 80 ticks that gives no upper bound on how long a raider can stand behind a guard
        // unnoticed — four heads in a row is 16 seconds, and "never" has non-zero probability. Cycling through the
        // four instead costs exactly the same (one box, one getEntitiesOfClass) and caps the worst case at four
        // scans. The starting index is still random, so neighbouring guards stay out of phase.
        if (searchDirectionIndex < 0)
        {
            searchDirectionIndex = user.getRandom().nextInt(4);
        }
        else
        {
            searchDirectionIndex = (searchDirectionIndex + 1) % 4;
        }
        final Direction randomDirection = Direction.from3DDataValue(searchDirectionIndex + 2);
        final int searchRange = getSearchRange();
        final double x1 = raiderPos.getX() + (Math.max(searchRange * randomDirection.getStepX() + DEFAULT_VISION, DEFAULT_VISION));
        final double x2 = raiderPos.getX() + (Math.min(searchRange * randomDirection.getStepX() - DEFAULT_VISION, -DEFAULT_VISION));
        final double y1 = raiderPos.getY() + (getYSearchRange());
        final double y2 = raiderPos.getY() - (getYSearchRange());
        final double z1 = raiderPos.getZ() + (Math.max(searchRange * randomDirection.getStepZ() + DEFAULT_VISION, DEFAULT_VISION));
        final double z2 = raiderPos.getZ() + (Math.min(searchRange * randomDirection.getStepZ() - DEFAULT_VISION, -DEFAULT_VISION));

        return new AABB(x1, y1, z1, x2, y2, z2);
    }

    /**
     * Get the Y search range.
     * <p>
     * The stock value is {@link com.minecolonies.api.util.constant.GuardConstants#Y_VISION} = 3, which is tight
     * enough to be visible in play: measured on a stand, a knight four blocks from a zombie six blocks above it,
     * in clear line of sight, made zero target acquisitions in 250 s, while an archer beside it engaged the
     * identical target three times -- {@link com.minecolonies.core.entity.ai.workers.guard.RangeCombatAI} raises
     * its own Y range to 28 while guarding, and nothing raises the knight's. That is the "my guards ignore the mob
     * on the wall" complaint. Raising it changes which enemies a guard picks, i.e. balance, so it is a server
     * config defaulting to the stock number rather than a new constant.
     *
     * @return the vertical half-height of the target search box.
     */
    protected int getYSearchRange()
    {
        return MineColonies.getConfig().getServer().guardVerticalVision.get();
    }

    /**
     * The search range in blocks, used for determining a shaped aabb entity lookup
     *
     * @return
     */
    protected int getSearchRange()
    {
        return 16;
    }

    /**
     * Whether the target is attackable
     *
     * @param target
     * @return
     */
    protected boolean isAttackableTarget(final LivingEntity target)
    {
        return target instanceof Enemy && !user.getClass().isInstance(target);
    }

    /**
     * Check if the target is within chasing distance
     *
     * @param target
     * @return
     */
    protected boolean isWithinPersecutionDistance(final LivingEntity target)
    {
        return true;
    }

    /**
     * When our previous target has died
     *
     * @param target
     */
    protected void onTargetDied(final LivingEntity target)
    {

    }

    /**
     * Actions on changing to a new target entity
     */
    protected void onTargetChange(final LivingEntity newTarget)
    {
        // Fill vanilla target info in, though we disregard the result
        user.setTarget(newTarget);
    }
}
