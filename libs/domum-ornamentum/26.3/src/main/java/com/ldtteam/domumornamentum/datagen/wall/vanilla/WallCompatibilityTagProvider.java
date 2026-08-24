package com.ldtteam.domumornamentum.datagen.wall.vanilla;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import net.minecraft.tags.BlockTags;
import org.jetbrains.annotations.NotNull;

public class WallCompatibilityTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {

        sink.tag(BlockTags.WALLS)
          .add(
            ModBlocks.getInstance().getWall()
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Wall Compatibility Tag Provider";
    }
}
