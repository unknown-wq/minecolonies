package com.minecolonies.core.client.assetfetch;

import com.minecolonies.api.util.Log;
import com.minecolonies.core.client.assetfetch.gui.AssetConsentScreen;
import com.minecolonies.core.client.assetfetch.gui.AssetFetchLang;
import com.minecolonies.core.client.assetfetch.gui.AssetFetchScreenSupport;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Wires the consent flow into the client (task D1): when to ask, and how to get back in after a "not now".
 *
 * <h2>When it asks</h2>
 * <p>On the first arrival at the title screen, and only when the assets are not installed <em>or are the
 * previous version's</em>, and "not now" has not already been pressed <i>this session</i>. The hook is
 * {@code fabric-screen-api-v1}'s {@link ScreenEvents#AFTER_INIT}, which fires for every screen that
 * finishes initialising; this filters for {@link TitleScreen}. <b>No mixin is involved</b> — the port has
 * exactly one mixin, for the pack injection, and that is deliberate.</p>
 *
 * <p>The screen is not opened from inside the title screen's own {@code init}: that would be re-entering a
 * screen change from within a screen change. It is queued with {@code client.execute(...)}, so it lands at
 * the next task drain, with the title screen fully built underneath it and available as the parent to return
 * to. A static latch makes it a genuine one-shot: closing the consent screen puts the title screen back,
 * which re-runs its {@code init}, and without the latch that would reopen the prompt forever.</p>
 *
 * <h2>Getting back in</h2>
 * <p>"Not now" holds for the rest of the session and nothing more — it is not written to {@code state.json},
 * so <b>the next launch asks again</b>, and goes on asking until the assets are installed. That is the point:
 * a mod that cannot draw a single one of its own windows should say so every time it starts, not once. Within
 * a session there are still the two other ways back — the {@code /minecolonies-client fetchassets} client
 * command registered here, and the Download button on the
 * {@link com.minecolonies.core.client.assetfetch.gui.AssetsMissingScreen} that the window-open gate shows.</p>
 *
 * <p>The command is also the one way to <em>complete</em> an install. A pack the archive could not fill in
 * completely is current, so the title screen does not ask about it again — asking on every launch would fetch
 * the same partial source every time — but the player who was told the translations are missing and to try
 * again later has to be able to try again. So a pack that is installed, current and complete is the only one
 * that does not go straight to the consent screen.</p>
 *
 * <h2>And it repairs</h2>
 * <p>That last case used to be a refusal: "the assets are already installed", and nothing done. But
 * "installed" is a claim {@link AssetFetch#isReady()} makes on the strength of {@code state.json} and
 * {@code pack.mcmeta} alone, and a pack can satisfy both while having lost any number of the eight and a half
 * thousand files it is supposed to hold. A player whose pack lost its raider textures and its citizen voices
 * saw a checkerboarded game, typed the one command there is, and was told everything was fine — with no way
 * out except deleting the cache directory by hand, which nothing tells them to do.</p>
 *
 * <p>So the command now <b>looks</b> ({@link PackAudit}) before it answers, and what it answers is a count.
 * A pack with everything in it is said to be complete and nothing is touched; a pack with files missing is
 * repaired. The repair is the ordinary install, because it has to be: the download sources are whole
 * archives, the manifest lists paths and no per-file hashes, so there is no way to ask any source for three
 * textures. That makes even a three-file repair a 78 MB download, and the command says so, with the size,
 * before it starts one. It does not ask again — the player installed these same assets once, and the consent
 * screen's question has already been answered — but it does not spring the download on them either.</p>
 */
@Environment(EnvType.CLIENT)
public final class AssetFetchClient
{
    /**
     * Root literal of the client-side command. It is deliberately not {@code /minecolonies}: that one is the
     * server's, and this command has to work before — and whether or not — a colony exists.
     */
    private static final String COMMAND_ROOT = "minecolonies-client";

    /**
     * The subcommand that reopens the consent screen.
     */
    private static final String COMMAND_FETCH = "fetchassets";

    /**
     * Whether the title-screen prompt has already been shown this session.
     */
    private static boolean prompted = false;

    /**
     * Private constructor to hide the public one.
     */
    private AssetFetchClient()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Registers the title-screen prompt and the client command. Called from the client initializer.
     */
    public static void register()
    {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
        {
            if (!(screen instanceof TitleScreen) || prompted || !shouldAsk())
            {
                return;
            }

            prompted = true;
            client.execute(() ->
            {
                if (client.gui.screen() == screen)
                {
                    client.gui.setScreen(new AssetConsentScreen(screen));
                }
            });
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
            dispatcher.register(ClientCommands.literal(COMMAND_ROOT)
                .then(ClientCommands.literal(COMMAND_FETCH).executes(ctx -> fetchAssets(ctx.getSource())))));
    }

    /**
     * The command body: install what is not there, or repair what is.
     *
     * @param source where to send the player's feedback.
     * @return 1 when something was started or the pack was found whole, 0 when the check could not be made.
     */
    private static int fetchAssets(final FabricClientCommandSource source)
    {
        // A pack that is not there, is the previous version's, or is missing files no source could supply is
        // an install to offer rather than a pack to audit, and the consent screen is where that is offered.
        // The title screen deliberately leaves the last of those three alone, so this is the only way to ask.
        if (!AssetFetch.isReady() || AssetFetch.isStale() || !InstallState.read(AssetFetch.stateFile()).isComplete())
        {
            AssetFetchGate.openConsent(null);
            return 1;
        }

        return repair(source);
    }

    /**
     * Checks an install that claims to be complete, and starts the download again if it is not.
     *
     * <p>Nothing is deleted and nothing outside the install's own cache directory is read: the audit is a
     * {@code stat} per manifest entry, and the repair is the ordinary install, which stages a whole pack
     * elsewhere and swaps it in only once it is finished. A run against an intact pack writes nothing at
     * all.</p>
     *
     * @param source where to send the player's feedback.
     * @return 1 when the pack was whole or a repair was started, 0 when the manifest could not be read.
     */
    private static int repair(final FabricClientCommandSource source)
    {
        final AssetManifest manifest;
        try
        {
            manifest = AssetManifest.load(BundleResources.ofModJar());
        }
        catch (final AssetInstallException | RuntimeException e)
        {
            // Without the file list there is nothing to check the pack against. Saying so beats both a
            // silent failure and a 78 MB download started on no evidence.
            Log.getLogger().error("The MineColonies asset manifest could not be read, so the installed assets"
                + " could not be checked", e);
            source.sendFeedback(Component.translatable(AssetFetchLang.COMMAND_CHECK_FAILED, e.getMessage()));
            return 0;
        }

        final PackAudit.Result audit = PackAudit.audit(AssetFetch.packDir(), manifest.files());
        if (audit.ok())
        {
            Log.getLogger().info("The installed MineColonies assets hold all {} files the manifest lists", audit.expected());
            source.sendFeedback(Component.translatable(AssetFetchLang.COMMAND_VERIFIED,
                AssetFetchScreenSupport.count(audit.expected())));
            return 1;
        }

        Log.getLogger().warn("The installed MineColonies assets are missing {} of the {} files the manifest lists,"
            + " so they are being fetched again -- {}", audit.missing().size(), audit.expected(), audit.describe());
        source.sendFeedback(Component.translatable(AssetFetchLang.COMMAND_REPAIRING,
            AssetFetchScreenSupport.count(audit.missing().size()),
            AssetFetchScreenSupport.count(audit.expected()),
            AssetFetchScreenSupport.megabytesNumber(SourceChain.MAVEN_1374.expectedSize())));
        AssetFetchGate.openInstall(null);
        return 1;
    }

    /**
     * Whether the player should be asked at all: not if the assets that are there are the ones this build
     * expects, and not if the player has said "not now" since this game started.
     *
     * <p>An installed pack from an earlier version of this mod asks again, on the same terms as no pack at
     * all: the player is offered the current assets and may say no, and while they say no the pack they have
     * goes on being served rather than being taken away from them.</p>
     *
     * @return true when the consent screen is due.
     */
    private static boolean shouldAsk()
    {
        return (!AssetFetch.isReady() || AssetFetch.isStale()) && !AssetInstaller.hasDeclined();
    }
}
