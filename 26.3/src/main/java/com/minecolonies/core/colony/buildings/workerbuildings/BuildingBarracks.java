package com.minecolonies.core.colony.buildings.workerbuildings;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.territory.HostileTerritory;
import com.minecolonies.api.colony.territory.HostileTerritoryMap;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.NBTUtils;
import com.minecolonies.core.client.gui.huts.WindowBarracksBuilding;
import com.minecolonies.core.colony.buildings.AbstractBuilding;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting;
import com.minecolonies.core.colony.buildings.views.AbstractBuildingView;
import com.minecolonies.core.colony.territory.BorderPatrol;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import com.minecolonies.api.util.Tuple;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.minecolonies.api.util.constant.Constants;

import static com.minecolonies.api.util.constant.Constants.STACKSIZE;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_BUILDING_MODULES;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_POS;

/**
 * Building class for the Barracks.
 */
public class BuildingBarracks extends AbstractBuilding
{
    /**
     * The module key the barracks' own settings page used to be registered under, kept only so the one setting it
     * held can be read out of an old save and turned into the tower tasks that replaced it. See
     * {@link #migrateLegacyBorderSetting}.
     */
    private static final String LEGACY_SETTINGS_MODULE = "barracks_settings";

    /**
     * The setting id the border patrol mode used to be stored under, inside {@link #LEGACY_SETTINGS_MODULE}.
     */
    private static final String LEGACY_BORDER_PATROL_KEY = Constants.MOD_ID + ":borderpatrol";

    /**
     * Name of our building's Schematics.
     */
    private static final String SCHEMATIC_NAME = "barracks";

    /**
     * How long a computed border stretch is kept before it is worked out again, in game ticks.
     * <p>
     * Twelve hundred ticks is one minute. The line moves when the player repaints a territory or when the colony's
     * claim changes shape; the first of those invalidates the plan immediately and for free (see
     * {@link #borderPlanSource}), and nothing at all reports the second, so age is the whole of the answer to it.
     * At the five minutes this used to be, a player who bought or lost a chunk watched his guards walk a border that
     * was no longer there for the rest of them. It is still not a per-tick cost: between recomputes, handing a guard
     * his next waypoint is a list index, and a recompute is 1225 hash lookups with no world access.
     */
    private static final int BORDER_PLAN_TTL_TICKS = 1200;

    /**
     * Max hut level of the Barracks.
     */
    private static final int BARRACKS_HUT_MAX_LEVEL = 5;

    /**
     * The tag to store the tower list to NBT.
     */
    private static final String TAG_TOWERS = "towers";

    /**
     * The list of barracksTowers.
     */
    private final List<BlockPos> towers = new ArrayList<>();

    /**
     * The goldcost for spies
     */
    public static int SPIES_GOLD_COST = 5;

    /**
     * The stretch of border currently being walked, sliced one entry per tower. Empty when there is no patrol on, or
     * when the border could not be found — see {@link #borderPlan} for why.
     */
    private final Map<BlockPos, List<BlockPos>> borderSlices = new HashMap<>();

    /**
     * The last plan computed, kept so the failure can be reported even when there are no waypoints.
     */
    private BorderPatrol.Plan borderPlan = null;

    /**
     * The game time {@link #borderPlan} was computed at.
     */
    private long borderPlanTime = Long.MIN_VALUE;

    /**
     * The territory index the plan was computed against.
     * <p>
     * {@link HostileTerritory} publishes a whole new immutable index every time a territory is created, painted or
     * deleted, so an identity comparison against this is an exact "has the enemy border moved?" test that costs one
     * reference compare. Nothing else in the colony gives that answer without walking chunks.
     */
    private HostileTerritoryMap borderPlanSource = null;

    /**
     * The towers the plan was sliced for, so a tower being built, destroyed or taken off the border re-slices the
     * line. The positions rather than a count, because which towers are patrolling decides how the line is cut and
     * two of them swapping tasks leaves the count alone.
     */
    private List<BlockPos> borderPlanTowers = List.of();

    /**
     * Whether an old save's barracks-level border patrol setting still has to be turned into tower tasks. Transient:
     * it is set while that save's NBT is being read and cleared by the tick that acts on it, and the setting cannot
     * come back because this building no longer writes it.
     */
    private boolean legacyBorderSettingPending = false;

    /**
     * Constructor for a AbstractBuilding.
     *
     * @param colony Colony the building belongs to.
     * @param pos    Location of the building (it's Hut Block).
     */
    public BuildingBarracks(@NotNull final IColony colony, final BlockPos pos)
    {
        super(colony, pos);
        keepX.put((stack) -> stack.getItem() == Items.GOLD_INGOT, new Tuple<>(STACKSIZE, true));
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return SCHEMATIC_NAME;
    }

    @SuppressWarnings("squid:S109")
    @Override
    public int getMaxBuildingLevel()
    {
        return BARRACKS_HUT_MAX_LEVEL;
    }

    @Override
    public void onDestroyed()
    {
        final Level world = getColony().getWorld();

        if (world != null)
        {
            for (final BlockPos tower : towers)
            {
                world.setBlockAndUpdate(tower, Blocks.AIR.defaultBlockState());
            }
        }
        super.onDestroyed();
        colony.getServerBuildingManager().guardBuildingChangedAt(this, 0);
    }

    @Override
    public void onUpgradeComplete(@Nullable final Blueprint blueprint, final int newLevel)
    {
        super.onUpgradeComplete(blueprint, newLevel);
        colony.getServerBuildingManager().guardBuildingChangedAt(this, newLevel);
    }

    @Override
    public void registerBlockPosition(@NotNull final BlockState block, @NotNull final BlockPos pos, @NotNull final Level world)
    {
        super.registerBlockPosition(block, pos, world);
        if (block.getBlock() == ModBlocks.blockHutBarracksTower)
        {
            final IBuilding building = getColony().getServerBuildingManager().getBuilding(pos);
            if (building instanceof BuildingBarracksTower)
            {
                building.setStructurePack(this.getStructurePack());
                ((BuildingBarracksTower) building).addBarracks(getPosition());
                if (!towers.contains(pos))
                {
                    towers.add(pos);
                }
            }
        }
    }

    @Override
    public void onColonyTick(@NotNull final IColony colony)
    {
        super.onColonyTick(colony);
        if (colony.getWorld().isClientSide())
        {
            return;
        }

        if (legacyBorderSettingPending)
        {
            migrateLegacyBorderSetting();
        }

        if (colony.getRaiderManager().isRaided())
        {
            if (!colony.getRaiderManager().areSpiesEnabled())
            {
                if (InventoryUtils.tryRemoveStackFromItemHandler(getItemHandlerCap(), new ItemStack(Items.GOLD_INGOT, SPIES_GOLD_COST)))
                {
                    colony.getRaiderManager().setSpiesEnabled(true);
                    colony.markDirty();
                }
            }
        }
        else
        {
            colony.getRaiderManager().setSpiesEnabled(false);
        }
    }

    @Override
    public int getClaimRadius(final int newLevel)
    {
        if (newLevel <= 0)
        {
            return 0;
        }

        // tower levels must all be 4+ to get increased radius of 3 
        int barracksClaimRadius = 3;
        for (final BlockPos pos : towers)
        {
            final IBuilding building = colony.getServerBuildingManager().getBuilding(pos);
            if (building != null)
            {
                if (building.getBuildingLevel() < 4) 
                { 
                    barracksClaimRadius = 2;
                    break;
                }
            }
        }
        return barracksClaimRadius;
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);
        towers.clear();
        towers.addAll(NBTUtils.streamCompound(compound.getListOrEmpty(TAG_TOWERS))
                        .map(resultCompound -> BlockPosUtil.read(resultCompound, TAG_POS))
                        .collect(Collectors.toList()));
        legacyBorderSettingPending = readLegacyBorderSetting(compound);
    }

    /**
     * Whether this save was written by a version that had the barracks-level border patrol setting, with that
     * setting turned on.
     * <p>
     * The setting is gone, so nothing registered reads its tag any more and {@code SettingsModule} never sees it.
     * The tag is still sitting in the save, though, and it is the only record of a player having asked for a border
     * patrol; ignoring it would turn his garrison off without saying so. Read raw rather than through the settings
     * machinery because there is deliberately no setting left to deserialise into.
     * <p>
     * The stored value is an index into the list of choices that was stored beside it, and index zero was
     * {@code Mode.OFF}. Anything else means the player had asked for one.
     *
     * @param compound the building's NBT.
     * @return true if a border patrol was on when this was saved.
     */
    private static boolean readLegacyBorderSetting(final CompoundTag compound)
    {
        final CompoundTag module = compound.getCompoundOrEmpty(TAG_BUILDING_MODULES).getCompoundOrEmpty(LEGACY_SETTINGS_MODULE);
        final ListTag settings = module.getListOrEmpty("settingslist");
        for (int i = 0; i < settings.size(); i++)
        {
            final CompoundTag entry = settings.getCompoundOrEmpty(i);
            if (LEGACY_BORDER_PATROL_KEY.equals(entry.getStringOr("key", "")))
            {
                return entry.getCompoundOrEmpty("value").getIntOr("value", 0) != 0;
            }
        }
        return false;
    }

    /**
     * Turn an old save's barracks-level border patrol into the tower tasks that replaced it.
     * <p>
     * Every built tower is put on {@code PATROL_BORDER}, which is what the setting used to mean for all of them at
     * once. A barracks that was set to the enemy frontier rather than its own claim lands on the colony border
     * instead, because that is the only border a guard task names; the enemy line has no task of its own.
     * <p>
     * Runs once: the flag is transient and this building no longer writes the tag it came from, so the next save
     * has nothing left to migrate.
     */
    private void migrateLegacyBorderSetting()
    {
        boolean seenTower = false;
        for (final BlockPos pos : towers)
        {
            if (colony.getServerBuildingManager().getBuilding(pos) instanceof final BuildingBarracksTower tower
                  && tower.getBuildingLevel() > 0)
            {
                seenTower = true;
                if (!tower.getTask().equals(GuardTaskSetting.PATROL_BORDER))
                {
                    tower.getModule(BuildingModules.GUARD_SETTINGS)
                      .getSetting(AbstractBuildingGuards.GUARD_TASK)
                      .set(GuardTaskSetting.PATROL_BORDER);
                    tower.markDirty();
                }
            }
        }

        // Only done once a tower has actually been seen. A barracks whose towers are still being read in would
        // otherwise consume the migration against an empty list and quietly lose it; a barracks with no towers at
        // all has nothing to migrate and stops here on the first tick either way.
        legacyBorderSettingPending = !towers.isEmpty() && !seenTower;
    }

    @Override
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag compound = super.serializeNBT(provider);
        final ListTag towerTagList = towers.stream().map(pos -> BlockPosUtil.write(new CompoundTag(), TAG_POS, pos)).collect(NBTUtils.toListNBT());
        compound.put(TAG_TOWERS, towerTagList);

        return compound;
    }

    public List<BlockPos> getTowers()
    {
        return towers;
    }

    /**
     * Which border this barracks has its towers walking.
     * <p>
     * Answered by the towers, not by a setting of its own. A border patrol is a guard task like every other guard
     * task -- the same {@code PATROL_BORDER} the stable offers -- and a barracks that has one is simply a barracks
     * one of whose towers has been set to it. The barracks stays the owner of the <em>line</em>, because one line
     * cut into slices is the only thing that keeps four towers off each other's ground, but it no longer owns the
     * switch.
     *
     * @return {@link BorderPatrol.Mode#COLONY} when at least one built tower is set to patrol the border,
     *         {@link BorderPatrol.Mode#OFF} otherwise.
     */
    @NotNull
    public BorderPatrol.Mode getBorderPatrolMode()
    {
        return liveTowers().isEmpty() ? BorderPatrol.Mode.OFF : BorderPatrol.Mode.COLONY;
    }

    /**
     * The piece of the border one tower is responsible for.
     * <p>
     * The whole stretch is found once, nearest this barracks, and then cut into as many contiguous pieces as there are
     * towers. Towers are ordered by where along the line they stand, so each one gets the piece it is nearest and the
     * four of them spread out along the frontier instead of walking on top of each other.
     *
     * @param tower the tower asking.
     * @return its waypoints in order, empty when there is no patrol on or no border was found.
     */
    @NotNull
    public List<BlockPos> getStretchFor(@NotNull final BlockPos tower)
    {
        refreshBorderPlan();
        return borderSlices.getOrDefault(tower, List.of());
    }

    /**
     * The border plan, for the diagnose report.
     * <p>
     * Refreshes first rather than returning whatever a tower last caused to be built, because a barracks whose towers
     * are not up yet has never called {@link #getStretchFor} and would otherwise report nothing at all — which is the
     * one state where the player most wants to be told whether there is a border out there to walk.
     *
     * @return the plan, or null when the setting is off.
     */
    @Nullable
    public BorderPatrol.Plan getBorderPlan()
    {
        refreshBorderPlan();
        return borderPlan;
    }

    /**
     * Work the border stretch out again, if anything that shapes it has changed.
     * <p>
     * The things that can invalidate a plan are checked in order of how cheap they are to check: no tower being on
     * the border task at all, the territory index having been republished (a reference compare), the set of
     * patrolling towers changing, and finally age. Only the last of those costs a chunk scan, and only once every
     * {@link #BORDER_PLAN_TTL_TICKS}.
     */
    private void refreshBorderPlan()
    {
        final List<BlockPos> live = liveTowers();
        if (live.isEmpty())
        {
            if (borderPlan != null)
            {
                borderPlan = null;
                borderPlanSource = null;
                borderPlanTowers = List.of();
                borderSlices.clear();
            }
            return;
        }

        // Always the colony's own border today: a guard task names one line, and the barracks-level setting that
        // used to offer the enemy frontier as well is gone. The territory branch below is kept rather than deleted
        // because it is the whole of what "has the enemy border moved" costs, and giving ENEMY a way back in should
        // be a change to this line and nothing else.
        final BorderPatrol.Mode mode = BorderPatrol.Mode.COLONY;
        final HostileTerritoryMap territory = mode == BorderPatrol.Mode.ENEMY ? HostileTerritory.in(colony.getDimension()) : null;
        final long now = colony.getWorld() == null ? 0 : colony.getWorld().getGameTime();

        if (borderPlan != null
              && borderPlan.mode() == mode
              && borderPlanSource == territory
              && borderPlanTowers.equals(live)
              && now - borderPlanTime < BORDER_PLAN_TTL_TICKS)
        {
            return;
        }

        borderPlan = BorderPatrol.findStretch(colony, getPosition(), mode, live.size());
        borderPlanSource = territory;
        borderPlanTowers = live;
        borderPlanTime = now;
        sliceBorderPlan(live);
    }

    /**
     * The towers of this barracks that actually exist, are built, and have been set to patrol the border.
     * <p>
     * The task filter is what makes the barracks-level switch unnecessary: a tower left on the ordinary patrol is
     * not in this list, so it is given no slice, so {@code BuildingBarracksTower#getBorderPatrolTarget} answers null
     * and it walks the route it always walked. It also means the line is cut for the towers that will actually walk
     * it rather than for every tower the barracks owns.
     *
     * @return their positions, in the order the barracks records them.
     */
    @NotNull
    private List<BlockPos> liveTowers()
    {
        final List<BlockPos> live = new ArrayList<>();
        for (final BlockPos pos : towers)
        {
            if (colony.getServerBuildingManager().getBuilding(pos) instanceof final BuildingBarracksTower tower
                  && tower.getBuildingLevel() > 0
                  && tower.getTask().equals(GuardTaskSetting.PATROL_BORDER))
            {
                live.add(pos);
            }
        }
        return live;
    }

    /**
     * Cut the computed stretch into one contiguous piece per tower.
     *
     * @param live the towers to cut it for.
     */
    private void sliceBorderPlan(@NotNull final List<BlockPos> live)
    {
        borderSlices.clear();
        if (borderPlan == null || !borderPlan.isUsable() || live.isEmpty())
        {
            return;
        }

        final List<BlockPos> line = borderPlan.waypoints();

        // Order the towers by where along the line they stand, so slice 0 goes to the tower nearest one end. Sorting
        // by nearest waypoint index rather than by position keeps the assignment stable across recomputes as long as
        // the line has not changed shape.
        final List<BlockPos> ordered = new ArrayList<>(live);
        ordered.sort(Comparator.comparingInt(pos -> BorderPatrol.nearestIndex(line, pos)));

        final int count = ordered.size();
        for (int i = 0; i < count; i++)
        {
            final int from = line.size() * i / count;
            final int to = line.size() * (i + 1) / count;
            // A stretch shorter than the tower count would otherwise hand somebody an empty slice, which reads as the
            // tower being broken. Give it the single nearest point instead: one guard standing on the line beats a
            // guard with nothing to do.
            borderSlices.put(ordered.get(i), from >= to ? List.of(line.get(Math.min(from, line.size() - 1))) : List.copyOf(line.subList(from, to)));
        }
    }


    /**
     * Barracks building View.
     */
    public static class View extends AbstractBuildingView
    {
        /**
         * Instantiate the barracks view.
         *
         * @param c the colonyview to put it in
         * @param l the positon
         */
        public View(final IColonyView c, final BlockPos l)
        {
            super(c, l);
        }

        @NotNull
        @Override
        public BOWindow getWindow()
        {
            return new WindowBarracksBuilding(this);
        }

        @Override
        public int getRange()
        {
            return getClaimRadius() * 16;
        }
    }
}
