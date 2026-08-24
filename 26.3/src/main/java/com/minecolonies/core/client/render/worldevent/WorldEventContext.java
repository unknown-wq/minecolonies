package com.minecolonies.core.client.render.worldevent;


import com.ldtteam.structurize.util.WorldRenderMacros;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.core.client.render.TileEntityColonySignRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Main class for handling world rendering.
 * Also holds all possible values which may be needed during rendering.
 * <p>
 * PORT-26.2: {@code Stage} now comes from Structurize's {@code WorldRenderMacros} (NeoForge's
 * {@code RenderLevelStageEvent.Stage} is gone); all three stages fire consecutively from a single
 * {@code LevelRenderEvents.COLLECT_SUBMITS} callback, so code that relied on stage ordering behaves differently.
 * {@code FastColor.ARGB32} became {@link ARGB}.
 */
public class WorldEventContext extends WorldRenderMacros
{
    public static final WorldEventContext INSTANCE = new WorldEventContext();

    private WorldEventContext()
    {
        // singleton
    }

    @Nullable
    public IColonyView nearestColony;

    @Override
    protected void renderWithinContext(final Stage stage)
    {
        if (stage == STAGE_FOR_LINES)
        {
            ColonyBorderRenderer.render(this); // renders directly (not into bufferSource)
            ColonyBlueprintRenderer.renderBlueprints(this);
            ColonyWaypointRenderer.render(this);
            ColonyPatrolPointRenderer.render(this);
            GuardTowerRallyBannerRenderer.render(this);
            PathfindingDebugRenderer.render(this);
            ColonyBlueprintRenderer.renderBoxes(this);
            ItemOverlayBoxesRenderer.render(this);
            HighlightManager.render(this);

        }
        else if (stage == Stage.AFTER_TRANSLUCENT_BLOCKS)
        {
            // PORT-26.2: RenderLevelStageEvent.Stage is NeoForge-only. Structurize's replacement enum has three
            //  values and no AFTER_TRIPWIRE_BLOCKS; all three now fire back to back from one COLLECT_SUBMITS
            //  callback anyway, so the sign hover just moved to the last of them.
            TileEntityColonySignRenderer.renderSignHover(this);
        }
    }

    boolean hasNearestColony()
    {
        return nearestColony != null;
    }

    /**
     * Checks for a nearby colony
     *
     * @param level
     */
    public void checkNearbyColony(final Level level)
    {
        if (clientPlayer != null)
        {
            nearestColony = IColonyManager.getInstance().getClosestColonyView(level, clientPlayer.blockPosition());
        }
    }

    public void renderLineBoxWithShadow(final BlockPos pos, final int argbColor, final float lineWidth)
    {
        final int red = ARGB.red(argbColor);
        final int green = ARGB.green(argbColor);
        final int blue = ARGB.blue(argbColor);
        final int alpha = ARGB.alpha(argbColor);

        renderLineBox(LINES_WITH_WIDTH_DEPTH_INVERT, pos, pos, red / 2, green / 2, blue / 2, alpha / 2, lineWidth);
        renderLineBox(LINES_WITH_WIDTH, pos, pos, red, green, blue, alpha, lineWidth);
    }

    public void renderLineAABBWithShadow(final AABB aabb, final int argbColor, final float lineWidth)
    {
        final int red = ARGB.red(argbColor);
        final int green = ARGB.green(argbColor);
        final int blue = ARGB.blue(argbColor);
        final int alpha = ARGB.alpha(argbColor);

        renderLineAABB(LINES_WITH_WIDTH_DEPTH_INVERT, aabb, red / 2, green / 2, blue / 2, alpha / 2, lineWidth);
        renderLineAABB(LINES_WITH_WIDTH, aabb, red, green, blue, alpha, lineWidth);
    }
}
