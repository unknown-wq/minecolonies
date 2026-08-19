package com.minecolonies.core.generation;


// PORT-TODO(structurize): re-checked against the real 26.2 structurize API. The structurize side of this file
// is now `BuiltInRegistries.DATA_COMPONENT_TYPE` filtered by namespace, because `ModDataComponents.REGISTRY`
// (a NeoForge DeferredRegister) no longer exists on either side.

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData.Builder;
import com.minecolonies.api.items.component.ModDataComponents;
import com.minecolonies.api.util.CraftingUtils;
import com.mojang.serialization.DynamicOps;
import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Automatically calculated vanilla level nbts of items.
 */
public class ItemNbtCalculator implements DataProvider
{
    private final PackOutput                               packOutput;
    private final CompletableFuture<HolderLookup.Provider> lookupProvider;

    public ItemNbtCalculator(@NotNull final PackOutput packOutput, final CompletableFuture<HolderLookup.Provider> lookupProvider)
    {
        this.packOutput = packOutput;
        this.lookupProvider = lookupProvider;
    }

    @NotNull
    @Override
    public String getName()
    {
        return "ItemNBTCalculator";
    }

    @NotNull
    @Override
    public CompletableFuture<?> run(@NotNull final CachedOutput cache)
    {
        final ResourceManager serverResources = loadServerData();

        // Force loading some tags, since the creative tabs don't enumerate properly without them.
        // 26.2: minecraft:instrument became a *dynamic* registry (Registries.INSTRUMENT), so it is no longer a
        // BuiltInRegistries entry whose tags we can rebind; CreativeModeTabs#generateInstrumentTypes silently skips
        // goat horns when #minecraft:instrument/goat_horns is unbound.  The one stack that costs us is added by
        // hand below instead -- the NBT key set of a goat horn does not depend on which instrument it holds.
        return lookupProvider
            // Missing tag: 'minecraft:enchantable/fishing' in 'minecraft:item'
            .thenApply(p -> loadRegistryTags(p, serverResources, BuiltInRegistries.ITEM))
            // Missing tag: 'minecraft:blocks_wind_charge_explosions' in 'minecraft:block'
            .thenApply(p -> loadRegistryTags(p, serverResources, BuiltInRegistries.BLOCK))
            // and now the actual calculator
            .thenCompose(provider ->
        {
            final List<ItemStack> allStacks;
            final ImmutableList.Builder<ItemStack> listBuilder = new ImmutableList.Builder<>();
            final CreativeModeTab.ItemDisplayParameters tempDisplayParams = new CreativeModeTab.ItemDisplayParameters(FeatureFlags.REGISTRY.allFlags(), false, provider);

            CraftingUtils.forEachCreativeTabItems(tempDisplayParams, (tab, stacks) ->
            {
                for (final ItemStack item : stacks)
                {
                    if (item.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof IMateriallyTexturedBlock texturedBlock)
                    {
                        final Builder builder = MaterialTextureData.builder();
                        for (final IMateriallyTexturedBlockComponent key : texturedBlock.getComponents())
                        {
                            builder.setComponent(key.getId(), key.getDefault());
                        }
                        final ItemStack copy = item.copy();
                        builder.writeToItemStack(copy);
                        listBuilder.add(copy);
                    }
                    else
                    {
                        listBuilder.add(item);
                    }
                }
            });

            listBuilder.add(Items.FILLED_MAP.getDefaultInstance());
            provider.lookupOrThrow(Registries.INSTRUMENT).listElements().findFirst()
              .ifPresent(instrument -> listBuilder.add(InstrumentItem.create(Items.GOAT_HORN, instrument)));
            allStacks = listBuilder.build();

            final TreeMap<String, Set<String>> keyMapping = new TreeMap<>();
            final Set<DataComponentType<?>> typesToRemove = new ReferenceArraySet<>();

            // We ignore damage in nbt.
            typesToRemove.add(DataComponents.DAMAGE);

            // The following we don't care about matching.
            typesToRemove.add(DataComponents.LORE);
            typesToRemove.add(DataComponents.MAX_STACK_SIZE);
            typesToRemove.add(DataComponents.RARITY);
            typesToRemove.add(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            // 26.2/Fabric: neither ModDataComponents any longer keeps a DeferredRegister of its own entries
            // (`REGISTRY` is gone on both sides), so the mods' components are picked out of the vanilla registry
            // by namespace instead.  Same set, one less indirection.
            for (final Identifier id : BuiltInRegistries.DATA_COMPONENT_TYPE.keySet())
            {
                if (id.getNamespace().equals(MOD_ID) || id.getNamespace().equals("structurize"))
                {
                    final DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
                    if (type != null)
                    {
                        typesToRemove.add(type);
                    }
                }
            }

            for (final ItemStack stack : allStacks)
            {
                final Identifier resourceLocation = stack.typeHolder().unwrapKey().get().identifier();
                final Set<DataComponentType<?>> keys = new ReferenceArraySet<>(stack.getComponents().keySet());

                // 26.2: `ArmorItem` is gone -- armour is data driven now.  The closest equivalent to
                // "this item is a piece of armour and could therefore carry a dyed_color" is an EQUIPPABLE
                // component pointing at an armour slot (this also catches carved pumpkins and mob heads,
                // which is harmless: it only adds one more key to the NBT-matching list).
                final Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
                if (equippable != null && isArmourSlot(equippable.slot()))
                {
                    keys.add(DataComponents.DYED_COLOR);
                }
                if (stack.is(Items.FILLED_MAP))
                {
                    keys.add(DataComponents.MAP_ID);
                }
                if (!stack.isEnchantable())
                {
                    keys.remove(DataComponents.ENCHANTMENTS);
                }
                // 26.2: ItemStack#isRepairable is gone; repairability is the REPAIRABLE data component now.
                if (!stack.has(DataComponents.REPAIRABLE))
                {
                    keys.remove(DataComponents.REPAIR_COST);
                }
                // 26.2: ItemStack#getAttributeModifiers is gone; read the component with its vanilla default.
                if (stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty())
                {
                    keys.remove(DataComponents.ATTRIBUTE_MODIFIERS);
                }

                keys.removeAll(typesToRemove);

                keyMapping.compute(resourceLocation.toString(), (k, keysInMap) -> {
                    if (keysInMap == null)
                    {
                        keysInMap = new TreeSet<>();
                    }

                    for (final DataComponentType<?> type : keys)
                    {
                        keysInMap.add(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type).toString());
                    }

                    return keysInMap;
                });
            }

            final Path path = packOutput.createPathProvider(PackOutput.Target.DATA_PACK, "compatibility").file(Identifier.fromNamespaceAndPath(MOD_ID, "itemnbtmatching"), "json");
            final JsonArray jsonArray = new JsonArray();
            for (final Map.Entry<String, Set<String>> entry : keyMapping.entrySet())
            {
                final JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("item", entry.getKey());

                if (!entry.getValue().isEmpty())
                {
                    final JsonArray subArray = new JsonArray();
                    entry.getValue().forEach(subArray::add);
                    jsonObject.add("checkednbtkeys", subArray);
                }

                jsonArray.add(jsonObject);
            }

            return DataProvider.saveStable(cache, jsonArray, path);
        });
    }

    private static boolean isArmourSlot(@NotNull final EquipmentSlot slot)
    {
        return slot == EquipmentSlot.HEAD
                 || slot == EquipmentSlot.CHEST
                 || slot == EquipmentSlot.LEGS
                 || slot == EquipmentSlot.FEET
                 || slot == EquipmentSlot.BODY;
    }

    /**
     * 26.2/Fabric: NeoForge's ResourcePackLoader is gone and its data pack carried nothing we read here anyway
     * (only vanilla tags are needed: instrument/goat_horns, item/enchantable/fishing and
     * block/blocks_wind_charge_explosions).  Degradation: mod-supplied tags -- ours included -- are not loaded.
     */
    private static ResourceManager loadServerData()
    {
        return DatagenLootTableManager.vanillaServerData();
    }

    @SuppressWarnings("unchecked")
    private static <T> HolderLookup.Provider loadRegistryTags(
            @NotNull final HolderLookup.Provider provider,
            @NotNull final ResourceManager resources,
            @NotNull final Registry<T> registry)
    {
        final ResourceKey<? extends Registry<T>> registryId = registry.key();

        // 26.2: TagManager no longer exists and TagLoader#loadAndBuild was replaced by the static
        // TagLoader#loadTagsForRegistry; Registry#bindTags became prepareTagReload(...).apply().
        final Map<TagKey<T>, List<Holder<T>>> map = TagLoader.loadTagsForRegistry(
                resources,
                registryId,
                (TagLoader.ElementLookup<Holder<T>>) TagLoader.ElementLookup.fromFrozenRegistry(registry));
        registry.prepareTagReload(new TagLoader.LoadResult<>(registryId, map)).apply();

        return new HolderLookup.Provider()
        {
            @NotNull
            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys()
            {
                return provider.listRegistryKeys();
            }

            @NotNull
            @Override
            public <U> Optional<? extends HolderLookup.RegistryLookup<U>> lookup(@NotNull final ResourceKey<? extends Registry<? extends U>> id)
            {
                if (id.equals(registryId))
                {
                    return Optional.of((HolderLookup.RegistryLookup<U>) registry);
                }

                return provider.lookup(id);
            }

            @NotNull
            @Override
            public <V> RegistryOps<V> createSerializationContext(@NotNull final DynamicOps<V> ops)
            {
                return provider.createSerializationContext(ops);
            }
        };
    }
}
