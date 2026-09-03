package com.minecolonies.core.client.assetfetch;

import com.minecolonies.api.util.Log;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The installer as the consent UI sees it: start it, watch it, cancel it, or note that the player said no.
 *
 * <p>This is the only class in the feature that touches Minecraft, and it touches exactly one thing —
 * the running game's client resource pack format, for {@link PackMetaWriter} — plus Fabric's game directory
 * by way of {@link AssetFetch}. Everything else lives in {@link InstallPipeline}, which is plain Java over
 * {@link Path} arguments and was tested headlessly.</p>
 *
 * <h2>Using it from a screen</h2>
 * <pre>
 * final AssetInstaller installer = AssetInstaller.forGame();
 * installer.start(new InstallListener()
 * {
 *     &#64;Override public void onPhase(InstallPhase phase)              { this.phase = phase; }
 *     &#64;Override public void onBytes(long transferred, long total)    { this.progress = ...; }
 *     &#64;Override public void onFiles(int done, int total)             { this.progress = ...; }
 *     &#64;Override public void onSourceFailed(SourceAttempt attempt)    { this.log.add(attempt.describe()); }
 *     &#64;Override public void onFinished(InstallReport report)         { this.report = report; }
 * });
 * </pre>
 *
 * <p><b>Every callback arrives on the installer's thread</b>, so a screen must hand values over to the client
 * thread itself — store them in volatile fields the render loop reads, or use
 * {@code Minecraft.getInstance().execute(...)}.</p>
 *
 * <p><b>The installer never reloads resources.</b> On a successful report the UI calls
 * {@code Minecraft.reloadResourcePacks()} on the client thread; doing that from a worker thread is not safe,
 * and the installer has no business deciding when the game should reload. By the time
 * {@link InstallListener#onFinished} fires, {@code state.json} is written and {@link AssetFetch#invalidate()}
 * has been called, so the pack repository will offer the new pack the moment it is asked again.</p>
 *
 * <p>One instance runs one install. Call {@link #start} once; call {@link #cancel} from any thread.</p>
 */
@Environment(EnvType.CLIENT)
public final class AssetInstaller
{
    /**
     * Name of the installer thread, so it is recognisable in a thread dump or a crash report.
     */
    private static final String THREAD_NAME = "MineColonies asset install";

    /**
     * Whether the player pressed "not now" since the game started. Deliberately not persisted — see
     * {@link #recordDeclined()}. Volatile because the screens that set it and the title-screen hook that reads
     * it are not guaranteed to be the same thread.
     */
    private static volatile boolean declinedThisSession = false;

    /**
     * What to install and where.
     */
    private final InstallConfig config;

    /**
     * Set by {@link #cancel()}, polled by every long step of the pipeline.
     */
    private final AtomicBoolean cancelled = new AtomicBoolean();

    /**
     * Guards against a second {@link #start}.
     */
    private final AtomicBoolean started = new AtomicBoolean();

    /**
     * Creates an installer over an explicit configuration. Useful for tests and for the local-jar path;
     * ordinary callers want {@link #forGame()}.
     *
     * @param config what to install and where.
     */
    public AssetInstaller(final InstallConfig config)
    {
        this.config = config;
    }

    /**
     * The installer the consent screen uses: the game's cache directory, the mod jar's bundle, the running
     * game's pack format, and the automatic source chain.
     *
     * @return a ready installer.
     */
    public static AssetInstaller forGame()
    {
        return new AssetInstaller(new InstallConfig(AssetFetch.baseDir(), BundleResources.ofModJar(), clientPackFormat(),
            SourceChain.automatic(), SourceChain.discovery(), SourceChain.afterDiscovery(customSourceUrl()),
            AssetFetch::invalidate));
    }

    /**
     * The installer for source 4, the manual escape hatch: a jar the player already has.
     *
     * <p>The file runs through the identical extract, patch and verify pipeline. A jar whose whole-jar hash is
     * one this build knows is treated as that source; a jar with an unknown hash is still attempted and is
     * accepted only if every single file verifies against the manifest, and otherwise refused with a message
     * naming the supported upstream versions.</p>
     *
     * <p>This is the last thing in the chain and the only part of it the installer does not reach on its own:
     * it is the player's own action, offered once everything automatic has been tried.</p>
     *
     * @param jar the file the player picked.
     * @return a ready installer.
     */
    public static AssetInstaller forLocalJar(final Path jar)
    {
        // The manual entry stands alone: the player named this file, so there is nothing to fall back to and
        // nothing to go looking for.
        return new AssetInstaller(configFor(List.of(SourceChain.localJar(jar))));
    }

    /**
     * Starts the install on its own thread.
     *
     * @param listener progress callbacks, called on the installer's thread; may be null.
     * @return a future completing with the terminal report. It never completes exceptionally: a failure is a
     *         report with a non-{@code INSTALLED} outcome and a player-showable {@link InstallReport#reason()}.
     */
    public CompletableFuture<InstallReport> start(final InstallListener listener)
    {
        if (!this.started.compareAndSet(false, true))
        {
            throw new IllegalStateException("This AssetInstaller has already been started");
        }

        final CompletableFuture<InstallReport> future = new CompletableFuture<>();
        final Thread thread = new Thread(() ->
        {
            final InstallReport report = new InstallPipeline(this.config, listener, this.cancelled::get).run();
            if (listener != null)
            {
                listener.onFinished(report);
            }
            future.complete(report);
        }, THREAD_NAME);
        thread.setDaemon(true);
        thread.start();
        return future;
    }

    /**
     * Asks the install to stop. Safe from any thread and safe to call more than once; the run stops at the
     * next checkpoint, deletes its scratch directory and changes nothing.
     */
    public void cancel()
    {
        this.cancelled.set(true);
    }

    /**
     * Records that the player said "not now" — <b>for this session only</b>.
     *
     * <p>It used to be written to {@code state.json}, which meant one "not now" silenced the prompt forever
     * and the only ways back were a command and the window-open gate. A player who declines while installing a
     * modpack, or who simply does not want to download 78 MB right then, is not saying "never ask again"; they
     * are saying "not now". So the answer lives in a field that dies with the JVM, and the next launch asks
     * again — until the assets are actually installed, which is the only state that stops the asking.</p>
     *
     * <p>Nothing is downloaded, nothing is deleted and no file is touched.</p>
     */
    public static void recordDeclined()
    {
        declinedThisSession = true;
    }

    /**
     * Whether the player has already said "not now" since the game started.
     *
     * <p>{@code state.json} is deliberately not consulted: a {@code "status": "declined"} left behind by an
     * older build is read tolerantly by {@link InstallState} and then ignored, so it can no longer suppress
     * the prompt.</p>
     *
     * @return true if "not now" was pressed this session.
     */
    public static boolean hasDeclined()
    {
        return declinedThisSession;
    }

    /**
     * Sets the owner's source-3 override in {@code state.json} without a rebuild.
     *
     * @param url the URL to try after the two Maven sources, or null to clear it. Plain {@code http://} is
     *            accepted on purpose.
     */
    public static void setCustomSourceUrl(final String url)
    {
        try
        {
            InstallState.writeCustomSourceUrl(AssetFetch.stateFile(), url);
            AssetFetch.invalidate();
        }
        catch (final AssetInstallException e)
        {
            Log.getLogger().error("Could not record the custom asset source URL", e);
        }
    }

    /**
     * The owner's source-3 override, read whatever state the file is otherwise in.
     *
     * @return the URL, or null when unset.
     */
    private static String customSourceUrl()
    {
        return InstallState.read(AssetFetch.stateFile()).customSourceUrl();
    }

    /**
     * Builds the configuration for a chain, filling in everything that comes from the running game.
     *
     * @param sources the chain to try.
     * @return the configuration.
     */
    private static InstallConfig configFor(final List<AssetSource> sources)
    {
        return new InstallConfig(AssetFetch.baseDir(), BundleResources.ofModJar(), clientPackFormat(), sources,
            null, List.of(), AssetFetch::invalidate);
    }

    /**
     * The running game's client resource pack format (task C8).
     *
     * <p>In 26.2 {@code packVersion} returns a {@code PackFormat(major, minor)} record rather than an
     * {@code int}; only the major component goes into {@code pack.mcmeta}. See {@link PackMetaWriter} for why
     * that is the right — and the only accepted — spelling for this version.</p>
     *
     * @return the major component of the format.
     */
    private static int clientPackFormat()
    {
        return SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES).major();
    }
}
