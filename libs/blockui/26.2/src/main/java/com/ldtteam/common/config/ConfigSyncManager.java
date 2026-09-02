package com.ldtteam.common.config;

import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.common.util.ServerLifecycleHooks;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replacement for NeoForge's {@code ConfigTracker} login sync (port contract K4).
 * <p>
 * Every {@link Configurations} tree registers itself here as it is built. When a player joins, the server sends
 * that player one {@link ConfigSyncMessage} per registered mod, carrying that mod's SERVER configuration; the
 * client parks those values on top of its own and drops them again when the connection ends. Which means a
 * client-side {@code XXX.get()} on a SERVER value finally answers with what the server plays by, which is the
 * whole point - MineColonies reads 56 such values, several of which drive client-side display logic.
 * <p>
 * Deviations from {@code ConfigTracker}, all of them deliberate:
 * <ul>
 * <li><b>Play phase, not login phase.</b> NeoForge synced during the login/configuration handshake, so the values
 * were in place before the world appeared. BlockUI's {@link com.ldtteam.common.network.PlayMessageType} layer -
 * which downstream mods already use for everything else, and which this is built on rather than hand-rolled
 * Fabric networking - only registers play payloads. The sync therefore lands a few ticks into the session
 * instead. Nothing can open a colony GUI in that window, but code that reads a SERVER value in the very first
 * client tick would still see the local one.</li>
 * <li><b>No kick on mismatch.</b> NeoForge could refuse the connection when the specs did not line up. Here a key
 * only one side knows is logged and skipped - see {@link ConfigSync} - because a library shared by several mods
 * has no business dropping a player over a config shape.</li>
 * <li><b>Singleplayer is a no-op.</b> Client and integrated server are one JVM sharing the very same
 * {@link ConfigValue} objects, so there is nothing to ship and an overlay would only be able to go stale against
 * the values underneath it. The client half checks {@link com.ldtteam.common.network.PlayMessageContext#server()}
 * and ignores the message. A LAN host still <em>sends</em>, because its guests are genuinely remote.</li>
 * </ul>
 */
public final class ConfigSyncManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigSyncManager.class);

    /**
     * Mod id -&gt; that mod's configuration tree. Concurrent because a mod is free to build its configuration off
     * the main thread during init, while the join handler reads this on the server thread.
     */
    private static final Map<String, Configurations<?, ?, ?>> REGISTERED = new ConcurrentHashMap<>();

    private static volatile boolean initialised = false;

    private ConfigSyncManager()
    {
        throw new IllegalStateException("Tried to initialize: ConfigSyncManager but this is a Utility class.");
    }

    /**
     * Registers the payload and the two lifecycle hooks. Called once, from
     * {@link com.ldtteam.common.network.ModNetworking#register()}, i.e. from BlockUI's common initializer - which
     * Fabric runs before any dependent mod's, so no join can outrun it.
     */
    public static synchronized void init()
    {
        if (initialised)
        {
            return;
        }
        initialised = true;

        ConfigSyncMessage.TYPE.register();

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncTo(handler.player));

        if (BlockUI.isClient())
        {
            // client-only Fabric types live behind this bouncer; a dedicated server never loads the class
            ClientConfigHelper.registerSyncLifecycle(ConfigSyncManager::revertAll);
        }
    }

    /**
     * Called by every {@link Configurations} constructor.
     */
    static void register(final Configurations<?, ?, ?> configurations)
    {
        final String modId = configurations.getModId();
        if (modId == null)
        {
            // nothing bound a mod id, so there is no file and no way to address this tree over the network
            return;
        }

        final Configurations<?, ?, ?> previous = REGISTERED.putIfAbsent(modId, configurations);
        if (previous != null && previous != configurations)
        {
            LOGGER.warn("A second configuration tree was built for '{}'; only the first one takes part in the "
                + "server -> client sync", modId);
        }
    }

    /**
     * Server side: ships every registered mod's SERVER configuration to one player.
     *
     * @param player freshly joined player
     */
    static void syncTo(final ServerPlayer player)
    {
        if (REGISTERED.isEmpty())
        {
            return;
        }

        if (!ServerPlayNetworking.canSend(player, ConfigSyncMessage.TYPE.id()))
        {
            // a vanilla client, or one without BlockUI: sending would disconnect it
            LOGGER.debug("{} cannot receive config syncs, skipping", player.getName().getString());
            return;
        }

        for (final Map.Entry<String, Configurations<?, ?, ?>> entry : REGISTERED.entrySet())
        {
            final byte[] document = documentFor(entry.getValue());
            if (document == null)
            {
                continue;
            }

            try
            {
                new ConfigSyncMessage(entry.getKey(), document).sendToPlayer(player);
            }
            catch (final RuntimeException e)
            {
                // one mod's oversized or unsendable config must not cost the others theirs, nor the login
                LOGGER.error("Could not send the '{}' server config to {}", entry.getKey(), player.getName().getString(), e);
            }
        }
    }

    /**
     * Server side: re-sends one mod's server configuration to everybody, after it was changed at runtime (a
     * command, an admin UI). No-op when no server is running, which is the case on a remote client.
     *
     * @param configurations the tree whose server config changed
     */
    static void resyncToConnectedPlayers(final Configurations<?, ?, ?> configurations)
    {
        final MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        final String modId = configurations.getModId();
        if (server == null || modId == null || !initialised)
        {
            return;
        }

        final byte[] document = documentFor(configurations);
        if (document == null)
        {
            return;
        }

        final Collection<ServerPlayer> players = PlayerLookup.all(server);
        for (final ServerPlayer player : players)
        {
            if (ServerPlayNetworking.canSend(player, ConfigSyncMessage.TYPE.id()))
            {
                new ConfigSyncMessage(modId, document).sendToPlayer(player);
            }
        }
    }

    /**
     * Renders one mod's server config and checks it against the wire limit.
     * <p>
     * The limit is what the receiving side will accept, so an oversized document has to be dropped <em>here</em>:
     * sending it anyway would fail decoding on the client and take the connection with it. A mod that manages to
     * exceed a megabyte of settings loses the sync and gets a loud log line, nothing worse.
     *
     * @param  configurations the tree to render
     * @return                UTF-8 document, or null when there is nothing (or too much) to send
     */
    @Nullable
    private static byte[] documentFor(final Configurations<?, ?, ?> configurations)
    {
        final String document = configurations.snapshotServerConfigForSync();
        if (document == null || document.isEmpty())
        {
            return null;
        }

        final byte[] bytes = document.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ConfigSync.MAX_DOCUMENT_BYTES)
        {
            LOGGER.error("The server config of '{}' is {} bytes, over the {} byte sync limit; clients will keep "
                + "their own values for it", configurations.getModId(), bytes.length, ConfigSync.MAX_DOCUMENT_BYTES);
            return null;
        }
        return bytes;
    }

    /**
     * Client side: hands a received document to the mod it belongs to.
     *
     * @param modId    mod the server sent this for
     * @param document flat TOML, see {@link ConfigSync}
     */
    static void applyOnClient(final String modId, final String document)
    {
        final Configurations<?, ?, ?> configurations = REGISTERED.get(modId);
        if (configurations == null)
        {
            // the server has a mod we do not, or has it under a different id; nothing to do, and certainly not
            // something to disconnect over
            LOGGER.debug("The server sent a config sync for '{}', which is not installed here", modId);
            return;
        }

        configurations.applyServerSync(document);
    }

    /**
     * Client side: drops every server override of every mod. Runs on disconnect - including a crash, a timeout
     * or a kick, all of which Fabric reports through the same event.
     */
    static void revertAll()
    {
        for (final Configurations<?, ?, ?> configurations : REGISTERED.values())
        {
            try
            {
                configurations.revertServerSync();
            }
            catch (final RuntimeException e)
            {
                LOGGER.error("Could not restore the local configuration of '{}'", configurations.getModId(), e);
            }
        }
    }

    /**
     * @param  modId mod id
     * @return       the configuration tree registered for that mod, if any
     */
    @Nullable
    public static Configurations<?, ?, ?> get(final String modId)
    {
        return REGISTERED.get(modId);
    }
}
