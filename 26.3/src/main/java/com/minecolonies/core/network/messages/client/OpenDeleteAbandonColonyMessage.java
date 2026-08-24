package com.minecolonies.core.network.messages.client;

import com.ldtteam.common.network.AbstractClientPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.assetfetch.AssetFetchGate;
import com.minecolonies.core.client.gui.townhall.WindowTownHallDeleteAbandonColony;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import com.ldtteam.common.network.PlayMessageContext;

/**
 * Message to open the colony founding covenant.
 */
public class OpenDeleteAbandonColonyMessage  extends AbstractClientPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forClient(Constants.MOD_ID, "open_delete_abandon_colony", OpenDeleteAbandonColonyMessage::new);

    /**
     * Colony pos at which we are trying to place.
     */
    private BlockPos currentTownHallPos;

    /**
     * Colony pos we are deleting or abandoning.
     */
    private BlockPos oldColonyPos;

    /**
     * Old colony name.
     */
    private String oldColonyName;

    /**
     * Old colony id.
     */
    private int oldColonyId;

    /**
     * Default constructor
     **/
    public OpenDeleteAbandonColonyMessage(RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(type);
        this.currentTownHallPos = buf.readBlockPos();
        this.oldColonyName = buf.readUtf(32767);
        this.oldColonyPos = buf.readBlockPos();
        this.oldColonyId = buf.readInt();
    }

    public OpenDeleteAbandonColonyMessage(final BlockPos currentTownHallPos, final String oldColonyName, final BlockPos oldColonyPos, final int oldColonyId)
    {
        super(TYPE);
        this.currentTownHallPos = currentTownHallPos;
        this.oldColonyName = oldColonyName;
        this.oldColonyPos = oldColonyPos;
        this.oldColonyId = oldColonyId;
    }

    @Override
    protected void onExecute(final PlayMessageContext ctxIn, final Player player)
    {
        // Asset gate (D2): building the window loads its BlockUI XML, which is not in this jar.
        AssetFetchGate.openOrOffer(() -> new WindowTownHallDeleteAbandonColony(currentTownHallPos, oldColonyName, oldColonyPos));
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf)
    {
        buf.writeBlockPos(currentTownHallPos);
        buf.writeUtf(oldColonyName);
        buf.writeBlockPos(oldColonyPos);
        buf.writeInt(oldColonyId);
    }
}
