package com.minecolonies.core.network.messages.server.colony.building.fields;

import com.ldtteam.common.network.PlayMessageType;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.network.messages.server.AbstractColonyServerMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.ldtteam.common.network.PlayMessageContext;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Message to change the set of seeds a farm field is sown with.
 * <p>
 * The whole set travels, not the one seed that changed. The scarecrow window builds the list it wants and sends it,
 * so the message is idempotent and two clicks racing each other end with one of the two lists rather than with a
 * list neither player asked for. {@link FarmField#setSeeds} does the clamping.
 */
public class FarmFieldUpdateSeedMessage extends AbstractColonyServerMessage
{
    public static final PlayMessageType<?> TYPE = PlayMessageType.forServer(Constants.MOD_ID, "farm_field_update_seed", FarmFieldUpdateSeedMessage::new);

    /**
     * The new seeds to assign to the field.
     */
    private final List<ItemStack> newSeeds;

    /**
     * The field position.
     */
    private final BlockPos position;

    /**
     * Default constructor.
     *
     * @param colony   the colony where the field is in.
     * @param newSeeds the new seeds to assign to the field.
     * @param position the field position.
     */
    public FarmFieldUpdateSeedMessage(@NotNull final IColony colony, final List<ItemStack> newSeeds, final BlockPos position)
    {
        super(TYPE, colony);
        this.newSeeds = List.copyOf(newSeeds);
        this.position = position;
    }

    @Override
    protected void onExecute(final PlayMessageContext ctxIn, final ServerPlayer player, final IColony colony)
    {
        colony.getServerBuildingManager()
          .getMatchingBuildingExtension(f -> f.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get()) && f.getPosition().equals(position))
          .map(m -> (FarmField) m)
          .ifPresent(field -> {
              field.setSeeds(newSeeds);
              // The sending client updates its own view straight away, but every other client watching the colony
              // only learns about it through the extension sync, and nothing else on this path triggers one.
              colony.getServerBuildingManager().markBuildingExtensionsDirty();
          });
    }

    @Override
    protected void toBytes(final RegistryFriendlyByteBuf buf)
    {
        super.toBytes(buf);
        buf.writeVarInt(newSeeds.size());
        for (final ItemStack stack : newSeeds)
        {
            Utils.serializeCodecMess(buf, stack);
        }
        buf.writeBlockPos(position);
    }

    protected FarmFieldUpdateSeedMessage(final RegistryFriendlyByteBuf buf, final PlayMessageType<?> type)
    {
        super(buf, type);
        // Clamped on read as well as in setSeeds: this is a client to server message, so the count on the wire is
        // whatever the client felt like sending, and allocating a list from it unchecked is how one packet becomes
        // an out of memory error.
        final int count = Math.max(0, Math.min(buf.readVarInt(), FarmField.MAX_SEEDS));
        final List<ItemStack> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            read.add(Utils.deserializeCodecMess(buf));
        }
        newSeeds = read;
        position = buf.readBlockPos();
    }
}
