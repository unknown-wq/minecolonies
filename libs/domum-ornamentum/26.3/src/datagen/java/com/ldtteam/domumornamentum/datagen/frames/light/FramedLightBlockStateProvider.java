package com.ldtteam.domumornamentum.datagen.frames.light;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.FramedLightBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class FramedLightBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        ModBlocks.getInstance().getFramedLights().forEach(block -> generateFor(models, block));
    }

    private void generateFor(final ModelCollector models, final FramedLightBlock framedLightBlock) {
        final String name = Objects.requireNonNull(framedLightBlock.getRegistryName()).getPath();
        final Identifier blockModel = models.materiallyTextured(
                "block/framed_light/" + name,
                ModelCollector.modLoc("block/framed_light/" + name + "_spec"));

        models.simpleBlock(framedLightBlock, blockModel);

        models.itemModel(framedLightBlock, name,
                MateriallyTexturedModelBuilder.withParent(blockModel).defaultItemTransforms().build());
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Framed Light BlockStates Provider";
    }
}
