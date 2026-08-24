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
     * Read buffer for {@link #sha256(Path)}. One megabyte: the biggest thing hashed here is a 78 MB jar.
     */
    private static final int BUFFER_SIZE = 1 << 20;

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
        final MessageDigest digest = newSha256();
        final byte[] buffer = new byte[BUFFER_SIZE];
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
