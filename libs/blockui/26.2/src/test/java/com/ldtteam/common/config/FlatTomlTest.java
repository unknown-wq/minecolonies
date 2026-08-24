package com.ldtteam.common.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the TOML subset {@link ConfigStore} needs, including the shapes NightConfig used to write into
 * {@code config/<modid>-<type>.toml} on NeoForge - those files have to keep loading after the port.
 */
class FlatTomlTest
{
    private static Map<String, Object> parseClean(final String text)
    {
        final List<String> problems = new ArrayList<>();
        final Map<String, Object> parsed = FlatToml.parse(text, problems);
        assertTrue(problems.isEmpty(), () -> "unexpected parse problems: " + problems);
        return parsed;
    }

    @Test
    void readsTheLayoutNightConfigWrote()
    {
        final Map<String, Object> parsed = parseClean("""
            #A root comment
            rootFlag = true

            \t[gui]
            \t\t#Scale of the interface.
            \t\tscale = 3
            \t\tname = "hello \\"world\\""
            \t\tratio = 0.75

            \t[gui.advanced]
            \t\tblacklist = ["a", "b", "c"]
            """);

        assertEquals(Boolean.TRUE, parsed.get("rootFlag"));
        assertEquals(3L, parsed.get("gui.scale"));
        assertEquals("hello \"world\"", parsed.get("gui.name"));
        assertEquals(0.75d, parsed.get("gui.ratio"));
        assertEquals(List.of("a", "b", "c"), parsed.get("gui.advanced.blacklist"));
    }

    @Test
    void readsNumbersBooleansAndSpecialFloats()
    {
        final Map<String, Object> parsed = parseClean("""
            a = -12
            b = 1_000_000
            c = 0xFF
            d = 0b1010
            e = 1.5e3
            f = inf
            g = -inf
            h = false
            """);

        assertEquals(-12L, parsed.get("a"));
        assertEquals(1000000L, parsed.get("b"));
        assertEquals(255L, parsed.get("c"));
        assertEquals(10L, parsed.get("d"));
        assertEquals(1500.0d, parsed.get("e"));
        assertEquals(Double.POSITIVE_INFINITY, parsed.get("f"));
        assertEquals(Double.NEGATIVE_INFINITY, parsed.get("g"));
        assertEquals(Boolean.FALSE, parsed.get("h"));
    }

    @Test
    void readsArraysSpreadOverSeveralLinesAndInlineComments()
    {
        final Map<String, Object> parsed = parseClean("""
            [section]
            list = [
                "one",   # first
                "two",
                "three",
            ]
            after = 1 # trailing comment
            """);

        assertEquals(List.of("one", "two", "three"), parsed.get("section.list"));
        assertEquals(1L, parsed.get("section.after"));
    }

    @Test
    void readsQuotedKeysAndLiteralStrings()
    {
        final Map<String, Object> parsed = parseClean("""
            ["odd section"]
            "a key" = 'literal \\ string'
            """);

        assertEquals("literal \\ string", parsed.get("odd section.a key"));
    }

    @Test
    void aBadLineCostsOneValueNotTheFile()
    {
        final List<String> problems = new ArrayList<>();
        final Map<String, Object> parsed = FlatToml.parse("""
            good1 = 1
            this line is nonsense
            broken = "unterminated
            good2 = 2
            """, problems);

        assertEquals(1L, parsed.get("good1"));
        assertEquals(2L, parsed.get("good2"));
        assertFalse(problems.isEmpty());
    }

    @Test
    void unsupportedConstructsAreReportedAndSkipped()
    {
        final List<String> problems = new ArrayList<>();
        final Map<String, Object> parsed = FlatToml.parse("""
            table = { a = 1 }
            fine = 7
            """, problems);

        assertEquals(7L, parsed.get("fine"));
        assertEquals(1, problems.size());
        assertTrue(problems.get(0).contains("inline tables"), problems.get(0));
    }

    @Test
    void formatsEveryTypeBackIntoValidToml()
    {
        assertEquals("true", FlatToml.format(Boolean.TRUE));
        assertEquals("42", FlatToml.format(42));
        assertEquals("42", FlatToml.format(42L));
        assertEquals("0.5", FlatToml.format(0.5d));
        assertEquals("inf", FlatToml.format(Double.POSITIVE_INFINITY));
        assertEquals("nan", FlatToml.format(Double.NaN));
        assertEquals("\"a\\\"b\\n\"", FlatToml.format("a\"b\n"));
        assertEquals("[\"x\", 1]", FlatToml.format(List.of("x", 1)));
        assertEquals("\"SECOND\"", FlatToml.format(Sample.SECOND));
    }

    @Test
    void formatAndParseRoundTrip()
    {
        final String document = "k = " + FlatToml.format(List.of("a \"quoted\" value", "b\tc")) + "\n"
            + "n = " + FlatToml.format(1.0E20d) + "\n";

        final Map<String, Object> parsed = parseClean(document);
        assertEquals(List.of("a \"quoted\" value", "b\tc"), parsed.get("k"));
        assertInstanceOf(Double.class, parsed.get("n"));
        assertEquals(1.0E20d, parsed.get("n"));
    }

    enum Sample
    {
        FIRST,
        SECOND
    }
}
