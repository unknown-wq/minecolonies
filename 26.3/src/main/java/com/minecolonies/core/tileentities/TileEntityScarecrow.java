package com.minecolonies.core.tileentities;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.tileentities.AbstractTileEntityScarecrow;
import com.minecolonies.api.tileentities.ScareCrowType;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

import static com.minecolonies.core.colony.buildingextensions.FarmField.*;

/**
 * The scarecrow tile entity to store extra data.
 */
@SuppressWarnings("PMD.ExcessiveImports")
public class TileEntityScarecrow extends AbstractTileEntityScarecrow
{
    /**
     * Random generator.
     */
    private final Random random = new Random();

    /**
     * The colony this field is located in.
     */
    private IColony currentColony;

    /**
     * The type of the scarecrow.
     */
    private ScareCrowType type;

    /**
     * The size of the field in all four directions
     * in the same order as {@link Direction}:
     * S, W, N, E
     */
    private int[] fieldSize = {DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE};

    /**
     * Creates an instance of the tileEntity.
     */
    public TileEntityScarecrow(final BlockPos pos, final BlockState state)
    {
        super(pos, state);
    }

    @Override
    public ScareCrowType getScarecrowType()
    {
        if (this.type == null)
        {
            final ScareCrowType[] values = ScareCrowType.values();
            this.type = values[this.random.nextInt(values.length)];
        }
        return this.type;
    }

    @Override
    public IColony getCurrentColony()
    {
        if (currentColony == null && level != null)
        {
            this.currentColony = IColonyManager.getInstance().getIColony(level, worldPosition);
        }
        return currentColony;
    }

    @Override
    public void saveAdditional(@NotNull final ValueOutput compoundTag)
    {
        super.saveAdditional(compoundTag);
        compoundTag.putIntArray(TAG_RADIUS, fieldSize);
    }

    @Override
    public void loadAdditional(@NotNull final ValueInput compoundTag)
    {
        super.loadAdditional(compoundTag);
        // Length checked because everything downstream indexes all four radii by Direction#get2DDataValue and a save
        // that carries anything else would throw on the first read rather than fall back.
        final int[] stored = compoundTag.getIntArray(TAG_RADIUS).orElse(null);
        if (stored != null && stored.length == RADII_COUNT)
        {
            fieldSize = stored;
        }
    }

    /**
     * @param direction the direction for the radius
     * @param radius    the number of blocks from the scarecrow that the farmer will work with
     */
    public void setFieldSize(Direction direction, int radius)
    {
        // Only the absolute ceiling, as on FarmField: whether a field may be this size is decided once, server side,
        // in FarmFieldPlotResizeMessage. Was Math.min(radius, MAX_RANGE), which free mode has to be able to exceed.
        this.fieldSize[direction.get2DDataValue()] = FarmField.clampRadius(radius);
        setChanged();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag(@NotNull final HolderLookup.Provider provider)
    {
        return saveWithFullMetadata(provider);
    }

    /**
     * Field size.
     * @return the field size.
     */
    public int[] getFieldSize()
    {
        return fieldSize;
    }
}
