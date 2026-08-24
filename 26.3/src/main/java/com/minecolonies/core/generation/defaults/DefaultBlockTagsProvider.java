package com.minecolonies.core.generation.defaults;

// PORT-TODO(structurize): re-checked against the real 26.2 structurize API (ModTags.GOOD_SOLID_FOR_PLACEHOLDER).

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.core.generation.ModTagAppender;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Port note (26.2 / Fabric): was a NeoForge {@code BlockTagsProvider}; it is a
 * {@link FabricTagsProvider.BlockTagsProvider} now, and every {@code tag(...)} call goes through
 * {@link ModTagAppender} to keep the {@code add(Block...)} / {@code addTags(...)} shape and to force-add
 * references to tags this run does not define.  NeoForge's {@code Tags.Blocks.*} became
 * fabric-convention-tags' {@code ConventionalBlockTags.*} -- same {@code c:} ids.
 */
@SuppressWarnings({"ConstantConditions", "unchecked"})
public class DefaultBlockTagsProvider extends FabricTagsProvider.BlockTagsProvider
{

    public DefaultBlockTagsProvider(
      final FabricPackOutput output,
      final CompletableFuture<HolderLookup.Provider> lookupProvider)
    {
        super(output, lookupProvider);
    }

    private ModTagAppender<Block> tagOf(@NotNull final TagKey<Block> key)
    {
        return ModTagAppender.blocks(builder(key), getOrCreateRawBuilder(key));
    }

    @NotNull
    @Override
    public String getName()
    {
        return "MineColonies Block Tags";
    }

    @Override
    protected void addTags(final HolderLookup.Provider holder)
    {
        tagOf(ModTags.decorationItems)
                .add(Blocks.DEAD_BRAIN_CORAL_BLOCK)
                .add(Blocks.DEAD_BUBBLE_CORAL_BLOCK)
                .add(Blocks.DEAD_FIRE_CORAL_BLOCK)
                .add(Blocks.DEAD_HORN_CORAL_BLOCK)
                .add(Blocks.DEAD_TUBE_CORAL_BLOCK)
                .add(Blocks.BRAIN_CORAL_BLOCK)
                .add(Blocks.BUBBLE_CORAL_BLOCK)
                .add(Blocks.FIRE_CORAL_BLOCK)
                .add(Blocks.HORN_CORAL_BLOCK)
                .add(Blocks.TUBE_CORAL_BLOCK)
                .add(Blocks.BELL)
                .add(Blocks.LANTERN)
                .add(ModBlocks.blockWoodenGate)
                .add(ModBlocks.blockIronGate)
                .addTag(BlockTags.BANNERS)
                .addTag(BlockTags.SIGNS)
                .addTag(BlockTags.CAMPFIRES);

        // these tags only exist for backwards compatibility and could be removed in a future Minecraft version
        tagOf(ModTags.concreteBlocks).addTag(ConventionalBlockTags.CONCRETES);
        tagOf(ModTags.concretePowderBlocks).addTag(BlockTags.CONCRETE_POWDERS);

        tagOf(ModTags.pathingBlocks)
                .addTag(ModTags.concreteBlocks)
                .addTag(BlockTags.STONE_BRICKS)
            .addTag(BlockTags.PLANKS)
            .addTag(BlockTags.WOODEN_SLABS)
            .addTag(BlockTags.WOOL_CARPETS)
                .add(Blocks.STONE_BRICK_STAIRS)
                .add(Blocks.STONE_BRICK_SLAB)
                .add(Blocks.MOSSY_STONE_BRICK_SLAB)
                .add(Blocks.MOSSY_STONE_BRICK_STAIRS)
                .add(Blocks.POLISHED_ANDESITE)
                .add(Blocks.POLISHED_ANDESITE_SLAB)
                .add(Blocks.POLISHED_ANDESITE_STAIRS)
                .add(Blocks.POLISHED_DIORITE)
                .add(Blocks.POLISHED_DIORITE_SLAB)
                .add(Blocks.POLISHED_DIORITE_STAIRS)
                .add(Blocks.POLISHED_GRANITE)
                .add(Blocks.POLISHED_GRANITE_SLAB)
                .add(Blocks.POLISHED_GRANITE_STAIRS)
                .add(Blocks.BRICKS)
                .add(Blocks.BRICK_SLAB)
                .add(Blocks.BRICK_STAIRS)
                .add(Blocks.NETHER_BRICKS)
                .add(Blocks.NETHER_BRICK_SLAB)
                .add(Blocks.NETHER_BRICK_STAIRS)
                .add(Blocks.RED_NETHER_BRICKS)
                .add(Blocks.RED_NETHER_BRICK_SLAB)
                .add(Blocks.RED_NETHER_BRICK_STAIRS)
                .add(Blocks.CRACKED_NETHER_BRICKS)
                .add(Blocks.CHISELED_NETHER_BRICKS)
                .add(Blocks.GRAVEL)
                .add(Blocks.DIRT_PATH)
                .add(Blocks.MUD_BRICKS)
                .add(Blocks.MUD_BRICK_SLAB)
                .add(Blocks.MUD_BRICK_STAIRS)
                .add(Blocks.SMOOTH_STONE)
                .add(Blocks.SMOOTH_STONE_SLAB)
                .add(Blocks.SMOOTH_SANDSTONE)
                .add(Blocks.SMOOTH_SANDSTONE_SLAB)
                .add(Blocks.SMOOTH_SANDSTONE_STAIRS)
                .add(Blocks.CHISELED_SANDSTONE)
                .add(Blocks.CHISELED_RED_SANDSTONE)
                .add(Blocks.CUT_SANDSTONE)
                .add(Blocks.CUT_SANDSTONE_SLAB)
                .add(Blocks.CUT_RED_SANDSTONE)
                .add(Blocks.CUT_RED_SANDSTONE_SLAB)
                .add(Blocks.SMOOTH_RED_SANDSTONE)
                .add(Blocks.SMOOTH_RED_SANDSTONE_SLAB)
                .add(Blocks.SMOOTH_RED_SANDSTONE_STAIRS)
                .add(Blocks.POLISHED_BLACKSTONE)
                .add(Blocks.POLISHED_BLACKSTONE_STAIRS)
                .add(Blocks.POLISHED_BLACKSTONE_SLAB)
                .add(Blocks.POLISHED_BLACKSTONE_BRICKS)
                .add(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB)
                .add(Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS)
                .add(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS)
                .add(Blocks.CHISELED_POLISHED_BLACKSTONE)
                .add(Blocks.END_STONE_BRICKS)
                .add(Blocks.END_STONE_BRICK_SLAB)
                .add(Blocks.END_STONE_BRICK_STAIRS)
                .add(Blocks.POLISHED_DEEPSLATE)
                .add(Blocks.POLISHED_DEEPSLATE_SLAB)
                .add(Blocks.POLISHED_DEEPSLATE_STAIRS)
                .add(Blocks.DEEPSLATE_BRICKS)
                .add(Blocks.DEEPSLATE_BRICK_SLAB)
                .add(Blocks.DEEPSLATE_BRICK_STAIRS)
                .add(Blocks.DEEPSLATE_TILES)
                .add(Blocks.DEEPSLATE_TILE_SLAB)
                .add(Blocks.DEEPSLATE_TILE_STAIRS)
                .add(com.ldtteam.domumornamentum.block.ModBlocks.getInstance().getAllBrickBlocks().toArray(new Block[0]))
                .add(com.ldtteam.domumornamentum.block.ModBlocks.getInstance().getAllBrickStairBlocks().toArray(new Block[0]))
                .addTag(com.ldtteam.domumornamentum.tag.ModTags.BRICKS);

        tagOf(ModTags.dangerousBlocks);

        tagOf(ModTags.freeClimbBlocks)
                .add(Blocks.LADDER)
                .add(Blocks.SCAFFOLDING);

        tagOf(ModTags.mangroveTree)
                .add(Blocks.MANGROVE_LOG)
                .add(Blocks.MANGROVE_ROOTS);

        tagOf(ModTags.extraTree)
                .addOptionalTag(Identifier.fromNamespaceAndPath("productivebees", "nests/wood_nests"));

        // sadly forge doesn't provide the block form of this tag, despite providing an item tag
        tagOf(ModTags.mushroomBlocks)
                .add(Blocks.BROWN_MUSHROOM)
                .add(Blocks.RED_MUSHROOM);

        tagOf(ModTags.hugeMushroomBlocks)
                .add(Blocks.BROWN_MUSHROOM_BLOCK)
                .add(Blocks.RED_MUSHROOM_BLOCK);

        tagOf(ModTags.fungiBlocks)
                .add(Blocks.WARPED_FUNGUS)
                .add(Blocks.CRIMSON_FUNGUS);

        tagOf(ModTags.tree)
                .addTag(BlockTags.LOGS)
                .addTag(ModTags.mangroveTree)
                .add(Blocks.MUSHROOM_STEM)
                .addTag(ModTags.extraTree);

        tagOf(ModTags.colonyProtectionException)
                .addOptionalTag(Identifier.fromNamespaceAndPath("waystones", "waystones"));

        tagOf(ModTags.indestructible).add(Blocks.BEDROCK);
        tagOf(ModTags.oreChanceBlocks)
                .addTags(ConventionalBlockTags.STONES)
                .addTags(BlockTags.BASE_STONE_OVERWORLD, BlockTags.BASE_STONE_NETHER);

        tagOf(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(ModBlocks.blockIronGate);

        tagOf(BlockTags.MINEABLE_WITH_AXE)
                .add(ModBlocks.blockBarrel)
                .add(ModBlocks.blockRack)
                .add(ModBlocks.blockWoodenGate)
                .add(ModBlocks.blockScarecrow)
                .add(ModBlocks.blockDecorationPlaceholder)
                .add(ModBlocks.blockColonyBanner)
                .add(ModBlocks.blockColonyWallBanner)
                .add(ModBlocks.blockPostBox)
                .add(ModBlocks.blockStash)
                .add(ModBlocks.blockPlantationField)
                .add((Block[]) ModBlocks.getHuts());

        tagOf(BlockTags.MINEABLE_WITH_SHOVEL)
                .add(ModBlocks.blockCompostedDirt)
                .add(ModBlocks.blockGrave)
                .add(ModBlocks.blockNamedGrave);

        // The florist plants flowers on composted dirt. In 26.2 a VegetationBlock survives only over
        // #minecraft:supports_vegetation -- canSurvive asks nothing else -- and composted dirt was in no soil tag at
        // all, so the flower was placed and then removed by the very next neighbour update. On NeoForge this was the
        // canSustainPlant hook the block used to override; Fabric has no counterpart, so the tag is the whole fix.
        tagOf(BlockTags.SUPPORTS_VEGETATION).add(ModBlocks.blockCompostedDirt);

        tagOf(ModTags.validSpawn)
          .add(Blocks.AIR, Blocks.CAVE_AIR, Blocks.SNOW, Blocks.TALL_GRASS, Blocks.SHORT_GRASS, Blocks.FERN, Blocks.TORCH)
          .addTags(BlockTags.BUTTONS)
          .addTags(BlockTags.RAILS)
          .addTags(BlockTags.WOOL_CARPETS);

        tagOf(com.ldtteam.structurize.tag.ModTags.GOOD_SOLID_FOR_PLACEHOLDER).add(ModBlocks.farmland, ModBlocks.floodedFarmland);
    }
}
