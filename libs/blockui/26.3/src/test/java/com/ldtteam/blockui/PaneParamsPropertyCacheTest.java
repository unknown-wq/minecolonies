package com.ldtteam.blockui;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link PaneParams#getProperty} caches parsed attribute values, and the cache has to hand a value back only to an
 * accessor of the kind that produced it.
 * <p>
 * Keying it by attribute name alone made every window with an {@code <image source="...">} in it unopenable: the
 * {@code Image} constructor reads {@code source} through {@link PaneParams#getResource} for the texture and through
 * {@link PaneParams#getString} for the diagnostic message, the second read found the first read's {@link Identifier}
 * under the same key, and the unchecked {@code (T)} inside {@code getProperty} erases to nothing - so the cast landed
 * on the caller's side as {@code ClassCastException: Identifier cannot be cast to String} and took the whole xml parse
 * down with it.
 * <p>
 * {@code PaneParams} has no {@code Pane} on it and touches no client state, so unlike most of this library it can be
 * exercised directly: a DOM node from the JDK parser and an {@link Identifier} are all it needs.
 */
public class PaneParamsPropertyCacheTest
{
    private static final String SOURCE = "minecolonies:textures/gui/builderhut/blockui.png";

    private static PaneParams params(final String attributes)
    {
        try
        {
            final Node node = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new InputSource(new StringReader("<image " + attributes + "/>")))
                .getDocumentElement();
            return new PaneParams(node, Identifier.parse("blockui:gui/test.xml"));
        }
        catch (final Exception e)
        {
            throw new AssertionError("could not build the test node", e);
        }
    }

    @Test
    public void aResourceReadDoesNotPoisonAStringRead()
    {
        // exactly the order Image's constructor uses
        final PaneParams params = params("source=\"" + SOURCE + "\"");

        assertEquals(Identifier.parse(SOURCE), params.getResource("source"));
        assertEquals(SOURCE, params.getString("source", ""));
    }

    @Test
    public void aStringReadDoesNotPoisonAResourceRead()
    {
        final PaneParams params = params("source=\"" + SOURCE + "\"");

        assertEquals(SOURCE, params.getString("source", ""));
        assertEquals(Identifier.parse(SOURCE), params.getResource("source"));
    }

    @Test
    public void aNumberReadDoesNotPoisonAStringRead()
    {
        // nothing about this is specific to source/Identifier - any two accessors on one attribute collided
        final PaneParams params = params("size=\"42\"");

        assertEquals(42, params.getInteger("size", 0));
        assertEquals("42", params.getString("size", ""));
        assertEquals(42.0, params.getDouble("size", 0.0));
        assertEquals(42, params.getInteger("size", 0));
    }

    @Test
    public void anEnumReadDoesNotPoisonAStringRead()
    {
        // Parsers.ENUM and Parsers.SCALED are factories: they hand out a fresh function per call, so the cache may not
        // key on the parser instance
        final PaneParams params = params("align=\"TOP_LEFT\"");

        assertSame(Alignment.TOP_LEFT, params.getEnum("align", Alignment.class, Alignment.MIDDLE));
        assertEquals("TOP_LEFT", params.getString("align", ""));
        assertSame(Alignment.TOP_LEFT, params.getEnum("align", Alignment.class, Alignment.MIDDLE));
    }

    @Test
    public void repeatedReadsThroughOneAccessorStillHitTheCache()
    {
        // the cache is why a re-opened window does not re-parse its whole xml; fixing the type confusion may not turn
        // it off. Identifier.parse allocates, so a cache hit is observable as object identity.
        final PaneParams params = params("source=\"" + SOURCE + "\"");

        assertSame(params.getResource("source"), params.getResource("source"));
    }

    @Test
    public void anAbsentAttributeFallsBackToTheDefaultForEveryAccessor()
    {
        // the "missing" entry is cached too (as null) and used to be shared between accessors just the same
        final PaneParams params = params("id=\"noSource\"");

        assertNull(params.getResource("source"));
        assertEquals("fallback", params.getString("source", "fallback"));
        assertEquals(7, params.getInteger("source", 7));
    }
}
