package com.ldtteam.domumornamentum.datagen.bricks;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.BrickBlock;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class BrickBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models)
    {
        ModBlocks.getInstance().getBricks().forEach(block -> generateFor(models, block));
    }

    private void generateFor(final ModelCollector models, final BrickBlock brickBlock) {
        final Identifier blockModel = models.cubeAll(
                "block/brick/" + brickBlock.getType().getSerializedName() + "_brick",
                Constants.resLocDO("block/brick/" + brickBlock.getType().getSerializedName()));
        models.simpleBlock(brickBlock, blockModel);
        models.simpleBlockItem(brickBlock, blockModel);
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Brick BlockStates Provider";
    }
}
