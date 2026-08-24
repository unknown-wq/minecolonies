package com.minecolonies.api.colony.workorders;

/**
 * What came of asking a building for a work order.
 * <p>
 * {@code AbstractBuilding#requestWorkOrder} used to be {@code void} and said what it had done only by sending a chat
 * message to the whole colony. That is right for the hut GUI, where one click means one order, and wrong for anything
 * that asks a whole colony at once: sixty refusals are sixty chat lines, and the caller still cannot count them. The
 * refusals are unchanged and still announced by default - this only makes them answerable.
 */
public enum WorkOrderRequestResult
{
    /** The order was created. */
    QUEUED,
    /** This building already has a work order; a second one is never created for the same hut. */
    ALREADY_QUEUED,
    /** A deconstruction was asked for and the building refuses to be deconstructed. */
    CANNOT_DECONSTRUCT,
    /** No builder in the colony is far enough along to take this, and no crafter could resolve it either. */
    NO_BUILDER_GOOD_ENOUGH,
    /** No builder with a worker is within {@code maxbuilderdistance} of the site. */
    NO_BUILDER_IN_RANGE,
    /** The building's footprint reaches above the world. */
    TOO_HIGH,
    /** The hut block sits at or below the bottom of the world. */
    TOO_LOW,
    /** A builder was named explicitly and cannot take this order. */
    ASSIGNED_BUILDER_REFUSED,
    /**
     * The building has no blueprint path recorded, so no work order can name a schematic for it. Asking anyway used
     * to throw out of {@code WorkOrderBuilding#create}.
     */
    NO_BLUEPRINT,
    /** The building is not built, so there is nothing to repair or take down. */
    NOT_BUILT,
    /**
     * The building was taken down and is waiting to be picked up. Only the callers that ask on a player's behalf for
     * a whole colony use this; {@code requestRepair} itself would happily rebuild such a building.
     */
    DECONSTRUCTED,
    /**
     * The request threw. Never returned by the building itself - it is what a caller that asks a whole colony at once
     * records for a building that blew up, so that one bad hut does not take the other fifty-nine with it.
     */
    FAILED
}
