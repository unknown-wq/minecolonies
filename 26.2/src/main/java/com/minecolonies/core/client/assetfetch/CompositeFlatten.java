package com.minecolonies.core.client.assetfetch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * The one mechanical transform in the asset bundle: collapsing a NeoForge {@code neoforge:composite} model
 * into a plain vanilla one.
 *
 * <p>Upstream writes some hut block models with NeoForge's composite model loader — a root model plus a
 * {@code children} map of sub-models, each with its own {@code textures} and {@code elements}. Fabric has no
 * such loader, so the port flattened every one of them by hand. The flattening turned out to be purely
 * mechanical, which is why the bundle ships it as a <em>rule the runtime recomputes</em> instead of as 18
 * stored patches full of upstream geometry.</p>
 *
 * <p>Rule id {@code neoforge-composite-flatten}, stated verbatim in {@code transforms.json} and implemented
 * identically in {@code 26.2/tools/assetfetch/composite_flatten.py}: drop {@code loader} and
 * {@code children}; merge each child's {@code textures} into the root map in document order (a later child
 * wins a repeated key); append each child's {@code elements} to the root list; discard the child-only keys
 * {@code render_type}, {@code parent} and {@code groups}.</p>
 */
public final class CompositeFlatten
{
    /**
     * The rule id {@code transforms.json} refers to.
     */
    public static final String RULE_ID = "neoforge-composite-flatten";

    /**
     * The {@code loader} value that marks a composite model.
     */
    private static final String COMPOSITE_LOADER = "neoforge:composite";

    /**
     * Private constructor to hide the public one.
     */
    private CompositeFlatten()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Whether a document is a composite model this rule applies to.
     *
     * @param document the document.
     * @return true if it declares the composite loader.
     */
    public static boolean isComposite(final JsonElement document)
    {
        if (!document.isJsonObject())
        {
            return false;
        }
        final JsonElement loader = document.getAsJsonObject().get("loader");
        return loader != null && loader.isJsonPrimitive() && loader.getAsJsonPrimitive().isString()
            && COMPOSITE_LOADER.equals(loader.getAsString());
    }

    /**
     * Flattens a composite model.
     *
     * <p>Key order matters here, because the result is serialised canonically and hashed. Copying the root
     * keys first and re-adding {@code textures}/{@code elements} afterwards reproduces the reference
     * implementation exactly: re-adding an existing key leaves it in its original slot, and a key that was
     * not there before lands at the end.</p>
     *
     * @param model the composite model.
     * @return a new, flat model. The input is not modified.
     * @throws AssetInstallException if {@code children} is not an object of objects.
     */
    public static JsonObject flatten(final JsonObject model) throws AssetInstallException
    {
        final JsonObject result = new JsonObject();
        for (final Map.Entry<String, JsonElement> member : model.entrySet())
        {
            if (!"loader".equals(member.getKey()) && !"children".equals(member.getKey()))
            {
                result.add(member.getKey(), member.getValue().deepCopy());
            }
        }

        final JsonObject textures = result.has("textures") && result.get("textures").isJsonObject()
            ? result.getAsJsonObject("textures")
            : new JsonObject();
        final JsonArray elements = result.has("elements") && result.get("elements").isJsonArray()
            ? result.getAsJsonArray("elements")
            : new JsonArray();

        final JsonElement rawChildren = model.get("children");
        if (rawChildren != null)
        {
            if (!rawChildren.isJsonObject())
            {
                throw new AssetInstallException("A composite model's 'children' is not an object");
            }
            for (final Map.Entry<String, JsonElement> child : rawChildren.getAsJsonObject().entrySet())
            {
                if (!child.getValue().isJsonObject())
                {
                    throw new AssetInstallException("Composite child '" + child.getKey() + "' is not an object");
                }
                final JsonObject body = child.getValue().getAsJsonObject();
                if (body.has("textures") && body.get("textures").isJsonObject())
                {
                    for (final Map.Entry<String, JsonElement> texture : body.getAsJsonObject("textures").entrySet())
                    {
                        textures.add(texture.getKey(), texture.getValue().deepCopy());
                    }
                }
                if (body.has("elements") && body.get("elements").isJsonArray())
                {
                    body.getAsJsonArray("elements").forEach(element -> elements.add(element.deepCopy()));
                }
            }
        }

        if (textures.size() > 0)
        {
            result.add("textures", textures);
        }
        if (!elements.isEmpty())
        {
            result.add("elements", elements);
        }
        return result;
    }
}
