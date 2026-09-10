package com.ldtteam.domumornamentum.datagen.fence;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import org.jetbrains.annotations.NotNull;

public class FenceComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(ModTags.FENCE_MATERIALS)
          .addTags(
            ModTags.GLOBAL_DEFAULT
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Fence Tag Provider";
    }
}
