package com.minecolonies.core.client.gui.citizen;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.ICitizenDataView;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.NotNull;

/**
 * BOWindow for the citizen.
 */
public class FamilyWindowCitizen extends AbstractWindowCitizen
{
    /**
     * Holder of a list element
     */
    protected final ScrollingList siblingList;
    protected final ScrollingList childrenList;

    /**
     * Constructor to initiate the citizen windows.
     *
     * @param citizen citizen to bind the window to.
     */
    public FamilyWindowCitizen(final ICitizenDataView citizen)
    {
        super(citizen, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "gui/citizen/family.xml"));
        siblingList = findPaneOfTypeByID("siblings", ScrollingList.class);
        childrenList = findPaneOfTypeByID("children", ScrollingList.class);
    }

    @Override
    public void onOpened()
    {
        super.onOpened();

        findPaneOfTypeByID("parentA", Text.class).setText(parentLabel(citizen.getParents().getA(), citizen.getParentIds().getA()));
        findPaneOfTypeByID("parentB", Text.class).setText(parentLabel(citizen.getParents().getB(), citizen.getParentIds().getB()));

        final int partner = citizen.getPartner();
        final ICitizenDataView partnerView = colony.getCitizen(partner);
        final Text partnerText = findPaneOfTypeByID("partner", Text.class);

        if (partnerView == null)
        {
            partnerText.setText(Component.literal("-"));
        }
        else
        {
            partnerText.setText(Component.literal(partnerView.getName()));
        }

        childrenList.setDataProvider(new ScrollingList.DataProvider()
        {
            /**
             * The number of rows of the list.
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return citizen.getChildren().size();
            }

            /**
             * Inserts the elements into each row.
             * @param index the index of the row/list element.
             * @param rowPane the parent Pane for the row, containing the elements to update.
             */
            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                rowPane.findPaneOfTypeByID("name", Text.class).setText(Component.literal(colony.getCitizen(citizen.getChildren().get(index)).getName()));
            }
        });

        // Deliberately not clickable. The parent id is now known here, but this pane is a plain <text> in family.xml
        // and every other family entry -- siblings, children, partner -- is plain text too; turning one of the five
        // into a link means a new pane type in the layout and a navigation path this window does not otherwise have.
        // The id is shown instead, because it is the argument to /mc citizens info, which is what actually locates a
        // citizen today. See the report for what the link would cost.
        siblingList.setDataProvider(new ScrollingList.DataProvider()
        {
            /**
             * The number of rows of the list.
             * @return the number.
             */
            @Override
            public int getElementCount()
            {
                return citizen.getSiblings().size();
            }

            /**
             * Inserts the elements into each row.
             * @param index the index of the row/list element.
             * @param rowPane the parent Pane for the row, containing the elements to update.
             */
            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                rowPane.findPaneOfTypeByID("name", Text.class).setText(Component.literal(colony.getCitizen(citizen.getSiblings().get(index)).getName()));
            }
        });
    }

    /**
     * How one parent is written in the family window.
     *
     * @param name the recorded parent name, empty when there is none.
     * @param id   the parent's citizen id, zero when the colony can no longer name them.
     * @return the label.
     */
    private static Component parentLabel(final String name, final int id)
    {
        if (name.isEmpty())
        {
            return Component.translatableEscape("com.minecolonies.coremod.gui.citizen.family.unknown");
        }
        return id == 0 ? Component.literal(name) : Component.literal(name + " (#" + id + ")");
    }
}
