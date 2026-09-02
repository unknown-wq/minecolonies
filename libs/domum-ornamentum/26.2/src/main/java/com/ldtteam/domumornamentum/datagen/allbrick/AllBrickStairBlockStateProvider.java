package com.ldtteam.domumornamentum.datagen.allbrick;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.AllBrickStairBlock;
import com.ldtteam.domumornamentum.block.decorative.ShingleBlock;
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

import java.util.Objects;

import static net.minecraft.world.level.block.StairBlock.FACING;
import static net.minecraft.world.level.block.StairBlock.HALF;
import static net.minecraft.world.level.block.StairBlock.SHAPE;

public class AllBrickStairBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        ModBlocks.getInstance().getAllBrickStairBlocks().forEach(block -> generateFor(models, block));
    }

    private void generateFor(final ModelCollector models, final AllBrickStairBlock allBrickStairBlock)
    {
        final String name = Objects.requireNonNull(allBrickStairBlock.getRegistryName()).getPath();
        final MultiPartGenerator builder = models.multiPart(allBrickStairBlock);

        for (final Direction facingValue : FACING.getPossibleValues())
        {
            for (final StairsShape shapeValue : ShingleBlock.SHAPE.getPossibleValues())
            {
                for (final Half halfValue : HALF.getPossibleValues())
                {
                    final String suffix = getTypeFromShape(shapeValue);
                    final Identifier model = models.materiallyTextured(
                            "block/allbrick/" + name + suffix,
                            ModelCollector.modLoc("block/allbrick/" + name + suffix + "_spec"));

                    builder.with(
                            new ConditionBuilder()
                                    .term(FACING, facingValue)
                                    .term(SHAPE, shapeValue)
                                    .term(HALF, halfValue),
                            ModelCollector.variant(model,
                                    halfValue == Half.TOP ? 180 : 0,
                                    getYFromFacing(facingValue) + getYFromShape(shapeValue) + getYFromHalf(halfValue, shapeValue)));
                }
            }
        }

        models.blockState(builder);

        models.itemModel(allBrickStairBlock, name,
                MateriallyTexturedModelBuilder.withParent(ModelCollector.modLoc("block/allbrick/" + name))
                        .customLoader()
                        .defaultItemTransforms()
                        .build());
    }

    @NotNull
    @Override
    public String getName()
    {
        return "All Brick Stair BlockStates Provider";
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
                     case INNER_LEFT, INNER_RIGHT -> "_inner";
                     case OUTER_LEFT, OUTER_RIGHT -> "_outer";
                     default -> "";
                 };
    }
}
