package com.ldtteam.structurize.items;

import com.ldtteam.structurize.api.ItemStackUtils;
import com.ldtteam.structurize.client.gui.GuiStubs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;

public class ItemShapeTool extends AbstractItemStructurize
{
    /**
     * Sets the name, creative tab, and registers the item.
     */
    public ItemShapeTool(final Properties properties)
    {
        super("shapetool", properties);
    }

    @Override
    @SuppressWarnings("resource")
    public InteractionResult useOn(final UseOnContext context)
    {
        if (context.getLevel().isClientSide())
        {
            GuiStubs.openShapeToolWindow(context.getClickedPos().relative(context.getClickedFace()), context.getLevel().registryAccess());
        }

        return InteractionResult.SUCCESS;
    }

        @Override
    public InteractionResult use(final Level worldIn, final Player playerIn, final InteractionHand hand)
    {
        final ItemStack stack = playerIn.getItemInHand(hand);

        if (worldIn.isClientSide())
        {
            GuiStubs.openShapeToolWindow(null, worldIn.registryAccess());
        }

        return InteractionResult.SUCCESS;
    }


    /**
     * TODO(port-26.2): DISABLED — {@code IItemExtension#getCraftingRemainingItem} /
     * {@code hasCraftingRemainingItem} are NeoForge extensions. Vanilla 26.2 declares the crafting remainder
     * statically through {@code Item.Properties#craftRemainder(Item)}
     * (/opt/mc-src/net/minecraft/world/item/Item.java:412), which cannot express "give the very same stack
     * back". Effect: using the shape tool in a crafting recipe consumes it. The mod ships no recipe that
     * uses it, so this is only visible to datapacks that add one.
     * Original:
     * <pre>
     * &#64;Override
     * public ItemStack getCraftingRemainingItem(final ItemStack itemStack)
     * {
     *     if (ItemStackUtils.isEmpty(itemStack)) { return ItemStack.EMPTY; }
     *     return itemStack.copy();
     * }
     *
     * &#64;Override
     * public boolean hasCraftingRemainingItem(final ItemStack itemStack)
     * {
     *     return !ItemStackUtils.isEmpty(itemStack);
     * }
     * </pre>
     */
}
