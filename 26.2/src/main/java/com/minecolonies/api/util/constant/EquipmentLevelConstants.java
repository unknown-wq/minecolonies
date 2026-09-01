package com.minecolonies.api.util.constant;

import org.jetbrains.annotations.NonNls;

/**
 * Constants for tool levels.
 */
public final class EquipmentLevelConstants
{
    /**
     * Tool level for hand.
     */
    @NonNls
    public static final int TOOL_LEVEL_HAND                = -1;

    /**
     * Tool level for gold or wood.
     */
    @NonNls
    public static final int TOOL_LEVEL_WOOD_OR_GOLD        = 0;

    /**
     * Tool level for stone.
     */
    @NonNls
    public static final int BASIC_TOOL_LEVEL        = 1;

    /**
     * Armor level for leather. 7 armour points, no toughness.
     * <p>
     * The armour ladder is ordered by the protection a full set actually gives, which is why it is not the same
     * order as the tool ladder and why gold does not sit beside leather: leather 7, copper 10, gold 11, chain 12,
     * iron 15, diamond 20 (+2 toughness), netherite 20 (+3 toughness). See
     * {@code ArmorMaterials} for the per-slot figures the totals are summed from.
     */
    @NonNls
    public static final int ARMOR_LEVEL_LEATHER = 1;

    /**
     * Armor level for copper. 10 armour points, no toughness.
     */
    @NonNls
    public static final int ARMOR_LEVEL_COPPER = 2;

    /**
     * Armor level for gold. 11 armour points, no toughness.
     */
    @NonNls
    public static final int ARMOR_LEVEL_GOLD = 3;

    /**
     * Armor level for chain. 12 armour points, no toughness.
     */
    @NonNls
    public static final int ARMOR_LEVEL_CHAIN = 4;

    /**
     * Armor level for iron. 15 armour points, no toughness.
     */
    @NonNls
    public static final int ARMOR_LEVEL_IRON = 5;

    /**
     * Armor level for diamond. 20 armour points and 2 toughness.
     */
    @NonNls
    public static final int ARMOR_LEVEL_DIAMOND = 6;

    /**
     * Armor level for netherite. 20 armour points and 3 toughness. The top rung an actual item can score; the
     * fallback for armour this code cannot identify is this level too.
     */
    @NonNls
    public static final int ARMOR_LEVEL_NETHERITE = 7;

    /**
     * Armor level ceiling, for a band with no upper bound.
     */
    @NonNls
    public static final int ARMOR_LEVEL_MAX = Integer.MAX_VALUE;

    /**
     * Tool level for maximum.
     */
    @NonNls
    public static final int TOOL_LEVEL_MAXIMUM = Integer.MAX_VALUE;

    private EquipmentLevelConstants()
    {
        //empty default
    }
}

