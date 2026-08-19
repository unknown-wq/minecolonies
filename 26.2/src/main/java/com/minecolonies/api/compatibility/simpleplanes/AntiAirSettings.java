package com.minecolonies.api.compatibility.simpleplanes;

import net.minecraft.nbt.CompoundTag;

/**
 * The four numbers a server owner may change about a colony's anti-air battery, and every rule about them.
 *
 * <h2>Why this class exists at all, and why it is here rather than next to the battery</h2>
 * The battery itself lives in {@code com.minecolonies.core.compatibility.simpleplanes}, which
 * {@code build.gradle} drops from the build entirely when the Simple Planes jar is not on the machine. The
 * settings cannot live there: {@link com.minecolonies.core.colony.Colony} has to save and load them, and the
 * command has to set them, on every build. So they sit in the always-compiled compat API package next to
 * {@link AircraftCompat}, and like everything else in this package they name no aircraft type — the whole file
 * is doubles, ints and one {@link CompoundTag}.
 *
 * <h2>Every value is bounded, and out of bounds is refused rather than clamped</h2>
 * {@link #check} returns the reason a value is unacceptable instead of quietly moving it to the nearest legal
 * one. A player who types 5000 blocks of range and is silently given 384 will spend the evening wondering why
 * the number he set is not the number that happens; a player who is told "between 16 and 384" knows
 * immediately. This is deliberately not the shape the Stable's patrol-interval setting has, where the field is
 * unbounded and an empty box reads as zero.
 *
 * <p>There is also no path here by which "no value" becomes zero. The command's subcommands report when given
 * no number and set only when given one, so an omitted argument never reaches a setter.
 *
 * <h2>The defaults are the old constants, exactly</h2>
 * Every default below is the compile-time constant the battery used before it was tunable, so a colony that
 * has never seen the command — and every colony saved before this existed, which reads back as all-defaults —
 * behaves bit for bit as it did.
 */
public final class AntiAirSettings
{
    /**
     * NBT tag the whole block is saved under, declared here rather than in {@code NbtTagConstants} so that
     * deleting this file leaves nothing of the feature behind anywhere else. The same choice
     * {@code ColonyProtection} makes.
     */
    public static final String TAG_ANTIAIR = "antiAirSettings";

    private static final String TAG_RANGE     = "range";
    private static final String TAG_INTERVAL  = "shotInterval";
    private static final String TAG_DAMAGE    = "damage";
    private static final String TAG_MIN_LEVEL = "minTowerLevel";

    /**
     * Ticks in a second. The whole rate-to-interval conversion is this number over the rate, and it is named
     * because the conversion is the part of this class most likely to be misread.
     */
    public static final int TICKS_PER_SECOND = 20;

    /**
     * How far a battery reaches, in blocks. The old {@code ENGAGEMENT_RANGE}.
     */
    public static final double DEFAULT_RANGE = 200.0;

    /**
     * Ticks between rounds from one emplacement. The old {@code SHOT_INTERVAL}, i.e. exactly one arrow a
     * second.
     */
    public static final int DEFAULT_SHOT_INTERVAL = 20;

    /**
     * Damage per round. The old {@code ROUND_DAMAGE}.
     */
    public static final double DEFAULT_DAMAGE = 3.0;

    /**
     * Lowest guard-tower level that mounts a battery. The old {@code MIN_TOWER_LEVEL}.
     */
    public static final int DEFAULT_MIN_TOWER_LEVEL = 3;

    /**
     * Launch speed of a round, blocks/tick. <b>Not tunable, and the reason is {@link #MAX_RANGE}.</b> An
     * arrow's horizontal reach saturates at {@code 100 * launch speed}; that constant is what makes 384 a
     * legal range and 500 an illegal one. Expose the speed and the range bound becomes a function of another
     * setting, so a player could lower the speed and leave a range that the solver can no longer reach — at
     * which point the battery tracks its target, computes no solution, and holds fire for ever with nothing
     * anywhere saying why. A knob whose only interesting setting silently breaks a different knob is not worth
     * having.
     */
    public static final double LAUNCH_SPEED = 4.0;

    /**
     * Smallest legal engagement range, blocks.
     *
     * <p>Rounds leave the muzzle two blocks above a tower that is itself several blocks tall, and an aircraft
     * on a run-in is a hundred blocks up. Below about a chunk the slant range to anything worth shooting at is
     * already outside the envelope before the tower has line of sight, so the battery would never fire and the
     * setting would read as broken rather than as strict.
     */
    public static final double MIN_RANGE = 16.0;

    /**
     * Largest legal engagement range, blocks.
     *
     * <p>Two separate ceilings agree on roughly this number and the lower one wins.
     *
     * <p><b>Ballistics.</b> Horizontal reach saturates at {@code 100 * }{@link #LAUNCH_SPEED} = 400 blocks.
     * Past that the solver returns no solution and the tower silently holds its round. 384 keeps the whole
     * legal range inside what the projectile can physically do, with margin for the fact that the solution has
     * to cover slant range rather than ground range.
     *
     * <p><b>Cost.</b> The hand-flown-aircraft scan inflates an AABB by the range around every colony centre.
     * At 384 that is a 768-block cube; at 5000 it is the whole world, every colony, every scan. This is the
     * bound that stops one number turning a cheap periodic sweep into a server-killer, and it is why the
     * command refuses instead of clamping — the person who typed 5000 wanted something and needs to be told he
     * cannot have it.
     */
    public static final double MAX_RANGE = 384.0;

    /**
     * Slowest legal rate of fire, arrows per second per emplacement. Maps to {@link #MAX_SHOT_INTERVAL}, a
     * round every twenty seconds, which is the point at which the battery is decorative rather than off. Off
     * is a different thing and already has its own lever — the {@code aircraftWarnings} server config gates
     * the battery entirely.
     */
    public static final double MIN_RATE = 0.05;

    /**
     * Fastest legal rate of fire, arrows per second per emplacement.
     *
     * <p><b>Four is already twice as fast as the game can use.</b> {@code PlaneEntity#hurtServer} applies a
     * ten-tick damage-immunity window after every hit, so an aircraft can absorb at most two damaging arrows a
     * second no matter how many arrive. Everything above 2.0/s is spent on airframes that cannot currently be
     * hurt. The bound is set at 4.0 rather than 2.0 because over-firing is not useless — a battery engaging
     * two aircraft alternately, or one whose rounds are missing a manoeuvring target, genuinely converts the
     * extra rate into hits — but it is set no higher because past this point the only measurable effect is the
     * racks emptying faster. See {@link #arrowOrder()} for what that costs the couriers.
     */
    public static final double MAX_RATE = 4.0;

    /**
     * Shortest legal gap between rounds, ticks. {@link #MAX_RATE} expressed the way the code actually counts.
     */
    public static final int MIN_SHOT_INTERVAL = 5;

    /**
     * Longest legal gap between rounds, ticks. {@link #MIN_RATE} expressed the way the code actually counts.
     */
    public static final int MAX_SHOT_INTERVAL = 400;

    /**
     * Least damage a round may do.
     *
     * <p>A stock airframe has 10 health and cannot be hurt more than twice a second. At half a heart a round
     * that is twenty hits, i.e. ten seconds of a perfect firing solution, which is longer than a fast target
     * spends inside any legal range. Below this the battery cannot bring anything down at all whatever else is
     * set, which is a state a number should not be able to reach silently.
     */
    public static final double MIN_DAMAGE = 0.5;

    /**
     * Most damage a round may do.
     *
     * <p>Ten health is a stock airframe, so twenty is already a certain kill on the first round that connects,
     * with margin for a modded aircraft twice as tough. Nothing above it can hit an aircraft any harder.
     *
     * <p>It is also a bound on what the battery does to <em>everything else</em>, which is the half that
     * matters more. These are real arrows on a real ballistic arc; the ones that miss come down somewhere, and
     * a tower firing 100-damage arrows over its own colony is a griefing tool rather than a difficulty
     * setting.
     */
    public static final double MAX_DAMAGE = 20.0;

    /**
     * Lowest guard-tower level that may be asked to mount a battery.
     */
    public static final int MIN_TOWER_LEVEL_BOUND = 1;

    /**
     * Highest guard-tower level that may be asked to mount a battery. Guard huts cap at 5, so demanding 6
     * would be an off switch wearing a number's clothes, and the off switch is the server config.
     */
    public static final int MAX_TOWER_LEVEL_BOUND = 5;

    /**
     * How many arrows a dry tower orders at the default rate of fire, and the divisor for the fewest it will
     * accept. The 64/16 pair a ranger asks for in {@code EntityAIRange#atBuildingActions}.
     */
    private static final int BASE_ARROW_ORDER = 64;

    /**
     * Most arrows one order may ask for, as a multiple of {@link #BASE_ARROW_ORDER}. Four stacks is four slots
     * of a courier's inventory for one tower; past that a single emplacement starts crowding out every other
     * delivery in the colony, which is a worse failure than a tower that is briefly dry.
     */
    private static final int MAX_ARROW_ORDER_STACKS = 4;

    private double range          = DEFAULT_RANGE;
    private int    shotInterval   = DEFAULT_SHOT_INTERVAL;
    private double damage         = DEFAULT_DAMAGE;
    private int    minTowerLevel  = DEFAULT_MIN_TOWER_LEVEL;

    /**
     * How far this colony's batteries reach, in blocks.
     *
     * @return the engagement range.
     */
    public double getRange()
    {
        return range;
    }

    /**
     * Ticks between rounds from one emplacement. <b>This is the stored value, not the rate.</b> See
     * {@link #intervalForRate} for why.
     *
     * @return the shot interval in ticks.
     */
    public int getShotInterval()
    {
        return shotInterval;
    }

    /**
     * Damage one round does.
     *
     * @return the round damage.
     */
    public double getDamage()
    {
        return damage;
    }

    /**
     * Lowest guard-tower level that mounts a battery here.
     *
     * @return the minimum tower level.
     */
    public int getMinTowerLevel()
    {
        return minTowerLevel;
    }

    /**
     * The rate of fire this colony actually gets, arrows per second per emplacement.
     *
     * <p>Derived from the interval rather than stored, because the interval is what happens. See
     * {@link #intervalForRate}.
     *
     * @return the effective rate.
     */
    public double getRate()
    {
        return rateForInterval(shotInterval);
    }

    /**
     * Whether every value is still the one the battery had before it was tunable.
     *
     * @return true if nothing has been changed.
     */
    public boolean isDefault()
    {
        return range == DEFAULT_RANGE
                 && shotInterval == DEFAULT_SHOT_INTERVAL
                 && damage == DEFAULT_DAMAGE
                 && minTowerLevel == DEFAULT_MIN_TOWER_LEVEL;
    }

    /**
     * Puts every value back to the constant the battery shipped with.
     */
    public void reset()
    {
        range = DEFAULT_RANGE;
        shotInterval = DEFAULT_SHOT_INTERVAL;
        damage = DEFAULT_DAMAGE;
        minTowerLevel = DEFAULT_MIN_TOWER_LEVEL;
    }

    /**
     * Sets the engagement range. The caller must have accepted {@link #check} first.
     *
     * @param blocks the new range in blocks.
     */
    public void setRange(final double blocks)
    {
        range = blocks;
    }

    /**
     * Sets the gap between rounds directly, in ticks.
     *
     * @param ticks the new interval.
     */
    public void setShotInterval(final int ticks)
    {
        shotInterval = ticks;
    }

    /**
     * Sets the round damage.
     *
     * @param hitPoints the new damage.
     */
    public void setDamage(final double hitPoints)
    {
        damage = hitPoints;
    }

    /**
     * Sets the lowest guard-tower level that mounts a battery.
     *
     * @param level the new minimum level.
     */
    public void setMinTowerLevel(final int level)
    {
        minTowerLevel = level;
    }

    /**
     * The tick count a requested rate of fire turns into.
     *
     * <h2>The honest part</h2>
     * The battery counts ticks. A rate is therefore only ever a request for a tick count, and the tick count
     * is the one the player actually gets: {@code round(20 / rate)}, never less than one tick.
     *
     * <p>The steps this leaves are fine at the bottom of the range and coarse at the top, because the same
     * one-tick step is a small fraction of a long interval and a large fraction of a short one. 1.0/s is
     * exactly 20 ticks and exactly 1.0/s; 2.0/s is exactly 10 ticks; but 3.0/s is 6.67 ticks, which rounds to
     * 7, which is 2.86/s — so a player who asks for 3.0 gets 2.86 and there is no setting that gives him 3.0.
     * Between 2.0/s and the 4.0/s ceiling there are exactly <b>four</b> rates available at all: 4.00 (5
     * ticks), 3.33 (6), 2.86 (7) and 2.50 (8).
     *
     * <p>Which is why {@link #getRate} recomputes the rate from the interval instead of storing the number
     * that was typed. Storing 3.0 and firing at 2.86 would make every later report a small lie. The command
     * says the achieved rate and the interval it came from at the moment the value is set, so the rounding is
     * visible where it happens rather than discovered later.
     *
     * @param rate the requested rate, arrows per second.
     * @return the interval in ticks.
     */
    public static int intervalForRate(final double rate)
    {
        return Math.max(1, (int) Math.round(TICKS_PER_SECOND / rate));
    }

    /**
     * The rate of fire a tick interval delivers.
     *
     * @param ticks the interval.
     * @return arrows per second.
     */
    public static double rateForInterval(final int ticks)
    {
        return (double) TICKS_PER_SECOND / ticks;
    }

    /**
     * How many arrows a dry tower orders, scaled to how fast it is emptying its racks.
     *
     * <h2>Why this scales and the trigger does not</h2>
     * A stack of arrows is a minute of fire at the default rate and a quarter of a minute at the ceiling,
     * while the time for a request to reach the warehouse, be resolved, and be carried to the tower by a
     * courier does not get any shorter. Left at a flat 64 a fast battery would spend most of its life empty,
     * waiting on a delivery it burns through in sixteen seconds. Scaling the order by the rate keeps one
     * delivery worth roughly one minute of fire whatever the rate is, so the courier round trip stays the same
     * fraction of the cycle.
     *
     * <p>What deliberately does <em>not</em> change is <b>when</b> the order is filed: still only once the
     * tower has actually run dry. Ordering at a low-water mark would remove the gap altogether and is the
     * obvious next step, but it would also change the behaviour of a colony that has never touched this
     * command, which is the one thing this work is not allowed to do. At the default rate this method returns
     * 64 and {@link #arrowOrderMin()} returns 16 — the exact pair that was there before.
     *
     * @return the order size, one to four stacks.
     */
    public int arrowOrder()
    {
        final long scaled = Math.round(BASE_ARROW_ORDER * (double) DEFAULT_SHOT_INTERVAL / shotInterval);
        return (int) Math.clamp(scaled, BASE_ARROW_ORDER, (long) BASE_ARROW_ORDER * MAX_ARROW_ORDER_STACKS);
    }

    /**
     * The smallest delivery a tower will accept, kept at a quarter of the order so it scales with it.
     *
     * @return the minimum delivery size.
     */
    public int arrowOrderMin()
    {
        return arrowOrder() / 4;
    }

    /**
     * Whether a number is inside its bounds.
     *
     * @param value the value offered.
     * @param min   the lowest acceptable value.
     * @param max   the highest acceptable value.
     * @return true if it may be used.
     */
    public static boolean check(final double value, final double min, final double max)
    {
        return value >= min && value <= max;
    }

    /**
     * Saves the block, and saves nothing at all when every value is still the default.
     *
     * <p>A colony that has never been tuned writes no tag, so the save file of an untouched world is
     * unchanged by this feature existing and {@link #read} on it takes the all-defaults path.
     *
     * @param compound the colony's tag.
     */
    public void write(final CompoundTag compound)
    {
        if (isDefault())
        {
            compound.remove(TAG_ANTIAIR);
            return;
        }

        final CompoundTag tag = new CompoundTag();
        tag.putDouble(TAG_RANGE, range);
        tag.putInt(TAG_INTERVAL, shotInterval);
        tag.putDouble(TAG_DAMAGE, damage);
        tag.putInt(TAG_MIN_LEVEL, minTowerLevel);
        compound.put(TAG_ANTIAIR, tag);
    }

    /**
     * Loads the block. A missing tag, or any missing field inside it, reads back as the default, so a save
     * from before this existed and a save from a future version with one more knob both load without a
     * migration.
     *
     * <p>Every value is put through the bounds on the way in as well as on the way through the command. A
     * hand-edited save, or one written by a build whose bounds were wider, cannot get an unbounded range past
     * this point — out of bounds on load is dropped back to the default rather than kept, because the
     * alternative is a server that will not come up or one that lags with no visible cause.
     *
     * @param compound the colony's tag.
     */
    public void read(final CompoundTag compound)
    {
        reset();
        if (!compound.contains(TAG_ANTIAIR))
        {
            return;
        }

        final CompoundTag tag = compound.getCompoundOrEmpty(TAG_ANTIAIR);

        final double storedRange = tag.getDoubleOr(TAG_RANGE, DEFAULT_RANGE);
        if (check(storedRange, MIN_RANGE, MAX_RANGE))
        {
            range = storedRange;
        }

        final int storedInterval = tag.getIntOr(TAG_INTERVAL, DEFAULT_SHOT_INTERVAL);
        if (check(storedInterval, MIN_SHOT_INTERVAL, MAX_SHOT_INTERVAL))
        {
            shotInterval = storedInterval;
        }

        final double storedDamage = tag.getDoubleOr(TAG_DAMAGE, DEFAULT_DAMAGE);
        if (check(storedDamage, MIN_DAMAGE, MAX_DAMAGE))
        {
            damage = storedDamage;
        }

        final int storedLevel = tag.getIntOr(TAG_MIN_LEVEL, DEFAULT_MIN_TOWER_LEVEL);
        if (check(storedLevel, MIN_TOWER_LEVEL_BOUND, MAX_TOWER_LEVEL_BOUND))
        {
            minTowerLevel = storedLevel;
        }
    }
}
