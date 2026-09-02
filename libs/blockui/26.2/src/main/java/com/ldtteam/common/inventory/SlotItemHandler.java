package com.ldtteam.common.inventory;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A vanilla menu {@link Slot} backed by an {@link IItemHandler} instead of a
 * {@link net.minecraft.world.Container}. Same role as NeoForge's
 * {@code net.neoforged.neoforge.items.SlotItemHandler}.
 * <p>
 * As in NeoForge, the {@link Slot} superclass is handed a throwaway empty container that is never read: every
 * accessor below is overridden to go to the handler instead. A handler that is not an
 * {@link IItemHandlerModifiable} still works, but {@link #set} silently does nothing on it - the same behaviour
 * NeoForge had.
 */
public class SlotItemHandler extends Slot
{
    private static final SimpleContainer EMPTY_INVENTORY = new SimpleContainer(0);

    private final IItemHandler itemHandler;
    private final int index;

    /**
     * @param itemHandler the handler this slot reads and writes
     * @param index       the handler slot this menu slot shows
     * @param xPosition   x position in the menu
     * @param yPosition   y position in the menu
     */
    public SlotItemHandler(@NotNull final IItemHandler itemHandler, final int index, final int xPosition, final int yPosition)
    {
        super(EMPTY_INVENTORY, index, xPosition, yPosition);
        this.itemHandler = itemHandler;
        this.index = index;
    }

    /**
     * @return the backing handler
     */
    @NotNull
    public IItemHandler getItemHandler()
    {
        return this.itemHandler;
    }

    @Override
    public boolean mayPlace(@NotNull final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return false;
        }
        return this.itemHandler.insertItem(this.index, stack, true).getCount() < stack.getCount();
    }

    @Override
    @NotNull
    public ItemStack getItem()
    {
        return this.itemHandler.getStackInSlot(this.index);
    }

    @Override
    public void set(@NotNull final ItemStack stack)
    {
        if (this.itemHandler instanceof final IItemHandlerModifiable modifiable)
        {
            modifiable.setStackInSlot(this.index, stack);
        }
        this.setChanged();
    }

    @Override
    public void setByPlayer(@NotNull final ItemStack stack, @NotNull final ItemStack previous)
    {
        this.set(stack);
    }

    @Override
    public void onQuickCraft(@NotNull final ItemStack picked, @NotNull final ItemStack original)
    {
        // Intentionally left empty: the handler owns the stacks, there is no container-side bookkeeping to do.
    }

    @Override
    public int getMaxStackSize()
    {
        return this.itemHandler.getSlotLimit(this.index);
    }

    @Override
    public int getMaxStackSize(@NotNull final ItemStack stack)
    {
        final ItemStack maxAdd = stack.copyWithCount(stack.getMaxStackSize());
        final ItemStack current = this.itemHandler.getStackInSlot(this.index);

        if (this.itemHandler instanceof final IItemHandlerModifiable modifiable)
        {
            // Emptying the slot first is how NeoForge asked "how much would fit if this slot were free"; the
            // simulated insert cannot answer that on its own, and the original stack goes straight back.
            modifiable.setStackInSlot(this.index, ItemStack.EMPTY);
            final ItemStack remainder = this.itemHandler.insertItem(this.index, maxAdd, true);
            modifiable.setStackInSlot(this.index, current);
            return maxAdd.getCount() - remainder.getCount();
        }

        final ItemStack remainder = this.itemHandler.insertItem(this.index, maxAdd, true);
        return current.getCount() + (maxAdd.getCount() - remainder.getCount());
    }

    @Override
    public boolean mayPickup(final Player player)
    {
        return !this.itemHandler.extractItem(this.index, 1, true).isEmpty();
    }

    @Override
    @NotNull
    public ItemStack remove(final int amount)
    {
        return this.itemHandler.extractItem(this.index, amount, false);
    }

    @Override
    public void setChanged()
    {
        // Intentionally left empty: the throwaway backing container has nothing to notify.
    }
}
