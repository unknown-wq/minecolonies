package com.minecolonies.core.entity.pathfinding;

import net.minecraft.network.RegistryFriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.minecolonies.api.util.constant.PathingConstants.SHIFT_X_BY;
import static com.minecolonies.api.util.constant.PathingConstants.SHIFT_Y_BY;

/**
 * Nodes used in pathfinding.
 */
public class MNode implements Comparable<MNode>
{
    /**
     * Which slot of the {@link OpenNodeHeap} this node currently occupies, -1 while it is not queued. Owned and
     * maintained exclusively by the heap; nothing else may write it. Stored on the node so taking an open node out
     * of the queue to re-cost it is a heap removal instead of a linear scan.
     */
    int heapIndex = -1;

    /**
     * Values used in the generation of the hash of the node.
     */
    private static final int HASH_A = 12;
    private static final int HASH_B = 20;
    private static final int HASH_C = 24;

    /**
     * The position of the node.
     */
    @NotNull
    public final int x;
    public final int y;
    public final int z;

    /**
     * The parent of the node (Node preceding this node).
     */
    @Nullable
    public MNode parent;

    /**
     * Added counter.
     */
    private int counterAdded;

    /**
     * The cost of the node. A* g value.
     */
    private double cost;

    /**
     * The heuristic of the node. A* h value.
     */
    private double heuristic;

    /**
     * Checks if the node has been closed already.
     */
    private int visitedCount = 0;

    /**
     * Checks if the node is on a ladder.
     */
    private boolean ladder = false;

    /**
     * Checks if the node is in water.
     */
    private boolean swimming = false;

    /**
     * If is on rails.
     */
    private boolean isOnRails = false;

    /**
     * If this is a water surface node a boat could float on.
     */
    private boolean isOnBoat = false;

    /**
     * How many boat-eligible nodes in a row end at this one, counting itself; 0 when this node is not eligible.
     * <p>
     * Written during the search from the parent chain, so it is the length of the run <em>so far</em> -- the search
     * cannot see how much water lies beyond a node it has not expanded yet. That is enough to price a node honestly:
     * see {@code AbstractPathJob#computeCost}.
     */
    private int boatRunLength = 0;

    /**
     * How many blocks of ground this node's link to its parent actually covers; 1 for an ordinary step.
     * <p>
     * A water crossing is expanded in macro edges -- one node at the far end of a straight run of boatable surface
     * instead of one node per block -- and this is how long that run is. Everything downstream that used to be able
     * to assume "one node, one block" reads it: the cost of the edge, the boat run measured in blocks rather than in
     * nodes, and {@code AbstractPathJob#finalizePath}, which materialises the skipped blocks back into path points so
     * the navigator still sees the neighbouring points it has always seen.
     * <p>
     * Together with {@link #boatRunLength} this describes the <em>current</em> parent link and nothing else. Any code
     * that writes {@link #parent} has to rewrite both, or the node will price and draw a link it no longer has; see
     * {@code AbstractPathJob#updateNode}.
     */
    private int edgeLength = 1;

    /**
     * The blocks a macro edge actually runs through, packed with {@link net.minecraft.core.BlockPos#asLong()}, or null
     * when the link's shape can be worked out from its two endpoints.
     * <p>
     * Water edges need nothing here: a probe walks a straight line at one height, so
     * {@code AbstractPathJob#edgeBlockPos} can recompute every block it crossed from the parent, the node and the
     * length alone. A rail edge cannot be recomputed that way and never could be -- the track curves, climbs and
     * doubles back as the player laid it, and the only description of where it went is where the walk went. Materialising
     * such an edge from its endpoints would draw a straight line between them and steer the cart at blocks that are not
     * on the track.
     * <p>
     * Ordered from the parent outwards, so element 0 is the first block past the parent and the last element is this
     * node's own position; the array is always exactly {@link #edgeLength} long. It describes the current parent link
     * like the two numbers above it and, like them, has to be rewritten whenever {@link #parent} is.
     */
    @Nullable
    private long[] edgeCells = null;

    /**
     * Whether this node ended up in a run of water long enough to be worth a boat.
     * <p>
     * Only meaningful once {@code AbstractPathJob#finalizePath} has decided it, which it does by measuring whole runs
     * along the finished parent chain. That is deliberately not read off {@link #boatRunLength}: {@code updateNode}
     * re-parents a node when it finds a cheaper way to it without refreshing what the node carries, so a run length
     * banked during the search can belong to a chain that is no longer the one being walked.
     */
    private boolean boatLeg = false;

    /**
     * Whether this is an air node
     */
    private boolean isCornerNode = false;

    /**
     * Wether this node got reached by an entity, for debug drawing
     */
    private boolean isReachedByWorker = false;

    /**
     * Create a Node that inherits from a parent, and has a Cost and Heuristic estimate.
     *
     * @param parent    parent node arrives from.
     * @param cost      node cost.
     * @param heuristic heuristic estimate.
     */
    public MNode(@Nullable final MNode parent, @NotNull final int posX, final int posY, final int posZ, final double cost, final double heuristic)
    {
        this.parent = parent;
        this.x = posX;
        this.y = posY;
        this.z = posZ;
        this.cost = cost;
        this.heuristic = heuristic;
    }

    /**
     * Create an MNode from a bytebuf.
     * @param byteBuf the buffer to load it from.
     */
    public MNode(final RegistryFriendlyByteBuf byteBuf)
    {
        if (byteBuf.readBoolean())
        {
            this.parent = new MNode(null, byteBuf.readVarInt(), byteBuf.readVarInt(), byteBuf.readVarInt(), 0, 0);
        }
        this.x = byteBuf.readVarInt();
        this.y = byteBuf.readVarInt();
        this.z = byteBuf.readVarInt();
        this.cost = byteBuf.readDouble();
        this.heuristic = byteBuf.readDouble();
        this.isReachedByWorker = byteBuf.readBoolean();
    }

    /**
     * Serialize the Node to buf.
     * @param byteBuf
     */
    public void serializeToBuf(final RegistryFriendlyByteBuf byteBuf)
    {
        byteBuf.writeBoolean(this.parent != null);
        if (this.parent != null)
        {
            // For debug display only position is used
            byteBuf.writeVarInt(this.parent.x);
            byteBuf.writeVarInt(this.parent.y);
            byteBuf.writeVarInt(this.parent.z);
        }
        byteBuf.writeVarInt(this.x);
        byteBuf.writeVarInt(this.y);
        byteBuf.writeVarInt(this.z);
        byteBuf.writeDouble(this.cost);
        byteBuf.writeDouble(this.heuristic);
        byteBuf.writeBoolean(this.isReachedByWorker);
    }

    @Override
    public int compareTo(@NotNull final MNode o)
    {
        //  Comparing doubles and returning value as int; can't simply cast the result
        if ((cost + heuristic) < (o.cost + o.heuristic))
        {
            return -1;
        }

        if ((cost + heuristic) > (o.cost + o.heuristic))
        {
            return 1;
        }

        if (heuristic < o.heuristic)
        {
            return -1;
        }

        if (heuristic > o.heuristic)
        {
            return 1;
        }

        //  In case of score tie, older node has better score
        return counterAdded - o.counterAdded;
    }

    @Override
    public int hashCode()
    {
        return x ^ ((z << HASH_A) | (z >> HASH_B)) ^ (y << HASH_C);
    }

    @Override
    public boolean equals(@Nullable final Object o)
    {
        if (o != null && o.getClass() == this.getClass())
        {
            @Nullable final MNode other = (MNode) o;
            return x == other.x
                     && y == other.y
                     && z == other.z;
        }

        return false;
    }

    /**
     * Checks if node is closed.
     *
     * @return true if so.
     */
    public boolean isVisited()
    {
        return visitedCount != 0;
    }

    /**
     * Get the visited count
     *
     * @return
     */
    public int getVisitedCount()
    {
        return visitedCount;
    }

    /**
     * Checks if node is on a ladder.
     *
     * @return true if so.
     */
    public boolean isLadder()
    {
        return ladder;
    }

    /**
     * Checks if node is in water.
     *
     * @return true if so.
     */
    public boolean isSwimming()
    {
        return swimming;
    }

    /**
     * Sets the node as closed.
     */
    public void increaseVisited()
    {
        visitedCount++;
    }

    /**
     * Getter of the score of the node.
     *
     * @return the score.
     */
    public double getScore()
    {
        return cost + heuristic;
    }

    /**
     * Getter of the cost of the node.
     *
     * @return the cost.
     */
    public double getCost()
    {
        return cost;
    }

    /**
     * Sets the node cost.
     *
     * @param cost the cost.
     */
    public void setCost(final double cost)
    {
        this.cost = cost;
    }

    /**
     * Sets the node as a ladder node.
     */
    public void setLadder()
    {
        ladder = true;
    }

    /**
     * Sets the node as a swimming node.
     */
    public void setSwimming()
    {
        swimming = true;
    }

    /**
     * Getter of the heuristic.
     *
     * @return the heuristic.
     */
    public double getHeuristic()
    {
        return heuristic;
    }

    /**
     * Sets the node heuristic.
     *
     * @param heuristic the heuristic.
     */
    public void setHeuristic(final double heuristic)
    {
        this.heuristic = heuristic;
    }

    // TODO: Debug index and counter added actually needed at all??

    /**
     * Getter of the added counter.
     *
     * @return the amount.
     */
    public int getDebugAddedIndex()
    {
        return counterAdded;
    }

    /**
     * Sets the added counter.
     *
     * @param counterAdded amount to set.
     */
    public void setCounterAdded(final int counterAdded)
    {
        this.counterAdded = counterAdded;
    }

    /**
     * Setup rails params.
     *
     * @param isOnRails if on rails.
     */
    public void setOnRails(final boolean isOnRails)
    {
        this.isOnRails = isOnRails;
    }

    /**
     * Check if is on rails.
     *
     * @return true if so.
     */
    public boolean isOnRails()
    {
        return isOnRails;
    }

    /**
     * Setup boat params.
     *
     * @param isOnBoat if crossed by boat.
     */
    public void setOnBoat(final boolean isOnBoat)
    {
        this.isOnBoat = isOnBoat;
    }

    /**
     * Check if this node is crossed by boat.
     *
     * @return true if so.
     */
    public boolean isOnBoat()
    {
        return isOnBoat;
    }

    /**
     * Set how many boat-eligible nodes in a row end at this one.
     *
     * @param boatRunLength the run length, counting this node.
     */
    public void setBoatRunLength(final int boatRunLength)
    {
        this.boatRunLength = boatRunLength;
    }

    /**
     * How many boat-eligible nodes in a row end at this one.
     *
     * @return the run length, 0 if this node is not eligible.
     */
    public int getBoatRunLength()
    {
        return boatRunLength;
    }

    /**
     * Set how many blocks the link from this node's parent to this node covers.
     *
     * @param edgeLength the length in blocks, 1 for an ordinary step.
     */
    public void setEdgeLength(final int edgeLength)
    {
        this.edgeLength = edgeLength;
    }

    /**
     * How many blocks the link from this node's parent to this node covers.
     *
     * @return the length in blocks, 1 for an ordinary step.
     */
    public int getEdgeLength()
    {
        return edgeLength;
    }

    /**
     * Set the blocks this node's macro edge runs through, or null if they can be derived from the endpoints.
     *
     * @param edgeCells packed positions from the parent outwards, exactly {@link #getEdgeLength()} of them.
     */
    public void setEdgeCells(@Nullable final long[] edgeCells)
    {
        this.edgeCells = edgeCells;
    }

    /**
     * The blocks this node's macro edge runs through, from the parent outwards.
     *
     * @return the packed positions, or null when the link is a straight line that can be derived from its endpoints.
     */
    @Nullable
    public long[] getEdgeCells()
    {
        return edgeCells;
    }

    /**
     * Mark this node as part of a water run long enough to be worth a boat.
     *
     * @param boatLeg whether it is.
     */
    public void setBoatLeg(final boolean boatLeg)
    {
        this.boatLeg = boatLeg;
    }

    /**
     * Whether this node is part of a water run long enough to be worth a boat.
     *
     * @return true if so.
     */
    public boolean isBoatLeg()
    {
        return boatLeg;
    }

    /**
     * Marks the node as reached by the worker
     * @param reached if reached or reset.
     */
    public void setReachedByWorker(final boolean reached)
    {
        isReachedByWorker = reached;
    }

    /**
     * Whether the node is reached by a worker
     *
     * @return reached
     */
    public boolean isReachedByWorker()
    {
        return isReachedByWorker;
    }

    /**
     * Marks the node as reached by the worker
     */
    public void setCornerNode(boolean isCornerNode)
    {
        this.isCornerNode = isCornerNode;
    }

    /**
     * Whether the node is reached by a worker
     *
     * @return reached
     */
    public boolean isCornerNode()
    {
        return isCornerNode;
    }

    /**
     * Get the added index
     *
     * @return
     */
    public int getCounterAdded()
    {
        return counterAdded;
    }

    /**
     * Generate a pseudo-unique key for identifying a given node by it's coordinates Encodes the lowest 12 bits of x,z and all useful bits of y. This creates unique keys for all
     * blocks within a 4096x256x4096 cube, which is FAR bigger volume than one should attempt to pathfind within
     *
     * @return key for node in map
     */
    public static int computeNodeKey(final int x, final int y, final int z)
    {
        return ((x & 0xFFF) << SHIFT_X_BY)
                 | ((y & 0xFF) << SHIFT_Y_BY)
                 | (z & 0xFFF);
    }

    @Override
    public String toString()
    {
        return "Node: [" + x + "," + y + "," + z + "] visited:" + visitedCount + " cost:" + cost + " heuristic:" + heuristic;
    }
}
