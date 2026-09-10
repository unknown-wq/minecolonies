package com.ldtteam.domumornamentum.datagen.frames.light;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public class FramedLightComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(ModTags.FRAMED_LIGHT_CENTER)
          .add(
            Blocks.GLOWSTONE,
            Blocks.SEA_LANTERN,
            Blocks.OCHRE_FROGLIGHT,
            Blocks.PEARLESCENT_FROGLIGHT,
            Blocks.VERDANT_FROGLIGHT,
            Blocks.SHROOMLIGHT
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Framed Light Tag Provider";
    }
}
