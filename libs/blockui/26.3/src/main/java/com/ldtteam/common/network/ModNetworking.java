package com.ldtteam.common.network;

import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.common.config.ConfigSyncManager;
import com.ldtteam.common.util.ServerLifecycleHooks;
import java.util.ArrayList;
import java.util.List;

/**
 * Networking entry point of the shared {@code com.ldtteam.common} layer (port contract K3).
 * <p>
 * {@link #register()} is invoked from the common mod initializer, {@link #registerClient()} from the client
 * initializer. Everything that touches client-only classes lives in {@link ClientNetworkUtils}, which is only
 * ever class-loaded through {@link #registerClient()} or through the body of
 * {@link IServerboundDistributor#sendToServer()} - a dedicated server never resolves it.
 * <p>
 * Shape copied from the reference port {@code com.ldtteam.domumornamentum.network.ModNetworking}.
 */
public final class ModNetworking
{
    /**
     * Clientbound receivers cannot be hooked from common code, so message types registered before the client
     * initializer runs are parked here. Stays empty on a dedicated server.
     */
    private static final List<PlayMessageType<?>> pendingClientReceivers = new ArrayList<>();

    /**
     * True once {@link #registerClient()} has run - later {@link PlayMessageType#register()} calls then hook
     * their clientbound receiver immediately instead of queueing it.
     */
    private static boolean clientBootstrapped = false;

    private ModNetworking()
    {
        throw new IllegalStateException("Tried to initialize: ModNetworking but this is a Utility class.");
    }

    /**
     * Common (both sides) networking bootstrap. Call from {@code ModInitializer#onInitialize()}.
     */
    public static void register()
    {
        ServerLifecycleHooks.init();

        // K4: the server -> client config sync (NeoForge's ConfigTracker). Registers its own payload type, so it
        // has to run here rather than from a dependent mod - and before any of them can build a Configurations.
        ConfigSyncManager.init();
    }

    /**
     * Client-only networking bootstrap. Call from {@code ClientModInitializer#onInitializeClient()}.
     * <p>
     * Fabric runs every mod's common initializer before any client initializer, so all message types declared
     * the normal way (in common init) are already queued by the time this runs.
     */
    public static void registerClient()
    {
        clientBootstrapped = true;
        for (final PlayMessageType<?> type : pendingClientReceivers)
        {
            ClientNetworkUtils.registerReceiver(type);
        }
        pendingClientReceivers.clear();
    }

    /**
     * Hooks (or queues) the clientbound receiver of the given message type.
     *
     * @param type message type with a clientbound handler
     */
    static void hookClientReceiver(final PlayMessageType<?> type)
    {
        if (!BlockUI.isClient())
        {
            return;
        }

        if (clientBootstrapped)
        {
            ClientNetworkUtils.registerReceiver(type);
        }
        else
        {
            pendingClientReceivers.add(type);
        }
    }
}
