package com.ldtteam.blockui.mod;

import com.ldtteam.blockui.views.BOWindow;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Opens one of the developer test windows straight after the client reaches its first screen, gated on a launch
 * argument.
 *
 * <p><b>The gate.</b> A JVM system property, {@code -Dblockui.testgui[=<screen>]}, read once from
 * {@link BlockUIClient#onInitializeClient()}. It was chosen over a config file, a keybind-only path or a build flag for
 * three reasons:</p>
 * <ul>
 *     <li>it is part of the launch arguments, so a launcher profile or a CI job can ask for it without editing any
 *         file in the game directory, and nothing has to be carved back out of the jar afterwards;</li>
 *     <li>it needs no mixin and no vanilla patching - {@link System#getProperty} is all of it;</li>
 *     <li>the property being absent is indistinguishable, for a player, from this class not existing:
 *         {@link #armIfRequested()} returns on its first statement, registers no callback, allocates nothing and
 *         logs nothing.</li>
 * </ul>
 *
 * <p><b>It cannot reach a dedicated server.</b> The only caller is {@link BlockUIClient}, which is the mod's
 * {@code client} entrypoint in {@code fabric.mod.json}. A dedicated server never loads a {@code ClientModInitializer},
 * so neither this class nor the windows it opens are reachable there even with the property set on the server command
 * line.</p>
 *
 * <p>The ctrl + alt + shift + X menu in {@link ClientEventSubscriber#onClientTickStart(Minecraft)} still works and is
 * unaffected; {@code -Dblockui.testgui=menu} simply opens that same menu without the keyboard.</p>
 *
 * <p><b>These screens only open inside a world, and that is not a limitation of the launcher.</b> Any layout
 * holding an {@code <itemicon item="…">} calls {@code Item#getDefaultInstance()} while it is being parsed, and on
 * the title screen that fails with {@code NullPointerException: Components not bound yet} - item components are
 * bound when a level is loaded, so before that there is no stack to build. Worse, the block-state icons dereference
 * {@code Minecraft#player} and {@code Minecraft#level} unguarded while drawing, which is a crash report rather than
 * a missing widget. None of that is specific to these layouts; {@code gui/test.xml} has always had it. So the
 * launcher waits for a player in a world (see {@link OneShotOpener}) and pairs with vanilla's
 * {@code --quickPlaySingleplayer <world>} argument:</p>
 *
 * <pre>java … -Dblockui.testgui=showcase … --quickPlaySingleplayer "New World"</pre>
 */
public final class TestGuiLauncher
{
    /**
     * The launch argument. Absent means "do nothing at all"; present with an empty value means {@link #DEFAULT_SCREEN}.
     */
    public static final String PROPERTY = "blockui.testgui";

    /**
     * What a bare {@code -Dblockui.testgui} (or {@code =true}) opens.
     */
    public static final String DEFAULT_SCREEN = "showcase";

    /**
     * How long to wait, in client ticks, once the client has a screen and no overlay. The title screen is not
     * necessarily the last screen to appear during start-up - a mod may replace it moments later - so opening on the
     * very first tick that qualifies is a race. Twenty ticks is one second at the client tick rate.
     */
    private static final int OPEN_DELAY_TICKS = 40;

    /**
     * How many times to try before giving up. The layouts come out of {@link com.ldtteam.blockui.Loader}'s cache,
     * which is filled by a resource-reload listener; the wait below is meant to outlast that reload, and a retry is
     * what turns "we were still a moment too early" into a slower open instead of an empty screen.
     */
    private static final int MAX_ATTEMPTS = 5;

    /**
     * The screens {@link #PROPERTY} can name, by their short names. Anything not in here is treated as a resource
     * location of an xml layout, see {@link #resolve(String)}.
     */
    private static final Map<String, Supplier<BOWindow>> SCREENS = new LinkedHashMap<>();
    static
    {
        // The layouts written for this launcher. All four live in this mod's own resources.
        SCREENS.put("showcase", () -> ShowcaseGui.showcase());
        SCREENS.put("nesting", () -> ShowcaseGui.nesting());
        SCREENS.put("parser", () -> ShowcaseGui.parser());
        SCREENS.put("inherit", () -> ShowcaseGui.inherited());

        // The pre-existing developer windows, so the launch argument reaches everything the key combination does.
        SCREENS.put("menu", ClientEventSubscriber::buildDevMenu);
        SCREENS.put("test", () -> ClientEventSubscriber.buildTestWindow(1));
        SCREENS.put("test2", () -> ClientEventSubscriber.buildTestWindow(2));
        SCREENS.put("test3", () -> ClientEventSubscriber.buildTestWindow(3));
        SCREENS.put("test4", () -> ClientEventSubscriber.buildTestWindow(4));
    }

    private TestGuiLauncher()
    {
        // utility class
    }

    /**
     * Reads the launch argument and, only if it is there, arms a one-shot client-tick callback that opens the window
     * it names.
     */
    public static void armIfRequested()
    {
        final String requested = System.getProperty(PROPERTY);
        if (requested == null)
        {
            // No property: nothing is registered, nothing is logged, nothing changes.
            return;
        }

        final String screen = requested.isBlank() || requested.equalsIgnoreCase("true") ? DEFAULT_SCREEN : requested.trim();
        if (resolve(screen) == null)
        {
            Log.getLogger()
                .error("-D{}={} names no test screen. Known names: {}; anything else is read as the resource location of"
                    + " an xml layout, e.g. -D{}=blockui:gui/test.xml",
                    PROPERTY,
                    screen,
                    String.join(", ", SCREENS.keySet()),
                    PROPERTY);
            return;
        }

        Log.getLogger().info("-D{}={} is set; opening that test screen once the client has one.", PROPERTY, screen);
        ClientTickEvents.END_CLIENT_TICK.register(new OneShotOpener(screen));
    }

    /**
     * @param screen a short name from {@link #SCREENS}, or the resource location of an xml layout
     * @return a supplier of the window, or null if the name is neither
     */
    @Nullable
    private static Supplier<BOWindow> resolve(final String screen)
    {
        final Supplier<BOWindow> known = SCREENS.get(screen.toLowerCase(Locale.ROOT));
        if (known != null)
        {
            return known;
        }

        final Identifier resLoc = Identifier.tryParse(screen);
        return resLoc == null || resLoc.getPath().isEmpty() ? null : () -> new BOWindow(resLoc);
    }

    /**
     * Waits for the client to reach a screen with no overlay over it - which is to say, past the loading overlay and
     * therefore past the resource reload that fills the xml cache - waits {@link #OPEN_DELAY_TICKS} more, opens the
     * window and then does nothing for the rest of the session. Fabric has no "unregister this callback" call, so the
     * no-op tail is how a one-shot is spelled.
     */
    private static final class OneShotOpener implements ClientTickEvents.EndTick
    {
        private final String screen;
        private Object lastScreen = null;
        private int waited = -1;
        private int attempts = 0;
        private boolean done = false;

        private OneShotOpener(final String screen)
        {
            this.screen = screen;
        }

        @Override
        public void onEndTick(final Minecraft mc)
        {
            if (done)
            {
                return;
            }

            // "Settled" is: no loading overlay, and a player in a world. Both halves are load-bearing.
            //
            //  * gui.overlay() is the loading overlay, up while resources are being reloaded. Opening a window before
            //    that finishes finds an empty Loader cache and throws "Gui at ... was not found".
            //  * mc.player, not mc.level and not "a screen is up": three separate places in this library dereference
            //    the client player or the client level with no null check while a window is being parsed or drawn -
            //    ItemIcon#getModifiedItemStackTooltip, ItemIconWithBlockState#readBlockStateFromCurrentItemStack and
            //    BlockStatePipRenderer's submit lambda. Any layout with an item icon in it is therefore only safe
            //    once a world is actually joined, which is also the state --quickPlaySingleplayer settles into a
            //    second or two after the level appears. Opening a tick early is not untidy, it is a crash report.
            if (mc.gui.overlay() != null || mc.player == null)
            {
                waited = -1;
                return;
            }

            // Any change of screen restarts the wait. Start-up walks through several screens - a mod's own prompt, a
            // world-loading progress screen - and the point of the delay is to open after the last of them, not during.
            if (mc.gui.screen() != lastScreen)
            {
                lastScreen = mc.gui.screen();
                waited = 0;
                return;
            }

            if (++waited < OPEN_DELAY_TICKS)
            {
                return;
            }
            waited = 0;

            try
            {
                final Supplier<BOWindow> supplier = resolve(screen);
                // resolve() already succeeded once in armIfRequested; a null here is impossible, but a launcher that
                // crashes the client it was asked to test is worse than one that says why it did not open.
                if (supplier != null)
                {
                    supplier.get().open();
                    Log.getLogger().info("Opened test screen '{}'.", screen);
                }
                done = true;
            }
            catch (final RuntimeException e)
            {
                if (++attempts >= MAX_ATTEMPTS)
                {
                    done = true;
                    Log.getLogger().error("Could not open test screen '" + screen + "', giving up", e);
                }
                else
                {
                    Log.getLogger()
                        .warn("Could not open test screen '{}' on attempt {} of {}, retrying: {}",
                            screen,
                            attempts,
                            MAX_ATTEMPTS,
                            e.toString());
                }
            }
        }
    }
}
