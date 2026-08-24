package com.minecolonies.core.generation.defaults;

import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.util.DamageSourceKeys;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricCodecDataProvider;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/**
 * Port note (26.2 / Fabric): NeoForge's {@code JsonCodecProvider} became
 * {@link FabricCodecDataProvider}, whose registry-key constructor resolves to the same
 * {@code data/&lt;ns&gt;/damage_type/} directory.  {@code unconditional(id, value)} is now the
 * {@code BiConsumer} handed to {@code configure}.
 */
public class DefaultDamageTypeProvider extends FabricCodecDataProvider<DamageType> {
    public DefaultDamageTypeProvider(@NotNull final FabricPackOutput packOutput,
                                     final CompletableFuture<Provider> lookupProvider) {
        super(packOutput, lookupProvider, Registries.DAMAGE_TYPE, DamageType.DIRECT_CODEC);
    }

    @NotNull
    @Override
    public String getName() {
        return "MineColonies Damage Types";
    }

    @Override
    protected void configure(final BiConsumer<Identifier, DamageType> unconditional, final Provider registries) {
        forEachDamageType(unconditional);
    }

    /**
     * Bootstraps the damage types into the datagen registry set.
     * <p>
     * Port note (26.2): writing {@code data/minecolonies/damage_type/*.json} is not enough on its own. The damage
     * type <em>tags</em> are validated by {@code TagsProvider#run} against the {@code HolderLookup.Provider} that
     * Fabric hands every provider, and a codec provider only produces files -- it puts nothing in that lookup. So
     * {@code minecraft:bypasses_armor} referencing {@code minecolonies:wakeywakey} aborted the whole run with
     * {@code Couldn't define tag ... missing following references}. Registering the same entries here through
     * {@code DataGeneratorEntrypoint#buildRegistry} makes them visible to the tag provider; nothing writes them
     * twice, because the dynamic registry provider only emits {@code Registries.ENCHANTMENT}.
     *
     * @param context the datagen bootstrap context.
     */
    public static void bootstrap(@NotNull final BootstrapContext<DamageType> context) {
        forEachDamageType((id, type) -> context.register(ResourceKey.create(Registries.DAMAGE_TYPE, id), type));
    }

    /**
     * The single list of damage types, feeding both the json writer and {@link #bootstrap}.
     *
     * @param unconditional receives every damage type by registry name.
     */
    private static void forEachDamageType(final BiConsumer<Identifier, DamageType> unconditional) {
        unconditional.accept(DamageSourceKeys.CONSOLE.identifier(), damage("console"));
        unconditional.accept(DamageSourceKeys.DEFAULT.identifier(), damage("default"));
        unconditional.accept(DamageSourceKeys.DESPAWN.identifier(), damage("despawn"));
        unconditional.accept(DamageSourceKeys.NETHER.identifier(), damage("nether"));
        unconditional.accept(DamageSourceKeys.GUARD.identifier(), damage("entity.minecolonies.guard"));
        unconditional.accept(DamageSourceKeys.GUARD_PVP.identifier(), damage("entity.minecolonies.guardpvp"));
        unconditional.accept(DamageSourceKeys.SLAP.identifier(), damage("entity.minecolonies.slap"));
        unconditional.accept(DamageSourceKeys.STUCK_DAMAGE.identifier(), damage("entity.minecolonies.stuckdamage"));
        unconditional.accept(DamageSourceKeys.TRAINING.identifier(), damage("entity.minecolonies.training"));
        unconditional.accept(DamageSourceKeys.WAKEY.identifier(), damage("entity.minecolonies.wakeywakey"));
        unconditional.accept(DamageSourceKeys.PIERCE.identifier(), damage("entity.minecolonies.pierce"));
        unconditional.accept(DamageSourceKeys.OLD_AGE.identifier(), damage("oldage"));

        unconditional.accept(DamageSourceKeys.AMAZON.identifier(), entityDamage(ModEntities.AMAZON));
        unconditional.accept(DamageSourceKeys.AMAZONCHIEF.identifier(), entityDamage(ModEntities.AMAZONCHIEF));
        unconditional.accept(DamageSourceKeys.AMAZONSPEARMAN.identifier(), entityDamage(ModEntities.AMAZONSPEARMAN));
        unconditional.accept(DamageSourceKeys.ARCHERBARBARIAN.identifier(), entityDamage(ModEntities.ARCHERBARBARIAN));
        unconditional.accept(DamageSourceKeys.ARCHERMUMMY.identifier(), entityDamage(ModEntities.ARCHERMUMMY));
        unconditional.accept(DamageSourceKeys.ARCHERPIRATE.identifier(), entityDamage(ModEntities.ARCHERPIRATE));
        unconditional.accept(DamageSourceKeys.BARBARIAN.identifier(), entityDamage(ModEntities.BARBARIAN));
        unconditional.accept(DamageSourceKeys.CHIEFBARBARIAN.identifier(), entityDamage(ModEntities.CHIEFBARBARIAN));
        unconditional.accept(DamageSourceKeys.CHIEFPIRATE.identifier(), entityDamage(ModEntities.CHIEFPIRATE));
        unconditional.accept(DamageSourceKeys.MERCENARY.identifier(), entityDamage(ModEntities.MERCENARY));
        unconditional.accept(DamageSourceKeys.MUMMY.identifier(), entityDamage(ModEntities.MUMMY));
        unconditional.accept(DamageSourceKeys.NORSEMENARCHER.identifier(), entityDamage(ModEntities.NORSEMEN_ARCHER));
        unconditional.accept(DamageSourceKeys.NORSEMENCHIEF.identifier(), entityDamage(ModEntities.NORSEMEN_CHIEF));
        unconditional.accept(DamageSourceKeys.PHARAO.identifier(), entityDamage(ModEntities.PHARAO));
        unconditional.accept(DamageSourceKeys.PIRATE.identifier(), entityDamage(ModEntities.PIRATE));
        unconditional.accept(DamageSourceKeys.SHIELDMAIDEN.identifier(), entityDamage(ModEntities.SHIELDMAIDEN));
        unconditional.accept(DamageSourceKeys.SPEAR.identifier(), entityDamage(ModEntities.SPEAR));
        unconditional.accept(DamageSourceKeys.VISITOR.identifier(), entityDamage(ModEntities.VISITOR));
        unconditional.accept(DamageSourceKeys.DROWNED_PIRATE.identifier(), entityDamage(ModEntities.DROWNED_PIRATE));
        unconditional.accept(DamageSourceKeys.DROWNED_ARCHERPIRATE.identifier(), entityDamage(ModEntities.DROWNED_ARCHERPIRATE));
        unconditional.accept(DamageSourceKeys.DROWNED_CHIEFPIRATE.identifier(), entityDamage(ModEntities.DROWNED_CHIEFPIRATE));

        unconditional.accept(DamageSourceKeys.CAMP_AMAZON.identifier(), entityDamage(ModEntities.CAMP_AMAZON));
        unconditional.accept(DamageSourceKeys.CAMP_AMAZONCHIEF.identifier(), entityDamage(ModEntities.CAMP_AMAZONCHIEF));
        unconditional.accept(DamageSourceKeys.CAMP_AMAZONSPEARMAN.identifier(), entityDamage(ModEntities.CAMP_AMAZONSPEARMAN));

        unconditional.accept(DamageSourceKeys.CAMP_BARBARIAN.identifier(), entityDamage(ModEntities.CAMP_BARBARIAN));
        unconditional.accept(DamageSourceKeys.CAMP_CHIEFBARBARIAN.identifier(), entityDamage(ModEntities.CAMP_CHIEFBARBARIAN));
        unconditional.accept(DamageSourceKeys.CAMP_ARCHERBARBARIAN.identifier(), entityDamage(ModEntities.CAMP_ARCHERBARBARIAN));

        unconditional.accept(DamageSourceKeys.CAMP_MUMMY.identifier(), entityDamage(ModEntities.CAMP_MUMMY));
        unconditional.accept(DamageSourceKeys.CAMP_ARCHERMUMMY.identifier(), entityDamage(ModEntities.CAMP_ARCHERMUMMY));
        unconditional.accept(DamageSourceKeys.CAMP_PHARAO.identifier(), entityDamage(ModEntities.CAMP_PHARAO));

        unconditional.accept(DamageSourceKeys.CAMP_PIRATE.identifier(), entityDamage(ModEntities.CAMP_PIRATE));
        unconditional.accept(DamageSourceKeys.CAMP_ARCHERPIRATE.identifier(), entityDamage(ModEntities.CAMP_ARCHERPIRATE));
        unconditional.accept(DamageSourceKeys.CAMP_CHIEFPIRATE.identifier(), entityDamage(ModEntities.CAMP_CHIEFPIRATE));

        unconditional.accept(DamageSourceKeys.CAMP_NORSEMENARCHER.identifier(), entityDamage(ModEntities.CAMP_NORSEMEN_ARCHER));
        unconditional.accept(DamageSourceKeys.CAMP_NORSEMENCHIEF.identifier(), entityDamage(ModEntities.CAMP_NORSEMEN_CHIEF));
        unconditional.accept(DamageSourceKeys.CAMP_SHIELDMAIDEN.identifier(), entityDamage(ModEntities.CAMP_SHIELDMAIDEN));
    }

    @NotNull
    private static DamageType entityDamage(@NotNull final EntityType<?> entityType) {
        return damage(entityType.getDescriptionId());
    }

    @NotNull
    private static DamageType damage(@NotNull final String msgId) {
        return new DamageType(msgId, DamageScaling.ALWAYS, 0.1F);
    }
}