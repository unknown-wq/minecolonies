package com.unknownwq.worldmap.colony;

import net.minecraft.world.level.ChunkPos;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Turns a set of claimed chunks into the boundary of the region they cover.
 *
 * <h2>Why not a box per chunk, and why not a hull</h2>
 * <p>Two obvious approaches are both wrong, and the MineColonies JourneyMap integration
 * ({@code core/compatibility/journeymap/ColonyBorderMapping.java}) says so by construction. Stroking a
 * rectangle around every claimed chunk fills the interior of a colony with a grid of seams and buries the
 * actual border in them. Taking the outer contour and drawing that draws a lie: a colony's claim is
 * <b>not necessarily simply connected</b> -- a chunk in the middle can belong to a neighbour or to nobody --
 * and JourneyMap's own type for this is {@code MapPolygonWithHoles}, hull plus holes, precisely because a
 * single ring cannot describe it.</p>
 *
 * <p>What this does instead is keep every grid edge that has a claimed chunk on exactly one side. That set
 * <em>is</em> the boundary, outer ring and every hole together, and it needs no ring-chaining step and no
 * decision about which winding a ring has: an edge shared by two claimed chunks is interior and drops out,
 * an edge on the rim of the region or on the rim of a hole in it survives. The renderer strokes the edges
 * one by one, which draws the same picture a set of closed polygons would, and the ambiguous case that
 * breaks contour tracing -- four chunks meeting at a corner, two claimed diagonally -- has no ambiguity
 * here at all, because nothing has to be chained through that corner.</p>
 *
 * <p>The cost is that a shape has no polygon to fill. That is why the fill is drawn per chunk instead, from
 * the same chunk set: adjacent rectangles, never overlapping, so a translucent fill comes out one even tone
 * with no double-blended seams down the middle.</p>
 *
 * <p>Pure arithmetic on {@link ChunkPos}, deliberately free of any MineColonies type, so the whole thing can
 * be reasoned about (and would be testable) without the mod installed.</p>
 */
public final class ChunkOutline
{
    /**
     * Chunk claims are per chunk, so the finest border this can draw is a chunk edge. MineColonies can in
     * fact claim part of a chunk -- {@code IChunkClaimData#hasPartialClaim()} and
     * {@code isColumnClaimed(BlockPos)} exist for borders redrawn with the border scepter -- and that
     * detail is not represented here: a partially claimed chunk is drawn whole. Doing better means running
     * this on a 16x finer grid, which is a different amount of work for a case most colonies never hit.
     */
    public static final int CHUNK_BLOCKS = 16;

    /**
     * Builds the boundary of a chunk set.
     *
     * @param chunks the claimed chunks.
     * @return four ints per edge -- {@code x0, z0, x1, z1} in block coordinates, each edge exactly
     *     {@value #CHUNK_BLOCKS} blocks long and axis aligned. Empty when the set is empty.
     */
    public static int[] edges(final Collection<ChunkPos> chunks)
    {
        if (chunks.isEmpty())
        {
            return new int[0];
        }

        final Set<Long> claimed = HashSet.newHashSet(chunks.size());
        for (final ChunkPos pos : chunks)
        {
            claimed.add(pack(pos.x(), pos.z()));
        }

        // At most four edges per chunk, and in practice far fewer, since every shared edge cancels.
        final int[] out = new int[chunks.size() * 16];
        int n = 0;

        for (final ChunkPos pos : chunks)
        {
            final int x = pos.x();
            final int z = pos.z();
            final int bx = x * CHUNK_BLOCKS;
            final int bz = z * CHUNK_BLOCKS;

            if (!claimed.contains(pack(x, z - 1)))
            {
                out[n++] = bx;
                out[n++] = bz;
                out[n++] = bx + CHUNK_BLOCKS;
                out[n++] = bz;
            }
            if (!claimed.contains(pack(x, z + 1)))
            {
                out[n++] = bx;
                out[n++] = bz + CHUNK_BLOCKS;
                out[n++] = bx + CHUNK_BLOCKS;
                out[n++] = bz + CHUNK_BLOCKS;
            }
            if (!claimed.contains(pack(x - 1, z)))
            {
                out[n++] = bx;
                out[n++] = bz;
                out[n++] = bx;
                out[n++] = bz + CHUNK_BLOCKS;
            }
            if (!claimed.contains(pack(x + 1, z)))
            {
                out[n++] = bx + CHUNK_BLOCKS;
                out[n++] = bz;
                out[n++] = bx + CHUNK_BLOCKS;
                out[n++] = bz + CHUNK_BLOCKS;
            }
        }

        return n == out.length ? out : java.util.Arrays.copyOf(out, n);
    }

    /**
     * Packs a chunk set for a snapshot.
     *
     * @param chunks the chunks.
     * @return one long per chunk, {@code x} in the high half and {@code z} in the low half.
     */
    public static long[] pack(final Collection<ChunkPos> chunks)
    {
        final long[] out = new long[chunks.size()];
        int i = 0;
        for (final ChunkPos pos : chunks)
        {
            out[i++] = pack(pos.x(), pos.z());
        }
        return out;
    }

    /**
     * @param packed a value from {@link #pack(Collection)}.
     * @return its chunk x.
     */
    public static int unpackX(final long packed)
    {
        return (int) (packed >> 32);
    }

    /**
     * @param packed a value from {@link #pack(Collection)}.
     * @return its chunk z.
     */
    public static int unpackZ(final long packed)
    {
        return (int) packed;
    }

    private static long pack(final int x, final int z)
    {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private ChunkOutline()
    {
        /*
         * Intentionally left empty.
         */
    }
}
