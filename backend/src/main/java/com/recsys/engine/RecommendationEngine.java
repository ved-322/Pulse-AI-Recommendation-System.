package com.recsys.engine;

import com.recsys.exception.RecSysException;
import com.recsys.model.Interaction;
import com.recsys.model.InteractionType;
import com.recsys.model.Item;
import com.recsys.model.Recommendation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Hybrid recommender. Runs every {@link Scorer} <b>in parallel</b> on a thread pool, blends
 * the results with the admin-configured weights, removes items the user already bought or
 * dismissed, enforces category diversity and explains every suggestion.
 */
public class RecommendationEngine implements AutoCloseable {

    private static final long TIMEOUT_SECONDS = 10;

    private final List<Scorer> scorers;
    private final ExecutorService pool;

    public RecommendationEngine() {
        this(List.of(new ContentBasedScorer(), new CollaborativeScorer(), new PopularityScorer()));
    }

    public RecommendationEngine(List<Scorer> scorers) {
        this.scorers = List.copyOf(scorers);
        this.pool = Executors.newFixedThreadPool(this.scorers.size(), r -> {
            Thread t = new Thread(r, "scorer-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public List<Recommendation> recommend(UserContext ctx, RecommendationSettings settings) {
        Map<String, Map<Long, Double>> results = runScorersInParallel(ctx, settings);

        Map<String, Double> shares = new HashMap<>();
        shares.put(ContentBasedScorer.NAME, settings.contentShare());
        shares.put(CollaborativeScorer.NAME, settings.collaborativeShare());
        shares.put(PopularityScorer.NAME, settings.popularityShare());

        Set<Long> excluded = excludedItems(ctx);
        List<Recommendation> candidates = new ArrayList<>();

        for (Item item : ctx.items().values()) {
            if (excluded.contains(item.id())) continue;

            Map<String, Double> breakdown = new LinkedHashMap<>();
            double total = 0;
            String bestSource = PopularityScorer.NAME;
            double bestContribution = -1;
            for (Scorer scorer : scorers) {
                double raw = results.get(scorer.name()).getOrDefault(item.id(), 0.0);
                double weighted = raw * shares.getOrDefault(scorer.name(), 0.0);
                breakdown.put(scorer.name(), round(weighted));
                total += weighted;
                if (weighted > bestContribution) {
                    bestContribution = weighted;
                    bestSource = scorer.name();
                }
            }
            total = Math.min(1.0, total);
            if (total < settings.minScore()) continue;
            candidates.add(new Recommendation(item, round(total), (int) Math.round(total * 100),
                    bestSource, explain(bestSource, item, ctx), breakdown));
        }

        candidates.sort(Comparator.comparingDouble(Recommendation::score).reversed()
                .thenComparing(r -> r.item().name()));
        return diversify(candidates, settings);
    }

    private Map<String, Map<Long, Double>> runScorersInParallel(UserContext ctx, RecommendationSettings settings) {
        Map<String, CompletableFuture<Map<Long, Double>>> futures = new LinkedHashMap<>();
        for (Scorer scorer : scorers) {
            futures.put(scorer.name(), CompletableFuture.supplyAsync(() -> scorer.score(ctx, settings), pool));
        }
        Map<String, Map<Long, Double>> out = new HashMap<>();
        try {
            for (Map.Entry<String, CompletableFuture<Map<Long, Double>>> e : futures.entrySet()) {
                out.put(e.getKey(), e.getValue().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RecSysException(500, "ENGINE_INTERRUPTED", "Recommendation was interrupted.", ex);
        } catch (ExecutionException | TimeoutException ex) {
            futures.values().forEach(f -> f.cancel(true));
            throw new RecSysException(500, "ENGINE_FAILURE", "Could not compute recommendations.", ex);
        }
        return out;
    }

    /** Items the user has already bought or explicitly dismissed are never recommended again. */
    private Set<Long> excludedItems(UserContext ctx) {
        Set<Long> excluded = new HashSet<>();
        for (Interaction in : ctx.history()) {
            if (in.type() == InteractionType.PURCHASE || in.type() == InteractionType.DISMISS) {
                excluded.add(in.itemId());
            }
        }
        return excluded;
    }

    /** Takes the best items while capping how many may come from the same category. */
    private List<Recommendation> diversify(List<Recommendation> sorted, RecommendationSettings settings) {
        Map<String, Integer> perCategory = new HashMap<>();
        List<Recommendation> picked = new ArrayList<>();
        for (Recommendation r : sorted) {
            int used = perCategory.getOrDefault(r.item().category(), 0);
            if (used >= settings.maxPerCategory()) continue;
            perCategory.put(r.item().category(), used + 1);
            picked.add(r);
            if (picked.size() >= settings.maxResults()) break;
        }
        return picked;
    }

    private String explain(String source, Item item, UserContext ctx) {
        return switch (source) {
            case ContentBasedScorer.NAME -> {
                List<String> shared = item.tags().stream().filter(ctx.tags()::contains).limit(2).collect(Collectors.toList());
                if (!shared.isEmpty()) yield "Matches your interest in " + String.join(" & ", shared);
                int pref = ctx.categoryPrefs().getOrDefault(item.category(), 0);
                yield pref >= 3 ? "Because you enjoy " + item.category()
                        : "Similar to things you've engaged with";
            }
            case CollaborativeScorer.NAME -> "Loved by people with taste like yours";
            default -> "Trending with the community right now";
        };
    }

    private static double round(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
