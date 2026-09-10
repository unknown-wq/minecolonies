package com.ldtteam.structurize.compat.itemhandler;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Replacement for the NeoForge {@code Capabilities.ItemHandler} lookups.
 *
 * <p>Blocks and item stacks go through fabric-transfer-api-v1, which is Fabric's equivalent of the capability
 * system and the interface every Fabric mod publishes its inventories on: {@link ItemStorage#SIDED} for a block
 * (optionally per face, exactly like the NeoForge lookup) and {@link ItemStorage#ITEM} for a stack. Both cover
 * the vanilla cases too -- fabric-transfer-api-v1 registers fallbacks for every {@link Container} block entity,
 * for {@link net.minecraft.world.WorldlyContainerHolder} blocks, and for the shulker-box and bundle stack
 * components -- so nothing that used to resolve stops resolving, and a modded inventory now resolves as well.
 * The results are adapted back to the slot-addressed {@link IItemHandler} by {@link StorageItemHandler}.</p>
 *
 * <p>TODO(port-26.3): DEGRADED, entities only. NeoForge also had {@code Capabilities.ItemHandler.ENTITY}, and
 * fabric-transfer-api-v1 publishes no entity-side item lookup at all -- {@link ItemStorage} declares
 * {@code SIDED} and {@code ITEM} and nothing else -- so {@link #of(Object)} still resolves an entity only when
 * it is a vanilla {@link Container} (minecarts with chests, chested horses). Consequence: "required items" for
 * a scanned entity whose inventory is published purely by another mod come out empty. Vanilla entity
 * inventories, and every block and stack inventory including modded ones, are complete.</p>
 */
public final class ItemHandlers
{
    private ItemHandlers()
    {
    }

    /**
     * Resolves the inventory of a block, the way {@code Capabilities.ItemHandler.BLOCK} used to.
     *
     * @param level       the level the block lives in; may be a fake level around a single loaded block entity.
     * @param pos         the block position.
     * @param state       the block state, passed explicitly so the level is never queried for it.
     * @param blockEntity the block entity, passed explicitly for the same reason; may be null.
     * @param side        the face to ask for, or null for the unsided view.
     * @return an item handler view, or null when nothing publishes an inventory there.
     */
    @Nullable
    public static IItemHandler of(final Level level,
        final BlockPos pos,
        final BlockState state,
        final @Nullable BlockEntity blockEntity,
        final @Nullable Direction side)
    {
        final Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, state, blockEntity, side);
        return storage == null ? null : new StorageItemHandler(storage);
    }

    /**
     * Resolves an object that carries its own inventory, with no position to look up.
     *
     * <p>This is the entity path, and the block path for a block entity that is not attached to a level. See the
     * class javadoc for what an entity can and cannot expose here.</p>
     *
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
            return new StorageItemHandler(ContainerStorage.of(container, null));
        }
        return null;
    }

    /**
     * @param stack a stack that may carry contents (shulker box, bundle, modded stack-held inventories).
     * @return a handler over the stack's contents, or null.
     */
    @Nullable
    public static IItemHandler ofStack(final ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return null;
        }

        // withConstant: the stack is being inspected, not carried in a slot that could be exchanged, so the
        // context deliberately refuses modification. Reading the contents is all Structurize does with it.
        final Storage<ItemVariant> storage = ItemStorage.ITEM.find(stack, ContainerItemContext.withConstant(stack));
        if (storage != null)
        {
            return new StorageItemHandler(storage);
        }

        // Fallback for a stack that carries vanilla container contents without registering an ItemStorage
        // provider for them. Nothing in vanilla lands here, but a modded item might.
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
