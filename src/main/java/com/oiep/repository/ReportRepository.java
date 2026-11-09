package com.oiep.repository;

import com.oiep.domain.Report;
import com.oiep.domain.ReportForm;
import com.oiep.domain.ReportOptions;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * All report persistence. Every statement is a prepared statement with bound parameters;
 * free-text search terms are escaped so {@code %} and {@code _} are matched literally.
 */
@Repository
public class ReportRepository {

    private static final int MAX_LIMIT = 100;

    private static final String COLUMNS =
            "id, company, role, round, work_mode, location, outcome, heard_back, question_types, "
                    + "salary, vibe, story, helpful, created_at, status";
    private static final String SELECT = "SELECT " + COLUMNS + " FROM reports";
    private static final String PUBLISHED_ONLY = " WHERE status = 'published'";

    private static final String INSERT_SQL =
            "INSERT INTO reports (company, role, round, work_mode, location, outcome, heard_back, "
                    + "question_types, salary, vibe, story, helpful, created_at, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String ORDER_RECENT = " ORDER BY created_at DESC, id DESC";
    private static final String ORDER_HELPFUL = " ORDER BY helpful DESC, created_at DESC, id DESC";

    private static final RowMapper<Report> ROW_MAPPER = (resultSet, rowNumber) -> toReport(resultSet);

    private final JdbcTemplate jdbcTemplate;

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countAll() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reports", Long.class);
        return count == null ? 0L : count;
    }

    public Optional<Report> findById(long id) {
        List<Report> reports = jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
        return reports.isEmpty() ? Optional.empty() : Optional.of(reports.get(0));
    }

    public Optional<Report> findPublishedById(long id) {
        List<Report> reports = jdbcTemplate.query(
                SELECT + " WHERE id = ? AND status = 'published'", ROW_MAPPER, id);
        return reports.isEmpty() ? Optional.empty() : Optional.of(reports.get(0));
    }

    public boolean existsPublishedById(long id) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE id = ? AND status = 'published'", Integer.class, id);
        return count != null && count > 0;
    }

    /** Newest-first reports for the landing page. */
    public List<Report> findRecent(int limit) {
        int safeLimit = clampLimit(limit);
        return jdbcTemplate.query(SELECT + PUBLISHED_ONLY + ORDER_RECENT + " LIMIT ?", ROW_MAPPER, safeLimit);
    }

    /** Applies search + filters; caller paginates with limit/offset (both server-side clamped). */
    public List<Report> findMatching(String q, String round, String outcome, String workMode,
                                     String sort, int limit, int offset) {
        FilterSql filter = filters(q, round, outcome, workMode);
        List<Object> arguments = new ArrayList<>(filter.parameters());
        arguments.add(clampLimit(limit));
        arguments.add(Math.max(0, offset));
        String orderBy = ReportOptions.SORT_HELPFUL.equals(sort) ? ORDER_HELPFUL : ORDER_RECENT;
        return jdbcTemplate.query(SELECT + filter.whereClause() + orderBy + " LIMIT ? OFFSET ?",
                ROW_MAPPER, arguments.toArray());
    }

    public long countMatching(String q, String round, String outcome, String workMode) {
        FilterSql filter = filters(q, round, outcome, workMode);
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reports" + filter.whereClause(),
                Long.class, filter.parameters().toArray());
        return count == null ? 0L : count;
    }

    /** Persists a validated form. Returns the generated id. */
    public long insert(ReportForm form) {
        return insertRow(
                ReportOptions.trimToEmpty(form.getCompany()),
                ReportOptions.trimToEmpty(form.getRole()),
                ReportOptions.trimToEmpty(form.getRound()),
                ReportOptions.trimToEmpty(form.getWorkMode()),
                ReportOptions.trimToEmpty(form.getLocation()),
                ReportOptions.trimToEmpty(form.getOutcome()),
                ReportOptions.trimToEmpty(form.getHeardBack()),
                ReportOptions.sanitizeQuestionTypes(form.getQuestionTypes()),
                ReportOptions.trimToEmpty(form.getSalary()),
                ReportOptions.trimToEmpty(form.getVibe()),
                ReportOptions.trimToEmpty(form.getStory()),
                0,
                Instant.now().toString(),
                "published");
    }

    /**
     * Persists a fully populated report (used by the optional demo seeder so dates/helpful
     * counts can be varied). Still applies question-type sanitization.
     */
    public long insertSeed(Report seed) {
        String createdAt = ReportOptions.trimToEmpty(seed.getCreatedAt());
        if (createdAt.isEmpty()) {
            createdAt = Instant.now().toString();
        }
        String status = ReportOptions.trimToEmpty(seed.getStatus());
        return insertRow(
                ReportOptions.trimToEmpty(seed.getCompany()),
                ReportOptions.trimToEmpty(seed.getRole()),
                ReportOptions.trimToEmpty(seed.getRound()),
                ReportOptions.trimToEmpty(seed.getWorkMode()),
                ReportOptions.trimToEmpty(seed.getLocation()),
                ReportOptions.trimToEmpty(seed.getOutcome()),
                ReportOptions.trimToEmpty(seed.getHeardBack()),
                ReportOptions.sanitizeQuestionTypes(seed.getQuestionTypes()),
                ReportOptions.trimToEmpty(seed.getSalary()),
                ReportOptions.trimToEmpty(seed.getVibe()),
                ReportOptions.trimToEmpty(seed.getStory()),
                Math.max(0, seed.getHelpful()),
                createdAt,
                status.isEmpty() ? "published" : status);
    }

    /** Increments the useful counter. Returns {@code false} when the report does not exist. */
    public boolean incrementHelpful(long id) {
        int updated = jdbcTemplate.update(
                "UPDATE reports SET helpful = helpful + 1 WHERE id = ? AND status = 'published'", id);
        return updated > 0;
    }

    /* ------------------------------------------------------------------
       Admin / moderation queries. These intentionally see every status.
       ------------------------------------------------------------------ */

    public List<Report> findForAdmin(String q, String status, int limit, int offset) {
        AdminFilter filter = adminFilters(q, status);
        List<Object> arguments = new ArrayList<>(filter.parameters());
        arguments.add(clampLimit(limit));
        arguments.add(Math.max(0, offset));
        return jdbcTemplate.query(SELECT + filter.whereClause() + ORDER_RECENT + " LIMIT ? OFFSET ?",
                ROW_MAPPER, arguments.toArray());
    }

    public long countForAdmin(String q, String status) {
        AdminFilter filter = adminFilters(q, status);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports" + filter.whereClause(),
                Long.class, filter.parameters().toArray());
        return count == null ? 0L : count;
    }

    public long countByStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE status = ?", Long.class, status);
        return count == null ? 0L : count;
    }

    public long sumHelpful() {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(helpful), 0) FROM reports", Long.class);
        return total == null ? 0L : total;
    }

    public long countCreatedSince(String isoInstant) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reports WHERE created_at >= ?", Long.class, isoInstant);
        return count == null ? 0L : count;
    }

    /** Changes the moderation status. Returns {@code false} when the report does not exist. */
    public boolean updateStatus(long id, String status) {
        if (!"published".equals(status) && !"hidden".equals(status)) {
            throw new IllegalArgumentException("Unsupported report status: " + status);
        }
        return jdbcTemplate.update("UPDATE reports SET status = ? WHERE id = ?", status, id) > 0;
    }

    public boolean deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM reports WHERE id = ?", id) > 0;
    }

    private long insertRow(String company, String role, String round, String workMode, String location,
                           String outcome, String heardBack, List<String> questionTypes, String salary,
                           String vibe, String story, int helpful, String createdAt, String status) {
        String questionTypesValue = String.join(",", questionTypes);
        Long generatedId = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL,
                    Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, company);
                statement.setString(2, role);
                statement.setString(3, round);
                statement.setString(4, workMode);
                statement.setString(5, location);
                statement.setString(6, outcome);
                statement.setString(7, heardBack);
                statement.setString(8, questionTypesValue);
                statement.setString(9, salary);
                statement.setString(10, vibe);
                statement.setString(11, story);
                statement.setInt(12, helpful);
                statement.setString(13, createdAt);
                statement.setString(14, status);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        long id = keys.getLong(1);
                        if (id > 0L) {
                            return id;
                        }
                    }
                }
            }
            // Fallback for drivers that do not expose generated keys for this statement kind.
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
            throw new IllegalStateException("SQLite did not return an id for the inserted report");
        });
        if (generatedId == null || generatedId <= 0L) {
            throw new IllegalStateException("SQLite did not return an id for the inserted report");
        }
        return generatedId;
    }

    private static Report toReport(ResultSet resultSet) throws SQLException {
        Report report = new Report();
        report.setId(resultSet.getLong("id"));
        report.setCompany(resultSet.getString("company"));
        report.setRole(resultSet.getString("role"));
        report.setRound(resultSet.getString("round"));
        report.setWorkMode(resultSet.getString("work_mode"));
        report.setLocation(resultSet.getString("location"));
        report.setOutcome(resultSet.getString("outcome"));
        report.setHeardBack(resultSet.getString("heard_back"));
        report.setQuestionTypes(splitQuestionTypes(resultSet.getString("question_types")));
        report.setSalary(resultSet.getString("salary"));
        report.setVibe(resultSet.getString("vibe"));
        report.setStory(resultSet.getString("story"));
        report.setHelpful(resultSet.getInt("helpful"));
        report.setCreatedAt(resultSet.getString("created_at"));
        report.setStatus(resultSet.getString("status"));
        return report;
    }

    private static List<String> splitQuestionTypes(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList());
    }

    private static AdminFilter adminFilters(String q, String status) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> parameters = new ArrayList<>();

        if ("published".equals(status) || "hidden".equals(status)) {
            where.append(" AND status = ?");
            parameters.add(status);
        }

        String search = ReportOptions.normalizeSearch(q);
        if (!search.isEmpty()) {
            String pattern = "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%";
            where.append(" AND (LOWER(company) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(role) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(location) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(story) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(question_types) LIKE ? ESCAPE '\\')");
            for (int i = 0; i < 5; i++) {
                parameters.add(pattern);
            }
        }
        return new AdminFilter(where.toString(), parameters);
    }

    private static FilterSql filters(String q, String round, String outcome, String workMode) {
        StringBuilder where = new StringBuilder(PUBLISHED_ONLY);
        List<Object> parameters = new ArrayList<>();

        String search = ReportOptions.normalizeSearch(q);
        if (!search.isEmpty()) {
            String pattern = "%" + escapeLike(search.toLowerCase(Locale.ROOT)) + "%";
            where.append(" AND (LOWER(company) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(role) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(location) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(story) LIKE ? ESCAPE '\\'")
                    .append(" OR LOWER(question_types) LIKE ? ESCAPE '\\')");
            for (int i = 0; i < 5; i++) {
                parameters.add(pattern);
            }
        }
        String roundFilter = ReportOptions.normalizeFilter(round, ReportOptions.ROUNDS);
        if (!roundFilter.isEmpty()) {
            where.append(" AND round = ?");
            parameters.add(roundFilter);
        }
        String outcomeFilter = ReportOptions.normalizeFilter(outcome, ReportOptions.OUTCOMES);
        if (!outcomeFilter.isEmpty()) {
            where.append(" AND outcome = ?");
            parameters.add(outcomeFilter);
        }
        String workModeFilter = ReportOptions.normalizeFilter(workMode, ReportOptions.WORK_MODES);
        if (!workModeFilter.isEmpty()) {
            where.append(" AND work_mode = ?");
            parameters.add(workModeFilter);
        }
        return new FilterSql(where.toString(), parameters);
    }

    /** Escapes SQL LIKE wildcards so user input is matched literally. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private record FilterSql(String whereClause, List<Object> parameters) {
    }

    private record AdminFilter(String whereClause, List<Object> parameters) {
    }
}
