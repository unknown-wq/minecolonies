package com.minecolonies.core.colony.managers;

import com.minecolonies.api.colony.managers.interfaces.IColonyPackageManager;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.colony.workorders.IWorkManager;
import com.minecolonies.api.util.ColonyUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.ColonyView;
import com.minecolonies.core.colony.permissions.Permissions;
import com.minecolonies.core.colony.territory.HostileTerritorySight;
import com.minecolonies.core.network.messages.PermissionsMessage;
import com.minecolonies.core.network.messages.client.colony.ColonyViewMessage;
import com.minecolonies.core.network.messages.client.colony.ColonyViewWorkOrderMessage;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.minecolonies.api.util.constant.ColonyConstants.UPDATE_STATE_INTERVAL;
import static com.minecolonies.api.util.constant.Constants.TICKS_HOUR;

public class ColonyPackageManager implements IColonyPackageManager
{
    /**
     * List of players close to the colony receiving updates. Populated by chunk entry events
     */
    @NotNull
    private Set<ServerPlayer> closeSubscribers = new HashSet<>();

    /**
     * List of players with global permissions, like receiving important messages from far away. Populated on player login and logoff.
     */
    private Set<ServerPlayer> importantColonyPlayers = new HashSet<>();

    /**
     * New subscribers which havent received a view yet.
     */
    private Set<ServerPlayer> newSubscribers = new HashSet<>();

    /**
     * Variables taking care of updating the views.
     */
    private boolean isDirty = false;

    /**
     * Amount of ticks passed.
     */
    private int ticksPassed = 0;

    /**
     * The last contact in hours.
     */
    private int lastContactInHours = 0;

    /**
     * The colony of the manager.
     */
    private final Colony colony;

    /**
     * Creates the ColonyPackageManager for a colony.
     *
     * @param colony the colony.
     */
    public ColonyPackageManager(final Colony colony)
    {
        this.colony = colony;
    }

    @Override
    public int getLastContactInHours()
    {
        return lastContactInHours;
    }

    @Override
    public void setLastContactInHours(final int lastContactInHours)
    {
        this.lastContactInHours = lastContactInHours;
    }

    @Override
    public Set<ServerPlayer> getCloseSubscribers()
    {
        return closeSubscribers;
    }

    @Override
    public void updateSubscribers()
    {
        final Level world = colony.getWorld();
        // If the world or server is null, don't try to update the closeSubscribers this tick.
        if (world == null || world.getServer() == null)
        {
            return;
        }

        updateClosePlayers();
        updateColonyViews();
    }

    /**
     * Updates currently close players to the colony
     */
    private void updateClosePlayers()
    {
        for (Iterator<ServerPlayer> iterator = closeSubscribers.iterator(); iterator.hasNext(); )
        {
            final ServerPlayer player = iterator.next();

            if (!player.isAlive() || colony.getWorld() != player.level() || !WorldUtil.isChunkLoaded(player.level(), player.chunkPosition().x(), player.chunkPosition().z()))
            {
                iterator.remove();
                continue;
            }

            final LevelChunk chunk = colony.getWorld().getChunk(player.chunkPosition().x(), player.chunkPosition().z());
            if (chunk.isEmpty())
            {
                iterator.remove();
                continue;
            }

            if (ColonyUtils.getOwningColony(chunk) != colony.getID() && !staysInSight(player))
            {
                iterator.remove();
            }
        }
    }

    /**
     * Whether a player who has stepped off this colony's ground should keep receiving its view anyway.
     * <p>
     * Only ever true for a hostile territory, and see {@link HostileTerritorySight} for why: an ordinary colony is
     * something you look at from inside, a territory is something you look at from your own side of the line, and the
     * standing rule drops the subscriber exactly where the border is wanted.
     *
     * @param player the subscriber being reconsidered.
     * @return true to keep them.
     */
    private boolean staysInSight(final ServerPlayer player)
    {
        return colony.isHostile()
                 && colony.getWorld() != null
                 && HostileTerritorySight.isInSight(colony.getWorld(), player.chunkPosition(), colony.getID());
    }

    /**
     * Updates the away timer for the colony.
     */
    @Override
    public void updateAwayTime()
    {
        if (importantColonyPlayers.isEmpty())
        {
            if (ticksPassed >= TICKS_HOUR)
            {
                ticksPassed = 0;
                lastContactInHours++;
                colony.markDirty();
            }
            ticksPassed += UPDATE_STATE_INTERVAL;
        }
        else if (lastContactInHours != 0)
        {
            lastContactInHours = 0;
            ticksPassed = 0;
            colony.markDirty();
        }
    }

    /**
     * Update the closeSubscribers of the colony.
     */
    public void updateColonyViews()
    {
        if (!closeSubscribers.isEmpty() || !newSubscribers.isEmpty())
        {
            //  Send each type of update packet as appropriate:
            //      - To close Subscribers if the data changes
            //      - To New Subscribers even if it hasn't changed

            //ColonyView
            sendColonyViewPackets();

            //Permissions
            sendPermissionsPackets();

            //WorkOrders
            sendWorkOrderPackets();

            colony.getCitizenManager().sendPackets(closeSubscribers, newSubscribers);
            colony.getVisitorManager().sendPackets(closeSubscribers, newSubscribers);
            colony.getServerBuildingManager().sendPackets(closeSubscribers, newSubscribers);
            colony.getAnimalManager().sendPackets(closeSubscribers, newSubscribers);
            colony.getResearchManager().sendPackets(closeSubscribers, newSubscribers);
        }

        if (newSubscribers.isEmpty())
        {
            isDirty = false;
        }
        colony.getPermissions().clearDirty();
        colony.getServerBuildingManager().clearDirty();
        colony.getCitizenManager().clearDirty();
        colony.getVisitorManager().clearDirty();
        colony.getAnimalManager().clearDirty();
        colony.getResearchManager().clearDirty();
        newSubscribers = new HashSet<>();
    }

    @Override
    public void sendColonyViewPackets()
    {
        if (isDirty || !newSubscribers.isEmpty())
        {
            final RegistryFriendlyByteBuf colonyFriendlyByteBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), colony.getWorld().registryAccess());
            ColonyView.serializeNetworkData(colony, colonyFriendlyByteBuf, !newSubscribers.isEmpty());
            final Set<ServerPlayer> players = new HashSet<>();
            if (isDirty)
            {
                players.addAll(closeSubscribers);
            }
            players.addAll(newSubscribers);

            for (ServerPlayer player : players)
            {
                new ColonyViewMessage(colony, colonyFriendlyByteBuf, newSubscribers.contains(player)).sendToPlayer(player);
            }

            // The ticketed chunk set has now been written into a packet, so the flag that says "the view does not have
            // this yet" can go -- but only if the packet reached everyone who is looking at the view. There is one
            // buffer per update for the whole colony, so this is not a per-subscriber decision; the one case where the
            // audience is short is isDirty == false with fresh subscribers, where only the fresh ones are sent to and
            // an existing close subscriber would silently lose the update. In that case the flag stays and the next
            // dirty tick carries it. Colony#markTicketedChunksDirty sets isDirty alongside the flag, so that next tick
            // is the very next update rather than whenever something unrelated happens to change.
            if (isDirty)
            {
                colony.clearTicketedChunksDirty();
            }
        }
        colony.getRequestManager().setDirty(false);
    }

    @Override
    public void sendPermissionsPackets()
    {
        final Permissions permissions = colony.getPermissions();
        if (permissions.isDirty() || !newSubscribers.isEmpty())
        {
            final Set<ServerPlayer> players = new HashSet<>();
            if (isDirty)
            {
                players.addAll(closeSubscribers);
            }
            players.addAll(newSubscribers);
            players.forEach(player -> new PermissionsMessage.View(colony, permissions.getRank(player)).sendToPlayer(player));
        }
    }

    @Override
    public void sendWorkOrderPackets()
    {
        final IWorkManager workManager = colony.getWorkManager();
        if (workManager.isDirty() || !newSubscribers.isEmpty())
        {
            final Set<ServerPlayer> players = new HashSet<>();

            players.addAll(closeSubscribers);
            players.addAll(newSubscribers);

            List<IServerWorkOrder> workOrders = new ArrayList<>(workManager.getWorkOrders().values());
            new ColonyViewWorkOrderMessage(colony, workOrders).sendToPlayer(players);

            workManager.setDirty(false);
        }
    }

    @Override
    public void setDirty()
    {
        this.isDirty = true;
    }

    @Override
    public void addCloseSubscriber(@NotNull final ServerPlayer subscriber)
    {
        // 26.2: Fabric does have a FakePlayer (net.fabricmc.fabric.api.entity.FakePlayer, shipped in
        // fabric-events-interaction-v0 inside fabric-api; ColonyPermissionEventHandler:251 already imports it).
        // Its constructor always installs a FakePlayerPacketListener into `connection`, so the old
        // `connection == null` guard never fired and fake players leaked into the subscriber sets forever.
        if (subscriber instanceof FakePlayer || subscriber.connection == null)
        {
            Log.getLogger().warn("Adding fakeplayer as subscriber: this should not happen", new Exception());
            return;
        }

        if (!closeSubscribers.contains(subscriber))
        {
            closeSubscribers.add(subscriber);
            newSubscribers.add(subscriber);
            updateColonyViews();
        }
    }

    @Override
    public void removeCloseSubscriber(@NotNull final ServerPlayer player)
    {
        newSubscribers.remove(player);
        closeSubscribers.remove(player);
    }

    /**
     * On login we're adding global subscribers.
     */
    @Override
    public void addImportantColonyPlayer(@NotNull final ServerPlayer subscriber)
    {
        // 26.2: see addCloseSubscriber above - `connection == null` alone never catches a Fabric fake player.
        if (subscriber instanceof FakePlayer || subscriber.connection == null)
        {
            Log.getLogger().warn("Adding fakeplayer as important subscriber: this should not happen", new Exception());
            return;
        }

        importantColonyPlayers.add(subscriber);
        newSubscribers.add(subscriber);
    }

    /**
     * On logoff we're removing global subscribers.
     */
    @Override
    public void removeImportantColonyPlayer(@NotNull final ServerPlayer subscriber)
    {
        importantColonyPlayers.remove(subscriber);
        newSubscribers.remove(subscriber);
    }

    /**
     * Returns the list of online global subscribers of the colony.
     */
    @Override
    public Set<ServerPlayer> getImportantColonyPlayers()
    {
        return importantColonyPlayers;
    }
}
