package com.minecolonies.core.colony.buildingextensions;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries;
import com.minecolonies.api.colony.buildingextensions.registry.BuildingExtensionRegistries.BuildingExtensionEntry;
import com.minecolonies.api.util.Utils;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.debug.FreeMode;
import com.minecolonies.core.tileentities.TileEntityScarecrow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.minecolonies.api.util.constant.TranslationConstants.FIELD_STATUS;
import net.minecraft.nbt.NbtOps;

/**
 * Field class implementation for the plantation
 */
public class FarmField extends AbstractBuildingExtension
{
    /**
     * The max width/length of a field.
     */
    public static final int MAX_RANGE = 20;
    public static final int DEFAULT_RANGE = 5;

    /**
     * How many radii a field has, one per horizontal {@link Direction}.
     */
    public static final int RADII_COUNT = 4;

    /**
     * The furthest a single radius may ever reach, free mode or not.
     * <p>
     * Not a gameplay number: it is the largest radius {@code EntityAIWorkFarmer#nextValidCell} can index. That walker
     * numbers the cells of the field's bounding square, so it needs {@code (2 * radius + 1)^2} to fit in an int, and
     * 23169 is the largest radius for which it does. A field wider than this cannot be walked at all, so it is
     * refused at the point it would be created rather than misbehaving later.
     */
    public static final int MAX_FREE_RANGE = 23169;

    /**
     * How many different seeds one field may carry at once.
     * <p>
     * Five because that is how many icons the scarecrow window has room for in the row under the seed button, and a
     * mixed field the window cannot show is a field the player cannot manage. It is also a hard cap on the wire and
     * in NBT, so a hostile or corrupt packet cannot make a field with a thousand seeds.
     */
    public static final int MAX_SEEDS = 5;

    private static final String TAG_SEED      = "seed";
    private static final String TAG_SEEDS     = "seeds";
    public static final  String TAG_RADIUS    = "radius";
    private static final String TAG_MAX_RANGE = "maxRange";
    private static final String TAG_STAGE     = "stage";

    /**
     * The seeds this field is sown with, in the order they were chosen, at most {@link #MAX_SEEDS} of them.
     * <p>
     * Empty means the field has no seed and cannot be assigned to a farmer, which is the state the field starts in.
     * One entry is the ordinary monoculture field and behaves exactly as it always did. Several entries make the
     * field a mixed planting: which seed a given cell of ground gets is decided by the farmer, per cell, from the
     * cell's own coordinates - see {@code EntityAIWorkFarmer#seedFor}. The field itself deliberately knows nothing
     * about that rule, so the pattern can be changed without touching saved data.
     */
    private final List<ItemStack> seeds = new ArrayList<>();

    /**
     * The size of the field in all four directions
     * in the same order as {@link Direction}:
     * S, W, N, E
     */
    private int[] radii = {DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE};

    /**
     * Has the field been planted
     */
    private Stage fieldStage = Stage.EMPTY;

    /**
     * Constructor used in NBT deserialization.
     *
     * @param fieldType the type of field.
     * @param position  the position of the field.
     */
    public FarmField(final BuildingExtensionEntry fieldType, final BlockPos position)
    {
        super(fieldType, position);
    }

    /**
     * Constructor to create new instances
     *
     * @param position the position it is placed in.
     * @param worldIn
     */
    public static FarmField create(final BlockPos position, final Level worldIn)
    {
        final FarmField farmField = (FarmField) BuildingExtensionRegistries.farmField.get().produceExtension(position);
        if (farmField != null)
        {
            final BlockEntity fieldBlock = worldIn.getBlockEntity(position);
            if (fieldBlock instanceof TileEntityScarecrow scarecrow)
            {
                farmField.radii = scarecrow.getFieldSize();
            }
        }
        return farmField;
    }

    @Override
    public boolean isValidPlacement(final IColony colony)
    {
        BlockState blockState = colony.getWorld().getBlockState(getPosition());
        return blockState.is(ModBlocks.blockScarecrow);
    }

    @Override
    public @NotNull CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        CompoundTag compound = super.serializeNBT(provider);
        // TAG_SEED is still written, holding the first seed. Nothing in this branch reads it any more when TAG_SEEDS
        // is present, but a save written here and then opened by a build from before mixed seeds existed still finds
        // the field's main crop where it expects it, rather than a field that has silently lost its seed.
        compound.put(TAG_SEED, ItemStack.OPTIONAL_CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), getSeed()).getOrThrow());
        final ListTag seedList = new ListTag();
        for (final ItemStack stack : seeds)
        {
            seedList.add(ItemStack.OPTIONAL_CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow());
        }
        compound.put(TAG_SEEDS, seedList);
        compound.putIntArray(TAG_RADIUS, radii);
        compound.putString(TAG_STAGE, fieldStage.name());
        return compound;
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final @NotNull CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);
        // A save from before mixed seeds carries only TAG_SEED. Absence of TAG_SEEDS is therefore not "no seeds", it
        // is "one seed, over there" - reading it the other way round would wipe the seed off every existing field on
        // the first world load.
        if (compound.contains(TAG_SEEDS))
        {
            final ListTag seedList = compound.getListOrEmpty(TAG_SEEDS);
            final List<ItemStack> read = new ArrayList<>();
            for (int i = 0; i < seedList.size(); i++)
            {
                read.add(ItemStack.OPTIONAL_CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), seedList.getCompoundOrEmpty(i))
                           .result()
                           .orElse(ItemStack.EMPTY));
            }
            setSeeds(read);
        }
        else
        {
            setSeed(ItemStack.OPTIONAL_CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), compound.getCompoundOrEmpty(TAG_SEED))
                      .result()
                      .orElse(ItemStack.EMPTY));
        }
        radii = sanitiseRadii(compound.getIntArray(TAG_RADIUS).orElse(null));
        fieldStage = Stage.valueOf(compound.getStringOr(TAG_STAGE, ""));
    }

    @Override
    public void serialize(final @NotNull RegistryFriendlyByteBuf buf)
    {
        super.serialize(buf);
        buf.writeVarInt(seeds.size());
        for (final ItemStack stack : seeds)
        {
            Utils.serializeCodecMess(buf, stack);
        }
        buf.writeVarIntArray(radii);
        buf.writeEnum(fieldStage);
    }

    @Override
    public void deserialize(@NotNull final RegistryFriendlyByteBuf buf)
    {
        super.deserialize(buf);
        final int count = Math.max(0, Math.min(buf.readVarInt(), MAX_SEEDS));
        final List<ItemStack> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
        {
            read.add(Utils.deserializeCodecMess(buf));
        }
        setSeeds(read);
        radii = sanitiseRadii(buf.readVarIntArray());
        fieldStage = buf.readEnum(Stage.class);
    }

    /**
     * Get the field's main seed - the first of its seeds.
     * <p>
     * This is what the hut's field list and the scarecrow window show as "the crop of this field", and what every
     * "does this field have a seed at all" test asks. On a field with one seed it is that seed and nothing has
     * changed. On a mixed field it is only the first of several, so it is <b>not</b> the right thing to ask when
     * deciding what to put in a particular cell of ground - that is {@link #getSeeds()} plus the farmer's per cell
     * rule.
     *
     * @return the first seed, or an empty stack if the field has none.
     */
    @NotNull
    public ItemStack getSeed()
    {
        if (seeds.isEmpty())
        {
            return ItemStack.EMPTY;
        }
        final ItemStack first = seeds.get(0);
        first.setCount(1);
        return first;
    }

    /**
     * All the seeds this field is sown with.
     *
     * @return an unmodifiable view of the live list, never null, possibly empty.
     */
    @NotNull
    public List<ItemStack> getSeeds()
    {
        return Collections.unmodifiableList(seeds);
    }

    /**
     * Updates the seed in the field, discarding any other seed it had.
     *
     * @param seed the new seed
     */
    public void setSeed(final ItemStack seed)
    {
        setSeeds(List.of(seed));
    }

    /**
     * Replace the whole set of seeds this field is sown with.
     * <p>
     * The list is normalised rather than trusted: empty stacks are dropped, a repeated item is kept once (a field
     * that lists wheat twice would simply give wheat two of the shares of ground, which is a rule nobody asked for
     * and cannot be seen in the window), counts are forced to one, and anything past {@link #MAX_SEEDS} is cut. Both
     * the network message and the command hand their input straight here, so this is the one place the invariant is
     * kept.
     *
     * @param newSeeds the wanted seeds, in order of preference.
     */
    public void setSeeds(final List<ItemStack> newSeeds)
    {
        if (newSeeds == null)
        {
            seeds.clear();
            return;
        }
        // Copied before the clear: getSeeds hands out a view of the live list, so setSeeds(getSeeds()) - which is
        // what "remove one seed" looks like at every call site - would otherwise empty the very list it is reading.
        final List<ItemStack> wanted = new ArrayList<>(newSeeds);
        seeds.clear();
        for (final ItemStack candidate : wanted)
        {
            if (candidate == null || candidate.isEmpty() || seeds.size() >= MAX_SEEDS)
            {
                continue;
            }
            if (seeds.stream().anyMatch(existing -> ItemStack.isSameItem(existing, candidate)))
            {
                continue;
            }
            final ItemStack copy = candidate.copy();
            copy.setCount(1);
            seeds.add(copy);
        }
    }

    /**
     * Move the field into the new state.
     */
    public void nextState()
    {
        if (getFieldStage().ordinal() + 1 >= Stage.values().length)
        {
            setFieldStage(Stage.values()[0]);
            return;
        }
        setFieldStage(Stage.values()[getFieldStage().ordinal() + 1]);
    }

    /**
     * Get the current stage the field is in.
     *
     * @return the stage of the field.
     */
    public Stage getFieldStage()
    {
        return this.fieldStage;
    }

    /**
     * Sets the current stage of the field.
     *
     * @param fieldStage the stage of the field.
     */
    public void setFieldStage(final Stage fieldStage)
    {
        this.fieldStage = fieldStage;
    }

    /**
     * @param direction the direction to get the range for
     * @return the radius
     */
    public int getRadius(Direction direction)
    {
        return radii[direction.get2DDataValue()];
    }

    /**
     * The four radii of this field, in {@link Direction#get2DDataValue} order.
     *
     * @return the backing array, do not write to it.
     */
    public int[] getRadii()
    {
        return radii;
    }

    @Override
    public long getFootprintDistanceSq(@NotNull final BlockPos from)
    {
        // The nearest block of the plot, not the scarecrow. With free mode a field may be a 4x1000 strip whose
        // scarecrow sits 500 blocks from either end, so scarecrow distance would call a hut standing on the field
        // "500 blocks away" and no sane claim range would ever let it be claimed.
        final long minX = (long) getPosition().getX() - radii[Direction.WEST.get2DDataValue()];
        final long maxX = (long) getPosition().getX() + radii[Direction.EAST.get2DDataValue()];
        final long minZ = (long) getPosition().getZ() - radii[Direction.NORTH.get2DDataValue()];
        final long maxZ = (long) getPosition().getZ() + radii[Direction.SOUTH.get2DDataValue()];

        final long dx = Math.max(0, Math.max(minX - from.getX(), from.getX() - maxX));
        final long dz = Math.max(0, Math.max(minZ - from.getZ(), from.getZ() - maxZ));
        return dx * dx + dz * dz;
    }

    /**
     * The four radii a rectangle of ground implies for a field anchored at this scarecrow.
     *
     * @param cornerA one corner of the rectangle, only its X and Z are read.
     * @param cornerB the opposite corner, likewise.
     * @return the four radii in {@link Direction#get2DDataValue} order, or null if the rectangle does not contain
     *         the scarecrow, which is the one shape that cannot be expressed as radii around it.
     */
    @Nullable
    public int[] radiiForRectangle(final BlockPos cornerA, final BlockPos cornerB)
    {
        final int minX = Math.min(cornerA.getX(), cornerB.getX());
        final int maxX = Math.max(cornerA.getX(), cornerB.getX());
        final int minZ = Math.min(cornerA.getZ(), cornerB.getZ());
        final int maxZ = Math.max(cornerA.getZ(), cornerB.getZ());

        final int anchorX = getPosition().getX();
        final int anchorZ = getPosition().getZ();
        if (anchorX < minX || anchorX > maxX || anchorZ < minZ || anchorZ > maxZ)
        {
            return null;
        }

        final int[] wanted = new int[RADII_COUNT];
        wanted[Direction.WEST.get2DDataValue()] = anchorX - minX;
        wanted[Direction.EAST.get2DDataValue()] = maxX - anchorX;
        wanted[Direction.NORTH.get2DDataValue()] = anchorZ - minZ;
        wanted[Direction.SOUTH.get2DDataValue()] = maxZ - anchorZ;
        return wanted;
    }

    /**
     * @param direction the direction for the radius
     * @param radius    the number of blocks from the scarecrow that the farmer will work with
     */
    public void setRadius(final Direction direction, final int radius)
    {
        // Only the absolute ceiling is applied here, not the colony's limit. Whether a field may be this size is one
        // decision and it is made in one place - FarmFieldPlotResizeMessage - so that shrinking an oversized field
        // hands back the size that was asked for rather than snapping to whatever the current mode's maximum is.
        this.radii[direction.get2DDataValue()] = clampRadius(radius);
    }

    /**
     * Hold a radius inside the range the field walker can index, whatever it was asked for.
     *
     * @param radius the wanted radius.
     * @return a radius that is safe to store.
     */
    public static int clampRadius(final int radius)
    {
        return Math.max(0, Math.min(radius, MAX_FREE_RANGE));
    }

    /**
     * The largest farm field, counted in blocks of ground, that may be laid out in a colony.
     * <p>
     * Free mode drops the "sum of the four radii" rule that caps an ordinary field at 11x11 and replaces it with a
     * limit on the area alone, so any shape fits as long as width times depth stays inside it. That is what makes a
     * 4x1000 strip expressible at all: no arrangement of four radii summing to twenty can describe one.
     *
     * @param colony the colony the field sits in. May be null outside a colony.
     * @return the largest allowed area in blocks.
     */
    public static int getMaxArea(@Nullable final IColony colony)
    {
        if (!FreeMode.isOn(colony))
        {
            // The ordinary rule, expressed as an area so that one check covers both modes: the four radii may sum to
            // MAX_RANGE, and among all shapes that satisfy that, the square one has the largest area - half the
            // allowance each way, so (MAX_RANGE / 2 + 1) squared, which is 11 x 11.
            //
            // This used to be (MAX_RANGE + 1) squared = 441, an area no field obeying the sum rule can reach. It made
            // no difference to isSizeAllowed, where the sum rule is the strict one, but it is also the number quoted
            // in the "field too large" refusal, and a player told a 15 x 9 field was refused because "this colony
            // allows at most 441 blocks" has been told something untrue.
            final int side = MAX_RANGE / 2 + 1;
            return side * side;
        }
        return MineColonies.getConfig().getServer().freeModeMaxFieldArea.get();
    }

    /**
     * The largest a single radius may be.
     * <p>
     * With free mode on this is only the degenerate case of the area limit - a field one block deep may be the whole
     * allowance wide - and the real check is {@link #isSizeAllowed}. Without it the old per direction cap stands.
     *
     * @param colony the colony the field sits in. May be null outside a colony.
     * @return the largest allowed radius in blocks.
     */
    public static int getMaxRadius(@Nullable final IColony colony)
    {
        if (!FreeMode.isOn(colony))
        {
            return MAX_RANGE;
        }
        return Math.min(getMaxArea(colony) - 1, MAX_FREE_RANGE);
    }

    /**
     * The area, in blocks of ground, a set of radii covers.
     *
     * @param radii the four radii, in {@link Direction#get2DDataValue} order.
     * @return the area, or zero if the array is not a well formed set of radii.
     */
    public static int getArea(final int[] radii)
    {
        if (radii == null || radii.length < RADII_COUNT)
        {
            return 0;
        }

        // Long arithmetic on purpose: two radii near Integer.MAX_VALUE, which is exactly what a hostile resize packet
        // would carry, overflow an int product into a small or negative number and would pass the limit check.
        final long width = 1L + radii[Direction.WEST.get2DDataValue()] + radii[Direction.EAST.get2DDataValue()];
        final long depth = 1L + radii[Direction.NORTH.get2DDataValue()] + radii[Direction.SOUTH.get2DDataValue()];
        final long area = width * depth;
        return area > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) area;
    }

    /**
     * Whether a set of radii describes a field a colony is allowed to have.
     *
     * @param radii  the four radii, in {@link Direction#get2DDataValue} order.
     * @param colony the colony the field sits in. May be null outside a colony.
     * @return true if the field may be that size.
     */
    public static boolean isSizeAllowed(final int[] radii, @Nullable final IColony colony)
    {
        if (radii == null || radii.length < RADII_COUNT)
        {
            return false;
        }

        for (final int radius : radii)
        {
            if (radius < 0 || radius > getMaxRadius(colony))
            {
                return false;
            }
        }

        if (!FreeMode.isOn(colony))
        {
            // Unchanged behaviour without free mode: the four radii together may not exceed MAX_RANGE, which is a
            // stricter rule than the area and is what the vanilla scarecrow window has always enforced.
            int sum = 0;
            for (final int radius : radii)
            {
                sum += radius;
            }
            if (sum > MAX_RANGE)
            {
                return false;
            }
        }

        return getArea(radii) <= getMaxArea(colony);
    }

    /**
     * Turn whatever was stored for the four radii into something the rest of the code can rely on.
     * <p>
     * Two separate jobs, and the difference matters:
     * <ul>
     *     <li><b>Shape.</b> A missing, short or over long array is replaced by four default radii. Upstream trusted
     *     the tag and {@code getRadius} threw {@link ArrayIndexOutOfBoundsException} on a save that did not carry
     *     one; free mode does not cause that, but it does make the array worth reading twice.</li>
     *     <li><b>Size.</b> A field laid out in free mode keeps its size when free mode is turned back off. It is
     *     <b>not</b> truncated: silently cutting a player's 4x1000 strip down to 11x11 on the next world load would
     *     destroy work with no way back, and nothing downstream needs the field to be small - the farmer walks the
     *     radii it is given. What free mode gates is <b>growing</b> a field, which
     *     {@code FarmFieldPlotResizeMessage} refuses, so an oversized field can only ever shrink once the switch is
     *     off. The only clamp applied here is the absolute one that keeps a corrupt or hostile value from producing
     *     a field with a radius of two billion.</li>
     * </ul>
     *
     * @param stored what was read from disk or off the wire, may be null.
     * @return four usable radii.
     */
    private static int[] sanitiseRadii(@Nullable final int[] stored)
    {
        if (stored == null || stored.length != RADII_COUNT)
        {
            return new int[] {DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE, DEFAULT_RANGE};
        }

        final int ceiling = MAX_FREE_RANGE;
        final int[] clamped = Arrays.copyOf(stored, RADII_COUNT);
        for (int i = 0; i < RADII_COUNT; i++)
        {
            clamped[i] = Math.max(0, Math.min(clamped[i], ceiling));
        }
        return clamped;
    }

    /**
     * Checks if a certain position is part of the field. Complies with the definition of field block.
     *
     * @param world    the world object.
     * @param position the position.
     * @return true if it is.
     */
    public boolean isNoPartOfField(@NotNull final Level world, @NotNull final BlockPos position)
    {
        return world.isEmptyBlock(position) || isValidDelimiter(world.getBlockState(position.above()).getBlock());
    }

    /**
     * Check if a block is a valid delimiter of the field.
     *
     * @param block the block to analyze.
     * @return true if so.
     */
    private static boolean isValidDelimiter(final Block block)
    {
        return block instanceof FenceBlock || block instanceof FenceGateBlock || block instanceof WallBlock;
    }

    /**
     * Describes the stage the field is in. Like if it has been hoed, planted or is empty.
     */
    public enum Stage
    {
        EMPTY(Identifier.fromNamespaceAndPath("minecraft", "textures/item/iron_hoe.png")), 
        HOED(Identifier.fromNamespaceAndPath("minecraft", "textures/item/wheat_seeds.png")), 
        PLANTED(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "textures/item/crops/durum.png"));

        protected final Identifier stageIcon;

        private Stage(Identifier stageIcon)
        {
            this.stageIcon = stageIcon;
        }

        /**
         * Gets the status icon of the current stage in the farm field's progress.
         *
         * @return the status icon of the current stage.
         */
        public Identifier getStageIcon()
        {
            return stageIcon;
        }

        /**
         * Gets the translatable text of the current stage in the farm field's progress.
         * 
         * @return the translatable text of the current stage.
         */
        public Component getStageText()
        {
            return Component.translatable(FIELD_STATUS + "." + name().toLowerCase(Locale.ROOT));
        }


        /**
         * Get the next stage in the field's progression.
         *
         * @return the next Stage, or the first Stage if the current one is the last.
         */
        public Stage getNextStage()
        {
            if (ordinal() + 1 >= values().length)
            {
                return values()[0];
            }
            return values()[ordinal() + 1];
        }
    }
}
