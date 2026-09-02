package com.ldtteam.domumornamentum.datagen.trapdoor;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class TrapdoorsCompatibilityTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {

        sink.tag(BlockTags.TRAPDOORS)
          .add(
            ModBlocks.getInstance().getTrapdoor()
          );

        sink.tag(BlockTags.WOODEN_TRAPDOORS)
          .add(
            ModBlocks.getInstance().getTrapdoor()
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Trapdoor Compatibility Tag Provider";
    }
}
