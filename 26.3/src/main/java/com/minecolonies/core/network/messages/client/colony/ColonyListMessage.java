package com.minecolonies.core.network.messages.client.colony;

import com.ldtteam.common.network.AbstractPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.gui.map.WindowColonyMap;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.ldtteam.common.network.PlayMessageContext;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Add or Update a AbstractBuilding.View to a ColonyView on the client.
 */
public class ColonyListMessage extends AbstractPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forBothSides(Constants.MOD_ID, "colony_list", ColonyListMessage::new);

    /**
     * List of colonies
     */
    private final List<IColony>    colonies;
    private final List<ColonyInfo> colonyInfo;

    // 26.3: FriendlyByteBuf#readList / #writeCollection were removed along with the rest of the collection
    // helpers; ByteBufCodecs is the replacement. The wire format is unchanged -- a VarInt count followed by
    // the elements, each of them the same int / BlockPos / UTF-8 string sequence written before.
    private static final StreamCodec<ByteBuf, ColonyInfo> COLONY_INFO_CODEC = new StreamCodec<>()
    {
        @Override
        public ColonyInfo decode(final ByteBuf buf)
        {
            final int id = ByteBufCodecs.INT.decode(buf);
            final BlockPos center = BlockPos.STREAM_CODEC.decode(buf);
            final String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            final int citizencount = ByteBufCodecs.INT.decode(buf);
            final String owner = ByteBufCodecs.STRING_UTF8.decode(buf);
            final int prestige = ByteBufCodecs.INT.decode(buf);
            return new ColonyInfo(id, center, name, citizencount, owner, prestige);
        }

        @Override
        public void encode(final ByteBuf buf, final ColonyInfo info)
        {
            ByteBufCodecs.INT.encode(buf, info.getId());
            BlockPos.STREAM_CODEC.encode(buf, info.getCenter());
            ByteBufCodecs.STRING_UTF8.encode(buf, info.getName());
            ByteBufCodecs.INT.encode(buf, info.getCitizencount());
            ByteBufCodecs.STRING_UTF8.encode(buf, info.getOwner());
            ByteBufCodecs.INT.encode(buf, info.getPrestige());
        }
    };

    private static final StreamCodec<ByteBuf, List<ColonyInfo>> COLONY_LIST_CODEC =
        ByteBufCodecs.collection(ArrayList::new, COLONY_INFO_CODEC);

    public ColonyListMessage()
    {
        this(Collections.emptyList());
    }

    /**
     * Creates a message to handle colony views.
     */
    public ColonyListMessage(final List<IColony> colonies)
    {
        super(TYPE);
        this.colonies = colonies;
        this.colonyInfo = null;
    }

    protected ColonyListMessage(@NotNull final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        colonies = null;
        colonyInfo = COLONY_LIST_CODEC.decode(buf);
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        COLONY_LIST_CODEC.encode(buf, colonies.stream()
            .map(colony -> new ColonyInfo(colony.getID(),
                colony.getCenter(),
                colony.getName(),
                colony.getCitizenManager().getCurrentCitizenCount(),
                colony.getPermissions().getOwnerName(),
                colony.getServerBuildingManager().getColonyPrestige()))
            .toList());
    }

    @Override
    protected void onClientExecute(final PlayMessageContext context, final Player player)
    {
        WindowColonyMap.setColonies(colonyInfo);
    }

    @Override
    protected void onServerExecute(final PlayMessageContext context, final ServerPlayer player)
    {
        new ColonyListMessage(IColonyManager.getInstance().getColonies(player.level())).sendToPlayer(player);
    }

    public static class ColonyInfo
    {
        private final int      id;
        private       BlockPos center;
        private       String   name;
        private       int      citizencount;
        private       String   owner;
        private       int      prestige;

        public ColonyInfo(final int id)
        {
            this.id = id;
        }

        public ColonyInfo(final int id,
            final BlockPos center,
            final String name,
            final int citizencount,
            final String owner,
            final int prestige)
        {
            this.id = id;
            this.center = center;
            this.name = name;
            this.citizencount = citizencount;
            this.owner = owner;
            this.prestige = prestige;
        }

        public int getId()
        {
            return id;
        }

        public BlockPos getCenter()
        {
            return center;
        }

        public String getName()
        {
            return name;
        }

        public int getCitizencount()
        {
            return citizencount;
        }

        public String getOwner()
        {
            return owner;
        }

        public int getPrestige() {
            return prestige;
        }
    }
}
