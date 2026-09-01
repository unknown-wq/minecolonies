package com.minecolonies.core.colony.buildings.workerbuildings;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.advancements.AdvancementTriggers;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.WorkOrderType;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.NBTUtils;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.territory.BorderPatrol;
import com.minecolonies.core.util.AdvancementUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_POS;
import static com.minecolonies.api.util.constant.TranslationConstants.WARNING_UPGRADE_BARRACKS;

/**
 * Building class for the Barracks Tower.
 */
@SuppressWarnings("squid:MaximumInheritanceDepth")
public class BuildingBarracksTower extends AbstractBuildingGuards
{
    ////// --------------------------- NBTConstants --------------------------- \\\\\\
    private static final String TAG_POS = "pos";
    ////// --------------------------- NBTConstants --------------------------- \\\\\\

    /**
     * Our constants. The Schematic names, Defence bonus, and Offence bonus.
     */
    private static final String SCHEMATIC_NAME = "barrackstower";

    /**
     * Position of the barracks for this tower.
     */
    private BlockPos barracks = null;

    /**
     * Which way along its slice of the border this tower's patrol is currently walking, +1 or -1.
     * <p>
     * Not saved: a reload starting the patrol off in the arbitrary direction it happens to have is indistinguishable
     * from it having turned round once more before the save.
     */
    private int patrolDirection = 1;

    /**
     * The abstract constructor of the building.
     *
     * @param c the colony
     * @param l the position
     */
    public BuildingBarracksTower(@NotNull final IColony c, final BlockPos l)
    {
        super(c, l);
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return SCHEMATIC_NAME;
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int getMaxBuildingLevel()
    {
        return 5;
    }

    @Override
    public void requestUpgrade(final Player player, final BlockPos builder)
    {
        final int buildingLevel = getBuildingLevel();
        final IBuilding building = getColony().getServerBuildingManager().getBuilding(barracks);

        if (building != null && buildingLevel < getMaxBuildingLevel() && buildingLevel < building.getBuildingLevel())
        {
            if (buildingLevel == 0)
            {
                requestWorkOrder(WorkOrderType.BUILD, builder);
            }
            else
            {
                requestWorkOrder(WorkOrderType.UPGRADE, builder);
            }
        }
        else
        {
            MessageUtils.format(WARNING_UPGRADE_BARRACKS).sendTo(player);
        }
    }

    /**
     * A tower belongs to its barracks rather than to a parent building, so
     * {@link com.minecolonies.core.colony.buildings.AbstractBuilding#requestUpgradeTo}'s
     * parent check does not see it and the barracks rule has to be repeated here: free mode lifts the research and
     * builder gates, it does not let a tower outgrow the barracks it is part of.
     *
     * @param player      the requesting player.
     * @param builder     the assigned builder.
     * @param targetLevel the level to end up at.
     */
    @Override
    public void requestUpgradeTo(final Player player, final BlockPos builder, final int targetLevel)
    {
        final IBuilding building = getColony().getServerBuildingManager().getBuilding(barracks);

        if (building == null || targetLevel > getMaxBuildingLevel() || targetLevel > building.getBuildingLevel())
        {
            MessageUtils.format(WARNING_UPGRADE_BARRACKS).sendTo(player);
            return;
        }

        super.requestUpgradeTo(player, builder, targetLevel);
    }

    @Override
    public boolean canDeconstruct()
    {
        return false;
    }

    @Override
    public int getClaimRadius(final int newLevel)
    {
        return 0;
    }

    @Override
    public void onUpgradeComplete(@Nullable final Blueprint blueprint, final int newLevel)
    {
        super.onUpgradeComplete(blueprint, newLevel);
        final IBuilding barrack = colony.getServerBuildingManager().getBuilding(barracks);
        if (barrack == null)
        {
            return;
        }

        if (newLevel == barrack.getMaxBuildingLevel())
        {
            boolean allUpgraded = true;
            for (BlockPos tower : ((BuildingBarracks) barrack).getTowers())
            {
                if (colony.getServerBuildingManager().getBuilding(tower).getBuildingLevel() != barrack.getMaxBuildingLevel())
                {
                    allUpgraded = false;
                }
            }

            if (allUpgraded)
            {
                AdvancementUtils.TriggerAdvancementPlayersForColony(colony, AdvancementTriggers.ALL_TOWERS.get()::trigger);
            }
        }
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);
        barracks = NBTUtils.readBlockPos(compound, TAG_POS);
    }

    @Override
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag compound = super.serializeNBT(provider);
        if (barracks != null)
        {
            compound.put(TAG_POS, NBTUtils.writeBlockPos(barracks));
        }

        return compound;
    }

    /**
     * Adds the position of the main barracks.
     *
     * @param pos the BlockPos.
     */
    public void addBarracks(final BlockPos pos)
    {
        barracks = pos;
    }

    /**
     * The barracks this tower belongs to, if it still exists.
     *
     * @return the barracks, or null.
     */
    @Nullable
    public BuildingBarracks getBarracks()
    {
        return barracks == null ? null : colony.getServerBuildingManager().getBuilding(barracks) instanceof final BuildingBarracks found ? found : null;
    }

    /**
     * Walk this tower's slice of the border, when the barracks has asked for one.
     *
     * <h2>What keeps the guard on his stretch</h2>
     * Three things, and none of them is a distance check on where he happens to be standing:
     * <ol>
     *   <li><b>Nothing else is ever offered.</b> While a border patrol is on, this is the only source of patrol
     *       points; the random 20-40 block wander and the "pick a building in the colony" branch of
     *       {@link AbstractBuildingGuards#getNextPatrolTarget} are both skipped. A point that does not exist cannot be
     *       walked to.</li>
     *   <li><b>Every point is inside a box.</b> The slice is cut out of a line that was only ever searched for within
     *       {@code BorderPatrol#SEARCH_RADIUS_CHUNKS} of the barracks, so no waypoint is further than 264 blocks from
     *       home, whatever the border does beyond that.</li>
     *   <li><b>The next point is always a neighbour of where he is.</b> The step below is taken from the waypoint
     *       nearest the patrol's current position, not from a remembered index, so it is at most one chunk away -- and
     *       a guard dragged off the line by a fight rejoins it at the closest point rather than at whatever point was
     *       next before the fight started.</li>
     * </ol>
     * The direction reverses at each end of the slice, which is what makes it a patrol rather than a one-way walk.
     *
     * @param from where the patrol currently stands.
     * @return the next waypoint, or null to fall back to the ordinary automatic patrol.
     */
    @Nullable
    @Override
    protected BlockPos getBorderPatrolTarget(@Nullable final BlockPos from)
    {
        final BuildingBarracks barracksBuilding = getBarracks();
        if (barracksBuilding == null)
        {
            return null;
        }

        final List<BlockPos> stretch = barracksBuilding.getStretchFor(getPosition());
        if (stretch.isEmpty())
        {
            // No border within reach, or the mode is off. Fall back to the ordinary patrol rather than standing still:
            // a guard with nothing to walk is worse than a guard walking the wrong thing, and the reason is reported
            // by /mc colony diagnose.
            return null;
        }

        final BlockPos anchor = from == null ? getPosition() : from;
        final int nearest = BorderPatrol.nearestIndex(stretch, anchor);

        if (stretch.size() == 1)
        {
            return BorderPatrol.surface(colony.getWorld(), stretch.get(0));
        }

        if (nearest + patrolDirection < 0 || nearest + patrolDirection >= stretch.size())
        {
            patrolDirection = -patrolDirection;
        }

        return BorderPatrol.surface(colony.getWorld(), stretch.get(nearest + patrolDirection));
    }

}
