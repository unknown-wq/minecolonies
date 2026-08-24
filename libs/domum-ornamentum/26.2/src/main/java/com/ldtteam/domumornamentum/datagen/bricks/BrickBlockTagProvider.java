package com.ldtteam.domumornamentum.datagen.bricks;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.IModBlocks;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import net.minecraft.world.level.block.Blocks;

public class BrickBlockTagProvider implements IBlockTagSubProvider
{

    @Override
    @NotNull
    public String getName()
    {
        return "Brick Blocks Tag Provider";
    }

    @Override
    public void addTags(final Sink sink) {
        sink.tag(ModTags.BRICKS)
                .add(IModBlocks.getInstance().getBricks().toArray(Block[]::new))
                .add(IModBlocks.getInstance().getExtraTopBlocks().toArray(Block[]::new));
    }
}
