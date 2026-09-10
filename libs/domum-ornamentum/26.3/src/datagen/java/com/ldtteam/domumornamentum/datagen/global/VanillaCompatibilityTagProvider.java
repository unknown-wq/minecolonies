package com.ldtteam.domumornamentum.datagen.global;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.datagen.utils.IBlockTagSubProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

/**
 * Puts Domum Ornamentum's copies of the vanilla shapes into the vanilla tags, so that anything keyed on
 * {@code #minecraft:doors}, {@code #minecraft:slabs} and friends treats a DO slab as a slab.
 *
 * <p>These used to be nine {@code *CompatibilityTagProvider} classes carrying two statements each. The table
 * below is in the order those nine ran in {@code DomumOrnamentumBlockTagProvider}'s list, and this provider
 * takes the place of the first of them. Order is load bearing: the aggregating provider appends into one
 * shared {@code TagBuilder} per tag, so it decides the order of the entries inside the generated JSON.
 * {@link GlobalTagProvider} also writes {@code minecraft:doors} and {@code minecraft:wooden_doors}, and runs
 * before all of these — keep it that way.</p>
 */
public class VanillaCompatibilityTagProvider implements IBlockTagSubProvider
{
    private record Entry(TagKey<Block> tag, Supplier<Block> block) {}

    private static final List<Entry> ENTRIES = List.of(
      // doors
      new Entry(BlockTags.DOORS, () -> ModBlocks.getInstance().getDoor()),
      new Entry(BlockTags.WOODEN_DOORS, () -> ModBlocks.getInstance().getDoor()),
      // fancy doors
      new Entry(BlockTags.DOORS, () -> ModBlocks.getInstance().getFancyDoor()),
      new Entry(BlockTags.WOODEN_DOORS, () -> ModBlocks.getInstance().getFancyDoor()),
      // fences
      new Entry(BlockTags.FENCES, () -> ModBlocks.getInstance().getFence()),
      new Entry(BlockTags.WOODEN_FENCES, () -> ModBlocks.getInstance().getFence()),
      // fence gates
      new Entry(BlockTags.FENCE_GATES, () -> ModBlocks.getInstance().getFenceGate()),
      // slabs
      new Entry(BlockTags.SLABS, () -> ModBlocks.getInstance().getSlab()),
      new Entry(BlockTags.WOODEN_SLABS, () -> ModBlocks.getInstance().getSlab()),
      // stairs
      new Entry(BlockTags.WOODEN_STAIRS, () -> ModBlocks.getInstance().getStair()),
      // trapdoors
      new Entry(BlockTags.TRAPDOORS, () -> ModBlocks.getInstance().getTrapdoor()),
      new Entry(BlockTags.WOODEN_TRAPDOORS, () -> ModBlocks.getInstance().getTrapdoor()),
      // fancy trapdoors
      new Entry(BlockTags.TRAPDOORS, () -> ModBlocks.getInstance().getFancyTrapdoor()),
      new Entry(BlockTags.WOODEN_TRAPDOORS, () -> ModBlocks.getInstance().getFancyTrapdoor()),
      // walls
      new Entry(BlockTags.WALLS, () -> ModBlocks.getInstance().getWall()));

    @Override
    public void addTags(final Sink sink)
    {
        for (final Entry entry : ENTRIES)
        {
            sink.tag(entry.tag()).add(entry.block().get());
        }
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Vanilla Compatibility Tag Provider";
    }
}
