package com.ldtteam.blockui.util;

import com.ldtteam.blockui.mod.Log;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Util;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for throwing errors which is safe during production.
 */
public class SafeError
{
    /**
     * Distinct error messages already reported in production. Everything that reaches this class sits on a per-tick or
     * per-frame path, so an unreported one is guaranteed to come back hundreds of times per open window and bury the rest
     * of the log. Deduplication happens on the full message, which always carries the pane path and, where one exists, the
     * offending resource - so collapsing repeats loses no identifying information, only the repetition.
     */
    private static final Set<String> REPORTED_ERRORS = ConcurrentHashMap.newKeySet();

    /**
     * Upper bound for {@link #REPORTED_ERRORS} so a caller that builds messages out of unbounded data (coordinates,
     * counters, ...) cannot grow it forever. Reaching it simply starts a new reporting round.
     */
    private static final int MAX_REPORTED_ERRORS = 1024;

    /**
     * Safe error throw call that only throws an exception during development, but logs an error in production instead so no crashes to desktop may occur.
     * <p>
     * In production every distinct message is logged once, see {@link #REPORTED_ERRORS}. Development keeps throwing on
     * every occurrence - deduplication is a log concern, not a control-flow one.
     *
     * @param exception the exception instance.
     */
    public static void throwInDev(final RuntimeException exception)
    {
        // NeoForge FMLEnvironment.isProduction() has no Fabric counterpart; the equivalent question
        // "am I in a dev workspace?" is FabricLoader#isDevelopmentEnvironment (inverted).
        if (FabricLoader.getInstance().isDevelopmentEnvironment())
        {
            throw Util.pauseInIde(exception);
        }
        else if (shouldReport(exception.getMessage()))
        {
            Log.getLogger().error(exception.getMessage(), exception);
        }
    }

    /**
     * @param message message to deduplicate on
     * @return whether this message has not been reported yet
     */
    private static boolean shouldReport(final String message)
    {
        if (REPORTED_ERRORS.size() >= MAX_REPORTED_ERRORS)
        {
            REPORTED_ERRORS.clear();
        }
        return REPORTED_ERRORS.add(String.valueOf(message));
    }

    /**
     * Forgets which errors have already been reported, so a problem that survives is reported once more.
     * Called on client resource reload: a resource pack swap or a {@code /reload} is exactly the moment at which a
     * previously missing texture may have appeared (or a previously fine one may have vanished).
     */
    public static void resetReportedErrors()
    {
        REPORTED_ERRORS.clear();
    }

    /**
     * @param value        the object reference to check for nullity
     * @param errorMessage detail message to be used in the event that a {@code NullPointerException} is thrown
     * @see Objects#requireNonNull(Object, String)
     */
    public static void requireNonNull(final Object value, final String errorMessage)
    {
        if (value == null)
        {
            throwInDev(new NullPointerException(errorMessage));
        }
    }

    /**
     * @param value        the object reference to check for nullity
     * @param defaultValue default value for production environment
     * @param errorMessage detail message to be used in the event that a {@code NullPointerException} is thrown
     * @see Objects#requireNonNull(Object, String)
     */
    public static <T> T requireNonNull(final T value, final T defaultValue, final String errorMessage)
    {
        if (value == null)
        {
            throwInDev(new NullPointerException(errorMessage));
            return defaultValue;
        }
        return value;
    }
}
