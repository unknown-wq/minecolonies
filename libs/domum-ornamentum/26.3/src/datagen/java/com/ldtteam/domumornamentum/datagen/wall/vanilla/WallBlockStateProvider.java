package com.ldtteam.domumornamentum.datagen.wall.vanilla;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.vanilla.WallBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.WallSide;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static net.minecraft.world.level.block.WallBlock.UP;

public class WallBlockStateProvider implements IBlockStateSubProvider {

    @Override
    public void generate(final ModelCollector models) {
        createBlockstateFile(models, ModBlocks.getInstance().getWall());
    }

    private void createBlockstateFile(final ModelCollector models, final WallBlock wallBlock) {
        final MultiPartGenerator builder = models.multiPart(wallBlock);

        builder.with(ModelCollector.when(UP, true),
                ModelCollector.variant(models.materiallyTextured(
                        "block/wall/wall_post", ModelCollector.modLoc("block/wall/wall_post_spec")), 0, 0, true));

        for (final Direction possibleValue : HorizontalDirectionalBlock.FACING.getPossibleValues()) {
            for (final WallSide value : WallSide.values()) {
                if (value == WallSide.NONE)
                    continue;

                final String suffix = value == WallSide.TALL ? "_tall" : "";
                final Identifier model = models.materiallyTextured(
                        "block/wall/wall_side" + suffix,
                        ModelCollector.modLoc("block/wall/wall_side" + suffix + "_spec"));

                builder.with(
                        ModelCollector.when(Objects.requireNonNull(WallBlock.PROPERTIES.get(possibleValue)), value),
                        ModelCollector.variant(model, 0, getYFromFacing(possibleValue), true));
            }
        }

        models.blockState(builder);

        models.itemModel(wallBlock, ModelCollector.registryPath(wallBlock),
                MateriallyTexturedModelBuilder.withParent(ModelCollector.modLoc("item/wall/wall_spec"))
                        .customLoader()
                        .defaultItemTransforms()
                        .build());
    }

    @NotNull
    @Override
    public String getName() {
        return "Wall BlockStates Provider";
    }

    private int getYFromFacing(final Direction facing) {
        return switch (facing) {
            default -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
        };
    }
}
