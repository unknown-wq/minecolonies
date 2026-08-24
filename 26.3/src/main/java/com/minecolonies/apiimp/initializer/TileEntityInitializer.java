package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.tileentities.*;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.tileentities.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import java.util.Set;
import java.util.function.Supplier;

public class TileEntityInitializer
{

    static
    {
        MinecoloniesTileEntities.SCARECROW = register("scarecrow", () -> buildType(TileEntityScarecrow::new, ModBlocks.blockScarecrow));

        MinecoloniesTileEntities.PLANTATION_FIELD = register("plantationfield", () -> buildType(TileEntityPlantationField::new, ModBlocks.blockPlantationField));

        MinecoloniesTileEntities.BARREL = register("barrel", () -> buildType(TileEntityBarrel::new, ModBlocks.blockBarrel));

        MinecoloniesTileEntities.BUILDING = register("colonybuilding", () -> buildType(TileEntityColonyBuilding::new, ModBlocks.getHuts()));

        MinecoloniesTileEntities.DECO_CONTROLLER = register("decorationcontroller", () -> buildType(TileEntityDecorationController::new, ModBlocks.blockDecorationPlaceholder));

        MinecoloniesTileEntities.RACK = register("rack", () -> buildType(TileEntityRack::new, ModBlocks.blockRack));

        MinecoloniesTileEntities.GRAVE = register("grave", () -> buildType(TileEntityGrave::new, ModBlocks.blockGrave));

        MinecoloniesTileEntities.NAMED_GRAVE = register("namedgrave", () -> buildType(TileEntityNamedGrave::new, ModBlocks.blockNamedGrave));

        MinecoloniesTileEntities.WAREHOUSE = register("warehouse", () -> buildType(TileEntityWareHouse::new, ModBlocks.blockHutWareHouse));

        MinecoloniesTileEntities.COMPOSTED_DIRT = register("composteddirt", () -> buildType(TileEntityCompostedDirt::new, ModBlocks.blockCompostedDirt));

        MinecoloniesTileEntities.ENCHANTER = register("enchanter", () -> buildType(TileEntityEnchanter::new, ModBlocks.blockHutEnchanter));

        MinecoloniesTileEntities.STASH = register("stash", () -> buildType(TileEntityStash::new, ModBlocks.blockStash));

        MinecoloniesTileEntities.COLONY_FLAG = register("colony_flag", () -> buildType(TileEntityColonyFlag::new, ModBlocks.blockColonyBanner, ModBlocks.blockColonyWallBanner));

        MinecoloniesTileEntities.COLONY_SIGN = register("colonysign", () -> buildType(TileEntityColonySign::new, ModBlocks.blockColonySign));
    }

    /**
     * Registers one block entity type eagerly (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path     the registry path.
     * @param supplier factory for the type.
     * @return supplier of the registered type.
     */
    private static <T extends BlockEntity> Supplier<BlockEntityType<T>> register(
      final String path,
      final Supplier<BlockEntityType<T>> supplier)
    {
        final BlockEntityType<T> value = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path), supplier.get());
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1).
     */
    public static void init()
    {
    }

    /**
     * Builds a block entity type.
     * <p>
     * Port note: 26.2 removed {@code BlockEntityType.Builder}; the constructor takes the factory and the set
     * of valid blocks directly (see {@code net/minecraft/world/level/block/entity/BlockEntityType.java}).
     *
     * @param factory     the block entity factory.
     * @param validBlocks the blocks this type may be attached to.
     * @return the built (not yet registered) type.
     */
    private static <T extends BlockEntity> BlockEntityType<T> buildType(
      final BlockEntityType.BlockEntitySupplier<T> factory,
      final Block... validBlocks)
    {
        return new BlockEntityType<>(factory, Set.of(validBlocks));
    }
}
