package com.unknownwq.worldmap.colony;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns {@link ColonyMemory} records into a {@link ColonySnapshot}.
 *
 * <p>Every colony on the map goes through here, whether it was read off a live colony view a moment ago or
 * off disk at login: the overlay merges live data into its memory records first and then renders the
 * memory, so there is one code path producing shapes and exactly one place where the live-versus-remembered
 * difference is applied. The alternative -- building live shapes directly and remembered ones separately --
 * is two loops that have to be kept in step for ever.</p>
 *
 * <p>No MineColonies type appears here, so this compiles and loads on an installation without the mod, like
 * everything else outside {@code com.unknownwq.worldmap.colony.minecolonies}.</p>
 */
@Environment(EnvType.CLIENT)
public final class ColonySnapshots
{
    /**
     * Drawn for a spawn point of a raid that is happening now. See {@link #spawnMarker} for why this one glyph is
     * allowed out of the colony's own palette.
     */
    private static final int RAID_LIVE_COLOUR = 0xE8412F;

    /**
     * Ticks in a Minecraft day, hour and minute, for turning the gap between two game times into words.
     */
    private static final long TICKS_PER_DAY = 24_000L;

    private static final long TICKS_PER_HOUR = 1_000L;

    private static final double TICKS_PER_MINUTE = 1_000.0 / 60.0;

    /**
     * Compass names, clockwise from north, indexed by 45-degree sector.
     */
    private static final String[] COMPASS = {
      "gui.worldmap.compass.n", "gui.worldmap.compass.ne", "gui.worldmap.compass.e", "gui.worldmap.compass.se",
      "gui.worldmap.compass.s", "gui.worldmap.compass.sw", "gui.worldmap.compass.w", "gui.worldmap.compass.nw"};

    /**
     * Assembles the snapshot.
     *
     * @param memories  every colony the overlay knows about in this dimension, live and remembered alike.
     * @param live      the ids that have live claim data behind them right now. Everything else is drawn as
     *                  remembered.
     * @param raiding   the ids under raid right now. Never contains a remembered colony: a raid is a state,
     *                  not a fact about the ground, and nothing that came off disk is claimed to be in one.
     * @param workers   who is working in which hut, keyed by hut position. Live only and deliberately not
     *                  persisted: a citizen list is a fact about right now, and a remembered hut that named
     *                  a worker who left months ago would be worse than one that names nobody. A hut with no
     *                  entry here gets an empty worker list.
     * @param deaths    grave markers, built by the caller from live data only.
     * @param waypoints waypoint markers, likewise.
     * @param gameTime  the world's game time right now, for dating the raider spawn points against the game time
     *                  the colony stamped their raid with. 0 when there is no level to ask, which reads as "no
     *                  date" rather than as "the raid was a very long time ago".
     * @return the snapshot, with every list immutable.
     */
    public static ColonySnapshot build(
      final Collection<ColonyMemory> memories,
      final Set<Integer> live,
      final Set<Integer> raiding,
      final Map<BlockPos, List<String>> workers,
      final List<ColonySnapshot.PointMarker> deaths,
      final List<ColonySnapshot.PointMarker> waypoints,
      final long gameTime)
    {
        final List<ColonySnapshot.ColonyShape> shapes = new ArrayList<>();
        final List<ColonySnapshot.BuildingMarker> buildings = new ArrayList<>();
        final List<ColonySnapshot.PointMarker> raiderSpawns = new ArrayList<>();
        final List<ColonySnapshot.FieldMarker> fields = new ArrayList<>();
        final List<ColonySnapshot.PatrolRoute> patrols = new ArrayList<>();

        for (final ColonyMemory colony : memories)
        {
            if (colony.chunks().length == 0)
            {
                continue;
            }

            final boolean remembered = !live.contains(colony.id());

            shapes.add(new ColonySnapshot.ColonyShape(
              colony.id(),
              colony.name(),
              colony.citizens(),
              colony.colour(),
              colony.own(),
              colony.centre(),
              colony.chunks(),
              edgesOf(colony.chunks()),
              !remembered && raiding.contains(colony.id()),
              colony.hostile(),
              remembered,
              colony.lastSeen()));

            for (final ColonyMemory.Hut hut : colony.huts())
            {
                buildings.add(new ColonySnapshot.BuildingMarker(
                  colony.id(),
                  hut.pos(),
                  colony.colour(),
                  iconFor(hut.itemId()),
                  hut.name(),
                  hut.level(),
                  hut.maxLevel(),
                  workers.getOrDefault(hut.pos(), List.of()),
                  hut.underConstruction(),
                  remembered));
            }

            for (final ColonyMemory.Field field : colony.fields())
            {
                fields.add(new ColonySnapshot.FieldMarker(
                  colony.id(),
                  field.pos(),
                  colony.colour(),
                  field.taken(),
                  field.type(),
                  colony.name(),
                  field.building(),
                  remembered));
            }

            for (final ColonyMemory.Patrol patrol : colony.patrols())
            {
                if (patrol.points().size() >= 2)
                {
                    patrols.add(new ColonySnapshot.PatrolRoute(
                      colony.id(), patrol.tower(), colony.colour(), patrol.points(), remembered));
                }
            }

            final boolean underRaid = !remembered && raiding.contains(colony.id());
            for (final BlockPos spawn : colony.raiderSpawns())
            {
                raiderSpawns.add(spawnMarker(colony, spawn, underRaid, gameTime));
            }
        }

        // Own colonies last in the list, so they are drawn on top of everybody else's, and remembered ones
        // before live ones for the same reason: what is true now wins the pixels.
        shapes.sort(Comparator.comparing(ColonySnapshot.ColonyShape::remembered).reversed()
                      .thenComparing(ColonySnapshot.ColonyShape::own));

        return new ColonySnapshot(
          List.copyOf(shapes),
          List.copyOf(buildings),
          List.copyOf(deaths),
          List.copyOf(waypoints),
          List.copyOf(raiderSpawns),
          List.copyOf(fields),
          List.copyOf(patrols));
    }

    /**
     * One raider spawn point, as the map presents it.
     *
     * <p>Two lines, because the position alone answers none of the three questions a player has when they see one.
     * The first names the colony and says which way and how far out of it the raiders came -- measured from the
     * colony centre rather than from the player, so it is the same sentence {@code /mc colony raid <colony> where}
     * prints and does not change as the player walks about. The second says whether this is an attack in progress
     * or the remains of the last one, and how old it is.</p>
     *
     * <p>A live raid is drawn in {@link #RAID_LIVE_COLOUR} instead of the colony's own team colour. Everything else
     * on this map takes the colony colour and that rule is worth keeping, but a raid happening now is the one thing
     * here that has to be readable before it is identified, and the label names the colony a hover later.</p>
     *
     * @param colony    the colony the points belong to.
     * @param spawn     the recorded position.
     * @param underRaid whether that colony is being raided right now.
     * @param gameTime  the world's game time.
     * @return the marker.
     */
    private static ColonySnapshot.PointMarker spawnMarker(
      final ColonyMemory colony,
      final BlockPos spawn,
      final boolean underRaid,
      final long gameTime)
    {
        final int dx = spawn.getX() - colony.centre().getX();
        final int dz = spawn.getZ() - colony.centre().getZ();
        final int distance = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));

        final String compass = compassKey(dx, dz);
        final String label = compass == null
                               ? Component.translatable("gui.worldmap.colony.raider_spawn_at_centre",
                                   colony.name()).getString()
                               : Component.translatable("gui.worldmap.colony.raider_spawn",
                                   colony.name(), distance, Component.translatable(compass)).getString();

        final String age = raidAge(colony.lastRaidTime(), gameTime);
        final String note;
        if (underRaid)
        {
            note = age == null
                     ? Component.translatable("gui.worldmap.colony.raider_spawn_live").getString()
                     : Component.translatable("gui.worldmap.colony.raider_spawn_live_since", age).getString();
        }
        else if (age == null)
        {
            note = Component.translatable("gui.worldmap.colony.raider_spawn_note").getString();
        }
        else
        {
            note = Component.translatable("gui.worldmap.colony.raider_spawn_ago", age).getString();
        }

        return new ColonySnapshot.PointMarker(
          spawn, underRaid ? RAID_LIVE_COLOUR : colony.colour(), label, note);
    }

    /**
     * How long ago the raid behind a spawn point started, as a translated phrase.
     *
     * <p>In game time, not wall-clock, and deliberately: the number the colony stamps a raid with is the world's
     * game time, a world that has been paused or left alone for a week has not aged its raid by a week, and a
     * player counts the distance from a raid in nights rather than in hours of their own evening.</p>
     *
     * @param raidTime the game time the raid started at, or {@link ColonyMemory#NO_RAID_TIME}.
     * @param gameTime the game time now.
     * @return the phrase, or null when the point carries no time at all and the map must say so instead.
     */
    private static String raidAge(final long raidTime, final long gameTime)
    {
        if (raidTime == ColonyMemory.NO_RAID_TIME)
        {
            return null;
        }
        // Game time is the world's total tick count: it only goes up and `/time set` does not touch it, which is
        // exactly why the age is taken from it rather than from the day counter a command can move. The clamp is
        // for the one case that can still run backwards -- a remembered record from a world that was later
        // restored from an earlier save -- where the youngest bucket is a better answer than a negative age.
        final long elapsed = Math.max(0L, gameTime - raidTime);
        if (elapsed >= TICKS_PER_DAY)
        {
            return Component.translatable("gui.worldmap.time.game_days", elapsed / TICKS_PER_DAY).getString();
        }
        if (elapsed >= TICKS_PER_HOUR)
        {
            return Component.translatable("gui.worldmap.time.game_hours", elapsed / TICKS_PER_HOUR).getString();
        }
        return Component.translatable("gui.worldmap.time.game_minutes",
          Math.max(1L, Math.round(elapsed / TICKS_PER_MINUTE))).getString();
    }

    /**
     * @param dx east-west offset, in blocks.
     * @param dz south-north offset, in blocks. Positive z is south, as everywhere else in Minecraft.
     * @return the translation key of the eight-point compass direction that offset points in, or null when there is
     *     no direction to give because the point is the centre itself. Eight points and not sixteen because this is
     *     read at a glance beside a distance, and "east-north-east" is not read at a glance.
     */
    private static String compassKey(final int dx, final int dz)
    {
        if (dx == 0 && dz == 0)
        {
            return null;
        }
        // Screen angle measured clockwise from north, which is -z. Adding half a sector before the divide is
        // what makes each name cover the 45 degrees centred on it rather than the 45 starting at it.
        final double degrees = (Math.toDegrees(Math.atan2(dx, -dz)) + 360.0) % 360.0;
        return COMPASS[(int) Math.floor((degrees + 22.5) % 360.0 / 45.0)];
    }

    /**
     * The hut icon, looked back up out of the item registry.
     *
     * <p>Storing the registry id rather than a serialised stack is what makes a remembered hut survive a
     * MineColonies update: the id is the mod's own stable name for the block, and an id that no longer
     * resolves gives an empty stack and the plain grey square the renderer already draws for a building type
     * with no block.</p>
     *
     * @param itemId {@code namespace:path}, or empty.
     * @return the stack, or {@link ItemStack#EMPTY}.
     */
    private static ItemStack iconFor(final String itemId)
    {
        if (itemId == null || itemId.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        try
        {
            final Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }
        catch (final RuntimeException e)
        {
            return ItemStack.EMPTY;
        }
    }

    /**
     * @param chunks packed chunk positions.
     * @return the boundary of that set, in the form {@link ColonySnapshot.ColonyShape} wants. Recomputed
     *     rather than stored, because it is derived from the chunk set and storing both would mean a file
     *     that can disagree with itself.
     */
    private static int[] edgesOf(final long[] chunks)
    {
        final List<ChunkPos> positions = new ArrayList<>(chunks.length);
        for (final long packed : chunks)
        {
            positions.add(new ChunkPos(ChunkOutline.unpackX(packed), ChunkOutline.unpackZ(packed)));
        }
        return ChunkOutline.edges(positions);
    }

    private ColonySnapshots()
    {
        /*
         * Intentionally left empty.
         */
    }
}
