package com.minecolonies.api.colony.claim;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.Log;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import com.minecolonies.api.util.INBTSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.minecolonies.api.util.constant.ColonyManagerConstants.NO_COLONY_ID;
import static com.minecolonies.api.util.constant.NbtTagConstants.*;

/**
 * The implementation of the colonyTagCapability.
 */
public class ChunkClaimData implements IChunkClaimData, INBTSerializable<CompoundTag>
{
    /**
     * How many columns a chunk has, and so how many bits a claim mask needs.
     */
    private static final int COLUMNS_PER_CHUNK = 16 * 16;

    /**
     * How many longs those bits take.
     */
    private static final int MASK_WORDS = COLUMNS_PER_CHUNK / Long.SIZE;

    /**
     * The set of all close colonies. Only relevant in non dynamic claiming.
     */
    private Set<Integer> colonies = new HashSet<>();

    /**
     * The colony owning the chunk. NO_COLONY_ID If none.
     */
    private int owningColony = NO_COLONY_ID;

    /**
     * List of buildings claiming this chunk for a certain colony.
     */
    private final Map<Integer, Set<BlockPos>> claimingBuildings;

    /**
     * Which of the chunk's 256 columns belong to the owning colony, or null while all of them do.
     * <p>
     * Null is the normal state and the one every chunk starts in, so a claim costs nothing extra until someone edits
     * its shape. Bit {@code (z & 15) * 16 + (x & 15)} of the four longs is column {@code (x, z)}.
     */
    private long[] claimedColumns = null;

    public ChunkClaimData()
    {
        this(new HashSet<>(), NO_COLONY_ID, new HashMap<>());
    }

    private ChunkClaimData(final Set<Integer> colonies, final int owningColony, final Map<Integer, Set<BlockPos>> claimingBuildings)
    {
        this.colonies = colonies;
        this.owningColony = owningColony;
        this.claimingBuildings = claimingBuildings;
    }

    @Override
    public void addColony(final int id, final LevelChunk chunk)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByDimension(id, chunk.getLevel().dimension());
        if (colony == null)
        {
            return;
        }

        colonies.add(id);
        if (owningColony == NO_COLONY_ID || IColonyManager.getInstance().getColonyByDimension(owningColony, chunk.getLevel().dimension()) == null)
        {
            colony.addLoadedChunk(chunk.getPos().pack(), chunk);
            owningColony = id;
        }
        chunk.markUnsaved();
    }

    @Override
    public void removeColony(final int id, final LevelChunk chunk)
    {
        colonies.remove(id);
        claimingBuildings.remove(id);
        if (owningColony == id)
        {
            if (!claimingBuildings.isEmpty())
            {
                owningColony = claimingBuildings.keySet().iterator().next();
            }
            else if (!colonies.isEmpty())
            {
                owningColony = colonies.iterator().next();
            }
            else
            {
                owningColony = NO_COLONY_ID;
            }
        }

        chunk.markUnsaved();
    }

    @Override
    public void setStaticColonyClaim(final List<Integer> colonies)
    {
        this.colonies = new HashSet<>(colonies);
    }

    @Override
    public void reset(final LevelChunk chunk)
    {
        colonies.clear();
        owningColony = NO_COLONY_ID;
        claimingBuildings.clear();
        claimedColumns = null;
        chunk.markUnsaved();
    }

    @Override
    public boolean hasPartialClaim()
    {
        return claimedColumns != null;
    }

    @Override
    public boolean isColumnClaimed(final BlockPos pos)
    {
        if (claimedColumns == null)
        {
            return true;
        }

        final int bit = columnBit(pos);
        return (claimedColumns[bit >> 6] & (1L << (bit & 63))) != 0L;
    }

    @Override
    public void setColumnClaimed(final BlockPos pos, final boolean claimed, final LevelChunk chunk)
    {
        if (claimedColumns == null)
        {
            if (claimed)
            {
                // Nothing to do: with no mask the whole chunk is already claimed, and creating a full one here would
                // only cost the extra bytes in save and in every colony view packet.
                return;
            }

            // Start from the whole chunk, so cutting one column out of an ordinary claim leaves the other 255.
            claimedColumns = new long[] {-1L, -1L, -1L, -1L};
        }

        final int bit = columnBit(pos);
        if (claimed)
        {
            claimedColumns[bit >> 6] |= 1L << (bit & 63);

            if (getClaimedColumnCount() == COLUMNS_PER_CHUNK)
            {
                // Filled back in. Drop the mask rather than keep a full one around, so a chunk that was carved and
                // then restored is indistinguishable from one that was never touched.
                claimedColumns = null;
            }
        }
        else
        {
            claimedColumns[bit >> 6] &= ~(1L << (bit & 63));
        }

        chunk.markUnsaved();
    }

    @Override
    public void clearPartialClaim(final LevelChunk chunk)
    {
        claimedColumns = null;
        chunk.markUnsaved();
    }

    @Override
    public void clearAllColumns(final LevelChunk chunk)
    {
        claimedColumns = new long[MASK_WORDS];
        chunk.markUnsaved();
    }

    @Override
    public int getClaimedColumnCount()
    {
        if (claimedColumns == null)
        {
            return COLUMNS_PER_CHUNK;
        }

        int count = 0;
        for (final long word : claimedColumns)
        {
            count += Long.bitCount(word);
        }
        return count;
    }

    /**
     * The bit in {@link #claimedColumns} standing for the column a position is in.
     *
     * @param pos the position, of which only the horizontal coordinates matter.
     * @return the bit index, 0 to 255.
     */
    private static int columnBit(final BlockPos pos)
    {
        return ((pos.getZ() & 15) << 4) | (pos.getX() & 15);
    }

    @Override
    public void addBuildingClaim(final int colonyId, final BlockPos pos, final LevelChunk chunk)
    {
        if (chunk.getPos().equals(ChunkPos.ZERO))
        {
            final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, chunk.getLevel().dimension());
            if (colony == null || BlockPosUtil.getDistance2D(colony.getCenter(), BlockPos.ZERO) > 200)
            {
                Log.getLogger().warn("Claiming id:" + colonyId + " building at zero pos!" + pos, new Exception());
            }
        }

        if (owningColony == NO_COLONY_ID)
        {
            setOwningColony(colonyId, chunk);
        }

        if (claimingBuildings.containsKey(colonyId))
        {
            claimingBuildings.get(colonyId).add(pos);
        }
        else
        {
            final Set<BlockPos> newList = new HashSet<>();
            newList.add(pos);
            claimingBuildings.put(colonyId, newList);
        }

        final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyId, chunk.getLevel().dimension());
        if (colony != null)
        {
            colony.addLoadedChunk(chunk.getPos().pack(), chunk);
        }

        chunk.markUnsaved();
    }

    @Override
    public void removeBuildingClaim(final int colonyId, final BlockPos pos, final LevelChunk chunk)
    {
        if (!claimingBuildings.containsKey(colonyId))
        {
            return;
        }

        chunk.markUnsaved();
        final Set<BlockPos> buildings = claimingBuildings.get(colonyId);
        buildings.remove(pos);

        if (buildings.isEmpty())
        {
            claimingBuildings.remove(colonyId);

            if (owningColony == colonyId && !colonies.contains(owningColony))
            {
                if (claimingBuildings.isEmpty())
                {
                    if (colonies.isEmpty())
                    {
                        owningColony = NO_COLONY_ID;
                    }
                    else
                    {
                        owningColony = colonies.iterator().next();
                    }
                }
                else
                {
                    for (final Iterator<Map.Entry<Integer, Set<BlockPos>>> colonyIt = claimingBuildings.entrySet().iterator(); colonyIt.hasNext(); )
                    {
                        final Map.Entry<Integer, Set<BlockPos>> colonyEntry = colonyIt.next();
                        final IColony colony = IColonyManager.getInstance().getColonyByDimension(colonyEntry.getKey(), chunk.getLevel().dimension());
                        if (colony == null)
                        {
                            continue;
                        }

                        for (final Iterator<BlockPos> buildingIt = colonyEntry.getValue().iterator(); buildingIt.hasNext(); )
                        {
                            final BlockPos buildingPos = buildingIt.next();
                            if (colony.getCommonBuildingManager().getBuilding(buildingPos) != null)
                            {
                                colony.addLoadedChunk(chunk.getPos().pack(), chunk);
                                setOwningColony(colonyEntry.getKey(), chunk);
                                return;
                            }
                            else
                            {
                                buildingIt.remove();
                            }
                        }

                        if (colonyEntry.getValue().isEmpty())
                        {
                            colonyIt.remove();
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setOwningColony(final int id, final LevelChunk chunk)
    {
        this.owningColony = id;
        chunk.markUnsaved();
    }

    @Override
    public int getOwningColony()
    {
        return owningColony;
    }

    @NotNull
    @Override
    public List<Integer> getStaticClaimColonies()
    {
        return new ArrayList<>(colonies);
    }

    @NotNull
    @Override
    public Map<Integer, Set<BlockPos>> getAllClaimingBuildings()
    {
        return claimingBuildings;
    }

    @Override
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag compound = new CompoundTag();
        compound.putInt(TAG_ID, owningColony);

        final ListTag colonyClaimTag = new ListTag();
        for (final int colonyId : colonies)
        {
            colonyClaimTag.add(IntTag.valueOf(colonyId));
        }
        compound.put(TAG_COLONIES, colonyClaimTag);

        final ListTag buildingsClaimTag = new ListTag();
        for (final Map.Entry<Integer, Set<BlockPos>> entry : claimingBuildings.entrySet())
        {
            final CompoundTag perColonyEntry = new CompoundTag();
            perColonyEntry.putInt(TAG_ID, entry.getKey());

            final ListTag buildingListTag = new ListTag();
            for (final BlockPos pos : entry.getValue())
            {
                BlockPosUtil.writeToListNBT(buildingListTag, pos);
            }
            perColonyEntry.put(TAG_BUILDING_CLAIM, buildingListTag);
            buildingsClaimTag.add(perColonyEntry);
        }
        compound.put(TAG_BUILDING_LIST_CLAIM, buildingsClaimTag);

        // Written only when the claim is not the whole chunk, so ordinary claims keep the tag they always had.
        if (claimedColumns != null)
        {
            compound.putLongArray(TAG_PARTIAL_CLAIM, claimedColumns.clone());
        }

        return compound;
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        // Set owning
        owningColony = compound.getIntOr(TAG_ID, 0);

        final ListTag colonyClaim = compound.getListOrEmpty(TAG_COLONIES);
        for (int i = 0; i < colonyClaim.size(); i++)
        {
            colonies.add(colonyClaim.getIntOr(i, 0));
        }

        final ListTag buildingClaim = compound.getListOrEmpty(TAG_BUILDING_LIST_CLAIM);
        for (int i = 0; i < buildingClaim.size(); i++)
        {
            final CompoundTag perColonyCompound = buildingClaim.getCompoundOrEmpty(i);
            final int id = perColonyCompound.getIntOr(TAG_ID, 0);
            final Set<BlockPos> buildings = claimingBuildings.computeIfAbsent(id, HashSet::new);

            final ListTag buildingList = perColonyCompound.getListOrEmpty(TAG_BUILDING_CLAIM);
            for (int j = 0; j < buildingList.size(); j++)
            {
                buildings.add(BlockPosUtil.readFromListNBT(buildingList, j));
            }
        }

        if (owningColony == NO_COLONY_ID && !getStaticClaimColonies().isEmpty())
        {
            owningColony = getStaticClaimColonies().get(0);
        }

        // Absent on every chunk saved before the border scepter existed, and on every chunk claimed whole since.
        claimedColumns = compound.getLongArray(TAG_PARTIAL_CLAIM).filter(mask -> mask.length == MASK_WORDS).orElse(null);
    }

    /**
     * Serialize data to buffer
     * @param buf
     */
    public void serialize(final RegistryFriendlyByteBuf buf)
    {
        buf.writeInt(owningColony);
        buf.writeInt(colonies.size());
        for (final int colonyId : colonies)
        {
            buf.writeInt(colonyId);
        }

        buf.writeInt(claimingBuildings.size());
        for (final Map.Entry<Integer, Set<BlockPos>> entry : claimingBuildings.entrySet())
        {
            buf.writeInt(entry.getKey());
            buf.writeInt(entry.getValue().size());
            for (final BlockPos pos : entry.getValue())
            {
                buf.writeLong(pos.asLong());
            }
        }

        buf.writeBoolean(claimedColumns != null);
        if (claimedColumns != null)
        {
            for (final long word : claimedColumns)
            {
                buf.writeLong(word);
            }
        }
    }

    /**
     * Deserialize claims from buffer
     * @param buf
     */
    public void deSerialize(final RegistryFriendlyByteBuf buf)
    {
        owningColony = buf.readInt();

        final int colonyAmount = buf.readInt();
        this.colonies = new HashSet<>();
        for (int i = 0; i < colonyAmount; i++)
        {
            colonies.add(buf.readInt());
        }

        final int buildingClaimAmount = buf.readInt();
        for (int i = 0; i < buildingClaimAmount; i++)
        {
            final int colonyID = buf.readInt();
            final int positionAmount = buf.readInt();
            Set<BlockPos> positions = new HashSet<>();
            for (int j = 0; j < positionAmount; j++)
            {
                positions.add(BlockPos.of(buf.readLong()));
            }

            claimingBuildings.put(colonyID, positions);
        }

        if (buf.readBoolean())
        {
            claimedColumns = new long[MASK_WORDS];
            for (int i = 0; i < MASK_WORDS; i++)
            {
                claimedColumns[i] = buf.readLong();
            }
        }
        else
        {
            claimedColumns = null;
        }
    }
}