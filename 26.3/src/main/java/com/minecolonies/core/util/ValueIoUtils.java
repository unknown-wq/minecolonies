package com.minecolonies.core.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;

/**
 * Small bridge helpers for the 26.2 {@link ValueInput}/{@link ValueOutput} block entity and entity storage API.
 * <p>
 * 26.2 moved block entity and entity persistence off raw {@code CompoundTag} onto {@link ValueInput}/
 * {@link ValueOutput}. The mod stores a lot of {@link BlockPos} values in the shape
 * {@code BlockPosUtil} uses -- a child compound with {@code x}/{@code y}/{@code z} ints. These helpers
 * write and read exactly that shape through the new API, so the on-disk layout is unchanged and
 * {@code BlockPosUtil.read}/{@code write} stay interchangeable with them.
 */
public final class ValueIoUtils
{
    private ValueIoUtils()
    {
        throw new IllegalStateException("Tried to initialize: ValueIoUtils but this is a Utility class.");
    }

    /**
     * Write a position in the {@code BlockPosUtil} shape.
     *
     * @param output the output to write to.
     * @param name   the tag name.
     * @param pos    the position.
     */
    public static void writePos(@NotNull final ValueOutput output, final String name, @NotNull final BlockPos pos)
    {
        final ValueOutput child = output.child(name);
        child.putInt("x", pos.getX());
        child.putInt("y", pos.getY());
        child.putInt("z", pos.getZ());
    }

    /**
     * Read a position written in the {@code BlockPosUtil} shape.
     *
     * @param input the input to read from.
     * @param name  the tag name.
     * @return the position, {@link BlockPos#ZERO} if absent.
     */
    @NotNull
    public static BlockPos readPos(@NotNull final ValueInput input, final String name)
    {
        final ValueInput child = input.childOrEmpty(name);
        return new BlockPos(child.getIntOr("x", 0), child.getIntOr("y", 0), child.getIntOr("z", 0));
    }
}
