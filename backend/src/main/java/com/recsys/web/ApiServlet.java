package com.recsys.web;

import com.recsys.exception.AuthenticationException;
import com.recsys.exception.NotFoundException;
import com.recsys.exception.RecSysException;
import com.recsys.exception.ValidationException;
import com.recsys.model.User;
import com.recsys.util.Json;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for all JSON endpoints. It gives every servlet the same behaviour:
 * one place that turns exceptions into JSON error responses, plus small request/response helpers.
 */
public abstract class ApiServlet extends HttpServlet {
    private static final Logger LOG = Logger.getLogger(ApiServlet.class.getName());
    private static final int MAX_BODY_CHARS = 64 * 1024;

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            if ("PATCH".equals(req.getMethod())) {
                doPatch(req, resp); // HttpServlet has no doPatch of its own
            } else {
                super.service(req, resp);
            }
        } catch (RecSysException e) {
            writeError(resp, e.getStatus(), e.getCode(), e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unhandled error on " + req.getMethod() + " " + req.getRequestURI(), e);
            writeError(resp, 500, "INTERNAL_ERROR", "Something went wrong on our side. Please try again.");
        }
    }

    protected void doPatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        writeError(resp, 405, "METHOD_NOT_ALLOWED", "PATCH is not supported here.");
    }

    protected void ok(HttpServletResponse resp, Object body) throws IOException {
        write(resp, 200, body);
    }

    protected void write(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(Json.toJson(body));
    }

    public static void writeError(HttpServletResponse resp, int status, String code, String message) throws IOException {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("error", code);
        err.put("message", message);
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(Json.toJson(err));
    }

    protected <T> T body(HttpServletRequest req, Class<T> type) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = req.getReader()) {
            char[] buf = new char[2048];
            int n;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
                if (sb.length() > MAX_BODY_CHARS) throw new ValidationException("Request body is too large.");
            }
        }
        return Json.fromJson(sb.toString(), type);
    }

    /** The signed-in user placed on the request by {@link AuthFilter}. */
    protected User currentUser(HttpServletRequest req) {
        Object u = req.getAttribute(AuthFilter.USER_ATTRIBUTE);
        if (u instanceof User user) return user;
        throw new AuthenticationException("Please sign in.");
    }

    /** Last path segment as a long id, e.g. /api/admin/users/42 -> 42. */
    protected long pathId(HttpServletRequest req) {
        String path = req.getPathInfo();
        if (path == null || path.length() < 2) throw new NotFoundException("Resource id missing.");
        try {
            return Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        } catch (NumberFormatException e) {
            throw new NotFoundException("Invalid resource id.");
        }
    }
}
