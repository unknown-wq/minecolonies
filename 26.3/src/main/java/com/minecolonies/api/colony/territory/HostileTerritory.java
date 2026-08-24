package com.minecolonies.api.colony.territory;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Is this ground somebody's hostile territory, and whose?" — the whole public answer, for callers inside this mod and
 * out of it.
 *
 * <h2>What a hostile territory is</h2>
 * Ground a player has marked as belonging to an enemy. Underneath it is an ordinary MineColonies colony with a flag
 * set, no town hall, no citizens and nobody as its owner, which is what buys it protection, persistence, client sync
 * and a border on screen for free. None of that matters to a caller: from out here it is an area of the world with an
 * id and a name, and this class is the only thing that needs to be known to ask about it.
 *
 * <h2>How to use it</h2>
 * <pre>{@code
 * final HostileTerritoryMap ground = HostileTerritory.in(level.dimension());
 * if (ground == null)
 * {
 *     return;                                   // nothing hostile in this dimension at all
 * }
 * for (final BlockPos step : lookAhead)
 * {
 *     if (ground.owningTerritory(step) != HostileTerritoryMap.NO_TERRITORY) { ... }
 * }
 * }</pre>
 * Take the map once per tick (or once per job) and probe it as often as wanted. {@link #at} is the one-shot form for a
 * caller that only asks once.
 *
 * <h2>Cost, which is the point of this class existing</h2>
 * <ul>
 *   <li><b>With no hostile territory anywhere</b> — the overwhelmingly common case — {@link #in} is one lookup in a
 *       small map keyed by dimension and returns {@code null}. Nothing is allocated, no colony is resolved, no chunk
 *       is touched, and a caller that stores the {@code null} pays a single reference comparison per position after
 *       that. A world that never uses the feature is indistinguishable from one built without it.</li>
 *   <li><b>With territories present</b>, each position costs one {@code long} hash lookup — see
 *       {@link HostileTerritoryMap}.</li>
 * </ul>
 *
 * <h2>Threading</h2>
 * <b>Every read here is safe from any thread.</b> That is deliberate and it is not what the rest of the colony API
 * offers: {@code IColonyManager#getClaimData} is a {@code computeIfAbsent} on a plain {@code HashMap}, i.e. a read
 * that writes, and calling it off the server thread is a data race. This class exists so that outside code — a flight
 * tick, a pathfinding worker, a render thread — never has to. The index is rebuilt on the server thread and published
 * whole; readers only ever see a complete, immutable, self-consistent snapshot, possibly one that is a moment out of
 * date.
 *
 * <h2>Unloaded and ungenerated ground</h2>
 * Answered like any other ground, without loading anything. A territory's claim lives with the colony rather than with
 * the chunk, so asking about a chunk nobody has ever visited costs the same as asking about the one underfoot, and the
 * answer is {@code NO_TERRITORY} unless a player has actually painted a territory over it.
 */
public final class HostileTerritory
{
    /**
     * Per dimension index. Written on the server thread by {@link Builder#publish}, read from anywhere.
     * <p>
     * A {@link ConcurrentHashMap} rather than a plain one because the write and the reads are genuinely on different
     * threads; the values it holds are immutable, so a reader that wins the race sees either the old index or the new
     * one and never a half built one.
     */
    private static final Map<ResourceKey<Level>, HostileTerritoryMap> INDEX = new ConcurrentHashMap<>();

    /**
     * Private constructor to hide the implicit one.
     */
    private HostileTerritory()
    {
        // Intentionally left empty.
    }

    /**
     * Every hostile territory in one dimension.
     *
     * @param dimension the dimension to ask about.
     * @return the index, or null when no hostile territory owns any ground in that dimension. Null rather than an
     *         empty map on purpose: it lets the caller's hot loop be a null check.
     */
    @Nullable
    public static HostileTerritoryMap in(final ResourceKey<Level> dimension)
    {
        return INDEX.get(dimension);
    }

    /**
     * The territory owning one position, for a caller that asks about a single place rather than many.
     *
     * @param dimension the dimension.
     * @param pos       the position; only its horizontal coordinates are read.
     * @return the territory's colony id, or {@link HostileTerritoryMap#NO_TERRITORY}.
     */
    public static int at(final ResourceKey<Level> dimension, final BlockPos pos)
    {
        final HostileTerritoryMap map = INDEX.get(dimension);
        return map == null ? HostileTerritoryMap.NO_TERRITORY : map.owningTerritory(pos.getX(), pos.getZ());
    }

    /**
     * Whether any hostile territory exists anywhere, in any dimension.
     *
     * @return true if at least one territory owns at least one chunk.
     */
    public static boolean anyExist()
    {
        return !INDEX.isEmpty();
    }

    /**
     * Start rebuilding the index of one dimension.
     * <p>
     * Internal. Called on the server thread when a territory is created, painted, repainted or deleted, and when a
     * dimension loads. Outside callers read; they do not build.
     *
     * @return a fresh builder.
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /**
     * Drop a dimension's index, when that dimension unloads.
     * <p>
     * Internal, same as {@link #builder()}.
     *
     * @param dimension the dimension.
     */
    public static void forget(final ResourceKey<Level> dimension)
    {
        INDEX.remove(dimension);
    }

    /**
     * Collects one dimension's territories and swaps the finished index in as a whole.
     * <p>
     * Building into a separate object rather than editing the live one is what makes every reader thread safe without
     * a lock: an index is never seen half written, because it is never written after it is seen.
     */
    public static final class Builder
    {
        private final Long2IntOpenHashMap          chunkOwners   = new Long2IntOpenHashMap();
        private final Long2ObjectOpenHashMap<long[]> partialChunks = new Long2ObjectOpenHashMap<>();
        private final Int2ObjectOpenHashMap<String>  names         = new Int2ObjectOpenHashMap<>();

        /**
         * Private constructor, {@link HostileTerritory#builder()} is the way in.
         */
        private Builder()
        {
            // Intentionally left empty.
        }

        /**
         * Name a territory, so callers can report whose ground they are on.
         *
         * @param territoryId the territory's colony id.
         * @param name        its name.
         * @return this.
         */
        public Builder territory(final int territoryId, final String name)
        {
            names.put(territoryId, name);
            return this;
        }

        /**
         * Record one chunk of a territory.
         *
         * @param chunkKey    the chunk, packed as {@code ChunkPos#pack}.
         * @param territoryId the territory owning it.
         * @param columnMask  the 256 bit mask of the columns actually owned, or null when the whole chunk is. The
         *                    array is stored as given and must not be modified afterwards.
         * @return this.
         */
        public Builder chunk(final long chunkKey, final int territoryId, @Nullable final long[] columnMask)
        {
            chunkOwners.put(chunkKey, territoryId);
            if (columnMask != null)
            {
                partialChunks.put(chunkKey, columnMask);
            }
            return this;
        }

        /**
         * Swap what has been collected in as the dimension's index, replacing whatever was there.
         * <p>
         * A dimension that turned out to have no territory at all is removed rather than given an empty index, so
         * that {@link HostileTerritory#in} answers null and every caller's fast path stays a null check.
         *
         * @param dimension the dimension this was built for.
         */
        public void publish(final ResourceKey<Level> dimension)
        {
            if (chunkOwners.isEmpty())
            {
                INDEX.remove(dimension);
                return;
            }

            INDEX.put(dimension, new HostileTerritoryMap(chunkOwners, partialChunks, names));
        }
    }
}
