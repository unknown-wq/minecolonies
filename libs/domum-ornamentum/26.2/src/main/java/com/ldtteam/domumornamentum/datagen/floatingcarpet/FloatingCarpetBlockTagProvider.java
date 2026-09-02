package com.ldtteam.domumornamentum.datagen.floatingcarpet;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public class FloatingCarpetBlockTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        for (final Block block : ModBlocks.getInstance().getFloatingCarpets())
        {
            sink.tag(ModTags.FLOATING_CARPETS).add(block);
        }
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Floating Carpets Tag Provider";
    }
}
