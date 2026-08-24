package com.minecolonies.api.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Fabric 26.2 replacement for {@code net.neoforged.neoforge.common.crafting.SizedIngredient}: an {@link Ingredient}
 * plus a required count.
 * <p>
 * Used by the research cost system (`IGlobalResearch`, `GlobalResearch`, `ResearchListener`, the research data
 * provider). Vanilla has no equivalent — {@link Ingredient} carries no count and the vanilla recipe types encode
 * counts positionally — so the record is reproduced here with the same accessors ({@code ingredient()},
 * {@code count()}, {@code getItems()}) and the same two codec names the mod already uses.
 * <p>
 * The JSON shape is the NeoForge one, so existing research datapacks keep parsing:
 * <pre>{"item": "minecraft:cobblestone", "count": 16}</pre>
 * with {@code item} being anything vanilla's {@link Ingredient#CODEC} accepts (single item, list, or {@code #tag}).
 *
 * @param ingredient the underlying ingredient.
 * @param count      how many of it are needed.
 */
public record SizedIngredient(@NotNull Ingredient ingredient, int count)
{
    /**
     * The flat, inline form: {@code {"item": …, "count": n}}. Named to match the NeoForge constant the mod
     * already references.
     */
    private static final Codec<SizedIngredient> CURRENT_CODEC = RecordCodecBuilder.create(builder -> builder
        .group(Ingredient.CODEC.fieldOf("item").forGetter(SizedIngredient::ingredient),
          ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(SizedIngredient::count))
        .apply(builder, SizedIngredient::new));

    /**
     * The NeoForge-era spelling, {@code {"tag": "minecraft:planks", "count": 64}}, accepted on read only.
     * <p>
     * Port note (26.2): a tag ingredient used to be written as its own {@code "tag"} field; vanilla's
     * {@link Ingredient#CODEC} now takes it inline as {@code "item": "#minecraft:planks"}, which is what we write.
     * Third-party research datapacks are still out there in the old spelling -- seven of MineColonies' own
     * researches used it -- so reading it keeps them working. Nothing writes this form any more.
     */
    private static final Codec<SizedIngredient> LEGACY_TAG_CODEC = RecordCodecBuilder.create(builder -> builder
        .group(TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(i -> {
              throw new UnsupportedOperationException("read-only codec");
          }),
          ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(SizedIngredient::count))
        .apply(builder, (tag, count) -> SizedIngredient.of(tag, count)));

    /**
     * The flat, inline form: {@code {"item": …, "count": n}}. Named to match the NeoForge constant the mod
     * already references. Writes the current spelling, reads the legacy one too.
     */
    public static final Codec<SizedIngredient> FLAT_CODEC =
      Codec.withAlternative(CURRENT_CODEC, LEGACY_TAG_CODEC);

    /**
     * Alias kept for call sites that used the nested NeoForge codec.
     */
    public static final Codec<SizedIngredient> NESTED_CODEC = FLAT_CODEC;

    public static final StreamCodec<RegistryFriendlyByteBuf, SizedIngredient> STREAM_CODEC = StreamCodec.composite(
      Ingredient.CONTENTS_STREAM_CODEC, SizedIngredient::ingredient,
      ByteBufCodecs.VAR_INT, SizedIngredient::count,
      SizedIngredient::new);

    public SizedIngredient
    {
        if (count <= 0)
        {
            throw new IllegalArgumentException("Size-d ingredient count must be positive");
        }
    }

    /**
     * @param item  the item required.
     * @param count how many.
     * @return the sized ingredient.
     */
    public static SizedIngredient of(@NotNull final ItemLike item, final int count)
    {
        return new SizedIngredient(Ingredient.of(item), count);
    }

    /**
     * @param tag   the item tag required.
     * @param count how many.
     * @return the sized ingredient.
     */
    public static SizedIngredient of(@NotNull final TagKey<Item> tag, final int count)
    {
        return new SizedIngredient(Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(tag)), count);
    }

    /**
     * The datagen counterpart of {@link #of(TagKey, int)}.
     * <p>
     * Port note (26.2): {@code Ingredient} now holds a resolved {@code HolderSet} rather than a bare
     * {@code TagKey}, and {@code BuiltInRegistries.ITEM#getOrThrow(TagKey)} only finds tags that are already
     * <em>bound</em> -- which they are at runtime and are not during datagen, where a tag we are in the middle of
     * generating throws {@code Missing tag minecolonies:concrete}. The lookup a data provider is handed resolves
     * through {@code MappedRegistry}'s registration lookup instead, which creates the tag holder set on demand;
     * that is the same path vanilla's own {@code RecipeProvider} takes.
     *
     * @param lookup the item lookup from the provider's {@code HolderLookup.Provider}.
     * @param tag    the item tag required.
     * @param count  how many.
     * @return the sized ingredient.
     */
    public static SizedIngredient of(@NotNull final HolderGetter<Item> lookup, @NotNull final TagKey<Item> tag, final int count)
    {
        return new SizedIngredient(Ingredient.of(lookup.getOrThrow(tag)), count);
    }

    /**
     * @param stack the stack to test.
     * @return whether the stack matches the ingredient (count is deliberately not checked, matching NeoForge's
     *         {@code test}).
     */
    public boolean test(final ItemStack stack)
    {
        return this.ingredient.test(stack);
    }

    /**
     * @return one stack per matching item, each already sized to {@link #count()}.
     */
    public ItemStack[] getItems()
    {
        return this.ingredient.items()
                 .map(holder -> new ItemStack(holder, this.count))
                 .toArray(ItemStack[]::new);
    }

    /**
     * @return the first matching item, if any — used where only a representative is needed.
     */
    public Optional<Holder<Item>> firstItem()
    {
        return this.ingredient.items().findFirst();
    }
}
