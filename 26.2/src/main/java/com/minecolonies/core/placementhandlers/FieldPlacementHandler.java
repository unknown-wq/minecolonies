package com.minecolonies.core.placementhandlers;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.placement.IPlacementContext;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
import com.ldtteam.structurize.util.BlockUtils;
import com.minecolonies.core.blocks.BlockScarecrow;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
// 26.2: IPlacementHandler#doesWorldStateMatchBlueprintState takes Structurize's own Tuple now --
// net.minecraft.util.Tuple is gone and each mod grew its own replacement.
import com.ldtteam.structurize.api.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.ldtteam.structurize.placement.handlers.placement.PlacementHandlers.simplePlacement;

public class FieldPlacementHandler implements IPlacementHandler
{
    @Override
    public boolean canHandle(@NotNull Level world, @NotNull BlockPos pos, @NotNull BlockState blockState)
    {
        return blockState.getBlock() instanceof BlockScarecrow;
    }

    @Override
    public ActionProcessingResult handle(
      @NotNull Level world,
      @NotNull BlockPos pos,
      @NotNull BlockState blockState,
      @Nullable CompoundTag tileEntityData,
      @NotNull final IPlacementContext placementContext)
    {
        if (blockState.getValue(DoorBlock.HALF).equals(DoubleBlockHalf.LOWER))
        {
            return simplePlacement(world, pos, blockState, placementContext.getRotationMirror(), tileEntityData);
        }

        return ActionProcessingResult.SUCCESS;
    }

    @Override
    public List<ItemStack> getRequiredItems(
        @NotNull Level world,
        @NotNull BlockPos pos,
        @NotNull BlockState blockState,
        @Nullable CompoundTag tileEntityData,
        @NotNull final IPlacementContext placementContext)
    {
        List<ItemStack> itemList = new ArrayList<>();
        if (blockState.getValue(DoorBlock.HALF).equals(DoubleBlockHalf.LOWER))
        {
            itemList.add(BlockUtils.getItemStackFromBlockState(blockState));
            if (blockState.getValue(BlockScarecrow.LANTERN))
            {
                itemList.add(new ItemStack(Items.LANTERN));
            }
        }

        return itemList;
    }

    @Override
    public boolean doesWorldStateMatchBlueprintState(
        final BlockState blueprintState,
        final BlockState worldState,
        final Tuple<BlockEntity, CompoundTag> tuple,
        @NotNull final IPlacementContext iPlacementContext)
    {
        return blueprintState.getBlock() == worldState.getBlock();
    }
}
