package com.minecolonies.core.generation.defaults.workers;


// PORT-TODO(structurize): re-checked against the real 26.2 structurize API (ModItems.buildTool).

import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.enchants.ModEnchants;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.research.util.ResearchConstants;
import com.minecolonies.core.generation.CustomRecipeAndLootTableProvider;
import com.minecolonies.core.generation.CustomRecipeProvider.CustomRecipeBuilder;
import com.minecolonies.core.generation.SimpleLootTableProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.BuildingConstants.MODULE_CUSTOM;
import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Datagen for Enchanter
 */
public class DefaultEnchanterCraftingProvider extends CustomRecipeAndLootTableProvider
{
    private final String ENCHANTER = ModJobs.ENCHANTER_ID.getPath();
    private static final int MAX_BUILDING_LEVEL = 5;

    /**
     * The enchanting cost each hut level rolls a book at, as {plain low, plain high, fine low, fine high}.
     *
     * <p>A vanilla enchanting table offers between 1 and 30 depending on how many bookshelves surround it, and the
     * quality of what it produces follows that number: a cost of five gives one weak enchantment, a cost of thirty
     * often gives three at or near their maximum level. These bands walk the same ladder, so a level one enchanter
     * produces roughly what a bare table produces and a level five enchanter what a fully shelved one does. This is
     * the whole of the hut-level progression for books, and it needs no maintenance when vanilla adds an
     * enchantment: the pool draws from the {@code #minecraft:in_enchanting_table} tag.</p>
     */
    private static final int[][] BOOK_LEVELS = {
      {1, 5, 5, 10},
      {3, 10, 10, 16},
      {6, 14, 14, 22},
      {10, 19, 19, 27},
      {15, 24, 24, 30}
    };

    /**
     * Rolls of the bonus pool per hut level. The bonus pool is mostly empty, so these are chances at a second book
     * rather than guaranteed extra books; see {@link #bonusPool(int)}. Ancient tomes are raid-gated and arrive far
     * more slowly than even a level one enchanter can consume them, so hut level is worth much more as yield per
     * tome than as a shorter cycle.
     */
    private static final int[] BONUS_ROLLS = {0, 1, 1, 2, 3};

    /**
     * Weights and qualities of the two main-pool entries.
     *
     * <p>An entry's effective weight is {@code max(floor(weight + quality * luck), 0)}, and the luck a crafter passes
     * to its loot context is the effective level of its primary skill -- Mana for the enchanter -- which is
     * {@code ((raw + 1) * 2) - ((raw + 1) / 10)^2}: 20 at Mana 10, 52 at Mana 30, 75 at Mana 50, 100 at Mana 99.
     * The weights are therefore in the low thousands so that the qualities have room to move them without either
     * entry ever reaching zero. A worker sitting on its hut's mana gate rolls the fine band 30% of the time at hut
     * one, 51% at hut three and 67% at hut five; a worker whose Mana has been pushed to 99 rolls it 83% of the
     * time. This is what the worker's own level buys, and it is separate from what the hut level buys.</p>
     */
    private static final int PLAIN_WEIGHT  = 1000;
    private static final int PLAIN_QUALITY = -8;
    private static final int FINE_WEIGHT   = 200;
    private static final int FINE_QUALITY  = 8;

    /**
     * Weights and qualities of the bonus pool. Same arithmetic as above. At hut five and Mana 50 a bonus roll is
     * empty 50% of the time, an extra book 28% and a treasure book 21%.
     */
    private static final int BONUS_EMPTY_WEIGHT    = 2000;
    private static final int BONUS_EMPTY_QUALITY   = -10;
    private static final int BONUS_BOOK_WEIGHT     = 400;
    private static final int BONUS_BOOK_QUALITY    = 4;
    private static final int TREASURE_WEIGHT       = 30;
    private static final int TREASURE_QUALITY      = 1;

    private final List<LootTable.Builder> levels = new ArrayList<>();
    private HolderLookup.Provider provider;

    public DefaultEnchanterCraftingProvider(@NotNull final PackOutput packOutput, final CompletableFuture<HolderLookup.Provider> lookupProvider)
    {
        super(packOutput, lookupProvider);
    }

    @Override
    protected CompletableFuture<HolderLookup.Provider> generate(@NotNull final HolderLookup.Provider provider)
    {
        this.provider = provider;

        for (int buildingLevel = 1; buildingLevel <= MAX_BUILDING_LEVEL; ++buildingLevel)
        {
            final LootTable.Builder table = LootTable.lootTable().withPool(bookPool(buildingLevel));
            if (BONUS_ROLLS[buildingLevel - 1] > 0)
            {
                table.withPool(bonusPool(buildingLevel));
            }
            levels.add(table);
        }

        return CompletableFuture.completedFuture(provider);
    }

    /**
     * The main pool: exactly one book, rolled the way a vanilla enchanting table rolls one.
     *
     * <p>Two entries, both {@code minecraft:book} passed through {@code minecraft:enchant_with_levels}, differing
     * only in the cost band they roll at. Which of the two wins is decided by the worker's Mana through the entry
     * {@code quality} term, so the hut level sets the ceiling and the worker's skill decides how close to it the
     * colony usually gets. {@code enchant_with_levels} swaps a plain book for an enchanted one on its way out.</p>
     *
     * @param buildingLevel the hut level this table belongs to.
     * @return the pool.
     */
    @NotNull
    private LootPool.Builder bookPool(final int buildingLevel)
    {
        final int[] band = BOOK_LEVELS[buildingLevel - 1];
        return LootPool.lootPool()
                 .setRolls(ContextIntProviders.exactly(1))
                 .add(tableRolledBook(band[0], band[1]).setWeight(PLAIN_WEIGHT).setQuality(PLAIN_QUALITY))
                 .add(tableRolledBook(band[2], band[3]).setWeight(FINE_WEIGHT).setQuality(FINE_QUALITY));
    }

    /**
     * The bonus pool: usually nothing, sometimes a second book, sometimes one of the enchantments a table cannot
     * offer at all.
     *
     * <p>The treasure entries are here because {@code #minecraft:in_enchanting_table} deliberately excludes them, so
     * the main pool can never produce them. That includes this mod's own raider-damage enchantment, which belongs to
     * no vanilla tag. Curses are excluded on purpose: they are a punishment, not a reward, and the tables this pool
     * replaces never offered them either.</p>
     *
     * @param buildingLevel the hut level this table belongs to.
     * @return the pool.
     */
    @NotNull
    private LootPool.Builder bonusPool(final int buildingLevel)
    {
        final int[] band = BOOK_LEVELS[buildingLevel - 1];
        final LootPool.Builder pool = LootPool.lootPool()
                                        .setRolls(ContextIntProviders.exactly(BONUS_ROLLS[buildingLevel - 1]))
                                        .add(EmptyLootItem.emptyItem()
                                               .setWeight(BONUS_EMPTY_WEIGHT)
                                               .setQuality(BONUS_EMPTY_QUALITY))
                                        .add(tableRolledBook(band[0], band[1])
                                               .setWeight(BONUS_BOOK_WEIGHT)
                                               .setQuality(BONUS_BOOK_QUALITY));

        if (buildingLevel >= 3)
        {
            treasure(pool, Enchantments.FROST_WALKER, 2);
            treasure(pool, ModEnchants.raiderDamage, buildingLevel >= 5 ? 2 : 1);
        }
        if (buildingLevel >= 4)
        {
            treasure(pool, Enchantments.SOUL_SPEED, buildingLevel >= 5 ? 3 : 2);
            treasure(pool, Enchantments.SWIFT_SNEAK, buildingLevel >= 5 ? 3 : 2);
        }
        if (buildingLevel >= 5)
        {
            treasure(pool, Enchantments.MENDING, 1);
        }
        return pool;
    }

    /**
     * Add one fixed treasure book to a pool.
     *
     * @param pool  the pool to add to.
     * @param key   the enchantment.
     * @param level the level of it.
     */
    private void treasure(@NotNull final LootPool.Builder pool, final ResourceKey<Enchantment> key, final int level)
    {
        pool.add(enchantedBook(key, level).setWeight(TREASURE_WEIGHT).setQuality(TREASURE_QUALITY));
    }

    /**
     * One book rolled by vanilla's own table formula, at a cost drawn uniformly from the given band.
     *
     * @param low  the low end of the enchanting cost.
     * @param high the high end of the enchanting cost.
     * @return the entry builder.
     */
    @NotNull
    private UniformContainerBase.Builder<?> tableRolledBook(final int low, final int high)
    {
        final HolderLookup.RegistryLookup<Enchantment> enchantments = provider.lookupOrThrow(Registries.ENCHANTMENT);
        return LootItem.lootTableItem(Items.BOOK)
                 .apply(EnchantWithLevelsFunction.enchantWithLevels(enchantments, ContextIntProviders.between(low, high))
                          .withOptions(enchantments.getOrThrow(EnchantmentTags.IN_ENCHANTING_TABLE)));
    }

    @NotNull
    private UniformContainerBase.Builder<?> enchantedBook(final ResourceKey<Enchantment> key, final int level)
    {
        return SimpleLootTableProvider.itemStack(enchantedBookStack(key, level));
    }

    /**
     * Build one enchanted book as an item stack.
     *
     * @param key   the enchantment.
     * @param level the level of it.
     * @return the stack.
     */
    @NotNull
    private ItemStack enchantedBookStack(final ResourceKey<Enchantment> key, final int level)
    {
        // 26.2: HolderLookup.Provider#holderOrThrow is gone; go through the registry lookup.
        final Holder<Enchantment> enchantment = provider.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        final ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        stack.enchant(enchantment, level);
        return stack;
    }

    @NotNull
    @Override
    public String getName()
    {
        return "EnchanterCraftingProvider";
    }

    @Override
    protected void registerRecipes(@NotNull final Consumer<CustomRecipeBuilder> consumer)
    {
        final List<ItemStorage> tome = Collections.singletonList(new ItemStorage(
                new ItemStack(ModItems.ancientTome), true, true));

        for (int buildingLevel = 1; buildingLevel <= MAX_BUILDING_LEVEL; ++buildingLevel)
        {
            recipe(ENCHANTER, MODULE_CUSTOM, "tome" + buildingLevel)
                    .minBuildingLevel(buildingLevel)
                    .maxBuildingLevel(buildingLevel)
                    .inputs(tome)
                    .secondaryOutputs(Collections.singletonList(new ItemStack(Items.ENCHANTED_BOOK)))
                    .lootTable(Identifier.fromNamespaceAndPath(MOD_ID, "recipes/" + ENCHANTER + buildingLevel))
                    .build(consumer);
        }

        // A sink for the pile of books the tables produce, and the first thing a level four enchanter can do that a
        // level three one cannot. Enchanted books are otherwise consumed by exactly one research, once; they do not
        // stack against each other, so every one made costs a rack slot forever. The ignore-damage/ignore-NBT flags
        // on the input are what make any book match, the same way the ancient tome input already does.
        recipe(ENCHANTER, MODULE_CUSTOM, "xp_bottles")
          .inputs(List.of(new ItemStorage(new ItemStack(Items.ENCHANTED_BOOK), true, true),
            new ItemStorage(new ItemStack(Items.GLASS_BOTTLE, 3))))
          .result(new ItemStack(Items.EXPERIENCE_BOTTLE, 3))
          .minBuildingLevel(4)
          .showTooltip(true)
          .build(consumer);

        // Two books the player can order by name instead of waiting for the table to roll them. Both cost several
        // ancient tomes, which is the scarce input, so they trade a good chance at several random books for a
        // certainty of one specific book. Neither is reachable below hut level five, and neither can come out of the
        // enchanting-table pool at all: raider damage belongs to no vanilla tag and Mending is treasure.
        recipe(ENCHANTER, MODULE_CUSTOM, "raider_bane_book")
          .inputs(List.of(new ItemStorage(new ItemStack(ModItems.ancientTome, 2), true, true),
            new ItemStorage(new ItemStack(Items.LAPIS_LAZULI, 16)),
            new ItemStorage(new ItemStack(Items.BOOK))))
          .result(enchantedBookStack(ModEnchants.raiderDamage, 2))
          .minBuildingLevel(5)
          .showTooltip(true)
          .build(consumer);

        recipe(ENCHANTER, MODULE_CUSTOM, "mending_book")
          .inputs(List.of(new ItemStorage(new ItemStack(ModItems.ancientTome, 3), true, true),
            new ItemStorage(new ItemStack(Items.LAPIS_LAZULI, 24)),
            new ItemStorage(new ItemStack(Items.BOOK))))
          .result(enchantedBookStack(Enchantments.MENDING, 1))
          .minBuildingLevel(5)
          .showTooltip(true)
          .build(consumer);

        recipe(ENCHANTER, MODULE_CUSTOM, "scroll_tp")
                .inputs(List.of(new ItemStorage(new ItemStack(Items.PAPER, 3)),
                        new ItemStorage(new ItemStack(Items.COMPASS)),
                        new ItemStorage(new ItemStack(com.ldtteam.structurize.items.ModItems.buildTool.get()))))
                .result(new ItemStack(ModItems.scrollColonyTP, 3))
                .showTooltip(true)
                .build(consumer);

        recipe(ENCHANTER, MODULE_CUSTOM, "scroll_area_tp")
                .inputs(List.of(new ItemStorage(new ItemStack(ModItems.scrollColonyTP, 3))))
                .result(new ItemStack(ModItems.scrollColonyAreaTP))
                .minBuildingLevel(2)
                .showTooltip(true)
                .build(consumer);

        recipe(ENCHANTER, MODULE_CUSTOM, "scroll_guard_help")
                .inputs(List.of(new ItemStorage(new ItemStack(ModItems.scrollColonyTP)),
                        new ItemStorage(new ItemStack(Items.LAPIS_LAZULI, 5)),
                        new ItemStorage(new ItemStack(Items.ENDER_PEARL)),
                        new ItemStorage(new ItemStack(Items.PAPER))))
                .result(new ItemStack(ModItems.scrollGuardHelp, 2))
                .minBuildingLevel(3)
                .minResearchId(ResearchConstants.MORE_SCROLLS)
                .showTooltip(true)
                .build(consumer);

        recipe(ENCHANTER, MODULE_CUSTOM, "scroll_highlight")
                .inputs(List.of(new ItemStorage(new ItemStack(ModItems.scrollColonyTP, 3)),
                        new ItemStorage(new ItemStack(Items.GLOWSTONE_DUST, 6)),
                        new ItemStorage(new ItemStack(Items.PAPER, 2))))
                .result(new ItemStack(ModItems.scrollHighLight, 5))
                .minBuildingLevel(3)
                .minResearchId(ResearchConstants.MORE_SCROLLS)
                .showTooltip(true)
                .build(consumer);
    }

    @NotNull
    @Override
    protected List<SimpleLootTableProvider.SubProviderEntry> registerTables()
    {
        return List.of(new SimpleLootTableProvider.SubProviderEntry(provider -> builder ->
        {
            for (int i = 0; i < levels.size(); i++)
            {
                final int buildingLevel = i + 1;
                builder.accept(table(Identifier.fromNamespaceAndPath(MOD_ID, "recipes/" + ENCHANTER + buildingLevel)), levels.get(i));
            }
        }, LootContextParamSets.ALL_PARAMS));
    }
}
