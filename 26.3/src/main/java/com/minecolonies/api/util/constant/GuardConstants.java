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

    // -- Cavalry charge -- \\

    /**
     * Navigation speed factor a cavalry charge is ridden at.
     * <p>
     * This is the navigator's own ceiling ({@code MinecoloniesAdvancedPathNavigate.MAX_SPEED_ALLOWED}); anything above
     * it is rejected with a logged error. It is deliberately the maximum rather than a tuned figure, because it is not
     * what actually decides the speed. A citizen-ridden horse accelerates by
     * {@code min(getSpeed() * CITIZEN_RIDE_INPUT, CITIZEN_RIDE_MAX_ACCELERATION)} per tick
     * ({@code CavalryHorseEntity#citizenRiddenInput}), and {@code getSpeed()} is this factor times the horse's rolled
     * MOVEMENT_SPEED. At 2.0 that product saturates the horse's own 0.18 acceleration cap for every horse whose
     * converted MOVEMENT_SPEED is at least 0.15 -- which is all but the very slowest roll -- so every cavalryman
     * charges at the same documented ceiling instead of at the speed of the horse he happened to be given.
     */
    public static final double CAVALRY_CHARGE_SPEED = 2.0;

    /**
     * Top speed of a citizen-ridden cavalry horse, in blocks per second, used as the denominator of the charge
     * damage bonus.
     * <p>
     * Derived, not measured. The acceleration cap is {@code CITIZEN_RIDE_MAX_ACCELERATION = 0.18} blocks per tick and
     * on ordinary ground (block friction exactly 0.6) {@code LivingEntity#getFrictionInfluencedSpeed} returns
     * {@code getSpeed()} unchanged, so the per-tick displacement settles at {@code a / (1 - 0.6 * 0.91) = 2.203 * a}
     * = 0.3965 blocks per tick = 7.93 blocks per second. That is the same quantity {@code Entity#getKnownSpeed}
     * reports, because {@code Entity#computeSpeed} defines it as the displacement over one tick. Ice, soul sand and
     * slopes move the real figure either way, which is why the bonus below is clamped rather than left open-ended.
     */
    public static final double CAVALRY_CHARGE_TOP_SPEED = 7.9;

    /**
     * How much harder a blow lands at {@link #CAVALRY_CHARGE_TOP_SPEED}, as a fraction of the same blow at rest.
     * <p>
     * 1.00 means a full-gallop strike does double damage and the bonus falls off linearly to nothing at a standstill.
     * It is multiplicative rather than a flat addition on purpose: cavalry keeps its place in the colony's damage
     * progression (weapon, research, crit and the low-health term are all still in the number being scaled), and the
     * tuning does not have to be revisited if the weapon-damage term in {@code MeleeCombatAI#getAttackDamage} ever
     * changes.
     */
    public static final double CAVALRY_CHARGE_DAMAGE_BONUS = 1.00;

    /**
     * How far past the target one pass carries, in blocks, before the rider is looking to turn.
     * <p>
     * The point is chosen on the line the horse is already travelling, so a pass rides through the target rather than
     * bouncing off him. It is longer than {@link #CAVALRY_CHARGE_TURN_DISTANCE} so that the turn is always taken while
     * the horse still has a live path under it and is still moving, never after the navigation has run dry.
     */
    public static final int CAVALRY_CHARGE_RUN_OUT_DISTANCE = 10;

    /**
     * Distance from the target, in blocks, at which the rider stops running out and turns back in.
     * <p>
     * It has to be long enough for the return leg to reach top speed again. From a standing turn the per-tick
     * displacement goes 0.18, 0.28, 0.33, 0.36, 0.38, 0.39 ... so about 2.3 blocks buys 98 % of the ceiling; six
     * blocks leaves roughly three of run-up once the striking distance of about 2.85 is subtracted, with margin for
     * the turn itself. Longer would only add downtime: the round trip is already close to the 16-32 tick attack
     * cooldown, so lengthening it costs damage output for nothing.
     */
    public static final int CAVALRY_CHARGE_TURN_DISTANCE = 6;

    /**
     * Hard ceiling on one run-out leg, in ticks.
     * <p>
     * At the charge speed the six blocks of {@link #CAVALRY_CHARGE_TURN_DISTANCE} take about fifteen ticks, so thirty
     * is twice the expected leg. It is deliberately short: the case it exists for is a target that keeps pace with
     * the horse and is therefore never left behind, and every tick spent running out is a tick not spent striking.
     * On a timeout the rider turns back in regardless of how far he got.
     */
    public static final int CAVALRY_CHARGE_RUN_OUT_TIMEOUT = 30;

    /**
     * How many run-outs in a row may time out *having gone nowhere* before the charge is given up as impossible here.
     * <p>
     * "Gone nowhere" is measured against where the leg started, not against the target: a rider who covered ground but
     * was followed the whole way is being chased, not boxed in, and that is an ordinary fight rather than a broken one.
     */
    public static final int CAVALRY_CHARGE_MAX_FAILED_PASSES = 3;

    /**
     * How long, in ticks, a cavalryman fights on the spot like an ordinary mounted guard after
     * {@link #CAVALRY_CHARGE_MAX_FAILED_PASSES} failed run-outs, before trying to charge again.
     */
    public static final int CAVALRY_CHARGE_SUSPEND_TICKS = 200;

    // -- Cavalry charge -- \\

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

    // -- Spear Guard Stuff -- \\

    /**
     * How far a spearman gives ground when a target gets inside the point of his spear, in blocks.
     * <p>
     * Vanilla's {@code SpearAttack.MIN_REPOSITION_DISTANCE} is 6 and {@code SpearRetreat.MIN_COOLDOWN_DISTANCE} is 9,
     * but both of those pay for themselves in vanilla because a vanilla spear's damage is a function of the charge
     * speed on the way back in. A colony guard's damage is flat, so a six-block run-up would be pure downtime. Three
     * blocks is enough to put a melee raider back outside contact range and short enough that the guard is back in
     * striking distance within one attack cooldown.
     */
    public static final int SPEAR_STEP_BACK_DISTANCE = 3;

    // -- Spear Guard Stuff -- \\

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
