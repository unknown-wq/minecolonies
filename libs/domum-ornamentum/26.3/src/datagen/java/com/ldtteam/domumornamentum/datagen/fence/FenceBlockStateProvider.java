package com.ldtteam.domumornamentum.datagen.fence;

import com.ldtteam.domumornamentum.block.AbstractBlockFence;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.jetbrains.annotations.NotNull;

public class FenceBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        final MultiPartGenerator builder = models.multiPart(ModBlocks.getInstance().getFence());

        builder.with(ModelCollector.variant(models.materiallyTextured(
                "block/fence/fence_post", ModelCollector.modLoc("block/fence/fence_post_spec"))));

        for (final Direction possibleValue : HorizontalDirectionalBlock.FACING.getPossibleValues()) {
            final Identifier side = models.materiallyTextured(
                    "block/fence/fence_side", ModelCollector.modLoc("block/fence/fence_side_spec"));

            builder.with(
                    ModelCollector.when(AbstractBlockFence.getDirectionalProperties().get(possibleValue), true),
                    ModelCollector.variant(side, 0, getYFromFacing(possibleValue)));
        }

        models.blockState(builder);

        models.itemModel(ModBlocks.getInstance().getFence(),
                ModelCollector.registryPath(ModBlocks.getInstance().getFence()),
                MateriallyTexturedModelBuilder.withParent(ModelCollector.modLoc("item/fence/fence_spec"))
                        .customLoader()
                        .defaultItemTransforms()
                        .build());
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Fence BlockStates Provider";
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
