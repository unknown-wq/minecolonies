package com.ldtteam.domumornamentum;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockManager;
import com.ldtteam.domumornamentum.block.IModBlocks;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.component.ModDataComponents;

/**
 * The central DO api.
 */
public interface IDomumOrnamentumApi
{
    /**
     * The current API instance.
     * @return The instance.
     */
    static IDomumOrnamentumApi getInstance() {
        return Holder.getInstance();
    }

    /**
     * Gives access to the blocks of the mod.
     * @return The blocks of this mod.
     */
    IModBlocks getBlocks();

    /**
     * The materially texturable block manager.
     * @return The manager
     */
    IMateriallyTexturedBlockManager getMateriallyTexturedBlockManager();

    /**
     * The material texture data component.
     *
     * <p>Port note (contract deviation, 26.1 NeoForge → 26.2 Fabric): this used to be declared as
     * {@code Supplier<DataComponentType<MaterialTextureData>>} and relied on NeoForge's
     * {@code ItemStack#set(Supplier<DataComponentType<T>>, T)} overloads, which vanilla does not have. The
     * concrete {@link ModDataComponents.ComponentType} is both, so every existing call site — the
     * {@code itemStack.set(api.getMaterialTextureComponentType(), …)} ones and the {@code .get()} ones —
     * keeps compiling unchanged.</p>
     */
    ModDataComponents.ComponentType<MaterialTextureData> getMaterialTextureComponentType();

    class Holder {
        private static IDomumOrnamentumApi apiInstance;

        public static IDomumOrnamentumApi getInstance()
        {
            return apiInstance;
        }

        public static void setInstance(final IDomumOrnamentumApi instance)
        {
            if (apiInstance != null)
                throw new IllegalStateException("Can not setup API twice!");

            apiInstance = instance;
        }
    }
}
