package com.unknownwq.worldmap.export;

import com.mojang.blaze3d.platform.NativeImage;
import com.unknownwq.worldmap.WorldMapClient;
import com.unknownwq.worldmap.map.MapService;
import com.unknownwq.worldmap.map.MapShading;
import com.unknownwq.worldmap.map.MapTile;
import com.unknownwq.worldmap.map.TileKey;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Writes a rectangle of the map out as a PNG file.
 *
 * <h2>What gets exported, and why that and not the other thing</h2>
 * <p>The rectangle is <b>the part of the world the map is currently showing</b>, at exactly one pixel per
 * block whatever the zoom. Two alternatives were available and are worse:</p>
 * <ul>
 *   <li><b>The whole explored region.</b> It has no bound. Tiles live on disk, there can be any number of
 *       them, and only a handful are in memory -- so "everything" means walking a directory and decompressing
 *       an unknown number of megabytes, on the client thread, to produce a file of unknown size. The map
 *       itself never does that, and an export button is a poor place to start.</li>
 *   <li><b>What is on screen, at screen resolution.</b> That is a screenshot, and F2 already takes those. It
 *       is also lossy in the case that matters: zoomed out to 1/4 px per block, three of every four columns
 *       are simply not in the output.</li>
 * </ul>
 * <p>One pixel per block over the visible rectangle is the version that is both bounded and lossless: it is
 * the same pixels the tiles hold, cropped to what you were looking at, and it is the same picture whether
 * you were zoomed in or out.</p>
 *
 * <p>Only <b>resident</b> tiles are read. A tile that is not in the cache exports as black, exactly as it
 * draws -- this deliberately does not pull tiles off disk, because that would put an unbounded number of
 * decompressions on the client thread for pixels the player cannot see either. Zooming to the region first
 * loads it and then it exports.</p>
 *
 * <p>The pixels are produced the same way the screen produces them -- {@link MapShading} over the tile's
 * stored planes, with whatever the configuration says -- so an export is what was on screen, not a second
 * rendering of the map with its own opinions. Each tile is shaded once into a scratch buffer and the part
 * of it that falls inside the rectangle is copied out.</p>
 *
 * <p>Nothing here uploads, shares or opens anything. It writes one file under the game directory and the
 * caller says where.</p>
 */
@Environment(EnvType.CLIENT)
public final class MapExport
{
    /**
     * Hard cap per axis. At 1/4 px per block a 4K window covers over 15000 blocks across, and
     * 15000 x 8000 x 4 bytes is half a gigabyte of off-heap image -- so the rectangle is trimmed to this,
     * centred, and the caller tells the player it was. 4096 x 4096 is 64 MiB while the file is being
     * written and a PNG of a few megabytes on disk.
     */
    public static final int MAX_SIDE = 4096;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Writes one PNG.
     *
     * @param service   the map service to read tiles out of.
     * @param gameDir   the client's game directory; the file lands under {@code <gameDir>/worldmap/exports}.
     * @param dimension the sanitized dimension name, as {@link MapService#dimension()} gives it.
     * @param blockX    world x of the rectangle's west edge.
     * @param blockZ    world z of its north edge.
     * @param width     its width in blocks, before the {@value #MAX_SIDE} cap.
     * @param height    its height in blocks, before the cap.
     * @return the file written.
     * @throws IOException if the file could not be written. The caller reports that to the player rather
     *     than swallowing it.
     */
    public static Path write(
      final MapService service,
      final Path gameDir,
      final String dimension,
      final int blockX,
      final int blockZ,
      final int width,
      final int height) throws IOException
    {
        final int w = Math.max(1, Math.min(MAX_SIDE, width));
        final int h = Math.max(1, Math.min(MAX_SIDE, height));

        // Trim from the edges rather than from one side, so a capped export is still centred on what the
        // player had centred.
        final int originX = blockX + (width - w) / 2;
        final int originZ = blockZ + (height - h) / 2;

        final Path directory = gameDir.resolve(WorldMapClient.MOD_ID).resolve("exports");
        Files.createDirectories(directory);
        final Path file = directory.resolve("%s-%d_%d-%s.png".formatted(
          dimension.isEmpty() ? "unknown" : dimension, originX, originZ, LocalDateTime.now().format(STAMP)));

        final MapShading shading = new MapShading();
        final int[] pixels = new int[MapTile.SIZE * MapTile.SIZE];

        try (NativeImage image = new NativeImage(w, h, false))
        {
            image.fillRect(0, 0, w, h, 0xFF000000);

            final int minTileX = Math.floorDiv(originX, MapTile.SIZE);
            final int maxTileX = Math.floorDiv(originX + w - 1, MapTile.SIZE);
            final int minTileZ = Math.floorDiv(originZ, MapTile.SIZE);
            final int maxTileZ = Math.floorDiv(originZ + h - 1, MapTile.SIZE);

            for (int tz = minTileZ; tz <= maxTileZ; tz++)
            {
                for (int tx = minTileX; tx <= maxTileX; tx++)
                {
                    final MapTile tile = service.residentTile(new TileKey(dimension, tx, tz));
                    if (tile == null)
                    {
                        continue;
                    }
                    shading.shade(tile.base(), tile.heights(), MapTile.SIZE, MapTile.SIZE,
                      service.config().shadingOptions(), pixels);
                    copy(tile, pixels, image, originX, originZ, w, h);
                }
            }

            image.writeToFile(file);
        }

        return file;
    }

    /**
     * Copies the part of one tile that falls inside the export rectangle.
     */
    private static void copy(
      final MapTile tile,
      final int[] pixels,
      final NativeImage image,
      final int originX,
      final int originZ,
      final int width,
      final int height)
    {
        final int tileX = tile.key().originX();
        final int tileZ = tile.key().originZ();

        final int fromX = Math.max(originX, tileX);
        final int toX = Math.min(originX + width, tileX + MapTile.SIZE);
        final int fromZ = Math.max(originZ, tileZ);
        final int toZ = Math.min(originZ + height, tileZ + MapTile.SIZE);

        for (int z = fromZ; z < toZ; z++)
        {
            final int row = (z - tileZ) * MapTile.SIZE - tileX;
            for (int x = fromX; x < toX; x++)
            {
                final int argb = pixels[row + x];
                if (argb != 0)
                {
                    // An unexplored column is shaded to zero and stays the black the rectangle was filled
                    // with; everything else is already opaque.
                    image.setPixel(x - originX, z - originZ, argb);
                }
            }
        }
    }

    private MapExport()
    {
        /*
         * Intentionally left empty.
         */
    }
}
