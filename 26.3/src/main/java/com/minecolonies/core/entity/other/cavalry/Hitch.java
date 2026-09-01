package com.minecolonies.core.entity.other.cavalry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Tying a cavalry horse to a fence, the way a player ties a mount up.
 * <p>
 * The whole feature is reachable from this one symbol - {@code grep -rn Hitch src/main/java} is a complete inventory
 * of it. It is vanilla's own machinery throughout: a {@link LeashFenceKnotEntity} found or created at a fence, and
 * {@link Leashable#setLeashedTo} to attach the horse to it. Nothing is persisted here, because the leash already is:
 * {@code Leashable.LeashData} is written into the horse's own NBT as the knot's block position and re-attached on
 * load by {@code Leashable#restoreLeashFromSave}.
 * <p>
 * No lead item is spent. The hitch is behaviour, not an economy sink, so nothing is dropped when it comes off either
 * - see {@link CavalryHorseEntity#leashTooFarBehaviour()} and {@link CavalryHorseEntity#dropLeash()}, which route a
 * fence hitch through {@link #untie} rather than through vanilla's spawn-a-lead path.
 * <h2>Why the knot does not leak</h2>
 * A knot is discarded by vanilla only from {@code LeashFenceKnotEntity#notifyLeasheeRemoved}, which
 * {@code Leashable#dropLeash} calls on the old holder, and from its own {@code survives()} check when the fence goes.
 * Nothing in vanilla cleans up after a leashed mob that is simply removed from the world, so every path that ends a
 * hitch has to be walked back to a {@code removeLeash()} call. They are:
 * <ul>
 *     <li>the guard wakes - {@code EntityAICavalry#stopSleeping};</li>
 *     <li>the guard remounts - {@code CavalryHorseEntity#addPassenger};</li>
 *     <li>the horse dies - {@code CavalryHorseEntity#die};</li>
 *     <li>the horse is discarded, which is what colony deletion and {@code /kill} do -
 *     {@code CavalryHorseEntity#onRemoval};</li>
 *     <li>the horse changes dimension - vanilla's own {@code Entity#removeAfterChangingDimensions};</li>
 *     <li>the leash snaps or the fence is broken - {@code CavalryHorseEntity#leashTooFarBehaviour} and
 *     {@code CavalryHorseEntity#dropLeash}.</li>
 * </ul>
 * A chunk unload is deliberately not on that list: the horse is removed with {@code UNLOADED_TO_CHUNK}, which saves
 * rather than destroys, and the knot is an ordinary saved entity in the fence's own chunk. Both come back and
 * {@code restoreLeashFromSave} re-links them. The one thing that can go wrong there is the horse's chunk loading
 * while the fence's has not, in which case {@code getOrCreateKnot} makes a second knot at the same position; that is
 * why {@link #untie} sweeps every knot at the position rather than trusting the one the horse happened to hold.
 */
public final class Hitch
{
    /**
     * Horizontal search radius for a fence, in blocks.
     * <p>
     * Well inside {@code Leashable#leashSnapDistance} (12) and inside the elastic distance (6) too, so a hitch made
     * at the limit of this box does not start out already fighting the spring.
     */
    private static final int SEARCH_RADIUS = 5;

    /**
     * Vertical search radius for a fence, in blocks. Smaller than the horizontal one because a fence a horse can
     * plausibly be tied to is at its own feet, not three floors up.
     */
    private static final int SEARCH_HEIGHT = 3;

    /**
     * Private constructor to hide the public one.
     */
    private Hitch()
    {
        //Hides implicit constructor.
    }

    /**
     * The fence knot a horse is tied to, if it is tied to one at all.
     *
     * @param horse the horse, may be null.
     * @return the knot, or null if the horse is loose or is being led by something that is not a fence.
     */
    @Nullable
    public static LeashFenceKnotEntity knotOf(@Nullable final Entity horse)
    {
        if (!(horse instanceof final Leashable leashable))
        {
            return null;
        }

        return leashable.getLeashHolder() instanceof final LeashFenceKnotEntity knot ? knot : null;
    }

    /**
     * Whether a horse is tied to a fence.
     *
     * @param horse the horse, may be null.
     * @return true if it is on a fence knot.
     */
    public static boolean isTiedToFence(@Nullable final Entity horse)
    {
        return knotOf(horse) != null;
    }

    /**
     * Tie a horse to the nearest fence.
     * <p>
     * Does nothing at all when there is no fence in range - no block is ever placed - and nothing when the horse is
     * carrying a rider, because a leash on a ridden mount is a fight between the elastic and the rider's navigation
     * that ends with the leash snapping the first time the pair goes anywhere.
     *
     * @param horse the horse to tie.
     * @return true if the horse ended up tied to a fence, including when it already was.
     */
    public static boolean tieUp(@Nullable final CavalryHorseEntity horse)
    {
        if (horse == null || !horse.isAlive() || horse.level().isClientSide())
        {
            return false;
        }

        if (!horse.getPassengers().isEmpty() || horse.isPassenger())
        {
            return false;
        }

        if (horse.isLeashed())
        {
            // Already tied, or being led by the Stablemaster. Either way it is somebody else's rope.
            return isTiedToFence(horse);
        }

        if (!horse.canBeLeashed())
        {
            return false;
        }

        final BlockPos fence = findFence(horse.level(), horse.blockPosition());
        if (fence == null)
        {
            return false;
        }

        final LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(horse.level(), fence);
        horse.setLeashedTo(knot, true);
        knot.playPlacementSound();
        return true;
    }

    /**
     * Untie a horse from its fence and make sure no knot is left standing on it.
     * <p>
     * {@code removeLeash} rather than {@code dropLeash} because no lead was spent to make the hitch; it still runs
     * the same {@code notifyLeasheeRemoved} that discards an empty knot. The sweep afterwards is for the duplicate
     * knot a split chunk load can leave behind, and costs one entity query against a two block box.
     *
     * @param horse the horse to untie, may be null.
     * @return true if the horse was tied to a fence and is not any more.
     */
    public static boolean untie(@Nullable final CavalryHorseEntity horse)
    {
        if (horse == null || horse.level().isClientSide())
        {
            return false;
        }

        final LeashFenceKnotEntity knot = knotOf(horse);
        if (knot == null)
        {
            return false;
        }

        final BlockPos pos = knot.getPos();
        horse.removeLeash();
        sweep(horse.level(), pos);
        return true;
    }

    /**
     * Discard every knot at a position that has nothing attached to it any more.
     *
     * @param level the level.
     * @param pos   the fence position the knot or knots sit on.
     */
    private static void sweep(@NotNull final Level level, @NotNull final BlockPos pos)
    {
        final AABB box = new AABB(pos.getX() - 1.0, pos.getY() - 1.0, pos.getZ() - 1.0, pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);

        for (final LeashFenceKnotEntity knot : level.getEntitiesOfClass(LeashFenceKnotEntity.class, box))
        {
            if (!knot.isRemoved() && knot.getPos().equals(pos) && Leashable.leashableLeashedTo(knot).isEmpty())
            {
                knot.discard();
            }
        }
    }

    /**
     * The nearest fence to a position, within the search box.
     * <p>
     * The tag is {@code BlockTags#FENCES}, which is the same tag {@code LeashFenceKnotEntity#survives} checks, so a
     * knot made on anything this finds stays put.
     *
     * @param level  the level.
     * @param origin where the horse is being parked.
     * @return the fence position, or null if there is none in range.
     */
    @Nullable
    private static BlockPos findFence(@NotNull final Level level, @NotNull final BlockPos origin)
    {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (final BlockPos pos : BlockPos.betweenClosed(
          origin.offset(-SEARCH_RADIUS, -SEARCH_HEIGHT, -SEARCH_RADIUS),
          origin.offset(SEARCH_RADIUS, SEARCH_HEIGHT, SEARCH_RADIUS)))
        {
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(BlockTags.FENCES))
            {
                continue;
            }

            final double distance = pos.distSqr(origin);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = pos.immutable();
            }
        }

        return best;
    }
}
