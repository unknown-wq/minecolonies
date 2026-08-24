package com.minecolonies.core.util;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.claim.ChunkClaimData;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.util.*;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import net.minecraft.world.entity.player.Player;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.Colony;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import com.minecolonies.api.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.Constants.BLOCKS_PER_CHUNK;
import static com.minecolonies.api.util.constant.TranslationConstants.COLONY_SIZE_CHANGE;

/**
 * Class to take care of chunk data helper.
 */
public final class ChunkDataHelper
{
    /**
     * Private constructor to hide implicit one.
     */
    private ChunkDataHelper()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Load the colony info for a certain chunk.
     *
     * @param chunk the chunk.
     * @param world the worldg to.
     */
    public static void loadChunk(final LevelChunk chunk, final ServerLevel world)
    {
        final int closeColony = ColonyUtils.getOwningColony(chunk);
        if (closeColony != 0)
        {
            final IColony colony = IColonyManager.getInstance().getColonyByDimension(closeColony, world.dimension());
            if (colony != null)
            {
                colony.addLoadedChunk(ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z()), chunk);
            }
        }
    }

    /**
     * Called when a chunk is unloaded
     *
     * @param world the world it is unloading in.
     * @param chunk the chunk that is unloading.
     */
    public static void unloadChunk(final LevelChunk chunk, final Level world)
    {
        final int closeColony = ColonyUtils.getOwningColony(chunk);
        if (closeColony != 0)
        {
            final IColony colony = IColonyManager.getInstance().getColonyByDimension(closeColony, world.dimension());
            if (colony != null)
            {
                colony.removeLoadedChunk(ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z()));
            }
        }
    }

    /**
     * Notify all chunks in the range of the colony about the colony.
     *
     * @param world  the world.
     * @param add    if add or remove.
     * @param colony the colony.
     * @param center the center chunk.
     */
    public static void claimColonyChunks(final ServerLevel world, final boolean add, final Colony colony, final BlockPos center)
    {
        final int range = MineColonies.getConfig().getServer().initialColonySize.get();
        staticClaimInRange(colony, add, center, add ? range : range * 2, world, false);
    }

    /**
     * Notify all chunks in the range of the colony about the colony.
     * <p>
     * --- This is only for dynamic claiming ---
     *
     * @param colony  the colony to claim for
     * @param add     if add or remove.
     * @param center  the center position of the colony.
     * @param range   the range to claim.
     * @param corners also (un)claim all chunks intersecting this box (if not null)
     */
    public static void claimBuildingChunks(
      final Colony colony, final boolean add, final BlockPos center, final int range,
      @Nullable final Tuple<BlockPos, BlockPos> corners)
    {
        buildingClaimInRange(colony, add, range, center, false);

        if (corners != null)
        {
            buildingClaimBox(colony, center, add, corners);
        }
    }

    /**
     * Check if all chunks within a certain range can be claimed, if range is too big this might require to load chunks. Use carefully.
     * <p>
     * --- This is only for dynamic claiming ---
     *
     * @param w     the world.
     * @param pos   the center position.
     * @param range the range to check.
     * @return true if possible.
     */
    public static boolean canClaimChunksInRange(final Level w, final BlockPos pos, final int range)
    {
        final LevelChunk centralChunk = w.getChunkAt(pos);
        final int chunkX = centralChunk.getPos().x();
        final int chunkZ = centralChunk.getPos().z();

        for (int i = chunkX - range; i <= chunkX + range; i++)
        {
            for (int j = chunkZ - range; j <= chunkZ + range; j++)
            {
                final LevelChunk chunk = w.getChunk(i, j);
                final IChunkClaimData colonyCap = IColonyManager.getInstance().getClaimData(w.dimension(), chunk.getPos());
                if (colonyCap == null)
                {
                    return true;
                }
                if (colonyCap.getOwningColony() != 0)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Claim a number of chunks in a certain range around a position. Prevents the initial chunkradius from beeing unclaimed, unless forced.
     *
     * @param colony the colony to claim for
     * @param add    if claim or unclaim.
     * @param range  the range.
     * @param center the center position to be claimed.
     * @param force  whether to ignore restrictions.
     */
    private static void buildingClaimInRange(
      final Colony colony,
      final boolean add,
      final int range,
      final BlockPos center,
      final boolean force)
    {
        final ServerLevel world = colony.getWorld();

        final int chunkX = center.getX() >> 4;
        final int chunkZ = center.getZ() >> 4;

        final BuildingClaimGuard guard = new BuildingClaimGuard(colony, center);

        for (int i = chunkX - range; i <= chunkX + range; i++)
        {
            for (int j = chunkZ - range; j <= chunkZ + range; j++)
            {
                final ChunkPos chunkPos = new ChunkPos(i, j);

                if (add && !force && !guard.mayClaim(chunkPos))
                {
                    continue;
                }

                if (tryClaimBuilding(world, chunkPos, add, colony, center))
                {
                    continue;
                }
            }
        }

        if (add && range > 0)
        {
            final IBuilding building = colony.getServerBuildingManager().getBuilding(center);
            MessageUtils.format(COLONY_SIZE_CHANGE, range, building.getSchematicName()).sendTo(colony).forManagers();
        }
    }

    /**
     * (Un)Claim all chunks within the given box for a specific building.
     *
     * @param colony  the colony to claim for
     * @param anchor  the building anchor to claim for
     * @param add     if claim or unclaim.
     * @param corners the box.
     */
    private static void buildingClaimBox(
      final Colony colony,
      final BlockPos anchor,
      final boolean add,
      final Tuple<BlockPos, BlockPos> corners)
    {
        final ServerLevel world = (ServerLevel) colony.getWorld();
        final BuildingClaimGuard guard = new BuildingClaimGuard(colony, anchor);

        for (final ChunkPos chunk : ChunkPos.rangeClosed(ChunkPos.containing(corners.getA()), ChunkPos.containing(corners.getB())).toList())
        {
            if (add && !guard.mayClaim(chunk))
            {
                continue;
            }

            tryClaimBuilding(world, chunk, add, colony, anchor);
        }
    }

    /**
     * The rule deciding which chunks a single building may claim, and the outlying-land budget it spends doing so.
     * <p>
     * Upstream asked one question: is this chunk further than {@code maxColonySize} chunks from the colony centre? If
     * it was, the claim was dropped and the fact logged at debug level only. That is right for a colony that grows
     * outwards from its town hall and wrong for an enclave -- a second patch of claimed land, far from the centre,
     * which the land claim scepter is deliberately willing to create
     * ({@link com.minecolonies.core.items.ItemScepterClaim}). In an enclave <em>no</em> building claim was ever
     * registered: not the footprint, not the level radius, not the guard tower radius. See {@code 26.2/ENCLAVE-BUILD.md}.
     * <p>
     * The rule here answers three questions instead:
     * <ol>
     *     <li><b>Is the chunk already this colony's?</b> Then claiming it is not an act of expansion at all -- the
     *     land is owned, the building only records that it stands on it -- so it is always allowed, at any distance,
     *     and costs no budget. This is what makes a hut on scepter-claimed ground register its own footprint.</li>
     *     <li><b>Is it within {@code maxColonySize} of the colony centre?</b> Then the upstream rule applies
     *     unchanged, and nothing about an ordinary colony behaves differently than before.</li>
     *     <li><b>Otherwise it is new outlying land.</b> Only a building that itself stands on a chunk this colony
     *     owns may take it, only within the same {@code maxColonySize} reach it would have around the centre, and
     *     only while the colony is under the {@code maxoutlyingchunks} ceiling. A hut cannot be placed on land that
     *     is not the colony's in the first place, so this bounds expansion to the footprint and level radius around
     *     buildings the player has already earned the ground for.</li>
     * </ol>
     * The ceiling counts only chunks the old rule would never have allowed, so it can never refuse anything an
     * existing save already holds.
     */
    private static final class BuildingClaimGuard
    {
        /**
         * The colony claiming.
         */
        private final Colony colony;

        /**
         * The chunk the colony centre is in.
         */
        private final ChunkPos centreChunk;

        /**
         * The chunk the claiming building is in.
         */
        private final ChunkPos anchorChunk;

        /**
         * {@code maxColonySize} in chunks, 0 for "no distance limit".
         */
        private final int maxColonySize;

        /**
         * Whether the claiming building stands on a chunk this colony owns. Only then may it take outlying land.
         */
        private final boolean anchorOnOwnLand;

        /**
         * How many more outlying chunks this colony may take, or -1 while it has not been counted yet. Counting walks
         * the colony's claim map, so it is done at most once per building claim and only when an outlying chunk is
         * actually wanted.
         */
        private int outlyingBudget = -1;

        BuildingClaimGuard(final Colony colony, final BlockPos anchor)
        {
            this.colony = colony;
            this.centreChunk = new ChunkPos(colony.getCenter().getX() >> 4, colony.getCenter().getZ() >> 4);
            this.anchorChunk = new ChunkPos(anchor.getX() >> 4, anchor.getZ() >> 4);
            this.maxColonySize = MineColonies.getConfig().getServer().maxColonySize.get();
            this.anchorOnOwnLand = owns(colony, this.anchorChunk);
        }

        /**
         * Whether the building may claim this chunk.
         * <p>
         * Only ever asked when claiming. Releasing a claim is never refused: a building being removed or downgraded
         * has to be able to let go of every chunk it took, including ground a neighbouring colony has since taken
         * over, or the claim map keeps a reference to a building that no longer exists.
         *
         * @param pos the chunk.
         * @return true if the claim goes ahead.
         */
        boolean mayClaim(final ChunkPos pos)
        {
            if (owns(colony, pos) || maxColonySize == 0)
            {
                return true;
            }

            if (pos.distanceSquared(centreChunk) <= (long) maxColonySize * maxColonySize)
            {
                return true;
            }

            if (!anchorOnOwnLand || pos.distanceSquared(anchorChunk) > (long) maxColonySize * maxColonySize)
            {
                Log.getLogger()
                  .debug("Colony " + colony.getID() + " may not claim chunk " + pos + " for the building at " + anchorChunk
                           + ": it is outlying land and the building does not stand on colony ground.");
                return false;
            }

            return spendOutlying(pos);
        }

        /**
         * Take one chunk off the colony's outlying-land ceiling.
         *
         * @param pos the chunk wanted, for the log line.
         * @return true if there was budget left.
         */
        private boolean spendOutlying(final ChunkPos pos)
        {
            final int max = MineColonies.getConfig().getServer().maxOutlyingChunks.get();
            if (max == 0)
            {
                return true;
            }

            if (outlyingBudget < 0)
            {
                outlyingBudget = max - countOutlying();
            }

            if (outlyingBudget <= 0)
            {
                Log.getLogger()
                  .info("Colony " + colony.getID() + " has reached maxoutlyingchunks (" + max + ") and will not claim chunk " + pos
                          + " for the building at " + anchorChunk + ". Raise the config, or claim the chunk by hand with the Land Claim Scepter.");
                return false;
            }

            outlyingBudget--;
            return true;
        }

        /**
         * How many chunks this colony owns that lie further than {@code maxColonySize} from its centre.
         *
         * @return the count.
         */
        private int countOutlying()
        {
            int count = 0;
            for (final Long2ObjectMap.Entry<ChunkClaimData> entry : colony.getClaimData().long2ObjectEntrySet())
            {
                if (entry.getValue().getOwningColony() != colony.getID())
                {
                    continue;
                }

                final ChunkPos pos = ChunkPos.unpack(entry.getLongKey());
                if (pos.distanceSquared(centreChunk) > (long) maxColonySize * maxColonySize)
                {
                    count++;
                }
            }
            return count;
        }

        /**
         * Whether a chunk belongs to this colony.
         *
         * @param colony the colony.
         * @param pos    the chunk.
         * @return true if the colony owns it.
         */
        private static boolean owns(final Colony colony, final ChunkPos pos)
        {
            return ColonyUtils.getOwningColony(colony.getDimension(), pos) == colony.getID();
        }
    }

    /**
     * Claim a number of chunks in a certain range around a position.
     *
     * @param colony   the colony.
     * @param add      if claim or unclaim.
     * @param center   the center position to be claimed.
     * @param range    the range.
     * @param world    the world.
     */
    public static void staticClaimInRange(
      final Colony colony,
      final boolean add,
      final BlockPos center,
      final int range,
      final ServerLevel world,
      final boolean forceOwnerChange)
    {
        final LevelChunk centralChunk = world.getChunkAt(center);

        final int chunkXMax = centralChunk.getPos().x();
        final int chunkZMax = centralChunk.getPos().z();

        for (int chunkPosX = chunkXMax - range; chunkPosX <= chunkXMax + range; chunkPosX++)
        {
            for (int chunkPosZ = chunkZMax - range; chunkPosZ <= chunkZMax + range; chunkPosZ++)
            {
                tryClaim(world, new BlockPos(chunkPosX * BLOCKS_PER_CHUNK, 0, chunkPosZ * BLOCKS_PER_CHUNK), add, colony, forceOwnerChange);
            }
        }
    }

    /**
     * Whether a player is allowed to move a colony's borders with one of the land scepters.
     * <p>
     * Normally {@link Action#MANAGE_HUTS}, the same permission the claim scepter has always asked for. A hostile
     * territory is the exception and has to be, because it belongs to nobody on purpose — every player is
     * {@code NEUTRAL} there, so the ordinary permission can never be granted and its border could never be edited at
     * all. An operator may edit it instead, which is the same authority that created it.
     *
     * @param colony the colony whose claim is being edited.
     * @param player the player holding the scepter.
     * @return true if the edit is allowed.
     */
    public static boolean mayEditClaim(final IColony colony, final Player player)
    {
        if (colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            return true;
        }

        return colony.isHostile() && IMCCommand.isPlayerOped(player);
    }

    /**
     * Add the data to the chunk directly.
     *
     * @param world         the world.
     * @param chunkBlockPos the position.
     * @param add           if add or delete.
     * @param colony        the colony.
     * @return true if successful.
     */
    public static boolean tryClaim(
      final ServerLevel world,
      final BlockPos chunkBlockPos,
      final boolean add,
      final Colony colony,
      boolean forceOwnerChange)
    {
        final LevelChunk chunk = (LevelChunk) world.getChunk(chunkBlockPos);
        IChunkClaimData chunkClaimData = IColonyManager.getInstance().getClaimData(world.dimension(), chunk.getPos());
        final int id = colony.getID();
        if (chunkClaimData == null)
        {
            if (add)
            {
                chunkClaimData = colony.claimNewChunk(chunk.getPos());
            }
            else
            {
                return true;
            }
        }

        if (add)
        {
            chunkClaimData.addColony(id, chunk);
            if (forceOwnerChange)
            {
                chunkClaimData.setOwningColony(id, chunk);
                colony.addLoadedChunk(ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z()), chunk);
            }
        }
        else
        {
            chunkClaimData.removeColony(id, chunk);
        }
        return true;
    }

    /**
     * Add the data to the chunk directly for dynamic claiming.
     * <p>
     * ----- Only for dynamic claiming -----
     *
     * @param world         the world.
     * @param chunkBlockPos the position.
     * @param add           if add or delete.
     * @param colony        the colony.
     * @param buildingPos   the building pos.
     * @return true if successful.
     */
    public static boolean tryClaimBuilding(
      final ServerLevel world,
      final ChunkPos chunkBlockPos,
      final boolean add,
      final Colony colony,
      final BlockPos buildingPos)
    {
        final LevelChunk chunk = world.getChunk(chunkBlockPos.x(), chunkBlockPos.z());
        IChunkClaimData chunkClaimData = IColonyManager.getInstance().getClaimData(world.dimension(), chunk.getPos());;
        if (chunkClaimData == null)
        {
            if (add)
            {
                chunkClaimData = colony.claimNewChunk(chunk.getPos());
            }
            else
            {
                return false;
            }
        }

        if (chunk.getPos().equals(ChunkPos.ZERO))
        {
            if (chunk.getPos().equals(ChunkPos.ZERO))
            {
                if (colony == null || BlockPosUtil.getDistance2D(colony.getCenter(), BlockPos.ZERO) > 200)
                {
                    Log.getLogger().warn("Trying to claim at zero chunk pos!:", new Exception());
                }
            }
        }

        if (add)
        {
            chunkClaimData.addBuildingClaim(colony.getID(), buildingPos, chunk);
        }
        else
        {
            chunkClaimData.removeBuildingClaim(colony.getID(), buildingPos, chunk);
        }

        return true;
    }
}
