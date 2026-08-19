package com.minecolonies.api.compatibility.simpleplanes;

import com.minecolonies.api.colony.IColony;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Everything MineColonies wants from an aircraft mod, and the complete behaviour when there is not one.
 *
 * <p>This is the fallback, on the pattern {@code DynamicTreeProxy} sets: a concrete class of harmless
 * answers rather than an interface, so the field in {@link com.minecolonies.api.compatibility.Compatibility}
 * is never null and no caller needs a presence check before every call. The real implementation,
 * {@code com.minecolonies.core.compatibility.simpleplanes.SimplePlanesCompat}, subclasses it and is
 * installed only when the loader reports {@code simpleplanes} present.
 *
 * <p><b>Nothing in this file, or in any file that calls it, may name a Simple Planes type.</b> That is
 * the whole point of the split: this package compiles with Simple Planes absent from the classpath,
 * the implementation package is listed in {@code optional-integrations.txt} and can be compiled out
 * entirely, and the vocabulary between them is vanilla — {@link BlockPos}, {@link Vec3},
 * {@link ServerLevel}.
 *
 * <p>The licence direction matters here and points the same way: MineColonies is GPL-3.0-only and
 * Simple Planes is LGPL-3.0-or-later, so MineColonies may link against Simple Planes but no
 * MineColonies code may be copied into it. All of the glue therefore lives on this side.
 */
public class AircraftCompat
{
    /**
     * Duration of the fallback slow-falling effect, in ticks. Long enough to fall from any cruise
     * altitude to the ground: slow falling caps the descent at roughly 0.1 blocks/tick, so 30 seconds
     * covers about 60 blocks of drop with a wide margin, and the effect simply expires harmlessly if
     * the raider is already down.
     */
    protected static final int SLOW_FALL_TICKS = 600;

    /**
     * Whether an aircraft mod is actually present. Callers use this to choose between an air raid and
     * an ordinary ground one; they must not use it to decide whether the other methods are safe to
     * call, because they always are.
     *
     * @return false in the fallback.
     */
    public boolean isPresent()
    {
        return false;
    }

    /**
     * Sends an unmanned transport over {@code dropPos} and calls {@code handler} back while it is
     * overhead.
     *
     * @param level   the level to fly in.
     * @param dropPos the point the raiders should be released over.
     * @param handler the callbacks, driven from the server tick.
     * @return true if a transport was launched; false means the caller must fall back to a ground
     * arrival, and {@code handler} will never be called.
     */
    public boolean launchDropRun(final ServerLevel level, final BlockPos dropPos, final DropRun handler)
    {
        return false;
    }

    /**
     * Puts a raider that has just left an aircraft into a survivable descent.
     *
     * <p>The fallback is slow falling, and it is deliberately a complete answer rather than a stub:
     * vanilla slow falling both limits the descent rate and cancels fall damage outright, so a raider
     * dropped with no aircraft mod present still arrives alive. The Simple Planes implementation puts
     * the raider under a parachute instead and keeps this as a backstop.
     *
     * @param raider the raider that has just been released.
     */
    public void deploy(final LivingEntity raider)
    {
        raider.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false));
    }

    /**
     * Aircraft near or inbound on a point, for the air-raid warning.
     *
     * @param level  the level to look in.
     * @param centre the colony centre.
     * @param radius how far around {@code centre} a merely-present aircraft counts as a sighting. An
     *               aircraft on a scripted attack run against something inside the colony is reported
     *               regardless of how far away it currently is — that is the whole value of the
     *               warning.
     * @return the sightings, never null; empty in the fallback.
     */
    public List<AircraftSighting> sightings(final ServerLevel level, final BlockPos centre, final int radius)
    {
        return List.of();
    }

    /**
     * Every aircraft worth telling a person about, for {@code /mc aircraft where|tp}.
     *
     * <h2>Why this is not {@link #sightings}</h2>
     * {@code sightings} answers a colony's question — "is anything threatening me" — and is shaped by
     * it: it reports a scripted aircraft from any distance only when that aircraft is on an
     * <em>attack</em> run, because a transport flying past on some errand is not what an alarm is
     * for. That is right for the warning and wrong for a person asking where the aircraft are. An air
     * raid's transport is on a plain route, so {@code sightings} would not find it until it was
     * already within scanning range of the colony — which is exactly the moment the player complained
     * about, when it is overhead and about to drop.
     *
     * <p>This method therefore reports <b>every</b> aircraft flying a scripted plan in the level,
     * whatever the plan is and however far away it is, plus any other aircraft within {@code radius}.
     * The first half is the useful one: a scripted run is known from the instant it is ordered,
     * hundreds of blocks out, long before anything could scan for it.
     *
     * @param level  the level to look in.
     * @param centre the point to measure from, normally the player.
     * @param radius how far around {@code centre} to look for aircraft that are <em>not</em> on a
     *               scripted plan — a plane somebody is flying by hand is in no registry and can only
     *               be found by looking.
     * @return the aircraft, never null; empty in the fallback.
     */
    public List<AircraftReport> aircraft(final ServerLevel level, final BlockPos centre, final int radius)
    {
        return List.of();
    }

    /**
     * Marks an aircraft as belonging to this colony's own traffic, so the warning does not fire for it
     * and a future anti-air battery does not shoot it down. No-op in the fallback.
     *
     * @param entityId the aircraft's entity id.
     */
    public void markFriendly(final int entityId)
    {
        // Nothing to remember when there are no aircraft.
    }

    /**
     * The colony's anti-air emplacements and what each of them has to shoot with.
     *
     * <p>Exists so that {@code /mc colony antiair} can answer "which tower needs arrows, and where is
     * it" without naming an aircraft type: which guard towers mount a battery, and how many arrows are
     * in their racks, is knowledge that belongs to the implementation and would otherwise have to be
     * duplicated — and diverge — in the command.
     *
     * <p>The empty fallback is the honest answer rather than a stub: with no aircraft mod present a
     * colony has no anti-air positions, because nothing would ever fire from one.
     *
     * @param colony the colony to look at.
     * @return the emplacements, never null; empty in the fallback.
     */
    public List<Emplacement> emplacements(final IColony colony)
    {
        return List.of();
    }

    /**
     * One guard tower that mounts an anti-air battery.
     *
     * @param position where the tower's hut block is.
     * @param name     the tower's display name, already resolved to a component so the caller does not
     *                 have to know whether the player renamed it.
     * @param arrows   how many arrows are in its racks right now. Zero is the state that stops it
     *                 firing and is what the "no arrows" warning is about.
     */
    public record Emplacement(BlockPos position, Component name, int arrows)
    {
    }

    /**
     * Callbacks from a live drop run. Every method runs on the server thread, inside the level tick.
     */
    public interface DropRun
    {
        /**
         * The transport is over the drop point and the bay is open.
         *
         * @param position where the aircraft is right now, which is where the raider should appear.
         * @return true when the caller has nothing left to drop, which closes the bay and lets the
         * aircraft fly on.
         */
        boolean dropTick(Vec3 position);

        /**
         * The run is over, once, whatever happened.
         *
         * @param delivered true if the aircraft reached the drop point and the bay was emptied; false
         *                  if it was destroyed, ran out of patience or never arrived.
         * @param where     the aircraft's last known position, for the report.
         */
        void finished(boolean delivered, Vec3 where);
    }

    /**
     * One aircraft the colony can see.
     *
     * @param entityId     the aircraft's entity id, so a caller can de-duplicate across ticks.
     * @param position     where it is now.
     * @param strikeTarget the point it is flying a scripted attack run at, or null if it is not on
     *                     one. A non-null value inside the colony is the strongest warning available
     *                     and arrives the moment the run is ordered.
     * @param unmanned     true if nobody is aboard, i.e. it is scripted rather than being flown.
     */
    public record AircraftSighting(int entityId, Vec3 position, @Nullable BlockPos strikeTarget, boolean unmanned)
    {
    }

    /**
     * One aircraft, described well enough to report it to a person and to teleport to it.
     *
     * <p>The vocabulary is vanilla on purpose, like the rest of this file. {@code type} is already a
     * resolved {@link Component} — the aircraft mod's own name for the airframe, in the player's
     * language — so no caller has to know what kinds of aircraft exist. The flight plan is reduced to
     * the two things a person actually asks: <em>where is it going</em> and <em>is it coming for
     * something</em>. Nothing here can express a flight plan in the aircraft mod's own terms, and it
     * should not: every string the command prints is a MineColonies lang key built from these fields.
     *
     * @param entityId    the aircraft's entity id.
     * @param type        the airframe's display name.
     * @param position    where it is right now. Its {@code y} is the altitude the report quotes.
     * @param destination the point its scripted plan is flying it to, or null if it is on no plan —
     *                    which means somebody is flying it by hand, or it is simply adrift.
     * @param strike      true if that destination is an attack run rather than a transit. Only
     *                    meaningful when {@code destination} is non-null.
     * @param unmanned    true if nobody is aboard.
     */
    public record AircraftReport(int entityId,
                                 Component type,
                                 Vec3 position,
                                 @Nullable BlockPos destination,
                                 boolean strike,
                                 boolean unmanned)
    {
        /**
         * Whether this aircraft is flying a scripted plan, i.e. whether {@link #destination} means
         * anything.
         *
         * @return true if it is on a plan.
         */
        public boolean scripted()
        {
            return destination != null;
        }
    }
}
