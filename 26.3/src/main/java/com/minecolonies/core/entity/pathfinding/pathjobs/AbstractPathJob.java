package com.minecolonies.core.entity.pathfinding.pathjobs;

import com.ldtteam.domumornamentum.block.decorative.FloatingCarpetBlock;
import com.ldtteam.domumornamentum.block.decorative.PanelBlock;
import com.ldtteam.domumornamentum.block.decorative.ShingleBlock;
import com.ldtteam.domumornamentum.block.decorative.ShingleSlabBlock;
import com.minecolonies.api.blocks.decorative.AbstractBlockGate;
import com.minecolonies.api.blocks.decorative.AbstractBlockMinecoloniesConstructionTape;
import com.minecolonies.api.entity.pathfinding.IDynamicHeuristicNavigator;
import com.minecolonies.api.entity.pathfinding.IPathJob;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.ShapeUtil;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.blocks.BlockDecorationController;
import com.minecolonies.core.entity.pathfinding.*;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.minecolonies.core.entity.pathfinding.world.CachingBlockLookup;
import com.minecolonies.api.colony.territory.HostileTerritory;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.colony.territory.HostileTerritoryMap;
import com.minecolonies.core.entity.pathfinding.world.ChunkCache;
import com.minecolonies.core.network.messages.client.SyncPathMessage;
import com.minecolonies.core.util.WorkerUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

import static com.minecolonies.api.util.constant.PathingConstants.*;
import static com.minecolonies.core.entity.pathfinding.PathingOptions.MAX_COST;

/**
 * Abstract class for Jobs that run in the multithreaded path finder.
 */
public abstract class AbstractPathJob implements Callable<Path>, IPathJob
{
    /**
     * PORT-NOTE(26.2/Fabric): NeoForge's {@code BlockState#getBlockPathType(level, pos, mob)}
     * extension is gone. Vanilla's equivalent is
     * {@code WalkNodeEvaluator#getPathTypeFromState}, which is {@code protected static} — this
     * abstract subclass exists only to reach it. It is never instantiated.
     */
    private abstract static class VanillaPathTypes extends net.minecraft.world.level.pathfinder.WalkNodeEvaluator
    {
        static PathType of(final net.minecraft.world.level.BlockGetter level, final net.minecraft.core.BlockPos pos)
        {
            return getPathTypeFromState(level, pos);
        }
    }

    /**
     * Maximium amount of nodes explored
     */
    public static final int MAX_NODES = 8000;


    /**
     * The longest run, in blocks, that one macro edge may cover -- of water for a boat probe, of track for a rail walk.
     * <p>
     * Deliberately not configurable. It trades two things off against each other and neither of them is a matter of
     * taste: longer edges cost fewer nodes on open sea, shorter ones follow a coastline more closely because the
     * search gets to reconsider its heading more often. 64 crosses a typical river or lake in one edge and an ocean
     * in a handful, while staying far inside the 4096 block cube {@link MNode#computeNodeKey} can tell apart and well
     * inside the box {@link ChunkCache} was built over.
     * <p>
     * PORT-NOTE(26.2): shared with the rail walk rather than given its own constant, and the reasoning above survives
     * the move intact even though a rail is a corridor rather than a plane. The thing a shorter edge buys back is the
     * same in both media -- the chance to stop and reconsider -- it is just that on a track "reconsider" means "get
     * out here" rather than "steer elsewhere", which is why {@link #walkRailEdge} lays a second node at the track's
     * closest approach to the goal instead of relying on the length cap to produce one.
     */
    private static final int MAX_EDGE_LENGTH = 64;

    /**
     * How far past the box between start and destination the block cache may reach, in blocks.
     * <p>
     * The margin exists so the search can leave the straight line to get around something, and it used to be half the
     * Manhattan distance, i.e. it grew without limit. That is the one cost in this whole file that genuinely scales
     * with how far the citizen was told to walk, and it is paid on the <em>server</em> thread, inside the citizen's
     * own tick, by {@link ChunkCache}'s constructor -- which walks every chunk of the box eagerly and allocates a
     * reference for each. Previously measured at 0.09 ms for a 20 block order and 2.0 ms for a 2000 block one; at 3000
     * blocks the box is about 6000 by 3000 and the array alone is over half a megabyte, thrown away again as soon as
     * the search ends. Repeat that every tick for a worker whose destination it cannot reach and it is a real bill.
     * <p>
     * Capping it does not cost a route that was going to succeed anything. The margin is room to detour, and a route
     * that needs to detour 1500 blocks sideways has long since run out of node budget; a route that succeeds over that
     * distance is a corridor -- rails or open water -- and a corridor does not wander 128 blocks off its own line. So
     * this is the change that makes a raised {@code maxpathfindingdistance} affordable rather than merely permitted,
     * and it is the only distance-dependent cost there was to make affordable: the node budget, which is what the
     * single pathfinding thread actually spends its time on, is already capped at {@link #MAX_NODES} and is the same
     * for a 3000 block order as for a 300 block one.
     */
    private static final int MAX_CACHE_MARGIN = 128;

    /**
     * The four horizontal directions, held as an array so walking a track allocates no iterator.
     */
    private static final Direction[] HORIZONTAL_DIRECTIONS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /**
     * What one block of hostile territory is added to the price of a step, walking or swimming.
     * <p>
     * A surcharge and not a veto, deliberately, and the two cases that decide it are the awkward ones. A citizen who
     * is <em>already</em> standing in hostile ground has to be able to walk out of it, and under a veto every node
     * around him is untraversable, so he finds no path at all, hands the problem to {@code PathingStuckHandler} and
     * ends up teleported. A hostile strip that cuts a colony in two is the same shape of problem: under a veto the
     * far half simply stops being reachable and the workers assigned there stand still. A surcharge degrades into
     * "walk round it if there is a way round, walk through it if there is not", which is the behaviour that was
     * actually asked for — that they go around.
     * <p>
     * 25 rather than a multiplier because an ordinary step costs about one, so this reads directly as "one block of
     * enemy ground is worth twenty five blocks of detour". A territory twenty blocks across is therefore walked
     * around by anything up to a five hundred block detour, which no citizen has the node budget to want; a five
     * block strip costs 125 to cross, so a route that would have to go a kilometre round crosses it instead. It is
     * also the value of {@code PathingOptions#MAX_COST}, which is the ceiling the node-budget bonus is clamped to, so
     * a hostile step buys the same extra search budget as any other expensive step and no more.
     */
    private static final double HOSTILE_GROUND_COST = 25.0;

    /**
     * Start position to path from.
     */
    @NotNull
    protected final BlockPos start;

    /**
     * The pathing cache.
     */
    @NotNull
    protected final LevelReader world;

    /**
     * The original world, do not use offthread
     */
    private final Level actualWorld;

    /**
     * The entity this job belongs to, can be none
     */
    @Nullable
    protected Mob entity = null;

    /**
     * Cached block lookup
     */
    protected CachingBlockLookup cachedBlockLookup;

    /**
     * Mutable pos used ot retrieve world info directly
     */
    protected BlockPos.MutableBlockPos tempWorldPos = new BlockPos.MutableBlockPos();

    /**
     * The result of the path calculation.
     */
    protected final PathResult result;

    /**
     * Maximum nodes we can visit.
     */
    protected int maxNodes;

    /**
     * Queue of all open nodes.
     */
    private Queue<MNode> nodesToVisit;

    /**
     * Queue of all the visited nodes.
     */
    private final Int2ObjectOpenHashMap<MNode> nodes = new Int2ObjectOpenHashMap<>();

    /**
     * Counts of nodes
     */
    private   int totalNodesAdded   = 0;
    protected int totalNodesVisited = 0;

    /**
     * When this job was handed to the pathfinding pool, from {@link System#nanoTime()}, or 0 if it was not stamped.
     * <p>
     * Diagnostics only, for {@link PathfindingStats}: written on the server thread before the job is submitted and read
     * on the pool thread when the job starts, which is how the queue wait is measured. Submitting a task to an executor
     * already happens-before the task runs, so this hand-off is ordered by the memory model; {@code volatile} states
     * that intent in the code rather than relying on the reader knowing it.
     */
    private volatile long submitNanos = 0L;

    /**
     * Whether the search gave up because it ran out of node budget, for {@link PathfindingStats}.
     * <p>
     * Set at the one place the loop actually breaks out on the limit, which is not the same thing as having visited
     * {@code maxNodes} nodes: the real cutoff is {@code maxNodes + maxCost² * 2}, and past it a subclass may still
     * decline to stop. Inferring this from the node count outside would count searches that finished normally.
     */
    private boolean hitNodeLimit = false;

    /**
     * How many nodes had been expanded when the destination was first reached, or -1 if it never was.
     * <p>
     * The search does not stop when it reaches: it goes on expanding everything cheaper than the route it just found
     * (see the exit condition in {@link #search()}). This field is what lets {@code /mc pathstats} say how much of the
     * single pathfinding thread's time is spent in that tail rather than in getting there, which is the number that
     * decides whether an earlier exit is worth anything on a real colony. Diagnostics only, never read by the search.
     */
    private int nodesAtReach = -1;

    /**
     * {@link System#nanoTime()} at the moment the destination was first reached, or 0 when the job is not being
     * sampled. The node count above is the honest measure of work; this one is the measure of time, and they differ
     * because a node reached over a macro edge costs more to expand than an ordinary step.
     */
    private long reachNanos = 0L;

    /**
     * The route as it stood the moment the destination was first reached: its cost and its length in blocks. Compared
     * against the same two numbers for the route actually returned, this says what an exit at first reach would cost
     * in path quality -- the search can still re-parent the destination node onto a cheaper chain after reaching, and
     * whether it usefully does so is a question for measurement rather than for argument. Sampling only, and computed
     * by walking the parent chain, which is why it is behind {@link PathfindingStats#isSampling()}.
     */
    private double reachPathCost   = 0;
    private int    reachPathBlocks = 0;

    /**
     * Additional nodes that get explored when reaching the target, useful when the destination is an area or not in a great spot.
     * Pathjobs may increase this value as they see fit
     */
    public int extraNodes = 0;

    /**
     * Debug settings
     */
    protected boolean    debugDrawEnabled       = false;
    protected Set<MNode> debugNodesVisited      = null;
    protected Set<MNode> debugNodesVisitedLater = null;
    protected Set<MNode> debugNodesNotVisited   = null;
    protected Set<MNode> debugNodesPath         = null;
    protected Set<MNode> debugNodesOrgPath      = null;
    protected Set<MNode> debugNodesExtra        = null;

    /**
     * The cost values for certain nodes.
     */
    private PathingOptions pathingOptions = new PathingOptions();

    /**
     * How many water nodes in a row make a crossing worth a boat.
     * <p>
     * Read once here rather than per node: this is consulted while costing every water node, and a path job runs on a
     * worker thread while the config is owned by the server thread. An instance initialiser runs during construction,
     * i.e. on the thread that created the job.
     */
    private final int minWaterToBoat = MineColonies.getConfig().getServer().minimumWaterToBoat.get();

    /**
     * Where the hostile territories of this dimension are, or null when there are none and for every path job that is
     * not a citizen's.
     * <p>
     * <b>Why a field and not a lookup.</b> Taken once, in the constructor, on the thread that created the job. That is
     * the same discipline {@link #minWaterToBoat} above follows and for the same reason — but here it also settles a
     * correctness question, not merely a cost one. A path job runs on a worker thread, and the mod's own ownership map
     * cannot be read from there at all: {@code IColonyManager#getClaimData} is a {@code computeIfAbsent} on a plain
     * {@code HashMap}, so asking it per node would be a data race, not a slow answer. (The one class that ever did
     * that, {@code PathJobPathway}, carries a TODO saying so and is dead code.)
     * <p>
     * <b>Why no separate snapshot.</b> {@link HostileTerritoryMap} is already immutable and already safe to read from
     * any thread, so copying the hostile chunks into a per-job set would buy nothing. Holding the reference for the
     * job's lifetime is still worth doing on its own account: a player repainting a border mid-search cannot then make
     * the same node cost two different things at two points of the same A*.
     * <p>
     * <b>What it costs when the feature is unused.</b> Null. Every world without a territory in it pays one map lookup
     * per job in the constructor, and one reference comparison per expanded node — no allocation, no colony resolved,
     * no chunk touched. Nothing else in the search changes.
     */
    @Nullable
    private final HostileTerritoryMap hostileGround;

    /**
     * Whether to stop the search once nothing left in the queue can beat the route already found, rather than expanding
     * everything cheaper than it. See {@link #search()} for what the two exit conditions actually are and
     * {@code 26.2/PATHFINDING-EXIT.md} for what each costs.
     * <p>
     * Read here, on the thread that constructs the job, for the same reason {@link #minWaterToBoat} is: the search runs
     * on the pathfinding pool and the config belongs to the server thread.
     */
    private final boolean stopOnArrival = MineColonies.getConfig().getServer().stopSearchOnArrival.get();

    /**
     * Whether the path reached its destination
     */
    private boolean reachesDestination = false;

    /**
     * The maximum cost discoverd
     */
    private double maxCost = 0;

    /**
     * Heuristic modifier
     */
    protected double heuristicMod = 2;

    /**
     * First node
     */
    private MNode startNode = null;

    /**
     * Current best node
     */
    private MNode bestNode = null;

    /**
     * Visited level
     */
    private int visitedLevel = 1;

    /**
     * AbstractPathJob constructor.
     *
     * @param world  the world within which to path.
     * @param start  the start position from which to path from.
     * @param range  estimated maximum path range.
     * @param result path result.
     * @param entity the entity.
     */
    public AbstractPathJob(final Level world, @NotNull final BlockPos start, int range, final PathResult result, @Nullable final Mob entity)
    {
        range = Math.max(10, range);

        // 30% Extra range to account for heuristics/cost based less circular exploring
        final int minX = (int) (start.getX() - range * 1.3);
        final int minZ = (int) (start.getZ() - range * 1.3);
        final int maxX = (int) (start.getX() + range * 1.3);
        final int maxZ = (int) (start.getZ() + range * 1.3);
        this.world = new ChunkCache(world, new BlockPos(minX, 0, minZ), new BlockPos(maxX, 0, maxZ));
        this.actualWorld = world;
        this.hostileGround = hostileGroundFor(world, entity);

        this.maxNodes = Math.min(MAX_NODES, range * range);
        nodesToVisit = new PriorityQueue<>(range * 2);
        this.start = start.immutable(); // 26.3: BlockPos copy ctor removed; job is async, so keep the defensive copy against MutableBlockPos callers.

        cachedBlockLookup = new CachingBlockLookup(start, this.world);

        this.result = result;
        result.setJob(this);

        this.entity = entity;
        if (entity != null && entity.getNavigation() instanceof IDynamicHeuristicNavigator navigator)
        {
            heuristicMod = 1 + navigator.getAvgHeuristicModifier();
        }

        this.maxNodes = (int) (maxNodes * MineColonies.getConfig().getServer().pathNodeLimitMultiplier.get());
    }

    /**
     * Internal constructor, for secondary pathjobs within another one
     *
     * @param chunkCache
     * @param start
     * @param range
     * @param result
     * @param entity
     */
    protected AbstractPathJob(final Level actualWorld, final LevelReader chunkCache, @NotNull final BlockPos start, int range, final PathResult result, @Nullable final Mob entity)
    {
        range = Math.max(10, range);
        this.maxNodes = Math.min(MAX_NODES, range * range);
        nodesToVisit = new PriorityQueue<>(range * 2);
        this.start = start.immutable(); // 26.3: BlockPos copy ctor removed; job is async, so keep the defensive copy against MutableBlockPos callers.

        world = chunkCache;
        cachedBlockLookup = new CachingBlockLookup(start, this.world);
        this.actualWorld = actualWorld;
        this.hostileGround = hostileGroundFor(actualWorld, entity);

        this.result = result;
        result.setJob(this);

        this.entity = entity;
        if (entity != null && entity.getNavigation() instanceof IDynamicHeuristicNavigator navigator)
        {
            heuristicMod = 1 + navigator.getAvgHeuristicModifier();
        }

        this.maxNodes = (int) (maxNodes * MineColonies.getConfig().getServer().pathNodeLimitMultiplier.get());
    }

    /**
     * AbstractPathJob constructor.
     *
     * @param world  the world within which to path.
     * @param start  the start position from which to path from.
     * @param result path result.
     * @param entity the entity.
     */
    public AbstractPathJob(final Level world, @NotNull final BlockPos start, @NotNull final BlockPos end, final PathResult result, @Nullable final Mob entity)
    {
        // Load at least 2 chunks further around start+end, extended with more distance
        final int expandedRange = (2 * 16) + Math.min(MAX_CACHE_MARGIN, BlockPosUtil.distManhattan(start, end) / 2);

        final int minX = Math.min(start.getX(), end.getX()) - expandedRange;
        final int minZ = Math.min(start.getZ(), end.getZ()) - expandedRange;
        final int maxX = Math.max(start.getX(), end.getX()) + expandedRange;
        final int maxZ = Math.max(start.getZ(), end.getZ()) + expandedRange;
        this.world = new ChunkCache(world, new BlockPos(minX, 0, minZ), new BlockPos(maxX, 0, maxZ));

        // Max nodes in relation to the box area
        final int xDiff = Math.max(1, Math.abs(start.getX() - end.getX()));
        final int yDiff = Math.max(1, Math.abs((start.getY() - end.getY())));
        final int zDiff = Math.max(1, Math.abs((start.getZ() - end.getZ())));

        final int directDistance = xDiff + yDiff + zDiff;
        final int corridorRadius = Math.min(20, 3 + directDistance / 20);
        final int corridorVolume = directDistance * corridorRadius * corridorRadius;
        final int estimate = 300 + directDistance * 16 + corridorVolume * 2;
        this.maxNodes = Math.min(MAX_NODES, estimate);

        nodesToVisit = new PriorityQueue<>(maxNodes / 4);
        this.start = start.immutable(); // 26.3: BlockPos copy ctor removed; job is async, so keep the defensive copy against MutableBlockPos callers.

        cachedBlockLookup = new CachingBlockLookup(start, this.world);
        actualWorld = world;
        this.hostileGround = hostileGroundFor(world, entity);

        this.result = result;
        result.setJob(this);
        this.entity = entity;
        if (entity != null && entity.getNavigation() instanceof IDynamicHeuristicNavigator navigator)
        {
            heuristicMod = 1 + navigator.getAvgHeuristicModifier();
        }

        this.maxNodes = (int) (maxNodes * MineColonies.getConfig().getServer().pathNodeLimitMultiplier.get());
    }

    /**
     * Callable method for initiating asynchronous task.
     *
     * @return path to follow or null.
     */
    @Override
    public final Path call()
    {
        final long startedNanos = PathfindingStats.onStart(this);
        Path path = null;
        try
        {
            path = search();
        }
        catch (final Exception e)
        {
            Log.getLogger().warn("Pathfinding Exception from: " + start + " range: " + Math.sqrt(maxNodes) + " entity: " + entity + " type: " + getClass().getSimpleName(), e);
        }

        PathfindingStats.onFinish(this, startedNanos, path, totalNodesVisited, hitNodeLimit);
        return path;
    }

    /**
     * When this job was submitted to the pool, for {@link PathfindingStats}. 0 if it was not stamped.
     *
     * @return the submission timestamp.
     */
    public long getSubmitNanos()
    {
        return submitNanos;
    }

    /**
     * Stamp the submission time, for {@link PathfindingStats}. Called on the server thread before the job is submitted.
     *
     * @param nanos the timestamp.
     */
    public void setSubmitNanos(final long nanos)
    {
        this.submitNanos = nanos;
    }

    /**
     * @return how many nodes had been expanded when the destination was first reached, or -1 if it never was.
     */
    public int getNodesAtReach()
    {
        return nodesAtReach;
    }

    /**
     * @return {@link System#nanoTime()} when the destination was first reached, or 0 if it never was or the job was not
     *     sampled.
     */
    public long getReachNanos()
    {
        return reachNanos;
    }

    /**
     * @return what the route cost when the destination was first reached, or 0 if it never was or was not sampled.
     */
    public double getReachPathCost()
    {
        return reachPathCost;
    }

    /**
     * @return how many blocks the route covered when the destination was first reached.
     */
    public int getReachPathBlocks()
    {
        return reachPathBlocks;
    }

    /**
     * @return what the route finally returned cost, or 0 if there is none.
     */
    public double getFinalPathCost()
    {
        return bestNode == null ? 0 : bestNode.getCost();
    }

    /**
     * @return how many blocks the route finally returned covers.
     */
    public int getFinalPathBlocks()
    {
        return bestNode == null ? 0 : pathBlocks(bestNode);
    }

    /**
     * How many blocks of ground a node's route back to the start covers. A macro edge stands for as many blocks as it
     * rides, so this is not the same as counting nodes.
     *
     * @param node the node to walk back from.
     * @return the length in blocks.
     */
    private static int pathBlocks(final MNode node)
    {
        int blocks = 0;
        MNode current = node;
        while (current.parent != null)
        {
            blocks += current.getEdgeLength();
            current = current.parent;
        }
        return blocks;
    }

    /**
     * Sets the initial first node up
     *
     * @return
     */
    private MNode getAndSetupStartNode()
    {
        final MNode startNode = new MNode(null, start.getX(), start.getY(), start.getZ(), 0, computeHeuristic(start.getX(), start.getY(), start.getZ()) * heuristicMod);

        if (PathfindingUtils.isLadder(cachedBlockLookup.getBlockState(start.getX(), start.getY(), start.getZ()), pathingOptions))
        {
            startNode.setLadder();
        }
        else if (!pathingOptions.canWalkUnderWater() && PathfindingUtils.isLiquid(cachedBlockLookup.getBlockState(start.below())))
        {
            startNode.setSwimming();
        }

        startNode.setOnRails(pathingOptions.canUseRails() && cachedBlockLookup.getBlockState(start).getBlock() instanceof BaseRailBlock);
        startNode.setOnBoat(pathingOptions.canUseBoat() && isBoatableSurface(start.getX(), start.getY(), start.getZ()));
        startNode.setBoatRunLength(startNode.isOnBoat() ? 1 : 0);

        nodesToVisit.offer(startNode);
        nodes.put(MNode.computeNodeKey(start.getX(), start.getY(), start.getZ()), startNode);

        ++totalNodesAdded;

        this.startNode = startNode;
        return startNode;
    }

    /**
     * Perform the search.
     *
     * @return Path of a path to the given location, a best-effort, or null.
     */
    @Nullable
    protected Path search()
    {
        // Expire stale recent-target entries once per job. This used to happen inside
        // RecentTargetCache#getExtraCost, i.e. once per expanded node, where the System.currentTimeMillis() it
        // opens with dominated the cost of the lookup itself. See the note there.
        RecentTargetCache.cleanup();

        bestNode = getAndSetupStartNode();
        double bestNodeEndScore = getEndNodeScore(bestNode);
        // Node count since we found a better end node than the current one
        int nodesSinceEndNode = 0;

        while (!nodesToVisit.isEmpty())
        {
            if (Thread.currentThread().isInterrupted())
            {
                return null;
            }

            final MNode node = nodesToVisit.poll();

            if (node.isVisited())
            {
                // Revisiting is used to update neighbours to an updated cost
                visitNode(node);
                node.increaseVisited();
                continue;
            }

            nodesSinceEndNode++;
            totalNodesVisited++;

            // Limiting max amount of nodes mapped, encountering a high cost node increases the limit
            if (totalNodesVisited > maxNodes + (maxCost * maxCost) * 2)
            {
                if (stopOnNodeLimit(totalNodesVisited, bestNode, nodesSinceEndNode))
                {
                    hitNodeLimit = true;
                    break;
                }
            }

            if (!reachesDestination && isAtDestination(node))
            {
                bestNode = node;
                bestNodeEndScore = getEndNodeScoreWithExtraCost(node);
                result.setPathReachesDestination(true);
                handleDebugPathReach(bestNode);

                reachesDestination = true;
                nodesAtReach = totalNodesVisited;
                if (PathfindingStats.isSampling())
                {
                    reachNanos = System.nanoTime();
                    reachPathCost = node.getCost();
                    reachPathBlocks = pathBlocks(node);
                }

                // Deliberately no heuristic rebalance on arrival when the f-bound below is in charge. That call exists
                // to re-aim a search that has been reading the terrain wrongly, and on arrival it always finds the
                // same thing -- the route cost a fraction of what the heuristic quoted for the distance -- so it
                // collapses heuristicMod towards the real cost per block. That is exactly the wrong thing to do for
                // an f-test, which only prunes while h is large: rebalanced down to the truth, f stops separating the
                // frontier from the route found and the rest of the search degenerates into Dijkstra over everything
                // cheaper than it. Left alone, the queue is still ordered by the inflated heuristic that got the
                // search here, and the bound below retires it in a pop or two.
                if (!stopOnArrival)
                {
                    if (reevaluteHeuristic(bestNode, true))
                    {
                        recalcHeuristic(bestNode);
                    }
                    else
                    {
                        break;
                    }
                }
            }

            // Re-evaluate current heuristic when progress is going bad
            if (((nodesSinceEndNode >= maxNodes / 2 && nodesSinceEndNode % 400 == 0)) && !reachesDestination)
            {
                if (reevaluteHeuristic(bestNode, reachesDestination))
                {
                    recalcHeuristic(bestNode);
                    recalcHeuristic(node);
                }
            }

            if (!node.isCornerNode())
            {
                // Calculates a score for a possible end node, defaults to heuristic(closest)
                final double nodeEndSCore = getEndNodeScoreWithExtraCost(node);
                if (nodeEndSCore < bestNodeEndScore)
                {
                    if (!reachesDestination || isAtDestination(node))
                    {
                        nodesSinceEndNode = 0;
                        bestNode = node;
                        bestNodeEndScore = nodeEndSCore;
                    }
                }
            }

            // Don't keep searching more costly nodes when there is a destination.
            //
            // The two arms are the same idea told with different information. The default arm is a g-test: it retires
            // the search only once the queue hands it a node that already cost more than the whole route found, which
            // on cheap connected ground means expanding every node in reach of that price -- the corridor, the lake,
            // the road. That is where the pathfinding thread's post-arrival time goes, and it is nearly all of the
            // cost of a long ride by rail or boat, where the route is found in tens of nodes and confirmed in
            // thousands.
            //
            // The other arm adds the heuristic back in, which is the textbook A* termination test: nothing still in
            // the queue can beat the route found once even the cheapest open node's f exceeds its cost. It is only as
            // trustworthy as the heuristic, and this one is not admissible -- Manhattan distance times heuristicMod,
            // which starts at 2 and is moved during the search, against steps that cost as little as a tenth of a
            // block on rails -- so what it retires is not provably the optimal route. That is the trade the config
            // names, and the measured size of it is in 26.2/PATHFINDING-EXIT.md: about 4 % dearer routes on an
            // ordinary colony, against the whole of the post-arrival phase.
            if (reachesDestination)
            {
                if (stopOnArrival)
                {
                    if (node.getCost() + node.getHeuristic() > bestNode.getCost())
                    {
                        break;
                    }
                }
                else if (node.getCost() > bestNode.getCost())
                {
                    if (reevaluteHeuristic(bestNode, reachesDestination))
                    {
                        recalcHeuristic(bestNode);
                        recalcHeuristic(node);
                    }
                    else
                    {
                        break;
                    }
                }
            }

            handleDebugOptions(node);
            visitNode(node);
            node.increaseVisited();
        }

        // Explore additional possible endnodes after reaching, if we got extra nodes to search
        if (extraNodes > 0 && reachesDestination)
        {
            // Make sure to expand from the final node
            visitNode(bestNode);

            if (!nodesToVisit.isEmpty())
            {
                // Search only closest nodes to the goal
                final Queue<MNode> original = nodesToVisit;
                nodesToVisit = new PriorityQueue<>(nodesToVisit.size(), (a, b) -> {
                    if ((a.getHeuristic()) < (b.getHeuristic()))
                    {
                        return -1;
                    }
                    else if (a.getHeuristic() > b.getHeuristic())
                    {
                        return 1;
                    }
                    else
                    {
                        return a.getCounterAdded() - b.getCounterAdded();
                    }
                });
                nodesToVisit.addAll(original);

                while (!nodesToVisit.isEmpty())
                {
                    if (Thread.currentThread().isInterrupted())
                    {
                        return null;
                    }

                    final MNode node = nodesToVisit.poll();
                    if (node.isVisited())
                    {
                        visitNode(node);
                        continue;
                    }

                    handleDebugExtraNode(node);

                    final double nodeEndSCore = getEndNodeScoreWithExtraCost(node);
                    if (nodeEndSCore < bestNodeEndScore && (!reachesDestination || isAtDestination(node)))
                    {
                        bestNode = node;
                        bestNodeEndScore = nodeEndSCore;
                    }

                    if (extraNodes > 0)
                    {
                        extraNodes--;
                        if (extraNodes == 0)
                        {
                            break;
                        }
                    }
                    visitNode(node);
                }
            }
        }

        RecentTargetCache.add(new BlockPos(bestNode.x, bestNode.y, bestNode.z), 50);
        return finalizePath(bestNode);
    }

    /**
     * Stops the pathjob when hitting a node limit
     *
     * @param totalNodesVisited
     * @param bestNode
     * @param nodesSinceEndNode
     * @return
     */
    protected boolean stopOnNodeLimit(final int totalNodesVisited, final MNode bestNode, final int nodesSinceEndNode)
    {
        return true;
    }

    /**
     * Analyzes the heuristic for an overestimation
     *
     * @param node    currently "best" node
     * @param reaches if we did reach the destination
     * @return true if the heuristic estimation got adjusted
     */
    private boolean reevaluteHeuristic(final MNode node, final boolean reaches)
    {
        if (startNode.getHeuristic() < 0.01)
        {
            return false;
        }

        double costPerEstimation = node.getCost() / startNode.getHeuristic();

        if (!reaches)
        {
            if (node.parent != null && this instanceof IDestinationPathJob job)
            {
                final double heuristicCostEstimationPerDist = startNode.getHeuristic() / Math.max(1, BlockPosUtil.dist(job.getDestination(), start));

                // Both halves of this ratio have to be per block or it is not a ratio of anything. The line above is
                // heuristic units per block of straight-line distance to the destination; this is the path's real
                // cost per block of the route actually walked, and it decides -- a few lines down -- whether the
                // heuristic is judged to be over- or under-estimating and by how much heuristicMod is then moved.
                //
                // It used to add one per node. That was the same number until macro edges, where one node stands for
                // as much as MAX_EDGE_LENGTH blocks; on a route made of edges the count came out a fraction of the
                // distance travelled, the cost per block read that many times too high, and the ratio pushed the
                // heuristic greedier than the terrain warranted. Rails make it worse than water did, because a rail
                // edge is both longer in practice and an order of magnitude cheaper per block, so the two errors
                // multiply.
                //
                // Summing the edge lengths is exactly the old count wherever no macro edge is involved: an ordinary
                // step carries a length of one, and so does a corner node, which is created by the cardinal
                // expansion and never by an edge. So this changes nothing on any path that has no edge in it, and
                // the only behaviour it can affect is the behaviour edges introduced.
                int dist = 0;
                MNode currNode = node;
                while (currNode.parent != null)
                {
                    dist += currNode.getEdgeLength();
                    currNode = currNode.parent;
                }

                final double realCostPerDist = node.getCost() / dist;
                costPerEstimation = realCostPerDist / heuristicCostEstimationPerDist;
            }
            else
            {
                int count = 0;
                costPerEstimation = 0;
                // Assume linearity
                double lowestAroundStart = Double.MAX_VALUE;
                lowestAroundStart = Math.min(lowestAroundStart, computeHeuristic(startNode.x + 1, startNode.y, startNode.z) * heuristicMod);
                lowestAroundStart = Math.min(lowestAroundStart, computeHeuristic(startNode.x - 1, startNode.y, startNode.z) * heuristicMod);
                lowestAroundStart = Math.min(lowestAroundStart, computeHeuristic(startNode.x, startNode.y, startNode.z + 1) * heuristicMod);
                lowestAroundStart = Math.min(lowestAroundStart, computeHeuristic(startNode.x, startNode.y, startNode.z - 1) * heuristicMod);
                lowestAroundStart = Math.min(lowestAroundStart, computeHeuristic(startNode.x, startNode.y + 1, startNode.z) * heuristicMod);
                lowestAroundStart = Math.min(lowestAroundStart, computeHeuristic(startNode.x, startNode.y - 1, startNode.z) * heuristicMod);

                final double heuristicPerDist = startNode.getHeuristic() - lowestAroundStart;

                if (heuristicPerDist <= 0)
                {
                    return false;
                }

                for (final MNode cur : nodesToVisit)
                {
                    if (cur.getHeuristic() >= startNode.getHeuristic() || cur.isVisited())
                    {
                        continue;
                    }

                    count++;
                    costPerEstimation += cur.getCost() / (BlockPosUtil.distManhattan(cur.x, cur.y, cur.z, startNode.x, startNode.y, startNode.z) * heuristicPerDist);

                    if (count == 20)
                    {
                        break;
                    }
                }

                if (count == 0)
                {
                    return false;
                }

                costPerEstimation = costPerEstimation / count;
            }
        }

        if (costPerEstimation <= 0.0)
        {
            return false;
        }

        // Detect an overstimating heuristic(not guranteed, but can check the found path)
        if (costPerEstimation < 0.9 || (costPerEstimation > 1.2 && !reaches) || visitedLevel == 1)
        {
            // Overshoot a bit
            costPerEstimation *= costPerEstimation < 1 ? 0.9 : 1.1;

            if (reaches && entity != null && entity.getNavigation() instanceof IDynamicHeuristicNavigator navigator)
            {
                double foundPathCostPerDist = node.getCost() / Math.max(1, BlockPosUtil.distManhattan(start, node.x, node.y, node.z));

                // If the path we found is per block more expensive than the entities historic we explore more for a potential cheaper path
                if (foundPathCostPerDist > navigator.getAvgHeuristicModifier())
                {
                    double modifier = Math.min(0.8, Math.max(0.3, navigator.getAvgHeuristicModifier() / foundPathCostPerDist));
                    costPerEstimation *= modifier;
                }
            }

            // Set a future heuristic modification
            if (reaches)
            {
                heuristicMod *= costPerEstimation;
            }
            else
            {
                // When not reaching slowly adjust to assist reaching
                double currentMod = heuristicMod;
                heuristicMod -= heuristicMod / 2;
                heuristicMod += (currentMod * costPerEstimation) / 2;
            }

            // Fix up existing heuristic values
            final List<MNode> nodes = new ArrayList<>(nodesToVisit);
            nodesToVisit.clear();
            for (final MNode recalc : nodes)
            {
                recalcHeuristic(recalc);
                nodesToVisit.offer(recalc);
            }

            recalcHeuristic(startNode);
            recalcHeuristic(node);
            visitedLevel++;
            return true;
        }

        return false;
    }

    /**
     * Recalculates the heuristic value for the node
     *
     * @param node given node
     */
    private void recalcHeuristic(final MNode node)
    {
        node.setHeuristic(computeHeuristic(node.x, node.y, node.z) * heuristicMod);
    }

    /**
     * Visits the given node and explores neighbours
     *
     * @param node
     */
    protected void visitNode(final MNode node)
    {
        cachedBlockLookup.resetToNextPos(node.x, node.y, node.z);

        int dX = 0;
        int dY = 0;
        int dZ = 0;

        if (node.parent != null)
        {
            dX = node.x - node.parent.x;
            dY = node.y - node.parent.y;
            dZ = node.z - node.parent.z;
        }

        if (node.isLadder() || node.isVisited())
        {
            exploreInDirection(node, 0, 1, 0);
            exploreInDirection(node, 0, -1, 0);
        }
        // Only explore downwards when dropping
        else if (node.isCornerNode() && (node.parent == null || !(dX == 0 && dY == 1 && dZ == 0)))
        {
            exploreInDirection(node, 0, -1, 0);
            return;
        }
        // Walk downwards node if passable
        else if (!node.isSwimming() && isPassable(node.x, node.y - 1, node.z, false, node.parent))
        {
            exploreInDirection(node, 0, -1, 0);
        }

        // N
        if (dZ <= 0)
        {
            exploreInDirection(node, 0, 0, -1);
        }

        // E
        if (dX >= 0)
        {
            exploreInDirection(node, 1, 0, 0);
        }

        // S
        if (dZ >= 0)
        {
            exploreInDirection(node, 0, 0, 1);
        }

        // W
        if (dX <= 0)
        {
            exploreInDirection(node, -1, 0, 0);
        }

        exploreBoatEdges(node);
        exploreRailEdges(node);
    }

    /**
     * Fan macro edges out over open water from this node, if it is a node worth fanning from.
     * <p>
     * Water is the one terrain where expanding block by block buys nothing. A lake has no obstacles to route around,
     * so every node in the middle of it has the same four neighbours and the search spends thousands of nodes
     * re-discovering that the water is still water. A probe walks a straight line of boatable surface instead and
     * hands back a single node at the far end, so a crossing costs a handful of nodes rather than one per block.
     * <p>
     * Only a few nodes get to fan, or the fans themselves would become the cost: the start, the node where the path
     * first touches water, and the far end of an edge already laid. Ordinary mid-water nodes, which only exist
     * because a cardinal step happened to create them, are left alone -- an edge from them would duplicate an edge
     * the fan of their run's source already covers.
     *
     * @param node the node being expanded.
     */
    private void exploreBoatEdges(final MNode node)
    {
        if (node.isVisited() || node.isLadder() || node.isCornerNode())
        {
            // Revisits only re-cost existing neighbours, and the ladder branch above is a vertical case; neither has
            // any business creating new water nodes.
            return;
        }

        if (!pathingOptions.canUseBoat() || !pathingOptions.canSwim() || !node.isOnBoat())
        {
            return;
        }

        if (!(this instanceof final IDestinationPathJob destinationJob) || !allowsBoatMacroEdges())
        {
            // Probing needs somewhere to aim: without a destination there is no way to stop an edge from jumping
            // clean over the thing being looked for. Jobs that search rather than travel -- water, trees, line of
            // sight, random walks -- keep the plain node by node expansion they have always had.
            return;
        }

        if (node.parent != null && node.parent.isOnBoat() && node.getEdgeLength() <= 1)
        {
            return;
        }

        final BlockPos destination = destinationJob.getDestination();

        probeBoatEdge(node, 0, -1, destination);
        probeBoatEdge(node, 1, 0, destination);
        probeBoatEdge(node, 0, 1, destination);
        probeBoatEdge(node, -1, 0, destination);
        probeBoatEdge(node, 1, -1, destination);
        probeBoatEdge(node, 1, 1, destination);
        probeBoatEdge(node, -1, 1, destination);
        probeBoatEdge(node, -1, -1, destination);
    }

    /**
     * Whether this job may cross open water in macro edges, rather than a block at a time.
     * <p>
     * True for every job that is travelling somewhere, which is what having a destination is normally taken to mean.
     * Override to false when water is the job's subject rather than something in its way: an edge is a claim that
     * sixty blocks of water can be skipped over without looking at them, and that claim is only safe for a job whose
     * answer lies on the far side. {@link PathJobEscapeWater} is the one that has to say no today -- see the reason
     * there.
     * <p>
     * A predicate rather than a list of exempt classes kept here, because the knowledge belongs to the job: a new
     * water-themed job is written by someone reading its own file, not this one, and a list up here is a thing they
     * would never think to look at.
     *
     * @return true if macro edges are allowed.
     */
    protected boolean allowsBoatMacroEdges()
    {
        return true;
    }

    /**
     * Walk a straight line of boatable surface away from a node and, if it got anywhere, put one node at its far end.
     * <p>
     * The probe never leaves the height it started at. A water node stands in the air above the water rather than in
     * it, so a run that keeps the same Y is exactly a run along one water surface, and anything that would change the
     * height -- a bank, a waterfall, a river dropping a step -- simply fails the surface test and ends the edge. So
     * does an unloaded chunk or the edge of the job's box, both of which read as air.
     * <p>
     * A diagonal advances one block in X and then one in Z, and both of those blocks have to hold a boat. That order
     * is not cosmetic: {@link #finalizePath} replays it to turn the edge back into path points, and if the two
     * disagreed the citizen would be sent along a line nobody priced.
     *
     * @param node        the node the edge starts from.
     * @param dX          the x step, -1, 0 or 1.
     * @param dZ          the z step, -1, 0 or 1.
     * @param destination where the job is headed, used to stop an edge before it overshoots.
     */
    private void probeBoatEdge(final MNode node, final int dX, final int dZ, final BlockPos destination)
    {
        final int srcY = node.y;
        final boolean diagonal = dX != 0 && dZ != 0;
        final int subCells = diagonal ? 2 : 1;
        final int maxSteps = MAX_EDGE_LENGTH / subCells;

        int endX = node.x;
        int endZ = node.z;
        int length = 0;
        boolean reachedDestination = false;

        for (int step = 0; step < maxSteps && !reachedDestination; step++)
        {
            int cellX = endX;
            int cellZ = endZ;
            int accepted = 0;

            for (int cell = 0; cell < subCells; cell++)
            {
                if (diagonal)
                {
                    if (cell == 0)
                    {
                        cellX += dX;
                    }
                    else
                    {
                        cellZ += dZ;
                    }
                }
                else
                {
                    cellX += dX;
                    cellZ += dZ;
                }

                // The hostile test ends an edge exactly as unboatable water does. An edge is permission to skip
                // sixty four blocks without pricing them, which would sail a citizen straight through enemy water
                // for nothing; stopping at the border hands the crossing back to the block-by-block expansion,
                // where every hostile column is charged. Costs nothing when there is no territory (see
                // isHostileGround).
                if (!isBoatableSurface(cellX, srcY, cellZ) || isHostileGround(cellX, cellZ))
                {
                    break;
                }

                accepted++;
                endX = cellX;
                endZ = cellZ;

                if (isNextToDestination(cellX, srcY, cellZ, destination))
                {
                    // Stop on top of the goal rather than sailing past it. An edge that steps over the destination
                    // leaves the search with no node near enough for isAtDestination to accept, and it would go on
                    // hunting for one out in open water.
                    reachedDestination = true;
                    break;
                }
            }

            if (accepted < subCells && !reachedDestination)
            {
                if (accepted == 1)
                {
                    // Half of a diagonal step is not a step: the X block held a boat but the Z block did not. Give
                    // the X block back, the edge ends where the last whole step left it.
                    endX -= dX;
                }
                break;
            }

            length += accepted;
        }

        if (length < 2)
        {
            // One block is what the cardinal expansion already does, and it does it with the full ground and cost
            // handling that a probe deliberately skips.
            return;
        }

        final MNode existing = nodes.get(MNode.computeNodeKey(endX, srcY, endZ));
        if (existing != null && existing.isCornerNode())
        {
            // Corner nodes are dropped from the finished path, so an edge must never end on one: the crossing would
            // lose its far end. Back off by a block and settle for the shorter edge.
            if (!diagonal)
            {
                endX -= dX;
                endZ -= dZ;
            }
            else if (length % 2 == 0)
            {
                // Even block of a diagonal, so the last thing it did was advance Z.
                endZ -= dZ;
            }
            else
            {
                endX -= dX;
            }
            length--;

            if (length < 2)
            {
                return;
            }
        }

        addBoatEdgeNode(node, endX, srcY, endZ, length);
    }

    /**
     * Whether a block is close enough to the destination that an edge should stop on it instead of passing it.
     *
     * @param x           probe cell x.
     * @param y           probe cell y, always the height the edge runs at.
     * @param z           probe cell z.
     * @param destination the job's destination.
     * @return true if the edge should end here.
     */
    private static boolean isNextToDestination(final int x, final int y, final int z, final BlockPos destination)
    {
        return Math.abs(x - destination.getX()) <= 1
                 && Math.abs(z - destination.getZ()) <= 1
                 && Math.abs(y - destination.getY()) <= 2;
    }

    /**
     * Add or re-cost the node at the far end of a finished water edge.
     *
     * @param node   the node the edge started from, always a boatable surface node.
     * @param endX   x of the far end.
     * @param endY   y of the far end, which is the y the edge ran at.
     * @param endZ   z of the far end.
     * @param length how many blocks of water the edge covers.
     */
    private void addBoatEdgeNode(final MNode node, final int endX, final int endY, final int endZ, final int length)
    {
        // The whole edge is water, so it extends the run by its full length in blocks.
        addMacroEdgeNode(node, endX, endY, endZ, length, node.getCost() + boatEdgeCost(node, length), false, node.getBoatRunLength() + length, null);
    }

    /**
     * Add or re-cost the node at the far end of a finished macro edge, in either medium.
     * <p>
     * The half the two media genuinely share: an edge is one node standing for a run of blocks, so whichever way it
     * was found it has to arrive here carrying its length, and everything downstream -- the cost of the link, the
     * blocks {@link #finalizePath} materialises back out of it, the run length {@link #closeBoatRun} measures -- reads
     * that length rather than assuming one node is one block. What differs between water and rails is entirely in how
     * the far end was arrived at and what the run cost, and that is settled before the call.
     *
     * @param node    the node the edge started from.
     * @param endX    x of the far end.
     * @param endY    y of the far end.
     * @param endZ    z of the far end.
     * @param length  how many blocks the edge covers.
     * @param cost    the total g value at the far end, i.e. the parent's cost plus the whole edge.
     * @param rails   true if the edge rides a track, false if it crosses water.
     * @param boatRun the water run length to record at the far end; 0 for a rail edge.
     * @param cells   the blocks the edge runs through, or null when they follow from the endpoints.
     */
    private void addMacroEdgeNode(
      final MNode node,
      final int endX,
      final int endY,
      final int endZ,
      final int length,
      final double cost,
      final boolean rails,
      final int boatRun,
      @Nullable final long[] cells)
    {
        final double heuristic = computeHeuristic(endX, endY, endZ) * heuristicMod;
        // Deliberately not folded into maxCost. That number exists to buy more search budget when a single step
        // turns out to be unexpectedly expensive; an edge is expensive only because it is long, and letting it raise
        // the budget would hand these jobs a bigger allowance than they had before macro edges, which is backwards.

        final MNode existing = nodes.get(MNode.computeNodeKey(endX, endY, endZ));
        if (existing == null)
        {
            final MNode endNode = createNode(node, endX, endY, endZ, heuristic, cost);
            endNode.setOnRails(rails);
            endNode.setOnBoat(!rails);
            if (!rails)
            {
                endNode.setSwimming();
            }
            endNode.setCornerNode(false);
            endNode.setBoatRunLength(boatRun);
            endNode.setEdgeLength(length);
            endNode.setEdgeCells(cells);
            nodesToVisit.offer(endNode);
        }
        else
        {
            updateNode(node, existing, heuristic, cost, length, boatRun, cells);
        }
    }

    /**
     * Ride the track away from this node, if it is a node worth riding from.
     * <p>
     * Rails are the other terrain where expanding a block at a time buys nothing, and they buy even less than water
     * does. {@code onRailCost} is a tenth, so A* will follow a line of track in preference to almost anything else --
     * which is the intended behaviour and it works, but it means a two kilometre line is two thousand expanded nodes
     * out of an eight thousand node budget, spent rediscovering that the track is still track. It is worse than the
     * water case in one respect: on open water the search at least learns the shape of the shore while it grinds,
     * whereas on a track there was never a choice to make. A rail block declares in its own block state which two
     * blocks the track continues into, so following it is a walk rather than a search.
     * <p>
     * That one dimension is why this looks nothing like {@link #exploreBoatEdges} even though the two do the same job.
     * Water needs eight rays fanned out and every cell of each validated, because a plane has no preferred direction;
     * a track has exactly two ways off it, and the block state names them. There is nothing to probe and nothing to
     * guess. What the track does instead is curve, climb and pass switches, none of which water does, so the walk has
     * to record where it actually went -- see {@link MNode#getEdgeCells()}.
     * <p>
     * The same few nodes get to walk as get to fan on water: the start, the node where the path first steps onto
     * track, and the far end of an edge already laid. A node in the middle of a run only exists because a cardinal
     * step happened to make it, and an edge from it would duplicate one the run's source already covers.
     *
     * @param node the node being expanded.
     */
    private void exploreRailEdges(final MNode node)
    {
        if (node.isVisited() || node.isLadder() || node.isCornerNode())
        {
            return;
        }

        if (!pathingOptions.canUseRails() || !node.isOnRails())
        {
            return;
        }

        if (!(this instanceof final IDestinationPathJob destinationJob) || !allowsRailMacroEdges())
        {
            // Same reason the boat probe wants a destination: an edge is permission to skip over sixty blocks
            // without looking at them, and a job that is searching rather than travelling may well be looking for
            // something in among those blocks.
            return;
        }

        if (node.parent != null && node.parent.isOnRails() && node.getEdgeLength() <= 1)
        {
            return;
        }

        final RailShape shape = railShapeAt(node.x, node.y, node.z);
        if (shape == null)
        {
            // isOnRails is read off the block below for a corner node, and corner nodes are excluded above, so this
            // is only reachable if the node was flagged from a block that has since been read as something else --
            // an unloaded chunk boundary, in practice.
            return;
        }

        final BlockPos destination = destinationJob.getDestination();
        walkRailEdge(node, shape, 0, destination);
        walkRailEdge(node, shape, 1, destination);
    }

    /**
     * Whether this job may ride a track in macro edges, rather than a block at a time.
     * <p>
     * The rail twin of {@link #allowsBoatMacroEdges()}, kept as its own predicate rather than merged with it. They
     * answer the same question about different terrain and today the same single job says no to both, which is an
     * argument for merging them; the argument against is that the reason a job declines is specific to the medium.
     * A job that is about water is not thereby about rails, and the day one of them wants to decline only one, a
     * merged predicate would force it to give up both to say so.
     *
     * @return true if macro edges are allowed.
     */
    protected boolean allowsRailMacroEdges()
    {
        return true;
    }

    /**
     * Walk the track one way out of a node and lay nodes along what the walk covered.
     * <p>
     * Two nodes, not one, and the second is the whole reason rails are not simply water with fewer directions. A water
     * edge only has to stop where the water does, because the citizen can leave the water anywhere along it and the
     * cost of doing so is the same everywhere. A track is not like that: the citizen is in a cart, the interesting
     * decision is <em>where to get out</em>, and if the edge runs a straight sixty four blocks past the one point
     * where the destination is fifty blocks off to the side, the search never gets offered that point at all. So the
     * walk also remembers where it came closest to the goal and lays a node there. That costs one extra node per walk
     * and it is what stops a long line from carrying citizens past their own front door.
     * <p>
     * Where the walk stops is the other half of it, and every stop is a place the search is being handed a decision:
     * <ul>
     *   <li>the track ends, or the next rail is not mutually connected to this one -- there is nowhere to ride to;</li>
     *   <li>a switch: a rail adjacent to the track that the track's own shape does not connect to. The cart cannot
     *       take it, so if the search wants that branch it has to get out and walk onto it, and it needs a node here
     *       to do that from;</li>
     *   <li>the destination is alongside, the same stop {@link #probeBoatEdge} makes and for the same reason;</li>
     *   <li>a powered activator rail, which is the one rail block that would make the edge a lie -- see
     *       {@link #ejectsRider}.</li>
     * </ul>
     *
     * @param node        the node the edge starts from, always a rail node.
     * @param shape       that node's rail shape.
     * @param exit        which of the shape's two ways off to take, 0 or 1.
     * @param destination where the job is headed.
     */
    private void walkRailEdge(final MNode node, final RailShape shape, final int exit, final BlockPos destination)
    {
        final long[] cells = new long[MAX_EDGE_LENGTH];
        int length = 0;
        int bestIndex = -1;
        double bestHeuristic = computeHeuristic(node.x, node.y, node.z);

        int curX = node.x;
        int curY = node.y;
        int curZ = node.z;
        RailShape curShape = shape;
        Direction direction = railExitDirection(curShape, exit);

        while (length < MAX_EDGE_LENGTH)
        {
            final int nextX = curX + direction.getStepX();
            final int nextZ = curZ + direction.getStepZ();
            // A slope's uphill connection is one block higher, and the rail that answers it may sit a block either
            // side of where the shape pointed -- that is how vanilla joins a flat rail to the slope below it, and
            // resolveRailY is that same three block search.
            final int nextY = resolveRailY(nextX, curY + (direction == railAscentDirection(curShape) ? 1 : 0), nextZ);
            if (nextY == Integer.MIN_VALUE)
            {
                break;
            }

            final BlockState nextState = cachedBlockLookup.getBlockState(nextX, nextY, nextZ);
            final RailShape nextShape = railShape(nextState);
            final Direction back = direction.getOpposite();
            if (nextShape == null || (railExitDirection(nextShape, 0) != back && railExitDirection(nextShape, 1) != back))
            {
                // Adjacent but not joined: two lines running alongside each other, or a rail whose shape was turned
                // by something built next to it. A cart cannot cross from one to the other.
                break;
            }

            if (ejectsRider(nextState))
            {
                break;
            }

            if (isHostileGround(nextX, nextZ))
            {
                // Same reason as the boat probe: a rail edge would carry the citizen over enemy ground unpriced.
                // The walk stops at the border and the ordinary expansion, which does price it, takes over.
                break;
            }

            if (PathfindingUtils.blocksMotion(cachedBlockLookup.getBlockState(nextX, nextY + 1, nextZ)))
            {
                // An edge must never claim ground the ordinary expansion would have refused, and this is the one
                // thing the walk would otherwise miss: a rail is passable by definition, so following the track alone
                // says nothing about whether a citizen fits over it. Somebody has built across the line -- a floor
                // laid over an old tunnel, a wall dropped on the track -- and getGroundHeight would turn back at that
                // block. So does the walk. The test is only the block directly above, which is what makes a two high
                // tunnel still ride: that is exactly the clearance checkHeadBlock settles for.
                break;
            }

            cells[length] = BlockPos.asLong(nextX, nextY, nextZ);
            length++;

            final double heuristic = computeHeuristic(nextX, nextY, nextZ);
            if (heuristic < bestHeuristic)
            {
                bestHeuristic = heuristic;
                bestIndex = length - 1;
            }

            curX = nextX;
            curY = nextY;
            curZ = nextZ;
            curShape = nextShape;

            if (isNextToDestination(nextX, nextY, nextZ, destination) || isRailSwitch(curX, curY, curZ, curShape))
            {
                break;
            }

            direction = railExitDirection(curShape, 0) == back ? railExitDirection(curShape, 1) : railExitDirection(curShape, 0);
            if (direction == back)
            {
                // Both ways off point back where we came from. Only a shape whose two exits are the same direction
                // could do this, which no vanilla shape is, but the walk must terminate on the data rather than on
                // the assumption.
                break;
            }
        }

        if (length >= 2)
        {
            addRailEdgeNode(node, cells, length);
        }

        if (bestIndex >= 1 && bestIndex < length - 1)
        {
            addRailEdgeNode(node, cells, bestIndex + 1);
        }
    }

    /**
     * Add the node at the end of a walked stretch of track, shortening it if the block it lands on cannot hold one.
     *
     * @param node   the node the edge starts from.
     * @param cells  the walked blocks, from the node outwards.
     * @param length how many of them this edge covers.
     */
    private void addRailEdgeNode(final MNode node, final long[] cells, int length)
    {
        while (length >= 2)
        {
            final BlockPos end = BlockPos.of(cells[length - 1]);
            final MNode existing = nodes.get(MNode.computeNodeKey(end.getX(), end.getY(), end.getZ()));
            if (existing == null || !existing.isCornerNode())
            {
                addMacroEdgeNode(node,
                  end.getX(),
                  end.getY(),
                  end.getZ(),
                  length,
                  node.getCost() + railEdgeCost(node, cells, length),
                  true,
                  0,
                  Arrays.copyOf(cells, length));
                return;
            }

            // Corner nodes are dropped from the finished path, so an edge that ended on one would lose its far end.
            // Back off a block and settle for the shorter ride, exactly as the water probe does.
            length--;
        }
    }

    /**
     * What one macro edge along a track costs.
     * <p>
     * The same prices the block by block costing would have charged for the same run, summed: the rail discount on
     * every block, and the climb or drop on every block of every slope. Only the terms that a rail block can actually
     * incur are here, which is why it is a short list -- rails have no collision box and no facing, so the shape and
     * direction penalties in {@link #computeCost} are all zero along a track. The turn penalty is left out with them,
     * and that one is a genuine simplification rather than an identity: it is zero for every entity that can use
     * rails today, because the only thing that sets it is the mounted cavalry navigator, which does not.
     * <p>
     * Nothing here charges for getting on or off. Boarding is a cardinal step onto the first rail block, which the
     * ordinary costing prices, and {@code railsExitCost} is charged by the cardinal step that leaves the track --
     * an edge never leaves it.
     * <p>
     * One randomness draw covers the edge rather than one per block, matching the single draw an ordinary step gets.
     *
     * @param node   the node the edge starts from.
     * @param cells  the walked blocks.
     * @param length how many of them this edge covers.
     * @return the g increment for the edge.
     */
    private double railEdgeCost(final MNode node, final long[] cells, final int length)
    {
        double base = length;
        if (pathingOptions.randomnessFactor > 0.0d)
        {
            base += length * ThreadLocalRandom.current().nextDouble() * pathingOptions.randomnessFactor;
        }

        double cost = base * pathingOptions.onRailCost;

        int previousY = node.y;
        for (int i = 0; i < length; i++)
        {
            final int y = BlockPos.getY(cells[i]);
            if (y > previousY)
            {
                cost += pathingOptions.jumpCost;
            }
            else if (y < previousY)
            {
                cost += pathingOptions.dropCost;
            }
            previousY = y;
        }

        return cost;
    }

    /**
     * Whether riding onto this rail would throw the citizen out of the cart.
     * <p>
     * A powered activator rail calls {@code Minecart#activateMinecart}, which ejects every passenger on the spot
     * ({@code Minecart.java:49} in 26.2, reached from both minecart movement behaviours). A macro edge is a claim that
     * the whole run is ridden in one go, and over one of these it is simply false: the citizen would be left standing
     * on the track halfway along an edge nobody can follow. So the walk stops before it, and the search has to price
     * the crossing a block at a time -- which is what it did before macro edges and still gets right.
     * <p>
     * The other two special rails are left alone deliberately. A detector rail changes nothing for us: it emits a
     * signal when a cart passes and does not touch the cart. An <em>unpowered</em> powered rail is a brake -- it
     * halves the cart's velocity each tick and stops it below 0.03 -- but the navigator pushes the cart on every tick
     * of the ride, so the cart crawls rather than stopping, and stopping the edge there would cost a node on every
     * un-lit booster in a line without changing where the path goes.
     *
     * @param state the rail's block state.
     * @return true if the edge must not cross it.
     */
    private static boolean ejectsRider(final BlockState state)
    {
        return state.is(Blocks.ACTIVATOR_RAIL) && state.getValue(PoweredRailBlock.POWERED);
    }

    /**
     * Whether a rail has track beside it that its own shape does not join.
     * <p>
     * This is the only kind of junction a track can have. A rail's shape names exactly two ways off, so the graph the
     * cart rides has no node of degree three and a fork in the sense the search cares about -- somewhere to make a
     * choice -- never appears in it. Where a player has built a fork, one of the three rails is simply not connected:
     * the switch is thrown one way, and a cart takes the two that are joined. A citizen on foot has no such
     * restriction, so the branch is reachable, just not by riding into it, and the search needs a node standing on the
     * switch block to be able to step off onto it.
     *
     * @param x     the rail's x.
     * @param y     the rail's y.
     * @param z     the rail's z.
     * @param shape the rail's shape.
     * @return true if the walk should stop here.
     */
    private boolean isRailSwitch(final int x, final int y, final int z, final RailShape shape)
    {
        final Direction first = railExitDirection(shape, 0);
        final Direction second = railExitDirection(shape, 1);

        for (final Direction direction : HORIZONTAL_DIRECTIONS)
        {
            if (direction == first || direction == second)
            {
                continue;
            }

            if (resolveRailY(x + direction.getStepX(), y, z + direction.getStepZ()) != Integer.MIN_VALUE)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Find the height of the rail that answers for a position, allowing for the block either side of it.
     * <p>
     * PORT-NOTE(26.2): this is {@code RailState#getRail} and {@code RailState#hasRail} reduced to the one question
     * they are being asked here. {@code RailState} itself cannot be used off the server thread -- it takes a
     * {@link Level} rather than a reader and its {@code place} writes blocks -- but the part that matters is pure, and
     * it is the part that says a rail joins to a rail one block above or below the position its shape pointed at.
     * That is how a flat rail meets the slope beneath it, so a walk that only looked at the exact position would stop
     * dead at the top of every hill.
     *
     * @param x the x to look at.
     * @param y the height the shape pointed at.
     * @param z the z to look at.
     * @return the height of the rail found, or {@link Integer#MIN_VALUE} if there is none.
     */
    private int resolveRailY(final int x, final int y, final int z)
    {
        if (cachedBlockLookup.getBlockState(x, y, z).getBlock() instanceof BaseRailBlock)
        {
            return y;
        }
        if (cachedBlockLookup.getBlockState(x, y + 1, z).getBlock() instanceof BaseRailBlock)
        {
            return y + 1;
        }
        if (cachedBlockLookup.getBlockState(x, y - 1, z).getBlock() instanceof BaseRailBlock)
        {
            return y - 1;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * The rail shape at a position, or null if there is no rail there.
     *
     * @return the shape.
     */
    @Nullable
    private RailShape railShapeAt(final int x, final int y, final int z)
    {
        return railShape(cachedBlockLookup.getBlockState(x, y, z));
    }

    /**
     * The rail shape of a block state, or null if it is not a rail.
     * <p>
     * PORT-NOTE(26.2): every rail keeps its shape under its own property -- {@code RailBlock.SHAPE} takes all ten
     * values, the powered, detector and activator rails take only the six straight ones -- so the property has to come
     * from the block. NeoForge's {@code BaseRailBlock#getRailDirection} used to hide that; vanilla's
     * {@code getShapeProperty()} is public and does the same job.
     *
     * @param state the state to read.
     * @return the shape.
     */
    @Nullable
    private static RailShape railShape(final BlockState state)
    {
        return state.getBlock() instanceof final BaseRailBlock rail ? state.getValue(rail.getShapeProperty()) : null;
    }

    /**
     * Which way a rail's shape says the track continues, for one of its two ends.
     * <p>
     * PORT-NOTE(26.2): the horizontal half of {@code RailState#updateConnections}, in the same order, so end 0 and end
     * 1 here are the same two ends vanilla lists. The vertical half is {@link #railAscentDirection}.
     *
     * @param shape the rail's shape.
     * @param exit  which end, 0 or 1.
     * @return the direction the track continues in.
     */
    private static Direction railExitDirection(final RailShape shape, final int exit)
    {
        return switch (shape)
        {
            case NORTH_SOUTH -> exit == 0 ? Direction.NORTH : Direction.SOUTH;
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> exit == 0 ? Direction.WEST : Direction.EAST;
            case ASCENDING_NORTH, ASCENDING_SOUTH -> exit == 0 ? Direction.NORTH : Direction.SOUTH;
            case SOUTH_EAST -> exit == 0 ? Direction.EAST : Direction.SOUTH;
            case SOUTH_WEST -> exit == 0 ? Direction.WEST : Direction.SOUTH;
            case NORTH_WEST -> exit == 0 ? Direction.WEST : Direction.NORTH;
            case NORTH_EAST -> exit == 0 ? Direction.EAST : Direction.NORTH;
        };
    }

    /**
     * Which way out of a sloped rail climbs, or null for a flat one.
     *
     * @param shape the rail's shape.
     * @return the uphill direction.
     */
    @Nullable
    private static Direction railAscentDirection(final RailShape shape)
    {
        return switch (shape)
        {
            case ASCENDING_EAST -> Direction.EAST;
            case ASCENDING_WEST -> Direction.WEST;
            case ASCENDING_NORTH -> Direction.NORTH;
            case ASCENDING_SOUTH -> Direction.SOUTH;
            default -> null;
        };
    }

    /**
     * What one macro edge costs.
     * <p>
     * The same two prices the block by block costing uses, charged once for the whole run: a boat's discount if the
     * water crossed by the time the edge ends is long enough to be worth boarding for, and the swimming surcharge if
     * it is not. Boarding itself is not charged here -- it was already paid on the first water node of the crossing,
     * which is a cardinal step off the bank -- and stepping ashore is charged by the cardinal step that does it.
     * <p>
     * One randomness draw covers the edge rather than one per block, matching the single draw an ordinary step gets.
     *
     * @param node   the node the edge starts from.
     * @param length how many blocks of water the edge covers.
     * @return the g increment for the edge.
     */
    private double boatEdgeCost(final MNode node, final int length)
    {
        double base = length;
        if (pathingOptions.randomnessFactor > 0.0d)
        {
            base += length * ThreadLocalRandom.current().nextDouble() * pathingOptions.randomnessFactor;
        }

        if (node.getBoatRunLength() + length >= minWaterToBoat)
        {
            return base * pathingOptions.onBoatCost;
        }

        return base + length * pathingOptions.swimCost;
    }

    /**
     * "Walk" from the parent in the direction specified by the delta, determining the new x,y,z position for such a move and adding or updating a node, as appropriate.
     *
     * @param node Node being walked from.
     */
    protected final void exploreInDirection(final MNode node, int dX, int dY, int dZ)
    {
        int nextX = node.x + dX;
        int nextY = node.y + dY;
        int nextZ = node.z + dZ;
        
        final int newY;
        //  Can we traverse into this node?  Fix the y up, skip on already explored nodes
        if (node.isVisited())
        {
            if (node.isCornerNode() && node.parent != null && node.parent.y == node.y)
            {
                // Corner nodes can only connect sideways when going up
                return;
            }

            final Block target = cachedBlockLookup.getBlockState(nextX, nextY, nextZ).getBlock();
            if (target instanceof PanelBlock || target instanceof TrapDoorBlock)
            {
                newY = getGroundHeight(node, nextX, nextY, nextZ);
            }
            else
            {
                final Block origin = cachedBlockLookup.getBlockState(node.x, node.y, node.z).getBlock();
                if (origin instanceof PanelBlock || origin instanceof TrapDoorBlock)
                {
                    newY = getGroundHeight(node, nextX, nextY, nextZ);
                }
                else
                {
                    newY = nextY;
                }
            }
        }
        else
        {
            newY = getGroundHeight(node, nextX, nextY, nextZ);
        }

        if (newY < world.getMinY())
        {
            return;
        }

        boolean corner = false;
        if (nextY != newY)
        {
            if (node.isCornerNode() && (dX != 0 || dZ != 0))
            {
                return;
            }

            // if the new position is above the current node, we're taking the node directly above
            if (!node.isCornerNode() && newY - node.y > 0 && (node.parent == null || !BlockPosUtil.equals(node.parent.x,
              node.parent.y,
              node.parent.z,
              node.x,
              node.y + newY - nextY,
              node.z)))
            {
                nextX = node.x;
                nextY = node.y + (newY - nextY);
                nextZ = node.z;
                corner = true;
            }
            // If we're going down, take the air-corner before going to the lower node
            else if (!node.isCornerNode() && newY - node.y < 0 && (dX != 0 || dZ != 0) &&
                       (node.parent == null || (node.x != node.parent.x || node.y - 1 != node.parent.y
                                                  || node.z != node.parent.z)))
            {
                nextX = node.x + dX;
                nextY = node.y;
                nextZ = node.z + dZ;

                corner = true;
            }
            // Fix up normal y
            else
            {
                dX = 0;
                dY = newY - nextY;
                dZ = 0;

                nextY = newY;
            }
        }

        final int nodeKey = MNode.computeNodeKey(nextX, nextY, nextZ);
        MNode nextNode = nodes.get(nodeKey);

        if (nextNode != null && nextNode.isCornerNode())
        {
            if (node.isCornerNode())
            {
                // Do not allow connecting corner nodes
                return;
            }

            if (corner && nextNode.parent != null && (nextNode.parent.x != nextX || nextNode.parent.z != nextZ))
            {
                // Corner node from different direction already created, skip to using the actual next pos
                nextX = node.x + dX;
                nextY = newY;
                nextZ = node.z + dZ;
                nextNode = nodes.get(MNode.computeNodeKey(nextX, nextY, nextZ));
                corner = false;
            }
            else
            {
                corner = true;
            }
        }

        // Current node is already visited, only update nearby costs do not create new nodes
        if (node.isVisited())
        {
            if (nextNode == null || nextNode == node.parent)
            {
                return;
            }
        }

        final BlockState aboveState = cachedBlockLookup.getBlockState(nextX, nextY + 1, nextZ);
        final BlockState state = cachedBlockLookup.getBlockState(nextX, nextY, nextZ);
        final BlockState belowState = cachedBlockLookup.getBlockState(nextX, nextY - 1, nextZ);

        final boolean isSwimming = calculateSwimming(belowState, state, aboveState, nextNode);
        if (isSwimming && !pathingOptions.canSwim())
        {
            return;
        }

        final boolean swimStart = isSwimming && !node.isSwimming();
        final boolean onRoad = WorkerUtil.isPathBlock(belowState.getBlock());
        final boolean onRails = pathingOptions.canUseRails() && (corner ? belowState : state).getBlock() instanceof BaseRailBlock;
        final boolean railsExit = !onRails && node != null && node.isOnRails();
        final boolean ladder = PathfindingUtils.isLadder(state, pathingOptions, nextX, nextY, nextZ, cachedBlockLookup);
        final boolean isDiving = isSwimming && PathfindingUtils.isWater(world, null, aboveState, null);
        // A boat rides the surface, so it is only ever an option on a swimming node whose head is in the air.
        // Boating never opens up a node that swimming would not already reach: it only changes what the crossing
        // costs and how the navigator performs it, which keeps non-boaters -- and drowning risk -- exactly as before.
        final boolean onBoat = pathingOptions.canUseBoat() && isSwimming && !isDiving;
        // How much water we have already crossed to get here. The search cannot see past the nodes it has expanded,
        // so this is the run so far and never the whole crossing; computeCost only ever draws conclusions that stay
        // true however the run turns out.
        final int boatRun = boatRunLength(node, onBoat, corner);
        final boolean boatExit = !onBoat && node != null && node.getBoatRunLength() >= minWaterToBoat;

        double nextCost = 0;
        if (!corner)
        {
            MNode costFrom = node;

            dY = nextY - node.y;
            // Base cost calc on parent if we're expanding from a corner node
            if (node.isCornerNode() && node.parent != null)
            {
                dX = nextX - node.parent.x;
                dY = nextY - node.parent.y;
                dZ = nextZ - node.parent.z;
                costFrom = node.parent;
            }

            nextCost = computeCost(costFrom, dX, dY, dZ, isSwimming, onRoad, isDiving, onRails, railsExit, boatRun, boatExit, swimStart, ladder, state, belowState, nextX, nextY, nextZ);
            nextCost += computeTurnPenalty(costFrom, nextX, nextZ, pathingOptions.getTurnPenalty());
            nextCost = modifyCost(nextCost, costFrom, swimStart, isSwimming, nextX, nextY, nextZ, state, belowState);

            // Applied here and not inside modifyCost, which six path jobs override without calling super: a citizen's
            // detour round enemy ground must not depend on which job happens to be moving him. Water needs nothing of
            // its own -- swimming and walking are the same expansion and differ only in what computeCost charged --
            // so this covers both. The macro edges that *do* skip this site are stopped at the border instead, in
            // probeBoatEdge and walkRailEdge.
            if (isHostileGround(nextX, nextZ))
            {
                nextCost += HOSTILE_GROUND_COST;
            }

            // The most expensive single step seen so far, which buys search budget: the loop in search() gives up at
            // maxNodes + maxCost^2 * 2, on the reasoning that a job which has met a genuinely dear step is a job
            // working in awkward terrain and deserves more room.
            //
            // Moving the boat's boarding charge onto the first water block changed what this reads on a water job,
            // and the change is worth knowing about. That block used to cost about 25 -- swimCostEnter plus the base
            // -- which pinned maxCost at its MAX_COST ceiling and handed every boating job the full +1250 node
            // extension. It now costs about 9, so maxCost settles near 10 and the extension is about +200. That is
            // deliberate and it is the right direction: the whole point of macro edges is that a crossing costs tens
            // of nodes rather than thousands, so a budget sized for the old failure mode is no longer buying
            // anything except the licence to grind. It does mean a route that mixes a short crossing with genuinely
            // hard land is now held to a tighter budget than before, and if such a route ever starts stopping short
            // where it used to arrive, this is the line that did it -- not the probing.
            if (nextCost > maxCost)
            {
                maxCost = Math.min(MAX_COST, Math.ceil(nextCost));
            }
        }

        final double heuristic = computeHeuristic(nextX, nextY, nextZ) * heuristicMod;
        final double cost = node.getCost() + nextCost;

        if (nextNode == null)
        {
            nextNode = createNode(node, nextX, nextY, nextZ, heuristic, cost);
            nextNode.setOnRails(onRails);
            nextNode.setOnBoat(onBoat);
            nextNode.setBoatRunLength(boatRun);
            nextNode.setCornerNode(corner);
            if (isSwimming)
            {
                nextNode.setSwimming();
            }

            if (ladder)
            {
                nextNode.setLadder();
            }

            nodesToVisit.offer(nextNode);
        }
        else
        {
            updateNode(node, nextNode, heuristic, cost);
        }
    }

    @NotNull
    private MNode createNode(
        final MNode parent, final int x, final int y, final int z, final double heuristic, final double cost)
    {
        final MNode node;
        node = new MNode(parent, x, y, z, cost, heuristic);
        nodes.put(MNode.computeNodeKey(x, y, z), node);
        if (debugDrawEnabled)
        {
            debugNodesNotVisited.add(node);
        }

        totalNodesAdded++;
        node.setCounterAdded(totalNodesAdded);
        return node;
    }

    /**
     * Updates an already existing node with new heuristic/cost valvues
     *
     * @param node
     * @param heuristic
     * @param cost
     * @return
     */
    private void updateNode(@NotNull final MNode node, @NotNull final MNode nextNode, final double heuristic, final double cost)
    {
        updateNode(node, nextNode, heuristic, cost, 1, boatRunLength(node, nextNode.isOnBoat(), nextNode.isCornerNode()), null);
    }

    /**
     * Updates an already existing node with new heuristic/cost valvues
     *
     * @param node       the new parent.
     * @param nextNode   the node being re-parented.
     * @param heuristic
     * @param cost
     * @param edgeLength how many blocks the new link covers: 1 for an ordinary step, the run length for a macro edge.
     * @param boatRun    the water run length the new link leaves this node with.
     * @param cells      the blocks the new link runs through, or null when they follow from the endpoints.
     */
    private void updateNode(
      @NotNull final MNode node,
      @NotNull final MNode nextNode,
      final double heuristic,
      final double cost,
      final int edgeLength,
      final int boatRun,
      @Nullable final long[] cells)
    {
        //  This node already exists
        if (cost >= nextNode.getCost() || nextNode.getVisitedCount() > visitedLevel)
        {
            return;
        }

        nodesToVisit.remove(nextNode);
        nextNode.parent = node;
        // Re-parenting changes how much water precedes this node, and anything expanded from it afterwards reads
        // that number to decide whether it is far enough into a crossing to be costed as boating. Left stale, a node
        // first reached along a long stretch of water and later re-parented onto a short one keeps the long run, and
        // its children get the boat discount without anyone ever paying to board -- the one way the search can end up
        // planning a boat it will not get. markBoatLegs re-measures the finished chain and so the flags stay honest
        // either way, but the route choice would not.
        //
        // The edge length is the same kind of statement about the same link, and goes stale the same way: a node
        // first reached by a sixty block macro edge and then re-parented onto its neighbour by a cardinal step would
        // otherwise still claim sixty blocks, and finalizePath would draw sixty path points along a line the search
        // never checked. The recorded cells of a rail edge are that same claim written out block by block, and a
        // stale one is worse than a stale length: it names a route the node no longer has. All three describe the
        // link the node has right now, so all three are rewritten here.
        nextNode.setBoatRunLength(boatRun);
        nextNode.setEdgeLength(edgeLength);
        nextNode.setEdgeCells(cells);
        nextNode.setCost(cost);
        nextNode.setHeuristic(heuristic);

        nodesToVisit.offer(nextNode);
    }

    /**
     * Compute the heuristic cost ('h' value) of a given position x,y,z.
     * <p>
     * Returning a value of 0 performs a breadth-first search. Returning a value less than actual possible cost to goal guarantees shortest path, but at computational expense.
     * Returning a value exactly equal to the cost to the goal guarantees shortest path and least expense (but generally. only works when path is straight and unblocked). Returning
     * a value greater than the actual cost to goal produces good, but not perfect paths, and is fast. Returning a very high value (such that 'h' is very high relative to 'g') then
     * only 'h' (the heuristic) matters as the search will be a very fast greedy best-first-search, ignoring cost weighting and distance.
     *
     * @return the heuristic.
     */
    protected abstract double computeHeuristic(final int x, final int y, final int z);

    /**
     * Return true if the given node is a viable final destination, and the path should generate to here.
     *
     * @param n Node to test.
     * @return true if the node is a viable destination.
     */
    protected abstract boolean isAtDestination(MNode n);

    /**
     * Calculates the end node score to compare end nodes additionally to heuristic/cost
     *
     * @param n
     * @return end node score, lower is better
     */
    private double getEndNodeScoreWithExtraCost(MNode n)
    {
        tempWorldPos.set(n.x, n.y, n.z);
        return getEndNodeScore(n) + RecentTargetCache.getExtraCost(tempWorldPos);
    }

    /**
     * Calculates a score for potential points where the path may end given no destination
     * By default the heuristic for the closest node is used
     *
     * @param n Node to test.
     * @return score for the node, lower is better
     */
    protected double getEndNodeScore(MNode n)
    {
        return n.getHeuristic();
    }

    /**
     * Compute the cost (immediate 'g' value) of moving from the parent space to the new space.
     *
     * @param parent
     * @param isSwimming true is the current node would require the citizen to swim.
     * @param onPath     checks if the node is on a path.
     * @param onRails    checks if the node is a rail block.
     * @param railsExit  the exit of the rails.
     * @param boatRun    how many boat-eligible nodes in a row end at this one, 0 if it is not eligible.
     * @param boatExit   the exit of a boat crossing.
     * @param swimStart  if its the swim start.
     * @param state      the blockstate
     * @return cost to move from the parent to the new position.
     */
    protected double computeCost(
      final MNode parent, final int dX, final int dY, final int dZ,
      final boolean isSwimming,
      final boolean onPath,
      final boolean isDiving,
      final boolean onRails,
      final boolean railsExit,
      final int boatRun,
      final boolean boatExit,
      final boolean swimStart,
      final boolean ladder,
      final BlockState state, final BlockState below,
      final int x, final int y, final int z)
    {
        double cost = 1;

        // Only once the run is long enough to clear minimumWaterToBoat is this node going to be travelled in a boat.
        // Everything before that is water the citizen will swim, because a shorter run never becomes a boat leg, so
        // it has to be priced as swimming. The search can be sure of this much without seeing the rest of the
        // crossing: a run that already qualifies cannot stop qualifying by growing.
        final boolean boating = boatRun >= minWaterToBoat;

        // ThreadLocalRandom, not the mod-wide ColonyConstants.rand: this runs once per considered A* transition, and
        // java.util.Random#next is a compare-and-set on one shared AtomicLong, so every pathfinding thread contends
        // on it with every other caller in the mod. The draw is a tie-break on step cost, never a game outcome, so
        // an unshared stream is exactly as correct and costs nothing.
        if (pathingOptions.randomnessFactor > 0.0d)
        {
            cost += ThreadLocalRandom.current().nextDouble() * pathingOptions.randomnessFactor;
        }

        if (!isSwimming)
        {
            if (onPath)
            {
                cost *= pathingOptions.onPathCost;
            }
            if (onRails)
            {
                cost *= pathingOptions.onRailCost;
            }
        }
        else if (boating)
        {
            // Same idea as the on-rails discount, one branch further down because every water node is a swim node.
            cost *= pathingOptions.onBoatCost;
        }

        if (state.getBlock() == Blocks.CAVE_AIR)
        {
            cost += pathingOptions.caveAirCost;
        }

        if (!isDiving)
        {
            if (dY != 0 && !(ladder && parent.isLadder()) && !(Math.abs(dY) == 1 && below.is(BlockTags.STAIRS)))
            {
                if (dY > 0)
                {
                    cost += pathingOptions.jumpCost;
                }
                else if (pathingOptions.dropCost != 0)
                {
                    cost += pathingOptions.dropCost * Math.abs(dY * dY * dY);
                }
            }
        }

        if (dX != 0 || dZ != 0)
        {
            // Entering the next block from the wrong direction
            cost += getFacingCost(state, dX, dZ);
            cost += getFacingCost(below, dX, dZ);

            // Leaving the old block in the wrong direction
            cost += getFacingCost(cachedBlockLookup.getBlockState(parent.x, parent.y, parent.z), dX, dZ);
            cost += getFacingCost(cachedBlockLookup.getBlockState(parent.x, parent.y - 1, parent.z), dX, dZ);
        }

        if (state.hasProperty(BlockStateProperties.OPEN) && !(state.getBlock() instanceof PanelBlock))
        {
            cost += pathingOptions.traverseToggleAbleCost;
        }
        else if (!onPath && ShapeUtil.hasCollision(cachedBlockLookup, tempWorldPos.set(x, y, z), state))
        {
            cost += pathingOptions.walkInShapesCost;
        }

        if (below.getBlock() instanceof ShingleBlock || below.getBlock() instanceof ShingleSlabBlock)
        {
            cost += 3;
        }

        if (railsExit)
        {
            cost += pathingOptions.railsExitCost;
        }

        if (boatExit)
        {
            cost += pathingOptions.boatExitCost;
        }

        if (!isDiving && ladder && !parent.isLadder() && !(state.getBlock() instanceof LadderBlock))
        {
            cost += pathingOptions.nonLadderClimbableCost;
        }

        if (isSwimming)
        {
            if (swimStart)
            {
                // Boarding is paid here, on the very first water block of a crossing, and not later on at the block
                // where the run happens to grow past minimumWaterToBoat. The charge has to land on the block the
                // search reaches first, because that is the block whose price decides whether the water gets
                // explored at all: a citizen who owns a boat and is looking at a lake he will obviously sail across
                // should not first be quoted the price of wading into it. boatRun > 0 is exactly "this is a water
                // surface block and the entity can use a boat" -- swimmers, divers and sea floor walkers are
                // unaffected and still pay the full swimCostEnter.
                //
                // Owning a boat can only ever make entering water cheaper, never dearer, hence the min. Several jobs
                // tune swimCostEnter far below the boat's price precisely because water is what they are about --
                // PathJobFindWater sets it to 0 so the fisherman will actually walk into the lake he was sent to,
                // PathJobEscapeWater sets it to 1 so a drowning citizen is not charged for the water it is already
                // standing in. Substituting the boat's flat 8 there would quietly overrule a decision those jobs
                // made on purpose, and it would do it only for citizens who happen to have finished the research.
                cost += boatRun > 0
                          ? Math.min(pathingOptions.boatEnterCost, pathingOptions.swimCostEnter)
                          : pathingOptions.swimCostEnter;
            }
            else if (!boating)
            {
                cost += pathingOptions.swimCost;
            }

            if (isDiving)
            {
                cost += pathingOptions.divingCost;
            }
        }

        return cost;
    }

    /**
     * Adds the cost for going against the blocks existing facing
     *
     * @param state block to check
     * @param dX
     * @param dZ
     * @return added cost or 0
     */
    private double getFacingCost(final BlockState state, final int dX, final int dZ)
    {
        if (state.hasProperty(HorizontalDirectionalBlock.FACING))
        {
            final Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
            if (facing.getStepX() != 0 && dZ != 0 || facing.getStepZ() != 0 && dX != 0)
            {
                return pathingOptions.badDirectionCost;
            }
        }

        return 0;
    }

    /**
     * Whether one column of the world is inside a hostile territory.
     *
     * @param x the column's x.
     * @param z the column's z.
     * @return true if it is enemy ground this job should route around.
     */
    protected final boolean isHostileGround(final int x, final int z)
    {
        return hostileGround != null && hostileGround.owningTerritory(x, z) != HostileTerritoryMap.NO_TERRITORY;
    }

    /**
     * Which hostile territories this job has to care about, decided once when the job is created.
     * <p>
     * Only a citizen's job gets an answer. Raiders are attacking, wandering mobs are nobody's, and a territory has no
     * citizens of its own to be confused about — so restricting it to citizens is both the requirement ("my colony's
     * people must go round") and the cheapest place to stop.
     *
     * @param world  the world the job is pathing in.
     * @param entity the entity being moved, possibly null.
     * @return the dimension's territories, or null if there are none or the mover is not a citizen.
     */
    @Nullable
    private static HostileTerritoryMap hostileGroundFor(@Nullable final Level world, @Nullable final Mob entity)
    {
        if (world == null || !(entity instanceof AbstractEntityCitizen))
        {
            return null;
        }
        return HostileTerritory.in(world.dimension());
    }

    /**
     * Modifies costs if needed for a node
     *
     * @param cost
     * @param parent
     * @param swimstart
     * @param swimming
     * @param state
     * @return
     */
    protected double modifyCost(
      final double cost,
      final MNode parent,
      final boolean swimstart,
      final boolean swimming,
      final int x,
      final int y,
      final int z,
      final BlockState state, final BlockState below)
    {
        return cost;
    }

    /**
     * Generate the path to the target node.
     *
     * @param targetNode the node to path to.
     * @return the path.
     */
    @NotNull
    private Path finalizePath(final MNode targetNode)
    {
        //  Compute length of path, since we need to allocate an array.  This is cheaper/faster than building a List
        //  and converting it.  Yes, we have targetNode.steps, but I do not want to rely on that being accurate (I might
        //  fudge that value later on for cutoff purposes
        int pathLength = 1;
        int railsLength = 0;
        MNode node = targetNode;
        while (node.parent != null)
        {
            if (!node.isCornerNode())
            {
                // A macro edge over water is one node standing for a run of blocks, and every one of those blocks
                // becomes a path point of its own below. Ordinary steps carry a length of one and count as before.
                pathLength += node.getEdgeLength();
            }
            if (node.isOnRails())
            {
                // In blocks, not in nodes, for the same reason closeBoatRun counts blocks: one macro edge stands for
                // as much as MAX_EDGE_LENGTH of track, so a two hundred block ride counted in nodes reads as four and
                // falls under minimumRailsToPath -- and the ride that most wants a cart is exactly the one made of
                // edges. Corner nodes still count as the one they always counted as; they carry no edge and they sit
                // over the track, so calling them a block of it is as true as it was before.
                railsLength += node.isCornerNode() ? 1 : node.getEdgeLength();
            }
            node = node.parent;
        }

        markBoatLegs(targetNode);

        final boolean onRailsPath = railsLength >= MineColonies.getConfig().getServer().minimumRailsToPath.get();

        final Node[] points = new Node[pathLength];
        final PathPointExtended startPoint = new PathPointExtended(new BlockPos(node.x, node.y, node.z));
        // The start point is built out here rather than in the loop below, so it is the one point that would
        // otherwise carry no flags at all. It has to carry this one. A path is installed with its next node index at
        // zero, so the first thing followThePath does with a freshly computed path is read this point -- and its rule
        // for getting out of a vehicle is "the node being followed is not part of a boat leg". A citizen who is
        // repathed while already halfway across a lake would be thrown into the water on the spot, and then handed a
        // brand new boat a tick later once the index reached node one, which is always the entry when a crossing
        // starts at the path start.
        startPoint.setOnBoat(node.isBoatLeg());
        // And the same for the cart, which had the same hole and had it before macro edges: followThePath gets a
        // citizen out of any vehicle the moment the node being followed is neither a rail nor a boat node, and the
        // start point was the one point that was never flagged either way. A citizen repathed while riding was
        // therefore dropped on the track on the spot, and picked up a fresh cart a tick later once the index reached
        // node one -- which is always the entry when the ride starts at the path start.
        startPoint.setOnRails(onRailsPath && node.isOnRails());
        points[0] = startPoint;
        if (debugDrawEnabled)
        {
            addPathNodeToDebug(node);
        }


        MNode nextInPath = null;
        Node next = null;
        node = targetNode;
        while (node.parent != null)
        {
            if (debugDrawEnabled)
            {
                addPathNodeToDebug(node);
            }

            if (node.isCornerNode())
            {
                node = node.parent;
                continue;
            }

            //  A macro edge is put back on the ground here: one point per block it covers, so the navigator sees the
            //  same unbroken line of neighbouring points it has always seen and nothing downstream has to learn what
            //  an edge is. Ordinary steps have a length of one and take the single pass through the loop below,
            //  exactly as before.
            final int edgeLength = node.getEdgeLength();
            pathLength -= edgeLength;

            //  Farthest block first, so cameFrom keeps chaining each point onto the one before it.
            for (int block = edgeLength - 1; block >= 0; block--)
            {
                final BlockPos pos = edgeBlockPos(node, block, edgeLength);

                if (node.isSwimming())
                {
                    //  Not truly necessary but helps prevent them spinning in place at swimming nodes
                    pos.offset(BLOCKPOS_DOWN);
                }

                final PathPointExtended p = new PathPointExtended(pos);
                //  Neither vehicle's entry and exit are decided here: see the pass over the finished array below.
                p.setOnBoat(node.isBoatLeg());
                //  Every block of a rail edge is track, not just the one the node stands on. Flagging only the node's
                //  own block would hand the navigator a path where one point in sixty four is a rail, and
                //  followThePath's rule for leaving a vehicle -- the node being followed is not part of a leg --
                //  would throw the citizen out of the cart at the first block after the entry.
                p.setOnRails(onRailsPath && node.isOnRails());

                if (block == edgeLength - 1)
                {
                    if (node.isLadder() || (node.parent != null && node.parent.isLadder() && node.isCornerNode()))
                    {
                        p.setOnLadder(true);
                        // TODO: Check working, logic is a bit odd
                        if (nextInPath != null && nextInPath.y > pos.getY())
                        {
                            //  We only care about facing if going up
                            //In the case of BlockVines (Which does not have Direction) we have to check the metadata of the vines... bitwise...
                            PathfindingUtils.setLadderFacing(world, pos, p);
                        }
                    }
                }

                if (next != null)
                {
                    next.cameFrom = p;
                }
                next = p;
                points[pathLength + block] = p;
            }

            nextInPath = node;
            node = node.parent;
        }

        if (pathingOptions.canUseBoat())
        {
            markVehicleEntryAndExit(points, false);
        }
        if (onRailsPath)
        {
            markVehicleEntryAndExit(points, true);
        }

        if (points.length > 1)
        {
            result.costPerDist = targetNode.getCost() / BlockPosUtil.distManhattan(start, targetNode.x, targetNode.y, targetNode.z);
        }

        result.searchedNodes = totalNodesVisited;
        return new Path(Arrays.asList(points), new BlockPos(targetNode.x, targetNode.y, targetNode.z), reachesDestination);
    }

    /**
     * The position of one block of the link between a node and its parent, counted from the parent.
     * <p>
     * For an ordinary step there is only block 0 and it is the node itself. For a macro edge the blocks are replayed
     * in the order the probe walked them, and for a diagonal that order is X before Z -- the same order
     * {@link #probeBoatEdge} tested the surface in. Replaying it any other way would send the citizen through blocks
     * nobody checked and, on a shoreline, through blocks that are not water at all.
     * <p>
     * The height is the node's own throughout: a water edge never leaves the surface it started on. A rail edge does
     * nothing of the sort -- it curves and climbs -- so it does not get reconstructed at all. It carries the blocks it
     * actually ran through and they are simply read back, which is the whole reason {@link MNode#getEdgeCells()}
     * exists.
     *
     * @param node   the node the link ends at.
     * @param block  which block of the link, 0 being the one nearest the parent.
     * @param length how many blocks the link covers.
     * @return the position of that block.
     */
    private static BlockPos edgeBlockPos(final MNode node, final int block, final int length)
    {
        final long[] cells = node.getEdgeCells();
        if (cells != null && block < cells.length)
        {
            return BlockPos.of(cells[block]);
        }

        if (length <= 1 || node.parent == null)
        {
            return new BlockPos(node.x, node.y, node.z);
        }

        final MNode parent = node.parent;
        final int dX = Integer.signum(node.x - parent.x);
        final int dZ = Integer.signum(node.z - parent.z);
        final int steps = block + 1;

        if (dX == 0 || dZ == 0)
        {
            return new BlockPos(parent.x + dX * steps, node.y, parent.z + dZ * steps);
        }

        // X first: block 1 moves in X, block 2 moves in Z, block 3 moves in X again.
        return new BlockPos(parent.x + dX * ((steps + 1) / 2), node.y, parent.z + dZ * (steps / 2));
    }

    /**
     * Decide where the citizen gets into a vehicle and where it gets out, over the finished array of path points.
     * <p>
     * Done here rather than while the points are being built because a macro edge turns one node into a run of
     * points: "the point after this node" stops being "the next node in the chain", and the array is the only place
     * every neighbour is present and in order. The rules are the ones the per node versions applied -- get in on the
     * first point of a leg, get out on the first point after it.
     * <p>
     * One pass serves both vehicles because the rule genuinely is the same rule; the flags it reads and writes are the
     * only difference, and the navigator keeps them apart for its own reasons ({@code handleRails} spawns a cart at
     * the rails entry, {@code handleBoats} places a hull at the boat entry). The rails half used to be computed inside
     * the point-building loop and had exactly the bug that moved the boat half out here: it looked one node ahead in
     * the chain rather than one point ahead in the path.
     *
     * @param points the finished path points, whose onBoat and onRails flags are already set.
     * @param rails  true to mark the cart's entry and exit, false for the boat's.
     */
    private void markVehicleEntryAndExit(final Node[] points, final boolean rails)
    {
        for (int i = 1; i < points.length; i++)
        {
            final PathPointExtended point = (PathPointExtended) points[i];
            final PathPointExtended previous = (PathPointExtended) points[i - 1];

            final boolean on = rails ? point.isOnRails() : point.isOnBoat();
            final boolean previousOn = rails ? previous.isOnRails() : previous.isOnBoat();

            if (on)
            {
                if (!previousOn || i == 1)
                {
                    // Point one gets in even when point zero is already flagged. That is the citizen who was repathed
                    // halfway across the lake or halfway along the line: point zero is flagged so followThePath does
                    // not throw them out on the spot, and the entry has nowhere else to go.
                    if (rails)
                    {
                        point.setRailsEntry();
                    }
                    else
                    {
                        point.setBoatEntry();
                    }
                }
            }
            else if (previousOn)
            {
                if (rails)
                {
                    point.setRailsExit();
                }
                else
                {
                    point.setBoatExit();
                }
            }
        }
    }

    /**
     * Get the height of the ground at the given x,z coordinate, within 1 step of y.
     *
     * @param node parent node.
     * @return y height of first open, viable block above ground, or -1 if blocked or too far a drop.
     */
    protected int getGroundHeight(final MNode node, final int x, final int y, final int z)
    {
        if (!pathingOptions.canWalkUnderWater() && PathfindingUtils.isLiquid(cachedBlockLookup.getBlockState(x, y + 1, z)))
        {
            return Integer.MIN_VALUE;
        }
        //  Check (y+1) first, as it's always needed, either for the upper body (level),
        //  lower body (headroom drop) or lower body (jump up)
        if (checkHeadBlock(node, x, y, z))
        {
            return handleTargetNotPassable(node, x, y + 1, z, cachedBlockLookup.getBlockState(x, y + 1, z));
        }

        //  Now check the block we want to move to
        final BlockState target = cachedBlockLookup.getBlockState(x, y, z);
        if (!isPassable(target, x, y, z, node, false))
        {
            return handleTargetNotPassable(node, x, y, z, target);
        }

        //  Do we have something to stand on in the target space?
        final BlockState below = cachedBlockLookup.getBlockState(x, y - 1, z);
        final SurfaceType walkability = SurfaceType.getSurfaceType(world, below, tempWorldPos.set(x, y - 1, z), pathingOptions);
        if (walkability == SurfaceType.WALKABLE)
        {
            //  Level path
            return y;
        }
        else if (walkability == SurfaceType.NOT_PASSABLE)
        {
            return Integer.MIN_VALUE;
        }

        return handleNotStanding(node, x, y, z, below);
    }

    /**
     * Checks for headblock space
     *
     * @param parent
     * @param x
     * @param y
     * @param z
     * @return
     */
    private boolean checkHeadBlock(@Nullable final MNode parent, final int x, final int y, final int z)
    {
        if (!canLeaveBlock(x, y + 1, z, parent, true))
        {
            return true;
        }

        if (!isPassable(x, y + 1, z, true, parent))
        {
            // TODO: Checking +1 and -1 seems odd? probably one intended to be current instead
            VoxelShape bb1 = cachedBlockLookup.getBlockState(x, y - 1, z).getCollisionShape(world, tempWorldPos.set(x, y - 1, z));
            if (PathfindingUtils.isLiquid(cachedBlockLookup.getBlockState(x, y - 1, z)))
            {
                bb1 = Shapes.block();
            }

            final VoxelShape bb2 = cachedBlockLookup.getBlockState(x, y + 1, z).getCollisionShape(world, tempWorldPos.set(x, y + 1, z));
            if ((y + 1 + ShapeUtil.getStartY(bb2, 1)) - (y - 1 + ShapeUtil.getEndY(bb1, 0)) < 2)
            {
                return true;
            }
            if (parent != null)
            {
                final VoxelShape bb3 =
                  cachedBlockLookup.getBlockState(parent.x, parent.y - 1, parent.z).getCollisionShape(world, tempWorldPos.set(parent.x, parent.y - 1, parent.z));
                if ((y + 1 + ShapeUtil.getStartY(bb2, 1)) - (parent.y - 1 + ShapeUtil.getEndY(bb3, 0)) < 1.75)
                {
                    return true;
                }
            }
        }

        if (parent != null)
        {
            final BlockState belowState = cachedBlockLookup.getBlockState(x, y - 1, z);
            final VoxelShape bb2 = cachedBlockLookup.getBlockState(x, y + 1, z).getCollisionShape(world, tempWorldPos.set(x, y + 1, z));
            final VoxelShape bb = cachedBlockLookup.getBlockState(x, y, z).getCollisionShape(world, tempWorldPos.set(x, y, z));
            if ((y + 1 + ShapeUtil.getStartY(bb2, 1)) - (y + ShapeUtil.getEndY(bb, 0)) >= 2)
            {
                return false;
            }

            return parent.isSwimming() && PathfindingUtils.isLiquid(belowState) && !isPassable(x, y, z, false, parent);
        }
        return false;
    }

    /**
     * Is the space passable.
     *
     * @param block  the block we are checking.
     * @param parent the parent node.
     * @param head   the head position.
     * @return true if the block does not block movement.
     */
    protected boolean isPassable(@NotNull final BlockState block, final int x, final int y, final int z, final MNode parent, final boolean head)
    {
        if (!canLeaveBlock(x, y, z, parent, head))
        {
            return false;
        }

        if (!block.isAir())
        {
            final VoxelShape shape = block.getCollisionShape(world, tempWorldPos.set(x, y, z));
            if (!pathingOptions.canPassDanger() && ShapeUtil.max(shape, Direction.Axis.Y) < 0.5 && PathfindingUtils.isDangerous(cachedBlockLookup.getBlockState(x, y - 1, z)))
            {
                return false;
            }
            if (PathfindingUtils.blocksMotion(block) && !(ShapeUtil.isEmpty(shape) || ShapeUtil.max(shape, Direction.Axis.Y) <= 0.1))
            {
                if (block.getBlock() instanceof TrapDoorBlock || block.getBlock() instanceof PanelBlock)
                {
                    int parentY = parent == null ? start.getY() : parent.y;
                    if (head)
                    {
                        parentY++;
                    }

                    final int dY = y - parentY;

                    final Direction direction = BlockPosUtil.getXZFacing(parent == null ? start.getX() : parent.x, parent == null ? start.getZ() : parent.z, x, z);
                    final Direction facing = block.getValue(TrapDoorBlock.FACING);

                    if (block.getBlock() instanceof PanelBlock && !block.getValue(PanelBlock.OPEN))
                    {
                        if (dY == 0)
                        {
                            return (head && block.getValue(PanelBlock.HALF) == Half.TOP);
                        }

                        if (head && dY == 1 && block.getValue(PanelBlock.HALF) == Half.TOP)
                        {
                            return true;
                        }

                        if (!head && dY == -1 && block.getValue(PanelBlock.HALF) == Half.BOTTOM)
                        {
                            return true;
                        }

                        return false;
                    }

                    // We can enter a space of a trapdoor if it's facing the same direction
                    if (direction == facing.getOpposite())
                    {
                        return true;
                    }

                    // We cannot enter a space of a trapdoor if its facing the opposite direction, unless we are above it
                    if (direction == facing)
                    {
                        return dY < 0;
                    }

                    return true;
                }
                else
                {
                    return (pathingOptions.canEnterDoors() && block.getBlock() instanceof DoorBlock)
                             || (pathingOptions.canEnterGates() && block.getBlock() instanceof FenceGateBlock)
                             || (pathingOptions.canEnterGates() && block.getBlock() instanceof AbstractBlockGate)
                             || block.getBlock() instanceof AbstractBlockMinecoloniesConstructionTape
                             || block.getBlock() instanceof PressurePlateBlock
                             || block.getBlock() instanceof BlockDecorationController
                             || block.getBlock() instanceof SignBlock
                             || block.getBlock() instanceof AbstractBannerBlock
                             || !block.getBlock().properties().hasCollision;
                }
            }
            else if (!pathingOptions.canPassDanger() && PathfindingUtils.isDangerous(block))
            {
                return false;
            }
            else
            {
                if (PathfindingUtils.isLadder(block, pathingOptions))
                {
                    return true;
                }

                if (ShapeUtil.isEmpty(shape) || ShapeUtil.max(shape, Direction.Axis.Y) <= 0.1
                    && !PathfindingUtils.isLiquid((block)) && (block.getBlock() != Blocks.SNOW || block.getValue(SnowLayerBlock.LAYERS) == 1))
                {
                    // PORT(26.2): this test was inverted by the swap away from NeoForge, which turned every
                    // collisionless block into a wall.
                    //
                    // Upstream called NeoForge's BlockState#getBlockPathType, whose default implementation is
                    // "isBurning(level, pos) ? DAMAGE_FIRE : null" — null being "no opinion", the answer for every
                    // block in the game that is not on fire. So "pathType == null || malus < 0" meant: a block with
                    // no real collision box is passable unless it is burning.
                    //
                    // Vanilla's WalkNodeEvaluator#getPathTypeFromState is not that method. It never returns null —
                    // its final fall-through is PathType.OPEN, whose malus is 0 — so the null branch became dead
                    // code and an ordinary low block failed "malus < 0" and fell through to "not passable". Grass,
                    // ferns, flowers, torches, rails, redstone, buttons, levers, sugar cane, lily pads and
                    // single-layer snow all became walls, and getGroundHeight turns a wall into Integer.MIN_VALUE,
                    // i.e. no path at all. The test now says what it did before: passable unless burning.
                    return VanillaPathTypes.of(cachedBlockLookup, tempWorldPos.set(x, y, z)) != PathType.FIRE;
                }
                return false;
            }
        }

        return true;
    }

    /**
     * Checks passability
     *
     * @param x
     * @param y
     * @param z
     * @param head
     * @param parent
     * @return
     */
    protected boolean isPassable(final int x, final int y, final int z, final boolean head, final MNode parent)
    {
        final BlockState state = cachedBlockLookup.getBlockState(x, y, z);
        final VoxelShape shape = state.getCollisionShape(world, tempWorldPos.set(x, y, z));
        if (ShapeUtil.isEmpty(shape) || ShapeUtil.max(shape, Direction.Axis.Y) <= 0.1)
        {
            return !head
                     || !(state.getBlock() instanceof WoolCarpetBlock || state.getBlock() instanceof FloatingCarpetBlock || state.getBlock() instanceof LilyPadBlock)
                     || PathfindingUtils.isLadder(state, pathingOptions);
        }
        return isPassable(state, x, y, z, parent, head);
    }

    /**
     * Handles not passable positions
     *
     * @param parent
     * @param x
     * @param y
     * @param z
     * @param target
     * @return
     */
    private int handleTargetNotPassable(@Nullable final MNode parent, final int x, final int y, final int z, @NotNull final BlockState target)
    {
        final boolean canJump = parent != null && !parent.isLadder() && !parent.isSwimming();
        //  Need to try jumping up one, if we can
        if (!canJump || SurfaceType.getSurfaceType(world, target, tempWorldPos.set(x, y, z), getPathingOptions()) != SurfaceType.WALKABLE)
        {
            return Integer.MIN_VALUE;
        }

        //  Check for headroom in the target space
        if (!isPassable(x, y + 2, z, true, parent))
        {
            final VoxelShape bb1 = cachedBlockLookup.getBlockState(x, y, z).getCollisionShape(world, tempWorldPos.set(x, y, z));
            final VoxelShape bb2 = cachedBlockLookup.getBlockState(x, y + 2, z).getCollisionShape(world, tempWorldPos.set(x, y + 2, z));
            if ((y + 2 + ShapeUtil.getStartY(bb2, 1)) - (y + ShapeUtil.getEndY(bb1, 0)) < 2)
            {
                return Integer.MIN_VALUE;
            }
        }

        if (!canLeaveBlock(x, y + 2, z, parent, true))
        {
            return Integer.MIN_VALUE;
        }

        //  Check for jump room from the origin space
        if (!isPassable(parent.x, parent.y + 2, parent.z, true, parent))
        {
            final VoxelShape bb1 = cachedBlockLookup.getBlockState(x, y, z).getCollisionShape(world, tempWorldPos.set(x, y, z));
            final VoxelShape bb2 = cachedBlockLookup.getBlockState(parent.x, parent.y + 2, parent.z).getCollisionShape(world, tempWorldPos.set(parent.x, parent.y + 2, parent.z));
            if ((parent.y + 2 + ShapeUtil.getStartY(bb2, 1)) - (y + ShapeUtil.getEndY(bb1, 0)) < 2)
            {
                return Integer.MIN_VALUE;
            }
        }

        final BlockState parentBelow = cachedBlockLookup.getBlockState(parent.x, parent.y - 1, parent.z);
        final VoxelShape parentBB = parentBelow.getCollisionShape(world, tempWorldPos.set(parent.x, parent.y - 1, parent.z));

        double parentY = ShapeUtil.max(parentBB, Direction.Axis.Y);
        double parentMaxY = parentY + parent.y - 1;
        final double targetMaxY = ShapeUtil.max(target.getCollisionShape(world, tempWorldPos.set(x, y, z)), Direction.Axis.Y) + y;
        if (targetMaxY - parentMaxY < MAX_JUMP_HEIGHT)
        {
            return y + 1;
        }
        if (target.is(BlockTags.STAIRS)
              && parentY - HALF_A_BLOCK < MAX_JUMP_HEIGHT
              && target.getValue(StairBlock.HALF) == Half.BOTTOM
              && BlockPosUtil.getXZFacing(parent.x, parent.z, x, z) == target.getValue(StairBlock.FACING))
        {
            return y + 1;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Handles not standing goto positions
     *
     * @param parent
     * @param x
     * @param y
     * @param z
     * @param below
     * @return
     */
    private int handleNotStanding(@Nullable final MNode parent, final int x, final int y, final int z, @NotNull final BlockState below)
    {
        final boolean isSwimming = parent != null && parent.isSwimming();

        if (!pathingOptions.canWalkUnderWater() && PathfindingUtils.isLiquid(below))
        {
            return handleInLiquid(x, y, z, below, isSwimming);
        }

        if (PathfindingUtils.isLadder(below, pathingOptions, x, y - 1, z, cachedBlockLookup))
        {
            return y;
        }

        return checkDrop(parent, x, y, z, isSwimming);
    }

    /**
     * Checks dropping down
     *
     * @param parent
     * @param x
     * @param y
     * @param z
     * @param isSwimming
     * @return
     */
    private int checkDrop(@Nullable final MNode parent, final int x, final int y, final int z, final boolean isSwimming)
    {
        final boolean canDrop = parent != null && !parent.isLadder();
        //  Nothing to stand on
        if (!canDrop || ((parent.x != x || parent.z != z) && isPassable(parent.x, parent.y - 1, parent.z, false, parent)
                           &&
                           SurfaceType.getSurfaceType(world,
                             cachedBlockLookup.getBlockState(parent.x, parent.y - 1, parent.z),
                             tempWorldPos.set(parent.x, parent.y - 1, parent.z),
                             getPathingOptions())
                             == SurfaceType.DROPABLE))
        {
            return Integer.MIN_VALUE;
        }

        for (int i = 2; i <= (pathingOptions.canDrop ? 10 : 2); i++)
        {
            final BlockState below = cachedBlockLookup.getBlockState(x, y - i, z);
            if (!canLeaveBlock(x, y - 1, z, x, y, z, false))
            {
                return Integer.MIN_VALUE;
            }
            if (SurfaceType.getSurfaceType(world, below, tempWorldPos.set(x, y - i, z), getPathingOptions()) == SurfaceType.WALKABLE)
            {
                //  Level path
                return y - i + 1;
            }
            else if (!below.isAir())
            {
                return Integer.MIN_VALUE;
            }
        }

        return Integer.MIN_VALUE;
    }

    /**
     * Handles goto position in liquid
     *
     * @param x
     * @param y
     * @param z
     * @param below
     * @param isSwimming
     * @return
     */
    private int handleInLiquid(final int x, final int y, final int z, @NotNull final BlockState below, final boolean isSwimming)
    {
        if (isSwimming)
        {
            //  Already swimming in something, or allowed to swim and this is water
            return y;
        }

        if (pathingOptions.canSwim() && PathfindingUtils.isWater(world, tempWorldPos.set(x, y - 1, z)))
        {
            //  This is water, and we are allowed to swim
            return y;
        }

        //  Not allowed to swim or this isn't water, and we're on dry land
        return Integer.MIN_VALUE;
    }

    /**
     * Check if we can leave the block at this pos.
     *
     * @param parent the parent pos (to check if we can leave)
     * @return true if so.
     */
    private boolean canLeaveBlock(final int x, final int y, final int z, final MNode parent, final boolean head)
    {
        int parentX = parent == null ? start.getX() : parent.x;
        int parentY = parent == null ? start.getY() : parent.y;
        int parentZ = parent == null ? start.getZ() : parent.z;
        return canLeaveBlock(x, y, z, parentX, head ? parentY + 1 : parentY, parentZ, head);
    }

    /**
     * Check if we can leave the block at this pos.
     *
     * @return true if so.
     */
    private boolean canLeaveBlock(final int x, final int y, final int z, final int parentX, final int parentY, final int parentZ, final boolean head)
    {
        final int dY = y - parentY;

        final BlockState parentBlockState = cachedBlockLookup.getBlockState(parentX, parentY, parentZ);
        final Block parentBlock = parentBlockState.getBlock();
        if (parentBlock instanceof TrapDoorBlock || parentBlock instanceof PanelBlock)
        {
            if (!parentBlockState.getValue(TrapDoorBlock.OPEN))
            {
                if (dY != 0)
                {
                    if (parentBlock instanceof TrapDoorBlock)
                    {
                        return true;
                    }
                    return (head && parentBlockState.getValue(PanelBlock.HALF) == Half.TOP && dY < 0)
                        || (!head && parentBlockState.getValue(PanelBlock.HALF) == Half.BOTTOM && dY > 0);
                }
                return true;
            }
            if (x - parentX != 0 || z - parentZ != 0)
            {
                // Check if we can leave the current block, there might be a trapdoor or panel blocking us.
                final Direction direction = BlockPosUtil.getXZFacing(parentX, parentZ, x, z);
                final Direction facing = parentBlockState.getValue(TrapDoorBlock.FACING);
                if (direction == facing.getOpposite())
                {
                    return false;
                }
            }
        }
        else if (parentBlock instanceof FloatingCarpetBlock || parentBlock instanceof LilyPadBlock)
        {
            if (dY < 0)
            {
                return head;
            }
            else if (dY > 0)
            {
                return !head;
            }
        }
        return true;
    }

    private boolean calculateSwimming(final BlockState below, final BlockState state, final BlockState above, @Nullable final MNode node)
    {
        if (node != null)
        {
            return node.isSwimming();
        }

        return PathfindingUtils.isWater(cachedBlockLookup, null, below, null)
                 || PathfindingUtils.isWater(cachedBlockLookup, null, state, null)
                 || PathfindingUtils.isWater(cachedBlockLookup, null, above, null);
    }

    /**
     * How many boat-eligible nodes in a row end at a node reached from this parent.
     * <p>
     * Corner nodes contribute nothing. They are an artefact of how a diagonal or a step up or down is expanded, and
     * {@code finalizePath} drops them from the finished path, so {@link #markBoatLegs} never counts them either. If
     * the search counted them the two would disagree by exactly the number of corners, which for an ordinary river is
     * two -- one stepping off the near bank, one stepping up onto the far one, both of them over water with air above
     * and so both boat-eligible. A four block river would then reach a banked run of six, be costed as a boat
     * crossing that {@code markBoatLegs} goes on to refuse, and end up dearer than the swim it actually is. The
     * research would make narrow water worse than not having it.
     *
     * @param parent the node being expanded from.
     * @param onBoat whether the new node is boat-eligible.
     * @param corner whether the new node is a corner node.
     * @return the run length to record on the new node.
     */
    private static int boatRunLength(@Nullable final MNode parent, final boolean onBoat, final boolean corner)
    {
        if (!onBoat)
        {
            return 0;
        }

        final int parentRun = parent != null && parent.isOnBoat() ? parent.getBoatRunLength() : 0;
        return corner ? parentRun : parentRun + 1;
    }

    /**
     * Decide which stretches of water on the finished path are actually worth a boat, and mark their nodes.
     * <p>
     * The gate is on a <em>run</em> of consecutive water nodes, not on how much water the path touches in total:
     * five separate one-block puddles are five puddles, not a crossing, and turning each into its own spawn, board,
     * dismount and discard would be worse than simply wading through them. Once a run does qualify the whole of it is
     * marked, back to the bank, so the citizen boards at the water's edge rather than after wading in.
     * <p>
     * Measured here, on the chain that was actually chosen, rather than read off the run length banked during the
     * search: {@code updateNode} re-parents a node whenever it finds a cheaper way to it and does not refresh what the
     * node carries, so a banked run length can describe a chain that lost.
     *
     * @param targetNode the end of the path.
     */
    private void markBoatLegs(final MNode targetNode)
    {
        if (!pathingOptions.canUseBoat())
        {
            // Every raider, animal and unresearched citizen in the colony finalizes paths too, and without this they
            // would each walk the parent chain a third time and allocate a list to discover no water. Nothing is
            // flagged onBoat unless canUseBoat held during the search, so there is never anything here to find.
            return;
        }

        final List<MNode> run = new ArrayList<>();

        for (MNode node = targetNode; node != null; node = node.parent)
        {
            if (node.isCornerNode())
            {
                // Corner nodes are dropped from the finished path, so they neither extend nor break a run.
                continue;
            }

            if (node.isOnBoat())
            {
                run.add(node);
            }
            else
            {
                closeBoatRun(run);
            }
        }

        // The chain ended while still in water.
        closeBoatRun(run);
    }

    /**
     * Mark a finished run of water nodes as a boat leg if it is long enough, and clear it either way.
     * <p>
     * Long is measured in blocks of water, not in nodes. A macro edge is one node standing for as much as
     * {@link #MAX_EDGE_LENGTH} blocks, so counting nodes would decide that an ocean crossing made of two edges was
     * shorter than a five block puddle crossed a block at a time -- and the crossing that most needs a boat is
     * exactly the one made of edges. Every node carries the length of the link that reaches it, which is 1 for the
     * ordinary steps this used to count, so the two readings agree wherever nothing has changed.
     *
     * @param run the nodes of the run, which is emptied.
     */
    private void closeBoatRun(final List<MNode> run)
    {
        int blocks = 0;
        for (final MNode node : run)
        {
            blocks += node.getEdgeLength();
        }

        if (blocks >= minWaterToBoat)
        {
            for (final MNode node : run)
            {
                node.setBoatLeg(true);
            }
        }
        run.clear();
    }

    /**
     * Whether a boat could float at this position: water at the position itself or directly below it, and no water
     * above it.
     * <p>
     * This is the exact complement of the diving case, on purpose. A boat cannot submerge, so a citizen who dives or
     * who walks along the sea floor ({@code walkUnderWater}) produces no boat nodes at all and keeps behaving the way
     * it always has. Only the water <em>surface</em> is boatable.
     *
     * @return true if a boat belongs here.
     */
    protected boolean isBoatableSurface(final int x, final int y, final int z)
    {
        if (PathfindingUtils.isWater(cachedBlockLookup, null, cachedBlockLookup.getBlockState(x, y + 1, z), null))
        {
            return false;
        }

        return PathfindingUtils.isWater(cachedBlockLookup, null, cachedBlockLookup.getBlockState(x, y, z), null)
                 || PathfindingUtils.isWater(cachedBlockLookup, null, cachedBlockLookup.getBlockState(x, y - 1, z), null);
    }

    /**
     * Calculate the turn penalty for the given move.
     *
     * @param from the node we are moving from.
     * @param toX the x coordinate of the node we are moving to.
     * @param toZ the z coordinate of the node we are moving to.
     * @param turnPenalty the base turn penalty to use.
     *
     * This function calculates the turn penalty for a move from one node to another. The penalty is based on the angle of the turn, 
     * with smaller angles having smaller penalties. The base turn penalty is also used to scale the penalty.
     *
     * @return the calculated turn penalty.
     */
    private double computeTurnPenalty(@NotNull final MNode from, final int toX, final int toZ, float turnPenalty)
    {
        if (entity == null || turnPenalty == 0.0f)
        {
            return 0.0;
        }

        final MNode prev = from.parent;
        if (prev == null)
        {
            return 0.0;
        }

        // Determine directional vectors of movement.
        int dxPrev = Integer.signum(from.x - prev.x);
        int dzPrev = Integer.signum(from.z - prev.z);

        int dxNow = Integer.signum(toX - from.x);
        int dzNow = Integer.signum(toZ - from.z);

        // ignore 'no horizontal move' cases
        if ((dxPrev == 0 && dzPrev == 0) || (dxNow == 0 && dzNow == 0))
        {
            return 0.0;
        }

        // Straight
        if (dxPrev == dxNow && dzPrev == dzNow)
        {
            return 0.0;
        }

        final int dot = dxPrev * dxNow + dzPrev * dzNow; // -2..2

        final double P = turnPenalty;

        // slight (diag<->cardinal)
        if (dot == 1) return P * 0.5;

        // 90 degrees
        if (dot == 0) return P;        

        // harsh / U-turn
        return P * 2.0;                
    }

    /**
     * Initializes debug tracking
     */
    public void initDebug()
    {
        if (!debugDrawEnabled)
        {
            debugDrawEnabled = true;
            debugNodesVisited = new HashSet<>();
            debugNodesVisitedLater = new HashSet<>();
            debugNodesNotVisited = new HashSet<>();
            debugNodesPath = new HashSet<>();
            debugNodesOrgPath = new HashSet<>();
            debugNodesExtra = new HashSet<>();
        }
    }

    /**
     * Handles debugging for a given node
     *
     * @param node
     */
    protected void handleDebugOptions(final MNode node)
    {
        if (debugDrawEnabled)
        {
            addNodeToDebug(node);
        }
    }

    /**
     * Add extra nodes to debug view
     *
     * @param node
     */
    private void handleDebugExtraNode(final MNode node)
    {
        if (debugDrawEnabled)
        {
            debugNodesNotVisited.remove(node);
            debugNodesExtra.add(node);
        }
    }

    /**
     * Add original path nodes to debug view
     *
     * @param bestNode
     */
    private void handleDebugPathReach(final MNode bestNode)
    {
        if (debugDrawEnabled)
        {
            debugNodesOrgPath.add(bestNode);

            MNode currentNode = bestNode;
            while (currentNode.parent != null)
            {
                currentNode = currentNode.parent;
                debugNodesOrgPath.add(currentNode);
            }
        }
    }

    /**
     * Adds a node to the debug view
     *
     * @param currentNode
     */
    private void addNodeToDebug(final MNode currentNode)
    {
        if (debugNodesOrgPath.contains(currentNode))
        {
            return;
        }

        debugNodesNotVisited.remove(currentNode);
        debugNodesVisited.add(currentNode);

        if (reachesDestination)
        {
            debugNodesVisited.remove(currentNode);
            debugNodesVisitedLater.add(currentNode);
        }
    }

    /**
     * Adds a path node to the debug view
     *
     * @param node
     */
    private void addPathNodeToDebug(final MNode node)
    {
        debugNodesVisited.remove(node);
        debugNodesPath.add(node);
    }

    /**
     * Sync the path to the client.
     */
    public void syncDebug(final List<ServerPlayer> debugWatchers)
    {
        if (debugDrawEnabled)
        {
            final SyncPathMessage message = new SyncPathMessage(debugNodesVisited,
              debugNodesNotVisited,
              debugNodesPath,
              debugNodesVisitedLater,
              debugNodesOrgPath,
              debugNodesExtra);

            for (final ServerPlayer player : debugWatchers)
            {
                message.sendToPlayer(player);
            }
        }
    }

    @Override
    public PathResult getResult()
    {
        return result;
    }

    /**
     * Sets the pathing options
     *
     * @param pathingOptions the pathing options to set.
     */
    public void setPathingOptions(final PathingOptions pathingOptions)
    {
        this.pathingOptions.importFrom(pathingOptions);
    }

    @Override
    public PathingOptions getPathingOptions()
    {
        return pathingOptions;
    }

    @Override
    public Mob getEntity()
    {
        return entity;
    }

    @Override
    public Level getActualWorld()
    {
        return actualWorld;
    }

    @Override
    public BlockPos getStart()
    {
        return start;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " start:" + start.toShortString() + " entity:" + entity + " maxNodes:" + maxNodes + " totalNodesVisited:" + totalNodesVisited
            + " bestNodeCost:"
            + bestNode.getCost() + " heuristicCostEstimate:" + startNode.getHeuristic() + " h-rebalances:" + (
            visitedLevel - 1) + " reaches:"
            + reachesDestination + (this instanceof IDestinationPathJob ? " dest:" + ((IDestinationPathJob) this).getDestination().toShortString() : "");
    }
}
