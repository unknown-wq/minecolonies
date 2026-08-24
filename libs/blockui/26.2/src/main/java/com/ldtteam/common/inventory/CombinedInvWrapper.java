package com.ldtteam.common.inventory;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Presents several {@link IItemHandlerModifiable}s as one flat slot range, in the order they were given. Same role
 * as NeoForge's {@code net.neoforged.neoforge.items.wrapper.CombinedInvWrapper}.
 * <p>
 * Every operation is forwarded to whichever sub-handler owns the slot, so nothing here spans handlers: inserting a
 * stack fills one slot of one handler and returns the rest. A flat index outside every sub-handler resolves to
 * {@link EmptyItemHandler}, which reads empty and accepts nothing.
 * <p>
 * The sub-handler slot counts are read once, at construction. A sub-handler that resizes afterwards makes the
 * mapping wrong - build a new wrapper instead.
 */
public class CombinedInvWrapper implements IItemHandlerModifiable
{
    /**
     * The wrapped handlers, in slot order.
     */
    protected final IItemHandlerModifiable[] handlers;

    /**
     * Exclusive flat end index of each handler, so {@code baseIndex[i]} is where handler {@code i} stops.
     */
    protected final int[] baseIndex;

    /**
     * Total number of slots across all handlers.
     */
    protected final int slotCount;

    /**
     * @param handlers the handlers to present as one, in order
     */
    public CombinedInvWrapper(@NotNull final IItemHandlerModifiable... handlers)
    {
        this.handlers = handlers;
        this.baseIndex = new int[handlers.length];

        int index = 0;
        for (int i = 0; i < handlers.length; i++)
        {
            index += handlers[i].getSlots();
            this.baseIndex[i] = index;
        }
        this.slotCount = index;
    }

    /**
     * @param slot a flat slot index
     * @return the index of the owning sub-handler, or -1 when the slot belongs to none
     */
    protected int getIndexForSlot(final int slot)
    {
        if (slot < 0)
        {
            return -1;
        }

        for (int i = 0; i < this.baseIndex.length; i++)
        {
            if (slot < this.baseIndex[i])
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * @param index a sub-handler index
     * @return that sub-handler, or {@link EmptyItemHandler} when the index belongs to none
     */
    @NotNull
    protected IItemHandlerModifiable getHandlerFromIndex(final int index)
    {
        return index < 0 || index >= this.handlers.length ? EmptyItemHandler.INSTANCE : this.handlers[index];
    }

    /**
     * @param slot  a flat slot index
     * @param index the owning sub-handler index
     * @return the slot index within that sub-handler
     */
    protected int getSlotFromIndex(final int slot, final int index)
    {
        return index <= 0 || index >= this.baseIndex.length ? slot : slot - this.baseIndex[index - 1];
    }

    @Override
    public int getSlots()
    {
        return this.slotCount;
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(final int slot)
    {
        final int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).getStackInSlot(getSlotFromIndex(slot, index));
    }

    @Override
    public void setStackInSlot(final int slot, @NotNull final ItemStack stack)
    {
        final int index = getIndexForSlot(slot);
        getHandlerFromIndex(index).setStackInSlot(getSlotFromIndex(slot, index), stack);
    }

    @Override
    @NotNull
    public ItemStack insertItem(final int slot, @NotNull final ItemStack stack, final boolean simulate)
    {
        final int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).insertItem(getSlotFromIndex(slot, index), stack, simulate);
    }

    @Override
    @NotNull
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
    {
        final int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).extractItem(getSlotFromIndex(slot, index), amount, simulate);
    }

    @Override
    public int getSlotLimit(final int slot)
    {
        final int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).getSlotLimit(getSlotFromIndex(slot, index));
    }

    @Override
    public boolean isItemValid(final int slot, @NotNull final ItemStack stack)
    {
        final int index = getIndexForSlot(slot);
        return getHandlerFromIndex(index).isItemValid(getSlotFromIndex(slot, index), stack);
    }
}
