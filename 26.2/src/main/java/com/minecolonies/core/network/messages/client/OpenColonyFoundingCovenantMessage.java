package com.minecolonies.core.network.messages.client;

import com.ldtteam.common.network.AbstractClientPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.assetfetch.AssetFetchGate;
import com.minecolonies.core.client.gui.townhall.WindowTownHallColonyManage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import com.ldtteam.common.network.PlayMessageContext;

/**
 * Message to open the colony founding covenant.
 */
public class OpenColonyFoundingCovenantMessage  extends AbstractClientPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forClient(Constants.MOD_ID, "open_colony_founding_covenant", OpenColonyFoundingCovenantMessage::new);

    /**
     * Info on the closest colony.
     */
    private String closestName;
    private int      closestDistance;
    private BlockPos townHallPos;

    /**
     * Default constructor
     **/
    public OpenColonyFoundingCovenantMessage(RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(type);
        this.closestName = buf.readUtf(32767);
        this.closestDistance = buf.readInt();
        this.townHallPos = buf.readBlockPos();
    }

    public OpenColonyFoundingCovenantMessage(final String closestName, final int closestDistance, final BlockPos townHallPos)
    {
        super(TYPE);
        this.closestName = closestName;
        this.closestDistance = closestDistance;
        this.townHallPos = townHallPos;
    }

    @Override
    protected void onExecute(final PlayMessageContext ctxIn, final Player player)
    {
        // Asset gate (D2): building the window loads its BlockUI XML, which is not in this jar.
        AssetFetchGate.openOrOffer(() -> new WindowTownHallColonyManage(townHallPos, closestName, closestDistance, "", false));
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf)
    {
        buf.writeUtf(closestName);
        buf.writeInt(closestDistance);
        buf.writeBlockPos(townHallPos);
    }
}
