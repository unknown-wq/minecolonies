package com.ldtteam.blockui.util.texture;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.blockui.util.resloc.OutOfJarResourceLocation;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.NativeImage.Format;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.renderer.texture.ReloadableTexture;
import net.minecraft.client.renderer.texture.TextureContents;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDL_Surface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

/**
 * Used for textured cursors.
 *
 * @see Pane#setCursor(CursorType)
 */
public class CursorTexture extends ReloadableTexture
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CursorTexture.class);

    private CursorMetadataSection cursorMetadata = CursorMetadataSection.EMPTY;
    private long sdlCursorAddress = 0;

    public CursorTexture(final Identifier resLoc)
    {
        super(resLoc);
    }

    @Override
    public TextureContents loadContents(final ResourceManager resourceManager) throws IOException
    {
        final Resource resource = OutOfJarResourceLocation.getResourceHandle(resourceId(), resourceManager);
        final NativeImage nativeImage;
        try (var is = resource.open())
        {
            nativeImage = NativeImage.read(is);
        }
        if (nativeImage.format() != Format.RGBA)
        {
            LOGGER.error("Cannot load texture for cursor as it is not in RGBA format, resource location: " + resourceId());

            nativeImage.close();
            return TextureContents.createMissing();
        }

        this.cursorMetadata = resource.metadata().getSection(CursorMetadataSection.TYPE).orElse(CursorMetadataSection.EMPTY);

        return new TextureContents(nativeImage, resource.metadata().getSection(TextureMetadataSection.TYPE).orElse(null));
    }

    @Override
    public void apply(final TextureContents contents)
    {
        try (NativeImage nativeImage = contents.image())
        {
            RenderSystem.assertOnRenderThread();

            this.close();

            // 26.3: GLFW is gone, cursors are SDL. GLFWImage has no counterpart; the equivalent is
            // an SDL_Surface built straight over the NativeImage pixels, exactly the way vanilla
            // builds the window icon (/opt/mc-src-26.3/com/mojang/blaze3d/platform/Window.java:129-132).
            // 376840196 == SDLPixels.SDL_PIXELFORMAT_ABGR8888, the same constant vanilla inlines there.
            final SDL_Surface surface = SDLSurface.SDL_CreateSurfaceFrom(nativeImage.getWidth(),
                nativeImage.getHeight(),
                SDLPixels.SDL_PIXELFORMAT_ABGR8888,
                nativeImage.getPixelBytes(),
                nativeImage.getWidth() * 4); // pitch

            if (surface != null)
            {
                sdlCursorAddress = SDLMouse.SDL_CreateColorCursor(surface, cursorMetadata.hotspotX, cursorMetadata.hotspotY);
                SDLSurface.SDL_DestroySurface(surface);
            }

            if (sdlCursorAddress == 0)
            {
                LOGGER.error("Cannot create textured cursor for resource location: " + resourceId());
            }
        }
    }

    @Override
    protected void doLoad(final NativeImage image)
    {
        // Noop
    }

    @Override
    public void close()
    {
        if (sdlCursorAddress != 0)
        {
            RenderSystem.assertOnRenderThread();
            SDLMouse.SDL_DestroyCursor(sdlCursorAddress);
            sdlCursorAddress = 0;
        }
        super.close();
    }

    public long getSdlCursorAddress()
    {
        return sdlCursorAddress;
    }

    public static record CursorMetadataSection(int hotspotX, int hotspotY)
    {
        public static final CursorMetadataSection EMPTY = new CursorMetadataSection(0, 0);

        public static final Codec<CursorMetadataSection> CODEC = RecordCodecBuilder.create(builder -> builder
            .group(Codec.INT.optionalFieldOf("hotspot.x", 0).forGetter(CursorMetadataSection::hotspotX),
                Codec.INT.optionalFieldOf("hotspot.y", 0).forGetter(CursorMetadataSection::hotspotY))
            .apply(builder, CursorMetadataSection::new));

        public static final MetadataSectionType<CursorMetadataSection> TYPE =
            new MetadataSectionType<>("ldtteam." + BlockUI.MOD_ID + ".cursor", CODEC);
    }
}
