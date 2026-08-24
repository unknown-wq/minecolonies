package com.minecolonies.core.client.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;

/**
 * 26.2/Fabric: the client main thread, for code that is reached from a render path but has to touch the level or
 * the renderer.
 * <p>
 * Model baking, block colours and mesh building all run on chunk worker threads in 26.2, and anything they call
 * back into that ends up in {@code Level#sendBlockUpdated} or {@code LevelRenderer} is a render-state access from
 * the wrong thread -- vanilla silently tolerates most of it, Sodium asserts and takes the chunk build down with
 * it. Such a call belongs here.
 * <p>
 * This class is client only and must be reached only from a code path that has already established it is running
 * on a client (a {@code level.isClientSide()} branch, an {@link Environment} client class, ...); block entities
 * and blocks are loaded on the dedicated server too and may not resolve {@link Minecraft} at all.
 */
@Environment(EnvType.CLIENT)
public final class ClientMainThread
{
    /**
     * Utility class, no instances.
     */
    private ClientMainThread()
    {
    }

    /**
     * Run a task on the client main thread: right away when the caller already is that thread, queued onto the
     * client's task list otherwise, where it is drained at the top of the next frame.
     * <p>
     * That is exactly what {@link Minecraft#execute(Runnable)} does -- it queues only when
     * {@code scheduleExecutables()} says so, and that is {@code !isSameThread()}, which the client does not
     * override. The inline half matters: a caller that is already on the main thread sees the task's effect
     * before it returns, rather than a frame later.
     *
     * @param task the task, which has to assume that the world moved on since it was handed over.
     */
    public static void runOrSchedule(@NotNull final Runnable task)
    {
        Minecraft.getInstance().execute(task);
    }
}
