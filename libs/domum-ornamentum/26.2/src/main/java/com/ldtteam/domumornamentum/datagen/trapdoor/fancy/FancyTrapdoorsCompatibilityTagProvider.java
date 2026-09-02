package com.ldtteam.domumornamentum.datagen.trapdoor.fancy;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class FancyTrapdoorsCompatibilityTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(BlockTags.TRAPDOORS)
          .add(
            ModBlocks.getInstance().getFancyTrapdoor()
          );

        sink.tag(BlockTags.WOODEN_TRAPDOORS)
          .add(
            ModBlocks.getInstance().getFancyTrapdoor()
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "FancyTrapdoor Compatibility Tag Provider";
    }
}
