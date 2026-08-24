package com.ldtteam.structurize.compat.itemhandler;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Replacement for the NeoForge {@code Capabilities.ItemHandler} lookups.
 *
 * <p>TODO(port-26.2): DEGRADED — NeoForge could ask any block entity, entity or item stack for an
 * {@code IItemHandler} capability, including sided views published by other mods. Fabric's equivalent
 * (fabric-transfer-api-v1) has incompatible semantics, so this resolves only what vanilla itself exposes:
 * {@link Container} for blocks and entities and {@link DataComponents#CONTAINER} for stacks. Consequence:
 * "required items" for a scanned block whose inventory is published purely as a modded capability come out
 * empty; all vanilla containers (chests, barrels, shulker boxes, hoppers, furnaces, item frames …) are
 * complete.</p>
 */
public final class ItemHandlers
{
    private ItemHandlers()
    {
    }

    /**
     * @param object a block entity or entity.
     * @return an item handler view, or null when the object holds no vanilla inventory.
     */
    @Nullable
    public static IItemHandler of(final @Nullable Object object)
    {
        if (object instanceof final IItemHandler itemHandler)
        {
            return itemHandler;
        }
        if (object instanceof final Container container)
        {
            return new InvWrapper(container);
        }
        return null;
    }

    /**
     * @param stack a stack that may carry contents (shulker box, bundle-like components).
     * @return a read-only item handler over {@link DataComponents#CONTAINER}, or null.
     */
    @Nullable
    public static IItemHandler ofStack(final ItemStack stack)
    {
        final ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null)
        {
            return null;
        }
        return new ReadOnlyItemHandler(contents.nonEmptyItemCopyStream().toList());
    }

    /**
     * Immutable handler over a fixed list, used for stack contents.
     */
    private record ReadOnlyItemHandler(List<ItemStack> stacks) implements IItemHandler
    {
        @Override
        public int getSlots()
        {
            return stacks.size();
        }

        @Override
        public ItemStack getStackInSlot(final int slot)
        {
            return slot < 0 || slot >= stacks.size() ? ItemStack.EMPTY : stacks.get(slot);
        }

        @Override
        public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate)
        {
            return stack;
        }

        @Override
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
        {
            return ItemStack.EMPTY;
        }
    }
}
