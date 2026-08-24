package com.minecolonies.api.util;


// PORT-TODO(structurize): re-check list only. This file compiles against the real 26.2 Structurize
// library and is part of the build; structurize-blocked.txt is nothing but the grep list of files worth
// re-verifying against the real API. build.gradle:128-133 states outright that this build never reads it.

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.placement.structure.CreativeStructureHandler;
import com.minecolonies.api.blocks.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Future;

/**
 * Load only structure handler just to get dimensions etc from structures, not for placement.
 */
public class LoadOnlyStructureHandler extends CreativeStructureHandler
{
    /**
     * The minecolonies specific creative structure placer.
     *
     * @param world          the world.
     * @param pos            the pos it is placed at.
     * @param blueprintFuture  the future of the structure.
     * @param rotMir       the placement settings.
     */
    public LoadOnlyStructureHandler(final Level world, final BlockPos pos, final Future<Blueprint> blueprintFuture, final RotationMirror rotMir)
    {
        super(world, pos, blueprintFuture, rotMir, true);
    }

    /**
     * The minecolonies specific creative structure placer.
     *
     * @param world          the world.
     * @param pos            the pos it is placed at.
     * @param blueprint      the blueprint.
     * @param rotMir       the placement settings.
     */
    public LoadOnlyStructureHandler(final Level world, final BlockPos pos, final Blueprint blueprint, final RotationMirror rotMir)
    {
        super(world, pos, blueprint, rotMir, true);
    }

    @Override
    public void triggerSuccess(final BlockPos pos, final List<ItemStack> list, final boolean placement)
    {
        // DO nothing
    }

    @Override
    public boolean isCreative()
    {
        return false;
    }

    @Override
    public boolean isStackFree(@Nullable final ItemStack itemStack)
    {
        return itemStack == null
                 || itemStack.isEmpty()
                 || itemStack.is(ItemTags.LEAVES)
                 || itemStack.getItem() == new ItemStack(ModBlocks.blockDecorationPlaceholder, 1).getItem();
    }
}
