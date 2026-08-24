package com.minecolonies.core.recipes;

import com.minecolonies.api.util.constant.Constants;
import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient;
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * An ingredient that can be used in a vanilla recipe to match plantable items.
 *
 * <pre>
 * // any plant item
 * {
 *     "fabric:type": "minecolonies:plant"
 * }
 * </pre>
 *
 * <p>Ported from NeoForge's {@code ICustomIngredient}/{@code IngredientType} to Fabric's
 * {@code CustomIngredient}/{@code CustomIngredientSerializer} (fabric-recipe-api-v1), the same way agent A ported
 * {@link com.minecolonies.api.crafting.CountedIngredient}: {@code getType()} became {@link #getSerializer()},
 * {@code isSimple()} became the inverted {@link #requiresTesting()}, and {@code getItems()} returning
 * {@code Stream<ItemStack>} became {@link #items()} returning {@code Stream<Holder<Item>>}. The JSON
 * discriminator key is {@code fabric:type}, not {@code type}. {@code NeoForge}'s {@code Lazy} is replaced by a
 * plain memoising supplier.</p>
 */
public class PlantIngredient implements CustomIngredient
{
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "plant");

    private static PlantIngredient instance;

    /**
     * Lazily built because it walks the item registry, which is not populated at class-load time.
     *
     * @return the singleton.
     */
    private static synchronized PlantIngredient instance()
    {
        if (instance == null)
        {
            instance = new PlantIngredient();
        }
        return instance;
    }

    public static final MapCodec<PlantIngredient> CODEC = MapCodec.unit((Supplier<PlantIngredient>) PlantIngredient::instance);

    public static final StreamCodec<RegistryFriendlyByteBuf, PlantIngredient> STREAM_CODEC =
      StreamCodec.unit(instance());

    /**
     * The Fabric serializer for this ingredient. Registered by {@code ModIngredientTypeInitializer}.
     */
    public static final CustomIngredientSerializer<PlantIngredient> SERIALIZER = new CustomIngredientSerializer<>()
    {
        @Override
        public Identifier getIdentifier()
        {
            return ID;
        }

        @Override
        public MapCodec<PlantIngredient> getCodec()
        {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PlantIngredient> getStreamCodec()
        {
            return STREAM_CODEC;
        }
    };

    private final List<Holder<Item>> items;

    private PlantIngredient()
    {
        items = BuiltInRegistries.ITEM.listElements()
                .filter(holder -> holder.value() instanceof final BlockItem block &&
                        (block.getBlock() instanceof CropBlock || block.getBlock() instanceof StemBlock))
                .map(holder -> (Holder<Item>) holder)
                .toList();
    }

    @NotNull
    public static Ingredient of()
    {
        return instance().toVanilla();
    }

    @Override
    public boolean test(@Nullable final ItemStack stack)
    {
        if (stack == null)
        {
            return false;
        }

        return items.stream().anyMatch(stack::is);
    }

    @NotNull
    @Override
    public Stream<Holder<Item>> items()
    {
        return items.stream();
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
}
