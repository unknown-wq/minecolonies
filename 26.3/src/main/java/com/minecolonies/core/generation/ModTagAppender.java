package com.minecolonies.core.generation;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagBuilder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.function.Function;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * Keeps the NeoForge {@code TagsProvider.TagAppender} call shape ({@code add(Block...)}, {@code addTags(TagKey...)},
 * {@code remove(TagKey...)}) alive on top of the 26.2 / Fabric appender.
 *
 * <p><b>Port note 1 (26.2)</b>: {@link TagAppender#add} takes a {@link ResourceKey} now, not the value itself —
 * 26.2 split id storage out of the objects. The key of an arbitrary block/item/entity type is reached through
 * {@code builtInRegistryHolder().key()}. See {@code /opt/mc-src/net/minecraft/data/tags/TagAppender.java}.</p>
 *
 * <p><b>Port note 2 — referencing a foreign tag.</b> {@code TagsProvider#run} validates every <em>required</em> tag
 * reference against {@code this.builders.containsKey(id) || parentProvider.contains(id)}
 * ({@code /opt/mc-src/net/minecraft/data/tags/TagsProvider.java:80,90-99}). Neither vanilla's own tags nor
 * fabric-convention-tags' ones are in either set during datagen, so a plain {@code addTag(BlockTags.LOGS)} aborts
 * the whole run. On NeoForge this was exactly what {@code ExistingFileHelper} papered over. The fix is Fabric's
 * {@code forceAddTag}, which inserts a {@code ForcedTagEntry} whose {@code verifyIfPresent} unconditionally returns
 * {@code true}; because it is still <em>required</em>, it serialises as the bare string {@code "#minecraft:logs"} —
 * byte-identical to the 1.21.1 output — and a genuinely missing tag still fails loudly at world load. This is
 * deliberately <b>not</b> {@code addOptionalTag}, which would write {@code {"id": …, "required": false}} and let the
 * reference silently degrade to empty.</p>
 *
 * <p>Which branch applies is decided by namespace: every {@code minecolonies:} tag is defined by one of our own
 * providers in this very run, so it keeps the real check (a typo in one of our tag names still fails the datagen);
 * anything else is force-added.</p>
 */
public final class ModTagAppender<T>
{
    private final TagAppender<T>             delegate;
    private final TagBuilder                 raw;
    private final Function<T, ResourceKey<T>> keyOf;

    private ModTagAppender(@NotNull final TagAppender<T> delegate,
                           @NotNull final TagBuilder raw,
                           @NotNull final Function<T, ResourceKey<T>> keyOf)
    {
        this.delegate = delegate;
        this.raw = raw;
        this.keyOf = keyOf;
    }

    public static ModTagAppender<Block> blocks(@NotNull final TagAppender<Block> delegate, @NotNull final TagBuilder raw)
    {
        return new ModTagAppender<>(delegate, raw, block -> block.builtInRegistryHolder().key());
    }

    public static ModTagAppender<Item> items(@NotNull final TagAppender<Item> delegate, @NotNull final TagBuilder raw)
    {
        return new ModTagAppender<>(delegate, raw, item -> item.builtInRegistryHolder().key());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ModTagAppender<EntityType<?>> entityTypes(@NotNull final TagAppender<EntityType<?>> delegate, @NotNull final TagBuilder raw)
    {
        return new ModTagAppender<>(delegate, raw, type -> (ResourceKey) ResourceKey.create(Registries.ENTITY_TYPE, EntityType.getKey(type)));
    }

    /**
     * For registries whose datagen constants are already {@link ResourceKey}s (damage types, biomes); only the
     * {@code addTags}/{@code remove} conveniences and the foreign-tag handling are added.
     */
    public static <T> ModTagAppender<T> byKey(@NotNull final TagAppender<T> delegate, @NotNull final TagBuilder raw)
    {
        return new ModTagAppender<>(delegate, raw, t -> { throw new UnsupportedOperationException("no key extractor"); });
    }

    @SafeVarargs
    public final ModTagAppender<T> add(@NotNull final T... elements)
    {
        for (final T element : elements)
        {
            delegate.add(keyOf.apply(element));
        }
        return this;
    }

    public ModTagAppender<T> add(@NotNull final Collection<? extends T> elements)
    {
        elements.forEach(element -> delegate.add(keyOf.apply(element)));
        return this;
    }

    @SafeVarargs
    public final ModTagAppender<T> addKeys(@NotNull final ResourceKey<T>... elements)
    {
        for (final ResourceKey<T> element : elements)
        {
            delegate.add(element);
        }
        return this;
    }

    public ModTagAppender<T> addEntry(@NotNull final TagEntry entry)
    {
        raw.add(entry);
        return this;
    }

    public ModTagAppender<T> addOptional(@NotNull final ResourceKey<T> element)
    {
        delegate.addOptional(element);
        return this;
    }

    public ModTagAppender<T> addOptional(@NotNull final T element)
    {
        delegate.addOptional(keyOf.apply(element));
        return this;
    }

    @SafeVarargs
    public final ModTagAppender<T> addTags(@NotNull final TagKey<T>... tags)
    {
        for (final TagKey<T> tag : tags)
        {
            addTag(tag);
        }
        return this;
    }

    public ModTagAppender<T> addTag(@NotNull final TagKey<T> tag)
    {
        if (MOD_ID.equals(tag.location().getNamespace()))
        {
            // Defined by one of our own providers in this run -- keep the datagen-time existence check.
            delegate.addTag(tag);
        }
        else
        {
            // Vanilla, convention or another mod's tag: invisible to the datagen, present at runtime.
            delegate.forceAddTag(tag);
        }
        return this;
    }

    public ModTagAppender<T> addOptionalTag(@NotNull final TagKey<T> tag)
    {
        delegate.addOptionalTag(tag);
        return this;
    }

    public ModTagAppender<T> addOptionalTag(@NotNull final Identifier tag)
    {
        raw.addOptionalTag(tag);
        return this;
    }

    public ModTagAppender<T> addOptionalElement(@NotNull final Identifier element)
    {
        raw.addOptionalElement(element);
        return this;
    }

    @SafeVarargs
    public final ModTagAppender<T> remove(@NotNull final TagKey<T>... tags)
    {
        for (final TagKey<T> tag : tags)
        {
            delegate.removeTag(tag);
        }
        return this;
    }

    @NotNull
    public TagAppender<T> delegate()
    {
        return delegate;
    }

    /**
     * Convenience for the item providers, where the datagen constants are often {@link ItemLike}s.
     */
    public static ResourceKey<Item> key(@NotNull final ItemLike item)
    {
        return item.asItem().builtInRegistryHolder().key();
    }
}
