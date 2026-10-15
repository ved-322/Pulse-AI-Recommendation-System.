package com.recsys.model;

import com.recsys.exception.ValidationException;

/** A behavioural signal. The weight says how strongly it expresses interest (negative = dislike). */
public enum InteractionType {
    VIEW(0.5),
    CLICK(1.0),
    LIKE(2.0),
    PURCHASE(3.0),
    DISMISS(-2.0);

    private final double weight;

    InteractionType(double weight) {
        this.weight = weight;
    }

    public double weight() { return weight; }

    public boolean isPositive() { return weight > 0; }

    public static InteractionType parse(String raw) {
        if (raw == null) throw new ValidationException("Interaction type is required.");
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Unknown interaction type: " + raw);
        }
    }
}
