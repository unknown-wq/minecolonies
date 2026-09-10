package com.ldtteam.common.fakelevel;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
// 26.3: Continuation is no longer nested in AbortableIterationConsumer, it is a top level enum
// (/opt/mc-src-26.3/net/minecraft/util/Continuation.java). Same CONTINUE/ABORT constants.
import net.minecraft.util.Continuation;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData.RespawnData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * As much as general fake level. Features:
 * <ul>
 * <li>static access to given data</li>
 * <li>immutability - disables all external changes (but levelSource can be mutable)</li>
 * <li>most of dimension related things is delegated to current client level (class instances can travel accross dimensions)</li>
 * <li>biome info is also delegated from client level</li>
 * <li>light control - manual or delegated from client level</li>
 * <li>primitive chunk and entity management</li>
 * <li>basic heightmap support (not fully working yet)</li>
 * <li><b>Few unsafe NPEs methods :)</b></li>
 * </ul>
 * <p>Porting info:
 * <ol>
 * <li>uncomment last method section</li>
 * <li>fix compile errors</li>
 * <li>add override for remaining methods and sort/implement them accordingly</li>
 * <li>comment last method section</li>
 * </ol>
 * <p>
 */
public class FakeLevel<SOURCE extends IFakeLevelBlockGetter> extends Level
{
    protected SOURCE levelSource;
    protected final IFakeLevelLightProvider lightProvider;
    protected Level realLevel;
    protected final Scoreboard scoreboard;
    protected final boolean overrideBeLevel;

    protected final FakeChunkSource chunkSource;
    protected final FakeLevelLightEngine lightEngine;
    protected FakeLevelEntityGetterAdapter levelEntityGetter = FakeLevelEntityGetterAdapter.EMPTY;
    protected List<EnderDragonPart> dragonParts = List.of();
    // TODO: this is currently manually filled by class user - ideally if not filled yet this should get constructed from levelSource
    // manually
    protected Map<BlockPos, BlockEntity> blockEntities = Collections.emptyMap();

    /**
     * Current rendering worldPos so we can use client level real info
     */
    protected BlockPos worldPos = BlockPos.ZERO;

    // chunk cache
    int lastX, lastZ;
    ChunkAccess lastChunk = null;

    /**
     * @param levelSource     data source, also try to set block entities/entities collections
     * @param lightProvider   light source
     * @param realLevel       real vanilla instance of any Level, in which will this fake level live
     * @param scoreboard      if null client level is used instead
     * @param overrideBeLevel if true all block entities will have set level to this instance
     * @see #setBlockEntities(Map) for better block entity handling, if set then levelSource BE getter is not used
     * @see #setEntities(Collection) only way to add entities into fake level
     * @see #setRealLevel(Level) if you want to reuse this instance
     */
    public FakeLevel(final SOURCE levelSource,
        final IFakeLevelLightProvider lightProvider,
        final Level realLevel,
        @Nullable final Scoreboard scoreboard,
        final boolean overrideBeLevel)
    {
        // we have to pass null in ctor, so realLevel can be used
        super(new FakeLevelData(realLevel::getLevelData, lightProvider),
            realLevel.dimension(),
            realLevel.registryAccess(),
            realLevel.dimensionTypeRegistration(),
            realLevel.isClientSide(),
            false,
            0,
            0);
        this.levelSource = levelSource;
        this.lightProvider = lightProvider;
        this.realLevel = realLevel;
        this.scoreboard = scoreboard;
        this.overrideBeLevel = overrideBeLevel;
        this.chunkSource = new FakeChunkSource(this);
        this.lightEngine = new FakeLevelLightEngine(this);

        setRealLevel(realLevel); // intentionally due to init
        ((FakeLevelData) getLevelData()).vanillaLevelData = () -> realLevel().getLevelData();
    }

    // ========================================
    // ========== FAKE LEVEL METHODS ==========
    // ========================================

    public void setRealLevel(final Level realLevel)
    {
        if (Objects.equals(this.realLevel, realLevel))
        {
            return;
        }

        if (realLevel != null && realLevel.isClientSide() != this.isClientSide())
        {
            throw new IllegalArgumentException("Received wrong sided realLevel - fakeLevel.isClientSide = " + this.isClientSide());
        }

        this.realLevel = realLevel;
    }

    public Level realLevel()
    {
        return realLevel;
    }

    /**
     * @param levelSource new data source
     */
    public void setLevelSource(final SOURCE levelSource)
    {
        this.levelSource = levelSource;
    }

    /**
     * @return current data source
     */
    public SOURCE getLevelSource()
    {
        return levelSource;
    }

    /**
     * @param worldPos where is fake level anchor when querying current client level data
     */
    public void setWorldPos(final BlockPos worldPos)
    {
        this.worldPos = worldPos;
    }

    /**
     * @return anchor in vanilla client level
     */
    public BlockPos getWorldPos()
    {
        return worldPos;
    }

    /**
     * For better block entity handling in chunk methods. If set then {@link IFakeLevelBlockGetter#getBlockEntity(BlockPos)
     * levelSource.getBlockEntity(BlockPos)} is not used. Reset with empty collection
     *
     * @param blockEntities all block entities, should be data equivalent to levelSource
     */
    public void setBlockEntities(final Map<BlockPos, BlockEntity> blockEntities)
    {
        this.blockEntities = blockEntities;
    }

    /**
     * @param entities all entities, their level should be this fake level instance. Reset with empty collection
     */
    public void setEntities(final Collection<? extends Entity> entities)
    {
        levelEntityGetter = entities.isEmpty() ? FakeLevelEntityGetterAdapter.EMPTY : FakeLevelEntityGetterAdapter.ofEntities(entities);
        // 26.2: NeoForge's generic multipart API (PartEntity, Entity#isMultipartEntity, Entity#getParts) is
        // gone; vanilla Level#dragonParts() is typed to EnderDragonPart and the parts come off the dragon
        // (/opt/mc-src/net/minecraft/world/level/Level.java:876,
        // /opt/mc-src/net/minecraft/world/entity/boss/enderdragon/EnderDragon.java:739).
        dragonParts = entities.stream()
            .filter(EnderDragon.class::isInstance)
            .map(EnderDragon.class::cast)
            .map(EnderDragon::getSubEntities)
            .flatMap(Arrays::stream)
            .toList();
    }

    // ========================================
    // ======= CTOR REAL LEVEL REDIRECTS ======
    // ========================================
    // Note: must have null check because super ctor

    @Override
    public ResourceKey<Level> dimension()
    {
        return realLevel() != null ? realLevel().dimension() : super.dimension();
    }

    @Override
    public RegistryAccess registryAccess()
    {
        return realLevel() != null ? realLevel().registryAccess() : super.registryAccess();
    }

    @Override
    public DamageSources damageSources()
    {
        return realLevel() != null ? realLevel().damageSources() : super.damageSources();
    }

    @Override
    public DimensionType dimensionType()
    {
        return realLevel() != null ? realLevel().dimensionType() : super.dimensionType();
    }

    @Override
    public Holder<DimensionType> dimensionTypeRegistration()
    {
        return realLevel() != null ? realLevel().dimensionTypeRegistration() : super.dimensionTypeRegistration();
    }

    @Override
    public WorldBorder getWorldBorder()
    {
        return realLevel() != null ? realLevel().getWorldBorder() : new WorldBorder();
    }

    // ========================================
    // ========== REDIRECTED METHODS ==========
    // ========================================

    @Nullable
    @Override
    public BlockEntity getBlockEntity(final BlockPos pos)
    {
        final BlockEntity blockEntity = blockEntities.isEmpty() ? levelSource.getBlockEntity(pos) : blockEntities.get(pos);
        if (blockEntity != null && blockEntity.getLevel() != this && (overrideBeLevel || !blockEntity.hasLevel()))
        {
            blockEntity.setLevel(this);
        }
        return blockEntity;
    }

    @Override
    public BlockState getBlockState(final BlockPos pos)
    {
        return levelSource.isPosInside(pos) ? levelSource.getBlockState(pos) : Blocks.AIR.defaultBlockState();
    }

    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus requiredStatus, boolean loadOrGenerate)
    {
        // loadOrGenerate effectively means non-null return value
        if (lastX == x && lastZ == z && lastChunk != null)
        {
            return lastChunk;
        }
        return loadOrGenerate || hasChunk(x, z) ? FakeChunk.create(this, x, z) : null;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ)
    {
        final int posX = SectionPos.sectionToBlockCoord(chunkX);
        final int posZ = SectionPos.sectionToBlockCoord(chunkZ);
        return levelSource.getMinX() <= posX && posX <= levelSource.getMaxX() &&
            levelSource.getMinZ() <= posZ && posZ <= levelSource.getMaxZ();
    }

    @Override
    public int getBrightness(final LightLayer lightType, final BlockPos pos)
    {
        return lightProvider.forceOwnLightLevel() ? lightProvider.getBrightness(lightType, pos) :
            realLevel().getBrightness(lightType, worldPos.offset(pos));
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount)
    {
        return lightProvider.forceOwnLightLevel() ? lightProvider.getRawBrightness(pos, amount) :
            realLevel().getRawBrightness(worldPos.offset(pos), amount);
    }

    @Override
    public int getSkyDarken()
    {
        return lightProvider.forceOwnLightLevel() ? lightProvider.getSkyDarken() : realLevel().getSkyDarken();
    }

    @Override
    public boolean isBrightOutside()
    {
        return !this.dimensionType().hasFixedTime() && this.getSkyDarken() < 4;
    }

    @Override
    public Scoreboard getScoreboard()
    {
        return scoreboard == null ? realLevel().getScoreboard() : scoreboard;
    }

    @Override
    public FluidState getFluidState(final BlockPos pos)
    {
        return levelSource.getFluidState(pos);
    }

    @Override
    public int getHeight()
    {
        return levelSource.getHeight();
    }

    @Override
    public int getMinY()
    {
        return levelSource.getMinY();
    }

    @Override
    public boolean isInWorldBounds(final BlockPos pos)
    {
        return levelSource.isPosInside(pos);
    }

    @Override
    public CrashReportCategory fillReportDetails(CrashReport report)
    {
        CrashReportCategory crashreportcategory = report.addCategory("BlockUI fake level");
        levelSource.describeSelfInCrashReport(crashreportcategory);
        return crashreportcategory;
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities()
    {
        return levelEntityGetter;
    }

    @Override
    @Nullable
    public Entity getEntity(int id)
    {
        return levelEntityGetter.get(id);
    }

    @Override
    public List<Player> players()
    {
        final List<Player> result = new ArrayList<>();
        levelEntityGetter.get(EntityTypeTest.forClass(Player.class), player -> {
            result.add(player);
            return Continuation.CONTINUE;
        });
        return result;
    }

    @Override
    public int getHeight(Types heightmapType, int x, int z)
    {
        final MutableBlockPos pos = new MutableBlockPos(x, levelSource.getMinY(), z);

        if (levelSource.isPosInside(pos))
        {
            for (int y = levelSource.getMaxY(); y >= levelSource.getMinY(); y--)
            {
                pos.setY(y);
                if (heightmapType.isOpaque().test(levelSource.getBlockState(pos)))
                {
                    return y;
                }
            }
        }

        return levelSource.getMinY();
    }

    @Override
    public ChunkSource getChunkSource()
    {
        return chunkSource;
    }

    // TODO(port-26.2): DISABLED - NeoForge's ModelData/ModelDataManager have no Fabric counterpart (contract
    // K5). The 26.2 equivalent is FabricBlockGetter#getBlockEntityRenderData(BlockPos), whose default
    // implementation already resolves this level's block entity via getBlockEntity(pos), so nothing has to be
    // overridden here.
    /*
    @Override
    public ModelData getModelData(BlockPos pos)
    {
        return modelDataManager.getAt(pos);
    }

    @Override
    @Nullable
    public ModelDataManager getModelDataManager()
    {
        return modelDataManager;
    }
    */

    @Override
    public LevelLightEngine getLightEngine()
    {
        return lightEngine;
    }

    @Override
    public RespawnData getRespawnData()
    {
        return getLevelData().getRespawnData();
    }

    @Override
    public Collection<EnderDragonPart> dragonParts()
    {
        return dragonParts;
    }

    @Override
    public String gatherChunkSourceStats()
    {
        return "Fake level for: " + levelSource;
    }

    @Override
    public Holder<Biome> getBiome(BlockPos pos)
    {
        return realLevel().getBiome(worldPos.offset(pos));
    }

    @Override
    public BiomeManager getBiomeManager()
    {
        return realLevel().getBiomeManager();
    }

    @Override
    public RecipeAccess recipeAccess()
    {
        return realLevel().recipeAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures()
    {
        return realLevel().enabledFeatures();
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z)
    {
        return realLevel().getUncachedNoiseBiome(x, y, z);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z)
    {
        return realLevel().getNoiseBiome(x, y, z);
    }

    @Override
    public TickRateManager tickRateManager()
    {
        return realLevel().tickRateManager();
    }

    // TODO(port-26.3): DEGRADED — PotionBrewing and Level#potionBrewing() were deleted in 26.3
    // (brewing is a recipe type now; API-CHECKLIST-26.3.md A2). FakeLevel is a facade over a real
    // Level, so there is nothing left to forward: the override is simply gone and callers reach
    // the recipe manager through recipeAccess() like vanilla does.

    @Override
    public ClockManager clockManager()
    {
        return realLevel().clockManager();
    }

    @Override
    public PalettedContainerFactory palettedContainerFactory()
    {
        return realLevel().palettedContainerFactory();
    }

    @Override
    public int getClientLeafTintColor(BlockPos pos)
    {
        return realLevel().getClientLeafTintColor(pos);
    }

    @Override
    public EnvironmentAttributeSystem environmentAttributes()
    {
        return realLevel().environmentAttributes();
    }

    // TODO(port-26.3): DEGRADED — FuelValues and Level#fuelValues() were deleted in 26.3
    // (API-CHECKLIST-26.3.md B12). Same reasoning as potionBrewing(): nothing left to forward.

    // ========================================
    // ======= NOOP UNSAFE NULL METHODS =======
    // ========================================

    @Override
    public void explode(
        final @Nullable Entity source,
        final @Nullable DamageSource damageSource,
        final @Nullable ExplosionDamageCalculator damageCalculator,
        final double x,
        final double y,
        final double z,
        final float r,
        final boolean fire,
        final Level.ExplosionInteraction interactionType,
        final ParticleOptions smallExplosionParticles,
        final ParticleOptions largeExplosionParticles,
        final WeightedList<ExplosionParticleInfo> blockParticles,
        final Holder<SoundEvent> explosionSound)
    {
        // Noop throw new UnsupportedOperationException("Structurize fake immutable level - no explosions possible!");
    }

    // ========================================
    // ========== PERMANENT SETTINGS ==========
    // ========================================

    @Override
    public boolean isLoaded(BlockPos pos)
    {
        // Noop
        return true;
    }

    @Override
    public float getRainLevel(float delta)
    {
        // Noop
        return 0;
    }

    @Override
    public float getThunderLevel(float delta)
    {
        // Noop
        return 0;
    }

    @Override
    public boolean isRainingAt(BlockPos position)
    {
        return isRaining();
    }

    @Override
    public boolean noSave()
    {
        // Noop
        return true;
    }

    @Override
    public int getSeaLevel()
    {
        return 0;
    }

    // ========================================
    // ============ NOOP OVERRIDES ============
    // ========================================

    @Override
    public void destroyBlockProgress(int p_46506_, BlockPos p_46507_, int p_46508_)
    {
        // Noop
    }

    @Override
    public MapItemSavedData getMapData(MapId p_324234_)
    {
        // Noop - null safe
        return null;
    }

    @Override
    public void playSeededSound(final @Nullable Entity except,
        final Entity sourceEntity,
        final Holder<SoundEvent> sound,
        final SoundSource source,
        final float volume,
        final float pitch,
        final long seed)
    {
        // Noop
    }

    @Override
    public void playSeededSound(final @Nullable Entity except,
        final double x,
        final double y,
        final double z,
        final Holder<SoundEvent> sound,
        final SoundSource source,
        final float volume,
        final float pitch,
        final long seed)
    {
        // Noop
    }

    @Override
    public void sendBlockUpdated(BlockPos p_46612_, BlockState p_46613_, BlockState p_46614_, int p_46615_)
    {
        // Noop
    }

    @Override
    public void gameEvent(Holder<GameEvent> p_316267_, Vec3 p_220405_, Context p_220406_)
    {
        // Noop
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks()
    {
        // Noop
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks()
    {
        // Noop
        return BlackholeTickAccess.emptyLevelList();
    }

    @Override
    public void levelEvent(@Nullable Entity p_46771_, int p_46772_, BlockPos p_46773_, int p_46774_)
    {
        // Noop
    }

    // ========================================
    // ============= NOOP METHODS =============
    // ========================================

    @Override
    public void addBlockEntityTicker(TickingBlockEntity p_151526_)
    {
        // Noop
    }

    // TODO(port-26.2): DISABLED - Level#addFreshBlockEntities was NeoForge-only, no vanilla 26.2 counterpart
    /*
    @Override
    public void addFreshBlockEntities(Collection<BlockEntity> beList)
    {
        // Noop
    }
    */

    @Override
    public void blockEvent(BlockPos p_46582_, Block p_46583_, int p_46584_, int p_46585_)
    {
        // Noop
    }

    @Override
    public void close() throws IOException
    {
        // Noop
    }

    @Override
    public boolean destroyBlock(BlockPos p_46626_, boolean p_46627_, @Nullable Entity p_46628_, int p_46629_)
    {
        // Noop
        return false;
    }

    // TODO(port-26.2): DISABLED - Level#markAndNotifyBlock was NeoForge-only, no vanilla 26.2 counterpart
    /*
    @Override
    public void markAndNotifyBlock(BlockPos p_46605_,
        @Nullable LevelChunk levelchunk,
        BlockState blockstate,
        BlockState p_46606_,
        int p_46607_,
        int p_46608_)
    {
        // Noop
    }
    */

    @Override
    public boolean mayInteract(Entity p_46557_, BlockPos p_46558_)
    {
        // Noop
        return false;
    }

    @Override
    public void neighborShapeChanged(Direction p_220385_,
        BlockPos p_220387_,
        BlockPos p_220388_,
        BlockState p_220386_,
        int p_220389_,
        int p_220390_)
    {
        // Noop
    }

    @Override
    public boolean removeBlock(BlockPos p_46623_, boolean p_46624_)
    {
        return false;
    }

    @Override
    public boolean setBlock(BlockPos p_46605_, BlockState p_46606_, int p_46607_, int p_46608_)
    {
        // Noop
        return false;
    }

    @Override
    public void setRainLevel(float p_46735_)
    {
        // Noop
    }

    @Override
    public void setSpawnSettings(boolean p_46704_)
    {
        // Noop
    }

    @Override
    public void setThunderLevel(float p_46708_)
    {
        // Noop
    }

    @Override
    public boolean shouldTickBlocksAt(long p_186456_)
    {
        // Noop
        return false;
    }

    @Override
    public boolean shouldTickDeath(Entity p_186458_)
    {
        // Noop
        return false;
    }

    @Override
    public void tickBlockEntities()
    {
        // Noop
    }

    @Override
    public void updateNeighborsAt(BlockPos p_46673_, Block p_46674_)
    {
        // Noop
    }

    @Override
    public void updateSkyBrightness()
    {
        // Noop
    }

    // TODO(port-26.2): DISABLED - capabilities are NeoForge-only and have no Fabric replacement (bundle part IV §5)
    /*
    @Override
    public void invalidateCapabilities(BlockPos pos)
    {
        // Noop
    }
    */

    // TODO(port-26.2): DISABLED - capabilities are NeoForge-only and have no Fabric replacement (bundle part IV §5)
    /*
    @Override
    public void invalidateCapabilities(ChunkPos pos)
    {
        // Noop
    }
    */

    @Override
    public void updateNeighborsAt(BlockPos pos, Block sourceBlock, @Nullable Orientation orientation)
    {
        // Noop
    }

    @Override
    public void setRespawnData(RespawnData respawnData)
    {
        // Noop
    }
}
