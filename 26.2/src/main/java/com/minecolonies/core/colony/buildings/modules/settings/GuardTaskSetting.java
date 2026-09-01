package com.minecolonies.core.colony.buildings.modules.settings;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.buildings.modules.ICommonSettingsModule;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingsModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

import static com.minecolonies.core.colony.buildings.AbstractBuildingGuards.PATROL_MODE;
import static com.minecolonies.core.colony.buildings.modules.settings.GuardPatrolModeSetting.MANUAL;

/**
 * Stores a guard task setting.
 */
public class GuardTaskSetting extends StringSettingWithDesc
{
    /**
     * Different setting possibilities.
     */
    public static final String PATROL      = "com.minecolonies.core.guard.setting.patrol";
    public static final String GUARD       = "com.minecolonies.core.guard.setting.guard";
    public static final String FOLLOW      = "com.minecolonies.core.guard.setting.follow";
    public static final String PATROL_MINE = "com.minecolonies.core.guard.setting.patrol_mine";

    /**
     * A patrol that never stands down: the unit walks from one patrol point to the next for as long as it is on duty,
     * and comes off the route only for the things that take any guard off it - a fight, a meal, a nap.
     * <p>
     * Offered by the Stable, where it is the difference between a cavalry unit that sorties and one that screens.
     * Every other guard building is registered with an explicit list of task options that does not contain it, so
     * nothing else in the colony gains an option it has no behaviour for.
     */
    public static final String PATROL_PERMANENT = "com.minecolonies.core.guard.setting.patrol_permanent";

    /**
     * A patrol whose route is the colony's own border rather than a wander between its buildings.
     * <p>
     * Offered by the Stable, where a mounted unit is fast enough for a frontier to be a route rather than a
     * destination. It answers <em>where</em> the route comes from and nothing else: whether the unit ever stands down
     * is still the Stable's rest interval, so a border screen that never comes in is this task with the interval at
     * zero. {@link #PATROL_PERMANENT} stays what it always was - the same "never stand down" with the ordinary route -
     * and is listed before this one because a stored setting is an index into this list and a value that moves
     * retasks every Stable in every existing save.
     */
    public static final String PATROL_BORDER = "com.minecolonies.core.guard.setting.patrol_border";

    /**
     * Different trigger button widths.
     */
    private static final int SET_POS_BUTTON_WIDTH = 60;
    private static final int HELP_BUTTON_WIDTH    = 125;

    /**
     * Create a new guard task list setting.
     */
    public GuardTaskSetting()
    {
        super(PATROL, GUARD, FOLLOW, PATROL_MINE);
    }

    /**
     * Create a new guard task list setting.
     */
    public GuardTaskSetting(final String...list)
    {
        super(list);
    }

    /**
     * Create a new string list setting.
     * @param settings the overall list of settings.
     * @param currentIndex the current selected index.
     */
    public GuardTaskSetting(final List<String> settings, final int currentIndex)
    {
        super(settings, currentIndex);
    }

    @Override
    public Identifier getLayoutItem()
    {
        return Identifier.fromNamespaceAndPath("minecolonies", "gui/layouthuts/layoutguardtasksetting.xml");
    }

    @Override
    public void onUpdate(final IBuilding building, final ServerPlayer sender)
    {
        if (building instanceof AbstractBuildingGuards guardBuilding && getValue().equals(FOLLOW))
        {
            guardBuilding.setPlayerToFollow(sender);
        }
    }

    @Override
    public void setupHandler(final ISettingKey<?> key, final Pane pane, final ICommonSettingsModule settingsModuleView, final IBuildingView building, final BOWindow window)
    {
        super.setupHandler(key, pane, settingsModuleView, building, window);

        final ButtonImage setPositionsButton = pane.findPaneOfTypeByID("setPositions", ButtonImage.class);
        if (building.getModuleView(BuildingModules.GUARD_TOOL) != null)
        {
            setPositionsButton.setHandler(button -> building.getModuleView(BuildingModules.GUARD_TOOL).getWindow().open());
        }
    }

    @Override
    public void render(final ISettingKey<?> key, final Pane pane, final ICommonSettingsModule settingsModuleView, final IBuildingView building, final BOWindow window)
    {
        super.render(key, pane, settingsModuleView, building, window);

        final ButtonImage setPositionsButton = pane.findPaneOfTypeByID("setPositions", ButtonImage.class);
        final ButtonImage helpButton = pane.findPaneOfTypeByID("helpButton", ButtonImage.class);

        switch (getValue())
        {
            case PATROL, PATROL_PERMANENT, PATROL_BORDER ->
            {
                final String patrolMode = settingsModuleView.getSetting(PATROL_MODE).getValue();
                setPositionsButton.setVisible(patrolMode.equals(MANUAL));
                helpButton.setVisible(false);
            }
            case GUARD -> setPositionsButton.setVisible(true);
            case PATROL_MINE ->
            {
                setPositionsButton.setVisible(false);
                helpButton.setVisible(true);
                setPatrolMineHelpLabel(helpButton, (AbstractBuildingGuards.View) building);
            }
            default ->
            {
                setPositionsButton.setVisible(false);
                helpButton.setVisible(false);
            }
        }
    }

    @Override
    protected int getButtonWidth(final ISettingsModuleView settingsModuleView)
    {
        return switch (getValue())
        {
            case PATROL, PATROL_PERMANENT, PATROL_BORDER ->
            {
                final String patrolMode = settingsModuleView.getSetting(PATROL_MODE).getValue();
                yield patrolMode.equals(MANUAL) ? SET_POS_BUTTON_WIDTH : MAX_BUTTON_WIDTH;
            }
            case GUARD -> SET_POS_BUTTON_WIDTH;
            case PATROL_MINE -> HELP_BUTTON_WIDTH;
            default -> MAX_BUTTON_WIDTH;
        };
    }

    /**
     * Set the correct text on the patrol mine help button.
     *
     * @param button   the button instance.
     * @param building the building.
     */
    private void setPatrolMineHelpLabel(final ButtonImage button, final AbstractBuildingGuards.View building)
    {
        Component component;
        if (building.getMinePos() != null)
        {
            component = Component.translatableEscape("com.minecolonies.coremod.gui.worherhuts.patrollingmine", building.getMinePos().toShortString());
        }
        else
        {
            component = Component.translatableEscape("com.minecolonies.coremod.job.guard.assignmine");
        }
        PaneBuilders.tooltipBuilder()
          .append(component)
          .hoverPane(button)
          .build();
    }
}
