package com.ldtteam.domumornamentum.datagen.allbrick;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.AllBrickBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class AllBrickBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        ModBlocks.getInstance().getAllBrickBlocks().forEach(block -> generateFor(models, block));
    }

    private void generateFor(final ModelCollector models, final AllBrickBlock allBrickBlock) {
        final String name = Objects.requireNonNull(allBrickBlock.getRegistryName()).getPath();
        final Identifier blockModel = models.materiallyTextured(
                "block/allbrick/" + name,
                ModelCollector.modLoc("block/allbrick/" + name + "_spec"));

        models.simpleBlock(allBrickBlock, blockModel);

        models.itemModel(allBrickBlock, name,
                MateriallyTexturedModelBuilder.withParent(blockModel).defaultItemTransforms().build());
    }

    @NotNull
    @Override
    public String getName()
    {
        return "All Brick BlockStates Provider";
    }
}
