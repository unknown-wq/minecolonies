package com.minecolonies.core.event;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.blocks.interfaces.IRSComponentBlock;
import com.minecolonies.api.client.render.modeltype.CitizenModel;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IVisitorData;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.IGuardBuilding;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.interactionhandling.ChatPriority;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.entity.ai.statemachine.tickratestatemachine.TickRateStateMachine;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.entity.other.AbstractFastMinecoloniesEntity;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.api.util.*;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.blocks.BlockScarecrow;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;
import com.minecolonies.api.loot.GenerateSupplyLoot;
import com.minecolonies.core.generation.defaults.DefaultCropsLootProvider;
import com.minecolonies.core.generation.defaults.DefaultLootModifiersProvider;
import com.minecolonies.core.blocks.huts.BlockHutTownHall;
import com.minecolonies.core.client.render.RenderBipedCitizen;
import com.minecolonies.core.colony.territory.HostileTerritoryFrontier;
import com.minecolonies.core.colony.territory.HostileTerritorySight;
import com.minecolonies.core.colony.ColonyManager;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.TavernBuildingModule;
import com.minecolonies.core.colony.interactionhandling.RecruitmentInteraction;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.colony.jobs.JobFarmer;
import com.minecolonies.core.colony.requestsystem.locations.EntityLocation;
import com.minecolonies.core.commands.EntryPoint;
import com.minecolonies.core.entity.ai.animals.AnimalPen;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import com.minecolonies.core.entity.mobs.EntityMercenary;
import com.minecolonies.core.items.ItemBannerRallyGuards;
import com.minecolonies.core.items.ItemFieldStick;
import com.minecolonies.core.network.messages.client.OpenSuggestionWindowMessage;
import com.minecolonies.core.util.ChunkDataHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.*;

import static com.minecolonies.api.research.util.ResearchConstants.SOFT_SHOES;
import static com.minecolonies.api.util.constant.ColonyManagerConstants.NO_COLONY_ID;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_COLONY_ID;
import static com.minecolonies.api.util.constant.NbtTagConstants.TAG_EVENT_ID;
import static com.minecolonies.api.util.constant.TranslationConstants.*;
import static com.minecolonies.api.util.constant.translation.BaseGameTranslationConstants.BASE_BED_OCCUPIED;

/**
 * Handles all forge events.
 */
public class EventHandler
{
    /**
     * Player position map for watching chunk entries
     */
    private static final Map<UUID, ChunkPos> playerPositions = new HashMap<>();

    /**
     * Installs every common callback. Called once from the mod entry point (contract C5).
     */
    public static void register()
    {
        // was: @SubscribeEvent onCommandsRegister(RegisterCommandsEvent)
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> EntryPoint.register(dispatcher));

        // was: @SubscribeEvent(HIGHEST) onEntityAdded(EntityJoinLevelEvent)
        ServerEntityEvents.ENTITY_LOAD.register(EventHandler::onEntityAdded);

        // was: @SubscribeEvent on(MobSpawnEvent.PositionCheck) -- keeps hostiles out of colony buildings.
        ServerEntityEvents.ALLOW_LOAD.register(EventHandler::allowSpawn);

        // was: @SubscribeEvent onLootTableLoad(LootTableLoadEvent)
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> onLootTableLoad(key, tableBuilder));

        // was: @SubscribeEvent onChunkLoad/onChunkUnLoad(ChunkEvent.Load/Unload)
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, newChunk) -> ChunkDataHelper.loadChunk(chunk, level));
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> ChunkDataHelper.unloadChunk(chunk, level));

        // was: @SubscribeEvent(LOWEST) onEntityTravelToDimensionEvent + playerChangeDim. Fabric has no
        // "about to change dimension" hook, only the after-the-fact one, so both halves run from it: the old
        // level is what the event hands us as `origin`.
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(EventHandler::playerChangeDim);

        // was: @SubscribeEvent onEnteringChunk(PlayerTickEvent.Pre) and onServerTick(ServerTickEvent.Pre)
        ServerTickEvents.START_SERVER_TICK.register(EventHandler::onServerTick);

        // was: @SubscribeEvent onPlayerEnterWorld/onPlayerLeaveWorld(PlayerEvent.PlayerLogged*Event)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerEnterWorld(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onPlayerLeaveWorld(handler.player));

        // was: @SubscribeEvent onBlockBreak(BlockEvent.BreakEvent)
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> onBlockBreak(level, state, blockEntity));

        // was: @SubscribeEvent onPlayerInteract(PlayerInteractEvent.RightClickBlock)
        UseBlockCallback.EVENT.register(EventHandler::onPlayerInteract);

        // was: @SubscribeEvent(HIGHEST) onWorldLoad / onWorldUnload(LevelEvent.Load/Unload)
        ServerLevelEvents.LOAD.register((server, level) -> IColonyManager.getInstance().onWorldLoad(level));
        ServerLevelEvents.UNLOAD.register((server, level) -> IColonyManager.getInstance().onWorldUnload(level));
    }

    /**
     * On Entity join do this.
     * <p>
     * Port note (degradation ladder step 3): {@code EntityJoinLevelEvent} was cancellable and fired
     * <em>before</em> the entity was added; {@link ServerEntityEvents#ENTITY_LOAD} fires after, and Fabric has
     * no cancellable equivalent. The two guards that used to cancel the join now {@code discard()} the entity
     * instead -- except the "duplicate {@code AbstractFastMinecoloniesEntity}" guard, which cannot survive the
     * move at all: by the time this runs the entity is already in the level's index, so
     * {@code level.getEntity(uuid)} always finds it (itself) and the test degenerates into "always true".
     *
     * @param entity the entity that joined.
     * @param level  the level it joined.
     */
    private static void onEntityAdded(@NotNull final Entity entity, @NotNull final ServerLevel level)
    {
        if (MineColonies.getConfig().getServer().mobAttackCitizens.get() && entity instanceof Mob && entity instanceof Enemy
              && !entity.getType().builtInRegistryHolder().is(ModTags.mobAttackBlacklist)
              && !(entity instanceof AbstractFastMinecoloniesEntity))
        {
            ((Mob) entity).targetSelector.addGoal(6,
              new NearestAttackableTargetGoal<>((Mob) entity, EntityCitizen.class, true, (citizen, serverLevel) -> !citizen.isInvisible()));
            ((Mob) entity).targetSelector.addGoal(7, new NearestAttackableTargetGoal<>((Mob) entity, EntityMercenary.class, true));
        }

        if (entity instanceof final Animal animal)
        {
            AnimalPen.onAnimalLoaded(animal);
        }

        if (entity instanceof EntityCitizen citizen && citizen.getCitizenColonyHandler().getColonyId() == 0)
        {
            Log.getLogger().info("Prevented citizen with colony id 0 from joining world");
            entity.discard();
        }
    }

    /**
     * Vanilla block loot tables that may additionally drop a MineColonies crop, resolved once on first use.
     * <p>
     * Computed lazily rather than in a static initialiser because it walks {@link ModBlocks#getCrops()}, which is
     * only populated after block registration; loot tables load much later, so by the time this runs the set is
     * complete.
     */
    private static Set<ResourceKey<LootTable>> cropSourceTables = null;

    /**
     * Injects everything that used to be a NeoForge global loot modifier.
     * <p>
     * Port note: NeoForge's {@code AddTableLootModifier} wrote {@code data/neoforge/loot_modifiers/*.json} at
     * datagen time and applied them at runtime. Fabric has no data-driven equivalent, so the same three
     * injections are performed here from the tables kept in
     * {@link com.minecolonies.core.generation.defaults.DefaultLootModifiersProvider}. The behaviour is meant to
     * be identical: a modifier adding table T under condition C is a pool holding a single reference to T,
     * guarded by C.
     *
     * @param key          the loot table being loaded.
     * @param tableBuilder the builder to add pools to.
     */
    private static void onLootTableLoad(final ResourceKey<LootTable> key, final LootTable.Builder tableBuilder)
    {
        if (key.equals(BuiltInLootTables.SIMPLE_DUNGEON))
        {
            final LootPool.Builder pool = LootPool.lootPool();
            for (final MinecoloniesCropBlock crop : ModBlocks.getCrops())
            {
                pool.add(LootItem.lootTableItem(crop)
                        .when(LootItemRandomChanceCondition.randomChance(0.005f)));
            }
            tableBuilder.withPool(pool);
        }

        if (cropSourceTables == null)
        {
            cropSourceTables = DefaultLootModifiersProvider.cropSourceTables();
        }

        // breaking a vanilla block that this crop is "dropped from" also rolls the crop's own source sub-table
        if (cropSourceTables.contains(key))
        {
            tableBuilder.withPool(LootPool.lootPool()
                    .add(NestedLootTable.lootTableReference(DefaultCropsLootProvider.getCropSourceLootTable(key.identifier()))));
        }

        if (DefaultLootModifiersProvider.SUPPLY_CAMP_SOURCES.contains(key))
        {
            tableBuilder.withPool(LootPool.lootPool()
                    .when(GenerateSupplyLoot.when())
                    .add(NestedLootTable.lootTableReference(DefaultLootModifiersProvider.SUPPLY_CAMP_TABLE)));
        }

        if (DefaultLootModifiersProvider.SUPPLY_SHIP_SOURCES.contains(key))
        {
            tableBuilder.withPool(LootPool.lootPool()
                    .when(GenerateSupplyLoot.when())
                    .add(NestedLootTable.lootTableReference(DefaultLootModifiersProvider.SUPPLY_SHIP_TABLE)));
        }
    }

    /**
     * Removes the player from the colony it left and adds it to the one it arrived in.
     * <p>
     * Port note: this merges the old {@code EntityTravelToDimensionEvent} (leave) and
     * {@code PlayerEvent.PlayerChangedDimensionEvent} (arrive) handlers, because Fabric only offers the
     * after-the-move callback. The chunk the player left is resolved in {@code origin} using the player's
     * <em>new</em> chunk position, which is what {@code ServerPlayer#teleportTo} preserves for a plain
     * dimension change; a cross-dimension teleport that also moves the player horizontally may therefore fail
     * to unsubscribe it from the old colony.
     *
     * @param player      the player.
     * @param origin      the level it came from.
     * @param destination the level it arrived in.
     */
    private static void playerChangeDim(final ServerPlayer player, final ServerLevel origin, final ServerLevel destination)
    {
        final LevelChunk oldChunk = origin.getChunk(player.chunkPosition().x(), player.chunkPosition().z());
        final int owningColony = ColonyUtils.getOwningColony(oldChunk);

        // Remove visiting/subscriber from old colony
        if (owningColony != 0)
        {
            final IColony oldColony = IColonyManager.getInstance().getColonyByWorld(owningColony, origin);
            if (oldColony != null)
            {
                oldColony.removeVisitingPlayer(player);
                oldColony.getPackageManager().removeCloseSubscriber(player);
            }
        }

        final LevelChunk newChunk = destination.getChunk(player.chunkPosition().x(), player.chunkPosition().z());

        // Add visiting/subscriber to new colony
        final IColony newColony = IColonyManager.getInstance().getColonyByWorld(ColonyUtils.getOwningColony(newChunk), destination);
        if (newColony != null)
        {
            newColony.addVisitingPlayer(player);
            newColony.getPackageManager().addCloseSubscriber(player);
        }
    }

    /**
     * Event called when the player enters a new chunk.
     * <p>
     * Port note: was {@code PlayerTickEvent.Pre}; Fabric has no per-player tick event, so this is driven from
     * the server tick over the player list instead. The "every 100 game ticks" gate is unchanged.
     *
     * @param player the player to check.
     */
    private static void onEnteringChunk(final ServerPlayer player)
    {
        if (!(player.level() instanceof final ServerLevel world) || player.level().getGameTime() % 100 != 0)
        {
            return;
        }

        final ChunkPos chunkPos = player.chunkPosition();

        final ChunkPos oldPos = playerPositions.get(player.getUUID());
        if (oldPos != null && oldPos.equals(chunkPos))
        {
            return;
        }

        playerPositions.put(player.getUUID(), chunkPos);

        final LevelChunk chunk = world.getChunk(chunkPos.x(), chunkPos.z());

        if (chunk.isEmpty())
        {
            return;
        }

        ChunkDataHelper.loadChunk(chunk, world);

        final ChunkCapData chunkCapData = ColonyUtils.getChunkCapData(chunk);

        // Check if we get into a differently claimed chunk
        if (chunkCapData.getOwningColony() != -1)
        {
            // Remove visiting/subscriber from old colony
            final IColony colony = IColonyManager.getInstance().getColonyByWorld(chunkCapData.getOwningColony(), world);
            if (colony != null)
            {
                colony.addVisitingPlayer(player);
                colony.getPackageManager().addCloseSubscriber(player);
            }
        }

        // A hostile territory is the one claim you need to see from outside rather than from inside, so its
        // subscribers are picked up by proximity instead of by standing on it. Free when there is no territory in
        // the dimension.
        HostileTerritorySight.subscribeNearby(world, player, chunkPos);

        // ... and having got the border onto his screen, say whose it is. Same gate: free when the dimension has no
        // territory in it.
        HostileTerritoryFrontier.onEnteringChunk(world, player);

        // Alert nearby buildings of close player
        if (chunkCapData.getOwningColony() != 0)
        {
            for (final Map.Entry<Integer, Set<BlockPos>> entry : chunkCapData.getAllClaimingBuildings().entrySet())
            {
                final IColony newColony = IColonyManager.getInstance().getColonyByWorld(entry.getKey(), world);
                if (newColony != null)
                {
                    for (final BlockPos buildingPos : entry.getValue())
                    {
                        IBuilding building = newColony.getServerBuildingManager().getBuilding(buildingPos);
                        if (building != null)
                        {
                            building.onPlayerEnterNearby(player);
                        }
                    }
                }
            }
        }
    }

    /**
     * Spawn veto; replaces {@code MobSpawnEvent.PositionCheck}.
     * <p>
     * {@link ServerEntityEvents#ALLOW_LOAD} is fired from {@code PersistentEntitySectionManager#addEntity},
     * i.e. in front of every entity that is about to enter a server level, and returning {@code false}
     * cancels the add so the mob never exists. That is a much wider net than the NeoForge event, so the
     * filter has to be narrow: only a real {@link Enemy}, only a fresh spawn (never one being read back
     * off disk), and only the two reasons {@code NaturalSpawner} uses. Everything the mod spawns itself --
     * raiders ({@code EVENT}), citizens, visitors and mercenaries ({@code MOB_SUMMONED}) -- is outside that
     * set and is never looked at, and neither are spawners, spawn eggs, {@code /summon} or breeding, which
     * matches upstream (it excluded {@code SPAWNER} explicitly).
     *
     * @param entity      the entity about to be added.
     * @param level       the level it is being added to.
     * @param spawnReason why it is being added.
     * @param fromDisk    whether it is being read back rather than spawned.
     * @return whether the spawn may go ahead.
     */
    private static boolean allowSpawn(
      final Entity entity, final ServerLevel level, final EntitySpawnReason spawnReason, final boolean fromDisk)
    {
        if (fromDisk
              || !(entity instanceof Enemy)
              || (spawnReason != EntitySpawnReason.NATURAL && spawnReason != EntitySpawnReason.CHUNK_GENERATION))
        {
            return true;
        }

        return !isSpawnBlockedByBuilding(level, entity.blockPosition());
    }

    /**
     * Whether a hostile mob may spawn at the given position. Reached from {@link #allowSpawn}.
     *
     * @param level the level.
     * @param pos   the candidate spawn position.
     * @return whether the spawn should be blocked.
     */
    public static boolean isSpawnBlockedByBuilding(final Level level, final BlockPos pos)
    {
        if (level.isClientSide() || !WorldUtil.isEntityBlockLoaded(level, pos))
        {
            return false;
        }

        final LevelChunk chunk = level.getChunkAt(pos);
        final int owningColony = ColonyUtils.getOwningColony(chunk);
        if (owningColony == NO_COLONY_ID)
        {
            return false;
        }
        final IColony newColony = IColonyManager.getInstance().getColonyByWorld(owningColony, level);
        if (newColony == null)
        {
            return false;
        }

        for (final BlockPos buildingPos : ColonyUtils.getAllClaimingBuildings(chunk).getOrDefault(owningColony, Collections.emptySet()))
        {
            final IBuilding building = newColony.getServerBuildingManager().getBuilding(buildingPos);
            if (building != null && building.getBuildingLevel() >= 1 && building.isInBuilding(pos))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Event called when a player enters the world.
     *
     * @param event player enter world event
     */
    private static void onPlayerEnterWorld(final ServerPlayer player)
    {
        {
            for (final IColony colony : IColonyManager.getInstance().getAllColonies())
            {
                if (colony.getPermissions().getRank(player).isColonyManager())
                {
                    colony.getPackageManager().addImportantColonyPlayer(player);
                    colony.getPackageManager().sendColonyViewPackets();
                    colony.getPackageManager().sendPermissionsPackets();
                }
            }

            final int size = player.getInventory().getContainerSize();
            for (int i = 0; i < size; i++)
            {
                final ItemStack stack = player.getInventory().getItem(i);
                if (stack.getItem() instanceof ItemBannerRallyGuards)
                {
                    ItemBannerRallyGuards.broadcastPlayerToRally(stack, player.level(), new EntityLocation(player.getUUID()));
                }
            }
        }
    }

    /**
     * Event called when a player leaves the world.
     *
     * @param event player leaves world event
     */
    private static void onPlayerLeaveWorld(final ServerPlayer player)
    {
        {
            for (final IColony colony : IColonyManager.getInstance().getAllColonies())
            {
                colony.getPackageManager().removeCloseSubscriber(player);
                colony.getPackageManager().removeImportantColonyPlayer(player);
                playerPositions.remove(player.getUUID());
            }
        }
        HostileTerritoryFrontier.forget(player);
    }

    /**
     * Event called when a citizen enters a new chunk.
     */
    public static void onEnteringChunkEntity(@NotNull final EntityCitizen entityCitizen, final ChunkPos newChunkPos)
    {
        if (MineColonies.getConfig().getServer().pvp_mode.get() && newChunkPos != null)
        {
            if (entityCitizen.level() == null || !WorldUtil.isEntityChunkLoaded(entityCitizen.level(), new ChunkPos(newChunkPos.x(), newChunkPos.z())))
            {
                return;
            }

            if (entityCitizen.getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard)
            {
                final Level world = entityCitizen.level();

                final LevelChunk chunk = world.getChunk(newChunkPos.x(), newChunkPos.z());
                final int owningColony = ColonyUtils.getOwningColony(chunk);
                if (owningColony != NO_COLONY_ID
                      && entityCitizen.getCitizenColonyHandler().getColonyId() != owningColony)
                {
                    final IColony colony = IColonyManager.getInstance().getColonyByWorld(owningColony, entityCitizen.level());
                    if (colony != null)
                    {
                        colony.addGuardToAttackers(entityCitizen, ((IGuardBuilding) entityCitizen.getCitizenColonyHandler().getWorkBuilding()).getPlayerToFollowOrRally());
                    }
                }
            }
        }
    }

    /**
     * Event called on player block breaks.
     *
     * @param event the event.
     */
    private static void onBlockBreak(@NotNull final Level world, @NotNull final BlockState state, final BlockEntity spawner)
    {
        if (world.isClientSide())
        {
            return;
        }

        if (state.getBlock() instanceof SpawnerBlock)
        {
            if (spawner instanceof SpawnerBlockEntity spawnerBE && spawnerBE.getSpawner().nextSpawnData != null)
            {
                final IColony colony = IColonyManager.getInstance()
                                         .getColonyByDimension(spawnerBE.getSpawner().nextSpawnData.getEntityToSpawn().getIntOr(TAG_COLONY_ID, 0),
                    world.dimension());
                if (colony != null)
                {
                    colony.getEventManager().onTileEntityBreak(spawnerBE.getSpawner().nextSpawnData.getEntityToSpawn().getIntOr(TAG_EVENT_ID, 0), spawner);
                }
            }
        }
    }

    /**
     * Event when a player right clicks a block, or right clicks with an item. The interaction is refused when
     * the player has no permission, or no permission to place a hut and tried it.
     * <p>
     * Port note (contract C5): was {@code PlayerInteractEvent.RightClickBlock}. Fabric's
     * {@link UseBlockCallback} carries the same information -- the clicked position and face come out of the
     * {@link BlockHitResult}, the stack out of the hand -- and "cancelled" is expressed by returning
     * {@link InteractionResult#FAIL} instead of {@code setCanceled(true)}.
     *
     * @param player    the interacting player.
     * @param world     the level.
     * @param hand      the hand used.
     * @param hitResult what was hit.
     * @return PASS to let vanilla continue, FAIL to swallow the interaction.
     */
    private static InteractionResult onPlayerInteract(
      @NotNull final Player player,
      final Level world,
      final InteractionHand hand,
      final BlockHitResult hitResult)
    {
        final BlockPos pos = hitResult.getBlockPos();
        final ItemStack itemStack = player.getItemInHand(hand);
        BlockPos bedBlockPos = pos;

        // The field stick claims the click before anything else looks at it, otherwise the scarecrow and the hut
        // blocks - both of which return SUCCESS from useItemOn unconditionally - would swallow it first and
        // Item#useOn would never run. CONSUME rather than FAIL on the client for the reason spelled out on
        // handleEventCancellation: FAIL cancels the whole method and the packet is never sent, so the server would
        // never learn about the click.
        if (itemStack.getItem() instanceof final ItemFieldStick fieldStick)
        {
            if (world.isClientSide())
            {
                return InteractionResult.CONSUME;
            }
            return fieldStick.handleUse((ServerPlayer) player, world, hand, hitResult);
        }

        // this was the simple way of doing it, minecraft calls onBlockActivated
        // and uses that return value, but I didn't want to call it twice
        if (playerRightClickInteract(player, world, pos))
        {
            final Block block = world.getBlockState(pos).getBlock();
            if (block instanceof AbstractBlockHut<?> abstractBlockHut)
            {
                if (abstractBlockHut.canRightClickWithoutPermissions())
                {
                    return InteractionResult.PASS;
                }
                final IColony colony = IColonyManager.getInstance().getIColony(world, pos);
                if (colony != null && !colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
                {
                    return InteractionResult.FAIL;
                }

                return InteractionResult.PASS;
            }
        }

        // Port note: NeoForge's IBlockExtension#isBed is gone; 26.2 answers the same question with the block type.
        if (world.getBlockState(pos).getBlock() instanceof BedBlock)
        {
            final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, bedBlockPos);
            //Checks to see if player tries to sleep in a bed belonging to a Citizen, cancels the event, and Notifies Player that bed is occupied
            if (colony != null && world.getBlockState(pos).hasProperty(BedBlock.PART))
            {
                final List<ICitizenData> citizenList = colony.getCitizenManager().getCitizens();
                final BlockState potentialBed = world.getBlockState(pos);
                if (potentialBed.getBlock() instanceof BedBlock && potentialBed.getValue(BedBlock.PART) == BedPart.FOOT)
                {
                    bedBlockPos = bedBlockPos.relative(world.getBlockState(pos).getValue(BedBlock.FACING));
                }
                //Searches through the nearest Colony's Citizen and sees if the bed belongs to a Citizen, and if the Citizen is asleep

                for (final ICitizenData citizen : citizenList)
                {
                    if (citizen.getBedPos().equals(bedBlockPos) && citizen.isAsleep())
                    {
                        MessageUtils.format(BASE_BED_OCCUPIED).sendTo(player);
                        return InteractionResult.FAIL;
                    }
                }
            }
        }

        final InteractionResult hutPlacement = handleEventCancellation(world, player, itemStack, pos, hitResult.getDirection());
        if (hutPlacement != InteractionResult.PASS)
        {
            return hutPlacement;
        }

        if (itemStack.getItem() instanceof BlockItem)
        {
            final Block block = ((BlockItem) itemStack.getItem()).getBlock();
            if (block instanceof AbstractBlockHut && !(block instanceof IRSComponentBlock))
            {
                final IColony colony = IColonyManager.getInstance().getIColony(world, pos);
                if (colony != null && !colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
                {
                    return InteractionResult.FAIL;
                }

                if (!(player.isCreative() && player.isShiftKeyDown()))
                {
                    if (!itemStack.isEmpty() && !world.isClientSide())
                    {
                        new OpenSuggestionWindowMessage(
                            block.defaultBlockState().setValue(AbstractBlockHut.FACING, player.getDirection()),
                            pos.relative(hitResult.getDirection()),
                            itemStack).sendToPlayer((ServerPlayer) player);
                    }
                    return InteractionResult.FAIL;
                }
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * Called when the player makes a right click.
     * <p>
     * Port note: NeoForge's {@code Item#doesSneakBypassUse} extension is gone in 26.2 and vanilla has no
     * counterpart, so a sneaking player with anything in the main hand now always blocks the hut interaction.
     *
     * @param player the player doing it.
     * @param world  the world he is clicking in.
     * @param pos    the position.
     * @return if should be executed.
     */
    private static boolean playerRightClickInteract(@NotNull final Player player, final Level world, final BlockPos pos)
    {
        return !player.isShiftKeyDown() || player.getMainHandItem().isEmpty();
    }

    /**
     * Handles the refusal of a hut placement interaction.
     * <p>
     * Port note (contract C5), and the reason hut blocks could not be placed at all: on NeoForge the client
     * half of this check cancelled {@code PlayerInteractEvent.RightClickBlock}, which suppressed the client's
     * own {@code useOn} but left {@code ServerboundUseItemOnPacket} on the wire -- vanilla
     * {@code MultiPlayerGameMode#useItemOn} builds that packet in the {@code startPrediction} lambda that wraps
     * {@code performUseItemOn}, so cancelling the inner call never stops the send. Fabric's
     * {@link UseBlockCallback} is injected at the <em>head</em> of {@code useItemOn}, and its client mixin only
     * runs {@code startPrediction} when the returned result {@code consumesAction()}; a {@link
     * InteractionResult#FAIL} therefore cancels the whole method and the packet is never sent. The server then
     * never learns about the click, never runs {@link #onBlockHutPlaced} and never sends
     * {@code OpenSuggestionWindowMessage} -- which is the only way a hut block ever gets placed -- so the click
     * did nothing whatsoever.
     * <p>
     * {@link InteractionResult#CONSUME} is the result that reproduces the NeoForge behaviour: it consumes the
     * click (no local placement, no fall-through to item use, no arm swing) while still being a
     * {@code Success}, so the mixin sends the packet and the server side of this same handler runs.
     *
     * @param world  the level.
     * @param player the player causing it.
     * @param stack  the held stack.
     * @param pos    the clicked position.
     * @param face   the clicked face.
     * @return PASS to keep going through the handler, anything else to return from it right away.
     */
    private static InteractionResult handleEventCancellation(
      @NotNull final Level world,
      @NotNull final Player player,
      @NotNull final ItemStack stack,
      final BlockPos pos,
      final Direction face)
    {
        final Block heldBlock = Block.byItem(stack.getItem());
        if (heldBlock instanceof AbstractBlockHut || heldBlock instanceof BlockScarecrow)
        {
            if (world.isClientSide())
            {
                return InteractionResult.CONSUME;
            }
            if (!onBlockHutPlaced(world, player, heldBlock, pos.relative(face)))
            {
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * Called when a player tries to place a AbstractBlockHut. Returns true if successful and false to cancel the block placement.
     *
     * @param world  The world the player is in
     * @param player The player
     * @param block  The block type the player is placing
     * @param pos    The location of the block
     * @return false to cancel the event
     */
    public static boolean onBlockHutPlaced(@NotNull final Level world, @NotNull final Player player, final Block block, final BlockPos pos)
    {
        if (!MineColonies.getConfig().getServer().allowOtherDimColonies.get() && !WorldUtil.isOverworldType(world))
        {
            MessageUtils.format(CANT_PLACE_COLONY_IN_OTHER_DIM).sendTo(player);
            return false;
        }

        return onBlockHutPlaced(world, player, pos, block);
    }

    private static boolean onBlockHutPlaced(final Level world, @NotNull final Player player, final BlockPos pos, final Block block)
    {
        final IColony colony = IColonyManager.getInstance().getIColony(world, pos);

        if (colony == null)
        {
            if (block instanceof BlockHutTownHall)
            {
                return true;
            }

            //  Not in a colony
            if (IColonyManager.getInstance().getIColonyByOwner(world, player) == null)
            {
                MessageUtils.format(MESSAGE_WARNING_TOWN_HALL_NOT_PRESENT).sendTo(player);
            }
            else
            {
                MessageUtils.format(MESSAGE_WARNING_TOWN_HALL_TOO_FAR_AWAY).sendTo(player);
            }

            return player.isCreative();
        }
        else if (!colony.getPermissions().hasPermission(player, Action.PLACE_HUTS))
        {
            //  No permission to place hut in colony
            MessageUtils.format(PERMISSION_OPEN_HUT, colony.getName()).sendTo(player);
            return false;
        }
        else
        {
            return player.isCreative() || colony.getServerBuildingManager().canPlaceAt(block, pos, player);
        }
    }

    /**
     * Client-side world-load extras: the seasonal render flags.
     * <p>
     * Port note: the old {@code LevelEvent.Load} handler ran on both sides and branched on
     * {@code isClientSide()}. The server half is wired to {@link ServerLevelEvents#LOAD} from
     * {@link #register()}; this half is called from the client initializer, once, instead of per level load --
     * the dates it reads do not change while the game is running.
     */
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public static void registerClientHolidayFeatures()
    {
        // Global events
        // Halloween ghost mode
        if (MineColonies.getConfig().getClient().holidayFeatures.get() &&
              (LocalDateTime.now().getDayOfMonth() == 31 && LocalDateTime.now().getMonth() == Month.OCTOBER
                 || LocalDateTime.now().getDayOfMonth() == 1 && LocalDateTime.now().getMonth() == Month.NOVEMBER
                 || LocalDateTime.now().getDayOfMonth() == 2 && LocalDateTime.now().getMonth() == Month.NOVEMBER))
        {
            // Re-enable for ghostly halloween
            RenderBipedCitizen.isItGhostTime = false;
        }
        // April 1st mode
        if (MineColonies.getConfig().getClient().holidayFeatures.get() &&
            LocalDateTime.now().getDayOfMonth() == 1 && LocalDateTime.now().getMonth() == Month.APRIL)
        {
            CitizenModel.isItApril1st = true;
        }
    }

    /**
     * Gets called when farmland is trampled.
     * <p>
     * <b>NEVER CALLED (degradation ladder step 2).</b> Was {@code BlockEvent.FarmlandTrampleEvent}; neither
     * Fabric API nor vanilla 26.2 exposes a trample hook. Consequence: the {@code SOFT_SHOES} research no
     * longer stops farmers from trampling crops.
     *
     * @param entity the trampling entity.
     * @return whether the trample should be prevented.
     */
    public static boolean shouldPreventCropTrample(final Entity entity)
    {
        return entity instanceof AbstractEntityCitizen
                 && ((AbstractEntityCitizen) entity).getCitizenJobHandler().getColonyJob() instanceof JobFarmer
                 && ((AbstractEntityCitizen) entity).getCitizenColonyHandler().getColonyOrRegister()
                      .getResearchManager().getResearchEffects().getEffectStrength(SOFT_SHOES) > 0;
    }

    /**
     * Gets called when a Hoglin, Pig, Piglin, Villager, or ZombieVillager gets converted to something else.
     * <p>
     * <b>NEVER CALLED (degradation ladder step 2).</b> Was {@code LivingConversionEvent.Pre}, which NeoForge
     * fires <em>before</em> the conversion and which could be cancelled; Fabric's
     * {@code ServerLivingEntityEvents.MOB_CONVERSION} only reports a conversion that already happened, so
     * there is no way to replace a cured zombie villager with a colony visitor any more. The body is kept for
     * a future mixin on {@code ZombieVillager#finishConversion}. Consequence: curing a zombie villager inside
     * a colony with a tavern no longer recruits a visitor.
     *
     * @param entity the entity being converted.
     * @param outcome what it is converting into.
     * @return whether the conversion was replaced by a visitor recruitment.
     */
    public static boolean onEntityConverted(@NotNull final LivingEntity entity, final EntityType<?> outcome)
    {
        if (entity instanceof ZombieVillager && outcome == EntityTypes.VILLAGER)
        {
            final Level world = entity.level();
            final IColony colony = IColonyManager.getInstance().getIColony(world, entity.blockPosition());
            if (colony != null && colony.getCommonBuildingManager().hasBuilding(ModBuildings.tavern.get().getRegistryName(), 1, false))
            {
                final BlockPos tavernPos = colony.getServerBuildingManager().getRandomBuilding(b -> !b.getModulesByType(TavernBuildingModule.class).isEmpty());
                if (tavernPos == null)
                {
                    return false;
                }

                final IBuilding tavern = colony.getServerBuildingManager().getBuilding(tavernPos);
                final TavernBuildingModule module = tavern.getModule(BuildingModules.TAVERN_VISITOR);
                final IVisitorData visitorData = module.spawnVisitor();
                if (visitorData == null)
                {
                    return false;
                }
                visitorData.triggerInteraction(new RecruitmentInteraction(Component.translatable(
                    "com.minecolonies.coremod.gui.chat.recruitstorycured", visitorData.getName().split(" ")[0]), ChatPriority.IMPORTANT));

                visitorData.getEntity().ifPresent(e -> e.setPos(entity.getX(), entity.getY(), entity.getZ()));
                if (!entity.isSilent())
                {
                    world.levelEvent(null, 1027, entity.blockPosition(), 0);
                }

                entity.remove(Entity.RemovalReason.DISCARDED);
                return true;
            }
        }
        return false;
    }

    /**
     * Server tick: keeps the AI slowness factor in sync with the measured tick time, and drives the
     * per-player chunk-entry check that used to live on {@code PlayerTickEvent.Pre}.
     *
     * @param server the server.
     */
    private static void onServerTick(final MinecraftServer server)
    {
        final double lastTickMs = server.getTickTimesNanos()[server.getTickCount() % 100] * 1.0E-6D;
        if (lastTickMs > 50)
        {
            TickRateStateMachine.slownessFactor = Mth.clamp(lastTickMs / 50, 1.0D, 5.0D);
        }
        else
        {
            TickRateStateMachine.slownessFactor = 1.0D;
        }

        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            onEnteringChunk(player);
        }
    }
}
