package com.recsys.service;

import com.recsys.config.Database;
import com.recsys.dao.InteractionDao;
import com.recsys.dao.RecommendationLogDao;
import com.recsys.dao.ReviewDao;
import com.recsys.exception.ValidationException;
import com.recsys.model.InteractionType;
import com.recsys.model.RecOutcome;
import com.recsys.model.Review;
import com.recsys.model.User;
import com.recsys.util.Validator;

import java.util.List;
import java.util.Map;

/**
 * Customer reviews. Writing a review is also behavioural feedback: a 4-5 star review counts as a LIKE
 * and a 1-2 star review as a dismissal, so the recommender learns from it in the same transaction.
 */
public class ReviewService {
    private static final int MAX_BODY = 1000;

    private final Database db;
    private final ReviewDao reviewDao;
    private final InteractionDao interactionDao;
    private final RecommendationLogDao logDao;
    private final ItemService items;

    public ReviewService(Database db, ReviewDao reviewDao, InteractionDao interactionDao,
                         RecommendationLogDao logDao, ItemService items) {
        this.db = db;
        this.reviewDao = reviewDao;
        this.interactionDao = interactionDao;
        this.logDao = logDao;
        this.items = items;
    }

    public List<Review> forItem(long itemId) {
        items.require(itemId);
        return db.read(c -> reviewDao.findByItem(c, itemId, 50));
    }

    public Map<Long, double[]> summaries() {
        return db.read(reviewDao::summaries);
    }

    public void write(User user, long itemId, int rating, String body) {
        items.require(itemId);
        int stars = Validator.intInRange(rating, 1, 5, "Rating");
        String text = body == null ? "" : body.trim();
        if (text.length() < 3) throw new ValidationException("Please write at least a few words about the product.");
        if (text.length() > MAX_BODY) throw new ValidationException("Reviews are limited to " + MAX_BODY + " characters.");

        long now = System.currentTimeMillis();
        db.inTransaction(c -> {
            reviewDao.upsert(c, user.id(), itemId, stars, text, false, now);
            InteractionType signal = stars >= 4 ? InteractionType.LIKE : stars <= 2 ? InteractionType.DISMISS : null;
            if (signal != null) {
                interactionDao.insert(c, user.id(), itemId, signal, now);
                RecOutcome outcome = RecOutcome.from(signal);
                logDao.findLatest(c, user.id(), itemId).ifPresent(row -> {
                    if (outcome.supersedes(row.outcome())) {
                        try {
                            logDao.updateOutcome(c, row.id(), outcome, now);
                        } catch (java.sql.SQLException e) {
                            throw new com.recsys.exception.DataAccessException("Could not update recommendation log", e);
                        }
                    }
                });
            }
            return null;
        });
    }

    public void deleteOwn(User user, long itemId) {
        db.inTransaction(c -> reviewDao.deleteOwn(c, user.id(), itemId));
    }
}
