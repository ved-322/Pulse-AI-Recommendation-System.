package com.recsys.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class SettingsDao {

    public Map<String, String> findAll(Connection c) throws SQLException {
        Map<String, String> out = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT setting_key, setting_value FROM settings")) {
            while (rs.next()) out.put(rs.getString(1), rs.getString(2));
        }
        return out;
    }

    /** Insert-or-update every entry in one batch (call inside a transaction). */
    public void upsertAll(Connection c, Map<String, String> values) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "MERGE INTO settings(setting_key, setting_value) KEY(setting_key) VALUES (?,?)")) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                ps.setString(1, e.getKey());
                ps.setString(2, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
