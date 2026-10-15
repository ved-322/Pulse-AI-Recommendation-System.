package com.recsys.web;

import com.recsys.engine.RecommendationSettings;
import com.recsys.service.SettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** /api/admin/settings - GET current + default parameters, PUT to update, DELETE to reset to defaults. */
public class AdminSettingsServlet extends ApiServlet {
    private final SettingsService settings;

    public AdminSettingsServlet(SettingsService settings) {
        this.settings = settings;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ok(resp, view(settings.current(), null));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> input = body(req, Map.class);
        ok(resp, view(settings.update(input), "Settings saved. New recommendations use them immediately."));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ok(resp, view(settings.reset(), "Settings reset to defaults."));
    }

    private Map<String, Object> view(RecommendationSettings current, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("settings", current.toMap());
        out.put("defaults", settings.defaults().toMap());
        if (message != null) out.put("message", message);
        return out;
    }
}
