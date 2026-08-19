package com.minecolonies.api.inventory.api;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric 26.2 replacement for {@code net.neoforged.neoforge.items.wrapper.InvWrapper}: exposes a vanilla
 * {@link Container} (typically {@code player.getInventory()}) as an {@link IItemHandlerModifiable}.
 */
public class InvWrapper implements IItemHandlerModifiable
{
    private final Container inv;

    public InvWrapper(final Container inv)
    {
        this.inv = inv;
    }

    /**
     * @return the wrapped container.
     */
    public Container getInv()
    {
        return this.inv;
    }

    @Override
    public int getSlots()
    {
        return this.inv.getContainerSize();
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
        if (!this.inv.canPlaceItem(slot, stack))
        {
            return stack;
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
                this.inv.setItem(slot, existing);
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

        if (simulate)
        {
            return existing.copyWithCount(Math.min(existing.getCount(), amount));
        }

        final ItemStack removed = this.inv.removeItem(slot, amount);
        this.inv.setChanged();
        return removed;
    }

    @Override
    public int getSlotLimit(final int slot)
    {
        return this.inv.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(final int slot, @NotNull final ItemStack stack)
    {
        return this.inv.canPlaceItem(slot, stack);
    }
}
