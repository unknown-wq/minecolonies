package com.minecolonies.core.generation.defaults;

import com.minecolonies.api.items.ModItems;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.special.BannerSpecialRenderer;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BannerBlock;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Datagen for item models
 *
 * <p>Port notes (26.2 / Fabric):</p>
 * <ul>
 *   <li>NeoForge's {@code ItemModelProvider} / {@code ModelFile} / {@code ExistingFileHelper} are gone. The
 *       replacement is vanilla's {@code ModelProvider} (Fabric flavour {@link FabricModelProvider}), whose
 *       {@link ItemModelGenerators} both writes {@code assets/&lt;ns&gt;/models/item/*.json} and — new since
 *       1.21.4 — the mandatory item model <em>definition</em> in {@code assets/&lt;ns&gt;/items/*.json}.</li>
 *   <li>TODO(port-26.2): DEGRADED — the {@code build_goggles} model no longer carries the
 *       {@code minecraft:disabled} override. Model overrides were replaced by item model definitions with a
 *       {@code minecraft:condition} on a registered {@code ConditionalItemModelProperty}, and no such property is
 *       registered by the client any more (see the TODO in
 *       {@code com.minecolonies.core.event.ClientRegistryHandler}, where the old
 *       {@code ItemProperties.register(buildGoggles, "disabled", …)} used to live). The
 *       {@code build_goggles_disabled} model is still generated, so restoring this is: register a
 *       {@code ConditionalItemModelProperty} client-side, then swap {@link #buildGoggles} to
 *       {@code ItemModelUtils.conditional(property, disabledModel, enabledModel)}.</li>
 *   <li>Everything else is a straight translation: the 1.21.1 provider emitted exactly the goggles pair plus one
 *       flat model per {@code ModItems.getAllIngredients()} / {@code getAllFoods()} entry, all textured from
 *       {@code minecolonies:item/food/&lt;name&gt;}.</li>
 *   <li>The trailing {@link #declareRemainingItems} loop is <b>new</b>: it emits the 1.21.4+ item definition for
 *       every other MineColonies item, pointing at the hand written {@code models/item/&lt;name&gt;.json} that
 *       already ships in {@code src/main/resources}. Without it those items have no definition at all and render
 *       as the missing model. It writes exactly what vanilla's own
 *       {@code ModelProvider$ItemInfoCollector#finalizeAndValidate} auto-fill would write, which Fabric's mixin
 *       restricts to items this provider touched.</li>
 * </ul>
 */
public class DefaultItemModelProvider extends FabricModelProvider
{
    private static final int BLACK  = 0x000000;
    private static final int BLUE   = 0x0000FF;
    private static final int GREEN  = 0x008000;
    private static final int ORANGE = 0xFFA500;
    private static final int RED    = 0xFF0000;
    private static final int WHITE  = 0xFFFFFF;
    private static final int YELLOW = 0xFFFF00;

    /**
     * {@code egg id -> {background, highlight}}, carried over verbatim from the 1.21.1 item registration, where the
     * same two colours were constructor arguments of {@code DeferredSpawnEggItem}. See {@link #spawnEggs}.
     */
    private static final Map<String, int[]> SPAWN_EGG_COLOURS = Map.ofEntries(
      Map.entry("barbarianegg", new int[] {ORANGE, BLACK}),
      Map.entry("barbarcheregg", new int[] {ORANGE, GREEN}),
      Map.entry("barbchiefegg", new int[] {ORANGE, YELLOW}),
      Map.entry("pirateegg", new int[] {RED, WHITE}),
      Map.entry("piratearcheregg", new int[] {RED, GREEN}),
      Map.entry("piratecaptainegg", new int[] {RED, YELLOW}),
      Map.entry("mummyegg", new int[] {YELLOW, WHITE}),
      Map.entry("mummyarcheregg", new int[] {YELLOW, GREEN}),
      Map.entry("pharaoegg", new int[] {YELLOW, YELLOW}),
      Map.entry("shieldmaidenegg", new int[] {BLACK, WHITE}),
      Map.entry("norsemenarcheregg", new int[] {BLACK, GREEN}),
      Map.entry("norsemenchiefegg", new int[] {BLACK, YELLOW}),
      Map.entry("amazonegg", new int[] {GREEN, WHITE}),
      Map.entry("amazonspearmanegg", new int[] {GREEN, GREEN}),
      Map.entry("amazonchiefegg", new int[] {GREEN, YELLOW}),
      Map.entry("drownedpirateegg", new int[] {BLUE, WHITE}),
      Map.entry("drownedpiratearcheregg", new int[] {BLUE, GREEN}),
      Map.entry("drownedpiratecaptainegg", new int[] {BLUE, YELLOW}));

    /**
     * Constructor
     */
    public DefaultItemModelProvider(final FabricPackOutput packOutput)
    {
        super(packOutput);
    }

    @Override
    public void generateBlockStateModels(@NotNull final BlockModelGenerators generator)
    {
        // MineColonies' blockstates and block models are all hand written in src/main/resources.
    }

    @Override
    public void generateItemModels(@NotNull final ItemModelGenerators generator)
    {
        final Set<Item> handled = new HashSet<>();

        buildGoggles(generator, handled);

        for (final Item foodItem : ModItems.getAllIngredients())
        {
            foodModel(generator, foodItem);
            handled.add(foodItem);
        }

        for (final Item foodItem : ModItems.getAllFoods())
        {
            foodModel(generator, foodItem);
            handled.add(foodItem);
        }

        spawnEggs(generator, handled);
        colonyBanner(generator, handled);

        declareRemainingItems(generator, handled);
    }

    /**
     * The colony flag banner. Its model, {@code minecraft:item/template_banner}, carries no geometry -- a banner has
     * always been drawn by a renderer, which under NeoForge/1.21.1 was reached through the model's {@code builtin}
     * parent. 26.2 wires renderers from the item model <em>definition</em> instead ({@code minecraft:special}), so a
     * plain model reference leaves the item with nothing to draw at all. This is the same declaration vanilla emits
     * for its own banners; the patterns come from the stack's {@code banner_patterns} component, which
     * {@code ItemColonyFlagBanner} (a {@code BannerItem}) already writes.
     */
    private static void colonyBanner(@NotNull final ItemModelGenerators generator, @NotNull final Set<Item> handled)
    {
        if (ModItems.flagBanner == null)
        {
            return;
        }
        generator.itemModelOutput.accept(ModItems.flagBanner, ItemModelUtils.specialModel(
          Identifier.withDefaultNamespace("item/template_banner"),
          BannerRenderer.TRANSFORMATIONS.freeTransformations(0),
          new BannerSpecialRenderer.Unbaked(DyeColor.WHITE, BannerBlock.AttachmentType.GROUND)));
        handled.add(ModItems.flagBanner);
    }

    /**
     * Spawn eggs. 26.2 dropped both halves of the old mechanism: {@code minecraft:item/template_spawn_egg} (the
     * two-layer greyscale model every modded egg parented onto) no longer exists, and neither does the item-level
     * colour pair that tinted it -- every vanilla egg now ships a bespoke texture instead. A modded egg that keeps
     * the old parent therefore resolves to no model at all and renders as the missing-texture cube.
     * <p>
     * The replacement is the same two-layer model drawn from the mod's own
     * {@code minecolonies:item/spawn_egg} / {@code spawn_egg_overlay} pair, with the two colours moved from the
     * item registration into the item model definition as constant tints. The colours are the ones the 1.21.1
     * registration passed to {@code DeferredSpawnEggItem}.
     */
    private static void spawnEggs(@NotNull final ItemModelGenerators generator, @NotNull final Set<Item> handled)
    {
        SPAWN_EGG_COLOURS.forEach((path, colours) -> {
            final Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(MOD_ID, path));
            if (item == null || item == Items.AIR)
            {
                return;
            }
            generator.itemModelOutput.accept(item, ItemModelUtils.tintedModel(
              Identifier.fromNamespaceAndPath(MOD_ID, "item/" + path),
              ItemModelUtils.constantTint(colours[0]),
              ItemModelUtils.constantTint(colours[1])));
            handled.add(item);
        });
    }

    private static void buildGoggles(@NotNull final ItemModelGenerators generator, @NotNull final Set<Item> handled)
    {
        // still generated so that the model exists for whoever restores the conditional (see the class javadoc)
        ModelTemplates.FLAT_ITEM.create(
          Identifier.fromNamespaceAndPath(MOD_ID, "item/build_goggles_disabled"),
          TextureMapping.layer0(new Material(Identifier.fromNamespaceAndPath(MOD_ID, "item/build_goggles_disabled"))),
          generator.modelOutput);

        generator.generateFlatItem(ModItems.buildGoggles, ModelTemplates.FLAT_ITEM);
        handled.add(ModItems.buildGoggles);
    }

    private static void foodModel(@NotNull final ItemModelGenerators generator, @NotNull final Item item)
    {
        final Identifier itemId = BuiltInRegistries.ITEM.getKey(item);
        final Identifier model = ModelLocationUtils.getModelLocation(item);
        ModelTemplates.FLAT_ITEM.create(
          model,
          TextureMapping.layer0(new Material(Identifier.fromNamespaceAndPath(MOD_ID, "item/food/" + itemId.getPath()))),
          generator.modelOutput);
        generator.itemModelOutput.accept(item, ItemModelUtils.plainModel(model));
    }

    private static void declareRemainingItems(@NotNull final ItemModelGenerators generator, @NotNull final Set<Item> handled)
    {
        for (final Identifier id : BuiltInRegistries.ITEM.keySet())
        {
            if (!MOD_ID.equals(id.getNamespace()))
            {
                continue;
            }
            final Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item == null || handled.contains(item))
            {
                continue;
            }
            generator.itemModelOutput.accept(item, ItemModelUtils.plainModel(ModelLocationUtils.getModelLocation(item)));
        }
    }

    @NotNull
    @Override
    public String getName()
    {
        return "MineColonies Item Models";
    }
}
