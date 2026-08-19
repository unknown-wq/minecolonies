package com.minecolonies.core.network.messages.server.colony.building.fields;

import com.ldtteam.common.network.AbstractServerPlayMessage;
import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.tileentities.TileEntityScarecrow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.ldtteam.common.network.PlayMessageContext;

import java.util.Arrays;

import static com.minecolonies.api.util.constant.TranslationConstants.FIELD_TOO_LARGE;

/**
 * Message to change the farmer field plot size.
 */
public class FarmFieldPlotResizeMessage extends AbstractServerPlayMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(Constants.MOD_ID, "farm_field_plot_resize", FarmFieldPlotResizeMessage::new);

    /**
     * How far from the scarecrow the sender is still believed. The window can only be opened by right clicking the
     * block, so any legitimate sender is within arm's reach; this is only here to keep a hand written packet from
     * resizing fields on the other side of the world.
     */
    private static final int MAX_INTERACTION_DISTANCE = 64;

    /**
     * The new radius of the field plot.
     */
    private final int size;

    /**
     * The specified direction for the new radius.
     */
    private final Direction direction;

    /**
     * The field position.
     */
    private final BlockPos position;

    /**
     * @param size      the new radius of the field plot
     * @param direction the specified direction for the new radius
     * @param position  the field position.
     */
    public FarmFieldPlotResizeMessage(final int size, final Direction direction, final BlockPos position)
    {
        super(TYPE);
        this.size = size;
        this.direction = direction;
        this.position = position;
    }

    @Override
    protected void onExecute(final PlayMessageContext ctxIn, final ServerPlayer player)
    {
        if (!player.level().isLoaded(position) || player.blockPosition().distSqr(position) > MAX_INTERACTION_DISTANCE * MAX_INTERACTION_DISTANCE)
        {
            return;
        }

        final BlockEntity fieldBlock = player.level().getBlockEntity(position);
        if (fieldBlock instanceof TileEntityScarecrow scarecrow)
        {
            final IColony colony = scarecrow.getCurrentColony();
            final int[] current = scarecrow.getFieldSize();
            if (current.length != FarmField.RADII_COUNT)
            {
                return;
            }

            // Everything below treats the packet as hostile: the size is a raw int off the wire, the window that is
            // supposed to have produced it may not have been ours, and the limit that applies depends on the colony,
            // not on anything the sender says. Shrinking is always allowed - it is the only way back for a field laid
            // out in free mode after free mode has been turned off.
            final int currentDirSize = current[direction.get2DDataValue()];
            if (size < 0)
            {
                return;
            }

            if (size > currentDirSize)
            {
                final int[] wanted = Arrays.copyOf(current, FarmField.RADII_COUNT);
                wanted[direction.get2DDataValue()] = size;

                if (!FarmField.isSizeAllowed(wanted, colony))
                {
                    // Say why. Upstream returned silently here and the player watched the number snap back with no
                    // explanation, which is exactly the complaint free mode is meant to remove.
                    player.sendSystemMessage(Component.translatable(FIELD_TOO_LARGE,
                      FarmField.getArea(wanted),
                      FarmField.getMaxArea(colony),
                      FarmField.getMaxRadius(colony)));
                    return;
                }
            }

            scarecrow.setFieldSize(direction, size);
            if (colony != null)
            {
                colony.getServerBuildingManager()
                    .getMatchingBuildingExtension(f -> f.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get()) && f.getPosition().equals(position))
                    .map(m -> (FarmField) m)
                    .ifPresent(field -> field.setRadius(direction, size));
            }
        }
    }

    @Override
    protected void toBytes(final RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(size);
        buf.writeInt(direction.get2DDataValue());
        buf.writeBlockPos(position);
    }

    protected FarmFieldPlotResizeMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        size = buf.readInt();
        direction = Direction.from2DDataValue(buf.readInt());
        position = buf.readBlockPos();
    }
}
