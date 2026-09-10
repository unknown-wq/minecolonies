package com.ldtteam.domumornamentum.datagen.floatingcarpet;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.FloatingCarpetBlock;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Port note: NeoForge's {@code models().getExistingFile(id)} only existed to run the id past the
 * {@code ExistingFileHelper}. Fabric has no such validation, so a plain {@link Identifier} is enough.
 */
public class FloatingCarpetBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        ModBlocks.getInstance().getFloatingCarpets().forEach(block -> generateFor(models, block));
    }

    private void generateFor(final ModelCollector models, final FloatingCarpetBlock floatingCarpetBlock) {
        final Identifier minecraftCarpetModel =
                Identifier.withDefaultNamespace("block/" + floatingCarpetBlock.getColor().getName() + "_carpet");
        models.simpleBlock(floatingCarpetBlock, minecraftCarpetModel);
        models.simpleBlockItem(floatingCarpetBlock, minecraftCarpetModel);
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Floating Carpet BlockStates Provider";
    }
}
