package com.minecolonies.core.colony.events.raid.pirateEvent;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.colonyEvents.EventStatus;
import com.minecolonies.api.compatibility.Compatibility;
import com.minecolonies.api.compatibility.simpleplanes.AircraftCompat;
import com.minecolonies.api.entity.mobs.AbstractEntityMinecoloniesRaider;
import com.minecolonies.api.entity.mobs.RaiderMobUtils;
import com.minecolonies.api.sounds.RaidSounds;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.api.util.MessageUtils.MessagePriority;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.MineColonies;
import com.minecolonies.core.colony.events.raid.HordeRaidEvent;
import com.minecolonies.core.entity.mobs.raider.pirates.EntityArcherPirateRaider;
import com.minecolonies.core.entity.mobs.raider.pirates.EntityCaptainPirateRaider;
import com.minecolonies.core.entity.mobs.raider.pirates.EntityPirateRaider;
import com.minecolonies.core.network.messages.client.PlayAudioMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.minecolonies.api.entity.ModEntities.*;
import static com.minecolonies.api.util.constant.ColonyConstants.SMALL_HORDE_SIZE;
import static com.minecolonies.api.util.constant.TranslationConstants.*;

/**
 * Pirates that arrive by air: an unmanned transport flies over the colony, drops them, and leaves.
 *
 * <h2>What this does differently from every other horde raid, and why each difference is necessary</h2>
 *
 * <p>{@link HordeRaidEvent} is written on the assumption that raiders arrive on foot from a point on
 * the ground outside the colony, and it re-asserts that assumption on every colony tick. Three parts
 * of it are wrong for an air drop and all three are handled here rather than worked around:
 *
 * <ol>
 *   <li><b>The spawn.</b> {@code HordeRaidEvent#onStart} resolves a walkable ground position with
 *       {@code ShipBasedRaiderUtils#getLoadedPositionTowardsCenter} and calls {@code spawnHorde} there.
 *       Here nothing is spawned at start-up at all: the raiders appear one at a time, at the aircraft,
 *       when it is over the drop point, through {@link RaiderMobUtils#spawnAt}.</li>
 *   <li><b>The unloaded-chunk cull.</b> {@code HordeRaidEvent#onUpdate} discards any raider whose block
 *       is not entity-ticking and queues it for respawn. A parachutist a hundred blocks up over a chunk
 *       nobody is standing in is exactly that, so the cull would delete the drop in mid-descent and put
 *       it back on the ground. {@link #onUpdate} keeps the cull but only applies it to raiders that have
 *       actually landed — see {@link #cullAndRespawn}.</li>
 *   <li><b>The ground top-up.</b> The same method tops the horde back up to its quota by calling
 *       {@code spawnHorde} at a ground position whenever anything dies, so the first pirate killed would
 *       be replaced by one walking in from the border. There is no top-up here. <b>An air drop is one
 *       wave</b>, which is both the only thing that can be made to look right and the better game: the
 *       transport is a thing that can be stopped, and a wave that cannot be reinforced is a wave worth
 *       stopping.</li>
 * </ol>
 *
 * <h2>The bookkeeping that had to be fixed</h2>
 * The raid bar and every raid report name a compass direction derived from {@code spawnPoint}
 * ({@code HordeRaidEvent#updateRaidBar}, {@code RaidManager.RaidSpawnInfo}). Left alone that would be
 * the ground point the raid manager picked on the colony border, so pirates falling onto the town hall
 * would be announced as coming from the north. {@link #onStart} therefore moves {@code spawnPoint} to
 * the drop point <em>before</em> anything reads it.
 *
 * <h2>What happens when the transport does not arrive</h2>
 * It can be shot down, fly into a hillside, or simply be lost to a server restart, and the horde
 * counters must survive all three. The raid ends when {@code horde.hordeSize} reaches zero
 * ({@code HordeRaidEvent#onUpdate}), and that counter only ever goes down when a spawned raider dies —
 * so a wave that never lands would pin the raid open for ever, with the boss bar showing and
 * {@code isRaided()} true until the world was deleted. {@link #finished} therefore closes the books
 * explicitly: nothing delivered ends the raid outright, and a partial delivery shrinks the horde to
 * what actually got out of the aircraft.
 */
public class PirateAirRaidEvent extends HordeRaidEvent
{
    /**
     * This raids event id, registry entries use res locations as ids.
     */
    public static final Identifier PIRATE_AIR_RAID_EVENT_TYPE_ID =
      Identifier.fromNamespaceAndPath(Constants.MOD_ID, "pirate_air_raid");

    /**
     * Height above the target building the transport is asked to fly its drop run at.
     *
     * <p>High enough that the aircraft clears anything the player has built and that the descent reads
     * as a descent rather than a stumble, low enough that the parachutists stay inside the chunks the
     * colony itself keeps loaded — which is what stops {@link #cullAndRespawn} ever having to look at
     * them.
     */
    private static final int DROP_ALTITUDE = 70;

    /**
     * Ticks the event waits for the transport before writing it off. Generous: the aircraft is launched
     * a few hundred blocks out and flies at roughly 2.8 blocks/tick, so a normal run is well under a
     * thousand ticks, and the only thing this bound protects against is a run that silently never
     * reports back at all.
     */
    private static final int TRANSPORT_TIMEOUT = 6000;

    /**
     * NBT keys of this event's own state.
     */
    private static final String TAG_DROP_POS      = "airraid_droppos";
    private static final String TAG_DROP_DONE     = "airraid_dropdone";
    private static final String TAG_GROUNDED      = "airraid_grounded";

    /**
     * Where the transport is asked to open its bay.
     */
    private BlockPos dropPos;

    /**
     * Raiders still aboard, in the order they leave. Rebuilt from the horde at start-up and never
     * persisted: a run that does not finish before a restart is written off by {@link #onUpdate}
     * rather than resumed, because the tracker that drives it lives only in memory.
     */
    private final Deque<EntityType<?>> manifest = new ArrayDeque<>();

    /**
     * How many of each kind actually left the aircraft, so a partial delivery can be reconciled.
     */
    private int deliveredRaiders = 0;
    private int deliveredArchers = 0;
    private int deliveredBosses  = 0;

    /**
     * Set once the run is over, however it ended.
     */
    private boolean dropComplete = false;

    /**
     * True when this raid gave up on flying and became an ordinary ground raid — no aircraft mod, the
     * feature switched off, or no transport available. Everything then defers to the parent class.
     */
    private boolean grounded = false;

    /**
     * Ticks spent waiting for the transport.
     */
    private int waitTicks = 0;

    /**
     * Raiders that vanished with the world under them and are owed a respawn.
     */
    private final List<BlockPos> respawns = new ArrayList<>();

    public PirateAirRaidEvent(final IColony colony)
    {
        super(colony);
    }

    @Override
    public Identifier getEventTypeID()
    {
        return PIRATE_AIR_RAID_EVENT_TYPE_ID;
    }

    /**
     * Whether an air raid is possible right now. Checked by the raid manager before it ever builds one
     * of these, so a colony with no aircraft mod never sees the type at all.
     *
     * @return true if the feature is on and something can fly.
     */
    public static boolean isAvailable()
    {
        return MineColonies.getConfig().getServer().airRaids.get() && Compatibility.aircraftCompat.isPresent();
    }

    @Override
    public void onStart()
    {
        if (!isAvailable() || horde == null || !(getColony().getWorld() instanceof final ServerLevel level))
        {
            grounded = true;
            super.onStart();
            return;
        }

        // Kept so the ground fallback below can put it back. This matters more than it looks: the parent's
        // onStart resolves its spawn through ShipBasedRaiderUtils#getLoadedPositionTowardsCenter, which
        // refuses any point closer to the colony centre than MIN_CENTER_DISTANCE -- and the drop point is
        // directly over a building, which is well inside it. Falling back with the drop point still in
        // place therefore does not produce a ground raid, it produces a CANCELED event and a raid that
        // silently never happens.
        final BlockPos groundSpawn = getSpawnPos();

        // The drop goes over a building rather than over the colony centre: the centre of a large
        // colony is often a plaza with nothing worth defending on it, and getRandomBuilding is the same
        // choice the raiders' own pathing makes once they are down.
        final BlockPos building = getColony().getRaiderManager().getRandomBuilding();
        dropPos = new BlockPos(building.getX(),
          Math.min(level.getMaxY() - 16, building.getY() + DROP_ALTITUDE),
          building.getZ());

        // Before updateRaidBar and before the announcement: from here on this raid comes from over the
        // colony, and everything that reads spawnPoint -- the boss bar's compass direction, the raid
        // message, the win announcement -- should say so rather than naming the ground point on the
        // border that the manager picked before anyone knew this would be an air raid.
        setSpawnPoint(dropPos);
        // The manager's own history is a separate record and has already been written, so it has to be
        // corrected rather than pre-empted. This is what /mc colony raid info reads.
        getColony().getRaiderManager().updateLastSpawnPoint(getEventTypeID(), dropPos);

        buildManifest();
        if (manifest.isEmpty() || !Compatibility.aircraftCompat.launchDropRun(level, dropPos, new Callbacks()))
        {
            Log.getLogger().warn("Air raid could not launch a transport for colony " + getColony().getName()
                                   + "; falling back to a ground raid.");
            grounded = true;
            dropPos = null;
            setSpawnPoint(groundSpawn);
            super.onStart();
            return;
        }

        status = EventStatus.PREPARING;

        updateRaidBar();
        MessageUtils.format(RAID_AIR_INBOUND, getColony().getName())
          .withPriority(MessagePriority.DANGER)
          .sendTo(getColony())
          .forManagers();

        final PlayAudioMessage audio = new PlayAudioMessage(
          horde.initialSize <= SMALL_HORDE_SIZE ? RaidSounds.WARNING_EARLY : RaidSounds.WARNING, SoundSource.HOSTILE);
        PlayAudioMessage.sendToAll(getColony(), false, false, audio);
    }

    /**
     * Fills the manifest from the horde, bosses last so a run that is cut short loses its captain
     * rather than its rank and file — being interrupted should cost the attacker the best of what it
     * was carrying, not the worst.
     */
    private void buildManifest()
    {
        for (int i = 0; i < horde.numberOfRaiders; i++)
        {
            manifest.add(getNormalRaiderType());
        }
        for (int i = 0; i < horde.numberOfArchers; i++)
        {
            manifest.add(getArcherRaiderType());
        }
        for (int i = 0; i < horde.numberOfBosses; i++)
        {
            manifest.add(getBossRaiderType());
        }
    }

    /**
     * A transport that has not yet dropped cannot be hurried, so the immediate-start path must not
     * promote this event to PROGRESSING the way it does for a horde already standing on the ground.
     */
    @Override
    public void skipPreparation()
    {
        if (grounded)
        {
            super.skipPreparation();
        }
    }

    @Override
    public void onUpdate()
    {
        if (grounded)
        {
            super.onUpdate();
            return;
        }

        updateRaidBar();
        getColony().getRaiderManager().setNightsSinceLastRaid(0);

        if (!dropComplete)
        {
            // Nothing has landed yet, or the aircraft is still emptying. The only thing that can go
            // wrong here and not report itself is a run that stops being ticked at all -- a server
            // restart drops the tracker, because it lives in memory and the flight plan does not know
            // about this raid.
            if (++waitTicks > TRANSPORT_TIMEOUT)
            {
                Log.getLogger().debug("Air raid transport for colony " + getColony().getName() + " never reported back; writing it off.");
                reconcile(false, null);
            }
            return;
        }

        if (horde.hordeSize <= 0)
        {
            status = EventStatus.DONE;
            return;
        }

        cullAndRespawn();

        if (horde.numberOfBosses + horde.numberOfRaiders + horde.numberOfArchers < Math.floor(horde.initialSize * 0.1))
        {
            status = EventStatus.DONE;
            return;
        }

        if (getColony().getRaiderManager().areSpiesEnabled()
              || horde.numberOfBosses + horde.numberOfRaiders + horde.numberOfArchers < Math.round(horde.initialSize * 0.15))
        {
            for (final Entity entity : getEntities())
            {
                if (entity instanceof final LivingEntity living)
                {
                    living.addEffect(new MobEffectInstance(MobEffects.GLOWING, 550));
                }
            }
        }
    }

    /**
     * The parent's unloaded-chunk recycling, with the one guard that makes it safe for an air drop.
     *
     * <p>{@code HordeRaidEvent#onUpdate} discards every raider standing in a chunk that is not
     * entity-ticking and respawns it closer in, which is right for a horde walking across country and
     * fatal for one under canopy: a parachutist is by definition somewhere nobody is standing, and
     * deleting it mid-descent is how an air drop turns back into a ground raid without anybody asking.
     *
     * <p>So the rule here is the same rule with {@code onGround()} added. A raider that has landed and
     * then been left behind by the world is recycled exactly as before; one still in the air is left
     * alone, because it is going somewhere and the aircraft's own chunk tickets are what kept it
     * loaded on the way down.
     */
    private void cullAndRespawn()
    {
        if (!respawns.isEmpty())
        {
            for (final BlockPos pos : respawns)
            {
                final BlockPos spawnPos = ShipBasedRaiderUtils.getLoadedPositionTowardsCenter(
                  pos, getColony(), MAX_RESPAWN_DEVIATION, getSpawnPos(), MIN_CENTER_DISTANCE, 10);
                if (spawnPos != null)
                {
                    RaiderMobUtils.spawn(getNormalRaiderType(), 1, spawnPos, getColony().getWorld(), getColony(), getID());
                }
            }
            respawns.clear();
            return;
        }

        for (final Entity entity : getEntities())
        {
            if (!entity.isAlive())
            {
                entity.remove(Entity.RemovalReason.DISCARDED);
                respawns.add(entity.blockPosition());
                continue;
            }

            // The guard. Everything above the ground is on its way down and stays.
            if (entity.onGround() && !WorldUtil.isEntityBlockLoaded(getColony().getWorld(), entity.blockPosition()))
            {
                entity.remove(Entity.RemovalReason.DISCARDED);
                respawns.add(entity.blockPosition());
            }
        }
    }

    /**
     * Closes the books on a run, once.
     *
     * @param delivered whether the aircraft emptied its bay.
     * @param where     the aircraft's last position, for the report; may be null.
     */
    private void reconcile(final boolean delivered, final Vec3 where)
    {
        if (dropComplete)
        {
            return;
        }
        dropComplete = true;
        manifest.clear();

        // Whatever is still aboard is never coming, so the horde is now exactly what landed. Without
        // this, hordeSize keeps counting raiders that do not exist and the raid never reaches DONE.
        horde.numberOfRaiders = deliveredRaiders;
        horde.numberOfArchers = deliveredArchers;
        horde.numberOfBosses = deliveredBosses;
        horde.hordeSize = deliveredRaiders + deliveredArchers + deliveredBosses;

        if (horde.hordeSize <= 0)
        {
            // The whole wave was stopped in the air. That is a win, and it is announced as one rather
            // than left to expire quietly.
            MessageUtils.format(RAID_AIR_STOPPED, getColony().getName())
              .sendTo(getColony(), true)
              .forManagers();
            status = EventStatus.DONE;
            return;
        }

        status = EventStatus.PROGRESSING;

        if (!delivered)
        {
            MessageUtils.format(RAID_AIR_PARTIAL, String.valueOf(horde.hordeSize), getColony().getName())
              .sendTo(getColony(), true)
              .forManagers();
        }
        else
        {
            MessageUtils.format(RAID_AIR_LANDED,
                BlockPosUtil.calcDirection(getColony().getCenter(), where == null ? getSpawnPos() : BlockPos.containing(where)).getLongText(),
                getColony().getName())
              .withPriority(MessagePriority.DANGER)
              .sendTo(getColony())
              .forManagers();
        }
    }

    /**
     * The bridge's view of this event. A separate object rather than {@code implements DropRun} on the
     * event itself, so the only thing the compat layer can reach is the two callbacks.
     */
    private final class Callbacks implements AircraftCompat.DropRun
    {
        @Override
        public boolean dropTick(final Vec3 position)
        {
            if (dropComplete || manifest.isEmpty() || !(getColony().getWorld() instanceof ServerLevel))
            {
                return true;
            }

            final EntityType<?> type = manifest.poll();
            final AbstractEntityMinecoloniesRaider raider =
              RaiderMobUtils.spawnAt(type, BlockPos.containing(position), getColony().getWorld(), getColony(), getID());

            if (raider != null)
            {
                // The descent. With Simple Planes present this is a parachute; without it, and as a
                // backstop if the parachute is refused, slow falling. Either way the raider survives
                // the drop, which is the only part the raid cares about.
                Compatibility.aircraftCompat.deploy(raider);

                if (type == getBossRaiderType())
                {
                    deliveredBosses++;
                }
                else if (type == getArcherRaiderType())
                {
                    deliveredArchers++;
                }
                else
                {
                    deliveredRaiders++;
                }
            }

            return manifest.isEmpty();
        }

        @Override
        public void finished(final boolean delivered, final Vec3 where)
        {
            reconcile(delivered, where);
        }
    }

    // ------------------------------------------------------------------
    // Below here: the pirate horde's own bookkeeping, identical in shape to PirateGroundRaidEvent.
    // ------------------------------------------------------------------

    @Override
    public void registerEntity(final Entity entity)
    {
        if (!(entity instanceof AbstractEntityMinecoloniesRaider) || !entity.isAlive())
        {
            entity.remove(Entity.RemovalReason.DISCARDED);
            return;
        }

        if (entity instanceof EntityCaptainPirateRaider && boss.keySet().size() < horde.numberOfBosses)
        {
            boss.put(entity, entity.getUUID());
            return;
        }

        if (entity instanceof EntityArcherPirateRaider && archers.keySet().size() < horde.numberOfArchers)
        {
            archers.put(entity, entity.getUUID());
            return;
        }

        if (entity instanceof EntityPirateRaider && normal.keySet().size() < horde.numberOfRaiders)
        {
            normal.put(entity, entity.getUUID());
            return;
        }

        entity.remove(Entity.RemovalReason.DISCARDED);
    }

    @Override
    public void onEntityDeath(final LivingEntity entity)
    {
        super.onEntityDeath(entity);
        if (!(entity instanceof AbstractEntityMinecoloniesRaider))
        {
            return;
        }

        if (entity instanceof EntityCaptainPirateRaider)
        {
            boss.remove(entity);
            horde.numberOfBosses--;
        }

        if (entity instanceof EntityArcherPirateRaider)
        {
            archers.remove(entity);
            horde.numberOfArchers--;
        }

        if (entity instanceof EntityPirateRaider)
        {
            normal.remove(entity);
            horde.numberOfRaiders--;
        }

        horde.hordeSize--;

        if (horde.hordeSize == 0)
        {
            status = EventStatus.DONE;
        }

        sendHordeMessage();
    }

    @Override
    public EntityType<?> getNormalRaiderType()
    {
        return PIRATE;
    }

    @Override
    public EntityType<?> getArcherRaiderType()
    {
        return ARCHERPIRATE;
    }

    @Override
    public EntityType<?> getBossRaiderType()
    {
        return CHIEFPIRATE;
    }

    @Override
    protected MutableComponent getDisplayName()
    {
        return Component.translatableEscape(RAID_PIRATE);
    }

    @Override
    protected void updateRaidBar()
    {
        super.updateRaidBar();
        raidBar.setDarkenScreen(true);
    }

    @Override
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag compound = super.serializeNBT(provider);
        compound.putBoolean(TAG_DROP_DONE, dropComplete);
        compound.putBoolean(TAG_GROUNDED, grounded);
        if (dropPos != null)
        {
            BlockPosUtil.write(compound, TAG_DROP_POS, dropPos);
        }
        return compound;
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);
        dropComplete = compound.getBooleanOr(TAG_DROP_DONE, true);
        grounded = compound.getBooleanOr(TAG_GROUNDED, false);
        if (compound.contains(TAG_DROP_POS))
        {
            dropPos = BlockPosUtil.read(compound, TAG_DROP_POS);
        }

        // A run that was still in the air when the world was saved cannot be resumed: the tracker that
        // flies it is not persisted, and the aircraft -- which is -- has no idea it was carrying anyone.
        // The wait clock is therefore started near its limit rather than at zero, so the raid writes the
        // transport off within a few seconds of loading instead of holding the boss bar open for five
        // minutes first. What landed before the save keeps raiding; what did not is gone.
        if (!dropComplete)
        {
            deliveredRaiders = horde.numberOfRaiders;
            deliveredArchers = horde.numberOfArchers;
            deliveredBosses = horde.numberOfBosses;
            waitTicks = TRANSPORT_TIMEOUT - 20;
        }
    }

    /**
     * Loads the event from the nbt compound.
     *
     * @param colony   colony to load into
     * @param compound NBTcompound with saved values
     * @return the raid event.
     */
    public static PirateAirRaidEvent loadFromNBT(final IColony colony, final CompoundTag compound, @NotNull final HolderLookup.Provider provider)
    {
        final PirateAirRaidEvent event = new PirateAirRaidEvent(colony);
        event.deserializeNBT(provider, compound);
        return event;
    }
}
