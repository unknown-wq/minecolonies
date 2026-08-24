package com.ldtteam.domumornamentum.datagen.door.fancy;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class FancyDoorsCompatibilityTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink)
    {
        sink.tag(BlockTags.DOORS)
          .add(
            ModBlocks.getInstance().getFancyDoor()
          );

        sink.tag(BlockTags.WOODEN_DOORS)
          .add(
            ModBlocks.getInstance().getFancyDoor()
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Fancy Doors Compatibility Tag Provider";
    }
}
