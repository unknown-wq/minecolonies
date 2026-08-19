package com.minecolonies.core.items;


// PORT-NOTE(structurize): ported against the real Structurize 26.2 API (built 2026-07-31). Kept as a
// grep marker for files that touch Structurize, not as an open TODO.

import com.ldtteam.structurize.Structurize;
import com.ldtteam.structurize.blueprints.v1.Blueprint;
import com.ldtteam.structurize.blueprints.v1.BlueprintUtil;
import com.ldtteam.structurize.component.ModDataComponents;
import com.ldtteam.structurize.items.AbstractItemWithPosSelector;
import com.ldtteam.structurize.storage.rendering.RenderingCache;
import com.ldtteam.structurize.storage.rendering.types.BoxPreviewData;
import com.minecolonies.api.items.ModItems;
import com.minecolonies.api.items.component.Timestamp;
import com.minecolonies.core.client.assetfetch.AssetFetchGate;
import com.minecolonies.core.client.gui.WindowSchematicAnalyzer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static com.ldtteam.structurize.api.constants.TranslationConstants.MAX_SCHEMATIC_SIZE_REACHED;

/**
 * Item used to analyze schematics or selected blocks
 */
public class ItemScanAnalyzer extends AbstractItemWithPosSelector
{
    /**
     * NBT constants
     */
    public static String TEMP_SCAN = "selection.blueprint";

    /**
     * Time after which the selection is ignored
     */
    private static final int TIMEOUT_DELAY = 20 * 60 * 2;

    /**
     * Client side selection caching
     */
    private static BlockPos  lastPos   = BlockPos.ZERO;
    private static BlockPos  lastPos2  = BlockPos.ZERO;
    public static  Blueprint blueprint = null;

    public ItemScanAnalyzer(
      @NotNull final String name,
      final Item.Properties properties)
    {
        super(properties.durability(0)
            
            .rarity(Rarity.UNCOMMON)
            .component(ModDataComponents.POS_SELECTION, PosSelection.EMPTY));
    }

    /**
     * MC constructor.
     *
     * @param properties properties
     */
    public ItemScanAnalyzer(final Properties properties)
    {
        super(properties);
    }

    /**
     * Structurize: Prevent block breaking server side.
     * {@inheritDoc}
     */
    // 26.2: Item#canAttackBlock(BlockState, Level, BlockPos, Player) became
    // Item#canDestroyBlock(ItemStack, BlockState, Level, BlockPos, LivingEntity) -- the same migration
    // Structurize made in ItemScanTool/AbstractItemWithPosSelector.
    @Override
    public boolean canDestroyBlock(final ItemStack heldStack, final BlockState state, final Level worldIn, final BlockPos pos, final LivingEntity user)
    {
        checkTimeout(heldStack, worldIn);
        final boolean result = super.canDestroyBlock(heldStack, state, worldIn, pos, user);
        openAreaBox(heldStack);
        return result;
    }

    @Override
    public InteractionResult useOn(final UseOnContext context)
    {
        checkTimeout(context.getItemInHand(), context.getLevel());
        InteractionResult result = super.useOn(context);
        openAreaBox(context.getItemInHand());
        return result;
    }

    @Override
    public InteractionResult use(final Level worldIn, final Player playerIn, final InteractionHand handIn)
    {
        checkTimeout(playerIn.getItemInHand(handIn), worldIn);
        return super.use(worldIn, playerIn, handIn);
    }

    @Override
    public InteractionResult onAirRightClick(final BlockPos start, final BlockPos end, final Level worldIn, final Player playerIn, final ItemStack itemStack)
    {
        if (worldIn.isClientSide())
        {
            if (start != null && end != null && (!lastPos.equals(start) || !lastPos2.equals(end)))
            {
                lastPos = start;
                lastPos2 = end;
                final PosSelection data = PosSelection.readFromItemStack(itemStack);

                blueprint = saveStructure(worldIn, playerIn, AABB.encapsulatingFullBlocks(data.startPos().orElse(null), data.endPos().orElse(null)));
            }

            // Asset gate (D2): building the window loads its BlockUI XML, which is not in this jar.
            AssetFetchGate.openOrOffer(WindowSchematicAnalyzer::new);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public AbstractItemWithPosSelector getRegisteredItemInstance()
    {
        return (AbstractItemWithPosSelector) ModItems.scanAnalyzer;
    }

    /**
     * Opens an area selection for the selected positions
     *
     * @param tool
     */
    private void openAreaBox(final ItemStack tool)
    {
        final PosSelection component = PosSelection.readFromItemStack(tool);
        final BlockPos start = component.startPos().orElse(null);
        final BlockPos end = component.endPos().orElse(null);
        if (start != null && end != null)
        {
            RenderingCache.queue("analyzer", new BoxPreviewData(start, end, Optional.empty()));
        }
    }

    /**
     * Checks the selection timeout
     */
    protected void checkTimeout(final ItemStack stack, final Level level)
    {
        if (stack == null || level == null)
        {
            return;
        }

        Timestamp.updateItemStack(stack, component -> {
            if (component.hasTime() && (level.getGameTime() - component.time()) > TIMEOUT_DELAY)
            {
                PosSelection.EMPTY.writeToItemStack(stack);
            }

            return new Timestamp(level.getGameTime());
        });
    }

    /**
     * Scan the structure and save it as blueprint
     *
     * @param world  Current world.
     * @param player causing this action.
     */
    public static Blueprint saveStructure(final Level world, final Player player, AABB box)
    {
        if (box.getXsize() * box.getYsize() * box.getZsize() > Structurize.getConfig().getServer().schematicBlockLimit.get())
        {
            player.sendSystemMessage(Component.translatableEscape(MAX_SCHEMATIC_SIZE_REACHED, Structurize.getConfig().getServer().schematicBlockLimit.get()));
            return null;
        }

        final String fileName = TEMP_SCAN;
        final BlockPos zero = new BlockPos((int) box.minX, (int) box.minY, (int) box.minZ);
        final Blueprint bp =
          BlueprintUtil.createBlueprint(world, zero, false, (short) (box.getXsize() + 1), (short) (box.getYsize() + 1), (short) (box.getZsize() + 1), fileName, Optional.empty());

        return bp;
    }
}
