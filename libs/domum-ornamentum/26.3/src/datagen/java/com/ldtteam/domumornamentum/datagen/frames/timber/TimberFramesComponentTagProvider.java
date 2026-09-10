package com.ldtteam.domumornamentum.datagen.frames.timber;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import org.jetbrains.annotations.NotNull;

public class TimberFramesComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(ModTags.TIMBERFRAMES_FRAME)
          .add(
            Blocks.BRICKS,
            Blocks.DEEPSLATE,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.COBBLED_DEEPSLATE,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.POLISHED_BLACKSTONE
          )
          .addTags(
            ModTags.GLOBAL_DEFAULT,
            BlockTags.PLANKS,
            ConventionalBlockTags.OBSIDIANS,
            ConventionalBlockTags.STONES
          );

        sink.tag(ModTags.TIMBERFRAMES_CENTER)
          .add(
            Blocks.BRICKS,
            Blocks.DEEPSLATE,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.COBBLED_DEEPSLATE,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.POLISHED_BLACKSTONE
          )
          .addTags(
            ModTags.GLOBAL_DEFAULT,
            BlockTags.PLANKS,
            ConventionalBlockTags.COBBLESTONES,
            ConventionalBlockTags.STONES,
            ConventionalBlockTags.END_STONES,
            ConventionalBlockTags.NETHERRACKS,
            ConventionalBlockTags.OBSIDIANS,
            ConventionalBlockTags.SANDSTONE_BLOCKS,
            BlockTags.DIRT
          );

    }

    @Override
    @NotNull
    public String getName()
    {
        return "Timber Frames Tag Provider";
    }
}
