package com.ldtteam.domumornamentum.datagen.utils;

/**
 * One domain's worth of blockstates, block models and item models.
 *
 * <p>Port note (26.2 / Fabric): NeoForge's {@code BlockStateProvider} is gone; the Fabric replacement is a single
 * {@link net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider}. Running 23 separate ones would mean
 * 23 passes over the whole model output (and 23 chances at a duplicate write), so the domain providers were reduced
 * to sub providers of {@link com.ldtteam.domumornamentum.datagen.DomumOrnamentumModelProvider}.</p>
 */
public interface IBlockStateSubProvider {

    void generate(ModelCollector models);

    default String getName() {
        return getClass().getSimpleName();
    }
}
