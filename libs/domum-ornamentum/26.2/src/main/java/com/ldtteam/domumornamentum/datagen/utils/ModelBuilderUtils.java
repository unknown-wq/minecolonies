package com.ldtteam.domumornamentum.datagen.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Item display transforms, previously built through NeoForge's {@code ModelBuilder#transforms()} DSL.
 *
 * <p>Port note (26.2 / Fabric): {@code net.neoforged.neoforge.client.model.generators.ModelBuilder} does not exist on
 * Fabric and vanilla's {@code ModelTemplate} has no transform DSL either. Model JSON is therefore assembled by hand
 * and pushed into {@code BlockModelGenerators#modelOutput}, which is a {@code BiConsumer<Identifier, ModelInstance>}
 * with {@code ModelInstance extends Supplier<JsonElement>} — raw JSON is a first class citizen of the vanilla model
 * datagen. The emitted {@code "display"} object is identical to what the NeoForge builder produced.</p>
 */
public final class ModelBuilderUtils {

    private ModelBuilderUtils() {
        throw new IllegalStateException("Can not instantiate an instance of: ModelBuilderUtils. This is a utility class");
    }

    /**
     * The {@code display} block that used to be produced by {@code applyDefaultItemTransforms}.
     */
    public static JsonObject defaultItemTransforms() {
        final JsonObject display = new JsonObject();
        display.add("gui", transform(vec(30, 225, 0), vec(0, 0.5f, 0), scale(0.625f)));
        display.add("thirdperson_lefthand", transform(vec(75, 45, 0), vec(0, 2.5f, 0), scale(0.375f)));
        display.add("thirdperson_righthand", transform(vec(75, 45, 0), vec(0, 2.5f, 0), scale(0.375f)));
        display.add("firstperson_lefthand", transform(vec(0, 225, 0), null, scale(0.4f)));
        display.add("firstperson_righthand", transform(vec(0, 225, 0), null, scale(0.4f)));
        display.add("ground", transform(null, vec(0, 3, 0), scale(0.25f)));
        display.add("fixed", transform(null, null, scale(0.5f)));
        display.add("head", transform(null, null, scale(1.03f)));
        return display;
    }

    /**
     * The {@code display} block that used to be produced by {@code applyDoorItemTransforms}.
     */
    public static JsonObject doorItemTransforms() {
        final JsonObject display = new JsonObject();
        display.add("gui", transform(vec(30, 225, 0), vec(-2, -4, 0), scale(0.45f)));
        display.add("thirdperson_lefthand", transform(vec(75, 45, 0), vec(0, 2.5f, 0), scale(0.375f)));
        display.add("thirdperson_righthand", transform(vec(75, 45, 0), vec(0, 2.5f, 0), scale(0.375f)));
        display.add("firstperson_lefthand", transform(vec(0, 225, 0), null, scale(0.4f)));
        display.add("firstperson_righthand", transform(vec(0, 225, 0), null, scale(0.4f)));
        display.add("ground", transform(null, vec(0, 3, 0), scale(0.25f)));
        display.add("fixed", transform(null, null, scale(0.5f)));
        display.add("head", transform(null, null, scale(1.03f)));
        return display;
    }

    /**
     * Adds {@link #defaultItemTransforms()} onto the given model JSON and returns the very same object.
     */
    public static JsonObject applyDefaultItemTransforms(final JsonObject model) {
        model.add("display", defaultItemTransforms());
        return model;
    }

    /**
     * Adds {@link #doorItemTransforms()} onto the given model JSON and returns the very same object.
     */
    public static JsonObject applyDoorItemTransforms(final JsonObject model) {
        model.add("display", doorItemTransforms());
        return model;
    }

    private static JsonObject transform(final JsonArray rotation, final JsonArray translation, final JsonArray scale) {
        final JsonObject transform = new JsonObject();
        if (rotation != null) {
            transform.add("rotation", rotation);
        }
        if (scale != null) {
            transform.add("scale", scale);
        }
        if (translation != null) {
            transform.add("translation", translation);
        }
        return transform;
    }

    private static JsonArray scale(final float scale) {
        return vec(scale, scale, scale);
    }

    private static JsonArray vec(final float x, final float y, final float z) {
        final JsonArray array = new JsonArray();
        array.add(x);
        array.add(y);
        array.add(z);
        return array;
    }
}
