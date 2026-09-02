package com.ldtteam.domumornamentum.datagen.wall.paper;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.tags.BlockTags;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import org.jetbrains.annotations.NotNull;

import net.minecraft.world.level.block.Blocks;

public class PaperwallComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {

        sink.tag(ModTags.PAPERWALL_FRAME)
          .addTags(
            BlockTags.PLANKS,
            ModTags.GLOBAL_DEFAULT
          );

        sink.tag(ModTags.PAPERWALL_CENTER)
          .addTags(
            BlockTags.PLANKS,
            ConventionalBlockTags.STONES,
            ModTags.GLOBAL_DEFAULT
          );

    }

    @Override
    @NotNull
    public String getName()
    {
        return "Paperwall Tag Provider";
    }
}
