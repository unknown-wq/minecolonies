package com.ldtteam.domumornamentum.datagen.slab;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.vanilla.SlabBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.world.level.block.SlabBlock.TYPE;

public class SlabBlockStateProvider implements IBlockStateSubProvider
{
    @Override
    public void generate(final ModelCollector models) {
        createBlockstateFile(models, ModBlocks.getInstance().getSlab());
    }

    private void createBlockstateFile(final ModelCollector models, final SlabBlock slabBlock)
    {
        final MultiPartGenerator builder = models.multiPart(slabBlock);
        for (final SlabType value : SlabType.values()) {
            final Identifier model = models.materiallyTextured(
                    "block/slab/" + value.getSerializedName(),
                    ModelCollector.modLoc("block/slab/slab_" + value.getSerializedName() + "_spec"));
            builder.with(ModelCollector.when(TYPE, value), ModelCollector.variant(model));
        }

        models.blockState(builder);

        models.itemModel(slabBlock, ModelCollector.registryPath(slabBlock),
                MateriallyTexturedModelBuilder.withParent(ModelCollector.modLoc("item/slab/slab_spec"))
                        .customLoader()
                        .defaultItemTransforms()
                        .build());
    }

    @NotNull
    @Override
    public String getName()
    {
        return "Slab BlockStates Provider";
    }
}
