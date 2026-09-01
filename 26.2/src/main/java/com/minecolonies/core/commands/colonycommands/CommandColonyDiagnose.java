package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.IAssignsJob;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.entity.ai.ITickingStateAI;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.core.colony.HeadlessColonyMode;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracks;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBarracksTower;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingMiner;
import com.minecolonies.core.colony.jobs.JobDeliveryman;
import com.minecolonies.core.colony.territory.BorderPatrol;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Prints a "what is stuck" report for a colony. Aimed at finding porting defects rather than at being a player convenience: it looks for the states a healthy colony never
 * reaches, such as a worker whose AI was never created, a job that lost its building or a work order claimed by a builder that no longer exists.
 * <p>
 * Chat only ever gets the summary plus the first few entries of every list; the complete report always goes to the server log, the same way
 * {@link CommandColonyPrintStats} does it.
 */
public class CommandColonyDiagnose implements IMCOPCommand
{
    /**
     * How many entries of a problem list are echoed into chat before it is cut off.
     */
    private static final int CHAT_LIST_CAP = 8;

    /**
     * How many workers are echoed into chat. Higher than {@link #CHAT_LIST_CAP} because the worker list is the point of the command.
     */
    private static final int CHAT_WORKER_CAP = 20;

    /**
     * How many border-patrol lines are echoed into chat. A barracks contributes one line plus one per tower plus one
     * per guard, so a full garrison is around twenty-five lines and the cap is set to show all of one barracks.
     */
    private static final int CHAT_PATROL_CAP = 25;

    /**
     * How far a patrolling guard may stand from his own stretch of border before the report calls him out, in blocks.
     * <p>
     * A guard walks to a waypoint and stands on it, so in normal service this is under twenty. A chase can legitimately
     * take him further; a hundred blocks means something has gone wrong with the patrol rather than with one fight.
     */
    private static final int PATROL_STRAY_BLOCKS = 100;

    /**
     * A request whose state has not moved for this many ticks is reported as stalled.
     */
    private static final long STALLED_REQUEST_TICKS = 20 * 60 * 5;

    /**
     * Nothing in the colony model records how long a citizen has been in an AI state or how long a request has been open, so this command remembers what it saw last time and
     * reports the age of anything that has not moved since. Keyed by colony id, then by an arbitrary tracking key.
     * <p>
     * Purely a debugging aid: it is never persisted, and it is rebuilt from scratch (dropping entries that no longer exist) on every run.
     */
    private static final Map<Integer, Map<String, Observation>> OBSERVATIONS = new HashMap<>();

    /**
     * A value seen for a tracking key, and the game time at which it was first seen with that value.
     *
     * @param value the observed value.
     * @param since the game time of the first observation of that value.
     */
    private record Observation(String value, long since) {}

    /**
     * What happens when the command is executed after preConditions are successful.
     *
     * @param context the context of the command execution
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final long now = colony.getWorld().getGameTime();

        final Map<String, Observation> previous = OBSERVATIONS.getOrDefault(colony.getID(), Map.of());
        final Map<String, Observation> current = new HashMap<>();

        final Report report = new Report(context.getSource());

        // The lists we fill in below. Everything except the worker list and the free slots counts as a problem.
        final List<Worker> workers = new ArrayList<>();
        final List<String> withoutAI = new ArrayList<>();
        final List<String> withoutBuilding = new ArrayList<>();
        final List<String> withoutWarehouse = new ArrayList<>();
        final List<String> freeSlots = new ArrayList<>();
        final List<String> badRequests = new ArrayList<>();
        final List<String> badWorkOrders = new ArrayList<>();
        final List<String> badBuildings = new ArrayList<>();
        final List<String> patrols = new ArrayList<>();

        int children = 0;
        int employed = 0;
        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            if (citizen.isChild())
            {
                children++;
            }

            final IJob<?> job = citizen.getJob();
            if (job == null)
            {
                continue;
            }

            // Adults only. The last figure on the line is population minus employed minus children, so counting a
            // schooled child in both terms subtracts it twice: a colony with sixteen pupils reported "-10 unemployed
            // adults". The pupil is still collected below, because its AI state is worth reporting like any other.
            if (!citizen.isChild())
            {
                employed++;
            }

            collectWorker(colony, citizen, job, previous, current, now, workers, withoutAI, withoutBuilding);
            collectUnadoptedCourier(colony, citizen, job, withoutWarehouse);
        }

        int built = 0;
        int slots = 0;
        int filledSlots = 0;
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (building.canAssignCitizens())
            {
                built++;
            }

            for (final IAssignsJob module : building.getModulesByType(IAssignsJob.class))
            {
                final int assigned = module.getAssignedCitizen().size();
                final int max = module.getModuleMax();
                slots += max;
                filledSlots += assigned;

                if (building.canAssignCitizens() && assigned < max)
                {
                    freeSlots.add(String.format("%s %s %s/%s at %s",
                      name(building),
                      module.getJobEntry().getKey().getPath(),
                      assigned,
                      max,
                      building.getPosition().toShortString()));
                }
            }

            collectBuildingProblems(colony, building, badBuildings);
            collectPatrols(colony, building, patrols);
        }

        final RequestCounts requestCounts = collectRequestProblems(colony, previous, current, now, badRequests);
        final int unclaimedOrders = collectWorkOrderProblems(colony, badWorkOrders);

        OBSERVATIONS.put(colony.getID(), current);

        // Summary first, then the lists.
        report.emit(Component.translatable(COMMAND_COLONY_DIAGNOSE_HEADER, colony.getName(), colony.getID()), ChatFormatting.GOLD);

        // Right under the header, because it changes how every number below it should be read: this colony is
        // running with nobody watching it, which is not something an ordinary server does. Nobody should be able to
        // read a diagnosis off a server in that state without being told.
        if (HeadlessColonyMode.isRunning())
        {
            report.emit(Component.translatable(COMMAND_COLONY_DIAGNOSE_HEADLESS), ChatFormatting.YELLOW);
        }
        report.emit(Component.translatable(COMMAND_COLONY_DIAGNOSE_CITIZENS,
          colony.getCitizenManager().getCurrentCitizenCount(),
          colony.getCitizenManager().getMaxCitizens(),
          employed,
          children,
          colony.getCitizenManager().getCurrentCitizenCount() - employed - children));
        report.emit(Component.translatable(COMMAND_COLONY_DIAGNOSE_BUILDINGS,
          colony.getServerBuildingManager().getBuildings().size(),
          built,
          filledSlots,
          slots));
        report.emit(Component.translatable(COMMAND_COLONY_DIAGNOSE_REQUESTS,
          requestCounts.open(),
          requestCounts.player(),
          requestCounts.retrying()));
        report.emit(Component.translatable(COMMAND_COLONY_DIAGNOSE_WORKORDERS,
          colony.getWorkManager().getWorkOrders().size(),
          unclaimedOrders));

        final int problems = withoutAI.size() + withoutBuilding.size() + withoutWarehouse.size() + badRequests.size() + badWorkOrders.size() + badBuildings.size();
        report.emit(problems == 0
                      ? Component.translatable(COMMAND_COLONY_DIAGNOSE_NO_PROBLEMS)
                      : Component.translatable(COMMAND_COLONY_DIAGNOSE_PROBLEMS, problems),
          problems == 0 ? ChatFormatting.GREEN : ChatFormatting.RED);

        workers.sort(Comparator.comparingInt(Worker::severity).reversed().thenComparing(Comparator.comparingLong(Worker::age).reversed()));
        report.section(Component.translatable(COMMAND_COLONY_DIAGNOSE_SECTION_WORKERS, workers.size()), workers.stream().map(Worker::line).toList(), CHAT_WORKER_CAP);

        report.section(Component.translatable(COMMAND_COLONY_DIAGNOSE_SECTION_NO_AI, withoutAI.size()), withoutAI, CHAT_LIST_CAP);
        report.section(Component.translatable(COMMAND_COLONY_DIAGNOSE_SECTION_NO_BUILDING, withoutBuilding.size()), withoutBuilding, CHAT_LIST_CAP);
        report.section(Component.translatable(COMMAND_COLONY_DIAGNOSE_SECTION_NO_WAREHOUSE, withoutWarehouse.size()), withoutWarehouse, CHAT_LIST_CAP);
        report.section(Component.translatable(COMMAND_COLONY_DIAGNOSE_SECTION_REQUESTS, badRequests.size()), badRequests, CHAT_LIST_CAP);
        report.section(Component.translatable(COMMAND_COLONY_DIAGNOSE_SECTION_WORKORDERS, badWorkOrders.size()), badWorkOrders, CHAT_LIST_CAP);
        report.section(Component.translatable(COMMAND_COLONY_DIAGNOSE_SECTION_BUILDINGS, badBuildings.size()), badBuildings, CHAT_LIST_CAP);
        report.section(Component.translatable(COMMAND_COLONY_DIAGNOSE_SECTION_FREE_SLOTS, freeSlots.size()), freeSlots, CHAT_LIST_CAP);
        report.section(Component.translatable(COMMAND_COLONY_DIAGNOSE_SECTION_PATROLS, patrols.size()), patrols, CHAT_PATROL_CAP);

        Log.getLogger().info(report.getLog());
        return 1;
    }

    /**
     * Look at a single employed citizen: which AI state it is in, for how long, and whether the job still points at a building.
     *
     * @param colony          the colony.
     * @param citizen         the citizen.
     * @param job             the citizen's job, never null.
     * @param previous        the observations of the previous run.
     * @param current         the observations of this run, filled in here.
     * @param now             the current game time.
     * @param workers         the worker list to add to.
     * @param withoutAI       the problem list for citizens whose AI was never created.
     * @param withoutBuilding the problem list for jobs without a work building.
     */
    private void collectWorker(
      @NotNull final IColony colony,
      @NotNull final ICitizenData citizen,
      @NotNull final IJob<?> job,
      @NotNull final Map<String, Observation> previous,
      @NotNull final Map<String, Observation> current,
      final long now,
      @NotNull final List<Worker> workers,
      @NotNull final List<String> withoutAI,
      @NotNull final List<String> withoutBuilding)
    {
        final String jobName = job.getJobRegistryEntry().getKey().getPath();

        final String state;
        final int severity;
        if (citizen.getEntity().isEmpty())
        {
            // Not a defect on its own, the entity is simply not loaded, but it does mean the AI is not running.
            state = "<no entity>";
            severity = 1;
        }
        else
        {
            final ITickingStateAI ai = job.getWorkerAI();
            if (ai == null)
            {
                state = "<no AI>";
                severity = 3;
                withoutAI.add(String.format("#%s %s [%s] entity loaded but IJob#getWorkerAI() is null", citizen.getId(), citizen.getName(), jobName));
            }
            else
            {
                state = String.valueOf(ai.getState());
                severity = citizen.isIdleAtJob() ? 2 : 0;
            }
        }

        final long age = observe(previous, current, "ai:" + citizen.getId(), state, now);
        workers.add(new Worker(severity, age, String.format("#%s %s [%s] state=%s held=%s status=%s",
          citizen.getId(),
          citizen.getName(),
          jobName,
          state,
          formatAge(age),
          citizen.getJobStatus())));

        if (citizen.getWorkBuilding() == null)
        {
            final BlockPos recorded = job.getBuildingPos();
            final String detail;
            if (recorded == null)
            {
                detail = "never assigned to a work module";
            }
            else if (colony.getServerBuildingManager().getBuilding(recorded) == null)
            {
                detail = "recorded work position " + recorded.toShortString() + " is not a building any more";
            }
            else
            {
                detail = "recorded work position " + recorded.toShortString() + " exists but the job was not re-attached to it";
            }
            withoutBuilding.add(String.format("#%s %s [%s] %s", citizen.getId(), citizen.getName(), jobName, detail));
        }
    }

    /**
     * Report a courier that no warehouse has taken on, and say why it cannot work.
     * <p>
     * A courier needs to be on <em>two</em> assignment lists: the Courier's Hut hires him, and a warehouse's
     * {@link CourierAssignmentModule} adopts him. Missing from the second, {@code EntityAIWorkDeliveryman#checkIfExecute}
     * refuses to run his AI at all, so he does not even loiter -- he stands where he was spawned, forever, while the
     * warehouse queue fills up behind him. Before the Courier's Hut scaled with its level this was one citizen and one
     * complaint, and the two lined up; a level-5 hut now holds five, so one wrong switch makes four statues.
     * <p>
     * The line names the warehouses and their courier occupancy, because "no warehouse has room" and "no warehouse
     * exists" and "the warehouse is set to manual" are three different repairs and the count is what tells them apart.
     *
     * @param colony           the colony.
     * @param citizen          the citizen.
     * @param job              the citizen's job, never null.
     * @param withoutWarehouse the problem list to add to.
     */
    private void collectUnadoptedCourier(
      @NotNull final IColony colony,
      @NotNull final ICitizenData citizen,
      @NotNull final IJob<?> job,
      @NotNull final List<String> withoutWarehouse)
    {
        if (!(job instanceof final JobDeliveryman courierJob) || courierJob.findWareHouse() != null)
        {
            return;
        }

        final StringBuilder warehouses = new StringBuilder();
        for (final IWareHouse wareHouse : colony.getServerBuildingManager().getWareHouses())
        {
            final CourierAssignmentModule module = wareHouse.getFirstModuleOccurance(CourierAssignmentModule.class);
            if (warehouses.length() > 0)
            {
                warehouses.append(", ");
            }
            warehouses.append(String.format("%s at %s couriers %s/%s mode %s",
              name(wareHouse),
              wareHouse.getPosition().toShortString(),
              module.getAssignedCitizen().size(),
              module.getModuleMax(),
              module.getHiringMode()));
        }

        withoutWarehouse.add(String.format("#%s %s [%s] hired at %s but no warehouse has taken him on, so his AI never runs -- %s",
          citizen.getId(),
          citizen.getName(),
          job.getJobRegistryEntry().getKey().getPath(),
          citizen.getWorkBuilding() == null ? "no hut" : citizen.getWorkBuilding().getPosition().toShortString(),
          warehouses.length() == 0 ? "the colony has no warehouse" : warehouses.toString()));
    }

    /**
     * Report where a barracks' border patrols are and what they are walking.
     * <p>
     * This is the answer to "where are my patrols and what are they doing", and it deliberately lives here rather than
     * in a window of its own: the worker section above already says which AI state each guard is in and for how long,
     * and what it could not say was <em>which piece of border he is supposed to be on and how far off it he is</em>.
     * That is the only thing added.
     * <p>
     * Costs nothing in a colony without a barracks, and nothing in one whose barracks is not patrolling a border --
     * the mode is one string compare and the method returns.
     *
     * @param colony   the colony.
     * @param building the building to look at; only a barracks or a stable contributes.
     * @param patrols  the list to add to.
     */
    private void collectPatrols(@NotNull final IColony colony, @NotNull final IBuilding building, @NotNull final List<String> patrols)
    {
        if (building instanceof final BuildingStable stable)
        {
            collectStablePatrol(stable, patrols);
            return;
        }

        if (!(building instanceof final BuildingBarracks barracks))
        {
            return;
        }

        final BorderPatrol.Mode mode = barracks.getBorderPatrolMode();
        if (mode == BorderPatrol.Mode.OFF)
        {
            return;
        }

        // Asking a tower for its stretch is what builds the plan, so ask before reading the plan back.
        final List<List<BlockPos>> slices = new ArrayList<>();
        final List<BlockPos> towers = new ArrayList<>();
        for (final BlockPos towerPos : barracks.getTowers())
        {
            if (colony.getServerBuildingManager().getBuilding(towerPos) instanceof BuildingBarracksTower)
            {
                towers.add(towerPos);
                slices.add(barracks.getStretchFor(towerPos));
            }
        }

        final BorderPatrol.Plan plan = barracks.getBorderPlan();
        patrols.add(String.format("barracks at %s mode=%s border=%s",
          barracks.getPosition().toShortString(),
          mode.name().toLowerCase(java.util.Locale.ROOT),
          plan == null ? "not computed yet" : plan.isUsable() ? plan.waypoints().size() + " waypoints" : plan.failure().toString()));

        for (int i = 0; i < towers.size(); i++)
        {
            final BlockPos towerPos = towers.get(i);
            final List<BlockPos> slice = slices.get(i);
            final IBuilding tower = colony.getServerBuildingManager().getBuilding(towerPos);
            if (!(tower instanceof final BuildingBarracksTower guardTower))
            {
                continue;
            }

            // Only ask an occupied tower where it is heading: with no guard and no point picked yet,
            // AbstractBuildingGuards#getNextPatrolTarget reads the first assigned citizen without checking there is
            // one, and would throw inside a report whose whole job is to survive a broken colony.
            final BlockPos heading = guardTower.getAllAssignedCitizen().isEmpty() ? null : guardTower.getNextPatrolTarget(false);
            patrols.add(String.format("  tower at %s: %s waypoints, %s guards, heading for %s",
              towerPos.toShortString(),
              slice.size(),
              guardTower.getAllAssignedCitizen().size(),
              heading == null ? "nothing yet" : heading.toShortString()));

            for (final ICitizenData guard : guardTower.getAllAssignedCitizen())
            {
                final BlockPos at = guard.getEntity().map(entity -> entity.blockPosition()).orElse(guard.getLastPosition());
                final int off = distanceToLine(slice, at);
                patrols.add(String.format("    #%s %s at %s%s",
                  guard.getId(),
                  guard.getName(),
                  at.toShortString(),
                  off < 0 ? " (no stretch assigned)" : off > PATROL_STRAY_BLOCKS ? " -- " + off + " blocks OFF its stretch" : " (" + off + " blocks off the line)"));
            }
        }
    }

    /**
     * Report where a stable's cavalry is along the border and what arc each rider holds.
     * <p>
     * A stable is one building with a troop in it rather than a barracks with a tower per man, so the interesting
     * line is per rider: whose arc is whose, and whether two of them have ended up on the same piece of frontier,
     * which is the failure this route exists to make impossible and therefore the one worth being able to see.
     *
     * @param stable  the stable.
     * @param patrols the list to add to.
     */
    private void collectStablePatrol(@NotNull final BuildingStable stable, @NotNull final List<String> patrols)
    {
        if (stable.getBorderPatrolMode() == BorderPatrol.Mode.OFF)
        {
            return;
        }

        final BorderPatrol.Plan plan = stable.getBorderPlan();
        patrols.add(String.format("stable at %s mode=%s border=%s",
          stable.getPosition().toShortString(),
          stable.getBorderPatrolMode().name().toLowerCase(java.util.Locale.ROOT),
          plan == null ? "not computed yet" : plan.isUsable() ? plan.waypoints().size() + " waypoints" : plan.failure().toString()));

        for (final ICitizenData rider : stable.getAllAssignedCitizen())
        {
            final List<BlockPos> arc = stable.getStretchFor(rider);
            final BlockPos at = rider.getEntity().map(entity -> entity.blockPosition()).orElse(rider.getLastPosition());
            final int off = distanceToLine(arc, at);
            patrols.add(String.format("  #%s %s at %s: %s waypoints%s%s",
              rider.getId(),
              rider.getName(),
              at.toShortString(),
              arc.size(),
              arc.isEmpty() ? "" : " from " + arc.get(0).toShortString() + " to " + arc.get(arc.size() - 1).toShortString(),
              off < 0 ? " (no arc assigned)" : off > PATROL_STRAY_BLOCKS ? " -- " + off + " blocks OFF its arc" : " (" + off + " blocks off the line)"));
        }
    }

    /**
     * How far a position is from the nearest waypoint of a stretch, horizontally.
     *
     * @param stretch the waypoints.
     * @param pos     the position.
     * @return the distance in blocks, or -1 when the stretch is empty.
     */
    private static int distanceToLine(@NotNull final List<BlockPos> stretch, @NotNull final BlockPos pos)
    {
        long best = Long.MAX_VALUE;
        for (final BlockPos waypoint : stretch)
        {
            final long dx = (long) waypoint.getX() - pos.getX();
            final long dz = (long) waypoint.getZ() - pos.getZ();
            best = Math.min(best, dx * dx + dz * dz);
        }
        return best == Long.MAX_VALUE ? -1 : (int) Math.sqrt(best);
    }

    /**
     * Check a building for blueprint and level state that cannot happen in a healthy colony.
     *
     * @param colony       the colony.
     * @param building     the building to check.
     * @param badBuildings the problem list to add to.
     */
    private void collectBuildingProblems(@NotNull final IColony colony, @NotNull final IBuilding building, @NotNull final List<String> badBuildings)
    {
        final List<String> issues = new ArrayList<>();

        // A mine that has stopped is the one failure in this colony that shows up nowhere else in this report. The
        // miner's AI state ticks over normally while it happens, he files no requests, his work order stays claimed
        // and his citizen entry looks like any other worker's -- so the command answered "No problems found"
        // through a shaft that had not moved a block in a hundred thousand ticks. The miner's own watchdog is what
        // notices; this only reads what it wrote.
        if (building instanceof final BuildingMiner miner && miner.getShaftStallTicks() > 0)
        {
            issues.add("the shaft has not got any deeper in " + formatAge(miner.getShaftStallTicks())
                         + (miner.getShaftStallPos() == null ? "" : ", stopped at " + miner.getShaftStallPos().toShortString())
                         + " -- something the miner cannot break or cannot stand next to is in the way, commonly liquid");
        }

        if (building.getBuildingLevel() > building.getMaxBuildingLevel())
        {
            issues.add("level " + building.getBuildingLevel() + " is above the schematic maximum of " + building.getMaxBuildingLevel());
        }
        if (building.getBuildingLevel() > 0 && !building.isBuilt())
        {
            issues.add("has level " + building.getBuildingLevel() + " but is not flagged as built");
        }
        if (building.isDeconstructed() && building.getBuildingLevel() > 0)
        {
            issues.add("flagged as deconstructed but still at level " + building.getBuildingLevel());
        }
        if (building.getBlueprintPath() == null || building.getBlueprintPath().isBlank())
        {
            issues.add("no blueprint path");
        }
        if (building.getStructurePack() == null || building.getStructurePack().isBlank())
        {
            issues.add("no structure pack");
        }
        if (building.getRotationMirror() == null)
        {
            // AbstractBuilding logs this as an error when it serializes the building to a view.
            issues.add("no rotation/mirror");
        }
        if (building.hasParent() && building.getParent().equals(BlockPos.ZERO))
        {
            issues.add("points at a parent building that no longer exists");
        }

        if (!issues.isEmpty())
        {
            badBuildings.add(String.format("%s at %s: %s", name(building), building.getPosition().toShortString(), String.join(", ", issues)));
        }
    }

    /**
     * Walk every open request in the colony and pick out the ones that nobody took or that have not moved in a long time.
     *
     * @param colony      the colony.
     * @param previous    the observations of the previous run.
     * @param current     the observations of this run, filled in here.
     * @param now         the current game time.
     * @param badRequests the problem list to add to.
     * @return the request counts for the summary.
     */
    private RequestCounts collectRequestProblems(
      @NotNull final IColony colony,
      @NotNull final Map<String, Observation> previous,
      @NotNull final Map<String, Observation> current,
      final long now,
      @NotNull final List<String> badRequests)
    {
        final IRequestManager manager = colony.getRequestManager();

        // Requests handed to these two resolvers are the ones the colony could not satisfy on its own: the player resolver is the "put it on the clipboard" fallback and the
        // retrying resolver is where requests land when no resolver would take them at all.
        final Set<IToken<?>> playerAssigned = new HashSet<>(manager.getPlayerResolver().getAllAssignedRequests());
        final Set<IToken<?>> retrying = new HashSet<>(manager.getRetryingRequestResolver().getAllAssignedRequests());

        final Set<IToken<?>> seen = new HashSet<>();
        int open = 0;
        int player = 0;
        int retried = 0;

        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            for (final Collection<IToken<?>> tokens : building.getOpenRequestsByRequestableType().values())
            {
                for (final IToken<?> token : tokens)
                {
                    if (!seen.add(token))
                    {
                        continue;
                    }

                    final IRequest<?> request = manager.getRequestForToken(token);
                    if (request == null)
                    {
                        badRequests.add(String.format("%s at %s holds token %s which the request manager does not know",
                          name(building),
                          building.getPosition().toShortString(),
                          token));
                        continue;
                    }

                    open++;
                    final boolean isPlayer = playerAssigned.contains(token);
                    final boolean isRetrying = retrying.contains(token);
                    if (isPlayer)
                    {
                        player++;
                    }
                    if (isRetrying)
                    {
                        retried++;
                    }

                    final RequestState state = request.getState();
                    final long age = observe(previous, current, "req:" + token, state.name(), now);
                    // CREATED/REPORTED/ASSIGNING all mean the request manager has not handed it to a resolver yet.
                    final boolean unresolved = state == RequestState.CREATED || state == RequestState.REPORTED || state == RequestState.ASSIGNING;
                    final boolean stalled = age >= STALLED_REQUEST_TICKS;

                    if (unresolved || isRetrying || stalled)
                    {
                        badRequests.add(String.format("%s at %s: %s state=%s held=%s%s",
                          name(building),
                          request.getRequester().getLocation().getInDimensionLocation().toShortString(),
                          // The raw requestable type rather than the display name: a dedicated server has no mod language file loaded, so the display name is just a key there.
                          request.getType().getRawType().getSimpleName(),
                          state,
                          formatAge(age),
                          isRetrying ? " (being retried)" : (unresolved ? " (no resolver took it)" : " (stalled)")));
                    }
                }
            }
        }

        return new RequestCounts(open, player, retried);
    }

    /**
     * Check the work orders for missing builders and for targets that no longer exist.
     *
     * @param colony        the colony.
     * @param badWorkOrders the problem list to add to.
     * @return the number of unclaimed work orders.
     */
    private int collectWorkOrderProblems(@NotNull final IColony colony, @NotNull final List<String> badWorkOrders)
    {
        int unclaimed = 0;
        for (final IServerWorkOrder order : colony.getWorkManager().getWorkOrders().values())
        {
            final List<String> issues = new ArrayList<>();
            if (!order.isClaimed())
            {
                unclaimed++;
                issues.add("no builder claimed it");
            }
            else if (colony.getServerBuildingManager().getBuilding(order.getClaimedBy()) == null)
            {
                issues.add("claimed by " + order.getClaimedBy().toShortString() + " which is not a building any more");
            }
            if (!order.isValid(colony))
            {
                issues.add("the work order reports itself as invalid, its target is gone");
            }

            if (!issues.isEmpty())
            {
                badWorkOrders.add(String.format("#%s %s %s at %s level %s->%s: %s",
                  order.getID(),
                  order.getWorkOrderType(),
                  String.valueOf(order.getStructurePath()),
                  order.getLocation().toShortString(),
                  order.getCurrentLevel(),
                  order.getTargetLevel(),
                  String.join(", ", issues)));
            }
        }
        return unclaimed;
    }

    /**
     * Record the value of a tracking key for this run and report for how long it has held that value.
     *
     * @param previous the observations of the previous run.
     * @param current  the observations of this run, written to here.
     * @param key      the tracking key.
     * @param value    the value observed now.
     * @param now      the current game time.
     * @return the age in ticks, or -1 if the value is new (or changed since the previous run).
     */
    private static long observe(
      @NotNull final Map<String, Observation> previous,
      @NotNull final Map<String, Observation> current,
      @NotNull final String key,
      @NotNull final String value,
      final long now)
    {
        final Observation observation = previous.get(key);
        if (observation != null && observation.value().equals(value) && observation.since() <= now)
        {
            current.put(key, observation);
            return now - observation.since();
        }

        current.put(key, new Observation(value, now));
        return -1;
    }

    /**
     * Render an age in ticks for display.
     *
     * @param ticks the age, or -1 if it is not known yet.
     * @return the display string.
     */
    private static String formatAge(final long ticks)
    {
        if (ticks < 0)
        {
            // Either the first time this command ran, or the value changed since the last run. Both mean "not stuck".
            return "new";
        }
        final long seconds = ticks / 20;
        return seconds < 60 ? seconds + "s" : (seconds / 60) + "m" + (seconds % 60) + "s";
    }

    /**
     * Short, stable name of a building for the report. The registry path rather than the display name, because that is what maps back onto the code.
     *
     * @param building the building.
     * @return the name.
     */
    private static String name(@NotNull final IBuilding building)
    {
        return building.getBuildingType().getRegistryName().getPath() + building.getBuildingLevel();
    }

    /**
     * One line of the worker list plus the keys it is sorted by.
     *
     * @param severity how suspicious the entry is, higher sorts first.
     * @param age      for how many ticks the AI state has not changed, -1 if unknown.
     * @param line     the display line.
     */
    private record Worker(int severity, long age, String line) {}

    /**
     * The request counts shown in the summary.
     *
     * @param open     total open requests.
     * @param player   requests waiting for a player to fulfill them.
     * @param retrying requests no resolver would take, which the retrying resolver keeps re-offering.
     */
    private record RequestCounts(int open, int player, int retrying) {}

    /**
     * Sends the report to chat, capped, while accumulating the complete text for the server log.
     */
    private static final class Report
    {
        private final CommandSourceStack source;
        private final StringBuilder      log = new StringBuilder("\n");

        Report(final CommandSourceStack source)
        {
            this.source = source;
        }

        /**
         * Emit a single unstyled line to both chat and the log.
         *
         * @param component the line.
         */
        void emit(final MutableComponent component)
        {
            emit(component, null);
        }

        /**
         * Emit a single line to both chat and the log.
         *
         * @param component the line.
         * @param color     the colour to use in chat, or null for the default.
         */
        void emit(final MutableComponent component, @Nullable final ChatFormatting color)
        {
            log.append(component.getString()).append('\n');
            final Component styled = color == null ? component : component.withStyle(color);
            source.sendSuccess(() -> styled, false);
        }

        /**
         * Emit a titled list. The whole list goes to the log, chat gets the first {@code cap} entries and a note about the rest.
         *
         * @param title   the section title, already carrying the count.
         * @param entries the entries.
         * @param cap     how many entries to show in chat.
         */
        void section(final MutableComponent title, final List<String> entries, final int cap)
        {
            if (entries.isEmpty())
            {
                log.append(title.getString()).append('\n');
                return;
            }

            emit(title, ChatFormatting.YELLOW);
            for (int i = 0; i < entries.size(); i++)
            {
                final String entry = "  " + entries.get(i);
                log.append(entry).append('\n');
                if (i < cap)
                {
                    final Component line = Component.literal(entry);
                    source.sendSuccess(() -> line, false);
                }
            }

            if (entries.size() > cap)
            {
                final Component more = Component.translatable(COMMAND_COLONY_DIAGNOSE_MORE, entries.size() - cap).withStyle(ChatFormatting.GRAY);
                source.sendSuccess(() -> more, false);
            }
        }

        /**
         * @return the complete report for the server log.
         */
        String getLog()
        {
            return log.toString();
        }
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "diagnose";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id()).executes(this::checkPreConditionAndExecute));
    }
}
