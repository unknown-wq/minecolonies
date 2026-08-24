package com.ldtteam.common.network;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import java.util.function.BiFunction;

/**
 * Class to connect message type with proper sided registration.
 */
public record PlayMessageType<T extends AbstractUnsidedPlayMessage>(Type<T> id,
    StreamCodec<RegistryFriendlyByteBuf, T> codec,
    boolean allowNullPlayer,
    @Nullable PayloadAction<T, Player> client,
    @Nullable PayloadAction<T, ServerPlayer> server)
{
    // =============== MESSAGE VARIANTS ===============

    /**
     * Creates type for Server (sender) -> Client (receiver) message
     */
    public static <T extends AbstractClientPlayMessage> PlayMessageType<T> forClient(final String modId,
        final String messageName,
        final BiFunction<RegistryFriendlyByteBuf, PlayMessageType<T>, T> messageFactory)
    {
        return forClient(modId, messageName, messageFactory, false, false);
    }

    /**
     * Creates type for Client (sender) -> Server (receiver) message
     */
    public static <T extends AbstractServerPlayMessage> PlayMessageType<T> forServer(final String modId,
        final String messageName,
        final BiFunction<RegistryFriendlyByteBuf, PlayMessageType<T>, T> messageFactory)
    {
        return forServer(modId, messageName, messageFactory, false, false);
    }

    /**
     * Creates type for bidirectional message
     */
    public static <T extends AbstractPlayMessage> PlayMessageType<T> forBothSides(final String modId,
        final String messageName,
        final BiFunction<RegistryFriendlyByteBuf, PlayMessageType<T>, T> messageFactory)
    {
        return forBothSides(modId, messageName, messageFactory, false, false);
    }

    /**
     * Creates type for Server (sender) -> Client (receiver) message
     *
     * @param playerNullable         if false then message wont execute without player
     * @param executeOnNetworkThread if true will execute on logical side main thread
     */
    public static <T extends AbstractClientPlayMessage> PlayMessageType<T> forClient(final String modId,
        final String messageName,
        final BiFunction<RegistryFriendlyByteBuf, PlayMessageType<T>, T> messageFactory,
        final boolean playerNullable,
        final boolean executeOnNetworkThread)
    {
        return codecise(new Type<>(Identifier.fromNamespaceAndPath(modId, messageName)),
            messageFactory,
            playerNullable,
            threadRedirect(AbstractClientPlayMessage::onExecute, executeOnNetworkThread),
            null);
    }

    /**
     * Creates type for Client (sender) -> Server (receiver) message
     *
     * @param playerNullable         if false then message wont execute without player
     * @param executeOnNetworkThread if true will execute on logical side main thread
     */
    public static <T extends AbstractServerPlayMessage> PlayMessageType<T> forServer(final String modId,
        final String messageName,
        final BiFunction<RegistryFriendlyByteBuf, PlayMessageType<T>, T> messageFactory,
        final boolean playerNullable,
        final boolean executeOnNetworkThread)
    {
        return codecise(new Type<>(Identifier.fromNamespaceAndPath(modId, messageName)),
            messageFactory,
            playerNullable,
            null,
            threadRedirect(AbstractServerPlayMessage::onExecute, executeOnNetworkThread));
    }

    /**
     * Creates type for bidirectional message
     *
     * @param playerNullable         if false then message wont execute without player
     * @param executeOnNetworkThread if true will execute on logical side main thread
     */
    public static <T extends AbstractPlayMessage> PlayMessageType<T> forBothSides(final String modId,
        final String messageName,
        final BiFunction<RegistryFriendlyByteBuf, PlayMessageType<T>, T> messageFactory,
        final boolean playerNullable,
        final boolean executeOnNetworkThread)
    {
        return codecise(new Type<>(Identifier.fromNamespaceAndPath(modId, messageName)),
            messageFactory,
            playerNullable,
            threadRedirect(AbstractPlayMessage::onClientExecute, executeOnNetworkThread),
            threadRedirect(AbstractPlayMessage::onServerExecute, executeOnNetworkThread));
    }

    // =============== CODEC VARIANTS ===============

    /**
     * Creates type for Server (sender) -> Client (receiver) message
     */
    public static <T extends AbstractClientPlayMessage> PlayMessageType<T> forClient(final String modId,
        final String messageName,
        final StreamCodec<RegistryFriendlyByteBuf, T> codec)
    {
        return forClient(modId, messageName, codec, false, false);
    }

    /**
     * Creates type for Client (sender) -> Server (receiver) message
     */
    public static <T extends AbstractServerPlayMessage> PlayMessageType<T> forServer(final String modId,
        final String messageName,
        final StreamCodec<RegistryFriendlyByteBuf, T> codec)
    {
        return forServer(modId, messageName, codec, false, false);
    }

    /**
     * Creates type for bidirectional message
     */
    public static <T extends AbstractPlayMessage> PlayMessageType<T> forBothSides(final String modId,
        final String messageName,
        final StreamCodec<RegistryFriendlyByteBuf, T> codec)
    {
        return forBothSides(modId, messageName, codec, false, false);
    }

    /**
     * Creates type for Server (sender) -> Client (receiver) message
     *
     * @param playerNullable         if false then message wont execute without player
     * @param executeOnNetworkThread if true will execute on logical side main thread
     */
    public static <T extends AbstractClientPlayMessage> PlayMessageType<T> forClient(final String modId,
        final String messageName,
        final StreamCodec<RegistryFriendlyByteBuf, T> codec,
        final boolean playerNullable,
        final boolean executeOnNetworkThread)
    {
        return new PlayMessageType<>(new Type<>(Identifier.fromNamespaceAndPath(modId, messageName)),
            codec,
            playerNullable,
            threadRedirect(AbstractClientPlayMessage::onExecute, executeOnNetworkThread),
            null);
    }

    /**
     * Creates type for Client (sender) -> Server (receiver) message
     *
     * @param playerNullable         if false then message wont execute without player
     * @param executeOnNetworkThread if true will execute on logical side main thread
     */
    public static <T extends AbstractServerPlayMessage> PlayMessageType<T> forServer(final String modId,
        final String messageName,
        final StreamCodec<RegistryFriendlyByteBuf, T> codec,
        final boolean playerNullable,
        final boolean executeOnNetworkThread)
    {
        return new PlayMessageType<>(new Type<>(Identifier.fromNamespaceAndPath(modId, messageName)),
            codec,
            playerNullable,
            null,
            threadRedirect(AbstractServerPlayMessage::onExecute, executeOnNetworkThread));
    }

    /**
     * Creates type for bidirectional message
     *
     * @param playerNullable         if false then message wont execute without player
     * @param executeOnNetworkThread if true will execute on logical side main thread
     */
    public static <T extends AbstractPlayMessage> PlayMessageType<T> forBothSides(final String modId,
        final String messageName,
        final StreamCodec<RegistryFriendlyByteBuf, T> codec,
        final boolean playerNullable,
        final boolean executeOnNetworkThread)
    {
        return new PlayMessageType<>(new Type<>(Identifier.fromNamespaceAndPath(modId, messageName)),
            codec,
            playerNullable,
            threadRedirect(AbstractPlayMessage::onClientExecute, executeOnNetworkThread),
            threadRedirect(AbstractPlayMessage::onServerExecute, executeOnNetworkThread));
    }

    /**
     * Call this from your common mod initializer:
     *
     * <pre>
     * public void onInitialize()
     * {
     *     // MyMessage extends one of AbstractPlayMessage, AbstractClientPlayMessage, AbstractServerPlayMessage
     *     MyMessage.TYPE.register();
     * }
     * </pre>
     *
     * Fabric has no payload versioning, so the NeoForge {@code registrar(modId).versioned(modVersion)} wrapper
     * is gone entirely rather than ported: a payload sent to a client running a different mod version is no
     * longer rejected by the handshake, it fails while decoding.
     */
    public void register()
    {
        if (server != null)
        {
            PayloadTypeRegistry.serverboundPlay().register(id, codec);
            ServerPlayNetworking.registerGlobalReceiver(id, (payload, context) -> onServer(payload, new ServerContext(context)));
        }

        if (client != null)
        {
            PayloadTypeRegistry.clientboundPlay().register(id, codec);
            ModNetworking.hookClientReceiver(this);
        }
    }

    void onClient(final T payload, final PlayMessageContext context)
    {
        final Player player = context.player();
        if (!allowNullPlayer && player == null)
        {
            wrongPlayerException(context, payload);
            return;
        }
        client.handle(payload, context, player);
    }

    void onServer(final T payload, final PlayMessageContext context)
    {
        final ServerPlayer serverPlayer = context.player() instanceof final ServerPlayer sp ? sp : null;
        if ((!allowNullPlayer && serverPlayer == null))
        {
            wrongPlayerException(context, payload);
            return;
        }
        server.handle(payload, context, serverPlayer);
    }

    /**
     * Fabric dispatches receivers on the logical side's main thread already, so there is nothing to redirect -
     * both branches behave like the old {@code executeOnNetworkThread = false} one. The parameter is kept so
     * existing call sites keep compiling.
     */
    private static <T extends AbstractUnsidedPlayMessage, U extends Player> PayloadAction<T, U> threadRedirect(
        final PayloadAction<T, U> payloadAction,
        final boolean executeOnNetworkThread)
    {
        return payloadAction;
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private static void wrongPlayerException(final PlayMessageContext context, final AbstractUnsidedPlayMessage payload)
    {
        final Player player = context.player();
        LOGGER.warn("Invalid packet received for - " + payload.getClass().getName() +
            " player: " +
            (player == null ? "MISSING" : player.getClass().getName()) +
            " flow: " +
            context.flow().id());
    }

    /**
     * Adapts Fabric's serverbound receiver context to the common one.
     */
    private record ServerContext(ServerPlayNetworking.Context wrapped) implements PlayMessageContext
    {
        @Override
        public Player player()
        {
            return wrapped.player();
        }

        @Override
        public PacketFlow flow()
        {
            return PacketFlow.SERVERBOUND;
        }

        @Override
        public MinecraftServer server()
        {
            return wrapped.server();
        }
    }

    @FunctionalInterface
    private interface PayloadAction<T, U>
    {
        void handle(T payload, PlayMessageContext context, U player);
    }

    /**
     * Redirect our messages to {@link StreamCodec}
     */
    private static <T extends AbstractUnsidedPlayMessage> PlayMessageType<T> codecise(Type<T> id,
        BiFunction<RegistryFriendlyByteBuf, PlayMessageType<T>, T> messageFactory,
        boolean allowNullPlayer,
        @Nullable PayloadAction<T, Player> client,
        @Nullable PayloadAction<T, ServerPlayer> server)
    {
        final MessageStreamCodec<T> codec = new MessageStreamCodec<>(messageFactory);
        final PlayMessageType<T> type = new PlayMessageType<>(id, codec, allowNullPlayer, client, server);
        codec.type = type;
        return type;
    }

    /**
     * Codec for wrapping our messages
     */
    private static class MessageStreamCodec<T extends AbstractUnsidedPlayMessage> implements StreamCodec<RegistryFriendlyByteBuf, T>
    {
        private final BiFunction<RegistryFriendlyByteBuf, PlayMessageType<T>, T> messageFactory;
        private PlayMessageType<T> type;

        private MessageStreamCodec(BiFunction<RegistryFriendlyByteBuf, PlayMessageType<T>, T> messageFactory)
        {
            this.messageFactory = messageFactory;
        }

        @Override
        public T decode(RegistryFriendlyByteBuf buf)
        {
            return messageFactory.apply(buf, type);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, T msg)
        {
            msg.toBytes(buf);
        }
    }
}
