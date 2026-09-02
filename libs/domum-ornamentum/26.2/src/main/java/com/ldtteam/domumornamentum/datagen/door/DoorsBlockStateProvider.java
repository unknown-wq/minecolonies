package com.ldtteam.domumornamentum.datagen.door;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.types.DoorType;
import com.ldtteam.domumornamentum.block.vanilla.DoorBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Port note: {@code getVariantBuilder(block).forAllStatesExcept(fn, POWERED)} maps onto
 * {@link ModelCollector#forAllStatesExcept}; see the javadoc there for why the vanilla
 * {@code MultiVariantGenerator}/{@code PropertyDispatch} route is not usable for a five-property door.
 */
public class DoorsBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models)
    {
        doorBlock(models, ModBlocks.getInstance().getDoor(), DoorType::getSerializedName);
    }

    private void doorBlock(final ModelCollector models, final DoorBlock block, final Function<DoorType, String> baseName)
    {
        final Function<DoorType, Identifier> bottomLeft = createModel(models, baseName, "bottom_left");
        final Function<DoorType, Identifier> bottomLeftOpen = createModel(models, baseName, "bottom_left_open");
        final Function<DoorType, Identifier> bottomRight = createModel(models, baseName, "bottom_right");
        final Function<DoorType, Identifier> bottomRightOpen = createModel(models, baseName, "bottom_right_open");
        final Function<DoorType, Identifier> topLeft = createModel(models, baseName, "top_left");
        final Function<DoorType, Identifier> topLeftOpen = createModel(models, baseName, "top_left_open");
        final Function<DoorType, Identifier> topRight = createModel(models, baseName, "top_right");
        final Function<DoorType, Identifier> topRightOpen = createModel(models, baseName, "top_right_open");

        models.blockState(ModelCollector.forAllStatesExcept(block, state -> {
            int yRot = ((int) state.getValue(DoorBlock.FACING).toYRot()) + 90;
            final boolean right = state.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT;
            final boolean open = state.getValue(DoorBlock.OPEN);
            final boolean lower = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
            final DoorType type = state.getValue(DoorBlock.TYPE);
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
        }, DoorBlock.POWERED));

        // Item: the hand written per-type door item models already carry the geometry, so each case only adds
        // DO's door display transforms on top. Replaces the dead
        // models/item/vanilla_doors_compat_spec.json "overrides" block — see ModelCollector#selectingItemModel.
        models.selectingItemModel(block, DoorBlock.TYPE, DoorType.FULL,
                type -> models.itemModel("vanilla_doors_compat_" + type.getSerializedName(),
                        MateriallyTexturedModelBuilder
                                .withParent(Constants.resLocDO("item/door/door_" + type.getSerializedName() + "_spec"))
                                .doorItemTransforms()
                                .build()));
    }

    private Function<DoorType, Identifier> createModel(final ModelCollector models,
                                                       final Function<DoorType, String> baseName,
                                                       final String stateDescription)
    {
        return type -> models.materiallyTextured(
                "block/door/door_" + baseName.apply(type) + "_" + stateDescription,
                Constants.resLocDO("block/door/door_" + baseName.apply(type) + "_" + stateDescription + "_spec"));
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Doors BlockStates Provider";
    }
}
