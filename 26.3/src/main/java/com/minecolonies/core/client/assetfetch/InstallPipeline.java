package com.minecolonies.core.client.assetfetch;

import com.minecolonies.api.util.Log;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The install itself: download, extract, patch, verify, promote (tasks C2, C3, C8, C9).
 *
 * <p>Pure Java over {@link Path} arguments — no Minecraft or Fabric types anywhere in it — so the whole thing
 * can be run headlessly against a scratch directory, which is how it was tested. {@link AssetInstaller} is
 * the thin layer that fills in the game's own paths and pack format and runs this off the client thread.</p>
 *
 * <p>Two rules protect an install that already works, and both of them are about the same thing: a player
 * who updates the mod must never end up with less than they started with.</p>
 * <ul>
 *     <li><b>An interrupted swap is undone, not left.</b> Putting the finished pack in place is two renames
 *         with a window between them, and a JVM that dies in that window leaves no pack at all and the old
 *         one parked in {@code tmp/}. {@link #recoverInterruptedSwap} is what the next launch calls to put it
 *         back; see there for what it will and will not pick up.</li>
 *     <li><b>A replacement may not be worse than what it replaces.</b> A source that is allowed not to carry
 *         part of the file set is fine for a first install and fine for replacing a pack that does not have
 *         those files either, but it must not quietly take files off a player who has them. What is compared
 *         is the files themselves, not a flag: the swap is refused only when the pack on disk really does
 *         hold files this archive cannot supply.</li>
 * </ul>
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
     * Held by a run for its whole length, and by {@link #recoverInterruptedSwap} while it looks.
     *
     * <p>Two runs in one JVM would share the scratch directory: each wipes it on the way in and on the way
     * out, so the second could delete the first's staged tree — or the pack the first had just parked —
     * from under it. Nothing above this class prevents that: a cancelled install stops at its next
     * checkpoint, not at once, and the screens let a player start another while it is still stopping. So a
     * run that finds the lock taken does not start at all, and says so.</p>
     *
     * <p>Recovery takes the same lock for the same reason it used to check a flag: the window it repairs
     * belongs to a run of the game that has ended, and a run still going owns the pack directory itself. A
     * lock rather than a flag, because a flag could be read as clear a moment before the swap set it.</p>
     */
    private static final ReentrantLock INSTALL_LOCK = new ReentrantLock();

    /**
     * What to tell a player who starts an install while another one is still running.
     */
    private static final String ALREADY_RUNNING =
        "Another MineColonies asset install is still running in this game. Let it finish -- or, if it is stuck on a"
            + " download that has stopped answering, restart the game -- and try again.";

    /**
     * How many failing paths to name in a player-facing verification error.
     */
    private static final int NAMED_FAILURES = 5;

    /**
     * The most any single source is allowed to send.
     *
     * <p>The two pinned Maven artifacts state their own exact size and are held to it. A source that pins no
     * size — the owner's slot, a source release archive — still gets a ceiling, because a download with no
     * upper bound on somebody else's server is a way to fill a player's disk with somebody else's mistake.
     * Generous enough that no plausible upstream archive comes near it.
     */
    private static final long MAX_UNPINNED_BYTES = 512L * 1024L * 1024L;

    /**
     * What to tell a player when every source in the chain has been tried and none of them worked.
     */
    private static final String NO_SOURCE_ADVICE =
        "You can still install the assets by hand: download an official MineColonies 1.21.1 jar yourself -- "
            + "from CurseForge, for instance -- and use \"Use a jar I already have\".";

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
     * The chain being walked. It starts as the shipped one and is appended to exactly once, when the shipped
     * entries have all failed and discovery gets its turn; indices into it therefore stay valid.
     */
    private final List<AssetSource> chain;

    /**
     * Whether that one expansion has already happened.
     */
    private boolean expanded;

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
        this.chain = new ArrayList<>(config.sources());
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
        if (!INSTALL_LOCK.tryLock())
        {
            Log.getLogger().warn("A MineColonies asset install was requested while another one is still running; not starting it");
            this.listener.onPhase(InstallPhase.DONE);
            return report(InstallReport.Outcome.FAILED, null, ALREADY_RUNNING);
        }
        try
        {
            return runLocked();
        }
        finally
        {
            INSTALL_LOCK.unlock();
        }
    }

    /**
     * {@link #run()} once the lock is held.
     *
     * @return what happened.
     */
    private InstallReport runLocked()
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
            releaseScratch();
            this.listener.onPhase(InstallPhase.DONE);
        }
    }

    /**
     * Deletes the scratch directory at the end of a run — unless it still holds the only copy of the
     * player's pack.
     *
     * <p>{@link #promote} parks the installed pack in {@code tmp/} while the staged one is moved into its
     * place, and puts it back itself if that second move fails. If putting it back fails too, the parked pack
     * is all the player has left, and wiping the scratch directory here would destroy it in the very run
     * that promised to leave an install alone. So the swap is undone once more from here, and if that still
     * does not work the scratch directory is left exactly as it is: {@link #recoverInterruptedSwap} on the
     * next launch, or the start of the next run, is built to pick a parked pack up from there.</p>
     */
    private void releaseScratch()
    {
        final Path pack = this.config.packDir();
        final Path parked = this.config.tempDir().resolve(PREVIOUS_DIR);
        try
        {
            if (!Files.exists(pack) && looksLikeAPack(parked))
            {
                Files.move(parked, pack);
                Log.getLogger().warn("The MineColonies asset pack that was installed before this run has been put back at {}", pack);
            }
        }
        catch (final IOException | RuntimeException e)
        {
            Log.getLogger().error("The previously installed MineColonies asset pack is parked at {} and could not be put"
                + " back; the scratch directory is left alone so the next launch can put it back", parked, e);
            return;
        }
        FileTrees.deleteQuietly(this.config.tempDir());
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

        // Before the scratch directory is wiped: it may still hold the pack an interrupted run parked, and
        // wiping that without putting it back would throw away the only copy the player has.
        recoverInterruptedSwap(this.config.packDir(), this.config.tempDir(), this.config.stateFile());

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

        int from = 0;
        while (true)
        {
            final Fetched fetched = fetch(manifest, from);
            if (fetched == null)
            {
                return report(InstallReport.Outcome.NO_SOURCE, null,
                    "None of the download sources worked. " + describeAttempts() + " " + NO_SOURCE_ADVICE);
            }

            try
            {
                return unpack(fetched, manifest, bundle);
            }
            catch (final InstallCancelledException | SwapFailure e)
            {
                // Neither is the archive's fault: a cancellation is the player's, and a swap that could not
                // be made or recorded would fail the same way for the next archive too.
                throw e;
            }
            catch (final AssetInstallException e)
            {
                if (fetched.source().verifiedByContents())
                {
                    // A source with nothing pinned is only ever as good as what came out of it, so failing
                    // here is that source failing — not the install failing. Rewrite its attempt as the
                    // failure it turned out to be and carry on down the chain.
                    replaceLastAttemptWithFailure(fetched, e.getMessage());
                    from = fetched.index() + 1;
                    continue;
                }
                if (fetched.unknownJar())
                {
                    // A player-supplied jar with an unknown hash gets exactly one chance: it is accepted only
                    // if it survives the whole pipeline. Whatever it failed on, what the player needs to be
                    // told is which builds this version can actually use.
                    throw new AssetInstallException("That jar is not a supported MineColonies build. Supported: "
                        + SourceChain.supportedVersions() + ". (" + e.getMessage() + ")", e);
                }
                throw e;
            }
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

        // A previous source may have got this far and been rejected; its files must not be counted as this
        // one's.
        FileTrees.deleteQuietly(stage);

        this.listener.onPhase(InstallPhase.EXTRACTING);
        final int extracted = JarAssetExtractor.extract(fetched.jar(), assetsRoot, fetched.source().layout(),
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

        final List<String> mayBeAbsent = manifest.mayBeAbsentFor(fetched.source().absencePolicySourceId());
        final List<String> absent = new ArrayList<>();
        final List<String> unexplained = new ArrayList<>();
        for (final String path : verification.missing())
        {
            (covered(path, mayBeAbsent) ? absent : unexplained).add(path);
        }

        if (!unexplained.isEmpty() || !verification.mismatched().isEmpty())
        {
            final String detail = describeVerification(unexplained, verification.mismatched());
            Log.getLogger().error("Downloaded MineColonies assets did not verify: {}", detail);
            throw new AssetInstallException("The downloaded assets did not match the expected contents: " + detail);
        }

        if (!absent.isEmpty())
        {
            Log.getLogger().warn("{} supplies {} of the {} pack files; {} declared file(s) it cannot supply are absent",
                fetched.source().id(), verification.verified(), expected.size(), absent.size());
        }

        final List<String> lost = installedButUnsupplied(absent);
        if (!lost.isEmpty())
        {
            // Everything about this archive is honest -- it verified, and it declared in advance what it
            // cannot carry -- and it is still the wrong thing to install here, because the player already has
            // those files and this would take them away. Refused as a failure of this source, so the chain
            // carries on to one that can supply them.
            Log.getLogger().warn("Not replacing the installed MineColonies assets from {}: {} installed file(s) are"
                + " ones that source cannot supply", fetched.source().id(), lost.size());
            throw new AssetInstallException("The assets already installed carry " + lost.size() + " file(s) that "
                + fetched.source().description() + " cannot supply, so installing from there would take them away: "
                + namePaths(lost) + ". They have been left exactly as they were.");
        }

        // The prune's own walk already added this up, so the tree is not walked a second time for it.
        final long packBytes = verification.bytes();

        this.listener.onPhase(InstallPhase.INSTALLING);
        promote(stage);

        try
        {
            InstallState.writeInstalled(this.config.stateFile(), fetched.installedSourceId(), fetched.source().url(),
                fetched.sha256(), manifest.sha256(), absent.size());
        }
        catch (final AssetInstallException e)
        {
            throw demote(stage, e);
        }
        stateChanged();

        Log.getLogger().info("Installed {} verified MineColonies asset files ({} bytes) from {}",
            verification.verified(), packBytes, fetched.installedSourceId());

        final String summary = "Installed " + verification.verified() + " files from " + fetched.source().description()
            + (absent.isEmpty() ? "" : ". That source does not carry " + absent.size()
                + " of the pack's files -- upstream builds them from data it does not publish -- so some"
                + " MineColonies text will show as its raw key until the assets are installed from a full"
                + " build");

        return new InstallReport(InstallReport.Outcome.INSTALLED, fetched.installedSourceId(), fetched.source().url(),
            fetched.sha256(), fetched.bytes(), extracted, patched.size(), verification.verified(),
            verification.deleted().size(), packBytes, summary, this.attempts);
    }

    /**
     * Walks the source chain, from the given entry onwards, until one of them yields an archive worth
     * unpacking.
     *
     * <p>Every entry is tried at most once, in order, and the walk stops at the first archive that is
     * acceptable at this stage — which for a pinned source means its hash matched, and for one verified by
     * its contents means only that it arrived. Whether it really holds what it must is settled by the
     * verifier; if it does not, the caller comes back here with a later starting point.</p>
     *
     * <p>When the shipped entries run out, the chain is grown once — discovery, then whatever comes after it
     * — and the walk continues into the new entries. Once, and only here: a player whose install worked from
     * the first source never reaches this, and so never causes a request to anybody's API.</p>
     *
     * @param manifest the install manifest, for the set of known jar hashes.
     * @param start    the index in the chain to start at.
     * @return the accepted archive, or null when every remaining source failed.
     * @throws AssetInstallException if the run is cancelled.
     */
    private Fetched fetch(final AssetManifest manifest, final int start) throws AssetInstallException
    {
        final JarDownloader downloader = new JarDownloader();
        int from = start;

        while (true)
        {
            final Fetched fetched = walk(manifest, downloader, from);
            if (fetched != null)
            {
                return fetched;
            }
            if (this.expanded)
            {
                return null;
            }
            this.expanded = true;
            from = this.chain.size();
            expand();
            if (from == this.chain.size())
            {
                return null;
            }
        }
    }

    /**
     * Tries each entry of the chain from the given index, stopping at the first that yields an archive.
     *
     * @param manifest   the install manifest, for the set of known jar hashes.
     * @param downloader the shared downloader.
     * @param from       the index to start at.
     * @return the accepted archive, or null when every entry from there on failed.
     * @throws AssetInstallException if the run is cancelled.
     */
    private Fetched walk(final AssetManifest manifest, final JarDownloader downloader, final int from) throws AssetInstallException
    {
        for (int index = from; index < this.chain.size(); index++)
        {
            final AssetSource source = this.chain.get(index);
            if (this.cancelled.isCancelled())
            {
                throw new InstallCancelledException();
            }

            this.listener.onSourceStarted(source.id(), source.url(), source.description());
            Log.getLogger().info("Trying MineColonies asset source {} at {}", source.id(), source.url());

            try
            {
                final Fetched fetched = attempt(source, manifest, downloader, index);
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
     * @param index      the entry's position in the chain.
     * @return the accepted jar.
     * @throws AssetInstallException if the transfer or the hash check fails.
     */
    private Fetched attempt(final AssetSource source, final AssetManifest manifest, final JarDownloader downloader,
        final int index) throws AssetInstallException
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
            final long cap = source.expectedSize() >= 0 ? source.expectedSize() : MAX_UNPINNED_BYTES;
            final JarDownloader.Result result = downloader.download(source.url(), jar, cap, this.listener::onBytes, this.cancelled);
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
            return new Fetched(source, jar, bytes, sha256, status, source.id(), source.id(), false, index);
        }

        if (source.verifiedByContents())
        {
            // Nothing about this archive is pinned and nothing pretends to be: GitHub builds its source
            // archives per request. What is pinned is the file set it has to produce, and that is checked
            // file by file after unpacking, against the same manifest every other source is held to.
            Log.getLogger().info("Source {} pins no archive hash; it is accepted only if its files verify against the {} manifest",
                source.id(), source.filesOfSourceId());
            return new Fetched(source, jar, bytes, sha256, status, source.id(), source.filesOfSourceId(), false, index);
        }

        // The owner's own slot and the player's own jar pin no hash of their own: the jar has to be one this
        // build already knows, and the check happens here, before a single entry is unpacked.
        final String identified = SourceChain.identify(sha256, manifest);
        if (identified != null)
        {
            return new Fetched(source, jar, bytes, sha256, status, source.id(), identified, false, index);
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
            return new Fetched(source, jar, bytes, sha256, status, source.id(), primary, true, index);
        }

        throw new JarDownloader.SourceFailure(status, bytes, "the jar from " + source.id() + " has SHA-256 " + sha256
            + ", which is not a known MineColonies build. Supported: " + SourceChain.supportedVersions(), null);
    }

    /**
     * Grows the chain by everything that is meant to be tried after the shipped entries.
     *
     * <p>Discovery first, then the entries that follow it, and the second part happens whether or not the
     * first found anything — the owner's own slot has nothing to do with what a listing answered. Discovery
     * is contracted not to throw, and is caught anyway: a fallback that took the install down with it would
     * be worse than no fallback.</p>
     */
    private void expand()
    {
        final SourceDiscovery discovery = this.config.discovery();
        if (discovery != null)
        {
            try
            {
                this.chain.addAll(discovery.discover());
            }
            catch (final RuntimeException e)
            {
                Log.getLogger().warn("Looking for further MineColonies asset sources failed: {}", e.toString());
            }
        }
        this.chain.addAll(this.config.sourcesAfterDiscovery());
    }

    /**
     * Whether a pack path sits under one of the prefixes a source is allowed not to supply.
     *
     * @param path     the pack-relative path.
     * @param prefixes the declared prefixes.
     * @return true if the file's absence is one this source declared in advance.
     */
    private static boolean covered(final String path, final List<String> prefixes)
    {
        for (final String prefix : prefixes)
        {
            if (path.startsWith(prefix))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * A short, player-showable description of what did not verify.
     *
     * @param missing    manifest entries with no file on disk that the source did not declare it would miss.
     * @param mismatched manifest entries whose file has the wrong contents.
     * @return the description.
     */
    private static String describeVerification(final List<String> missing, final List<String> mismatched)
    {
        final List<String> named = new ArrayList<>(missing);
        named.addAll(mismatched);
        return missing.size() + " file(s) missing and " + mismatched.size()
            + " file(s) with the wrong contents: " + namePaths(named);
    }

    /**
     * Names the first few of a list of pack paths and counts the rest.
     *
     * @param paths the paths, in the order they should be read.
     * @return the description.
     */
    private static String namePaths(final List<String> paths)
    {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < Math.min(NAMED_FAILURES, paths.size()); i++)
        {
            out.append(i > 0 ? ", " : "").append(paths.get(i));
        }
        if (paths.size() > NAMED_FAILURES)
        {
            out.append(", and ").append(paths.size() - NAMED_FAILURES).append(" more");
        }
        return out.toString();
    }

    /**
     * Which of the files this archive cannot supply the player already has.
     *
     * <p>This is the whole of the "a replacement may not be worse" rule, and it deliberately asks the pack on
     * disk rather than the {@code complete} flag in {@code state.json}. The flag records what a source could
     * supply against the manifest of the build that installed it, which is not the question here; the
     * question is what this particular swap would remove from this particular player. Asking the files
     * themselves also keeps the two cases that must stay allowed allowed: a first install, where there is no
     * pack and nothing can be lost, and the replacement of an incomplete pack by another one missing the same
     * files, which is how a player on a fallback source gets updated at all.</p>
     *
     * @param paths the pack-relative paths this source declared it would not supply and did not.
     * @return those of them that are installed right now, in manifest order.
     */
    private List<String> installedButUnsupplied(final List<String> paths)
    {
        final Path pack = this.config.packDir();
        final List<String> installed = new ArrayList<>();
        for (final String path : paths)
        {
            if (Files.isRegularFile(pack.resolve(path)))
            {
                installed.add(path);
            }
        }
        return installed;
    }

    /**
     * Turns the recorded success of a source that later failed to verify into the failure it turned out to
     * be, so the report says what actually happened.
     *
     * @param fetched the archive that did not hold up.
     * @param message why it did not.
     */
    private void replaceLastAttemptWithFailure(final Fetched fetched, final String message)
    {
        if (!this.attempts.isEmpty() && this.attempts.get(this.attempts.size() - 1).succeeded())
        {
            this.attempts.remove(this.attempts.size() - 1);
        }
        final SourceAttempt attempt = new SourceAttempt(fetched.source().id(), fetched.source().url(), false,
            fetched.httpStatus(), fetched.bytes(), fetched.sha256(), message);
        this.attempts.add(attempt);
        Log.getLogger().warn("MineColonies asset source failed -- {}", attempt.describe());
        this.listener.onSourceFailed(attempt);
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
            throw new SwapFailure("Could not move the finished pack into place: " + e.getMessage(), e);
        }
    }

    /**
     * Undoes {@link #promote} when the install record could not be written afterwards.
     *
     * <p>A pack without its record is not an install: nothing offers it to the game, the next launch asks
     * for the download again, and — when this was a replacement — the pack that was there before would
     * have gone out with the scratch directory. So the swap is reversed before the failure is reported: the
     * pack just put in place goes back to the staging path and the parked one, if there was one, returns.
     * Then the report's "nothing was changed" is the truth.</p>
     *
     * <p>If the reversal itself fails the disk is left as it is — the verified pack in place, the record
     * missing or describing its predecessor — and the caches are dropped so nothing goes on answering from
     * before the swap. The cleanup at the end of the run keeps a parked pack rather than deleting it, and
     * the next launch settles what is installed from what it finds.</p>
     *
     * @param stage the path the staged tree was promoted from, free again since the swap.
     * @param cause why the record could not be written.
     * @return the exception to report.
     */
    private SwapFailure demote(final Path stage, final AssetInstallException cause)
    {
        final Path pack = this.config.packDir();
        final Path parked = this.config.tempDir().resolve(PREVIOUS_DIR);
        try
        {
            Files.move(pack, stage);
            if (Files.isDirectory(parked))
            {
                Files.move(parked, pack);
            }
        }
        catch (final IOException e)
        {
            stateChanged();
            Log.getLogger().error("The MineColonies asset pack was put in place but its install record could not be"
                + " written, and undoing the swap failed as well", e);
            return new SwapFailure("The assets were put in place but the install record could not be written ("
                + cause.getMessage() + "), and undoing that failed too: " + e.getMessage()
                + ". What is installed will be looked at again on the next launch.", cause);
        }
        Log.getLogger().warn("The install record could not be written; the swap has been undone and the assets are as they were");
        return new SwapFailure(cause.getMessage() + " The assets that were installed before have been put back.", cause);
    }

    /**
     * A failure of putting the verified pack in place or of recording it — the two steps after an archive
     * has proved itself.
     *
     * <p>Kept apart from every other failure because the chain must not treat it as the archive's: a source
     * verified by its contents is otherwise allowed to fail and hand over to the next one, and a swap that
     * could not be made would then be retried at the cost of another whole download, to fail the same way.</p>
     */
    private static final class SwapFailure extends AssetInstallException
    {
        /**
         * Version id of this class for serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * Creates the failure.
         *
         * @param message what went wrong, for the player.
         * @param cause   the underlying failure.
         */
        private SwapFailure(final String message, final Throwable cause)
        {
            super(message, cause);
        }
    }

    /**
     * Puts back a pack that an interrupted swap left parked (the counterpart of {@link #promote}).
     *
     * <p>{@link #promote} is two renames: the installed pack out of the way, then the staged one into its
     * place. Between them there is a moment with no pack directory at all, and a JVM that dies there — a
     * crash, a killed process, the machine losing power — leaves the player with no assets and the previous,
     * perfectly good pack sitting in the scratch directory. Left alone that is exactly the failure this whole
     * feature exists to avoid: the assets vanish and the player is told to download 78 MB again. So the next
     * run puts it back, before anything asks whether the assets are installed and before the scratch
     * directory is wiped.</p>
     *
     * <p>Five things all have to hold, so that this restores an install and never adopts scrap:</p>
     * <ul>
     *     <li>no swap is going on in this JVM right now — the window this repairs belongs to a run of the
     *         game that has ended, and a swap still in flight owns the pack directory itself;</li>
     *     <li>there is no pack directory — with one there, nothing was interrupted and nothing is touched;</li>
     *     <li>the parked directory exists and carries a {@code pack.mcmeta};</li>
     *     <li>it carries an asset root with something in it, so it is a pack tree rather than some leftover
     *         or an empty shell that would load as a pack and supply nothing;</li>
     *     <li>{@code state.json} still records an install — the record was written before this pack was
     *         parked and is not rewritten until the swap has finished, so it is precisely the description of
     *         the pack being put back.</li>
     * </ul>
     *
     * <p>Whether the restored metadata is one the game can use is not decided here: that judgement already
     * exists, on the read side, and a pack it refuses is reported as "not installed" exactly as before.</p>
     *
     * <p>Never throws. It runs on the way to the title screen and on a resource reload, where an exception
     * would be far worse than a pack that is not restored.</p>
     *
     * @param packDir   where the installed pack belongs.
     * @param tempDir   the scratch directory the interrupted run used.
     * @param stateFile the install record.
     * @return true if a parked pack was put back.
     */
    public static boolean recoverInterruptedSwap(final Path packDir, final Path tempDir, final Path stateFile)
    {
        if (!INSTALL_LOCK.tryLock())
        {
            // A run is going on in this JVM and owns the pack directory; whatever is parked is its business.
            return false;
        }
        try
        {
            if (Files.exists(packDir))
            {
                return false;
            }

            final Path parked = tempDir.resolve(PREVIOUS_DIR);
            if (!looksLikeAPack(parked) || !InstallState.read(stateFile).isInstalled())
            {
                return false;
            }

            Files.move(parked, packDir);
            Log.getLogger().warn("A MineColonies asset install was interrupted while putting the pack in place;"
                + " the pack that was installed before it has been put back at {}", packDir);

            // What is left of that run is scrap: a part-moved staging tree and the archive it came from.
            FileTrees.deleteQuietly(tempDir);
            return true;
        }
        catch (final IOException | RuntimeException e)
        {
            Log.getLogger().error("Could not put back the MineColonies asset pack that an interrupted install parked", e);
            return false;
        }
        finally
        {
            INSTALL_LOCK.unlock();
        }
    }

    /**
     * Whether a directory is shaped like an installed pack: the metadata the game reads, and an asset root
     * with at least something in it.
     *
     * <p>Deliberately shallow. It is not a verification — the pack this asks about was verified file by file
     * before it was installed, and re-verifying 78 MB to recover from a crash would take longer than the
     * install did. It is only enough to tell an installed pack from scrap, so that nothing else that happens
     * to be sitting in the scratch directory can be promoted into the pack's place.</p>
     *
     * @param directory the candidate.
     * @return true if it may be put back as the installed pack.
     * @throws IOException if the directory cannot be read.
     */
    private static boolean looksLikeAPack(final Path directory) throws IOException
    {
        final Path assets = directory.resolve(AssetManifest.ASSET_PREFIX);
        if (!Files.isDirectory(directory)
            || !Files.isRegularFile(directory.resolve(PackMetaWriter.FILE_NAME))
            || !Files.isDirectory(assets))
        {
            return false;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(assets))
        {
            return entries.iterator().hasNext();
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
     * An archive that arrived and got past whatever check applies before unpacking.
     *
     * @param source            the chain entry it came from.
     * @param jar               where it is on disk.
     * @param bytes             its size.
     * @param sha256            its whole-jar hash.
     * @param httpStatus        the HTTP status, or -1.
     * @param installedSourceId what to record in {@code state.json} — the chain entry's own id.
     * @param manifestSourceId  which manifest variant to verify against — the id of the upstream build the
     *                          hash identifies, which for the owner's slot, the player's jar or a source
     *                          release is not the chain entry's id.
     * @param unknownJar        whether the hash matched nothing known, so verification is the only check.
     * @param index             the entry's position in the chain, so a rejected archive can be resumed from.
     */
    private record Fetched(AssetSource source, Path jar, long bytes, String sha256, int httpStatus,
        String installedSourceId, String manifestSourceId, boolean unknownJar, int index)
    {
    }
}
