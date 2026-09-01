package com.minecolonies.core.colony.buildings.workerbuildings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.settings.GuardPatrolModeSetting;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.modules.settings.PatrolIntervalSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import com.minecolonies.core.colony.territory.BorderPatrol;

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
import static com.minecolonies.api.util.constant.TranslationConstants.STABLE_BORDER_UNREACHABLE;
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
     * How long a computed border stretch is kept before it is worked out again, in game ticks.
     * <p>
     * The same five minutes the barracks uses, for the same reason: the line only moves when the claim does, and
     * between recomputes handing a rider his next waypoint is a list index rather than a chunk scan.
     */
    private static final int BORDER_PLAN_TTL_TICKS = 6000;

    /**
     * The border stretch currently being walked, or null when the task is not a border patrol.
     */
    private BorderPatrol.Plan borderPlan = null;

    /**
     * The game time {@link #borderPlan} was computed at.
     */
    private long borderPlanTime = Long.MIN_VALUE;

    /**
     * The riders {@link #borderSlices} was cut for, in ascending id order, so that hiring or losing one re-cuts it.
     */
    private List<Integer> borderPlanRiders = List.of();

    /**
     * One contiguous arc of the stretch per rider, by citizen id.
     * <p>
     * This, and not a saved cursor, is what keeps the troop spread out. Two riders cannot be walking the same piece
     * of frontier because their pieces do not overlap, which makes the crowd unrepresentable rather than merely
     * unlikely - the same property the barracks gets from giving each tower its own slice, moved down a level
     * because a Stable is one building with a whole troop in it. It is derived from the rider list and the line, so
     * nothing has to be saved, kept in step with a recompute, or repaired when a rider is replaced.
     */
    private final Map<Integer, List<BlockPos>> borderSlices = new HashMap<>();

    /**
     * Which way along his own arc each rider is currently walking, by citizen id.
     * <p>
     * Deliberately not saved. A rider whose direction is forgotten walks the arc he is standing on from wherever he
     * is standing, which is what he would have done anyway; persisting it would buy nothing and would have to be
     * repaired every time the line changed shape.
     */
    private final Map<Integer, Integer> borderDirections = new HashMap<>();

    /**
     * Where along the manual patrol circuit each rider currently is, by citizen id. Same lifetime reasoning as
     * {@link #borderDirections}: not saved, because a forgotten cursor merely means the rider rejoins the circuit
     * at the point nearest him, which is also exactly how he starts.
     */
    private final Map<Integer, Integer> patrolIndices = new HashMap<>();

    /**
     * Whether the managers have already been told that the border patrol found no border to walk. The fallback to
     * an ordinary patrol is silent from the ground -- riders visibly do something, just not the thing the setting
     * asked for -- so the one moment the plan comes back empty is worth a message. One message: the plan is
     * recomputed every {@link #BORDER_PLAN_TTL_TICKS}, and repeating the sentence every five minutes for as long
     * as the setting stays on would train the player to ignore the message channel. Cleared when a usable plan
     * arrives or the task leaves border patrol, so switching it back on asks -- and warns -- afresh.
     */
    private boolean borderFailureWarned = false;
    
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
     * Which border, if any, this stable's cavalry is walking.
     *
     * @return {@link BorderPatrol.Mode#COLONY} while the task is a border patrol, {@link BorderPatrol.Mode#OFF}
     *         otherwise.
     */
    @NotNull
    public BorderPatrol.Mode getBorderPatrolMode()
    {
        return getTask().equals(GuardTaskSetting.PATROL_BORDER) ? BorderPatrol.Mode.COLONY : BorderPatrol.Mode.OFF;
    }

    /**
     * The border stretch this stable is working from, for the diagnose report.
     *
     * @return the plan, or null when the task is not a border patrol.
     */
    @Nullable
    public BorderPatrol.Plan getBorderPlan()
    {
        refreshBorderPlan();
        return borderPlan;
    }

    /**
     * The arc of the border one rider is responsible for.
     *
     * @param rider the rider asking.
     * @return his waypoints in order, empty when there is no border patrol on or no border was found.
     */
    @NotNull
    public List<BlockPos> getStretchFor(@Nullable final ICitizenData rider)
    {
        refreshBorderPlan();
        return rider == null ? List.of() : borderSlices.getOrDefault(rider.getId(), List.of());
    }

    /**
     * The next point of a rider's border patrol.
     * <p>
     * Each rider holds his own place on the line: the step is taken from the waypoint nearest where he actually
     * stands rather than from a remembered index, so a rider dragged off by a fight rejoins his arc at the closest
     * point instead of walking back to where he was, and the direction turns round at each end of the arc, which is
     * what makes it a patrol rather than a one-way ride. Every point comes out of a search bounded to
     * {@link BorderPatrol#SEARCH_RADIUS_CHUNKS} of this building, so no rider can be sent further than that from
     * home whatever the claim does beyond it.
     *
     * @param rider the rider asking.
     * @param from  where his patrol currently stands, null before he has been given a point.
     * @return the next waypoint, or null to fall back to the ordinary patrol.
     */
    @Nullable
    public BlockPos getBorderPatrolTarget(@Nullable final ICitizenData rider, @Nullable final BlockPos from)
    {
        final List<BlockPos> stretch = getStretchFor(rider);
        if (stretch.isEmpty())
        {
            // No border within reach, or the task is not a border patrol. Fall back to the ordinary patrol rather
            // than standing still: a rider with nothing to walk is worse than a rider walking the wrong thing.
            return null;
        }

        final BlockPos anchor = from == null ? getPosition() : from;
        if (stretch.size() == 1)
        {
            return BorderPatrol.surface(getColony().getWorld(), stretch.get(0));
        }

        final int nearest = BorderPatrol.nearestIndex(stretch, anchor);
        int direction = borderDirections.getOrDefault(rider.getId(), 1);
        if (nearest + direction < 0 || nearest + direction >= stretch.size())
        {
            direction = -direction;
            borderDirections.put(rider.getId(), direction);
        }

        return BorderPatrol.surface(getColony().getWorld(), stretch.get(nearest + direction));
    }

    /**
     * The next point of a rider's own ordinary patrol -- the per-rider counterpart of
     * {@link AbstractBuildingGuards#getNextPatrolTarget}.
     * <p>
     * The building's own route holds one cursor for the whole hut and advances it only once every assigned guard
     * has reported arrival, with a fallback timer of five colony ticks -- about two minutes. That is the right
     * shape for a garrison meant to move as one and the wrong one for cavalry: one rider asleep, dismounted or
     * fighting parked the rest of the troop at the current point for the whole of that timer. A rider on a stable
     * patrol therefore draws each leg from here, against his own cursor, and waits for nobody. The building is
     * still told about every arrival, because that is also the clock the rest window runs on.
     * <p>
     * What a leg is does not change: a raid alert point is consumed first, exactly as the shared route consumed it
     * (it is a one-shot for the whole hut, so the first rider to ask rides it); a manual circuit is walked in the
     * order the player laid it, each rider by his own index, joining at the point nearest him; and the automatic
     * route draws the same stable-flavoured random target the shared route drew, under the same distance clamp.
     *
     * @param rider the rider asking, for his cursor; null falls back to a shared one.
     * @param from  where he is standing, to join the circuit at the nearest point.
     * @return the next waypoint, or null to fall back to the building's shared route.
     */
    @Nullable
    public BlockPos getPatrolTargetFor(@Nullable final ICitizenData rider, @NotNull final BlockPos from)
    {
        if (tempNextPatrolPoint != null)
        {
            final BlockPos alert = tempNextPatrolPoint;
            tempNextPatrolPoint = null;
            return alert;
        }

        if (getSetting(PATROL_MODE).getValue().equals(GuardPatrolModeSetting.MANUAL) && patrolTargets != null && !patrolTargets.isEmpty())
        {
            final int riderId = rider == null ? 0 : rider.getId();
            final Integer held = patrolIndices.get(riderId);
            final int index = held == null
                                ? BorderPatrol.nearestIndex(patrolTargets, from)
                                : (held + 1) % patrolTargets.size();
            patrolIndices.put(riderId, index);
            return patrolTargets.get(index);
        }

        final BlockPos pos = getRandomPatrolTarget();
        if (pos == null || BlockPosUtil.getDistance(pos, getPosition()) > getPatrolDistance())
        {
            return getPosition();
        }
        return pos;
    }

    /**
     * Work the border stretch out again, and re-cut it, if anything that shapes it has changed.
     * <p>
     * Checked in order of what each check costs: the task not being a border patrol (a string compare), the rider
     * list having changed (a list compare over a handful of ints), and finally age. Only the last of those pays for
     * a chunk scan, and only once every {@link #BORDER_PLAN_TTL_TICKS}.
     */
    private void refreshBorderPlan()
    {
        if (getBorderPatrolMode() == BorderPatrol.Mode.OFF)
        {
            borderFailureWarned = false;
            if (borderPlan != null)
            {
                borderPlan = null;
                borderPlanRiders = List.of();
                borderSlices.clear();
                borderDirections.clear();
            }
            return;
        }

        final List<ICitizenData> riders = new ArrayList<>(getAllAssignedCitizen());
        riders.sort(Comparator.comparingInt(ICitizenData::getId));
        final List<Integer> ids = riders.stream().map(ICitizenData::getId).toList();

        final long now = getColony().getWorld() == null ? 0 : getColony().getWorld().getGameTime();
        if (borderPlan != null && borderPlanRiders.equals(ids) && now - borderPlanTime < BORDER_PLAN_TTL_TICKS)
        {
            return;
        }

        borderPlan = BorderPatrol.findStretch(getColony(), getPosition(), BorderPatrol.Mode.COLONY);
        borderPlanRiders = ids;
        borderPlanTime = now;
        sliceBorderPlan(riders);

        if (borderPlan.isUsable())
        {
            borderFailureWarned = false;
        }
        else if (!borderFailureWarned)
        {
            borderFailureWarned = true;
            MessageUtils.format(STABLE_BORDER_UNREACHABLE,
                getCustomName().isEmpty() ? getSchematicName() : getCustomName(),
                BlockPosUtil.getString(getPosition()),
                BorderPatrol.SEARCH_RADIUS_CHUNKS * 16)
              .sendTo(getColony()).forManagers();
        }
    }

    /**
     * Cut the computed stretch into one contiguous arc per rider.
     * <p>
     * Riders are ordered by where along the line they already stand, so each takes the arc he is nearest and the
     * troop spreads out along the frontier instead of every man riding to the same end of it to start. Two riders in
     * the same place fall back to their ids, so the cut is the same every time it is made.
     *
     * @param riders the riders to cut it for, in id order.
     */
    private void sliceBorderPlan(@NotNull final List<ICitizenData> riders)
    {
        borderSlices.clear();
        borderDirections.clear();
        if (borderPlan == null || !borderPlan.isUsable() || riders.isEmpty())
        {
            return;
        }

        final List<BlockPos> line = borderPlan.waypoints();
        final List<ICitizenData> ordered = new ArrayList<>(riders);
        ordered.sort(Comparator.comparingInt((final ICitizenData rider) -> BorderPatrol.nearestIndex(line, standingAt(rider)))
                       .thenComparingInt(ICitizenData::getId));

        final int count = ordered.size();
        for (int i = 0; i < count; i++)
        {
            final int from = line.size() * i / count;
            final int to = line.size() * (i + 1) / count;
            // A line shorter than the troop would otherwise hand somebody an empty arc, which reads as that rider
            // being broken. Give him the single nearest point instead: a rider standing on the line beats a rider
            // with nothing to do.
            borderSlices.put(ordered.get(i).getId(),
              from >= to ? List.of(line.get(Math.min(from, line.size() - 1))) : List.copyOf(line.subList(from, to)));
        }
    }

    /**
     * Where a rider is, for the purpose of cutting the line.
     *
     * @param rider the rider.
     * @return his last known position, or this building's when he has none yet.
     */
    @NotNull
    private BlockPos standingAt(@NotNull final ICitizenData rider)
    {
        final BlockPos last = rider.getLastPosition();
        return last == null || BlockPos.ZERO.equals(last) ? getPosition() : last;
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
            //
            // A border patrol is deliberately not on this branch. Where the route comes from and whether the unit
            // ever comes in are two different questions, and folding them together would have meant either a second
            // permanent option for every route or a border screen that cannot be told to rest. It rests on the
            // interval like an ordinary patrol, and an interval of zero - which the setting already allows and
            // documents as "no wait" - is the border screen that never comes in.
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
