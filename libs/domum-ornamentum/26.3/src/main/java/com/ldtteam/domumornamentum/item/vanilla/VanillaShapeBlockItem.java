package com.ldtteam.domumornamentum.item.vanilla;

import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlock;
import com.ldtteam.domumornamentum.block.IMateriallyTexturedBlockComponent;
import com.ldtteam.domumornamentum.client.model.data.MaterialTextureData;
import com.ldtteam.domumornamentum.item.BlockItemWithClientBePlacement;
import com.ldtteam.domumornamentum.item.interfaces.IDoItem;
import com.ldtteam.domumornamentum.util.BlockUtils;
import com.ldtteam.domumornamentum.util.Constants;
import com.ldtteam.domumornamentum.util.MaterialTextureDataUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Item for the materially textured copies of the vanilla shapes - slab, stair, wall, fence and fence gate.
 *
 * <p>All five have exactly one component and name themselves after whatever block was put in it, so they only
 * differ in the block they carry and in the translation key that formats the name. They used to be five
 * byte-identical classes.</p>
 *
 * @param <B> the block this item places
 */
public class VanillaShapeBlockItem<B extends Block & IMateriallyTexturedBlock> extends BlockItemWithClientBePlacement implements IDoItem
{
    private final B      block;
    private final String nameFormatKey;

    /**
     * @param blockIn the block this item places
     * @param shape   the shape's key segment, i.e. {@code slab} for {@code domum_ornamentum.slab.name.format}
     * @param builder the item properties
     */
    public VanillaShapeBlockItem(final B blockIn, final String shape, final Properties builder)
    {
        super(blockIn, builder);
        this.block = blockIn;
        this.nameFormatKey = Constants.MOD_ID + "." + shape + ".name.format";
    }

    @Override
    public Component getName(final ItemStack stack)
    {
        final MaterialTextureData textureData = MaterialTextureData.readFromItemStack(stack);

        final IMateriallyTexturedBlockComponent coverComponent = block.getMainComponent();
        final Block centerBlock = textureData.getTexturedComponents().getOrDefault(coverComponent.getId(), coverComponent.getDefault());
        final Component centerBlockName = BlockUtils.getHoverName(centerBlock);

        return Component.translatable(nameFormatKey, centerBlockName);
    }

    @Override
    public void appendHoverText(final ItemStack stack, final TooltipContext tooltipContext, final TooltipDisplay tooltipDisplay, final Consumer<Component> tooltip, final TooltipFlag flagIn)
    {
        super.appendHoverText(stack, tooltipContext, tooltipDisplay, tooltip, flagIn);
        tooltip.accept(Component.translatable(Constants.MOD_ID + ".origin.tooltip"));

        MaterialTextureData textureData = MaterialTextureData.readFromItemStack(stack);
        if (textureData.isEmpty()) {
            textureData = MaterialTextureDataUtil.generateRandomTextureDataFrom(stack);
        }

        final IMateriallyTexturedBlockComponent component = block.getMainComponent();
        final Block materialBlock = textureData.getTexturedComponents().getOrDefault(component.getId(), component.getDefault());
        tooltip.accept(Component.translatable(Constants.MOD_ID + ".desc.onlyone", Component.translatable(Constants.MOD_ID + ".desc.material", BlockUtils.getHoverName(materialBlock))));
    }

    @Override
    public Identifier getGroup()
    {
        return Constants.resLocDO("avanilla");
    }
}
