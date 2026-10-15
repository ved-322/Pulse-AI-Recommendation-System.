package com.recsys.web;

import com.recsys.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** GET /api/recommendations - personalised suggestions for the signed-in user. */
public class RecommendationServlet extends ApiServlet {
    private final RecommendationService service;

    public RecommendationServlet(RecommendationService service) {
        this.service = service;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        RecommendationService.Result result = service.recommendFor(currentUser(req));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recommendations", result.recommendations());
        out.put("coldStart", result.coldStart());
        out.put("weights", Map.of(
                "content", result.settings().contentShare(),
                "collaborative", result.settings().collaborativeShare(),
                "popularity", result.settings().popularityShare()));
        out.put("generatedAt", System.currentTimeMillis());
        ok(resp, out);
    }
}
