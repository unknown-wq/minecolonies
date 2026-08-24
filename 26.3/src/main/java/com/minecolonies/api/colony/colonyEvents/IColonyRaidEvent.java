package com.minecolonies.api.colony.colonyEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/**
 * Interface type for raid events
 */
public interface IColonyRaidEvent extends IColonyEntitySpawnEvent
{
    /**
     * Get the normal raider type.
     *
     * @return the normal type.
     */
    EntityType<?> getNormalRaiderType();

    /**
     * Get the archer raider type.
     *
     * @return the archer type.
     */
    EntityType<?> getArcherRaiderType();

    /**
     * Get the boss raider type.
     *
     * @return the boss type.
     */
    EntityType<?> getBossRaiderType();

    /**
     * Add a spawner to an event.
     *
     * @param pos the pos to add the spawner at.
     */
    void addSpawner(final BlockPos pos);

    /**
     * Gets the list of waypoints
     */
    List<BlockPos> getWayPoints();

    /**
     * Whether or not the raid is still active.
     * @return true if so.
     */
    default boolean isRaidActive()
    {
        return getStatus() == EventStatus.PROGRESSING ||getStatus() == EventStatus.PREPARING;
    }

    /**
     * How many raiders the event still counts as alive. That is not the same as the number of entities: a raider whose
     * chunk stops being loaded is discarded and queued to be put back, so the raid outlives the entities that carry it.
     *
     * @return the number of raiders left.
     */
    default int getRemainingRaiderCount()
    {
        return getEntities().size();
    }

    /**
     * Whether everything the event needs before {@link IColonyEvent#onStart()} is in place, which in practice means
     * its spawn path has finished computing. Starting before that costs the raid its waypoints, so a raid that is
     * being started on demand waits for this rather than for the next colony tick.
     *
     * @return true if the event can be started right now.
     */
    default boolean isReadyToStart()
    {
        return true;
    }

    /**
     * Drop whatever waiting the event does between spawning its raiders and sending them at the colony, so a raid
     * asked for by name arrives at once instead of sitting at its campfires first.
     */
    default void skipPreparation()
    {
        // Nothing to skip by default.
    }
}
