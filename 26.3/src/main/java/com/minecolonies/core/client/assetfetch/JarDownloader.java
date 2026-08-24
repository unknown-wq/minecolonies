package com.minecolonies.core.client.assetfetch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;

/**
 * Streams an upstream jar to disk, hashing it on the way past.
 *
 * <p>Nothing is buffered in memory: the jar is 78 MB and the client may be a laptop. The SHA-256 is computed
 * from the same bytes that are written, so the hash that gets checked against the pin is the hash of what
 * actually landed on disk.</p>
 *
 * <p>{@code java.net.http.HttpClient} with redirects enabled, and the JVM's default proxy selector so a
 * player behind a corporate proxy is not stuck. No TLS configuration of any kind: {@code https://} uses the
 * JVM's own trust store and {@code http://} is accepted as-is, because for this feature integrity comes from
 * the whole-jar hash and the per-file manifest rather than from the transport.</p>
 */
public final class JarDownloader
{
    /**
     * How long to wait for the connection itself. The body has no deadline: a 78 MB download on a slow line
     * is normal, and the player can cancel.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Copy buffer size.
     */
    private static final int BUFFER_SIZE = 1 << 16;

    /**
     * How often to report progress, in bytes.
     */
    private static final long PROGRESS_INTERVAL = 1L << 20;

    /**
     * The client, built once per installer run.
     */
    private final HttpClient client;

    /**
     * Creates a downloader with the shipped client configuration.
     */
    public JarDownloader()
    {
        this.client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .proxy(java.net.ProxySelector.getDefault())
            .build();
    }

    /**
     * Downloads a jar.
     *
     * @param url       where to get it.
     * @param target    where to write it. Overwritten if it exists.
     * @param progress  called as bytes arrive; may be null.
     * @param cancelled polled as bytes arrive; may be null.
     * @return the HTTP status, the byte count and the hash of what arrived.
     * @throws AssetInstallException if the transfer fails, is refused, or is cancelled.
     */
    public Result download(final String url, final Path target, final ProgressSink progress, final CancelSignal cancelled) throws AssetInstallException
    {
        final URI uri;
        try
        {
            uri = URI.create(url);
        }
        catch (final IllegalArgumentException e)
        {
            throw new AssetInstallException("Not a usable download URL: " + url, e);
        }

        final HttpRequest request = HttpRequest.newBuilder(uri)
            .GET()
            .header("Accept", "application/java-archive, application/octet-stream, */*")
            .build();

        long received = 0;
        int status = -1;
        try
        {
            final HttpResponse<InputStream> response = this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            status = response.statusCode();
            if (status / 100 != 2)
            {
                response.body().close();
                throw new HttpStatusException(status, "the server answered HTTP " + status);
            }

            final long total = response.headers().firstValueAsLong("content-length").orElse(-1L);
            final MessageDigest digest = Hashes.newSha256();
            Files.createDirectories(target.getParent());

            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
            {
                final byte[] buffer = new byte[BUFFER_SIZE];
                long nextReport = PROGRESS_INTERVAL;
                int read;
                while ((read = in.read(buffer)) > 0)
                {
                    if (cancelled != null && cancelled.isCancelled())
                    {
                        throw new InstallCancelledException();
                    }
                    out.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    received += read;
                    if (progress != null && received >= nextReport)
                    {
                        progress.accept(received, total);
                        nextReport = received + PROGRESS_INTERVAL;
                    }
                }
            }

            if (progress != null)
            {
                progress.accept(received, total);
            }
            if (total >= 0 && received != total)
            {
                throw new SourceFailure(status, received, "the download stopped early: " + received + " of " + total + " bytes", null);
            }
            return new Result(status, received, Hashes.hex(digest.digest()));
        }
        catch (final HttpStatusException e)
        {
            throw new SourceFailure(e.status(), 0, e.getMessage(), e);
        }
        catch (final IOException e)
        {
            throw new SourceFailure(status, received, describe(e), e);
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new SourceFailure(status, received, "the download was interrupted", e);
        }
    }

    /**
     * Turns an I/O failure into something worth showing a player.
     *
     * @param e the failure.
     * @return a one-line description.
     */
    private static String describe(final IOException e)
    {
        final String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    /**
     * What a completed download produced.
     *
     * @param httpStatus the final HTTP status.
     * @param bytes      how many bytes arrived.
     * @param sha256     their SHA-256, lower-case hex.
     */
    public record Result(int httpStatus, long bytes, String sha256)
    {
    }

    /**
     * A failure that carries the per-source diagnostics the brief's escalation rule asks for.
     */
    public static class SourceFailure extends AssetInstallException
    {
        /**
         * Version id of this class for serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * The HTTP status, or -1 if the request never got that far.
         */
        private final int status;

        /**
         * How many bytes had arrived before the failure.
         */
        private final long bytes;

        /**
         * Creates a source failure.
         *
         * @param status  the HTTP status, or -1.
         * @param bytes   bytes received before the failure.
         * @param message what went wrong.
         * @param cause   the underlying failure.
         */
        public SourceFailure(final int status, final long bytes, final String message, final Throwable cause)
        {
            super(message, cause);
            this.status = status;
            this.bytes = bytes;
        }

        /**
         * The HTTP status.
         *
         * @return the status, or -1 when there was none.
         */
        public int status()
        {
            return this.status;
        }

        /**
         * How far the transfer got.
         *
         * @return bytes received.
         */
        public long bytes()
        {
            return this.bytes;
        }
    }

    /**
     * Internal marker for a non-2xx response, so the status survives into {@link SourceFailure}.
     */
    private static final class HttpStatusException extends IOException
    {
        /**
         * Version id of this class for serialization.
         */
        private static final long serialVersionUID = 1L;

        /**
         * The status the server answered with.
         */
        private final int status;

        /**
         * Creates the marker.
         *
         * @param status  the HTTP status.
         * @param message the description.
         */
        private HttpStatusException(final int status, final String message)
        {
            super(message);
            this.status = status;
        }

        /**
         * The status.
         *
         * @return the HTTP status.
         */
        private int status()
        {
            return this.status;
        }
    }

    /**
     * How many bytes have arrived.
     */
    @FunctionalInterface
    public interface ProgressSink
    {
        /**
         * Reports progress.
         *
         * @param transferred bytes received so far.
         * @param total       the expected total, or -1 when the server did not say.
         */
        void accept(long transferred, long total);
    }
}
