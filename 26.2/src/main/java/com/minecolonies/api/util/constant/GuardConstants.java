package com.minecolonies.api.util.constant;

import com.minecolonies.api.util.Tuple;

/**
 * Constants used by the Guard AIs.
 */
public final class GuardConstants
{
    /**
     * Default vision range.
     */
    public static final int DEFAULT_VISION = 16;

    /**
     * Range a guard should be within of GuardPos.
     */
    public static final int GUARD_POS_RANGE = 3;

    /**
     * Range a guard should be within of Follow for Tight Formation.
     */
    public static final int GUARD_FOLLOW_TIGHT_RANGE = 6;

    /**
     * Range a guard should be within of Follow for Lose Formation.
     */
    public static final int GUARD_FOLLOW_LOSE_RANGE = 15;

    /**
     * Y search range.
     */
    public static final int Y_VISION = 3;

    /**
     * Experience to add when a mob is killed
     */
    public static final int EXP_PER_MOB_DEATH = 15;

    // -- Delays -- \\

    /**
     * Seconds to delay after prepare AI State.
     */
    public static final int PREPARE_DELAY_SECONDS = 5;

    /**
     * Minimum physical Attack delay in ticks, Monsters are immune for 10 ticks
     */
    public static final int PHYSICAL_ATTACK_DELAY_MIN = 10;

    /**
     * Physical Attack delay in ticks.
     */
    public static final int PHYSICAL_ATTACK_DELAY_BASE = 20;

    // -- Delays -- \\

    // -- Ranged Guard Stuff -- \\

    /**
     * The base distance for an attack in Blocks.
     */
    public static final int BASE_DISTANCE_FOR_RANGED_ATTACK = 10;

    /**
     * The base distance for a potion throwing attack in Blocks.
     */
    public static final int BASE_DISTANCE_FOR_POTION_ATTACK = 3;

    /**
     * Rangers maximum distance in blocks for an attack.(24 max arrow dist)
     */
    public static final int MAX_DISTANCE_FOR_RANGED_ATTACK = 24;

    /**
     * Ranger's base damage
     */
    public static final int RANGER_BASE_DMG = 2;

    /**
     * Flee distance from Target
     */
    public static final int RANGED_FLEE_SQDIST = 7;

    /**
     * Ceiling on the fraction of a marksman's shot that bypasses armour.
     * <p>
     * The share itself is {@code (50 + Adaptability / 2) / 100}, so it reaches 0.995 at skill 99 and never 1. The
     * ceiling exists because the armour-bypassing part of the shot is reconstructed by dividing the arrow's carried
     * damage by {@code 1 - share}, and that division must not be allowed near zero if the formula above is ever
     * retuned.
     */
    public static final double MARKSMAN_MAX_TRUE_DAMAGE_SHARE = 0.99;

    /**
     * Ranged attack velocity
     */
    public static final float RANGED_VELOCITY = (float) 1.6D;

    /**
     * Physical Attack delay in ticks.
     */
    public static final int RANGED_ATTACK_DELAY_BASE = 60;

    /**
     * Ranged hit chance devider.
     */
    public static final float HIT_CHANCE_DIVIDER = 15.0F;

    /**
     * Have to aim that bit higher to hit the target.
     */
    public static final double RANGED_AIM_SLIGHTLY_HIGHER_MULTIPLIER = 0.20000000298023224D;

    // -- Ranged Guard Stuff -- \\

    // -- Knight Guard Stuff -- \\

    /**
     * Basic delay for the next Knight attack.
     */
    public static final int KNIGHT_ATTACK_DELAY_BASE = 32;

    /**
     * Minimum delay for the next Knight attack.
     */
    public static final int KNIGHT_ATTACK_DELAY_MIN = 16;

    /**
     * Basic bonus hp for knights
     */
    public static final int KNIGHT_HP_BONUS = 15;

    /**
     * Basic bonus hp for cavalry
     */
    public static final int CAVALRY_HP_BONUS = 20;

    /**
     * Basic bonus damage for cavalry
     */
    public static final double CAVALRY_DAMAGE_MULTIPLIER = 1.00;

    /**
     * Basic bonus melee range for cavalry
     */
    public static final double CAVALRY_RANGE_MULTIPLIER = 1.20;

    /**
     * Damage multiplier for cavalry when hit by ranged attacks, to represent their vulnerability to such attacks.
     */
    public static final double CAVALRY_RANGED_DAMAGE_VULNERABILITY = 1.20;

    /**
     * Cornering penalty for mounted cavalry units.
     * Encourages "straight-first" pathing.
     */
    public static final float CAVALRY_CORNER_PENALTY = 0.3f;

    /**
     * Patrol range for cavalry units.
     */
    public static final int CAVALRY_PATROL_RANGE = 150;

    /**
     * Basic bonus hp for druids
     */
    public static final int DRUID_HP_BONUS = 12;

    /**
     * Basic bonus hp for rangers and marksmen.
     * <p>
     * There was no ranger health curve at all until this constant existed: {@code JobRanger} had no
     * {@code onLevelUp}, so a max-level ranger in a level-five barracks tower had 30 max health, less than a
     * day-one knight in a level-one tower. The archer is meant to be the fragile one, but that was the absence of a
     * method rather than a tuning choice. Off the ranger's primary skill, and divided, so he stays comfortably the
     * most fragile guard: at skill 99 in a level-five guard tower a knight has 164, a druid 111 and a ranger 91.
     */
    public static final int RANGER_HP_BONUS = 8;

    /**
     * How much of a ranger's Agility turns into max health -- one heart per six levels.
     */
    public static final int RANGER_HP_LEVEL_DIVISOR = 3;

    /**
     * This knight's max distance for attacking.
     */
    public static final int MAX_DISTANCE_FOR_ATTACK = 2;

    // -- Knight Guard Stuff -- \\

    // -- Physical Guard Stuff -- \\

    /**
     * Base physical damage.
     */
    public static final int BASE_PHYSICAL_DAMAGE = 3;

    /**
     * How much harder a melee guard hits than the raw damage figure of the weapon in his hand.
     * <p>
     * Until this constant existed the same factor was applied by accident. {@code MeleeCombatAI#getAttackDamage}
     * read {@code addDmg += EnchantmentHelper.modifyDamage(..., addDmg)}, and that helper seeds its accumulator
     * with the damage it is handed and returns the whole thing, so the line evaluated to
     * {@code 2 * addDmg + enchantments} for every melee guard in the game, enchanted or not. The arithmetic is now
     * written the way it reads, and the factor is kept here because doubled damage is the balance this port has
     * shipped for its whole life: a netherite-sworded knight has always hit for 20 before research and crits, and
     * halving that overnight is not a bug fix, it is a different game.
     * <p>
     * Set to 1.0 for damage that matches the weapon's own numbers -- a knight then deals what the weapon says, and
     * the {@code MELEE_DAMAGE} research and the crit multiplier become a much larger share of his output.
     */
    public static final double MELEE_WEAPON_DAMAGE_SCALE = 2.0;

    // -- Physical Guard Stuff -- \\

    // -- Guard Movement -- \\

    /**
     * Quantity the worker should turn around all at once.
     */
    public static final double TURN_AROUND = 180D;

    /**
     * Normal volume at which sounds are played at.
     */
    public static final double BASIC_VOLUME = 1.0D;

    /**
     * Quantity to be moved to rotate the entity without actually moving.
     */
    public static final double MOVE_MINIMAL = 0.01D;

    // -- Guard Movement -- \\

    /**
     * Guard armor constants.
     * <p>
     * LEATHER_BUILDING_LEVEL_RANGE is the citizen-level range every armour band is demanded over, i.e. all of them.
     * The rest are hut-level ranges, named after the top material the band they gate licenses: a hut of level 1 or 2
     * may issue up to copper, a hut of 2 or 3 up to chain, and so on.
     */
    public static final Tuple<Integer, Integer> LEATHER_BUILDING_LEVEL_RANGE = new Tuple<>(0, 99);
    public static final Tuple<Integer, Integer> COPPER_BUILDING_LEVEL_RANGE = new Tuple<>(1, 2);
    public static final Tuple<Integer, Integer> CHAIN_BUILDING_LEVEL_RANGE = new Tuple<>(2, 3);
    public static final Tuple<Integer, Integer> IRON_BUILDING_LEVEL_RANGE  = new Tuple<>(3, 4);
    public static final Tuple<Integer, Integer> DIA_BUILDING_LEVEL_RANGE   = new Tuple<>(4, 5);

    public static final Tuple<Integer, Integer> SHIELD_LEVEL_RANGE          = new Tuple<>(0, 99);
    public static final Tuple<Integer, Integer> SHIELD_BUILDING_LEVEL_RANGE = new Tuple<>(1, 5);

    /**
     * Private constructor to hide the implicit one.
     */
    private GuardConstants()
    {
        /*
         * Intentionally left empty.
         */
    }
}
