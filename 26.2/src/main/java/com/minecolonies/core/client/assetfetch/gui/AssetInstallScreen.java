package com.minecolonies.core.client.assetfetch.gui;

import com.minecolonies.core.client.assetfetch.AssetFetch;
import com.minecolonies.core.client.assetfetch.AssetInstaller;
import com.minecolonies.core.client.assetfetch.InstallListener;
import com.minecolonies.core.client.assetfetch.InstallPhase;
import com.minecolonies.core.client.assetfetch.InstallReport;
import com.minecolonies.core.client.assetfetch.InstallState;
import com.minecolonies.core.client.assetfetch.SourceAttempt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;

/**
 * The install screen (task D1): progress while it runs, and the result when it stops.
 *
 * <p>One screen covers both because they are the same conversation. While the installer runs it shows which
 * build is being fetched, a {@link AssetProgressBar} carrying whichever number the current phase has (bytes
 * while downloading, files while extracting, patching and assembling, a sliding block when the total is not
 * known yet), and one status line under it. When the run ends it rebuilds itself into one of three result
 * states — installed, failed, or cancelled.</p>
 *
 * <p><b>Threading.</b> Every {@link InstallListener} callback arrives on the installer's own thread, so all
 * this class does there is write volatile fields; {@link #tick()} reads them on the client thread and
 * {@link #onFinished} hands the terminal report over with {@link Minecraft#execute}. The resource reload —
 * which the installer deliberately does not do itself — happens here, on the client thread, and only after a
 * report that actually installed something.</p>
 *
 * <p><b>Failure is honest.</b> A failed install leaves the game exactly as it was: nothing is installed, the
 * pre-consent state stands, and the screen says so, lists every source that was tried with its HTTP status,
 * byte count and error text, and offers Try again / Not now.</p>
 */
@Environment(EnvType.CLIENT)
public class AssetInstallScreen extends Screen implements InstallListener
{
    /**
     * The port's issue tracker, named on the failure screen when every online source is dead.
     */
    private static final String ISSUES_URL = "https://github.com/unknown-wq/minecolonies/issues";

    /**
     * Where the screen goes when it is done. Null means "back to the game".
     */
    private final @Nullable Screen parent;

    /**
     * A local jar to install from (source 4), or null for the automatic chain. Kept so "Try again" can retry
     * the same thing the player asked for.
     */
    private final @Nullable Path localJar;

    /**
     * The running installer, for the Cancel button.
     */
    private @Nullable AssetInstaller installer;

    /**
     * Latest phase, written by the installer thread.
     */
    private volatile InstallPhase phase = InstallPhase.STARTING;

    /**
     * Latest "which source" line, written by the installer thread; null until a source starts.
     */
    private volatile @Nullable Component sourceLine = null;

    /**
     * Download progress, written by the installer thread. {@code total} is -1 when the server did not say.
     */
    private volatile long bytes = 0L;

    /**
     * Expected download size, or -1.
     */
    private volatile long bytesTotal = -1L;

    /**
     * File progress, written by the installer thread.
     */
    private volatile int files = 0;

    /**
     * Expected file count, or -1.
     */
    private volatile int filesTotal = -1;

    /**
     * The terminal report, or null while the run is still going. Only ever set on the client thread.
     */
    private @Nullable InstallReport report = null;

    /**
     * True between a successful report and the end of the resource reload.
     */
    private boolean reloading = false;

    /**
     * The bar; updated every tick while running.
     */
    private @Nullable AssetProgressBar barWidget = null;

    /**
     * The one status line under the bar: phase, and whatever it can count. Updated every tick while running.
     */
    private @Nullable StringWidget statusWidget = null;

    /**
     * The line that names the source being fetched; updated every tick while running.
     */
    private @Nullable StringWidget sourceWidget = null;

    /**
     * The running state's layout, kept so the two live lines can be re-centred when their text changes.
     *
     * <p>A {@link StringWidget} is exactly as wide as its message, and the layout centres it by that width —
     * measured once, when the widgets are built. Both live lines start out short or empty (no source has been
     * tried yet, nothing has been counted yet), so without re-arranging, the source line would be laid out at
     * width zero and then draw from the middle of the screen off the right-hand edge.</p>
     */
    private @Nullable LinearLayout runningLayout = null;

    /**
     * Creates the screen. Use {@link #startAutomatic} or {@link #startLocalJar}, which also start the run.
     *
     * @param parent   the screen to return to, or null for the game.
     * @param localJar a player-picked jar, or null for the automatic source chain.
     */
    private AssetInstallScreen(final @Nullable Screen parent, final @Nullable Path localJar)
    {
        super(Component.translatable(AssetFetchLang.PROGRESS_TITLE));
        this.parent = parent;
        this.localJar = localJar;
    }

    /**
     * Builds the screen and starts the automatic source chain on it.
     *
     * @param parent the screen to return to, or null for the game.
     * @return the screen to show.
     */
    public static AssetInstallScreen startAutomatic(final @Nullable Screen parent)
    {
        final AssetInstallScreen screen = new AssetInstallScreen(parent, null);
        screen.begin();
        return screen;
    }

    /**
     * Builds the screen and starts an install from a jar the player picked (source 4).
     *
     * @param parent the screen to return to, or null for the game.
     * @param jar    the file the player chose.
     * @return the screen to show.
     */
    public static AssetInstallScreen startLocalJar(final @Nullable Screen parent, final Path jar)
    {
        final AssetInstallScreen screen = new AssetInstallScreen(parent, jar);
        screen.begin();
        return screen;
    }

    /**
     * Starts (or restarts) the install this screen is showing.
     */
    private void begin()
    {
        this.report = null;
        this.reloading = false;
        this.phase = InstallPhase.STARTING;
        this.sourceLine = null;
        this.bytes = 0L;
        this.bytesTotal = -1L;
        this.files = 0;
        this.filesTotal = -1;

        this.installer = this.localJar == null ? AssetInstaller.forGame() : AssetInstaller.forLocalJar(this.localJar);
        this.installer.start(this);
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init()
    {
        super.init();

        this.barWidget = null;
        this.statusWidget = null;
        this.sourceWidget = null;
        this.runningLayout = null;

        final int textWidth = Math.min(this.width - 40, 380);
        final LinearLayout layout = LinearLayout.vertical().spacing(8);
        layout.defaultCellSetting().alignHorizontallyCenter();

        final InstallReport finished = this.report;
        if (finished == null)
        {
            this.runningLayout = layout;
            this.buildRunning(layout, textWidth);
        }
        else if (finished.succeeded())
        {
            this.buildInstalled(layout, textWidth, finished);
        }
        else if (finished.outcome() == InstallReport.Outcome.CANCELLED)
        {
            this.buildCancelled(layout, textWidth);
        }
        else
        {
            this.buildFailed(layout, textWidth, finished);
        }

        layout.visitWidgets(this::addRenderableWidget);
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, this.getRectangle());
    }

    /**
     * The running state: what is being fetched, the bar, one status line and a Cancel button.
     *
     * <p>There is deliberately no separate "Downloading..." line: the title says that already, the bar says it
     * is still happening, and the status line says how far along it is.</p>
     *
     * @param layout    the layout to fill.
     * @param textWidth how wide text may be.
     */
    private void buildRunning(final LinearLayout layout, final int textWidth)
    {
        layout.addChild(new StringWidget(this.title, this.font));

        this.sourceWidget = layout.addChild(new StringWidget(this.sourceLabel(), this.font).setMaxWidth(textWidth));

        this.barWidget = layout.addChild(new AssetProgressBar(barWidth(this.width), this.font));
        this.updateBar();

        this.statusWidget = layout.addChild(new StringWidget(this.statusLabel(), this.font).setMaxWidth(textWidth));

        final LinearLayout buttons = layout.addChild(LinearLayout.horizontal().spacing(8));
        buttons.defaultCellSetting().paddingTop(12);
        buttons.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_CANCEL), b -> this.cancel()).width(120).build());
    }

    /**
     * The success state.
     *
     * @param layout    the layout to fill.
     * @param textWidth how wide text may be.
     * @param finished  the report.
     */
    private void buildInstalled(final LinearLayout layout, final int textWidth, final InstallReport finished)
    {
        layout.addChild(new StringWidget(Component.translatable(AssetFetchLang.DONE_TITLE), this.font));
        layout.addChild(new MultiLineTextWidget(
            Component.translatable(AssetFetchLang.DONE_BODY,
                AssetFetchScreenSupport.count(finished.filesWritten() + finished.filesCarried()),
                AssetFetchScreenSupport.megabytesNumber(finished.packBytes())),
            this.font).setMaxWidth(textWidth).setCentered(true));

        // What the archive could not supply is worth a line either way: files kept from the previous install
        // explain why an install of a new build changed less than expected, and files nothing could supply
        // are ones the player is going to notice missing and is owed the reason for.
        final InstallState state = InstallState.read(AssetFetch.stateFile());
        if (state.filesCarried() > 0)
        {
            layout.addChild(new MultiLineTextWidget(
                Component.translatable(AssetFetchLang.DONE_CARRIED, AssetFetchScreenSupport.count(state.filesCarried())),
                this.font).setMaxWidth(textWidth).setCentered(true));
        }
        if (state.filesAbsent() > 0)
        {
            layout.addChild(new MultiLineTextWidget(
                Component.translatable(AssetFetchLang.DONE_PARTIAL, AssetFetchScreenSupport.count(state.filesAbsent())),
                this.font).setMaxWidth(textWidth).setCentered(true));
        }

        if (this.reloading)
        {
            layout.addChild(new StringWidget(Component.translatable(AssetFetchLang.PROGRESS_RELOADING), this.font));
        }

        final LinearLayout buttons = layout.addChild(LinearLayout.horizontal().spacing(8));
        buttons.defaultCellSetting().paddingTop(12);
        final Button close = Button.builder(Component.translatable(AssetFetchLang.BUTTON_CLOSE), b -> this.onClose()).width(120).build();
        close.active = !this.reloading;
        buttons.addChild(close);
    }

    /**
     * The cancelled state.
     *
     * @param layout    the layout to fill.
     * @param textWidth how wide text may be.
     */
    private void buildCancelled(final LinearLayout layout, final int textWidth)
    {
        layout.addChild(new StringWidget(Component.translatable(AssetFetchLang.CANCELLED_TITLE), this.font));
        layout.addChild(new MultiLineTextWidget(Component.translatable(AssetFetchLang.CANCELLED_BODY), this.font)
            .setMaxWidth(textWidth).setCentered(true));

        final LinearLayout buttons = layout.addChild(LinearLayout.horizontal().spacing(8));
        buttons.defaultCellSetting().paddingTop(12);
        buttons.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_RETRY), b -> this.retry()).width(120).build());
        buttons.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_NOT_NOW), b -> this.notNow()).width(120).build());
    }

    /**
     * The failure state: the outcome, then one line per source tried, then what to do about it.
     *
     * @param layout    the layout to fill.
     * @param textWidth how wide text may be.
     * @param finished  the report.
     */
    private void buildFailed(final LinearLayout layout, final int textWidth, final InstallReport finished)
    {
        layout.addChild(new StringWidget(Component.translatable(AssetFetchLang.FAILED_TITLE), this.font));
        layout.addChild(new MultiLineTextWidget(Component.translatable(AssetFetchLang.FAILED_REASON, finished.reason()), this.font)
            .setMaxWidth(textWidth).setCentered(true));

        if (!finished.attempts().isEmpty())
        {
            final MutableComponent attempts = Component.translatable(AssetFetchLang.FAILED_ATTEMPTS);
            for (final SourceAttempt attempt : finished.attempts())
            {
                attempts.append(Component.literal("\n")).append(describe(attempt));
            }
            layout.addChild(new MultiLineTextWidget(attempts, this.font).setMaxWidth(textWidth).setMaxRows(14));
        }

        layout.addChild(new MultiLineTextWidget(Component.translatable(AssetFetchLang.FAILED_UNCHANGED), this.font)
            .setMaxWidth(textWidth).setCentered(true));
        layout.addChild(new MultiLineTextWidget(Component.translatable(AssetFetchLang.FAILED_REPORT, ISSUES_URL), this.font)
            .setMaxWidth(textWidth).setCentered(true));

        final LinearLayout buttons = layout.addChild(LinearLayout.horizontal().spacing(8));
        buttons.defaultCellSetting().paddingTop(12);
        buttons.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_RETRY), b -> this.retry()).width(120).build());
        buttons.addChild(Button.builder(Component.translatable(AssetFetchLang.BUTTON_NOT_NOW), b -> this.notNow()).width(120).build());
    }

    /**
     * One line of the attempt list: which source, and either that it worked or exactly how it did not.
     *
     * @param attempt the attempt.
     * @return the line.
     */
    private static Component describe(final SourceAttempt attempt)
    {
        if (attempt.succeeded())
        {
            return Component.translatable(AssetFetchLang.FAILED_ATTEMPT_OK, attempt.sourceId(), attempt.url(),
                AssetFetchScreenSupport.megabytes(attempt.bytes()));
        }
        final String status = attempt.httpStatus() >= 0 ? String.valueOf(attempt.httpStatus()) : "-";
        return Component.translatable(AssetFetchLang.FAILED_ATTEMPT_BAD, attempt.sourceId(), attempt.url(), status,
            AssetFetchScreenSupport.megabytes(attempt.bytes()), String.valueOf(attempt.error()));
    }

    // ------------------------------------------------------------------ live progress

    @Override
    public void tick()
    {
        super.tick();
        if (this.report != null)
        {
            return;
        }

        boolean resized = false;
        if (this.sourceWidget != null)
        {
            resized |= retitle(this.sourceWidget, this.sourceLabel());
        }
        if (this.statusWidget != null)
        {
            resized |= retitle(this.statusWidget, this.statusLabel());
        }
        if (resized)
        {
            this.recentre();
        }
        this.updateBar();
    }

    /**
     * Puts new text on a line, and says whether that changed how wide it is.
     *
     * @param widget  the line.
     * @param message the new text.
     * @return true when the line has to be re-centred.
     */
    private static boolean retitle(final StringWidget widget, final Component message)
    {
        if (message.equals(widget.getMessage()))
        {
            return false;
        }
        final int before = widget.getWidth();
        widget.setMessage(message);
        return widget.getWidth() != before;
    }

    /**
     * Re-centres the running layout after one of its lines changed width.
     */
    private void recentre()
    {
        final LinearLayout layout = this.runningLayout;
        if (layout == null)
        {
            return;
        }
        layout.arrangeElements();
        FrameLayout.centerInRectangle(layout, this.getRectangle());
    }

    /**
     * The phase label, e.g. "Unpacking". Shown on its own only when the phase has nothing to count.
     *
     * @return the component.
     */
    private Component phaseLabel()
    {
        return Component.translatable(AssetFetchLang.PHASE_PREFIX + this.phase.name().toLowerCase(Locale.ROOT));
    }

    /**
     * The line naming the source, or an empty line before the first source starts.
     *
     * @return the component.
     */
    private Component sourceLabel()
    {
        final Component line = this.sourceLine;
        return line == null ? Component.empty() : line;
    }

    /**
     * The one status line under the bar: megabytes while downloading, the phase and a file count while
     * extracting, patching and assembling, and the bare phase when there is nothing yet to count.
     *
     * <p>The unit is not appended here. {@code progress.bytes} carries its own {@code MB} / {@code МБ}, so a
     * translation says it once, in its own alphabet, and can put it where that language puts it.</p>
     *
     * @return the component.
     */
    private Component statusLabel()
    {
        if (this.phase == InstallPhase.DOWNLOADING)
        {
            final long total = this.bytesTotal;
            final long done = this.bytes;
            if (total > 0)
            {
                return Component.translatable(AssetFetchLang.PROGRESS_BYTES,
                    AssetFetchScreenSupport.megabytesNumber(done), AssetFetchScreenSupport.megabytesNumber(total));
            }
            if (done <= 0)
            {
                return this.phaseLabel();
            }
            return Component.translatable(AssetFetchLang.PROGRESS_BYTES_UNKNOWN, AssetFetchScreenSupport.megabytesNumber(done));
        }

        final int total = this.filesTotal;
        if (this.files <= 0 && total <= 0)
        {
            return this.phaseLabel();
        }
        if (total > 0)
        {
            return Component.translatable(AssetFetchLang.PROGRESS_FILES, this.phaseLabel(),
                AssetFetchScreenSupport.count(this.files), AssetFetchScreenSupport.count(total));
        }
        return Component.translatable(AssetFetchLang.PROGRESS_FILES_UNKNOWN, this.phaseLabel(),
            AssetFetchScreenSupport.count(this.files));
    }

    /**
     * Points the bar at whichever number the current phase has.
     *
     * <p>Downloading counts bytes, extract/patch/assemble count files, and everything else — and any phase whose
     * total the installer has not worked out yet, which is what {@code EXTRACTING} does while it walks the jar
     * — gets the sliding block instead of a lie about how far along it is.</p>
     */
    private void updateBar()
    {
        final AssetProgressBar bar = this.barWidget;
        if (bar == null)
        {
            return;
        }
        if (this.phase == InstallPhase.DOWNLOADING)
        {
            bar.setProgress(this.bytes, this.bytesTotal);
            return;
        }
        bar.setProgress(this.files, this.filesTotal);
    }

    /**
     * How wide the bar is: about two thirds of the screen, kept sane on very narrow and very wide windows.
     *
     * @param screenWidth the screen width.
     * @return the bar width in pixels.
     */
    private static int barWidth(final int screenWidth)
    {
        return Mth.clamp(screenWidth * 2 / 3, 120, 400);
    }

    // ------------------------------------------------------------------ installer callbacks (installer thread)

    @Override
    public void onPhase(final InstallPhase newPhase)
    {
        if (newPhase != this.phase)
        {
            // Each phase counts its own files; carrying the last one's total over would put the bar at 100%
            // for the tick between the phase change and its first onFiles.
            this.files = 0;
            this.filesTotal = -1;
        }
        this.phase = newPhase;
    }

    @Override
    public void onSourceStarted(final String sourceId, final String url, final String description)
    {
        // description is the chain's own English blurb and url is a 130-character diagnostic; neither belongs
        // in front of a player mid-download, so the human name is built from the id instead.
        this.sourceLine = AssetFetchScreenSupport.sourceLine(sourceId, url);
        this.bytes = 0L;
        this.bytesTotal = -1L;
        this.files = 0;
        this.filesTotal = -1;
    }

    @Override
    public void onBytes(final long transferred, final long total)
    {
        this.bytes = transferred;
        this.bytesTotal = total;
    }

    @Override
    public void onFiles(final int done, final int total)
    {
        this.files = done;
        this.filesTotal = total;
    }

    @Override
    public void onFinished(final InstallReport finished)
    {
        final Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> this.deliver(mc, finished));
    }

    /**
     * Takes the terminal report on the client thread: reload resources if something was installed, then show
     * the matching result state.
     *
     * @param mc       the client.
     * @param finished the report.
     */
    private void deliver(final Minecraft mc, final InstallReport finished)
    {
        this.report = finished;
        this.installer = null;

        if (!finished.succeeded())
        {
            this.rebuildIfShowing(mc);
            return;
        }

        // The installer never reloads resources itself; this is the one place that does, and it is the client
        // thread. AssetFetch.invalidate() has already run, so the pack repository will offer the new pack.
        this.reloading = true;
        this.rebuildIfShowing(mc);
        mc.reloadResourcePacks().thenRun(() -> mc.execute(() ->
        {
            this.reloading = false;
            this.rebuildIfShowing(mc);
        }));
    }

    /**
     * Rebuilds the widgets, but only while this screen is the one on screen.
     *
     * @param mc the client.
     */
    private void rebuildIfShowing(final Minecraft mc)
    {
        if (mc.gui.screen() == this)
        {
            this.rebuildWidgets();
        }
    }

    // ------------------------------------------------------------------ buttons

    /**
     * Asks the installer to stop. The run ends as CANCELLED and changes nothing.
     */
    private void cancel()
    {
        final AssetInstaller running = this.installer;
        if (running != null)
        {
            running.cancel();
        }
    }

    /**
     * Runs the same install again.
     */
    private void retry()
    {
        this.begin();
        this.rebuildWidgets();
    }

    /**
     * Records the decline and leaves.
     */
    private void notNow()
    {
        AssetInstaller.recordDeclined();
        this.onClose();
    }

    @Override
    public boolean shouldCloseOnEsc()
    {
        return this.report != null && !this.reloading;
    }

    @Override
    public void onClose()
    {
        if (this.reloading)
        {
            return;
        }
        this.cancel();
        this.minecraft.gui.setScreen(this.parent);
    }
}
