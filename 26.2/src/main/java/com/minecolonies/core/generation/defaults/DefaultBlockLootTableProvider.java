package com.minecolonies.core.generation.defaults;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.loot.ModLootConditions;
import com.minecolonies.core.blocks.BlockMinecoloniesRack;
import net.minecraft.advancements.predicates.StatePropertiesPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootPool.Builder;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.CopyNameFunction;
import net.minecraft.world.level.storage.loot.functions.SetBannerPatternFunction;
import net.minecraft.world.level.storage.loot.predicates.ExplosionCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraft.resources.ResourceKey;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class DefaultBlockLootTableProvider extends BlockLootSubProvider
{
    /**
     * Every table this provider produced, in insertion order.
     * <p>
     * Port note (26.2): {@code BlockLootSubProvider#generate(BiConsumer)} walks the whole block registry and
     * throws {@code Missing loottable 'minecraft:blocks/stone' for 'minecraft:stone'} for the first block it finds
     * without one. Vanilla generates them all, so that check is right for vanilla and wrong for a mod; NeoForge
     * papered over it with {@code getKnownBlocks()}, which 26.2 removed. Narrowing {@code enabledFeatures} is not
     * an alternative -- the same flag set gates the loop that <em>emits</em> the tables, so an empty one would
     * write nothing at all. Recording what {@link #add} was called with and emitting exactly that is the
     * equivalent of the old override, and it keeps the "block has no loot table key" error from {@code add}.
     */
    private final Map<ResourceKey<LootTable>, LootTable.Builder> ourTables = new LinkedHashMap<>();

    public DefaultBlockLootTableProvider(@NotNull final HolderLookup.Provider provider)
    {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), provider);
    }

    @Override
    public void add(final Block block, final LootTable.Builder builder)
    {
        super.add(block, builder);
        ourTables.put(block.getLootTable().orElseThrow(() -> new IllegalStateException("Block " + block + " does not have loot table")), builder);
    }

    @Override
    public void generate(@NotNull final BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output)
    {
        generate();
        ourTables.forEach(output);
    }

    @Override
    public void generate()
    {
        HolderLookup.RegistryLookup<Enchantment> enchantments = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
        saveBlocks(Arrays.asList(ModBlocks.getHuts()));

        saveBlock(ModBlocks.blockHutWareHouse);
        saveBlock(ModBlocks.blockStash);

        saveBlock(ModBlocks.blockRack);
        saveBlock(ModBlocks.blockWayPoint);
        saveBlock(ModBlocks.blockBarrel);
        saveBlock(ModBlocks.blockScarecrow);
        saveBlock(ModBlocks.blockPlantationField);
        saveBlock(ModBlocks.blockColonyBanner);
        saveBlock(ModBlocks.blockColonyWallBanner);
        saveBlock(ModBlocks.blockIronGate);
        saveBlock(ModBlocks.blockWoodenGate);
        saveBlock(ModBlocks.blockCompostedDirt,
          lootPool -> lootPool.add(AlternativesEntry.alternatives()
                                     .otherwise(LootItem.lootTableItem(ModBlocks.blockCompostedDirt)
                                                  .when(ModLootConditions.hasSilkTouch(enchantments)))
                                     .otherwise(LootItem.lootTableItem(Blocks.DIRT)
                                                  .when(ExplosionCondition.survivesExplosion()))));

        saveBlock(ModBlocks.farmland, lootPool -> lootPool.add(AlternativesEntry.alternatives().otherwise(LootItem.lootTableItem(Blocks.DIRT))));
        saveBlock(ModBlocks.floodedFarmland, lootPool -> lootPool.add(AlternativesEntry.alternatives().otherwise(LootItem.lootTableItem(Blocks.DIRT))));
        saveBlock(ModBlocks.blockColonySign);

        for (Block block : ModBlocks.getCrops())
        {
            final LootItemBlockStatePropertyCondition.Builder cropCondition = LootItemBlockStatePropertyCondition.hasBlockStateProperties(block).setProperties(StatePropertiesPredicate.Builder.properties().hasProperty(CropBlock.AGE, 6));
            saveBlock(block, lootPool -> lootPool.add(LootItem.lootTableItem(block.asItem()).when(cropCondition).apply(ApplyBonusCount.addBonusBinomialDistributionCount(enchantments.getOrThrow(Enchantments.FORTUNE), 0.5714286F, 3)).otherwise(LootItem.lootTableItem(block.asItem()))));
        }

        // intentionally no drops -- creative only
        //saveBlock(ModBlocks.blockDecorationPlaceholder);
    }

    private <T extends Block> void saveBlocks(@NotNull final List<T> blocks)
    {
        for (final Block block : blocks)
        {
            saveBlock(block);
        }
    }

    private void saveBlock(@NotNull final Block block)
    {
        final LootPoolSingletonContainer.Builder<?> item = LootItem.lootTableItem(block);
        if (block instanceof AbstractBlockHut || block instanceof BlockMinecoloniesRack)
        {
            item.apply(CopyNameFunction.copyName(LootContext.BlockEntityTarget.BLOCK_ENTITY));
        }

        this.saveBlock(block, lootPool -> lootPool.add(item).when(ExplosionCondition.survivesExplosion()));
    }

    private void saveBlock(@NotNull final Block block, final Consumer<Builder> lootPoolConfigurer)
    {
            final Builder lootPoolbuilder = LootPool.lootPool();
            lootPoolConfigurer.accept(lootPoolbuilder);
            add(block, LootTable.lootTable().withPool(lootPoolbuilder));
    }

    private void saveBannerBlock(@NotNull final Block block)
    {
            add(block,
              LootTable.lootTable().withPool(LootPool.lootPool()
                                               .add(LootItem.lootTableItem(block))
                                               .apply(CopyNameFunction.copyName(LootContext.BlockEntityTarget.BLOCK_ENTITY))
                                               .apply(SetBannerPatternFunction.setBannerPattern(false))
                                               .when(ExplosionCondition.survivesExplosion())
              ));
    }

    // 26.2: BlockLootSubProvider no longer declares getKnownBlocks() -- the "did you forget a block?" check
    // moved to Fabric's FabricBlockLootSubProvider, which we do not extend (see SimpleLootTableProvider).
    // The former list was exactly the blocks generated above.
}
