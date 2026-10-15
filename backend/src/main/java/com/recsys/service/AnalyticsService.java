package com.recsys.service;

import com.recsys.config.Database;
import com.recsys.dao.ItemDao;
import com.recsys.dao.RecommendationLogDao;
import com.recsys.dao.RecommendationLogDao.GroupBy;
import com.recsys.dao.RecommendationLogDao.GroupStat;
import com.recsys.dao.UserDao;
import com.recsys.engine.RecommendationSettings;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Recommendation-effectiveness analysis for the admin dashboard: KPIs, trends, per-algorithm and
 * per-category performance, plus rule-based tuning suggestions the admin can apply in one click.
 */
public class AnalyticsService {
    private static final long DAY_MILLIS = 86_400_000L;
    private static final long MIN_IMPRESSIONS_FOR_ADVICE = 15;

    private final Database db;
    private final RecommendationLogDao logDao;
    private final UserDao userDao;
    private final ItemDao itemDao;
    private final SettingsService settings;

    public AnalyticsService(Database db, RecommendationLogDao logDao, UserDao userDao, ItemDao itemDao,
                            SettingsService settings) {
        this.db = db;
        this.logDao = logDao;
        this.userDao = userDao;
        this.itemDao = itemDao;
        this.settings = settings;
    }

    public Map<String, Object> report(int days) {
        int window = Math.max(1, Math.min(days, 90));
        long since = startOfDay(LocalDate.now().minusDays(window - 1L));

        Map<String, Object> data = db.read(c -> {
            Map<String, Object> out = new LinkedHashMap<>();
            List<GroupStat> bySource = logDao.stats(c, GroupBy.SOURCE, since, 10);
            List<GroupStat> byCategory = logDao.stats(c, GroupBy.CATEGORY, since, 20);
            List<GroupStat> daily = logDao.stats(c, GroupBy.DAY, since, 400);
            List<GroupStat> topItems = logDao.stats(c, GroupBy.ITEM, since, 5);

            out.put("kpis", kpis(bySource, userDao.count(c), itemDao.count(c), logDao.countActiveUsersSince(c, since)));
            out.put("bySource", bySource.stream().map(AnalyticsService::toMap).toList());
            out.put("byCategory", byCategory.stream().map(AnalyticsService::toMap).toList());
            out.put("topItems", topItems.stream().map(AnalyticsService::toMap).toList());
            out.put("daily", fillDays(daily, window));
            out.put("_sources", bySource);
            out.put("_categories", byCategory);
            return out;
        });

        @SuppressWarnings("unchecked") List<GroupStat> sources = (List<GroupStat>) data.remove("_sources");
        @SuppressWarnings("unchecked") List<GroupStat> categories = (List<GroupStat>) data.remove("_categories");
        data.put("windowDays", window);
        data.put("suggestions", suggestions(sources, categories, settings.current()));
        return data;
    }

    // ------------------------------------------------------------------ KPI + series building

    private Map<String, Object> kpis(List<GroupStat> bySource, long users, long items, long activeUsers) {
        long imp = bySource.stream().mapToLong(GroupStat::impressions).sum();
        long clicks = bySource.stream().mapToLong(GroupStat::clicks).sum();
        long likes = bySource.stream().mapToLong(GroupStat::likes).sum();
        long purchases = bySource.stream().mapToLong(GroupStat::purchases).sum();
        long dismissed = bySource.stream().mapToLong(GroupStat::dismissed).sum();
        Map<String, Object> k = new LinkedHashMap<>();
        k.put("impressions", imp);
        k.put("clicks", clicks);
        k.put("ctr", rate(clicks, imp));
        k.put("likeRate", rate(likes, imp));
        k.put("conversionRate", rate(purchases, imp));
        k.put("dismissRate", rate(dismissed, imp));
        k.put("users", users);
        k.put("activeUsers", activeUsers);
        k.put("items", items);
        return k;
    }

    private List<Map<String, Object>> fillDays(List<GroupStat> rows, int window) {
        Map<String, GroupStat> byDay = rows.stream().collect(Collectors.toMap(GroupStat::key, g -> g, (a, b) -> a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = window - 1; i >= 0; i--) {
            String day = LocalDate.now().minusDays(i).toString();
            GroupStat g = byDay.getOrDefault(day, new GroupStat(day, 0, 0, 0, 0, 0));
            out.add(toMap(g));
        }
        return out;
    }

    private static Map<String, Object> toMap(GroupStat g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", g.key());
        m.put("impressions", g.impressions());
        m.put("clicks", g.clicks());
        m.put("likes", g.likes());
        m.put("purchases", g.purchases());
        m.put("dismissed", g.dismissed());
        m.put("ctr", rate(g.clicks(), g.impressions()));
        m.put("dismissRate", rate(g.dismissed(), g.impressions()));
        return m;
    }

    private static double rate(long part, long whole) {
        return whole == 0 ? 0 : Math.round(part * 10_000.0 / whole) / 10_000.0;
    }

    private static long startOfDay(LocalDate d) {
        return d.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // ------------------------------------------------------------------ suggestions

    private List<Map<String, Object>> suggestions(List<GroupStat> sources, List<GroupStat> categories,
                                                  RecommendationSettings s) {
        List<Map<String, Object>> out = new ArrayList<>();
        long imp = sources.stream().mapToLong(GroupStat::impressions).sum();
        long clicks = sources.stream().mapToLong(GroupStat::clicks).sum();
        long dismissed = sources.stream().mapToLong(GroupStat::dismissed).sum();

        if (imp < MIN_IMPRESSIONS_FOR_ADVICE) {
            out.add(suggestion("info", "Not enough data yet",
                    "Only " + imp + " recommendations were shown in this period. Suggestions appear after ~"
                            + MIN_IMPRESSIONS_FOR_ADVICE + " impressions.", null));
            return out;
        }

        reweightSuggestion(sources, s).ifPresent(out::add);

        double dismissRate = rate(dismissed, imp);
        if (dismissRate > 0.12 && s.minScore() < 0.4) {
            double next = Math.min(0.4, Math.round((s.minScore() + 0.05) * 100) / 100.0);
            out.add(suggestion("warn", "Raise the minimum score",
                    "Users dismiss " + pct(dismissRate) + " of recommendations. A stricter cut-off of " + next
                            + " filters low-confidence matches.", Map.of("minScore", next)));
        }

        double ctr = rate(clicks, imp);
        if (ctr < 0.15 && s.maxPerCategory() > 2) {
            out.add(suggestion("warn", "Tighten category diversity",
                    "Click-through is only " + pct(ctr) + ". Limiting a category to "
                            + (s.maxPerCategory() - 1) + " items per list can surface fresher options.",
                    Map.of("maxPerCategory", s.maxPerCategory() - 1)));
        }

        categories.stream().filter(g -> g.impressions() >= MIN_IMPRESSIONS_FOR_ADVICE)
                .max(Comparator.comparingDouble(g -> rate(g.dismissed(), g.impressions())))
                .filter(g -> rate(g.dismissed(), g.impressions()) > 0.15)
                .ifPresent(g -> out.add(suggestion("info", g.key() + " is dismissed most often",
                        pct(rate(g.dismissed(), g.impressions())) + " of " + g.key()
                                + " recommendations are dismissed. Review catalogue quality or user interest in this category.", null)));

        if (ctr >= 0.25) {
            out.add(suggestion("good", "Recommendations are performing well",
                    "A click-through rate of " + pct(ctr) + " is healthy. Keep monitoring after any change.", null));
        }
        if (out.isEmpty()) {
            out.add(suggestion("good", "No changes recommended", "Current settings look balanced for the selected period.", null));
        }
        return out;
    }

    /** Shifts weight toward the signal with the best click-through rate (blended 50/50 with today's weights). */
    private java.util.Optional<Map<String, Object>> reweightSuggestion(List<GroupStat> sources, RecommendationSettings s) {
        Map<String, GroupStat> by = sources.stream().collect(Collectors.toMap(GroupStat::key, g -> g, (a, b) -> a));
        String[] names = {"content", "collaborative", "popularity"};
        double[] current = {s.contentShare(), s.collaborativeShare(), s.popularityShare()};
        double[] target = new double[3];
        double sum = 0;
        for (int i = 0; i < 3; i++) {
            GroupStat g = by.get(names[i]);
            double c = g == null || g.impressions() < MIN_IMPRESSIONS_FOR_ADVICE ? 0.15 : rate(g.clicks(), g.impressions());
            target[i] = c + 0.02;
            sum += target[i];
        }
        double[] proposed = new double[3];
        double maxShift = 0;
        for (int i = 0; i < 3; i++) {
            proposed[i] = Math.round((0.5 * current[i] + 0.5 * target[i] / sum) * 100) / 100.0;
            maxShift = Math.max(maxShift, Math.abs(proposed[i] - current[i]));
        }
        if (maxShift < 0.05) return java.util.Optional.empty();

        int best = 0;
        for (int i = 1; i < 3; i++) if (target[i] > target[best]) best = i;
        Map<String, Object> apply = new LinkedHashMap<>();
        apply.put("weightContent", proposed[0]);
        apply.put("weightCollaborative", proposed[1]);
        apply.put("weightPopularity", proposed[2]);
        return java.util.Optional.of(suggestion("tip", "Shift weight toward " + names[best] + " signals",
                names[best].substring(0, 1).toUpperCase() + names[best].substring(1) + " recommendations convert best ("
                        + pct(rate(by.get(names[best]) == null ? 0 : by.get(names[best]).clicks(),
                        by.get(names[best]) == null ? 1 : by.get(names[best]).impressions()))
                        + " CTR). Suggested weights: content " + proposed[0] + ", collaborative " + proposed[1]
                        + ", popularity " + proposed[2] + ".", apply));
    }

    private static Map<String, Object> suggestion(String level, String title, String detail, Map<String, ?> apply) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("title", title);
        m.put("detail", detail);
        if (apply != null) m.put("apply", apply);
        return m;
    }

    private static String pct(double v) {
        return Math.round(v * 1000) / 10.0 + "%";
    }
}
