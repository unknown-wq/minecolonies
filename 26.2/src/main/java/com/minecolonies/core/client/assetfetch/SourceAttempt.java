package com.minecolonies.core.client.assetfetch;

/**
 * What happened when the installer tried one source.
 *
 * <p>The escalation rule in the brief asks for exactly these four facts per failed source — the URL, the HTTP
 * status, how many bytes arrived, and the error text — so the owner can act on a dead download instead of
 * guessing. They are collected here for every attempt, successful or not, and handed to the UI in
 * {@link InstallReport#attempts()} as well as written to the log.</p>
 *
 * @param sourceId   which chain entry this was.
 * @param url        the URL or local path that was tried.
 * @param succeeded  whether this attempt produced a usable jar.
 * @param httpStatus the HTTP status, or -1 when the request never got one.
 * @param bytes      how many bytes arrived before success or failure.
 * @param sha256     the whole-jar hash of what arrived, or null when nothing usable arrived.
 * @param error      the failure text, or null on success.
 */
public record SourceAttempt(String sourceId, String url, boolean succeeded, int httpStatus, long bytes, String sha256, String error)
{
    /**
     * A one-line form for logs and for the failure screen.
     *
     * @return the description.
     */
    public String describe()
    {
        if (this.succeeded)
        {
            return this.sourceId + " (" + this.url + "): ok, " + this.bytes + " bytes";
        }
        return this.sourceId + " (" + this.url + "): "
            + (this.httpStatus >= 0 ? "HTTP " + this.httpStatus + ", " : "")
            + this.bytes + " bytes received, " + this.error;
    }
}
