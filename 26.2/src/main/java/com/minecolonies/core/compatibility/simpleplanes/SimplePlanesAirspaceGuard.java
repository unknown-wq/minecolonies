package com.minecolonies.core.compatibility.simpleplanes;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.claim.IChunkClaimData;
import com.minecolonies.api.colony.permissions.Rank;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.MineColonies;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.api.util.constant.ColonyManagerConstants.NO_COLONY_ID;

/**
 * Tells an aircraft's autopilot which colonies its pilot would rather not be flown over.
 * <p>
 * The sibling of {@link SimplePlanesBlastGuard} and bound exactly the same way: Simple Planes publishes an
 * interface and a registry that name no mod but their own, and this class registers a
 * {@link Proxy} into it. Everything the note on that class says about <em>why</em> the binding is reflective
 * applies here word for word -- Simple Planes is not on this project's compile classpath, the only jar of it that
 * exists locally predates both APIs, and a class that compiles today and starts working the moment a new enough
 * jar is present is worth more than a build-file edit somebody has to remember. So is the licence half: this
 * repository is GPL-3.0-only and Simple Planes is LGPL-3.0-or-later, so MineColonies may link to Simple Planes
 * but no MineColonies code may be copied into it. The seam lives over there and is maintained over there.
 *
 * <h2>What this answers</h2>
 * One question, per point, per candidate route: <b>is this position inside a colony this flight's pilot is an
 * enemy of?</b> That is all. It is not a permission check and it prevents nothing:
 * <ul>
 *     <li>A {@code true} is advice to a route planner, which weighs it against the terrain and may still fly
 *     through -- and does, whenever the aircraft is already inside. Nothing here can strand an aircraft,
 *     because nothing here can say "no".</li>
 *     <li>It is asked only while the <b>autopilot</b> is flying. A player with their hands on the stick is never
 *     routed by this and never stopped by it. This is not a no-fly zone and it cannot be made into one from this
 *     side.</li>
 * </ul>
 *
 * <h2>The three rules that decide whether a claim is considered at all</h2>
 * <ol>
 *     <li><b>Somebody has to be aboard.</b> An empty aircraft -- a colony's own drop run, a strike, a ferry
 *     flight ordered from the console -- flies straight, exactly as it did before this existed. Being an enemy is
 *     a relation between a colony and a <em>player</em>, and an aircraft with nobody in it is not trespassing on
 *     anybody: routing it round would be a detour nobody asked for, and it would slow down the mod's own
 *     aircraft. The pilot Simple Planes names is not enough to decide this by itself, because that is also
 *     filled in for the player who <em>ordered</em> an empty flight; {@code Flight#pilotAboard} is the fact, and
 *     it is the first thing read.</li>
 *     <li><b>The colony containing the destination is ignored.</b> Otherwise a flight <em>to</em> a hostile
 *     colony -- which is the interesting flight, and the only way to deliver anything to one -- would spend its
 *     approach being pushed away from the field it is trying to land on, and would arrive, if at all, by the
 *     accident Simple Planes' own route planner documents. Whether to land there is the pilot's business and the
 *     colony's; it is not this method's.</li>
 *     <li><b>The colony containing the departure point is ignored.</b> The same argument at the other end and
 *     rather more sharply: an aircraft standing inside a claim it must keep out of is an aircraft that cannot
 *     take off, and "you may not leave" is not a routing preference.</li>
 * </ol>
 * Both exemptions are per colony, not global: every <em>other</em> colony the pilot is an enemy of is still
 * routed around on the same flight. Both are resolved from the chunk claim map rather than from
 * {@code IColony#isCoordInColony}, so a field a block outside a partially-claimed chunk still exempts the colony
 * that owns that chunk -- deliberately the generous direction, because the cost of exempting one colony too many
 * is a flight that goes straight, and the cost of exempting one too few is a flight that cannot happen.
 * <p>
 * A Simple Planes too old to state those facts is still bound to, on its own point-only interface, and then
 * behaves as this class did before the rules existed: it routes around every colony the responsible player is an
 * enemy of, aboard or not, with no exemptions. That is the most an older build can be asked, and it is what the
 * log line at registration says is happening.
 *
 * <h2>Who counts as an enemy</h2>
 * Two ways, and the second is why this is not simply a rank test:
 * <ul>
 *     <li><b>The pilot's rank in that colony is hostile</b> -- {@link Rank#isHostile()}, which is false for
 *     Owner, Officer, Friend and, the case that matters, <b>Neutral</b>. Neutral is what
 *     {@code Permissions#getRank} returns for every player who is not in the colony's table at all, which on any
 *     populated server is nearly everybody. Treating it as hostile would mean an aircraft detoured around every
 *     colony its pilot did not happen to be a member of, which is not "route around territory where the pilot is
 *     an enemy", it is a general ban on overflight that nobody asked for. Hostile is a deliberate act by a colony
 *     owner -- {@code /mc colony setrank <colony> <player> hostile} -- and this only ever responds to that act.</li>
 *     <li><b>The colony is a hostile territory</b> -- {@link IColony#isHostile()}, the ownerless enemy ground
 *     created by {@code /mc colony territory create}. There is no rank to consult there, because there is no
 *     owner to have set one and every player in it is Neutral; what makes it enemy ground is the flag, which is
 *     also exactly what the border renderer reads to draw it in the territory's own colour for everybody who
 *     looks at it. A territory that reads as enemy ground on the map and as ordinary airspace to an autopilot
 *     would be two halves of one feature disagreeing.</li>
 * </ul>
 *
 * <h2>Cost, and the one call this must never make</h2>
 * {@code IColonyManager#getIColony} is the obvious way to answer "which colony is at this position" and it is
 * the wrong one here: it goes through {@code Level#getChunkAt}, which <b>loads and, on ground nobody has
 * visited, generates the chunk on the server thread</b> -- see the port note on
 * {@code ColonyUtils#getOwningColony(ResourceKey, ChunkPos)}. This runs up to eight times per candidate heading
 * on ground up to 220 blocks ahead of an aircraft, so using it would mean an autopilot flight quietly
 * generating a corridor of world in front of itself. Instead the claim map is read directly, keyed by dimension
 * and chunk position, which touches no chunk at all, and the whole method is gated on the chunk being resident
 * in the first place.
 * <p>
 * The order of the tests below is the order of their cost, cheapest first, so that the overwhelmingly common
 * answer -- unclaimed ground, no colony, nothing to say -- costs one hash lookup and returns. The facts that
 * belong to the flight rather than to the point are read once per route search rather than once per probe; see
 * {@link Sortie}.
 *
 * <h2>Why there is no MineColonies command for this</h2>
 * Because the switch already exists and is finer than a command would be. A colony owner controls this per
 * player, deliberately, with {@code /mc colony setrank}: setting somebody hostile turns it on for them and
 * setting them back turns it off. A per-colony boolean on top of that could only express "hostile on the ground
 * but welcome in the air", which is a distinction nobody has asked for, and it would add a second switch to
 * explain -- the confusion the note on {@link SimplePlanesBlastGuard} already flags for blasts, where a
 * per-colony switch <em>was</em> needed because an explosion has no player attached to weigh permissions
 * against. Server-wide, Simple Planes' own {@code /airspaceguard off} stops its autopilot consulting any guard,
 * this one included, and that is the right place for a server-wide switch to live: it belongs to the mod whose
 * behaviour changes.
 */
public final class SimplePlanesAirspaceGuard
{
    /**
     * Mod id, as declared in Simple Planes' {@code fabric.mod.json}.
     */
    private static final String SIMPLE_PLANES_MOD_ID = "simpleplanes";

    /**
     * The registry to add ourselves to and the interfaces to implement. Names rather than classes, because none
     * of them can be on this jar's classpath.
     */
    private static final String GUARDS_CLASS           = "xyz.przemyk.simpleplanes.api.AirspaceGuards";
    private static final String GUARD_INTERFACE        = "xyz.przemyk.simpleplanes.api.AirspaceGuard";
    private static final String FLIGHT_AWARE_INTERFACE = "xyz.przemyk.simpleplanes.api.FlightAwareAirspaceGuard";

    /**
     * The method name both guard interfaces use. They differ only in what is handed to it, which is why the
     * handler below tells them apart by argument count rather than by name.
     */
    private static final String GUARD_METHOD = "isAirspaceAvoided";

    /**
     * Accessors of Simple Planes' {@code Flight} record, resolved once at registration from the interface's own
     * method signature. Null until a flight-aware registration succeeds, and non-null from then on.
     */
    private static Method flightLevel;
    private static Method flightPilot;
    private static Method flightPilotAboard;
    private static Method flightDeparture;
    private static Method flightDestination;

    /**
     * The last route search's flight facts, kept so that five reflective reads and two claim lookups happen once
     * per search instead of once per probe.
     * <p>
     * Server thread only, like everything else here, and keyed on the {@code Flight} instance <b>by identity</b>,
     * so a miss re-reads and a hit cannot possibly be a different flight. Simple Planes documents that one
     * instance is shared by every probe of one search, which is what makes a single entry enough; if a future
     * version stopped doing that this would quietly become a cache that always misses rather than a bug.
     */
    private static Sortie lastSortie;

    private SimplePlanesAirspaceGuard()
    {
        throw new IllegalAccessError("Utility class");
    }

    /**
     * Registers the colony airspace guard with Simple Planes, if Simple Planes is installed and new enough to
     * have the seam. Called once, from the mod initialiser.
     * <p>
     * Every failure below is a no-op with a log line rather than a thrown exception: a missing or older Simple
     * Planes must not stop MineColonies from loading, and it is not an error for the mod to be absent.
     */
    public static void register()
    {
        if (!FabricLoader.getInstance().isModLoaded(SIMPLE_PLANES_MOD_ID))
        {
            return;
        }

        try
        {
            final ClassLoader loader = SimplePlanesAirspaceGuard.class.getClassLoader();
            final Class<?> guardsClass = Class.forName(GUARDS_CLASS, true, loader);
            final Class<?> guardInterface = Class.forName(GUARD_INTERFACE, true, loader);

            // The flight-aware interface is the optional half. A Simple Planes new enough to have the base API
            // but not this one still gets a working guard -- with the two exemptions and the "somebody aboard"
            // rule unavailable, because it cannot state the facts they turn on. Proxying an interface that is
            // not there is not an option, since newProxyInstance would throw, so it is looked up separately and
            // the interface list is built from whatever was found.
            final List<Class<?>> interfaces = new ArrayList<>(2);
            interfaces.add(guardInterface);
            final Class<?> flightAware = findFlightAware(loader);
            if (flightAware != null)
            {
                readFlightAccessors(flightAware);
                interfaces.add(flightAware);
            }

            final Object guard =
              Proxy.newProxyInstance(loader, interfaces.toArray(new Class<?>[0]), new GuardHandler());
            guardsClass.getMethod("register", guardInterface).invoke(null, guard);

            if (flightAware == null)
            {
                Log.getLogger()
                  .info("Simple Planes is present but predates the flight-aware airspace API: aircraft on autopilot will "
                          + "route around colonies their pilot is an enemy of, without the take-off and landing "
                          + "exemptions. Update Simple Planes to a build that has {} for the full rules.",
                    FLIGHT_AWARE_INTERFACE);
            }
            else
            {
                Log.getLogger()
                  .info("Simple Planes is present: aircraft on autopilot with a player aboard will route around colonies "
                          + "that player is an enemy of, other than the ones they took off from and are flying to.");
            }
        }
        catch (final ClassNotFoundException | NoSuchMethodException e)
        {
            Log.getLogger()
              .info("Simple Planes is present but has no airspace guard API; colony airspace avoidance stays off. "
                      + "Update Simple Planes to a build that has xyz.przemyk.simpleplanes.api.AirspaceGuards to enable it. ({})",
                e.toString());
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            Log.getLogger().warn("Could not register the colony airspace guard with Simple Planes.", e);
        }
    }

    /**
     * The flight-aware interface, or null on a Simple Planes too old to have it.
     *
     * @param loader the class loader Simple Planes was loaded by.
     * @return the interface, or null.
     */
    @Nullable
    private static Class<?> findFlightAware(final ClassLoader loader)
    {
        try
        {
            return Class.forName(FLIGHT_AWARE_INTERFACE, true, loader);
        }
        catch (final ClassNotFoundException e)
        {
            return null;
        }
    }

    /**
     * Resolves the {@code Flight} record's accessors -- from the interface method's own parameter type rather
     * than from a class name, so this cannot end up reading a different {@code Flight}.
     *
     * @param flightAware the flight-aware guard interface.
     * @throws ReflectiveOperationException if the record is not the shape this was written against. The caller
     *                                      catches it, and the guard is then left unregistered rather than
     *                                      registered and broken.
     */
    private static void readFlightAccessors(@NotNull final Class<?> flightAware) throws ReflectiveOperationException
    {
        Class<?> flight = null;
        for (final Method method : flightAware.getMethods())
        {
            if (GUARD_METHOD.equals(method.getName()) && method.getParameterCount() == 2)
            {
                flight = method.getParameterTypes()[0];
                break;
            }
        }
        if (flight == null)
        {
            throw new NoSuchMethodException(FLIGHT_AWARE_INTERFACE + " has no two-argument " + GUARD_METHOD);
        }
        flightLevel = flight.getMethod("level");
        flightPilot = flight.getMethod("pilot");
        flightPilotAboard = flight.getMethod("pilotAboard");
        flightDeparture = flight.getMethod("departure");
        flightDestination = flight.getMethod("destination");
    }

    /**
     * Turns the proxy's untyped call back into a typed one.
     * <p>
     * {@link Object}'s own three methods have to be answered here as well: a {@link Proxy} routes them through
     * the handler like any other, and Simple Planes calls {@code getClass().getName()} on a guard that throws.
     */
    private static class GuardHandler implements InvocationHandler
    {
        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args)
        {
            if (GUARD_METHOD.equals(method.getName()) && args != null)
            {
                // Two arguments is the flight-aware form and four is the point-only one. Both are answered,
                // because a proxy is handed whichever the running Simple Planes chooses to call and this side
                // does not get to insist on one.
                if (args.length == 2)
                {
                    return isAvoided(args[0], (Vec3) args[1]);
                }
                if (args.length == 4)
                {
                    return isAvoided((ServerLevel) args[0], (Player) args[2], (Vec3) args[3],
                      NO_COLONY_ID, NO_COLONY_ID);
                }
            }
            return switch (method.getName())
            {
                case "equals" -> proxy == (args == null ? null : args[0]);
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "MineColonies colony airspace guard";
                default -> null;
            };
        }
    }

    /**
     * The flight-aware answer: the three rules from the class note, and then the same claim and hostility tests
     * the point-only form runs.
     *
     * @param flight Simple Planes' {@code Flight} record for this route search, untyped.
     * @param at     the point in question.
     * @return true if the autopilot should prefer a route that does not pass over here.
     */
    private static boolean isAvoided(final Object flight, @NotNull final Vec3 at)
    {
        final Sortie sortie = sortieFor(flight);
        if (sortie == null || !sortie.aboard())
        {
            // Rule 1, and the branch every one of the mod's own drop runs takes -- once per probe, before
            // anything at all is looked up.
            return false;
        }
        return isAvoided(sortie.level(), sortie.pilot(), at, sortie.departureColony(), sortie.destinationColony());
    }

    /**
     * Whether this point is inside a colony the pilot is an enemy of, with the two exempt colonies excluded.
     *
     * @param level             the level being flown in.
     * @param pilot             the player the flight is being flown for, or null for an anonymous one.
     * @param at                the point in question; only its horizontal coordinates are read, because a colony
     *                          claim is a column and its border is the same at every altitude.
     * @param departureColony   colony this flight took off inside, or {@code NO_COLONY_ID} for none.
     * @param destinationColony colony this flight is heading into, or {@code NO_COLONY_ID} for none.
     * @return true if the autopilot should prefer a route that does not pass over here.
     */
    private static boolean isAvoided(@NotNull final ServerLevel level,
      @Nullable final Player pilot,
      @NotNull final Vec3 at,
      final int departureColony,
      final int destinationColony)
    {
        if (pilot == null)
        {
            // Nobody to be an enemy of. A flight reloaded off disk after a restart and an unmanned strike both
            // land here, and both route on terrain alone rather than on a guess about who sent them.
            return false;
        }

        if (!MineColonies.getConfig().getServer().enableColonyProtection.get())
        {
            // A server that has switched colony protection off has said its colonies do not restrict players.
            // Ranks are decorative in that world -- a hostile player may already build in the town square -- so
            // routing aircraft around one would be the only place the rank still bit, which would be strange.
            return false;
        }

        final BlockPos pos = BlockPos.containing(at);

        // Nothing below may load a chunk; see the class note. This gate is belt and braces -- Simple Planes only
        // ever probes ground whose heightmap it has already read, so the chunk is resident by construction --
        // but a guard that trusted a caller's guarantee about chunk residency would be a world-generation stall
        // waiting for the first caller that changed its mind.
        if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4))
        {
            return false;
        }

        // One hash lookup, and the answer for every point over unclaimed ground, which is nearly all of them.
        final int colonyId = claimAt(level, pos);
        if (colonyId == NO_COLONY_ID)
        {
            return false;
        }

        // Rules 2 and 3, before the colony is even resolved: ground this flight is leaving or arriving at is not
        // avoided, however hostile it is to the pilot.
        if (colonyId == departureColony || colonyId == destinationColony)
        {
            return false;
        }

        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, level);
        if (colony == null)
        {
            return false;
        }

        if (!isEnemyOf(colony, pilot))
        {
            return false;
        }

        // The colony's own answer about its own border, rather than a second opinion assembled from the claim
        // data above. This port has block-precise borders -- a chunk edited with the border scepter belongs to
        // the colony in some of its columns and to nobody in the rest -- and isCoordInColony is where that is
        // decided. Re-deriving it here would be a copy that drifts. It costs a getChunkAt, which is why the
        // residency gate above is not optional.
        return colony.isCoordInColony(level, pos);
    }

    /**
     * Whether this pilot is an enemy of this colony -- see the class note for why there are two ways to be one.
     * <p>
     * The rank test is first because it is the cheaper of the two and because it is the one that is usually
     * false: most pilots are not hostile anywhere.
     *
     * @param colony the colony owning the ground.
     * @param pilot  the player being flown.
     * @return true if that is enemy ground for that player.
     */
    private static boolean isEnemyOf(@NotNull final IColony colony, @NotNull final Player pilot)
    {
        final Rank rank = colony.getPermissions().getRank(pilot);
        return (rank != null && rank.isHostile()) || colony.isHostile();
    }

    /**
     * Which colony claims the chunk containing a position, without loading anything.
     * <p>
     * Chunk granularity, and no residency gate unlike the probe path, because this is the exemption question:
     * the departure point is behind the aircraft and the destination may be hundreds of blocks ahead, so both
     * are routinely in chunks nobody is standing in. The claim map lives with the colonies rather than with the
     * chunks, so it answers for those exactly as well -- which is the whole reason the exemptions can be
     * resolved at all.
     *
     * @param level the level.
     * @param pos   the position, or null.
     * @return the owning colony's id, or {@code NO_COLONY_ID}.
     */
    private static int claimAt(@NotNull final ServerLevel level, @Nullable final BlockPos pos)
    {
        if (pos == null)
        {
            return NO_COLONY_ID;
        }
        final IChunkClaimData claim = IColonyManager.getInstance()
          .getClaimData(level.dimension(), new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        return claim == null ? NO_COLONY_ID : claim.getOwningColony();
    }

    /**
     * This route search's flight facts, read once and then handed back for every probe of it.
     *
     * @param flight Simple Planes' {@code Flight} record, untyped.
     * @return the facts, or null if they could not be read -- in which case the guard claims nothing, which is
     *         the only safe answer to "I cannot tell who is flying this".
     */
    @Nullable
    private static Sortie sortieFor(final Object flight)
    {
        final Sortie cached = lastSortie;
        if (cached != null && cached.flight() == flight)
        {
            return cached;
        }

        try
        {
            final ServerLevel level = (ServerLevel) flightLevel.invoke(flight);
            final Sortie sortie = new Sortie(flight,
              level,
              (Player) flightPilot.invoke(flight),
              (Boolean) flightPilotAboard.invoke(flight),
              claimAt(level, blockPos((Vec3) flightDeparture.invoke(flight))),
              claimAt(level, blockPos((Vec3) flightDestination.invoke(flight))));
            lastSortie = sortie;
            return sortie;
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            // Debug rather than warn: this can only happen if Simple Planes changed the record's shape under a
            // build that had already resolved its accessors, and at warn it would say so eight times a second
            // per aircraft.
            Log.getLogger().debug("Could not read the flight from Simple Planes; claiming no airspace for it.", e);
            return null;
        }
    }

    /**
     * @param at a point, or null.
     * @return the block containing it, or null.
     */
    @Nullable
    private static BlockPos blockPos(@Nullable final Vec3 at)
    {
        return at == null ? null : BlockPos.containing(at);
    }

    /**
     * One route search's worth of facts about the flight, none of which change between the probes of that
     * search.
     *
     * @param flight            the {@code Flight} instance these were read from, kept only as the cache key and
     *                          compared only by identity: it is never called again and never dereferenced.
     * @param level             the level being flown in.
     * @param pilot             the player the flight is flown for, or null.
     * @param aboard            whether that player is actually in the aircraft.
     * @param departureColony   colony claiming the chunk the flight took off in, or {@code NO_COLONY_ID}.
     * @param destinationColony colony claiming the chunk it is heading for, or {@code NO_COLONY_ID}.
     */
    private record Sortie(Object flight,
                          ServerLevel level,
                          @Nullable Player pilot,
                          boolean aboard,
                          int departureColony,
                          int destinationColony)
    {
    }
}
