package com.ldtteam.domumornamentum.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * List of possible network targets when sending from client to server.
 */
public interface IServerboundDistributor extends CustomPacketPayload
{
    /**
     * Only ever called from client code. The reference to {@link ModClientNetworking} lives inside a
     * method body, so a dedicated server never resolves the client-only class behind it.
     */
    public default void sendToServer()
    {
        ModClientNetworking.sendToServer(this);
    }
}
