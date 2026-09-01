package com.minecolonies.api.equipment;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.compatibility.Compatibility;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.translation.ToolTranslationConstants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.minecolonies.api.util.constant.EquipmentLevelConstants.BASIC_TOOL_LEVEL;

/**
 * Class used for storing and registering any EquipmentTypes.
 */
public class ModEquipmentTypes
{

    public static final Supplier<EquipmentTypeEntry> none;
    public static final Supplier<EquipmentTypeEntry> pickaxe;
    public static final Supplier<EquipmentTypeEntry> shovel;
    public static final Supplier<EquipmentTypeEntry> axe;
    public static final Supplier<EquipmentTypeEntry> hoe;
    public static final Supplier<EquipmentTypeEntry> sword;
    public static final Supplier<EquipmentTypeEntry> bow;
    public static final Supplier<EquipmentTypeEntry> fishing_rod;
    public static final Supplier<EquipmentTypeEntry> shears;
    public static final Supplier<EquipmentTypeEntry> shield;
    public static final Supplier<EquipmentTypeEntry> helmet;
    public static final Supplier<EquipmentTypeEntry> leggings;
    public static final Supplier<EquipmentTypeEntry> chestplate;
    public static final Supplier<EquipmentTypeEntry> boots;
    public static final Supplier<EquipmentTypeEntry> flint_and_steel;
    public static final Supplier<EquipmentTypeEntry> lead;
    public static final Supplier<EquipmentTypeEntry> spear;
    public static final Supplier<EquipmentTypeEntry> crossbow;

    static
    {
        none = register("none",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_NONE))
                       .setIsEquipment((itemStack, equipmentType) -> true)
                       .setEquipmentLevel((itemStack, equipmentType) -> -1)
                   .build());

        pickaxe = register("pickaxe",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_PICKAXE))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.is(ItemTags.PICKAXES) || Compatibility.isTinkersTool(
                         itemStack,
                         equipmentType))
                       .setEquipmentLevel(ModEquipmentTypes::vanillaToolLevel)
                  .build());

        shovel = register("shovel",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_SHOVEL))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.is(ItemTags.SHOVELS) || Compatibility.isTinkersTool(
                         itemStack,
                         equipmentType))
                       .setEquipmentLevel(ModEquipmentTypes::vanillaToolLevel)
                  .build());

        axe = register("axe",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_AXE))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.is(ItemTags.AXES) || Compatibility.isTinkersTool(itemStack,
                         equipmentType))
                       .setEquipmentLevel(ModEquipmentTypes::vanillaToolLevel)
                  .build());

        hoe = register("hoe",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_HOE))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.is(ItemTags.HOES) || Compatibility.isTinkersTool(itemStack,
                         equipmentType))
                       .setEquipmentLevel(ModEquipmentTypes::vanillaToolLevel)
                  .build());

        sword = register("sword",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_SWORD))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.is(ItemTags.SWORDS)
                                                                     || Compatibility.isTinkersWeapon(itemStack)
                                                                     || Compatibility.isCustomWeapon(itemStack))
                       .setEquipmentLevel(ModEquipmentTypes::vanillaToolLevel)
                  .build());

        bow = register("bow",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_BOW))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof BowItem)
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        crossbow = register("crossbow",
            builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_CROSSBOW))
                .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof CrossbowItem)
                .setEquipmentLevel((itemStack, equipmentType) -> durabilityBasedLevel(itemStack, new ItemStack(Items.CROSSBOW).getMaxDamage()))
                .build());

        fishing_rod = register("rod",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_FISHING_ROD))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof FishingRodItem)
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        shears = register("shears",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_SHEARS))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof ShearsItem)
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        shield = register("shield",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_SHIELD))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof ShieldItem)
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        helmet = register("helmet",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_HELMET))
                       .setIsEquipment((itemStack, equipmentType) -> isEquippableIn(itemStack, EquipmentSlot.HEAD))
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        leggings = register("leggings",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_LEGGINGS))
                       .setIsEquipment((itemStack, equipmentType) -> isEquippableIn(itemStack, EquipmentSlot.LEGS))
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        chestplate = register("chestplate",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_CHEST_PLATE))
                       .setIsEquipment((itemStack, equipmentType) -> isEquippableIn(itemStack, EquipmentSlot.CHEST))
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        boots = register("boots",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_BOOTS))
                       .setIsEquipment((itemStack, equipmentType) -> isEquippableIn(itemStack, EquipmentSlot.FEET))
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        flint_and_steel = register("flintandsteel",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_LIGHTER))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof FlintAndSteelItem)
                       .setEquipmentLevel((itemStack, equipmentType) -> Compatibility.getItemLevel(itemStack))
                  .build());

        lead = register("lead",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_LEAD))
                       .setIsEquipment((itemStack, equipmentType) -> itemStack.getItem() instanceof LeadItem)
                       .setEquipmentLevel((itemStack, equipmentType) -> -1)
                  .build());

        spear = register("spear",
          builder -> builder.setDisplayName(Component.translatable(ToolTranslationConstants.TOOL_TYPE_SPEAR))
                      // 26.2 ships seven spears of its own (wood through netherite) and a #minecraft:spears tag to
                      // hold them, so a cavalryman is no longer restricted to the mod's one.
                      .setIsEquipment((itemStack, equipmentType) -> itemStack.is(ModItems.spear) || itemStack.is(ItemTags.SPEARS))
                      .setEquipmentLevel((itemStack, equipmentType) -> spearLevel(itemStack))
                  .build());

    }

    /**
     * Get the equipmentType registry.
     *
     * @return The equipmentType registry
     */
    public static Registry<EquipmentTypeEntry> getRegistry()
    {
        return IMinecoloniesAPI.getInstance().getEquipmentTypeRegistry();
    }

    /**
     * Register a new equipmentType to the registry.
     *
     * @param id The unique ID of the equipment type
     * @param consumer The consumer that builds the equipment type
     * @return The registry entry
     */
    private static Supplier<EquipmentTypeEntry> register(final String id, final Consumer<EquipmentTypeEntry.Builder> consumer)
    {
        EquipmentTypeEntry.Builder equipmentType = new EquipmentTypeEntry.Builder()
                                           .setRegistryName(Identifier.fromNamespaceAndPath(Constants.MOD_ID, id));
        consumer.accept(equipmentType);
        final EquipmentTypeEntry value = Registry.register(getRegistry(), Identifier.fromNamespaceAndPath(Constants.MOD_ID, id), equipmentType.build());
        return () -> value;
    }

    /**
     * Get the equipment level for vanilla tools.
     *
     * @param equipmentType  The type of vanilla tool
     * @param itemStack The item stack to check
     * @return The tool level
     */
    public static int vanillaToolLevel(final ItemStack itemStack, final EquipmentTypeEntry equipmentType)
    {
        if (Compatibility.isTinkersTool(itemStack, equipmentType) || Compatibility.isTinkersWeapon(itemStack))
        {
            return Compatibility.getToolLevel(itemStack);
        }
        return Compatibility.getItemLevel(itemStack);
    }

    /**
     * The equipment level of a spear.
     * <p>
     * Spears used to be scored on durability against the mod spear's 250, which is not a measure of how well a spear
     * fights and gave a scale with no middle: gold, wooden, stone and copper spears all came out at 0, iron at 1,
     * and diamond and netherite both jumped to 5. Hut levels 2, 3 and 4 licensed nothing a level-1 hut did not
     * already licence, and a diamond spear was refused until the hut was maxed while a diamond *sword* was allowed
     * at level 3. Scoring off the material's attack bonus, exactly as swords and every other tool are scored, gives
     * wood and gold 0, stone and copper 1, iron 2, diamond 3 and netherite 4 -- one rung per hut level.
     * <p>
     * The mod's own spear is pinned rather than measured. Its durability is the iron spear's, so the material match
     * below would call it iron; keeping it at the basic tool level is what it has always scored and what lets a
     * level-one guard tower arm a spearman with the spear the player can actually craft at that point.
     *
     * @param itemStack the spear.
     * @return the equipment level.
     */
    public static int spearLevel(final ItemStack itemStack)
    {
        if (itemStack.is(ModItems.spear))
        {
            return BASIC_TOOL_LEVEL;
        }

        final ToolMaterial material = toolMaterialOf(itemStack);
        if (material != null)
        {
            return (int) material.attackDamageBonus();
        }

        return durabilityBasedLevel(itemStack, new ItemStack(ModItems.spear).getMaxDamage());
    }

    /**
     * Get the durability based item level.
     *
     * @param itemStack The item stack to check
     * @return The item level
     */
    public static int durabilityBasedLevel(ItemStack itemStack, int vanillaItemDurability)
    {
        if (!itemStack.isDamageableItem())
        {
            return 5;
        }

        return Math.min(itemStack.getMaxDamage() / vanillaItemDurability, 5);
    }

    /**
     * Populate the tier registry with every item currently in the game.
     * Called once during FMLCommonSetupEvent via MineColonies.preInit.
     */
    @SuppressWarnings("null")
    public static void initRegisterEquipmentTiers()
    {
        int bowRef    = 0;
        int rodRef    = 0;
        int shearsRef = 0;
        int shieldRef = 0;
        int flintRef  = 0;
        try
        {
            bowRef    = new ItemStack(Items.BOW).getMaxDamage();
            rodRef    = new ItemStack(Items.FISHING_ROD).getMaxDamage();
            shearsRef = new ItemStack(Items.SHEARS).getMaxDamage();
            shieldRef = new ItemStack(Items.SHIELD).getMaxDamage();
            flintRef  = new ItemStack(Items.FLINT_AND_STEEL).getMaxDamage();
        }
        catch (Exception e)
        {
            // In case something goes wrong with fetching durability references, we can still continue and just won't have durability based tiers for those items.
            Log.getLogger().error("Failed to fetch getMaxDamage references for equipment tier registration, durability based tiers for certain items will not be registered.", e);
            return;
        }
        

        for (final Item item : BuiltInRegistries.ITEM)
        {
            try
            {
                final ItemStack dummy = new ItemStack(item);

                final ToolMaterial material = toolMaterialOf(dummy);
                if (material != null)
                {
                    Compatibility.registerItemTierIfAbsent(item, material, (int) material.attackDamageBonus());
                }
                else if (dummy.get(DataComponents.EQUIPPABLE) != null)
                {
                    final int level = ItemStackUtils.getArmorLevel(dummy);
                    if (level > 0)
                    {
                        Compatibility.registerItemTierIfAbsent(item, level);
                    }
                }
                else if (item instanceof BowItem)
                {
                    Compatibility.registerItemTierIfAbsent(item, durabilityBasedLevel(dummy, bowRef));
                }
                else if (item instanceof FishingRodItem)
                {
                    Compatibility.registerItemTierIfAbsent(item, durabilityBasedLevel(dummy, rodRef));
                }
                else if (item instanceof ShearsItem)
                {
                    Compatibility.registerItemTierIfAbsent(item, durabilityBasedLevel(dummy, shearsRef));
                }
                else if (item instanceof ShieldItem)
                {
                    Compatibility.registerItemTierIfAbsent(item, durabilityBasedLevel(dummy, shieldRef));
                }
                else if (item instanceof FlintAndSteelItem)
                {
                    Compatibility.registerItemTierIfAbsent(item, durabilityBasedLevel(dummy, flintRef));
                }
            }
            catch (Exception e)
            {
                Log.getLogger().error("Failed to register equipment tiers for item: " + BuiltInRegistries.ITEM.getKey(item), e);
            }
        }
    }

    /**
     * All vanilla tool materials, so an item can be matched back to one.
     */
    private static final ToolMaterial[] TOOL_MATERIALS = {
      ToolMaterial.WOOD, ToolMaterial.GOLD, ToolMaterial.STONE, ToolMaterial.COPPER,
      ToolMaterial.IRON, ToolMaterial.DIAMOND, ToolMaterial.NETHERITE
    };

    /**
     * Identify the tool material of a stack.
     * <p>
     * 26.2 removed {@code TieredItem} and {@code Item#getTier()}: a tool is now just an item carrying a {@code TOOL}
     * data component, and its {@link ToolMaterial} is not stored anywhere retrievable. The material is therefore
     * recovered by matching the item's durability against the vanilla materials, which is exact for every vanilla
     * tool and for modded tools built with {@code ToolMaterial#applyToolProperties}.
     *
     * @param stack the stack to identify.
     * @return the matching tool material, or null if the stack is not a recognised tool.
     */
    @Nullable
    public static ToolMaterial toolMaterialOf(final ItemStack stack)
    {
        if (!stack.is(ItemTags.PICKAXES) && !stack.is(ItemTags.AXES) && !stack.is(ItemTags.SHOVELS)
              && !stack.is(ItemTags.HOES) && !stack.is(ItemTags.SWORDS) && !stack.is(ItemTags.SPEARS))
        {
            return null;
        }

        final int durability = stack.getMaxDamage();
        for (final ToolMaterial material : TOOL_MATERIALS)
        {
            if (material.durability() == durability)
            {
                return material;
            }
        }
        return null;
    }

    /**
     * Determine whether a stack is equippable in a specific slot.
     * <p>
     * 26.2 dropped {@code ArmorItem}: what makes something armour is the {@code EQUIPPABLE} data component, and its
     * {@code slot()} is what {@code ArmorItem#getEquipmentSlot()} used to return.
     *
     * @param itemStack the stack to check.
     * @param slot      the slot in question.
     * @return whether the stack is worn in that slot.
     */
    public static boolean isEquippableIn(final ItemStack itemStack, final EquipmentSlot slot)
    {
        final Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == slot;
    }

    /**
     * Determine whether an item stack belongs to a given tool tag.
     * <p>
     * TODO(port-26.2): DISABLED — replaces NeoForge's {@code ItemAbility}/{@code ItemAbilities} tool-action system,
     * which has no counterpart in Fabric or vanilla 26.2. Vanilla asks the same question with item tags, so tool
     * detection is tag-based now: a modded pickaxe that never joins {@code #minecraft:pickaxes} is no longer seen as
     * a pickaxe by colonists.
     *
     * @param itemStack the item stack to check.
     * @param tag       the tool tag.
     * @return whether the stack is in the tag.
     */
    public static boolean canPerformDefaultActions(final ItemStack itemStack, final TagKey<Item> tag)
    {
        return itemStack.is(tag);
    }
}
