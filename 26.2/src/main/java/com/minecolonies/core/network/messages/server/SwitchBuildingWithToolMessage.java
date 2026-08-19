package com.minecolonies.core.network.messages.server;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.common.network.AbstractServerPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.ldtteam.structurize.items.ModItems;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.ldtteam.common.network.PlayMessageContext;

import org.jetbrains.annotations.NotNull;

/**
 * Switch the buildtool with the respective item in the inventory.
 */
public class SwitchBuildingWithToolMessage extends AbstractServerPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(Constants.MOD_ID, "switch_building_with_tool", SwitchBuildingWithToolMessage::new);

    /**
     * The stack to switch.
     */
    private final ItemStack stack;

    /**
     * Switch the stack.
     *
     * @param stack the stack in the hand.
     */
    public SwitchBuildingWithToolMessage(final ItemStack stack)
    {
        super(TYPE);
        this.stack = stack;
    }

    /**
     * Reads this packet from a {@link RegistryFriendlyByteBuf}.
     *
     * @param buf The buffer begin read from.
     */
    protected SwitchBuildingWithToolMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        stack = Utils.deserializeCodecMess(buf);
    }

    /**
     * Writes this packet to a {@link RegistryFriendlyByteBuf}.
     *
     * @param buf The buffer being written to.
     */
    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        Utils.serializeCodecMess(buf, stack);
    }

    @Override
    protected void onExecute(final PlayMessageContext ctxIn, final ServerPlayer player)
    {
        int stackSlot = -1;
        int buildToolSlot = -1;
        for (int i = 0; i < 9; i++)
        {
            if (ItemStack.isSameItem(player.getInventory().getItem(i), stack))
            {
                stackSlot = i;
            }
            else if (player.getInventory().getItem(i).getItem() == ModItems.buildTool.get())
            {
                buildToolSlot = i;
            }
        }

        for (int i = 9; i < player.getInventory().getContainerSize(); i++)
        {
            if (player.getInventory().getItem(i).getItem() == ModItems.buildTool.get())
            {
                buildToolSlot = i;
            }
        }

        if (stackSlot != -1 && buildToolSlot != -1)
        {
            player.getInventory().setItem(buildToolSlot, player.getInventory().getItem(stackSlot).copy());
            player.getInventory().setItem(stackSlot, new ItemStack(ModItems.buildTool.get(), 1));
        }
    }
}
