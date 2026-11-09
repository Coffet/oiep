package com.oiep.domain;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * Backing bean for the "share an interview experience" form.
 *
 * <p>Empty optional values are represented by {@code ""} rather than {@code null} so JSP/EL
 * rendering is always safe. Message codes resolve through the application MessageSource
 * (Spring Boot 3.x wires a MessageSource-backed Bean Validation interpolator).</p>
 */
public class ReportForm {

    @NotBlank(message = "{validation.company.required}")
    @Size(max = 80, message = "{validation.company.size}")
    private String company = "";

    @NotBlank(message = "{validation.role.required}")
    @Size(max = 100, message = "{validation.role.size}")
    private String role = "";

    @NotBlank(message = "{validation.round.required}")
    @Pattern(regexp = ReportOptions.ROUND_ALLOWED_REGEX, message = "{validation.round.invalid}")
    private String round = "";

    @Pattern(regexp = ReportOptions.WORK_MODE_ALLOWED_REGEX, message = "{validation.workmode.invalid}")
    private String workMode = "";

    @Size(max = 80, message = "{validation.location.size}")
    private String location = "";

    @NotBlank(message = "{validation.outcome.required}")
    @Pattern(regexp = ReportOptions.OUTCOME_ALLOWED_REGEX, message = "{validation.outcome.invalid}")
    private String outcome = "";

    @Pattern(regexp = ReportOptions.HEARD_BACK_ALLOWED_REGEX, message = "{validation.heardback.invalid}")
    private String heardBack = "";

    @Size(max = ReportOptions.MAX_QUESTION_TYPES, message = "{validation.questions.size}")
    private List<@Pattern(regexp = ReportOptions.QUESTION_TYPE_ALLOWED_REGEX,
            message = "{validation.questions.invalid}") String> questionTypes = new ArrayList<>();

    @Size(max = 80, message = "{validation.salary.size}")
    private String salary = "";

    @Pattern(regexp = ReportOptions.VIBE_ALLOWED_REGEX, message = "{validation.vibe.invalid}")
    private String vibe = "";

    @NotBlank(message = "{validation.story.required}")
    @Size(min = 15, max = 4000, message = "{validation.story.size}")
    private String story = "";

    @AssertTrue(message = "{validation.confirm.required}")
    private boolean confirm;

    /**
     * Honeypot field rendered hidden in the form. Real users never fill it; bots usually do.
     * Deliberately has no user-facing constraint.
     */
    private String website = "";

    public ReportForm() {
        // JavaBean constructor (JSP EL / Spring form binding)
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRound() {
        return round;
    }

    public void setRound(String round) {
        this.round = round;
    }

    public String getWorkMode() {
        return workMode;
    }

    public void setWorkMode(String workMode) {
        this.workMode = workMode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public String getHeardBack() {
        return heardBack;
    }

    public void setHeardBack(String heardBack) {
        this.heardBack = heardBack;
    }

    public List<String> getQuestionTypes() {
        if (questionTypes == null) {
            questionTypes = new ArrayList<>();
        }
        return questionTypes;
    }

    public void setQuestionTypes(List<String> questionTypes) {
        this.questionTypes = questionTypes;
    }

    public String getSalary() {
        return salary;
    }

    public void setSalary(String salary) {
        this.salary = salary;
    }

    public String getVibe() {
        return vibe;
    }

    public void setVibe(String vibe) {
        this.vibe = vibe;
    }

    public String getStory() {
        return story;
    }

    public void setStory(String story) {
        this.story = story;
    }

    public boolean isConfirm() {
        return confirm;
    }

    public void setConfirm(boolean confirm) {
        this.confirm = confirm;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }
}
