package com.minecolonies.core.commands.citizencommands;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IBuildingWorkerModule;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.colony.jobs.registry.JobEntry;
import com.minecolonies.api.colony.managers.interfaces.ICitizenManager;
import com.minecolonies.api.eventbus.events.colony.citizens.CitizenAddedModEvent;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.buildings.modules.ChildrenBuildingModule;
import com.minecolonies.core.colony.buildings.modules.LivingBuildingModule;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.util.BuildingUtils;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;
import static com.minecolonies.core.commands.CommandArgumentNames.CHILDREN_ARG;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Fills a colony up: spawns citizens until it is at its maximum population, then hires every one of them that it can into an empty job slot, so that as many distinct worker
 * AIs as possible are running at once. Purely a testing aid - the point is to get all forty-odd job AIs ticking in one colony.
 * <p>
 * Everything goes through the colony's own citizen manager and through the buildings' assignment modules, so the modules keep enforcing their own rules about who may take
 * which job (children only in the school, couriers only in a warehouse, and so on).
 * <p>
 * With {@code children} appended, the same fill spawns children instead of adults, so a colony can be started as a
 * generation to be raised rather than as a finished workforce.
 */
public class CommandCitizenFill implements IMCOPCommand
{
    /**
     * Upper bound on spawn attempts, so a colony that cannot place citizens (nothing loaded to spawn into) cannot spin here.
     */
    private static final int MAX_SPAWN_ATTEMPTS = 2000;

    /**
     * How many job names to name in chat before the list is cut off.
     */
    private static final int JOB_NAME_CAP = 15;

    /**
     * What happens when the command is executed after preConditions are successful.
     *
     * @param context the context of the command execution
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        return fill(context, false);
    }

    /**
     * The {@code children} form: same fill, but everything it spawns is a child.
     * <p>
     * An argument on {@code fill} rather than a command of its own, because everything except the one
     * {@code setIsChild} call is shared -- the cap, the spawn loop, the hiring pass and all five of the report lines.
     *
     * @param context the context of the command execution.
     * @return 1 on success.
     */
    private int onExecuteChildren(final CommandContext<CommandSourceStack> context)
    {
        return fill(context, true);
    }

    /**
     * Fill the colony to its population cap and hire whoever can be hired.
     *
     * @param context  the context of the command execution.
     * @param children whether to spawn children rather than adults.
     * @return 1 on success.
     */
    private int fill(final CommandContext<CommandSourceStack> context, final boolean children)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final ICitizenManager citizenManager = colony.getCitizenManager();

        // The cap depends on the beds the colony currently has, and building levels may have changed since the last recalculation.
        citizenManager.calculateMaxCitizens();

        final int target = MineColonies.getConfig().getServer().maxCitizenPerColony.get();

        final int spawned = spawn(colony, citizenManager, target, children);

        // Run unchanged for the children form as well, and it is not the contradiction it sounds like. Every job
        // module except the school's refuses a child outright -- see candidates() -- so the pass can only put the new
        // children into the school, and the adults who were already in the colony keep being hired into their jobs
        // exactly as before. Hiring a town of children therefore means filling the school and nothing else.
        final int hired = hire(colony);

        context.getSource()
          .sendSuccess(() -> Component.translatable(children ? COMMAND_CITIZEN_FILL_SUCCESS_CHILDREN : COMMAND_CITIZEN_FILL_SUCCESS, spawned, hired, colony.getName()), true);

        if (children && spawned > 0)
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_CITIZEN_FILL_CHILDREN_NOTE).withStyle(ChatFormatting.GRAY), true);
        }

        final int room = target - citizenManager.getCurrentCitizenCount();
        if (room > 0)
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_CITIZEN_FILL_SPAWN_BLOCKED, room).withStyle(ChatFormatting.YELLOW), true);
        }
        else if (spawned == 0)
        {
            // Without this the command reports "spawned 0, hired 0" and says nothing about why, which reads as a broken
            // command rather than as a colony that is simply full.
            context.getSource()
              .sendSuccess(() -> Component.translatable(COMMAND_CITIZEN_FILL_AT_CAP, citizenManager.getCurrentCitizenCount(), target).withStyle(ChatFormatting.YELLOW),
                true);
        }

        // Who is actually homeless, not population minus beds. Those two stopped being the same number when children
        // stopped holding a bed: a colony of ten adults and three children in their parents' houses has thirteen
        // citizens, ten beds and nobody homeless, and subtracting would have reported three.
        int homelessCount = 0;
        for (final ICitizenData citizen : citizenManager.getCitizens())
        {
            if (citizen.getHomeBuilding() == null && !citizen.isChild())
            {
                homelessCount++;
            }
        }
        final int homeless = homelessCount;
        if (homeless > 0)
        {
            context.getSource()
              .sendSuccess(() -> Component.translatable(COMMAND_CITIZEN_FILL_HOMELESS, homeless, citizenManager.getMaxCitizens()).withStyle(ChatFormatting.YELLOW), true);
        }

        int unemployed = 0;
        for (final ICitizenData citizen : citizenManager.getCitizens())
        {
            if (citizen.getJob() == null && !citizen.isChild())
            {
                unemployed++;
            }
        }
        if (unemployed > 0)
        {
            final int stillUnemployed = unemployed;
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_CITIZEN_FILL_UNEMPLOYED, stillUnemployed), true);
        }

        reportMissingJobs(context, colony);
        return 1;
    }

    /**
     * Spawn citizens until the colony reaches the given population.
     * <p>
     * Deliberately not {@link ICitizenManager#getMaxCitizens()}. That is the smallest of the colony's beds, its citizen
     * cap research and the {@code maxcitizenpercolony} config, and the bed count is the binding one in practice -- so
     * aiming at it means building a residence for every citizen before this command can do anything. Since the whole
     * point is to skip that, only the config is honoured here. Nothing else enforces the cap: the spawn call itself
     * only checks the town hall's move-in setting, which {@code force} already bypasses.
     *
     * @param colony         the colony.
     * @param citizenManager its citizen manager.
     * @param target         how many citizens the colony should end up with.
     * @param children       whether the spawned citizens should be children.
     * @return how many citizens were actually spawned.
     */
    private int spawn(@NotNull final IColony colony, @NotNull final ICitizenManager citizenManager, final int target, final boolean children)
    {
        // Houses that already have an adult in them, which is what a child needs to be born into. Collected once and
        // used round robin so a hundred children do not all end up in the first house; without this every child the
        // command makes is permanently homeless, because the automatic housing pass deliberately skips children and
        // there is no birth here to give them a family home.
        final List<IBuilding> families = new ArrayList<>();
        if (children)
        {
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                if (building.getBuildingLevel() > 0
                      && building.hasModule(LivingBuildingModule.class)
                      && building.getFirstModuleOccurance(LivingBuildingModule.class).getResidentCount() > 0)
                {
                    families.add(building);
                }
            }
        }

        int spawned = 0;
        int attempts = 0;
        while (citizenManager.getCurrentCitizenCount() < target && attempts++ < MAX_SPAWN_ATTEMPTS)
        {
            final int before = citizenManager.getCurrentCitizenCount();

            // force = true so the town hall "move in" setting does not silently block us; the loop condition is what stops us at the target.
            final ICitizenData citizen = citizenManager.spawnOrCreateCivilian(null, colony.getWorld(), new ArrayList<>(), true);
            if (citizen == null || citizenManager.getCurrentCitizenCount() <= before)
            {
                // Nowhere loaded to place the citizen. Trying again would give the same answer.
                break;
            }

            if (children)
            {
                // spawnOrCreateCivilian only ever makes an adult. One call now: ICitizenData#setIsChild propagates to
                // the loaded entity itself, which is what installs the child AI, sizes the hitbox and re-resolves the
                // model. It did not use to, which is why this had to reach for the entity by hand -- and why anything
                // that forgot to ended up with a full sized citizen in a worker's skin.
                citizen.setIsChild(true);

                // Into a house that has adults in it, the way a birth would. Costs that house nothing -- a child holds
                // no bed -- and it is what keeps the homelessness penalty and the seven day complaint off a child the
                // command made. A colony with no inhabited residence yet leaves them homeless, correctly.
                if (!families.isEmpty())
                {
                    families.get(spawned % families.size()).getFirstModuleOccurance(LivingBuildingModule.class).assignCitizen(citizen);
                }
            }

            IMinecoloniesAPI.getInstance().getEventBus().post(new CitizenAddedModEvent(citizen, CitizenAddedModEvent.CitizenAddedSource.COMMANDS));
            spawned++;
        }
        return spawned;
    }

    /**
     * Hire every citizen we can into an empty job slot.
     * <p>
     * Two passes, because the two kinds of job assignment module want different candidates. A {@link IBuildingWorkerModule} creates the job itself, so it takes an unemployed
     * citizen. The remaining modules (the warehouse's courier slots and the quarry's miner slot) only re-home a citizen who already holds that job from another building, so
     * they have to run after the first pass has created those jobs.
     *
     * @param colony the colony.
     * @return how many citizens were hired.
     */
    private int hire(@NotNull final IColony colony)
    {
        final List<IBuilding> buildings = new ArrayList<>(colony.getServerBuildingManager().getBuildings().values());

        int hired = 0;
        for (final IBuilding building : buildings)
        {
            hired += hire(colony, building, true);
        }
        for (final IBuilding building : buildings)
        {
            hired += hire(colony, building, false);
        }
        return hired;
    }

    /**
     * Hire into one building's job modules.
     *
     * @param colony     the colony.
     * @param building   the building.
     * @param jobCreator true for the modules that create the job themselves, false for the ones that re-home an existing job holder.
     * @return how many citizens were hired here.
     */
    private int hire(@NotNull final IColony colony, @NotNull final IBuilding building, final boolean jobCreator)
    {
        if (!building.canAssignCitizens())
        {
            return 0;
        }

        // Hut blocks can carry job= tags that restrict which jobs may live there; the auto hiring path honours these, so we do too.
        final Predicate<JobEntry> allowed = BuildingUtils.getAllowedJobs(colony.getWorld(), building.getPosition());

        int hired = 0;
        for (final IAssignsJob module : building.getModulesByType(IAssignsJob.class))
        {
            if ((module instanceof IBuildingWorkerModule) != jobCreator || module.isFull() || !allowed.test(module.getJobEntry()))
            {
                continue;
            }

            for (final ICitizenData candidate : candidates(colony, module, jobCreator))
            {
                if (module.isFull())
                {
                    break;
                }
                if (module.assignCitizen(candidate))
                {
                    hired++;
                }
            }
        }
        return hired;
    }

    /**
     * Work out who may take the job slots of a module.
     *
     * @param colony     the colony.
     * @param module     the module to fill.
     * @param jobCreator whether the module creates the job itself.
     * @return the candidates, best first.
     */
    private List<ICitizenData> candidates(@NotNull final IColony colony, @NotNull final IAssignsJob module, final boolean jobCreator)
    {
        final List<ICitizenData> candidates = new ArrayList<>();

        if (!jobCreator)
        {
            // The warehouse and the quarry only take a citizen who already holds their job and is not already placed in a module of the same kind elsewhere.
            final Set<Integer> taken = new HashSet<>();
            for (final IBuilding other : colony.getServerBuildingManager().getBuildings().values())
            {
                for (final IAssignsJob otherModule : other.getModulesByType(module.getClass()))
                {
                    otherModule.getAssignedCitizen().forEach(citizen -> taken.add(citizen.getId()));
                }
            }

            for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
            {
                if (citizen.getJob() != null && citizen.getJob().getJobRegistryEntry().equals(module.getJobEntry()) && !taken.contains(citizen.getId()))
                {
                    candidates.add(citizen);
                }
            }
            return candidates;
        }

        // The school wants children, every other job module refuses them.
        final boolean wantsChildren = module instanceof ChildrenBuildingModule;
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (citizen.isChild() != wantsChildren || citizen.getWorkBuilding() != null)
            {
                continue;
            }
            // Either unemployed, or holding this very job without a building to do it in - the module can take both, and the second case is worth repairing.
            if (citizen.getJob() == null || citizen.getJob().getJobRegistryEntry().equals(module.getJobEntry()))
            {
                candidates.add(citizen);
            }
        }

        // Give the module its best fit first, the same way a player hiring by hand would.
        if (module instanceof final IBuildingWorkerModule workerModule)
        {
            candidates.sort(Comparator.comparingInt((final ICitizenData citizen) ->
              citizen.getCitizenSkillHandler().getLevel(workerModule.getPrimarySkill()) * 2
                + citizen.getCitizenSkillHandler().getLevel(workerModule.getSecondarySkill())).reversed());
        }
        return candidates;
    }

    /**
     * Report which job types are still not being worked, split by whether the colony even has a building for them.
     *
     * @param context the command context.
     * @param colony  the colony.
     */
    private void reportMissingJobs(@NotNull final CommandContext<CommandSourceStack> context, @NotNull final IColony colony)
    {
        final Set<JobEntry> worked = new HashSet<>();
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (citizen.getJob() != null)
            {
                worked.add(citizen.getJob().getJobRegistryEntry());
            }
        }

        final Set<JobEntry> available = new HashSet<>();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            for (final IAssignsJob module : building.getModulesByType(IAssignsJob.class))
            {
                available.add(module.getJobEntry());
            }
        }

        final Set<String> unmanned = new TreeSet<>();
        for (final JobEntry entry : available)
        {
            if (!worked.contains(entry))
            {
                unmanned.add(entry.getKey().getPath());
            }
        }

        final Set<String> noBuilding = new TreeSet<>();
        for (final JobEntry entry : IMinecoloniesAPI.getInstance().getJobRegistry())
        {
            // The placeholder job is an internal sentinel, not something a citizen is ever supposed to do.
            if (!available.contains(entry) && !worked.contains(entry) && !entry.getKey().equals(ModJobs.PLACEHOLDER_ID))
            {
                noBuilding.add(entry.getKey().getPath());
            }
        }

        if (unmanned.isEmpty())
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_CITIZEN_FILL_NO_MISSING_JOBS).withStyle(ChatFormatting.GREEN), true);
        }
        else
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_CITIZEN_FILL_MISSING_JOBS, unmanned.size(), join(unmanned)), true);
        }

        if (!noBuilding.isEmpty())
        {
            context.getSource()
              .sendSuccess(() -> Component.translatable(COMMAND_CITIZEN_FILL_NO_BUILDING_JOBS, noBuilding.size(), join(noBuilding)).withStyle(ChatFormatting.GRAY), true);
        }
    }

    /**
     * Join a set of job names for chat, cut off at {@link #JOB_NAME_CAP}.
     *
     * @param names the names.
     * @return the joined string.
     */
    private static String join(@NotNull final Set<String> names)
    {
        final List<String> shown = names.stream().limit(JOB_NAME_CAP).toList();
        return String.join(", ", shown) + (names.size() > shown.size() ? ", ..." : "");
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "fill";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .executes(this::checkPreConditionAndExecute)
                         .then(IMCCommand.newLiteral(CHILDREN_ARG).executes(ctx -> checkPreConditionAndExecute(ctx, this::onExecuteChildren))));
    }
}
