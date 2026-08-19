package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.compatibility.Compatibility;
import com.minecolonies.api.compatibility.simpleplanes.AircraftCompat;
import com.minecolonies.api.compatibility.simpleplanes.AntiAirSettings;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.commands.arguments.MultiColonyIdArgument;
import com.minecolonies.core.commands.arguments.MultipleOptionsArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_AMMO;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_DAMAGE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_DAMAGE_SET;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_MINLEVEL;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_MINLEVEL_SET;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_NO_COLONY;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_NONE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_OUT_OF_BOUNDS;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_RANGE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_RANGE_SET;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_RATE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_RATE_ROUNDED;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_RATE_SATURATED;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_RATE_SET;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_RESET;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_SETTINGS;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_SETTINGS_DEFAULT;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_SUMMARY;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_TOWER;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_TP_DRY;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_TP_NO_PLAYER;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_ANTIAIR_TP_STOCKED;
import static com.minecolonies.core.commands.CommandArgumentNames.ANTIAIR_DAMAGE;
import static com.minecolonies.core.commands.CommandArgumentNames.ANTIAIR_LOCATE;
import static com.minecolonies.core.commands.CommandArgumentNames.ANTIAIR_MINLEVEL;
import static com.minecolonies.core.commands.CommandArgumentNames.ANTIAIR_RANGE;
import static com.minecolonies.core.commands.CommandArgumentNames.ANTIAIR_RATE;
import static com.minecolonies.core.commands.CommandArgumentNames.ANTIAIR_RESET;
import static com.minecolonies.core.commands.CommandArgumentNames.ANTIAIR_SETTINGS;
import static com.minecolonies.core.commands.CommandArgumentNames.ANTIAIR_TP;
import static com.minecolonies.core.commands.CommandArgumentNames.ANTIAIR_VALUE;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Finds the guard towers that shoot at aircraft, takes you to the one that needs arrows, and tunes what
 * they do.
 *
 * <h2>Why this exists</h2>
 * The anti-air battery tells the colony when a tower has run out of arrows, and hangs the tower's
 * position off that message as a hover. A hover is the fast path and it is also the fragile one: it
 * scrolls out of the chat log, it needs the mouse, and it only ever names one tower. This is the
 * reliable path — ask at any time, get every emplacement with its stock, and be put next to the one
 * that is empty.
 *
 * <h2>Shape</h2>
 * {@code where} and {@code tp} are deliberately the same two verbs as {@code /mc colony raid <colony>
 * where|tp}, and behave the same: {@code where} reports and changes nothing, {@code tp} moves you to
 * whichever one the report would have pointed at. Bare, with no verb, it reports — the same choice
 * {@link CommandColonyBlastProtection} makes.
 *
 * <p>The tuning verbs — {@code range}, {@code rate}, {@code damage}, {@code minlevel} — take the same
 * shape one level down: <b>with a value they set, without one they report.</b> That is
 * {@code blastprotection}'s rule again, and it is also the structural reason an omitted number cannot
 * be mistaken for zero. There is no path from "no argument" to a setter; brigadier routes the two
 * cases to different methods.
 *
 * <h2>Refusing rather than clamping</h2>
 * The value argument is deliberately <em>unbounded</em> at the brigadier level and checked here
 * instead. Bounded brigadier arguments do refuse, but they refuse with a parser message that names a
 * number and nothing else. Every bound in {@link AntiAirSettings} exists for a stated reason — the
 * scan cost, the ballistic reach, the aircraft's damage-immunity window, what a stray arrow does to a
 * citizen — and a server owner who has just been told no is exactly the person who needs to be told
 * why. Nothing is ever silently moved to the nearest legal value.
 *
 * <h2>With no aircraft mod</h2>
 * The emplacement half of the command comes through {@link AircraftCompat}, whose default
 * implementation returns no emplacements at all, so a colony without Simple Planes reports "no
 * anti-air positions" — the truth rather than a stub. The tuning half still works, because the
 * settings are MineColonies' own state rather than the aircraft mod's: they are saved with the colony
 * on every build, and are simply not read by anything until an aircraft mod is installed. No aircraft
 * type is named here, which is the rule the whole compat split rests on.
 */
public class CommandColonyAntiAir implements IMCOPCommand
{
    /**
     * Bare {@code /mc colony antiair <colony>}: report.
     *
     * @param context the context of the command execution.
     * @return the command status.
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        return onExecuteLocate(context);
    }

    /**
     * Says where every anti-air position of the colony is and how many arrows it has.
     *
     * @param context the command context.
     * @return the command status.
     */
    private int onExecuteLocate(final CommandContext<CommandSourceStack> context)
    {
        for (final IColony colony : MultiColonyIdArgument.getColonies(context, COLONYID_ARG))
        {
            final List<AircraftCompat.Emplacement> towers = Compatibility.aircraftCompat.emplacements(colony);
            if (towers.isEmpty())
            {
                sendNone(context, colony);
                continue;
            }

            int dryCount = 0;
            for (final AircraftCompat.Emplacement tower : towers)
            {
                if (tower.arrows() == 0)
                {
                    dryCount++;
                }
            }

            final int dry = dryCount;
            context.getSource()
              .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_SUMMARY, colony.getName(), towers.size(), dry), true);

            for (final AircraftCompat.Emplacement tower : towers)
            {
                final BlockPos pos = tower.position();
                context.getSource()
                  .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_TOWER,
                    tower.name(),
                    BlockPosUtil.calcDirection(colony.getCenter(), pos).getLongText(),
                    (int) BlockPosUtil.dist(pos, colony.getCenter()),
                    CommandRaid.posToString(pos),
                    tower.arrows()), true);
            }
        }
        return 1;
    }

    /**
     * "This colony has no anti-air positions", naming the level threshold the colony is actually using
     * rather than the shipped one — otherwise a colony with {@code minlevel 5} would be told its level-4
     * towers should have batteries.
     */
    private static void sendNone(final CommandContext<CommandSourceStack> context, final IColony colony)
    {
        final int minLevel = colony instanceof final Colony server
                               ? server.getAntiAirSettings().getMinTowerLevel()
                               : AntiAirSettings.DEFAULT_MIN_TOWER_LEVEL;
        context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_NONE, colony.getName(), minLevel), true);
    }

    /**
     * Puts the player at the tower that needs arrows.
     *
     * <p>Nearest to the colony centre, of the dry ones — the same tower the "no arrows" chat message
     * names, so the message and the command never disagree about which one is meant. When nothing is
     * dry the nearest emplacement is used instead and the reply says so, rather than refusing: someone
     * who typed this wanted to stand at a battery.
     *
     * @param context the command context.
     * @return the command status.
     */
    private int onExecuteTeleport(final CommandContext<CommandSourceStack> context)
    {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player))
        {
            context.getSource().sendFailure(Component.translatableEscape(COMMAND_ANTIAIR_TP_NO_PLAYER));
            return 0;
        }

        for (final IColony colony : MultiColonyIdArgument.getColonies(context, COLONYID_ARG))
        {
            final List<AircraftCompat.Emplacement> towers = Compatibility.aircraftCompat.emplacements(colony);
            if (towers.isEmpty())
            {
                sendNone(context, colony);
                continue;
            }

            final AircraftCompat.Emplacement dry = nearest(towers, colony.getCenter(), true);
            final AircraftCompat.Emplacement target = dry != null ? dry : nearest(towers, colony.getCenter(), false);
            if (target == null)
            {
                continue;
            }

            final BlockPos pos = target.position();
            CommandRaid.teleport(player, pos.above());

            if (dry != null)
            {
                context.getSource()
                  .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_TP_DRY,
                    target.name(),
                    (int) BlockPosUtil.dist(pos, colony.getCenter()),
                    CommandRaid.posToString(pos)), true);
            }
            else
            {
                context.getSource()
                  .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_TP_STOCKED,
                    colony.getName(),
                    target.name(),
                    CommandRaid.posToString(pos)), true);
            }
            return 1;
        }
        return 1;
    }

    /**
     * The whole tuning readout: every knob, its value, the shipped default and the bounds, plus what the
     * current rate of fire costs in arrows.
     *
     * @param context the command context.
     * @return the command status.
     */
    private int onExecuteSettings(final CommandContext<CommandSourceStack> context)
    {
        for (final IColony colony : MultiColonyIdArgument.getColonies(context, COLONYID_ARG))
        {
            if (!(colony instanceof final Colony serverColony))
            {
                context.getSource().sendFailure(Component.translatableEscape(COMMAND_ANTIAIR_NO_COLONY));
                continue;
            }

            final AntiAirSettings tuning = serverColony.getAntiAirSettings();
            context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_SETTINGS, colony.getName()), true);
            if (tuning.isDefault())
            {
                context.getSource().sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_SETTINGS_DEFAULT), true);
            }
            reportRange(context, tuning);
            reportRate(context, tuning);
            reportDamage(context, tuning);
            reportMinLevel(context, tuning);
            reportAmmo(context, tuning);
        }
        return 1;
    }

    /**
     * Puts every knob back to the number the battery shipped with.
     *
     * @param context the command context.
     * @return the command status.
     */
    private int onExecuteReset(final CommandContext<CommandSourceStack> context)
    {
        for (final IColony colony : MultiColonyIdArgument.getColonies(context, COLONYID_ARG))
        {
            if (!(colony instanceof final Colony serverColony))
            {
                context.getSource().sendFailure(Component.translatableEscape(COMMAND_ANTIAIR_NO_COLONY));
                continue;
            }

            serverColony.getAntiAirSettings().reset();
            serverColony.markAntiAirDirty();
            context.getSource()
              .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_RESET,
                colony.getName(),
                format(AntiAirSettings.DEFAULT_RANGE),
                format(AntiAirSettings.rateForInterval(AntiAirSettings.DEFAULT_SHOT_INTERVAL)),
                format(AntiAirSettings.DEFAULT_DAMAGE),
                AntiAirSettings.DEFAULT_MIN_TOWER_LEVEL), true);
        }
        return 1;
    }

    /**
     * {@code range} with no value.
     */
    private int onReportRange(final CommandContext<CommandSourceStack> context)
    {
        return forEachTuning(context, (ctx, colony) -> {
            reportRange(ctx, colony.getAntiAirSettings());
            return 1;
        });
    }

    /**
     * {@code range <blocks>}.
     */
    private int onSetRange(final CommandContext<CommandSourceStack> context)
    {
        final double blocks = DoubleArgumentType.getDouble(context, ANTIAIR_VALUE);
        if (!AntiAirSettings.check(blocks, AntiAirSettings.MIN_RANGE, AntiAirSettings.MAX_RANGE))
        {
            return refuse(context, ANTIAIR_RANGE, format(AntiAirSettings.MIN_RANGE), format(AntiAirSettings.MAX_RANGE), format(blocks));
        }

        return forEachTuning(context, (ctx, colony) -> {
            colony.getAntiAirSettings().setRange(blocks);
            colony.markAntiAirDirty();
            ctx.getSource()
              .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_RANGE_SET, colony.getName(), format(blocks)), true);
            return 1;
        });
    }

    /**
     * {@code rate} with no value.
     */
    private int onReportRate(final CommandContext<CommandSourceStack> context)
    {
        return forEachTuning(context, (ctx, colony) -> {
            reportRate(ctx, colony.getAntiAirSettings());
            reportAmmo(ctx, colony.getAntiAirSettings());
            return 1;
        });
    }

    /**
     * {@code rate <arrows per second>}.
     *
     * <p>This is the one setter that answers with more than it was asked, because it is the one whose
     * request cannot always be honoured. A rate is a tick count in disguise: the battery counts ticks and
     * nothing else, so a requested rate is rounded to the nearest whole-tick interval and the achievable
     * rate is whatever that interval delivers. When the two differ the reply says so, naming both — a
     * command that accepted 3.0 and quietly fired at 2.86 would be claiming a precision it does not have.
     *
     * <p>It also warns above two arrows a second, which is the point at which the aircraft's own
     * ten-tick damage-immunity window starts throwing rounds away. That is not a bound — over-firing is
     * legitimate against several targets or a manoeuvring one — but it is a cost the person setting it
     * should know he is paying.
     */
    private int onSetRate(final CommandContext<CommandSourceStack> context)
    {
        final double requested = DoubleArgumentType.getDouble(context, ANTIAIR_VALUE);
        if (!AntiAirSettings.check(requested, AntiAirSettings.MIN_RATE, AntiAirSettings.MAX_RATE))
        {
            return refuse(context, ANTIAIR_RATE, format(AntiAirSettings.MIN_RATE), format(AntiAirSettings.MAX_RATE), format(requested));
        }

        final int interval = AntiAirSettings.intervalForRate(requested);
        final double achieved = AntiAirSettings.rateForInterval(interval);

        return forEachTuning(context, (ctx, colony) -> {
            colony.getAntiAirSettings().setShotInterval(interval);
            colony.markAntiAirDirty();
            ctx.getSource()
              .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_RATE_SET, colony.getName(), format(achieved), interval), true);
            if (format(achieved).equals(format(requested)))
            {
                // The rounding is invisible at the precision the player can see, so saying it would be noise.
            }
            else
            {
                ctx.getSource()
                  .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_RATE_ROUNDED,
                    format(requested),
                    interval,
                    format(achieved)), true);
            }
            if (achieved > SATURATION_RATE)
            {
                ctx.getSource()
                  .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_RATE_SATURATED, format(SATURATION_RATE)), true);
            }
            reportAmmo(ctx, colony.getAntiAirSettings());
            return 1;
        });
    }

    /**
     * {@code damage} with no value.
     */
    private int onReportDamage(final CommandContext<CommandSourceStack> context)
    {
        return forEachTuning(context, (ctx, colony) -> {
            reportDamage(ctx, colony.getAntiAirSettings());
            return 1;
        });
    }

    /**
     * {@code damage <hit points>}.
     */
    private int onSetDamage(final CommandContext<CommandSourceStack> context)
    {
        final double value = DoubleArgumentType.getDouble(context, ANTIAIR_VALUE);
        if (!AntiAirSettings.check(value, AntiAirSettings.MIN_DAMAGE, AntiAirSettings.MAX_DAMAGE))
        {
            return refuse(context, ANTIAIR_DAMAGE, format(AntiAirSettings.MIN_DAMAGE), format(AntiAirSettings.MAX_DAMAGE), format(value));
        }

        return forEachTuning(context, (ctx, colony) -> {
            colony.getAntiAirSettings().setDamage(value);
            colony.markAntiAirDirty();
            ctx.getSource()
              .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_DAMAGE_SET, colony.getName(), format(value)), true);
            return 1;
        });
    }

    /**
     * {@code minlevel} with no value.
     */
    private int onReportMinLevel(final CommandContext<CommandSourceStack> context)
    {
        return forEachTuning(context, (ctx, colony) -> {
            reportMinLevel(ctx, colony.getAntiAirSettings());
            return 1;
        });
    }

    /**
     * {@code minlevel <level>}.
     */
    private int onSetMinLevel(final CommandContext<CommandSourceStack> context)
    {
        final int level = IntegerArgumentType.getInteger(context, ANTIAIR_VALUE);
        if (!AntiAirSettings.check(level, AntiAirSettings.MIN_TOWER_LEVEL_BOUND, AntiAirSettings.MAX_TOWER_LEVEL_BOUND))
        {
            return refuse(context,
              ANTIAIR_MINLEVEL,
              String.valueOf(AntiAirSettings.MIN_TOWER_LEVEL_BOUND),
              String.valueOf(AntiAirSettings.MAX_TOWER_LEVEL_BOUND),
              String.valueOf(level));
        }

        return forEachTuning(context, (ctx, colony) -> {
            colony.getAntiAirSettings().setMinTowerLevel(level);
            colony.markAntiAirDirty();
            ctx.getSource()
              .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_MINLEVEL_SET, colony.getName(), level), true);
            return 1;
        });
    }

    /**
     * The rate at which an aircraft stops being able to absorb any more, arrows per second.
     *
     * <p>{@code PlaneEntity#hurtServer} makes the airframe immune for ten ticks after every hit, so two
     * damaging rounds a second is the ceiling the game itself imposes. Quoted in the warning, not
     * enforced: see {@link #onSetRate}.
     */
    private static final double SATURATION_RATE = 2.0;

    /**
     * Runs a tuning action against every colony the argument resolved to, refusing the ones that are not
     * live server colonies.
     *
     * <p>Every tuning verb funnels through here so that the view check, and the "nothing was changed"
     * failure that goes with it, exist once.
     *
     * @param context the command context.
     * @param action  what to do with each colony.
     * @return the command status.
     */
    private int forEachTuning(final CommandContext<CommandSourceStack> context, final TuningAction action)
    {
        for (final IColony colony : MultiColonyIdArgument.getColonies(context, COLONYID_ARG))
        {
            if (!(colony instanceof final Colony serverColony))
            {
                // Settings are read only on the server, so they live on Colony rather than on IColony.
                context.getSource().sendFailure(Component.translatableEscape(COMMAND_ANTIAIR_NO_COLONY));
                continue;
            }
            action.apply(context, serverColony);
        }
        return 1;
    }

    /**
     * What every tuning verb does to one colony.
     */
    @FunctionalInterface
    private interface TuningAction
    {
        int apply(CommandContext<CommandSourceStack> context, Colony colony);
    }

    /**
     * Says no, names the setting, and names the bounds it is outside.
     *
     * <p>Deliberately {@code sendFailure} rather than a success line: a refusal that scrolls past looking
     * like every other reply is how a player ends up believing a value was accepted. Nothing is changed
     * on any colony — the check runs before the colony loop, so a multi-colony argument is all-or-nothing
     * rather than partly applied.
     *
     * @return 0, the failure status.
     */
    private static int refuse(
      final CommandContext<CommandSourceStack> context,
      final String setting,
      final String min,
      final String max,
      final String offered)
    {
        context.getSource().sendFailure(Component.translatableEscape(COMMAND_ANTIAIR_OUT_OF_BOUNDS, setting, min, max, offered));
        return 0;
    }

    private static void reportRange(final CommandContext<CommandSourceStack> context, final AntiAirSettings tuning)
    {
        context.getSource()
          .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_RANGE,
            format(tuning.getRange()),
            format(AntiAirSettings.DEFAULT_RANGE),
            format(AntiAirSettings.MIN_RANGE),
            format(AntiAirSettings.MAX_RANGE)), true);
    }

    /**
     * The rate, and the tick interval it is really made of, always together.
     *
     * <p>Quoting the interval alongside the rate is the whole honesty of this feature in one line: it is
     * the number the battery actually uses, and it is what makes it obvious why some rates are available
     * and others are not.
     */
    private static void reportRate(final CommandContext<CommandSourceStack> context, final AntiAirSettings tuning)
    {
        context.getSource()
          .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_RATE,
            format(tuning.getRate()),
            tuning.getShotInterval(),
            format(AntiAirSettings.rateForInterval(AntiAirSettings.DEFAULT_SHOT_INTERVAL)),
            format(AntiAirSettings.MIN_RATE),
            format(AntiAirSettings.MAX_RATE)), true);
    }

    private static void reportDamage(final CommandContext<CommandSourceStack> context, final AntiAirSettings tuning)
    {
        context.getSource()
          .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_DAMAGE,
            format(tuning.getDamage()),
            format(AntiAirSettings.DEFAULT_DAMAGE),
            format(AntiAirSettings.MIN_DAMAGE),
            format(AntiAirSettings.MAX_DAMAGE)), true);
    }

    private static void reportMinLevel(final CommandContext<CommandSourceStack> context, final AntiAirSettings tuning)
    {
        context.getSource()
          .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_MINLEVEL,
            tuning.getMinTowerLevel(),
            AntiAirSettings.DEFAULT_MIN_TOWER_LEVEL,
            AntiAirSettings.MIN_TOWER_LEVEL_BOUND,
            AntiAirSettings.MAX_TOWER_LEVEL_BOUND), true);
    }

    /**
     * What the current rate costs in arrows, said in seconds of fire rather than in arrows, because
     * arrows per delivery means nothing without knowing how fast they leave.
     */
    private static void reportAmmo(final CommandContext<CommandSourceStack> context, final AntiAirSettings tuning)
    {
        final int order = tuning.arrowOrder();
        final double seconds = order / tuning.getRate();
        context.getSource()
          .sendSuccess(() -> Component.translatableEscape(COMMAND_ANTIAIR_AMMO,
            order,
            tuning.arrowOrderMin(),
            format(seconds)), true);
    }

    /**
     * A number as a person reads it: two decimals, and no trailing ".00" on a whole one.
     *
     * <p>Formatted here rather than left to the language file because {@code %s} on a boxed double prints
     * every digit the binary representation has, and "2.8571428571428572 arrows/second" is not a rate a
     * person can act on. It is also deliberately a string by the time it reaches the component: {@code %d}
     * does not render in this version's translation pipeline.
     */
    private static String format(final double value)
    {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.2f", value);
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "antiair";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        final RequiredArgumentBuilder<CommandSourceStack, MultipleOptionsArgument.OptionContainer<List<Integer>>> colonyIdArg =
          IMCCommand.newArgument(COLONYID_ARG, MultiColonyIdArgument.id())
            .executes(this::checkPreConditionAndExecute);
        addVerbs(colonyIdArg);

        final RequiredArgumentBuilder<CommandSourceStack, String> allColoniesArg =
          IMCCommand.newArgument(COLONYID_ARG, StringArgumentType.string())
            .executes(this::checkPreConditionAndExecute);
        addVerbs(allColoniesArg);

        return IMCCommand.newLiteral(getName()).then(colonyIdArg).then(allColoniesArg);
    }

    /**
     * Hangs every verb off one colony argument.
     *
     * <p>Called twice because the command has two ways of naming a colony — the rich
     * {@link MultiColonyIdArgument} and a bare string for {@code all} — and both have always carried the
     * same verbs. Building the list once is what stops the two drifting apart, which with ten verbs
     * instead of two is no longer a theoretical risk.
     *
     * @param colonyArg the colony argument to hang them off.
     */
    private void addVerbs(final ArgumentBuilder<CommandSourceStack, ?> colonyArg)
    {
        colonyArg.then(IMCCommand.newLiteral(ANTIAIR_LOCATE).executes(ctx -> checkPreConditionAndExecute(ctx, this::onExecuteLocate)));
        colonyArg.then(IMCCommand.newLiteral(ANTIAIR_TP).executes(ctx -> checkPreConditionAndExecute(ctx, this::onExecuteTeleport)));
        colonyArg.then(IMCCommand.newLiteral(ANTIAIR_SETTINGS).executes(ctx -> checkPreConditionAndExecute(ctx, this::onExecuteSettings)));
        colonyArg.then(IMCCommand.newLiteral(ANTIAIR_RESET).executes(ctx -> checkPreConditionAndExecute(ctx, this::onExecuteReset)));
        colonyArg.then(tuningVerb(ANTIAIR_RANGE, this::onReportRange, this::onSetRange, true));
        colonyArg.then(tuningVerb(ANTIAIR_RATE, this::onReportRate, this::onSetRate, true));
        colonyArg.then(tuningVerb(ANTIAIR_DAMAGE, this::onReportDamage, this::onSetDamage, true));
        colonyArg.then(tuningVerb(ANTIAIR_MINLEVEL, this::onReportMinLevel, this::onSetMinLevel, false));
    }

    /**
     * One tuning verb: report with no value, set with one.
     *
     * <p>The value argument is unbounded on purpose — see the class comment. It is the widest argument
     * brigadier has for the type, so every out-of-bounds number reaches this class and is refused with a
     * message that explains itself, rather than being rejected by the parser with a bare number or,
     * worse, accepted and clamped.
     *
     * @param name     the verb.
     * @param report   what to do with no value.
     * @param set      what to do with one.
     * @param fraction true if the value is a double, false if it is a whole number.
     * @return the built subcommand.
     */
    private LiteralArgumentBuilder<CommandSourceStack> tuningVerb(
      final String name,
      final IMCCommand.ExecutionHandler report,
      final IMCCommand.ExecutionHandler set,
      final boolean fraction)
    {
        final ArgumentBuilder<CommandSourceStack, ?> value = fraction
                                                              ? IMCCommand.newArgument(ANTIAIR_VALUE, DoubleArgumentType.doubleArg())
                                                              : IMCCommand.newArgument(ANTIAIR_VALUE, IntegerArgumentType.integer());
        value.executes(ctx -> checkPreConditionAndExecute(ctx, set));

        return IMCCommand.newLiteral(name)
                 .then(value)
                 .executes(ctx -> checkPreConditionAndExecute(ctx, report));
    }

    /**
     * The emplacement closest to a point.
     *
     * @param towers   the emplacements to choose from.
     * @param from     the point to measure from, the colony centre.
     * @param dryOnly  true to consider only the ones with no arrows.
     * @return the closest one, or null if the filter left nothing.
     */
    private static AircraftCompat.Emplacement nearest(
      final List<AircraftCompat.Emplacement> towers,
      final BlockPos from,
      final boolean dryOnly)
    {
        AircraftCompat.Emplacement closest = null;
        double best = Double.MAX_VALUE;
        for (final AircraftCompat.Emplacement tower : towers)
        {
            if (dryOnly && tower.arrows() > 0)
            {
                continue;
            }
            final double distance = BlockPosUtil.getDistanceSquared(tower.position(), from);
            if (distance < best)
            {
                best = distance;
                closest = tower;
            }
        }
        return closest;
    }
}
