/*
 * # About JEI Plugin
 * The JEI plugin currently provides two separate features of significance:
 *
 * Firstly, as part of the recipe-teaching system, it facilitates looking up a recipe in JEI and then clicking the
 * recipe as a whole into the crafting grid (even when the player does not have the corresponding items, as this is
 * just ghost-crafting) so that it can be taught to a building.
 *
 * Secondly, it provides a way to look up which colony buildings will accept various recipes, either to be taught
 * or as natively supported products.
 */
package com.minecolonies.core.compatibility.jei;

// PORT-TODO(optional-integration): DISABLED for the 26.2 port.
// This file is excluded from compilation via 26.2/optional-integrations.txt. Nothing
// outside this package depends on it -- Compatibility.jeiProxy already defaults to a
// no-op IJeiProxy, and the JourneyMap code was never referenced from the mod proper.
// To bring the integration back, delete the matching line from that list.
