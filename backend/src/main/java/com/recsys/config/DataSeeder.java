package com.recsys.config;

import com.recsys.dao.InteractionDao;
import com.recsys.dao.PreferenceDao;
import com.recsys.dao.RecommendationLogDao;
import com.recsys.dao.ReviewDao;
import com.recsys.dao.UserDao;
import com.recsys.model.InteractionType;
import com.recsys.model.Item;
import com.recsys.model.RecOutcome;
import com.recsys.model.Role;
import com.recsys.service.ItemService;
import com.recsys.util.PasswordHasher;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates demo accounts plus a realistic behaviour history so the dashboards and the recommender are
 * meaningful on first launch. Uses a fixed random seed => reproducible demo data. Runs only on an empty database.
 * All seeded reviews are flagged {@code sample} so the UI labels them as sample reviews.
 */
public class DataSeeder {
    public static final String ADMIN_EMAIL = "admin@recsys.com";
    public static final String ADMIN_PASSWORD = "Admin@123";
    public static final String DEMO_EMAIL = "alice@example.com";
    public static final String DEMO_PASSWORD = "User@123";

    private record Archetype(String label, List<String> categories, List<String> tags) { }

    private static final List<Archetype> ARCHETYPES = List.of(
            new Archetype("tech", List.of("Phones & Tablets", "Laptops & PCs", "Wearables & Smart Home", "Gaming"),
                    List.of("smartphone", "programming", "wireless", "smart-home")),
            new Archetype("audiophile", List.of("Audio", "Phones & Tablets", "Movies"),
                    List.of("headphones", "noise-cancelling", "wireless", "music")),
            new Archetype("creator", List.of("Cameras", "Laptops & PCs", "Audio"),
                    List.of("photography", "video", "creator", "vlogging")),
            new Archetype("gamer", List.of("Gaming", "Laptops & PCs", "Toys & Hobbies"),
                    List.of("gaming", "rgb", "console", "esports")),
            new Archetype("bookworm", List.of("Books", "Movies", "Food & Drink"),
                    List.of("sci-fi", "classic", "fiction", "philosophy")),
            new Archetype("cinephile", List.of("Movies", "Books", "Home & Kitchen"),
                    List.of("drama", "epic", "animation", "bollywood")),
            new Archetype("athlete", List.of("Fitness & Outdoors", "Wearables & Smart Home", "Food & Drink"),
                    List.of("running", "fitness", "outdoor", "healthy")),
            new Archetype("homebody", List.of("Home & Kitchen", "Food & Drink", "Beauty & Wellness"),
                    List.of("coffee", "kitchen", "relax", "gourmet")),
            new Archetype("stylist", List.of("Fashion", "Beauty & Wellness", "Wearables & Smart Home"),
                    List.of("sneakers", "streetwear", "skincare", "stylish")),
            new Archetype("family", List.of("Toys & Hobbies", "Movies", "Food & Drink"),
                    List.of("family", "animation", "board-game", "creative")));

    private static final String[] FIRST = {"Aarav", "Maya", "Liam", "Sofia", "Noah", "Isha", "Ethan", "Zara", "Kabir",
            "Emma", "Rohan", "Olivia", "Arjun", "Mia", "Dev", "Ava", "Kian", "Nina", "Omar", "Leah", "Ravi", "Tara",
            "Jon", "Priya", "Ananya", "Vikram", "Meera", "Sam", "Lucas", "Diya", "Neil", "Aisha", "Rhea", "Karan",
            "Chloe", "Yash", "Sara", "Ishaan", "Naomi", "Tanvi", "Reyansh", "Kiara", "Jay", "Anika", "Mohan", "Elena",
            "Harsh", "Zoe", "Aditya", "Lina"};

    private static final String[][] REVIEW_TEXT = {
            {}, // index 0 unused
            {"Disappointed with the %s. It did not live up to what I expected.", "Could not get on with the %s - returning to my old one.", "The %s was not for me. Had high hopes but it fell short."},
            {"The %s is below what I expected for the price. Some good points, but I would look elsewhere.", "Not quite right. The %s has issues I could not ignore.", "Mixed feelings - the %s felt underwhelming."},
            {"Decent. The %s does the job, though nothing special.", "An okay choice - the %s is fine for the price but not outstanding.", "Average experience with the %s. Fine, not memorable."},
            {"Really good. The %s does what it promises and I am happy with it.", "Solid pick - the %s has been reliable and well worth it.", "Very happy with the %s. A couple of small niggles, but overall great."},
            {"Absolutely love the %s. It exceeded my expectations and I use it all the time.", "Brilliant - the %s is easily one of my best buys. Highly recommended.", "Outstanding. The %s is worth every rupee."}};

    private final Database db;
    private final UserDao userDao = new UserDao();
    private final PreferenceDao prefDao = new PreferenceDao();
    private final InteractionDao interactionDao = new InteractionDao();
    private final RecommendationLogDao logDao = new RecommendationLogDao();
    private final ReviewDao reviewDao = new ReviewDao();
    private final ItemService items;

    public DataSeeder(Database db, ItemService items) {
        this.db = db;
        this.items = items;
    }

    public void seedIfEmpty() {
        if (db.read(userDao::count) > 0) return;
        Random rnd = new Random(42);
        String sharedHash = PasswordHasher.hash(DEMO_PASSWORD); // hashing once keeps start-up fast
        long now = System.currentTimeMillis();
        List<Item> catalogue = new ArrayList<>(items.byId().values());

        db.inTransaction(c -> {
            long adminId = userDao.insert(c, "Admin", ADMIN_EMAIL, PasswordHasher.hash(ADMIN_PASSWORD), Role.ADMIN);

            Archetype tech = ARCHETYPES.get(0);
            long alice = userDao.insert(c, "Alice Demo", DEMO_EMAIL, sharedHash, Role.USER);
            prefDao.replaceAll(c, alice, prefsFor(tech, rnd), new LinkedHashSet<>(tech.tags().subList(0, 2)));
            seedActivity(c, alice, tech, catalogue, 10, now, rnd);

            for (int i = 0; i < FIRST.length; i++) {
                Archetype a = ARCHETYPES.get(i % ARCHETYPES.size());
                String name = FIRST[i] + " " + (char) ('A' + rnd.nextInt(26)) + ".";
                String email = FIRST[i].toLowerCase() + (i + 1) + "@example.com";
                long id = userDao.insert(c, name, email, sharedHash, Role.USER);
                prefDao.replaceAll(c, id, prefsFor(a, rnd), new LinkedHashSet<>(pickTags(a, rnd)));
                seedActivity(c, id, a, catalogue, 24 + rnd.nextInt(20), now, rnd);
                seedRecommendationHistory(c, id, a, catalogue, 40, now, rnd);
            }
            return adminId;
        });
    }

    private Map<String, Integer> prefsFor(Archetype a, Random rnd) {
        Map<String, Integer> prefs = new LinkedHashMap<>();
        for (String cat : a.categories()) prefs.put(cat, 3 + rnd.nextInt(3));
        return prefs;
    }

    private List<String> pickTags(Archetype a, Random rnd) {
        List<String> tags = new ArrayList<>(a.tags());
        Collections.shuffle(tags, rnd);
        return tags.subList(0, 2);
    }

    /** Past interactions, plus a flagged sample review for roughly a third of them. */
    private void seedActivity(Connection c, long userId, Archetype a, List<Item> catalogue,
                              int count, long now, Random rnd) throws SQLException {
        Set<Long> used = new LinkedHashSet<>();
        for (int n = 0; n < count; n++) {
            Item item = pickItem(a, catalogue, rnd);
            if (!used.add(item.id())) continue;
            int roll = rnd.nextInt(100);
            InteractionType type = roll < 36 ? InteractionType.VIEW : roll < 64 ? InteractionType.CLICK
                    : roll < 82 ? InteractionType.LIKE : roll < 93 ? InteractionType.PURCHASE : InteractionType.DISMISS;
            long when = now - (long) (rnd.nextDouble() * 30 * 86_400_000L);
            interactionDao.insert(c, userId, item.id(), type, when);

            boolean reviewable = type != InteractionType.VIEW && rnd.nextInt(100) < 38;
            if (reviewable) {
                int stars = switch (type) {
                    case PURCHASE, LIKE -> rnd.nextInt(100) < 55 ? 5 : 4;
                    case CLICK -> 3 + rnd.nextInt(2);
                    default -> 1 + rnd.nextInt(2);
                };
                String[] pool = REVIEW_TEXT[stars];
                String text = String.format(pool[rnd.nextInt(pool.length)], item.name());
                reviewDao.upsert(c, userId, item.id(), stars, text, true, when + 3_600_000L);
            }
        }
    }

    /** Historic impressions with source-dependent click rates that improve slightly over time. */
    private void seedRecommendationHistory(Connection c, long userId, Archetype a, List<Item> catalogue,
                                           int count, long now, Random rnd) throws SQLException {
        String[] sources = {"content", "collaborative", "popularity"};
        double[] engage = {0.30, 0.38, 0.16};
        for (int n = 0; n < count; n++) {
            int s = rnd.nextInt(3);
            Item item = pickItem(a, catalogue, rnd);
            int dayOffset = rnd.nextInt(30);
            long shown = now - dayOffset * 86_400_000L - rnd.nextInt(80_000_000);
            double p = engage[s] * (0.85 + 0.3 * (1 - dayOffset / 30.0));
            double r = rnd.nextDouble();
            RecOutcome outcome;
            if (r < p) {
                double q = rnd.nextDouble();
                outcome = q < 0.55 ? RecOutcome.CLICK : q < 0.85 ? RecOutcome.LIKE : RecOutcome.PURCHASE;
            } else if (r < p + 0.07) {
                outcome = RecOutcome.DISMISSED;
            } else {
                outcome = RecOutcome.SHOWN;
            }
            Long outcomeAt = outcome == RecOutcome.SHOWN ? null : shown + 60_000L;
            logDao.insert(c, userId, item.id(), 0.2 + rnd.nextDouble() * 0.6, sources[s], outcome, shown, outcomeAt);
        }
    }

    private Item pickItem(Archetype a, List<Item> catalogue, Random rnd) {
        if (rnd.nextInt(100) < 80) {
            List<Item> pool = catalogue.stream().filter(i -> a.categories().contains(i.category()))
                    .collect(Collectors.toList());
            return pool.get(rnd.nextInt(pool.size()));
        }
        return catalogue.get(rnd.nextInt(catalogue.size()));
    }
}
