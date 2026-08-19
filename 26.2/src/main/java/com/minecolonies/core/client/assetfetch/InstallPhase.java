package com.minecolonies.core.client.assetfetch;

/**
 * The stages an install goes through, in order, for the progress UI to label.
 *
 * <p>Only {@link #DOWNLOADING} has a meaningful byte count; the others report a count of files. They arrive
 * on {@link InstallListener#onPhase} on the installer's own thread, never on the client thread.</p>
 */
public enum InstallPhase
{
    /**
     * Working out where to fetch from and preparing the cache directories.
     */
    STARTING,

    /**
     * Streaming an upstream jar to the temporary directory. Byte progress is live here.
     */
    DOWNLOADING,

    /**
     * Checking the whole-jar hash against the pinned values before anything is unpacked.
     */
    CHECKING_JAR,

    /**
     * Unpacking {@code assets/minecolonies/**} out of the jar.
     */
    EXTRACTING,

    /**
     * Applying the shipped patch bundle to the unpacked tree.
     */
    PATCHING,

    /**
     * Pruning what the manifest does not list and hashing what it does.
     */
    VERIFYING,

    /**
     * Moving the staged pack into place and recording the install.
     */
    INSTALLING,

    /**
     * Nothing left to do; a terminal report has been delivered.
     */
    DONE
}
