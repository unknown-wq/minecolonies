package com.minecolonies.core.commands.citizencommands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.entity.citizen.citizenhandlers.ICitizenSkillHandler;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.translation.CommandTranslationConstants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCColonyOfficerCommand;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.minecolonies.api.colony.ICitizenData.MAX_SATURATION;
import static com.minecolonies.api.util.constant.CitizenConstants.MAX_CITIZEN_LEVEL;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Maxes out every citizen of a colony: every skill to the level cap the game enforces ({@link com.minecolonies.api.util.constant.CitizenConstants#MAX_CITIZEN_LEVEL}),
 * saturation to {@link ICitizenData#MAX_SATURATION} and health back to full.
 * <p>
 * This is a cheat command and sits behind the same gate as {@link CommandCitizenModify}: colony manager (or op) rights, plus either op rights or the
 * {@code canplayerusemodifycitizenscommand} server config, plus a creative-mode player or the server console.
 */
public class CommandCitizenMaxStats implements IMCColonyOfficerCommand
{
    @NotNull
    @Override
    public String getName()
    {
        return "maxstats";
    }

    @NotNull
    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .executes(this::onExecute));
    }

    @Override
    public int onExecute(@NotNull final CommandContext<CommandSourceStack> context)
    {
        try
        {
            if (!checkPreCondition(context))
            {
                return 0;
            }

            if (!IMCCommand.hasOpPermission(context.getSource()) && !MineColonies.getConfig().getServer().canPlayerUseModifyCitizensCommand.get())
            {
                context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_DISABLED_IN_CONFIG), true);
                return 0;
            }

            // 26.2: CommandSourceStack#source is private; isPlayer() distinguishes console from player just as well for this check.
            if (context.getSource().isPlayer() && !context.getSource().getPlayer().isCreative())
            {
                context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_REQUIRES_CREATIVE), true);
                return 0;
            }

            final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
            final List<ICitizenData> citizens = colony.getCitizenManager().getCitizens();
            if (citizens.isEmpty())
            {
                context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_MAX_STATS_NO_CITIZENS, colony.getName()), true);
                return 0;
            }

            int changed = 0;
            for (final ICitizenData citizen : citizens)
            {
                if (maxOut(citizen))
                {
                    changed++;
                }
            }

            // Saturation feeds the colony wide social happiness modifier, so every citizen's cached happiness is stale now, not just the ones we touched.
            for (final ICitizenData citizen : citizens)
            {
                citizen.getCitizenHappinessHandler().invalidateCachedHappiness();
            }

            final int changedCount = changed;
            if (changedCount == 0)
            {
                context.getSource()
                  .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_MAX_STATS_NOTHING_TO_DO, colony.getName()), true);
                return 0;
            }

            context.getSource()
              .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_MAX_STATS_SUCCESS, changedCount, colony.getName()), true);
            return 1;
        }
        catch (Throwable e)
        {
            Log.getLogger().warn("Error during running command:", e);
            return 0;
        }
    }

    /**
     * Max out a single citizen and run the bookkeeping the regular level-up path would have run.
     *
     * @param citizen the citizen to max out.
     * @return true if anything actually changed.
     */
    private boolean maxOut(@NotNull final ICitizenData citizen)
    {
        final ICitizenSkillHandler skillHandler = citizen.getCitizenSkillHandler();

        boolean leveled = false;
        for (final Skill skill : Skill.values())
        {
            if (skillHandler.getLevel(skill) < MAX_CITIZEN_LEVEL)
            {
                // incrementLevel clamps to MAX_CITIZEN_LEVEL itself, which is the same clamp the xp path and the nbt reader use.
                skillHandler.incrementLevel(skill, MAX_CITIZEN_LEVEL);
                leveled = true;
            }
        }

        boolean changed = leveled;

        if (leveled)
        {
            // Same follow-up the xp based level up does: particles/sound plus IJob#onLevelUp, which is what recomputes level derived state such as the
            // guard bonus health granted by the stamina skill. Skipping it would leave those attributes on the pre-max values.
            skillHandler.levelUp(citizen);
        }

        if (citizen.getSaturation() < MAX_SATURATION)
        {
            citizen.setSaturation(MAX_SATURATION);
            changed = true;
        }

        // After onLevelUp, so that the max health already includes the level based modifiers we just refreshed.
        if (citizen.getEntity().isPresent())
        {
            final AbstractEntityCitizen entity = citizen.getEntity().get();
            if (entity.getHealth() < entity.getMaxHealth())
            {
                entity.setHealth(entity.getMaxHealth());
                changed = true;
            }
        }

        if (changed)
        {
            // Immediate sync: pushes the new skill map, saturation and happiness into the citizen view, and flags the colony so it gets saved.
            citizen.markDirty(0);
        }

        return changed;
    }
}
