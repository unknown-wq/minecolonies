package com.minecolonies.core.client.gui.modules.building;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.blockui.views.ScrollingList;
import com.minecolonies.api.colony.workorders.IWorkOrderView;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.gui.AbstractModuleWindow;
import com.minecolonies.core.colony.buildings.moduleviews.SettingsModuleView;
import com.minecolonies.core.colony.buildings.moduleviews.WorkOrderListModuleView;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingBuilder;
import com.minecolonies.core.debug.FreeMode;
import com.minecolonies.core.network.messages.server.colony.WorkOrderChangeMessage;
import com.minecolonies.core.network.messages.server.colony.building.builder.BuilderSelectWorkOrderMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import static com.minecolonies.api.util.constant.TranslationConstants.*;
import static com.minecolonies.api.util.constant.WindowConstants.*;

/**
 * BOWindow for the builder hut work orders list.
 */
public class WorkOrderModuleWindow extends AbstractModuleWindow<WorkOrderListModuleView>
{
    /**
     * List of work orders.
     */
    private final List<IWorkOrderView> workOrders = new ArrayList<>();

    /**
     * The scrolling list showing all the work orders.
     */
    private ScrollingList workOrdersList;

    /**
     * If the building is set to manual builder mode.
     */
    private boolean manualMode;

    /**
     * The tick check.
     */
    private int tick = 0;

    /**
     * @param moduleView the module view
     */
    public WorkOrderModuleWindow(final WorkOrderListModuleView moduleView)
    {
        super(moduleView, Identifier.fromNamespaceAndPath(Constants.MOD_ID, "gui/layouthuts/layoutworkorders.xml"));
        registerButton(WORK_ORDER_SELECT, this::selectWorkOrder);
    }

    @Override
    public void onOpened()
    {
        super.onOpened();
        manualMode = buildingView.getModuleViewByType(SettingsModuleView.class).getSetting(BuildingBuilder.MODE).getValue().equals(BuildingBuilder.MANUAL_SETTING);

        workOrdersList = findPaneOfTypeByID(LIST_WORK_ORDERS, ScrollingList.class);
        workOrdersList.setDataProvider(new ScrollingList.DataProvider()
        {
            @Override
            public int getElementCount()
            {
                return workOrders.size();
            }

            @Override
            public void updateElement(final int index, @NotNull final Pane rowPane)
            {
                updateAvailableWorkOrders(index, rowPane);
            }
        });

        updateWorkOrders();
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();

        if (tick++ == 20)
        {
            tick = 0;
            updateWorkOrders();
        }
    }

    /**
     * Clears and resets all work orders.
     */
    private void updateWorkOrders()
    {
        final Predicate<IWorkOrderView> shouldShow = wo -> wo.shouldShowIn(buildingView);
        final Predicate<IWorkOrderView> isClaimedBySelf = wo -> wo.getClaimedBy().equals(buildingView.getPosition());
        final Predicate<IWorkOrderView> isUnclaimed = wo -> wo.getClaimedBy().equals(BlockPos.ZERO);
        // Free mode lets any builder take any order, so the level the order asks for stops deciding what this hut is
        // shown - it has to agree with WorkOrderBuilding#canBuildIgnoringDistance, which is what the server assigns by.
        final Predicate<IWorkOrderView> isInRange = wo -> canBuild(wo);

        Predicate<IWorkOrderView> finalPredicate = shouldShow.and(isInRange);
        if (manualMode)
        {
            finalPredicate = finalPredicate.and(isClaimedBySelf).or(isUnclaimed);
        }
        else
        {
            finalPredicate = finalPredicate.and(isClaimedBySelf);
        }

        workOrders.clear();
        workOrders.addAll(buildingView.getColony().getWorkOrders().stream()
                            .filter(finalPredicate)
                            .toList());
        sortWorkOrders();
    }

    /**
     * Whether this builder hut may be handed a work order, its own level aside from free mode.
     *
     * @param order the order.
     * @return true if so.
     */
    private boolean canBuild(final IWorkOrderView order)
    {
        return order.canBuildIgnoringDistance(buildingView.getPosition(), buildingView.getBuildingLevel())
                 || FreeMode.isOn(buildingView.getColony());
    }

    /**
     * Re-sorts the WorkOrders list according to the priorities inside the list.
     */
    private void sortWorkOrders()
    {
        workOrders.sort(Comparator.comparing(IWorkOrderView::getPriority, Comparator.reverseOrder()));
    }

    /**
     * Updates the available work orders page.
     *
     * @param index   index in the list of resources.
     * @param rowPane The Pane to use to display the information.
     */
    private void updateAvailableWorkOrders(final int index, @NotNull final Pane rowPane)
    {
        final IWorkOrderView order = workOrders.get(index);
        boolean buttonEnabled = true;
        String disabledMessage = "";

        Text workOrderTextPanel = rowPane.findPaneOfTypeByID(WORK_ORDER_NAME, Text.class);
        PaneBuilders.tooltipBuilder()
          .append(order.getDisplayName())
          .hoverPane(workOrderTextPanel)
          .build();
        workOrderTextPanel.setText(order.getDisplayName());
        rowPane.findPaneOfTypeByID(WORK_ORDER_POS, Text.class)
          .setText(Component.translatableEscape("com.minecolonies.coremod.gui.blocks.distance", BlockPosUtil.getDistance2D(order.getLocation(), buildingView.getPosition())));

        if (buildingView.getAllAssignedCitizens().isEmpty())
        {
            disabledMessage = MESSAGE_WARNING_NO_WORKER_ASSIGNED;
            buttonEnabled = false;
        }
        if (!order.getClaimedBy().equals(BlockPos.ZERO))
        {
            disabledMessage = MESSAGE_WARNING_ALREADY_CLAIMED;
            buttonEnabled = false;
        }
        if (!canBuild(order))
        {
            disabledMessage = MESSAGE_WARNING_CANNOTBUILD;
            buttonEnabled = false;
        }

        if (order.getClaimedBy().equals(buildingView.getPosition()))
        {
            rowPane.findPaneOfTypeByID(WORK_ORDER_SELECT, ButtonImage.class).setText(Component.translatableEscape("com.minecolonies.coremod.gui.builder.cancel"));
        }
        else if (manualMode)
        {
            Button assign = rowPane.findPaneOfTypeByID(WORK_ORDER_SELECT, ButtonImage.class);
            assign.setText(Component.translatable("com.minecolonies.coremod.gui.builder.select"));

            if (!buttonEnabled)
            {
                PaneBuilders.tooltipBuilder().hoverPane(assign).build().setText(Component.translatable(disabledMessage));
                assign.setEnabled(false);
            }
        }
    }

    /**
     * On click select the clicked work order.
     *
     * @param button the clicked button.
     */
    private void selectWorkOrder(@NotNull final Button button)
    {
        final int row = workOrdersList.getListElementIndexByPane(button);
        final IWorkOrderView view = workOrders.get(row);

        if (view.getClaimedBy().equals(buildingView.getPosition()))
        {
            view.setClaimedBy(buildingView.getPosition());
            new WorkOrderChangeMessage(buildingView, view.getID(), true, 0).sendToServer();
        }
        else
        {
            new BuilderSelectWorkOrderMessage(buildingView, view.getID()).sendToServer();
        }
    }
}
