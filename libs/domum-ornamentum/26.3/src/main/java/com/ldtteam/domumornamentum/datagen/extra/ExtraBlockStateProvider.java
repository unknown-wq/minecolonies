package com.ldtteam.domumornamentum.datagen.extra;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.ExtraBlock;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public class ExtraBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        ModBlocks.getInstance().getExtraTopBlocks().forEach(block -> generateFor(models, block));
    }

    private void generateFor(final ModelCollector models, final ExtraBlock extraBlock) {
        final String name = extraBlock.getRegistryName().getPath();
        final Identifier cubeAll = models.cubeAll(
                "block/extra/" + extraBlock.getType().getCategory().name().toLowerCase(Locale.ROOT) + "/" + name,
                ModelCollector.modLoc("block/extra/" + name));
        models.simpleBlock(extraBlock, cubeAll);
        models.simpleBlockItem(extraBlock, cubeAll);
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Extra BlockStates Provider";
    }
}
