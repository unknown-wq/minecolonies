package com.unknownwq.worldmap.map;

/**
 * One square of map, {@value #SIZE} blocks on a side, holding two planes: one base colour and one surface
 * height per block column.
 *
 * <h2>The base plane</h2>
 * <p>Not the pixel that gets drawn -- the measurement the pixel is made from. The low three bytes are the
 * column's colour before any shading: the block's {@code MapColor}, moved towards the biome's own grass,
 * foliage or water colour where the game itself tints that block. The top byte says what kind of column it
 * is, and that is what {@link MapShading} needs to know to finish the job:</p>
 * <table>
 *   <tr><th>{@value #UNMAPPED}</th><td>never scanned. The screen draws black behind it, so unexplored and
 *       not-in-memory look identical -- intended, and unchanged</td></tr>
 *   <tr><th>{@value #LAND}</th><td>ordinary ground; shade it against its neighbours</td></tr>
 *   <tr><th>{@value #PRESHADED}</th><td>the colour is already finished and must be drawn as it is. Two
 *       things land here: a roofed dimension, where the drawn colour is vanilla's dirt-and-stone noise and
 *       there is no surface to light, and a column read out of a pre-{@code v3} tile file, whose stored
 *       colour already has vanilla's three-step brightness baked into it and would be shaded twice</td></tr>
 *   <tr><th>{@value #WATER} and up</th><td>water, with the fluid's depth in blocks as the excess over
 *       {@value #WATER}, capped at {@value #MAX_WATER_DEPTH}</td></tr>
 * </table>
 *
 * <p>Keeping the depth here rather than throwing it away after the scan is what lets water be a gradient
 * instead of three steps, and lets the relief be taken from the sea floor rather than from a flat sheet of
 * blue. It costs nothing: the top byte of the colour was carrying a single explored/unexplored bit.</p>
 *
 * <p>The height plane is the y of the block whose map colour was taken -- the top of the column, water
 * surface included. It costs nothing to collect, because the scan already walks down from the
 * {@code WORLD_SURFACE} heightmap to find that block, and it is the difference between a map that can report
 * a real Y and one that has to invent one. Its own "no data" value is {@link #NO_HEIGHT} rather than the
 * base plane's kind byte, because the two genuinely disagree: a tile written before the height plane existed
 * has colours and no heights at all, and a roofed dimension has colours that are noise rather than a real
 * surface.</p>
 *
 * <p>A column is written <b>once per plane it is missing</b>, and then only re-written when something asks.
 * There is no block-change listener, no periodic re-scan and no re-scan when a chunk reloads, and
 * {@link #hasChunk} is what {@link MapService} uses to skip a chunk it already holds. Three things get past
 * it. Two are one-off catch-ups for ground mapped by an older build -- a chunk with no heights, and a chunk
 * whose colours are pre-shaded -- which {@link #hasHeights} and {@link #isPreshaded} let through for exactly
 * one re-scan each. The third is opening the map, which re-scans the loaded chunks round the player
 * outright: see {@code MapService#refreshAroundPlayer}. The only mutable state left is {@link #dirty}, which
 * is a disk-write flag, not a staleness flag.</p>
 *
 * <p>Threading: the scanner thread writes columns, the render thread reads them, and neither locks. That is
 * safe here because a base write is a single {@code int} store and a height write a single {@code short}
 * store -- the JLS guarantees neither is torn -- and a reader that misses the newest write simply draws the
 * tile one revision late and picks it up next frame. {@link #revision} is volatile so the render thread does
 * eventually see that something changed.</p>
 */
public final class MapTile
{
    /**
     * Side length in blocks, and in pixels. 512 makes the colour plane exactly 1 MiB of {@code int[]} and
     * the height plane exactly 512 KiB of {@code short[]}, so a resident tile is 1.5 MiB and a GPU texture
     * -- colour only -- is 1 MiB. Both cache caps are counted in tiles, not bytes.
     */
    public static final int SIZE = 512;

    /**
     * The value a height plane entry carries when the column's surface y is not known: either it was never
     * scanned, or it came off disk in the colour-only v1 tile format, or it is in a roofed dimension where
     * the drawn colour is noise and there is no surface to report.
     */
    public static final short NO_HEIGHT = Short.MIN_VALUE;

    /**
     * Kind byte: this column has never been scanned. Zero, so an all-zero base plane is an empty tile and
     * {@link #hasChunk} stays a single comparison.
     */
    public static final int UNMAPPED = 0;

    /**
     * Kind byte: ordinary ground, to be shaded against its neighbours.
     */
    public static final int LAND = 1;

    /**
     * Kind byte: the colour is finished and is to be drawn exactly as stored.
     */
    public static final int PRESHADED = 2;

    /**
     * Kind byte for water of depth zero; deeper water counts up from here.
     */
    public static final int WATER = 3;

    /**
     * The deepest water the kind byte can express. Past this the column is recorded at the cap, which is
     * well beyond the depth at which the colour ramp has stopped changing anyway.
     */
    public static final int MAX_WATER_DEPTH = 255 - WATER;

    /**
     * Hands every tile object a starting revision of its own.
     *
     * <p>A tile is evicted from the cache and loaded again whenever the map is zoomed far enough out that
     * the view holds more of them than the cache does, and the object that comes back is a different one.
     * If both started at zero the texture cache would compare the new tile's revision against the one it
     * uploaded from the old tile, find them equal, and keep showing pixels from before -- for ever, since
     * nothing else would ever make them differ. A counter shared by every tile makes "same revision" mean
     * "same tile object, unchanged", which is what the comparison is asking.</p>
     */
    private static final java.util.concurrent.atomic.AtomicInteger REVISIONS = new java.util.concurrent.atomic.AtomicInteger();

    private final TileKey key;
    private final int[] base = new int[SIZE * SIZE];
    private final short[] heights = new short[SIZE * SIZE];

    private volatile int revision = REVISIONS.incrementAndGet();
    private volatile boolean dirty;
    private volatile long dirtySince;
    private volatile long lastUsed = System.nanoTime();

    /**
     * @param key the tile this is.
     */
    public MapTile(final TileKey key)
    {
        this.key = key;
        java.util.Arrays.fill(this.heights, NO_HEIGHT);
    }

    /**
     * @return the tile's identity.
     */
    public TileKey key()
    {
        return this.key;
    }

    /**
     * @return the backing base-colour array, row-major, {@code z * SIZE + x} with both relative to the tile
     *     origin. See the class notes for the packing. Callers must not resize or replace it.
     */
    public int[] base()
    {
        return this.base;
    }

    /**
     * Packs one column of a scan.
     *
     * @param rgb  the unshaded colour, low three bytes.
     * @param kind {@link #LAND}, {@link #PRESHADED}, or {@link #WATER} plus a depth.
     * @return the value to store in the base plane.
     */
    public static int pack(final int rgb, final int kind)
    {
        return kind << 24 | rgb & 0xFFFFFF;
    }

    /**
     * @param depth fluid depth in blocks.
     * @return the kind byte for water that deep.
     */
    public static int waterKind(final int depth)
    {
        return WATER + Math.min(MAX_WATER_DEPTH, Math.max(0, depth));
    }

    /**
     * @return the backing height array, indexed exactly like {@link #base()}. Entries are
     *     {@link #NO_HEIGHT} where the surface y is not known. Callers must not resize or replace it.
     */
    public short[] heights()
    {
        return this.heights;
    }

    /**
     * The surface y of one column.
     *
     * @param blockX world x.
     * @param blockZ world z.
     * @return the y of the block whose map colour this tile drew, or {@link #NO_HEIGHT} if that is not
     *     known. Reading a column of a different tile is not checked for: callers pick the tile first.
     */
    public short heightAt(final int blockX, final int blockZ)
    {
        return this.heights[Math.floorMod(blockZ, SIZE) * SIZE + Math.floorMod(blockX, SIZE)];
    }

    /**
     * @return a counter bumped on every batch of pixel writes. The texture cache compares it against the
     *     revision it last uploaded to decide whether a re-upload is needed.
     */
    public int revision()
    {
        return this.revision;
    }

    /**
     * @return {@link System#nanoTime()} of the last draw or write. Drives eviction, which is why the render
     *     thread touches it too.
     */
    public long lastUsed()
    {
        return this.lastUsed;
    }

    /**
     * Records that the tile is still wanted.
     */
    public void touch()
    {
        this.lastUsed = System.nanoTime();
    }

    /**
     * @return true if there are pixel changes not yet written to disk.
     */
    public boolean isDirty()
    {
        return this.dirty;
    }

    /**
     * @return {@link System#currentTimeMillis()} at the moment this tile first became dirty; meaningless when
     *     it is clean.
     */
    public long dirtySince()
    {
        return this.dirtySince;
    }

    /**
     * Do we already hold this chunk?
     *
     * <p>Scanning a chunk always writes at least one column unless every column in it is empty, so a
     * single unmapped-column search answers the question without any extra bookkeeping, and it answers it
     * across restarts too -- a tile loaded from disk knows what it holds. The one case it gets wrong is a
     * chunk that is genuinely nothing but air and void, which is re-scanned each time it loads; that costs
     * 256 heightmap lookups and produces the same 256 unmapped columns, so it is left alone.</p>
     *
     * @param blockX world x of the chunk's north-west column.
     * @param blockZ world z of the chunk's north-west column.
     * @return true if any column of that chunk has already been written.
     */
    public boolean hasChunk(final int blockX, final int blockZ)
    {
        final int baseX = Math.floorMod(blockX, SIZE);
        final int baseZ = Math.floorMod(blockZ, SIZE);
        for (int dz = 0; dz < 16; dz++)
        {
            final int rowStart = (baseZ + dz) * SIZE + baseX;
            for (int dx = 0; dx < 16; dx++)
            {
                if (this.base[rowStart + dx] != UNMAPPED)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Does this chunk carry any surface heights?
     *
     * <p>The companion to {@link #hasChunk}, and the reason a colour-only tile off disk does not stay
     * height-less for ever: a chunk with colours but no heights predates the height plane, and
     * {@link MapService} re-scans it once to fill it in.</p>
     *
     * <p><b>Why one column is enough.</b> In a dimension without a ceiling {@link ColumnScanner#scan} writes
     * a height for every one of the 256 columns and cannot write {@link #NO_HEIGHT} for any of them: it
     * clamps the value to {@code Short.MIN_VALUE + 1} at the bottom, and the void and bedrock branch reaches
     * that clamp with a real y like every other branch does. So a chunk that comes back from a re-scan
     * always answers true here, and cannot be re-scanned a second time. In a roofed dimension the reverse
     * holds -- the scan writes no heights at all, on purpose -- which is why {@link MapService} does not ask
     * this question there; if it did, every Nether chunk would re-scan on every load, for ever.</p>
     *
     * @param blockX world x of the chunk's north-west column.
     * @param blockZ world z of the chunk's north-west column.
     * @return true if any column of that chunk has a height other than {@link #NO_HEIGHT}.
     */
    public boolean hasHeights(final int blockX, final int blockZ)
    {
        final int baseX = Math.floorMod(blockX, SIZE);
        final int baseZ = Math.floorMod(blockZ, SIZE);
        for (int dz = 0; dz < 16; dz++)
        {
            final int rowStart = (baseZ + dz) * SIZE + baseX;
            for (int dx = 0; dx < 16; dx++)
            {
                if (this.heights[rowStart + dx] != NO_HEIGHT)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Were this chunk's colours finished by an older build?
     *
     * <p>A tile file written before the base plane existed holds pixels with vanilla's three brightnesses
     * already multiplied into them. They are loaded as {@link #PRESHADED} and drawn exactly as they were,
     * because shading them again would apply two reliefs to the same ground. That is honest but it is not
     * good, so {@link MapService} lets such a chunk through for one more scan -- which replaces the finished
     * pixels with the measurements they should have been, and cannot happen twice, because a re-scanned
     * chunk has no pre-shaded columns left.</p>
     *
     * <p>Asked only in dimensions without a ceiling. In a roofed one every column is legitimately
     * {@link #PRESHADED} -- the drawn colour there is noise, not a lit surface -- and asking would re-scan
     * the Nether for ever.</p>
     *
     * @param blockX world x of the chunk's north-west column.
     * @param blockZ world z of the chunk's north-west column.
     * @return true if any column of that chunk carries a colour that must be drawn unshaded.
     */
    public boolean isPreshaded(final int blockX, final int blockZ)
    {
        final int baseX = Math.floorMod(blockX, SIZE);
        final int baseZ = Math.floorMod(blockZ, SIZE);
        for (int dz = 0; dz < 16; dz++)
        {
            final int rowStart = (baseZ + dz) * SIZE + baseX;
            for (int dx = 0; dx < 16; dx++)
            {
                if (this.base[rowStart + dx] >>> 24 == PRESHADED)
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Copies one scanned chunk -- 16x16 ARGB, row-major by z -- into this tile.
     *
     * @param blockX world x of the chunk's north-west column.
     * @param blockZ world z of the chunk's north-west column.
     * @param colours 256 packed base values.
     * @param columnHeights 256 surface heights, laid out the same way; {@link #NO_HEIGHT} where unknown.
     */
    public void writeChunk(final int blockX, final int blockZ, final int[] colours, final short[] columnHeights)
    {
        final int baseX = Math.floorMod(blockX, SIZE);
        final int baseZ = Math.floorMod(blockZ, SIZE);
        for (int dz = 0; dz < 16; dz++)
        {
            System.arraycopy(colours, dz * 16, this.base, (baseZ + dz) * SIZE + baseX, 16);
            System.arraycopy(columnHeights, dz * 16, this.heights, (baseZ + dz) * SIZE + baseX, 16);
        }

        this.lastUsed = System.nanoTime();
        this.revision++;
        if (!this.dirty)
        {
            this.dirtySince = System.currentTimeMillis();
            this.dirty = true;
        }
    }

    /**
     * Marks the tile as saved. Racy on purpose: if the scanner writes a chunk between the writer copying the
     * array and this call, the tile is left clean with one unsaved chunk, which the next visit to that chunk
     * fixes. The alternative is holding a lock across a disk write.
     */
    public void clearDirty()
    {
        this.dirty = false;
    }
}
