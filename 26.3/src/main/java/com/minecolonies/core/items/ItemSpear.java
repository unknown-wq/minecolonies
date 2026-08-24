package com.minecolonies.core.items;

import com.minecolonies.core.entity.other.SpearEntity;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;


public class ItemSpear extends TridentItem
{
    protected static final int SPEAR_BASE_DAMAGE = 3;

    public ItemSpear(final Properties properties)
    {
        super(properties.durability(250));
    }

    @Override
    public boolean releaseUsing(@NotNull ItemStack stack, @NotNull Level worldIn, @NotNull LivingEntity entityLiving, int timeLeft)
    {
        if (entityLiving instanceof Player)
        {
            Player playerEntity = (Player) entityLiving;
            int usedForDuration = this.getUseDuration(stack, entityLiving) - timeLeft;
            if (usedForDuration >= 10)
            {
                if (!worldIn.isClientSide())
                {
                    stack.hurtAndBreak(1, playerEntity, EquipmentSlot.MAINHAND);
                    SpearEntity spearEntity = new SpearEntity(worldIn, playerEntity, stack);

                    if (playerEntity.getAbilities().instabuild)
                    {
                        spearEntity.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                    }
                    else
                    {
                        playerEntity.getInventory().removeItem(stack);
                    }

                    worldIn.addFreshEntity(spearEntity);
                    worldIn.playSound(null, spearEntity, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                }

                SoundEvent soundEvent = SoundEvents.TRIDENT_THROW.value();
                playerEntity.awardStat(Stats.ITEM_USED.get(this));
                worldIn.playSound(null, playerEntity, soundEvent, SoundSource.PLAYERS, 1.0F, 1.0F);
                // 26.2: Item#releaseUsing returns whether the release was handled (was void in 1.21.1).
                return true;
            }
        }
        return false;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull final Level world, final Player playerEntity, @NotNull final InteractionHand hand)
    {
        ItemStack itemstack = playerEntity.getItemInHand(hand);
        if (itemstack.getDamageValue() >= itemstack.getMaxDamage() - 1)
        {
            return InteractionResult.FAIL;
        }
        else
        {
            playerEntity.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
    }

    // TODO(port-26.2): DISABLED -- NeoForge's ItemAbility/ItemAbilities tool-action system has no counterpart in
    // Fabric or vanilla 26.2 (agent A hit the same wall in ModEquipmentTypes).
    //
    // What this override actually did was claim the sword tool actions: sweeping, cutting a cobweb, shearing a
    // pumpkin -- the things vanilla now decides from #minecraft:swords and the item's tool component. It never had
    // anything to do with whether a cavalryman will fight with a spear: JobCavalry#getWeaponType returns
    // ModEquipmentTypes.spear directly and that entry matches on the item, not on any vanilla tag. An earlier
    // version of this comment claimed cavalry melee now depends on the spear being written into #minecraft:swords by
    // the datagen; that was wrong on both halves -- the datagen writes no such file, and nothing reads one.
    //
    // So the live consequence of leaving this disabled is only that a player swinging a mod spear does not get the
    // sword tool actions. Restoring it would mean tagging the item into #minecraft:swords and giving it a tool
    // component, which is a separate piece of work.
    // Original NeoForge implementation:
    //     public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility)
    //     { return ItemAbilities.DEFAULT_SWORD_ACTIONS.contains(itemAbility); }

    /** Gets the base damage of the spear.
     *
     * @return the base damage of the spear.
     **/
    public int getDamage()
    {
        return SPEAR_BASE_DAMAGE;
    }
}
