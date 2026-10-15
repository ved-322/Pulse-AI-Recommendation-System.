package com.recsys.web;

import com.recsys.model.User;
import com.recsys.service.UserService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Set;

/**
 * Session-based access control for every /api/* call (chain-of-responsibility).
 * Public: login / register / logout / me. Everything else needs a live session of an active
 * user; /api/admin/* additionally needs the ADMIN role.
 */
public class AuthFilter implements Filter {
    public static final String USER_ATTRIBUTE = "recsys.user";
    public static final String SESSION_USER_ID = "uid";
    private static final Set<String> PUBLIC = Set.of("/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/auth/me", "/api/categories");

    private final UserService users;

    public AuthFilter(UserService users) {
        this.users = users;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();

        if (PUBLIC.contains(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        Object uid = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (!(uid instanceof Long id)) {
            ApiServlet.writeError(resp, 401, "UNAUTHENTICATED", "Please sign in to continue.");
            return;
        }

        User user;
        try {
            user = users.require(id);
        } catch (RuntimeException e) {
            session.invalidate(); // account deleted while signed in
            ApiServlet.writeError(resp, 401, "UNAUTHENTICATED", "Your session has expired.");
            return;
        }
        if (!user.active()) {
            session.invalidate();
            ApiServlet.writeError(resp, 403, "FORBIDDEN", "This account has been deactivated.");
            return;
        }
        if (path.startsWith("/api/admin/") && !user.isAdmin()) {
            ApiServlet.writeError(resp, 403, "FORBIDDEN", "Administrator access required.");
            return;
        }
        req.setAttribute(USER_ATTRIBUTE, user);
        chain.doFilter(request, response);
    }
}
