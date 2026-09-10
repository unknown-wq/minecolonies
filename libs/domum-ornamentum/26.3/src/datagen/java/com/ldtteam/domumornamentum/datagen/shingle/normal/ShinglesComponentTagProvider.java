package com.ldtteam.domumornamentum.datagen.shingle.normal;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

public class ShinglesComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(ModTags.SHINGLES_ROOF)
          .add(
            Blocks.CLAY,
            Blocks.BRICKS,
            Blocks.DEEPSLATE,
            Blocks.COBBLED_DEEPSLATE,
            Blocks.POLISHED_BLACKSTONE
          )
          .addTags(
            ModTags.GLOBAL_DEFAULT,
            BlockTags.LEAVES,
            BlockTags.PLANKS,
            BlockTags.DIRT
          );

        sink.tag(ModTags.SHINGLES_SUPPORT)
          .add(
            Blocks.DEEPSLATE,
            Blocks.COBBLED_DEEPSLATE,
            Blocks.POLISHED_BLACKSTONE
          )
          .addTags(
            ModTags.GLOBAL_DEFAULT,
            BlockTags.PLANKS
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Shingles Tag Provider";
    }
}
