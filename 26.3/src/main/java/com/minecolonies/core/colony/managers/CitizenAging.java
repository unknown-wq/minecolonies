package com.minecolonies.core.colony.managers;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenDiedModEvent;
import com.minecolonies.api.util.DamageSourceKeys;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.eventhooks.citizenEvents.CitizenDiedEvent;
import com.minecolonies.core.entity.citizen.CitizenCombatTracker;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.minecolonies.api.util.constant.StatisticsConstants.DEATH;

/**
 * The aging half of the generational mechanic: citizens accumulate life, and when they have used it all up they die of
 * old age.
 *
 * <h2>What the clock is</h2>
 * The colony day ({@code Colony#getDay()}), which is the unit the mod already keeps for long running colony state. It
 * only advances while the colony is loaded, so a town nobody visits does not age, and this class is driven from
 * {@link CitizenManager#onWakeUp()} -- the once per colony day hook that the day rollover already calls. No new clock.
 *
 * <h2>Why a used-life counter and not a birthday</h2>
 * The counter advances by more or less than one per colony day depending on how well the citizen is living, so that
 * housing and food and happiness shorten or lengthen a life instead of being decoration. A stored birthday could not
 * express that without also storing the history that produced it.
 *
 * <h2>Death</h2>
 * Goes through {@code EntityCitizen#die} with the {@code minecolonies:oldage} damage type, which is the one death path
 * in the mod: grave, undertaker, job vacancy and re-hiring, mourning, the event log and the death statistic all come
 * for free. The only thing that path is asked to do differently is skip the colony wide three day misery modifier a
 * violent death injects -- see {@code EntityCitizen#die}.
 * <p>
 * A citizen whose entity is not in the world cannot take that path, and in a large colony that is most of them at any
 * moment. It gets {@link #killAway} instead, which does the same bookkeeping by hand and leaves no grave, because
 * there is no loaded chunk to put one in.
 */
public final class CitizenAging
{
    /**
     * The widest a citizen's personal lifespan may deviate from the configured one, as a fraction.
     * <p>
     * Without this a colony founded on one day is a cohort that dies on one day, which is the mass funeral this whole
     * mechanic is trying not to be. A quarter either way spreads a cohort's deaths over half a lifespan -- fifty
     * colony days at the default -- which is long enough that a school can keep up.
     */
    private static final double LIFESPAN_JITTER = 0.25;

    /**
     * How much of a lifespan an already existing citizen may be given when the mechanic first touches it.
     * <p>
     * Half. Existing citizens in an old save carry no age at all, and the two obvious answers are both bad: age zero
     * makes the whole town one synchronised cohort, and a full random age kills somebody the day the setting is turned
     * on. Half a lifespan, spread uniformly, does neither -- nobody is within fifty colony days of dying when the
     * switch is flipped, and by the time deaths start they are already staggered across the population.
     */
    private static final double LEGACY_AGE_FRACTION = 0.5;

    /**
     * Floor on the living conditions multiplier: the most a wretched life can shorten one, as a divisor on the days a
     * day is worth. 0.5 means a citizen in the worst conditions the happiness system can express burns two days of
     * life per colony day and so lives half as long.
     */
    private static final double CONDITION_FLOOR = 0.5;

    /**
     * Ceiling on the living conditions multiplier. A quarter longer for a citizen who is as happy as the game allows;
     * deliberately much smaller than the floor, because a comfortable life should be the norm the default lifespan was
     * measured against, not a bonus stacked on top of it.
     */
    private static final double CONDITION_CEILING = 1.25;

    /**
     * How many citizens a colony may lose to old age in one colony day, as a divisor on its population.
     * <p>
     * A safety valve, not a balance lever: at the default lifespan the natural rate is population over a hundred, so
     * this is forty times looser than anything normal play produces and never binds. What it catches is an operator
     * dropping {@code generationslifespandays} from 100 to 10 on a live colony, where without it the entire top of the
     * age distribution would die in a single dawn.
     */
    private static final int MAX_DEATHS_PER_DAY_DIVISOR = 20;

    /**
     * Vanilla's death message for the {@code minecolonies:oldage} damage type, which is what a citizen with a body
     * dies with. Reused verbatim for a death without one, so the two read the same in chat.
     */
    private static final String OLD_AGE_DEATH_MESSAGE = "death.attack.oldage";

    /**
     * Appended to that message when the citizen died outside the loaded world, to say why there is no grave.
     */
    private static final String COLONIST_DEATH_AWAY = "com.minecolonies.coremod.citizen.oldage.away";

    /**
     * What the colony event log records as the cause. {@code EntityCitizen#die} puts the rendered death message here;
     * without an entity there is no combat tracker to render one, so this is the same text in plain form.
     */
    private static final String OLD_AGE_CAUSE = "old age";

    private static final Random RANDOM = new Random();

    private CitizenAging()
    {
    }

    /**
     * Whether the generational mechanic is switched on at all.
     *
     * @return true if it is.
     */
    public static boolean isEnabled()
    {
        return MineColonies.getConfig().getServer().generations.get();
    }

    /**
     * The configured base lifespan in colony days.
     *
     * @return the base lifespan.
     */
    public static int baseLifespan()
    {
        return MineColonies.getConfig().getServer().generationsLifespanDays.get();
    }

    /**
     * How long this particular citizen is going to live, in colony days of neutral living conditions.
     *
     * @param citizen the citizen.
     * @return the expectancy.
     */
    public static double lifeExpectancy(@NotNull final ICitizenData citizen)
    {
        return baseLifespan() * jitterOf(citizen);
    }

    /**
     * Advance every citizen of the colony by one colony day, and kill the ones who have run out of life.
     * <p>
     * Children do not age: the clock starts at adulthood. Childhood already has its own timer ({@code Colony}'s
     * additional child time and the {@code GROWTH} research), and letting a child that has nowhere to be schooled burn
     * through a whole lifespan would mean it grew up and dropped dead in the same breath.
     *
     * @param colony   the colony.
     * @param citizens its citizens.
     */
    public static void onNewDay(@NotNull final IColony colony, @NotNull final List<ICitizenData> citizens)
    {
        if (!isEnabled())
        {
            return;
        }

        final int lifespan = baseLifespan();
        final List<ICitizenData> dying = new ArrayList<>();

        for (final ICitizenData citizen : citizens)
        {
            if (citizen.getAgeDays() == ICitizenData.AGE_UNKNOWN)
            {
                initialise(citizen, lifespan);
            }

            if (citizen.isChild())
            {
                continue;
            }

            citizen.setAgeDays(citizen.getAgeDays() + 1.0 / conditionMultiplier(colony, citizen));

            if (citizen.getAgeDays() >= lifeExpectancy(citizen))
            {
                dying.add(citizen);
            }
        }

        if (dying.isEmpty())
        {
            return;
        }

        // Oldest relative to their own expectancy first, so that when the cap below bites it is the people who have
        // been overdue longest who go, and the rest simply wait for tomorrow.
        dying.sort((a, b) -> Double.compare(b.getAgeDays() / lifeExpectancy(b), a.getAgeDays() / lifeExpectancy(a)));

        int budget = Math.max(1, citizens.size() / MAX_DEATHS_PER_DAY_DIVISOR);
        for (final ICitizenData citizen : dying)
        {
            if (budget <= 0)
            {
                break;
            }
            if (kill(citizen))
            {
                budget--;
            }
        }
    }

    /**
     * Give a citizen that has never been aged a starting age and a personal lifespan.
     *
     * @param citizen  the citizen.
     * @param lifespan the configured base lifespan.
     */
    private static void initialise(@NotNull final ICitizenData citizen, final int lifespan)
    {
        if (citizen.getLifeJitter() == ICitizenData.LIFE_JITTER_UNSET)
        {
            citizen.setLifeJitter((float) (1.0 - LIFESPAN_JITTER + RANDOM.nextDouble() * 2 * LIFESPAN_JITTER));
        }

        // A child starts at nothing whatever the save says; anyone else is an adult of unknown age, and gets one.
        citizen.setAgeDays(citizen.isChild() ? 0.0 : RANDOM.nextDouble() * LEGACY_AGE_FRACTION * lifespan * citizen.getLifeJitter());
    }

    /**
     * This citizen's personal lifespan multiplier, rolling one if it does not have one yet.
     *
     * @param citizen the citizen.
     * @return the multiplier.
     */
    private static double jitterOf(@NotNull final ICitizenData citizen)
    {
        if (citizen.getLifeJitter() == ICitizenData.LIFE_JITTER_UNSET)
        {
            citizen.setLifeJitter((float) (1.0 - LIFESPAN_JITTER + RANDOM.nextDouble() * 2 * LIFESPAN_JITTER));
        }
        return citizen.getLifeJitter();
    }

    /**
     * How much a colony day is worth to this citizen, as a divisor on the day.
     * <p>
     * Read straight off the happiness the colony already computes, which is the one number that has housing, food,
     * security, health, employment and sleep folded into it -- so a homeless citizen in a starving town ages fastest
     * without this needing to know anything about houses or bread. Happiness runs 0 to 10 and a working colony sits
     * around 7 to 9, which maps to a shade over neutral; the misery end is where the interesting part is.
     *
     * @param colony  the colony.
     * @param citizen the citizen.
     * @return the multiplier, between {@link #CONDITION_FLOOR} and {@link #CONDITION_CEILING}.
     */
    private static double conditionMultiplier(@NotNull final IColony colony, @NotNull final ICitizenData citizen)
    {
        final double happiness = citizen.getCitizenHappinessHandler().getHappiness(colony, citizen);
        return Math.max(CONDITION_FLOOR, Math.min(CONDITION_CEILING, CONDITION_FLOOR + happiness * 0.075));
    }

    /**
     * Kill a citizen of old age, if its body is currently in the world.
     * <p>
     * A citizen whose entity is not loaded is left alone and tried again next dawn rather than being deleted from the
     * data side: the grave, the inventory and the undertaker all hang off the entity's death, and a citizen removed
     * without one would leave its belongings nowhere and its family unmourned.
     *
     * @param citizen the citizen.
     * @return true if it actually died.
     */
    private static boolean kill(@NotNull final ICitizenData citizen)
    {
        Log.getLogger()
          .info("Citizen {} ({}) of colony {} died of old age at {} days.",
            citizen.getId(),
            citizen.getName(),
            citizen.getColony().getID(),
            String.format("%.1f", citizen.getAgeDays()));

        final AbstractEntityCitizen entity = citizen.getEntity().orElse(null);
        if (entity == null || !entity.isAlive())
        {
            return killAway(citizen);
        }

        entity.die(entity.level().damageSources().source(DamageSourceKeys.OLD_AGE));
        return true;
    }

    /**
     * Kill a citizen whose entity is not currently in the world.
     * <p>
     * Most of a large colony is unloaded most of the time -- the player stands in one corner of a town spanning
     * dozens of chunks -- so requiring a loaded entity would make old age a thing that only happens to whoever the
     * player happens to be standing next to, at a small fraction of the configured rate. The clock is the colony day
     * and it runs for the whole colony, so the death has to as well.
     * <p>
     * What is lost is the grave: {@code createCitizenGrave} needs a block position in a loaded chunk to put it in, and
     * there is none. The rest of what {@code EntityCitizen#die} does that matters without a body is done here by hand
     * -- the vacancy and re-hiring, the family's mourning, the death statistic, the colony event log, the mod event
     * and the announcement -- and the announcement says out loud that there is no grave to collect, so a player does
     * not go looking for one.
     *
     * @param citizen the citizen.
     * @return true, always: an unloaded citizen can always be removed.
     */
    private static boolean killAway(@NotNull final ICitizenData citizen)
    {
        final IColony colony = citizen.getColony();

        // Same key and same decorated name as the death with a body now uses, so the two read alike in chat.
        MessageUtils.format(OLD_AGE_DEATH_MESSAGE, CitizenCombatTracker.deathName(citizen))
          .append(Component.literal(" "))
          .append(Component.translatable(COLONIST_DEATH_AWAY))
          .withPriority(MessageUtils.MessagePriority.DANGER)
          .sendTo(colony)
          .forManagers();

        if (!(citizen.getJob() instanceof AbstractJobGuard))
        {
            colony.getCitizenManager().updateCitizenMourn(citizen, true);
        }

        colony.getStatisticsManager().increment(DEATH, colony.getDay());
        colony.getEventDescriptionManager()
          .addEventDescription(new CitizenDiedEvent(citizen.getLastPosition(), citizen.getName(), OLD_AGE_CAUSE));

        if (citizen.getJob() != null)
        {
            citizen.getJob().onRemoval();
        }
        colony.getCitizenManager().removeCivilian(citizen);

        if (colony.getWorld() != null)
        {
            IMinecoloniesAPI.getInstance()
              .getEventBus()
              .post(new CitizenDiedModEvent(citizen, colony.getWorld().damageSources().source(DamageSourceKeys.OLD_AGE)));
        }
        return true;
    }
}
