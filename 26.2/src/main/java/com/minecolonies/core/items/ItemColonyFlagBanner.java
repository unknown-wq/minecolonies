package com.minecolonies.core.items;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.core.tileentities.TileEntityColonyFlag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import java.util.List;

/**
 * This item represents the colony flag banner, both wall and floor blocks.
 * Allows duplication of other banner pattern lists to its own default
 */
public class ItemColonyFlagBanner extends BannerItem
{
    public ItemColonyFlagBanner(String name, Properties properties)
    {
        this(ModBlocks.blockColonyBanner, ModBlocks.blockColonyWallBanner, properties.stacksTo(16));
    }

    public ItemColonyFlagBanner(Block standingBanner, Block wallBanner, Properties builder)
    {
        super(standingBanner, wallBanner, builder);
    }

    @NotNull
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        // Duplicate the patterns of the banner that was clicked on
        BlockEntity te = context.getLevel().getBlockEntity(context.getClickedPos());
        ItemStack stack = context.getPlayer().getMainHandItem();

        if (te instanceof BannerBlockEntity || te instanceof TileEntityColonyFlag)
        {
            final BannerPatternLayers bannerPatternLayers;
            if (te instanceof BannerBlockEntity)
            {
                bannerPatternLayers = ((BannerBlockEntity) te).getPatterns();
            }
            else
            {
                bannerPatternLayers = ((TileEntityColonyFlag) te).getPatterns();
            }

            stack.set(DataComponents.BANNER_PATTERNS, bannerPatternLayers);
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }

    @Override
    public void appendHoverText(@NotNull final ItemStack stack,
      @NotNull final TooltipContext ctx,
      @NotNull final TooltipDisplay display,
      @NotNull final Consumer<Component> tooltip,
      @NotNull final TooltipFlag flagIn)
    {
        // 26.2: appendHoverText writes into a Consumer<Component>, not a mutable List, so the second line
        // produced by the super call can no longer be removed after the fact. It is dropped instead by
        // filtering the super call's own output: the first line is kept, the second (the untranslated banner
        // base) is skipped, the rest pass through.
        final int[] seen = {0};
        super.appendHoverText(stack, ctx, display, line -> {
            if (seen[0]++ != 1)
            {
                tooltip.accept(line);
            }
        }, flagIn);
    }
}
