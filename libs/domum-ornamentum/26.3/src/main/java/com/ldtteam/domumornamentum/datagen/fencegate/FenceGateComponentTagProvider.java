package com.ldtteam.domumornamentum.datagen.fencegate;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import org.jetbrains.annotations.NotNull;

public class FenceGateComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(ModTags.FENCE_GATE_MATERIALS)
          .addTags(
            ModTags.FENCE_MATERIALS
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "FenceGate Tag Provider";
    }
}
