package com.minecolonies.api.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

/**
 * Fabric 26.2 replacement for {@code net.neoforged.neoforge.common.util.INBTSerializable}.
 * <p>
 * NeoForge's interface has no counterpart in Fabric or in vanilla, and the mod implements it on ~24 api types and
 * calls {@code serializeNBT}/{@code deserializeNBT} in 390 places, so the interface is reproduced verbatim (same
 * method names, same parameter order) rather than migrated to a different serialisation contract.
 *
 * @param <T> the tag type this object serialises to.
 */
public interface INBTSerializable<T extends Tag>
{
    /**
     * Write this object out to a fresh tag.
     *
     * @param provider registry lookup used for codec-based members.
     * @return the serialized tag.
     */
    T serializeNBT(final HolderLookup.Provider provider);

    /**
     * Read this object back from a tag previously produced by {@link #serializeNBT(HolderLookup.Provider)}.
     *
     * @param provider registry lookup used for codec-based members.
     * @param nbt      the tag to read.
     */
    void deserializeNBT(final HolderLookup.Provider provider, final T nbt);
}
