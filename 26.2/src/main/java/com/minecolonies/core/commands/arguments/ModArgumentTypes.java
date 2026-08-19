package com.minecolonies.core.commands.arguments;

import com.minecolonies.core.commands.colonycommands.CommandDeleteColony;
import com.mojang.brigadier.arguments.ArgumentType;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

import static com.minecolonies.api.util.constant.Constants.MOD_ID;

/**
 * This class handles registration for custom argument types.
 * <p>
 * <b>Port note (contracts C1/C5).</b> NeoForge's {@code DeferredRegister}/{@code DeferredHolder} are gone, and
 * so is {@code ArgumentTypeInfos#registerByClass} -- registering an argument type now means telling both the
 * registry and the brigadier class-to-info map, which is exactly what
 * {@code fabric-command-api-v2}'s {@link ArgumentTypeRegistry#registerArgumentType} does in one call.
 * Registration is eager, in the static initialiser, and the handles stay {@link Supplier}s so existing
 * {@code .get()} call sites keep compiling. {@link #init()} is the class-load hook the mod entry point calls.
 */
public class ModArgumentTypes
{
    public static final Supplier<SingletonArgumentInfo<ColonyIdArgument>> COLONY_ID =
      register("colony_id", ColonyIdArgument.class, SingletonArgumentInfo.contextFree(ColonyIdArgument::id));

    public static final Supplier<SingletonArgumentInfo<MultiColonyIdArgument>> MULTI_COLONY_ID =
      register("multi_colony_id", MultiColonyIdArgument.class, SingletonArgumentInfo.contextFree(MultiColonyIdArgument::id));

    /**
     * Lives here rather than in a static block on the type itself. That block only ran when the command was first
     * built, which is during {@code Commands.<init>} on the first datapack load -- by then the argument type
     * registry is frozen and the server dies with "Registry is already frozen (trying to add key
     * minecolonies:delete_buildings)" before it can load a world. This type had no registry id at all on NeoForge,
     * where {@code registerByClass} only filled the brigadier class map.
     */
    public static final Supplier<SingletonArgumentInfo<CommandDeleteColony.DeleteBuildingsArgumentType>> DELETE_BUILDINGS =
      register("delete_buildings", CommandDeleteColony.DeleteBuildingsArgumentType.class,
        SingletonArgumentInfo.contextFree(CommandDeleteColony.DeleteBuildingsArgumentType::argument));

    /**
     * Registers one argument type eagerly.
     *
     * @param path          the registry path.
     * @param argumentClass the brigadier argument type class.
     * @param info          the argument type info.
     * @return supplier of the registered info.
     */
    private static <A extends ArgumentType<?>, T extends ArgumentTypeInfo.Template<A>, I extends ArgumentTypeInfo<A, T>> Supplier<I> register(
      final String path,
      final Class<? extends A> argumentClass,
      final I info)
    {
        ArgumentTypeRegistry.registerArgumentType(Identifier.fromNamespaceAndPath(MOD_ID, path), argumentClass, info);
        return () -> info;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1).
     */
    public static void init()
    {
    }
}
