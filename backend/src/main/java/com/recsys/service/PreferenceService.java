package com.recsys.service;

import com.recsys.config.Database;
import com.recsys.dao.PreferenceDao;
import com.recsys.dao.UserDao;
import com.recsys.exception.NotFoundException;
import com.recsys.exception.ValidationException;
import com.recsys.model.User;
import com.recsys.util.Validator;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads and updates a user's explicit preferences (categories + tags + display name). */
public class PreferenceService {
    private static final int MAX_TAGS = 15;

    private final Database db;
    private final PreferenceDao prefDao;
    private final UserDao userDao;
    private final ItemService items;

    public PreferenceService(Database db, PreferenceDao prefDao, UserDao userDao, ItemService items) {
        this.db = db;
        this.prefDao = prefDao;
        this.userDao = userDao;
        this.items = items;
    }

    public Map<String, Object> view(long userId) {
        return db.read(c -> {
            User user = userDao.findById(c, userId).orElseThrow(() -> new NotFoundException("User not found."));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", user.name());
            out.put("email", user.email());
            out.put("categories", prefDao.categoryWeights(c, userId));
            out.put("tags", prefDao.tags(c, userId));
            out.put("allCategories", items.categories());
            out.put("suggestedTags", items.popularTags(48));
            return out;
        });
    }

    /**
     * Validates everything first, then replaces name + categories + tags in ONE transaction:
     * either all of the profile changes are saved or none are.
     */
    public void update(long userId, String name, Map<String, Double> categories, List<String> tags) {
        String cleanName = Validator.name(name);

        List<String> known = items.categories();
        Map<String, Integer> cleanCats = new LinkedHashMap<>();
        if (categories != null) {
            for (Map.Entry<String, Double> e : categories.entrySet()) {
                if (!known.contains(e.getKey())) throw new ValidationException("Unknown category: " + e.getKey());
                if (e.getValue() == null) throw new ValidationException("Weight missing for " + e.getKey());
                cleanCats.put(e.getKey(), Validator.intInRange(e.getValue(), 0, 5, "Weight for " + e.getKey()));
            }
        }
        Set<String> cleanTags = new LinkedHashSet<>();
        if (tags != null) tags.forEach(t -> cleanTags.add(Validator.tag(t)));
        if (cleanTags.size() > MAX_TAGS) throw new ValidationException("You can pick at most " + MAX_TAGS + " tags.");

        db.inTransaction(c -> {
            userDao.updateName(c, userId, cleanName);
            prefDao.replaceAll(c, userId, cleanCats, cleanTags);
            return null;
        });
    }
}
