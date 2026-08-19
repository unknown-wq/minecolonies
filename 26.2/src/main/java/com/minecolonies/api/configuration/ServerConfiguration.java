package com.minecolonies.api.configuration;

import com.ldtteam.common.config.ConfigValue;
import com.ldtteam.common.config.AbstractConfiguration;
import com.minecolonies.api.colony.permissions.Explosions;
import com.minecolonies.api.util.constant.CitizenConstants;
import com.minecolonies.api.util.constant.GuardConstants;
import com.ldtteam.common.config.ConfigValue.*;

import java.util.List;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.Constants.*;

/**
 * Mod server configuration. Loaded serverside, synced on connection.
 */
public class ServerConfiguration extends AbstractConfiguration
{
    /**
     * PORT-NOTE(26.2): the speed a colony boat is steered at, in <em>blocks per second</em>, exposed here and through
     * {@code /mc boatspeed} so it can be raised on a live server the way vanilla's {@code maxMinecartSpeed} game rule
     * can.
     * <p>
     * Blocks per second rather than the blocks per tick the navigator actually writes, because that is the unit the
     * setting an operator will compare this against speaks: {@code maxMinecartSpeed} defaults to 8 and
     * {@code NewMinecartBehavior#getMaxSpeed} divides it by 20 at the point of use. The navigator does the same
     * division, so nobody has to know what a tick is to read either number.
     * <p>
     * The default is the constant this replaced: 0.3 blocks per tick is 6 blocks per second, so a server that never
     * touches this setting behaves exactly as it did before.
     * <p>
     * What the boat <em>travels</em> is 0.9 of this. {@code AbstractBoat#tick} runs {@code floatBoat()} immediately
     * before {@code move(SELF, getDeltaMovement())}, and {@code floatBoat} multiplies the horizontal components by
     * {@code invFriction}, which is 0.9 while the status is {@code IN_WATER}. The navigator overwrites the velocity
     * every tick rather than adding to it, so there is no accumulation and no ramp-up: the displacement is a flat
     * {@code 0.9 * value / 20} blocks per tick from the first tick onwards. At the default 6 that is 0.27 blocks per
     * tick, 5.4 blocks per second over the water.
     */
    public static final double BOAT_SPEED_DEFAULT = 6.0D;

    /**
     * Floor of {@link #BOAT_SPEED_DEFAULT}, in blocks per second.
     * <p>
     * The hard stall is at 0.45: {@code MinecoloniesAdvancedPathNavigate.BOAT_PROGRESS} judges a boat held up when it
     * covers less than 0.02 blocks in a tick, and 0.9 * 0.45 / 20 is exactly that -- below it every boat in the world
     * reads as stuck in open water and is abandoned three seconds into its crossing. This is four and a half times
     * that, which leaves the stuck detector its job: a hull grinding along a bank keeps only the component of its
     * velocity parallel to the wall, and at this speed that still counts as progress until the heading is within
     * about 13 degrees of driving straight into it.
     */
    public static final double BOAT_SPEED_MIN = 2.0D;

    /**
     * Ceiling of {@link #BOAT_SPEED_DEFAULT}, in blocks per second, which is 0.9 blocks of travel per tick.
     * <p>
     * Not a physics limit. Vanilla collision is swept: {@code Entity#collide} gathers colliders from
     * {@code boundingBox.expandTowards(movement)} and {@code collideWithShapes} sweeps each axis over the whole
     * displacement, so a boat cannot tunnel through a bank at any speed -- it stops against it. The disembark radius
     * is not the limit either: the final approach passes within a block of the exit node, which is a chord of about
     * 3.5 blocks through the 2.0 block disk, so a tick step would have to exceed that to skip it.
     * <p>
     * The limit is the control loop. The navigator re-aims once per tick and the pathfinder's world is a one block
     * grid, so a boat that covers more than a block between corrections can no longer track that grid: it stops being
     * able to follow a channel whose bends are a block wide, and grinds along banks instead of turning at them. One
     * block of travel per tick is 22.2 blocks per second; this is rounded down from there.
     */
    public static final double BOAT_SPEED_MAX = 20.0D;

    /**
     * PORT-NOTE(26.2): how many visitors a tavern may hold, <em>per level of that tavern</em>. Replaces the literal 3
     * in {@code TavernBuildingModule#onColonyTick}, so the default leaves a level 1 tavern at 3 and a level 5 tavern
     * at 15, exactly as before.
     * <p>
     * Per level rather than a flat number on purpose: the tavern is the thing being upgraded, and a flat ceiling would
     * either crowd a hut that has not been built up yet or leave a finished one no busier than a fresh one. A
     * multiplier keeps the existing shape of the curve and only changes how steep it is.
     * <p>
     * 0 is allowed and turns visitors off altogether. Nothing else in the mod spawns them any more -- the cured
     * zombie villager path is dead in this port, see {@code EventHandler#onEntityConverted} -- so 0 also means no
     * recruitment, which is a real choice an operator might want to make and not an accident.
     */
    public static final int VISITORS_PER_TAVERN_LEVEL_DEFAULT = 3;

    /**
     * Ceiling of {@link #VISITORS_PER_TAVERN_LEVEL_DEFAULT}, which is 50 visitors at a level 5 tavern.
     * <p>
     * A visitor is a live {@code VisitorCitizen}: a pathfinder mob with six vanilla goals, a five state machine
     * stepping every 50 ticks, and a share of the pathfinding thread pool whenever it picks somewhere to walk. What
     * it does <em>not</em> have is the expensive half of a citizen -- no job AI, no request system, no per colony
     * tick data update ({@code VisitorManager#onColonyTick} only checks whether the body still exists, while
     * {@code CitizenManager} runs the full {@code CitizenData#update} on every citizen). Read that way one visitor is
     * worth roughly a third of a citizen, so 50 of them cost about what 17 more citizens cost, against a colony
     * ceiling of 250. That is a real bill but not an unreasonable one, and it is the largest number this setting can
     * be given.
     */
    public static final int VISITORS_PER_TAVERN_LEVEL_MAX = 10;

    /**
     * PORT-NOTE(26.2): scales every waiting period between visitor arrivals -- the opening delay on a new tavern, the
     * gap after each arrival, and the penalty after a visitor is killed. 1.0 is the behaviour this port shipped with.
     * <p>
     * It exists because the ceiling above is useless without it. The tavern module ticks once per 500 ticks and takes
     * 500 off its own timer each time, so the timer is in world ticks: a fresh tavern waits 10000 of them (over eight
     * minutes) for its first visitor, and a level 5 tavern in a full colony waits an average of 2700 (two and a
     * quarter minutes) between arrivals after that. Raising the ceiling to 50 without touching this fills the tavern
     * over about two hours of play, which reads as the ceiling not working rather than as the queue being slow.
     * <p>
     * Applied as a divisor on the amount taken off the timer per module tick rather than as a multiplier on the
     * timer's target, so it reaches all three of those waits from one place without any of them having to read the
     * config at construction time.
     * <p>
     * The floor it cannot get past is the module's own tick rate: one arrival per 500 ticks, 25 seconds, however
     * small this is set. Filling a ceiling of 50 therefore takes at least 20 minutes whatever happens.
     */
    public static final double VISITOR_INTERVAL_DEFAULT = 1.0D;

    /**
     * Floor of {@link #VISITOR_INTERVAL_DEFAULT}. At this value every wait collapses to the module's own 25 second
     * tick, so anything smaller would be the same setting spelled differently.
     */
    public static final double VISITOR_INTERVAL_MIN = 0.05D;

    /**
     * Ceiling of {@link #VISITOR_INTERVAL_DEFAULT}: five times the current waits, which puts a fresh tavern's first
     * visitor about 42 minutes out. For a server that wants visitors to be an event rather than a queue.
     */
    public static final double VISITOR_INTERVAL_MAX = 5.0D;

    /**
     * Base adult lifespan of the generational mechanic, in colony days.
     * <p>
     * A colony day is the unit the mod already keeps for long running colony state ({@code Colony#getDay()}), and it
     * only advances while the colony is loaded, so a town nobody visits does not age. One colony day is one in game
     * day of loaded play, twenty real minutes.
     * <p>
     * 100 was picked off the turnover arithmetic, not off a feeling. In a steady state colony the number of deaths a
     * day is population over lifespan, so 100 days puts a thirty citizen town at 0.3 funerals a day -- roughly one an
     * hour of play, which is an event -- and the 250 citizen ceiling at 2.5 a day, which is a graveyard that needs a
     * real undertaker. Below about 40 the smallest colonies start losing people faster than a school can raise
     * replacements; above a few hundred nothing is ever seen to happen. See {@code 26.2/GENERATIONS.md}.
     */
    public static final int GENERATIONS_LIFESPAN_DEFAULT = 100;

    /**
     * Floor of {@link #GENERATIONS_LIFESPAN_DEFAULT}. Seven colony days is a little over two hours of loaded play,
     * which is about as fast as a colony can turn over and still have anybody grow up in time to replace anyone.
     */
    public static final int GENERATIONS_LIFESPAN_MIN = 7;

    /**
     * Ceiling of {@link #GENERATIONS_LIFESPAN_DEFAULT}. Ten thousand colony days is over three thousand hours of
     * loaded play: effectively immortal, but reachable for somebody who wants the birth side of the mechanic without
     * the death side.
     */
    public static final int GENERATIONS_LIFESPAN_MAX = 10000;

    /**
     * Global multiplier on the generational birth rate. 1.0 is exact replacement in a colony whose living conditions
     * are neutral, because the birth rate is derived from the same population-over-lifespan figure the death rate is.
     */
    public static final double GENERATIONS_BIRTH_DEFAULT = 1.0D;

    /**
     * Floor of {@link #GENERATIONS_BIRTH_DEFAULT}: a tenth of replacement, which is a colony that dies out on purpose.
     */
    public static final double GENERATIONS_BIRTH_MIN = 0.1D;

    /**
     * Ceiling of {@link #GENERATIONS_BIRTH_DEFAULT}. Five times replacement in a well run colony is already at the
     * point where the school and the housing queue are the only things holding the population down.
     */
    public static final double GENERATIONS_BIRTH_MAX = 5.0D;

    /*  --------------------------------------------------------------------------- *
     *  ------------------- ######## Gameplay settings ######## ------------------- *
     *  --------------------------------------------------------------------------- */

    public final IntValue     initialCitizenAmount;
    public final BooleanValue allowInfiniteSupplyChests;
    public final BooleanValue allowInfiniteColonies;
    public final BooleanValue allowOtherDimColonies;
    public final IntValue     maxCitizenPerColony;
    public final BooleanValue enableInDevelopmentFeatures;
    public final BooleanValue alwaysRenderNameTag;
    public final BooleanValue workersAlwaysWorkInRain;
    public final IntValue     luckyBlockChance;
    public final IntValue     minThLevelToTeleport;
    public final DoubleValue  foodModifier;
    public final IntValue     diseaseModifier;
    public final IntValue     stuckRescueSeconds;
    public final BooleanValue forceLoadColony;
    public final IntValue     loadtime;
    public final IntValue     colonyLoadStrictness;
    public final BooleanValue forceLoadAllClaims;
    public final IntValue     maxForcedChunks;
    public final IntValue     maxBuilderDistance;
    public final BooleanValue decorationsOutsideColony;
    public final IntValue     maxTreeSize;
    public final BooleanValue noSupplyPlacementRestrictions;
    public final BooleanValue skyRaiders;
    public final BooleanValue airRaids;
    public final IntValue     airRaidChance;
    public final BooleanValue aircraftWarnings;
    public final IntValue     freeModeMaxFieldArea;
    public final IntValue     freeModeFarmerBatchSize;
    public final BooleanValue farmerPreparesGround;
    public final IntValue     maxFieldClaimDistance;
    public final BooleanValue animalPenContainment;
    public final IntValue     animalPenSlack;
    public final IntValue     animalPenRecallDistance;
    public final IntValue     visitorsPerTavernLevel;
    public final DoubleValue  visitorIntervalModifier;
    public final BooleanValue generations;
    public final IntValue     generationsLifespanDays;
    public final DoubleValue  generationsBirthModifier;

    /*  --------------------------------------------------------------------------- *
     *  ------------------- ######## Research settings ######## ------------------- *
     *  --------------------------------------------------------------------------- */
    public final BooleanValue                        researchCreativeCompletion;
    public final BooleanValue                        researchDebugLog;
    public final ConfigValue<List<? extends String>> researchResetCost;

    /*  --------------------------------------------------------------------------- *
     *  ------------------- ######## Command settings ######## ------------------- *
     *  --------------------------------------------------------------------------- */

    public final BooleanValue canPlayerUseRTPCommand;
    public final BooleanValue canPlayerUseColonyTPCommand;
    public final BooleanValue canPlayerUseAllyTHTeleport;
    public final BooleanValue canPlayerUseHomeTPCommand;
    public final BooleanValue canPlayerUseShowColonyInfoCommand;
    public final BooleanValue canPlayerUseKillCitizensCommand;
    public final BooleanValue canPlayerUseModifyCitizensCommand;
    public final BooleanValue canPlayerUseAddOfficerCommand;
    public final BooleanValue canPlayerUseDeleteColonyCommand;
    public final BooleanValue canPlayerUseResetCommand;

    /*  --------------------------------------------------------------------------- *
     *  ------------------- ######## Claim settings ######## ------------------- *
     *  --------------------------------------------------------------------------- */

    public final IntValue     maxColonySize;
    public final IntValue     maxOutlyingChunks;
    public final IntValue     minColonyDistance;
    public final IntValue     initialColonySize;
    public final IntValue     maxDistanceFromWorldSpawn;
    public final IntValue     minDistanceFromWorldSpawn;

    /*  ------------------------------------------------------------------------- *
     *  ------------------- ######## Combat Settings ######## ------------------- *
     *  ------------------------------------------------------------------------- */

    public final BooleanValue enableColonyRaids;
    public final IntValue     raidDifficulty;
    public final IntValue     maxRaiders;
    public final BooleanValue raidersbreakblocks;
    public final IntValue     averageNumberOfNightsBetweenRaids;
    public final IntValue     minimumNumberOfNightsBetweenRaids;
    public final BooleanValue raidersbreakdoors;
    public final BooleanValue mobAttackCitizens;
    public final DoubleValue  guardDamageMultiplier;
    public final DoubleValue  guardHealthMult;
    public final IntValue     guardVerticalVision;
    public final BooleanValue guardsFallAsleep;
    public final BooleanValue pvp_mode;

    /*  ----------------------------------------------------------------------------- *
     *  ------------------- ######## Permission Settings ######## ------------------- *
     *  ----------------------------------------------------------------------------- */

    public final BooleanValue          enableColonyProtection;
    public final EnumValue<Explosions> turnOffExplosionsInColonies;
    public final IntValue              permissionEventMinBypassPermLevel;

    /*  -------------------------------------------------------------------------------- *
     *  ------------------- ######## Compatibility Settings ######## ------------------- *
     *  -------------------------------------------------------------------------------- */

    public final BooleanValue                        auditCraftingTags;
    public final BooleanValue                        debugInventories;
    public final BooleanValue                        cavalryDebugLog;
    public final BooleanValue                        blueprintBuildMode;

    /*  ------------------------------------------------------------------------------ *
     *  ------------------- ######## Pathfinding Settings ######## ------------------- *
     *  ------------------------------------------------------------------------------ */

    public final IntValue minimumRailsToPath;
    public final IntValue minimumWaterToBoat;
    public final DoubleValue boatSpeed;
    public final DoubleValue pathNodeLimitMultiplier;
    public final IntValue maxPathfindingDistance;
    public final BooleanValue stopSearchOnArrival;

    /*  --------------------------------------------------------------------------------- *
     *  ------------------- ######## Request System Settings ######## ------------------- *
     *  --------------------------------------------------------------------------------- */

    public final BooleanValue creativeResolve;

    /**
     * Builds server configuration.
     *
     * @param builder config builder
     */
    public ServerConfiguration(final Builder builder)
    {
        super(builder, MOD_ID);
        final Predicate<Object> stringValidator = s -> s instanceof String;

        createCategory("gameplay");

        initialCitizenAmount = defineInteger("initialcitizenamount", 4, 1, 10);
        allowInfiniteSupplyChests = defineBoolean("allowinfinitesupplychests", false);
        allowInfiniteColonies = defineBoolean("allowinfinitecolonies", false);
        allowOtherDimColonies = defineBoolean("allowotherdimcolonies", true);
        maxCitizenPerColony = defineInteger("maxcitizenpercolony", 250, 25, CitizenConstants.CITIZEN_LIMIT_CONFIG_MAX);
        enableInDevelopmentFeatures = defineBoolean("enableindevelopmentfeatures", false);
        alwaysRenderNameTag = defineBoolean("alwaysrendernametag", true);
        workersAlwaysWorkInRain = defineBoolean("workersalwaysworkinrain", false);
        luckyBlockChance = defineInteger("luckyblockchance", 1, 0, 100);
        minThLevelToTeleport = defineInteger("minthleveltoteleport", 3, 0, 5);
        foodModifier = defineDouble("foodmodifier", 1.0, 0.1, 100);
        diseaseModifier = defineInteger("diseasemodifier", 5, 1, 100);
        stuckRescueSeconds = defineInteger("stuckrescueseconds", 60, 0, 3600);
        forceLoadColony = defineBoolean("forceloadcolony", true);
        loadtime = defineInteger("loadtime", 20, 1, 1440);
        colonyLoadStrictness = defineInteger("colonyloadstrictness", 3, 1, 15);
        forceLoadAllClaims = defineBoolean("forceloadallclaims", false);
        maxForcedChunks = defineInteger("maxforcedchunks", 1024, 0, 4096);
        maxBuilderDistance = defineInteger("maxbuilderdistance", 2000, 16, 5000);
        decorationsOutsideColony = defineBoolean("decorationsoutsidecolony", true);
        maxTreeSize = defineInteger("maxtreesize", 400, 1, 1000);
        noSupplyPlacementRestrictions = defineBoolean("nosupplyplacementrestrictions", false);
        skyRaiders = defineBoolean("skyraiders", false);
        airRaids = defineBoolean("airraids", true);
        airRaidChance = defineInteger("airraidchance", 25, 0, 100);
        aircraftWarnings = defineBoolean("aircraftwarnings", true);
        freeModeMaxFieldArea = defineInteger("freemodemaxfieldarea", 4096, 121, 1000000);
        freeModeFarmerBatchSize = defineInteger("freemodefarmerbatchsize", 3, 1, 8);
        farmerPreparesGround = defineBoolean("farmerpreparesground", true);
        maxFieldClaimDistance = defineInteger("maxfieldclaimdistance", 128, 0, 100000);
        animalPenContainment = defineBoolean("animalpencontainment", true);
        animalPenSlack = defineInteger("animalpenslack", 4, 0, 64);
        animalPenRecallDistance = defineInteger("animalpenrecalldistance", 32, 0, 256);
        visitorsPerTavernLevel = defineInteger("visitorspertavernlevel", VISITORS_PER_TAVERN_LEVEL_DEFAULT, 0, VISITORS_PER_TAVERN_LEVEL_MAX);
        visitorIntervalModifier = defineDouble("visitorintervalmodifier", VISITOR_INTERVAL_DEFAULT, VISITOR_INTERVAL_MIN, VISITOR_INTERVAL_MAX);
        generations = defineBoolean("generations", false);
        generationsLifespanDays = defineInteger("generationslifespandays", GENERATIONS_LIFESPAN_DEFAULT, GENERATIONS_LIFESPAN_MIN, GENERATIONS_LIFESPAN_MAX);
        generationsBirthModifier = defineDouble("generationsbirthmodifier", GENERATIONS_BIRTH_DEFAULT, GENERATIONS_BIRTH_MIN, GENERATIONS_BIRTH_MAX);

        swapToCategory("research");
        researchCreativeCompletion = defineBoolean("researchcreativecompletion", true);
        researchDebugLog = defineBoolean("researchdebuglog", false);
        researchResetCost = defineList("researchresetcost", () -> "item ID, possibly with data", stringValidator, "minecolonies:ancienttome:1");

        swapToCategory("commands");

        canPlayerUseRTPCommand = defineBoolean("canplayerusertpcommand", false);
        canPlayerUseColonyTPCommand = defineBoolean("canplayerusecolonytpcommand", false);
        canPlayerUseAllyTHTeleport = defineBoolean("canplayeruseallytownhallteleport", true);
        canPlayerUseHomeTPCommand = defineBoolean("canplayerusehometpcommand", false);
        canPlayerUseShowColonyInfoCommand = defineBoolean("canplayeruseshowcolonyinfocommand", true);
        canPlayerUseKillCitizensCommand = defineBoolean("canplayerusekillcitizenscommand", false);
        canPlayerUseModifyCitizensCommand = defineBoolean("canplayerusemodifycitizenscommand", false);
        canPlayerUseAddOfficerCommand = defineBoolean("canplayeruseaddofficercommand", true);
        canPlayerUseDeleteColonyCommand = defineBoolean("canplayerusedeletecolonycommand", false);
        canPlayerUseResetCommand = defineBoolean("canplayeruseresetcommand", false);

        swapToCategory("claims");

        maxColonySize = defineInteger("maxColonySize", 20, 1, 250);
        maxOutlyingChunks = defineInteger("maxoutlyingchunks", 256, 0, 100000);
        minColonyDistance = defineInteger("minColonyDistance", 8, 1, 200);
        initialColonySize = defineInteger("initialColonySize", 4, 1, 15);
        maxDistanceFromWorldSpawn = defineInteger("maxdistancefromworldspawn", 30000, 1000, Integer.MAX_VALUE);
        minDistanceFromWorldSpawn = defineInteger("mindistancefromworldspawn", 0, 0, 1000);

        swapToCategory("combat");

        enableColonyRaids = defineBoolean("dobarbariansspawn", true);
        raidDifficulty = defineInteger("barbarianhordedifficulty", DEFAULT_BARBARIAN_DIFFICULTY, MIN_BARBARIAN_DIFFICULTY, MAX_BARBARIAN_DIFFICULTY);
        maxRaiders = defineInteger("maxBarbarianSize", 80, MIN_BARBARIAN_HORDE_SIZE, MAX_BARBARIAN_HORDE_SIZE);
        raidersbreakblocks = defineBoolean("dobarbariansbreakthroughwalls", true);
        averageNumberOfNightsBetweenRaids = defineInteger("averagenumberofnightsbetweenraids", 14, 1, 50);
        minimumNumberOfNightsBetweenRaids = defineInteger("minimumnumberofnightsbetweenraids", 10, 1, 30);
        mobAttackCitizens = defineBoolean("mobattackcitizens", true);
        raidersbreakdoors = defineBoolean("shouldraiderbreakdoors", true);
        guardDamageMultiplier = defineDouble("guardDamageMultiplier", 1.0, 0.1, 15.0);
        guardHealthMult = defineDouble("guardhealthmult", 1.0, 0.1, 5.0);
        // How far up and down a guard's target scan reaches. Measured on a stand (GUARD-AUDIT.md 3.1):
        // at the stock 3 a knight standing four blocks from a zombie six blocks above it does not acquire
        // it at all -- zero target acquisitions in 250 s -- while an archer beside it engages, because
        // RangeCombatAI raises its own Y range to 28 while guarding. Raising this makes knights *decide*
        // differently, so it defaults to the stock value; it also enlarges the entity lookup box, whose
        // volume is linear in this number.
        guardVerticalVision = defineInteger("guardverticalvision", GuardConstants.Y_VISION, GuardConstants.Y_VISION, 64);
        // Guards nod off on duty at any hour, day or night, and other guards walk over and hit them awake.
        // Measured: 2.6-16.8 % of a guard's day asleep plus 1.3-5.0 % walking to wake a colleague.
        // Set false to switch the mechanic off; true is the stock behaviour.
        guardsFallAsleep = defineBoolean("guardsfallasleep", true);
        pvp_mode = defineBoolean("pvp_mode", false);

        swapToCategory("permissions");

        enableColonyProtection = defineBoolean("enablecolonyprotection", true);
        turnOffExplosionsInColonies = defineEnum("turnoffexplosionsincolonies", Explosions.DAMAGE_ENTITIES);
        permissionEventMinBypassPermLevel = defineInteger("permissioneventbypassminpermlevel", 2, 0, 4);

        swapToCategory("compatibility");

        auditCraftingTags = defineBoolean("auditcraftingtags", false);
        debugInventories = defineBoolean("debuginventories", false);
        cavalryDebugLog = defineBoolean("cavalrydebuglog", false);
        blueprintBuildMode = defineBoolean("blueprintbuildmode", false);

        pathNodeLimitMultiplier = defineDouble("pathNodeLimitMultiplier", 1, 1, 4);
        minimumRailsToPath = defineInteger("minimumrailstopath", 8, 5, 100);
        minimumWaterToBoat = defineInteger("minimumwatertoboat", 5, 2, 100);
        boatSpeed = defineDouble("boatspeed", BOAT_SPEED_DEFAULT, BOAT_SPEED_MIN, BOAT_SPEED_MAX);
        maxPathfindingDistance = defineInteger("maxpathfindingdistance", 2000, 100, 5000);
        stopSearchOnArrival = defineBoolean("stopsearchonarrival", true);

        swapToCategory("requestSystem");

        creativeResolve = defineBoolean("creativeresolve", false);

        finishCategory();
    }
}
