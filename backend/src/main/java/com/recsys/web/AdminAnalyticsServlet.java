package com.recsys.web;

import com.recsys.exception.ValidationException;
import com.recsys.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** GET /api/admin/analytics?days=14 - effectiveness report for the dashboard. */
public class AdminAnalyticsServlet extends ApiServlet {
    private final AnalyticsService analytics;

    public AdminAnalyticsServlet(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int days = 14;
        String raw = req.getParameter("days");
        if (raw != null) {
            try {
                days = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                throw new ValidationException("days must be a number.");
            }
        }
        ok(resp, analytics.report(days));
    }
}
