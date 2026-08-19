package com.minecolonies.core.generation.defaults;

import com.minecolonies.api.entity.ModEntities;
import com.minecolonies.api.items.ModTags;
import com.minecolonies.core.generation.ModTagAppender;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Port note (26.2 / Fabric): NeoForge's {@code EntityTypeTagsProvider} is gone; vanilla's own is hardcoded to the
 * vanilla tag set, so this is the generic {@link FabricTagsProvider} over {@code Registries.ENTITY_TYPE}.
 * {@code add(EntityType…)} now needs a {@link net.minecraft.resources.ResourceKey}, which {@link ModTagAppender}
 * supplies, and foreign tag references are force-added (see that class' javadoc).
 */
public class DefaultEntityTypeTagsProvider extends FabricTagsProvider<EntityType<?>>
{
    public DefaultEntityTypeTagsProvider(final FabricPackOutput output,
      final CompletableFuture<HolderLookup.Provider> lookupProvider)
    {
        super(output, Registries.ENTITY_TYPE, lookupProvider);
    }

    private ModTagAppender<EntityType<?>> tagOf(@NotNull final TagKey<EntityType<?>> key)
    {
        return ModTagAppender.entityTypes(builder(key), getOrCreateRawBuilder(key));
    }

    @NotNull
    @Override
    public String getName()
    {
        return "MineColonies Entity Type Tags";
    }

    @Override
    protected void addTags(final HolderLookup.Provider holder)
    {
        tagOf(ModTags.hostile).add(EntityTypes.SLIME);
        tagOf(ModTags.mobAttackBlacklist).add(EntityTypes.ENDERMAN, EntityTypes.LLAMA);
        tagOf(ModTags.freeToInteractWith).addOptionalElement(Identifier.fromNamespaceAndPath("corpse", "corpse"));

        final ModTagAppender<EntityType<?>> raiderTagAppender = tagOf(ModTags.raiders);
        ModEntities.getRaiders().forEach(raiderType -> raiderTagAppender.addEntry(TagEntry.element(EntityType.getKey(raiderType))));

        tagOf(TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("dynamictrees", "falling_tree_damage_immune")))
                .add(ModEntities.CITIZEN)
                .add(ModEntities.VISITOR);
    }
}
