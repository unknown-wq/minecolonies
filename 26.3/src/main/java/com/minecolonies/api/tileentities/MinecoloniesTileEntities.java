package com.minecolonies.api.tileentities;

import com.minecolonies.core.tileentities.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.function.Supplier;

public class MinecoloniesTileEntities
{
    public static Supplier<BlockEntityType<TileEntityScarecrow>> SCARECROW;

    public static Supplier<BlockEntityType<TileEntityPlantationField>> PLANTATION_FIELD;

    public static Supplier<BlockEntityType<TileEntityBarrel>> BARREL;

    public static Supplier<BlockEntityType<TileEntityColonyBuilding>> BUILDING;

    public static Supplier<BlockEntityType<TileEntityDecorationController>> DECO_CONTROLLER;

    public static Supplier<BlockEntityType<TileEntityRack>> RACK;

    public static Supplier<BlockEntityType<TileEntityGrave>> GRAVE;

    public static Supplier<BlockEntityType<TileEntityNamedGrave>> NAMED_GRAVE;

    public static Supplier<BlockEntityType<TileEntityWareHouse>> WAREHOUSE;

    public static Supplier<BlockEntityType<TileEntityCompostedDirt>> COMPOSTED_DIRT;

    public static Supplier<BlockEntityType<TileEntityEnchanter>> ENCHANTER;

    public static Supplier<BlockEntityType<TileEntityStash>> STASH;

    public static Supplier<BlockEntityType<TileEntityColonyFlag>> COLONY_FLAG;

    public static Supplier<BlockEntityType<TileEntityColonySign>> COLONY_SIGN;
}