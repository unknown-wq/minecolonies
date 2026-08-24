package com.minecolonies.core.colony.permissions;

// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.items.ItemScanTool;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.colony.permissions.Explosions;
import com.minecolonies.api.colony.permissions.PermissionEvent;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.api.util.EntityUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.blocks.BlockDecorationController;
import com.minecolonies.core.blocks.huts.BlockHutTownHall;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.entity.citizen.EntityCitizen;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.ItemEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.minecolonies.api.util.constant.Constants.TICKS_SECOND;
import static com.minecolonies.api.util.constant.TranslationConstants.PERMISSION_DENIED;

/**
 * This class handles all permission checks on events and cancels them if needed.
 * <p>
 * PORT(26.2): NeoForge let every colony register its own handler instance on the global event bus and
 * cancel a rich set of events. Fabric has no event bus and a much smaller callback surface, so the shape
 * changed: the Fabric callbacks are hooked exactly once ({@link #hookCallbacks()}, lazily from the first
 * {@link #register()}), and every live handler is asked in turn.
 * <p>
 * Events with no Fabric counterpart are gone; the protections they carried are, in full:
 * <ul>
 *     <li>{@code BlockEvent.EntityPlaceEvent} — PLACE_BLOCKS / PLACE_HUTS. <b>Restored</b> on
 *         {@code ItemEvents.USE_ON} ({@link #onBlockPlace}), which wraps {@code Item#useOn} and so sits
 *         directly in front of {@code BlockItem#place}. Not reached: dispensers and falling blocks,
 *         which never call {@code ItemStack#useOn}.</li>
 *     <li>{@code ExplosionEvent.Start} / {@code .Detonate} — EXPLODE, and the
 *         {@code turnOffExplosionsInColonies} config. <b>Partly restored</b>, and not from here:
 *         {@code SimplePlanesBlastGuard} applies both of them to the blasts Simple Planes produces, through a
 *         guard seam in that mod. Vanilla explosions — creepers, TNT, beds, end crystals, a payload bomb
 *         dropped from a plane — are still unprotected, because reaching those generically needs a mixin on
 *         {@code ServerLevel#explode} and Fabric API ships no explosion callback. See
 *         {@code 26.2/BLAST-PROTECTION.md}. The <b>entity</b> half of the policy is restored for every
 *         explosion in the game — see {@link #allowExplosionDamage}.</li>
 *     <li>{@code ItemTossEvent} — TOSS_ITEM</li>
 *     <li>{@code ItemEntityPickupEvent.Pre} — PICKUP_ITEM</li>
 *     <li>{@code VanillaGameEvent} FLUID_PICKUP — FILL_BUCKET. <b>Restored</b> on
 *         {@code UseItemCallback} ({@link #onItemUse}), which is where {@code BucketItem#use} lands.</li>
 *     <li>{@code ArrowLooseEvent} — SHOOT_ARROW. <b>Restored</b> on the same callback, on the draw rather
 *         than on the release.</li>
 * </ul>
 */
public class ColonyPermissionEventHandler
{
    /**
     * Every handler that is currently registered. Fabric callbacks are global, so the dispatch is too.
     */
    private static final List<ColonyPermissionEventHandler> ACTIVE = new CopyOnWriteArrayList<>();

    /**
     * Whether {@link #hookCallbacks()} has already run. Fabric callbacks can only be added, never removed.
     */
    private static boolean hooked = false;

    /**
     * The colony involved in this permission-check event
     */
    private final Colony colony;

    /**
     * The last time the player was notified about not having permission.
     */
    private final Map<UUID, Long> lastPlayerNotificationTick = new HashMap<>();

    /**
     * Number of attempts within a notif tick.
     */
    private final Object2IntMap<UUID> playerAttempts = new Object2IntOpenHashMap<>();

    /**
     * Create this EventHandler.
     *
     * @param colony the colony to check on.
     */
    public ColonyPermissionEventHandler(final Colony colony)
    {
        this.colony = colony;
    }

    /**
     * Start answering interaction callbacks for this colony. Replaces {@code NeoForge.EVENT_BUS.register(this)}.
     */
    public void register()
    {
        hookCallbacks();
        if (!ACTIVE.contains(this))
        {
            ACTIVE.add(this);
        }
    }

    /**
     * Stop answering interaction callbacks. Replaces {@code NeoForge.EVENT_BUS.unregister(this)}.
     */
    public void unregister()
    {
        ACTIVE.remove(this);
    }

    /**
     * Install the Fabric callbacks. Idempotent; Fabric events cannot be unsubscribed from, so the
     * per-colony part of the dispatch is {@link #ACTIVE}, not the subscription itself.
     */
    public static synchronized void hookCallbacks()
    {
        if (hooked)
        {
            return;
        }
        hooked = true;

        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            for (final ColonyPermissionEventHandler handler : ACTIVE)
            {
                if (!handler.onBlockBreak(player, pos, state))
                {
                    return false;
                }
            }
            return true;
        });

        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> dispatchBlockInteract(player, level, hand, pos, false));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> dispatchBlockInteract(player, level, hand, hit.getBlockPos(), true));

        // PORT(26.2): the stand-in for BlockEvent.EntityPlaceEvent. ItemEvents.USE_ON wraps
        // Item#useOn from inside ItemStack#useOn, which is the one and only door to BlockItem#place --
        // and it is only walked through *after* the clicked block's own useItemOn/useWithoutItem had
        // its chance (ServerPlayerGameMode#useItemOn). Checking here rather than on UseBlockCallback
        // is what keeps "right-click a lever while holding a block" from being read as a placement.
        // It also fires for any other caller of ItemStack#useOn, fake players included.
        ItemEvents.USE_ON.register(context -> {
            final Player player = context.getPlayer();
            if (player == null || context.getLevel().isClientSide())
            {
                return null;
            }
            for (final ColonyPermissionEventHandler handler : ACTIVE)
            {
                if (handler.onBlockPlace(player, context))
                {
                    return InteractionResult.FAIL;
                }
            }
            return null;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            for (final ColonyPermissionEventHandler handler : ACTIVE)
            {
                if (handler.onItemUse(player, level, hand))
                {
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            for (final ColonyPermissionEventHandler handler : ACTIVE)
            {
                if (handler.onEntityInteract(player, level, entity))
                {
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            for (final ColonyPermissionEventHandler handler : ACTIVE)
            {
                if (handler.onAttackEntity(player, entity))
                {
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            for (final ColonyPermissionEventHandler handler : ACTIVE)
            {
                if (!handler.allowDamage(entity, source))
                {
                    return false;
                }
            }
            return true;
        });
    }

    private static InteractionResult dispatchBlockInteract(
      final Player player, final Level level, final InteractionHand hand, final BlockPos pos, final boolean rightClick)
    {
        for (final ColonyPermissionEventHandler handler : ACTIVE)
        {
            if (handler.onBlockInteract(player, level, hand, pos, rightClick))
            {
                return InteractionResult.FAIL;
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * Record a denial in the colony's town hall and tell the player off. Replaces the bookkeeping half of
     * NeoForge's {@code cancelEvent}; the cancelling half is the caller's return value now.
     *
     * @param entity the player whose action was denied, may be null
     * @param colony the colony where the action took place
     * @param action the action which was denied
     * @param pos    the location of the action which was denied
     */
    private void denyAction(@Nullable final Entity entity, final Colony colony, final Action action, final BlockPos pos)
    {
        if (entity == null)
        {
            return;
        }

        // Server only, and this is not a tidiness rule - running it client side leaves a levitation icon that
        // nothing can remove. Unlike NeoForge's events, most of the Fabric callbacks this class hooks
        // (UseItemCallback, UseBlockCallback, AttackBlockCallback, UseEntityCallback, AttackEntityCallback) fire
        // on BOTH sides, and in single player the client thread walks the same server side colonies. The
        // punishment below then lands on the client's own copy of the player, which the server never hears about.
        // LivingEntity#tickEffects only *removes* an expired effect in its ServerLevel branch; the client branch
        // calls tickClient(), which counts the display down and stops there. So the icon reaches 00:00 and stays
        // there forever: /effect clear and milk act on the server's list, which never held it, so no removal
        // packet is ever sent, and only a relog rebuilds the entity. The denial itself still happens on both
        // sides - the callers return FAIL either way, which is what stops the client mispredicting the action -
        // only the bookkeeping and the punishment are server side. The town hall write below wants that anyway:
        // getServerBuildingManager is not a client side object.
        if (entity.level().isClientSide())
        {
            return;
        }

        if (colony.getCommonBuildingManager().hasTownHall())
        {
            colony.getServerBuildingManager().getTownHall().addPermissionEvent(new PermissionEvent(entity.getUUID(), entity.getName().getString(), action, pos));
        }

        if (entity instanceof FakePlayer || (entity instanceof ServerPlayer serverPlayer && serverPlayer.connection == null))
        {
            return;
        }

        final long worldTime = entity.level().getGameTime();
        if (!lastPlayerNotificationTick.containsKey(entity.getUUID())
              || lastPlayerNotificationTick.get(entity.getUUID()) + (TICKS_SECOND * 10) < worldTime)
        {
            MessageUtils.format(PERMISSION_DENIED).sendTo((Player) entity);
            lastPlayerNotificationTick.put(entity.getUUID(), worldTime);
            playerAttempts.put(entity.getUUID(), 0);
        }
        else
        {
            if (playerAttempts.compute(entity.getUUID(), (uuid, count) -> count == null ? 1 : count + 1) > 10)
            {
                if (entity instanceof LivingEntity living)
                {
                    playerAttempts.put(entity.getUUID(), 0);
                    living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, TICKS_SECOND * 10));
                }
            }
        }
    }

    /**
     * Block break handler; replaces {@code BlockEvent.BreakEvent}.
     *
     * @return true when the break may go ahead.
     */
    private boolean onBlockBreak(final Player player, final BlockPos pos, final BlockState state)
    {
        if (!(player.level() instanceof final ServerLevel world))
        {
            return true;
        }

        if (state.getBlock() instanceof AbstractBlockHut)
        {
            @Nullable final IBuilding building = IColonyManager.getInstance().getBuilding(world, pos);
            if (building == null)
            {
                return true;
            }

            if (!MineColonies.getConfig().getServer().enableColonyProtection.get())
            {
                building.destroy();
                return true;
            }

            if (state.getBlock() == ModBlocks.blockHutTownHall && !((BlockHutTownHall) state.getBlock()).getValidBreak() && !player.isCreative())
            {
                denyAction(player, colony, Action.BREAK_HUTS, pos);
                return false;
            }

            if (!building.getColony().getPermissions().hasPermission(player, Action.BREAK_HUTS)
                  && isActionDenied(Action.BREAK_HUTS, player, world, pos))
            {
                return false;
            }

            building.destroy();

            if (MineColonies.getConfig().getServer().pvp_mode.get() && state.getBlock() == ModBlocks.blockHutTownHall)
            {
                IColonyManager.getInstance().deleteColonyByWorld(building.getColony().getID(), false, world);
            }
            return true;
        }
        else if (state.getBlock() instanceof BlockDecorationController)
        {
            if (isActionDenied(Action.BREAK_HUTS, player, world, pos))
            {
                return false;
            }
            colony.getServerBuildingManager().removeLeisureSite(pos);
            return true;
        }

        return !isActionDenied(Action.BREAK_BLOCKS, player, player.level(), pos);
    }

    /**
     * Block interaction handler; replaces the {@code PlayerInteractEvent.LeftClickBlock} /
     * {@code .RightClickBlock} pair.
     *
     * @return true when the interaction must be blocked.
     */
    private boolean onBlockInteract(final Player player, final Level level, final InteractionHand hand, final BlockPos pos, final boolean rightClick)
    {
        if (!colony.isCoordInColony(level, pos))
        {
            return false;
        }

        final BlockState state = level.getBlockState(pos);
        final Block block = state.getBlock();

        // Huts
        if (rightClick && block instanceof AbstractBlockHut && !colony.getPermissions().hasPermission(player, Action.ACCESS_HUTS))
        {
            denyAction(player, colony, Action.ACCESS_HUTS, pos);
            return true;
        }

        final Permissions perms = colony.getPermissions();

        if (isFreeToInteractWith(block, pos) && !perms.getRank(player).isHostile())
        {
            return false;
        }

        if ((state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES)) && perms.hasPermission(player, Action.ACCESS_TOGGLEABLES))
        {
            return false;
        }

        if (!MineColonies.getConfig().getServer().enableColonyProtection.get())
        {
            return false;
        }

        if (!perms.hasPermission(player, Action.RIGHTCLICK_BLOCK) && !(block instanceof AirBlock))
        {
            return isActionDenied(Action.RIGHTCLICK_BLOCK, player, level, pos);
        }

        final BlockEntity blockEntity = level.getBlockEntity(pos);

        if (isContainer(level, pos, state, blockEntity) && !perms.hasPermission(player, Action.OPEN_CONTAINER))
        {
            denyAction(player, colony, Action.OPEN_CONTAINER, pos);
            return true;
        }

        if (blockEntity != null && !perms.hasPermission(player, Action.RIGHTCLICK_ENTITY))
        {
            return isActionDenied(Action.RIGHTCLICK_ENTITY, player, level, pos);
        }

        final ItemStack stack = player.getItemInHand(hand);
        if (ItemStackUtils.isEmpty(stack) || stack.has(DataComponents.FOOD))
        {
            return false;
        }

        if (stack.getItem() instanceof PotionItem)
        {
            return isActionDenied(Action.THROW_POTION, player, level, pos);
        }

        if (stack.getItem() instanceof ItemScanTool && !perms.hasPermission(player, Action.USE_SCAN_TOOL))
        {
            denyAction(player, colony, Action.USE_SCAN_TOOL, pos);
            return true;
        }

        return false;
    }

    /**
     * Block placement handler; replaces {@code BlockEvent.EntityPlaceEvent}.
     * <p>
     * Only reached once vanilla has decided that the click is a placement and not a use of the clicked
     * block, so holding a stack of cobble does not stop a member from flipping a lever or opening a
     * crafting table.
     *
     * @return true when the placement must be blocked.
     */
    private boolean onBlockPlace(final Player player, final UseOnContext context)
    {
        if (!(context.getItemInHand().getItem() instanceof final BlockItem blockItem))
        {
            return false;
        }

        final Level level = context.getLevel();
        final BlockPos pos = new BlockPlaceContext(context).getClickedPos();
        final Block block = blockItem.getBlock();

        if (isFreeToInteractWith(block, pos) && !colony.getPermissions().getRank(player).isHostile())
        {
            return false;
        }

        return isActionDenied(block instanceof AbstractBlockHut ? Action.PLACE_HUTS : Action.PLACE_BLOCKS, player, level, pos);
    }

    /**
     * Item use handler; replaces {@code PlayerInteractEvent.RightClickItem}.
     *
     * @return true when the use must be blocked.
     */
    private boolean onItemUse(final Player player, final Level level, final InteractionHand hand)
    {
        final BlockPos pos = player.blockPosition();
        if (!colony.isCoordInColony(level, pos) || !MineColonies.getConfig().getServer().enableColonyProtection.get())
        {
            return false;
        }

        final ItemStack stack = player.getItemInHand(hand);
        if (ItemStackUtils.isEmpty(stack) || stack.has(DataComponents.FOOD))
        {
            return false;
        }

        if (stack.getItem() instanceof PotionItem)
        {
            return isActionDenied(Action.THROW_POTION, player, level, pos);
        }

        // was: VanillaGameEvent / GameEvent.FLUID_PICKUP. Only the empty bucket picks a fluid up; a full one
        // is placing, and upstream did not route that through FILL_BUCKET either.
        if (stack.getItem() instanceof final BucketItem bucket && bucket.getContent() == Fluids.EMPTY)
        {
            return isActionDenied(Action.FILL_BUCKET, player, level, pos);
        }

        // was: ArrowLooseEvent. That fired on the release; this fires on the draw, so a denied player cannot
        // nock the arrow at all rather than drawing and having the shot swallowed.
        if (stack.getItem() instanceof ProjectileWeaponItem)
        {
            return isActionDenied(Action.SHOOT_ARROW, player, level, pos);
        }

        if (stack.getItem() instanceof ItemScanTool && !colony.getPermissions().hasPermission(player, Action.USE_SCAN_TOOL))
        {
            denyAction(player, colony, Action.USE_SCAN_TOOL, pos);
            return true;
        }

        return false;
    }

    /**
     * Whether OPEN_CONTAINER applies to this block.
     * <p>
     * PORT(26.2): NeoForge capabilities are gone (C4), and the first pass of this port narrowed the test to
     * {@code blockEntity instanceof Container}. That agrees with upstream for every vanilla chest, barrel,
     * hopper and furnace, and disagrees for a modded inventory that exposes items without implementing the
     * vanilla interface — which was protected on NeoForge and was not here. {@code ItemStorage.SIDED} from
     * fabric-transfer-api-v1 is the counterpart of {@code Capabilities.ItemHandler.BLOCK}, so the six-direction
     * sweep upstream did translates directly.
     *
     * @return true if the block holds items by anyone's definition.
     */
    private static boolean isContainer(
      final Level level, final BlockPos pos, final BlockState state, @Nullable final BlockEntity blockEntity)
    {
        if (blockEntity instanceof Container)
        {
            return true;
        }

        if (ItemStorage.SIDED.find(level, pos, state, blockEntity, null) != null)
        {
            return true;
        }

        for (final Direction direction : Direction.values())
        {
            if (ItemStorage.SIDED.find(level, pos, state, blockEntity, direction) != null)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Check in the config if that block can be interacted with freely.
     *
     * @param block the block to check.
     * @param pos   the position of the interaction
     * @return true if so.
     */
    private boolean isFreeToInteractWith(@Nullable final Block block, final BlockPos pos)
    {
        return (block != null && (colony.getFreeBlocks().contains(block) || block.defaultBlockState().is(ModTags.colonyProtectionException))) || colony.getFreePositions().contains(pos);
    }

    /**
     * Entity interaction handler; replaces {@code PlayerInteractEvent.EntityInteract} and
     * {@code .EntityInteractSpecific}.
     *
     * @return true when the interaction must be blocked.
     */
    private boolean onEntityInteract(final Player player, final Level level, final Entity target)
    {
        final BlockPos pos = target.blockPosition();
        if (isFreeToInteractWith(null, pos) && !colony.getPermissions().getRank(player).isHostile())
        {
            return false;
        }

        if (target.getType().builtInRegistryHolder().is(ModTags.freeToInteractWith))
        {
            return false;
        }

        return isActionDenied(Action.RIGHTCLICK_ENTITY, player, level, pos);
    }

    /**
     * Check if the action should be denied for a given player, and record it if so.
     *
     * @param action   the action that was performed on the position
     * @param playerIn the player.
     * @param world    the world.
     * @param pos      the position. Can be null if no target was provided.
     * @return true if denied.
     */
    private boolean isActionDenied(
      final Action action, @NotNull final Player playerIn, @NotNull final Level world, @Nullable final BlockPos pos)
    {
        @NotNull final Player player = EntityUtils.getPlayerOfFakePlayer(playerIn, world);

        BlockPos positionToCheck = pos;
        if (null == positionToCheck)
        {
            positionToCheck = player.blockPosition();
        }
        if (MineColonies.getConfig().getServer().enableColonyProtection.get()
              && colony.isCoordInColony(player.level(), positionToCheck)
              && !colony.getPermissions().hasPermission(player, action))
        {
            if (MineColonies.getConfig().getServer().pvp_mode.get() && !world.isClientSide() && colony.isValidAttackingPlayer(playerIn))
            {
                return false;
            }

            denyAction(player, colony, action, positionToCheck);
            return true;
        }
        return false;
    }

    /**
     * Damage veto; replaces {@code LivingDamageEvent.Pre}.
     * <p>
     * PORT(26.2): the NeoForge handler zeroed the damage, Fabric's ALLOW_DAMAGE can only veto it outright.
     * Same visible effect for this case (a guard of your own colony cannot hurt you during a raid).
     *
     * @return true when the damage may go ahead.
     */
    private boolean allowDamage(final LivingEntity entity, final DamageSource source)
    {
        if (!allowExplosionDamage(entity, source))
        {
            return false;
        }

        return !(entity instanceof ServerPlayer
                   && source.getEntity() instanceof EntityCitizen citizen
                   && citizen.getCitizenColonyHandler().getColonyId() == colony.getID()
                   && colony.getRaiderManager().isRaided()
                   && !colony.getPermissions().getRank((Player) entity).isHostile());
    }

    /**
     * The entity half of {@code turnOffExplosionsInColonies}; replaces the entity-list filtering that
     * {@code ExplosionEvent.Detonate} did on 1.21.1 (see {@code 1.21.1/.../ColonyPermissionEventHandler:301-338}).
     * <p>
     * The block half of that filter is not reachable on this loader — {@code ServerExplosion#interactsWithBlocks}
     * is private and Fabric API ships no explosion event — but which entities the blast is allowed to hurt is a
     * plain question about a damage source, and {@code ServerLivingEntityEvents.ALLOW_DAMAGE} is already
     * subscribed. Faithful to upstream in both directions:
     * <ul>
     *     <li>{@code DAMAGE_ENTITIES} (the shipped default) and {@code DAMAGE_EVERYTHING} — abstain. Upstream
     *         protected no entity under either, so this is inert unless a server sets the config stricter.</li>
     *     <li>{@code DAMAGE_PLAYERS} — protect everything in the colony that is neither a player nor an
     *         {@link Enemy}: citizens, livestock, pets.</li>
     *     <li>{@code DAMAGE_NOTHING} — protect everything in the colony that is not a player, hostiles
     *         included.</li>
     * </ul>
     * Note that <b>players are never protected here</b>, and were not upstream either: the Detonate handler
     * filtered {@code !(entity instanceof ServerPlayer)} under both strict policies. Upstream's cover for a
     * player was {@code ExplosionEvent.Start}, which cancelled an explosion whose <em>centre</em> was in the
     * colony, and that half needs the mixin this port does not have.
     * <p>
     * Two deviations, both from the shape of the Fabric callback rather than from a decision:
     * {@code ALLOW_DAMAGE} only fires for a {@link LivingEntity}, so dropped items, boats and minecarts inside
     * a colony are still destroyed where upstream's entity list covered them; and {@code Action.EXPLODE} is not
     * consulted, exactly as upstream's Detonate handler did not consult it.
     *
     * @return true when the damage may go ahead.
     */
    private boolean allowExplosionDamage(final LivingEntity entity, final DamageSource source)
    {
        if (entity instanceof ServerPlayer || !source.is(DamageTypeTags.IS_EXPLOSION))
        {
            return true;
        }

        if (!MineColonies.getConfig().getServer().enableColonyProtection.get() || !colony.isBlastProtection())
        {
            return true;
        }

        final Explosions policy = MineColonies.getConfig().getServer().turnOffExplosionsInColonies.get();
        if (policy == Explosions.DAMAGE_EVERYTHING || policy == Explosions.DAMAGE_ENTITIES)
        {
            return true;
        }

        if (policy == Explosions.DAMAGE_PLAYERS && entity instanceof Enemy)
        {
            return true;
        }

        return !colony.isCoordInColony(entity.level(), entity.blockPosition());
    }

    /**
     * Attack handler; replaces {@code AttackEntityEvent}.
     *
     * @return true when the attack must be blocked.
     */
    private boolean onAttackEntity(final Player attacker, final Entity target)
    {
        if (target instanceof Monster)
        {
            return false;
        }

        @NotNull final Player player = EntityUtils.getPlayerOfFakePlayer(attacker, attacker.level());

        if (MineColonies.getConfig().getServer().enableColonyProtection.get()
              && colony.isCoordInColony(player.level(), player.blockPosition()))
        {
            final Permissions perms = colony.getPermissions();
            if (target instanceof EntityCitizen)
            {
                final AbstractEntityCitizen citizen = (AbstractEntityCitizen) target;
                if (citizen.getCitizenJobHandler().getColonyJob() instanceof AbstractJobGuard && perms.getRank(attacker).isHostile())
                {
                    return false;
                }

                if (perms.hasPermission(attacker, Action.ATTACK_CITIZEN))
                {
                    return false;
                }

                denyAction(attacker, colony, Action.ATTACK_CITIZEN, target.blockPosition());
                return true;
            }

            if (!(target instanceof Enemy) && !perms.hasPermission(attacker, Action.ATTACK_ENTITY))
            {
                denyAction(attacker, colony, Action.ATTACK_ENTITY, target.blockPosition());
                return true;
            }
        }
        return false;
    }
}
