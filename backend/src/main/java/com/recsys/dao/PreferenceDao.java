package com.recsys.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Explicit user preferences: a 0-5 weight per category plus a set of favourite tags. */
public class PreferenceDao {

    public Map<String, Integer> categoryWeights(Connection c, long userId) throws SQLException {
        Map<String, Integer> out = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT category, weight FROM user_preferences WHERE user_id = ? ORDER BY category")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getString(1), rs.getInt(2));
            }
        }
        return out;
    }

    public Set<String> tags(Connection c, long userId) throws SQLException {
        Set<String> out = new LinkedHashSet<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT tag FROM user_tags WHERE user_id = ? ORDER BY tag")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1));
            }
        }
        return out;
    }

    /** Replaces the user's complete preference set. Must be called inside a transaction. */
    public void replaceAll(Connection c, long userId, Map<String, Integer> categories, Set<String> tags)
            throws SQLException {
        delete(c, "DELETE FROM user_preferences WHERE user_id = ?", userId);
        delete(c, "DELETE FROM user_tags WHERE user_id = ?", userId);

        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO user_preferences(user_id, category, weight) VALUES (?,?,?)")) {
            for (Map.Entry<String, Integer> e : categories.entrySet()) {
                if (e.getValue() <= 0) continue; // 0 == "no preference", don't store
                ps.setLong(1, userId);
                ps.setString(2, e.getKey());
                ps.setInt(3, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO user_tags(user_id, tag) VALUES (?,?)")) {
            for (String tag : tags) {
                ps.setLong(1, userId);
                ps.setString(2, tag);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void delete(Connection c, String sql, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }
}
