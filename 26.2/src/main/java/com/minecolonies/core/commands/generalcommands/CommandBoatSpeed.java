package com.minecolonies.core.commands.generalcommands;

import com.minecolonies.api.configuration.ServerConfiguration;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.text.DecimalFormat;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_BOATSPEED_CURRENT;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_BOATSPEED_SET;

/**
 * Reads and sets how fast citizens steer a colony boat, without a config file edit or a restart.
 * <p>
 * The counterpart of vanilla's {@code maxMinecartSpeed}, and deliberately shaped like it: one number, in blocks per
 * second, that an operator can raise on a live server. It is a config value rather than a game rule because game rules
 * are per world and belong to vanilla's registry, while everything else this port made tunable already lives in
 * {@link ServerConfiguration} -- but the ergonomics an operator cares about are the same, which is what this command
 * exists to provide.
 * <p>
 * {@code /mc boatspeed} on its own reports, {@code /mc boatspeed <blocks per second>} changes. A change is picked up by
 * boats that are already sailing on their very next tick, because {@code MinecoloniesAdvancedPathNavigate#steerBoat}
 * reads the value every tick and overwrites the hull's velocity with it rather than accumulating an impulse. It is also
 * written back to {@code config/minecolonies-server.toml}, so it survives a restart.
 * <p>
 * Only the server ever reads this value -- navigation runs server side -- so nothing has to be pushed to connected
 * clients for the change to be complete.
 */
public class CommandBoatSpeed implements IMCOPCommand
{
    /**
     * Name of the number argument.
     */
    private static final String SPEED_ARG = "blockspersecond";

    /**
     * What {@code AbstractBoat#floatBoat} multiplies the horizontal velocity by before {@code move()} consumes it,
     * while the boat's status is {@code IN_WATER}. Reported alongside the configured number so an operator is not left
     * to wonder why a boat set to 6 covers 5.4 blocks in a second.
     */
    private static final double IN_WATER_DAMPING = 0.9D;

    /**
     * Trims the trailing zeroes a raw {@code %.2f} would leave on the round numbers this setting usually holds.
     */
    private static final DecimalFormat FORMAT = new DecimalFormat("0.##");

    /**
     * Report the current setting without changing it.
     *
     * @param context the context of the command execution.
     * @return 1 always.
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final double current = MineColonies.getConfig().getServer().boatSpeed.get();
        context.getSource().sendSuccess(() -> Component.translatable(COMMAND_BOATSPEED_CURRENT,
          FORMAT.format(current),
          FORMAT.format(current * IN_WATER_DAMPING),
          FORMAT.format(ServerConfiguration.BOAT_SPEED_MIN),
          FORMAT.format(ServerConfiguration.BOAT_SPEED_MAX)), false);
        return 1;
    }

    /**
     * Change the setting and persist it.
     *
     * @param context the context of the command execution.
     * @return 1 always.
     */
    private int setSpeed(final CommandContext<CommandSourceStack> context)
    {
        final double previous = MineColonies.getConfig().getServer().boatSpeed.get();

        MineColonies.getConfig().getServer().boatSpeed.set(DoubleArgumentType.getDouble(context, SPEED_ARG));
        MineColonies.getConfig().getServer().boatSpeed.save();

        // Read back rather than echoing what was asked for: DoubleValue#set clamps into the range it was declared
        // with, so this is the only number that is certainly the one boats will be steered at.
        final double current = MineColonies.getConfig().getServer().boatSpeed.get();
        context.getSource().sendSuccess(() -> Component.translatable(COMMAND_BOATSPEED_SET,
          FORMAT.format(previous),
          FORMAT.format(current),
          FORMAT.format(current * IN_WATER_DAMPING)).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "boatspeed";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(SPEED_ARG,
                     // Bounded here as well as in the config so that a number outside the range is refused with
                     // Brigadier's own error, naming the limits, instead of being silently clamped.
                     DoubleArgumentType.doubleArg(ServerConfiguration.BOAT_SPEED_MIN, ServerConfiguration.BOAT_SPEED_MAX))
                         .executes(context -> checkPreConditionAndExecute(context, this::setSpeed)))
                 .executes(this::checkPreConditionAndExecute);
    }
}
