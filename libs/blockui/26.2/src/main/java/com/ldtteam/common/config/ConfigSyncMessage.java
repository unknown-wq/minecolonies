package com.ldtteam.common.config;

import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.common.network.AbstractClientPlayMessage;
import com.ldtteam.common.network.PlayMessageContext;
import com.ldtteam.common.network.PlayMessageType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * Carries one mod's SERVER configuration from the server to a joining client - BlockUI's replacement for the
 * config payload NeoForge's {@code ConfigTracker} sent during login.
 * <p>
 * It is an ordinary {@link AbstractClientPlayMessage} on purpose: that is the layer the dependent mods already
 * speak (MineColonies alone has well over a hundred message types on it), so this feature carries no networking
 * of its own to maintain. The payload is a mod id plus the UTF-8 bytes of a flat TOML document; see
 * {@link ConfigSync} for why that shape, and {@link ConfigSyncManager} for the lifecycle around it.
 */
public class ConfigSyncMessage extends AbstractClientPlayMessage
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigSyncMessage.class);

    /**
     * Registered from {@link ConfigSyncManager#init()}, i.e. from BlockUI's common initializer.
     */
    public static final PlayMessageType<ConfigSyncMessage> TYPE =
        PlayMessageType.forClient(BlockUI.MOD_ID, "config_sync", ConfigSyncMessage::new);

    private static final int MAX_MOD_ID_LENGTH = 256;

    private final String modId;

    /**
     * UTF-8 of the flat TOML document. Kept as bytes rather than a string so the length limit on the wire is a
     * byte count, which is the thing that actually has to be bounded.
     */
    private final byte[] document;

    /**
     * Send site.
     *
     * @param modId    mod whose server config this is
     * @param document UTF-8 of what {@link ConfigSync#encode(java.util.Collection)} produced, already checked
     *                 against {@link ConfigSync#MAX_DOCUMENT_BYTES} by {@link ConfigSyncManager}
     */
    ConfigSyncMessage(final String modId, final byte[] document)
    {
        super(TYPE);
        this.modId = modId;
        this.document = document;
    }

    protected ConfigSyncMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        this.modId = buf.readUtf(MAX_MOD_ID_LENGTH);
        this.document = buf.readByteArray(ConfigSync.MAX_DOCUMENT_BYTES);
    }

    /**
     * @return mod whose server config this carries
     */
    String getModId()
    {
        return modId;
    }

    /**
     * @return the flat TOML document, decoded
     */
    String getDocument()
    {
        return new String(document, StandardCharsets.UTF_8);
    }

    @Override
    protected void toBytes(final RegistryFriendlyByteBuf buf)
    {
        buf.writeUtf(modId, MAX_MOD_ID_LENGTH);
        buf.writeByteArray(document);
    }

    @Override
    protected void onExecute(final PlayMessageContext context, final Player player)
    {
        if (context.server() != null)
        {
            // Singleplayer or a LAN host receiving its own broadcast: the "client" and the "server" are the same
            // JVM and, more to the point, the same ConfigValue objects. There is nothing to apply, and applying
            // it anyway would leave an overlay that goes stale the moment the integrated server edits a value.
            LOGGER.debug("Ignoring the config sync for '{}': integrated server, the values are already ours", modId);
            return;
        }

        ConfigSyncManager.applyOnClient(modId, getDocument());
    }
}
