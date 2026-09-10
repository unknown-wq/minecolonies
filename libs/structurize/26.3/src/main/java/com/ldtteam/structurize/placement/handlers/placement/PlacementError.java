package com.ldtteam.structurize.placement.handlers.placement;

import net.minecraft.core.BlockPos;


public class PlacementError
{
    public enum PlacementErrorType
    {
        NOT_SOLID,
        INSIDE_COLONY,
        NEEDS_AIR_ABOVE,
        NOT_WATER
    }

    private PlacementErrorType type;
    private BlockPos pos;
    
    public PlacementError(final PlacementErrorType type, final BlockPos pos)
    {
        super();
        this.type = type;
        this.pos = pos;
    }

    public PlacementErrorType getType()
    {
        return type;
    }

    public BlockPos getPos()
    {
        return pos;
    }

    public void setPos(final BlockPos pos)
    {
        this.pos = pos;
    }

}
