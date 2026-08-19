package com.minecolonies.api.inventory;

import com.minecolonies.api.inventory.container.*;
import net.minecraft.world.inventory.MenuType;
import java.util.function.Supplier;

public class ModContainers
{
    public static Supplier<MenuType<ContainerCraftingFurnace>> craftingFurnace;

    public static Supplier<MenuType<ContainerBuildingInventory>> buildingInv;

    public static Supplier<MenuType<ContainerCitizenInventory>> citizenInv;

    public static Supplier<MenuType<ContainerRack>> rackInv;

    public static Supplier<MenuType<ContainerGrave>> graveInv;

    public static Supplier<MenuType<ContainerCrafting>> craftingGrid;

    public static Supplier<MenuType<ContainerCraftingBrewingstand>> craftingBrewingstand;
}
