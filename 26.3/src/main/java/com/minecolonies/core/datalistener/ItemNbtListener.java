package com.minecolonies.core.datalistener;

import com.google.gson.*;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Log;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Loads and listens to get custom nbt matching rules.
 */
public class ItemNbtListener extends SimpleJsonResourceReloadListener<JsonElement>
{
    /**
     * Gson instance
     */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * Create a new listener.
     */
    public ItemNbtListener()
    {
        super(DataListenerUtils.JSON_CODEC, DataListenerUtils.dir("compatibility"));
    }

    @Override
    protected void apply(final Map<Identifier, JsonElement> jsonElementMap, final @NotNull ResourceManager resourceManager, final @NotNull ProfilerFiller profiler)
    {
        ItemStackUtils.CHECKED_NBT_KEYS.clear();
        for (final Map.Entry<Identifier, JsonElement> entry : jsonElementMap.entrySet())
        {
            // One unreadable file must not cost us the other files' rules. The table was cleared a line ago, so an
            // exception escaping this loop leaves it empty or half-filled -- and it is the table
            // ItemStackUtils#compareItemStacksIgnoreStackSize consults, so losing it silently changes which items
            // the whole mod considers equal. Reported from outside: a modpack where this aborted on the very first
            // file. Whatever goes wrong in one entry, the rest still load.
            try
            {
                tryParse(DataListenerUtils.registryLookup(), entry);
            }
            catch (final Exception e)
            {
                Log.getLogger().warn("Skipping unreadable compatibility file " + entry.getKey(), e);
            }
        }
        Log.getLogger().info("Read " + ItemStackUtils.CHECKED_NBT_KEYS.size() + " items with their nbt keys for compatibility.");
    }

    /**
     * Tries to parse the entry
     *
     * @param entry
     */
    private void tryParse(@NotNull final HolderLookup.Provider provider, final Map.Entry<Identifier, JsonElement> entry)
    {
        // "compatibility" is a generic folder name and this listener reads it from every namespace, not just ours --
        // deliberately, because that is how another mod ships nbt rules for its own items. The cost is that we also
        // get handed files that merely happen to live at data/<their mod>/compatibility/*.json and mean something
        // else entirely. One such file, an object rather than our array, used to take the whole listener down here.
        // Ours is always an array, so anything else is somebody else's file and is not ours to complain about
        // loudly.
        if (!entry.getValue().isJsonArray())
        {
            Log.getLogger().debug("Ignoring " + entry.getKey() + ": not a MineColonies nbt-matching file (expected an array).");
            return;
        }

        for (final JsonElement element : entry.getValue().getAsJsonArray())
        {
            try
            {
                final JsonObject jsonObj = element.getAsJsonObject();
                final Identifier itemLoc = Identifier.parse(jsonObj.get("item").getAsString());
                if (jsonObj.has("checkednbtkeys"))
                {
                    final HashSet<DataComponentType<?>> set = new HashSet<>();
                    final JsonArray jsonArray = jsonObj.getAsJsonArray("checkednbtkeys");
                    for (final JsonElement subElement : jsonArray)
                    {
                        set.add(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(Identifier.parse(subElement.getAsString())));
                    }

                    ItemStackUtils.CHECKED_NBT_KEYS.put(BuiltInRegistries.ITEM.getValue(itemLoc), set);
                }
                else
                {
                    ItemStackUtils.CHECKED_NBT_KEYS.put(BuiltInRegistries.ITEM.getValue(itemLoc), new HashSet<>());
                }
            }
            catch (Exception e)
            {
                Log.getLogger().warn("Could not nbt comparator for:" + entry.getKey(), e);
            }
        }
    }
}
