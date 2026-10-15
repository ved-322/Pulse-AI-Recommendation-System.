package com.recsys.web;

import com.recsys.exception.NotFoundException;
import com.recsys.exception.ValidationException;
import com.recsys.model.Review;
import com.recsys.model.User;
import com.recsys.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/reviews/summary - average rating + count per item.
 * GET /api/reviews?itemId=  - reviews of one item.
 * POST /api/reviews {itemId, rating, body} - write or replace your review.
 * DELETE /api/reviews?itemId= - remove your review.
 */
public class ReviewServlet extends ApiServlet {
    record ReviewRequest(Long itemId, Integer rating, String body) { }

    private final ReviewService service;

    public ReviewServlet(ReviewService service) {
        this.service = service;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User me = currentUser(req);
        if ("/summary".equals(req.getPathInfo())) {
            Map<String, Object> out = new LinkedHashMap<>();
            service.summaries().forEach((id, s) -> out.put(String.valueOf(id), s));
            ok(resp, out);
            return;
        }
        long itemId = itemId(req);
        List<Map<String, Object>> list = service.forItem(itemId).stream().map(r -> view(r, me)).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reviews", list);
        ok(resp, out);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ReviewRequest in = body(req, ReviewRequest.class);
        if (in.itemId() == null || in.rating() == null) throw new ValidationException("itemId and rating are required.");
        service.write(currentUser(req), in.itemId(), in.rating(), in.body());
        write(resp, 201, Map.of("ok", true));
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        service.deleteOwn(currentUser(req), itemId(req));
        ok(resp, Map.of("ok", true));
    }

    private long itemId(HttpServletRequest req) {
        try {
            return Long.parseLong(req.getParameter("itemId"));
        } catch (NumberFormatException e) {
            throw new NotFoundException("itemId is required.");
        }
    }

    private Map<String, Object> view(Review r, User me) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("author", r.author());
        m.put("rating", r.rating());
        m.put("body", r.body());
        m.put("sample", r.sample());
        m.put("mine", r.userId() == me.id());
        m.put("createdAt", r.createdAt());
        return m;
    }
}
