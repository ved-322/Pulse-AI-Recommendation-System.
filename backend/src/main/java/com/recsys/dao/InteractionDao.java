package com.recsys.dao;

import com.recsys.model.Interaction;
import com.recsys.model.InteractionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class InteractionDao {

    public void insert(Connection c, long userId, long itemId, InteractionType type, long createdAtMillis)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO interactions(user_id, item_id, type, created_at) VALUES (?,?,?,?)")) {
            ps.setLong(1, userId);
            ps.setLong(2, itemId);
            ps.setString(3, type.name());
            ps.setTimestamp(4, new Timestamp(createdAtMillis));
            ps.executeUpdate();
        }
    }

    public List<Interaction> findByUser(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM interactions WHERE user_id = ? ORDER BY created_at")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return readAll(rs);
            }
        }
    }

    public List<Interaction> findAll(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT * FROM interactions")) {
            return readAll(rs);
        }
    }

    private List<Interaction> readAll(ResultSet rs) throws SQLException {
        List<Interaction> out = new ArrayList<>();
        while (rs.next()) {
            out.add(new Interaction(rs.getLong("id"), rs.getLong("user_id"), rs.getLong("item_id"),
                    InteractionType.valueOf(rs.getString("type")), rs.getTimestamp("created_at").getTime()));
        }
        return out;
    }
}
