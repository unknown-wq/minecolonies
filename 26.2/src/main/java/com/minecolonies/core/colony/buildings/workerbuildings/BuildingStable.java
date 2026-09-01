package com.minecolonies.core.colony.buildings.workerbuildings;

import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.modules.settings.PatrolIntervalSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import net.minecraft.util.RandomSource;
import com.minecolonies.api.util.Tuple;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.item.Items;

import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.SchematicTagConstants.TAG_GROUNDLEVEL;
import static com.minecolonies.api.util.constant.SchematicTagConstants.TAG_PATROL_POINT;

/**
 * Building of the stable.
 * Supports cavalry military units and the Stablemaster job.
 */
public class BuildingStable extends AbstractBuildingGuards
{

    public static final float CAVALRY_PATROL_RANGE_BOOST = 1.5f;

    /**
     * Tag for the structurize tags designating stall positions.
     */
    private final static String STALL_STRUCTURE_TAG = "stall";

    /**
     * NBT tag for the last time the guards patrolled from this stable.
     */
    private static final String NBT_LAST_PATROL_TAG    = "lastPatrolTime";

    /**
     * Setting key for the patrol interval.
     */
    public static final ISettingKey<PatrolIntervalSetting> PATROL_INTERVAL =
      new SettingKey<>(PatrolIntervalSetting.class, Identifier.fromNamespaceAndPath(com.minecolonies.api.util.constant.Constants.MOD_ID, "patrolinterval"));

    /**
     * How long a single sortie is allowed to last before it is written off, in minutes.
     * <p>
     * The patrol timer already ends a leg roughly two minutes after it was handed out, so this only catches the case
     * where the timer is not running at all - a colony that was unloaded mid-leg - and stops a unit being counted as
     * "out on patrol" for the rest of the save.
     */
    private static final int MAX_SORTIE_MINUTES = 5;

    /**
     * Patrol timer to set while the cavalry is resting, in colony ticks.
     * <p>
     * {@code patrolTimer} is counted down once per colony tick, which is one slow tick of the colony state machine
     * (500 game ticks, about 25 seconds) - not once per game tick. Two of them is a recheck about every 50 seconds,
     * which is fine enough for an interval expressed in whole minutes and cheap enough to run all day.
     */
    private static final int REST_RECHECK_TICKS = 2;

    /**
     * The last time the guards finished a patrol from this stable, i.e. when the current rest window started.
     */
    private long lastPatrolTime = 0;

    /**
     * When the sortie now under way was dispatched, or 0 when the cavalry is not out on one.
     * <p>
     * Deliberately not saved: a colony that is reloaded has no cavalry standing anywhere in particular, so the
     * honest state to come back in is "resting", which is what a zero here means.
     */
    private long patrolStartTime = 0;

    /**
     * The last stable position used.
     */
    private int lastStable = -1;
    
    /**
     * Constructor.
     * 
     * @param colony the colony.
     * @param pos the position of the building.
     */
    public BuildingStable(@NotNull IColony colony, BlockPos pos)
    {
        super(colony, pos);
    }

    /**
     * Gets the schematic name of the building.
     *
     * @return the schematic name of the building.
     */
    @Override
    public String getSchematicName()
    {
        return ModBuildings.STABLE_ID;
    }

    /**
     * The herding module for the stable.
     */
    public static class HerdingModule extends AnimalHerdingModule
    {

        public HerdingModule()
        {
            super(ModJobs.stablemaster.get(), a -> a instanceof Horse, new ItemStorage(Items.GOLDEN_APPLE, 2), EntityTypes.HORSE);
        }
    }

    /**
     * Reads the tag positions
     */
    public List<BlockPos> stallPositions()
    {
        List<BlockPos> stallPositions = getLocationsFromTag(STALL_STRUCTURE_TAG);
        
        if (stallPositions.isEmpty())
        {
            Log.getLogger().warn("Colony {} has a stable with no stall positions (blueprint {}) at {}. Use the '" + STALL_STRUCTURE_TAG + "' tag to add some.", 
                getColony().getID(), getBlueprintPath(), getPosition());
        }

        return stallPositions;
    }

    /**
     * Gets the next stable position to use for a horse. Just keeps iterating the aviable positions, 
     * so we do not have to keep track of what horse is where.
     *
     * @return horse stable position
     */
    public BlockPos getNextStallPosition()
    {
        List<BlockPos> stallPositions = stallPositions();

        if (stallPositions.isEmpty())
        {
            return null;
        }

        lastStable++;

        if (lastStable >= stallPositions.size())
        {
            lastStable = 0;
        }

        return stallPositions.get(lastStable);
    }

    /**
     * Deserializes the compound tag and sets the last patrol time.
     * @param compound the compound tag to read from.
     */
    @Override
    public void deserializeNBT(final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);
        this.lastPatrolTime = compound.getLongOr(NBT_LAST_PATROL_TAG, 0L);
    }

    /**
     * Serializes the data of this building to NBT.
     * @return the serialized compound tag.
     */
    @Override
    public CompoundTag serializeNBT(final HolderLookup.Provider provider)
    {        
        final CompoundTag compound = super.serializeNBT(provider);
        compound.putLong(NBT_LAST_PATROL_TAG, lastPatrolTime);
        return compound;
    }

    /**
     * Returns the task that the guards should perform when patrolling.
     * <p>
     * This can be either 'patrol', 'patrol_mine', or 'follow'.
     * <p>
     * The task is determined by the setting in the Stable Settings module.
     * @return the task to perform when patrolling
     */
    @Override
    public String getTask()
    {
        return getModule(BuildingModules.STABLE_SETTINGS).getSetting(GUARD_TASK).getValue();
    }

    /**
     * Gets the last time the guards patrolled from this stable.
     * @return the game time of when the guards last patrolled.
     */    
    public long getLastPatrolTime()
    {
        return lastPatrolTime;
    }

    /**
     * Sets the last time the guards patrolled from this stable.
     * @param lastPatrolTime the time in milliseconds since the epoch
     */
    public void setLastPatrolTime(long lastPatrolTime)
    {
        this.lastPatrolTime = lastPatrolTime;
    }

    /**
     * Returns the time in minutes since the last patrol from this stable.
     * This is based on the game time of the world.
     * @return the time in minutes since the last patrol.
     */
    public int minutesSinceLastPatrol()
    {
        return minutesSince(lastPatrolTime);
    }

    /**
     * The number of whole minutes of world time that have passed since a game time stamp.
     *
     * @param since the game time to measure from.
     * @return the elapsed time in minutes, never negative.
     */
    private int minutesSince(final long since)
    {
        final long ticks = this.getColony().getWorld().getGameTime() - since;
        return ticks <= 0 ? 0 : (int) (ticks / TICKS_SECOND / 60);
    }

    /**
     * Gets the patrol distance for cavalry guards assigned to this stable.
     * This range is based on the base patrol distance of guards, and is multiplied by a constant to
     * give cavalry guards a wider patrol range.
     * @return the patrol distance for cavalry guards assigned to this stable.
     */
    @Override
    public int getPatrolDistance()
    {
        int patrolDistance = super.getPatrolDistance();

        return (int) (patrolDistance * CAVALRY_PATROL_RANGE_BOOST);
    }

    /**
     * The number of minutes the cavalry waits at the stable between sorties.
     *
     * @return the configured interval, in minutes.
     */
    public int getPatrolInterval()
    {
        return PatrolIntervalSetting.clamp(getSetting(PATROL_INTERVAL).getValue());
    }

    /**
     * Whether the cavalry should be waiting at the stable rather than walking a patrol.
     * <p>
     * This is the one place that answers the question. It used to be asked twice - here and again in the cavalry AI -
     * against {@link #minutesSinceLastPatrol()}, and {@code startPatrolNext} reset that clock at the moment it handed
     * out a patrol point. So the clock the AI consulted was reset by the act of dispatching the patrol: the unit was
     * given a destination and, on its very next AI tick, told it was inside the rest window and sent back to loiter
     * at the stable without ever walking the leg. The clock now starts when a sortie <em>ends</em>, and a sortie in
     * progress is a state of its own rather than something inferred from a timestamp.
     *
     * @return true if the cavalry should stay at the stable.
     */
    public boolean restingAtStable()
    {
        if (getTask().equals(GuardTaskSetting.PATROL_PERMANENT))
        {
            // A permanent patrol never stands down. Eating, sleeping and fighting still take the unit off the route,
            // because those interrupt the guard AI itself rather than going through the patrol task.
            return false;
        }

        if (patrolStartTime > 0)
        {
            if (minutesSince(patrolStartTime) < MAX_SORTIE_MINUTES)
            {
                return false;
            }

            endPatrol();
        }

        return minutesSinceLastPatrol() < getPatrolInterval();
    }

    /**
     * Ends the sortie now under way and opens the rest window.
     */
    private void endPatrol()
    {
        patrolStartTime = 0;
        setLastPatrolTime(getColony().getWorld().getGameTime());
    }

    /**
     * Initiate the next patrol.
     * <p>
     * Called when a patrol leg has been walked (every assigned guard reached the point) and when the patrol timer
     * runs out part way through one. Either way the leg is over, so this is where a sortie ends and where the next
     * one is dispatched once the rest window has passed.
     */
    @Override
    public void startPatrolNext()
    {
        if (patrolStartTime > 0)
        {
            endPatrol();
        }

        if (restingAtStable())
        {
            setPatrolTimer(REST_RECHECK_TICKS);
            return;
        }

        patrolStartTime = getColony().getWorld().getGameTime();
        super.startPatrolNext();
    }

    /**
     * Get a patrol target.
     */
    @Override
    protected BlockPos getRandomPatrolTarget()
    {
        BlockPos buildingPos = getColony().getServerBuildingManager().getRandomBuilding(cavalryPatrolFilter());

        // A colony with one stable and no gate house has nothing in that filter but this building, so every leg of
        // the patrol ended where it started and the cavalry never left the yard. Fall back to the ordinary guard
        // target in that case: the filter is there to give cavalry a preference for the colony's approaches, not to
        // confine it to a route that does not exist yet.
        if (buildingPos == null || buildingPos.equals(getPosition()))
        {
            buildingPos = getColony().getServerBuildingManager().getRandomBuilding(b -> b.getBuildingLevel() >= 1);
        }

        return patrolPointForBuilding(buildingPos);
    }

    /**
     * If the building structure includes potential patrol points, pick one and use it.
     * Otherwise, use the hut (or tagged ground-level) Y and nominate one of the exterior corners.
     *
     * @param targetPos the building position to patrol.
     * @return a patrol point designated by a tag, a building corner, or the target position.
     */
    public BlockPos patrolPointForBuilding(final BlockPos targetPos)
    {
        if (targetPos == null || BlockPos.ZERO.equals(targetPos))
        {
            return null;
        }

        IBuilding targetBuilding = getColony().getServerBuildingManager().getBuilding(targetPos);

        if (targetBuilding == null)
        {
            return targetPos;
        }

        final List<BlockPos> patrolPoints = targetBuilding.getLocationsFromTag(TAG_PATROL_POINT);
        final RandomSource rand = getColony().getWorld().getRandom();

        if (patrolPoints != null && !patrolPoints.isEmpty())
        {
            return patrolPoints.get(rand.nextInt(patrolPoints.size()));
        }

        if (targetBuilding.getParent() != null && !BlockPos.ZERO.equals(targetBuilding.getParent()))
        {
            return patrolPointForBuilding(targetBuilding.getParent());
        }

        final List<BlockPos> groundLevel = targetBuilding.getLocationsFromTag(TAG_GROUNDLEVEL);
        final int groundY =
            (groundLevel != null && !groundLevel.isEmpty()) ? groundLevel.get(0).getY() : targetBuilding.getPosition().below().getY();

        final Tuple<BlockPos, BlockPos> corners = targetBuilding.getCorners();
        if (corners == null)
        {
            final BlockPos hut = targetBuilding.getPosition();
            return new BlockPos(hut.getX(), groundY, hut.getZ());
        }

        final BlockPos a = corners.getA();
        final BlockPos b = corners.getB();

        switch (rand.nextInt(4))
        {
            case 0:
                return new BlockPos(a.getX(), groundY, a.getZ());
            case 1:
                return new BlockPos(a.getX(), groundY, b.getZ());
            case 2:
                return new BlockPos(b.getX(), groundY, b.getZ());
            default:
                return new BlockPos(b.getX(), groundY, a.getZ());
        }
    }

    /*
     * Filter for buildings that cavalry patrols.
     */
    public static Predicate<IBuilding> cavalryPatrolFilter()
    {
        return b -> b instanceof BuildingStable || b instanceof BuildingGateHouse;
    }
}
