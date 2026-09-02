package com.ldtteam.domumornamentum.recipe.architectscutter;

import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Factory for the architect's cutter recipe serializer.
 * <p>
 * 26.2: {@code RecipeSerializer} is no longer an interface to implement — it is a record
 * {@code RecipeSerializer<T>(MapCodec<T> codec, StreamCodec<RegistryFriendlyByteBuf, T> streamCodec)}
 * (/opt/mc-src/net/minecraft/world/item/crafting/RecipeSerializer.java:7). This class therefore only
 * hands out the ready-made instance instead of implementing the (now non-existent) interface.
 */
public final class ArchitectsCutterRecipeSerializer
{
    private ArchitectsCutterRecipeSerializer()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ArchitectsCutterRecipeSerializer. This is a utility class");
    }

    public static RecipeSerializer<ArchitectsCutterRecipe> create()
    {
        return new RecipeSerializer<>(ArchitectsCutterRecipe.CODEC, ArchitectsCutterRecipe.STREAM_CODEC);
    }
}
