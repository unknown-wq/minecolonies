package com.ldtteam.common.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * Replacement for NeoForge's {@code net.neoforged.neoforge.server.ServerLifecycleHooks}: Fabric has no static
 * accessor for the running server, so it is captured from the lifecycle events instead.
 * <p>
 * Same shape as the reference port, {@code com.ldtteam.domumornamentum.network.ModNetworking#getCurrentServer()}.
 *
 * @see com.ldtteam.common.network.ModNetworking#register()
 */
public final class ServerLifecycleHooks
{
    @Nullable
    private static volatile MinecraftServer currentServer = null;

    private ServerLifecycleHooks()
    {
        throw new IllegalStateException("Tried to initialize: ServerLifecycleHooks but this is a Utility class.");
    }

    /**
     * Hooks the lifecycle events. Called exactly once, from the common mod initializer.
     */
    public static void init()
    {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
    }

    /**
     * @return currently running (integrated or dedicated) server, or null when not in a world
     */
    @Nullable
    public static MinecraftServer getCurrentServer()
    {
        return currentServer;
    }
}
