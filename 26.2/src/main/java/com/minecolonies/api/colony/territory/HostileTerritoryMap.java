package com.minecolonies.api.colony.territory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

/**
 * Every hostile territory of one dimension, frozen at a moment in time, in a shape that can be asked "who owns this
 * column?" from any thread without touching the world.
 *
 * <h2>What this is for</h2>
 * The ownership answer MineColonies keeps for its own use — {@code IColonyManager#getClaimData} — is a
 * {@code computeIfAbsent} on a plain {@code HashMap}, so reading it <em>writes</em> to it, and it is only safe on the
 * server thread. That is fine inside the mod, where every caller is on that thread; it is not fine for an outside
 * caller asking a question thousands of times a second from a flight tick or a pathfinding worker. This is the answer
 * for those callers: built once on the server thread whenever a territory changes, published as a whole, and
 * thereafter read only.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li><b>Immutable.</b> Nothing here mutates after {@link HostileTerritory} hands it out. Every method may be called
 *       from any thread, concurrently, including the pathfinding worker and a client render thread.</li>
 *   <li><b>Not live.</b> This is a snapshot. Hold on to one for the length of a single job or a single tick and the
 *       answers stay self-consistent; hold one for a minute and it may be stale by however long ago the player last
 *       repainted a border. Ask {@link HostileTerritory#in} again to get the current one.</li>
 *   <li><b>No world access.</b> A query never loads, generates or even looks at a chunk, so a question about
 *       unexplored or unloaded ground is answered as readily as one about the block under the asker's feet — and it
 *       is answered {@code 0}, since ground nobody has claimed belongs to nobody.</li>
 *   <li><b>Column precision.</b> A territory is painted by hand with the border scepter and may own part of a chunk,
 *       so {@link #owningTerritory} is precise to the single 1×1 column. {@link #chunkTerritory} is the coarse
 *       question, for a caller that wants to skip a whole chunk cheaply before asking about positions inside it.</li>
 * </ul>
 *
 * <h2>Cost</h2>
 * {@link #owningTerritory} is one {@code long} hash lookup, plus a second one and a bit test on the rare chunk that is
 * only partly owned. There is no allocation and no synchronisation on any path.
 */
public final class HostileTerritoryMap
{
    /**
     * The answer for ground no territory owns, and the same value MineColonies uses internally for "no colony".
     */
    public static final int NO_TERRITORY = 0;

    /**
     * How many longs a 16x16 column mask takes.
     */
    private static final int MASK_WORDS = 4;

    /**
     * Chunk key ({@link ChunkPos#pack(int, int)}) to the territory owning it. Absent means no territory owns any of it.
     */
    private final Long2IntMap chunkOwners;

    /**
     * Chunk key to the 256 bit column mask of the chunks that are only partly owned. A chunk absent from here but
     * present in {@link #chunkOwners} is owned whole.
     */
    private final Long2ObjectMap<long[]> partialChunks;

    /**
     * Territory id to its name, so a caller can say <i>whose</i> airspace this is without resolving a colony (which
     * is a server-thread-only operation).
     */
    private final Int2ObjectMap<String> names;

    /**
     * Build an index. Only {@link HostileTerritory} does this, on the server thread.
     *
     * @param chunkOwners   chunk key to owning territory.
     * @param partialChunks chunk key to column mask, for the partly owned chunks only.
     * @param names         territory id to name.
     */
    HostileTerritoryMap(final Long2IntOpenHashMap chunkOwners,
      final Long2ObjectOpenHashMap<long[]> partialChunks,
      final Int2ObjectOpenHashMap<String> names)
    {
        chunkOwners.defaultReturnValue(NO_TERRITORY);
        this.chunkOwners = chunkOwners;
        this.partialChunks = partialChunks;
        this.names = names;
    }

    /**
     * Whether any territory owns any ground in this dimension.
     *
     * @return true if there is nothing to avoid.
     */
    public boolean isEmpty()
    {
        return chunkOwners.isEmpty();
    }

    /**
     * The territory owning one column of the world.
     *
     * @param blockX the column's block x.
     * @param blockZ the column's block z.
     * @return the territory's colony id, or {@link #NO_TERRITORY} when no territory owns this column.
     */
    public int owningTerritory(final int blockX, final int blockZ)
    {
        final long key = ChunkPos.pack(blockX >> 4, blockZ >> 4);
        final int owner = chunkOwners.get(key);
        if (owner == NO_TERRITORY)
        {
            return NO_TERRITORY;
        }

        final long[] mask = partialChunks.get(key);
        if (mask == null)
        {
            return owner;
        }

        final int bit = ((blockZ & 15) << 4) | (blockX & 15);
        return (mask[bit >> 6] & (1L << (bit & 63))) != NO_TERRITORY ? owner : NO_TERRITORY;
    }

    /**
     * The territory owning the column a position stands in. Only the horizontal coordinates are read: a territory is
     * ground with no top and no bottom, so a plane a hundred blocks up is over it exactly as much as a citizen
     * standing on it is in it.
     *
     * @param pos the position.
     * @return the territory's colony id, or {@link #NO_TERRITORY}.
     */
    public int owningTerritory(final BlockPos pos)
    {
        return owningTerritory(pos.getX(), pos.getZ());
    }

    /**
     * The territory holding a chunk, in whole or in part. The cheap coarse question: a non-zero answer does not mean
     * every column of the chunk is that territory's, only that some of it is and that {@link #owningTerritory} is
     * worth asking about columns inside it.
     *
     * @param chunkX the chunk x.
     * @param chunkZ the chunk z.
     * @return the territory's colony id, or {@link #NO_TERRITORY}.
     */
    public int chunkTerritory(final int chunkX, final int chunkZ)
    {
        return chunkOwners.get(ChunkPos.pack(chunkX, chunkZ));
    }

    /**
     * The name of a territory, as the player named it.
     *
     * @param territoryId the id {@link #owningTerritory} returned.
     * @return the name, or null if that id is not a territory in this dimension.
     */
    @Nullable
    public String name(final int territoryId)
    {
        return names.get(territoryId);
    }
}
