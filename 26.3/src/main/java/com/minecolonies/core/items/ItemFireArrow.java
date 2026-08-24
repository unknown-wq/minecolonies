package com.minecolonies.core.items;

import com.minecolonies.api.entity.ModEntities;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
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
        return entity;
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
