package com.minecolonies.core.commands.generalcommands;

import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.MutableComponent;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_HELP_INFO_DISCORD;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_HELP_INFO_WIKI;

public class CommandHelp implements IMCCommand
{

    private static final String wikiUrl    = "https://wiki.minecolonies.ldtteam.com";
    private static final String discordUrl = "https://discord.minecolonies.com";

    /**
     * What happens when the command is executed
     *
     * @param context the context of the command execution
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final Entity sender = context.getSource().getEntity();
        if (!(sender instanceof Player))
        {
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_HELP_INFO_WIKI), true);
        // 26.2/Fabric: NeoForge's CommonHooks#newChatWithLinks (auto-linkify a URL in chat) has no
        // counterpart; the link is built explicitly with a click event instead.
        context.getSource().sendSuccess(() -> link(wikiUrl).append(Component.literal("\n")), true);
        context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_HELP_INFO_DISCORD), true);
        context.getSource().sendSuccess(() -> link(discordUrl), true);

        return 1;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "help";
    }

    /**
     * Build a clickable chat link, replacing NeoForge's {@code CommonHooks#newChatWithLinks}.
     *
     * @param url the target url.
     * @return the component.
     */
    private static MutableComponent link(final String url)
    {
        return Component.literal(url).withStyle(style -> style
          .withColor(net.minecraft.ChatFormatting.BLUE)
          .withUnderlined(true)
          .withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(url))));
    }
}
