package com.ldtteam.structurize.compat.itemhandler;

import net.minecraft.world.item.ItemStack;

/**
 * Fabric stand-in for NeoForge's {@code net.neoforged.neoforge.items.IItemHandler}.
 *
 * <p>NeoForge's capability system does not exist on Fabric, and fabric-transfer-api-v1 exposes a very
 * different {@code Storage&lt;ItemVariant&gt;} model with transactions. Structurize only ever uses four of the
 * eleven {@code IItemHandler} methods, all of them slot-based, so the port keeps this thin interface: it
 * leaves {@code util/InventoryUtils}, {@code placement/structure/IStructureHandler} and
 * {@code api/ItemStackUtils} untouched apart from their import line.</p>
 */
public interface IItemHandler
{
    /**
     * @return the number of slots.
     */
    int getSlots();

    /**
     * @param slot slot index.
     * @return the stack in that slot; do not mutate.
     */
    ItemStack getStackInSlot(int slot);

    /**
     * @param slot     slot index.
     * @param stack    the stack to insert.
     * @param simulate true to only test.
     * @return the remainder that could not be inserted.
     */
    ItemStack insertItem(int slot, ItemStack stack, boolean simulate);

    /**
     * @param slot     slot index.
     * @param amount   how many items to pull.
     * @param simulate true to only test.
     * @return the extracted stack.
     */
    ItemStack extractItem(int slot, int amount, boolean simulate);

    /**
     * @param slot slot index.
     * @return the maximum stack size of that slot.
     */
    default int getSlotLimit(final int slot)
    {
        return 64;
    }

    /**
     * @param slot  slot index.
     * @param stack the candidate stack.
     * @return true when the slot accepts that stack.
     */
    default boolean isItemValid(final int slot, final ItemStack stack)
    {
        return true;
    }
}
