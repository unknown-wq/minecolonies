package com.minecolonies.core.items;

import com.ldtteam.structurize.component.ModDataComponents;
import com.ldtteam.structurize.items.AbstractItemWithPosSelector.PosSelection;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.ColonyManagerConstants.NO_COLONY_ID;
import static com.minecolonies.api.util.constant.translation.ToolTranslationConstants.*;

/**
 * Border Scepter item class. Draws a colony's border inside a chunk, one column at a time.
 * <p>
 * A claim was a whole chunk or nothing, so a border could only ever run along the chunk grid, whatever the shape of
 * what was being enclosed. This paints instead: right-click puts a column inside the border, sneak-right-click takes
 * one out, and holding the button paints a line as fast as the client repeats the interaction.
 * <p>
 * It paints outside the border as well as inside it. A column drawn on a chunk nobody owns takes the chunk for the
 * nearest colony and starts its border empty, so what the colony gains is the column rather than the chunk it fell
 * in; rubbing the last column off a chunk gives it back. That is what makes a hand-drawn border able to grow rather
 * than only to shrink from whatever {@link ItemScepterClaim} or the colony's own growth happened to enclose.
 * <p>
 * The mask that holds the result lives on the chunk claim itself, so everything that asks which colony a position
 * belongs to sees the drawn border rather than the chunk: protection, whether a citizen thinks it is home, the raid
 * spawner and the borders drawn by the build tool all follow it.
 * <p>
 * <b>Rectangles.</b> Painting a field edge one column at a time is fine for a curve and tedious for a straight run, so
 * left-click marks a corner and the next right-click fills every column between that corner and where it lands —
 * painting or, while sneaking, erasing, the same as an ordinary click. The corner is cleared by the fill, so a held
 * button never repeats a rectangle and the click after one is a single column again. Left-click was the free gesture:
 * the item is not a tool and never broke anything, which is what {@link #canDestroyBlock} keeps true.
 * <p>
 * The cap is {@value #MAX_RECTANGLE_COLUMNS} columns — a 64x64 block square, which is a whole field and then some, and
 * a great deal more than anybody marks by accident. It exists to bound the mis-click, not the feature: this is the
 * in-chunk painter, so a rectangle is tens of columns and at worst a few chunks. A rectangle that spills over a chunk
 * boundary is allowed and pulls the chunks it lands on in exactly as a single click does; at this size that is at most
 * twenty five chunks, against the nine an ordinary claim scepter click already loads.
 */
public class ItemScepterBorder extends AbstractItemMinecolonies
{
    /**
     * How many columns a chunk has, for the "so many of so many" messages.
     */
    private static final int COLUMNS_PER_CHUNK = 16 * 16;

    /**
     * The most columns one rectangle may cover, which is a 64x64 block square.
     */
    private static final int MAX_RECTANGLE_COLUMNS = 64 * 64;

    /**
     * BorderScepter constructor. Sets max stack to 1, like the other scepters.
     *
     * @param properties the properties.
     */
    public ItemScepterBorder(final Properties properties)
    {
        super("scepterborder", properties.stacksTo(1).component(ModDataComponents.POS_SELECTION, PosSelection.EMPTY));
    }

    /**
     * Left-click a block: mark the first corner of a rectangle.
     * <p>
     * On the stack rather than in a per-player server map, following {@link ItemScepterLumberjack}: the mark then
     * survives a relog, two players holding two scepters cannot collide, and there is no lifetime to manage.
     * <p>
     * Idempotent on purpose — a left-click that happens to be delivered twice sets the same corner twice, which is why
     * the corner and not the fill lives on this gesture.
     */
    // 26.2: Item#canAttackBlock(BlockState, Level, BlockPos, Player) became
    // Item#canDestroyBlock(ItemStack, BlockState, Level, BlockPos, LivingEntity), as in Structurize's port.
    @Override
    public boolean canDestroyBlock(@NotNull final ItemStack heldStack,
      @NotNull final BlockState state,
      @NotNull final Level world,
      @NotNull final BlockPos pos,
      @NotNull final LivingEntity user)
    {
        if (!world.isClientSide() && user instanceof final Player player)
        {
            PosSelection.updateItemStack(heldStack, selection -> selection.setStartPos(pos));
            MessageUtils.format(TOOL_BORDER_SCEPTER_CORNER, pos.getX(), pos.getZ()).sendTo(player);
        }

        // Never breaks the block. The scepter is not a tool and a border drawn by knocking the ground out from under
        // it would be a surprising way to lose a field.
        return false;
    }

    /**
     * Break the clicked block instantly, so that {@link #canDestroyBlock} is reached on the first click rather than
     * after holding the button. It still breaks nothing, because that method refuses.
     *
     * @param stack the scepter.
     * @param state the block being clicked.
     * @return an unreachable mining speed.
     */
    @Override
    public float getDestroySpeed(@NotNull final ItemStack stack, @NotNull final BlockState state)
    {
        return Float.MAX_VALUE;
    }

    /**
     * Used when clicking on a block in the world. Puts the clicked column inside the border, or takes it out while
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

        final ServerLevel level = (ServerLevel) ctx.getLevel();
        final BlockPos clickedPos = ctx.getClickedPos();
        final ChunkPos chunkPos = ChunkPos.containing(clickedPos);
        final LevelChunk chunk = level.getChunk(chunkPos.x(), chunkPos.z());
        final boolean claim = !ctx.isSecondaryUseActive();

        final ItemStack scepter = ctx.getItemInHand();
        final BlockPos corner = PosSelection.readFromItemStack(scepter).startPos().orElse(null);
        if (corner != null)
        {
            // Cleared before the fill, not after, so that a rectangle that is refused for being too big still puts
            // the player back to single columns rather than leaving a corner armed behind an error message.
            PosSelection.updateItemStack(scepter, selection -> selection.setStartPos(null));
            return paintRectangle(level, player, corner, clickedPos, claim);
        }

        IChunkClaimData claimData = IColonyManager.getInstance().getClaimData(level.dimension(), chunkPos);
        Colony colony = ownerOf(level, claimData);

        if (colony == null)
        {
            if (!claim)
            {
                // Nothing out here belongs to anyone, so there is nothing to cut out of a border either.
                MessageUtils.format(TOOL_BORDER_SCEPTER_NOT_CLAIMED).sendTo(player);
                SoundUtils.playErrorSound(player, player.blockPosition());
                return InteractionResult.FAIL;
            }

            colony = closestColony(level, clickedPos);
            if (colony == null)
            {
                MessageUtils.format(TOOL_BORDER_SCEPTER_NO_COLONY).sendTo(player);
                SoundUtils.playErrorSound(player, player.blockPosition());
                return InteractionResult.FAIL;
            }

            if (!ChunkDataHelper.mayEditClaim(colony, player))
            {
                MessageUtils.format(TOOL_BORDER_SCEPTER_NO_PERMISSION, colony.getName()).sendTo(player);
                SoundUtils.playErrorSound(player, player.blockPosition());
                return InteractionResult.FAIL;
            }

            claimData = pullChunkIn(level, chunk, colony);
            if (claimData == null)
            {
                MessageUtils.format(TOOL_BORDER_SCEPTER_NOT_CLAIMED).sendTo(player);
                SoundUtils.playErrorSound(player, player.blockPosition());
                return InteractionResult.FAIL;
            }

            MessageUtils.format(TOOL_BORDER_SCEPTER_NEW_CHUNK, colony.getName()).sendTo(player);
        }
        else if (!ChunkDataHelper.mayEditClaim(colony, player))
        {
            MessageUtils.format(TOOL_BORDER_SCEPTER_NO_PERMISSION, colony.getName()).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        if (claimData.isColumnClaimed(clickedPos) == claim)
        {
            // Painting back over what has already been painted. Saying nothing keeps a held button quiet.
            return InteractionResult.SUCCESS;
        }

        claimData.setColumnClaimed(clickedPos, claim, chunk);
        colony.markDirty();

        if (releaseIfEmpty(level, chunk, chunkPos, claimData, colony))
        {
            MessageUtils.format(TOOL_BORDER_SCEPTER_RELEASED, colony.getName()).sendTo(player);
            return InteractionResult.SUCCESS;
        }

        // PORT(26.2): sendOverlayMessage, i.e. the action bar, is what displayClientMessage(component, true) was.
        // The action bar rather than the chat because this fires five times a second while the button is held.
        player.sendOverlayMessage(Component.translatable(TOOL_BORDER_SCEPTER_PROGRESS,
          claimData.getClaimedColumnCount(),
          COLUMNS_PER_CHUNK,
          colony.getName()));
        return InteractionResult.SUCCESS;
    }

    /**
     * Mid air use. Reports the chunk the player is standing in, or restores it to a whole claim while sneaking.
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

        final BlockPos pos = playerIn.blockPosition();
        final ChunkPos chunkPos = ChunkPos.containing(pos);
        final IChunkClaimData claimData = IColonyManager.getInstance().getClaimData(worldIn.dimension(), chunkPos);
        final Colony colony = ownerOf(worldIn, claimData);

        if (colony == null)
        {
            MessageUtils.format(TOOL_BORDER_SCEPTER_NOT_CLAIMED).sendTo(playerIn);
            return InteractionResult.FAIL;
        }

        if (!playerIn.isShiftKeyDown())
        {
            MessageUtils.format(TOOL_BORDER_SCEPTER_INFO, colony.getName(), claimData.getClaimedColumnCount(), COLUMNS_PER_CHUNK).sendTo(playerIn);
            return InteractionResult.SUCCESS;
        }

        if (!ChunkDataHelper.mayEditClaim(colony, playerIn))
        {
            MessageUtils.format(TOOL_BORDER_SCEPTER_NO_PERMISSION, colony.getName()).sendTo(playerIn);
            SoundUtils.playErrorSound(playerIn, pos);
            return InteractionResult.FAIL;
        }

        if (!claimData.hasPartialClaim())
        {
            MessageUtils.format(TOOL_BORDER_SCEPTER_ALREADY_WHOLE).sendTo(playerIn);
            return InteractionResult.FAIL;
        }

        claimData.clearPartialClaim(((ServerLevel) worldIn).getChunk(chunkPos.x(), chunkPos.z()));
        colony.markDirty();

        MessageUtils.format(TOOL_BORDER_SCEPTER_RESET, colony.getName()).sendTo(playerIn);
        SoundUtils.playSuccessSound(playerIn, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(@NotNull final ItemStack stack,
      @NotNull final TooltipContext ctx,
      @NotNull final TooltipDisplay display,
      @NotNull final Consumer<Component> tooltip,
      @NotNull final TooltipFlag flags)
    {
        final MutableComponent guiHint = Component.translatableEscape(TOOL_BORDER_SCEPTER_DESCRIPTION);
        guiHint.setStyle(Style.EMPTY.withColor(ChatFormatting.DARK_AQUA));
        tooltip.accept(guiHint);

        super.appendHoverText(stack, ctx, display, tooltip, flags);
    }

    /**
     * Fill every column between two corners, painting or erasing.
     * <p>
     * The colony is resolved from the second corner, exactly as a single click resolves it from where it landed: the
     * colony owning that chunk, or the nearest one if nobody owns it. The whole rectangle is then that colony's, which
     * is what makes a rectangle straddling a border legible — it grows one colony rather than each chunk's own owner.
     * <p>
     * Chunks somebody else owns are skipped whole and counted, the same refusal {@code ItemScepterClaim} makes; a
     * rectangle drawn over a neighbour leaves no trace in his land. Chunks nobody owns are pulled in exactly as a
     * single click pulls one in, and are pulled in only when painting: there is nothing to erase from ground that
     * belongs to nobody.
     *
     * @param level   the level.
     * @param player  the player.
     * @param cornerA the corner marked by left-click.
     * @param cornerB the corner just clicked.
     * @param claim   true to paint the columns in, false to cut them out.
     * @return the result.
     */
    private static InteractionResult paintRectangle(final ServerLevel level,
      final Player player,
      final BlockPos cornerA,
      final BlockPos cornerB,
      final boolean claim)
    {
        final int minX = Math.min(cornerA.getX(), cornerB.getX());
        final int maxX = Math.max(cornerA.getX(), cornerB.getX());
        final int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        final int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        final int columns = (maxX - minX + 1) * (maxZ - minZ + 1);
        if (columns > MAX_RECTANGLE_COLUMNS)
        {
            MessageUtils.format(TOOL_BORDER_SCEPTER_TOO_BIG, columns, MAX_RECTANGLE_COLUMNS).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        Colony colony = ownerOf(level, IColonyManager.getInstance().getClaimData(level.dimension(), ChunkPos.containing(cornerB)));
        if (colony == null)
        {
            if (!claim)
            {
                MessageUtils.format(TOOL_BORDER_SCEPTER_NOT_CLAIMED).sendTo(player);
                SoundUtils.playErrorSound(player, player.blockPosition());
                return InteractionResult.FAIL;
            }

            colony = closestColony(level, cornerB);
            if (colony == null)
            {
                MessageUtils.format(TOOL_BORDER_SCEPTER_NO_COLONY).sendTo(player);
                SoundUtils.playErrorSound(player, player.blockPosition());
                return InteractionResult.FAIL;
            }
        }

        if (!ChunkDataHelper.mayEditClaim(colony, player))
        {
            MessageUtils.format(TOOL_BORDER_SCEPTER_NO_PERMISSION, colony.getName()).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        int painted = 0;
        int skippedChunks = 0;
        final BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();

        for (int chunkX = minX >> 4; chunkX <= (maxX >> 4); chunkX++)
        {
            for (int chunkZ = minZ >> 4; chunkZ <= (maxZ >> 4); chunkZ++)
            {
                final ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                IChunkClaimData claimData = IColonyManager.getInstance().getClaimData(level.dimension(), chunkPos);
                final int owner = claimData == null ? NO_COLONY_ID : claimData.getOwningColony();

                if (owner != NO_COLONY_ID && owner != colony.getID())
                {
                    skippedChunks++;
                    continue;
                }

                final LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                if (owner == NO_COLONY_ID)
                {
                    if (!claim)
                    {
                        continue;
                    }

                    claimData = pullChunkIn(level, chunk, colony);
                    if (claimData == null)
                    {
                        skippedChunks++;
                        continue;
                    }
                }

                for (int x = Math.max(minX, chunkPos.getMinBlockX()); x <= Math.min(maxX, chunkPos.getMaxBlockX()); x++)
                {
                    for (int z = Math.max(minZ, chunkPos.getMinBlockZ()); z <= Math.min(maxZ, chunkPos.getMaxBlockZ()); z++)
                    {
                        column.set(x, 0, z);
                        if (claimData.isColumnClaimed(column) == claim)
                        {
                            continue;
                        }

                        claimData.setColumnClaimed(column, claim, chunk);
                        painted++;
                    }
                }

                releaseIfEmpty(level, chunk, chunkPos, claimData, colony);
            }
        }

        colony.markDirty();

        if (painted == 0)
        {
            MessageUtils.format(TOOL_BORDER_SCEPTER_RECTANGLE_NOTHING).sendTo(player);
            SoundUtils.playErrorSound(player, player.blockPosition());
            return InteractionResult.FAIL;
        }

        MessageUtils.format(claim ? TOOL_BORDER_SCEPTER_RECTANGLE_PAINTED : TOOL_BORDER_SCEPTER_RECTANGLE_ERASED,
          painted,
          colony.getName()).sendTo(player);
        if (skippedChunks > 0)
        {
            MessageUtils.format(TOOL_BORDER_SCEPTER_RECTANGLE_SKIPPED, skippedChunks).sendTo(player);
        }
        SoundUtils.playSuccessSound(player, player.blockPosition());
        return InteractionResult.SUCCESS;
    }

    /**
     * Take a chunk nobody owns, hand it to a colony, and start its border empty.
     * <p>
     * Claiming through {@link ChunkDataHelper#tryClaim} is the same call the claim scepter and the colony's own
     * growth make, with {@code forceOwnerChange} false so it can never take a chunk off someone else — the caller
     * has already established that nobody owns this one. What differs is what happens next: a chunk claimed the
     * ordinary way belongs to the colony whole, and this one starts with nothing inside its border, so only the
     * columns actually painted on it become the colony's land.
     *
     * @param level  the level.
     * @param chunk  the chunk.
     * @param colony the colony taking it.
     * @return the chunk's claim, or null if the claim did not take.
     */
    @Nullable
    private static IChunkClaimData pullChunkIn(final ServerLevel level, final LevelChunk chunk, final Colony colony)
    {
        ChunkDataHelper.tryClaim(level, chunk.getPos().getWorldPosition(), true, colony, false);

        final IChunkClaimData claimData = IColonyManager.getInstance().getClaimData(level.dimension(), chunk.getPos());
        if (claimData == null)
        {
            return null;
        }

        claimData.clearAllColumns(chunk);
        return claimData;
    }

    /**
     * Give a chunk back once the last of it has been rubbed out.
     * <p>
     * Erasing every column leaves a claim that covers nothing, which would still hold the chunk against every other
     * colony while being nobody's land in every other respect. Rubbing out the last of a chunk is a clear enough
     * statement that it should go back.
     * <p>
     * The colony's centre chunk is the exception, and is kept whatever its border looks like: everything that asks
     * which colony a position is in reads the chunk claim, so a colony that has given its centre away stops
     * recognising its own town hall.
     *
     * @param level     the level.
     * @param chunk     the chunk.
     * @param chunkPos  the chunk's position.
     * @param claimData the chunk's claim.
     * @param colony    the colony holding it.
     * @return true if the chunk was given back.
     */
    private static boolean releaseIfEmpty(final ServerLevel level,
      final LevelChunk chunk,
      final ChunkPos chunkPos,
      final IChunkClaimData claimData,
      final Colony colony)
    {
        if (claimData.getClaimedColumnCount() > 0 || ChunkPos.containing(colony.getCenter()).equals(chunkPos))
        {
            return false;
        }

        ChunkDataHelper.tryClaim(level, chunkPos.getWorldPosition(), false, colony, false);
        if (claimData.getOwningColony() == NO_COLONY_ID)
        {
            // Only once it is really nobody's: a border drawn on it means nothing then, and leaving it behind would
            // silently shape the claim of whoever takes the chunk next.
            claimData.clearPartialClaim(chunk);
        }
        return true;
    }

    /**
     * The colony a chunk outside every border would be claimed for, which is the nearest one — the same rule the
     * land claim scepter uses, and with the same absence of a distance limit.
     *
     * @param level the level.
     * @param pos   the position that was interacted with.
     * @return the colony, or null if there is none.
     */
    @Nullable
    private static Colony closestColony(final ServerLevel level, final BlockPos pos)
    {
        // getClosestNonHostileColony rather than getClosestColony: a hostile territory owns the chunk it is on, so
        // the plain question would answer with the enemy and quietly draw the enemy's border instead of the
        // player's. Painting a territory's own border is done with the territory scepter.
        final IColony colony = IColonyManager.getInstance().getClosestNonHostileColony(level, pos);
        return colony instanceof Colony ? (Colony) colony : null;
    }

    /**
     * The colony owning a chunk, given that chunk's claim.
     * <p>
     * The owner rather than the closest colony: this edits the shape of a claim that already exists, and only the
     * colony holding it has one to edit.
     *
     * @param level     the level.
     * @param claimData the chunk's claim, possibly null.
     * @return the colony, or null if the chunk is unclaimed or its colony is not loaded.
     */
    @Nullable
    private static Colony ownerOf(final Level level, @Nullable final IChunkClaimData claimData)
    {
        if (claimData == null || claimData.getOwningColony() == NO_COLONY_ID)
        {
            return null;
        }

        final IColony colony = IColonyManager.getInstance().getColonyByDimension(claimData.getOwningColony(), level.dimension());
        return colony instanceof Colony ? (Colony) colony : null;
    }
}
