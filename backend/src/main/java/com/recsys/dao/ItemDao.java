package com.recsys.dao;

import com.recsys.model.Category;
import com.recsys.model.Item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemDao {

    public List<Item> findAll(Connection c) throws SQLException {
        List<Item> items = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM items ORDER BY id")) {
            while (rs.next()) {
                items.add(new Item(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                        rs.getString("brand"), rs.getString("category"), rs.getString("description"),
                        rs.getBigDecimal("price").doubleValue(),
                        Arrays.asList(rs.getString("tags").split(",")),
                        rs.getString("meta"), rs.getString("emoji"), rs.getString("accent")));
            }
        }
        return items;
    }

    public List<Category> findCategories(Connection c) throws SQLException {
        List<Category> out = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, emoji, color FROM categories ORDER BY name")) {
            while (rs.next()) out.add(new Category(rs.getString(1), rs.getString(2), rs.getString(3)));
        }
        return out;
    }

    public void insertCategory(Connection c, Category cat) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO categories(name, emoji, color) VALUES (?,?,?)")) {
            ps.setString(1, cat.name());
            ps.setString(2, cat.emoji());
            ps.setString(3, cat.color());
            ps.executeUpdate();
        }
    }

    public void insertBatch(Connection c, List<Item> items) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO items(slug, name, brand, category, description, price, tags, meta, emoji, accent) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            for (Item i : items) {
                ps.setString(1, i.slug());
                ps.setString(2, i.name());
                ps.setString(3, i.brand());
                ps.setString(4, i.category());
                ps.setString(5, i.description());
                ps.setBigDecimal(6, java.math.BigDecimal.valueOf(i.price()));
                ps.setString(7, String.join(",", i.tags()));
                ps.setString(8, i.meta());
                ps.setString(9, i.emoji());
                ps.setString(10, i.accent());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public long count(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM items")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
