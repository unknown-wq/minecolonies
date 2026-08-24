package com.ldtteam.structurize.datagen;

import com.ldtteam.structurize.tag.ModTags;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypeIds;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Datagen provider for Entity Tags.
 */
public class EntityTagProvider extends FabricTagsProvider.EntityTypeTagsProvider
{
    /**
     * @param output           the pack output.
     * @param registriesFuture the registry lookup future.
     */
    public EntityTagProvider(final FabricPackOutput output, final CompletableFuture<Provider> registriesFuture)
    {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(final Provider provider)
    {
        // 26.2 split ids out of the registry objects: the EntityType constants moved to EntityTypes and the
        // ResourceKeys live in EntityTypeIds, which is exactly what TagAppender#add wants now
        // (/opt/mc-src/net/minecraft/world/entity/EntityTypeIds.java:13).
        final TagAppender<EntityType<?>> tag = builder(ModTags.PREVIEW_TICKING_ENTITIES);
        tag.add(EntityTypeIds.ARMOR_STAND);
        tag.add(EntityTypeIds.END_CRYSTAL);
        tag.add(EntityTypeIds.BLOCK_DISPLAY);
        tag.add(EntityTypeIds.ITEM_DISPLAY);
        tag.add(EntityTypeIds.TEXT_DISPLAY);
        tag.add(EntityTypeIds.FURNACE_MINECART);
        tag.add(EntityTypeIds.OMINOUS_ITEM_SPAWNER);
    }

    @Override
    @NotNull
    public String getName()
    {
        return "Structurize Entity Tags";
    }
}
