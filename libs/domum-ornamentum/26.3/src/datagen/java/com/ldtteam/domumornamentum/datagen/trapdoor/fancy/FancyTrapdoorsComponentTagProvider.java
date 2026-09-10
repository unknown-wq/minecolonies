package com.ldtteam.domumornamentum.datagen.trapdoor.fancy;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import org.jetbrains.annotations.NotNull;

public class FancyTrapdoorsComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(ModTags.FANCY_TRAPDOORS_MATERIALS)
          .addTags(
            ModTags.TRAPDOORS_MATERIALS
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "FancyTrapdoors Tag Provider";
    }
}
