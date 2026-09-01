package com.minecolonies.core.commands.citizencommands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import com.minecolonies.api.util.constant.translation.CommandTranslationConstants;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.minecolonies.core.commands.CommandArgumentNames.CITIZENID_ARG;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Puts one named citizen into one named job slot, immediately.
 * <p>
 * Every other way into a job goes through the hiring queue. A hut's assignment module hires on the colony tick, out
 * of {@code ICitizenManager#getJoblessCitizen}, and only when the hut's hiring mode allows it and the module is not
 * already full; the hut GUI's own hire button is the same call reached through {@code HireFireMessage}, and needs a
 * client. On a server with nobody connected there is no third way, so which of a guard tower's four jobs is filled
 * -- knight, ranger, marksman or huscarl -- is decided by a coin toss on a colony tick, and a test that needs a
 * <em>marksman</em> can only wait and hope. That is what this exists for.
 * <p>
 * It is the same assignment the GUI makes, {@code IAssignsJob#assignCitizen}, with two gates opened around it:
 * <ul>
 *     <li><b>The citizen's current job is given up first.</b> {@code AbstractJob#assignTo} refuses a module whose
 *     job entry is not the one the citizen already holds, so a knight can never become a ranger through the ordinary
 *     path -- the GUI never offers it one, because it lists only jobless citizens. Here the old post is vacated
 *     (the old module's {@code removeCitizen}, then {@code setJob(null)}) and the new job is created fresh.</li>
 *     <li><b>The hiring mode is not consulted.</b> Automatic hiring is a policy for who the colony picks on its own;
 *     an operator naming a citizen has already picked.</li>
 * </ul>
 * <p>
 * What is <em>not</em> stepped over is the slot count. A guard tower holds one guard, and
 * {@code GuardBuildingModule#isFull} counts the whole building rather than the module, so its four job modules share
 * that one slot. A full module is refused and the occupant named, because emptying it is a separate decision with
 * its own command ({@code /mc citizens fire}), and a hire that silently sacked somebody would be a poor thing to
 * type by accident.
 */
public class CommandCitizenHire implements IMCOPCommand
{
    /**
     * Where the hut block of the building to hire into stands.
     */
    private static final String POS_ARG = "position";

    /**
     * Which of that building's jobs to hire into.
     */
    private static final String JOB_ARG = "job";

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        return 0;
    }

    /**
     * Puts the citizen in the job slot.
     *
     * @param context the command context.
     * @return the command status.
     * @throws CommandSyntaxException if the position argument does not resolve.
     */
    private int hire(final CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final ICitizenData citizen = colony.getCitizenManager().getCivilian(IntegerArgumentType.getInteger(context, CITIZENID_ARG));
        if (citizen == null)
        {
            context.getSource().sendFailure(Component.translatableEscape(CommandTranslationConstants.COMMAND_CITIZEN_NOT_FOUND));
            return 0;
        }

        final BlockPos pos = BlockPosArgument.getSpawnablePos(context, POS_ARG);
        final IBuilding building = colony.getServerBuildingManager().getBuilding(pos);
        if (building == null)
        {
            context.getSource().sendFailure(Component.translatableEscape(CommandTranslationConstants.COMMAND_CITIZEN_HIRE_NO_BUILDING, pos.toShortString()));
            return 0;
        }

        final String job = StringArgumentType.getString(context, JOB_ARG);
        final IAssignsJob module = moduleFor(building, job);
        if (module == null)
        {
            context.getSource()
              .sendFailure(Component.translatableEscape(CommandTranslationConstants.COMMAND_CITIZEN_HIRE_NO_JOB, job, pos.toShortString(), String.join(", ", jobsOf(building))));
            return 0;
        }

        if (module.isFull())
        {
            context.getSource().sendFailure(Component.translatableEscape(CommandTranslationConstants.COMMAND_CITIZEN_HIRE_FULL, job, pos.toShortString(), occupants(building)));
            return 0;
        }

        release(citizen);
        citizen.setPaused(false);
        if (!module.assignCitizen(citizen))
        {
            context.getSource().sendFailure(Component.translatableEscape(CommandTranslationConstants.COMMAND_CITIZEN_HIRE_REFUSED, citizen.getId(), citizen.getName(), job));
            return 0;
        }

        context.getSource()
          .sendSuccess(() -> Component.translatableEscape(CommandTranslationConstants.COMMAND_CITIZEN_HIRE_SUCCESS,
            citizen.getId(),
            citizen.getName(),
            job,
            pos.toShortString()), true);
        return 1;
    }

    /**
     * Vacates whatever post the citizen currently holds, so a module with a different job entry will take him.
     *
     * @param citizen the citizen.
     */
    static void release(@NotNull final ICitizenData citizen)
    {
        final IBuilding old = citizen.getWorkBuilding();
        if (old != null)
        {
            for (final IAssignsJob module : old.getModulesByType(IAssignsJob.class))
            {
                if (module.hasAssignedCitizen(citizen))
                {
                    module.removeCitizen(citizen);
                }
            }
        }

        citizen.setJob(null);
    }

    /**
     * The building's job module for a job named by the path of its registry name, e.g. {@code marksman}.
     *
     * @param building the building.
     * @param job      the job name.
     * @return the module, or null if this building has no such job.
     */
    @Nullable
    private static IAssignsJob moduleFor(@NotNull final IBuilding building, @NotNull final String job)
    {
        for (final IAssignsJob module : building.getModulesByType(IAssignsJob.class))
        {
            if (module.getJobEntry().getKey().getPath().equals(job))
            {
                return module;
            }
        }

        return null;
    }

    /**
     * The names of every job this building offers.
     *
     * @param building the building.
     * @return the job names.
     */
    private static List<String> jobsOf(@NotNull final IBuilding building)
    {
        final List<String> jobs = new ArrayList<>();
        for (final IAssignsJob module : building.getModulesByType(IAssignsJob.class))
        {
            jobs.add(module.getJobEntry().getKey().getPath());
        }
        return jobs;
    }

    /**
     * Who is working at this building, for the message that refuses a full slot.
     *
     * @param building the building.
     * @return a readable list of citizen ids and names.
     */
    private static String occupants(@NotNull final IBuilding building)
    {
        final List<String> names = new ArrayList<>();
        for (final ICitizenData citizen : building.getAllAssignedCitizen())
        {
            names.add("#" + citizen.getId() + " " + citizen.getName());
        }
        return String.join(", ", names);
    }

    @Override
    public String getName()
    {
        return "hire";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .then(IMCCommand.newArgument(CITIZENID_ARG, IntegerArgumentType.integer(1))
                                 .then(IMCCommand.newArgument(POS_ARG, BlockPosArgument.blockPos())
                                         .then(IMCCommand.newArgument(JOB_ARG, StringArgumentType.word())
                                                 // The suggestions read the position that has already been parsed, so
                                                 // a tab at this point lists exactly the jobs that building offers.
                                                 .suggests((ctx, builder) -> {
                                                     final IColony colony = ColonyIdArgument.tryGetColony(ctx, COLONYID_ARG, false);
                                                     if (colony == null)
                                                     {
                                                         return builder.buildFuture();
                                                     }
                                                     final IBuilding building =
                                                       colony.getServerBuildingManager().getBuilding(BlockPosArgument.getSpawnablePos(ctx, POS_ARG));
                                                     return SharedSuggestionProvider.suggest(building == null ? List.of() : jobsOf(building), builder);
                                                 })
                                                 .executes(context -> checkPreConditionAndExecute(context, this::hire))))));
    }
}
