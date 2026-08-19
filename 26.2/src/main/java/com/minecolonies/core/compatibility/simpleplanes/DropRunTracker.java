package com.minecolonies.core.compatibility.simpleplanes;

import com.minecolonies.api.compatibility.simpleplanes.AircraftCompat;
import com.minecolonies.api.util.Log;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import xyz.przemyk.simpleplanes.autopilot.AircraftType;
import xyz.przemyk.simpleplanes.autopilot.AutopilotSpawner;
import xyz.przemyk.simpleplanes.autopilot.Blast;
import xyz.przemyk.simpleplanes.entities.PlaneEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Transports flying a raid in, and the tick that drives them.
 *
 * <h2>What is reused rather than written</h2>
 * All of the flying. {@link AutopilotSpawner#launchRoute} creates an unmanned aircraft at the first
 * waypoint, fits it a booster, gives it launch speed and engages the flight director on a
 * {@code FlightPlan.route}; {@code AutopilotRegistry} keeps its chunks resident so it flies whether or
 * not a player is anywhere near it. Nothing here steers, throttles or navigates.
 *
 * <h2>What this adds</h2>
 * The bay. The flight director has no notion of cargo, so the decision to open the doors is made here,
 * from the aircraft's own distance to the drop point, and the raiders themselves are created by the
 * raid event through {@link AircraftCompat.DropRun#dropTick}. Measuring the distance directly rather
 * than waiting for the autopilot to announce a waypoint is deliberate: the arrival radius the flight
 * director sequences on scales with speed and turn radius and can be sixty blocks wide, which is fine
 * for turning a corner and much too coarse for deciding where a raider lands.
 */
public final class DropRunTracker
{
    /**
     * How far out the transport is spawned, and how far past the drop point it flies before turning
     * for its landing. Far enough that it is a run rather than an apparition, and far enough that an
     * anti-air battery has something to shoot at.
     */
    private static final int RUN_IN_DISTANCE = 300;

    /**
     * Blocks from the drop point at which the bay opens.
     */
    private static final double DROP_RADIUS = 36.0;

    /**
     * Ticks between raiders leaving the aircraft. Four is a stick rather than a heap: the aircraft
     * moves a couple of blocks between each one, so they land in a line instead of on top of each
     * other, and the drop reads as a drop.
     */
    private static final int DROP_INTERVAL = 4;

    /**
     * Ticks the run is allowed to last before it is written off. The transit is a few hundred blocks
     * at a bit over one block per tick, so this is several times the expected flight.
     */
    private static final int RUN_TIMEOUT = 4000;

    /**
     * Commanded cruise speed, blocks/tick. Well under the airframe's 2.8 maximum on purpose: a slow
     * transport is one the player can see coming, point at, and shoot at.
     */
    private static final double CRUISE_SPEED = 1.2;

    /**
     * Live runs. Small — one per air raid, and a colony has one raid at a time.
     */
    private static final List<Run> ACTIVE = new ArrayList<>();

    private DropRunTracker()
    {
    }

    /**
     * Sends a transport over a point.
     *
     * @param level   the level to fly in.
     * @param dropPos the point to drop over; its Y is the altitude the run is flown at.
     * @param handler the raid event's callbacks.
     * @return true if an aircraft was created.
     */
    static boolean launch(final ServerLevel level, final BlockPos dropPos, final AircraftCompat.DropRun handler)
    {
        // A bearing rather than a fixed compass direction, so successive raids do not all come in over
        // the same fence.
        final double bearing = level.getRandom().nextDouble() * Math.PI * 2.0;
        final int dx = (int) Math.round(Math.cos(bearing) * RUN_IN_DISTANCE);
        final int dz = (int) Math.round(Math.sin(bearing) * RUN_IN_DISTANCE);

        final BlockPos ingress = dropPos.offset(-dx, 0, -dz);
        final BlockPos egress = dropPos.offset(dx, 0, dz);

        final PlaneEntity plane = AutopilotSpawner.launchRoute(
          level,
          List.of(ingress, dropPos, egress),
          dropPos.getY(),
          1,
          null,
          null,
          CRUISE_SPEED,
          // The transport is not a bomb. If it is brought down it should make a noise and leave a
          // wreck, not a crater -- the raid is the threat, and the aircraft carrying it should not
          // also be a warhead nobody asked for.
          new Blast(3.0F, false, false),
          AircraftType.CARGO);

        if (plane == null)
        {
            return false;
        }

        ACTIVE.add(new Run(plane, dropPos, handler));
        Log.getLogger().debug("Air raid transport #" + plane.getId() + " launched towards " + dropPos.toShortString());
        return true;
    }

    /**
     * One tick of every live run.
     *
     * @param level the level being ticked.
     */
    static void tick(final ServerLevel level)
    {
        if (ACTIVE.isEmpty())
        {
            return;
        }

        for (final Iterator<Run> iterator = ACTIVE.iterator(); iterator.hasNext(); )
        {
            final Run run = iterator.next();
            if (run.plane.level() != level)
            {
                continue;
            }
            if (run.tick())
            {
                iterator.remove();
            }
        }
    }

    /**
     * A transport in flight, and the raid waiting on it.
     */
    private static final class Run
    {
        private final PlaneEntity plane;
        private final Vec3 dropCentre;
        private final AircraftCompat.DropRun handler;

        private boolean bayOpen = false;
        private boolean done = false;
        private int ticks = 0;
        private int nextDrop = 0;

        private Run(final PlaneEntity plane, final BlockPos dropPos, final AircraftCompat.DropRun handler)
        {
            this.plane = plane;
            this.dropCentre = Vec3.atCenterOf(dropPos);
            this.handler = handler;
            // Deliberately NOT marked friendly. It is tempting to protect the raid's own transport from
            // the colony's guns so the raid is guaranteed to happen, and it would be a mistake: a
            // transport shot down on its way in is the best thing in this feature. It is a raid the
            // player can prevent instead of one they can only survive, it rewards the battery they paid
            // for, and it is the reason the run is flown slowly and from three hundred blocks out
            // rather than materialising overhead. The air-raid warning firing on it is likewise correct
            // -- an aircraft full of pirates is exactly what that alarm is for.
        }

        /**
         * @return true when this run is finished and should be dropped from the list.
         */
        private boolean tick()
        {
            if (done)
            {
                return true;
            }
            ticks++;

            if (plane.isRemoved() || !plane.isAlive() || plane.getHealth() <= 0)
            {
                // Shot down, flown into a hill, or otherwise lost. Whatever is still aboard is not
                // coming, and the raid has to be told so or its horde counters never reach zero.
                finish(false);
                return true;
            }

            if (ticks > RUN_TIMEOUT)
            {
                Log.getLogger().debug("Air raid transport #" + plane.getId() + " timed out short of its drop point.");
                finish(false);
                return true;
            }

            if (!bayOpen)
            {
                if (SimplePlanesCompat.horizontalDistSqr(plane.position(), dropCentre) > DROP_RADIUS * DROP_RADIUS)
                {
                    return false;
                }
                bayOpen = true;
            }

            if (ticks < nextDrop)
            {
                return false;
            }
            nextDrop = ticks + DROP_INTERVAL;

            // Once the bay is open it stays open until it is empty, whatever the aircraft does next.
            // Closing it again at the edge of the radius would strand whoever had not jumped yet, and
            // a stick that trails out behind the aircraft is what a drop looks like anyway.
            if (handler.dropTick(plane.position()))
            {
                finish(true);
                return true;
            }
            return false;
        }

        private void finish(final boolean delivered)
        {
            if (done)
            {
                return;
            }
            done = true;
            handler.finished(delivered, plane.position());
        }
    }
}
