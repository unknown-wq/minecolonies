package com.ldtteam.structurize.operations;

import com.ldtteam.structurize.Structurize;
import com.ldtteam.structurize.network.messages.UpdateClientRender;
import com.ldtteam.structurize.util.ChangeStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Operations running on entire areas, require a start and end position and will iterate over the entire area.
 */
public abstract class AreaOperation extends BaseOperation
{
    /**
     * The player who initiated the area operation.
     */
    protected final Player player;

    /**
     * The start position to iterate from.
     */
    protected BlockPos.MutableBlockPos startPos = new BlockPos.MutableBlockPos();

    /**
     * The end position to iterate to.
     */
    protected BlockPos.MutableBlockPos endPos = new BlockPos.MutableBlockPos();

    /**
     * A pre-existing list of positions to work on
     */
    private List<BlockPos> workPosList = new ArrayList<>();

    /**
     * The current list index pos
     */
    private int currentListIndex = 0;

    /**
     * Default constructor.
     *
     * @param storageText the text for the change storage.
     * @param player      the player who initiated the area operation.
     * @param startPos    the start pos to iterate from.
     * @param endPos      the end pos to iterate to.
     */
    protected AreaOperation(final Component storageText, final Player player, final BlockPos startPos, final BlockPos endPos)
    {
        super(new ChangeStorage(storageText, player != null ? player.getUUID() : UUID.randomUUID()));
        this.player = player;
        this.startPos = new BlockPos.MutableBlockPos(Math.min(startPos.getX(), endPos.getX()), Math.min(startPos.getY(), endPos.getY()), Math.min(startPos.getZ(), endPos.getZ()));
        this.endPos = new BlockPos.MutableBlockPos(Math.max(startPos.getX(), endPos.getX()), Math.max(startPos.getY(), endPos.getY()), Math.max(startPos.getZ(), endPos.getZ()));
        for (int x = startPos.getX(); x <= endPos.getX(); x++)
        {
            for (int z = startPos.getZ(); z <= endPos.getZ(); z++)
            {
                for (int y = startPos.getY(); y <= endPos.getY(); y++)
                {
                    workPosList.add(new BlockPos(x, y, z));
                }
            }
        }
    }

    protected AreaOperation(final Component storageText, final Player player, final List<BlockPos> workPosList)
    {
        super(new ChangeStorage(storageText, player != null ? player.getUUID() : UUID.randomUUID()));
        this.player = player;
        this.workPosList = workPosList;

        // The bounds have to come from the list. Left at their default they are (0,0,0), and the render
        // invalidation at the end of apply() then covers everything between world origin and the work area.
        if (!workPosList.isEmpty())
        {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (final BlockPos pos : workPosList)
            {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            this.startPos = new BlockPos.MutableBlockPos(minX, minY, minZ);
            this.endPos = new BlockPos.MutableBlockPos(maxX, maxY, maxZ);
        }
    }

    @Override
    public final boolean apply(final ServerLevel world)
    {
        if (player != null && player.level().dimension() != world.dimension())
        {
            return false;
        }

        int count = 0;
        for (int i = currentListIndex; i < workPosList.size(); i++)
        {
            final BlockPos currentPos = workPosList.get(i);
            // i + 1, not i: on a tick that runs out of budget the index has to point at the position that has
            // not been done yet, otherwise the next tick applies this one a second time -- and records it in
            // the change storage twice, so undo runs it twice as well.
            currentListIndex = i + 1;
            apply(world, currentPos);

            count++;
            if (count >= Structurize.getConfig().getServer().maxOperationsPerTick.get())
            {
                return false;
            }
        }

        if (!workPosList.isEmpty())
        {
            UpdateClientRender.sendFor(world, startPos, endPos);
        }

        return true;
    }

    /**
     * Apply the operation on the world.
     *
     * @param world    the world to apply them on.
     * @param position the current area position.
     */
    protected abstract void apply(final ServerLevel world, final BlockPos position);
}
