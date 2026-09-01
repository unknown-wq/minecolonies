package com.unknownwq.worldmap.map;

import com.unknownwq.worldmap.WorldMapClient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reads and writes tiles under {@code <gamedir>/worldmap/<world>/<dimension>/<x>_<z>.wmt}.
 *
 * <p>Format: gzip over a tiny header ({@value #MAGIC}, a version, the tile side length) followed by the
 * tile's two planes. Raw rather than PNG, for three reasons that all point the same way. A tile is updated a
 * chunk at a time, so every save is a read-modify-write of arrays that are already in memory -- with PNG
 * those arrays would have to be re-encoded from scratch anyway, so the format buys nothing there. Raw needs
 * no image codec, so a load is a pair of reads straight into an {@code int[]} and a {@code short[]} with no
 * intermediate {@code NativeImage} and no off-heap allocation on the writer thread. And gzip already does
 * the job PNG's filters would: map tiles are enormous runs of identical colour and slowly-varying height,
 * and a typical explored tile compresses from 1.5 MiB to well under 200 KiB.</p>
 *
 * <h2>Versions</h2>
 * <table>
 *   <tr><th>v1</th><td>{@code SIZE * SIZE} little-endian finished ARGB ints. Colour only -- no heights.</td></tr>
 *   <tr><th>v2</th><td>the same ints, then {@code SIZE * SIZE} little-endian shorts of surface y.</td></tr>
 *   <tr><th>v3</th><td>the same shape as v2, but the ints are {@link MapTile}'s base plane -- an unshaded
 *       colour and a kind byte -- rather than a finished pixel. <b>Written by every save now.</b></td></tr>
 * </table>
 *
 * <p>v3 is the same number of bytes as v2 and compresses a little better, because a base plane has fewer
 * distinct values in it than a shaded one does.</p>
 *
 * <p><b>Old tiles are kept, not discarded.</b> A v1 or v2 file is read as what it is: its ints are finished
 * pixels, so they are loaded as {@link MapTile#PRESHADED} columns and drawn exactly as they were drawn
 * before, and a v1 file's heights are every one {@link MapTile#NO_HEIGHT}. Losing a map somebody walked is a
 * worse outcome than a region of it that is a version behind, and the mod already has an honest answer for a
 * column with no height. The version is checked before a single byte of payload is read, and v1's payload is
 * a different length from the other two, which the length check catches independently.</p>
 *
 * <p><b>An old region heals itself as it is walked over.</b> {@link MapService} normally skips any chunk it
 * already holds, but it makes an exception for a chunk that is missing heights or whose colours are
 * pre-shaded -- which is exactly what a v1 or v2 file loads as. Such a chunk is scanned once more, the
 * finished pixels are replaced by the measurements they should always have been, and the tile is saved in
 * v3. So old ground picks up its heights, its water depths and its relief the next time the player is near
 * it, rather than never.</p>
 *
 * <p>The cost is one extra chunk scan -- a few hundred microseconds on the scanner thread -- per chunk of
 * old map, paid once, spread over however long the player takes to revisit that ground, and then never
 * again: a re-scanned chunk has no pre-shaded columns and no missing heights left, so the test passes next
 * time. A roofed dimension is the exception in both directions. There the scan records no heights at all and
 * every column is legitimately pre-shaded, by design -- vanilla draws dirt-and-stone noise instead of the
 * bedrock roof -- so the re-scan rule is not applied to it at all, or every Nether chunk would be re-scanned
 * on every load for ever.</p>
 *
 * <h2>The index</h2>
 * <p>Beside the files themselves this keeps one cheap thing per dimension: the set of tile coordinates that
 * have anything behind them, and the bounding box of that set. It is built once per dimension by listing the
 * directory, and kept current afterwards by {@link #note}, which the scanner calls the moment a chunk is
 * written into a tile -- long before the tile itself reaches the disk.</p>
 *
 * <p>It exists because of how far the map now zooms out. At 1/32 of a pixel per block a 4K window covers
 * roughly a hundred and twenty thousand blocks across, which is tens of thousands of tile squares, and
 * almost every one of them is ground nobody has ever walked. Without the index the screen asks for each of
 * them, and every ask allocates a 1.5 MiB tile and a disk probe for a file that is not there. With it, empty
 * ground is a single set lookup and the cost of a frame follows how much has actually been explored rather
 * than how much is on screen.</p>
 *
 * <p>{@link #load}, {@link #save} and {@link #note} are called from the scanner thread. {@link #has} and
 * {@link #bounds} are called from the render thread, which is why the index is concurrent and why a
 * dimension whose listing has not finished answers "maybe" rather than "no" -- a wrong "no" would hide a
 * tile that is really there, and there is no frame in which that is better than one wasted probe.</p>
 */
public final class TileStore
{
    private static final int MAGIC = 0x574D5431;

    /**
     * Written by every save. See the class notes: {@link #load} accepts the two older layouts as well and
     * converts them on the way in.
     */
    private static final int VERSION = 3;

    /**
     * Finished pixels plus heights, written before the base plane existed.
     */
    private static final int VERSION_SHADED = 2;

    /**
     * Finished pixels and nothing else, written before the height plane existed.
     */
    private static final int VERSION_COLOUR_ONLY = 1;

    private final Path root;

    /**
     * Sanitized dimension name to what is known to exist in it. See the class notes.
     */
    private final Map<String, DimensionIndex> indices = new ConcurrentHashMap<>();

    /**
     * @param root the directory this world's tiles live in, e.g. {@code <gamedir>/worldmap/<world>}.
     */
    public TileStore(final Path root)
    {
        this.root = root;
    }

    /**
     * Builds the index for one dimension if it has not been built already, by listing its directory. Cheap
     * to call repeatedly -- a built index is a single map lookup -- so the scanner simply calls it every
     * time round its loop rather than tracking when the dimension changed.
     *
     * @param dimension the sanitized dimension name, or the empty string for "no world", which does nothing.
     */
    public void index(final String dimension)
    {
        if (dimension.isEmpty() || this.indices.containsKey(dimension))
        {
            return;
        }

        // Published before the listing runs, not after: a save that lands while the directory is being
        // walked must have somewhere to record itself, or its tile would be missing from the index until
        // the next session. Until `ready` is set the index answers "maybe" to everything, so the half-built
        // state is never mistaken for an empty dimension.
        final DimensionIndex index = new DimensionIndex();
        final DimensionIndex existing = this.indices.putIfAbsent(dimension, index);
        if (existing != null)
        {
            return;
        }

        final Path directory = this.root.resolve(dimension);
        if (Files.isDirectory(directory))
        {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.wmt"))
            {
                for (final Path file : stream)
                {
                    addFromName(index, file.getFileName().toString());
                }
            }
            catch (final IOException | RuntimeException e)
            {
                WorldMapClient.LOGGER.warn("Could not index tiles in {}", directory, e);
            }
        }
        index.ready = true;
    }

    /**
     * Records that a tile now has something in it. Called when a chunk is written into a tile rather than
     * when the tile reaches the disk, so ground scanned a moment ago is drawable immediately instead of
     * after the next flush.
     *
     * @param key the tile.
     */
    public void note(final TileKey key)
    {
        final DimensionIndex index = this.indices.get(key.dimension());
        if (index != null)
        {
            index.add(key.x(), key.z());
        }
    }

    /**
     * @param dimension the sanitized dimension name.
     * @param x         tile x.
     * @param z         tile z.
     * @return false only when the index for that dimension has been built and does not have this tile in it.
     *     An unbuilt index answers true, so a tile is never hidden by a listing that has not finished.
     */
    public boolean has(final String dimension, final int x, final int z)
    {
        final DimensionIndex index = this.indices.get(dimension);
        return index == null || !index.ready || index.has(x, z);
    }

    /**
     * @param dimension the sanitized dimension name.
     * @return the smallest tile rectangle containing everything known in that dimension, or null when the
     *     index is not built yet or nothing is in it. The screen intersects its visible rectangle with this,
     *     which is what keeps a zoomed-right-out frame from walking tens of thousands of empty squares.
     */
    public Bounds bounds(final String dimension)
    {
        final DimensionIndex index = this.indices.get(dimension);
        return index == null || !index.ready ? null : index.bounds;
    }

    private static void addFromName(final DimensionIndex index, final String name)
    {
        final int underscore = name.indexOf('_');
        if (underscore <= 0 || !name.endsWith(".wmt"))
        {
            return;
        }
        try
        {
            index.add(Integer.parseInt(name.substring(0, underscore)),
              Integer.parseInt(name.substring(underscore + 1, name.length() - ".wmt".length())));
        }
        catch (final NumberFormatException e)
        {
            // Not one of ours. A stray file in the directory is not an error.
        }
    }

    /**
     * A tile rectangle, inclusive at both ends.
     *
     * @param minX lowest tile x.
     * @param minZ lowest tile z.
     * @param maxX highest tile x.
     * @param maxZ highest tile z.
     */
    public record Bounds(int minX, int minZ, int maxX, int maxZ)
    {
    }

    /**
     * What is known to exist in one dimension.
     *
     * <p>{@link #bounds} is replaced whole rather than mutated in four fields, so a reader never sees a
     * rectangle that is half old and half new -- which could exclude a tile that is really inside both.</p>
     */
    private static final class DimensionIndex
    {
        private final Set<Long> keys = ConcurrentHashMap.newKeySet();

        private volatile Bounds bounds;
        private volatile boolean ready;

        private void add(final int x, final int z)
        {
            if (!this.keys.add((long) x << 32 | z & 0xFFFFFFFFL))
            {
                return;
            }
            synchronized (this)
            {
                final Bounds current = this.bounds;
                this.bounds = current == null
                                ? new Bounds(x, z, x, z)
                                : new Bounds(Math.min(current.minX(), x), Math.min(current.minZ(), z),
                                  Math.max(current.maxX(), x), Math.max(current.maxZ(), z));
            }
        }

        private boolean has(final int x, final int z)
        {
            return this.keys.contains((long) x << 32 | z & 0xFFFFFFFFL);
        }
    }

    /**
     * Loads a tile from disk into the given arrays.
     *
     * @param key     which tile.
     * @param base    the base-plane destination, {@code SIZE * SIZE} long.
     * @param heights the height destination, the same length. Filled with {@link MapTile#NO_HEIGHT} when the
     *                file is an old colour-only one, and left untouched when the load fails.
     * @return true if the file existed and was read whole. On any failure the arrays are left as the caller
     *     passed them and false is returned -- a corrupt tile is treated as an unexplored one and is
     *     overwritten by the next save.
     */
    public boolean load(final TileKey key, final int[] base, final short[] heights)
    {
        final Path file = this.fileFor(key);
        if (!Files.isRegularFile(file))
        {
            return false;
        }

        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(raw), 1 << 16)))
        {
            if (in.readInt() != MAGIC)
            {
                WorldMapClient.LOGGER.warn("Ignoring tile {} -- not a world map tile", file);
                return false;
            }
            final int version = in.readInt();
            if (version != VERSION && version != VERSION_SHADED && version != VERSION_COLOUR_ONLY)
            {
                WorldMapClient.LOGGER.warn("Ignoring tile {} -- format version {} is not one this build reads", file, version);
                return false;
            }
            if (in.readInt() != MapTile.SIZE)
            {
                WorldMapClient.LOGGER.warn("Ignoring tile {} -- wrong tile size", file);
                return false;
            }

            final int colourBytes = base.length * 4;
            final int heightBytes = version == VERSION_COLOUR_ONLY ? 0 : heights.length * 2;

            final byte[] bytes = in.readAllBytes();
            if (bytes.length != colourBytes + heightBytes)
            {
                WorldMapClient.LOGGER.warn("Ignoring tile {} -- v{} expects {} bytes, found {}",
                  file, version, colourBytes + heightBytes, bytes.length);
                return false;
            }

            final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            buffer.asIntBuffer().get(base);
            if (version == VERSION_COLOUR_ONLY)
            {
                Arrays.fill(heights, MapTile.NO_HEIGHT);
            }
            else
            {
                buffer.position(colourBytes).asShortBuffer().get(heights);
            }
            if (version != VERSION)
            {
                adoptFinishedPixels(base);
            }
            this.note(key);
            return true;
        }
        catch (final IOException | RuntimeException e)
        {
            WorldMapClient.LOGGER.warn("Could not read tile {}", file, e);
            return false;
        }
    }

    /**
     * Re-labels a plane of finished pixels as a plane of pre-shaded base colours, in place.
     *
     * <p>The colour is kept exactly as it was: it already carries whichever of vanilla's three brightnesses
     * the old build chose, and there is no way to divide that back out and no reason to try. What changes is
     * that the top byte stops being an opaque alpha and starts saying what the column is, which is what
     * every reader of the plane now expects. An unwritten column is zero in both readings.</p>
     */
    private static void adoptFinishedPixels(final int[] base)
    {
        for (int i = 0; i < base.length; i++)
        {
            if (base[i] != 0)
            {
                base[i] = MapTile.pack(base[i], MapTile.PRESHADED);
            }
        }
    }

    /**
     * Writes a tile. The bytes go to a sibling {@code .tmp} first and are then moved into place, so an
     * interrupted save cannot leave a half-written tile that the next session would read back as garbage.
     *
     * @param key     which tile.
     * @param base    the base plane to write.
     * @param heights the heights to write.
     */
    public void save(final TileKey key, final int[] base, final short[] heights)
    {
        final Path file = this.fileFor(key);
        final Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try
        {
            Files.createDirectories(file.getParent());

            final byte[] bytes = new byte[base.length * 4 + heights.length * 2];
            final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            buffer.asIntBuffer().put(base);
            buffer.position(base.length * 4).asShortBuffer().put(heights);

            try (OutputStream raw = Files.newOutputStream(temp);
                 DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(raw), 1 << 16)))
            {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(MapTile.SIZE);
                out.write(bytes);
            }

            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            this.note(key);
        }
        catch (final IOException | RuntimeException e)
        {
            WorldMapClient.LOGGER.warn("Could not write tile {}", file, e);
            try
            {
                Files.deleteIfExists(temp);
            }
            catch (final IOException ignored)
            {
                // Nothing useful to do about a leftover temp file; the next save overwrites it.
            }
        }
    }

    private Path fileFor(final TileKey key)
    {
        return this.root.resolve(key.dimension()).resolve(key.fileName());
    }
}
