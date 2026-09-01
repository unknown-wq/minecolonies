package com.minecolonies.core.colony.buildings.modules.settings;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICommonSettingsModule;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.buildings.modules.ISettingsModule;
import com.minecolonies.api.colony.buildings.modules.settings.ICraftingSetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingsModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.buildings.moduleviews.CraftingModuleView;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.ItemStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores a recipe based setting.
 */
public class RecipeSetting implements ICraftingSetting
{
    /**
     * Current index of the setting.
     */
    protected IToken<?> selectedRecipe;

    /**
     * The specific crafting module.
     */
    protected final String craftingModuleId;

    /**
     * Create a new crafting setting.
     * @param craftingModuleId the crafting module id.
     */
    public RecipeSetting(final String craftingModuleId)
    {
        this.craftingModuleId = craftingModuleId;
    }

    /**
     * Create a new string list setting.
     *
     * @param selectedRecipe the current selected recipe.
     * @param craftingModuleId the crafting module id.
     */
    public RecipeSetting(final IToken<?> selectedRecipe, final String craftingModuleId)
    {
        this.selectedRecipe = selectedRecipe;
        this.craftingModuleId = craftingModuleId;
    }

    @Override
    public IRecipeStorage getValue(final IBuilding building)
    {
        final ICraftingBuildingModule craftingModule = building.getModuleMatching(ICraftingBuildingModule.class, m -> m.getId().equals(craftingModuleId));
        final List<IToken<?>> tokens = craftingModule.getRecipes();
        for (final IToken<?> token : tokens)
        {
            if (token.equals(selectedRecipe))
            {
                return IColonyManager.getInstance().getRecipeManager().getRecipe(selectedRecipe);
            }
        }

        // The selection is gone from the module. That happens on its own: a completed research replaces a recipe
        // with a better one carrying a different token, and checkForWorkerSpecificRecipes drops the one that stopped
        // being valid. Gilded Hammer and Knowledge of the Depth do exactly that to sand, gravel, clay, bonemeal,
        // cobblestone and tuff. Falling back on the first entry of the list picked whatever happened to sit at index
        // zero, so a crusher set to sand came out of the research making something else. Look for the recipe that
        // produces what the old one produced instead, and take the list order only when nothing matches.
        final IRecipeStorage replacement = findEquivalentRecipe(tokens);
        selectedRecipe = replacement == null ? null : replacement.getToken();
        return replacement;
    }

    /**
     * Find the recipe standing in for a selection the crafting module no longer offers.
     * <p>
     * The old recipe is still in the recipe manager -- nothing is ever removed from it -- so its output is available
     * to match on even after the module has let go of the token.
     *
     * @param tokens the recipes the module offers now.
     * @return the recipe with the same primary output, else the first resolvable recipe, else null for an empty list.
     */
    private IRecipeStorage findEquivalentRecipe(final List<IToken<?>> tokens)
    {
        final IRecipeStorage previous = selectedRecipe == null ? null : IColonyManager.getInstance().getRecipeManager().getRecipe(selectedRecipe);
        final ItemStack previousOutput = previous == null ? ItemStack.EMPTY : previous.getPrimaryOutput();

        IRecipeStorage first = null;
        for (final IToken<?> token : tokens)
        {
            final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipe(token);
            if (storage == null)
            {
                // A token the manager cannot resolve is not a choice, same as in getSettings below.
                continue;
            }
            if (!previousOutput.isEmpty() && ItemStackUtils.compareItemStacksIgnoreStackSize(previousOutput, storage.getPrimaryOutput()))
            {
                return storage;
            }
            if (first == null)
            {
                first = storage;
            }
        }
        return first;
    }

    @Override
    public IRecipeStorage getValue(final IBuildingView building)
    {
        final CraftingModuleView craftingModule = building.getModuleViewMatching(CraftingModuleView.class, m -> m.getId().equals(craftingModuleId));
        if (craftingModule == null)
        {
            return null;
        }

        final List<IRecipeStorage> recipes = craftingModule.getRecipes();
        for (final IRecipeStorage recipe : recipes)
        {
            if (recipe.getToken().equals(selectedRecipe))
            {
                return recipe;
            }
        }

        // The view carries only the recipes the module offers now, so the old output cannot be looked up here. The
        // server heals its own copy of the setting and sends the new token down, and until it does this is what the
        // button shows; the empty list is the case that used to throw out of get(0).
        if (recipes.isEmpty())
        {
            return null;
        }

        selectedRecipe = recipes.get(0).getToken();
        return recipes.get(0);
    }

    @Override
    public List<ItemStack> getSettings(final IBuilding building)
    {
        final List<ItemStack> settings = new ArrayList<>();
        for (final IToken<?> token : building.getFirstModuleOccurance(ICraftingBuildingModule.class).getRecipes())
        {
            // 26.2/Fabric: same unguarded lookup as the stale-token NPE in checkForWorkerSpecificRecipes. A recipe the
            // manager cannot resolve has no output to offer as a setting, so it is simply not one of the choices.
            final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipe(token);
            if (storage != null)
            {
                settings.add(storage.getPrimaryOutput());
            }
        }
        return new ArrayList<>(settings);
    }

    @Override
    public List<ItemStack> getSettings(final IBuildingView building)
    {
        final List<ItemStack> settings = new ArrayList<>();
        for (final IRecipeStorage recipe : building.getModuleViewByType(CraftingModuleView.class).getRecipes())
        {
            settings.add(recipe.getPrimaryOutput());
        }
        return new ArrayList<>(settings);
    }

    @Override
    public Identifier getLayoutItem()
    {
        return Identifier.fromNamespaceAndPath("minecolonies", "gui/layouthuts/layoutcraftingsetting.xml");
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void setupHandler(
      final ISettingKey<?> key,
      final Pane pane,
      final ICommonSettingsModule settingsModuleView,
      final IBuildingView building,
      final BOWindow window)
    {
        pane.findPaneOfTypeByID("trigger", ButtonImage.class).setHandler(input -> {
            final List<IRecipeStorage> list = building.getModuleViewByType(CraftingModuleView.class).getRecipes();
            int currentIntIndex = 0;

            int index = 0;
            for (final IRecipeStorage recipe : list)
            {
                if (recipe.getToken().equals(selectedRecipe))
                {
                    currentIntIndex = index;
                    break;
                }
                index++;
            }
            int newIndex = currentIntIndex + 1;
            if (newIndex >= list.size())
            {
                newIndex = 0;
            }

            selectedRecipe = list.get(newIndex).getToken();
            settingsModuleView.trigger(key);
        });
    }

    @Override
    public void render(
      final ISettingKey<?> key,
      final Pane pane,
      final ICommonSettingsModule settingsModuleView,
      final IBuildingView building,
      final BOWindow window)
    {
        final IRecipeStorage stack = getValue(building);
        ButtonImage triggerButton = pane.findPaneOfTypeByID("trigger", ButtonImage.class);
        triggerButton.setEnabled(isActive((ISettingsModuleView) settingsModuleView));
        if (stack == null)
        {
            // No recipe to show. shouldHideWhenInactive keeps the row off the screen in that case, but the setting is
            // rendered from more than one place and getValue no longer invents a recipe to keep this line fed.
            triggerButton.setEnabled(false);
            return;
        }
        triggerButton.setText(Component.translatable(stack.getPrimaryOutput().getItem().getDescriptionId()));
        setHoverPane(key, triggerButton, settingsModuleView);
        pane.findPaneOfTypeByID("iconto", ItemIcon.class).setItem(stack.getPrimaryOutput());
        pane.findPaneOfTypeByID("iconfrom", ItemIcon.class).setItem(stack.getCleanedInput().get(0).getItemStack());
    }

    @Override
    public void set(final IRecipeStorage value)
    {
        selectedRecipe = value.getToken();
    }

    @Override
    public boolean isActive(final ISettingsModule module)
    {
        final ICraftingBuildingModule craftingModule = module.getBuilding().getModuleMatching(ICraftingBuildingModule.class, m -> m.getId().equals(craftingModuleId));
        return !craftingModule.getRecipes().isEmpty();
    }

    @Override
    public boolean isActive(final ISettingsModuleView module)
    {
        final CraftingModuleView craftingModule = module.getBuildingView().getModuleViewMatching(CraftingModuleView.class, m -> m.getId().equals(craftingModuleId));
        return craftingModule != null && !craftingModule.getRecipes().isEmpty();
    }

    @Override
    public IToken<?> getValue()
    {
        return selectedRecipe;
    }

    @Override
    public boolean shouldHideWhenInactive()
    {
        return true;
    }

    @Override
    public void copyValue(final ISetting<?> setting)
    {
        if (setting instanceof final RecipeSetting other)
        {
            selectedRecipe = other.selectedRecipe;
        }
    }
}
