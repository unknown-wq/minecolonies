package com.minecolonies.api.client.render.modeltype;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;

/**
 * Render state of a MineColonies citizen.
 * <p>
 * 26.2 animates and submits models from a render state instead of from the entity, so everything the citizen models and
 * layers used to read off {@code AbstractEntityCitizen} has to be copied here in
 * {@code RenderBipedCitizen#extractRenderState}.
 */
@Environment(EnvType.CLIENT)
public class CitizenRenderState extends HumanoidRenderState
{
    /**
     * Working render meta marker, see {@link CitizenModel#isWorking(CitizenRenderState)}.
     */
    public static final String RENDER_META_WORKING = "working";

    /**
     * The raw render metadata string of the citizen ({@code AbstractEntityCitizen#getRenderMetadata()}).
     */
    public String renderMetadata = "";

    /**
     * Whether a job specific hat may be shown (no head armor, not sleeping, no custom skin).
     */
    public boolean displayHat = true;

    /**
     * Whether the citizen is female.
     */
    public boolean female = false;

    /**
     * The texture to render the body with.
     */
    public @Nullable Identifier texture = null;

    /**
     * The model chosen for this citizen. Resolved during extraction because the model registry lookup needs the entity.
     */
    public @Nullable CitizenModel<?> model = null;

    /**
     * Player profile of the custom citizen skin, or null when the citizen uses a normal texture.
     */
    public @Nullable ResolvableProfile customTextureProfile = null;

    /**
     * Status icon above the name tag, or null when there is none.
     */
    public @Nullable Identifier statusIcon = null;

    /**
     * Whether the citizen is currently riding something.
     */
    public boolean riding = false;

    /**
     * Civilian id of the citizen, used by the April-1st gag.
     */
    public int civilianId = 0;

    /**
     * Whether the citizen uses a custom player skin instead of a citizen texture.
     */
    public boolean hasCustomTexture = false;

    /**
     * Whether the citizen is rendered as a "ghost" (partially transparent preview).
     */
    public boolean ghost = false;

    /**
     * @return true when the citizen is flagged as working.
     */
    public boolean isWorking()
    {
        return this.renderMetadata.contains(RENDER_META_WORKING);
    }
}
