package com.minecolonies.core.client.assetfetch;

import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.core.client.assetfetch.gui.AssetConsentScreen;
import com.minecolonies.core.client.assetfetch.gui.AssetsMissingScreen;
import net.minecraft.client.Minecraft;

import java.util.function.Supplier;

/**
 * The window-open gate (task D2): the one place the port asks "are the fetched assets there?" before it
 * builds a BlockUI window.
 *
 * <p><b>Why a gate and not a try/catch.</b> Every MineColonies window is an XML file under
 * {@code assets/minecolonies/gui/}, and those files arrive with the download. With them absent,
 * {@code Loader.createFromXMLFile} throws {@code RuntimeException("Gui at ... was not found!")}. That throw
 * happens in the {@link BOWindow} <em>constructor</em>, not in {@code open()} — the constructor is what loads
 * the XML — so the check has to come before the window is created at all. That is why every call site hands
 * this class a {@link Supplier} instead of a finished window: the supplier is not invoked unless the assets
 * are ready.</p>
 *
 * <p>When they are not ready the player gets {@link AssetsMissingScreen}, which explains the situation and
 * offers the download, rather than a crash report.</p>
 *
 * <p>Deliberately <b>not</b> annotated {@code @Environment(EnvType.CLIENT)}, for the same reason
 * {@link AssetFetch} is not: several of its call sites are common classes — items, blocks, entities — whose
 * client-only branches already construct client-only window classes. Leaving this class un-stripped means a
 * dedicated server can load it harmlessly; nothing on a server ever calls it.</p>
 */
public final class AssetFetchGate
{
    /**
     * Private constructor to hide the public one.
     */
    private AssetFetchGate()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Opens a MineColonies window, or offers the asset download instead.
     *
     * <p>The supplier is only called when {@link AssetFetch#isReady()}, because constructing the window is
     * itself the operation that would throw.</p>
     *
     * <p><b>Why the type variable.</b> With a plain {@code Supplier<BOWindow>} parameter, javac compiles a
     * call site's lambda into a synthetic method <em>declared</em> to return {@code BOWindow} while its body
     * returns the concrete window class. Verifying the call site's class then has to prove that concrete
     * class is a {@code BOWindow}, which loads it — on a dedicated server that is a client-only class and
     * Fabric refuses it, so the server dies at startup the moment such a block or item class is touched.
     * Inferring {@code T} as the concrete window type makes the synthetic method's declared and actual
     * return types identical, so nothing is proved and nothing is loaded until the lambda actually runs,
     * which only ever happens on a client.</p>
     *
     * @param <T>    the concrete window type, inferred at the call site — see above.
     * @param window builds the window to open. May return null, in which case nothing happens — some call
     *               sites legitimately have no window for the state they are in.
     */
    public static <T extends BOWindow> void openOrOffer(final Supplier<T> window)
    {
        if (!AssetFetch.isReady())
        {
            offerInstall();
            return;
        }

        final BOWindow built = window.get();
        if (built != null)
        {
            built.open();
        }
    }

    /**
     * Whether a BlockUI window may be built right now.
     *
     * <p>For the handful of call sites whose shape does not fit {@link #openOrOffer} — where the window is
     * one branch of a larger decision, or where the caller must return a value.</p>
     *
     * @return true when the fetched assets are installed.
     */
    public static boolean ready()
    {
        return AssetFetch.isReady();
    }

    /**
     * Shows the "assets are not installed" screen, from which the player can start the download.
     */
    public static void offerInstall()
    {
        final Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(new AssetsMissingScreen()));
    }

    /**
     * Shows the consent screen itself, skipping the gate screen.
     *
     * <p>This is the way back in after a decline: the client command uses it, and so does the title-screen
     * hook.</p>
     *
     * @param parent the screen to return to when the player is done, or null to return to the game.
     */
    public static void openConsent(final net.minecraft.client.gui.screens.Screen parent)
    {
        final Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(new AssetConsentScreen(parent)));
    }
}
