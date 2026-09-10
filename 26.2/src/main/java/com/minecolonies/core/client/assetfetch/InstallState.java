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
 *   "status": "installed",  // "declined" is legacy, read-only -- see STATUS_DECLINED
 *   "sourceId": ..., "sourceUrl": ..., "jarSha256": ..., "manifestSha256": ...,
 *   "complete": true, "filesAbsent": 0, "filesCarried": 0,   // the counts are omitted when 0; see writeInstalled
 *   "installedAt": ..., "customSourceUrl": ...
 * }
 * </pre>
 *
 * <p>Still schema version 1, deliberately. {@code filesCarried} replaced a {@code filesSkipped} that older
 * builds wrote; both are advisory counts and an unknown one is simply not read, so a file written by such a
 * build still describes an install this build can serve. Bumping the version for a counter would have made
 * every existing install read as "not installed" and taken the player's assets away on the spot, which is
 * exactly the failure the rest of this class exists to prevent.</p>
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
     * {@code status} for a finished install.
     */
    public static final String STATUS_INSTALLED = "installed";

    /**
     * {@code status} for "the player said not now".
     *
     * <p><b>Legacy, read-only.</b> Builds up to 0.0.52 wrote it and treated it as permanent; nothing writes
     * it any more, because a decline now lasts one session. It is kept so a file written by such a build
     * still parses into a state that is recognisably not an install.</p>
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
     * @return {@code installed}, the legacy {@code declined}, or null when neither has been recorded.
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
     * <p>Only a file written by a build up to 0.0.52 can say so; a decline is no longer persisted. Kept so
     * that such a file is understood rather than merely unrecognised.</p>
     *
     * @return true if {@code status} is the legacy {@code declined}.
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
     * The manifest the recorded install was made against.
     *
     * @return the hash, or null when the file does not carry one.
     */
    public String manifestSha256()
    {
        final String value = string("manifestSha256");
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Whether the recorded install was made against the given manifest, and is therefore the set of files
     * this build expects rather than one left behind by an earlier one.
     *
     * <p>An install that records no manifest at all does not match: every build that has ever written this
     * file wrote the field, so a state file without it has been damaged or written by something else, and the
     * one thing that can be said about the pack next to it is that nothing vouches for it.</p>
     *
     * <p>Completeness is a separate question and is deliberately not asked here. A pack put together from a
     * source that could not supply part of the file set is recorded as incomplete ({@code complete} false,
     * see {@link #isComplete()}) and still matches the manifest it was installed against, because it is
     * exactly as much as that source had to give. Folding the two together would reinstall such a pack on
     * every single launch, for ever, and end each time with the same files missing.</p>
     *
     * @param manifestSha256 the hash of the manifest this build ships.
     * @return true when the two agree.
     */
    public boolean matchesManifest(final String manifestSha256)
    {
        final String recorded = manifestSha256();
        return recorded != null && recorded.equals(manifestSha256);
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
     * Records a completed install.
     *
     * @param stateFile      where to write.
     * @param sourceId       which chain entry produced it.
     * @param sourceUrl      the URL or local path it came from.
     * @param jarSha256      the whole-archive hash that was accepted, which for a source that pins none is
     *                       simply the hash of what arrived, recorded rather than checked.
     * @param manifestSha256 the hash of the manifest it was installed against.
     * @param filesAbsent    how many of the manifest's files the pack does not carry, because neither the
     *                       archive nor an earlier install had them. Zero for a complete install, which is
     *                       what {@code complete} is written from.
     * @param filesCarried   how many of them the archive did not supply and were kept from the pack that was
     *                       installed before. They are in the pack, so they do not make it incomplete; the
     *                       count is recorded so a later run, and the player, can see how much of this
     *                       install is really the previous one.
     * @throws AssetInstallException if the file cannot be written.
     */
    public static void writeInstalled(final Path stateFile, final String sourceId, final String sourceUrl,
        final String jarSha256, final String manifestSha256, final int filesAbsent, final int filesCarried)
        throws AssetInstallException
    {
        final JsonObject out = base(stateFile);
        out.addProperty("status", STATUS_INSTALLED);
        out.addProperty("sourceId", sourceId);
        out.addProperty("sourceUrl", sourceUrl);
        out.addProperty("jarSha256", jarSha256);
        out.addProperty("manifestSha256", manifestSha256);
        out.addProperty("complete", filesAbsent == 0);
        if (filesAbsent > 0)
        {
            out.addProperty("filesAbsent", filesAbsent);
        }
        if (filesCarried > 0)
        {
            out.addProperty("filesCarried", filesCarried);
        }
        out.addProperty("installedAt", Instant.now().toString());
        write(stateFile, out);
    }

    /**
     * Whether the recorded install carries every file the manifest lists.
     *
     * <p>An install written by a build that did not know about incomplete sources says nothing about this,
     * and such an install was necessarily complete, so silence reads as complete.</p>
     *
     * @return false only when the state file says so outright.
     */
    public boolean isComplete()
    {
        final JsonElement value = this.root.get("complete");
        return value == null || !value.isJsonPrimitive() || value.getAsBoolean();
    }

    /**
     * How many of the manifest's files the installed pack does not carry at all.
     *
     * @return the count, or 0 when the install is complete or the file does not say.
     */
    public int filesAbsent()
    {
        return count("filesAbsent");
    }

    /**
     * How many of the installed pack's files came from the install before it rather than from the archive.
     *
     * @return the count, or 0 when the archive supplied everything or the file does not say.
     */
    public int filesCarried()
    {
        return count("filesCarried");
    }

    /**
     * Reads a non-negative count, tolerating absence and wrong types.
     *
     * @param member the member name.
     * @return the value, or 0.
     */
    private int count(final String member)
    {
        final JsonElement value = this.root.get(member);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
        {
            return 0;
        }
        return Math.max(0, value.getAsInt());
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
            // The temporary file is worthless without the rename that was to make it the record.
            try
            {
                Files.deleteIfExists(temporary);
            }
            catch (final IOException ignored)
            {
                // Nothing more to do: the real failure is the one reported.
            }
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
