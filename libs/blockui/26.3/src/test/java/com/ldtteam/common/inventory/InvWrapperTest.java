package com.ldtteam.common.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the slot mapping of {@link InvWrapper} - which container slot a view slot reaches, and which ones it must
 * never reach. That is the half of this wrapper that fails silently: a range that leaks past its end lands on the
 * player's armour slots, where nothing complains because {@link net.minecraft.world.Container#canPlaceItem}
 * defaults to true and {@link Inventory} does not override it.
 * <p>
 * Only empty stacks appear here. Anything else needs a bound {@code Items} registry, which does not exist outside
 * a running game - the same reason the config tests mint their values by hand. The stacking arithmetic of
 * {@code insertItem} is therefore not covered by these.
 */
class InvWrapperTest
{
    /**
     * A container that remembers which slots were asked for, so a test can assert that a slot was never touched
     * rather than only that the answer looked right. Written out by hand rather than derived from
     * {@link net.minecraft.world.SimpleContainer}, whose {@code setItem} asks the stack for its maximum size and
     * so needs a bootstrapped registry.
     */
    private static final class RecordingContainer implements Container
    {
        private final List<Integer> reads = new ArrayList<>();
        private final List<Integer> writes = new ArrayList<>();
        private final int size;

        private RecordingContainer(final int size)
        {
            this.size = size;
        }

        @Override
        public int getContainerSize()
        {
            return size;
        }

        @Override
        public boolean isEmpty()
        {
            return true;
        }

        @Override
        public ItemStack getItem(final int slot)
        {
            reads.add(slot);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(final int slot, final int count)
        {
            writes.add(slot);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(final int slot)
        {
            writes.add(slot);
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(final int slot, final ItemStack stack)
        {
            writes.add(slot);
        }

        @Override
        public void setChanged()
        {
            // Intentionally left empty.
        }

        @Override
        public boolean stillValid(final Player player)
        {
            return true;
        }

        @Override
        public void clearContent()
        {
            // Intentionally left empty.
        }
    }

    @Test
    void wholeContainerViewFollowsTheContainer()
    {
        assertEquals(41, new InvWrapper(new RecordingContainer(41)).getSlots());
    }

    @Test
    void aRangeHidesTheSlotsAboveIt()
    {
        final RecordingContainer container = new RecordingContainer(41);
        final InvWrapper wrapper = new InvWrapper(container, 0, 36);

        assertEquals(36, wrapper.getSlots());

        assertSame(ItemStack.EMPTY, wrapper.getStackInSlot(36));
        assertSame(ItemStack.EMPTY, wrapper.getStackInSlot(40));
        wrapper.setStackInSlot(36, ItemStack.EMPTY);
        assertSame(ItemStack.EMPTY, wrapper.extractItem(36, 1, false));
        assertSame(ItemStack.EMPTY, wrapper.insertItem(36, ItemStack.EMPTY, false));

        assertEquals(0, wrapper.getSlotLimit(36));
        assertFalse(wrapper.isItemValid(36, ItemStack.EMPTY));

        assertTrue(container.reads.isEmpty(), () -> "read hidden container slots " + container.reads);
        assertTrue(container.writes.isEmpty(), () -> "wrote hidden container slots " + container.writes);
    }

    @Test
    void aRangeIsOffsetByItsFirstSlot()
    {
        final RecordingContainer container = new RecordingContainer(41);
        final InvWrapper wrapper = new InvWrapper(container, 9, 27);

        assertEquals(27, wrapper.getSlots());
        assertEquals(9, wrapper.getFirstSlot());

        wrapper.getStackInSlot(0);
        wrapper.getStackInSlot(26);
        assertEquals(List.of(9, 35), container.reads);

        wrapper.setStackInSlot(1, ItemStack.EMPTY);
        assertEquals(List.of(10), container.writes);
    }

    @Test
    void aRangeNeverOutgrowsTheContainer()
    {
        assertEquals(5, new InvWrapper(new RecordingContainer(5), 0, 36).getSlots());
        assertEquals(2, new InvWrapper(new RecordingContainer(5), 3, 36).getSlots());
        assertEquals(0, new InvWrapper(new RecordingContainer(5), 10, 4).getSlots());
    }

    @Test
    void rejectsARangeThatIsNotOne()
    {
        final Container container = new RecordingContainer(9);

        assertThrows(IllegalArgumentException.class, () -> new InvWrapper(container, -1, 9));
        assertThrows(IllegalArgumentException.class, () -> new InvWrapper(container, 0, -9));
    }

    /**
     * {@link PlayerMainInvWrapper} is a range over a constant, so this pins the constant. It is inlined at compile
     * time, which is exactly the point: should vanilla ever change the main inventory size, this fails on the
     * first rebuild instead of the wrapper quietly reaching into the equipment slots.
     */
    @Test
    void thePlayerMainInventoryIsStillThirtySixSlots()
    {
        assertEquals(36, Inventory.INVENTORY_SIZE);
    }
}
