package com.minecolonies.core.colony.territory;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.colony.territory.HostileTerritory;
import com.minecolonies.api.colony.territory.HostileTerritoryMap;
import com.minecolonies.api.util.WorldUtil;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Works out one stretch of border for guards to walk, as an ordered line of waypoints.
 *
 * <h2>What a "stretch" is</h2>
 * A border is a line, not an area, and a colony's guards cannot walk all of it, nor should they try: a colony with a
 * huge perimeter would otherwise send every guard on a hike. A stretch is the piece of that line <em>nearest the
 * building that ordered the patrol</em>, grown outwards from that nearest point in both directions until it is
 * roughly {@link Plan#targetLength()} blocks long, which is {@link Mode#lengthPerPatroller()} for each guard sharing
 * it. Everything the patrol ever does happens on that line;
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
 *       colony owns it and a neighbour of it does not. A claim is not necessarily one blob: chunks bought at a
 *       distance, and chunks lost in the middle of one, leave a colony owning several regions that do not touch. Only
 *       the region the asking building stands in is considered, so an exclave's edge is never handed to a guard who
 *       would have to cross somebody else's ground to reach it — see {@link #restrictToComponent}.</li>
 * </ul>
 *
 * <h2>Cost</h2>
 * One call scans a fixed {@code (2*(R+1)+1)²} box of chunks, which is 1225 probes at the radius below, and then walks
 * at most a few dozen of them. In {@link Mode#ENEMY} a probe is one {@code long} hash lookup into the immutable
 * territory index; in {@link Mode#COLONY} it is one {@link ChunkPos} and one lookup into the colony manager's claim
 * map, which is {@code getOrDefault} and so leaves no claim record behind for a chunk nobody has touched. Nothing
 * here loads a chunk or touches the world.
 * <p>
 * {@link Mode#COLONY} adds one flood fill over the same box before the border is marked — two more arrays of
 * 1225 entries and at most that many pushes, with no allocation per cell.
 * <p>
 * This is <b>not</b> per tick work. The asking building caches the result and only asks again when the territory
 * index has actually been rebuilt, the set of guards sharing the line has changed, or the cache has aged out — see
 * {@code BuildingBarracks#borderPlan} and {@code BuildingStable#borderPlan}.
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
     * chunks is 256 blocks, which comfortably holds the longest line any real garrison asks for, and is well inside
     * the 2000-block ceiling at which the navigator refuses a walk order outright.
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
     * Hard ceiling on the length of a stretch, whatever the mode and the patroller count asked for.
     * <p>
     * 1024 blocks is 64 chunks of line. A stretch is now budgeted per patroller (see
     * {@link Mode#lengthPerPatroller()}), so this is the guard against a building that somehow reports an absurd
     * number of them rather than a limit any real garrison meets: four barracks towers ask for 512 and a five man
     * stable for 640.
     */
    private static final int MAX_STRETCH_BLOCKS = 1024;

    /**
     * The eight neighbour offsets, edge-sharing first so a straight line is preferred to a corner cut.
     */
    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /**
     * The four edge-sharing neighbour offsets, which is what "connected" means for a claim a citizen has to walk.
     */
    private static final int[][] ORTHOGONAL_NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

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
        OFF(0),

        /**
         * Walk your own side of the nearest hostile territory's edge.
         * <p>
         * Nothing selects this at the moment. It was reachable through a barracks-level setting that has been
         * replaced by the {@code PATROL_BORDER} guard task, and a guard task names one border rather than a choice
         * of them, so the enemy line has no way in until something offers one. The geometry that finds it is kept
         * and still tested by {@link #findStretch}; only the switch is gone.
         */
        ENEMY(128),

        /**
         * Walk the edge of your own claim.
         */
        COLONY(128);

        /**
         * How many blocks of border one patroller is given.
         */
        private final int lengthPerPatroller;

        Mode(final int lengthPerPatroller)
        {
            this.lengthPerPatroller = lengthPerPatroller;
        }

        /**
         * How much border one guard is asked to walk.
         * <p>
         * 128 blocks is eight chunks, which is a beat a unit can walk end to end and back inside a sortie, and short
         * enough that a colony with a huge perimeter does not send anybody on a hike. The whole line is this times
         * the number of patrollers sharing it, so each of them ends up with about this much after
         * {@code sliceBorderPlan} cuts it: a bigger garrison covers more frontier rather than walking the same
         * frontier in a tighter crowd.
         *
         * @return the per patroller budget in blocks.
         */
        public int lengthPerPatroller()
        {
            return lengthPerPatroller;
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
     * @param targetLength how many blocks of border were asked for, i.e. the per patroller budget times the number
     *                     of them, capped. Reported by {@code /mc colony diagnose} so the length a patrol was cut to
     *                     can be seen next to the length it came out at.
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
     * @param colony     the colony asking, whose dimension is searched and whose claim {@link Mode#COLONY} follows.
     * @param anchor     the position the stretch should be nearest to, i.e. the barracks.
     * @param mode       which line to follow.
     * @param patrollers how many guards will share the line, which is what its length is budgeted from. Zero is
     *                   treated as one, so a building with nobody posted still reports a line for the diagnose
     *                   report rather than an empty plan that reads as "no border out there".
     * @return the plan, which may hold no waypoints — check {@link Plan#isUsable()}.
     */
    @NotNull
    public static Plan findStretch(
      @NotNull final IColony colony,
      @NotNull final BlockPos anchor,
      @NotNull final Mode mode,
      final int patrollers)
    {
        if (mode == Mode.OFF)
        {
            return new Plan(mode, List.of(), Failure.NONE, 0);
        }

        final int targetLength = Math.min(mode.lengthPerPatroller() * Math.max(1, patrollers), MAX_STRETCH_BLOCKS);

        final HostileTerritoryMap territory = HostileTerritory.in(colony.getDimension());
        if (mode == Mode.ENEMY && territory == null)
        {
            // The overwhelmingly common case, and the cheapest possible answer to it: one map lookup and out. A colony
            // in a world where nobody has ever painted a territory never reaches the chunk scan below.
            return new Plan(mode, List.of(), Failure.NO_TERRITORY_AT_ALL, targetLength);
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

        final List<BlockPos> line = trace(inside, side, originX, originZ, anchor, mode, targetLength);
        if (line.isEmpty())
        {
            return new Plan(mode,
              List.of(),
              mode == Mode.ENEMY ? Failure.NO_TERRITORY_IN_RANGE : Failure.NO_COLONY_BORDER,
              targetLength);
        }

        return new Plan(mode, line, Failure.NONE, targetLength);
    }

    /**
     * Turn a bitmap of what counts as inside into the ordered line to walk.
     * <p>
     * Split out of {@link #findStretch} because everything above it is world access and everything in it is geometry:
     * the two answer to completely different failure modes, and only the geometry is worth reading twice.
     *
     * @param inside  the bitmap, one cell per chunk, which this may edit.
     * @param side    the width and height of the bitmap.
     * @param originX chunk x of the bitmap's first column.
     * @param originZ chunk z of the bitmap's first row.
     * @param anchor       the position the stretch should be nearest to, which is the centre cell of the bitmap.
     * @param mode         which line to follow.
     * @param targetLength how many blocks of line to build.
     * @return the ordered waypoints, empty when there is no such line in the box.
     */
    @NotNull
    private static List<BlockPos> trace(
      @NotNull final boolean[] inside,
      final int side,
      final int originX,
      final int originZ,
      @NotNull final BlockPos anchor,
      @NotNull final Mode mode,
      final int targetLength)
    {
        if (mode == Mode.COLONY)
        {
            // Only the claim region the anchor is standing in. Done here rather than inside the ownership probe
            // because the probe answers one chunk at a time and connectedness is a property of the whole bitmap.
            restrictToComponent(inside, side, side / 2, side / 2);
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

        return border.isEmpty() ? List.of() : walkLine(border, anchor, targetLength);
    }

    /**
     * Cut a claim bitmap down to the one connected region a given cell belongs to.
     * <p>
     * Four-connected on purpose: two blobs that meet only at a corner are two places to a citizen, who cannot walk a
     * chunk diagonal that has unclaimed ground on both sides of it any more easily than he can walk to an island.
     * Treating them as one region is what let a stretch grown from a seed in the first blob step across the corner
     * and carry on around the second, which is the shape of "the patrol teleport-hopped between islands".
     * <p>
     * A cell that is not itself inside leaves the bitmap alone. That is the building standing on ground its colony
     * does not own — a hut just outside the claim, or a claim map that has not caught up — and the honest answer
     * there is the whole box, which is the line this returned before there was any of this.
     *
     * @param inside the bitmap, edited in place.
     * @param side   the width and height of the bitmap.
     * @param cellX  x of the cell whose region is kept.
     * @param cellZ  z of the cell whose region is kept.
     */
    private static void restrictToComponent(@NotNull final boolean[] inside, final int side, final int cellX, final int cellZ)
    {
        final int start = cellZ * side + cellX;
        if (!inside[start])
        {
            return;
        }

        final boolean[] reached = new boolean[inside.length];
        final int[] stack = new int[inside.length];
        int top = 0;
        stack[top++] = start;
        reached[start] = true;

        while (top > 0)
        {
            final int current = stack[--top];
            final int x = current % side;
            final int z = current / side;

            for (final int[] offset : ORTHOGONAL_NEIGHBOURS)
            {
                final int nx = x + offset[0];
                final int nz = z + offset[1];
                if (nx < 0 || nz < 0 || nx >= side || nz >= side)
                {
                    continue;
                }

                final int next = nz * side + nx;
                if (inside[next] && !reached[next])
                {
                    reached[next] = true;
                    stack[top++] = next;
                }
            }
        }

        for (int i = 0; i < inside.length; i++)
        {
            inside[i] &= reached[i];
        }
    }

    /**
     * The index of the waypoint nearest a position.
     *
     * @param line the waypoints, in order.
     * @param pos  the position.
     * @return the index, 0 for an empty line.
     */
    public static int nearestIndex(@NotNull final List<BlockPos> line, @NotNull final BlockPos pos)
    {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < line.size(); i++)
        {
            final long dx = (long) line.get(i).getX() - pos.getX();
            final long dz = (long) line.get(i).getZ() - pos.getZ();
            final long distance = dx * dx + dz * dz;
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /**
     * Put a waypoint on the ground, or on the water, of a chunk that is actually loaded.
     * <p>
     * A plan stores waypoints with the asking building's own Y because working one out needs the world and the plan
     * is built without touching it. Resolving it here costs a heightmap read of a loaded chunk and is skipped
     * entirely for one that is not: {@code AbstractEntityAIGuard#patrol} treats an unloaded patrol point as already
     * arrived at and moves on, which is exactly the right thing for a stretch running out into ground nobody is
     * standing near.
     * <p>
     * {@code MOTION_BLOCKING_NO_LEAVES} counts fluids, so over sea this answers the water surface rather than the sea
     * bed. That is what a guard crossing it wants: the navigator spawns a {@code MinecoloniesBoat} when a path runs
     * over water and it has the Boats research, and a target on the surface is one it can sail to.
     * <p>
     * It is the no-leaves heightmap and not plain {@code MOTION_BLOCKING} because leaves block motion too, so in a
     * forest the plain one answers the top of the canopy. A waypoint eight or ten blocks up a tree is one no guard
     * ever reaches: arrival is {@code BlockPosUtil.dist(where he is, the waypoint) <= 4} measured in three
     * dimensions, and the path job will not call a node at the foot of the tree the destination either, so the walk
     * order is reissued for as long as the leg lasts. The waypoint belongs on the ground under the canopy.
     *
     * @param world    the level, may be null.
     * @param waypoint the waypoint.
     * @return the same column, on the surface where that is known.
     */
    @NotNull
    public static BlockPos surface(@Nullable final Level world, @NotNull final BlockPos waypoint)
    {
        if (world == null || !WorldUtil.isBlockLoaded(world, waypoint))
        {
            return waypoint;
        }
        return new BlockPos(waypoint.getX(),
          world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, waypoint.getX(), waypoint.getZ()),
          waypoint.getZ());
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
