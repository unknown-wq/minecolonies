package com.unknownwq.worldmap.colony.minecolonies;

import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.colony.permissions.Action;
import com.unknownwq.worldmap.WorldMapClient;
import com.unknownwq.worldmap.colony.ChunkOutline;
import com.unknownwq.worldmap.colony.ColonyMemory;
import com.unknownwq.worldmap.colony.ColonyOverlay;
import com.unknownwq.worldmap.colony.ColonySnapshot;
import com.unknownwq.worldmap.colony.ColonySnapshots;
import com.unknownwq.worldmap.colony.ColonyStore;
import com.unknownwq.worldmap.map.MapService;
import com.unknownwq.worldmap.map.TileKey;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The only class in this mod that names a {@code com.minecolonies} type, apart from {@link GuardPatrols}
 * next door.
 *
 * <p>Everything it knows how to do is turn colony objects into {@link ColonyMemory} records -- plain
 * positions, colours and strings -- and hand those to {@link ColonySnapshots}, which turns them into what
 * the screen draws. Nothing MineColonies-shaped crosses that line, which is what lets the rest of the mod be
 * compiled, loaded and run on an installation that has never heard of colonies.</p>
 *
 * <h2>Live borders, and only live borders</h2>
 * <p>Claims change while you play: a builder finishes a hut on the rim and the colony grows a chunk, a
 * colony is abandoned and a hole appears in the middle of its neighbour. So the claim map is re-read once a
 * second and the shapes rebuilt from it, and this is the <b>one</b> thing on this map that refreshes by
 * itself -- surface pixels are still scanned once per chunk and never revisited, and nothing here touches
 * that.</p>
 *
 * <p>The re-read is batched, for the reason
 * {@code core/compatibility/journeymap/ColonyBorderMapping.java} batches it: on a populated server the claim
 * map has tens of thousands of entries in it and walking all of them inside one client tick is a visible
 * stutter. {@value #UPDATES_PER_TICK} is that file's number, kept deliberately. A poll that has not finished
 * draining blocks the next poll rather than queueing a second copy of the same work behind it.</p>
 *
 * <h2>Memory, and why live always wins</h2>
 * <p>Everything the map knows about a colony is kept in one {@link ColonyMemory} per colony id, seeded from
 * {@link ColonyStore} when the world or the dimension changes and then overwritten, colony by colony, as
 * live data arrives. Three rules keep the stale copy out of the way of the fresh one:</p>
 * <ul>
 *   <li>A colony with live claim data is <b>live</b>, and its claimed chunks, name, colour and centre come
 *       from the live view. Nothing off disk can overrule them.</li>
 *   <li>A live colony whose <em>view</em> has not arrived yet -- claims are on the client, the view is not --
 *       keeps the remembered name and colour rather than being drawn as an anonymous grey blob. That is
 *       filling a hole, not overwriting anything.</li>
 *   <li>An <b>empty</b> live list never replaces a non-empty remembered one. Building extensions in
 *       particular are pushed only to nearby subscribers, so a colony you have not stood in this session
 *       reports zero fields, and taking that literally would erase a good record every second.</li>
 * </ul>
 *
 * <p>A colony that is <em>not</em> in the live claim map is drawn as remembered, and is never deleted
 * automatically. It cannot be: the live claim map only ever contains colonies somebody is standing in, so
 * "deleted while you were away" and "out of range" are the same observation. The player forgets one
 * explicitly from the map's right-click menu -- see {@link #forgetColony}.</p>
 *
 * <h2>What is read, and from where</h2>
 * <ul>
 *   <li>chunk ownership -- {@code IColonyManager#getClaimData(dimension)}, a map of {@link ChunkPos} to
 *       {@link IChunkClaimData}, whose {@code getOwningColony()} is the colony id or 0 for nobody. The map
 *       is populated on the client, which is the whole reason this integration can exist at all.</li>
 *   <li>colour -- {@code IColony#getTeamColonyColor()}, the colour MineColonies has <em>already</em> given
 *       that colony and shows on its banners and its citizens. The map does not invent a second palette.</li>
 *   <li>name, centre, id -- the colony view.</li>
 *   <li>raid state -- {@code IColonyView#isRaiding()}, {@code IColonyView#getLastSpawnPoints()} and
 *       {@code IColonyView#getLastRaidTime()}, all three written into the view packet by
 *       {@code ColonyView#serializeNetworkData}. The positions are the <em>last</em> ones and say nothing on
 *       their own about whether a raid is running; {@code isRaiding()} answers that, and the raid time dates
 *       them, so a point left over from four in-game days ago is labelled as one instead of being drawn like
 *       an attack in progress.</li>
 *   <li>hostility -- {@code IColony#isHostile()}, also a synced field on the view.
 *       {@code HostileTerritory} and its index are deliberately <b>not</b> read: that index is built from a
 *       {@code ServerLevel} on the server thread, so it would appear to work in single player and quietly
 *       produce nothing on a dedicated server.</li>
 *   <li>huts, fields, patrol routes, graves and waypoints -- the colony view again, and only for colonies
 *       the player belongs to: the client is not sent anybody else's building list, so there is nothing to
 *       draw for them and no point pretending otherwise.</li>
 * </ul>
 *
 * <p>{@code Action.MAP_BORDER} and {@code Action.MAP_DEATHS} are honoured. They are the permissions a colony
 * uses to say who may see its outline and its graves on a map, they already exist for exactly this purpose,
 * and a map that ignored them would be a wallhack with extra steps. A colony that revokes {@code MAP_BORDER}
 * is dropped from the remembered list too, so the permission cannot be worked around by having seen the
 * colony yesterday.</p>
 */
@Environment(EnvType.CLIENT)
public final class MineColoniesOverlay implements ColonyOverlay
{
    /**
     * Claim entries applied per client tick. Straight out of
     * {@code ColonyBorderMapping.UPDATES_PER_TICK}, which arrived at it against real colonies.
     */
    private static final int UPDATES_PER_TICK = 250;

    /**
     * Ticks between re-reads of the claim map. One second: fast enough that a chunk claimed in front of you
     * appears while you are still looking at it, slow enough that the copy costs nothing measurable.
     */
    private static final int POLL_TICKS = 20;

    /**
     * Ticks between flushes of the remembered colony file. Thirty seconds: the file is written on a
     * background thread and is rewritten whole each time, so there is no reason to do it often, and the
     * worst a crash can cost is half a minute of "last seen".
     */
    private static final int SAVE_TICKS = 600;

    /**
     * A claim map larger than this is not walked at all. Nothing in vanilla MineColonies produces one --
     * it would be a quarter of a million claimed chunks in a single dimension -- but a map that quietly
     * stops drawing borders is a better failure than one that hitches every second on a server that has.
     */
    private static final int MAX_CLAIMS = 250_000;

    /**
     * Fallback for a colony whose view has not arrived and which is not remembered either, so its name and
     * team colour are not known. Its chunks are real and are drawn; the grey says "claimed, by somebody"
     * honestly.
     */
    private static final int UNKNOWN_COLOUR = 0x9AA0A8;

    private volatile ColonySnapshot snapshot = ColonySnapshot.EMPTY;

    /**
     * Colony id to its claimed chunks, for the dimension in {@link #dimension}. Client thread only.
     */
    private final Map<Integer, Set<ChunkPos>> claims = new HashMap<>();

    /**
     * Everything the map knows about a colony in this dimension, live or off disk, keyed by colony id.
     * Insertion-ordered so the file does not churn between saves for no reason. Client thread only.
     */
    private final Map<Integer, ColonyMemory> memory = new LinkedHashMap<>();

    /**
     * Claim entries read but not yet applied. Client thread only.
     */
    private final ArrayDeque<ChunkOwnership> pending = new ArrayDeque<>();

    private ResourceKey<Level> dimension;
    private String dimensionName = "";
    private String worldKey = "";
    private ColonyStore store;

    private boolean claimsChanged;
    private boolean memoryChanged;
    private int ticksSincePoll;
    private int ticksSinceRebuild;
    private int ticksSinceSave;
    private boolean warnedAboutSize;

    @Override
    public boolean isActive()
    {
        return true;
    }

    @Override
    public ColonySnapshot snapshot()
    {
        return this.snapshot;
    }

    @Override
    public void clear()
    {
        this.flushMemory(true);
        this.claims.clear();
        this.memory.clear();
        this.pending.clear();
        this.dimension = null;
        this.dimensionName = "";
        this.worldKey = "";
        this.store = null;
        this.snapshot = ColonySnapshot.EMPTY;
    }

    @Override
    public void tick(final Minecraft minecraft)
    {
        final ClientLevel level = minecraft.level;
        final LocalPlayer player = minecraft.player;
        if (level == null || player == null)
        {
            if (this.dimension != null)
            {
                this.clear();
            }
            return;
        }

        try
        {
            this.followWorld(level);
            this.pump(minecraft);
        }
        catch (final Throwable t)
        {
            // A colony overlay that throws must not take the map's tick with it. Drop what is half-applied
            // and start the next poll from scratch: the claim map is the source of truth, not this cache.
            // The remembered records are left alone -- they are the half of this that cannot be re-read.
            WorldMapClient.LOGGER.warn("Colony overlay recovered from an error; borders may be a second stale", t);
            this.pending.clear();
            this.claims.clear();
            this.claimsChanged = true;
        }
    }

    /**
     * Notices a world or dimension change and swaps the remembered set over.
     *
     * <p>Both matter, and for different reasons. A different world is a different save directory and must
     * never see the last one's colonies. A different dimension is a different file inside the same
     * directory, because colony ids are per-dimension in the claim map and drawing the Nether's colonies on
     * the Overworld would be nonsense.</p>
     */
    private void followWorld(final ClientLevel level)
    {
        final MapService service = WorldMapClient.service();
        final String key = service == null ? "unknown" : service.worldKey();
        final String dim = TileKey.dimensionName(level.dimension());

        if (key.equals(this.worldKey) && dim.equals(this.dimensionName))
        {
            return;
        }

        this.flushMemory(true);
        this.claims.clear();
        this.memory.clear();
        this.pending.clear();
        this.snapshot = ColonySnapshot.EMPTY;

        this.worldKey = key;
        this.dimensionName = dim;
        this.dimension = level.dimension();
        this.store = storeFor(service, key);

        if (this.store != null)
        {
            for (final ColonyMemory remembered : this.store.load(dim))
            {
                this.memory.put(remembered.id(), remembered);
            }
            if (!this.memory.isEmpty())
            {
                WorldMapClient.LOGGER.info("Remembered {} colonies in {}", this.memory.size(), dim);
            }
        }

        this.claimsChanged = true;
        this.memoryChanged = false;
        this.ticksSincePoll = POLL_TICKS;
        this.ticksSinceSave = 0;
    }

    private static ColonyStore storeFor(final MapService service, final String worldKey)
    {
        if (service == null || worldKey.isEmpty())
        {
            return null;
        }
        final Path root = service.storeRoot();
        return root == null ? null : new ColonyStore(root.resolve(worldKey));
    }

    private void pump(final Minecraft minecraft)
    {
        this.ticksSincePoll++;
        this.ticksSinceRebuild++;
        this.ticksSinceSave++;

        if (this.pending.isEmpty() && this.ticksSincePoll >= POLL_TICKS)
        {
            this.ticksSincePoll = 0;
            this.poll();
        }

        for (int i = 0; i < UPDATES_PER_TICK && !this.pending.isEmpty(); i++)
        {
            final ChunkOwnership entry = this.pending.poll();
            this.claimsChanged |= this.apply(entry.pos(), entry.id());
        }

        if (!this.pending.isEmpty())
        {
            return;
        }

        // Colonies, huts and graves change on their own schedule, so the snapshot is rebuilt on a timer as
        // well as whenever a claim moved. Both are once a second at worst, and rebuilding is a few
        // thousand set lookups.
        if (this.claimsChanged || this.ticksSinceRebuild >= POLL_TICKS)
        {
            this.claimsChanged = false;
            this.ticksSinceRebuild = 0;
            this.snapshot = this.build(minecraft);
        }

        if (this.memoryChanged && this.ticksSinceSave >= SAVE_TICKS)
        {
            this.flushMemory(false);
        }
    }

    /**
     * Reads the whole claim map for this dimension into {@link #pending}.
     *
     * <p>Every chunk this cache holds is queued too, as unowned, before the real entries: that is what makes
     * a claim that has <em>gone away</em> disappear from the map. Without it a colony could only ever grow
     * on screen, because a chunk it no longer owns is simply absent from the map being read and nothing
     * would ever notice.</p>
     */
    private void poll()
    {
        final Map<ChunkPos, IChunkClaimData> data = IColonyManager.getInstance().getClaimData(this.dimension);
        if (data.size() > MAX_CLAIMS)
        {
            if (!this.warnedAboutSize)
            {
                this.warnedAboutSize = true;
                WorldMapClient.LOGGER.warn("{} claimed chunks in {} -- colony borders are switched off for this world",
                  data.size(), this.dimension.identifier());
            }
            return;
        }

        for (final Set<ChunkPos> owned : this.claims.values())
        {
            for (final ChunkPos pos : owned)
            {
                this.pending.add(new ChunkOwnership(pos, 0));
            }
        }
        for (final Map.Entry<ChunkPos, IChunkClaimData> entry : data.entrySet())
        {
            final IChunkClaimData claim = entry.getValue();
            if (claim != null)
            {
                this.pending.add(new ChunkOwnership(entry.getKey(), claim.getOwningColony()));
            }
        }
    }

    /**
     * @return true if the cache actually changed.
     */
    private boolean apply(final ChunkPos pos, final int id)
    {
        if (id == 0)
        {
            boolean changed = false;
            for (final Set<ChunkPos> owned : this.claims.values())
            {
                changed |= owned.remove(pos);
            }
            return changed;
        }
        return this.claims.computeIfAbsent(id, k -> new HashSet<>()).add(pos);
    }

    private ColonySnapshot build(final Minecraft minecraft)
    {
        final LocalPlayer player = minecraft.player;
        if (player == null || this.dimension == null)
        {
            return ColonySnapshot.EMPTY;
        }

        final IColonyManager manager = IColonyManager.getInstance();
        final Set<Integer> live = new HashSet<>();
        final Set<Integer> raiding = new HashSet<>();
        final Map<BlockPos, List<String>> workers = new HashMap<>();
        final List<ColonySnapshot.PointMarker> deaths = new ArrayList<>();
        final List<ColonySnapshot.PointMarker> waypoints = new ArrayList<>();
        final long now = System.currentTimeMillis();

        for (final Map.Entry<Integer, Set<ChunkPos>> entry : this.claims.entrySet())
        {
            final Set<ChunkPos> chunks = entry.getValue();
            if (chunks.isEmpty())
            {
                continue;
            }

            final int id = entry.getKey();
            final IColonyView colony = manager.getColonyView(id, this.dimension);

            if (colony != null && !colony.getPermissions().hasPermission(player, Action.MAP_BORDER))
            {
                // The colony says this player may not see its outline. That is what the permission is for,
                // and a record from before it was revoked would be a way round it, so that goes too.
                if (this.memory.remove(id) != null)
                {
                    this.memoryChanged = true;
                }
                continue;
            }

            live.add(id);
            final ColonyMemory previous = this.memory.get(id);
            this.memory.put(id, merge(colony, player, id, chunks, previous, now));

            // Marked here rather than after the null check below, so a colony the client has claims for but
            // no view of is written out too. That is a real case on a server -- chunk claims arrive with the
            // chunk, the view only when the server decides you are close enough -- and the shape of a border
            // with no name on it is still a record of somewhere you have been.
            this.memoryChanged = true;

            if (colony == null)
            {
                continue;
            }

            if (colony.isRaiding())
            {
                raiding.add(id);
            }

            if (!colony.getPermissions().isColonyMember(player))
            {
                // Only a colony the player belongs to sends its building list, its graves and its waypoints
                // to the client.
                continue;
            }

            collectWorkers(colony, workers);

            if (colony.getPermissions().hasPermission(player, Action.MAP_DEATHS))
            {
                for (final BlockPos grave : colony.getGraveManager().getGraves().keySet())
                {
                    deaths.add(new ColonySnapshot.PointMarker(grave, 0xBFBFBF,
                      Component.translatable("gui.worldmap.colony.grave", colony.getName()).getString(), null));
                }
            }

            for (final BlockPos waypoint : colony.getWayPoints().keySet())
            {
                waypoints.add(new ColonySnapshot.PointMarker(waypoint, teamColour(colony),
                  Component.translatable("gui.worldmap.colony.waypoint", colony.getName()).getString(), null));
            }
        }

        return ColonySnapshots.build(this.memory.values(), live, raiding, workers, deaths, waypoints,
          minecraft.level == null ? 0L : minecraft.level.getGameTime());
    }

    /**
     * Folds one colony's live state into what is already remembered about it.
     *
     * <p>The live claim set always wins -- it is the reason this colony is in the loop at all. Everything
     * else follows the rule in the class notes: live data replaces remembered data, and the absence of live
     * data does not.</p>
     *
     * @param colony   the colony view, or null when only its claims have reached the client.
     * @param player   the client's player, for the membership test.
     * @param id       the colony id.
     * @param chunks   its live claimed chunks.
     * @param previous what was remembered, or null.
     * @param now      epoch milliseconds, for the last-seen stamp.
     * @return the merged record.
     */
    private static ColonyMemory merge(
      final IColonyView colony,
      final LocalPlayer player,
      final int id,
      final Set<ChunkPos> chunks,
      final ColonyMemory previous,
      final long now)
    {
        final long[] packed = ChunkOutline.pack(chunks);

        if (colony == null)
        {
            // Claims but no view. Keep whatever the record already said about the colony itself and give it
            // the fresh outline; a remembered name is a better answer than a grey blob, and it is not
            // overwriting anything, because there is nothing live to overwrite it with.
            if (previous != null)
            {
                return new ColonyMemory(id, previous.name(), previous.citizens(), previous.colour(),
                  previous.own(), previous.hostile(), previous.centre(), packed, previous.lastSeen(),
                  previous.huts(), previous.fields(), previous.patrols(), previous.raiderSpawns(),
                  previous.lastRaidTime());
            }
            return new ColonyMemory(id, "", -1, UNKNOWN_COLOUR, false, false, centreOf(chunks), packed, 0L,
              List.of(), List.of(), List.of(), List.of(), ColonyMemory.NO_RAID_TIME);
        }

        final int colour = teamColour(colony);
        final boolean own = colony.getPermissions().isColonyMember(player);

        List<ColonyMemory.Hut> huts = List.of();
        List<ColonyMemory.Field> fields = List.of();
        List<ColonyMemory.Patrol> patrols = List.of();
        if (own)
        {
            huts = collectHuts(colony);
            fields = collectFields(colony);
            patrols = collectPatrols(colony);
        }

        List<BlockPos> spawns = List.copyOf(colony.getLastSpawnPoints());
        long raidTime = colony.getLastRaidTime();

        // An empty live list usually means "not sent to you yet" rather than "there are none". The colony
        // view arrives as soon as its claims do; the building list and the building extensions are pushed
        // only to close subscribers, so a colony whose border you can see but whose ground you have not
        // stood on this session reports no huts and no fields, and taking that literally would erase a good
        // record every second.
        //
        // The hut list is the signal for which of the two it is. MineColonies sends the buildings and the
        // extensions to the same audience in the same pass (RegisteredStructureManager#sendPackets and
        // #sendBuildingExtensionPackets, both against closeSubscribers plus newSubscribers), so a non-empty
        // hut list means this client is a close subscriber and an empty field list from the same colony is
        // the truth -- the last field really was removed, and the marker should go. With no huts either,
        // nothing has been sent and the remembered lists stand.
        if (previous != null)
        {
            final boolean subscribed = !huts.isEmpty();
            huts = huts.isEmpty() ? previous.huts() : huts;
            fields = fields.isEmpty() && !subscribed ? previous.fields() : fields;
            patrols = patrols.isEmpty() && !subscribed ? previous.patrols() : patrols;
            // The time belongs to the points, so the two move together: falling back to the remembered points and
            // keeping a live time from a raid they are not the spawn of would date them wrongly.
            if (spawns.isEmpty())
            {
                spawns = previous.raiderSpawns();
                raidTime = previous.lastRaidTime();
            }
        }

        return new ColonyMemory(id, colony.getName(), colony.getCitizens().size(), colour, own,
          colony.isHostile(), colony.getCenter(), packed, now, huts, fields, patrols, spawns, raidTime);
    }

    /**
     * Who is working in which hut, for the tooltip.
     *
     * <p>Kept out of {@link ColonyMemory} and rebuilt every second instead, because it is the one part of a
     * hut that is a fact about right now rather than about the ground. A remembered hut that still named a
     * worker who left months ago would be worse than one that names nobody.</p>
     */
    private static void collectWorkers(final IColonyView colony, final Map<BlockPos, List<String>> out)
    {
        for (final Map.Entry<BlockPos, IBuildingView> entry : colony.getClientBuildingManager().getBuildings().entrySet())
        {
            final IBuildingView building = entry.getValue();
            if (building == null)
            {
                continue;
            }
            final List<String> names = new ArrayList<>();
            for (final int citizenId : building.getAllAssignedCitizens())
            {
                final ICitizenDataView citizen = colony.getCitizens().get(citizenId);
                if (citizen != null)
                {
                    final String job = citizen.getJob();
                    names.add(job == null || job.isEmpty()
                                ? citizen.getName()
                                : citizen.getName() + " - " + Component.translatable(job).getString());
                }
            }
            if (!names.isEmpty())
            {
                out.put(entry.getKey(), List.copyOf(names));
            }
        }
    }

    private static List<ColonyMemory.Hut> collectHuts(final IColonyView colony)
    {
        final List<ColonyMemory.Hut> out = new ArrayList<>();
        for (final Map.Entry<BlockPos, IBuildingView> entry : colony.getClientBuildingManager().getBuildings().entrySet())
        {
            final IBuildingView building = entry.getValue();
            if (building == null)
            {
                continue;
            }
            final BuildingEntry type = building.getBuildingType();
            out.add(new ColonyMemory.Hut(
              entry.getKey(),
              iconIdFor(type),
              displayName(building, type),
              building.getBuildingLevel(),
              building.getBuildingMaxLevel(),
              building.hasWorkOrder()));
        }
        return List.copyOf(out);
    }

    /**
     * Farmer and plantation fields.
     *
     * <p>{@code getBuildingExtensions} is the client-side list, filled in by
     * {@code ColonyViewBuildingExtensionsUpdateMessage}, which the server sends to close subscribers. So it
     * is empty until the player has been in the colony this session, and the caller treats an empty list as
     * "not sent yet" rather than "none".</p>
     */
    private static List<ColonyMemory.Field> collectFields(final IColonyView colony)
    {
        final List<ColonyMemory.Field> out = new ArrayList<>();
        for (final IBuildingExtension extension : colony.getClientBuildingManager().getBuildingExtensions(x -> true))
        {
            if (extension == null)
            {
                continue;
            }
            out.add(new ColonyMemory.Field(
              extension.getPosition(),
              extension.isTaken(),
              typePathOf(extension),
              extension.getBuildingId()));
        }
        return List.copyOf(out);
    }

    private static String typePathOf(final IBuildingExtension extension)
    {
        try
        {
            final Identifier name = extension.getBuildingExtensionType().getRegistryName();
            return name == null ? "" : name.getPath();
        }
        catch (final RuntimeException e)
        {
            return "";
        }
    }

    /**
     * Manual guard patrol routes, one per tower that has any. See {@link GuardPatrols} for why the read is
     * behind its own class.
     */
    private static List<ColonyMemory.Patrol> collectPatrols(final IColonyView colony)
    {
        final List<ColonyMemory.Patrol> out = new ArrayList<>();
        for (final Map.Entry<BlockPos, IBuildingView> entry : colony.getClientBuildingManager().getBuildings().entrySet())
        {
            final List<BlockPos> targets = GuardPatrols.targetsOf(entry.getValue());
            if (targets.size() >= 2)
            {
                out.add(new ColonyMemory.Patrol(entry.getKey(), List.copyOf(targets)));
            }
        }
        return List.copyOf(out);
    }

    /**
     * The hut block's item id, which is what the icon is looked back up by.
     *
     * <p>MineColonies already ships a distinct block, model and texture for every hut, and players
     * recognise them: the builder's hut icon on this map is the same picture as the builder's hut in their
     * hand. Drawing a made-up glyph set instead would be more work and less legible. Storing the registry id
     * rather than the stack is what lets a remembered hut keep its icon across a restart.</p>
     */
    private static String iconIdFor(final BuildingEntry type)
    {
        if (type == null)
        {
            return "";
        }
        final Block block = type.getBuildingBlock();
        if (block == null)
        {
            return "";
        }
        final Item item = block.asItem();
        final Identifier id = item == null ? null : BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.toString();
    }

    private static String displayName(final IBuildingView building, final BuildingEntry type)
    {
        final String custom = building.getCustomName();
        if (custom != null && !custom.isEmpty())
        {
            return custom;
        }
        return type == null ? "" : Component.translatable(type.getTranslationKey()).getString();
    }

    /**
     * The team colour MineColonies assigned, as 0xRRGGBB.
     *
     * <p>26.3 note: {@code ChatFormatting} no longer carries an RGB value at all -- {@code getColor()},
     * {@code getId()} and the rest are gone, and the enum is now nothing but a code character. The mapping
     * moved to {@code TextColor.fromLegacyFormat}, which returns null for the formatting codes that are not
     * colours (bold, obfuscated, reset), so the null is real and is handled rather than unboxed on faith.
     * This is the route MineColonies' own {@code ColonyBorderRenderer} takes for the same value; the parked
     * JourneyMap file still calls {@code getColor()} and would not compile as it stands.</p>
     */
    private static int teamColour(final IColonyView colony)
    {
        final ChatFormatting formatting = colony.getTeamColonyColor();
        if (formatting == null)
        {
            return UNKNOWN_COLOUR;
        }
        final TextColor colour = TextColor.fromLegacyFormat(formatting);
        return colour == null ? UNKNOWN_COLOUR : colour.getValue() & 0xFFFFFF;
    }

    /**
     * Centre of the claimed area, used only when the colony view has not arrived and nothing is remembered,
     * so the real centre is not known from either source.
     */
    private static BlockPos centreOf(final Set<ChunkPos> chunks)
    {
        long x = 0;
        long z = 0;
        for (final ChunkPos pos : chunks)
        {
            x += pos.getMiddleBlockX();
            z += pos.getMiddleBlockZ();
        }
        return new BlockPos((int) (x / chunks.size()), 0, (int) (z / chunks.size()));
    }

    @Override
    public boolean forgetColony(final int colonyId)
    {
        final Set<ChunkPos> claimed = this.claims.get(colonyId);
        if (claimed != null && !claimed.isEmpty())
        {
            // Live. Forgetting it would achieve nothing -- the next poll puts it straight back -- and the
            // entry in the menu is only offered for remembered colonies in the first place.
            return false;
        }
        if (this.memory.remove(colonyId) == null)
        {
            return false;
        }
        this.memoryChanged = true;
        this.flushMemory(true);

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null)
        {
            this.snapshot = this.build(minecraft);
        }
        return true;
    }

    /**
     * Writes the remembered colonies for the current dimension, if anything has changed since the last
     * write. The list is copied here, on the client thread, and the copy is what the writer thread sees.
     *
     * @param now true to also drain the writer queue on this thread. Used where there may be no later
     *            chance -- leaving the world, and the player forgetting a colony, which should be on disk
     *            before they can quit and find it back.
     */
    private void flushMemory(final boolean now)
    {
        this.ticksSinceSave = 0;
        if (!this.memoryChanged || this.store == null || this.dimensionName.isEmpty())
        {
            return;
        }
        this.memoryChanged = false;
        this.store.save(this.dimensionName, List.copyOf(this.memory.values()));
        if (now)
        {
            this.store.flush();
        }
    }

    @Override
    public boolean openBuildingGui(final int colonyId, final BlockPos pos)
    {
        if (this.dimension == null)
        {
            return false;
        }
        try
        {
            final IColonyView colony = IColonyManager.getInstance().getColonyView(colonyId, this.dimension);
            if (colony == null)
            {
                return false;
            }
            final IBuildingView building = colony.getClientBuildingManager().getBuildings().get(pos);
            if (building == null)
            {
                return false;
            }
            building.openGui(false);
            return true;
        }
        catch (final Throwable t)
        {
            WorldMapClient.LOGGER.warn("Could not open the hut window at {}", pos, t);
            return false;
        }
    }

    /**
     * One claim map entry, waiting to be applied.
     *
     * @param pos the chunk.
     * @param id  the owning colony, or 0 for nobody.
     */
    private record ChunkOwnership(ChunkPos pos, int id)
    {
    }
}
