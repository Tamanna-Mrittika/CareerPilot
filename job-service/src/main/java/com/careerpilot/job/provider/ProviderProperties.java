package com.careerpilot.job.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param remotiveUrl   Remotive feed. Their terms advise at most ~4 requests/day and warn
 *                      that excessive polling is blocked -- one reason ingestion is
 *                      scheduled rather than triggered per user search.
 * @param arbeitnowUrl  Arbeitnow feed, keyless.
 * @param remoteOkUrl   RemoteOK feed, keyless.
 * @param adzunaBaseUrl Adzuna API root.
 * @param adzunaAppId   Blank disables the Adzuna provider entirely.
 * @param adzunaAppKey  Blank disables the Adzuna provider entirely.
 * @param adzunaCountry Two-letter country code; Adzuna partitions its API by country.
 * @param adzunaPages   Pages per run. The free tier allows ~1,000 calls per MONTH (~33/day),
 *                      so this stays deliberately small.
 * @param jsearchApiKey Blank disables JSearch -- and with it all Bangladesh coverage.
 * @param jsearchQueries Free-text queries, one API call each. Free tier is 200 calls a
 *                      MONTH, so keep this list short.
 * @param apifyToken   Blank disables Apify -- and with it the only working Dhaka source.
 * @param apifyTimeout Actor runs take minutes, not seconds, so this is far longer than
 *                     the shared provider timeout.
 * @param timeout      Per-provider request timeout.
 */
@ConfigurationProperties(prefix = "careerpilot.providers")
public record ProviderProperties(
        String remotiveUrl,
        String arbeitnowUrl,
        String remoteOkUrl,
        String adzunaBaseUrl,
        String adzunaAppId,
        String adzunaAppKey,
        String adzunaCountry,
        Integer adzunaPages,
        String jsearchBaseUrl,
        String jsearchApiKey,
        java.util.List<String> jsearchQueries,
        String apifyToken,
        String apifyActorId,
        java.util.List<String> apifySearchTerms,
        String apifyLocation,
        java.util.List<String> apifySites,
        Integer apifyMaxResults,
        Duration apifyTimeout,
        Duration timeout,
        String userAgent
) {
    public ProviderProperties {
        if (jsearchQueries == null || jsearchQueries.isEmpty()) {
            jsearchQueries = java.util.List.of("software engineer in Dhaka, Bangladesh");
        }
        if (apifyActorId == null || apifyActorId.isBlank()) {
            apifyActorId = "openclawai~job-board-scraper";
        }
        if (apifySearchTerms == null || apifySearchTerms.isEmpty()) {
            apifySearchTerms = java.util.List.of("software engineer");
        }
        if (apifyLocation == null || apifyLocation.isBlank()) {
            apifyLocation = "Dhaka, Bangladesh";
        }
        if (apifySites == null || apifySites.isEmpty()) {
            // linkedin only: verified as the board that actually returns Dhaka results.
            apifySites = java.util.List.of("linkedin");
        }
        if (apifyMaxResults == null || apifyMaxResults < 1) {
            apifyMaxResults = 25;
        }
        if (apifyTimeout == null) {
            apifyTimeout = Duration.ofMinutes(5);
        }
        if (adzunaPages == null || adzunaPages < 1) {
            adzunaPages = 1;
        }
        if (timeout == null) {
            timeout = Duration.ofSeconds(20);
        }
        if (adzunaCountry == null || adzunaCountry.isBlank()) {
            adzunaCountry = "gb";
        }
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "CareerPilot/1.0 (academic project)";
        }
    }
}
