package com.minecolonies.core.client.assetfetch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecolonies.api.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks LDTTeam's repository which releases it is serving right now, and turns the answer into sources.
 *
 * <p>GitHub's release listing is readable without a token, without registration and without a key: one
 * anonymous {@code GET} to {@code /repos/{owner}/{repo}/releases}, rate-limited to 60 requests an hour per
 * address. That is an ordinary documented use of a public API, which is why it is here and why CurseForge —
 * whose equivalent endpoint refuses every keyless request — is not.</p>
 *
 * <h2>How little this is allowed to cost</h2>
 *
 * <p>One request. One page of it. No retry loop. The rate limit is shared by everyone behind a player's
 * address, so this asks once and takes what it gets, and it only asks at all when every shipped source has
 * already failed — which for a player whose install worked is never.</p>
 *
 * <h2>The answer is somebody else's data</h2>
 *
 * <p>Nothing that arrives here is put into a URL as it stands. A tag is accepted only if it matches
 * {@link #TAG} exactly — digits, dots and the literal {@code -snapshot}, nothing else — and only if the
 * Minecraft version it names is the one this build is for. The download URL is then rebuilt from that
 * validated tag rather than from anything the response offered, so a tag carrying a path traversal, a query
 * string or another host cannot become a request. The bytes that come back from such a URL are still held to
 * every per-file hash in the manifest, exactly as a pinned source is: discovery decides what to try, never
 * what to trust.</p>
 *
 * <h2>Which releases, and in what order</h2>
 *
 * <p>The manifest describes one upstream build. A different build is usable only if its assets happen to be
 * identical to that one's, which is common — upstream's asset tree went unchanged across the whole range this
 * port was measured over — but is never assumed: a candidate that differs anywhere fails verification and the
 * chain moves on.</p>
 *
 * <p>Which to try first follows from where a change can be. A build older than the one the manifest describes
 * cannot contain a change made after it; a newer one is precisely where such a change would first show up.
 * So candidates are ordered older before newer, and within each by nearness to the described build. That is
 * the ordering most likely to succeed on the first download rather than after three 70 MB ones, and only the
 * first few are queued at all, for the same reason.</p>
 */
public final class GithubReleases implements SourceDiscovery
{
    /**
     * The release listing, first page only.
     */
    private static final String LISTING = "https://api.github.com/repos/%s/releases?per_page=100&page=1";

    /**
     * The only tag shape that is accepted: {@code v<game version>-<mod version>} with an optional
     * {@code -snapshot}. Every character is a digit, a dot or part of that literal suffix, so a tag that gets
     * through is safe to put in a URL path.
     */
    private static final Pattern TAG = Pattern.compile("^v(\\d{1,3}(?:\\.\\d{1,3}){1,2})-(\\d{1,4}\\.\\d{1,4}\\.(\\d{1,6}))(-snapshot)?$");

    /**
     * How long to wait for the connection.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    /**
     * How long to wait for an answer once connected.
     */
    private static final long RESPONSE_TIMEOUT_SECONDS = 15L;

    /**
     * The most of the listing that will be read. Release notes are part of it and can be long; this is far
     * above a hundred of them and far below anything that would hurt.
     */
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    /**
     * How many discovered releases to queue. Each one that does not verify costs a whole archive download, so
     * this is small on purpose.
     */
    private static final int MAX_CANDIDATES = 3;

    /**
     * What this client calls itself. GitHub refuses a request without one, and an honest name is the whole of
     * what is needed — nothing here pretends to be a browser or anybody else.
     */
    private static final String USER_AGENT = "minecolonies-fabric-assetfetch";

    /**
     * The repository to ask, {@code owner/name}.
     */
    private final String repository;

    /**
     * The Minecraft version whose tags are wanted.
     */
    private final String gameVersion;

    /**
     * The upstream build the manifest describes, for ordering candidates by nearness.
     */
    private final int referenceBuild;

    /**
     * Tags already in the shipped chain, which there is no point discovering again.
     */
    private final Set<String> alreadyTried;

    /**
     * Creates a discoverer.
     *
     * @param repository     the repository to ask, {@code owner/name}.
     * @param gameVersion    the Minecraft version whose tags are wanted.
     * @param referenceBuild the upstream build number the manifest describes.
     * @param alreadyTried   tags the shipped chain has already tried.
     */
    public GithubReleases(final String repository, final String gameVersion, final int referenceBuild,
        final Set<String> alreadyTried)
    {
        this.repository = repository;
        this.gameVersion = gameVersion;
        this.referenceBuild = referenceBuild;
        this.alreadyTried = Set.copyOf(alreadyTried);
    }

    @Override
    public List<AssetSource> discover()
    {
        final JsonArray listing;
        try
        {
            listing = request();
        }
        catch (final IOException | RuntimeException e)
        {
            Log.getLogger().warn("Could not read the {} release listing: {}", this.repository, e.getMessage());
            return List.of();
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return List.of();
        }

        final List<AssetSource> sources = select(listing);
        if (sources.isEmpty())
        {
            Log.getLogger().warn("The {} release listing named no usable {} tag", this.repository, this.gameVersion);
        }
        else
        {
            Log.getLogger().info("Discovered {} further MineColonies source release(s): {}", sources.size(),
                sources.stream().map(AssetSource::id).toList());
        }
        return sources;
    }

    /**
     * Picks the sources worth trying out of a release listing.
     *
     * <p>Separate from the request that fetched it so that the part with the judgement in it — what counts
     * as a usable tag, and which of them to try first — can be exercised without a network.</p>
     *
     * @param listing the listing as it arrived.
     * @return the sources to try, nearest build to the manifest's first, at most {@link #MAX_CANDIDATES}.
     */
    List<AssetSource> select(final JsonArray listing)
    {
        final List<Candidate> candidates = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        for (final JsonElement element : listing)
        {
            final Candidate candidate = candidate(element);
            if (candidate != null && seen.add(candidate.tag()))
            {
                candidates.add(candidate);
            }
        }

        // Nearness is not symmetric. A build older than the one the manifest describes cannot contain a
        // change made after it, while a newer one is exactly where such a change would first appear, so an
        // older candidate is tried before a newer one that happens to be as close.
        candidates.sort(Comparator
            .comparingInt((Candidate c) -> c.build() > this.referenceBuild ? 1 : 0)
            .thenComparingInt(c -> Math.abs(c.build() - this.referenceBuild))
            .thenComparingInt(Candidate::build));

        final List<AssetSource> sources = new ArrayList<>(MAX_CANDIDATES);
        for (final Candidate candidate : candidates.subList(0, Math.min(MAX_CANDIDATES, candidates.size())))
        {
            sources.add(SourceChain.discovered(candidate.tag(), candidate.build()));
        }
        return sources;
    }

    /**
     * Turns one listing entry into a candidate, or refuses it.
     *
     * @param element the entry as it arrived.
     * @return the candidate, or null when the entry is a draft, malformed, for another game version, or
     *         already in the shipped chain.
     */
    private Candidate candidate(final JsonElement element)
    {
        if (!element.isJsonObject())
        {
            return null;
        }
        final JsonObject release = element.getAsJsonObject();
        final JsonElement draft = release.get("draft");
        if (draft != null && draft.isJsonPrimitive() && draft.getAsBoolean())
        {
            return null;
        }
        final JsonElement tagName = release.get("tag_name");
        if (tagName == null || !tagName.isJsonPrimitive())
        {
            return null;
        }

        final String tag = tagName.getAsString();
        final Matcher matcher = TAG.matcher(tag);
        if (!matcher.matches() || !this.gameVersion.equals(matcher.group(1)) || this.alreadyTried.contains(tag))
        {
            return null;
        }
        try
        {
            return new Candidate(tag, Integer.parseInt(matcher.group(3)));
        }
        catch (final NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * Performs the one request this class is allowed.
     *
     * @return the parsed listing.
     * @throws IOException          if the request fails, is refused, answers something other than an array,
     *                              or answers more than {@link #MAX_BODY_BYTES}.
     * @throws InterruptedException if the wait is interrupted.
     */
    private JsonArray request() throws IOException, InterruptedException
    {
        final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .proxy(java.net.ProxySelector.getDefault())
            .build();

        final HttpRequest request = HttpRequest.newBuilder(URI.create(String.format(LISTING, this.repository)))
            .GET()
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", USER_AGENT)
            .build();

        final HttpResponse<InputStream> response;
        try
        {
            response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .get(RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (final TimeoutException e)
        {
            throw new IOException("no answer within " + RESPONSE_TIMEOUT_SECONDS + " seconds", e);
        }
        catch (final ExecutionException e)
        {
            final Throwable cause = e.getCause();
            throw new IOException(cause == null ? e.toString() : cause.toString(), cause);
        }

        final byte[] body;
        try (InputStream in = response.body())
        {
            if (response.statusCode() != 200)
            {
                // 403 with a rate-limit header is the expected refusal once the hourly allowance is spent.
                throw new IOException("HTTP " + response.statusCode()
                    + (response.statusCode() == 403 ? " (the anonymous allowance is 60 requests an hour)" : ""));
            }
            body = read(in);
        }

        final JsonElement parsed = CanonicalJson.parse(body, "the release listing");
        if (!parsed.isJsonArray())
        {
            throw new IOException("the release listing was not a JSON array");
        }
        return parsed.getAsJsonArray();
    }

    /**
     * Reads a response body, refusing one that will not stop.
     *
     * @param in the body stream.
     * @return the bytes.
     * @throws IOException if reading fails or the body is over {@link #MAX_BODY_BYTES}.
     */
    private static byte[] read(final InputStream in) throws IOException
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        final byte[] buffer = new byte[1 << 16];
        int count;
        while ((count = in.read(buffer)) > 0)
        {
            if (out.size() + count > MAX_BODY_BYTES)
            {
                throw new IOException("the release listing is larger than " + MAX_BODY_BYTES + " bytes");
            }
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }

    /**
     * A release that got through validation.
     *
     * @param tag   the tag, already checked against {@link #TAG}.
     * @param build the upstream build number it names.
     */
    private record Candidate(String tag, int build)
    {
    }
}
