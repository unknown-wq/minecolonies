package com.ldtteam.common.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Exposes a vanilla {@link Container} - or a contiguous slot range of one - as an {@link IItemHandlerModifiable}.
 * Same role as NeoForge's {@code net.neoforged.neoforge.items.wrapper.InvWrapper} and its {@code RangedWrapper}.
 * <p>
 * <b>Do not wrap a player {@link net.minecraft.world.entity.player.Inventory} with the whole-container
 * constructor.</b> Its {@code getContainerSize()} counts the equipment slots too, and it does not override
 * {@link Container#canPlaceItem}, which defaults to true - so a whole-container view would let anything be
 * inserted into the armour slots and nothing would report an error. That is what
 * {@link PlayerMainInvWrapper} and the range constructor are for. Everything a whole-container view protects
 * against, it protects against because the container itself implements {@code canPlaceItem}; a container that
 * does not implement it needs a range.
 * <p>
 * Slot indices are relative to the range. Indices outside it read as {@link ItemStack#EMPTY} and are inert on
 * write, exactly like {@link EmptyItemHandler} - out of range does nothing rather than reaching a slot the caller
 * was never given.
 */
public class InvWrapper implements IItemHandlerModifiable
{
    /**
     * Slot count meaning "however many the container currently has", so that a whole-container view follows a
     * container which changes size.
     */
    private static final int WHOLE_CONTAINER = -1;

    private final Container container;
    private final int firstSlot;
    private final int slotCount;

    /**
     * Wraps every slot of a container. Only correct when the container implements {@link Container#canPlaceItem}
     * for the slots that need protecting - see the class javadoc.
     *
     * @param container the container to wrap
     */
    public InvWrapper(@NotNull final Container container)
    {
        this.container = container;
        this.firstSlot = 0;
        this.slotCount = WHOLE_CONTAINER;
    }

    /**
     * Wraps a contiguous range of a container's slots, hiding everything outside it.
     *
     * @param container the container to wrap
     * @param firstSlot the container slot that becomes slot 0 of this view
     * @param slotCount how many slots the view exposes
     */
    public InvWrapper(@NotNull final Container container, final int firstSlot, final int slotCount)
    {
        if (firstSlot < 0 || slotCount < 0)
        {
            throw new IllegalArgumentException("Slot range [" + firstSlot + ", " + (firstSlot + slotCount) + ") is not a range");
        }

        this.container = container;
        this.firstSlot = firstSlot;
        this.slotCount = slotCount;
    }

    /**
     * @return the wrapped container, all of its slots, including any this view hides
     */
    @NotNull
    public Container getContainer()
    {
        return container;
    }

    /**
     * @return the container slot this view's slot 0 maps to
     */
    public int getFirstSlot()
    {
        return firstSlot;
    }

    /**
     * @param slot a slot index of this view
     * @return the matching container slot, or -1 when the index is outside the view
     */
    protected int containerSlot(final int slot)
    {
        return slot < 0 || slot >= getSlots() ? -1 : firstSlot + slot;
    }

    @Override
    public int getSlots()
    {
        final int available = container.getContainerSize() - firstSlot;
        if (available <= 0)
        {
            return 0;
        }

        return slotCount == WHOLE_CONTAINER ? available : Math.min(slotCount, available);
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(final int slot)
    {
        final int target = containerSlot(slot);
        return target < 0 ? ItemStack.EMPTY : container.getItem(target);
    }

    @Override
    public void setStackInSlot(final int slot, @NotNull final ItemStack stack)
    {
        final int target = containerSlot(slot);
        if (target < 0)
        {
            return;
        }

        container.setItem(target, stack);
        container.setChanged();
    }

    @Override
    @NotNull
    public ItemStack insertItem(final int slot, @NotNull final ItemStack stack, final boolean simulate)
    {
        final int target = containerSlot(slot);
        if (target < 0 || stack.isEmpty() || !container.canPlaceItem(target, stack))
        {
            return stack;
        }

        final ItemStack existing = container.getItem(target);
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
                container.setItem(target, reachedLimit ? stack.copyWithCount(limit) : stack);
            }
            else
            {
                container.setItem(target, existing.copyWithCount(existing.getCount() + (reachedLimit ? limit : stack.getCount())));
            }
            container.setChanged();
        }

        return reachedLimit ? stack.copyWithCount(stack.getCount() - limit) : ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
    {
        final int target = containerSlot(slot);
        if (target < 0 || amount <= 0)
        {
            return ItemStack.EMPTY;
        }

        final ItemStack existing = container.getItem(target);
        if (existing.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        if (simulate)
        {
            return existing.copyWithCount(Math.min(existing.getCount(), amount));
        }

        final ItemStack removed = container.removeItem(target, amount);
        container.setChanged();
        return removed;
    }

    @Override
    public int getSlotLimit(final int slot)
    {
        return containerSlot(slot) < 0 ? 0 : container.getMaxStackSize();
    }

    @Override
    public boolean isItemValid(final int slot, @NotNull final ItemStack stack)
    {
        final int target = containerSlot(slot);
        return target >= 0 && container.canPlaceItem(target, stack);
    }
}
