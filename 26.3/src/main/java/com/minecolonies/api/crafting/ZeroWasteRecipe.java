package com.minecolonies.api.crafting;

import com.google.common.collect.Lists;
import com.minecolonies.api.crafting.registry.ModRecipeSerializer;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.advancements.triggers.RecipeUnlockedTrigger;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeBuilder;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A shapeless recipe that discards any remaining items.  Mainly intended for mixing things into bottles or bowls
 * without leaving extra empties behind, but can be used for other things too.
 * <p>
 * 26.2 reshaped {@link ShapelessRecipe}: its constructor takes {@code (Recipe.CommonInfo, CraftingRecipe.CraftingBookInfo,
 * ItemStackTemplate, List&lt;Ingredient&gt;)}, {@link RecipeSerializer} became a record instead of an interface, and
 * {@code Ingredient.CODEC_NONEMPTY}/{@code ItemStack.STRICT_CODEC} are gone. Everything below follows the vanilla
 * shape from {@code /opt/mc-src/net/minecraft/world/item/crafting/ShapelessRecipe.java}.
 */
public class ZeroWasteRecipe extends ShapelessRecipe
{
    public static final MapCodec<ZeroWasteRecipe> CODEC = RecordCodecBuilder.mapCodec(
      builder -> builder.group(
        ItemStackTemplate.CODEC.fieldOf("result").forGetter(ZeroWasteRecipe::result),
        Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(ZeroWasteRecipe::ingredients)
      ).apply(builder, ZeroWasteRecipe::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ZeroWasteRecipe> STREAM_CODEC = StreamCodec.composite(
      ItemStackTemplate.STREAM_CODEC, ZeroWasteRecipe::result,
      Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), ZeroWasteRecipe::ingredients,
      ZeroWasteRecipe::new);

    private final ItemStackTemplate    result;
    private final List<Ingredient>     ingredients;

    public ZeroWasteRecipe(@NotNull final ItemStackTemplate output,
                           @NotNull final List<Ingredient> inputs)
    {
        super(new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""), output, inputs);
        this.result = output;
        this.ingredients = inputs;
    }

    public ZeroWasteRecipe(@NotNull final ItemStack output,
                           @NotNull final NonNullList<Ingredient> inputs)
    {
        this(ItemStackTemplate.fromNonEmptyStack(output), List.copyOf(inputs));
    }

    /**
     * @return the recipe result template.
     */
    public ItemStackTemplate result()
    {
        return this.result;
    }

    /**
     * @return the recipe inputs.
     */
    public List<Ingredient> ingredients()
    {
        return this.ingredients;
    }

    /**
     * Kept for the mod's own crafting/JEI code, which still asks a recipe for its ingredient list; 26.2 dropped
     * {@code getIngredients()} from the {@link Recipe} interface.
     *
     * @return the recipe inputs, as the mutable list type the mod expects.
     */
    @NotNull
    public NonNullList<Ingredient> getIngredients()
    {
        final NonNullList<Ingredient> list = NonNullList.create();
        list.addAll(this.ingredients);
        return list;
    }

    @NotNull
    @Override
    public NonNullList<ItemStack> getRemainingItems(@NotNull final CraftingInput input)
    {
        final NonNullList<ItemStack> remainingItems = super.getRemainingItems(input);
        Collections.fill(remainingItems, ItemStack.EMPTY);
        return remainingItems;
    }

    @NotNull
    @Override
    public RecipeSerializer<ShapelessRecipe> getSerializer()
    {
        // ShapelessRecipe#getSerializer is declared as RecipeSerializer<ShapelessRecipe> in 26.2; the cast is
        // safe because the serializer only ever produces ZeroWasteRecipe instances.
        return (RecipeSerializer<ShapelessRecipe>) (RecipeSerializer<?>) ModRecipeSerializer.ZeroWasteRecipeSerializer.get();
    }

    /**
     * 26.2 turned {@link RecipeSerializer} into a record of (mapCodec, streamCodec) — there is nothing left to
     * subclass, so the former {@code Serializer} class is now just a factory for that record.
     *
     * @return a fresh serializer instance for registration.
     */
    public static RecipeSerializer<ZeroWasteRecipe> createSerializer()
    {
        return new RecipeSerializer<>(CODEC, STREAM_CODEC);
    }

    public static Builder build(@NotNull final RecipeCategory category,
                                @NotNull final ItemLike output,
                                final int count)
    {
        return new Builder(category, new ItemStackTemplate(output.asItem(), count));
    }

    public static Builder build(@NotNull final RecipeCategory category,
                                @NotNull final ItemStackTemplate output)
    {
        return new Builder(category, output);
    }

    public static class Builder implements RecipeBuilder
    {
        private final RecipeCategory            category;
        private final ItemStackTemplate         output;
        private final List<Ingredient>          ingredients = Lists.newArrayList();
        private final Map<String, Criterion<?>> criteria    = new LinkedHashMap<>();

        public Builder(@NotNull final RecipeCategory category,
                       @NotNull final ItemStackTemplate output)
        {
            this.category = category;
            this.output = output;
        }

        public Builder requires(@NotNull final TagKey<Item> tag)
        {
            return this.requires(Ingredient.of(net.minecraft.core.registries.BuiltInRegistries.ITEM.getOrThrow(tag)));
        }

        public Builder requires(@NotNull final ItemLike item)
        {
            return this.requires(item, 1);
        }

        public Builder requires(@NotNull final ItemLike item, final int count)
        {
            for (int i = 0; i < count; ++i)
            {
                this.requires(Ingredient.of(item));
            }
            return this;
        }

        public Builder requires(@NotNull final Ingredient ingredient)
        {
            return this.requires(ingredient, 1);
        }

        public Builder requires(@NotNull final Ingredient ingredient, final int count)
        {
            for (int i = 0; i < count; ++i)
            {
                this.ingredients.add(ingredient);
            }
            return this;
        }

        @NotNull
        @Override
        public Builder unlockedBy(@NotNull final String name, @NotNull final Criterion<?> criterion)
        {
            this.criteria.put(name, criterion);
            return this;
        }

        @NotNull
        public Item getResult()
        {
            return this.output.item().value();
        }

        @NotNull
        @Override
        public RecipeBuilder group(@Nullable final String group)
        {
            return this;
        }

        @NotNull
        @Override
        public ResourceKey<Recipe<?>> defaultId()
        {
            return RecipeBuilder.getDefaultRecipeId(this.output);
        }

        @Override
        public void save(@NotNull final RecipeOutput consumer, @NotNull final ResourceKey<Recipe<?>> key)
        {
            save(consumer, key.identifier());
        }

        public void save(@NotNull final RecipeOutput consumer, @NotNull final Identifier id)
        {
            this.ensureValid(id);

            final ZeroWasteRecipe recipe = new ZeroWasteRecipe(this.output, List.copyOf(this.ingredients));

            final Advancement.Builder advancementBuilder = consumer.advancement();
            final ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
            advancementBuilder
              // 26.3: RecipeUnlockedTrigger.unlocked takes the recipe Holder now, not its ResourceKey; the
              // holder is looked up off the RecipeOutput exactly as RecipeUnlockAdvancementBuilder does
              // (RecipeUnlockAdvancementBuilder.java:28).
              .addCriterion("has_the_recipe", RecipeUnlockedTrigger.unlocked(consumer.lookup(Registries.RECIPE).getOrThrow(key)))
              .rewards(AdvancementRewards.Builder.recipe(key))
              .requirements(AdvancementRequirements.Strategy.OR);
            this.criteria.forEach(advancementBuilder::addCriterion);
            final AdvancementHolder advancement = advancementBuilder.build(id.withPrefix("recipes/" + this.category.getFolderName() + "/"));

            consumer.accept(key, recipe, advancement);
        }

        private void ensureValid(@NotNull final Identifier id)
        {
            if (this.criteria.isEmpty())
            {
                throw new IllegalStateException("No way of obtaining recipe " + id);
            }
        }
    }
}
