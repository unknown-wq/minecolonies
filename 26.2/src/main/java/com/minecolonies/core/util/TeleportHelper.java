package com.minecolonies.core.util;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.EntityUtils;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_ID_NOT_FOUND;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_TELEPORT_SUCCESS;

/**
 * Helper class for server-side teleporting.
 */
public final class TeleportHelper
{
    private static final double MIDDLE_BLOCK_OFFSET = 0.5D;

    /**
     * Private constructor to hide the implicit public one.
     */
    private TeleportHelper()
    {
        // Intentionally left empty.
    }

    /**
     * Move a citizen to a position, taking his mount with him.
     * <p>
     * The teleport is a dismount -- {@code stopRiding} below is what makes the destination safe to place the citizen
     * on -- and for every citizen but a cavalryman that is the end of it. A cavalryman leaves a horse behind, and the
     * horse is not merely scenery: his AI answers {@code FIND_MOUNT} the moment he is on foot and walks him back to
     * wherever the horse was left, which is by construction the place he was just moved out of. The unit therefore
     * spends its time walking backwards to a horse that is falling further behind on every teleport, which is what a
     * follow order looks like when half the troop is on foot.
     * <p>
     * So the mount comes along. Only a {@link CavalryHorseEntity} the citizen is actually steering: a player's horse
     * a citizen happens to be sitting on, a boat, or a {@code SittingEntity} nap seat are all somebody else's, and
     * dragging them across the world would be a surprise rather than a fix.
     *
     * @param citizen  the citizen to move.
     * @param world    the level.
     * @param location where to move him.
     * @return true if he was moved.
     */
    public static boolean teleportCitizen(final AbstractEntityCitizen citizen, final Level world, final BlockPos location)
    {
        if (citizen == null || world == null || world.isClientSide())
        {
            return false;
        }

        final BlockPos spawnPoint = EntityUtils.getSpawnPoint(world, location);
        if (spawnPoint == null)
        {
            return false;
        }

        if (citizen.getCitizenSleepHandler().isAsleep())
        {
            citizen.getCitizenSleepHandler().onWakeUp();
        }

        final CavalryHorseEntity mount = citizen.getVehicle() instanceof final CavalryHorseEntity horse
                                           && horse.getControllingPassenger() == citizen ? horse : null;

        citizen.getNavigation().stop();
        citizen.stopRiding();
        citizen.snapTo(
          spawnPoint.getX() + MIDDLE_BLOCK_OFFSET,
          spawnPoint.getY(),
          spawnPoint.getZ() + MIDDLE_BLOCK_OFFSET,
          citizen.getRotationYaw(),
          citizen.getRotationPitch());

        remount(citizen, mount);
        return true;
    }

    /**
     * Bring a mount to a citizen who has just been moved, and put him back in the saddle.
     * <p>
     * Nothing is created and nothing is destroyed: the same horse entity is moved and re-ridden, so the colony's
     * mount count, its animal data and its home stable are all untouched. The rope, if there ever was one, is already
     * off -- a horse cannot be hitched while it is being ridden, and remounting runs
     * {@code CavalryHorseEntity#addPassenger}, which is the mod's own untie path -- so this cannot leave a knot
     * standing at the far end of the teleport.
     *
     * @param citizen the citizen who has been moved.
     * @param mount   the mount to bring, or null when there was none.
     */
    private static void remount(@NotNull final AbstractEntityCitizen citizen, @Nullable final CavalryHorseEntity mount)
    {
        if (mount == null || !mount.isAlive() || mount.isRemoved() || mount.level() != citizen.level())
        {
            return;
        }

        mount.getNavigation().stop();
        mount.snapTo(citizen.getX(), citizen.getY(), citizen.getZ(), citizen.getRotationYaw(), 0.0F);
        citizen.startRiding(mount, true, true);
    }

    /**
     * Teleports the player to his home colony.
     *
     * @param player the player to teleport home.
     */
    public static void homeTeleport(@NotNull final ServerPlayer player)
    {
        final IColony colony = IColonyManager.getInstance().getIColonyByOwner(player.level(), player);
        if (colony == null)
        {
            MessageUtils.format(COMMAND_COLONY_ID_NOT_FOUND).sendTo(player);
            return;
        }

        colonyTeleport(player, colony);
    }

    /**
     * Teleports the player to the nearest safe surface location above their current location
     */
    public static void surfaceTeleport(@NotNull final ServerPlayer player)
    {
        BlockPos position = BlockPos.containing(player.getX(), 250, player.getZ()); //start at current position

        position = BlockPosUtil.findLand(position, player.level());

        ChunkPos chunkpos = ChunkPos.containing(position);
        player.level().getChunkSource().addTicketWithRadius(TicketType.PORTAL, chunkpos, 1);
        player.stopRiding();
        if (player.isSleeping())
        {
            player.stopSleepInBed(true, true);
        }

        player.teleportTo(player.level(), position.getX(), position.getY() + 2.0, position.getZ(), java.util.Set.of(), player.getYRot(), player.getXRot(), false);
    }

    /**
     * Teleports the player to his home colony.
     *
     * @param dimension the dimension the colony is in.
     * @param player    the player to teleport.
     * @param id        the colony id.
     */
    public static void colonyTeleportByID(@NotNull final ServerPlayer player, final int id, final ResourceKey<Level> dimension)
    {
        final IColony colony = IColonyManager.getInstance().getColonyByDimension(id, dimension);
        if (colony == null)
        {
            MessageUtils.format(COMMAND_COLONY_ID_NOT_FOUND).sendTo(player);
            return;
        }

        colonyTeleport(player, colony);
    }

    /**
     * Teleports the player to the given colony.
     *
     * @param player the player to teleport.
     * @param colony the colony to teleport to.
     */
    public static void colonyTeleport(@NotNull final ServerPlayer player, @NotNull final IColony colony)
    {
        colonyTeleport(player, colony, null);
    }

    /**
     * Teleports the player to the given colony.
     *
     * @param player the player to teleport.
     * @param colony the colony to teleport to.
     * @param pos the preferred position to teleport to.
     */
    public static void colonyTeleport(@NotNull final ServerPlayer player, @NotNull final IColony colony, final BlockPos pos)
    {
        BlockPos position = pos;
        if (pos == null)
        {
            if (colony.getServerBuildingManager().getTownHall() != null)
            {
                position = colony.getServerBuildingManager().getTownHall().getPosition();
            }
            else
            {
                position = colony.getCenter();
            }
        }

        final ServerLevel world = player.level().getServer().getLevel(colony.getDimension());

        position = BlockPosUtil.findAround(world,
          position,
          5,
          5,
          (predWorld, predPos) -> predWorld.getBlockState(predPos).isAir() && predWorld.getBlockState(predPos.above()).isAir());

        if (position == null)
        {
            return;
        }

        ChunkPos chunkpos = ChunkPos.containing(position);
        world.getChunkSource().addTicketWithRadius(TicketType.PORTAL, chunkpos, 1);
        player.stopRiding();
        if (player.isSleeping())
        {
            player.stopSleepInBed(true, true);
        }

        player.teleportTo(world, position.getX(), position.getY(), position.getZ(), java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        MessageUtils.format(COMMAND_TELEPORT_SUCCESS, colony.getName()).sendTo(player);
    }
}
