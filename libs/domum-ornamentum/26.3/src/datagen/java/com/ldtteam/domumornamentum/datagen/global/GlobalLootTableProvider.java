package com.ldtteam.domumornamentum.datagen.global;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.BrickBlock;
import com.ldtteam.domumornamentum.block.decorative.ExtraBlock;
import com.ldtteam.domumornamentum.block.decorative.FloatingCarpetBlock;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * This class generates the default loot_table for blocks (if a block is destroyed, it drops its item).
 *
 * <p>Port note (26.2 / Fabric): NeoForge's {@code LootTableProvider(packOutput, Set.of(), List.of(SubProviderEntry…))}
 * has no counterpart. Every sub provider is now a standalone {@link FabricBlockLootSubProvider}; the second one,
 * {@link com.ldtteam.domumornamentum.datagen.loot.MaterialLootTableProvider}, is registered directly from
 * {@link com.ldtteam.domumornamentum.datagen.DomumOrnamentumDataGenerator}. Both write into
 * {@code data/domum_ornamentum/loot_table/blocks/} and cover disjoint blocks, which is safe because Fabric's
 * sub provider only emits what its own {@code generate()} produced.</p>
 */
public class GlobalLootTableProvider extends FabricBlockLootSubProvider
{
    public GlobalLootTableProvider(final FabricPackOutput output, final CompletableFuture<HolderLookup.Provider> provider) {
        super(output, provider);
    }

    @Override
    public void generate() {
        for (final BrickBlock block : ModBlocks.getInstance().getBricks())
        {
            dropSelf(block);
        }

        for (final ExtraBlock block : ModBlocks.getInstance().getExtraTopBlocks())
        {
            dropSelf(block);
        }

        for (final FloatingCarpetBlock block : ModBlocks.getInstance().getFloatingCarpets())
        {
            dropSelf(block);
        }

        dropSelf(ModBlocks.getInstance().getStandingBarrel());
        dropSelf(ModBlocks.getInstance().getLayingBarrel());
        dropSelf(ModBlocks.getInstance().getArchitectsCutter());
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Default Block Loot Tables Provider";
    }
}
