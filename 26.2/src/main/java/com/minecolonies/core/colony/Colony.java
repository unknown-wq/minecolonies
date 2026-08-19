package com.minecolonies.core.colony;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import net.minecraft.server.MinecraftServer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.ldtteam.structurize.util.BlockUtils;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.*;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ISettingsModule;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.claim.ChunkClaimData;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.colony.connections.IColonyConnectionManager;
import com.minecolonies.api.colony.managers.interfaces.*;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.workorders.IWorkManager;
import com.minecolonies.api.compatibility.newstruct.BlueprintMapping;
import com.minecolonies.api.compatibility.simpleplanes.AntiAirSettings;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.ITickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickingTransition;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.eventbus.events.colony.permissions.PlayerEnteringModEvent;
import com.minecolonies.api.eventbus.events.colony.permissions.PlayerLeavingModEvent;
import com.minecolonies.api.colony.territory.HostileTerritory;
import com.minecolonies.api.quests.IQuestManager;
import com.minecolonies.api.research.IResearchManager;
import com.minecolonies.core.colony.territory.HostileTerritoryIndex;
import com.minecolonies.api.util.*;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.minecolonies.api.util.constant.Suppression;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.colony.events.raid.RaidManager;
import com.minecolonies.core.colony.managers.*;
import com.minecolonies.core.colony.permissions.ColonyPermissionEventHandler;
import com.minecolonies.core.colony.permissions.Permissions;
import com.minecolonies.core.colony.pvp.AttackingPlayer;
import com.minecolonies.core.colony.requestsystem.management.manager.StandardRequestManager;
import com.minecolonies.core.colony.workorders.WorkManager;
import com.minecolonies.core.datalistener.CitizenNameListener;
import com.minecolonies.core.colony.workoverrides.WorkOverride;
import com.minecolonies.core.colony.rescue.KeepBuildings;
import com.minecolonies.core.debug.ColonyProtection;
import com.minecolonies.core.debug.FreeMode;
import com.minecolonies.core.network.messages.client.colony.ColonyViewRemoveWorkOrderMessage;
import com.minecolonies.core.quests.QuestManager;
import com.minecolonies.core.util.BackUpHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.minecolonies.api.colony.ColonyState.*;
import static com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateConstants.MAX_TICKRATE;
import static com.minecolonies.api.research.util.ResearchConstants.SHIELD_USAGE;
import static com.minecolonies.api.util.constant.ColonyConstants.*;
import static com.minecolonies.api.util.constant.Constants.DEFAULT_STYLE;
import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.NbtTagConstants.*;
import static com.minecolonies.api.util.constant.TranslationConstants.*;
import static com.minecolonies.core.MineColonies.getConfig;

/**
 * This class describes a colony and contains all the data and methods for manipulating a Colony.
 */
@SuppressWarnings({Suppression.BIG_CLASS, Suppression.SPLIT_CLASS})
public class Colony implements IColony
{
    /**
     * The default style for the building.
     */
    private String pack = DEFAULT_STYLE;

    /**
     * ID of the colony.
     */
    private final int id;

    /**
     * Dimension of the colony.
     */
    private ResourceKey<Level> dimensionId;

    /**
     * List of loaded chunks for the colony.
     */
    private final ConcurrentHashMap<Long, Long> loadedChunks = new ConcurrentHashMap<>();

    /**
     * The radius every colony force-load ticket is registered with.
     * <p>
     * {@code TicketStorage#addTicketWithRadius} turns this into ticket level {@code ChunkLevel.byStatus(FULL) - radius}
     * = 31 at the ticket's own chunk, and the level rises by one per chunk of distance from there. 31 is
     * ENTITY_TICKING, 32 BLOCK_TICKING, 33 FULL -- so this exact number is what makes the ticketed chunk itself tick
     * its entities, with a block-ticking ring and a loaded ring around it. Lowering it to 1 would leave the chunk
     * merely block-ticking and citizens standing on it would freeze; raising it would tick ground the colony does not
     * own. See {@link #registerClaimedChunkTickets()}.
     */
    private static final int KEEP_LOADED_RADIUS = 2;

    /**
     * This colony's own answer to "force-load the whole claim?", or null while it has never been given one.
     * <p>
     * Three states, not two, and the third is the useful one: a colony that has never been told either way follows
     * the {@code forceloadallclaims} server config, so changing that config still moves every colony an operator has
     * not spoken about, while a colony that has been set keeps its own answer whatever the config later says. Costing
     * roughly N/441 of a player each, this is a per-colony decision -- the one you play in can be on while the rest of
     * the server's colonies stay off.
     */
    @Nullable
    private Boolean forceLoadAllClaims = null;

    /**
     * List of loaded chunks for the colony.
     */
    public Set<Long> ticketedChunks = new HashSet<>();

    /**
     * Whether {@link #ticketedChunks} has changed since the last colony view packet that reached every close
     * subscriber. Cleared by {@link #clearTicketedChunksDirty()}, which {@code ColonyPackageManager} calls once the
     * buffer carrying the set has actually gone out.
     */
    private boolean ticketedChunksDirty = true;

    /**
     * List of chunks that have to be force loaded.
     */
    private final Set<Long> pendingChunks = new HashSet<>();

    /**
     * List of chunks pending for unloading, which have their tickets removed
     */
    private final Set<Long> pendingToUnloadChunks = new HashSet<>();

    /**
     * List of waypoints of the colony.
     */
    private final Map<BlockPos, BlockState> wayPoints = new HashMap<>();

    /**
     * Work Manager of the colony (Request System).
     */
    private final WorkManager workManager;

    /**
     * Building manager of the colony.
     */
    private final IRegisteredStructureManager buildingManager;

    /**
     * Grave manager of the colony.
     */
    private final IGraveManager graveManager;

    /**
     * Citizen manager of the colony.
     */
    private final ICitizenManager citizenManager;

    /**
     * Citizen manager of the colony.
     */
    private final IVisitorManager visitorManager;

    /**
     * Animal manager of the colony.
     */
    private final IAnimalManager animalManager = new AnimalManager(this);

    /**
     * Barbarian manager of the colony.
     */
    private final IRaiderManager raidManager;

    /**
     * Event manager of the colony.
     */
    private final IEventManager eventManager;

    /**
     * Reproduction manager of the colony.
     */
    private final IReproductionManager reproductionManager;

    /**
     * Event description manager of the colony.
     */
    private final IEventDescriptionManager eventDescManager;

    /**
     * The colony package manager.
     */
    private final IColonyPackageManager packageManager;

    /**
     * Event manager of the colony.
     */
    private final IStatisticsManager statisticManager;

    /**
     * Quest manager of the colony
     */
    private final IQuestManager questManager;

    /**
     * The colony permission object.
     */
    private final Permissions permissions;

    /**
     * The request manager assigned to the colony.
     */
    private IRequestManager requestManager;

    /**
     * The request manager assigned to the colony.
     */
    private final IResearchManager researchManager;

    /**
     * Traveling manager of the colony.
     */
    private final TravellingManager travellingManager = new TravellingManager(this);

    /**
     * Connection manager of the colony.
     */
    private final ColonyConnectionManager connectionManager = new ColonyConnectionManager(this);

    /**
     * The Positions which players can freely interact.
     */
    private ImmutableSet<BlockPos> freePositions = ImmutableSet.of();

    /**
     * The Blocks which players can freely interact with.
     */
    private ImmutableSet<Block> freeBlocks = ImmutableSet.of();

    /**
     * Colony permission event handler.
     */
    private ColonyPermissionEventHandler eventHandler;

    /**
     * Whether this colony may be auto-deleted.
     */
    private boolean canColonyBeAutoDeleted = true;

    /**
     * Whether this colony works without any items at all. See {@link FreeMode}.
     */
    private boolean freeMode = false;

    /**
     * Whether this colony is a hostile territory rather than a colony anybody lives in.
     * <p>
     * A territory is a colony because that is the cheapest thing it can be: ownership is one int per chunk plus a
     * column mask, and everything that asks who owns a position — protection, the border on screen, the save format,
     * the client sync — is already written against a colony id. Marking one with a flag rather than inventing a
     * second ownership layer costs a boolean; the alternative costs about fifty call sites and a new packet.
     * <p>
     * The flag is what stops it being treated as a <em>neighbour</em>. {@code ColonyManager#getClosestColony} answers
     * with the owner of the chunk before it measures any distance, so without this every scepter and every "which
     * colony is this" question asked while standing on enemy ground would answer with the enemy — and the player's
     * own tools would start editing it. See {@link com.minecolonies.api.colony.territory.HostileTerritory} for the
     * outside view of the same thing.
     */
    private boolean hostile = false;

    /**
     * The "keep working anyway" switches that are on for this colony. See {@link WorkOverride}.
     */
    private final EnumSet<WorkOverride> workOverrides = EnumSet.noneOf(WorkOverride.class);

    /**
     * Whether this colony shields its claimed chunks from blasts at all.
     * <p>
     * Defaults to on. This is the per-colony off switch the server config cannot express:
     * {@code turnoffexplosionsincolonies} says <em>how much</em> is shielded and says it for every colony on the
     * server at once, so a single colony that wants to be blown up — a testing colony, a PvP colony, one whose
     * owner simply prefers craters — has nowhere to say so. Set with
     * {@code /mc colony blastprotection <colony> off}.
     */
    private boolean blastProtection = true;

    /**
     * Whether this colony enforces its permissions against players at all. See {@link ColonyProtection}.
     * <p>
     * Defaults to on. Set with {@code /mc colony protection <colony> off}, which is the one lever that takes
     * away every denial a player can run into here at once, for a colony that is being tested rather than
     * played.
     */
    private boolean protection = true;

    /**
     * Whether the sanity cleanup is forbidden to remove anything from this colony. See {@link KeepBuildings}.
     * <p>
     * Defaults to <b>off</b>, so a colony that has never run the command behaves exactly as it always did. Set
     * with {@code /mc colony keepbuildings <colony> on}, for the one situation the cleanup cannot tell apart
     * from a player mining their huts: a world that was opened once without the mod, whose hut blocks are gone
     * for a reason that has nothing to do with the player.
     */
    private boolean keepBuildings = false;

    /**
     * What this colony's anti-air batteries are tuned to. See {@link AntiAirSettings}.
     * <p>
     * Starts out holding the exact constants the battery used before it was tunable, so an untouched colony is
     * indistinguishable from one on a build without this feature. Set with
     * {@code /mc colony antiair <colony> range|rate|damage|minlevel}.
     */
    private final AntiAirSettings antiAirSettings = new AntiAirSettings();

    /**
     * Variable to determine if its currently day or night.
     */
    private boolean isDay = true;

    /**
     * The world the colony currently runs on.
     */
    @Nullable
    private ServerLevel world = null;

    /**
     * The name of the colony.
     */
    private String name;

    /**
     * The center of the colony.
     */
    private final BlockPos center;

    /**
     * The NBTTag compound of the colony itself.
     */
    private CompoundTag colonyTag;

    /**
     * List of players visiting the colony.
     */
    private final List<Player> visitingPlayers = new ArrayList<>();

    /**
     * List of players attacking the colony.
     */
    private final List<AttackingPlayer> attackingPlayers = new ArrayList<>();

    /**
     * The colonies state machine
     */
    private ITickRateStateMachine<ColonyState> colonyStateMachine = null;

    /**
     * If the colony is dirty.
     */
    private boolean isDirty = true;

    /**
     * The colony team color.
     */
    private ChatFormatting colonyTeamColor = ChatFormatting.WHITE;

    /**
     * The colony flag, as a list of patterns.
     */
    private BannerPatternLayers colonyFlag;

    /**
     * The last time the mercenaries were used.
     */
    private long mercenaryLastUse = 0;

    /**
     * The amount of additional child time gathered when the colony is not loaded.
     */
    private int additionalChildTime = 0;

    /**
     * The maximum amount of additional child time to be stored when the colony is not loaded.
     */
    private static final int maxAdditionalChildTime = 70000;

    /**
     * Boolean whether the colony has childs.
     */
    private boolean hasChilds = false;

    /**
     * Last time the server was online.
     */
    public long lastOnlineTime = 0;

    /**
     * The force chunk load timer.
     */
    private int forceLoadTimer = 0;

    /**
     * Whether the {@code maxforcedchunks} ceiling has already been reported for this colony. Not persisted: it exists
     * only to keep the log line to one per colony per server run.
     */
    private boolean warnedAboutForcedChunkCap = false;

    /**
     * The texture set of the colony.
     */
    private String textureStyle = "default";

    /**
     * The colony name style.
     */
    private String nameStyle = "default";

    /**
     * Current day of the colony.
     */
    private int day = 0;

    /**
     * Colony claim data.
     */
    private final Long2ObjectMap<ChunkClaimData> claimData = new Long2ObjectOpenHashMap<>();

    /**
     * Townhall settings module.
     */
    private final SettingsModule settingsModule = (SettingsModule) BuildingEntry.produceModuleWithoutBuilding(BuildingModules.TOWNHALL_SETTINGS.key);

    /**
     * Base constructor.
     *
     * @param id     The current id for the colony.
     * @param name The name of the colony.
     * @param world  The world the colony exists in.
     * @param center The center of the colony (location of Town Hall).
     */
    Colony(final int id, final String name, @Nullable final ServerLevel world, final BlockPos center)
    {
        this.id = id;
        this.name = name;
        this.center = center;

        this.workManager = new WorkManager(this);
        this.buildingManager = new RegisteredStructureManager(this);
        this.graveManager = new GraveManager(this);
        this.citizenManager = new CitizenManager(this);
        this.visitorManager = new VisitorManager(this);
        this.raidManager = new RaidManager(this);
        this.eventManager = new EventManager(this);
        this.reproductionManager = new ReproductionManager(this);
        this.eventDescManager = new EventDescriptionManager(this);
        this.packageManager = new ColonyPackageManager(this);
        this.statisticManager = new StatisticsManager();
        this.questManager = new QuestManager(this);
        this.permissions = new Permissions(this);
        this.researchManager = new ResearchManager(this);

        if (world != null)
        {
            this.colonyFlag = new BannerPatternLayers.Builder().add(Utils.getRegistryValue(BannerPatterns.BASE, world), DyeColor.WHITE).build();
            this.dimensionId = world.dimension();
            onWorldLoad(world);
        }

        colonyStateMachine = new TickRateStateMachine<>(INACTIVE, e ->
        {
            Log.getLogger().warn("Exception triggered in colony:{} in dimension:{} history:{}", getID(), getDimension().identifier(), colonyStateMachine.getHistory().getString(), e);
            colonyStateMachine.setCurrentDelay(20 * 60 * 5);
        });
        colonyStateMachine.setHistoryEnabled(true, 10);

        colonyStateMachine.addTransition(new TickingTransition<>(INACTIVE, () -> true, this::updateState, UPDATE_STATE_INTERVAL));
        colonyStateMachine.addTransition(new TickingTransition<>(UNLOADED, () -> true, this::updateState, UPDATE_STATE_INTERVAL));
        colonyStateMachine.addTransition(new TickingTransition<>(ACTIVE, () -> true, this::updateState, UPDATE_STATE_INTERVAL));
        colonyStateMachine.addTransition(new TickingTransition<>(ACTIVE, () -> {
            citizenManager.tickCitizenData(TICKS_SECOND * 3);
            return false;
        }, () -> ACTIVE, TICKS_SECOND * 3));
        colonyStateMachine.addTransition(new TickingTransition<>(ACTIVE, () -> {
            animalManager.tickAnimalData(TICKS_SECOND * 3);
            return false;
        }, () -> ACTIVE, TICKS_SECOND * 3));
        colonyStateMachine.addTransition(new TickingTransition<>(ACTIVE, this::updateSubscribers, () -> ACTIVE, UPDATE_SUBSCRIBERS_INTERVAL));
        colonyStateMachine.addTransition(new TickingTransition<>(ACTIVE, this::tickRequests, () -> ACTIVE, UPDATE_RS_INTERVAL));
        colonyStateMachine.addTransition(new TickingTransition<>(ACTIVE, this::tickTravellers, () -> ACTIVE, UPDATE_TRAVELING_INTERVAL));
        colonyStateMachine.addTransition(new TickingTransition<>(ACTIVE, this::checkDayTime, () -> ACTIVE, UPDATE_DAYTIME_INTERVAL));
        colonyStateMachine.addTransition(new TickingTransition<>(ACTIVE, this::updateWayPoints, () -> ACTIVE, CHECK_WAYPOINT_EVERY));
        colonyStateMachine.addTransition(new TickingTransition<>(ACTIVE, this::worldTickSlow, () -> ACTIVE, MAX_TICKRATE));
        colonyStateMachine.addTransition(new TickingTransition<>(ACTIVE, this::tickWorkManager, () -> ACTIVE, 20));
        colonyStateMachine.addTransition(new TickingTransition<>(ACTIVE, this::tickImmediateRaids, () -> ACTIVE, TICKS_SECOND / 4));
        colonyStateMachine.addTransition(new TickingTransition<>(UNLOADED, this::worldTickUnloaded, () -> UNLOADED, MAX_TICKRATE));
    }

    /**
     * Updates the state the colony is in.
     *
     * @return the new colony state.
     */
    private ColonyState updateState()
    {
        if (world == null)
        {
            return INACTIVE;
        }
        packageManager.updateAwayTime();

        if (!packageManager.getCloseSubscribers().isEmpty() || (loadedChunks.size() > 40 && !packageManager.getImportantColonyPlayers().isEmpty()))
        {
            isDirty = true;
            return ACTIVE;
        }

        if (!packageManager.getImportantColonyPlayers().isEmpty() || forceLoadTimer > 0)
        {
            isDirty = true;
            return UNLOADED;
        }

        return INACTIVE;
    }

    /**
     * Updates the existing subscribers
     *
     * @return false
     */
    private boolean updateSubscribers()
    {
        packageManager.updateSubscribers();
        return false;
    }

    /**
     * Ticks the request manager.
     *
     * @return false
     */
    private boolean tickRequests()
    {
        getRequestManager().tick();
        return false;
    }

    /**
     * Ticks the travelling manager.
     *
     * @return false
     */
    private boolean tickTravellers()
    {
        if (getTravellingManager() != null)
        {
            return !getTravellingManager().onTick();
        }
        return false;
    }

    /**
     * Starts the raids that were asked to begin at once, rather than leaving them to the next slow tick.
     *
     * @return false
     */
    private boolean tickImmediateRaids()
    {
        raidManager.tickImmediateRaids();
        return false;
    }

    /**
     * Called every 500 ticks, for slower updates.
     *
     * @return false
     */
    private boolean worldTickSlow()
    {
        buildingManager.cleanUpBuildings(this);
        citizenManager.onColonyTick(this);
        visitorManager.onColonyTick(this);
        animalManager.onColonyTick(this);
        updateAttackingPlayers();
        eventManager.onColonyTick(this);
        buildingManager.onColonyTick(this);
        graveManager.onColonyTick(this);
        reproductionManager.onColonyTick(this);
        questManager.onColonyTick();

        final long currTime = System.currentTimeMillis();
        if (lastOnlineTime != 0)
        {
            final long pastTime = currTime - lastOnlineTime;
            if (pastTime > ONE_HOUR_IN_MILLIS)
            {
                for (final IBuilding building : buildingManager.getBuildings().values())
                {
                    building.processOfflineTime(pastTime / 1000);
                }
            }
        }
        lastOnlineTime = currTime;

        updateChildTime();
        updateChunkLoadTimer();
        return false;
    }

    /**
     * Tick the work Manager.
     */
    private boolean tickWorkManager()
    {
        workManager.onColonyTick(this);
        return false;
    }

    /**
     * Check if we can unload the colony now.
     * Update chunk unload timer and releases chunks when it hits 0.
     */
    private void updateChunkLoadTimer()
    {
        if (getConfig().getServer().forceLoadColony.get())
        {
            for (final ServerPlayer sub : getPackageManager().getCloseSubscribers())
            {
                if (getPermissions().getRank(sub).isColonyManager())
                {
                    this.forceLoadTimer = getConfig().getServer().loadtime.get() * 20 * 60;

                    // First the claim, because it needs no chunk to be loaded to decide anything. Every chunk it
                    // covers is then skipped below instead of being pulled off disk (or generated) purely to be asked
                    // a question that has already been answered -- which matters here, since after the timer expired
                    // every chunk the colony held is sitting in pendingToUnloadChunks waiting for exactly that.
                    registerClaimedChunkTickets();

                    pendingChunks.addAll(pendingToUnloadChunks);
                    for (final long pending : pendingChunks)
                    {
                        if (ticketedChunks.contains(pending))
                        {
                            continue;
                        }
                        checkChunkAndRegisterTicket(pending, world.getChunk(ChunkPos.getX(pending), ChunkPos.getZ(pending)));
                    }

                    pendingToUnloadChunks.clear();
                    pendingChunks.clear();
                    return;
                }
            }

            if (this.forceLoadTimer > 0)
            {
                // The claim can change while the timer runs down -- a hut is placed, a scepter click adds ground --
                // and the chunk-load event that used to be the only trigger never fires for ground nobody walks on.
                registerClaimedChunkTickets();

                if (getPackageManager().getImportantColonyPlayers().isEmpty())
                {
                    this.forceLoadTimer -= (MAX_TICKRATE * 3);
                }
                else
                {
                    this.forceLoadTimer -= MAX_TICKRATE;
                }
                if (this.forceLoadTimer <= 0)
                {
                    for (final long chunkPos : this.ticketedChunks)
                    {
                        final int chunkX = ChunkPos.getX(chunkPos);
                        final int chunkZ = ChunkPos.getZ(chunkPos);
                        if (world instanceof ServerLevel)
                        {
                            final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                            // 26.2: chunk tickets no longer carry a per-ticket value object.
                            // add/removeRegionTicket(type, pos, radius, value[, forceTicks]) became
                            // add/removeTicketWithRadius(type, pos, radius); the "keep loaded" flags live on
                            // the TicketType itself (see ColonyConstants.KEEP_LOADED_TYPE).
                            ((ServerChunkCache) world.getChunkSource()).removeTicketWithRadius(KEEP_LOADED_TYPE, pos, KEEP_LOADED_RADIUS);
                            pendingToUnloadChunks.add(chunkPos);
                        }
                    }
                    ticketedChunks.clear();
                    markTicketedChunksDirty();
                }
            }
        }
    }

    /**
     * Checks the chunk and registers a ticket for it if needed
     *
     * @param chunkPos chunk position to check
     */
    private void checkChunkAndRegisterTicket(final long chunkPos, final LevelChunk chunk)
    {
        if (forceLoadTimer > 0 && world instanceof ServerLevel)
        {
            if (!ticketedChunks.contains(chunkPos) && buildingManager.keepChunkColonyLoaded(chunk))
            {
                registerTicket(chunkPos);
            }
        }
    }

    /**
     * Force-load and tick every chunk this colony owns, not only the ones its buildings stand on.
     * <p>
     * <b>Why this exists.</b> The upstream rule tickets a chunk only when {@code keepChunkColonyLoaded} passes -- three
     * overlapping building claims, or (since the enclave work) a static claim with a building standing in it. Everything
     * else the colony owns is left to whatever a nearby player happens to load. A citizen standing on such ground when
     * the player walks away first stops ticking and then stops existing, and because a citizen entity only registers
     * itself with the colony from its own tick ({@link com.minecolonies.core.entity.citizen.EntityCitizen} state
     * machine -> {@code CitizenColonyHandler#registerWithColony}), the colony reports it as {@code <no entity>} rather
     * than as a citizen standing still.
     * <p>
     * <b>The arithmetic, because it decides the whole shape.</b> {@code TicketStorage#addTicketWithRadius} builds
     * {@code Ticket(type, ChunkLevel.byStatus(FULL) - radius)}, i.e. level {@code 33 - radius} at the ticket's own
     * chunk, and {@code SimulationChunkTracker} raises that level by one per chunk of Chebyshev distance.
     * {@code ChunkLevel.fullStatus} then reads level <= 31 as ENTITY_TICKING, 32 as BLOCK_TICKING and 33 as FULL --
     * loaded, entities present but frozen ({@code Visibility.TRACKED}), nothing ticking. So a radius-2 ticket makes
     * <em>exactly its own centre chunk</em> entity-ticking, with a block-ticking ring at distance 1 and a merely loaded
     * ring at distance 2. There is no radius that entity-ticks a wide area cheaply and stops exactly at the claim
     * border: covering N claimed chunks costs N tickets, one each. A larger radius would tick a square that overhangs
     * the claim onto ground the colony does not own, so this stays at one exact ticket per owned chunk.
     * <p>
     * <b>What "owned" means.</b> {@link #claimData} is the colony's own claim map, keyed by packed {@link ChunkPos} and
     * saved in the colony NBT under {@code TAG_CLAIM_DATA}; a chunk is the colony's when its
     * {@link IChunkClaimData#getOwningColony()} is this colony's id -- the same test the border renderer draws and the
     * same one {@code ChunkDataHelper.BuildingClaimGuard} counts against {@code maxoutlyingchunks}. Reading it needs no
     * chunk to be loaded, which is the point: the old path could only ever ticket a chunk that something else had
     * already loaded.
     * <p>
     * One known gap, inherited rather than introduced: a {@code ChunkClaimData} object lives in the map of whichever
     * colony first touched that chunk ({@link #claimNewChunk}), and ownership can later move to a neighbour through
     * {@code addColony}/{@code removeColony} without the entry moving with it. A chunk this colony owns but never
     * created the data for is therefore invisible here. It takes two colonies claiming into each other to happen, and
     * the same blind spot already governs the outlying-chunk ceiling, so it is left alone rather than papered over by
     * walking the whole dimension's claim map every 500 ticks.
     * <p>
     * Behind {@link #isForceLoadAllClaims()} -- the colony's own switch, falling back to the {@code forceloadallclaims}
     * server config -- default off, because entity-ticking a whole town is real server time. The
     * {@code maxforcedchunks} ceiling still applies and is the only thing standing between a large claim and the tick
     * loop.
     */
    private void registerClaimedChunkTickets()
    {
        if (!isForceLoadAllClaims() || forceLoadTimer <= 0 || world == null)
        {
            return;
        }

        final LongArrayList owned = new LongArrayList();
        for (final Long2ObjectMap.Entry<ChunkClaimData> entry : claimData.long2ObjectEntrySet())
        {
            if (entry.getValue().getOwningColony() == getID())
            {
                owned.add(entry.getLongKey());
            }
        }

        // A claim map iterates in hash order, which is a poor thing to be truncated by. Once the claim is larger than
        // the ceiling, whichever chunks happen to come first would win and the town hall itself could end up outside
        // the ticketed set. Sorting by distance from the centre costs a sort of a few hundred longs, and only in the
        // case that is going to be cut short anyway, and it makes the cut mean "the innermost chunks stay".
        final int max = getConfig().getServer().maxForcedChunks.get();
        if (max != 0 && owned.size() > max)
        {
            final ChunkPos centre = ChunkPos.containing(getCenter());
            owned.sort((first, second) -> Long.compare(chunkDistanceSq(first, centre), chunkDistanceSq(second, centre)));
        }

        for (final long chunkPos : owned)
        {
            if (!registerTicket(chunkPos))
            {
                break;
            }
        }

        releaseTicketsOnLostGround();
    }

    /**
     * Squared distance in chunks between a packed chunk position and another chunk.
     *
     * @param packed the packed position.
     * @param other  the chunk to measure to.
     * @return the squared distance.
     */
    private static long chunkDistanceSq(final long packed, final ChunkPos other)
    {
        final long dx = (long) ChunkPos.getX(packed) - other.x();
        final long dz = (long) ChunkPos.getZ(packed) - other.z();
        return dx * dx + dz * dz;
    }

    /**
     * Drop the tickets of chunks this colony no longer has any claim on.
     * <p>
     * Without this, unclaiming ground with the scepter or tearing a building down leaves its ticket in place until the
     * force-load timer runs out -- and while an officer keeps visiting, that timer never runs out, so the server would
     * keep ticking land that stopped being anyone's. Only chunks the colony has lost entirely are released: a chunk it
     * still owns, or still has a building claim on, keeps its ticket even if it no longer passes
     * {@code keepChunkColonyLoaded}, so this never fights the upstream rule.
     */
    private void releaseTicketsOnLostGround()
    {
        final Iterator<Long> iterator = ticketedChunks.iterator();
        while (iterator.hasNext())
        {
            final long packed = iterator.next();
            final ChunkPos pos = ChunkPos.unpack(packed);
            final IChunkClaimData data = IColonyManager.getInstance().getClaimData(dimensionId, pos);
            if (data != null && (data.getOwningColony() == getID() || !data.getAllClaimingBuildings().getOrDefault(getID(), Collections.emptySet()).isEmpty()))
            {
                continue;
            }

            iterator.remove();
            markTicketedChunksDirty();
            world.getChunkSource().removeTicketWithRadius(KEEP_LOADED_TYPE, pos, KEEP_LOADED_RADIUS);
            // Deliberately not queued into pendingToUnloadChunks: that queue is re-examined on the next officer visit
            // with a world.getChunk() per entry, which would load the very ground the colony just gave up.
            pendingChunks.remove(packed);
        }
    }

    /**
     * Whether this colony force-loads its whole claim right now.
     *
     * @return the colony's own answer if it has been given one, otherwise the {@code forceloadallclaims} server config.
     */
    public boolean isForceLoadAllClaims()
    {
        return forceLoadAllClaims != null ? forceLoadAllClaims : getConfig().getServer().forceLoadAllClaims.get();
    }

    /**
     * This colony's own answer, as opposed to the one it is currently acting on.
     *
     * @return true or false if an operator has set one, null while the colony follows the server config.
     */
    @Nullable
    public Boolean getForceLoadAllClaimsOverride()
    {
        return forceLoadAllClaims;
    }

    /**
     * Set, or clear, this colony's own answer to "force-load the whole claim?".
     * <p>
     * Turning it off has to give the ground back, not merely stop taking more: an operator who switches it off, sees
     * the same chunks still ticking and concludes the switch does nothing is worse served than one who was never
     * offered the switch. So the effective value dropping to false releases the claim tickets immediately rather than
     * waiting out the force-load timer.
     *
     * @param value true, false, or null to go back to following the server config.
     */
    public void setForceLoadAllClaimsOverride(@Nullable final Boolean value)
    {
        final boolean wasOn = isForceLoadAllClaims();
        this.forceLoadAllClaims = value;
        this.markDirty();

        final boolean isOn = isForceLoadAllClaims();
        if (wasOn && !isOn)
        {
            releaseClaimOnlyTickets();
        }
        else if (!wasOn && isOn)
        {
            // Sweep now rather than at the next slow tick, so the command that flipped the switch can report the
            // real numbers instead of the ones from a second ago. It is a no-op while the force-load timer is not
            // running, which is exactly when there would be nothing to show anyway.
            registerClaimedChunkTickets();
        }
    }

    /**
     * Give back every ticket that only the whole-claim rule was holding.
     * <p>
     * The upstream rule -- {@code keepChunkColonyLoaded}, three overlapping building claims or a static claim with a
     * building in it -- has to keep working the moment the whole-claim switch goes off, so this asks it about each
     * ticketed chunk and drops only the ones it does not want. It asks with {@code getChunkNow}, which answers from
     * the loaded map and never pulls a chunk off disk: every chunk in question is force-loaded at the moment of
     * asking, precisely because we are holding its ticket. A chunk that somehow is not loaded loses its ticket and is
     * simply re-ticketed by the ordinary path if the old rule wants it back.
     */
    private void releaseClaimOnlyTickets()
    {
        if (world == null)
        {
            return;
        }

        final Iterator<Long> iterator = ticketedChunks.iterator();
        while (iterator.hasNext())
        {
            final long packed = iterator.next();
            final ChunkPos pos = ChunkPos.unpack(packed);
            final LevelChunk chunk = world.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk != null && buildingManager.keepChunkColonyLoaded(chunk))
            {
                continue;
            }

            iterator.remove();
            markTicketedChunksDirty();
            world.getChunkSource().removeTicketWithRadius(KEEP_LOADED_TYPE, pos, KEEP_LOADED_RADIUS);
        }
    }

    /**
     * Register one force-load ticket, if the colony may still hold one.
     *
     * @param chunkPos the packed chunk position.
     * @return true if the chunk is ticketed after this call.
     */
    private boolean registerTicket(final long chunkPos)
    {
        if (ticketedChunks.contains(chunkPos))
        {
            return true;
        }

        if (!hasForcedChunkBudget())
        {
            return false;
        }

        ticketedChunks.add(chunkPos);
        markTicketedChunksDirty();
        world.getChunkSource().addTicketWithRadius(KEEP_LOADED_TYPE, ChunkPos.unpack(chunkPos), KEEP_LOADED_RADIUS);
        return true;
    }

    /**
     * Whether this colony may hold one more force-load ticket.
     * <p>
     * Every ticket here is registered with a radius of {@value #KEEP_LOADED_RADIUS}, so it keeps a 5x5 block of chunks
     * loaded and its own centre chunk ticking; the only thing that bounded the total until now was that colonies did
     * not grow far enough for many chunks to pass {@code keepChunkColonyLoaded}. Enclaves change that -- see
     * {@code 26.2/ENCLAVE-FEATURES.md} -- and {@code forceloadallclaims} changes it far more, so the number is capped
     * outright by {@code maxforcedchunks}. The cap only refuses <em>new</em> tickets, it never drops one the colony
     * already holds, and the default is above what a full town needs; 0 switches it off.
     *
     * @return true if a ticket may be added.
     */
    private boolean hasForcedChunkBudget()
    {
        final int max = getConfig().getServer().maxForcedChunks.get();
        if (max == 0 || ticketedChunks.size() < max)
        {
            return true;
        }

        if (!warnedAboutForcedChunkCap)
        {
            warnedAboutForcedChunkCap = true;
            Log.getLogger()
              .info("Colony " + getID() + " (" + getName() + ") has reached maxforcedchunks (" + max
                      + ") and will not force-load any more chunks. Raise the config if the colony is meant to be this large.");
        }
        return false;
    }

    /**
     * Called every 500 ticks, for slower updates. Only ticked when the colony is not loaded.
     *
     * @return false
     */
    private boolean worldTickUnloaded()
    {
        updateChildTime();
        updateChunkLoadTimer();
        return false;
    }

    /**
     * Adds 500 additional ticks to the child growth.
     */
    private void updateChildTime()
    {
        if (hasChilds && additionalChildTime < maxAdditionalChildTime)
        {
            additionalChildTime += MAX_TICKRATE;
        }
        else
        {
            additionalChildTime = 0;
        }
    }

    /**
     * Updates the day and night detection.
     *
     * @return false
     */
    private boolean checkDayTime()
    {
        if (isDay && !WorldUtil.isDayTime(world))
        {
            isDay = false;
            eventManager.onNightFall();
            raidManager.onNightFall();
            if (!packageManager.getCloseSubscribers().isEmpty())
            {
                citizenManager.checkCitizensForHappiness();
            }

            citizenManager.updateCitizenSleep(false);
        }
        else if (!isDay && WorldUtil.isDayTime(world))
        {
            isDay = true;
            day++;
            citizenManager.onWakeUp();
        }
        return false;
    }

    /**
     * Updates the pvping playeres.
     */
    public void updateAttackingPlayers()
    {
        final List<Player> visitors = new ArrayList<>(visitingPlayers);

        //Clean up visiting player.
        for (final Player player : visitors)
        {
            if (!packageManager.getCloseSubscribers().contains(player))
            {
                visitingPlayers.remove(player);
                attackingPlayers.remove(new AttackingPlayer(player));
            }
        }

        for (final AttackingPlayer player : attackingPlayers)
        {
            if (!player.getGuards().isEmpty())
            {
                player.refreshList(this);
                if (player.getGuards().isEmpty())
                {
                    MessageUtils.format(COLONY_DEFENDED_SUCCESS_MESSAGE, player.getPlayer().getName()).sendTo(this).forManagers();
                }
            }
        }
    }

    /**
     * Set up the colony color for team handling for pvp.
     *
     * @param colonyColor the colony color.
     */
    public void setColonyColor(final ChatFormatting colonyColor)
    {
        if (this.world != null)
        {
            this.colonyTeamColor = colonyColor;
        }
        this.markDirty();
    }

    /**
     * Set up the colony flag patterns for use in decorations etc
     *
     * @param colonyFlag the list of pattern-color pairs
     */
    @Override
    public void setColonyFlag(BannerPatternLayers colonyFlag)
    {
        this.colonyFlag = colonyFlag;
        if (researchManager.getResearchEffects().getEffectStrength(SHIELD_USAGE) > 0)
        {
            citizenManager.onFlagChange();
        }
        markDirty();
    }

    /**
     * Load a saved colony.
     *
     * @param compound The NBT compound containing the colony's data.
     * @param world    the world to load it for.
     * @param provider
     * @return loaded colony.
     */
    @Nullable
    public static Colony loadColony(@NotNull final CompoundTag compound, @Nullable final ServerLevel world, final HolderLookup.@NotNull Provider provider)
    {
        try
        {
            final int id = compound.getIntOr(TAG_ID, 0);
            final String name = compound.getStringOr(TAG_NAME, "");
            final BlockPos center = BlockPosUtil.read(compound, TAG_CENTER);
            @NotNull final Colony c = new Colony(id, name, world, center);
            c.dimensionId = ResourceKey.create(Registries.DIMENSION, Identifier.parse(compound.getStringOr(TAG_DIMENSION, "")));

            c.read(compound, provider);

            return c;
        }
        catch (final Exception e)
        {
            Log.getLogger().warn("Something went wrong loading a colony, please report this to the administrators", e);
        }
        return null;
    }

    /**
     * Read colony from saved data.
     *
     * @param compound compound to read from.
     */
    public void read(@NotNull final CompoundTag compound, @NotNull final HolderLookup.Provider provider)
    {
        dimensionId = ResourceKey.create(Registries.DIMENSION, Identifier.parse(compound.getStringOr(TAG_DIMENSION, "")));

        mercenaryLastUse = compound.getLongOr(TAG_MERCENARY_TIME, 0L);
        additionalChildTime = compound.getIntOr(TAG_CHILD_TIME, 0);

        // Permissions
        permissions.loadPermissions(compound);

        citizenManager.read(provider, compound.getCompoundOrEmpty(TAG_CITIZEN_MANAGER));
        visitorManager.read(provider, compound);
        animalManager.read(provider, compound);
        buildingManager.read(provider, compound.getCompoundOrEmpty(TAG_BUILDING_MANAGER));

        // Recalculate max after citizens and buildings are loaded.
        citizenManager.afterBuildingLoad();

        graveManager.read(compound.getCompoundOrEmpty(TAG_GRAVE_MANAGER));

        eventManager.readFromNBT(provider, compound);
        statisticManager.readFromNBT(compound);

        questManager.deserializeNBT(provider, compound.getCompoundOrEmpty(TAG_QUEST_MANAGER));
        eventDescManager.deserializeNBT(provider, compound.getCompoundOrEmpty(NbtTagConstants.TAG_EVENT_DESC_MANAGER));

        if (compound.contains(TAG_RESEARCH))
        {
            researchManager.readFromNBT(provider, compound.getCompoundOrEmpty(TAG_RESEARCH));
            // now that buildings, colonists, and research are loaded, check for new autoStartResearch.
            // this is mostly for backwards compatibility with older saves, so players do not have to manually start newly added autostart researches that they've unlocked before the update.
            researchManager.checkAutoStartResearch();
        }

        //  Workload
        workManager.read(compound.getCompoundOrEmpty(TAG_WORK));

        wayPoints.clear();
        // Waypoints
        final ListTag wayPointTagList = compound.getListOrEmpty(TAG_WAYPOINT);
        for (int i = 0; i < wayPointTagList.size(); ++i)
        {
            final CompoundTag blockAtPos = wayPointTagList.getCompoundOrEmpty(i);
            final BlockPos pos = BlockPosUtil.read(blockAtPos, TAG_WAYPOINT);
            final BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, blockAtPos);
            wayPoints.put(pos, state);
        }

        // Free blocks
        final Set<Block> tempFreeBlocks = new HashSet<>();
        final ListTag freeBlockTagList = compound.getListOrEmpty(TAG_FREE_BLOCKS);
        for (int i = 0; i < freeBlockTagList.size(); ++i)
        {
            tempFreeBlocks.add(BuiltInRegistries.BLOCK.getValue(Identifier.parse(freeBlockTagList.getStringOr(i, ""))));
        }
        freeBlocks = ImmutableSet.copyOf(tempFreeBlocks);

        final Set<BlockPos> tempFreePositions = new HashSet<>();
        // Free positions
        final ListTag freePositionTagList = compound.getListOrEmpty(TAG_FREE_POSITIONS);
        for (int i = 0; i < freePositionTagList.size(); ++i)
        {
            final CompoundTag blockTag = freePositionTagList.getCompoundOrEmpty(i);
            final BlockPos block = BlockPosUtil.read(blockTag, TAG_FREE_POSITIONS);
            tempFreePositions.add(block);
        }
        freePositions = ImmutableSet.copyOf(tempFreePositions);

        packageManager.setLastContactInHours(compound.getIntOr(TAG_ABANDONED, 0));

        if (compound.contains(TAG_STYLE))
        {
            this.pack = BlueprintMapping.getStyleMapping(compound.getStringOr(TAG_STYLE, ""));
        }
        else
        {
            this.pack = compound.getStringOr(TAG_PACK, "");
        }

        raidManager.read(compound);

        if (compound.contains(TAG_AUTO_DELETE))
        {
            this.canColonyBeAutoDeleted = compound.getBooleanOr(TAG_AUTO_DELETE, false);
        }
        else
        {
            this.canColonyBeAutoDeleted = true;
        }

        this.freeMode = compound.getBooleanOr(FreeMode.TAG_FREE_MODE, false);
        this.hostile = compound.getBooleanOr(TAG_HOSTILE_TERRITORY, false);
        // Default true, so every colony saved before this flag existed reads back as protected.
        this.blastProtection = compound.getBooleanOr(TAG_BLAST_PROTECTION, true);
        // Default true for the same reason: a colony saved before this flag existed reads back as protected.
        this.protection = compound.getBooleanOr(ColonyProtection.TAG_PROTECTION, true);
        // Default false: an absent tag is a colony that never asked for the cleanup to be held back, and the
        // cleanup is what keeps an ordinary colony tidy.
        this.keepBuildings = compound.getBooleanOr(KeepBuildings.TAG_KEEP_BUILDINGS, false);
        // Absent tag means "never tuned", which reads back as the battery's original constants.
        this.antiAirSettings.read(compound);

        this.workOverrides.clear();
        this.workOverrides.addAll(WorkOverride.read(compound));

        if (compound.contains(TAG_TEAM_COLOR))
        {
            // This read can occur before the world is non-null, due to Minecraft's order of operations for capabilities.
            // As a result, setColonyColor proper must wait until onWorldLoad fires.
            this.colonyTeamColor = ChatFormatting.values()[compound.getIntOr(TAG_TEAM_COLOR, 0)];
        }

        if (compound.contains(TAG_FLAG_PATTERNS))
        {
            this.setColonyFlag(Utils.deserializeCodecMess(BannerPatternLayers.CODEC, provider, compound.get(TAG_FLAG_PATTERNS)));
        }

        getRequestManager().reset();
        if (compound.contains(TAG_REQUESTMANAGER))
        {
            getRequestManager().deserializeNBT(provider, compound.getCompoundOrEmpty(TAG_REQUESTMANAGER));
        }
        this.lastOnlineTime = compound.getLongOr(TAG_LAST_ONLINE, 0L);
        if (compound.contains(TAG_COL_TEXT))
        {
            this.textureStyle = compound.getStringOr(TAG_COL_TEXT, "");
        }
        if (compound.contains(TAG_COL_NAME_STYLE))
        {
            this.nameStyle = compound.getStringOr(TAG_COL_NAME_STYLE, "");
        }

        // Absent means "never told", which is not the same as "told no": the tag is only written when an operator has
        // actually set one, so a colony from before this existed keeps following the server config.
        this.forceLoadAllClaims = compound.contains(TAG_FORCE_LOAD_ALL_CLAIMS)
                                    ? compound.getBooleanOr(TAG_FORCE_LOAD_ALL_CLAIMS, false)
                                    : null;

        if (compound.contains(BuildingModules.TOWNHALL_SETTINGS.key) && settingsModule != null)
        {
            settingsModule.deserializeNBT(provider, compound.getCompoundOrEmpty(BuildingModules.TOWNHALL_SETTINGS.key));
        }

        @NotNull final ListTag claimTagList = compound.getListOrEmpty(TAG_CLAIM_DATA);
        for (int i = 0; i < claimTagList.size(); i++)
        {
            @NotNull final CompoundTag chunkCompound = claimTagList.getCompoundOrEmpty(i);
            final ChunkClaimData chunkClaimData = new ChunkClaimData();
            chunkClaimData.deserializeNBT(provider, chunkCompound.getCompoundOrEmpty(TAG_CHUNK_CLAIM));
            claimData.put(chunkCompound.getLongOr(TAG_CHUNK_POS, 0L), chunkClaimData);
        }
        IColonyManager.getInstance().addClaimData(this, claimData);

        this.day = compound.getIntOr(COLONY_DAY, 0);
        this.colonyTag = compound;

        if (compound.contains(NbtTagConstants.TAG_TRAVELLING_DATA))
        {
            this.travellingManager.deserializeNBT(provider, compound.getCompoundOrEmpty(NbtTagConstants.TAG_TRAVELLING_DATA));
        }

        if (compound.contains(NbtTagConstants.TAG_CONNECTION_MANAGER))
        {
            this.connectionManager.deserializeNBT(provider, compound.getCompoundOrEmpty(NbtTagConstants.TAG_CONNECTION_MANAGER));
        }
    }

    /**
     * Get the event handler assigned to the colony.
     *
     * @return the ColonyPermissionEventHandler.
     */
    public ColonyPermissionEventHandler getEventHandler()
    {
        return eventHandler;
    }

    /**
     * Write colony to save data.
     *
     * @param compound compound to write to.
     */
    public CompoundTag write(@NotNull final CompoundTag compound, @NotNull final HolderLookup.Provider provider)
    {
        compound.putInt(DATA_VERSION_TAG, DATA_VERSION);

        //  Core attributes
        compound.putInt(TAG_ID, id);
        compound.putString(TAG_DIMENSION, dimensionId.identifier().toString());

        //  Basic data
        compound.putString(TAG_NAME, name);
        BlockPosUtil.write(compound, TAG_CENTER, center);

        compound.putLong(TAG_MERCENARY_TIME, mercenaryLastUse);

        compound.putInt(TAG_CHILD_TIME, additionalChildTime);

        // Permissions
        permissions.savePermissions(compound);

        final CompoundTag buildingCompound = new CompoundTag();
        buildingManager.write(provider, buildingCompound);
        compound.put(TAG_BUILDING_MANAGER, buildingCompound);

        final CompoundTag citizenCompound = new CompoundTag();
        citizenManager.write(provider, citizenCompound);
        compound.put(TAG_CITIZEN_MANAGER, citizenCompound);

        visitorManager.write(provider, compound);
        
        animalManager.write(provider, compound);

        final CompoundTag graveCompound = new CompoundTag();
        graveManager.write(graveCompound);
        compound.put(TAG_GRAVE_MANAGER, graveCompound);

        //  Workload
        @NotNull final CompoundTag workManagerCompound = new CompoundTag();
        workManager.write(workManagerCompound);
        compound.put(TAG_WORK, workManagerCompound);

        eventManager.writeToNBT(provider, compound);
        statisticManager.writeToNBT(compound);

        compound.put(TAG_QUEST_MANAGER, questManager.serializeNBT(provider));
        compound.put(NbtTagConstants.TAG_EVENT_DESC_MANAGER, eventDescManager.serializeNBT(provider));
        raidManager.write(compound);

        @NotNull final CompoundTag researchManagerCompound = new CompoundTag();
        researchManager.writeToNBT(provider, researchManagerCompound);
        compound.put(TAG_RESEARCH, researchManagerCompound);

        // Waypoints
        @NotNull final ListTag wayPointTagList = new ListTag();
        for (@NotNull final Map.Entry<BlockPos, BlockState> entry : wayPoints.entrySet())
        {
            @NotNull final CompoundTag wayPointCompound = new CompoundTag();
            BlockPosUtil.write(wayPointCompound, TAG_WAYPOINT, entry.getKey());
            wayPointCompound.put(TAG_BLOCK, NbtUtils.writeBlockState(entry.getValue()));
            wayPointTagList.add(wayPointCompound);
        }
        compound.put(TAG_WAYPOINT, wayPointTagList);

        // Free blocks
        @NotNull final ListTag freeBlocksTagList = new ListTag();
        for (@NotNull final Block block : freeBlocks)
        {
            freeBlocksTagList.add(StringTag.valueOf(BuiltInRegistries.BLOCK.getKey(block).toString()));
        }
        compound.put(TAG_FREE_BLOCKS, freeBlocksTagList);

        // Free positions
        @NotNull final ListTag freePositionsTagList = new ListTag();
        for (@NotNull final BlockPos pos : freePositions)
        {
            @NotNull final CompoundTag wayPointCompound = new CompoundTag();
            BlockPosUtil.write(wayPointCompound, TAG_FREE_POSITIONS, pos);
            freePositionsTagList.add(wayPointCompound);
        }
        compound.put(TAG_FREE_POSITIONS, freePositionsTagList);

        compound.putInt(TAG_ABANDONED, packageManager.getLastContactInHours());
        compound.put(TAG_REQUESTMANAGER, getRequestManager().serializeNBT(provider));
        compound.putString(TAG_PACK, pack);
        compound.putBoolean(TAG_AUTO_DELETE, canColonyBeAutoDeleted);
        compound.putBoolean(FreeMode.TAG_FREE_MODE, freeMode);
        compound.putBoolean(TAG_HOSTILE_TERRITORY, hostile);
        WorkOverride.write(compound, workOverrides);
        compound.putBoolean(TAG_BLAST_PROTECTION, blastProtection);
        compound.putBoolean(ColonyProtection.TAG_PROTECTION, protection);
        compound.putBoolean(KeepBuildings.TAG_KEEP_BUILDINGS, keepBuildings);
        // Writes nothing at all while every value is still the default, so an untuned colony's save is
        // byte-identical to one from a build without this feature.
        antiAirSettings.write(compound);
        compound.putInt(TAG_TEAM_COLOR, colonyTeamColor.ordinal());
        compound.put(TAG_FLAG_PATTERNS, Utils.serializeCodecMess(BannerPatternLayers.CODEC, provider, colonyFlag));
        compound.putLong(TAG_LAST_ONLINE, lastOnlineTime);
        compound.putString(TAG_COL_TEXT, textureStyle);
        compound.putString(TAG_COL_NAME_STYLE, nameStyle);
        compound.putInt(COLONY_DAY, day);

        if (forceLoadAllClaims != null)
        {
            compound.putBoolean(TAG_FORCE_LOAD_ALL_CLAIMS, forceLoadAllClaims);
        }

        final CompoundTag settings = new CompoundTag();
        settingsModule.serializeNBT(provider, settings);
        compound.put(BuildingModules.TOWNHALL_SETTINGS.key, settings);

        compound.put(TAG_TRAVELLING_DATA, travellingManager.serializeNBT(provider));
        compound.put(TAG_CONNECTION_MANAGER, connectionManager.serializeNBT(provider));

        @NotNull final ListTag claimTagList = new ListTag();
        for (final Long2ObjectMap.Entry<ChunkClaimData> chunkClaimData : claimData.long2ObjectEntrySet())
        {
            @NotNull final CompoundTag chunkCompound = new CompoundTag();
            chunkCompound.put(TAG_CHUNK_CLAIM, chunkClaimData.getValue().serializeNBT(provider));
            chunkCompound.putLong(TAG_CHUNK_POS, chunkClaimData.getLongKey());
            claimTagList.add(chunkCompound);
        }
        compound.put(TAG_CLAIM_DATA, claimTagList);

        this.colonyTag = compound;

        isDirty = false;
        return compound;
    }

    /**
     * Returns the dimension ID.
     *
     * @return Dimension ID.
     */
    public ResourceKey<Level> getDimension()
    {
        return dimensionId;
    }

    @Override
    public boolean isRemote()
    {
        return false;
    }

    @Override
    public IResearchManager getResearchManager()
    {
        return this.researchManager;
    }

    /**
     * When the Colony's world is loaded, associate with it.
     *
     * @param w World object.
     */
    @Override
    public void onWorldLoad(@NotNull final ServerLevel w)
    {
        if (w.dimension() == dimensionId)
        {
            this.world = w;
            // Register a new event handler
            if (eventHandler == null)
            {
                eventHandler = new ColonyPermissionEventHandler(this);
                questManager.onWorldLoad();
                // PORT(26.2): Fabric has no global event bus; ColonyPermissionEventHandler now hooks the
        // Fabric callbacks itself in its constructor.
        eventHandler.register();

                // Recovery for missing static colony claims. A hostile territory is exempt: it never had a static
                // claim to lose, because it is created without one on purpose, and reclaiming would hand it the
                // initialColonySize square around its centre that the whole design is about not giving it.
                final IChunkClaimData data = claimData.get(ChunkPos.containing(getCenter()).pack());
                if (!hostile && (data == null || !data.getStaticClaimColonies().contains(getID())))
                {
                    BackUpHelper.reclaimChunks(this);
                }
            }
            setColonyColor(this.colonyTeamColor);
            refreshTerritoryIndex();
        }
    }

    /**
     * Unsets the world if the world unloads.
     *
     * @param w World object.
     */
    @Override
    public void onWorldUnload(@NotNull final Level w)
    {
        if (w != world)
        {
            /*
             * If the event world is not the colony world ignore. This might happen in interactions with other mods.
             * This should not be a problem for minecolonies as long as we take care to do nothing in that moment.
             */
            return;
        }

        if (eventHandler != null)
        {
            eventHandler.unregister();
        }

        // The tickets die with the level: KEEP_LOADED_TYPE carries no FLAG_PERSIST, so nothing of ours is written to
        // the level's chunk_tickets saved data and nothing comes back when it loads again. Forgetting them here is what
        // lets the colony register them anew; keeping the stale set would make registerTicket believe every chunk was
        // already covered and quietly leave the colony unloaded for the rest of the run.
        ticketedChunks.clear();
        markTicketedChunksDirty();
        pendingChunks.clear();
        pendingToUnloadChunks.clear();
        forceLoadTimer = 0;
        world = null;

        if (hostile)
        {
            HostileTerritory.forget(w.dimension());
        }
    }

    /**
     * Empty in upstream 1.21.1 too (Colony.java:1106-1109). Because of that, this is no longer driven from
     * the server tick at all; the per-tick work lives in {@link #onWorldTick}. See FMLEventHandler#register.
     */
    @Override
    public void onServerTick(@NotNull final MinecraftServer server)
    {

    }

    /**
     * Get the Work Manager for the Colony.
     *
     * @return WorkManager for the Colony.
     */
    @Override
    @NotNull
    public IWorkManager getWorkManager()
    {
        return workManager;
    }

    /**
     * Get a copy of the freePositions list.
     *
     * @return the list of free to interact positions.
     */
    public Set<BlockPos> getFreePositions()
    {
        return freePositions;
    }

    /**
     * Get a copy of the freeBlocks list.
     *
     * @return the list of free to interact blocks.
     */
    public Set<Block> getFreeBlocks()
    {
        return freeBlocks;
    }

    /**
     * Add a new free to interact position.
     *
     * @param pos position to add.
     */
    public void addFreePosition(@NotNull final BlockPos pos)
    {
        ImmutableSet.Builder<BlockPos> builder = ImmutableSet.builder();
        builder.addAll(freePositions);
        builder.add(pos);
        freePositions = builder.build();
        markDirty();
    }

    /**
     * Add a new free to interact block.
     *
     * @param block block to add.
     */
    public void addFreeBlock(@NotNull final Block block)
    {
        ImmutableSet.Builder<Block> builder = ImmutableSet.builder();
        builder.addAll(freeBlocks);
        builder.add(block);
        freeBlocks = builder.build();
        markDirty();
    }

    /**
     * Remove a free to interact position.
     *
     * @param pos position to remove.
     */
    public void removeFreePosition(@NotNull final BlockPos pos)
    {
        ImmutableSet.Builder<BlockPos> builder = ImmutableSet.builder();
        for (final BlockPos tempPos : freePositions)
        {
            if (!pos.equals(tempPos))
            {
                builder.add(tempPos);
            }
        }
        freePositions = builder.build();
        markDirty();
    }

    /**
     * Remove a free to interact block.
     *
     * @param block state to remove.
     */
    public void removeFreeBlock(@NotNull final Block block)
    {
        ImmutableSet.Builder<Block> builder = ImmutableSet.builder();
        for (final Block tempBlock : freeBlocks)
        {
            if (block != tempBlock)
            {
                builder.add(tempBlock);
            }
        }
        freeBlocks = builder.build();
        markDirty();
    }

    /**
     * Any per-world-tick logic should be performed here. NOTE: If the Colony's world isn't loaded, it won't have a world tick. Use onServerTick for logic that should _always_
     * run.
     *
     * @param level the level being ticked
     */
    @Override
    public void onWorldTick(@NotNull final Level level)
    {
        if (level != getWorld())
        {
            /*
             * If the event world is not the colony world ignore. This might happen in interactions with other mods.
             * This should not be a problem for minecolonies as long as we take care to do nothing in that moment.
             */
            return;
        }

        if (hostile)
        {
            territoryTick(level);
            return;
        }

        if (!level.isClientSide() && (level.getGameTime() + id) % 20 == 0)
        {
            connectionManager.tick();
        }

        colonyStateMachine.tick();
    }

    /**
     * The whole of what a hostile territory does per tick, which is: hand its border to whoever is standing in it,
     * and only when it has changed.
     * <p>
     * A territory has no citizens, no buildings, no requests, no work orders, no waypoints, no travellers and nobody
     * to raid, so every transition of the ordinary state machine is work with no subject. It would do that work
     * anyway — {@code updateState} returns ACTIVE as soon as one player is a close subscriber, and a territory is by
     * design pressed up against the player's own colony, so somebody is nearly always standing in it. Hence the
     * bypass rather than a flag on each transition.
     * <p>
     * <b>What is deliberately kept.</b> {@code updateSubscribers} is the transition the red border depends on: it is
     * how a colony view, claim map and all, reaches the client at all. Skipping the tick wholesale would leave the
     * territory invisible, which would look exactly like a rendering bug and would be hunted in the wrong place.
     * <p>
     * <b>What it actually costs.</b> Once every {@code UPDATE_SUBSCRIBERS_INTERVAL} ticks: two iterations over the
     * subscriber sets, both empty when nobody is near. With somebody near, a view packet goes out only when
     * {@link #isDirty} — and since {@code updateState}, which is what sets that flag on an ordinary colony, is not
     * running here, the only thing that sets it is {@link #markDirty()}, i.e. an actual edit to the territory. So a
     * territory nobody is repainting sends nothing however many players stand in it. A player arriving is handled
     * without any tick at all: {@code ColonyPackageManager#addCloseSubscriber} pushes the first view itself, which is
     * also what makes this survive a save and reload — the view is not "pushed once when created", it is pushed to
     * whoever walks in, whenever that is.
     *
     * @param level the level being ticked.
     */
    private void territoryTick(@NotNull final Level level)
    {
        if (!level.isClientSide() && (level.getGameTime() + id) % UPDATE_SUBSCRIBERS_INTERVAL == 0)
        {
            packageManager.updateSubscribers();
        }
    }

    /**
     * Calculate randomly if the colony should update the citizens. By mean they update it at CLEANUP_TICK_INCREMENT.
     *
     * @param world        the world.
     * @param averageTicks the average ticks to upate it.
     * @return a boolean by random.
     */
    public static boolean shallUpdate(final Level world, final int averageTicks)
    {
        return world.getGameTime() % (world.getRandom().nextInt(averageTicks * 2) + 1) == 0;
    }

    /**
     * Update the waypoints after worldTicks.
     *
     * @return false
     */
    private boolean updateWayPoints()
    {
        if (!wayPoints.isEmpty() && world != null)
        {
            final int randomPos = world.getRandom().nextInt(wayPoints.size());
            int count = 0;
            for (final Map.Entry<BlockPos, BlockState> entry : wayPoints.entrySet())
            {
                if (count++ == randomPos)
                {
                    if (WorldUtil.isBlockLoaded(world, entry.getKey()))
                    {
                        final Block worldBlock = world.getBlockState(entry.getKey()).getBlock();
                        if (
                            ((worldBlock != (entry.getValue().getBlock()) && entry.getValue().getBlock() != ModBlocks.blockWayPoint)
                                && worldBlock != ModBlocks.blockConstructionTape)
                                || (world.isEmptyBlock(entry.getKey().below()) && !BlockUtils.isAnySolid(entry.getValue())))
                        {
                            wayPoints.remove(entry.getKey());
                            markDirty();
                        }
                    }
                    return false;
                }
            }
        }

        return false;
    }

    /**
     * Returns the center of the colony.
     *
     * @return Chunk Coordinates of the center of the colony.
     */
    @Override
    public BlockPos getCenter()
    {
        return center;
    }

    @Override
    public String getName()
    {
        return name;
    }

    /**
     * Sets the name of the colony. Marks dirty.
     *
     * @param n new name.
     */
    @Override
    public void setName(final String n)
    {
        name = n;
        markDirty();
    }

    @NotNull
    @Override
    public Permissions getPermissions()
    {
        return permissions;
    }

    @Override
    public boolean isCoordInColony(@NotNull final Level w, @NotNull final BlockPos pos)
    {
        if (w.dimension() != this.dimensionId)
        {
            return false;
        }


        final LevelChunk chunk = w.getChunkAt(pos);
        return ColonyUtils.getOwningColony(chunk, pos) == this.getID();
    }

    @Override
    public long getDistanceSquared(@NotNull final BlockPos pos)
    {
        return BlockPosUtil.getDistanceSquared2D(center, pos);
    }

    /**
     * Returns the ID of the colony.
     *
     * @return Colony ID.
     */
    @Override
    public int getID()
    {
        return id;
    }

    @Override
    public int getLastContactInHours()
    {
        return packageManager.getLastContactInHours();
    }

    /**
     * Returns the world the colony is in.
     *
     * @return World the colony is in.
     */
    @Nullable
    public ServerLevel getWorld()
    {
        return world;
    }

    @NotNull
    @Override
    public IRequestManager getRequestManager()
    {
        if (requestManager == null)
        {
            requestManager = new StandardRequestManager(this);
        }
        return requestManager;
    }

    /**
     * Marks the instance dirty.
     */
    public void markDirty()
    {
        packageManager.setDirty();
        isDirty = true;
        refreshTerritoryIndex();
    }

    @Override
    public boolean canBeAutoDeleted()
    {
        return canColonyBeAutoDeleted;
    }

    @Nullable
    @Override
    public IRequester getRequesterBuildingForPosition(@NotNull final BlockPos pos)
    {
        return buildingManager.getBuilding(pos);
    }

    @Override
    @NotNull
    public List<Player> getMessagePlayerEntities()
    {
        List<Player> players = new ArrayList<>();

        for (ServerPlayer player : packageManager.getCloseSubscribers())
        {
            if (permissions.hasPermission(player, Action.RECEIVE_MESSAGES))
            {
                players.add(player);
            }
        }

        return players;
    }

    @Override
    @NotNull
    public List<Player> getImportantMessageEntityPlayers()
    {
        final Set<Player> playerList = new HashSet<>(getMessagePlayerEntities());

        for (final ServerPlayer player : packageManager.getImportantColonyPlayers())
        {
            if (permissions.getRank(player).isColonyManager())
            {
                playerList.add(player);
            }
        }
        return new ArrayList<>(playerList);
    }

    /**
     * Send the message of a removed workOrder to the client.
     *
     * @param orderId the workOrder to remove.
     */
    public void removeWorkOrderInView(final int orderId)
    {
        //  Inform Subscribers of removed workOrder
        new ColonyViewRemoveWorkOrderMessage(this, orderId).sendToPlayer(packageManager.getCloseSubscribers());
    }

    /**
     * Adds a waypoint to the colony.
     *
     * @param point the waypoint to add.
     * @param block the block at the waypoint.
     */
    public void addWayPoint(final BlockPos point, final BlockState block)
    {
        wayPoints.put(point, block);
        this.markDirty();
    }

    /**
     * Getter for overall happiness.
     *
     * @return the overall happiness.
     */
    @Override
    public double getOverallHappiness()
    {
        // Reached from ColonyView#serializeNetworkData, i.e. once a second per colony while anyone is subscribed.
        // This used to call getCitizens() three times, and every one of those was a full ArrayList copy of the
        // citizen map. getCurrentCitizenCount() is citizens.size() and getCitizensUnmodifiable() is a view;
        // nothing in the loop mutates the citizen map (getHappiness only reads the happiness handler).
        final int citizenCount = citizenManager.getCurrentCitizenCount();
        if (citizenCount <= 0)
        {
            return 5.5;
        }

        double happinessSum = 0;
        for (final ICitizenData citizen : citizenManager.getCitizensUnmodifiable())
        {
            happinessSum += citizen.getCitizenHappinessHandler().getHappiness(citizen.getColony(), citizen);
        }
        return happinessSum / citizenCount;
    }

    /**
     * Get all the waypoints of the colony.
     *
     * @return copy of hashmap.
     */
    @Override
    public Map<BlockPos, BlockState> getWayPoints()
    {
        return new HashMap<>(wayPoints);
    }

    /**
     * This sets whether or not a colony can be automatically deleted Via command, or an on-tick check.
     *
     * @param canBeDeleted whether the colony is able to be deleted automatically
     */
    public void setCanBeAutoDeleted(final boolean canBeDeleted)
    {
        this.canColonyBeAutoDeleted = canBeDeleted;
        this.markDirty();
    }

    /**
     * Whether this colony works without any items at all. See {@link FreeMode}.
     *
     * @return true if free mode is on.
     */
    public boolean isFreeMode()
    {
        return freeMode;
    }

    /**
     * Turn free mode on or off for this colony. See {@link FreeMode}.
     *
     * @param freeMode whether the colony should work without any items at all.
     */
    public void setFreeMode(final boolean freeMode)
    {
        this.freeMode = freeMode;
        this.markDirty();
    }

    /**
     * Whether this colony is a hostile territory rather than a colony anybody lives in.
     *
     * @return true if it is enemy ground.
     */
    @Override
    public boolean isHostile()
    {
        return hostile;
    }

    /**
     * Make this colony a hostile territory, or stop it being one.
     *
     * @param hostile true for enemy ground.
     */
    public void setHostile(final boolean hostile)
    {
        this.hostile = hostile;
        this.markDirty();

        // Unconditionally, unlike markDirty's own refresh: switching the flag *off* has to take the colony's chunks
        // back out of the index, and the flag is already false by the time that refresh looks at it.
        if (world != null)
        {
            HostileTerritoryIndex.refresh(world);
        }
    }

    /**
     * Republish the outside world's view of where hostile ground is, after this colony's claim or flag changed.
     * <p>
     * Hung off {@link #markDirty()} rather than off each of the four or five places that edit a territory's claim,
     * because every one of them already has to mark the colony dirty to get the border onto the client — so this can
     * never be the thing somebody forgets. It costs nothing for an ordinary colony: one boolean test.
     */
    private void refreshTerritoryIndex()
    {
        if (hostile && world != null)
        {
            HostileTerritoryIndex.refresh(world);
        }
    }

    /**
     * Whether one of the "keep working anyway" switches is on for this colony. See {@link WorkOverride}.
     *
     * @param override the switch to ask about.
     * @return true if the switch is on.
     */
    public boolean isWorkOverrideOn(final WorkOverride override)
    {
        return workOverrides.contains(override);
    }

    /**
     * Turn one of the "keep working anyway" switches on or off for this colony. See {@link WorkOverride}.
     *
     * @param override the switch to throw.
     * @param on       whether it should be on.
     */
    public void setWorkOverride(final WorkOverride override, final boolean on)
    {
        if (on)
        {
            workOverrides.add(override);
        }
        else
        {
            workOverrides.remove(override);
        }
        this.markDirty();
    }

    /**
     * Whether this colony shields its claimed chunks from blasts at all.
     *
     * @return true if the server's explosion policy applies here.
     */
    public boolean isBlastProtection()
    {
        return blastProtection;
    }

    /**
     * Turn blast protection on or off for this colony.
     *
     * @param blastProtection whether the server's explosion policy should apply here.
     */
    public void setBlastProtection(final boolean blastProtection)
    {
        this.blastProtection = blastProtection;
        this.markDirty();
    }

    /**
     * Whether the sanity cleanup is forbidden to remove anything from this colony. See {@link KeepBuildings}.
     *
     * @return true if the cleanup must keep its hands off.
     */
    public boolean isKeepBuildings()
    {
        return keepBuildings;
    }

    /**
     * Hold the sanity cleanup back, or let it run again.
     *
     * @param keepBuildings true to stop it removing anything.
     */
    public void setKeepBuildings(final boolean keepBuildings)
    {
        this.keepBuildings = keepBuildings;
        this.markDirty();
    }

    /**
     * What this colony's anti-air batteries are tuned to.
     * <p>
     * Handed out live rather than copied, so the command mutates the colony's own block and the battery reads
     * the change on its next tick. Server side only: every reader — the battery, the emplacement listing, the
     * command — runs on the server, and a colony view never has one.
     *
     * @return the settings, never null and never partly filled in.
     */
    public AntiAirSettings getAntiAirSettings()
    {
        return antiAirSettings;
    }

    /**
     * Marks the colony as needing a save after its anti-air settings were changed.
     * <p>
     * Separate from the getter because the getter is also the read path, and marking the colony dirty on every
     * read would save it once a tick for ever.
     */
    public void markAntiAirDirty()
    {
        this.markDirty();
    }

    /**
     * Whether this colony enforces its permissions against players at all.
     *
     * @return true if permission checks apply here, which is the normal state.
     */
    public boolean isProtection()
    {
        return protection;
    }

    /**
     * Turn permission enforcement on or off for this colony.
     *
     * @param protection whether permission checks should apply here.
     */
    public void setProtection(final boolean protection)
    {
        this.protection = protection;
        this.markDirty();
    }

    /**
     * Getter for the default style of the colony.
     *
     * @return the style string.
     */
    @Override
    public String getStructurePack()
    {
        return pack;
    }

    /**
     * Setter for the default pack of the colony.
     *
     * @param style the default string.
     */
    @Override
    public void setStructurePack(final String style)
    {
        this.pack = style;
        this.markDirty();
    }

    /**
     * Get the buildingmanager of the colony.
     *
     * @return the buildingManager.
     */
    @Override
    public IRegisteredStructureManager getServerBuildingManager()
    {
        return buildingManager;
    }

    @Override
    public ICommonRegisteredStructureManager getCommonBuildingManager()
    {
        //todo merge with above.
        return buildingManager;
    }

    /**
     * Get the graveManager of the colony.
     *
     * @return the graveManager.
     */
    @Override
    public IGraveManager getGraveManager()
    {
        return graveManager;
    }

    /**
     * Get the citizenManager of the colony.
     *
     * @return the citizenManager.
     */
    @Override
    public ICitizenManager getCitizenManager()
    {
        return citizenManager;
    }

    /**
     * Get the visitor manager of the colony.
     *
     * @return the visitor manager.
     */
    @Override
    public IVisitorManager getVisitorManager()
    {
        return visitorManager;
    }

    /**
     * Get the animal manager of the colony.
     *
     * @return the animal manager.
     */
    @Override
    public IAnimalManager getAnimalManager()
    {
        return animalManager;
    }

    /**
     * Get the barbManager of the colony.
     *
     * @return the barbManager.
     */
    @Override
    public IRaiderManager getRaiderManager()
    {
        return raidManager;
    }

    @Override
    public IEventManager getEventManager()
    {
        return eventManager;
    }

    @Override
    public IStatisticsManager getStatisticsManager()
    {
        return statisticManager;
    }

    @Override
    public IReproductionManager getReproductionManager()
    {
        return reproductionManager;
    }

    @Override
    public IEventDescriptionManager getEventDescriptionManager()
    {
        return eventDescManager;
    }

    /**
     * Get the packagemanager of the colony.
     *
     * @return the manager.
     */
    @Override
    public IColonyPackageManager getPackageManager()
    {
        return packageManager;
    }

    @Override
    public TravellingManager getTravellingManager()
    {
        return travellingManager;
    }

    @Override
    public IColonyConnectionManager getConnectionManager()
    {
        return connectionManager;
    }

    /**
     * Get all visiting players.
     *
     * @return the list.
     */
    public ImmutableList<Player> getVisitingPlayers()
    {
        return ImmutableList.copyOf(visitingPlayers);
    }

    @Override
    public void addVisitingPlayer(final Player player)
    {
        final Rank rank = getPermissions().getRank(player);
        if (!rank.isColonyManager() && !visitingPlayers.contains(player) && settingsModule.getSetting(BuildingTownHall.ENTER_LEAVE_MESSAGES).getValue())
        {
            visitingPlayers.add(player);
            if (!this.getImportantMessageEntityPlayers().contains(player))
            {
                MessageUtils.format(ENTERING_COLONY_MESSAGE, this.getName()).sendTo(player);
            }

            final PlayerEnteringModEvent notifyPlayerEnteringModEvent = new PlayerEnteringModEvent(this, player);
            IMinecoloniesAPI.getInstance().getEventBus().post(notifyPlayerEnteringModEvent);

            if (notifyPlayerEnteringModEvent.shouldShowNotification())
            {
                MessageUtils.format(ENTERING_COLONY_MESSAGE_NOTIFY, player.getName()).sendTo(this, true).forManagers();
            }
        }
    }

    @Override
    public void removeVisitingPlayer(final Player player)
    {
        if (visitingPlayers.contains(player) && settingsModule.getSetting(BuildingTownHall.ENTER_LEAVE_MESSAGES).getValue())
        {
            visitingPlayers.remove(player);
            if (!this.getImportantMessageEntityPlayers().contains(player))
            {
                MessageUtils.format(LEAVING_COLONY_MESSAGE, this.getName()).sendTo(player);
            }

            final PlayerLeavingModEvent notifyPlayerLeavingModEvent = new PlayerLeavingModEvent(this, player);
            IMinecoloniesAPI.getInstance().getEventBus().post(notifyPlayerLeavingModEvent);

            if (notifyPlayerLeavingModEvent.shouldShowNotification())
            {
                MessageUtils.format(LEAVING_COLONY_MESSAGE_NOTIFY, player.getName()).sendTo(this, true).forManagers();
            }
        }
    }

    /**
     * Get the NBT tag of the colony.
     *
     * @return the tag of it.
     */
    @Override
    public CompoundTag getColonyTag()
    {
        try
        {
            if (this.colonyTag == null || this.isDirty)
            {
                this.write(new CompoundTag(), world.registryAccess());
            }
        }
        catch (final Exception e)
        {
            Log.getLogger().warn("Something went wrong persisting colony: " + id, e);
        }
        return this.colonyTag;
    }

    /**
     * Is player part of a wave trying to invade the colony?
     *
     * @param player the player to check..
     * @return true if so.
     */
    public boolean isValidAttackingPlayer(final Player player)
    {
        if (packageManager.getLastContactInHours() > 1)
        {
            return false;
        }

        for (final AttackingPlayer attackingPlayer : attackingPlayers)
        {
            if (attackingPlayer.getPlayer().equals(player))
            {
                return attackingPlayer.isValidAttack(this);
            }
        }
        return false;
    }

    /**
     * Check if attack of guard is valid.
     *
     * @param entity the guard entity.
     * @return true if so.
     */
    public boolean isValidAttackingGuard(final AbstractEntityCitizen entity)
    {
        if (packageManager.getLastContactInHours() > 1)
        {
            return false;
        }

        return AttackingPlayer.isValidAttack(entity, this);
    }

    /**
     * Add a guard to the list of attacking guards.
     *
     * @param IEntityCitizen the citizen to add.
     */
    public void addGuardToAttackers(final AbstractEntityCitizen IEntityCitizen, final Player player)
    {
        if (player == null)
        {
            return;
        }

        for (final AttackingPlayer attackingPlayer : attackingPlayers)
        {
            if (attackingPlayer.getPlayer().equals(player))
            {
                if (attackingPlayer.addGuard(IEntityCitizen))
                {
                    MessageUtils.format(COLONY_ATTACK_GUARD_GROUP_SIZE_MESSAGE, attackingPlayer.getPlayer().getName(), attackingPlayer.getGuards().size())
                        .sendTo(this)
                        .forManagers();
                }
                return;
            }
        }

        for (final Player visitingPlayer : visitingPlayers)
        {
            if (visitingPlayer.equals(player))
            {
                final AttackingPlayer attackingPlayer = new AttackingPlayer(visitingPlayer);
                attackingPlayer.addGuard(IEntityCitizen);
                attackingPlayers.add(attackingPlayer);
                MessageUtils.format(COLONY_ATTACK_START_MESSAGE, visitingPlayer.getName()).sendTo(this).forManagers();
            }
        }
    }

    /**
     * Check if the colony is currently under attack by another player.
     *
     * @return true if so.
     */
    public boolean isColonyUnderAttack()
    {
        return !attackingPlayers.isEmpty();
    }

    /**
     * Getter for the colony team color.
     *
     * @return the ChatFormatting enum color.
     */
    @Override
    public ChatFormatting getTeamColonyColor()
    {
        return colonyTeamColor;
    }

    /**
     * Getter for the colony flag patterns
     *
     * @return the list of pattern-color pairs
     */
    @Override
    public BannerPatternLayers getColonyFlag()
    {
        return colonyFlag;
    }

    /**
     * Set the colony to be dirty.
     *
     * @param dirty if dirty.
     */
    public void setDirty(final boolean dirty)
    {
        this.isDirty = dirty;
    }

    /**
     * Save the time when mercenaries are used, to set a cooldown.
     */
    @Override
    public void usedMercenaries()
    {
        mercenaryLastUse = world.getGameTime();
        markDirty();
    }

    /**
     * Get the last time mercenaries were used.
     */
    @Override
    public long getMercenaryUseTime()
    {
        return mercenaryLastUse;
    }

    @Override
    public boolean useAdditionalChildTime(final int amount)
    {
        if (additionalChildTime < amount)
        {
            return false;
        }
        else
        {
            additionalChildTime -= amount;
            return true;
        }
    }

    @Override
    public void updateHasChilds()
    {
        for (ICitizenData data : this.getCitizenManager().getCitizens())
        {
            if (data.isChild())
            {
                this.hasChilds = true;
                return;
            }
        }
        this.hasChilds = false;
    }

    @Override
    public boolean hasChilds()
    {
        return hasChilds;
    }

    @Override
    public void addLoadedChunk(final long chunkPos, final LevelChunk chunk)
    {
        if (world instanceof ServerLevel
            && getConfig().getServer().forceLoadColony.get())
        {
            if (this.forceLoadTimer > 0)
            {
                checkChunkAndRegisterTicket(chunkPos, chunk);
            }
            else if (buildingManager.keepChunkColonyLoaded(chunk))
            {
                this.pendingChunks.add(chunkPos);
            }
        }
        this.loadedChunks.put(chunkPos, chunkPos);
    }

    @Override
    public void removeLoadedChunk(final long chunkPos)
    {
        loadedChunks.remove(chunkPos);
        pendingToUnloadChunks.remove(chunkPos);
    }

    @Override
    public int getLoadedChunkCount()
    {
        return loadedChunks.size();
    }

    @Override
    public Set<Long> getLoadedChunks()
    {
        return loadedChunks.keySet();
    }

    @Override
    public ColonyState getState()
    {
        return colonyStateMachine.getState();
    }

    @Override
    public boolean isActive()
    {
        return colonyStateMachine.getState() != INACTIVE;
    }

    @Override
    public boolean isDay()
    {
        return isDay;
    }

    @Override
    public Set<Long> getTicketedChunks()
    {
        return ticketedChunks;
    }

    @Override
    public void setTextureStyle(final String style)
    {
        this.textureStyle = style;
        this.markDirty();
    }

    @Override
    public String getTextureStyleId()
    {
        return this.textureStyle;
    }

    @Override
    public void setNameStyle(final String style)
    {
        this.nameStyle = style;
        this.markDirty();
    }

    @Override
    public String getNameStyle()
    {
        return this.nameStyle;
    }

    @Override
    public CitizenNameFile getCitizenNameFile()
    {
        return CitizenNameListener.nameFileMap.getOrDefault(nameStyle, CitizenNameListener.nameFileMap.get("default"));
    }

    /**
     * Check if we need to update the view's chunk ticket info
     *
     * @return true if dirty.
     */
    public boolean isTicketedChunksDirty()
    {
        return ticketedChunksDirty;
    }

    /**
     * Record that the ticketed chunk set changed, and make sure a colony view packet actually goes out to carry it.
     * <p>
     * The flag alone is not enough: {@code ColonyPackageManager#sendColonyViewPackets} only builds a packet when the
     * colony is dirty or someone just subscribed, so a ticket added on a quiet tick would set the flag and then wait
     * for some unrelated change to push it. Marking the package manager dirty here is what pairs the flag with a send,
     * and it is what makes clearing the flag on send safe.
     */
    private void markTicketedChunksDirty()
    {
        ticketedChunksDirty = true;
        packageManager.setDirty();
    }

    /**
     * Forget that the ticketed chunk set changed.
     * <p>
     * Called by {@code ColonyPackageManager} once the buffer holding the set has gone out to every close subscriber --
     * and only then. There is one buffer per update for the whole colony, not one per player, so this is a colony-wide
     * flag and not a per-subscriber one; a player who subscribes later is served by the {@code hasNewSubscribers}
     * branch in {@code ColonyView#serializeNetworkData}, which writes the full set regardless of this flag.
     * <p>
     * Until this existed the flag was set once at construction and never cleared, so the whole ticketed chunk set --
     * over 1500 longs on a large colony -- was written into every colony view packet, roughly once a second per
     * colony, forever.
     */
    public void clearTicketedChunksDirty()
    {
        ticketedChunksDirty = false;
    }

    @Override
    public int getDay()
    {
        return day;
    }

    @Override
    public IQuestManager getQuestManager()
    {
        return questManager;
    }

    @Override
    public ICitizen getCitizen(final int id)
    {
        return citizenManager.getCivilian(id);
    }

    @Override
    public ISettingsModule getSettings()
    {
        return settingsModule;
    }

    /**
     * Get the claim data from the colony.
     * @return the claim data map.
     */
    public Long2ObjectMap<ChunkClaimData> getClaimData()
    {
        return claimData;
    }

    public IChunkClaimData claimNewChunk(final ChunkPos pos)
    {
        final ChunkClaimData chunkClaimData = new ChunkClaimData();
        claimData.put(pos.pack(), chunkClaimData);
        IColonyManager.getInstance().addNewChunk(this, pos, chunkClaimData);
        this.markDirty();
        return chunkClaimData;
    }

    /**
     * Sets the dimension ID, use with care!
     *
     * @param dimensionId
     */
    public void setDimensionId(final ResourceKey<Level> dimensionId)
    {
        this.dimensionId = dimensionId;
    }
}
