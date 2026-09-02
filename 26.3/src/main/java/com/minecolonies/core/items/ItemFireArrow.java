package com.minecolonies.core.items;

import com.minecolonies.api.entity.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

/**
 * Class handling the Scepter for the Pharao.
 */
public class ItemFireArrow extends ArrowItem
{
    /**
     * Constructor method for the Chief Sword Item
     *
     * @param properties the properties.
     */
    public ItemFireArrow(final Properties properties)
    {
        super(properties);
    }

    // 26.2: EntityType#create takes an EntitySpawnReason now.
    @Override
    public AbstractArrow createArrow(final Level worldIn, final ItemStack stack, final LivingEntity shooter, @Nullable final ItemStack bow)
    {
        final AbstractArrow entity = ModEntities.FIREARROW.create(worldIn, EntitySpawnReason.TRIGGERED);
        if (entity == null)
        {
            return super.createArrow(worldIn, stack, shooter, bow);
        }
        entity.setOwner(shooter);
        applyWeapon(entity, worldIn, stack, bow);
        return entity;
    }

    /**
     * Carry over what the bow that fired this arrow contributes to it.
     * <p>
     * Vanilla does this in the arrow's own constructor, which takes the ammunition and the weapon
     * (AbstractArrow.java:85-113). That constructor is not reachable here: the fire arrow is a subclass of
     * {@code Arrow}, which declares no constructor carrying an entity type alongside those two stacks, so the
     * entity has to be built from its type and the weapon applied afterwards. This does the same two things that
     * constructor does, in the same order - the arrow keeps the weapon it was fired from, which is what vanilla
     * reads back for a Punch bow's knockback and for the killed-by-arrow trigger, and the piercing the weapon
     * grants is set on it. The field holding the weapon is opened for this in the mod's access widener.
     *
     * @param arrow      the arrow about to be fired.
     * @param world      the level it is fired in.
     * @param ammunition the stack it was drawn from.
     * @param weapon     the bow it is fired from, if any.
     */
    private static void applyWeapon(final AbstractArrow arrow, final Level world, final ItemStack ammunition, @Nullable final ItemStack weapon)
    {
        if (weapon == null || weapon.isEmpty() || !(world instanceof final ServerLevel serverLevel))
        {
            return;
        }

        arrow.firedFromWeapon = weapon.copy();

        final int pierceLevel = EnchantmentHelper.getPiercingCount(serverLevel, weapon, ammunition);
        if (pierceLevel > 0)
        {
            arrow.setPierceLevel((byte) pierceLevel);
        }
    }

    // TODO(port-26.2): DISABLED -- NeoForge's IItemExtension#hasCustomEntity/#createEntity (which let a dropped
    // item stack become a custom ItemEntity) have no counterpart in Fabric or vanilla 26.2. The fire arrow item
    // now drops as an ordinary ItemEntity; only the *fired* projectile is custom, which is what createArrow
    // above still provides.
    // Original NeoForge implementation:
    //     public boolean hasCustomEntity(final ItemStack stack) { return true; }
    //     public Entity createEntity(final Level world, final Entity location, final ItemStack itemstack)
    //     { return ModEntities.FIREARROW.create(world); }
}
