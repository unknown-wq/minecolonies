package com.minecolonies.api.loot;

import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.predicates.DataComponentMatchers;
import net.minecraft.advancements.predicates.EnchantmentPredicate;
import net.minecraft.advancements.predicates.ItemPredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.predicates.DataComponentPredicates;
import net.minecraft.core.component.predicates.EnchantmentsPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.MatchTool;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Supplier;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Container class for registering custom loot conditions.
 * <p>
 * 26.2 removed {@code LootItemConditionType}: {@code BuiltInRegistries.LOOT_CONDITION_TYPE} now holds
 * {@code MapCodec<? extends LootItemCondition>} directly and {@code LootItemCondition#codec()} replaced
 * {@code getType()} (see {@code /opt/mc-src/net/minecraft/world/level/storage/loot/predicates/LootItemCondition.java}
 * and {@code LootItemConditions.java}). The {@code Supplier} field shape is kept per contract C1.
 */
public final class ModLootConditions
{
    public static final Identifier ENTITY_IN_BIOME_TAG_ID = Identifier.fromNamespaceAndPath(MOD_ID, "entity_in_biome_tag");
    public static final Identifier RESEARCH_UNLOCKED_ID = Identifier.fromNamespaceAndPath(MOD_ID, "research_unlocked");
    public static final Identifier GENERATE_SUPPLY_LOOT_ID = Identifier.fromNamespaceAndPath(MOD_ID, "generate_supply_loot");

    public static final Supplier<MapCodec<? extends LootItemCondition>> entityInBiomeTag;
    public static final Supplier<MapCodec<? extends LootItemCondition>> researchUnlocked;
    public static final Supplier<MapCodec<? extends LootItemCondition>> generateSupplyLoot;

    static
    {
        entityInBiomeTag = register(ENTITY_IN_BIOME_TAG_ID, EntityInBiomeTag.CODEC);
        researchUnlocked = register(RESEARCH_UNLOCKED_ID, ResearchUnlocked.CODEC);
        generateSupplyLoot = register(GENERATE_SUPPLY_LOOT_ID, GenerateSupplyLoot.CODEC);
    }

    /**
     * Register one condition codec eagerly (contract C1: the field stays a {@link Supplier}).
     *
     * @param id    the condition id.
     * @param codec its codec.
     * @return a supplier of the registered codec.
     */
    private static Supplier<MapCodec<? extends LootItemCondition>> register(
      @NotNull final Identifier id,
      @NotNull final MapCodec<? extends LootItemCondition> codec)
    {
        final MapCodec<? extends LootItemCondition> value = Registry.register(BuiltInRegistries.LOOT_CONDITION_TYPE, id, codec);
        return () -> value;
    }

    // also some convenience definitions for existing conditions; some stolen from BlockLootSubProvider.
    //
    // These used to be static constants. In 26.2 ItemPredicate.Builder#of takes a HolderGetter<Item>
    // (/opt/mc-src/net/minecraft/advancements/predicates/ItemPredicate.java:43,48), which is only available once
    // registries are loaded, so they became methods. Datagen call sites have to pass their lookup provider.

    /**
     * @param items the lookup to resolve items against.
     * @return a condition matching a shears tool.
     */
    public static LootItemCondition.Builder hasShears(@NotNull final HolderGetter<Item> items)
    {
        return MatchTool.toolMatches(ItemPredicate.Builder.item().of(items, Items.SHEARS));
    }

    /**
     * @param items the lookup to resolve items against.
     * @param item  the exact tool item required.
     * @return a condition matching that tool.
     */
    public static LootItemCondition.Builder hasTool(@NotNull final HolderGetter<Item> items, @NotNull final Item item)
    {
        return MatchTool.toolMatches(ItemPredicate.Builder.item().of(items, item));
    }

    /**
     * @param items the lookup to resolve items against.
     * @return a condition matching any hoe.
     */
    public static LootItemCondition.Builder hasHoe(@NotNull final HolderGetter<Item> items)
    {
        return MatchTool.toolMatches(ItemPredicate.Builder.item().of(items, ItemTags.HOES));
    }

    /**
     * @param items the lookup to resolve items against.
     * @return a condition matching a netherite hoe.
     */
    public static LootItemCondition.Builder hasNetheriteHoe(@NotNull final HolderGetter<Item> items)
    {
        return hasTool(items, Items.NETHERITE_HOE);
    }

    /**
     * @param items the lookup to resolve items against.
     * @return a condition matching a diamond hoe.
     */
    public static LootItemCondition.Builder hasDiamondHoe(@NotNull final HolderGetter<Item> items)
    {
        return hasTool(items, Items.DIAMOND_HOE);
    }

    /**
     * @param items the lookup to resolve items against.
     * @return a condition matching an iron hoe.
     */
    public static LootItemCondition.Builder hasIronHoe(@NotNull final HolderGetter<Item> items)
    {
        return hasTool(items, Items.IRON_HOE);
    }

    /**
     * @param items the lookup to resolve items against.
     * @return a condition matching a golden hoe.
     */
    public static LootItemCondition.Builder hasGoldenHoe(@NotNull final HolderGetter<Item> items)
    {
        return hasTool(items, Items.GOLDEN_HOE);
    }

    /**
     * @param enchantments the enchantment lookup.
     * @return a condition matching a silk touch tool.
     */
    public static LootItemCondition.Builder hasSilkTouch(@NotNull final HolderLookup.RegistryLookup<Enchantment> enchantments)
    {
        return MatchTool.toolMatches(
          ItemPredicate.Builder.item()
            .withComponents(
              DataComponentMatchers.Builder.components()
                .partial(
                  DataComponentPredicates.ENCHANTMENTS,
                  EnchantmentsPredicate.enchantments(
                    List.of(new EnchantmentPredicate(enchantments.getOrThrow(Enchantments.SILK_TOUCH), MinMaxBounds.Ints.atLeast(1)))
                  )
                )
                .build()
            )
        );
    }

    /**
     * @param provider the full registry lookup.
     * @return a condition matching shears or a silk touch tool.
     */
    public static LootItemCondition.Builder hasShearsOrSilkTouch(@NotNull final HolderLookup.Provider provider)
    {
        return hasShears(provider.lookupOrThrow(Registries.ITEM))
                 .or(hasSilkTouch(provider.lookupOrThrow(Registries.ENCHANTMENT)));
    }

    /**
     * @param provider the full registry lookup.
     * @return a condition matching neither shears nor silk touch.
     */
    public static LootItemCondition.Builder doesNotHaveShearsOrSilkTouch(@NotNull final HolderLookup.Provider provider)
    {
        return hasShearsOrSilkTouch(provider).invert();
    }

    public static void init()
    {
        // just for classloading
    }

    private ModLootConditions()
    {
        throw new IllegalStateException("Tried to initialize: ModLootConditions but this is a Utility class.");
    }
}
