package com.minecolonies.core.client.assetfetch;

/**
 * The player cancelled the install.
 *
 * <p>Its own type so the pipeline can tell it apart from a source that failed: a failed source means "try the
 * next one", a cancellation means "stop, clean up, and change nothing".</p>
 */
public final class InstallCancelledException extends AssetInstallException
{
    /**
     * Version id of this class for serialization.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     */
    public InstallCancelledException()
    {
        super("The download was cancelled");
    }
}
