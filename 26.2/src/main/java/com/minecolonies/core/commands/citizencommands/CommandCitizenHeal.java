package com.minecolonies.core.commands.citizencommands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
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

import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Cures every citizen of a colony and puts them back to full health.
 * <p>
 * A sick citizen stops working and walks to the hospital, and so does a badly hurt one, which makes a freshly spawned
 * colony grind to a halt for reasons that have nothing to do with what is being tested. This clears both at once.
 * <p>
 * This is a cheat command and sits behind the same gate as {@link CommandCitizenMaxStats}: colony manager (or op)
 * rights, plus either op rights or the {@code canplayerusemodifycitizenscommand} server config, plus a creative-mode
 * player or the server console.
 */
public class CommandCitizenHeal implements IMCColonyOfficerCommand
{
    @NotNull
    @Override
    public String getName()
    {
        return "heal";
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
                context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_HEAL_NO_CITIZENS, colony.getName()), true);
                return 0;
            }

            int cured = 0;
            int healed = 0;
            for (final ICitizenData citizen : citizens)
            {
                if (citizen.getCitizenDiseaseHandler().isSick())
                {
                    // The handler's own cure: it clears the disease, wakes the citizen out of a hospital bed, tells the
                    // hospital about it, counts the colony statistic and grants the usual post-cure immunity. Setting
                    // the disease to null by hand would skip all of that.
                    citizen.getCitizenDiseaseHandler().cure();
                    cured++;
                }

                if (citizen.getEntity().isPresent())
                {
                    final AbstractEntityCitizen entity = citizen.getEntity().get();
                    if (entity.getHealth() < entity.getMaxHealth())
                    {
                        entity.setHealth(entity.getMaxHealth());
                        citizen.markDirty(0);
                        healed++;
                    }
                }
            }

            if (cured == 0 && healed == 0)
            {
                context.getSource()
                  .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_HEAL_NOTHING_TO_DO, colony.getName()), true);
                return 0;
            }

            final int curedCount = cured;
            final int healedCount = healed;
            context.getSource()
              .sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_HEAL_SUCCESS, curedCount, healedCount, colony.getName()), true);

            // The cure grants immunity, but only for a while, and a citizen out of immunity can fall ill again on any
            // tick. Say so rather than leaving the impression that one run settles it for good.
            context.getSource().sendSuccess(() -> Component.translatable(CommandTranslationConstants.COMMAND_CITIZEN_HEAL_IMMUNITY), true);
            return 1;
        }
        catch (Throwable e)
        {
            Log.getLogger().warn("Error during running command:", e);
            return 0;
        }
    }
}
