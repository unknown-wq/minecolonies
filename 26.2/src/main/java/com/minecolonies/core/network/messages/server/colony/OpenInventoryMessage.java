package com.minecolonies.core.network.messages.server.colony;

import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.CompatibilityUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.network.messages.server.AbstractColonyServerMessage;
import com.minecolonies.core.tileentities.TileEntityGrave;
import com.minecolonies.core.tileentities.TileEntityRack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.ldtteam.common.network.PlayMessageContext;

import org.jetbrains.annotations.NotNull;

/**
 * Message sent to open an inventory.
 */
public class OpenInventoryMessage extends AbstractColonyServerMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(Constants.MOD_ID, "open_inventory", OpenInventoryMessage::new);

    /***
     * The inventory name.
     */
    private final String name;

    /**
     * The inventory type.
     */
    private final InventoryType inventoryType;

    /**
     * The entities id.
     */
    private int entityID;

    /**
     * The position of the inventory block/entity.
     */
    private BlockPos tePos;

    /**
     * Creates an open inventory message for the citizen.
     *
     * @param name   the name of the citizen.
     * @param id     its id.
     * @param colony the colony of the network message
     */
    public OpenInventoryMessage(final IColonyView colony, @NotNull final String name, final int id)
    {
        super(TYPE, colony);
        inventoryType = InventoryType.INVENTORY_CITIZEN;
        this.name = name;
        this.entityID = id;
    }

    /**
     * Creates an open inventory message for a building.
     *
     * @param building the building we're executing on.
     */
    public OpenInventoryMessage(final IBuildingView building)
    {
        super(TYPE, building.getColony());
        inventoryType = InventoryType.INVENTORY_CHEST;
        name = "";
        tePos = building.getID();
    }

    protected OpenInventoryMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);

        inventoryType = InventoryType.values()[buf.readInt()];
        name = buf.readUtf(32767);
        switch (inventoryType)
        {
            case INVENTORY_CITIZEN:
                entityID = buf.readInt();
                break;
            case INVENTORY_CHEST:
                tePos = buf.readBlockPos();
                break;
        }
    }

    @Override
    protected void toBytes(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);

        buf.writeInt(inventoryType.ordinal());
        buf.writeUtf(name);
        switch (inventoryType)
        {
            case INVENTORY_CITIZEN:
                buf.writeInt(entityID);
                break;
            case INVENTORY_CHEST:
                buf.writeBlockPos(tePos);
                break;
        }
    }

    @Override
    protected void onExecute(final PlayMessageContext ctxIn, final ServerPlayer player, final IColony colony)
    {
        switch (inventoryType)
        {
            case INVENTORY_CITIZEN:
                doCitizenInventory(player);
                break;
            case INVENTORY_CHEST:
                doHutInventory(player, colony);
                break;
            default:
                break;
        }
    }

    private void doCitizenInventory(final ServerPlayer player)
    {
        // The id comes off the wire and names an entity that may be gone by the time the button is pressed -- a
        // citizen killed in a raid while its window was open, and the network id handed on to whatever spawned next.
        // The cast was unchecked, so that turned into a ClassCastException out of the packet handler instead of the
        // window simply not opening.
        if (CompatibilityUtils.getWorldFromEntity(player).getEntity(entityID) instanceof final AbstractEntityCitizen citizen)
        {
            if (!StringUtil.isNullOrEmpty(name))
            {
                citizen.getInventoryCitizen().setCustomName(name);
            }

            // 26.2/Fabric: NeoForge's openMenu(MenuProvider, Consumer<FriendlyByteBuf>) is replaced by
            // fabric-menu-api-v1's ExtendedMenuProvider. AbstractEntityCitizen lives in api/ and is only a plain
            // MenuProvider, so the extra data is supplied by a thin adapter here instead of changing that class.
            player.openMenu(new ExtendedMenuProvider<RegistryFriendlyByteBuf>()
            {
                @NotNull
                @Override
                public Component getDisplayName()
                {
                    return citizen.getDisplayName();
                }

                @Override
                public AbstractContainerMenu createMenu(final int id, @NotNull final Inventory inv, @NotNull final Player menuPlayer)
                {
                    return citizen.createMenu(id, inv, menuPlayer);
                }

                @Override
                public RegistryFriendlyByteBuf getScreenOpeningData(@NotNull final ServerPlayer serverPlayer)
                {
                    final RegistryFriendlyByteBuf buffer =
                      new RegistryFriendlyByteBuf(Unpooled.buffer(), serverPlayer.level().registryAccess());
                    buffer.writeVarInt(citizen.getCitizenColonyHandler().getColonyId());
                    buffer.writeVarInt(citizen.getCivilianID());
                    return buffer;
                }
            });
        }
    }

    private void doHutInventory(final ServerPlayer player, final IColony colony)
    {
        final BlockEntity tileEntity = BlockPosUtil.getTileEntity(player.level(), tePos);

        if(tileEntity instanceof TileEntityRack || tileEntity instanceof TileEntityGrave)
        {
            // 26.2/Fabric: TileEntityRack and TileEntityGrave implement ExtendedMenuProvider themselves now and
            // write their own screen-opening data, which is what ContainerRack/ContainerGrave actually read
            // (rack: two block positions; grave: one). The 1.21.1 call site wrote colonyId + pos here, which
            // neither container read -- that mismatch is gone rather than reproduced.
            player.openMenu((MenuProvider) tileEntity);
        }
    }

    /**
     * Type of inventory.
     */
    private enum InventoryType
    {
        INVENTORY_CITIZEN,
        INVENTORY_CHEST
    }
}
