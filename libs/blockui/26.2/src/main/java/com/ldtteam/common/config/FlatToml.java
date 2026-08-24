package com.ldtteam.common.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal TOML reader/writer for the flat {@code [category]} + {@code key = value} subset that
 * {@link AbstractConfiguration} can actually produce.
 * <p>
 * This exists because NightConfig - the TOML implementation NeoForge's {@code ModConfigSpec} used - is not on the
 * Fabric classpath (neither fabric-loader 0.19.3 nor fabric-api 0.154.2 nor Minecraft itself ship it), and pulling
 * a new runtime dependency into a library mod that MineColonies and Structurize both depend on is a far bigger
 * change than the ~300 lines below. The format is kept rather than swapped for JSON so that a
 * {@code config/<modid>-client.toml} written by the NeoForge build stays readable by this one: an existing
 * installation keeps its settings across the port.
 * <p>
 * What is supported, i.e. everything {@link ConfigStore} can emit plus everything NightConfig emitted for these
 * specs: comments, table headers (including dotted and quoted names), bare and quoted keys, booleans, integers
 * (decimal, {@code 0x}/{@code 0o}/{@code 0b}, {@code _} separators), floats (including {@code inf}/{@code nan}),
 * basic and literal strings, and arrays - inline or spread over several lines.
 * <p>
 * What is not supported: inline tables, arrays of tables, multi-line strings and datetimes. None of them can be
 * produced by a {@code defineXxx}. A line that cannot be parsed is skipped with a warning rather than aborting
 * the whole file, so one corrupt entry costs one value, not the entire configuration.
 */
final class FlatToml
{
    private FlatToml()
    {
        throw new IllegalStateException("Tried to initialize: FlatToml but this is a Utility class.");
    }

    /**
     * Parses a whole TOML document.
     *
     * @param  text     document contents
     * @param  problems every recoverable parse problem is appended here, for the caller to log
     * @return          dot-path -&gt; value, in file order; never null, possibly empty
     */
    static Map<String, Object> parse(final String text, final List<String> problems)
    {
        final Map<String, Object> result = new LinkedHashMap<>();
        final Cursor cursor = new Cursor(text);
        String section = "";

        while (true)
        {
            cursor.skipIgnorable();
            if (cursor.eof())
            {
                break;
            }

            final int entryStart = cursor.index;
            try
            {
                if (cursor.peek() == '[')
                {
                    if (cursor.index + 1 < text.length() && text.charAt(cursor.index + 1) == '[')
                    {
                        throw new TomlException("arrays of tables are not supported");
                    }

                    cursor.index++;
                    section = parseKey(cursor);
                    cursor.skipInlineSpace();
                    cursor.expect(']');
                }
                else
                {
                    final String key = parseKey(cursor);
                    cursor.skipInlineSpace();
                    cursor.expect('=');
                    final Object value = parseValue(cursor);
                    result.put(section.isEmpty() ? key : section + "." + key, value);
                }
            }
            catch (final TomlException e)
            {
                problems.add("line " + cursor.lineOf(entryStart) + ": " + e.getMessage());
                // recover on the next line rather than dropping the rest of the file; the cursor has never moved
                // backwards, so this always makes progress and the loop cannot spin
                cursor.skipToNextLine();
            }
        }

        return result;
    }

    /**
     * Renders a single value in TOML syntax. Never fails: a type this class does not know is written as a string,
     * which round-trips back into the default on the next load instead of corrupting the file.
     */
    static String format(final Object value)
    {
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
            || value instanceof Short || value instanceof Byte)
        {
            return value.toString();
        }
        if (value instanceof final Double d)
        {
            return formatFloat(d);
        }
        if (value instanceof final Float f)
        {
            return formatFloat(f.doubleValue());
        }
        if (value instanceof final Enum<?> e)
        {
            return quote(e.name());
        }
        if (value instanceof final Iterable<?> iterable)
        {
            final StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (final Object element : iterable)
            {
                if (!first)
                {
                    sb.append(", ");
                }
                first = false;
                sb.append(element == null ? "\"\"" : format(element));
            }
            return sb.append(']').toString();
        }
        return quote(String.valueOf(value));
    }

    private static String formatFloat(final double d)
    {
        if (Double.isNaN(d))
        {
            return "nan";
        }
        if (Double.isInfinite(d))
        {
            return d > 0 ? "inf" : "-inf";
        }
        // Double#toString always yields either a '.' or an exponent, both of which are valid TOML floats
        return Double.toString(d);
    }

    /**
     * Quotes and escapes a TOML basic string.
     */
    static String quote(final String raw)
    {
        final StringBuilder sb = new StringBuilder(raw.length() + 2).append('"');
        for (int i = 0; i < raw.length(); i++)
        {
            final char c = raw.charAt(i);
            switch (c)
            {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x7F)
                    {
                        sb.append("\\u").append(String.format(Locale.ROOT, "%04X", (int) c));
                    }
                    else
                    {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    // ---------------------------------------------------------------------------------------------------------
    // parsing internals
    // ---------------------------------------------------------------------------------------------------------

    /**
     * A possibly dotted key or table name. Segments may be bare or quoted; the result is always the plain dotted
     * form, which is exactly what {@link ConfigValue#getPath()} produces.
     */
    private static String parseKey(final Cursor c)
    {
        final StringBuilder sb = new StringBuilder();
        while (true)
        {
            c.skipInlineSpace();
            if (c.eof())
            {
                throw new TomlException("unterminated key");
            }

            final char ch = c.peek();
            if (ch == '"')
            {
                sb.append(parseBasicString(c));
            }
            else if (ch == '\'')
            {
                sb.append(parseLiteralString(c));
            }
            else
            {
                final int start = c.index;
                while (!c.eof() && isBareKeyChar(c.peek()))
                {
                    c.index++;
                }
                if (c.index == start)
                {
                    throw new TomlException("expected a key but found '" + ch + "'");
                }
                sb.append(c.text, start, c.index);
            }

            c.skipInlineSpace();
            if (!c.eof() && c.peek() == '.')
            {
                c.index++;
                sb.append('.');
                continue;
            }
            return sb.toString();
        }
    }

    private static boolean isBareKeyChar(final char c)
    {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private static Object parseValue(final Cursor c)
    {
        c.skipInlineSpace();
        if (c.eof())
        {
            throw new TomlException("missing value");
        }

        final char ch = c.peek();
        if (ch == '"')
        {
            if (c.startsWith("\"\"\""))
            {
                throw new TomlException("multi-line strings are not supported");
            }
            return parseBasicString(c);
        }
        if (ch == '\'')
        {
            if (c.startsWith("'''"))
            {
                throw new TomlException("multi-line strings are not supported");
            }
            return parseLiteralString(c);
        }
        if (ch == '[')
        {
            return parseArray(c);
        }
        if (ch == '{')
        {
            throw new TomlException("inline tables are not supported");
        }
        return parseBare(c);
    }

    private static List<Object> parseArray(final Cursor c)
    {
        c.index++; // '['
        final List<Object> list = new ArrayList<>();
        while (true)
        {
            c.skipIgnorable();
            if (c.eof())
            {
                throw new TomlException("unterminated array");
            }
            if (c.peek() == ']')
            {
                c.index++;
                return list;
            }

            list.add(parseValue(c));

            c.skipIgnorable();
            if (c.eof())
            {
                throw new TomlException("unterminated array");
            }
            if (c.peek() == ',')
            {
                c.index++;
            }
            else if (c.peek() != ']')
            {
                throw new TomlException("expected ',' or ']' in array but found '" + c.peek() + "'");
            }
        }
    }

    private static String parseBasicString(final Cursor c)
    {
        c.index++; // opening quote
        final StringBuilder sb = new StringBuilder();
        while (true)
        {
            if (c.eof())
            {
                throw new TomlException("unterminated string");
            }
            final char ch = c.peek();
            if (ch == '\n')
            {
                // deliberately not consumed: the recovery in parse() has to land on the *next* line, not the
                // one after it
                throw new TomlException("unterminated string");
            }
            c.index++;
            if (ch == '"')
            {
                return sb.toString();
            }
            if (ch != '\\')
            {
                sb.append(ch);
                continue;
            }

            if (c.eof())
            {
                throw new TomlException("unterminated escape sequence");
            }
            final char esc = c.text.charAt(c.index++);
            switch (esc)
            {
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case 'u' -> sb.append(readCodePoint(c, 4));
                case 'U' -> sb.append(readCodePoint(c, 8));
                default -> throw new TomlException("unknown escape sequence '\\" + esc + "'");
            }
        }
    }

    private static String readCodePoint(final Cursor c, final int digits)
    {
        if (c.index + digits > c.text.length())
        {
            throw new TomlException("truncated unicode escape");
        }
        final String hex = c.text.substring(c.index, c.index + digits);
        c.index += digits;
        try
        {
            return Character.toString(Integer.parseInt(hex, 16));
        }
        catch (final IllegalArgumentException e)
        {
            throw new TomlException("invalid unicode escape '" + hex + "'");
        }
    }

    private static String parseLiteralString(final Cursor c)
    {
        c.index++; // opening quote
        final int start = c.index;
        while (!c.eof() && c.peek() != '\'' && c.peek() != '\n')
        {
            c.index++;
        }
        if (c.eof() || c.peek() != '\'')
        {
            throw new TomlException("unterminated literal string");
        }
        final String value = c.text.substring(start, c.index);
        c.index++;
        return value;
    }

    /**
     * A bare token: boolean, integer or float. Anything else is handed back verbatim as a string - the caller
     * coerces against the declared type anyway, and being lenient here means an unquoted string in a
     * hand-edited file still works.
     */
    private static Object parseBare(final Cursor c)
    {
        final int start = c.index;
        while (!c.eof())
        {
            final char ch = c.peek();
            if (ch == ',' || ch == ']' || ch == '}' || ch == '#' || ch == '\n' || ch == '\r')
            {
                break;
            }
            c.index++;
        }

        final String token = c.text.substring(start, c.index).trim();
        if (token.isEmpty())
        {
            throw new TomlException("missing value");
        }

        switch (token)
        {
            case "true":
                return Boolean.TRUE;
            case "false":
                return Boolean.FALSE;
            case "inf", "+inf":
                return Double.POSITIVE_INFINITY;
            case "-inf":
                return Double.NEGATIVE_INFINITY;
            case "nan", "+nan", "-nan":
                return Double.NaN;
            default:
                break;
        }

        final String cleaned = token.replace("_", "");
        try
        {
            if (cleaned.length() > 2 && (cleaned.charAt(0) == '0'))
            {
                final char radixMarker = cleaned.charAt(1);
                if (radixMarker == 'x' || radixMarker == 'X')
                {
                    return Long.parseLong(cleaned.substring(2), 16);
                }
                if (radixMarker == 'o' || radixMarker == 'O')
                {
                    return Long.parseLong(cleaned.substring(2), 8);
                }
                if (radixMarker == 'b' || radixMarker == 'B')
                {
                    return Long.parseLong(cleaned.substring(2), 2);
                }
            }

            if (cleaned.matches("[+-]?[0-9]+"))
            {
                return Long.parseLong(cleaned);
            }
            if (cleaned.matches("[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))
            {
                return Double.parseDouble(cleaned);
            }
        }
        catch (final NumberFormatException e)
        {
            // fall through to the lenient string
        }

        return token;
    }

    /**
     * Thrown and immediately caught inside {@link #parse}: one bad entry must not cost the whole file.
     */
    private static final class TomlException extends RuntimeException
    {
        private static final long serialVersionUID = 1L;

        private TomlException(final String message)
        {
            super(message, null, false, false);
        }
    }

    private static final class Cursor
    {
        private final String text;
        private int index;

        private Cursor(final String text)
        {
            this.text = text;
        }

        private boolean eof()
        {
            return index >= text.length();
        }

        private char peek()
        {
            return text.charAt(index);
        }

        private boolean startsWith(final String prefix)
        {
            return text.startsWith(prefix, index);
        }

        private void expect(final char expected)
        {
            if (eof() || peek() != expected)
            {
                throw new TomlException("expected '" + expected + "'");
            }
            index++;
        }

        /**
         * Spaces and tabs only - stops at a line break.
         */
        private void skipInlineSpace()
        {
            while (!eof() && (peek() == ' ' || peek() == '\t'))
            {
                index++;
            }
        }

        /**
         * Any whitespace including line breaks, plus whole comments.
         */
        private void skipIgnorable()
        {
            while (!eof())
            {
                final char c = peek();
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r')
                {
                    index++;
                }
                else if (c == '#')
                {
                    skipToNextLine();
                }
                else
                {
                    return;
                }
            }
        }

        private void skipToNextLine()
        {
            while (!eof() && peek() != '\n')
            {
                index++;
            }
            if (!eof())
            {
                index++;
            }
        }

        private int lineOf(final int position)
        {
            int line = 1;
            for (int i = 0; i < position && i < text.length(); i++)
            {
                if (text.charAt(i) == '\n')
                {
                    line++;
                }
            }
            return line;
        }
    }
}
