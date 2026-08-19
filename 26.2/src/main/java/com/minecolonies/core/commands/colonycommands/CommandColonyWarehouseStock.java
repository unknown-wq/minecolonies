package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.colony.buildings.modules.WarehouseIdleTrackerModule;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Reports how long every kind of item in a colony's warehouses has been sitting there unused, and how fast it is
 * actually being drawn down.
 * <p>
 * Reads {@link WarehouseIdleTrackerModule}, which samples the racks once per colony tick; the command itself measures
 * nothing and changes nothing, so running it twice in a row costs two map walks.
 * <p>
 * <b>Two outputs on purpose.</b> A real warehouse holds hundreds of item types and chat can show a dozen of them
 * usefully, so chat gets the worst {@value #CHAT_CAP} offenders and the complete list goes to a CSV file under the
 * world save, next to where the mod already writes its colony backups and tag audits. The file is named after the
 * colony id and nothing else -- there is no filename argument, so nothing a player types can steer where it lands --
 * and it is overwritten on every run rather than accumulating a directory of dated copies nobody asked for.
 * <p>
 * <b>Named {@code warehousestock}.</b> The colony subtree spells its commands out in full lowercase words
 * ({@code blastprotection}, {@code raidsinfo}, {@code fieldseeds}), and {@code stock} on its own would collide with
 * the minimum-stock module that every building GUI already calls "stock".
 */
public class CommandColonyWarehouseStock implements IMCOPCommand
{
    /**
     * How many rows chat gets. Roughly a screen, and the file has the rest.
     */
    private static final int CHAT_CAP = 15;

    /**
     * The CSV header, and with it the column order.
     */
    private static final String CSV_HEADER =
      "item_id,display_name,count,days_idle,taken_per_day,taken_last_" + WarehouseIdleTrackerModule.WINDOW_DAYS
        + "_days,total_taken,first_seen_days_ago,ever_taken,warehouses";

    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        if (colony == null)
        {
            return 0;
        }

        final List<IWareHouse> warehouses = colony.getServerBuildingManager().getWareHouses();
        if (warehouses.isEmpty())
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_WAREHOUSESTOCK_NO_WAREHOUSE, colony.getName()), true);
            return 0;
        }

        final List<Map<ItemStorage, WarehouseIdleTrackerModule.ItemHistory>> histories = new ArrayList<>();
        final List<String> fillLines = new ArrayList<>();
        long sampleNanos = 0;
        int sampledWarehouses = 0;
        int racks = 0;
        int usedSlots = 0;
        int totalSlots = 0;
        long itemCount = 0;
        double stackEquivalents = 0;
        for (final IWareHouse warehouse : warehouses)
        {
            final WarehouseIdleTrackerModule module = warehouse.getModule(WarehouseIdleTrackerModule.class);
            if (module == null)
            {
                continue;
            }
            histories.add(module.getHistory());
            final String where = warehouse.getPosition().toShortString();
            if (module.getLastSampleTick() < 0)
            {
                // Not "0 of 0 slots": a warehouse whose chunks have never been loaded since the server came up has no
                // occupancy at all, and printing zeroes for it would read as an empty warehouse.
                fillLines.add(where + ": not sampled yet (chunks not loaded since start-up)");
                continue;
            }

            sampleNanos += module.getLastSampleNanos();
            racks += module.getLastSampleRacks();
            sampledWarehouses++;
            usedSlots += module.getUsedSlots();
            totalSlots += module.getTotalSlots();
            itemCount += module.getItemCount();
            stackEquivalents += module.getStackEquivalents();
            fillLines.add(fillLine(where, module.getUsedSlots(), module.getTotalSlots(), module.getItemCount(), module.getStackEquivalents()));
        }

        final long now = colony.getWorld() == null ? 0 : colony.getWorld().getGameTime();
        final List<WarehouseIdleTrackerModule.Aggregate> rows = WarehouseIdleTrackerModule.aggregate(histories, now);
        final String colonyFill = fillLine("colony total", usedSlots, totalSlots, itemCount, stackEquivalents);

        if (rows.isEmpty())
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_WAREHOUSESTOCK_NO_DATA, colony.getName()), true);
            final Component emptyFill = Component.literal(colonyFill).withStyle(ChatFormatting.AQUA);
            context.getSource().sendSuccess(() -> emptyFill, false);
            return 0;
        }

        final int warehouseCount = warehouses.size();
        final int rowCount = rows.size();
        final String sampleMillis = String.format(Locale.ROOT, "%.2f", sampleNanos / 1_000_000.0);
        final int sampledRacks = racks;
        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_WAREHOUSESTOCK_HEADER,
            colony.getName(),
            rowCount,
            warehouseCount,
            sampleMillis,
            sampledRacks).withStyle(ChatFormatting.YELLOW), true);

        final Component fillHeader = Component.literal(colonyFill).withStyle(ChatFormatting.AQUA);
        context.getSource().sendSuccess(() -> fillHeader, false);
        if (fillLines.size() > 1)
        {
            // Only worth breaking out per warehouse when there is more than one; with a single warehouse the colony
            // total is the same line twice.
            for (final String line : fillLines)
            {
                final Component perWarehouse = Component.literal("  " + line).withStyle(ChatFormatting.AQUA);
                context.getSource().sendSuccess(() -> perWarehouse, false);
            }
        }

        for (int i = 0; i < Math.min(CHAT_CAP, rows.size()); i++)
        {
            final WarehouseIdleTrackerModule.Aggregate row = rows.get(i);
            final Component line = Component.literal(String.format(Locale.ROOT,
              "  %s x%d  idle %.1fd  taken/day %.1f  (%d in %dd)",
              row.item.getItemStack().getHoverName().getString(),
              row.count,
              row.getIdleDays(now),
              row.getTakenPerDay(),
              row.takenInWindow,
              WarehouseIdleTrackerModule.WINDOW_DAYS));
            context.getSource().sendSuccess(() -> line, false);
        }

        if (rows.size() > CHAT_CAP)
        {
            final int omitted = rows.size() - CHAT_CAP;
            context.getSource()
              .sendSuccess(() -> Component.translatable(COMMAND_COLONY_WAREHOUSESTOCK_MORE, omitted).withStyle(ChatFormatting.GRAY), false);
        }

        writeCsv(context.getSource(), colony, rows, now, sampledWarehouses, warehouseCount, colonyFill, fillLines);
        return 1;
    }

    /**
     * One occupancy line, in the wording used identically by chat and the file.
     * <p>
     * Two numbers, never merged. Slot occupancy is what physically stops a courier from storing a new item type; the
     * stack-equivalent figure is how much of the capacity the goods really take, where a slot with three cobblestone
     * in it counts 3/64 rather than 1. A warehouse can be 100% of the first and 5% of the second, and averaging them
     * would hide exactly the case the player needs to see.
     *
     * @param what             what this line is about -- a position, or the colony total.
     * @param usedSlots        slots holding something.
     * @param totalSlots       slots in total.
     * @param itemCount        items held.
     * @param stackEquivalents items held, measured in full stacks of their own item.
     * @return the line.
     */
    private static String fillLine(final String what, final int usedSlots, final int totalSlots, final long itemCount, final double stackEquivalents)
    {
        if (totalSlots <= 0)
        {
            return what + ": no rack slots";
        }
        return String.format(Locale.ROOT,
          "%s: %d/%d slots occupied (%.1f%%); %d item(s) = %.1f full stacks of a possible %d (%.1f%% of capacity)",
          what,
          usedSlots,
          totalSlots,
          100.0 * usedSlots / totalSlots,
          itemCount,
          stackEquivalents,
          totalSlots,
          100.0 * stackEquivalents / totalSlots);
    }

    /**
     * Write the complete, uncapped list to a CSV file under the world save, and say in chat where it went or why it
     * could not go there.
     *
     * @param source            the command source, for the chat reply.
     * @param colony            the colony, which names the file.
     * @param rows              every row, already sorted.
     * @param now               the current game tick.
     * @param sampledWarehouses how many warehouses have produced a sample at all.
     * @param warehouseCount    how many warehouses the colony has.
     */
    private void writeCsv(
      @NotNull final CommandSourceStack source,
      @NotNull final IColony colony,
      @NotNull final List<WarehouseIdleTrackerModule.Aggregate> rows,
      final long now,
      final int sampledWarehouses,
      final int warehouseCount,
      @NotNull final String colonyFill,
      @NotNull final List<String> fillLines)
    {
        final MinecraftServer server = source.getServer();
        // Same directory the tag audits write to and the colony backups live in: <world>/minecolonies. The name is
        // built from the colony id, an int, so it cannot contain a separator and cannot escape the directory.
        final Path path = server.getWorldPath(LevelResource.ROOT).resolve(Constants.MOD_ID).resolve("warehouse_stock_colony" + colony.getID() + ".csv");

        try
        {
            Files.createDirectories(path.getParent());
            try (final BufferedWriter writer = Files.newBufferedWriter(path))
            {
                // The warehouse totals go in a '#'-prefixed header block above the header row rather than in a
                // trailing summary line, so that everything below the header row is uniform per-item data and a
                // spreadsheet's sort covers all of it.
                writer.write("# colony " + colony.getID() + " (" + colony.getName() + "), " + warehouseCount + " warehouse(s), "
                               + sampledWarehouses + " sampled, game tick " + now);
                writer.newLine();
                writer.write("# " + colonyFill);
                writer.newLine();
                for (final String line : fillLines)
                {
                    writer.write("# warehouse at " + line);
                    writer.newLine();
                }
                writer.write(CSV_HEADER);
                writer.newLine();

                for (final WarehouseIdleTrackerModule.Aggregate row : rows)
                {
                    final ItemStack stack = row.item.getItemStack();
                    final Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                    writer.write(String.join(",",
                      escape(id.toString()),
                      escape(stack.getHoverName().getString()),
                      Integer.toString(row.count),
                      String.format(Locale.ROOT, "%.3f", row.getIdleDays(now)),
                      String.format(Locale.ROOT, "%.3f", row.getTakenPerDay()),
                      Long.toString(row.takenInWindow),
                      Long.toString(row.totalTaken),
                      String.format(Locale.ROOT, "%.3f", row.getAgeDays(now)),
                      Boolean.toString(row.everTaken),
                      Integer.toString(row.warehouses)));
                    writer.newLine();
                }
            }

            final String shown = path.toAbsolutePath().normalize().toString();
            source.sendSuccess(() -> Component.translatable(COMMAND_COLONY_WAREHOUSESTOCK_FILE, shown, rows.size()).withStyle(ChatFormatting.GREEN), false);
        }
        catch (final IOException | RuntimeException e)
        {
            // A full disk or a read-only save directory is exactly the case where silence would be worst: the chat
            // report is capped, so the player would believe the rest was on disk when it is not.
            Log.getLogger().error("Failed to write the warehouse stock file to " + path, e);
            final String reason = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "no detail" : e.getMessage());
            source.sendFailure(Component.translatable(COMMAND_COLONY_WAREHOUSESTOCK_FILE_FAILED, path.toString(), reason));
        }
    }

    /**
     * Quote a CSV field if it needs it. Item display names are player-editable through anvils and come from other
     * mods, so neither commas nor quotes nor newlines can be ruled out.
     *
     * @param value the field.
     * @return the field, quoted if necessary.
     */
    private static String escape(final String value)
    {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0)
        {
            return value;
        }
        return '"' + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "warehousestock";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id()).executes(this::checkPreConditionAndExecute));
    }
}
