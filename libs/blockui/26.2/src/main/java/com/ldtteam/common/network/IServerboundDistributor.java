package com.ldtteam.common.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * List of possible network targets when sending from client to server.
 */
public interface IServerboundDistributor extends CustomPacketPayload
{
    public default void sendToServer()
    {
        // client-only class, referenced from a method body only - see ClientNetworkUtils javadoc
        ClientNetworkUtils.sendToServer(this);
    }
}
