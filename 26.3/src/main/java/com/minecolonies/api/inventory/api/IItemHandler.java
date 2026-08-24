package com.minecolonies.api.inventory.api;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric 26.2 replacement for {@code net.neoforged.neoforge.items.IItemHandler}.
 * <p>
 * Contract C4 says capabilities do not travel to Fabric, and they do not: nothing here is exposed to other mods and
 * there is no capability lookup left in the codebase. What did have to survive is the <em>shape</em> of the interface —
 * MineColonies routes every inventory operation it has through {@code IItemHandler} (roughly 280 call sites of
 * {@code getSlots}/{@code getStackInSlot}/{@code insertItem}/{@code extractItem} plus the whole of
 * {@code InventoryUtils}), and {@link net.minecraft.world.Container} has neither slot-limited insertion nor the
 * simulate flag those call sites depend on. So the interface is reproduced here, unchanged, as plain mod code.
 * <p>
 * The bridge from vanilla is {@link InvWrapper}, which adapts any {@link net.minecraft.world.Container}.
 */
public interface IItemHandler
{
    /**
     * @return the number of slots this handler exposes.
     */
    int getSlots();

    /**
     * @param slot the slot index.
     * @return the stack in that slot; {@link ItemStack#EMPTY} if none. Never mutate the returned stack.
     */
    @NotNull
    ItemStack getStackInSlot(final int slot);

    /**
     * Insert into a slot.
     *
     * @param slot     the slot index.
     * @param stack    the stack to insert; never mutated.
     * @param simulate if true nothing is actually changed.
     * @return the remainder that could not be inserted.
     */
    @NotNull
    ItemStack insertItem(final int slot, @NotNull final ItemStack stack, final boolean simulate);

    /**
     * Extract from a slot.
     *
     * @param slot     the slot index.
     * @param amount   the maximum amount to extract.
     * @param simulate if true nothing is actually changed.
     * @return the extracted stack, empty if nothing could be extracted.
     */
    @NotNull
    ItemStack extractItem(final int slot, final int amount, final boolean simulate);

    /**
     * @param slot the slot index.
     * @return the maximum stack size that slot accepts.
     */
    int getSlotLimit(final int slot);

    /**
     * @param slot  the slot index.
     * @param stack the stack to test.
     * @return whether that slot would accept the stack at all.
     */
    boolean isItemValid(final int slot, @NotNull final ItemStack stack);
}
