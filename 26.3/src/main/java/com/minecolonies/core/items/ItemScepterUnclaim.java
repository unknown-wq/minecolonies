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
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.ColonyManagerConstants.NO_COLONY_ID;
import static com.minecolonies.api.util.constant.translation.ToolTranslationConstants.*;

/**
 * Unclaim Scepter item class. The opposite number of {@link ItemScepterClaim}: it hands chunks back rather than takes
 * them.
 * <p>
 * A claim only ever grew before this. Chunks came in through the automatic claiming around buildings, through
 * {@code /minecolonies colony claim} and through the claim scepter, and the only way to give one back was to delete the
 * colony. Testing a border needs to be able to go the other way too.
 * <p>
 * The colony it acts on is the one that owns the clicked chunk rather than the closest one, since that is whose claim
 * is being removed, and the permission asked for is the same {@code MANAGE_HUTS} the claim scepter asks for --
 * through {@link ChunkDataHelper#mayEditClaim}, which also lets an operator release the ground of a hostile
 * territory, where nobody holds that permission and nobody can be given it.
 */
public class ItemScepterUnclaim extends AbstractItemMinecolonies
{
    /**
     * The chunk radius a sneaking use covers, matching {@link ItemScepterClaim#CLAIM_RANGE} so one sneak-click undoes
     * one ordinary claim-scepter click.
     */
    public static final int WIDE_RANGE = 1;

    /**
     * UnclaimScepter constructor. Sets max stack to 1, like the other scepters.
     *
     * @param properties the properties.
     */
    public ItemScepterUnclaim(final Properties properties)
    {
        super("scepterunclaim", properties.stacksTo(1));
    }

    /**
     * Used when clicking on a block in the world. Gives up the clicked chunk, or the nine around it while sneaking.
     * Server authoritative, the client only relays the interaction.
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

        final ServerLevel level = (ServerLevel) ctx.getLevel();
        final BlockPos clickedPos = ctx.getClickedPos();
        final Colony colony = getOwningColony(level, clickedPos);
        if (colony == null)
        {
            MessageUtils.format(TOOL_UNCLAIM_SCEPTER_NOT_CLAIMED).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        if (!ChunkDataHelper.mayEditClaim(colony, player))
        {
            MessageUtils.format(TOOL_UNCLAIM_SCEPTER_NO_PERMISSION, colony.getName()).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        final int range = ctx.isSecondaryUseActive() ? WIDE_RANGE : 0;
        final int chunkX = clickedPos.getX() >> 4;
        final int chunkZ = clickedPos.getZ() >> 4;

        int released = 0;
        int kept = 0;

        for (int x = chunkX - range; x <= chunkX + range; x++)
        {
            for (int z = chunkZ - range; z <= chunkZ + range; z++)
            {
                final ChunkPos chunkPos = new ChunkPos(x, z);
                if (getOwner(level, chunkPos) != colony.getID())
                {
                    // Either free already or someone else's; a wide click is not a way to take land off a neighbour.
                    continue;
                }

                if (isColonyCentre(colony, chunkPos))
                {
                    kept++;
                    continue;
                }

                unclaim(level, colony, chunkPos);
                released++;
            }
        }

        if (released == 0)
        {
            MessageUtils.format(kept > 0 ? TOOL_UNCLAIM_SCEPTER_CENTRE : TOOL_UNCLAIM_SCEPTER_NOTHING).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        MessageUtils.format(TOOL_UNCLAIM_SCEPTER_SUCCESS, released, colony.getName()).sendTo(player);
        if (kept > 0)
        {
            MessageUtils.format(TOOL_UNCLAIM_SCEPTER_CENTRE).sendTo(player);
        }
        SoundUtils.playSuccessSound(player, player.blockPosition());
        return InteractionResult.SUCCESS;
    }

    /**
     * Give one chunk back.
     * <p>
     * {@link ChunkDataHelper#tryClaim} with {@code add} false is the same call the colony makes when it drops a claim
     * of its own, and it takes the colony out of the chunk's static claims and its building claims both. What it does
     * not do is clear the owner when some other colony is still listed on the chunk, which is right: that chunk is
     * theirs now, not nobody's.
     *
     * @param level    the level.
     * @param colony   the colony giving the chunk up.
     * @param chunkPos the chunk.
     */
    private static void unclaim(final ServerLevel level, final Colony colony, final ChunkPos chunkPos)
    {
        ChunkDataHelper.tryClaim(level, chunkPos.getWorldPosition(), false, colony, false);

        final IChunkClaimData claimData = IColonyManager.getInstance().getClaimData(level.dimension(), chunkPos);
        if (claimData != null && claimData.getOwningColony() == NO_COLONY_ID && claimData.hasPartialClaim())
        {
            // A border drawn inside a chunk means nothing once nobody owns the chunk, and leaving it behind would
            // silently shape the claim of whoever takes the chunk next.
            final LevelChunk chunk = level.getChunk(chunkPos.x(), chunkPos.z());
            claimData.clearPartialClaim(chunk);
        }
    }

    /**
     * Whether a chunk is the one the colony is centred on.
     * <p>
     * Unclaiming that one is refused. Everything that asks which colony a position is in reads the chunk claim, so a
     * colony whose own centre is unclaimed stops recognising its own town hall — protection, the citizens' idea of
     * being home, and the colony overview all break at once. Any other chunk is fair game.
     *
     * @param colony   the colony.
     * @param chunkPos the chunk.
     * @return true if this is the centre chunk.
     */
    private static boolean isColonyCentre(final Colony colony, final ChunkPos chunkPos)
    {
        return ChunkPos.containing(colony.getCenter()).equals(chunkPos);
    }

    /**
     * Mid air use, reports what the scepter is pointed at.
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

        final Colony colony = getOwningColony(worldIn, playerIn.blockPosition());
        if (colony == null)
        {
            MessageUtils.format(TOOL_UNCLAIM_SCEPTER_NOT_CLAIMED).sendTo(playerIn);
            return InteractionResult.FAIL;
        }

        MessageUtils.format(TOOL_UNCLAIM_SCEPTER_INFO, colony.getName()).sendTo(playerIn);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(@NotNull final ItemStack stack,
      @NotNull final TooltipContext ctx,
      @NotNull final TooltipDisplay display,
      @NotNull final Consumer<Component> tooltip,
      @NotNull final TooltipFlag flags)
    {
        final MutableComponent guiHint = Component.translatableEscape(TOOL_UNCLAIM_SCEPTER_DESCRIPTION);
        guiHint.setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_RED));
        tooltip.accept(guiHint);

        super.appendHoverText(stack, ctx, display, tooltip, flags);
    }

    /**
     * The id of the colony owning a chunk.
     *
     * @param level    the level.
     * @param chunkPos the chunk.
     * @return the id, or {@code NO_COLONY_ID}.
     */
    private static int getOwner(final Level level, final ChunkPos chunkPos)
    {
        final IChunkClaimData claimData = IColonyManager.getInstance().getClaimData(level.dimension(), chunkPos);
        return claimData == null ? NO_COLONY_ID : claimData.getOwningColony();
    }

    /**
     * Resolve the colony that owns the chunk a position is in, which is the one whose claim this scepter removes.
     * Deliberately not the closest colony: the closest one may well not be the one holding the chunk.
     *
     * @param level the level.
     * @param pos   the position that was interacted with.
     * @return the colony, or null if the chunk is unclaimed.
     */
    @Nullable
    private static Colony getOwningColony(final Level level, final BlockPos pos)
    {
        final int id = getOwner(level, ChunkPos.containing(pos));
        if (id == NO_COLONY_ID)
        {
            return null;
        }

        final IColony colony = IColonyManager.getInstance().getColonyByDimension(id, level.dimension());
        return colony instanceof Colony ? (Colony) colony : null;
    }
}
