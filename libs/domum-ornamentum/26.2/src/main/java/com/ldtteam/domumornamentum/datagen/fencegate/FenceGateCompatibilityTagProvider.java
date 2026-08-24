package com.ldtteam.domumornamentum.datagen.fencegate;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class FenceGateCompatibilityTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {

        sink.tag(BlockTags.FENCE_GATES)
          .add(
            ModBlocks.getInstance().getFenceGate()
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "FenceGate Compatibility Tag Provider";
    }
}
