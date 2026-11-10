package com.oiep.companion.read;

/**
 * One joined row of the component event table and (optionally) the current report row.
 *
 * <p>A row exists for every report event. The report side is {@code null} when the report has
 * been deleted (the event table intentionally outlives the report so the deletion can be
 * propagated as a tombstone), and may be hidden or otherwise unpublished if the current status
 * disagrees with the last recorded event.</p>
 *
 * @param reportId      OIEP report id (also the event primary key)
 * @param eventMs       epoch milliseconds of the latest component event for this report
 * @param eventDeleted  true when the latest event removed the report from the public corpus
 * @param reportPresent true when the reports row still exists
 * @param company       report company (full items only)
 * @param role          report role (mapped to {@code role_title})
 * @param round         report round (mapped to a round type)
 * @param workMode      report work mode (mapped to an enum)
 * @param location      report location (omitted when blank)
 * @param outcome       report outcome (mapped to an enum)
 * @param questionTypes comma-separated question-type tags (mapped to round topics)
 * @param vibe          interviewer vibe (only used by the optional vibe-proxy difficulty mode)
 * @param story         free-text account (mapped to {@code overall_experience})
 * @param createdAt     ISO-8601 submission timestamp
 * @param status        current moderation status ({@code published} / {@code hidden})
 */
public record ReadApiRow(
        long reportId,
        long eventMs,
        boolean eventDeleted,
        boolean reportPresent,
        String company,
        String role,
        String round,
        String workMode,
        String location,
        String outcome,
        String questionTypes,
        String vibe,
        String story,
        String createdAt,
        String status) {
}
