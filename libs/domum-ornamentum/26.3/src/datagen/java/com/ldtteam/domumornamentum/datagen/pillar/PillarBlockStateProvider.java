package com.ldtteam.domumornamentum.datagen.pillar;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.decorative.PillarBlock;
import com.ldtteam.domumornamentum.block.types.PillarShapeType;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

public class PillarBlockStateProvider implements IBlockStateSubProvider {

    @Override
    public void generate(final ModelCollector models) {
        for (final PillarBlock pillar : ModBlocks.getInstance().getPillars())
        {
            createBlockStateFile(models, pillar);
        }
    }

    private void createBlockStateFile(final ModelCollector models, final PillarBlock pillar) {
        final MultiPartGenerator builder = models.multiPart(pillar);

        for (final PillarShapeType shape : new PillarShapeType[] {
                PillarShapeType.PILLAR_COLUMN,
                PillarShapeType.PILLAR_BASE,
                PillarShapeType.PILLAR_CAPITAL,
                PillarShapeType.FULL_PILLAR }) {
            final Identifier model = models.materiallyTextured(
                    pillarModelLocation(pillar, shape),
                    ModelCollector.modLoc(pillarSpecModelLocation(pillar, shape)));

            builder.with(ModelCollector.when(PillarBlock.COLUMN, shape),
                    ModelCollector.variant(model, 0, 0, true));
        }

        models.blockState(builder);

        models.itemModel(pillar, ModelCollector.registryPath(pillar),
                MateriallyTexturedModelBuilder
                        .withParent(ModelCollector.modLoc(pillarModelLocation(pillar, PillarShapeType.FULL_PILLAR)))
                        .customLoader()
                        .defaultItemTransforms()
                        .build());
    }

    private String pillarModelLocation(final PillarBlock pillar, final PillarShapeType suffix) {
        return "block/pillar/" + pillar.getRegistryName().getPath() + "_" + suffix.getSerializedName();
    }

    private String pillarSpecModelLocation(final PillarBlock pillar, final PillarShapeType suffix) {
        return "block/pillar/" + pillar.getRegistryName().getPath() + "_" + suffix.getSpecificationName();
    }

    @NotNull
    @Override
    public String getName() {
        return "Pillar BlockStates Provider";
    }
}
