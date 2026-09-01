package com.minecolonies.core.colony.buildings.workerbuildings;

import com.minecolonies.api.blocks.AbstractBlockMinecoloniesNamedGrave;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.equipment.ModEquipmentTypes;
import com.minecolonies.core.tileentities.TileEntityGrave;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.minecolonies.api.util.constant.EquipmentLevelConstants.TOOL_LEVEL_WOOD_OR_GOLD;
import static com.minecolonies.api.util.constant.UndertakerConstants.TOTEMS_TO_KEEP;

/**
 * Class which handles the graveyard building.
 */
public class BuildingGraveyard extends AbstractBuilding
{
    /**
     * Descriptive string of the building.
     */
    private static final String GRAVEYARD = "graveyard";

    /**
     * The maximum building level of the hut.
     */
    private static final int MAX_BUILDING_LEVEL = 5;

    /**
     * NBTTag to store the visual grave positions.
     */
    private static final String TAG_VISUAL_GRAVES = "visualgraves";

    /**
     * NBTTag to store the visual grave positions blockpos.
     */
    private static final String TAG_VISUAL_GRAVES_BLOCKPOS = "visualgravesblockpos";

    /**
     * NBTTag to store the visual grave facing.
     */
    private static final String TAG_VISUAL_GRAVES_FACING = "visualgravesfacing";

    /**
     * The last field tag.
     */
    private static final String TAG_CURRENT_GRAVE = "currentGRAVE";

    /**
     * The grave the undertaker is currently collecting.
     */
    @Nullable
    private BlockPos currentGrave;

    /**
     * Grave positions
     */
    private Set<Tuple<BlockPos, Direction>> visualGravePositions = new HashSet<>();

    /**
     * Public constructor which instantiates the building.
     *
     * @param c the colony the building is in.
     * @param l the position it has been placed (it's id).
     */
    public BuildingGraveyard(final IColony c, final BlockPos l)
    {
        super(c, l);
        keepX.put(itemStack -> ItemStackUtils.hasEquipmentLevel(itemStack, ModEquipmentTypes.shovel.get(), TOOL_LEVEL_WOOD_OR_GOLD, getMaxEquipmentLevel()), new com.minecolonies.api.util.Tuple<>(1, true));
        keepX.put(ItemStackUtils::hasDeathProtection, new com.minecolonies.api.util.Tuple<>(TOTEMS_TO_KEEP, true));
    }

    /**
     * Clear the current grave the undertaker is currently working on.
     */
    public void ClearCurrentGrave()
    {
        // The reservation has to go with the pointer. The grave manager persists the reserved flag to the colony
        // save and only ever clears it from getGraveToWorkOn below, so dropping the pointer on its own left a live
        // grave marked as somebody's work with nobody holding it - reserved for ever, worked by no graveyard, and
        // left to decay.
        releaseCurrentGrave();
    }

    @Override
    public void onDestroyed()
    {
        super.onDestroyed();
        releaseCurrentGrave();
    }

    /**
     * Drop the pointer to the grave being worked on and hand the reservation back to the colony.
     */
    private void releaseCurrentGrave()
    {
        if (currentGrave != null)
        {
            colony.getGraveManager().unReserveGrave(currentGrave);
            currentGrave = null;
        }
    }

    /**
     * Retrieves a random grave to work on for the undertaker.
     *
     * @return a field to work on.
     */
    @Nullable
    public BlockPos getGraveToWorkOn()
    {
        if(currentGrave != null)
        {
            if (WorldUtil.isBlockLoaded(colony.getWorld(), currentGrave))
            {
                final BlockEntity tileEntity = getColony().getWorld().getBlockEntity(currentGrave);
                if (tileEntity instanceof TileEntityGrave)
                {
                    return currentGrave;
                }
            }

            colony.getGraveManager().unReserveGrave(currentGrave);
            currentGrave = null;
        }

        currentGrave = colony.getGraveManager().reserveNextFreeGrave();
        return currentGrave;
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);

        if (compound.contains(TAG_CURRENT_GRAVE))
        {
            currentGrave = BlockPosUtil.read(compound, TAG_CURRENT_GRAVE);
        }

        visualGravePositions.clear();
        final ListTag visualGraveTagList = compound.getListOrEmpty(TAG_VISUAL_GRAVES);
        for (int i = 0; i < visualGraveTagList.size(); ++i)
        {
            final CompoundTag graveCompound = visualGraveTagList.getCompoundOrEmpty(i);
            final BlockPos graveLocation = BlockPosUtil.read(graveCompound, TAG_VISUAL_GRAVES_BLOCKPOS);
            final Direction graveFacing = Direction.byName(graveCompound.getStringOr(TAG_VISUAL_GRAVES_FACING, ""));
            visualGravePositions.add(new Tuple<>(graveLocation, graveFacing));
        }
    }

    @Override
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag compound = super.serializeNBT(provider);

        if (currentGrave != null)
        {
            BlockPosUtil.write(compound, TAG_CURRENT_GRAVE, currentGrave);
        }

        @NotNull final ListTag visualGraveTagList = new ListTag();
        for (@NotNull final Tuple<BlockPos, Direction> vgp : visualGravePositions)
        {
            @NotNull final CompoundTag graveCompound = new CompoundTag();
            BlockPosUtil.write(graveCompound, TAG_VISUAL_GRAVES_BLOCKPOS, vgp.getA());
            graveCompound.putString(TAG_VISUAL_GRAVES_FACING, vgp.getB().getName());
            visualGraveTagList.add(graveCompound);
        }
        compound.put(TAG_VISUAL_GRAVES, visualGraveTagList);
        return compound;
    }

    /**
     * Get the set of grave positions.
     * @return the set of positions with their directions.
     */
    public Set<Tuple<BlockPos, Direction>> getGravePositions()
    {
        return visualGravePositions;
    }

    /**
     * Whether this graveyard owns the headstone slot at the given position.
     *
     * @param pos the position to test.
     * @return true if the blueprint put a headstone slot there.
     */
    public boolean isVisualGravePos(final BlockPos pos)
    {
        for (final Tuple<BlockPos, Direction> vgp : visualGravePositions)
        {
            if (vgp.getA().equals(pos))
            {
                return true;
            }
        }
        return false;
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return GRAVEYARD;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return MAX_BUILDING_LEVEL;
    }

    @Override
    public void registerBlockPosition(@NotNull final BlockState state, @NotNull final BlockPos pos, @NotNull final Level world)
    {
        super.registerBlockPosition(state, pos, world);
        if (state.getBlock() == ModBlocks.blockNamedGrave)
        {
            visualGravePositions.add(new Tuple<>(pos, state.getValue(AbstractBlockMinecoloniesNamedGrave.FACING)));
        }
    }

    /**
     * Return a random free visual grave position
     */
    public Tuple<BlockPos, Direction> getRandomFreeVisualGravePos()
    {
        if (visualGravePositions.isEmpty())
        {
            return null;
        }

        final List<Tuple<BlockPos, Direction>> availablePos = new ArrayList<Tuple<BlockPos, Direction>>();
        for(final Tuple<BlockPos, Direction> tuple : visualGravePositions)
        {
            if (getColony().getWorld().getBlockState(tuple.getA()).canBeReplaced())
            {
                availablePos.add(tuple);
            }
        }

        if (availablePos.isEmpty())
        {
            return null;
        }

        Collections.shuffle(availablePos);
        return availablePos.get(0);
    }
}
