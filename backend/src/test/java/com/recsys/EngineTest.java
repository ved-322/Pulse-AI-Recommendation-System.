package com.recsys;

import com.recsys.engine.CollaborativeScorer;
import com.recsys.engine.ContentBasedScorer;
import com.recsys.engine.PopularityScorer;
import com.recsys.engine.RecommendationEngine;
import com.recsys.engine.RecommendationSettings;
import com.recsys.engine.UserContext;
import com.recsys.exception.ValidationException;
import com.recsys.model.Interaction;
import com.recsys.model.InteractionType;
import com.recsys.model.Item;
import com.recsys.model.Recommendation;
import com.recsys.model.Role;
import com.recsys.model.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineTest {
    private static final long NOW = System.currentTimeMillis();

    private final Map<Long, Item> items = new LinkedHashMap<>();
    private RecommendationEngine engine;

    private Item item(long id, String category, String... tags) {
        Item i = new Item(id, "item-" + id, "Item" + id, "Brand", category, "d", 10, List.of(tags), "", "x", "#000");
        items.put(id, i);
        return i;
    }

    private User user(long id) {
        return new User(id, "U" + id, "u" + id + "@x.com", "h", Role.USER, true, NOW);
    }

    private Interaction ev(long user, long item, InteractionType t) {
        return new Interaction(0, user, item, t, NOW - 3_600_000L);
    }

    @BeforeEach
    void setUp() {
        engine = new RecommendationEngine();
        item(1, "Books", "sci-fi");
        item(2, "Books", "sci-fi", "classic");
        item(3, "Fitness", "running");
        item(4, "Fitness", "yoga");
        item(5, "Food", "coffee");
    }

    @AfterEach
    void tearDown() {
        engine.close();
    }

    private UserContext ctx(User u, Map<String, Integer> prefs, Set<String> tags, Map<Long, List<Interaction>> all) {
        return new UserContext(u, prefs, tags, all.getOrDefault(u.id(), List.of()), items, all, NOW);
    }

    @Test
    void contentScorerFavoursPreferredCategoryAndTags() {
        UserContext c = ctx(user(1), Map.of("Books", 5), Set.of("sci-fi"), Map.of());
        Map<Long, Double> s = new ContentBasedScorer().score(c, RecommendationSettings.defaults());
        assertTrue(s.get(1L) > s.get(3L));
        assertEquals(1.0, s.get(2L), 1e-9, "item sharing the most features should have the top normalised score");
    }

    @Test
    void collaborativeScorerLearnsFromSimilarUsers() {
        Map<Long, List<Interaction>> all = new HashMap<>();
        all.put(1L, List.of(ev(1, 1, InteractionType.LIKE)));
        all.put(2L, List.of(ev(2, 1, InteractionType.LIKE), ev(2, 5, InteractionType.PURCHASE)));
        all.put(3L, List.of(ev(3, 3, InteractionType.LIKE), ev(3, 4, InteractionType.LIKE)));
        Map<Long, Double> s = new CollaborativeScorer().score(ctx(user(1), Map.of(), Set.of(), all),
                RecommendationSettings.defaults());
        assertTrue(s.containsKey(5L), "item loved by the similar user should be suggested");
        assertFalse(s.containsKey(3L), "dissimilar users must not influence the result");
        assertFalse(s.containsKey(1L), "already-known items are not scored");
    }

    @Test
    void popularityScorerRanksMostEngagedItemFirst() {
        Map<Long, List<Interaction>> all = new HashMap<>();
        all.put(2L, List.of(ev(2, 5, InteractionType.PURCHASE), ev(2, 3, InteractionType.VIEW)));
        all.put(3L, List.of(ev(3, 5, InteractionType.LIKE)));
        Map<Long, Double> s = new PopularityScorer().score(ctx(user(1), Map.of(), Set.of(), all),
                RecommendationSettings.defaults());
        assertEquals(1.0, s.get(5L), 1e-9);
        assertTrue(s.get(3L) < s.get(5L));
    }

    @Test
    void engineExcludesPurchasedAndDismissedItems() {
        Map<Long, List<Interaction>> all = new HashMap<>();
        all.put(1L, List.of(ev(1, 1, InteractionType.PURCHASE), ev(1, 2, InteractionType.DISMISS)));
        List<Recommendation> recs = engine.recommend(ctx(user(1), Map.of("Books", 5), Set.of("sci-fi"), all),
                RecommendationSettings.defaults());
        List<Long> ids = recs.stream().map(r -> r.item().id()).collect(Collectors.toList());
        assertFalse(ids.contains(1L));
        assertFalse(ids.contains(2L));
    }

    @Test
    void engineEnforcesCategoryDiversityAndResultLimit() {
        for (long id = 10; id < 20; id++) item(id, "Books", "sci-fi");
        RecommendationSettings s = new RecommendationSettings(1, 0, 0, 20, 0, 2, 14);
        List<Recommendation> recs = engine.recommend(ctx(user(1), Map.of("Books", 5), Set.of("sci-fi"), Map.of()), s);
        long books = recs.stream().filter(r -> r.item().category().equals("Books")).count();
        assertEquals(2, books);
    }

    @Test
    void engineSortsByScoreAndExplainsResults() {
        List<Recommendation> recs = engine.recommend(ctx(user(1), Map.of("Fitness", 5), Set.of("running"), Map.of()),
                RecommendationSettings.defaults());
        assertFalse(recs.isEmpty());
        assertEquals(3L, recs.get(0).item().id());
        for (int i = 1; i < recs.size(); i++) assertTrue(recs.get(i - 1).score() >= recs.get(i).score());
        assertFalse(recs.get(0).reason().isBlank());
    }

    @Test
    void weightsChangeTheRanking() {
        Map<Long, List<Interaction>> all = new HashMap<>();
        all.put(2L, List.of(ev(2, 5, InteractionType.PURCHASE), ev(2, 5, InteractionType.LIKE)));
        UserContext c = ctx(user(1), Map.of("Books", 5), Set.of(), all);
        List<Recommendation> contentFirst = engine.recommend(c, new RecommendationSettings(1, 0, 0, 5, 0, 5, 14));
        List<Recommendation> popularityFirst = engine.recommend(c, new RecommendationSettings(0, 0, 1, 5, 0, 5, 14));
        assertEquals("Books", contentFirst.get(0).item().category());
        assertEquals(5L, popularityFirst.get(0).item().id());
    }

    @Test
    void coldStartUserStillReceivesSuggestions() {
        Map<Long, List<Interaction>> all = new HashMap<>();
        all.put(2L, List.of(ev(2, 4, InteractionType.LIKE)));
        UserContext c = ctx(user(1), Map.of(), Set.of(), all);
        assertTrue(c.isColdStart());
        assertFalse(engine.recommend(c, RecommendationSettings.defaults()).isEmpty());
    }

    @Test
    void settingsRejectIllegalValues() {
        assertThrows(ValidationException.class, () -> new RecommendationSettings(0, 0, 0, 8, 0.1, 3, 14));
        assertThrows(ValidationException.class, () -> new RecommendationSettings(1, 0, 0, 0, 0.1, 3, 14));
        assertThrows(ValidationException.class, () -> new RecommendationSettings(1.5, 0, 0, 8, 0.1, 3, 14));
        assertEquals(1.0, RecommendationSettings.defaults().contentShare()
                + RecommendationSettings.defaults().collaborativeShare()
                + RecommendationSettings.defaults().popularityShare(), 1e-9);
    }

    @Test
    void recencyDecayHalvesAfterHalfLife() {
        UserContext c = ctx(user(1), Map.of(), Set.of(), new HashMap<>());
        long fourteenDaysAgo = NOW - 14L * 86_400_000L;
        assertEquals(0.5, c.decay(fourteenDaysAgo, 14), 1e-6);
        assertEquals(1.0, c.decay(NOW, 14), 1e-9);
        List<Long> unused = new ArrayList<>();
        assertTrue(unused.isEmpty());
    }
}
