package com.ldtteam.common.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Client-only half of the networking layer. Never class-loaded on a dedicated server: every reference to it
 * sits inside a method body which only runs on a client ({@link ModNetworking#registerClient()},
 * {@link ModNetworking#hookClientReceiver(PlayMessageType)} and {@link IServerboundDistributor#sendToServer()}).
 * Constant-pool entries are resolved lazily per call site, so a class name in a body is safe while the same
 * name in a field type, a signature or an {@code implements} clause would not be.
 */
final class ClientNetworkUtils
{
    private ClientNetworkUtils()
    {
        throw new IllegalStateException("Tried to initialize: ClientNetworkUtils but this is a Utility class.");
    }

    /**
     * Replacement for NeoForge's {@code ClientPacketDistributor.sendToServer(payload)}.
     */
    static void sendToServer(final CustomPacketPayload payload)
    {
        ClientPlayNetworking.send(payload);
    }

    /**
     * Replacement for {@code PayloadRegistrar#playToClient}'s handler half.
     */
    static <T extends AbstractUnsidedPlayMessage> void registerReceiver(final PlayMessageType<T> type)
    {
        ClientPlayNetworking.registerGlobalReceiver(type.id(), (payload, context) -> type.onClient(payload, new ClientContext(context)));
    }

    /**
     * Adapts Fabric's clientbound receiver context to the common one.
     */
    private record ClientContext(ClientPlayNetworking.Context wrapped) implements PlayMessageContext
    {
        @Override
        @Nullable
        public Player player()
        {
            return wrapped.player();
        }

        @Override
        public PacketFlow flow()
        {
            return PacketFlow.CLIENTBOUND;
        }

        @Override
        @Nullable
        public MinecraftServer server()
        {
            return wrapped.client().getSingleplayerServer();
        }
    }
}
