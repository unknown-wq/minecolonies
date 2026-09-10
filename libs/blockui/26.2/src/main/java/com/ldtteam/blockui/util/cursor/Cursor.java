package com.ldtteam.blockui.util.cursor;

import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.blockui.util.SafeError;
import com.ldtteam.blockui.util.texture.CursorTexture;
import com.ldtteam.blockui.util.texture.IsOurTexture;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Interface to wrap various cursors
 */
public class Cursor
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Cursor.class);

    /** Probably arrow, but OS dependend */
    public static final CursorType DEFAULT = CursorType.DEFAULT;
    public static final CursorType ARROW = CursorTypes.ARROW;
    public static final CursorType TEXT_CURSOR = CursorTypes.IBEAM;
    public static final CursorType CROSSHAIR = CursorTypes.CROSSHAIR;
    public static final CursorType HAND = CursorTypes.POINTING_HAND;
    public static final CursorType HORIZONTAL_RESIZE = CursorTypes.RESIZE_EW;
    public static final CursorType VERTICAL_RESIZE = CursorTypes.RESIZE_NS;
    public static final CursorType RESIZE_NWSE = CursorType.createStandardCursor(GLFW.GLFW_RESIZE_NWSE_CURSOR, "resize_nwse", Cursor.DEFAULT);
    public static final CursorType RESIZE_NESW = CursorType.createStandardCursor(GLFW.GLFW_RESIZE_NESW_CURSOR, "resize_nesw", Cursor.DEFAULT);
    public static final CursorType RESIZE = CursorTypes.RESIZE_ALL;
    public static final CursorType NOT_ALLOWED = CursorTypes.NOT_ALLOWED;

    /**
     * Name -> cursor lookup behind the {@code blockui_std:} namespace in {@link #of(Identifier)}.
     * <p>
     * The constants above are not guaranteed to be distinct objects: {@code CursorType#createStandardCursor} returns the
     * fallback it was handed - here always {@link #DEFAULT}, whose name is {@code "default"} - whenever the platform
     * cannot supply that shape - which cursors those are is up to the OS and the active cursor theme, and on Linux the
     * two diagonal resize shapes are the usual casualties. The list below then holds {@link #DEFAULT} more than once,
     * and {@code Map.ofEntries} rejects the repeated key outright: {@code IllegalArgumentException: duplicate key:
     * default} killed this class initializer, and with it every window, the first time a Pane was constructed.
     * <p>
     * Duplicates are therefore collapsed rather than treated as an error: a cursor the platform did not provide simply
     * has no entry of its own, and {@link #of(Identifier)} resolves its name to {@link #DEFAULT} - the cursor it had
     * already fallen back to anyway.
     */
    private static final Map<String, CursorType> CURSOR_MAP = Stream
        .of(DEFAULT,
            ARROW,
            TEXT_CURSOR,
            CROSSHAIR,
            HAND,
            HORIZONTAL_RESIZE,
            VERTICAL_RESIZE,
            RESIZE_NWSE,
            RESIZE_NESW,
            RESIZE,
            NOT_ALLOWED)
        .collect(Collectors.toUnmodifiableMap(cursor -> cursor.name, cursor -> cursor, (first, second) -> first));

    public static CursorType of(final Identifier resLoc)
    {
        if ((BlockUI.MOD_ID + "_std").equalsIgnoreCase(resLoc.getNamespace()))
        {
            return SafeError.requireNonNull(CURSOR_MAP.get(resLoc.getPath()), Cursor.DEFAULT, "Invalid built-in cursor: " + resLoc.toString());
        }

        final TextureManager texManager = Minecraft.getInstance().getTextureManager();
        final AbstractTexture texture = texManager.getTexture(resLoc);
        if (!(texture instanceof CursorTexture))
        {
            if (IsOurTexture.isOur(texture))
            {
                LOGGER.warn("Trying to use special BlockUI texture as cursor? Things may not work well: " + resLoc.toString());
            }

            texManager.registerAndLoad(resLoc, new CursorTexture(resLoc));
        }

        return new TexturedCursorType(resLoc);
    }

    public static class TexturedCursorType extends CursorType
    {
        private final Identifier resLoc;

        protected TexturedCursorType(final Identifier resLoc)
        {
            super(BlockUI.MOD_ID + "_tex_cursor:" + resLoc.toString(), -1L);
            this.resLoc = resLoc;
        }

        @Override
        public void select(final Window window)
        {
            final AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(resLoc);

            if (!(texture instanceof final CursorTexture cursorTexture))
            {
                throw new IllegalArgumentException("Did you forget to load CursorTexture (or create CursorType) for: " + resLoc);
            }

            GLFW.glfwSetCursor(window.handle(), cursorTexture.getGlfwCursorAddress());
        }
    }
}
