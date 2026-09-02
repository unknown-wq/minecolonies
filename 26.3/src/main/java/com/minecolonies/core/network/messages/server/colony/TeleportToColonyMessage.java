package com.minecolonies.core.network.messages.server.colony;

import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.connections.ColonyConnection;
import com.minecolonies.api.colony.connections.DiplomacyStatus;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MathUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGateHouse;
import com.minecolonies.core.network.messages.server.AbstractColonyServerMessage;
import com.minecolonies.core.tileentities.TileEntityColonyBuilding;
import com.minecolonies.core.util.TeleportHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.minecolonies.api.inventory.api.InvWrapper;
import com.ldtteam.common.network.PlayMessageContext;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.minecolonies.api.util.constant.Constants.STACKSIZE;
import static com.minecolonies.api.util.constant.SchematicTagConstants.TAG_GATE;

/**
 * Message for trying to teleport to a friends colony.
 * <p>
 * Both ends of the journey are named by the sender and neither is believed. The gate house the player is standing at
 * has to be a gate house of the colony he says it is, and close enough to him to have been clicked; the destination
 * has to be the gate position the origin colony's own connection manager records for the target colony, which is the
 * only position the window ever offers. The fare is worked out here from those two positions rather than read off the
 * wire, and the journey only happens once it has been paid.
 */
public class TeleportToColonyMessage extends AbstractColonyServerMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(Constants.MOD_ID, "teleport_to_colony", TeleportToColonyMessage::new);

    /**
     * How far from the gate house the sender is still believed. The window is opened by right clicking that very
     * block, so any legitimate sender is within arm's reach of it; this leaves room for the walk to the confirmation
     * dialog and stops a hand written packet naming a gate house on the other side of the colony to get a cheaper
     * fare out of it.
     */
    private static final int MAX_GATE_DISTANCE = 64;

    /**
     * How many blocks of travel one gold nugget pays for.
     */
    private static final int BLOCKS_PER_NUGGET = 125;

    /**
     * Origin colony id.
     */
    private int originColonyId;

    /**
     * The gate house of the origin colony the player is travelling from.
     */
    private BlockPos originGate;

    /**
     * Gatehouse pos to teleport to.
     */
    private BlockPos pos;

    public TeleportToColonyMessage(final ResourceKey<Level> dimensionId, final int colonyId, final BlockPos pos, final int originColonyId, final BlockPos originGate)
    {
        super(TYPE, dimensionId, colonyId);
        this.pos = pos;
        this.originColonyId = originColonyId;
        this.originGate = originGate;
    }

    protected TeleportToColonyMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        this.pos = buf.readBlockPos();
        this.originColonyId = buf.readInt();
        this.originGate = buf.readBlockPos();
    }

    @Nullable
    @Override
    protected Action permissionNeeded()
    {
        return null;
    }

    @Override
    protected void toBytes(final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeBlockPos(pos);
        buf.writeInt(originColonyId);
        buf.writeBlockPos(originGate);
    }

    @Override
    protected void onExecute(final PlayMessageContext ctxIn, final ServerPlayer player, final IColony colony)
    {
        if (player == null)
        {
            return;
        }

        final IColony originColony = IColonyManager.getInstance().getColonyByDimension(originColonyId, player.level().dimension());
        if (originColony == null)
        {
            return;
        }

        if (originColony.getConnectionManager().getColonyDiplomacyStatus(colony.getID()) != DiplomacyStatus.ALLIES)
        {
            return;
        }

        if (!originColony.getPermissions().hasPermission(player, Action.TELEPORT_TO_COLONY) && !colony.getPermissions().hasPermission(player, Action.TELEPORT_TO_COLONY))
        {
            return;
        }

        // The gate house the fare is measured from has to be one of the origin colony's, and one the player could
        // have clicked. Without both, any gate house in the colony could be named to shorten the journey.
        final IBuilding origin = originColony.getServerBuildingManager().getBuilding(originGate);
        if (!(origin instanceof BuildingGateHouse) || player.blockPosition().distSqr(originGate) > MAX_GATE_DISTANCE * MAX_GATE_DISTANCE)
        {
            return;
        }

        // The destination is not a position the sender gets to choose: it is the one the origin colony recorded when
        // the two were connected, which is exactly what the window puts on the button.
        if (!pos.equals(connectedGate(originColony, colony.getID())))
        {
            return;
        }

        final int cost = fare(originColony, player, pos, originGate);
        final BlockEntity gateHouse = colony.getWorld().getBlockEntity(pos);
        final boolean atGateHouse = gateHouse instanceof TileEntityColonyBuilding && ((TileEntityColonyBuilding) gateHouse).getBuilding() instanceof BuildingGateHouse;

        if (cost > 0)
        {
            if (!InventoryUtils.attemptReduceStackInItemHandler(new InvWrapper(player.getInventory()), new ItemStack(Items.GOLD_NUGGET), cost))
            {
                // The window disables the button when the fare cannot be paid, so this only refuses a request that
                // never should have been made. Nothing is taken and nobody travels.
                return;
            }

            if (atGateHouse)
            {
                int output = cost / 2;
                while (output > 0)
                {
                    final int qty = Math.min(STACKSIZE, output);
                    InventoryUtils.addItemStackToItemHandler(((TileEntityColonyBuilding) gateHouse).getInventory(), new ItemStack(Items.GOLD_NUGGET, qty));
                    output -= qty;
                }
            }
        }

        if (atGateHouse)
        {
            final List<BlockPos> posList = ((TileEntityColonyBuilding) gateHouse).getCachedWorldTagNamePosMap().get(TAG_GATE);
            if (posList != null && !posList.isEmpty())
            {
                TeleportHelper.colonyTeleport(player, colony, posList.get(MathUtils.RANDOM.nextInt(posList.size())));
                return;
            }
        }

        TeleportHelper.colonyTeleport(player, colony, pos);
    }

    /**
     * The gate position the origin colony records for a colony it is connected to.
     *
     * @param originColony the colony the player is travelling from.
     * @param targetId     the colony he is travelling to.
     * @return the recorded gate position, or null when the two are not connected.
     */
    @Nullable
    private static BlockPos connectedGate(final IColony originColony, final int targetId)
    {
        ColonyConnection connection = originColony.getConnectionManager().getDirectlyConnectedColonies().get(targetId);
        if (connection == null)
        {
            connection = originColony.getConnectionManager().getIndirectlyConnectedColonies().get(targetId);
        }
        return connection == null ? null : connection.pos;
    }

    /**
     * What the journey costs in gold nuggets, worked out the way the window displays it.
     * <p>
     * A player the origin colony lets into its huts travels for nothing; everybody else pays by the distance between
     * the two gates. Both the membership test and the distance come from the server's own data, so the number here is
     * the number the window showed rather than the number the sender would like it to be.
     *
     * @param originColony the colony the player is travelling from.
     * @param player       the traveller.
     * @param target       the destination gate.
     * @param origin       the gate he is leaving from.
     * @return the fare, zero when there is none.
     */
    private static int fare(final IColony originColony, final ServerPlayer player, final BlockPos target, final BlockPos origin)
    {
        if (originColony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
        {
            return 0;
        }
        return (int) BlockPosUtil.dist(target, origin) / BLOCKS_PER_NUGGET;
    }
}
