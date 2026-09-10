package com.ldtteam.domumornamentum.datagen.door.fancy;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.FancyDoorBlock;
import com.ldtteam.domumornamentum.block.types.FancyDoorType;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class FancyDoorsBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models)
    {
        fancyDoorBlock(models, ModBlocks.getInstance().getFancyDoor(), FancyDoorType::getSerializedName);
    }

    private void fancyDoorBlock(final ModelCollector models, final FancyDoorBlock block, final Function<FancyDoorType, String> baseName)
    {
        final Function<FancyDoorType, Identifier> bottomLeft = createModel(models, baseName, "bottom_left");
        final Function<FancyDoorType, Identifier> bottomLeftOpen = createModel(models, baseName, "bottom_left_open");
        final Function<FancyDoorType, Identifier> bottomRight = createModel(models, baseName, "bottom_right");
        final Function<FancyDoorType, Identifier> bottomRightOpen = createModel(models, baseName, "bottom_right_open");
        final Function<FancyDoorType, Identifier> topLeft = createModel(models, baseName, "top_left");
        final Function<FancyDoorType, Identifier> topLeftOpen = createModel(models, baseName, "top_left_open");
        final Function<FancyDoorType, Identifier> topRight = createModel(models, baseName, "top_right");
        final Function<FancyDoorType, Identifier> topRightOpen = createModel(models, baseName, "top_right_open");

        models.blockState(ModelCollector.forAllStatesExcept(block, state -> {
            int yRot = ((int) state.getValue(FancyDoorBlock.FACING).toYRot()) + 90;
            final boolean right = state.getValue(FancyDoorBlock.HINGE) == DoorHingeSide.RIGHT;
            final boolean open = state.getValue(FancyDoorBlock.OPEN);
            final boolean lower = state.getValue(FancyDoorBlock.HALF) == DoubleBlockHalf.LOWER;
            final FancyDoorType type = state.getValue(FancyDoorBlock.TYPE);
            if (open)
            {
                yRot += 90;
            }
            if (right && open)
            {
                yRot += 180;
            }
            yRot %= 360;

            Identifier model = null;
            if (lower && right && open)
            {
                model = bottomRightOpen.apply(type);
            } else if (lower && !right && open)
            {
                model = bottomLeftOpen.apply(type);
            }
            if (lower && right && !open)
            {
                model = bottomRight.apply(type);
            } else if (lower && !right && !open)
            {
                model = bottomLeft.apply(type);
            }
            if (!lower && right && open)
            {
                model = topRightOpen.apply(type);
            } else if (!lower && !right && open)
            {
                model = topLeftOpen.apply(type);
            }
            if (!lower && right && !open)
            {
                model = topRight.apply(type);
            } else if (!lower && !right && !open)
            {
                model = topLeft.apply(type);
            }

            return ModelCollector.variant(model, 0, yRot);
        }, FancyDoorBlock.POWERED));

        // See DoorsBlockStateProvider / ModelCollector#selectingItemModel.
        models.selectingItemModel(block, FancyDoorBlock.TYPE, FancyDoorType.FULL,
                type -> models.itemModel("fancy_door_" + type.getSerializedName(),
                        MateriallyTexturedModelBuilder
                                .withParent(Constants.resLocDO("item/door/fancy/door_" + type.getSerializedName() + "_spec"))
                                .doorItemTransforms()
                                .build()));
    }

    private Function<FancyDoorType, Identifier> createModel(final ModelCollector models,
                                                            final Function<FancyDoorType, String> baseName,
                                                            final String stateDescription)
    {
        return type -> models.materiallyTextured(
                "block/door/fancy/door_" + baseName.apply(type) + "_" + stateDescription,
                Constants.resLocDO("block/door/fancy/door_" + baseName.apply(type) + "_" + stateDescription + "_spec"));
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Fancy Doors BlockStates Provider";
    }
}
