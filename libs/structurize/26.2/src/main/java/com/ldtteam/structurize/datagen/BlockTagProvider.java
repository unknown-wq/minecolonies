package com.ldtteam.structurize.datagen;

import com.ldtteam.structurize.tag.ModTags;
import com.ldtteam.structurize.util.BlockUtils;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Datagen provider for Block Tags.
 *
 * <p>Port note: vanilla tags such as {@code minecraft:leaves} are not visible to a Fabric datagen run, so a
 * plain {@code addTag} aborts with "missing following references". {@code forceAddTag} is Fabric's answer and
 * serialises byte-identically to the NeoForge output — the same workaround Domum Ornamentum landed on, see
 * /workspace/domum-ornamentum/26.2/src/main/java/com/ldtteam/domumornamentum/datagen/utils/BlockTagAppender.java:60.</p>
 */
public class BlockTagProvider extends FabricTagsProvider.BlockTagsProvider
{
    /**
     * @param output           the pack output.
     * @param registriesFuture the registry lookup future.
     */
    public BlockTagProvider(final FabricPackOutput output, final CompletableFuture<HolderLookup.Provider> registriesFuture)
    {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(final HolderLookup.@NotNull Provider provider)
    {
        final TagAppender<Block> weakSolidTag = builder(ModTags.WEAK_SOLID_BLOCKS).forceAddTag(BlockTags.LEAVES);

        provider.lookupOrThrow(Registries.BLOCK)
            .filterElements(block -> block instanceof Fallable || block instanceof FallingBlock)
            .filterElements(BlockUtils::canBlockSurviveWithoutSupport)
            .listElementIds()
            .forEach(weakSolidTag::add);

        builder(ModTags.UNSUITABLE_SOLID_FOR_PLACEHOLDER).forceAddTag(BlockTags.LEAVES);

        builder(ModTags.GOOD_SOLID_FOR_PLACEHOLDER).add(Blocks.FARMLAND.builtInRegistryHolder().key());

        builder(ModTags.BLUEPRINT_BLACKLIST);
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Structurize Block Tags";
    }
}
