package com.oiep.companion.read;

import com.oiep.companion.CompanionConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Prepares the read API's database objects at startup, and only when the read API is enabled.
 *
 * <p>Two idempotent steps:</p>
 * <ol>
 *   <li>create the component-owned {@code companion_report_events} table, its
 *       {@code (event_ms, report_id)} ordering index and its triggers on {@code reports}
 *       (core code is untouched; the triggers are what make moderation changes and deletes
 *       visible to the companion);</li>
 *   <li>backfill one event for every report that predates the component, so a companion can
 *       seed its corpus with a single full sync.</li>
 * </ol>
 *
 * <p>With the component disabled no bean of this class exists and consequently no trigger is
 * ever created: the application behaves exactly as it did before the component was added.
 * Removing the component code requires dropping the triggers first
 * ({@code scripts/companion-uninstall.sql}), otherwise SQLite would reject every delete on
 * {@code reports}.</p>
 */
@Component
@Conditional(CompanionConditions.ReadApi.class)
public class ReadApiSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReadApiSchemaInitializer.class);

    private final ReadApiEventStore eventStore;

    public ReadApiSchemaInitializer(ReadApiEventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        eventStore.ensureEventSchema();
        int backfilled = eventStore.backfillExistingReports();
        log.info("AI companion read API event store ready: triggers installed, "
                + "backfilled {} existing report event(s)", backfilled);
    }
}
