package com.ldtteam.structurize.network.messages;

import com.ldtteam.common.network.AbstractPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.client.gui.GuiStubs;
import com.ldtteam.structurize.management.Manager;
import com.ldtteam.structurize.util.ChangeStorage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import com.ldtteam.structurize.api.Tuple;
import net.minecraft.world.entity.player.Player;
import com.ldtteam.common.network.PlayMessageContext;

import java.util.ArrayList;
import java.util.List;

public class OperationHistoryMessage extends AbstractPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forBothSides(Constants.MOD_ID, "operation_history", OperationHistoryMessage::new);

    // TODO(port-26.3): RegistryFriendlyByteBuf#readList / #writeCollection were removed together with the rest
    //  of the FriendlyByteBuf collection helpers. ByteBufCodecs.collection is the replacement and keeps the
    //  wire format identical: a VarInt count followed by the elements, each a VarInt-prefixed UTF-8 string and
    //  a fixed 4-byte int, exactly what writeUtf/writeInt produced before.
    private static final StreamCodec<ByteBuf, List<Tuple<String, Integer>>> OPERATION_IDS_CODEC =
        ByteBufCodecs.collection(ArrayList::new,
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Tuple::getA, ByteBufCodecs.INT, Tuple::getB, Tuple::new));

    /**
     * List of operations and their IDs
     */
    private final List<Tuple<String, Integer>> operationIDs;

    /**
     * Empty constructor used when registering the
     */
    protected OperationHistoryMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        operationIDs = OPERATION_IDS_CODEC.decode(buf);
    }

    public OperationHistoryMessage()
    {
        super(TYPE);
        operationIDs = new ArrayList<>();
    }

    @Override
    protected void toBytes(final RegistryFriendlyByteBuf buf)
    {
        OPERATION_IDS_CODEC.encode(buf, operationIDs);
    }

    @Override
    protected void onClientExecute(final PlayMessageContext context, final Player player)
    {
        GuiStubs.setLastOperations(operationIDs);
    }

    @Override
    protected void onServerExecute(final PlayMessageContext context, final ServerPlayer player)
    {
        final List<ChangeStorage> operations = Manager.getChangeStoragesForPlayer(player.getUUID());
        for (final ChangeStorage storage : operations)
        {
            operationIDs.add(new Tuple<>(storage.getOperation().getString(), storage.getID()));
        }

        this.sendToPlayer(player);        
    }
}
