package com.oiep.web;

import com.oiep.domain.Report;
import com.oiep.domain.ReportOptions;
import com.oiep.repository.ReportRepository;
import com.oiep.service.AdminAuth;
import com.oiep.service.AdminLoginPageRateLimiter;
import com.oiep.service.AdminLoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.regex.Pattern;

/**
 * Back-office moderation. Credentials come from the deployment environment; all actions are
 * POST + CSRF protected, and the public site only ever sees published reports.
 *
 * <p>Security-relevant events (failed sign-ins, throttling, moderation actions) are written
 * to the application log with the resolved client address so an operator has an audit trail
 * without needing a separate database table.</p>
 */
@Controller
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private static final int PAGE_SIZE = 20;

    /** Same ceiling the public locale switcher applies to its {@code returnTo} parameter. */
    private static final int MAX_RETURN_TO_LENGTH = 2000;

    /**
     * CSRF tokens belong in the request body (or an explicit header), never in a URL: URLs end
     * up in browser history, proxy/access logs and {@code Referer} headers. A request that
     * carries {@code _csrf} in its query string is rejected instead of accepted.
     */
    private static final Pattern CSRF_IN_QUERY_STRING =
            Pattern.compile("(?:^|&)_csrf=", Pattern.CASE_INSENSITIVE);

    private final ReportRepository reportRepository;
    private final AdminAuth adminAuth;
    private final AdminLoginRateLimiter loginRateLimiter;
    private final AdminLoginPageRateLimiter loginPageRateLimiter;
    private final ClientAddressResolver clientAddressResolver;

    public AdminController(ReportRepository reportRepository,
                           AdminAuth adminAuth,
                           AdminLoginRateLimiter loginRateLimiter,
                           AdminLoginPageRateLimiter loginPageRateLimiter,
                           ClientAddressResolver clientAddressResolver) {
        this.reportRepository = reportRepository;
        this.adminAuth = adminAuth;
        this.loginRateLimiter = loginRateLimiter;
        this.loginPageRateLimiter = loginPageRateLimiter;
        this.clientAddressResolver = clientAddressResolver;
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

        // Do not allocate a session while the admin area is disabled: the page only shows the
        // "not configured" notice and has no form, so there is nothing to protect.
        if (!adminAuth.isConfigured()) {
            prepareLoginModel(model, null);
            model.addAttribute("loggedOut", false);
            return "admin/login";
        }

        // The login form carries a CSRF token in a server-side session, which makes this page
        // a session-allocation primitive for cookie-less clients. Throttle it per client and
        // never allocate a session for a throttled request (the global session cap is the
        // hard backstop).
        if (!loginPageRateLimiter.tryAcquire(clientKey(request))) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            prepareLoginModel(model, null);
            model.addAttribute("loginError", "admin.login.error.rateLimited");
            model.addAttribute("loggedOut", false);
            return "admin/login";
        }

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
        if (csrfSuppliedInQueryString(request) || !adminAuth.csrfMatches(session, csrf)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            // Never create a session just to render the CSRF error page: the caller can
            // reload the login page (which is itself throttled) to get a fresh token.
            prepareLoginModel(model, session);
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
            log.warn("Admin sign-in throttled for client={}", clientKey(request));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            prepareLoginModel(model, session);
            model.addAttribute("loginError", "admin.login.error.rateLimited");
            return "admin/login";
        }

        if (!adminAuth.authenticate(username, password)) {
            log.warn("Failed admin sign-in attempt username='{}' client={}",
                    LogValues.safe(username), clientKey(request));
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            prepareLoginModel(model, session);
            model.addAttribute("loginError", "admin.login.error.invalid");
            return "admin/login";
        }

        log.info("Admin sign-in succeeded username='{}' client={}",
                LogValues.safe(username), clientKey(request));
        adminAuth.signIn(request);
        return "redirect:/admin";
    }

    @PostMapping("/admin/logout")
    public String logout(@RequestParam(name = "_csrf", required = false) String csrf,
                         HttpServletRequest request) {
        requireCsrf(request, csrf);
        adminAuth.signOut(request);
        log.info("Admin signed out client={}", clientKey(request));
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
        log.info("Moderation: hid report id={} client={}", id, clientKey(request));
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
        log.info("Moderation: restored report id={} client={}", id, clientKey(request));
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
        log.info("Moderation: deleted report id={} client={}", id, clientKey(request));
        redirectAttributes.addFlashAttribute("adminNotice", "admin.notice.deleted");
        return "redirect:/admin";
    }

    /**
     * Populates the login page model. The session may be {@code null} when the caller must
     * not allocate one (throttled request or invalid CSRF token); the form then renders with
     * an empty token and the visitor can reload to obtain a fresh one.
     */
    private void prepareLoginModel(Model model, HttpSession session) {
        model.addAttribute("adminAuthenticated", false);
        model.addAttribute("adminConfigured", adminAuth.isConfigured());
        model.addAttribute("adminCsrfToken", session == null ? "" : adminAuth.csrfToken(session));
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
        if (csrfSuppliedInQueryString(request)
                || !adminAuth.csrfMatches(request.getSession(false), candidate)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid CSRF token");
        }
    }

    /**
     * True when a CSRF token was supplied in the URL query string. Tokens are only accepted
     * from the request body, so they cannot leak through history, proxy logs or {@code Referer}.
     */
    static boolean csrfSuppliedInQueryString(HttpServletRequest request) {
        String queryString = request.getQueryString();
        return queryString != null && CSRF_IN_QUERY_STRING.matcher(queryString).find();
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

    /**
     * Restricted redirect target for moderation actions. Only {@code /admin} paths are
     * accepted; protocol-relative URLs, backslashes, control characters and dot segments are
     * rejected so the parameter can never be used for an open redirect.
     */
    static String safeAdminReturn(String rawReturnTo) {
        if (rawReturnTo == null) {
            return "/admin";
        }
        String value = rawReturnTo.trim();
        if (value.length() > MAX_RETURN_TO_LENGTH) {
            return "/admin";
        }
        boolean adminPath = value.equals("/admin")
                || value.startsWith("/admin/")
                || value.startsWith("/admin?");
        if (!adminPath || value.startsWith("//") || value.contains("\\")) {
            return "/admin";
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character < 0x20 || character == 0x7F) {
                return "/admin";
            }
        }
        int queryStart = value.indexOf('?');
        String pathPart = queryStart >= 0 ? value.substring(0, queryStart) : value;
        for (String segment : pathPart.split("/", -1)) {
            if (".".equals(segment) || "..".equals(segment)) {
                return "/admin";
            }
        }
        return value;
    }

    private String clientKey(HttpServletRequest request) {
        return clientAddressResolver.resolve(request);
    }
}
