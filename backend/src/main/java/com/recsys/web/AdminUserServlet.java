package com.recsys.web;

import com.recsys.exception.ValidationException;
import com.recsys.model.Role;
import com.recsys.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Admin user management: GET /api/admin/users (list), GET /api/admin/users/{id} (profile),
 * PATCH /api/admin/users/{id} {role?, active?}, DELETE /api/admin/users/{id}.
 */
public class AdminUserServlet extends ApiServlet {
    record UpdateRequest(String role, Boolean active) { }

    private final UserService users;

    public AdminUserServlet(UserService users) {
        this.users = users;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (req.getPathInfo() == null || "/".equals(req.getPathInfo())) {
            ok(resp, Map.of("users", users.listAll()));
        } else {
            ok(resp, users.profile(pathId(req)));
        }
    }

    @Override
    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long target = pathId(req);
        UpdateRequest in = body(req, UpdateRequest.class);
        Role role = null;
        if (in.role() != null) {
            try {
                role = Role.valueOf(in.role().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ValidationException("Role must be ADMIN or USER.");
            }
        }
        ok(resp, Map.of("user", users.updateByAdmin(currentUser(req).id(), target, role, in.active()).toPublicMap()));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        users.delete(currentUser(req).id(), pathId(req));
        ok(resp, Map.of("ok", true));
    }
}
