package com.ldtteam.domumornamentum.datagen.bricks;

import com.ldtteam.domumornamentum.datagen.utils.IItemTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import org.jetbrains.annotations.NotNull;

public class BrickItemTagProvider implements IItemTagSubProvider
{
    @Override
    @NotNull
    public String getName()
    {
        return "Brick Item Tag Provider";
    }

    @Override
    public void addTags(final Sink sink) {
        sink.copy(ModTags.BRICKS, ModTags.BRICK_ITEMS);
    }
}
