package com.minecolonies.core.items;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.util.ChunkDataHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.ColonyManagerConstants.NO_COLONY_ID;
import static com.minecolonies.api.util.constant.translation.ToolTranslationConstants.*;

/**
 * Claim Scepter Item class. Used to push the borders of a colony outwards.
 * <p>
 * This is the item shaped counterpart of {@code /minecolonies colony claim <id> <range> true}: it drives the exact
 * same claiming API ({@link ChunkDataHelper#tryClaim}), but resolves its colony from the world instead of a command
 * argument and asks the colony permissions instead of the server op level.
 * <p>
 * Its one rule is that a chunk must have no owner. Distance is not a rule: the scepter will happily claim a chunk on
 * the far side of the world from the town hall, so a colony's territory may come in pieces with unclaimed ground
 * between them.
 */
public class ItemScepterClaim extends AbstractItemMinecolonies
{
    /**
     * The chunk radius claimed by a single use, so one click takes the clicked chunk and the eight around it.
     */
    public static final int CLAIM_RANGE = 1;

    /**
     * ClaimScepter constructor. Sets max stack to 1, like the other scepters.
     *
     * @param properties the properties.
     */
    public ItemScepterClaim(final Properties properties)
    {
        super("scepterclaim", properties.stacksTo(1));
    }

    /**
     * Used when clicking on a block in the world. Claims the chunks around the clicked position for the closest
     * colony. Server authoritative, the client only relays the interaction.
     *
     * @param ctx the use context.
     * @return the result.
     */
    @Override
    @NotNull
    public InteractionResult useOn(final UseOnContext ctx)
    {
        final Player player = ctx.getPlayer();
        if (player == null)
        {
            return InteractionResult.FAIL;
        }

        // The client does nothing but send the interaction, all claiming happens below on the server.
        if (ctx.getLevel().isClientSide())
        {
            return InteractionResult.SUCCESS;
        }

        final BlockPos clickedPos = ctx.getClickedPos();
        final Colony colony = getColony(ctx.getLevel(), clickedPos);
        if (colony == null)
        {
            MessageUtils.format(TOOL_CLAIM_SCEPTER_NO_COLONY).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        // Same gate the command gets from being op only: only someone who may manage the colony may move its borders.
        if (!ChunkDataHelper.mayEditClaim(colony, player))
        {
            MessageUtils.format(TOOL_CLAIM_SCEPTER_NO_PERMISSION, colony.getName()).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        // Deliberately no distance test against maxColonySize. That limit shapes the automatic claiming
        // ChunkDataHelper does around buildings, and it keeps a colony's own growth compact; the scepter is
        // the manual override, so its only rule is that the chunk has no owner. A claim is therefore free to
        // sit anywhere, including with a gap of unclaimed chunks between it and the rest of the colony.
        final ServerLevel level = (ServerLevel) ctx.getLevel();
        final int chunkX = clickedPos.getX() >> 4;
        final int chunkZ = clickedPos.getZ() >> 4;

        int claimed = 0;
        int owned = 0;

        for (int x = chunkX - CLAIM_RANGE; x <= chunkX + CLAIM_RANGE; x++)
        {
            for (int z = chunkZ - CLAIM_RANGE; z <= chunkZ + CLAIM_RANGE; z++)
            {
                final ChunkPos chunkPos = new ChunkPos(x, z);
                if (isOwnedByAnotherColony(level, chunkPos, colony.getID()))
                {
                    owned++;
                    continue;
                }

                // The claim itself is not reimplemented here, tryClaim is the call staticClaimInRange makes
                // per chunk. forceOwnerChange stays false: nothing here may take a chunk off another colony.
                ChunkDataHelper.tryClaim(level, chunkPos.getWorldPosition(), true, colony, false);
                claimed++;
            }
        }

        if (claimed == 0)
        {
            MessageUtils.format(TOOL_CLAIM_SCEPTER_ALL_OWNED).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        MessageUtils.format(TOOL_CLAIM_SCEPTER_SUCCESS, claimed, colony.getName()).sendTo(player);
        if (owned > 0)
        {
            MessageUtils.format(TOOL_CLAIM_SCEPTER_SOME_OWNED, owned).sendTo(player);
        }
        SoundUtils.playSuccessSound(player, player.blockPosition());
        return InteractionResult.SUCCESS;
    }

    /**
     * Check whether a chunk already belongs to some colony other than this one.
     * <p>
     * Claiming through {@link ChunkDataHelper#tryClaim} with {@code forceOwnerChange} false does not steal an owned
     * chunk, but it does still add the colony to that chunk's list of nearby colonies. Skipping those chunks outright
     * keeps a click on the edge of someone else's land from leaving a trace in it.
     *
     * @param level    the level.
     * @param chunkPos the chunk to test.
     * @param colonyId the colony doing the claiming.
     * @return true if another colony owns this chunk.
     */
    private static boolean isOwnedByAnotherColony(final ServerLevel level, final ChunkPos chunkPos, final int colonyId)
    {
        final IChunkClaimData claimData = IColonyManager.getInstance().getClaimData(level.dimension(), chunkPos);
        if (claimData == null)
        {
            return false;
        }

        final int owningColony = claimData.getOwningColony();
        return owningColony != NO_COLONY_ID && owningColony != colonyId;
    }

    /**
     * Mid air use, reports which colony the scepter would act on and how far that colony may still reach.
     *
     * @param worldIn  the world.
     * @param playerIn the player.
     * @param hand     the hand.
     * @return the result.
     */
    @Override
    @NotNull
    public InteractionResult use(final Level worldIn, final Player playerIn, final InteractionHand hand)
    {
        if (worldIn.isClientSide())
        {
            return InteractionResult.SUCCESS;
        }

        final Colony colony = getColony(worldIn, playerIn.blockPosition());
        if (colony == null)
        {
            MessageUtils.format(TOOL_CLAIM_SCEPTER_NO_COLONY).sendTo(playerIn);
            return InteractionResult.FAIL;
        }

        MessageUtils.format(TOOL_CLAIM_SCEPTER_INFO, colony.getName()).sendTo(playerIn);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(@NotNull final ItemStack stack,
      @NotNull final TooltipContext ctx,
      @NotNull final TooltipDisplay display,
      @NotNull final Consumer<Component> tooltip,
      @NotNull final TooltipFlag flags)
    {
        final MutableComponent guiHint = Component.translatableEscape(TOOL_CLAIM_SCEPTER_DESCRIPTION);
        guiHint.setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_GREEN));
        tooltip.accept(guiHint);

        super.appendHoverText(stack, ctx, display, tooltip, flags);
    }

    /**
     * Resolve the colony this scepter acts on. Server side only, so this is always the real colony and never a view.
     *
     * @param level the level.
     * @param pos   the position that was interacted with.
     * @return the colony or null if there is none close enough.
     */
    private static Colony getColony(final Level level, final BlockPos pos)
    {
        // Deliberately not getClosestColony: that answers with the owner of the chunk before it measures anything, so
        // a click made while standing on a hostile territory would claim for the enemy rather than for the colony
        // whose borders the player came to push out.
        final IColony colony = IColonyManager.getInstance().getClosestNonHostileColony(level, pos);
        return colony instanceof Colony ? (Colony) colony : null;
    }

}
