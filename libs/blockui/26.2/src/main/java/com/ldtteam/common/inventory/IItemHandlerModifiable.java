package com.ldtteam.common.inventory;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link IItemHandler} whose slots can also be overwritten outright, bypassing the insertion rules.
 * <p>
 * Same shape as NeoForge's {@code net.neoforged.neoforge.items.IItemHandlerModifiable}.
 */
public interface IItemHandlerModifiable extends IItemHandler
{
    /**
     * Puts a stack into a slot, replacing whatever was there. No stacking, no limit check, no validity check -
     * use {@link #insertItem} unless you own the inventory.
     *
     * @param slot  the slot index
     * @param stack the stack to put there
     */
    void setStackInSlot(final int slot, @NotNull final ItemStack stack);
}
