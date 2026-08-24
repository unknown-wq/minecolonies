package com.ldtteam.domumornamentum.datagen.utils;

import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Keeps the NeoForge {@code TagsProvider.TagAppender} call shape ({@code add(Block...)}, {@code addTags(TagKey...)})
 * alive on top of the 26.2 appender.
 *
 * <p>Port note 1 (26.2): {@code TagAppender#add} takes a {@link net.minecraft.resources.ResourceKey} now, not the
 * value itself — 26.2 split id storage out of the objects ({@code BlockIds} / {@code ItemIds} / {@code BlockItemId}).
 * The key of an arbitrary block is reached through {@code block.builtInRegistryHolder().key()}. Confirmed in
 * {@code /opt/mc-src/net/minecraft/data/tags/TagAppender.java:12} and in
 * {@code /workspace/desolation/src/main/java/raltsmc/desolation/data/DesolationBlockTagProvider.java:21}.</p>
 *
 * <p><b>Port note 2 — referencing a foreign tag.</b> {@code TagsProvider#run} validates every <em>required</em> tag
 * reference against {@code this.builders.containsKey(id) || parentProvider.contains(id)}
 * ({@code /opt/mc-src/net/minecraft/data/tags/TagsProvider.java:80,90-99}). Neither vanilla's own tags nor
 * fabric-convention-tags' ones are in either set during datagen, so a plain
 * {@code addTag(BlockTags.LOGS)} aborts the whole run with
 * {@code "Couldn't define tag … as it is missing following references: #minecraft:logs, …"}. On NeoForge this was
 * exactly what {@code ExistingFileHelper} papered over.</p>
 *
 * <p>The fix is Fabric's {@code forceAddTag}, which inserts a {@code ForcedTagEntry} — a {@code TagEntry} built with
 * {@code (id, tag = true, required = true)} whose {@code verifyIfPresent} unconditionally returns {@code true}
 * (javap on {@code net/fabricmc/fabric/impl/datagen/ForcedTagEntry.class} inside
 * {@code fabric-data-generation-api-v1-25.4.4+9e7dc27f9e.jar}). Because {@code required} stays {@code true}, the entry
 * still serialises as the bare string {@code "#minecraft:logs"} — byte-identical to the 26.1 output — and a genuinely
 * missing tag still fails <em>loudly</em> at world load. This is deliberately <b>not</b> {@code addOptionalTag}: that
 * one writes {@code {"id": "#minecraft:logs", "required": false}} and would let the tag silently degrade to empty.</p>
 *
 * <p>Which branch applies is decided by namespace: every {@code domum_ornamentum:} tag is defined by
 * {@link com.ldtteam.domumornamentum.datagen.DomumOrnamentumBlockTagProvider} in this very run, so it keeps the real
 * check (a typo in a DO tag name still fails the datagen); anything else is force-added.</p>
 */
public final class BlockTagAppender {

    private final TagAppender<Block> delegate;

    public BlockTagAppender(final TagAppender<Block> delegate) {
        this.delegate = delegate;
    }

    public BlockTagAppender add(final Block... blocks) {
        for (final Block block : blocks) {
            this.delegate.add(block.builtInRegistryHolder().key());
        }
        return this;
    }

    @SafeVarargs
    public final BlockTagAppender addTags(final TagKey<Block>... tags) {
        for (final TagKey<Block> tag : tags) {
            addTag(tag);
        }
        return this;
    }

    public BlockTagAppender addTag(final TagKey<Block> tag) {
        if (Constants.MOD_ID.equals(tag.location().getNamespace())) {
            // Defined by this provider in this run — keep the datagen-time existence check.
            this.delegate.addTag(tag);
        } else {
            // Vanilla or convention tag: invisible to the datagen, present at runtime. See the class javadoc.
            this.delegate.forceAddTag(tag);
        }
        return this;
    }

    public BlockTagAppender addOptional(final Block block) {
        this.delegate.addOptional(block.builtInRegistryHolder().key());
        return this;
    }

    public TagAppender<Block> delegate() {
        return this.delegate;
    }
}
