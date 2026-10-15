package com.recsys.engine;

import com.recsys.model.Interaction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Trending signal: recency-weighted positive engagement across the whole community. */
public class PopularityScorer implements Scorer {

    public static final String NAME = "popularity";

    @Override
    public String name() { return NAME; }

    @Override
    public Map<Long, Double> score(UserContext ctx, RecommendationSettings settings) {
        Map<Long, Double> totals = new HashMap<>();
        double max = 0;
        for (List<Interaction> events : ctx.allByUser().values()) {
            for (Interaction in : events) {
                if (!in.type().isPositive()) continue;
                double v = totals.merge(in.itemId(),
                        in.type().weight() * ctx.decay(in.createdAt(), settings.halfLifeDays()), Double::sum);
                max = Math.max(max, v);
            }
        }
        if (max <= 0) return Map.of();
        final double top = max;
        totals.replaceAll((id, v) -> v / top);
        return totals;
    }
}
