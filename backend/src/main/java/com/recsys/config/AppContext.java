package com.recsys.config;

import com.recsys.dao.InteractionDao;
import com.recsys.dao.ItemDao;
import com.recsys.dao.PreferenceDao;
import com.recsys.dao.RecommendationLogDao;
import com.recsys.dao.ReviewDao;
import com.recsys.service.ReviewService;
import com.recsys.dao.SettingsDao;
import com.recsys.dao.UserDao;
import com.recsys.engine.RecommendationEngine;
import com.recsys.service.AnalyticsService;
import com.recsys.service.InteractionService;
import com.recsys.service.ItemService;
import com.recsys.service.PreferenceService;
import com.recsys.service.RecommendationService;
import com.recsys.service.SettingsService;
import com.recsys.service.UserService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Composition root: builds the whole object graph once (manual dependency injection).
 * Servlets receive services from here, services receive DAOs, DAOs are stateless.
 */
public class AppContext implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(AppContext.class.getName());

    public final Database db;
    public final ItemService items;
    public final SettingsService settings;
    public final UserService users;
    public final PreferenceService preferences;
    public final RecommendationService recommendations;
    public final InteractionService interactions;
    public final AnalyticsService analytics;
    public final ReviewService reviews;
    private final RecommendationEngine engine;
    private final ScheduledExecutorService maintenance;

    public AppContext(String jdbcUrl, boolean seedDemoData) {
        this.db = new Database(jdbcUrl);
        new DatabaseInitializer(db).run();

        UserDao userDao = new UserDao();
        ItemDao itemDao = new ItemDao();
        PreferenceDao prefDao = new PreferenceDao();
        InteractionDao interactionDao = new InteractionDao();
        RecommendationLogDao logDao = new RecommendationLogDao();
        SettingsDao settingsDao = new SettingsDao();

        this.items = new ItemService(db, itemDao);
        this.settings = new SettingsService(db, settingsDao);
        this.users = new UserService(db, userDao, prefDao, items);
        this.preferences = new PreferenceService(db, prefDao, userDao, items);
        this.engine = new RecommendationEngine();
        this.recommendations = new RecommendationService(db, prefDao, interactionDao, logDao, items, settings, engine);
        this.interactions = new InteractionService(db, interactionDao, logDao, items);
        this.analytics = new AnalyticsService(db, logDao, userDao, itemDao, settings);
        ReviewDao reviewDao = new ReviewDao();
        this.reviews = new ReviewService(db, reviewDao, interactionDao, logDao, items);

        if (seedDemoData) new DataSeeder(db, items).seedIfEmpty();

        // Background thread: hourly housekeeping of old, never-engaged impressions.
        this.maintenance = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "maintenance");
            t.setDaemon(true);
            return t;
        });
        this.maintenance.scheduleWithFixedDelay(() -> {
            try {
                long cutoff = System.currentTimeMillis() - 90L * 86_400_000L;
                int removed = db.inTransaction(c -> logDao.deleteUnengagedOlderThan(c, cutoff));
                if (removed > 0) LOG.info("Maintenance removed " + removed + " stale impression rows");
            } catch (RuntimeException e) {
                LOG.warning("Maintenance job failed: " + e.getMessage());
            }
        }, 1, 60, TimeUnit.MINUTES);
    }

    @Override
    public void close() {
        maintenance.shutdownNow();
        engine.close();
        db.close();
    }
}
