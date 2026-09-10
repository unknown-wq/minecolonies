package com.ldtteam.domumornamentum.datagen.shingle.slab;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import org.jetbrains.annotations.NotNull;

public class ShingleSlabComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(ModTags.SHINGLES_COVER)
          .addTags(ModTags.SHINGLES_ROOF);
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Shingle Slabs Tag Provider";
    }
}
