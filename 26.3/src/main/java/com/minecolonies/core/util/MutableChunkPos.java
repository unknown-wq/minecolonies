package com.minecolonies.core.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Reusable, mutable stand-in for {@link ChunkPos}, used as a scratch lookup key so hot loops do not
 * allocate a fresh {@link ChunkPos} per probe.
 * <p>
 * In 26.2 {@link ChunkPos} became a {@code record} and can no longer be extended, so this class no
 * longer inherits from it. It stays usable as a map key against {@code Map<ChunkPos, ?>} because
 * {@link #hashCode()} reproduces {@link ChunkPos#hashCode()} exactly and {@link #equals(Object)}
 * accepts a plain {@link ChunkPos} -- {@code HashMap} invokes {@code queryKey.equals(storedKey)},
 * so the asymmetry with {@code ChunkPos.equals} never comes into play for lookups.
 */
public class MutableChunkPos
{
    private int mutableX;
    private int mutableZ;

    public MutableChunkPos(final int x, int z)
    {
        this.mutableX = x;
        this.mutableZ = z;
    }

    public MutableChunkPos(final BlockPos pos)
    {
        this.mutableX = pos.getX() >> 4;
        this.mutableZ = pos.getZ() >> 4;
    }

    public MutableChunkPos(final long longIn)
    {
        this.mutableX = (int) longIn;
        this.mutableZ = (int) (longIn >> 32);
    }

    public long toLong()
    {
        return ChunkPos.pack(this.mutableX, this.mutableZ);
    }

    @Override
    public int hashCode()
    {
        final int i = 1664525 * this.mutableX + 1013904223;
        final int j = 1664525 * (this.mutableZ ^ -559038737) + 1013904223;
        return i ^ j;
    }

    @Override
    public boolean equals(final Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        else if (obj instanceof MutableChunkPos mcp)
        {
            return this.mutableX == mcp.mutableX && this.mutableZ == mcp.mutableZ;
        }
        else if (obj instanceof ChunkPos cp)
        {
            return this.mutableX == cp.x() && this.mutableZ == cp.z();
        }
        else
        {
            return false;
        }
    }

    public int getMinBlockX() {
       return SectionPos.sectionToBlockCoord(this.mutableX);
    }

    public int getMinBlockZ()
    {
        return SectionPos.sectionToBlockCoord(this.mutableZ);
    }

    public int getBlockX(final int offset)
    {
        return SectionPos.sectionToBlockCoord(this.mutableX, offset);
    }

    public int getBlockZ(final int offset)
    {
        return SectionPos.sectionToBlockCoord(this.mutableZ, offset);
    }

    public int getRegionX()
    {
        return this.mutableX >> 5;
    }

    public int getRegionZ()
    {
        return this.mutableZ >> 5;
    }

    public int getRegionLocalX()
    {
        return this.mutableX & 31;
    }

    public int getRegionLocalZ()
    {
        return this.mutableZ & 31;
    }

    @Override
    public String toString()
    {
        return "[" + this.mutableX + ", " + this.mutableZ + "]";
    }

    public int getChessboardDistance(final ChunkPos chunkPosIn)
    {
        return Math.max(Math.abs(this.mutableX - chunkPosIn.x()), Math.abs(this.mutableZ - chunkPosIn.z()));
    }

    public int getChessboardDistance(final MutableChunkPos chunkPosIn)
    {
        return Math.max(Math.abs(this.mutableX - chunkPosIn.mutableX), Math.abs(this.mutableZ - chunkPosIn.mutableZ));
    }

    public int getX()
    {
        return mutableX;
    }

    public void setX(final int x)
    {
        this.mutableX = x;
    }

    public int getZ()
    {
        return mutableZ;
    }

    public void setZ(final int z)
    {
        this.mutableZ = z;
    }

    public void from(final ChunkPos chunkPos)
    {
        this.mutableX = chunkPos.x();
        this.mutableZ = chunkPos.z();
    }

    public ChunkPos toImmutable()
    {
        return new ChunkPos(this.mutableX, this.mutableZ);
    }
}
