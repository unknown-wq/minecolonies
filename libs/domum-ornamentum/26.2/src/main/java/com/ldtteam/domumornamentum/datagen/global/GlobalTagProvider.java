package com.ldtteam.domumornamentum.datagen.global;

import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.tag.ModTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopperCollection;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import org.jetbrains.annotations.NotNull;

public class GlobalTagProvider implements IBlockTagSubProvider
{

    @Override
    public void addTags(final Sink sink) {
        sink.tag(ModTags.CONCRETE)
          .add(
            Blocks.CONCRETE.pick(DyeColor.BLACK),
            Blocks.CONCRETE.pick(DyeColor.CYAN),
            Blocks.CONCRETE.pick(DyeColor.BLUE),
            Blocks.CONCRETE.pick(DyeColor.BROWN),
            Blocks.CONCRETE.pick(DyeColor.GRAY),
            Blocks.CONCRETE.pick(DyeColor.GREEN),
            Blocks.CONCRETE.pick(DyeColor.LIGHT_BLUE),
            Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY),
            Blocks.CONCRETE.pick(DyeColor.LIME),
            Blocks.CONCRETE.pick(DyeColor.MAGENTA),
            Blocks.CONCRETE.pick(DyeColor.ORANGE),
            Blocks.CONCRETE.pick(DyeColor.PINK),
            Blocks.CONCRETE.pick(DyeColor.PURPLE),
            Blocks.CONCRETE.pick(DyeColor.RED),
            Blocks.CONCRETE.pick(DyeColor.WHITE),
            Blocks.CONCRETE.pick(DyeColor.YELLOW));

        sink.tag(ModTags.GLACED_TERRACOTTA).add(
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.WHITE),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.ORANGE),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.MAGENTA),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIGHT_BLUE),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.YELLOW),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIME),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.PINK),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.GRAY),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.LIGHT_GRAY),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.CYAN),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.PURPLE),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.BLUE),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.BROWN),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.GREEN),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.RED),
            Blocks.GLAZED_TERRACOTTA.pick(DyeColor.BLACK));

        // 26.2 folded the copper blocks into WeatheringCopperCollection<Block>: the flat
        // Blocks.WAXED_EXPOSED_CUT_COPPER-style constants are gone
        // (/opt/mc-src/net/minecraft/world/level/block/Blocks.java:4997,5023,5030,5074).
        sink.tag(ModTags.COPPER)
            .add(copper(Blocks.COPPER_BLOCK))
            .add(copper(Blocks.CUT_COPPER))
            .add(copper(Blocks.CHISELED_COPPER))
            .add(copper(Blocks.COPPER_GRATE));

        sink.tag(ModTags.GLOBAL_DEFAULT).add(
            Blocks.MOSS_BLOCK,
            Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS,
            Blocks.CHISELED_POLISHED_BLACKSTONE,
            Blocks.POLISHED_BLACKSTONE,
            Blocks.BRICKS,
            Blocks.CALCITE,
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
            Blocks.SANDSTONE,
            Blocks.CUT_SANDSTONE,
            Blocks.CHISELED_SANDSTONE,
            Blocks.RED_SANDSTONE,
            Blocks.CHISELED_RED_SANDSTONE,
            Blocks.CUT_RED_SANDSTONE,
            Blocks.SMOOTH_SANDSTONE,
            Blocks.SMOOTH_RED_SANDSTONE,
            Blocks.QUARTZ_PILLAR,
            Blocks.QUARTZ_BLOCK,
            Blocks.QUARTZ_BRICKS,
            Blocks.SMOOTH_QUARTZ,
            Blocks.CHISELED_QUARTZ_BLOCK,
            Blocks.RED_NETHER_BRICKS,
            Blocks.TUFF,
            Blocks.NETHER_BRICKS,
            Blocks.END_STONE_BRICKS,
            Blocks.PRISMARINE,
            Blocks.PRISMARINE_BRICKS,
            Blocks.DARK_PRISMARINE,
            Blocks.CHISELED_NETHER_BRICKS,
            Blocks.CHISELED_DEEPSLATE,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.COBBLED_DEEPSLATE,
            Blocks.CRACKED_DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES,
            Blocks.CRACKED_DEEPSLATE_TILES,
            Blocks.CALCITE,
            Blocks.TUFF,
            Blocks.BONE_BLOCK,
            Blocks.AZALEA_LEAVES,
            Blocks.FLOWERING_AZALEA_LEAVES,
            Blocks.MUD_BRICKS,
            Blocks.DRIED_KELP_BLOCK,
            Blocks.BAMBOO_BLOCK,
            Blocks.BAMBOO_MOSAIC,
            Blocks.BAMBOO_PLANKS,
            Blocks.STRIPPED_BAMBOO_BLOCK,
            Blocks.SCULK,
            Blocks.PACKED_MUD,
            Blocks.BROWN_MUSHROOM_BLOCK,
            Blocks.RED_MUSHROOM_BLOCK,
            Blocks.MAGMA_BLOCK,
            Blocks.CRYING_OBSIDIAN,
            Blocks.OBSIDIAN,
            Blocks.POLISHED_ANDESITE,
            Blocks.POLISHED_DIORITE,
            Blocks.POLISHED_GRANITE,
            Blocks.TUFF_BRICKS,
            Blocks.CHISELED_TUFF,
            Blocks.CHISELED_TUFF_BRICKS,
            Blocks.POLISHED_TUFF
        )
          .addTags(
            ModTags.EXTRA_BLOCKS,
            ConventionalBlockTags.END_STONES,
            ModTags.BRICKS,
            ModTags.CONCRETE,
            ModTags.COPPER,
            BlockTags.TERRACOTTA,
            BlockTags.WOOL,
            ConventionalBlockTags.STORAGE_BLOCKS,
            ConventionalBlockTags.GLASS_BLOCKS,
            BlockTags.LOGS,
            BlockTags.WART_BLOCKS,
            ConventionalBlockTags.STONES,
            ConventionalBlockTags.COBBLESTONES,
            ConventionalBlockTags.OBSIDIANS,
            BlockTags.STONE_BRICKS,
            BlockTags.BASE_STONE_NETHER
          );

        sink.tag(BlockTags.MINEABLE_WITH_AXE)
          .add(ModBlocks.getInstance().getArchitectsCutter(),
            ModBlocks.getInstance().getLayingBarrel(),
            ModBlocks.getInstance().getStandingBarrel());

        sink.tag(BlockTags.MINEABLE_WITH_PICKAXE)
          .add(ModBlocks.getInstance().getBricks().toArray(new Block[0]));

        ModBlocks.getInstance().getExtraTopBlocks().forEach(extraBlock -> sink.tag(extraBlock.getType().getCategory().getMineableTag()).add(extraBlock));

        sink.tag(BlockTags.DOORS)
          .add(ModBlocks.getInstance().getDoor())
          .add(ModBlocks.getInstance().getFancyDoor());

        sink.tag(BlockTags.WOODEN_DOORS)
          .add(ModBlocks.getInstance().getDoor())
          .add(ModBlocks.getInstance().getFancyDoor());

        sink.tag(BlockTags.STAIRS)
          .add(ModBlocks.getInstance().getStair())
          .add(ModBlocks.getInstance().getAllBrickStairBlocks().toArray(new Block[0]));
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Global Tag Provider";
    }

    /**
     * The eight weathering/waxed variants of a copper family, in the order the flat constants used to be listed in.
     */
    private static Block[] copper(final WeatheringCopperCollection<Block> family) {
        return new Block[] {
            family.weathering().unaffected(), family.waxed().unaffected(),
            family.weathering().exposed(), family.waxed().exposed(),
            family.weathering().weathered(), family.waxed().weathered(),
            family.weathering().oxidized(), family.waxed().oxidized()
        };
    }
}
