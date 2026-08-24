package com.minecolonies.core.network.messages.server;

import com.ldtteam.common.network.AbstractServerPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.Colony;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.minecolonies.api.inventory.api.InvWrapper;
import com.ldtteam.common.network.PlayMessageContext;

import static com.minecolonies.api.util.constant.TranslationConstants.WARNING_BUILDING_PICKUP_PLAYER_INVENTORY_FULL;

/**
 * Pickup the town hall block.
 */
public class PickupBlockMessage extends AbstractServerPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(Constants.MOD_ID, "pickup_block", PickupBlockMessage::new);

    /**
     * How far from the block the sender is still believed. Every sender of this message is a window that was opened
     * by right clicking that very block, so any legitimate one is within arm's reach; this only stops a hand written
     * packet naming a position on the other side of the world, which the block lookups below would otherwise load
     * and generate a chunk for, on the server thread.
     */
    private static final int MAX_INTERACTION_DISTANCE = 64;

    /**
     * Position the player wants to found the colony at.
     */
    BlockPos pos;

    public PickupBlockMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(type);
        pos = buf.readBlockPos();
    }

    public PickupBlockMessage(final BlockPos pos)
    {
        super(TYPE);
        this.pos = pos;
    }

    @Override
    public void toBytes(final RegistryFriendlyByteBuf buf)
    {
        buf.writeBlockPos(pos);
    }

    @Override
    protected void onExecute(final PlayMessageContext ctxIn, final ServerPlayer sender)
    {
        if (sender == null)
        {
            return;
        }

        if (!sender.level().isLoaded(pos) || sender.blockPosition().distSqr(pos) > MAX_INTERACTION_DISTANCE * MAX_INTERACTION_DISTANCE)
        {
            return;
        }

        final Level world = sender.level();

        if (IColonyManager.getInstance().getColonyByPosFromWorld(world, pos) instanceof Colony)
        {
            return;
        }

        final ItemStack stack = new ItemStack(world.getBlockState(pos).getBlock(), 1);
        if (InventoryUtils.addItemStackToItemHandler(new InvWrapper(sender.getInventory()), stack))
        {
            world.destroyBlock(pos, false);
        }
        else
        {
            MessageUtils.format(WARNING_BUILDING_PICKUP_PLAYER_INVENTORY_FULL).sendTo(sender);
        }

    }
}
