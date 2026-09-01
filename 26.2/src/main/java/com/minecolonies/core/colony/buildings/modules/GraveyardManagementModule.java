package com.minecolonies.core.colony.buildings.modules;

import com.minecolonies.api.blocks.AbstractBlockMinecoloniesNamedGrave;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.GraveData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.modules.AbstractBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingEventsModule;
import com.minecolonies.api.colony.buildings.modules.IBuildingModule;
import com.minecolonies.api.colony.buildings.modules.IPersistentModule;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.tileentities.TileEntityGrave;
import com.minecolonies.core.tileentities.TileEntityNamedGrave;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.minecolonies.api.util.constant.Constants.TAG_STRING;

/**
 * The graveyard list module.
 */
public class GraveyardManagementModule extends AbstractBuildingModule implements IBuildingModule, IPersistentModule, IBuildingEventsModule
{
    /**
     * The tag to store the list of resting citizen in this graveyard
     */
    private static final String TAG_RIP_CITIZEN_LIST = "ripCitizenList";

    /**
     * NBTTag to store grave data.
     */
    private static final String TAG_GRAVE_DATA = "gravedata";

    /**
     * The list of resting citizen in this graveyard.
     */
    private final List<String> restingCitizen = new ArrayList<>();

    /**
     * The data of the last grave dug by the undertaker.
     */
    @Nullable
    private GraveData lastGraveData;

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        restingCitizen.clear();
        if (compound.contains(TAG_RIP_CITIZEN_LIST))
        {
            final ListTag ripCitizen = compound.getListOrEmpty(TAG_RIP_CITIZEN_LIST);
            for (int i = 0; i < ripCitizen.size(); i++)
            {
                final String citizenName = ripCitizen.getStringOr(i, "");
                restingCitizen.add(citizenName);
            }
        }

        if (compound.contains(TAG_GRAVE_DATA))
        {
            lastGraveData = new GraveData();
            lastGraveData.read(compound.getCompoundOrEmpty(TAG_GRAVE_DATA));
        }
        else lastGraveData = null;
    }

    @Override
    public void serializeNBT(@NotNull final HolderLookup.Provider provider, CompoundTag compound)
    {
        @NotNull final ListTag ripCitizen = new ListTag();
        for (@NotNull final String citizenName : restingCitizen)
        {
            ripCitizen.add(StringTag.valueOf(citizenName));
        }
        compound.put(TAG_RIP_CITIZEN_LIST, ripCitizen);

        if(lastGraveData != null)
        {
            compound.put(TAG_GRAVE_DATA, lastGraveData.write());
        }
    }

    @Override
    public void serializeToView(@NotNull final RegistryFriendlyByteBuf buf)
    {
        final IColony colony = building.getColony();
        final List<BlockPos> graves = new ArrayList<>(colony.getGraveManager().getGraves().keySet());
        final List<BlockPos> cleanList = new ArrayList<>();

        for (@NotNull final BlockPos grave : graves)
        {
            if (WorldUtil.isBlockLoaded(colony.getWorld(), grave))
            {
                final BlockEntity tileEntity = colony.getWorld().getBlockEntity(grave);
                if (tileEntity instanceof TileEntityGrave)
                {
                    cleanList.add(grave);
                }
            }
        }

        // grave list
        buf.writeInt(cleanList.size());
        for (@NotNull final BlockPos grave : cleanList)
        {
            buf.writeBlockPos(grave);
        }

        //resting citizen list
        buf.writeInt(restingCitizen.size());
        for (@NotNull final String citizenName : restingCitizen)
        {
            buf.writeUtf(citizenName);
        }
    }

    /**
     * Setter for the last grave data.
     * <p>
     * Deliberately does not mark the module dirty. Nothing about the last grave reaches
     * {@link #serializeToView(RegistryFriendlyByteBuf)} - the view carries the colony's live graves and this
     * graveyard's resting list, and neither depends on it - while the building's NBT is written on every colony save
     * whether it is dirty or not. Marking dirty here only pushed the whole resting list, every name in it, to every
     * subscribed client once per grave dug.
     *
     * @param graveData the last grave the worker has dug.
     */
    public void setLastGraveData(final GraveData graveData)
    {
        this.lastGraveData = graveData;
    }

    /**
     * Get for the last grave.
     * @return the last grave the worker has dug.
     */
    public GraveData getLastGraveData()
    {
        return this.lastGraveData;
    }

    /**
     * Check if one of the citizens in the list is resting.
     * @param citizens the citizens to check.
     * @return true if so.
     */
    public boolean hasRestingCitizen(final Set<String> citizens)
    {
        for (final String citizen : citizens)
        {
            if (restingCitizen.contains(citizen))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Take a citizen off the list of those resting in this graveyard.
     * <p>
     * The headstone is what holds a slot in a graveyard, and the name in this list is what holds the mourners'
     * destination. Both used to be one-way: nothing removed a headstone and nothing shrank the list, so a graveyard
     * filled up permanently and its list of names grew for the life of the world. Breaking a headstone - which a
     * player could always do, and which already freed the slot for the next burial - now takes the name with it.
     *
     * @param citizenName the name to remove; one entry only, because two citizens may genuinely share a name.
     */
    public void removeRestingCitizen(final String citizenName)
    {
        if (restingCitizen.remove(citizenName))
        {
            markDirty();
        }
    }

    /**
     * Add a citizen to the list of resting citizen in this graveyard
     */
    public void buryCitizenHere(final Tuple<BlockPos, Direction> positionAndDirection, final AbstractEntityCitizen worker)
    {
        // No !restingCitizen.contains(name) guard. Citizen names come from a fixed pool of first and last names, so
        // a colony of any size and age repeats one sooner or later, and the guard silently placed no headstone at
        // all for the second bearer of a name - while the AI counted the burial done, cleared its grave data and
        // walked off. Two people with the same name get two headstones; the list may hold the name twice.
        if (lastGraveData != null)
        {
            final IColony colony = building.getColony();
            Direction facing = positionAndDirection.getB();
            if(facing == Direction.UP || facing == Direction.DOWN)
            {
                facing = Direction.NORTH; //prevent setting an invalid HorizontalDirection
            }

            colony.getWorld().destroyBlock(positionAndDirection.getA(), true, worker);
            colony.getWorld().setBlockAndUpdate(positionAndDirection.getA(),
                    ModBlocks.blockNamedGrave.defaultBlockState().setValue(AbstractBlockMinecoloniesNamedGrave.FACING, facing));

            BlockEntity tileEntity = colony.getWorld().getBlockEntity(positionAndDirection.getA());
            if (tileEntity instanceof TileEntityNamedGrave)
            {
                final String firstName = StringUtils.split(lastGraveData.getCitizenName())[0];
                final String lastName = lastGraveData.getCitizenName().replaceFirst(firstName,"");

                final ArrayList<String> lines = new ArrayList<>();
                lines.add(firstName);
                lines.add(lastName);
                if (lastGraveData.getCitizenJobName() != null)
                {
                    lines.add(lastGraveData.getCitizenJobName());
                }
                ((TileEntityNamedGrave) tileEntity).setTextLines(lines);
            }

            restingCitizen.add(lastGraveData.getCitizenName());
            markDirty();
        }
    }
}
