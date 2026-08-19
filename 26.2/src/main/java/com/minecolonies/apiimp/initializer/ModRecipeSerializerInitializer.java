package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.crafting.ZeroWasteRecipe;
import com.minecolonies.api.crafting.CompostRecipe;
import com.minecolonies.api.crafting.registry.ModRecipeSerializer;
import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import java.util.function.Supplier;

public final class ModRecipeSerializerInitializer
{

    static
    {
        ModRecipeSerializer.CompostRecipeSerializer = registerSerializer("composting", CompostRecipe::createSerializer);
        ModRecipeSerializer.CompostRecipeType = registerType("composting");

        ModRecipeSerializer.ZeroWasteRecipeSerializer = registerSerializer("zero_waste", ZeroWasteRecipe::createSerializer);
    }

    private ModRecipeSerializerInitializer()
    {
        throw new IllegalStateException("Tried to initialize: ModRecipeSerializerInitializer but this is a Utility class.");
    }

    /**
     * Registers one recipe serializer eagerly (contract C1: the handle stays a {@link Supplier}).
     */
    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> Supplier<RecipeSerializer<T>> registerSerializer(
      final String path,
      final Supplier<RecipeSerializer<T>> supplier)
    {
        final RecipeSerializer<T> value = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Registers one recipe type eagerly.
     * <p>
     * Port note: 26.2 has no {@code RecipeType.simple(Identifier)} any more -- {@code RecipeType} is a bare
     * marker interface and vanilla registers an anonymous instance (see
     * {@code net/minecraft/world/item/crafting/RecipeType.java}). Same shape here.
     */
    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> Supplier<RecipeType<T>> registerType(final String path)
    {
        final RecipeType<T> value = Registry.register(BuiltInRegistries.RECIPE_TYPE,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), new RecipeType<T>()
          {
              @Override
              public String toString()
              {
                  return path;
              }
          });
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1).
     */
    public static void init()
    {
    }
}
