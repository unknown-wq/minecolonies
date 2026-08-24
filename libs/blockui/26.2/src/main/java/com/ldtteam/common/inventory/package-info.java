/**
 * Slot-based inventory abstraction shared by everything that builds on BlockUI - the replacement for
 * NeoForge's {@code net.neoforged.neoforge.items.*}, which has no Fabric counterpart.
 *
 * <h2>Why it lives here</h2>
 * <p>
 * Both Structurize and MineColonies ported the same interface independently and ended up with the same six
 * methods, and MineColonies alone routes 81 files through it. Somebody had to own the type, and BlockUI is the
 * only library all three mods already depend on: putting it in Structurize would make every future consumer pull
 * in a building library to get an inventory interface, and would make 81 MineColonies files depend on Structurize
 * where they do not today. {@code com.ldtteam.common} is already the shared layer that lives inside BlockUI -
 * {@code network}, {@code config}, {@code codec}, {@code language}, {@code util}, {@code fakelevel} - and an
 * inventory view is the same kind of thing.
 *
 * <h2>What this is not</h2>
 * <ul>
 * <li><b>Not a capability system.</b> Nothing here is published to other mods and nothing here can be looked up
 *     from one. NeoForge could ask any block entity, entity or stack for an {@code IItemHandler}, including sided
 *     views contributed by third parties; that mechanism does not exist on Fabric.
 *     {@link com.ldtteam.common.inventory.ItemHandlers} resolves only what vanilla itself exposes, so an
 *     inventory that some other mod publishes purely as its own capability is invisible here.</li>
 * <li><b>Not fabric-transfer-api-v1.</b> That API models storage as {@code Storage<ItemVariant>} with
 *     transactions and participants - a different shape from the slot-and-simulate-flag contract the ported call
 *     sites are written against. Bridging the two is a separate piece of work; deliberately none of it is
 *     started here, and this package must not grow a dependency on it by accident.</li>
 * <li><b>Not a container/menu framework.</b> {@link com.ldtteam.common.inventory.SlotItemHandler} exists only so
 *     that a vanilla {@link net.minecraft.world.inventory.Slot} can sit on top of a handler.</li>
 * </ul>
 *
 * <h2>The one trap</h2>
 * <p>
 * Never wrap a player {@link net.minecraft.world.entity.player.Inventory} with a plain
 * {@link com.ldtteam.common.inventory.InvWrapper}: its {@code getContainerSize()} counts the equipment slots as
 * well and it does not override {@code canPlaceItem}, so a whole-container view accepts a milk bottle into the
 * helmet slot without complaining. Use {@link com.ldtteam.common.inventory.PlayerMainInvWrapper}, or an
 * {@code InvWrapper} built with an explicit slot range. This is enforced by construction in
 * {@link com.ldtteam.common.inventory.ItemHandlers#of(Object)}, which never hands back a whole-inventory view
 * for a player.
 */
package com.ldtteam.common.inventory;
