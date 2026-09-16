package com.ldtteam.structurize.operations;

import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.placement.BlockPlacementResult.Result;
import com.ldtteam.structurize.placement.StructurePhasePlacementResult;
import com.ldtteam.structurize.placement.StructurePlacer;
import com.ldtteam.structurize.placement.StructurePlacer.Operation;
import com.ldtteam.structurize.util.BlockUtils;
import com.ldtteam.structurize.util.ChangeStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

import static com.ldtteam.structurize.placement.AbstractBlueprintIterator.NULL_POS;

/**
 * Operation for placing structures.
 */
public class PlaceStructureOperation extends BaseOperation
{
    /**
     * The structure wrapper.
     */
    @NotNull
    private final StructurePlacer placer;

    /**
     * The phase the placement is in.
     */
    private int structurePhase = 0;

    /**
     * The current position to start iterating.
     */
    private BlockPos currentPos;

    /**
     * Default constructor.
     *
     * @param placer the structure wrapper.
     * @param player the player who placed the structure.
     */
    public PlaceStructureOperation(@NotNull final StructurePlacer placer, @Nullable final Player player)
    {
        super(new ChangeStorage(Component.translatable("com.ldtteam.structurize.place_structure", describe(placer)),
          player != null ? player.getUUID() : UUID.randomUUID()));
        this.placer = placer;
        this.currentPos = NULL_POS;
    }

    /**
     * Name for the change storage. The blueprint is only there when the placer was built around a loaded one;
     * when it was built around a future, it is still loading (or failed), and neither may be dereferenced here.
     *
     * @param placer the placer this operation runs.
     * @return the blueprint name, or a placeholder.
     */
    private static String describe(@NotNull final StructurePlacer placer)
    {
        final Blueprint blueprint = placer.getHandler().getBluePrint();
        return blueprint == null ? "[NULL]" : Objects.requireNonNullElse(blueprint.getName(), "[NULL]");
    }

    @Override
    public boolean apply(final ServerLevel world)
    {
        if (placer.isReady() && placer.getHandler().getWorld() == world)
        {
            if (placer.getHandler().getBluePrint() == null)
            {
                // The blueprint resolved to nothing: missing file, unreadable, or refused for its data version.
                // Returning true takes the operation off the queue; leaving it on would retry the same failure
                // every tick, and dereferencing the result would throw out of the level tick instead.
                Log.getLogger()
                    .error("Cancelling structure placement at " + placer.getHandler().getCenterPos()
                             + ": its blueprint could not be loaded.");
                return true;
            }

            StructurePhasePlacementResult result;
            switch (structurePhase)
            {
                case 0:
                    //structure
                    result = placer.executeStructureStep(world, storage, currentPos, Operation.BLOCK_PLACEMENT,
                      () -> placer.getIterator().increment((info, pos, handler) -> !BlockUtils.canBlockFloatInAir(info.getBlockInfo().getState())), false);

                    currentPos = result.getIteratorPos();
                    break;
                case 1:
                    // weak solid
                    result = placer.executeStructureStep(world, storage, currentPos, Operation.BLOCK_PLACEMENT,
                      () -> placer.getIterator().increment((info, pos, handler) -> !BlockUtils.isWeakSolidBlock(info.getBlockInfo().getState())), false);

                    currentPos = result.getIteratorPos();
                    break;
                case 2:
                    //water
                    result = placer.clearWaterStep(world, currentPos);
                    currentPos = result.getIteratorPos();
                    if (result.getBlockResult().getResult() == Result.FINISHED)
                    {
                        currentPos = placer.getIterator().getProgressPos();
                    }
                    break;
                case 3:
                    // not solid
                    result = placer.executeStructureStep(world, storage, currentPos, Operation.BLOCK_PLACEMENT,
                      () -> placer.getIterator().increment((info, pos, handler) -> BlockUtils.isAnySolid(info.getBlockInfo().getState())), false);
                    currentPos = result.getIteratorPos();
                    break;
                default:
                    // entities
                    result = placer.executeStructureStep(world, storage, currentPos, Operation.SPAWN_ENTITY,
                      () -> placer.getIterator().increment((info, pos, handler) -> info.getEntities().length == 0), true);
                    currentPos = result.getIteratorPos();
                    break;
            }

            if (result.getBlockResult().getResult() == Result.FINISHED)
            {
                structurePhase++;
                if (structurePhase > 4)
                {
                    structurePhase = 0;
                    currentPos = null;
                    placer.getHandler().onCompletion();
                }
            }

            return currentPos == null;
        }
        return false;
    }
}
