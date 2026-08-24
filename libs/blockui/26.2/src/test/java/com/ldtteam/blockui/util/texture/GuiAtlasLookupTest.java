package com.ldtteam.blockui.util.texture;

import com.ldtteam.blockui.mod.BlockUI;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression tests for the crash "IllegalArgumentException: Invalid atlas id: null" during
 * "ReportedException: Rendering BO screen".
 * <p>
 * A consumer that draws a texture from a namespace which registered no gui atlas used to reach
 * {@code AtlasManager#getAtlasOrThrow(null)}, which in 26.2 throws instead of returning the atlas' missing sprite.
 * That happened inside {@code BOScreen#extractRenderState}, so it killed the client rather than the widget.
 * <p>
 * Note what these tests can and cannot see: there is no running {@link Minecraft} in the test JVM, so
 * {@code Minecraft.getInstance()} is null and any code path that touches the atlas manager would fail here with a
 * {@link NullPointerException}. That is precisely what makes the assertions below meaningful - a clean null return
 * proves the lookup short-circuited before it ever asked for an atlas.
 */
public class GuiAtlasLookupTest
{
    private static final String UNREGISTERED_NAMESPACE = "some_consumer_mod";

    @BeforeEach
    @AfterEach
    public void clearAtlasRegistrations()
    {
        BlockUI.NAMESPACE_TO_ATLAS_MAP.remove(UNREGISTERED_NAMESPACE);
        GuiAtlasLookup.forgetReportedNamespaces();
    }

    @Test
    public void namespaceWithoutAtlasResolvesToNoSpriteInsteadOfThrowing()
    {
        final Identifier texture = Identifier.fromNamespaceAndPath(UNREGISTERED_NAMESPACE, "textures/gui/smiley.png");

        // sanity: if the lookup did reach the atlas manager it could not possibly survive this environment
        assertNull(Minecraft.getInstance(), "test JVM is expected to have no client instance");

        assertNull(assertDoesNotThrow(() -> GuiAtlasLookup.resolveSprite(texture)),
            "a namespace with no registered gui atlas must resolve to 'no sprite', not to an exception");
    }

    @Test
    public void repeatedLookupsOfTheSameNamespaceStayQuietAndKeepAnswering()
    {
        // the log line for an atlas-less namespace is emitted once, but the answer has to stay the same every frame
        for (int i = 0; i < 8; i++)
        {
            final Identifier texture = Identifier.fromNamespaceAndPath(UNREGISTERED_NAMESPACE, "textures/gui/icon_" + i + ".png");
            assertNull(assertDoesNotThrow(() -> GuiAtlasLookup.resolveSprite(texture)));
        }
    }

    @Test
    public void aRegisteredNamespaceStillGoesToTheAtlas()
    {
        // guard against "fixing" the crash by simply never consulting an atlas: with a mapping present the lookup has
        // to reach the atlas manager, which in this JVM does not exist - so it must fail here rather than quietly
        // report "no sprite" the way the unregistered namespace above does
        BlockUI.NAMESPACE_TO_ATLAS_MAP.put(UNREGISTERED_NAMESPACE, Identifier.fromNamespaceAndPath(UNREGISTERED_NAMESPACE, "gui"));

        assertThrows(Throwable.class,
            () -> GuiAtlasLookup.resolveSprite(Identifier.fromNamespaceAndPath(UNREGISTERED_NAMESPACE, "widget/button")),
            "with an atlas registered the lookup is expected to go through Minecraft#getAtlasManager, which this JVM lacks");
    }
}
