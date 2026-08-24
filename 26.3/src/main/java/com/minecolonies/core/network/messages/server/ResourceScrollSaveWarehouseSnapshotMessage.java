package com.minecolonies.core.network.messages.server;

import com.ldtteam.common.network.AbstractServerPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.items.component.BuildingId;
import com.minecolonies.api.items.component.WarehouseSnapshot;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.items.ItemResourceScroll;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import com.ldtteam.common.network.PlayMessageContext;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Message sent to the server when the client saves a new snapshot by clicking on a warehouse.
 */
public class ResourceScrollSaveWarehouseSnapshotMessage extends AbstractServerPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(Constants.MOD_ID, "resource_scroll_save_warehouse_snapshot", ResourceScrollSaveWarehouseSnapshotMessage::new);

    // 26.3: FriendlyByteBuf#readMap / #writeMap were removed along with the rest of the collection helpers.
    // ByteBufCodecs.map keeps the wire format: a VarInt count, then VarInt-prefixed UTF-8 keys and fixed
    // 4-byte int values -- exactly what writeUtf/writeInt produced.
    private static final StreamCodec<ByteBuf, Map<String, Integer>> SNAPSHOT_CODEC =
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.INT);

    /**
     * The position of the builder.
     */
    private final BlockPos builderPos;

    /**
     * The warehouse snapshot mapping.
     */
    @NotNull
    private final Map<String, Integer> snapshot;

    /**
     * The hash of the current work order (if any).
     */
    @NotNull
    private final String workOrderHash;

    /**
     * Empty constructor used when registering the message.
     */
    public ResourceScrollSaveWarehouseSnapshotMessage(final BlockPos builderPos)
    {
        this(builderPos, Map.of(), "");
    }

    /**
     * Empty constructor used when registering the message.
     */
    public ResourceScrollSaveWarehouseSnapshotMessage(final BlockPos builderPos, @NotNull final Map<String, Integer> snapshot, @NotNull final String workOrderHash)
    {
        super(TYPE);
        this.builderPos = builderPos;
        this.snapshot = snapshot;
        this.workOrderHash = workOrderHash;
    }

    protected ResourceScrollSaveWarehouseSnapshotMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        builderPos = buf.readBoolean() ? buf.readBlockPos() : null;
        snapshot = SNAPSHOT_CODEC.decode(buf);
        workOrderHash = buf.readUtf(32767);
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        buf.writeBoolean(builderPos != null);
        if (builderPos != null)
        {
            buf.writeBlockPos(builderPos);
        }
        SNAPSHOT_CODEC.encode(buf, snapshot);
        buf.writeUtf(workOrderHash);
    }

    @Override
    protected void onExecute(final PlayMessageContext ctxIn, final ServerPlayer player)
    {
        player.getInventory().getNonEquipmentItems().stream()
          .filter(stack -> stack.getItem() instanceof ItemResourceScroll)
          .filter(stack -> Objects.equals(builderPos, BuildingId.readFromItemStack(stack).id()))
          .forEach(stack -> new WarehouseSnapshot(snapshot, workOrderHash).writeToItemStack(stack));
    }
}
