package com.ldtteam.domumornamentum.datagen.extra;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import net.minecraft.world.level.block.Blocks;

public class ExtraBlockTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        for (final Block block : ModBlocks.getInstance().getExtraTopBlocks())
        {
            sink.tag(ModTags.EXTRA_BLOCKS).add(block);
        }
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Extra Blocks Tag Provider";
    }
}
