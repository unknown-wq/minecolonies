package com.minecolonies.core.client.assetfetch;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies the {@code diff -U0} unified diffs the asset bundle ships for the six edited BlockUI XML files.
 *
 * <p>Zero context lines is the point: a context line would be a line of upstream text quoted in our jar, and
 * the bundle quotes as little of it as it possibly can. The cost is that there is no fuzz to fall back on —
 * a hunk applies at the line it says or it does not apply at all. That is the behaviour we want anyway: a
 * hunk that no longer matches means the upstream file changed under us, and installing a mangled GUI would
 * be worse than stopping.</p>
 *
 * <p>Only the shape GNU {@code diff -U0} emits is understood: {@code ---}/{@code +++} headers,
 * {@code @@ -start[,count] +start[,count] @@} hunk headers, and {@code -}/{@code +} lines. A
 * {@code \ No newline at end of file} marker is rejected rather than guessed at; no file in the bundle has
 * one.</p>
 */
public final class UnifiedDiffPatcher
{
    /**
     * Private constructor to hide the public one.
     */
    private UnifiedDiffPatcher()
    {
        /*
         * Intentionally left empty.
         */
    }

    /**
     * Applies a unified diff to a text.
     *
     * @param original the text to patch.
     * @param diff     the diff.
     * @param origin   a name for the patched file, used in error messages.
     * @return the patched text.
     * @throws AssetInstallException if the diff is malformed or a hunk does not match.
     */
    public static String apply(final String original, final String diff, final String origin) throws AssetInstallException
    {
        final boolean trailingNewline = original.endsWith("\n");
        final List<String> lines = splitLines(original);
        final List<String> out = new ArrayList<>(lines.size());
        final String[] diffLines = diff.split("\n", -1);

        int cursor = 0;
        int i = 0;
        while (i < diffLines.length)
        {
            final String line = diffLines[i];
            if (line.startsWith("--- ") || line.startsWith("+++ ") || line.isEmpty())
            {
                i++;
                continue;
            }
            if (line.startsWith("\\"))
            {
                throw new AssetInstallException("Unsupported '" + line.trim() + "' marker in the diff for " + origin);
            }
            if (!line.startsWith("@@"))
            {
                throw new AssetInstallException("Unexpected line in the diff for " + origin + ": " + line);
            }

            final int[] header = parseHunkHeader(line, origin);
            final int oldStart = header[0];
            final int oldCount = header[1];
            i++;

            // Everything before the hunk is copied through unchanged.
            final int target = oldCount == 0 ? oldStart : oldStart - 1;
            if (target < cursor || target > lines.size())
            {
                throw new AssetInstallException("Hunk '" + line + "' of " + origin + " points outside the file");
            }
            while (cursor < target)
            {
                out.add(lines.get(cursor++));
            }

            int removed = 0;
            final List<String> added = new ArrayList<>();
            while (i < diffLines.length && (diffLines[i].startsWith("-") || diffLines[i].startsWith("+")))
            {
                final String body = diffLines[i].substring(1);
                if (diffLines[i].startsWith("-"))
                {
                    if (!added.isEmpty())
                    {
                        throw new AssetInstallException("Malformed hunk in the diff for " + origin + ": '-' after '+'");
                    }
                    if (cursor >= lines.size() || !lines.get(cursor).equals(body))
                    {
                        throw new AssetInstallException("The base file " + origin + " does not match the shipped patch at line "
                            + (cursor + 1) + " -- the upstream file has changed");
                    }
                    cursor++;
                    removed++;
                }
                else
                {
                    added.add(body);
                }
                i++;
            }

            if (removed != oldCount)
            {
                throw new AssetInstallException("Hunk '" + line + "' of " + origin + " removes " + removed + " lines, header says " + oldCount);
            }
            out.addAll(added);
        }

        while (cursor < lines.size())
        {
            out.add(lines.get(cursor++));
        }

        final StringBuilder result = new StringBuilder();
        for (int n = 0; n < out.size(); n++)
        {
            result.append(out.get(n));
            if (n < out.size() - 1 || trailingNewline)
            {
                result.append('\n');
            }
        }
        return result.toString();
    }

    /**
     * Parses {@code @@ -start[,count] +start[,count] @@}.
     *
     * @param header the hunk header line.
     * @param origin the file being patched, for error messages.
     * @return the old-side start line (1-based) and line count.
     * @throws AssetInstallException if the header is malformed.
     */
    private static int[] parseHunkHeader(final String header, final String origin) throws AssetInstallException
    {
        final int minus = header.indexOf('-');
        final int space = header.indexOf(' ', minus);
        if (minus < 0 || space < 0)
        {
            throw new AssetInstallException("Malformed hunk header in the diff for " + origin + ": " + header);
        }
        final String range = header.substring(minus + 1, space);
        final int comma = range.indexOf(',');
        try
        {
            final int start = Integer.parseInt(comma < 0 ? range : range.substring(0, comma));
            final int count = comma < 0 ? 1 : Integer.parseInt(range.substring(comma + 1));
            return new int[] {start, count};
        }
        catch (final NumberFormatException e)
        {
            throw new AssetInstallException("Malformed hunk header in the diff for " + origin + ": " + header);
        }
    }

    /**
     * Splits a text into lines, dropping the empty remainder a trailing newline produces.
     *
     * @param text the text.
     * @return its lines, without terminators.
     */
    private static List<String> splitLines(final String text)
    {
        final List<String> lines = new ArrayList<>();
        final String[] parts = text.split("\n", -1);
        final int limit = parts.length > 0 && parts[parts.length - 1].isEmpty() ? parts.length - 1 : parts.length;
        for (int i = 0; i < limit; i++)
        {
            lines.add(parts[i]);
        }
        return lines;
    }
}
