package com.minecolonies.api.entity.other;

import org.jetbrains.annotations.NotNull;
import com.minecolonies.api.entity.pathfinding.IStuckHandlerEntity;
import com.minecolonies.api.util.EntityUtils;
import com.minecolonies.api.util.LookHandler;
import com.minecolonies.api.util.constant.ColonyConstants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Special abstract minecolonies mob that overrides laggy vanilla behaviour.
 */
public abstract class AbstractFastMinecoloniesEntity extends PathfinderMob implements IStuckHandlerEntity
{
    /**
     * Whether this entity can be stuck for stuckhandling
     */
    private boolean canBeStuck = true;

    /**
     * Random update variance for this entity, used to spread out updates equalls
     */
    public final int randomVariance = ColonyConstants.rand.nextInt(20);

    /**
     * Cache fluid state
     */
    private boolean isInFluid = false;

    /**
     * Cache fire state
     */
    private boolean onFire = false;

    /**
     * Entity push cache.
     */
    private List<LivingEntity> entityPushCache = new ArrayList<>();

    /**
     * The timepoint at which the entity last collided
     */
    private long lastHorizontalCollision = 0;

    /**
     * Last knockback time
     */
    protected long lastKnockBack = 0;

    /**
     * Create a new instance.
     *
     * @param type    from type.
     * @param worldIn the world.
     */
    protected AbstractFastMinecoloniesEntity(final EntityType<? extends PathfinderMob> type, final Level worldIn)
    {
        super(type, worldIn);
        lookControl = new LookHandler(this);
    }

    @Override
    public boolean canBeLeashed()
    {
        return false;
    }

    @Override
    public boolean canBeStuck()
    {
        return canBeStuck;
    }

    /**
     * Sets whether the entity currently can be stuck
     *
     * @param canBeStuck
     */
    public void setCanBeStuck(final boolean canBeStuck)
    {
        this.canBeStuck = canBeStuck;
    }

    @Override
    protected boolean isHorizontalCollisionMinor(Vec3 vec3)
    {
        lastHorizontalCollision = level().getGameTime();
        return super.isHorizontalCollisionMinor(vec3);
    }

    /**
     * Whether the citizen collided in the last 10 ticks
     *
     * @return
     */
    public boolean hadHorizontalCollission()
    {
        return level().getGameTime() - lastHorizontalCollision < 10;
    }

    // TODO(port-26.2): DISABLED — LivingEntity#checkBedExists is private in 26.2 and the AccessWidener
    // widens it to public *and final* (Fabric AW rule for private methods), so it cannot be overridden.
    // Effect: citizens can again be considered to have a bed by vanilla sleep logic.

    @Override
    protected void removeFrost()
    {

    }

    @Override
    protected void tryAddFrost()
    {

    }

    @Override
    public void onInsideBubbleColumn(boolean down)
    {

    }

    @Override
    protected int decreaseAirSupply(final int supply)
    {
        return supply - 1;
    }

    @Override
    protected int increaseAirSupply(final int supply)
    {
        return supply + 1;
    }

    @Override
    protected void onChangedBlock(final ServerLevel level, final BlockPos pos)
    {
        // This just tries to apply soulspeed or frostwalker
    }

    /**
     * Ignores cramming
     */
    @Override
    public void pushEntities()
    {
        if (this.level().isClientSide())
        {
            this.level().getEntities(EntityTypeTest.forClass(Player.class), this.getBoundingBox(), EntityUtils.pushableBy()).forEach(this::doPush);
        }
        else
        {
            if (this.tickCount % 10 == randomVariance % 10)
            {
                entityPushCache = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox(), EntitySelector.pushableBy(this));
            }

            if (!entityPushCache.isEmpty())
            {
                for (int i = 0, entityPushCacheSize = entityPushCache.size(); i < entityPushCacheSize; i++)
                {
                    final Entity entity = entityPushCache.get(i);
                    if (entity != this && getBoundingBox().contains(entity.position()))
                    {
                        this.doPush(entity);
                    }
                }
            }
        }
    }

    /**
     * Prevent citizens and visitors from travelling to other dimensions through portals.
     */
    @Nullable
    @Override
    public Entity teleport(final TeleportTransition transition)
    {
        return null;
    }

    @Override
    public boolean canSpawnSprintParticle()
    {
        return false;
    }

    // PORT-NOTE(26.2): CHECKED, NOTHING LOST. Entity#updateFluidOnEyes() is gone in 26.2, but the work it used to
    // do is not done at vanilla frequency — it is still throttled, by the override right below.
    // The eye-in-fluid computation moved inside EntityFluidInteraction#update
    // (/opt/mc-src/net/minecraft/world/entity/EntityFluidInteraction.java:32-70, field Tracker.eyesInside), and
    // that method has exactly one caller in the whole game: Entity#updateFluidInteraction
    // (/opt/mc-src/net/minecraft/world/entity/Entity.java:1643-1644) — verified by grep across /opt/mc-src.
    // updateFluidInteraction is overridden and throttled here to one tick in ten. So work upstream throttled twice
    // (20 ticks + 20 ticks) is now throttled once, and more often than either. Do not "restore" anything.

    @Override
    protected boolean updateFluidInteraction()
    {
        // 26.2 renamed updateInWaterStateAndDoFluidPushing() to updateFluidInteraction()
        // (/opt/mc-src/net/minecraft/world/entity/Entity.java:1643).
        if (tickCount % 10 == randomVariance % 10)
        {
            isInFluid = super.updateFluidInteraction();
        }

        return isInFluid;
    }

    @Override
    public void setSharedFlagOnFire(boolean newState)
    {
        if (newState != onFire)
        {
            super.setSharedFlagOnFire(newState);
            onFire = newState;
        }
    }

    @Override
    protected void handlePortal()
    {
        // Noop our entities dont use portals
    }

    @Override
    public void updateSwimming()
    {
        // Noop our entities dont swim
    }

    @Override
    public boolean isInWall()
    {
        if (tickCount % 10 == randomVariance % 10)
        {
            return super.isInWall();
        }

        return false;
    }

    @Override
    public boolean isInWaterOrRain()
    {
        // 26.2 dropped the bubble-column half of isInWaterRainOrBubble(); isInWaterOrRain() is what is left.
        // Used to extinguish fire, only check if on fire
        if (getRemainingFireTicks() > 0 || level().isClientSide())
        {
            return super.isInWaterOrRain();
        }

        return false;
    }

    @Override
    public void updateFallFlying()
    {
        // Simplified updateFallflying to only set flags when they did change
        if (!this.level().isClientSide() && tickCount % 5 == randomVariance % 5)
        {
            boolean flag = this.getSharedFlag(7);
            if (!flag || this.onGround() || this.isPassenger() || this.hasEffect(MobEffects.LEVITATION))
            {
                flag = false;
                this.setSharedFlag(7, flag);
            }
        }
    }

    // PORT-NOTE(26.2): CHECKED, NOTHING LOST. Entity#sendDebugPackets() does not exist in 26.2 — and neither does
    // the work it did: grep for "sendDebugPackets" across /opt/mc-src/net/minecraft/world/entity/Entity.java
    // returns zero hits, so there is no per-entity debug packet left for an override to suppress. The upstream
    // empty override cost and saved exactly nothing here. Not a regression, nothing to restore.

    @Override
    public void setTicksFrozen(int p_146918_)
    {

    }

    // PORT-NOTE(26.2): CHECKED, NOT WORTH RESTORING. LivingEntity#updateSwimAmount is private in 26.2 and the
    // AccessWidener widens private methods to public *and final*, so it genuinely cannot be overridden — that part
    // of the old note is accurate. What the note overstated is the cost. The whole method body
    // (/opt/mc-src/net/minecraft/world/entity/LivingEntity.java:3455-3462) is one float copy, one
    // isVisuallySwimming() call and a Math.min/Math.max — single-digit nanoseconds per entity per tick. Suppressing
    // it is not worth the project's first mixin. Deliberately left alone.

    /**
     * Static Byte values to avoid frequent autoboxing
     */
    final Byte ENABLE  = 2;
    final Byte DISABLE = 0;

    @Override
    public void setShiftKeyDown(boolean enable)
    {
        if (enable)
        {
            this.entityData.set(DATA_SHARED_FLAGS_ID, ENABLE);
        }
        else
        {
            this.entityData.set(DATA_SHARED_FLAGS_ID, DISABLE);
        }
    }

    @Override
    public boolean isShiftKeyDown()
    {
        return (this.entityData.get(DATA_SHARED_FLAGS_ID)).byteValue() == ENABLE.byteValue();
    }

    @Override
    public void knockback(final double power, final double xRatio, final double zRatio,
                          @Nullable final DamageSource source, final float damage, final boolean comesFromEffect)
    {
        // 26.2: LivingEntity#knockback carries the damage source through
        // (/opt/mc-src/net/minecraft/world/entity/LivingEntity.java:1631).
        if (level().getGameTime() - lastKnockBack > 20 * 3)
        {
            lastKnockBack = level().getGameTime();
            super.knockback(power, xRatio, zRatio, source, damage, comesFromEffect);
        }
    }

    @Override
    public boolean hurtServer(@NotNull final ServerLevel level, @NotNull final DamageSource dmgSource, final float dmg)
    {
        // 26.2: Entity#hurt is final and only dispatches; hurtServer is the override point (Entity.java:1918-1931).
        if (dmgSource.getEntity() instanceof AbstractFastMinecoloniesEntity otherFastMinecolEntity && otherFastMinecolEntity.getTeamId() == getTeamId())
        {
            return false;
        }
        return super.hurtServer(level, dmgSource, dmg);
    }

    /**
     * Get the team name of this entity.
     * todo sam make colony ids unique across dimensions.
     * @return the team name.
     */
    public abstract int getTeamId();
}
