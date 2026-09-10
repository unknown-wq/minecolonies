package com.ldtteam.domumornamentum.datagen.utils;

import com.google.gson.JsonObject;
import com.ldtteam.domumornamentum.datagen.MateriallyTexturedModelBuilder;
import com.ldtteam.domumornamentum.util.Constants;
import com.mojang.math.Quadrant;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyValueList;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Thin façade over vanilla's {@link BlockModelGenerators} that reproduces the handful of NeoForge
 * {@code BlockStateProvider} conveniences the mod relied on.
 *
 * <p>Port notes (26.2 / Fabric):</p>
 * <ul>
 *   <li>{@code BlockStateProvider}, {@code ModelFile}, {@code ConfiguredModel},
 *       {@code MultiPartBlockStateBuilder} and {@code ExistingFileHelper} do not exist on Fabric. The
 *       replacements are {@link net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider} plus the
 *       vanilla trio {@link MultiVariantGenerator} / {@link MultiPartGenerator} / {@link MultiVariant}.</li>
 *   <li>There is no {@code ExistingFileHelper} equivalent, i.e. nothing validates that a referenced parent model
 *       actually exists. Referencing a hand written {@code _spec} model is therefore simply an
 *       {@link Identifier} — no registration, no validation, and no build failure when it is missing.</li>
 *   <li>Vanilla's model sink throws {@code "Duplicate model definition for <id>"} on a second write of the same id,
 *       whereas NeoForge's {@code models().withExistingParent(...)} silently returned the cached builder. Several DO
 *       providers emit the same model from nested loops, so every write goes through {@link #model} which drops
 *       repeats.</li>
 *   <li>Rotations are {@link Quadrant}s now; {@code -90} / {@code 360} / {@code 450} are normalised into
 *       {@code 0/90/180/270}. (The codec itself uses {@code Mth.positiveModulo}, so the un-normalised values in the
 *       already committed JSON still load — the generated files simply differ cosmetically.)</li>
 * </ul>
 */
public final class ModelCollector {

    private final BlockModelGenerators generator;
    private final Set<Identifier> emittedModels = new HashSet<>();

    public ModelCollector(final BlockModelGenerators generator) {
        this.generator = generator;
    }

    public BlockModelGenerators generator() {
        return this.generator;
    }

    // ----------------------------------------------------------------------------------------------------------
    // identifiers
    // ----------------------------------------------------------------------------------------------------------

    /**
     * Replacement for NeoForge's {@code modLoc(path)}.
     */
    public static Identifier modLoc(final String path) {
        return Constants.resLocDO(path);
    }

    /**
     * Replacement for NeoForge's {@code mcLoc(path)}.
     */
    public static Identifier mcLoc(final String path) {
        return Identifier.withDefaultNamespace(path);
    }

    // ----------------------------------------------------------------------------------------------------------
    // models
    // ----------------------------------------------------------------------------------------------------------

    /**
     * Writes {@code assets/domum_ornamentum/models/<path>.json} once and returns its id.
     */
    public Identifier model(final String path, final JsonObject json) {
        return model(modLoc(path), json);
    }

    /**
     * Writes a model once and returns its id; a second write of the same id is ignored.
     */
    public Identifier model(final Identifier id, final JsonObject json) {
        if (this.emittedModels.add(id)) {
            this.generator.modelOutput.accept(id, () -> json);
        }
        return id;
    }

    /**
     * Replacement for {@code models().withExistingParent(path, parent).customLoader(MateriallyTexturedModelBuilder::new).end()}.
     */
    public Identifier materiallyTextured(final String path, final Identifier parent) {
        return model(path, MateriallyTexturedModelBuilder.materiallyTextured(parent));
    }

    /**
     * Replacement for {@code models().withExistingParent(path, parent)}.
     */
    public Identifier parented(final String path, final Identifier parent) {
        return model(path, MateriallyTexturedModelBuilder.withParent(parent).build());
    }

    /**
     * Replacement for {@code models().cubeAll(path, texture)}.
     */
    public Identifier cubeAll(final String path, final Identifier texture) {
        final JsonObject json = new JsonObject();
        json.addProperty("parent", "minecraft:block/cube_all");
        final JsonObject textures = new JsonObject();
        textures.addProperty("all", texture.toString());
        json.add("textures", textures);
        return model(path, json);
    }

    // ----------------------------------------------------------------------------------------------------------
    // blockstates
    // ----------------------------------------------------------------------------------------------------------

    public void blockState(final BlockModelDefinitionGenerator definition) {
        this.generator.blockStateOutput.accept(definition);
    }

    /**
     * Replacement for {@code simpleBlock(block, model)}.
     */
    public void simpleBlock(final Block block, final Identifier model) {
        blockState(MultiVariantGenerator.dispatch(block, variant(model)));
    }

    /**
     * Replacement for {@code getMultipartBuilder(block)}.
     */
    public MultiPartGenerator multiPart(final Block block) {
        return MultiPartGenerator.multiPart(block);
    }

    /**
     * Replacement for {@code getVariantBuilder(block).forAllStatesExcept(fn, ignored…)}.
     *
     * <p>26.2's {@link MultiVariantGenerator} only knows how to fan out over a {@code PropertyDispatch}, which is
     * unusable for a five-property door. The blockstate file is therefore assembled directly: iterate the possible
     * states, build the variant key with {@link PropertyValueList} (identical formatting — sorted by property name,
     * {@code name=value} joined by commas) and hand the finished
     * {@link BlockStateModelDispatcher} back as an ad-hoc {@link BlockModelDefinitionGenerator}.</p>
     */
    public static BlockModelDefinitionGenerator forAllStatesExcept(final Block block,
                                                                  final Function<BlockState, MultiVariant> factory,
                                                                  final Property<?>... ignored) {
        final Set<Property<?>> skipped = Set.of(ignored);
        final Map<String, BlockStateModel.Unbaked> variants = new LinkedHashMap<>();
        for (final BlockState state : block.getStateDefinition().getPossibleStates()) {
            PropertyValueList key = PropertyValueList.EMPTY;
            for (final Property<?> property : state.getProperties()) {
                if (!skipped.contains(property)) {
                    key = key.extend(valueOf(state, property));
                }
            }
            variants.putIfAbsent(key.getKey(), factory.apply(state).toUnbaked());
        }

        final BlockStateModelDispatcher dispatcher = new BlockStateModelDispatcher(
                Optional.of(new BlockStateModelDispatcher.SimpleModelSelectors(variants)), Optional.empty());
        return new BlockModelDefinitionGenerator() {
            @Override
            public Block block() {
                return block;
            }

            @Override
            public BlockStateModelDispatcher create() {
                return dispatcher;
            }
        };
    }

    private static <T extends Comparable<T>> Property.Value<T> valueOf(final BlockState state, final Property<T> property) {
        return property.value(state.getValue(property));
    }

    // ----------------------------------------------------------------------------------------------------------
    // variants
    // ----------------------------------------------------------------------------------------------------------

    public static MultiVariant variant(final Identifier model) {
        return new MultiVariant(WeightedList.of(new Variant(model)));
    }

    public static MultiVariant variant(final Identifier model, final int xRot, final int yRot) {
        return variant(model, xRot, yRot, false);
    }

    public static MultiVariant variant(final Identifier model, final int xRot, final int yRot, final boolean uvLock) {
        Variant single = new Variant(model);
        if (xRot != 0) {
            single = single.withXRot(quadrant(xRot));
        }
        if (yRot != 0) {
            single = single.withYRot(quadrant(yRot));
        }
        if (uvLock) {
            single = single.withUvLock(true);
        }
        return new MultiVariant(WeightedList.of(single));
    }

    /**
     * NeoForge's {@code ConfiguredModel} accepted any multiple of 90, including negatives and values above 270;
     * 26.2 stores them as {@link Quadrant}, so they are folded into the {@code 0/90/180/270} range here.
     */
    public static Quadrant quadrant(final int degrees) {
        return switch (Mth.positiveModulo(degrees, 360)) {
            case 0 -> Quadrant.R0;
            case 90 -> Quadrant.R90;
            case 180 -> Quadrant.R180;
            case 270 -> Quadrant.R270;
            default -> throw new IllegalArgumentException("Invalid rotation " + degrees + ", only 0/90/180/270 allowed");
        };
    }

    /**
     * Replacement for {@code .condition(property, value)} on a multipart part.
     */
    public static <T extends Comparable<T>> ConditionBuilder when(final Property<T> property, final T value) {
        return new ConditionBuilder().term(property, value);
    }

    // ----------------------------------------------------------------------------------------------------------
    // item models
    // ----------------------------------------------------------------------------------------------------------

    /**
     * Replacement for {@code itemModels().getBuilder(name)} / {@code itemModels().withExistingParent(name, parent)}:
     * writes {@code assets/domum_ornamentum/models/item/<name>.json}.
     *
     * <p>Additionally registers the model as the block item's model, which makes vanilla write the 1.21.4+
     * {@code assets/domum_ornamentum/items/<name>.json} dispatch file. Without it vanilla's
     * {@code ModelProvider$ItemInfoCollector#finalizeAndValidate} auto-fills that file with a reference to
     * {@code domum_ornamentum:block/<name>}, which does not exist for most DO blocks.</p>
     */
    public Identifier itemModel(final Block block, final String name, final JsonObject json) {
        final Identifier id = model("item/" + name, json);
        itemModelReference(block, id);
        return id;
    }

    /**
     * Writes a plain item model file without binding it to an item (used for the {@code _spec} intermediates).
     */
    public Identifier itemModel(final String name, final JsonObject json) {
        return model("item/" + name, json);
    }

    /**
     * Replacement for the dead item-model {@code "overrides"} block: an
     * {@code assets/domum_ornamentum/items/&lt;name&gt;.json} that dispatches on a blockstate property carried by the
     * stack.
     *
     * <p>Port note (26.2 / Fabric). {@code "overrides"} on an item model stopped being read in 1.21.4, and
     * NeoForge's {@code ItemProperties.register(item, id, (stack, …) -> ordinal)} that drove it does not exist
     * either. Both are replaced by the data-driven {@code minecraft:select} item model:</p>
     *
     * <pre>{@code
     * { "model": { "type": "minecraft:select",
     *              "property": "minecraft:block_state",
     *              "block_state_property": "type",
     *              "cases":   [ { "when": "waffle", "model": { "type": "minecraft:model", "model": "…" } }, … ],
     *              "fallback": { "type": "minecraft:model", "model": "…" } } }
     * }</pre>
     *
     * <p>{@code minecraft:block_state} is the right selector rather than {@code minecraft:component}, because
     * {@link net.minecraft.client.renderer.item.properties.select.ItemBlockState} reads exactly what Domum Ornamentum
     * writes: {@code stack.get(DataComponents.BLOCK_STATE).properties().get("type")}
     * ({@code /opt/mc-src/net/minecraft/client/renderer/item/properties/select/ItemBlockState.java:24-29} against
     * {@code util/BlockUtils#putPropertyIntoBlockStateTag}). {@code minecraft:component} would hand back the whole
     * {@code BlockItemStateProperties} map and force whole-map equality.</p>
     *
     * <p>Everything is assembled by the vanilla helper
     * {@code ItemModelUtils.selectBlockItemProperty(Property, fallback, Map)}
     * ({@code /opt/mc-src/net/minecraft/client/data/models/model/ItemModelUtils.java:176-180}), which derives both
     * the property name and each case's string value from the {@link Property} itself.</p>
     */
    public <T extends Comparable<T>> void selectingItemModel(final Block block,
                                                             final Property<T> property,
                                                             final T fallbackValue,
                                                             final Function<T, Identifier> modelForValue) {
        final Item item = block.asItem();
        if (item == Items.AIR) {
            return;
        }

        final Map<T, ItemModel.Unbaked> cases = new LinkedHashMap<>();
        for (final T value : property.getPossibleValues()) {
            cases.put(value, ItemModelUtils.plainModel(modelForValue.apply(value)));
        }

        this.generator.itemModelOutput.accept(item, ItemModelUtils.selectBlockItemProperty(
                property, ItemModelUtils.plainModel(modelForValue.apply(fallbackValue)), cases));
    }

    /**
     * Binds an already emitted model to a block's item. A block without an item is skipped: {@code asItem()} would
     * hand back {@code Items.AIR} and vanilla would happily write {@code assets/minecraft/items/air.json}.
     */
    public void itemModelReference(final Block block, final Identifier model) {
        final Item item = block.asItem();
        if (item == Items.AIR) {
            return;
        }
        this.generator.itemModelOutput.accept(item, ItemModelUtils.plainModel(model));
    }

    /**
     * Replacement for {@code simpleBlockItem(block, model)}: {@code models/item/<name>.json} = {@code {"parent": model}}.
     */
    public void simpleBlockItem(final Block block, final Identifier parent) {
        itemModel(block, registryPath(block), MateriallyTexturedModelBuilder.withParent(parent).build());
    }

    // ----------------------------------------------------------------------------------------------------------
    // misc
    // ----------------------------------------------------------------------------------------------------------

    public static String registryPath(final Block block) {
        return block.builtInRegistryHolder().key().identifier().getPath();
    }
}
