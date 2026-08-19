package com.minecolonies.core.generation.defaults;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.minecolonies.core.generation.defaults.DefaultCropsLootProvider.DUNGEON_CROPS;
import static com.minecolonies.core.generation.defaults.DefaultSupplyLootProvider.SUPPLY_CAMP_LT;
import static com.minecolonies.core.generation.defaults.DefaultSupplyLootProvider.SUPPLY_SHIP_LT;

/**
 * TODO(port-26.2): DISABLED — NeoForge global loot modifiers have no Fabric counterpart.
 *
 * <p>This used to be a {@code GlobalLootModifierProvider} writing {@code data/neoforge/loot_modifiers/*.json} plus
 * {@code data/minecolonies/loot_modifiers/**}. Those files are read by NeoForge's own loot-modifier system, which
 * does not exist on Fabric, so generating them would produce dead data. The provider is therefore no longer
 * registered in {@code com.minecolonies.core.generation.MineColoniesDataGenerator}.</p>
 *
 * <p><b>Nothing about the intent is lost:</b> the three modifications it described are kept below as plain
 * constants so that the Fabric replacement — a {@code LootTableEvents.MODIFY} handler, of which
 * {@code com.minecolonies.core.event.EventHandler#onLootTableLoad} is already the beginning — can consume them
 * directly. As of this port that handler only covers the {@link #DUNGEON_CROPS_TABLE} case (crops in
 * {@code minecraft:chests/simple_dungeon}); the per-source crop tables and both supply tables are <b>not</b>
 * injected yet, which means colony crops do not drop from vanilla blocks and supply camps/ships do not appear in
 * vanilla chest loot.</p>
 */
public final class DefaultLootModifiersProvider
{
    private DefaultLootModifiersProvider()
    {
    }

    /**
     * Vanilla block loot tables that should additionally roll
     * {@link DefaultCropsLootProvider#getCropSourceLootTable} for that same source, i.e. "breaking this vanilla
     * block can also drop a MineColonies crop".
     *
     * @return the set of vanilla source tables.
     */
    @NotNull
    public static Set<ResourceKey<LootTable>> cropSourceTables()
    {
        final Set<ResourceKey<LootTable>> cropSources = new HashSet<>();
        for (final MinecoloniesCropBlock crop : ModBlocks.getCrops())
        {
            for (final Block source : crop.getDroppedFrom())
            {
                // 26.2: Block#getLootTable returns Optional now.
                source.getLootTable().ifPresent(cropSources::add);
            }
        }
        return cropSources;
    }

    /**
     * Extra table rolled into {@code minecraft:chests/simple_dungeon}.
     */
    public static final ResourceKey<LootTable> DUNGEON_CROPS_TABLE = DUNGEON_CROPS;

    /**
     * Chest tables that additionally roll {@link #SUPPLY_CAMP_TABLE}, guarded at runtime by
     * {@code com.minecolonies.api.loot.GenerateSupplyLoot}.
     */
    public static final List<ResourceKey<LootTable>> SUPPLY_CAMP_SOURCES = List.of(
            BuiltInLootTables.SPAWN_BONUS_CHEST,
            BuiltInLootTables.SIMPLE_DUNGEON,
            BuiltInLootTables.VILLAGE_CARTOGRAPHER,
            BuiltInLootTables.VILLAGE_MASON,
            BuiltInLootTables.VILLAGE_DESERT_HOUSE,
            BuiltInLootTables.VILLAGE_PLAINS_HOUSE,
            BuiltInLootTables.VILLAGE_TAIGA_HOUSE,
            BuiltInLootTables.VILLAGE_SNOWY_HOUSE,
            BuiltInLootTables.VILLAGE_SAVANNA_HOUSE,
            BuiltInLootTables.ABANDONED_MINESHAFT,
            BuiltInLootTables.STRONGHOLD_LIBRARY,
            BuiltInLootTables.STRONGHOLD_CROSSING,
            BuiltInLootTables.STRONGHOLD_CORRIDOR,
            BuiltInLootTables.DESERT_PYRAMID,
            BuiltInLootTables.JUNGLE_TEMPLE,
            BuiltInLootTables.IGLOO_CHEST,
            BuiltInLootTables.WOODLAND_MANSION,
            BuiltInLootTables.PILLAGER_OUTPOST
    );

    /**
     * Chest tables that additionally roll {@link #SUPPLY_SHIP_TABLE}.
     */
    public static final List<ResourceKey<LootTable>> SUPPLY_SHIP_SOURCES = List.of(
            BuiltInLootTables.UNDERWATER_RUIN_SMALL,
            BuiltInLootTables.UNDERWATER_RUIN_BIG,
            BuiltInLootTables.BURIED_TREASURE,
            BuiltInLootTables.SHIPWRECK_MAP,
            BuiltInLootTables.SHIPWRECK_SUPPLY,
            BuiltInLootTables.SHIPWRECK_TREASURE,
            BuiltInLootTables.VILLAGE_FISHER,
            BuiltInLootTables.VILLAGE_ARMORER,
            BuiltInLootTables.VILLAGE_TEMPLE
    );

    /**
     * The supply camp loot table injected into {@link #SUPPLY_CAMP_SOURCES}.
     */
    public static final ResourceKey<LootTable> SUPPLY_CAMP_TABLE = ResourceKey.create(Registries.LOOT_TABLE, SUPPLY_CAMP_LT);

    /**
     * The supply ship loot table injected into {@link #SUPPLY_SHIP_SOURCES}.
     */
    public static final ResourceKey<LootTable> SUPPLY_SHIP_TABLE = ResourceKey.create(Registries.LOOT_TABLE, SUPPLY_SHIP_LT);
}
