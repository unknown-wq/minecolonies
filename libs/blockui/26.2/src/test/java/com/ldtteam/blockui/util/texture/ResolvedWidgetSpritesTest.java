package com.ldtteam.blockui.util.texture;

import com.ldtteam.blockui.UiRenderMacros.ResolvedBlit;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code ButtonImage#drawSelf} validates its enabled texture through {@code SafeError}, which in production logs the
 * problem and carries on by design. The line right below it then resolved the very same texture - and used to do so
 * through a hard {@code Objects.requireNonNull}, throwing exactly the case that had just been declared survivable, on
 * the render path, where it comes out as {@code ReportedException: Rendering BO screen}.
 * <p>
 * Real resolvers ({@code Image::resolveBlit}) answer null with the missing-texture blit, so tolerating null here loses
 * nothing. The resolver below mimics that contract; nothing in this test needs a running client.
 */
public class ResolvedWidgetSpritesTest
{
    private static final ResolvedBlit MISSING = (ps, x, y, w, h, c) -> {};

    private final List<Identifier> resolveCalls = new ArrayList<>();

    /** Stands in for {@code Image::resolveBlit}: null is not an error, it is the missing texture. */
    private ResolvedBlit resolve(final Identifier resLoc)
    {
        resolveCalls.add(resLoc);
        return resLoc == null ? MISSING : (ps, x, y, w, h, c) -> {};
    }

    @Test
    public void allSpritesMissingIsResolvableAndNotFatal()
    {
        final ResolvedWidgetSprites resolved =
            assertDoesNotThrow(() -> ResolvedWidgetSprites.fromUnresolved(new WidgetSprites(null, null, null, null), this::resolve));

        assertSame(MISSING, resolved.enabled());
        assertSame(MISSING, resolved.disabled());
        assertSame(MISSING, resolved.enabledFocused());
        assertSame(MISSING, resolved.disabledFocused());

        // null is resolved once and reused for every state
        assertEquals(1, resolveCalls.size());
    }

    @Test
    public void onlyTheEnabledSpriteMissingStillYieldsAFullSet()
    {
        final Identifier present = Identifier.fromNamespaceAndPath("blockui", "widget/button");
        final ResolvedWidgetSprites resolved =
            assertDoesNotThrow(() -> ResolvedWidgetSprites.fromUnresolved(new WidgetSprites(null, present, present, present), this::resolve));

        assertSame(MISSING, resolved.enabled());
        assertNotNull(resolved.disabled());
        assertSame(resolved.disabled(), resolved.enabledFocused());
        assertSame(resolved.disabled(), resolved.disabledFocused());
    }

    @Test
    public void everyStateIsStillDrawableAfterResolvingNothing()
    {
        final ResolvedWidgetSprites resolved =
            ResolvedWidgetSprites.fromUnresolved(new WidgetSprites(null, null, null, null), this::resolve);

        for (final boolean isEnabled : new boolean[] {true, false})
        {
            for (final boolean isFocused : new boolean[] {true, false})
            {
                assertNotNull(assertDoesNotThrow(() -> resolved.getAndPrepare(isEnabled, isFocused)),
                    "a button with no textures at all must still hand out something to blit");
            }
        }
    }
}
