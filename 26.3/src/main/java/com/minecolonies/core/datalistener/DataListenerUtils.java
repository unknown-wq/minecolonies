package com.minecolonies.core.datalistener;

import com.google.gson.JsonElement;
import com.ldtteam.common.util.ServerLifecycleHooks;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ExtraCodecs;
import org.jetbrains.annotations.NotNull;

/**
 * Shared plumbing for the mod's datapack reload listeners.
 * <p>
 * 26.2 changed {@code SimpleJsonResourceReloadListener} in two ways that every listener here runs into:
 * <ul>
 *     <li>it became generic and takes a {@code Codec<T>} plus a {@link FileToIdConverter} instead of a
 *     {@code Gson} plus a directory name. The mod's listeners parse raw JSON themselves, so they keep doing
 *     that and use {@link #JSON_CODEC} (vanilla's own identity codec) to get the {@code JsonElement} back
 *     untouched -- the behaviour is exactly what the {@code Gson} constructor used to give;</li>
 *     <li>{@code getRegistryLookup()} was NeoForge's {@code ContextAwareReloadListener} extension and does not
 *     exist. {@link #registryLookup()} replaces it with the running server's registry access.</li>
 * </ul>
 */
public final class DataListenerUtils
{
    /**
     * Identity codec: hands the parsed {@code JsonElement} straight through, so listeners can keep doing their
     * own manual parsing.
     */
    public static final Codec<JsonElement> JSON_CODEC = ExtraCodecs.JSON;

    /**
     * The registry access of the datapack reload currently being parsed, or {@code null} outside one.
     * Set by {@link DeferredDataListeners} right before it drains the parked apply stages.
     */
    private static volatile HolderLookup.Provider reloadLookup = null;

    private DataListenerUtils()
    {
        throw new IllegalStateException("Tried to initialize: DataListenerUtils but this is a Utility class.");
    }

    /**
     * Build the file lister for a datapack directory, replacing the old {@code (Gson, String)} constructor's
     * second argument.
     *
     * @param directory the directory name under {@code data/<namespace>/}.
     * @return the lister.
     */
    @NotNull
    public static FileToIdConverter dir(final String directory)
    {
        return FileToIdConverter.json(directory);
    }

    /**
     * Publishes the registry access of the reload whose entries are about to be parsed.
     *
     * @param provider the reload's registry access, or {@code null} to forget it.
     */
    public static void setReloadLookup(final HolderLookup.Provider provider)
    {
        reloadLookup = provider;
    }

    /**
     * Replacement for NeoForge's {@code getRegistryLookup()} on reload listeners.
     * <p>
     * Preferred source is the registry access of the reload being parsed, published by
     * {@link DeferredDataListeners} -- that is the composite access of the {@code ReloadableServerResources}
     * that were just built, datapack registries included, which is what NeoForge's
     * {@code ContextAwareReloadListener} injected. It is also the only one that exists during the first world
     * load, where {@code WorldLoader} runs the reload before any {@code MinecraftServer} is constructed.
     * <p>
     * Falls back to the running server and finally to {@link RegistryAccess#EMPTY} rather than throwing, so a
     * lookup outside a reload degrades instead of crashing.
     *
     * @return the registry lookup.
     */
    @NotNull
    public static HolderLookup.Provider registryLookup()
    {
        final HolderLookup.Provider reload = reloadLookup;
        if (reload != null)
        {
            return reload;
        }
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null ? server.registryAccess() : RegistryAccess.EMPTY;
    }
}
