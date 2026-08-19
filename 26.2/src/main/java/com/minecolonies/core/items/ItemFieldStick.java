package com.minecolonies.core.items;

import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildingextensions.IBuildingExtension;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.items.component.FieldSelection;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.SoundUtils;
import com.minecolonies.core.blocks.BlockScarecrow;
import com.minecolonies.core.colony.buildingextensions.FarmField;
import com.minecolonies.core.colony.buildings.modules.BuildingExtensionsModule;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

import static com.minecolonies.api.util.constant.TranslationConstants.FIELD_TOO_LARGE;
import static com.minecolonies.api.util.constant.translation.ToolTranslationConstants.*;

/**
 * The field stick: lays a farm field out as a rectangle in the world and pins it to a hut, without a menu.
 *
 * <p>One click, one meaning, decided by <em>what</em> was clicked rather than by a mode the player has to remember:</p>
 * <ul>
 *     <li><b>a scarecrow</b> - pick that field up. Any half drawn rectangle is dropped.</li>
 *     <li><b>a scarecrow while sneaking</b> - the way back out. First press releases the field from whoever owns it
 *     and pins it, so no hut takes it again on the next colony tick; a second press unpins it and hands it back to
 *     automatic assignment.</li>
 *     <li><b>any other block</b> - a rectangle corner. Two of them resize the picked field.</li>
 *     <li><b>a hut</b> - bind the picked field to it, or unbind it if that hut already owns it.</li>
 * </ul>
 *
 * <p>PORT-NOTE: not upstream, and deliberately server side only. The tool has no message of its own; it runs off
 * the vanilla use-on-block interaction through {@code EventHandler}, so the clicked position is one the server
 * derived itself and already reach-checked. The two things a client <em>can</em> forge are the item component's
 * remembered positions, so both are re-validated here: loaded, within {@link #MAX_INTERACTION_DISTANCE} of the
 * player, still a scarecrow, still in a colony the player may manage.</p>
 */
public class ItemFieldStick extends AbstractItemMinecolonies
{
    /**
     * How far from the player a remembered position is still believed. Same rule and same number as
     * {@code FarmFieldPlotResizeMessage}: every legitimate one was clicked, so it is within arm's reach.
     */
    private static final int MAX_INTERACTION_DISTANCE = 64;

    /**
     * @param properties the properties.
     */
    public ItemFieldStick(final Properties properties)
    {
        super("fieldstick", properties.stacksTo(1).component(com.minecolonies.api.items.component.ModDataComponents.FIELD_SELECTION, FieldSelection.EMPTY));
    }

    /**
     * Handle a right click on a block with the stick in hand. Called from {@code EventHandler}, server side only.
     *
     * @param player    the clicking player.
     * @param level     the level.
     * @param hand      the hand holding the stick.
     * @param hitResult what was clicked.
     * @return CONSUME always - the click is ours, and nothing else should act on it.
     */
    public InteractionResult handleUse(
      @NotNull final ServerPlayer player,
      @NotNull final Level level,
      @NotNull final InteractionHand hand,
      @NotNull final BlockHitResult hitResult)
    {
        final ItemStack stick = player.getItemInHand(hand);
        final BlockPos clicked = hitResult.getBlockPos();
        final BlockState state = level.getBlockState(clicked);

        if (state.getBlock() instanceof BlockScarecrow)
        {
            final BlockPos anchor = BlockScarecrow.getFieldBasePos(state, clicked);
            if (player.isShiftKeyDown())
            {
                releaseField(player, level, anchor);
            }
            else
            {
                selectField(player, level, stick, anchor);
            }
        }
        else if (state.getBlock() instanceof AbstractBlockHut<?>)
        {
            bindField(player, level, stick, clicked);
        }
        else
        {
            addCorner(player, level, stick, clicked);
        }

        return InteractionResult.CONSUME;
    }

    /**
     * Clicked a scarecrow: make its field the one the stick works on.
     */
    private void selectField(final ServerPlayer player, final Level level, final ItemStack stick, final BlockPos anchor)
    {
        final FarmField field = resolveField(player, level, anchor);
        if (field == null)
        {
            return;
        }

        FieldSelection.updateItemStack(stick, selection -> selection.withField(anchor));

        final int[] radii = field.getRadii();
        MessageUtils.format(TOOL_FIELD_STICK_SELECTED,
            anchor.getX(), anchor.getY(), anchor.getZ(),
            1 + radii[Direction.WEST.get2DDataValue()] + radii[Direction.EAST.get2DDataValue()],
            1 + radii[Direction.NORTH.get2DDataValue()] + radii[Direction.SOUTH.get2DDataValue()])
          .sendTo(player);
        SoundUtils.playSuccessSound(player, player.blockPosition());
    }

    /**
     * Clicked any ordinary block: remember it as a corner, and on the second one resize the field.
     */
    private void addCorner(final ServerPlayer player, final Level level, final ItemStack stick, final BlockPos clicked)
    {
        final FieldSelection selection = FieldSelection.readFromItemStack(stick);
        if (selection.field().isEmpty())
        {
            MessageUtils.format(TOOL_FIELD_STICK_NO_FIELD).sendTo(player);
            return;
        }

        final Optional<BlockPos> first = selection.corner();
        if (first.isEmpty())
        {
            FieldSelection.updateItemStack(stick, sel -> sel.withCorner(clicked));
            MessageUtils.format(TOOL_FIELD_STICK_CORNER, clicked.getX(), clicked.getZ()).sendTo(player);
            return;
        }

        if (!isBelievable(player, level, first.get()))
        {
            FieldSelection.updateItemStack(stick, FieldSelection::withoutCorner);
            MessageUtils.format(TOOL_FIELD_STICK_TOO_FAR).sendTo(player);
            return;
        }

        applyRectangle(player, level, stick, selection.field().get(), first.get(), clicked);
    }

    /**
     * Turn two corners into four radii and, if the colony allows that size, put them on the field.
     * <p>
     * The vertical component of both corners is thrown away on purpose. A farm field is a plan, not a box: the
     * farmer finds its ground with {@code getSurfacePos} at work time, so the height a player happened to click at
     * carries no information, and two corners at different heights describe the same rectangle as two at the same
     * height. Saying so is friendlier than refusing a click for a reason that would look arbitrary.
     */
    private void applyRectangle(
      final ServerPlayer player,
      final Level level,
      final ItemStack stick,
      final BlockPos anchor,
      final BlockPos cornerA,
      final BlockPos cornerB)
    {
        final FarmField field = resolveField(player, level, anchor);
        if (field == null)
        {
            return;
        }

        final int[] wanted = field.radiiForRectangle(cornerA, cornerB);
        if (wanted == null)
        {
            // Radii are measured from the scarecrow, so a rectangle that does not contain it simply cannot be
            // written down. Clamping to the nearest expressible rectangle would silently hand back a field other
            // than the one drawn, with nothing on screen to explain it.
            MessageUtils.format(TOOL_FIELD_STICK_OUTSIDE, anchor.getX(), anchor.getZ()).sendTo(player);
            sayWhereWeAre(player, cornerA);
            return;
        }

        final IColony colony = IColonyManager.getInstance().getIColony(level, anchor);
        if (!FarmField.isSizeAllowed(wanted, colony) && !isShrinkingOnly(field.getRadii(), wanted))
        {
            // Same rule and the same sentence the resize packet uses. Shrinking stays allowed whatever the colony's
            // current limit is, so a field laid out in free mode can still be cut down after free mode is off.
            player.sendSystemMessage(Component.translatable(FIELD_TOO_LARGE,
              FarmField.getArea(wanted),
              FarmField.getMaxArea(colony),
              FarmField.getMaxRadius(colony)));
            sayWhereWeAre(player, cornerA);
            return;
        }

        for (final Direction direction : Direction.Plane.HORIZONTAL)
        {
            field.setRadius(direction, wanted[direction.get2DDataValue()]);
        }
        if (level.getBlockEntity(anchor) instanceof final com.minecolonies.core.tileentities.TileEntityScarecrow scarecrow)
        {
            // The scarecrow keeps its own copy for the window, and the resize message writes both. Keep them level.
            for (final Direction direction : Direction.Plane.HORIZONTAL)
            {
                scarecrow.setFieldSize(direction, wanted[direction.get2DDataValue()]);
            }
        }
        if (colony != null)
        {
            colony.getServerBuildingManager().markBuildingExtensionsDirty();
        }

        FieldSelection.updateItemStack(stick, FieldSelection::withoutCorner);

        final int width = 1 + wanted[Direction.WEST.get2DDataValue()] + wanted[Direction.EAST.get2DDataValue()];
        final int depth = 1 + wanted[Direction.NORTH.get2DDataValue()] + wanted[Direction.SOUTH.get2DDataValue()];
        MessageUtils.format(TOOL_FIELD_STICK_RESIZED, width, depth, FarmField.getArea(wanted)).sendTo(player);
        SoundUtils.playSuccessSound(player, player.blockPosition());
    }

    /**
     * Clicked a hut: bind the picked field to it, or unbind it when that hut is already the owner.
     * <p>
     * Unlike every other step this one deliberately does <b>not</b> ask how far the field is. The whole point of the
     * tool is that the hut and the field may be nowhere near each other - the bug that started this was a field a
     * thousand blocks from its hut - so a proximity rule here would forbid the one thing being asked for. What
     * replaces it is stronger anyway: the field is looked up in the extension list of <em>the colony the clicked hut
     * belongs to</em>, never off a position the client supplied, and the player must be allowed to manage that
     * colony. A forged item component can therefore only name a field the player could already reassign from the
     * hut's own window. It also means the field's chunk does not have to be loaded, which at a thousand blocks it
     * will not be.
     */
    private void bindField(final ServerPlayer player, final Level level, final ItemStack stick, final BlockPos hutPos)
    {
        final FieldSelection selection = FieldSelection.readFromItemStack(stick);
        if (selection.field().isEmpty())
        {
            MessageUtils.format(TOOL_FIELD_STICK_NO_FIELD).sendTo(player);
            return;
        }

        final BlockPos anchor = selection.field().get();

        final IBuilding building = IColonyManager.getInstance().getBuilding(level, hutPos);
        if (building == null)
        {
            MessageUtils.format(TOOL_FIELD_STICK_NOT_A_HUT).sendTo(player);
            return;
        }

        final IColony colony = building.getColony();
        if (!colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            MessageUtils.format(TOOL_FIELD_STICK_NO_PERMISSION, colony.getName()).sendTo(player);
            return;
        }

        final FarmField field = findField(colony, anchor);
        if (field == null)
        {
            MessageUtils.format(TOOL_FIELD_STICK_NOT_REGISTERED).sendTo(player);
            return;
        }

        final BuildingExtensionsModule module = building.getFirstModuleOccurance(BuildingExtensionsModule.class);
        if (module == null || !module.getExpectedExtensionType().isInstance(field))
        {
            MessageUtils.format(TOOL_FIELD_STICK_NOT_A_HUT).sendTo(player);
            return;
        }

        final Component hutName = Component.translatable(building.getBuildingDisplayName());

        if (building.getID().equals(field.getBuildingId()))
        {
            module.freeExtension(field);
            field.setHandAssigned(true);
            MessageUtils.format(TOOL_FIELD_STICK_UNBOUND, hutName).sendTo(player);
            SoundUtils.playSoundForPlayer(player, SoundEvents.NOTE_BLOCK_BELL.value(), (float) SoundUtils.VOLUME * 2, 0.5f);
            return;
        }

        if (module.getOwnedExtensions().size() >= module.getMaxExtensionCount())
        {
            MessageUtils.format(TOOL_FIELD_STICK_HUT_FULL, hutName, module.getMaxExtensionCount()).sendTo(player);
            return;
        }

        // Asked before anything is taken away. Doing it the other way round - release, then discover the new hut
        // refuses the field - would leave the field owned by nobody as the price of a click that was refused, and
        // the worker who was on it walking home for a reason the player was never told.
        if (!module.canAssignExtension(field))
        {
            // Everything the module can still refuse on: for the farmer, a field with no seed chosen.
            MessageUtils.format(TOOL_FIELD_STICK_HUT_REFUSED, hutName).sendTo(player);
            return;
        }

        // Taking the field off its previous owner is the point of the tool - the whole complaint is a field held by
        // the wrong hut - but it has to go through that hut's own module so its "currently working on" pointer is
        // cleared with it, or that hut's farmer keeps walking to a field it no longer owns.
        final IBuilding previous = ownerOf(colony, field);
        releaseFromCurrentOwner(colony, field);

        if (!module.assignExtension(field))
        {
            // Should be unreachable, canAssignExtension was just asked. Left in rather than assumed away: it is the
            // only thing standing between a refusal and a field left with no owner at all.
            field.setHandAssigned(false);
            MessageUtils.format(TOOL_FIELD_STICK_HUT_REFUSED, hutName).sendTo(player);
            return;
        }

        field.setHandAssigned(true);
        if (previous != null)
        {
            MessageUtils.format(TOOL_FIELD_STICK_TAKEN_FROM, Component.translatable(previous.getBuildingDisplayName()),
              previous.getPosition().getX(), previous.getPosition().getY(), previous.getPosition().getZ()).sendTo(player);
        }
        MessageUtils.format(TOOL_FIELD_STICK_BOUND, hutName, hutPos.getX(), hutPos.getY(), hutPos.getZ()).sendTo(player);
        SoundUtils.playSuccessSound(player, player.blockPosition());
    }

    /**
     * Sneak clicked a scarecrow: the two step way back to a field nobody owns and, eventually, to automatic
     * assignment again.
     */
    private void releaseField(final ServerPlayer player, final Level level, final BlockPos anchor)
    {
        final FarmField field = resolveField(player, level, anchor);
        if (field == null)
        {
            return;
        }

        final IColony colony = IColonyManager.getInstance().getIColony(level, anchor);
        if (field.isTaken())
        {
            final IBuilding previous = ownerOf(colony, field);
            releaseFromCurrentOwner(colony, field);
            if (previous != null)
            {
                MessageUtils.format(TOOL_FIELD_STICK_TAKEN_FROM, Component.translatable(previous.getBuildingDisplayName()),
                  previous.getPosition().getX(), previous.getPosition().getY(), previous.getPosition().getZ()).sendTo(player);
            }
            // Pinned, not merely freed. A freed field is claimed again within a colony tick by whichever hut is
            // nearest, which would make "let go of this field" look like it did nothing at all.
            field.setHandAssigned(true);
            MessageUtils.format(TOOL_FIELD_STICK_RELEASED).sendTo(player);
            SoundUtils.playSoundForPlayer(player, SoundEvents.NOTE_BLOCK_BELL.value(), (float) SoundUtils.VOLUME * 2, 0.5f);
            return;
        }

        field.setHandAssigned(false);
        if (colony != null)
        {
            colony.getServerBuildingManager().markBuildingExtensionsDirty();
        }
        MessageUtils.format(TOOL_FIELD_STICK_AUTOMATIC).sendTo(player);
    }

    /**
     * The building that currently holds a field, if the colony still has one at that position.
     */
    @Nullable
    private static IBuilding ownerOf(@Nullable final IColony colony, final IBuildingExtension field)
    {
        if (colony == null || !field.isTaken())
        {
            return null;
        }
        return colony.getServerBuildingManager().getBuilding(field.getBuildingId());
    }

    /**
     * Hand a field back by the owning building's own module, so that module also forgets it was working on it.
     */
    private static void releaseFromCurrentOwner(@Nullable final IColony colony, final IBuildingExtension field)
    {
        if (!field.isTaken())
        {
            return;
        }

        final IBuilding owner = ownerOf(colony, field);
        final BuildingExtensionsModule ownerModule = owner == null ? null : owner.getFirstModuleOccurance(BuildingExtensionsModule.class);
        if (ownerModule != null)
        {
            ownerModule.freeExtension(field);
        }
        else
        {
            field.resetOwningBuilding();
            if (colony != null)
            {
                colony.getServerBuildingManager().markBuildingExtensionsDirty();
            }
        }
    }

    /**
     * Everything that has to be true of a remembered scarecrow position before anything is done to it, with a
     * reason in chat for each way it is not.
     *
     * @return the field, or null when the player has already been told why not.
     */
    @Nullable
    private FarmField resolveField(final ServerPlayer player, final Level level, final BlockPos anchor)
    {
        if (!isBelievable(player, level, anchor))
        {
            MessageUtils.format(TOOL_FIELD_STICK_TOO_FAR).sendTo(player);
            return null;
        }

        if (!(level.getBlockState(anchor).getBlock() instanceof BlockScarecrow))
        {
            MessageUtils.format(TOOL_FIELD_STICK_NOT_REGISTERED).sendTo(player);
            return null;
        }

        final IColony colony = IColonyManager.getInstance().getIColony(level, anchor);
        if (colony == null)
        {
            MessageUtils.format(TOOL_FIELD_STICK_NO_COLONY).sendTo(player);
            return null;
        }

        if (!colony.getPermissions().hasPermission(player, Action.MANAGE_HUTS))
        {
            MessageUtils.format(TOOL_FIELD_STICK_NO_PERMISSION, colony.getName()).sendTo(player);
            return null;
        }

        final FarmField field = findField(colony, anchor);
        if (field == null)
        {
            MessageUtils.format(TOOL_FIELD_STICK_NOT_REGISTERED).sendTo(player);
        }
        return field;
    }

    /**
     * Find the farm field a colony has registered at a position, without touching the world.
     *
     * @param colony the colony to look in.
     * @param anchor the scarecrow position.
     * @return the field, or null if that colony has none there.
     */
    @Nullable
    private static FarmField findField(final IColony colony, final BlockPos anchor)
    {
        return colony.getServerBuildingManager()
                 .getMatchingBuildingExtension(extension -> extension.getBuildingExtensionType().equals(BuildingExtensionRegistries.farmField.get())
                                                             && extension.getPosition().equals(anchor))
                 .map(extension -> (FarmField) extension)
                 .orElse(null);
    }

    /**
     * Repeat where the half drawn rectangle stands, so a refusal never leaves the stick in a state the player has to
     * guess at. Nothing about the refusal itself is said twice.
     */
    private static void sayWhereWeAre(final ServerPlayer player, final BlockPos corner)
    {
        MessageUtils.format(TOOL_FIELD_STICK_CORNER, corner.getX(), corner.getZ()).sendTo(player);
    }

    /**
     * Whether a position stored on the stack is one the player could plausibly have clicked.
     */
    private static boolean isBelievable(final Player player, final Level level, final BlockPos pos)
    {
        return level.isLoaded(pos)
                 && player.blockPosition().distSqr(pos) <= (double) MAX_INTERACTION_DISTANCE * MAX_INTERACTION_DISTANCE;
    }

    /**
     * Whether every wanted radius is at most the current one, which is always permitted however large the field is.
     */
    private static boolean isShrinkingOnly(final int[] current, final int[] wanted)
    {
        for (int i = 0; i < FarmField.RADII_COUNT; i++)
        {
            if (wanted[i] > current[i])
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public void appendHoverText(
      @NotNull final ItemStack stack,
      @NotNull final TooltipContext ctx,
      @NotNull final TooltipDisplay display,
      @NotNull final Consumer<Component> components,
      @NotNull final TooltipFlag flags)
    {
        super.appendHoverText(stack, ctx, display, components, flags);
        components.accept(Component.translatableEscape(TOOL_FIELD_STICK_TIP_ONE).withStyle(ChatFormatting.GRAY));
        components.accept(Component.translatableEscape(TOOL_FIELD_STICK_TIP_TWO).withStyle(ChatFormatting.GRAY));
        components.accept(Component.translatableEscape(TOOL_FIELD_STICK_TIP_THREE).withStyle(ChatFormatting.GRAY));
    }
}
