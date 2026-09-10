package com.ldtteam.domumornamentum.datagen.panel;

import com.ldtteam.domumornamentum.block.ModBlocks;
import com.ldtteam.domumornamentum.block.types.TrapdoorType;
import com.ldtteam.domumornamentum.block.decorative.PanelBlock;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.datagen.utils.IBlockStateSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ModelCollector;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.properties.Half;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import static com.ldtteam.domumornamentum.block.AbstractPanelBlockTrapdoor.HALF;
import static com.ldtteam.domumornamentum.block.AbstractPanelBlockTrapdoor.OPEN;
import static net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

public class PanelBlockStateProvider implements IBlockStateSubProvider {

    @Override
    public void generate(final ModelCollector models) {
        final MultiPartGenerator builder = models.multiPart(ModBlocks.getInstance().getPanel());

        for (final Direction facingValue : HORIZONTAL_FACING.getPossibleValues()) {
            for (final TrapdoorType typeValue : PanelBlock.TYPE.getPossibleValues()) {
                for (final Half halfValue : HALF.getPossibleValues()) {
                    for (final boolean openValue : OPEN.getPossibleValues()) {
                        final Identifier model = models.materiallyTextured(
                                "block/panel/panel_" + typeValue.getSerializedName(),
                                ModelCollector.modLoc("block/panel/panel_%s_spec".formatted(typeValue.getSerializedName())));

                        builder.with(
                                new ConditionBuilder()
                                        .term(HORIZONTAL_FACING, facingValue)
                                        .term(PanelBlock.TYPE, typeValue)
                                        .term(HALF, halfValue)
                                        .term(OPEN, openValue),
                                ModelCollector.variant(model,
                                        getXFromOpenAndHalf(openValue, halfValue),
                                        getYFromFacing(facingValue) + getYFromOpenAndHalf(openValue, halfValue),
                                        true));
                    }
                }
            }
        }

        models.blockState(builder);

        // See ModelCollector#selectingItemModel: the old models/item/panel_spec.json "overrides" block is dead
        // in 26.2, so the type is dispatched through a minecraft:select item model instead.
        models.selectingItemModel(ModBlocks.getInstance().getPanel(), PanelBlock.TYPE, TrapdoorType.FULL,
                type -> models.itemModel("panel_" + type.getSerializedName(),
                        MateriallyTexturedModelBuilder
                                .withParent(ModelCollector.modLoc("block/panel/panel_" + type.getSerializedName()))
                                .defaultItemTransforms()
                                .build()));
    }

    @Contract(pure = true)
    private int getYFromFacing(final Direction facing) {
        return switch (facing) {
            default -> 0;
            case SOUTH -> 180;
            case WEST -> 270;
            case EAST -> 90;
        };
    }

    private int getYFromOpenAndHalf(final boolean open, final Half half) {
        if (!open) {
            return half == Half.TOP ? 0 : 180;
        }

        return half == Half.TOP ? 180 : 0;
    }

    private int getXFromOpenAndHalf(final boolean open, final Half half) {
        if (!open) {
            return half == Half.TOP ? 180 : 0;
        }

        return half == Half.TOP ? -90 : 90;
    }

    @NotNull
    @Override
    public String getName() {
        return "Panel BlockStates Provider";
    }
}
