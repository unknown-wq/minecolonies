package com.minecolonies.core.client.assetfetch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the RFC 6902 JSON Patch subset the asset bundle uses.
 *
 * <p>The bundle's patches are produced by {@code 26.2/tools/assetfetch/jsonpatch.py} and only ever contain
 * {@code add}, {@code replace} and {@code remove} — no {@code copy}, {@code move} or {@code test}. This is
 * the runtime twin of that script's {@code apply_patch}, and it has to agree with it operation for
 * operation: the manifest hashes were computed from documents the Python side produced.</p>
 *
 * <p>Two details carry the agreement. Member order is never disturbed — replacing an existing key leaves it
 * where it was (Gson's {@code JsonObject} is insertion-ordered and re-adding a key keeps its slot, exactly
 * like a Python dict) — and {@code add} on an array <em>inserts</em> at the index rather than overwriting.</p>
 */
public final class JsonPatch
{
    /**
     * Private constructor to hide the public one.
     */
    private JsonPatch()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Applies a patch to a document.
     *
     * @param document the document to patch. It is not modified; a deep copy is patched and returned.
     * @param patch    the patch, an array of operation objects.
     * @return the patched document.
     * @throws AssetInstallException if an operation is malformed or does not apply.
     */
    public static JsonElement apply(final JsonElement document, final JsonArray patch) throws AssetInstallException
    {
        JsonElement doc = document.deepCopy();

        for (final JsonElement rawOp : patch)
        {
            if (!rawOp.isJsonObject())
            {
                throw new AssetInstallException("JSON patch operation is not an object: " + rawOp);
            }
            final JsonObject op = rawOp.getAsJsonObject();
            final String kind = string(op, "op");
            final String pointer = string(op, "path");

            if (pointer.isEmpty())
            {
                if (!"replace".equals(kind))
                {
                    throw new AssetInstallException("Only 'replace' is defined on the whole document, got '" + kind + "'");
                }
                doc = value(op, pointer);
                continue;
            }

            final List<String> tokens = tokens(pointer);
            final JsonElement parent = resolve(doc, tokens, pointer);
            final String last = tokens.get(tokens.size() - 1);

            if (parent.isJsonArray())
            {
                applyToArray(parent.getAsJsonArray(), last, kind, op, pointer);
            }
            else if (parent.isJsonObject())
            {
                applyToObject(parent.getAsJsonObject(), last, kind, op, pointer);
            }
            else
            {
                throw new AssetInstallException("JSON pointer " + pointer + " does not address a container");
            }
        }

        return doc;
    }

    /**
     * Applies one operation whose parent is an array.
     *
     * @param array   the parent array.
     * @param token   the last pointer token: an index, or {@code -} for "past the end".
     * @param kind    the operation name.
     * @param op      the whole operation, for its {@code value}.
     * @param pointer the pointer, for error messages.
     * @throws AssetInstallException if the operation is unsupported or the index is out of range.
     */
    private static void applyToArray(final JsonArray array, final String token, final String kind, final JsonObject op, final String pointer)
        throws AssetInstallException
    {
        final int index = "-".equals(token) ? array.size() : index(token, pointer);

        switch (kind)
        {
            case "add" ->
            {
                if (index < 0 || index > array.size())
                {
                    throw new AssetInstallException("JSON patch 'add' index out of range at " + pointer);
                }
                // An 'add' on an array inserts, it does not overwrite. asList() is a live, mutable view.
                array.asList().add(index, value(op, pointer));
            }
            case "replace" ->
            {
                requireIndex(array, index, pointer);
                array.set(index, value(op, pointer));
            }
            case "remove" ->
            {
                requireIndex(array, index, pointer);
                array.remove(index);
            }
            default -> throw new AssetInstallException("Unsupported JSON patch op '" + kind + "' at " + pointer);
        }
    }

    /**
     * Applies one operation whose parent is an object.
     *
     * @param object  the parent object.
     * @param key     the last pointer token, already unescaped.
     * @param kind    the operation name.
     * @param op      the whole operation, for its {@code value}.
     * @param pointer the pointer, for error messages.
     * @throws AssetInstallException if the operation is unsupported or removes an absent key.
     */
    private static void applyToObject(final JsonObject object, final String key, final String kind, final JsonObject op, final String pointer)
        throws AssetInstallException
    {
        switch (kind)
        {
            case "add", "replace" -> object.add(key, value(op, pointer));
            case "remove" ->
            {
                if (!object.has(key))
                {
                    throw new AssetInstallException("JSON patch 'remove' of an absent member at " + pointer);
                }
                object.remove(key);
            }
            default -> throw new AssetInstallException("Unsupported JSON patch op '" + kind + "' at " + pointer);
        }
    }

    /**
     * Walks every pointer token but the last, so the caller can act on the container that holds the target.
     *
     * @param document the document.
     * @param tokens   the pointer tokens.
     * @param pointer  the pointer, for error messages.
     * @return the parent container.
     * @throws AssetInstallException if the path does not exist.
     */
    private static JsonElement resolve(final JsonElement document, final List<String> tokens, final String pointer) throws AssetInstallException
    {
        JsonElement current = document;
        for (int i = 0; i < tokens.size() - 1; i++)
        {
            final String token = tokens.get(i);
            if (current.isJsonArray())
            {
                final JsonArray array = current.getAsJsonArray();
                final int index = index(token, pointer);
                requireIndex(array, index, pointer);
                current = array.get(index);
            }
            else if (current.isJsonObject() && current.getAsJsonObject().has(token))
            {
                current = current.getAsJsonObject().get(token);
            }
            else
            {
                throw new AssetInstallException("JSON pointer " + pointer + " does not resolve (stopped at '" + token + "')");
            }
        }
        return current;
    }

    /**
     * Splits a JSON Pointer into unescaped tokens.
     *
     * @param pointer the pointer, which always starts with {@code /} here.
     * @return its tokens.
     * @throws AssetInstallException if the pointer is not rooted.
     */
    private static List<String> tokens(final String pointer) throws AssetInstallException
    {
        if (pointer.charAt(0) != '/')
        {
            throw new AssetInstallException("JSON pointer must start with '/': " + pointer);
        }
        final List<String> tokens = new ArrayList<>();
        for (final String raw : pointer.substring(1).split("/", -1))
        {
            tokens.add(raw.replace("~1", "/").replace("~0", "~"));
        }
        return tokens;
    }

    /**
     * Parses an array index token.
     *
     * @param token   the token.
     * @param pointer the pointer, for error messages.
     * @return the index.
     * @throws AssetInstallException if it is not a number.
     */
    private static int index(final String token, final String pointer) throws AssetInstallException
    {
        try
        {
            return Integer.parseInt(token);
        }
        catch (final NumberFormatException e)
        {
            throw new AssetInstallException("JSON pointer " + pointer + " indexes an array with '" + token + "'");
        }
    }

    /**
     * Bounds-checks an array index.
     *
     * @param array   the array.
     * @param index   the index.
     * @param pointer the pointer, for error messages.
     * @throws AssetInstallException if the index is out of range.
     */
    private static void requireIndex(final JsonArray array, final int index, final String pointer) throws AssetInstallException
    {
        if (index < 0 || index >= array.size())
        {
            throw new AssetInstallException("JSON pointer " + pointer + " is out of range (size " + array.size() + ")");
        }
    }

    /**
     * Reads the {@code value} of an operation.
     *
     * @param op      the operation.
     * @param pointer the pointer, for error messages.
     * @return the value.
     * @throws AssetInstallException if the operation carries none.
     */
    private static JsonElement value(final JsonObject op, final String pointer) throws AssetInstallException
    {
        final JsonElement value = op.get("value");
        if (value == null)
        {
            throw new AssetInstallException("JSON patch operation at " + pointer + " is missing its 'value'");
        }
        return value;
    }

    /**
     * Reads a required string member of an operation object.
     *
     * @param op     the operation.
     * @param member the member name.
     * @return its value.
     * @throws AssetInstallException if it is absent or not a string.
     */
    private static String string(final JsonObject op, final String member) throws AssetInstallException
    {
        final JsonElement value = op.get(member);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
        {
            throw new AssetInstallException("JSON patch operation is missing a string '" + member + "': " + op);
        }
        return value.getAsString();
    }
}
