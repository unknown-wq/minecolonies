package com.ldtteam.structurize.items;

import com.ldtteam.structurize.api.ItemStackUtils;
import com.ldtteam.structurize.client.gui.GuiStubs;
import net.minecraft.world.entity.player.Player;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ItemShapeTool extends AbstractItemStructurize implements FabricItem
{
    /**
     * Sets the name, creative tab, and registers the item.
     *
     * @param properties {@link Item.Properties}, qualified because {@link FabricItem} declares a nested
     *                   {@code Properties} of its own.
     */
    public ItemShapeTool(final Item.Properties properties)
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
     * The tool survives being used as a crafting ingredient, which is what NeoForge's
     * {@code IItemExtension#getCraftingRemainingItem(ItemStack)} did. Vanilla's static
     * {@code Item.Properties#craftRemainder} cannot express "the same stack back", but
     * {@link FabricItem#getCraftingRemainder(ItemStack)} is the per-stack hook for exactly this, and
     * fabric-item-api-v1 routes the vanilla crafting code through it.
     *
     * <p>{@code implements FabricItem} is declared explicitly so this really is an override that javac
     * checks: every {@code Item} implements the interface at runtime, but the compile-time declaration
     * only arrives through Loom's interface injection, which this build does not use.</p>
     *
     * @param stack the stack sitting in the crafting grid; the tool always stacks to one.
     * @return the same tool, components and all, or null for an empty stack.
     */
    @Override
    @Nullable
    public ItemStackTemplate getCraftingRemainder(final ItemStack stack)
    {
        return ItemStackUtils.isEmpty(stack) ? null : ItemStackTemplate.fromNonEmptyStack(stack);
    }
}
