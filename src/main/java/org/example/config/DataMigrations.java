package org.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time schema clean-ups that Hibernate's ddl-auto=update cannot perform
 * automatically (dropping constraints that were removed from entity classes).
 *
 * Each migration is idempotent — safe to run on every startup.
 */
@Component
public class DataMigrations implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrations.class);

    private final JdbcTemplate jdbc;

    public DataMigrations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        dropRoomsNameUniqueConstraint();
        dropLegacyRuleSensorColumns();
    }

    /**
     * The sensor-trigger columns (TRIGGER_ROOM, TRIGGER_METRIC, TRIGGER_OPERATOR,
     * TRIGGER_THRESHOLD) were removed from the Rule entity when the automation system
     * was simplified to time-based-only rules.  Hibernate's ddl-auto=update adds
     * columns but never drops them, so we do it here instead.
     * Each DROP COLUMN is idempotent — silently skipped when the column is already gone.
     */
    private void dropLegacyRuleSensorColumns() {
        for (String col : new String[]{"TRIGGER_ROOM", "TRIGGER_METRIC", "TRIGGER_OPERATOR", "TRIGGER_THRESHOLD"}) {
            try {
                jdbc.execute("ALTER TABLE AUTOMATION_RULES DROP COLUMN IF EXISTS " + col);
                log.info("Dropped legacy column AUTOMATION_RULES.{}", col);
            } catch (Exception e) {
                log.debug("Could not drop AUTOMATION_RULES.{} (may already be absent): {}", col, e.getMessage());
            }
        }
    }

    /**
     * Room.name had a single-column UNIQUE constraint that was replaced by
     * per-floor uniqueness (checked at the application level).  The old DB
     * constraint must be dropped so the same room name can exist on multiple floors.
     */
    private void dropRoomsNameUniqueConstraint() {
        try {
            // Step 1: collect all UNIQUE constraint names on the ROOMS table.
            // Two separate queries (no JOIN) avoid H2 version differences in
            // INFORMATION_SCHEMA column naming / nullability.
            var candidates = jdbc.queryForList(
                    "SELECT CONSTRAINT_NAME " +
                    "FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
                    "WHERE UPPER(TABLE_NAME) = 'ROOMS' AND CONSTRAINT_TYPE = 'UNIQUE'",
                    String.class);

            for (String cn : candidates) {
                // Step 2: list the columns covered by this constraint.
                var cols = jdbc.queryForList(
                        "SELECT UPPER(COLUMN_NAME) " +
                        "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                        "WHERE UPPER(TABLE_NAME) = 'ROOMS' AND CONSTRAINT_NAME = ?",
                        String.class, cn);

                if (cols.size() == 1 && "NAME".equals(cols.get(0))) {
                    // Step 3: drop it — try without quotes, then with quotes.
                    boolean dropped = false;
                    for (String ddl : new String[]{
                            "ALTER TABLE ROOMS DROP CONSTRAINT " + cn,
                            "ALTER TABLE ROOMS DROP CONSTRAINT \"" + cn + "\""
                    }) {
                        try { jdbc.execute(ddl); dropped = true; break; }
                        catch (Exception ignored) { /* try next form */ }
                    }
                    if (dropped) log.info("Dropped legacy UNIQUE constraint on ROOMS.NAME: {}", cn);
                    else         log.warn("Could not drop constraint {} — remove it manually if duplicate room names fail", cn);
                }
            }
        } catch (Exception e) {
            log.debug("Could not query ROOMS constraints (may already be clean): {}", e.getMessage());
        }
    }
}
