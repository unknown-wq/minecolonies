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

    /**
     * The main explanation: what is missing and what will be downloaded.
     *
     * <p>One argument, a bare number of megabytes. The unit belongs to the translation, as on the progress
     * screen, so each language says it once and in its own alphabet.</p>
     */
    public static final String CONSENT_BODY = P + "consent.body";

    /** Title of the consent screen when an older version's assets are already installed. */
    public static final String CONSENT_TITLE_UPDATE = P + "consent.title.update";

    /**
     * The main explanation when the installed assets are an earlier version's: what does not match, what
     * replaces it, and that nothing already installed is removed until the replacement is in hand.
     *
     * <p>One argument, the same bare number of megabytes as {@link #CONSENT_BODY}.</p>
     */
    public static final String CONSENT_BODY_UPDATE = P + "consent.body.update";

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

    /**
     * The source line for LDTTeam's current official build; the argument is the version, e.g. {@code 1.1.1374}.
     *
     * <p>There are four of these, one per kind of source, because a player wants to know <i>what</i> is being
     * fetched, not from which URL. The URL is still written to the log and to the failure screen's per-source
     * detail lines, where it is being read as diagnostics rather than as prose.</p>
     */
    public static final String PROGRESS_SOURCE_OFFICIAL = P + "progress.source.official";

    /** The source line for the older Maven build, tried when the current one is gone. */
    public static final String PROGRESS_SOURCE_BACKUP = P + "progress.source.backup";

    /** The source line for a host this build knows nothing else about; the argument is the host name. */
    public static final String PROGRESS_SOURCE_HOST = P + "progress.source.host";

    /** The source line for a jar the player picked; the argument is the file name, without its directory. */
    public static final String PROGRESS_SOURCE_FILE = P + "progress.source.file";

    /** Byte line during the download. The unit belongs to the translation, so the arguments are bare numbers. */
    public static final String PROGRESS_BYTES = P + "progress.bytes";

    /** Byte line when the server did not send a content length. */
    public static final String PROGRESS_BYTES_UNKNOWN = P + "progress.bytes.unknown";

    /** File line during extract, patch and assemble. Argument 1 is the phase label, then done and total. */
    public static final String PROGRESS_FILES = P + "progress.files";

    /** File line while the total is still unknown. Argument 1 is the phase label. */
    public static final String PROGRESS_FILES_UNKNOWN = P + "progress.files.unknown";

    /** Told to the player while the game reloads its resources. */
    public static final String PROGRESS_RELOADING = P + "progress.reloading";

    // ---- phase labels ----

    /**
     * Label prefix for {@code InstallPhase}; the enum name in lower case is appended.
     *
     * <p>These read as bare noun phrases — "Unpacking", not "Unpacking..." — because they are shown next to
     * the file counter on one line ("Unpacking — 3,210 of 8,474 files") as often as they are shown alone. The
     * bar above them is what says the work is still going.</p>
     */
    public static final String PHASE_PREFIX = P + "phase.";

    // ---- result screens ----

    /** Title of the success screen. */
    public static final String DONE_TITLE = P + "done.title";

    /**
     * Body of the success screen: how many files the pack holds, and how big they are.
     *
     * <p>No source id and no claim about readiness, and — since nothing checks a file's contents any more —
     * no claim that anything was verified. Which chain entry answered is a fact for the log; whether the mod
     * "is ready" is a promise this screen is in no position to make.</p>
     */
    public static final String DONE_BODY = P + "done.body";

    /**
     * Shown under {@link #DONE_BODY} when part of the pack is in neither the archive nor an earlier install,
     * so the pack is missing it. One argument: how many files.
     */
    public static final String DONE_PARTIAL = P + "done.partial";

    /**
     * Shown under {@link #DONE_BODY} when the archive did not carry part of the pack and those files were
     * kept from the install before it. One argument: how many.
     */
    public static final String DONE_CARRIED = P + "done.carried";

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
