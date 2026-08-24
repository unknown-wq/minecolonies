package com.minecolonies.api.client.render.modeltype;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.NotNull;

/**
 * Citizen model.
 * <p>
 * PORT-26.2: models animate from a {@link CitizenRenderState} instead of from the entity. Everything the per-job models
 * used to read off the citizen (render metadata, hat visibility, pose, civilian id) is copied onto the state by
 * {@code RenderBipedCitizen#extractRenderState}.
 */
public class CitizenModel<T extends AbstractEntityCitizen> extends HumanoidModel<CitizenRenderState>
{
    /**
     * Working render meta.
     */
    private static final String RENDER_META_WORKING = CitizenRenderState.RENDER_META_WORKING;

    public static boolean isItApril1st = false;

    public CitizenModel(final ModelPart part)
    {
        super(part, RenderTypes::entityCutout);
    }

    @Override
    public void setupAnim(@NotNull final CitizenRenderState state)
    {
        super.setupAnim(state);

        if (body.xRot == 0)
        {
            body.xRot = getActualRotation(state);
        }

        if (head.xRot == 0)
        {
            head.xRot = getActualRotation(state);
        }

        // The head of a citizen with a custom player skin is drawn by CitizenArmorLayer instead.
        final boolean ownHead = !state.hasCustomTexture;
        head.visible = ownHead;
        hat.visible = ownHead;

        if (isItApril1st)
        {
            switch (state.civilianId % 7)
            {
                case 0:
                    leftArm.visible = false;
                    break;
                case 1:
                    rightArm.visible = false;
                    break;
                case 2:
                    body.visible = false;
                    break;
                case 3:
                    head.visible = false;
                    break;
                case 4:
                    hat.visible = false;
                    break;
                case 5:
                    leftLeg.visible = false;
                    break;
                case 6:
                    rightLeg.visible = false;
                    break;
                default:
                    break;
            }
        }
    }

    public static LayerDefinition createMesh()
    {
        MeshDefinition meshdefinition = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        return LayerDefinition.create(meshdefinition, 64, 32);
    }

    /**
     * Override to change body rotation.
     *
     * @param state the render state of the citizen.
     * @return the rotation.
     */
    public float getActualRotation(@NotNull final CitizenRenderState state)
    {
        return 0;
    }

    /**
     * Check if the citizen is supposed to be working.
     *
     * @param state the render state of the citizen.
     * @return true if so.
     */
    public boolean isWorking(@NotNull final CitizenRenderState state)
    {
        return state.renderMetadata.contains(RENDER_META_WORKING);
    }

    /**
     * Check if the hat should be displayed.
     *
     * @param state the render state of the citizen.
     * @return true if so.
     */
    public boolean displayHat(@NotNull final CitizenRenderState state)
    {
        return state.displayHat;
    }

    /**
     * Resolve, while extracting the render state, whether the job hat may be shown for this citizen.
     *
     * @param citizen the citizen entity to check.
     * @return true if so.
     */
    public static boolean shouldDisplayHat(@NotNull final AbstractEntityCitizen citizen)
    {
        if (citizen.getPose() == Pose.SLEEPING || !citizen.getItemBySlot(EquipmentSlot.HEAD).isEmpty())
        {
            return false;
        }
        return citizen.getCitizenDataView() == null
                 || (citizen.getCitizenDataView().getDisplayArmor(EquipmentSlot.HEAD).isEmpty()
                       && citizen.getCitizenDataView().getCustomTextureUUID() == null);
    }
}
