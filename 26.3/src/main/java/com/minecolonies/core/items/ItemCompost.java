package com.minecolonies.core.items;

import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.world.item.BoneMealItem.growCrop;

/**
 * Class used to handle the compost item.
 */
public class ItemCompost extends AbstractItemMinecolonies
{

    /***
     * Constructor for the ItemCompost
     * @param properties the properties.
     */
    public ItemCompost(final Item.Properties properties)
    {
        super("compost", properties.stacksTo(Constants.STACKSIZE));
    }

    /**
     * Wrapper around {@link net.minecraft.world.item.BoneMealItem#growCrop(ItemStack, Level, BlockPos, Player)}
     * to handle {@link MinecoloniesCropBlock} as well.
     *
     * @param stack  the input item stack.
     * @param level  the input level.
     * @param pos    the input position.
     * @param player the input player.
     * @return true if successfully bone-mealed.
     */
    private static boolean applyCompost(@NotNull ItemStack stack, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player)
    {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MinecoloniesCropBlock cropBlock)
        {
            if (!cropBlock.isMaxAge(state))
            {
                if (level instanceof ServerLevel serverLevel)
                {
                    cropBlock.attemptGrow(state, serverLevel, pos);
                    stack.shrink(1);
                }

                return true;
            }

            return false;
        }

        return growCrop(stack, level, pos);
    }

    @Override
    @NotNull
    public InteractionResult useOn(final UseOnContext ctx)
    {
        if (applyCompost(ctx.getItemInHand(), ctx.getLevel(), ctx.getClickedPos(), ctx.getPlayer()))
        {
            if (!ctx.getLevel().isClientSide())
            {
                ctx.getLevel().levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, ctx.getClickedPos(), 0);
            }

            // 26.2 dropped InteractionResult.sidedSuccess(boolean): SUCCESS already swings the arm on the
            // client and SUCCESS_SERVER is the server-authoritative variant.
            return ctx.getLevel().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }
}


