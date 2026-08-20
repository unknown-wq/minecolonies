package com.minecolonies.core.entity.ai.workers.util;

import com.minecolonies.api.entity.ai.statemachine.states.AIWorkerState;
import com.minecolonies.api.entity.ai.statemachine.states.IAIState;
import com.minecolonies.api.util.Log;
import org.jetbrains.annotations.Nullable;

/**
 * The running commentary a builder writes about itself, so a build that stops can be read out of {@code latest.log}
 * instead of guessed at.
 * <p>
 * A structure worker that stops has, historically, stopped <em>silently</em>: the state machine returns the same
 * state for ever, no exception is raised, nothing reaches the chat and nothing reaches the log. Issue #1 is exactly
 * that shape twice over — a builder pinned in {@code MINE_BLOCK}, and a builder that stands still the moment the
 * last block goes in. Neither report could be placed in the code from the log alone, because the log said nothing.
 * <p>
 * Every line here is prefixed {@value #PREFIX} so the owner of a stuck colony can {@code grep} one string and get
 * the whole story. What is written, and only what is written:
 * <ul>
 *     <li>every AI state change of a structure worker, at INFO, except the {@code BUILDING_STEP}/{@code MINE_BLOCK}
 *     pair the building loop alternates between once per block - that one is counted but not written;</li>
 *     <li>every structure stage change (CLEAR, BUILD_SOLID, … SPAWN) and the hand-over to the completion state;</li>
 *     <li>the completion path itself, on both sides of the work-order bookkeeping, so a completion that dies
 *     halfway is visible as a "begin" with no "done";</li>
 *     <li>a WARN when a state has held for {@link #STUCK_TICKS} ticks without changing, repeated no more often
 *     than {@link #REPEAT_TICKS}.</li>
 * </ul>
 * Nothing here is written per tick. A worker doing its job produces a handful of lines per building; a worker that
 * has stopped produces one line a minute naming the state it is stuck in and what it was holding when it stopped.
 */
public final class BuildWatch
{
    /**
     * The grep handle. Every line this class writes starts with it.
     */
    public static final String PREFIX = "[BuilderDebug]";

    /**
     * How long one state may hold before it is called out, in ticks. Six hundred ticks is thirty seconds, and a
     * builder that is working resets this clock on every block it places or mines - the two states the building loop
     * alternates between count as a change even though the line for them is suppressed. Half a minute without either
     * is therefore already unusual, and a player who notices a frozen builder finds the line waiting in the log.
     */
    private static final long STUCK_TICKS = 600L;

    /**
     * How often the warning repeats while the state keeps holding, in ticks. One line a minute per stuck worker,
     * which is enough to tell "stopped an hour ago" from "stopped just now" without filling the log.
     */
    private static final long REPEAT_TICKS = 1200L;

    /**
     * The state last seen, to notice a change.
     */
    @Nullable
    private IAIState lastState;

    /**
     * The game time the current state was entered.
     */
    private long stateSince;

    /**
     * The game time of the last warning about the current state, or 0 if there has been none.
     */
    private long lastWarned;

    /**
     * The stage last seen, to notice a change.
     */
    @Nullable
    private BuildingProgressStage lastStage;

    /**
     * Note the state the worker is in, and complain if it has held that state for {@link #STUCK_TICKS} ticks.
     *
     * @param who     what to call the worker in the log.
     * @param state   the state it is in now.
     * @param now     the current game time, in ticks.
     * @param details what the worker was doing, appended to the warning only.
     */
    public void state(final String who, final IAIState state, final long now, final String details)
    {
        if (state != lastState)
        {
            // The building loop alternates between these two once per block placed or mined; writing a line for
            // every block would drown the very thing this class exists to make visible. The clock is still reset,
            // so a worker pinned in either of them is still caught by the warning below.
            if (!(isBuildLoop(state) && isBuildLoop(lastState)))
            {
                Log.getLogger().info("{} {} state {} -> {}", PREFIX, who, lastState == null ? "(none)" : lastState, state);
            }
            lastState = state;
            stateSince = now;
            lastWarned = 0L;
            return;
        }

        final long held = now - stateSince;
        if (held < STUCK_TICKS || (lastWarned != 0L && now - lastWarned < REPEAT_TICKS))
        {
            return;
        }

        lastWarned = now;
        Log.getLogger().warn("{} {} has been in state {} for {} ticks with no change. {}", PREFIX, who, state, held, details);
    }

    /**
     * Whether a state is one of the two the block-by-block building loop alternates between.
     *
     * @param state the state, may be null.
     * @return true if it is part of the loop.
     */
    private static boolean isBuildLoop(@Nullable final IAIState state)
    {
        return state == AIWorkerState.BUILDING_STEP || state == AIWorkerState.MINE_BLOCK;
    }

    /**
     * Note the structure stage the worker is on.
     *
     * @param who   what to call the worker in the log.
     * @param stage the stage it is on now, null once the last one is done.
     */
    public void stage(final String who, @Nullable final BuildingProgressStage stage)
    {
        if (stage == lastStage)
        {
            return;
        }
        Log.getLogger().info("{} {} stage {} -> {}", PREFIX, who, lastStage == null ? "(none)" : lastStage, stage == null ? "(end)" : stage);
        lastStage = stage;
    }

    /**
     * Note a step of the completion path. Always paired: a "begin" with no matching "done" is a completion that
     * threw or never returned, which is the one thing a silent freeze at the last stage can be.
     *
     * @param who  what to call the worker in the log.
     * @param what the step.
     */
    public static void mark(final String who, final String what)
    {
        Log.getLogger().info("{} {} {}", PREFIX, who, what);
    }

    /**
     * Note something that went wrong but was not thrown.
     *
     * @param who  what to call the worker in the log.
     * @param what what went wrong.
     */
    public static void warn(final String who, final String what)
    {
        Log.getLogger().warn("{} {} {}", PREFIX, who, what);
    }
}
