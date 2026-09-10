package com.ldtteam.domumornamentum.datagen;

import com.ldtteam.domumornamentum.datagen.bricks.BrickItemTagProvider;
import com.ldtteam.domumornamentum.datagen.extra.ExtraItemTagProvider;
import com.ldtteam.domumornamentum.datagen.utils.IItemTagSubProvider;
import com.ldtteam.domumornamentum.datagen.utils.ItemTagAppender;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Single item tag provider that runs the item tag sub providers. See {@link DomumOrnamentumBlockTagProvider}.
 *
 * <p>{@code copy(blockTag, itemTag)} needs the block tag provider instance so that it can read the block builders —
 * that is why the block provider is constructed first and passed in, exactly as in
 * {@code /workspace/desolation/src/main/java/raltsmc/desolation/data/DesolationDatagen.java:15-16}.</p>
 */
public class DomumOrnamentumItemTagProvider extends FabricTagsProvider.ItemTagsProvider {

    private static final List<IItemTagSubProvider> SUB_PROVIDERS = List.of(
            new BrickItemTagProvider(),
            new ExtraItemTagProvider());

    public DomumOrnamentumItemTagProvider(final FabricPackOutput output,
                                          final CompletableFuture<HolderLookup.Provider> registriesFuture,
                                          final BlockTagsProvider blockTagProvider) {
        super(output, registriesFuture, blockTagProvider);
    }

    @Override
    protected void addTags(final HolderLookup.Provider registries) {
        // TagsProvider already declares tag(TagKey<T>) with a different return type, so the sub provider sink is a
        // separate object rather than this provider itself.
        final IItemTagSubProvider.Sink sink = new IItemTagSubProvider.Sink() {
            @Override
            public void copy(final TagKey<Block> blockTag, final TagKey<Item> itemTag) {
                DomumOrnamentumItemTagProvider.this.copy(blockTag, itemTag);
            }

            @Override
            public ItemTagAppender tag(final TagKey<Item> tag) {
                return new ItemTagAppender(builder(tag));
            }
        };
        for (final IItemTagSubProvider subProvider : SUB_PROVIDERS) {
            subProvider.addTags(sink);
        }
    }

    @Override
    @NotNull
    public String getName() {
        return "Domum Ornamentum Item Tags";
    }
}
