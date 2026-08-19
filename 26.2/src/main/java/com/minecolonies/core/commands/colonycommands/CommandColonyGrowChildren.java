package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_GROW_CHILDREN_SUCCESS;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Grows up every child in a colony at once.
 * <p>
 * A child grows up on its own once its AI has been active long enough, which means a child in a corner of the world
 * nobody visits stays a child for as long as the save lives: the timer only runs while the entity ticks. That is fine
 * in play and awkward everywhere else -- a world left over from testing, a colony restored from a backup, a rescue
 * after something went wrong with the reproduction system -- where the alternative is killing them one by one and
 * waiting for the colony to breed replacements.
 * <p>
 * This is the same transition the AI performs, not a new one: {@code ICitizenData#setIsChild(false)}, which releases
 * the citizen to take a bed and a job of their own. Everything downstream -- the housing pass picking them up, the
 * texture and model swapping to the adult one -- follows from that, so a grown citizen here is indistinguishable
 * from one who grew up by living.
 */
public class CommandColonyGrowChildren implements IMCOPCommand
{
    /**
     * Grows up every child the colony has.
     *
     * @param context the command execution context.
     * @return {@code 1} always -- a colony with no children is a legitimate answer, not a failure.
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);

        int grown = 0;
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (citizen.isChild())
            {
                citizen.setIsChild(false);
                grown++;
            }
        }

        colony.markDirty();
        final int grownCount = grown;
        context.getSource().sendSuccess(
          () -> Component.translatable(COMMAND_COLONY_GROW_CHILDREN_SUCCESS, colony.getID(), colony.getName(), grownCount), true);
        return 1;
    }

    /**
     * Gets the literal name used to register this command.
     *
     * @return the command name.
     */
    @Override
    public String getName()
    {
        return "growChildren";
    }

    /**
     * Builds the command with its required colony selector argument.
     *
     * @return the command builder.
     */
    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id()).executes(this::checkPreConditionAndExecute));
    }
}
