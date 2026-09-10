package com.minecolonies.core.client.assetfetch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The install-time patch bundle: {@code transforms.json} plus the patches it names.
 *
 * <p>This is task C3. It turns the fetched upstream asset tree into the port's asset tree without ever having
 * shipped a copy of either. Two sections drive it:</p>
 * <ul>
 *     <li>{@code files} — 65 upstream files edited in place. Steps run in order: the composite-flatten
 *         {@code rule}, an RFC 6902 {@code jsonPatch}, or a {@code unifiedDiff} for the six BlockUI XMLs.</li>
 *     <li>{@code derivedFiles} — 4 files that do not exist upstream and are near-copies of one that does.
 *         Each starts from a {@code copyFrom} path and takes at most one patch.</li>
 * </ul>
 *
 * <p>JSON results are written through {@link CanonicalJson}; XML results are written back as UTF-8 exactly
 * as the diff produced them.</p>
 *
 * <h2>A file to patch that is not there</h2>
 *
 * <p>Every other missing file is now forgiven — a path the archive does not carry is taken from the pack
 * already installed, or left out — and these 66 are the deliberate exception: the run stops and the source
 * fails. Two reasons, and both of them are about honesty rather than strictness.</p>
 * <ul>
 *     <li>What these entries produce is not upstream's file, it is this port's, made by editing upstream's.
 *         With the input gone there is nothing to make it out of, and quietly falling back to the copy in the
 *         player's existing pack would put a file derived from one upstream build into a pack made of
 *         another — the one case where taking the old file is not the safe answer.</li>
 *     <li>It is the only structural check left on an archive nothing else vouches for. An unpinned source
 *         reaches the pack on the strength of what it carries; requiring that it carry the files this port
 *         has to edit is what tells a MineColonies asset tree from any other zip that happened to answer.</li>
 * </ul>
 *
 * <p>For an unpinned source that is not fatal to the install: the chain treats it as that source failing and
 * goes on to the next.</p>
 */
public final class PatchBundle
{
    /**
     * Name of the recipe inside the bundle.
     */
    private static final String TRANSFORMS = "transforms.json";

    /**
     * The {@code formatVersion} this code understands.
     */
    private static final int FORMAT_VERSION = 1;

    /**
     * Where the bundle's own files come from.
     */
    private final BundleResources resources;

    /**
     * The parsed {@code transforms.json}.
     */
    private final JsonObject transforms;

    /**
     * Creates a bundle over an already-parsed recipe.
     *
     * @param resources  the bundle resource reader.
     * @param transforms the parsed {@code transforms.json}.
     */
    private PatchBundle(final BundleResources resources, final JsonObject transforms)
    {
        this.resources = resources;
        this.transforms = transforms;
    }

    /**
     * Loads the bundle.
     *
     * @param resources where to read it from.
     * @return the loaded bundle.
     * @throws AssetInstallException if the recipe is missing, unreadable or of an unknown format version.
     */
    public static PatchBundle load(final BundleResources resources) throws AssetInstallException
    {
        final JsonElement parsed;
        try
        {
            parsed = CanonicalJson.parse(resources.read(TRANSFORMS), TRANSFORMS);
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("The install bundle's transforms.json could not be read", e);
        }
        if (!parsed.isJsonObject())
        {
            throw new AssetInstallException("The install bundle's transforms.json is not a JSON object");
        }
        final JsonObject root = parsed.getAsJsonObject();
        final JsonElement version = root.get("formatVersion");
        if (version == null || !version.isJsonPrimitive() || version.getAsInt() != FORMAT_VERSION)
        {
            throw new AssetInstallException("The install bundle's transforms.json has an unsupported formatVersion");
        }
        return new PatchBundle(resources, root);
    }

    /**
     * How many entries the recipe has, for progress reporting.
     *
     * @return the number of edited plus derived files.
     */
    public int size()
    {
        return array("files").size() + array("derivedFiles").size();
    }

    /**
     * Applies the whole bundle to an extracted asset tree.
     *
     * @param assetsRoot the {@code assets/minecolonies} directory of the staged pack.
     * @param progress   called after each file, with the number done so far; may be null.
     * @param cancelled  polled between files; when it reports true the run stops with an exception.
     * @return the paths written, relative to {@code assetsRoot}.
     * @throws AssetInstallException if any step fails.
     */
    public List<String> apply(final Path assetsRoot, final ProgressSink progress, final CancelSignal cancelled) throws AssetInstallException
    {
        final List<String> written = new ArrayList<>();

        for (final JsonElement entry : array("files"))
        {
            checkCancelled(cancelled);
            final JsonObject file = entry.getAsJsonObject();
            final String relative = string(file, "path");
            applyOne(assetsRoot, relative, relative, file.getAsJsonArray("steps"));
            written.add(relative);
            report(progress, written.size());
        }

        for (final JsonElement entry : array("derivedFiles"))
        {
            checkCancelled(cancelled);
            final JsonObject file = entry.getAsJsonObject();
            final String relative = string(file, "path");
            applyOne(assetsRoot, string(file, "copyFrom"), relative, file.getAsJsonArray("steps"));
            written.add(relative);
            report(progress, written.size());
        }

        return written;
    }

    /**
     * Runs one entry's steps and writes the result.
     *
     * @param assetsRoot the {@code assets/minecolonies} directory.
     * @param sourceRel  the file the steps start from.
     * @param targetRel  the file the result is written to; equal to {@code sourceRel} for an in-place edit.
     * @param steps      the steps, in order.
     * @throws AssetInstallException if a step fails or the source file is missing.
     */
    private void applyOne(final Path assetsRoot, final String sourceRel, final String targetRel, final JsonArray steps) throws AssetInstallException
    {
        final Path source = assetsRoot.resolve(sourceRel);
        final Path target = assetsRoot.resolve(targetRel);
        if (!Files.isRegularFile(source))
        {
            // Deliberately fatal to this archive; see the note on this class about why this one absence is
            // not forgiven the way every other one is.
            throw new AssetInstallException("The downloaded assets do not contain " + sourceRel + ", which the install bundle has to patch");
        }

        try
        {
            if (targetRel.endsWith(".xml"))
            {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                for (final JsonElement rawStep : steps)
                {
                    final JsonObject step = rawStep.getAsJsonObject();
                    if (!"unifiedDiff".equals(string(step, "op")))
                    {
                        throw new AssetInstallException("Step '" + string(step, "op") + "' is not defined for the XML file " + targetRel);
                    }
                    final String diff = new String(this.resources.read(string(step, "patch")), StandardCharsets.UTF_8);
                    text = UnifiedDiffPatcher.apply(text, diff, targetRel);
                }
                Files.createDirectories(target.getParent());
                Files.write(target, text.getBytes(StandardCharsets.UTF_8));
            }
            else
            {
                JsonElement document = CanonicalJson.parse(source);
                for (final JsonElement rawStep : steps)
                {
                    final JsonObject step = rawStep.getAsJsonObject();
                    final String op = string(step, "op");
                    switch (op)
                    {
                        case "rule" ->
                        {
                            final String rule = string(step, "rule");
                            if (!CompositeFlatten.RULE_ID.equals(rule))
                            {
                                throw new AssetInstallException("The install bundle asks for an unknown rule '" + rule + "'");
                            }
                            if (!document.isJsonObject() || !CompositeFlatten.isComposite(document))
                            {
                                throw new AssetInstallException("The upstream " + sourceRel + " is no longer a " + rule + " candidate");
                            }
                            document = CompositeFlatten.flatten(document.getAsJsonObject());
                        }
                        case "jsonPatch" ->
                        {
                            final JsonElement patch = CanonicalJson.parse(this.resources.read(string(step, "patch")), string(step, "patch"));
                            if (!patch.isJsonArray())
                            {
                                throw new AssetInstallException("The patch " + string(step, "patch") + " is not a JSON Patch array");
                            }
                            document = JsonPatch.apply(document, patch.getAsJsonArray());
                        }
                        default -> throw new AssetInstallException("Step '" + op + "' is not defined for the JSON file " + targetRel);
                    }
                }
                Files.createDirectories(target.getParent());
                Files.write(target, CanonicalJson.toBytes(document));
            }
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("Patching " + targetRel + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Reads a top-level array of the recipe, tolerating its absence.
     *
     * @param member the member name.
     * @return the array, or an empty one.
     */
    private JsonArray array(final String member)
    {
        final JsonElement value = this.transforms.get(member);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    /**
     * Reads a required string member.
     *
     * @param object the object to read from.
     * @param member the member name.
     * @return its value.
     * @throws AssetInstallException if it is absent or not a string.
     */
    private static String string(final JsonObject object, final String member) throws AssetInstallException
    {
        final JsonElement value = object.get(member);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
        {
            throw new AssetInstallException("The install bundle is missing a string '" + member + "' in " + object);
        }
        return value.getAsString();
    }

    /**
     * Reports progress if anybody is listening.
     *
     * @param progress the sink, may be null.
     * @param done     how many files are done.
     */
    private static void report(final ProgressSink progress, final int done)
    {
        if (progress != null)
        {
            progress.accept(done);
        }
    }

    /**
     * Stops the run when the player cancelled.
     *
     * @param cancelled the signal, may be null.
     * @throws AssetInstallException if it reports cancellation.
     */
    private static void checkCancelled(final CancelSignal cancelled) throws AssetInstallException
    {
        if (cancelled != null && cancelled.isCancelled())
        {
            throw new InstallCancelledException();
        }
    }

    /**
     * How many files have been patched so far.
     */
    @FunctionalInterface
    public interface ProgressSink
    {
        /**
         * Reports progress.
         *
         * @param done the number of files finished.
         */
        void accept(int done);
    }
}
