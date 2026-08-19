package com.minecolonies.core.compatibility.simpleplanes;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.sounds.RaidSounds;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.MessageUtils.MessagePriority;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.network.messages.client.PlayAudioMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import xyz.przemyk.simpleplanes.autopilot.AutopilotRegistry;
import xyz.przemyk.simpleplanes.entities.PlaneEntity;

import java.util.HashMap;
import java.util.Map;

import static com.minecolonies.api.util.constant.TranslationConstants.AIRCRAFT_WARNING;
import static com.minecolonies.api.util.constant.TranslationConstants.AIRCRAFT_WARNING_STRIKE;

/**
 * The air-raid warning: the colony notices hostile aircraft, and says so.
 *
 * <h2>Two detectors, and the difference between them is most of the value</h2>
 *
 * <p><b>The registry.</b> {@link AutopilotRegistry#active()} is the live list of every aircraft under
 * a flight director, pruned on every read and costing no world lookup at all. An aircraft on a scripted
 * attack run carries its aim point in its flight plan from the moment the run is ordered — so a strike
 * called in from eight hundred bleak blocks away is known about <em>before it has moved</em>. At the
 * airframe's roughly 2.8 blocks/tick that is about fourteen seconds of warning.
 *
 * <p><b>The scan.</b> A plane a player is flying by hand is in no registry, and can only be found by
 * looking for it. That is a bounded {@code getEntitiesOfClass} around the colony centre, which is why
 * it runs on a slow interval and not every tick, and why it buys far less time — a colony claim is at
 * most twenty chunks, i.e. 320 blocks, which at the same speed is under six seconds.
 *
 * <p>Both are worth having and they are not interchangeable. The proximity scan is the only thing that
 * sees a hostile player; the registry is the only thing that sees a strike in time to matter.
 *
 * <h2>What it does not do</h2>
 * Shoot. That is {@link AntiAirBattery}, and it is a separate feature with a separate cost. This raises
 * the alarm and points the guards at where it is going to happen.
 */
public final class AircraftWatch
{
    /**
     * Ticks between sweeps. Aircraft are large, slow and few; a sweep a second is ample and keeps the
     * entity scan off the hot path.
     */
    private static final int SWEEP_INTERVAL = 20;

    /**
     * How far from the colony centre a merely-present aircraft is worth mentioning.
     */
    private static final int SIGHTING_RADIUS = 160;

    /**
     * Ticks before the same colony may be warned again, so a plane circling overhead is one alarm and
     * not sixty.
     */
    private static final int WARNING_COOLDOWN = 1200;

    /**
     * How far a guard may be from the threatened point and still be sent to it.
     */
    private static final int GUARD_CALL_RANGE = 200;

    /**
     * Last warning per colony id.
     */
    private static final Map<Integer, Long> LAST_WARNED = new HashMap<>();

    private AircraftWatch()
    {
    }

    /**
     * One sweep, on the interval.
     *
     * @param level the level being ticked.
     */
    static void tick(final ServerLevel level)
    {
        if (!MineColonies.getConfig().getServer().aircraftWarnings.get()
              || level.getGameTime() % SWEEP_INTERVAL != 0)
        {
            return;
        }

        for (final IColony colony : IColonyManager.getInstance().getColonies(level))
        {
            if (colony.isHostile())
            {
                // Enemy ground has no citizens to warn and nobody to warn them.
                continue;
            }
            check(level, colony);
        }
    }

    private static void check(final ServerLevel level, final IColony colony)
    {
        final Long last = LAST_WARNED.get(colony.getID());
        if (last != null && level.getGameTime() - last < WARNING_COOLDOWN)
        {
            return;
        }

        for (final PlaneEntity plane : AutopilotRegistry.active())
        {
            if (plane.level() != level || SimplePlanesCompat.isFriendly(plane) || !plane.isAlive())
            {
                continue;
            }

            final BlockPos strikeTarget = SimplePlanesCompat.strikeTargetOf(plane);
            if (strikeTarget != null)
            {
                // The strongest signal available: an aim point the colony owns, read straight off the
                // flight plan, whatever distance the aircraft is currently at.
                final IColony targeted = IColonyManager.getInstance().getColonyByPosFromWorld(level, strikeTarget);
                if (targeted != null && targeted.getID() == colony.getID())
                {
                    warn(level, colony, plane.position(), strikeTarget, true);
                    return;
                }
                continue;
            }

            if (plane.blockPosition().closerThan(colony.getCenter(), SIGHTING_RADIUS))
            {
                warn(level, colony, plane.position(), plane.blockPosition(), false);
                return;
            }
        }

        // Anything nobody is flying for us and nobody registered: a player at the controls.
        for (final PlaneEntity plane : level.getEntitiesOfClass(PlaneEntity.class,
          new net.minecraft.world.phys.AABB(colony.getCenter()).inflate(SIGHTING_RADIUS)))
        {
            if (!plane.isAlive() || SimplePlanesCompat.isFriendly(plane))
            {
                continue;
            }
            warn(level, colony, plane.position(), plane.blockPosition(), false);
            return;
        }
    }

    /**
     * Sounds the alarm and points the guards.
     *
     * @param level     the level.
     * @param colony    the colony to warn.
     * @param aircraft  where the aircraft is.
     * @param threatens the point under threat — the aim point of a strike, or the aircraft itself.
     * @param strike    true if this is a known attack run rather than a sighting.
     */
    private static void warn(
      final ServerLevel level,
      final IColony colony,
      final Vec3 aircraft,
      final BlockPos threatens,
      final boolean strike)
    {
        LAST_WARNED.put(colony.getID(), level.getGameTime());

        MessageUtils.format(strike ? AIRCRAFT_WARNING_STRIKE : AIRCRAFT_WARNING,
            BlockPosUtil.calcDirection(colony.getCenter(), BlockPos.containing(aircraft)).getLongText(),
            colony.getName())
          .withPriority(MessagePriority.DANGER)
          .sendTo(colony)
          .forManagers();

        PlayAudioMessage.sendToAll(colony, false, false,
          new PlayAudioMessage(RaidSounds.WARNING, SoundSource.HOSTILE));

        // The same call the raid manager makes when raiders reach a building: send the guards who can
        // get there to where it is going to happen. Against a strike that is the aim point, which is
        // known long before the aircraft is.
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building instanceof final AbstractBuildingGuards guards
                  && BlockPosUtil.getDistanceSquared(building.getPosition(), threatens) < GUARD_CALL_RANGE * GUARD_CALL_RANGE)
            {
                guards.setTempNextPatrolPoint(threatens);
            }
        }
    }
}
