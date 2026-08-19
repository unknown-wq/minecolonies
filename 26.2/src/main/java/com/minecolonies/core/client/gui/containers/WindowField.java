package com.minecolonies.core.client.gui.containers;


import com.ldtteam.blockui.PaneBuilders;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.controls.ItemIcon;
import com.ldtteam.blockui.controls.Text;
import com.ldtteam.structurize.client.gui.WindowSelectRes;
import com.minecolonies.api.colony.ICitizen;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildings.views.IBuildingView;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.core.client.gui.AbstractWindowSkeleton;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.items.ItemCrop;
import com.minecolonies.core.network.messages.server.colony.building.fields.FarmFieldPlotResizeMessage;
import com.minecolonies.core.network.messages.server.colony.building.fields.FarmFieldUpdateSeedMessage;
import com.minecolonies.core.tileentities.TileEntityScarecrow;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.CropBlock;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;

import static com.minecolonies.api.items.ModTags.cropBiomeTags;
import static com.minecolonies.api.util.constant.TranslationConstants.*;
import static com.minecolonies.api.util.constant.translation.GuiTranslationConstants.FIELD_GUI_ASSIGNED_FARMER;
import static com.minecolonies.api.util.constant.translation.GuiTranslationConstants.FIELD_GUI_MIX_SEED_TOOLTIP;
import static com.minecolonies.api.util.constant.translation.GuiTranslationConstants.FIELD_GUI_NO_ASSIGNED_FARMER;

/**
 * Class which creates the GUI of our field inventory.
 */
@Environment(EnvType.CLIENT)
public class WindowField extends AbstractWindowSkeleton
{
    /**
     * The prefix ID of the directional buttons.
     */
    private static final String DIRECTIONAL_BUTTON_ID_PREFIX = "dir-resize-";

    /**
     * The ID of the center icon of the directional buttons.
     */
    private static final String DIRECTIONAL_BUTTON_CENTER_ICON_ID = "dir-center";

    /**
     * The ID of the select seed button.
     */
    private static final String SELECT_SEED_BUTTON_ID = "select-seed";

    /**
     * The ID of the button that mixes a further seed into the field, or takes one back out.
     */
    private static final String MIX_SEED_BUTTON_ID = "mix-seed";

    /**
     * The ID for the current seed text.
     */
    private static final String CURRENT_SEED_TEXT_ID = "current-seed";

    /**
     * The IDs of the icons showing the second and further seeds of a mixed field. The first seed keeps the icon it
     * always had, {@link #CURRENT_SEED_TEXT_ID}, so a field with one seed looks exactly as it used to.
     */
    private static final String EXTRA_SEED_ICON_ID_PREFIX = "current-seed-";

    /**
     * The ID for the current farmer text.
     */
    private static final String CURRENT_FARMER_TEXT_ID = "current-farmer";

    /**
     * How much a directional button steps per click while shift, respectively control, is held.
     */
    private static final int MEDIUM_STEP = 10;
    private static final int LARGE_STEP  = 100;

    /**
     * The tile entity of the scarecrow.
     */
    @NotNull
    private final TileEntityScarecrow tileEntityScarecrow;

    /**
     * The farm field instance.
     */
    @Nullable
    private FarmField farmField;

    /**
     * Create the field GUI.
     *
     * @param tileEntityScarecrow the scarecrow tile entity.
     */
    public WindowField(@NotNull TileEntityScarecrow tileEntityScarecrow)
    {
        super(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "gui/windowfield.xml"));
        this.tileEntityScarecrow = tileEntityScarecrow;

        registerButton(SELECT_SEED_BUTTON_ID, this::selectSeed);
        registerButton(MIX_SEED_BUTTON_ID, this::mixSeed);
        for (Direction dir : Direction.Plane.HORIZONTAL)
        {
            registerButton(DIRECTIONAL_BUTTON_ID_PREFIX + dir.getName(), this::onDirectionalButtonClick);
        }

        final Holder<Biome> biomeHolder = Minecraft.getInstance().level.getBiome(tileEntityScarecrow.getBlockPos());
        final Identifier biomeID = biomeHolder.unwrapKey().get().identifier();
        final String biomeLangKey = "biome." + biomeID.getNamespace() + "." + biomeID.getPath();
        this.findPaneOfTypeByID("biome", Text.class)
            .setText(Component.translatable("com.minecolonies.core.biome")
                .append(net.minecraft.locale.Language.getInstance().has(biomeLangKey) ? Component.translatable(biomeLangKey) : Component.literal(biomeID.getPath())));

        MutableComponent biomecategory = Component.literal("");
        for (final TagKey<Biome> preferredBiome : cropBiomeTags)
        {
            if (biomeHolder.is(preferredBiome))
            {
                if (!biomecategory.getSiblings().isEmpty())
                {
                    biomecategory.append(Component.literal(","));
                }
                biomecategory.append(Component.translatable(TranslationConstants.CROP_CLIMATE + "." + preferredBiome.location().getPath()));
            }
        }
        this.findPaneOfTypeByID("climate", Text.class).setText(biomecategory);

        // Built once, here, rather than in updateButtons: that method runs on every client tick, and a tooltip
        // rebuilt sixty times a second is sixty panes a second.
        final ButtonImage mixButton = findPaneOfTypeByID(MIX_SEED_BUTTON_ID, ButtonImage.class);
        if (mixButton != null)
        {
            PaneBuilders.tooltipBuilder()
              .hoverPane(mixButton)
              .append(Component.translatableEscape(FIELD_GUI_MIX_SEED_TOOLTIP, FarmField.MAX_SEEDS))
              .build();
        }

        updateAll();
    }

    /**
     * Button handler for selecting a seed. Unchanged in meaning: the field is sown with exactly what is picked, and
     * anything that had been mixed into it is dropped. This is the button somebody who wants a plain field of wheat
     * presses, and it does that in one click as it always did.
     */
    private void selectSeed()
    {
        openSeedPicker(stack -> setSeeds(List.of(stack)));
    }

    /**
     * Button handler for mixing a further seed into the field, or taking one back out.
     * <p>
     * A toggle rather than an add, because there has to be a way back and a second button that only removed would
     * mean three buttons for one small idea. Picking a seed the field already has takes it out - including the last
     * one, which leaves the field with no seed, the same state a newly placed scarecrow is in and one the rest of
     * the code already handles. Picking a new one appends it, up to {@link FarmField#MAX_SEEDS}; past that the click
     * does nothing, since silently dropping the oldest seed would be worse than doing nothing at all.
     */
    private void mixSeed()
    {
        openSeedPicker(stack -> {
            final List<ItemStack> wanted = new ArrayList<>(farmField.getSeeds());
            final boolean removed = wanted.removeIf(existing -> ItemStack.isSameItem(existing, stack));
            if (!removed)
            {
                if (wanted.size() >= FarmField.MAX_SEEDS)
                {
                    return;
                }
                wanted.add(stack);
            }
            setSeeds(wanted);
        });
    }

    /**
     * Open the seed picker, offering the same items it has always offered, and hand what was picked to the caller.
     *
     * @param onPicked what to do with the chosen stack.
     */
    private void openSeedPicker(@NotNull final Consumer<ItemStack> onPicked)
    {
        if (farmField == null)
        {
            return;
        }
        final Holder<Biome> biomeHolder = Minecraft.getInstance().level.getBiome(tileEntityScarecrow.getBlockPos());
        new WindowSelectRes(
            this,
            Component.translatable("com.minecolonies.coremod.gui.field.selectseed"),
            farmField.getSeed(),
            IColonyManager.getInstance().getCompatibilityManager().getListOfMatchingItems(stack -> stack.is(ConventionalItemTags.SEEDS)
                || (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock)
                || (stack.getItem() instanceof ItemCrop itemCrop && itemCrop.canBePlantedIn(biomeHolder))),
            (stack, qty) -> onPicked.accept(stack)).open();
    }

    /**
     * Button handler for clicking on any of the directional buttons.
     *
     * @param button which button was clicked.
     */
    private void onDirectionalButtonClick(Button button)
    {
        if (!button.isEnabled())
        {
            return;
        }

        String directionName = button.getID().replace(DIRECTIONAL_BUTTON_ID_PREFIX, "");
        Optional<Direction> direction = Direction.Plane.HORIZONTAL.stream().filter(f -> f.getName().equals(directionName)).findFirst();

        if (direction.isEmpty())
        {
            return;
        }

        final int[] radii = tileEntityScarecrow.getFieldSize();
        final int index = direction.get().get2DDataValue();
        final int currentValue = radii[index];

        // Clicking steps by one, which is all anyone needs for an 11x11 field but is useless for the thousand block
        // strip free mode allows, so the modifiers step by ten and by a hundred.
        final int stepSize = Minecraft.getInstance().hasControlDown() ? LARGE_STEP : (Minecraft.getInstance().hasShiftDown() ? MEDIUM_STEP : 1);

        int newRadius = currentValue + stepSize;
        if (!isAllowed(radii, index, newRadius))
        {
            // Walk back down to the largest step that still fits, and wrap round to zero when even one more block
            // does not. That keeps the button a cycle, the way it has always behaved, at any field size.
            newRadius = 0;
            for (int candidate = currentValue + stepSize - 1; candidate > currentValue; candidate--)
            {
                if (isAllowed(radii, index, candidate))
                {
                    newRadius = candidate;
                    break;
                }
            }
        }

        tileEntityScarecrow.setFieldSize(direction.get(), newRadius);
        button.setText(Component.literal(String.valueOf(tileEntityScarecrow.getFieldSize()[index])));

        new FarmFieldPlotResizeMessage(newRadius, direction.get(), tileEntityScarecrow.getBlockPos()).sendToServer();
    }

    /**
     * Whether the server would accept this field, asked the same way the server asks it.
     * <p>
     * Only a prediction: {@code FarmFieldPlotResizeMessage} re-checks server side against the authoritative colony,
     * and a client that lies here gets its resize refused with a message in chat rather than a silent snap back.
     *
     * @param radii  the field's current four radii.
     * @param index  which one is being changed.
     * @param radius what it would become.
     * @return true if the resulting field is within the colony's limit.
     */
    private boolean isAllowed(final int[] radii, final int index, final int radius)
    {
        final int[] wanted = Arrays.copyOf(radii, FarmField.RADII_COUNT);
        wanted[index] = radius;
        return FarmField.isSizeAllowed(wanted, getCurrentColony());
    }

    private void updateAll()
    {
        updateFarmField();
        updateElementStates();
        updateOwner();
        updateSeed();
        updateButtons();
    }

    /**
     * Sends a message to the server to update the seeds of the field.
     * <p>
     * The client's own view is updated at once as well, exactly as it was before mixed seeds existed: the server
     * pushes the authoritative list back on its next extension sync, and without this the icons would not change
     * until it did.
     *
     * @param stacks the seeds the field should be sown with.
     */
    private void setSeeds(final List<ItemStack> stacks)
    {
        IColonyView colonyView = getCurrentColony();
        if (colonyView != null && farmField != null)
        {
            new FarmFieldUpdateSeedMessage(colonyView, stacks, farmField.getPosition()).sendToServer();

            farmField.setSeeds(stacks);
            updateSeed();
        }
    }

    /**
     * Keep attempting to fetch the currently loaded farm field, if not present already.
     */
    private void updateFarmField()
    {
        if (farmField != null)
        {
            return;
        }

        IColonyView colonyView = getCurrentColony();
        if (colonyView == null)
        {
            return;
        }

        final @NotNull List<IBuildingExtension> fields = colonyView.getClientBuildingManager()
            .getBuildingExtensions(otherField -> otherField.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get()) && otherField.getPosition()
                .equals(tileEntityScarecrow.getBlockPos()));
        if (!fields.isEmpty() && fields.get(0) instanceof FarmField farmFieldFound)
        {
            farmField = farmFieldFound;
        }
    }

    /**
     * Updates the states of certain additional elements, determining whether they should be enabled/visible.
     */
    private void updateElementStates()
    {
        IColonyView colonyView = getCurrentColony();

        findPaneOfTypeByID(CURRENT_FARMER_TEXT_ID, Text.class).setVisible(colonyView != null);
        findPaneOfTypeByID(SELECT_SEED_BUTTON_ID, ButtonImage.class).setVisible(colonyView != null);
        findPaneOfTypeByID(CURRENT_SEED_TEXT_ID, ItemIcon.class).setVisible(colonyView != null);
        findPaneOfTypeByID(DIRECTIONAL_BUTTON_CENTER_ICON_ID, ItemIcon.class).setVisible(colonyView != null);

        final ButtonImage mixButton = findPaneOfTypeByID(MIX_SEED_BUTTON_ID, ButtonImage.class);
        if (mixButton != null)
        {
            // Only offered once the field has a seed at all: "mix in a second crop" is not a sensible first thing to
            // do with a bare field, and the plain seed button is right there.
            mixButton.setVisible(colonyView != null && farmField != null && !farmField.getSeeds().isEmpty());
        }
    }

    /**
     * Update the label which farmer owns the field, if any.
     */
    private void updateOwner()
    {
        findPaneOfTypeByID(CURRENT_FARMER_TEXT_ID, Text.class).setText(Component.translatableEscape(FIELD_GUI_NO_ASSIGNED_FARMER));

        IColonyView colonyView = getCurrentColony();
        if (colonyView == null || farmField == null || !farmField.isTaken())
        {
            return;
        }

        final IBuildingView building = colonyView.getClientBuildingManager().getBuilding(farmField.getBuildingId());
        if (building == null)
        {
            return;
        }

        final Integer citizenId = building.getAllAssignedCitizens().stream().findFirst().orElse(null);
        if (citizenId == null)
        {
            return;
        }

        ICitizen citizen = colonyView.getCitizen(citizenId);
        if (citizen == null)
        {
            return;
        }

        findPaneOfTypeByID(CURRENT_FARMER_TEXT_ID, Text.class).setText(Component.translatableEscape(FIELD_GUI_ASSIGNED_FARMER, citizen.getName()));
    }

    /**
     * Updates the row of seed icons under the selection button: one per seed the field carries, empty for the rest.
     */
    private void updateSeed()
    {
        if (farmField == null)
        {
            return;
        }

        final List<ItemStack> seeds = farmField.getSeeds();
        for (int i = 0; i < FarmField.MAX_SEEDS; i++)
        {
            // The first icon keeps the id it has always had, so the XML of a field with one seed is untouched.
            final ItemIcon icon = i == 0
                                    ? findPaneOfTypeByID(CURRENT_SEED_TEXT_ID, ItemIcon.class)
                                    : findPaneOfTypeByID(EXTRA_SEED_ICON_ID_PREFIX + (i + 1), ItemIcon.class);
            if (icon == null)
            {
                continue;
            }
            icon.setItem(i < seeds.size() ? seeds.get(i) : ItemStack.EMPTY);
        }
    }

    /**
     * Updates the directional buttons.
     */
    private void updateButtons()
    {
        for (Direction dir : Direction.Plane.HORIZONTAL)
        {
            ButtonImage button = findPaneOfTypeByID(DIRECTIONAL_BUTTON_ID_PREFIX + dir.getName(), ButtonImage.class);
            button.setText(Component.literal(Integer.toString(tileEntityScarecrow.getFieldSize()[dir.get2DDataValue()])));

            PaneBuilders.tooltipBuilder()
              .hoverPane(button)
              .append(Component.translatableEscape(PARTIAL_BLOCK_HUT_FIELD_DIRECTION_ABSOLUTE + dir.getSerializedName()))
              .appendNL(Component.translatableEscape(getDirectionalTranslationKey(dir)).setStyle(Style.EMPTY.withItalic(true).withColor(ChatFormatting.GRAY)))
              .build();
        }
    }

    /**
     * Get the current colony, if any, from the tile entity.
     *
     * @return the colony view, if exists.
     */
    @Nullable
    private IColonyView getCurrentColony()
    {
        if (tileEntityScarecrow.getCurrentColony() instanceof IColonyView colonyView)
        {
            return colonyView;
        }
        return null;
    }

    /**
     * Get translation keys for the different directional buttons.
     *
     * @param direction the direction.
     * @return the translation key.
     */
    private String getDirectionalTranslationKey(Direction direction)
    {
        Direction[] looks = Direction.orderedByNearest(Minecraft.getInstance().player);
        Direction facing = looks[0].getAxis() == Direction.Axis.Y ? looks[1] : looks[0];

        return switch (facing.getOpposite().get2DDataValue() - direction.get2DDataValue())
        {
            case 1, -3 -> BLOCK_HUT_FIELD_DIRECTION_RELATIVE_TO_RIGHT;
            case 2, -2 -> BLOCK_HUT_FIELD_DIRECTION_RELATIVE_OPPOSITE;
            case 3, -1 -> BLOCK_HUT_FIELD_DIRECTION_RELATIVE_TO_LEFT;
            default -> BLOCK_HUT_FIELD_DIRECTION_RELATIVE_NEAREST;
        };
    }

    @Override
    public void onUpdate()
    {
        super.onUpdate();
        updateAll();
    }
}
