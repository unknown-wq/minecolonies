package com.ldtteam.common.inventory;

import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Covers which sub-handler a flat slot index lands on, including the empty sub-handler and the indices that belong
 * to none. As with {@link InvWrapperTest} only empty stacks appear: real ones need a bound item registry.
 */
class CombinedInvWrapperTest
{
    /**
     * A handler of a fixed size that answers nothing but remembers which slot it was asked about.
     */
    private static final class RecordingHandler implements IItemHandlerModifiable
    {
        private final int slots;
        private final List<Integer> asked = new ArrayList<>();

        private RecordingHandler(final int slots)
        {
            this.slots = slots;
        }

        @Override
        public int getSlots()
        {
            return slots;
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(final int slot)
        {
            asked.add(slot);
            return ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(final int slot, @NotNull final ItemStack stack)
        {
            asked.add(slot);
        }

        @Override
        @NotNull
        public ItemStack insertItem(final int slot, @NotNull final ItemStack stack, final boolean simulate)
        {
            asked.add(slot);
            return ItemStack.EMPTY;
        }

        @Override
        @NotNull
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
        {
            asked.add(slot);
            return ItemStack.EMPTY;
        }
    }

    @Test
    void addsUpTheSlotsOfEveryHandler()
    {
        assertEquals(7, new CombinedInvWrapper(new RecordingHandler(3), new RecordingHandler(4)).getSlots());
        assertEquals(0, new CombinedInvWrapper().getSlots());
    }

    @Test
    void mapsAFlatIndexOntoTheOwningHandler()
    {
        final RecordingHandler first = new RecordingHandler(3);
        final RecordingHandler second = new RecordingHandler(4);
        final CombinedInvWrapper combined = new CombinedInvWrapper(first, second);

        combined.getStackInSlot(0);
        combined.getStackInSlot(2);
        combined.getStackInSlot(3);
        combined.getStackInSlot(6);

        assertEquals(List.of(0, 2), first.asked);
        assertEquals(List.of(0, 3), second.asked);
    }

    @Test
    void anEmptyHandlerInTheMiddleShiftsNothing()
    {
        final RecordingHandler first = new RecordingHandler(2);
        final RecordingHandler empty = new RecordingHandler(0);
        final RecordingHandler last = new RecordingHandler(2);
        final CombinedInvWrapper combined = new CombinedInvWrapper(first, empty, last);

        combined.getStackInSlot(2);
        combined.getStackInSlot(3);

        assertEquals(List.of(0, 1), last.asked);
        assertEquals(List.of(), empty.asked);
    }

    @Test
    void anIndexBelongingToNoHandlerIsInert()
    {
        final RecordingHandler only = new RecordingHandler(2);
        final CombinedInvWrapper combined = new CombinedInvWrapper(only);

        assertSame(ItemStack.EMPTY, combined.getStackInSlot(2));
        assertSame(ItemStack.EMPTY, combined.getStackInSlot(-1));
        combined.setStackInSlot(2, ItemStack.EMPTY);
        assertEquals(0, combined.getSlotLimit(2));
        assertFalse(combined.isItemValid(2, ItemStack.EMPTY));

        assertEquals(List.of(), only.asked);
    }
}
