package com.minecolonies.core.compatibility.simpleplanes;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.compatibility.simpleplanes.AircraftCompat;
import com.minecolonies.api.compatibility.simpleplanes.AntiAirSettings;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.requestable.Stack;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.buildings.AbstractBuildingGuards;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.debug.FreeMode;
import com.minecolonies.core.entity.ai.combat.CombatUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.przemyk.simpleplanes.autopilot.AutopilotRegistry;
import xyz.przemyk.simpleplanes.combat.Ballistics;
import xyz.przemyk.simpleplanes.combat.GunshipRegistry;
import xyz.przemyk.simpleplanes.entities.PlaneEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.minecolonies.api.util.constant.TranslationConstants.AIRCRAFT_DOWNED;
import static com.minecolonies.api.util.constant.TranslationConstants.AIRCRAFT_NO_AMMO;
import static com.minecolonies.api.util.constant.TranslationConstants.AIRCRAFT_NO_AMMO_LOCATION;
import static com.minecolonies.api.util.constant.TranslationConstants.AIRCRAFT_NO_AMMO_MORE_MANY;
import static com.minecolonies.api.util.constant.TranslationConstants.AIRCRAFT_NO_AMMO_MORE_ONE;

/**
 * Colony anti-air: the guard towers shoot at aircraft.
 *
 * <h2>Why this is not a change to {@code RangeCombatAI}</h2>
 * Because the range budget is wrong before anything else is. {@code RangeCombatAI#getAttackDistance}
 * starts at {@code BASE_DISTANCE_FOR_RANGED_ATTACK} 10, caps at
 * {@code MAX_DISTANCE_FOR_RANGED_ATTACK} 24, and then adds {@code user.getY() - target.getY()} — which
 * for a target <em>above</em> the shooter is negative and makes the envelope smaller still. A strike
 * aircraft flies its run-in a hundred blocks above the ground. No amount of work on target selection
 * closes a gap of that size in the wrong direction, so this is a separate weapon with its own reach,
 * its own rate and its own gunnery, and the guards' ordinary combat AI is untouched.
 *
 * <h2>The three things that make it affordable</h2>
 * <ul>
 *   <li><b>Targets are free.</b> {@link AutopilotRegistry#active()} is the live list of scripted
 *       aircraft with no world lookup at all. Only manually flown planes need a scan, and that runs
 *       once every {@link #SCAN_INTERVAL} ticks rather than every tick.</li>
 *   <li><b>The gunnery already exists.</b> {@link Ballistics#solve} is a closed-form solution of the
 *       arrow recurrence and is completely direction-agnostic — {@code dy} is signed, so a target a
 *       hundred blocks up is the same algebra as one twenty blocks down. It is called, not copied:
 *       Simple Planes is LGPL-3.0-or-later and this is ordinary linking.</li>
 *   <li><b>The damage is flat.</b> {@code CustomArrowEntity#onHitEntity} divides the base damage by
 *       the arrow's speed before vanilla multiplies it back, so a MineColonies arrow does exactly its
 *       base damage at any range. The battery therefore has a predictable time-to-kill instead of one
 *       that collapses at the far edge of its envelope.</li>
 * </ul>
 *
 * <h2>Bringing one down, which is the part that had to be designed rather than coded</h2>
 * <b>A plane at zero health does not fall.</b> Both places that end its life require it to be touching
 * the ground ({@code PlaneEntity#tick} and {@code PlaneEntity#hurtServer} both test {@code onGround()}),
 * so an aircraft shot to pieces at altitude flies serenely on under its flight director and detonates
 * its warhead wherever it eventually arrives — which, for a bomber that was already pointed at the
 * town hall, is the town hall. Damage alone moves the crater; it does not prevent it.
 *
 * <p>So the kill is {@link #disable}, and it is three lines with a lot behind them:
 * <ol>
 *   <li><b>{@code setAutopilot(null)}</b> — the flight director stops flying it, {@code isPowered()}
 *       goes false because a scripted aircraft has no engine of its own (the autopilot is what powered
 *       it), and it becomes a falling object. <b>It also disarms the warhead</b>, which is the happy
 *       accident this whole design rests on: {@code PlaneEntity#explode} reads the blast off
 *       {@code getAutopilot().getPlan().blast()} and falls back to {@code Blast.DEFAULT} when there is
 *       no autopilot. A 16-power incendiary block-breaker becomes ordinary TNT the moment the flight
 *       plan goes away.</li>
 *   <li><b>{@code setThrottle(0)}</b> — throttle zero is the airbrake in {@code tickMotion}, so it
 *       comes down steeply and near where it was hit rather than gliding on for a quarter of a mile.</li>
 *   <li><b>A chunk ticket</b> — leaving the autopilot registry means losing the one that kept it
 *       ticking, and an aircraft that stops ticking hangs in the air for ever. This keeps its own until
 *       it lands.</li>
 * </ol>
 *
 * <p>It is then left alone. <b>The wreck falling into a hillside three hundred blocks out and going up
 * is the point</b>, not a side effect to be tidied away: it is visible, it is legible, and it tells the
 * player their battery worked. The only case that is intercepted is the one that would undo the whole
 * exercise — see {@link #tickWrecks} for a plane coming down inside the colony's own claim.
 */
public final class AntiAirBattery
{
    /**
     * What a colony that has never been tuned shoots like, and what a colony view or a null colony
     * falls back to.
     *
     * <p>Every number the battery used to hold as a compile-time constant now lives in
     * {@link AntiAirSettings}, which is per colony, saved with it, and set by
     * {@code /mc colony antiair}. This instance is never mutated — it is the all-defaults object, i.e.
     * the exact behaviour this class had before any of it was tunable.
     *
     * <h2>What the numbers were chosen for, which has not changed</h2>
     * <p><b>Range, 200 blocks.</b> This is the design parameter, not a number to be minimised. A
     * battery that only kills the bomber when it is overhead has failed even when it kills it every
     * time, because the wreck then lands on the colony. 200 blocks against an aircraft closing at about
     * 2.8 blocks/tick is roughly 71 ticks — three and a half seconds — of tracking before it arrives.
     * The time to kill is four hits at the default interval, i.e. about 80 ticks from one emplacement
     * and half that from two, so <b>one tower engages a fast target and does not quite finish it; two
     * do.</b> That is the intended difficulty. It is also inside what the projectile can physically do:
     * {@link Ballistics} notes that an arrow's horizontal reach saturates at
     * {@code 100 * launch speed}, which at {@link AntiAirSettings#LAUNCH_SPEED} is 400 blocks — and
     * that is where {@link AntiAirSettings#MAX_RANGE} comes from.
     *
     * <p><b>Launch speed, 4.0 blocks/tick.</b> Faster than a hand bow's 1.4 — this is an emplacement on
     * a tower, not a citizen with a stick — which buys both reach and a flatter, shorter time of
     * flight, and a shorter time of flight is the single biggest term in whether a shot at a moving
     * target connects. Deliberately the one number that stayed a constant; see
     * {@link AntiAirSettings#LAUNCH_SPEED} for why.
     *
     * <p><b>Damage, 3.0.</b> Four rounds to bring down a 10-health airframe.
     *
     * <p><b>Interval, 20 ticks.</b> Twenty is deliberately just above the ten-tick damage-immunity
     * window {@code PlaneEntity#hurtServer} applies after every hit, so nothing is wasted on an aircraft
     * that cannot currently be hurt, and a single tower is a steady thump rather than a stream. That
     * window is also the reason {@link AntiAirSettings#MAX_RATE} is where it is: past two arrows a
     * second the airframe simply cannot take them.
     *
     * <p><b>Minimum tower level, 3.</b> Anti-air is not what a starter tower does.
     */
    private static final AntiAirSettings DEFAULTS = new AntiAirSettings();

    /**
     * Ticks between scans for manually flown aircraft, which are in no registry and can only be found
     * by looking.
     *
     * <p><b>Left as a constant on purpose.</b> It is a cost knob rather than a difficulty one: the only
     * thing it changes about the game is how long a hand-flown plane can be in range before a tower
     * notices it, and every setting of it that saves measurable work also makes the battery look broken
     * against a player who is flying. A server owner reaching for performance has the range bound, the
     * {@code aircraftWarnings} switch and the tower-level threshold, all of which are legible; a number
     * whose wrong value produces silence is not a knob worth handing out.
     */
    private static final int SCAN_INTERVAL = 40;

    /**
     * Height above the tower's own block that rounds leave from, so they clear the roof the archers are
     * standing on.
     */
    private static final double MUZZLE_HEIGHT = 2.0;

    /**
     * Height above the ground at which a wreck coming down inside the colony is taken out of the world
     * instead of being allowed to land. High enough that the descent is fully visible, low enough that
     * the puff of smoke reads as an impact.
     */
    private static final double WRECK_INTERCEPT_HEIGHT = 5.0;

    /**
     * Ticks a disabled aircraft is followed down before it is given up on.
     */
    private static final int WRECK_TIMEOUT = 1200;

    /**
     * Ticks before a colony may be told again that its batteries are dry.
     *
     * <p><b>Why two minutes, and why it is held here rather than read off the world clock.</b> The
     * original test was {@code getGameTime() % 600 == 0}, which is not a throttle at all: it is a
     * property of the tick, not of the colony, so on the one tick in six hundred that satisfies it
     * <em>every</em> empty tower in the colony reports, all in the same frame — six or seven identical
     * lines, and then thirty seconds of silence however bad it gets. A timestamp per colony cannot do
     * that: whichever tower reports first sets it, and the rest of that same tick already fails the
     * test.
     *
     * <p>The interval is two minutes because this line only ever appears while a tower has an aircraft
     * in its sights and a firing solution it cannot take, so it cannot nag in peacetime no matter how
     * long the chests stay empty. Against that, twice {@link AircraftWatch}'s one-minute sighting
     * cooldown is the right ratio — an alarm the player can do nothing about should come more often
     * than a chore they have to walk to. Two minutes is longer than a single run-in, so one engagement
     * is one line, and short enough that a second wave arriving on a still-empty tower says so again
     * while the aircraft the player is meant to connect it to is still overhead.
     */
    private static final int NO_AMMO_COOLDOWN = 2400;

    /**
     * Ticks between sweeps that file restock orders for dry towers. Five seconds: the request system
     * deduplicates the orders anyway, so this exists only to keep the open-request scan off a per-tick
     * path, and any value short enough to be invisible to the player will do.
     */
    private static final int RESTOCK_INTERVAL = 100;

    /**
     * Next tick each emplacement may fire on, keyed by the tower's position.
     */
    private static final Map<BlockPos, Long> NEXT_SHOT = new HashMap<>();

    /**
     * Last tick each colony was told its batteries are dry, keyed by colony id.
     */
    private static final Map<Integer, Long> LAST_NO_AMMO = new HashMap<>();

    /**
     * Last tick each colony's dry towers were swept for restock orders, keyed by colony id.
     */
    private static final Map<Integer, Long> LAST_RESTOCK = new HashMap<>();

    /**
     * Aircraft this battery has disabled and is watching down.
     */
    private static final List<Wreck> WRECKS = new ArrayList<>();

    private AntiAirBattery()
    {
    }

    /**
     * What this colony's batteries are tuned to.
     *
     * <p>The cast is the same one {@link SimplePlanesBlastGuard} makes for blast protection, and for the
     * same reason: the settings are only ever read on the server, so they live on {@code Colony} rather
     * than on {@code IColony}, and anything holding a view gets the defaults — which is the right answer
     * for a view, because a view never fires anything.
     *
     * @param colony the colony, possibly a view or null.
     * @return its settings, or {@link #DEFAULTS}, never null.
     */
    private static AntiAirSettings settings(final IColony colony)
    {
        return colony instanceof final Colony serverColony ? serverColony.getAntiAirSettings() : DEFAULTS;
    }

    /**
     * One tick of every colony's batteries, and of everything they have already shot down.
     *
     * @param level the level being ticked.
     */
    static void tick(final ServerLevel level)
    {
        tickWrecks(level);

        if (!MineColonies.getConfig().getServer().aircraftWarnings.get())
        {
            // The battery is gated on the same switch as the warning: a server that does not want the
            // colony to notice aircraft does not want it shooting at them either.
            return;
        }

        final List<PlaneEntity> seen = visible(level);
        if (seen.isEmpty())
        {
            return;
        }

        // An aircraft is brought down by the round that arrives, not by the round that is fired, so the
        // check for a dead airframe is a separate pass over everything in sight rather than something
        // the firing code can do. Anything already at zero health loses its flight here, and drops out
        // of the target list on the same tick so no further rounds are wasted on it.
        final List<PlaneEntity> targets = new ArrayList<>();
        for (final PlaneEntity plane : seen)
        {
            if (plane.getHealth() <= 0)
            {
                if (plane.getAutopilot() != null)
                {
                    disable(level, plane, IColonyManager.getInstance().getColonyByPosFromWorld(level, plane.blockPosition()));
                }
                continue;
            }
            targets.add(plane);
        }
        if (targets.isEmpty())
        {
            return;
        }

        for (final IColony colony : IColonyManager.getInstance().getColonies(level))
        {
            if (colony.isHostile())
            {
                // A hostile territory has no buildings at all, so the loop below would find nothing. Skipped up
                // front so it is not walked for a colony that can never mount a gun.
                continue;
            }

            // Collected across the whole colony and reported once at the end of its pass, rather than
            // by whichever tower happens to notice first. See NO_AMMO_COOLDOWN.
            final List<IBuilding> dry = new ArrayList<>();
            final AntiAirSettings tuning = settings(colony);
            for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
            {
                if (building instanceof AbstractBuildingGuards && building.getBuildingLevel() >= tuning.getMinTowerLevel())
                {
                    tickEmplacement(level, colony, tuning, building, targets, dry);
                }
            }
            reportDry(level, colony, dry);
        }
    }

    /**
     * Which of a colony's guard towers mount a battery, and what each has left to shoot with.
     *
     * <p>The single place that knows the emplacement rule — a guard tower at the colony's own
     * {@link AntiAirSettings#getMinTowerLevel()} or above — so that {@code /mc colony antiair} cannot
     * drift out of step with what actually fires. That includes drifting after the threshold is
     * <em>changed</em>: the listing reads the same setting the firing loop does, so raising it to 5 makes
     * the level-4 towers disappear from the report on the same tick they stop shooting. Reached from
     * outside this package only through {@code AircraftCompat#emplacements}.
     *
     * @param colony the colony to look at.
     * @return the emplacements, in no particular order.
     */
    static List<AircraftCompat.Emplacement> emplacements(final IColony colony)
    {
        final List<AircraftCompat.Emplacement> found = new ArrayList<>();
        final int minLevel = settings(colony).getMinTowerLevel();
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            if (!(building instanceof AbstractBuildingGuards) || building.getBuildingLevel() < minLevel)
            {
                continue;
            }

            final var handler = building.getItemHandlerCap();
            final int arrows = handler == null
                                 ? 0
                                 : InventoryUtils.getItemCountInItemHandler(handler, stack -> stack.is(Items.ARROW));
            found.add(new AircraftCompat.Emplacement(building.getPosition(),
              Component.translatableEscape(building.getBuildingDisplayName()),
              arrows));
        }
        return found;
    }

    /**
     * Every aircraft in this level the colony is entitled to notice.
     *
     * <p>The registry is walked every tick because it is a pruned list of a handful of aircraft and
     * costs nothing. The world scan for hand-flown planes is on an interval because it is not.
     */
    private static List<PlaneEntity> visible(final ServerLevel level)
    {
        final List<PlaneEntity> found = new ArrayList<>();
        final Set<Integer> ids = new HashSet<>();

        for (final PlaneEntity plane : AutopilotRegistry.active())
        {
            if (plane.level() == level && plane.isAlive() && !SimplePlanesCompat.isFriendly(plane))
            {
                ids.add(plane.getId());
                found.add(plane);
            }
        }

        if (level.getGameTime() % SCAN_INTERVAL == 0)
        {
            for (final IColony colony : IColonyManager.getInstance().getColonies(level))
            {
                if (colony.isHostile())
                {
                    // Nothing in a territory can shoot, so scanning for something to shoot at is pure cost.
                    continue;
                }

                // Each colony scans out to its own range. This is the one place a large range costs real
                // work every time it runs, which is what AntiAirSettings#MAX_RANGE is bounding.
                final AABB box = new AABB(colony.getCenter()).inflate(settings(colony).getRange());
                for (final PlaneEntity plane : level.getEntitiesOfClass(PlaneEntity.class, box))
                {
                    if (!ids.contains(plane.getId()) && plane.isAlive() && !SimplePlanesCompat.isFriendly(plane))
                    {
                        ids.add(plane.getId());
                        found.add(plane);
                    }
                }
            }
        }

        return found;
    }

    /**
     * One guard tower's turn.
     *
     * <p>The colony's tuning is passed in rather than looked up here, because it is the same object for
     * every tower of the colony and this runs once per tower per tick.
     */
    private static void tickEmplacement(
      final ServerLevel level,
      final IColony colony,
      final AntiAirSettings tuning,
      final IBuilding tower,
      final List<PlaneEntity> targets,
      final List<IBuilding> dry)
    {
        final Long next = NEXT_SHOT.get(tower.getPosition());
        if (next != null && level.getGameTime() < next)
        {
            return;
        }

        final Vec3 muzzle = Vec3.atCenterOf(tower.getPosition()).add(0.0, MUZZLE_HEIGHT + tower.getBuildingLevel(), 0.0);

        PlaneEntity target = null;
        double bestDistance = tuning.getRange() * tuning.getRange();
        for (final PlaneEntity candidate : targets)
        {
            final double distance = candidate.position().distanceToSqr(muzzle);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                target = candidate;
            }
        }
        if (target == null)
        {
            return;
        }

        // An emplacement is its archers. No guard on the tower, no anti-air -- which is also where the
        // arrow's owner comes from, and vanilla wants one.
        final AbstractEntityCitizen gunner = gunner(tower);
        if (gunner == null)
        {
            return;
        }

        final Ballistics.Solution solution = solve(muzzle, target);
        if (solution == null)
        {
            // Out of ballistic reach at this launch speed. Hold the round rather than throwing it away.
            return;
        }

        if (!drawRound(tower, dry))
        {
            return;
        }

        NEXT_SHOT.put(tower.getPosition(), level.getGameTime() + tuning.getShotInterval());

        final AbstractArrow arrow = CombatUtils.createArrowForShooter(gunner);
        arrow.setBaseDamage(tuning.getDamage());
        arrow.setPos(muzzle.x, muzzle.y, muzzle.z);
        CombatUtils.launchArrow(arrow, solution.velocity());

        level.playSound(null, tower.getPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.NEUTRAL, 2.0F, 0.8F);
    }

    /**
     * The firing solution, with lead.
     *
     * <p>Three passes of a fixed point, because the two unknowns feed each other: where the aircraft
     * will be depends on the time of flight, and the time of flight depends on how far away that is.
     *
     * <p>Unlike the gunship's version of this, the lead is taken in <b>all three axes</b> and straight
     * off {@code getDeltaMovement()}. The gunship deliberately throws away a mob's vertical velocity
     * because for a mob it is gravity jitter that reverses the moment it lands. An aircraft's vertical
     * velocity is not jitter — a bomber in its terminal dive is descending faster than it is closing —
     * and ignoring it would put every round above a diving target.
     */
    private static Ballistics.Solution solve(final Vec3 muzzle, final PlaneEntity target)
    {
        final Vec3 centre = target.getBoundingBox().getCenter();
        final Vec3 velocity = target.getDeltaMovement();

        Vec3 aim = centre;
        Ballistics.Solution solution = null;
        for (int pass = 0; pass < 3; pass++)
        {
            solution = Ballistics.solve(muzzle, aim, AntiAirSettings.LAUNCH_SPEED);
            if (solution == null)
            {
                return null;
            }
            aim = centre.add(velocity.scale(solution.flightTicks()));
        }
        return solution;
    }

    /**
     * The citizen who fires the round: an assigned guard, in the world, with a bow in hand.
     *
     * @param tower the guard tower.
     * @return the gunner, or null if the tower is unmanned or its guards are not archers.
     */
    private static AbstractEntityCitizen gunner(final IBuilding tower)
    {
        for (final ICitizenData citizen : tower.getAllAssignedCitizen())
        {
            if (citizen.getJob() instanceof AbstractJobGuard && citizen.getEntity().isPresent())
            {
                final AbstractEntityCitizen entity = citizen.getEntity().get();
                if (entity.isAlive() && entity.getMainHandItem().getItem() instanceof BowItem)
                {
                    return entity;
                }
            }
        }
        return null;
    }

    /**
     * Takes one arrow out of the tower.
     *
     * <p>A battery that fires for free is a turret, and a turret is not interesting. Running dry is the
     * intended failure mode, so a tower that cannot find a round is added to the colony's dry list
     * rather than reporting on its own account — see {@link #reportDry}.
     *
     * <p><b>Free mode hands the arrows over instead.</b> The house style is
     * {@link FreeMode#supply}, not a branch that skips the cost: the battery reads the tower's own
     * inventory to decide whether it may fire, exactly as {@code RangeCombatAI} reads the guard's, so a
     * free-mode battery that merely stopped consuming would still have to be special-cased everywhere
     * else that looks. Filling the racks means every later reader — this method, the restock request,
     * the dry list, the chat line — sees an ordinary stocked tower and needs no free-mode knowledge at
     * all. Conjured arrows leak into the racks, which {@link FreeMode} says in as many words is
     * expected of a testing switch.
     *
     * @return true if a round was drawn.
     */
    private static boolean drawRound(final IBuilding tower, final List<IBuilding> dry)
    {
        final var handler = tower.getItemHandlerCap();
        if (handler == null)
        {
            return false;
        }
        int slot = InventoryUtils.findFirstSlotInItemHandlerWith(handler, Items.ARROW);
        if (slot == -1 && FreeMode.isOn(tower))
        {
            FreeMode.supply(handler, new ItemStack(Items.ARROW), Items.ARROW.getDefaultMaxStackSize());
            slot = InventoryUtils.findFirstSlotInItemHandlerWith(handler, Items.ARROW);
        }
        if (slot == -1)
        {
            dry.add(tower);
            return false;
        }
        handler.extractItem(slot, 1, false);
        return true;
    }

    /**
     * Everything the colony is told, and asks for, when its batteries have nothing to shoot with.
     *
     * <p>Both halves are here rather than at the point of failure because both are properties of the
     * <em>colony</em>, not of one tower: the message must be one line however many towers are dry, and
     * the restock is a single sweep over all of them.
     */
    private static void reportDry(final ServerLevel level, final IColony colony, final List<IBuilding> dry)
    {
        if (dry.isEmpty())
        {
            return;
        }
        restock(level, colony, dry);
        announceDry(level, colony, dry);
    }

    /**
     * Asks for arrows the way everything else in the mod asks for anything.
     *
     * <p><b>This is the half of the feature that was missing.</b> Before it, a dry tower complained and
     * the player had to walk over with a stack in hand, which is not how a single other building in
     * MineColonies is supplied. An ordinary async {@link Stack} request filed <em>by the building</em>
     * (citizen id -1, the same shelf {@code MinimumStockModule} files on) is resolved by the warehouse
     * and delivered into the tower's own racks by a courier — which is precisely where
     * {@link #drawRound} looks. The chat line then stops being the only remedy and becomes what it
     * should be: a warning that the automatic remedy is not going to arrive in time.
     *
     * <p>Deduplicated against the tower's own open requests, so a colony under a long attack files one
     * order per tower and not one per tick. Citizen requests are deliberately not consulted: a ranger's
     * arrows are delivered into the ranger's inventory and never reach the racks, so treating one as
     * satisfying the battery would leave the tower permanently empty.
     *
     * <p><b>The order size follows the rate of fire.</b> A stack is a minute of shooting at the default
     * rate and fifteen seconds at the ceiling, while the time for the request to be resolved and walked
     * over by a courier is the same either way — so a fast battery on a flat 64 would spend most of its
     * life waiting on a delivery it burns through almost immediately. {@link AntiAirSettings#arrowOrder}
     * scales it so that one delivery is about the same amount of <em>shooting</em> whatever the rate is,
     * and at the default rate it returns exactly the 64/16 pair that was hard-coded here before.
     */
    private static void restock(final ServerLevel level, final IColony colony, final List<IBuilding> dry)
    {
        final Long last = LAST_RESTOCK.get(colony.getID());
        if (last != null && level.getGameTime() - last < RESTOCK_INTERVAL)
        {
            return;
        }
        LAST_RESTOCK.put(colony.getID(), level.getGameTime());

        final AntiAirSettings tuning = settings(colony);
        final int order = tuning.arrowOrder();
        final int orderMin = tuning.arrowOrderMin();
        for (final IBuilding tower : dry)
        {
            if (!onOrder(tower))
            {
                tower.createRequest(new Stack(new ItemStack(Items.ARROW), order, orderMin), true);
            }
        }
    }

    /**
     * Whether this tower has already asked for arrows on its own behalf.
     */
    private static boolean onOrder(final IBuilding tower)
    {
        for (final IRequest<?> request : tower.getOpenRequests(-1))
        {
            if (request.getRequest() instanceof final Stack stack && stack.getStack().is(Items.ARROW))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * The one line the colony gets.
     *
     * <p><b>One tower is named, not all of them.</b> A line per dry tower is the bug this replaced, and
     * a list of seven names with seven separate hovers is the same wall of text wearing a hat. So the
     * message names the dry tower <em>nearest the colony centre</em> — the shortest walk, and the one
     * the direction and distance in the hover are measured from, so the two agree — and then says how
     * many others are in the same state. A player who restocks the named one and comes back to a
     * message naming a different tower has been given a work list one item at a time, which is the
     * right size for a chat line.
     *
     * <p>The position is a hover on the "on the north side of the colony" clause, which is the
     * convention {@code EntityCitizen#die} established for "the thing this message is about is over
     * there": {@code message.positiondist} with the block coordinates and the distance from the centre.
     * The clause is underlined so that a player who has never hovered a MineColonies message can see
     * there is something to hover — the owner's question was literally "can I get the coordinates
     * somehow?", and an affordance nobody notices answers it no better than no affordance at all.
     */
    private static void announceDry(final ServerLevel level, final IColony colony, final List<IBuilding> dry)
    {
        final Long last = LAST_NO_AMMO.get(colony.getID());
        if (last != null && level.getGameTime() - last < NO_AMMO_COOLDOWN)
        {
            return;
        }
        LAST_NO_AMMO.put(colony.getID(), level.getGameTime());

        IBuilding nearest = dry.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (final IBuilding tower : dry)
        {
            final double distance = BlockPosUtil.getDistanceSquared(tower.getPosition(), colony.getCenter());
            if (distance < bestDistance)
            {
                bestDistance = distance;
                nearest = tower;
            }
        }

        final BlockPos pos = nearest.getPosition();
        final int distance = (int) BlockPosUtil.dist(pos, colony.getCenter());

        final MutableComponent line =
          Component.translatableEscape(AIRCRAFT_NO_AMMO, Component.translatableEscape(nearest.getBuildingDisplayName()), colony.getName())
            .append(Component.translatableEscape(AIRCRAFT_NO_AMMO_LOCATION,
                BlockPosUtil.calcDirection(colony.getCenter(), pos).getLongText())
              .withStyle(style -> style
                .withUnderlined(true)
                .withHoverEvent(new HoverEvent.ShowText(Component.translatableEscape("message.positiondist",
                  pos.getX(),
                  pos.getY(),
                  pos.getZ(),
                  distance)))));

        if (dry.size() == 2)
        {
            line.append(Component.translatableEscape(AIRCRAFT_NO_AMMO_MORE_ONE));
        }
        else if (dry.size() > 2)
        {
            line.append(Component.translatableEscape(AIRCRAFT_NO_AMMO_MORE_MANY, dry.size() - 1));
        }

        MessageUtils.format(line).sendTo(colony).forManagers();
    }

    /**
     * Takes an aircraft's flight away from it. See the class comment for why this and not damage.
     */
    private static void disable(final ServerLevel level, final PlaneEntity plane, final IColony colony)
    {
        // Order matters: the blast the wreck will make is read off the flight plan, so clearing the
        // autopilot is what turns a warhead back into an aeroplane.
        plane.setAutopilot(null);
        plane.setThrottle(0);

        WRECKS.add(new Wreck(plane, level.getGameTime()));

        if (colony != null)
        {
            MessageUtils.format(AIRCRAFT_DOWNED, colony.getName()).sendTo(colony, true).forManagers();
        }
        Log.getLogger().debug("Anti-air brought down aircraft #" + plane.getId() + " at " + plane.position());
    }

    /**
     * Follows everything the battery has disabled all the way down.
     *
     * <p>Two jobs, and only the second one is an intervention.
     *
     * <p><b>Keeping it ticking.</b> Clearing the autopilot takes the aircraft out of
     * {@code AutopilotRegistry}, and with it the chunk ticket that was the only reason it ticked at all
     * this far from a player. Without a replacement it would simply stop, mid-air, for ever — the exact
     * trap both {@code AutopilotRegistry} and {@code GunshipRegistry} document against themselves.
     *
     * <p><b>Not letting it land on the town.</b> A wreck coming down in open country is the whole
     * point and is left entirely alone; it hits the ground, {@code PlaneEntity#tick} calls
     * {@code crash}, and it goes up with {@code Blast.DEFAULT} because its flight plan is gone. But a
     * bomber hit late and still over the colony would put that same explosion through somebody's roof,
     * which would make the battery worse than useless. So inside the claim, and only inside the claim,
     * the last few blocks of the fall are taken away from it: smoke, a bang, and no crater. The player
     * still watches it come down, which is what makes the difference legible rather than magical.
     */
    private static void tickWrecks(final ServerLevel level)
    {
        if (WRECKS.isEmpty())
        {
            return;
        }

        for (final Iterator<Wreck> iterator = WRECKS.iterator(); iterator.hasNext(); )
        {
            final Wreck wreck = iterator.next();
            final PlaneEntity plane = wreck.plane;

            if (plane.level() != level)
            {
                continue;
            }
            if (plane.isRemoved() || !plane.isAlive() || level.getGameTime() - wreck.since > WRECK_TIMEOUT)
            {
                iterator.remove();
                continue;
            }

            GunshipRegistry.keepChunksLoaded(level, plane.position());

            final IColony over = IColonyManager.getInstance().getColonyByPosFromWorld(level, plane.blockPosition());
            if (over == null)
            {
                // Open country. Let it land, let it burn.
                continue;
            }

            final int ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
              plane.blockPosition()).getY();
            if (plane.getY() - ground > WRECK_INTERCEPT_HEIGHT)
            {
                continue;
            }

            level.sendParticles(ParticleTypes.LARGE_SMOKE, plane.getX(), plane.getY(), plane.getZ(), 60, 2.0, 1.5, 2.0, 0.05);
            level.sendParticles(ParticleTypes.POOF, plane.getX(), plane.getY(), plane.getZ(), 40, 1.5, 1.0, 1.5, 0.1);
            level.playSound(null, plane.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 3.0F, 0.7F);
            // discard(), not kill(): kill() runs PlaneEntity#crash, which explodes. Over the colony's own
            // buildings that is the thing being prevented.
            plane.discard();
            iterator.remove();
        }
    }

    /**
     * An aircraft on its way down, and when it started.
     */
    private record Wreck(PlaneEntity plane, long since)
    {
    }
}
