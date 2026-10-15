package com.recsys.service;

import com.recsys.config.Database;
import com.recsys.dao.InteractionDao;
import com.recsys.dao.RecommendationLogDao;
import com.recsys.model.InteractionType;
import com.recsys.model.RecOutcome;

/** Records user behaviour and links it back to the recommendation that triggered it. */
public class InteractionService {
    private final Database db;
    private final InteractionDao interactionDao;
    private final RecommendationLogDao logDao;
    private final ItemService items;

    public InteractionService(Database db, InteractionDao interactionDao, RecommendationLogDao logDao, ItemService items) {
        this.db = db;
        this.interactionDao = interactionDao;
        this.logDao = logDao;
        this.items = items;
    }

    /**
     * Inserts the interaction and, if the item was recommended, upgrades the outcome of the
     * latest recommendation-log row. Both writes commit together or not at all.
     */
    public void record(long userId, long itemId, InteractionType type) {
        items.require(itemId); // NotFoundException for unknown items
        long now = System.currentTimeMillis();
        RecOutcome outcome = RecOutcome.from(type);

        db.inTransaction(c -> {
            interactionDao.insert(c, userId, itemId, type, now);
            if (outcome != null) {
                logDao.findLatest(c, userId, itemId).ifPresent(row -> {
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
}
