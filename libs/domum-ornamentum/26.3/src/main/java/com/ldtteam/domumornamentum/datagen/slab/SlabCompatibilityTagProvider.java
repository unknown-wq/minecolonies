package com.ldtteam.domumornamentum.datagen.slab;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class SlabCompatibilityTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {

        sink.tag(BlockTags.SLABS)
          .add(
            ModBlocks.getInstance().getSlab()
          );

        sink.tag(BlockTags.WOODEN_SLABS)
          .add(
            ModBlocks.getInstance().getSlab()
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Slab Compatibility Tag Provider";
    }
}
