package com.minecolonies.apiimp.initializer;

import com.ldtteam.blockui.Color;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.items.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;

import static com.minecolonies.api.blocks.decorative.AbstractBlockGate.IRON_GATE;
import static com.minecolonies.api.blocks.decorative.AbstractBlockGate.WOODEN_GATE;

public final class ModItemsInitializer
{
    /**
     * Spawn egg colors.
     */
    private static final int PRIMARY_COLOR_BARBARIAN   = 5;
    private static final int SECONDARY_COLOR_BARBARIAN = 700;
    private static final int PRIMARY_COLOR_PIRATE      = 7;
    private static final int SECONDARY_COLOR_PIRATE    = 600;
    private static final int PRIMARY_COLOR_EG          = 10;
    private static final int SECONDARY_COLOR_EG        = 400;

    private ModItemsInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModItemsInitializer but this is a Utility class.");
    }

    /**
     * Registers every item.
     * <p>
     * Port note (contract C5): {@code RegisterEvent} is gone; the mod entry point calls this directly.
     */
    public static void init()
    {
        init(BuiltInRegistries.ITEM);
    }

    /**
     * Initates all the blocks. At the correct time.
     *
     * @param registry the registry.
     */
    @SuppressWarnings("PMD.ExcessiveMethodLength")
    public static void init(final Registry<Item> registry)
    {
        ModItems.scepterLumberjack = new ItemScepterLumberjack(props("scepterlumberjack"));
        ModItems.supplyChest = new ItemSupplyChestDeployer(props("supplychestdeployer"));
        ModItems.permTool = new ItemScepterPermission(props("scepterpermission"));
        ModItems.scepterGuard = new ItemScepterGuard(props("scepterguard"));
        ModItems.scepterClaim = new ItemScepterClaim(props("scepterclaim"));
        ModItems.scepterUnclaim = new ItemScepterUnclaim(props("scepterunclaim"));
        ModItems.scepterBorder = new ItemScepterBorder(props("scepterborder"));
        ModItems.scepterTerritory = new ItemScepterTerritory(props("scepterterritory"));
        ModItems.assistantHammer_Gold = new ItemAssistantHammer("assistanthammer_gold", props("assistanthammer_gold").durability(200), 1);
        ModItems.assistantHammer_Iron = new ItemAssistantHammer("assistanthammer_iron", props("assistanthammer_iron").durability(400), 2);
        ModItems.assistantHammer_Diamond = new ItemAssistantHammer("assistanthammer_diamond", props("assistanthammer_diamond").durability(1000), 3);
        ModItems.bannerRallyGuards = new ItemBannerRallyGuards(props("banner_rally_guards"));
        ModItems.supplyCamp = new ItemSupplyCampDeployer(props("supplycampdeployer"));
        ModItems.ancientTome = new ItemAncientTome(props("ancienttome"));
        ModItems.chiefSword = new ItemChiefSword(props("chiefsword").durability(1500));
        ModItems.scimitar = new ItemIronScimitar(props("iron_scimitar").durability(250));
        ModItems.clipboard = new ItemClipboard(props("clipboard"));
        ModItems.compost = new ItemCompost(props("compost"));
        ModItems.resourceScroll = new ItemResourceScroll(props("resourcescroll"));
        ModItems.pharaoscepter = new ItemPharaoScepter(props("pharaoscepter").durability(400));
        ModItems.firearrow = new ItemFireArrow(props("firearrow"));
        ModItems.scepterBeekeeper = new ItemScepterBeekeeper(props("scepterbeekeeper"));
        ModItems.fieldStick = new ItemFieldStick(props("fieldstick"));
        ModItems.mistletoe = new ItemMistletoe(props("mistletoe"));
        ModItems.spear = new ItemSpear(props("spear"));
        ModItems.questLog = new ItemQuestLog(props("questlog"));

        ModItems.breadDough = new ItemBreadDough(props("bread_dough"));
        ModItems.cookieDough = new ItemCookieDough(props("cookie_dough"));
        ModItems.cakeBatter = new ItemCakeBatter(props("cake_batter"));
        ModItems.rawPumpkinPie = new ItemRawPumpkinPie(props("raw_pumpkin_pie"));

        ModItems.milkyBread = new ItemMilkyBread(props("milky_bread"));
        ModItems.sugaryBread = new ItemSugaryBread(props("sugary_bread"));
        ModItems.goldenBread = new ItemGoldenBread(props("golden_bread"));
        ModItems.chorusBread = new ItemChorusBread(props("chorus_bread"));

        ModItems.adventureToken = new ItemAdventureToken(props("adventure_token"));

        ModItems.scrollColonyTP = new ItemScrollColonyTP(props("scroll_tp").stacksTo(16));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scroll_tp"), ModItems.scrollColonyTP);

        ModItems.scrollColonyAreaTP = new ItemScrollColonyAreaTP(props("scroll_area_tp").stacksTo(16));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scroll_area_tp"), ModItems.scrollColonyAreaTP);

        ModItems.scrollBuff = new ItemScrollBuff(props("scroll_buff").stacksTo(16));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scroll_buff"), ModItems.scrollBuff);

        ModItems.scrollGuardHelp = new ItemScrollGuardHelp(props("scroll_guard_help").stacksTo(16));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scroll_guard_help"), ModItems.scrollGuardHelp);

        ModItems.scrollHighLight = new ItemScrollHighlight(props("scroll_highlight").stacksTo(16));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scroll_highlight"), ModItems.scrollHighLight);

        ModItems.santaHat = new Item(props("santa_hat").humanoidArmor(SANTA_HAT, ArmorType.HELMET)
                                 .component(DataComponents.UNBREAKABLE, Unit.INSTANCE));
        // Port note (26.2): block items no longer inherit their description id from the block, so the three block
        // items built here need useBlockDescriptionPrefix() to keep their block.minecolonies.* translation keys.
        ModItems.irongate = new ItemGate(IRON_GATE, ModBlocks.blockIronGate, props(IRON_GATE).useBlockDescriptionPrefix());
        ModItems.woodgate = new ItemGate(WOODEN_GATE, ModBlocks.blockWoodenGate, props(WOODEN_GATE).useBlockDescriptionPrefix());

        ModItems.flagBanner = new ItemColonyFlagBanner("colony_banner", props("colony_banner").useBlockDescriptionPrefix());
        ModItems.pirateHelmet_1 = new Item(props("pirate_hat").humanoidArmor(PIRATE_ARMOR_1, ArmorType.HELMET).durability(350));
        ModItems.pirateChest_1 = new Item(props("pirate_top").humanoidArmor(PIRATE_ARMOR_1, ArmorType.CHESTPLATE).durability(550));
        ModItems.pirateLegs_1 = new Item(props("pirate_leggins").humanoidArmor(PIRATE_ARMOR_1, ArmorType.LEGGINGS).durability(500));
        ModItems.pirateBoots_1 = new Item(props("pirate_boots").humanoidArmor(PIRATE_ARMOR_1, ArmorType.BOOTS).durability(400));

        ModItems.pirateHelmet_2 = new Item(props("pirate_cap").humanoidArmor(PIRATE_ARMOR_2, ArmorType.HELMET).durability(200));
        ModItems.pirateChest_2 = new Item(props("pirate_chest").humanoidArmor(PIRATE_ARMOR_2, ArmorType.CHESTPLATE).durability(350));
        ModItems.pirateLegs_2 = new Item(props("pirate_legs").humanoidArmor(PIRATE_ARMOR_2, ArmorType.LEGGINGS).durability(300));
        ModItems.pirateBoots_2 = new Item(props("pirate_shoes").humanoidArmor(PIRATE_ARMOR_2, ArmorType.BOOTS).durability(250));

        ModItems.plateArmorHelmet = new Item(props("plate_armor_helmet").humanoidArmor(PLATE_ARMOR, ArmorType.HELMET).durability(350));
        ModItems.plateArmorChest = new Item(props("plate_armor_chest").humanoidArmor(PLATE_ARMOR, ArmorType.CHESTPLATE).durability(500));
        ModItems.plateArmorLegs = new Item(props("plate_armor_legs").humanoidArmor(PLATE_ARMOR, ArmorType.LEGGINGS).durability(450));
        ModItems.plateArmorBoots = new Item(props("plate_armor_boots").humanoidArmor(PLATE_ARMOR, ArmorType.BOOTS).durability(400));

        ModItems.sifterMeshString = new ItemSifterMesh("sifter_mesh_string", props("sifter_mesh_string").durability(500));
        ModItems.sifterMeshFlint = new ItemSifterMesh("sifter_mesh_flint", props("sifter_mesh_flint").durability(1000));
        ModItems.sifterMeshIron = new ItemSifterMesh("sifter_mesh_iron", props("sifter_mesh_iron").durability(1500));
        ModItems.sifterMeshDiamond = new ItemSifterMesh("sifter_mesh_diamond", props("sifter_mesh_diamond").durability(2000));

        ModItems.magicpotion = new ItemMagicPotion("magicpotion", props("magicpotion"));
        ModItems.buildGoggles = new ItemBuildGoggles("build_goggles", props("build_goggles"));
        ModItems.scanAnalyzer = new ItemScanAnalyzer("scan_analyzer", props("scan_analyzer"));
        ModItems.colonyMap = new ItemColonyMap(props("colonymap"));

        // All Biomes
        // Tier 1 Food Items
        ModItems.cheddar_cheese = new ItemFood((props("cheddar_cheese")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);
        ModItems.feta_cheese = new ItemFood((props("feta_cheese")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);
        ModItems.cooked_rice = new ItemFood((props("cooked_rice")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()).usingConvertsTo(Items.BOWL), 1);
        ModItems.tofu = new ItemFood((props("tofu")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);
        ModItems.flatbread = new ItemFood((props("flatbread")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);
        ModItems.cheese_ravioli = new ItemFood((props("cheese_ravioli")).food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.1F).build()), 1);
        ModItems.chicken_broth = new ItemFood((props("chicken_broth")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);
        ModItems.meat_ravioli = new ItemFood((props("meat_ravioli")).food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.1F).build()), 1);
        ModItems.mint_jelly = new ItemFood((props("mint_jelly")).food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.1F).build()), 1);
        ModItems.mint_tea = new ItemFood((props("mint_tea")).food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.1F).build()), 1);
        ModItems.polenta = new ItemFood((props("polenta")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);
        ModItems.potato_soup = new ItemFood((props("potato_soup")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);
        ModItems.veggie_ravioli = new ItemFood((props("veggie_ravioli")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);
        ModItems.yogurt = new ItemFood((props("yogurt")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);
        ModItems.manchet_bread = new ItemFood((props("manchet_bread")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);

        // Tier 2 Food Items
        ModItems.lembas_scone = new ItemFood((props("lembas_scone")).food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.25F).build()), 2);
        ModItems.muffin = new ItemFood((props("muffin")).food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.25F).build()), 2);
        ModItems.pottage = new ItemFood((props("pottage")).food(new FoodProperties.Builder().nutrition(9).saturationModifier(0.25F).build()).usingConvertsTo(Items.BOWL), 2);
        ModItems.pasta_plain = new ItemFood((props("pasta_plain")).food(new FoodProperties.Builder().nutrition(11).saturationModifier(0.25F).build()).usingConvertsTo(Items.BOWL), 2);
        ModItems.apple_pie = new ItemFood((props("apple_pie")).food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.25F).build()), 2);
        ModItems.plain_cheesecake = new ItemFood((props("plain_cheesecake")).food(new FoodProperties.Builder().nutrition(11).saturationModifier(0.25F).build()), 2);
        ModItems.baked_salmon = new ItemFood((props("baked_salmon")).food(new FoodProperties.Builder().nutrition(9).saturationModifier(0.25F).build()), 2);
        ModItems.eggdrop_soup = new ItemFood((props("eggdrop_soup")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 2);
        ModItems.fish_n_chips = new ItemFood((props("fish_n_chips")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 2);
        ModItems.pierogi = new ItemFood((props("pierogi")).food(new FoodProperties.Builder().nutrition(11).saturationModifier(0.25F).build()), 2);
        ModItems.veggie_soup = new ItemFood((props("veggie_soup")).food(new FoodProperties.Builder().nutrition(11).saturationModifier(0.25F).build()), 2);
        ModItems.yogurt_with_berries = new ItemFood((props("yogurt_with_berries")).food(new FoodProperties.Builder().nutrition(9).saturationModifier(0.25F).build()).usingConvertsTo(Items.BOWL), 2);
        ModItems.borscht = new ItemFood((props("borscht")).food(new FoodProperties.Builder().nutrition(9).saturationModifier(0.25F).build()), 2);

        // Tier 3 Food items
        ModItems.hand_pie = new ItemFood((props("hand_pie")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 3);
        ModItems.mintchoco_cheesecake = new ItemFood((props("mintchoco_cheesecake")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 3);
        ModItems.schnitzel = new ItemFood((props("schnitzel")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 3);
        ModItems.steak_dinner = new ItemFood((props("steak_dinner")).food(new FoodProperties.Builder().nutrition(12).saturationModifier(0.25F).build()), 3);

        // Cold Biomes
        // Tier 1
        ModItems.squash_soup = new ItemFood((props("squash_soup")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);
        // Tier 2
        ModItems.cabochis = new ItemFood((props("cabochis")).food(new FoodProperties.Builder().nutrition(11).saturationModifier(0.25F).build()).usingConvertsTo(Items.BOWL), 2);
        ModItems.veggie_quiche = new ItemFood((props("veggie_quiche")).food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.25F).build()), 2);
        // Tier 3
        ModItems.lamb_stew = new ItemFood((props("lamb_stew")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()).usingConvertsTo(Items.BOWL), 3);
        ModItems.fish_dinner = new ItemFood((props("fish_dinner")).food(new FoodProperties.Builder().nutrition(12).saturationModifier(0.25F).build()), 3);

        // Hot Humid Biomes
        // Tier 1
        ModItems.pea_soup = new ItemFood((props("pea_soup")).food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.1F).build()), 1);
        // Tier 2
        ModItems.rice_ball = new ItemFood((props("rice_ball")).food(new FoodProperties.Builder().nutrition(9).saturationModifier(0.25F).build()), 2);
        ModItems.mutton_dinner = new ItemFood((props("mutton_dinner")).food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.25F).build()), 2);
        // Tier 3
        ModItems.sushi_roll = new ItemFood((props("sushi_roll")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 3);
        ModItems.ramen = new ItemFood((props("ramen")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 3);
        ModItems.fried_rice = new ItemFood((props("fried_rice")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 3);

        // Temperate Biomes
        // Tier 1
        ModItems.corn_chowder = new ItemFood((props("corn_chowder")).food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.1F).build()), 1);
        ModItems.tortillas = new ItemFood((props("tortillas")).food(new FoodProperties.Builder().nutrition(6).saturationModifier(0.1F).build()), 1);
        // Tier 2
        ModItems.pasta_tomato = new ItemFood((props("pasta_tomato")).food(new FoodProperties.Builder().nutrition(11).saturationModifier(0.25F).build()).usingConvertsTo(Items.BOWL), 2);
        ModItems.cheese_pizza = new ItemFood((props("cheese_pizza")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 2);
        // Tier 3
        ModItems.eggplant_dolma = new ItemFood((props("eggplant_dolma")).food(new FoodProperties.Builder().nutrition(12).saturationModifier(0.25F).build()), 3);
        ModItems.stuffed_pita = new ItemFood((props("stuffed_pita")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 3);
        ModItems.mushroom_pizza = new ItemFood((props("mushroom_pizza")).food(new FoodProperties.Builder().nutrition(12).saturationModifier(0.25F).build()), 3);

        // Hot Dry Biomes
        // Tier 1
        ModItems.spicy_grilled_chicken = new ItemFood((props("spicy_grilled_chicken")).food(new FoodProperties.Builder().nutrition(7).saturationModifier(0.1F).build()), 1);
        // Tier 2
        ModItems.pepper_hummus = new ItemFood((props("pepper_hummus")).food(new FoodProperties.Builder().nutrition(9).saturationModifier(0.25F).build()), 2);
        ModItems.kebab = new ItemFood((props("kebab")).food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.25F).build()), 2);
        // Tier 3
        ModItems.pita_hummus = new ItemFood((props("pita_hummus")).food(new FoodProperties.Builder().nutrition(12).saturationModifier(0.25F).build()), 3);
        ModItems.spicy_eggplant = new ItemFood((props("spicy_eggplant")).food(new FoodProperties.Builder().nutrition(12).saturationModifier(0.25F).build()), 3);

        // Require trading
        // Tier 2
        ModItems.congee = new ItemFood((props("congee")).food(new FoodProperties.Builder().nutrition(9).saturationModifier(0.25F).build()).usingConvertsTo(Items.BOWL), 2);
        ModItems.kimchi = new ItemFood((props("kimchi")).food(new FoodProperties.Builder().nutrition(11).saturationModifier(0.25F).build()), 2);
        // Tier 3
        ModItems.stew_trencher = new ItemFood((props("stew_trencher")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 3);
        ModItems.stuffed_pepper = new ItemFood((props("stuffed_pepper")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 3);
        ModItems.tacos = new ItemFood((props("tacos")).food(new FoodProperties.Builder().nutrition(13).saturationModifier(0.25F).build()), 3);

        // Just dough
        ModItems.muffin_dough = new Item((props("muffin_dough")));
        ModItems.manchet_dough = new Item((props("manchet_dough")));
        ModItems.raw_noodle = new Item((props("raw_noodle")));
        ModItems.butter = new Item((props("butter")));
        ModItems.cornmeal = new Item((props("cornmeal")));
        ModItems.creamcheese = new Item((props("creamcheese")));
        ModItems.soysauce = new Item((props("soysauce")));

        // Port note (26.2): Item.Properties#craftRemainder now stores an ItemStackTemplate, which resolves the
        // remainder item's registry holder right away ("Trying to access unbound value" otherwise). The empty
        // bottle therefore has to be registered before the filled bottles below are constructed -- its entry
        // from the bulk registration block further down moved up here.
        ModItems.large_empty_bottle = Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "large_empty_bottle"),
          new ItemLargeBottle(props("large_empty_bottle")));
        ModItems.large_milk_bottle = new ItemLargeBottle((props("large_milk_bottle").craftRemainder(ModItems.large_empty_bottle)));
        ModItems.large_water_bottle = new ItemLargeBottle((props("large_water_bottle").craftRemainder(ModItems.large_empty_bottle)));
        ModItems.large_soy_milk_bottle = new ItemLargeBottle((props("large_soy_milk_bottle").craftRemainder(ModItems.large_empty_bottle)));

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "supplychestdeployer"), ModItems.supplyChest);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scan_analyzer"), ModItems.scanAnalyzer);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scepterpermission"), ModItems.permTool);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scepterguard"), ModItems.scepterGuard);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scepterclaim"), ModItems.scepterClaim);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scepterunclaim"), ModItems.scepterUnclaim);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scepterborder"), ModItems.scepterBorder);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scepterterritory"), ModItems.scepterTerritory);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "banner_rally_guards"), ModItems.bannerRallyGuards);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "supplycampdeployer"), ModItems.supplyCamp);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "ancienttome"), ModItems.ancientTome);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "chiefsword"), ModItems.chiefSword);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "clipboard"), ModItems.clipboard);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "compost"), ModItems.compost);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "resourcescroll"), ModItems.resourceScroll);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "iron_scimitar"), ModItems.scimitar);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scepterlumberjack"), ModItems.scepterLumberjack);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pharaoscepter"), ModItems.pharaoscepter);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "firearrow"), ModItems.firearrow);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "scepterbeekeeper"), ModItems.scepterBeekeeper);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "fieldstick"), ModItems.fieldStick);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mistletoe"), ModItems.mistletoe);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "spear"), ModItems.spear);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "questlog"), ModItems.questLog);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "colonymap"), ModItems.colonyMap);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "assistanthammer_gold"), ModItems.assistantHammer_Gold);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "assistanthammer_iron"), ModItems.assistantHammer_Iron);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "assistanthammer_diamond"), ModItems.assistantHammer_Diamond);

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "bread_dough"), ModItems.breadDough);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cookie_dough"), ModItems.cookieDough);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cake_batter"), ModItems.cakeBatter);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "raw_pumpkin_pie"), ModItems.rawPumpkinPie);

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "milky_bread"), ModItems.milkyBread);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sugary_bread"), ModItems.sugaryBread);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "golden_bread"), ModItems.goldenBread);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "chorus_bread"), ModItems.chorusBread);

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "adventure_token"), ModItems.adventureToken);

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pirate_hat"), ModItems.pirateHelmet_1);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pirate_top"), ModItems.pirateChest_1);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pirate_leggins"), ModItems.pirateLegs_1);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pirate_boots"), ModItems.pirateBoots_1);

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pirate_cap"), ModItems.pirateHelmet_2);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pirate_chest"), ModItems.pirateChest_2);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pirate_legs"), ModItems.pirateLegs_2);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pirate_shoes"), ModItems.pirateBoots_2);

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "plate_armor_helmet"), ModItems.plateArmorHelmet);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "plate_armor_chest"), ModItems.plateArmorChest);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "plate_armor_legs"), ModItems.plateArmorLegs);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "plate_armor_boots"), ModItems.plateArmorBoots);


        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "santa_hat"), ModItems.santaHat);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, IRON_GATE), ModItems.irongate);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, WOODEN_GATE), ModItems.woodgate);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "colony_banner"), ModItems.flagBanner);


        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sifter_mesh_string"), ModItems.sifterMeshString);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sifter_mesh_flint"), ModItems.sifterMeshFlint);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sifter_mesh_iron"), ModItems.sifterMeshIron);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sifter_mesh_diamond"), ModItems.sifterMeshDiamond);

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "magicpotion"), ModItems.magicpotion);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "build_goggles"), ModItems.buildGoggles);

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "butter"), ModItems.butter);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cabochis"), ModItems.cabochis);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cheddar_cheese"), ModItems.cheddar_cheese);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "congee"), ModItems.congee);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cooked_rice"), ModItems.cooked_rice);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "eggplant_dolma"), ModItems.eggplant_dolma);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "feta_cheese"), ModItems.feta_cheese);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "flatbread"), ModItems.flatbread);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "hand_pie"), ModItems.hand_pie);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "lamb_stew"), ModItems.lamb_stew);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "lembas_scone"), ModItems.lembas_scone);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "manchet_bread"), ModItems.manchet_bread);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "manchet_dough"), ModItems.manchet_dough);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "muffin"), ModItems.muffin);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "muffin_dough"), ModItems.muffin_dough);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pasta_plain"), ModItems.pasta_plain);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pasta_tomato"), ModItems.pasta_tomato);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pepper_hummus"), ModItems.pepper_hummus);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pita_hummus"), ModItems.pita_hummus);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pottage"), ModItems.pottage);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "raw_noodle"), ModItems.raw_noodle);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "rice_ball"), ModItems.rice_ball);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stew_trencher"), ModItems.stew_trencher);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stuffed_pepper"), ModItems.stuffed_pepper);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stuffed_pita"), ModItems.stuffed_pita);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sushi_roll"), ModItems.sushi_roll);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "tofu"), ModItems.tofu);

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cheese_ravioli"), ModItems.cheese_ravioli);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "chicken_broth"), ModItems.chicken_broth);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "corn_chowder"), ModItems.corn_chowder);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "spicy_grilled_chicken"), ModItems.spicy_grilled_chicken);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "kebab"), ModItems.kebab);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "meat_ravioli"), ModItems.meat_ravioli);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mint_jelly"), ModItems.mint_jelly);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mint_tea"), ModItems.mint_tea);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pea_soup"), ModItems.pea_soup);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "polenta"), ModItems.polenta);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "potato_soup"), ModItems.potato_soup);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "squash_soup"), ModItems.squash_soup);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "veggie_ravioli"), ModItems.veggie_ravioli);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "yogurt"), ModItems.yogurt);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "baked_salmon"), ModItems.baked_salmon);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "eggdrop_soup"), ModItems.eggdrop_soup);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "fish_n_chips"), ModItems.fish_n_chips);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "kimchi"), ModItems.kimchi);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pierogi"), ModItems.pierogi);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "veggie_quiche"), ModItems.veggie_quiche);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "veggie_soup"), ModItems.veggie_soup);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "yogurt_with_berries"), ModItems.yogurt_with_berries);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "borscht"), ModItems.borscht);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "fish_dinner"), ModItems.fish_dinner);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mutton_dinner"), ModItems.mutton_dinner);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "ramen"), ModItems.ramen);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "fried_rice"), ModItems.fried_rice);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "schnitzel"), ModItems.schnitzel);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "steak_dinner"), ModItems.steak_dinner);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "tacos"), ModItems.tacos);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cornmeal"), ModItems.cornmeal);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "creamcheese"), ModItems.creamcheese);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "soysauce"), ModItems.soysauce);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "tortillas"), ModItems.tortillas);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "apple_pie"), ModItems.apple_pie);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cheese_pizza"), ModItems.cheese_pizza);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mushroom_pizza"), ModItems.mushroom_pizza);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "plain_cheesecake"), ModItems.plain_cheesecake);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mintchoco_cheesecake"), ModItems.mintchoco_cheesecake);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "spicy_eggplant"), ModItems.spicy_eggplant);

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "large_water_bottle"), ModItems.large_water_bottle);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "large_milk_bottle"), ModItems.large_milk_bottle);
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "large_soy_milk_bottle"), ModItems.large_soy_milk_bottle);

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "barbarianegg"),
          new SpawnEggItem(props("barbarianegg").spawnEgg(ModEntities.CAMP_BARBARIAN)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "barbarcheregg"),
          new SpawnEggItem(props("barbarcheregg").spawnEgg(ModEntities.CAMP_ARCHERBARBARIAN)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "barbchiefegg"),
          new SpawnEggItem(props("barbchiefegg").spawnEgg(ModEntities.CAMP_CHIEFBARBARIAN)));

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pirateegg"),
          new SpawnEggItem(props("pirateegg").spawnEgg(ModEntities.CAMP_PIRATE)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "piratearcheregg"),
          new SpawnEggItem(props("piratearcheregg").spawnEgg(ModEntities.CAMP_ARCHERPIRATE)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "piratecaptainegg"),
          new SpawnEggItem(props("piratecaptainegg").spawnEgg(ModEntities.CAMP_CHIEFPIRATE)));

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mummyegg"),
          new SpawnEggItem(props("mummyegg").spawnEgg(ModEntities.CAMP_MUMMY)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mummyarcheregg"),
          new SpawnEggItem(props("mummyarcheregg").spawnEgg(ModEntities.CAMP_ARCHERMUMMY)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pharaoegg"),
          new SpawnEggItem(props("pharaoegg").spawnEgg(ModEntities.CAMP_PHARAO)));

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "shieldmaidenegg"),
          new SpawnEggItem(props("shieldmaidenegg").spawnEgg(ModEntities.CAMP_SHIELDMAIDEN)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "norsemenarcheregg"),
          new SpawnEggItem(props("norsemenarcheregg").spawnEgg(ModEntities.CAMP_NORSEMEN_ARCHER)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "norsemenchiefegg"),
          new SpawnEggItem(props("norsemenchiefegg").spawnEgg(ModEntities.CAMP_NORSEMEN_CHIEF)));

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "amazonegg"),
          new SpawnEggItem(props("amazonegg").spawnEgg(ModEntities.CAMP_AMAZON)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "amazonspearmanegg"),
          new SpawnEggItem(props("amazonspearmanegg").spawnEgg(ModEntities.CAMP_AMAZONSPEARMAN)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "amazonchiefegg"),
          new SpawnEggItem(props("amazonchiefegg").spawnEgg(ModEntities.CAMP_AMAZONCHIEF)));

        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "drownedpirateegg"),
          new SpawnEggItem(props("drownedpirateegg").spawnEgg(ModEntities.CAMP_DROWNED_PIRATE)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "drownedpiratearcheregg"),
          new SpawnEggItem(props("drownedpiratearcheregg").spawnEgg(ModEntities.CAMP_DROWNED_ARCHERPIRATE)));
        Registry.register(registry, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "drownedpiratecaptainegg"),
          new SpawnEggItem(props("drownedpiratecaptainegg").spawnEgg(ModEntities.CAMP_DROWNED_CHIEFPIRATE)));
    }

    /**
     * Armour materials.
     * <p>
     * <b>Port note.</b> 26.2 deleted {@code Registries.ARMOR_MATERIAL} and {@code ArmorItem} entirely: armour is
     * an ordinary {@link Item} carrying the {@code EQUIPPABLE} data component, and {@link ArmorMaterial} is a
     * plain record that is never registered (see {@code net/minecraft/world/item/equipment/ArmorMaterials.java}).
     * The {@code DeferredRegister<ArmorMaterial>} is therefore gone and these are constants.
     * <p>
     * Two fields changed meaning: the old {@code List<ArmorMaterial.Layer>} became a single
     * {@code ResourceKey<EquipmentAsset>} pointing at {@code assets/minecolonies/equipment/<name>.json}, which
     * datagen has to emit, and {@code Supplier<Ingredient> repairIngredient} became a {@code TagKey<Item>}.
     * "Not repairable" is expressed with {@link #NO_REPAIR}, a tag that is deliberately never populated.
     */
    private static final TagKey<Item> NO_REPAIR =
      TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "no_repair_material"));

    public static final ArmorMaterial SANTA_HAT = new ArmorMaterial(
      1,
      Map.of(ArmorType.BOOTS, 0, ArmorType.LEGGINGS, 0, ArmorType.CHESTPLATE, 0, ArmorType.HELMET, 0),
      500,
      SoundEvents.ARMOR_EQUIP_LEATHER,
      0,
      0,
      NO_REPAIR,
      equipmentAsset("santa_hat"));

    public static final ArmorMaterial PLATE_ARMOR = new ArmorMaterial(
      31,
      Map.of(ArmorType.BOOTS, 3, ArmorType.LEGGINGS, 6, ArmorType.CHESTPLATE, 8, ArmorType.HELMET, 3),
      37,
      SoundEvents.ARMOR_EQUIP_IRON,
      0,
      0,
      ItemTags.REPAIRS_IRON_ARMOR,
      equipmentAsset("plate_armor"));

    public static final ArmorMaterial GOGGLES = new ArmorMaterial(
      1,
      Map.of(ArmorType.BOOTS, 0, ArmorType.LEGGINGS, 0, ArmorType.CHESTPLATE, 0, ArmorType.HELMET, 0),
      20,
      SoundEvents.ARMOR_EQUIP_LEATHER,
      0,
      0,
      NO_REPAIR,
      equipmentAsset("build_goggles"));

    public static final ArmorMaterial PIRATE_ARMOR_1 = new ArmorMaterial(
      31,
      Map.of(ArmorType.BOOTS, 2, ArmorType.LEGGINGS, 5, ArmorType.CHESTPLATE, 6, ArmorType.HELMET, 2),
      5,
      SoundEvents.ARMOR_EQUIP_LEATHER,
      0,
      0,
      ItemTags.REPAIRS_DIAMOND_ARMOR,
      equipmentAsset("pirate"));

    public static final ArmorMaterial PIRATE_ARMOR_2 = new ArmorMaterial(
      31,
      Map.of(ArmorType.BOOTS, 3, ArmorType.LEGGINGS, 6, ArmorType.CHESTPLATE, 8, ArmorType.HELMET, 3),
      5,
      SoundEvents.ARMOR_EQUIP_LEATHER,
      2,
      0,
      ItemTags.REPAIRS_DIAMOND_ARMOR,
      equipmentAsset("pirate2"));

    /**
     * @param name the equipment asset path inside the minecolonies namespace.
     * @return the key of the equipment asset json describing the armour layers.
     */
    private static ResourceKey<EquipmentAsset> equipmentAsset(final String name)
    {
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Constants.MOD_ID, name));
    }

    /**
     * Builds item properties with the mandatory item id already stamped on them.
     * <p>
     * Port note: {@link Item.Properties#setId(ResourceKey)} is not optional in 26.2 -- {@code new Item(...)}
     * throws {@code Item id not set} without it -- so every item in this class is built through here.
     *
     * @param name the registry path of the item inside the minecolonies namespace.
     * @return fresh properties carrying the item's id.
     */
    private static Item.Properties props(final String name)
    {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, name)));
    }
}
