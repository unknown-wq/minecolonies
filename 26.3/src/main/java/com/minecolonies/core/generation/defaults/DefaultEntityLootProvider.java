package com.minecolonies.core.generation.defaults;

import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.core.generation.SimpleLootTableProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.loot.EntityLootSubProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Loot table generator for entities
 */
public class DefaultEntityLootProvider extends EntityLootSubProvider implements SimpleLootTableProvider.SubProvider
{
    /**
     * The context the base class writes its finished tables into.
     *
     * <p>Port note (26.3): {@code EntityLootSubProvider} no longer has a {@code generate(BiConsumer)} -- the walk
     * over the entity registry moved into {@code run()}, which pushes into the
     * {@code LootTableSubProvider.Context} the constructor was given
     * ({@code /opt/mc-src-26.3/net/minecraft/data/loot/EntityLootSubProvider.java:135-190}). Handing it a collecting
     * context and calling {@code run()} from {@link #generate(BiConsumer)} keeps this provider usable from
     * {@link SimpleLootTableProvider}, which writes json rather than bootstrapping a registry.</p>
     */
    private final SimpleLootTableProvider.CollectingContext context;

    public DefaultEntityLootProvider(@NotNull final HolderLookup.Provider provider)
    {
        this(SimpleLootTableProvider.context(provider));
    }

    private DefaultEntityLootProvider(@NotNull final SimpleLootTableProvider.CollectingContext context)
    {
        // Port note (26.2): the single-FeatureFlagSet constructor sets both `allowed` and `required`, which makes
        // EntityLootSubProvider#run demand a loot table for *every* entity type in the game -- it aborts with
        // "Missing loottable 'minecraft:entities/allay' for 'minecraft:allay'". That is right for vanilla, which
        // generates them all, and wrong for a mod. This is what replaced the removed getKnownEntityTypes()
        // override: allow everything, require nothing.
        super(FeatureFlags.REGISTRY.allFlags(), FeatureFlagSet.of(), context);
        this.context = context;
    }

    @Override
    public void generate(@NotNull final BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output)
    {
        this.context.collectInto(output);
        run();
    }

    @Override
    public void generate()
    {
        registerLoot(ModEntities.AMAZON, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(80))
                .add(LootItem.lootTableItem(Items.BOW).setWeight(15))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(5)));

        registerLoot(ModEntities.AMAZONSPEARMAN, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(80))
                .add(LootItem.lootTableItem(ModItems.spear).setWeight(15))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(5)));

        registerLoot(ModEntities.AMAZONCHIEF, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(80))
                .add(LootItem.lootTableItem(Items.BOW).setWeight(80))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(30)));

        registerLoot(ModEntities.BARBARIAN, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(80))
                .add(LootItem.lootTableItem(Items.DIAMOND_AXE).setWeight(1))
                .add(LootItem.lootTableItem(Items.GOLDEN_AXE).setWeight(2))
                .add(LootItem.lootTableItem(Items.IRON_AXE).setWeight(5))
                .add(LootItem.lootTableItem(Items.STONE_AXE).setWeight(6))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(3)));

        registerLoot(ModEntities.ARCHERBARBARIAN, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(80))
                .add(LootItem.lootTableItem(Items.BOW).setWeight(10))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(5)));

        registerLoot(ModEntities.CHIEFBARBARIAN, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(50))
                .add(LootItem.lootTableItem(ModItems.chiefSword).setWeight(1).setQuality(1))
                .add(LootItem.lootTableItem(Items.DIAMOND_SWORD).setWeight(5))
                .add(LootItem.lootTableItem(Items.GOLDEN_SWORD).setWeight(5))
                .add(LootItem.lootTableItem(Items.IRON_SWORD).setWeight(10))
                .add(LootItem.lootTableItem(Items.STONE_SWORD).setWeight(20))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(30)));

        registerLoot(ModEntities.SHIELDMAIDEN, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(80))
                .add(LootItem.lootTableItem(Items.SHIELD).setWeight(10))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(5)));

        registerLoot(ModEntities.NORSEMEN_ARCHER, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(80))
                .add(LootItem.lootTableItem(Items.BOW).setWeight(10))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(5)));

        registerLoot(ModEntities.NORSEMEN_CHIEF, builder -> builder
                .setRolls(ContextIntProviders.exactly(2))
                .add(EmptyLootItem.emptyItem().setWeight(50))
                .add(LootItem.lootTableItem(Items.LEATHER).setWeight(15).setQuality(5))
                .add(LootItem.lootTableItem(Items.DIAMOND_AXE).setWeight(10).setQuality(1))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(50)));

        registerLoot(ModEntities.PIRATE, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(80))
                .add(LootItem.lootTableItem(ModItems.scimitar).setWeight(6))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(4)));

        registerLoot(ModEntities.ARCHERPIRATE, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(80))
                .add(LootItem.lootTableItem(Items.BOW).setWeight(10))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(5)));

        registerLoot(ModEntities.CHIEFPIRATE, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(50))
                .add(LootItem.lootTableItem(ModItems.pirateHelmet_1).setWeight(5).setQuality(1))
                .add(LootItem.lootTableItem(ModItems.pirateLegs_1).setWeight(5).setQuality(1))
                .add(LootItem.lootTableItem(ModItems.pirateBoots_1).setWeight(5).setQuality(1))
                .add(LootItem.lootTableItem(ModItems.pirateChest_1).setWeight(5).setQuality(1))
                .add(LootItem.lootTableItem(ModItems.pirateHelmet_2).setWeight(5).setQuality(1))
                .add(LootItem.lootTableItem(ModItems.pirateLegs_2).setWeight(5).setQuality(1))
                .add(LootItem.lootTableItem(ModItems.pirateBoots_2).setWeight(5).setQuality(1))
                .add(LootItem.lootTableItem(ModItems.pirateChest_2).setWeight(5).setQuality(1))
                .add(LootItem.lootTableItem(ModItems.scimitar).setWeight(25).setQuality(1))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(30)));

        registerLoot(ModEntities.MUMMY, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(80))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(15)));

        registerLoot(ModEntities.ARCHERMUMMY, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(80))
                .add(LootItem.lootTableItem(Items.BOW).setWeight(10))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(5)));

        registerLoot(ModEntities.PHARAO, builder -> builder
                .add(EmptyLootItem.emptyItem().setWeight(50))
                .add(LootItem.lootTableItem(ModItems.pharaoscepter).setWeight(3).setQuality(1))
                .add(LootItem.lootTableItem(Items.ARROW).setWeight(20)
                        .apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 16)))
                        .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.enchantments, ContextFloatProviders.between(1.0F, 32.0F))))
                .add(LootItem.lootTableItem(ModItems.firearrow).setWeight(10)
                        .apply(SetItemCountFunction.setCount(ContextIntProviders.between(1, 16)))
                        .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.enchantments, ContextFloatProviders.between(1.0F, 32.0F))))
                .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(30)));

        registerLoot(ModEntities.DROWNED_PIRATE, builder -> builder
                                                                 .add(EmptyLootItem.emptyItem().setWeight(80))
                                                                 .add(LootItem.lootTableItem(ModItems.scimitar).setWeight(6))
                                                                 .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(4)));

        registerLoot(ModEntities.DROWNED_ARCHERPIRATE, builder -> builder
                                                                       .add(EmptyLootItem.emptyItem().setWeight(80))
                                                                       .add(LootItem.lootTableItem(Items.BOW).setWeight(10))
                                                                       .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(5)));

        registerLoot(ModEntities.DROWNED_CHIEFPIRATE, builder -> builder
                                                                      .add(EmptyLootItem.emptyItem().setWeight(50))
                                                                      .add(LootItem.lootTableItem(ModItems.pirateHelmet_1).setWeight(5).setQuality(1))
                                                                      .add(LootItem.lootTableItem(ModItems.pirateLegs_1).setWeight(5).setQuality(1))
                                                                      .add(LootItem.lootTableItem(ModItems.pirateBoots_1).setWeight(5).setQuality(1))
                                                                      .add(LootItem.lootTableItem(ModItems.pirateChest_1).setWeight(5).setQuality(1))
                                                                      .add(LootItem.lootTableItem(ModItems.pirateHelmet_2).setWeight(5).setQuality(1))
                                                                      .add(LootItem.lootTableItem(ModItems.pirateLegs_2).setWeight(5).setQuality(1))
                                                                      .add(LootItem.lootTableItem(ModItems.pirateBoots_2).setWeight(5).setQuality(1))
                                                                      .add(LootItem.lootTableItem(ModItems.pirateChest_2).setWeight(5).setQuality(1))
                                                                      .add(LootItem.lootTableItem(ModItems.scimitar).setWeight(25).setQuality(1))
                                                                      .add(LootItem.lootTableItem(ModItems.ancientTome).setWeight(30)));
    }

    private void registerLoot(@NotNull final EntityType<?> entity,
                              @NotNull final Consumer<LootPool.Builder> builder)
    {
        final Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity);

        final LootPool.Builder pool = LootPool.lootPool()
                .setRolls(ContextIntProviders.exactly(1));
        builder.accept(pool);

        // 26.2: EntityType#getDefaultLootTable returns Optional now.
        entity.getDefaultLootTable().ifPresent(table -> add(entity, table, LootTable.lootTable().withPool(pool)));
    }

    // 26.2: EntityLootSubProvider no longer declares getKnownEntityTypes() -- the "did you forget an entity?"
    // check moved to Fabric's FabricEntityLootSubProvider, which we do not extend (see SimpleLootTableProvider).
    // The former list was ModEntities.getRaiders().
}
