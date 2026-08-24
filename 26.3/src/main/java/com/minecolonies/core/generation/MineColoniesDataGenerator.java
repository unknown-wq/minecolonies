package com.minecolonies.core.generation;

import com.minecolonies.core.generation.defaults.*;
import com.minecolonies.core.generation.defaults.workers.*;
import com.minecolonies.core.util.SchemFixerUtil;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code fabric-datagen} entrypoint — the Fabric replacement for
 * {@code com.minecolonies.core.event.GatherDataHandler#dataGeneratorSetup(GatherDataEvent)}.
 *
 * <p>Wired up through the {@code fabric-datagen} entrypoint in {@code fabric.mod.json} and
 * {@code fabricApi.configureDataGeneration { client = true }} in {@code build.gradle}. Shape copied from
 * {@code /workspace/domum-ornamentum/26.2/…/datagen/DomumOrnamentumDataGenerator.java}.</p>
 *
 * <p>Port notes:</p>
 * <ul>
 *   <li>NeoForge's {@code DatapackBuiltinEntriesProvider} for the enchantment registry became
 *       {@link #buildRegistry} (which folds our bootstrap into the datagen registry set that Fabric passes to every
 *       provider) plus {@link EnchantmentRegistryProvider}, which writes
 *       {@code data/minecolonies/enchantment/*.json}.</li>
 *   <li>{@code event.includeClient()} / {@code includeServer()} have no counterpart: Fabric decides client vs
 *       server by which entrypoint the generator is listed under, and everything here lives in the single
 *       {@code fabric-datagen} entrypoint. All providers therefore always run, exactly as they did on a
 *       {@code runData} that included both sides.</li>
 *   <li>Two former providers are gone because they generated NeoForge-only data — see
 *       {@link DefaultLootModifiersProvider} and {@link DefaultDataMapsProvider}, which now only carry the data
 *       they used to write so a Fabric runtime hook can pick it up.</li>
 *   <li>Three asset providers are unregistered for a different reason: everything they produced is derived from
 *       the upstream asset tree, which this repository does not carry, and the runtime asset fetch supplies it
 *       instead. See {@link DefaultSoundProvider}, {@link DefaultEntityIconProvider} and
 *       {@link DefaultLanguageProvider}, and the comments at their former registration sites below.</li>
 * </ul>
 */
public class MineColoniesDataGenerator implements DataGeneratorEntrypoint
{
    @Override
    public void buildRegistry(final RegistrySetBuilder builder)
    {
        // was: new RegistrySetBuilder().add(Registries.ENCHANTMENT, DefaultEnchantmentProvider::bootstrap)
        //      wrapped in a DatapackBuiltinEntriesProvider.
        builder.add(Registries.ENCHANTMENT, DefaultEnchantmentProvider::bootstrap);
        // Damage types are written as plain json by DefaultDamageTypeProvider, but they also have to exist in the
        // lookup the tag providers validate against -- see DefaultDamageTypeProvider#bootstrap.
        builder.add(Registries.DAMAGE_TYPE, DefaultDamageTypeProvider::bootstrap);
    }

    @Override
    public void onInitializeDataGenerator(final FabricDataGenerator generator)
    {
        final FabricDataGenerator.Pack pack = generator.createPack();

        // Bind item components before anything generates.
        //
        // Port note (26.2): item components are no longer decided when the item is constructed. Item#<init> only
        // files an initializer into BuiltInRegistries.DATA_COMPONENT_INITIALIZERS, and the only thing that ever
        // runs those is ReloadableServerResources -- i.e. a datapack reload, which never happens in a datagen run.
        // Until they are bound, every `new ItemStack(...)` throws "Components not bound yet" out of
        // Holder$Reference#components, which is fatal here: MineColonies' own crafting model (ItemStorage,
        // IRecipeStorage, the custom recipe providers) is built on real ItemStacks, ~300 construction sites of
        // them, and vanilla's ItemStackTemplate escape hatch only covers vanilla recipe results.
        //
        // Doing it once, here, is the whole fix. It is registered before any provider is added, so it runs first.
        generator.getRegistries().thenAccept(provider ->
          BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(provider)
            .forEach(DataComponentInitializers.PendingComponents::apply));

        // The loot table analyzer resolves nested loot table references while generating the sifter and
        // netherworker recipes; this wraps the registry lookup so those resolve against the vanilla data pack.
        // (Was: enchRegProvider.getRegistryProvider().thenApply(p -> new DatagenLootTableManager(p, fileHelper)).)
        final CompletableFuture<HolderLookup.Provider> registries =
          generator.getRegistries().thenApply(DatagenLootTableManager::new);

        // data/<ns>/enchantment/
        pack.addProvider(EnchantmentRegistryProvider::new);

        // assets/
        //
        // Two asset providers are deliberately NOT registered any more -- the same treatment
        // DefaultLootModifiersProvider and DefaultDataMapsProvider got above, i.e. the class stays, the
        // registration goes. Both of them only ever produced content that is derived from the upstream
        // MineColonies asset tree, which this repository does not carry and never will; the runtime asset
        // fetch downloads the upstream jar and injects it as a resource pack instead
        // (docs/assetfetch/BRIEF.md, phases 2-4).
        //
        //  - DefaultSoundProvider wrote assets/minecolonies/sounds.json. The fetched pack carries upstream's
        //    real sounds.json, and the fetched pack is injected at Position.BOTTOM -- jar files WIN over
        //    fetched files. A sounds.json in our jar would therefore permanently mask the real one, and with
        //    the sound folder absent at build time this provider writes a crippled one (no citizen events at
        //    all: see its citizenSoundFolder() fallback). Shipping nothing is what lets the fetched file
        //    through.
        //  - DefaultEntityIconProvider wrote the 3481 citizen/raider face icons under
        //    assets/minecolonies/textures/entity_icon/. They are cut straight out of the upstream skin
        //    textures, so they are as derivative as the skins are; the fetched jar already contains all 3481
        //    of them.
        pack.addProvider(DefaultItemModelProvider::new);
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultStoriesProvider(output));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new QuestTranslationProvider(output));

        // data/
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultDamageTypeProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultAdvancementsProvider(output, registries));

        final DefaultBlockTagsProvider blockTags =
          pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultBlockTagsProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultItemTagsProvider(output, registries, blockTags));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultEntityTypeTagsProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultDamageTagsProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultBiomeTagsProvider(output, registries));

        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultResearchProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultRecipeProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultRecruitmentItemsProvider(output));

        // workers
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultAlchemistCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultBakerCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultBlacksmithCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultConcreteMixerCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultChefCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultCrusherCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultDyerCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultEnchanterCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultFarmerCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new LootTableProviders(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultFletcherCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultGlassblowerCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultLumberjackCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultMechanicCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultNetherWorkerLootProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultPlanterCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultSawmillCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultSifterCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultStonemasonCraftingProvider(output, registries));
        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new DefaultStoneSmelteryCraftingProvider(output, registries));

        pack.addProvider((FabricPackOutput output, CompletableFuture<HolderLookup.Provider> r) -> new ItemNbtCalculator(output, registries));

        // DefaultLanguageProvider is not registered either, and for a different reason than the two above -- see
        // that class for the full account. In short: en_us.json is no longer assembled here at all. The 357-key
        // port-owned overlay ships verbatim from src/main/resources, upstream's own 3898-key en_us.json arrives
        // with the fetched pack, and the game merges every lang/en_us.json it can see (Language loads the whole
        // resource stack, unlike every other asset), so the two layer instead of masking each other.

        SchemFixerUtil.fixSchematics(registries);
    }

    /**
     * Writes the enchantments that {@link #buildRegistry} contributed.
     *
     * <p>{@code DatapackBuiltinEntriesProvider} has no Fabric counterpart; {@link FabricDynamicRegistryProvider}
     * plus {@code addAll} on our own registry lookup is the direct equivalent, and only writes elements whose id is
     * in this mod's namespace.</p>
     */
    private static final class EnchantmentRegistryProvider extends FabricDynamicRegistryProvider
    {
        EnchantmentRegistryProvider(final FabricPackOutput output, final CompletableFuture<HolderLookup.Provider> registries)
        {
            super(output, registries);
        }

        @Override
        protected void configure(final HolderLookup.Provider registries, final Entries entries)
        {
            entries.addAll(registries.lookupOrThrow(Registries.ENCHANTMENT));
        }

        @NotNull
        @Override
        public String getName()
        {
            return "MineColonies Enchantments";
        }
    }

    /**
     * The loot table sub providers that were bundled into one {@code LootTableProvider} by
     * {@code GatherDataHandler.LootTableProviders}.
     */
    private static final class LootTableProviders extends SimpleLootTableProvider
    {
        LootTableProviders(final FabricPackOutput output, final CompletableFuture<HolderLookup.Provider> registries)
        {
            super(output, registries);
        }

        @NotNull
        @Override
        public String getName()
        {
            return "MineColonies Loot Tables";
        }

        @NotNull
        @Override
        public List<SimpleLootTableProvider.SubProviderEntry> getTables()
        {
            return List.of(
                new SimpleLootTableProvider.SubProviderEntry(DefaultFishermanLootProvider::new, LootContextParamSets.FISHING),
                new SimpleLootTableProvider.SubProviderEntry(DefaultRecipeLootProvider::new, LootContextParamSets.ALL_PARAMS),
                new SimpleLootTableProvider.SubProviderEntry(DefaultSupplyLootProvider::new, LootContextParamSets.CHEST),
                new SimpleLootTableProvider.SubProviderEntry(DefaultCropsLootProvider::new, LootContextParamSets.BLOCK),
                new SimpleLootTableProvider.SubProviderEntry(DefaultEntityLootProvider::new, LootContextParamSets.ENTITY),
                new SimpleLootTableProvider.SubProviderEntry(DefaultBlockLootTableProvider::new, LootContextParamSets.BLOCK),
                new SimpleLootTableProvider.SubProviderEntry(DefaultLuckyOreLootProvider::new, LootContextParamSets.BLOCK)
            );
        }
    }
}
