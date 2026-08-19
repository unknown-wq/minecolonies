package com.minecolonies.api.inventory.api;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric 26.2 replacement for {@code net.neoforged.neoforge.items.wrapper.CombinedInvWrapper}: presents several
 * {@link IItemHandlerModifiable}s as one flat slot range, in order.
 */
public class CombinedInvWrapper implements IItemHandlerModifiable
{
    protected final IItemHandlerModifiable[] itemHandler;
    protected final int[]                    baseIndex;
    protected final int                      slotCount;

    public CombinedInvWrapper(final IItemHandlerModifiable... itemHandler)
    {
        this.itemHandler = itemHandler;
        this.baseIndex = new int[itemHandler.length];
        int index = 0;
        for (int i = 0; i < itemHandler.length; i++)
        {
            index += itemHandler[i].getSlots();
            this.baseIndex[i] = index;
        }
        this.slotCount = index;
    }

    /**
     * @param slot a flat slot index.
     * @return the index of the owning sub-handler.
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
     * @param index sub-handler index.
     * @return that sub-handler.
     */
    protected IItemHandlerModifiable getHandlerFromIndex(final int index)
    {
        if (index < 0 || index >= this.itemHandler.length)
        {
            return EmptyHandler.INSTANCE;
        }
        return this.itemHandler[index];
    }

    /**
     * @param slot  a flat slot index.
     * @param index the owning sub-handler index.
     * @return the slot index within that sub-handler.
     */
    protected int getSlotFromIndex(final int slot, final int index)
    {
        if (index <= 0 || index >= this.baseIndex.length)
        {
            return slot;
        }
        return slot - this.baseIndex[index - 1];
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

    /**
     * Null object used when a flat slot index falls outside every sub-handler.
     */
    public static final class EmptyHandler implements IItemHandlerModifiable
    {
        public static final EmptyHandler INSTANCE = new EmptyHandler();

        private EmptyHandler()
        {
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
            // NOOP.
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
}
