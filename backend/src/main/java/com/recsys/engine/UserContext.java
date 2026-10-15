package com.recsys.engine;

import com.recsys.model.Interaction;
import com.recsys.model.Item;
import com.recsys.model.User;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of everything a scorer may need. Because it is never mutated,
 * the scorers can safely run in parallel threads.
 *
 * @param categoryPrefs explicit 0-5 weight per category
 * @param tags          explicitly chosen favourite tags
 * @param history       this user's past interactions
 * @param items         the catalogue by id
 * @param allByUser     every user's interactions (for collaborative filtering / popularity)
 */
public record UserContext(User user, Map<String, Integer> categoryPrefs, Set<String> tags,
                          List<Interaction> history, Map<Long, Item> items,
                          Map<Long, List<Interaction>> allByUser, long now) {

    public boolean isColdStart() {
        return categoryPrefs.isEmpty() && tags.isEmpty() && history.isEmpty();
    }

    /** Exponential recency decay: an event {@code halfLifeDays} old counts half as much. */
    public double decay(long timestamp, int halfLifeDays) {
        double ageDays = Math.max(0, now - timestamp) / 86_400_000.0;
        return Math.pow(0.5, ageDays / halfLifeDays);
    }
}
