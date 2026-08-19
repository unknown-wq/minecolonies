package com.minecolonies.api.inventory.api;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric 26.2 replacement for {@code net.neoforged.neoforge.items.SlotItemHandler}: a vanilla {@link Slot} backed by
 * an {@link IItemHandler} instead of a {@link net.minecraft.world.Container}.
 * <p>
 * As in NeoForge the {@code Slot} superclass is handed a throwaway one-slot {@link SimpleContainer} which is never
 * read: every accessor below is overridden to go to the handler.
 */
public class SlotItemHandler extends Slot
{
    private static final SimpleContainer EMPTY_INVENTORY = new SimpleContainer(0);

    private final IItemHandler itemHandler;
    private final int          index;

    public SlotItemHandler(final IItemHandler itemHandler, final int index, final int xPosition, final int yPosition)
    {
        super(EMPTY_INVENTORY, index, xPosition, yPosition);
        this.itemHandler = itemHandler;
        this.index = index;
    }

    /**
     * @return the backing handler.
     */
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
        // NOOP: the handler owns the stacks, there is no container-side bookkeeping to do.
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
        // NOOP: the throwaway backing container has nothing to notify.
    }
}
