package com.minecolonies.core.compatibility.simpleplanes;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.compatibility.simpleplanes.AircraftCompat;
import com.minecolonies.api.util.Log;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.przemyk.simpleplanes.autopilot.AutopilotRegistry;
import xyz.przemyk.simpleplanes.autopilot.FlightPlan;
import xyz.przemyk.simpleplanes.autopilot.PlaneAutopilot;
import xyz.przemyk.simpleplanes.entities.ParachuteEntity;
import xyz.przemyk.simpleplanes.entities.PlaneEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Simple Planes half of the aircraft integration. Everything that names a {@code xyz.przemyk} type
 * is in this package and nowhere else.
 *
 * <p>Installed by {@code MineColonies#onInitialize} only when the loader reports {@code simpleplanes}
 * present. With the mod absent this class is never loaded — the field in
 * {@code Compatibility.aircraftCompat} keeps its {@link AircraftCompat} default, which is a complete
 * no-aircraft implementation rather than a stub — and when the jar is not even available to build
 * against, {@code build.gradle} drops the whole package from compilation.
 *
 * <h2>Why one tick registration for three features</h2>
 * The drop runs, the warning and the anti-air battery all want the same thing: a server tick that runs
 * whether or not any player is nearby. Registering three callbacks would be three list walks over the
 * same empty state on 99% of ticks, so they share one, and each part returns immediately when it has
 * nothing live. The registration happens once, from {@link #init}, because Fabric callbacks cannot be
 * removed.
 */
public class SimplePlanesCompat extends AircraftCompat
{
    /**
     * Aircraft the colony sent itself. Neither warned about nor shot at.
     *
     * <p>Entity ids rather than references: this only ever answers "is this one ours", the aircraft
     * that answer concerns are alive for minutes at most, and holding strong references to entities
     * from a static set is how a world leaks. Ids are recycled by the level eventually, which in the
     * worst case means one aircraft is mistaken for friendly long after ours is gone — the failure
     * mode is a missed warning, not a crash, and {@link #forget} keeps the set short in practice.
     */
    private static final Set<Integer> FRIENDLY = new HashSet<>();

    /**
     * Set once the shared tick is hooked. Fabric events cannot be unsubscribed, so this is idempotent
     * rather than reversible.
     */
    private static boolean hooked = false;

    /**
     * Installs the implementation. Safe to call more than once.
     */
    public static synchronized void init()
    {
        if (hooked)
        {
            return;
        }
        hooked = true;
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            DropRunTracker.tick(level);
            AircraftWatch.tick(level);
            AntiAirBattery.tick(level);
        });
        Log.getLogger().info("MineColonies: Simple Planes detected, aircraft integration enabled.");
    }

    @Override
    public boolean isPresent()
    {
        return true;
    }

    @Override
    public boolean launchDropRun(final ServerLevel level, final BlockPos dropPos, final DropRun handler)
    {
        return DropRunTracker.launch(level, dropPos, handler);
    }

    /**
     * Puts the raider under a parachute, and keeps the inherited slow falling as a backstop.
     *
     * <p><b>Both, not either.</b> The parachute is what it should look like:
     * {@code ParachuteEntity#tick} zeroes its own fall distance, decays horizontal motion and clamps
     * the descent to 0.1 blocks/tick, then removes itself and drops a parachute item when the block
     * under it stops being replaceable. Nothing in MineColonies dismounts a passenger from it — the
     * navigator's own dismount is guarded on {@code MinecoloniesBoat} and {@code MinecoloniesMinecart}
     * specifically ({@code MinecoloniesAdvancedPathNavigate#tick}), and vanilla {@code LivingEntity}
     * only lets go underwater or on going to sleep.
     *
     * <p>But a raider that does come off the parachute for any reason is a raider falling seventy
     * blocks onto stone, and that is a silent, total failure of the drop. Slow falling costs one
     * effect instance, is invisible, and turns that failure into a slightly odd-looking landing. It is
     * applied first so it is already in place before the mount is attempted.
     */
    @Override
    public void deploy(final LivingEntity raider)
    {
        super.deploy(raider);

        try
        {
            final ParachuteEntity parachute = new ParachuteEntity(raider.level());
            parachute.setPos(raider.getX(), raider.getY(), raider.getZ());
            parachute.setDeltaMovement(raider.getDeltaMovement());
            raider.level().addFreshEntity(parachute);
            // force = true: the raider has no goal that would ever choose to board, and this is not an
            // invitation. sendEventAndTriggers stays on so the client is told, or the parachute is
            // invisible to everyone watching.
            raider.startRiding(parachute, true, true);
        }
        catch (final RuntimeException e)
        {
            // Never let a decorative failure cost the raid its raiders: the slow falling above is
            // already applied, so a raider whose parachute could not be created still lands alive.
            Log.getLogger().warn("Could not deploy a parachute for an air-dropped raider", e);
        }
    }

    @Override
    public List<AircraftSighting> sightings(final ServerLevel level, final BlockPos centre, final int radius)
    {
        final List<AircraftSighting> found = new ArrayList<>();
        final Set<Integer> seen = new HashSet<>();

        // Scripted aircraft first, from the registry rather than from the world. This is the half that
        // matters: the registry knows about an attack run the instant it is ordered, hundreds of blocks
        // away and long before anything is within scanning distance of the colony.
        for (final PlaneEntity plane : AutopilotRegistry.active())
        {
            if (plane.level() != level || FRIENDLY.contains(plane.getId()))
            {
                continue;
            }
            final BlockPos strikeTarget = strikeTargetOf(plane);
            final boolean nearby = plane.blockPosition().closerThan(centre, radius);
            if (strikeTarget == null && !nearby)
            {
                continue;
            }
            seen.add(plane.getId());
            found.add(new AircraftSighting(plane.getId(), plane.position(), strikeTarget, plane.getControllingPassenger() == null));
        }

        // Then the ones nobody registered: a plane a player is flying by hand is not in the registry at
        // all, so it can only be found by looking. Bounded to the radius asked for, and the caller runs
        // this on a slow interval for exactly that reason.
        final AABB box = new AABB(centre).inflate(radius);
        for (final PlaneEntity plane : level.getEntitiesOfClass(PlaneEntity.class, box))
        {
            if (seen.contains(plane.getId()) || FRIENDLY.contains(plane.getId()) || !plane.isAlive())
            {
                continue;
            }
            found.add(new AircraftSighting(plane.getId(), plane.position(), null, plane.getControllingPassenger() == null));
        }

        return found;
    }

    @Override
    public List<AircraftReport> aircraft(final ServerLevel level, final BlockPos centre, final int radius)
    {
        final List<AircraftReport> found = new ArrayList<>();
        final Set<Integer> seen = new HashSet<>();

        // Every scripted aircraft in the level, at any distance and whatever its plan says. This is
        // the half that answers the question the command exists for: an air raid's transport is on a
        // plain route rather than an attack run, so the warning's own scan deliberately ignores it
        // until it is close, and the registry is the only thing that knows about it while it is still
        // three hundred blocks out.
        for (final PlaneEntity plane : AutopilotRegistry.active())
        {
            if (plane.level() != level || !plane.isAlive())
            {
                continue;
            }
            seen.add(plane.getId());
            found.add(report(plane));
        }

        // Then everything else within reach: a plane a player is flying by hand is in no registry, and
        // neither is one abandoned in a field, so both can only be found by looking.
        final AABB box = new AABB(centre).inflate(radius);
        for (final PlaneEntity plane : level.getEntitiesOfClass(PlaneEntity.class, box))
        {
            if (seen.contains(plane.getId()) || !plane.isAlive())
            {
                continue;
            }
            found.add(report(plane));
        }

        return found;
    }

    /**
     * Turns one aircraft into the vanilla-typed description the API hands out.
     *
     * <p>The flight plan is flattened to a destination and a flag here rather than in the command,
     * because this is the only side allowed to know what a {@code FlightPlan} is.
     *
     * @param plane the aircraft.
     * @return the report.
     */
    private static AircraftReport report(final PlaneEntity plane)
    {
        BlockPos destination = null;
        boolean strike = false;

        final PlaneAutopilot autopilot = plane.getAutopilot();
        if (autopilot != null && autopilot.getPlan() != null)
        {
            final FlightPlan plan = autopilot.getPlan();
            if (plan.kind() == FlightPlan.Kind.STRIKE)
            {
                destination = plan.strikeTarget();
                strike = true;
            }
            else
            {
                // The waypoint it is flying at now, not the last one on the list: "where is it
                // headed" means the next place it will be, which is what someone trying to intercept
                // it or stand under it needs.
                final Vec3 waypoint = plan.currentWaypointGround();
                if (waypoint != null)
                {
                    destination = BlockPos.containing(waypoint);
                }
            }
        }

        return new AircraftReport(plane.getId(),
          plane.getType().getDescription(),
          plane.position(),
          destination,
          strike,
          plane.getControllingPassenger() == null);
    }

    @Override
    public void markFriendly(final int entityId)
    {
        remember(entityId);
    }

    @Override
    public List<Emplacement> emplacements(final IColony colony)
    {
        return AntiAirBattery.emplacements(colony);
    }

    /**
     * Static form of {@link #markFriendly}, for the parts of this package that have no proxy instance
     * to hand.
     *
     * @param entityId the aircraft's entity id.
     */
    static void remember(final int entityId)
    {
        FRIENDLY.add(entityId);
    }

    /**
     * Stops treating an aircraft as ours. Called when one of our own runs ends, so the set does not
     * grow for the lifetime of the server.
     *
     * @param entityId the aircraft's entity id.
     */
    static void forget(final int entityId)
    {
        FRIENDLY.remove(entityId);
    }

    /**
     * Whether this aircraft is one of the colony's own.
     *
     * @param plane the aircraft.
     * @return true if it should not be warned about or engaged.
     */
    static boolean isFriendly(final PlaneEntity plane)
    {
        return FRIENDLY.contains(plane.getId());
    }

    /**
     * The point an aircraft is flying a scripted attack run at, or null if it is not on one.
     *
     * @param plane the aircraft.
     * @return the aim point, or null.
     */
    static BlockPos strikeTargetOf(final PlaneEntity plane)
    {
        final PlaneAutopilot autopilot = plane.getAutopilot();
        if (autopilot == null || autopilot.getPlan() == null)
        {
            return null;
        }
        final FlightPlan plan = autopilot.getPlan();
        return plan.kind() == FlightPlan.Kind.STRIKE ? plan.strikeTarget() : null;
    }

    /**
     * Squared horizontal distance between two points, for the many range tests in this package.
     *
     * @param a first point.
     * @param b second point.
     * @return the squared distance, ignoring Y.
     */
    static double horizontalDistSqr(final Vec3 a, final Vec3 b)
    {
        final double dx = a.x - b.x;
        final double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}
