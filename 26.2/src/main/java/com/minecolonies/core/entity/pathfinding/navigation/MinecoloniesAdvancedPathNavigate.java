package com.minecolonies.core.entity.pathfinding.navigation;

import net.minecraft.world.entity.EntitySpawnReason;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.other.MinecoloniesBoat;
import com.minecolonies.api.entity.other.MinecoloniesMinecart;
import com.minecolonies.api.entity.pathfinding.IDynamicHeuristicNavigator;
import com.minecolonies.api.entity.pathfinding.IMinecoloniesNavigator;
import com.minecolonies.api.entity.pathfinding.IStuckHandler;
import com.minecolonies.api.util.*;
import com.minecolonies.api.util.constant.ColonyConstants;
import com.minecolonies.api.util.constant.GuardConstants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import com.minecolonies.core.entity.pathfinding.*;
import com.minecolonies.core.entity.pathfinding.pathjobs.*;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.minecolonies.core.entity.pathfinding.pathresults.TreePathResult;
import com.minecolonies.core.util.WorkerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.core.entity.pathfinding.PathFindingStatus.IN_PROGRESS_FOLLOWING;
import static com.minecolonies.core.entity.pathfinding.pathjobs.AbstractPathJob.MAX_NODES;

/**
 * Minecolonies async PathNavigate.
 */
// TODO: Rework
public class MinecoloniesAdvancedPathNavigate extends AbstractAdvancedPathNavigate implements IDynamicHeuristicNavigator, IMinecoloniesNavigator
{
    private static final double ON_PATH_SPEED_MULTIPLIER = 1.3D;
    public static final  double MIN_Y_DISTANCE           = 0.001;
    public static final  int    MAX_SPEED_ALLOWED        = 2;
    public static final  double MIN_SPEED_ALLOWED        = 0.1;

    /**
     * Horizontal distance from the boat to the node it must unload its passenger onto, within which the crossing counts
     * as finished, in blocks.
     * <p>
     * A boat cannot enter the shore block itself, so it stalls roughly a block short of the exit node. The path index
     * only advances once the passenger is within half a block of a node, so without a proximity rule of its own the
     * boat sits nudging the bank forever with the citizen aboard, and the path never completes.
     */
    private static final double BOAT_DISEMBARK_DISTANCE = 2.0D;

    /**
     * How far the passenger may be stepped across to reach solid ground once the crossing has been given up on, in
     * blocks. Only ever consulted after {@link #BOAT_STUCK_DISEMBARK} ticks of a hull that is not getting any closer
     * to where it is going, so it never ends a crossing that was still going to arrive.
     * <p>
     * PORT-NOTE(26.2): {@link #BOAT_DISEMBARK_DISTANCE} is the distance a healthy arrival needs and is deliberately
     * left alone, but it is not always enough. A hull is 1.375 blocks wide, so its centre cannot come closer than
     * 1.19 blocks to the centre of a block it is pressed against face on, and about 1.5 at a corner -- fine when the
     * exit node is the very block being touched, and not fine at a dock, where the deck the pathfinder chose to
     * unload onto can sit a block back from the water's edge at 1.19 + 1 = 2.19 blocks. Such a crossing can never
     * satisfy a flat 2.0 however long it waits. Rather than widen the ordinary radius, which would start ending
     * healthy crossings early and a couple of blocks off, the wider reach is granted only where the alternative is
     * dumping the citizen in the water. Two blocks of set-back is the most it will bridge.
     */
    private static final double BOAT_STUCK_DISEMBARK_DISTANCE = 3.0D;

    /**
     * Spacing of the samples taken along a straight line over water to decide whether a boat can travel it, in blocks.
     * Half a block, so no one block obstacle can hide between two samples.
     */
    private static final double BOAT_LINE_STEP = 0.5D;

    /**
     * Half the width of a boat hull, in blocks, used to sweep both flanks of a line rather than just its middle.
     */
    private static final double BOAT_HALF_WIDTH = 0.75D;

    /**
     * How far ahead of the boat the taut line is allowed to reach, in blocks. A line test costs work proportional to
     * its length, and past a few dozen blocks a straighter heading is not worth re-deriving every tick.
     */
    private static final double BOAT_MAX_LOOKAHEAD = 32.0D;

    /**
     * How much closer to what it is steering at a boat must get in a tick to count as making progress, in blocks.
     * <p>
     * PORT-NOTE(26.2): this used to be how far the hull moved, full stop, and that is why a boat could sit against a
     * dock forever. A hull pressed against a pier is not still: the steering writes a fresh velocity into it every
     * tick, it slides along the face it is touching and it bobs, and every one of those tenths of a block reset the
     * counter. Closing distance is the honest measure -- a boat that is not getting nearer to where it is going is
     * held up, whatever it is doing on the spot. Well under the 0.09 blocks a tick the slowest configurable speed
     * closes at, so only a hull that really is held up trips it.
     */
    private static final double BOAT_PROGRESS = 0.02D;

    /**
     * Ticks of no progress after which a boat stops cutting corners and heads for the very next node instead. A hull
     * caught on a block it half clipped is usually freed by aiming somewhere the pathfinder had already proved open.
     */
    private static final int BOAT_STUCK_STRAIGHTEN = 20;

    /**
     * Ticks of no progress after which the passenger is put ashore anyway, if there is an exit node near enough to
     * step onto. This is the dock case: the boat has arrived, it is touching the thing it was aiming at, it is never
     * going to get closer, and the citizen could have walked from here two seconds ago.
     */
    private static final int BOAT_STUCK_DISEMBARK = 40;

    /**
     * Ticks of no progress after which the crossing is given up on and the boat abandoned. The citizen swims the rest,
     * which is slow but always finishes -- being stuck in a boat forever does not.
     */
    private static final int BOAT_STUCK_ABANDON = 60;

    @Nullable
    private PathResult<? extends AbstractPathJob> pathResult;

    /**
     * Spawn pos of minecart.
     */
    private BlockPos spawnedPos = BlockPos.ZERO;

    /**
     * Spawn pos of the boat, so a single boat entry point does not spawn a new boat every tick.
     */
    private BlockPos spawnedBoatPos = BlockPos.ZERO;

    /**
     * Game time until which a rider who runs out of path is left in their boat instead of being put out of it.
     * <p>
     * Zero, and therefore off, for every entity that does not ask. See {@link #keepBoat(int)}.
     */
    private long keepBoatUntil = 0L;

    /**
     * Scratch position for sampling a straight line over water, kept apart from {@code tempPos} because the boat entry
     * held in that one has to survive the steering done later in the same tick.
     */
    private final BlockPos.MutableBlockPos boatLinePos = new BlockPos.MutableBlockPos();

    /**
     * Ticks the boat has failed to get any closer to what it is being steered at.
     */
    private int boatStuckTicks;

    /**
     * The node the held-up check is measuring against, and the closest the hull has come to it in blocks.
     * <p>
     * The node is remembered so that being given a new one counts as progress in its own right: {@code
     * farthestVisible} only ever moves the index forward, and only to a node the boat can see and is heading for, so
     * a new goal is the crossing getting on with itself. It is remembered by position rather than by index because an
     * index means nothing across a replan -- node 3 of the path installed a moment ago is not node 3 of this one.
     */
    private BlockPos boatGoal         = BlockPos.ZERO;
    private double   boatBestDistance = Double.MAX_VALUE;

    /**
     * Desired position to reach
     */
    private BlockPos safeDestinationPos;

    /**
     * The stuck handler to use
     */
    private IStuckHandler<MinecoloniesAdvancedPathNavigate> stuckHandler;

    /**
     * Whether we did set sneaking
     */
    private boolean isSneaking = true;

    /**
     * Speed factor for swimming
     */
    private double swimSpeedFactor = 1.0;

    /**
     * Average heuristic
     */
    // Volatile: written by the server thread as finished paths report their real cost per block, and read by the
    // pathfinding worker inside AbstractPathJob#reevaluteHeuristic while a later job for the same entity is being
    // searched. Without it that cross-thread read of a double is a data race in the strict sense -- the JLS permits
    // a torn word on a non-volatile long/double -- and at best sees a stale value.
    private volatile double heuristicAvg = 1;

    /**
     * Paused ticks, during those no new pathjob is allowed
     */
    private int pauseTicks = 0;

    /**
     * Increasing amount for pause times, each time a path fails
     */
    private int pauseTickBackupAmount = 10;

    /**
     * Temporary block position
     */
    private BlockPos.MutableBlockPos tempPos = new BlockPos.MutableBlockPos();

    /**
     * wanted position for movecontrol
     */
    private Vec3Mutable wantedPosition = Vec3Mutable.createEmpty();

    /**
     * The recheck delay for checking stuck
     */
    private int checkStuckDelay = 10;

    /**
     * Time at which a path finished
     */
    private long finishTime = Long.MAX_VALUE;

    /**
     * The last path index used for wanted position calculations
     */
    private int lastWantedPathIndex = -1;

    /**
     * Instantiates the navigation of an ourEntity.
     *
     * @param entity the ourEntity.
     * @param world  the world it is in.
     */
    public MinecoloniesAdvancedPathNavigate(@NotNull final Mob entity, final Level world)
    {
        super(entity, world);

        entity.moveControl = new MovementHandler(entity);
        this.nodeEvaluator = new WalkNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        getPathingOptions().setEnterDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        getPathingOptions().setCanOpenDoors(true);
        this.nodeEvaluator.setCanFloat(true);
        getPathingOptions().setCanSwim(true);

        stuckHandler = PathingStuckHandler.createStuckHandler().withTakeDamageOnStuck(0.2f).withTeleportSteps(6).withTeleportOnFullStuck();
    }

    @Nullable
    protected PathResult<PathJobMoveAwayFromLocation> walkAwayFrom(final BlockPos avoid, final double range, final double speedFactor, final boolean safeDestination)
    {
        @NotNull final BlockPos start = PathfindingUtils.prepareStart(ourEntity);

        return setPathJob(new PathJobMoveAwayFromLocation(CompatibilityUtils.getWorldFromEntity(ourEntity),
            start,
            avoid,
            (int) range,
            (int) ourEntity.getAttribute(Attributes.FOLLOW_RANGE).getValue(),
            ourEntity), null, speedFactor, safeDestination);
    }

    @Nullable
    @Override
    protected PathResult<AbstractPathJob> walkTowards(final BlockPos towards, final double range, final double speedFactor)
    {
        return setPathJob(new PathJobMoveTowards(CompatibilityUtils.getWorldFromEntity(ourEntity),
            PathfindingUtils.prepareStart(ourEntity),
            towards,
            (int) range,
            ourEntity), null, speedFactor, false);
    }

    @Nullable
    protected PathResult<PathJobRandomPos> walkToRandomPos(final int range, final double speedFactor)
    {
        @NotNull final BlockPos start = PathfindingUtils.prepareStart(ourEntity);
        final PathResult<PathJobRandomPos> result = setPathJob(new PathJobRandomPos(CompatibilityUtils.getWorldFromEntity(ourEntity),
            start,
            range,
            (int) ourEntity.getAttribute(Attributes.FOLLOW_RANGE).getValue(),

            ourEntity), null, speedFactor, true);

        if (result == null)
        {
            return null;
        }

        result.getJob().getPathingOptions().withToggleCost(1).withJumpCost(1).withDropCost(1).canDrop = false;
        return result;
    }

    @Nullable
    protected PathResult<PathJobRandomPos> walkToRandomPosAround(final int range, final double speedFactor, final BlockPos pos)
    {
        final PathResult<PathJobRandomPos> result = setPathJob(new PathJobRandomPos(CompatibilityUtils.getWorldFromEntity(ourEntity),
            PathfindingUtils.prepareStart(ourEntity),
            3,
            (int) ourEntity.getAttribute(Attributes.FOLLOW_RANGE).getValue(),
            range,
            ourEntity, pos), pos, speedFactor, false);

        if (result == null)
        {
            return null;
        }

        result.getJob().getPathingOptions().withToggleCost(1).withJumpCost(1).withDropCost(1).canDrop = false;
        return result;
    }

    @Override
    protected PathResult<PathJobRandomPos> walkToRandomPos(
        final int range,
        final double speedFactor,
        final com.minecolonies.api.util.Tuple<BlockPos, BlockPos> corners)
    {
        return walkToRandomPos(range, speedFactor, corners, false);
    }

    @Override
    protected PathResult<PathJobRandomPos> walkToRandomPos(
        final int range,
        final double speedFactor,
        final com.minecolonies.api.util.Tuple<BlockPos, BlockPos> corners, final boolean preferInside)
    {
        @NotNull final BlockPos start = PathfindingUtils.prepareStart(ourEntity);

        final PathResult<PathJobRandomPos> result = setPathJob(new PathJobRandomPos(CompatibilityUtils.getWorldFromEntity(ourEntity),
            start,
            range,
            (int) ourEntity.getAttribute(Attributes.FOLLOW_RANGE).getValue(),
            ourEntity,
            corners.getA(),
            corners.getB(), preferInside), null, speedFactor, true);

        if (result == null)
        {
            return null;
        }

        result.getJob().getPathingOptions().withJumpCost(1).withDropCost(1).canDrop = false;
        return result;
    }

    @Override
    protected PathResult<PathJobMoveCloseToXNearY> walkCloseToXNearY(
        final BlockPos desiredPosition,
        final BlockPos nearbyPosition,
        final int distToDesired,
        final double speedFactor,
        final boolean safeDestination)
    {
        PathJobMoveCloseToXNearY pathJob = new PathJobMoveCloseToXNearY(ourEntity.level(), desiredPosition, nearbyPosition, 1, ourEntity);
        return setPathJob(pathJob, desiredPosition, speedFactor, safeDestination);
    }

    @Nullable
    @Override
    public <T extends AbstractPathJob> PathResult<T> setPathJob(
        @NotNull final AbstractPathJob job,
        final BlockPos dest,
        final double speedFactor, final boolean safeDestination)
    {
        if (pauseTicks > 0)
        {
            return null;
        }

        if (ourEntity.getPose() != Pose.STANDING)
        {
            ourEntity.setPose(Pose.STANDING);
        }

        if (pathResult != null)
        {
            // If last pathjob was into unloaded, and we're trying to path into unloaded again unload entity
            if (dest != null && !WorldUtil.isBlockLoaded(ourEntity.level(), dest) && pathResult.getJob() instanceof IDestinationPathJob destinationPathJob &&
                !WorldUtil.isBlockLoaded(ourEntity.level(), destinationPathJob.getDestination()))
            {
                if (FabricLoader.getInstance().isDevelopmentEnvironment())
                {
                    Log.getLogger().info("Unloaded citizen:" + ourEntity + " trying to path into unloaded position at: " + dest, new Exception());
                }
                ourEntity.discard();
                return null;
            }

            pathResult.cancel();
            pathResult.setStatus(PathFindingStatus.CANCELLED);
            pathResult = null;
        }
        super.stop();

        if (dest != null)
        {
            final int maxDistance = MineColonies.getConfig().getServer().maxPathfindingDistance.get();
            if (job.getStart().distSqr(dest) > (double) maxDistance * maxDistance)
            {
                final double distance = Math.sqrt(job.getStart().distSqr(dest));
                PathfindingStats.recordRefusedTooFar(ourEntity.getDisplayName().getString(),
                    distance,
                    job.getStart(),
                    dest);

                // No stack trace. It named the same three frames of the navigator every time and never the AI that
                // set the destination, which is the only thing anyone wants to know, and filling one in is where
                // essentially the whole ~2 ms cost of a refusal went. That mattered because a refused citizen used to
                // come straight back: the early return below skipped the pause, so a worker whose order was out of
                // range was measured refusing 393 times in 18 seconds -- close to a second of server thread spent
                // building stack traces to say the same sentence. The short pause a few lines down is the other half
                // of that fix, and together they are what makes it safe to let the owner raise the limit at all: the
                // refusal has to stay far cheaper than the search it is standing in for, and it was not.
                Log.getLogger()
                    .error("Entity: " + ourEntity.getDisplayName().getString() + " is trying to walk too far! distance:" + distance + " from:" + job.getStart() + " to:" + dest
                             + " (limit " + maxDistance + ", raise 'maxpathfindingdistance' in the server config)");

                if (!dest.equals(BlockPos.ZERO))
                {
                    if (ourEntity instanceof AbstractEntityCitizen citizen)
                    {
                        final BlockPos tpPos = citizen.getCitizenData().getHomePosition();
                        ourEntity.snapTo(tpPos.getX(), tpPos.getY(), tpPos.getZ());
                        // Seconds, where everything else here gets five minutes. A citizen is not a raider: the order
                        // that was refused is one of many its AI will produce, and most of the rest are perfectly
                        // reachable, so blanking its pathing for five minutes would punish the colony for one bad
                        // building placement. Long enough to turn a per-tick loop into a per-hundred-tick one, short
                        // enough that a worker whose next order is ordinary is only briefly idle.
                        pauseTicks = TICKS_SECOND * 5;
                        return null;
                    }

                    ourEntity.snapTo(dest.getX(), dest.getY(), dest.getZ());
                }

                pauseTicks = 20 * 300;
                return null;
            }
        }

        finishTime = Long.MAX_VALUE;
        this.originalDestination = dest;
        if (safeDestination)
        {
            safeDestinationPos = dest;
        }

        this.walkSpeedFactor = speedFactor;

        if (speedFactor > MAX_SPEED_ALLOWED || speedFactor < MIN_SPEED_ALLOWED)
        {
            Log.getLogger().error("Tried to set a bad speed:" + speedFactor + " for entity:" + ourEntity, new Exception());
            return null;
        }

        job.setPathingOptions(getOptionsForPathJob());
        pathResult = job.getResult();
        Pathfinding.submit(pathResult);
        return (PathResult<T>) pathResult;
    }

    /**
     * Resolve the effective pathing options for a newly created path job.
     *
     * <p>Mounted units should path according to their vehicle's constraints without mutating
     * either navigator's long-lived settings. The returned options are copied into the job.</p>
     *
     * @return the pathing options for the next job
     */
    protected PathingOptions getOptionsForPathJob()
    {
        if (ourEntity.getVehicle() instanceof Mob riddenMob
            && riddenMob.getNavigation() instanceof AbstractAdvancedPathNavigate vehicleNavigation)
        {
            final PathingOptions mountedOptions = new PathingOptions();
            mountedOptions.importFrom(vehicleNavigation.getPathingOptions());

            if (riddenMob instanceof CavalryHorseEntity)
            {
                mountedOptions.setEnterGates(true);
                mountedOptions.setEnterDoors(false);
                mountedOptions.setTurnPenalty(GuardConstants.CAVALRY_CORNER_PENALTY);
            }

            return mountedOptions;
        }

        return getPathingOptions();
    }

    @Override
    public boolean isDone()
    {
        return (pathResult == null || pathResult.isDone() && pathResult.getStatus() != PathFindingStatus.CALCULATION_COMPLETE) && super.isDone();
    }

    @Override
    public void tick()
    {
        if (checkStuckDelay-- < 0)
        {
            checkStuckDelay = 10;
            stuckHandler.checkStuck(this);
        }

        if (pauseTicks > 0)
        {
            pauseTicks--;
        }

        if (pathResult != null)
        {
            if (!pathResult.isDone())
            {
                return;
            }
            else if (pathResult.getStatus() == PathFindingStatus.CALCULATION_COMPLETE)
            {
                processCompletedCalculationResult();
                wantedPosition.setEmpty();
            }
        }

        if (isDone())
        {
            // Never leave a citizen sitting in a colony vehicle with no path to follow. recalc() drops the path
            // without going through stop(), so nothing else notices that the ride has nowhere left to go.
            //
            // PORT-NOTE(26.2): the cart used to be missing from this, and it is the case with no other way out. An
            // abandoned boat at least drifts off across the lake and is conspicuous; a cart sits still on its rails,
            // and because MinecoloniesMinecart only discards itself while it is empty, one with a citizen aboard sits
            // there for the rest of the save. It is the rail twin of the boat that would not let go at a dock.
            final Entity vehicle = ourEntity.getVehicle();
            if (vehicle instanceof final MinecoloniesBoat boat && keepsItsBoat(boat))
            {
                // Somebody is using this hull for something other than travelling -- the fisherman parks in one and
                // casts from it. Left as it is: the request is a deadline that the asker has to keep renewing, so
                // the tick it stops being renewed this branch takes the boat away exactly as it always did.
                boat.moor(keepBoatUntil);
            }
            else if (vehicle instanceof MinecoloniesBoat || vehicle instanceof MinecoloniesMinecart)
            {
                // A bare dismount, with no attempt to place the citizen. There is no path left, so there is no node
                // to stand them on, and unlike the mid-crossing case there does not need to be one: stopRiding leaves
                // a passenger at the seat, and a cart's seat is the rail block it is sitting on, which is solid
                // ground. A boat's seat is water, which is a state the rest of the navigator already handles.
                ourEntity.stopRiding();

                // Discarded here rather than left to the vehicle's own empty-self-discard -- 19 ticks for the cart,
                // one for the boat -- because that timer only runs where the entity ticks. One left behind in a chunk
                // that is loaded without ticking would sit there indefinitely, and this is the moment we know the
                // vehicle is finished with.
                vehicle.remove(Entity.RemovalReason.DISCARDED);

                // The spawn markers mean "something has already been placed at this entry, do not place another every
                // tick". Having just removed that something, leaving them set would be a lie, and the next path that
                // boards at the same block would find the marker still matching and place nothing -- the citizen
                // would walk a leg the search had costed for a ride.
                spawnedPos = BlockPos.ZERO;
                spawnedBoatPos = BlockPos.ZERO;
            }

            if (pathResult != null)
            {
                pathResult.setStatus(PathFindingStatus.COMPLETE);

                // Cleanup pathresult if the entity forgot about it
                if (ourEntity.level().getGameTime() - finishTime > TICKS_SECOND * 20 + pauseTickBackupAmount)
                {
                    pathResult = null;
                }
            }

            if (!wantedPosition.empty())
            {
                mob.getMoveControl().setWantedPosition(wantedPosition.getX(), wantedPosition.getY(), wantedPosition.getZ(), speedModifier);
                wantedPosition.setEmpty();
            }
            return;
        }

        this.ourEntity.setYya(0);
        if (handleLadders())
        {
            return;
        }

        if (ColonyConstants.rand.nextInt(20) == 0)
        {
            if (WorkerUtil.isPathBlock(level.getBlockState(findBlockUnderEntity(ourEntity)).getBlock()))
            {
                speedModifier = ON_PATH_SPEED_MULTIPLIER * getSpeedFactor();
            }
            else
            {
                speedModifier = getSpeedFactor();
            }
        }

        if (isSneaking)
        {
            isSneaking = false;
            mob.setShiftKeyDown(false);
        }

        if (handleRails())
        {
            return;
        }

        if (handleBoats())
        {
            return;
        }

        ++this.tick;
        if (this.hasDelayedRecomputation)
        {
            this.recomputePath();
        }

        // The following block replaces mojangs super.tick(). Why you may ask? Because it's broken, that's why.
        // The moveHelper won't move up if standing in a block with an empty bounding box (put grass, 1 layer snow, mushroom in front of a solid block and have them try jump up).
        this.followThePath();

        if (this.path != null && !this.path.isDone())
        {
            if ((wantedPosition.empty() || lastWantedPathIndex != path.getNextNodeIndex() && path.getNextNodeIndex() < path.getNodeCount()))
            {
                lastWantedPathIndex = path.getNextNodeIndex();
                Vec3 vector3d2 = path.getNextEntityPos(mob);
                tempPos.set(Mth.floor(vector3d2.x), Mth.floor(vector3d2.y), Mth.floor(vector3d2.z));
                if (wantedPosition.empty() || ChunkPos.pack(tempPos) == mob.chunkPosition().pack() || WorldUtil.isEntityBlockLoaded(level, tempPos))
                {
                    wantedPosition.set(vector3d2.x,
                        getSmartGroundY(this.level, tempPos, vector3d2.y),
                        vector3d2.z);
                }
            }

            if (!wantedPosition.empty())
            {
                double moveSpeed = speedModifier;

                // Lower speed when moving up/down to the side to not miss a block when we have perpendicular momentum
                if (Math.abs(wantedPosition.getY() - mob.getY()) > 0.6 && getPreviousNode() != null
                    && ((getPreviousNode().x != getNextNode().x && Math.abs(mob.getDeltaMovement().z()) > Math.abs(mob.getDeltaMovement().x()))
                    || (getPreviousNode().z != getNextNode().z && Math.abs(mob.getDeltaMovement().x()) > Math.abs(mob.getDeltaMovement().z()))))
                {
                    // Overrule existing speed for safe turn, when changing y levels
                    moveSpeed = 0.6;
                }

                mob.getMoveControl().setWantedPosition(wantedPosition.getX(), wantedPosition.getY(), wantedPosition.getZ(), moveSpeed);
            }
        }
    }

    /**
     * Similar to WalkNodeProcessor.getGroundY but not broken.
     * This checks if the block below the position we're trying to move to reaches into the block above, if so, it has to aim a little bit higher.
     *
     * @param world the world.
     * @param pos   the position to check.
     * @param orgY  original y level
     * @return the next y level to go to.
     */
    public static double getSmartGroundY(final BlockGetter world, final BlockPos.MutableBlockPos pos, final double orgY)
    {
        BlockState state = world.getBlockState(pos);

        if (!state.isAir())
        {
            if (state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock)
            {
                return orgY;
            }

            final VoxelShape voxelshape = state.getCollisionShape(world, pos);
            if (!ShapeUtil.isEmpty(voxelshape))
            {
                return pos.getY() + ShapeUtil.max(voxelshape, Direction.Axis.Y);
            }
        }

        pos.set(pos.getX(), pos.getY() - 1, pos.getZ());

        state = world.getBlockState(pos);
        if (!state.isAir())
        {
            final VoxelShape voxelshape = state.getCollisionShape(world, pos);
            if (!ShapeUtil.isEmpty(voxelshape))
            {
                return pos.getY() + ShapeUtil.max(voxelshape, Direction.Axis.Y);
            }
        }

        return orgY;
    }

    @Nullable
    protected PathResult<PathJobMoveToLocation> walkTo(final BlockPos desiredPos, final double speedFactor, final boolean safeDestination)
    {
        @NotNull final BlockPos start = PathfindingUtils.prepareStart(ourEntity);
        return setPathJob(
            new PathJobMoveToLocation(CompatibilityUtils.getWorldFromEntity(ourEntity),
                start,
                desiredPos,
                (int) ourEntity.getAttribute(Attributes.FOLLOW_RANGE).getValue(),
                ourEntity),
            desiredPos, speedFactor, safeDestination);
    }

    @Deprecated(since = "Do not use, always returns true, vanilla override")
    @Override
    public boolean walkTo(final BlockPos pos, final double speedFactor)
    {
        walkTo(pos, speedFactor, false);
        return true;
    }

    /**
     * Port note (26.2): this returned {@code null} upstream, because MineColonies replaces vanilla pathfinding
     * wholesale and never uses this object. That is no longer allowed -- {@code PathNavigation}'s constructor now
     * dereferences the result immediately on a server level, to hook up path debug capture
     * ({@code PathNavigation.java:62-66}), so returning null throws before any citizen can even be created and
     * every entity fails with "Couldnt analyze animal". A real instance is handed back instead; it is still never
     * asked to compute anything, since every pathing entry point on this class is overridden.
     */
    @Override
    protected PathFinder createPathFinder(final int maxVisitedNodes)
    {
        return new PathFinder(new WalkNodeEvaluator(), maxVisitedNodes);
    }

    @Override
    protected boolean canUpdatePath()
    {
        return true;
    }

    @NotNull
    @Override
    protected Vec3 getTempMobPos()
    {
        return this.ourEntity.position();
    }

    @Override
    public Path createPath(final BlockPos pos, final int p_179680_2_)
    {
        //Because this directly returns Path we can't do it async.
        return null;
    }

    @Override
    protected boolean canMoveDirectly(final Vec3 start, final Vec3 end)
    {
        // TODO improve road walking. This is better in some situations, but still not great.
        return !WorkerUtil.isPathBlock(level.getBlockState(BlockPos.containing(start.x, start.y - 1, start.z)).getBlock())
            && super.canMoveDirectly(start, end);
    }

    public double getSpeedFactor()
    {
        if (ourEntity.isInWater())
        {
            speedModifier = walkSpeedFactor * swimSpeedFactor;
            return speedModifier;
        }

        speedModifier = walkSpeedFactor;
        return walkSpeedFactor;
    }

    @Override
    public void setSpeedModifier(final double speedFactor)
    {
        if (speedFactor > MAX_SPEED_ALLOWED || speedFactor < MIN_SPEED_ALLOWED)
        {
            Log.getLogger().error("Tried to set a bad speed:" + speedFactor + " for entity:" + ourEntity, new Exception());
            return;
        }
        walkSpeedFactor = speedFactor;
    }

    @Deprecated(since = "Do not use, always returns true, vanilla override")
    @Override
    public boolean moveTo(final double x, final double y, final double z, final double speedFactor)
    {
        walkTo(BlockPos.containing(x, y, z), speedFactor, false);
        return true;
    }

    @Override
    public boolean moveTo(final Entity entityIn, final double speedFactor)
    {
        return walkTo(entityIn.blockPosition(), speedFactor);
    }

    // Removes stupid vanilla stuff, causing our pathpoints to occasionally be replaced by vanilla ones.
    @Override
    protected void trimPath() {}

    @Deprecated(since = "Do not use, always returns true, vanilla override")
    @Override
    public boolean moveTo(@Nullable final Path path, final double speedFactor)
    {
        if (path == null)
        {
            super.stop();
            return false;
        }
        return super.moveTo(convertPath(path), speedFactor);
    }

    /**
     * Converts the given path to a minecolonies path if needed.
     *
     * @param path given path
     * @return resulting path
     */
    private Path convertPath(final Path path)
    {
        final int pathLength = path.getNodeCount();
        Path tempPath = null;
        if (pathLength > 0 && !(path.getNode(0) instanceof PathPointExtended))
        {
            //  Fix vanilla PathPoints to be PathPointExtended
            @NotNull final PathPointExtended[] newPoints = new PathPointExtended[pathLength];

            for (int i = 0; i < pathLength; ++i)
            {
                final Node point = path.getNode(i);
                if (!(point instanceof PathPointExtended))
                {
                    newPoints[i] = new PathPointExtended(new BlockPos(point.x, point.y, point.z));
                }
                else
                {
                    newPoints[i] = (PathPointExtended) point;
                }
            }

            tempPath = new Path(Arrays.asList(newPoints), path.getTarget(), path.canReach());
        }

        return tempPath == null ? path : tempPath;
    }

    /**
     * Processes the pathresult when it finished computing
     */
    private void processCompletedCalculationResult()
    {
        if (pathResult == null)
        {
            return;
        }

        if (pathResult != null)
        {
            pathResult.setStatus(IN_PROGRESS_FOLLOWING);
        }

        // Calculate an overtime-heuristic adjustment for pathfinding to use which fits the terrain
        if (pathResult.hasPath() && pathResult.getPathLength() > 2 && pathResult.costPerDist != 1)
        {
            final double factor = 1 + pathResult.getPathLength() / 30.0;
            heuristicAvg -= heuristicAvg / (50 / factor);
            heuristicAvg += pathResult.costPerDist / (50 / factor);
        }

        if (pathResult.failedToReachDestination())
        {
            pauseTicks = pauseTickBackupAmount;
            pauseTickBackupAmount += 10;

            if (pathResult.searchedNodes >= MAX_NODES)
            {
                pauseTicks += 50;
            }
        }
        else
        {
            pauseTickBackupAmount = 10;
        }

        moveTo(pathResult.getPath(), getSpeedFactor());
    }

    /**
     * Handles ladders on a path, following and movement
     *
     * @return true if handling a ladder
     */
    private boolean handleLadders()
    {
        if (!getNextNode().isOnLadder())
        {
            return false;
        }

        if (ourEntity.getVehicle() != null)
        {
            final Entity entity = ourEntity.getVehicle();
            ourEntity.stopRiding();
            if (!(ourEntity.getVehicle() instanceof CavalryHorseEntity))
            {
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        // Ladder path follow
        if (path.getNextNodeIndex() < path.getNodeCount())
        {
            HashSet<BlockPos> reached = null;
            if (PathfindingUtils.trackingMap.containsValue(ourEntity.getUUID()))
            {
                reached = new HashSet<>();
            }

            final double nextX = (double) getNextNode().x + (double) ((int) (this.mob.getBbWidth() + 1.0F)) * 0.5D;
            final double nextY = getNextNode().y;
            final double nextZ = (double) getNextNode().z + (double) ((int) (this.mob.getBbWidth() + 1.0F)) * 0.5D;

            final double diffX = Math.abs(this.mob.getX() - nextX);
            final double diffY = Math.abs(this.mob.getY() - nextY);
            final double diffZ = Math.abs(this.mob.getZ() - nextZ);

            // Ladder entry needs more exact position tracking, we want to center the citizen before doing movement in another axis
            if (getNextNode().isOnLadder() && getPreviousNode() == null || !getPreviousNode().isOnLadder())
            {
                if (diffX < 0.2 && diffZ < 0.2)
                {
                    if (reached != null)
                    {
                        reached.add(getNextNode().asBlockPos());
                        PathfindingUtils.syncDebugReachedPositions(reached, pathResult.getDebugWatchers());
                    }
                    this.path.setNextNodeIndex(path.getNextNodeIndex() + 1);
                    return true;
                }

                // Slightly offsets the ladders starting position, so entities walk infront of it and do not get stuck trying to enter from the side
                final double offSetStartX = nextX + getNextNode().getLadderFacing().getStepX() * 0.1;
                final double offSetStartZ = nextZ + getNextNode().getLadderFacing().getStepZ() * 0.1;
                ourEntity.xxa = 0;
                ourEntity.zza = 0;
                wantedPosition.set(offSetStartX, nextY, offSetStartZ);
                this.ourEntity.getMoveControl().setWantedPosition(offSetStartX, nextY, offSetStartZ, 0.4);
            }
            // Scaling ladder, move
            else
            {
                final PathPointExtended afterNext = getNextNextNode();
                if (diffX < 0.5 && diffZ < 0.5 && diffY < 0.1)
                {
                    if (reached != null)
                    {
                        reached.add(getNextNode().asBlockPos());
                        PathfindingUtils.syncDebugReachedPositions(reached, pathResult.getDebugWatchers());
                    }

                    if (afterNext == null || !afterNext.isOnLadder())
                    {
                        final PathPointExtended previous = getPreviousNode();
                        if (previous != null)
                        {
                            final boolean up = previous.y < nextY;
                            if (up && ourEntity.getY() > nextY || !up && ourEntity.getY() < nextY)
                            {
                                this.path.setNextNodeIndex(path.getNextNodeIndex() + 1);
                            }
                        }
                    }
                    else
                    {
                        this.path.setNextNodeIndex(path.getNextNodeIndex() + 1);
                    }
                }

                if (isDone())
                {
                    return true;
                }

                //  Ladder Workaround
                if (getNextNode().isOnLadder() && afterNext != null && (getNextNode().y != afterNext.y || mob.getY() > getNextNode().y))
                {
                    return doLadderMovement();
                }
                return false;
            }
        }

        return true;
    }

    /**
     * Handles movement on a ladder
     *
     * @return true if a ladder is being handled
     */
    private boolean doLadderMovement()
    {
        Vec3 vec3 = this.getPath().getNextEntityPos(this.ourEntity);
        final BlockPos entityPos = this.ourEntity.blockPosition();
        //This way he is less nervous and gets up the ladder
        double newSpeed = 0.5;
        switch (getNextNode().getLadderFacing())
        {
            //  Any of these values is climbing, so adjust our direction of travel towards the ladder
            case NORTH:
                vec3 = vec3.add(0, 1, 0.8);
                break;
            case SOUTH:
                vec3 = vec3.add(0, 1, -0.8);
                break;
            case WEST:
                vec3 = vec3.add(0.8, 1, 0);
                break;
            case EAST:
                vec3 = vec3.add(-0.8, 1, 0);
                break;
            case UP:
                vec3 = vec3.add(0, 1, 0);
                break;
            //  Any other value is going down, so lets not move at all
            default:
                newSpeed = 0;
                if (!isSneaking)
                {
                    mob.setShiftKeyDown(true);
                    isSneaking = true;
                }
                this.ourEntity.getMoveControl().setWantedPosition(vec3.x, vec3.y, vec3.z, 0.2);
                wantedPosition.set(vec3.x, vec3.y, vec3.z);
                break;
        }

        if (newSpeed > 0)
        {
            if (!(level.getBlockState(ourEntity.blockPosition()).getBlock() instanceof LadderBlock) && ourEntity.getY() <= vec3.y)
            {
                this.ourEntity.setDeltaMovement(0, 0.1D, 0);
            }
            this.ourEntity.getMoveControl().setWantedPosition(vec3.x, vec3.y, vec3.z, newSpeed);
            wantedPosition.set(vec3.x, vec3.y, vec3.z);
            return true;
        }
        else
        {
            if (PathfindingUtils.isLadder(level.getBlockState(entityPos.below()), getPathingOptions()) || ourEntity.getY() > getNextNode().y)
            {
                this.ourEntity.setYya(-0.5f);
            }
            else
            {
                return false;
            }
            return true;
        }
    }

    /**
     * Determine what block the entity stands on
     *
     * @param parEntity the entity that stands on the block
     * @return the Blockstate.
     */
    private BlockPos findBlockUnderEntity(@NotNull final Entity parEntity)
    {
        int blockX = (int) Math.round(parEntity.getX());
        int blockY = Mth.floor(parEntity.getY() - 0.2D);
        int blockZ = (int) Math.round(parEntity.getZ());
        return tempPos.set(blockX, blockY, blockZ);
    }

    /**
     * Handle rails navigation.
     *
     * @return true if block.
     */
    private boolean handleRails()
    {
        if (!this.isDone())
        {
            @NotNull final PathPointExtended pEx = (PathPointExtended) this.getPath().getNode(this.getPath().getNextNodeIndex());
            PathPointExtended pExNext = getPath().getNodeCount() > this.getPath().getNextNodeIndex() + 1
                ? (PathPointExtended) this.getPath()
                                      .getNode(this.getPath()
                                               .getNextNodeIndex() + 1) : null;

            if (pExNext != null && pEx.x == pExNext.x && pEx.z == pExNext.z)
            {
                pExNext = getPath().getNodeCount() > this.getPath().getNextNodeIndex() + 2
                    ? (PathPointExtended) this.getPath()
                                          .getNode(this.getPath()
                                                   .getNextNodeIndex() + 2) : null;
            }

            if (pEx.isOnRails() || pEx.isRailsExit())
            {
                return handlePathOnRails(pEx, pExNext);
            }

            if (mob.getVehicle() instanceof final MinecoloniesMinecart cart)
            {
                // The rails twin of the boat's endCrossing: the index has left the leg with the citizen still in the
                // cart, so there is no leg left to drive and nothing that would ever notice. followThePath's dismount
                // rule is what normally covers this and cannot be relied on -- it sits inside a
                // curNode + 1 < getNodeCount() guard, so it never fires once the index has come to rest on the last
                // node of a path, which leaves the citizen sitting in a cart that cannot discard itself either.
                //
                // Written out here rather than sharing the boat's version because handleBoats is never reached on
                // this path: a rails leg returns above, and a citizen in a cart falls through its boat branch. It is
                // also simpler than the boat's. There is no landing place to find, because a cart's seat is the rail
                // block underneath it -- see the isDone cleanup in tick(), which makes the same call for the same
                // reason.
                mob.stopRiding();
                cart.remove(Entity.RemovalReason.DISCARDED);
                spawnedPos = BlockPos.ZERO;
            }
        }
        return false;
    }

    /**
     * Handle pathing on rails.
     *
     * @param pEx     the current path point.
     * @param pExNext the next path point.
     * @return if go to next point.
     */
    private boolean handlePathOnRails(final PathPointExtended pEx, final PathPointExtended pExNext)
    {
        if (pEx.isRailsEntry())
        {
            tempPos.set(pEx.x, pEx.y, pEx.z);
            if (!spawnedPos.equals(tempPos))
            {
                final BlockState blockstate = level.getBlockState(tempPos);
                // 26.2 dropped NeoForge's BaseRailBlock#getRailDirection; the shape property is public.
                final RailShape railshape = blockstate.getBlock() instanceof final BaseRailBlock railBlock
                    ? blockstate.getValue(railBlock.getShapeProperty())
                    : RailShape.NORTH_SOUTH;
                double yOffset = 0.0D;
                if (railshape.isSlope())
                {
                    yOffset = 0.5D;
                }

                if (mob.getVehicle() instanceof final MinecoloniesMinecart ourMinecart)
                {
                    ourMinecart.setHurtDir(1);
                }
                else
                {
                    MinecoloniesMinecart minecart = ModEntities.MINECART.create(level, EntitySpawnReason.MOB_SUMMONED);
                    final double x = pEx.x + 0.5D;
                    final double y = pEx.y + 0.625D + yOffset;
                    final double z = pEx.z + 0.5D;
                    minecart.setPos(x, y, z);
                    minecart.setDeltaMovement(Vec3.ZERO);
                    minecart.xo = x;
                    minecart.yo = y;
                    minecart.zo = z;

                    // Claimed before it enters the world, exactly as the boat is: from addFreshEntity onwards the cart
                    // is a vanilla minecart sitting on a rail, and a minecart picks up whatever is standing in it.
                    minecart.claimFor(mob);

                    level.addFreshEntity(minecart);
                    minecart.setHurtDir(1);
                    mob.startRiding(minecart, true, true);
                }
                spawnedPos = tempPos.immutable();
            }
        }
        else
        {
            spawnedPos = BlockPos.ZERO;
        }

        if (mob.getVehicle() instanceof MinecoloniesMinecart && pExNext != null)
        {
            final Vec3 motion = mob.getVehicle().getDeltaMovement();
            double forward;
            switch (BlockPosUtil.directionFromDelta(pExNext.x - pEx.x, 0, pExNext.z - pEx.z).getOpposite())
            {
                case EAST:
                    forward = Math.min(Math.max(motion.x() - 1 * 0.01D, -1), 0);
                    mob.getVehicle().setDeltaMovement(motion.add(forward == -1 ? -1 : -0.01D, 0.0D, 0.0D));
                    break;
                case WEST:
                    forward = Math.max(Math.min(motion.x() + 0.01D, 1), 0);
                    mob.getVehicle().setDeltaMovement(motion.add(forward == 1 ? 1 : 0.01D, 0.0D, 0.0D));
                    break;
                case NORTH:
                    forward = Math.max(Math.min(motion.z() + 0.01D, 1), 0);
                    mob.getVehicle().setDeltaMovement(motion.add(0.0D, 0.0D, forward == 1 ? 1 : 0.01D));
                    break;
                case SOUTH:
                    forward = Math.min(Math.max(motion.z() - 1 * 0.01D, -1), 0);
                    mob.getVehicle().setDeltaMovement(motion.add(0.0D, 0.0D, forward == -1 ? -1 : -0.01D));
                    break;

                case DOWN:
                case UP:
                    // unreachable
                    break;
            }
        }
        return false;
    }

    /**
     * Handle boat navigation, the water counterpart of {@link #handleRails()}.
     * <p>
     * Boarding happens the moment the boat entry becomes the node we are heading for, exactly the way the minecart
     * spawns at the rails entry: the citizen is still on the bank, the boat is placed on the water and
     * {@code startRiding} pulls them aboard.
     * <p>
     * PORT-NOTE(26.2): leaving used to be left entirely to {@link #followThePath()}, whose rule is "get out of the
     * vehicle once the node being followed is no longer part of the leg". That rule is right but it is not reliable:
     * it sits inside a {@code curNode + 1 < getNodeCount()} guard, so it does not fire at all when the index has come
     * to rest on the last node of a path, and it drops the citizen at the seat rather than putting them anywhere --
     * {@code Entity#stopRiding} only clears the vehicle link, it does not move the passenger. Every way out of a boat
     * is therefore decided here now, where the alternative to deciding is a citizen who sits in a boat for the rest
     * of the save.
     *
     * @return true if the rest of the tick should be skipped. Always false, kept for symmetry with handleRails.
     */
    private boolean handleBoats()
    {
        if (this.isDone())
        {
            return false;
        }

        final Node current = this.getPath().getNode(this.getPath().getNextNodeIndex());
        if (!(current instanceof final PathPointExtended pEx))
        {
            // A vanilla path that never went through convertPath carries no boat flags at all, so there is no leg
            // here to follow. For a citizen who is nonetheless aboard that means the same thing an unflagged node
            // means below: the crossing is over and they have to be got out.
            if (mob.getVehicle() instanceof final MinecoloniesBoat boat)
            {
                endCrossing(boat, current, "the path being followed carries no boat flags");
            }
            return false;
        }

        if (pEx.isBoatEntry())
        {
            tempPos.set(pEx.x, pEx.y, pEx.z);
            if (!spawnedBoatPos.equals(tempPos))
            {
                if (!(mob.getVehicle() instanceof MinecoloniesBoat))
                {
                    // A path can hand a rails leg straight over to a water leg. startRiding() would silently drop
                    // the old vehicle and leave it behind, so get out of it deliberately first.
                    if (mob.getVehicle() != null && !(mob.getVehicle() instanceof CavalryHorseEntity))
                    {
                        final Entity previous = mob.getVehicle();
                        mob.stopRiding();
                        previous.remove(Entity.RemovalReason.DISCARDED);
                    }

                    final MinecoloniesBoat boat = ModEntities.BOAT.create(level, EntitySpawnReason.MOB_SUMMONED);
                    if (boat != null)
                    {
                        final double x = pEx.x + 0.5D;
                        final double z = pEx.z + 0.5D;
                        // Drop the hull in from just above the surface and let vanilla buoyancy seat it. Placing it
                        // inside the water block instead would leave the boat in UNDER_WATER status, which refuses
                        // passengers (AbstractBoat#canAddPassenger) and ejects the ones it has after 60 ticks.
                        final double y = pEx.y + (PathfindingUtils.isWater(level, tempPos) ? 1.0D : 0.0D);

                        boat.setPos(x, y, z);
                        boat.setDeltaMovement(Vec3.ZERO);
                        boat.xo = x;
                        boat.yo = y;
                        boat.zo = z;
                        boat.setYRot(mob.getYRot());
                        boat.yRotO = boat.getYRot();
                        // Claimed before it enters the world, so vanilla's boarding sweep can never hand the seat to
                        // something that wandered into the hull between placing the boat and our citizen mounting it.
                        boat.claimFor(mob);

                        level.addFreshEntity(boat);
                        mob.startRiding(boat, true, true);
                    }
                }
                spawnedBoatPos = tempPos.immutable();
            }
        }
        else
        {
            spawnedBoatPos = BlockPos.ZERO;
        }

        if (mob.getVehicle() instanceof final MinecoloniesBoat boat)
        {
            if (disembark(boat))
            {
                return false;
            }

            if (!pEx.isOnBoat() && !pEx.isBoatExit())
            {
                // The index has left the leg with the citizen still aboard. There is nothing to steer at, no exit to
                // reach and nothing that counts ticks, so this is the shape every deadlock takes; end it outright
                // rather than hope followThePath's rule fires, which on the last node of a path it will not.
                endCrossing(boat, pEx, "the node being followed is no longer part of a boat leg");
                return false;
            }

            // Once held up, stop cutting the corner: the node chain is ground the search proved open, so heading for
            // the very next node is the most likely way off whatever the hull caught on. Chosen before the held-up
            // check rather than after it, because it is progress towards this node that the check measures.
            final PathPointExtended target = boatStuckTicks >= BOAT_STUCK_STRAIGHTEN ? pEx : farthestVisible(boat);

            if (rescueIfHeldUp(boat, target))
            {
                return false;
            }

            steerBoat(boat, target);
        }
        else
        {
            resetBoatProgress();
        }

        return false;
    }

    /**
     * Ask that a boat this entity is riding be left alone for a while once its path runs out.
     * <p>
     * The default is the opposite: a colony boat with no path left is a boat nobody is going to steer, so
     * {@link #tick()} takes the citizen out of it and discards the hull. That is right for travel and wrong for a
     * fisherman, who reaches open water and then deliberately stops moving in order to cast from where he is.
     * <p>
     * Deliberately a short deadline that the caller has to keep renewing, not a switch. The caller is a worker AI,
     * and a worker AI stops running for a dozen ordinary reasons -- the citizen goes to eat, goes to bed, is
     * unassigned, dies, has his chunk unloaded. Every one of those has to end with the boat gone rather than with a
     * citizen sitting in the middle of a lake for ever, and an expiring deadline is the only shape that gets all of
     * them without the AI having to remember any of them.
     *
     * @param ticks how much longer the boat is wanted for. Called again each time the AI ticks.
     */
    public void keepBoat(final int ticks)
    {
        keepBoatUntil = Math.max(keepBoatUntil, level.getGameTime() + ticks);
    }

    /**
     * @param boat the hull being ridden.
     * @return true if this boat is being kept on purpose and the idle cleanup should leave it alone.
     */
    private boolean keepsItsBoat(final MinecoloniesBoat boat)
    {
        return level.getGameTime() < keepBoatUntil || boat.isMoored();
    }

    /**
     * Put the passenger ashore once the boat has got as close to the far bank as it is ever going to.
     * <p>
     * {@link #followThePath()} dismounts on the rule "the node being followed is no longer part of the leg", which is
     * the right rule but is reached by a mechanism a boat defeats: the index only advances when the passenger comes
     * within half a block of a node, and the exit node is on land, inside a block the hull cannot enter. The boat
     * therefore stalls against the bank one block short, the index never reaches the exit, and the citizen rides on
     * forever. Proximity to the exit is the thing that can actually be observed, so that is what ends the crossing.
     *
     * @param boat the boat being ridden.
     * @return true if the passenger was put ashore and the rest of the tick should be skipped.
     */
    private boolean disembark(final MinecoloniesBoat boat)
    {
        final int exitIndex = boatExitIndex();
        if (exitIndex < 0)
        {
            return false;
        }

        if (horizontalDistance(boat, path.getNode(exitIndex)) > BOAT_DISEMBARK_DISTANCE)
        {
            return false;
        }

        putAshore(boat, exitIndex);
        return true;
    }

    /**
     * Step the passenger off onto a node of the path and get rid of the hull.
     * <p>
     * The landing place is the node the path already picked, rather than anywhere vanilla would choose:
     * {@code Entity#getDismountLocationForPassenger} answers with the top of the boat's own bounding box, and
     * {@code Entity#stopRiding} does not even apply that -- it clears the vehicle link and leaves the passenger
     * hanging at the seat. On a steep bank or a dock that means falling straight back into the water the boat has
     * just finished crossing.
     *
     * @param boat      the boat being ridden.
     * @param nodeIndex the index of the node to stand the passenger on.
     */
    private void putAshore(final MinecoloniesBoat boat, final int nodeIndex)
    {
        final Node landing = path.getNode(nodeIndex);

        mob.stopRiding();
        mob.snapTo(landing.x + 0.5D, landing.y, landing.z + 0.5D, mob.getYRot(), mob.getXRot());
        boat.remove(Entity.RemovalReason.DISCARDED);
        spawnedBoatPos = BlockPos.ZERO;
        resetBoatProgress();

        // Land the index on the node itself rather than past it, so the node the citizen is now standing on is retired
        // by the ordinary proximity check on the next tick and the walk carries on from there.
        while (!path.isDone() && path.getNextNodeIndex() < nodeIndex)
        {
            path.advance();
        }
    }

    /**
     * End a crossing that has nothing left to follow, with the passenger still aboard.
     * <p>
     * There is no leg and no exit here, so there is no node the path is asking the boat to reach; the best that can be
     * done is to stand the citizen on the node they are heading for, and only when that is near enough not to be a
     * teleport. Otherwise they are simply let go where they are and swim, which the rest of the navigator handles.
     *
     * @param boat   the boat being ridden.
     * @param node   the node currently being followed.
     * @param reason what put us here, for the log.
     */
    private void endCrossing(final MinecoloniesBoat boat, final Node node, final String reason)
    {
        // A hull somebody was keeping on purpose has just been given a path that walks away from it -- the fisherman
        // has filled his inventory and is off to the hut. That is the intended end of a mooring, not a crossing that
        // went wrong, so it takes the same dismount without the warning: the log line exists to make a stuck boat
        // findable and one per trip home would only bury it.
        if (!keepsItsBoat(boat))
        {
            logHeldUp(boat, node, reason);
        }

        if (horizontalDistance(boat, node) <= BOAT_STUCK_DISEMBARK_DISTANCE)
        {
            putAshore(boat, path.getNextNodeIndex());
            return;
        }

        mob.stopRiding();
        boat.remove(Entity.RemovalReason.DISCARDED);
        spawnedBoatPos = BlockPos.ZERO;
        resetBoatProgress();
    }

    /**
     * The index of the node the boat leg being followed unloads onto.
     * <p>
     * -1 is a normal answer, not only an error one: {@code AbstractPathJob#markBoatEntryAndExit} can only flag the
     * point <em>after</em> a leg, so a leg that runs to the last point of the path -- a crossing whose destination is
     * the water itself -- has no exit to find. Such a crossing ends by the boat reaching that last point and the path
     * completing, and failing that by the held-up check, so nothing here has to invent a landing place for it.
     *
     * @return the index of the exit, or -1 if there is no boat leg ahead with an exit to reach.
     */
    private int boatExitIndex()
    {
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++)
        {
            if (!(path.getNode(i) instanceof final PathPointExtended point))
            {
                return -1;
            }
            if (point.isBoatExit())
            {
                return i;
            }
            if (!point.isOnBoat())
            {
                // Off the end of the leg without meeting an exit: a replan cut the crossing short.
                return -1;
            }
        }
        return -1;
    }

    /**
     * Count how long a boat has failed to get any closer to what it is being steered at, and get the passenger out of
     * it once that has gone on long enough.
     * <p>
     * A hull that catches the corner of a bank stops dead: the velocity written each tick keeps pointing into the block
     * it is already against, so nothing about steering alone ever frees it. Without a way out the citizen sits there
     * for good, which is the one outcome worse than a slow crossing.
     * <p>
     * PORT-NOTE(26.2): two things changed here, and between them they are the reported "it arrived, bumped the dock,
     * and then just waited". The measure is now closing distance rather than raw displacement -- see
     * {@link #BOAT_PROGRESS}, a hull grinding along a pier moves plenty while getting nowhere, and kept resetting the
     * old counter. And there is now a rung between "held up" and "thrown in the water": a boat that is touching the
     * thing it was aiming at and can get no closer has arrived in every sense the citizen cares about, so they are
     * stepped ashore rather than made to swim a crossing that is already over.
     * <p>
     * Distance is measured horizontally, for the same reason {@link #disembark(MinecoloniesBoat)} measures it that
     * way: at a dock the node being made for is a deck a block or two above the water line, and a straight-line
     * distance would never close.
     *
     * @param boat   the boat being ridden.
     * @param target the node the boat is being steered at this tick, which is always the node at the current index.
     * @return true if the passenger was got out and the rest of the tick should be skipped.
     */
    private boolean rescueIfHeldUp(final MinecoloniesBoat boat, final PathPointExtended target)
    {
        final double distance = horizontalDistance(boat, target);

        if (boatGoal.getX() != target.x || boatGoal.getY() != target.y || boatGoal.getZ() != target.z
              || distance < boatBestDistance - BOAT_PROGRESS)
        {
            // Either the crossing has moved on to a new node, which farthestVisible only does for a node the boat can
            // see and is heading for, or it is closing on the one it has. Both are progress.
            boatGoal = new BlockPos(target.x, target.y, target.z);
            boatBestDistance = distance;
            boatStuckTicks = 0;
            return false;
        }

        if (++boatStuckTicks < BOAT_STUCK_DISEMBARK)
        {
            return false;
        }

        final int exitIndex = boatExitIndex();
        if (exitIndex >= 0 && horizontalDistance(boat, path.getNode(exitIndex)) <= BOAT_STUCK_DISEMBARK_DISTANCE)
        {
            logHeldUp(boat, target, "put ashore on the exit node, which the hull could not reach");
            putAshore(boat, exitIndex);
            return true;
        }

        if (boatStuckTicks < BOAT_STUCK_ABANDON)
        {
            return false;
        }

        logHeldUp(boat, target, "abandoned, the citizen swims the rest");
        // Leave the citizen in the water rather than trying to place them: they are mid crossing, there is no shore to
        // put them on, and swimming is a state the rest of the navigator already handles.
        mob.stopRiding();
        boat.remove(Entity.RemovalReason.DISCARDED);
        spawnedBoatPos = BlockPos.ZERO;
        resetBoatProgress();
        return true;
    }

    /**
     * Forget everything the held-up check has been counting. Called wherever a crossing ends or none is under way, so
     * that the next one starts from its own first tick rather than inheriting a distance to a node it never saw.
     */
    private void resetBoatProgress()
    {
        boatStuckTicks = 0;
        boatGoal = BlockPos.ZERO;
        boatBestDistance = Double.MAX_VALUE;
    }

    /**
     * Horizontal distance from a boat to the centre of a node's block.
     *
     * @param boat the boat.
     * @param node the node.
     * @return the distance in blocks.
     */
    private static double horizontalDistance(final MinecoloniesBoat boat, final Node node)
    {
        final double dx = node.x + 0.5D - boat.getX();
        final double dz = node.z + 0.5D - boat.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Say in the server log what a crossing that could not finish itself looked like when it was cut short.
     * <p>
     * Every field here is one the failure cannot be diagnosed without and none of it is visible in game: which citizen,
     * where the hull ended up against where it was aiming, which node of which path, and what that node was flagged as.
     * A boat is only ever cut short by one of the paths above, so this is rare and worth a warning rather than a debug
     * line.
     *
     * @param boat    the boat being ridden.
     * @param node    the node being made for.
     * @param outcome what was done about it.
     */
    private void logHeldUp(final MinecoloniesBoat boat, final Node node, final String outcome)
    {
        final String flags = node instanceof final PathPointExtended point
                               ? "onBoat=" + point.isOnBoat() + " entry=" + point.isBoatEntry() + " exit=" + point.isBoatExit()
                               : "not a minecolonies path point";

        Log.getLogger().warn(String.format(
          "Boat crossing cut short for %s: hull at %.1f/%.1f/%.1f, node %d of %d at %d/%d/%d [%s] %.2f blocks away, "
            + "closest it came %.2f, held up %d ticks -- %s",
          mob.getName().getString(),
          boat.getX(), boat.getY(), boat.getZ(),
          path.getNextNodeIndex(), path.getNodeCount(),
          node.x, node.y, node.z,
          flags,
          horizontalDistance(boat, node),
          boatBestDistance == Double.MAX_VALUE ? -1.0D : boatBestDistance,
          boatStuckTicks,
          outcome));
    }

    /**
     * The farthest node of the current boat leg the boat can make for in a straight line.
     * <p>
     * The A* works in blocks and steps around corners, which is right on land and pointless on open water: a lake has
     * no obstacles to step around, so following it node by node makes a boat trace the staircase the search happened to
     * expand instead of the diagonal any of it could have been. {@code AbstractPathJob#visitNode} expands the four
     * cardinals only, so that staircase cannot be straightened in the search; pulling the line taut against the water
     * here gives the crossing its true heading, and turns the search's grid into what it was always meant to
     * approximate.
     * <p>
     * The index is advanced to the chosen node as a matter of course: the nodes behind it are provably skippable, and
     * {@link #followThePath()} can only retire a node by walking up to it, which a boat cutting the corner never does.
     * Left alone the index would stall on a node the boat had already sailed past.
     *
     * @param boat the boat being ridden.
     * @return the node to steer at, never null -- at worst the node already being followed.
     */
    private PathPointExtended farthestVisible(final MinecoloniesBoat boat)
    {
        final int from = path.getNextNodeIndex();

        // How much leg there is to aim at: consecutive boat nodes, and no farther than the lookahead. The exit node is
        // on land and is deliberately not a candidate -- the straight line is only ever pulled taut over water.
        int last = from;
        for (int i = from + 1; i < path.getNodeCount(); i++)
        {
            if (!(path.getNode(i) instanceof final PathPointExtended candidate) || !candidate.isOnBoat())
            {
                break;
            }

            final double dx = candidate.x + 0.5D - boat.getX();
            final double dz = candidate.z + 0.5D - boat.getZ();
            if (dx * dx + dz * dz > BOAT_MAX_LOOKAHEAD * BOAT_MAX_LOOKAHEAD)
            {
                break;
            }

            last = i;
        }

        // Farthest first. Open water answers on the first test, which is the case worth being cheap in; walking out
        // from the near end instead costs the square of the crossing length in block lookups every tick.
        int targetIndex = from;
        for (int i = last; i > from; i--)
        {
            if (isClearWaterLine(boat, (PathPointExtended) path.getNode(i)))
            {
                targetIndex = i;
                break;
            }
        }

        while (path.getNextNodeIndex() < targetIndex)
        {
            path.advance();
        }
        return (PathPointExtended) path.getNode(targetIndex);
    }

    /**
     * Whether a boat could travel the straight line from where it is to a node without leaving the water.
     *
     * @param boat   the boat, its current position is the start of the line.
     * @param target the node at the far end.
     * @return true if every sample along the line is water.
     */
    private boolean isClearWaterLine(final MinecoloniesBoat boat, final PathPointExtended target)
    {
        final double fromX = boat.getX();
        final double fromZ = boat.getZ();
        final double dx = target.x + 0.5D - fromX;
        final double dz = target.z + 0.5D - fromZ;

        final double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < BOAT_LINE_STEP)
        {
            return true;
        }

        // The hull is wider than the line through its middle, so testing that line alone let a corner clip a block the
        // boat was never going to fit past -- it would touch land by a fraction of its nose and stop dead. Both flanks
        // are swept as well, half a hull out to either side.
        final double flankX = -dz / distance * BOAT_HALF_WIDTH;
        final double flankZ = dx / distance * BOAT_HALF_WIDTH;

        final int steps = (int) Math.ceil(distance / BOAT_LINE_STEP);
        for (int i = 1; i <= steps; i++)
        {
            final double progress = (double) i / steps;
            final double x = fromX + dx * progress;
            final double z = fromZ + dz * progress;

            if (!isWaterAt(x, z, target.y)
                  || !isWaterAt(x + flankX, z + flankZ, target.y)
                  || !isWaterAt(x - flankX, z - flankZ, target.y))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a boat could float at a point of a sampled line.
     *
     * @param x the x coordinate.
     * @param z the z coordinate.
     * @param y the y of the boat-leg node the line is being tested at.
     * @return true if the boat may sit here.
     */
    private boolean isWaterAt(final double x, final double z, final int y)
    {
        final int blockX = Mth.floor(x);
        final int blockZ = Mth.floor(z);

        // A boat-leg node is usually the air block *above* the water surface, not the water block: SurfaceType calls
        // water WALKABLE, so getGroundHeight returns the candidate's own y as soon as the block below it is water, and
        // AbstractPathJob#calculateSwimming counts a node as swimming when the water is below it. Sampling at the
        // node's own y alone therefore hit air on the very first sample of every such line, isClearWaterLine never
        // returned true, and the taut line was dead code -- the boat traced the cardinal staircase the search had
        // expanded, which is exactly the reported snaking.
        //
        // Both levels are accepted, the same rule the hull spawn already applies in handleBoats. A leg whose path was
        // computed while the citizen was already swimming really does sit inside the water block, because prepareStart
        // floors the entity's y.
        boatLinePos.set(blockX, y, blockZ);
        if (PathfindingUtils.isWater(level, boatLinePos))
        {
            return true;
        }

        if (level.getBlockState(boatLinePos).blocksMotion())
        {
            // The bank itself, or something standing on it. The line is cutting a corner the pathfinder never
            // evaluated, so this is the only thing stopping the hull from being aimed straight through it.
            return false;
        }

        boatLinePos.set(blockX, y - 1, blockZ);
        return PathfindingUtils.isWater(level, boatLinePos);
    }

    /**
     * Point a boat at the node currently being followed.
     * <p>
     * Where the minecart nudges its cart along one axis and lets the rails do the steering, a boat has nothing
     * constraining it, so the heading has to be produced here. The velocity is written absolutely rather than
     * accumulated as an impulse: {@code AbstractBoat#floatBoat} damps whatever it finds by 0.9 before
     * {@code move()} consumes it, we have no ordering guarantee against the boat's own tick, and overwriting means
     * the boat turns the instant the followed node changes, so it tracks the path node for node with no drift.
     * Only the horizontal components are touched -- the vertical one belongs to buoyancy.
     * <p>
     * The speed is read from the config on every tick rather than cached, which is what lets {@code /mc boatspeed}
     * take effect on boats that are already halfway across. It is stored in blocks per second and divided by
     * {@link com.minecolonies.api.util.constant.Constants#TICKS_SECOND} here, exactly the way
     * {@code NewMinecartBehavior#getMaxSpeed} handles vanilla's {@code maxMinecartSpeed} -- see
     * {@link com.minecolonies.api.configuration.ServerConfiguration#BOAT_SPEED_DEFAULT} for why that is the unit and
     * for what the boat actually travels once {@code floatBoat} has damped this.
     *
     * @param boat   the boat being ridden.
     * @param target the node to head for.
     */
    private void steerBoat(final MinecoloniesBoat boat, final PathPointExtended target)
    {
        double dx = target.x + 0.5D - boat.getX();
        double dz = target.z + 0.5D - boat.getZ();

        final double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.0001D)
        {
            return;
        }

        dx /= distance;
        dz /= distance;

        final double speed = MineColonies.getConfig().getServer().boatSpeed.get() / TICKS_SECOND;
        boat.setDeltaMovement(dx * speed, boat.getDeltaMovement().y, dz * speed);
        // Minecraft yaw runs 0 = +Z with -X at 90, i.e. the view vector is (-sin(yaw), _, cos(yaw)).
        boat.setYRot((float) (Mth.atan2(-dx, dz) * (180.0D / Math.PI)));
    }

    @Override
    protected void followThePath()
    {
        // TODO: Rework pathfollow
        getSpeedFactor();
        final int curNode = path.getNextNodeIndex();
        final int curNodeNext = curNode + 1;
        if (curNodeNext < path.getNodeCount())
        {
            if (!(path.getNode(curNode) instanceof PathPointExtended))
            {
                path = convertPath(path);
            }

            final PathPointExtended pEx = (PathPointExtended) path.getNode(curNode);
            final PathPointExtended pExNext = (PathPointExtended) path.getNode(curNodeNext);

            //  If current node is bottom of a ladder, then stay on this node until
            //  the ourEntity reaches the bottom, otherwise they will try to head out early
            if (pEx.isOnLadder() && pEx.getLadderFacing() == Direction.DOWN
                && !pExNext.isOnLadder())
            {
                final Vec3 vec3 = getTempMobPos();
                if ((vec3.y - (double) pEx.y) < MIN_Y_DISTANCE)
                {
                    this.path.setNextNodeIndex(curNodeNext);
                }
                return;
            }

            // Boats no longer depend on this: handleBoats runs earlier in the same tick and ends a crossing itself,
            // because this rule is skipped whenever the index has come to rest on the last node of a path and because
            // stopRiding leaves the passenger hanging at the seat rather than putting them anywhere. It still covers
            // the minecart, which has neither problem.
            if (!pEx.isOnRails() && !pEx.isOnBoat() && ourEntity.getVehicle() != null && !(ourEntity.getVehicle() instanceof CavalryHorseEntity))
            {
                final Entity entity = ourEntity.getVehicle();
                ourEntity.stopRiding();
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        this.maxDistanceToWaypoint = 0.5F;
        boolean wentAhead = false;
        boolean isTracking = PathfindingUtils.trackingMap.containsValue(ourEntity.getUUID());

        HashSet<BlockPos> reached = null;
        if (isTracking)
        {
            reached = new HashSet<>();
        }

        // Look at multiple points, incase we're too fast
        for (int i = this.path.getNextNodeIndex(); i < Math.min(this.path.getNodeCount(), this.path.getNextNodeIndex() + 4); i++)
        {
            // TODO: Only keep advancing if distance gets closer, instead of looping many points, check if entity pos at node is even needed, normal pos probably fine
            final Node node = path.getNode(i);
            final double nextX = (double) node.x + (double) ((int) (this.mob.getBbWidth() + 1.0F)) * 0.5D;
            final double nextY = node.y;
            final double nextZ = (double) node.z + (double) ((int) (this.mob.getBbWidth() + 1.0F)) * 0.5D;

            if (Math.abs(this.mob.getX() - nextX) < (double) this.maxDistanceToWaypoint - Math.abs(this.mob.getY() - (nextY)) * 0.1
                && Math.abs(this.mob.getZ() - nextZ) < (double) this.maxDistanceToWaypoint - Math.abs(this.mob.getY() - (nextY)) * 0.1 &&
                Math.abs(this.mob.getY() - nextY) <= 1.0D)
            {
                this.path.advance();
                wentAhead = true;

                if (isTracking)
                {
                    final Node point = path.getNode(i);
                    reached.add(new BlockPos(point.x, point.y, point.z));
                }
            }
        }

        if (isTracking)
        {
            PathfindingUtils.syncDebugReachedPositions(reached, pathResult.getDebugWatchers());
            reached.clear();
        }

        if (path.isDone())
        {
            onPathFinish();
            return;
        }

        if (wentAhead)
        {
            return;
        }

        if (curNode >= path.getNodeCount() || curNode <= 1)
        {
            return;
        }

        // Check some past nodes case we fell behind.
        final Vec3 curr = this.path.getEntityPosAtNode(this.mob, curNode - 1);
        final Vec3 next = this.path.getEntityPosAtNode(this.mob, curNode);

        // Never while boating. Being far from both nodes means "fell behind" for something that walks the chain, but it
        // is the normal state of a boat cutting a corner: farthestVisible deliberately leaves the nodes behind and
        // advances the index past them. Rewinding here undid that every tick, so the steering target flipped between
        // the far end of the stretch and a node already passed, and the boat shook and snaked its way across.
        if (ourEntity.getVehicle() instanceof MinecoloniesBoat)
        {
            return;
        }

        if (mob.position().distanceTo(curr) >= 2.0 && mob.position().distanceTo(next) >= 2.0)
        {
            int currentIndex = curNode - 1;
            while (currentIndex > 0)
            {
                final Vec3 tempoPos = this.path.getEntityPosAtNode(this.mob, currentIndex);
                if (mob.position().distanceTo(tempoPos) <= 1.0)
                {
                    this.path.setNextNodeIndex(currentIndex);
                }
                else if (isTracking)
                {
                    reached.add(BlockPos.containing(tempoPos.x, tempoPos.y, tempoPos.z));
                }
                currentIndex--;
            }
        }

        if (isTracking)
        {
            PathfindingUtils.syncDebugReachedPositions(reached, pathResult.getDebugWatchers());
            reached.clear();
        }
    }

    /**
     * Called upon reaching the path end, reset values
     */
    private void onPathFinish()
    {
        finishTime = ourEntity.level().getGameTime();
        super.stop();
    }

    public void recomputePath() {}

    /**
     * Don't let vanilla rapidly discard paths, set a timeout before its allowed to use stuck.
     */
    @Override
    protected void doStuckDetection(@NotNull final Vec3 positionVec3)
    {
        // Do nothing, unstuck is checked on tick, not just when we have a path
    }

    /**
     * Stop indicates that the entity no longer desires to move.
     */
    @Override
    public void stop()
    {
        if (pathResult != null)
        {
            pathResult.cancel();
            pathResult.setStatus(PathFindingStatus.CANCELLED);
            pathResult = null;
            if ((ourEntity.getVehicle() != null) && !(ourEntity.getVehicle() instanceof CavalryHorseEntity)
                  // A boat somebody asked to keep survives the end of the path that brought it. This is the site that
                  // matters for a citizen who was ferried to where he wanted to be: walkToPos calls stop() the moment
                  // it decides he has arrived, so without this the hull that carried him is destroyed one tick
                  // before the AI that asked for it gets to say what it wanted it for. See keepBoat.
                  && !(ourEntity.getVehicle() instanceof final MinecoloniesBoat boat && keepsItsBoat(boat)))
            {
                final Entity entity = ourEntity.getVehicle();
                ourEntity.stopRiding();
                entity.remove(Entity.RemovalReason.DISCARDED);
            }
        }

        safeDestinationPos = BlockPos.ZERO;
        stuckHandler.resetGlobalStuckTimers();

        super.stop();
    }

    /**
     * Triggers an indirect recalc, isDone() returns true now
     */
    @Override
    public void recalc()
    {
        if (pathResult != null)
        {
            pathResult.cancel();
            pathResult.setStatus(PathFindingStatus.CANCELLED);
        }
        super.stop();
    }

    @Override
    public TreePathResult walkToTree(
        final BlockPos startRestriction,
        final BlockPos endRestriction,
        final double speed,
        final List<ItemStorage> excludedTrees,
        final int dyntreesize,
        final IColony colony)
    {
        @NotNull final BlockPos start = PathfindingUtils.prepareStart(ourEntity);
        final BlockPos furthestRestriction = BlockPosUtil.getFurthestCorner(start, startRestriction, endRestriction);

        final PathJobFindTree job =
            new PathJobFindTree(CompatibilityUtils.getWorldFromEntity(mob),
                start,
                startRestriction,
                endRestriction,
                furthestRestriction,
                excludedTrees,
                dyntreesize,
                colony,
                ourEntity);

        return (TreePathResult) setPathJob(job, null, speed, true);
    }

    @Override
    public TreePathResult walkToTree(final int range, final double speed, final List<ItemStorage> excludedTrees, final int dyntreesize, final IColony colony)
    {
        @NotNull BlockPos start = PathfindingUtils.prepareStart(ourEntity);
        final BlockPos buildingPos = ((AbstractEntityCitizen) mob).getCitizenColonyHandler().getWorkBuilding().getPosition();

        if (BlockPosUtil.getDistance2D(buildingPos, mob.blockPosition()) > range * 4)
        {
            start = buildingPos;
        }

        return (TreePathResult) setPathJob(
            new PathJobFindTree(CompatibilityUtils.getWorldFromEntity(mob), start, buildingPos, range, excludedTrees, dyntreesize, colony, ourEntity), null, speed, true);
    }

    @Nullable
    @Override
    public PathResult<PathJobMoveToLocation> walkToEntity(@NotNull final Entity e, final double speed)
    {
        return walkTo(e.blockPosition(), speed, false);
    }

    @Nullable
    @Override
    public PathResult<PathJobMoveAwayFromLocation> moveAwayFromLivingEntity(@NotNull final Entity e, final double distance, final double speed)
    {
        return walkAwayFrom(e.blockPosition(), distance, speed, true);
    }

    @Override
    public void setCanFloat(boolean canSwim)
    {
        super.setCanFloat(canSwim);
        getPathingOptions().setCanSwim(canSwim);
    }

    @Override
    public BlockPos getSafeDestination()
    {
        return safeDestinationPos;
    }

    @Override
    public void setSafeDestinationPos(final BlockPos pos)
    {
        safeDestinationPos = pos;
    }

    /**
     * Sets the stuck handler
     *
     * @param stuckHandler handler to set
     */
    @Override
    public void setStuckHandler(final IStuckHandler stuckHandler)
    {
        this.stuckHandler = stuckHandler;
    }

    @Override
    public void setSwimSpeedFactor(final double factor)
    {
        this.swimSpeedFactor = factor;
    }

    @Override
    public double getAvgHeuristicModifier()
    {
        return heuristicAvg;
    }

    @Override
    public void setPauseTicks(final int pauseTicks)
    {
        if (pauseTicks > TICKS_SECOND * 120)
        {
            Log.getLogger().warn("Tried to pause entity pathfinding for " + mob + " too long for " + pauseTicks + " ticks.", new Exception());
            this.pauseTicks = 50;
        }
        else
        {
            this.pauseTicks = pauseTicks;
        }
    }

    @Override
    public PathResult getPathResult()
    {
        return pathResult;
    }

    @Override
    public IStuckHandler<MinecoloniesAdvancedPathNavigate> getStuckHandler()
    {
        return stuckHandler;
    }

    @Override
    public boolean isStuck()
    {
        return stuckHandler.getStuckLevel() >= 3;
    }

    /**
     * Gets the next node, which is the node the entity is currently moving towards
     *
     * @return the next path node
     */
    private PathPointExtended getNextNode()
    {
        return (PathPointExtended) path.getNextNode();
    }

    /**
     * Get the previous node, which is the last node the entity reached
     *
     * @return
     */
    @Nullable
    private PathPointExtended getPreviousNode()
    {
        if (path.getNextNodeIndex() > 0)
        {
            return (PathPointExtended) path.getNode(path.getNextNodeIndex() - 1);
        }

        return null;
    }

    /**
     * Get the node after the next node, which the entity will be going for after reaching the next node
     *
     * @return
     */
    @Nullable
    private PathPointExtended getNextNextNode()
    {
        if (path.getNextNodeIndex() + 1 < path.getNodeCount())
        {
            return (PathPointExtended) path.getNode(path.getNextNodeIndex() + 1);
        }

        return null;
    }
}
