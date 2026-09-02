package com.ldtteam.domumornamentum.datagen.utils;

import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

/**
 * Item-side counterpart of {@link BlockTagAppender}, including the same {@code forceAddTag} rule for foreign tags —
 * see the {@link BlockTagAppender} javadoc for why a plain {@code addTag(ItemTags.LOGS)} aborts the whole datagen.
 */
public final class ItemTagAppender {

    private final TagAppender<Item> delegate;

    public ItemTagAppender(final TagAppender<Item> delegate) {
        this.delegate = delegate;
    }

    public ItemTagAppender add(final ItemLike... items) {
        for (final ItemLike item : items) {
            this.delegate.add(item.asItem().builtInRegistryHolder().key());
        }
        return this;
    }

    @SafeVarargs
    public final ItemTagAppender addTags(final TagKey<Item>... tags) {
        for (final TagKey<Item> tag : tags) {
            addTag(tag);
        }
        return this;
    }

    public ItemTagAppender addTag(final TagKey<Item> tag) {
        if (Constants.MOD_ID.equals(tag.location().getNamespace())) {
            this.delegate.addTag(tag);
        } else {
            this.delegate.forceAddTag(tag);
        }
        return this;
    }

    public TagAppender<Item> delegate() {
        return this.delegate;
    }
}
