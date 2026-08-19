package com.minecolonies.core.network.messages.client.colony;

import com.ldtteam.common.network.AbstractClientPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.datalistener.DiseasesListener;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * The message used to synchronize global disease data from a server to a remote client.
 */
public class GlobalDiseaseSyncMessage extends AbstractClientPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forClient(Constants.MOD_ID, "global_disease_sync", GlobalDiseaseSyncMessage::new, true, false);

    /**
     * The buffer with the data.
     */
    private RegistryFriendlyByteBuf buffer;

    /**
     * Add or Update QuestData on the client.
     *
     * @param buf the bytebuffer.
     */
    public GlobalDiseaseSyncMessage(final RegistryFriendlyByteBuf buf)
    {
        super(TYPE);
        this.buffer = new RegistryFriendlyByteBuf(new FriendlyByteBuf(buf.copy()), buf.registryAccess());
    }

    protected GlobalDiseaseSyncMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        buffer = new RegistryFriendlyByteBuf(new FriendlyByteBuf(Unpooled.wrappedBuffer(buf.readByteArray())), buf.registryAccess());
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buffer.resetReaderIndex();
        buf.writeByteArray(buffer.array());
    }

    @Override
    protected void onExecute(final IPayloadContext ctxIn, final Player player)
    {
        DiseasesListener.readGlobalDiseasesPackets(buffer);
    }
}
