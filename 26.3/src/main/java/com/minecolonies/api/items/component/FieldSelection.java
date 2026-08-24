package com.minecolonies.api.items.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * What the field stick is currently holding on to: the scarecrow whose field is being edited, and the first corner
 * of a rectangle if one has already been clicked.
 * <p>
 * Shaped after Structurize's {@code AbstractItemWithPosSelector.PosSelection}, which is the closest thing in the
 * tree to a two-click selection stored on a stack.
 * <p>
 * Both values are only ever hints. The server re-derives everything it acts on from the world - is the anchor still
 * a scarecrow, is it still in a colony, may the player manage that colony, does the rectangle contain the anchor -
 * because a creative client can put whatever it likes in a data component.
 *
 * @param field  the scarecrow position, empty when no field has been picked yet.
 * @param corner the first rectangle corner, empty when the next click starts a rectangle.
 */
public record FieldSelection(Optional<BlockPos> field, Optional<BlockPos> corner)
{
    public static final FieldSelection EMPTY = new FieldSelection(Optional.empty(), Optional.empty());

    public static final Codec<FieldSelection> CODEC = RecordCodecBuilder.create(
      builder -> builder
                   .group(BlockPos.CODEC.optionalFieldOf("field").forGetter(FieldSelection::field),
                     BlockPos.CODEC.optionalFieldOf("corner").forGetter(FieldSelection::corner))
                   .apply(builder, FieldSelection::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FieldSelection> STREAM_CODEC =
      StreamCodec.composite(ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
        FieldSelection::field,
        ByteBufCodecs.optional(BlockPos.STREAM_CODEC),
        FieldSelection::corner,
        FieldSelection::new);

    /**
     * Point the stick at a field. Any half finished rectangle is dropped, since its corners belonged to the
     * previous field.
     *
     * @param pos the scarecrow position.
     * @return the new selection.
     */
    public FieldSelection withField(final BlockPos pos)
    {
        return new FieldSelection(Optional.of(pos), Optional.empty());
    }

    /**
     * Remember the first corner of a rectangle.
     *
     * @param pos the corner.
     * @return the new selection.
     */
    public FieldSelection withCorner(final BlockPos pos)
    {
        return new FieldSelection(field, Optional.of(pos));
    }

    /**
     * Forget the half finished rectangle, keeping the field.
     *
     * @return the new selection.
     */
    public FieldSelection withoutCorner()
    {
        return new FieldSelection(field, Optional.empty());
    }

    public void writeToItemStack(final ItemStack itemStack)
    {
        itemStack.set(ModDataComponents.FIELD_SELECTION, this);
    }

    public static FieldSelection readFromItemStack(final ItemStack itemStack)
    {
        return itemStack.getOrDefault(ModDataComponents.FIELD_SELECTION, FieldSelection.EMPTY);
    }

    public static void updateItemStack(final ItemStack itemStack, final UnaryOperator<FieldSelection> updater)
    {
        updater.apply(readFromItemStack(itemStack)).writeToItemStack(itemStack);
    }
}
