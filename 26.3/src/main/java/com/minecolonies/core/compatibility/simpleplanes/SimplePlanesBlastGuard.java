package com.minecolonies.core.compatibility.simpleplanes;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.Explosions;
import com.minecolonies.api.util.Log;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.Colony;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Colony protection against the blasts Simple Planes produces.
 *
 * <h2>What this restores</h2>
 * Upstream MineColonies shielded a colony's claimed chunks from explosions through NeoForge's
 * {@code ExplosionEvent.Start} / {@code .Detonate}. Fabric has no counterpart, so the Fabric port lost the
 * protection while keeping everything around it: the {@link Explosions} enum, the
 * {@code turnoffexplosionsincolonies} server config and the {@link Action#EXPLODE} colony permission were all
 * still shipped, still settable, and did nothing. This class makes them mean something again — for the blasts it
 * can reach.
 *
 * <h2>What it does not reach, said plainly</h2>
 * <b>Only Simple Planes' own blasts.</b> A creeper, a bed in the Nether, a TNT block, a wither skull, an end
 * crystal and a bomb dropped from a plane as a payload entity are all <em>vanilla</em> explosions, and they go
 * through {@code ServerLevel#explode} without ever passing this code. Intercepting those generically needs a
 * mixin on {@code ServerLevel#explode}; this port deliberately contains none, and Fabric API ships no explosion
 * callback to use instead. See {@code 26.2/BLAST-PROTECTION.md} for the cost of that decision, stated so it can
 * be revisited with numbers.
 *
 * <h2>Why the binding is reflective</h2>
 * Simple Planes is not on this project's compile classpath: it has no Maven coordinate, it is not staged into
 * {@code .staged-libs} the way BlockUI, Domum and Structurize are, and the only jar of it that exists locally
 * predates the {@code BlastGuard} API this class registers into. A {@code compileOnly} dependency on that jar
 * would therefore turn the build red rather than green, and parking this file in
 * {@code optional-integrations.txt} the way JEI and JourneyMap are parked would mean the feature never runs
 * until somebody edits a build file. Reflection buys a class that compiles today, ships today, and starts
 * working the moment a Simple Planes jar carrying the API is present — and silently does nothing when it is
 * not, which is exactly the no-op-proxy behaviour the rest of the compat layer has.
 * <p>
 * The seam it binds to lives in Simple Planes and knows nothing about MineColonies. That direction is a licence
 * requirement, not a preference: this repository is GPL-3.0-only and Simple Planes is LGPL-3.0-or-later, so
 * MineColonies may link to Simple Planes but no MineColonies code may be copied into it — doing so would make
 * that jar undistributable under its own licence. The seam therefore lives in that repository and is maintained
 * there; nothing has to be applied on this side, and there is nothing here to keep in step with it beyond the
 * three class names below.
 *
 * <h2>If the aircraft mod can also switch it off</h2>
 * It can, and that is not this class's business. Simple Planes has its own {@code /blastguard off}, which stops
 * it consulting <em>any</em> guard, this one included. A colony owner therefore has two independent switches —
 * {@code /mc colony blastprotection} decides what this guard answers, and the aircraft mod's own switch decides
 * whether it is asked at all. Nothing here needs to know which position that one is in: a guard that is never
 * consulted simply never runs.
 */
public final class SimplePlanesBlastGuard
{
    /**
     * Mod id, as declared in Simple Planes' {@code fabric.mod.json}.
     */
    private static final String SIMPLE_PLANES_MOD_ID = "simpleplanes";

    /**
     * The registry to add ourselves to, the interface to implement, and the record the whole exchange is in.
     * Names rather than classes, because none of the three can be on this jar's classpath.
     */
    private static final String GUARDS_CLASS    = "xyz.przemyk.simpleplanes.api.BlastGuards";
    private static final String GUARD_INTERFACE = "xyz.przemyk.simpleplanes.api.BlastGuard";
    private static final String BLAST_CLASS     = "xyz.przemyk.simpleplanes.autopilot.Blast";

    /**
     * The single method of the guard interface.
     */
    private static final String GUARD_METHOD = "guardBlast";

    /**
     * {@code Blast}'s canonical constructor and accessors, resolved once in {@link #register()} and used on
     * every blast afterwards. Null until (and unless) the binding succeeds.
     */
    @Nullable private static Constructor<?> blastConstructor;
    @Nullable private static Method blastPower;
    @Nullable private static Method blastBreaksBlocks;
    @Nullable private static Method blastFire;

    private SimplePlanesBlastGuard()
    {
        throw new IllegalAccessError("Utility class");
    }

    /**
     * Registers the colony blast guard with Simple Planes, if Simple Planes is installed and new enough to have
     * the seam. Called once, from the mod initialiser.
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
            final ClassLoader loader = SimplePlanesBlastGuard.class.getClassLoader();
            final Class<?> guardsClass = Class.forName(GUARDS_CLASS, true, loader);
            final Class<?> guardInterface = Class.forName(GUARD_INTERFACE, true, loader);
            final Class<?> blastClass = Class.forName(BLAST_CLASS, true, loader);

            blastConstructor = blastClass.getConstructor(float.class, boolean.class, boolean.class);
            blastPower = blastClass.getMethod("power");
            blastBreaksBlocks = blastClass.getMethod("breaksBlocks");
            blastFire = blastClass.getMethod("fire");

            final Object guard = Proxy.newProxyInstance(loader, new Class<?>[] {guardInterface}, new GuardHandler());
            guardsClass.getMethod("register", guardInterface).invoke(null, guard);

            Log.getLogger().info("Simple Planes is present: colony blast protection is active for aircraft blasts.");
        }
        catch (final ClassNotFoundException | NoSuchMethodException e)
        {
            Log.getLogger()
              .info("Simple Planes is present but has no blast guard API; colony blast protection stays off. "
                      + "Update Simple Planes to a build that has xyz.przemyk.simpleplanes.api.BlastGuards to enable it. ({})",
                e.toString());
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            Log.getLogger().warn("Could not register the colony blast guard with Simple Planes.", e);
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
                return guard((ServerLevel) args[0], (Entity) args[1], (Vec3) args[2], args[3]);
            }
            return switch (method.getName())
            {
                case "equals" -> proxy == (args == null ? null : args[0]);
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "MineColonies colony blast guard";
                default -> null;
            };
        }
    }

    /**
     * Decides what a blast about to go off inside a colony should actually do.
     *
     * @param level  the level the blast is in.
     * @param source the aircraft, or null.
     * @param at     the centre of the blast.
     * @param blast  Simple Planes' {@code Blast} record, opaque here.
     * @return the blast to apply, or null to suppress it entirely.
     */
    @Nullable
    private static Object guard(@NotNull final ServerLevel level,
      @Nullable final Entity source,
      @NotNull final Vec3 at,
      @NotNull final Object blast)
    {
        if (!MineColonies.getConfig().getServer().enableColonyProtection.get())
        {
            return blast;
        }

        final Explosions policy = MineColonies.getConfig().getServer().turnOffExplosionsInColonies.get();
        if (policy == Explosions.DAMAGE_EVERYTHING)
        {
            return blast;
        }

        final IColony colony = IColonyManager.getInstance().getIColony(level, BlockPos.containing(at));
        if (colony == null)
        {
            // Not a claimed chunk. Outside a colony the mod has no business touching anybody's explosion.
            return blast;
        }

        // The per-colony off switch. Only a server-side Colony carries it; a view never reaches this code.
        if (colony instanceof final Colony serverColony && !serverColony.isBlastProtection())
        {
            return blast;
        }

        // Action.EXPLODE, honoured the way every other colony permission is: if the colony's own permission
        // table says this player may explode things here, it may. Officers demolishing their own build with a
        // plane is a legitimate thing to do, and the permission exists precisely to say so. A strike aircraft
        // is unmanned and has no responsible player, so it never takes this exit.
        final Player pilot = pilotOf(source);
        if (pilot != null && colony.getPermissions().hasPermission(pilot, Action.EXPLODE))
        {
            return blast;
        }

        if (policy == Explosions.DAMAGE_NOTHING)
        {
            return null;
        }

        // DAMAGE_ENTITIES and DAMAGE_PLAYERS both land here, and this seam cannot tell them apart: it decides
        // what the explosion *is* before vanilla casts a single ray, and it has no say over which entities the
        // rays then hit. Both therefore mean "the blast still goes off and still hurts whatever is standing in
        // it, but the world is left exactly as it was" -- power untouched, no crater, no fire. DAMAGE_PLAYERS
        // is thus honoured exactly on its block half and treated as DAMAGE_ENTITIES on its entity half.
        return defuse(blast);
    }

    /**
     * Whoever is flying, if anyone. Read off the vehicle through vanilla's own accessor, so that no Simple
     * Planes type is named here either.
     *
     * @param source the exploding entity, or null.
     * @return the player responsible for the blast, or null if it is unmanned.
     */
    @Nullable
    private static Player pilotOf(@Nullable final Entity source)
    {
        if (source == null)
        {
            return null;
        }
        if (source instanceof final Player player)
        {
            return player;
        }
        return source.getControllingPassenger() instanceof final Player pilot ? pilot : null;
    }

    /**
     * The same blast with its block damage and its fire taken away.
     *
     * @param blast the blast as ordered.
     * @return a new {@code Blast} of the same power that rearranges nothing, or the original if the record
     *         cannot be rebuilt for any reason -- an unprotected blast is a better outcome than a crash inside
     *         another mod's tick.
     */
    private static Object defuse(@NotNull final Object blast)
    {
        if (blastConstructor == null || blastPower == null || blastBreaksBlocks == null || blastFire == null)
        {
            return blast;
        }
        try
        {
            final float power = (float) blastPower.invoke(blast);
            final boolean breaksBlocks = (boolean) blastBreaksBlocks.invoke(blast);
            final boolean fire = (boolean) blastFire.invoke(blast);
            if (!breaksBlocks && !fire)
            {
                // Already harmless to the world; hand back the very same object rather than an equal one.
                return blast;
            }
            return blastConstructor.newInstance(power, false, false);
        }
        catch (final ReflectiveOperationException | RuntimeException e)
        {
            Log.getLogger().warn("Could not defuse an aircraft blast inside a colony.", e);
            return blast;
        }
    }
}
