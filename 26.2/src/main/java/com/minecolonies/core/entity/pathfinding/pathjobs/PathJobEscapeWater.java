package com.minecolonies.core.entity.pathfinding.pathjobs;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.entity.pathfinding.MNode;
import com.minecolonies.core.entity.pathfinding.PathingOptions;
import com.minecolonies.core.entity.pathfinding.SurfaceType;
import com.minecolonies.core.entity.pathfinding.pathresults.PathResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Job that handles moving away from something.
 */
public class PathJobEscapeWater extends AbstractPathJob implements IDestinationPathJob
{
    /**
     * Position to run to, in order to avoid something.
     */
    @NotNull
    protected final BlockPos avoid;

    /**
     * The blockposition we're trying to move away to
     */
    private BlockPos preferredDirection;

    /**
     * Prepares the PathJob for the path finding system.
     *
     * @param world  world the entity is in.
     * @param start  starting location.
     * @param range  max range to search.
     * @param entity the entity.
     */
    public PathJobEscapeWater(
      final Level world,
      @NotNull final BlockPos start,
      final int range,
      final Mob entity)
    {
        super(world, start, 500, new PathResult<PathJobEscapeWater>(), entity);

        this.avoid = start;
        preferredDirection = entity.blockPosition().offset(entity.blockPosition().subtract(avoid).multiply(range));
        if (entity instanceof AbstractEntityCitizen)
        {
            final IColony colony = ((AbstractEntityCitizen) entity).getCitizenColonyHandler().getColonyOrRegister();
            if (colony != null)
            {
                preferredDirection = colony.getCenter();
            }
        }
    }

    /**
     * For MoveAwayFromLocation we want our heuristic to weight.
     *
     * @return heuristic as a double - Manhatten Distance with tie-breaker.
     */
    @Override
    protected double computeHeuristic(final int x, final int y, final int z)
    {
        return BlockPosUtil.dist(preferredDirection, x, y, z) * 2 / (y / 10.0);
    }

    /**
     * Checks if the destination has been reached. Meaning that the avoid distance has been reached.
     *
     * @param n Node to test.
     * @return true if so.
     */
    @Override
    protected boolean isAtDestination(@NotNull final MNode n)
    {
        return cachedBlockLookup.getBlockState(n.x, n.y, n.z).isAir() && cachedBlockLookup.getBlockState(n.x, n.y + 1, n.z).isAir()
                 && SurfaceType.getSurfaceType(world, cachedBlockLookup.getBlockState(n.x, n.y - 1, n.z), tempWorldPos.set(n.x, n.y - 1, n.z), getPathingOptions())
                      == SurfaceType.WALKABLE;
    }

    /**
     * Never, for this job: the whole point of it is to get out of the water, and a macro edge is a cheap way to
     * travel further into it.
     * <p>
     * The edges are priced by how much water they cross, so for a citizen with the boat research the nearest bank
     * would keep losing to a sixty block hop toward the colony centre -- which is where this job aims, and which is
     * usually across the water rather than out of it. That inverts the job. A drowning citizen wants the closest dry
     * block, not the best-connected one.
     *
     * @return false, always.
     */
    @Override
    protected boolean allowsBoatMacroEdges()
    {
        return false;
    }

    /**
     * Never, for this job, and for the same reason as the boat.
     * <p>
     * A waterlogged rail is an ordinary sight in a colony -- a line laid across a shallow, or one a river has since
     * risen over -- so a drowning citizen standing on track is not a contrived case. From such a block the walk would
     * offer sixty four blocks of ride at a tenth of the price, aimed at the colony centre this job uses as its
     * preferred direction, and the nearest bank two blocks away would lose to it. The ride is not even wrong in
     * itself; it is simply not what a citizen who is running out of air should be doing. Everything this job is for
     * happens within a few blocks, so it wants the plain expansion that looks at all of them.
     * <p>
     * This is the second and last job to decline, and it declines both media separately rather than through one
     * switch -- see {@link AbstractPathJob#allowsRailMacroEdges()} for why the two questions are kept apart.
     *
     * @return false, always.
     */
    @Override
    protected boolean allowsRailMacroEdges()
    {
        return false;
    }

    @Override
    public void setPathingOptions(final PathingOptions pathingOptions)
    {
        super.setPathingOptions(pathingOptions);
        getPathingOptions().setWalkUnderWater(true);
        getPathingOptions().swimCost = 1;
        getPathingOptions().swimCostEnter = 1;
        getPathingOptions().nonLadderClimbableCost = 1;
    }

    @Override
    public BlockPos getDestination()
    {
        return preferredDirection;
    }
}
