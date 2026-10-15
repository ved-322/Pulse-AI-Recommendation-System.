package com.recsys.config;

import com.recsys.dao.ItemDao;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

/** Creates the schema on first start and loads the catalogue. Idempotent - safe on every boot. */
public class DatabaseInitializer {
    private final Database db;
    private final ItemDao itemDao = new ItemDao();

    public DatabaseInitializer(Database db) {
        this.db = db;
    }

    public void run() {
        runScript("/schema.sql");
        new CatalogueLoader(db, itemDao).loadIfEmpty();
    }

    private void runScript(String resource) {
        String script = readResource(resource);
        db.inTransaction(c -> {
            try (Statement st = c.createStatement()) {
                String cleaned = Arrays.stream(script.split("\\R"))
                        .filter(line -> !line.trim().startsWith("--"))
                        .reduce("", (a, b) -> a + b + "\n");
                for (String sql : cleaned.split(";\\s*\\n")) {
                    if (!sql.isBlank()) st.execute(sql);
                }
            } catch (SQLException e) {
                throw e;
            }
            return null;
        });
    }

    private String readResource(String path) {
        try (InputStream in = DatabaseInitializer.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing classpath resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }
}
