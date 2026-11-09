package com.oiep.service;

import com.oiep.domain.Report;
import com.oiep.repository.ReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional demo content, enabled only with {@code --oiep.demo-data=true}. Runs once at boot
 * and only when the reports table is empty, so a normal boot shows the empty state.
 *
 * <p>All companies below are fictional and the notes are plainly labelled as sample content.
 * Nothing here describes a real employer or a real person.</p>
 */
@Component
@ConditionalOnProperty(prefix = "oiep", name = "demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ReportRepository reportRepository;

    public DemoDataSeeder(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        long existing = reportRepository.countAll();
        if (existing > 0L) {
            log.info("Demo data not seeded: reports table already contains {} row(s)", existing);
            return;
        }
        List<Report> seeds = buildSeedReports();
        for (Report seed : seeds) {
            reportRepository.insertSeed(seed);
        }
        log.info("Seeded {} fictional demo reports (oiep.demo-data=true)", seeds.size());
    }

    private static List<Report> buildSeedReports() {
        Instant now = Instant.now();
        List<Report> seeds = new ArrayList<>();

        seeds.add(seed(
                "Aster & Finch Technologies",
                "Senior Frontend Engineer",
                "technical",
                "remote",
                "Remote (APAC)",
                "offer",
                "week",
                List.of("algorithm", "behavioral", "culture"),
                "HKD 55k / month",
                "warm",
                "Three rounds in one afternoon: a pair-programming exercise on an accessible component, "
                        + "a systems chat about state management, and a conversation with the design lead. "
                        + "Feedback arrived in six days, with concrete reasons for the decision. "
                        + "The questions were practical rather than trivia, and I was allowed to use my own editor.",
                14,
                now.minus(Duration.ofDays(2))));

        seeds.add(seed(
                "Banyan Stack",
                "Backend Engineer",
                "systemdesign",
                "hybrid",
                "Kowloon Bay, HK",
                "rejection",
                "weeks",
                List.of("systemdesign", "domain", "behavioral"),
                "not discussed",
                "neutral",
                "Two system design rounds plus a short behavioural interview. The rejection email was "
                        + "specific about the gap: they wanted more hands-on experience with event-driven "
                        + "data pipelines. I would apply again; the process was fair and the interviewers "
                        + "explained their reasoning when I asked for feedback.",
                11,
                now.minus(Duration.ofDays(4))));

        seeds.add(seed(
                "Cobalt Meridian",
                "Data Analyst",
                "takehome",
                "onsite",
                "Central, HK",
                "ghosted",
                "never",
                List.of("takehome", "domain"),
                "not mentioned",
                "cold",
                "The take-home dashboard took about six hours and was reviewed in a 20 minute call. "
                        + "After that I heard nothing for a month, and the recruiter stopped replying after "
                        + "one follow-up. Ask about the review timeline and whether the task is paid before "
                        + "you start, especially for longer exercises.",
                19,
                now.minus(Duration.ofDays(7))));

        seeds.add(seed(
                "Driftwood Systems",
                "Product Manager",
                "behavioral",
                "hybrid",
                "Sha Tin, HK",
                "waiting",
                "days",
                List.of("behavioral", "culture", "casestudy"),
                "HKD 50k / month",
                "warm",
                "The first interview was a structured behavioural round, followed by a case study about "
                        + "prioritising a small roadmap. They shared the interview rubric in advance, which "
                        + "made the exercise much fairer. Still waiting on the final decision after two "
                        + "weeks, but communication has been clear throughout.",
                7,
                now.minus(Duration.ofDays(12))));

        seeds.add(seed(
                "Evergreen Loom",
                "QA Engineer",
                "phone",
                "remote",
                "Remote",
                "withdrew",
                "days",
                List.of("algorithm", "behavioral"),
                "HKD 32k / month",
                "rushed",
                "Recruiter screen was a ten minute call, but the follow-up technical interview felt "
                        + "rushed: mostly textbook definitions and the interviewer seemed distracted. "
                        + "The role description and the questions did not line up, so I withdrew after "
                        + "the second round. The recruiter was polite about it.",
                5,
                now.minus(Duration.ofDays(18))));

        seeds.add(seed(
                "Fernhill Robotics",
                "Embedded Software Engineer",
                "final",
                "onsite",
                "Tai Po, HK",
                "offer",
                "week",
                List.of("algorithm", "domain", "casestudy"),
                "HKD 48k / month",
                "warm",
                "Final round was on-site: a debugging exercise on real hardware, an architecture "
                        + "discussion, and a chat with the team. Everyone was candid about the workload "
                        + "and the on-call rotation. The offer came a week later and was close to my "
                        + "expectation, with the details explained in plain language.",
                9,
                now.minus(Duration.ofDays(25))));

        return seeds;
    }

    private static Report seed(String company, String role, String round, String workMode, String location,
                               String outcome, String heardBack, List<String> questionTypes, String salary,
                               String vibe, String story, int helpful, Instant createdAt) {
        Report report = new Report();
        report.setCompany(company);
        report.setRole(role);
        report.setRound(round);
        report.setWorkMode(workMode);
        report.setLocation(location);
        report.setOutcome(outcome);
        report.setHeardBack(heardBack);
        report.setQuestionTypes(new ArrayList<>(questionTypes));
        report.setSalary(salary);
        report.setVibe(vibe);
        report.setStory(story);
        report.setHelpful(helpful);
        report.setCreatedAt(createdAt.toString());
        report.setStatus("published");
        return report;
    }
}
