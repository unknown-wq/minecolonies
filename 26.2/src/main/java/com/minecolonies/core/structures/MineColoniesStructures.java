package com.minecolonies.core.structures;

import com.minecolonies.api.util.constant.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.function.Supplier;

import static com.minecolonies.core.structures.EmptyColonyStructure.COLONY_CODEC;

/**
 * Thanks to: https://github.com/TelepathicGrunt/StructureTutorialMod/tree/1.18.x-Forge-Jigsaw
 * <p>
 * <b>Port note (contracts C1/C5).</b> NeoForge's {@code DeferredRegister}/{@code DeferredHolder} are gone;
 * registration is eager in the static initialiser and the handle stays a {@link Supplier} so the existing
 * {@code .get()} call site in {@link EmptyColonyStructure} keeps compiling. {@link #init()} is the class-load
 * hook the mod entry point calls.
 */
public class MineColoniesStructures
{
    /**
     * Empty colony structure.
     */
    public static final Supplier<StructureType<EmptyColonyStructure>> EMPTY_COLONY = register("empty_colony", () -> COLONY_CODEC);

    /**
     * Registers one structure type eagerly.
     *
     * @param path the registry path.
     * @param type the structure type.
     * @return supplier of the registered type.
     */
    private static <T extends net.minecraft.world.level.levelgen.structure.Structure> Supplier<StructureType<T>> register(
      final String path,
      final StructureType<T> type)
    {
        final StructureType<T> value = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), type);
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1).
     */
    public static void init()
    {
    }
}
