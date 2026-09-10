package com.ldtteam.domumornamentum.datagen.post;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.PostBlock;
import com.ldtteam.domumornamentum.block.types.PostType;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static com.ldtteam.domumornamentum.block.AbstractPostBlock.FACING;
import static com.ldtteam.domumornamentum.block.AbstractPostBlock.UPRIGHT;

public class PostBlockStateProvider implements IBlockStateSubProvider {

    @Override
    public void generate(final ModelCollector models) {
        final MultiPartGenerator builder = models.multiPart(ModBlocks.getInstance().getPost());

        for (final Direction facingValue : FACING.getPossibleValues()) {
            for (final Boolean upright : UPRIGHT.getPossibleValues())  {
                for (final PostType typeValue : PostBlock.TYPE.getPossibleValues()) {
                    final Identifier model = models.materiallyTextured(
                            "block/post/post_" + typeValue.getSerializedName(),
                            ModelCollector.modLoc("block/post/post_%s_spec".formatted(typeValue.getSerializedName())));

                    /*
                       posts can be placed upright normally, or against a face if the face is vertical.
                       If placing horizontally (upright is false), add 90 to X rotation
                     */
                    builder.with(
                            new ConditionBuilder()
                                    .term(UPRIGHT, upright)
                                    .term(FACING, facingValue)
                                    .term(PostBlock.TYPE, typeValue),
                            ModelCollector.variant(model,
                                    getXFromFacing(facingValue) + getUpright(upright, facingValue),
                                    getYFromFacing(facingValue)));
                }
            }
        }

        models.blockState(builder);

        // See ModelCollector#selectingItemModel.
        models.selectingItemModel(ModBlocks.getInstance().getPost(), PostBlock.TYPE, PostType.PLAIN,
                type -> models.itemModel("post_" + type.getSerializedName(),
                        MateriallyTexturedModelBuilder
                                .withParent(ModelCollector.modLoc("block/post/post_" + type.getSerializedName()))
                                .defaultItemTransforms()
                                .build()));
    }

    @Contract(pure = true)
    private int getYFromFacing(final Direction facing) {
        return switch (facing) {
            default -> 0;
            case SOUTH -> 180;
            case WEST -> 270;
            case EAST -> 90;
        };
    }

    private int getXFromFacing(final Direction facing) {
        return switch (facing) {
            case UP -> 180;
            case DOWN -> 0;
            case NORTH -> 0;
            case SOUTH -> 0;
            case WEST -> 0;
            case EAST -> 0;
        };
    }

    private int getUpright(final Boolean upright, final Direction direction) {
        if (!upright) {
            if (direction != Direction.DOWN) {
                if (direction != Direction.UP) {
                    return 90;
                }
            }
        }
        return 0;
    }

    @NotNull
    @Override
    public String getName() {
        return "Post BlockStates Provider";
    }
}
