package com.minecolonies.core.client.render;

import com.minecolonies.core.entity.other.cavalry.CavalryHorseEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.animal.equine.BabyHorseModel;
import net.minecraft.client.model.animal.equine.EquineSaddleModel;
import net.minecraft.client.model.animal.equine.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.HorseMarkingLayer;
import net.minecraft.client.renderer.entity.layers.SimpleEquipmentLayer;
import net.minecraft.client.renderer.entity.state.HorseRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.Variant;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * Renderer for the cavalry horse.
 * <p>
 * PORT-26.2: {@code HorseRenderer} is {@code final} in 26.2, so the mod can no longer construct one and bolt an extra
 * layer onto it; the vanilla horse renderer is mirrored here instead. This is also the only place where the combat
 * readiness of the horse can be captured, because layers only ever see the render state. The value travels on the state
 * through Fabric's {@link RenderStateDataKey}, since {@link HorseRenderState} itself cannot be subclassed without
 * losing the vanilla {@link HorseMarkingLayer} (its generics are invariant).
 */
@Environment(EnvType.CLIENT)
public class CavalryHorseRenderer extends AbstractHorseRenderer<CavalryHorseEntity, HorseRenderState, HorseModel>
{
    /**
     * How many of the five readiness segments of the overlay texture to show.
     */
    public static final RenderStateDataKey<Integer> COMBAT_READINESS_SEGMENTS = RenderStateDataKey.create();

    private static final Map<Variant, Identifier> ADULT_BY_VARIANT = new EnumMap<>(Variant.class);
    private static final Map<Variant, Identifier> BABY_BY_VARIANT  = new EnumMap<>(Variant.class);

    static
    {
        put(Variant.WHITE, "white");
        put(Variant.CREAMY, "creamy");
        put(Variant.CHESTNUT, "chestnut");
        put(Variant.BROWN, "brown");
        put(Variant.BLACK, "black");
        put(Variant.GRAY, "gray");
        put(Variant.DARK_BROWN, "darkbrown");
    }

    private static void put(final Variant variant, final String name)
    {
        ADULT_BY_VARIANT.put(variant, Identifier.withDefaultNamespace("textures/entity/horse/horse_" + name + ".png"));
        BABY_BY_VARIANT.put(variant, Identifier.withDefaultNamespace("textures/entity/horse/horse_" + name + "_baby.png"));
    }

    public CavalryHorseRenderer(final EntityRendererProvider.Context context)
    {
        super(context, new HorseModel(context.bakeLayer(ModelLayers.HORSE)), new BabyHorseModel(context.bakeLayer(ModelLayers.HORSE_BABY)));
        this.addLayer(new HorseMarkingLayer(this));
        this.addLayer(new SimpleEquipmentLayer<>(this,
          context.getEquipmentRenderer(),
          EquipmentClientInfo.LayerType.HORSE_BODY,
          state -> state.bodyArmorItem,
          new HorseModel(context.bakeLayer(ModelLayers.HORSE_ARMOR)),
          null,
          2));
        this.addLayer(new SimpleEquipmentLayer<>(this,
          context.getEquipmentRenderer(),
          EquipmentClientInfo.LayerType.HORSE_SADDLE,
          state -> state.saddle,
          new EquineSaddleModel(context.bakeLayer(ModelLayers.HORSE_SADDLE)),
          null,
          2));
        this.addLayer(new CavalryOverlayLayer(this));
    }

    @NotNull
    @Override
    public HorseRenderState createRenderState()
    {
        return new HorseRenderState();
    }

    @Override
    public void extractRenderState(final CavalryHorseEntity entity, final HorseRenderState state, final float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        state.variant = entity.getVariant();
        state.markings = entity.getMarkings();
        state.bodyArmorItem = entity.getBodyArmorItem().copy();

        final float threshold = entity.getMaxHealth() * CavalryHorseEntity.COMBAT_READINESS_THRESHOLD;
        final float cooldown = Math.max(0f, entity.getAnimalDataView() == null ? 0 : entity.getAnimalDataView().getCombatCooldown());
        final float readiness = Mth.clamp(1.0f - (cooldown / Math.max(0.001f, threshold)), 0f, 1f);
        ((FabricRenderState) state).setData(COMBAT_READINESS_SEGMENTS, Mth.clamp((int) Math.floor(readiness * 5f + 0.0001f), 0, 5));
    }

    @NotNull
    @Override
    public Identifier getTextureLocation(@NotNull final HorseRenderState state)
    {
        final Map<Variant, Identifier> map = state.isBaby ? BABY_BY_VARIANT : ADULT_BY_VARIANT;
        final Identifier texture = map.get(state.variant);
        return texture != null ? texture : map.get(Variant.WHITE);
    }
}
