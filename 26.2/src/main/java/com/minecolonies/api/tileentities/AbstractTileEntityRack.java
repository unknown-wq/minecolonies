package com.minecolonies.api.tileentities;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.IItemHandlerCapProvider;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import com.minecolonies.api.inventory.api.IItemHandlerModifiable;
import com.minecolonies.api.inventory.api.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.Constants.DEFAULT_SIZE;

public abstract class AbstractTileEntityRack extends BlockEntity implements MenuProvider, IItemHandlerCapProvider
{
    /**
     * whether this rack is in a warehouse or not. defaults to not set by the warehouse building upon being built
     */
    protected boolean inWarehouse = false;

    /**
     * Pos of the owning building.
     */
    protected BlockPos buildingPos = BlockPos.ZERO;

    /**
     * The inventory of the tileEntity.
     */
    protected ItemStackHandler inventory;

    /**
     * Create a new rack.
     * @param tileEntityTypeIn the specific block entity type.
     * @param pos the position.
     * @param state its state.
     */
    public AbstractTileEntityRack(final BlockEntityType<?> tileEntityTypeIn, final BlockPos pos, final BlockState state)
    {
        super(tileEntityTypeIn, pos, state);
        inventory = createInventory(DEFAULT_SIZE);
    }

    /**
     * Create a rack with a specific inventory size.
     * @param tileEntityTypeIn the specific block entity type.
     * @param pos the position.
     * @param state its state.
     * @param size the ack size.
     */
    public AbstractTileEntityRack(final BlockEntityType<?> tileEntityTypeIn, final BlockPos pos, final BlockState state, final int size)
    {
        super(tileEntityTypeIn, pos, state);
        inventory = createInventory(size);
    }

    /**
     * Rack inventory type.
     */
    public class RackInventory extends ItemStackHandler
    {
        public RackInventory(final int defaultSize)
        {
            super(defaultSize);
        }

        @Override
        protected void onContentsChanged(final int slot)
        {
            updateItemStorage();
            super.onContentsChanged(slot);
        }

        @Override
        public void setStackInSlot(final int slot, final @NotNull ItemStack stack)
        {
            validateSlotIndex(slot);
            final boolean changed = !ItemStack.matches(stack, this.stacks.get(slot));
            this.stacks.set(slot, stack);
            if (changed)
            {
                onContentsChanged(slot);
            }
            updateWarehouseIfAvailable(stack);
        }

        @NotNull
        @Override
        public ItemStack insertItem(final int slot, @NotNull final ItemStack stack, final boolean simulate)
        {
            final ItemStack result = super.insertItem(slot, stack, simulate);
            if ((result.isEmpty() || result.getCount() < stack.getCount()) && !simulate)
            {
                updateWarehouseIfAvailable(stack);
            }
            return result;
        }
    }

    /**
     * Create the inventory that belongs to the rack.
     *
     * @param slots the number of slots.
     * @return the created inventory,
     */
    public abstract ItemStackHandler createInventory(final int slots);

    /**
     * Spill the inventory when the block holding it goes away.
     * <p>
     * PORT-NOTE(26.2): every block built on this class -- the rack, the grave, and every hut -- dropped this exact
     * inventory from its own {@code BlockBehaviour#onRemove}, which still saw the block entity. 26.2 split that hook
     * in two and reordered it: {@code LevelChunk#setBlockState} calls {@link BlockEntity#preRemoveSideEffects} first,
     * then removes the block entity, and only then calls the block's {@code affectNeighborsAfterRemoval}
     * (/opt/mc-src/net/minecraft/world/level/chunk/LevelChunk.java:307-320) -- where {@code getBlockEntity(pos)} is
     * already null, so the ported drops could never fire. It lives here, once, because this is the class that owns
     * the inventory; the three block classes keep only what is still true of them.
     * <p>
     * Vanilla calls this server side only, only when the block really changed, and only when the caller did not ask
     * for drops to be suppressed. That is narrower than the hook it replaces -- 1.21.1 spilled a hut chest on any
     * state change of the hut block, not just on its removal -- and narrower is the intent everywhere here.
     *
     * @param pos   the position the block is being removed from.
     * @param state the state being removed.
     */
    @Override
    public void preRemoveSideEffects(@NotNull final BlockPos pos, @NotNull final BlockState state)
    {
        super.preRemoveSideEffects(pos, state);

        if (level != null)
        {
            InventoryUtils.dropItemHandler(inventory, level, pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /**
     * Update the warehouse if available with the updated stack.
     *
     * @param stack the incoming stack.
     */
    public void updateWarehouseIfAvailable(final ItemStack stack)
    {
        if (!ItemStackUtils.isEmpty(stack) && level != null && !level.isClientSide())
        {
            if (inWarehouse || !buildingPos.equals(BlockPos.ZERO))
            {
                if (IColonyManager.getInstance().isCoordinateInAnyColony(level, worldPosition))
                {
                    final IColony colony = IColonyManager.getInstance().getClosestColony(level, worldPosition);
                    if (colony == null)
                    {
                        return;
                    }

                    if (inWarehouse)
                    {
                        colony.getRequestManager().onColonyUpdate(request ->
                                                                    request.getRequest() instanceof IDeliverable && ((IDeliverable) request.getRequest()).matches(stack));
                    }
                    else
                    {
                        final IBuilding building = colony.getServerBuildingManager().getBuilding(buildingPos);
                        if (building != null)
                        {
                            building.overruleNextOpenRequestWithStack(stack);
                            building.markDirty();
                        }
                    }
                }
            }
        }
    }

    /**
     * Set the value for inWarehouse
     *
     * @param isInWarehouse is this rack in a warehouse?
     */
    public abstract void setInWarehouse(Boolean isInWarehouse);

    /**
     * Get the amount of free slots in the inventory. This method checks the content list, it is therefore extremely fast.
     *
     * @return the amount of free slots (an integer).
     */
    public abstract int getFreeSlots();

    /**
     * Check if a similar/same item as the stack is in the inventory. This method checks the content list, it is therefore extremely fast.
     *
     * @param stack             the stack to check.
     * @param count             the min count it should have.
     * @param ignoreDamageValue ignore the damage value.
     * @return true if so.
     */
    public abstract boolean hasItemStack(ItemStack stack, final int count, boolean ignoreDamageValue);

    /**
     * Check if the itemStorage exists in the inventory. This method checks the content list, it is therefore extremely fast.
     *
     * @param storage           the storage to check.
     * @param count             the min count it should have.
     * @return true if so.
     */
    public abstract boolean hasItemStorage(final ItemStorage storage, final int count);

    /**
     * Check if a similar/same item as the stack is in the inventory. And return the count if so.
     *
     * @param stack             the stack to check.
     * @param ignoreDamageValue ignore the damage value.
     * @param ignoreNBT         if nbt should be ignored.
     * @return the quantity or 0.
     */
    public abstract int getCount(ItemStack stack, boolean ignoreDamageValue, final boolean ignoreNBT);

    /**
     * Check if a similar/same item as the stack is in the inventory. And return the count if so.
     *
     * @param storage the storage to match.
     * @return the quantity or 0.
     */
    public abstract int getCount(ItemStorage storage);

    /**
     * Check if a similar/same item as the stack is in the inventory. This method checks the content list, it is therefore extremely fast.
     *
     * @param itemStackSelectionPredicate the predicate to test the stack against.
     * @return true if so.
     */
    public abstract boolean hasItemStack(@NotNull Predicate<ItemStack> itemStackSelectionPredicate);

    /**
     * Check if a similar stack is in the rack.
     *
     * @param stack stack to check.
     * @return a set of different results depending on the similarity metric.
     */
    public abstract boolean hasSimilarStack(@NotNull ItemStack stack);

    /**
     * Upgrade the rack by 1. This adds 9 more slots and copies the inventory to the new one.
     */
    public abstract void upgradeRackSize();

    /**
     * Set the building pos it belongs to.
     *
     * @param pos the pos of the building.
     */
    public void setBuildingPos(final BlockPos pos)
    {
        if (level != null && (buildingPos == null || !buildingPos.equals(pos)))
        {
            setChanged();
        }
        this.buildingPos = pos;
    }

    /**
     * Get the upgrade size.
     *
     * @return the upgrade size.
     */
    public abstract int getUpgradeSize();

    /* Get the amount of items matching a predicate in the inventory.
     * @param predicate the predicate.
     * @return the total count.
     */
    public abstract int getItemCount(Predicate<ItemStack> predicate);

    /**
     * Scans through the whole storage and updates it.
     */
    public abstract void updateItemStorage();

    /**
     * Update the blockState of the rack. Switch between connected, single, full and empty texture.
     */
    protected abstract void updateBlockState();

    /**
     * Get the other double chest or null.
     *
     * @return the tileEntity of the other half or null.
     */
    public abstract AbstractTileEntityRack getOtherChest();

    /**
     * Checks if the chest is empty. This method checks the content list, it is therefore extremely fast.
     *
     * @return true if so.
     */
    public abstract boolean isEmpty();

    public IItemHandlerModifiable getInventory()
    {
        return inventory;
    }
}
