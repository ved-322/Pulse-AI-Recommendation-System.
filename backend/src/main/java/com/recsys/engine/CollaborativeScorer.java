package com.recsys.engine;

import com.recsys.model.Interaction;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * User-based collaborative filtering: "people with similar behaviour liked this".
 * Similarity is the cosine of two users' positive-interest vectors; the K nearest
 * neighbours (kept in a bounded min-heap) vote on items the target user has not seen.
 */
public class CollaborativeScorer implements Scorer {

    public static final String NAME = "collaborative";
    static final int NEIGHBOURS = 10;

    record Neighbour(long userId, double similarity) { }

    @Override
    public String name() { return NAME; }

    @Override
    public Map<Long, Double> score(UserContext ctx, RecommendationSettings settings) {
        Map<Long, Double> mine = vector(ctx.history(), ctx, settings);
        if (mine.isEmpty()) return Map.of();

        PriorityQueue<Neighbour> heap = new PriorityQueue<>(Comparator.comparingDouble(Neighbour::similarity));
        Map<Long, Map<Long, Double>> vectors = new HashMap<>();

        for (Map.Entry<Long, List<Interaction>> e : ctx.allByUser().entrySet()) {
            if (e.getKey() == ctx.user().id()) continue;
            Map<Long, Double> theirs = vector(e.getValue(), ctx, settings);
            double sim = cosine(mine, theirs);
            if (sim <= 0) continue;
            vectors.put(e.getKey(), theirs);
            heap.offer(new Neighbour(e.getKey(), sim));
            if (heap.size() > NEIGHBOURS) heap.poll(); // evict the weakest neighbour
        }

        Map<Long, Double> votes = new HashMap<>();
        double max = 0;
        for (Neighbour n : heap) {
            for (Map.Entry<Long, Double> r : vectors.get(n.userId()).entrySet()) {
                if (mine.containsKey(r.getKey())) continue; // already known to the user
                double v = votes.merge(r.getKey(), n.similarity() * r.getValue(), Double::sum);
                max = Math.max(max, v);
            }
        }
        if (max <= 0) return Map.of();
        final double top = max;
        votes.replaceAll((id, v) -> v / top);
        return votes;
    }

    /** item -> summed, recency-decayed positive interest. Negative signals are dropped. */
    private Map<Long, Double> vector(List<Interaction> events, UserContext ctx, RecommendationSettings s) {
        Map<Long, Double> v = new HashMap<>();
        for (Interaction in : events) {
            if (!in.type().isPositive()) continue;
            v.merge(in.itemId(), in.type().weight() * ctx.decay(in.createdAt(), s.halfLifeDays()), Double::sum);
        }
        return v;
    }

    static double cosine(Map<Long, Double> a, Map<Long, Double> b) {
        Map<Long, Double> small = a.size() <= b.size() ? a : b;
        Map<Long, Double> large = small == a ? b : a;
        double dot = 0;
        for (Map.Entry<Long, Double> e : small.entrySet()) {
            Double other = large.get(e.getKey());
            if (other != null) dot += e.getValue() * other;
        }
        if (dot == 0) return 0;
        return dot / (norm(a) * norm(b));
    }

    private static double norm(Map<Long, Double> v) {
        double sum = 0;
        for (double x : v.values()) sum += x * x;
        return Math.sqrt(sum);
    }
}
