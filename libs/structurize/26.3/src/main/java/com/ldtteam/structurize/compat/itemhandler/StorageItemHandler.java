package com.ldtteam.structurize.compat.itemhandler;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapts a fabric-transfer-api-v1 {@link Storage}&lt;{@link ItemVariant}&gt; to the slot-addressed
 * {@link IItemHandler} the rest of Structurize is written against.
 *
 * <p>The two models differ in two ways, and this class is where the difference is resolved:</p>
 * <ul>
 *   <li><b>Slots.</b> Most item storages -- every vanilla {@link net.minecraft.world.Container}, and the great
 *       majority of modded inventories -- are a {@link SlottedStorage}, and those are addressed slot for slot,
 *       live, with no copying. A storage that is not slotted has no slot indices to address at all, so its
 *       non-empty {@linkplain Storage#nonEmptyViews() views} are snapshotted once at construction and used as
 *       the slot list. The views themselves stay live, so the stacks read out of them are current; only the
 *       number and order of slots is fixed for the lifetime of this handler, which is what a slot-indexed
 *       reader needs anyway.</li>
 *   <li><b>Transactions.</b> {@link IItemHandler} has a {@code simulate} flag where the transfer API has a
 *       transaction. Every mutating call opens its own short transaction and commits it only when
 *       {@code simulate} is false; a simulated call lets try-with-resources abort it, which is exactly the
 *       "compute the result, change nothing" semantic the flag asks for. The transaction is nested when one is
 *       already open on this thread, so a caller that is itself inside a transaction is not broken.</li>
 * </ul>
 *
 * <p>Reads never open a transaction: {@link StorageView#getResource()} and {@link StorageView#getAmount()} are
 * defined outside one, and Structurize's own uses of a looked-up inventory -- the scan tool's contents window
 * and the required-items computation -- are read-only.</p>
 */
final class StorageItemHandler implements IItemHandler
{
    /**
     * The adapted storage.
     */
    private final Storage<ItemVariant> storage;

    /**
     * Non-null when {@link #storage} exposes real slots; then it is the same object as {@link #storage}.
     */
    @Nullable
    private final SlottedStorage<ItemVariant> slotted;

    /**
     * The stand-in slot list for a storage that is not slotted; empty and unused otherwise.
     */
    private final List<StorageView<ItemVariant>> views;

    /**
     * @param storage the storage to adapt, never null.
     */
    StorageItemHandler(final Storage<ItemVariant> storage)
    {
        this.storage = storage;
        this.slotted = storage instanceof final SlottedStorage<ItemVariant> slottedStorage ? slottedStorage : null;
        this.views = this.slotted != null ? List.of() : snapshotViews(storage);
    }

    /**
     * @param storage the storage whose non-empty views to capture.
     * @return the views, in iteration order.
     */
    private static List<StorageView<ItemVariant>> snapshotViews(final Storage<ItemVariant> storage)
    {
        final List<StorageView<ItemVariant>> snapshot = new ArrayList<>();
        for (final StorageView<ItemVariant> view : storage.nonEmptyViews())
        {
            snapshot.add(view);
        }
        return snapshot;
    }

    /**
     * Opens a transaction, nesting it when this thread is already inside one.
     *
     * @return the open transaction; the caller closes it.
     */
    private static Transaction openTransaction()
    {
        return Transaction.isOpen() ? Transaction.openNested(Transaction.getCurrentUnsafe()) : Transaction.openOuter();
    }

    /**
     * @param slot slot index.
     * @return the view backing that slot, or null when the index is out of range.
     */
    @Nullable
    private StorageView<ItemVariant> view(final int slot)
    {
        if (slot < 0 || slot >= getSlots())
        {
            return null;
        }
        return slotted != null ? slotted.getSlot(slot) : views.get(slot);
    }

    @Override
    public int getSlots()
    {
        return slotted != null ? slotted.getSlotCount() : views.size();
    }

    @Override
    public ItemStack getStackInSlot(final int slot)
    {
        final StorageView<ItemVariant> view = view(slot);
        if (view == null || view.isResourceBlank() || view.getAmount() <= 0)
        {
            return ItemStack.EMPTY;
        }
        return view.getResource().toStack((int) Math.min(view.getAmount(), Integer.MAX_VALUE));
    }

    @Override
    public ItemStack insertItem(final int slot, final ItemStack stack, final boolean simulate)
    {
        if (stack.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        if (slotted != null && (slot < 0 || slot >= slotted.getSlotCount()))
        {
            return stack;
        }

        // A slotted storage inserts into the addressed slot. A storage without slots has nothing to address, so
        // the insertion goes to the storage as a whole; a caller walking the slots still inserts the original
        // amount at most, because it feeds the remainder of one call into the next.
        final Storage<ItemVariant> target = slotted != null ? slotted.getSlot(slot) : storage;
        if (!target.supportsInsertion())
        {
            return stack;
        }

        final long inserted;
        try (final Transaction transaction = openTransaction())
        {
            inserted = target.insert(ItemVariant.of(stack), stack.getCount(), transaction);
            if (!simulate)
            {
                transaction.commit();
            }
        }

        if (inserted <= 0)
        {
            return stack;
        }
        return inserted >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - (int) inserted);
    }

    @Override
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
    {
        if (amount <= 0)
        {
            return ItemStack.EMPTY;
        }

        final StorageView<ItemVariant> view = view(slot);
        if (view == null || view.isResourceBlank())
        {
            return ItemStack.EMPTY;
        }

        final ItemVariant variant = view.getResource();
        final long extracted;
        try (final Transaction transaction = openTransaction())
        {
            extracted = view.extract(variant, amount, transaction);
            if (!simulate)
            {
                transaction.commit();
            }
        }

        return extracted <= 0 ? ItemStack.EMPTY : variant.toStack((int) Math.min(extracted, Integer.MAX_VALUE));
    }

    @Override
    public int getSlotLimit(final int slot)
    {
        final StorageView<ItemVariant> view = view(slot);
        return view == null ? 0 : (int) Math.min(view.getCapacity(), Integer.MAX_VALUE);
    }

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack)
    {
        return !stack.isEmpty() && insertItem(slot, stack, true).getCount() < stack.getCount();
    }
}
