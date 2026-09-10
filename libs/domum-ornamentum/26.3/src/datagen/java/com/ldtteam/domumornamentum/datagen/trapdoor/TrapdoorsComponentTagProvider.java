package com.ldtteam.domumornamentum.datagen.trapdoor;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class TrapdoorsComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {

        sink.tag(ModTags.TRAPDOORS_MATERIALS)
          .addTags(
            ModTags.GLOBAL_DEFAULT,
            BlockTags.PLANKS,
            ModTags.GLACED_TERRACOTTA
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Trapdoors Tag Provider";
    }
}
