package com.minecolonies.core.event;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.quests.IQuestInstance;
import com.minecolonies.api.quests.IQuestManager;
import com.minecolonies.api.quests.IQuestObjectiveTemplate;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.quests.objectives.*;
import net.minecraft.resources.Identifier;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * This class handles all permission checks on events and cancels them if needed.
 */
public class QuestObjectiveEventHandler
{
    /**
     * Building building objective tracker.
     */
    private static final Map<BuildingEntry, Map<IColony, List<IQuestInstance>>> buildBuildingObjectives = new HashMap<>();

    /**
     * Research objective tracker.
     */
    private static final Map<Identifier, Map<IColony, List<IQuestInstance>>> researchObjectives = new HashMap<>();

    /**
     * Mine block objective tracker.
     */
    private static final Map<Block, Map<UUID, List<IQuestInstance>>> breakBlockObjectives = new HashMap<>();

    /**
     * Entity kill objective tracker.
     */
    private static final Map<EntityType<?>, Map<UUID, List<IQuestInstance>>> entityKillObjectives = new HashMap<>();

    /**
     * Place block objective tracker.
     */
    private static final Map<Block, Map<UUID, List<IQuestInstance>>> placeBlockObjectives = new HashMap<>();

    /**
     * Installs the callbacks. Called once from the mod entry point.
     * <p>
     * Port note (contract C5): {@code BlockEvent.BreakEvent} became
     * {@link PlayerBlockBreakEvents#AFTER} and {@code LivingDeathEvent} became
     * {@link ServerLivingEntityEvents#AFTER_DEATH}. {@code BlockEvent.EntityPlaceEvent} has no Fabric
     * counterpart at all -- see {@link #onBlockPlace}.
     */
    public static void register()
    {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> onBlockBreak(level, player, state));
        ServerLivingEntityEvents.AFTER_DEATH.register(QuestObjectiveEventHandler::onEntityDeath);
    }

    /**
     * Block break handler.
     *
     * @param world  the level the block was broken in.
     * @param player the breaking player.
     * @param state  the broken block state.
     */
    private static void onBlockBreak(final LevelAccessor world, final Player player, final BlockState state)
    {
        if (world.isClientSide())
        {
            return;
        }

        final Block block = state.getBlock();
        if (breakBlockObjectives.containsKey(block) && breakBlockObjectives.get(block).containsKey(player.getUUID()))
        {
            final List<IQuestInstance> objectives = breakBlockObjectives.get(block).get(player.getUUID());
            for (IQuestInstance colonyQuest : new ArrayList<>(objectives))
            {
                final IQuestObjectiveTemplate objective = IQuestManager.GLOBAL_SERVER_QUESTS.get(colonyQuest.getId()).getObjective(colonyQuest.getObjectiveIndex());
                if (objective instanceof IBreakBlockObjectiveTemplate)
                {
                    ((IBreakBlockObjectiveTemplate) objective).onBlockBreak(colonyQuest.getCurrentObjectiveInstance(), colonyQuest, player);
                }
                else
                {
                    objectives.remove(colonyQuest);
                    break;
                }
            }
        }
    }

    /**
     * Entity death handler.
     *
     * @param entity the entity that died.
     * @param source what killed it.
     */
    private static void onEntityDeath(final LivingEntity entity, final DamageSource source)
    {
        if (source.getEntity() instanceof Player
              && entityKillObjectives.containsKey(entity.getType())
              && entityKillObjectives.get(entity.getType()).containsKey(source.getEntity().getUUID()))
        {
            final List<IQuestInstance> objectives = entityKillObjectives.get(entity.getType()).get(source.getEntity().getUUID());
            for (IQuestInstance colonyQuest : new ArrayList<>(objectives))
            {
                final IQuestObjectiveTemplate objective = IQuestManager.GLOBAL_SERVER_QUESTS.get(colonyQuest.getId()).getObjective(colonyQuest.getObjectiveIndex());
                if (objective instanceof IKillEntityObjectiveTemplate)
                {
                    ((IKillEntityObjectiveTemplate) objective).onEntityKill(colonyQuest.getCurrentObjectiveInstance(), colonyQuest, (Player) source.getEntity());
                }
                else
                {
                    objectives.remove(colonyQuest);
                    break;
                }
            }
        }
    }

    /**
     * Block place handler.
     * <p>
     * <b>NEVER CALLED (degradation ladder step 2).</b> This was {@code BlockEvent.EntityPlaceEvent}; neither
     * Fabric API nor vanilla 26.2 fires anything equivalent -- there is no "a player placed a block" callback,
     * only {@code UseBlockCallback}, which runs before placement and cannot tell whether one happened. The body
     * is kept intact and public so a mixin can drive it later; until then "place N blocks" quest objectives
     * never advance.
     *
     * @param world  the level the block was placed in.
     * @param placer the placing entity.
     * @param placed the placed block state.
     */
    public static void onBlockPlace(final LevelAccessor world, final Entity placer, final BlockState placed)
    {
        if (world.isClientSide() || !(placer instanceof Player))
        {
            return;
        }

        final Block block = placed.getBlock();
        if (placeBlockObjectives.containsKey(block) && placeBlockObjectives.get(block).containsKey(placer.getUUID()))
        {
            final List<IQuestInstance> objectives = placeBlockObjectives.get(block).get(placer.getUUID());
            for (IQuestInstance colonyQuest : new ArrayList<>(objectives))
            {
                final IQuestObjectiveTemplate objective = IQuestManager.GLOBAL_SERVER_QUESTS.get(colonyQuest.getId()).getObjective(colonyQuest.getObjectiveIndex());
                if (objective instanceof IPlaceBlockObjectiveTemplate)
                {
                    ((IPlaceBlockObjectiveTemplate) objective).onBlockPlace(colonyQuest.getCurrentObjectiveInstance(), colonyQuest, (Player) placer);
                }
                else
                {
                    objectives.remove(colonyQuest);
                    break;
                }
            }
        }
    }

    /**
     * Add an objective listener for block mining to this event handler.
     *
     * @param blockToMine    the block that we listen for.
     * @param assignedPlayer the player we check for.
     * @param colonyQuest    the colony quest it is related to.
     */
    public static void addQuestMineObjectiveListener(final Block blockToMine, final UUID assignedPlayer, final IQuestInstance colonyQuest)
    {
        final Map<UUID, List<IQuestInstance>> currentMap = breakBlockObjectives.getOrDefault(blockToMine, new HashMap<>());
        final List<IQuestInstance> objectives = currentMap.getOrDefault(assignedPlayer, new ArrayList<>());
        objectives.add(colonyQuest);
        currentMap.put(assignedPlayer, objectives);
        breakBlockObjectives.put(blockToMine, currentMap);
    }

    /**
     * Remove an objective listener to this event handler.
     *
     * @param blockToMine    the block that we listen for.
     * @param assignedPlayer the player we check for.
     * @param colonyQuest    the colony quest it is related to.
     */
    public static void removeQuestMineObjectiveListener(final Block blockToMine, final UUID assignedPlayer, final IQuestInstance colonyQuest)
    {
        breakBlockObjectives.getOrDefault(blockToMine, new HashMap<>()).getOrDefault(assignedPlayer, new ArrayList<>()).remove(colonyQuest);
    }

    /**
     * Add an objective listener for block placement to this event handler.
     *
     * @param blockToPlace    the block that we listen for.
     * @param assignedPlayer the player we check for.
     * @param colonyQuest    the colony quest it is related to.
     */
    public static void addQuestPlaceObjectiveListener(final Block blockToPlace, final UUID assignedPlayer, final IQuestInstance colonyQuest)
    {
        final Map<UUID, List<IQuestInstance>> currentMap = placeBlockObjectives.getOrDefault(blockToPlace, new HashMap<>());
        final List<IQuestInstance> objectives = currentMap.getOrDefault(assignedPlayer, new ArrayList<>());
        objectives.add(colonyQuest);
        currentMap.put(assignedPlayer, objectives);
        placeBlockObjectives.put(blockToPlace, currentMap);
    }

    /**
     * Remove an objective listener for block placement to this event handler.
     *
     * @param blockToPlace    the block that we listen for.
     * @param assignedPlayer the player we check for.
     * @param colonyQuest    the colony quest it is related to.
     */
    public static void removeQuestPlaceBlockObjectiveListener(final Block blockToPlace, final UUID assignedPlayer, final IQuestInstance colonyQuest)
    {
        placeBlockObjectives.getOrDefault(blockToPlace, new HashMap<>()).getOrDefault(assignedPlayer, new ArrayList<>()).remove(colonyQuest);
    }

    /**
     * Add an objective listener to this event handler.
     *
     * @param entityToKill   the entity type that we listen for.
     * @param assignedPlayer the player we check for.
     * @param colonyQuest    the colony quest it is related to.
     */
    public static void addKillQuestObjectiveListener(final EntityType<?> entityToKill, final UUID assignedPlayer, final IQuestInstance colonyQuest)
    {
        final Map<UUID, List<IQuestInstance>> currentMap = entityKillObjectives.getOrDefault(entityToKill, new HashMap<>());
        final List<IQuestInstance> objectives = currentMap.getOrDefault(assignedPlayer, new ArrayList<>());
        objectives.add(colonyQuest);
        currentMap.put(assignedPlayer, objectives);
        entityKillObjectives.put(entityToKill, currentMap);
    }

    /**
     * Remove an objective listener to this event handler.
     *
     * @param entityToKill   the entity type that we listen for.
     * @param assignedPlayer the player we check for.
     * @param colonyQuest    the colony quest it is related to.
     */
    public static void removeKillQuestObjectiveListener(final EntityType<?> entityToKill, final UUID assignedPlayer, final IQuestInstance colonyQuest)
    {
        entityKillObjectives.getOrDefault(entityToKill, new HashMap<>()).getOrDefault(assignedPlayer, new ArrayList<>()).remove(colonyQuest);
    }

    /**
     * Research complete event.
     * @param colony the colony the research happened in.
     * @param id the research id.
     */
    public static void onResearchComplete(final IColony colony, final Identifier id)
    {
        if (researchObjectives.containsKey(id))
        {
            for (final IQuestInstance instance : new ArrayList<>(researchObjectives.get(id).getOrDefault(colony, new ArrayList<>())))
            {
                final IQuestObjectiveTemplate objective = IQuestManager.GLOBAL_SERVER_QUESTS.get(instance.getId()).getObjective(instance.getObjectiveIndex());
                if (objective instanceof IResearchObjectiveTemplate researchTemplate)
                {
                    researchTemplate.onResearchCompletion(instance);
                }
            }
        }
    }

    /**
     * Building upgrade handling.
     * @param building the building being upgraded.
     * @param level its level.
     */
    public static void onBuildingUpgradeComplete(final IBuilding building, final int level)
    {
        if (buildBuildingObjectives.containsKey(building.getBuildingType()))
        {
            for (final IQuestInstance instance : new ArrayList<>(buildBuildingObjectives.get(building.getBuildingType()).getOrDefault(building.getColony(), new ArrayList<>())))
            {
                final IQuestObjectiveTemplate objective = IQuestManager.GLOBAL_SERVER_QUESTS.get(instance.getId()).getObjective(instance.getObjectiveIndex());
                if (objective instanceof IBuildingUpgradeObjectiveTemplate buildingTemplate)
                {
                    buildingTemplate.onBuildingUpgrade(instance.getCurrentObjectiveInstance(), instance, level);
                }
            }
        }
    }

    /**
     * Track building leveling.
     * @param buildingEntry the building to track.
     * @param colonyQuest the quest tracking it.
     */
    public static void trackBuildingLevelUp(final @NotNull BuildingEntry buildingEntry, final @NotNull IQuestInstance colonyQuest)
    {
        final Map<IColony, List<IQuestInstance>> currentMap = buildBuildingObjectives.getOrDefault(buildingEntry, new HashMap<>());
        final List<IQuestInstance> objectives = currentMap.getOrDefault(colonyQuest.getColony(), new ArrayList<>());
        objectives.add(colonyQuest);
        currentMap.put(colonyQuest.getColony(), objectives);
        buildBuildingObjectives.put(buildingEntry, currentMap);
    }

    /**
     * Stop tracking building leveling.
     * @param buildingEntry the building to stop tracking.
     * @param colonyQuest the quest tracking it.
     */
    public static void stopTrackingBuildingLevelUp(final @NotNull BuildingEntry buildingEntry, final @NotNull IQuestInstance colonyQuest)
    {
        buildBuildingObjectives.getOrDefault(buildingEntry, new HashMap<>()).getOrDefault(colonyQuest.getColony(), new ArrayList<>()).remove(colonyQuest);
    }

    /**
     * Track research.
     * @param researchId the research to track.
     * @param colonyQuest the quest tracking it.
     */
    public static void trackResearch(final Identifier researchId, final IQuestInstance colonyQuest)
    {
        final Map<IColony, List<IQuestInstance>> currentMap = researchObjectives.getOrDefault(researchId, new HashMap<>());
        final List<IQuestInstance> objectives = currentMap.getOrDefault(colonyQuest.getColony(), new ArrayList<>());
        objectives.add(colonyQuest);
        currentMap.put(colonyQuest.getColony(), objectives);
        researchObjectives.put(researchId, currentMap);
    }

    /**
     * Stop tracking research.
     * @param researchId the research to stop tracking.
     * @param colonyQuest the quest tracking it.
     */
    public static void stopTrackingResearch(final @NotNull Identifier researchId, final @NotNull IQuestInstance colonyQuest)
    {
        researchObjectives.getOrDefault(researchId, new HashMap<>()).getOrDefault(colonyQuest.getColony(), new ArrayList<>()).remove(colonyQuest);
    }
}
