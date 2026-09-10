package com.ldtteam.domumornamentum.datagen.frames.dynamic;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.DynamicTimberFrameBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class DynamicTimberFramesBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        generateFor(models, ModBlocks.getInstance().getDynamicTimberFrame());
    }

    private void generateFor(final ModelCollector models, final DynamicTimberFrameBlock timberFrameBlock) {
        final String name = Objects.requireNonNull(timberFrameBlock.getRegistryName()).getPath();
        final Identifier blockModel = models.materiallyTextured(
                "block/timber_frame/" + name,
                ModelCollector.modLoc("block/timber_frame/" + name + "_spec"));

        models.simpleBlock(timberFrameBlock, blockModel);

        models.itemModel(timberFrameBlock, name,
                MateriallyTexturedModelBuilder.withParent(blockModel).defaultItemTransforms().build());
    }

    @NotNull
    @Override
    public String getName() {
        return "Dynamic Timber Frames BlockStates Provider";
    }
}
