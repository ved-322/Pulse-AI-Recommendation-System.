package com.recsys.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** A registered account. {@code passwordHash} must never leave the server - use {@link #toPublicMap()}. */
public record User(long id, String name, String email, String passwordHash,
                   Role role, boolean active, long createdAt) {

    public boolean isAdmin() { return role == Role.ADMIN; }

    public Map<String, Object> toPublicMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("email", email);
        m.put("role", role.name());
        m.put("active", active);
        m.put("createdAt", createdAt);
        return m;
    }
}
