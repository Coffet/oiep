package com.oiep.web;

import com.oiep.domain.ReportForm;
import com.oiep.domain.ReportOptions;
import com.oiep.repository.ReportRepository;
import com.oiep.service.SubmissionRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Landing page (share a note + recent notes) and report creation.
 */
@Controller
public class HomeController {

    private final ReportRepository reportRepository;
    private final SubmissionRateLimiter rateLimiter;

    public HomeController(ReportRepository reportRepository, SubmissionRateLimiter rateLimiter) {
        this.reportRepository = reportRepository;
        this.rateLimiter = rateLimiter;
    }

    /** Trim text inputs and treat all-whitespace fields as empty. */
    @InitBinder("reportForm")
    public void initReportFormBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("reportForm", new ReportForm());
        addIndexModel(model);
        return "index";
    }

    @PostMapping("/reports")
    public String createReport(@Valid @ModelAttribute("reportForm") ReportForm reportForm,
                               BindingResult bindingResult,
                               Model model,
                               HttpServletRequest request,
                               HttpServletResponse response,
                               RedirectAttributes redirectAttributes) {
        // Honeypot: real users never fill the hidden "website" field. Pretend all is well
        // and save nothing, so bots do not learn that they were caught.
        if (StringUtils.hasText(reportForm.getWebsite())) {
            return "redirect:/";
        }

        if (!rateLimiter.tryAcquire(clientKey(request))) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute("submitBlocked", true);
            addIndexModel(model);
            return "index";
        }

        if (bindingResult.hasErrors()) {
            response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
            addIndexModel(model);
            return "index";
        }

        reportForm.setQuestionTypes(ReportOptions.sanitizeQuestionTypes(reportForm.getQuestionTypes()));
        long reportId = reportRepository.insert(reportForm);
        redirectAttributes.addFlashAttribute("justPosted", true);
        return "redirect:/reports/" + reportId;
    }

    private void addIndexModel(Model model) {
        model.addAttribute("recentReports", reportRepository.findRecent(3));
        model.addAttribute("currentUrl", "/");
        model.addAttribute("activeNav", "home");
    }

    private static String clientKey(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddress) ? remoteAddress : "unknown";
    }
}
