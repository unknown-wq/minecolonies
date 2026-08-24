package com.minecolonies.api.util;


// PORT-TODO(structurize): re-check list only. This file compiles against the real 26.2 Structurize
// library and is part of the build; structurize-blocked.txt is nothing but the grep list of files worth
// re-verifying against the real API. build.gradle:128-133 states outright that this build never reads it.

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.storage.ClientFutureProcessor;
import com.ldtteam.structurize.storage.ServerFutureProcessor;
import com.ldtteam.structurize.storage.StructurePacks;
import com.ldtteam.structurize.util.IOPool;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import net.minecraft.core.BlockPos;
import com.minecolonies.api.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.ColonyManagerConstants.NO_COLONY_ID;

/**
 * Contains colony specific utility.
 */
public final class ColonyUtils
{
    /**
     * Private constructor to hide implicit one.
     */
    private ColonyUtils()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Queues a blueprint load to the right side
     *
     * @param world
     * @param structurePack
     * @param structurePath
     * @param afterLoad
     */
    public static CompletableFuture<Blueprint> queueBlueprintLoad(final Level world, final String structurePack, final String structurePath, final Consumer<Blueprint> afterLoad)
    {
        return queueBlueprintLoad(world, structurePack, structurePath, afterLoad, e -> Log.getLogger().warn(e));
    }

    /**
     * Queues a blueprint load to the right side
     *
     * @param world
     * @param structurePack
     * @param structurePath
     * @param afterLoad
     */
    public static CompletableFuture<Blueprint> queueBlueprintLoad(
        final Level world,
        final String structurePack,
        final String structurePath,
        final Consumer<Blueprint> afterLoad,
        final Consumer<String> errorHandler)
    {
        final CompletableFuture<Blueprint> future =
            CompletableFuture.supplyAsync(() -> StructurePacks.getBlueprint(structurePack, structurePath, !FabricLoader.getInstance().isDevelopmentEnvironment(), world.registryAccess()), IOPool.getExecutor());
        if (world.isClientSide())
        {
            ClientFutureProcessor.queueBlueprint(new ClientFutureProcessor.BlueprintProcessingData(future,
                (blueprint ->
                {
                    if (blueprint == null)
                    {
                        errorHandler.accept("Couldn't find structure with name: " + structurePack + " in: " + structurePath + ". Aborting loading procedure");
                    }
                    else
                    {
                        afterLoad.accept(blueprint);
                    }
                })));

            return future;
        }
        else
        {
            ServerFutureProcessor.queueBlueprint(new ServerFutureProcessor.BlueprintProcessingData(future, world,
                (blueprint ->
                {
                    if (blueprint == null)
                    {
                        errorHandler.accept("Couldn't find structure with name: " + structurePack + " in: " + structurePath + ". Aborting loading procedure");
                    }
                    else
                    {
                        afterLoad.accept(blueprint);
                    }
                })));

            return future;
        }
    }

    /**
     * Calculated the corner of a building.  Also rotates the blueprint accordingly.
     *
     * @param pos        the central position.
     * @param world      the world.
     * @param blueprint  the structureWrapper.
     * @param rotMir     the rotation and mirror.
     * @return a tuple with the required corners.
     */
    public static Tuple<BlockPos, BlockPos> calculateCorners(
      final BlockPos pos,
      final Level world,
      final Blueprint blueprint,
      final RotationMirror rotMir)
    {
        if (blueprint == null)
        {
            return new Tuple<>(pos, pos);
        }

        blueprint.setRotationMirror(rotMir, world);
        final BlockPos zeroPos = pos.subtract(blueprint.getPrimaryBlockOffset());

        final BlockPos pos1 = new BlockPos(zeroPos.getX(), zeroPos.getY(), zeroPos.getZ());
        final BlockPos pos2 = new BlockPos(zeroPos.getX() + blueprint.getSizeX() - 1, zeroPos.getY() + blueprint.getSizeY() - 1, zeroPos.getZ() + blueprint.getSizeZ() - 1);

        return new Tuple<>(pos1, pos2);
    }

    /**
     * Reports the block corners from a bounding box.
     *
     * @param box the bounding box.
     * @return the corners.
     */
    public static Tuple<BlockPos, BlockPos> calculateCorners(@NotNull final AABB box)
    {
        final BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        final BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        return new Tuple<>(min, max);
    }

    /**
     * Resolve the dimension key of a chunk.
     * <p>
     * 26.2 removed {@code ChunkAccess#getLevel()}; only {@link net.minecraft.world.level.chunk.LevelChunk} still
     * knows its level. Every current call site passes a {@code LevelChunk} (they all come from
     * {@code level.getChunk(...)}), so the signature is kept and the narrowing is done here.
     *
     * @param chunk the chunk.
     * @return its dimension key, or the overworld key if the chunk is not level-bound.
     */
    private static ResourceKey<Level> dimensionOf(final ChunkAccess chunk)
    {
        return chunk instanceof final LevelChunk levelChunk ? levelChunk.getLevel().dimension() : Level.OVERWORLD;
    }

    /**
     * Get the owning colony from a chunk.
     * @param chunk the chunk to check.
     * @return the colony id.
     */
    public static int getOwningColony(final ChunkAccess chunk)
    {
        final IChunkClaimData cap = IColonyManager.getInstance().getClaimData(dimensionOf(chunk), chunk.getPos());
        return cap == null ? NO_COLONY_ID : cap.getOwningColony();
    }

    /**
     * Get the owning colony of a chunk named by its position, without touching the chunk itself.
     * <p>
     * PORT-NOTE: the same answer as {@link #getOwningColony(ChunkAccess)}, which only ever wanted the chunk for its
     * dimension key -- the claim itself lives in {@link IColonyManager}, keyed by dimension and {@link ChunkPos}. Every
     * caller that already knows the dimension should use this one: {@code Level#getChunk(int, int)} loads and, on ground
     * nobody has visited, generates the chunk on the server thread, which is a large price for reading a number out of
     * a map.
     *
     * @param dimension the dimension the chunk is in.
     * @param pos       the chunk position.
     * @return the colony id, or {@link com.minecolonies.api.util.constant.ColonyManagerConstants#NO_COLONY_ID}.
     */
    public static int getOwningColony(final ResourceKey<Level> dimension, final ChunkPos pos)
    {
        final IChunkClaimData cap = IColonyManager.getInstance().getClaimData(dimension, pos);
        return cap == null ? NO_COLONY_ID : cap.getOwningColony();
    }

    /**
     * Get the owning colony at one position, rather than for the chunk as a whole.
     * <p>
     * The same as {@link #getOwningColony(ChunkAccess)} for every chunk claimed the ordinary way. It differs only on a
     * chunk whose border has been redrawn by hand with the border scepter: there the claim covers some of the columns
     * and not others, and a position in a column that was cut out belongs to nobody.
     *
     * @param chunk the chunk the position is in.
     * @param pos   the position.
     * @return the colony id, or {@link com.minecolonies.api.util.constant.ColonyManagerConstants#NO_COLONY_ID}.
     */
    public static int getOwningColony(final ChunkAccess chunk, final BlockPos pos)
    {
        final IChunkClaimData cap = IColonyManager.getInstance().getClaimData(dimensionOf(chunk), chunk.getPos());
        if (cap == null || !cap.isColumnClaimed(pos))
        {
            return NO_COLONY_ID;
        }
        return cap.getOwningColony();
    }

    /**
     * Get all claiming buildings from the chunk.
     * @param chunk the chunk they are at.
     * @return the map from colony to building claims.
     */
    public static Map<Integer, Set<BlockPos>> getAllClaimingBuildings(final ChunkAccess chunk)
    {
        final IChunkClaimData cap = IColonyManager.getInstance().getClaimData(dimensionOf(chunk), chunk.getPos());
        return cap == null ? new HashMap<>() : cap.getAllClaimingBuildings();
    }

    /**
     * Get all static claims from a chunk.
     * @param chunk the chunk to get it from.
     * @return the list.
     */
    public static List<Integer> getStaticClaims(final ChunkAccess chunk)
    {
        final IChunkClaimData cap = IColonyManager.getInstance().getClaimData(dimensionOf(chunk), chunk.getPos());
        return cap == null ? new ArrayList<>() : cap.getStaticClaimColonies();
    }

    /**
     * Get comprehensive chunk ownership data.
     * @param chunk the chunk to get it from.
     * @return the ownership data, or null.
     */
    @Nullable
    public static ChunkCapData getChunkCapData(final ChunkAccess chunk)
    {
        final IChunkClaimData cap = IColonyManager.getInstance().getClaimData(dimensionOf(chunk), chunk.getPos());
        return cap == null ? new ChunkCapData(chunk.getPos().x(), chunk.getPos().z()) : new ChunkCapData(chunk.getPos().x(), chunk.getPos().z(), cap.getOwningColony(), cap.getStaticClaimColonies(), cap.getAllClaimingBuildings());
    }
}
