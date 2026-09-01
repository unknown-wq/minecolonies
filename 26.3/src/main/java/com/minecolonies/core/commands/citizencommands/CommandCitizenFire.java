package com.minecolonies.core.commands.citizencommands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.constant.translation.CommandTranslationConstants;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.minecolonies.core.commands.CommandArgumentNames.CITIZENID_ARG;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Takes one named citizen out of the job he holds, immediately.
 * <p>
 * The counterpart to {@code /mc citizens hire}, and its usual prerequisite: a guard tower holds one guard between its
 * four job modules, so the tower has to be emptied before a different kind of guard can be put in it. Without a
 * client there is no other way to do that -- the hut GUI's fire button is the only other caller of
 * {@code IAssignsJob#removeCitizen}.
 * <p>
 * The citizen keeps living in the colony and goes back on the jobless list, which is where automatic hiring draws
 * from, so a colony left to itself will eventually re-employ him somewhere.
 */
public class CommandCitizenFire implements IMCOPCommand
{
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final ICitizenData citizen = colony.getCitizenManager().getCivilian(IntegerArgumentType.getInteger(context, CITIZENID_ARG));
        if (citizen == null)
        {
            context.getSource().sendFailure(Component.translatableEscape(CommandTranslationConstants.COMMAND_CITIZEN_NOT_FOUND));
            return 0;
        }

        final IBuilding workBuilding = citizen.getWorkBuilding();
        if (workBuilding == null && citizen.getJob() == null)
        {
            context.getSource().sendFailure(Component.translatableEscape(CommandTranslationConstants.COMMAND_CITIZEN_FIRE_NO_JOB, citizen.getId(), citizen.getName()));
            return 0;
        }

        final String was = citizen.getJob() == null ? "?" : citizen.getJob().getJobRegistryEntry().getKey().getPath();
        CommandCitizenHire.release(citizen);

        context.getSource()
          .sendSuccess(() -> Component.translatableEscape(CommandTranslationConstants.COMMAND_CITIZEN_FIRE_SUCCESS, citizen.getId(), citizen.getName(), was), true);
        return 1;
    }

    @Override
    public String getName()
    {
        return "fire";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .then(IMCCommand.newArgument(CITIZENID_ARG, IntegerArgumentType.integer(1)).executes(this::checkPreConditionAndExecute)));
    }
}
