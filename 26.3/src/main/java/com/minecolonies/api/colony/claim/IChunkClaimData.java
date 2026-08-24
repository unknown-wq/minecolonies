package com.minecolonies.api.colony.claim;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Capability for the colony tag for chunks
 */
public interface IChunkClaimData
{
    /**
     * Remove a colony from the list. Only relevant in non dynamic claiming.
     *
     * @param chunk the chunk to remove it from.
     * @param id    the id to remove.
     */
    void removeColony(final int id, final LevelChunk chunk);

    /**
     * Add a new colony to the chunk. Only relevant in non dynamic claiming.
     *
     * @param chunk the chunk to add it to.
     * @param id    the id to add.
     */
    void addColony(final int id, final LevelChunk chunk);

    /**
     * Get a list of colonies with a static claim.
     *
     * @return a list of their ids.
     */
    @NotNull
    List<Integer> getStaticClaimColonies();

    /**
     * Set the owning colony.
     *
     * @param chunk the chunk to set it for.
     * @param id    the id to set.
     */
    void setOwningColony(final int id, final LevelChunk chunk);

    /**
     * Get the owning colony.
     *
     * @return the id of it.
     */
    int getOwningColony();

    /**
     * Reset the capability.
     *
     * @param chunk the chunk to reset.
     */
    void reset(final LevelChunk chunk);

    /**
     * Add the building claim of a certain building.
     *
     * @param colonyId the colony id.
     * @param pos      the position of the building.
     * @param chunk    the chunk to add the claim for.
     */
    void addBuildingClaim(final int colonyId, final BlockPos pos, final LevelChunk chunk);

    /**
     * Remove the building claim of a certain building.
     *
     * @param colonyId the colony id.
     * @param pos      the position of the building.
     * @param chunk    the chunk to remove it from.
     */
    void removeBuildingClaim(final int colonyId, final BlockPos pos, final LevelChunk chunk);

    /**
     * Sets all close colonies.
     *
     * @param colonies the set of colonies.
     */
    void setStaticColonyClaim(final List<Integer> colonies);

    /**
     * Get the claiming buildings map.
     *
     * @return the entire map.
     */
    @NotNull
    Map<Integer, Set<BlockPos>> getAllClaimingBuildings();

    /**
     * Whether this chunk's claim has been cut down to a part of the chunk.
     * <p>
     * A claim is normally the whole 16x16, and that stays the default: a chunk only carries a column mask once
     * something has edited it, and everything that does not care about the distinction can go on reading
     * {@link #getOwningColony()} alone.
     *
     * @return true if only some of the columns belong to the owning colony.
     */
    boolean hasPartialClaim();

    /**
     * Whether one column of this chunk belongs to the owning colony.
     *
     * @param pos any position in the column; only its horizontal coordinates are read.
     * @return true if the column is claimed, and always true while {@link #hasPartialClaim()} is false.
     */
    boolean isColumnClaimed(final BlockPos pos);

    /**
     * Add or remove one column from the claim.
     * <p>
     * The first call on a chunk that has no mask yet starts from the whole chunk, so removing a single column from an
     * ordinary claim leaves the other 255 alone.
     *
     * @param pos     any position in the column; only its horizontal coordinates are read.
     * @param claimed true to claim it, false to cut it out.
     * @param chunk   the chunk, to mark it for saving.
     */
    void setColumnClaimed(final BlockPos pos, final boolean claimed, final LevelChunk chunk);

    /**
     * Drop the column mask, so the whole chunk is claimed again.
     *
     * @param chunk the chunk, to mark it for saving.
     */
    void clearPartialClaim(final LevelChunk chunk);

    /**
     * Cut every column out, leaving a claim that covers nothing.
     * <p>
     * The opposite end of {@link #clearPartialClaim}, and how a chunk claimed from outside the border starts: the
     * colony holds the chunk, but only the columns drawn on it afterwards count as its land.
     *
     * @param chunk the chunk, to mark it for saving.
     */
    void clearAllColumns(final LevelChunk chunk);

    /**
     * How many of the 256 columns are claimed.
     *
     * @return the count, 256 while there is no mask.
     */
    int getClaimedColumnCount();
}
