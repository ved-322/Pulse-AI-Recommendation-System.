package com.recsys.model;

/** One behavioural event: a user viewed / clicked / liked / bought / dismissed an item. */
public record Interaction(long id, long userId, long itemId, InteractionType type, long createdAt) {
}
