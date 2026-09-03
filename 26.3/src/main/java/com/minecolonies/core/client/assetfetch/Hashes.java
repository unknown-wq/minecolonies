package com.minecolonies.core.client.assetfetch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 helpers for the asset installer.
 *
 * <p>Everything this feature trusts is trusted because of a hash: the whole downloaded jar against the
 * pinned source hashes, every installed file against {@code manifest.json}, and the manifest itself against
 * what {@code state.json} recorded. This class is the single place those digests are produced.</p>
 */
public final class Hashes
{
    /**
     * Lower-case hex digits, for {@link #hex(byte[])}.
     */
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * Read buffer size for the file digests.
     *
     * <p>64 KiB, not the megabyte this used to allocate. The per-file verification hashes roughly 8500 files
     * whose median size is under a kilobyte, so a megabyte-per-call buffer allocated about 8 GB over a single
     * install and spent more time in the allocator and the collector than in SHA-256 itself. Measured on the
     * staged tree, the same 8471 files hashed in 1450 ms with a fresh megabyte per file and in 110 ms with a
     * reused 64 KiB one. Callers that hash many files in a row should reuse one buffer through
     * {@link #sha256(Path, byte[])}.</p>
     */
    static final int BUFFER_SIZE = 1 << 16;

    /**
     * Private constructor to hide the public one.
     */
    private Hashes()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * A fresh SHA-256 digest.
     *
     * @return the digest.
     */
    public static MessageDigest newSha256()
    {
        try
        {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (final NoSuchAlgorithmException e)
        {
            // Every conforming JRE ships SHA-256; if this one does not, nothing downstream can work.
            throw new IllegalStateException("SHA-256 is not available in this JVM", e);
        }
    }

    /**
     * Hashes a file.
     *
     * @param file the file to read.
     * @return its SHA-256 as lower-case hex.
     * @throws IOException if the file cannot be read.
     */
    public static String sha256(final Path file) throws IOException
    {
        return sha256(file, new byte[BUFFER_SIZE]);
    }

    /**
     * Hashes a file through a caller-owned read buffer.
     *
     * <p>For one file this is the same thing as {@link #sha256(Path)}. It exists for the loops that hash
     * thousands of files in a row, where allocating the buffer per file dominates the cost. The buffer is
     * only ever written and read within this call, so one per worker thread is enough.</p>
     *
     * @param file   the file to read.
     * @param buffer the scratch buffer to read through; any size, {@link #BUFFER_SIZE} is the one used here.
     * @return its SHA-256 as lower-case hex.
     * @throws IOException if the file cannot be read.
     */
    public static String sha256(final Path file, final byte[] buffer) throws IOException
    {
        final MessageDigest digest = newSha256();
        try (InputStream in = Files.newInputStream(file))
        {
            int read;
            while ((read = in.read(buffer)) > 0)
            {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    /**
     * Hashes a byte array.
     *
     * @param bytes the bytes to hash.
     * @return their SHA-256 as lower-case hex.
     */
    public static String sha256(final byte[] bytes)
    {
        return hex(newSha256().digest(bytes));
    }

    /**
     * Renders a digest as lower-case hex, the form every hash in this feature is written and compared in.
     *
     * @param bytes the digest.
     * @return the hex string.
     */
    public static String hex(final byte[] bytes)
    {
        final StringBuilder out = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes)
        {
            out.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        }
        return out.toString();
    }
}
