package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import com.minecolonies.core.blocks.MinecoloniesCropBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.*;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Reads and edits the set of seeds a farm field is sown with, without going through the scarecrow window.
 * <p>
 * The window is the way a player is meant to do this, and this command duplicates it deliberately. Two reasons, and
 * the second is the one that matters:
 * <ul>
 *     <li>it is the only way to see, in one line per field, what every field of a colony is actually sown with,
 *     which the window cannot do because it only ever shows one field;</li>
 *     <li>the window is client code and this branch is developed without a display. A server that is right is worth
 *     having even on a day the window turns out to be wrong, and this is what makes the feature reachable then.</li>
 * </ul>
 * <p>
 * Everything it can do, the window can do; it adds no ability the ordinary player does not have.
 */
public class CommandColonyFieldSeeds implements IMCOPCommand
{
    /**
     * Argument name for the scarecrow position.
     */
    private static final String FIELD_POS_ARG = "field";

    /**
     * Argument name for the seed item id.
     */
    private static final String SEED_ARG = "seed";

    /**
     * With no position given: print every farm field the colony owns and what it is sown with.
     *
     * @param context the context of the command execution
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final List<IBuildingExtension> fields = colony.getServerBuildingManager()
                                                  .getBuildingExtensions(f -> f.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get()));
        if (fields.isEmpty())
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_FIELDSEEDS_NONE, colony.getName()), true);
            return 1;
        }

        for (final IBuildingExtension field : fields)
        {
            if (field instanceof final FarmField farmField)
            {
                report(context, farmField);
            }
        }
        return 1;
    }

    /**
     * Change what one field is sown with.
     *
     * @param context the context of the command execution.
     * @param mode    which of add / remove / set / clear was asked for.
     * @return 1 if the field was found, 0 otherwise.
     */
    private int edit(final CommandContext<CommandSourceStack> context, final Mode mode) throws CommandSyntaxException
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final BlockPos position = BlockPosArgument.getSpawnablePos(context, FIELD_POS_ARG);

        final Optional<FarmField> found = colony.getServerBuildingManager()
                                            .getBuildingExtensions(f -> f.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get())
                                                                          && f.getPosition().equals(position))
                                            .stream()
                                            .filter(FarmField.class::isInstance)
                                            .map(FarmField.class::cast)
                                            .findFirst();
        if (found.isEmpty())
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_FIELDSEEDS_NO_FIELD, position.toShortString(), colony.getName()), true);
            return 0;
        }
        final FarmField farmField = found.get();

        final List<ItemStack> wanted = new ArrayList<>(farmField.getSeeds());
        if (mode != Mode.CLEAR)
        {
            // An Identifier argument rather than a plain string: brigadier's unquoted strings do not allow a colon,
            // so "minecraft:carrot" typed at a console was rejected before the command ever ran. Measured.
            final Identifier itemId = IdentifierArgument.getId(context, SEED_ARG);
            final Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item == null)
            {
                context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_FIELDSEEDS_BAD_ITEM, itemId.toString()), true);
                return 0;
            }

            final ItemStack seed = new ItemStack(item);
            switch (mode)
            {
                case ADD, SET ->
                {
                    // Refused here rather than reported by the farmer three colony days later. The scarecrow window
                    // offers a wider set than the farmer can plant - that is why FIELD_BAD_SEED exists - but a
                    // command typed by hand has no such excuse, and a field with one unplantable seed in its list is
                    // refused wholesale by canGoPlanting, so this would break the field's other crops too.
                    if (!isPlantable(seed))
                    {
                        context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_FIELDSEEDS_NOT_PLANTABLE, seed.getHoverName()), true);
                        return 0;
                    }
                    if (mode == Mode.SET)
                    {
                        wanted.clear();
                    }
                    else if (wanted.stream().anyMatch(existing -> ItemStack.isSameItem(existing, seed)))
                    {
                        // Already there; nothing to do, and saying so is more useful than silently succeeding.
                        report(context, farmField);
                        return 1;
                    }
                    else if (wanted.size() >= FarmField.MAX_SEEDS)
                    {
                        context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_FIELDSEEDS_FULL, FarmField.MAX_SEEDS), true);
                        return 0;
                    }
                    wanted.add(seed);
                }
                case REMOVE -> wanted.removeIf(existing -> ItemStack.isSameItem(existing, seed));
                default -> { }
            }
        }
        else
        {
            wanted.clear();
        }

        farmField.setSeeds(wanted);
        colony.getServerBuildingManager().markBuildingExtensionsDirty();
        report(context, farmField);
        return 1;
    }

    /**
     * Print one field and what it is now sown with.
     *
     * @param context   the command context.
     * @param farmField the field.
     */
    private void report(final CommandContext<CommandSourceStack> context, final FarmField farmField)
    {
        final MutableComponent seeds = Component.empty();
        boolean first = true;
        for (final ItemStack seed : farmField.getSeeds())
        {
            if (!first)
            {
                seeds.append(Component.literal(", "));
            }
            seeds.append(seed.getHoverName());
            first = false;
        }
        if (first)
        {
            seeds.append(Component.literal("-"));
        }
        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_FIELDSEEDS_STATE,
            farmField.getPosition().toShortString(),
            farmField.getSeeds().size(),
            seeds), true);
    }

    /**
     * Whether the farmer could actually put this item in the ground. The same three block kinds
     * {@code EntityAIWorkFarmer#isPlantableSeed} accepts, restated here because that method is private to the AI and
     * this is the only other place that has to know.
     *
     * @param seed the seed to test.
     * @return true if it can be planted.
     */
    private static boolean isPlantable(final ItemStack seed)
    {
        return seed.getItem() instanceof final BlockItem blockItem
                 && (blockItem.getBlock() instanceof CropBlock
                       || blockItem.getBlock() instanceof StemBlock
                       || blockItem.getBlock() instanceof MinecoloniesCropBlock);
    }

    /**
     * Suggest the items that are worth typing here: everything the farmer can actually plant.
     *
     * @return the ids, as strings.
     */
    private static List<String> plantableItemIds()
    {
        final List<String> ids = new ArrayList<>();
        for (final Item item : BuiltInRegistries.ITEM)
        {
            if (isPlantable(new ItemStack(item)))
            {
                ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }
        return ids;
    }

    @Override
    public String getName()
    {
        return "fieldseeds";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id())
                         .then(IMCCommand.newArgument(FIELD_POS_ARG, BlockPosArgument.blockPos())
                                 .then(IMCCommand.newLiteral("add")
                                         .then(IMCCommand.newArgument(SEED_ARG, IdentifierArgument.id())
                                                 .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(plantableItemIds(), builder))
                                                 .executes(context -> checkPreConditionAndExecute(context, ctx -> edit(ctx, Mode.ADD)))))
                                 .then(IMCCommand.newLiteral("set")
                                         .then(IMCCommand.newArgument(SEED_ARG, IdentifierArgument.id())
                                                 .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(plantableItemIds(), builder))
                                                 .executes(context -> checkPreConditionAndExecute(context, ctx -> edit(ctx, Mode.SET)))))
                                 .then(IMCCommand.newLiteral("remove")
                                         .then(IMCCommand.newArgument(SEED_ARG, IdentifierArgument.id())
                                                 .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(plantableItemIds(), builder))
                                                 .executes(context -> checkPreConditionAndExecute(context, ctx -> edit(ctx, Mode.REMOVE)))))
                                 .then(IMCCommand.newLiteral("clear")
                                         .executes(context -> checkPreConditionAndExecute(context, ctx -> edit(ctx, Mode.CLEAR)))))
                         .executes(this::checkPreConditionAndExecute));
    }

    /**
     * What the command was asked to do to the field's seed list.
     */
    private enum Mode
    {
        ADD,
        SET,
        REMOVE,
        CLEAR
    }
}
