package com.ldtteam.domumornamentum.datagen.slab;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import org.jetbrains.annotations.NotNull;

public class SlabComponentTagProvider implements IBlockTagSubProvider {

    @Override
    public void addTags(final Sink sink) {

        sink.tag(ModTags.SLAB_MATERIALS)
                .add(
                        Blocks.BLACKSTONE,
                        Blocks.GILDED_BLACKSTONE,
                        Blocks.NETHERRACK,
                        Blocks.CRIMSON_NYLIUM,
                        Blocks.WARPED_NYLIUM,
                        Blocks.BASALT,
                        Blocks.POLISHED_BASALT,
                        Blocks.SMOOTH_BASALT,
                        Blocks.HAY_BLOCK,
                        Blocks.COPPER_BLOCK.weathering().unaffected(),
                        Blocks.CUT_COPPER.weathering().unaffected(),
                        Blocks.COPPER_BLOCK.weathering().exposed(),
                        Blocks.COPPER_BLOCK.weathering().oxidized(),
                        Blocks.COPPER_BLOCK.weathering().weathered(),
                        Blocks.COPPER_BLOCK.waxed().unaffected(),
                        Blocks.COPPER_BLOCK.waxed().exposed(),
                        Blocks.COPPER_BLOCK.waxed().oxidized(),
                        Blocks.COPPER_BLOCK.waxed().weathered(),
                        Blocks.BOOKSHELF,
                        Blocks.AMETHYST_BLOCK,
                        Blocks.BUDDING_AMETHYST,
                        Blocks.CHISELED_SANDSTONE,
                        Blocks.CHISELED_QUARTZ_BLOCK,
                        Blocks.QUARTZ_PILLAR,
                        Blocks.PACKED_ICE,
                        Blocks.SNOW_BLOCK,
                        Blocks.CHISELED_STONE_BRICKS,
                        Blocks.CRACKED_STONE_BRICKS,
                        Blocks.OBSIDIAN,
                        Blocks.CALCITE,
                        Blocks.BONE_BLOCK,
                        Blocks.DRIED_KELP_BLOCK,
                        Blocks.DIRT_PATH
                )
                .addTags(
                        ModTags.GLOBAL_DEFAULT,
                        BlockTags.PLANKS,
                        ModTags.EXTRA_BLOCKS,
                        ModTags.BRICKS,
                        ModTags.CONCRETE,
                        BlockTags.TERRACOTTA,
                        BlockTags.DIRT,
                        BlockTags.WOOL,
                        BlockTags.LEAVES,
                        ConventionalBlockTags.STORAGE_BLOCKS,
                        ConventionalBlockTags.GLASS_BLOCKS,
                        BlockTags.LOGS,
                        BlockTags.CORAL_BLOCKS,
                        BlockTags.WART_BLOCKS
                );
    }

    @Override
    @NotNull
    public String getName() {
        return "Slab Tag Provider";
    }
}
