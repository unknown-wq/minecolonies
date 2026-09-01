package com.ldtteam.structurize.placement;

import com.ldtteam.structurize.placement.structure.IStructureHandler;
import net.minecraft.core.BlockPos;

/**
 * Iterator which walks the structure from outside in , in a clockwise rotation
 */
public class BlueprintIteratorInwardCircle extends AbstractBlueprintIterator
{
    /**
     * Tracking current state.
     */
    private int min_x;
    private int max_x;
    private int min_z;
    private int max_z;

    /**
     * Initialize the blueprint iterator with the structure handler.
     * @param structureHandler the structure handler.
     */
    public BlueprintIteratorInwardCircle(final IStructureHandler structureHandler)
    {
        super(structureHandler, new BlockPos(structureHandler.getBluePrint().getSizeX(), structureHandler.getBluePrint().getSizeY(), structureHandler.getBluePrint().getSizeZ()));
    }

    /**
     * Iterate progress pos.
     * @param up if bottom up, or top down.
     * @return END if finished, or new block if continuous.
     */
    public Result iterate(final boolean up)
    {
        this.prevProgressPos.set(this.progressPos);
        if (this.progressPos.equals(NULL_POS))
        {
            this.progressPos.set(-1, up ? 0 : this.size.getY() - 1, 0);
            this.max_x = this.size.getX()-1;
            this.max_z = this.size.getZ()-1;
            this.min_z = 0;
            this.min_x = 0;
        }

        if ((this.max_x < this.min_x && this.size.getX() % 2 != 0) || (this.max_z < this.min_z && this.size.getZ() % 2 != 0) || (this.max_z < this.min_z && this.max_x < this.min_x))
        {
            this.progressPos.set(-1, up ? this.progressPos.getY() + 1 : this.progressPos.getY() - 1, 0);
            this.max_x = this.size.getX()-1;
            this.max_z = this.size.getZ()-1;
            this.min_z = 0;
            this.min_x = 0;

            if ((up && this.progressPos.getY() >= this.size.getY()) || (!up && this.progressPos.getY() < 0))
            {
                this.reset();
                return Result.AT_END;
            }
        }

        if (this.progressPos.getZ() == min_z && this.progressPos.getX() < max_x)
        {
            this.progressPos.set(this.progressPos.getX() + 1, this.progressPos.getY(), this.progressPos.getZ());
            if (this.progressPos.getX() == max_x)
            {
                this.min_z++;
            }
        }
        else if (this.progressPos.getX() == max_x && this.progressPos.getZ() < max_z)
        {
            this.progressPos.set(this.progressPos.getX(), this.progressPos.getY(), this.progressPos.getZ() + 1);
            if (this.progressPos.getZ() == max_z)
            {
                this.max_x--;
            }
        }
        else if (this.progressPos.getX() == min_x && this.progressPos.getZ() > min_z)
        {
            this.progressPos.set(this.progressPos.getX(), this.progressPos.getY(), this.progressPos.getZ() - 1);
            if (this.progressPos.getZ() == min_z)
            {
                this.min_x++;
            }
        }
        else if (this.progressPos.getZ() == max_z && this.progressPos.getX() > min_x)
        {
            this.progressPos.set(this.progressPos.getX() - 1, this.progressPos.getY(), this.progressPos.getZ());
            if (this.progressPos.getX() == min_x)
            {
                this.max_z--;
            }
        }
        else
        {
            this.progressPos.set(0, up ? this.progressPos.getY() + 1 : this.progressPos.getY() - 1, 0);
            this.max_x = this.size.getX()-1;
            this.max_z = this.size.getZ()-1;
            this.min_z = 0;
            this.min_x = 0;

            if ((up && this.progressPos.getY() >= this.size.getY()) || (!up && this.progressPos.getY() < 0))
            {
                this.reset();
                return Result.AT_END;
            }
        }

        return Result.NEW_BLOCK;
    }

    @Override
    public Result increment()
    {
        return iterate(true);
    }

    @Override
    public Result decrement()
    {
        return iterate(false);
    }

    /**
     * Change the current progressPos. Used when loading progress.
     *
     * @param localPosition new progressPos.
     */
    @Override
    public void setProgressPos(final BlockPos localPosition)
    {
        if (localPosition.equals(NULL_POS))
        {
            super.setProgressPos(localPosition);
        }
        else if (!this.progressPos.equals(localPosition))
        {
            this.progressPos.set(NULL_POS);

            // The replay exists to restore the four ring bounds, which are not derivable from the position
            // alone. It is bounded now: the spiral covers one layer in exactly sizeX * sizeZ steps, so any
            // (x, z) inside the blueprint is reached within that many. An (x, z) outside it -- a resume
            // position saved against a blueprint that has since been resized, say -- used to spin forever,
            // because iterate() resets to NULL_POS at the top of the structure and starts the layer again.
            final int layer = size.getX() * size.getZ();
            for (int i = 0; i <= layer
                && (progressPos.getX() != localPosition.getX() || progressPos.getZ() != localPosition.getZ()); i++)
            {
                iterate(true);
            }

            this.progressPos.set(localPosition.getX() % size.getX(),
              localPosition.getY() % size.getY(),
              localPosition.getZ() % size.getZ());
        }
    }
}
