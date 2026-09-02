package com.ldtteam.blockui.util.texture;

import com.ldtteam.blockui.mod.BlockUI;
import com.ldtteam.blockui.mod.Log;
import com.ldtteam.blockui.util.SafeError;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves a texture into the gui atlas sprite registered for its namespace, or decides that it has none.
 * <p>
 * <b>This class never throws.</b> It sits on the render path: everything in the {@code Image}/{@code ButtonImage}/
 * {@code CheckBox} family funnels through {@code Image#resolveBlit} while {@code BOScreen} is extracting its render
 * state, so an escaping exception is not a broken widget, it is
 * {@code ReportedException: Rendering BO screen} and a client on the desktop. Every failure mode below therefore ends
 * in "no sprite", which the caller answers by blitting the texture as a stand-alone file - and if that file is missing
 * too, vanilla hands out the missing-texture image. A missing texture must be able to look wrong; it must not be able
 * to stop a frame.
 * <p>
 * <b>Where the throw comes from.</b> Vanilla {@code AtlasManager#getAtlasOrThrow} answers an id it does not know by
 * throwing {@code IllegalArgumentException: Invalid atlas id: <id>} rather than by returning anything blittable
 * ({@code net/minecraft/client/resources/model/sprite/AtlasManager.java:68} in the 26.2 sources). A namespace that
 * registered no gui atlas is absent from {@link BlockUI#NAMESPACE_TO_ATLAS_MAP}, so the map answers {@code null} - and
 * {@code null} is exactly such an unknown id. The two compose into a guaranteed crash for every texture whose owning
 * mod did not register an atlas.
 * <p>
 * <b>What changed, precisely.</b> Not the 26.1.2 to 26.2 step: the pre-port branch holds this lookup verbatim, so the
 * port did not introduce it. It arrived one step earlier, with LDTTeam's own 1.21.1 to 26.x rewrite, which dropped
 * BlockUI's private {@code AtlasManager} - the one standing on vanilla {@code TextureAtlasHolder} /
 * {@code GuiSpriteManager}, neither of which exists in 26.2 - in favour of the vanilla atlas manager plus a
 * namespace-to-atlas-id map BlockUI now has to keep correct itself. That is what turned "this texture is not in an
 * atlas" from an ordinary value into an exception, and what created a per-namespace id that can be missing at all.
 * Consumers coming from 1.21.1 meet it here for the first time, which is why it reads as a 26.2 regression.
 */
public final class GuiAtlasLookup
{
    /**
     * Namespaces already reported as having no gui atlas, so the debug line below stays one line per namespace instead
     * of one per texture per frame.
     */
    private static final Set<String> ATLASLESS_NAMESPACES = ConcurrentHashMap.newKeySet();

    private GuiAtlasLookup()
    {
        // utility class
    }

    /**
     * @param resLoc texture resource location, must not be null
     * @return the gui atlas sprite for this texture, or null when it has none - because its namespace registered no
     *         atlas, because the atlas does not contain it, or because the atlas could not be reached at all
     */
    @Nullable
    public static TextureAtlasSprite resolveSprite(final Identifier resLoc)
    {
        final Identifier atlasId = BlockUI.NAMESPACE_TO_ATLAS_MAP.get(resLoc.getNamespace());
        if (atlasId == null)
        {
            // No gui atlas registered for this namespace. Not an error in itself - a mod is free to ship plain png
            // files and stitch nothing - so this is a silent fallback to the stand-alone texture path. Reported once
            // per namespace at debug level because the other reading of it, "the mod forgot to register its atlas",
            // is otherwise completely invisible: its sprites simply render as missingno.
            if (ATLASLESS_NAMESPACES.add(resLoc.getNamespace()))
            {
                Log.getLogger()
                    .debug("No gui atlas is registered for namespace '{}' (first seen while resolving '{}'). Textures from it are"
                        + " blitted as stand-alone files; if they are meant to be sprites, the owning mod has to put the namespace"
                        + " into BlockUI.NAMESPACE_TO_ATLAS_MAP.",
                        resLoc.getNamespace(),
                        resLoc);
            }
            return null;
        }

        try
        {
            final TextureAtlas guiAtlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(atlasId);
            final TextureAtlasSprite atlasSprite = guiAtlas.getSprite(resLoc);

            // unless we sprited missing texture pass to sprite blit (intentional object equality)
            return atlasSprite == guiAtlas.missingSprite() ? null : atlasSprite;
        }
        catch (final RuntimeException e)
        {
            // The namespace claims an atlas the atlas manager does not have, or one that has not been stitched yet
            // (TextureAtlas#missingSprite throws "Atlas not initialized" until the first upload). That is a genuine
            // misconfiguration, so it goes through SafeError - which throws in a dev workspace and, in production,
            // logs it once and leaves this frame to the stand-alone texture path. The message names the texture and
            // not just the atlas, so the broken widget can actually be found.
            SafeError.throwInDev(new IllegalStateException("Cannot resolve texture '" + resLoc + "' in gui atlas '" + atlasId
                + "' registered for namespace '" + resLoc.getNamespace() + "'", e));
            return null;
        }
    }

    /**
     * Forgets which namespaces were reported as atlas-less. Called on client resource reload, where both atlas
     * registrations and texture contents may have changed.
     */
    public static void forgetReportedNamespaces()
    {
        ATLASLESS_NAMESPACES.clear();
    }
}
