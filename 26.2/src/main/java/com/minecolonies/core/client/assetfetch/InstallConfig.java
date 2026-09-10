package com.minecolonies.core.client.assetfetch;

import java.nio.file.Path;
import java.util.List;

/**
 * Everything {@link InstallPipeline} needs, and nothing that would tie it to a running game.
 *
 * <p>The pack format arrives as a plain {@code int} rather than being read from {@code SharedConstants},
 * and the cache root arrives as a {@link Path} rather than being derived from the game directory, precisely
 * so that the download, extract, patch and assemble pipeline can be run headlessly against a scratch directory.
 * The glue that fills these in from the running client is {@link AssetInstaller}.</p>
 *
 * @param baseDir         the cache root: {@code <gameDir>/minecolonies/fetched-assets}.
 * @param bundle          where {@code transforms.json}, {@code patches/**} and {@code manifest.json} come from.
 * <p>The chain arrives in three parts because it is tried in three parts: {@code sources} first, then
 * whatever {@code discovery} turns up, then {@code sourcesAfterDiscovery}. Splitting it this way is what
 * keeps discovery from running at all until everything known-good has failed, and a headless caller that
 * passes null for it gets the shipped behaviour minus any network beyond the downloads themselves.</p>
 *
 * @param packFormatMajor the running game's client resource pack format, major component (task C8).
 * @param sources         the chain to try first, in order.
 * @param discovery       consulted once, after {@code sources} is exhausted; may be null for none.
 * @param sourcesAfterDiscovery the chain to try last, in order, whether or not discovery found anything.
 * @param onStateChanged  run after {@code state.json} changes, so caches of it can be dropped; may be null.
 */
public record InstallConfig(Path baseDir, BundleResources bundle, int packFormatMajor, List<AssetSource> sources,
    SourceDiscovery discovery, List<AssetSource> sourcesAfterDiscovery, Runnable onStateChanged)
{
    /**
     * Normalises the lists so the pipeline never has to check for null.
     *
     * @param baseDir               the cache root.
     * @param bundle                the install bundle.
     * @param packFormatMajor       the pack format.
     * @param sources               the chain tried first.
     * @param discovery             the discoverer, or null.
     * @param sourcesAfterDiscovery the chain tried last.
     * @param onStateChanged        the state-change callback, or null.
     */
    public InstallConfig
    {
        sources = sources == null ? List.of() : List.copyOf(sources);
        sourcesAfterDiscovery = sourcesAfterDiscovery == null ? List.of() : List.copyOf(sourcesAfterDiscovery);
    }

    /**
     * The pack directory that gets injected into the resource pack repository.
     *
     * @return {@code baseDir/pack}.
     */
    public Path packDir()
    {
        return this.baseDir.resolve("pack");
    }

    /**
     * The install record.
     *
     * @return {@code baseDir/state.json}.
     */
    public Path stateFile()
    {
        return this.baseDir.resolve("state.json");
    }

    /**
     * The scratch directory. Everything in it is disposable and is deleted when a run ends, whichever way it
     * ends.
     *
     * @return {@code baseDir/tmp}.
     */
    public Path tempDir()
    {
        return this.baseDir.resolve("tmp");
    }
}
