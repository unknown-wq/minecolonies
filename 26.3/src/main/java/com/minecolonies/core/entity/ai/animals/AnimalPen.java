package com.minecolonies.core.entity.ai.animals;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.managers.interfaces.IManagedAnimal;
import com.minecolonies.api.util.EntityUtils;
import com.minecolonies.api.util.Tuple;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.buildings.modules.AnimalHerdingModule;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingStable;
import com.minecolonies.core.debug.FreeMode;
import com.minecolonies.core.entity.ai.workers.production.herders.AbstractEntityAIHerder;
import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.minecolonies.api.util.constant.SchematicTagConstants.TAG_GROUNDLEVEL;

/**
 * Keeps the animals of a herder hut inside that hut, and - in free mode - puts them there in the first place.
 * <p>
 * <h3>What the pen is</h3>
 * There is no pen anywhere in the mod's data. A herder hut's idea of "my animals" is a single expression,
 * {@code WorldUtil#getEntitiesWithinBuilding}, which is the axis aligned box between the blueprint's two corners; the
 * cowboy milks, breeds, feeds and butchers whatever is inside that box and is blind to everything outside it. So that
 * box is the pen, and this class does not invent a second notion of one. It only turns the box into the sphere that
 * vanilla's home restriction understands: same centre, radius large enough that every corner of the box is still
 * inside it, plus {@code animalpenslack} blocks of elbow room.
 * <p>
 * <h3>How the containment works</h3>
 * Vanilla mobs carry a home ({@code Mob#setHomeTo}) and every wander goal in the game refuses to pick a destination
 * outside it - {@code WaterAvoidingRandomStrollGoal} through {@code DefaultRandomPos#getPos}, the block-seeking goals
 * through {@code MoveToBlockGoal}. Farm animals simply never have one set. Setting it is therefore not new behaviour
 * bolted onto a vanilla mob but the switch vanilla already has, thrown. Three properties of it are load bearing and
 * were read out of the 26.2 sources rather than remembered:
 * <ul>
 *     <li>it is saved to entity NBT ({@code home_pos}, {@code home_radius}), so it survives chunk unload, chunk
 *     reload and a server restart with no bookkeeping of ours;</li>
 *     <li>{@code Mob#onLeashRemoved} clears it, so a player who leashes an animal, walks it out and unleashes it has
 *     taken it out of the pen - deliberately, permanently, and without a command;</li>
 *     <li>{@code GoalUtils#mobRestricted} only applies the restriction while the animal is within roughly its home
 *     radius of home. An animal that has been carried, pushed or frightened out is <em>not</em> pulled back by it.
 *     That gap is what {@link PenReturnGoal} and, past {@code animalpenrecalldistance}, the recall below are for.</li>
 * </ul>
 * <p>
 * <h3>What is deliberately left alone</h3>
 * An animal that is leashed, ridden or ridable-with-a-passenger is skipped entirely: something else is steering it.
 * So is anything implementing {@link IManagedAnimal} - the stable's {@link
 * com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity} keeps its own home while the stablemaster leads it
 * about and while cavalry rides it on patrol, and a second writer to the same field would fight it.
 */
public final class AnimalPen
{
    /**
     * Smallest pen radius. A hut whose blueprint corners have collapsed to a single block (which
     * {@code AbstractSchematicProvider#getCorners} returns when it has no blueprint data yet) would otherwise pin its
     * animals to one column and they would never be able to stand anywhere legal.
     */
    private static final int MIN_RADIUS = 4;

    /**
     * How far outside the pen an animal may be before it counts as a stray. Not zero, because the containment sphere
     * circumscribes the hut's box: an animal standing in the box's corner is legitimately at the far edge of the
     * sphere and must not flicker between penned and stray.
     */
    private static final int STRAY_MARGIN = 2;

    /**
     * Extra blocks scanned around the pen when looking for strays, over and above {@code animalpenrecalldistance}.
     * Strays are found by an entity query, and an animal exactly at the recall distance has to be inside the box for
     * the query to see it at all.
     */
    private static final int SCAN_MARGIN = 16;

    /**
     * How many animals free mode conjures per colony tick, i.e. per 500 world ticks.
     * <p>
     * Deliberately not "fill to the cap at once". The herder butchers back down towards the same cap it is filled to
     * ({@code AbstractEntityAIHerder#chanceToButcher}), so an instant refill would put a hut with breeding on into a
     * spawn-and-slaughter loop running at colony tick rate. Two at a time fills a level 5 hut in about two minutes
     * and keeps the loop, where it happens at all, down to a trickle.
     */
    private static final int MAX_STOCK_PER_TICK = 2;

    /**
     * How many spots are tried when looking for somewhere to put a conjured animal before giving up for this tick.
     */
    private static final int SPAWN_ATTEMPTS = 8;

    /**
     * Priority the return goal is inserted at. Above the wander goal of all five farm species (5 for cows, chickens
     * and sheep-after-grass, 6 for pigs and rabbits) and below panic, breeding and temptation, so a player holding
     * wheat still outranks the pen and a burning animal still runs.
     */
    private static final int GOAL_PRIORITY = 4;

    /**
     * Private constructor to hide the public one.
     */
    private AnimalPen()
    {
        //Hides implicit constructor.
    }

    /**
     * Sweep one herder hut: adopt the animals standing in it, fetch back the ones that have left, and in free mode
     * top the hut up to the population its own worker would keep.
     * <p>
     * Called from {@link AnimalHerdingModule#onColonyTick}, so once per 500 world ticks per herder hut and only for
     * huts whose position is loaded ({@code RegisteredStructureManager#onColonyTick} checks that first). The whole
     * sweep is one entity query over a box around the hut; there is deliberately no per-animal tick anywhere in this
     * feature.
     *
     * @param module the herding module being ticked.
     */
    public static void onColonyTick(@NotNull final AnimalHerdingModule module)
    {
        final IBuilding building = module.getBuilding();
        if (building == null || building.getBuildingLevel() <= 0 || module.getPenAnimals().isEmpty())
        {
            return;
        }

        final IColony colony = building.getColony();
        if (colony == null || !(colony.getWorld() instanceof final ServerLevel level))
        {
            return;
        }

        final Pen pen = Pen.of(building);
        final boolean contain = MineColonies.getConfig().getServer().animalPenContainment.get();
        final int recallDistance = MineColonies.getConfig().getServer().animalPenRecallDistance.get();

        // With containment off the sweep has two much smaller jobs - count the herd for free mode, and hand back any
        // animal a previous run had claimed - and neither of them looks past the hut itself.
        final double scan = pen.radius() + (contain ? recallDistance + SCAN_MARGIN : 0);
        final List<Animal> animals = level.getEntitiesOfClass(Animal.class,
          AABB.ofSize(Vec3.atCenterOf(pen.centre()), scan * 2, scan * 2, scan * 2),
          animal -> animal.isAlive() && module.isCompatible(animal));

        int inside = 0;
        for (final Animal animal : animals)
        {
            final boolean isInside = pen.footprint().contains(animal.position());
            if (isInside)
            {
                // Counted before anything is skipped, because this is the number the worker's own culling measures
                // against: a stable full of trained cavalry steeds is a full stable even though none of them is ours
                // to contain.
                inside++;
            }

            if (animal instanceof IManagedAnimal || animal.isLeashed() || animal.isVehicle() || animal.isPassenger())
            {
                continue;
            }

            if (!contain)
            {
                release(pen, animal);
            }
            else if (isInside)
            {
                claim(pen, animal);
            }
            else if (pen.owns(animal))
            {
                claim(pen, animal);
                if (recallDistance > 0 && pen.distanceOutside(animal) > recallDistance)
                {
                    recall(level, pen, animal);
                }
            }
        }

        if (FreeMode.isOn(building))
        {
            stock(level, building, module, pen, inside, contain);
        }
    }

    /**
     * Give an animal that has just been loaded its way home back.
     * <p>
     * The home itself is in the entity's NBT and needs nothing doing to it; the goal that walks a strayed animal back
     * is not persistable and has to be re-attached. Doing it here rather than from the hut's sweep matters for
     * exactly one case, but it is the case that loses animals for good: an animal that is already outside the box the
     * sweep scans. Nothing would ever look at it again, and it would keep drifting.
     * <p>
     * The test is loose on purpose - any animal with any home, whoever set it. A home set by something else (a leash,
     * another mod) leaves the goal attached but inert, because {@code MoveTowardsRestrictionGoal#canUse} starts by
     * asking {@code isWithinHome()}, which is unconditionally true for an animal with no home radius, and
     * {@link PenReturnGoal} additionally stands down while the animal is leashed.
     *
     * @param animal the animal that was just added to the level.
     */
    public static void onAnimalLoaded(@NotNull final Animal animal)
    {
        if (animal.hasHome() && !(animal instanceof IManagedAnimal) && MineColonies.getConfig().getServer().animalPenContainment.get())
        {
            addReturnGoal(animal);
        }
    }

    /**
     * Point an animal at a pen and make sure it can find its way back to it.
     *
     * @param pen    the pen.
     * @param animal the animal.
     */
    private static void claim(@NotNull final Pen pen, @NotNull final Animal animal)
    {
        animal.setHomeTo(pen.centre(), pen.radius());
        addReturnGoal(animal);
    }

    /**
     * Hand an animal back its freedom, so that switching {@code animalpencontainment} off actually undoes the
     * containment instead of only stopping it spreading.
     * <p>
     * Without this the switch would be a lie for animals already claimed: the home is vanilla's field, saved in their
     * NBT, and vanilla's wander goals go on obeying it whatever a MineColonies config says. The goal is not removed
     * along with it because it costs nothing to leave - it asks {@code isWithinHome()} first, which is unconditionally
     * true for an animal with no home - and because it will be needed again unaltered if the switch goes back on.
     *
     * @param pen    the pen.
     * @param animal the animal.
     */
    private static void release(@NotNull final Pen pen, @NotNull final Animal animal)
    {
        if (pen.owns(animal))
        {
            animal.clearHome();
        }
    }

    /**
     * Attach the return goal unless it is already attached.
     * <p>
     * Idempotence is the whole point: this runs against the same animals every colony tick, and
     * {@code GoalSelector#addGoal} appends without checking, so an unguarded call would pile up a goal per sweep for
     * as long as the animal lives.
     *
     * @param animal the animal.
     */
    private static void addReturnGoal(@NotNull final PathfinderMob animal)
    {
        for (final WrappedGoal goal : animal.goalSelector.getAvailableGoals())
        {
            if (goal.getGoal() instanceof PenReturnGoal)
            {
                return;
            }
        }
        animal.goalSelector.addGoal(GOAL_PRIORITY, new PenReturnGoal(animal));
    }

    /**
     * Put an animal back in its pen by hand.
     * <p>
     * Inelegant, and kept because it is the only part of this that cannot fail. Walking home depends on there being a
     * path home; an animal that fell down a ravine, got shoved across a river or was carried off in a boat has none,
     * and a pen that leaks one animal a week still empties itself. It fires only past
     * {@code animalpenrecalldistance} blocks outside the pen, which is far enough that a player standing at the fence
     * never sees it happen.
     *
     * @param level  the level.
     * @param pen    the pen.
     * @param animal the animal.
     */
    private static void recall(@NotNull final ServerLevel level, @NotNull final Pen pen, @NotNull final Animal animal)
    {
        final BlockPos spot = findSpot(level, pen, true);
        if (spot == null)
        {
            return;
        }

        animal.getNavigation().stop();
        animal.teleportTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D);
    }

    /**
     * Conjure animals into a hut that is short of them, free mode only.
     * <p>
     * The ceiling is the herder's own, {@code building level * ANIMAL_MULTIPLIER} - the same number
     * {@code AbstractEntityAIHerder#chanceToButcher} measures the herd against. Filling past it would only hand the
     * worker more to butcher.
     *
     * @param level    the level.
     * @param building the hut.
     * @param module   the herding module, which names the animals this hut is for.
     * @param pen      the pen.
     * @param current  how many of them are already in it.
     * @param contain  whether containment is on, i.e. whether the new arrivals get a home as well as a body.
     */
    private static void stock(
      @NotNull final ServerLevel level,
      @NotNull final IBuilding building,
      @NotNull final AnimalHerdingModule module,
      @NotNull final Pen pen,
      final int current,
      final boolean contain)
    {
        // A stable counts differently. Everywhere else the ceiling is measured against what is standing in the hut,
        // which is the same number the worker's culling pass measures - an animal that has strayed is not the hut's
        // problem. But a cavalry horse is meant to be out: it patrols with its rider and strolls off on its own, and
        // EntityAIWorkStablemaster measures its own 2-per-level cap with getAnimalsOfClassByHome, which counts them
        // wherever they are. Using the footprint count here instead would conjure a replacement for every horse that
        // happened to be away, and the herd would grow without bound. Observed: a level 5 stable reached twelve
        // mounts against a cap of ten within two minutes.
        final int held = building instanceof BuildingStable
                           ? building.getColony().getAnimalManager().getAnimalsOfClassByHome(CavalryHorseEntity.class, building).size()
                           : current;
        final int wanted = Math.min(building.getBuildingLevel() * AbstractEntityAIHerder.ANIMAL_MULTIPLIER - held, MAX_STOCK_PER_TICK);
        final List<EntityType<? extends Animal>> types = module.getPenAnimals();

        for (int i = 0; i < wanted; i++)
        {
            final BlockPos spot = findSpot(level, pen, i == 0);
            if (spot == null)
            {
                return;
            }

            final Animal animal = types.get(level.getRandom().nextInt(types.size())).spawn(level, spot, EntitySpawnReason.MOB_SUMMONED);
            if (animal == null)
            {
                return;
            }
            if (conscript(building, level, animal))
            {
                // A cavalry horse keeps its own home through its animal data, and the sweep above skips every
                // IManagedAnimal, so claiming it here would only set a home nothing reads.
                continue;
            }
            if (contain)
            {
                claim(pen, animal);
            }
        }
    }

    /**
     * In free mode a Stable is given trained, armoured, combat-ready mounts rather than raw horses.
     * <p>
     * Free mode's promise is that the colony works without the player fetching it anything, and a plain horse in a
     * stable is not yet a mount: a Stablemaster has to be hired, has to be awake (a day worker, so not at night),
     * has to roll {@code TRAINING_CHANCE} to convert it, and then has to spend tack from
     * {@code ModTags#leather} to walk its combat cooldown back down before a cavalryman will take it
     * ({@code EntityAICavalry#isAvailableFor} gates on {@code isReadyForCombat}). That is the whole point of the
     * Stablemaster and it stays exactly as it is when free mode is off; what free mode does here is the same thing
     * it does for the anti-air battery's arrows and a guard's weapon - hand over the finished article.
     * <p>
     * Armour is by building level, so a level 1 stable is not fielding diamond-barded cavalry. Nothing is taken
     * back when free mode is switched off: these are real, persisted entities the colony owns, and free mode
     * documents that it does not clean up after itself. What stops is the supply - the pen is no longer topped up,
     * and readiness goes back to costing the Stablemaster real tack.
     *
     * @param building the hut being stocked.
     * @param level    the level.
     * @param animal   the animal just conjured into the pen.
     * @return true if the animal was turned into a cavalry horse.
     */
    private static boolean conscript(@NotNull final IBuilding building, @NotNull final ServerLevel level, @NotNull final Animal animal)
    {
        if (!(building instanceof BuildingStable) || !(animal instanceof final AbstractHorse vanilla))
        {
            return false;
        }

        final CavalryHorseEntity cav = CavalryHorseEntity.createFromVanilla(building.getColony(), level, vanilla);
        if (cav == null || cav.getAnimalData() == null)
        {
            return false;
        }

        cav.getAnimalData().setHomeBuilding(building);
        // Ready to ride now: the Stablemaster's readiness pass is what free mode is standing in for.
        cav.getAnimalData().setCombatCooldown(0);
        cav.getAnimalData().markDirty();
        cav.setItemSlot(EquipmentSlot.BODY, new ItemStack(bardingFor(building.getBuildingLevel())));
        return true;
    }

    /**
     * The barding a free-mode stable of this level issues its mounts.
     *
     * @param buildingLevel the stable's level.
     * @return the horse armour item.
     */
    private static Item bardingFor(final int buildingLevel)
    {
        return switch (buildingLevel)
        {
            case 1 -> Items.LEATHER_HORSE_ARMOR;
            case 2 -> Items.IRON_HORSE_ARMOR;
            case 3 -> Items.GOLDEN_HORSE_ARMOR;
            default -> Items.DIAMOND_HORSE_ARMOR;
        };
    }

    /**
     * Find somewhere inside the pen an animal can stand.
     * <p>
     * Where the hut expects its animals rather than wherever there is room: the search starts at a stall position if
     * the blueprint marks any (only the stable does today) and otherwise at the centre of the hut's own footprint,
     * and every candidate is rejected unless it is still inside that footprint. An animal put down outside it is
     * invisible to its own herder.
     *
     * @param level  the level.
     * @param pen    the pen.
     * @param anchor whether to try the hut's own anchor first. False for the second and later animals of one pass, so
     *               that a hut being filled does not stack its whole herd on one block.
     * @return a standable position inside the pen, or null if none was found.
     */
    @Nullable
    private static BlockPos findSpot(@NotNull final ServerLevel level, @NotNull final Pen pen, final boolean anchor)
    {
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++)
        {
            final BlockPos candidate = anchor && attempt == 0 ? pen.anchor() : pen.randomPos(level);
            if (!WorldUtil.isBlockLoaded(level, candidate))
            {
                continue;
            }

            final BlockPos spot = EntityUtils.getSpawnPoint(level, candidate);
            if (spot != null && pen.footprint().contains(Vec3.atCenterOf(spot)))
            {
                return spot;
            }
        }
        return null;
    }

    /**
     * The area a herder hut keeps its animals in, in both shapes it is needed in: the hut's own box, which is what
     * the herder AI searches and therefore what "in the hut" means everywhere else in the mod, and the sphere that
     * vanilla's home restriction is expressed in.
     *
     * @param centre    centre of the sphere, at the hut's ground level rather than the middle of its height - the
     *                  restriction is a 3D distance and an animal must not be counted out for standing on a slope.
     * @param radius    radius of the sphere, big enough to contain the whole box plus {@code animalpenslack}.
     * @param footprint the hut's box, built exactly as {@code WorldUtil#getEntitiesWithinBuilding} builds it so that
     *                  "penned" and "the herder can see it" cannot disagree.
     * @param anchor    where animals are put when the hut is stocked or one is recalled.
     */
    private record Pen(BlockPos centre, int radius, AABB footprint, BlockPos anchor)
    {
        /**
         * Blueprint tag the stable marks its horse stalls with. No other herder blueprint tags anything, so every
         * other hut falls back to its own centre; the tag is read rather than the stable special-cased because a
         * blueprint that grows the tag then works without a code change.
         */
        private static final String STALL_TAG = "stall";

        /**
         * Derive the pen of a hut.
         *
         * @param building the hut.
         * @return its pen.
         */
        private static Pen of(@NotNull final IBuilding building)
        {
            final Tuple<BlockPos, BlockPos> corners = building.getCorners();
            final BlockPos low = corners.getA();
            final BlockPos high = corners.getB();

            final AABB footprint = new AABB(low.getX(), low.getY(), low.getZ(), high.getX(), high.getY(), high.getZ());

            final List<BlockPos> ground = building.getLocationsFromTag(TAG_GROUNDLEVEL);
            final int centreY = ground.isEmpty() ? building.getPosition().getY() : ground.get(0).getY();
            final BlockPos centre = new BlockPos((low.getX() + high.getX()) / 2, centreY, (low.getZ() + high.getZ()) / 2);

            final double halfX = (high.getX() - low.getX()) / 2.0D;
            final double halfZ = (high.getZ() - low.getZ()) / 2.0D;
            final int radius = Math.max(MIN_RADIUS,
              Mth.ceil(Math.sqrt(halfX * halfX + halfZ * halfZ)) + MineColonies.getConfig().getServer().animalPenSlack.get());

            final List<BlockPos> stalls = building.getLocationsFromTag(STALL_TAG);
            final BlockPos anchor = stalls.isEmpty() ? centre : stalls.get(0);

            return new Pen(centre, radius, footprint, anchor);
        }

        /**
         * Whether an animal's home is this pen.
         * <p>
         * Matched on the home lying inside the hut's box rather than on it being exactly the current centre, because
         * upgrading the hut moves both the corners and the centre and the animals standing in it must not all be
         * disowned at once by a rebuild.
         *
         * @param animal the animal.
         * @return true if this pen is where it belongs.
         */
        private boolean owns(@NotNull final Animal animal)
        {
            return animal.hasHome() && footprint.contains(Vec3.atCenterOf(animal.getHomePosition()));
        }

        /**
         * How far outside the pen an animal is.
         *
         * @param animal the animal.
         * @return the distance in blocks past the pen's edge, zero if it is inside.
         */
        private double distanceOutside(@NotNull final Animal animal)
        {
            return Math.max(0.0D, Math.sqrt(animal.distanceToSqr(Vec3.atCenterOf(centre))) - radius - STRAY_MARGIN);
        }

        /**
         * A random column inside the hut's box.
         *
         * @param level the level, for its random source.
         * @return the position, at the pen's ground level.
         */
        private BlockPos randomPos(@NotNull final ServerLevel level)
        {
            final int x = Mth.floor(footprint.minX) + level.getRandom().nextInt(Math.max(1, Mth.floor(footprint.getXsize()) + 1));
            final int z = Mth.floor(footprint.minZ) + level.getRandom().nextInt(Math.max(1, Mth.floor(footprint.getZsize()) + 1));
            return new BlockPos(x, centre.getY(), z);
        }
    }
}
