package com.minecolonies.api.entity.mobs;

import net.minecraft.resources.Identifier;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.entity.mobs.barbarians.IChiefBarbarianEntity;
import com.minecolonies.api.entity.mobs.barbarians.IMeleeBarbarianEntity;
import com.minecolonies.api.entity.mobs.egyptians.IPharaoEntity;
import com.minecolonies.api.entity.mobs.pirates.ICaptainPirateEntity;
import com.minecolonies.api.entity.mobs.pirates.IPirateEntity;
import com.minecolonies.api.entity.mobs.vikings.IMeleeNorsemenEntity;
import com.minecolonies.api.entity.mobs.vikings.INorsemenChiefEntity;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.util.BlockPosUtil;
import com.minecolonies.api.util.CompatibilityUtils;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.Random;

import static com.minecolonies.core.colony.events.raid.RaiderConstants.*;
import java.util.function.Supplier;

/**
 * Util class for raider mobs/spawning
 */
public final class RaiderMobUtils
{
    /**
     * Mob attribute, used for custom attack damage.
     * <p>
     * Registered eagerly: 26.2 has no DeferredRegister, the field shape stays a {@link Supplier} (contract C1).
     */
    public final static Supplier<RangedAttribute> MOB_ATTACK_DAMAGE = registerAttribute("mc_mob_damage",
      new RangedAttribute("mc_mob_damage", 2.0, 1.0, 20));

    /**
     * The same attribute as a {@link net.minecraft.core.Holder}, which is what
     * {@code LivingEntity#getAttribute} and {@code AttributeSupplier.Builder#add} take in 26.2. Provided so call
     * sites do not have to write {@code BuiltInRegistries.ATTRIBUTE.wrapAsHolder(MOB_ATTACK_DAMAGE.get())}.
     */
    public final static net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> MOB_ATTACK_DAMAGE_HOLDER =
      BuiltInRegistries.ATTRIBUTE.wrapAsHolder(MOB_ATTACK_DAMAGE.get());

    /**
     * Register one attribute eagerly.
     *
     * @param name      the attribute path.
     * @param attribute the attribute.
     * @return a supplier of the registered attribute.
     */
    private static Supplier<RangedAttribute> registerAttribute(final String name, final RangedAttribute attribute)
    {
        final RangedAttribute value = Registry.register(BuiltInRegistries.ATTRIBUTE,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, name), attribute);
        return () -> value;
    }

    /**
     * Damage increased by 1 for every 200 raid level difficulty
     */
    public static int DAMAGE_PER_X_RAID_LEVEL = 400;

    /**
     * Max damage from raidlevels
     */
    public static int MAX_RAID_LEVEL_DAMAGE = 3;

    private RaiderMobUtils()
    {
        throw new IllegalStateException("Tried to initialize: MobSpawnUtils but this is a Utility class.");
    }

    /**
     * Set mob attributes.
     *
     * @param mob    The mob to set the attributes on.
     * @param colony The colony that the mob is attacking.
     */
    public static void setMobAttributes(final AbstractEntityMinecoloniesRaider mob, final IColony colony)
    {
        final double difficultyModifier = colony.getRaiderManager().getRaidDifficultyModifier();
        mob.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(FOLLOW_RANGE * 2);
        mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(difficultyModifier < 2.4 ? MOVEMENT_SPEED : MOVEMENT_SPEED * 1.2);
        final int raidLevel = colony.getRaiderManager().getColonyRaidLevel();

        // Base damage
        final double attackDamage =
          ATTACK_DAMAGE +
            difficultyModifier *
              Math.min(raidLevel / DAMAGE_PER_X_RAID_LEVEL, MAX_RAID_LEVEL_DAMAGE);

        // Base health
        final double baseHealth = getHealthBasedOnRaidLevel(raidLevel) * difficultyModifier;

        mob.initStatsFor(baseHealth, difficultyModifier, attackDamage);
    }

    /**
     * Sets the entity's health based on the raidLevel
     *
     * @param raidLevel the raid level.
     * @return returns the health in the form of a double
     */
    public static double getHealthBasedOnRaidLevel(final int raidLevel)
    {
        return Math.max(BARBARIAN_BASE_HEALTH, (BARBARIAN_BASE_HEALTH + raidLevel * BARBARIAN_HEALTH_MULTIPLIER));
    }

    /**
     * Sets up and spawns the Barbarian entities of choice
     *
     * @param entityToSpawn  The entity which should be spawned
     * @param numberOfSpawns The number of times the entity should be spawned
     * @param spawnLocation  the location at which to spawn the entity
     * @param world          the world in which the colony and entity are
     * @param colony         the colony to spawn them close to.
     * @param eventID        the event id.
     */
    public static void spawn(
      final EntityType<?> entityToSpawn,
      final int numberOfSpawns,
      final BlockPos spawnLocation,
      final Level world,
      final IColony colony,
      final int eventID)
    {
        if (spawnLocation != null && entityToSpawn != null && world != null && numberOfSpawns > 0)
        {
            int spawnDeviationX = 0;
            int spawnDeviationZ = 0;

            for (int i = 0; i < numberOfSpawns; i++)
            {
                final AbstractEntityMinecoloniesRaider entity = (AbstractEntityMinecoloniesRaider) entityToSpawn.create(world, net.minecraft.world.entity.EntitySpawnReason.EVENT);

                if (entity != null)
                {
                    BlockPos spawnpos = BlockPosUtil.findAround(world, spawnLocation.offset(spawnDeviationX, 0, spawnDeviationZ), 5, 5, BlockPosUtil.SOLID_AIR_POS_SELECTOR);
                    if (spawnpos == null)
                    {
                        spawnpos = spawnLocation.above();
                    }

                    entity.snapTo(spawnpos.getX(), spawnpos.getY(), spawnpos.getZ(), (float) Mth.wrapDegrees(world.getRandom().nextDouble() * WHOLE_CIRCLE), 0.0F);
                    CompatibilityUtils.addEntity(world, entity);
                    entity.setColony(colony);
                    entity.setEventID(eventID);
                    entity.registerWithColony();
                    spawnDeviationZ += 1;

                    if (spawnDeviationZ > 5)
                    {
                        spawnDeviationZ = 0;
                        spawnDeviationX += 1;
                    }
                }
            }
        }
    }

    /**
     * Spawns one raider at exactly the position given, with no search for standing room, and hands it
     * back.
     *
     * <p>Two things separate this from {@link #spawn}, and an air drop needs both.
     *
     * <p><b>No ground search.</b> {@code spawn} looks for a solid block with air above it within five
     * blocks ({@link BlockPosUtil#findAround} with {@code SOLID_AIR_POS_SELECTOR}) and only falls back
     * to the position it was given when that search comes up empty. In open air a hundred blocks up
     * the search does come up empty, so {@code spawn} would in fact place the raider correctly — but
     * only by accident, and it would stop doing so the first time a transport passed within five
     * blocks of a mountainside. A raider leaving an aircraft belongs at the aircraft, so this asks for
     * that outright.
     *
     * <p><b>It returns the entity.</b> The caller has to put a parachute under it, and {@code spawn}
     * creates between zero and many raiders and returns none of them.
     *
     * @param entityToSpawn the raider type.
     * @param spawnLocation the exact position to place it at.
     * @param world         the world.
     * @param colony        the colony it is raiding.
     * @param eventID       the raid event it belongs to.
     * @return the raider, or null if the entity type refused to create one.
     */
    public static AbstractEntityMinecoloniesRaider spawnAt(
      final EntityType<?> entityToSpawn,
      final BlockPos spawnLocation,
      final Level world,
      final IColony colony,
      final int eventID)
    {
        if (spawnLocation == null || entityToSpawn == null || world == null)
        {
            return null;
        }

        final Entity created = entityToSpawn.create(world, net.minecraft.world.entity.EntitySpawnReason.EVENT);
        if (!(created instanceof final AbstractEntityMinecoloniesRaider entity))
        {
            if (created != null)
            {
                created.discard();
            }
            return null;
        }

        entity.snapTo(spawnLocation.getX() + 0.5,
          spawnLocation.getY(),
          spawnLocation.getZ() + 0.5,
          (float) Mth.wrapDegrees(world.getRandom().nextDouble() * WHOLE_CIRCLE),
          0.0F);
        CompatibilityUtils.addEntity(world, entity);
        entity.setColony(colony);
        entity.setEventID(eventID);
        entity.registerWithColony();
        return entity;
    }

    /**
     * Set the equipment of a certain mob.
     *
     * @param mob the equipment to set up.
     */
    public static void setEquipment(final AbstractEntityMinecoloniesMonster mob)
    {
        if (mob instanceof IMeleeBarbarianEntity || mob instanceof IMeleeNorsemenEntity || mob instanceof INorsemenChiefEntity)
        {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
        }
        else if (mob instanceof IPharaoEntity)
        {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.pharaoscepter));
        }
        else if (mob instanceof IArcherMobEntity)
        {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        }
        else if (mob instanceof ISpearmanMobEntity)
        {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.spear));
        }
        else if (mob instanceof IChiefBarbarianEntity)
        {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.chiefSword));
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
            mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.CHAINMAIL_BOOTS));
        }
        else if (mob instanceof IPirateEntity)
        {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.scimitar));
            if (mob instanceof ICaptainPirateEntity)
            {
                if (new Random().nextBoolean())
                {
                    mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.pirateHelmet_1));
                    mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.pirateChest_1));
                    mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.pirateLegs_1));
                    mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.pirateBoots_1));
                }
                else
                {
                    mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.pirateHelmet_2));
                    mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.pirateChest_2));
                    mob.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.pirateLegs_2));
                    mob.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.pirateBoots_2));
                }
            }
        }
    }

    /**
     * Returns the barbarians close to an entity.
     *
     * @param entity             The entity to test against
     * @param distanceFromEntity The distance to check for
     * @return the barbarians (if any) that is nearest
     */
    public static List<AbstractEntityMinecoloniesRaider> getBarbariansCloseToEntity(final Entity entity, final double distanceFromEntity)
    {
        return CompatibilityUtils.getWorldFromEntity(entity).getEntitiesOfClass(
          AbstractEntityMinecoloniesRaider.class,
          entity.getBoundingBox().expandTowards(
            distanceFromEntity,
            3.0D,
            distanceFromEntity),
          Entity::isAlive);
    }
}
