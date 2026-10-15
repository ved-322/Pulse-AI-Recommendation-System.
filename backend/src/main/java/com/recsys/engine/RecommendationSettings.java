package com.recsys.engine;

import com.recsys.util.Validator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin-tunable algorithm parameters. The compact constructor validates, so an instance
 * can never hold illegal values (fail fast, one source of truth for the rules).
 */
public record RecommendationSettings(double weightContent, double weightCollaborative, double weightPopularity,
                                     int maxResults, double minScore, int maxPerCategory, int halfLifeDays) {

    public RecommendationSettings {
        Validator.doubleInRange(weightContent, 0, 1, "Content weight");
        Validator.doubleInRange(weightCollaborative, 0, 1, "Collaborative weight");
        Validator.doubleInRange(weightPopularity, 0, 1, "Popularity weight");
        if (weightContent + weightCollaborative + weightPopularity <= 0) {
            throw new com.recsys.exception.ValidationException("At least one signal weight must be greater than zero.");
        }
        Validator.intInRange(maxResults, 1, 24, "Max results");
        Validator.doubleInRange(minScore, 0, 1, "Minimum score");
        Validator.intInRange(maxPerCategory, 1, 10, "Max per category");
        Validator.intInRange(halfLifeDays, 1, 90, "Recency half-life");
    }

    public static RecommendationSettings defaults() {
        return new RecommendationSettings(0.50, 0.30, 0.20, 12, 0.05, 3, 14);
    }

    private double total() { return weightContent + weightCollaborative + weightPopularity; }

    public double contentShare() { return weightContent / total(); }

    public double collaborativeShare() { return weightCollaborative / total(); }

    public double popularityShare() { return weightPopularity / total(); }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("weightContent", weightContent);
        m.put("weightCollaborative", weightCollaborative);
        m.put("weightPopularity", weightPopularity);
        m.put("maxResults", maxResults);
        m.put("minScore", minScore);
        m.put("maxPerCategory", maxPerCategory);
        m.put("halfLifeDays", halfLifeDays);
        return m;
    }
}
