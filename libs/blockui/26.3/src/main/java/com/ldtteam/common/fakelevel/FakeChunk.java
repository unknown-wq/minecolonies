package com.ldtteam.common.fakelevel;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.shorts.ShortList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.TickContainerAccess;
import org.jetbrains.annotations.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fake level fake chunk :D all data related methods must redirect to fake level Updating procedure is same as fakeLevel Porting info:
 * <ol>
 * <li>uncomment last method section</li>
 * <li>fix compile errors</li>
 * <li>add override for remaining methods and sort/implement them accordingly</li>
 * <li>comment last method section</li>
 * </ol>
 * <p>
 */
public class FakeChunk extends LevelChunk
{
    private final FakeLevel<?> fakeLevel;

    public static FakeChunk create(final FakeLevel<?> fakeLevel, final int x, final int z)
    {
        // 26.1 porting notes - we need to create this ourselves or we will get vanilla sections

        final ChunkPos chunkPos = new ChunkPos(x, z);
        final LevelChunkSection[] sections = new LevelChunkSection[fakeLevel.getSectionsCount()];
        for (int i = 0; i < sections.length; i++)
        {
            sections[i] = new FakeLevelChunkSection(fakeLevel, i, chunkPos);
        }

        final FakeChunk chunk = new FakeChunk(fakeLevel, new ChunkPos(x, z), sections);

        // set itself to cache
        fakeLevel.lastX = x;
        fakeLevel.lastZ = z;
        fakeLevel.lastChunk = chunk;

        return chunk;
    }

    private FakeChunk(final FakeLevel<?> fakeLevel, final ChunkPos pos, final LevelChunkSection[] sections)
    {
        super(fakeLevel, pos, UpgradeData.EMPTY, new LevelChunkTicks<>(), new LevelChunkTicks<>(), 0L, sections, null, null);
        this.fakeLevel = fakeLevel;
    }

    // ========================================
    // ========== REDIRECTED METHODS ==========
    // ========================================

    @Override
    public BlockState getBlockState(final BlockPos pos)
    {
        return fakeLevel.getBlockState(pos);
    }

    @Nullable
    @Override
    public BlockEntity getBlockEntity(final BlockPos pos, final EntityCreationType creationMode)
    {
        return fakeLevel.getBlockEntity(pos);
    }

    @Override
    public FluidState getFluidState(final BlockPos pos)
    {
        return fakeLevel.getFluidState(pos);
    }

    @Override
    public FluidState getFluidState(final int bx, final int by, final int bz)
    {
        return getFluidState(new BlockPos(bx, by, bz));
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z)
    {
        return fakeLevel.getNoiseBiome(x, y, z);
    }

    @Override
    public Map<BlockPos, BlockEntity> getBlockEntities()
    {
        // TODO: this should ideally return only BEs in this chunk
        return fakeLevel.blockEntities;
    }

    @Override
    public Set<BlockPos> getBlockEntitiesPos()
    {
        return getBlockEntities().keySet();
    }

    // TODO(port-26.2): DISABLED - NeoForge's ModelData has no Fabric counterpart (contract K5). The 26.2
    // equivalent, FabricBlockGetter#getBlockEntityRenderData(BlockPos), is inherited from BlockGetter and
    // resolves the block entity through this chunk's getBlockEntity(pos), so no override is needed.
    /*
    @Override
    public ModelData getModelData(BlockPos pos)
    {
        return fakeLevel.getModelData(pos);
    }
    */

    // ========================================
    // ======= NOOP UNSAFE NULL METHODS =======
    // ========================================

    // ========================================
    // ========== PERMANENT SETTINGS ==========
    // ========================================

    @Override
    public FullChunkStatus getFullStatus()
    {
        return FullChunkStatus.FULL;
    }

    @Override
    public ChunkStatus getPersistedStatus()
    {
        return ChunkStatus.FULL;
    }

    @Override
    public boolean isUnsaved()
    {
        return false;
    }

    @Override
    public boolean isUpgrading()
    {
        return false;
    }

    @Override
    public boolean isLightCorrect()
    {
        return true;
    }

    @Override
    public boolean canBeSerialized()
    {
        return false;
    }

    // ========================================
    // ========== HEIGHTMAP RELATED ===========
    // ========================================

    @Override
    public int getHeight(Types type, int x, int z)
    {
        return fakeLevel.getHeight(type, chunkPos.getBlockX(x), chunkPos.getBlockZ(z));
    }

    @Override
    public Collection<Entry<Types, Heightmap>> getHeightmaps()
    {
        // TODO: investigate..
        return Collections.emptyList();
    }

    @Override
    public Heightmap getOrCreateHeightmapUnprimed(Types p_62079_)
    {
        return null;
    }

    @Override
    public boolean hasPrimedHeightmap(Types p_187659_)
    {
        return false;
    }

    // ========================================
    // ============= NOOP METHODS =============
    // ========================================

    @Override
    public void addAndRegisterBlockEntity(BlockEntity p_156391_)
    {
        // Noop
    }

    @Override
    public TickContainerAccess<Block> getBlockTicks()
    {
        // Noop
        return BlackholeTickAccess.emptyContainer();
    }

    @Override
    public TickContainerAccess<Fluid> getFluidTicks()
    {
        // Noop
        return BlackholeTickAccess.emptyContainer();
    }

    @Override
    public void postProcessGeneration(ServerLevel level)
    {
        // Noop
    }

    @Override
    public void registerAllBlockEntitiesAfterLevelLoad()
    {
        // Noop
    }

    @Override
    public void removeBlockEntity(BlockPos p_62919_)
    {
        // Noop
    }

    @Override
    public void replaceBiomes(FriendlyByteBuf p_275574_)
    {
        // Noop
    }

    @Override
    public void replaceWithPacketData(int p_187971_, int p_187972_, ClientboundLevelChunkPacketData p_187973_)
    {
        // Noop
    }

    @Override
    public void setBlockEntity(BlockEntity p_156374_)
    {
        // Noop
    }

    @Override
    @Nullable
    public BlockState setBlockState(BlockPos p_62865_, BlockState p_62866_, @Block.UpdateFlags int p_62867_)
    {
        // Noop
        return null;
    }

    @Override
    public void setFullStatus(Supplier<FullChunkStatus> p_62880_)
    {
        // Noop
    }

    @Override
    public void unpackTicks(long p_187986_)
    {
        // Noop
    }

    @Override
    public void addReferenceForStructure(Structure p_223007_, long p_223008_)
    {
        // Noop
    }

    @Override
    // 26.3: ChunkAccess#fillBiomesFromNoise lost the Climate.Sampler parameter
    // (/opt/mc-src-26.3/net/minecraft/world/level/chunk/ChunkAccess.java:444).
    public void fillBiomesFromNoise(BiomeResolver p_187638_)
    {
        // Noop
    }

    @Override
    @Nullable
    public CompoundTag getBlockEntityNbt(BlockPos p_62103_)
    {
        // Noop, for pending BEs only
        return null;
    }

    @Override
    public void setAllReferences(Map<Structure, LongSet> p_187663_)
    {
        // Noop
    }

    @Override
    public void setAllStarts(Map<Structure, StructureStart> p_62090_)
    {
        // Noop
    }

    @Override
    public void setBlockEntityNbt(CompoundTag p_62091_)
    {
        // Noop
    }

    @Override
    public void setLightCorrect(boolean p_62100_)
    {
        // Noop
    }

    @Override
    public void setStartForStructure(Structure p_223010_, StructureStart p_223011_)
    {
        // Noop
    }

    @Override
    public void setHeightmap(Types p_62083_, long[] p_62084_)
    {
        // Noop
    }

    @Override
    public void markUnsaved()
    {
        // Noop
    }

    @Override
    public void setUnsavedListener(UnsavedListener unsavedListener)
    {
        // Noop
    }

    @Override
    public void addPackedPostProcess(ShortList packedOffsets, int sectionIndex)
    {
        // Noop
    }
}
