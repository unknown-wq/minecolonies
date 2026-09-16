package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.Vec2i;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.workorders.WorkOrderMiner;
import com.minecolonies.core.entity.ai.workers.util.MinerLevel;
import com.minecolonies.core.entity.ai.workers.util.MineNode;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.BlockPos;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.util.constant.BuildingConstants.*;
import static com.minecolonies.core.entity.ai.workers.production.EntityAIStructureMiner.SHAFT_RADIUS;

/**
 * Module containing miner level management.
 */
public class MinerLevelManagementModule extends AbstractBuildingModule implements IPersistentModule
{
    /**
     * Stores the levels of the miners mine. This could be a map with (depth,level).
     */
    @NotNull
    private final List<MinerLevel> levels = new ArrayList<>();

    /**
     * The number of the current level.
     */
    private int currentLevel = 0;

    /**
     * The id of the activeNode node.
     */
    @Nullable
    private MineNode activeNode = null;

    /**
     * The id of the old node.
     */
    @Nullable
    private MineNode oldNode = null;

    /**
     * The first y level to start the shaft at.
     */
    private int startingLevelShaft = 0;

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        startingLevelShaft = compound.getIntOr(TAG_STARTING_LEVEL, 0);
        currentLevel = compound.getIntOr(TAG_CURRENT_LEVEL, 0);
        final ListTag levelTagList = compound.getListOrEmpty(TAG_LEVELS);
        for (int i = 0; i < levelTagList.size(); i++)
        {
            this.levels.add(new MinerLevel(levelTagList.getCompoundOrEmpty(i)));
        }

        if (compound.contains(TAG_ACTIVE))
        {
            activeNode = resolveAgainstCurrentLevel(MineNode.createFromNBT(compound.getCompoundOrEmpty(TAG_ACTIVE)));
        }
        else if (compound.contains(TAG_OLD))
        {
            oldNode = resolveAgainstCurrentLevel(MineNode.createFromNBT(compound.getCompoundOrEmpty(TAG_OLD)));
        }
    }

    /**
     * Turn a node that was just read out of NBT into the level's own node object.
     * <p>
     * The active node is stored a second time, next to the level it belongs to, so reading it back gave a third
     * copy of a node that the level already held: status changes made through it never reached the copy that gets
     * saved. The node always comes from the current level (that is the only place {@link #getActiveNode()} takes
     * one from), so it is looked up there; a node that is not in it any more is dropped rather than kept as a
     * detached copy, and the miner simply picks a new one.
     *
     * @param node the node read from NBT.
     * @return the level's own object for that position, or null.
     */
    @Nullable
    private MineNode resolveAgainstCurrentLevel(@Nullable final MineNode node)
    {
        if (node == null)
        {
            return null;
        }

        final MinerLevel level = getCurrentLevel();
        if (level == null)
        {
            Log.getLogger().warn("Minecolonies mine: dropping the stored node " + node.getX() + ":" + node.getZ()
                                   + ", level " + currentLevel + " of " + levels.size() + " is not there");
            return null;
        }

        final MineNode stored = level.getNode(new Vec2i(node.getX(), node.getZ()));
        if (stored == null)
        {
            Log.getLogger().warn("Minecolonies mine: dropping the stored node " + node.getX() + ":" + node.getZ()
                                   + ", it is not part of level " + currentLevel + " (depth " + level.getDepth() + ") any more");
        }
        return stored;
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        compound.putInt(TAG_STARTING_LEVEL, startingLevelShaft);
        compound.putInt(TAG_CURRENT_LEVEL, currentLevel);
        @NotNull final ListTag levelTagList = new ListTag();
        for (@NotNull final MinerLevel level : levels)
        {
            @NotNull final CompoundTag levelCompound = new CompoundTag();
            level.write(levelCompound);
            levelTagList.add(levelCompound);
        }
        compound.put(TAG_LEVELS, levelTagList);

        if (activeNode != null)
        {
            final CompoundTag nodeCompound = new CompoundTag();
            activeNode.write(nodeCompound);
            compound.put(TAG_ACTIVE, nodeCompound);
        }

        if (oldNode != null)
        {
            final CompoundTag nodeCompound = new CompoundTag();
            oldNode.write(nodeCompound);
            compound.put(TAG_OLD, nodeCompound);
        }
    }

    @Override
    public void serializeToView(final RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(currentLevel);
        buf.writeInt(levels.size());

        for (@NotNull final MinerLevel level : levels)
        {
            buf.writeInt(level.getNumberOfBuiltNodes());
            buf.writeInt(level.getDepth());
        }

        final List<WorkOrderMiner> list = building.getColony().getWorkManager().getOrderedList(WorkOrderMiner.class, building.getPosition());
        buf.writeInt(list.size());
        for (@NotNull final WorkOrderMiner wo : list)
        {
            wo.serializeViewNetworkData(buf);
        }
    }

    /**
     * Adds a level to the levels list.
     *
     * @param currentLevel {@link MinerLevel} to add.
     */
    public void addLevel(final MinerLevel currentLevel)
    {
        levels.add(currentLevel);
    }

    /**
     * The number of levels in the mine.
     *
     * @return levels size.
     */
    public int getNumberOfLevels()
    {
        return levels.size();
    }

    /**
     * Returns the current level.
     *
     * @return Current level.
     */
    @Nullable
    public MinerLevel getCurrentLevel()
    {
        if (currentLevel >= 0 && currentLevel < levels.size())
        {
            return levels.get(currentLevel);
        }
        return null;
    }

    /**
     * Find given level in the levels array.
     *
     * @param level the level.
     * @return position in the levels array.
     */
    public int getLevelId(final MinerLevel level)
    {
        return levels.indexOf(level);
    }

    /**
     * Sets the current level the miner is at.
     *
     * @param currentLevel the level to set.
     */
    public void setCurrentLevel(final int currentLevel)
    {
        if (currentLevel < 0 || currentLevel >= levels.size())
        {
            // The index comes straight off a GUI packet, the same way repairLevel's does, and a level that is out
            // of range would be stored as-is: getCurrentLevel() then hands back null for good, and a negative one
            // threw out of getActiveNode's levels.get.
            return;
        }

        this.currentLevel = currentLevel;
        this.activeNode = null;
        this.oldNode = null;
    }

    /**
     * Getter of the starting level of the shaft. (Y position).
     *
     * @return the start level.
     */
    public int getStartingLevelShaft()
    {
        if (levels.isEmpty())
        {
            return startingLevelShaft;
        }
        else
        {
            return levels.get(levels.size() - 1).getDepth() - 6;
        }
    }

    /**
     * Getter for the active node.
     *
     * @return the int id of the active node.
     */
    @Nullable
    public MineNode getActiveNode()
    {
        if (levels.isEmpty())
        {
            return null;
        }

        MineNode calcNode = activeNode;
        if (activeNode == null || activeNode.getStatus() == MineNode.NodeStatus.COMPLETED)
        {
            if (currentLevel >= levels.size())
            {
                currentLevel = levels.size() - 1;
            }
            if (currentLevel < 0)
            {
                currentLevel = 0;
            }
            calcNode = levels.get(currentLevel).getRandomNode(oldNode);
        }

        if (activeNode != calcNode)
        {
            activeNode = calcNode;
        }
        return activeNode;
    }

    /**
     * Read the active node without picking a new one.
     * <p>
     * {@link #getActiveNode()} is not a getter: when there is no active node it takes a fresh one out of the
     * current level and stores it. That is what the mining AI wants when it is looking for work, and it is exactly
     * wrong for everybody else -- the pathfinding proxy asking which node the miner is at, or the build-completion
     * code asking which node was just dug, would silently invent one. In particular, switching level in the GUI
     * clears the active node, so a completion that asked the lazy getter got an untouched node of the newly
     * selected level and closed that instead.
     *
     * @return the active node, or null if there is none.
     */
    @Nullable
    public MineNode peekActiveNode()
    {
        return activeNode;
    }

    /**
     * Find the level a node object belongs to.
     *
     * @param node the node.
     * @return the level holding this very node object, or null if no level does.
     */
    @Nullable
    public MinerLevel getLevelForNode(@Nullable final MineNode node)
    {
        if (node == null)
        {
            return null;
        }

        for (final MinerLevel level : levels)
        {
            if (level.holds(node))
            {
                return level;
            }
        }
        return null;
    }

    /**
     * Whether any level of this mine still has a node left to dig.
     *
     * @return true if at least one level has an open node.
     */
    public boolean hasOpenNodes()
    {
        for (final MinerLevel level : levels)
        {
            if (level.hasOpenNodes())
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the first level that still has a node left to dig.
     *
     * @return the index of that level, or -1 if the whole mine is dug out.
     */
    public int getFirstLevelWithOpenNodes()
    {
        for (int i = 0; i < levels.size(); i++)
        {
            if (levels.get(i).hasOpenNodes())
            {
                return i;
            }
        }
        return -1;
    }

    /**
     * Setter for the active node.
     *
     * @param activeNode the int id of the active node.
     */
    public void setActiveNode(@Nullable final MineNode activeNode)
    {
        this.activeNode = activeNode;
    }

    /**
     * Setter for the old node.
     *
     * @param oldNode the int id of the old node.
     */
    public void setOldNode(@Nullable final MineNode oldNode)
    {
        this.oldNode = oldNode;
    }

    /**
     * Resets the starting level of the shaft to 0.
     *
     * @param level the level o set it to.
     */
    public void setStartingLevelShaft(final int level)
    {
        this.startingLevelShaft = level;
    }

    /**
     * Repair the level.
     * @param level the level to repair.
     */
    public void repairLevel(final int level)
    {
        if (level < 0 || level >= levels.size())
        {
            // The index comes straight off a GUI packet and names a level that may already be gone; ignore it rather
            // than letting an IndexOutOfBoundsException out of the packet handler.
            return;
        }

        if (building instanceof BuildingMiner)
        {
            final BlockPos ladderPos = ((BuildingMiner) building).getLadderLocation();
            final BlockPos vector = ladderPos.subtract(((BuildingMiner) building).getCobbleLocation());
            final int xOffset = SHAFT_RADIUS * vector.getX();
            final int zOffset = SHAFT_RADIUS * vector.getZ();

            BuildingMiner.initStructure(null,
              new BlockPos(ladderPos.getX() + xOffset, levels.get(level).getDepth(), ladderPos.getZ() + zOffset),
              (BuildingMiner) building,
              building.getColony().getWorld(),
              null);
        }
    }

    /**
     * Get the list of levels.
     * @return the list.
     */
    public List<MinerLevel> getLevels()
    {
        return levels;
    }
}
