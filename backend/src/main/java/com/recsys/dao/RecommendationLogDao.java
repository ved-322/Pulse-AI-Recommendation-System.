package com.recsys.dao;

import com.recsys.model.RecOutcome;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Stores every recommendation shown to a user and what happened to it - the basis of the analytics. */
public class RecommendationLogDao {

    /** Aggregated counters for one group (a source, a category, a day, an item ...). */
    public record GroupStat(String key, long impressions, long clicks, long likes, long purchases, long dismissed) { }

    /** Whitelisted GROUP BY expressions - never built from user input. */
    public enum GroupBy {
        SOURCE("l.source"),
        CATEGORY("i.category"),
        DAY("CAST(l.shown_at AS DATE)"),
        ITEM("i.name");

        private final String expression;

        GroupBy(String expression) { this.expression = expression; }
    }

    public record LogRow(long id, RecOutcome outcome) { }

    public void insert(Connection c, long userId, long itemId, double score, String source,
                       RecOutcome outcome, long shownAt, Long outcomeAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO recommendation_log(user_id, item_id, score, source, outcome, shown_at, outcome_at) "
                        + "VALUES (?,?,?,?,?,?,?)")) {
            ps.setLong(1, userId);
            ps.setLong(2, itemId);
            ps.setDouble(3, score);
            ps.setString(4, source);
            ps.setString(5, outcome.name());
            ps.setTimestamp(6, new Timestamp(shownAt));
            ps.setTimestamp(7, outcomeAt == null ? null : new Timestamp(outcomeAt));
            ps.executeUpdate();
        }
    }

    public Set<Long> itemsShownSince(Connection c, long userId, long sinceMillis) throws SQLException {
        Set<Long> out = new HashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT DISTINCT item_id FROM recommendation_log WHERE user_id = ? AND shown_at >= ?")) {
            ps.setLong(1, userId);
            ps.setTimestamp(2, new Timestamp(sinceMillis));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getLong(1));
            }
        }
        return out;
    }

    public Optional<LogRow> findLatest(Connection c, long userId, long itemId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id, outcome FROM recommendation_log WHERE user_id = ? AND item_id = ? "
                        + "ORDER BY shown_at DESC, id DESC LIMIT 1")) {
            ps.setLong(1, userId);
            ps.setLong(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                        ? Optional.of(new LogRow(rs.getLong(1), RecOutcome.valueOf(rs.getString(2))))
                        : Optional.empty();
            }
        }
    }

    public void updateOutcome(Connection c, long id, RecOutcome outcome, long at) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE recommendation_log SET outcome = ?, outcome_at = ? WHERE id = ?")) {
            ps.setString(1, outcome.name());
            ps.setTimestamp(2, new Timestamp(at));
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    public int deleteUnengagedOlderThan(Connection c, long cutoffMillis) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM recommendation_log WHERE outcome = 'SHOWN' AND shown_at < ?")) {
            ps.setTimestamp(1, new Timestamp(cutoffMillis));
            return ps.executeUpdate();
        }
    }

    public List<GroupStat> stats(Connection c, GroupBy groupBy, long sinceMillis, int limit) throws SQLException {
        String expr = groupBy.expression;
        String sql = "SELECT " + expr + " AS k, COUNT(*) AS imp, "
                + "SUM(CASE WHEN l.outcome IN ('CLICK','LIKE','PURCHASE') THEN 1 ELSE 0 END) AS clicks, "
                + "SUM(CASE WHEN l.outcome IN ('LIKE','PURCHASE') THEN 1 ELSE 0 END) AS likes, "
                + "SUM(CASE WHEN l.outcome = 'PURCHASE' THEN 1 ELSE 0 END) AS purchases, "
                + "SUM(CASE WHEN l.outcome = 'DISMISSED' THEN 1 ELSE 0 END) AS dismissed "
                + "FROM recommendation_log l JOIN items i ON i.id = l.item_id "
                + "WHERE l.shown_at >= ? GROUP BY " + expr + " "
                + (groupBy == GroupBy.DAY ? "ORDER BY k" : "ORDER BY clicks DESC, imp DESC")
                + " LIMIT ?";
        List<GroupStat> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(sinceMillis));
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new GroupStat(rs.getString("k"), rs.getLong("imp"), rs.getLong("clicks"),
                            rs.getLong("likes"), rs.getLong("purchases"), rs.getLong("dismissed")));
                }
            }
        }
        return out;
    }

    public long countActiveUsersSince(Connection c, long sinceMillis) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(DISTINCT user_id) FROM recommendation_log WHERE shown_at >= ?")) {
            ps.setTimestamp(1, new Timestamp(sinceMillis));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
