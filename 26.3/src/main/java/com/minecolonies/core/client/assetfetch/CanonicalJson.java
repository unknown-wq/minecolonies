package com.minecolonies.core.client.assetfetch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Reads and writes JSON in the one byte form the asset bundle is allowed to produce.
 *
 * <p>A patched JSON document has no natural byte form, so {@code assetfetch/transforms.json} pins one in its
 * {@code canonicalJson} block and everything that hashes a patched file has to agree with it exactly — the
 * generator that built {@code manifest.json}, and this class, which writes the files the manifest describes.
 * One byte of drift and every hash of every patched file misses.</p>
 *
 * <p>The pinned form is: UTF-8, two-space indent, {@code ": "} between key and value, {@code ","} between
 * items, keys in document order (never sorted), non-ASCII emitted literally, one trailing newline, and
 * <b>numbers re-emitted from their source literal</b>.</p>
 *
 * <p>That last clause is the trap. A reader that parses JSON numbers into {@code double} turns {@code 16}
 * into {@code 16.0} and {@code 4.52} into whatever the platform's shortest round-trip happens to be. Gson's
 * {@code JsonParser} does not do that: it stores every number as a {@code LazilyParsedNumber} holding the
 * original text, and {@link JsonPrimitive#getAsString()} hands that text back untouched. This class relies on
 * that and never converts a number to a Java numeric type.</p>
 *
 * <p>The reference implementation is Python's {@code json.dumps(doc, indent=2, ensure_ascii=False,
 * separators=(",", ": "))} in {@code 26.2/tools/assetfetch/gen_bundle.py}; the escaping below mirrors
 * CPython's {@code ensure_ascii=False} table (only {@code "}, {@code \}, the five short escapes and
 * {@code U+0000}–{@code U+001F} are escaped, the last as lower-case {@code \}{@code u00xx}).</p>
 */
public final class CanonicalJson
{
    /**
     * Spaces per indent level, per the {@code canonicalJson} block.
     */
    private static final int INDENT = 2;

    /**
     * Private constructor to hide the public one.
     */
    private CanonicalJson()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Parses a JSON document, preserving member order and number literals.
     *
     * @param file the file to read.
     * @return the parsed document.
     * @throws IOException if the file cannot be read or is not JSON.
     */
    public static JsonElement parse(final Path file) throws IOException
    {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8))
        {
            return JsonParser.parseReader(reader);
        }
        catch (final RuntimeException e)
        {
            throw new IOException("Not valid JSON: " + file, e);
        }
    }

    /**
     * Parses a JSON document held in memory, preserving member order and number literals.
     *
     * @param bytes  UTF-8 JSON text.
     * @param origin what to name in the error message if it does not parse.
     * @return the parsed document.
     * @throws IOException if the bytes are not JSON.
     */
    public static JsonElement parse(final byte[] bytes, final String origin) throws IOException
    {
        try
        {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
        }
        catch (final RuntimeException e)
        {
            throw new IOException("Not valid JSON: " + origin, e);
        }
    }

    /**
     * Serialises a document into the canonical byte form.
     *
     * @param document the document to write.
     * @return its UTF-8 bytes, trailing newline included.
     */
    public static byte[] toBytes(final JsonElement document)
    {
        final StringBuilder out = new StringBuilder();
        write(document, 0, out);
        out.append('\n');
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Writes one element at the given nesting depth.
     *
     * @param element the element to write.
     * @param level   the current indent level.
     * @param out     the buffer to append to.
     */
    private static void write(final JsonElement element, final int level, final StringBuilder out)
    {
        if (element.isJsonObject())
        {
            writeObject(element.getAsJsonObject(), level, out);
        }
        else if (element.isJsonArray())
        {
            writeArray(element.getAsJsonArray(), level, out);
        }
        else if (element.isJsonNull())
        {
            out.append("null");
        }
        else
        {
            writePrimitive(element.getAsJsonPrimitive(), out);
        }
    }

    /**
     * Writes an object. An empty object is {@code {}} on one line, as in the reference implementation.
     *
     * @param object the object to write.
     * @param level  the current indent level.
     * @param out    the buffer to append to.
     */
    private static void writeObject(final JsonObject object, final int level, final StringBuilder out)
    {
        if (object.size() == 0)
        {
            out.append("{}");
            return;
        }

        out.append("{\n");
        boolean first = true;
        for (final Map.Entry<String, JsonElement> member : object.entrySet())
        {
            if (!first)
            {
                out.append(",\n");
            }
            first = false;
            indent(level + 1, out);
            writeString(member.getKey(), out);
            out.append(": ");
            write(member.getValue(), level + 1, out);
        }
        out.append('\n');
        indent(level, out);
        out.append('}');
    }

    /**
     * Writes an array. An empty array is {@code []} on one line, as in the reference implementation.
     *
     * @param array the array to write.
     * @param level the current indent level.
     * @param out   the buffer to append to.
     */
    private static void writeArray(final JsonArray array, final int level, final StringBuilder out)
    {
        if (array.isEmpty())
        {
            out.append("[]");
            return;
        }

        out.append("[\n");
        for (int i = 0; i < array.size(); i++)
        {
            if (i > 0)
            {
                out.append(",\n");
            }
            indent(level + 1, out);
            write(array.get(i), level + 1, out);
        }
        out.append('\n');
        indent(level, out);
        out.append(']');
    }

    /**
     * Writes a string, number or boolean.
     *
     * <p>Numbers go out through {@link JsonPrimitive#getAsString()}, which for a parsed document returns the
     * literal exactly as it appeared in the source. Nothing here ever calls {@code getAsDouble} or
     * {@code getAsInt}.</p>
     *
     * @param primitive the primitive to write.
     * @param out       the buffer to append to.
     */
    private static void writePrimitive(final JsonPrimitive primitive, final StringBuilder out)
    {
        if (primitive.isString())
        {
            writeString(primitive.getAsString(), out);
        }
        else if (primitive.isBoolean())
        {
            out.append(primitive.getAsBoolean() ? "true" : "false");
        }
        else
        {
            out.append(primitive.getAsString());
        }
    }

    /**
     * Writes a quoted, escaped JSON string.
     *
     * @param value the raw string.
     * @param out   the buffer to append to.
     */
    private static void writeString(final String value, final StringBuilder out)
    {
        out.append('"');
        for (int i = 0; i < value.length(); i++)
        {
            final char c = value.charAt(i);
            switch (c)
            {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default ->
                {
                    if (c < 0x20)
                    {
                        out.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        // Everything else, non-ASCII included, goes out literally: the pinned form says
                        // escapeNonAscii=false, and the file is UTF-8 anyway.
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }

    /**
     * Appends one indent level's worth of spaces.
     *
     * @param level how deep.
     * @param out   the buffer to append to.
     */
    private static void indent(final int level, final StringBuilder out)
    {
        out.append(" ".repeat(level * INDENT));
    }
}
