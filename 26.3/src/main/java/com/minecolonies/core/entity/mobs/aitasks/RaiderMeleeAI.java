package com.minecolonies.core.entity.mobs.aitasks;

import net.minecraft.core.registries.BuiltInRegistries;
import com.minecolonies.api.entity.ai.combat.threat.IThreatTableEntity;
import com.minecolonies.api.entity.ai.statemachine.states.IState;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesMonster;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.events.raid.RaiderConstants;
import com.minecolonies.core.entity.ai.combat.AttackMoveAI;
import com.minecolonies.core.entity.ai.combat.TargetAI;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.pathfinding.navigation.EntityNavigationUtils;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

import static com.minecolonies.api.entity.mobs.RaiderMobUtils.MOB_ATTACK_DAMAGE;
import static com.minecolonies.core.colony.events.raid.RaiderConstants.*;

/**
 * Raider AI for melee attacking a target
 */
public class RaiderMeleeAI<T extends AbstractEntityMinecoloniesMonster & IThreatTableEntity> extends AttackMoveAI<T>
{
    public RaiderMeleeAI(
      final T owner,
      final ITickRateStateMachine<IState> stateMachine)
    {
        super(owner, stateMachine);
    }

    @Override
    protected void doAttack(final LivingEntity target)
    {
        double damageToBeDealt = user.getAttribute(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(MOB_ATTACK_DAMAGE.get())).getValue();
        if (user.getName().getContents() instanceof TranslatableContents translatableContents)
        {
            target.hurt(target.level().damageSources().source(ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(Constants.MOD_ID, translatableContents.getKey().replace("entity.minecolonies.", ""))), user), (float) damageToBeDealt);
        }
        else
        {
            target.hurt(target.level().damageSources().mobAttack(user), (float) damageToBeDealt);
        }
        user.swingForAttack(InteractionHand.MAIN_HAND);
        user.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, (float) 1.0D, (float) SoundUtils.getRandomPitch(user.getRandom()));
        target.setLastHurtByMob(user);
    }

    @Override
    protected double getAttackDistance()
    {
        return user.getDifficulty() < EXTENDED_REACH_DIFFICULTY ? MIN_DISTANCE_FOR_ATTACK : MIN_DISTANCE_FOR_ATTACK + EXTENDED_REACH;
    }

    @Override
    protected int getAttackDelay()
    {
        return MELEE_ATTACK_DELAY;
    }

    @Override
    protected PathResult moveInAttackPosition(final LivingEntity target)
    {
        EntityNavigationUtils.walkToPos(user,
            target.blockPosition(),
            (int) getAttackDistance(),
            false,
            user.getDifficulty() < ADD_SPEED_DIFFICULTY ? BASE_COMBAT_SPEED : BASE_COMBAT_SPEED * BONUS_SPEED);
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
        return BlockPosUtil.getDistanceSquared(user.blockPosition(), target.blockPosition()) <= RaiderConstants.MAX_MELEE_RAIDER_PERSECUTION_DISTANCE * RaiderConstants.MAX_MELEE_RAIDER_PERSECUTION_DISTANCE;
    }

    @Override
    protected int getSearchRange()
    {
        return 0;
    }

    /**
     * Vertical half-height of the target search box, for every raider and camp mob that fights hand to hand.
     * <p>
     * Upstream never overrode this, so a raider inherited {@link TargetAI#getYSearchRange()}, which reads the
     * <em>guard</em> config and defaults to {@link com.minecolonies.api.util.constant.GuardConstants#Y_VISION} = 3.
     * With {@link #getSearchRange()} returning 0 the resulting box is 32 x 6 x 32: sixteen blocks sideways and three
     * up. A defender standing on a four-block wall is outside it, which is why a horde walks under a manned wall to
     * reach whatever building it was sent to.
     * <p>
     * The default is {@link com.minecolonies.api.util.constant.GuardConstants#DEFAULT_VISION} = 16, the same constant
     * that already sets this raider's horizontal reach, so the box becomes a cube rather than a slab -- a raider sees
     * as far up as it already saw sideways, and no new magic number enters the tree. Sixteen is also the point past
     * which more vertical range buys a melee raider nothing: its own reach is
     * {@link com.minecolonies.core.colony.events.raid.RaiderConstants#MIN_DISTANCE_FOR_ATTACK} = 2.5, so it can never
     * hit an elevated target; all the range does is make it path towards the foot of the wall, and every wall and all
     * but the tallest towers are inside 16.
     *
     * @return the vertical half-height, from the server config.
     */
    @Override
    protected int getYSearchRange()
    {
        return MineColonies.getConfig().getServer().raiderVerticalVision.get();
    }
}
