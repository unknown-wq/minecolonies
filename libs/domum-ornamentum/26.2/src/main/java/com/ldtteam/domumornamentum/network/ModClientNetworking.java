package com.ldtteam.domumornamentum.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client-only half of the networking layer. Never class-loaded on a dedicated server: the only
 * reference to this class sits inside {@link ModNetworking#registerClient()}.
 */
public final class ModClientNetworking
{
    private ModClientNetworking()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModClientNetworking. This is a utility class");
    }

    static void register()
    {
        // No clientbound payloads currently exist; payload types are registered in ModNetworking#register().
    }

    /**
     * Replacement for NeoForge's {@code PacketDistributor.sendToServer(payload)}.
     */
    static void sendToServer(final CustomPacketPayload payload)
    {
        ClientPlayNetworking.send(payload);
    }
}
