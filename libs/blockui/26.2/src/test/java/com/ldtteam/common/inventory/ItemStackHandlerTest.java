package com.ldtteam.common.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers sizing and bounds of {@link ItemStackHandler}. The contents themselves stay empty: real stacks need a
 * bound item registry, which does not exist outside a running game.
 */
class ItemStackHandlerTest
{
    @Test
    void defaultsToASingleSlot()
    {
        assertEquals(1, new ItemStackHandler().getSlots());
        assertEquals(9, new ItemStackHandler(9).getSlots());
        assertEquals(4, new ItemStackHandler(NonNullList.withSize(4, ItemStack.EMPTY)).getSlots());
    }

    @Test
    void resizingMovesTheBoundary()
    {
        final ItemStackHandler handler = new ItemStackHandler(4);

        handler.setSize(2);
        assertEquals(2, handler.getSlots());
        assertThrows(IndexOutOfBoundsException.class, () -> handler.getStackInSlot(2));

        handler.setSize(6);
        assertEquals(6, handler.getSlots());
        assertSame(ItemStack.EMPTY, handler.getStackInSlot(5));
    }

    @Test
    void refusesASlotItDoesNotHave()
    {
        final ItemStackHandler handler = new ItemStackHandler(2);

        assertThrows(IndexOutOfBoundsException.class, () -> handler.getStackInSlot(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> handler.getStackInSlot(2));
        assertThrows(IndexOutOfBoundsException.class, () -> handler.setStackInSlot(2, ItemStack.EMPTY));
        assertThrows(IndexOutOfBoundsException.class, () -> handler.extractItem(2, 1, true));
    }

    @Test
    void insertingNothingChangesNothing()
    {
        final ItemStackHandler handler = new ItemStackHandler(2);

        assertSame(ItemStack.EMPTY, handler.insertItem(0, ItemStack.EMPTY, false));
        assertSame(ItemStack.EMPTY, handler.extractItem(0, 0, false));
        assertSame(ItemStack.EMPTY, handler.getStackInSlot(0));
    }
}
