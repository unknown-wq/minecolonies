package com.minecolonies.core.tileentities;

import com.minecolonies.api.inventory.InventoryCitizen;
import com.minecolonies.api.tileentities.AbstractTileEntityRack;
import com.minecolonies.api.tileentities.AbstractTileEntityWareHouse;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.*;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.Constants.TICKS_FIVE_MIN;
import static com.minecolonies.api.util.constant.TranslationConstants.*;
import static com.minecolonies.core.colony.buildings.workerbuildings.BuildingWareHouse.MAX_STORAGE_UPGRADE;

/**
 * Class which handles the tileEntity of our colony warehouse.
 */
public class TileEntityWareHouse extends AbstractTileEntityWareHouse
{
    /**
     * Time of last sent notifications.
     */
    private long lastNotification                   = 0;

    public TileEntityWareHouse(final BlockPos pos, final BlockState state)
    {
        super(MinecoloniesTileEntities.WAREHOUSE.get(), pos, state);
        inWarehouse = true;
    }

    @Override
    public boolean hasMatchingItemStackInWarehouse(@NotNull final Predicate<ItemStack> itemStackSelectionPredicate, int count)
    {
        int totalCount = 0;
        if (getBuilding() != null)
        {
            for (@NotNull final BlockPos pos : getBuilding().getContainers())
            {
                if (WorldUtil.isBlockLoaded(level, pos))
                {
                    final BlockEntity entity = getLevel().getBlockEntity(pos);
                    if (entity instanceof final TileEntityRack rack && !rack.isEmpty())
                    {
                        totalCount += rack.getItemCount(itemStackSelectionPredicate);
                        if (totalCount >= count)
                        {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    @Override
    public boolean hasMatchingItemStackInWarehouse(@NotNull final ItemStack itemStack, final int count, final boolean ignoreNBT)
    {
        return hasMatchingItemStackInWarehouse(itemStack, count, ignoreNBT, 0);
    }

    @Override
    public boolean hasMatchingItemStackInWarehouse(@NotNull final ItemStack itemStack, final int count, final boolean ignoreNBT, final boolean ignoreDamage, final int leftOver)
    {
        int totalCountFound = 0 - leftOver;
        for (@NotNull final BlockPos pos : getBuilding().getContainers())
        {
            if (WorldUtil.isBlockLoaded(level, pos))
            {
                final BlockEntity entity = getLevel().getBlockEntity(pos);
                if (entity instanceof TileEntityRack && !((AbstractTileEntityRack) entity).isEmpty())
                {
                    totalCountFound += ((AbstractTileEntityRack) entity).getCount(itemStack, ignoreDamage, ignoreNBT);
                    if (totalCountFound >= count)
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasMatchingItemStackInWarehouse(@NotNull final ItemStack itemStack, final int count, final boolean ignoreNBT, final int leftOver)
    {
        return hasMatchingItemStackInWarehouse(itemStack, count, ignoreNBT, true, leftOver);
    }

    @Override
    @NotNull
    public List<Tuple<ItemStack, BlockPos>> getMatchingItemStacksInWarehouse(@NotNull final Predicate<ItemStack> itemStackSelectionPredicate)
    {
        List<Tuple<ItemStack, BlockPos>> found = new ArrayList<>();
        
        if (getBuilding() != null)
        {
            for (@NotNull final BlockPos pos : getBuilding().getContainers())
            {
                if (WorldUtil.isBlockLoaded(level, pos))
                {
                    final BlockEntity entity = getLevel().getBlockEntity(pos);
                    if (entity instanceof final TileEntityRack rack && !rack.isEmpty() && rack.getItemCount(itemStackSelectionPredicate) > 0)
                    {
                        for (final ItemStack stack : (InventoryUtils.filterItemHandler(rack.getInventory(), itemStackSelectionPredicate)))
                        {
                            found.add(new Tuple<>(stack, pos));
                        }
                    }
                }
            }
        }
        return found;
    }

    @Override
    public int dumpInventoryIntoWareHouse(@NotNull final InventoryCitizen inventoryCitizen)
    {
        int stored = 0;
        boolean refused = false;
        for (int i = 0; i < inventoryCitizen.getSlots(); i++)
        {
            final ItemStack stack = inventoryCitizen.getStackInSlot(i);
            if (ItemStackUtils.isEmpty(stack))
            {
                continue;
            }

            @Nullable final AbstractTileEntityRack chest = getRackForStack(stack);
            if (chest == null)
            {
                // Was a `return`, which abandoned the rest of the pack on the first stack that would not fit. The
                // three probes in getRackForStack are per stack -- a rack already holding item B can have room for B
                // while nothing at all has room for A -- so giving up on the whole pack threw away deliveries that
                // would have gone in. Carry on with the next slot and report the refusal once, at the end.
                refused = true;
                continue;
            }

            InventoryUtils.transferItemStackIntoNextBestSlotInItemHandler(inventoryCitizen, i, chest.getItemHandlerCap());
            if (ItemStackUtils.isEmpty(inventoryCitizen.getStackInSlot(i)))
            {
                stored++;
            }
        }

        if (refused && level.getGameTime() - lastNotification > TICKS_FIVE_MIN)
        {
            lastNotification = level.getGameTime();
            warnWarehouseFull();
        }

        return stored;
    }

    /**
     * Tell the colony that this warehouse could not take something, with the numbers that say how bad it is and what
     * would help.
     * <p>
     * The old message was three fixed sentences with no numbers in them, and it did not mention the thing that
     * actually stops the colony: a courier that cannot put its pack down does not go on to make deliveries either.
     * <p>
     * <b>Two numbers, never one.</b> Slot occupancy is what physically refuses the courier -- a slot holding three
     * cobblestone is as full as a slot holding sixty-four. Capacity fill is how much of the warehouse the goods really
     * take, measured in whole stacks of their own item. A warehouse can be 100 % of the first and 5 % of the second,
     * and that gap is precisely the number the Sort button recovers, so it is quoted as a third figure rather than
     * blended into either.
     */
    private void warnWarehouseFull()
    {
        final Occupancy occupancy = measureOccupancy();
        final String where = worldPosition.toShortString();
        final String used = String.valueOf(occupancy.usedSlots);
        final String total = String.valueOf(occupancy.totalSlots);
        final String percent = occupancy.totalSlots <= 0
                                 ? "100.0"
                                 : String.format(Locale.ROOT, "%.1f", 100.0 * occupancy.usedSlots / occupancy.totalSlots);
        final String capacityPercent = occupancy.totalSlots <= 0
                                         ? "0.0"
                                         : String.format(Locale.ROOT, "%.1f", 100.0 * occupancy.stackEquivalents / occupancy.totalSlots);
        final String recoverable = String.valueOf(Math.max(0, occupancy.usedSlots - occupancy.packedSlots));

        if (getBuilding().getBuildingLevel() == getBuilding().getMaxBuildingLevel())
        {
            if (getBuilding().getModule(BuildingModules.WAREHOUSE_OPTIONS).getStorageUpgrade() < MAX_STORAGE_UPGRADE)
            {
                MessageUtils.format(COM_MINECOLONIES_COREMOD_WAREHOUSE_FULL_LEVEL5_UPGRADE,
                  where, used, total, percent, capacityPercent, recoverable,
                  String.valueOf(MAX_STORAGE_UPGRADE - getBuilding().getModule(BuildingModules.WAREHOUSE_OPTIONS).getStorageUpgrade()))
                  .sendTo(getColony()).forAllPlayers();
            }
            else
            {
                MessageUtils.format(COM_MINECOLONIES_COREMOD_WAREHOUSE_FULL_MAX_UPGRADE,
                  where, used, total, percent, capacityPercent, recoverable).sendTo(getColony()).forAllPlayers();
            }
        }
        else
        {
            MessageUtils.format(COM_MINECOLONIES_COREMOD_WAREHOUSE_FULL,
              where, used, total, percent, capacityPercent, recoverable).sendTo(getColony()).forAllPlayers();
        }
    }

    /**
     * How full this warehouse is, right now, by both measures.
     * <p>
     * Counted here rather than read off {@link com.minecolonies.core.colony.buildings.modules.WarehouseIdleTrackerModule}
     * on purpose: that module samples once per colony tick and only while every one of the warehouse's chunks is
     * loaded, so its figures can be minutes old or absent altogether, and a message that says "full" has to be able
     * to say how full at the moment it says it. The walk is one array read per slot plus one map walk per rack,
     * bounded by the five-minute throttle on the only caller.
     *
     * @return the three figures.
     */
    private Occupancy measureOccupancy()
    {
        final Occupancy result = new Occupancy();
        final Map<ItemStorage, Integer> totals = new HashMap<>();
        for (@NotNull final BlockPos pos : getBuilding().getContainers())
        {
            if (!WorldUtil.isBlockLoaded(level, pos) || !(getLevel().getBlockEntity(pos) instanceof final TileEntityRack rack))
            {
                continue;
            }

            final int slots = rack.getInventory().getSlots();
            result.totalSlots += slots;
            for (int slot = 0; slot < slots; slot++)
            {
                if (!rack.getInventory().getStackInSlot(slot).isEmpty())
                {
                    result.usedSlots++;
                }
            }
            for (final Map.Entry<ItemStorage, Integer> entry : rack.getAllContent().entrySet())
            {
                totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }

        for (final Map.Entry<ItemStorage, Integer> entry : totals.entrySet())
        {
            final int maxStack = Math.max(1, entry.getKey().getItemStack().getMaxStackSize());
            result.stackEquivalents += entry.getValue() / (double) maxStack;
            // What a perfectly packed warehouse would need: whole stacks per item type, with the remainder of each
            // type taking one slot of its own. Anything above this is fragmentation, and fragmentation is exactly
            // what the Sort button gives back.
            result.packedSlots += (entry.getValue() + maxStack - 1) / maxStack;
        }

        return result;
    }

    /**
     * The three occupancy figures of one warehouse: slots holding something, slots in total, the goods measured in
     * whole stacks of their own item, and the slots a perfectly packed warehouse would need for the same goods.
     */
    private static final class Occupancy
    {
        private int    usedSlots        = 0;
        private int    totalSlots       = 0;
        private double stackEquivalents = 0;
        private int    packedSlots      = 0;
    }

    /**
     * Whether this warehouse can take anything at all.
     * <p>
     * All three probes of {@link #getRackForStack} require a rack with a free slot -- the third,
     * {@link #searchMostEmptyRack}, accepts an empty rack or one with {@code getFreeSlots() > 0} and nothing else --
     * so "no registered rack has a free slot" is exactly "getRackForStack returns null whatever the stack is", without
     * having to name a stack.
     *
     * @return true when no rack of this warehouse has a free slot.
     */
    public boolean isFull()
    {
        for (@NotNull final BlockPos pos : getBuilding().getContainers())
        {
            if (WorldUtil.isBlockLoaded(level, pos)
                  && getLevel().getBlockEntity(pos) instanceof final AbstractTileEntityRack rack
                  && rack.getFreeSlots() > 0)
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Get a rack for a stack.
     * @param stack the stack to insert.
     * @return the matching rack.
     */
    public AbstractTileEntityRack getRackForStack(final ItemStack stack)
    {
        AbstractTileEntityRack rack = getPositionOfChestWithItemStack(stack);
        if (rack == null)
        {
            rack = getPositionOfChestWithSimilarItemStack(stack);
            if (rack == null)
            {
                rack = searchMostEmptyRack();
            }
        }
        return rack;
    }

    /**
     * Search the right chest for an itemStack.
     *
     * @param stack the stack to dump.
     * @return the tile entity of the chest
     */
    @Nullable
    private AbstractTileEntityRack getPositionOfChestWithItemStack(@NotNull final ItemStack stack)
    {
        for (@NotNull final BlockPos pos : getBuilding().getContainers())
        {
            if (WorldUtil.isBlockLoaded(level, pos))
            {
                final BlockEntity entity = getLevel().getBlockEntity(pos);
                if (entity instanceof final AbstractTileEntityRack rack)
                {
                    if (rack.getFreeSlots() > 0 && rack.hasItemStack(stack, 1, true))
                    {
                        return rack;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Searches a chest with a similar item as the incoming stack.
     *
     * @param stack the stack.
     * @return the entity of the chest.
     */
    @Nullable
    private AbstractTileEntityRack getPositionOfChestWithSimilarItemStack(final ItemStack stack)
    {
        for (@NotNull final BlockPos pos : getBuilding().getContainers())
        {
            if (WorldUtil.isBlockLoaded(level, pos))
            {
                final BlockEntity entity = getLevel().getBlockEntity(pos);
                if (entity instanceof final AbstractTileEntityRack rack)
                {
                    if (rack.getFreeSlots() > 0 && rack.hasSimilarStack(stack))
                    {
                        return rack;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Search for the chest with the least items in it.
     *
     * @return the tileEntity of this chest.
     */
    @Nullable
    private AbstractTileEntityRack searchMostEmptyRack()
    {
        int freeSlots = 0;
        AbstractTileEntityRack emptiestChest = null;
        for (@NotNull final BlockPos pos : getBuilding().getContainers())
        {
            final BlockEntity entity = getLevel().getBlockEntity(pos);
            if (entity instanceof final TileEntityRack rack)
            {
                if (rack.isEmpty())
                {
                    return rack;
                }

                final int tempFreeSlots = rack.getFreeSlots();
                if (tempFreeSlots > freeSlots)
                {
                    freeSlots = tempFreeSlots;
                    emptiestChest = rack;
                }
            }
        }
        return emptiestChest;
    }
}
