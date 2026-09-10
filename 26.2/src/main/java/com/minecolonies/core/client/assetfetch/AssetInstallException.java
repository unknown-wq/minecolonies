package com.minecolonies.core.client.assetfetch;

/**
 * A step of the asset install failed in a way the installer understands and can report.
 *
 * <p>Everything the pipeline throws deliberately is this: a bad patch, a jar whose hash matches nothing
 * known, a patch that will not apply. {@link #getMessage()} is written to be shown to a player, not only
 * logged, so keep new messages concrete and free of stack-trace vocabulary.</p>
 */
public class AssetInstallException extends Exception
{
    /**
     * Version id of this class for serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception with a player-showable message.
     *
     * @param message what went wrong.
     */
    public AssetInstallException(final String message)
    {
        super(message);
    }

    /**
     * Creates an exception with a player-showable message and an underlying cause.
     *
     * @param message what went wrong.
     * @param cause   the underlying failure.
     */
    public AssetInstallException(final String message, final Throwable cause)
    {
        super(message, cause);
    }
}
