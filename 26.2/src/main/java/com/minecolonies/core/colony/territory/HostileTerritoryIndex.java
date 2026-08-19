package com.minecolonies.core.colony.territory;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.ChunkClaimData;
import com.minecolonies.api.colony.territory.HostileTerritory;
import com.minecolonies.core.colony.Colony;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Keeps {@link HostileTerritory}'s read-only index in step with the colonies it is built from.
 * <p>
 * The index exists because the live ownership map cannot be read off the server thread (see
 * {@link HostileTerritory}); this is the other half of that bargain — the part that runs on the server thread, walks
 * the few colonies flagged hostile, and publishes the result whole. It is a rebuild rather than an edit because the
 * published index must never be seen half written, and a territory changes only when a player repaints it, which is
 * rare enough that rebuilding a handful of chunk entries is not worth being clever about.
 * <p>
 * The claim it reads is {@code Colony#getClaimData()}, the colony's own map, and deliberately not the global one. That
 * is the same map {@code ColonyView} serialises to the client, so the API and the red border on screen are built from
 * exactly the same set of chunks and can never disagree about where a territory is.
 */
public final class HostileTerritoryIndex
{
    /**
     * How many longs a 16x16 column mask takes.
     */
    private static final int MASK_WORDS = 4;

    /**
     * Private constructor to hide the implicit one.
     */
    private HostileTerritoryIndex()
    {
        // Intentionally left empty.
    }

    /**
     * Rebuild one dimension's index from the colonies currently in it. Server thread only.
     *
     * @param level the level whose dimension to rebuild.
     */
    public static void refresh(final ServerLevel level)
    {
        final HostileTerritory.Builder builder = HostileTerritory.builder();

        for (final IColony iColony : IColonyManager.getInstance().getColonies(level))
        {
            if (!(iColony instanceof final Colony colony) || !colony.isHostile())
            {
                continue;
            }

            builder.territory(colony.getID(), colony.getName());
            collect(builder, colony);
        }

        builder.publish(level.dimension());
    }

    /**
     * Put every chunk a territory actually owns into the index.
     *
     * @param builder the index being built.
     * @param colony  the territory.
     */
    private static void collect(final HostileTerritory.Builder builder, final Colony colony)
    {
        final int id = colony.getID();
        for (final Long2ObjectMap.Entry<ChunkClaimData> entry : colony.getClaimData().long2ObjectEntrySet())
        {
            final ChunkClaimData claim = entry.getValue();
            if (claim.getOwningColony() != id)
            {
                // The colony touched this chunk once but does not hold it now. Ownership is the only thing the
                // question "am I standing on enemy ground?" can be answered from.
                continue;
            }

            builder.chunk(entry.getLongKey(), id, claim.hasPartialClaim() ? mask(entry.getLongKey(), claim) : null);
        }
    }

    /**
     * Read a hand painted chunk's column mask back out, column by column.
     * <p>
     * {@link ChunkClaimData} does not hand its mask array out, and it should not: it is mutable state the chunk edits
     * in place, and the index needs a copy that will never change under a reader on another thread. 256 bit tests on
     * the rare chunk that has a mask at all is a fair price for that guarantee.
     *
     * @param chunkKey the chunk, packed.
     * @param claim    its claim.
     * @return a fresh 256 bit mask.
     */
    private static long[] mask(final long chunkKey, final ChunkClaimData claim)
    {
        final long[] columns = new long[MASK_WORDS];
        final int baseX = ChunkPos.getX(chunkKey) << 4;
        final int baseZ = ChunkPos.getZ(chunkKey) << 4;
        final BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();

        for (int dz = 0; dz < 16; dz++)
        {
            for (int dx = 0; dx < 16; dx++)
            {
                if (claim.isColumnClaimed(column.set(baseX + dx, 0, baseZ + dz)))
                {
                    final int bit = (dz << 4) | dx;
                    columns[bit >> 6] |= 1L << (bit & 63);
                }
            }
        }
        return columns;
    }
}
