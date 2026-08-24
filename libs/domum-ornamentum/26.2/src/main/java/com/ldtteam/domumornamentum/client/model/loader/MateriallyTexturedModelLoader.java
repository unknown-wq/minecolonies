package com.ldtteam.domumornamentum.client.model.loader;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.client.model.baked.MateriallyTexturedBakedModel;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.Block;

/**
 * Fabric replacement for the NeoForge {@code IGeometryLoader} +
 * {@code ModelEvent.RegisterGeometryLoaders} registration.
 *
 * <h2>Why this is no longer a JSON loader</h2>
 * Contract C7 freezes the shipped model JSON as
 * <pre>{ "parent": "domum_ornamentum:block/…_spec", "loader": "domum_ornamentum:materially_textured" }</pre>
 * Fabric does have a JSON-dispatch hook - {@code UnbakedModelDeserializer.register(Identifier, …)} in
 * {@code fabric-model-loading-api-v1} - but it dispatches on a <b>{@code "fabric:type"}</b> key, not on
 * {@code "loader"}. Verified by decompiling
 * {@code net.fabricmc.fabric.impl.client.model.loading.UnbakedModelJsonDeserializer}, whose bytecode reads the
 * literal string {@code "fabric:type"} and nothing else. Renaming that key in the 197 shipped model files
 * would break C7, so that hook is deliberately not used.
 * <p>
 * Skipping it costs nothing: {@code "loader"} is simply an unknown key to vanilla. 26.2's model deserializer
 * ({@code /opt/mc-src/net/minecraft/client/resources/model/cuboid/CuboidModel.java}, inner
 * {@code Deserializer}) only reads {@code elements}, {@code parent}, {@code textures},
 * {@code ambientocclusion}, {@code display} and {@code gui_light}, and silently ignores everything else. The
 * shipped files therefore load as plain "inherit everything from my {@code _spec} parent" models - which is
 * exactly the geometry that has to be retextured.
 * <p>
 * The retexturing is attached per block instead of per model file, through
 * {@link ModelLoadingPlugin.Context#modifyBlockModelAfterBake()}: every block state of every
 * {@link IMateriallyTexturedBlock} gets its baked {@link BlockStateModel} wrapped in a
 * {@link MateriallyTexturedBakedModel}. That is strictly more robust than the JSON-driven version, because a
 * block whose model JSON lost the marker still gets retextured.
 */
public class MateriallyTexturedModelLoader implements ModelLoadingPlugin
{
    /**
     * Registers the plugin. Called from {@code ClientRegistrations#register()}.
     */
    public static void register()
    {
        ModelLoadingPlugin.register(new MateriallyTexturedModelLoader());
    }

    @Override
    public void initialize(final Context pluginContext)
    {
        pluginContext.modifyBlockModelAfterBake().register(
          ModelModifier.WRAP_PHASE,
          (model, context) -> {
              final Block block = context.state().getBlock();
              if (!(block instanceof IMateriallyTexturedBlock))
              {
                  return model;
              }

              if (model instanceof MateriallyTexturedBakedModel)
              {
                  return model;
              }

              return new MateriallyTexturedBakedModel(model);
          });
    }
}
