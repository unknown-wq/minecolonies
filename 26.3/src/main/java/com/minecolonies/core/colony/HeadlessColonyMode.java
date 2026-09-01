package com.minecolonies.core.colony;

import com.minecolonies.api.util.Log;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Whether the colonies on this server are allowed to run with nobody watching them.
 * <p>
 * <b>The question this separates out.</b> {@link Colony#updateState()} decides whether a colony ticks by asking
 * whether anybody can see it: a close subscriber, or a loaded claim with an important colony player somewhere in the
 * dimension. Those are two different questions -- "this colony should run" and "somebody is looking at it" -- and
 * answering the first with the second is right for a server people play on and wrong for one nobody is logged into.
 * On such a server every colony sits at {@code INACTIVE}: no work manager, so a work order is never handed to a
 * builder, and no force-load timer, so the ground the colony stands on is never ticketed and its citizens stop
 * existing. This flag answers the first question directly, and nothing else.
 * <p>
 * <b>It is deliberately not a subscriber.</b> The other way to reach {@code ACTIVE} is to hand the package manager a
 * fake player, and {@link com.minecolonies.core.colony.managers.ColonyPackageManager#addCloseSubscriber} refuses one
 * for a reason: a close subscriber is an address that colony view, permission and work-order packets are serialised
 * and sent to on every update interval. A fake subscriber would mean re-serialising the whole colony several times a
 * second into a connection that discards it, and it would mean weakening a guard that protects every install in order
 * to help the one case that wants this. Here nothing is sent, because the subscriber sets stay empty and every send
 * path is already conditional on them.
 * <p>
 * <b>The second half, and it is not optional.</b> A colony that ticks is no use in a level whose entities do not.
 * {@code ServerLevel#tick} counts up an empty timer and, once it passes 300 ticks, stops walking the entity tick list
 * and the block entities altogether; the only things that reset it are a player and a chunk ticket whose type carries
 * {@code TicketType.FLAG_KEEP_DIMENSION_ACTIVE} (vanilla's {@code FORCED}, the one {@code /forceload} registers, is
 * such a type). The colony's own force-load tickets are loading-and-simulation only, so fifteen seconds after the last
 * player leaves, a colony's citizens stand exactly where they were: the colony ticks, the chunks are held, and nothing
 * in them moves. So while the mode is on, one ticket carrying that flag is held per colony. It is registered with
 * radius 0 -- enough to load its own chunk and no more -- because its whole purpose is the flag; which chunks tick is
 * still the force-load timer's business. The colony's own ticket type is deliberately left alone: giving it the flag
 * would keep a dimension awake for every install that force-loads a colony, which is a decision for a different day.
 * <p>
 * <b>What it does not do.</b> It does not force-load anything by itself. It makes the colony refresh its force-load
 * timer as an officer standing in the colony would, and from there the ordinary rules decide which chunks get a
 * ticket -- {@code colonyloadstrictness} as usual, or the whole claim where {@code forceloadallclaims} (or
 * {@code /mc colony forceloadclaims}) is on. A colony that would not have been force-loaded with a player standing in
 * it is not force-loaded by this either.
 * <p>
 * <b>How it is reached, and why the chain is that long.</b> Nothing here changes behaviour on an install that has not
 * asked for it, and "the default is false" is not by itself a safeguard -- a default can be flipped by a stray config
 * file, a modpack, or a colony's own saved data. So:
 * <ol>
 *   <li>the JVM must have been started with {@code -Dminecolonies.headless=true}. This is read once, at class
 *       initialisation, and there is no way to set it from inside the game -- no config key, no command, no packet.
 *       A launcher, a modpack or a server host does not add it by accident;</li>
 *   <li>the server must be a dedicated one. An integrated server -- singleplayer, or a world opened to LAN -- is
 *       refused even with the property set;</li>
 *   <li>an operator must run {@code /mc debug headless on}. The command literal is not even added to the tree
 *       unless the property is set, so on an ordinary server it does not exist to be found or mistyped;</li>
 *   <li>it lasts until the server stops. Nothing is written to the colony NBT, the server config or any other file,
 *       so a server that comes back up is off again and somebody has to decide a second time.</li>
 * </ol>
 * The last point is the important one. A flag persisted in a colony's saved data would travel with a world backup
 * into somebody else's server and quietly keep their colonies ticking; re-arming a test run costs one command.
 * <p>
 * And because a server in this state is doing something no ordinary server does, it says so: once when it comes up
 * armed, once when the mode is switched, and on a repeating reminder for as long as it is on.
 */
public final class HeadlessColonyMode
{
    /**
     * The JVM system property that has to be set to {@code true} before any of this can be switched on.
     */
    public static final String ARM_PROPERTY = "minecolonies.headless";

    /**
     * How often the running reminder is logged, in ticks. Ten minutes: often enough that nobody reads a day of log
     * without meeting it, rare enough not to be noise.
     */
    private static final long REMINDER_INTERVAL = 20L * 60L * 10L;

    /**
     * The ticket that keeps a level's entities ticking with nobody in it. Loading and simulation because a ticket
     * that does neither is refused by {@code addTicketWithRadius}, and the dimension flag because that is the whole
     * point of it. Not persistent: {@code TicketStorage} writes out only the ticket types that say they persist, so
     * this one cannot outlive the server that registered it -- which is exactly what is wanted of it.
     */
    private static final TicketType KEEP_ACTIVE_TYPE =
      new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);

    /**
     * Radius of that ticket. Zero, so it holds its own chunk at {@code FULL} and nothing around it: it is registered
     * for the flag it carries, not for the ground it covers.
     */
    private static final int KEEP_ACTIVE_RADIUS = 0;

    /**
     * Where a keep-active ticket has been registered, so that switching the mode off gives every one of them back.
     * Written and read from the server thread only.
     */
    private static final Map<ResourceKey<Level>, LongSet> keptActive = new HashMap<>();

    /**
     * Whether the JVM was started with the arming property. Read once and never again -- a value that could change
     * while the server runs would be a value something in the game could change.
     */
    private static final boolean ARMED = Boolean.parseBoolean(System.getProperty(ARM_PROPERTY));

    /**
     * Whether the mode is on right now. Written from the server thread by the command, read from the colony tick.
     */
    private static volatile boolean running = false;

    /**
     * Game time of the next reminder in the log.
     */
    private static long nextReminder = 0;

    private HeadlessColonyMode()
    {
        throw new IllegalStateException("Tried to initialize: HeadlessColonyMode but this is a Utility class.");
    }

    /**
     * Whether this JVM may be asked for the mode at all.
     *
     * @return true if the arming property was set on the command line.
     */
    public static boolean isArmed()
    {
        return ARMED;
    }

    /**
     * Whether colonies should tick without anybody watching them.
     *
     * @return true only when the JVM was armed and an operator switched the mode on during this run.
     */
    public static boolean isRunning()
    {
        return ARMED && running;
    }

    /**
     * Switch the mode on or off.
     *
     * @param server the server the command was run on.
     * @param on     the state asked for.
     * @return what happened, for the command to report.
     */
    public static Result set(@NotNull final MinecraftServer server, final boolean on)
    {
        if (!ARMED)
        {
            return Result.NOT_ARMED;
        }

        if (!server.isDedicatedServer())
        {
            return Result.NOT_DEDICATED;
        }

        if (running == on)
        {
            return on ? Result.ALREADY_ON : Result.ALREADY_OFF;
        }

        running = on;

        if (!on)
        {
            releaseDimensions(server);
            Log.getLogger().warn("Headless colony mode is OFF: colonies go back to ticking only while somebody is watching them.");
            return Result.SWITCHED_OFF;
        }

        nextReminder = server.overworld().getGameTime() + REMINDER_INTERVAL;
        Log.getLogger().warn("Headless colony mode is ON: colonies on this server will tick with no player watching them. "
                               + "This is not how the mod behaves on an ordinary server, and it lasts only until this server stops.");
        return Result.SWITCHED_ON;
    }

    /**
     * Keep the level a colony is in ticking its entities, for as long as the mode is on.
     * <p>
     * Idempotent and cheap to call on a beat: a position already held is a set lookup and nothing else.
     *
     * @param level the level to keep awake.
     * @param pos   the chunk to hang the ticket on, normally the colony's centre.
     */
    public static void holdDimensionActive(@NotNull final ServerLevel level, @NotNull final ChunkPos pos)
    {
        if (!isRunning())
        {
            return;
        }

        if (keptActive.computeIfAbsent(level.dimension(), key -> new LongOpenHashSet()).add(pos.pack()))
        {
            level.getChunkSource().addTicketWithRadius(KEEP_ACTIVE_TYPE, pos, KEEP_ACTIVE_RADIUS);
        }
    }

    /**
     * Give back every keep-active ticket. Switching the mode off has to leave the server as it found it, and a level
     * that goes on ticking its entities after the switch would be indistinguishable from the switch not working.
     *
     * @param server the server holding the levels, or null if it is already gone.
     */
    private static void releaseDimensions(final MinecraftServer server)
    {
        if (server != null)
        {
            for (final Map.Entry<ResourceKey<Level>, LongSet> entry : keptActive.entrySet())
            {
                final ServerLevel level = server.getLevel(entry.getKey());
                if (level == null)
                {
                    continue;
                }
                for (final long packed : entry.getValue())
                {
                    level.getChunkSource().removeTicketWithRadius(KEEP_ACTIVE_TYPE, ChunkPos.unpack(packed), KEEP_ACTIVE_RADIUS);
                }
            }
        }

        keptActive.clear();
    }

    /**
     * Report at startup that this server can be asked for the mode, so that an operator who inherited a start script
     * finds out from the log rather than from the behaviour.
     *
     * @param server the server that just started.
     */
    public static void onServerStarted(@NotNull final MinecraftServer server)
    {
        if (!ARMED)
        {
            return;
        }

        if (!server.isDedicatedServer())
        {
            Log.getLogger().warn("-D{}=true was passed to a client JVM. Headless colony mode is refused on an integrated server and nothing will change.",
              ARM_PROPERTY);
            return;
        }

        Log.getLogger().warn("-D{}=true: this server can be put into headless colony mode with '/mc debug headless on'. "
                               + "It is off until an operator asks for it, and it does not survive a restart.", ARM_PROPERTY);
    }

    /**
     * Repeat the warning while the mode is on.
     *
     * @param server the ticking server.
     */
    public static void onServerTick(@NotNull final MinecraftServer server)
    {
        if (!running)
        {
            return;
        }

        final long now = server.overworld().getGameTime();
        if (now < nextReminder)
        {
            return;
        }

        nextReminder = now + REMINDER_INTERVAL;
        Log.getLogger().warn("Headless colony mode is still ON. Colonies are ticking with no player watching them; '/mc debug headless off' ends it.");
    }

    /**
     * Forget the mode. Bound to both ends of the server lifecycle rather than only to shutdown, because an integrated
     * server starts and stops repeatedly inside one JVM and the flag must not carry from one world into the next.
     *
     * @param server the server the mode is being forgotten on.
     */
    public static void reset(final MinecraftServer server)
    {
        if (running)
        {
            Log.getLogger().warn("Headless colony mode ends with this server.");
        }
        running = false;
        nextReminder = 0;
        releaseDimensions(server);
    }

    /**
     * The outcome of asking for the mode.
     */
    public enum Result
    {
        /**
         * The mode is now on.
         */
        SWITCHED_ON,

        /**
         * The mode is now off.
         */
        SWITCHED_OFF,

        /**
         * It was already on.
         */
        ALREADY_ON,

        /**
         * It was already off.
         */
        ALREADY_OFF,

        /**
         * The JVM was not started with the arming property.
         */
        NOT_ARMED,

        /**
         * This is an integrated server.
         */
        NOT_DEDICATED
    }
}
