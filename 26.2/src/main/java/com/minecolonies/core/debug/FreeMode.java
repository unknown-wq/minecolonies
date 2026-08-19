package com.minecolonies.core.debug;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.requestsystem.manager.IRequestManager;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.IDeliverable;
import com.minecolonies.api.colony.requestsystem.requestable.Tool;
import com.minecolonies.api.colony.requestsystem.requester.IRequester;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.equipment.registry.EquipmentTypeEntry;
import com.minecolonies.api.inventory.api.IItemHandler;
import com.minecolonies.api.util.InventoryUtils;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.requestsystem.requesters.IBuildingBasedRequester;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Free mode: one colony wide switch that makes the colony work without any items at all.
 * <p>
 * The whole feature is reachable from this one symbol - {@code grep -rn FreeMode src/main/java} is a complete
 * inventory of it. The switch itself is a boolean on {@link Colony}, set by {@code /mc colony freemode <colony> on}.
 * <p>
 * Two mechanisms, deliberately kept apart:
 * <ul>
 *     <li>{@link #isOn} is read by the four hut accessors ({@code worksWithoutTools}, {@code worksWithoutMaterials},
 *     {@code worksWithoutFood}, {@code buildsWithoutResources}) and by the handful of AI sites that have to act
 *     rather than merely stop asking. Those guards keep a worker from stalling inside its own tick.</li>
 *     <li>{@link #fulfil} conjures the items for a request that nothing in the colony can serve, hooked into
 *     {@code StandardRetryingRequestResolver} - the resolver the request system hands a request to once every real
 *     resolver has declined it. That single interception covers every request in the mod, including the ones no hut
 *     setting could ever reach: crafting recipe inputs, furnace and brewing fuel, smeltable ore, the teacher's paper,
 *     guard weapons and armour.</li>
 * </ul>
 * Conjured items are ordinary items and are expected to leak into inventories and chests. There is no escrow and no
 * cleanup: this is a testing switch, not an economy.
 */
public final class FreeMode
{
    /**
     * NBT tag the switch is saved under, declared here rather than in {@code NbtTagConstants} so that deleting this
     * file leaves nothing of the feature behind anywhere else.
     */
    public static final String TAG_FREE_MODE = "freeMode";

    /**
     * The lowest equipment level free mode digs with: vanilla diamond.
     * <p>
     * The number is the equipment level {@code ModEquipmentTypes#initRegisterEquipmentTiers} derives from
     * {@code ToolMaterial#attackDamageBonus}, which is 3.0 for diamond (wood and gold 0, stone 1, iron 2,
     * netherite 4).
     * <p>
     * A floor, not a cap. {@code AbstractEntityAIBasic#holdEfficientTool} hands out the best tool at or above this
     * level and then holds whichever of the worker's tools actually breaks the block fastest, so a netherite pickaxe
     * already carried still wins over the diamond one handed out here.
     * <p>
     * Declared with the rest of the feature: outside free mode nothing reads it, and the hut level ceiling
     * ({@code IBuilding#getMaxEquipmentLevel}) that would normally refuse a diamond tool to a level one hut still
     * applies everywhere else.
     */
    public static final int MIN_DIG_LEVEL = 3;

    /**
     * Private constructor to hide the public one.
     */
    private FreeMode()
    {
        //Hides implicit constructor.
    }

    /**
     * Whether free mode is on for a colony.
     * <p>
     * Deliberately not on {@link IColony}: every behaviour that reads it runs on the server, so keeping the field on
     * {@link Colony} alone spares the interface a stub.
     * <p>
     * The one exception is the farm field size limit, which the scarecrow window has to agree with the server about
     * before it sends a resize; {@link IColonyView} therefore carries a synced copy and this method answers off it
     * when handed a client side view. Nothing else client side reads free mode.
     *
     * @param colony the colony, may be null or a client side view.
     * @return true if free mode is on.
     */
    public static boolean isOn(@Nullable final IColony colony)
    {
        if (colony instanceof final IColonyView view)
        {
            return view.isFreeMode();
        }
        return colony instanceof final Colony serverColony && serverColony.isFreeMode();
    }

    /**
     * Whether free mode is on for the colony a building belongs to.
     *
     * @param building the building, may be null.
     * @return true if free mode is on.
     */
    public static boolean isOn(@Nullable final IBuilding building)
    {
        return building != null && isOn(building.getColony());
    }

    /**
     * Put items into an inventory out of thin air.
     * <p>
     * One max size stack at a time through {@link InventoryUtils#addItemStackToItemHandler}, which dry runs the insert
     * first and so leaves the inventory untouched once it is full.
     *
     * @param target the inventory to fill.
     * @param stack  what to hand over, its own count is ignored.
     * @param count  how many items to hand over.
     * @return how many items were actually inserted, zero if there was no room at all.
     */
    public static int supply(@NotNull final IItemHandler target, @NotNull final ItemStack stack, final int count)
    {
        int inserted = 0;
        while (inserted < count)
        {
            final int batchCount = Math.min(count - inserted, stack.getMaxStackSize());
            if (!InventoryUtils.addItemStackToItemHandler(target, stack.copyWithCount(batchCount)))
            {
                break;
            }
            inserted += batchCount;
        }
        return inserted;
    }

    /**
     * Hand a worker a piece of equipment it would otherwise have had to request.
     * <p>
     * Supplying rather than suppressing matters for guards: {@code MeleeCombatAI#canAttack} and
     * {@code RangeCombatAI#canAttack} look the weapon up in the inventory themselves and refuse to swing without one,
     * so a guard that merely stops asking never fights. The same goes for armour, which
     * {@code AbstractEntityAIFight#equipInventoryArmor} only equips once it is actually carried.
     *
     * @param target        the inventory to equip.
     * @param equipmentType the kind of equipment wanted.
     * @param minLevel      the lowest acceptable level.
     * @param maxLevel      the highest acceptable level, usually the hut's.
     * @return true if something was handed over.
     */
    public static boolean equip(
      @NotNull final IItemHandler target,
      @NotNull final EquipmentTypeEntry equipmentType,
      final int minLevel,
      final int maxLevel)
    {
        final Tool tool = new Tool(equipmentType, minLevel, Math.max(minLevel, maxLevel));
        if (InventoryUtils.hasItemInItemHandler(target, tool::matches))
        {
            // Already carrying something that fits. Without this the guard gear loop, which asks again every time it
            // passes its hut, would keep conjuring a fresh set on every pass.
            return false;
        }

        final ItemStack best = pick(tool, candidatesFor(tool));
        return !ItemStackUtils.isEmpty(best) && supply(target, best, 1) > 0;
    }

    /**
     * Satisfy a request out of thin air and take it out of the system.
     * <p>
     * Called from {@code StandardRetryingRequestResolver#resolveRequest}, which is where the request system parks a
     * deliverable request that every real resolver has declined - so the colony's own chests, warehouses and crafters
     * still get first refusal and only the genuinely unservable requests are conjured.
     *
     * @param manager the request manager.
     * @param request the request to satisfy.
     * @return true if the request was satisfied and resolved, false if it was left alone.
     */
    public static boolean fulfil(@NotNull final IRequestManager manager, @NotNull final IRequest<?> request)
    {
        if (!isOn(manager.getColony()) || !(request.getRequest() instanceof final IDeliverable deliverable))
        {
            return false;
        }

        if (!conjureInto(manager, request, deliverable))
        {
            return false;
        }

        manager.updateRequestState(request.getId(), RequestState.RESOLVED);
        return true;
    }

    /**
     * Clear out every request a worker is still waiting on, so a worker that was already stuck starts working the
     * moment the switch is thrown.
     * <p>
     * Necessary because the toggles only ever stopped new requests being made. A request filed before free mode was
     * turned on keeps {@code AbstractBuilding#hasOpenSyncRequest} true, and the AI_BLOCKING target in
     * {@code AbstractEntityAIBasic} pins the worker in {@code NEEDS_ITEM} from any state for as long as that holds -
     * the worker never reaches its own states at all, so no guard further in can help it.
     * <p>
     * Only requests that nothing in the colony is actually working on are touched, judged the same way
     * {@code AbstractBuilding#overruleNextOpenRequestWithStack} judges it: parked on the player resolver or on the
     * retrying resolver, either directly or through a child. A delivery already on its way is left to arrive.
     * <p>
     * The request is overruled rather than resolved because it may have children (a warehouse resolution waiting on a
     * courier); overruling cancels those first, where resolving would leave the request sitting in
     * {@code FOLLOWUP_IN_PROGRESS} forever.
     *
     * @param building the worker's hut.
     * @param citizen  the worker.
     * @return true if anything was handed over.
     */
    public static boolean unstick(@NotNull final IBuilding building, @NotNull final ICitizenData citizen)
    {
        if (!isOn(building))
        {
            return false;
        }

        // -1 is the hut's own requests (minimum stock and the like), which have no citizen behind them but keep the
        // same "filed before the switch and never served" problem.
        final List<IRequest<?>> open = new ArrayList<>(building.getOpenRequests(citizen.getId()));
        open.addAll(building.getOpenRequests(-1));
        if (open.isEmpty())
        {
            return false;
        }

        final IRequestManager manager = building.getColony().getRequestManager();
        final List<IToken<?>> stuck = manager.getPlayerResolver().getAllAssignedRequests();
        final List<IToken<?>> retrying = manager.getRetryingRequestResolver().getAllAssignedRequests();

        boolean handedOver = false;
        for (final IRequest<?> request : open)
        {
            if (!(request.getRequest() instanceof final IDeliverable deliverable) || !isStuck(manager, request, stuck, retrying))
            {
                continue;
            }

            conjureInto(manager, request, deliverable);
            manager.updateRequestState(request.getId(), RequestState.OVERRULED);
            handedOver = true;
        }
        return handedOver;
    }

    /**
     * Whether a request is parked with nobody able to serve it, itself or through one of its children.
     *
     * @param manager  the request manager.
     * @param request  the request to judge.
     * @param stuck    the requests the player resolver holds.
     * @param retrying the requests the retrying resolver holds.
     * @return true if nothing in the colony is going to satisfy this.
     */
    private static boolean isStuck(
      @NotNull final IRequestManager manager,
      @NotNull final IRequest<?> request,
      @NotNull final List<IToken<?>> stuck,
      @NotNull final List<IToken<?>> retrying)
    {
        if (stuck.contains(request.getId()) || retrying.contains(request.getId()))
        {
            return true;
        }

        for (final IToken<?> child : request.getChildren())
        {
            final IRequest<?> childRequest = manager.getRequestForToken(child);
            if (childRequest != null && isStuck(manager, childRequest, stuck, retrying))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Put the items a request asked for wherever that request expects to find them.
     *
     * @param manager     the request manager.
     * @param request     the request.
     * @param deliverable what it asked for.
     * @return true if something was handed over.
     */
    private static boolean conjureInto(
      @NotNull final IRequestManager manager,
      @NotNull final IRequest<?> request,
      @NotNull final IDeliverable deliverable)
    {
        final ItemStack stack = pick(deliverable, candidatesFor(request, deliverable));
        if (ItemStackUtils.isEmpty(stack))
        {
            return false;
        }

        final IBuilding building = buildingOf(manager, request);
        if (building == null)
        {
            return false;
        }

        // The citizen that asked, if there was one. Requests made by a resolver on a hut's behalf - crafting recipe
        // inputs above all - name no citizen, so they go to whoever works there: both the crafting resolver's
        // "can this recipe be fulfilled" check and the crafter AI's own count the worker's inventory as well as the
        // hut's racks, so that is the one place that satisfies every reader.
        ICitizenData citizen = building.getCitizenForRequest(request.getId()).orElse(null);
        if (citizen == null)
        {
            citizen = building.getAllAssignedCitizen().stream().findFirst().orElse(null);
        }

        final int count = Math.max(deliverable.getCount(), 1);
        if (citizen != null && supply(citizen.getInventory(), stack, count) > 0)
        {
            return true;
        }

        // Nobody works here (a warehouse minimum stock request, say), or the worker is full up.
        return InventoryUtils.addItemStackToProvider(building, stack.copyWithCount(Math.min(count, stack.getMaxStackSize())));
    }

    /**
     * Find the hut a request was made for.
     *
     * @param manager the request manager.
     * @param request the request.
     * @return the hut, or null if the request did not come from one.
     */
    @Nullable
    private static IBuilding buildingOf(@NotNull final IRequestManager manager, @NotNull final IRequest<?> request)
    {
        final IRequester requester = request.getRequester();
        if (!(requester instanceof final IBuildingBasedRequester buildingBased))
        {
            return null;
        }

        return buildingBased.getBuilding(manager, request.getId())
                 .filter(IBuilding.class::isInstance)
                 .map(IBuilding.class::cast)
                 .orElse(null);
    }

    /**
     * The items that would satisfy a request.
     *
     * @param request     the request, whose display stacks are the request system's own answer to this question.
     * @param deliverable what it asked for.
     * @return the candidates, possibly empty.
     */
    private static List<ItemStack> candidatesFor(@NotNull final IRequest<?> request, @NotNull final IDeliverable deliverable)
    {
        final List<ItemStack> displayStacks = request.getDisplayStacks();
        return displayStacks.isEmpty() ? candidatesFor(deliverable) : displayStacks;
    }

    /**
     * The items that would satisfy a deliverable that has no request behind it yet.
     *
     * @param deliverable what is wanted.
     * @return the candidates, possibly empty.
     */
    private static List<ItemStack> candidatesFor(@NotNull final IDeliverable deliverable)
    {
        final List<ItemStack> all = IColonyManager.getInstance().getCompatibilityManager().getListOfAllItems();
        if (all.isEmpty())
        {
            return Collections.emptyList();
        }

        final List<ItemStack> candidates = new ArrayList<>();
        for (final ItemStack stack : all)
        {
            if (deliverable.matches(stack))
            {
                candidates.add(stack);
            }
        }
        return candidates;
    }

    /**
     * Choose which of the candidates to hand over.
     * <p>
     * Anything will do for a material, but not for equipment: a guard handed the first sword the registry offers ends
     * up with a wooden one, so equipment takes the best the request allows instead.
     *
     * @param deliverable what was asked for.
     * @param candidates  the items that would satisfy it.
     * @return the chosen stack, empty if there was nothing to choose from.
     */
    private static ItemStack pick(@NotNull final IDeliverable deliverable, @NotNull final List<ItemStack> candidates)
    {
        if (candidates.isEmpty())
        {
            return ItemStack.EMPTY;
        }

        if (!(deliverable instanceof final Tool tool))
        {
            return candidates.get(0).copy();
        }

        ItemStack best = ItemStack.EMPTY;
        int bestLevel = Integer.MIN_VALUE;
        for (final ItemStack candidate : candidates)
        {
            final int level = tool.getEquipmentType().getMiningLevel(candidate);
            if (level > bestLevel)
            {
                bestLevel = level;
                best = candidate;
            }
        }
        return best.copy();
    }
}
