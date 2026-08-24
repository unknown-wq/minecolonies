package com.ldtteam.domumornamentum.datagen.fencegate;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.vanilla.FenceGateBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class FenceGateBlockStateProvider implements IBlockStateSubProvider {

    private record FenceGateModelData(boolean wallState, boolean open) { }

    @Override
    public void generate(final ModelCollector models) {
        final MultiPartGenerator builder = models.multiPart(ModBlocks.getInstance().getFenceGate());

        final Map<FenceGateModelData, Identifier> blockModels = new HashMap<>();
        for (final boolean wallState : FenceGateBlock.IN_WALL.getPossibleValues()) {
            for (final boolean open : FenceGateBlock.OPEN.getPossibleValues()) {
                blockModels.put(new FenceGateModelData(wallState, open), generateBlockModel(models, wallState, open));
            }
        }

        for (final Direction direction : HorizontalDirectionalBlock.FACING.getPossibleValues()) {
            for (final boolean wallState : FenceGateBlock.IN_WALL.getPossibleValues()) {
                for (final boolean open : FenceGateBlock.OPEN.getPossibleValues()) {
                    final Identifier blockModel = blockModels.get(new FenceGateModelData(wallState, open));

                    builder.with(
                            new ConditionBuilder()
                                    .term(HorizontalDirectionalBlock.FACING, direction)
                                    .term(FenceGateBlock.IN_WALL, wallState)
                                    .term(FenceGateBlock.OPEN, open),
                            ModelCollector.variant(blockModel, 0, getYFromFacing(direction)));
                }
            }
        }

        models.blockState(builder);

        models.itemModel(ModBlocks.getInstance().getFenceGate(),
                ModelCollector.registryPath(ModBlocks.getInstance().getFenceGate()),
                MateriallyTexturedModelBuilder.withParent(ModelCollector.modLoc("item/fence_gate/fence_gate_spec"))
                        .customLoader()
                        .defaultItemTransforms()
                        .build());
    }

    private Identifier generateBlockModel(final ModelCollector models, final boolean wallState, final boolean open) {
        final String name = "block/fence_gate/fence_gate_"
                + (wallState ? "wall_" : "")
                + (open ? "open" : "");

        final Identifier specLocation = Constants.resLocDO("block/fence_gate/fence_gate_"
                + (wallState ? "wall_" : "")
                + (open ? "open_" : "") + "spec");

        return models.materiallyTextured(name, specLocation);
    }

    @NotNull
    @Override
    public String getName() {
        return "FenceGate BlockStates Provider";
    }

    private int getYFromFacing(final Direction facing) {
        return switch (facing) {
            default -> 0;
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
        };
    }
}
