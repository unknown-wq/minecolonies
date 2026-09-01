package com.minecolonies.core.entity.ai.animals;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tying a farm animal to a fence, the way a player ties one up with a lead.
 * <p>
 * Vanilla machinery throughout: a {@link LeashFenceKnotEntity} found or created at a fence and
 * {@link Leashable#setLeashedTo} to attach the animal to it. Nothing is persisted here because the leash already is -
 * {@code Leashable.LeashData} is written into the animal's own NBT as the knot's block position and re-attached on
 * load by {@code Leashable#restoreLeashFromSave}. No block is ever placed: a pen with no fence in it simply gets no
 * hitches.
 * <p>
 * This is deliberately a second copy of the search that {@code Hitch} does for cavalry horses rather than a shared
 * one. {@code Hitch} is about a mount a rider unties again within the hour and answers to the stable's own
 * bookkeeping; this is about a herd animal that is meant to stand where it was put, is found by a different owner
 * (the hut's own box rather than a horse's parking spot) and has to pass a reachability test the stable does not
 * need. Merging the two would tie two unrelated behaviours to one set of constants.
 * <h2>Who cleans up the knot</h2>
 * A knot is discarded by vanilla from {@code LeashFenceKnotEntity#notifyLeasheeRemoved}, which every
 * {@code dropLeash}/{@code removeLeash} path calls on the old holder, and from its own {@code survives()} check when
 * the fence block goes. Nothing here holds a reference that would outlive either, so there is no bookkeeping to keep:
 * an animal that is butchered, despawned or led away by a player takes its knot with it.
 */
public final class Tether
{
    /**
     * Horizontal search radius for a fence, in blocks.
     * <p>
     * Inside {@code Leashable#leashSnapDistance} (12), so a hitch made at the limit of this box is not already at
     * risk of snapping the moment the animal takes a step.
     */
    private static final int SEARCH_RADIUS = 5;

    /**
     * Vertical search radius for a fence, in blocks. Smaller than the horizontal one because a fence an animal can
     * plausibly be tied to is at its own feet, not two floors up.
     */
    private static final int SEARCH_HEIGHT = 3;

    /**
     * Private constructor to hide the public one.
     */
    private Tether()
    {
        //Hides implicit constructor.
    }

    /**
     * The fence knot an animal is tied to, if it is tied to one at all.
     *
     * @param animal the animal, may be null.
     * @return the knot, or null if the animal is loose or is being led by something that is not a fence.
     */
    @Nullable
    public static LeashFenceKnotEntity knotOf(@Nullable final Entity animal)
    {
        if (!(animal instanceof final Leashable leashable))
        {
            return null;
        }

        return leashable.getLeashHolder() instanceof final LeashFenceKnotEntity knot ? knot : null;
    }

    /**
     * Whether an animal is tied to a fence.
     *
     * @param animal the animal, may be null.
     * @return true if it is on a fence knot.
     */
    public static boolean isTiedToFence(@Nullable final Entity animal)
    {
        return knotOf(animal) != null;
    }

    /**
     * Whether an animal is a candidate for being tied up at all.
     * <p>
     * Asked of the entity rather than of a list of types on purpose: {@code canBeLeashed} is the only honest answer
     * for a species this mod has never heard of, and a herding module can be pointed at any {@code Animal} a content
     * mod registers.
     *
     * @param animal the animal, may be null.
     * @return true if a hitch could be put on it right now.
     */
    public static boolean canTie(@Nullable final Mob animal)
    {
        return animal != null
                 && animal.isAlive()
                 && !animal.level().isClientSide()
                 && !animal.isLeashed()
                 && !animal.isVehicle()
                 && !animal.isPassenger()
                 && animal.canBeLeashed();
    }

    /**
     * Tie an animal to a fence that has already been found for it.
     * <p>
     * Split from {@link #findFence} so that a caller which has to walk to the animal first, and pay for the hitch out
     * of an inventory, can look before it commits.
     *
     * @param animal the animal to tie.
     * @param fence  the fence to tie it to.
     * @return true if the animal ended up tied.
     */
    public static boolean tieTo(@Nullable final Mob animal, @NotNull final BlockPos fence)
    {
        if (!canTie(animal) || !animal.level().getBlockState(fence).is(BlockTags.FENCES))
        {
            return false;
        }

        final LeashFenceKnotEntity knot = LeashFenceKnotEntity.getOrCreateKnot(animal.level(), fence);
        animal.setLeashedTo(knot, true);
        knot.playPlacementSound();
        return true;
    }

    /**
     * Tie an animal to the nearest fence it can reach.
     * <p>
     * Does nothing at all when there is no fence in range - no block is ever placed.
     *
     * @param animal the animal to tie, may be null.
     * @return true if the animal ended up tied to a fence.
     */
    public static boolean tieUp(@Nullable final Mob animal)
    {
        if (!canTie(animal))
        {
            return false;
        }

        final BlockPos fence = findFence(animal);
        return fence != null && tieTo(animal, fence);
    }

    /**
     * The nearest fence an animal could be tied to.
     * <p>
     * Nearest by squared distance rather than first found, and only fences the animal can actually see: a search box
     * this size reaches straight through a wall, and a hitch made on the far side of one leaves the animal hauled up
     * against the wall by the elastic for as long as it lives.
     * <p>
     * The tag is {@code BlockTags#FENCES}, which is the same tag {@code LeashFenceKnotEntity#survives} checks, so a
     * knot made on anything this finds stays put.
     *
     * @param animal the animal being tied up.
     * @return the fence position, or null if there is none in reach.
     */
    @Nullable
    public static BlockPos findFence(@NotNull final Mob animal)
    {
        final Level level = animal.level();
        final BlockPos origin = animal.blockPosition();
        final Vec3 eye = animal.getEyePosition();

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
            if (distance >= bestDistance || !canReach(animal, eye, pos))
            {
                continue;
            }

            bestDistance = distance;
            best = pos.immutable();
        }

        return best;
    }

    /**
     * Whether there is nothing solid between an animal and a fence.
     *
     * @param animal the animal, used as the ray's own exemption.
     * @param eye    where the ray starts.
     * @param fence  the fence being considered.
     * @return true if the fence is the first thing the ray meets, or it meets nothing at all.
     */
    private static boolean canReach(@NotNull final Mob animal, @NotNull final Vec3 eye, @NotNull final BlockPos fence)
    {
        final BlockHitResult hit = animal.level().clip(
          new ClipContext(eye, Vec3.atCenterOf(fence), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, animal));

        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(fence);
    }
}
