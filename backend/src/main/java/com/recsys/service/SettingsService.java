package com.recsys.service;

import com.recsys.config.Database;
import com.recsys.dao.SettingsDao;
import com.recsys.engine.RecommendationSettings;
import com.recsys.exception.ValidationException;

import java.util.HashMap;
import java.util.Map;

/** Loads, validates and persists the admin-configurable algorithm parameters. */
public class SettingsService {
    private static final String[] KEYS = {"weightContent", "weightCollaborative", "weightPopularity",
            "maxResults", "minScore", "maxPerCategory", "halfLifeDays"};

    private final Database db;
    private final SettingsDao dao;
    private volatile RecommendationSettings current;

    public SettingsService(Database db, SettingsDao dao) {
        this.db = db;
        this.dao = dao;
        this.current = load();
    }

    public RecommendationSettings current() { return current; }

    public RecommendationSettings defaults() { return RecommendationSettings.defaults(); }

    /**
     * Merges the supplied values over the current ones, validates the result and stores it
     * atomically. Unknown keys are rejected so typos never silently do nothing.
     */
    public RecommendationSettings update(Map<String, Object> input) {
        Map<String, Object> merged = new HashMap<>(current.toMap());
        for (Map.Entry<String, Object> e : input.entrySet()) {
            if (!merged.containsKey(e.getKey())) throw new ValidationException("Unknown setting: " + e.getKey());
            if (!(e.getValue() instanceof Number)) throw new ValidationException(e.getKey() + " must be a number.");
            merged.put(e.getKey(), e.getValue());
        }
        RecommendationSettings next = new RecommendationSettings(
                num(merged, "weightContent"), num(merged, "weightCollaborative"), num(merged, "weightPopularity"),
                (int) num(merged, "maxResults"), num(merged, "minScore"),
                (int) num(merged, "maxPerCategory"), (int) num(merged, "halfLifeDays"));

        Map<String, String> rows = new HashMap<>();
        next.toMap().forEach((k, v) -> rows.put(k, String.valueOf(v)));
        db.inTransaction(c -> {
            dao.upsertAll(c, rows);
            return null;
        });
        this.current = next;
        return next;
    }

    public RecommendationSettings reset() {
        return update(defaults().toMap());
    }

    private RecommendationSettings load() {
        Map<String, String> rows = db.read(dao::findAll);
        Map<String, Object> m = new HashMap<>(RecommendationSettings.defaults().toMap());
        for (String key : KEYS) {
            if (rows.containsKey(key)) m.put(key, Double.parseDouble(rows.get(key)));
        }
        try {
            return new RecommendationSettings(num(m, "weightContent"), num(m, "weightCollaborative"),
                    num(m, "weightPopularity"), (int) num(m, "maxResults"), num(m, "minScore"),
                    (int) num(m, "maxPerCategory"), (int) num(m, "halfLifeDays"));
        } catch (ValidationException e) {
            return RecommendationSettings.defaults(); // corrupted rows must never stop the app
        }
    }

    private static double num(Map<String, Object> m, String key) {
        return ((Number) m.get(key)).doubleValue();
    }
}
