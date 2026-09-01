package com.minecolonies.api.quests.registries;

import com.google.gson.JsonObject;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.quests.IQuestDialogueAnswer;
import com.minecolonies.api.quests.IQuestObjectiveTemplate;
import com.minecolonies.api.quests.IQuestRewardTemplate;
import com.minecolonies.api.quests.IQuestTriggerTemplate;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * All quest registries related things.
 */
public class QuestRegistries
{
    /**
     * Get the reward registry.
     * @return the reward registry.
     */
    static Registry<RewardEntry> getQuestRewardsRegistry()
    {
        return IMinecoloniesAPI.getInstance().getQuestRewardRegistry();
    }

    /**
     * Get the objective registry.
     * @return the reward registry.
     */
    static Registry<ObjectiveEntry> getQuestObjectiveRegistry()
    {
        return IMinecoloniesAPI.getInstance().getQuestObjectiveRegistry();
    }

    /**
     * Get the trigger registry.
     * @return the reward registry.
     */
    static Registry<TriggerEntry> getQuestTriggerRegistry()
    {
        return IMinecoloniesAPI.getInstance().getQuestTriggerRegistry();
    }

    /**
     * Get the dialogue answer result registry.
     * @return the reward registry.
     */
    static Registry<DialogueAnswerEntry> getDialogueAnswerResultRegistry()
    {
        return IMinecoloniesAPI.getInstance().getQuestDialogueAnswerRegistry();
    }

    /**
     * Quest reward entry type.
     */
    public static class RewardEntry
    {
        //todo create instance getters

        private Function<JsonObject, IQuestRewardTemplate>                          producer = null;
        private BiFunction<HolderLookup.Provider, JsonObject, IQuestRewardTemplate> providerProducer = null;

        public RewardEntry(final Function<JsonObject, IQuestRewardTemplate> productionFunction)
        {
            this.producer = productionFunction;
        }

        public RewardEntry(final BiFunction<HolderLookup.Provider, JsonObject, IQuestRewardTemplate> productionFunction)
        {
            this.providerProducer = productionFunction;
        }

        /**
         * Create one from json.
         * @param jsonObject the input.
         * @return the reward.
         */
        public IQuestRewardTemplate produce(@NotNull final HolderLookup.Provider provider, final JsonObject jsonObject)
        {
            if (producer != null)
            {
                return producer.apply(jsonObject);
            }
            return providerProducer.apply(provider, jsonObject);
        }
    }

    /**
     * Quest objective entry type.
     */
    public static class ObjectiveEntry
    {
        private final BiFunction<HolderLookup.Provider, JsonObject, IQuestObjectiveTemplate> producer;

        public ObjectiveEntry(final BiFunction<HolderLookup.Provider, JsonObject, IQuestObjectiveTemplate> productionFunction)
        {
            this.producer = productionFunction;
        }

        /**
         * Create one from json.
         * @param jsonObject the input.
         * @return the objective.
         */
        public IQuestObjectiveTemplate produce(@NotNull final HolderLookup.Provider provider, final JsonObject jsonObject)
        {
            return producer.apply(provider, jsonObject);
        }
    }

    /**
     * Quest trigger entry type.
     */
    public static class TriggerEntry
    {
        private final Function<JsonObject, IQuestTriggerTemplate> producer;

        public TriggerEntry(final Function<JsonObject, IQuestTriggerTemplate> productionFunction)
        {
            this.producer = productionFunction;
        }

        /**
         * Create one from json.
         * @param jsonObject the input.
         * @return the trigger.
         */
        public IQuestTriggerTemplate produce(final JsonObject jsonObject)
        {
            return producer.apply(jsonObject);
        }
    }

    /**
     * Quest dialogue entry type.
     */
    public static class DialogueAnswerEntry
    {
        private final Function<JsonObject, IQuestDialogueAnswer> producer;

        public DialogueAnswerEntry(final Function<JsonObject, IQuestDialogueAnswer> productionFunction)
        {
            this.producer = productionFunction;
        }

        /**
         * Create one from json.
         * @param jsonObject the input.
         * @return the answer result.
         */
        public IQuestDialogueAnswer produce(final JsonObject jsonObject)
        {
            return producer.apply(jsonObject);
        }
    }

    public static Identifier ITEM_REWARD_ID         = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "item");
    public static Identifier SKILL_REWARD_ID        = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "skill");
    public static Identifier RESEARCH_REWARD_ID     = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "research");
    public static Identifier RAID_REWARD_ID         = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "raid");
    public static Identifier RELATIONSHIP_REWARD_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "relationship");
    public static Identifier HAPPINESS_REWARD_ID    = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "happiness");
    public static Identifier UNLOCK_QUEST_REWARD_ID     = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "unlockquest");
    public static Identifier QUEST_REPUTATION_REWARD_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "questreputation");

    public static Identifier DIALOGUE_OBJECTIVE_ID   = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "dialogue");
    public static Identifier BREAKBLOCK_OBJECTIVE_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "breakblock");
    public static Identifier DELIVERY_OBJECTIVE_ID   = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "delivery");
    public static Identifier KILLENTITY_OBJECTIVE_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "killentity");
    public static Identifier PLACEBLOCK_OBJECTIVE_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "placeblock");
    public static Identifier BUILD_BUILDING_OBJECTIVE_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "buildbuilding");
    public static Identifier RESEARCH_OBJECTIVE_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "research");
    public static Identifier RAIDER_CAMP_OBJECTIVE_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "raidercamp");

    public static Identifier STATE_TRIGGER_ID       = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "state");
    public static Identifier RANDOM_TRIGGER_ID      = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "random");
    public static Identifier CITIZEN_TRIGGER_ID     = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "citizen");
    public static Identifier UNLOCK_TRIGGER_ID      = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "unlock");
    public static Identifier QUEST_REPUTATION_TRIGGER_ID  = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "questreputation");
    public static Identifier WORLD_DIFFICULTY_TRIGGER_ID  = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "difficulty");

    public static Identifier DIALOGUE_ANSWER_ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "dialogue");
    public static Identifier RETURN_ANSWER_ID   = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "return");
    public static Identifier CANCEL_ANSWER_ID   = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "cancel");
    public static Identifier GOTO_ANSWER_ID     = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "advanceobjective");


    public static Supplier<RewardEntry>  itemReward;
    public static Supplier<RewardEntry> skillReward;
    public static Supplier<RewardEntry> researchReward;
    public static Supplier<RewardEntry> raidReward;
    public static Supplier<RewardEntry> relationshipReward;
    public static Supplier<RewardEntry> happinessReward;
    public static Supplier<RewardEntry> unlockQuestReward;
    public static Supplier<RewardEntry> questReputationReward;

    public static Supplier<ObjectiveEntry> dialogueObjective;
    public static Supplier<ObjectiveEntry> breakBlockObjective;
    public static Supplier<ObjectiveEntry> deliveryObjective;
    public static Supplier<ObjectiveEntry> killEntityObjective;
    public static Supplier<ObjectiveEntry> placeBlockObjective;
    public static Supplier<ObjectiveEntry> buildBuildingObjective;
    public static Supplier<ObjectiveEntry> researchObjective;
    public static Supplier<ObjectiveEntry> raiderCampObjective;

    public static Supplier<TriggerEntry> stateTrigger;
    public static Supplier<TriggerEntry> randomTrigger;
    public static Supplier<TriggerEntry> citizenTrigger;
    public static Supplier<TriggerEntry> unlockTrigger;
    public static Supplier<TriggerEntry> questReputationTrigger;
    public static Supplier<TriggerEntry> worldDifficultyTrigger;

    public static Supplier<DialogueAnswerEntry> dialogueAnswerResult;
    public static Supplier<DialogueAnswerEntry> returnAnswerResult;
    public static Supplier<DialogueAnswerEntry> cancelAnswerResult;
    public static Supplier<DialogueAnswerEntry> gotoAnswerResult;

}
