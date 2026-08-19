package com.careerpilot.job.domain;

/**
 * Where a posting came from.
 *
 * <p>{@code attribution} is not cosmetic. Remotive's and RemoteOK's API terms both require
 * naming them as the source and linking back to their URL; failing to do so is grounds for
 * them revoking access. Storing it on every row means the UI cannot forget.
 */
public enum JobSource {

    REMOTIVE("Remotive", true),
    ARBEITNOW("Arbeitnow", false),
    REMOTEOK("Remote OK", true),
    ADZUNA("Adzuna", false),
    /** Google for Jobs aggregator. */
    JSEARCH("Google for Jobs", false),
    /**
     * Apify multi-board scraper. Verified as the only source that actually returns Dhaka
     * postings, via LinkedIn -- the actor's own BDJobs board returns nothing.
     */
    APIFY("LinkedIn via Apify", true);

    private final String attribution;
    private final boolean linkBackRequired;

    JobSource(String attribution, boolean linkBackRequired) {
        this.attribution = attribution;
        this.linkBackRequired = linkBackRequired;
    }

    public String attribution() {
        return attribution;
    }

    public boolean isLinkBackRequired() {
        return linkBackRequired;
    }
}
