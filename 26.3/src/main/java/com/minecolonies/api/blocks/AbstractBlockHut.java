package com.minecolonies.api.blocks;


// PORT-TODO(structurize): re-check list only. The 26.2 Structurize port landed on 31.07.2026, this file
// compiles against the real library and is part of the build; structurize-blocked.txt is nothing but the
// grep list of files worth re-verifying against the real API.

import com.minecolonies.api.blocks.interfaces.IBlockMinecolonies;
import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.blocks.interfaces.*;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.placement.structure.AbstractStructureHandler;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.MinecoloniesAPIProxy;
import com.minecolonies.api.blocks.interfaces.IBuildingBrowsableBlock;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.permissions.Action;
import com.minecolonies.api.items.ItemBlockHut;
import com.minecolonies.api.items.component.ModDataComponents;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.*;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.TranslationConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.minecolonies.api.inventory.api.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ldtteam.structurize.blockentities.interfaces.IBlueprintDataProviderBE.*;
import static com.minecolonies.api.util.constant.TranslationConstants.*;

/**
 * Base class for all Minecolonies Hut Blocks. Hut Blocks are the base blocks for Minecolonies buildings.
 * Extending this class enables all the blueprint functionalities.
 */
@SuppressWarnings("PMD.ExcessiveImports")
public abstract class AbstractBlockHut<B extends AbstractBlockHut<B>> extends AbstractColonyBlock<B> implements IAnchorBlock,
                                                                                                                        INamedBlueprintAnchorBlock,
                                                                                                                        ILeveledBlueprintAnchorBlock,
                                                                                                                        IRequirementsBlueprintAnchorBlock,
                                                                                                                        IInvisibleBlueprintAnchorBlock,
                                                                                                                        ISpecialCreativeHandlerAnchorBlock,
                                                                                                                        IBuildingBrowsableBlock

{
    /**
     * Constructor for a hut block.
     * <p>
     * Registers the block, sets the creative tab, as well as the resistance and the hardness.
     *
     * @param name the hut name, i.e. the registry path of the block; must match {@link #getHutName()}.
     */
    public AbstractBlockHut(final String name)
    {
        super(name);
    }

    /**
     * Constructor for a hut block.
     * <p>
     * Registers the block, sets the creative tab, as well as the resistance and the hardness.
     *
     * @param name       the hut name, i.e. the registry path of the block; must match {@link #getHutName()}.
     * @param properties custom properties.
     */
    public AbstractBlockHut(final String name, final Properties properties)
    {
        super(name, properties);
    }

    /**
     * Event-Handler for placement of this block.
     * <p>
     * Override for custom logic.
     *
     * @param worldIn the word we are in.
     * @param pos     the position where the block was placed.
     * @param state   the state the placed block is in.
     * @param placer  the player placing the block.
     * @param stack   the itemstack from where the block was placed.
     * @param rotMir  the mirror used.
     * @param style   the style of the building
     */
    public void onBlockPlacedByBuildTool(
      @NotNull final Level worldIn,
      @NotNull final BlockPos pos,
      final BlockState state,
      final LivingEntity placer,
      final ItemStack stack,
      final RotationMirror rotMir,
      final String style,
      final String blueprintPath)
    {
        final BlockEntity tileEntity = worldIn.getBlockEntity(pos);
        if (tileEntity instanceof final AbstractTileEntityColonyBuilding hut)
        {
            hut.setRotationMirror(rotMir);
            hut.setPackName(style);
            hut.setBlueprintPath(blueprintPath);
        }

        setPlacedBy(worldIn, pos, state, placer, stack);
    }

    @Override
    public boolean isVisible(@Nullable final CompoundTag beData)
    {
        final Map<BlockPos, List<String>> data = readTagPosMapFrom(beData.getCompoundOrEmpty(TAG_BLUEPRINTDATA));
        return !data.getOrDefault(BlockPos.ZERO, new ArrayList<>()).contains("invisible");
    }

    @Override
    @Environment(EnvType.CLIENT)
    public List<MutableComponent> getRequirements(final ClientLevel level, final BlockPos pos, final LocalPlayer player)
    {
        final List<MutableComponent> requirements = new ArrayList<>();
        final IColonyView colonyView = IColonyManager.getInstance().getClosestColonyView(level, pos);
        if (colonyView == null)
        {
            requirements.add(Component.translatableEscape("com.minecolonies.coremod.hut.incolony").setStyle((Style.EMPTY).withColor(ChatFormatting.RED)));
            return requirements;
        }

        if (InventoryUtils.findFirstSlotInItemHandlerWith(new InvWrapper(player.getInventory()), this) == -1)
        {
            requirements.add(Component.translatableEscape("com.minecolonies.coremod.hut.cost", Component.translatableEscape("block." + Constants.MOD_ID + "." + getHutName())).setStyle((Style.EMPTY).withColor(ChatFormatting.RED)));
            return requirements;
        }

        final Identifier effectId = colonyView.getResearchManager().getResearchEffectIdFrom(this);
        if (colonyView.getResearchManager().getResearchEffects().getEffectStrength(effectId) > 0)
        {
            return requirements;
        }

        if (MinecoloniesAPIProxy.getInstance().getGlobalResearchTree().getResearchForEffect(effectId) != null)
        {
            requirements.add(Component.translatableEscape(TranslationConstants.HUT_NEEDS_RESEARCH_TOOLTIP_1, getName()));
            requirements.add(Component.translatableEscape(TranslationConstants.HUT_NEEDS_RESEARCH_TOOLTIP_2, getName()));
        }

        return requirements;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public boolean areRequirementsMet(final ClientLevel level, final BlockPos pos, final LocalPlayer player)
    {
        if (player.isCreative())
        {
            return true;
        }
        return this.getRequirements(level, pos, player).isEmpty();
    }

    @Override
    public List<MutableComponent> getDesc()
    {
        final List<MutableComponent> desc = new ArrayList<>();
        desc.add(Component.translatableEscape(getBuildingEntry().getTranslationKey()));
        desc.add(Component.translatableEscape(getBuildingEntry().getTranslationKey() + ".desc"));
        return desc;
    }

    @Override
    public Component getBlueprintDisplayName()
    {
        return Component.translatableEscape(getBuildingEntry().getTranslationKey());
    }

    @Override
    public int getLevel(final CompoundTag beData)
    {
        if (beData == null)
        {
            return 0;
        }

        try
        {
            return Integer.parseInt(beData.getCompoundOrEmpty(TAG_BLUEPRINTDATA).getStringOr(TAG_SCHEMATIC_NAME, "").replaceAll("[^0-9]", ""));
        }
        catch (final NumberFormatException exception)
        {
            Log.getLogger().error("Couldn't get level from hut: " + getHutName() + ". Potential corrubt blockEntity data.");
            return 0;
        }
    }

    @Override
    public AbstractStructureHandler getStructureHandler(final Level level, final BlockPos blockPos, final Blueprint blueprint, final RotationMirror rotationMirror, final boolean b)
    {
        return new CreativeBuildingStructureHandler(level, blockPos, blueprint, rotationMirror, b);
    }

    @Override
    public boolean setup(
      final ServerPlayer player,
      final Level world,
      final BlockPos pos,
      final Blueprint blueprint,
      final RotationMirror rotationMirror,
      final boolean fancyPlacement,
      final String pack,
      final String path)
    {
        final BlockState anchor = blueprint.getBlockState(blueprint.getPrimaryBlockOffset());
        if (!(anchor.getBlock() instanceof AbstractBlockHut<?>) || (!fancyPlacement && player.isCreative()))
        {
            return true;
        }

        if (!IMinecoloniesAPI.getInstance().getConfig().getServer().blueprintBuildMode.get() && !canPaste(anchor.getBlock(), player, pos))
        {
            return false;
        }
        world.destroyBlock(pos, true);
        world.setBlockAndUpdate(pos, anchor);
        ((AbstractBlockHut<?>) anchor.getBlock()).onBlockPlacedByBuildTool(world,
          pos,
          anchor,
          player,
          null,
          rotationMirror,
          pack,
          path);

        if (IMinecoloniesAPI.getInstance().getConfig().getServer().blueprintBuildMode.get())
        {
            return true;
        }

        @Nullable final IBuilding building = IColonyManager.getInstance().getBuilding(world, pos);
        if (building == null)
        {
            if (anchor.getBlock() != ModBlocks.blockHutTownHall)
            {
                SoundUtils.playErrorSound(player, player.blockPosition());
                Log.getLogger().error("BuildTool: building is null!", new Exception());
                return false;
            }
        }
        else
        {
            SoundUtils.playSuccessSound(player, player.blockPosition());
            if (building.getTileEntity() != null)
            {
                final IColony colony = IColonyManager.getInstance().getColonyByPosFromWorld(world, pos);
                if (colony == null)
                {
                    Log.getLogger().info("No colony for " + player.getName().getString());
                    return false;
                }
                else
                {
                    building.getTileEntity().setColony(colony);
                }
            }

            // The level is the last character of the blueprint path, which is how the rest of the mod reads this
            // naming scheme -- WorkOrderBuilding#create rewrites exactly that character to ask for the next level.
            // Taking the character before it handed Integer.parseInt a letter for every real path
            // ("builder/builder1" -> "r"), so the catch below fired every time and a pasted blueprint always came
            // out as a level 1 building whatever level it was drawn at.
            final String adjusted = path.replace(".blueprint", "");
            final String num = adjusted.isEmpty() ? "" : adjusted.substring(adjusted.length() - 1);

            building.setStructurePack(pack);
            building.setBlueprintPath(path);
            try
            {
                building.setBuildingLevel(Integer.parseInt(num));
            }
            catch (final NumberFormatException ex)
            {
                building.setBuildingLevel(1);
            }

            building.setRotationMirror(rotationMirror);
            building.onUpgradeComplete(blueprint, building.getBuildingLevel());
        }
        return true;
    }

    /**
     * Check if we got permissions to paste.
     * @param anchor the anchor of the paste.
     * @param player the player pasting it.
     * @param pos the position its pasted at.
     * @return true if fine.
     */
    private boolean canPaste(final Block anchor, final Player player, final BlockPos pos)
    {
        final IColony colony = IColonyManager.getInstance().getIColony(player.level(), pos);

        if (colony == null)
        {
            if(anchor == ModBlocks.blockHutTownHall)
            {
                return true;
            }

            //  Not in a colony
            if (IColonyManager.getInstance().getIColonyByOwner(player.level(), player) == null)
            {
                MessageUtils.format(MESSAGE_WARNING_TOWN_HALL_NOT_PRESENT).sendTo(player);
            }
            else
            {
                MessageUtils.format(MESSAGE_WARNING_TOWN_HALL_TOO_FAR_AWAY).sendTo(player);
            }

            return false;
        }
        else if (!colony.getPermissions().hasPermission(player, Action.PLACE_HUTS))
        {
            //  No permission to place hut in colony
            MessageUtils.format(PERMISSION_OPEN_HUT, colony.getName()).sendTo(player);
            return false;
        }
        else
        {
            return colony.getServerBuildingManager().canPlaceAt(anchor, pos, player);
        }
    }

    /**
     * Get the blueprint name.
     * @return the name.
     */
    public String getBlueprintName()
    {
        return getBuildingEntry().getRegistryName().getPath();
    }

    // TODO(port-26.2): DISABLED — Block#appendHoverText no longer exists (only Item has it in 26.2), and
    // ItemStack#addToTooltip now needs a TooltipDisplay argument that only the item tooltip pipeline has. Hut item
    // tooltips therefore no longer show the building level / owning colony lines.

    @Override
    public void registerBlockItem(final Registry<Item> registry, final Item.Properties properties)
    {
        Registry.register(registry, getRegistryName(), new ItemBlockHut(this, IBlockMinecolonies.withBlockItemId(properties, getRegistryName())));
    }

    /**
     * Can this block be right-clicked without the appropriate permissions?
     * @return true if so. Default false.
     */
    public boolean canRightClickWithoutPermissions()
    {
        return false;
    }

    /**
     * Check if the block can be placed at the given position by the player.
     *
     * @param pos the position to check.
     * @param player the player trying to place the block.
     * @return true if the block can be placed.
     */
    public boolean canPlaceAt(final BlockPos pos, final Player player)
    {
        return true;
    }
}
