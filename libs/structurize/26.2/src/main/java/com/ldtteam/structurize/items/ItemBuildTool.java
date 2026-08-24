package com.ldtteam.structurize.items;

import com.ldtteam.structurize.api.ItemStackUtils;
import com.ldtteam.structurize.client.gui.GuiStubs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import static com.ldtteam.structurize.api.constants.Constants.GROUNDSTYLE_RELATIVE;
/**
import net.minecraft.world.item.Item.Properties;

 * Class handling the buildTool item.
 */
public class ItemBuildTool extends AbstractItemStructurize
{
    /**
     * Instantiates the buildTool on load.
     */
    public ItemBuildTool(final Properties properties)
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
     * TODO(port-26.2): DISABLED — {@code IItemExtension#getCraftingRemainingItem} /
     * {@code hasCraftingRemainingItem} are NeoForge extensions. Vanilla 26.2 declares the crafting remainder
     * statically through {@code Item.Properties#craftRemainder(Item)}
     * (/opt/mc-src/net/minecraft/world/item/Item.java:412), which cannot express "give the very same stack
     * back". Effect: using the build tool in a crafting recipe consumes it. The mod ships no recipe that
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
