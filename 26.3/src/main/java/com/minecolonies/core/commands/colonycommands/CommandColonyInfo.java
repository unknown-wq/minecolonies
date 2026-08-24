package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCColonyOfficerCommand;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_DISABLED_IN_CONFIG;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

public class CommandColonyInfo implements IMCColonyOfficerCommand
{
    public static final  String ID_TEXT           = "ID: ";
    public static final  String NAME_TEXT         = "Name: ";
    public static final String MAYOR_TEXT = "Mayor: ";
    private static final String COORDINATES_TEXT  = "Coordinates: ";
    private static final String COORDINATES_XYZ   = "x=%s y=%s z=%s";
    private static final String CITIZENS          = "Citizens: ";
    private static final String HOUSING_TEXT      = "  of which %s adults and %s children; %s beds, %s adults homeless";
    private static final String HOMELESS_CHILDREN_TEXT = "  %s children have no home either: their parents have none to share";
    private static final String LAST_CONTACT_TEXT = "Last contact with Owner or Officer: %d hours ago!";
    private static final String IS_DELETABLE      = "If true this colony cannot be deleted: ";
    private static final String CANNOT_BE_RAIDED  = "This colony is unable to be raided";

    /**
     * What happens when the command is executed after preConditions are successful.
     *
     * @param context the context of the command execution
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);

        if (!IMCCommand.hasOpPermission(context.getSource()) && !MineColonies.getConfig().getServer().canPlayerUseShowColonyInfoCommand.get())
        {
            context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_DISABLED_IN_CONFIG), true);
            return 0;
        }

        final BlockPos position = colony.getCenter();
        context.getSource().sendSuccess(() -> Component.literal(ID_TEXT + colony.getID() + " " + NAME_TEXT + colony.getName()), true);
        final String mayor = colony.getPermissions().getOwnerName();
        context.getSource().sendSuccess(() -> Component.literal(MAYOR_TEXT + mayor), true);
        context.getSource()
          .sendSuccess(() -> Component.literal(CITIZENS + colony.getCitizenManager().getCurrentCitizenCount() + "/" + colony.getCitizenManager().getMaxCitizens()), true);

        // "Citizens: 13/10" stopped being readable as "three of them are homeless" the moment children stopped
        // holding a bed, so the arithmetic that reconciles the two numbers is spelled out rather than left to be
        // inferred: the population is adults plus children, only the adults take beds, and the homeless are counted
        // one by one rather than derived from population minus beds.
        int childCount = 0;
        int homelessCount = 0;
        int homelessChildCount = 0;
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizensUnmodifiable())
        {
            if (citizen.isChild())
            {
                childCount++;
                if (citizen.getHomeBuilding() == null)
                {
                    homelessChildCount++;
                }
            }
            else if (citizen.getHomeBuilding() == null)
            {
                homelessCount++;
            }
        }
        final int children = childCount;
        final int homeless = homelessCount;
        final int homelessChildren = homelessChildCount;
        context.getSource()
          .sendSuccess(() -> Component.literal(String.format(HOUSING_TEXT,
            colony.getCitizenManager().getCurrentCitizenCount() - children,
            children,
            colony.getCitizenManager().getMaxCitizens(),
            homeless)), true);

        if (homelessChildren > 0)
        {
            // A child with no home means its parents had none either. Worth saying: it is the one case where the new
            // birth rule cannot house a child, and it reads as a bug otherwise.
            context.getSource().sendSuccess(() -> Component.literal(String.format(HOMELESS_CHILDREN_TEXT, homelessChildren)).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)),
              true);
        }

        context.getSource()
          .sendSuccess(() -> Component.literal(COORDINATES_TEXT + String.format(COORDINATES_XYZ, position.getX(), position.getY(), position.getZ())).setStyle(Style.EMPTY.withColor(
            ChatFormatting.GREEN)), true);
        context.getSource().sendSuccess(() -> Component.literal(String.format(LAST_CONTACT_TEXT, colony.getLastContactInHours())), true);

        if (!colony.getRaiderManager().canHaveRaiderEvents())
        {
            context.getSource().sendSuccess(() -> Component.literal(CANNOT_BE_RAIDED), true);
        }

        return 1;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "info";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id()).executes(this::checkPreConditionAndExecute));
    }
}
