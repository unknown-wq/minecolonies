package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.colony.jobs.*;
import com.minecolonies.core.colony.jobs.guard.*;
import com.minecolonies.core.colony.jobs.views.CrafterJobView;
import com.minecolonies.core.colony.jobs.views.DefaultJobView;
import com.minecolonies.core.colony.jobs.views.DmanJobView;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

public final class ModJobsInitializer
{

    private ModJobsInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModJobsInitializer but this is a Utility class.");
    }

    static
    {
        ModJobs.placeHolder = register(ModJobs.PLACEHOLDER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobPlaceholder::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.PLACEHOLDER_ID)
          .createJobEntry());

        ModJobs.builder = register(ModJobs.BUILDER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobBuilder::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.BUILDER_ID)
          .createJobEntry());

        ModJobs.delivery = register(ModJobs.DELIVERY_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobDeliveryman::new)
          .setJobViewProducer(() -> DmanJobView::new)
          .setRegistryName(ModJobs.DELIVERY_ID)
          .createJobEntry());

        ModJobs.miner = register(ModJobs.MINER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobMiner::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.MINER_ID)
          .createJobEntry());

        ModJobs.lumberjack = register(ModJobs.LUMBERJACK_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobLumberjack::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.LUMBERJACK_ID)
          .createJobEntry());

        ModJobs.farmer = register(ModJobs.FARMER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobFarmer::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.FARMER_ID)
          .createJobEntry());

        ModJobs.undertaker = register(ModJobs.UNDERTAKER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobUndertaker::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.UNDERTAKER_ID)
          .createJobEntry());

        ModJobs.fisherman = register(ModJobs.FISHERMAN_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobFisherman::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.FISHERMAN_ID)
          .createJobEntry());

        ModJobs.baker = register(ModJobs.BAKER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobBaker::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.BAKER_ID)
          .createJobEntry());

        ModJobs.cook = register(ModJobs.COOK_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobCook::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.COOK_ID)
          .createJobEntry());

        ModJobs.shepherd = register(ModJobs.SHEPHERD_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobShepherd::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.SHEPHERD_ID)
          .createJobEntry());

        ModJobs.cowboy = register(ModJobs.COWBOY_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobCowboy::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.COWBOY_ID)
          .createJobEntry());

        ModJobs.stablemaster = register(ModJobs.STABLEMASTER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobStablemaster::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.STABLEMASTER_ID)
          .createJobEntry());

        ModJobs.swineHerder = register(ModJobs.SWINE_HERDER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobSwineHerder::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.SWINE_HERDER_ID)
          .createJobEntry());

        ModJobs.chickenHerder = register(ModJobs.CHICKEN_HERDER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobChickenHerder::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.CHICKEN_HERDER_ID)
          .createJobEntry());

        ModJobs.smelter = register(ModJobs.SMELTER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobSmelter::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.SMELTER_ID)
          .createJobEntry());

        ModJobs.archer = register(ModJobs.ARCHER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobRanger::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.ARCHER_ID)
          .createJobEntry());

        ModJobs.knight = register(ModJobs.KNIGHT_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobKnight::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.KNIGHT_ID)
          .createJobEntry());

        ModJobs.marksman = register(ModJobs.MARKSMAN_ID.getPath(), () -> new JobEntry.Builder()
            .setJobProducer(JobMarksman::new)
            .setJobViewProducer(() -> DefaultJobView::new)
            .setRegistryName(ModJobs.MARKSMAN_ID)
            .createJobEntry());

        ModJobs.huscarl = register(ModJobs.HUSCARL_ID.getPath(), () -> new JobEntry.Builder()
            .setJobProducer(JobHuscarl::new)
            .setJobViewProducer(() -> DefaultJobView::new)
            .setRegistryName(ModJobs.HUSCARL_ID)
            .createJobEntry());

        ModJobs.cavalry = register(ModJobs.CAVALRY_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobCavalry::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.CAVALRY_ID)
          .createJobEntry());

        ModJobs.composter = register(ModJobs.COMPOSTER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobComposter::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.COMPOSTER_ID)
          .createJobEntry());

        ModJobs.student = register(ModJobs.STUDENT_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobStudent::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.STUDENT_ID)
          .createJobEntry());

        ModJobs.archerInTraining = register(ModJobs.ARCHER_TRAINING_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobArcherTraining::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.ARCHER_TRAINING_ID)
          .createJobEntry());

        ModJobs.knightInTraining = register(ModJobs.KNIGHT_TRAINING_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobCombatTraining::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.KNIGHT_TRAINING_ID)
          .createJobEntry());

        ModJobs.sawmill = register(ModJobs.SAWMILL_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobSawmill::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.SAWMILL_ID)
          .createJobEntry());

        ModJobs.blacksmith = register(ModJobs.BLACKSMITH_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobBlacksmith::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.BLACKSMITH_ID)
          .createJobEntry());

        ModJobs.stoneMason = register(ModJobs.STONEMASON_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobStonemason::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.STONEMASON_ID)
          .createJobEntry());

        ModJobs.stoneSmeltery = register(ModJobs.STONE_SMELTERY_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobStoneSmeltery::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.STONE_SMELTERY_ID)
          .createJobEntry());

        ModJobs.crusher = register(ModJobs.CRUSHER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobCrusher::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.CRUSHER_ID)
          .createJobEntry());

        ModJobs.sifter = register(ModJobs.SIFTER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobSifter::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.SIFTER_ID)
          .createJobEntry());

        ModJobs.florist = register(ModJobs.FLORIST_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobFlorist::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.FLORIST_ID)
          .createJobEntry());

        ModJobs.enchanter = register(ModJobs.ENCHANTER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobEnchanter::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.ENCHANTER_ID)
          .createJobEntry());

        ModJobs.researcher = register(ModJobs.RESEARCHER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobResearch::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.RESEARCHER_ID)
          .createJobEntry());

        ModJobs.healer = register(ModJobs.HEALER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobHealer::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.HEALER_ID)
          .createJobEntry());

        ModJobs.pupil = register(ModJobs.PUPIL_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobPupil::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.PUPIL_ID)
          .createJobEntry());

        ModJobs.teacher = register(ModJobs.TEACHER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobTeacher::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.TEACHER_ID)
          .createJobEntry());

        ModJobs.glassblower = register(ModJobs.GLASSBLOWER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobGlassblower::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.GLASSBLOWER_ID)
          .createJobEntry());

        ModJobs.dyer = register(ModJobs.DYER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobDyer::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.DYER_ID)
          .createJobEntry());

        ModJobs.fletcher = register(ModJobs.FLETCHER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobFletcher::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.FLETCHER_ID)
          .createJobEntry());

        ModJobs.mechanic = register(ModJobs.MECHANIC_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobMechanic::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.MECHANIC_ID)
          .createJobEntry());

        ModJobs.planter = register(ModJobs.PLANTER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobPlanter::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.PLANTER_ID)
          .createJobEntry());

        ModJobs.rabbitHerder = register(ModJobs.RABBIT_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobRabbitHerder::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.RABBIT_ID)
          .createJobEntry());

        ModJobs.concreteMixer = register(ModJobs.CONCRETE_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobConcreteMixer::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.CONCRETE_ID)
          .createJobEntry());

        ModJobs.beekeeper = register(ModJobs.BEEKEEPER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobBeekeeper::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.BEEKEEPER_ID)
          .createJobEntry());

        ModJobs.cookassistant = register(ModJobs.COOKASSISTANT_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobChef::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.COOKASSISTANT_ID)
          .createJobEntry());

        ModJobs.netherworker = register(ModJobs.NETHERWORKER_ID.getPath(), () -> new JobEntry.Builder()
                              .setJobProducer(JobNetherWorker::new)
                              .setJobViewProducer(() -> CrafterJobView::new)
                              .setRegistryName(ModJobs.NETHERWORKER_ID)
                              .createJobEntry());

        ModJobs.quarrier = register(ModJobs.QUARRY_MINER_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobQuarrier::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.QUARRY_MINER_ID)
          .createJobEntry());

        ModJobs.druid = register(ModJobs.DRUID_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobDruid::new)
          .setJobViewProducer(() -> DefaultJobView::new)
          .setRegistryName(ModJobs.DRUID_ID)
          .createJobEntry());

        ModJobs.alchemist = register(ModJobs.ALCHEMIST_ID.getPath(), () -> new JobEntry.Builder()
          .setJobProducer(JobAlchemist::new)
          .setJobViewProducer(() -> CrafterJobView::new)
          .setRegistryName(ModJobs.ALCHEMIST_ID)
          .createJobEntry());

        ModJobs.chef = register(ModJobs.CHEF_ID.getPath(), () -> new JobEntry.Builder()
                                                                                                        .setJobProducer(JobChef::new)
                                                                                                        .setJobViewProducer(() -> CrafterJobView::new)
                                                                                                        .setRegistryName(ModJobs.CHEF_ID)
                                                                                                        .createJobEntry());
    }

    /**
     * Register a job at the deferred registry and store the job token in the job list.
     * @param path the path.
     * @param supplier the supplier of the entry.
     * @return the registry object.
     */
    private static Supplier<JobEntry> register(final String path, final Supplier<JobEntry> supplier)
    {
        final Identifier id = Identifier.fromNamespaceAndPath(Constants.MOD_ID, path);
        ModJobs.jobs.add(id);
        final JobEntry value = Registry.register(CommonMinecoloniesAPIImpl.JOB_REGISTRY, id, supplier.get());
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1); calling this from
     * the mod entry point is what pins the moment it happens -- and it must happen before anything reads
     * {@link ModJobs#getJobs()}, {@code ModSoundEvents} in particular.
     */
    public static void init()
    {
    }
}
