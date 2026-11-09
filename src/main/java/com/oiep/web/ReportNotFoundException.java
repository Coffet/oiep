package com.oiep.web;

/**
 * Thrown when a report id does not exist (or is not published). Mapped to the styled 404
 * error view by {@link GlobalModelAdvice}.
 */
public class ReportNotFoundException extends RuntimeException {

    private final long reportId;

    public ReportNotFoundException(long reportId) {
        super("Report not found: " + reportId);
        this.reportId = reportId;
    }

    public long getReportId() {
        return reportId;
    }
}
