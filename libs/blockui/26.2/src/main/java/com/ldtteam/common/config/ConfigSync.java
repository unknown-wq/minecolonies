package com.ldtteam.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The wire format of the server -&gt; client config sync, and the whole of its logic. Deliberately free of any
 * Minecraft, Fabric or {@code FabricLoader} reference so that it can be unit tested headlessly - everything that
 * needs the game lives in {@link ConfigSyncManager} and {@link ConfigSyncMessage}.
 * <p>
 * <b>What this restores.</b> NeoForge's {@code ConfigTracker} shipped every SERVER config to a client at login,
 * so that client-side code reading e.g. a distance limit or a difficulty multiplier saw the value the server
 * actually plays by. The port dropped it, which left a remote client evaluating those settings against its own
 * local defaults. This is that mechanism, rebuilt on BlockUI's own {@code PlayMessageType} layer.
 * <p>
 * <b>The format is a flat TOML document</b>, one {@code path = value} line per setting, exactly the dialect
 * {@link ConfigStore} already writes and {@link FlatToml} already parses. That is not laziness:
 * <ul>
 * <li>there are no type tags on the wire, so a client and a server that declare the same key with different
 * types cannot desync into a decode failure - the value arrives as a TOML scalar and is coerced into whatever
 * <em>this</em> side declared, by {@link ConfigCoercion}, i.e. by the same code that already survives a
 * hand-mangled config file;</li>
 * <li>a key only one side knows is a line the other side ignores, never a protocol error;</li>
 * <li>it stays readable in a packet dump, which matters for a feature nobody can unit test end to end;</li>
 * <li>56 settings - MineColonies' SERVER count - come to roughly 2 KB, sent once per login. Compressing that
 * would cost more than it saves, and the vanilla packet pipeline compresses it anyway.</li>
 * </ul>
 * <b>Only SERVER configs are sent.</b> CLIENT configs are the player's own and must never be touched. COMMON
 * configs are deliberately left alone too: NeoForge did not sync them either, both sides load their own copy,
 * and downstream code is written against that. The transport below has no opinion on type, so should COMMON ever
 * have to follow, only {@link ConfigSyncManager} needs to change.
 *
 * @see ConfigValue#applySync(Object)
 */
final class ConfigSync
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigSync.class);

    /**
     * Refuses to decode a document larger than this. A sane mod is three orders of magnitude below it; the cap
     * only exists so a hostile or broken server cannot make a client allocate without bound.
     */
    static final int MAX_DOCUMENT_BYTES = 1 << 20;

    private ConfigSync()
    {
        throw new IllegalStateException("Tried to initialize: ConfigSync but this is a Utility class.");
    }

    /**
     * Outcome of {@link #apply(Collection, String)} - what changed, and everything worth logging once instead of
     * once per key.
     *
     * @param changed        values whose {@link ConfigValue#get()} now answers differently, i.e. the ones whose
     *                       watchers have to fire
     * @param applied        keys the server sent and this side accepted
     * @param missingLocally keys the server sent that this side does not declare (server is newer, or has a mod
     *                       configuration this build predates)
     * @param missingRemotely keys this side declares that the server did not send (server is older) - those keep
     *                       the local value
     * @param problems       human readable description of every value that could not be used
     */
    record Outcome(List<ConfigValue<?>> changed,
        int applied,
        int missingLocally,
        int missingRemotely,
        List<String> problems)
    {}

    /**
     * Renders the values a server hands to a joining client.
     *
     * @param  values the SERVER configuration's values
     * @return        flat TOML document; empty string when there is nothing to send
     */
    static String encode(final Collection<ConfigValue<?>> values)
    {
        final StringBuilder sb = new StringBuilder(Math.max(64, values.size() * 48));
        for (final ConfigValue<?> value : values)
        {
            // getLocalValue, not get: a listen server hosting a LAN game must send what it plays by, and its own
            // values are the local ones. On a dedicated server the two are always the same thing.
            sb.append(value.getPath()).append(" = ").append(FlatToml.format(value.getLocalValue())).append('\n');
        }
        return sb.toString();
    }

    /**
     * Pushes a received document over the given values. Never throws: every failure mode - an unparseable
     * document, an unknown key, a key whose type does not match, a value out of this side's range - degrades to
     * "keep the local value" and a log line.
     *
     * @param  values   the SERVER configuration's values
     * @param  document what {@link #encode(Collection)} produced on the other side
     * @return          what changed and what went wrong
     */
    static Outcome apply(final Collection<ConfigValue<?>> values, final String document)
    {
        final List<String> problems = new ArrayList<>();
        final Map<String, Object> parsed = parse(document, problems);

        final List<ConfigValue<?>> changed = new ArrayList<>();
        int applied = 0;
        int missingRemotely = 0;

        for (final ConfigValue<?> value : values)
        {
            final Object raw = parsed.remove(value.getPath());

            if (raw == null)
            {
                // the server does not know this key at all - most likely an older mod version over there. Our own
                // value is the best answer we have, and dropping a stale overlay is what makes a reconnect to a
                // different server safe.
                missingRemotely++;
                if (value.clearSync())
                {
                    changed.add(value);
                }
                continue;
            }

            try
            {
                if (applyOne(value, raw))
                {
                    changed.add(value);
                }
                applied++;
            }
            catch (final RuntimeException e)
            {
                problems.add("'" + value.getPath() + "': " + e.getMessage() + "; keeping the local value");
                if (value.clearSync())
                {
                    changed.add(value);
                }
            }
        }

        if (!parsed.isEmpty())
        {
            problems.add("the server sent " + parsed.size() + " setting(s) this installation does not know: " +
                shortList(parsed.keySet()));
        }

        return new Outcome(changed, applied, parsed.size(), missingRemotely, problems);
    }

    /**
     * Drops every server override, i.e. puts the client back on its own settings. Called on disconnect.
     *
     * @param  values the SERVER configuration's values
     * @return        values whose {@link ConfigValue#get()} now answers differently
     */
    static List<ConfigValue<?>> revert(final Collection<ConfigValue<?>> values)
    {
        final List<ConfigValue<?>> changed = new ArrayList<>();
        for (final ConfigValue<?> value : values)
        {
            if (value.clearSync())
            {
                changed.add(value);
            }
        }
        return changed;
    }

    /**
     * @return true when any of the values is currently overridden by a server
     */
    static boolean isAnySynced(final Collection<ConfigValue<?>> values)
    {
        return values.stream().anyMatch(ConfigValue::isSynced);
    }

    /**
     * Captures the generic parameter so the coercion and the assignment agree on a type.
     */
    private static <T> boolean applyOne(final ConfigValue<T> value, final Object raw)
    {
        return value.applySync(ConfigCoercion.coerce(raw, value.getDefault()));
    }

    private static Map<String, Object> parse(final String document, final List<String> problems)
    {
        try
        {
            return FlatToml.parse(document, problems);
        }
        catch (final RuntimeException e)
        {
            // FlatToml recovers per entry, so this can only be something pathological; treat the whole document
            // as absent rather than letting it escape into the network handler
            problems.add("the document could not be read at all (" + e + "); every value keeps its local setting");
            LOGGER.debug("Unreadable config sync document", e);
            return new LinkedHashMap<>();
        }
    }

    private static String shortList(final Collection<String> keys)
    {
        final int limit = 8;
        final List<String> shown = keys.stream().limit(limit).toList();
        return keys.size() <= limit ? String.join(", ", shown) : String.join(", ", shown) + ", ...";
    }
}
