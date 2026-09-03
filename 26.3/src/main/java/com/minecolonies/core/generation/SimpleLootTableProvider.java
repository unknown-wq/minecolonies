package com.minecolonies.core.generation;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableSubProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.UniformContainerBase;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Wrapper around the vanilla loot table provider which makes it easier to use.
 * Just override getName and getTables.
 *
 * <p>Port note (26.2 / Fabric): this used to {@code extend LootTableProvider}, and NeoForge patched in a
 * {@code protected void validate(...)} hook that {@code GatherDataHandler.LootTableProviders} overrode with a no-op.
 * In 26.2 the validation is inlined in the private {@code LootTableProvider#run(cache, registries)} as
 * {@code LootDataType.TABLE.runValidation(...)} ({@code /opt/mc-src/net/minecraft/data/loot/LootTableProvider.java:87})
 * and there is no hook left to override. Our tables deliberately reference loot tables that do not exist inside the
 * datagen registry (the fisherman tables wrap {@code minecraft:gameplay/fishing/*}), so the run would abort. The
 * writer half of the vanilla provider is therefore reproduced here verbatim minus the validation — including
 * {@code setRandomSequence}, which Fabric's own {@code FabricLootTableProviderImpl} does <b>not</b> set and whose
 * absence would silently drop the {@code "random_sequence"} key from every generated table.</p>
 */
public abstract class SimpleLootTableProvider implements DataProvider
{
    private final PackOutput.PathProvider                  pathProvider;
    private final CompletableFuture<HolderLookup.Provider> registries;

    protected SimpleLootTableProvider(@NotNull final PackOutput output,
                                      @NotNull final CompletableFuture<HolderLookup.Provider> provider)
    {
        this.pathProvider = output.createRegistryElementsPathProvider(Registries.LOOT_TABLE);
        this.registries = provider;
    }

    /**
     * A single loot table sub provider.
     *
     * <p>Port note (26.3): this used to be {@code net.minecraft.data.loot.LootTableSubProvider}, whose one method was
     * {@code generate(BiConsumer<ResourceKey<LootTable>, LootTable.Builder>)}. In 26.3 loot tables became a real
     * registry bootstrap ({@code LootTableProvider implements SingleRegistryBootstrap<LootTable>}), so vanilla's
     * interface turned into a no-argument {@code run()} that writes into a {@code LootTableSubProvider.Context}
     * handed to the sub provider at construction time
     * ({@code /opt/mc-src-26.3/net/minecraft/data/loot/LootTableSubProvider.java}). That context only exposes
     * {@code HolderGetter}s, but this mod's sub providers need the whole {@code HolderLookup.Provider} — both for
     * {@link com.minecolonies.api.loot.ModLootConditions} (which takes a {@code Provider}) and because the provider
     * they are handed is a {@link DatagenLootTableManager}, whose entire purpose is to resolve loot tables that the
     * datagen registries do not contain. So the old shape is kept as a mod-local interface instead, and only the
     * vanilla sub provider <em>bases</em> ({@code BlockLootSubProvider}, {@code EntityLootSubProvider}) are fed a
     * context, built by {@link #context}.</p>
     */
    @FunctionalInterface
    public interface SubProvider
    {
        void generate(@NotNull BiConsumer<ResourceKey<LootTable>, LootTable.Builder> output);
    }

    /**
     * Mod-local replacement for {@code LootTableProvider.SubProviderEntry}; see {@link SubProvider} for why.
     *
     * @param provider constructs the sub provider from the datagen registries.
     * @param paramSet the loot context param set every table of that sub provider is stamped with.
     */
    public record SubProviderEntry(@NotNull Function<HolderLookup.Provider, SubProvider> provider,
                                   @NotNull ContextKeySet paramSet)
    {
    }

    /**
     * The sub providers to run.
     *
     * @return the sub providers.
     */
    @NotNull
    public abstract List<SubProviderEntry> getTables();

    @NotNull
    @Override
    public CompletableFuture<?> run(@NotNull final CachedOutput cache)
    {
        return this.registries.thenCompose(provider ->
        {
            final Map<ResourceKey<LootTable>, LootTable> tables = new LinkedHashMap<>();

            for (final SubProviderEntry entry : getTables())
            {
                entry.provider().apply(provider).generate((id, builder) ->
                {
                    builder.setRandomSequence(id.identifier());
                    if (tables.put(id, builder.setParamSet(entry.paramSet()).build()) != null)
                    {
                        throw new IllegalStateException("Duplicate loot table " + id.identifier());
                    }
                });
            }

            final List<CompletableFuture<?>> futures = new ArrayList<>();
            for (final Map.Entry<ResourceKey<LootTable>, LootTable> entry : tables.entrySet())
            {
                final Path path = this.pathProvider.json(entry.getKey().identifier());
                futures.add(DataProvider.saveStable(cache, provider, LootTable.DIRECT_CODEC, entry.getValue(), path));
            }

            return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        });
    }

    /**
     * Create a loot table resource key.
     * @param id the location.
     * @return the resource key.
     */
    public static ResourceKey<LootTable> table(@NotNull final Identifier id)
    {
        return ResourceKey.create(Registries.LOOT_TABLE, id);
    }

    /**
     * A reference to a loot table that datagen is (or is not) generating.
     *
     * <p>Port note (26.3): every API that took a {@code ResourceKey<LootTable>} now takes a {@code Holder<LootTable>}
     * — {@code NestedLootTable.lootTableReference} above all. During datagen the referenced table usually does not
     * exist in any registry yet (we are in the middle of writing it), so a bound holder cannot be obtained. Vanilla's
     * own bootstrap gets its holders back from {@code Context#accept}; Fabric, which writes json rather than
     * bootstrapping, makes a stand-alone reference instead
     * ({@code net.fabricmc.fabric.impl.datagen.loot.FabricLootTableContext#accept}), and that is what this does.
     * A stand-alone reference serialises as its id, and its owner must be the very same object the serialization
     * {@code RegistryOps} hands out for {@code Registries.LOOT_TABLE} — {@code HolderOwner#canSerialize} is an
     * identity check. That is why {@link DatagenLootTableManager} no longer overrides
     * {@code createSerializationContext}.</p>
     *
     * @param registries the datagen registries.
     * @param key        the table to point at.
     * @return a holder that serialises to that table's id.
     */
    @NotNull
    public static Holder<LootTable> tableRef(@NotNull final HolderLookup.Provider registries,
                                             @NotNull final ResourceKey<LootTable> key)
    {
        return Holder.Reference.createStandAlone(registries.lookupOrThrow(Registries.LOOT_TABLE), key);
    }

    /**
     * Adapts a {@link HolderLookup.Provider} to the {@link LootTableSubProvider.Context} that 26.3's
     * {@code BlockLootSubProvider} / {@code EntityLootSubProvider} constructors now demand, and forwards whatever
     * their {@code run()} pushes into it to a sink of our choosing.
     *
     * <p>Vanilla's sub provider bases stopped taking a {@code HolderLookup.Provider} and stopped having a
     * {@code generate(BiConsumer)}: they now write into this context from {@code run()}. Subclasses that want the
     * tables back as a {@link SubProvider} call {@link #collectInto} and then {@code run()}.</p>
     */
    public static final class CollectingContext implements LootTableSubProvider.Context
    {
        private final HolderLookup.Provider registries;

        private BiConsumer<ResourceKey<LootTable>, LootTable.Builder> sink = (key, value) -> { };

        public CollectingContext(@NotNull final HolderLookup.Provider registries)
        {
            this.registries = registries;
        }

        /**
         * @param sink where tables accepted from here on go.
         */
        public void collectInto(@NotNull final BiConsumer<ResourceKey<LootTable>, LootTable.Builder> sink)
        {
            this.sink = sink;
        }

        @NotNull
        @Override
        public Holder.Reference<LootTable> accept(@NotNull final ResourceKey<LootTable> key,
                                                  @NotNull final LootTable.Builder value)
        {
            this.sink.accept(key, value);
            return Holder.Reference.createStandAlone(this.registries.lookupOrThrow(Registries.LOOT_TABLE), key);
        }

        @NotNull
        @Override
        public <S> HolderGetter<S> lookup(@NotNull final ResourceKey<? extends Registry<? extends S>> key)
        {
            return this.registries.lookupOrThrow(key);
        }

        @NotNull
        @Override
        @SuppressWarnings("deprecation")
        public <S> Stream<Holder.Reference<S>> listContextElements(@NotNull final ResourceKey<? extends Registry<? extends S>> key)
        {
            return this.registries.lookupOrThrow(key).listElements();
        }
    }

    /**
     * @param registries the datagen registries.
     * @return a context backed by them that drops everything accepted until {@link CollectingContext#collectInto} is
     *         called.
     */
    @NotNull
    public static CollectingContext context(@NotNull final HolderLookup.Provider registries)
    {
        return new CollectingContext(registries);
    }

    /**
     * Helper method to make a loot entry builder for an ItemStack
     * @param stack The loot ItemStack
     * @return A loot entry builder for this stack
     */
    public static UniformContainerBase.Builder<?> itemStack(@NotNull final ItemStack stack)
    {
        if (!stack.isEmpty())
        {
            final UniformContainerBase.Builder<?> builder = LootItem.lootTableItem(stack.getItem());
            if (!stack.getComponentsPatch().isEmpty())
            {
                // 26.3: DataComponentPatch no longer exposes its map (entrySet() is gone; the field is private).
                // split() is the replacement view -- added() carries the components the patch sets, removed() the
                // ones it clears ({@code /opt/mc-src-26.3/net/minecraft/core/component/DataComponentPatch.java:173}).
                // Only the set half was ever used here: the old loop skipped every empty Optional.
                for (final TypedDataComponent<?> component : stack.getComponentsPatch().split().added())
                {
                    setComponent(component, builder);
                }
            }
            if (stack.getCount() > 1)
            {
                builder.apply(SetItemCountFunction.setCount(ContextIntProviders.exactly(stack.getCount())));
            }
            return builder;
        }
        return EmptyLootItem.emptyItem();
    }

    private static <T> void setComponent(final TypedDataComponent<T> component, final UniformContainerBase.Builder<?> builder)
    {
        // idk if there's a better way to do this generic ... but SetComponentsFunction is Mojank anyway because there's
        // no method to set multiple components at once, short of hacking the constructor directly.
        builder.apply(SetComponentsFunction.setComponent(component.type(), component.value()));
    }
}
