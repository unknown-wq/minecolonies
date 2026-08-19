package com.minecolonies.core.client.assetfetch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Reads and writes {@code state.json}, the install record (task C1).
 *
 * <p>Schema version 1, exactly as documented on {@link AssetFetch} — that class is the read side of this
 * contract and treats anything it does not understand as "not installed", so the two must not drift:</p>
 * <pre>
 * {
 *   "version": 1,
 *   "status": "installed" | "declined",
 *   "sourceId": ..., "sourceUrl": ..., "jarSha256": ..., "manifestSha256": ...,
 *   "installedAt": ..., "customSourceUrl": ...
 * }
 * </pre>
 *
 * <p>Two rules hold everywhere in this class. Writes are atomic — a temporary file in the same directory
 * followed by a move — because a half-written state file is exactly the "half-installed" condition the design
 * forbids. And {@code customSourceUrl} is <em>preserved</em> across every rewrite: it is the owner's
 * operational override for source 3, it is set independently of any install, and losing it on the next
 * install would silently undo the owner's fix.</p>
 */
public final class InstallState
{
    /**
     * The schema version written and accepted.
     */
    public static final int SCHEMA_VERSION = 1;

    /**
     * {@code status} for a verified install.
     */
    public static final String STATUS_INSTALLED = "installed";

    /**
     * {@code status} for "the player said not now".
     */
    public static final String STATUS_DECLINED = "declined";

    /**
     * Suffix of the temporary file an atomic write goes through.
     */
    private static final String TEMP_SUFFIX = ".tmp";

    /**
     * The raw object last read, or an empty object.
     */
    private final JsonObject root;

    /**
     * Wraps a parsed state object.
     *
     * @param root the object, never null.
     */
    private InstallState(final JsonObject root)
    {
        this.root = root;
    }

    /**
     * Reads the state file, tolerating every kind of damage.
     *
     * @param stateFile the file, which need not exist.
     * @return the state; an empty one when the file is missing or unusable.
     */
    public static InstallState read(final Path stateFile)
    {
        if (!Files.isRegularFile(stateFile))
        {
            return new InstallState(new JsonObject());
        }
        try (Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8))
        {
            final JsonElement parsed = JsonParser.parseReader(reader);
            return new InstallState(parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject());
        }
        catch (final IOException | RuntimeException e)
        {
            // Deliberately silent about the cause here: AssetFetch already logs a damaged state file, and the
            // installer's job is to overwrite it, not to complain about it twice.
            return new InstallState(new JsonObject());
        }
    }

    /**
     * The recorded status.
     *
     * @return {@code installed}, {@code declined}, or null when neither has been recorded.
     */
    public String status()
    {
        return string("status");
    }

    /**
     * Whether the state records a completed install.
     *
     * @return true if {@code status} is {@code installed}.
     */
    public boolean isInstalled()
    {
        return STATUS_INSTALLED.equals(status()) && version() == SCHEMA_VERSION;
    }

    /**
     * Whether the player has said no.
     *
     * @return true if {@code status} is {@code declined}.
     */
    public boolean isDeclined()
    {
        return STATUS_DECLINED.equals(status()) && version() == SCHEMA_VERSION;
    }

    /**
     * The owner's source-3 override.
     *
     * <p>Read regardless of {@code status} and regardless of schema version: it is the escape hatch for a
     * broken install, so it has to survive a state file that is otherwise unusable.</p>
     *
     * @return the URL, or null when unset.
     */
    public String customSourceUrl()
    {
        final String value = string("customSourceUrl");
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * The source the current install came from.
     *
     * @return the source id, or null.
     */
    public String sourceId()
    {
        return string("sourceId");
    }

    /**
     * The schema version of the file that was read.
     *
     * @return the version, or -1.
     */
    public int version()
    {
        final JsonElement value = this.root.get("version");
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
        {
            return -1;
        }
        return value.getAsInt();
    }

    /**
     * Records a completed, verified install.
     *
     * @param stateFile      where to write.
     * @param sourceId       which chain entry produced it.
     * @param sourceUrl      the URL or local path it came from.
     * @param jarSha256      the whole-jar hash that was accepted.
     * @param manifestSha256 the hash of the manifest it was verified against.
     * @throws AssetInstallException if the file cannot be written.
     */
    public static void writeInstalled(final Path stateFile, final String sourceId, final String sourceUrl,
        final String jarSha256, final String manifestSha256) throws AssetInstallException
    {
        final JsonObject out = base(stateFile);
        out.addProperty("status", STATUS_INSTALLED);
        out.addProperty("sourceId", sourceId);
        out.addProperty("sourceUrl", sourceUrl);
        out.addProperty("jarSha256", jarSha256);
        out.addProperty("manifestSha256", manifestSha256);
        out.addProperty("installedAt", Instant.now().toString());
        write(stateFile, out);
    }

    /**
     * Records that the player declined.
     *
     * <p>Everything an install would have recorded is left out, so a later install writes a clean record.</p>
     *
     * @param stateFile where to write.
     * @throws AssetInstallException if the file cannot be written.
     */
    public static void writeDeclined(final Path stateFile) throws AssetInstallException
    {
        final JsonObject out = base(stateFile);
        out.addProperty("status", STATUS_DECLINED);
        out.addProperty("installedAt", Instant.now().toString());
        write(stateFile, out);
    }

    /**
     * Sets or clears the owner's source-3 override, leaving the install status alone.
     *
     * @param stateFile where to write.
     * @param url       the URL, or null/blank to clear it.
     * @throws AssetInstallException if the file cannot be written.
     */
    public static void writeCustomSourceUrl(final Path stateFile, final String url) throws AssetInstallException
    {
        final InstallState current = read(stateFile);
        final JsonObject out = current.root.deepCopy();
        out.addProperty("version", SCHEMA_VERSION);
        if (url == null || url.isBlank())
        {
            out.remove("customSourceUrl");
        }
        else
        {
            out.addProperty("customSourceUrl", url.trim());
        }
        write(stateFile, out);
    }

    /**
     * Starts a new state object, carrying over the one field that must survive a rewrite.
     *
     * @param stateFile the file being rewritten.
     * @return a fresh object with {@code version} and any existing {@code customSourceUrl}.
     */
    private static JsonObject base(final Path stateFile)
    {
        final JsonObject out = new JsonObject();
        out.addProperty("version", SCHEMA_VERSION);
        final String custom = read(stateFile).customSourceUrl();
        if (custom != null)
        {
            out.addProperty("customSourceUrl", custom);
        }
        return out;
    }

    /**
     * Writes the state file atomically.
     *
     * @param stateFile the destination.
     * @param content   what to write.
     * @throws AssetInstallException if it cannot be written.
     */
    private static void write(final Path stateFile, final JsonObject content) throws AssetInstallException
    {
        final Path temporary = stateFile.resolveSibling(stateFile.getFileName() + TEMP_SUFFIX);
        try
        {
            Files.createDirectories(stateFile.getParent());
            Files.write(temporary, CanonicalJson.toBytes(content));
            try
            {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (final AtomicMoveNotSupportedException e)
            {
                // Some Windows filesystems refuse an atomic replace. A plain replace is still a single
                // rename, which is all the guarantee this file actually needs.
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("Could not write the install state to " + stateFile + ": " + e.getMessage(), e);
        }
    }

    /**
     * Reads a string member, tolerating absence and wrong types.
     *
     * @param member the member name.
     * @return the value, or null.
     */
    private String string(final String member)
    {
        final JsonElement value = this.root.get(member);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
        {
            return null;
        }
        return value.getAsString();
    }
}
