package com.minecolonies.core.colony.rescue;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.Colony;
import com.minecolonies.core.colony.managers.RegisteredStructureManager;
import org.jetbrains.annotations.Nullable;

/**
 * Keep buildings: one colony-wide switch that stops the sanity cleanup deleting buildings from the colony.
 * <p>
 * The whole feature is reachable from this one symbol — {@code grep -rn KeepBuildings src/main/java} is a
 * complete inventory of it, the same way it is for {@code FreeMode} and {@code ColonyProtection}. The switch
 * itself is a boolean on {@link Colony}, <b>off by default</b>, set with
 * {@code /mc colony keepbuildings <colony> on}.
 * <p>
 * <b>What it suspends.</b> {@link RegisteredStructureManager#cleanUpBuildings(IColony)} runs on the colony tick
 * and, for every building whose chunk is loaded and whose anchor is no longer
 * {@code building.isMatchingBlock(...)}, calls {@code IBuilding#destroy} — the building leaves the colony for
 * good, taking its level, its work orders and its worker's assignment with it. That is right when a player
 * mines a hut and wrong when the block is missing for a reason that has nothing to do with the player: a world
 * opened once without the mod installed loses every one of the mod's block entities, and the colony then
 * deletes itself building by building as the player walks around and more chunks load. The code already
 * suspects this case — it warns <em>"Did you just load a backup?"</em> when every building goes at once — but a
 * warning does not put anything back. This switch is the missing "stop".
 * <p>
 * While it is on the cleanup destroys nothing, and each building it would have destroyed is named in the server
 * log so the player has the damage list rather than a silent count.
 * <p>
 * <b>It suspends the whole of that method's removal, not only the buildings.</b> Building extensions (fields,
 * plantation fields) and leisure sites are dropped by the same method for the same reason — their block is not
 * where it was — and are lost just as irrecoverably. A switch that saved the buildings and let the fields go
 * would be a switch that half worked, so both are held back too. Everything the method does that is not a
 * removal still happens.
 * <p>
 * <b>It is a pause, not a repair.</b> The buildings stay in the colony but the world still has no hut blocks,
 * so nothing else about them works: no hut GUI, no worker attachment, no repair order. The switch buys the time
 * to run {@code /mc colony restorehuts}, which puts the anchors back; once that has been done the buildings are
 * consistent again and the switch can go off, at which point the cleanup resumes its ordinary job of noticing
 * huts the player really did mine.
 */
public final class KeepBuildings
{
    /**
     * NBT tag the switch is saved under, declared here rather than in {@code NbtTagConstants} so that deleting
     * this file leaves nothing of the feature behind anywhere else.
     */
    public static final String TAG_KEEP_BUILDINGS = "colonyKeepBuildings";

    /**
     * Private constructor to hide the public one.
     */
    private KeepBuildings()
    {
        //Hides implicit constructor.
    }

    /**
     * Whether this colony has stopped letting the sanity cleanup delete anything.
     * <p>
     * Server side only: the cleanup only ever runs on the server, and a client side view never reaches the one
     * call site. A view therefore answers "the cleanup is running normally", which is the answer that changes
     * no client behaviour.
     *
     * @param colony the colony, may be null or a client side view.
     * @return true if the cleanup must not remove anything from this colony.
     */
    public static boolean isOn(@Nullable final IColony colony)
    {
        return colony instanceof final Colony serverColony && serverColony.isKeepBuildings();
    }
}
