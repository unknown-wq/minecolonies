package com.ldtteam.common.inventory;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A handler with no slots that accepts nothing and yields nothing. The null object of this package: returning it
 * from a lookup that found no inventory keeps the caller free of null checks.
 */
public final class EmptyItemHandler implements IItemHandlerModifiable
{
    /**
     * The only instance there ever needs to be.
     */
    public static final EmptyItemHandler INSTANCE = new EmptyItemHandler();

    private EmptyItemHandler()
    {
        // Intentionally left empty.
    }

    @Override
    public int getSlots()
    {
        return 0;
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(final int slot)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public void setStackInSlot(final int slot, @NotNull final ItemStack stack)
    {
        // Intentionally left empty.
    }

    @Override
    @NotNull
    public ItemStack insertItem(final int slot, @NotNull final ItemStack stack, final boolean simulate)
    {
        return stack;
    }

    @Override
    @NotNull
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(final int slot)
    {
        return 0;
    }

    @Override
    public boolean isItemValid(final int slot, @NotNull final ItemStack stack)
    {
        return false;
    }
}
