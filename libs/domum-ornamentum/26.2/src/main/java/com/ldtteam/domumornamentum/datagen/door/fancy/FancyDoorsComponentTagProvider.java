package com.ldtteam.domumornamentum.datagen.door.fancy;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import org.jetbrains.annotations.NotNull;

public class FancyDoorsComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(ModTags.FANCY_DOORS_MATERIALS)
          .addTags(
            ModTags.DOORS_MATERIALS
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Fancy Doors Tag Provider";
    }
}
