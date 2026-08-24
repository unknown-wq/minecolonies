package com.minecolonies.core.commands.colonycommands;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.GenericRecipe;
import com.minecolonies.api.crafting.IGenericRecipe;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.crafting.ModCraftingTypes;
import com.minecolonies.api.crafting.RecipeStorage;
import com.minecolonies.api.crafting.registry.CraftingType;
import com.minecolonies.core.colony.crafting.GenericRecipeUtils;
import com.minecolonies.core.colony.crafting.RecipeAnalyzer;
import com.minecolonies.core.commands.arguments.ColonyIdArgument;
import com.minecolonies.core.commands.commandTypes.IMCCommand;
import com.minecolonies.core.commands.commandTypes.IMCOPCommand;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_TEACH_RECIPES_BUILDING;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_TEACH_RECIPES_CUTTER_SKIPPED;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_TEACH_RECIPES_FULL;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_TEACH_RECIPES_NO_CRAFTERS;
import static com.minecolonies.api.util.constant.translation.CommandTranslationConstants.COMMAND_COLONY_TEACH_RECIPES_SUCCESS;
import static com.minecolonies.core.commands.CommandArgumentNames.COLONYID_ARG;

/**
 * Command to teach every crafter in a colony every recipe its crafting module is allowed to learn.
 */
public class CommandColonyTeachRecipes implements IMCOPCommand
{
    /**
     * Teaches every crafting building in the colony everything it may be taught, up to its recipe limit.
     *
     * @param context the command execution context.
     * @return {@code 1} when the colony was walked successfully.
     */
    @Override
    public int onExecute(final CommandContext<CommandSourceStack> context)
    {
        final IColony colony = ColonyIdArgument.getColony(context, COLONYID_ARG);
        final Level world = colony.getWorld();
        if (world == null)
        {
            return 0;
        }

        // The learnable set is whatever the crafting type registry can find and the module itself accepts; the
        // vanilla side of this is the same lookup the JEI display and the crafting tag audit use.
        final Map<CraftingType, List<IGenericRecipe>> vanilla =
          RecipeAnalyzer.buildVanillaRecipesMap(context.getSource().getServer().getRecipeManager(), world);

        int buildingCount = 0;
        int totalTaught = 0;
        int totalSkipped = 0;
        boolean anyBuildingFull = false;
        boolean anyCutterSkipped = false;

        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            int taught = 0;
            int full = 0;
            int rejected = 0;
            boolean isCrafter = false;

            for (final ICraftingBuildingModule module : building.getModulesByType(ICraftingBuildingModule.class))
            {
                // Modules without a crafting job are the 2x2 self-crafting modules of the builder and the miner.
                // They do not answer requests, and their handful of slots is better left to the player.
                if (module.getCraftingJob() == null || module.getSupportedCraftingTypes().isEmpty())
                {
                    continue;
                }
                isCrafter = true;

                // Datapack recipes are not taught, they are granted; make sure the module has picked them up
                // before measuring how much room is left.
                module.checkForWorkerSpecificRecipes();

                boolean moduleFull = false;
                for (final Map.Entry<CraftingType, List<IGenericRecipe>> entry : vanilla.entrySet())
                {
                    if (!module.canLearn(entry.getKey()))
                    {
                        continue;
                    }

                    // The cutter recipes the analyzer produces are display approximations: the ingredient variants are
                    // shuffled and the output carries empty texture data instead of the material actually used. Teaching
                    // from them would produce recipes that craft the wrong block, so they are left to the cutter GUI.
                    if (entry.getKey().equals(ModCraftingTypes.ARCHITECTS_CUTTER.get()))
                    {
                        anyCutterSkipped = true;
                        continue;
                    }

                    for (final IGenericRecipe recipe : entry.getValue())
                    {
                        final IGenericRecipe safeRecipe = GenericRecipeUtils.filterInputs(recipe, module.getIngredientValidator());
                        if (!module.isRecipeCompatible(safeRecipe))
                        {
                            continue;
                        }

                        if (moduleFull)
                        {
                            full++;
                            continue;
                        }

                        final IRecipeStorage storage = toStorage(safeRecipe);
                        if (storage == null)
                        {
                            rejected++;
                            continue;
                        }

                        final IToken<?> token = IColonyManager.getInstance().getRecipeManager().checkOrAddRecipe(storage);
                        if (module.getRecipes().contains(token))
                        {
                            continue;
                        }

                        if (module.addRecipe(token))
                        {
                            taught++;
                            continue;
                        }

                        // addRecipe only fails on a full building or an incompatible recipe. The recipe survived a
                        // round trip through a storage here, so anything still compatible must have run out of room.
                        final IGenericRecipe roundTrip = GenericRecipe.of(token);
                        if (roundTrip != null && module.isRecipeCompatible(roundTrip))
                        {
                            moduleFull = true;
                        }
                        full += moduleFull ? 1 : 0;
                        rejected += moduleFull ? 0 : 1;
                    }
                }

                anyBuildingFull |= moduleFull;
            }

            if (!isCrafter)
            {
                continue;
            }

            building.markDirty();
            buildingCount++;
            totalTaught += taught;
            totalSkipped += full + rejected;

            final BlockPos position = building.getPosition();
            final int taughtCount = taught;
            final int fullCount = full;
            final int rejectedCount = rejected;
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_TEACH_RECIPES_BUILDING,
              Component.translatableEscape(building.getBuildingDisplayName()),
              position.getX(), position.getY(), position.getZ(),
              taughtCount, fullCount, rejectedCount), true);
        }

        if (buildingCount == 0)
        {
            context.getSource().sendSuccess(() -> Component.translatable(COMMAND_COLONY_TEACH_RECIPES_NO_CRAFTERS, colony.getID(), colony.getName()), true);
            return 1;
        }

        colony.markDirty();

        final int buildings = buildingCount;
        final int taughtTotal = totalTaught;
        final int skippedTotal = totalSkipped;
        context.getSource()
          .sendSuccess(() -> Component.translatable(COMMAND_COLONY_TEACH_RECIPES_SUCCESS, taughtTotal, buildings, colony.getID(), colony.getName(), skippedTotal), true);

        if (anyBuildingFull)
        {
            context.getSource()
              .sendSuccess(() -> Component.translatable(COMMAND_COLONY_TEACH_RECIPES_FULL).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)), true);
        }
        if (anyCutterSkipped)
        {
            context.getSource()
              .sendSuccess(() -> Component.translatable(COMMAND_COLONY_TEACH_RECIPES_CUTTER_SKIPPED).setStyle(Style.EMPTY.withColor(ChatFormatting.YELLOW)), true);
        }

        return 1;
    }

    /**
     * Converts a learnable recipe into the recipe storage the teaching GUI would have produced for it.
     *
     * @param recipe the recipe to convert.
     * @return the storage, or null if the recipe has no usable inputs.
     */
    private static IRecipeStorage toStorage(final IGenericRecipe recipe)
    {
        final List<ItemStorage> inputs = new ArrayList<>();
        for (final List<ItemStack> slot : recipe.getInputs())
        {
            // A slot holds the acceptable variants of one ingredient; teaching picks exactly one of them, the same
            // way the player picks one when filling in the grid.
            for (final ItemStack variant : slot)
            {
                if (!variant.isEmpty())
                {
                    inputs.add(new ItemStorage(variant.copy()));
                    break;
                }
            }
        }

        if (inputs.isEmpty() || recipe.getPrimaryOutput().isEmpty())
        {
            return null;
        }

        return RecipeStorage.builder()
                 .withInputs(inputs)
                 .withPrimaryOutput(recipe.getPrimaryOutput().copy())
                 .withSecondaryOutputs(recipe.getAdditionalOutputs())
                 .withGridSize(recipe.getGridSize())
                 .withIntermediate(recipe.getIntermediate())
                 .withRequiredTool(recipe.getRequiredTool())
                 .withLootTable(recipe.getLootTable())
                 .build();
    }

    /**
     * Name string of the command.
     */
    @Override
    public String getName()
    {
        return "teachRecipes";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> build()
    {
        return IMCCommand.newLiteral(getName())
                 .then(IMCCommand.newArgument(COLONYID_ARG, ColonyIdArgument.id()).executes(this::checkPreConditionAndExecute));
    }
}
