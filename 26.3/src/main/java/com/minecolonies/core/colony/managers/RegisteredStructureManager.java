package com.minecolonies.core.colony.managers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.IAnimalData;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildings.*;
import com.minecolonies.api.colony.buildings.registry.IBuildingDataManager;
import com.minecolonies.api.colony.buildings.workerbuildings.ITownHall;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.managers.interfaces.IRegisteredStructureManager;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingAddedModEvent;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingRemovedModEvent;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.*;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.BuildingMysticalSite;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.*;
import com.minecolonies.core.colony.buildingextensions.registry.BuildingExtensionDataManager;
import com.minecolonies.core.colony.rescue.KeepBuildings;
import com.minecolonies.core.event.QuestObjectiveEventHandler;
import com.minecolonies.core.network.messages.client.colony.ColonyViewBuildingViewMessage;
import com.minecolonies.core.network.messages.client.colony.ColonyViewBuildingExtensionsUpdateMessage;
import com.minecolonies.core.network.messages.client.colony.ColonyViewRemoveBuildingMessage;
import com.minecolonies.core.tileentities.TileEntityDecorationController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

import static com.minecolonies.api.util.MathUtils.RANDOM;
import static com.minecolonies.api.util.constant.NbtTagConstants.*;

public class RegisteredStructureManager implements IRegisteredStructureManager
{
    /**
     * Total order on positions, used only to break housing distance ties so two equally close houses are always
     * decided the same way instead of by whatever order the building map happens to iterate in.
     */
    private static final Comparator<BlockPos> BLOCK_POS_ORDER = BlockPosUtil.POSITION_ORDER;

    /**
     * List of building in the colony.
     */
    @NotNull
    private ImmutableMap<BlockPos, IBuilding> buildings = ImmutableMap.of();

    /**
     * How many consecutive colony-tick failures of one building pass between repeat log lines. At one slow tick per 500
     * game ticks that is roughly one line every hour and a half.
     */
    private static final int BUILDING_TICK_FAILURE_REPEAT = 240;

    /**
     * Buildings that need to be recalculated for prestige value.
     */
    private List<IBuilding> pendingPrestigeCalc = new ArrayList<>();

    /**
     * How often each building has thrown out of its colony tick. Empty on a healthy colony and never written to on the
     * happy path; it exists only to keep {@link #reportBuildingTickFailure} from writing a stack trace every 500 ticks
     * for a building that is permanently unhappy. Not persisted -- one server run is the right scope for it.
     */
    private final Map<BlockPos, Integer> buildingTickFailures = new HashMap<>();

    /**
     * Which buildings the sanity cleanup has already reported as spared, and how many it spared last pass. Only
     * ever written to while {@link KeepBuildings} is on, and only to keep {@link #reportSpared} from writing the
     * whole damage list every 500 game ticks. Not persisted -- one server run is the right scope for it.
     */
    private final Set<BlockPos> sparedReported = new HashSet<>();

    /**
     * How many buildings the last cleanup pass spared, or 0 if it spared none. See {@link #reportSpared}.
     */
    private int lastSparedCount = 0;

    /**
     * List of building extensions of the colony.
     */
    private final Map<IBuildingExtension.ExtensionId, IBuildingExtension> buildingExtensions = new HashMap<>();

    /**
     * The warehouse building position. Initially null.
     */
    private final List<IWareHouse> wareHouses = new ArrayList<>();

    /**
     * The warehouse building position. Initially null.
     */
    private final List<IMysticalSite> mysticalSites = new ArrayList<>();

    /**
     * List of leisure sites.
     */
    private ImmutableList<BlockPos> leisureSites = ImmutableList.of();

    /**
     * The townhall of the colony.
     */
    @Nullable
    private ITownHall townHall;

    /**
     * Variable to check if the buildings needs to be synced.
     */
    private boolean isBuildingsDirty = false;

    /**
     * Variable to check if the building extensions needs to be synced.
     */
    private boolean isBuildingExtensionsDirty = false;

    /**
     * The colony of the manager.
     */
    private final Colony colony;

    /**
     * Max chunk pos where a building is placed into a certain direction.
     */
    private int minChunkX;
    private int maxChunkX;
    private int minChunkZ;
    private int maxChunkZ;

    /**
     * Creates the BuildingManager for a colony.
     *
     * @param colony the colony.
     */
    public RegisteredStructureManager(final Colony colony)
    {
        this.colony = colony;
    }

    @Override
    public void read(@NotNull final HolderLookup.Provider provider, @NotNull final CompoundTag compound)
    {
        buildings = ImmutableMap.of();
        maxChunkX = colony.getCenter().getX() >> 4;
        minChunkX = colony.getCenter().getX() >> 4;
        maxChunkZ = colony.getCenter().getZ() >> 4;
        minChunkZ = colony.getCenter().getZ() >> 4;

        // Building extensions (previously fields)
        final ListTag extensionsTagList;
        if (compound.contains(TAG_FIELDS))
        {
            extensionsTagList = compound.getListOrEmpty(TAG_FIELDS);
        }
        else
        {
            extensionsTagList = compound.getListOrEmpty(TAG_BUILDING_EXTENSIONS);
        }
        for (int i = 0; i < extensionsTagList.size(); ++i)
        {
            try
            {
                final CompoundTag extensionCompound = extensionsTagList.getCompoundOrEmpty(i);
                final IBuildingExtension extension = BuildingExtensionDataManager.compoundToExtension(provider, extensionCompound);
                if (extension != null)
                {
                    addBuildingExtension(extension);
                }
            }
            catch (final Exception e)
            {
                Log.getLogger().error("Failure loading building extension", e);
            }
        }

        //  Buildings
        final ListTag buildingTagList = compound.getListOrEmpty(TAG_BUILDINGS);
        for (int i = 0; i < buildingTagList.size(); ++i)
        {
            final CompoundTag buildingCompound = buildingTagList.getCompoundOrEmpty(i);
            @Nullable final IBuilding b = IBuildingDataManager.getInstance().createFrom(colony, buildingCompound, provider);
            if (b != null)
            {
                addBuilding(b);
                setMaxChunk(b);
            }
        }

        if (compound.contains(TAG_LEISURE))
        {
            final ListTag leisureTagList = compound.getListOrEmpty(TAG_LEISURE);
            final List<BlockPos> leisureSitesList = new ArrayList<>();
            for (int i = 0; i < leisureTagList.size(); ++i)
            {
                final BlockPos pos = BlockPosUtil.read(leisureTagList.getCompoundOrEmpty(i), TAG_POS);
                if (!leisureSitesList.contains(pos))
                {
                    leisureSitesList.add(pos);
                }
            }
            leisureSites = ImmutableList.copyOf(leisureSitesList);
        }

        // Ensure building extensions are still tied to an appropriate building
        for (final IBuildingExtension extension : buildingExtensions.values())
        {
            if (!extension.isTaken())
            {
                continue;
            }
            final IBuilding building = buildings.get(extension.getBuildingId());
            if (building == null)
            {
                extension.resetOwningBuilding();
                continue;
            }

            final BuildingExtensionsModule extensionsModule = building.getFirstModuleOccurance(BuildingExtensionsModule.class);
            if (extensionsModule == null || !extension.getClass().equals(extensionsModule.getExpectedExtensionType()))
            {
                extension.resetOwningBuilding();
                if (extensionsModule != null)
                {
                    extensionsModule.freeExtension(extension);
                }
            }
        }
    }

    /**
     * Set the max chunk direction this building is in.
     *
     * @param b the max chunk dir.
     */
    private void setMaxChunk(final IBuilding b)
    {
        final int chunkX = b.getPosition().getX() >> 4;
        final int chunkZ = b.getPosition().getZ() >> 4;
        if (chunkX >= maxChunkX)
        {
            maxChunkX = chunkX + 1;
        }

        if (chunkX <= minChunkX)
        {
            minChunkX = chunkX - 1;
        }

        if (chunkZ >= maxChunkZ)
        {
            maxChunkZ = chunkZ + 1;
        }

        if (chunkZ <= minChunkZ)
        {
            minChunkZ = chunkZ - 1;
        }
    }

    @Override
    public void write(@NotNull final HolderLookup.Provider provider, @NotNull final CompoundTag compound)
    {
        //  Buildings
        @NotNull final ListTag buildingTagList = new ListTag();
        for (@NotNull final IBuilding b : buildings.values())
        {
            @NotNull final CompoundTag buildingCompound = b.serializeNBT(provider);
            buildingTagList.add(buildingCompound);
        }
        compound.put(TAG_BUILDINGS, buildingTagList);

        // Building extensions
        compound.put(TAG_BUILDING_EXTENSIONS, buildingExtensions.values().stream().map(f -> BuildingExtensionDataManager.extensionToCompound(provider, f)).collect(NBTUtils.toListNBT()));

        // Leisure sites
        @NotNull final ListTag leisureTagList = new ListTag();
        for (@NotNull final BlockPos pos : leisureSites)
        {
            @NotNull final CompoundTag leisureCompound = new CompoundTag();
            BlockPosUtil.write(leisureCompound, TAG_POS, pos);
            leisureTagList.add(leisureCompound);
        }
        compound.put(TAG_LEISURE, leisureTagList);
    }

    @Override
    public void clearDirty()
    {
        isBuildingsDirty = false;
        isBuildingExtensionsDirty = false;
        buildings.values().forEach(IBuilding::clearDirty);
    }

    @Override
    public void sendPackets(final Set<ServerPlayer> closeSubscribers, final Set<ServerPlayer> newSubscribers)
    {
        sendBuildingPackets(closeSubscribers, newSubscribers);
        sendBuildingExtensionPackets(closeSubscribers, newSubscribers);
        isBuildingsDirty = false;
        isBuildingExtensionsDirty = false;
    }

    @Override
    public void onColonyTick(final IColony colony)
    {
        //  Tick Buildings
        for (@NotNull final IBuilding building : buildings.values())
        {
            if (WorldUtil.isBlockLoaded(colony.getWorld(), building.getPosition()))
            {
                // PORT-NOTE(26.2): the try/catch is ours; the 1.21.1 original and upstream both let a throwing building
                // out of this loop. It escapes into Colony's state machine handler, which delays the *whole* slow tick
                // by five minutes -- and the loop it aborted never reaches the buildings after this one, so they go
                // untouched too. Since the same building throws first on every retry, the tail of this map effectively
                // stops existing. Containing the failure to the one building costs nothing when nothing throws and
                // keeps the other 148 huts, the citizens, the graves, reproduction and quests ticking.
                try
                {
                    building.onColonyTick(colony);
                }
                catch (final RuntimeException e)
                {
                    reportBuildingTickFailure(colony, building, e);
                }
            }
        }

        if (pendingPrestigeCalc.isEmpty())
        {
            pendingPrestigeCalc.addAll(buildings.values());
            Collections.shuffle(pendingPrestigeCalc);
        }
        else
        {
            pendingPrestigeCalc.getLast().asyncPrestigeRecalc();
        }
    }

    /**
     * Report a building that threw out of its colony tick, loudly the first time and rarely afterwards.
     *
     * <p>The full trace goes out once per building per server run, because the first one is the one worth reading and a
     * building that is broken keeps being broken every 500 ticks. After that it is a single line every
     * {@value #BUILDING_TICK_FAILURE_REPEAT} failures -- about once an hour and a half -- so a permanently unhappy hut
     * stays visible in the log without burying everything else in it.</p>
     */
    private void reportBuildingTickFailure(final IColony colony, final IBuilding building, final RuntimeException e)
    {
        final int count = buildingTickFailures.merge(building.getPosition(), 1, Integer::sum);
        if (count == 1)
        {
            Log.getLogger().error("Colony {}: building {} at {} threw during its colony tick and was skipped."
                                  + " The rest of the colony keeps ticking; this building will be retried next tick.",
              colony.getID(), building.getBuildingType().getRegistryName(), building.getPosition(), e);
        }
        else if (count % BUILDING_TICK_FAILURE_REPEAT == 0)
        {
            Log.getLogger().error("Colony {}: building {} at {} has now failed its colony tick {} times in a row.",
              colony.getID(), building.getBuildingType().getRegistryName(), building.getPosition(), count);
        }
    }

    @Override
    public void clearPendingPrestigeCalc(final IBuilding building)
    {
        pendingPrestigeCalc.remove(building);
    }

    @Override
    public int getColonyPrestige()
    {
        int total = 0;
        for (IBuilding building : buildings.values())
        {
            total += building.getPrestige();
        }
        return total;
    }

    @Override
    public void markBuildingsDirty()
    {
        isBuildingsDirty = true;
    }

    @Override
    public void cleanUpBuildings(@NotNull final IColony colony)
    {
        // The whole of this method's removal is suspended while the switch is on, buildings and extensions and
        // leisure sites alike: all three are dropped for the same reason -- their block is not where it was --
        // and all three are lost just as irrecoverably. See KeepBuildings.
        final boolean keepBuildings = KeepBuildings.isOn(colony);

        @Nullable final List<IBuilding> removedBuildings = new ArrayList<>();
        final List<IBuilding> sparedBuildings = new ArrayList<>();

        //Need this list, we may enter here while we add a building in the real world.
        final List<IBuilding> tempBuildings = new ArrayList<>(buildings.values());

        for (@NotNull final IBuilding building : tempBuildings)
        {
            final BlockPos loc = building.getPosition();
            if (WorldUtil.isBlockLoaded(colony.getWorld(), loc) && !building.isMatchingBlock(colony.getWorld().getBlockState(loc).getBlock()))
            {
                //  Sanity cleanup
                if (keepBuildings)
                {
                    sparedBuildings.add(building);
                }
                else
                {
                    removedBuildings.add(building);
                }
            }
        }

        reportSpared(colony, sparedBuildings);

        if (!keepBuildings)
        {
            if (buildingExtensions.entrySet().removeIf(extension -> WorldUtil.isBlockLoaded(colony.getWorld(), extension.getValue().getPosition())
                && (!colony.isCoordInColony(colony.getWorld(), extension.getValue().getPosition()) || !extension.getValue().isValidPlacement(colony))))
            {
                markBuildingExtensionsDirty();
            }

            for (@NotNull final BlockPos pos : leisureSites)
            {
                if (WorldUtil.isBlockLoaded(colony.getWorld(), pos) && (!(colony.getWorld().getBlockEntity(pos) instanceof TileEntityDecorationController)))
                {
                    removeLeisureSite(pos);
                }
            }
        }

        if (!removedBuildings.isEmpty() && removedBuildings.size() >= buildings.values().size())
        {
            Log.getLogger()
              .warn("Colony:" + colony.getID()
                      + " is removing all buildings at once. Did you just load a backup? If not there is a chance that colony data got corrupted and you want to restore a backup."
                      + " Use /mc colony keepbuildings " + colony.getID() + " on to stop this happening, then /mc colony restorehuts to put the hut blocks back.");
        }

        removedBuildings.forEach(IBuilding::destroy);
    }

    /**
     * Say which buildings the cleanup would have destroyed but did not, because {@link KeepBuildings} is on.
     * <p>
     * The player needs the damage list, and needs it to still be readable: the cleanup runs every 500 game
     * ticks, so a colony with a hundred and forty-five orphaned buildings would put a hundred and forty-five
     * lines in the log every twenty-five seconds for as long as the switch stayed on. Each building is
     * therefore named once, the first pass it is spared on, and after that only a change in the count says
     * anything at all. A pass that spares nothing forgets everything, so turning the switch off and on again --
     * or restoring the huts and losing one again -- reports afresh.
     *
     * @param colony the colony.
     * @param spared the buildings that were kept.
     */
    private void reportSpared(@NotNull final IColony colony, @NotNull final List<IBuilding> spared)
    {
        if (spared.isEmpty())
        {
            sparedReported.clear();
            lastSparedCount = 0;
            return;
        }

        for (final IBuilding building : spared)
        {
            if (sparedReported.add(building.getPosition()))
            {
                Log.getLogger().warn("Colony {}: keeping {} (level {}) at {} although its hut block is missing or wrong."
                                       + " The sanity cleanup would have deleted it; /mc colony keepbuildings is on.",
                  colony.getID(), building.getBuildingType().getRegistryName(), building.getBuildingLevel(), building.getPosition().toShortString());
            }
        }

        if (spared.size() != lastSparedCount)
        {
            lastSparedCount = spared.size();
            Log.getLogger().warn("Colony {}: {} of {} building(s) have no hut block and are being kept only by /mc colony keepbuildings."
                                   + " Run /mc colony restorehuts {} confirm to put the hut blocks back, then turn the switch off.",
              colony.getID(), spared.size(), buildings.size(), colony.getID());
        }
    }

    @Override
    public List<BlockPos> getLeisureSites()
    {
        return leisureSites;
    }

    @Override
    public BlockPos getRandomLeisureSite()
    {
        final boolean isRaining = colony.getWorld().isRaining();

        BlockPos building = null;
        final int randomDist = RANDOM.nextInt(4);
        if (randomDist < 1)
        {
            building = townHall != null && townHall.getBuildingLevel() >= 3 ? townHall.getPosition() : null;
            if (building != null)
            {
                return building;
            }
        }

        if (randomDist < 2)
        {
            if (!isRaining && RANDOM.nextBoolean())
            {
                building = getRandomBuilding(b -> b instanceof BuildingMysticalSite && b.getBuildingLevel() >= 1);
            }
            else if (RANDOM.nextBoolean())
            {
                building = getRandomBuilding(b -> b instanceof BuildingLibrary && b.getBuildingLevel() >= 1);
            }
            else
            {
                building = getRandomBuilding(b -> b instanceof BuildingUniversity && b.getBuildingLevel() >= 1);
            }
        }

        if (building != null)
        {
            return building;
        }

        if (randomDist < 3 || (isRaining && (townHall == null || townHall.getBuildingLevel() < 1)))
        {
            building = getRandomBuilding(b -> b.hasModule(BuildingModules.TAVERN_VISITOR) && b.getBuildingLevel() >= 1);
            if (building != null)
            {
                return building;
            }
        }

        if (isRaining)
        {
            return townHall == null ? null : townHall.getPosition();
        }

        return leisureSites.isEmpty() ? null : leisureSites.get(RANDOM.nextInt(leisureSites.size()));
    }

    @Override
    public void addLeisureSite(final BlockPos pos)
    {
        final List<BlockPos> tempList = new ArrayList<>(leisureSites);
        if (!tempList.contains(pos))
        {
            tempList.add(pos);
            this.leisureSites = ImmutableList.copyOf(tempList);
            markBuildingsDirty();
        }
    }

    @Override
    public void removeLeisureSite(final BlockPos pos)
    {
        if (leisureSites.contains(pos))
        {
            final List<BlockPos> tempList = new ArrayList<>(leisureSites);
            tempList.remove(pos);
            this.leisureSites = ImmutableList.copyOf(tempList);
            markBuildingsDirty();
        }
    }

    @Nullable
    @Override
    public IWareHouse getClosestWarehouseInColony(final BlockPos pos)
    {
        IWareHouse wareHouse = null;
        double dist = 0;
        for (final IWareHouse building : wareHouses)
        {
            if (building.getBuildingLevel() > 0 && building.getTileEntity() != null)
            {
                final double tempDist = building.getPosition().distSqr(pos);
                if (wareHouse == null || tempDist < dist)
                {
                    dist = tempDist;
                    wareHouse = building;
                }
            }
        }

        return wareHouse;
    }

    @Override
    public boolean keepChunkColonyLoaded(final LevelChunk chunk)
    {
        final Set<BlockPos> capList = ColonyUtils.getAllClaimingBuildings(chunk).get(colony.getID());
        if (capList != null && capList.size() >= MineColonies.getConfig().getServer().colonyLoadStrictness.get())
        {
            return true;
        }

        return isInhabitedStaticClaim(chunk);
    }

    /**
     * Whether a chunk is one the colony claimed by hand and put a building on.
     * <p>
     * {@code colonyloadstrictness} counts <em>building</em> claims, and a building claims its own chunk plus its level
     * radius, so three of them have to overlap before the ground under them is force-loaded. In the middle of a town
     * that is met everywhere and the rule works. An enclave -- a patch of scepter-claimed land far from the centre --
     * holds a handful of huts spread out, never three deep, so nothing there was ever ticketed: the moment the player
     * walked away its citizens stopped existing while the main colony carried on. See {@code 26.2/ENCLAVE-BUILD.md} §7.2.
     * <p>
     * A static claim on its own is not enough to justify a ticket -- the scepter can paint a lot of empty ground, and
     * every ticket is server time. It has to be land the colony actually lives on, so this asks for both: the chunk is
     * in the colony's static claim <em>and</em> a building of the colony stands in it. The number of tickets that can
     * come out of this is bounded by {@code maxforcedchunks} where the tickets are actually registered, in
     * {@link com.minecolonies.core.colony.Colony#checkChunkAndRegisterTicket}.
     *
     * @param chunk the chunk.
     * @return true if the chunk is statically claimed by this colony and holds one of its buildings.
     */
    private boolean isInhabitedStaticClaim(final LevelChunk chunk)
    {
        if (!ColonyUtils.getStaticClaims(chunk).contains(colony.getID()))
        {
            return false;
        }

        final ChunkPos pos = chunk.getPos();
        for (final BlockPos buildingPos : buildings.keySet())
        {
            if ((buildingPos.getX() >> 4) == pos.x() && (buildingPos.getZ() >> 4) == pos.z())
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public IBuilding getHouseWithSpareBed()
    {
        return getHouseWithSpareBed(null);
    }

    @Override
    public IBuilding getHouseWithSpareBed(@Nullable final ICitizenData citizen)
    {
        if (citizen != null && citizen.isChild())
        {
            // A child is not housed by bed at all -- it lives with its parents, which ReproductionManager decided at
            // birth. Answering this question for one would offer it a bed and undo that, so refuse to answer.
            return null;
        }

        final BlockPos anchor = getHousingAnchor(citizen);

        IBuilding best = null;
        double bestDistance = Double.MAX_VALUE;
        for (final IBuilding building : buildings.values())
        {
            if (!hasSpareBedFor(building, citizen))
            {
                continue;
            }

            final double distance = building.getPosition().distSqr(anchor);
            if (best == null
                  || distance < bestDistance
                  || (distance == bestDistance && BLOCK_POS_ORDER.compare(building.getPosition(), best.getPosition()) < 0))
            {
                best = building;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    public BlockPos getHousingAnchor(@Nullable final ICitizenData citizen)
    {
        if (citizen != null && citizen.getWorkBuilding() != null)
        {
            return citizen.getWorkBuilding().getPosition();
        }
        return colony.getCenter();
    }

    /**
     * Whether a building could take this citizen as a resident.
     * <p>
     * A house the citizen already lives in always counts, even when it is full (they occupy one of its beds) and even
     * when it is {@link HiringMode#LOCKED} (that mode means the player picked the residents, so it is not a reason to
     * evict one). Anything else has to be an unlocked house with a free bed.
     *
     * @param building the candidate building.
     * @param citizen  the citizen, or null when asking for anybody at all.
     * @return true if the citizen could live there.
     */
    private static boolean hasSpareBedFor(final IBuilding building, @Nullable final ICitizenData citizen)
    {
        if (!building.hasModule(LivingBuildingModule.class))
        {
            return false;
        }

        final LivingBuildingModule module = building.getFirstModuleOccurance(LivingBuildingModule.class);
        if (citizen != null && module.hasAssignedCitizen(citizen))
        {
            return true;
        }

        if (HiringMode.LOCKED.equals(module.getHiringMode()))
        {
            return false;
        }

        // getResidentCount, not the list size: a child living with its parents is on the list but holds no bed.
        return module.getResidentCount() < module.getModuleMax();
    }

    @NotNull
    @Override
    public Map<BlockPos, IBuilding> getBuildings()
    {
        return buildings;
    }

    @Nullable
    @Override
    public ITownHall getTownHall()
    {
        return townHall;
    }

    @Override
    public int getMysticalSiteMaxBuildingLevel()
    {
        int maxLevel = 0;
        if (hasMysticalSite())
        {
            for (final IMysticalSite mysticalSite : mysticalSites)
            {
                if (mysticalSite.getBuildingLevel() > maxLevel)
                {
                    maxLevel = mysticalSite.getBuildingLevel();
                }
            }
        }
        return maxLevel;
    }

    @Override
    public boolean hasWarehouse()
    {
        return !wareHouses.isEmpty();
    }

    @Override
    public boolean hasMysticalSite()
    {
        return !mysticalSites.isEmpty();
    }

    @Override
    public boolean hasTownHall()
    {
        return townHall != null;
    }

    @Override
    public IBuilding addNewBuilding(@NotNull final AbstractTileEntityColonyBuilding tileEntity, final Level world)
    {
        tileEntity.setColony(colony);
        if (!buildings.containsKey(tileEntity.getPosition()))
        {
            @Nullable final IBuilding building = IBuildingDataManager.getInstance().createFrom(colony, tileEntity);
            if (building != null)
            {
                addBuilding(building);
                tileEntity.setBuilding(building);
                building.upgradeBuildingLevelToSchematicData();

                Log.getLogger().debug(String.format("Colony %d - new Building %s for %s at %s",
                  colony.getID(),
                  building.getBuildingDisplayName(),
                  tileEntity.getBlockState().getBlock(),
                  tileEntity.getPosition()));

                building.setRotationMirror(tileEntity.getRotationMirror());
                if (tileEntity.getStructurePack() != null)
                {
                    building.setStructurePack(tileEntity.getStructurePack().getName());
                    building.setBlueprintPath(tileEntity.getBlueprintPath());
                }
                else
                {
                    building.setStructurePack(colony.getStructurePack());
                }

                if (world != null && !(building instanceof IRSComponent))
                {
                    building.onPlacement();
                }

                colony.getRequestManager().onProviderAddedToColony(building);

                setMaxChunk(building);
            }
            else
            {
                Log.getLogger().error(String.format("Colony %d unable to create AbstractBuilding for %s at %s",
                  colony.getID(),
                  tileEntity.getBlockState().getClass(),
                  tileEntity.getPosition()), new Exception());
            }

            colony.getCitizenManager().calculateMaxCitizens();
            colony.getPackageManager().updateSubscribers();

            IMinecoloniesAPI.getInstance().getEventBus().post(new BuildingAddedModEvent(building));

            return building;
        }
        return null;
    }

    @Override
    public void removeBuilding(@NotNull final IBuilding building, final Set<ServerPlayer> subscribers)
    {
        if (buildings.containsKey(building.getID()))
        {
            final ImmutableMap.Builder<BlockPos, IBuilding> builder = new ImmutableMap.Builder<>();
            for (final IBuilding tbuilding : buildings.values())
            {
                if (tbuilding != building)
                {
                    builder.put(tbuilding.getID(), tbuilding);
                }
            }

            buildings = builder.build();

            new ColonyViewRemoveBuildingMessage(colony, building.getID()).sendToPlayer(subscribers);

            Log.getLogger().info(String.format("Colony %d - removed AbstractBuilding %s of type %s",
              colony.getID(),
              building.getID(),
              building.getSchematicName()));
        }

        if (building instanceof BuildingTownHall)
        {
            townHall = null;
        }
        else if (building instanceof BuildingWareHouse)
        {
            wareHouses.remove(building);
        }
        else if (building instanceof BuildingMysticalSite)
        {
            mysticalSites.remove(building);
        }

        //Allow Citizens to fix up any data that wasn't fixed up by the AbstractBuilding's own onDestroyed
        for (@NotNull final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            citizen.onRemoveBuilding(building);
            building.cancelAllRequestsOfCitizenOrBuilding(citizen);
        }

        //Allow Animals to fix up any data that wasn't fixed up by the AbstractBuilding's own onDestroyed
        for (@NotNull final IAnimalData animal : colony.getAnimalManager().getAnimals())
        {
            animal.onRemoveBuilding(building);
        }

        colony.getRequestManager().onProviderRemovedFromColony(building);
        colony.getRequestManager().onRequesterRemovedFromColony(building.getRequester());

        colony.getCitizenManager().calculateMaxCitizens();

        IMinecoloniesAPI.getInstance().getEventBus().post(new BuildingRemovedModEvent(building));
    }

    /**
     * Finds whether there is a guard building close to the given building
     *
     * @param building the building to check.
     * @return false if no guard tower close, true in other cases
     */
    @Override
    public boolean hasGuardBuildingNear(final IBuilding building)
    {
        if (building == null)
        {
            return true;
        }

        for (final IBuilding colonyBuilding : getBuildings().values())
        {
            if (colonyBuilding.getBuildingLevel() > 0 && (colonyBuilding instanceof IGuardBuilding || colonyBuilding instanceof BuildingBarracks))
            {
                final BoundingBox guardedRegion = BlockPosUtil.getChunkAlignedBB(colonyBuilding.getPosition(), colonyBuilding.getClaimRadius(colonyBuilding.getBuildingLevel()));
                if (guardedRegion.isInside(building.getPosition()))
                {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void guardBuildingChangedAt(final IBuilding guardBuilding, final int newLevel)
    {
        final int claimRadius = guardBuilding.getClaimRadius(Math.max(guardBuilding.getBuildingLevel(), newLevel));
        final BoundingBox guardedRegion = BlockPosUtil.getChunkAlignedBB(guardBuilding.getPosition(), claimRadius);
        for (final IBuilding building : getBuildings().values())
        {
            if (guardedRegion.isInside(building.getPosition()))
            {
                building.resetGuardBuildingNear();
            }
        }
    }

    @Override
    public void setTownHall(@Nullable final ITownHall building)
    {
        this.townHall = building;
    }

    @Override
    public List<IWareHouse> getWareHouses()
    {
        return wareHouses;
    }

    @Override
    public void removeWareHouse(final IWareHouse wareHouse)
    {
        wareHouses.remove(wareHouse);
    }

    @Override
    public List<IMysticalSite> getMysticalSites()
    {
        return mysticalSites;
    }

    @Override
    public void removeMysticalSite(final IMysticalSite mysticalSite)
    {
        mysticalSites.remove(mysticalSite);
    }

    /**
     * Updates all subscribers of building extensions etc.
     */
    @Override
    public void markBuildingExtensionsDirty()
    {
        isBuildingExtensionsDirty = true;
    }

    /**
     * Add a AbstractBuilding to the Colony.
     *
     * @param building AbstractBuilding to add to the colony.
     */
    private void addBuilding(@NotNull final IBuilding building)
    {
        buildings = new ImmutableMap.Builder<BlockPos, IBuilding>().putAll(buildings).put(building.getID(), building).build();

        building.markDirty();

        //  Limit 1 town hall
        if (building instanceof BuildingTownHall && townHall == null)
        {
            townHall = (ITownHall) building;
        }

        if (building instanceof BuildingWareHouse)
        {
            wareHouses.add((IWareHouse) building);
        }
        else if (building instanceof BuildingMysticalSite)
        {
            mysticalSites.add((IMysticalSite) building);
        }
    }

    /**
     * Sends packages to update the buildings.
     *
     * @param closeSubscribers the current event subscribers.
     * @param newSubscribers   the new event subscribers.
     */
    private void sendBuildingPackets(final Set<ServerPlayer> closeSubscribers, final Set<ServerPlayer> newSubscribers)
    {
        if (isBuildingsDirty || !newSubscribers.isEmpty())
        {
            final Set<ServerPlayer> players = new HashSet<>();
            if (isBuildingsDirty)
            {
                players.addAll(closeSubscribers);
            }
            players.addAll(newSubscribers);
            for (@NotNull final IBuilding building : buildings.values())
            {
                if (building.isDirty() || !newSubscribers.isEmpty())
                {
                    new ColonyViewBuildingViewMessage(building, !newSubscribers.isEmpty()).sendToPlayer(players);
                }
            }
        }
    }

    /**
     * Sends packages to update the building extensions.
     *
     * @param closeSubscribers the current event subscribers.
     * @param newSubscribers   the new event subscribers.
     */
    private void sendBuildingExtensionPackets(final Set<ServerPlayer> closeSubscribers, final Set<ServerPlayer> newSubscribers)
    {
        if (isBuildingExtensionsDirty || !newSubscribers.isEmpty())
        {
            final Set<ServerPlayer> players = new HashSet<>();
            if (isBuildingExtensionsDirty)
            {
                players.addAll(closeSubscribers);
            }
            players.addAll(newSubscribers);
            new ColonyViewBuildingExtensionsUpdateMessage(colony, buildingExtensions.values()).sendToPlayer(players);
        }
    }

    @Override
    public boolean canPlaceAt(final Block block, final BlockPos pos, final Player player)
    {
        if (block instanceof AbstractBlockHut hutblock)
        {
            return hutblock.canPlaceAt(pos, player);
        }

        return true;
    }

    @Override
    public void onBuildingUpgradeComplete(@Nullable final IBuilding building, final int level)
    {
        if (building != null)
        {
            colony.getCitizenManager().calculateMaxCitizens();
            markBuildingsDirty();
            QuestObjectiveEventHandler.onBuildingUpgradeComplete(building, level);
        }
    }

    @NotNull
    @Override
    public List<IBuildingExtension> getBuildingExtensions(Predicate<IBuildingExtension> matcher)
    {
        return buildingExtensions.values().stream()
                 .filter(matcher)
                 .toList();
    }

    @Override
    public Optional<IBuildingExtension> getMatchingBuildingExtension(Predicate<IBuildingExtension> matcher)
    {
        return getBuildingExtensions(matcher)
                 .stream()
                 .findFirst();
    }

    @Override
    public boolean addBuildingExtension(IBuildingExtension extension)
    {
        if (buildingExtensions.putIfAbsent(extension.getId(), extension) == null)
        {
            markBuildingExtensionsDirty();
            return true;
        }
        return false;
    }

    @Override
    public void removeBuildingExtension(Predicate<IBuildingExtension> matcher)
    {
        buildingExtensions.entrySet().removeIf(entry -> matcher.test(entry.getValue()));

        // We must send the message to everyone since building extensions here will be permanently removed from the list.
        // And the clients have no way to later on also get their building extensions removed, thus every client has to be told
        // immediately that the building extension is gone.
        markBuildingExtensionsDirty();
    }

    @Override
    @Nullable
    public IBuildingExtension getMatchingBuildingExtension(final IBuildingExtension.ExtensionId extensionId)
    {
        return buildingExtensions.get(extensionId);
    }

    @Override
    public void addBuildingExtensionIfMissing(final BuildingExtensionRegistries.BuildingExtensionEntry buildingExtensionEntry, final BlockPos pos, final Player player)
    {
        buildingExtensions.computeIfAbsent(new IBuildingExtension.ExtensionId(pos, buildingExtensionEntry), (id) -> {
            new ColonyViewBuildingExtensionsUpdateMessage(colony, buildingExtensions.values()).sendToPlayer((ServerPlayer) player);
            markBuildingExtensionsDirty();
            return buildingExtensionEntry.produceExtension(pos);
        });
    }

    @Override
    public Colony getColony()
    {
        return colony;
    }
}
