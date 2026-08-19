package com.minecolonies.core.recipes;

import com.minecolonies.api.util.constant.Constants;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.stream.Stream;

import static com.minecolonies.api.util.ItemStackUtils.IS_ANY_FOOD;

/**
 * An ingredient that can be used in a vanilla recipe to match food items.
 * Only items with at least *some* healing and saturation are counted, and
 * further restrictions can be imposed if desired.
 *
 * <pre>
 * // any food item at all
 * {
 *     "fabric:type": "minecolonies:food"
 * }
 *
 * // any food with at least 4 healing points (inclusive)
 * {
 *     "fabric:type": "minecolonies:food",
 *     "min-healing": 4
 * }
 *
 * // any food with less than 1.0 saturation points (not including 1.0 itself)
 * {
 *     "fabric:type": "minecolonies:food",
 *     "max-saturation": 1.0
 * }
 * </pre>
 *
 * <p>Ported from NeoForge's {@code ICustomIngredient}/{@code IngredientType} to Fabric's
 * {@code CustomIngredient}/{@code CustomIngredientSerializer}, the same way agent A ported
 * {@link com.minecolonies.api.crafting.CountedIngredient}. The JSON discriminator key is {@code fabric:type}.</p>
 *
 * Conditions can also be combined.
 * Min bounds are inclusive and max bounds are exclusive.
 *
 * @param minHealing minimum healing value
 * @param maxHealing maximum healing value
 * @param minSaturation minimum saturation value
 * @param maxSaturation maximum saturation value
 */
public record FoodIngredient(@NotNull Optional<Integer> minHealing,
                             @NotNull Optional<Integer> maxHealing,
                             @NotNull Optional<Float> minSaturation,
                             @NotNull Optional<Float> maxSaturation) implements CustomIngredient
{
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "food");

    public static final MapCodec<FoodIngredient> CODEC = RecordCodecBuilder.mapCodec(builder -> builder
        .group(Codec.INT.optionalFieldOf("min-healing").forGetter(FoodIngredient::minHealing),
               Codec.INT.optionalFieldOf("max-healing").forGetter(FoodIngredient::maxHealing),
               Codec.FLOAT.optionalFieldOf("min-saturation").forGetter(FoodIngredient::minSaturation),
               Codec.FLOAT.optionalFieldOf("max-saturation").forGetter(FoodIngredient::maxSaturation))
        .apply(builder, FoodIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FoodIngredient> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), FoodIngredient::minHealing,
      ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), FoodIngredient::maxHealing,
      ByteBufCodecs.optional(ByteBufCodecs.FLOAT), FoodIngredient::minSaturation,
      ByteBufCodecs.optional(ByteBufCodecs.FLOAT), FoodIngredient::maxSaturation,
      FoodIngredient::new);

    /**
     * The Fabric serializer for this ingredient. Registered by {@code ModIngredientTypeInitializer}.
     */
    public static final CustomIngredientSerializer<FoodIngredient> SERIALIZER = new CustomIngredientSerializer<>()
    {
        @Override
        public Identifier getIdentifier()
        {
            return ID;
        }

        @Override
        public MapCodec<FoodIngredient> getCodec()
        {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FoodIngredient> getStreamCodec()
        {
            return STREAM_CODEC;
        }
    };

    public static Builder builder()
    {
        return new Builder();
    }

    private boolean matchesFood(@NotNull final ItemStack stack)
    {
        // 26.2: Item#getFoodProperties(ItemStack, LivingEntity) was a NeoForge extension; food is a data
        // component now, exactly as ItemStackUtils.IS_ANY_FOOD already reads it.
        final FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null)
        {
            return false;
        }
        return minHealing.map(healing -> food.nutrition() >= healing).orElse(true) &&
               maxHealing.map(healing -> food.nutrition() < healing).orElse(true) &&
               minSaturation.map(saturation -> food.saturation() >= saturation).orElse(true) &&
               maxSaturation.map(saturation -> food.saturation() < saturation).orElse(true);
    }

    @Override
    public boolean test(@Nullable final ItemStack stack)
    {
        if (stack == null)
        {
            return false;
        }

        return IS_ANY_FOOD.test(stack) && matchesFood(stack);
    }

    @NotNull
    @Override
    public Stream<Holder<Item>> items()
    {
        return BuiltInRegistries.ITEM.listElements()
                .filter(holder -> test(new ItemStack(holder)))
                .map(holder -> (Holder<Item>) holder);
    }

    @Override
    public boolean requiresTesting()
    {
        return false;
    }

    @NotNull
    @Override
    public CustomIngredientSerializer<?> getSerializer()
    {
        return SERIALIZER;
    }

    public static class Builder
    {
        private Optional<Integer> minHealing = Optional.empty();
        private Optional<Integer> maxHealing = Optional.empty();
        private Optional<Float> minSaturation = Optional.empty();
        private Optional<Float> maxSaturation = Optional.empty();

        private Builder()
        {
        }

        public Builder minHealing(final int healing) { minHealing = Optional.of(healing); return this; }
        public Builder maxHealing(final int healing) { maxHealing = Optional.of(healing); return this; }
        public Builder minSaturation(final float saturation) { minSaturation = Optional.of(saturation); return this; }
        public Builder maxSaturation(final float saturation) { maxSaturation = Optional.of(saturation); return this; }

        public Ingredient build()
        {
            return new FoodIngredient(minHealing, maxHealing, minSaturation, maxSaturation).toVanilla();
        }
    }
}
