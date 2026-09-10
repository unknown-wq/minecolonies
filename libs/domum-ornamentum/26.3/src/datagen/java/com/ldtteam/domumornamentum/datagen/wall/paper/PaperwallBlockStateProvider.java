package com.ldtteam.domumornamentum.datagen.wall.paper;

import com.ldtteam.domumornamentum.block.AbstractBlockPane;
import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.PaperWallBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

public class PaperwallBlockStateProvider implements IBlockStateSubProvider {

    @Override
    public void generate(final ModelCollector models) {
        createBlockstateFile(models, ModBlocks.getInstance().getPaperWall(), "");
        createBlockstateFile(models, ModBlocks.getInstance().getTiledPaperWall(), "tiled");
    }

    private void createBlockstateFile(final ModelCollector models, final PaperWallBlock paperWallBlock, final String type) {
        final MultiPartGenerator builder = models.multiPart(paperWallBlock);

        builder.with(ModelCollector.variant(models.materiallyTextured(
                "block/" + type + "paperwall/blockpaperwall_post",
                ModelCollector.modLoc("block/" + type + "paperwall/blockpaperwall_post_spec")), 0, 0, true));

        for (final Direction possibleValue : HorizontalDirectionalBlock.FACING.getPossibleValues()) {
            final String dir = possibleValue.name().toLowerCase(Locale.ROOT);

            final Identifier on = models.materiallyTextured(
                    "block/" + type + "paperwall/blockpaperwall_side_" + dir,
                    ModelCollector.modLoc("block/" + type + "paperwall/blockpaperwall_side_" + dir + "_spec"));
            builder.with(ModelCollector.when(Objects.requireNonNull(AbstractBlockPane.PROPERTIES.get(possibleValue)), true),
                    ModelCollector.variant(on, 0, 0, true));

            final Identifier off = models.materiallyTextured(
                    "block/" + type + "paperwall/blockpaperwall_side_off_" + dir,
                    ModelCollector.modLoc("block/" + type + "paperwall/blockpaperwall_side_off_" + dir + "_spec"));
            builder.with(ModelCollector.when(Objects.requireNonNull(AbstractBlockPane.PROPERTIES.get(possibleValue)), false),
                    ModelCollector.variant(off, 0, 0, true));
        }

        models.blockState(builder);

        models.itemModel(paperWallBlock, ModelCollector.registryPath(paperWallBlock),
                MateriallyTexturedModelBuilder
                        .withParent(ModelCollector.modLoc("item/paperwall/block" + type + "paperwall_spec"))
                        .customLoader()
                        .defaultItemTransforms()
                        .build());
    }

    @NotNull
    @Override
    public String getName() {
        return "Paperwall BlockStates Provider";
    }
}
