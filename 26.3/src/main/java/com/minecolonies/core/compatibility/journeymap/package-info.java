/**
 * # About JourneyMap integration
 *
 * Two integrations are currently supported:
 *
 * 1. Colony chunks/borders are highlighted
 * 2. Deathpoints are added when a citizen dies
 *
 * Since JourneyMap is an optional dependency, it is absolutely forbidden
 * for any class outside of this package to call into this package.
 */
package com.minecolonies.core.compatibility.journeymap;

// PORT-TODO(optional-integration): DISABLED for the 26.2 port.
// This file is excluded from compilation via 26.2/optional-integrations.txt. Nothing
// outside this package depends on it -- Compatibility.jeiProxy already defaults to a
// no-op IJeiProxy, and the JourneyMap code was never referenced from the mod proper.
// To bring the integration back, delete the matching line from that list.

