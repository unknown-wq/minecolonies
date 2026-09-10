package com.ldtteam.domumornamentum.datagen.shingle.normal;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.ShingleBlock;
import com.ldtteam.domumornamentum.block.types.ShingleShapeType;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import com.ldtteam.domumornamentum.shingles.ShingleHeightType;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class ShinglesBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        createBlockstateFile(models, ModBlocks.getInstance().getShingle(ShingleHeightType.DEFAULT), ShingleHeightType.DEFAULT);
        createBlockstateFile(models, ModBlocks.getInstance().getShingle(ShingleHeightType.FLAT), ShingleHeightType.FLAT);
        createBlockstateFile(models, ModBlocks.getInstance().getShingle(ShingleHeightType.FLAT_LOWER), ShingleHeightType.FLAT_LOWER);
    }

    private void createBlockstateFile(final ModelCollector models, final ShingleBlock shingle, final ShingleHeightType heightType)
    {
        if (shingle.getRegistryName() == null)
            return;

        final MultiPartGenerator builder = models.multiPart(shingle);

        final Map<StairsShape, Identifier> blockModels = new EnumMap<>(StairsShape.class);
        for (final Direction facingValue : StairBlock.FACING.getPossibleValues())
        {
            for (final StairsShape shapeValue : ShingleBlock.SHAPE.getPossibleValues())
            {
                for (final Half halfValue : StairBlock.HALF.getPossibleValues())
                {
                    final ShingleShapeType shingleShapeType = ShingleBlock.getTypeFromShape(shapeValue);
                    final Identifier model = blockModels.computeIfAbsent(shapeValue, shape -> models.materiallyTextured(
                            "block/shingle/" + heightType.getId() + shape.name().toLowerCase(Locale.ROOT),
                            ModelCollector.modLoc("block/shingle/" + heightType.getId() + shingleShapeType.name().toLowerCase(Locale.ROOT) + "_spec")));

                    builder.with(
                            new ConditionBuilder()
                                    .term(StairBlock.FACING, facingValue)
                                    .term(StairBlock.SHAPE, shapeValue)
                                    .term(StairBlock.HALF, halfValue),
                            ModelCollector.variant(model,
                                    halfValue == Half.TOP ? 180 : 0,
                                    getYFromFacing(facingValue) + getYFromShape(shapeValue) + getYFromHalf(halfValue, shapeValue)));
                }
            }
        }

        models.blockState(builder);

        models.itemModel(shingle, ModelCollector.registryPath(shingle),
                MateriallyTexturedModelBuilder.withParent(blockModels.get(StairsShape.STRAIGHT))
                        .defaultItemTransforms()
                        .build());
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Shingles BlockStates Provider";
    }

    private int getYFromHalf(final Half half, final StairsShape shape)
    {
        if (half == Half.TOP)
        {
            if (shape == StairsShape.STRAIGHT)
            {
                return 180;
            }
            return 270;
        }
        else
        {
            return 180;
        }
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
                     default -> 180;
                     case SOUTH -> 270;
                     case WEST -> 0;
                     case NORTH -> 90;
                 };
    }
}
