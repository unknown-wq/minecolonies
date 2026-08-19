package com.minecolonies.core.colony.managers;

import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.advancements.AdvancementTriggers;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.ICivilianData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.managers.interfaces.IReproductionManager;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingTownHall;
import com.minecolonies.core.colony.eventhooks.citizenEvents.CitizenBornEvent;
import com.minecolonies.core.util.AdvancementUtils;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateConstants.MAX_TICKRATE;
import static com.minecolonies.api.research.util.ResearchConstants.GROWTH;
import static com.minecolonies.api.util.constant.StatisticsConstants.BIRTH;
import static com.minecolonies.api.util.constant.TranslationConstants.MESSAGE_NEW_CHILD_BORN;
import static com.minecolonies.api.util.constant.TranslationConstants.MESSAGE_NEW_CHILD_BORN_HOMELESS;
import static com.minecolonies.core.colony.CitizenData.SUFFIXES;

/**
 * Repo manager to spawn children.
 */
public class ReproductionManager implements IReproductionManager
{
    /**
     * The time in seconds before the initial try to spawn. 5 minutes between each attempt.
     */
    private static final int MIN_TIME_BEFORE_SPAWNTRY = 20 * 60 * 5;

    /**
     * Interval at which the childen are created, in ticks. Every 10 min it tries to spawn a child.
     */
    private final static int CHILD_SPAWN_INTERVAL = 20 * 60 * 10;

    /**
     * Min necessary citizens for reproduction.
     */
    private static final int MIN_SIZE_FOR_REPRO   = 2;

    /**
     * How many colony ticks make up one in game day: 24000 world ticks divided by the 500 tick colony tick. The
     * generational birth rate is quoted per colony day, so this is what turns it into a per tick probability.
     */
    private static final int COLONY_TICKS_PER_DAY = 24000 / MAX_TICKRATE;

    /**
     * Upper bound on the fertility product, so that the birth modifier config and a fully researched growth tree
     * cannot between them outrun the school.
     */
    private static final double MAX_FERTILITY = 4.0;

    /**
     * The timer counting ticks to the next time creating a child
     */
    private int childCreationTimer;

    /**
     * The last colony day on which the "born without a bed" warning was sent, so a colony breeding through a housing
     * shortage complains once a day rather than once a birth.
     */
    private int lastHomelessWarningDay = -1;

    /**
     * The colony the manager belongs to.
     */
    private final Colony colony;

    /**
     * Random function for the manager to use.
     */
    private Random random = new Random();

    /**
     * Create a new reproduction manager.
     * @param colony the colony to spawn kids for.
     */
    public ReproductionManager(final Colony colony)
    {
        this.colony = colony;
        childCreationTimer = random.nextInt(CHILD_SPAWN_INTERVAL) + MIN_TIME_BEFORE_SPAWNTRY;
    }

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        if (CitizenAging.isEnabled())
        {
            tickGenerational(colony);
            return;
        }

        if ( (childCreationTimer -= MAX_TICKRATE) <= 0)
        {
            childCreationTimer = (MIN_TIME_BEFORE_SPAWNTRY + random.nextInt(CHILD_SPAWN_INTERVAL)) * (colony.getCitizenManager().getCurrentCitizenCount() / Math.max(4, colony.getCitizenManager().getMaxCitizens()));
            trySpawnChild();
        }
    }

    /**
     * The generational birth rate.
     * <p>
     * Upstream's timer is not a rate at all: the multiplier on it is an integer division of the population by the
     * maximum, which is zero for every colony below its cap, so the timer is set to zero and a birth is attempted on
     * every single colony tick -- forty-eight attempts an in game day, bounded only by how many free beds there are.
     * That is survivable while free beds are the brake. It is not survivable once the brake is taken off, which is
     * what {@link #trySpawnChild()} now does, so the rate has to come from somewhere real instead.
     * <p>
     * It comes from the death rate. A colony in a steady state loses population over lifespan citizens a day, so the
     * birth rate is written as the same figure times a fertility multiplier: at fertility 1 births exactly replace
     * deaths, above 1 the colony grows, below 1 it shrinks. That makes the two halves of the mechanic reconcilable by
     * construction rather than by a tuned constant, and it means the thing the player is actually managing is the
     * fertility multiplier -- food, happiness, housing pressure and the growth research. See
     * {@code 26.2/GENERATIONS.md} for what each is worth.
     *
     * @param colony the colony.
     */
    private void tickGenerational(@NotNull final IColony colony)
    {
        // One pass for both numbers this needs off the citizen list; it runs on every colony tick of every colony, and
        // getOverallHappiness() inside fertility() is already a second walk of the same list.
        int adults = 0;
        double saturation = 0;
        int counted = 0;
        for (final ICitizenData data : colony.getCitizenManager().getCitizensUnmodifiable())
        {
            if (!data.isChild())
            {
                adults++;
            }
            saturation += data.getSaturation();
            counted++;
        }

        if (adults < MIN_SIZE_FOR_REPRO)
        {
            return;
        }

        final double fed = counted == 0 ? 0 : Math.min(1.0, saturation / counted / ICitizenData.MAX_SATURATION);
        final double perDay = (double) adults / CitizenAging.baseLifespan() * fertility(colony, fed);
        // One roll, so at most one birth per colony tick -- an implicit ceiling of 48 births an in game day. At any
        // sane setting the probability is a few percent and this never binds; it is what stops a silly
        // generationsbirthmodifier from filling a colony in one tick.
        if (random.nextDouble() < perDay / COLONY_TICKS_PER_DAY)
        {
            trySpawnChild();
        }
    }

    /**
     * How fertile the colony currently is, as a multiplier on exact replacement.
     * <p>
     * Four factors, all read off state the colony already keeps, multiplied together and capped. They were picked to
     * be things a player can see and act on rather than to model anything: what is on the table, how the town feels,
     * how crowded the houses are, and whether the university has been put to work.
     *
     * @param colony the colony.
     * @param fed    average citizen saturation as a fraction of the maximum, from 0 to 1.
     * @return the multiplier, at most {@link #MAX_FERTILITY}.
     */
    private double fertility(@NotNull final IColony colony, final double fed)
    {
        // Food. Saturation is the citizens' own larder reading and it is what falls first when the restaurant runs
        // dry, so a town that has stopped eating stops having children before anything else notices.
        final double foodFactor = 0.3 + 1.2 * fed;

        // Happiness, which already has security, health, sleep and employment folded into it. Runs 0 to 10; a colony
        // that is working sits around 7.5, which is deliberately where this crosses 1.
        final double happinessFactor = 0.4 + 0.08 * colony.getOverallHappiness();

        // Housing pressure. Beds no longer gate a birth at all, but they still decide how pleasant the answer is: a
        // town with spare rooms breeds half again as fast as one that is already sleeping two to a bed.
        // getMaxCitizens() is the colony's own housing cap -- the bed count, itself capped by the research and config
        // ceiling. It is the "276" a player reads in /mc colony info, which is the number they would reason with.
        final int beds = Math.max(1, colony.getCitizenManager().getMaxCitizens());
        final double occupancy = (double) colony.getCitizenManager().getCurrentCitizenCount() / beds;
        final double housingFactor = Math.max(0.2, Math.min(1.5, 1.5 - 0.5 * occupancy));

        // The growth research, which already speeds children up; letting it speed births up too means the one thing
        // in the tree that is about children is the one thing that answers a population problem. Doubles the rate
        // when fully researched.
        final double researchFactor = 1 + colony.getResearchManager().getResearchEffects().getEffectStrength(GROWTH);

        final double modifier = MinecoloniesAPIProxy.getInstance().getConfig().getServer().generationsBirthModifier.get();
        return Math.min(MAX_FERTILITY, foodFactor * happinessFactor * housingFactor * researchFactor * modifier);
    }

    /**
     * A pair of parents for a child about to be born.
     * <p>
     * {@code second} may be null: a colony can be down to one adult with a home and the birth still has to be able to
     * happen. {@code first} never is, and is always the one whose home the child moves into when the two of them do
     * not share one.
     *
     * @param first  the parent whose home the child joins.
     * @param second the other parent, or null.
     */
    private record Parents(@NotNull ICitizenData first, ICitizenData second) {}

    /**
     * Try to spawn a new citizen as a child of two of the colony's adults, born into their home.
     *
     * <h2>Parents first, then the house</h2>
     * 26.2/Fabric: this used to run the other way round. It picked a house with a spare bed -- with no citizen passed,
     * so anchored on the colony centre -- and then took the child's parents from whoever already lived there. Two
     * things fell out of that and both were wrong. The tavern has four beds and sits near the middle of most colonies,
     * so it won that contest again and again and the colony's children all grew up in the tavern while their parents
     * lived in houses. And when the winning house had no residents at all, {@code assignedCitizens} came back empty and
     * the child was born with no parents whatever -- a name generated from nothing, no family, no siblings.
     * <p>
     * So the parents are chosen first, by {@link #chooseParents()}, and the child is born into <em>their</em> home. A
     * child cannot live alone.
     *
     * <h2>A child holds no bed</h2>
     * It lives with its parents and consumes no housing slot; {@code LivingBuildingModule#getResidentCount()} is what
     * makes that true, and it is why the child can be assigned to a house that is full of adults. On growing up it
     * becomes an ordinary adult, is released by {@code ICitizenData#setIsChild(false)} and looks for a bed like
     * anybody else -- see {@code CitizenData#leaveTheParentalHome}.
     *
     * <h2>A missing bed still does not stop a birth</h2>
     * With the generational mechanic on, housing is a pressure the player has to answer rather than a silent
     * contraceptive. What still stops a birth is the colony's population ceiling, and specifically
     * {@code maxCitizensFromResearch()} -- the smaller of the citizen cap research and the {@code maxcitizenpercolony}
     * config -- rather than {@code getMaxCitizens()}, which folds the bed count in and would put the housing gate
     * straight back. Now that a child holds no bed, the case where a child is born homeless has shrunk to the one
     * where its <em>parents</em> are: a colony with no residence built yet, or one whose houses are so short that the
     * parents themselves never got a bed. It has not disappeared, so the warning below stays.
     */
    public void trySpawnChild()
    {
        final boolean generational = CitizenAging.isEnabled();
        final int ceiling = generational
                              ? (int) colony.getCitizenManager().maxCitizensFromResearch()
                              : colony.getCitizenManager().getMaxCitizens();

        // Spawn a child when adults are present
        if (!colony.getSettings().getSetting(BuildingTownHall.MOVE_IN).getValue()
              || colony.getCitizenManager().getCurrentCitizenCount() >= ceiling
              || colony.getCitizenManager().getCurrentCitizenCount() < Math.min(MIN_SIZE_FOR_REPRO,
          MinecoloniesAPIProxy.getInstance().getConfig().getServer().initialCitizenAmount.get()))
        {
            return;
        }

        if (!checkForBioParents())
        {
            return;
        }

        final Parents chosen = chooseParents();
        if (chosen == null)
        {
            // No adult in the colony at all. Nothing to be a parent, so nothing is born.
            return;
        }

        final ICitizenData firstParent = chosen.first();
        final ICitizenData secondParent = chosen.second();

        // The family home: the first parent's, since chooseParents puts the one with a home there. Null only when
        // neither parent has one, which is the housing shortage the class comment describes.
        final IBuilding home = firstParent.getHomeBuilding() != null ? firstParent.getHomeBuilding()
                                 : (secondParent == null ? null : secondParent.getHomeBuilding());
        final boolean homeless = home == null;
        if (homeless && !generational)
        {
            // Without the generational mechanic the old rule stands: no bed, no birth.
            return;
        }

        // Somewhere for the entity to appear. The family home if there is one, otherwise the town hall, otherwise
        // where a parent is standing -- a colony that has not built anything still has people in it.
        BlockPos birthPlace = homeless ? null : home.getPosition();
        if (birthPlace == null && colony.getServerBuildingManager().getTownHall() != null)
        {
            birthPlace = colony.getServerBuildingManager().getTownHall().getPosition();
        }
        if (birthPlace == null)
        {
            birthPlace = firstParent.getLastPosition();
        }

        boolean isOnlyChildInColony = true;
        for (final ICitizenData data : colony.getCitizenManager().getCitizens())
        {
            if (data.isChild())
            {
                isOnlyChildInColony = false;
                break;
            }
        }

        final ICitizenData newCitizen = colony.getCitizenManager().createAndRegisterCivilianData();

        if (secondParent != null)
        {
            firstParent.setPartner(secondParent.getId());
            secondParent.setPartner(firstParent.getId());
        }

        newCitizen.getCitizenSkillHandler().init(colony, firstParent, secondParent, random);
        newCitizen.setIsChild(true);

        final List<String> possibleSuffixes = new ArrayList<>();
        newCitizen.addSiblings(firstParent.getChildren().toArray(new Integer[0]));
        firstParent.addChildren(newCitizen.getId());
        possibleSuffixes.add(firstParent.getTextureSuffix());

        if (secondParent != null)
        {
            newCitizen.addSiblings(secondParent.getChildren().toArray(new Integer[0]));
            secondParent.addChildren(newCitizen.getId());
            possibleSuffixes.add(secondParent.getTextureSuffix());
        }

        newCitizen.setParents(firstParent.getId(),
          secondParent == null ? ICitizenData.NO_PARENT : secondParent.getId(),
          firstParent.getName(),
          secondParent == null ? "" : secondParent.getName());
        newCitizen.generateName(random, firstParent.getName(), secondParent == null ? "" : secondParent.getName(), colony.getCitizenNameFile());

        if (!homeless)
        {
            // Never refused: the child is not a resident for capacity purposes, so a house full of adults still takes
            // its own children. See LivingBuildingModule#getResidentCount.
            home.getFirstModuleOccurance(LivingBuildingModule.class).assignCitizen(newCitizen);
        }

        for (final int sibling : newCitizen.getSiblings())
        {
            final ICitizenData siblingData = colony.getCitizenManager().getCivilian(sibling);
            if (siblingData != null)
            {
                siblingData.addSiblings(newCitizen.getId());
            }
        }

        if (possibleSuffixes.contains("_w") && possibleSuffixes.contains("_d"))
        {
            possibleSuffixes.add("_b");
        }

        if (possibleSuffixes.isEmpty())
        {
            possibleSuffixes.addAll(SUFFIXES);
        }

        newCitizen.setSuffix(possibleSuffixes.get(random.nextInt(possibleSuffixes.size())));

        final int populationCount = colony.getCitizenManager().getCurrentCitizenCount();
        AdvancementUtils.TriggerAdvancementPlayersForColony(colony, playerMP -> AdvancementTriggers.COLONY_POPULATION.get().trigger(playerMP, populationCount));

        colony.getCitizenManager().spawnOrCreateCitizen(newCitizen, colony.getWorld(), birthPlace);
        if (isOnlyChildInColony)
        {
            MessageUtils.format(MESSAGE_NEW_CHILD_BORN, newCitizen.getName(), colony.getName()).sendTo(colony).forManagers();
        }

        // A colony whose parents have nowhere to live reads as a bug unless it is said out loud. The homelessness
        // interaction says it too, but only after seven days of it; this is the same day.
        if (homeless && colony.getDay() != lastHomelessWarningDay)
        {
            lastHomelessWarningDay = colony.getDay();
            MessageUtils.format(MESSAGE_NEW_CHILD_BORN_HOMELESS, newCitizen.getName(), colony.getName()).sendTo(colony).forManagers();
        }

        colony.getEventDescriptionManager().addEventDescription(new CitizenBornEvent(birthPlace, newCitizen.getName()));
        colony.getStatisticsManager().increment(BIRTH, colony.getDay());
    }

    /**
     * Choose the two citizens whose child is about to be born.
     * <p>
     * A random adult with a home first, and then that adult's family. Everything else follows from that ordering, and
     * the ordering is the point: draw the pair instead and a colony ends up with one prolific couple and nobody else,
     * because the only citizens who are anybody's partner are the ones who have already had a child. That is not a
     * hypothetical -- it is what the first version of this did on a live colony, eight births in a row to the same two
     * people while eighty other adults looked on.
     * <p>
     * So: pick a citizen, then find who it would have the child with.
     * <ol>
     *     <li>it already has a partner -- then that is the other parent, always, which is what keeps a family a
     *     family across all its children;</li>
     *     <li>otherwise the nearest unattached adult it could have a child with, a housemate for preference, and the
     *     two become partners -- so the next child of either of them takes branch 1;</li>
     *     <li>and if the colony has nobody at all to pair it with, it is a single parent.</li>
     * </ol>
     * The draw is from adults who <em>have</em> a home, because that home is where the child is going to live; a
     * colony where nobody has one falls back to any adult and the child is born homeless with its parents.
     * <p>
     * "Adult" excludes children throughout -- a child is not a parent -- and a pair is rejected if the two are
     * related, which {@code isRelatedTo} takes to mean siblings, parent and child, or already partners.
     *
     * @return the parents, or null when the colony has no adult at all.
     */
    private Parents chooseParents()
    {
        final List<ICitizenData> adults = new ArrayList<>();
        final List<ICitizenData> homed = new ArrayList<>();
        for (final ICitizenData data : colony.getCitizenManager().getCitizensUnmodifiable())
        {
            if (!data.isChild())
            {
                adults.add(data);
                if (data.getHomeBuilding() != null)
                {
                    homed.add(data);
                }
            }
        }

        if (adults.isEmpty())
        {
            return null;
        }

        final List<ICitizenData> pool = homed.isEmpty() ? adults : homed;
        Collections.shuffle(pool, random);

        // Walks the shuffled pool rather than taking only the first, so that one candidate the colony happens to have
        // nobody for does not drop the whole birth to a single parent. In practice the first candidate answers.
        for (final ICitizenData one : pool)
        {
            final ICitizenData partner = one.getPartner();
            if (partner != null && !partner.isChild())
            {
                return withHomeFirst(one, partner);
            }

            final ICitizenData match = findPartnerFor(one, adults);
            if (match != null)
            {
                return withHomeFirst(one, match);
            }
        }

        return new Parents(pool.get(0), null);
    }

    /**
     * Who an unattached adult would have a child with.
     * <p>
     * A housemate first -- that is the pair a player looking at the house would expect, and it puts the child in a
     * home both parents already share. Failing that the nearest acceptable adult, so a town of one bed huts pairs
     * neighbours rather than opposite ends of the map.
     *
     * @param one    the citizen looking.
     * @param adults every adult in the colony.
     * @return the other parent, or null if the colony has nobody.
     */
    private static ICitizenData findPartnerFor(final ICitizenData one, final List<ICitizenData> adults)
    {
        ICitizenData best = null;
        double bestScore = Double.MAX_VALUE;

        for (final ICitizenData other : adults)
        {
            if (other == one || other.getPartner() != null || !couldBeParents(one, other))
            {
                continue;
            }

            // Negative for a housemate so that nothing outranks one; otherwise the squared distance between the two
            // homes, and an adult with no home at all sorts last without being excluded.
            final double score;
            if (one.doesLiveWith(other))
            {
                score = -1;
            }
            else if (one.getHomeBuilding() == null || other.getHomeBuilding() == null)
            {
                score = Double.MAX_VALUE - 1;
            }
            else
            {
                score = other.getHomeBuilding().getPosition().distSqr(one.getHomeBuilding().getPosition());
            }

            if (best == null || score < bestScore)
            {
                best = other;
                bestScore = score;
                if (score < 0)
                {
                    break;
                }
            }
        }

        return best;
    }
    /**
     * Whether two adults may have a child together.
     *
     * @param one   the first.
     * @param other the second.
     * @return true if they may.
     */
    private static boolean couldBeParents(final ICitizenData one, final ICitizenData other)
    {
        // Opposite sexes, because checkForBioParents already establishes that as the colony's rule and it would read
        // oddly to enforce it colony-wide and then ignore it per couple. Unrelated, so a colony does not quietly pair
        // brothers with sisters or parents with their own children.
        return one.isFemale() != other.isFemale() && !one.isRelatedTo(other) && !other.isRelatedTo(one);
    }

    /**
     * Order a pair so that the parent with a home comes first, since that is the home the child is born into.
     *
     * @param one   one parent.
     * @param other the other.
     * @return the ordered pair.
     */
    private static Parents withHomeFirst(final ICitizenData one, final ICitizenData other)
    {
        if (one.getHomeBuilding() == null && other != null && other.getHomeBuilding() != null)
        {
            return new Parents(other, one);
        }
        return new Parents(one, other);
    }

    /**
     * Check if there are potential biological parents in the colony.
     * (At least one male/female citizen).
     * @return true if so.
     */
    private boolean checkForBioParents()
    {
        boolean hasMale = false;
        boolean hasFemale = false;

        for (final ICitizenData data : colony.getCitizenManager().getCitizens())
        {
            if (data.isFemale())
            {
                hasFemale = true;
            }
            else
            {
                hasMale = true;
            }

            if (hasFemale && hasMale)
            {
                return true;
            }
        }

        for (final ICivilianData data : colony.getVisitorManager().getCivilianDataMap().values())
        {
            if (data.isFemale())
            {
                hasFemale = true;
            }
            else
            {
                hasMale = true;
            }

            if (hasFemale && hasMale)
            {
                return true;
            }
        }
        return false;
    }
}
