package com.minecolonies.core.entity.mobs.aitasks;

import net.minecraft.core.registries.BuiltInRegistries;
import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import com.minecolonies.api.entity.mobs.ICustomAttackSound;
import com.minecolonies.api.entity.mobs.IRangedMobEntity;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.EntityUtils;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.events.raid.RaiderConstants;
import com.minecolonies.core.entity.ai.combat.AttackMoveAI;
import com.minecolonies.core.entity.ai.combat.TargetAI;
import com.minecolonies.core.entity.ai.combat.CombatUtils;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.other.CustomArrowEntity;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

import static com.minecolonies.api.entity.mobs.RaiderMobUtils.MOB_ATTACK_DAMAGE;

/**
 * Raider AI for shooting arrows at a target
 */
public class RaiderRangedAI<T extends AbstractEntityMinecoloniesMonster & IThreatTableEntity & IRangedMobEntity> extends AttackMoveAI<T>
{
    /**
     * Max delay between attacks is 3s, aka 60 ticks.
     */
    private static final int ATTACK_DELAY = 60;

    /**
     * How many ticks we activate the bow before shooting
     */
    private static final int BOW_HOLDING_DELAY = 40;

    /**
     * Difficulty level at which arrows do pierce
     */
    private static final double ARROW_PIERCE_DIFFICULTY = 3.0d;

    /**
     * Movement speed
     */
    private static final double COMBAT_MOVEMENT_SPEED = 1.1;

    /**
     * Attack distance
     */
    private static final double MAX_ATTACK_DISTANCE = 20.0D;

    /**
     * Sound variance
     */
    private static final double PITCH_MULTIPLIER = 0.4;
    private static final double BASE_PITCH       = 0.8D;
    private static final double PITCH_DIVIDER = 1.0D;

    /**
     * Counter for flying time
     */
    private int flightCounter = 0;

    public RaiderRangedAI(
      final T owner,
      final ITickRateStateMachine<IState> stateMachine)
    {
        super(owner, stateMachine);
    }

    @Override
    protected boolean isInDistanceForAttack(final LivingEntity target)
    {
        if (EntityUtils.isFlying(target))
        {
            flightCounter++;
        }
        else
        {
            flightCounter = 0;
        }

        if (flightCounter > 5)
        {
            // Always allowed to try attacking flying targets
            return true;
        }

        return super.isInAttackDistance(target);
    }

    @Override
    protected void doAttack(final LivingEntity target)
    {
        user.getNavigation().stop();

        // Setup arrow
        AbstractArrow arrowEntity = CombatUtils.createArrowForShooter(user);
        if (this.user.penetrateFluids() && arrowEntity instanceof CustomArrowEntity customArrowEntity )
        {
            customArrowEntity.setWaterInertia(0.99f);
        }

        arrowEntity.setBaseDamage(user.getAttribute(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(MOB_ATTACK_DAMAGE.get())).getValue());
        if (flightCounter > 5 && arrowEntity instanceof CustomArrowEntity)
        {
            ((CustomArrowEntity) arrowEntity).setPlayerArmorPierce();
            arrowEntity.setRemainingFireTicks(10 * 20);
            arrowEntity.setBaseDamage(10);
        }

        if (user.getDifficulty() > ARROW_PIERCE_DIFFICULTY)
        {
            arrowEntity.setPierceLevel((byte) 2);
        }

        // Shoot arrow
        CombatUtils.shootArrow(arrowEntity, target, 10.0f);

        // Visuals
        user.swingForAttack(InteractionHand.MAIN_HAND);
        user.stopUsingItem();
        SoundEvent attackSound = SoundEvents.SKELETON_SHOOT;
        if (arrowEntity instanceof ICustomAttackSound)
        {
            attackSound = ((ICustomAttackSound) arrowEntity).getAttackSound();
        }
        user.playSound(attackSound, (float) 1.0D, (float) getRandomPitch());
    }

    /**
     * Random pitch generator
     *
     * @return A random double to act as a pitch value
     */
    private double getRandomPitch()
    {
        return PITCH_DIVIDER / (user.getRandom().nextDouble() * PITCH_MULTIPLIER + BASE_PITCH);
    }

    @Override
    protected double getAttackDistance()
    {
        return MAX_ATTACK_DISTANCE * Math.max(user.getDifficulty(), 2.0d);
    }

    @Override
    protected int getAttackDelay()
    {
        if (flightCounter > 5)
        {
            return 10;
        }

        return ATTACK_DELAY;
    }

    @Override
    public boolean canAttack()
    {
        if (nextAttackTime - BOW_HOLDING_DELAY >= user.level().getGameTime() && !user.isUsingItem() && !user.getMainHandItem().isEmpty())
        {
            user.startUsingItem(InteractionHand.MAIN_HAND);
        }

        return true;
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
    protected PathResult moveInAttackPosition(final LivingEntity target)
    {
        EntityNavigationUtils.walkToPos(user, target.blockPosition(), (int) getAttackDistance(), false, COMBAT_MOVEMENT_SPEED);
        return user.getNavigation().getPathResult();
    }

    @Override
    protected boolean isAttackableTarget(final LivingEntity target)
    {
        return ((target instanceof EntityCitizen || target instanceof AbstractVillager || target instanceof IronGolem) && !target.isInvisible())
            || (target instanceof Player && !((Player) target).isCreative() && !target.isSpectator());
    }

    @Override
    protected boolean isWithinPersecutionDistance(final LivingEntity target)
    {
        return BlockPosUtil.getDistanceSquared(user.blockPosition(), target.blockPosition()) <= RaiderConstants.MAX_ARCHER_RAIDER_PERSECUTION_DISTANCE * RaiderConstants.MAX_ARCHER_RAIDER_PERSECUTION_DISTANCE;
    }

    /**
     * Horizontal half-width of the target search box on its full-range tier.
     * <p>
     * Upstream never overrode this either, so an archer raider inherited {@link TargetAI#getSearchRange()} = 16 and
     * got a 48 x 6 x 32 box, while {@link #getAttackDistance()} is {@link #MAX_ATTACK_DISTANCE} = 20 times
     * <code>max(difficulty, 2)</code>, i.e. never below <b>40</b> blocks. The weapon outranged the eye by a factor of
     * two and the archer could only shoot what had already walked well inside its own range.
     * <p>
     * The default is 40, the bow's floor, so the eye now reaches the weapon. {@link TargetAI#getSearchArea()} sweeps
     * this range as a full box on its slow tier (and the near cube between), so the box reaches 40 in every
     * direction; that is inside
     * {@link com.minecolonies.core.colony.events.raid.RaiderConstants#MAX_ARCHER_RAIDER_PERSECUTION_DISTANCE} = 80,
     * which is what {@link #isWithinPersecutionDistance(LivingEntity)} would otherwise immediately throw the target
     * away for. The config maximum of 64 stays inside that persecution limit for the same reason.
     * <p>
     * Not clamped to the raider's live <code>getAttackDistance()</code>, which rises with raid difficulty: acquiring
     * at the floor and shooting further is harmless, and a search box that grows with difficulty grows the raid's
     * dominant per-entity cost with it, which is precisely what the config exists to bound.
     *
     * @return the horizontal search range, from the server config.
     */
    @Override
    protected int getSearchRange()
    {
        return MineColonies.getConfig().getServer().raiderArcherVision.get();
    }

    /**
     * Vertical half-height of the target search box. Shares
     * {@link com.minecolonies.core.entity.mobs.aitasks.RaiderMeleeAI#getYSearchRange()}'s config value and its
     * reasoning, with one difference that matters: the archer is the only raider whose weapon can actually reach an
     * elevated target, so this is the override that turns "the raid ignores the archer on my tower" into return fire.
     *
     * @return the vertical half-height, from the server config.
     */
    @Override
    protected int getYSearchRange()
    {
        return MineColonies.getConfig().getServer().raiderVerticalVision.get();
    }
}
