package com.minecolonies.core.entity.pathfinding;

import net.minecraft.core.BlockPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Small target cache class, which tracks used target positions for some time to have citizens spread out more naturally when a target was recently used.
 */
public class RecentTargetCache
{
    /**
     * Max amount removed per cleanup call
     */
    private static final int  REMOVALS    = 64;
    /**
     * Path targets expire after 200 seconds
     */
    private static final long EXPIRE_TIME = 1000 * 200;

    /**
     * Storage for position, expire time and cost
     */
    private static class TargetInfo
    {
        BlockPos pos;
        long     expiresAt;
        double   extraCost;
    }

    private static final Map<BlockPos, TargetInfo>         cache    = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<TargetInfo> expiries = new ConcurrentLinkedQueue<>();

    /**
     * Adds a position that got used as path target
     *
     * @param pos
     * @param extraCost extra cost assigned to further paths resulting in this pos
     */
    public static void add(BlockPos pos, double extraCost)
    {
        TargetInfo targetWithTIme = new TargetInfo();
        targetWithTIme.expiresAt = System.currentTimeMillis() + EXPIRE_TIME;
        targetWithTIme.extraCost = extraCost;
        targetWithTIme.pos = pos;

        cache.put(pos, targetWithTIme);
        expiries.add(targetWithTIme);
    }

    /**
     * Get the extra cost associated with ending a path at this blockpos
     *
     * @param pos
     * @return
     */
    public static double getExtraCost(BlockPos pos)
    {
        // NOTE: this is called once per expanded A* node (AbstractPathJob#getEndNodeScoreWithExtraCost), up to
        // MAX_NODES = 8000 times per job, on the single pathfinding thread. It used to call cleanup() every time,
        // and cleanup() opens with System.currentTimeMillis() — measured at ~28 ns here, which made the clock read
        // ~92% of the cost of the whole lookup (36.5 ns/call with cleanup vs 2.8 ns/call without). Expiry is
        // 200 seconds, so per-node cleanup buys nothing; it now runs once per job from AbstractPathJob#search.
        TargetInfo targetInfo = cache.get(pos);
        if (targetInfo == null)
        {
            return 0.0;
        }
        return targetInfo.extraCost;
    }

    /**
     * Cleans up older entries. Removes at most {@link #REMOVALS} expired entries per call; call it once per
     * pathfinding job, not once per visited node.
     */
    public static void cleanup()
    {
        final long nowMillis = System.currentTimeMillis();
        for (int i = 0; i < REMOVALS; i++)
        {
            TargetInfo targetInfo = expiries.peek();
            if (targetInfo == null || targetInfo.expiresAt > nowMillis)
            {
                break;
            }

            TargetInfo polled = expiries.poll();
            if (polled == null)
            {
                break;
            }

            cache.remove(polled.pos, polled);
        }
    }
}
