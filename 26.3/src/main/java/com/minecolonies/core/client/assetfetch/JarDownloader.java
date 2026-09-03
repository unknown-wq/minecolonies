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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Streams an upstream jar to disk, hashing it on the way past.
 *
 * <p>Nothing is buffered in memory: the archive is around 78 MB and the client may be a laptop. The SHA-256 is computed
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
     * How long to wait for the connection itself.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * How long to wait for the server to answer with a status line and headers once connected.
     *
     * <p>Separate from the connect timeout on purpose: a host that accepts a connection and then says
     * nothing would otherwise hold the install open for as long as the player is willing to watch it. The
     * body still has no deadline — a 78 MB download on a slow line is normal, and the player can cancel --
     * so this only bounds the part where nothing should be taking any time at all.</p>
     */
    private static final long RESPONSE_TIMEOUT_SECONDS = 60L;

    /**
     * What this client calls itself.
     *
     * <p>An honest name, not somebody else's. Every one of these sources is a public distribution point
     * being asked for a public file, and the operator of one is entitled to see who is asking; pretending to
     * be a browser to get past something would make this an evasion rather than a download.</p>
     */
    private static final String USER_AGENT = "minecolonies-fabric-assetfetch";

    /**
     * Copy buffer size.
     */
    private static final int BUFFER_SIZE = 1 << 16;

    /**
     * How often to report progress, in bytes.
     */
    private static final long PROGRESS_INTERVAL = 1L << 20;

    /**
     * How often, in milliseconds, the cancellation signal is looked at while the transfer is waiting on the
     * server rather than on the player: for the response head, and for the next chunk of the body.
     *
     * <p>The copy loop polls the signal between reads, which is enough while bytes are flowing. A server
     * that stops sending mid-body leaves the loop inside a read with no deadline, and a poll that never
     * gets its turn is no cancellation at all; a player who pressed Cancel there would have had a thread
     * stuck for as long as the connection lived. So the waits are cut into slices of this length, and a
     * watchdog closes the body stream from the outside when the signal fires.</p>
     */
    private static final long CANCEL_POLL_MILLIS = 250L;

    /**
     * Name of the thread that closes a stalled body stream on cancellation.
     */
    private static final String WATCHDOG_NAME = "MineColonies asset download watchdog";

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
     * @param maxBytes  refuse anything larger than this, whether the server admits the size up front or only
     *                  keeps sending. A source that pins no size still must not be able to fill the disk.
     * @param progress  called as bytes arrive; may be null.
     * @param cancelled polled as bytes arrive; may be null.
     * @return the HTTP status, the byte count and the hash of what arrived.
     * @throws AssetInstallException if the transfer fails, is refused, is oversized, or is cancelled.
     */
    public Result download(final String url, final Path target, final long maxBytes, final ProgressSink progress,
        final CancelSignal cancelled) throws AssetInstallException
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
            .header("Accept", "application/java-archive, application/zip, application/octet-stream, */*")
            .header("User-Agent", USER_AGENT)
            .build();

        long received = 0;
        int status = -1;
        try
        {
            final HttpResponse<InputStream> response = await(request, cancelled);
            status = response.statusCode();
            if (status / 100 != 2)
            {
                response.body().close();
                throw new HttpStatusException(status, "the server answered HTTP " + status);
            }

            final long total = response.headers().firstValueAsLong("content-length").orElse(-1L);
            if (total > maxBytes)
            {
                response.body().close();
                throw new SourceFailure(status, 0, "the server offered " + total + " bytes, more than the " + maxBytes
                    + " this source is allowed to send", null);
            }
            final MessageDigest digest = Hashes.newSha256();
            Files.createDirectories(target.getParent());

            try (InputStream in = response.body();
                 OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
            {
                final Thread watchdog = cancelled == null ? null : closeOnCancel(in, cancelled);
                try
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
                        received += read;
                        if (received > maxBytes)
                        {
                            throw new SourceFailure(status, received, "the download passed " + maxBytes
                                + " bytes, which is more than this source is allowed to send", null);
                        }
                        out.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        if (progress != null && received >= nextReport)
                        {
                            progress.accept(received, total);
                            nextReport = received + PROGRESS_INTERVAL;
                        }
                    }
                }
                finally
                {
                    if (watchdog != null)
                    {
                        watchdog.interrupt();
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
        catch (final SourceFailure e)
        {
            throw e;
        }
        catch (final HttpStatusException e)
        {
            throw new SourceFailure(e.status(), 0, e.getMessage(), e);
        }
        catch (final IOException e)
        {
            if (cancelled != null && cancelled.isCancelled())
            {
                // The watchdog closed the stream under a read that was not coming back on its own.
                throw new InstallCancelledException();
            }
            throw new SourceFailure(status, received, describe(e), e);
        }
        catch (final InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new SourceFailure(status, received, "the download was interrupted", e);
        }
    }

    /**
     * Starts a thread that closes the body stream the moment the player cancels, so a read that is waiting
     * on a server that has stopped sending does not outlive the player's decision.
     *
     * <p>The copy loop interrupts it when the transfer ends on its own. Closing the stream is the only thing
     * it ever does, and closing it twice is harmless, so the race between the loop finishing and the
     * watchdog noticing a late cancellation costs nothing.</p>
     *
     * @param in        the body stream to close.
     * @param cancelled the signal to watch.
     * @return the running watchdog, to be interrupted when the transfer is over.
     */
    private static Thread closeOnCancel(final InputStream in, final CancelSignal cancelled)
    {
        final Thread thread = new Thread(() ->
        {
            try
            {
                while (!cancelled.isCancelled())
                {
                    Thread.sleep(CANCEL_POLL_MILLIS);
                }
                in.close();
            }
            catch (final InterruptedException e)
            {
                // The transfer finished first; nothing to close.
            }
            catch (final IOException e)
            {
                // The stream was already closed by the loop; either way it is no longer being read.
            }
        }, WATCHDOG_NAME);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    /**
     * Sends the request and waits for the response head, and no longer than {@link #RESPONSE_TIMEOUT_SECONDS}
     * for it.
     *
     * <p>{@code sendAsync} with a streaming body handler completes its future as soon as the status line and
     * headers have arrived, which is exactly the part worth putting a clock on. The body is then read from
     * the returned stream at whatever pace the line allows.</p>
     *
     * <p>The wait is sliced so the cancellation signal gets a look every {@link #CANCEL_POLL_MILLIS}: a
     * player who presses Cancel while the server is still making up its mind should not have to wait out
     * the whole minute.</p>
     *
     * @param request   the request to send.
     * @param cancelled the cancellation signal; may be null.
     * @return the response, its body not yet read.
     * @throws IOException                 if the exchange fails or the head does not arrive in time.
     * @throws InterruptedException        if the wait is interrupted.
     * @throws InstallCancelledException if the player cancelled while waiting.
     */
    private HttpResponse<InputStream> await(final HttpRequest request, final CancelSignal cancelled)
        throws IOException, InterruptedException, InstallCancelledException
    {
        final java.util.concurrent.CompletableFuture<HttpResponse<InputStream>> pending =
            this.client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RESPONSE_TIMEOUT_SECONDS);
        try
        {
            while (true)
            {
                try
                {
                    return pending.get(CANCEL_POLL_MILLIS, TimeUnit.MILLISECONDS);
                }
                catch (final TimeoutException e)
                {
                    if (cancelled != null && cancelled.isCancelled())
                    {
                        pending.cancel(true);
                        throw new InstallCancelledException();
                    }
                    if (System.nanoTime() >= deadline)
                    {
                        pending.cancel(true);
                        throw new IOException("the server did not answer within " + RESPONSE_TIMEOUT_SECONDS + " seconds", e);
                    }
                }
            }
        }
        catch (final ExecutionException e)
        {
            final Throwable cause = e.getCause();
            if (cause instanceof IOException io)
            {
                throw io;
            }
            throw new IOException(cause == null ? e.toString() : cause.toString(), cause);
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
