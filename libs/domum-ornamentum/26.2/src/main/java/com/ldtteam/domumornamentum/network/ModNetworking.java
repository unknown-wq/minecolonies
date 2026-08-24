package com.ldtteam.domumornamentum.network;

import com.ldtteam.domumornamentum.network.messages.CreativeSetArchitectCutterSlotMessage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * Networking entry point (port contract C3).
 * <p>
 * {@link #register()} is invoked from the common mod initializer, {@link #registerClient()} from the
 * client initializer. Everything that touches client-only classes lives in
 * {@link ModClientNetworking}, which is only ever class-loaded through {@link #registerClient()} —
 * a dedicated server never resolves it.
 */
public final class ModNetworking
{
    /**
     * Replacement for NeoForge's {@code ServerLifecycleHooks.getCurrentServer()}: Fabric has no
     * static accessor, so the running server is captured from the lifecycle events instead.
     */
    private static @Nullable MinecraftServer currentServer = null;

    private ModNetworking()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModNetworking. This is a utility class");
    }

    public static void register()
    {
        PayloadTypeRegistry.serverboundPlay().register(CreativeSetArchitectCutterSlotMessage.ID, CreativeSetArchitectCutterSlotMessage.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CreativeSetArchitectCutterSlotMessage.ID,
            (payload, context) -> payload.onExecute(context.player()));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> currentServer = server);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> currentServer = null);
    }

    public static void registerClient()
    {
        ModClientNetworking.register();
    }

    /**
     * @return the currently running server, or null when not in a world.
     */
    static @Nullable MinecraftServer getCurrentServer()
    {
        return currentServer;
    }
}
