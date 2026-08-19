package com.minecolonies.core.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * This is a HolderLookup.Provider that's populated on-demand during datagen, so that we
 * can look up loot tables for {@link com.minecolonies.core.colony.crafting.LootTableAnalyzer}.
 *
 * <p>Port note (26.2 / Fabric): the previous implementation asked NeoForge's {@code ExistingFileHelper} for the
 * loot table JSON. Fabric has no equivalent, so the lookup now reads straight out of the vanilla data pack
 * ({@link ServerPacksSource#createVanillaPackSource()}). That covers exactly the tables the analyzer can reach today
 * — the fisherman tables wrap {@code minecraft:gameplay/fishing/*} — and nothing else was ever loaded from the
 * NeoForge pack either. Mod-supplied loot tables are <b>not</b> visible here; if a future datagen table nests a
 * reference to another MineColonies table, that reference resolves to empty and the analyzed drop list comes out
 * short (the failure is logged by {@code LootTableAnalyzer}).</p>
 *
 * <p>Port note 2: 26.2 renamed {@code Registry#getHolder} to {@code get} and {@code HolderLookup.Provider}'s
 * {@code listRegistries} to {@code listRegistryKeys}, so the delegating shape below reads a little differently from
 * the 1.21.1 original.</p>
 */
public class DatagenLootTableManager implements HolderLookup.Provider
{
    private final HolderLookup.Provider  baseProvider;
    private final MappedRegistry<LootTable> registry =
      new MappedRegistry<>(Registries.LOOT_TABLE, Lifecycle.stable());
    private final DynamicLoadingLookup    lookup    = new DynamicLoadingLookup();

    private ResourceManager resources;

    public DatagenLootTableManager(@NotNull final HolderLookup.Provider baseProvider)
    {
        this.baseProvider = baseProvider;
    }

    @NotNull
    @Override
    public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys()
    {
        return baseProvider.listRegistryKeys();
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(@NotNull final ResourceKey<? extends Registry<? extends T>> registryId)
    {
        if (registryId.equals(Registries.LOOT_TABLE))
        {
            return Optional.of((HolderLookup.RegistryLookup<T>) lookup);
        }

        return baseProvider.lookup(registryId);
    }

    @NotNull
    @Override
    public <V> RegistryOps<V> createSerializationContext(@NotNull final DynamicOps<V> ops)
    {
        return baseProvider.createSerializationContext(ops);
    }

    /**
     * The vanilla server data pack, used to read loot tables (and, in {@link ItemNbtCalculator}, tags) that the
     * datagen registries do not contain.
     *
     * @return a resource manager over the vanilla data pack only.
     */
    @NotNull
    public static ResourceManager vanillaServerData()
    {
        final List<PackResources> packs = List.of(ServerPacksSource.createVanillaPackSource());
        return new MultiPackResourceManager(PackType.SERVER_DATA, packs);
    }

    /**
     * A registry lookup that will try to dynamically load the corresponding JSON file if not already found.
     * It's intended for use during datagen for registries that are not populated by default.
     */
    private class DynamicLoadingLookup implements HolderLookup.RegistryLookup.Delegate<LootTable>
    {
        @NotNull
        @Override
        public HolderLookup.RegistryLookup<LootTable> parent()
        {
            return registry;
        }

        @NotNull
        @Override
        public Optional<Holder.Reference<LootTable>> get(@NotNull final ResourceKey<LootTable> id)
        {
            final Optional<Holder.Reference<LootTable>> existing = registry.get(id);
            if (existing.isPresent())
            {
                return existing;
            }

            return dynamicLoad(id).map(table -> registry.register(id, table, RegistrationInfo.BUILT_IN));
        }
    }

    private Optional<LootTable> dynamicLoad(@NotNull final ResourceKey<LootTable> id)
    {
        try
        {
            if (resources == null)
            {
                resources = vanillaServerData();
            }

            final String dir = Registries.elementsDirPath(Registries.LOOT_TABLE);
            final Optional<Resource> resource =
              resources.getResource(id.identifier().withPath(path -> dir + "/" + path + ".json"));
            if (resource.isEmpty())
            {
                return Optional.empty();
            }

            final DynamicOps<JsonElement> ops = createSerializationContext(JsonOps.INSTANCE);
            try (final var reader = resource.get().openAsReader())
            {
                final JsonElement json = JsonParser.parseReader(reader);
                return Optional.of(LootTable.DIRECT_CODEC.decode(ops, json).getOrThrow().getFirst());
            }
        }
        catch (Throwable e)
        {
            e.printStackTrace();
            return Optional.empty();
        }
    }
}
