package com.recsys.engine;

import java.util.Map;

/**
 * Strategy interface: one recommendation signal. Implementations return a score in 0..1
 * per item id (items missing from the map are treated as 0). The engine blends the signals,
 * so new algorithms can be added without touching existing code (open/closed principle).
 */
public interface Scorer {

    /** Stable identifier used in the UI and analytics: content | collaborative | popularity. */
    String name();

    Map<Long, Double> score(UserContext ctx, RecommendationSettings settings);
}
