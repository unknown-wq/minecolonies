package com.minecolonies.core.commands.citizencommands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.pathfinding.IMinecoloniesNavigator;
import com.minecolonies.api.util.constant.translation.CommandTranslationConstants;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.managers.CitizenAging;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCColonyOfficerCommand;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

import static com.minecolonies.core.commands.CommandArgumentNames.CITIZENID_ARG;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Displays information about a chosen citizen in a chosen colony.
 */
public class CommandCitizenInfo implements IMCColonyOfficerCommand
{
    /**
     * What happens when the command is executed after preConditions are successful.
     *
     * @param context the context of the command execution
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final ICitizenData citizenData = colony.getCitizenManager().getCivilian(IntegerArgumentType.getInteger(context, CITIZENID_ARG));

        if (citizenData == null)
        {
            context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_NOT_FOUND), false);
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO, citizenData.getId(), citizenData.getName()), false);
        final Optional<AbstractEntityCitizen> optionalEntityCitizen = citizenData.getEntity();

        if (optionalEntityCitizen.isPresent())
        {
            final AbstractEntityCitizen entityCitizen = optionalEntityCitizen.get();

            final BlockPos citizenPosition = entityCitizen.blockPosition();
            context.getSource()
              .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_POSITION,
                citizenPosition.getX(),
                citizenPosition.getY(),
                  citizenPosition.getZ()).withStyle(styleWithTeleport(citizenPosition)), false);

            context.getSource()
                .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_HEALTH, entityCitizen.getHealth(), entityCitizen.getMaxHealth()), false);
        }
        else
        {
            context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_POSITION,
              citizenData.getLastPosition().getX(),
              citizenData.getLastPosition().getY(),
                citizenData.getLastPosition().getZ()).withStyle(styleWithTeleport(citizenData.getLastPosition())), false);

            context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_NOT_LOADED), false);
        }

        // getHomePosition() falls back to the nearest tavern for a citizen with no residence, so printing it alone
        // told the player the citizen lives in the tavern and gave them no way to tell that apart from a citizen who
        // really does. Ask getHomeBuilding() -- the thing the homelessness penalty and the complaints read -- and say
        // which of the two this is.
        final BlockPos homePosition = citizenData.getHomePosition();
        if (citizenData.getHomeBuilding() == null)
        {
            context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_HOMELESS), false);
            if (homePosition != null)
            {
                context.getSource()
                  .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_SLEEPS_POSITION,
                    homePosition.getX(),
                    homePosition.getY(),
                    homePosition.getZ()).withStyle(styleWithTeleport(homePosition)), false);
            }
        }
        else
        {
            context.getSource()
              .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_HOME_POSITION,
                homePosition.getX(),
                homePosition.getY(),
                homePosition.getZ()).withStyle(styleWithTeleport(homePosition)), false);

            if (citizenData.isChild())
            {
                context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_HOME_WITH_PARENTS), false);
            }
        }

        // Parents carry an id as well as a name now, so this line is what lets a player actually go and find one:
        // the id it prints is the argument to another /mc citizens info.
        final String parentsLine = describeParents(citizenData);
        if (!parentsLine.isEmpty())
        {
            context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_PARENTS, parentsLine), false);
        }

        if (citizenData.getWorkBuilding() == null)
        {
            context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_NO_WORKING_POSITION), false);
        }
        else
        {
            final BlockPos workingPosition = citizenData.getWorkBuilding().getPosition();
            context.getSource()
              .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_WORKING_POSITION,
                workingPosition.getX(),
                workingPosition.getY(),
                  workingPosition.getZ()).withStyle(styleWithTeleport(workingPosition)), false);
        }

        if (citizenData.getJob() == null)
        {
            context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_NO_JOB), false);
            context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_NO_ACTIVITY), false);
        }
        else if (citizenData.getWorkBuilding() != null && citizenData.getWorkBuilding().hasModule(WorkerBuildingModule.class))
        {
            context.getSource()
              .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_JOB,
                  citizenData.getWorkBuilding().getFirstModuleOccurance(WorkerBuildingModule.class).getJobEntry().getTranslationKey()), false);

            if (optionalEntityCitizen.isPresent())
            {
                final AbstractEntityCitizen entityCitizen = optionalEntityCitizen.get();
                entityCitizen.getCitizenJobHandler().getWorkAI().getStateAI().setHistoryEnabled(true, 16);
                context.getSource()
                    .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_ACTIVITY,
                        ((EntityCitizen) entityCitizen).getCitizenAI().getHistory(),
                        entityCitizen.getCitizenJobHandler().getColonyJob().getNameTagDescription(),
                        entityCitizen.getCitizenJobHandler().getWorkAI().getStateAI().getHistory()), false);
            }
        }

        if (optionalEntityCitizen.isPresent())
        {
            final AbstractEntityCitizen entityCitizen = optionalEntityCitizen.get();
            context.getSource()
                .sendSuccess(() -> Component.literal("Stuck level: " + ((IMinecoloniesNavigator) entityCitizen.getNavigation()).getStuckHandler().getStuckLevel()), false);
        }

        // Only when the generational mechanic is on: with it off there is no age to report and the line would be noise.
        // This is the one place a player can see how far through its life a citizen is, which is what makes the
        // mechanic checkable without waiting a hundred colony days for the answer.
        if (CitizenAging.isEnabled() && citizenData.getAgeDays() != ICitizenData.AGE_UNKNOWN)
        {
            context.getSource()
              .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_AGE,
                String.format("%.1f", citizenData.getAgeDays()),
                String.format("%.0f", CitizenAging.lifeExpectancy(citizenData))), false);
        }

        if (citizenData.getCitizenFoodHandler() != null)
        {
            String lastEaten = "";

            for (final Item item : citizenData.getCitizenFoodHandler().getLastEatenFoods())
            {
                ItemStack stack = new ItemStack(item);
                lastEaten = lastEaten + stack.getHoverName().getString() + ", ";
            }

            // A citizen that has never eaten has an empty food history, and substring(0, -2) throws -- which killed
            // the whole command halfway through its output, on exactly the citizen someone is inspecting to find out
            // why a worker is idle.
            final String lastEatenCompiled = lastEaten.isEmpty() ? "" : lastEaten.substring(0, lastEaten.length() - 2);

            context.getSource()
                .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_INFO_FOOD,
                    citizenData.getCitizenFoodHandler().hasFullFoodHistory(),
                    citizenData.getCitizenFoodHandler().getFoodHappinessStats().quality(),
                    citizenData.getCitizenFoodHandler().getFoodHappinessStats().diversity(),
                    lastEatenCompiled), false);
        }

        return 1;
    }

    /**
     * The parents of a citizen, as one line, with an id where the colony still has that parent.
     * <p>
     * A parent with an id can be looked up with this same command; a parent with only a name is either dead or born
     * before parent ids were stored, and there is nothing to look up. Both are said in the same shape so the player
     * can see which is which.
     *
     * @param citizen the citizen.
     * @return the line, or empty when the citizen has no recorded parents at all.
     */
    private static String describeParents(final ICitizenData citizen)
    {
        final String first = describeParent(citizen.getParents().getA(), citizen.getParentIds().getA());
        final String second = describeParent(citizen.getParents().getB(), citizen.getParentIds().getB());

        if (first.isEmpty())
        {
            return second;
        }
        return second.isEmpty() ? first : first + ", " + second;
    }

    /**
     * One parent of {@link #describeParents(ICitizenData)}.
     *
     * @param name the recorded name.
     * @param id   the recorded id, or {@link ICitizenData#NO_PARENT}.
     * @return "Marie (#12)", "Marie" or the empty string.
     */
    private static String describeParent(final String name, final int id)
    {
        if (name.isEmpty())
        {
            return "";
        }
        return id == ICitizenData.NO_PARENT ? name : name + " (#" + id + ")";
    }

    /**
     * Creates a style with clickable teleport
     *
     * @param pos
     * @return
     */
    private static Style styleWithTeleport(final BlockPos pos)
    {
        return Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
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
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .then(IMCCommand.newArgument(CITIZENID_ARG, IntegerArgumentType.integer(1)).executes(this::checkPreConditionAndExecute)));
    }
}
