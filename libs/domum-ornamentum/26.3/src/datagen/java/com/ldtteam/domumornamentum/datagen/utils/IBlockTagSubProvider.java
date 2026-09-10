package com.ldtteam.domumornamentum.datagen.utils;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * One domain's worth of block tags.
 *
 * <p>Port note (26.2 / Fabric): on NeoForge each of these was a standalone {@code BlockTagsProvider}, and NeoForge's
 * {@code ExistingFileHelper} merged several providers writing the same tag file. Fabric has no such merging — the
 * last provider to write {@code minecraft:mineable/pickaxe} would simply overwrite the first. All sub providers are
 * therefore funnelled through the single
 * {@link com.ldtteam.domumornamentum.datagen.DomumOrnamentumBlockTagProvider}, whose {@code builder(tag)} appends to
 * one shared {@code TagBuilder} per tag.</p>
 */
public interface IBlockTagSubProvider {

    void addTags(Sink sink);

    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * The subset of the aggregating provider that sub providers are allowed to touch.
     */
    interface Sink {
        BlockTagAppender tag(TagKey<Block> tag);
    }
}
