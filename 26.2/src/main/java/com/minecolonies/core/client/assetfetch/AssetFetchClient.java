package com.minecolonies.core.client.assetfetch;

import com.minecolonies.core.client.assetfetch.gui.AssetConsentScreen;
import com.minecolonies.core.client.assetfetch.gui.AssetFetchLang;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
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
 * <p>The command is also the one way to <em>complete</em> an install. A pack that came from a source allowed
 * not to carry part of the file set is current, so the title screen does not ask about it again — asking on
 * every launch would download the same declared absence every time — but the player who was told the
 * translations are missing and to try again later has to be able to try again. So the command refuses only a
 * pack that is installed, current and complete.</p>
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
                .then(ClientCommands.literal(COMMAND_FETCH).executes(ctx ->
                {
                    // A pack that is current but was installed from a source that could not carry all of it
                    // is still an install the player may want to complete, and this command is the only way
                    // to ask for that: the title screen deliberately leaves such a pack alone.
                    if (AssetFetch.isReady() && !AssetFetch.isStale() && InstallState.read(AssetFetch.stateFile()).isComplete())
                    {
                        ctx.getSource().sendFeedback(Component.translatable(AssetFetchLang.COMMAND_ALREADY_INSTALLED));
                        return 0;
                    }

                    AssetFetchGate.openConsent(null);
                    return 1;
                }))));
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
