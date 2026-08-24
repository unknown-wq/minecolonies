package com.minecolonies.core.colony.territory;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.colony.territory.HostileTerritory;
import com.minecolonies.api.colony.territory.HostileTerritoryMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Works out one stretch of border for guards to walk, as an ordered line of waypoints.
 *
 * <h2>What a "stretch" is</h2>
 * A border is a line, not an area, and a colony's guards cannot walk all of it. A stretch is the piece of that line
 * <em>nearest the barracks that ordered the patrol</em>, grown outwards from that nearest point in both directions
 * until it is roughly {@link Plan#targetLength()} blocks long. Everything the patrol ever does happens on that line;
 * see {@link #SEARCH_RADIUS_CHUNKS} for the box it is allowed to live in, which is what stops a guard walking to the
 * other side of the world to find a nicer bit of frontier.
 *
 * <h2>The two lines it can find</h2>
 * <ul>
 *   <li>{@link Mode#ENEMY} — the columns on <em>your</em> side of a hostile territory's edge. A chunk qualifies when
 *       no territory owns it but a neighbour of it is owned by one. Standing a patrol point one column the wrong side
 *       of that line would make every guard pay the +25 per node hostile-ground surcharge and detour around the place
 *       he was sent, which reads as the guards being broken, so the line is deliberately the outside of it.</li>
 *   <li>{@link Mode#COLONY} — the edge of the colony's own claim, with no enemy involved. A chunk qualifies when this
 *       colony owns it and a neighbour of it does not.</li>
 * </ul>
 *
 * <h2>Cost</h2>
 * One call scans a fixed {@code (2*(R+1)+1)²} box of chunks, which is 1225 probes at the radius below, and then walks
 * at most a few dozen of them. In {@link Mode#ENEMY} a probe is one {@code long} hash lookup into the immutable
 * territory index; in {@link Mode#COLONY} it is one lookup into the colony manager's claim map, which is
 * {@code getOrDefault} and therefore does not create anything. Nothing here loads a chunk or touches the world.
 * <p>
 * This is <b>not</b> per tick work. {@code BuildingBarracks} caches the result and only asks again when the territory
 * index has actually been rebuilt or several minutes have passed — see {@code BuildingBarracks#borderPlan}.
 *
 * <h2>Threading</h2>
 * Server thread only, because {@link Mode#COLONY} reads {@code IColonyManager#getClaimData}, which is a plain
 * {@code HashMap} behind a {@code computeIfAbsent} on the dimension. {@link Mode#ENEMY} would be safe anywhere, but
 * there is no caller that wants it off-thread and one rule is easier to keep than two.
 */
public final class BorderPatrol
{
    /**
     * How far from the barracks, in chunks, the border is looked for and therefore how far a patrol can ever take a
     * guard from home.
     * <p>
     * This is the whole of the "a patrolling guard must not wander off" guarantee, and it is deliberately a hard box
     * rather than a soft preference: no waypoint outside it is ever built, so none can ever be handed to a guard. 16
     * chunks is 256 blocks, which comfortably holds a 500-block line that snakes, and is well inside the 2000-block
     * ceiling at which the navigator refuses a walk order outright.
     */
    public static final int SEARCH_RADIUS_CHUNKS = 16;

    /**
     * Length in blocks credited for stepping from one chunk to an edge-sharing neighbour.
     */
    private static final int ORTHOGONAL_STEP = 16;

    /**
     * Length in blocks credited for stepping to a corner-sharing neighbour, 16·√2 rounded.
     */
    private static final int DIAGONAL_STEP = 23;

    /**
     * Hard ceiling on the length of a stretch, whatever the mode asked for.
     */
    private static final int MAX_STRETCH_BLOCKS = 600;

    /**
     * The eight neighbour offsets, edge-sharing first so a straight line is preferred to a corner cut.
     */
    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /**
     * Private constructor to hide the implicit one.
     */
    private BorderPatrol()
    {
        // Intentionally left empty.
    }

    /**
     * Which line to walk.
     */
    public enum Mode
    {
        /**
         * Do not patrol a border at all; guards keep whatever patrol they had.
         */
        OFF("com.minecolonies.core.barracks.setting.borderpatrol.off", 0),

        /**
         * Walk your own side of the nearest hostile territory's edge.
         */
        ENEMY("com.minecolonies.core.barracks.setting.borderpatrol.enemy", 500),

        /**
         * Walk the edge of your own claim.
         */
        COLONY("com.minecolonies.core.barracks.setting.borderpatrol.colony", 550);

        /**
         * The translation key this mode shows as on the barracks setting button.
         */
        private final String settingKey;

        /**
         * How many blocks of border this mode tries to cover.
         */
        private final int targetLength;

        Mode(final String settingKey, final int targetLength)
        {
            this.settingKey = settingKey;
            this.targetLength = targetLength;
        }

        /**
         * @return the translation key used as this mode's value in the barracks setting.
         */
        public String settingKey()
        {
            return settingKey;
        }

        /**
         * @return how many blocks of border this mode tries to cover.
         */
        public int targetLength()
        {
            return targetLength;
        }

        /**
         * The mode a setting value names.
         *
         * @param value the stored setting value, which is one of the {@link #settingKey()}s.
         * @return the mode, {@link #OFF} for anything unrecognised.
         */
        public static Mode bySettingKey(@Nullable final String value)
        {
            for (final Mode mode : values())
            {
                if (mode.settingKey.equals(value))
                {
                    return mode;
                }
            }
            return OFF;
        }
    }

    /**
     * Why a stretch could not be found, so the player can be told rather than left watching guards do nothing new.
     */
    public enum Failure
    {
        /**
         * A stretch was found; there is nothing to report.
         */
        NONE("found"),

        /**
         * No hostile territory owns any ground in this dimension at all.
         */
        NO_TERRITORY_AT_ALL("no hostile territory in this dimension"),

        /**
         * There is hostile ground somewhere, but none within the search box of this barracks.
         */
        NO_TERRITORY_IN_RANGE("no hostile ground within " + (SEARCH_RADIUS_CHUNKS * 16) + " blocks of the barracks"),

        /**
         * The colony's own claim has no edge inside the search box, which in practice means the barracks is not in the
         * colony's claim map at all.
         */
        NO_COLONY_BORDER("no colony border within " + (SEARCH_RADIUS_CHUNKS * 16) + " blocks of the barracks");

        /**
         * Plain English for the diagnose report.
         */
        private final String description;

        Failure(final String description)
        {
            this.description = description;
        }

        @Override
        public String toString()
        {
            return description;
        }
    }

    /**
     * One computed stretch: the ordered waypoints and, when there are none, why.
     *
     * @param mode         the mode it was computed for.
     * @param waypoints    the line, in order, each one the centre column of a chunk. Empty when nothing was found.
     * @param failure      why {@link #waypoints} is empty, {@link Failure#NONE} when it is not.
     * @param targetLength how many blocks of border were asked for.
     */
    public record Plan(@NotNull Mode mode, @NotNull List<BlockPos> waypoints, @NotNull Failure failure, int targetLength)
    {
        /**
         * @return true when there is a line to walk.
         */
        public boolean isUsable()
        {
            return !waypoints.isEmpty();
        }
    }

    /**
     * Find the stretch of border nearest a position.
     *
     * @param colony the colony asking, whose dimension is searched and whose claim {@link Mode#COLONY} follows.
     * @param anchor the position the stretch should be nearest to, i.e. the barracks.
     * @param mode   which line to follow.
     * @return the plan, which may hold no waypoints — check {@link Plan#isUsable()}.
     */
    @NotNull
    public static Plan findStretch(@NotNull final IColony colony, @NotNull final BlockPos anchor, @NotNull final Mode mode)
    {
        if (mode == Mode.OFF)
        {
            return new Plan(mode, List.of(), Failure.NONE, 0);
        }

        final HostileTerritoryMap territory = HostileTerritory.in(colony.getDimension());
        if (mode == Mode.ENEMY && territory == null)
        {
            // The overwhelmingly common case, and the cheapest possible answer to it: one map lookup and out. A colony
            // in a world where nobody has ever painted a territory never reaches the chunk scan below.
            return new Plan(mode, List.of(), Failure.NO_TERRITORY_AT_ALL, mode.targetLength());
        }

        // One ring wider than the box we will report border in, so that a chunk on the very edge of the box is judged
        // against a real neighbour rather than against the empty space outside the array.
        final int sampled = SEARCH_RADIUS_CHUNKS + 1;
        final int side = 2 * sampled + 1;
        final int originX = (anchor.getX() >> 4) - sampled;
        final int originZ = (anchor.getZ() >> 4) - sampled;

        final boolean[] inside = new boolean[side * side];
        for (int dz = 0; dz < side; dz++)
        {
            for (int dx = 0; dx < side; dx++)
            {
                inside[dz * side + dx] = mode == Mode.ENEMY
                                           ? territory.chunkTerritory(originX + dx, originZ + dz) != HostileTerritoryMap.NO_TERRITORY
                                           : ownsChunk(colony, originX + dx, originZ + dz);
            }
        }

        // A border chunk is one on the near side of a transition. For the enemy line that is a chunk nobody hostile
        // owns next to one they do; for our own it is a chunk we own next to one we do not.
        final LongOpenHashSet border = new LongOpenHashSet();
        for (int dz = 1; dz < side - 1; dz++)
        {
            for (int dx = 1; dx < side - 1; dx++)
            {
                final boolean self = inside[dz * side + dx];
                if (mode == Mode.ENEMY ? self : !self)
                {
                    continue;
                }

                if (inside[(dz - 1) * side + dx] != self
                      || inside[(dz + 1) * side + dx] != self
                      || inside[dz * side + dx - 1] != self
                      || inside[dz * side + dx + 1] != self)
                {
                    border.add(ChunkPos.pack(originX + dx, originZ + dz));
                }
            }
        }

        if (border.isEmpty())
        {
            return new Plan(mode,
              List.of(),
              mode == Mode.ENEMY ? Failure.NO_TERRITORY_IN_RANGE : Failure.NO_COLONY_BORDER,
              mode.targetLength());
        }

        final List<BlockPos> line = walkLine(border, anchor, Math.min(mode.targetLength(), MAX_STRETCH_BLOCKS));
        return new Plan(mode, line, line.isEmpty() ? Failure.NO_COLONY_BORDER : Failure.NONE, mode.targetLength());
    }

    /**
     * Whether a colony owns a chunk, without creating a claim record for one it has never touched.
     * <p>
     * {@code IColonyManager#getClaimData(dimension, pos)} is {@code getOrDefault} on the inner map, so probing a chunk
     * nobody has claimed answers null and leaves nothing behind. That is the only reason it is safe to sweep a
     * thousand chunks with it.
     *
     * @param colony the colony.
     * @param chunkX the chunk x.
     * @param chunkZ the chunk z.
     * @return true when this colony owns that chunk.
     */
    private static boolean ownsChunk(@NotNull final IColony colony, final int chunkX, final int chunkZ)
    {
        final IChunkClaimData data = IColonyManager.getInstance().getClaimData(colony.getDimension(), new ChunkPos(chunkX, chunkZ));
        return data != null && data.getOwningColony() == colony.getID();
    }

    /**
     * Turn a set of border chunks into one ordered line through the member nearest the anchor.
     * <p>
     * Two arms are grown from that seed and then interleaved, so the stretch is centred on the point nearest the
     * barracks rather than starting there and running off in whichever direction the iteration order happened to pick.
     * Branches in the border — a claim with a hole in it, two territories meeting — are resolved by taking whichever
     * neighbour continues straightest, and the arm simply stops when nothing unused is adjacent.
     *
     * @param border       the border chunks, packed.
     * @param anchor       the position to centre on.
     * @param targetLength how many blocks of line to build.
     * @return the ordered waypoints, one per chunk, at the chunk's centre column.
     */
    @NotNull
    private static List<BlockPos> walkLine(@NotNull final LongOpenHashSet border, @NotNull final BlockPos anchor, final int targetLength)
    {
        long seed = 0;
        long bestDistance = Long.MAX_VALUE;
        for (final long packed : border)
        {
            final long dx = (long) centreX(packed) - anchor.getX();
            final long dz = (long) centreZ(packed) - anchor.getZ();
            final long distance = dx * dx + dz * dz;
            if (distance < bestDistance)
            {
                bestDistance = distance;
                seed = packed;
            }
        }

        final LongOpenHashSet used = new LongOpenHashSet();
        used.add(seed);

        final List<Long> first = new ArrayList<>();
        final List<Long> second = new ArrayList<>();
        growArm(border, used, seed, first, targetLength);
        growArm(border, used, seed, second, targetLength);

        // Spend the budget alternately so both arms are represented; a border that dead-ends one way still gets its
        // full length out of the other.
        final List<Long> ordered = new ArrayList<>();
        int length = 0;
        int firstTaken = 0;
        int secondTaken = 0;
        while (length < targetLength && (firstTaken < first.size() || secondTaken < second.size()))
        {
            if (firstTaken < first.size())
            {
                firstTaken++;
                length += stepLength(firstTaken == 1 ? seed : first.get(firstTaken - 2), first.get(firstTaken - 1));
            }
            if (length < targetLength && secondTaken < second.size())
            {
                secondTaken++;
                length += stepLength(secondTaken == 1 ? seed : second.get(secondTaken - 2), second.get(secondTaken - 1));
            }
        }

        for (int i = firstTaken - 1; i >= 0; i--)
        {
            ordered.add(first.get(i));
        }
        ordered.add(seed);
        for (int i = 0; i < secondTaken; i++)
        {
            ordered.add(second.get(i));
        }

        final List<BlockPos> waypoints = new ArrayList<>(ordered.size());
        for (final long packed : ordered)
        {
            // Y is filled in when the point is handed to a guard, from the heightmap of a chunk that is actually
            // loaded. Storing the anchor's Y here keeps the plan free of world access.
            waypoints.add(new BlockPos(centreX(packed), anchor.getY(), centreZ(packed)));
        }
        return Collections.unmodifiableList(waypoints);
    }

    /**
     * Grow one arm of the line away from the seed, consuming chunks as it goes.
     *
     * @param border       every border chunk.
     * @param used         chunks already spoken for, added to here.
     * @param seed         the chunk both arms start from.
     * @param arm          the arm to fill, in order away from the seed.
     * @param targetLength the most blocks this arm could possibly need, as a step ceiling.
     */
    private static void growArm(
      @NotNull final LongOpenHashSet border,
      @NotNull final LongOpenHashSet used,
      final long seed,
      @NotNull final List<Long> arm,
      final int targetLength)
    {
        final int maxSteps = targetLength / ORTHOGONAL_STEP + 2;
        long current = seed;
        int previousX = 0;
        int previousZ = 0;

        for (int step = 0; step < maxSteps; step++)
        {
            final int currentX = ChunkPos.getX(current);
            final int currentZ = ChunkPos.getZ(current);

            long best = 0;
            int bestScore = Integer.MIN_VALUE;
            int bestX = 0;
            int bestZ = 0;
            for (final int[] offset : NEIGHBOURS)
            {
                final long candidate = ChunkPos.pack(currentX + offset[0], currentZ + offset[1]);
                if (!border.contains(candidate) || used.contains(candidate))
                {
                    continue;
                }

                // Prefer carrying on in the direction already travelled, then edge-sharing over corner-sharing. The
                // first step of an arm has no direction yet and simply takes the first neighbour in the fixed order,
                // which is what makes the two arms leave the seed opposite ways.
                final int straightness = previousX * offset[0] + previousZ * offset[1];
                final int score = straightness * 4 - Math.abs(offset[0] * offset[1]);
                if (score > bestScore)
                {
                    bestScore = score;
                    best = candidate;
                    bestX = offset[0];
                    bestZ = offset[1];
                }
            }

            if (bestScore == Integer.MIN_VALUE)
            {
                return;
            }

            used.add(best);
            arm.add(best);
            current = best;
            previousX = bestX;
            previousZ = bestZ;
        }
    }

    /**
     * How many blocks of border one chunk step is worth.
     *
     * @param from the chunk stepped from, packed.
     * @param to   the chunk stepped to, packed.
     * @return the length in blocks.
     */
    private static int stepLength(final long from, final long to)
    {
        return ChunkPos.getX(from) != ChunkPos.getX(to) && ChunkPos.getZ(from) != ChunkPos.getZ(to) ? DIAGONAL_STEP : ORTHOGONAL_STEP;
    }

    /**
     * The block x of a chunk's centre column.
     *
     * @param packed the packed chunk position.
     * @return the block x.
     */
    private static int centreX(final long packed)
    {
        return (ChunkPos.getX(packed) << 4) + 8;
    }

    /**
     * The block z of a chunk's centre column.
     *
     * @param packed the packed chunk position.
     * @return the block z.
     */
    private static int centreZ(final long packed)
    {
        return (ChunkPos.getZ(packed) << 4) + 8;
    }
}
