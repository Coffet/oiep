package com.oiep.web;

import com.oiep.domain.Report;
import com.oiep.repository.ReportRepository;
import com.oiep.service.HelpfulSessionTracker;
import com.oiep.service.HelpfulVoteRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single report page and the "this helped" action. No login in this phase: a helpful vote is
 * tracked per HTTP session and additionally throttled per client address.
 */
@Controller
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportRepository reportRepository;
    private final HelpfulVoteRateLimiter helpfulRateLimiter;
    private final ClientAddressResolver clientAddressResolver;
    private final SameOriginPostValidator sameOriginPostValidator;

    public ReportController(ReportRepository reportRepository,
                            HelpfulVoteRateLimiter helpfulRateLimiter,
                            ClientAddressResolver clientAddressResolver,
                            SameOriginPostValidator sameOriginPostValidator) {
        this.reportRepository = reportRepository;
        this.helpfulRateLimiter = helpfulRateLimiter;
        this.clientAddressResolver = clientAddressResolver;
        this.sameOriginPostValidator = sameOriginPostValidator;
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
        // Useful votes are anonymous, so a token cannot bind them to a session; reject
        // cross-site browser POSTs instead so a third-party page cannot stuff the counter.
        if (!sameOriginPostValidator.isSameOrigin(request)) {
            // Log only the resolved client address: Origin/Referer are attacker-controlled.
            log.warn("Rejected cross-origin helpful vote reportId={} client={}",
                    id, clientAddressResolver.resolve(request));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cross-origin helpful vote rejected");
        }
        // Throttle per client: without it, a client that discards its cookie can inflate the
        // counter (and allocate sessions) without limit.
        if (!helpfulRateLimiter.tryAcquire(clientAddressResolver.resolve(request))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many useful votes");
        }
        HttpSession session = request.getSession(true);
        if (HelpfulSessionTracker.markHelpful(session, id)) {
            reportRepository.incrementHelpful(id);
        }
        return "redirect:/reports/" + id + "#useful";
    }
}
