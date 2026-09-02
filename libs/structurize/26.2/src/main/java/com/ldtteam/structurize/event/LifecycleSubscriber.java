package com.ldtteam.structurize.event;

import com.ldtteam.structurize.network.messages.*;
import com.ldtteam.structurize.storage.ServerStructurePackLoader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Common lifecycle hooks.
 *
 * <p>Port note: on NeoForge this class was a bundle of {@code @SubscribeEvent} methods on the mod bus —
 * {@code RegisterPayloadHandlersEvent}, {@code FMLLoadCompleteEvent}, {@code FMLDedicatedServerSetupEvent}
 * and {@code GatherDataEvent}. Fabric has no mod bus: {@link #register()} is called straight from
 * {@link com.ldtteam.structurize.Structurize#onInitialize()} and installs the callbacks itself.</p>
 *
 * <ul>
 * <li>Payload registration no longer needs a {@code PayloadRegistrar} nor a protocol version string — every
 * message owns its {@code PlayMessageType} and registers itself.</li>
 * <li>{@code FMLLoadCompleteEvent} was only used for {@code LanguageHandler.setMClanguageLoaded()}, which is
 * client only and moved to {@link ClientLifecycleSubscriber}.</li>
 * <li>Data generation is a separate Fabric entrypoint
 * ({@code com.ldtteam.structurize.datagen.StructurizeDataGenerator}), so {@code onDatagen} is gone.</li>
 * </ul>
 */
public class LifecycleSubscriber
{
    /**
     * Private constructor to hide implicit public one.
     */
    private LifecycleSubscriber()
    {
        /*
         * Intentionally left empty
         */
    }

    /**
     * Installs every common lifecycle callback. Called once from the mod initializer.
     */
    public static void register()
    {
        registerMessages();

        // NeoForge fired FMLDedicatedServerSetupEvent, which never runs for the integrated server; Fabric's
        // SERVER_STARTING runs for both, so the dedicated check is kept explicitly. In single player the
        // packs are loaded by ClientStructurePackLoader and the server side has to stay UNINITIALIZED.
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
        {
            if (server.isDedicatedServer())
            {
                ServerStructurePackLoader.onServerStarting();
            }
        });
    }

    /**
     * Publishes the codecs of all 25 play payloads and installs the serverbound receivers. Clientbound
     * receivers are queued by {@code PlayMessageType#register()} and drained later from the client
     * entrypoint by {@link com.ldtteam.common.network.ModNetworking#registerClient()}.
     */
    private static void registerMessages()
    {
        AbsorbBlockMessage.TYPE.register();
        AddRemoveTagMessage.TYPE.register();
        BlueprintSyncMessage.TYPE.register();
        BuildToolPlacementMessage.TYPE.register();
        ClientBlueprintRequestMessage.TYPE.register();
        FillTopPlaceholderMessage.TYPE.register();
        ItemMiddleMouseMessage.TYPE.register();
        NotifyClientAboutStructurePacksMessage.TYPE.register();
        NotifyServerAboutStructurePacksMessage.TYPE.register();
        OperationHistoryMessage.TYPE.register();
        RemoveBlockMessage.TYPE.register();
        RemoveEntityMessage.TYPE.register();
        ReplaceBlockMessage.TYPE.register();
        SaveScanMessage.TYPE.register();
        ScanOnServerMessage.TYPE.register();
        ScanToolTeleportMessage.TYPE.register();
        SetTagInTool.TYPE.register();
        ShowScanMessage.TYPE.register();
        SyncPreviewCacheToClient.TYPE.register();
        SyncPreviewCacheToServer.TYPE.register();
        SyncSettingsToServer.TYPE.register();
        TransferStructurePackToClient.TYPE.register();
        UndoRedoMessage.TYPE.register();
        UpdateClientRender.TYPE.register();
        UpdateScanToolMessage.TYPE.register();
    }
}
