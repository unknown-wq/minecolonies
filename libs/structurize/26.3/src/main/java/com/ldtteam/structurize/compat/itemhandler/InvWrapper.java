package com.ldtteam.structurize.compat.itemhandler;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Fabric stand-in for NeoForge's {@code net.neoforged.neoforge.items.wrapper.InvWrapper}: exposes a vanilla
 * {@link Container} as an {@link IItemHandler}.
 */
public class InvWrapper implements IItemHandler
{
    private final Container container;

    /**
     * @param container the wrapped container.
     */
    public InvWrapper(final Container container)
    {
        this.container = container;
    }

    /**
     * @return the wrapped container.
     */
    public Container getContainer()
    {
        return container;
    }

    @Override
    public int getSlots()
    {
        return container.getContainerSize();
    }

    @Override
    public ItemStack getStackInSlot(final int slot)
    {
        return container.getItem(slot);
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate)
    {
        if (stack.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        if (!container.canPlaceItem(slot, stack))
        {
            return stack;
        }

        final ItemStack existing = container.getItem(slot);
        final int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());

        if (!existing.isEmpty())
        {
            if (!ItemStack.isSameItemSameComponents(stack, existing))
            {
                return stack;
            }
            final int space = limit - existing.getCount();
            if (space <= 0)
            {
                return stack;
            }
            final int inserted = Math.min(space, stack.getCount());
            if (!simulate)
            {
                final ItemStack copy = existing.copy();
                copy.grow(inserted);
                container.setItem(slot, copy);
                container.setChanged();
            }
            return stack.getCount() <= inserted ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
        }

        final int inserted = Math.min(limit, stack.getCount());
        if (!simulate)
        {
            container.setItem(slot, stack.copyWithCount(inserted));
            container.setChanged();
        }
        return stack.getCount() <= inserted ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
    {
        if (amount <= 0)
        {
            return ItemStack.EMPTY;
        }
        final ItemStack existing = container.getItem(slot);
        if (existing.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        final int extracted = Math.min(amount, existing.getCount());
        if (simulate)
        {
            return existing.copyWithCount(extracted);
        }
        return container.removeItem(slot, extracted);
    }

    @Override
    public int getSlotLimit(final int slot)
    {
        return container.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack)
    {
        return container.canPlaceItem(slot, stack);
    }
}
