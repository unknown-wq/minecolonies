package com.unknownwq.worldmap.map;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Identifies one tile: a dimension plus tile coordinates. Tile coordinates are block coordinates divided by
 * {@link MapTile#SIZE} with floor semantics, so tile (-1, -1) is the square from (-512, -512) to (-1, -1).
 *
 * @param dimension a filesystem- and {@code Identifier}-safe form of the dimension id, e.g. {@code minecraft.overworld}.
 * @param x         tile x.
 * @param z         tile z.
 */
public record TileKey(String dimension, int x, int z)
{
    /**
     * Turns a dimension key into the string used in tile keys and on disk. Only characters legal in a file
     * name on every platform survive, so {@code minecraft:the_nether} becomes {@code minecraft.the_nether}.
     *
     * <p>26.3 note: {@code ResourceKey#location()} is now {@code ResourceKey#identifier()}, matching the
     * {@code ResourceLocation} to {@code Identifier} rename.</p>
     *
     * @param dimension the level's dimension key.
     * @return the sanitized name.
     */
    public static String dimensionName(final ResourceKey<Level> dimension)
    {
        return sanitize(dimension.identifier().getNamespace() + "." + dimension.identifier().getPath());
    }

    /**
     * @param value any string.
     * @return the string with every character outside {@code [a-z0-9._-]} replaced by {@code _}. Lower-cased
     *     first, because {@code Identifier} paths reject upper case and Windows file names are case-blind.
     */
    public static String sanitize(final String value)
    {
        final StringBuilder out = new StringBuilder(value.length());
        for (final char c : value.toLowerCase(java.util.Locale.ROOT).toCharArray())
        {
            out.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-' ? c : '_');
        }
        return out.isEmpty() ? "unknown" : out.toString();
    }

    /**
     * @param dimension the sanitized dimension name.
     * @param blockX    a block x.
     * @param blockZ    a block z.
     * @return the key of the tile containing that block column.
     */
    public static TileKey forBlock(final String dimension, final int blockX, final int blockZ)
    {
        return new TileKey(dimension, Math.floorDiv(blockX, MapTile.SIZE), Math.floorDiv(blockZ, MapTile.SIZE));
    }

    /**
     * @return the block x of this tile's north-west corner.
     */
    public int originX()
    {
        return this.x * MapTile.SIZE;
    }

    /**
     * @return the block z of this tile's north-west corner.
     */
    public int originZ()
    {
        return this.z * MapTile.SIZE;
    }

    /**
     * @return the file name this tile is stored under, without a directory.
     */
    public String fileName()
    {
        return this.x + "_" + this.z + ".wmt";
    }

    @Override
    public String toString()
    {
        return this.dimension + "/" + this.x + "," + this.z;
    }
}
