package com.minecolonies.core.generation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
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

    // Port note (26.3): this used to override createSerializationContext to delegate to baseProvider. It must not
    // any more, for a reason that has nothing to do with loot table *references* (that is settled in
    // DynamicLoadingLookup#canSerialize below) and everything to do with resolving them.
    //
    // In 26.3 the nested references inside a loot table are Holder<LootTable>, so decoding a vanilla table in
    // dynamicLoad() now has to resolve them: RegistryFileCodec#decode calls lookup.get(elementKey) on whatever the
    // ops hands out for Registries.LOOT_TABLE. Delegating to baseProvider would hand out baseProvider's loot table
    // registry, which in datagen is empty -- so any vanilla table that nests another one would fail to decode,
    // dynamicLoad would return empty, and LootTableAnalyzer (sifter/netherworker recipe generation) would silently
    // come up short. Inheriting the default (RegistryOps.create(ops, this)) routes those lookups back through
    // lookup() below, so a nested reference recursively loads the table it points at.

    /**
     * The vanilla server data pack, used to read loot tables (and, in {@link ItemNbtCalculator}, tags) that the
     * datagen registries do not contain.
     *
     * @return a resource manager over the vanilla data pack only.
     */
    @NotNull
    public static ResourceManager vanillaServerData()
    {
        // 26.3: VanillaPackResources no longer implements PackResources; it is a holder for the real pack plus its
        // resource layers, and fullResources() is the PackResources it used to be
        // (/opt/mc-src-26.3/net/minecraft/server/packs/VanillaPackResources.java:28).
        final List<PackResources> packs = List.of(ServerPacksSource.createVanillaPackSource().fullResources());
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

        /**
         * Accept holders that this lookup itself owns.
         *
         * <p>Port note (26.3): loot APIs now take {@code Holder<LootTable>}, and datagen has to point at tables that
         * do not exist in any registry yet, so {@link SimpleLootTableProvider#tableRef} builds stand-alone references
         * owned by <em>this</em> lookup -- it is what {@code createSerializationContext} hands out for
         * {@code Registries.LOOT_TABLE}, so it is the registry set those references belong to.</p>
         *
         * <p>The encoders disagreed. {@code Holder.Reference#canSerializeIn(context)} asks
         * {@code context.canSerialize(this.owner)} ({@code /opt/mc-src-26.3/net/minecraft/core/Holder.java:207-210}),
         * and {@code HolderLookup.RegistryLookup.Delegate} overrides {@code canSerialize} to forward straight to
         * {@code parent()} ({@code HolderLookup.java:130-133}). A delegate is transparent for lookups, but forwarding
         * ownership loses the identity leg of {@code HolderOwner}'s own default ({@code owner == this}): the question
         * reached {@code MappedRegistry}, which is not this object, and every reference we own was rejected with
         * "Element ... is not valid in current registry set" -- {@code RegistryFixedCodec:31}. Restoring that leg is
         * the whole fix; the parent is still consulted for holders the {@link #registry} really owns, which is the
         * behaviour the inherited default already had.</p>
         *
         * @param owner the owner of the holder being serialized.
         * @return whether a holder owned by it belongs to this registry set.
         */
        @Override
        public boolean canSerialize(@NotNull final HolderOwner<LootTable> owner)
        {
            return owner == this || parent().canSerialize(owner);
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
