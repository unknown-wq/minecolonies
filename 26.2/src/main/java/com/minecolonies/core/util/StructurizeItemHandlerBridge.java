package com.minecolonies.core.util;

import com.minecolonies.api.inventory.api.IItemHandler;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapter between the two identical-but-unrelated {@code IItemHandler} interfaces that exist after the 26.2 port.
 * <p>
 * NeoForge's {@code net.neoforged.neoforge.items.IItemHandler} is gone, and both projects grew their own copy of
 * it: MineColonies has {@link com.minecolonies.api.inventory.api.IItemHandler} (vendored by agent A) and
 * Structurize has {@code com.ldtteam.structurize.compat.itemhandler.IItemHandler}. The two declare exactly the
 * same six methods, so an instance of either can stand in for the other -- but only through an adapter, because
 * Java has no structural typing.
 * <p>
 * {@code IStructureHandler#getInventory()} is declared in terms of Structurize's copy, so
 * {@code BuildingStructureHandler} hands the citizen inventory across with {@link #toStructurize}, and code that
 * consumes it as a MineColonies handler converts back with {@link #fromStructurize}.
 */
public final class StructurizeItemHandlerBridge
{
    private StructurizeItemHandlerBridge()
    {
        throw new IllegalStateException("Tried to initialize: StructurizeItemHandlerBridge but this is a Utility class.");
    }

    /**
     * Wrap a MineColonies handler so Structurize can use it.
     *
     * @param handler the handler, may be null.
     * @return the wrapped handler, or null if the input was null.
     */
    @Nullable
    public static com.ldtteam.structurize.compat.itemhandler.IItemHandler toStructurize(@Nullable final IItemHandler handler)
    {
        if (handler == null)
        {
            return null;
        }
        if (handler instanceof StructurizeToMineColonies unwrapped)
        {
            return unwrapped.delegate;
        }
        return new MineColoniesToStructurize(handler);
    }

    /**
     * Wrap a Structurize handler so MineColonies code can use it.
     *
     * @param handler the handler, may be null.
     * @return the wrapped handler, or null if the input was null.
     */
    @Nullable
    public static IItemHandler fromStructurize(@Nullable final com.ldtteam.structurize.compat.itemhandler.IItemHandler handler)
    {
        if (handler == null)
        {
            return null;
        }
        if (handler instanceof MineColoniesToStructurize unwrapped)
        {
            return unwrapped.delegate;
        }
        return new StructurizeToMineColonies(handler);
    }

    /**
     * MineColonies handler seen as a Structurize one.
     */
    private record MineColoniesToStructurize(@NotNull IItemHandler delegate)
      implements com.ldtteam.structurize.compat.itemhandler.IItemHandler
    {
        @Override
        public int getSlots()
        {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(final int slot)
        {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate)
        {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
        {
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(final int slot)
        {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(final int slot, final ItemStack stack)
        {
            return delegate.isItemValid(slot, stack);
        }
    }

    /**
     * Structurize handler seen as a MineColonies one.
     */
    private record StructurizeToMineColonies(@NotNull com.ldtteam.structurize.compat.itemhandler.IItemHandler delegate)
      implements IItemHandler
    {
        @Override
        public int getSlots()
        {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(final int slot)
        {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(final int slot, @NotNull final ItemStack stack, final boolean simulate)
        {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
        {
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(final int slot)
        {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(final int slot, @NotNull final ItemStack stack)
        {
            return delegate.isItemValid(slot, stack);
        }
    }
}
