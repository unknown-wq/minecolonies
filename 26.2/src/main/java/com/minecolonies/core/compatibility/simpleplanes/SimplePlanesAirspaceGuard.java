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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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
 * One question, per point, per candidate route: <b>is this position inside a colony where this player is
 * hostile?</b> That is all. It is not a permission check and it prevents nothing:
 * <ul>
 *     <li>A {@code true} is advice to a route planner, which weighs it against the terrain and may still fly
 *     through -- and does, whenever the aircraft is already inside. Nothing here can strand an aircraft,
 *     because nothing here can say "no".</li>
 *     <li>It is asked only while the <b>autopilot</b> is flying. A player with their hands on the stick is never
 *     routed by this and never stopped by it. This is not a no-fly zone and it cannot be made into one from this
 *     side.</li>
 * </ul>
 *
 * <h2>Who counts as hostile: the explicit rank, and only that</h2>
 * {@link Rank#isHostile()}, which is false for Owner, Officer, Friend and -- the case that matters --
 * <b>Neutral</b>. Neutral is what {@code Permissions#getRank} returns for every player who is not in the colony's
 * table at all, which on any populated server is nearly everybody. Treating it as hostile would mean an aircraft
 * detoured around every colony its pilot did not happen to be a member of, which is not "route around territory
 * where the pilot is hostile", it is a general ban on overflight that nobody asked for and that would land as a
 * bug report the first time somebody's mail run started going the long way round a neighbour's town. Hostile is
 * a deliberate act by a colony owner -- {@code /mc colony setrank <colony> <player> hostile} -- and this feature
 * only ever responds to that deliberate act.
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
 * answer -- unclaimed ground, no colony, nothing to say -- costs one hash lookup and returns.
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
     * The registry to add ourselves to and the interface to implement. Names rather than classes, because
     * neither can be on this jar's classpath.
     */
    private static final String GUARDS_CLASS    = "xyz.przemyk.simpleplanes.api.AirspaceGuards";
    private static final String GUARD_INTERFACE = "xyz.przemyk.simpleplanes.api.AirspaceGuard";

    /**
     * The single method of the guard interface.
     */
    private static final String GUARD_METHOD = "isAirspaceAvoided";

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

            final Object guard = Proxy.newProxyInstance(loader, new Class<?>[] {guardInterface}, new GuardHandler());
            guardsClass.getMethod("register", guardInterface).invoke(null, guard);

            Log.getLogger()
              .info("Simple Planes is present: aircraft on autopilot will route around colonies their pilot is hostile in.");
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
            if (GUARD_METHOD.equals(method.getName()) && args != null && args.length == 4)
            {
                return isAvoided((ServerLevel) args[0], (Entity) args[1], (Player) args[2], (Vec3) args[3]);
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
     * Whether this point is inside a colony where the pilot is hostile.
     *
     * @param level the level being flown in.
     * @param craft the aircraft; unused, and deliberately so -- what is being flown has no bearing on whose land
     *              it is over. It is accepted because the seam offers it and a future rule ("gunships only")
     *              would want it.
     * @param pilot the player the flight is being flown for, or null for an anonymous one.
     * @param at    the point in question; only its horizontal coordinates are read, because a colony claim is a
     *              column and its border is the same at every altitude.
     * @return true if the autopilot should prefer a route that does not pass over here.
     */
    private static boolean isAvoided(@NotNull final ServerLevel level,
      @Nullable final Entity craft,
      @Nullable final Player pilot,
      @NotNull final Vec3 at)
    {
        if (pilot == null)
        {
            // Nobody to be hostile to. A flight reloaded off disk after a restart and an unmanned strike both
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
        // 26.2's ChunkPos is a record with no BlockPos constructor; shifting is what every other call site here
        // does and it saves the intermediate object anyway.
        final ChunkPos chunk = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);

        // Nothing below may load a chunk; see the class note. This gate is belt and braces -- Simple Planes only
        // ever probes ground whose heightmap it has already read, so the chunk is resident by construction --
        // but a guard that trusted a caller's guarantee about chunk residency would be a world-generation stall
        // waiting for the first caller that changed its mind.
        if (!level.hasChunk(chunk.x(), chunk.z()))
        {
            return false;
        }

        // One hash lookup, and the answer for every point over unclaimed ground, which is nearly all of them.
        final IChunkClaimData claim = IColonyManager.getInstance().getClaimData(level.dimension(), chunk);
        if (claim == null)
        {
            return false;
        }
        final int colonyId = claim.getOwningColony();
        if (colonyId == NO_COLONY_ID)
        {
            return false;
        }

        final IColony colony = IColonyManager.getInstance().getColonyByWorld(colonyId, level);
        if (colony == null)
        {
            return false;
        }

        // The rank test before the geometry test, because it is the cheaper of the two and because it is the one
        // that is usually false: most pilots are not hostile anywhere. Getting a false negative out of this
        // ordering is not possible -- both have to be true.
        final Rank rank = colony.getPermissions().getRank(pilot);
        if (rank == null || !rank.isHostile())
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
}
