package com.oiep.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read model for a persisted interview report. Plain JavaBean (no-arg constructor + getters)
 * so JSP EL can read every property directly.
 */
public class Report {

    /** Roughly how many plain-text characters the browse cards get for the story. */
    private static final int EXCERPT_LENGTH = 170;

    private static final DateTimeFormatter CREATED_AT_LABEL_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yy").withZone(ZoneId.systemDefault());

    private long id;
    private String company = "";
    private String role = "";
    private String round = "";
    private String workMode = "";
    private String location = "";
    private String outcome = "";
    private String heardBack = "";
    private List<String> questionTypes = new ArrayList<>();
    private String salary = "";
    private String vibe = "";
    private String story = "";
    private int helpful;
    /** ISO-8601 instant, e.g. {@code 2026-11-09T14:43:12Z}. */
    private String createdAt = "";
    private String status = "published";

    public Report() {
        // JavaBean constructor
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company == null ? "" : company;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role == null ? "" : role;
    }

    public String getRound() {
        return round;
    }

    public void setRound(String round) {
        this.round = round == null ? "" : round;
    }

    public String getWorkMode() {
        return workMode;
    }

    public void setWorkMode(String workMode) {
        this.workMode = workMode == null ? "" : workMode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location == null ? "" : location;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome == null ? "" : outcome;
    }

    public String getHeardBack() {
        return heardBack;
    }

    public void setHeardBack(String heardBack) {
        this.heardBack = heardBack == null ? "" : heardBack;
    }

    public List<String> getQuestionTypes() {
        if (questionTypes == null) {
            questionTypes = new ArrayList<>();
        }
        return questionTypes;
    }

    public void setQuestionTypes(List<String> questionTypes) {
        this.questionTypes = questionTypes == null ? new ArrayList<>() : questionTypes;
    }

    public String getSalary() {
        return salary;
    }

    public void setSalary(String salary) {
        this.salary = salary == null ? "" : salary;
    }

    public String getVibe() {
        return vibe;
    }

    public void setVibe(String vibe) {
        this.vibe = vibe == null ? "" : vibe;
    }

    public String getStory() {
        return story;
    }

    public void setStory(String story) {
        this.story = story == null ? "" : story;
    }

    public int getHelpful() {
        return helpful;
    }

    public void setHelpful(int helpful) {
        this.helpful = helpful;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt == null ? "" : createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status == null ? "" : status;
    }

    /** Display label for the post date, e.g. {@code 09.11.26} (server time zone). */
    public String getCreatedAtLabel() {
        if (createdAt.isEmpty()) {
            return "";
        }
        try {
            return CREATED_AT_LABEL_FORMAT.format(Instant.parse(createdAt));
        } catch (DateTimeParseException ex) {
            try {
                return CREATED_AT_LABEL_FORMAT.format(
                        LocalDateTime.parse(createdAt).atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                return "";
            }
        }
    }

    /**
     * Plain-text preview of the story: whitespace-normalized, at most ~170 characters, cut at
     * a word boundary. The view remains responsible for HTML escaping.
     */
    public String getExcerpt() {
        if (story.isEmpty()) {
            return "";
        }
        String text = story.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (text.length() <= EXCERPT_LENGTH) {
            return text;
        }
        int end = text.lastIndexOf(' ', EXCERPT_LENGTH);
        if (end < EXCERPT_LENGTH / 3) {
            end = EXCERPT_LENGTH;
        }
        return text.substring(0, end).trim();
    }

    @Override
    public String toString() {
        return "Report{id=" + id + ", company='" + company + "', round='" + round + "'}";
    }
}
