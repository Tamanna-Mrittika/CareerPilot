package com.careerpilot.job.service;

import com.careerpilot.job.domain.JobSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives ingestion on a schedule.
 *
 * <p>The interval is set by provider terms, not by taste. Remotive's API notice advises no
 * more than about four requests a day and warns that excessive polling gets blocked, and
 * Adzuna's free tier is roughly 1,000 calls a month. Every six hours sits inside both
 * budgets with room to spare, and the data genuinely does not change faster than that.
 *
 * <p>{@code @SchedulerLock} means only one replica runs a given cycle: two instances waking
 * on the same tick would double quota consumption against limits that are already the
 * binding constraint.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobIngestionScheduler {

    private final JobIngestionService ingestionService;

    /**
     * The three free boards, on the frequent cadence their own terms allow. Apify is
     * deliberately excluded here -- see {@link #scheduledApifyIngestion()} for why it
     * cannot share this schedule.
     */
    @Scheduled(cron = "${careerpilot.ingestion.cron:0 0 */6 * * *}")
    @SchedulerLock(name = "jobIngestion", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void scheduledIngestion() {
        log.info("Scheduled ingestion triggered (free boards)");
        ingestionService.ingestExcluding(java.util.Set.of(JobSource.APIFY));
    }

    /**
     * Apify's Dhaka scrape, on its own much longer cadence.
     *
     * <p>Measured cost: ~$0.24 per run against this service's configured query (2 search
     * terms, 25 results each, with LinkedIn description fetch), not the $0.003/job the
     * actor's store listing implies -- LinkedIn scraping carries real proxy and browser
     * compute cost on top of the per-result fee. Every 6 hours would be ~$7.20/month
     * against Apify's $5 free credit and lock the account mid-cycle. The default here
     * (every 3 days, ~10 runs/month, ~$2.40/month) leaves comfortable headroom, but this
     * is a product cadence-vs-cost tradeoff the team should tune deliberately after
     * watching real usage on the Apify dashboard -- this default is a safe starting point,
     * not a considered final answer.
     *
     * <p>An immediate on-demand refresh remains available to ADMIN via
     * {@code POST /api/v1/jobs/ingest}, which still runs every enabled provider including
     * Apify -- useful right before a demo.
     */
    @Scheduled(cron = "${careerpilot.ingestion.apify-cron:0 0 4 */3 * *}")
    @SchedulerLock(name = "apifyIngestion", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void scheduledApifyIngestion() {
        log.info("Scheduled Apify ingestion triggered");
        ingestionService.ingestOnly(java.util.Set.of(JobSource.APIFY));
    }

    /**
     * Seeds the corpus shortly after startup when the database is empty, so a fresh
     * {@code docker compose up} has searchable jobs without waiting up to six hours for the
     * first cron tick. Skipped when data already exists, so ordinary restarts do not spend
     * quota.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    @SchedulerLock(name = "jobIngestionStartup", lockAtMostFor = "PT30M")
    public void ingestOnStartupIfEmpty() {
        ingestionService.ingestIfCorpusEmpty();
    }
}
