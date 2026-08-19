package com.minecolonies.core.items;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.items.component.ColonyId;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.util.ChunkDataHelper;
import net.minecraft.ChatFormatting;
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
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.ColonyManagerConstants.NO_COLONY_ID;
import static com.minecolonies.api.util.constant.translation.ToolTranslationConstants.*;

/**
 * Territory Scepter item class. Takes ground for a colony the holder does not belong to — in practice, for a hostile
 * territory, which is how a player paints the enemy's border next to his own.
 * <p>
 * The three land scepters that came before all resolve their colony from the world: {@link ItemScepterClaim} and
 * {@link ItemScepterBorder} take the nearest one, {@link ItemScepterUnclaim} the one that owns the chunk. None of them
 * can express "this ground belongs to <i>that</i> colony over there", and none of them ever could, because the answer
 * is not derivable from where the player is standing — a territory that owns nothing yet is at no distance from
 * anywhere. So this one carries its target rather than deducing it: a {@link ColonyId} written onto the stack by
 * {@code /mc colony territory create} or {@code /mc colony territory bind}, and shown on the tooltip and on every
 * mid-air click so the holder can always see whose ground he is about to draw.
 * <p>
 * It claims whole chunks and nothing finer. Shaping a territory inside a chunk is {@link ItemScepterBorder}'s job and
 * that item already does it for whatever colony owns the chunk, hostile or not — which is why the two together are
 * enough and this one does not duplicate the column painting.
 * <p>
 * Operator only. Not by permission — a hostile territory belongs to nobody on purpose, so there is no permission that
 * could ever be granted on it — but by the same op check the colony commands use, which is the authority that created
 * the territory in the first place.
 */
public class ItemScepterTerritory extends AbstractItemMinecolonies
{
    /**
     * TerritoryScepter constructor. Sets max stack to 1, like the other scepters.
     *
     * @param properties the properties.
     */
    public ItemScepterTerritory(final Properties properties)
    {
        super("scepterterritory", properties.stacksTo(1));
    }

    /**
     * Used when clicking on a block in the world. Takes the clicked chunk for the bound colony, or gives it back while
     * sneaking. Server authoritative, the client only relays the interaction.
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

        if (ctx.getLevel().isClientSide())
        {
            return InteractionResult.SUCCESS;
        }

        if (!IMCCommand.isPlayerOped(player))
        {
            MessageUtils.format(TOOL_TERRITORY_SCEPTER_NO_OP).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        final Colony colony = boundColony(ctx.getItemInHand());
        if (colony == null)
        {
            MessageUtils.format(TOOL_TERRITORY_SCEPTER_NOT_BOUND).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        final ServerLevel level = (ServerLevel) ctx.getLevel();
        final ChunkPos chunkPos = ChunkPos.containing(ctx.getClickedPos());
        final int owner = ownerOf(level, chunkPos);

        if (ctx.isSecondaryUseActive())
        {
            return release(level, player, colony, chunkPos, owner);
        }

        return claim(level, player, colony, chunkPos, owner);
    }

    /**
     * Take one chunk for the bound colony.
     *
     * @param level    the level.
     * @param player   the player.
     * @param colony   the colony being given the ground.
     * @param chunkPos the chunk.
     * @param owner    who owns the chunk now.
     * @return the result.
     */
    private static InteractionResult claim(final ServerLevel level,
      final Player player,
      final Colony colony,
      final ChunkPos chunkPos,
      final int owner)
    {
        if (owner == colony.getID())
        {
            // Already theirs. Silent, because the button is held down to sweep a strip of ground.
            return InteractionResult.SUCCESS;
        }

        if (owner != NO_COLONY_ID)
        {
            MessageUtils.format(TOOL_TERRITORY_SCEPTER_OWNED).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        // forceOwnerChange stays false, the same as every other scepter: nothing here may take a chunk off another
        // colony, and the caller has already established that nobody owns this one.
        ChunkDataHelper.tryClaim(level, chunkPos.getWorldPosition(), true, colony, false);
        colony.markDirty();

        MessageUtils.format(TOOL_TERRITORY_SCEPTER_CLAIMED, colony.getName(), chunkPos.x(), chunkPos.z()).sendTo(player);
        SoundUtils.playSuccessSound(player, player.blockPosition());
        return InteractionResult.SUCCESS;
    }

    /**
     * Give one chunk back from the bound colony.
     * <p>
     * The centre chunk is refused, exactly as {@link ItemScepterUnclaim} refuses it: everything that asks which colony
     * a position is in reads the chunk claim, and a colony whose centre is unclaimed stops recognising itself.
     * Erasing a territory outright is {@code /mc colony territory delete}, which releases the centre too because it
     * removes the colony along with it.
     *
     * @param level    the level.
     * @param player   the player.
     * @param colony   the colony giving the ground up.
     * @param chunkPos the chunk.
     * @param owner    who owns the chunk now.
     * @return the result.
     */
    private static InteractionResult release(final ServerLevel level,
      final Player player,
      final Colony colony,
      final ChunkPos chunkPos,
      final int owner)
    {
        if (owner != colony.getID())
        {
            MessageUtils.format(TOOL_TERRITORY_SCEPTER_NOTHING, colony.getName()).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        if (ChunkPos.containing(colony.getCenter()).equals(chunkPos))
        {
            MessageUtils.format(TOOL_TERRITORY_SCEPTER_CENTRE).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        ChunkDataHelper.tryClaim(level, chunkPos.getWorldPosition(), false, colony, false);

        final IChunkClaimData claimData = IColonyManager.getInstance().getClaimData(level.dimension(), chunkPos);
        if (claimData != null && claimData.getOwningColony() == NO_COLONY_ID && claimData.hasPartialClaim())
        {
            // A border drawn inside a chunk means nothing once nobody owns the chunk, and leaving it behind would
            // silently shape the claim of whoever takes the chunk next.
            claimData.clearPartialClaim(level.getChunk(chunkPos.x(), chunkPos.z()));
        }
        colony.markDirty();

        MessageUtils.format(TOOL_TERRITORY_SCEPTER_RELEASED, colony.getName(), chunkPos.x(), chunkPos.z()).sendTo(player);
        SoundUtils.playSuccessSound(player, player.blockPosition());
        return InteractionResult.SUCCESS;
    }

    /**
     * Mid air use, reports which colony the scepter is pointed at and how much ground it holds.
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

        final Colony colony = boundColony(playerIn.getItemInHand(hand));
        if (colony == null)
        {
            MessageUtils.format(TOOL_TERRITORY_SCEPTER_NOT_BOUND).sendTo(playerIn);
            return InteractionResult.FAIL;
        }

        MessageUtils.format(TOOL_TERRITORY_SCEPTER_INFO, colony.getName(), colony.getID(), countChunks(colony)).sendTo(playerIn);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(@NotNull final ItemStack stack,
      @NotNull final TooltipContext ctx,
      @NotNull final TooltipDisplay display,
      @NotNull final Consumer<Component> tooltip,
      @NotNull final TooltipFlag flags)
    {
        final MutableComponent guiHint = Component.translatableEscape(TOOL_TERRITORY_SCEPTER_DESCRIPTION);
        guiHint.setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        tooltip.accept(guiHint);

        super.appendHoverText(stack, ctx, display, tooltip, flags);
    }

    /**
     * How many chunks the territory holds, for the report.
     *
     * @param colony the colony.
     * @return the count.
     */
    private static int countChunks(final Colony colony)
    {
        int owned = 0;
        for (final var entry : colony.getClaimData().long2ObjectEntrySet())
        {
            if (entry.getValue().getOwningColony() == colony.getID())
            {
                owned++;
            }
        }
        return owned;
    }

    /**
     * The colony this scepter was bound to, if it still exists.
     *
     * @param stack the scepter.
     * @return the colony, or null if the scepter is unbound or its colony is gone.
     */
    @Nullable
    private static Colony boundColony(final ItemStack stack)
    {
        final IColony colony = ColonyId.readColonyFromItemStack(stack);
        return colony instanceof Colony ? (Colony) colony : null;
    }

    /**
     * The id of the colony owning a chunk.
     *
     * @param level    the level.
     * @param chunkPos the chunk.
     * @return the id, or {@code NO_COLONY_ID}.
     */
    private static int ownerOf(final Level level, final ChunkPos chunkPos)
    {
        final IChunkClaimData claimData = IColonyManager.getInstance().getClaimData(level.dimension(), chunkPos);
        return claimData == null ? NO_COLONY_ID : claimData.getOwningColony();
    }

    /**
     * Point a scepter at a colony. Called by {@code /mc colony territory}.
     *
     * @param stack  the scepter.
     * @param colony the colony to bind it to.
     */
    public static void bind(final ItemStack stack, final IColony colony)
    {
        new ColonyId(colony.getID(), colony.getDimension()).writeToItemStack(stack);
    }

    /**
     * Whether a stack is a territory scepter, for the command that binds one.
     *
     * @param stack the stack.
     * @return true if it is.
     */
    public static boolean isTerritoryScepter(final ItemStack stack)
    {
        return stack.getItem() instanceof ItemScepterTerritory;
    }
}
