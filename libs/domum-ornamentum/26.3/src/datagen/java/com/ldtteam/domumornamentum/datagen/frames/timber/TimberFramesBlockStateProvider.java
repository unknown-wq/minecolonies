package com.ldtteam.domumornamentum.datagen.frames.timber;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.TimberFrameBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class TimberFramesBlockStateProvider implements IBlockStateSubProvider {

    @Override
    public void generate(final ModelCollector models) {
        ModBlocks.getInstance().getTimberFrames().forEach(block -> generateFor(models, block));
    }

    private void generateFor(final ModelCollector models, final TimberFrameBlock timberFrameBlock) {
        final String name = Objects.requireNonNull(timberFrameBlock.getRegistryName()).getPath();
        final MultiPartGenerator builder = models.multiPart(timberFrameBlock);

        final Identifier blockModel = models.materiallyTextured(
                "block/timber_frame/" + name,
                ModelCollector.modLoc("block/timber_frame/" + name + "_spec"));

        TimberFrameBlock.FACING.getPossibleValues().forEach(direction -> {
            final boolean rotatable = timberFrameBlock.getTimberFrameType().isRotatable();
            builder.with(ModelCollector.when(TimberFrameBlock.FACING, direction),
                    ModelCollector.variant(blockModel,
                            rotatable ? getXFromDirection(direction) : 0,
                            rotatable ? getYFromDirection(direction) : 0));
        });

        models.blockState(builder);

        models.itemModel(timberFrameBlock, name,
                MateriallyTexturedModelBuilder.withParent(blockModel).defaultItemTransforms().build());
    }

    private int getXFromDirection(final Direction direction) {
        return switch (direction) {
            case UP -> 0;
            case DOWN -> 180;
            default -> 90;
        };
    }

    private int getYFromDirection(final Direction direction) {
        return switch (direction) {
            default -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
        };
    }

    @NotNull
    @Override
    public String getName() {
        return "Timber Frames BlockStates Provider";
    }
}
