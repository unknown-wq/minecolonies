package com.minecolonies.core.commands.generalcommands;

import com.minecolonies.core.colony.HeadlessColonyMode;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;

/**
 * Lets the colonies on this server run with nobody logged in to watch them.
 * <p>
 * The problem it solves is described in {@link HeadlessColonyMode}: a colony reaches {@code ACTIVE} only while
 * somebody can see it, so on a server with no players every colony is inert -- no work manager, so a builder is
 * never handed the work order that is waiting for it, and no force-load timer, so the ground the colony stands on
 * stops ticking and its citizens stop existing. {@code /mc debug headless on} answers "should this colony run"
 * without inventing a viewer to answer it with.
 * <p>
 * <b>This command does not exist on an ordinary server.</b> The literal is added to the tree only when the JVM was
 * started with {@code -Dminecolonies.headless=true}, so without that property it is not there to be found, completed
 * or mistyped, and the mode cannot be reached at all. It is refused on an integrated server even with the property
 * set, it takes operator rights, and it is forgotten when the server stops -- nothing about it is written to a
 * colony, a config or any other file. See {@link HeadlessColonyMode} for why each of those is there.
 * <p>
 * It is a switch for a server that is being measured or driven from a console, not a way to run a colony while the
 * owner is away: every colony on the server ticks under it, and none of them will ever have been ticked by somebody
 * looking at them, which is the state the mod's timings assume.
 */
public class CommandHeadless implements IMCOPCommand
{
    /**
     * Report whether the mode is on.
     *
     * @param context the context of the command execution.
     * @return 1 always.
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        if (HeadlessColonyMode.isRunning())
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_HEADLESS_ON).withStyle(ChatFormatting.YELLOW), false);
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_HEADLESS_NOT_PERSISTED).withStyle(ChatFormatting.GRAY), false);
        }
        else
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_HEADLESS_OFF), false);
        }
        return 1;
    }

    /**
     * Switch the mode.
     *
     * @param context the context of the command execution.
     * @param on      the state asked for.
     * @return 1 if the mode is now what was asked for, 0 if it was refused.
     */
    private int set(final CommandContext<CommandSourceStack> context, final boolean on)
    {
        final HeadlessColonyMode.Result result = HeadlessColonyMode.set(context.getSource().getServer(), on);

        switch (result)
        {
            case NOT_ARMED ->
            {
                context.getSource().sendSuccess(() -> Component.translatable(COMMAND_HEADLESS_NOT_ARMED, HeadlessColonyMode.ARM_PROPERTY)
                                                        .withStyle(ChatFormatting.RED), false);
                return 0;
            }
            case NOT_DEDICATED ->
            {
                context.getSource().sendSuccess(() -> Component.translatable(COMMAND_HEADLESS_NOT_DEDICATED).withStyle(ChatFormatting.RED), false);
                return 0;
            }
            case ALREADY_ON -> context.getSource().sendSuccess(() -> Component.translatable(COMMAND_HEADLESS_ON).withStyle(ChatFormatting.YELLOW), false);
            case ALREADY_OFF -> context.getSource().sendSuccess(() -> Component.translatable(COMMAND_HEADLESS_OFF), false);
            case SWITCHED_ON ->
            {
                // Broadcast rather than whisper: on a server that does have players, everybody with the rights to
                // see command feedback should see that the rules just changed under them.
                context.getSource().sendSuccess(() -> Component.translatable(COMMAND_HEADLESS_SWITCHED_ON).withStyle(ChatFormatting.YELLOW), true);
                context.getSource().sendSuccess(() -> Component.translatable(COMMAND_HEADLESS_NOT_PERSISTED).withStyle(ChatFormatting.GRAY), false);

                // The mode makes a colony tick; which of its chunks stay loaded is a separate question that this
                // deliberately does not answer, and an operator who is not told that will read the first empty
                // result as the switch not working.
                context.getSource().sendSuccess(() -> Component.translatable(COMMAND_HEADLESS_CLAIMS_HINT).withStyle(ChatFormatting.GRAY), false);
            }
            case SWITCHED_OFF -> context.getSource().sendSuccess(() -> Component.translatable(COMMAND_HEADLESS_SWITCHED_OFF).withStyle(ChatFormatting.GREEN), true);
        }

        return 1;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "headless";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newLiteral("on").executes(context -> checkPreConditionAndExecute(context, ctx -> set(ctx, true))))
                 .then(IMCCommand.newLiteral("off").executes(context -> checkPreConditionAndExecute(context, ctx -> set(ctx, false))))
                 .executes(this::checkPreConditionAndExecute);
    }
}
