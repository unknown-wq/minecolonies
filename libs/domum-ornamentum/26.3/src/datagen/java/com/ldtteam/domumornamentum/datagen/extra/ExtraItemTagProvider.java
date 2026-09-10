package com.ldtteam.domumornamentum.datagen.extra;

import com.ldtteam.domumornamentum.datagen.utils.IItemTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import org.jetbrains.annotations.NotNull;

public class ExtraItemTagProvider implements IItemTagSubProvider
{
    @Override
    public void addTags(final Sink sink) {
        sink.copy(ModTags.EXTRA_BLOCKS, ModTags.EXTRA_BLOCK_ITEMS);
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Extra Block Item Tag Provider";
    }
}
