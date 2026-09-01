package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.quests.IDialogueObjectiveTemplate;
import com.minecolonies.api.quests.IQuestDialogueAnswer;
import com.minecolonies.api.quests.registries.QuestRegistries;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.apiimp.CommonMinecoloniesAPIImpl;
import com.minecolonies.core.quests.objectives.*;
import com.minecolonies.core.quests.rewards.*;
import com.minecolonies.core.quests.triggers.*;

import static com.minecolonies.api.quests.registries.QuestRegistries.*;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import java.util.function.Supplier;

public final class ModQuestInitializer
{





    private ModQuestInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModQuestInitializer but this is a Utility class.");
    }

    static
    {
        QuestRegistries.deliveryObjective = registerObjective(DELIVERY_OBJECTIVE_ID.getPath(), () -> new QuestRegistries.ObjectiveEntry(
          DeliveryObjectiveTemplateTemplate::createObjective));
        QuestRegistries.dialogueObjective = registerObjective(DIALOGUE_OBJECTIVE_ID.getPath(), () -> new QuestRegistries.ObjectiveEntry(
          DialogueObjectiveTemplateTemplate::createObjective));
        QuestRegistries.killEntityObjective = registerObjective(KILLENTITY_OBJECTIVE_ID.getPath(), () -> new QuestRegistries.ObjectiveEntry(
          KillEntityObjectiveTemplateTemplate::createObjective));
        QuestRegistries.breakBlockObjective = registerObjective(BREAKBLOCK_OBJECTIVE_ID.getPath(), () -> new QuestRegistries.ObjectiveEntry(
          BreakBlockObjectiveTemplate::createObjective));
        QuestRegistries.placeBlockObjective = registerObjective(PLACEBLOCK_OBJECTIVE_ID.getPath(), () -> new QuestRegistries.ObjectiveEntry(
          PlaceBlockObjectiveTemplate::createObjective));
        QuestRegistries.buildBuildingObjective = registerObjective(BUILD_BUILDING_OBJECTIVE_ID.getPath(), () -> new QuestRegistries.ObjectiveEntry(
          BuildBuildingObjectiveTemplate::createObjective));
        QuestRegistries.researchObjective = registerObjective(RESEARCH_OBJECTIVE_ID.getPath(), () -> new QuestRegistries.ObjectiveEntry(
          ResearchObjectiveTemplate::createObjective));
        QuestRegistries.raiderCampObjective = registerObjective(RAIDER_CAMP_OBJECTIVE_ID.getPath(), () -> new QuestRegistries.ObjectiveEntry(
          RaiderCampObjectiveTemplate::createObjective));

        QuestRegistries.randomTrigger = registerTrigger(RANDOM_TRIGGER_ID.getPath(), () -> new QuestRegistries.TriggerEntry(RandomQuestTriggerTemplate::createStateTrigger));
        QuestRegistries.stateTrigger = registerTrigger(STATE_TRIGGER_ID.getPath(), () -> new QuestRegistries.TriggerEntry(StateQuestTriggerTemplate::createStateTrigger));
        QuestRegistries.citizenTrigger = registerTrigger(CITIZEN_TRIGGER_ID.getPath(), () -> new QuestRegistries.TriggerEntry(CitizenQuestTriggerTemplate::createStateTrigger));
        QuestRegistries.unlockTrigger = registerTrigger(UNLOCK_TRIGGER_ID.getPath(), () -> new QuestRegistries.TriggerEntry(UnlockQuestTriggerTemplate::createUnlockTrigger));
        QuestRegistries.questReputationTrigger = registerTrigger(QUEST_REPUTATION_TRIGGER_ID.getPath(), () -> new QuestRegistries.TriggerEntry(QuestReputationTriggerTemplate::createQuestReputationTrigger));
        QuestRegistries.worldDifficultyTrigger = registerTrigger(WORLD_DIFFICULTY_TRIGGER_ID.getPath(), () -> new QuestRegistries.TriggerEntry(WorldDifficultyTriggerTemplate::createDifficultyTrigger));

        QuestRegistries.itemReward = registerReward(ITEM_REWARD_ID.getPath(), () -> new QuestRegistries.RewardEntry(ItemRewardTemplate::createReward));
        QuestRegistries.skillReward = registerReward(SKILL_REWARD_ID.getPath(), () -> new QuestRegistries.RewardEntry(SkillRewardTemplate::createReward));
        QuestRegistries.researchReward = registerReward(RESEARCH_REWARD_ID.getPath(), () -> new QuestRegistries.RewardEntry(ResearchCompleteRewardTemplate::createReward));
        QuestRegistries.raidReward = registerReward(RAID_REWARD_ID.getPath(), () -> new QuestRegistries.RewardEntry(RaidAdjustmentRewardTemplate::createReward));
        QuestRegistries.relationshipReward = registerReward(RELATIONSHIP_REWARD_ID.getPath(), () -> new QuestRegistries.RewardEntry(RelationshipRewardTemplate::createReward));
        QuestRegistries.happinessReward = registerReward(HAPPINESS_REWARD_ID.getPath(), () -> new QuestRegistries.RewardEntry(HappinessRewardTemplate::createReward));
        QuestRegistries.unlockQuestReward = registerReward(UNLOCK_QUEST_REWARD_ID.getPath(), () -> new QuestRegistries.RewardEntry(UnlockQuestRewardTemplate::createReward));
        QuestRegistries.questReputationReward = registerReward(QUEST_REPUTATION_REWARD_ID.getPath(), () -> new QuestRegistries.RewardEntry(QuestReputationRewardTemplate::createReward));

        QuestRegistries.dialogueAnswerResult = registerAnswerResult(DIALOGUE_ANSWER_ID.getPath(), () -> new QuestRegistries.DialogueAnswerEntry(IDialogueObjectiveTemplate.DialogueElement::parse));
        QuestRegistries.returnAnswerResult = registerAnswerResult(RETURN_ANSWER_ID.getPath(), () -> new QuestRegistries.DialogueAnswerEntry(json -> new IQuestDialogueAnswer.CloseUIDialogueAnswer()));
        QuestRegistries.gotoAnswerResult = registerAnswerResult(GOTO_ANSWER_ID.getPath(), () -> new QuestRegistries.DialogueAnswerEntry(IQuestDialogueAnswer.NextObjectiveDialogueAnswer::new));
        QuestRegistries.cancelAnswerResult = registerAnswerResult(CANCEL_ANSWER_ID.getPath(), () -> new QuestRegistries.DialogueAnswerEntry(json -> new IQuestDialogueAnswer.QuestCancellationDialogueAnswer()));
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends QuestRegistries.ObjectiveEntry> Supplier<T> registerObjective(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.QUEST_OBJECTIVE_REGISTRY,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends QuestRegistries.TriggerEntry> Supplier<T> registerTrigger(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.QUEST_TRIGGER_REGISTRY,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends QuestRegistries.RewardEntry> Supplier<T> registerReward(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.QUEST_REWARD_REGISTRY,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Registers one entry eagerly into the mod registry (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path inside the minecolonies namespace.
     * @param supplier factory for the entry.
     * @return supplier of the registered entry.
     */
    private static <T extends QuestRegistries.DialogueAnswerEntry> Supplier<T> registerAnswerResult(final String path, final Supplier<T> supplier)
    {
        final T value = Registry.register(CommonMinecoloniesAPIImpl.QUEST_ANSWER_REGISTRY,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1); calling this from
     * the mod entry point is what pins the moment it happens.
     */
    public static void init()
    {
    }
}
