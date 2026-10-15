package com.recsys.dao;

import com.recsys.model.Role;
import com.recsys.model.User;
import com.recsys.model.UserSummary;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** CRUD access to the {@code users} table. All statements are parameterised (no SQL injection). */
public class UserDao {

    public long insert(Connection c, String name, String email, String passwordHash, Role role) throws SQLException {
        String sql = "INSERT INTO users(name, email, password_hash, role) VALUES (?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, passwordHash);
            ps.setString(4, role.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public Optional<User> findById(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<User> findByEmail(Connection c, String email) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** Users joined with a count of their interactions, for the admin table. */
    public List<UserSummary> findAllWithActivity(Connection c) throws SQLException {
        String sql = "SELECT u.id, u.name, u.email, u.role, u.active, u.created_at, "
                + "(SELECT COUNT(*) FROM interactions i WHERE i.user_id = u.id) AS n "
                + "FROM users u ORDER BY u.id";
        List<UserSummary> out = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new UserSummary(rs.getLong("id"), rs.getString("name"), rs.getString("email"),
                        Role.valueOf(rs.getString("role")), rs.getBoolean("active"),
                        rs.getTimestamp("created_at").getTime(), rs.getInt("n")));
            }
        }
        return out;
    }

    public void updateName(Connection c, long id, String name) throws SQLException {
        exec(c, "UPDATE users SET name = ? WHERE id = ?", name, id);
    }

    public void updateRole(Connection c, long id, Role role) throws SQLException {
        exec(c, "UPDATE users SET role = ? WHERE id = ?", role.name(), id);
    }

    public void updateActive(Connection c, long id, boolean active) throws SQLException {
        exec(c, "UPDATE users SET active = ? WHERE id = ?", active, id);
    }

    public boolean delete(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    public long count(Connection c) throws SQLException {
        return scalar(c, "SELECT COUNT(*) FROM users");
    }

    public long countActiveAdmins(Connection c) throws SQLException {
        return scalar(c, "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = TRUE");
    }

    private long scalar(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void exec(Connection c, String sql, Object a, Object b) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, a);
            ps.setObject(2, b);
            ps.executeUpdate();
        }
    }

    private User map(ResultSet rs) throws SQLException {
        return new User(rs.getLong("id"), rs.getString("name"), rs.getString("email"),
                rs.getString("password_hash"), Role.valueOf(rs.getString("role")),
                rs.getBoolean("active"), rs.getTimestamp("created_at").getTime());
    }
}
