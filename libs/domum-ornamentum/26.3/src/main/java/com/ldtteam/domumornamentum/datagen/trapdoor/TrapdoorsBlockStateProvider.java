package com.ldtteam.domumornamentum.datagen.trapdoor;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.types.TrapdoorType;
import com.ldtteam.domumornamentum.block.vanilla.TrapdoorBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.Half;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.world.level.block.TrapDoorBlock.HALF;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN;

public class TrapdoorsBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        createBlockstateFile(models, ModBlocks.getInstance().getTrapdoor());
    }

    private void createBlockstateFile(final ModelCollector models, final TrapdoorBlock trapdoor)
    {
        final MultiPartGenerator builder = models.multiPart(trapdoor);
        for (final Direction facingValue : HORIZONTAL_FACING.getPossibleValues())
        {
            for (final TrapdoorType typeValue : TrapdoorBlock.TYPE.getPossibleValues())
            {
                for (final Half halfValue : HALF.getPossibleValues())
                {
                    for (final boolean openValue : OPEN.getPossibleValues())
                    {
                        final Identifier model = models.materiallyTextured(
                                "block/trapdoor/trapdoor_" + typeValue.getSerializedName(),
                                ModelCollector.modLoc("block/trapdoor/trapdoor_" + typeValue.getSerializedName() + "_spec"));

                        builder.with(
                                new ConditionBuilder()
                                        .term(HORIZONTAL_FACING, facingValue)
                                        .term(TrapdoorBlock.TYPE, typeValue)
                                        .term(HALF, halfValue)
                                        .term(OPEN, openValue),
                                ModelCollector.variant(model,
                                        getXFromOpenAndHalf(openValue, halfValue),
                                        getYFromFacing(facingValue) + getYFromOpenAndHalf(openValue, halfValue),
                                        true));
                    }
                }
            }
        }

        models.blockState(builder);

        // Item: one small model per type (geometry inherited from the block model, plus DO's own display
        // transforms) selected by the "type" blockstate property carried on the stack. Replaces the dead
        // models/item/vanilla_trapdoors_compat_spec.json "overrides" block — see
        // ModelCollector#selectingItemModel.
        models.selectingItemModel(trapdoor, TrapdoorBlock.TYPE, TrapdoorType.FULL,
                type -> models.itemModel("vanilla_trapdoors_compat_" + type.getSerializedName(),
                        MateriallyTexturedModelBuilder
                                .withParent(ModelCollector.modLoc("block/trapdoor/trapdoor_" + type.getSerializedName()))
                                .defaultItemTransforms()
                                .build()));
    }

    private int getYFromFacing(final Direction facing)
    {
        return switch (facing)
                 {
                     default -> 0;
                     case SOUTH -> 180;
                     case WEST -> 270;
                     case EAST -> 90;
                 };
    }

    private int getYFromOpenAndHalf(final boolean open, final Half half) {
        if (!open) {
            return half == Half.TOP ? 0 : 180;
        }

        return half == Half.TOP ? 180 : 0;
    }

    private int getXFromOpenAndHalf(final boolean open, final Half half) {
        if (!open) {
            return half == Half.TOP ? 180 : 0;
        }

        return half == Half.TOP ? -90 : 90;
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Trapdoors BlockStates Provider";
    }
}
