package com.recsys.util;

import com.recsys.exception.ValidationException;

import java.util.Locale;
import java.util.regex.Pattern;

/** Central input validation so every layer rejects bad data the same way. */
public final class Validator {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern TAG = Pattern.compile("^[a-z0-9][a-z0-9 -]{0,23}$");

    private Validator() { }

    public static String name(String raw) {
        String v = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (v.length() < 2 || v.length() > 80) throw new ValidationException("Name must be 2-80 characters.");
        return v;
    }

    public static String email(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (v.length() > 120 || !EMAIL.matcher(v).matches()) throw new ValidationException("Please enter a valid e-mail address.");
        return v;
    }

    public static String password(String raw) {
        if (raw == null || raw.length() < 8 || raw.length() > 100) {
            throw new ValidationException("Password must be 8-100 characters.");
        }
        boolean letter = raw.chars().anyMatch(Character::isLetter);
        boolean digit = raw.chars().anyMatch(Character::isDigit);
        if (!letter || !digit) throw new ValidationException("Password must contain letters and numbers.");
        return raw;
    }

    public static String tag(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!TAG.matcher(v).matches()) throw new ValidationException("Invalid tag: \"" + raw + "\".");
        return v;
    }

    public static int intInRange(double value, int min, int max, String field) {
        if (value != Math.rint(value) || value < min || value > max) {
            throw new ValidationException(field + " must be a whole number between " + min + " and " + max + ".");
        }
        return (int) value;
    }

    public static double doubleInRange(double value, double min, double max, String field) {
        if (Double.isNaN(value) || value < min || value > max) {
            throw new ValidationException(field + " must be between " + min + " and " + max + ".");
        }
        return value;
    }
}
