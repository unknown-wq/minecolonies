package com.ldtteam.domumornamentum.client.event.handlers;

import com.ldtteam.domumornamentum.client.screens.ArchitectsCutterScreen;
import com.ldtteam.domumornamentum.container.ModContainerTypes;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * The client-side mod-bus registrations that survived the port.
 *
 * <p>Contract C5: the {@code @EventBusSubscriber(bus = MOD, value = Dist.CLIENT)} class becomes a plain
 * {@code register()} called from {@code ClientRegistrations}.
 *
 * <p>Two of the three original blocks are gone:
 * <ul>
 *   <li><b>{@code ItemProperties.register(...)}</b> — TODO(port-26.2): DISABLED.
 *       {@code net.minecraft.client.renderer.item.ItemProperties} does not exist in 26.2 (no such file
 *       anywhere under {@code /opt/mc-src}). Float item properties were replaced by the data-driven item model
 *       tree in {@code assets/&lt;ns&gt;/items/&lt;item&gt;.json}:
 *       {@code net.minecraft.client.renderer.item.RangeSelectItemModel} /
 *       {@code SelectItemModel} / {@code ConditionalItemModel}, each with a
 *       {@code net.minecraft.client.renderer.item.properties.*} source. The DO trapdoor/door/post model
 *       overrides therefore have to move into datagen (agent D) as a {@code select} item model keyed on the
 *       {@code minecraft:block_state} property, not into code.</li>
 *   <li><b>{@code ItemBlockRenderTypes.setRenderLayer(...)}</b> — TODO(port-26.2): DISABLED.
 *       {@code ItemBlockRenderTypes} does not exist in 26.2 either. Chunk layers are now decided per quad from
 *       the sprite's alpha ({@code BakedQuad.MaterialInfo.of(...)} calls
 *       {@code ChunkSectionLayer.byTransparency(sprite.transparency())}) or forced from the model JSON via a
 *       material's {@code "force_translucent"} flag
 *       ({@code /opt/mc-src/net/minecraft/client/resources/model/sprite/Material.java}). Fabric's
 *       {@code BlockRenderLayerMap} was removed for the same reason.</li>
 * </ul>
 *
 * <p>Original NeoForge implementation of the two removed blocks:
 * <pre>
 * &#64;SubscribeEvent
 * public static void onFMLClientSetup(final FMLClientSetupEvent event)
 * {
 *     event.enqueueWork(() -&gt; ItemProperties.register(IModBlocks.getInstance().getTrapdoor().asItem(),
 *       Constants.TRAPDOOR_MODEL_OVERRIDE,
 *       (itemStack, clientLevel, livingEntity, i) -&gt; getTypeOrdinal(itemStack, TrapdoorType.class, TrapdoorType.FULL)));
 *     … (door, fancy door, fancy trapdoor, panel, post) …
 *
 *     event.enqueueWork(() -&gt; {
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getArchitectsCutter(), RenderType.cutout());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getStandingBarrel(),  RenderType.cutout());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getLayingBarrel(),    RenderType.cutout());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getShingleSlab(),     RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getPaperWall(),       RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getFence(),           RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getFenceGate(),       RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getSlab(),            RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getStair(),           RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getWall(),            RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getFancyDoor(),       RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getFancyTrapdoor(),   RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getTrapdoor(),        RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getDoor(),            RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getPanel(),           RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getPost(),            RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getTiledPaperWall(),  RenderType.translucent());
 *         ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getDynamicTimberFrame(), RenderType.translucent());
 *
 *         for (final ShingleHeightType heightType : ShingleHeightType.values())
 *             ItemBlockRenderTypes.setRenderLayer(IModBlocks.getInstance().getShingle(heightType), RenderType.translucent());
 *
 *         IModBlocks.getInstance().getFloatingCarpets().forEach(b -&gt; ItemBlockRenderTypes.setRenderLayer(b, RenderType.cutout()));
 *         IModBlocks.getInstance().getTimberFrames().forEach(b -&gt; ItemBlockRenderTypes.setRenderLayer(b, RenderType.translucent()));
 *         IModBlocks.getInstance().getAllBrickBlocks().forEach(b -&gt; ItemBlockRenderTypes.setRenderLayer(b, RenderType.solid()));
 *         IModBlocks.getInstance().getExtraTopBlocks().forEach(b -&gt; ItemBlockRenderTypes.setRenderLayer(b,
 *             ((ExtraBlock) b).getType().isTranslucent() ? RenderType.translucent() : RenderType.solid()));
 *     });
 * }
 *
 * private static &lt;T extends Enum&lt;T&gt;&gt; float getTypeOrdinal(final ItemStack itemStack, final Class&lt;T&gt; enumClass, final T defaultValue)
 * {
 *     final String type = itemStack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY)
 *                                  .properties().get(Constants.TYPE_BLOCK_PROPERTY);
 *     if (type == null) return defaultValue.ordinal();
 *     try { return Enum.valueOf(enumClass, type.toUpperCase()).ordinal(); }
 *     catch (Exception e) { return defaultValue.ordinal(); }
 * }
 * </pre>
 */
public final class ModBusEventHandler
{
    private ModBusEventHandler()
    {
        throw new IllegalStateException("Can not instantiate an instance of: ModBusEventHandler. This is a utility class");
    }

    /**
     * Registers the Architect's Cutter screen.
     *
     * <p>{@code RegisterMenuScreensEvent} has no Fabric counterpart; the vanilla
     * {@code MenuScreens.register(MenuType, ScreenConstructor)} is used instead. Both the method and the
     * {@code ScreenConstructor} interface are {@code private} in raw vanilla and are widened to public by
     * {@code fabric-transitive-access-wideners-v1} - see the javadoc note in
     * {@code /opt/mc-src/net/minecraft/client/gui/screens/MenuScreens.java}:60,113.
     */
    public static void register()
    {
        MenuScreens.register(ModContainerTypes.ARCHITECTS_CUTTER.get(), ArchitectsCutterScreen::new);
    }
}
