package com.ldtteam.structurize.placement.structure;

import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.StructurePackMeta;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.api.RotationMirror;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Abstract implementation of the handler holding information that is common for all handlers.
 */
public abstract class AbstractStructureHandler implements IStructureHandler
{
    /**
     * The blueprint future.
     */
    private Future<Blueprint> blueprintFuture = null;

    /**
     * Whether the future above has already been taken. A future that resolved to null must not be asked again,
     * or every call re-reports the same failure.
     */
    private boolean blueprintFutureTaken = false;
    
    /**
     * blueprint of the structure.
     */
    private Blueprint               blueprint;

    /**
     * The used settings for the placement.
     */
    private RotationMirror rotMir;

    /**
     * The minecraft world this struture is displayed in.
     */
    private Level world;

    /**
     * The anchor position this structure will be
     * placed on in the minecraft world.
     */
    private BlockPos worldPos;

    /**
     * Abstract constructor of structure handler.
     * @param world the world it gets.
     * @param worldPos the position the anchor of the structure got placed.
     * @param blueprintFuture the name of the structure.
     * @param rotMir the placement settings.
     */
    public AbstractStructureHandler(final Level world, final BlockPos worldPos, final Future<Blueprint> blueprintFuture, final RotationMirror rotMir)
    {
        this.world = world;
        this.worldPos = worldPos;
        this.rotMir = rotMir;
        this.blueprintFuture = blueprintFuture;
    }

    /**
     * Load the handler with the blueprint already.
     * @param world the world.
     * @param pos the position.
     * @param blueprint the blueprint.
     * @param rotMir the placement settings.
     */
    public AbstractStructureHandler(final Level world, final BlockPos pos, final Blueprint blueprint, final RotationMirror rotMir)
    {
        this.world = world;
        this.worldPos = pos;
        this.rotMir = rotMir;
        this.blueprint = blueprint;
    }

    @Override
    public void triggerSuccess(final BlockPos pos, final List<ItemStack> requiredRes, final boolean placement)
    {
        // Worked out once: getProgressPosInWorld allocates, and this runs for every iterated position, not
        // only the ones actually placed.
        final BlockPos inWorld = getProgressPosInWorld(pos);
        final BlockEntity be = getWorld().getBlockEntity(inWorld);
        if (be instanceof final IBlueprintDataProviderBE dataProvider)
        {
            if (inWorld.equals(worldPos))
            {
                // The pack can be gone -- an unloaded or renamed pack leaves the blueprint holding a name that
                // no longer resolves -- and this used to dereference the lookup straight away.
                final StructurePackMeta pack = StructurePacks.getStructurePack(getBluePrint().getPackName());
                if (pack != null)
                {
                    dataProvider.setBlueprintPath(
                        pack.getSubPath(getBluePrint().getFilePath().resolve(getBluePrint().getFileName())) + ".blueprint");
                }
                else
                {
                    Log.getLogger().warn("No structure pack named '" + getBluePrint().getPackName()
                                           + "'; the placed block keeps no blueprint path.");
                }
            }
            dataProvider.setPackName(getBluePrint().getPackName());
        }
    }

    @Override
    public boolean hasBluePrint()
    {
        return blueprint != null;
    }

    @Override
    public void setBlueprint(final Blueprint blueprint)
    {
        this.blueprint = blueprint;
    }

    @Override
    public Blueprint getBluePrint()
    {
        if (blueprint == null && blueprintFuture != null && !blueprintFutureTaken)
        {
            if (!blueprintFuture.isDone())
            {
                return null;
            }

            blueprintFutureTaken = true;
            try
            {
                // A refused or unreadable blueprint resolves to null here, which is a legal result and not an
                // error to dereference: the caller has to be able to see that there is nothing to place.
                final Blueprint loaded = blueprintFuture.get();
                if (loaded == null)
                {
                    Log.getLogger().error("Structure at " + worldPos + " could not be loaded; nothing will be placed.");
                }
                else
                {
                    loaded.setRotationMirror(rotMir, world);
                }
                blueprint = loaded;
            }
            catch (InterruptedException | ExecutionException e)
            {
                Log.getLogger().error("Failed to load the structure at " + worldPos, e);
            }
        }
        return this.blueprint;
    }

    @Override
    public Level getWorld()
    {
        return this.world;
    }

    @Override
    public BlockPos getCenterPos()
    {
        return this.worldPos;
    }

    @Override
    public RotationMirror getRotationMirror()
    {
        return this.rotMir;
    }
    
    @Override
    public boolean isReady()
    {
        return blueprint != null || (blueprintFuture != null && blueprintFuture.isDone());
    }
}
