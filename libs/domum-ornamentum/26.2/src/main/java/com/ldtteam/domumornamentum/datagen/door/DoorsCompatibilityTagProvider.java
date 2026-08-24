package com.ldtteam.domumornamentum.datagen.door;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class DoorsCompatibilityTagProvider implements IBlockTagSubProvider
{

    @Override
    @NotNull
    public String getName()
    {
        return "Door Compatibility Tag Provider";
    }

    @Override
    public void addTags(final Sink sink) {
        sink.tag(BlockTags.DOORS)
                .add(
                        ModBlocks.getInstance().getDoor()
                );

        sink.tag(BlockTags.WOODEN_DOORS)
                .add(
                        ModBlocks.getInstance().getDoor()
                );
    }
}
