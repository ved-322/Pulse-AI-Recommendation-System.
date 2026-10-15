package com.recsys.config;

import com.recsys.dao.ItemDao;
import com.recsys.model.Category;
import com.recsys.model.Item;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Loads the product catalogue from {@code /catalogue/*.tsv} (listed in {@code /catalogue/index.txt}).
 * Each file starts with {@code #category=Name|emoji=..|color=..} followed by rows of
 * {@code brand|name|price|tags|meta|description}. Rows are inserted with JDBC batches in one transaction.
 */
public class CatalogueLoader {
    private final Database db;
    private final ItemDao itemDao;

    public CatalogueLoader(Database db, ItemDao itemDao) {
        this.db = db;
        this.itemDao = itemDao;
    }

    public void loadIfEmpty() {
        if (db.read(c -> itemDao.count(c)) > 0) return;
        List<Category> categories = new ArrayList<>();
        List<Item> items = new ArrayList<>();
        Set<String> slugs = new HashSet<>();

        for (String file : read("/catalogue/index.txt").split("\\R")) {
            if (file.isBlank()) continue;
            Category category = null;
            for (String line : read("/catalogue/" + file.trim()).split("\\R")) {
                if (line.isBlank()) continue;
                if (line.startsWith("#category=")) {
                    category = parseCategory(line);
                    categories.add(category);
                } else if (!line.startsWith("#")) {
                    if (category == null) throw new IllegalStateException(file + ": row before #category header");
                    items.add(parseItem(line, category, slugs, file));
                }
            }
        }
        db.inTransaction(c -> {
            for (Category cat : categories) itemDao.insertCategory(c, cat);
            itemDao.insertBatch(c, items);
            return null;
        });
    }

    private Category parseCategory(String line) {
        String[] parts = line.substring(1).split("\\|");
        String name = null, emoji = "✨", color = "#6366f1";
        for (String p : parts) {
            String[] kv = p.split("=", 2);
            switch (kv[0]) {
                case "category" -> name = kv[1];
                case "emoji" -> emoji = kv[1];
                case "color" -> color = kv[1];
                default -> { }
            }
        }
        if (name == null) throw new IllegalStateException("Bad category header: " + line);
        return new Category(name, emoji, color);
    }

    private Item parseItem(String line, Category cat, Set<String> slugs, String file) {
        String[] f = line.split("\\|", -1);
        if (f.length != 6) throw new IllegalStateException(file + ": expected 6 fields but found " + f.length + " in: " + line);
        String slug = slugify(f[1], f[4]);
        for (int n = 2; !slugs.add(slug); n++) slug = slugify(f[1], f[4]) + "-" + n;
        return new Item(0, slug, f[1].trim(), f[0].trim(), cat.name(), f[5].trim(), Double.parseDouble(f[2].trim()),
                Arrays.asList(f[3].trim().split(",")), f[4].trim(), cat.emoji(), cat.color());
    }

    /** Lower-case, URL-safe key: "Pegasus 41" -> "pegasus-41"; a year/meta suffix keeps remakes distinct. */
    public static String slugify(String name, String meta) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return meta == null || meta.isBlank() ? s : s + "-" + meta.trim();
    }

    private String read(String path) {
        try (InputStream in = CatalogueLoader.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing classpath resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
    }
}
