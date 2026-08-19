package com.minecolonies.core.generation.defaults;

import com.minecolonies.api.util.DamageSourceKeys;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Port note (26.2 / Fabric): the NeoForge {@code TagsProvider(output, registry, lookup, modId, existingFileHelper)}
 * constructor is gone.  The three vanilla tags extended here already exist at runtime but are invisible to the
 * datagen, so they are appended to directly -- {@code tag(TagKey)} on the Fabric provider creates a builder for
 * them, which is exactly the "extend an existing tag" behaviour we want; only <em>references</em> to foreign tags
 * need force-adding, and there are none here.
 */
@SuppressWarnings("unchecked")
public class DefaultDamageTagsProvider extends FabricTagsProvider<DamageType>
{
    public DefaultDamageTagsProvider(
      @NotNull final FabricPackOutput output,
      final CompletableFuture<HolderLookup.Provider> lookupProvider)
    {
        super(output, Registries.DAMAGE_TYPE, lookupProvider);
    }

    @NotNull
    @Override
    public String getName()
    {
        return "MineColonies Damage Type Tags";
    }

    @Override
    protected void addTags(final HolderLookup.Provider lookup)
    {
        builder(DamageTypeTags.BYPASSES_ARMOR).add(DamageSourceKeys.WAKEY, DamageSourceKeys.GUARD_PVP, DamageSourceKeys.PIERCE);
        builder(DamageTypeTags.IS_PROJECTILE).add(DamageSourceKeys.SPEAR, DamageSourceKeys.PIERCE);
        builder(DamageTypeTags.BYPASSES_SHIELD).add(DamageSourceKeys.PIERCE);
    }
}
