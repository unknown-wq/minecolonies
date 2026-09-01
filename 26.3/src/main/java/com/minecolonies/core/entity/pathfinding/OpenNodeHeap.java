package com.minecolonies.core.entity.pathfinding;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * The pathfinder's open list: a binary min-heap of {@link MNode} that tracks each contained node's heap slot on the
 * node itself.
 * <p>
 * This exists for exactly one call: {@code updateNode} re-parents an open node onto a cheaper route by taking it out
 * of the queue, rewriting its cost, and putting it back. On the {@link java.util.PriorityQueue} this used to live in,
 * the take-out is a linear scan of the whole open list -- {@code PriorityQueue.remove} has no idea where its element
 * sits -- and a search that keeps finding cheaper routes through terrain it has already opened pays that scan once per
 * improvement, which turns the open list into a quadratic tail on exactly the maps where the search is already
 * working hardest. With the slot stored on the node ({@link MNode#heapIndex}, package-private and owned by this
 * class), the removal is the ordinary heap removal: swap to the hole, sift, O(log n).
 * <p>
 * Everything else deliberately keeps {@code PriorityQueue}'s observable behaviour: {@link #poll()} returns the least
 * element by the comparator (natural {@link MNode#compareTo} order unless one is given), {@link #remove} of an absent
 * node is a no-op, {@link #poll()} on empty returns null, and iteration walks the backing array in unspecified heap
 * order -- the one iterator user samples nodes and never depended on an order. No lazy deletion and no duplicate
 * entries: a node is in the heap at most once, and a mutation while enqueued goes through take-out/put-back exactly
 * as before, so the search visits the same nodes in the same order it always did.
 */
public class OpenNodeHeap implements Iterable<MNode>
{
    /**
     * The backing array, a classic implicit binary heap: children of i at 2i+1 and 2i+2.
     */
    private MNode[] heap;

    /**
     * How many of the slots are in use.
     */
    private int size = 0;

    /**
     * The order, or null for {@link MNode}'s natural order. Kept nullable rather than defaulting to
     * {@code MNode::compareTo} so the common path pays a null check instead of a lambda dispatch.
     */
    @Nullable
    private final Comparator<MNode> comparator;

    /**
     * A heap ordered by {@link MNode}'s natural order (f-value, then heuristic, then age).
     *
     * @param initialCapacity how many slots to start with; grows as needed.
     */
    public OpenNodeHeap(final int initialCapacity)
    {
        this(initialCapacity, null);
    }

    /**
     * A heap with its own order, for the post-arrival phase that re-sorts the open list by heuristic alone.
     *
     * @param initialCapacity how many slots to start with; grows as needed.
     * @param comparator      the order, or null for natural order.
     */
    public OpenNodeHeap(final int initialCapacity, @Nullable final Comparator<MNode> comparator)
    {
        heap = new MNode[Math.max(4, initialCapacity)];
        this.comparator = comparator;
    }

    /**
     * @return how many nodes are queued.
     */
    public int size()
    {
        return size;
    }

    /**
     * @return true if nothing is queued.
     */
    public boolean isEmpty()
    {
        return size == 0;
    }

    /**
     * Add a node, or restore heap order for one that is already here and had its keys rewritten in place. The second
     * case does not arise from the pathfinder as written -- updateNode removes before it re-offers -- but treating it
     * as a re-sift keeps this container safe against the call order drifting, where treating it as a second insert
     * would corrupt the slot bookkeeping silently.
     *
     * @param node the node to queue.
     */
    public void offer(@NotNull final MNode node)
    {
        final int index = node.heapIndex;
        if (index >= 0 && index < size && heap[index] == node)
        {
            siftDown(siftUp(index));
            return;
        }

        if (size == heap.length)
        {
            heap = Arrays.copyOf(heap, heap.length * 2);
        }

        heap[size] = node;
        node.heapIndex = size;
        size++;
        siftUp(size - 1);
    }

    /**
     * Take the least node off the heap.
     *
     * @return the least node by the heap's order, or null when empty.
     */
    @Nullable
    public MNode poll()
    {
        if (size == 0)
        {
            return null;
        }

        final MNode top = heap[0];
        removeAt(0);
        top.heapIndex = -1;
        return top;
    }

    /**
     * Take one specific node out, wherever it sits. A node that is not here is a no-op, matching what
     * {@code PriorityQueue.remove} returning false used to mean to the caller.
     *
     * @param node the node to remove.
     */
    public void remove(@NotNull final MNode node)
    {
        final int index = node.heapIndex;
        if (index < 0 || index >= size || heap[index] != node)
        {
            return;
        }

        removeAt(index);
        node.heapIndex = -1;
    }

    /**
     * Empty the heap. Every contained node's slot marker is reset, so the same nodes can be re-offered afterwards --
     * which is precisely what the heuristic rebalance does.
     */
    public void clear()
    {
        for (int i = 0; i < size; i++)
        {
            heap[i].heapIndex = -1;
            heap[i] = null;
        }
        size = 0;
    }

    /**
     * The queued nodes as a list, in unspecified order. A copy: safe to hold across {@link #clear()}.
     *
     * @return the queued nodes.
     */
    @NotNull
    public List<MNode> toList()
    {
        final List<MNode> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++)
        {
            list.add(heap[i]);
        }
        return list;
    }

    /**
     * Move everything queued on another heap onto this one. The nodes' slot markers are simply overwritten -- the
     * source heap must be one that is being discarded, which is the only way this is used.
     *
     * @param source the heap being replaced.
     */
    public void addAll(@NotNull final OpenNodeHeap source)
    {
        for (int i = 0; i < source.size; i++)
        {
            final MNode node = source.heap[i];
            node.heapIndex = -1;
            offer(node);
        }
    }

    /**
     * Close the hole a removal left at the given slot and restore heap order.
     *
     * @param index the emptied slot.
     */
    private void removeAt(final int index)
    {
        size--;
        if (index == size)
        {
            heap[size] = null;
            return;
        }

        final MNode moved = heap[size];
        heap[size] = null;
        heap[index] = moved;
        moved.heapIndex = index;
        siftDown(siftUp(index));
    }

    /**
     * Bubble a slot towards the root while it beats its parent.
     *
     * @param index the slot to sift.
     * @return where the element ended up.
     */
    private int siftUp(int index)
    {
        final MNode node = heap[index];
        while (index > 0)
        {
            final int parentIndex = (index - 1) >> 1;
            final MNode parent = heap[parentIndex];
            if (compare(node, parent) >= 0)
            {
                break;
            }
            heap[index] = parent;
            parent.heapIndex = index;
            index = parentIndex;
        }
        heap[index] = node;
        node.heapIndex = index;
        return index;
    }

    /**
     * Bubble a slot towards the leaves while a child beats it.
     *
     * @param index the slot to sift.
     */
    private void siftDown(int index)
    {
        final MNode node = heap[index];
        final int half = size >> 1;
        while (index < half)
        {
            int childIndex = (index << 1) + 1;
            MNode child = heap[childIndex];
            final int rightIndex = childIndex + 1;
            if (rightIndex < size && compare(heap[rightIndex], child) < 0)
            {
                childIndex = rightIndex;
                child = heap[rightIndex];
            }
            if (compare(child, node) >= 0)
            {
                break;
            }
            heap[index] = child;
            child.heapIndex = index;
            index = childIndex;
        }
        heap[index] = node;
        node.heapIndex = index;
    }

    private int compare(final MNode a, final MNode b)
    {
        return comparator == null ? a.compareTo(b) : comparator.compare(a, b);
    }

    @NotNull
    @Override
    public Iterator<MNode> iterator()
    {
        return new Iterator<>()
        {
            private int cursor = 0;

            @Override
            public boolean hasNext()
            {
                return cursor < size;
            }

            @Override
            public MNode next()
            {
                if (cursor >= size)
                {
                    throw new NoSuchElementException();
                }
                return heap[cursor++];
            }
        };
    }
}
