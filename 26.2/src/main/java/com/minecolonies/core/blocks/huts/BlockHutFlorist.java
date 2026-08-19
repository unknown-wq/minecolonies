package com.minecolonies.core.blocks.huts;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import org.jetbrains.annotations.NotNull;

public class BlockHutFlorist extends AbstractBlockHut<BlockHutFlorist>
{
    public BlockHutFlorist()
    {
        super("blockhutflorist");
    }

    @NotNull
    @Override
    public String getHutName()
    {
        return "blockhutflorist";
    }

    @Override
    public BuildingEntry getBuildingEntry()
    {
        return ModBuildings.florist.get();
    }
}
