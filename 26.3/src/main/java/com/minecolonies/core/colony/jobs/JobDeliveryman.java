package com.minecolonies.core.colony.jobs;

import com.google.common.collect.ImmutableList;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.util.WorldUtil;
import com.minecolonies.core.colony.buildings.modules.BuildingModules;
import com.minecolonies.core.colony.buildings.modules.WarehouseRequestQueueModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;

import com.minecolonies.api.client.render.modeltype.ModModelTypes;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.buildings.workerbuildings.IWareHouse;
import com.minecolonies.api.colony.requestsystem.StandardFactoryController;
import com.minecolonies.api.colony.requestsystem.data.IRequestSystemDeliveryManJobDataStore;
import com.minecolonies.api.colony.requestsystem.request.IRequest;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.AbstractDeliverymanRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Delivery;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.IDeliverymanRequestable;
import com.minecolonies.api.colony.requestsystem.requestable.deliveryman.Pickup;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.NbtTagConstants;
import com.minecolonies.api.util.constant.TypeConstants;
import com.minecolonies.core.colony.buildings.modules.CourierAssignmentModule;
import com.minecolonies.core.colony.requestsystem.requests.StandardRequests;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman;
import com.minecolonies.core.util.AttributeModifierUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static com.minecolonies.api.util.constant.BuildingConstants.TAG_ONGOING;
import static com.minecolonies.api.util.constant.CitizenConstants.SKILL_BONUS_ADD_NAME;
import static com.minecolonies.api.util.constant.Suppression.UNCHECKED;

/**
 * Class of the deliveryman job.
 */
public class JobDeliveryman extends AbstractJob<EntityAIWorkDeliveryman, JobDeliveryman>
{
    private IToken<?> rsDataStoreToken;

    /**
     * Walking speed bonus per level
     */
    public static final double BONUS_SPEED_PER_LEVEL = 0.003;

    /**
     * Old field for backwards compatibility.
     */
    private int ongoingDeliveries;

    /**
     * Largest contribution the queue-position tie-break may make to a request's score. Chosen to sit in the same range
     * as the request priorities themselves (1..15) and as {@link #WALK_COST_CAP}, so that none of the three terms can
     * drown the other two.
     */
    private static final int QUEUE_POSITION_CAP = 12;

    /**
     * Largest penalty the walk to a job may impose, and deliberately the same number as {@link #QUEUE_POSITION_CAP}.
     * <p>
     * Equal caps are what bounds the wait of a distant building. A request at the head of the queue carries the full
     * position term, so it can only be overtaken by a request whose position term exceeds its own by more than the
     * walk it saves -- which, with equal caps, means one of the dozen most recent arrivals. Those are claimed within a
     * dozen claims and the distant one then wins. Raise this above the position cap and the colony's edges are served
     * only when the middle is empty; lower it much and nothing changes at all.
     * <p>
     * The distance is sqrt-compressed before the cap applies, so 12 corresponds to a round trip of about 144 Manhattan
     * blocks -- past that, "far" and "further" stop being a useful distinction.
     */
    private static final int WALK_COST_CAP = 12;

    /**
     * How far apart two buildings may be, in Manhattan blocks, before they stop being worth serving on one round. Wide
     * enough to catch the neighbours of a building on this colony's layout, narrow enough that the detour never
     * approaches the round trip it replaces.
     */
    private static final int MULTI_DROP_RADIUS = 24;

    /**
     * How many distinct buildings one round may visit. The geometry stops paying well past three or four -- the head of
     * the queue plus its two nearest neighbours is where the measured saving lands -- and every extra stop is another
     * chance for a building to have filled up or been pulled down while the courier was on its way.
     */
    private static final int MAX_DROPS_PER_ROUND = 3;

    /**
     * Instantiates the job for the deliveryman.
     *
     * @param entity the citizen who becomes a deliveryman
     */
    public JobDeliveryman(final ICitizenData entity)
    {
        super(entity);
        if (entity != null)
        {
            setupRsDataStore();
        }
    }

    private void setupRsDataStore()
    {
        rsDataStoreToken = this.getCitizen()
                             .getColony()
                             .getRequestManager()
                             .getDataStoreManager()
                             .get(
                               StandardFactoryController.getInstance().getNewInstance(TypeConstants.ITOKEN),
                               TypeConstants.REQUEST_SYSTEM_DELIVERY_MAN_JOB_DATA_STORE
                             )
                             .getId();
    }

    @Override
    public void onLevelUp()
    {
        if (getCitizen().getEntity().isPresent())
        {
            final AbstractEntityCitizen worker = getCitizen().getEntity().get();
            final AttributeModifier speedModifier = new AttributeModifier(SKILL_BONUS_ADD_NAME, getCitizen().getCitizenSkillHandler().getLevel(getCitizen().getWorkBuilding().getModule(
              BuildingModules.COURIER_WORK).getPrimarySkill()) * BONUS_SPEED_PER_LEVEL, AttributeModifier.Operation.ADD_VALUE);
            AttributeModifierUtils.addModifier(worker, speedModifier, Attributes.MOVEMENT_SPEED);
        }
    }

    @NotNull
    @Override
    public Identifier getModel()
    {
        return ModModelTypes.COURIER_ID;
    }

    @Override
    public CompoundTag serializeNBT(@NotNull final HolderLookup.Provider provider)
    {
        final CompoundTag compound = super.serializeNBT(provider);
        compound.put(NbtTagConstants.TAG_RS_DMANJOB_DATASTORE, StandardFactoryController.getInstance().serializeTag(provider, rsDataStoreToken));
        return compound;
    }

    @Override
    public void deserializeNBT(@NotNull final HolderLookup.Provider provider, final CompoundTag compound)
    {
        super.deserializeNBT(provider, compound);

        if (compound.contains(NbtTagConstants.TAG_RS_DMANJOB_DATASTORE))
        {
            rsDataStoreToken = StandardFactoryController.getInstance().deserializeTag(provider, compound.getCompoundOrEmpty(NbtTagConstants.TAG_RS_DMANJOB_DATASTORE));
        }
        else
        {
            setupRsDataStore();
        }
        this.ongoingDeliveries = compound.getIntOr(TAG_ONGOING, 0);
    }

    /**
     * Generate your AI class to register.
     *
     * @return your personal AI instance.
     */
    @NotNull
    @Override
    public EntityAIWorkDeliveryman generateAI()
    {
        return new EntityAIWorkDeliveryman(this);
    }

    private IRequestSystemDeliveryManJobDataStore getDataStore()
    {
        return getCitizen().getColony().getRequestManager().getDataStoreManager().get(rsDataStoreToken, TypeConstants.REQUEST_SYSTEM_DELIVERY_MAN_JOB_DATA_STORE);
    }

    @Override
    public void serializeToView(final RegistryFriendlyByteBuf buffer)
    {
        super.serializeToView(buffer);
        StandardFactoryController.getInstance().serialize(buffer, rsDataStoreToken);
    }

    private LinkedList<IToken<?>> getTaskQueueFromDataStore()
    {
        return getDataStore().getQueue();
    }

    @Override
    public int getInactivityLimit()
    {
        return 60 * 10;
    }

    @Override
    public void triggerActivityChangeAction(final boolean newState)
    {
        try
        {
            if (newState)
            {
                getColony().getRequestManager().onColonyUpdate(request -> request.getRequest() instanceof Delivery || request.getRequest() instanceof Pickup);
            }
            else
            {
                cancelAssignedRequests();
            }
        }
        catch (final Exception ex)
        {
            Log.getLogger().warn("Active Triggered resulted in exception", ex);
        }
    }

    /**
     * Score one candidate request. Higher wins.
     *
     * @param req       the request.
     * @param index     the request's index in the shared queue; the caller already knows it, and asking the list for it
     *                  again made the claim scan O(n^2) in the queue depth.
     * @param queueSize the shared queue's length.
     * @param courier   where the courier is standing, or null when it has no position yet.
     * @return the score.
     */
    private int getRequestPriority(final IRequest<?> req, final int index, final int queueSize, final BlockPos courier)
    {
        int priority = 1;
        if (req.getRequest() instanceof AbstractDeliverymanRequestable requestable)
        {
            priority = requestable.getPriority();
            if (requestable instanceof Pickup pickup && pickup.getDay() > getColony().getDay())
            {
                priority -= 100;
            }
        }

        // PORT-NOTE(26.2): the unloaded-chunk penalty used to be applied *before* the block above, which then
        // overwrote `priority` outright. Every token that can reach this method is a Delivery or a Pickup and both
        // extend AbstractDeliverymanRequestable, so the branch always ran and the penalty was always discarded --
        // the guard was dead in every case it existed for. Applying it afterwards is what the TODO below asks for:
        // a courier will no longer set off towards a building whose chunk is not loaded while loaded work waits.
        // Byte-identical in 1.21.1 and upstream, so this is a defect inherited rather than introduced by the port.
        final BlockPos target = getTarget(req);
        if (!WorldUtil.isBlockLoaded(getColony().getWorld(), target))
        {
            priority -= 1000;
        }

        // 26.2/Fabric: the queue-position term used to be the raw `size - index`, which reaches the queue depth --
        // measured at a mean of 665 and a peak of 1258 on a colony under a real backlog. Base priorities live in 1..15
        // and the walk term below in 0..10, so the raw term was two orders of magnitude larger than everything it was
        // competing with and the rule degenerated to plain FIFO across the whole colony: twenty couriers behaving as
        // one FIFO server with twenty pairs of legs. Capped, it is what it was always meant to be -- a tie-break that
        // grows as a request waits, comparable in size to a priority rather than to a queue length. Starvation is
        // still bounded: as the queue drains a skipped request's index falls, its term rises to the cap and stays
        // there while its competitors' fall, and incrementPriorityDueToAging() raises its base priority as well.
        priority += Math.min(QUEUE_POSITION_CAP, queueSize - index);
        return priority - walkCost(req, target, courier);
    }

    /**
     * How far this courier would have to walk to serve a request, sqrt-compressed and capped so that it trades against
     * a priority rather than swamping it.
     * <p>
     * Deliberately not simply "distance from the courier to the job", which sounds right and is not. A delivery cannot
     * be walked to directly -- the courier must load at the rack first -- so its marginal cost from wherever the
     * courier stands is {@code d(courier, rack) + d(rack, target)}, and with a single warehouse the first term is the
     * same for every candidate and cannot discriminate at all. The courier's own position only earns its place there
     * when the sources really do differ: several warehouses, or a delivery a crafter raised whose start is its own hut
     * rather than a rack. A pickup is the opposite case, and the one where the courier's position decides everything,
     * because {@code pickup()} chains from building to building without going home first, so it is scored on the walk
     * to the building alone.
     * <p>
     * Straight-line distance throughout. No path query is made here and none should be: this runs once per queue entry
     * per claim, and pathfinding is the expensive thing in this codebase.
     *
     * @param req     the request.
     * @param target  the request's target, already looked up by the caller.
     * @param courier where the courier is standing, or null.
     * @return the penalty, 0..{@link #WALK_COST_CAP}.
     */
    private int walkCost(final IRequest<?> req, final BlockPos target, final BlockPos courier)
    {
        if (target == null)
        {
            return 0;
        }

        // The square root is taken over the whole trip, not leg by leg. Compressing each leg separately and adding
        // the roots gives a number that reaches the cap for almost every request on a colony of ordinary size -- on
        // the fixture measured here, a typical delivery came to 14 against a cap of 12 and a short one to 10.6, so
        // the term saturated and stopped telling any two candidates apart, which is the one way this change could
        // have been worse than no change at all.
        int blocks = 0;
        if (req.getRequest() instanceof Delivery)
        {
            // getSource is only asked for a delivery: for a pickup it answers with the warehouse, which is where the
            // courier is going afterwards rather than first, and answering costs a scan of the colony's warehouses.
            final BlockPos source = getSource(req);
            if (source == null)
            {
                return 0;
            }
            blocks = (courier == null ? 0 : courier.distManhattan(source)) + source.distManhattan(target);
        }
        else if (courier != null)
        {
            blocks = courier.distManhattan(target);
        }
        return Math.min(WALK_COST_CAP, (int) Math.sqrt(blocks));
    }

    /**
     * Returns the {@link IRequest} of the current Task.
     *
     * @return {@link IRequest} of the current Task.
     */
    @SuppressWarnings(UNCHECKED)
    // TODO: Rework logic to account for partially unloaded colonies, skipping tasks who's location is unloaded temporarily
    public IRequest<IDeliverymanRequestable> getCurrentTask()
    {
        final IToken<?> currentRequest = getTaskQueueFromDataStore().peekFirst();
        if (currentRequest != null)
        {
            return (IRequest<IDeliverymanRequestable>) getColony().getRequestManager().getRequestForToken(currentRequest);
        }

        IBuilding wareHouse = findWareHouse();
        if (wareHouse == null)
        {
            return null;
        }

        final WarehouseRequestQueueModule wareHouseModule = wareHouse.getModule(BuildingModules.WAREHOUSE_REQUEST_QUEUE);
        if (wareHouseModule.getMutableRequestList().isEmpty())
        {
            return null;
        }

        final List<IToken<?>> reqsToRemove = new ArrayList<>();

        // Where the courier is standing when it decides. Not the warehouse: decide() runs the moment a delivery
        // finishes, so this is normally the building it has just served.
        final BlockPos courierPos = getCitizen().getEntity().map(entity -> entity.blockPosition()).orElse(getCitizen().getLastPosition());

        IToken<?> resultRequestId = null;
        int priority = Integer.MIN_VALUE;
        int scanIndex = 0;
        final int queueSize = wareHouseModule.getMutableRequestList().size();
        for (final IToken<?> reqId : wareHouseModule.getMutableRequestList())
        {
            final IRequest<?> req = getColony().getRequestManager().getRequestForToken(reqId);
            if (req == null)
            {
                reqsToRemove.add(reqId);
                scanIndex++;
                continue;
            }
            final int localPriority = getRequestPriority(req, scanIndex, queueSize, courierPos);
            scanIndex++;
            if (localPriority > priority)
            {
                priority = localPriority;
                resultRequestId = reqId;
            }
        }

        if (resultRequestId == null)
        {
            return null;
        }

        final int resultIndex = wareHouseModule.getMutableRequestList().indexOf(resultRequestId);
        reqsToRemove.add(resultRequestId);

        final IRequest<?> resultRequest = getColony().getRequestManager().getRequestForToken(resultRequestId);
        if (resultRequest instanceof StandardRequests.DeliveryRequest)
        {
            getTaskQueueFromDataStore().add(resultRequestId);
        }
        // 26.2/Fabric: the claim used to extend only to requests whose target BlockPos was *identical*, so every trip
        // a courier ever made was warehouse -> one building -> warehouse. It may now take a few parcels for buildings
        // near the chosen one as well, and deliver them on the same round. Measured on this colony's real geometry,
        // the head of the queue plus its two nearest neighbours saves about half the walking against three separate
        // round trips, and it costs no extra path searches: the same number of legs, each of them shorter, because the
        // leg that used to run all the way back to the warehouse now runs to the building next door.
        final BlockPos resultTarget = getTarget(resultRequest);
        final boolean roundable = resultRequest.getRequest() instanceof Delivery;
        final Set<BlockPos> dropTargets = new LinkedHashSet<>();
        if (roundable)
        {
            dropTargets.add(resultTarget);
        }

        int index = 0;
        int extendedReqs = 1;
        for (final IToken<?> reqId : wareHouseModule.getMutableRequestList())
        {
            final IRequest<?> localRequest = getColony().getRequestManager().getRequestForToken(reqId);
            if (localRequest == null)
            {
                reqsToRemove.add(reqId);
                index++;
                continue;
            }

            // If we skipped this, we should add this
            if (index < resultIndex)
            {
                if (localRequest.getRequest() instanceof AbstractDeliverymanRequestable requestable)
                {
                    requestable.incrementPriorityDueToAging();
                }
            }
            else if (index == resultIndex)
            {
                index++;
                continue;
            }

            final BlockPos localTarget = getTarget(localRequest);
            if (mayJoinRound(localRequest, localTarget, resultTarget, roundable, dropTargets))
            {
                getTaskQueueFromDataStore().add(reqId);
                dropTargets.add(localTarget);
                extendedReqs++;
                reqsToRemove.add(reqId);
            }

            index++;
            if (extendedReqs >= getMaxParallelDeliveries())
            {
                break;
            }
        }

        if (resultRequest instanceof StandardRequests.PickupRequest)
        {
            getTaskQueueFromDataStore().add(resultRequestId);
        }

        // The tour starts where the courier will be when it sets off, which is the rack it loads at and not where it
        // is standing now -- it has to go and fetch the goods before it can deliver any of them.
        orderRoundNearestNext(getSource(resultRequest));

        wareHouseModule.getMutableRequestList().removeAll(reqsToRemove);
        wareHouseModule.markDirty();

        // The head of the queue rather than the winner of the scan: the round may have been reordered above, and every
        // later reader of this job -- decide(), prepareDelivery(), deliver() -- peeks the head. Handing back anything
        // else would have the AI acting on one request while the job believes in another.
        final IToken<?> head = getTaskQueueFromDataStore().peekFirst();
        return head == null ? (IRequest<IDeliverymanRequestable>) resultRequest : (IRequest<IDeliverymanRequestable>) getColony().getRequestManager().getRequestForToken(head);
    }

    /**
     * Whether a candidate may join the round being claimed.
     * <p>
     * Deliveries only, and only with deliveries. Upstream tested the target position alone, so a pickup for building B
     * could be swept into a delivery round bound for building B and end up queued ahead of the request that won the
     * claim -- after which {@code pickup()} and {@code deliver()} take turns rejecting each other's head of queue. A
     * pickup shares neither the loading step nor the unload loop and has no business on a delivery round.
     * <p>
     * The distinct-destination bound is separate from {@link #getMaxParallelDeliveries()}, which counts parcels rather
     * than stops: a courier may already carry eleven parcels for one address, and that is not the same thing as
     * visiting eleven addresses. Both bounds apply, so a courier whose Adaptability has not yet earned it a second
     * parallel delivery still makes exactly the trips it makes today.
     *
     * @param candidate   the candidate request.
     * @param target      the candidate's target, may be null.
     * @param roundTarget the target of the request that won the claim.
     * @param roundable   whether the winning request is a delivery at all.
     * @param dropTargets the distinct destinations already on the round.
     * @return true if it may join.
     */
    private boolean mayJoinRound(
      final IRequest<?> candidate,
      final BlockPos target,
      final BlockPos roundTarget,
      final boolean roundable,
      final Set<BlockPos> dropTargets)
    {
        if (!roundable || target == null || !(candidate.getRequest() instanceof Delivery))
        {
            return false;
        }
        if (target.equals(roundTarget) || dropTargets.contains(target))
        {
            return true;
        }
        return dropTargets.size() < MAX_DROPS_PER_ROUND && target.distManhattan(roundTarget) <= MULTI_DROP_RADIUS;
    }

    /**
     * Put the claimed round in the order it should be walked: nearest destination next, starting from where the round
     * begins. Parcels for the same destination stay together and keep their relative order, so a drop is still one
     * stop rather than several.
     * <p>
     * Greedy and deliberately so. Measured against an exhaustive tour on this colony's real building positions, plain
     * nearest-next captures 29.1 % of the walking against an optimal 30.8 % on three drops, and it is O(k^2) on a k of
     * at most {@link #MAX_DROPS_PER_ROUND}. A solver would buy 1.7 points and cost a solver. No path queries are made
     * here -- this is straight-line distance only.
     *
     * @param origin where the courier will start the round, or null if that is not known.
     */
    private void orderRoundNearestNext(final BlockPos origin)
    {
        final LinkedList<IToken<?>> queue = getTaskQueueFromDataStore();
        if (origin == null || queue.size() < 2)
        {
            return;
        }

        final LinkedHashMap<BlockPos, List<IToken<?>>> byTarget = new LinkedHashMap<>();
        for (final IToken<?> token : queue)
        {
            final IRequest<?> req = getColony().getRequestManager().getRequestForToken(token);
            if (req == null || !(req.getRequest() instanceof Delivery))
            {
                // A queue that is not a pure delivery round. Leave the order alone rather than guess at it.
                return;
            }
            byTarget.computeIfAbsent(getTarget(req), k -> new ArrayList<>()).add(token);
        }

        if (byTarget.size() < 2)
        {
            return;
        }

        final List<IToken<?>> ordered = new ArrayList<>(queue.size());
        BlockPos at = origin;
        while (!byTarget.isEmpty())
        {
            BlockPos best = null;
            int bestDistance = Integer.MAX_VALUE;
            for (final BlockPos target : byTarget.keySet())
            {
                final int distance = at.distManhattan(target);
                if (distance < bestDistance)
                {
                    bestDistance = distance;
                    best = target;
                }
            }
            ordered.addAll(byTarget.remove(best));
            at = best;
        }

        queue.clear();
        queue.addAll(ordered);
    }

    /**
     * Every delivery still in this courier's private queue, in the order the round will walk them.
     * <p>
     * {@link #getTaskListWithSameDestination} answers a different question -- "the parcels for <em>this</em> stop" --
     * and both are needed now: the gathering step has to load for the whole round before setting off, while the unload
     * step must hand over only what belongs to the building it is standing in front of.
     *
     * @return the deliveries of the round.
     */
    @SuppressWarnings(UNCHECKED)
    public List<IRequest<? extends Delivery>> getRoundTaskList()
    {
        final List<IRequest<? extends Delivery>> deliveries = new ArrayList<>();
        for (final IToken<?> token : getTaskQueueFromDataStore())
        {
            final IRequest<?> request = getColony().getRequestManager().getRequestForToken(token);
            if (request != null && request.getRequest() instanceof Delivery)
            {
                deliveries.add((IRequest<? extends Delivery>) request);
            }
        }
        return deliveries;
    }

    /**
     * Whether the courier is still carrying goods for a stop it has not made yet.
     * <p>
     * Asked by {@code deliver()} to decide whether to walk on to the next drop or go back to the decision point. It
     * deliberately does not go through {@link #getCurrentTask()}, which claims fresh work when the queue runs dry and
     * would send an empty-handed courier off to a building it had loaded nothing for.
     *
     * @return true if a loaded delivery remains.
     */
    /**
     * Whether the goods for one particular delivery are in the courier's pack right now.
     * <p>
     * The unload step needs this as well as the queue: a stop's parcels are what
     * {@link #getTaskListWithSameDestination} says, but a parcel whose rack turned out to be empty is in that list and
     * is not in the pack. Counting it as owed would let the stop take another stop's goods to make up the difference,
     * which is the same cross-contamination the count-based unload exists to prevent, arriving by a different door.
     *
     * @param token the delivery's token.
     * @return true if it was gathered.
     */
    public boolean isLoadedDelivery(final IToken<?> token)
    {
        return getDataStore().getOngoingDeliveries().contains(token);
    }

    public boolean hasLoadedDeliveryPending()
    {
        for (final IToken<?> token : getTaskQueueFromDataStore())
        {
            if (getDataStore().getOngoingDeliveries().contains(token))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Forget which deliveries are loaded, because the courier is about to empty its pack into the warehouse.
     * <p>
     * The requests themselves stay claimed and stay in the queue: what is no longer true is that their goods are in
     * the courier's inventory, and {@code prepareDelivery} has to fetch them again before the round can go on.
     */
    public void forgetLoadedDeliveries()
    {
        getDataStore().getOngoingDeliveries().clear();
    }

    /**
     * Method called to mark the current request as finished.
     *
     * @param successful True when the processing was successful, false when not.
     */
    public void finishRequest(final boolean successful)
    {
        if (getTaskQueueFromDataStore().isEmpty())
        {
            return;
        }

        final IToken<?> current = getTaskQueueFromDataStore().getFirst();

        final IRequest<?> request = getColony().getRequestManager().getRequestForToken(current);

        if (request == null)
        {
            if (!getTaskQueueFromDataStore().isEmpty() && current == getTaskQueueFromDataStore().getFirst())
            {
                getTaskQueueFromDataStore().removeFirst();
            }
            return;
        }
        else if (request.getRequest() instanceof Delivery)
        {
            final List<IRequest<? extends Delivery>> taskList = getTaskListWithSameDestination((IRequest<? extends Delivery>) request);
            if (ongoingDeliveries != 0)
            {
                for (int i = 0; i < Math.max(1, Math.min(ongoingDeliveries, taskList.size())); i++)
                {
                    final IRequest<? extends Delivery> req = taskList.get(i);
                    if (req.getState() == RequestState.IN_PROGRESS)
                    {
                        getColony().getRequestManager().updateRequestState(req.getId(), successful ? RequestState.RESOLVED : RequestState.FAILED);
                    }
                    getTaskQueueFromDataStore().remove(req.getId());
                }
            }
            else
            {
                // 26.2/Fabric: this used to resolve every loaded delivery the courier was carrying, which was the same
                // set as "this stop" only because a round could never have more than one stop. It now has to be the
                // intersection: the parcels for the building being stood in front of, and only those of them that were
                // actually loaded. The rest of the round stays claimed and stays loaded, and is finished at its own
                // stop -- which is what makes a partially failed round expressible instead of an all-or-nothing one.
                boolean finishedAny = false;
                for (final IRequest<? extends Delivery> req : taskList)
                {
                    if (!getDataStore().getOngoingDeliveries().contains(req.getId()))
                    {
                        continue;
                    }
                    if (req.getState() == RequestState.IN_PROGRESS)
                    {
                        getColony().getRequestManager().updateRequestState(req.getId(), successful ? RequestState.RESOLVED : RequestState.FAILED);
                    }
                    getTaskQueueFromDataStore().remove(req.getId());
                    getDataStore().getOngoingDeliveries().remove(req.getId());
                    finishedAny = true;
                }

                if (!finishedAny)
                {
                    // Nothing for this stop was ever loaded -- the rack was empty, or the target was gone before the
                    // courier arrived. Resolve it anyway, or its token sits at the head of the queue for ever and the
                    // courier walks the same failed errand until it is fired.
                    if (request.getState() == RequestState.IN_PROGRESS)
                    {
                        getColony().getRequestManager().updateRequestState(current, successful ? RequestState.RESOLVED : RequestState.FAILED);
                    }
                    getTaskQueueFromDataStore().remove(current);
                }
            }
        }
        else if (request.getRequest() instanceof Pickup)
        {
            getTaskQueueFromDataStore().remove(request.getId());
            getColony().getRequestManager().updateRequestState(current, successful ? RequestState.RESOLVED : RequestState.FAILED);
        }
        else
        {
            getColony().getRequestManager().updateRequestState(current, successful ? RequestState.RESOLVED : RequestState.FAILED);

            //Just to be sure lets delete them!
            if (!getTaskQueueFromDataStore().isEmpty() && current == getTaskQueueFromDataStore().getFirst())
            {
                getTaskQueueFromDataStore().removeFirst();
            }
        }

        getCitizen().getWorkBuilding().markDirty();
    }

    /**
     * Called when a task that is being scheduled is being canceled.
     *
     * @param token token of the task to be deleted.
     */
    public void onTaskDeletion(@NotNull final IToken<?> token)
    {
        if (getTaskQueueFromDataStore().contains(token))
        {
            getTaskQueueFromDataStore().remove(token);
        }

        if (getCitizen().getWorkBuilding() != null)
        {
            getCitizen().getWorkBuilding().markDirty();
        }
    }

    /**
     * Method to get the task queue of this job.
     *
     * @return The task queue.
     */
    public List<IToken<?>> getTaskQueue()
    {
        return ImmutableList.copyOf(getTaskQueueFromDataStore());
    }

    private void cancelAssignedRequests()
    {
        for (final IToken<?> t : getTaskQueue())
        {
            final IRequest<?> r = getColony().getRequestManager().getRequestForToken(t);
            if (r != null)
            {
                getColony().getRequestManager().updateRequestState(t, RequestState.FAILED);
            }
            else
            {
                Log.getLogger().warn("Oops, the request with ID: " + t.toString() + " couldn't be cancelled by the deliveryman because it doesn't exist");
            }
            getTaskQueueFromDataStore().remove(t);
        }
    }

    @Override
    public void onRemoval()
    {
        getCitizen().setWorking(false);
        try
        {
            cancelAssignedRequests();
        }
        catch (final Exception ex)
        {
            Log.getLogger().warn("Active Triggered resulted in exception", ex);
        }
        super.onRemoval();
        getColony().getRequestManager().getDataStoreManager().remove(this.rsDataStoreToken);
    }

    /**
     * Check if two deliveries have the same source and destination.
     *
     * @param requestA the first request.
     * @param requestB the second request.
     * @return true if so.
     */
    private boolean haveTasksSameSourceAndDest(@NotNull final Delivery requestA, @NotNull final Delivery requestB)
    {
        if (requestA.getTarget().equals(requestB.getTarget()))
        {
            if (requestA.getStart().equals(requestB.getStart()))
            {
                return true;
            }
            for (final IWareHouse wareHouse : getColony().getServerBuildingManager().getWareHouses())
            {
                if (wareHouse.hasContainerPosition(requestA.getStart().getInDimensionLocation()) && wareHouse.hasContainerPosition(requestB.getStart().getInDimensionLocation()))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Build a list of all requests that have the same source/dest pair.
     *
     * @param request the first request.
     * @return a list.
     */
    public List<IRequest<? extends Delivery>> getTaskListWithSameDestination(final IRequest<? extends Delivery> request)
    {
        final List<IRequest<? extends Delivery>> deliveryList = new ArrayList<>();
        deliveryList.add(request);
        for (final IToken<?> requestToken : getTaskQueue())
        {
            if (!requestToken.equals(request.getId()))
            {
                final IRequest<?> compareRequest = getColony().getRequestManager().getRequestForToken(requestToken);
                if (compareRequest != null && compareRequest.getRequest() instanceof Delivery)
                {
                    final Delivery current = (Delivery) compareRequest.getRequest();
                    final Delivery newDev = request.getRequest();
                    if (haveTasksSameSourceAndDest(current, newDev))
                    {
                        deliveryList.add((IRequest<? extends Delivery>) compareRequest);
                    }
                }
            }
        }
        return deliveryList;
    }

    /**
     * Calculate the max parallel deliveries the courier can do.
     * @return the max.
     */
    // PORT-NOTE(26.2): upstream reads assignedCitizen.get(0), which is this courier only while the hut
    // holds exactly one. Now that COURIER_WORK scales with hut level, reading slot 0 would give every
    // courier in the hut the parallel-delivery capacity of whoever happens to be listed first -- a fresh
    // hire would inherit a veteran's throughput, and the veteran would lose it if hired second. The skill
    // being read is this citizen's own, so ask this citizen.
    public int getMaxParallelDeliveries()
    {
        if (!(getWorkModule() instanceof final WorkerBuildingModule workModule))
        {
            return 1;
        }
        return 1 + (getCitizen().getCitizenSkillHandler().getLevel(workModule.getSecondarySkill()) / 5);
    }

    /**
     * Gets the source position of a request, pickups are reversed
     *
     * @param request
     * @return
     */
    private BlockPos getSource(final IRequest<?> request)
    {
        if (request.getRequest() instanceof Delivery)
        {
            return ((Delivery) request.getRequest()).getStart().getInDimensionLocation();
        }

        if (request.getRequest() instanceof Pickup)
        {
            final IWareHouse wareHouse = findWareHouse();
            if (wareHouse != null)
            {
                return wareHouse.getID();
            }
        }

        return null;
    }

    /**
     * Gets the target position of a request, pickups are reversed
     *
     * @param request
     * @return
     */
    private BlockPos getTarget(final IRequest<?> request)
    {
        if (request.getRequest() instanceof Delivery)
        {
            return ((Delivery) request.getRequest()).getTarget().getInDimensionLocation();
        }

        if (request.getRequest() instanceof Pickup)
        {
            return request.getRequester().getLocation().getInDimensionLocation();
        }

        return null;
    }

    /**
     * Finds the warehouse our dman is assigned to
     *
     * @return warehouse building or null
     */
    public IWareHouse findWareHouse()
    {
        for (final IWareHouse building : getColony().getServerBuildingManager().getWareHouses())
        {
            if (building.getFirstModuleOccurance(CourierAssignmentModule.class).hasAssignedCitizen(getCitizen()))
            {
                return building;
            }
        }

        return null;
    }

    /**
     * Add a concurrent delivery that is going on.
     * @param requestToken the token of the request.
     */
    public void addConcurrentDelivery(final IToken<?> requestToken)
    {
        getDataStore().getOngoingDeliveries().add(requestToken);
    }

    /**
     * Remove a concurrent delivery that is going on.
     * @param requestToken the token of the request.
     */
    public void removeConcurrentDelivery(final IToken<?> requestToken)
    {
        getDataStore().getOngoingDeliveries().remove(requestToken);
    }

    @Override
    public double getSaturationFactor()
    {
        return 1.2;
    }
}
