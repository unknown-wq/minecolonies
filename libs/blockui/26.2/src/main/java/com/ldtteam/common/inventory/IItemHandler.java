package com.ldtteam.common.inventory;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A view of an inventory as a fixed number of slots, with insertion and extraction that can be simulated.
 * <p>
 * Same shape as NeoForge's {@code net.neoforged.neoforge.items.IItemHandler}, kept because
 * {@link net.minecraft.world.Container} offers neither slot-limited insertion nor a simulate flag, and the ported
 * call sites are built on both. The bridge from vanilla is {@link InvWrapper}; {@link ItemHandlers} resolves a
 * handler for an arbitrary object.
 *
 * @see IItemHandlerModifiable for the variant that can also overwrite a slot outright
 */
public interface IItemHandler
{
    /**
     * @return the number of slots this handler exposes
     */
    int getSlots();

    /**
     * @param slot the slot index
     * @return the stack in that slot, {@link ItemStack#EMPTY} if none - never mutate the returned stack
     */
    @NotNull
    ItemStack getStackInSlot(final int slot);

    /**
     * Inserts into a single slot.
     *
     * @param slot     the slot index
     * @param stack    the stack to insert, never mutated
     * @param simulate true to only report what would happen, changing nothing
     * @return the remainder that could not be inserted, {@link ItemStack#EMPTY} when all of it fit
     */
    @NotNull
    ItemStack insertItem(final int slot, @NotNull final ItemStack stack, final boolean simulate);

    /**
     * Extracts from a single slot.
     *
     * @param slot     the slot index
     * @param amount   the maximum number of items to pull
     * @param simulate true to only report what would happen, changing nothing
     * @return the extracted stack, {@link ItemStack#EMPTY} when nothing could be extracted
     */
    @NotNull
    ItemStack extractItem(final int slot, final int amount, final boolean simulate);

    /**
     * @param slot the slot index
     * @return the maximum stack size that slot accepts, ignoring what is in it
     */
    default int getSlotLimit(final int slot)
    {
        return Item.DEFAULT_MAX_STACK_SIZE;
    }

    /**
     * Whether the slot would accept the stack at all, ignoring how full it currently is. Answering true is no
     * promise that {@link #insertItem} accepts the whole stack.
     *
     * @param slot  the slot index
     * @param stack the stack to test
     * @return true when that slot accepts this kind of item
     */
    default boolean isItemValid(final int slot, @NotNull final ItemStack stack)
    {
        return true;
    }
}
