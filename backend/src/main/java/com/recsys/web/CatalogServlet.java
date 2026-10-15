package com.recsys.web;

import com.recsys.service.ItemService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** GET /api/items?category=&amp;q= - browse and search the catalogue. */
public class CatalogServlet extends ApiServlet {
    private final ItemService items;

    public CatalogServlet(ItemService items) {
        this.items = items;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("items", items.search(req.getParameter("category"), req.getParameter("q")));
        out.put("categories", items.categories());
        ok(resp, out);
    }
}
