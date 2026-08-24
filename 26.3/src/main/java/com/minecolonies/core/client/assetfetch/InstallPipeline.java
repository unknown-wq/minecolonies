package com.minecolonies.core.client.assetfetch;

import com.minecolonies.api.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The install itself: download, extract, patch, verify, promote (tasks C2, C3, C8, C9).
 *
 * <p>Pure Java over {@link Path} arguments — no Minecraft or Fabric types anywhere in it — so the whole thing
 * can be run headlessly against a scratch directory, which is how it was tested. {@link AssetInstaller} is
 * the thin layer that fills in the game's own paths and pack format and runs this off the client thread.</p>
 *
 * <p><b>Nothing outside the temporary directory is touched until the very end.</b> The jar is downloaded to
 * {@code tmp/}, unpacked into {@code tmp/stage/}, patched there, given its {@code pack.mcmeta} there and
 * verified there. Only once every file matches the manifest is the staged tree swapped into place and
 * {@code state.json} rewritten. A failure at any earlier point deletes {@code tmp/} and returns, leaving an
 * existing install — or the clean pre-consent state — exactly as it was. That is the escalation rule applied
 * to shipped code: a failed download must never leave a half-installed game.</p>
 */
public final class InstallPipeline
{
    /**
     * Name of the staging pack root inside {@code tmp/}.
     */
    private static final String STAGE_DIR = "stage";

    /**
     * Name of the downloaded jar inside {@code tmp/}.
     */
    private static final String DOWNLOAD_NAME = "upstream.jar";

    /**
     * Name of the directory an existing pack is parked in during the swap.
     */
    private static final String PREVIOUS_DIR = "previous-pack";

    /**
     * How many failing paths to name in a player-facing verification error.
     */
    private static final int NAMED_FAILURES = 5;

    /**
     * What to install and where.
     */
    private final InstallConfig config;

    /**
     * Where progress goes. Never null.
     */
    private final InstallListener listener;

    /**
     * Polled by every long step.
     */
    private final CancelSignal cancelled;

    /**
     * Every source tried so far, for the report.
     */
    private final List<SourceAttempt> attempts = new ArrayList<>();

    /**
     * Creates a pipeline.
     *
     * @param config    what to install and where.
     * @param listener  progress callbacks; may be null.
     * @param cancelled the cancellation signal; may be null.
     */
    public InstallPipeline(final InstallConfig config, final InstallListener listener, final CancelSignal cancelled)
    {
        this.config = config;
        this.listener = listener == null ? new InstallListener() { } : listener;
        this.cancelled = cancelled == null ? CancelSignal.never() : cancelled;
    }

    /**
     * Runs the whole install.
     *
     * <p>Never throws: every failure is turned into a report, because the caller is a UI that has to say
     * something useful either way.</p>
     *
     * @return what happened.
     */
    public InstallReport run()
    {
        try
        {
            return install();
        }
        catch (final InstallCancelledException e)
        {
            return report(InstallReport.Outcome.CANCELLED, null, e.getMessage());
        }
        catch (final AssetInstallException e)
        {
            Log.getLogger().error("MineColonies asset install failed", e);
            return report(InstallReport.Outcome.FAILED, null, e.getMessage());
        }
        catch (final RuntimeException e)
        {
            Log.getLogger().error("MineColonies asset install failed unexpectedly", e);
            return report(InstallReport.Outcome.FAILED, null, "an unexpected error: " + e);
        }
        finally
        {
            FileTrees.deleteQuietly(this.config.tempDir());
            this.listener.onPhase(InstallPhase.DONE);
        }
    }

    /**
     * The install proper.
     *
     * @return the report.
     * @throws AssetInstallException if any step fails.
     */
    private InstallReport install() throws AssetInstallException
    {
        this.listener.onPhase(InstallPhase.STARTING);

        final AssetManifest manifest = AssetManifest.load(this.config.bundle());
        final PatchBundle bundle = PatchBundle.load(this.config.bundle());

        try
        {
            Files.createDirectories(this.config.baseDir());
            FileTrees.deleteRecursively(this.config.tempDir());
            Files.createDirectories(this.config.tempDir());
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("Could not prepare " + this.config.baseDir() + ": " + e.getMessage(), e);
        }

        final Fetched fetched = fetch(manifest);
        if (fetched == null)
        {
            return report(InstallReport.Outcome.NO_SOURCE, null,
                "None of the download sources worked. " + describeAttempts());
        }

        try
        {
            return unpack(fetched, manifest, bundle);
        }
        catch (final InstallCancelledException e)
        {
            throw e;
        }
        catch (final AssetInstallException e)
        {
            if (fetched.unknownJar())
            {
                // A player-supplied jar with an unknown hash gets exactly one chance: it is accepted only if
                // it survives the whole pipeline. Whatever it failed on, what the player needs to be told is
                // which builds this version can actually use.
                throw new AssetInstallException("That jar is not a supported MineColonies build. Supported: "
                    + SourceChain.supportedVersions() + ". (" + e.getMessage() + ")", e);
            }
            throw e;
        }
    }

    /**
     * Everything after a jar has been accepted: unpack it, patch it, verify it, put it in place.
     *
     * @param fetched  the accepted jar.
     * @param manifest the install manifest.
     * @param bundle   the patch bundle.
     * @return the report.
     * @throws AssetInstallException if any step fails.
     */
    private InstallReport unpack(final Fetched fetched, final AssetManifest manifest, final PatchBundle bundle) throws AssetInstallException
    {
        final Path stage = this.config.tempDir().resolve(STAGE_DIR);
        final Path assetsRoot = stage.resolve(AssetManifest.ASSET_PREFIX);

        this.listener.onPhase(InstallPhase.EXTRACTING);
        final int extracted = JarAssetExtractor.extract(fetched.jar(), assetsRoot,
            done -> this.listener.onFiles(done, -1), this.cancelled);
        Log.getLogger().info("Extracted {} MineColonies asset files from {}", extracted, fetched.source().id());

        this.listener.onPhase(InstallPhase.PATCHING);
        final int patchTotal = bundle.size();
        final List<String> patched = bundle.apply(assetsRoot, done -> this.listener.onFiles(done, patchTotal), this.cancelled);

        PackMetaWriter.write(stage, this.config.packFormatMajor());

        this.listener.onPhase(InstallPhase.VERIFYING);
        final Map<String, AssetManifest.FileEntry> expected = manifest.effectiveFor(fetched.manifestSourceId());
        if (expected.isEmpty())
        {
            throw new AssetInstallException("The install manifest lists no files for source " + fetched.manifestSourceId());
        }
        final PackVerifier.Result verification = PackVerifier.verify(stage, expected,
            done -> this.listener.onFiles(done, expected.size()), this.cancelled);

        if (!verification.ok())
        {
            final String detail = verification.describeFailure(NAMED_FAILURES);
            Log.getLogger().error("Downloaded MineColonies assets did not verify: {}", detail);
            throw new AssetInstallException("The downloaded assets did not match the expected contents: " + detail);
        }

        final long packBytes = FileTrees.size(stage);

        this.listener.onPhase(InstallPhase.INSTALLING);
        promote(stage);

        InstallState.writeInstalled(this.config.stateFile(), fetched.installedSourceId(), fetched.source().url(),
            fetched.sha256(), manifest.sha256());
        stateChanged();

        Log.getLogger().info("Installed {} verified MineColonies asset files ({} bytes) from {}",
            verification.verified(), packBytes, fetched.installedSourceId());

        return new InstallReport(InstallReport.Outcome.INSTALLED, fetched.installedSourceId(), fetched.source().url(),
            fetched.sha256(), fetched.bytes(), extracted, patched.size(), verification.verified(),
            verification.deleted().size(), packBytes,
            "Installed " + verification.verified() + " files from " + fetched.source().description(), this.attempts);
    }

    /**
     * Walks the source chain until one entry yields a jar whose whole-jar hash is acceptable.
     *
     * @param manifest the install manifest, for the set of known jar hashes.
     * @return the accepted jar, or null when every source failed.
     * @throws AssetInstallException if the run is cancelled.
     */
    private Fetched fetch(final AssetManifest manifest) throws AssetInstallException
    {
        final JarDownloader downloader = new JarDownloader();

        for (final AssetSource source : this.config.sources())
        {
            if (this.cancelled.isCancelled())
            {
                throw new InstallCancelledException();
            }

            this.listener.onSourceStarted(source.id(), source.url(), source.description());
            Log.getLogger().info("Trying MineColonies asset source {} at {}", source.id(), source.url());

            try
            {
                final Fetched fetched = attempt(source, manifest, downloader);
                this.attempts.add(new SourceAttempt(source.id(), source.url(), true, fetched.httpStatus(),
                    fetched.bytes(), fetched.sha256(), null));
                return fetched;
            }
            catch (final InstallCancelledException e)
            {
                throw e;
            }
            catch (final JarDownloader.SourceFailure e)
            {
                recordFailure(source, e.status(), e.bytes(), e.getMessage());
            }
            catch (final AssetInstallException e)
            {
                recordFailure(source, -1, 0L, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Obtains and hash-checks one source's jar.
     *
     * @param source     the chain entry.
     * @param manifest   the install manifest.
     * @param downloader the shared downloader.
     * @return the accepted jar.
     * @throws AssetInstallException if the transfer or the hash check fails.
     */
    private Fetched attempt(final AssetSource source, final AssetManifest manifest, final JarDownloader downloader) throws AssetInstallException
    {
        final Path jar;
        final long bytes;
        final String sha256;
        final int status;

        if (source.kind() == AssetSource.Kind.LOCAL_FILE)
        {
            jar = Path.of(source.url());
            if (!Files.isRegularFile(jar))
            {
                throw new AssetInstallException("There is no file at " + source.url());
            }
            this.listener.onPhase(InstallPhase.CHECKING_JAR);
            try
            {
                bytes = Files.size(jar);
                sha256 = Hashes.sha256(jar);
            }
            catch (final IOException e)
            {
                throw new AssetInstallException("Could not read " + source.url() + ": " + e.getMessage(), e);
            }
            status = -1;
        }
        else
        {
            this.listener.onPhase(InstallPhase.DOWNLOADING);
            jar = this.config.tempDir().resolve(DOWNLOAD_NAME);
            final JarDownloader.Result result = downloader.download(source.url(), jar, this.listener::onBytes, this.cancelled);
            bytes = result.bytes();
            sha256 = result.sha256();
            status = result.httpStatus();
            this.listener.onPhase(InstallPhase.CHECKING_JAR);
        }

        Log.getLogger().info("Source {} produced {} bytes, sha256 {}", source.id(), bytes, sha256);

        if (source.expectedSize() >= 0 && bytes != source.expectedSize())
        {
            throw new JarDownloader.SourceFailure(status, bytes,
                "expected " + source.expectedSize() + " bytes from " + source.id() + " but got " + bytes, null);
        }

        if (source.expectedSha256() != null)
        {
            if (!source.expectedSha256().equals(sha256))
            {
                throw new JarDownloader.SourceFailure(status, bytes, "the jar from " + source.id() + " has SHA-256 " + sha256
                    + ", not the expected " + source.expectedSha256(), null);
            }
            return new Fetched(source, jar, bytes, sha256, status, source.id(), source.id(), false);
        }

        // Sources 3 and 4 pin no hash of their own: the jar has to be one this build already knows, and the
        // check happens here, before a single entry is unpacked.
        final String identified = SourceChain.identify(sha256, manifest);
        if (identified != null)
        {
            return new Fetched(source, jar, bytes, sha256, status, source.id(), identified, false);
        }

        if (source.kind() == AssetSource.Kind.LOCAL_FILE)
        {
            // An unknown hash from a player-supplied jar is not fatal yet: another distributor may serve a
            // byte-identical build under a different name. It is accepted only if every file verifies.
            final String primary = manifest.primarySource();
            if (primary == null)
            {
                throw new AssetInstallException("That jar is not a supported MineColonies build (SHA-256 " + sha256
                    + "). Supported: " + SourceChain.supportedVersions());
            }
            Log.getLogger().warn("Local jar {} has unknown SHA-256 {}; accepting it only if it verifies against the manifest",
                source.url(), sha256);
            return new Fetched(source, jar, bytes, sha256, status, source.id(), primary, true);
        }

        throw new JarDownloader.SourceFailure(status, bytes, "the jar from " + source.id() + " has SHA-256 " + sha256
            + ", which is not a known MineColonies build. Supported: " + SourceChain.supportedVersions(), null);
    }

    /**
     * Swaps the staged pack into place, parking any existing pack until the swap has succeeded.
     *
     * @param stage the verified staging tree.
     * @throws AssetInstallException if the swap fails.
     */
    private void promote(final Path stage) throws AssetInstallException
    {
        final Path pack = this.config.packDir();
        final Path parked = this.config.tempDir().resolve(PREVIOUS_DIR);

        try
        {
            if (Files.exists(pack))
            {
                Files.move(pack, parked, StandardCopyOption.REPLACE_EXISTING);
            }
            try
            {
                Files.move(stage, pack);
            }
            catch (final IOException e)
            {
                // Put the old pack back rather than leave the player with nothing.
                if (Files.exists(parked) && !Files.exists(pack))
                {
                    Files.move(parked, pack);
                }
                throw e;
            }
        }
        catch (final IOException e)
        {
            throw new AssetInstallException("Could not move the finished pack into place: " + e.getMessage(), e);
        }
    }

    /**
     * Notes a failed source, logging the four facts the escalation rule asks for.
     *
     * @param source  the chain entry.
     * @param status  the HTTP status, or -1.
     * @param bytes   bytes received before the failure.
     * @param message the error text.
     */
    private void recordFailure(final AssetSource source, final int status, final long bytes, final String message)
    {
        final SourceAttempt attempt = new SourceAttempt(source.id(), source.url(), false, status, bytes, null, message);
        this.attempts.add(attempt);
        Log.getLogger().warn("MineColonies asset source failed -- {}", attempt.describe());
        this.listener.onSourceFailed(attempt);
    }

    /**
     * A one-line summary of every attempt, for the failure message.
     *
     * @return the summary.
     */
    private String describeAttempts()
    {
        final StringBuilder out = new StringBuilder();
        for (final SourceAttempt attempt : this.attempts)
        {
            out.append(out.isEmpty() ? "" : "; ").append(attempt.describe());
        }
        return out.toString();
    }

    /**
     * Tells the caller that {@code state.json} changed, so cached readings of it can be dropped.
     */
    private void stateChanged()
    {
        if (this.config.onStateChanged() != null)
        {
            this.config.onStateChanged().run();
        }
    }

    /**
     * Builds a terminal report for a run that produced no install.
     *
     * @param outcome  how it ended.
     * @param sourceId the source involved, or null.
     * @param reason   the player-showable reason.
     * @return the report.
     */
    private InstallReport report(final InstallReport.Outcome outcome, final String sourceId, final String reason)
    {
        return new InstallReport(outcome, sourceId, null, null, 0L, 0, 0, 0, 0, 0L,
            reason == null ? "" : reason, this.attempts);
    }

    /**
     * A jar that arrived and passed its whole-jar hash check.
     *
     * @param source            the chain entry it came from.
     * @param jar               where it is on disk.
     * @param bytes             its size.
     * @param sha256            its whole-jar hash.
     * @param httpStatus        the HTTP status, or -1.
     * @param installedSourceId what to record in {@code state.json} — the chain entry's own id.
     * @param manifestSourceId  which manifest variant to verify against — the id of the upstream build the
     *                          hash identifies, which for source 3 or 4 is not the chain entry's id.
     * @param unknownJar        whether the hash matched nothing known, so verification is the only check.
     */
    private record Fetched(AssetSource source, Path jar, long bytes, String sha256, int httpStatus,
        String installedSourceId, String manifestSourceId, boolean unknownJar)
    {
    }
}
