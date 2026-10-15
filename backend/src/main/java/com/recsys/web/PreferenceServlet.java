package com.recsys.web;

import com.recsys.service.PreferenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** GET /api/preferences - read; PUT /api/preferences - replace name, category weights and tags. */
public class PreferenceServlet extends ApiServlet {
    record PreferenceRequest(String name, Map<String, Double> categories, List<String> tags) { }

    private final PreferenceService service;

    public PreferenceServlet(PreferenceService service) {
        this.service = service;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ok(resp, service.view(currentUser(req).id()));
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long id = currentUser(req).id();
        PreferenceRequest in = body(req, PreferenceRequest.class);
        service.update(id, in.name(), in.categories(), in.tags());
        ok(resp, service.view(id));
    }
}
