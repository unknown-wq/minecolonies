package com.minecolonies.core.client.assetfetch;

import com.minecolonies.api.util.Log;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The install itself: download, extract, patch, assemble, promote (tasks C2, C3, C8, C9).
 *
 * <p>Pure Java over {@link Path} arguments — no Minecraft or Fabric types anywhere in it — so the whole thing
 * can be run headlessly against a scratch directory, which is how it was tested. {@link AssetInstaller} is
 * the thin layer that fills in the game's own paths and pack format and runs this off the client thread.</p>
 *
 * <p><b>An install is a merge, not a replacement.</b> The manifest lists the paths the pack is made of; the
 * archive supplies what it supplies; anything listed that the archive did not carry is taken out of the pack
 * the player already has. {@link PackAssembler} is where that happens, and it happens in the staging
 * directory, by copying, so the installed pack is only ever read from until the swap. A player who updates
 * the mod therefore cannot end up with fewer files than they started with, whatever source answered.</p>
 *
 * <p><b>What is and is not checked.</b> A source with a pinned whole-archive hash is checked against it,
 * hash and size, before a single entry is unpacked — see {@link #attempt} — and that pin is the only
 * integrity guarantee this feature makes. Nothing after the unpack looks at any file's contents: whatever
 * the archive carries at a listed path is what gets installed. A source that pins nothing is therefore
 * trusted for its bytes and vouched for by nobody; what it is still held to is shape — the archive must
 * unpack without escaping the staging tree ({@link JarAssetExtractor}), and it must carry the files the
 * patch bundle has to edit ({@link PatchBundle}), or the source fails and the chain moves on.</p>
 *
 * <p><b>An interrupted swap is undone, not left.</b> Putting the finished pack in place is two renames with
 * a window between them, and a JVM that dies in that window leaves no pack at all and the old one parked in
 * {@code tmp/}. {@link #recoverInterruptedSwap} is what the next launch calls to put it back; see there for
 * what it will and will not pick up.</p>
 *
 * <p><b>Nothing outside the temporary directory is written until the very end.</b> The jar is downloaded to
 * {@code tmp/}, unpacked into {@code tmp/stage/}, patched there, given its {@code pack.mcmeta} there and
 * assembled there. Only then is the staged tree swapped into place and {@code state.json} rewritten. A
 * failure at any earlier point deletes {@code tmp/} and returns, leaving an existing install — or the clean
 * pre-consent state — exactly as it was. That is the escalation rule applied to shipped code: a failed
 * download must never leave a half-installed game.</p>
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
     * How many paths to name when a message or a log line lists some.
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
                if (fetched.source().unpinned())
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
                    // A player-supplied jar with an unknown hash gets exactly one chance: it has to survive
                    // the unpack and the patch bundle. Whatever it failed on, what the player needs to be
                    // told is which builds this version is actually known to work with.
                    throw new AssetInstallException("That jar is not a supported MineColonies build. Supported: "
                        + SourceChain.supportedVersions() + ". (" + e.getMessage() + ")", e);
                }
                throw e;
            }
        }
    }

    /**
     * Everything after a jar has been accepted: unpack it, patch it, assemble the pack, put it in place.
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

        this.listener.onPhase(InstallPhase.ASSEMBLING);
        final Set<String> expected = manifest.files();
        if (expected.isEmpty())
        {
            throw new AssetInstallException("The install manifest lists no files at all");
        }
        final PackAssembler.Result assembled = PackAssembler.assemble(stage, expected, this.config.packDir(),
            done -> this.listener.onFiles(done, expected.size()), this.cancelled);

        final int carried = assembled.carried().size();
        final int absent = assembled.absent().size();
        if (carried > 0)
        {
            Log.getLogger().info("{} of the {} pack files are not in the archive from {} and have been kept from the"
                + " pack already installed -- {}", carried, expected.size(), fetched.source().id(), namePaths(assembled.carried()));
        }
        if (absent > 0)
        {
            Log.getLogger().warn("{} of the {} pack files are in neither the archive from {} nor the pack already"
                + " installed, and are not in the finished pack -- {}", absent, expected.size(), fetched.source().id(),
                namePaths(assembled.absent()));
        }

        // The prune's own walk already added this up, so the tree is not walked a second time for it.
        final long packBytes = assembled.bytes();

        this.listener.onPhase(InstallPhase.INSTALLING);
        promote(stage);

        try
        {
            InstallState.writeInstalled(this.config.stateFile(), fetched.installedSourceId(), fetched.source().url(),
                fetched.sha256(), manifest.sha256(), absent, carried);
        }
        catch (final AssetInstallException e)
        {
            throw demote(stage, e);
        }
        stateChanged();

        Log.getLogger().info("Installed {} MineColonies asset files ({} bytes) from {}; {} written from the archive,"
                + " {} kept from the previous install, {} absent, {} unwanted file(s) removed",
            assembled.present(), packBytes, fetched.installedSourceId(), assembled.written(), carried, absent,
            assembled.deleted().size());

        final String summary = "Installed " + assembled.present() + " files from " + fetched.source().description()
            + (carried == 0 ? "" : ", " + carried + " of which that source does not carry and which were kept from"
                + " the assets already installed")
            + (absent == 0 ? "" : ". That source does not carry " + absent + " of the pack's files and there was no"
                + " earlier install to take them from, so the pack is that much short. For a source release those are"
                + " upstream's translations, which it builds from data it does not publish, and some MineColonies text"
                + " will show as its raw key until the assets are installed from a full build");

        return new InstallReport(InstallReport.Outcome.INSTALLED, fetched.installedSourceId(), fetched.source().url(),
            fetched.sha256(), fetched.bytes(), extracted, patched.size(), assembled.written(), carried,
            assembled.deleted().size(), absent, packBytes, summary, this.attempts);
    }

    /**
     * Walks the source chain, from the given entry onwards, until one of them yields an archive worth
     * unpacking.
     *
     * <p>Every entry is tried at most once, in order, and the walk stops at the first archive that is
     * acceptable at this stage — which for a pinned source means its hash and size matched, and for an
     * unpinned one means only that it arrived. Whether it can actually be unpacked and patched is settled
     * downstream; if it cannot, the caller comes back here with a later starting point.</p>
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
            return new Fetched(source, jar, bytes, sha256, status, source.id(), false, index);
        }

        if (source.unpinned())
        {
            // Nothing about this archive is pinned and nothing pretends to be: GitHub builds its source
            // archives per request, so there is no stable hash to pin. It is taken for what it carries, and
            // the only thing that can still turn it down is failing to unpack or failing to be patchable.
            Log.getLogger().info("Source {} pins no archive hash; what it carries is installed as it comes", source.id());
            return new Fetched(source, jar, bytes, sha256, status, source.id(), false, index);
        }

        // The owner's own slot and the player's own jar pin no hash of their own: the jar should be one this
        // build already knows, and that is settled here, before a single entry is unpacked.
        final String identified = SourceChain.identify(sha256, manifest);
        if (identified != null)
        {
            return new Fetched(source, jar, bytes, sha256, status, source.id(), false, index);
        }

        if (source.kind() == AssetSource.Kind.LOCAL_FILE)
        {
            // An unknown hash from a player-supplied jar is not fatal: another distributor may serve a
            // byte-identical build under a different name, and a build newer than any this manifest lists is
            // exactly the jar a player would go and fetch by hand. Nothing vouches for such a file -- it is
            // accepted if it unpacks and the patch bundle applies to it, and that is the whole of it -- so
            // the player is told as much rather than being left to think it was checked.
            Log.getLogger().warn("Local jar {} has unknown SHA-256 {}; installing what it carries unchecked",
                source.url(), sha256);
            return new Fetched(source, jar, bytes, sha256, status, source.id(), true, index);
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
     * Turns the recorded success of a source that later failed into the failure it turned out to be, so the
     * report says what actually happened.
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
     * @param stage the finished staging tree.
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
     * <p>If the reversal itself fails the disk is left as it is — the new pack in place, the record
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
     * A failure of putting the finished pack in place or of recording it — the two steps after an archive
     * has got through the pipeline.
     *
     * <p>Kept apart from every other failure because the chain must not treat it as the archive's: an
     * unpinned source is otherwise allowed to fail and hand over to the next one, and a swap that could not
     * be made would then be retried at the cost of another whole download, to fail the same way.</p>
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
     * <p>Deliberately shallow, and there is nothing deeper to be had: the installer holds no file-by-file
     * expectation of a pack any more. It is only enough to tell an installed pack from scrap, so that nothing
     * else that happens to be sitting in the scratch directory can be promoted into the pack's place.</p>
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
        return new InstallReport(outcome, sourceId, null, null, 0L, 0, 0, 0, 0, 0, 0, 0L,
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
     * @param unknownJar        whether the hash matched nothing this build knows, so nothing at all vouches
     *                          for the archive.
     * @param index             the entry's position in the chain, so a rejected archive can be resumed from.
     */
    private record Fetched(AssetSource source, Path jar, long bytes, String sha256, int httpStatus,
        String installedSourceId, boolean unknownJar, int index)
    {
    }
}
