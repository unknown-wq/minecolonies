package com.minecolonies.core.generation;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

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
     * The sub providers to run.  Kept as the vanilla {@link LootTableProvider.SubProviderEntry} record so that the
     * existing {@code getTables()} implementations did not have to change shape.
     *
     * @return the sub providers.
     */
    @NotNull
    public abstract List<LootTableProvider.SubProviderEntry> getTables();

    @NotNull
    @Override
    public CompletableFuture<?> run(@NotNull final CachedOutput cache)
    {
        return this.registries.thenCompose(provider ->
        {
            final Map<ResourceKey<LootTable>, LootTable> tables = new LinkedHashMap<>();

            for (final LootTableProvider.SubProviderEntry entry : getTables())
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
     * Helper method to make a loot entry builder for an ItemStack
     * @param stack The loot ItemStack
     * @return A loot entry builder for this stack
     */
    public static LootPoolSingletonContainer.Builder<?> itemStack(@NotNull final ItemStack stack)
    {
        if (!stack.isEmpty())
        {
            final LootPoolSingletonContainer.Builder<?> builder = LootItem.lootTableItem(stack.getItem());
            if (!stack.getComponentsPatch().isEmpty())
            {
                for (final Map.Entry<DataComponentType<?>, Optional<?>> entry : stack.getComponentsPatch().entrySet())
                {
                    entry.getValue().ifPresent(setComponent(entry, builder));
                }
            }
            if (stack.getCount() > 1)
            {
                builder.apply(SetItemCountFunction.setCount(ConstantValue.exactly(stack.getCount())));
            }
            return builder;
        }
        return EmptyLootItem.emptyItem();
    }

    @NotNull
    private static <T> Consumer<T> setComponent(Map.Entry<DataComponentType<?>, Optional<?>> entry, LootPoolSingletonContainer.Builder<?> builder)
    {
        // idk if there's a better way to do this generic ... but SetComponentsFunction is Mojank anyway because there's
        // no method to set multiple components at once, short of hacking the constructor directly.
        return value -> builder.apply(SetComponentsFunction.setComponent((DataComponentType<T>) entry.getKey(), value));
    }
}
