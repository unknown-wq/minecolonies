package com.unknownwq.worldmap.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.unknownwq.worldmap.map.MapShading;
import com.unknownwq.worldmap.map.MapTile;
import com.unknownwq.worldmap.map.TileKey;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The GPU side of the map: one 512x512 RGBA texture per visible tile.
 *
 * <p>Owned by the screen and closed with it, so nothing of this mod sits in video memory while the map is
 * shut. Reopening re-uploads, which costs a handful of frames -- and the game is paused for them anyway.</p>
 *
 * <p>This is also where a tile stops being two planes of measurements and becomes a picture: the upload
 * runs {@link MapShading} over the tile's base and height planes into a scratch buffer and hands that to the
 * texture. Shading at upload rather than at scan time is what lets the relief see a whole tile at once
 * instead of one chunk, and it costs nothing extra in the common case, because an upload was already
 * touching every one of the 262144 pixels.</p>
 *
 * <h2>Detail</h2>
 * <p>The texture is created at the size the tile is actually drawn, not at 512 always. Below one pixel per
 * block a tile occupies {@code 512 * 2^zoom} screen pixels -- 256 at half, 16 at a thirty-second -- and a
 * 512-pixel texture drawn into 16 pixels is a megabyte of video memory to show two hundred and fifty-six of
 * them. So the shaded pixels are reduced by {@link MapShading#reduce} and the texture is made that size.
 * The picture is the same one a full-size texture would have produced, because the sampler is nearest and
 * would have thrown away everything the reduction averaged; what changes is that the widest zoom costs a
 * kilobyte a tile instead of a megabyte, which is the difference between a few dozen tiles on screen and a
 * few thousand.</p>
 *
 * <p>Detail is therefore part of a texture's identity, and a change to it closes every texture rather than
 * re-uploading it: the size cannot change under a live {@code DynamicTexture}. That happens once per rung
 * of the zoom ladder below 1x, and the tiles come back over the next few frames.</p>
 *
 * <h2>A texture outlives the tile behind it</h2>
 * <p>The screen draws from whatever texture is here, and only hands over a {@link MapTile} when it happens
 * to have one resident. That is deliberate: at the widest zoom there are far more tiles on screen than the
 * CPU cache holds, so a tile is loaded, uploaded and evicted again, and the picture has to survive that. A
 * tile that is still resident -- the ground around the player, which the scanner is writing into -- is
 * handed over every frame and its texture is re-uploaded whenever its revision moves, so live ground still
 * updates while the map is open.</p>
 *
 * <p>Everything here runs on the render thread. Creating a {@link DynamicTexture} needs a live
 * {@code GpuDevice}, and vanilla's own {@code MapTextureManager} creates and uploads its map textures from
 * inside {@code extractRenderState} for exactly that reason, so this follows the same rule: touch nothing
 * here from anywhere else.</p>
 */
@Environment(EnvType.CLIENT)
public final class TileTextures implements AutoCloseable
{
    /**
     * New or changed tiles uploaded per frame. One upload shades and writes 262144 pixels, a couple of
     * milliseconds; two is a bound the frame time can absorb, and a tile that misses its turn just appears
     * a frame later.
     */
    private static final int UPLOADS_PER_FRAME = 2;

    /**
     * Hard ceiling on live textures, whatever the detail level says the budget could afford. Past a few
     * thousand the cost stops being video memory and starts being the number of objects and blits, and no
     * real view needs more: it is a quarter of a million tiles' worth of explored ground on screen at once.
     */
    private static final int MAX_TEXTURES = 4096;

    private final Map<TileKey, Entry> entries = new HashMap<>();
    private final List<TileKey> evictionScratch = new ArrayList<>();
    private final MapShading shading = new MapShading();
    private final int[] scratch = new int[MapTile.SIZE * MapTile.SIZE];

    /**
     * Where {@link MapShading#reduce} puts its output. Sized for detail 1, the largest reduced texture there
     * can be; every deeper level needs less.
     */
    private final int[] reduced = new int[(MapTile.SIZE / 2) * (MapTile.SIZE / 2)];

    private final int capacity;

    private MapShading.Options options = MapShading.Options.DEFAULT;
    private int detail;
    private boolean smooth = true;
    private long frame;
    private int uploadsThisFrame;

    /**
     * @param capacity how many full-size textures may be live at once. A reduced texture is a quarter of the
     *                 size of the one a level above it, so the count this allows grows with detail and the
     *                 video memory it stands for does not.
     */
    public TileTextures(final int capacity)
    {
        this.capacity = Math.max(4, capacity);
    }

    /**
     * Call once at the top of each frame's extraction pass.
     *
     * <p>A change to any argument invalidates every texture, because all three change what the finished
     * pixels are. A change to {@code detail} goes further and throws the textures away, because it changes
     * how big they are and a {@code DynamicTexture} cannot be resized.</p>
     *
     * @param options what {@link MapShading} should apply.
     * @param detail  how many times the 512-pixel tile is halved before it becomes a texture: 0 at one pixel
     *                per block and above, 1 at a half, up to 5 at a thirty-second.
     * @param smooth  true to average each reduced block, false to sample one pixel out of it.
     */
    public void beginFrame(final MapShading.Options options, final int detail, final boolean smooth)
    {
        this.frame++;
        this.uploadsThisFrame = 0;

        if (detail != this.detail)
        {
            this.detail = detail;
            this.options = options;
            this.smooth = smooth;
            this.closeAll();
            return;
        }

        if (smooth != this.smooth || !options.equals(this.options))
        {
            this.smooth = smooth;
            this.options = options;
            for (final Entry entry : this.entries.values())
            {
                entry.revision = -1;
            }
        }
    }

    /**
     * The texture for one tile, made or refreshed if that is possible this frame.
     *
     * @param key  which tile.
     * @param tile the tile itself if it happens to be resident, otherwise null. A null means "draw what you
     *             have": an existing texture is returned unchanged, and a missing one cannot be made, which
     *             is the screen's cue to ask for the tile to be loaded.
     * @return the texture, or null if nothing can be drawn for this tile this frame.
     */
    public DynamicTexture textureFor(final TileKey key, final MapTile tile)
    {
        Entry entry = this.entries.get(key);
        if (entry == null)
        {
            if (tile == null || this.uploadsThisFrame >= UPLOADS_PER_FRAME)
            {
                return null;
            }
            final int side = MapTile.SIZE >> this.detail;
            entry = new Entry(new DynamicTexture(() -> "World Map " + key, side, side, true));
            this.entries.put(key, entry);
            this.evictIfNeeded();
        }

        entry.lastFrame = this.frame;
        if (tile != null && entry.revision != tile.revision() && this.uploadsThisFrame < UPLOADS_PER_FRAME)
        {
            entry.revision = tile.revision();
            this.upload(entry.texture, tile);
            this.uploadsThisFrame++;
        }
        return entry.texture;
    }

    /**
     * @return how many textures may be live at this detail level. A texture at detail {@code d} is
     *     {@code 4^d} times smaller than a full-size one, so the same video memory buys that many more of
     *     them; {@link #MAX_TEXTURES} is where counting objects starts to matter more than counting bytes.
     */
    private int liveCapacity()
    {
        return Math.min(MAX_TEXTURES, this.capacity << Math.min(2 * this.detail, 20));
    }

    private void evictIfNeeded()
    {
        if (this.entries.size() <= this.liveCapacity())
        {
            return;
        }
        this.evictionScratch.clear();
        this.evictionScratch.addAll(this.entries.keySet());
        this.evictionScratch.sort((a, b) -> Long.compare(this.entries.get(a).lastFrame, this.entries.get(b).lastFrame));
        for (int i = 0; i < this.evictionScratch.size() && this.entries.size() > this.liveCapacity(); i++)
        {
            final Entry victim = this.entries.remove(this.evictionScratch.get(i));
            if (victim != null)
            {
                victim.texture.close();
            }
        }
        this.evictionScratch.clear();
    }

    private void upload(final DynamicTexture texture, final MapTile tile)
    {
        this.shading.shade(tile.base(), tile.heights(), MapTile.SIZE, MapTile.SIZE, this.options, this.scratch);

        final int side = MapTile.SIZE >> this.detail;
        final int[] pixels;
        if (this.detail > 0)
        {
            MapShading.reduce(this.scratch, MapTile.SIZE, 1 << this.detail, this.smooth, this.reduced);
            pixels = this.reduced;
        }
        else
        {
            pixels = this.scratch;
        }

        final NativeImage image = texture.getPixels();
        int i = 0;
        for (int y = 0; y < side; y++)
        {
            for (int x = 0; x < side; x++)
            {
                image.setPixel(x, y, pixels[i++]);
            }
        }
        texture.upload();
    }

    /**
     * @return how many textures are live; shown in the screen's readout.
     */
    public int size()
    {
        return this.entries.size();
    }

    @Override
    public void close()
    {
        this.closeAll();
    }

    private void closeAll()
    {
        for (final Entry entry : this.entries.values())
        {
            entry.texture.close();
        }
        this.entries.clear();
    }

    private static final class Entry
    {
        private final DynamicTexture texture;
        private int revision = -1;
        private long lastFrame;

        private Entry(final DynamicTexture texture)
        {
            this.texture = texture;
        }
    }
}
