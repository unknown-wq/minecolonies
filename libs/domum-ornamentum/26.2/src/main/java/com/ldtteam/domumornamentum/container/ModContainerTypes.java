package com.ldtteam.domumornamentum.container;

import com.ldtteam.domumornamentum.util.Constants;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

public class ModContainerTypes
{
    public static Supplier<MenuType<ArchitectsCutterContainer>> ARCHITECTS_CUTTER =
        register("architects_cutter", new MenuType<>(ArchitectsCutterContainer::new, FeatureFlagSet.of()));

    /**
     * Class-load hook — registration happens eagerly in the static initialiser above (contract C1).
     */
    public static void init()
    {
    }

    private static <T extends AbstractContainerMenu> Supplier<MenuType<T>> register(final String name, final MenuType<T> type)
    {
        final MenuType<T> value = Registry.register(BuiltInRegistries.MENU, Constants.resLocDO(name), type);
        return () -> value;
    }

    private ModContainerTypes()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModContainerTypes. This is a utility class");
    }
}
