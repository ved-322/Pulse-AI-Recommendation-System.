package com.recsys.service;

import com.recsys.config.Database;
import com.recsys.dao.ItemDao;
import com.recsys.exception.NotFoundException;
import com.recsys.model.Category;
import com.recsys.model.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Catalogue access. The catalogue is read-mostly, so it is cached in memory. */
public class ItemService {
    private final Database db;
    private final ItemDao itemDao;
    private volatile Map<Long, Item> cache;
    private volatile List<Category> categories;

    public ItemService(Database db, ItemDao itemDao) {
        this.db = db;
        this.itemDao = itemDao;
        reload();
    }

    public final void reload() {
        List<Item> all = db.read(itemDao::findAll);
        this.cache = all.stream().collect(Collectors.toMap(Item::id, Function.identity(),
                (a, b) -> a, LinkedHashMap::new));
        this.categories = db.read(itemDao::findCategories);
    }

    public Map<Long, Item> byId() { return cache; }

    public Item require(long id) {
        Item item = cache.get(id);
        if (item == null) throw new NotFoundException("Item " + id + " does not exist.");
        return item;
    }

    /** Category names, alphabetically. */
    public List<String> categories() {
        return categories.stream().map(Category::name).toList();
    }

    /** Category names with their emoji and colour - drives the interest tiles in the UI. */
    public List<Category> categoryDetails() { return categories; }

    /** Most frequent tags in the catalogue - offered as suggestions in the interest studio. */
    public List<String> popularTags(int limit) {
        Map<String, Integer> counts = new TreeMap<>();
        cache.values().forEach(i -> i.tags().forEach(t -> counts.merge(t, 1, Integer::sum)));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(limit).map(Map.Entry::getKey).toList();
    }

    public List<Item> search(String category, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return cache.values().stream()
                .filter(i -> category == null || category.isBlank() || i.category().equalsIgnoreCase(category))
                .filter(i -> q.isEmpty() || i.name().toLowerCase(Locale.ROOT).contains(q)
                        || i.brand().toLowerCase(Locale.ROOT).contains(q)
                        || i.description().toLowerCase(Locale.ROOT).contains(q)
                        || i.tags().stream().anyMatch(t -> t.contains(q)))
                .toList();
    }
}
