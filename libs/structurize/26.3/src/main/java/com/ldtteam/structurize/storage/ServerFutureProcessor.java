package com.ldtteam.structurize.storage;

import com.ldtteam.structurize.api.Log;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Waits for blueprint futures to finish loading and then processes them.
 */
public class ServerFutureProcessor
{
    /**
     * Queue for processing blueprint futures.
     */
    private static final Queue<BlueprintProcessingData> blueprintConsumerQueue = new LinkedList<>();

    /**
     * Queue for processing blueprint futures.
     */
    private static final Queue<BlueprintListProcessingData> blueprintListConsumerQueue = new LinkedList<>();

    /**
     * Queue processing data to be handled on tick.
     * @param processingData the data to be processed.
     */
    public static void queueBlueprint(@NotNull final BlueprintProcessingData processingData)
    {
        blueprintConsumerQueue.add(processingData);
    }

    /**
     * Queue processing data to be handled on tick.
     * @param processingData the data to be processed.
     */
    public static void queueBlueprintList(@NotNull final BlueprintListProcessingData processingData)
    {
        blueprintListConsumerQueue.add(processingData);
    }

    /**
     * Register the server side lifecycle hooks. Called from the mod initializer.
     */
    public static void register()
    {
        ServerTickEvents.END_LEVEL_TICK.register(ServerFutureProcessor::onWorldTick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
    }

    /**
     * Drop everything still queued. The queues are static and outlive a single server, so without this a
     * world that is left with work in them keeps its levels and blueprints reachable, and the leftovers are
     * handed to the next world's tick.
     */
    public static void clear()
    {
        blueprintConsumerQueue.clear();
        blueprintListConsumerQueue.clear();
    }

    public static void onWorldTick(final ServerLevel level)
    {
        drain(blueprintConsumerQueue, level);
        drain(blueprintListConsumerQueue, level);
    }

    /**
     * Hand over every entry belonging to this level whose future has finished.
     * <p>
     * The queue is walked rather than peeked: an entry for a level that has stopped ticking (a dimension that
     * was unloaded, a server that went away) used to sit at the head forever and block everything behind it,
     * so no blueprint would ever be placed again. Entries for other levels are left in place for their own
     * tick; entries for this level that are not done yet stop the drain, so the order within a level is kept.
     *
     * @param queue the queue to drain.
     * @param level the level currently ticking.
     */
    private static <T extends ProcessingData> void drain(final Queue<T> queue, final ServerLevel level)
    {
        if (queue.isEmpty())
        {
            return;
        }

        for (final Iterator<T> iterator = queue.iterator(); iterator.hasNext(); )
        {
            final T data = iterator.next();
            if (data.level() != level)
            {
                continue;
            }
            if (!data.isDone())
            {
                break;
            }

            iterator.remove();
            try
            {
                data.accept();
            }
            catch (final Exception e)
            {
                Log.getLogger().error("Error processing blueprint future", e);
            }
        }
    }

    /**
     * Common shape of everything the queues hold.
     */
    private interface ProcessingData
    {
        /**
         * @return the level this entry belongs to.
         */
        Level level();

        /**
         * @return true once the future has resolved.
         */
        boolean isDone();

        /**
         * Hand the resolved value to the consumer.
         *
         * @throws Exception whatever the future or the consumer throws.
         */
        void accept() throws Exception;
    }

    /**
     * Data to be processed.
     */
    public record BlueprintProcessingData(Future<Blueprint> blueprintFuture, Level level, Consumer<Blueprint> consumer) implements ProcessingData
    {
        @Override
        public boolean isDone()
        {
            return blueprintFuture.isDone();
        }

        @Override
        public void accept() throws Exception
        {
            consumer.accept(blueprintFuture.get());
        }
    }

    /**
     * Data to be processed.
     */
    public record BlueprintListProcessingData(Future<List<Blueprint>> blueprintFuture, Level level, Consumer<List<Blueprint>> consumer) implements ProcessingData
    {
        @Override
        public boolean isDone()
        {
            return blueprintFuture.isDone();
        }

        @Override
        public void accept() throws Exception
        {
            consumer.accept(blueprintFuture.get());
        }
    }
}
