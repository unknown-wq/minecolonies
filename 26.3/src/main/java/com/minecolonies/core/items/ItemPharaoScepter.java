package com.minecolonies.core.items;

import com.minecolonies.api.items.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Predicate;

import static net.minecraft.SharedConstants.TICKS_PER_SECOND;

/**
 * Class handling the Pharao Scepter item.
 */
public class ItemPharaoScepter extends BowItem
{
    /**
     * Constructor method for the Chief Sword Item
     *
     * @param properties the properties.
     */
    public ItemPharaoScepter(final Properties properties)
    {
        super(properties.durability(384));
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull final Level worldIn, Player playerIn, @NotNull final InteractionHand handIn)
    {
        // 26.2/Fabric: NeoForge's EventHooks.onArrowNock (the cancellable ArrowNockEvent) has no counterpart;
        // what is lost is only other mods' ability to veto drawing the bow.
        playerIn.startUsingItem(handIn);
        return InteractionResult.CONSUME;
    }

    // 26.2: Item#releaseUsing returns whether the release was handled (was void in 1.21.1). NeoForge's
    // EventHooks.onArrowLoose (the cancellable ArrowLooseEvent) has no counterpart and is dropped.
    @Override
    public boolean releaseUsing(@NotNull final ItemStack stack, @NotNull final Level worldIn, LivingEntity entityLiving, int timeLeft)
    {
        if (entityLiving instanceof Player)
        {
            Player playerentity = (Player) entityLiving;
            int useDuration = this.getUseDuration(stack, entityLiving) - timeLeft;

            ItemStack itemstack = playerentity.getProjectile(stack);
            if (!itemstack.isEmpty())
            {
                float speed = getPowerForTime(useDuration);
                if (!((double) speed < 0.1))
                {
                    List<ItemStack> list = draw(stack, itemstack, entityLiving);
                    if (worldIn instanceof ServerLevel serverlevel && !list.isEmpty())
                    {
                        this.shoot(serverlevel, entityLiving, entityLiving.getUsedItemHand(), stack, list, speed * 3.0F, 1.0F, speed == 1.0F, null);
                    }

                    worldIn.playSound(null,
                      playerentity.getX(),
                      playerentity.getY(),
                      playerentity.getZ(),
                      SoundEvents.ARROW_SHOOT,
                      SoundSource.PLAYERS,
                      1.0F,
                      1.0F / (entityLiving.getRandom().nextFloat() * 0.4F + 1.2F) + speed * 0.5F);
                    playerentity.awardStat(Stats.ITEM_USED.get(this));
                    return true;
                }
            }
        }
        return false;
    }

    @NotNull
    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles()
    {
        return itemStack -> true;
    }

    // TODO(port-26.2): DISABLED -- NeoForge's ProjectileWeaponItem#customArrow(AbstractArrow, ItemStack,
    // ItemStack) hook, which let a bow swap the arrow entity it fires, has no counterpart in Fabric or vanilla
    // 26.2 (0 hits for customArrow in /opt/mc-src). The Pharao Scepter therefore fires the player's own arrows
    // instead of always converting them to burning fire arrows; the damage and the scepter's other behaviour
    // are unchanged.
    // Original NeoForge implementation:
    //     public AbstractArrow customArrow(arrow, projectileStack, weaponStack)
    //     {
    //         if (arrow.getOwner() == null) { return arrow; }
    //         AbstractArrow entity = ((ArrowItem) ModItems.firearrow).createArrow(
    //             arrow.level(), new ItemStack(ModItems.firearrow, 1), (LivingEntity) arrow.getOwner(), weaponStack);
    //         entity.pickup = AbstractArrow.Pickup.DISALLOWED;
    //         entity.setRemainingFireTicks(3 * TICKS_PER_SECOND);
    //         return entity;
    //     }
}
