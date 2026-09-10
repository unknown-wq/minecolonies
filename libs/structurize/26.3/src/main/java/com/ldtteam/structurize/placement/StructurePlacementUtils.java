package com.ldtteam.structurize.placement;

import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.operations.PlaceStructureOperation;
import com.ldtteam.structurize.placement.structure.CreativeStructureHandler;
import com.ldtteam.structurize.placement.structure.IStructureHandler;
import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.management.Manager;
import com.ldtteam.structurize.api.RotationMirror;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Utility methods related to structure placement.
 */
public class StructurePlacementUtils
{
    /**
     * Load a structure into this world
     * and place it in the right position and rotation.
     *
     * @param worldObj the world to load it in
     * @param blueprint the structures blueprint
     * @param pos      coordinates
     * @param rotMir   the rotation and the mirror used.
     * @param fancyPlacement if fancy or complete.
     * @param player   the placing player.
     */
    public static void loadAndPlaceStructureWithRotation(
      final Level worldObj, final Blueprint blueprint,
      final BlockPos pos, final RotationMirror rotMir,
      final boolean fancyPlacement,
      final ServerPlayer player)
    {
        try
        {
            final IStructureHandler structure = new CreativeStructureHandler(worldObj, pos, blueprint, rotMir, fancyPlacement);
            if (fancyPlacement)
            {
                structure.fancyPlacement();
            }
            structure.getBluePrint().setRotationMirror(rotMir, worldObj);

            final StructurePlacer instantPlacer = new StructurePlacer(structure);
            Manager.addToQueue(new PlaceStructureOperation(instantPlacer, player));
        }
        catch (final IllegalStateException e)
        {
            Log.getLogger().warn("Could not load structure!", e);
        }
    }

}
