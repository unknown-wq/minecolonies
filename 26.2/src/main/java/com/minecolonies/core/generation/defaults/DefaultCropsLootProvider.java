package com.minecolonies.core.generation.defaults;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.loot.EntityInBiomeTag;
import com.minecolonies.api.loot.ModLootConditions;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Loot datagen for crop items -- not the blocks themselves, but how to obtain them from the world.
  */
public class DefaultCropsLootProvider implements LootTableSubProvider
{
    public static final ResourceKey<LootTable> DUNGEON_CROPS = ResourceKey.create(Registries.LOOT_TABLE,
            Identifier.fromNamespaceAndPath(MOD_ID, "crops/dungeon"));

    private final HolderLookup.Provider provider;

    public DefaultCropsLootProvider(@NotNull final HolderLookup.Provider provider)
    {
        this.provider = provider;
    }

    @Override
    public void generate(@NotNull final BiConsumer<ResourceKey<LootTable>, LootTable.Builder> generator)
    {
        // 26.2: ModLootConditions' constants became methods -- ItemPredicate.Builder#of needs an item lookup now.
        final HolderGetter<Item> items = provider.lookupOrThrow(Registries.ITEM);

        final Map<Identifier, List<MinecoloniesCropBlock>> cropDrops = new HashMap<>();
        for (final MinecoloniesCropBlock crop : ModBlocks.getCrops())
        {
            for (final Block source : crop.getDroppedFrom())
            {
                source.getLootTable().ifPresent(table -> cropDrops.computeIfAbsent(table.identifier(), t -> new ArrayList<>()).add(crop));
            }
        }

        for (final Map.Entry<Identifier, List<MinecoloniesCropBlock>> entry : cropDrops.entrySet())
        {
            // grass blocks have a lot of crops (both MineColonies and vanilla) so the base drop chance is reduced
            final float chance = entry.getKey().equals(Blocks.SHORT_GRASS.getLootTable().map(ResourceKey::identifier).orElse(null)) ? 0.001f : 0.01f;

            final LootTable.Builder table = LootTable.lootTable();
            for (final MinecoloniesCropBlock crop : entry.getValue())
            {
                final LootPool.Builder pool = LootPool.lootPool();
                if (crop.getPreferredBiome() != null)
                {
                    pool.when(EntityInBiomeTag.of(crop.getPreferredBiome()));
                }
                pool.when(ModLootConditions.doesNotHaveShearsOrSilkTouch(provider));

                pool.add(AlternativesEntry.alternatives()
                        .otherwise(LootItem.lootTableItem(crop)
                                .when(ModLootConditions.hasNetheriteHoe(items))
                                .when(LootItemRandomChanceCondition.randomChance(chance * 4f)))
                        .otherwise(LootItem.lootTableItem(crop)
                                .when(ModLootConditions.hasDiamondHoe(items))
                                .when(LootItemRandomChanceCondition.randomChance(chance * 3.5f)))
                        .otherwise(LootItem.lootTableItem(crop)
                                .when(ModLootConditions.hasIronHoe(items))
                                .when(LootItemRandomChanceCondition.randomChance(chance * 3f)))
                        .otherwise(LootItem.lootTableItem(crop)
                                .when(ModLootConditions.hasGoldenHoe(items))
                                .when(LootItemRandomChanceCondition.randomChance(chance * 2.5f)))
                        .otherwise(LootItem.lootTableItem(crop)
                                .when(ModLootConditions.hasHoe(items)
                                        .and(ModLootConditions.hasNetheriteHoe(items).invert())
                                        .and(ModLootConditions.hasDiamondHoe(items).invert())
                                        .and(ModLootConditions.hasIronHoe(items).invert())
                                        .and(ModLootConditions.hasGoldenHoe(items).invert()))
                                .when(LootItemRandomChanceCondition.randomChance(chance * 2f)))
                        .otherwise(LootItem.lootTableItem(crop)
                                .when(ModLootConditions.hasHoe(items).invert())
                                .when(LootItemRandomChanceCondition.randomChance(chance))));
                table.withPool(pool);
            }
            generator.accept(getCropSourceLootTable(entry.getKey()), table);
        }

        final LootPool.Builder dungeonPool = LootPool.lootPool();
        for (final MinecoloniesCropBlock crop : ModBlocks.getCrops())
        {
            dungeonPool.add(LootItem.lootTableItem(crop)
                    .when(LootItemRandomChanceCondition.randomChance(0.005f)));
        }
        generator.accept(DUNGEON_CROPS, LootTable.lootTable().withPool(dungeonPool));
    }

    /**
     * Generate the loot table key for the crop source block sub-table.
     * @param source the block that (when broken) has a chance to drop crops.
     * @return the loot table key.
     */
    public static ResourceKey<LootTable> getCropSourceLootTable(@NotNull final Identifier source)
    {
        return ResourceKey.create(Registries.LOOT_TABLE,
                Identifier.fromNamespaceAndPath(MOD_ID, "crops/" + source.getPath()));
    }
}
