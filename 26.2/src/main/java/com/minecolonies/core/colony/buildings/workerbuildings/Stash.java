package com.minecolonies.core.colony.buildings.workerbuildings;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.ldtteam.structurize.api.RotationMirror;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IRSComponent;
import com.minecolonies.api.colony.requestsystem.resolver.IRequestResolver;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import net.minecraft.core.BlockPos;
import com.minecolonies.api.util.Tuple;
import org.jetbrains.annotations.NotNull;

/**
 * Class used to manage the stash building block.
 */
public class Stash extends AbstractBuilding implements IRSComponent
{
    /**
     * Description of the block used to set this block.
     */
    private static final String STASH = "stash";

    /**
     * Instantiates the building.
     *
     * @param c the colony.
     * @param l the location.
     */
    public Stash(final IColony c, final BlockPos l)
    {
        super(c, l);
    }

    @Override
    public ImmutableCollection<IRequestResolver<?>> createResolvers()
    {
        return ImmutableList.of();
    }

    @Override
    public Tuple<BlockPos, BlockPos> getCorners()
    {
        return new Tuple<>(getPosition(),getPosition());
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return STASH;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return 0;
    }

    @Override
    public RotationMirror getRotationMirror()
    {
        return RotationMirror.NONE;
    }
}
