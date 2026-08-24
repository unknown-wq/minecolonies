package com.ldtteam.common.config;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one thing {@link ConfigSyncTest} cannot cover: that the payload itself survives a buffer.
 * <p>
 * This only touches {@code FriendlyByteBuf} and {@code Identifier}, neither of which needs the game to be
 * bootstrapped - unlike {@code PlayMessageType#register}, which would want the payload registries and is
 * therefore left to the live run.
 */
class ConfigSyncMessageTest
{
    private static RegistryFriendlyByteBuf buffer()
    {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    @Test
    void thePayloadSurvivesTheWire()
    {
        final String document = """
            raids = false
            gameplay.colonySize = 75
            gameplay.difficulty = "HARD"
            gameplay.dims = ["overworld", "the_nether"]
            motd = "a \\"quoted\\" server"
            """;

        final RegistryFriendlyByteBuf buf = buffer();
        new ConfigSyncMessage("minecolonies", document.getBytes(StandardCharsets.UTF_8)).toBytes(buf);

        final ConfigSyncMessage received = new ConfigSyncMessage(buf, ConfigSyncMessage.TYPE);

        assertEquals(0, buf.readableBytes(), "the decoder must consume exactly what the encoder wrote");
        assertEquals("minecolonies", received.getModId());
        assertEquals(document, received.getDocument());
    }

    @Test
    void aNonAsciiDocumentIsNotMangled()
    {
        final String document = "motd = \"Приветствие — ünïcode\"\n";

        final RegistryFriendlyByteBuf buf = buffer();
        new ConfigSyncMessage("blockui", document.getBytes(StandardCharsets.UTF_8)).toBytes(buf);

        assertEquals(document, new ConfigSyncMessage(buf, ConfigSyncMessage.TYPE).getDocument());
    }
}
