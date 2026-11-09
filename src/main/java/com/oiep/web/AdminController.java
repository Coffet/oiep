package com.oiep.web;

import com.oiep.domain.Report;
import com.oiep.domain.ReportOptions;
import com.oiep.repository.ReportRepository;
import com.oiep.service.AdminAuth;
import com.oiep.service.AdminLoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Back-office moderation. Credentials come from the deployment environment; all actions are
 * POST + CSRF protected, and the public site only ever sees published reports.
 */
@Controller
public class AdminController {

    private static final int PAGE_SIZE = 20;

    private final ReportRepository reportRepository;
    private final AdminAuth adminAuth;
    private final AdminLoginRateLimiter loginRateLimiter;

    public AdminController(ReportRepository reportRepository,
                           AdminAuth adminAuth,
                           AdminLoginRateLimiter loginRateLimiter) {
        this.reportRepository = reportRepository;
        this.adminAuth = adminAuth;
        this.loginRateLimiter = loginRateLimiter;
    }

    @GetMapping("/admin/login")
    public String loginForm(@RequestParam(name = "loggedOut", required = false) String loggedOut,
                            HttpServletRequest request,
                            HttpServletResponse response,
                            Model model) {
        if (adminAuth.isAuthenticated(request.getSession(false))) {
            return "redirect:/admin";
        }
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");
        HttpSession session = request.getSession(true);
        prepareLoginModel(model, session);
        model.addAttribute("loggedOut", StringUtils.hasText(loggedOut));
        return "admin/login";
    }

    @PostMapping("/admin/login")
    public String login(@RequestParam(name = "username", defaultValue = "") String username,
                        @RequestParam(name = "password", defaultValue = "") String password,
                        @RequestParam(name = "_csrf", required = false) String csrf,
                        HttpServletRequest request,
                        HttpServletResponse response,
                        Model model) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");

        HttpSession session = request.getSession(false);
        if (!adminAuth.csrfMatches(session, csrf)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            prepareLoginModel(model, request.getSession(true));
            model.addAttribute("loginError", "admin.login.error.csrf");
            return "admin/login";
        }

        if (!adminAuth.isConfigured()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            prepareLoginModel(model, session);
            model.addAttribute("loginError", "admin.login.error.unavailable");
            return "admin/login";
        }

        if (!loginRateLimiter.tryAcquire("admin:" + clientKey(request))) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            prepareLoginModel(model, session);
            model.addAttribute("loginError", "admin.login.error.rateLimited");
            return "admin/login";
        }

        if (!adminAuth.authenticate(username, password)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            prepareLoginModel(model, session);
            model.addAttribute("loginError", "admin.login.error.invalid");
            return "admin/login";
        }

        adminAuth.signIn(request);
        return "redirect:/admin";
    }

    @PostMapping("/admin/logout")
    public String logout(@RequestParam(name = "_csrf", required = false) String csrf,
                         HttpServletRequest request) {
        requireCsrf(request, csrf);
        adminAuth.signOut(request);
        return "redirect:/admin/login?loggedOut=1";
    }

    @GetMapping("/admin")
    public String dashboard(@RequestParam(name = "q", required = false) String q,
                            @RequestParam(name = "status", required = false) String status,
                            @RequestParam(name = "page", required = false) String page,
                            HttpServletRequest request,
                            Model model) {
        String search = ReportOptions.normalizeSearch(q);
        String statusFilter = normalizeStatus(status);
        long total = reportRepository.countForAdmin(search, statusFilter);
        int totalPages = totalPages(total);
        int currentPage = Math.min(Math.max(1, parsePage(page)), totalPages);
        List<Report> results = reportRepository.findForAdmin(
                search, statusFilter, PAGE_SIZE, (currentPage - 1) * PAGE_SIZE);
        String returnUrl = adminUrl(search, statusFilter, currentPage);

        prepareAdminModel(request, model, returnUrl);
        model.addAttribute("reports", results);
        model.addAttribute("total", total);
        model.addAttribute("page", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("q", search);
        model.addAttribute("status", statusFilter);
        model.addAttribute("totalNotes", reportRepository.countAll());
        model.addAttribute("publishedNotes", reportRepository.countByStatus("published"));
        model.addAttribute("hiddenNotes", reportRepository.countByStatus("hidden"));
        model.addAttribute("helpfulTotal", reportRepository.sumHelpful());
        model.addAttribute("recentNotes", reportRepository.countCreatedSince(
                Instant.now().minus(7, ChronoUnit.DAYS).toString()));
        return "admin/dashboard";
    }

    @GetMapping("/admin/reports/{id:\\d+}")
    public String report(@PathVariable("id") long id,
                         HttpServletRequest request,
                         Model model) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ReportNotFoundException(id));
        String returnUrl = "/admin/reports/" + report.getId();
        prepareAdminModel(request, model, returnUrl);
        model.addAttribute("report", report);
        return "admin/report";
    }

    @PostMapping("/admin/reports/{id:\\d+}/hide")
    public String hide(@PathVariable("id") long id,
                       @RequestParam(name = "_csrf", required = false) String csrf,
                       @RequestParam(name = "returnTo", required = false) String returnTo,
                       HttpServletRequest request,
                       RedirectAttributes redirectAttributes) {
        requireCsrf(request, csrf);
        if (!reportRepository.updateStatus(id, "hidden")) {
            throw new ReportNotFoundException(id);
        }
        redirectAttributes.addFlashAttribute("adminNotice", "admin.notice.hidden");
        return "redirect:" + safeAdminReturn(returnTo);
    }

    @PostMapping("/admin/reports/{id:\\d+}/restore")
    public String restore(@PathVariable("id") long id,
                          @RequestParam(name = "_csrf", required = false) String csrf,
                          @RequestParam(name = "returnTo", required = false) String returnTo,
                          HttpServletRequest request,
                          RedirectAttributes redirectAttributes) {
        requireCsrf(request, csrf);
        if (!reportRepository.updateStatus(id, "published")) {
            throw new ReportNotFoundException(id);
        }
        redirectAttributes.addFlashAttribute("adminNotice", "admin.notice.restored");
        return "redirect:" + safeAdminReturn(returnTo);
    }

    @GetMapping("/admin/reports/{id:\\d+}/delete")
    public String deleteConfirmation(@PathVariable("id") long id,
                                     HttpServletRequest request,
                                     Model model) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ReportNotFoundException(id));
        prepareAdminModel(request, model, "/admin/reports/" + report.getId());
        model.addAttribute("report", report);
        return "admin/delete";
    }

    @PostMapping("/admin/reports/{id:\\d+}/delete")
    public String delete(@PathVariable("id") long id,
                         @RequestParam(name = "_csrf", required = false) String csrf,
                         HttpServletRequest request,
                         RedirectAttributes redirectAttributes) {
        requireCsrf(request, csrf);
        if (!reportRepository.deleteById(id)) {
            throw new ReportNotFoundException(id);
        }
        redirectAttributes.addFlashAttribute("adminNotice", "admin.notice.deleted");
        return "redirect:/admin";
    }

    private void prepareLoginModel(Model model, HttpSession session) {
        model.addAttribute("adminAuthenticated", false);
        model.addAttribute("adminConfigured", adminAuth.isConfigured());
        model.addAttribute("adminCsrfToken", adminAuth.csrfToken(session));
        model.addAttribute("currentUrl", "/admin/login");
        model.addAttribute("activeNav", "admin");
        model.addAttribute("loggedOut", false);
    }

    private void prepareAdminModel(HttpServletRequest request, Model model, String returnUrl) {
        HttpSession session = request.getSession(false);
        model.addAttribute("adminAuthenticated", true);
        model.addAttribute("adminCsrfToken", adminAuth.csrfToken(session));
        model.addAttribute("adminReturnUrl", returnUrl);
        model.addAttribute("currentUrl", returnUrl);
        model.addAttribute("activeNav", "admin");
    }

    private void requireCsrf(HttpServletRequest request, String candidate) {
        if (!adminAuth.csrfMatches(request.getSession(false), candidate)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid CSRF token");
        }
    }

    private static int totalPages(long total) {
        if (total <= 0L) {
            return 1;
        }
        return (int) Math.min(Integer.MAX_VALUE, (total + PAGE_SIZE - 1L) / PAGE_SIZE);
    }

    private static int parsePage(String rawPage) {
        if (!StringUtils.hasText(rawPage)) {
            return 1;
        }
        try {
            return Integer.parseInt(rawPage.trim());
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static String normalizeStatus(String rawStatus) {
        if ("published".equals(rawStatus) || "hidden".equals(rawStatus)) {
            return rawStatus;
        }
        return "";
    }

    private static String adminUrl(String q, String status, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/admin");
        if (!q.isEmpty()) {
            builder.queryParam("q", q);
        }
        if (!status.isEmpty()) {
            builder.queryParam("status", status);
        }
        if (page > 1) {
            builder.queryParam("page", page);
        }
        return builder.build().encode().toUriString();
    }

    private static String safeAdminReturn(String rawReturnTo) {
        if (rawReturnTo == null) {
            return "/admin";
        }
        String value = rawReturnTo.trim();
        boolean adminPath = value.equals("/admin")
                || value.startsWith("/admin/")
                || value.startsWith("/admin?");
        if (!adminPath || value.startsWith("//") || value.contains("\\")
                || value.contains("\r") || value.contains("\n")) {
            return "/admin";
        }
        return value;
    }

    private static String clientKey(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddress) ? remoteAddress : "unknown";
    }
}
