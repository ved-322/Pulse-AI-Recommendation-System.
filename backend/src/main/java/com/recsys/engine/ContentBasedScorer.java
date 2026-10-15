package com.recsys.engine;

import com.recsys.model.Interaction;
import com.recsys.model.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * Content-based filtering. Builds a feature profile of the user (categories + tags) from
 * explicit preferences AND implicit behaviour, then scores each item by how many weighted
 * features it shares with that profile.
 */
public class ContentBasedScorer implements Scorer {

    public static final String NAME = "content";

    @Override
    public String name() { return NAME; }

    @Override
    public Map<Long, Double> score(UserContext ctx, RecommendationSettings settings) {
        Map<String, Double> profile = buildProfile(ctx, settings);

        Map<Long, Double> raw = new HashMap<>();
        double max = 0;
        for (Item item : ctx.items().values()) {
            double s = 0;
            for (String feature : features(item)) s += profile.getOrDefault(feature, 0.0);
            raw.put(item.id(), s);
            max = Math.max(max, s);
        }
        if (max <= 0) return Map.of();

        Map<Long, Double> normalised = new HashMap<>();
        for (Map.Entry<Long, Double> e : raw.entrySet()) {
            normalised.put(e.getKey(), Math.max(0, e.getValue()) / max);
        }
        return normalised;
    }

    /** Feature -> weight. Explicit prefs weigh strongly; behaviour is recency-decayed. */
    Map<String, Double> buildProfile(UserContext ctx, RecommendationSettings settings) {
        Map<String, Double> profile = new HashMap<>();
        ctx.categoryPrefs().forEach((cat, w) -> profile.merge(categoryFeature(cat), w / 5.0, Double::sum));
        ctx.tags().forEach(t -> profile.merge(tagFeature(t), 0.6, Double::sum));

        for (Interaction in : ctx.history()) {
            Item item = ctx.items().get(in.itemId());
            if (item == null) continue;
            double w = in.type().weight() * ctx.decay(in.createdAt(), settings.halfLifeDays()) * 0.25;
            for (String f : features(item)) profile.merge(f, w, Double::sum);
        }
        return profile;
    }

    static java.util.List<String> features(Item item) {
        java.util.List<String> f = new java.util.ArrayList<>();
        f.add(categoryFeature(item.category()));
        for (String t : item.tags()) f.add(tagFeature(t));
        return f;
    }

    static String categoryFeature(String category) { return "c:" + category.toLowerCase(); }

    static String tagFeature(String tag) { return "t:" + tag; }
}
