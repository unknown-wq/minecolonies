package com.ldtteam.domumornamentum.event.handlers;

import com.ldtteam.domumornamentum.network.ModNetworking;

/**
 * Common-side mod bus handlers (contract C5).
 *
 * <p>What used to be here, and where it went:</p>
 * <table>
 *   <tr><th>NeoForge 26.1</th><th>Fabric 26.2</th></tr>
 *   <tr>
 *     <td>{@code @SubscribeEvent onNetworkRegistry(RegisterPayloadHandlersEvent)} —
 *         {@code event.registrar(MOD_ID).versioned(modVersion).playToServer(…)}</td>
 *     <td>{@link ModNetworking#register()} (agent B): {@code PayloadTypeRegistry.playC2S().register(…)} +
 *         {@code ServerPlayNetworking.registerGlobalReceiver(…)}. Fabric has no protocol-version handshake
 *         for payloads, so {@code ModList.get()…getVersion()} has no counterpart and is simply gone.</td>
 *   </tr>
 *   <tr>
 *     <td>{@code @SubscribeEvent dataGeneratorSetup(GatherDataEvent)} — 60+ {@code addProvider} calls</td>
 *     <td>{@code com.ldtteam.domumornamentum.datagen.DomumOrnamentumDataGenerator} — the
 *         {@code fabric-datagen} entrypoint. Owned by agent D from here on.</td>
 *   </tr>
 * </table>
 *
 * <p>The {@code @EventBusSubscriber} annotation is gone with the bus; this class is now called explicitly
 * from {@code DomumOrnamentum#onInitialize()}.</p>
 */
public class ModBusEventHandler
{
    private ModBusEventHandler()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModBusEventHandler. This is a utility class");
    }

    /**
     * Called from the common entrypoint, once, at mod initialisation.
     */
    public static void registerCommon()
    {
        ModNetworking.register();
    }
}
