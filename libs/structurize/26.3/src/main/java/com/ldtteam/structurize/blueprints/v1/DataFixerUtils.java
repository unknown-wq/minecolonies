package com.ldtteam.structurize.blueprints.v1;

import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.SharedConstants;
import net.minecraft.util.datafix.DataFixers;

/**
 * Utils for data fixer mechanism.
 * <p>
 * This is a thin wrapper over {@link DataFixers#getDataFixer()} and nothing more: one hop from the stored data
 * version straight to the running one, which is what vanilla does internally anyway. The wrapper earns its keep
 * because blueprints store block states, block entities and entities as three separate lists, and
 * {@code DataFixTypes} has no members for those - only {@link net.minecraft.util.datafix.fixes.References} does -
 * so every call site would otherwise have to spell out the {@code Dynamic}/{@code NbtOps} dance itself.
 */
public class DataFixerUtils
{
    /**
     * If the used datafixer is the vanilla one.
     */
    public static boolean isVanillaDF = DataFixers.getDataFixer() instanceof com.mojang.datafixers.DataFixerUpper;

    /**
     * Private constructor to hide implicit one.
     */
    private DataFixerUtils()
    {
        // Intentionally left empty.
    }

    public static CompoundTag runDataFixer(final CompoundTag dataIn, final TypeReference dataType, final int startVersion)
    {
        return runDataFixer(dataIn, dataType, startVersion, SharedConstants.getCurrentVersion().dataVersion().version());
    }

    public static CompoundTag runDataFixer(final CompoundTag dataIn, final TypeReference dataType, final int startVersion, final int endVersion)
    {
        return startVersion == endVersion
            ? dataIn
            : (CompoundTag) DataFixers.getDataFixer()
                .update(dataType, new Dynamic<>(NbtOps.INSTANCE, dataIn), startVersion, endVersion)
                .getValue();
    }
}
