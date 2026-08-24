package com.ldtteam.domumornamentum.datagen.wall.vanilla;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import org.jetbrains.annotations.NotNull;

public class WallComponentTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {

        sink.tag(ModTags.WALL_MATERIALS).add(
          Blocks.HAY_BLOCK,
          Blocks.BLACKSTONE,
          Blocks.GILDED_BLACKSTONE,
          Blocks.POLISHED_BLACKSTONE_BRICKS,
          Blocks.NETHERRACK,
          Blocks.CRIMSON_NYLIUM,
          Blocks.WARPED_NYLIUM,
          Blocks.BASALT,
          Blocks.POLISHED_BASALT,
          Blocks.SMOOTH_BASALT,
          Blocks.DEEPSLATE_BRICKS,
          Blocks.POLISHED_DEEPSLATE,
          Blocks.PURPUR_BLOCK,
          Blocks.PURPUR_PILLAR,
          Blocks.END_STONE,
          Blocks.OBSIDIAN,
          Blocks.AMETHYST_BLOCK,
          Blocks.BUDDING_AMETHYST,
          Blocks.PACKED_ICE,
          Blocks.SNOW_BLOCK,
          Blocks.CRACKED_STONE_BRICKS,
          Blocks.SMOOTH_STONE,
          Blocks.CHISELED_STONE_BRICKS,
          Blocks.POLISHED_ANDESITE,
          Blocks.POLISHED_DIORITE,
          Blocks.POLISHED_GRANITE,
          Blocks.SMOOTH_SANDSTONE,
          Blocks.CUT_SANDSTONE,
          Blocks.QUARTZ_BLOCK,
          Blocks.QUARTZ_BRICKS,
          Blocks.QUARTZ_PILLAR,
          Blocks.CHISELED_QUARTZ_BLOCK,
          Blocks.CALCITE,
          Blocks.BONE_BLOCK,
          Blocks.AZALEA_LEAVES,
          Blocks.FLOWERING_AZALEA_LEAVES,
          Blocks.CHISELED_RED_SANDSTONE,
          Blocks.CUT_RED_SANDSTONE
        )
          .addTags(
            ModTags.EXTRA_BLOCKS,
            ModTags.BRICKS,
            ModTags.CONCRETE,
            BlockTags.TERRACOTTA,
            ModTags.COPPER,
            BlockTags.WOOL,
            ConventionalBlockTags.STORAGE_BLOCKS,
            ConventionalBlockTags.GLASS_BLOCKS,
            BlockTags.LOGS,
            BlockTags.WART_BLOCKS,
            BlockTags.PLANKS
          );
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Wall Tag Provider";
    }
}
