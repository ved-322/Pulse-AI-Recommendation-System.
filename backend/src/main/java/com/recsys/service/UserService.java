package com.recsys.service;

import com.recsys.config.Database;
import com.recsys.dao.PreferenceDao;
import com.recsys.dao.UserDao;
import com.recsys.exception.AuthenticationException;
import com.recsys.exception.ConflictException;
import com.recsys.exception.DataAccessException;
import com.recsys.exception.ForbiddenException;
import com.recsys.exception.NotFoundException;
import com.recsys.exception.TooManyRequestsException;
import com.recsys.model.Role;
import com.recsys.model.User;
import com.recsys.model.UserSummary;
import com.recsys.util.PasswordHasher;
import com.recsys.util.Validator;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Registration, authentication and admin-side profile management. */
public class UserService {
    private static final int MAX_FAILURES = 5;
    private static final long LOCK_MILLIS = 5 * 60_000L;

    private record Attempts(int failures, long lockedUntil) { }

    private final Database db;
    private final UserDao userDao;
    private final PreferenceDao prefDao;
    private final ItemService items;
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public UserService(Database db, UserDao userDao, PreferenceDao prefDao, ItemService items) {
        this.db = db;
        this.userDao = userDao;
        this.prefDao = prefDao;
        this.items = items;
    }

    /** Creates the account and its starting interests atomically. */
    public User register(String name, String email, String password, List<String> categories) {
        String cleanName = Validator.name(name);
        String cleanEmail = Validator.email(email);
        Validator.password(password);
        String hash = PasswordHasher.hash(password);

        Map<String, Integer> prefs = new LinkedHashMap<>();
        if (categories != null) {
            List<String> known = items.categories();
            for (String cat : categories) if (known.contains(cat)) prefs.put(cat, 4);
        }

        try {
            return db.inTransaction(c -> {
                if (userDao.findByEmail(c, cleanEmail).isPresent()) {
                    throw new ConflictException("An account with this e-mail already exists.");
                }
                long id = userDao.insert(c, cleanName, cleanEmail, hash, Role.USER);
                prefDao.replaceAll(c, id, prefs, new LinkedHashSet<>());
                return userDao.findById(c, id).orElseThrow();
            });
        } catch (DataAccessException e) {
            if (Database.isUniqueViolation(e)) throw new ConflictException("An account with this e-mail already exists.");
            throw e;
        }
    }

    public User login(String email, String password) {
        String key = email == null ? "" : email.trim().toLowerCase();
        Attempts a = attempts.get(key);
        if (a != null && a.lockedUntil() > System.currentTimeMillis()) {
            throw new TooManyRequestsException("Too many failed attempts. Try again in a few minutes.");
        }
        User user = db.read(c -> userDao.findByEmail(c, key)).orElse(null);
        // Always run the hash check, even for unknown users, so timing does not reveal valid e-mails.
        boolean ok = user != null && PasswordHasher.verify(password == null ? "" : password, user.passwordHash());
        if (!ok) {
            registerFailure(key);
            throw new AuthenticationException("Incorrect e-mail or password.");
        }
        if (!user.active()) throw new ForbiddenException("This account has been deactivated.");
        attempts.remove(key);
        return user;
    }

    private void registerFailure(String key) {
        attempts.merge(key, new Attempts(1, 0), (old, ignored) -> {
            int failures = old.failures() + 1;
            return new Attempts(failures, failures >= MAX_FAILURES ? System.currentTimeMillis() + LOCK_MILLIS : 0);
        });
    }

    public User require(long id) {
        return db.read(c -> userDao.findById(c, id)).orElseThrow(() -> new NotFoundException("User not found."));
    }

    // ------------------------------------------------------------------ admin operations

    public List<UserSummary> listAll() {
        return db.read(userDao::findAllWithActivity);
    }

    public Map<String, Object> profile(long targetId) {
        return db.read(c -> {
            User u = userDao.findById(c, targetId).orElseThrow(() -> new NotFoundException("User not found."));
            Map<String, Object> m = new LinkedHashMap<>(u.toPublicMap());
            m.put("categories", prefDao.categoryWeights(c, targetId));
            Set<String> tags = prefDao.tags(c, targetId);
            m.put("tags", tags);
            return m;
        });
    }

    /** Changes role and / or active flag. Guards against an admin locking everyone out. */
    public User updateByAdmin(long actorId, long targetId, Role role, Boolean active) {
        return db.inTransaction(c -> {
            User target = userDao.findById(c, targetId).orElseThrow(() -> new NotFoundException("User not found."));
            boolean demotes = role != null && role != Role.ADMIN && target.isAdmin();
            boolean deactivates = active != null && !active && target.isAdmin();
            if (targetId == actorId && (demotes || deactivates || (active != null && !active))) {
                throw new ForbiddenException("You cannot demote or deactivate your own account.");
            }
            if ((demotes || deactivates) && userDao.countActiveAdmins(c) <= 1) {
                throw new ForbiddenException("At least one active administrator is required.");
            }
            if (role != null) userDao.updateRole(c, targetId, role);
            if (active != null) userDao.updateActive(c, targetId, active);
            return userDao.findById(c, targetId).orElseThrow();
        });
    }

    public void delete(long actorId, long targetId) {
        if (actorId == targetId) throw new ForbiddenException("You cannot delete your own account.");
        db.inTransaction(c -> {
            User target = userDao.findById(c, targetId).orElseThrow(() -> new NotFoundException("User not found."));
            if (target.isAdmin() && userDao.countActiveAdmins(c) <= 1) {
                throw new ForbiddenException("At least one active administrator is required.");
            }
            userDao.delete(c, targetId); // FK ON DELETE CASCADE removes prefs, interactions and log rows
            return null;
        });
    }
}
