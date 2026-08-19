package com.minecolonies.api.crafting;

import com.minecolonies.api.util.constant.Constants;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * An ingredient that can be used in a vanilla recipe to require more than one item in a particular input slot.
 * Similar to {@link SizedIngredient}, but this deliberately skips the count check in {@link #test(ItemStack)} to more
 * easily support consuming across multiple inventory slots -- and unlike that, it participates as a custom ingredient
 * directly.
 *
 * <pre>
 * {
 *     "fabric:type": "minecolonies:counted",
 *     "item": {
 *         "item": "minecraft:cobblestone"  // could be a tag or something else
 *     },
 *     "count": 16
 * }
 * </pre>
 *
 * <p>Ported from NeoForge's {@code ICustomIngredient}/{@code IngredientType} to Fabric's
 * {@code CustomIngredient}/{@code CustomIngredientSerializer} (fabric-recipe-api-v1). The two systems line up almost
 * one-to-one: {@code getType()} became {@link #getSerializer()}, {@code isSimple()} became the inverted
 * {@link #requiresTesting()}, and {@code getItems()} returning {@code Stream<ItemStack>} became {@link #items()}
 * returning {@code Stream<Holder<Item>>} — which means the per-item count no longer travels with the display stacks.
 * The JSON discriminator key is {@code fabric:type}, not {@code type}.</p>
 *
 * @param child the underlying ingredient.
 * @param count the number of items required.
 */
public record CountedIngredient(@NotNull Ingredient child, int count) implements CustomIngredient
{
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "counted");

    public static final MapCodec<CountedIngredient> CODEC = RecordCodecBuilder.mapCodec(builder -> builder
        .group(Ingredient.CODEC.fieldOf("item").forGetter(CountedIngredient::child),
          ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(CountedIngredient::count))
        .apply(builder, CountedIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CountedIngredient> STREAM_CODEC = StreamCodec.composite(
      Ingredient.CONTENTS_STREAM_CODEC, CountedIngredient::child,
      ByteBufCodecs.VAR_INT, CountedIngredient::count,
      CountedIngredient::new);

    /**
     * The Fabric serializer for this ingredient. Registered by the mod's ingredient initializer via
     * {@code CustomIngredientSerializer.register(CountedIngredient.SERIALIZER)}.
     */
    public static final CustomIngredientSerializer<CountedIngredient> SERIALIZER = new CustomIngredientSerializer<>()
    {
        @Override
        public Identifier getIdentifier()
        {
            return ID;
        }

        @Override
        public MapCodec<CountedIngredient> getCodec()
        {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CountedIngredient> getStreamCodec()
        {
            return STREAM_CODEC;
        }
    };

    public CountedIngredient
    {
        if (child.isEmpty() || count <= 0)
        {
            throw new IllegalArgumentException("Counted ingredient must have a child");
        }
    }

    /**
     * Creates a counted ingredient.
     *
     * @param child the underlying ingredient.
     * @param count the number of items required.
     * @return the counted ingredient.
     */
    public static Ingredient of(@NotNull final Ingredient child, final int count)
    {
        return count == 1 ? child : new CountedIngredient(child, count).toVanilla();
    }

    /**
     * Tests if the given stack matches the base ingredient.  Note: deliberately does *not* verify the count.
     *
     * @param stack the stack to test
     * @return true if the stack is the matching ingredient, regardless of count.
     */
    @Override
    public boolean test(@Nullable final ItemStack stack)
    {
        return stack != null && child.test(stack);
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

    @NotNull
    @Override
    public Stream<Holder<Item>> items()
    {
        return child.items();
    }
}
