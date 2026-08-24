package com.ldtteam.common.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Bidirectional message
 */
public abstract class AbstractPlayMessage extends AbstractUnsidedPlayMessage implements
    IClientboundDistributor,
    IServerboundDistributor
{
    /**
     * This constructor should be called from message call site, ie. the code where you instantiate the message to send it to the other side
     *
     * @param type message type
     */
    public AbstractPlayMessage(final PlayMessageType<?> type)
    {
        super(type);
    }

    /**
     * In this constructor you deserialize received network payload. Formerly known as <code>#fromBytes(RegistryFriendlyByteBuf)</code>
     *
     * @param buf received network payload
     * @param type message type
     * @apiNote you can keep this protected to reduce visibility
     */
    protected AbstractPlayMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(type);
    }

    /**
     * Executes message action on main thread.
     *
     * @param context network context
     * @param player  client player which is receiving this packet
     */
    protected abstract void onClientExecute(final PlayMessageContext context, final Player player);

    /**
     * Executes message action on main thread.
     *
     * @param context network context
     * @param player  server player which is receiving this packet
     */
    protected abstract void onServerExecute(final PlayMessageContext context, final ServerPlayer player);
}
