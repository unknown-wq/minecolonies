package com.unknownwq.worldmap.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;

/**
 * Turns a client-side chunk into 256 measurements, one per block column: what colour the ground is before
 * any lighting, how deep the water on top of it is, and what y the surface sits at.
 *
 * <p>The walk down the column is vanilla's, from {@code MapItem#update}: descend from the
 * {@code WORLD_SURFACE} heightmap skipping blocks whose map colour is {@link MapColor#NONE}, and count how
 * deep any fluid on top is. What vanilla does <i>next</i> is not copied. It picks one of three brightnesses
 * by comparing the column against the one to its north and throws the depth away; this keeps both numbers
 * and leaves the lighting to {@link MapShading}, which can see the whole tile and does not have to guess at
 * a chunk boundary. The parts of vanilla that exist only because a map item has a scale -- averaging
 * {@code scale x scale} columns into one pixel and taking the most common colour -- collapse away, because
 * this map is always one pixel per column.</p>
 *
 * <p>Deferring the shading also takes the northern neighbour out of the signature. Vanilla needed the chunk
 * at {@code z - 1} to shade this chunk's first row, and a scan that ran before that neighbour loaded left
 * the row permanently flat; there is nothing here for a missing neighbour to spoil.</p>
 *
 * <h2>Biome tint</h2>
 * <p>The one thing a {@code MapColor} cannot express is that the same block is a different colour in
 * different places. Grass, leaves and water are tinted by the biome everywhere in the world except on a map,
 * which is why a filled map shows a swamp, a jungle and a savanna as the same green. Here the block's map
 * colour is moved towards the biome's own grass, foliage or water colour -- the values the game itself
 * renders that block with -- before it is stored.</p>
 *
 * <p>Grass and foliage are scaled rather than replaced: the biome colour is divided by the colour of the
 * biome the {@code MapColor} palette was chosen against, and the map colour is multiplied by the ratio. That
 * keeps a grass block, a leaf canopy and a moss carpet distinguishable from each other while still moving
 * all three together when the biome changes. Water has no such correspondence -- {@code MapColor.WATER} is a
 * flat blue that resembles no rendered water at all -- so it is interpolated towards the biome colour
 * instead.</p>
 *
 * <p>This is the one part of the look that is <b>baked in at scan time</b> and cannot be changed afterwards
 * without re-walking the ground, because there is nowhere in the two stored planes to keep the untinted
 * colour as well. Everything else about how a column is drawn is decided at draw time.</p>
 *
 * <p>Not thread-safe, and deliberately stateful: one instance lives on the scanner thread and reuses its
 * buffers so a chunk scan allocates nothing.</p>
 */
public final class ColumnScanner
{
    /**
     * The grass colour of the biome the palette's {@link MapColor#GRASS} was chosen to look like -- a
     * temperate plain. Dividing by it turns an absolute biome colour into "how far from ordinary grass is
     * this", which is the quantity that can be applied to a map colour without destroying it.
     */
    private static final int GRASS_REFERENCE = 0x91BD59;

    /**
     * The same reference point for {@link MapColor#PLANT}, taken from foliage rather than grass.
     */
    private static final int FOLIAGE_REFERENCE = 0x77AB2F;

    /**
     * How far water is taken towards its biome colour at full strength. Not all the way: the rendered water
     * colour is a tint applied over a texture, and used neat it is darker and duller than water looks.
     */
    private static final float WATER_TINT = 0.8f;

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

    /**
     * Scan output, row-major by z: {@code base[z * 16 + x]}. Reused between calls. See {@link MapTile} for
     * what is packed into each entry.
     */
    private final int[] base = new int[256];

    /**
     * The surface y of each column of the same scan, laid out the same way, or {@link MapTile#NO_HEIGHT}
     * where there is no real surface to report. Free to collect: the walk down from the
     * {@code WORLD_SURFACE} heightmap already has to find this block to know what colour to draw.
     */
    private final short[] heights = new short[256];

    private final float biomeTint;

    /**
     * @param biomeTint how far a tintable block is moved towards its biome's own colour, 0 to 1. Zero keeps
     *                  the plain {@code MapColor} palette a filled map uses.
     */
    public ColumnScanner(final float biomeTint)
    {
        this.biomeTint = Math.min(1f, Math.max(0f, biomeTint));
    }

    /**
     * Scans one chunk.
     *
     * @param chunk      the chunk to scan. Must be loaded.
     * @param hasCeiling true in dimensions like the Nether, where vanilla draws pseudo-random dirt and stone
     *                   instead of the real surface because the real surface is the bedrock roof.
     * @return the reused {@link #base} buffer. Copy out of it -- and out of {@link #heights()}, which is
     *     filled by the same call -- before the next call.
     */
    public int[] scan(final LevelChunk chunk, final boolean hasCeiling)
    {
        java.util.Arrays.fill(this.heights, MapTile.NO_HEIGHT);
        java.util.Arrays.fill(this.base, MapTile.UNMAPPED);

        final int originX = chunk.getPos().getMinBlockX();
        final int originZ = chunk.getPos().getMinBlockZ();

        if (hasCeiling)
        {
            this.scanCeilingDimension(chunk, originX, originZ);
            return this.base;
        }

        for (int z = 0; z < 16; z++)
        {
            for (int x = 0; x < 16; x++)
            {
                final int blockX = originX + x;
                final int blockZ = originZ + z;

                final int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) + 1;
                int height = surface;
                int waterDepth = 0;
                BlockState state;

                if (height <= chunk.getMinY())
                {
                    state = Blocks.BEDROCK.defaultBlockState();
                }
                else
                {
                    do
                    {
                        this.cursor.set(blockX, --height, blockZ);
                        state = chunk.getBlockState(this.cursor);
                    }
                    while (state.getMapColor(chunk, this.cursor) == MapColor.NONE && height > chunk.getMinY());

                    if (height > chunk.getMinY() && !state.getFluidState().isEmpty())
                    {
                        int solidY = height - 1;
                        this.below.set(this.cursor);
                        BlockState belowState;
                        do
                        {
                            this.below.setY(solidY--);
                            belowState = chunk.getBlockState(this.below);
                            waterDepth++;
                        }
                        while (solidY > chunk.getMinY() && !belowState.getFluidState().isEmpty());

                        state = correctFluidState(chunk, state, this.cursor);
                    }
                }

                final MapColor colour = state.getMapColor(chunk, this.cursor);
                final int rgb = this.tint(chunk, state, colour, blockX, height, blockZ);
                final int kind = colour == MapColor.WATER ? MapTile.waterKind(waterDepth) : MapTile.LAND;

                this.base[z * 16 + x] = MapTile.pack(rgb, kind);
                this.heights[z * 16 + x] = (short) Math.max(Short.MIN_VALUE + 1, Math.min(Short.MAX_VALUE, height));
            }
        }

        return this.base;
    }

    /**
     * @return the surface height of each column of the last {@link #scan}, laid out like the base plane.
     *     {@link MapTile#NO_HEIGHT} for a column with no real surface -- every column of a roofed dimension,
     *     where vanilla draws noise instead of the ceiling.
     */
    public short[] heights()
    {
        return this.heights;
    }

    /**
     * Moves a block's map colour towards the colour the game actually renders that block with in this
     * biome. Only the three families the game tints are touched; stone stays stone everywhere.
     */
    private int tint(
      final LevelChunk chunk,
      final BlockState state,
      final MapColor colour,
      final int blockX,
      final int y,
      final int blockZ)
    {
        if (this.biomeTint <= 0f || (colour != MapColor.GRASS && colour != MapColor.PLANT && colour != MapColor.WATER))
        {
            return colour.col;
        }

        final Biome biome = chunk.getNoiseBiome(
          QuartPos.fromBlock(blockX), QuartPos.fromBlock(y), QuartPos.fromBlock(blockZ)).value();

        if (colour == MapColor.WATER)
        {
            return blend(colour.col, biome.getWaterColor(), this.biomeTint * WATER_TINT);
        }
        if (colour == MapColor.GRASS)
        {
            return ratio(colour.col, biome.getGrassColor(blockX, blockZ), GRASS_REFERENCE, this.biomeTint);
        }
        // PLANT covers both the leaf canopy and everything growing on the ground, and the game tints those
        // two from different colour maps; the block itself is the only thing that says which.
        return state.getBlock() instanceof LeavesBlock
                 ? ratio(colour.col, biome.getFoliageColor(), FOLIAGE_REFERENCE, this.biomeTint)
                 : ratio(colour.col, biome.getGrassColor(blockX, blockZ), GRASS_REFERENCE, this.biomeTint);
    }

    /**
     * Multiplies a colour by the ratio between a biome's colour and the reference biome's, per channel.
     */
    private static int ratio(final int rgb, final int biome, final int reference, final float weight)
    {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8)
        {
            final float scale = (biome >> shift & 0xFF) / (float) Math.max(1, reference >> shift & 0xFF);
            final int channel = Math.round((rgb >> shift & 0xFF) * (1f + (scale - 1f) * weight));
            out |= Math.min(255, Math.max(0, channel)) << shift;
        }
        return out;
    }

    private static int blend(final int from, final int to, final float weight)
    {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8)
        {
            final int a = from >> shift & 0xFF;
            final int channel = a + Math.round(((to >> shift & 0xFF) - a) * weight);
            out |= Math.min(255, Math.max(0, channel)) << shift;
        }
        return out;
    }

    /**
     * Vanilla's stand-in for a roofed dimension: a deterministic dirt-or-stone hash of the column position,
     * so the Nether reads as noise rather than as a solid sheet of bedrock. Recorded as
     * {@link MapTile#PRESHADED} -- there is no surface under it to light, and shading noise would only make
     * more noise.
     */
    private void scanCeilingDimension(final LevelChunk chunk, final int originX, final int originZ)
    {
        final int dirt = MapTile.pack(
          Blocks.DIRT.defaultBlockState().getMapColor(chunk, BlockPos.ZERO).calculateARGBColor(MapColor.Brightness.NORMAL),
          MapTile.PRESHADED);
        final int stone = MapTile.pack(
          Blocks.STONE.defaultBlockState().getMapColor(chunk, BlockPos.ZERO).calculateARGBColor(MapColor.Brightness.NORMAL),
          MapTile.PRESHADED);
        for (int z = 0; z < 16; z++)
        {
            for (int x = 0; x < 16; x++)
            {
                int noise = (originX + x) + (originZ + z) * 231871;
                noise = noise * noise * 31287121 + noise * 11;
                this.base[z * 16 + x] = (noise >> 20 & 1) == 0 ? dirt : stone;
            }
        }
    }

    /**
     * Vanilla's {@code MapItem#getCorrectStateForFluidBlock}: a waterlogged slab draws as its own block, but
     * a block that water merely flows over draws as water.
     */
    private static BlockState correctFluidState(final LevelChunk chunk, final BlockState state, final BlockPos pos)
    {
        final FluidState fluid = state.getFluidState();
        return !fluid.isEmpty() && !state.isFaceSturdy(chunk, pos, Direction.UP) ? fluid.createLegacyBlock() : state;
    }
}
