package com.minecolonies.core.util;

import com.minecolonies.api.inventory.api.IItemHandlerModifiable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Replacement for {@code net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper}: exposes only the 36 main
 * inventory slots of a player, excluding armour and offhand.
 * <p>
 * This cannot be {@code new InvWrapper(player.getInventory())}: in 26.2 {@link Inventory#getContainerSize()}
 * counts the equipment slots too (see {@code /opt/mc-src/.../Inventory.java:408}) and
 * {@link Inventory#canPlaceItem} is not overridden, so a plain wrapper would happily stuff a milk bottle into
 * the helmet slot.
 */
public class PlayerMainInvWrapper implements IItemHandlerModifiable
{
    /**
     * Number of main inventory slots -- 36 in 26.2, the size of {@code Inventory#items}.
     */
    private static final int MAIN_SIZE = 36;

    private final Inventory inv;

    /**
     * @param inv the player inventory to wrap.
     */
    public PlayerMainInvWrapper(final Inventory inv)
    {
        this.inv = inv;
    }

    @Override
    public int getSlots()
    {
        return Math.min(MAIN_SIZE, this.inv.getContainerSize());
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(final int slot)
    {
        return this.inv.getItem(slot);
    }

    @Override
    public void setStackInSlot(final int slot, @NotNull final ItemStack stack)
    {
        this.inv.setItem(slot, stack);
        this.inv.setChanged();
    }

    @Override
    @NotNull
    public ItemStack insertItem(final int slot, @NotNull final ItemStack stack, final boolean simulate)
    {
        if (stack.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        final ItemStack existing = this.inv.getItem(slot);
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        if (!existing.isEmpty())
        {
            if (!ItemStack.isSameItemSameComponents(stack, existing))
            {
                return stack;
            }
            limit -= existing.getCount();
        }

        if (limit <= 0)
        {
            return stack;
        }

        final boolean reachedLimit = stack.getCount() > limit;
        if (!simulate)
        {
            if (existing.isEmpty())
            {
                this.inv.setItem(slot, reachedLimit ? stack.copyWithCount(limit) : stack);
            }
            else
            {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }
            this.inv.setChanged();
        }

        return reachedLimit ? stack.copyWithCount(stack.getCount() - limit) : ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
    {
        if (amount <= 0)
        {
            return ItemStack.EMPTY;
        }

        final ItemStack existing = this.inv.getItem(slot);
        if (existing.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        final int toExtract = Math.min(amount, existing.getMaxStackSize());
        if (existing.getCount() <= toExtract)
        {
            if (!simulate)
            {
                this.inv.setItem(slot, ItemStack.EMPTY);
                this.inv.setChanged();
            }
            return existing;
        }

        if (!simulate)
        {
            this.inv.setItem(slot, existing.copyWithCount(existing.getCount() - toExtract));
            this.inv.setChanged();
        }
        return existing.copyWithCount(toExtract);
    }

    @Override
    public int getSlotLimit(final int slot)
    {
        return this.inv.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(final int slot, @NotNull final ItemStack stack)
    {
        return true;
    }
}
