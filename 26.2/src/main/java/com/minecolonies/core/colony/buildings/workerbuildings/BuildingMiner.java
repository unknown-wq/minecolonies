package com.minecolonies.core.colony.buildings.workerbuildings;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.google.common.collect.ImmutableList;
import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.AbstractBuildingStructureBuilder;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.buildings.modules.settings.BlockSetting;
import com.minecolonies.core.colony.buildings.modules.settings.IntSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.colony.jobs.JobMiner;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.entity.ai.workers.util.MineNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import com.minecolonies.api.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.minecolonies.api.util.constant.BuildingConstants.TAG_CLOCATION;
import static com.minecolonies.api.util.constant.BuildingConstants.TAG_LLOCATION;
import static com.minecolonies.api.util.constant.Constants.STACKSIZE;
import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;
import static com.minecolonies.api.util.constant.SchematicTagConstants.TAG_COBBLE;
import static com.minecolonies.api.util.constant.SchematicTagConstants.TAG_LADDER;

/**
 * The miners building.
 */
public class BuildingMiner extends AbstractBuildingStructureBuilder
{
    /**
     * Setting for solid filling block.
     */
    public static final ISettingKey<BlockSetting> FILL_BLOCK = new SettingKey<>(BlockSetting.class, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "fillblock"));

    /**
     * Max depth the miner is going for.
     */
    public static final ISettingKey<IntSetting> MAX_DEPTH = new SettingKey<>(IntSetting.class, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "maxdepth"));

    /**
     * Mine height levels:
     * 48: Copper
     * 16: Iron
     * -16: Gold
     * -100: Diamond
     */
    private static final List<Integer> MINING_LEVELS = ImmutableList.copyOf(new Integer[] {48, 16, -16, -100});

    /**
     * The job description.
     */
    private static final String MINER = "miner";

    /**
     * The location of the topmost cobblestone the ladder starts at.
     */
    private BlockPos cobbleLocation;

    /**
     * The location of the topmost ladder in the shaft.
     */
    private BlockPos ladderLocation;

    /**
     * How long the shaft has been making no progress, in ticks, and where it stopped -- or zero and null while the
     * shaft is healthy. Written by {@code EntityAIStructureMiner}'s watchdog and read by {@code /mc colony
     * diagnose}, which had no way of telling a mine that was working from one that had been standing still for a
     * hundred thousand ticks: the AI state ticks over either way and a stalled shaft files no requests.
     * <p>
     * Deliberately not serialised. It is a live observation about a worker that is running right now; after a
     * restart the watchdog re-establishes it within its own timeout, and a value restored from disk would only be
     * able to lie.
     */
    private long shaftStallTicks = 0;

    private BlockPos shaftStallPos = null;

    /**
     * The rest of the watchdog's working state, on the building rather than on the worker AI.
     * <p>
     * It started out on {@code EntityAIStructureMiner}, which is where the logic lives and looked like the natural
     * home for it. The server disagreed: a shaft flooded with lava kills miners, the colony hires a replacement
     * within a few hundred ticks, and each replacement brought a brand new AI object with the clock back at zero.
     * Measured over four twenty-thousand-tick runs on a dead shaft the clock reached 9660, then restarted at 630,
     * then 9500, then 11000 -- it never once crossed the ten-minute threshold, precisely because the failure it
     * exists to report is also a failure that keeps killing the miner watching for it. The shaft is a property of
     * the mine, not of whoever happens to be standing in it, so the clock belongs here.
     * <p>
     * Not serialised, for the same reason {@link #shaftStallTicks} is not.
     */
    private int shaftProgressLadder = Integer.MIN_VALUE;

    private long shaftLastVisit = 0;

    private long shaftLastRetry = 0;

    private boolean shaftStallReported = false;

    /**
     * Required constructor.
     *
     * @param c colony containing the building.
     * @param l location of the building.
     */
    public BuildingMiner(final IColony c, final BlockPos l)
    {
        super(c, l);

        final ItemStack stackLadder = new ItemStack(Blocks.LADDER);
        final ItemStack stackTorch = new ItemStack(Blocks.TORCH);
        final ItemStack stackCobble = new ItemStack(Blocks.COBBLESTONE);

        keepX.put(stack -> ItemStack.isSameItem(stackLadder, stack), new Tuple<>(STACKSIZE, true));
        keepX.put(stack -> ItemStack.isSameItem(stackTorch, stack), new Tuple<>(STACKSIZE, true));
        keepX.put(stack -> ItemStack.isSameItem(stackCobble, stack), new Tuple<>(STACKSIZE, true));

        keepX.put(itemStack -> ItemStackUtils.hasEquipmentLevel(itemStack, ModEquipmentTypes.pickaxe.get(), TOOL_LEVEL_WOOD_OR_GOLD, getMaxEquipmentLevel()), new Tuple<>(1, true));
        keepX.put(itemStack -> ItemStackUtils.hasEquipmentLevel(itemStack, ModEquipmentTypes.shovel.get(), TOOL_LEVEL_WOOD_OR_GOLD, getMaxEquipmentLevel()), new Tuple<>(1, true));
        keepX.put(itemStack -> ItemStackUtils.hasEquipmentLevel(itemStack, ModEquipmentTypes.axe.get(), TOOL_LEVEL_WOOD_OR_GOLD, getMaxEquipmentLevel()), new Tuple<>(1, true));
        keepX.put(itemStack -> ItemStackUtils.hasEquipmentLevel(itemStack, ModEquipmentTypes.shears.get(), TOOL_LEVEL_WOOD_OR_GOLD, getMaxEquipmentLevel()), new Tuple<>(1, true));

        keepX.put(itemStack -> ItemStackUtils.compareItemStacksIgnoreStackSize(itemStack, new ItemStack(getSetting(FILL_BLOCK).getValue())), new Tuple<>(STACKSIZE, true));
    }

    /**
     * Getter of the structure name.
     *
     * @return the structure name.
     */
    @NotNull
    @Override
    public String getSchematicName()
    {
        return MINER;
    }

    /**
     * Getter of the max building level.
     *
     * @return the integer.
     */
    @Override
    public int getMaxBuildingLevel()
    {
        return MAX_BUILDING_LEVEL;
    }

    /**
     * The Miner wants to get multiple nodes/levels worth of stuff when requesting.
     */
    @Override
    public int getResourceBatchMultiplier() 
    {
        if (getModuleMatching(WorkerBuildingModule.class, m -> m.getJobEntry() == ModJobs.quarrier.get()).getAssignedCitizen().isEmpty())
        {
            //Ask for 4x the resources if possible
            return 4;
        }
        return 1;
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);

        ladderLocation = BlockPosUtil.readOrNull(compound, TAG_LLOCATION);
        cobbleLocation = BlockPosUtil.readOrNull(compound, TAG_CLOCATION);
    }

    @Override
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag compound = super.serializeNBT(provider);

        BlockPosUtil.writeOptional(compound, TAG_CLOCATION, cobbleLocation);
        BlockPosUtil.writeOptional(compound, TAG_LLOCATION, ladderLocation);

        return compound;
    }

    /**
     * Returns the depth limit. Limitted by building level.
     * <pre>
     * - Level 1: 50
     * - Level 2: 20
     * - Level 3: 0
     * </pre>
     *
     * @return Depth limit.
     */
    public int getDepthLimit(final Level level)
    {
        int buildingY = this.getLadderLocation().getY() - 5;

        int buildingLevels = getBuildingLevel();
        int yLevel = 0;
        for (final Integer miningLevel : MINING_LEVELS)
        {
            if (miningLevel < buildingY)
            {
                yLevel = miningLevel;
                buildingLevels--;
            }

            if (buildingLevels == 0)
            {
                break;
            }
        }

        return normalizeMaxDepth(yLevel, level);
    }

    /**
     * Normalize the maximum depth.
     * Make sure that the returned depth respects the world limits and follows the building setting..
     * @param max the max depth of the given building level.
     * @param level the world.
     * @return the max.
     */
    public int normalizeMaxDepth(final int max, final Level level)
    {
        final int worldMaxDepth = level.getMinY() + 5;
        final IntSetting maxDepth = getSetting(MAX_DEPTH);
        if (maxDepth.getValue() == maxDepth.getDefault())
        {
            return Math.max(worldMaxDepth, max);
        }
        return Math.max(worldMaxDepth, Math.max(max, maxDepth.getValue()));
    }

    /**
     * Getter of the ladderLocation.
     *
     * @return the ladder location.
     */
    public BlockPos getLadderLocation()
    {
        if (ladderLocation == null)
        {
            loadLadderPos();
        }

        return ladderLocation;
    }

    /**
     * Getter of the cobbleLocation.
     *
     * @return the location.
     */
    public BlockPos getCobbleLocation()
    {
        if (cobbleLocation == null)
        {
            loadLadderPos();
        }

        return cobbleLocation;
    }

    /**
     * What the shaft watchdog wants the miner to do after one visit to the working face.
     *
     * @param reaim  drop the block being aimed at and the position chosen to stand on, and look again.
     * @param report say so to the player, once per stall.
     */
    public record ShaftWatch(boolean reaim, boolean report) {}

    /**
     * Advance the shaft watchdog by one visit from the miner.
     * <p>
     * The only thing that has to change for a shaft to be alive is the bottom of the ladder; everything else the
     * miner does down there can carry on indefinitely without the shaft getting one block deeper. So that is what
     * is watched, and the clock is reset the moment it moves.
     * <p>
     * The thresholds are the caller's, because they were measured against the caller's behaviour, not the
     * building's. The clock is the building's, because a stalled shaft outlives the miner who found it.
     *
     * @param lastLadder the y of the bottom of the ladder right now.
     * @param now        the game time.
     * @param gapLimit   the longest gap between two visits that still counts as continuous work on the shaft.
     * @param retryTicks how long without progress before the miner should re-aim.
     * @param stallTicks how long without progress before the player should be told.
     * @return what to do.
     */
    public ShaftWatch tickShaftWatch(final int lastLadder, final long now, final int gapLimit, final int retryTicks, final int stallTicks)
    {
        if (lastLadder != shaftProgressLadder)
        {
            shaftProgressLadder = lastLadder;
            shaftStallTicks = 0;
            shaftLastRetry = 0;
            shaftStallReported = false;
            shaftLastVisit = now;
            shaftStallPos = null;
            return new ShaftWatch(false, false);
        }

        final long sinceLastVisit = now - shaftLastVisit;
        shaftLastVisit = now;
        if (sinceLastVisit > 0 && sinceLastVisit <= gapLimit)
        {
            shaftStallTicks += sinceLastVisit;
        }

        boolean reaim = false;
        if (shaftStallTicks - shaftLastRetry >= retryTicks)
        {
            shaftLastRetry = shaftStallTicks;
            reaim = true;
        }

        if (shaftStallTicks < stallTicks)
        {
            // Below the alarm the clock is the watchdog's own business; the building only advertises a stall it is
            // prepared to stand behind, so that /mc colony diagnose never reports a shaft that is merely slow.
            shaftStallPos = null;
            return new ShaftWatch(reaim, false);
        }

        shaftStallPos = ladderLocation == null ? null : new BlockPos(ladderLocation.getX(), lastLadder, ladderLocation.getZ());
        final boolean report = !shaftStallReported;
        shaftStallReported = true;
        return new ShaftWatch(reaim, report);
    }

    /**
     * @return ticks the shaft has been making no progress, zero if it is healthy or merely slow.
     */
    public long getShaftStallTicks()
    {
        return shaftStallPos == null ? 0 : shaftStallTicks;
    }

    /**
     * @return where the shaft stopped, null if it is healthy.
     */
    public BlockPos getShaftStallPos()
    {
        return shaftStallPos;
    }

    private void loadLadderPos()
    {
        final Map<String, Set<BlockPos>> map = getTileEntity().getWorldTagNamePosMap();
        final Set<BlockPos> cobblePos = map.getOrDefault(TAG_COBBLE, new HashSet<>());
        final Set<BlockPos> ladderPos = map.getOrDefault(TAG_LADDER, new HashSet<>());
        if (cobblePos.isEmpty() || ladderPos.isEmpty())
        {
            return;
        }
        cobbleLocation = cobblePos.iterator().next();
        ladderLocation = ladderPos.iterator().next();
    }

    /**
     * Initiates structure loading.
     *
     * @param mineNode     the node to load it for.
     * @param rotateTimes  The amount of time to rotate the structure.
     * @param structurePos The position of the structure.
     */
    public static void initStructure(final MineNode mineNode, final BlockPos structurePos, final BuildingMiner buildingMiner, final Level world, final JobMiner job)
    {
        final String structurePack = buildingMiner.getStructurePack();
        RotationMirror rotMir;
        final String style;

        if (mineNode == null)
        {
            rotMir = getRotationFromVector(buildingMiner);
            style = MineNode.NodeType.SHAFT.getSchematicName();
        }
        else
        {
            rotMir = mineNode.getRotationMirror().orElse(RotationMirror.NONE);
            style = mineNode.getStyle().getSchematicName();
        }

        if (job == null || buildingMiner.getWorkOrder() == null)
        {
            final WorkOrderMiner wo = new WorkOrderMiner(structurePack, style + ".blueprint", style, rotMir, structurePos, false, buildingMiner.getPosition());
            wo.setClaimedBy(buildingMiner.getPosition());
            buildingMiner.getColony().getWorkManager().addWorkOrder(wo, false);
            if (job != null)
            {
                buildingMiner.setWorkOrder(wo);
            }
            else
            {
                wo.setClaimedBy(buildingMiner.getPosition());
            }
        }
        buildingMiner.markDirty();
    }

    /**
     * Return number of rotation for our building, for the main shaft.
     *
     * @return the rotation.
     */
    private static RotationMirror getRotationFromVector(final BuildingMiner buildingMiner)
    {
        final BlockPos vector = buildingMiner.getLadderLocation().subtract(buildingMiner.getCobbleLocation());

        if (vector.getX() == 1)
        {
            return RotationMirror.R90;
        }
        else if (vector.getZ() == 1)
        {
            return RotationMirror.R180;
        }
        else if (vector.getX() == -1)
        {
            return RotationMirror.R270;
        }
        else if (vector.getZ() == -1)
        {
            return RotationMirror.NONE;
        }
        return RotationMirror.NONE;
    }
}
