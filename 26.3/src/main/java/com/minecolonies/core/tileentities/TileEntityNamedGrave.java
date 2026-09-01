package com.minecolonies.core.tileentities;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.tileentities.AbstractTileEntityNamedGrave;
import com.minecolonies.api.tileentities.MinecoloniesTileEntities;
import com.minecolonies.core.colony.buildings.modules.GraveyardManagementModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingGraveyard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Represent a TileEntity that will be built by a graveyard undertaker in a graveyard to honor a dead citizen
 */
public class TileEntityNamedGrave extends AbstractTileEntityNamedGrave
{
    /**
     * Default constructor used to create a new TileEntity via reflection. Do not use.
     */
    public TileEntityNamedGrave(final BlockPos pos, final BlockState state)
    {
        this(MinecoloniesTileEntities.NAMED_GRAVE.get(), pos, state);
    }

    /**
     * Alternative overriden constructor.
     *
     * @param type the entity type.
     */
    public TileEntityNamedGrave(final BlockEntityType<? extends TileEntityNamedGrave> type, final BlockPos pos, final BlockState state)
    {
        super(type, pos, state);
    }

    /**
     * Breaking a headstone takes the name off the graveyard's resting list.
     * <p>
     * A graveyard holds as many burials as its blueprint has headstone slots, and once they are all built on there
     * is no more room: the undertaker announces that he has nowhere to put the next citizen and walks away. Breaking
     * a stone always freed the slot - the position is a slot again the moment it is replaceable - but the name it
     * carried stayed in {@code restingCitizen} for ever, saved with the colony, listed in the graveyard's tab and
     * sent to every subscribed client. This is the other half of that action, and it is what makes a graveyard
     * something a player can clear.
     * <p>
     * Server side and on a real block change only; see {@code LevelChunk#setBlockState}. The same hook is where the
     * grave block spills its inventory, for the same reason: it is the last point at which the block entity still
     * exists.
     *
     * @param pos   the position the block is being removed from.
     * @param state the state being removed.
     */
    @Override
    public void preRemoveSideEffects(@NotNull final BlockPos pos, @NotNull final BlockState state)
    {
        super.preRemoveSideEffects(pos, state);

        if (level == null || level.isClientSide())
        {
            return;
        }

        final String citizenName = getRestingCitizenName();
        if (citizenName.isEmpty())
        {
            return;
        }

        final IColony colony = IMinecoloniesAPI.getInstance().getColonyManager().getColonyByPosFromWorld(level, pos);
        if (colony == null)
        {
            return;
        }

        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building instanceof final BuildingGraveyard graveyard && graveyard.isVisualGravePos(pos))
            {
                graveyard.getFirstModuleOccurance(GraveyardManagementModule.class).removeRestingCitizen(citizenName);
                return;
            }
        }
    }
}
