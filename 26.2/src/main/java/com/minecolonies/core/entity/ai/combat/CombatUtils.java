package com.minecolonies.core.entity.ai.combat;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.items.ItemSpear;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;

import java.util.Map;

/**
 * Utility class of combat functions
 */
public class CombatUtils
{
    /**
     * Shooting constants
     */
    private static final double AIM_HEIGHT                     = 2.0D;
    private static final double ARROW_SPEED                    = 1.4D;
    private static final double AIM_SLIGHTLY_HIGHER_MULTIPLIER = 0.18;
    private static final double SPEED_FOR_DIST                 = 35;

    /**
     * Get an arrow entity for the given shooter
     *
     * @param shooter entity
     * @return arrow entity
     */
    public static AbstractArrow createArrowForShooter(final LivingEntity shooter)
    {
        AbstractArrow arrowEntity = ModEntities.MC_NORMAL_ARROW.create(shooter.level(), EntitySpawnReason.MOB_SUMMONED);

        final ItemStack rangedWeapon = shooter.getItemInHand(InteractionHand.MAIN_HAND);
        final Item rangedWeaponItem = rangedWeapon.getItem();

        arrowEntity.setOwner(shooter);
        // PORT-NOTE(26.2/Fabric): NeoForge's BowItem#customArrow(arrow, ammo, bow) modding hook has
        // no Fabric equivalent, so bows always produce the plain mod arrow now.
        if (rangedWeaponItem instanceof ItemSpear)
        {
            arrowEntity = ModEntities.SPEAR.create(shooter.level(), EntitySpawnReason.MOB_SUMMONED);
        }
        else if (rangedWeaponItem instanceof TridentItem)
        {
            arrowEntity = EntityTypes.TRIDENT.create(shooter.level(), EntitySpawnReason.MOB_SUMMONED);
        }

        arrowEntity.setOwner(shooter);
        arrowEntity.setPos(shooter.getX(), shooter.getY() + 1, shooter.getZ());
        return arrowEntity;
    }

    /**
     * Shoots a given arrow at the given target with a hit chance
     *
     * @param arrow     the arrow entity to be shot
     * @param target    the target to be shot at
     * @param hitChance the chance the target will be hit
     */
    public static void shootArrow(final AbstractArrow arrow, final LivingEntity target, final float hitChance)
    {
        final double xVector = target.getX() - arrow.getX();
        final double yVector = target.getBoundingBox().minY + target.getBbHeight() / AIM_HEIGHT - arrow.getY();

        final double zVector = target.getZ() - arrow.getZ();
        final double distance = Mth.sqrt((float) (xVector * xVector + zVector * zVector));
        final double dist3d = Mth.sqrt((float) (yVector * yVector + xVector * xVector + zVector * zVector));
        // PORT-NOTE(26.2): upstream read arrow.shotFromCrossbow(); AbstractArrow carries no crossbow flag at all
        // in 26.2 (grep the class — there is no "crossbow" in it), so the branch had to be resolved by hand.
        //
        // It resolves to *always false*, because that is what it did at runtime in 1.21.1. The flag is set only by
        // vanilla CrossbowItem's shooting path, and guard arrows never go through it: they are built directly as
        // ModEntities.MC_NORMAL_ARROW in createArrowForShooter and fired here. setShotFromCrossbow appears nowhere
        // in the 1.21.1 tree; the one place that looks like it sets the flag, RangeCombatAI:213, actually reads
        // it — `arrow.shotFromCrossbow();` as a bare statement, a getter whose result is discarded. Upstream
        // clearly meant to mark marksman bolts and never did, so every guard arrow flew on
        // AIM_SLIGHTLY_HIGHER_MULTIPLIER and played ARROW_SHOOT.
        //
        // An earlier pass of this port inferred the flag from the shooter's weapon instead. That inference is true
        // for the marksman (JobMarksman#getEquipmentType is ModEquipmentTypes.crossbow, which is defined as
        // `getItem() instanceof CrossbowItem`), so it quietly cut his gravity compensation from 0.18 to 0.05 —
        // roughly a quarter — making him undershoot distant targets, and played CROSSBOW_SHOOT at the target's
        // position on every shot. Neither happened in 1.21.1. This is a port; parity wins. If the 0.05 arc is
        // wanted, it is a balance change and belongs in its own commit.
        final double distanceMultiplier = AIM_SLIGHTLY_HIGHER_MULTIPLIER;
        arrow.shoot(xVector, yVector + distance * distanceMultiplier, zVector, (float) (ARROW_SPEED * 1 + (dist3d / SPEED_FOR_DIST)), (float) hitChance);
        target.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (target.level().getRandom().nextFloat() * 0.4F + 1.2F));
        target.level().addFreshEntity(arrow);
    }

    /**
     * Launches an arrow along a velocity that has already been solved for, rather than at an entity.
     *
     * <p>{@link #shootArrow} cannot be used for anything that is not a {@link LivingEntity}, and more
     * to the point it does not aim: it points the arrow at the target and adds
     * {@code AIM_SLIGHTLY_HIGHER_MULTIPLIER} times the range as a gravity fudge, which is tuned for a
     * mob walking about at ground level. Against something moving fifty-odd blocks a second at
     * altitude that fudge is not an approximation, it is a different sport. Callers that have a real
     * ballistic solution — and a lead — pass the answer in here instead.
     *
     * <p>Uses {@code Projectile#shoot} rather than setting the delta movement directly, because that
     * is also what sets the arrow's pitch and yaw; an arrow with the right velocity and no rotation
     * flies correctly and is drawn sideways. Passing the vector's own length as the power and zero
     * uncertainty makes the call an exact assignment.
     *
     * @param arrow    the arrow to launch.
     * @param velocity the launch velocity, in blocks/tick.
     */
    public static void launchArrow(final AbstractArrow arrow, final net.minecraft.world.phys.Vec3 velocity)
    {
        arrow.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0.0F);
        arrow.level().addFreshEntity(arrow);
    }

    /**
     * Actions on changing to a new target entity
     */
    public static void notifyGuardsOfTarget(final AbstractEntityCitizen user, final LivingEntity target, final int callRange)
    {
        for (final ICitizenData citizen : user.getCitizenData().getWorkBuilding().getAllAssignedCitizen())
        {
            if (citizen.getEntity().isPresent() && citizen.getEntity().get().getLastHurtByMob() == null)
            {
                ((EntityCitizen) citizen.getEntity().get()).getThreatTable().addThreat(target, 0);
            }
        }

        if (target instanceof AbstractEntityMinecoloniesRaider)
        {
            // The cached guard-building list, not the whole building map. This runs on every target change to a
            // raider -- up to once per five ticks per guard during a raid -- and it exists to set one BlockPos on the
            // handful of guard buildings within forty blocks, so walking every hut in the colony to find them was
            // the largest avoidable number on the guard path. Buildings whose task does not walk a patrol never read
            // the point, so they are skipped too.
            for (final IGuardBuilding building : user.getCitizenColonyHandler().getColonyOrRegister().getServerBuildingManager().getGuardBuildings())
            {
                if (building.walksAPatrol() && user.blockPosition().distSqr(building.getID()) < callRange)
                {
                    building.setTempNextPatrolPoint(target.blockPosition());
                }
            }
        }
    }
}
