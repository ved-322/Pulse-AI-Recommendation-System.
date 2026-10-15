package com.recsys;

import com.recsys.config.AppContext;
import com.recsys.config.DataSeeder;
import com.recsys.dao.RecommendationLogDao;
import com.recsys.engine.RecommendationSettings;
import com.recsys.exception.AuthenticationException;
import com.recsys.exception.ConflictException;
import com.recsys.exception.ForbiddenException;
import com.recsys.exception.NotFoundException;
import com.recsys.exception.ValidationException;
import com.recsys.model.InteractionType;
import com.recsys.model.RecOutcome;
import com.recsys.model.Recommendation;
import com.recsys.model.Role;
import com.recsys.model.User;
import com.recsys.service.RecommendationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end tests of services + DAOs against a real (in-memory) JDBC database. */
class IntegrationTest {
    private static AppContext app;

    @BeforeAll
    static void start() {
        app = new AppContext("jdbc:h2:mem:itest;DB_CLOSE_DELAY=-1", true);
    }

    @AfterAll
    static void stop() {
        app.close();
    }

    @Test
    void schemaAndSeedDataAreLoaded() {
        assertEquals(251, app.items.byId().size(), "catalogue should hold the 251 products that have real pictures");
        assertEquals(14, app.items.categories().size());
        assertTrue(app.users.listAll().size() >= 50);
    }

    @Test
    void demoAccountsCanSignIn() {
        User admin = app.users.login(DataSeeder.ADMIN_EMAIL, DataSeeder.ADMIN_PASSWORD);
        assertEquals(Role.ADMIN, admin.role());
        assertNotNull(app.users.login(DataSeeder.DEMO_EMAIL, DataSeeder.DEMO_PASSWORD));
    }

    @Test
    void wrongPasswordIsRejectedAndEventuallyLocked() {
        for (int i = 0; i < 5; i++) {
            assertThrows(AuthenticationException.class, () -> app.users.login("lock.me@example.com", "Wrong123"));
        }
        assertThrows(com.recsys.exception.TooManyRequestsException.class,
                () -> app.users.login("lock.me@example.com", "Wrong123"));
    }

    @Test
    void registrationCreatesUserWithPreferencesAndRejectsDuplicates() {
        User u = app.users.register("Test Person", "Test.Person@Example.com", "Passw0rd1", List.of("Books", "Nope"));
        assertEquals("test.person@example.com", u.email());
        assertEquals(Map.of("Books", 4), app.preferences.view(u.id()).get("categories"));
        assertThrows(ConflictException.class,
                () -> app.users.register("Other", "test.person@example.com", "Passw0rd1", List.of()));
        assertThrows(ValidationException.class,
                () -> app.users.register("X", "bad", "pw", List.of()));
    }

    @Test
    void failedTransactionRollsBackEveryStatement() {
        long before = app.db.read(c -> new com.recsys.dao.UserDao().count(c));
        assertThrows(IllegalStateException.class, () -> app.db.inTransaction(c -> {
            new com.recsys.dao.UserDao().insert(c, "Ghost", "ghost@example.com", "h", Role.USER);
            throw new IllegalStateException("boom");
        }));
        long after = app.db.read(c -> new com.recsys.dao.UserDao().count(c));
        assertEquals(before, after);
    }

    @Test
    void preferenceUpdatePersistsAndValidates() {
        User u = app.users.register("Pref User", "pref.user@example.com", "Passw0rd1", List.of());
        app.preferences.update(u.id(), "Pref Renamed", Map.of("Fitness & Outdoors", 5.0, "Home & Kitchen", 0.0), List.of("Running", "yoga"));
        Map<String, Object> view = app.preferences.view(u.id());
        assertEquals("Pref Renamed", view.get("name"));
        assertEquals(Map.of("Fitness & Outdoors", 5), view.get("categories")); // weight 0 is not stored
        assertEquals(2, ((java.util.Collection<?>) view.get("tags")).size());

        assertThrows(ValidationException.class,
                () -> app.preferences.update(u.id(), "Pref", Map.of("Fitness & Outdoors", 9.0), List.of()));
        assertThrows(ValidationException.class,
                () -> app.preferences.update(u.id(), "Pref", Map.of("Unknown", 3.0), List.of()));
        assertEquals("Pref Renamed", app.preferences.view(u.id()).get("name"), "rejected update must change nothing");
    }

    @Test
    void recommendationsAreLoggedAndInteractionsUpdateOutcome() {
        User alice = app.users.login(DataSeeder.DEMO_EMAIL, DataSeeder.DEMO_PASSWORD);
        RecommendationService.Result result = app.recommendations.recommendFor(alice);
        assertFalse(result.recommendations().isEmpty());
        assertTrue(result.recommendations().size() <= app.settings.current().maxResults());

        Recommendation first = result.recommendations().get(0);
        RecommendationLogDao logDao = new RecommendationLogDao();
        assertEquals(RecOutcome.SHOWN, app.db.read(c -> logDao.findLatest(c, alice.id(), first.item().id())).orElseThrow().outcome());

        app.interactions.record(alice.id(), first.item().id(), InteractionType.LIKE);
        assertEquals(RecOutcome.LIKE, app.db.read(c -> logDao.findLatest(c, alice.id(), first.item().id())).orElseThrow().outcome());

        app.interactions.record(alice.id(), first.item().id(), InteractionType.PURCHASE);
        List<Long> after = app.recommendations.recommendFor(alice).recommendations().stream()
                .map(r -> r.item().id()).toList();
        assertFalse(after.contains(first.item().id()), "purchased items must disappear from recommendations");

        assertThrows(NotFoundException.class, () -> app.interactions.record(alice.id(), 99_999, InteractionType.CLICK));
    }


    @Test
    void reviewsAreStoredSummarisedAndFeedTheRecommender() {
        User u = app.users.register("Review Writer", "review.writer@example.com", "Passw0rd1", List.of());
        long itemId = app.items.byId().values().iterator().next().id();
        assertThrows(ValidationException.class, () -> app.reviews.write(u, itemId, 9, "far too many stars"));
        assertThrows(ValidationException.class, () -> app.reviews.write(u, itemId, 5, "x"));

        app.reviews.write(u, itemId, 5, "Brilliant product, would buy again.");
        app.reviews.write(u, itemId, 4, "Updated: still great after a month.");   // one review per user per item
        var mine = app.reviews.forItem(itemId).stream().filter(r -> r.userId() == u.id()).toList();
        assertEquals(1, mine.size());
        assertEquals(4, mine.get(0).rating());
        assertFalse(mine.get(0).sample(), "user-written reviews are not flagged as samples");
        assertTrue(app.reviews.forItem(itemId).stream().anyMatch(com.recsys.model.Review::sample), "seeded reviews are labelled as samples");
        assertTrue(app.reviews.summaries().containsKey(itemId));

        // a 4-5 star review counts as a LIKE signal, so the recommender learns from it
        long likes = app.db.read(c -> new com.recsys.dao.InteractionDao().findByUser(c, u.id())).stream()
                .filter(i -> i.type() == InteractionType.LIKE).count();
        assertEquals(2, likes);
        assertThrows(NotFoundException.class, () -> app.reviews.forItem(99_999));
    }
    @Test
    void newUserGetsColdStartRecommendations() {
        User u = app.users.register("Cold Start", "cold.start@example.com", "Passw0rd1", List.of());
        RecommendationService.Result r = app.recommendations.recommendFor(u);
        assertTrue(r.coldStart());
        assertFalse(r.recommendations().isEmpty());
    }

    @Test
    void settingsAreValidatedPersistedAndResettable() {
        assertThrows(ValidationException.class, () -> app.settings.update(Map.of("maxResults", 500.0)));
        assertThrows(ValidationException.class, () -> app.settings.update(Map.of("bogus", 1.0)));
        RecommendationSettings updated = app.settings.update(Map.of("maxResults", 5.0, "weightPopularity", 0.4));
        assertEquals(5, updated.maxResults());
        assertEquals(5, app.settings.current().maxResults());
        assertTrue(app.recommendations.recommendFor(app.users.login(DataSeeder.DEMO_EMAIL, DataSeeder.DEMO_PASSWORD))
                .recommendations().size() <= 5, "new max-results setting must apply immediately");
        app.settings.reset();
        assertEquals(12, app.settings.current().maxResults());
    }

    @Test
    void analyticsReportContainsKpisSeriesAndSuggestions() {
        Map<String, Object> report = app.analytics.report(14);
        @SuppressWarnings("unchecked") Map<String, Object> kpis = (Map<String, Object>) report.get("kpis");
        assertTrue(((Number) kpis.get("impressions")).longValue() > 100);
        assertEquals(14, ((List<?>) report.get("daily")).size());
        assertEquals(3, ((List<?>) report.get("bySource")).size());
        assertFalse(((List<?>) report.get("suggestions")).isEmpty());
    }

    @Test
    void adminSafeguardsProtectTheLastAdministrator() {
        User admin = app.users.login(DataSeeder.ADMIN_EMAIL, DataSeeder.ADMIN_PASSWORD);
        assertThrows(ForbiddenException.class, () -> app.users.delete(admin.id(), admin.id()));
        assertThrows(ForbiddenException.class, () -> app.users.updateByAdmin(admin.id(), admin.id(), Role.USER, null));

        User victim = app.users.register("To Delete", "to.delete@example.com", "Passw0rd1", List.of());
        User deactivated = app.users.updateByAdmin(admin.id(), victim.id(), null, false);
        assertFalse(deactivated.active());
        assertThrows(ForbiddenException.class, () -> app.users.login("to.delete@example.com", "Passw0rd1"));
        app.users.delete(admin.id(), victim.id());
        assertThrows(NotFoundException.class, () -> app.users.require(victim.id()));
    }
}
