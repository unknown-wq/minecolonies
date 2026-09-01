package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.minecolonies.core.util.RaiderCampPlacer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_CAMP_FAILED;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_CAMP_PLACED;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Puts a raider camp near a colony, the same way the camp-clearing quest does.
 * <p>
 * Two uses. It is the only way to find out <em>why</em> a colony is not being offered the camp quest, or why the quest
 * cancelled itself the moment it was accepted: the failure this reports is the same one
 * {@link RaiderCampPlacer#place} hands the quest, so an operator gets an answer instead of a shrug. And it is how the
 * placement was tested at all -- creating a colony needs a player to place a town hall, but
 * {@code /mc colony territory create} makes a real colony object from the console, and this command drives the
 * placement against it without a client anywhere in the picture.
 * <p>
 * The camp it places is permanent and is not owned by the quest: clearing it clears it, and nothing puts it back.
 */
public class CommandColonyCamp implements IMCOPCommand
{
    /**
     * Optional band arguments, in blocks from the colony centre.
     */
    private static final String MIN_RANGE_ARG = "minRange";
    private static final String MAX_RANGE_ARG = "maxRange";

    /**
     * Same band the quest uses when its JSON does not say otherwise.
     */
    private static final int DEFAULT_MIN_RANGE = 120;
    private static final int DEFAULT_MAX_RANGE = 220;

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        return place(context, DEFAULT_MIN_RANGE, DEFAULT_MAX_RANGE);
    }

    /**
     * Run the placement and report either where it went or why it did not.
     *
     * @param context  the command context.
     * @param minRange closest the camp may be to the colony centre.
     * @param maxRange furthest.
     * @return 1 on success, 0 on failure.
     */
    private int place(final CommandContext<CommandSourceStack> context, final int minRange, final int maxRange)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final RaiderCampPlacer.Placement placement =
          RaiderCampPlacer.place(colony, RaiderCampPlacer.DEFAULT_CAMP, minRange, Math.max(minRange, maxRange));

        if (!placement.succeeded())
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_CAMP_FAILED,
              colony.getName(), placement.failure().name() + " -- " + placement.detail()));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_CAMP_PLACED,
          colony.getName(), placement.pos().toShortString()), true);
        return 1;
    }

    @Override
    public String getName()
    {
        return "camp";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .then(IMCCommand.newArgument(MIN_RANGE_ARG, IntegerArgumentType.integer(0, 2000))
                                 .then(IMCCommand.newArgument(MAX_RANGE_ARG, IntegerArgumentType.integer(0, 2000))
                                         .executes(ctx -> place(ctx,
                                           IntegerArgumentType.getInteger(ctx, MIN_RANGE_ARG),
                                           IntegerArgumentType.getInteger(ctx, MAX_RANGE_ARG)))))
                         .executes(this::checkPreConditionAndExecute));
    }
}
