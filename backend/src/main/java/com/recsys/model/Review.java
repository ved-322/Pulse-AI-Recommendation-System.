package com.recsys.model;

/**
 * A customer review. {@code sample} marks seeded demo reviews so the UI can label them honestly.
 */
public record Review(long id, long userId, String author, long itemId, int rating, String body,
                     boolean sample, long createdAt) {
}
