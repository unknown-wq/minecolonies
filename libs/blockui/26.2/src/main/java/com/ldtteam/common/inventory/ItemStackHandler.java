package com.ldtteam.common.inventory;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A plain array-backed {@link IItemHandlerModifiable} that owns its stacks, the counterpart of NeoForge's
 * {@code net.neoforged.neoforge.items.ItemStackHandler}. Use it when there is no vanilla
 * {@link net.minecraft.world.Container} to wrap.
 * <p>
 * The NBT format is <b>not</b> NeoForge's. NeoForge wrote {@code {Size:n, Items:[{Slot:i, ...stack}]}} through its
 * own patched {@code ItemStack#save}, which no longer exists; this stores {@link ItemStack#MAP_CODEC} inline next
 * to an explicit {@code Slot} int, so the shape survives but worlds written by a NeoForge build do not round-trip.
 * <p>
 * {@link #serializeNBT(HolderLookup.Provider)} and {@link #deserializeNBT(HolderLookup.Provider, CompoundTag)}
 * deliberately carry the names and parameter order NeoForge's {@code INBTSerializable<CompoundTag>} used, so a
 * consumer that kept its own copy of that interface can declare it on a subclass and have these count as the
 * implementation. This package does not declare such an interface itself - serialisation is not what an inventory
 * view is for, and BlockUI has no business owning that contract for its consumers.
 */
public class ItemStackHandler implements IItemHandlerModifiable
{
    private static final String TAG_SIZE = "Size";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";

    /**
     * Backing storage. Protected because subclasses are expected to index it directly.
     */
    protected NonNullList<ItemStack> stacks;

    /**
     * Creates a single-slot handler.
     */
    public ItemStackHandler()
    {
        this(1);
    }

    /**
     * @param size how many slots to start with
     */
    public ItemStackHandler(final int size)
    {
        this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    /**
     * @param stacks the list to store into, taken over as-is rather than copied
     */
    public ItemStackHandler(@NotNull final NonNullList<ItemStack> stacks)
    {
        this.stacks = stacks;
    }

    /**
     * Resizes, keeping as much of the current contents as still fits. Anything above the new size is dropped on
     * the floor - it is the caller's job to empty those slots first if the items matter.
     *
     * @param size the new slot count
     */
    public void setSize(final int size)
    {
        final NonNullList<ItemStack> old = this.stacks;
        this.stacks = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int slot = 0; slot < Math.min(size, old.size()); slot++)
        {
            this.stacks.set(slot, old.get(slot));
        }
    }

    @Override
    public void setStackInSlot(final int slot, @NotNull final ItemStack stack)
    {
        validateSlotIndex(slot);
        this.stacks.set(slot, stack);
        onContentsChanged(slot);
    }

    @Override
    public int getSlots()
    {
        return this.stacks.size();
    }

    @Override
    @NotNull
    public ItemStack getStackInSlot(final int slot)
    {
        validateSlotIndex(slot);
        return this.stacks.get(slot);
    }

    @Override
    @NotNull
    public ItemStack insertItem(final int slot, @NotNull final ItemStack stack, final boolean simulate)
    {
        if (stack.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        if (!isItemValid(slot, stack))
        {
            return stack;
        }

        validateSlotIndex(slot);

        final ItemStack existing = this.stacks.get(slot);

        int limit = getStackLimit(slot, stack);
        if (!existing.isEmpty())
        {
            if (!ItemStack.isSameItemSameComponents(stack, existing))
            {
                return stack;
            }
            limit -= existing.getCount();
        }

        if (limit <= 0)
        {
            return stack;
        }

        final boolean reachedLimit = stack.getCount() > limit;
        if (!simulate)
        {
            if (existing.isEmpty())
            {
                this.stacks.set(slot, reachedLimit ? stack.copyWithCount(limit) : stack);
            }
            else
            {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }
            onContentsChanged(slot);
        }

        return reachedLimit ? stack.copyWithCount(stack.getCount() - limit) : ItemStack.EMPTY;
    }

    @Override
    @NotNull
    public ItemStack extractItem(final int slot, final int amount, final boolean simulate)
    {
        if (amount == 0)
        {
            return ItemStack.EMPTY;
        }

        validateSlotIndex(slot);

        final ItemStack existing = this.stacks.get(slot);
        if (existing.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        final int toExtract = Math.min(amount, existing.getMaxStackSize());

        if (existing.getCount() <= toExtract)
        {
            if (!simulate)
            {
                this.stacks.set(slot, ItemStack.EMPTY);
                onContentsChanged(slot);
                return existing;
            }
            return existing.copy();
        }

        if (!simulate)
        {
            this.stacks.set(slot, existing.copyWithCount(existing.getCount() - toExtract));
            onContentsChanged(slot);
        }
        return existing.copyWithCount(toExtract);
    }

    @Override
    public int getSlotLimit(final int slot)
    {
        return Item.DEFAULT_MAX_STACK_SIZE;
    }

    /**
     * @param slot  the slot index
     * @param stack the stack being inserted
     * @return the effective cap for that stack in that slot
     */
    protected int getStackLimit(final int slot, @NotNull final ItemStack stack)
    {
        return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    }

    @Override
    public boolean isItemValid(final int slot, @NotNull final ItemStack stack)
    {
        return true;
    }

    /**
     * Writes the contents out to a fresh tag.
     *
     * @param provider registry lookup for the stack codec
     * @return the serialized contents
     */
    public CompoundTag serializeNBT(final HolderLookup.Provider provider)
    {
        final ListTag items = new ListTag();
        for (int slot = 0; slot < this.stacks.size(); slot++)
        {
            final ItemStack stack = this.stacks.get(slot);
            if (!stack.isEmpty())
            {
                final CompoundTag entry = new CompoundTag();
                entry.putInt(TAG_SLOT, slot);
                entry.store(ItemStack.MAP_CODEC, stack);
                items.add(entry);
            }
        }

        final CompoundTag out = new CompoundTag();
        out.put(TAG_ITEMS, items);
        out.putInt(TAG_SIZE, this.stacks.size());
        return out;
    }

    /**
     * Reads back contents produced by {@link #serializeNBT(HolderLookup.Provider)}. A slot the tag does not
     * mention is left empty, and a slot outside the recorded size is skipped rather than throwing.
     *
     * @param provider registry lookup for the stack codec
     * @param nbt      the tag to read
     */
    public void deserializeNBT(final HolderLookup.Provider provider, final CompoundTag nbt)
    {
        setSize(nbt.getIntOr(TAG_SIZE, this.stacks.size()));

        final ListTag items = nbt.getListOrEmpty(TAG_ITEMS);
        for (int i = 0; i < items.size(); i++)
        {
            final CompoundTag entry = items.getCompoundOrEmpty(i);
            final int slot = entry.getIntOr(TAG_SLOT, -1);
            if (slot >= 0 && slot < this.stacks.size())
            {
                this.stacks.set(slot, entry.read(ItemStack.MAP_CODEC).orElse(ItemStack.EMPTY));
            }
        }
        onLoad();
    }

    /**
     * Hook for subclasses, called at the end of {@link #deserializeNBT}.
     */
    protected void onLoad()
    {
        // Intentionally left empty.
    }

    /**
     * Hook for subclasses, called whenever a slot changed.
     *
     * @param slot the slot that changed
     */
    protected void onContentsChanged(final int slot)
    {
        // Intentionally left empty.
    }

    /**
     * @param slot the slot index to bounds-check
     * @throws IndexOutOfBoundsException when the slot is outside this handler
     */
    protected void validateSlotIndex(final int slot)
    {
        if (slot < 0 || slot >= this.stacks.size())
        {
            throw new IndexOutOfBoundsException("Slot " + slot + " not in valid range - [0," + this.stacks.size() + ")");
        }
    }
}
