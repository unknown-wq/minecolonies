package com.minecolonies.core.client.assetfetch.gui;

/**
 * Translation keys for the asset-fetch consent UI (task D1) and the window-open gate (task D2).
 *
 * <p>Every one of these lives in the port's own {@code assets/minecolonies/lang/en_us.json}, which ships
 * <b>inside this jar</b>. That is not an accident: these screens are the ones a player sees when the fetched
 * assets are not installed, so any string they need has to resolve with no resource pack present at all.
 * Nothing here may ever move into the downloaded pack.</p>
 */
public final class AssetFetchLang
{
    /**
     * Common prefix of every key in this file.
     */
    private static final String P = "com.minecolonies.coremod.assetfetch.";

    // ---- consent screen (D1) ----

    /** Title of the consent screen. */
    public static final String CONSENT_TITLE = P + "consent.title";

    /** The main explanation: what is missing and what will be downloaded. */
    public static final String CONSENT_BODY = P + "consent.body";

    /** The licence sentence: All Rights Reserved, stays on this machine. */
    public static final String CONSENT_LICENCE = P + "consent.licence";

    /** The manual escape hatch help line. */
    public static final String CONSENT_MANUAL = P + "consent.manual";

    /** "Download" button. */
    public static final String BUTTON_DOWNLOAD = P + "button.download";

    /** "Not now" button. */
    public static final String BUTTON_NOT_NOW = P + "button.notnow";

    /** "Use a jar I already have" button. */
    public static final String BUTTON_LOCAL_JAR = P + "button.localjar";

    /** "Try again" button on the failure screen. */
    public static final String BUTTON_RETRY = P + "button.retry";

    /** Generic cancel button. */
    public static final String BUTTON_CANCEL = P + "button.cancel";

    /** Generic close/done button. */
    public static final String BUTTON_CLOSE = P + "button.close";

    // ---- local jar screen (source 4) ----

    /** Title of the local-jar screen. */
    public static final String LOCALJAR_TITLE = P + "localjar.title";

    /** Explanation on the local-jar screen. */
    public static final String LOCALJAR_BODY = P + "localjar.body";

    /** Hint inside the empty path field. */
    public static final String LOCALJAR_HINT = P + "localjar.hint";

    /** Shown when the typed path is not a readable file. */
    public static final String LOCALJAR_NOT_A_FILE = P + "localjar.notafile";

    // ---- progress screen (D1) ----

    /** Title of the progress screen. */
    public static final String PROGRESS_TITLE = P + "progress.title";

    /** "Source: %s (%s)" line, shown when a source starts. */
    public static final String PROGRESS_SOURCE = P + "progress.source";

    /** "%s of %s" byte line during the download. */
    public static final String PROGRESS_BYTES = P + "progress.bytes";

    /** Byte line when the server did not send a content length. */
    public static final String PROGRESS_BYTES_UNKNOWN = P + "progress.bytes.unknown";

    /** "%s of %s files" line during extract, patch and verify. */
    public static final String PROGRESS_FILES = P + "progress.files";

    /** File line while the total is still unknown. */
    public static final String PROGRESS_FILES_UNKNOWN = P + "progress.files.unknown";

    /** Told to the player while the game reloads its resources. */
    public static final String PROGRESS_RELOADING = P + "progress.reloading";

    // ---- phase labels ----

    /** Label prefix for {@code InstallPhase}; the enum name in lower case is appended. */
    public static final String PHASE_PREFIX = P + "phase.";

    // ---- result screens ----

    /** Title of the success screen. */
    public static final String DONE_TITLE = P + "done.title";

    /** Body of the success screen: source, files and size. */
    public static final String DONE_BODY = P + "done.body";

    /** Title of the failure screen. */
    public static final String FAILED_TITLE = P + "failed.title";

    /** The outcome line of the failure screen. */
    public static final String FAILED_REASON = P + "failed.reason";

    /** Heading above the per-source attempt list. */
    public static final String FAILED_ATTEMPTS = P + "failed.attempts";

    /** One successful attempt, in the attempt list. */
    public static final String FAILED_ATTEMPT_OK = P + "failed.attempt.ok";

    /** One failed attempt, in the attempt list. */
    public static final String FAILED_ATTEMPT_BAD = P + "failed.attempt.bad";

    /** Says that nothing was installed and the game is exactly as it was. */
    public static final String FAILED_UNCHANGED = P + "failed.unchanged";

    /** Points the player at the port's issue tracker when every online source failed. */
    public static final String FAILED_REPORT = P + "failed.report";

    /** Title of the cancelled screen. */
    public static final String CANCELLED_TITLE = P + "cancelled.title";

    /** Body of the cancelled screen. */
    public static final String CANCELLED_BODY = P + "cancelled.body";

    // ---- window-open gate (D2) ----

    /** Title of the "assets not installed" gate screen. */
    public static final String GATE_TITLE = P + "gate.title";

    /** Body of the gate screen. */
    public static final String GATE_BODY = P + "gate.body";

    // ---- client command ----

    /** Chat feedback when {@code /minecolonies-client fetchassets} finds the assets already installed. */
    public static final String COMMAND_ALREADY_INSTALLED = P + "command.alreadyinstalled";

    /**
     * Private constructor to hide the public one.
     */
    private AssetFetchLang()
    {
        /*
         * Intentionally left empty.
         */
    }
}
