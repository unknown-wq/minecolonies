package com.minecolonies.core.client.assetfetch;

import java.util.List;

/**
 * Finds download sources that were not known when the mod was built.
 *
 * <p>Every source the installer ships is a constant: a URL written into {@link SourceChain} and measured
 * against the manifest before release. That is what makes them trustworthy, and it is also what makes them
 * age — a pinned tag is a bet that upstream will still be serving that exact tag years from now. This
 * interface is the other half: a way to ask, at install time, what upstream is actually serving today.</p>
 *
 * <p>It is strictly an addition. The pinned entries are tried first and in full; discovery is consulted only
 * once they have all failed, so an installer that cannot reach the network, or is refused, or gets an answer
 * it does not understand, still behaves exactly as it did before this existed. For that reason an
 * implementation <b>never throws</b>: a failure is an empty list.</p>
 */
@FunctionalInterface
public interface SourceDiscovery
{
    /**
     * Asks upstream what it is serving.
     *
     * <p>Called at most once per install run, and only after every shipped source has failed. An
     * implementation gets exactly that one chance: no retry loop, here or in the caller.</p>
     *
     * @return further sources to try, in the order they should be tried, or an empty list when nothing could
     *         be found. Never null.
     */
    List<AssetSource> discover();
}
