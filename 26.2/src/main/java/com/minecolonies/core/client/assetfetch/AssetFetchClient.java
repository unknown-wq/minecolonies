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
 * <p>On the first arrival at the title screen, and only when the assets are neither installed nor declined.
 * The hook is {@code fabric-screen-api-v1}'s {@link ScreenEvents#AFTER_INIT}, which fires for every screen
 * that finishes initialising; this filters for {@link TitleScreen}. <b>No mixin is involved</b> — the port has
 * exactly one mixin, for the pack injection, and that is deliberate.</p>
 *
 * <p>The screen is not opened from inside the title screen's own {@code init}: that would be re-entering a
 * screen change from within a screen change. It is queued with {@code client.execute(...)}, so it lands at
 * the next task drain, with the title screen fully built underneath it and available as the parent to return
 * to. A static latch makes it a genuine one-shot: closing the consent screen puts the title screen back,
 * which re-runs its {@code init}, and without the latch that would reopen the prompt forever.</p>
 *
 * <h2>Getting back in</h2>
 * <p>"Not now" is recorded in {@code state.json} and is not asked about again. Two ways back: the
 * {@code /minecolonies-client fetchassets} client command registered here, and the Download button on the
 * {@link com.minecolonies.core.client.assetfetch.gui.AssetsMissingScreen} that the window-open gate shows.</p>
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
                    if (AssetFetch.isReady())
                    {
                        ctx.getSource().sendFeedback(Component.translatable(AssetFetchLang.COMMAND_ALREADY_INSTALLED));
                        return 0;
                    }

                    AssetFetchGate.openConsent(null);
                    return 1;
                }))));
    }

    /**
     * Whether the player should be asked at all: not if the assets are already there, and not if they have
     * said no.
     *
     * @return true when the consent screen is due.
     */
    private static boolean shouldAsk()
    {
        return !AssetFetch.isReady() && !AssetInstaller.hasDeclined();
    }
}
