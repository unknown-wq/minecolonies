package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.colony.buildings.modules.ITickingModule;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.tileentities.TileEntityRack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Remembers how long every kind of item in one warehouse has been sitting there without anybody taking it, and how
 * much of it has been taken lately.
 * <p>
 * <b>Pure observer.</b> Nothing in the withdrawal path calls into this module and no mixin widens anything for it. It
 * samples the warehouse once per colony tick (every 500 ticks, ~25 s) through
 * {@link TileEntityRack#getAllContent()}, which the rack keeps up to date anyway, so the item totals cost one map
 * walk per rack rather than a slot-by-slot scan; only the occupied-slot count needs a pass over the slots, for the
 * reason given at the call site. Everything it reports is a difference between two such samples.
 * <p>
 * <b>Only a decrease counts.</b> The player's question is "what is nobody using", and the only evidence of use that a
 * stock level carries is going down. An increase is a delivery or a crafter's output and says nothing about demand, so
 * restocking deliberately leaves the idle clock alone: a barrel of cobblestone that is topped up every morning and
 * never drawn from is exactly the junk this is meant to find.
 * <p>
 * <b>Two numbers, not one.</b> Idle age alone misjudges a thing taken rarely but regularly, so every item also carries
 * a rolling {@value #WINDOW_DAYS}-day withdrawal total. "Idle 20 days, never taken" is junk; "idle 1 day, 400 taken
 * this week" is a consumable that must not be sold.
 * <p>
 * <b>A sample is all-or-nothing.</b> An unloaded chunk reads as an empty rack, which would look exactly like somebody
 * emptying it. Unless every registered container of this warehouse is in a loaded chunk the whole sample is thrown
 * away, and the next one starts a fresh baseline rather than being differenced against a stale one. Racks appearing,
 * disappearing or being upgraded are handled the same way: the counts are refreshed but no withdrawal is attributed
 * to the tick that noticed the change.
 */
public class WarehouseIdleTrackerModule extends AbstractBuildingModule implements IPersistentModule, ITickingModule
{
    /**
     * Ticks in a Minecraft day. Idle ages and rates are quoted in these days because that is the unit the player
     * plays in; the tracker itself never looks at the clock time of day.
     */
    public static final int TICKS_PER_DAY = 24000;

    /**
     * How many days of withdrawals the rolling rate window keeps.
     * <p>
     * A week is long enough that a consumable used a few times a week still shows a rate, and short enough that a
     * thing which stopped being used a month ago has already fallen out of it.
     */
    public static final int WINDOW_DAYS = 7;

    /**
     * How long an item type that has left the warehouse entirely is kept before it is forgotten, in ticks.
     * <p>
     * Exactly the width of the rate window: once an item has been at zero for the whole window it has no count left
     * to report and no withdrawals left in the window either, so its row would be empty in every column the report
     * has. Keeping it for the window and not longer means a warehouse that is briefly emptied -- a courier carrying
     * the last stack across, a rack being rebuilt -- does not lose its history, while a map entry cannot outlive the
     * data that would have justified it.
     */
    private static final long FORGET_AFTER_TICKS = (long) WINDOW_DAYS * TICKS_PER_DAY;

    /**
     * NBT tags.
     */
    private static final String TAG_ITEMS      = "whIdleItems";
    private static final String TAG_STACK      = "stack";
    private static final String TAG_FIRST_SEEN = "first";
    private static final String TAG_LAST_TAKEN = "taken";
    private static final String TAG_TOTAL      = "total";
    private static final String TAG_COUNT      = "count";
    private static final String TAG_WINDOW_DAY = "wday";
    private static final String TAG_WINDOW     = "window";
    private static final String TAG_LAST_HELD  = "held";

    /**
     * The tracked history, one entry per distinct item type currently or recently in this warehouse.
     */
    private final Map<ItemStorage, ItemHistory> history = new HashMap<>();

    /**
     * Whether the previous sample may be differenced against.
     * <p>
     * Deliberately not persisted. False after a load, after a discarded sample and after any structural change, which
     * is what makes "the racks were not all loaded" and "somebody took things out" different events rather than the
     * same subtraction.
     */
    private boolean baselined = false;

    /**
     * The container positions that actually held a rack at the previous sample, so that a rack appearing or
     * disappearing is recognised as a structural change rather than as a delivery or a withdrawal.
     * <p>
     * The positions that <em>resolved</em>, not the building's registered list, and the difference matters. Breaking a
     * rack with a pickaxe leaves its position registered for ever: the only thing that ever calls
     * {@code removeContainerPosition} is {@code TileEntityColonyBuilding}'s inventory-capability walk, and that only
     * fires when the position holds some <em>other</em> block entity, never when it holds air. Comparing registered
     * lists would therefore have made every subsequent sample "structural" and quietly stopped the whole warehouse
     * being tracked; comparing what resolved costs one re-baseline for the break itself and nothing after it.
     */
    private Set<BlockPos> lastRacks = Collections.emptySet();

    /**
     * The storage upgrade level the previous sample saw. An upgrade moves the stacks into a bigger inventory; the
     * contents do not change, but the re-baseline costs nothing and removes the question.
     */
    private int lastStorageUpgrade = -1;

    /**
     * Cost of the most recent accepted sample, in nanoseconds, and how many racks and item types it covered. Reported
     * by the command so that the price of the feature is visible rather than asserted.
     */
    private long lastSampleNanos = -1;

    /**
     * Racks walked by the most recent accepted sample.
     */
    private int lastSampleRacks = 0;

    /**
     * When the most recent accepted sample was taken, in game ticks, or -1 if there has not been one.
     */
    private long lastSampleTick = -1;

    /**
     * Slots holding something, out of {@link #lastTotalSlots}, as of the last accepted sample.
     * <p>
     * This is the number that decides whether a courier can put anything down: a slot with three cobblestone in it is
     * occupied, and a warehouse of such slots refuses new item types while being almost empty by weight. It is
     * therefore the headline occupancy figure and is never blended with {@link #lastStackEquivalents}.
     */
    private int lastUsedSlots = 0;

    /**
     * Every slot of every rack of this warehouse, as of the last accepted sample. Read live from each rack's
     * inventory, so a storage upgrade -- which rebuilds the inventory nine slots larger -- is reflected by the very
     * next sample rather than by a stored capacity that would go stale.
     */
    private int lastTotalSlots = 0;

    /**
     * How many items are held, as of the last accepted sample.
     */
    private long lastItemCount = 0;

    /**
     * How much of the warehouse's capacity the goods actually take up, measured in full stacks of their own item:
     * one sword counts a whole stack because a sword's stack is one, three cobblestone count 3/64 of one. Divided by
     * {@link #lastTotalSlots} this is the "how full by capacity" figure, and it is deliberately reported separately
     * from slot occupancy rather than averaged with it.
     */
    private double lastStackEquivalents = 0;

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        final Level world = colony.getWorld();
        if (world == null)
        {
            baselined = false;
            return;
        }

        final List<BlockPos> containers = building.getContainers();

        // Wholesale load check first: one rack in an unloaded chunk poisons the entire sample, because its contents
        // read as absent and every item only it held would look like it had just been taken to zero.
        for (final BlockPos pos : containers)
        {
            if (!WorldUtil.isBlockLoaded(world, pos))
            {
                baselined = false;
                return;
            }
        }

        final long start = System.nanoTime();

        final Map<ItemStorage, Integer> totals = new HashMap<>();
        final Set<BlockPos> racks = new HashSet<>();
        int usedSlots = 0;
        int totalSlots = 0;
        for (final BlockPos pos : containers)
        {
            final BlockEntity entity = world.getBlockEntity(pos);
            if (!(entity instanceof final TileEntityRack rack))
            {
                // A registered container whose block is no longer a rack: the player broke it, or the building is
                // mid-repair. Its items did not get withdrawn, they left with the block.
                continue;
            }
            racks.add(pos);
            // Capacity is read live, so a storage upgrade -- which replaces the inventory with a bigger one -- is
            // reflected by the very next sample.
            //
            // Occupied slots are counted here rather than taken from the rack's own getFreeSlots(). That started as a
            // workaround: TileEntityRack#upgradeRackSize copied the stacks into a nine-slot-larger inventory and never
            // re-ran updateContent(), so freeSlots kept the pre-upgrade figure and slots - getFreeSlots() over-reported
            // by exactly nine per rack until something else touched the rack -- 610 occupied slots read as 1069 across
            // 51 upgraded racks. That bug is fixed at the source now (see the comment in upgradeRackSize), and the two
            // numbers were measured equal on every rack of a freshly upgraded warehouse afterwards. The scan stays
            // anyway, for two reasons that are not that bug: freeSlots is a cache maintained by one class from one set
            // of hooks, and this module is the thing that would report its next lapse as a warehouse statistic; and
            // the loop is already reading getSlots() off every rack for the capacity figure, so the extra cost is an
            // array read per slot -- under three thousand of them for a level-five warehouse -- against a number that
            // cannot be wrong.
            final int slots = rack.getInventory().getSlots();
            totalSlots += slots;
            for (int slot = 0; slot < slots; slot++)
            {
                if (!rack.getInventory().getStackInSlot(slot).isEmpty())
                {
                    usedSlots++;
                }
            }
            for (final Map.Entry<ItemStorage, Integer> entry : rack.getAllContent().entrySet())
            {
                totals.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }

        final WarehouseModule options = building.getModule(WarehouseModule.class);
        final int storageUpgrade = options == null ? 0 : options.getStorageUpgrade();
        final boolean structural = !racks.equals(lastRacks) || storageUpgrade != lastStorageUpgrade;
        lastRacks = racks;
        lastStorageUpgrade = storageUpgrade;

        final boolean attribute = baselined && !structural;
        final long now = world.getGameTime();
        final long today = now / TICKS_PER_DAY;

        long items = 0;
        double stackEquivalents = 0;
        for (final Map.Entry<ItemStorage, Integer> entry : totals.entrySet())
        {
            final ItemHistory item = history.computeIfAbsent(entry.getKey(), key -> new ItemHistory(now, today));
            item.roll(today);
            final int count = entry.getValue();
            items += count;
            stackEquivalents += count / (double) Math.max(1, entry.getKey().getItemStack().getMaxStackSize());
            if (attribute && count < item.count)
            {
                item.recordWithdrawal(item.count - count, now);
            }
            item.count = count;
            item.lastHeld = now;
        }

        for (final Iterator<Map.Entry<ItemStorage, ItemHistory>> it = history.entrySet().iterator(); it.hasNext(); )
        {
            final Map.Entry<ItemStorage, ItemHistory> entry = it.next();
            if (totals.containsKey(entry.getKey()))
            {
                continue;
            }

            final ItemHistory item = entry.getValue();
            item.roll(today);
            if (attribute && item.count > 0)
            {
                item.recordWithdrawal(item.count, now);
            }
            item.count = 0;

            if (now - item.lastHeld > FORGET_AFTER_TICKS)
            {
                it.remove();
            }
        }

        baselined = true;
        lastSampleTick = now;
        lastSampleRacks = racks.size();
        lastUsedSlots = usedSlots;
        lastTotalSlots = totalSlots;
        lastItemCount = items;
        lastStackEquivalents = stackEquivalents;
        lastSampleNanos = System.nanoTime() - start;
    }

    /**
     * @return slots holding something, as of the last accepted sample.
     */
    public int getUsedSlots()
    {
        return lastUsedSlots;
    }

    /**
     * @return every slot of every rack of this warehouse, as of the last accepted sample.
     */
    public int getTotalSlots()
    {
        return lastTotalSlots;
    }

    /**
     * @return how many items are held, as of the last accepted sample.
     */
    public long getItemCount()
    {
        return lastItemCount;
    }

    /**
     * @return the goods held measured in full stacks of their own item, as of the last accepted sample.
     */
    public double getStackEquivalents()
    {
        return lastStackEquivalents;
    }

    /**
     * The tracked history of this warehouse, keyed by item type.
     *
     * @return the live map; callers must not modify it.
     */
    public Map<ItemStorage, ItemHistory> getHistory()
    {
        return history;
    }

    /**
     * @return how long the last accepted sample took, in nanoseconds, or -1 if there has not been one.
     */
    public long getLastSampleNanos()
    {
        return lastSampleNanos;
    }

    /**
     * @return how many containers the last accepted sample walked.
     */
    public int getLastSampleRacks()
    {
        return lastSampleRacks;
    }

    /**
     * @return the game tick of the last accepted sample, or -1 if there has not been one.
     */
    public long getLastSampleTick()
    {
        return lastSampleTick;
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        history.clear();
        // A warehouse saved before this module existed hands us the whole building tag; the list is simply absent.
        final ListTag items = compound.getListOrEmpty(TAG_ITEMS);
        for (int i = 0; i < items.size(); i++)
        {
            final CompoundTag tag = items.getCompoundOrEmpty(i);
            final ItemStack stack = ItemStack.OPTIONAL_CODEC
                                      .parse(provider.createSerializationContext(NbtOps.INSTANCE), tag.getCompoundOrEmpty(TAG_STACK))
                                      .result()
                                      .orElse(ItemStack.EMPTY);
            if (stack.isEmpty())
            {
                // An item from a mod that is no longer installed. Dropping it is the only sane option and costs
                // nothing: it is not in the warehouse either.
                continue;
            }

            final ItemHistory item = new ItemHistory(tag.getLongOr(TAG_FIRST_SEEN, 0L), tag.getLongOr(TAG_WINDOW_DAY, 0L));
            item.lastTaken = tag.getLongOr(TAG_LAST_TAKEN, -1L);
            item.totalTaken = tag.getLongOr(TAG_TOTAL, 0L);
            item.count = tag.getIntOr(TAG_COUNT, 0);
            item.lastHeld = tag.getLongOr(TAG_LAST_HELD, 0L);
            final int[] window = tag.getIntArray(TAG_WINDOW).orElse(new int[0]);
            for (int d = 0; d < Math.min(window.length, WINDOW_DAYS); d++)
            {
                item.window[d] = window[d];
            }
            history.put(new ItemStorage(stack), item);
        }

        // Whatever the world did while this was on disk is not a withdrawal we can honestly attribute, so the first
        // sample after a load only re-establishes the baseline. The idle ages and rates themselves survive intact.
        baselined = false;
        lastRacks = Collections.emptySet();
        lastStorageUpgrade = -1;
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        final ListTag items = new ListTag();
        for (final Map.Entry<ItemStorage, ItemHistory> entry : history.entrySet())
        {
            final ItemHistory item = entry.getValue();
            final CompoundTag tag = new CompoundTag();
            final ItemStack stack = entry.getKey().getItemStack().copy();
            stack.setCount(1);
            tag.put(TAG_STACK, ItemStack.OPTIONAL_CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow());
            tag.putLong(TAG_FIRST_SEEN, item.firstSeen);
            tag.putLong(TAG_LAST_TAKEN, item.lastTaken);
            tag.putLong(TAG_TOTAL, item.totalTaken);
            tag.putInt(TAG_COUNT, item.count);
            tag.putLong(TAG_WINDOW_DAY, item.windowDay);
            tag.putLong(TAG_LAST_HELD, item.lastHeld);
            tag.putIntArray(TAG_WINDOW, item.window.clone());
            items.add(tag);
        }
        compound.put(TAG_ITEMS, items);
    }

    /**
     * What is remembered about one item type in one warehouse.
     */
    public static class ItemHistory
    {
        /**
         * Game tick at which this item type was first seen in this warehouse.
         */
        private long firstSeen;

        /**
         * Game tick at which the total of this item type last went down, or -1 if it never has.
         */
        private long lastTaken = -1;

        /**
         * Everything ever taken of this item type, over the life of the warehouse. Not windowed, so it survives the
         * window rolling over and answers "has this ever been used at all".
         */
        private long totalTaken = 0;

        /**
         * The total seen by the last accepted sample.
         */
        private int count = 0;

        /**
         * Game tick at which this item type was last actually held in the warehouse, used only for forgetting.
         */
        private long lastHeld;

        /**
         * The day index (game tick / {@link #TICKS_PER_DAY}) that {@code window[0]} stands for.
         */
        private long windowDay;

        /**
         * Withdrawals per day, newest first: {@code window[0]} is today, {@code window[1]} yesterday, and so on.
         */
        private final int[] window = new int[WINDOW_DAYS];

        ItemHistory(final long firstSeen, final long windowDay)
        {
            this.firstSeen = firstSeen;
            this.lastHeld = firstSeen;
            this.windowDay = windowDay;
        }

        /**
         * Move the window forward to the given day, dropping whatever fell off the far end.
         *
         * @param today the current day index.
         */
        void roll(final long today)
        {
            if (today <= windowDay)
            {
                return;
            }

            final long advance = today - windowDay;
            if (advance >= WINDOW_DAYS)
            {
                Arrays.fill(window, 0);
            }
            else
            {
                final int shift = (int) advance;
                for (int i = WINDOW_DAYS - 1; i >= shift; i--)
                {
                    window[i] = window[i - shift];
                }
                Arrays.fill(window, 0, shift, 0);
            }
            windowDay = today;
        }

        /**
         * Record that something was taken. The window must already have been rolled to today.
         *
         * @param amount how much.
         * @param now    the current game tick.
         */
        void recordWithdrawal(final int amount, final long now)
        {
            lastTaken = now;
            totalTaken += amount;
            window[0] += amount;
        }

        /**
         * @return the count seen by the last accepted sample.
         */
        public int getCount()
        {
            return count;
        }

        /**
         * @return the game tick this item type was first seen at.
         */
        public long getFirstSeen()
        {
            return firstSeen;
        }

        /**
         * @return the game tick this item type was last taken at, or -1 if it never was.
         */
        public long getLastTaken()
        {
            return lastTaken;
        }

        /**
         * @return everything ever taken of this item type.
         */
        public long getTotalTaken()
        {
            return totalTaken;
        }

        /**
         * The last moment this item type was either taken or, failing that, first seen -- the point the idle age is
         * measured from.
         *
         * @return the game tick.
         */
        public long getLastActivity()
        {
            return lastTaken >= 0 ? lastTaken : firstSeen;
        }

        /**
         * Everything taken inside the rolling window, as of the given day.
         *
         * @param today the current day index.
         * @return the total.
         */
        public long getTakenInWindow(final long today)
        {
            long sum = 0;
            final long advance = Math.max(0, today - windowDay);
            for (int i = 0; i < WINDOW_DAYS; i++)
            {
                // Read the window as it would look after rolling, without mutating it: a bucket that has aged past
                // the end of the window no longer counts.
                if (i + advance < WINDOW_DAYS)
                {
                    sum += window[i];
                }
            }
            return sum;
        }

        /**
         * How many days the window can honestly average over: the window width, or the item's whole life if it is
         * younger than that. Without this a stack delivered an hour ago and taken from once would be reported at a
         * seventh of its real rate.
         *
         * @param now the current game tick.
         * @return the divisor, never below one.
         */
        public double getWindowDays(final long now)
        {
            final double lived = Math.max(0, now - firstSeen) / (double) TICKS_PER_DAY;
            return Math.max(1.0, Math.min(WINDOW_DAYS, lived));
        }
    }

    /**
     * Read-only snapshot of one item type, already aggregated across every warehouse of a colony.
     */
    public static class Aggregate
    {
        /**
         * The item type.
         */
        public final ItemStorage item;

        /**
         * How much of it the colony holds across all its warehouses.
         */
        public int count = 0;

        /**
         * The earliest first-seen over the warehouses holding it.
         */
        public long firstSeen = Long.MAX_VALUE;

        /**
         * The latest withdrawal over the warehouses holding it, or {@link Long#MIN_VALUE} if no warehouse has ever
         * seen one.
         */
        public long lastTaken = Long.MIN_VALUE;

        /**
         * Whether any warehouse has ever seen this item type go down.
         */
        public boolean everTaken = false;

        /**
         * Withdrawals inside the rolling window, summed over the warehouses.
         */
        public long takenInWindow = 0;

        /**
         * Lifetime withdrawals, summed over the warehouses.
         */
        public long totalTaken = 0;

        /**
         * The widest window any contributing warehouse can honestly average over.
         */
        public double windowDays = 1.0;

        /**
         * How many warehouses hold this item type.
         */
        public int warehouses = 0;

        public Aggregate(final ItemStorage item)
        {
            this.item = item;
        }

        /**
         * Fold one warehouse's history for this item into the aggregate.
         *
         * @param item the history.
         * @param now  the current game tick.
         */
        public void add(final ItemHistory item, final long now)
        {
            count += item.getCount();
            // The earliest arrival anywhere in the colony. A copy of a thing the colony has held untouched for a
            // month being carried into a second warehouse must not make it look a day old.
            firstSeen = Math.min(firstSeen, item.getFirstSeen());
            // The latest withdrawal, not the earliest: an item drawn from warehouse A every day is in use, whatever
            // the untouched copy in warehouse B suggests. Idle age is a property of the item type, and the colony has
            // been using it.
            lastTaken = Math.max(lastTaken, item.getLastTaken());
            everTaken |= item.getLastTaken() >= 0;
            takenInWindow += item.getTakenInWindow(now / TICKS_PER_DAY);
            totalTaken += item.getTotalTaken();
            windowDays = Math.max(windowDays, item.getWindowDays(now));
            warehouses++;
        }

        /**
         * @param now the current game tick.
         * @return days since anybody took this item, or since it arrived if nobody ever has.
         */
        public double getIdleDays(final long now)
        {
            return Math.max(0, now - (everTaken ? lastTaken : firstSeen)) / (double) TICKS_PER_DAY;
        }

        /**
         * @param now the current game tick.
         * @return days since this item type first arrived.
         */
        public double getAgeDays(final long now)
        {
            return Math.max(0, now - firstSeen) / (double) TICKS_PER_DAY;
        }

        /**
         * @return the rolling withdrawal rate, in items per day.
         */
        public double getTakenPerDay()
        {
            return takenInWindow / windowDays;
        }
    }

    /**
     * Aggregate every warehouse of a colony into one list, newest-idle last.
     *
     * @param histories the per-warehouse histories.
     * @param now       the current game tick.
     * @return the aggregated rows, sorted by idle time descending.
     */
    public static List<Aggregate> aggregate(final List<Map<ItemStorage, ItemHistory>> histories, final long now)
    {
        final Map<ItemStorage, Aggregate> merged = new HashMap<>();
        for (final Map<ItemStorage, ItemHistory> history : histories)
        {
            for (final Map.Entry<ItemStorage, ItemHistory> entry : history.entrySet())
            {
                merged.computeIfAbsent(entry.getKey(), Aggregate::new).add(entry.getValue(), now);
            }
        }

        final List<Aggregate> rows = new ArrayList<>(merged.values());
        rows.sort((a, b) -> Double.compare(b.getIdleDays(now), a.getIdleDays(now)));
        return rows;
    }
}
