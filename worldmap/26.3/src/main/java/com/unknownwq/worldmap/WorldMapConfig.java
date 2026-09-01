package com.unknownwq.worldmap;

import com.unknownwq.worldmap.map.MapShading;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The whole configuration surface, deliberately tiny: a handful of numbers in a java.util.Properties file at
 * {@code config/worldmap.properties}. It is read once at startup and never written back except to create
 * the file with its defaults the first time, so a hand-edited file is never clobbered.
 *
 * <p>Nothing here is hot-reloadable. Changing a value needs a client restart.</p>
 */
public final class WorldMapConfig
{
    /**
     * Chunks handed to the scanner thread per client tick. 32 chunks/tick is 640 chunks a second, which is
     * faster than a client can receive them, so in practice the queue is drained as fast as it fills.
     */
    public final int chunksPerTick;

    /**
     * Live 512x512 tiles held in Java heap. A tile is a 512*512 {@code int[]} of colour (1 MiB) plus a
     * 512*512 {@code short[]} of surface height (512 KiB), so <b>1.5 MiB each</b> and the default 64 is a
     * ceiling of <b>96 MiB</b>.
     *
     * <p>It used to be sized to cover the whole view at the widest zoom, and that is no longer what it is
     * for. The map now zooms out to 1/32 of a pixel per block, where a window covers thousands of tiles and
     * no believable cache covers them all; what carries the picture there is the texture cache, which holds
     * the tile reduced to the size it is actually drawn. This cache is the staging area behind that: chunks
     * are scanned into it, tiles are loaded into it to be uploaded, and both are given back afterwards. The
     * screen asks for only a handful of tiles a frame for exactly that reason, so the default is generous
     * rather than tight. Lower it if 96 MiB is too much -- the cache evicts sooner and reloads from
     * disk.</p>
     */
    public final int cpuTileCap;

    /**
     * Live GPU textures, counted in full-size ones. A full-size texture is 512x512 RGBA8, 1 MiB -- colour
     * only, the height plane never reaches the GPU -- so the default 48 is a ceiling of <b>48 MiB</b>.
     *
     * <p>Below one pixel per block a texture is made the size the tile is drawn instead of 512 square, so it
     * is a quarter of the size for every rung down the zoom ladder, and the number of live textures this
     * figure allows grows to match: 48 at 1x, and tens of thousands at 1/32, for the same video memory. It
     * is a budget in bytes wearing a count as its clothes. A view needing more than the budget allows draws
     * the surplus tiles as black until they come back into the cache; raise it if that bothers you.</p>
     */
    public final int gpuTileCap;

    /**
     * How long a tile may sit modified in memory before the writer thread flushes it to disk. Bounds how
     * much scanning a hard crash can lose.
     */
    public final int saveIntervalSeconds;

    /**
     * Master switch. False leaves the mod loaded but scanning nothing; the map then only shows tiles that
     * were already written to disk on an earlier session.
     */
    public final boolean scanEnabled;

    /**
     * Whole wheel notches' worth of fine-grained scrolling needed to move one rung of the zoom ladder.
     *
     * <p>Applies only to the fractional deltas a touchpad emits. A notched wheel sends one whole unit per
     * detent and is stepped immediately whatever this says, so one notch is always exactly one rung. Raise
     * it if a two-finger swipe still races through the ladder, lower it if the map feels sluggish.</p>
     */
    public final double scrollZoomThreshold;

    /**
     * Screen pixels the map pans per whole unit of horizontal scroll. Zero switches horizontal-scroll
     * panning off and leaves dragging as the only way to pan.
     */
    public final double scrollPanPixels;

    /**
     * How far a grass, leaf or water block is moved towards its biome's own colour when it is scanned, 0 to
     * 1. Zero gives the flat {@code MapColor} palette a filled map uses.
     *
     * <p>Alone among the appearance settings this one is <b>baked into the tile</b>, because there is
     * nowhere in the stored planes to keep the untinted colour as well. Changing it therefore only affects
     * ground scanned afterwards; everything already on disk keeps the tint it was scanned with.</p>
     */
    public final float biomeTint;

    /**
     * Strength of the relief shading, 0 to 2. Zero draws the surface flat.
     */
    public final float hillshade;

    /**
     * Strength of the water depth ramp and the coastline highlight, 0 to 2. Zero draws every depth of water
     * the same.
     */
    public final float waterDepth;

    /**
     * Strength of the hypsometric tint -- high ground warmer and lighter, low ground cooler -- 0 to 2.
     */
    public final float elevationTint;

    /**
     * Vertical spacing of contour lines in blocks. Zero draws none.
     */
    public final int contourInterval;

    /**
     * Whether the pixels of a tile are averaged together when the map is zoomed out past one pixel per
     * block. Off, the screen samples one column in four or one in sixteen and discards the rest, which is
     * cheaper and looks like static.
     */
    public final boolean smoothZoomedOut;

    private WorldMapConfig(final Properties props)
    {
        this.chunksPerTick = clamp(readInt(props, "chunksPerTick", 32), 1, 512);
        this.cpuTileCap = clamp(readInt(props, "cpuTileCap", 64), 4, 512);
        this.gpuTileCap = clamp(readInt(props, "gpuTileCap", 48), 4, 256);
        this.saveIntervalSeconds = clamp(readInt(props, "saveIntervalSeconds", 20), 2, 600);
        this.scanEnabled = Boolean.parseBoolean(props.getProperty("scanEnabled", "true"));
        this.scrollZoomThreshold = clamp(readDouble(props, "scrollZoomThreshold", 2.0), 0.05, 64.0);
        this.scrollPanPixels = clamp(readDouble(props, "scrollPanPixels", 16.0), 0.0, 512.0);
        this.biomeTint = (float) clamp(readDouble(props, "biomeTint", 1.0), 0.0, 1.0);
        this.hillshade = (float) clamp(readDouble(props, "hillshade", 1.0), 0.0, 2.0);
        this.waterDepth = (float) clamp(readDouble(props, "waterDepth", 1.0), 0.0, 2.0);
        this.elevationTint = (float) clamp(readDouble(props, "elevationTint", 0.55), 0.0, 2.0);
        this.contourInterval = clamp(readInt(props, "contourInterval", 0), 0, 256);
        this.smoothZoomedOut = Boolean.parseBoolean(props.getProperty("smoothZoomedOut", "true"));
    }

    /**
     * @return the shading knobs, packaged for {@link MapShading}.
     */
    public MapShading.Options shadingOptions()
    {
        return new MapShading.Options(
          this.hillshade, this.waterDepth, this.elevationTint, this.contourInterval,
          MapShading.Options.DEFAULT.seaLevel());
    }

    /**
     * Reads the config file, creating it with defaults if it is absent. Any failure -- unreadable file,
     * garbage contents -- falls back to the defaults rather than stopping the mod from loading.
     *
     * @param file the properties file to read.
     * @return the configuration; never null.
     */
    public static WorldMapConfig load(final Path file)
    {
        final Properties props = new Properties();
        try
        {
            if (Files.exists(file))
            {
                try (var in = Files.newBufferedReader(file))
                {
                    props.load(in);
                }
            }
        }
        catch (final IOException | IllegalArgumentException e)
        {
            WorldMapClient.LOGGER.warn("Could not read {}, using defaults", file, e);
            props.clear();
        }

        final WorldMapConfig config = new WorldMapConfig(props);
        if (!Files.exists(file))
        {
            config.writeDefaults(file);
        }
        return config;
    }

    private void writeDefaults(final Path file)
    {
        final String text = """
            # World Map -- client-only configuration. Restart the client after editing.

            # Chunks fed to the background scanner each client tick.
            chunksPerTick=%d

            # Live 512x512 map tiles held in memory. One tile is 1.5 MiB -- 1 MiB of colour and
            # 512 KiB of surface height -- so the default 64 is a ceiling of 96 MiB. This is the staging
            # area for scanning and uploading, not the picture: what is on screen lives in the textures.
            cpuTileCap=%d

            # Live GPU textures, counted in full-size ones: 1 MiB each in VRAM, colour only, no heights.
            # Zoomed out a texture is made the size the tile is drawn, so it is far smaller and this budget
            # buys correspondingly more of them.
            gpuTileCap=%d

            # Seconds a modified tile may wait in memory before it is flushed to disk.
            saveIntervalSeconds=%d

            # Set to false to stop scanning entirely; already-saved tiles still display.
            scanEnabled=%s

            # Wheel notches' worth of fine-grained scrolling per rung of the zoom ladder. Only touchpads
            # scroll finely enough for this to apply; one notch of a wheel is always one rung. Raise it if
            # a two-finger swipe races through the zoom levels, lower it if the map feels sluggish.
            scrollZoomThreshold=%s

            # Screen pixels panned per whole unit of horizontal scroll. 0 turns it off.
            scrollPanPixels=%s

            # How far grass, leaves and water move towards their biome's own colour, 0 to 1. This one is
            # baked in when a chunk is scanned, so changing it only affects ground you have not mapped yet.
            biomeTint=%s

            # Relief shading strength, 0 to 2. 0 draws the ground flat.
            hillshade=%s

            # Water depth ramp and coastline highlight, 0 to 2.
            waterDepth=%s

            # Hypsometric tint -- high ground warmer, low ground cooler -- 0 to 2.
            elevationTint=%s

            # Contour line spacing in blocks. 0 draws none.
            contourInterval=%d

            # Average pixels together when zoomed out past one pixel per block, instead of dropping three
            # columns in four.
            smoothZoomedOut=%s
            """.formatted(this.chunksPerTick, this.cpuTileCap, this.gpuTileCap, this.saveIntervalSeconds, this.scanEnabled,
          this.scrollZoomThreshold, this.scrollPanPixels, this.biomeTint, this.hillshade, this.waterDepth,
          this.elevationTint, this.contourInterval, this.smoothZoomedOut);
        try
        {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text);
        }
        catch (final IOException e)
        {
            WorldMapClient.LOGGER.warn("Could not write default config to {}", file, e);
        }
    }

    private static int readInt(final Properties props, final String key, final int fallback)
    {
        final String raw = props.getProperty(key);
        if (raw == null)
        {
            return fallback;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (final NumberFormatException e)
        {
            return fallback;
        }
    }

    private static double readDouble(final Properties props, final String key, final double fallback)
    {
        final String raw = props.getProperty(key);
        if (raw == null)
        {
            return fallback;
        }
        try
        {
            final double value = Double.parseDouble(raw.trim());
            return Double.isFinite(value) ? value : fallback;
        }
        catch (final NumberFormatException e)
        {
            return fallback;
        }
    }

    private static int clamp(final int value, final int min, final int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(final double value, final double min, final double max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
