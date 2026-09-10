package com.ldtteam.blockui.mod;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.CheckBox;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.controls.TextField;
import com.ldtteam.blockui.controls.ToggleButton;
import com.ldtteam.blockui.support.DataProviders.CheckListDataProvider;
import com.ldtteam.blockui.views.BOWindow;
import com.ldtteam.blockui.views.DropDownList;
import com.ldtteam.blockui.views.ScrollingList;
import com.ldtteam.blockui.views.ScrollingList.DataProvider;
import com.ldtteam.blockui.views.ScrollingListContainer.RowSizeModifier;
import com.ldtteam.blockui.views.SwitchView;
import com.ldtteam.blockui.views.View;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Code-side half of the four {@code showcase*.xml} layouts.
 *
 * <p>The layouts carry everything that xml can express, and this class carries the rest: scrolling-list data
 * providers, dropdown contents, the initial checkbox states, the {@link SwitchView} cycling button, a
 * {@link TextField} (which has no xml tag of its own — {@code <input>} builds a {@code TextFieldVanilla}) and a
 * tooltip assembled with {@link PaneBuilders} rather than with the {@code tooltip=} attribute.</p>
 *
 * <p>Every lookup below is defensive. These windows exist to survive a refactor of the library and report what
 * broke; one renamed id must cost one missing widget, not the whole screen.</p>
 */
public final class ShowcaseGui
{
    /**
     * Items used to give the demo rows something recognisable to draw.
     */
    private static final List<String> DEMO_ITEMS = List.of("minecraft:stone",
        "minecraft:oak_log",
        "minecraft:iron_ingot",
        "minecraft:gold_ingot",
        "minecraft:diamond",
        "minecraft:emerald",
        "minecraft:redstone",
        "minecraft:lapis_lazuli",
        "minecraft:coal",
        "minecraft:bread");

    private ShowcaseGui()
    {
        // utility class
    }

    /**
     * @return {@code blockui:gui/showcase.xml} — every control and every view at once.
     */
    public static BOWindow showcase()
    {
        return ClientEventSubscriber.testWindow(BlockUI.resLoc("gui/showcase.xml"), ShowcaseGui::setupShowcase);
    }

    /**
     * @return {@code blockui:gui/showcase_nesting.xml} — the same widgets, but never at top level.
     */
    public static BOWindow nesting()
    {
        return ClientEventSubscriber.testWindow(BlockUI.resLoc("gui/showcase_nesting.xml"), ShowcaseGui::setupNesting);
    }

    /**
     * @return {@code blockui:gui/showcase_parser.xml} — attribute parsing probes.
     */
    public static BOWindow parser()
    {
        return ClientEventSubscriber.testWindow(BlockUI.resLoc("gui/showcase_parser.xml"), ShowcaseGui::setupParser);
    }

    /**
     * @return {@code blockui:gui/showcase_inherit.xml} — {@code inherit=} and {@code <layout>} includes.
     */
    public static BOWindow inherited()
    {
        return ClientEventSubscriber.testWindow(BlockUI.resLoc("gui/showcase_inherit.xml"), ShowcaseGui::setupInherit);
    }

    private static void setupShowcase(final BOWindow window)
    {
        setChecked(window, "cb_on", true);
        setChecked(window, "cb_off", false);
        setChecked(window, "cb_disabled", true);

        simpleList(window, "list_many", 200, index -> Component.literal("row " + index));

        // Rows whose height depends on the row index, which is the only way a list can be told to vary.
        final ScrollingList varying = find(window, "list_varying", ScrollingList.class);
        if (varying != null)
        {
            varying.setDataProvider(new DataProvider()
            {
                @Override
                public int getElementCount()
                {
                    return 40;
                }

                @Override
                public void modifyRowSize(final int index, final RowSizeModifier modifier)
                {
                    modifier.setHeight(index % 3 == 0 ? 28 : 14);
                }

                @Override
                public void updateElement(final int index, final Pane rowPane)
                {
                    setRowText(rowPane, index % 3 == 0 ? "tall row " + index : "row " + index);
                }
            });
        }

        checkList(window, "list_checks", 24);

        // Deliberately left at zero elements so the emptytext= of the layout is what shows.
        simpleList(window, "list_empty", 0, index -> Component.literal(""));

        simpleList(window, "list_nested", 30, index -> Component.literal("nested row " + index));

        dropDown(window, "dropdown", "dropdown_label", 12);
        dropDown(window, "zoom_dropdown", null, 8);

        // SwitchView has no built-in "next" control; a button plus setView is how a consumer drives one.
        final SwitchView pages = find(window, "switch_pages", SwitchView.class);
        final Button next = find(window, "switch_next", Button.class);
        final Text label = find(window, "switch_label", Text.class);
        if (pages != null && next != null)
        {
            final List<String> names = List.of("page_a", "page_b", "page_c");
            final int[] current = {names.indexOf("page_b")};
            next.setHandler(b -> {
                current[0] = (current[0] + 1) % names.size();
                pages.setView(names.get(current[0]));
                if (label != null)
                {
                    label.setText(Component.literal("showing: " + names.get(current[0])));
                }
            });
        }

        // TextField is reachable only from code: Loader maps <input> to TextFieldVanilla.
        final View textFieldHost = find(window, "plain_textfield_host", View.class);
        if (textFieldHost != null)
        {
            final TextField plain = new TextField();
            plain.setSize(292, 16);
            plain.setPosition(0, 0);
            plain.setText("TextField, built in code");
            plain.putInside(textFieldHost);
        }

        // A tooltip assembled by the builder rather than by the tooltip= attribute, so both paths are covered.
        final Pane richTooltipHost = find(window, "btn_offset", Pane.class);
        if (richTooltipHost != null)
        {
            PaneBuilders.tooltipBuilder()
                .append(Component.literal("PaneBuilders.tooltipBuilder()"))
                .paragraphBreak()
                .colorName("orange")
                .append(Component.literal("coloured"))
                .append(Component.literal(" and "))
                .colorName("aqua")
                .italic()
                .append(Component.literal("italic"))
                .newLine()
                .colorName("lightgray")
                .append(Component.translatable("blockui.tooltip.properties"))
                .hoverPane(richTooltipHost)
                .build();
        }
    }

    private static void setupNesting(final BOWindow window)
    {
        checkList(window, "depth_5", 18);
        simpleList(window, "nest_list_full", 20, index -> Component.literal("full " + index));
        simpleList(window, "nest_list_empty", 0, index -> Component.literal(""));
        simpleList(window, "zoom_list", 40, index -> Component.literal("zoomed row " + index));
        dropDown(window, "nested_dropdown", null, 6);

        // Rows that are whole layouts: an icon, two texts, a toggle and a checkbox, all four levels deep.
        final ScrollingList rich = find(window, "rich_rows", ScrollingList.class);
        if (rich != null)
        {
            rich.setDataProvider(new DataProvider()
            {
                @Override
                public int getElementCount()
                {
                    return 40;
                }

                @Override
                public void updateElement(final int index, final Pane rowPane)
                {
                    final ItemIcon icon = rowPane.findPaneOfTypeByID("row_icon", ItemIcon.class);
                    if (icon != null)
                    {
                        icon.setItem(demoStack(index));
                    }

                    final Text title = rowPane.findPaneOfTypeByID("row_title", Text.class);
                    if (title != null)
                    {
                        title.setText(Component.literal("row " + index + " — " + DEMO_ITEMS.get(index % DEMO_ITEMS.size())));
                    }

                    final Text sub = rowPane.findPaneOfTypeByID("row_sub", Text.class);
                    if (sub != null)
                    {
                        sub.setText(Component.literal("four levels of children under this row"));
                    }

                    final ToggleButton toggle = rowPane.findPaneOfTypeByID("row_toggle", ToggleButton.class);
                    if (toggle != null)
                    {
                        toggle.setActiveState(index % 2 == 0 ? "on" : "off");
                    }

                    final CheckBox check = rowPane.findPaneOfTypeByID("row_check", CheckBox.class);
                    if (check != null)
                    {
                        check.setChecked(index % 3 == 0);
                    }
                }
            });
        }
    }

    private static void setupParser(final BOWindow window)
    {
        // list_no_provider is deliberately left without one: that is what it is testing.
        simpleList(window, "list_with_rows", 8, index -> Component.literal("row " + index + ", so emptytext must stay hidden"));
        simpleList(window, "offset_bar", 30, index -> Component.literal("row " + index));
        dropDown(window, "dd_sized", null, 10);
    }

    private static void setupInherit(final BOWindow window)
    {
        final ScrollingList rows = find(window, "include_rows", ScrollingList.class);
        if (rows != null)
        {
            rows.setDataProvider(new DataProvider()
            {
                @Override
                public int getElementCount()
                {
                    return 6;
                }

                @Override
                public void updateElement(final int index, final Pane rowPane)
                {
                    final Text note = rowPane.findPaneOfTypeByID("row_index", Text.class);
                    if (note != null)
                    {
                        note.setText(Component.literal("row " + index + " — the green box to the left came from the include"));
                    }

                    final ItemIcon icon = rowPane.findPaneOfTypeByID("fragment_icon", ItemIcon.class);
                    if (icon != null)
                    {
                        icon.setItem(demoStack(index));
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * A list of {@code count} rows whose first {@link Text} descendant carries the label.
     */
    private static void simpleList(final BOWindow window,
        final String id,
        final int count,
        final IntFunction<MutableComponent> label)
    {
        final ScrollingList list = find(window, id, ScrollingList.class);
        if (list == null)
        {
            return;
        }

        list.setDataProvider(new DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return count;
            }

            @Override
            public void updateElement(final int index, final Pane rowPane)
            {
                final Text text = rowPane.findPaneByType(Text.class);
                if (text != null)
                {
                    text.setText(label.apply(index));
                }
            }
        });
    }

    /**
     * A list of rows each holding a {@code checkbox}, driven through {@link CheckListDataProvider} so that the
     * provider in {@code com.ldtteam.blockui.support} is exercised as well.
     */
    private static void checkList(final BOWindow window, final String id, final int count)
    {
        final ScrollingList list = find(window, id, ScrollingList.class);
        if (list == null)
        {
            return;
        }

        final List<Boolean> states = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            states.add(i % 4 == 0);
        }

        list.setDataProvider(new CheckListDataProvider()
        {
            @Override
            public String getCheckboxId()
            {
                return "checkbox";
            }

            @Override
            public boolean isChecked(final int index)
            {
                return states.get(index);
            }

            @Override
            public void setChecked(final int index, final boolean checked)
            {
                states.set(index, checked);
            }

            @Override
            public void updateElement(final int index, final Pane rowPane, final boolean checked)
            {
                setRowText(rowPane, "row " + index + (checked ? " (on)" : ""));

                // Two rows are disabled so the disabled= texture of the checkbox is on screen too.
                if (index == 5 || index == 6)
                {
                    final CheckBox box = rowPane.findPaneOfTypeByID(getCheckboxId(), CheckBox.class);
                    if (box != null)
                    {
                        box.disable();
                    }
                }
            }

            @Override
            public int getElementCount()
            {
                return states.size();
            }
        });
    }

    /**
     * Fills a {@link DropDownList} with {@code count} labelled entries and, if a label pane is named, reports the
     * pick into it.
     */
    private static void dropDown(final BOWindow window, final String id, final String labelId, final int count)
    {
        final DropDownList dropDown = find(window, id, DropDownList.class);
        if (dropDown == null)
        {
            return;
        }

        dropDown.setDataProvider(new DropDownList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return count;
            }

            @Override
            public MutableComponent getLabel(final int index)
            {
                return Component.literal("choice " + index);
            }
        });

        if (labelId != null)
        {
            final Text label = find(window, labelId, Text.class);
            if (label != null)
            {
                dropDown.setHandler(d -> label.setText(Component.literal("picked: choice " + d.getSelectedIndex())));
            }
        }

        dropDown.setSelectedIndex(0);
    }

    private static void setRowText(final Pane rowPane, final String text)
    {
        final Text label = rowPane.findPaneByType(Text.class);
        if (label != null)
        {
            label.setText(Component.literal(text));
        }
    }

    private static void setChecked(final BOWindow window, final String id, final boolean checked)
    {
        final CheckBox box = find(window, id, CheckBox.class);
        if (box != null)
        {
            box.setChecked(checked);
        }
    }

    private static ItemStack demoStack(final int index)
    {
        final Identifier id = Identifier.tryParse(DEMO_ITEMS.get(index % DEMO_ITEMS.size()));
        return id == null
            ? ItemStack.EMPTY
            : BuiltInRegistries.ITEM.get(id).map(Reference::value).map(Item::getDefaultInstance).orElse(ItemStack.EMPTY);
    }

    /**
     * {@link Pane#findPaneOfTypeByID(String, Class)} but without the {@link IllegalArgumentException} when the id
     * exists with a different type: a showcase must degrade to a missing widget, never to a closed window.
     */
    private static <T extends Pane> T find(final BOWindow window, final String id, final Class<T> type)
    {
        final Pane pane = window.findPaneByID(id);
        if (type.isInstance(pane))
        {
            return type.cast(pane);
        }

        Log.getLogger().warn("Showcase layout {} has no '{}' of type {}", window.getXmlResourceLocation(), id, type.getSimpleName());
        return null;
    }
}
