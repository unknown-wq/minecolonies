package com.minecolonies.api.colony.managers.interfaces;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.colonyEvents.IColonyRaidEvent;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Interface implementing all methods required for all raider managers.
 */
public interface IRaiderManager
{
    public enum RaidSpawnResult
    {
        SUCCESS,
        TOO_SMALL,
        CANNOT_RAID,
        NO_SPAWN_POINT,
        ERROR
    }

    /**
     * Checks if the raider manager can have raider events.
     *
     * @return true if so.
     */
    boolean canHaveRaiderEvents();

    /**
     * Checks if raiders will raid tonight.
     *
     * @return true if so.
     */
    boolean willRaidTonight();

    /**
     * Set that the manager can receive raider events.
     *
     * @param canHave true or false.
     */
    void setCanHaveRaiderEvents(final boolean canHave);

    /**
     * Set if raiders will raid tonight.
     *
     * @param raidSettings the settings for the next raid, or null to stop the next raid.
     */
    void setRaidNextNight(final RaidSettings raidSettings);

    /**
     * Returns whether spies are enabled
     *
     * @return true if enabled.
     */
    boolean areSpiesEnabled();

    /**
     * Sets whether spies are enabled
     *
     * @param enabled true if enabled.
     */
    void setSpiesEnabled(boolean enabled);

    /**
     * Trigger a specific type of raid on a colony.
     *
     * @param raidSettings the settings for this raid.
     */
    RaidSpawnResult raiderEvent(final @NotNull RaidSettings raidSettings);

    /**
     * Calculates the spawn position for raids
     *
     * @return the spawn location.
     */
    BlockPos calculateSpawnLocation();

    /**
     * Getter for the last spawn points.
     *
     * @return a copy of the list
     */
    List<BlockPos> getLastSpawnPoints();

    /**
     * Calculates the barbarian amount for raids
     *
     * @param raidLevel the colonies raidlevel
     * @return the number of barbs.
     */
    int calculateRaiderAmount(final int raidLevel);

    /**
     * Whether the colony is currently raided.
     *
     * @return true if so.
     */
    boolean isRaided();

    /**
     * Called on nightfall.
     */
    void onNightFall();

    /**
     * Returns the amount of nights since the last raid
     *
     * @return the number of nights.
     */
    int getNightsSinceLastRaid();

    /**
     * Sets the amount of nights since the last raid
     *
     * @param nightsSinceLastRaid the nights to set.
     */
    void setNightsSinceLastRaid(int nightsSinceLastRaid);

    /**
     * Whether the colony can be raided.
     *
     * @return true if possible.
     */
    boolean canRaid();

    /**
     * calculates the colonies raid level
     *
     * @return the raid level.
     */
    int getColonyRaidLevel();

    /**
     * Returns a random building for raiders to go to, groups up 3 raiders to the same position.
     *
     * @return a random building.
     */
    BlockPos getRandomBuilding();

    /**
     * Gets the difficulty modifier for raids, default difficulty is 1.0
     *
     * @return difficulty
     */
    double getRaidDifficultyModifier();

    /**
     * Called on loosing a citizen, to record deaths during raids
     * @param citizen that died
     */
    void onLostCitizen(ICitizenData citizen);

    /**
     * Writes the raid manager to nbt
     * @param compound to write to
     */
    void write(CompoundTag compound);

    /**
     * Reads the raid manager form nbt
     * @param compound to read from
     */
    void read(CompoundTag compound);

    /**
     * Gets the amount of citizens lost in a raid.
     *
     * @return amount
     */
    int getLostCitizen();

    /**
     * Called when a raider mob dies
     *
     * @param entity
     */
    void onRaiderDeath(AbstractEntityMinecoloniesRaider entity);

    void onRaidEventFinished(IColonyRaidEvent event);

    /**
     * Notify raid manager of a passing through raid.
     */
    void setPassThroughRaid();

    /**
     * Tick the raids that were asked to start at once, moving them along as soon as their spawn path is ready.
     */
    void tickImmediateRaids();

    /**
     * Corrects the position recorded for the most recent spawn of a raid type.
     *
     * <p>The manager records where it <em>intended</em> a raid to arrive, at the moment it builds the event and
     * before the event has run. That is right for every raid that walks in from the border, and wrong for one that
     * decides its real arrival point later — an air drop picks a building to come down over when it starts, which is
     * hundreds of blocks from the ground point chosen here. Without this, {@code /mc colony raid info} and
     * {@link #getLastSpawnPoints()} would report a plausible compass direction that is not where anything happened.
     *
     * @param raidType the event type whose latest entry should be corrected.
     * @param pos      where the raid really arrives.
     */
    default void updateLastSpawnPoint(final net.minecraft.resources.Identifier raidType, final BlockPos pos)
    {
        // Nothing to correct in an implementation that keeps no history.
    }

    record RaidSettings(
        boolean forcedSpawn,
        @Nullable String raidType,
        boolean allowShips,
        @Nullable Integer raiderAmount,
        @Nullable BlockPos location,
        @Nullable Double strength,
        boolean immediate)
    {
        public RaidSettings(
          final boolean forcedSpawn,
          final @Nullable String raidType,
          final boolean allowShips,
          final @Nullable Integer raiderAmount,
          final @Nullable BlockPos location)
        {
            this(forcedSpawn, raidType, allowShips, raiderAmount, location, null, false);
        }

        public RaidSettings withExplicitType(final @Nullable String raidType)
        {
            return new RaidSettings(forcedSpawn, raidType, allowShips, raiderAmount, location, strength, immediate);
        }

        public RaidSettings withImmediateStart()
        {
            return new RaidSettings(forcedSpawn, raidType, allowShips, raiderAmount, location, strength, true);
        }

        public static RaidSettings defaultRaidSettings()
        {
            return new RaidSettings(false, null, true, null, null);
        }
    }
}
