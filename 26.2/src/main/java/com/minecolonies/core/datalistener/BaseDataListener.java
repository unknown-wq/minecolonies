package com.minecolonies.core.datalistener;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.datalistener.util.*;
import com.minecolonies.core.util.GsonHelper;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;



/**
 * Base json reload listener class that handles part of the heavy lifting across all reload listeners.
 *
 * @param <T> the type of entry.
 */
public abstract class BaseDataListener<T> extends SimpleJsonResourceReloadListener<JsonElement>
{
    /**
     * JSON keys.
     */
    private static final String KEY_REMOVE     = "remove";
    // 26.2/Fabric: NeoForge's ICondition/ConditionalOps datapack-condition system is replaced by
    // fabric-resource-conditions-api-v1, which is the same shape: a list under a well-known key, each
    // entry a registered condition type with a boolean test. The key name changes from
    // "neoforge:conditions" to "fabric:load_conditions".
    private static final String KEY_CONDITIONS = ResourceConditions.CONDITIONS_KEY;

    /**
     * Gson instance
     */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * The visual name in the logs for output regarding the loader.
     */
    private final String name;

    /**
     * All parsed entries.
     */
    private ImmutableMap<Identifier, T> entries = ImmutableMap.of();

    /**
     * Default constructor.
     *
     * @param directory the directory name where to look for json files.
     */
    protected BaseDataListener(final String directory)
    {
        super(DataListenerUtils.JSON_CODEC, DataListenerUtils.dir(directory));
        this.name = StringUtils.capitalize(directory.replaceAll("_", " "));
    }

    @Override
    protected final void apply(
        @NotNull final Map<Identifier, JsonElement> jsonElementMap,
        @NotNull final ResourceManager resourceManager,
        @NotNull final ProfilerFiller profilerFiller)
    {
        Log.getLogger().info("[{} Loader]: Starting reload...", name);

        long start = System.nanoTime();
        final Map<Identifier, T> newEntries = new HashMap<>();
        final Set<RemovalOrder> toRemove = new HashSet<>();
        for (final Map.Entry<Identifier, JsonElement> entry : jsonElementMap.entrySet())
        {
            final Identifier key = entry.getKey();
            if (!entry.getValue().isJsonObject())
            {
                logWarning(key, String.format("Entry is not a JSON object, found %s", entry.getValue().getClass()));
                continue;
            }
            final JsonObject value = entry.getValue().getAsJsonObject();

            try
            {
                toRemove.addAll(getRemovalOrders(key, value));

                final List<ResourceCondition> conditions = getConditions(value);
                if (conditions.stream().allMatch(a -> a.test(null)))
                {
                    final MappingResult<T> result = mapEntry(key, value);
                    if (result.success())
                    {
                        newEntries.put(key, result.item());
                    }
                    else
                    {
                        logWarning(key, result.reason());
                    }
                }
            }
            catch (Exception e)
            {
                Log.getLogger().error("[{} Loader]: Error loading entry with id {}.", name, key, e);
            }
        }

        final Iterator<Identifier> iterator = newEntries.keySet().iterator();
        while (iterator.hasNext())
        {
            final Identifier key = iterator.next();
            final boolean shouldRemove = toRemove.stream().anyMatch(order -> order.test(key));
            if (shouldRemove)
            {
                iterator.remove();
            }
        }

        Log.getLogger().info("[{} Loader]: Finished reloading. {} entries created.", name, newEntries.size());
        Log.getLogger().debug("[{} Loader]: Reloading finished in {} nanoseconds.", name, System.nanoTime() - start);
        entries = ImmutableMap.copyOf(newEntries);
    }

    /**
     * Simple reusable logger.
     *
     * @param key    the resource key.
     * @param reason the failure reason.
     */
    private void logWarning(final Identifier key, final String reason)
    {
        Log.getLogger().warn("[{} Loader]: Problem loading entry with id {}: {}", name, key, reason);
    }

    /**
     * Get the list of removal orders.
     *
     * @param key    the resource key.
     * @param object the resource value.
     * @return the list of removal order instances.
     */
    private List<RemovalOrder> getRemovalOrders(final Identifier key, final JsonObject object)
    {
        final List<RemovalOrder> orders = new ArrayList<>();
        final JsonArray remove = GsonHelper.getAsJsonArray(object, KEY_REMOVE, new JsonArray());
        for (final JsonElement element : remove)
        {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
            {
                final String text = element.getAsString();
                if (text.equals("*"))
                {
                    orders.add(new OtherModsRemovalOrder(key.getNamespace()));
                }
                else if (text.matches("\\w+:\\*"))
                {
                    orders.add(new ModRemovalorder(StringUtils.substringBefore(text, ":")));
                }
                if (Identifier.tryParse(text) != null)
                {
                    orders.add(new SingleEntryRemovalOrder(Identifier.tryParse(text)));
                }
            }
        }
        return orders;
    }

    /**
     * Get the list of conditionals.
     *
     * @param object the resource value.
     * @return the list of condition instances.
     */
    private List<ResourceCondition> getConditions(final JsonObject object)
    {
        if (object.has(KEY_CONDITIONS))
        {
            final DataResult<Pair<List<ResourceCondition>, JsonElement>> parse =
              ResourceCondition.LIST_CODEC.decode(JsonOps.INSTANCE, object.get(KEY_CONDITIONS));
            if (parse.isSuccess())
            {
                return parse.getOrThrow().getFirst();
            }
        }
        return List.of();
    }

    /**
     * Parsed an individual entry from it's input json element.
     *
     * @param key    the resource key.
     * @param object the resource value.
     * @return the mapping result.
     */
    @NotNull
    protected abstract MappingResult<T> mapEntry(final Identifier key, final JsonObject object);

    /**
     * Get all entries for this listener.
     *
     * @return the map of entries.
     */
    public Map<Identifier, T> getEntries()
    {
        return entries;
    }
}
