package com.recsys.dao;

import com.recsys.model.Review;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReviewDao {

    /** Insert-or-update: one review per user per item. */
    public void upsert(Connection c, long userId, long itemId, int rating, String body, boolean sample, long createdAt)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "MERGE INTO reviews(user_id, item_id, rating, body, sample, created_at) KEY(user_id, item_id) VALUES (?,?,?,?,?,?)")) {
            ps.setLong(1, userId);
            ps.setLong(2, itemId);
            ps.setInt(3, rating);
            ps.setString(4, body);
            ps.setBoolean(5, sample);
            ps.setTimestamp(6, new Timestamp(createdAt));
            ps.executeUpdate();
        }
    }

    public boolean deleteOwn(Connection c, long userId, long itemId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM reviews WHERE user_id = ? AND item_id = ?")) {
            ps.setLong(1, userId);
            ps.setLong(2, itemId);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Review> findByItem(Connection c, long itemId, int limit) throws SQLException {
        List<Review> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT r.*, u.name AS author FROM reviews r JOIN users u ON u.id = r.user_id "
                        + "WHERE r.item_id = ? ORDER BY r.sample ASC, r.created_at DESC LIMIT ?")) {
            ps.setLong(1, itemId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Review(rs.getLong("id"), rs.getLong("user_id"), rs.getString("author"),
                            rs.getLong("item_id"), rs.getInt("rating"), rs.getString("body"),
                            rs.getBoolean("sample"), rs.getTimestamp("created_at").getTime()));
                }
            }
        }
        return out;
    }

    /** itemId -> [average rating, number of reviews] */
    public Map<Long, double[]> summaries(Connection c) throws SQLException {
        Map<Long, double[]> out = new LinkedHashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT item_id, AVG(CAST(rating AS DOUBLE)), COUNT(*) FROM reviews GROUP BY item_id")) {
            while (rs.next()) out.put(rs.getLong(1), new double[]{Math.round(rs.getDouble(2) * 10) / 10.0, rs.getLong(3)});
        }
        return out;
    }
}
