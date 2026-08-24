package com.ldtteam.domumornamentum.block;

import com.ldtteam.domumornamentum.shingles.ShingleHeightType;
import com.ldtteam.domumornamentum.util.Constants;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

import static com.ldtteam.domumornamentum.util.Constants.MOD_ID;

/**
 * Class used to handle the creativeTab of structurize.
 */
public final class ModCreativeTabs {

    public static final ResourceKey<CreativeModeTab> GENERAL_KEY = tabKey("general");
    public static final ResourceKey<CreativeModeTab> EXTRA_BLOCKS_KEY = tabKey("extra_blocks");
    public static final ResourceKey<CreativeModeTab> FLOATING_CARPETS_KEY = tabKey("floating_carpets");

    public static final Supplier<CreativeModeTab> GENERAL = register(GENERAL_KEY, () -> FabricCreativeModeTab.builder()
            .icon(() -> new ItemStack(ModBlocks.getInstance().getArchitectsCutter()))
            .title(Component.translatable("itemGroup." + MOD_ID + ".general"))
            .displayItems(new OutputAwareGenerator((config, output) -> {
        output.accept(ModBlocks.getInstance().getArchitectsCutter());
        ModBlocks.getInstance().getTimberFrames().forEach(output::accept);

        for (ShingleHeightType heightType : ShingleHeightType.values())
        {
            output.accept(ModBlocks.getInstance().getShingle(heightType));
        }

        output.accept(ModBlocks.getInstance().getShingleSlab());
        output.accept(ModBlocks.getInstance().getPaperWall());
        output.accept(ModBlocks.getInstance().getTiledPaperWall());

        ModBlocks.getInstance().getPillars().forEach(output::accept);
        ModBlocks.getInstance().getFramedLights().forEach(output::accept);
        ModBlocks.getInstance().getAllBrickBlocks().forEach(output::accept);

        output.accept(ModBlocks.getInstance().getFence());
        output.accept(ModBlocks.getInstance().getFenceGate());
        output.accept(ModBlocks.getInstance().getSlab());
        output.accept(ModBlocks.getInstance().getWall());
        output.accept(ModBlocks.getInstance().getStair());
        output.accept(ModBlocks.getInstance().getTrapdoor());
        output.accept(ModBlocks.getInstance().getDoor());
        output.accept(ModBlocks.getInstance().getPanel());
        output.accept(ModBlocks.getInstance().getPost());
        output.accept(ModBlocks.getInstance().getFancyDoor());
        output.accept(ModBlocks.getInstance().getFancyTrapdoor());
        output.accept(ModBlocks.getInstance().getDynamicTimberFrame());

            })).build());

    // TODO(port-26.2): DISABLED — `CreativeModeTab.Builder#withTabsBefore` no longer exists in 26.2
    // (/opt/mc-src/net/minecraft/world/item/CreativeModeTab.java:120-192); tab ordering is now decided by
    // fabric-creative-tab-api-v1, which appends modded tabs after the vanilla ones in registration order.
    // Original: .withTabsBefore(GENERAL.getId())
    public static final Supplier<CreativeModeTab> EXTRA_BLOCKS = register(EXTRA_BLOCKS_KEY, () -> FabricCreativeModeTab.builder()
            .icon(() -> new ItemStack(ModBlocks.getInstance().getExtraTopBlocks().get(0)))
            .title(Component.translatable("itemGroup." + MOD_ID + ".extra-blocks"))
            .displayItems(new OutputAwareGenerator((config, output) -> {
        ModBlocks.getInstance().getExtraTopBlocks().forEach(output::accept);
        ModBlocks.getInstance().getBricks().forEach(output::accept);
        output.accept(ModBlocks.getInstance().getStandingBarrel());
        output.accept(ModBlocks.getInstance().getLayingBarrel());
    })).build());

    // TODO(port-26.2): DISABLED — see EXTRA_BLOCKS above. Original: .withTabsBefore(EXTRA_BLOCKS.getId())
    public static final Supplier<CreativeModeTab> FLOATING_CARPETS = register(FLOATING_CARPETS_KEY, () -> FabricCreativeModeTab.builder()
            .icon(() -> new ItemStack(ModBlocks.getInstance().getFloatingCarpets().get(0)))
            .title(Component.translatable("itemGroup." + MOD_ID + ".floating-carpets"))
            .displayItems(new OutputAwareGenerator((config, output) -> {
        ModBlocks.getInstance().getFloatingCarpets().forEach(output::accept);
    })).build());

    private ModCreativeTabs()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModCreativeTabs. This is a utility class");
    }

    /**
     * Class-load hook — the tabs are registered eagerly by the static initialisers above (contract C1).
     * Must be called after {@link ModBlocks#init()}: the icon suppliers are lazy but the display generators
     * dereference {@link ModBlocks} the moment the creative screen is built.
     */
    public static void init()
    {
    }

    private static ResourceKey<CreativeModeTab> tabKey(final String name)
    {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Constants.resLocDO(name));
    }

    private static Supplier<CreativeModeTab> register(final ResourceKey<CreativeModeTab> key, final Supplier<CreativeModeTab> factory)
    {
        final CreativeModeTab tab = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, factory.get());
        return () -> tab;
    }

    private record OutputAwareGenerator(CreativeModeTab.DisplayItemsGenerator delegate) implements CreativeModeTab.DisplayItemsGenerator {

        @Override
        public void accept(@NotNull CreativeModeTab.ItemDisplayParameters featureFlagSet, @NotNull final CreativeModeTab.Output output) {
            delegate.accept(featureFlagSet, new Output(output));
        }

        private record Output(CreativeModeTab.Output delegate) implements CreativeModeTab.Output {

            @Override
            public void accept(@NotNull ItemStack stack, CreativeModeTab.@NotNull TabVisibility visibility) {
                delegate.accept(stack, visibility);
            }

            @Override
            public void accept(@NotNull ItemLike itemLike) {

                if (itemLike instanceof ICachedItemGroupBlock cachedItemGroupBlock) {
                    final NonNullList<ItemStack> stacks = NonNullList.create();
                    cachedItemGroupBlock.fillItemCategory(stacks);
                    stacks.forEach(delegate::accept);
                }
                else
                {
                    delegate.accept(itemLike);
                }
            }
        }
    }
}
