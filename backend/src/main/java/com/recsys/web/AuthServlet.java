package com.recsys.web;

import com.recsys.exception.NotFoundException;
import com.recsys.model.User;
import com.recsys.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * /api/auth/register | login | logout (POST) and /api/auth/me (GET).
 * Demonstrates HttpSession management: a fresh session id is issued at login (session-fixation defence)
 * and the session is destroyed at logout.
 */
public class AuthServlet extends ApiServlet {
    record RegisterRequest(String name, String email, String password, List<String> categories) { }

    record LoginRequest(String email, String password) { }

    private final UserService users;

    public AuthServlet(UserService users) {
        this.users = users;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getPathInfo() == null ? "" : req.getPathInfo();
        switch (action) {
            case "/register" -> {
                RegisterRequest in = body(req, RegisterRequest.class);
                User user = users.register(in.name(), in.email(), in.password(), in.categories());
                startSession(req, user);
                write(resp, 201, userBody(user));
            }
            case "/login" -> {
                LoginRequest in = body(req, LoginRequest.class);
                User user = users.login(in.email(), in.password());
                startSession(req, user);
                ok(resp, userBody(user));
            }
            case "/logout" -> {
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
                ok(resp, Map.of("ok", true));
            }
            default -> throw new NotFoundException("Unknown auth action.");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!"/me".equals(req.getPathInfo())) throw new NotFoundException("Unknown auth action.");
        HttpSession s = req.getSession(false);
        Object uid = s == null ? null : s.getAttribute(AuthFilter.SESSION_USER_ID);
        if (!(uid instanceof Long id)) {
            writeError(resp, 401, "UNAUTHENTICATED", "Not signed in.");
            return;
        }
        try {
            User user = users.require(id);
            if (!user.active()) throw new NotFoundException("inactive");
            ok(resp, userBody(user));
        } catch (RuntimeException e) {
            s.invalidate();
            writeError(resp, 401, "UNAUTHENTICATED", "Not signed in.");
        }
    }

    private void startSession(HttpServletRequest req, User user) {
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = req.getSession(true);
        session.setAttribute(AuthFilter.SESSION_USER_ID, user.id());
        session.setMaxInactiveInterval(30 * 60);
    }

    private Map<String, Object> userBody(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user", user.toPublicMap());
        return m;
    }
}
