// Why the 26.3 client does or does not get a window, answered in seconds.
//
// 26.3 opens its window through SDL, and GlBackend.createWindow always asks for an
// sRGB-capable framebuffer. Whether that succeeds depends on the graphics stack, not on
// anything in this repository -- so it is worth answering without starting the client,
// which takes minutes and holds the global Gradle lock. This does exactly what
// GlBackend.createWindow does and nothing else.
//
//   tools/sdl-probe/run.sh                    create a window the way 26.3 does
//   tools/sdl-probe/run.sh nosrgb             the same, minus the sRGB attribute
//   tools/sdl-probe/run.sh glx                count GLX fbconfigs and sRGB-capable ones
//
// See docs/CLIENT-SCREENSHOTS.md §7.

import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLX;
import org.lwjgl.opengl.GLX13;
import org.lwjgl.opengl.GLXARBFramebufferSRGB;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.linux.X11;

import java.nio.IntBuffer;

import static org.lwjgl.sdl.SDLError.SDL_GetError;
import static org.lwjgl.sdl.SDLInit.SDL_INIT_VIDEO;
import static org.lwjgl.sdl.SDLInit.SDL_Init;
import static org.lwjgl.sdl.SDLVideo.*;

public final class SdlProbe {

    public static void main(final String[] args) {
        final String mode = args.length > 0 ? args[0] : "sdl";
        switch (mode) {
            case "glx" -> glx();
            case "nosrgb" -> sdl(false);
            default -> sdl(true);
        }
    }

    /** Replays GlBackend.createWindow attribute for attribute. */
    private static void sdl(final boolean srgb) {
        System.out.println("SDL_Init(VIDEO) = " + SDL_Init(SDL_INIT_VIDEO) + "  " + SDL_GetError());
        System.out.println("video driver   = " + SDL_GetCurrentVideoDriver());
        System.out.println("GL_LoadLibrary = " + SDL_GL_LoadLibrary((String) null) + "  " + SDL_GetError());

        // GlBackend.createWindow, /opt/mc-src-26.3/com/mojang/renderpearl/backend/opengl/GlBackend.java:63-68
        SDL_GL_SetAttribute(17, 3);            // SDL_GL_CONTEXT_MAJOR_VERSION
        SDL_GL_SetAttribute(18, 3);            // SDL_GL_CONTEXT_MINOR_VERSION
        SDL_GL_SetAttribute(20, 1);            // SDL_GL_CONTEXT_PROFILE_MASK = CORE
        SDL_GL_SetAttribute(19, 2);            // SDL_GL_CONTEXT_FLAGS
        if (srgb) {
            SDL_GL_SetAttribute(22, 1);        // SDL_GL_FRAMEBUFFER_SRGB_CAPABLE -- the one that matters
        } else {
            System.out.println("(sRGB attribute 22 deliberately omitted)");
        }

        final long window = SDL_CreateWindow("sdl-probe", 854, 480, 2L);   // 2 = SDL_WINDOW_OPENGL
        if (window == 0L) {
            System.out.println("SDL_CreateWindow FAILED: " + SDL_GetError());
            System.out.println("RESULT=FAIL");
            return;
        }
        final long context = SDL_GL_CreateContext(window);
        if (context == 0L) {
            System.out.println("SDL_GL_CreateContext FAILED: " + SDL_GetError());
            System.out.println("RESULT=FAIL");
            return;
        }
        GL.createCapabilities();
        System.out.println("GL_VERSION     = " + GL11.glGetString(GL11.GL_VERSION));
        System.out.println("GL_RENDERER    = " + GL11.glGetString(GL11.GL_RENDERER));
        System.out.println("RESULT=OK");
    }

    /**
     * Counts GLX fbconfigs and how many of them are sRGB-capable. On the GLX path SDL asks
     * for GLX_FRAMEBUFFER_SRGB_CAPABLE_ARB while choosing a visual, so an sRGB-capable count
     * of zero is exactly why SDL_CreateWindow reports "Couldn't find matching GLX visual".
     */
    private static void glx() {
        final long display = X11.XOpenDisplay((java.nio.ByteBuffer) null);
        if (display == 0L) {
            System.out.println("XOpenDisplay failed -- is DISPLAY set and the X server up?");
            System.out.println("RESULT=FAIL");
            return;
        }
        final int screen = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer major = stack.mallocInt(1);
            final IntBuffer minor = stack.mallocInt(1);
            GLX.glXQueryVersion(display, major, minor);
            System.out.println("GLX version    = " + major.get(0) + "." + minor.get(0));
        }
        final String extensions = GLX13.glXQueryExtensionsString(display, screen);
        System.out.println("advertises GLX_ARB_framebuffer_sRGB = "
            + (extensions != null && extensions.contains("GLX_ARB_framebuffer_sRGB")));

        try (MemoryStack stack = MemoryStack.stackPush()) {
            final PointerBuffer configs = GLX13.glXGetFBConfigs(display, screen);
            final IntBuffer value = stack.mallocInt(1);
            int total = 0, srgb = 0, withVisual = 0, srgbWithVisual = 0;
            for (int i = 0; i < configs.remaining(); i++) {
                final long config = configs.get(i);
                total++;
                final boolean isSrgb = GLX13.glXGetFBConfigAttrib(
                    display, config, GLXARBFramebufferSRGB.GLX_FRAMEBUFFER_SRGB_CAPABLE_ARB, value) == 0
                    && value.get(0) != 0;
                final boolean hasVisual = GLX13.glXGetFBConfigAttrib(
                    display, config, GLX13.GLX_VISUAL_ID, value) == 0 && value.get(0) != 0;
                if (isSrgb) srgb++;
                if (hasVisual) withVisual++;
                if (isSrgb && hasVisual) srgbWithVisual++;
            }
            System.out.println("fbconfigs      = " + total
                + "  sRGB-capable=" + srgb
                + "  with-visual=" + withVisual
                + "  sRGB+visual=" + srgbWithVisual);
            System.out.println(srgbWithVisual > 0
                ? "RESULT=OK (GLX could satisfy SDL here)"
                : "RESULT=FAIL (no sRGB visual -- SDL cannot use GLX here)");
        }
    }
}
