package com.minecolonies.apiimp.initializer;

import com.minecolonies.api.inventory.ModContainers;
import com.minecolonies.api.inventory.container.*;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.core.client.gui.containers.*;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

/**
 * Menu type registration.
 * <p>
 * <b>Port note (contracts C1/C5).</b> NeoForge's {@code IMenuTypeExtension.create(IContainerFactory)} is
 * replaced by Fabric's {@link ExtendedMenuType}. The container factories still have the old
 * {@code (int, Inventory, RegistryFriendlyByteBuf)} shape, so the extra screen-opening data type is the raw
 * buffer itself and {@link ScreenOpeningData#RAW_BUFFER} is the pass-through codec for it -- Fabric ships the
 * extra data as the tail of its own {@code fabric-menu} payload, so "read whatever is left" is well defined.
 * <p>
 * The server half of that contract is {@code net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider}: every
 * place that used to call {@code ServerPlayer#openMenu(MenuProvider, Consumer<FriendlyByteBuf>)} has to
 * implement {@code ExtendedMenuProvider<RegistryFriendlyByteBuf>} and return the filled buffer from
 * {@code getScreenOpeningData}.
 * <p>
 * {@code RegisterMenuScreensEvent} became {@link Client#registerScreens()}, called from the client
 * initializer; it lives in a nested {@link Environment}-annotated class so a dedicated server never loads
 * the screen classes.
 */
public class ModContainerInitializers
{
    /**
     * Holder for the extra-data codec.
     * <p>
     * It lives in a nested class on purpose: reading {@code ScreenOpeningData.RAW_BUFFER} initialises only this
     * class, not the enclosing one (JLS 12.4.1), so the codec can be exercised without triggering the menu
     * registration in the outer static initialiser -- which is what makes it reachable from a headless
     * round-trip check.
     */
    public static final class ScreenOpeningData
    {
        private ScreenOpeningData()
        {
            throw new IllegalStateException("Tried to initialize: ModContainerInitializers.ScreenOpeningData but this is a Utility class.");
        }

        /**
         * Codec for the extra screen-opening data: the payload's remaining bytes are handed to the container
         * factory as-is.
         * <p>
         * {@code decode} must actually <em>consume</em> the tail. {@code fabric-menu-api-v1} writes the extra
         * data last in its {@code fabric-menu-api-v1:open_screen} custom payload, and vanilla's
         * {@code ClientboundCustomPayloadPacket} reader asserts the payload buffer is fully drained afterwards
         * -- a codec that returns the buffer without moving the reader index leaves exactly the extra-data
         * bytes behind and the client dies with "was larger than I expected, found N bytes extra".
         * <p>
         * The bytes are copied out because the payload buffer is released once decoding finishes, while the
         * container factory only runs later, on the client thread.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, RegistryFriendlyByteBuf> RAW_BUFFER =
          new StreamCodec<>()
          {
              @Override
              public RegistryFriendlyByteBuf decode(final RegistryFriendlyByteBuf buffer)
              {
                  final int length = buffer.readableBytes();
                  final RegistryFriendlyByteBuf copy =
                    new RegistryFriendlyByteBuf(Unpooled.buffer(Math.max(length, 1)), buffer.registryAccess());
                  copy.writeBytes(buffer, length);
                  return copy;
              }

              @Override
              public void encode(final RegistryFriendlyByteBuf buffer, final RegistryFriendlyByteBuf value)
              {
                  buffer.writeBytes(value, value.readerIndex(), value.readableBytes());
              }
          };
    }

    static
    {
        ModContainers.craftingFurnace = register("crafting_furnace", ContainerCraftingFurnace::fromFriendlyByteBuf);
        ModContainers.buildingInv = register("building_inv", ContainerBuildingInventory::fromFriendlyByteBuf);
        ModContainers.citizenInv = register("citizen_inv", ContainerCitizenInventory::fromFriendlyByteBuf);
        ModContainers.craftingGrid = register("crafting_building", ContainerCrafting::fromFriendlyByteBuf);
        ModContainers.rackInv = register("rack_inv", ContainerRack::fromFriendlyByteBuf);
        ModContainers.graveInv = register("grave_inv", ContainerGrave::fromFriendlyByteBuf);
        ModContainers.craftingBrewingstand = register("crafting_brewingstand", ContainerCraftingBrewingstand::fromFriendlyByteBuf);
    }

    /**
     * Registers one menu type eagerly (contract C1: the handle stays a {@link Supplier}).
     *
     * @param path    the registry path.
     * @param factory the client-side container factory.
     * @return supplier of the registered menu type.
     */
    private static <T extends AbstractContainerMenu> Supplier<MenuType<T>> register(
      final String path,
      final ExtendedMenuType.ExtendedFactory<T, RegistryFriendlyByteBuf> factory)
    {
        final MenuType<T> value = Registry.register(BuiltInRegistries.MENU,
          Identifier.fromNamespaceAndPath(Constants.MOD_ID, path),
          new ExtendedMenuType<>(factory, ScreenOpeningData.RAW_BUFFER));
        return () -> value;
    }

    /**
     * Class-load hook. Registration happens in the static initialiser above (contract C1).
     */
    public static void init()
    {
    }

    /**
     * Client-only half: what {@code RegisterMenuScreensEvent} used to do.
     */
    @Environment(EnvType.CLIENT)
    public static final class Client
    {
        private Client()
        {
            throw new IllegalStateException("Tried to initialize: ModContainerInitializers.Client but this is a Utility class.");
        }

        /**
         * Binds every menu type to its screen. Called from the client initializer.
         */
        public static void registerScreens()
        {
            MenuScreens.register(ModContainers.craftingFurnace.get(), WindowFurnaceCrafting::new);
            MenuScreens.register(ModContainers.craftingGrid.get(), WindowCrafting::new);
            MenuScreens.register(ModContainers.craftingBrewingstand.get(), WindowBrewingstandCrafting::new);

            MenuScreens.register(ModContainers.buildingInv.get(), WindowBuildingInventory::new);
            MenuScreens.register(ModContainers.citizenInv.get(), WindowCitizenInventory::new);
            MenuScreens.register(ModContainers.rackInv.get(), WindowRack::new);
            MenuScreens.register(ModContainers.graveInv.get(), WindowGrave::new);
        }
    }
}
