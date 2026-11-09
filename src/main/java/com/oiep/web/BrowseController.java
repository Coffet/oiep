package com.oiep.web;

import com.oiep.domain.Report;
import com.oiep.domain.ReportOptions;
import com.oiep.repository.ReportRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

/**
 * Public report browse/search page. Unknown filter values are normalized to "": they never
 * reach SQL and are never echoed back to the page.
 */
@Controller
public class BrowseController {

    static final int PAGE_SIZE = 10;

    private final ReportRepository reportRepository;

    public BrowseController(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    @GetMapping("/browse")
    public String browse(@RequestParam(name = "q", required = false) String q,
                         @RequestParam(name = "round", required = false) String round,
                         @RequestParam(name = "outcome", required = false) String outcome,
                         @RequestParam(name = "workMode", required = false) String workMode,
                         @RequestParam(name = "sort", required = false) String sort,
                         @RequestParam(name = "page", required = false) String page,
                         Model model,
                         HttpServletRequest request) {
        String search = ReportOptions.normalizeSearch(q);
        String roundFilter = ReportOptions.normalizeFilter(round, ReportOptions.ROUNDS);
        String outcomeFilter = ReportOptions.normalizeFilter(outcome, ReportOptions.OUTCOMES);
        String workModeFilter = ReportOptions.normalizeFilter(workMode, ReportOptions.WORK_MODES);
        String sortValue = ReportOptions.normalizeSort(sort);

        long total = reportRepository.countMatching(search, roundFilter, outcomeFilter, workModeFilter);
        int totalPages = totalPages(total);
        int currentPage = Math.min(Math.max(1, parsePage(page)), totalPages);
        int offset = (currentPage - 1) * PAGE_SIZE;
        List<Report> results = reportRepository.findMatching(
                search, roundFilter, outcomeFilter, workModeFilter, sortValue, PAGE_SIZE, offset);

        model.addAttribute("results", results);
        model.addAttribute("total", total);
        model.addAttribute("page", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("q", search);
        model.addAttribute("round", roundFilter);
        model.addAttribute("outcome", outcomeFilter);
        model.addAttribute("workMode", workModeFilter);
        model.addAttribute("sort", sortValue);
        model.addAttribute("currentUrl",
                browseUrl(search, roundFilter, outcomeFilter, workModeFilter, sortValue, currentPage));
        // "Clear filters" target used by browse.jsp; includes the context path so the link
        // works when the app is deployed under a non-root path.
        model.addAttribute("clearUrl", request.getContextPath() + "/browse");
        model.addAttribute("activeNav", "browse");
        return "browse";
    }

    static int totalPages(long total) {
        if (total <= 0L) {
            // An empty result set still renders as page 1 of 1.
            return 1;
        }
        long pages = (total + PAGE_SIZE - 1L) / PAGE_SIZE;
        return (int) Math.min(Integer.MAX_VALUE, pages);
    }

    static int parsePage(String rawPage) {
        if (rawPage == null || rawPage.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(rawPage.trim());
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    /**
     * Canonical path+query for the current browse state, used as the locale switch
     * {@code returnTo}. Values are percent-encoded; {@code c:param}/{@code c:url} in the JSP
     * encodes the whole value again for the link itself.
     */
    static String browseUrl(String q, String round, String outcome, String workMode,
                            String sort, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/browse");
        if (!q.isEmpty()) {
            builder.queryParam("q", q);
        }
        if (!round.isEmpty()) {
            builder.queryParam("round", round);
        }
        if (!outcome.isEmpty()) {
            builder.queryParam("outcome", outcome);
        }
        if (!workMode.isEmpty()) {
            builder.queryParam("workMode", workMode);
        }
        if (!ReportOptions.SORT_RECENT.equals(sort)) {
            builder.queryParam("sort", sort);
        }
        if (page > 1) {
            builder.queryParam("page", page);
        }
        return builder.build().encode().toUriString();
    }
}
