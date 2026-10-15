package com.recsys.model;

/** Row of the admin user-management table. */
public record UserSummary(long id, String name, String email, Role role, boolean active,
                          long createdAt, int interactions) {
}
