package com.minecolonies.core.placementhandlers;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.placement.IPlacementContext;
import com.ldtteam.structurize.placement.handlers.placement.IPlacementHandler;
// 26.2: IPlacementHandler#doesWorldStateMatchBlueprintState takes Structurize's own Tuple now --
// net.minecraft.util.Tuple is gone and each mod grew its own replacement.
import com.ldtteam.structurize.api.Tuple;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NyliumBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NetherrackPlacementHandler implements IPlacementHandler
{
    @Override
    public boolean canHandle(@NotNull Level world, @NotNull BlockPos pos, @NotNull BlockState blockState)
    {
        return blockState.getBlock() instanceof NyliumBlock;
    }

    @Override
    public ActionProcessingResult handle(
      @NotNull Level world,
      @NotNull BlockPos pos,
      @NotNull BlockState blockState,
      @Nullable CompoundTag tileEntityData,
      @NotNull final IPlacementContext placementContext)
    {
        return !world.setBlock(pos, blockState, 3) ? ActionProcessingResult.DENY : ActionProcessingResult.SUCCESS;
    }

    @Override
    public List<ItemStack> getRequiredItems(@NotNull Level world,
        @NotNull BlockPos pos,
        @NotNull BlockState blockState,
        @Nullable CompoundTag tileEntityData,
        @NotNull final IPlacementContext placementContext)
    {
        List<ItemStack> itemList = new ArrayList<>();
        if (placementContext.fancyPlacement())
        {
            itemList.add(new ItemStack(Blocks.NETHERRACK));
        }
        else
        {
            itemList.add(new ItemStack(blockState.getBlock()));
        }

        return itemList;
    }

    @Override
    public boolean doesWorldStateMatchBlueprintState(
        final BlockState worldState,
        final BlockState blueprintState,
        final Tuple<BlockEntity, CompoundTag> blockEntityData,
        @NotNull final IPlacementContext placementContext)
    {
        if (placementContext.fancyPlacement())
        {
            return worldState.getBlock() instanceof NyliumBlock || worldState.getBlock() == Blocks.NETHERRACK;
        }
        return worldState.equals(blueprintState);
    }
}
