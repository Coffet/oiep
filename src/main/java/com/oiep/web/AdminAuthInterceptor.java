package com.oiep.web;

import com.oiep.service.AdminAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Protects every /admin route except the login page. Unauthenticated requests are redirected
 * to the login screen without creating a session; authenticated admin responses are marked
 * non-cacheable.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminAuth adminAuth;

    public AdminAuthInterceptor(AdminAuth adminAuth) {
        this.adminAuth = adminAuth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("X-Robots-Tag", "noindex, nofollow");

        HttpSession session = request.getSession(false);
        if (adminAuth.isAuthenticated(session)) {
            return true;
        }

        response.sendRedirect(request.getContextPath() + "/admin/login");
        return false;
    }
}
