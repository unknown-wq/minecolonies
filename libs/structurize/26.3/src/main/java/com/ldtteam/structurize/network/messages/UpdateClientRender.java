package com.ldtteam.structurize.network.messages;

import com.ldtteam.common.network.AbstractClientPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.ldtteam.structurize.api.constants.Constants;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import com.ldtteam.common.network.PlayMessageContext;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Marks an area of blocks for re-rendering on the client
 */
public class UpdateClientRender extends AbstractClientPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forClient(Constants.MOD_ID, "update_client_render", UpdateClientRender::new);

    /**
     * Position to scan from.
     */
    private final BlockPos from;

    /**
     * Position to scan to.
     */
    private final BlockPos to;

    /**
     * Empty public constructor.
     */
    protected UpdateClientRender(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        this.from = buf.readBlockPos();
        this.to = buf.readBlockPos();
    }

    /**
     * Update the scan tool.
     * @param from the start pos.
     * @param to the end pos.
     */
    public UpdateClientRender(final BlockPos from, final BlockPos to)
    {
        super(TYPE);
        this.from = from;
        this.to = to;
    }

    @Override
    protected void toBytes(final RegistryFriendlyByteBuf buf)
    {
        buf.writeBlockPos(from);
        buf.writeBlockPos(to);
    }

    /**
     * Send this to exactly the players who can see the area, instead of to every player on the server.
     *
     * @param level the level the area is in.
     * @param from  the lower corner.
     * @param to    the upper corner.
     */
    public static void sendFor(final ServerLevel level, final BlockPos from, final BlockPos to)
    {
        final UpdateClientRender message = new UpdateClientRender(from.immutable(), to.immutable());

        // One packet per player, not one per chunk: a player tracking the whole area would otherwise get the
        // same message once for every chunk of it.
        final Set<ServerPlayer> receivers = new LinkedHashSet<>();
        for (int x = SectionPos.blockToSectionCoord(from.getX()); x <= SectionPos.blockToSectionCoord(to.getX()); x++)
        {
            for (int z = SectionPos.blockToSectionCoord(from.getZ()); z <= SectionPos.blockToSectionCoord(to.getZ()); z++)
            {
                receivers.addAll(PlayerLookup.tracking(level, new ChunkPos(x, z)));
            }
        }

        message.sendToPlayer(receivers);
    }

    @SuppressWarnings("resource")
    @Override
    protected void onExecute(final PlayMessageContext context, final Player player)
    {
        // setBlocksDirty walks every block of the box and converts each one to its section, so a large area
        // costs a full triple loop over its volume on the render thread. The sections are all this needs.
        Minecraft.getInstance()
            .levelExtractor
            .setSectionRangeDirty(SectionPos.blockToSectionCoord(from.getX() - 1),
                SectionPos.blockToSectionCoord(from.getY() - 1),
                SectionPos.blockToSectionCoord(from.getZ() - 1),
                SectionPos.blockToSectionCoord(to.getX() + 1),
                SectionPos.blockToSectionCoord(to.getY() + 1),
                SectionPos.blockToSectionCoord(to.getZ() + 1));
    }
}
