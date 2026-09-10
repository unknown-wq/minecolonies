package com.ldtteam.domumornamentum.datagen;

import com.google.gson.JsonObject;
import com.ldtteam.domumornamentum.datagen.utils.ModelBuilderUtils;
import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.resources.Identifier;

/**
 * Builds the JSON of a model handled by the mod's own model loader.
 *
 * <p>Port note (26.2 / Fabric): NeoForge's {@code CustomLoaderBuilder} / {@code ModelBuilder} pair does not exist on
 * Fabric. The generated shape is frozen by contract C7 and is produced literally here:</p>
 *
 * <pre>{@code
 * { "parent": "domum_ornamentum:block/fence/fence_post_spec",
 *   "loader": "domum_ornamentum:materially_textured" }
 * }</pre>
 *
 * <p>The {@code loader} key carries {@link Constants#MATERIALLY_TEXTURED_MODEL_LOADER}, exactly as the NeoForge
 * custom loader builder used to emit it, so the already committed JSON files stay byte-compatible.</p>
 *
 * <p>There is deliberately no {@code override(...)} builder any more: item model {@code "overrides"} stopped being
 * read in 1.21.4 and reproducing it was what made every type-carrying Domum Ornamentum item invisible in the
 * inventory. The replacement is {@code ModelCollector#selectingItemModel}.</p>
 */
public final class MateriallyTexturedModelBuilder {

    private final JsonObject json = new JsonObject();

    private MateriallyTexturedModelBuilder() {
    }

    /**
     * A brand new, empty model.
     */
    public static MateriallyTexturedModelBuilder builder() {
        return new MateriallyTexturedModelBuilder();
    }

    /**
     * A model with the given parent, replacing {@code models().withExistingParent(name, parent)}.
     */
    public static MateriallyTexturedModelBuilder withParent(final Identifier parent) {
        return builder().parent(parent);
    }

    /**
     * A model with the given parent and the mod's custom loader attached — the exact contract C7 shape.
     */
    public static JsonObject materiallyTextured(final Identifier parent) {
        return withParent(parent).customLoader().build();
    }

    public MateriallyTexturedModelBuilder parent(final Identifier parent) {
        this.json.addProperty("parent", parent.toString());
        return this;
    }

    /**
     * Replacement for {@code .customLoader(MateriallyTexturedModelBuilder::new).end()}.
     */
    public MateriallyTexturedModelBuilder customLoader() {
        this.json.addProperty("loader", Constants.MATERIALLY_TEXTURED_MODEL_LOADER.toString());
        return this;
    }

    /**
     * Replacement for {@code ModelBuilderUtils.applyDefaultItemTransforms(builder)}.
     */
    public MateriallyTexturedModelBuilder defaultItemTransforms() {
        ModelBuilderUtils.applyDefaultItemTransforms(this.json);
        return this;
    }

    /**
     * Replacement for {@code ModelBuilderUtils.applyDoorItemTransforms(builder)}.
     */
    public MateriallyTexturedModelBuilder doorItemTransforms() {
        ModelBuilderUtils.applyDoorItemTransforms(this.json);
        return this;
    }

    public JsonObject build() {
        return this.json;
    }
}
