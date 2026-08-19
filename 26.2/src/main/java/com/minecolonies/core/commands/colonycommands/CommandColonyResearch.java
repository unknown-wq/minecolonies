package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.research.IResearchEffect;
import com.minecolonies.api.research.util.ResearchState;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_RESEARCH_COMPLETE_ALL_EXCLUSIVE;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_RESEARCH_COMPLETE_ALL_EXCLUSIVE_ENTRY;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_RESEARCH_COMPLETE_ALL_SUCCESS;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Command to complete the entire global research tree for a colony, ignoring costs and university requirements.
 */
public class CommandColonyResearch implements IMCOPCommand
{
    /**
     * Name of the subcommand that completes the whole tree.
     */
    private static final String COMPLETE_ALL_ARG = "completeall";

    /**
     * Completes every research of every branch, in dependency order, and pushes the resulting effects to the client.
     *
     * @param context the command execution context.
     * @return {@code 1} when the tree was walked successfully.
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final IGlobalResearchTree globalTree = IGlobalResearchTree.getInstance();

        final List<IGlobalResearch> exclusions = new ArrayList<>();
        int completed = 0;
        for (final Identifier branch : globalTree.getBranches())
        {
            for (final Identifier primary : globalTree.getPrimaryResearch(branch))
            {
                completed += completeSubTree(colony, branch, primary, exclusions);
            }
        }

        // Effects apply in strength order rather than completion order, but a research completed out of a datapack
        // reload may have left a stale entry behind, so recalculate the whole set from the completed list.
        colony.getResearchManager().getResearchEffects().removeAllEffects();
        for (final Identifier researchId : colony.getResearchManager().getResearchTree().getCompletedList())
        {
            for (final IResearchEffect effect : globalTree.getEffectsForResearch(researchId))
            {
                colony.getResearchManager().getResearchEffects().applyEffect(effect);
            }
        }

        for (final ICitizenData citizen : colony.getCitizenManager().getCitizens())
        {
            citizen.applyResearchEffects();
        }

        // Drops the now-pointless "research available" announcements for anything that started out on autostart.
        colony.getResearchManager().checkAutoStartResearch();
        colony.getResearchManager().markDirty();
        colony.markDirty();

        final int completedCount = completed;
        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_RESEARCH_COMPLETE_ALL_SUCCESS, completedCount, colony.getID(), colony.getName()), true);

        if (!exclusions.isEmpty())
        {
            context.getSource()
              .sendSuccess(() -> Component.translatable(COMMAND_COLONY_RESEARCH_COMPLETE_ALL_EXCLUSIVE, exclusions.size())
                                   .setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)), true);
            for (final IGlobalResearch excluded : exclusions)
            {
                context.getSource()
                  .sendSuccess(() -> Component.translatable(COMMAND_COLONY_RESEARCH_COMPLETE_ALL_EXCLUSIVE_ENTRY,
                    MutableComponent.create(excluded.getName()),
                    excluded.getId().toString()), true);
            }
        }

        return 1;
    }

    /**
     * Completes a research and then everything below it, parents first, so that nothing is unlocked out of sequence.
     *
     * @param colony     the colony to research for.
     * @param branch     the branch the research sits on.
     * @param researchId the research to complete.
     * @param exclusions collects the researches that were skipped because a sibling won an exclusive choice.
     * @return the number of researches that were newly completed.
     */
    private int completeSubTree(final IColony colony, final Identifier branch, final Identifier researchId, final List<IGlobalResearch> exclusions)
    {
        final IGlobalResearch research = IGlobalResearchTree.getInstance().getResearch(branch, researchId);
        if (research == null)
        {
            return 0;
        }

        int completed = complete(colony, research) ? 1 : 0;

        final List<Identifier> children = new ArrayList<>(research.getChildren());
        children.sort(Comparator.comparingInt((Identifier id) -> childSortOrder(branch, id)).thenComparing(Identifier::toString));

        // Some researches only allow a single one of their children to ever be taken. Keep the first one the
        // university GUI would offer and drop the alternatives, rather than producing a tree the GUI cannot represent.
        if (research.hasOnlyChild() && children.size() > 1)
        {
            for (final Identifier skipped : children.subList(1, children.size()))
            {
                collectSubTree(branch, skipped, exclusions);
            }
            children.subList(1, children.size()).clear();
        }

        for (final Identifier child : children)
        {
            completed += completeSubTree(colony, branch, child, exclusions);
        }

        return completed;
    }

    /**
     * Completes a single research through the model's own completion route, without charging the colony or requiring a university.
     *
     * @param colony   the colony to research for.
     * @param research the research to complete.
     * @return true if the research was not already finished.
     */
    private boolean complete(final IColony colony, final IGlobalResearch research)
    {
        final ILocalResearchTree localTree = colony.getResearchManager().getResearchTree();

        ILocalResearch localResearch = localTree.getResearch(research.getBranch(), research.getId());
        if (localResearch != null && localResearch.getState() == ResearchState.FINISHED)
        {
            return false;
        }

        if (localResearch == null)
        {
            research.startResearch(localTree);
            localResearch = localTree.getResearch(research.getBranch(), research.getId());
            if (localResearch == null)
            {
                return false;
            }
        }

        // research() ticks the progress up by one and concludes the research once it is over the branch base time,
        // applying the effects and marking the tree complete on the way through.
        localResearch.setState(ResearchState.IN_PROGRESS);
        localResearch.setProgress(IGlobalResearchTree.getInstance().getBranchData(research.getBranch()).getBaseTime(localResearch.getDepth()));
        return localResearch.research(colony.getResearchManager().getResearchEffects(), localTree);
    }

    /**
     * Collects a research and everything below it into the exclusion list.
     *
     * @param branch     the branch the research sits on.
     * @param researchId the research to collect.
     * @param exclusions the list to add to.
     */
    private void collectSubTree(final Identifier branch, final Identifier researchId, final List<IGlobalResearch> exclusions)
    {
        final IGlobalResearch research = IGlobalResearchTree.getInstance().getResearch(branch, researchId);
        if (research == null)
        {
            return;
        }

        exclusions.add(research);
        for (final Identifier child : research.getChildren())
        {
            collectSubTree(branch, child, exclusions);
        }
    }

    /**
     * Gets the display sort order of a research, used to pick a winner between exclusive siblings.
     *
     * @param branch     the branch the research sits on.
     * @param researchId the research to look up.
     * @return the sort order, or the datapack default for an unknown research.
     */
    private int childSortOrder(final Identifier branch, final Identifier researchId)
    {
        final IGlobalResearch research = IGlobalResearchTree.getInstance().getResearch(branch, researchId);
        return research == null ? Integer.MAX_VALUE : research.getSortOrder();
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "research";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newLiteral(COMPLETE_ALL_ARG)
                         .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id()).executes(this::checkPreConditionAndExecute)));
    }
}
