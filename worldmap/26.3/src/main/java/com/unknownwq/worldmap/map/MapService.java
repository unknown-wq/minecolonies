package com.unknownwq.worldmap.map;

import com.unknownwq.worldmap.WorldMapClient;
import com.unknownwq.worldmap.WorldMapConfig;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Owns everything that is not the screen: which world we are in, which tiles are in memory, and the one
 * background thread that scans chunks and talks to the disk.
 *
 * <h2>Where each piece of work happens</h2>
 * <ul>
 *   <li><b>Client thread.</b> {@link #onChunkLoad} only records a chunk position. {@link #tick} converts at
 *       most {@code chunksPerTick} of those positions into scan jobs, which means resolving the chunk out
 *       of the chunk source -- the one thing that genuinely has to happen on the client thread.</li>
 *   <li><b>Scanner thread.</b> Runs {@link ColumnScanner} over the {@link LevelChunk} objects handed to it,
 *       merges the result into tiles, loads tiles the screen asked for, saves dirty tiles and evicts old
 *       ones. One thread, not a pool: the work is a few hundred microseconds per chunk and serialising it
 *       removes every question about two scans racing on the same tile.</li>
 *   <li><b>Render thread.</b> Reads {@link MapTile#base()} straight out of the cache. See {@link MapTile}
 *       for why that needs no lock.</li>
 * </ul>
 *
 * <p>A scan job carries the {@link LevelChunk} object itself rather than a position, so the scanner never
 * touches the chunk source. If the chunk unloads before the scanner reaches it the object is simply
 * detached -- its sections are still there and still hold what they held when the chunk was live, which is
 * exactly the data we wanted. Reading block states off the client thread is still a read of a structure the
 * client thread may be writing, so the scan is wrapped in a catch-all: a torn read costs one chunk of map,
 * not a crash.</p>
 *
 * <p><b>A chunk is scanned once, and re-scanned only when somebody asks.</b> There is no block change
 * listener and no periodic refresh: {@link MapTile#hasChunk} is checked before every scan, so a chunk
 * already on the map -- including one loaded off disk from a previous session -- is dropped. Two things get
 * a second pass. Ground mapped by an older build, a chunk with no heights or one whose colours are finished
 * pixels rather than measurements, catches up once: see {@link #runScan}. And
 * {@link #refreshAroundPlayer}, which is called at the moment the map screen opens, re-scans the chunks the
 * client currently has loaded round the player whatever the tile already holds -- so the ground you are
 * standing on is as it is now rather than as it was the first time you walked past it.</p>
 */
@Environment(EnvType.CLIENT)
public final class MapService
{
    /**
     * Bound on the handoff queue. At 32 chunks a tick this is four seconds of backlog; past that the client
     * thread simply keeps the positions in {@link #pendingPositions} until the scanner catches up.
     */
    private static final int SCAN_QUEUE_CAPACITY = 2048;

    /**
     * Bound on the client-side position backlog. A client can hold roughly 32*32*4 chunks at render
     * distance 32; this is comfortably above that, and past it the oldest position is dropped rather than
     * letting the deque grow without limit.
     */
    private static final int PENDING_CAPACITY = 16384;

    /**
     * Chunks taken off the refresh backlog per client tick, well above {@code chunksPerTick}.
     *
     * <p>The refresh is a burst with a beginning and an end -- everything loaded round the player, queued in
     * one go when the map is opened -- not a stream, and the point of it is that the ground under the player
     * is current by the time they have finished looking at where they are. At render distance 32 the burst
     * is about 4200 chunks, which this drains in seventeen ticks; the scanning itself happens on the
     * background thread, so what the client thread pays is 256 chunk-source lookups a tick, which is an
     * array index each.</p>
     */
    private static final int REFRESH_CHUNKS_PER_TICK = 256;


    private final WorldMapConfig config;
    private final Path storeRoot;

    private final Map<TileKey, MapTile> tiles = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<ScanJob> scanQueue = new ArrayBlockingQueue<>(SCAN_QUEUE_CAPACITY);
    private final ArrayBlockingQueue<TileKey> loadQueue = new ArrayBlockingQueue<>(512);

    /**
     * Client-thread-only. A {@link LinkedHashSet} so a chunk that loads, unloads and loads again does not
     * sit in the backlog twice, while still draining oldest-first.
     */
    private final LinkedHashSet<ChunkPos> pendingPositions = new LinkedHashSet<>();

    /**
     * Client-thread-only. Chunks queued by {@link #refreshAroundPlayer} to be scanned again whatever the
     * tile already holds. Drained ahead of {@link #pendingPositions} and faster; a set, so opening the map
     * twice in a row does not queue the same ground twice.
     */
    private final LinkedHashSet<ChunkPos> refreshPositions = new LinkedHashSet<>();

    private final ArrayDeque<ChunkPos> drainBuffer = new ArrayDeque<>();

    private volatile TileStore store;

    /**
     * Set by the client thread when the world changes; consumed by the scanner, which flushes what it holds
     * to the old store and only then adopts this one. Non-null means "a world swap is outstanding".
     */
    private volatile TileStore pendingStore;

    private volatile String worldKey = "";
    private volatile String dimension = "";
    private volatile Thread worker;
    private volatile boolean running = true;

    /**
     * @param config the loaded configuration.
     * @param gameDir the client's game directory; tiles go under {@code <gameDir>/worldmap}.
     */
    public MapService(final WorldMapConfig config, final Path gameDir)
    {
        this.config = config;
        this.storeRoot = gameDir.resolve(WorldMapClient.MOD_ID);
        this.store = new TileStore(this.storeRoot.resolve("unknown"));
        this.worker = new Thread(this::runWorker, "worldmap-scanner");
        this.worker.setDaemon(true);
        this.worker.setPriority(Thread.MIN_PRIORITY);
        this.worker.start();
    }

    /**
     * @return the configuration in force.
     */
    public WorldMapConfig config()
    {
        return this.config;
    }

    /**
     * @return the sanitized name of the dimension the player is currently in, or the empty string when there
     *     is no world.
     */
    public String dimension()
    {
        return this.dimension;
    }

    /**
     * @return the directory every world's map data lives under, {@code <gamedir>/worldmap}. The colony
     *     overlay puts its remembered-colony files beside the tiles, under the same world key, so a player
     *     who deletes a world's directory deletes everything the map knows about that world.
     */
    public Path storeRoot()
    {
        return this.storeRoot;
    }

    /**
     * @return the key of the world the player is in -- {@code sp.<save-folder>} or
     *     {@code mp.<server-address>} -- or the empty string when there is no world. Recomputed in
     *     {@link #tick}, which runs before the colony overlay's tick, so a caller on the client thread reads
     *     the current world and not the last one.
     */
    public String worldKey()
    {
        return this.worldKey;
    }

    /**
     * @return how many chunks are waiting to be scanned, client backlog plus handoff queue.
     */
    public int backlog()
    {
        return this.pendingPositions.size() + this.refreshPositions.size() + this.scanQueue.size();
    }

    /**
     * @return how many tiles are resident in memory.
     */
    public int residentTiles()
    {
        return this.tiles.size();
    }

    // ---------------------------------------------------------------------------------------------------
    // Client thread
    // ---------------------------------------------------------------------------------------------------

    /**
     * A chunk arrived from the server. Records its position; the scan itself happens in {@link #tick}.
     *
     * <p>A chunk that has already been mapped is queued again here and dropped by the scanner, which is
     * cheaper than keeping a second index of what has been seen -- see {@link MapTile#hasChunk}.</p>
     *
     * @param level the level the chunk belongs to.
     * @param chunk the chunk.
     */
    public void onChunkLoad(final ClientLevel level, final LevelChunk chunk)
    {
        if (!this.config.scanEnabled)
        {
            return;
        }
        this.enqueue(chunk.getPos());
    }

    /**
     * A chunk went away. Nothing to do -- whatever it contributed is already in a tile -- but the position
     * is dropped from the backlog so we do not resolve a chunk that is no longer there.
     *
     * @param level the level the chunk belonged to.
     * @param chunk the chunk.
     */
    public void onChunkUnload(final ClientLevel level, final LevelChunk chunk)
    {
        this.pendingPositions.remove(chunk.getPos());
        this.refreshPositions.remove(chunk.getPos());
    }

    /**
     * Re-scans the chunks the client has loaded round the player, whatever the map already holds for them.
     *
     * <p>Called from the one place a player asks to see the map, which is the whole design: the map is a
     * record of ground somebody walked over, and the one part of it that has any business being current is
     * the part they are standing in. Doing it when the screen opens costs nothing at any other moment --
     * with the map shut nothing is re-scanned, and while it is open nothing is re-scanned either, so
     * walking about with the map up does not keep the scanner busy. The next refresh is the next opening.</p>
     *
     * <p>The radius is the client's own loaded area rather than a number picked here. Every chunk in the
     * square out to the effective render distance is asked for with {@code load = false}, and the ones the
     * client does not have come back null and are skipped, so what is queued is exactly what is loaded --
     * which is also exactly the set whose block states can be read at all.</p>
     *
     * <p>Cost, at render distance 32, the largest a vanilla client offers: about 4200 chunks. On the client
     * thread that is 4200 array lookups here plus 256 a tick to hand them over, none of it measurable. On
     * the scanner thread it is 4200 chunk scans at a few hundred microseconds each -- a second or two of a
     * minimum-priority background thread, spent while the player is reading the map, and visible only as
     * the tiles round the player redrawing shortly after it opens.</p>
     *
     * @param minecraft the client.
     */
    public void refreshAroundPlayer(final Minecraft minecraft)
    {
        final ClientLevel level = minecraft.level;
        if (!this.config.scanEnabled || level == null || minecraft.player == null)
        {
            return;
        }

        final int centreX = minecraft.player.chunkPosition().x();
        final int centreZ = minecraft.player.chunkPosition().z();
        final int radius = Math.max(0, minecraft.options.getEffectiveRenderDistance());

        int queued = 0;
        for (int dz = -radius; dz <= radius; dz++)
        {
            for (int dx = -radius; dx <= radius; dx++)
            {
                final LevelChunk chunk =
                  level.getChunkSource().getChunk(centreX + dx, centreZ + dz, ChunkStatus.FULL, false);
                if (chunk != null && !chunk.isEmpty())
                {
                    this.refreshPositions.add(chunk.getPos());
                    queued++;
                }
            }
        }

        if (queued > 0)
        {
            WorldMapClient.LOGGER.debug("Refreshing {} loaded chunks round the player", queued);
        }
    }

    /**
     * Called once per client tick. Notices world and dimension changes, then feeds the scanner.
     *
     * @param minecraft the client.
     */
    public void tick(final Minecraft minecraft)
    {
        final ClientLevel level = minecraft.level;
        if (level == null)
        {
            if (!this.worldKey.isEmpty())
            {
                this.leaveWorld(null);
                this.worldKey = "";
                this.dimension = "";
            }
            return;
        }

        final String key = worldKeyOf(minecraft);
        if (!key.equals(this.worldKey))
        {
            this.leaveWorld(new TileStore(this.storeRoot.resolve(key)));
            this.worldKey = key;
        }
        this.dimension = TileKey.dimensionName(level.dimension());

        if (!this.config.scanEnabled)
        {
            return;
        }
        this.drainPending(level);
    }

    private void drainPending(final ClientLevel level)
    {
        final boolean hasCeiling = level.dimensionType().hasCeiling();
        final String dim = this.dimension;

        // Refreshes first and at their own rate. They are a burst somebody is waiting on; new ground is a
        // stream that arrives at whatever speed the server sends it.
        this.hand(level, this.refreshPositions, REFRESH_CHUNKS_PER_TICK, dim, hasCeiling, true);
        this.hand(level, this.pendingPositions, this.config.chunksPerTick, dim, hasCeiling, false);
    }

    /**
     * Resolves up to {@code budget} positions out of one backlog into scan jobs.
     *
     * @param level      the level to resolve against.
     * @param backlog    the positions to take from; drained oldest first.
     * @param budget     how many to take.
     * @param dim        the dimension name to stamp the jobs with.
     * @param hasCeiling whether the dimension is roofed.
     * @param force      true to re-scan whatever the tile already holds.
     */
    private void hand(
      final ClientLevel level,
      final LinkedHashSet<ChunkPos> backlog,
      final int budget,
      final String dim,
      final boolean hasCeiling,
      final boolean force)
    {
        if (backlog.isEmpty())
        {
            return;
        }

        this.drainBuffer.clear();
        final var iterator = backlog.iterator();
        for (int i = 0; i < budget && iterator.hasNext(); i++)
        {
            this.drainBuffer.add(iterator.next());
            iterator.remove();
        }

        for (final ChunkPos pos : this.drainBuffer)
        {
            final LevelChunk chunk = level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
            if (chunk == null || chunk.isEmpty())
            {
                continue;
            }
            if (!this.scanQueue.offer(new ScanJob(this.worldKey, dim, chunk, hasCeiling, force)))
            {
                // Scanner is behind. Put the rest back and try again next tick.
                backlog.add(pos);
            }
        }
        this.drainBuffer.clear();
    }

    private void enqueue(final ChunkPos pos)
    {
        if (this.pendingPositions.size() >= PENDING_CAPACITY)
        {
            final var iterator = this.pendingPositions.iterator();
            iterator.next();
            iterator.remove();
        }
        this.pendingPositions.add(pos);
    }

    /**
     * Hands the current world back: everything queued is dropped, and the scanner is asked to write out what
     * it still holds and then start using {@code next}.
     *
     * <p>The store swap has to happen on the scanner thread, not here. If the client thread replaced
     * {@link #store} directly, the flush that follows would write the world we are leaving into the
     * directory of the world we are joining. Handing the new store over as {@link #pendingStore} instead
     * lets the scanner do both halves in the right order, and {@link #runScan} refuses to write anything at
     * all while a swap is outstanding, so no pixel can land in the wrong place in between.</p>
     *
     * @param next the store to adopt afterwards, or null when there is simply no world any more.
     */
    private void leaveWorld(final TileStore next)
    {
        this.pendingPositions.clear();
        this.refreshPositions.clear();
        this.scanQueue.clear();
        this.loadQueue.clear();
        this.pendingStore = next != null ? next : new TileStore(this.storeRoot.resolve("unknown"));
    }

    // ---------------------------------------------------------------------------------------------------
    // Render thread
    // ---------------------------------------------------------------------------------------------------

    /**
     * Looks a tile up for drawing.
     *
     * @param key which tile.
     * @return the tile if it is resident, otherwise null. A miss also schedules a disk load, so the tile
     *     usually appears within a frame or two.
     */
    public MapTile tileForDisplay(final TileKey key)
    {
        final MapTile tile = this.tiles.get(key);
        if (tile != null)
        {
            tile.touch();
            return tile;
        }
        this.requestTile(key);
        return null;
    }

    /**
     * Asks for a tile to be brought in from disk, without waiting for it.
     *
     * <p>Separate from {@link #tileForDisplay} because the screen wants the two halves apart: it draws from
     * the texture it already has whether or not the tile behind it is still resident, and it rations how
     * many tiles it asks for per frame so that a zoomed-out view fills in steadily instead of demanding a
     * thousand loads at once and evicting them again before any of them is drawn.</p>
     *
     * <p>A tile the index says does not exist is not asked for at all. Without that check a wide view over
     * unexplored ground allocates a blank 1.5 MiB tile and probes a missing file for every square on
     * screen.</p>
     *
     * @param key which tile.
     */
    public void requestTile(final TileKey key)
    {
        if (this.mayHaveTile(key.dimension(), key.x(), key.z()))
        {
            this.loadQueue.offer(key);
        }
    }

    /**
     * @param dimension the sanitized dimension name.
     * @param tileX     tile x.
     * @param tileZ     tile z.
     * @return false only when the store is certain there is nothing behind that square -- neither a file nor
     *     a chunk scanned this session. Takes coordinates rather than a {@link TileKey} because the screen
     *     asks this for every square of a zoomed-out view and there is no reason to allocate a key for the
     *     ones that answer no.
     */
    public boolean mayHaveTile(final String dimension, final int tileX, final int tileZ)
    {
        return this.store.has(dimension, tileX, tileZ);
    }

    /**
     * @param dimension the sanitized dimension name.
     * @return the tile rectangle everything known in that dimension fits inside, or null when that is not
     *     known yet or nothing is known at all.
     */
    public TileStore.Bounds exploredBounds(final String dimension)
    {
        return this.store.bounds(dimension);
    }

    /**
     * Looks a tile up without asking for it to be loaded.
     *
     * <p>The PNG export uses this: it walks a rectangle that may be far larger than anything on screen, and
     * queueing a disk load for every tile in it would put an unbounded number of decompressions on the
     * scanner thread for pixels nobody asked to look at. A tile that is not already in the cache exports as
     * black, exactly as it draws.</p>
     *
     * @param key which tile.
     * @return the tile if it is resident, otherwise null.
     */
    public MapTile residentTile(final TileKey key)
    {
        return this.tiles.get(key);
    }

    /**
     * The recorded surface y of one column, for the readout and for the teleport entry in the context menu.
     *
     * <p>Answers only out of tiles that are already resident: a miss schedules the same disk load
     * {@link #tileForDisplay} would, and returns "unknown" for this frame. That is the right shape for both
     * callers -- the readout redraws next frame anyway, and a teleport into ground the map has not got in
     * front of it is exactly the teleport that should be refused.</p>
     *
     * @param blockX world x.
     * @param blockZ world z.
     * @return the y of the block whose colour the map drew there, or {@link MapTile#NO_HEIGHT} if the tile
     *     is not resident, the column was never scanned, or the tile came off disk in the colour-only
     *     format.
     */
    public short heightAt(final int blockX, final int blockZ)
    {
        final String dim = this.dimension;
        if (dim.isEmpty())
        {
            return MapTile.NO_HEIGHT;
        }
        final MapTile tile = this.tileForDisplay(TileKey.forBlock(dim, blockX, blockZ));
        if (tile == null
              || tile.base()[Math.floorMod(blockZ, MapTile.SIZE) * MapTile.SIZE + Math.floorMod(blockX, MapTile.SIZE)]
                   == MapTile.UNMAPPED)
        {
            return MapTile.NO_HEIGHT;
        }
        return tile.heightAt(blockX, blockZ);
    }

    /**
     * The same answer as {@link #heightAt}, but waiting for the disk if that is what it takes.
     *
     * <p>For the one caller that cannot come back next frame: the teleport entry in the right-click menu,
     * which has to say where it will put the player at the moment the menu is built. {@link #heightAt}
     * answers only out of memory, and since the screen now draws from its textures rather than from resident
     * tiles, a tile the map is drawing perfectly well may not be in memory at all -- so answering "unknown"
     * there would offer the fallback height for ground whose real height is on the disk.</p>
     *
     * <p>One gzipped tile is a couple of hundred kilobytes and a few milliseconds, paid on a right-click,
     * which is the same bargain the PNG export makes. Nothing is read for a tile the index says does not
     * exist, so a click on genuinely unexplored ground costs nothing and answers immediately.</p>
     *
     * @param blockX world x.
     * @param blockZ world z.
     * @return the recorded surface y, or {@link MapTile#NO_HEIGHT} if there is genuinely none.
     */
    public short heightAtNow(final int blockX, final int blockZ)
    {
        final String dim = this.dimension;
        if (dim.isEmpty())
        {
            return MapTile.NO_HEIGHT;
        }

        final TileKey key = TileKey.forBlock(dim, blockX, blockZ);
        MapTile tile = this.tiles.get(key);
        if (tile == null)
        {
            if (!this.mayHaveTile(dim, key.x(), key.z()))
            {
                return MapTile.NO_HEIGHT;
            }
            // Off the scanner thread, which every other call to this is on. Safe because the cache is a
            // ConcurrentHashMap and resident() closes with putIfAbsent, so two threads racing on the same
            // key both end up with the same tile and the loser's arrays are simply thrown away.
            tile = this.resident(key);
        }

        tile.touch();
        if (tile.base()[Math.floorMod(blockZ, MapTile.SIZE) * MapTile.SIZE + Math.floorMod(blockX, MapTile.SIZE)]
              == MapTile.UNMAPPED)
        {
            return MapTile.NO_HEIGHT;
        }
        return tile.heightAt(blockX, blockZ);
    }

    // ---------------------------------------------------------------------------------------------------
    // Scanner thread
    // ---------------------------------------------------------------------------------------------------

    private void runWorker()
    {
        final ColumnScanner scanner = new ColumnScanner(this.config.biomeTint);
        long lastMaintenance = System.nanoTime();

        while (this.running)
        {
            try
            {
                this.applyPendingStore();
                this.store.index(this.dimension);

                final ScanJob job = this.scanQueue.poll(50, TimeUnit.MILLISECONDS);
                if (job != null)
                {
                    this.runScan(scanner, job);
                }

                TileKey request;
                while ((request = this.loadQueue.poll()) != null)
                {
                    this.resident(request);
                }

                // Eviction is checked every time round rather than only on the two-second timer. The screen
                // can ask for a few tiles every frame now, and at the widest zoom it works through more of
                // them than the cache holds; waiting two seconds to trim would let a burst of loads sit at
                // several hundred megabytes before anything gave them back.
                if (this.tiles.size() > this.config.cpuTileCap)
                {
                    this.evict();
                }

                if (System.nanoTime() - lastMaintenance > TimeUnit.SECONDS.toNanos(2))
                {
                    lastMaintenance = System.nanoTime();
                    this.saveAged();
                    this.evict();
                }
            }
            catch (final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
            catch (final Throwable t)
            {
                // A scanner that dies takes the map with it for the rest of the session, so nothing gets out.
                WorldMapClient.LOGGER.error("World map scanner recovered from an error", t);
            }
        }

        this.flushAll(true);
    }

    /**
     * Writes out everything held for the world being left and adopts the store of the one being joined.
     * Runs at the top of the scanner loop, before any scan, so no pixel is written across the boundary.
     */
    private void applyPendingStore()
    {
        final TileStore next = this.pendingStore;
        if (next == null)
        {
            return;
        }
        this.flushAll(true);
        this.store = next;
        this.pendingStore = null;
    }

    private void runScan(final ColumnScanner scanner, final ScanJob job)
    {
        if (this.pendingStore != null || !job.world().equals(this.worldKey))
        {
            // Either a world swap is outstanding, or the player changed world between this job being made
            // and being run. Its pixels belong to a store we no longer have open, so they are dropped
            // rather than filed under the new world.
            return;
        }

        final int blockX = job.chunk().getPos().getMinBlockX();
        final int blockZ = job.chunk().getPos().getMinBlockZ();
        final MapTile tile = this.resident(TileKey.forBlock(job.dimension(), blockX, blockZ));
        if (!job.force()
              && tile.hasChunk(blockX, blockZ)
              && (job.hasCeiling() || (tile.hasHeights(blockX, blockZ) && !tile.isPreshaded(blockX, blockZ))))
        {
            // Already mapped, this session or a previous one. Chunks are scanned once and are refreshed
            // only when the player opens the map, which is what job.force() marks.
            return;
        }

        // Falling through with a chunk already present means one of two things. Either the player just
        // opened the map and this is the ground round them, which is re-read whatever is already there --
        // that is the whole of job.force().
        //
        // Or it was mapped by an older build: either the
        // tile came off disk with no heights at all, or its colours are finished pixels rather than the
        // measurements the current format stores. Re-scanning replaces them with real ones, marks the tile
        // dirty so the file is rewritten in the current format, and lets the map heal itself as the player
        // walks over old ground. It happens at most once per chunk -- a re-scanned chunk has heights in
        // every column and no pre-shaded ones left, so the test above passes next time.
        //
        // The ceiling check is what stops that becoming a permanent re-scan loop. In a roofed dimension the
        // scanner draws vanilla's dirt-and-stone noise, deliberately records no heights at all, and marks
        // every column pre-shaded because there is no surface under it to light. Without the exception
        // every Nether chunk would qualify on every single load for the rest of the world's life.

        final int[] columns;
        try
        {
            columns = scanner.scan(job.chunk(), job.hasCeiling());
        }
        catch (final Throwable t)
        {
            // The client thread may have been rewriting the chunk's palette underneath us. One lost chunk.
            WorldMapClient.LOGGER.debug("Skipped chunk {} -- inconsistent read", job.chunk().getPos(), t);
            return;
        }

        tile.writeChunk(blockX, blockZ, columns, scanner.heights());
        this.store.note(tile.key());
    }

    private MapTile resident(final TileKey key)
    {
        MapTile tile = this.tiles.get(key);
        if (tile != null)
        {
            return tile;
        }
        tile = new MapTile(key);
        this.store.load(key, tile.base(), tile.heights());
        final MapTile existing = this.tiles.putIfAbsent(key, tile);
        return existing != null ? existing : tile;
    }

    private void saveAged()
    {
        final long cutoff = System.currentTimeMillis() - this.config.saveIntervalSeconds * 1000L;
        for (final MapTile tile : this.tiles.values())
        {
            if (tile.isDirty() && tile.dirtySince() <= cutoff)
            {
                tile.clearDirty();
                this.store.save(tile.key(), tile.base(), tile.heights());
            }
        }
    }

    private void evict()
    {
        final int excess = this.tiles.size() - this.config.cpuTileCap;
        if (excess <= 0)
        {
            return;
        }
        final List<MapTile> victims = new ArrayList<>(this.tiles.values());
        victims.sort(Comparator.comparingLong(MapTile::lastUsed));
        for (int i = 0; i < excess && i < victims.size(); i++)
        {
            final MapTile tile = victims.get(i);
            this.tiles.remove(tile.key(), tile);
            if (tile.isDirty())
            {
                tile.clearDirty();
                this.store.save(tile.key(), tile.base(), tile.heights());
            }
        }
    }

    private void flushAll(final boolean drop)
    {
        for (final MapTile tile : this.tiles.values())
        {
            if (tile.isDirty())
            {
                tile.clearDirty();
                this.store.save(tile.key(), tile.base(), tile.heights());
            }
        }
        if (drop)
        {
            this.tiles.clear();
        }
    }

    /**
     * Stops the scanner and waits for it to write everything out. Called from
     * {@code ClientLifecycleEvents.CLIENT_STOPPING}, so a couple of seconds of blocking is acceptable --
     * the alternative is losing the last few minutes of exploring.
     */
    public void shutdown()
    {
        this.running = false;
        final Thread thread = this.worker;
        if (thread != null)
        {
            thread.interrupt();
            try
            {
                thread.join(5000);
            }
            catch (final InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
        this.worker = null;
    }

    private static String worldKeyOf(final Minecraft minecraft)
    {
        final IntegratedServer singleplayer = minecraft.getSingleplayerServer();
        if (singleplayer != null)
        {
            final Path world = singleplayer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            final Path name = world.getFileName();
            return "sp." + TileKey.sanitize(name == null ? "world" : name.toString());
        }

        final ServerData server = minecraft.getCurrentServer();
        if (server != null && server.ip != null)
        {
            return "mp." + TileKey.sanitize(server.ip);
        }
        return "unknown";
    }

    /**
     * One unit of scanner work.
     *
     * @param world      the world key in force when the job was made; a job whose world no longer matches is
     *                   discarded rather than written into somebody else's save directory.
     * @param dimension  the sanitized dimension name the chunk belongs to, captured when the job was made so
     *                   a dimension change mid-queue cannot file pixels under the wrong dimension.
     * @param chunk      the chunk to read.
     * @param hasCeiling whether the dimension is roofed.
     * @param force      true to scan the chunk whatever the tile already holds. Set only by
     *                   {@link #refreshAroundPlayer}; every other job is dropped if the ground is already
     *                   mapped.
     */
    private record ScanJob(String world, String dimension, LevelChunk chunk, boolean hasCeiling, boolean force)
    {
    }
}
