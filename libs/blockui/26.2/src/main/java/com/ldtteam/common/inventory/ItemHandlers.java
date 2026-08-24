package com.ldtteam.common.inventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Resolves an {@link IItemHandler} for an arbitrary object - the stand-in for NeoForge's
 * {@code Capabilities.ItemHandler} lookups.
 * <p>
 * <b>Reduced by design.</b> NeoForge could ask any block entity, entity or stack for an item-handler capability,
 * including sided views published by other mods. Fabric has no equivalent registry with those semantics, so this
 * resolves only what vanilla itself exposes: {@link Container} for blocks and entities, and
 * {@link DataComponents#CONTAINER} for stacks. An inventory that a third-party mod publishes only through its own
 * API is invisible here and comes back as null - that is a known gap, not a bug to be worked around at the call
 * site.
 */
public final class ItemHandlers
{
    private ItemHandlers()
    {
        // Intentionally left empty.
    }

    /**
     * Resolves a handler for a block entity, an entity, or anything else that might hold an inventory.
     * <p>
     * A player {@link Inventory} deliberately resolves to {@link PlayerMainInvWrapper} and not to a view of all
     * its slots: a whole-inventory view would expose the equipment slots and accept anything into them, because
     * {@link Inventory} does not implement {@code canPlaceItem}. Ask for the equipment explicitly if you want it.
     *
     * @param object the object to resolve, may be null
     * @return an item handler view, or null when the object holds no inventory this can see
     */
    @Nullable
    public static IItemHandler of(@Nullable final Object object)
    {
        if (object instanceof final IItemHandler itemHandler)
        {
            return itemHandler;
        }
        if (object instanceof final Inventory inventory)
        {
            return new PlayerMainInvWrapper(inventory);
        }
        if (object instanceof final Container container)
        {
            return new InvWrapper(container);
        }
        return null;
    }

    /**
     * Resolves the contents of a stack that carries an inventory of its own - a shulker box, and anything else
     * using {@link DataComponents#CONTAINER}.
     *
     * @param stack the stack to look inside
     * @return a read-only handler over the contents, or null when the stack carries none
     */
    @Nullable
    public static IItemHandler ofStack(@NotNull final ItemStack stack)
    {
        final ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null)
        {
            return null;
        }

        return new ReadOnlyItemHandler(contents.nonEmptyItemCopyStream().toList());
    }

    /**
     * Immutable handler over a fixed list of stacks. Insertion and extraction are no-ops: the list is a copy of a
     * data component, so writing to it would change nothing that anybody can observe, and pretending otherwise
     * would lose items.
     *
     * @param stacks the contents, already copied
     */
    private record ReadOnlyItemHandler(List<ItemStack> stacks) implements IItemHandler
    {
        @Override
        public int getSlots()
        {
            return stacks.size();
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(final int slot)
        {
            return slot < 0 || slot >= stacks.size() ? ItemStack.EMPTY : stacks.get(slot);
        }

        @Override
        @NotNull
        public ItemStack insertItem(final int slot, @NotNull final ItemStack stack, final boolean simulate)
        {
            return stack;
        }

        @Override
        @NotNull
        public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
        {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean isItemValid(final int slot, @NotNull final ItemStack stack)
        {
            return false;
        }
    }
}
