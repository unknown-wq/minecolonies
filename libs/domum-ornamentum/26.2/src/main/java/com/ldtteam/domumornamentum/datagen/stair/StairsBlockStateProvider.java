package com.ldtteam.domumornamentum.datagen.stair;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.vanilla.StairBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.world.level.block.StairBlock.FACING;
import static net.minecraft.world.level.block.StairBlock.HALF;
import static net.minecraft.world.level.block.StairBlock.SHAPE;

public class StairsBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        createBlockstateFile(models, ModBlocks.getInstance().getStair());
    }

    private void createBlockstateFile(final ModelCollector models, final StairBlock stairBlock) {
        final MultiPartGenerator builder = models.multiPart(stairBlock);
        for (final Direction facingValue : FACING.getPossibleValues())
        {
            for (final StairsShape shapeValue : SHAPE.getPossibleValues())
            {
                for (final Half halfValue : HALF.getPossibleValues())
                {
                    final String type = getTypeFromShape(shapeValue);
                    final Identifier model = models.materiallyTextured(
                            "block/stair/" + type,
                            ModelCollector.modLoc("block/stair/" + type + "_spec"));

                    builder.with(
                            new ConditionBuilder()
                                    .term(FACING, facingValue)
                                    .term(SHAPE, shapeValue)
                                    .term(HALF, halfValue),
                            ModelCollector.variant(model,
                                    halfValue == Half.TOP ? 180 : 0,
                                    getYFromFacing(facingValue) + getYFromShape(shapeValue) + getYFromHalf(halfValue, shapeValue),
                                    true));
                }
            }
        }

        models.blockState(builder);

        models.itemModel(stairBlock, ModelCollector.registryPath(stairBlock),
                MateriallyTexturedModelBuilder.withParent(ModelCollector.modLoc("block/stair/stairs"))
                        .customLoader()
                        .defaultItemTransforms()
                        .build());
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Stairs BlockStates Provider";
    }

    private int getYFromHalf(final Half half, final StairsShape shape)
    {
        if (half == Half.TOP)
        {
            if (shape == StairsShape.STRAIGHT)
            {
                return 0;
            }
            return 90;
        }
        return 0;
    }

    private int getYFromShape(final StairsShape shape)
    {
        return switch (shape)
                 {
                     default -> 0;
                     case OUTER_LEFT, INNER_LEFT -> -90;
                 };
    }

    private int getYFromFacing(final Direction facing)
    {
        return switch (facing)
                 {
                     default -> 90;
                     case WEST -> 180;
                     case NORTH -> 270;
                     case EAST -> 0;
                 };
    }

    /**
     * Get the model type from a StairsShape object
     *
     * @param shape the StairsShape object
     * @return the model type for provided StairsShape
     */
    private static String getTypeFromShape(final StairsShape shape)
    {
        return switch (shape)
                 {
                     case INNER_LEFT, INNER_RIGHT -> "stairs_inner";
                     case OUTER_LEFT, OUTER_RIGHT -> "stairs_outer";
                     default -> "stairs";
                 };
    }
}
