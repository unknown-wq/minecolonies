package com.ldtteam.structurize.datagen;

import com.ldtteam.structurize.compat.DomumCompat;
import com.ldtteam.structurize.tag.ModTags;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityTypeIds;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Datagen provider for Block Entity Tags.
 *
 * <p>Port note: NeoForge's {@code IntrinsicHolderTagsProvider} plus {@code ExistingFileHelper} became
 * {@link FabricTagsProvider}; Fabric ships no block-entity-type flavour, so the registry key is passed
 * explicitly. 26.2 also split id storage out of the registry objects, so {@code TagAppender#add} takes a
 * {@link ResourceKey} — see /opt/mc-src/net/minecraft/data/tags/TagAppender.java:37.</p>
 */
public class BlockEntityTagProvider extends FabricTagsProvider<BlockEntityType<?>>
{
    /**
     * @param output           the pack output.
     * @param registriesFuture the registry lookup future.
     */
    public BlockEntityTagProvider(final FabricPackOutput output, final CompletableFuture<HolderLookup.Provider> registriesFuture)
    {
        super(output, Registries.BLOCK_ENTITY_TYPE, registriesFuture);
    }

    @Override
    protected void addTags(final HolderLookup.@NotNull Provider provider)
    {
        final TagAppender<BlockEntityType<?>> tag = builder(ModTags.SUBSTITUTION_ABSORB_WHITELIST);
        tag.add(BlockEntityTypeIds.CHEST);
        tag.add(BlockEntityTypeIds.SIGN);
        tag.add(BlockEntityTypeIds.LECTERN);
        tag.add(keyOf(DomumCompat.materiallyTexturedBlockEntityType()));
    }

    private static ResourceKey<BlockEntityType<?>> keyOf(final BlockEntityType<?> type)
    {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getResourceKey(type).orElseThrow();
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Structurize Block Entity Tags";
    }
}
