package com.ldtteam.domumornamentum.datagen.fence;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class FenceCompatibilityTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {

        sink.tag(BlockTags.FENCES)
          .add(
            ModBlocks.getInstance().getFence()
          );

        sink.tag(BlockTags.WOODEN_FENCES)
          .add(
            ModBlocks.getInstance().getFence()
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Fence Compatibility Tag Provider";
    }
}
