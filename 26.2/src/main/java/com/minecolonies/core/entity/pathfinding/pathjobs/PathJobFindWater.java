package com.minecolonies.core.entity.pathfinding.pathjobs;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.util.BlockUtils;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.Pond;
import com.minecolonies.api.util.Pond.PondState;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.core.entity.pathfinding.MNode;
import com.minecolonies.core.entity.pathfinding.PathfindingUtils;
import com.minecolonies.core.entity.pathfinding.PathingOptions;
import com.minecolonies.core.entity.pathfinding.SurfaceType;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import com.minecolonies.core.entity.pathfinding.pathresults.WaterPathResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Find and return a path to the nearest water. Created: March 25, 2016
 */
public class PathJobFindWater extends AbstractPathJob implements ISearchPathJob
{
    private static final int MAX_RANGE = 100;
    private final        BlockPos                        hutLocation;
    @NotNull
    private final        List<Tuple<BlockPos, BlockPos>> ponds;

    /**
     * AbstractPathJob constructor.
     *
     * @param world  the world within which to path.
     * @param start  the start position from which to path from.
     * @param home   the position of the worker hut.
     * @param range  maximum path range.
     * @param ponds  already visited fishing places.
     * @param entity the entity.
     */
    public PathJobFindWater(
        final Level world,
        @NotNull final BlockPos start,
        final BlockPos home,
        final int range,
        @NotNull final List<Tuple<BlockPos, BlockPos>> ponds,
        final Mob entity)
    {
        super(world, start, range, new WaterPathResult(), entity);
        this.ponds = new ArrayList<>(ponds);
        hutLocation = home;
    }

    @NotNull
    @Override
    public WaterPathResult getResult()
    {
        return (WaterPathResult) super.getResult();
    }

    @Override
    protected double computeHeuristic(final int x, final int y, final int z)
    {
        return BlockPosUtil.distManhattan(hutLocation, x, y, z);
    }

    @Override
    protected boolean isAtDestination(@NotNull final MNode n)
    {
        if (BlockPosUtil.distSqr(hutLocation, n.x, n.y, n.z) > MAX_RANGE * MAX_RANGE)
        {
            return false;
        }

        final MutableBlockPos problemPos = debugDrawEnabled ? BlockPosUtil.SAFE_ZERO.mutable() : null;
        PondState pondState = Pond.checkPond(world, tempWorldPos.set(n.x, n.y - 1, n.z), problemPos);

        if (n.isSwimming() && pondState != PondState.INVALID)
        {
            for (Tuple<BlockPos, BlockPos> existingPond : ponds)
            {
                if (BlockPosUtil.distManhattan(existingPond.getA(), n.x, n.y, n.z) < Pond.WATER_POOL_WIDTH_REQUIREMENT + 2)
                {
                    return false;
                }
            }

            final PathJobFindFishingPos job = new PathJobFindFishingPos(getActualWorld(), world, new BlockPos(n.x, n.y, n.z), hutLocation, 10);
            job.setPathingOptions(getPathingOptions());
            final Path path = job.search();
            if (path != null && path.canReach())
            {
                getResult().pond = new BlockPos(n.x, n.y, n.z);
                getResult().pondState = pondState;
                getResult().parent = path.getTarget();

                return true;
            }

            if (canFishFromABoat(n, pondState))
            {
                // No bank: fish from a boat, sitting on the pond itself. The shore search above is left exactly as it
                // was and still gets first refusal, so a pond with any walkable bank within ten blocks is still fished
                // from the bank -- this only picks up the water a fisherman cannot stand beside at all, which today he
                // rejects outright and then complains he can find no suitable water.
                //
                // The pond node is the spot rather than some node found by a second search: it has already been
                // proven a pond, it is already the water nearest the hut of everything this search has reached, and
                // asking for a *different* water node would cost another synchronous A* per candidate pond in a job
                // that is already the most node-hungry one in the pool.
                getResult().pond = new BlockPos(n.x, n.y, n.z);
                getResult().pondState = pondState;

                // The spot is the surface, one above the water block, and not the water block itself. The two are the
                // same place to a boat -- a hull dropped on either floats at the same height -- but they are not the
                // same place to walk to, and the fisherman has to walk there: a citizen sent to a submerged block does
                // not swim to it, he wanders. Measured on the stand: a fisherman given a water block as his spot spent
                // ninety seconds crossing the colony in both directions and never arrived, where the same fisherman
                // sent to the surface block above the same water swam straight out and sat down.
                getResult().parent = surfaceOf(n);

                return true;
            }
        }

        // node is not pond -> debug
        if (problemPos != null && !problemPos.equals(BlockPosUtil.SAFE_ZERO))
        {
            debugNodesExtra.add(new MNode(n, problemPos.getX(), problemPos.getY(), problemPos.getZ(), -1, -1));
        }

        return false;
    }

    /**
     * Whether this pond node may be fished from a boat parked on it, there being no bank to stand on.
     * <p>
     * Three conditions, and each of them is load bearing:
     * <ul>
     *     <li>the citizen has the BOATS research. {@code canUseBoat} is where that research lands
     *     ({@code EntityCitizen#canPathOnBoat}), it is copied into this job's options by the caller, and reading it
     *     here rather than asking the colony keeps the research lookup off the pathfinding thread.</li>
     *     <li>the node is a <em>boatable surface</em>, not merely a swimming one. A node deep inside the water is
     *     swimming too, and a boat placed in a water block comes up UNDER_WATER, which refuses passengers and ejects
     *     the ones it has -- see the note in {@code MinecoloniesAdvancedPathNavigate#handleBoats}.</li>
     *     <li>the pond is {@link PondState#VALID}, so every block sampled around it is a source block. A moored hull
     *     in a current drifts off the pond it was moored on; refusing flowing water here is the first of the two
     *     defences against that, the anchor in {@link com.minecolonies.api.entity.other.MinecoloniesBoat} the
     *     second.</li>
     * </ul>
     * Boat macro edges stay banned for this job (see {@code AbstractPathJob#exploreBoatEdges}) and nothing here
     * changes that: an edge is a promise that open water may be skipped unlooked-at, and this search is a search in
     * that water. The boat is what the fisherman sits in once the water has been found node by node, not a way of
     * finding it.
     *
     * @param n         the candidate pond node.
     * @param pondState what {@link Pond#checkPond} made of it.
     * @return true if the fisherman may park a boat here.
     */
    private boolean canFishFromABoat(final MNode n, final PondState pondState)
    {
        return getPathingOptions().canUseBoat()
                 && pondState == PondState.VALID
                 && isBoatableSurface(n.x, n.y, n.z);
    }

    /**
     * The place a boat would float above this node: the node itself when it is already the open space over the water,
     * and the block above it when the node is the water. {@code isBoatableSurface} accepts both, so both reach here.
     *
     * @param n a node already known to be a boatable surface.
     * @return where the fisherman sits.
     */
    private BlockPos surfaceOf(final MNode n)
    {
        final BlockPos pos = new BlockPos(n.x, n.y, n.z);
        return PathfindingUtils.isWater(cachedBlockLookup, null, cachedBlockLookup.getBlockState(n.x, n.y, n.z), null)
                 ? pos.above()
                 : pos;
    }

    @Override
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
        if (BlockPosUtil.distSqr(hutLocation, x, y, z) > MAX_RANGE * MAX_RANGE)
        {
            return cost * 10;
        }

        return cost;
    }

    @Override
    public void setPathingOptions(final PathingOptions pathingOptions)
    {
        super.setPathingOptions(pathingOptions);
        getPathingOptions().swimCostEnter = 0;
        getPathingOptions().swimCost = 0;
    }

    @Override
    public double getEndNodeScore(final MNode n)
    {
        return BlockPosUtil.distManhattan(hutLocation, n.x, n.y, n.z);
    }

    /**
     * Simple reverse lookup to find a fitting shore for a pond location
     */
    private class PathJobFindFishingPos extends AbstractPathJob implements ISearchPathJob
    {
        private final BlockPos direction;
        private final int      distance;

        public PathJobFindFishingPos(
            final Level actualWorld,
            final LevelReader world,
            final @NotNull BlockPos start,
            final @NotNull BlockPos direction,
            final int distance)
        {
            super(actualWorld, world, start, distance + 100, new PathResult(), null);
            this.direction = direction;
            this.distance = distance;
        }

        @Override
        protected void handleDebugOptions(final MNode node)
        {
            PathJobFindWater.this.handleDebugOptions(node);
        }

        @Override
        protected double computeHeuristic(final int x, final int y, final int z)
        {
            return BlockPosUtil.distManhattan(direction, x, y, z);
        }

        @Override
        protected boolean isAtDestination(final MNode n)
        {
            return !n.isSwimming()
                && BlockPosUtil.distManhattan(start, n.x, n.y, n.z) < distance
                && SurfaceType.getSurfaceType(world, cachedBlockLookup.getBlockState(n.x, n.y - 1, n.z), tempWorldPos.set(n.x, n.y - 1, n.z), getPathingOptions())
                == SurfaceType.WALKABLE && BlockUtils.isAnySolid(cachedBlockLookup.getBlockState(n.x, n.y - 1, n.z))
                && canSeeTargetFromPos(n);
        }

        /**
         * Checks visibility
         *
         * @param n
         * @return
         */
        private boolean canSeeTargetFromPos(final MNode n)
        {
            return !PathfindingUtils.hasAnyCollisionAlong(start.getX(), start.getY(), start.getZ(), n.x, n.y + 1, n.z, cachedBlockLookup);
        }

        @Override
        public double getEndNodeScore(final MNode n)
        {
            return BlockPosUtil.distManhattan(start, n.x, n.y, n.z);
        }
    }
}

