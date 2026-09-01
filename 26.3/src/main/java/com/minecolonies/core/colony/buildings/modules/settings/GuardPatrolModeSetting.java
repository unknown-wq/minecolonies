package com.minecolonies.core.colony.buildings.modules.settings;

import com.minecolonies.api.colony.buildings.modules.ISettingsModule;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingsModuleView;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;

import java.util.List;

import static com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting.PATROL;
import static com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting.PATROL_BORDER;
import static com.minecolonies.core.colony.buildings.modules.settings.GuardTaskSetting.PATROL_PERMANENT;

/**
 * Stores the patrol mode setting.
 */
public class GuardPatrolModeSetting extends StringSettingWithDesc
{
    /**
     * Different setting possibilities.
     */
    public static final String AUTO   = "com.minecolonies.core.guard.setting.patrol.auto";
    public static final String MANUAL = "com.minecolonies.core.guard.setting.patrol.manual";

    /**
     * Create a new patrol mode list setting.
     */
    public GuardPatrolModeSetting()
    {
        super(AUTO, MANUAL);
    }

    /**
     * Create a new patrol mode list setting.
     *
     * @param settings     the overall list of settings.
     * @param currentIndex the current selected index.
     */
    public GuardPatrolModeSetting(final List<String> settings, final int currentIndex)
    {
        super(settings, currentIndex);
    }

    @Override
    public boolean isActive(final ISettingsModule module)
    {
        return patrols(module.getSetting(AbstractBuildingGuards.GUARD_TASK).getValue());
    }

    @Override
    public boolean isActive(final ISettingsModuleView module)
    {
        return patrols(module.getSetting(AbstractBuildingGuards.GUARD_TASK).getValue());
    }

    /**
     * Whether a guard task walks a patrol route, and so has a source of patrol points to choose.
     * <p>
     * Where the points come from and whether the unit ever stands down between them are two separate questions, so a
     * permanent patrol picks its route the same two ways an ordinary one does. A border patrol is on this list for
     * the same reason it is on the barracks tower's: the border only ever supplies the <em>automatic</em> route, so a
     * player who has set his own points and switched to Manual keeps them, and hiding the choice would take that
     * away without saying so.
     *
     * @param task the guard task to test.
     * @return true if the patrol mode applies to that task.
     */
    private static boolean patrols(final String task)
    {
        return task.equals(PATROL) || task.equals(PATROL_PERMANENT) || task.equals(PATROL_BORDER);
    }

    @Override
    public boolean shouldHideWhenInactive()
    {
        return true;
    }
}
