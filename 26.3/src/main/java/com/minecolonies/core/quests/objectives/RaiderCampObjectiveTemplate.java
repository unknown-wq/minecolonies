package com.minecolonies.core.quests.objectives;

import com.google.gson.JsonObject;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.quests.IObjectiveInstance;
import com.minecolonies.api.quests.IQuestInstance;
import com.minecolonies.api.quests.IQuestObjectiveTemplate;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.util.RaiderCampPlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.minecolonies.api.quests.QuestParseConstant.*;

/**
 * A kill objective that first makes sure there is something to kill.
 *
 * <h2>Why this exists at all</h2>
 * "Go and clear the barbarian camp" is a quest the data-driven system can already express: {@code killentity} on the
 * camp mobs and {@code breakblock} on {@code minecraft:spawner} are both stock objective types and neither needed
 * changing. What the data cannot do is guarantee the camp. Camps are worldgen, one attempt per 55 x 55 chunks,
 * competing with two sibling structure sets for the same chunk and losing it unless the biome matches -- so a colony
 * may have no camp within a kilometre and the quest would be sending the player to look for something that is not
 * there. See {@link RaiderCampPlacer} for the arithmetic.
 * <p>
 * So this is the one new objective type, and it does exactly one thing the stock types do not: on start it puts a camp
 * on the ground. Everything after that -- counting the kills, the progress line in the quest window, the reward
 * unlocks, the cancellation cleanup -- is {@link KillEntityObjectiveTemplateTemplate} unchanged, and the objectives
 * that follow it in the quest file are stock {@code breakblock} and {@code dialogue}.
 *
 * <h2>What happens when it cannot place one</h2>
 * The quest is cancelled, with a message to the player saying so. Cancelling rather than stalling, because a quest
 * whose first objective can never complete is dead weight in the quest window; and cancelling rather than refusing to
 * offer the quest, because {@link com.minecolonies.core.quests.QuestManager#onColonyTick()} evaluates every unstarted
 * quest against its triggers on every colony tick, and a site search is far too expensive to run there. Cancellation
 * is not final: {@code QuestManager#deleteQuest} only drops the instance, it does not count the quest as finished, so
 * the same quest is offered again on a later tick when the player is somewhere the camp can go.
 *
 * <h2>JSON</h2>
 * The {@code details} block of {@code minecolonies:killentity}, plus three optional keys:
 * <pre>
 * "structure":  namespaced id of the structure template  (default minecolonies:camps/small_barbarian_camp)
 * "min-range":  closest the camp may be to the colony centre, blocks  (default 120)
 * "max-range":  furthest                                              (default 220)
 * </pre>
 */
public class RaiderCampObjectiveTemplate extends KillEntityObjectiveTemplateTemplate
{
    /**
     * JSON key for the structure template to place.
     */
    private static final String STRUCTURE_KEY = "structure";

    /**
     * JSON keys for the band the camp is placed in.
     */
    private static final String MIN_RANGE_KEY = "min-range";
    private static final String MAX_RANGE_KEY = "max-range";

    /**
     * Far enough out that the camp lands beyond a colony's claim at the default {@code maxColonySize} of 20 chunks
     * only when the colony has actually grown into it -- the placer rejects claimed chunks outright, so the band's job
     * is only to be wide enough that some of it is usually unclaimed. Close enough that the walk there is a trip
     * rather than an expedition, and that the chunks are plausibly loaded around a player standing in the colony.
     */
    private static final int DEFAULT_MIN_RANGE = 120;
    private static final int DEFAULT_MAX_RANGE = 220;

    /**
     * Message keys.
     */
    private static final String CAMP_FOUND_KEY  = "com.minecolonies.coremod.questobjectives.raidercamp.found";
    private static final String CAMP_FAILED_KEY = "com.minecolonies.coremod.questobjectives.raidercamp.failed";

    /**
     * The structure template placed when this objective starts.
     */
    private final Identifier structure;

    /**
     * The band, in blocks from the colony centre, the camp is placed in.
     */
    private final int minRange;
    private final int maxRange;

    public RaiderCampObjectiveTemplate(
      final int target,
      final int entitiesToKill,
      final EntityType<?> entityToKill,
      final int nextObjective,
      final List<Integer> rewards,
      final Identifier structure,
      final int minRange,
      final int maxRange)
    {
        super(target, entitiesToKill, entityToKill, nextObjective, rewards);
        this.structure = structure;
        this.minRange = minRange;
        this.maxRange = maxRange;
    }

    /**
     * Parse the objective from json.
     *
     * @param jsonObject the json to parse it from.
     * @return a new objective object.
     */
    public static IQuestObjectiveTemplate createObjective(@NotNull final HolderLookup.Provider provider, final JsonObject jsonObject)
    {
        final JsonObject details = jsonObject.getAsJsonObject(DETAILS_KEY);
        final int target = details.get(TARGET_KEY).getAsInt();
        final int quantity = details.get(QUANTITY_KEY).getAsInt();
        final EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(details.get(ENTITY_TYPE_KEY).getAsString()));
        final int nextObj = details.has(NEXT_OBJ_KEY) ? details.get(NEXT_OBJ_KEY).getAsInt() : -1;
        final Identifier structure = details.has(STRUCTURE_KEY)
                                       ? Identifier.parse(details.get(STRUCTURE_KEY).getAsString())
                                       : RaiderCampPlacer.DEFAULT_CAMP;
        final int minRange = details.has(MIN_RANGE_KEY) ? details.get(MIN_RANGE_KEY).getAsInt() : DEFAULT_MIN_RANGE;
        final int maxRange = details.has(MAX_RANGE_KEY) ? details.get(MAX_RANGE_KEY).getAsInt() : DEFAULT_MAX_RANGE;

        return new RaiderCampObjectiveTemplate(target, quantity, entityType, nextObj, parseRewards(jsonObject),
          structure, minRange, Math.max(minRange, maxRange));
    }

    @Override
    public IObjectiveInstance startObjective(final IQuestInstance colonyQuest)
    {
        final IColony colony = colonyQuest.getColony();
        final RaiderCampPlacer.Placement placement = RaiderCampPlacer.place(colony, structure, minRange, maxRange);

        if (!placement.succeeded())
        {
            Log.getLogger().info("Camp quest {} cancelled for colony {}: {} ({})",
              colonyQuest.getId(), colony.getID(), placement.failure().name(), placement.detail());
            MessageUtils.format(CAMP_FAILED_KEY).sendTo(colony).forAllPlayers();
            colonyQuest.onDeletion();
            return null;
        }

        final BlockPos pos = placement.pos();
        MessageUtils.format(CAMP_FOUND_KEY,
            BlockPosUtil.calcDirection(colony.getCenter(), pos).getLongText(),
            Component.literal(pos.toShortString()))
          .sendTo(colony)
          .forAllPlayers();

        return super.startObjective(colonyQuest);
    }
}
