package com.minecolonies.core.entity.ai.workers.util;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.other.MinecoloniesBoat;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.entity.pathfinding.navigation.AbstractAdvancedPathNavigate;
import com.minecolonies.core.util.TeleportHelper;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Teleports a worker that has spent too long failing to reach where its job wants it.
 * <p>
 * The navigator has a stuck handler of its own, but it only helps a worker that is walking and getting nowhere. It
 * cannot help one whose destination has no path to it at all: the pathfinder returns nothing, the AI sees "not there
 * yet", returns its own state, and asks again on the next tick. Nothing moves, nothing is logged, no request is
 * outstanding and no message reaches the player — the worker simply stands there. A builder sent to a bridge over
 * water, or to a tower on an island, does this forever.
 * <p>
 * So this watches the destination rather than the path: as long as a worker keeps asking to reach the same place and
 * never gets closer to it, it is stuck, whatever the pathfinder is or is not doing. After the configured time it is
 * put there.
 */
public final class StuckRescue
{
    /**
     * How much closer a worker has to get before it counts as making progress, in blocks. A worker shuffling on the
     * spot moves a little every tick; this is the margin that separates that from actually travelling.
     */
    private static final double PROGRESS_MARGIN = 2.0;

    /**
     * How close counts as arrived, in blocks.
     * <p>
     * The navigator keeps the last requested destination after the worker gets there — nothing clears it until the
     * next path job — so a worker standing at its work site looks exactly like one that has stopped making progress.
     * Anything this close is doing its job, not failing to reach it.
     */
    private static final double ARRIVED_DIST = 6.0;

    /**
     * The destination currently being watched, or null if the worker is not going anywhere in particular.
     */
    @Nullable
    private BlockPos watched;

    /**
     * The closest the worker has been to {@link #watched} since it started trying.
     */
    private double closest;

    /**
     * The game time at which the worker last got meaningfully closer to {@link #watched}.
     */
    private long lastProgress;

    /**
     * Look at where the worker is trying to go and rescue it if it has stopped getting there.
     *
     * @param worker the worker to check, never null.
     */
    public void check(@NotNull final AbstractEntityCitizen worker)
    {
        final int timeout = MineColonies.getConfig().getServer().stuckRescueSeconds.get();
        if (timeout <= 0)
        {
            return;
        }

        if (!(worker.getNavigation() instanceof final AbstractAdvancedPathNavigate navigator))
        {
            return;
        }

        if (worker.getVehicle() instanceof final MinecoloniesBoat boat && boat.isMoored())
        {
            // A worker moored in a boat is not failing to get anywhere, he is standing still because that is the job
            // -- the fisherman casts from open water. The last destination the navigator remembers is wherever he was
            // sent before he parked, so without this he counts as never getting closer to it and is rescued every
            // timeout for as long as he keeps fishing. The rescue does not even move him: a passenger is put back on
            // his seat by the vehicle on the next tick, so all it produced was a "Teleported" line a minute saying
            // something that had not happened.
            watched = null;
            return;
        }

        final BlockPos destination = navigator.getOriginalDestination();
        if (destination == null || destination.equals(BlockPos.ZERO))
        {
            watched = null;
            return;
        }

        final long now = worker.level().getGameTime();
        final double distance = BlockPosUtil.dist(worker.blockPosition(), destination);

        if (distance <= ARRIVED_DIST)
        {
            watched = null;
            return;
        }

        if (!destination.equals(watched))
        {
            // A new destination. Whatever the worker was failing to reach before no longer matters.
            watched = destination;
            closest = distance;
            lastProgress = now;
            return;
        }

        if (distance < closest - PROGRESS_MARGIN)
        {
            closest = distance;
            lastProgress = now;
            return;
        }

        if (now - lastProgress < (long) timeout * 20L)
        {
            return;
        }

        // Deliberately teleported to the destination the job asked for rather than to somewhere near the worker:
        // being stuck means the place it was sent to is the place it could not get to. TeleportHelper picks a
        // standable spot around it, so this does not drop anyone inside a wall or over a drop.
        if (TeleportHelper.teleportCitizen(worker, worker.level(), destination))
        {
            Log.getLogger()
              .info(String.format("Teleported %s to %s: it had not got closer than %.0f blocks in %s seconds.",
                worker.getCitizenData() == null ? worker.getName().getString() : worker.getCitizenData().getName(),
                destination.toShortString(),
                closest,
                timeout));
        }

        // Reset either way. A failed teleport means there was nowhere to stand, and hammering it every tick would
        // only cost a spawn point search per worker per tick for as long as the destination stays unreachable.
        watched = null;
    }
}
