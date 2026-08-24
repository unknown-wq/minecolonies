package com.minecolonies.api.colony.jobs;

import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModJobs
{
    public static final Identifier PLACEHOLDER_ID    = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "placeholder");
    public static final Identifier BUILDER_ID        = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "builder");
    public static final Identifier DELIVERY_ID       = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "deliveryman");
    public static final Identifier MINER_ID          = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "miner");
    public static final Identifier LUMBERJACK_ID     = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "lumberjack");
    public static final Identifier FARMER_ID         = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "farmer");
    public static final Identifier UNDERTAKER_ID     = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "undertaker");
    public static final Identifier FISHERMAN_ID      = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "fisherman");
    public static final Identifier BAKER_ID          = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "baker");
    public static final Identifier COOK_ID           = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cook");
    public static final Identifier SHEPHERD_ID       = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "shepherd");
    public static final Identifier COWBOY_ID         = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cowboy");
    public static final Identifier SWINE_HERDER_ID   = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "swineherder");
    public static final Identifier CHICKEN_HERDER_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "chickenherder");
    public static final Identifier SMELTER_ID        = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "smelter");
    public static final Identifier ARCHER_ID         = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "ranger");
    public static final Identifier KNIGHT_ID         = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "knight");
    public static final Identifier MARKSMAN_ID       = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "marksman");
    public static final Identifier HUSCARL_ID        = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "huscarl");
    public static final Identifier CAVALRY_ID        = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cavalry");
    public static final Identifier COMPOSTER_ID      = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "composter");
    public static final Identifier STUDENT_ID        = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "student");
    public static final Identifier ARCHER_TRAINING_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "archertraining");
    public static final Identifier KNIGHT_TRAINING_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "combattraining");
    public static final Identifier SAWMILL_ID         = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sawmill");
    public static final Identifier BLACKSMITH_ID     = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "blacksmith");
    public static final Identifier STONEMASON_ID     = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stonemason");
    public static final Identifier STONE_SMELTERY_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stonesmeltery");
    public static final Identifier CRUSHER_ID        = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "crusher");
    public static final Identifier SIFTER_ID         = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "sifter");
    public static final Identifier FLORIST_ID        = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "florist");
    public static final Identifier ENCHANTER_ID      = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "enchanter");
    public static final Identifier RESEARCHER_ID     = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "researcher");
    public static final Identifier HEALER_ID         = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "healer");
    public static final Identifier PUPIL_ID          = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pupil");
    public static final Identifier TEACHER_ID        = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "teacher");
    public static final Identifier GLASSBLOWER_ID    = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "glassblower");
    public static final Identifier DYER_ID           = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "dyer");
    public static final Identifier FLETCHER_ID       = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "fletcher");
    public static final Identifier MECHANIC_ID       = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mechanic");
    public static final Identifier PLANTER_ID        = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "planter");
    public static final Identifier RABBIT_ID         = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "rabbitherder");
    public static final Identifier CONCRETE_ID       = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "concretemixer");
    public static final Identifier BEEKEEPER_ID      = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "beekeeper");
    public static final Identifier NETHERWORKER_ID   = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "netherworker");
    public static final Identifier QUARRY_MINER_ID   = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "quarrier");
    public static final Identifier DRUID_ID          = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "druid");
    public static final Identifier ALCHEMIST_ID      = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "alchemist");
    public static final Identifier CHEF_ID           = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "chef");
    public static final Identifier STABLEMASTER_ID   = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "stablemaster");

    @Deprecated
    public static final Identifier COOKASSISTANT_ID  = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cookassistant");

    public static Supplier<JobEntry> placeHolder;
    public static Supplier<JobEntry> builder;
    public static Supplier<JobEntry> delivery;
    public static Supplier<JobEntry> miner;
    public static Supplier<JobEntry> lumberjack;
    public static Supplier<JobEntry> farmer;
    public static Supplier<JobEntry> fisherman;
    public static Supplier<JobEntry> undertaker;
    public static Supplier<JobEntry> baker;
    public static Supplier<JobEntry> cook;
    public static Supplier<JobEntry> shepherd;
    public static Supplier<JobEntry> cowboy;
    public static Supplier<JobEntry> swineHerder;
    public static Supplier<JobEntry> chickenHerder;
    public static Supplier<JobEntry> smelter;
    public static Supplier<JobEntry> archer;
    public static Supplier<JobEntry> knight;
    public static Supplier<JobEntry> marksman;
    public static Supplier<JobEntry> huscarl;
    public static Supplier<JobEntry> cavalry;
    public static Supplier<JobEntry> composter;
    public static Supplier<JobEntry> student;
    public static Supplier<JobEntry> archerInTraining;
    public static Supplier<JobEntry> knightInTraining;
    public static Supplier<JobEntry> sawmill;
    public static Supplier<JobEntry> blacksmith;
    public static Supplier<JobEntry> stoneMason;
    public static Supplier<JobEntry> stoneSmeltery;
    public static Supplier<JobEntry> crusher;
    public static Supplier<JobEntry> sifter;
    public static Supplier<JobEntry> florist;
    public static Supplier<JobEntry> enchanter;
    public static Supplier<JobEntry> researcher;
    public static Supplier<JobEntry> healer;
    public static Supplier<JobEntry> pupil;
    public static Supplier<JobEntry> teacher;
    public static Supplier<JobEntry> glassblower;
    public static Supplier<JobEntry> dyer;
    public static Supplier<JobEntry> fletcher;
    public static Supplier<JobEntry> mechanic;
    public static Supplier<JobEntry> planter;
    public static Supplier<JobEntry> rabbitHerder;
    public static Supplier<JobEntry> concreteMixer;
    public static Supplier<JobEntry> beekeeper;
    public static Supplier<JobEntry> netherworker;
    public static Supplier<JobEntry> quarrier;
    public static Supplier<JobEntry> druid;
    public static Supplier<JobEntry> alchemist;
    public static Supplier<JobEntry> chef;
    public static Supplier<JobEntry> stablemaster;

    @Deprecated
    public static Supplier<JobEntry> cookassistant;

    /**
     * List of all jobs.
     */
    public static List<Identifier> jobs = new ArrayList<>() { };

    private ModJobs()
    {
        throw new IllegalStateException("Tried to initialize: ModJobs but this is a Utility class.");
    }

    public static List<Identifier> getJobs()
    {
        return jobs;
    }
}
