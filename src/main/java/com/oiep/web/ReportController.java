package com.oiep.web;

import com.oiep.domain.Report;
import com.oiep.repository.ReportRepository;
import com.oiep.service.HelpfulSessionTracker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Single report page and the "this helped" action. No login in this phase: a helpful vote is
 * tracked per HTTP session.
 */
@Controller
public class ReportController {

    private final ReportRepository reportRepository;

    public ReportController(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @GetMapping("/reports/{id:\\d+}")
    public String report(@PathVariable("id") long id, Model model, HttpServletRequest request) {
        Report report = reportRepository.findPublishedById(id)
                .orElseThrow(() -> new ReportNotFoundException(id));
        HttpSession session = request.getSession(false);
        model.addAttribute("report", report);
        model.addAttribute("alreadyHelpful", HelpfulSessionTracker.isHelpful(session, id));
        model.addAttribute("currentUrl", "/reports/" + report.getId());
        model.addAttribute("activeNav", "browse");
        return "report";
    }

    @PostMapping("/reports/{id:\\d+}/helpful")
    public String markHelpful(@PathVariable("id") long id, HttpServletRequest request) {
        // Check the report before touching the session: an anonymous POST to a missing id
        // must not create a JSESSIONID (cheap memory-exhaustion vector).
        if (!reportRepository.existsPublishedById(id)) {
            throw new ReportNotFoundException(id);
        }
        HttpSession session = request.getSession(true);
        if (HelpfulSessionTracker.markHelpful(session, id)) {
            reportRepository.incrementHelpful(id);
        }
        return "redirect:/reports/" + id + "#useful";
    }
}
