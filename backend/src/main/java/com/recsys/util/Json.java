package com.recsys.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.recsys.exception.ValidationException;

public final class Json {
    private static final Gson GSON = new GsonBuilder().create();

    private Json() { }

    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) throw new ValidationException("Request body is empty.");
        try {
            T value = GSON.fromJson(json, type);
            if (value == null) throw new ValidationException("Request body is empty.");
            return value;
        } catch (JsonParseException e) {
            throw new ValidationException("Malformed JSON in request body.");
        }
    }
}
