package com.ldtteam.structurize.client;

import com.ldtteam.structurize.api.constants.Constants;
import com.ldtteam.structurize.client.gui.GuiStubs;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.sdl.SDLScancode;

import java.util.function.Supplier;

public class ModKeyMappings
{
    /**
     * Key mapping categories are no longer plain strings in 26.2 - {@code KeyMapping.Category} is a record
     * around an {@link Identifier}, and its label key is derived as {@code key.category.<namespace>.<path>}.
     */
    private static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "general"));

    // TODO(port-26.2): DEGRADED — IKeyConflictContext/KeyConflictContext/KeyModifier are NeoForge-only.
    //  26.2 KeyMapping has no conflict context, so the blueprint window mappings are plain global mappings.
    //  Phase 4 restored the behavioural half of the context: isBlueprintWindowActive() below is a real test
    //  again and AbstractBlueprintManipulationWindow#onUnhandledKeyTyped gates on it, so the mappings only
    //  ever act while a blueprint window is on top. What cannot be restored is the *declarative* half: the
    //  vanilla controls screen has no notion of a context and will still report these mappings as conflicting
    //  with the vanilla bindings that share their keys.
    /*
    public static final IKeyConflictContext BLUEPRINT_WINDOW = new IKeyConflictContext()
    {
        @Override
        public boolean isActive()
        {
            if (Minecraft.getInstance().screen instanceof BOScreen screen)
            {
                return screen.getWindow() instanceof AbstractBlueprintManipulationWindow;
            }
            return false;
        }

        @Override
        public boolean conflicts(IKeyConflictContext other)
        {
            return this == other;
        }
    };
    */

    /**
     * Replacement for the removed {@code BLUEPRINT_WINDOW} key conflict context: true while a blueprint
     * manipulation window is on top. Callers of the blueprint mappings have to gate on this themselves;
     * {@code AbstractBlueprintManipulationWindow#onUnhandledKeyTyped} is the only one in this mod.
     *
     * @return true while the top screen is a blueprint manipulation window.
     */
    public static boolean isBlueprintWindowActive()
    {
        return GuiStubs.isBlueprintManipulationScreenOpen();
    }

    // TODO(port-26.3): 26.3 replaced GLFW with SDL. InputConstants.Type.KEYSYM is now Type.KEYBOARD, and the
    //  int a KeyMapping is bound to is an *SDL scancode* (physical key position), not a GLFW keysym. The values
    //  below therefore come from the InputConstants.KEY_* family (scancodes), never from KEYCODE_* (layout
    //  dependent keycodes, which is what text editing uses). Where InputConstants has no name for a key -
    //  numpad minus - the scancode comes from org.lwjgl.sdl.SDLScancode. Note InputConstants.KEY_ADD == 87 ==
    //  SDL_SCANCODE_KP_PLUS, i.e. it is the numpad plus, matching the old GLFW_KEY_KP_ADD.
    /**
     * Teleport using active Scan Tool
     */
    public static final Supplier<KeyMapping> TELEPORT = lazy(() -> new KeyMapping("key.structurize.teleport",
            InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));

    /**
     * Move build previews
     */
    public static final Supplier<KeyMapping> MOVE_FORWARD = lazy(() -> new KeyMapping("key.structurize.move_forward",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_UP, CATEGORY));
    public static final Supplier<KeyMapping> MOVE_BACK = lazy(() -> new KeyMapping("key.structurize.move_back",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_DOWN, CATEGORY));
    public static final Supplier<KeyMapping> MOVE_LEFT = lazy(() -> new KeyMapping("key.structurize.move_left",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_LEFT, CATEGORY));
    public static final Supplier<KeyMapping> MOVE_RIGHT = lazy(() -> new KeyMapping("key.structurize.move_right",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_RIGHT, CATEGORY));
    public static final Supplier<KeyMapping> MOVE_UP = lazy(() -> new KeyMapping("key.structurize.move_up",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_ADD, CATEGORY));
    public static final Supplier<KeyMapping> MOVE_DOWN = lazy(() -> new KeyMapping("key.structurize.move_down",
            InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_KP_MINUS, CATEGORY));
    // TODO(port-26.2): DEGRADED — KeyModifier.SHIFT dropped, 26.2 KeyMapping has no modifier support.
    //  ROTATE_CW/CCW would collide with MOVE_RIGHT/MOVE_LEFT, so they are rebound to X/Z by default.
    /* ROTATE_CW  = shift + GLFW_KEY_RIGHT, ROTATE_CCW = shift + GLFW_KEY_LEFT */
    public static final Supplier<KeyMapping> ROTATE_CW = lazy(() -> new KeyMapping("key.structurize.rotate_cw",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_X, CATEGORY));
    public static final Supplier<KeyMapping> ROTATE_CCW = lazy(() -> new KeyMapping("key.structurize.rotate_ccw",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_Z, CATEGORY));
    public static final Supplier<KeyMapping> MIRROR = lazy(() -> new KeyMapping("key.structurize.mirror",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_M, CATEGORY));
    public static final Supplier<KeyMapping> PLACE = lazy(() -> new KeyMapping("key.structurize.place",
            InputConstants.Type.KEYBOARD, InputConstants.KEY_RETURN, CATEGORY));

    /**
     * Register key mappings. Called from {@code StructurizeClient#onInitializeClient}.
     */
    public static void init()
    {
        KeyMappingHelper.registerKeyMapping(TELEPORT.get());
        KeyMappingHelper.registerKeyMapping(MOVE_FORWARD.get());
        KeyMappingHelper.registerKeyMapping(MOVE_BACK.get());
        KeyMappingHelper.registerKeyMapping(MOVE_LEFT.get());
        KeyMappingHelper.registerKeyMapping(MOVE_RIGHT.get());
        KeyMappingHelper.registerKeyMapping(MOVE_UP.get());
        KeyMappingHelper.registerKeyMapping(MOVE_DOWN.get());
        KeyMappingHelper.registerKeyMapping(ROTATE_CW.get());
        KeyMappingHelper.registerKeyMapping(ROTATE_CCW.get());
        KeyMappingHelper.registerKeyMapping(MIRROR.get());
        KeyMappingHelper.registerKeyMapping(PLACE.get());
    }

    /**
     * Minimal stand-in for NeoForge's {@code Lazy}: keeps every {@code .get()} call site untouched while the
     * {@link KeyMapping} constructor (which self-registers into a static vanilla map) stays deferred.
     */
    private static <T> Supplier<T> lazy(final Supplier<T> factory)
    {
        return new Supplier<>()
        {
            private T value;

            @Override
            public T get()
            {
                if (value == null)
                {
                    value = factory.get();
                }
                return value;
            }
        };
    }

    /**
     * Private constructor to hide the implicit one.
     */
    private ModKeyMappings()
    {
        /*
         * Intentionally left empty.
         */
    }
}
