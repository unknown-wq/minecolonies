package com.minecolonies.core.colony.buildings.modules.settings;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.views.BOWindow;
import com.minecolonies.api.colony.buildings.modules.ICommonSettingsModule;
import com.minecolonies.api.colony.buildings.modules.ISettingsModule;
import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingsModuleView;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import net.minecraft.resources.Identifier;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
/**
 * Stores an integer setting.
 */
public class IntSetting implements ISetting<Integer>
{
    /**
     * Default value of the setting.
     */
    private final int defaultValue;

    /**
     * The value of the setting.
     */
    private int value;

    /**
     * Create a new boolean setting.
     * @param init the initial value.
     */
    public IntSetting(final int init)
    {
        this.value = init;
        this.defaultValue = init;
    }

    /**
     * Create a new int setting.
     * @param value the value.
     * @param def the default value.
     */
    public IntSetting(final int value, final int def)
    {
        this.value = value;
        this.defaultValue = def;
    }

    /**
     * Get the setting value.
     * @return the set value.
     */
    public Integer getValue()
    {
        return value;
    }

    /**
     * Get the default value.
     * @return the default value.
     */
    public int getDefault()
    {
        return defaultValue;
    }

    @Override
    public Identifier getLayoutItem()
    {
        return Identifier.fromNamespaceAndPath("minecolonies", "gui/layouthuts/layoutintsetting.xml");
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void setupHandler(
      final ISettingKey<?> key,
      final Pane pane,
      final ICommonSettingsModule settingsModuleView,
      final IBuildingView building, final BOWindow window)
    {
        pane.findPaneOfTypeByID("trigger", TextField.class).setHandler(input -> {
            // An empty box is a half-finished edit, not a value of zero. Writing zero here and letting render() paint
            // it straight back put a "0" under the caret the instant the player cleared the field, so a fresh number
            // could never be typed - it had to be typed around the digit that reappeared.
            if (input.getText().isEmpty())
            {
                return;
            }

            try
            {
                // Through setValue rather than the field, so a subclass that constrains its value constrains what
                // the text field can produce as well.
                setValue(Integer.parseInt(input.getText()));
                settingsModuleView.trigger(key);
            }
            catch (final NumberFormatException ex)
            {
                // A partially typed number ("-", "12x"); leave the last good value alone.
            }
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
        final TextField field = pane.findPaneOfTypeByID("trigger", TextField.class);
        field.setEnabled(isActive((ISettingsModuleView) settingsModuleView));
        setHoverPane(key, field, settingsModuleView);

        // Never repaint the box the player is typing in. This runs once per frame, and the value behind it is
        // overwritten by every building view the server sends; a sync that landed mid-edit used to drop the old
        // number back into the field and move the caret to the end of it, which reads as the field refusing the edit.
        // Once focus moves on, the field is brought back into agreement with the value.
        if (!field.isFocus() && !field.getText().equals(String.valueOf(this.value)))
        {
            field.setText(String.valueOf(value));
        }
    }

    @Override
    public void copyValue(final ISetting<?> setting)
    {
        if (setting instanceof final IntSetting other)
        {
            setValue(other.getValue());
        }
    }

    /**
     * Set a new int value.
     * @param value the int to set.
     */
    public void setValue(final int value)
    {
        this.value = value;
    }
}
