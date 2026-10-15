package com.recsys.web;

import com.recsys.service.ItemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** GET /api/categories - public list of categories (name, emoji, colour) and popular tags. */
public class CategoryServlet extends ApiServlet {
    private final ItemService items;

    public CategoryServlet(ItemService items) {
        this.items = items;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("categories", items.categoryDetails());
        out.put("tags", items.popularTags(48));
        ok(resp, out);
    }
}
