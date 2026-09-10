package com.ldtteam.domumornamentum.datagen.utils;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * One domain's worth of item tags. See {@link IBlockTagSubProvider} for why these are aggregated.
 */
public interface IItemTagSubProvider {

    void addTags(Sink sink);

    default String getName() {
        return getClass().getSimpleName();
    }

    interface Sink {
        /**
         * Copies a block tag into the item tag of the same shape — {@code FabricTagsProvider.ItemTagsProvider#copy}.
         */
        void copy(TagKey<Block> blockTag, TagKey<Item> itemTag);

        ItemTagAppender tag(TagKey<Item> tag);
    }
}
