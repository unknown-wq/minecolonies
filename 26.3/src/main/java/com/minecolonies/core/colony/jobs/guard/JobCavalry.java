package com.minecolonies.core.colony.jobs.guard;

import net.minecraft.core.UUIDUtil;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import net.minecraft.resources.Identifier;

import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.core.entity.ai.workers.guard.EntityAICavalry;
import com.minecolonies.core.util.AttributeModifierUtils;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Items;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.NotNull;

import static com.minecolonies.api.research.util.ResearchConstants.SHIELD_USAGE;
import static com.minecolonies.api.util.constant.CitizenConstants.GUARD_HEALTH_MOD_LEVEL_NAME;
import static com.minecolonies.api.util.constant.GuardConstants.CAVALRY_HP_BONUS;

import java.util.UUID;

/**
 * The Cavalry job class.
 */
public class JobCavalry extends AbstractJobGuard<JobCavalry>
{
    public static final float MOUNT_DAMAGE_SPLIT = .20f;
    public static final int DININGHALL_HORSE_PARKING_RANGE = 40;
    private static final String TAG_MOUNT = "mount";

    /**
     * The UUID of the mount.
     */
    protected UUID myMount = null;

    /**
     * Initialize citizen data.
     *
     * @param entity the citizen data.
     */
    public JobCavalry(final ICitizenData entity)
    {
        super(entity);
    }

    @Override
    public EntityAICavalry generateGuardAI()
    {
        return new EntityAICavalry(this);
    }

    /**
     * Fired when level increases.
     */
    @Override
    public void onLevelUp()
    {
        // Bonus Health for cavalry matches knights (gets reset upon Firing)
        if (getCitizen().getEntity().isPresent())
        {
            final AbstractEntityCitizen citizen = getCitizen().getEntity().get();

            // +1 Heart every 2 level
            final AttributeModifier healthModLevel =
              new AttributeModifier(GUARD_HEALTH_MOD_LEVEL_NAME,
                getCitizen().getCitizenSkillHandler().getLevel(Skill.Stamina) + CAVALRY_HP_BONUS,
                AttributeModifier.Operation.ADD_VALUE);
            AttributeModifierUtils.addHealthModifier(citizen, healthModLevel);
        }
    }

    @Override
    public Identifier getModel()
    {
        return ModModelTypes.KNIGHT_GUARD_ID;
    }

    @Override
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag compound = super.serializeNBT(provider);

        if (myMount != null)
        {
            compound.store(TAG_MOUNT, UUIDUtil.CODEC, myMount);
        }

        return compound;
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);
        myMount = compound.contains(TAG_MOUNT) ? compound.read(TAG_MOUNT, UUIDUtil.CODEC).orElse(null) : null;
    }

    /**
     * Whether the citizen will ignore damage from the given source.
     * Units will ignore explosion and projectile damage if they have a shield in their offhand and the SHIELD_USAGE research is enabled.
     *
     * @param damageSource the source of the damage
     * @return true if the citizen will ignore the damage, false otherwise
     */
    @Override
    public boolean ignoresDamage(@NotNull final DamageSource damageSource)
    {
        // The shield has to actually be up, and asking whether the damage counts may not change anything about the
        // rider. This used to accept a shield anywhere in the pack and then equip it, raise it, stamp the colony flag
        // on it and spend saturation as a side effect of the question, so a researched rider with a spare shield in
        // his bag shrugged off every creeper and every arrow, facing away or not. MeleeCombatAI#attackProtect is what
        // raises the shield, every eight ticks while the rider is fighting and not about to swing, and it applies the
        // flag there; a rider in a fight still blocks, one caught unawares does not.
        final boolean applicableDamageSource = damageSource.is(DamageTypeTags.IS_EXPLOSION) || damageSource.is(DamageTypeTags.IS_PROJECTILE);

        if (applicableDamageSource && this.getColony().getResearchManager().getResearchEffects().getEffectStrength(SHIELD_USAGE) > 0
              && this.getCitizen().getEntity().isPresent())
        {
            final AbstractEntityCitizen worker = this.getCitizen().getEntity().get();
            if (worker.isUsingItem() && worker.getUsedItemHand() == InteractionHand.OFF_HAND
                  && worker.getInventoryCitizen().getHeldItem(InteractionHand.OFF_HAND).is(Items.SHIELD))
            {
                return true;
            }
        }
        return super.ignoresDamage(damageSource);
    }

    /**
     * If the cavalry unity is missing a mount.
     *
     * @return true if so.
     */
    public boolean isMissingMount()
    {
        return myMount == null;
    }

    /**
     * Sets the mount UUID.
     *
     * @param mountUUID the mount UUID.
     */
    public void setMount(final UUID mountUUID)
    {
        this.myMount = mountUUID;
    }

    /**
     * Return the current mount for the cavalry job.
     *
     * @return
     */
    public UUID getMount()
    {
        return this.myMount;
    }

    /**
     * The fraction of damage that is applied to the mount instead of the rider.
     * This is used to calculate the damage to apply to the mount when the rider is attacked.
     * @return the fraction of damage to apply to the mount.
     */
    public float getMountDamageSplit()
    {
        return MOUNT_DAMAGE_SPLIT;
    }

    /**
     * Gets the weapon type that the AI will look for when checking if it can attack.
     *
     * @return the weapon type.
     */
    public static EquipmentTypeEntry getWeaponType()
    {
        return ModEquipmentTypes.spear.get();
    }
}
