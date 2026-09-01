package com.minecolonies.api.tileentities;

import com.minecolonies.api.colony.IGraveData;
import com.minecolonies.core.tileentities.TileEntityRack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.Constants.DEFAULT_SIZE;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;

/**
 * Abstract class for minecolonies graves.
 */
public abstract class AbstractTileEntityGrave extends TileEntityRack implements MenuProvider
{
    /**
     * default duration of the countdown before the grave disapear, in ticks (20 ticks / seconds)
     */
    protected static final int DEFAULT_DECAY_TIMER = TICKS_SECOND * 60 * 10;

    /**
     * Is this grave decayed or not
     */
    protected boolean decayed;

    /**
     * The decay timer counting down before the grave decay and then disapear
     */
    protected int decay_timer;

    /**
     * The GraveData of the citizen that spawned this grave.
     */
    @Nullable
    protected IGraveData graveData;

    public AbstractTileEntityGrave(final BlockEntityType<? extends AbstractTileEntityGrave> tileEntityTypeIn, final BlockPos pos, final BlockState state)
    {
        super(tileEntityTypeIn, pos, state, DEFAULT_SIZE * 2);
        decay_timer = DEFAULT_DECAY_TIMER;
        decayed = false;
    }

    /**
     * Delay the decay timer by minutes
     * @param minutes number of minutes to delay the time by
     */
    public void delayDecayTimer(final double minutes)
    {
        decay_timer += minutes * TICKS_SECOND * 60;
    }

    /**
     * How many ticks of life this grave has left, counting both phases: the countdown to turning decayed and, if it
     * has not turned yet, the second full countdown from there to being removed. A grave with the timer disabled
     * reports {@link Integer#MAX_VALUE}, which sorts it last among things that need attention.
     *
     * @return remaining life in ticks.
     */
    public int getRemainingDecayTicks()
    {
        if (decay_timer == -1)
        {
            return Integer.MAX_VALUE;
        }
        return decayed ? decay_timer : decay_timer + DEFAULT_DECAY_TIMER;
    }

    /**
     * Get the graveData of the saved citizen
     */
    public IGraveData getGraveData()
    {
        return graveData;
    }

    /**
     * Set the graveData of the saved citizen
     * @param graveData
     */
    public void setGraveData(IGraveData graveData)
    {
        this.graveData = graveData;
        setChanged();
    }
}
