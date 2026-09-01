package com.unknownwq.worldmap.colony;

import com.unknownwq.worldmap.WorldMapClient;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reads and writes the remembered colony list, at
 * {@code <gamedir>/worldmap/<world>/<dimension>/colonies.wmc}.
 *
 * <p>Same directory, same world-key scheme and the same write discipline as {@code TileStore}: gzip over a
 * twelve-byte header, and a save that goes to a sibling {@code .tmp} and is then moved into place, so an
 * interrupted write cannot leave a file the next session reads back as garbage. There is deliberately not a
 * second convention here -- a player who deletes {@code worldmap/<world>} to start the map over has always
 * deleted everything the map knows about that world, and that stays true.</p>
 *
 * <h2>Why this file exists at all</h2>
 * <p>The client's claim map is filled in by {@code ColonyView} packets, and the server sends those to a
 * player standing on the colony's claimed chunks (plus, once, at login, to a player who is a manager of the
 * colony). So the borders on this map survive walking away -- the overlay accumulates within a session --
 * but they do not survive quitting: on the next login the client knows about the colonies it manages and
 * nothing else, and everybody else's borders come back only by walking into them again. That is the exact
 * opposite of what the rest of this map promises, which is that it remembers where you have been.</p>
 *
 * <h2>Format</h2>
 * <p>Not raw arrays, unlike a tile: this is a handful of variable-length records rather than two fixed
 * planes, so it is written field by field through {@link DataOutputStream}. Strings are its modified UTF-8,
 * positions are three ints, and a null position is a single {@code false}.</p>
 *
 * <table>
 *   <tr><th>v1</th><td>colony count, then per colony: id, name, citizens, colour, own, hostile, centre,
 *   chunk count and packed chunks, last-seen millis, then the hut, field, patrol and raider-spawn
 *   lists.</td></tr>
 *   <tr><th>v2</th><td>the same, plus the game time of the raid the spawn points came out of, after the
 *   spawn list. <b>Written by every save now.</b></td></tr>
 * </table>
 *
 * <p>A v1 file is still read, and its colonies come back with {@link ColonyMemory#NO_RAID_TIME}: the points are
 * where they always were and only their age is unknown, which the map says out loud rather than guessing at. It
 * is rewritten as v2 on the next save, so a colony picks its date up again the first time it is seen live.</p>
 *
 * <p>A file whose magic, version or size checks fail is ignored whole rather than partly read. There is one
 * of these per dimension and it is small, so nothing is gained by salvaging half of it, and a half-read
 * colony list would draw borders that were never there.</p>
 *
 * <h2>Threads</h2>
 * <p>{@link #load} runs on the client thread, once per world or dimension change: the file is a few tens of
 * kilobytes and reading it where the caller can use the result immediately is simpler than a callback.
 * {@link #save} hands an already-immutable list to a single daemon writer thread and returns, because a
 * populated server can put a few hundred thousand claimed chunks in this file and gzipping that on the
 * client thread would be a visible hitch every time it was flushed.</p>
 */
@Environment(EnvType.CLIENT)
public final class ColonyStore
{
    /**
     * {@code WMC1}, the same shape of marker as the tile format's {@code WMT1}.
     */
    private static final int MAGIC = 0x574D4331;

    private static final int VERSION = 2;

    /**
     * The oldest version this build still reads. See the format table above for what a v1 file is missing.
     */
    private static final int MIN_VERSION = 1;

    /**
     * The file name inside {@code <world>/<dimension>/}.
     */
    private static final String FILE_NAME = "colonies.wmc";

    /**
     * Refused on read. Nothing sane produces more than a few thousand colonies in one dimension, and the
     * counts in this file are used to size arrays, so they are bounded before they are believed.
     */
    private static final int MAX_COLONIES = 20_000;

    private static final int MAX_CHUNKS = 500_000;

    private static final int MAX_LIST = 100_000;

    /**
     * Pending writes. Bounded and small: a save that arrives while three are already queued is dropped,
     * because each one is a complete copy of the same dimension's list and the newest is the only one worth
     * having. The map is flushed again on the next change and on world exit.
     */
    private final ArrayBlockingQueue<Write> writes = new ArrayBlockingQueue<>(4);

    private final Path root;

    private volatile Thread writer;

    /**
     * @param root the directory this world's map lives in, e.g. {@code <gamedir>/worldmap/sp.myworld}.
     */
    public ColonyStore(final Path root)
    {
        this.root = root;
    }

    /**
     * Reads one dimension's remembered colonies.
     *
     * @param dimension the sanitized dimension name, as {@code TileKey.dimensionName} produces it.
     * @return what was on disk, or an empty list when there is no file or it cannot be read whole.
     */
    public List<ColonyMemory> load(final String dimension)
    {
        final Path file = this.fileFor(dimension);
        if (!Files.isRegularFile(file))
        {
            return List.of();
        }

        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(raw), 1 << 16)))
        {
            if (in.readInt() != MAGIC)
            {
                WorldMapClient.LOGGER.warn("Ignoring {} -- not a world map colony file", file);
                return List.of();
            }
            final int version = in.readInt();
            if (version < MIN_VERSION || version > VERSION)
            {
                WorldMapClient.LOGGER.warn("Ignoring {} -- format version {} is not one this build reads", file, version);
                return List.of();
            }

            final int count = in.readInt();
            if (count < 0 || count > MAX_COLONIES)
            {
                WorldMapClient.LOGGER.warn("Ignoring {} -- {} colonies is not a believable count", file, count);
                return List.of();
            }

            final List<ColonyMemory> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++)
            {
                out.add(readColony(in, version));
            }
            return List.copyOf(out);
        }
        catch (final IOException | RuntimeException e)
        {
            WorldMapClient.LOGGER.warn("Could not read remembered colonies from {}", file, e);
            return List.of();
        }
    }

    /**
     * Queues one dimension's remembered colonies to be written.
     *
     * @param dimension the sanitized dimension name.
     * @param colonies  the list; must already be immutable, because the writer thread reads it later.
     */
    public void save(final String dimension, final List<ColonyMemory> colonies)
    {
        this.startWriter();
        if (!this.writes.offer(new Write(dimension, colonies)))
        {
            // Three complete copies of this list are already waiting. Dropping the fourth loses nothing the
            // next flush will not carry, and the alternative is an unbounded queue of stale duplicates.
            WorldMapClient.LOGGER.debug("Colony save for {} dropped -- the writer is behind", dimension);
        }
    }

    /**
     * Writes anything still queued and stops the writer thread. Called when the client shuts down.
     */
    public synchronized void flush()
    {
        // Synchronized on the same monitor as write(), so this first waits for a write already in progress
        // and only then drains what is left, on this thread. Draining here rather than waiting on the writer
        // is what makes it safe at shutdown, where the writer is a daemon thread that may not get another
        // slice before the JVM exits.
        Write pending;
        while ((pending = this.writes.poll()) != null)
        {
            this.write(pending);
        }
    }

    private synchronized void startWriter()
    {
        if (this.writer != null)
        {
            return;
        }
        final Thread thread = new Thread(this::runWriter, "worldmap-colony-writer");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        this.writer = thread;
        thread.start();
    }

    private void runWriter()
    {
        while (true)
        {
            try
            {
                this.write(this.writes.take());
            }
            catch (final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
            catch (final RuntimeException e)
            {
                WorldMapClient.LOGGER.warn("Colony writer recovered from an error", e);
            }
        }
    }

    private synchronized void write(final Write request)
    {
        final Path file = this.fileFor(request.dimension());
        final Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try
        {
            Files.createDirectories(file.getParent());

            try (OutputStream raw = Files.newOutputStream(temp);
                 DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(raw), 1 << 16)))
            {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(request.colonies().size());
                for (final ColonyMemory colony : request.colonies())
                {
                    writeColony(out, colony);
                }
            }

            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final IOException | RuntimeException e)
        {
            WorldMapClient.LOGGER.warn("Could not write remembered colonies to {}", file, e);
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

    private static void writeColony(final DataOutputStream out, final ColonyMemory colony) throws IOException
    {
        out.writeInt(colony.id());
        out.writeUTF(colony.name());
        out.writeInt(colony.citizens());
        out.writeInt(colony.colour());
        out.writeBoolean(colony.own());
        out.writeBoolean(colony.hostile());
        writePos(out, colony.centre());
        out.writeLong(colony.lastSeen());

        out.writeInt(colony.chunks().length);
        for (final long packed : colony.chunks())
        {
            out.writeLong(packed);
        }

        out.writeInt(colony.huts().size());
        for (final ColonyMemory.Hut hut : colony.huts())
        {
            writePos(out, hut.pos());
            out.writeUTF(hut.itemId());
            out.writeUTF(hut.name());
            out.writeInt(hut.level());
            out.writeInt(hut.maxLevel());
            out.writeBoolean(hut.underConstruction());
        }

        out.writeInt(colony.fields().size());
        for (final ColonyMemory.Field field : colony.fields())
        {
            writePos(out, field.pos());
            out.writeBoolean(field.taken());
            out.writeUTF(field.type());
            writeNullablePos(out, field.building());
        }

        out.writeInt(colony.patrols().size());
        for (final ColonyMemory.Patrol patrol : colony.patrols())
        {
            writePos(out, patrol.tower());
            out.writeInt(patrol.points().size());
            for (final BlockPos point : patrol.points())
            {
                writePos(out, point);
            }
        }

        out.writeInt(colony.raiderSpawns().size());
        for (final BlockPos spawn : colony.raiderSpawns())
        {
            writePos(out, spawn);
        }
        out.writeLong(colony.lastRaidTime());
    }

    private static ColonyMemory readColony(final DataInputStream in, final int version) throws IOException
    {
        final int id = in.readInt();
        final String name = in.readUTF();
        final int citizens = in.readInt();
        final int colour = in.readInt();
        final boolean own = in.readBoolean();
        final boolean hostile = in.readBoolean();
        final BlockPos centre = readPos(in);
        final long lastSeen = in.readLong();

        final int chunkCount = bounded(in.readInt(), MAX_CHUNKS, "claimed chunks");
        final long[] chunks = new long[chunkCount];
        for (int i = 0; i < chunkCount; i++)
        {
            chunks[i] = in.readLong();
        }

        final int hutCount = bounded(in.readInt(), MAX_LIST, "huts");
        final List<ColonyMemory.Hut> huts = new ArrayList<>(hutCount);
        for (int i = 0; i < hutCount; i++)
        {
            huts.add(new ColonyMemory.Hut(
              readPos(in), in.readUTF(), in.readUTF(), in.readInt(), in.readInt(), in.readBoolean()));
        }

        final int fieldCount = bounded(in.readInt(), MAX_LIST, "fields");
        final List<ColonyMemory.Field> fields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++)
        {
            fields.add(new ColonyMemory.Field(
              readPos(in), in.readBoolean(), in.readUTF(), readNullablePos(in)));
        }

        final int patrolCount = bounded(in.readInt(), MAX_LIST, "patrol routes");
        final List<ColonyMemory.Patrol> patrols = new ArrayList<>(patrolCount);
        for (int i = 0; i < patrolCount; i++)
        {
            final BlockPos tower = readPos(in);
            final int pointCount = bounded(in.readInt(), MAX_LIST, "patrol points");
            final List<BlockPos> points = new ArrayList<>(pointCount);
            for (int p = 0; p < pointCount; p++)
            {
                points.add(readPos(in));
            }
            patrols.add(new ColonyMemory.Patrol(tower, List.copyOf(points)));
        }

        final int spawnCount = bounded(in.readInt(), MAX_LIST, "raider spawn points");
        final List<BlockPos> spawns = new ArrayList<>(spawnCount);
        for (int i = 0; i < spawnCount; i++)
        {
            spawns.add(readPos(in));
        }

        return new ColonyMemory(id, name, citizens, colour, own, hostile, centre, chunks, lastSeen,
          List.copyOf(huts), List.copyOf(fields), List.copyOf(patrols), List.copyOf(spawns),
          version >= 2 ? in.readLong() : ColonyMemory.NO_RAID_TIME);
    }

    private static int bounded(final int count, final int limit, final String what)
    {
        if (count < 0 || count > limit)
        {
            throw new IllegalStateException(count + " " + what + " is not a believable count");
        }
        return count;
    }

    private static void writePos(final DataOutputStream out, final BlockPos pos) throws IOException
    {
        out.writeInt(pos.getX());
        out.writeInt(pos.getY());
        out.writeInt(pos.getZ());
    }

    private static BlockPos readPos(final DataInputStream in) throws IOException
    {
        return new BlockPos(in.readInt(), in.readInt(), in.readInt());
    }

    private static void writeNullablePos(final DataOutputStream out, final BlockPos pos) throws IOException
    {
        out.writeBoolean(pos != null);
        if (pos != null)
        {
            writePos(out, pos);
        }
    }

    private static BlockPos readNullablePos(final DataInputStream in) throws IOException
    {
        return in.readBoolean() ? readPos(in) : null;
    }

    private Path fileFor(final String dimension)
    {
        return this.root.resolve(dimension).resolve(FILE_NAME);
    }

    /**
     * One queued write.
     *
     * @param dimension which dimension's file.
     * @param colonies  what to put in it; already immutable.
     */
    private record Write(String dimension, List<ColonyMemory> colonies)
    {
    }
}
