package com.minecolonies.api.inventory.api;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric 26.2 replacement for {@code net.neoforged.neoforge.items.IItemHandlerModifiable}.
 *
 * @see IItemHandler
 */
public interface IItemHandlerModifiable extends IItemHandler
{
    /**
     * Overwrite a slot outright, bypassing the insert rules.
     *
     * @param slot  the slot index.
     * @param stack the stack to put there.
     */
    void setStackInSlot(final int slot, @NotNull final ItemStack stack);
}
