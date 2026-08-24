package com.ldtteam.domumornamentum.datagen.shingle.slab;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.ShingleSlabBlock;
import com.ldtteam.domumornamentum.block.types.ShingleSlabShapeType;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.StairBlock;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class ShingleSlabBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        createBlockstateFile(models, ModBlocks.getInstance().getShingleSlab());
    }

    private void createBlockstateFile(final ModelCollector models, final ShingleSlabBlock shingle) {
        final MultiPartGenerator builder = models.multiPart(shingle);
        final Map<ShingleSlabShapeType, Identifier> blockModels = new EnumMap<>(ShingleSlabShapeType.class);

        for (final Direction facingValue : StairBlock.FACING.getPossibleValues())
        {
            for (final ShingleSlabShapeType shapeValue : ShingleSlabBlock.SHAPE.getPossibleValues())
            {
                final Identifier model = blockModels.computeIfAbsent(shapeValue, shape -> models.materiallyTextured(
                        "block/shingle_slab/" + shape.name().toLowerCase(Locale.ROOT),
                        ModelCollector.modLoc("block/shingle_slab/shingle_slab_" + shape.name().toLowerCase(Locale.ROOT) + "_spec")));

                builder.with(
                        new ConditionBuilder()
                                .term(StairBlock.FACING, facingValue)
                                .term(ShingleSlabBlock.SHAPE, shapeValue),
                        ModelCollector.variant(model, 0, getYFromFacing(facingValue)));
            }
        }

        models.blockState(builder);

        models.itemModel(shingle, ModelCollector.registryPath(shingle),
                MateriallyTexturedModelBuilder.withParent(blockModels.get(ShingleSlabShapeType.TOP))
                        .defaultItemTransforms()
                        .build());
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Shingle Slabs BlockStates Provider";
    }

    private int getYFromFacing(final Direction facing)
    {
        return switch (facing) {
            default -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
        };
    }
}
