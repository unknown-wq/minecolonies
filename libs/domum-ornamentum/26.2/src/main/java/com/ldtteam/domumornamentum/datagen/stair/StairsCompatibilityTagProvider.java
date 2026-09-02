package com.ldtteam.domumornamentum.datagen.stair;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class StairsCompatibilityTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(BlockTags.WOODEN_STAIRS)
          .add(
            ModBlocks.getInstance().getStair()
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Stair Compatibility Tag Provider";
    }
}
