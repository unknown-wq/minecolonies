package com.ldtteam.common.config;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Client bouncer class: everything in here touches a client-only Fabric API type, so it must never be loaded on
 * a dedicated server. {@link Configurations} only calls into it behind
 * {@link com.ldtteam.blockui.mod.BlockUI#isClient()}.
 * <p>
 * TODO(port-26.2): the generated config <i>screen</i> is still gone - NeoForge's {@code IConfigScreenFactory} +
 * {@code ConfigurationScreen} have no Fabric counterpart, and with {@code ModConfigSpec} gone (contract K4)
 * there is no spec to generate one from either.
 *
 * <pre>
 * import net.neoforged.fml.ModContainer;
 * import net.neoforged.neoforge.client.gui.ConfigurationScreen;
 * import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
 *
 * public class ClientConfigHelper
 * {
 *     static void registerClient(final ModContainer modContainer)
 *     {
 *         modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
 *     }
 * }
 * </pre>
 */
public class ClientConfigHelper
{
    private ClientConfigHelper()
    {
        // Intentionally left empty.
    }

    /**
     * Flushes the configuration one last time when the client shuts down, so the tail of a settings-screen
     * session survives even if it lands inside the writer's debounce window.
     *
     * @param flush {@link Configurations#saveAll()} of the configuration tree being registered
     */
    static void registerClient(final Runnable flush)
    {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> flush.run());
    }

    /**
     * Hooks the client end of the server -&gt; client config sync: the server's values are dropped again the
     * moment the connection ends, so the player is back on their own settings.
     * <p>
     * {@code DISCONNECT} fires for every way a session can end - a clean quit, a kick, a timeout, a crashed
     * server - which is exactly why the revert hangs off it rather than off anything the disconnecting code has
     * to remember to call. {@code JOIN} additionally clears anything a missed disconnect could have left behind;
     * it runs when the client enters play, i.e. before any sync message for the new server can be handled.
     * <p>
     * Both go through {@code Minecraft#execute}: a disconnect can be reported from the netty thread, and the
     * revert fires config watchers, whose listeners are downstream game code that has every right to expect the
     * client thread. {@code execute} runs the task inline when it is already on that thread, so the JOIN case
     * keeps its ordering against the sync message that follows it.
     *
     * @param revert {@link ConfigSyncManager#revertAll()}
     */
    static void registerSyncLifecycle(final Runnable revert)
    {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(revert));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(revert));
    }
}
