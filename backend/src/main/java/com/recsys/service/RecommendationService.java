package com.recsys.service;

import com.recsys.config.Database;
import com.recsys.dao.InteractionDao;
import com.recsys.dao.PreferenceDao;
import com.recsys.dao.RecommendationLogDao;
import com.recsys.engine.RecommendationEngine;
import com.recsys.engine.RecommendationSettings;
import com.recsys.engine.UserContext;
import com.recsys.model.Interaction;
import com.recsys.model.RecOutcome;
import com.recsys.model.Recommendation;
import com.recsys.model.User;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Assembles the user's data, runs the engine and records the impressions for analytics. */
public class RecommendationService {
    private static final long IMPRESSION_DEDUPE_MILLIS = 30 * 60_000L;

    public record Result(List<Recommendation> recommendations, boolean coldStart, RecommendationSettings settings) { }

    private final Database db;
    private final PreferenceDao prefDao;
    private final InteractionDao interactionDao;
    private final RecommendationLogDao logDao;
    private final ItemService items;
    private final SettingsService settings;
    private final RecommendationEngine engine;

    public RecommendationService(Database db, PreferenceDao prefDao, InteractionDao interactionDao,
                                 RecommendationLogDao logDao, ItemService items, SettingsService settings,
                                 RecommendationEngine engine) {
        this.db = db;
        this.prefDao = prefDao;
        this.interactionDao = interactionDao;
        this.logDao = logDao;
        this.items = items;
        this.settings = settings;
        this.engine = engine;
    }

    public Result recommendFor(User user) {
        RecommendationSettings s = settings.current();
        UserContext ctx = db.read(c -> {
            List<Interaction> all = interactionDao.findAll(c);
            Map<Long, List<Interaction>> byUser = all.stream().collect(Collectors.groupingBy(Interaction::userId));
            List<Interaction> mine = byUser.getOrDefault(user.id(), List.of());
            return new UserContext(user, prefDao.categoryWeights(c, user.id()), prefDao.tags(c, user.id()),
                    mine, items.byId(), byUser, System.currentTimeMillis());
        });

        List<Recommendation> recs = engine.recommend(ctx, s);
        logImpressions(user.id(), recs);
        return new Result(recs, ctx.isColdStart(), s);
    }

    /** Stores what was shown; items already shown in the last 30 minutes are not double-counted. */
    private void logImpressions(long userId, List<Recommendation> recs) {
        if (recs.isEmpty()) return;
        long now = System.currentTimeMillis();
        db.inTransaction(c -> {
            Set<Long> recent = logDao.itemsShownSince(c, userId, now - IMPRESSION_DEDUPE_MILLIS);
            for (Recommendation r : recs) {
                if (recent.contains(r.item().id())) continue;
                logDao.insert(c, userId, r.item().id(), r.score(), r.source(), RecOutcome.SHOWN, now, null);
            }
            return null;
        });
    }
}
