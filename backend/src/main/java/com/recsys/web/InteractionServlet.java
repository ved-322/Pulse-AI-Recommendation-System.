package com.recsys.web;

import com.recsys.exception.ValidationException;
import com.recsys.model.InteractionType;
import com.recsys.service.InteractionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/** POST /api/interactions {itemId, type} - records a view / click / like / purchase / dismiss. */
public class InteractionServlet extends ApiServlet {
    record InteractionRequest(Long itemId, String type) { }

    private final InteractionService service;

    public InteractionServlet(InteractionService service) {
        this.service = service;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        InteractionRequest in = body(req, InteractionRequest.class);
        if (in.itemId() == null) throw new ValidationException("itemId is required.");
        service.record(currentUser(req).id(), in.itemId(), InteractionType.parse(in.type()));
        write(resp, 201, Map.of("ok", true));
    }
}
