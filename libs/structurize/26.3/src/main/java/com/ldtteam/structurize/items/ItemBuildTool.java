package com.ldtteam.structurize.items;

import com.ldtteam.structurize.api.ItemStackUtils;
import com.ldtteam.structurize.client.gui.GuiStubs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import static com.ldtteam.structurize.api.constants.Constants.GROUNDSTYLE_RELATIVE;

/**
 * Class handling the buildTool item.
 */
public class ItemBuildTool extends AbstractItemStructurize implements FabricItem
{
    /**
     * Instantiates the buildTool on load.
     *
     * @param properties {@link Item.Properties}, qualified because {@link FabricItem} declares a nested
     *                   {@code Properties} of its own.
     */
    public ItemBuildTool(final Item.Properties properties)
    {
        super("sceptergold", properties);
    }

    @Override
    @SuppressWarnings("resource")
    public InteractionResult useOn(final UseOnContext context)
    {
        if (context.getLevel().isClientSide())
        {
            openBuildToolWindow(context.getClickedPos().relative(context.getClickedFace()), GROUNDSTYLE_RELATIVE, context.getLevel().registryAccess());
        }
        return InteractionResult.SUCCESS;
    }

        @Override
    public InteractionResult use(final Level worldIn, final Player playerIn, final InteractionHand handIn)
    {
        final ItemStack stack = playerIn.getItemInHand(handIn);

        if (worldIn.isClientSide())
        {
            openBuildToolWindow(null, GROUNDSTYLE_RELATIVE, worldIn.registryAccess());
        }

        return InteractionResult.SUCCESS;
    }

    private static void openBuildToolWindow(final BlockPos pos, final int groundstyle, final HolderLookup.Provider provider)
    {
        if (Minecraft.getInstance().gui.screen() != null)
        {
            return;
        }

        GuiStubs.openBuildToolWindow(pos, groundstyle, provider);
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
