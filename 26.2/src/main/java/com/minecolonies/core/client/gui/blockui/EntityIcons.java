package com.minecolonies.core.client.gui.blockui;

import com.ldtteam.blockui.controls.EntityIcon;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Helpers for BlockUI's {@link EntityIcon}.
 * <p>
 * PORT-26.2: BlockUI's icon no longer takes an entity or an entity type — 26.2 renders from a render state, so the icon
 * takes an {@code EntityIcon.EntityIconState}. These two helpers rebuild the old {@code setEntity(...)} calls on top of
 * the state API (the same shapes BlockUI itself uses in {@code EntityIcon(PaneParams)}).
 */
@Environment(EnvType.CLIENT)
public final class EntityIcons
{
    private EntityIcons()
    {
        // utility
    }

    /**
     * Show a live entity in the icon; the render state is re-extracted every frame.
     *
     * @param icon   the icon pane.
     * @param entity the entity to show.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void setEntity(final EntityIcon icon, final Entity entity)
    {
        icon.setEntityState(new EntityIcon.DynamicState<>(entity, null));
    }

    /**
     * Show a "generic" instance of an entity type in the icon; the render state is built once and left at its defaults.
     *
     * @param icon the icon pane.
     * @param type the entity type to show.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void setEntityType(final EntityIcon icon, final EntityType<?> type)
    {
        final EntityRenderState probe = new EntityRenderState();
        probe.entityType = type;

        final EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(probe).createRenderState();
        state.entityType = type;

        icon.setEntityState(new EntityIcon.StaticState<>(state));
    }
}
